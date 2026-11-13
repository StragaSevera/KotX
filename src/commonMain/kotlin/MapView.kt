package ru.ought.kotx

/** A live, shallow, read-only observable map, including protected entries. */
interface MapView<K, out V> : Map<K, V> {
    fun asView(): MapView<K, V> = this
}

internal class ReadOnlyMapView<K, V>(private val source: ObservableMap<K, V>) : AbstractMap<K, V>(), MapView<K, V> {
    override val size: Int get() = source.size
    override fun get(key: K): V? = source[key]
    override fun containsKey(key: K): Boolean = source.containsKey(key)
    override val keys: Set<K> = ReadOnlySetView(source.keys)
    override val values: Collection<V> = object : AbstractCollection<V>() {
        override val size: Int get() = source.size
        override fun iterator(): Iterator<V> {
            val iterator = source.values.iterator()
            return object : Iterator<V> {
                override fun hasNext() = iterator.hasNext()
                override fun next() = iterator.next()
            }
        }
    }
    override val entries: Set<Map.Entry<K, V>> = object : AbstractSet<Map.Entry<K, V>>() {
        override val size: Int get() = source.size
        override fun iterator(): Iterator<Map.Entry<K, V>> {
            val iterator = source.entries.iterator()
            return object : Iterator<Map.Entry<K, V>> {
                override fun hasNext() = iterator.hasNext()
                override fun next(): Map.Entry<K, V> {
                    val entry = iterator.next()
                    return object : Map.Entry<K, V> {
                        override val key: K get() = entry.key
                        override val value: V get() = entry.value
                        override fun equals(other: Any?) = other is Map.Entry<*, *> && key == other.key && value == other.value
                        override fun hashCode() = key.hashCode() xor value.hashCode()
                        override fun toString() = "$key=$value"
                    }
                }
            }
        }
    }
}
