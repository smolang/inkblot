package bikes

import inkblot.ChangeSingletProperty
import inkblot.CreateNode
import inkblot.Inkblot
import inkblot.SemanticObject
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ResultSet
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP
import org.apache.jena.sparql.exec.http.QueryExecutionHTTPBuilder

class Bell private constructor(uri: String, color: String) : SemanticObject(uri) {
    companion object {

        private const val prefix = "http://rec0de.net/ns/bike#"
        private const val query = "PREFIX bk: <$prefix> SELECT ?bell ?color WHERE { ?bell a bk:bell; bk:color ?color }"

        fun create(color: String): Bell {
            val uri = prefix + "bell" + Inkblot.freshSuffixFor("bell")

            val template = ParameterizedSparqlString("INSERT DATA { ?bell a bk:bell. ?bell bk:color ?color }")
            template.setNsPrefix("bk", prefix)
            template.setIri("bell", uri)
            template.setParam("color", ResourceFactory.createTypedLiteral(color))
            Inkblot.changelog.add(CreateNode(template.asUpdate()))

            return Bell(uri, color)
        }

        fun loadFromURI(uri: String): Bell {

            if(Inkblot.loadedObjects.containsKey(uri))
                return Inkblot.loadedObjects[uri] as Bell

            val builder = QueryExecutionHTTPBuilder.create()
            builder.endpoint(Inkblot.endpoint)
            builder.query(query)
            builder.substitution("bell", ResourceFactory.createResource(uri).asNode())
            val res = builder.select()

            val list = res.asSequence().toList()
            res.close()

            if(list.isEmpty())
                throw Exception("Loading bell by URI <$uri> failed, no such bell")

            return instantiateSingleResult(list)!!
        }

        fun loadAll(): List<Bell> {
            val query = QueryExecutionHTTP.service(Inkblot.endpoint, query)
            return instantiateFromResultSet(query.execSelect())
        }

        fun loadSelected(filter: String): List<Bell> {
            val selectQuery = ParameterizedSparqlString(query).asQuery()
            val filtered = Inkblot.addFilterToSelect(selectQuery, filter)
            val execCtx = QueryExecutionHTTP.service(Inkblot.endpoint, filtered)
            return instantiateFromResultSet(execCtx.execSelect())
        }

        private fun instantiateFromResultSet(res: ResultSet): List<Bell> {
            val entities = res.asSequence().groupBy { it.getResource("bell").uri }
            res.close()
            return entities.values.map { instantiateSingleResult(it)!! } // entity groups are never empty here, non-null assert is fine
        }

        private fun instantiateSingleResult(lines: List<QuerySolution>): Bell? {
            if(lines.isEmpty())
                return null

            // for single cardinality properties we can read the first only, as all others have to be the same
            val uri = lines.first().getResource("bell").uri
            val color = lines.first().getLiteral("color").string

            return Bell(uri, color)
        }
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