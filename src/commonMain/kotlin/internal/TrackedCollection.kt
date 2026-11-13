package ru.ought.kotx.internal

/** Mutation bookkeeping shared by sets and map collection views. */
internal open class TrackedCollection<T>(
    private val data: MutableCollection<T>,
    private val source: CollectionSource,
    private val wrap: (T) -> T = { it },
) : AbstractMutableCollection<T>() {
    override val size: Int get() {
        source.read()
        return data.size
    }
    override fun contains(element: T): Boolean {
        source.read()
        return data.contains(element)
    }
    override fun add(element: T): Boolean = source.mutate { data.add(element).also { if (it) source.changed() } }
    override fun remove(element: T): Boolean = source.mutate { data.remove(element).also { if (it) source.changed() } }
    override fun clear() = source.mutate { if (data.isNotEmpty()) { data.clear(); source.changed() } }
    override fun addAll(elements: Collection<T>): Boolean = source.mutate {
        var changed = false
        for (element in elements.toList()) if (add(element)) changed = true
        changed
    }
    override fun removeAll(elements: Collection<T>): Boolean = filterMembership(elements, false)
    override fun retainAll(elements: Collection<T>): Boolean = filterMembership(elements, true)
    private fun filterMembership(elements: Collection<T>, retain: Boolean): Boolean = source.mutate {
        var changed = false
        val iterator = iterator()
        while (iterator.hasNext()) {
            if (elements.contains(iterator.next()) != retain) {
                iterator.remove()
                changed = true
            }
        }
        changed
    }
    override fun iterator(): MutableIterator<T> {
        source.read()
        val iterator = data.iterator()
        return object : MutableIterator<T> {
            private var expected = source.version
            private fun checkVersion() {
                source.read()
                if (expected != source.version) throw ConcurrentModificationException()
            }
            override fun hasNext(): Boolean {
                checkVersion()
                return iterator.hasNext()
            }
            override fun next(): T {
                checkVersion()
                return wrap(iterator.next())
            }
            override fun remove() = source.mutate {
                checkVersion()
                iterator.remove()
                source.changed()
                expected = source.version
            }
        }
    }
}

internal class TrackedSet<T>(data: MutableSet<T>, source: CollectionSource, wrap: (T) -> T = { it }) :
    AbstractMutableSet<T>() {
    private val contents = TrackedCollection(data, source, wrap)
    override val size: Int get() = contents.size
    override fun contains(element: T) = contents.contains(element)
    override fun iterator() = contents.iterator()
    override fun add(element: T) = contents.add(element)
    override fun addAll(elements: Collection<T>) = contents.addAll(elements)
    override fun remove(element: T) = contents.remove(element)
    override fun removeAll(elements: Collection<T>) = contents.removeAll(elements)
    override fun retainAll(elements: Collection<T>) = contents.retainAll(elements)
    override fun clear() = contents.clear()
}
