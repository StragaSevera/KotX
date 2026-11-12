package ru.ought.kotx.internal

/** Reconciles one consumer's subscriptions with the reads of its latest evaluation. */
internal class DependencyTracker(private val owner: Reaction) {
    private var previous = linkedSetOf<Source>()
    private var current = linkedSetOf<Source>()

    fun recordRead(source: Source) {
        // A set deduplicates repeated reads; subscribe before returning the read value.
        if (current.add(source)) source.subscribe(owner)
    }

    /**
     * Runs [block] with the owner as the active reaction. On exit, restores the
     * previous reaction and removes subscriptions that this run no longer needs.
     */
    fun <T> runAsActive(block: () -> T): T {
        current = linkedSetOf()
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
            previous.filter { it !in current }.forEach { it.unsubscribe(owner) }
            previous = current
        }
    }

    fun clearSources() {
        // Closing during an evaluation must release both old and newly collected reads.
        (previous + current).forEach { it.unsubscribe(owner) }
        previous.clear()
        current.clear()
    }
}
