package ru.ought.kotx.observable

import ru.ought.kotx.runtime.SourceReadTracker
import ru.ought.kotx.tracking.SubscriptionSource

/** Shared by a collection and all its mutable ranges and views. */
internal class CollectionSource : SubscriptionSource() {
    var version = 0
        private set
    private var depth = 0
    private var dirty = false

    public override fun reportRead() = super.reportRead()

    // Value replacements notify readers without invalidating iterator positions.
    fun changed(structural: Boolean = true) {
        if (structural) version++
        dirty = true
    }

    /**
     * Batches nested mutations of this source, including changes through its views.
     * Changes are not rolled back: partial changes must notify even if the block throws.
     */
    fun <T> mutate(block: () -> T): T {
        check(!SourceReadTracker.isTracking) { "Observable writes during tracked evaluation are unsupported" }
        depth++
        try {
            return block()
        } finally {
            depth--
            if (depth == 0 && dirty) {
                // Clear before callbacks so reentrant mutations can record a new change.
                dirty = false
                notifySubscribers()
            }
        }
    }
}
