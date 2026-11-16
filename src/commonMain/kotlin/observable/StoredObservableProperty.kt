package ru.ought.kotx.observable

import ru.ought.kotx.runtime.RuntimeEngine
import ru.ought.kotx.tracking.SubscriptionSource
import ru.ought.kotx.view.PropertyView

/** Mutable reactive storage with structural-equality notification suppression. */
class StoredObservableProperty<T>(initial: T) : SubscriptionSource(), ObservableProperty<T> {
    private var storedValue = initial
    private val view = object : PropertyView<T> {
        override val value: T get() = this@StoredObservableProperty.value
    }

    override var value: T
        get() {
            onRead()
            return storedValue
        }
        set(value) {
            check(!RuntimeEngine.isTracking) { "Observable writes during tracked evaluation are unsupported" }
            val changed = storedValue != value
            storedValue = value
            if (changed) notifySubscribers()
        }

    override fun asView(): PropertyView<T> = view
}
