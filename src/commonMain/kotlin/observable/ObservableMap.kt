package ru.ought.kotx.observable

import ru.ought.kotx.view.MapView

/** A single-threaded, insertion-ordered mutable map with whole-collection tracking. */
interface ObservableMap<K, V> : MutableMap<K, V>, MapView<K, V> {
    override fun asView(): MapView<K, V>
}

fun <K, V> observableMapOf(vararg entries: Pair<K, V>): ObservableMap<K, V> = ObservableLinkedHashMap(linkedMapOf(*entries))
