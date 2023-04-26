package ref

import org.smolang.inkblot.runtime.*
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.QuerySolution
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.datatypes.xsd.XSDDatatype

object WheelFactory : SemanticObjectFactory<Wheel>(
    listOf(
        "PREFIX bk: <http://rec0de.net/ns/bike#> SELECT ?wheel ?dia ?mfgD ?mfgN WHERE { ?wheel a bk:wheel; bk:diameter ?dia OPTIONAL { ?wheel bk:mfgDate ?mfgD } OPTIONAL { ?wheel bk:mfgName ?mfgN } FILTER (((bound(?dia) && (datatype(?dia) != <http://www.w3.org/2001/XMLSchema#double>)) || (bound(?mfgD) && (datatype(?mfgD) != <http://www.w3.org/2001/XMLSchema#int>))) || (bound(?mfgN) && (datatype(?mfgN) != <http://www.w3.org/2001/XMLSchema#string>))) }",
        "SELECT * WHERE { ?anchor a <http://rec0de.net/ns/bike#wheel>; <http://rec0de.net/ns/bike#diameter> ?a; <http://rec0de.net/ns/bike#diameter> ?b FILTER (?a != ?b) }",
        "SELECT * WHERE { ?anchor a <http://rec0de.net/ns/bike#wheel>; <http://rec0de.net/ns/bike#mfgDate> ?a; <http://rec0de.net/ns/bike#mfgDate> ?b FILTER (?a != ?b) }"
    ),
    "Wheel"
) {
    override val anchor = "wheel"
    override val query = ParameterizedSparqlString("PREFIX bk: <http://rec0de.net/ns/bike#> SELECT ?wheel ?dia ?mfgD ?mfgN WHERE { ?wheel a bk:wheel; bk:diameter ?dia OPTIONAL { ?wheel bk:mfgDate ?mfgD } OPTIONAL { ?wheel bk:mfgName ?mfgN } }")
    private val initUpdate_mfgD = ParameterizedSparqlString("INSERT { ?anchor <http://rec0de.net/ns/bike#mfgDate> ?n.  } WHERE {  }")
    private val initUpdate_mfgN = ParameterizedSparqlString("INSERT { ?anchor <http://rec0de.net/ns/bike#mfgName> ?n.  } WHERE {  }")
    private val baseCreationUpdate = ParameterizedSparqlString("INSERT DATA { ?anchor <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://rec0de.net/ns/bike#wheel>. ?anchor <http://rec0de.net/ns/bike#diameter> ?dia. }")
    
    fun create(diameter: Double, mfgYear: Int?, mfgNames: List<String>): Wheel {
        val uri = "http://rec0de.net/ns/bike#wheel" + Inkblot.freshSuffixFor("wheel")
    
        // set non-null parameters and create object
        val template = baseCreationUpdate.copy()
        template.setIri("anchor", uri)
        template.setParam("dia", ResourceFactory.createTypedLiteral(diameter))
    
        val update = template.asUpdate()
    
        // initialize nullable functional properties
        if(mfgYear != null) {
            val partialUpdate = initUpdate_mfgD.copy()
            partialUpdate.setIri("anchor", uri)
            partialUpdate.setParam("n", ResourceFactory.createTypedLiteral(mfgYear))
            partialUpdate.asUpdate().forEach { update.add(it) }
        }
    
    
        // initialize non-functional properties
        mfgNames.forEach {
            val partialUpdate = initUpdate_mfgN.copy()
            partialUpdate.setIri("anchor", uri)
            partialUpdate.setParam("n", ResourceFactory.createTypedLiteral(it))
            partialUpdate.asUpdate().forEach { part -> update.add(part) }
        }
    
        Inkblot.changelog.add(CreateNode(update))
        return Wheel(uri, diameter, mfgYear, mfgNames)
    }
    
    override fun instantiateSingleResult(lines: List<QuerySolution>): Wheel? {
        if(lines.isEmpty())
            return null
    
        val uri = lines.first().getResource("wheel").uri
    
        // for functional properties we can read the first only, as all others have to be the same
        val diameter = lines.first().getLiteral("dia").double
        val mfgYear = lines.first().getLiteral("mfgD")?.int
    
    
        // for higher cardinality properties, we have to collect all distinct values
        val mfgNames = lines.mapNotNull { it.getLiteral("mfgN")?.string }.distinct()
    
        return Wheel(uri, diameter, mfgYear, mfgNames) 
    }
}
class Wheel internal constructor(uri: String, diameter: Double, mfgYear: Int?, mfgNames: List<String>) : SemanticObject(uri) {
    companion object {
        fun create(diameter: Double, mfgYear: Int?, mfgNames: List<String>) = WheelFactory.create(diameter, mfgYear, mfgNames)
        fun commitAndLoadAll() = WheelFactory.commitAndLoadAll()
        fun commitAndLoadSelected(filter: String) = WheelFactory.commitAndLoadSelected(filter)
        fun loadFromURI(uri: String) = WheelFactory.loadFromURI(uri)
    }
    
    override val deleteUpdate = ParameterizedSparqlString("DELETE WHERE { ?anchor ?b ?c }; DELETE WHERE { ?d ?e ?anchor }")
    override val deleteRedirectUpdate = ParameterizedSparqlString("DELETE { ?s ?p ?anchor } INSERT { ?s ?p ?target } WHERE { ?s ?p ?anchor }; DELETE WHERE { ?anchor ?b ?c }")
    
    var diameter: Double = diameter
        set(value) {
            if(deleted)
                throw Exception("Trying to set property 'diameter' on deleted object <$uri>")
    
    
            val newValueNode = ResourceFactory.createTypedLiteral(value).asNode()
            val oldValueNode = ResourceFactory.createTypedLiteral(field).asNode()
            val cn = CommonPropertyChange(uri, "http://rec0de.net/ns/bike#diameter", oldValueNode, newValueNode)
            Inkblot.changelog.add(cn)
    
            field = value
            markDirty()
        }
    
    var mfgYear: Int? = mfgYear
        set(value) {
            if(deleted)
                throw Exception("Trying to set property 'mfgYear' on deleted object <$uri>")
    
            if(value == null) {
                // Unset value
                val oldValueNode = ResourceFactory.createTypedLiteral(field).asNode()
                val cn = CommonPropertyRemove(uri, "http://rec0de.net/ns/bike#mfgDate", oldValueNode)
                Inkblot.changelog.add(cn)
            }
            else if(field == null) {
                // Pure insertion
                val newValueNode = ResourceFactory.createTypedLiteral(value).asNode()
    
                val cn = CommonPropertyAdd(uri, "http://rec0de.net/ns/bike#mfgDate", newValueNode)
                Inkblot.changelog.add(cn)
            }
            else {
                // Update value
                val oldValueNode = ResourceFactory.createTypedLiteral(field).asNode()
                val newValueNode = ResourceFactory.createTypedLiteral(value).asNode()
    
                val cn = CommonPropertyChange(uri, "http://rec0de.net/ns/bike#mfgDate", oldValueNode, newValueNode!!)
                Inkblot.changelog.add(cn)
            }
    
            field = value
            markDirty()
        }
    
    private val _inkblt_mfgNames = mfgNames.toMutableList()
    
    val mfgNames: List<String>
        get() = _inkblt_mfgNames
    
    fun mfgNames_add(data: String) {
        if(deleted)
            throw Exception("Trying to set property 'mfgNames' on deleted object <$uri>")
    
        _inkblt_mfgNames.add(data)
    
        val cn = CommonPropertyAdd(uri, "http://rec0de.net/ns/bike#mfgName", ResourceFactory.createTypedLiteral(data).asNode())
        Inkblot.changelog.add(cn)
        markDirty()
    }
    
    fun mfgNames_remove(data: String) {
        if(deleted)
            throw Exception("Trying to set property 'mfgNames' on deleted object <$uri>")
        _inkblt_mfgNames.remove(data)
    
        val cn = CommonPropertyRemove(uri, "http://rec0de.net/ns/bike#mfgName", ResourceFactory.createTypedLiteral(data).asNode())
        Inkblot.changelog.add(cn)
        markDirty()
    }
    
    fun merge(other: Wheel) {
        if(deleted || other.deleted)
            throw Exception("Trying to merge into/out of deleted objects <$uri> / <${other.uri}>")
    
        diameter = other.diameter
        mfgYear = other.mfgYear ?: mfgYear
        _inkblt_mfgNames.addAll(other._inkblt_mfgNames)
    
        other.delete(uri)
        markDirty()
    }
}