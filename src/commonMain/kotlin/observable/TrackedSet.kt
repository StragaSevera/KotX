package ru.ought.kotx.observable

internal open class TrackedSet<T>(data: MutableSet<T>, source: CollectionSource) :
    AbstractMutableSet<T>() {
    private val contents = TrackedCollection(data, source)
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
