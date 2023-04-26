package ref

import org.smolang.inkblot.runtime.*
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.QuerySolution
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.datatypes.xsd.XSDDatatype

object BikeFactory : SemanticObjectFactory<Bike>(
    listOf(
        "PREFIX bk: <http://rec0de.net/ns/bike#> SELECT ?bike ?mfg ?fw ?bw ?bells WHERE { ?bike a bk:bike; bk:hasFrame _:b0. _:b0 bk:frontWheel ?fw; bk:backWheel ?bw OPTIONAL { ?bike bk:mfgYear ?mfg } OPTIONAL { ?bike bk:hasFrame _:b1. _:b1 bk:hasBell ?bells } FILTER ((((bound(?fw) && (! (isIRI(?fw) && EXISTS { ?fw a bk:wheel }))) || (bound(?bw) && (! (isIRI(?bw) && EXISTS { ?bw a bk:wheel })))) || (bound(?bells) && (! (isIRI(?bells) && EXISTS { ?bells a bk:bell })))) || (bound(?mfg) && (datatype(?mfg) != <http://www.w3.org/2001/XMLSchema#int>))) }",
        "SELECT * WHERE { ?anchor a <http://rec0de.net/ns/bike#bike>. _:b0 <http://rec0de.net/ns/bike#frontWheel> ?a. ?anchor <http://rec0de.net/ns/bike#hasFrame> _:b0. _:b0 <http://rec0de.net/ns/bike#backWheel> _:b2. _:b1 <http://rec0de.net/ns/bike#frontWheel> ?b. ?anchor <http://rec0de.net/ns/bike#hasFrame> _:b1. _:b1 <http://rec0de.net/ns/bike#backWheel> _:b3 FILTER (?a != ?b) }",
        "SELECT * WHERE { ?anchor a <http://rec0de.net/ns/bike#bike>. _:b0 <http://rec0de.net/ns/bike#backWheel> ?a. ?anchor <http://rec0de.net/ns/bike#hasFrame> _:b0. _:b0 <http://rec0de.net/ns/bike#frontWheel> _:b2. _:b1 <http://rec0de.net/ns/bike#backWheel> ?b. ?anchor <http://rec0de.net/ns/bike#hasFrame> _:b1. _:b1 <http://rec0de.net/ns/bike#frontWheel> _:b3 FILTER (?a != ?b) }",
        "SELECT * WHERE { ?anchor a <http://rec0de.net/ns/bike#bike>; <http://rec0de.net/ns/bike#mfgYear> ?a; <http://rec0de.net/ns/bike#mfgYear> ?b FILTER (?a != ?b) }"
    ),
    "Bike"
) {
    override val anchor = "bike"
    override val query = ParameterizedSparqlString("PREFIX bk: <http://rec0de.net/ns/bike#> SELECT ?bike ?mfg ?fw ?bw ?bells WHERE { ?bike a bk:bike; bk:hasFrame _:b0. _:b0 bk:frontWheel ?fw; bk:backWheel ?bw OPTIONAL { ?bike bk:mfgYear ?mfg } OPTIONAL { ?bike bk:hasFrame _:b1. _:b1 bk:hasBell ?bells } }")
    private val initUpdate_bells = ParameterizedSparqlString("INSERT { ?inkblt1 <http://rec0de.net/ns/bike#hasBell> ?n. ?anchor <http://rec0de.net/ns/bike#hasFrame> ?inkblt1. } WHERE {  }")
    private val initUpdate_mfg = ParameterizedSparqlString("INSERT { ?anchor <http://rec0de.net/ns/bike#mfgYear> ?n.  } WHERE {  }")
    private val baseCreationUpdate = ParameterizedSparqlString("INSERT DATA { ?anchor <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://rec0de.net/ns/bike#bike>. ?anchor <http://rec0de.net/ns/bike#hasFrame> _:b8. _:b8 <http://rec0de.net/ns/bike#frontWheel> ?fw. _:b8 <http://rec0de.net/ns/bike#backWheel> ?bw. }")
    
    fun create(frontWheel: Wheel, backWheel: Wheel, bells: List<Bell>, mfgYear: Int?): Bike {
        val uri = "http://rec0de.net/ns/bike#bike" + Inkblot.freshSuffixFor("bike")
    
        // set non-null parameters and create object
        val template = baseCreationUpdate.copy()
        template.setIri("anchor", uri)
        template.setIri("fw", frontWheel.uri)
        template.setIri("bw", backWheel.uri)
    
        val update = template.asUpdate()
    
        // initialize nullable functional properties
        if(mfgYear != null) {
            val partialUpdate = initUpdate_mfg.copy()
            partialUpdate.setIri("anchor", uri)
            partialUpdate.setParam("n", ResourceFactory.createTypedLiteral(mfgYear))
            partialUpdate.asUpdate().forEach { update.add(it) }
        }
    
    
        // initialize non-functional properties
        bells.forEach {
            val partialUpdate = initUpdate_bells.copy()
            partialUpdate.setIri("anchor", uri)
            partialUpdate.setIri("n", it.uri)
            partialUpdate.asUpdate().forEach { part -> update.add(part) }
        }
    
        Inkblot.changelog.add(CreateNode(update))
        return Bike(uri, frontWheel.uri, backWheel.uri, bells.map{ it.uri }, mfgYear)
    }
    
    override fun instantiateSingleResult(lines: List<QuerySolution>): Bike? {
        if(lines.isEmpty())
            return null
    
        val uri = lines.first().getResource("bike").uri
    
        // for functional properties we can read the first only, as all others have to be the same
        val frontWheel = lines.first().getResource("fw").uri
        val backWheel = lines.first().getResource("bw").uri
        val mfgYear = lines.first().getLiteral("mfg")?.int
    
    
        // for higher cardinality properties, we have to collect all distinct values
        val bells = lines.mapNotNull { it.getResource("bells")?.uri }.distinct()
    
        return Bike(uri, frontWheel, backWheel, bells, mfgYear) 
    }
}
class Bike internal constructor(uri: String, frontWheel: String, backWheel: String, bells: List<String>, mfgYear: Int?) : SemanticObject(uri) {
    companion object {
        fun create(frontWheel: Wheel, backWheel: Wheel, bells: List<Bell>, mfgYear: Int?) = BikeFactory.create(frontWheel, backWheel, bells, mfgYear)
        fun commitAndLoadAll() = BikeFactory.commitAndLoadAll()
        fun commitAndLoadSelected(filter: String) = BikeFactory.commitAndLoadSelected(filter)
        fun loadFromURI(uri: String) = BikeFactory.loadFromURI(uri)
    }
    
    override val deleteUpdate = ParameterizedSparqlString("DELETE WHERE { ?anchor ?b ?c }; DELETE WHERE { ?d ?e ?anchor }")
    override val deleteRedirectUpdate = ParameterizedSparqlString("DELETE { ?s ?p ?anchor } INSERT { ?s ?p ?target } WHERE { ?s ?p ?anchor }; DELETE WHERE { ?anchor ?b ?c }")
    
    private var _inkbltRef_frontWheel: String = frontWheel
    var frontWheel: Wheel
        get() = Wheel.loadFromURI(_inkbltRef_frontWheel)
    set(value) {
        if(deleted)
            throw Exception("Trying to set property 'frontWheel' on deleted object <$uri>")
    
        val template = ParameterizedSparqlString("DELETE { ?inkblt0 <http://rec0de.net/ns/bike#frontWheel> ?o. } INSERT { ?inkblt0 <http://rec0de.net/ns/bike#frontWheel> ?n. } WHERE { ?anchor <http://rec0de.net/ns/bike#hasFrame> ?inkblt0. ?inkblt0 <http://rec0de.net/ns/bike#frontWheel> _:b9. ?inkblt0 <http://rec0de.net/ns/bike#backWheel> _:b10. }")
        template.setIri("anchor", uri)
        template.setParam("o", ResourceFactory.createResource(_inkbltRef_frontWheel).asNode())
        template.setParam("n", ResourceFactory.createResource(value.uri).asNode())
        val cn = ComplexPropertyRemove(template.asUpdate())
        Inkblot.changelog.add(cn)
    
        _inkbltRef_frontWheel = value.uri
        markDirty()
    }
    
    private var _inkbltRef_backWheel: String = backWheel
    var backWheel: Wheel
        get() = Wheel.loadFromURI(_inkbltRef_backWheel)
    set(value) {
        if(deleted)
            throw Exception("Trying to set property 'backWheel' on deleted object <$uri>")
    
        val template = ParameterizedSparqlString("DELETE { ?inkblt0 <http://rec0de.net/ns/bike#backWheel> ?o. } INSERT { ?inkblt0 <http://rec0de.net/ns/bike#backWheel> ?n. } WHERE { ?anchor <http://rec0de.net/ns/bike#hasFrame> ?inkblt0. ?inkblt0 <http://rec0de.net/ns/bike#frontWheel> _:b11. ?inkblt0 <http://rec0de.net/ns/bike#backWheel> _:b12. }")
        template.setIri("anchor", uri)
        template.setParam("o", ResourceFactory.createResource(_inkbltRef_backWheel).asNode())
        template.setParam("n", ResourceFactory.createResource(value.uri).asNode())
        val cn = ComplexPropertyRemove(template.asUpdate())
        Inkblot.changelog.add(cn)
    
        _inkbltRef_backWheel = value.uri
        markDirty()
    }
    
    private val _inkbltRef_bells = bells.toMutableSet()
    val bells: List<Bell>
        get() = _inkbltRef_bells.map { BellFactory.loadFromURI(it) } // this is cached from DB so I hope it's fine?
    
    fun bells_add(obj: Bell) {
        if(deleted)
            throw Exception("Trying to set property 'bells' on deleted object <$uri>")
        _inkbltRef_bells.add(obj.uri)
    
        val template = ParameterizedSparqlString("INSERT { ?inkblt1 <http://rec0de.net/ns/bike#hasBell> ?n. ?anchor <http://rec0de.net/ns/bike#hasFrame> ?inkblt1. } WHERE {  }")
        template.setIri("anchor", uri)
        template.setParam("n", ResourceFactory.createResource(obj.uri).asNode())
        val cn = ComplexPropertyAdd(template.asUpdate())
        Inkblot.changelog.add(cn)
        markDirty()
    }
    
    fun bells_remove(obj: Bell) {
        if(deleted)
            throw Exception("Trying to set property 'bells' on deleted object <$uri>")
        _inkbltRef_bells.remove(obj.uri)
    
        val template = ParameterizedSparqlString("DELETE { ?inkblt1 <http://rec0de.net/ns/bike#hasBell> ?o. } WHERE { ?anchor <http://rec0de.net/ns/bike#hasFrame> ?inkblt1. ?inkblt1 <http://rec0de.net/ns/bike#hasBell> _:b13. }")
        template.setIri("anchor", uri)
        template.setParam("o", ResourceFactory.createResource(obj.uri).asNode())
        val cn = ComplexPropertyRemove(template.asUpdate())
        Inkblot.changelog.add(cn)
        markDirty()
    }
    
    var mfgYear: Int? = mfgYear
        set(value) {
            if(deleted)
                throw Exception("Trying to set property 'mfgYear' on deleted object <$uri>")
    
            if(value == null) {
                // Unset value
                val oldValueNode = ResourceFactory.createTypedLiteral(field).asNode()
                val cn = CommonPropertyRemove(uri, "http://rec0de.net/ns/bike#mfgYear", oldValueNode)
                Inkblot.changelog.add(cn)
            }
            else if(field == null) {
                // Pure insertion
                val newValueNode = ResourceFactory.createTypedLiteral(value).asNode()
    
                val cn = CommonPropertyAdd(uri, "http://rec0de.net/ns/bike#mfgYear", newValueNode)
                Inkblot.changelog.add(cn)
            }
            else {
                // Update value
                val oldValueNode = ResourceFactory.createTypedLiteral(field).asNode()
                val newValueNode = ResourceFactory.createTypedLiteral(value).asNode()
    
                val cn = CommonPropertyChange(uri, "http://rec0de.net/ns/bike#mfgYear", oldValueNode, newValueNode!!)
                Inkblot.changelog.add(cn)
            }
    
            field = value
            markDirty()
        }
    
    fun merge(other: Bike) {
        if(deleted || other.deleted)
            throw Exception("Trying to merge into/out of deleted objects <$uri> / <${other.uri}>")
    
        _inkbltRef_frontWheel = other._inkbltRef_frontWheel // avoids triggering lazy loading
        _inkbltRef_backWheel = other._inkbltRef_backWheel // avoids triggering lazy loading
        mfgYear = other.mfgYear ?: mfgYear
        _inkbltRef_bells.addAll(other._inkbltRef_bells)
    
        other.delete(uri)
        markDirty()
    }
}