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

        val requiredVariablePaths = assignableVars.flatMap { paths.pathsToVariable(it) }.filter { path -> path.none { it.dependency.optional } }
        val requiredConcreteLeavedPaths = paths.concreteLeaves().flatMap { paths.pathsToConcrete(it) }.filter { path -> path.none { it.dependency.optional } }
        val requiredAnonymousVariablePaths = emptyList<List<VarDepEdge>>() // TODO

        val varEdges = requiredVariablePaths.flatten().map{ it.dependency }.toSet()
        val conEdges = requiredConcreteLeavedPaths.flatten().map{ it.dependency }.toSet()
        val anoEdges = requiredAnonymousVariablePaths.flatten().map{ it.dependency }.toSet()
        val requiredEdges = varEdges + conEdges + anoEdges

        val insertStatements = renderEdgeSet(requiredEdges, assignableVars)

        return "INSERT DATA { $insertStatements }"
    }

    override fun initializerUpdate(v: String): String {
        // figure out in which optional contexts v is safe
        // get all edges along paths to v
        // recursively expand using edges considered safe not already in edge set
        // provide copy of select query in where clause to provide variable bindings?
        return ""
    }

    override fun changeUpdate(v: String): String {
        // create insert / delete clauses as in old synthesizer
        // recursively expand WHERE clause to bind variables appropriately?
        val varPaths = paths.pathsToVariable(v)
        val deleteSentences = mutableListOf<String>()
        val insertSentences = mutableListOf<String>()
        val whereSentences = mutableListOf<String>()

        varPaths.forEach { path ->
            val lastEdge = path.last()
            val lastEdgeUri = lastEdge.dependency.p
            val lastNodeVar = if(path.size == 1) "?anchor" else "?${lastEdge.destination}"
            deleteSentences.add(tripleInGraph(lastNodeVar, lastEdgeUri, "?o", lastEdge.backward, lastEdge.dependency.inGraph))
            insertSentences.add(tripleInGraph(lastNodeVar, lastEdgeUri, "?n", lastEdge.backward, lastEdge.dependency.inGraph))
        }

        return "DELETE { ${deleteSentences.joinToString(" ")} } INSERT { ${insertSentences.joinToString(" ")}} WHERE {}"
    }

    override fun addUpdate(v: String): String {
        return ""
    }

    override fun removeUpdate(v: String): String {
        return ""
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
                else if (it.s == anchor || assignableVars.contains(it.s))
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
                else if(it.o == anchor || assignableVars.contains(it.o))
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