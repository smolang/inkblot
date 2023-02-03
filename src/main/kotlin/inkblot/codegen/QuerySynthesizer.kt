package inkblot.codegen

import inkblot.reasoning.VarDepEdge
import inkblot.reasoning.VariablePathAnalysis
import inkblot.reasoning.VariableProperties
import org.apache.jena.query.Query

class QuerySynthesizer(query: Query, val anchor: String, private val variableInfo: Map<String, VariableProperties>) {
    private val paths = VariablePathAnalysis(query, anchor)
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
            val lastEdgeUri = path.last().dependency.p
            val lastNodeVar = freshVar()
            deleteSentences.add("$lastNodeVar <$lastEdgeUri> ?o.")
            insertSentences.add("$lastNodeVar <$lastEdgeUri> ?n.")
            whereSentences.add(pathToSparqlSelect(path, lastNodeVar))
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
            val lastEdgeUri = path.last().dependency.p
            val lastNodeVar = freshVar()
            verbSentences.add("$lastNodeVar <$lastEdgeUri> ?o.")
            whereSentences.add(pathToSparqlSelect(path.dropLast(1), lastNodeVar))
        }

        val verbSection = "$verb { ${verbSentences.joinToString(" ")} } "
        val whereSection = "WHERE { ${whereSentences.joinToString(" ")} }"

        return verbSection + whereSection
    }

    private fun pathToSparqlSelect(path: List<VarDepEdge>, lastNodeVar: String?) : String {
        return if(path.all { !it.backward }) {
            val closingBrackets = "]".repeat(path.size-1)
            "?anchor " + path.joinToString(" [") { "<${it.dependency.p}>" } + " ${lastNodeVar ?: ("<" + path.last().dependency.o + ">")}$closingBrackets."
        }
        else {
            val anchor = if(path.first().backward) path.first().dependency.o else path.first().dependency.s
            val last = if(path.last().backward) path.last().dependency.s else path.last().dependency.o

            val varMapping = path.flatMap { listOf(it.dependency.s, it.dependency.o) }.distinct().associateWith { freshVar() }.toMutableMap()
            varMapping[anchor] = "?anchor"
            varMapping[last] = lastNodeVar ?: "<$last>"

            return path.map {
                val s = varMapping[it.dependency.s]!!
                val o = varMapping[it.dependency.o]!!
                "$s <${it.dependency.p}> $o."
            }.joinToString(" ")
        }
    }

    private fun freshVar(): String {
        uniqueIDCtr += 1
        return "?inkblt$uniqueIDCtr"
    }
}