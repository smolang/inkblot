package bikes

import inkblot.runtime.*
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.QuerySolution
import org.apache.jena.rdf.model.ResourceFactory

object WheelFactory : SemanticObjectFactory<Wheel>() {
    private const val prefix = "http://rec0de.net/ns/bike#"
    override val query = "PREFIX bk: <$prefix> SELECT ?wheel ?dia ?mfgD ?mfgN WHERE { ?wheel a bk:wheel; bk:diameter ?dia. OPTIONAL { ?wheel bk:mfgDate ?mfgD } OPTIONAL {?wheel bk:mfgName ?mfgN } }"
    override val anchor = "wheel"

    fun create(diameter: Double, mfgDate: Int?, mfgName: List<String>): Wheel {
        val uri = prefix + "wheel" + Inkblot.freshSuffixFor("wheel")

        val template = ParameterizedSparqlString("INSERT DATA { ?wheel a bk:wheel. ?wheel bk:diameter ?dia }")
        template.setNsPrefix("bk", prefix)
        template.setIri("wheel", uri)
        template.setParam("dia", ResourceFactory.createTypedLiteral(diameter))
        val update = template.asUpdate()

        if(mfgDate != null) {
            val mfgUpdate = ParameterizedSparqlString("INSERT DATA { ?wheel bk:mfgDate ?mfg }")
            mfgUpdate.setNsPrefix("bk", prefix)
            mfgUpdate.setIri("wheel", uri)
            mfgUpdate.setParam("mfg", ResourceFactory.createTypedLiteral(mfgDate))
            update.add(mfgUpdate.asUpdate().first())
        }

        mfgName.forEach {
            val bellUpdate = ParameterizedSparqlString("INSERT DATA { ?wheel bk:mfgName ?name }")
            bellUpdate.setNsPrefix("bk", prefix)
            bellUpdate.setIri("wheel", uri)
            bellUpdate.setParam("name", ResourceFactory.createTypedLiteral(it))
            update.add(bellUpdate.asUpdate().first())
        }

        Inkblot.changelog.add(CreateNode(update))

        return Wheel(uri, diameter, mfgDate, mfgName)
    }

    override fun instantiateSingleResult(lines: List<QuerySolution>): Wheel? {
        if(lines.isEmpty())
            return null

        // for single cardinality properties we can read the first only, as all others have to be the same
        val uri = lines.first().getResource("wheel").uri
        val diameter = lines.first().getLiteral("dia").double
        val mfgD = lines.first().getLiteral("mfgD")?.int

        // for higher cardinality properties, we have to collect all distinct ones
        val mfgN = lines.mapNotNull { it.getLiteral("mfgN")?.string }.distinct()

        return Wheel(uri, diameter, mfgD, mfgN)
    }
}

class Wheel internal constructor(uri: String, diameter: Double, mfgDate: Int?, mfgName: List<String>) : SemanticObject(uri) {
    companion object {
        private const val prefix = "http://rec0de.net/ns/bike#"
        fun create(diameter: Double, mfgDate: Int?, mfgName: List<String>) = WheelFactory.create(diameter, mfgDate, mfgName)
        fun loadFromURI(uri: String) = WheelFactory.loadFromURI(uri)
        fun loadAll() = WheelFactory.loadAll()
        fun loadSelected(filter: String) = WheelFactory.loadSelected(filter)
    }

    // Single cardinality properties can make do with just a field
    var diameter: Double = diameter
        set(value) {
            if(deleted)
                throw Exception("Trying to set property 'diameter' on deleted object <$uri>")
            field = value

            val valueNode = ResourceFactory.createTypedLiteral(diameter).asNode()
            Inkblot.changelog.add(ChangeSingletProperty(uri, prefix + "diameter", valueNode))
            markDirty()
        }

    var mfgDate: Int? = mfgDate
        set(value) {
            if(deleted)
                throw Exception("Trying to set property 'mfgDate' on deleted object <$uri>")
            field = value

            if(value == null)
                Inkblot.changelog.add(UnsetSingletProperty(uri, prefix + "mfgDate"))
            else
                Inkblot.changelog.add(
                    ChangeSingletProperty(uri,prefix + "mfgDate", ResourceFactory.createTypedLiteral(mfgDate).asNode())
                )
            markDirty()
        }

    // Higher cardinality properties need custom add/remove methods

    private val inkblt_mfgName = mfgName.toMutableList()

    val mfgName: List<String>
        get() = inkblt_mfgName

    fun mfgName_add(data: String) {
        if(deleted)
            throw Exception("Trying to set property 'mfgName' on deleted object <$uri>")
        inkblt_mfgName.add(data)
        Inkblot.changelog.add(CommonPropertyAdd(uri, prefix + "mfgName", ResourceFactory.createTypedLiteral(data).asNode()))
        markDirty()
    }

    fun mfgName_remove(data: String) {
        if(deleted)
            throw Exception("Trying to remove property 'mfgName' on deleted object <$uri>")
        inkblt_mfgName.remove(data)
        Inkblot.changelog.add(CommonPropertyRemove(uri, prefix + "mfgName", ResourceFactory.createTypedLiteral(data).asNode()))
        markDirty()
    }

    // Merge functionality
    fun merge(other: Wheel) {
        if(deleted || other.deleted)
            throw Exception("Trying to merge into/out of deleted objects <$uri> / <${other.uri}>")

        // non-null asserted singlet properties can just be overwritten
        diameter = other.diameter

        // nullable singlet properties are overwritten if non-null in other
        if(other.mfgDate != null)
            mfgDate = other.mfgDate

        // non-singlet properties are just copied over (todo: enforcing size limits?)
        other.mfgName.forEach { mfgName_add(it) }

        other.delete(uri)
        markDirty()
    }
}