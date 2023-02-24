package net.rec0de.inkblot.codegen

import net.rec0de.inkblot.reasoning.VariableProperties
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.Query

object ValidatingSparqlGenerator {

    fun validatorQueryFor(query: String, variableInfo: Collection<VariableProperties>): Query {
        // alright, so:
        // we know that the base query is a SELECT that doesn't contain any super weird SPARQL features
        // we should therefore be able to correctly insert a FILTER just before the last closing brace
        val filter = typeFilters(variableInfo)
        val filteredQuery = query.replace(Regex("}[^}]*$")){ res -> "FILTER ($filter) ${res.value}" }

        val q = ParameterizedSparqlString(filteredQuery)
        return q.asQuery()
    }

    private fun multiplicityFilters(variableInfo: Collection<VariableProperties>): String {
        return variableInfo.mapNotNull { multiplicityValidatingFilter(it) }.joinToString(" || ")
    }

    private fun typeFilters(variableInfo: Collection<VariableProperties>): String {
        return variableInfo.joinToString(" || ") { typeValidatingFilter(it) }
    }

    private fun multiplicityValidatingFilter(v: VariableProperties): String? {
        return when {
            else -> null
        }
    }

    private fun typeValidatingFilter(v: VariableProperties): String {
        return if(v.xsdType == "inkblot:rawObjectReference") {
            "(bound(?${v.sparqlName}) && !isIRI(?${v.sparqlName}))"
        }
        else if(v.isObjectReference)
            "(bound(?${v.sparqlName}) && !(isIRI(?${v.sparqlName}) && EXISTS { ?${v.sparqlName} a <${v.xsdType}> }))"
        else {
            val expandedXsd = v.xsdType.replace("xsd:", "http://www.w3.org/2001/XMLSchema#")
            "(bound(?${v.sparqlName}) && datatype(?${v.sparqlName}) != <$expandedXsd>)"
        }
    }
}