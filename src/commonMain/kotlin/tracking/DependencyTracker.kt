package ru.ought.kotx.tracking

import ru.ought.kotx.runtime.SourceReadTracker
import ru.ought.kotx.reaction.Reaction
import ru.ought.kotx.reaction.SourceReadListener

/**
 * Optional single-threaded subscription bookkeeping for a [Reaction].
 * Each evaluation replaces previous dependencies. The owner stays subscribed to sources read before a failure.
 * Use [clearSources] when the owner is disposed. The tracker can be reused after clearing.
 * Reentrant evaluation of the same tracker is unsupported.
 */
class DependencyTracker(private val owner: Reaction) {
    private var previousSubscriptions = mutableListOf<Source>()
    private var currentSubscriptions = mutableListOf<Source>()

    /** Call this from the owner's [SourceReadListener.onSourceRead] implementation. */
    fun recordDependency(source: Source) {
        // Deduplicate by identity; value equality must not merge graph nodes.
        if (currentSubscriptions.none { it === source }) {
            currentSubscriptions.add(source)
            owner.subscribe(source)
        }
    }

    /**
     * Runs [block] with the owner receiving source reads. On exit, restores the
     * previous listener and removes subscriptions that this run no longer needs.
     */
    fun <T> runAsActive(block: () -> T): T {
        currentSubscriptions = mutableListOf()
        try {
            return SourceReadTracker.withReadListener(owner, block)
        } finally {
            // Unsubscribe from obsolete branches.
            // On failure, current contains the reads made before the exception.
            previousSubscriptions.filter { old -> currentSubscriptions.none { it === old } }.forEach(owner::unsubscribe)
            previousSubscriptions = currentSubscriptions
        }
    }

    /** Releases all recorded subscriptions. Repeated clearing is harmless. */
    fun clearSources() {
        // Closing during an evaluation must release both old and newly collected reads.
        (previousSubscriptions + currentSubscriptions).forEach(owner::unsubscribe)
        previousSubscriptions.clear()
        currentSubscriptions.clear()
    }
}
