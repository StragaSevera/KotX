package ru.ought.kotx.tracking

import ru.ought.kotx.runtime.SourceReadTracker
import ru.ought.kotx.runtime.InvalidationDispatcher
import ru.ought.kotx.reaction.InvalidationListener

/**
 * Reusable single-threaded Source with identity-based subscriptions and coordinated
 * notifications. Subclass to report reads and publish changes after updating storage.
 */
open class SubscriptionSource : Source {
    private val subscribers = mutableListOf<InvalidationListener>()
    protected val observed: Boolean get() = subscribers.isNotEmpty()

    override fun addListener(listener: InvalidationListener) {
        if (subscribers.none { it === listener }) subscribers.add(listener)
    }

    override fun removeListener(listener: InvalidationListener) {
        // Computed sources use this transition to release their upstream dependencies.
        val index = subscribers.indexOfFirst { it === listener }
        if (index >= 0) {
            subscribers.removeAt(index)
            if (subscribers.isEmpty()) onUnobserved()
        }
    }

    protected open fun onUnobserved() = Unit

    protected open fun reportRead() {
        // Outside a tracked evaluation, reading state does not establish a subscription.
        SourceReadTracker.reportRead(this)
    }

    protected fun notifySubscribers() {
        InvalidationDispatcher.notifyInvalidationListeners(subscribers)
    }
}
