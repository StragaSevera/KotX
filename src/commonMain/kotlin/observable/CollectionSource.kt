package ru.ought.kotx.observable

import ru.ought.kotx.runtime.Runtime
import ru.ought.kotx.tracking.SubscriptionSource

/** Shared by a collection and all its mutable ranges and views. */
internal class CollectionSource : SubscriptionSource() {
    var version = 0
        private set
    private var depth = 0
    private var dirty = false

    fun read() = onRead()

    fun changed(structural: Boolean = true) {
        if (structural) version++
        dirty = true
    }

    fun <T> mutate(block: () -> T): T {
        check(!Runtime.isTracking) { "Observable writes during tracked evaluation are unsupported" }
        depth++
        try {
            return block()
        } finally {
            depth--
            if (depth == 0 && dirty) {
                dirty = false
                notifySubscribers()
            }
        }
    }
}
