package inkblot.reasoning

import inkblot.Inkblot
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.Query
import org.apache.jena.query.QuerySolution
import org.apache.jena.sparql.exec.http.QueryExecutionHTTPBuilder

object ParameterAnalysis {

    private val functionalProperties = getFunctionalProperties()
    private val inverseFunctionalProperties = getFunctionalProperties(true)

    fun main() {
        //val query = ParameterizedSparqlString("SELECT ?bike ?mfg WHERE { ?bike bk:hasFrame ?frame. ?bell bk:mfg ?mfg. ?mfg bk:mfg ?frame. ?bell bk:altMfg ?mfg }")
        //val query = ParameterizedSparqlString("SELECT ?wheel ?dia ?mfgD ?mfgN WHERE { ?wheel a bk:wheel; bk:diameter ?dia. OPTIONAL { ?wheel bk:mfgDate ?mfgD } OPTIONAL {?wheel bk:mfgName ?mfgN } }")
        val query = ParameterizedSparqlString("SELECT ?bike ?mfg ?fw ?bw ?bells WHERE { ?bike a bk:bike; bk:hasFrame [bk:frontWheel ?fw] OPTIONAL { ?bike bk:hasFrame [bk:backWheel ?bw] } OPTIONAL { ?bike bk:mfgDate ?mfg } OPTIONAL { ?bike bk:hasFrame [bk:hasBell ?bells] } }")

        query.setNsPrefix("bk", "http://rec0de.net/ns/bike#")
        deriveTypes(query.asQuery(), "bike")
    }
    fun deriveTypes(query: Query, anchor: String) {
        if(!query.isSelectType)
            throw Exception("Query should be a SELECT")
        query.resetResultVars()

        val vars = query.resultVars.toSet()
        val results = vars.associateWith { VariableProperties() }

        println("Trying to derive types for these variables:")
        println(vars)
        println()

        val visitor = TypeDerivationVisitor(vars.toSet(), functionalProperties, inverseFunctionalProperties)
        query.queryPattern.visit(visitor)

        println()

        val dependencyPaths = SimplePaths.variableDependencyPaths(anchor, vars, visitor.variableDependencies)
        dependencyPaths.forEach { (k, v) ->
            if(v.isEmpty())
                throw Exception("Unable to derive dependency path from ?$anchor to ?$k")

            println("Dependency paths from $anchor to $k:")

            v.forEach { path ->
                println(path.joinToString("->"))
                val (min, max) = pathMultiplicity(anchor, path)

                println("\tmultiplicity analysis: min $min max $max")

                if(min == 0.0)
                    println("\toptional path")
                // if there is a safe path to the variable, the variable has to be instantiated and the corresponding property cannot be null
                else {
                    println("\tsafe path")
                    results[k]!!.nullable = false
                }

                if(max == 1.0) {
                    println("\tfunctional path")
                    results[k]!!.functional = true
                }
            }

        }

        // TODO: find 'anchor subgraph', ie nodes that have to be deleted with the object
        println()
        val anonymousVariables = visitor.variableDependencies.flatMap { listOf(it.s, it.o) }.toSet() - vars
        println("Query contains anonymous variables: ${anonymousVariables.joinToString(", ")}")
        val anonDependencyPaths =
            SimplePaths.variableDependencyPaths(anchor, anonymousVariables, visitor.variableDependencies)
        // TODO: find out which anonymous variables are equivalent
        anonDependencyPaths.forEach { (k, v) ->
            if(v.isEmpty())
                throw Exception("Unable to derive dependency path from ?$anchor to ?$k")

            println("Dependency paths from $anchor to $k:")

            v.forEach { path ->
                println(path.joinToString("->"))
                val (min, max) = pathMultiplicity(anchor, path)

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

        // there might be a SPARQL injection here but they are probably all over the place anyway so ¯\_(ツ)_/¯
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

    private fun pathMultiplicity(anchor: String, path: List<VarDependency>): Pair<Double, Double> {
        var prevNode = anchor
        var max = 1.0
        var min = 1.0

        for (i in (path.indices)) {
            val edge = path[i]

            // forward edge
            if(edge.s == prevNode) {
                max *= edge.max
                min *= edge.min
                prevNode = edge.o
            }
            // backwards edges are not yet really supported
            else if(edge.o == prevNode) {
                if(edge.optional)
                    min = 0.0
                if(!edge.inverseFunctional)
                    max = Double.POSITIVE_INFINITY
                prevNode = edge.s
            }
            else
                throw Exception("Disconnected path")
        }

        return Pair(min, max)
    }

    /*private fun pathIsFunctional(anchor: String, path: List<VarDependency>): Boolean {
        var prevNode = anchor
        for (i in (path.indices)) {
            val edge = path[i]

            // forward edge
            if(edge.s == prevNode) {
                if(!edge.functional)
                    return false
                prevNode = edge.o
            }
            // backward edge
            else if(edge.o == prevNode) {
                if(!edge.inverseFunctional)
                    return false
                prevNode = edge.s
            }
            else
                throw Exception("Disconnected path")
        }

        return true
    }*/

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

data class VariableProperties(var nullable: Boolean = true, var functional: Boolean = false, var datatype: String? = null)
data class VarDependency(
    val s: String,
    val p: String,
    val o: String,
    val optional: Boolean,
    val functional: Boolean,
    val inverseFunctional: Boolean,
    val min: Double,
    val max: Double = Double.POSITIVE_INFINITY
) {
    override fun toString(): String {
        return if(functional) "($s-$p->$o)f" else "($s-$p->$o)"
    }
}

object SimplePaths {

    private val visited = mutableSetOf<String>()
    private val currentPath = mutableListOf<String>()
    private val simplePaths = mutableListOf<List<String>>()
    private val adjacency = mutableMapOf<String,MutableSet<String>>()

    fun variableDependencyPaths(anchor: String, vars: Set<String>, dependencies: Set<VarDependency>): Map<String, List<List<VarDependency>>> {
        initAdjacencyMatrix(dependencies)
        val edgesBySourceNode = dependencies.groupBy { it.s }
        val vertexPaths = vars.filterNot { it == anchor }.associateWith { simplePaths(anchor, it) }

        val edgePaths = vertexPaths.mapValues { (_, paths) ->
            paths.flatMap { nodePathToEdgePaths(it, edgesBySourceNode) }
        }

        return edgePaths
    }

    // There may be two different edges leading from ?a to ?b
    // these are different paths for our purposes since they can convey different cardinality information
    // Since the simple path algorithm considers all edges equivalent, we have to do some post-processing
    // to 'multiplex' the choices of edges into different paths.
    // This is inherently exponential, but we trust it doesn't happen too much
    private fun nodePathToEdgePaths(nodePath: List<String>, edgesByStartNode: Map<String,List<VarDependency>>): List<List<VarDependency>> {
        var paths = mutableListOf<MutableList<VarDependency>>(mutableListOf())
        for(i in (1 until nodePath.size)) {
            val currentNode = nodePath[i-1]
            val nextNode = nodePath[i]
            val forwardEdges = edgesByStartNode[currentNode]?.filter { it.o == nextNode } ?: emptyList()
            val backwardEdges = edgesByStartNode[nextNode]?.filter { it.o == currentNode } ?: emptyList()
            val availableEdges = forwardEdges + backwardEdges

            // only one available edge, just add it to all the paths we are currently tracking
            if(availableEdges.size == 1)
                paths.forEach { it.add(availableEdges.first()) }
            // 'fork in the road', for every available edge, create a copy of all paths we are tracking
            else {
                val multiplexed = availableEdges.flatMap { edge ->
                    paths.map { (it + edge).toMutableList() }
                }
                paths = multiplexed.toMutableList()
            }
        }

        return paths
    }

    private fun initAdjacencyMatrix(dependencies: Set<VarDependency>) {
        adjacency.clear()
        dependencies.forEach {
            if(adjacency.containsKey(it.s))
                adjacency[it.s]!!.add(it.o)
            else
                adjacency[it.s] = mutableSetOf(it.o)

            // we also allow backwards edges in our paths
            if(adjacency.containsKey(it.o))
                adjacency[it.o]!!.add(it.s)
            else
                adjacency[it.o] = mutableSetOf(it.s)
        }
    }

    // Common algorithm to compute all simple paths between two vertices
    // (simple = each vertex occurs only once on the path)
    // see https://www.baeldung.com/cs/simple-paths-between-two-vertices
    private fun simplePaths(anchor: String, target: String): List<List<String>> {
        visited.clear()
        currentPath.clear()
        simplePaths.clear()

        dfs(anchor, target)
        return simplePaths.toList()
    }


    private fun dfs(from: String, to: String) {
        if(visited.contains(from))
            return

        if(from == to) {
            simplePaths.add(currentPath + from)
        }
        else {
            visited.add(from)
            currentPath.add(from)
            adjacency[from]?.forEach { dfs(it, to) }
            currentPath.removeLast()
            visited.remove(from)
        }
    }
}