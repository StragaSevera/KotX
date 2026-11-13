package ru.ought.kotx

import ru.ought.kotx.internal.CollectionSource
import ru.ought.kotx.internal.TrackedSet

/** A single-threaded, insertion-ordered mutable set with whole-collection tracking. */
interface ObservableSet<T> : MutableSet<T>, SetView<T> {
    override fun asView(): SetView<T>
}

fun <T> observableSetOf(vararg elements: T): ObservableSet<T> = ObservableSetImpl(elements.toMutableSet())

private class ObservableSetImpl<T>(data: MutableSet<T>) : AbstractMutableSet<T>(), ObservableSet<T> {
    private val contents = TrackedSet(data, CollectionSource())
    private val view: SetView<T> by lazy { ReadOnlySetView(this) }
    override val size: Int get() = contents.size
    override fun contains(element: T) = contents.contains(element)
    override fun iterator() = contents.iterator()
    override fun add(element: T) = contents.add(element)
    override fun addAll(elements: Collection<T>) = contents.addAll(elements)
    override fun remove(element: T) = contents.remove(element)
    override fun removeAll(elements: Collection<T>) = contents.removeAll(elements)
    override fun retainAll(elements: Collection<T>) = contents.retainAll(elements)
    override fun clear() = contents.clear()
    override fun asView(): SetView<T> = view
}
