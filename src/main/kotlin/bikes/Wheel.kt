package bikes

import inkblot.runtime.*
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.QuerySolution
import org.apache.jena.rdf.model.ResourceFactory

object WheelFactory : SemanticObjectFactory<Wheel>() {
    override val query = "PREFIX  bk:   <http://rec0de.net/ns/bike#>  SELECT  ?wheel ?dia ?mfgD ?mfgN WHERE   { ?wheel  a            bk:wheel ;             bk:diameter  ?dia     OPTIONAL       { ?wheel  bk:mfgDate  ?mfgD }     OPTIONAL       { ?wheel  bk:mfgName  ?mfgN }   } "
    override val anchor = "wheel"
    
    fun create(dia: Double, mfgD: Int?, mfgN: List<String>): Wheel {
        val uri = "http://rec0de.net/ns/bike#wheel" + Inkblot.freshSuffixFor("wheel")
    
        // set non-null parameters and create object
        val template = ParameterizedSparqlString("INSERT DATA { ?anchor <http://rec0de.net/ns/bike#diameter> ?dia. ?anchor <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://rec0de.net/ns/bike#wheel>. }")
        template.setIri("anchor", uri)
        template.setParam("dia", ResourceFactory.createTypedLiteral(dia))
    
        val update = template.asUpdate()
    
        // initialize nullable functional properties
        if(mfgD != null) {
            val partialUpdate = ParameterizedSparqlString("INSERT DATA { ?anchor <http://rec0de.net/ns/bike#mfgDate> ?v. }")
            partialUpdate.setIri("anchor", uri)
            partialUpdate.setParam("v", ResourceFactory.createTypedLiteral(mfgD))
            partialUpdate.asUpdate().forEach { update.add(it) }
        }
    
    
        // initialize non-functional properties
        mfgN.forEach {
            val partialUpdate = ParameterizedSparqlString("INSERT DATA { ?anchor <http://rec0de.net/ns/bike#mfgName> ?v. }")
            partialUpdate.setIri("wheel", uri)
            partialUpdate.setParam("mfgN", ResourceFactory.createTypedLiteral(it))
            partialUpdate.asUpdate().forEach { part -> update.add(part) }
        }
    
        Inkblot.changelog.add(CreateNode(update))
        return Wheel(uri, dia, mfgD, mfgN)
    }
    
    override fun instantiateSingleResult(lines: List<QuerySolution>): Wheel? {
        if(lines.isEmpty())
            return null
    
        val uri = lines.first().getResource("wheel").uri
    
        // for functional properties we can read the first only, as all others have to be the same
        val dia = Inkblot.types.literalToDouble(lines.first().getLiteral("dia"))
        val mfgD = Inkblot.types.literalToNullableInt(lines.first().getLiteral("mfgD"))
    
    
        // for higher cardinality properties, we have to collect all distinct values
        val mfgN = lines.mapNotNull { Inkblot.types.literalToNullableString(it.getLiteral("mfgN")) }.distinct()
    
        return Wheel(uri, dia, mfgD, mfgN) 
    }
}

class Wheel internal constructor(uri: String, dia: Double, mfgD: Int?, mfgN: List<String>) : SemanticObject(uri) {
    companion object {
        fun create(dia: Double, mfgD: Int?, mfgN: List<String>) = WheelFactory.create(dia, mfgD, mfgN)
        fun loadAll() = WheelFactory.loadAll()
        fun loadSelected(filter: String) = WheelFactory.loadSelected(filter)
        fun loadFromURI(uri: String) = WheelFactory.loadFromURI(uri)
    }
    
    var diameter: Double = dia
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
    
    var mfgYear: Int? = mfgD
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
    
    private val inkblt_mfgNames = mfgN.toMutableList()
    
    val mfgNames: List<String>
        get() = inkblt_mfgNames
    
    fun mfgNames_add(data: String) {
        if(deleted)
            throw Exception("Trying to set property 'mfgNames' on deleted object <$uri>")
        inkblt_mfgNames.add(data)
    
        val cn = CommonPropertyAdd(uri, "http://rec0de.net/ns/bike#mfgName", ResourceFactory.createTypedLiteral(data).asNode())
        Inkblot.changelog.add(cn)
        markDirty()
    }
    
    fun mfgNames_remove(data: String) {
        if(deleted)
            throw Exception("Trying to set property 'mfgNames' on deleted object <$uri>")
        inkblt_mfgNames.remove(data)
    
        val cn = CommonPropertyRemove(uri, "http://rec0de.net/ns/bike#mfgName", ResourceFactory.createTypedLiteral(data).asNode())
        Inkblot.changelog.add(cn)
        markDirty()
    }
    
    // TODO: Merge
}