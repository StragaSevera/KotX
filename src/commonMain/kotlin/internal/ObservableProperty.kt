package ru.ought.kotx.internal

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Stores the value and participates in the graph through ordinary delegated access. */
internal class ObservableProperty<T>(private var value: T) : Source(), ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        onRead()
        return value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        // This prototype rejects feedback writes rather than supporting reaction loops
        // or mutation during computed evaluation. Ordinary application writes are allowed.
        check(Runtime.activeReaction == null) { "Observable writes during tracked evaluation are unsupported" }
        if (this.value == value) return
        this.value = value
        // Publish the value before notifying consumers so synchronous reruns see it.
        notifySubscribers()
    }
}
