package bikes

import inkblot.runtime.*
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.QuerySolution
import org.apache.jena.rdf.model.ResourceFactory

object BikeFactory : SemanticObjectFactory<Bike>() {
    override val query = "PREFIX  bk:   <http://rec0de.net/ns/bike#>  SELECT  ?bike ?mfg ?fw ?bw ?bells WHERE   { ?bike  a              bk:bike ;            bk:hasFrame    _:b0 .     _:b0   bk:frontWheel  ?fw     OPTIONAL       { ?bike  bk:hasFrame   _:b1 .         _:b1   bk:backWheel  ?bw       }     OPTIONAL       { ?bike  bk:mfgDate  ?mfg }     OPTIONAL       { ?bike  bk:hasFrame  _:b2 .         _:b2   bk:hasBell   ?bells       }   } "
    override val anchor = "bike"
    
    fun create(fw: Wheel, bw: Wheel?, bells: List<Bell>, mfg: Int?): Bike {
        val uri = "http://rec0de.net/ns/bike#bike" + Inkblot.freshSuffixFor("bike")
    
        // set non-null parameters and create object
        val template = ParameterizedSparqlString("INSERT DATA { ?anchor <http://rec0de.net/ns/bike#hasFrame> [<http://rec0de.net/ns/bike#frontWheel> ?fw]. ?anchor <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://rec0de.net/ns/bike#bike>. }")
        template.setIri("anchor", uri)
        template.setIri("fw", fw.uri)
    
        val update = template.asUpdate()
    
        // initialize nullable functional properties
        if(bw != null) {
            val partialUpdate = ParameterizedSparqlString("INSERT DATA { ?anchor <http://rec0de.net/ns/bike#hasFrame> [<http://rec0de.net/ns/bike#backWheel> ?v]. }")
            partialUpdate.setIri("anchor", uri)
            partialUpdate.setIri("v", bw.uri)
            partialUpdate.asUpdate().forEach { update.add(it) }
        }
    
        if(mfg != null) {
            val partialUpdate = ParameterizedSparqlString("INSERT DATA { ?anchor <http://rec0de.net/ns/bike#mfgDate> ?v. }")
            partialUpdate.setIri("anchor", uri)
            partialUpdate.setParam("v", ResourceFactory.createTypedLiteral(mfg))
            partialUpdate.asUpdate().forEach { update.add(it) }
        }
    
    
        // initialize non-functional properties
        bells.forEach {
            val partialUpdate = ParameterizedSparqlString("INSERT DATA { ?anchor <http://rec0de.net/ns/bike#hasFrame> [<http://rec0de.net/ns/bike#hasBell> ?v]. }")
            partialUpdate.setIri("bike", uri)
            partialUpdate.setIri("bells", it.uri)
            partialUpdate.asUpdate().forEach { part -> update.add(part) }
        }
    
        Inkblot.changelog.add(CreateNode(update))
        return Bike(uri, fw.uri, bw?.uri, bells.map{ it.uri }, mfg)
    }
    
    override fun instantiateSingleResult(lines: List<QuerySolution>): Bike? {
        if(lines.isEmpty())
            return null
    
        val uri = lines.first().getResource("bike").uri
    
        // for functional properties we can read the first only, as all others have to be the same
        val fw = lines.first().getResource("fw").uri
        val bw = lines.first().getResource("bw")?.uri
        val mfg = Inkblot.types.literalToNullableInt(lines.first().getLiteral("mfg"))
    
    
        // for higher cardinality properties, we have to collect all distinct values
        val bells = lines.mapNotNull { it.getResource("bells")?.uri }.distinct()
    
        return Bike(uri, fw, bw, bells, mfg) 
    }
}

class Bike internal constructor(uri: String, fw: String, bw: String?, bells: List<String>, mfg: Int?) : SemanticObject(uri) {
    companion object {
        fun create(fw: Wheel, bw: Wheel?, bells: List<Bell>, mfg: Int?) = BikeFactory.create(fw, bw, bells, mfg)
        fun loadAll() = BikeFactory.loadAll()
        fun loadSelected(filter: String) = BikeFactory.loadSelected(filter)
        fun loadFromURI(uri: String) = BikeFactory.loadFromURI(uri)
    }
    
    private var _inkbltRef_frontWheel: String = fw
    var frontWheel: Wheel
        get() = Wheel.loadFromURI(_inkbltRef_frontWheel)
    set(value) {
        if(deleted)
            throw Exception("Trying to set property 'frontWheel' on deleted object <$uri>")
    
        val template = ParameterizedSparqlString("DELETE { ?inkblt1 <http://rec0de.net/ns/bike#frontWheel> ?o. } INSERT { ?inkblt1 <http://rec0de.net/ns/bike#frontWheel> ?n.} WHERE { ?anchor <http://rec0de.net/ns/bike#hasFrame> [<http://rec0de.net/ns/bike#frontWheel> ?inkblt1]. }")
        template.setIri("anchor", uri)
        template.setParam("o", ResourceFactory.createResource(value.uri).asNode())
        template.setParam("n", ResourceFactory.createResource(value.uri).asNode())
        val cn = ComplexPropertyRemove(template.asUpdate())
        Inkblot.changelog.add(cn)
    
        _inkbltRef_frontWheel = value.uri
        markDirty()
    }
    
    private var _inkbltRef_backWheel: String? = bw
    var backWheel: Wheel? = null
        get() {
            return if(field == null && _inkbltRef_backWheel != null)
                WheelFactory.loadFromURI(_inkbltRef_backWheel!!)
            else
                field
        }
        set(value) {
            if(deleted)
                throw Exception("Trying to set property 'backWheel' on deleted object <$uri>")
            field = value
    
            val oldValueNode = ResourceFactory.createResource(_inkbltRef_backWheel).asNode()
            val newValueNode = ResourceFactory.createResource(value?.uri).asNode()
    
            if(value == null) {
                // Unset value
                val template = ParameterizedSparqlString("DELETE { ?inkblt2 <http://rec0de.net/ns/bike#backWheel> ?o. } WHERE { ?anchor <http://rec0de.net/ns/bike#hasFrame> ?inkblt2. }")
                template.setIri("anchor", uri)
                template.setParam("o", oldValueNode)
                val cn = ComplexPropertyRemove(template.asUpdate())
                Inkblot.changelog.add(cn)
            }
            else if(_inkbltRef_backWheel == null) {
                // Pure insertion
                val template = ParameterizedSparqlString("INSERT { ?inkblt3 <http://rec0de.net/ns/bike#backWheel> ?o. } WHERE { ?anchor <http://rec0de.net/ns/bike#hasFrame> ?inkblt3. }")
                template.setIri("anchor", uri)
                template.setParam("o", newValueNode)
                val cn = ComplexPropertyAdd(template.asUpdate())
                Inkblot.changelog.add(cn)
            }
            else {
                // Change value
                val template = ParameterizedSparqlString("DELETE { ?inkblt4 <http://rec0de.net/ns/bike#backWheel> ?o. } INSERT { ?inkblt4 <http://rec0de.net/ns/bike#backWheel> ?n.} WHERE { ?anchor <http://rec0de.net/ns/bike#hasFrame> [<http://rec0de.net/ns/bike#backWheel> ?inkblt4]. }")
                template.setIri("anchor", uri)
                template.setParam("o", newValueNode)
                template.setParam("n", newValueNode)
                val cn = ComplexPropertyRemove(template.asUpdate())
                Inkblot.changelog.add(cn)
            }
    
            _inkbltRef_backWheel = value?.uri
            markDirty()
        }
    
    private val _inkbltRef_bells = bells.toMutableSet()
    val bells: List<Bell>
        get() = _inkbltRef_bells.map { BellFactory.loadFromURI(it) } // this is cached from DB so I hope it's fine?
    
    fun bells_add(obj: Bell) {
        if(deleted)
            throw Exception("Trying to set property 'bells' on deleted object <$uri>")
        _inkbltRef_bells.add(obj.uri)
    
        val template = ParameterizedSparqlString("INSERT { ?inkblt5 <http://rec0de.net/ns/bike#hasBell> ?o. } WHERE { ?anchor <http://rec0de.net/ns/bike#hasFrame> ?inkblt5. }")
        template.setIri("anchor", uri)
        template.setParam("o", ResourceFactory.createResource(obj.uri).asNode())
        val cn = ComplexPropertyAdd(template.asUpdate())
        Inkblot.changelog.add(cn)
        markDirty()
    }
    
    fun bells_remove(obj: Bell) {
        if(deleted)
            throw Exception("Trying to set property 'bells' on deleted object <$uri>")
        _inkbltRef_bells.remove(obj.uri)
    
        val template = ParameterizedSparqlString("DELETE { ?inkblt6 <http://rec0de.net/ns/bike#hasBell> ?o. } WHERE { ?anchor <http://rec0de.net/ns/bike#hasFrame> ?inkblt6. }")
        template.setIri("anchor", uri)
        template.setParam("o", ResourceFactory.createResource(obj.uri).asNode())
        val cn = ComplexPropertyRemove(template.asUpdate())
        Inkblot.changelog.add(cn)
        markDirty()
    }
    
    var mfgYear: Int? = mfg
        set(value) {
            if(deleted)
                throw Exception("Trying to set property 'mfgYear' on deleted object <$uri>")
    
            val oldValueNode = ResourceFactory.createTypedLiteral(field).asNode()
            val newValueNode = ResourceFactory.createTypedLiteral(value).asNode()
            if(value == null) {
                // Unset value
                val cn = CommonPropertyRemove(uri, "http://rec0de.net/ns/bike#mfgDate", oldValueNode)
                Inkblot.changelog.add(cn)
            }
            else if(field == null) {
                // Pure insertion
                val cn = CommonPropertyAdd(uri, "http://rec0de.net/ns/bike#mfgDate", newValueNode)
                Inkblot.changelog.add(cn)
            }
            else {
                // Update value
                val cn = CommonPropertyChange(uri, "http://rec0de.net/ns/bike#mfgDate", oldValueNode, newValueNode!!)
                Inkblot.changelog.add(cn)
            }
    
            field = value
            markDirty()
        }
    
    // TODO: Merge
}