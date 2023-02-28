package net.rec0de.inkblot.codegen

import net.rec0de.inkblot.reasoning.VarDependency
import net.rec0de.inkblot.reasoning.VariablePathAnalysis
import net.rec0de.inkblot.reasoning.VariableProperties

class QuerySynthesizer(anchor: String, classTypeUri: String, vars: Map<String, VariableProperties>, paths: VariablePathAnalysis, queryMap: MutableMap<String, String>): AbstractQuerySynthesizer(anchor, classTypeUri, vars, paths, queryMap) {

    // persist blank node counter across calls to make property functionality checks easier
    private var blankNodeCounter = 0

    override fun synthBaseCreationUpdate(): String {
        // check that we have constructor values for all requires sparql variables
        val assignableVars = variableInfo.filterValues{ it.functional && !it.nullable }.keys
        val requiredResultSetVars = paths.resultVars.intersect(paths.safeVariables()) - anchor

        val unboundRequired = requiredResultSetVars.minus(assignableVars)
        if(unboundRequired.isNotEmpty())
            throw Exception("SPARQL variables ${unboundRequired.joinToString(", ")} are not optional but considered optional according to configured multiplicity")

        val boundButOptional = assignableVars.minus(requiredResultSetVars)
        if(boundButOptional.isNotEmpty())
            throw Exception("SPARQL variables ${boundButOptional.joinToString(", ")} are optional in SPARQL but not considered optional according to configured multiplicity")


        // gather all edges that are required / non-optional, rendering variables we have constructor values for using their names and everything else as blank nodes
        val requiredEdges = bindingsFor(setOf(anchor), "")
        val insertStatements = renderEdgeSet(requiredEdges, assignableVars.associateWith { it })

        return "INSERT DATA { $insertStatements }"
    }

    // TODO: can we replace most of this with an invocation to bindingsFor?
    override fun synthInitializerUpdate(v: String): String {
        // v is safe in all contexts that have the bindingContext as a prefix
        // edges from parent contexts of the binding context are safe
        val bindingContext = paths.definingContextForVariable(v)

        // all paths to v that are safe in this context
        val requiredPaths = paths.pathsToVariable(v).filter { path -> path.all { bindingContext.startsWith(it.dependency.optionalContexts) } }

        // create concrete leaved paths that intersect with this property and are (newly) safe in this context
        val requiredConcreteLeavedPaths = paths.concreteLeaves().flatMap { paths.pathsToConcrete(it) }.filter { path ->
            path.any { it.dependency.optional } && path.all { bindingContext.startsWith(it.dependency.optionalContexts) } && requiredPaths.any{ pPath -> pPath.toSet().intersect(path.toSet()).isNotEmpty() }
        }

        //println("Newly safe concrete leaved paths for $v: $requiredConcreteLeavedPaths")

        // maybe we'll worry about variable-leaved paths here at some point
        // TODO

        val varEdges = requiredPaths.flatten().map{ it.dependency }.toSet()
        val conEdges = requiredConcreteLeavedPaths.flatten().map{ it.dependency }.toSet()
        val requiredEdges = varEdges + conEdges

        // recursively expand using edges considered safe not already in edge set
        // provide copy of select query in where clause to provide variable bindings?
        val insertStatements = renderEdgeSet(requiredEdges, mapOf(v to "v"))
        return "INSERT DATA { $insertStatements }"
    }

    private fun genericUpdate(v: String, delete: Boolean, insert: Boolean): String {
        val deleteSentences = mutableListOf<String>()
        val insertSentences = mutableListOf<String>()
        val requiredVarBindings = mutableSetOf<String>()
        mutableSetOf<VarDependency>()

        val lastEdgesOnPaths = paths.pathsToVariable(v).map { it.last().dependency }

        val neighborhood = paths.edgesFor(v)
        neighborhood.forEach { edge ->
            val backwards = edge.s == v
            val sourceNode = if(backwards) edge.oNode else edge.sNode

            val source = when {
                sourceNode.isURI -> "<${sourceNode.uri}>"
                sourceNode.isLiteral -> "\"${sourceNode.literal}\""
                sourceNode.isVariable && sourceNode.name == anchor -> "?anchor"
                sourceNode.isVariable -> {
                    requiredVarBindings.add(sourceNode.name)
                    rewriteAnonymousVariableNames(sourceNode.name)
                }
                else -> throw Exception("Unknown Node type in $edge")
            }

            // when deleting, we only want to delete what's absolutely necessary, e.g. leaving type labels of removed nodes intact
            if(lastEdgesOnPaths.contains(edge)) {
                deleteSentences.add(tripleInGraph(source, edge.p, "?o", backwards, edge.inGraph))
            }
            insertSentences.add(tripleInGraph(source, edge.p, "?n", backwards, edge.inGraph))
        }

        val ctx = paths.definingContextForVariable(v)
        // in the pure insertion case we cannot include full bindings in the where clause because those structures might not be there yet
        val bindings = if(insert && !delete) bindingsFor(requiredVarBindings, ctx).minus(lastEdgesOnPaths.toSet()) else bindingsFor(requiredVarBindings, ctx)
        val whereSentences = renderEdgeSet(bindings, requiredVarBindings.associateWith { it })

        if(!delete && !insert)
            throw Exception("Not inserting anything and not deleting anything is probably unintentional")

        var stmt = ""
        if(delete)
            stmt += "DELETE { ${deleteSentences.joinToString(" ")} } "
        if(insert)
            stmt += "INSERT { ${insertSentences.joinToString(" ")} } "
        stmt += "WHERE { $whereSentences }"

        return stmt
    }

    override fun synthChangeUpdate(v: String) = genericUpdate(v, delete = true, insert = true)

    override fun synthAddUpdate(v: String) = genericUpdate(v, delete = false, insert = true)

    override fun synthRemoveUpdate(v: String) = genericUpdate(v, delete = true, insert = false)

    override fun synthFunctionalValidationQuery(v: String): String {
        val ctx = paths.definingContextForVariable(v)

        val edges = bindingsFor(setOf(v), ctx)
        val whereTriplesA = renderEdgeSet(edges, mapOf(v to "a"))
        val whereTriplesB = renderEdgeSet(edges, mapOf(v to "b"))

        return "SELECT * WHERE { ?anchor a <$classTypeUri>. $whereTriplesA $whereTriplesB FILTER (?a != ?b) }"
    }

    private fun bindingsFor(vars: Set<String>, optionalCtx: String): Set<VarDependency> {
        val toBind = vars.toMutableSet()
        val bound = mutableSetOf<String>()
        val bindings = mutableSetOf<VarDependency>()

        while (toBind.isNotEmpty()) {
            val v = toBind.first()
            toBind.remove(v)
            bound.add(v)

            val edges = paths.edgesFor(v).filter { optionalCtx.startsWith(it.optionalContexts) }
            bindings.addAll(edges)
            val newVars = edges.flatMap {
                if(it.oNode.isVariable && it.sNode.isVariable)
                    listOf(it.o, it.s)
                else if(it.oNode.isVariable)
                    listOf(it.o)
                else if(it.sNode.isVariable)
                    listOf(it.s)
                else emptyList()
            }.toSet().minus(bound).filter { it != anchor }
            toBind.addAll(newVars)
        }

        return bindings
    }

    private fun renderEdgeSet(edgeSet: Set<VarDependency>, assignableVars: Map<String,String>): String {
        // variable to blank node mapping
        val blankNodeMap = mutableMapOf<String, String>()

        // collect by graph name and render into one block of triples per graph
        val byGraph = edgeSet.groupBy { it.inGraph ?: "inkblot:default" }.mapValues { (_, edges) ->
            edges.joinToString(" ") {
                val s = if(it.sNode.isURI)
                    "<${it.s}>"
                else if(it.s == anchor)
                    "?anchor"
                else if (assignableVars.containsKey(it.s))
                    rewriteAnonymousVariableNames(assignableVars[it.s]!!)
                else {
                    if(blankNodeMap.containsKey(it.s))
                        blankNodeMap[it.s]!!
                    else {
                        val bnode = "_:b$blankNodeCounter"
                        blankNodeCounter += 1
                        blankNodeMap[it.s] = bnode
                        bnode
                    }
                }

                val o = if(it.oNode.isURI)
                    "<${it.o}>"
                else if(it.oNode.isLiteral)
                    "\"${it.o}\""
                else if(it.o == anchor)
                    "?anchor"
                else if(assignableVars.containsKey(it.o))
                    rewriteAnonymousVariableNames(assignableVars[it.o]!!)
                else {
                    if(blankNodeMap.containsKey(it.o))
                        blankNodeMap[it.o]!!
                    else {
                        val bnode = "_:b$blankNodeCounter"
                        blankNodeCounter += 1
                        blankNodeMap[it.o] = bnode
                        bnode
                    }
                }

                "$s <${it.p}> $o."
            }
        }

        return byGraph.map { (graph, block) ->
            if(graph == "inkblot:default")
                block
            else
                "GRAPH <$graph> { $block }"
        }.joinToString(" ")
    }

    private fun rewriteAnonymousVariableNames(v: String): String {
        return if(v.startsWith("?"))
                "?inkblt${v.removePrefix("?")}"
            else
                "?$v"
    }
}