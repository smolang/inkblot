package org.smolang.inkblot.codegen

import org.smolang.inkblot.reasoning.ForbiddenMagicAnalysis
import org.apache.jena.query.Query

object ConfigGenerator {
    fun configForClass(query: Query, forbiddenMagic: Boolean, endpoint: String?): Pair<String, ClassConfig> {
        query.resetResultVars()
        val anchor = query.resultVars.first()
        val className = anchor.replaceFirstChar(Char::titlecase)
        var classType = "http://example.com/ns/class"

        val properties = if(forbiddenMagic) {
            if(endpoint == null)
                throw Exception("Forbidden magic analysis requires SPARQL endpoint using --endpoint")
            val (ct, pr) = ForbiddenMagicAnalysis(endpoint).divine(query, anchor)
            classType = ct ?: classType
            pr
        }
        else (query.resultVars.toSet() - anchor).associateWith { PropertyConfig(it, "[classnameOrXSD]", "*") }

        val classConfig = ClassConfig(anchor, classType, prettifySparql(query), null, properties)
        return Pair(className, classConfig)
    }
}

fun prettifySparql(query: Query): String {
    val singleLine = query.toString().replace("\n", " ")
    // trim excessive spacing & indentation
    var despaced = singleLine.replace(Regex("\\s+"), " ")
    // replace spaces in front of ; . and ) as well as at start and end of query
    despaced = despaced.replace(Regex("\\s([;\\.\\)])")) { match -> match.groupValues[1] }.trim()
    // replace spaces following (
    despaced = despaced.replace(Regex("\\(\\s+"), "(")
    return despaced
}