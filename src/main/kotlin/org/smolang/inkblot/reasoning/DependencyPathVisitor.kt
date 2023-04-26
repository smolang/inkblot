package org.smolang.inkblot.reasoning

import org.apache.jena.graph.Triple
import org.apache.jena.sparql.syntax.*

// traverse SPARQL AST, check if all language constructs are supported & gather required information from query
class DependencyPathVisitor : ElementVisitorBase() {

    // we need to keep track of whether we are in an optional block to know which fields of our object are nullable
    private var indentDepth = 0
    private var optionalCtxIdCounter = 0
    private val optionalCtxStack = mutableListOf<Int>()
    private val graphNameStack = mutableListOf<String>()

    val variableDependencies = mutableSetOf<VarDependency>()
    val variablesInOptionalContexts = mutableSetOf<Pair<String, String>>()
    val concreteLeaves = mutableSetOf<String>()
    val safeVariables = mutableSetOf<String>()

    private var foundFilter = false
    val containsFilter: Boolean
        get() = foundFilter

    // keeping these around for forbidden magic purposes
    val variableInRangesOf = mutableMapOf<String,MutableSet<String>>()
    val variableInDomainsOf = mutableMapOf<String,MutableSet<String>>()

    private fun print(data: Any) {
        println("\t".repeat(indentDepth) + data.toString())
    }

    override fun visit(el: ElementAssign) {
        print("ElementAssign")
        throw Exception("Assignments are not supported")
    }

    override fun visit(el: ElementBind) {
        print("ElementBind")
        throw Exception("Explicit bindings are not supported")
    }

    override fun visit(el: ElementData) {
        print("ElementData")
    }

    override fun visit(el: ElementDataset) {
        print("ElementDataset")
    }

    override fun visit(el: ElementExists) {
        throw Exception("ElementExists should not be reachable")
    }

    override fun visit(el: ElementFilter) {
        print("Filter")
        foundFilter = true
    }

    override fun visit(el: ElementGroup) {
        print("Group")
        indentDepth += 1
        el.elements.forEach { it.visit(this) }
        indentDepth -= 1
    }

    override fun visit(el: ElementMinus) {
        print("ElementMinus")
        throw Exception("MINUS is not supported")
    }

    override fun visit(el: ElementNamedGraph) {
        print("NamedGraph")
        if(el.graphNameNode.isVariable)
            throw Exception("Variable named graphs are not supported")

        indentDepth += 1
        graphNameStack.add(el.graphNameNode.uri)
        el.element.visit(this)
        graphNameStack.removeLast()
        indentDepth -= 1
        //throw Exception("Named graphs are not (yet) supported")
    }

    override fun visit(el: ElementNotExists) {
        throw Exception("ElementNotExists should not be reachable")
    }

    override fun visit(el: ElementOptional) {
        print("Optional")
        indentDepth += 1
        optionalCtxIdCounter += 1
        optionalCtxStack.add(optionalCtxIdCounter)
        el.optionalElement.visit(this)
        optionalCtxStack.removeLast()
        indentDepth -= 1
    }

    override fun visit(el: ElementPathBlock) {
        print("PathBlock")
        indentDepth += 1
        val paths = el.pattern.list
        paths.forEach {
            if(!it.isTriple)
                throw Exception("Triple path expressions are not supported ('$it')")
            processTriple(it.asTriple())
        }
        indentDepth -= 1
    }

    override fun visit(el: ElementService) {
        print("Service")
        throw Exception("External services are not supported")
    }

    override fun visit(el: ElementSubQuery) {
        print("SubQuery")
        throw Exception("Sub-queries are not supported")
    }

    override fun visit(el: ElementTriplesBlock) {
        print("TriplesBlock")
        indentDepth += 1
        val paths = el.pattern.list
        paths.forEach {
            processTriple(it)
        }
        indentDepth -= 1
    }

    // I *think* it's okay to treat these as independent optional contexts
    // most of the time they will be rejected due to conflicting variable bindings but there might be cases that work?
    override fun visit(el: ElementUnion) {
        print("Union")
        indentDepth += 1
        el.elements.forEach {
            optionalCtxIdCounter += 1
            optionalCtxStack.add(optionalCtxIdCounter)
            it.visit(this)
            optionalCtxStack.removeLast()
        }
        indentDepth -= 1
    }

    private fun processTriple(triple: Triple) {
            print(triple.toString())
            val s = triple.subject
            val p = triple.predicate
            val o = triple.`object`

            if (p.isVariable)
                throw Exception("Variable ?${p.name} occurs in predicate position in '$triple'. I don't think that's supported yet.")

            // keep track of the optional contexts in which variables appear
            if (o.isVariable) {
                variableInRange(o.name, p.uri)
                // keeping track of in which optional contexts a variable appears to detect possibly conflicting bindings
                variablesInOptionalContexts.add(Pair(o.name, optionalCtxStack.joinToString(",")))
                if(optionalCtxStack.isEmpty())
                    safeVariables.add(o.name)
            }

            if (s.isVariable) {
                variableInDomain(s.name, p.uri)
                // keeping track of in which optional contexts a variable appears to detect possibly conflicting bindings
                variablesInOptionalContexts.add(Pair(s.name, optionalCtxStack.joinToString(",")))
                if(optionalCtxStack.isEmpty())
                    safeVariables.add(s.name)
            }

            // concrete leaves that may need to be created explicitly
            if (s.isVariable && o.isConcrete) {
                if(o.isURI) {
                    variableDependencies.add(VarDependency(s.name, s, p.uri, o.uri, o, optionalCtxStack.joinToString(","), graphNameStack.lastOrNull()))
                    concreteLeaves.add(o.uri)
                }
                else if(o.isLiteral) {
                    variableDependencies.add(VarDependency(s.name, s, p.uri, o.literal.toString(), o, optionalCtxStack.joinToString(","), graphNameStack.lastOrNull()))
                    concreteLeaves.add(o.literal.toString())
                }
            }

            if (s.isConcrete && o.isVariable) {
                if(s.isURI) {
                    variableDependencies.add(VarDependency(s.uri, s, p.uri, o.name, o, optionalCtxStack.joinToString(","), graphNameStack.lastOrNull()))
                    concreteLeaves.add(s.uri)
                }
                else if(s.isLiteral) {
                    variableDependencies.add(VarDependency(s.literal.toString(), s, p.uri, o.name, o, optionalCtxStack.joinToString(","), graphNameStack.lastOrNull()))
                    concreteLeaves.add(s.literal.toString())
                }
            }

            // basic variable-to-variable dependencies
            if (s.isVariable && o.isVariable) {
                variableDependencies.add(
                    VarDependency(
                        s.name, s,
                        p.uri,
                        o.name, o,
                        optionalCtxStack.joinToString(","),
                        graphNameStack.lastOrNull()
                    )
                )
            }
    }

    private fun variableInDomain(variable: String, inDomainOf: String) {
        if(!variableInDomainsOf.containsKey(variable))
            variableInDomainsOf[variable] = mutableSetOf(inDomainOf)
        else
            variableInDomainsOf[variable]!!.add(inDomainOf)
    }

    private fun variableInRange(variable: String, inRangeOf: String) {
        if(!variableInRangesOf.containsKey(variable))
            variableInRangesOf[variable] = mutableSetOf(inRangeOf)
        else
            variableInRangesOf[variable]!!.add(inRangeOf)
    }
}