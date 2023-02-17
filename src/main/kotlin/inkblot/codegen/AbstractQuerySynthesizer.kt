package inkblot.codegen

import inkblot.reasoning.VariablePathAnalysis
import inkblot.reasoning.VariableProperties

abstract class AbstractQuerySynthesizer(
    val anchor: String,
    protected val variableInfo: Map<String, VariableProperties>,
    protected val paths: VariablePathAnalysis
) {
    fun isSimple(v: String) = paths.simpleProperties.containsKey(v)

    fun simplePathUri(v: String): String {
        if(!paths.simpleProperties.containsKey(v))
            throw Exception("Variable ?$v is not a simple property")
        return paths.simpleProperties[v]!!
    }

    abstract fun baseCreationUpdate(): String

    abstract fun initializerUpdate(v: String): String

    abstract fun changeUpdate(v: String): String

    abstract fun addUpdate(v: String): String

    abstract fun removeUpdate(v: String): String

    protected fun tripleInGraph(s: String, p: String, o: String, inverse: Boolean, graph: String?): String {
        val triple = if(inverse) "$o <$p> $s." else "$s <$p> $o."
        return if(graph == null)
            triple
        else
            "GRAPH <$graph> { $triple }"
    }
}