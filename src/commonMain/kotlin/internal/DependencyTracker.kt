package ru.ought.kotx.internal

/** Reconciles one consumer's subscriptions with the reads of its latest evaluation. */
internal class DependencyTracker(private val owner: Reaction) {
    private var previousSubscriptions = linkedSetOf<Source>()
    private var currentSubscriptions = linkedSetOf<Source>()

    fun recordRead(source: Source) {
        // A set deduplicates repeated reads; subscribe before returning the read value.
        if (currentSubscriptions.add(source)) source.subscribe(owner)
    }

    /**
     * Runs [block] with the owner as the active reaction. On exit, restores the
     * previous reaction and removes subscriptions that this run no longer needs.
     */
    fun <T> runAsActive(block: () -> T): T {
        currentSubscriptions = linkedSetOf()
        val enclosing = Runtime.activeReaction
        Runtime.activeReaction = owner
        try {
            return block()
        } finally {
            // Nested evaluations must restore their enclosing reaction,
            // including when the evaluated block throws.
            Runtime.activeReaction = enclosing
            // Unsubscribe from obsolete branches.
            // On failure, current contains the reads made before the exception.
            previousSubscriptions.filter { it !in currentSubscriptions }.forEach { it.unsubscribe(owner) }
            previousSubscriptions = currentSubscriptions
        }
    }

    fun clearSources() {
        // Closing during an evaluation must release both old and newly collected reads.
        (previousSubscriptions + currentSubscriptions).forEach { it.unsubscribe(owner) }
        previousSubscriptions.clear()
        currentSubscriptions.clear()
    }
}
