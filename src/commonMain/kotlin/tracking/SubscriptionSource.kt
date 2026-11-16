package ru.ought.kotx.tracking

import ru.ought.kotx.runtime.RuntimeEngine
import ru.ought.kotx.reaction.Reaction

/**
 * Reusable single-threaded Source with identity-based subscriptions and coordinated
 * notifications. Subclass to report reads and publish changes after updating storage.
 */
open class SubscriptionSource : Source {
    private val subscribers = mutableListOf<Reaction>()
    protected val observed: Boolean get() = subscribers.isNotEmpty()

    override fun subscribe(reaction: Reaction) {
        if (subscribers.none { it === reaction }) subscribers.add(reaction)
    }

    override fun unsubscribe(reaction: Reaction) {
        // Computed sources use this transition to release their upstream dependencies.
        val index = subscribers.indexOfFirst { it === reaction }
        if (index >= 0) {
            subscribers.removeAt(index)
            if (subscribers.isEmpty()) onUnobserved()
        }
    }

    protected open fun onUnobserved() = Unit

    protected open fun recordRead() {
        // Outside a tracked evaluation, reading state does not establish a subscription.
        RuntimeEngine.recordRead(this)
    }

    protected fun notifySubscribers() {
        RuntimeEngine.notifyReactions(subscribers)
    }
}
