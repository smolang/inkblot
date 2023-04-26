package org.smolang.inkblot.reasoning

import org.apache.jena.query.Query

// Check that input query conforms to constraints (connectedness, unambiguous writeback) and determine which properties are 'simple'
class VariablePathAnalysis(query: Query, val anchor: String) {

    val resultVars = query.resultVars.toSet()
    private val visitor = DependencyPathVisitor()
    val simpleProperties = mutableMapOf<String, String>()
    private val dependencyPaths: Map<String, List<List<VarDepEdge>>>
    private val concreteLeafPaths: Map<String, List<List<VarDepEdge>>>
    private val optionalContextsByVariable: Map<String, List<String>>

    init {
        if(!query.isSelectType)
            throw Exception("Query has to be SELECT to run VariablePathAnalysis")

        query.resetResultVars()
        query.queryPattern.visit(visitor)

        optionalContextsByVariable = visitor.variablesInOptionalContexts.groupBy { it.first }.mapValues { (_, v) -> v.map { it.second } }
        checkConflictingBindings()

        dependencyPaths =
            VariableDependencePaths.variableDependencyPaths(anchor, resultVars, visitor.variableDependencies)

        analyzePaths()

        concreteLeafPaths = VariableDependencePaths.variableDependencyPaths(
            anchor,
            visitor.concreteLeaves,
            visitor.variableDependencies
        )
    }

    val queryContainsFilter: Boolean
        get() = visitor.containsFilter

    private fun checkConflictingBindings() {
        resultVars.filter { !visitor.safeVariables.contains(it) }.forEach { v ->
            if(!optionalContextsByVariable.containsKey(v))
                throw Exception("SPARQL variable '?$v' from result set does not actually appear in query")
            val stacks = optionalContextsByVariable[v]!!.distinct().sortedBy { it.length }.toMutableList() // non-null assertion is safe since nullable vars occur in at least one context
            val distinctBindings = mutableListOf<String>()

            while(stacks.size > 0) {
                // first item in the list must be a distinct binding since it is the shortest stack by length and thus cannot have another item as a prefix
                val top = stacks.removeFirst()
                // if we have a binding near the top of the stack, all bindings in sub-contexts are safe
                stacks.removeAll { it.startsWith(top) }
                distinctBindings.add(top)
            }

            if(distinctBindings.size > 1)
                throw Exception("Optional variable ?$v has potentially conflicting bindings in different optional contexts")
        }
    }

    private fun analyzePaths() {
        dependencyPaths.forEach { (k, v) ->
            if (v.isEmpty())
                throw Exception("Unable to derive dependency path from ?$anchor to ?$k")

            val optionalCtx = definingContextForVariable(k)
            if(v.none{ path -> path.all { edge -> optionalCtx.startsWith(edge.dependency.optionalContexts) } })
                throw Exception("No path from ?$anchor to ?$k is safe in the defining context of ?$k")

            /*println("Dependency paths from $anchor to $k:")
            v.forEach { path -> println(path.joinToString("->")) }*/

            // We consider a simple property to be: 1. accessed via exactly one forward path of length one
            if(v.size == 1 && v.first().size == 1 && !v.first().first().backward) {
                val edge = v.first().first().dependency
                val target = edge.o
                // 2. it is in the default graph and 3. there are no other edges to the target (target is unconstrained)
                if(edge.inGraph == null && visitor.variableDependencies.none { it != edge && (it.s == target || it.o == target) })
                    simpleProperties[k] = edge.p
            }
        }
    }

    fun definingContextForVariable(v: String): String {
        // the defining context is the shallowest optional context stack in which the variable occurs
        return optionalContextsByVariable[v]?.minBy { it.length } ?: throw Exception("No optional context data for variable '$v'")
    }

    fun concreteLeaves(): Set<String> = visitor.concreteLeaves
    fun safeVariables(): Set<String> = visitor.safeVariables

    fun pathsToVariable(v: String) = dependencyPaths[v] ?: throw Exception("No path to variable '$v'")
    fun pathsToConcrete(c: String) = concreteLeafPaths[c] ?: throw Exception("No path to concrete leaf '$c'")

    fun edgesFor(v: String) = visitor.variableDependencies.filter { it.o  == v || it.s == v }
}

data class VariableProperties(
    val sparqlName: String,
    val targetName: String,
    val nullable: Boolean,
    val functional: Boolean,
    val kotlinType: String,
    val xsdType: String,
    val isObjectReference: Boolean
)