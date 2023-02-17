package inkblot.codegen

import inkblot.reasoning.VarDepEdge
import inkblot.reasoning.VarDependency
import inkblot.reasoning.VariablePathAnalysis
import inkblot.reasoning.VariableProperties

class NewQuerySynthesizer(anchor: String, vars: Map<String, VariableProperties>, paths: VariablePathAnalysis): AbstractQuerySynthesizer(anchor, vars, paths) {
    override fun baseCreationUpdate(): String {
        // we have to initialize everything that's not optional
        // concretely, that's three different things:
        // 1. non-nullable variables that we'll assign concrete values to
        // 2. concrete leaves
        // 3. anonymous nodes that are not part of any path leading to either (1) or (2)

        val assignableVars = variableInfo.filterValues{ it.functional && !it.nullable }.keys
        val requiredResultSetVars = paths.resultVars.intersect(paths.safeVariables()) - anchor

        val unboundRequired = requiredResultSetVars.minus(assignableVars)
        if(unboundRequired.isNotEmpty())
            throw Exception("SPARQL variables ${unboundRequired.joinToString(", ")} are not optional but considered optional according to configured multiplicity")

        val requiredEdges = bindingsFor(setOf(anchor), "")
        val insertStatements = renderEdgeSet(requiredEdges, assignableVars)

        return "INSERT DATA { $insertStatements }"
    }

    // TODO: can we replace most of this with an invocation to bindingsFor?
    override fun initializerUpdate(v: String): String {
        // v is safe in all contexts that have the bindingContext as a prefix
        // edges from parent contexts of the binding context are safe
        val bindingContext = paths.definingContextForVariable(v)

        // all paths to v that are safe in this context
        val requiredPaths = paths.pathsToVariable(v).filter { path -> path.all { bindingContext.startsWith(it.dependency.optionalContexts) } }

        // create concrete leaved paths that intersect with this property and are (newly) safe in this context
        val requiredConcreteLeavedPaths = paths.concreteLeaves().flatMap { paths.pathsToConcrete(it) }.filter { path ->
            path.any { it.dependency.optional } && path.all { bindingContext.startsWith(it.dependency.optionalContexts) } && requiredPaths.any{ pPath -> pPath.toSet().intersect(path.toSet()).isNotEmpty() }
        }

        //println("Newly safe concrete leaved paths for $v: $requiredConcreteLeavedPaths")

        // maybe we'll worry about variable-leaved paths here at some point
        // TODO

        val varEdges = requiredPaths.flatten().map{ it.dependency }.toSet()
        val conEdges = requiredConcreteLeavedPaths.flatten().map{ it.dependency }.toSet()
        val requiredEdges = varEdges + conEdges

        // recursively expand using edges considered safe not already in edge set
        // provide copy of select query in where clause to provide variable bindings?
        val insertStatements = renderEdgeSet(requiredEdges, setOf(v))
        return "INSERT DATA { $insertStatements }"
    }

    // TODO: more accurate binding using concrete leaves as well
    private fun genericUpdate(v: String, delete: Boolean, insert: Boolean): String {
        val deleteSentences = mutableListOf<String>()
        val insertSentences = mutableListOf<String>()
        val requiredVarBindings = mutableSetOf<String>()
        val lastEdges = mutableSetOf<VarDependency>()

        val lastEdgesOnPaths = paths.pathsToVariable(v).map { it.last().dependency }

        val neighborhood = paths.edgesFor(v)
        neighborhood.forEach { edge ->
            val backwards = edge.s == v
            val sourceNode = if(backwards) edge.oNode else edge.sNode

            val source = when {
                sourceNode.isURI -> "<${sourceNode.uri}>"
                sourceNode.isLiteral -> "\"${sourceNode.literal}\""
                sourceNode.isVariable && sourceNode.name == anchor -> "?anchor"
                sourceNode.isVariable -> {
                    requiredVarBindings.add(sourceNode.name)
                    "?${sourceNode.name}"
                }
                else -> throw Exception("Unknown Node type in $edge")
            }

            // when deleting, we only want to delete what's absolutely necessary, e.g. leaving type labels of removed nodes intact
            if(lastEdgesOnPaths.contains(edge))
                deleteSentences.add(tripleInGraph(source, edge.p, "?o", backwards, edge.inGraph))
            insertSentences.add(tripleInGraph(source, edge.p, "?n", backwards, edge.inGraph))
            lastEdges.add(edge)
        }

        val ctx = paths.definingContextForVariable(v)
        val bindings = bindingsFor(requiredVarBindings, ctx)
        val whereSentences = renderEdgeSet(bindings, requiredVarBindings)

        if(!delete && !insert)
            throw Exception("Not inserting anything and not deleting anything is probably unintentional")

        var stmt = ""
        if(delete)
            stmt += "DELETE { ${deleteSentences.joinToString(" ")} } "
        if(insert)
            stmt += "INSERT { ${insertSentences.joinToString(" ")} } "
        stmt += "WHERE { $whereSentences }"

        return stmt
    }

    override fun changeUpdate(v: String) = genericUpdate(v, delete = true, insert = true)

    override fun addUpdate(v: String) = genericUpdate(v, delete = false, insert = true)

    override fun removeUpdate(v: String) = genericUpdate(v, delete = true, insert = false)

    private fun bindingsFor(vars: Set<String>, optionalCtx: String): Set<VarDependency> {
        val toBind = vars.toMutableSet()
        val bound = mutableSetOf<String>()
        val bindings = mutableSetOf<VarDependency>()

        println("Gathering bindings for: $vars")

        while (toBind.isNotEmpty()) {
            val v = toBind.first()
            toBind.remove(v)
            bound.add(v)

            println("Binding: $v")

            val edges = paths.edgesFor(v).filter { optionalCtx.startsWith(it.optionalContexts) }
            bindings.addAll(edges)
            println("Edges: $edges")
            val newVars = edges.flatMap {
                if(it.oNode.isVariable && it.sNode.isVariable)
                    listOf(it.o, it.s)
                else if(it.oNode.isVariable)
                    listOf(it.o)
                else if(it.sNode.isVariable)
                    listOf(it.s)
                else emptyList()
            }.toSet().minus(bound).filter { it != anchor }
            println("New vars to bind: $newVars")
            toBind.addAll(newVars)
        }

        return bindings
    }

    private fun renderEdgeSet(edgeSet: Set<VarDependency>, assignableVars: Set<String>): String {
        // variable to blank node mapping
        var blankNodeCounter = 0
        val blankNodeMap = mutableMapOf<String, String>()

        // collect by graph name and render into one block of triples per graph
        val byGraph = edgeSet.groupBy { it.inGraph ?: "inkblot:default" }.mapValues { (_, edges) ->
            edges.joinToString(" ") {
                val s = if(it.sNode.isURI)
                    "<${it.s}>"
                else if(it.s == anchor)
                    "?anchor"
                else if (assignableVars.contains(it.s))
                    "?${it.s}"
                else {
                    if(blankNodeMap.containsKey(it.s))
                        blankNodeMap[it.s]!!
                    else {
                        val bnode = "_:b$blankNodeCounter"
                        blankNodeCounter += 1
                        blankNodeMap[it.s] = bnode
                        bnode
                    }
                }

                val o = if(it.oNode.isURI)
                    "<${it.o}>"
                else if(it.oNode.isLiteral)
                    "\"${it.o}\""
                else if(it.o == anchor)
                    "?anchor"
                else if(assignableVars.contains(it.o))
                    "?${it.o}"
                else {
                    if(blankNodeMap.containsKey(it.o))
                        blankNodeMap[it.o]!!
                    else {
                        val bnode = "_:b$blankNodeCounter"
                        blankNodeCounter += 1
                        blankNodeMap[it.o] = bnode
                        bnode
                    }
                }

                "$s <${it.p}> $o."
            }
        }

        return byGraph.map { (graph, block) ->
            if(graph == "inkblot:default")
                block
            else
                "GRAPH <$graph> { $block }"
        }.joinToString(" ")
    }
}