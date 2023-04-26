package org.smolang.inkblot.runtime

import org.apache.jena.query.*
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP
import org.apache.jena.sparql.syntax.ElementFilter
import org.apache.jena.sparql.syntax.ElementGroup
import org.apache.jena.sparql.util.ExprUtils

/*
Common functionality shared across all factory classes.
Contains large parts of object instantiation logic and data loading functions. Also handles on-line data validation using generated SPARQL queries.
 */
abstract class SemanticObjectFactory<Obj>(validateQueries: List<String>, private val debugName: String) {
    protected abstract val anchor: String
    protected abstract val query: ParameterizedSparqlString
    private val validatingQueries: MutableList<Query>

    init {
        Inkblot.forceInit()
        validatingQueries = validateQueries.map { QueryFactory.create(it) }.toMutableList()
        validateOnlineData(exceptionOnFailure = true)
    }

    // Run validation queries to check that our assumptions about types / multiplicities match reality
    fun validateOnlineData(exceptionOnFailure: Boolean = false) {
        validatingQueries.forEach { query ->
            val execCtx = QueryExecutionHTTP.service(Inkblot.endpoint, query)
            val res = execCtx.execSelect()
            if(res.hasNext()) {
                val stringQuery = prettifySparql(query)

                if(exceptionOnFailure)
                    throw Exception("Validation query failed for $debugName, data at endpoint looks inconsistent. Query: $stringQuery")

                Inkblot.violation(ValidationQueryFailure(debugName, stringQuery))
            }
        }
    }

    // Allow use of existing validation infrastructure to run custom checks
    // Validating queries are expected to return empty result on success and non-empty on failure
    fun addValidatingQuery(query: Query) {
        validatingQueries.add(query)
    }

    fun loadFromURI(uri: String): Obj {
        if(Inkblot.loadedObjects.containsKey(uri))
            return Inkblot.loadedObjects[uri] as Obj

        val template = query.toString()
        // insert appropriate binding of anchor variable
        val queryString = template.replaceFirst("{", "{ BIND(<$uri> AS ?$anchor) ")
        val q = QueryFactory.create(queryString)
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
        val previouslyLoaded = uris.filter { Inkblot.loadedObjects.containsKey(it) }.map { Inkblot.loadedObjects[it]!! as Obj }
        val created = grouped.filter { !Inkblot.loadedObjects.containsKey(it.key) }.values.map { instantiateSingleResult(it)!! }  // entity groups are never empty here, non-null assert is fine

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

    private fun prettifySparql(query: Query): String {
        val singleLine = query.toString().replace("\n", " ")
        // trim excessive spacing & indentation
        var despaced = singleLine.replace(Regex("\\s+"), " ")
        // replace spaces in front of ; . and ) as well as at start and end of query
        despaced = despaced.replace(Regex("\\s([;\\.\\)])")) { match -> match.groupValues[1] }.trim()
        // replace spaces following (
        despaced = despaced.replace(Regex("\\(\\s+"), "(")
        return despaced
    }
}