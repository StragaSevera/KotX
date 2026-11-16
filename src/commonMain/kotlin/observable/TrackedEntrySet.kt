package ru.ought.kotx.observable

/** Map entries intercept value replacement in addition to ordinary set mutations. */
internal class TrackedEntrySet<K, V>(
    data: MutableSet<MutableMap.MutableEntry<K, V>>,
    private val source: CollectionSource,
) : TrackedSet<MutableMap.MutableEntry<K, V>>(data, source) {
    override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
        val iterator = super.iterator()
        return object : MutableIterator<MutableMap.MutableEntry<K, V>> by iterator {
            override fun next(): MutableMap.MutableEntry<K, V> {
                val entry = iterator.next()
                // Keep iterator checks and removal in the tracked iterator; intercept setValue here.
                return wrapEntry(entry)
            }
        }
    }

    private fun wrapEntry(entry: MutableMap.MutableEntry<K, V>): MutableMap.MutableEntry<K, V> =
        object : MutableMap.MutableEntry<K, V> {
            override val key: K get() {
                source.recordRead()
                return entry.key
            }
            override val value: V get() {
                source.recordRead()
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
