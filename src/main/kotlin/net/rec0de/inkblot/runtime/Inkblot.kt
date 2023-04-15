package net.rec0de.inkblot.runtime

import org.apache.jena.graph.Node
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.sparql.exec.http.UpdateExecHTTPBuilder
import org.apache.jena.update.UpdateRequest
import java.util.WeakHashMap

object Inkblot {
    private val idgen: FreshUriGenerator = TinyUIDGen

    private val violationListeners = mutableSetOf<ConstraintViolationListener>()

    var endpoint = "http://localhost:3030/bikes"
    val loadedObjects = WeakHashMap<String, SemanticObject>()
    val dirtySet = mutableSetOf<SemanticObject>() // we keep modified objects here in addition to the weak hashmap to ensure they aren't unloaded
    val changelog = mutableListOf<ChangeNode>()

    init {
        // we have to explicitly initialize ARQ for some reason if running from the .jar
        org.apache.jena.query.ARQ.init()
    }

    fun commit() {
        if(changelog.isEmpty())
            return

        val update = UpdateRequest()
        changelog.forEach { chg ->
            chg.asUpdate().forEach { update.add(it) }
        }
        changelog.clear()

        val builder = UpdateExecHTTPBuilder.create()
        builder.endpoint(endpoint)
        builder.update(update)
        //println(update.toString())
        builder.execute()

        dirtySet.forEach { it.markCommitted() }
        dirtySet.clear()
    }

    fun addViolationListener(listener: ConstraintViolationListener) = violationListeners.add(listener)
    fun removeViolationListener(listener: ConstraintViolationListener) = violationListeners.remove(listener)
    fun violation(violation: ConstraintViolation) = violationListeners.forEach { it.handleViolation(violation) }

    fun freshSuffixFor(context: String) = idgen.freshSuffixFor(context)

    fun forceInit() = {}
}

interface ChangeNode {
    fun asUpdate(): UpdateRequest
}

abstract class ComplexChangeNode(private val update: UpdateRequest) : ChangeNode {
    override fun asUpdate() = update
}

class ComplexPropertyAdd(update: UpdateRequest) : ComplexChangeNode(update)
class ComplexPropertyRemove(update: UpdateRequest) : ComplexChangeNode(update)
class CreateNode(update: UpdateRequest) : ComplexChangeNode(update)
class ComplexDelete(update: UpdateRequest) : ComplexChangeNode(update)

abstract class CommonChangeNode : ChangeNode

abstract class CommonSPOChange(private val query: String, private val s: String, private val p: String, private val o: Node) : CommonChangeNode() {
    override fun asUpdate(): UpdateRequest {
        val template = ParameterizedSparqlString(query)
        template.setIri("s", s)
        template.setIri("p", p)
        template.setParam("o", o)
        return template.asUpdate()
    }
}

class CommonPropertyAdd(
    objectUri: String,
    propertyUri: String,
    newValue: Node
) : CommonSPOChange("INSERT DATA { ?s ?p ?o }", objectUri, propertyUri, newValue)

class CommonPropertyRemove(
    objectUri: String,
    propertyUri: String,
    deleteValue: Node
) : CommonSPOChange("DELETE DATA { ?s ?p ?o }", objectUri, propertyUri, deleteValue)

class CommonPropertyChange(private val objectUri: String, private val propertyUri: String, private val oldValue: Node, private val newValue: Node) : CommonChangeNode() {
    override fun asUpdate(): UpdateRequest {
        val template = ParameterizedSparqlString("DELETE { ?s ?p ?o } WHERE { ?s ?p ?o }; INSERT DATA { ?s ?p ?n }")
        template.setIri("s", objectUri)
        template.setIri("p", propertyUri)
        template.setParam("o", oldValue)
        template.setParam("n", newValue)
        return template.asUpdate()
    }
}