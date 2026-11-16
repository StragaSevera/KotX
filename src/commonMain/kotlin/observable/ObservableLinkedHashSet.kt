package ru.ought.kotx.observable

import ru.ought.kotx.view.ReadOnlySetView
import ru.ought.kotx.view.SetView

/** An insertion-ordered hash-backed observable set. Constructors copy their input. */
class ObservableLinkedHashSet<T>() : AbstractMutableSet<T>(), ObservableSet<T> {
    private val contents = TrackedSet(linkedSetOf<T>(), CollectionSource())

    constructor(elements: Collection<T>) : this() {
        contents.addAll(elements)
    }
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
