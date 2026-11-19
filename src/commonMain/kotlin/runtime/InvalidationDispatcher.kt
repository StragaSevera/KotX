package ru.ought.kotx.runtime

import ru.ought.kotx.reaction.InvalidationListener

/** Delivers nested invalidations before signaling completion of the outermost notification. */
internal object InvalidationDispatcher {
    private var notificationDepth = 0
    fun isInvalidating(): Boolean = notificationDepth != 0

    fun notifyInvalidationListeners(listeners: List<InvalidationListener>) {
        val snapshot = listeners.toList()
        // A ComputedProperty can notify its own subscribers. Wait for the outermost
        // notification to finish so all affected caches are stale before reactions run.
        notificationDepth++
        try {
            var failure: Throwable? = null
            for (listener in snapshot) {
                try {
                    listener.onInvalidated()
                } catch (error: Throwable) {
                    if (failure == null) failure = error
                }
            }
            failure?.let { throw it }
        } finally {
            notificationDepth--
            // When invalidation is complete, start the reaction execution step.
            if (!isInvalidating()) ReactionScheduler.executePending()
        }
    }
}
