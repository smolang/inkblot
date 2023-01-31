package inkblot.runtime

import org.apache.jena.rdf.model.Literal

object TypeHelper {
    val supported = setOf("Int", "String")
    fun literalToNullableInt(lit: Literal?): Int? = lit?.int
    fun literalToInt(lit: Literal): Int = lit.int
    fun literalToNullableString(lit: Literal?): String? = lit?.string
    fun literalToString(lit: Literal): String = lit.string
}