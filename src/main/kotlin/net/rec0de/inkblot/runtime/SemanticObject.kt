package net.rec0de.inkblot.runtime

import org.apache.jena.query.ParameterizedSparqlString

abstract class SemanticObject(val uri: String) {
    protected var deleted = false
    private var dirty = false
    protected abstract val deleteUpdate: ParameterizedSparqlString
    protected abstract val deleteRedirectUpdate: ParameterizedSparqlString

    init {
        // compiler tells me this is bad because we're pushing a potentially unfinished object to cache
        // we'll see if this will come back to bite us at some point
        Inkblot.loadedObjects[uri] = this // update object cache
    }

    fun markDirty(){
        if(dirty)
            return

        dirty = true
        Inkblot.dirtySet.add(this)
    }

    fun markCommitted() {
        dirty = false
    }

    // Delete functionality
    open fun delete() {
        markDirty()
        deleted = true
        val update = deleteUpdate.copy()
        update.setIri("anchor", uri)
        Inkblot.changelog.add(ComplexDelete(update.asUpdate()))
    }

    fun delete(redirectReferencesToURI: String) {
        markDirty()
        deleted = true
        val update = deleteRedirectUpdate.copy()
        update.setIri("anchor", uri)
        update.setIri("target", redirectReferencesToURI)
        Inkblot.changelog.add(ComplexDelete(update.asUpdate()))
    }
}