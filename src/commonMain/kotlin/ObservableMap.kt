package ru.ought.kotx

import ru.ought.kotx.internal.CollectionSource
import ru.ought.kotx.internal.TrackedCollection
import ru.ought.kotx.internal.TrackedSet

/** A single-threaded, insertion-ordered mutable map with whole-collection tracking. */
interface ObservableMap<K, V> : MutableMap<K, V>, MapView<K, V> {
    override fun asView(): MapView<K, V>
}

fun <K, V> observableMapOf(vararg entries: Pair<K, V>): ObservableMap<K, V> = ObservableMapImpl(linkedMapOf(*entries))

private class ObservableMapImpl<K, V>(private val data: MutableMap<K, V>) : AbstractMutableMap<K, V>(), ObservableMap<K, V> {
    private val source = CollectionSource()
    private val view: MapView<K, V> by lazy { ReadOnlyMapView(this) }
    override val size: Int get() {
        source.read()
        return data.size
    }
    override fun get(key: K): V? {
        source.read()
        return data[key]
    }
    override fun containsKey(key: K): Boolean {
        source.read()
        return data.containsKey(key)
    }
    override val keys: MutableSet<K> = TrackedSet(data.keys, source)
    override val values: MutableCollection<V> = TrackedCollection(data.values, source)
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> = TrackedSet(data.entries, source) { entry ->
        object : MutableMap.MutableEntry<K, V> {
            override val key: K get() {
                source.read()
                return entry.key
            }
            override val value: V get() {
                source.read()
                return entry.value
            }
            override fun setValue(newValue: V): V = source.mutate {
                val old = entry.value
                val different = old != newValue
                entry.setValue(newValue)
                if (different) source.changed(false)
                old
            }
            override fun equals(other: Any?) = other is Map.Entry<*, *> && key == other.key && value == other.value
            override fun hashCode() = key.hashCode() xor value.hashCode()
            override fun toString() = "$key=$value"
        }
    }
    override fun put(key: K, value: V): V? = source.mutate {
        val existed = data.containsKey(key)
        val old = data[key]
        val different = !existed || old != value
        data[key] = value
        if (different) source.changed(!existed)
        old
    }
    override fun putAll(from: Map<out K, V>) = source.mutate {
        for ((key, value) in from.entries.toList()) put(key, value)
    }
    override fun remove(key: K): V? = source.mutate {
        val existed = data.containsKey(key)
        val old = data.remove(key)
        if (existed) source.changed()
        old
    }
    override fun clear() = source.mutate {
        if (data.isNotEmpty()) {
            data.clear()
            source.changed()
        }
    }
    override fun asView(): MapView<K, V> = view
}
