package inkblot.codegen

import inkblot.reasoning.VariableProperties
import inkblot.runtime.Inkblot
import org.apache.jena.query.Query

class SemanticObjectGenerator(private val className: String, private val query: Query, private val anchor: String, private val namespace: String, private val variableInfo: Map<String, VariableProperties>) {

    private val synthesizer = QuerySynthesizer(query, anchor, variableInfo)
    private val changeNodeGenerator = ChangeNodeGenerator(synthesizer)

    init {
        val foundVars = query.resultVars.toSet().minus(anchor)
        val specifiedVars = variableInfo.keys.toSet()
        val unspecified = foundVars.minus(specifiedVars)
        val overspecified = specifiedVars.minus(foundVars)

        // Ensure all variables are properly specified
        if(unspecified.isNotEmpty() && overspecified.isNotEmpty())
            throw Exception("Configuration of '$className' is missing information about these SPARQL variables: ${unspecified.joinToString(", ")} but specifies these unused variables: ${overspecified.joinToString(", ")}")
        else if(unspecified.isNotEmpty())
            throw Exception("Configuration of '$className' is missing information about these SPARQL variables: ${unspecified.joinToString(", ")}")
        else if(overspecified.isNotEmpty())
            throw Exception("Configuration of '$className' specifies these SPARQL variables that are absent from the query: ${overspecified.joinToString(", ")}. This is most likely unintentional.")

        // Ensure all data types are supported
        val unsupportedTypes = variableInfo.values.filter { !it.isObjectReference && !Inkblot.types.supported.contains(it.datatype) }.map { it.datatype }
        if(unsupportedTypes.isNotEmpty())
            throw Exception("Configuration of '$className' uses these unsupported data types: ${unsupportedTypes.joinToString(", ")}")
    }

    private fun indent(content: String, l: Int): String {
        val lines = content.lines()
        return if(lines.size == 1)
                lines.first()
            else
                lines.first() + "\n" + lines.drop(1).joinToString("\n").prependIndent(" ".repeat(4*l))
    }

    fun gen(): String {
        return pkg() + "\n" + imports() + "\n\n" + genFactory() + "\n\n" + genObject()
    }

    private fun genFactory() = """
        object ${className}Factory : SemanticObjectFactory<$className>() {
            override val query = "${query.toString().replace("\n", " ").replace(Regex("/\\s+/"), " ")}"
            override val anchor = "$anchor"
            
            ${indent(genFactoryCreate(), 3)}
            
            ${indent(genFactoryInstantiate(), 3)}
        }
        """.trimIndent()

    private fun genFactoryCreate() : String {
        val synthesizedCreateQuery = synthesizer.baseCreationUpdate()
        val constructorVars = variableInfo.values.map { props ->
            if(props.isObjectReference) {
                when {
                    !props.functional -> "${props.targetName}.map{ it.uri }"
                    props.nullable -> "${props.targetName}?.uri"
                    else -> "${props.targetName}.uri"
                }
            }
            else
                props.targetName
        }

        val constructorParamLine = (listOf("uri") + constructorVars).joinToString(", ")

        val safeInitObjRefBindings = variableInfo.filterValues { it.functional && !it.nullable && it.isObjectReference }.map { (sparql, info) ->
            "template.setIri(\"$sparql\", ${info.targetName}.uri)"
        }

        val safeInitDataBindings = variableInfo.filterValues { it.functional && !it.nullable && !it.isObjectReference }.map { (sparql, info) ->
            "template.setParam(\"$sparql\", ResourceFactory.createTypedLiteral(${info.targetName}))"
        }

        return """
            fun create(${genExternalConstructorArgs()}): $className {
                val uri = "$namespace$anchor" + Inkblot.freshSuffixFor("$anchor")

                // set non-null parameters and create object
                val template = ParameterizedSparqlString("$synthesizedCreateQuery")
                template.setIri("anchor", uri)
                ${indent((safeInitObjRefBindings + safeInitDataBindings).joinToString("\n"), 4)}
                
                val update = template.asUpdate()
                ${indent(genFactoryCreateInitNullable(), 4)}
                ${indent(genFactoryCreateInitNonFunctional(), 4)}
                Inkblot.changelog.add(CreateNode(update))
                return $className($constructorParamLine)
            }
        """.trimIndent()
    }

    private fun genFactoryCreateInitNullable(): String {
        val vars = variableInfo.filter { it.value.nullable && it.value.functional }

        if(vars.isEmpty())
            return ""

        val initializers = vars.mapValues { (sparql, props) ->
            val targetBinding = if(props.isObjectReference)
                "partialUpdate.setIri(\"v\", ${props.targetName}.uri)"
            else
                "partialUpdate.setParam(\"v\", ResourceFactory.createTypedLiteral(${props.targetName}))"

            """
                if(${props.targetName} != null) {
                    val partialUpdate = ParameterizedSparqlString("${synthesizer.initializerUpdate(sparql)}")
                    partialUpdate.setIri("anchor", uri)
                    $targetBinding
                    partialUpdate.asUpdate().forEach { update.add(it) }
                }
            """.trimIndent()
        }.values.joinToString("\n\n")

        return "\n// initialize nullable functional properties\n$initializers\n"
    }

    private fun genFactoryCreateInitNonFunctional(): String {
        val vars = variableInfo.filter { !it.value.functional }

        if(vars.isEmpty())
            return ""

        val initializers = vars.mapValues { (sparql, props) ->
            val targetBinding = if(props.isObjectReference)
                    "partialUpdate.setIri(\"$sparql\", it.uri)"
                else
                    "partialUpdate.setParam(\"$sparql\", ResourceFactory.createTypedLiteral(it))"

            """
                ${props.targetName}.forEach {
                    val partialUpdate = ParameterizedSparqlString("${synthesizer.initializerUpdate(sparql)}")
                    partialUpdate.setIri("$anchor", uri)
                    $targetBinding
                    partialUpdate.asUpdate().forEach { part -> update.add(part) }
                }
            """.trimIndent()
        }.values.joinToString("\n\n")

        return "\n// initialize non-functional properties\n$initializers\n"
    }

    private fun genFactoryInstantiate() : String {
        val constructorArgs = (listOf("uri") + variableInfo.values.map { it.targetName }).joinToString(", ")
        return """
            override fun instantiateSingleResult(lines: List<QuerySolution>): $className? {
                if(lines.isEmpty())
                    return null
                    
                val uri = lines.first().getResource("$anchor").uri
                ${indent(genFactoryInstantiateFunctional(), 4)}
                ${indent(genFactoryInstantiateNonFunctional(), 4)}
                return $className($constructorArgs) 
            }
        """.trimIndent()
    }

    private fun genFactoryInstantiateFunctional() : String {
        val functionalVars = variableInfo.filterValues { it.functional }

        if(functionalVars.isEmpty())
            return ""

        val assignments = functionalVars.mapValues { (sparql, props) ->
            when {
                props.isObjectReference && props.nullable -> "val ${props.targetName} = lines.first().getResource(\"$sparql\")?.uri"
                props.isObjectReference -> "val ${props.targetName} = lines.first().getResource(\"$sparql\").uri"
                props.nullable -> "val ${props.targetName} = Inkblot.types.literalToNullable${props.datatype}(lines.first().getLiteral(\"$sparql\"))"
                else -> "val ${props.targetName} = Inkblot.types.literalTo${props.datatype}(lines.first().getLiteral(\"$sparql\"))"
            }
        }.values.joinToString("\n")

        return "\n// for functional properties we can read the first only, as all others have to be the same\n$assignments\n"
    }

    private fun genFactoryInstantiateNonFunctional(): String {
        val nonFuncVars = variableInfo.filterValues { !it.functional }

        if(nonFuncVars.isEmpty())
            return ""

        val assignments = nonFuncVars.mapValues { (sparql, props) ->
            if(props.isObjectReference)
                "val ${props.targetName} = lines.mapNotNull { it.getResource(\"$sparql\")?.uri }.distinct()"
            else
                "val ${props.targetName} = lines.mapNotNull { Inkblot.types.literalToNullable${props.datatype}(it.getLiteral(\"$sparql\")) }.distinct()"
        }.values.joinToString("\n")

        return "\n// for higher cardinality properties, we have to collect all distinct values\n$assignments\n"
    }

    private fun genObject(): String {
        val constructorVars = variableInfo.values.joinToString(", "){ it.targetName }
        return """
            class $className internal constructor(${genInternalConstructorArgs()}) : SemanticObject(uri) {
                companion object {
                    fun create(${genExternalConstructorArgs()}) = ${className}Factory.create($constructorVars)
                    fun loadAll() = ${className}Factory.loadAll()
                    fun loadSelected(filter: String) = ${className}Factory.loadSelected(filter)
                    fun loadFromURI(uri: String) = ${className}Factory.loadFromURI(uri)
                }
                
                ${indent(genProperties(), 4)}
                
                // TODO: Merge
            }
        """.trimIndent()
    }

    private fun genExternalConstructorArgs(): String {
        return variableInfo.map { (_, info) ->
            when {
                info.functional && info.nullable -> "${info.targetName}: ${info.datatype}?"
                info.functional -> "${info.targetName}: ${info.datatype}"
                else -> "${info.targetName}: List<${info.datatype}>"
            }
        }.joinToString(", ")
    }

    private fun genInternalConstructorArgs(): String {
        val args = mutableListOf("uri: String")

        variableInfo.values.forEach { info ->
            val arg = when {
                info.isObjectReference && info.functional && info.nullable -> "${info.targetName}: String?"
                info.isObjectReference && info.functional -> "${info.targetName}: String"
                info.isObjectReference -> "${info.targetName}: List<String>"
                info.functional && info.nullable -> "${info.targetName}: ${info.datatype}?"
                info.functional -> "${info.targetName}: ${info.datatype}"
                else -> "${info.targetName}: List<${info.datatype}>"
            }
            args.add(arg)
        }

        return args.joinToString(", ")
    }

    private fun genProperties() = variableInfo.keys.joinToString("\n\n") { genProperty(it) }

    private fun genProperty(varName: String): String {
        return if(variableInfo[varName]!!.isObjectReference)
            genObjectProperty(varName)
        else
            genDataProperty(varName)
    }

    private fun genDataProperty(varName: String): String {
        val varProps = variableInfo[varName]!!
        return if(varProps.functional) {
            if(varProps.nullable)
                genSingletNullableDataProperty(varProps.targetName, varName, varProps.datatype)
            else
                genSingletNonNullDataProperty(varProps.targetName, varName, varProps.datatype)
        } else {
            genMultiDataProperty(varProps.targetName, varName, varProps.datatype)
        }
    }

    private fun genSingletNonNullDataProperty(varName: String, sparqlName: String, datatype: String): String {
        return """
            var $varName: $datatype = $varName
                set(value) {
                    ${indent(genDeleteCheck(varName), 5)}

                    val newValueNode = ResourceFactory.createTypedLiteral(value).asNode()
                    val oldValueNode = ResourceFactory.createTypedLiteral(field).asNode()
                    ${indent(changeNodeGenerator.changeCN("uri", sparqlName, "oldValueNode", "newValueNode"), 5) }
                    Inkblot.changelog.add(cn)
                    
                    field = value
                    markDirty()
                }
        """.trimIndent()
    }

    private fun genSingletNullableDataProperty(varName: String, sparqlName: String, datatype: String): String {
        return """
            var $varName: $datatype? = $varName
                set(value) {
                    ${indent(genDeleteCheck(varName), 5)}
                    
                    val oldValueNode = ResourceFactory.createTypedLiteral(field).asNode()
                    val newValueNode = ResourceFactory.createTypedLiteral(value).asNode()
                    if(value == null) {
                        // Unset value
                        ${indent(changeNodeGenerator.removeCN("uri", sparqlName, "oldValueNode"), 6)}
                        Inkblot.changelog.add(cn)
                    }
                    else if(field == null) {
                        // Pure insertion
                        ${indent(changeNodeGenerator.addCN("uri", sparqlName, "newValueNode"), 6)}
                        Inkblot.changelog.add(cn)
                    }
                    else {
                        // Update value
                        ${indent(changeNodeGenerator.changeCN("uri", sparqlName, "oldValueNode", "newValueNode!!"), 6)}
                        Inkblot.changelog.add(cn)
                    }

                    field = value
                    markDirty()
                }
        """.trimIndent()
    }

    private fun genMultiDataProperty(varName: String, sparqlName: String, datatype: String): String {
        val valueNode = "ResourceFactory.createTypedLiteral(data).asNode()"
        return """
            private val inkblt_$varName = $varName.toMutableList()

            val $varName: List<$datatype>
                get() = inkblt_$varName

            fun ${varName}_add(data: $datatype) {
                ${indent(genDeleteCheck(varName), 4)}
                inkblt_$varName.add(data)
                
                ${indent(changeNodeGenerator.addCN("uri", sparqlName, valueNode), 4)}
                Inkblot.changelog.add(cn)
                markDirty()
            }

            fun ${varName}_remove(data: $datatype) {
                ${indent(genDeleteCheck(varName), 4)}
                inkblt_$varName.remove(data)
                
                ${indent(changeNodeGenerator.removeCN("uri", sparqlName, valueNode), 4)}
                Inkblot.changelog.add(cn)
                markDirty()
            }
        """.trimIndent()
    }

    private fun genObjectProperty(varName: String): String {
        val varProps = variableInfo[varName]!!
        return if(varProps.functional) {
            if(varProps.nullable)
                genSingletNullableObjectProperty(varProps.targetName, varName, varProps.datatype)
            else
                genSingletNonNullObjectProperty(varProps.targetName, varName, varProps.datatype)
        } else {
            genMultiObjectProperty(varProps.targetName, varName, varProps.datatype)
        }
    }

    private fun genSingletNonNullObjectProperty(varName: String, sparqlName: String, datatype: String): String {
        return """
            private var _inkbltRef_$varName: String = $varName
            var $varName: $datatype
                get() = $datatype.loadFromURI(_inkbltRef_$varName)
            set(value) {
                ${indent(genDeleteCheck(varName), 4)}

                ${indent(changeNodeGenerator.changeCN("uri", sparqlName, "ResourceFactory.createResource(_inkbltRef_$varName).asNode()", "ResourceFactory.createResource(value.uri).asNode()"), 4)}
                Inkblot.changelog.add(cn)
                
                _inkbltRef_$varName = value.uri
                markDirty()
            }
        """.trimIndent()
    }

    private fun genSingletNullableObjectProperty(varName: String, sparqlName: String, datatype: String): String {
        return """
            private var _inkbltRef_$varName: String? = $varName
            var $varName: $datatype? = null
                get() {
                    return if(field == null && _inkbltRef_$varName != null)
                        ${datatype}Factory.loadFromURI(_inkbltRef_$varName!!)
                    else
                        field
                }
                set(value) {
                    ${indent(genDeleteCheck(varName), 5)}
                    field = value

                    val oldValueNode = ResourceFactory.createResource(_inkbltRef_$varName).asNode()
                    val newValueNode = ResourceFactory.createResource(value?.uri).asNode()

                    if(value == null) {
                        // Unset value
                        ${indent(changeNodeGenerator.removeCN("uri", sparqlName, "oldValueNode"), 6)}
                        Inkblot.changelog.add(cn)
                    }
                    else if(_inkbltRef_$varName == null) {
                        // Pure insertion
                        ${indent(changeNodeGenerator.addCN("uri", sparqlName, "newValueNode"), 6)}
                        Inkblot.changelog.add(cn)
                    }
                    else {
                        // Change value
                        ${indent(changeNodeGenerator.changeCN("uri", sparqlName, "oldValueNode", "newValueNode"), 6)}
                        Inkblot.changelog.add(cn)
                    }
                    
                    _inkbltRef_$varName = value?.uri
                    markDirty()
                }
        """.trimIndent()
    }

    private fun genMultiObjectProperty(varName: String, sparqlName: String, datatype: String): String {
        val valueNode = "ResourceFactory.createResource(obj.uri).asNode()"
        return """
            private val _inkbltRef_$varName = $varName.toMutableSet()
            val $varName: List<$datatype>
                get() = _inkbltRef_$varName.map { ${datatype}Factory.loadFromURI(it) } // this is cached from DB so I hope it's fine?

            fun ${varName}_add(obj: $datatype) {
                ${indent(genDeleteCheck(varName), 4)}
                _inkbltRef_$varName.add(obj.uri)

                ${indent(changeNodeGenerator.addCN("uri", sparqlName, valueNode), 4)}
                Inkblot.changelog.add(cn)
                markDirty()
            }

            fun ${varName}_remove(obj: $datatype) {
                ${indent(genDeleteCheck(varName), 4)}
                _inkbltRef_$varName.remove(obj.uri)

                ${indent(changeNodeGenerator.removeCN("uri", sparqlName, valueNode), 4)}
                Inkblot.changelog.add(cn)
                markDirty()
            }
        """.trimIndent()
    }

    private fun genDeleteCheck(varName: String) = """
            if(deleted)
                throw Exception("Trying to set property '$varName' on deleted object <${'$'}uri>")
        """.trimIndent()

    private fun imports(): String {
        return """
            import inkblot.runtime.*
            import org.apache.jena.query.ParameterizedSparqlString
            import org.apache.jena.query.QuerySolution
            import org.apache.jena.rdf.model.ResourceFactory
        """.trimIndent()
    }

    private fun pkg() = "package bikes\n"
}