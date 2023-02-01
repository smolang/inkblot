package bikes

import inkblot.runtime.*
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.QuerySolution
import org.apache.jena.rdf.model.ResourceFactory

object BellFactory : SemanticObjectFactory<Bell>() {
    override val query = "PREFIX  bk:   <http://rec0de.net/ns/bike#>  SELECT  ?bell ?color WHERE   { ?bell  a         bk:bell ;            bk:color  ?color   } "
    override val anchor = "bell"
    
    fun create(color: String): Bell {
        val uri = "http://rec0de.net/ns/bike#bell" + Inkblot.freshSuffixFor("bell")
    
        // set non-null parameters and create object
        val template = ParameterizedSparqlString("INSERT DATA { ?anchor <http://rec0de.net/ns/bike#color> ?color. ?anchor <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://rec0de.net/ns/bike#bell>. }")
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
        val color = Inkblot.types.literalToString(lines.first().getLiteral("color"))
    
    
        return Bell(uri, color) 
    }
}

class Bell internal constructor(uri: String, color: String) : SemanticObject(uri) {
    companion object {
        fun create(color: String) = BellFactory.create(color)
        fun loadAll() = BellFactory.loadAll()
        fun loadSelected(filter: String) = BellFactory.loadSelected(filter)
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
    
    // TODO: Merge
}