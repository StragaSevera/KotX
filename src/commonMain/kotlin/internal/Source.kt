package ru.ought.kotx.internal

/** A graph consumer notified when a source may have changed. */
internal interface Subscriber {
    fun invalidate()
}

/** Shared subscription bookkeeping for observable properties and computed values. */
internal open class Source {
    private val subscribers = linkedSetOf<Subscriber>()
    protected val observed: Boolean get() = subscribers.isNotEmpty()

    fun subscribe(subscriber: Subscriber) {
        subscribers.add(subscriber)
    }

    fun unsubscribe(subscriber: Subscriber) {
        // Computed sources use this transition to release their upstream dependencies.
        if (subscribers.remove(subscriber) && subscribers.isEmpty()) onUnobserved()
    }

    protected open fun onUnobserved() = Unit

    protected fun read() {
        // Outside a tracked evaluation, reading state does not establish a subscription.
        Runtime.collector?.record(this)
    }

    protected fun changed() {
        // Propagate all invalidations before executing any effects.
        Runtime.propagate {
            // Effects can replace subscriptions later; traverse a stable subscriber set.
            subscribers.toList().forEach { it.invalidate() }
        }
    }
}
