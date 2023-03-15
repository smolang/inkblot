package net.rec0de.inkblot.codegen

import net.rec0de.inkblot.reasoning.VariableProperties
import org.apache.jena.query.Query
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path

class SemanticObjectGenerator(
    className: String,
    query: Query,
    anchor: String,
    namespace: String,
    variableInfo: Map<String, VariableProperties>,
    synthesizer: AbstractQuerySynthesizer
): AbstractSemanticObjectGenerator(className, query, anchor, namespace, variableInfo, synthesizer) {

    private val changeNodeGenerator = ChangeNodeGenerator(synthesizer)
    private var pkg = "gen"

    override fun generateToFilesInPath(path: Path, options: List<String>) {
        val destination = File(path.toFile(), "$className.kt")

        // we might be able to guess the correct package name based on the output location
        if(options.any { it.startsWith("pkg=") })
            pkg = options.first { it.startsWith("pkg=") }.removePrefix("pkg=")
        else if(path.contains(Path("kotlin"))) {
            val parts = path.toList().map { it.toString() }
            val kotlinIdx = parts.lastIndexOf("kotlin")
            pkg = parts.subList(kotlinIdx+1, parts.size).joinToString(".")
            println("WARNING: no target package specified, guessing '$pkg' from target destination")
        }
        else
            println("WARNING: no target package specified, using 'gen'")

        destination.writeText(gen())
        println("Generated file '$className.kt'")

        if(options.contains("wrappers"))
            WrapperGenerator(className, variableInfo).generateToFilesInPath(path, pkg)
    }

    override fun genBoilerplate() = pkg() + "\n" + imports() + "\n"

    override fun genFactory(): String {
        val validateQueries = ValidatingSparqlGenerator.validatorQueriesFor(stringQuery, synthesizer, variableInfo.values).map {
            escape(prettifySparql(it))
        }.joinToString(",\n") { "\"$it\"" }
        return """
        object ${className}Factory : SemanticObjectFactory<$className>(
            listOf(
                ${indent(validateQueries, 4)}
            ),
            "$className"
        ) {
            override val anchor = "$anchor"
            override val query = ParameterizedSparqlString("${escape(stringQuery)}")
            ${indent(genInitializerQueries(), 3)}
            
            ${indent(genFactoryCreate(), 3)}
            
            ${indent(genFactoryInstantiate(), 3)}
        }
        """.trimIndent()
    }

    private fun genInitializerQueries(): String {
        val synthesizedCreateQuery = synthesizer.baseCreationUpdate()
        val base = "private val baseCreationUpdate = ParameterizedSparqlString(\"${escape(synthesizedCreateQuery)}\")"
        val vars = variableInfo.filter { (_, info) -> info.nullable || !info.functional }.map { it.value.sparqlName }

        val lines = vars.map { "private val initUpdate_$it = ParameterizedSparqlString(\"${escape(synthesizer.initializerUpdate(it))}\")" }

        return (lines + base).joinToString("\n")
    }

    private fun genFactoryCreate() : String {

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
            if(info.xsdType == "inkblot:rawObjectReference") {
                "template.setIri(\"$sparql\", ${info.targetName})"
            }
            else {
                val literal = TypeMapper.valueToLiteral(info.targetName, info.xsdType)
                "template.setParam(\"$sparql\", $literal)"
            }
        }

        return """
            fun create(${genExternalConstructorArgs()}): $className {
                val uri = "$namespace$anchor" + Inkblot.freshSuffixFor("$anchor")

                // set non-null parameters and create object
                val template = baseCreationUpdate.copy()
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
            else if(props.xsdType == "inkblot:rawObjectReference")
                "partialUpdate.setIri(\"v\", ${props.targetName})"
            else
                "partialUpdate.setParam(\"v\", ${TypeMapper.valueToLiteral(props.targetName, props.xsdType)})"

            """
                if(${props.targetName} != null) {
                    val partialUpdate = initUpdate_$sparql.copy()
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
                    "partialUpdate.setIri(\"v\", it.uri)"
                else if(props.xsdType == "inkblot:rawObjectReference")
                    "partialUpdate.setIri(\"v\", it)"
                else
                    "partialUpdate.setParam(\"v\", ${TypeMapper.valueToLiteral("it", props.xsdType)})"

            """
                ${props.targetName}.forEach {
                    val partialUpdate = initUpdate_$sparql.copy()
                    partialUpdate.setIri("anchor", uri)
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
                (props.isObjectReference || props.xsdType == "inkblot:rawObjectReference") && props.nullable -> "val ${props.targetName} = lines.first().getResource(\"$sparql\")?.uri"
                (props.isObjectReference || props.xsdType == "inkblot:rawObjectReference") -> "val ${props.targetName} = lines.first().getResource(\"$sparql\").uri"
                props.nullable -> "val ${props.targetName} = ${
                    TypeMapper.literalToType(
                        "lines.first().getLiteral(\"$sparql\")",
                        props.kotlinType,
                        true
                    )
                }"
                else -> "val ${props.targetName} = ${
                    TypeMapper.literalToType(
                        "lines.first().getLiteral(\"$sparql\")",
                        props.kotlinType
                    )
                }"
            }
        }.values.joinToString("\n")

        return "\n// for functional properties we can read the first only, as all others have to be the same\n$assignments\n"
    }

    private fun genFactoryInstantiateNonFunctional(): String {
        val nonFuncVars = variableInfo.filterValues { !it.functional }

        if(nonFuncVars.isEmpty())
            return ""

        val assignments = nonFuncVars.mapValues { (sparql, props) ->
            if(props.isObjectReference || props.xsdType == "inkblot:rawObjectReference")
                "val ${props.targetName} = lines.mapNotNull { it.getResource(\"$sparql\")?.uri }.distinct()"
            else
                "val ${props.targetName} = lines.mapNotNull { ${
                    TypeMapper.literalToType(
                        "it.getLiteral(\"$sparql\")",
                        props.kotlinType,
                        true
                    )
                } }.distinct()"
        }.values.joinToString("\n")

        return "\n// for higher cardinality properties, we have to collect all distinct values\n$assignments\n"
    }

    override fun genObject(): String {
        val constructorVars = variableInfo.values.joinToString(", "){ it.targetName }
        return """
            class $className internal constructor(${genInternalConstructorArgs()}) : SemanticObject(uri) {
                companion object {
                    fun create(${genExternalConstructorArgs()}) = ${className}Factory.create($constructorVars)
                    fun commitAndLoadAll() = ${className}Factory.commitAndLoadAll()
                    fun commitAndLoadSelected(filter: String) = ${className}Factory.commitAndLoadSelected(filter)
                    fun loadFromURI(uri: String) = ${className}Factory.loadFromURI(uri)
                }
                
                ${indent(genProperties(), 4)}
                
                ${indent(genMerge(), 4)}
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

    override fun genSingletNonNullDataProperty(properties: VariableProperties): String {
        val targetName = properties.targetName
        val datatype = properties.kotlinType
        val xsdType = properties.xsdType
        val sparqlName = properties.sparqlName
        return """
            var $targetName: $datatype = $targetName
                set(value) {
                    ${indent(genDeleteCheck(targetName), 5)}
                    ${indent(genRuntimeTypeChecks(properties, "value"), 5)}

                    val newValueNode = ${TypeMapper.valueToLiteral("value", xsdType)}.asNode()
                    val oldValueNode = ${TypeMapper.valueToLiteral("field", xsdType)}.asNode()
                    ${indent(changeNodeGenerator.changeCN("uri", sparqlName, "oldValueNode", "newValueNode"), 5) }
                    Inkblot.changelog.add(cn)
                    
                    field = value
                    markDirty()
                }
        """.trimIndent()
    }

    override fun genSingletNullableDataProperty(properties: VariableProperties): String {
        val targetName = properties.targetName
        val datatype = properties.kotlinType
        val xsdType = properties.xsdType
        val sparqlName = properties.sparqlName
        return """
            var $targetName: $datatype? = $targetName
                set(value) {
                    ${indent(genDeleteCheck(targetName), 5)}
                    
                    if(value == null) {
                        // Unset value
                        val oldValueNode = ${TypeMapper.valueToLiteral("field", xsdType)}.asNode()
                        ${indent(changeNodeGenerator.removeCN("uri", sparqlName, "oldValueNode"), 6)}
                        Inkblot.changelog.add(cn)
                    }
                    else if(field == null) {
                        // Pure insertion
                        val newValueNode = ${TypeMapper.valueToLiteral("value", xsdType)}.asNode()
                        ${indent(genRuntimeTypeChecks(properties, "value"), 6)}
                        ${indent(changeNodeGenerator.addCN("uri", sparqlName, "newValueNode"), 6)}
                        Inkblot.changelog.add(cn)
                    }
                    else {
                        // Update value
                        val oldValueNode = ${TypeMapper.valueToLiteral("field", xsdType)}.asNode()
                        val newValueNode = ${TypeMapper.valueToLiteral("value", xsdType)}.asNode()
                        ${indent(genRuntimeTypeChecks(properties, "value"), 6)}
                        ${indent(changeNodeGenerator.changeCN("uri", sparqlName, "oldValueNode", "newValueNode!!"), 6)}
                        Inkblot.changelog.add(cn)
                    }

                    field = value
                    markDirty()
                }
        """.trimIndent()
    }

    override fun genMultiDataProperty(properties: VariableProperties): String {
        val targetName = properties.targetName
        val datatype = properties.kotlinType
        val xsdType = properties.xsdType
        val sparqlName = properties.sparqlName
        val valueNode = "${TypeMapper.valueToLiteral("data", xsdType)}.asNode()"
        return """
            private val _inkblt_$targetName = $targetName.toMutableList()

            val $targetName: List<$datatype>
                get() = _inkblt_$targetName

            fun ${targetName}_add(data: $datatype) {
                ${indent(genDeleteCheck(targetName), 4)}
                ${indent(genRuntimeTypeChecks(properties, "data"), 4)}
                _inkblt_$targetName.add(data)
                
                ${indent(changeNodeGenerator.addCN("uri", sparqlName, valueNode), 4)}
                Inkblot.changelog.add(cn)
                markDirty()
            }

            fun ${targetName}_remove(data: $datatype) {
                ${indent(genDeleteCheck(targetName), 4)}
                _inkblt_$targetName.remove(data)
                
                ${indent(changeNodeGenerator.removeCN("uri", sparqlName, valueNode), 4)}
                Inkblot.changelog.add(cn)
                markDirty()
            }
        """.trimIndent()
    }

    override fun genSingletNonNullObjectProperty(properties: VariableProperties): String {
        val targetName = properties.targetName
        val datatype = properties.kotlinType
        val sparqlName = properties.sparqlName
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

    override fun genSingletNullableObjectProperty(properties: VariableProperties): String {
        val targetName = properties.targetName
        val datatype = properties.kotlinType
        val sparqlName = properties.sparqlName
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

                    if(value == null) {
                        // Unset value
                        val oldValueNode = ResourceFactory.createResource(_inkbltRef_$targetName).asNode()
                        ${indent(changeNodeGenerator.removeCN("uri", sparqlName, "oldValueNode"), 6)}
                        Inkblot.changelog.add(cn)
                    }
                    else if(_inkbltRef_$targetName == null) {
                        // Pure insertion
                        val newValueNode = ResourceFactory.createResource(value?.uri).asNode()
                        ${indent(changeNodeGenerator.addCN("uri", sparqlName, "newValueNode"), 6)}
                        Inkblot.changelog.add(cn)
                    }
                    else {
                        // Change value
                        val oldValueNode = ResourceFactory.createResource(_inkbltRef_$targetName).asNode()
                        val newValueNode = ResourceFactory.createResource(value?.uri).asNode()
                        ${indent(changeNodeGenerator.changeCN("uri", sparqlName, "oldValueNode", "newValueNode"), 6)}
                        Inkblot.changelog.add(cn)
                    }
                    
                    _inkbltRef_$targetName = value?.uri
                    markDirty()
                }
        """.trimIndent()
    }

    override fun genMultiObjectProperty(properties: VariableProperties): String {
        val targetName = properties.targetName
        val datatype = properties.kotlinType
        val sparqlName = properties.sparqlName
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

    private fun genMerge(): String {


        val overwriteNonNull = variableInfo.values.filter { !it.nullable && it.functional }.map {
            if(it.isObjectReference)
                "_inkbltRef_${it.targetName} = other._inkbltRef_${it.targetName} // avoids triggering lazy loading"
            else
                "${it.targetName} = other.${it.targetName}"
        }

        val overwriteNullable = variableInfo.values.filter { it.nullable && it.functional }.map {
            if(it.isObjectReference)
                "if(other._inkbltRef_${it.targetName} != null) ${it.targetName} = other.${it.targetName}"
            else
                "${it.targetName} = other.${it.targetName} ?: ${it.targetName}"
        }

        val addNonFunctional = variableInfo.values.filter { !it.functional }.map {
            if(it.isObjectReference)
                "_inkbltRef_${it.targetName}.addAll(other._inkbltRef_${it.targetName})"
            else
                "_inkblt_${it.targetName}.addAll(other._inkblt_${it.targetName})"
        }

        val lines = (overwriteNonNull + overwriteNullable + addNonFunctional).joinToString("\n")

        return """
            fun merge(other: $className) {
                if(deleted || other.deleted)
                    throw Exception("Trying to merge into/out of deleted objects <${'$'}uri> / <${'$'}{other.uri}>")
                
                ${indent(lines, 4)}
                
                other.delete(uri)
                markDirty()
            }
        """.trimIndent()
    }

    private fun genDeleteCheck(varName: String) = """
            if(deleted)
                throw Exception("Trying to set property '$varName' on deleted object <${'$'}uri>")
        """.trimIndent()

    private fun genRuntimeTypeChecks(variable: VariableProperties, varExp: String): String {
        return when(variable.xsdType.removePrefix("xsd:").removePrefix("http://www.w3.org/2001/XMLSchema#")) {
            "nonPositiveInteger" -> "if($varExp > BigInteger.ZERO) Inkblot.violation(VariableDomainViolation(this, \"${variable.targetName}\", \"${variable.xsdType}\"))"
            "negativeInteger" -> "if($varExp >= BigInteger.ZERO) Inkblot.violation(VariableDomainViolation(this, \"${variable.targetName}\", \"${variable.xsdType}\"))"
            "nonNegativeInteger" -> "if($varExp < BigInteger.ZERO) Inkblot.violation(VariableDomainViolation(this, \"${variable.targetName}\", \"${variable.xsdType}\"))"
            "positiveInteger" -> "if($varExp <= BigInteger.ZERO) Inkblot.violation(VariableDomainViolation(this, \"${variable.targetName}\", \"${variable.xsdType}\"))"
            else -> ""
        }
    }

    private fun imports(): String {
        val imports = mutableListOf(
            "net.rec0de.inkblot.runtime.*",
            "org.apache.jena.query.ParameterizedSparqlString",
            "org.apache.jena.query.QuerySolution",
            "org.apache.jena.rdf.model.ResourceFactory",
            "org.apache.jena.datatypes.xsd.XSDDatatype"
        )
        if(variableInfo.values.any { it.kotlinType == "BigInteger"})
            imports.add("import java.math.BigInteger")
        if(variableInfo.values.any { it.kotlinType == "BigDecimal"})
            imports.add("import java.math.BigDecimal")
        return imports.joinToString("\n"){ "import $it" }
    }

    private fun pkg() = "package $pkg\n"
}

fun escape(query: String) = query.replace("\"", "\\\"")

fun indent(content: String, l: Int): String {
    val lines = content.lines()
    return if(lines.size == 1)
        lines.first()
    else
        lines.first() + "\n" + lines.drop(1).joinToString("\n").prependIndent(" ".repeat(4*l))
}