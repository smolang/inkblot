package inkblot.codegen

import inkblot.reasoning.VarDepEdge
import inkblot.reasoning.VariablePathAnalysis
import inkblot.reasoning.VariableProperties
import org.apache.jena.query.Query

class QuerySynthesizer(
    val anchor: String,
    private val variableInfo: Map<String, VariableProperties>,
    private val paths: VariablePathAnalysis
) {
    private var uniqueIDCtr = 0

    fun isSimple(v: String) = paths.simpleProperties.containsKey(v)

    fun simplePathUri(v: String): String {
        if(!paths.simpleProperties.containsKey(v))
            throw Exception("Variable ?$v is not a simple property")
        return paths.simpleProperties[v]!!
    }

    fun baseCreationUpdate(): String {
        val concreteLeaves = paths.concreteLeaves()
        val safeInitVars = variableInfo.filterValues{ it.functional && !it.nullable }.keys

        val variableInitializers = safeInitVars.map {v ->
            paths.pathsTo(v).joinToString(" ") { pathToSparqlSelect(it, "?$v")}
        }.joinToString(" ")

        val concreteInitializers = concreteLeaves.map {c ->
            paths.pathsToConcrete(c).joinToString(" ") { pathToSparqlSelect(it, null) }
        }.joinToString(" ")

        return "INSERT DATA { $variableInitializers $concreteInitializers }"

    }

    fun initializerUpdate(v: String): String {
        val insertSentences = paths.pathsTo(v).joinToString(" ") { pathToSparqlSelect(it, "?v") }
        return "INSERT DATA { $insertSentences }"
    }

    fun changeUpdate(v: String): String {
        val varPaths = paths.pathsTo(v)
        val deleteSentences = mutableListOf<String>()
        val insertSentences = mutableListOf<String>()
        val whereSentences = mutableListOf<String>()

        varPaths.forEach { path ->
            val lastEdge = path.last()
            val lastEdgeUri = lastEdge.dependency.p
            val lastNodeVar = if(path.size == 1) "?anchor" else freshVar()
            deleteSentences.add(tripleInGraph(lastNodeVar, lastEdgeUri, "?o", lastEdge.backward, lastEdge.dependency.inGraph))
            insertSentences.add(tripleInGraph(lastNodeVar, lastEdgeUri, "?n", lastEdge.backward, lastEdge.dependency.inGraph))
            if(path.size > 1)
                whereSentences.add(pathToSparqlSelect(path.dropLast(1), lastNodeVar))
        }

        return "DELETE { ${deleteSentences.joinToString(" ") } } INSERT { ${insertSentences.joinToString(" ")}} WHERE { ${whereSentences.joinToString(" ")} }"
    }

    fun addUpdate(v: String) = verbLastEdgeWherePath("INSERT", v)

    fun removeUpdate(v: String) = verbLastEdgeWherePath("DELETE", v)

    private fun verbLastEdgeWherePath(verb: String, v: String): String {
        val varPaths = paths.pathsTo(v)
        val verbSentences = mutableListOf<String>()
        val whereSentences = mutableListOf<String>()

        varPaths.forEach { path ->
            val lastEdge = path.last()
            val lastEdgeUri = lastEdge.dependency.p
            val lastNodeVar = if(path.size == 1) "?anchor" else freshVar()
            verbSentences.add(tripleInGraph(lastNodeVar, lastEdgeUri, "?o", lastEdge.backward, lastEdge.dependency.inGraph))

            if(path.size > 1)
                whereSentences.add(pathToSparqlSelect(path.dropLast(1), lastNodeVar))
        }

        val data = if(whereSentences.isEmpty()) " DATA" else ""
        val verbSection = "$verb$data { ${verbSentences.joinToString(" ")} } "
        val whereSection = if(whereSentences.isNotEmpty()) "WHERE { ${whereSentences.joinToString(" ")} }" else ""

        return verbSection + whereSection
    }

    private fun tripleInGraph(s: String, p: String, o: String, inverse: Boolean, graph: String?): String {
        val triple = if(inverse) "$o <$p> $s" else "$s <$p> $o."
        return if(graph == null)
            triple
        else
            "GRAPH <$graph> { $triple }"
    }

    private fun pathToSparqlSelect(path: List<VarDepEdge>, lastNodeVar: String?) : String {
        // nicer rendering for easy paths (no backward edges, all in the same graph)
        return if(path.isEmpty())
            throw Exception("cannot convert empty path to sparql")
        else if(path.all { !it.backward } && path.map{ it.dependency.inGraph }.distinct().size == 1) {
            // we have established that there is only one distinct graph, so we can use the first one
            val graph = path.first().dependency.inGraph
            val closingBrackets = "]".repeat(path.size-1)
            val basePath = "?anchor " + path.joinToString(" [") { "<${it.dependency.p}>" } + " ${lastNodeVar ?: ("<" + path.last().dependency.o + ">")}$closingBrackets."

            if(graph == null)
                basePath
            else
                "GRAPH <$graph> { $basePath }"
        }
        // ugly but more generic rendering
        else {
            val anchor = if(path.first().backward) path.first().dependency.o else path.first().dependency.s
            val last = if(path.last().backward) path.last().dependency.s else path.last().dependency.o

            val varMapping = path.flatMap { listOf(it.dependency.s, it.dependency.o) }.distinct().associateWith { freshVar() }.toMutableMap()
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

    private fun freshVar(): String {
        uniqueIDCtr += 1
        return "?inkblt$uniqueIDCtr"
    }
}