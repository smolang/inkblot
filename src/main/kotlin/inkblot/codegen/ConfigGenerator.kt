package inkblot.codegen

import org.apache.jena.query.Query

object ConfigGenerator {
    fun configForClass(query: Query): Pair<String, ClassConfig> {
        query.resetResultVars()
        val anchor = query.resultVars.first()
        val className = anchor.replaceFirstChar(Char::titlecase)

        val properties = (query.resultVars.toSet() - anchor).associateWith { PropertyConfig(it, "Unit", "*") }
        val classConfig = ClassConfig(anchor, prettifySparql(query), null, properties)
        return Pair(className, classConfig)
    }
}

fun prettifySparql(query: Query): String {
    val singleLine = query.toString().replace("\n", " ")
    // trim excessive spacing & indentation
    val despaced = singleLine.replace(Regex("\\s+"), " ")
    // replace spaces in front of ; and . as well as at start and end of query
    return despaced.replace(Regex("\\s([;\\.])")) { match -> match.groupValues[1] }.trim()
}