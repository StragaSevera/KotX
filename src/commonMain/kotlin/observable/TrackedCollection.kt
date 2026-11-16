package ru.ought.kotx.observable

/** Mutation bookkeeping shared by sets and map collection views. */
internal open class TrackedCollection<T>(
    private val data: MutableCollection<T>,
    private val source: CollectionSource,
) : AbstractMutableCollection<T>() {
    override val size: Int get() {
        source.reportRead()
        return data.size
    }
    override fun contains(element: T): Boolean {
        source.reportRead()
        return data.contains(element)
    }
    override fun add(element: T): Boolean = source.mutate { data.add(element).also { if (it) source.changed() } }
    override fun remove(element: T): Boolean = source.mutate { data.remove(element).also { if (it) source.changed() } }
    override fun clear() = source.mutate { if (data.isNotEmpty()) { data.clear(); source.changed() } }
    override fun addAll(elements: Collection<T>): Boolean = source.mutate {
        var changed = false
        // Snapshot first: the input may be this collection or another view of its storage.
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
        source.reportRead()
        val iterator = data.iterator()
        return object : MutableIterator<T> {
            private var expected = source.version
            private fun checkVersion() {
                source.reportRead()
                if (expected != source.version) throw ConcurrentModificationException()
            }
            override fun hasNext(): Boolean {
                checkVersion()
                return iterator.hasNext()
            }
            override fun next(): T {
                checkVersion()
                return iterator.next()
            }
            override fun remove() = source.mutate {
                checkVersion()
                iterator.remove()
                source.changed()
                // This iterator remains valid after its own removal; other iterators do not.
                expected = source.version
            }
        }
    }
}
