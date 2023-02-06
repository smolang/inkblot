package bikes

import inkblot.runtime.*
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.QuerySolution
import org.apache.jena.rdf.model.ResourceFactory
import java.math.BigDecimal
import java.math.BigInteger

object BellFactory : SemanticObjectFactory<Bell>() {
    override val anchor = "bell"
    override val query = ParameterizedSparqlString("PREFIX bk: <http://rec0de.net/ns/bike#> SELECT ?bell ?color WHERE { ?bell a bk:bell; bk:color ?color }")
    private val baseCreationUpdate = ParameterizedSparqlString("INSERT DATA { ?anchor <http://rec0de.net/ns/bike#color> ?color. ?anchor <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://rec0de.net/ns/bike#bell>. }")
    
    fun create(color: String): Bell {
        val uri = "http://rec0de.net/ns/inkblot#bell" + Inkblot.freshSuffixFor("bell")
    
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
        fun loadAll(commitBefore: Boolean) = BellFactory.loadAll(commitBefore)
        fun commitAndLoadSelected(filter: String) = BellFactory.commitAndLoadSelected(filter)
        fun loadFromURI(uri: String) = BellFactory.loadFromURI(uri)
    }
    
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