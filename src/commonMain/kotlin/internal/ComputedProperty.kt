package ru.ought.kotx.internal

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * A **lazy** derived property that tracks the sources read by its expression.
 *
 * When a dependency may have changed, [react] marks the cached result stale and
 * notifies subscribers. Reevaluation happens only on the next read. A synchronous
 * autorun can make that read before the original write returns.
 */
internal class ComputedProperty<T>(private val computation: () -> T) :
    Source(), Reaction, ReadOnlyProperty<Any?, T> {
    private val tracker = DependencyTracker(this)
    private var evaluating = false
    // An absent result is stale; a present result can hold a null value or a failure.
    private var cachedResult: Result<T>? = null

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        // Reject cycles before registering the recursive read as another dependency.
        check(!evaluating) { "Computed dependency cycle at ${property.name}" }
        // Subscribe the enclosing consumer to this computed, not to its internals.
        onRead()
        return (cachedResult ?: evaluate()).getOrThrow()
    }

    private fun evaluate(): Result<T> {
        evaluating = true
        try {
            // Cache failures until a dependency changes, just like successful results.
            val result = runCatching { tracker.runAsActive(computation) }
            cachedResult = result
            return result
        } finally {
            evaluating = false
            // Return the result to this reader even if detaching discards the cache.
            if (!observed) onUnobserved()
        }
    }

    override fun recordRead(source: Source) = tracker.recordRead(source)

    override fun react() {
        // Once stale, further notifications needn't reach subscribers until a fresh read.
        if (cachedResult == null) return
        cachedResult = null
        notifySubscribers()
    }

    override fun onUnobserved() {
        // Detaching recursively releases computed chains with no remaining consumers.
        // A future read starts fresh, so no stale cached value survives detachment.
        tracker.clearSources()
        cachedResult = null
    }
}
