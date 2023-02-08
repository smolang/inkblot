package inkblot.codegen

import inkblot.reasoning.VariableProperties

class DecoratorGenerator(
    private val className: String,
    private val pkg: String,
    private val variableInfo: Map<String, VariableProperties>
) {

    fun gen() = pkg() + "\n" + genDecorator()

    private fun genDecorator(): String {
        val varName = className.replaceFirstChar(Char::lowercase)
        return """
            class Decorated$className(private val $varName: $className) {
                ${indent(genPropertyLifting(varName), 4)}
            }
        """.trimIndent()
    }

    private fun genPropertyLifting(innerClass: String): String {
        return variableInfo.values.map {
            if(it.functional)
                functionalPropertyLifting(it, innerClass)
            else
                nonFunctionalPropertyLifting(it, innerClass)
        }.joinToString("\n\n")
    }

    private fun functionalPropertyLifting(v: VariableProperties, innerClass: String): String {
        val typeSuffix = if(v.nullable) "?" else ""
        return """
            var ${v.targetName}: ${v.kotlinType}$typeSuffix
                get() = $innerClass.${v.targetName}
                set(value) { $innerClass.${v.targetName} = value }
        """.trimIndent()
    }

    private fun nonFunctionalPropertyLifting(v: VariableProperties, innerClass: String): String {
        return """
            val ${v.targetName}: List<${v.kotlinType}>
                get() = $innerClass.${v.targetName}
            fun ${v.targetName}_add(entry: ${v.kotlinType}) = $innerClass.${v.targetName}_add(entry)
            fun ${v.targetName}_remove(entry: ${v.kotlinType}) = $innerClass.${v.targetName}_remove(entry)
        """.trimIndent()
    }

    private fun pkg() = "package $pkg\n"
}