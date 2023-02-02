package inkblot.reasoning

import inkblot.codegen.PropertyConfig
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.Query
import org.apache.jena.query.QuerySolution
import org.apache.jena.sparql.exec.http.QueryExecutionHTTPBuilder

class ForbiddenMagicAnalysis(private val endpoint: String) {

    private val functionalProperties = getFunctionalProperties()
    private val inverseFunctionalProperties = getFunctionalProperties(true)

    fun divine(query: Query, anchor: String): Map<String, PropertyConfig> {
        if(!query.isSelectType)
            throw Exception("Query should be a SELECT")
        query.resetResultVars()

        val vars = query.resultVars.toSet() - anchor
        val nonNullable = mutableSetOf<String>()
        val functional = mutableSetOf<String>()

        println("Trying to divine info for these variables: ${vars.joinToString(", ")}")

        val visitor = DependencyPathVisitor()
        query.queryPattern.visit(visitor)

        val dependencyPaths = VariableDependencePaths.variableDependencyPaths(anchor, vars, visitor.variableDependencies)
        dependencyPaths.forEach { (k, v) ->
            if(v.isEmpty())
                throw Exception("Unable to derive dependency path from ?$anchor to ?$k")

            v.forEach { path ->
                val (min, max) = pathMultiplicity(path)

                // if there is a safe path to the variable, the variable has to be instantiated and the corresponding property cannot be null
                if(min > 0.0)
                    nonNullable.add(k)

                if(max == 1.0)
                    functional.add(k)
            }
        }

        val rangeRelevantPredicates = visitor.variableInRangesOf.values.flatten().toSet()
        val domainRelevantPredicates = visitor.variableInDomainsOf.values.flatten().toSet()
        val rangePredicatesSparql = rangeRelevantPredicates.joinToString(" "){ "<$it>" }
        val domainPredicatesSparql = domainRelevantPredicates.joinToString(" "){ "<$it>" }

        // there might be a SPARQL injection here, but they are probably all over the place anyway so ¯\_(ツ)_/¯
        // predicates are also kind of trusted input since they come from a valid query object - should be fine?
        val domainQuery = ParameterizedSparqlString("SELECT ?rel ?dom WHERE { VALUES ?rel { $domainPredicatesSparql } ?rel <http://www.w3.org/2000/01/rdf-schema#domain> ?dom }")
        val rangeQuery = ParameterizedSparqlString("SELECT ?rel ?range WHERE { VALUES ?rel { $rangePredicatesSparql } ?rel <http://www.w3.org/2000/01/rdf-schema#range> ?range }")

        val predicateDomains = queryExecutionHelper(domainQuery.asQuery()).groupBy { it.getResource("rel").uri }.mapValues { (_, v) -> v.map{ it.getResource("dom").uri }.toSet() }
        val predicateRanges = queryExecutionHelper(rangeQuery.asQuery()).groupBy { it.getResource("rel").uri }.mapValues { (_, v) -> v.map{ it.getResource("range").uri }.toSet() }

        vars.forEach { v ->
            println("Type analysis for ?$v")

            if(visitor.variableInRangesOf.containsKey(v)) {
                val ranges = visitor.variableInRangesOf[v]!!
                val types = ranges.flatMap { predicateRanges[it] ?: emptySet() }.distinct()
                println("Variable is constrained by ranges of predicates: ${ranges.joinToString(", ")}")
                println("These predicates correspond to these types: ${types.joinToString(", ")}")
            }

            if(visitor.variableInDomainsOf.containsKey(v)) {
                val domains = visitor.variableInDomainsOf[v]!!
                val types = domains.flatMap { predicateDomains[it] ?: emptySet() }.distinct()
                println("Variable is constrained by domains of predicates: ${visitor.variableInDomainsOf[v]!!.joinToString(", ")}")
                println("These predicates correspond to these types: ${types.joinToString(", ")}")
            }
            println()
        }

        return vars.associateWith {
            val divinedMultiplicity = when {
                functional.contains(it) && nonNullable.contains(it) -> "!"
                functional.contains(it) -> "?"
                else -> "*"
            }
            PropertyConfig(it, "Unit", divinedMultiplicity)
        }
    }

    private fun pathMultiplicity(path: List<VarDepEdge>): Pair<Double, Double> {
        var max = 1.0
        var min = 1.0

        for (i in (path.indices)) {
            val edge = path[i]

            // forward edge
            if(!edge.backward) {
                val edgeMax = if(functionalProperties.contains(edge.dependency.p)) 1.0 else Double.POSITIVE_INFINITY
                val edgeMin = if(edge.dependency.optional) 0.0 else 1.0
                max *= edgeMax
                min *= edgeMin
            }
            // backwards edges are not yet really supported
            else {
                if(edge.dependency.optional)
                    min = 0.0
                if(!inverseFunctionalProperties.contains(edge.dependency.p))
                    max = Double.POSITIVE_INFINITY
            }
        }

        return Pair(min, max)
    }

    private fun getFunctionalProperties(inverse: Boolean = false): Set<String> {
        val queryStr = if(inverse)
            "SELECT ?r WHERE { ?r a <http://www.w3.org/2002/07/owl#InverseFunctionalProperty> }"
        else
            "SELECT ?r WHERE { ?r a <http://www.w3.org/2002/07/owl#FunctionalProperty> }"

        val template = ParameterizedSparqlString(queryStr)

        return queryExecutionHelper(template.asQuery()).map { it.getResource("r").uri }.toSet()
    }

    private fun queryExecutionHelper(query: Query): Sequence<QuerySolution> {
        val builder = QueryExecutionHTTPBuilder.create()
        builder.endpoint(endpoint)
        builder.query(query)
        val res = builder.select()
        val seq = res.asSequence()
        res.close()
        return seq
    }
}

