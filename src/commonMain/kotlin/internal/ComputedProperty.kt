package ru.ought.kotx.internal

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/** Both a source for downstream readers and a subscriber to its expression's reads. */
internal class ComputedProperty<T>(private val expression: () -> T) :
    Source(), Subscriber, ReadOnlyProperty<Any?, T> {
    private val dependencies = Dependencies(this)
    private var dirty = true
    private var evaluating = false
    private var cached: Any? = null
    private var failure: Throwable? = null

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        // Reject cycles before registering the recursive read as another dependency.
        check(!evaluating) { "Computed dependency cycle at ${property.name}" }
        // Subscribe the enclosing consumer to this computed, not to its internals.
        read()
        if (dirty) {
            evaluating = true
            try {
                try {
                    cached = dependencies.collect(expression)
                    failure = null
                } catch (error: Throwable) {
                    // Cache failures too. A later upstream change invalidates the failure
                    // and gives the expression another chance to evaluate.
                    failure = error
                    dirty = false
                    throw error
                }
                dirty = false
                @Suppress("UNCHECKED_CAST")
                // Capture the result before finally can discard an unobserved cache.
                return cached as T
            } finally {
                evaluating = false
                // Unobserved reads must not leave the computed attached to its sources.
                if (!observed) onUnobserved()
            }
        }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return cached as T
    }

    override fun invalidate() {
        // A dirty computed has already notified downstream consumers for this change.
        if (dirty) return
        dirty = true
        changed()
    }

    override fun onUnobserved() {
        // Detaching recursively releases computed chains with no remaining consumers.
        // A future read starts fresh, so no stale cached value survives detachment.
        dependencies.clear()
        dirty = true
        cached = null
        failure = null
    }
}
