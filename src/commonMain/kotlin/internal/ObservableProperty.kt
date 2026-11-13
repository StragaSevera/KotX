package ru.ought.kotx.internal

import ru.ought.kotx.PropertyView

internal class ObservableProperty<T>(initial: T) : Source(), ru.ought.kotx.ObservableProperty<T> {
    private var storedValue = initial
    private val view = object : PropertyView<T> {
        override val value: T get() = this@ObservableProperty.value
    }

    override var value: T
        get() {
            onRead()
            return storedValue
        }
        set(value) {
            check(Runtime.activeReaction == null) { "Observable writes during tracked evaluation are unsupported" }
            val changed = storedValue != value
            storedValue = value
            if (changed) notifySubscribers()
        }

    override fun asView(): PropertyView<T> = view
}
