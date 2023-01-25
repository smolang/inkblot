package inkblot.runtime

import org.apache.jena.query.*
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP
import org.apache.jena.sparql.exec.http.QueryExecutionHTTPBuilder
import org.apache.jena.sparql.syntax.ElementFilter
import org.apache.jena.sparql.syntax.ElementGroup
import org.apache.jena.sparql.util.ExprUtils

abstract class SemanticObjectFactory<Obj> {
    protected abstract val anchor: String
    protected abstract val query: String

    fun loadFromURI(uri: String): Obj {
        if(Inkblot.loadedObjects.containsKey(uri))
            return Inkblot.loadedObjects[uri] as Obj

        val builder = QueryExecutionHTTPBuilder.create()
        builder.endpoint(Inkblot.endpoint)
        builder.query(query)
        builder.substitution(anchor, ResourceFactory.createResource(uri).asNode())
        val res = builder.select()

        val list = res.asSequence().toList()
        res.close()

        if(list.isEmpty())
            throw Exception("Loading $anchor by URI <$uri> failed, no such $anchor")

        return instantiateSingleResult(list)!!
    }

    fun loadAll(): List<Obj> {
        val query = QueryExecutionHTTP.service(Inkblot.endpoint, query)
        return instantiateFromResultSet(query.execSelect())
    }

    fun loadSelected(filterStr: String): List<Obj> {
        val selectQuery = ParameterizedSparqlString(query).asQuery()
        val filtered = addFilterToSelect(selectQuery, filterStr)
        val execCtx = QueryExecutionHTTP.service(Inkblot.endpoint, filtered)
        return instantiateFromResultSet(execCtx.execSelect())
    }

    private fun instantiateFromResultSet(res: ResultSet): List<Obj> {
        val entities = res.asSequence().groupBy { it.getResource(anchor).uri }
        res.close()
        return entities.values.map { instantiateSingleResult(it)!! } // entity groups are never empty here, non-null assert is fine
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