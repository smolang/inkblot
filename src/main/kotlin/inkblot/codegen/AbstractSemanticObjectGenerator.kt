package inkblot.codegen

import inkblot.reasoning.VariableProperties
import org.apache.jena.query.Query

abstract class AbstractSemanticObjectGenerator(
    protected val className: String,
    protected val query: Query,
    protected val anchor: String,
    protected val namespace: String,
    protected val variableInfo: Map<String, VariableProperties>
) {
    protected val stringQuery = prettifySparql(query)
    protected val synthesizer = QuerySynthesizer(query, anchor, variableInfo)

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
    }

    fun gen(): String {
        return genBoilerplate() + "\n" + genFactory() + "\n" + genObject()
    }

    protected abstract fun genBoilerplate(): String
    protected abstract fun genFactory(): String
    protected abstract fun genObject(): String

    protected fun genProperties() = variableInfo.keys.joinToString("\n\n") { genProperty(it) }

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
                genSingletNullableDataProperty(varProps.targetName, varName, varProps.kotlinType)
            else
                genSingletNonNullDataProperty(varProps.targetName, varName, varProps.kotlinType)
        } else {
            genMultiDataProperty(varProps.targetName, varName, varProps.kotlinType)
        }
    }

    private fun genObjectProperty(varName: String): String {
        val varProps = variableInfo[varName]!!
        return if(varProps.functional) {
            if(varProps.nullable)
                genSingletNullableObjectProperty(varProps.targetName, varName, varProps.kotlinType)
            else
                genSingletNonNullObjectProperty(varProps.targetName, varName, varProps.kotlinType)
        } else {
            genMultiObjectProperty(varProps.targetName, varName, varProps.kotlinType)
        }
    }

    protected abstract fun genSingletNullableObjectProperty(targetName: String, sparqlName: String, datatype: String): String
    protected abstract fun genSingletNonNullObjectProperty(targetName: String, sparqlName: String, datatype: String): String
    protected abstract fun genMultiObjectProperty(targetName: String, sparqlName: String, datatype: String): String

    protected abstract fun genSingletNullableDataProperty(targetName: String, sparqlName: String, datatype: String): String
    protected abstract fun genSingletNonNullDataProperty(targetName: String, sparqlName: String, datatype: String): String
    protected abstract fun genMultiDataProperty(targetName: String, sparqlName: String, datatype: String): String
}