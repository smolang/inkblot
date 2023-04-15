package net.rec0de.inkblot.codegen

import net.rec0de.inkblot.reasoning.VariablePathAnalysis
import net.rec0de.inkblot.reasoning.VariableProperties

abstract class AbstractQuerySynthesizer(
    val anchor: String,
    protected val classTypeUri: String,
    protected val variableInfo: Map<String, VariableProperties>,
    protected val paths: VariablePathAnalysis,
    private val overrideMap: MutableMap<String, String>
) {
    val effectiveQueries: Map<String, String>
        get() = overrideMap

    fun isSimple(v: String) = paths.simpleProperties.containsKey(v)

    fun simplePathUri(v: String): String {
        if(!paths.simpleProperties.containsKey(v))
            throw Exception("Variable ?$v is not a simple property")
        return paths.simpleProperties[v]!!
    }

    fun baseCreationUpdate(): String {
        return if(overrideMap.containsKey("creation")) {
            val update = overrideMap["creation"]!!
            println("Using override base creation update: ")
            println(update)
            update
        }
        else {
            val update = synthBaseCreationUpdate()
            overrideMap["creation"] = update
            update
        }
    }

    fun initializerUpdate(v: String): String {
        val key = "init-$v"
        return if(overrideMap.containsKey(key)) {
            val update = overrideMap[key]!!
            println("Using override $key update: ")
            println(update)
            update
        }
        else {
            val update = synthInitializerUpdate(v)
            overrideMap[key] = update
            update
        }
    }

    fun changeUpdate(v: String): String {
        val key = "change-$v"
        return if(overrideMap.containsKey(key)) {
            val update = overrideMap[key]!!
            println("Using override $key update: ")
            println(update)
            update
        }
        else {
            val update = synthChangeUpdate(v)
            overrideMap[key] = update
            update
        }
    }

    fun addUpdate(v: String): String {
        val key = "add-$v"
        return if(overrideMap.containsKey(key)) {
            val update = overrideMap[key]!!
            println("Using override $key update: ")
            println(update)
            update
        }
        else {
            val update = synthAddUpdate(v)
            overrideMap[key] = update
            update
        }
    }

    fun removeUpdate(v: String): String {
        val key = "remove-$v"
        return if(overrideMap.containsKey(key)) {
            val update = overrideMap[key]!!
            println("Using override $key update: ")
            println(update)
            update
        }
        else {
            val update = synthRemoveUpdate(v)
            overrideMap[key] = update
            update
        }
    }

    fun deleteUpdate(): String {
        val key = "delete"
        return if(overrideMap.containsKey(key)) {
            val update = overrideMap[key]!!
            println("Using override $key update: ")
            println(update)
            update
        }
        else {
            val update = "DELETE WHERE { ?anchor ?b ?c }; DELETE WHERE { ?d ?e ?anchor }"
            overrideMap[key] = update
            update
        }
    }

    fun deleteRedirectUpdate(): String {
        val key = "deleteRedirect"
        return if(overrideMap.containsKey(key)) {
            val update = overrideMap[key]!!
            println("Using override $key update: ")
            println(update)
            update
        }
        else {
            val update = "DELETE { ?s ?p ?anchor } INSERT { ?s ?p ?target } WHERE { ?s ?p ?anchor }; DELETE WHERE { ?anchor ?b ?c }"
            overrideMap[key] = update
            update
        }
    }

    abstract fun synthBaseCreationUpdate(): String

    abstract fun synthInitializerUpdate(v: String): String

    abstract fun synthChangeUpdate(v: String): String

    abstract fun synthAddUpdate(v: String): String

    abstract fun synthRemoveUpdate(v: String): String

    // only used for validating SPARQL generation, not actual data access
    abstract fun synthFunctionalValidationQuery(v: String): String

    protected fun tripleInGraph(s: String, p: String, o: String, inverse: Boolean, graph: String?): String {
        val triple = if(inverse) "$o <$p> $s." else "$s <$p> $o."
        return if(graph == null)
            triple
        else
            "GRAPH <$graph> { $triple }"
    }
}