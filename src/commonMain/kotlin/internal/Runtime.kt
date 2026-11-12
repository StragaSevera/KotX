package ru.ought.kotx.internal

/** Tracks the active consumer and coordinates invalidation before executing effects. */
internal object Runtime {
    // Intentionally execution-global for this single-threaded JVM/JS prototype.
    var collector: Dependencies? = null
    private var propagationDepth = 0
    private var draining = false
    // One reaction may be reached through several computed paths from a single write.
    private val pending = linkedSetOf<Autorun>()

    fun schedule(reaction: Autorun) {
        pending.add(reaction)
    }

    fun cancel(reaction: Autorun) {
        pending.remove(reaction)
    }

    fun propagate(block: () -> Unit) {
        // A computed can invalidate further computeds. Only the outermost propagation
        // drains effects, after every affected cache has been marked dirty.
        propagationDepth++
        try {
            block()
        } finally {
            propagationDepth--
            if (propagationDepth == 0) drain()
        }
    }

    private fun drain() {
        // Keep one drain loop in control if invalidation is nested during cleanup.
        if (draining) return
        draining = true
        var failure: Throwable? = null
        try {
            while (pending.isNotEmpty()) {
                val reaction = pending.first()
                pending.remove(reaction)
                try {
                    reaction.run()
                } catch (error: Throwable) {
                    // One broken effect must not prevent other queued consumers updating.
                    // Report the first error only after all pending effects were attempted.
                    if (failure == null) failure = error
                }
            }
        } finally {
            draining = false
        }
        failure?.let { throw it }
    }
}
