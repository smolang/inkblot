package inkblot.codegen

import inkblot.reasoning.VarDepEdge
import inkblot.reasoning.VariablePathAnalysis
import inkblot.reasoning.VariableProperties
import inkblot.runtime.Inkblot

object ShaclGenerator {
    fun genClassShape(targetClassUri: String, className: String, variableInfo: Map<String, VariableProperties>, paths: VariablePathAnalysis): String {
        val shapeUri = "http://rec0de.net/ns/inkblot#SHACL${Inkblot.freshSuffixFor("SHACL")}"
        val shapeStatements = mutableListOf("a sh:NodeShape", "sh:targetClass <$targetClassUri>")

        checkForWeirdness(className, variableInfo.values, paths)

        variableInfo.values.forEach {
            shapeStatements.add(genPropertyShape(className, it, paths.pathsTo(it.sparqlName)))
        }

        return "<$shapeUri> ${shapeStatements.joinToString("; ")}."
    }

    // SPARQL happens to be annoyingly expressive
    // in a decent amount of cases, the SHACL we can generate does not quite match the actual restrictions
    // (i think usually the SHACL will just be overly restrictive, but there might be edge cases where it's just wrong sometimes)
    // therefore we'll try to determine how trustworthy our SHACL is by detecting SPARQL weirdness
    private fun checkForWeirdness(className: String, variableInfo: Collection<VariableProperties>, paths: VariablePathAnalysis) {
        val concreteLeaves = paths.concreteLeaves().map { paths.pathsToConcrete(it) }

        // we expect one concrete leaf to specify the type of what we select - this is fine and can be mapped to SHACL
        val relevantLeaves = concreteLeaves.filterNot { it.size == 1 && it.first().size == 1 && it.first().first().dependency.p == "http://www.w3.org/2000/01/rdf-schema#type" }.flatten()

        if(relevantLeaves.isNotEmpty())
            println("WARNING: Query contains constant leaves, generated SHACL for '$className' might match instances it shouldn't")

        val propertyPaths = variableInfo.flatMap {
            val p = paths.pathsTo(it.sparqlName)
            if(p.size > 1)
                println("WARNING: Query contains several paths to '?${it.sparqlName}', generated SHACL for '$className.${it.targetName}' might be too restrictive")
            p
        }

        propertyPaths.forEach { pPath ->
            if(relevantLeaves.any{ cPath -> pPath.toSet().intersect(cPath.toSet()).isNotEmpty() }) {
                val last = pPath.last()
                val v = if(last.backward) last.dependency.s else last.dependency.o
                println("WARNING: Query contains concrete leaf along path to '?$v', generated SHACL might be too restrictive")
            }
        }
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