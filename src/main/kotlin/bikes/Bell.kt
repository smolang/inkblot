package bikes

import inkblot.runtime.*
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.QuerySolution
import org.apache.jena.rdf.model.ResourceFactory

object BellFactory : SemanticObjectFactory<Bell>() {
    private const val prefix = "http://rec0de.net/ns/bike#"
    override val query = "PREFIX bk: <$prefix> SELECT ?bell ?color WHERE { ?bell a bk:bell; bk:color ?color }"
    override val anchor = "bell"

    fun create(color: String): Bell {
        val uri = prefix + "bell" + Inkblot.freshSuffixFor("bell")

        val template = ParameterizedSparqlString("INSERT DATA { ?bell a bk:bell. ?bell bk:color ?color }")
        template.setNsPrefix("bk", prefix)
        template.setIri("bell", uri)
        template.setParam("color", ResourceFactory.createTypedLiteral(color))
        Inkblot.changelog.add(CreateNode(template.asUpdate()))

        return Bell(uri, color)
    }

    override fun instantiateSingleResult(lines: List<QuerySolution>): Bell? {
        if(lines.isEmpty())
            return null

        // for single cardinality properties we can read the first only, as all others have to be the same
        val uri = lines.first().getResource("bell").uri
        val color = lines.first().getLiteral("color").string

        return Bell(uri, color)
    }
}

class Bell internal constructor(uri: String, color: String) : SemanticObject(uri) {
    companion object {
        private const val prefix = "http://rec0de.net/ns/bike#"
        fun create(color: String) = BellFactory.create(color)
        fun loadAll() = BellFactory.loadAll()
        fun loadSelected(filter: String) = BellFactory.loadSelected(filter)
        fun loadFromURI(uri: String) = BellFactory.loadFromURI(uri)
    }

    // Single cardinality properties can make do with just a field
    var color: String = color
        set(value) {
            if(deleted)
                throw Exception("Trying to set property 'color' on deleted object <$uri>")
            field = value

            val valueNode = ResourceFactory.createTypedLiteral(color).asNode()
            Inkblot.changelog.add(ChangeSingletProperty(uri, prefix + "color", valueNode))
            markDirty()
        }

    // Merge functionality
    fun merge(other: Bell) {
        if(deleted || other.deleted)
            throw Exception("Trying to merge into/out of deleted objects <$uri> / <${other.uri}>")

        // non-null asserted singlet properties can just be overwritten
        color = other.color
        other.delete(uri)
        markDirty()
    }
}