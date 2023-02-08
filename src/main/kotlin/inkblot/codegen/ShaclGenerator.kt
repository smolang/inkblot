package inkblot.codegen

import inkblot.reasoning.VarDepEdge
import inkblot.reasoning.VariablePathAnalysis
import inkblot.reasoning.VariableProperties
import inkblot.runtime.Inkblot

object ShaclGenerator {
    fun genClassShape(targetClassUri: String, className: String, variableInfo: Map<String, VariableProperties>, paths: VariablePathAnalysis): String {
        val shapeUri = "http://rec0de.net/ns/inkblot#SHACL${Inkblot.freshSuffixFor("SHACL")}"
        val shapeStatements = mutableListOf("a sh:NodeShape", "sh:targetClass <$targetClassUri>")

        variableInfo.values.forEach {
            shapeStatements.add(genPropertyShape(className, it, paths.pathsTo(it.sparqlName)))
        }

        return "<$shapeUri> ${shapeStatements.joinToString("; ")}."
    }

    private fun genPropertyShape(className: String, v: VariableProperties, paths: List<List<VarDepEdge>>): String {
        val spec = if(v.xsdType == "inkblot:rawObjectReference")
            mutableListOf("sh:nodeKind sh:IRI")
        else if(v.isObjectReference)
            mutableListOf("sh:nodeKind sh:IRI", "sh:class <${v.xsdType}>")
        else {
            val expanded = v.xsdType.replace("xsd:", "http://www.w3.org/2001/XMLSchema#")
            mutableListOf("sh:datatype <$expanded>")
        }

        if(v.functional)
            spec.add("sh:maxCount 1")

        if(!v.nullable)
            spec.add("sh:minCount 1")

        spec.add("sh:name \"${v.targetName} property of Inkblot class $className\"")
        spec.add(genShaclPath(v.sparqlName, paths))

        return "sh:property [ ${spec.joinToString("; ")}]"

    }

    private fun genShaclPath(v: String, paths: List<List<VarDepEdge>>): String {
        if(paths.size > 1)
            println("WARNING: More than one path leads to ?$v, generated SHACL constraints may be wonky")

        val shaclPaths = paths.map { singlePathToShacl(it) }

        return if(shaclPaths.size > 1)
            "sh:path [ sh:alternativePath (${shaclPaths.joinToString(" ")}) ]"
        else
            "sh:path ${shaclPaths.first()}"
    }

    private fun singlePathToShacl(path: List<VarDepEdge>): String {
        val predicates = path.map { edgeToPrimitivePath(it) }
        return if(predicates.size > 1)
                "(${predicates.joinToString(" ")})"
            else
                predicates.first()
    }

    private fun edgeToPrimitivePath(edge: VarDepEdge): String {
        return if(edge.backward)
                "[sh:inversePath <${edge.dependency.p}>]"
            else
                "<${edge.dependency.p}>"
    }
}