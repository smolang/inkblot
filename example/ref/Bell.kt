package ref

import org.smolang.inkblot.runtime.*
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.QuerySolution
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.datatypes.xsd.XSDDatatype

object BellFactory : SemanticObjectFactory<Bell>(
    listOf(
        "PREFIX bk: <http://rec0de.net/ns/bike#> SELECT ?bell ?color WHERE { ?bell a bk:bell; bk:color ?color FILTER (bound(?color) && (datatype(?color) != <http://www.w3.org/2001/XMLSchema#string>)) }",
        "SELECT * WHERE { ?anchor a <http://rec0de.net/ns/bike#bell>; <http://rec0de.net/ns/bike#color> ?a; <http://rec0de.net/ns/bike#color> ?b FILTER (?a != ?b) }"
    ),
    "Bell"
) {
    override val anchor = "bell"
    override val query = ParameterizedSparqlString("PREFIX bk: <http://rec0de.net/ns/bike#> SELECT ?bell ?color WHERE { ?bell a bk:bell; bk:color ?color }")
    private val baseCreationUpdate = ParameterizedSparqlString("INSERT DATA { ?anchor <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://rec0de.net/ns/bike#bell>. ?anchor <http://rec0de.net/ns/bike#color> ?color. }")
    
    fun create(color: String): Bell {
        val uri = "http://rec0de.net/ns/bike#bell" + Inkblot.freshSuffixFor("bell")
    
        // set non-null parameters and create object
        val template = baseCreationUpdate.copy()
        template.setIri("anchor", uri)
        template.setParam("color", ResourceFactory.createTypedLiteral(color))
    
        val update = template.asUpdate()
    
    
        Inkblot.changelog.add(CreateNode(update))
        return Bell(uri, color)
    }
    
    override fun instantiateSingleResult(lines: List<QuerySolution>): Bell? {
        if(lines.isEmpty())
            return null
    
        val uri = lines.first().getResource("bell").uri
    
        // for functional properties we can read the first only, as all others have to be the same
        val color = lines.first().getLiteral("color").string
    
    
        return Bell(uri, color) 
    }
}
class Bell internal constructor(uri: String, color: String) : SemanticObject(uri) {
    companion object {
        fun create(color: String) = BellFactory.create(color)
        fun commitAndLoadAll() = BellFactory.commitAndLoadAll()
        fun commitAndLoadSelected(filter: String) = BellFactory.commitAndLoadSelected(filter)
        fun loadFromURI(uri: String) = BellFactory.loadFromURI(uri)
    }
    
    override val deleteUpdate = ParameterizedSparqlString("DELETE WHERE { ?anchor ?b ?c }; DELETE WHERE { ?d ?e ?anchor }")
    override val deleteRedirectUpdate = ParameterizedSparqlString("DELETE { ?s ?p ?anchor } INSERT { ?s ?p ?target } WHERE { ?s ?p ?anchor }; DELETE WHERE { ?anchor ?b ?c }")
    
    var color: String = color
        set(value) {
            if(deleted)
                throw Exception("Trying to set property 'color' on deleted object <$uri>")
    
    
            val newValueNode = ResourceFactory.createTypedLiteral(value).asNode()
            val oldValueNode = ResourceFactory.createTypedLiteral(field).asNode()
            val cn = CommonPropertyChange(uri, "http://rec0de.net/ns/bike#color", oldValueNode, newValueNode)
            Inkblot.changelog.add(cn)
    
            field = value
            markDirty()
        }
    
    fun merge(other: Bell) {
        if(deleted || other.deleted)
            throw Exception("Trying to merge into/out of deleted objects <$uri> / <${other.uri}>")
    
        color = other.color
    
        other.delete(uri)
        markDirty()
    }
}