package inkblot.reasoning

import org.apache.jena.sparql.syntax.*

class TypeDerivationVisitor(private val variables: Set<String>, private val functional: Set<String>, private val invFunctional: Set<String>) : ElementVisitorBase() {

    // we need to keep track of whether we are in an optional block to know which fields of our object are nullable
    private var optionalNestDepth = 0
    private var indentDepth = 0

    val variableInRangesOf = mutableMapOf<String,MutableSet<String>>()
    val variableInDomainsOf = mutableMapOf<String,MutableSet<String>>()
    val variableDependencies = mutableSetOf<VarDependency>()

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
        optionalNestDepth += 1
        el.optionalElement.visit(this)
        optionalNestDepth -= 1
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

            if(o.isVariable && p.isConcrete && variables.contains(o.name)) {
                //print("Variable ${o.name} in range of predicate ${p.uri}")
                variableInRange(o.name, p.uri)
            }

            if(s.isVariable && p.isConcrete && variables.contains(s.name)) {
                //print("Variable ${s.name} in domain of predicate ${p.uri}")
                variableInDomain(s.name, p.uri)
            }

            if(s.isVariable && o.isVariable && p.isConcrete) {
                val isFunct = functional.contains(p.uri)
                val isInvFunct = invFunctional.contains(p.uri)
                val min = if(optionalNestDepth == 0) 1.0 else 0.0
                val max = if(isFunct) 1.0 else Double.POSITIVE_INFINITY
                variableDependencies.add(VarDependency(s.name, p.uri, o.name, optionalNestDepth > 0, isFunct, isInvFunct, min, max))
            }
        }
        indentDepth -= 1
    }

    override fun visit(el: ElementService?) {
        print("ElementService")
        super.visit(el)
    }

    override fun visit(el: ElementSubQuery?) {
        print("ElementSubQuery")
        super.visit(el)
    }

    override fun visit(el: ElementTriplesBlock?) {
        print("ElementTriplesBlock")
        super.visit(el)
    }

    override fun visit(el: ElementUnion?) {
        print("ElementUnion")
        super.visit(el)
    }
}