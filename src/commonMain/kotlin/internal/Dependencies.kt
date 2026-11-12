package ru.ought.kotx.internal

/** Reconciles one consumer's subscriptions with the reads of its latest evaluation. */
internal class Dependencies(private val owner: Subscriber) {
    private var previous = linkedSetOf<Source>()
    private var current = linkedSetOf<Source>()

    fun record(source: Source) {
        // A set deduplicates repeated reads; subscribe before returning the read value.
        if (current.add(source)) source.subscribe(owner)
    }

    fun <T> collect(block: () -> T): T {
        current = linkedSetOf()
        val enclosing = Runtime.collector
        Runtime.collector = this
        try {
            return block()
        } finally {
            // Nested computed/autorun evaluations must restore their enclosing collector,
            // including when the evaluated block throws.
            Runtime.collector = enclosing
            // Retain dependencies still in use and unsubscribe only obsolete branches.
            // On failure, current contains the reads made before the exception.
            previous.filter { it !in current }.forEach { it.unsubscribe(owner) }
            previous = current
        }
    }

    fun clear() {
        // Closing during an evaluation must release both old and newly collected reads.
        (previous + current).forEach { it.unsubscribe(owner) }
        previous.clear()
        current.clear()
    }
}
