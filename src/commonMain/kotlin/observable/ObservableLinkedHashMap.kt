package ru.ought.kotx.observable

import ru.ought.kotx.view.MapView
import ru.ought.kotx.view.ReadOnlyMapView

/** An insertion-ordered hash-backed observable map. Constructors copy their input. */
class ObservableLinkedHashMap<K, V>() : AbstractMutableMap<K, V>(), ObservableMap<K, V> {
    private val data = linkedMapOf<K, V>()

    constructor(entries: Map<out K, V>) : this() {
        data.putAll(entries)
    }

    private val source = CollectionSource()
    private val view: MapView<K, V> by lazy { ReadOnlyMapView(this) }
    override fun asView(): MapView<K, V> = view

    override val size: Int get() {
        source.reportRead()
        return data.size
    }
    override fun get(key: K): V? {
        source.reportRead()
        return data[key]
    }
    override fun containsKey(key: K): Boolean {
        source.reportRead()
        return data.containsKey(key)
    }
    // All views share the map source, so changes through any view notify map readers.
    override val keys: MutableSet<K> = TrackedSet(data.keys, source)
    override val values: MutableCollection<V> = TrackedCollection(data.values, source)
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> = TrackedEntrySet(data.entries, source)
    override fun put(key: K, value: V): V? = source.mutate {
        // A null old value cannot distinguish an absent key from a present null value.
        val existed = data.containsKey(key)
        val old = data[key]
        val different = !existed || old != value
        data[key] = value
        if (different) source.changed(!existed)
        old
    }
    override fun putAll(from: Map<out K, V>) = source.mutate {
        // Snapshot aliases before writes can change the source map or invalidate its iterator.
        for ((key, value) in from.entries.toList()) put(key, value)
    }
    override fun remove(key: K): V? = source.mutate {
        // A null old value cannot distinguish an absent key from a present null value.
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
}
