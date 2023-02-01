package inkblot.reasoning

import inkblot.runtime.Inkblot
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.Query
import org.apache.jena.query.QuerySolution
import org.apache.jena.sparql.exec.http.QueryExecutionHTTPBuilder

object ParameterAnalysis {

    private val functionalProperties = getFunctionalProperties()
    private val inverseFunctionalProperties = getFunctionalProperties(true)

    fun deriveTypes(query: Query, anchor: String) {
        if(!query.isSelectType)
            throw Exception("Query should be a SELECT")
        query.resetResultVars()

        val vars = query.resultVars.toSet()

        println("Trying to derive info for these variables:")
        println(vars)
        println()

        val visitor = TypeDerivationVisitor(vars.toSet(), functionalProperties, inverseFunctionalProperties)
        query.queryPattern.visit(visitor)

        println()

        val dependencyPaths = VariableDependencePaths.variableDependencyPaths(anchor, vars, visitor.variableDependencies)
        dependencyPaths.forEach { (k, v) ->
            if(v.isEmpty())
                throw Exception("Unable to derive dependency path from ?$anchor to ?$k")

            println("Dependency paths from $anchor to $k:")

            v.forEach { path ->
                println(path.joinToString("->"))
                val (min, max) = pathMultiplicity(path)

                println("\tmultiplicity analysis: min $min max $max")

                if(min == 0.0)
                    println("\toptional path")
                // if there is a safe path to the variable, the variable has to be instantiated and the corresponding property cannot be null
                else {
                    println("\tsafe path")
                }

                if(max == 1.0) {
                    println("\tfunctional path")
                }
            }
        }

        // TODO: find 'anchor subgraph', ie nodes that have to be deleted with the object
        println()
        val anonymousVariables = visitor.variableDependencies.flatMap { listOf(it.s, it.o) }.toSet() - vars
        val variableEquivalenceMap = anonymousVariables.associateWith { it }.toMutableMap()
        val anonDependencyPaths =
            VariableDependencePaths.variableDependencyPaths(anchor, anonymousVariables, visitor.variableDependencies)
        println("Query contains anonymous variables: ${anonymousVariables.joinToString(", ")}")

        // ok let's work this out: when are two variables equivalent to each other?
        // -> if every path to the variable has an equivalent path leading to the other one, and vice versa
        // -> corollary: the number of paths to each variable has to be identical
        val coreAnonVars = anonymousVariables.toMutableList()
        var prevSize = coreAnonVars.size + 1
        var i: Int
        println("Variable equivalence analysis starting with ${coreAnonVars.size} distinct variables")

        while (coreAnonVars.size < prevSize) {
            prevSize = coreAnonVars.size
            i = 0
            while(i < coreAnonVars.size) {
                val variable = coreAnonVars[i]
                println("Checking equivalences for $variable")
                val referencePaths = anonDependencyPaths[variable]!!
                val candidates = coreAnonVars.filter { it != variable && anonDependencyPaths[it]?.size == referencePaths.size }
                val equivalent = candidates.filter {
                    val paths = anonDependencyPaths[it]!!
                    paths.all { p -> referencePaths.any { q -> VariableDependencePaths.pathsEquivalentUpToTargetVar(p, q, variableEquivalenceMap) } }
                }.toSet()
                equivalent.forEach {
                    println("\tFound equivalence to $it")
                    // update all variables currently pointing to the equivalent one to the canonical one
                    variableEquivalenceMap.filter { (_, v) -> v == it }.forEach { (k, _) -> variableEquivalenceMap[k] = variable }
                }
                // equivalence is symmetric, so we will always remove things AFTER the current coreAnonVars index
                // therefore it should be safe to remove during iteration?
                coreAnonVars.removeAll(equivalent)
                i += 1
            }
        }

        println("Variable equivalence analysis found ${coreAnonVars.size} distinct variables")
        println()

        // at this point, multiplicity of the canonical anonymous variables might be overly pessimistic
        // since we throw ways info found on redundant paths
        // but as we only use these to properly delete objects, I think we're fine

        coreAnonVars.forEach { v ->
            val paths = anonDependencyPaths[v]!!
            if(paths.isEmpty())
                throw Exception("Unable to derive dependency path from ?$anchor to ?$v")

            println("Dependency paths from $anchor to $v:")

            paths.forEach { path ->
                println(path.joinToString("->"))
                val (min, max) = pathMultiplicity(path)

                println("\tmultiplicity analysis: min $min max $max")

                if(min == 0.0)
                    println("\toptional path")
                // if there is a safe path to the variable, the variable has to be instantiated and the corresponding property cannot be null
                else {
                    println("\tsafe path")
                }

                if(max == 1.0) {
                    println("\tfunctional path")
                }
            }

        }

        println()

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
    }

    private fun pathMultiplicity(path: List<VarDepEdge>): Pair<Double, Double> {
        var max = 1.0
        var min = 1.0

        for (i in (path.indices)) {
            val edge = path[i]

            // forward edge
            if(!edge.backward) {
                max *= 1//edge.dependency.max
                min *= 1//edge.dependency.min
            }
            // backwards edges are not yet really supported
            else {
                if(edge.dependency.optional)
                    min = 0.0
                /*if(!edge.dependency.inverseFunctional)
                    max = Double.POSITIVE_INFINITY*/
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
        builder.endpoint(Inkblot.endpoint)
        builder.query(query)
        val res = builder.select()
        val seq = res.asSequence()
        res.close()
        return seq
    }
}

data class VariableProperties(
    val targetName: String,
    val nullable: Boolean,
    val functional: Boolean,
    val datatype: String,
    val isObjectReference: Boolean
)


