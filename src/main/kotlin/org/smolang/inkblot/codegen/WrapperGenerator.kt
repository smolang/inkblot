package org.smolang.inkblot.codegen

import org.smolang.inkblot.reasoning.VariableProperties
import java.io.File
import java.nio.file.Path

class WrapperGenerator(
    private val className: String,
    private val variableInfo: Map<String, VariableProperties>
) {

    private var pkg = "gen"

    fun generateToFilesInPath(path: Path, pkgId: String) {
        val destination = File(path.toFile(), "Wrapped$className.kt")
        pkg = pkgId
        destination.writeText(gen())
        println("Generated file 'Wrapped$className.kt'")
    }

    fun gen() = pkg() + "\n" + imports() + "\n" + genWrapper()

    private fun genWrapper(): String {
        val varName = className.replaceFirstChar(Char::lowercase)
        return """
            class Wrapped$className(private val $varName: $className) {
                ${indent(genPropertyLifting(varName), 4)}
                ${indent(mergeDeleteLifting(varName), 4)}
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

    private fun mergeDeleteLifting(innerClass: String): String {
        return """
            fun delete() = $innerClass.delete()
            fun merge(other: Wrapped$className) = $innerClass.merge(other.$innerClass)
        """.trimIndent()
    }

    private fun pkg() = "package $pkg\n"

    private fun imports(): String {
        val imports = mutableListOf<String>()
        if(variableInfo.values.any { it.kotlinType == "BigInteger"})
            imports.add("import java.math.BigInteger")
        if(variableInfo.values.any { it.kotlinType == "BigDecimal"})
            imports.add("import java.math.BigDecimal")
        return imports.joinToString("\n")
    }
}