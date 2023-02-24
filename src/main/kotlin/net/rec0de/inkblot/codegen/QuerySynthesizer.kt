package net.rec0de.inkblot.codegen

import net.rec0de.inkblot.reasoning.VarDepEdge
import net.rec0de.inkblot.reasoning.VariablePathAnalysis
import net.rec0de.inkblot.reasoning.VariableProperties

class QuerySynthesizer(
    anchor: String,
    variableInfo: Map<String, VariableProperties>,
    paths: VariablePathAnalysis,
    queryMap: MutableMap<String, String>
): AbstractQuerySynthesizer(anchor, variableInfo, paths, queryMap) {
    override fun synthBaseCreationUpdate(): String {
        val concreteLeaves = paths.concreteLeaves()
        val safeInitVars = variableInfo.filterValues{ it.functional && !it.nullable }.keys

        val variableInitializers = safeInitVars.map {v ->
            paths.pathsToVariable(v).joinToString(" ") { pathToSparqlSelect(it, "?$v")}
        }.joinToString(" ")

        val safePathsToConcrete = concreteLeaves.map{ paths.pathsToConcrete(it) }.flatten().filter { path -> path.all{ !it.dependency.optional } }
        val concreteInitializers = safePathsToConcrete.joinToString(" "){ pathToSparqlSelect(it, null) }

        return "INSERT DATA { $variableInitializers $concreteInitializers }"
    }

    override fun synthInitializerUpdate(v: String): String {
        val insertSentences = paths.pathsToVariable(v).joinToString(" ") {
            pathToSparqlSelect(it, "?v")
        }
        return "INSERT DATA { $insertSentences }"
    }

    override fun synthChangeUpdate(v: String): String {
        val varPaths = paths.pathsToVariable(v)
        val deleteSentences = mutableListOf<String>()
        val insertSentences = mutableListOf<String>()
        val whereSentences = mutableListOf<String>()

        varPaths.forEach { path ->
            val lastEdge = path.last()
            val lastEdgeUri = lastEdge.dependency.p
            val lastNodeVar = if(path.size == 1) "?anchor" else "?${lastEdge.source}"
            deleteSentences.add(tripleInGraph(lastNodeVar, lastEdgeUri, "?o", lastEdge.backward, lastEdge.dependency.inGraph))
            insertSentences.add(tripleInGraph(lastNodeVar, lastEdgeUri, "?n", lastEdge.backward, lastEdge.dependency.inGraph))
            if(path.size > 1)
                whereSentences.add(pathToSparqlSelect(path.dropLast(1), lastNodeVar))
        }

        return "DELETE { ${deleteSentences.joinToString(" ") } } INSERT { ${insertSentences.joinToString(" ")}} WHERE { ${whereSentences.joinToString(" ")} }"
    }

    override fun synthAddUpdate(v: String) = verbLastEdgeWherePath("INSERT", v)

    override fun synthRemoveUpdate(v: String) = verbLastEdgeWherePath("DELETE", v)

    private fun verbLastEdgeWherePath(verb: String, v: String): String {
        val varPaths = paths.pathsToVariable(v)
        val verbSentences = mutableListOf<String>()
        val whereSentences = mutableListOf<String>()

        varPaths.forEach { path ->
            val lastEdge = path.last()
            val lastEdgeUri = lastEdge.dependency.p
            val lastNodeVar = if(path.size == 1) "?anchor" else "?${lastEdge.source}"
            verbSentences.add(tripleInGraph(lastNodeVar, lastEdgeUri, "?o", lastEdge.backward, lastEdge.dependency.inGraph))

            if(path.size > 1)
                whereSentences.add(pathToSparqlSelect(path.dropLast(1), lastNodeVar))
        }

        val data = if(whereSentences.isEmpty()) " DATA" else ""
        val verbSection = "$verb$data { ${verbSentences.joinToString(" ")} } "
        val whereSection = if(whereSentences.isNotEmpty()) "WHERE { ${whereSentences.joinToString(" ")} }" else ""

        return verbSection + whereSection
    }

    private fun pathToSparqlSelect(path: List<VarDepEdge>, lastNodeVar: String?) : String {
        // nicer rendering for easy paths (no backward edges, all in the same graph)
        return if(path.isEmpty())
            throw Exception("cannot convert empty path to sparql")
        /*else if(path.all { !it.backward } && path.map{ it.dependency.inGraph }.distinct().size == 1) {
            // we have established that there is only one distinct graph, so we can use the first one
            val graph = path.first().dependency.inGraph
            val closingBrackets = "]".repeat(path.size-1)
            val basePath = "?anchor " + path.joinToString(" [") { "<${it.dependency.p}>" } + " ${lastNodeVar ?: ("<" + path.last().dependency.o + ">")}$closingBrackets."

            if(graph == null)
                basePath
            else
                "GRAPH <$graph> { $basePath }"
        }*/
        // ugly but more generic rendering
        else {
            val anchor = if(path.first().backward) path.first().dependency.o else path.first().dependency.s
            val last = if(path.last().backward) path.last().dependency.s else path.last().dependency.o

            val varMapping = path.flatMap { listOf(it.dependency.s, it.dependency.o) }.distinct().associateWith { "?$it" }.toMutableMap()
            varMapping[anchor] = "?anchor"
            varMapping[last] = lastNodeVar ?: "<$last>"

            // this will wrap every triple in its own graph block, which is incredibly ugly but should work
            return path.map {
                val s = varMapping[it.dependency.s]!!
                val o = varMapping[it.dependency.o]!!
                tripleInGraph(s, it.dependency.p, o, false, it.dependency.inGraph)
            }.joinToString(" ")
        }
    }
}