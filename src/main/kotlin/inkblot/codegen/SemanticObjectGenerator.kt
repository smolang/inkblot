package inkblot.codegen

import inkblot.reasoning.VariableProperties
import inkblot.runtime.Inkblot
import org.apache.jena.query.Query

class SemanticObjectGenerator(
    className: String,
    private val pkg: String,
    query: Query,
    anchor: String,
    namespace: String,
    variableInfo: Map<String, VariableProperties>
): AbstractSemanticObjectGenerator(className, query, anchor, namespace, variableInfo) {

    private val changeNodeGenerator = ChangeNodeGenerator(synthesizer)

    private fun indent(content: String, l: Int): String {
        val lines = content.lines()
        return if(lines.size == 1)
                lines.first()
            else
                lines.first() + "\n" + lines.drop(1).joinToString("\n").prependIndent(" ".repeat(4*l))
    }

    override fun genBoilerplate() = pkg() + "\n" + imports() + "\n"

    override fun genFactory() = """
        object ${className}Factory : SemanticObjectFactory<$className>() {
            override val query = "$stringQuery"
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
                props.nullable -> "val ${props.targetName} = ${TypeMapper.literalToType("lines.first().getLiteral(\"$sparql\")", props.kotlinType, true)}"
                else -> "val ${props.targetName} = ${TypeMapper.literalToType("lines.first().getLiteral(\"$sparql\")", props.kotlinType)}"
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
                "val ${props.targetName} = lines.mapNotNull { ${TypeMapper.literalToType("it.getLiteral(\"$sparql\")", props.kotlinType, true)} }.distinct()"
        }.values.joinToString("\n")

        return "\n// for higher cardinality properties, we have to collect all distinct values\n$assignments\n"
    }

    override fun genObject(): String {
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
                info.functional && info.nullable -> "${info.targetName}: ${info.kotlinType}?"
                info.functional -> "${info.targetName}: ${info.kotlinType}"
                else -> "${info.targetName}: List<${info.kotlinType}>"
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
                info.functional && info.nullable -> "${info.targetName}: ${info.kotlinType}?"
                info.functional -> "${info.targetName}: ${info.kotlinType}"
                else -> "${info.targetName}: List<${info.kotlinType}>"
            }
            args.add(arg)
        }

        return args.joinToString(", ")
    }

    override fun genSingletNonNullDataProperty(targetName: String, sparqlName: String, datatype: String): String {
        return """
            var $targetName: $datatype = $targetName
                set(value) {
                    ${indent(genDeleteCheck(targetName), 5)}

                    val newValueNode = ResourceFactory.createTypedLiteral(value).asNode()
                    val oldValueNode = ResourceFactory.createTypedLiteral(field).asNode()
                    ${indent(changeNodeGenerator.changeCN("uri", sparqlName, "oldValueNode", "newValueNode"), 5) }
                    Inkblot.changelog.add(cn)
                    
                    field = value
                    markDirty()
                }
        """.trimIndent()
    }

    override fun genSingletNullableDataProperty(targetName: String, sparqlName: String, datatype: String): String {
        return """
            var $targetName: $datatype? = $targetName
                set(value) {
                    ${indent(genDeleteCheck(targetName), 5)}
                    
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

    override fun genMultiDataProperty(targetName: String, sparqlName: String, datatype: String): String {
        val valueNode = "ResourceFactory.createTypedLiteral(data).asNode()"
        return """
            private val inkblt_$targetName = $targetName.toMutableList()

            val $targetName: List<$datatype>
                get() = inkblt_$targetName

            fun ${targetName}_add(data: $datatype) {
                ${indent(genDeleteCheck(targetName), 4)}
                inkblt_$targetName.add(data)
                
                ${indent(changeNodeGenerator.addCN("uri", sparqlName, valueNode), 4)}
                Inkblot.changelog.add(cn)
                markDirty()
            }

            fun ${targetName}_remove(data: $datatype) {
                ${indent(genDeleteCheck(targetName), 4)}
                inkblt_$targetName.remove(data)
                
                ${indent(changeNodeGenerator.removeCN("uri", sparqlName, valueNode), 4)}
                Inkblot.changelog.add(cn)
                markDirty()
            }
        """.trimIndent()
    }

    override fun genSingletNonNullObjectProperty(targetName: String, sparqlName: String, datatype: String): String {
        return """
            private var _inkbltRef_$targetName: String = $targetName
            var $targetName: $datatype
                get() = $datatype.loadFromURI(_inkbltRef_$targetName)
            set(value) {
                ${indent(genDeleteCheck(targetName), 4)}

                ${indent(changeNodeGenerator.changeCN("uri", sparqlName, "ResourceFactory.createResource(_inkbltRef_$targetName).asNode()", "ResourceFactory.createResource(value.uri).asNode()"), 4)}
                Inkblot.changelog.add(cn)
                
                _inkbltRef_$targetName = value.uri
                markDirty()
            }
        """.trimIndent()
    }

    override fun genSingletNullableObjectProperty(targetName: String, sparqlName: String, datatype: String): String {
        return """
            private var _inkbltRef_$targetName: String? = $targetName
            var $targetName: $datatype? = null
                get() {
                    return if(field == null && _inkbltRef_$targetName != null)
                        ${datatype}Factory.loadFromURI(_inkbltRef_$targetName!!)
                    else
                        field
                }
                set(value) {
                    ${indent(genDeleteCheck(targetName), 5)}
                    field = value

                    val oldValueNode = ResourceFactory.createResource(_inkbltRef_$targetName).asNode()
                    val newValueNode = ResourceFactory.createResource(value?.uri).asNode()

                    if(value == null) {
                        // Unset value
                        ${indent(changeNodeGenerator.removeCN("uri", sparqlName, "oldValueNode"), 6)}
                        Inkblot.changelog.add(cn)
                    }
                    else if(_inkbltRef_$targetName == null) {
                        // Pure insertion
                        ${indent(changeNodeGenerator.addCN("uri", sparqlName, "newValueNode"), 6)}
                        Inkblot.changelog.add(cn)
                    }
                    else {
                        // Change value
                        ${indent(changeNodeGenerator.changeCN("uri", sparqlName, "oldValueNode", "newValueNode"), 6)}
                        Inkblot.changelog.add(cn)
                    }
                    
                    _inkbltRef_$targetName = value?.uri
                    markDirty()
                }
        """.trimIndent()
    }

    override fun genMultiObjectProperty(targetName: String, sparqlName: String, datatype: String): String {
        val valueNode = "ResourceFactory.createResource(obj.uri).asNode()"
        return """
            private val _inkbltRef_$targetName = $targetName.toMutableSet()
            val $targetName: List<$datatype>
                get() = _inkbltRef_$targetName.map { ${datatype}Factory.loadFromURI(it) } // this is cached from DB so I hope it's fine?

            fun ${targetName}_add(obj: $datatype) {
                ${indent(genDeleteCheck(targetName), 4)}
                _inkbltRef_$targetName.add(obj.uri)

                ${indent(changeNodeGenerator.addCN("uri", sparqlName, valueNode), 4)}
                Inkblot.changelog.add(cn)
                markDirty()
            }

            fun ${targetName}_remove(obj: $datatype) {
                ${indent(genDeleteCheck(targetName), 4)}
                _inkbltRef_$targetName.remove(obj.uri)

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

    private fun pkg() = "package $pkg\n"
}