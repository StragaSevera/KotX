package ru.ought.kotx

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/** Live read-only access to a value. Reads participate in dependency tracking. */
interface PropertyView<out T> : ReadOnlyProperty<Any?, T> {
    val value: T
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
    fun asView(): PropertyView<T> = this
}
