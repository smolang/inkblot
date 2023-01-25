package bikes

import inkblot.runtime.*
import org.apache.jena.query.ParameterizedSparqlString
import org.apache.jena.query.QuerySolution
import org.apache.jena.rdf.model.ResourceFactory

object BikeFactory : SemanticObjectFactory<Bike>() {
    private const val prefix = "http://rec0de.net/ns/bike#"
    override val query = "PREFIX bk: <${prefix}> SELECT ?bike ?mfg ?fw ?bw ?bells WHERE { ?bike a bk:bike; bk:hasFrame [bk:frontWheel ?fw] OPTIONAL { ?bike bk:hasFrame [bk:backWheel ?bw] } OPTIONAL { ?bike bk:mfgDate ?mfg } OPTIONAL { ?bike bk:hasFrame [bk:hasBell ?bells] } }"
    override val anchor = "bike"

    fun create(mfgDate: Int?, frontWheel: Wheel, backWheel: Wheel?, bells: List<Bell>): Bike {
        val uri = prefix + "bike" + Inkblot.freshSuffixFor("bike")


        val template = ParameterizedSparqlString("INSERT DATA { ?bike a bk:bike; bk:hasFrame [bk:frontWheel ?fw] }")
        template.setNsPrefix("bk", prefix)
        template.setIri("bike", uri)
        template.setIri("fw", frontWheel.uri)
        val update = template.asUpdate()

        if(mfgDate != null) {
            val mfgUpdate = ParameterizedSparqlString("INSERT DATA { ?bike bk:mfgDate ?mfg }")
            mfgUpdate.setNsPrefix("bk", prefix)
            mfgUpdate.setIri("bike", uri)
            mfgUpdate.setParam("mfg", ResourceFactory.createTypedLiteral(mfgDate))
            update.add(mfgUpdate.asUpdate().first())
        }

        if(backWheel != null) {
            val mfgUpdate = ParameterizedSparqlString("INSERT DATA { ?bike bk:hasFrame [bk:backWheel ?bw] }")
            mfgUpdate.setNsPrefix("bk", prefix)
            mfgUpdate.setIri("bike", uri)
            mfgUpdate.setIri("bw", backWheel.uri)
            update.add(mfgUpdate.asUpdate().first())
        }

        bells.forEach {
            val bellUpdate = ParameterizedSparqlString("INSERT DATA { ?bike bk:hasFrame [bk:hasBell ?bell] }")
            bellUpdate.setNsPrefix("bk", prefix)
            bellUpdate.setIri("bike", uri)
            bellUpdate.setIri("bell", it.uri)
            update.add(bellUpdate.asUpdate().first())
        }

        Inkblot.changelog.add(CreateNode(update))

        return Bike(uri, mfgDate, frontWheel.uri, backWheel?.uri, bells.map { it.uri })
    }

    override fun instantiateSingleResult(lines: List<QuerySolution>): Bike? {
        if(lines.isEmpty())
            return null

        // for single cardinality properties we can read the first only, as all others have to be the same
        val uri = lines.first().getResource("bike").uri
        val mfgD = lines.first().getLiteral("mfg")?.int

        val fw = lines.first().getResource("fw").uri
        val bw = lines.first().getResource("bw")?.uri

        // for higher cardinality properties, we have to collect all distinct ones
        val bells = lines.mapNotNull { it.getResource("bells")?.uri }.distinct()

        return Bike(uri, mfgD, fw, bw, bells)
    }
}

class Bike internal constructor(uri: String, mfgDate: Int?, frontWheel: String, backWheel: String?, bells: List<String>) : SemanticObject(uri) {
    companion object {
        private const val prefix = "http://rec0de.net/ns/bike#"
        fun create(mfgDate: Int?, frontWheel: Wheel, backWheel: Wheel?, bells: List<Bell>) = BikeFactory.create(mfgDate, frontWheel, backWheel, bells)
        fun loadFromURI(uri: String) = BikeFactory.loadFromURI(uri)
        fun loadAll() = BikeFactory.loadAll()
        fun loadSelected(filterStr: String) = BikeFactory.loadSelected(filterStr)
    }

    // Single cardinality properties can make do with just a field
    var mfgDate: Int? = mfgDate
        set(value) {
            if(deleted)
                throw Exception("Trying to set property 'mfgDate' on deleted object <$uri>")
            field = value

            if(value == null)
                Inkblot.changelog.add(UnsetSingletProperty(uri, prefix + "mfgDate"))
            else
                Inkblot.changelog.add(ChangeSingletProperty(uri, prefix + "mfgDate", ResourceFactory.createTypedLiteral(mfgDate).asNode()))
            markDirty()
        }

    // Singlet object references
    private var _inkbltRef_frontWheel: String = frontWheel
    var frontWheel: Wheel
        get() = Wheel.loadFromURI(_inkbltRef_frontWheel)
        set(value) {
            _inkbltRef_frontWheel = value.uri

            val template = ParameterizedSparqlString("DELETE { ?frame bk:frontWheel ?x } INSERT { ?frame bk:frontWheel ?w } WHERE { ?bike bk:hasFrame ?frame. ?frame bk:frontWheel ?x }")
            template.setNsPrefix("bk", prefix)
            template.setIri("bike", uri)
            template.setIri("w", value.uri)
            Inkblot.changelog.add(ComplexPropertyChange(template.asUpdate()))
            markDirty()
        }

    private var _inkbltRef_backWheel: String? = backWheel
    var backWheel: Wheel? = null
        get() {
            return if(field == null && _inkbltRef_backWheel != null)
                Wheel.loadFromURI(_inkbltRef_backWheel!!)
            else
                field
        }
        set(value) {
            field = value
            _inkbltRef_backWheel = value?.uri

            val template = if(value != null)
                ParameterizedSparqlString("DELETE { ?frame bk:backWheel ?x } WHERE { ?bike bk:hasFrame ?frame. ?frame bk:backWheel ?x }; INSERT { ?frame bk:backWheel ?w } WHERE { ?bike bk:hasFrame ?frame }")
            else
                ParameterizedSparqlString("DELETE { ?frame bk:backWheel ?x } WHERE { ?bike bk:hasFrame ?frame. ?frame bk:backWheel ?x }")
            template.setNsPrefix("bk", prefix)
            template.setIri("bike", uri)
            if(value != null)
                template.setIri("w", value.uri)

            Inkblot.changelog.add(ComplexPropertyChange(template.asUpdate()))
            markDirty()
        }

    // Higher cardinality object references need some more trickery
    private val _inkbltRef_bells = bells.toMutableSet()
    val bells: List<Bell>
        get() = _inkbltRef_bells.map { Bell.loadFromURI(it) } // this is cached from DB so i hope it's fine?

    fun bells_add(bell: Bell) {
        _inkbltRef_bells.add(bell.uri)

        val template = ParameterizedSparqlString("INSERT { ?frame bk:hasBell ?o } WHERE { ?bike bk:hasFrame ?frame }")
        template.setNsPrefix("bk", prefix)
        template.setIri("bike", uri)
        template.setIri("o", bell.uri)
        Inkblot.changelog.add(ComplexPropertyAdd(template.asUpdate()))
        markDirty()
    }

    fun bells_remove(bell: Bell) {
        _inkbltRef_bells.remove(bell.uri)

        val template = ParameterizedSparqlString("DELETE { ?frame bk:hasBell ?o } WHERE { ?bike bk:hasFrame ?frame }")
        template.setNsPrefix("bk", prefix)
        template.setIri("bike", uri)
        template.setIri("o", bell.uri)
        Inkblot.changelog.add(ComplexPropertyRemove(template.asUpdate()))
        markDirty()
    }

    // Merge functionality
    fun merge(other: Bike) {
        if(deleted || other.deleted)
            throw Exception("Trying to merge into/out of deleted objects <$uri> / <${other.uri}>")

        // non-null asserted singlet properties can just be overwritten
        _inkbltRef_frontWheel = other._inkbltRef_frontWheel // sneaking around lazy loading

        // nullable singlet properties are overwritten if non-null in other
        if(other.mfgDate != null)
            mfgDate = other.mfgDate

        // we can at least check for null-ness before triggering lazy load (does that make sense?)
        if(other._inkbltRef_backWheel != null)
            backWheel = other.backWheel

        // non-singlet properties are just copied over (todo: enforcing size limits?)
        _inkbltRef_bells.addAll(other._inkbltRef_bells)

        other.delete(uri)
        markDirty()
    }

    override fun delete() {
        markDirty()
        deleted = true

        val template = ParameterizedSparqlString("DELETE { ?f ?p ?o} WHERE { ?bike bk:hasFrame ?f. ?f ?p ?o }; DELETE { ?s ?p ?f } WHERE { ?bike bk:hasFrame ?f. ?s ?p ?f }; DELETE WHERE { ?s ?p ?bike }; DELETE WHERE { ?bike ?p ?o }")
        template.setNsPrefix("bk", prefix)
        template.setIri("bike", uri)

        Inkblot.changelog.add(ComplexDelete(template.asUpdate()))
    }
}