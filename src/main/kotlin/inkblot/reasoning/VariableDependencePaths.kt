package inkblot.reasoning

object VariableDependencePaths {

    private val visited = mutableSetOf<String>()
    private val currentPath = mutableListOf<String>()
    private val simplePaths = mutableListOf<List<String>>()
    private val adjacency = mutableMapOf<String,MutableSet<String>>()

    fun pathsEquivalentUpToTargetVar(a: List<VarDepEdge>, b: List<VarDepEdge>, equivMap: Map<String, String>): Boolean {
        if(a.size != b.size)
            return false
        val zipped = a.zip(b)
        val (lastA, lastB) = zipped.last()
        val rest = zipped.dropLast(1)

        return rest.all{ it.first.equivalent(it.second, equivMap) } && lastA.equivalentUpToTarget(lastB, equivMap)
    }

    fun variableDependencyPaths(anchor: String, vars: Set<String>, dependencies: Set<VarDependency>): Map<String, List<List<VarDepEdge>>> {
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
    private fun nodePathToEdgePaths(nodePath: List<String>, edgesByStartNode: Map<String,List<VarDependency>>): List<List<VarDepEdge>> {
        var paths = mutableListOf<MutableList<VarDepEdge>>(mutableListOf())
        for(i in (1 until nodePath.size)) {
            val currentNode = nodePath[i-1]
            val nextNode = nodePath[i]
            val forwardEdges = edgesByStartNode[currentNode]?.filter { it.o == nextNode } ?: emptyList()
            val backwardEdges = edgesByStartNode[nextNode]?.filter { it.o == currentNode } ?: emptyList()
            val availableEdges = forwardEdges + backwardEdges

            // only one available edge, just add it to all the paths we are currently tracking
            if(availableEdges.size == 1)
                paths.forEach { it.add(
                    VarDepEdge(
                        availableEdges.first(),
                        backwardEdges.contains(availableEdges.first())
                    )
                ) }
            // 'fork in the road', for every available edge, create a copy of all paths we are tracking
            else {
                val multiplexed = availableEdges.flatMap { edge ->
                    val wrappedEdge = VarDepEdge(edge, backwardEdges.contains(edge))
                    paths.map { (it + wrappedEdge).toMutableList() }
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

data class VarDependency(
    val s: String,
    val p: String,
    val o: String,
    val optional: Boolean,
    val functional: Boolean,
    val inverseFunctional: Boolean,
    val min: Double,
    val max: Double
) {
    override fun toString(): String {
        return if(functional) "($s-$p->$o)f" else "($s-$p->$o)"
    }

    fun equivalent(other: VarDependency, equivMap: Map<String,String>): Boolean {
        return p == other.p && equivMap[s] == equivMap[other.s] && equivMap[o] == equivMap[other.o]
    }
}

data class VarDepEdge(val dependency: VarDependency, val backward: Boolean) {
    fun equivalent(other: VarDepEdge, equivMap: Map<String, String>) = dependency.equivalent(other.dependency, equivMap)

    fun equivalentUpToTarget(other: VarDepEdge, equivMap: Map<String, String>): Boolean {
        // conservative choice, necessary?
        return if(backward.xor(other.backward))
            false
        else if(backward)
            dependency.p == other.dependency.p && equivMap[dependency.o] == equivMap[other.dependency.o]
        else
            dependency.p == other.dependency.p && equivMap[dependency.s] == equivMap[other.dependency.s]
    }
}