package inkblot.reasoning

import org.apache.jena.sparql.syntax.*

class DependencyPathVisitor : ElementVisitorBase() {

    // we need to keep track of whether we are in an optional block to know which fields of our object are nullable
    private var indentDepth = 0
    private var optionalCtxIdCounter = 0
    private val optionalCtxStack = mutableListOf<Int>()

    val variableDependencies = mutableSetOf<VarDependency>()
    val variablesInOptionalContexts = mutableSetOf<Pair<String, String>>()
    val concreteLeaves = mutableSetOf<String>()
    val safeVariables = mutableSetOf<String>()

    private fun print(data: Any) {
        println("\t".repeat(indentDepth) + data.toString())
    }

    override fun visit(el: ElementAssign) {
        print("ElementAssign")
    }

    override fun visit(el: ElementBind) {
        print("ElementBind")
    }

    override fun visit(el: ElementData) {
        print("ElementData")
    }

    override fun visit(el: ElementDataset) {
        print("ElementDataset")
    }

    override fun visit(el: ElementExists?) {
        print("ElementExists")
    }

    override fun visit(el: ElementFilter?) {
        print("ElementFilter")
    }

    override fun visit(el: ElementGroup) {
        print("Group")
        indentDepth += 1
        el.elements.forEach { it.visit(this) }
        indentDepth -= 1
    }

    override fun visit(el: ElementMinus?) {
        print("ElementMinus")
    }

    override fun visit(el: ElementNamedGraph?) {
        print("ElementNamedGraph")
    }

    override fun visit(el: ElementNotExists?) {
        print("ElementNotExists")
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
            print(it.toString())
            val s = it.subject
            val p = it.predicate
            val o = it.`object`

            if (p.isVariable)
                throw Exception("Variable ?${p.name} occurs in predicate position in '$it'. I don't think that's supported yet.")

            if (o.isVariable) {
                // keeping track of in which optional contexts a variable appears to detect possibly conflicting bindings
                variablesInOptionalContexts.add(Pair(o.name, optionalCtxStack.joinToString(",")))
                if(optionalCtxStack.isEmpty())
                    safeVariables.add(o.name)
            }

            if (s.isVariable) {
                // keeping track of in which optional contexts a variable appears to detect possibly conflicting bindings
                variablesInOptionalContexts.add(Pair(s.name, optionalCtxStack.joinToString(",")))
                if(optionalCtxStack.isEmpty())
                    safeVariables.add(s.name)
            }

            // concrete leaves that may need to be created explicitly
            if (s.isVariable && o.isConcrete) {
                variableDependencies.add(VarDependency(s.name, p.uri, o.uri, optionalCtxStack.isNotEmpty()))
                concreteLeaves.add(o.uri)
            }

            if (s.isVariable && o.isVariable && p.isConcrete) {
                variableDependencies.add(
                    VarDependency(
                        s.name,
                        p.uri,
                        o.name,
                        optionalCtxStack.isNotEmpty(),
                    )
                )
            }
        }
        indentDepth -= 1
    }

    override fun visit(el: ElementService?) {
        print("ElementService")
    }

    override fun visit(el: ElementSubQuery?) {
        print("ElementSubQuery")
    }

    override fun visit(el: ElementTriplesBlock?) {
        print("ElementTriplesBlock")
    }

    override fun visit(el: ElementUnion?) {
        print("ElementUnion")
    }
}