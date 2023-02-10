package inkblot.runtime

import org.apache.jena.query.*
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP
import org.apache.jena.sparql.syntax.ElementFilter
import org.apache.jena.sparql.syntax.ElementGroup
import org.apache.jena.sparql.util.ExprUtils

abstract class SemanticObjectFactory<Obj> {
    protected abstract val anchor: String
    protected abstract val query: ParameterizedSparqlString

    fun loadFromURI(uri: String): Obj {
        if(Inkblot.loadedObjects.containsKey(uri))
            return Inkblot.loadedObjects[uri] as Obj

        val template = query.copy()
        template.setIri(anchor, uri)
        // we have to get rid of the raw URI in the select clause
        val queryString = template.toString().replaceFirst("<$uri>", "")
        val q = QueryFactory.create(queryString)

        println(q)
        val execCtx = QueryExecutionHTTP.service(Inkblot.endpoint, q)
        val res = execCtx.execSelect()

        val list = res.asSequence().toList()
        res.close()

        if(list.isEmpty())
            throw Exception("Loading $anchor by URI <$uri> failed, no such $anchor")

        return instantiateSingleResult(list)!!
    }

    // Load all only returns objects that already exist in the datastore
    // to ensure all objects are loaded, commit to data store before
    fun commitAndLoadAll(): List<Obj> {
        Inkblot.commit()
        val query = QueryExecutionHTTP.service(Inkblot.endpoint, query.asQuery())
        return instantiateFromResultSet(query.execSelect())
    }

    // loadSelected is weird because the filter operates on the data store
    // if we allow using this while not synchronized to data store, we'll get inconsistencies
    // (e.g. results for x == 0 including objects already in memory where x has been changed to 1)
    fun commitAndLoadSelected(filterStr: String): List<Obj> {
        Inkblot.commit()
        val selectQuery = query.copy().asQuery() // copy just to be sure
        val filtered = addFilterToSelect(selectQuery, filterStr)
        val execCtx = QueryExecutionHTTP.service(Inkblot.endpoint, filtered)
        return instantiateFromResultSet(execCtx.execSelect())
    }

    // regardless of the load operation, objects already loaded into memory should be re-used
    private fun instantiateFromResultSet(res: ResultSet): List<Obj> {
        val grouped = res.asSequence().groupBy { it.getResource(anchor).uri }
        res.close()
        val uris = grouped.keys
        val created = grouped.filter { !Inkblot.loadedObjects.containsKey(it.key) }.values.map { instantiateSingleResult(it)!! }  // entity groups are never empty here, non-null assert is fine
        val previouslyLoaded = uris.filter { Inkblot.loadedObjects.containsKey(it) }.map { Inkblot.loadedObjects[it]!! as Obj }

        return previouslyLoaded + created
    }

    protected abstract fun instantiateSingleResult(lines: List<QuerySolution>): Obj?

    private fun addFilterToSelect(originalQuery: Query, filterString: String): Query {
        val select = originalQuery.queryPattern
        val filterExpr = ExprUtils.parse(filterString)
        val filter = ElementFilter(filterExpr)

        val body = ElementGroup()
        body.addElement(select)
        body.addElement(filter)

        val q = QueryFactory.make()
        q.queryPattern = body
        q.setQuerySelectType()
        originalQuery.resultVars.forEach { q.addResultVar(it) }

        return q
    }
}