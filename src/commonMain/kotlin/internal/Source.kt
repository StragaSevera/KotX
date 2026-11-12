package ru.ought.kotx.internal

/** Shared subscription bookkeeping for observable properties and computed values. */
internal open class Source {
    private val subscribers = linkedSetOf<Reaction>()
    protected val observed: Boolean get() = subscribers.isNotEmpty()

    fun subscribe(subscriber: Reaction) {
        subscribers.add(subscriber)
    }

    fun unsubscribe(subscriber: Reaction) {
        // Computed sources use this transition to release their upstream dependencies.
        if (subscribers.remove(subscriber) && subscribers.isEmpty()) onUnobserved()
    }

    protected open fun onUnobserved() = Unit

    protected fun onRead() {
        // Outside a tracked evaluation, reading state does not establish a subscription.
        Runtime.activeReaction?.recordRead(this)
    }

    protected fun notifySubscribers() {
        // Take a snapshot so notifications traverse a stable set of subscribers.
        Runtime.notifyReactions(subscribers.toList())
    }
}
