package inkblot.runtime

import inkblot.runtime.DeleteObject
import inkblot.runtime.Inkblot
import inkblot.runtime.RedirectDelete

abstract class SemanticObject(val uri: String) {
    protected var deleted = false
    private var dirty = false

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
        Inkblot.changelog.add(DeleteObject(uri))
    }

    fun delete(redirectReferencesToURI: String) {
        markDirty()
        deleted = true
        Inkblot.changelog.add(RedirectDelete(uri, redirectReferencesToURI))
    }
}