package ru.ought.kotx.tracking

import ru.ought.kotx.runtime.RuntimeEngine
import ru.ought.kotx.reaction.Reaction

/**
 * Optional single-threaded subscription bookkeeping for a [Reaction].
 * Each evaluation replaces previous dependencies; reads before a failure remain subscribed.
 * Use [clearSources] when the owner is disposed. The tracker can be reused after clearing.
 * Reentrant evaluation of the same tracker is unsupported.
 */
class DependencyTracker(private val owner: Reaction) {
    private var previousSubscriptions = mutableListOf<Source>()
    private var currentSubscriptions = mutableListOf<Source>()

    /** Call from the owner's [Reaction.recordRead] implementation. */
    fun recordRead(source: Source) {
        // Deduplicate by identity; value equality must not merge graph nodes.
        if (currentSubscriptions.none { it === source }) {
            currentSubscriptions.add(source)
            source.subscribe(owner)
        }
    }

    /**
     * Runs [block] with the owner as the active reaction. On exit, restores the
     * previous reaction and removes subscriptions that this run no longer needs.
     */
    fun <T> runAsActive(block: () -> T): T {
        currentSubscriptions = mutableListOf()
        try {
            return RuntimeEngine.withReaction(owner, block)
        } finally {
            // Unsubscribe from obsolete branches.
            // On failure, current contains the reads made before the exception.
            previousSubscriptions.filter { old -> currentSubscriptions.none { it === old } }.forEach { it.unsubscribe(owner) }
            previousSubscriptions = currentSubscriptions
        }
    }

    /** Releases all recorded subscriptions. Repeated clearing is harmless. */
    fun clearSources() {
        // Closing during an evaluation must release both old and newly collected reads.
        (previousSubscriptions + currentSubscriptions).forEach { it.unsubscribe(owner) }
        previousSubscriptions.clear()
        currentSubscriptions.clear()
    }
}
