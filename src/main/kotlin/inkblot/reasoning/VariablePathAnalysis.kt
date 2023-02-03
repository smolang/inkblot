package inkblot.reasoning

import org.apache.jena.query.Query

class VariablePathAnalysis(query: Query, val anchor: String) {

    private val vars = query.resultVars.toSet()
    private val visitor = DependencyPathVisitor()
    val simpleProperties = mutableMapOf<String, String>()
    private val dependencyPaths: Map<String, List<List<VarDepEdge>>>
    private val concreteLeafPaths: Map<String, List<List<VarDepEdge>>>

    init {
        if(!query.isSelectType)
            throw Exception("Query has to be SELECT to run VariablePathAnalysis")

        query.resetResultVars()
        query.queryPattern.visit(visitor)

        checkConflictingBindings()

        dependencyPaths = VariableDependencePaths.variableDependencyPaths(anchor, vars, visitor.variableDependencies)

        analyzePaths()

        concreteLeafPaths = VariableDependencePaths.variableDependencyPaths(anchor, visitor.concreteLeaves, visitor.variableDependencies)
    }

    private fun checkConflictingBindings() {
        val optCtxStacks = visitor.variablesInOptionalContexts.groupBy { it.first }.mapValues { (_, v) -> v.map { it.second } }
        vars.filter { !visitor.safeVariables.contains(it) }.forEach {v ->
            val stacks = optCtxStacks[v]!!.distinct().sortedBy { it.length }.toMutableList() // non-null assertion is safe since nullable vars occur in at least one context
            val distinctBindings = mutableListOf<String>()

            while(stacks.size > 0) {
                // first item in the list must be a distinct binding since it is the shortest stack by length and thus cannot have another item as a prefix
                val top = stacks.removeFirst()
                // if we have a binding near the top of the stack, all bindings in sub-contexts are safe (I think. I don't actually know how SPARQL works here exactly)
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

            /*println("Dependency paths from $anchor to $k:")
            v.forEach { path -> println(path.joinToString("->")) }*/

            // We have a simple property access if there is exactly one path and that path is of length one (anchor in direct relation to property, in forward direction)
            if (v.size == 1 && v.first().size == 1 && !v.first().first().backward)
                simpleProperties[k] =
                    v.first().first().dependency.p // save the URI that is used to access this simple property
        }
    }

    // we don't have a use for this right now but i thought *real hard* about it so you better believe we're going to keep it around
    private fun distinctAnonymousVariables() {
        val anonymousVariables = visitor.variableDependencies.flatMap { listOf(it.s, it.o) }.toSet() - vars
        val variableEquivalenceMap = anonymousVariables.associateWith { it }.toMutableMap()
        val anonDependencyPaths =
            VariableDependencePaths.variableDependencyPaths(anchor, anonymousVariables, visitor.variableDependencies)
        println("Query contains anonymous variables: ${anonymousVariables.joinToString(", ")}")

        // ok let's work this out: when are two variables equivalent to each other?
        // -> if every path to the variable has an equivalent path leading to the other one, and vice versa
        // -> corollary: the number of paths to each variable has to be identical
        val coreAnonVars = anonymousVariables.toMutableList()
        var prevSize = coreAnonVars.size + 1
        var i: Int
        println("Variable equivalence analysis starting with ${coreAnonVars.size} distinct variables")

        while (coreAnonVars.size < prevSize) {
            prevSize = coreAnonVars.size
            i = 0
            while (i < coreAnonVars.size) {
                val variable = coreAnonVars[i]
                println("Checking equivalences for $variable")
                val referencePaths = anonDependencyPaths[variable]!!
                val candidates =
                    coreAnonVars.filter { it != variable && anonDependencyPaths[it]?.size == referencePaths.size }
                val equivalent = candidates.filter {
                    val paths = anonDependencyPaths[it]!!
                    paths.all { p ->
                        referencePaths.any { q ->
                            VariableDependencePaths.pathsEquivalentUpToTargetVar(
                                p,
                                q,
                                variableEquivalenceMap
                            )
                        }
                    }
                }.toSet()
                equivalent.forEach {
                    println("\tFound equivalence to $it")
                    // update all variables currently pointing to the equivalent one to the canonical one
                    variableEquivalenceMap.filter { (_, v) -> v == it }
                        .forEach { (k, _) -> variableEquivalenceMap[k] = variable }
                }
                // equivalence is symmetric, so we will always remove things AFTER the current coreAnonVars index
                // therefore it should be safe to remove during iteration?
                coreAnonVars.removeAll(equivalent)
                i += 1
            }
        }

        println("Variable equivalence analysis found ${coreAnonVars.size} distinct variables")
        println()

        coreAnonVars.forEach { v ->
            val paths = anonDependencyPaths[v]!!
            if (paths.isEmpty())
                throw Exception("Unable to derive dependency path from ?$anchor to ?$v")

            println("Dependency paths from $anchor to $v:")
            paths.forEach { path -> println(path.joinToString("->")) }
        }
    }

    fun concreteLeaves(): Set<String> = visitor.concreteLeaves

    fun pathsTo(v: String) = dependencyPaths[v]!!
    fun pathsToConcrete(c: String) = concreteLeafPaths[c]!!
}

data class VariableProperties(
    val targetName: String,
    val nullable: Boolean,
    val functional: Boolean,
    val kotlinType: String,
    val xsdType: String,
    val isObjectReference: Boolean
)