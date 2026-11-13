package ru.ought.kotx

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** A writable reactive handle, usable directly or as a property delegate. */
interface ObservableProperty<T> : PropertyView<T>, ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
    override var value: T
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
    /** Returns a cached live wrapper without writable access. */
    override fun asView(): PropertyView<T>
}
