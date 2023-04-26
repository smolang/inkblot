package org.smolang.inkblot.runtime

import org.apache.jena.query.ParameterizedSparqlString
import org.smolang.inkblot.runtime.ComplexDelete
import org.smolang.inkblot.runtime.Inkblot

/*
Common functionality shared across all generated classes (all generated core classes inherit from SemanticObject).
Contains logic for runtime cache management and object deletion.
 */
abstract class SemanticObject(val uri: String) {
    protected var deleted = false
    private var dirty = false
    protected abstract val deleteUpdate: ParameterizedSparqlString
    protected abstract val deleteRedirectUpdate: ParameterizedSparqlString

    init {
        // the compiler does not like this because we're pushing a potentially unfinished object to cache
        // should work fine for our use-case though since the cache should not be referenced before initialization is completed
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