package ru.ought.kotx.observable

import ru.ought.kotx.view.ListView
import ru.ought.kotx.view.ReadOnlyListView

/**
 * An array-backed observable list. Constructors copy their input.
 * Sublists share storage and tracking with their parent.
 */
class ObservableArrayList<T> private constructor(
    private val data: MutableList<T>,
    private val source: CollectionSource = CollectionSource(),
    private val parent: ObservableArrayList<T>? = null,
) : AbstractMutableList<T>(), ObservableList<T> {
    constructor() : this(mutableListOf<T>())
    constructor(elements: Collection<T>) : this(elements.toMutableList(), CollectionSource(), null)

    private var expectedVersion = source.version
    private val view: ListView<T> by lazy { ReadOnlyListView(this) }

    override fun asView(): ListView<T> = view

    private fun recordRead() {
        source.recordRead()
        if (parent != null && expectedVersion != source.version) throw ConcurrentModificationException()
    }

    private fun changed(structural: Boolean = true) {
        source.changed(structural)
        // A change through a sublist keeps that range and its ancestors valid.
        // Sibling and descendant ranges retain their old version and become invalid.
        var range: ObservableArrayList<T>? = this
        while (range != null) {
            range.expectedVersion = source.version
            range = range.parent
        }
    }

    override val size: Int get() {
        recordRead()
        return data.size
    }
    override fun get(index: Int): T {
        recordRead()
        return data[index]
    }
    override fun set(index: Int, element: T): T = source.mutate {
        recordRead()
        val old = data[index]
        val different = old != element
        data[index] = element
        if (different) changed(false)
        old
    }
    override fun add(index: Int, element: T) = source.mutate {
        recordRead()
        data.add(index, element)
        changed()
    }
    override fun add(element: T): Boolean = source.mutate {
        add(size, element)
        true
    }
    override fun removeAt(index: Int): T = source.mutate {
        recordRead()
        val old = data.removeAt(index)
        changed()
        old
    }
    override fun remove(element: T): Boolean = source.mutate {
        val index = indexOf(element)
        if (index < 0) false else {
            removeAt(index)
            true
        }
    }
    override fun addAll(elements: Collection<T>): Boolean = source.mutate { addAll(size, elements) }
    override fun addAll(index: Int, elements: Collection<T>): Boolean = source.mutate {
        recordRead()
        if (index !in 0..data.size) throw IndexOutOfBoundsException("Index: $index")
        // Snapshot aliases before inserting so adding this list or one of its views works.
        val incoming = elements.toList()
        var position = index
        for (element in incoming) add(position++, element)
        incoming.isNotEmpty()
    }
    override fun clear() = source.mutate {
        recordRead()
        if (data.isNotEmpty()) {
            data.clear()
            changed()
        }
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
    override fun iterator(): MutableIterator<T> = listIterator()
    override fun listIterator(): MutableListIterator<T> = listIterator(0)
    override fun listIterator(index: Int): MutableListIterator<T> {
        recordRead()
        if (index !in 0..size) throw IndexOutOfBoundsException()
        return object : MutableListIterator<T> {
            private var expected = source.version
            private var cursor = index
            // Index returned by next/previous; reset after add/remove to forbid reuse.
            private var last = -1
            private fun checkVersion() {
                recordRead()
                if (expected != source.version) throw ConcurrentModificationException()
            }
            override fun hasNext(): Boolean {
                checkVersion()
                return cursor < size
            }
            override fun hasPrevious(): Boolean {
                checkVersion()
                return cursor > 0
            }
            override fun nextIndex(): Int {
                checkVersion()
                return cursor
            }
            override fun previousIndex(): Int {
                checkVersion()
                return cursor - 1
            }
            override fun next(): T {
                if (!hasNext()) throw NoSuchElementException()
                last = cursor
                return get(cursor++)
            }
            override fun previous(): T {
                if (!hasPrevious()) throw NoSuchElementException()
                last = --cursor
                return get(cursor)
            }
            override fun remove() = source.mutate {
                checkVersion()
                check(last >= 0)
                removeAt(last)
                // Removing after next shifts the cursor back; after previous it stays put.
                if (last < cursor) cursor--
                last = -1
                expected = source.version
            }
            override fun add(element: T) = source.mutate {
                checkVersion()
                this@ObservableArrayList.add(cursor++, element)
                last = -1
                expected = source.version
            }
            override fun set(element: T) = source.mutate {
                checkVersion()
                check(last >= 0)
                this@ObservableArrayList[last] = element
            }
        }
    }
    override fun subList(fromIndex: Int, toIndex: Int): ObservableList<T> {
        recordRead()
        return ObservableArrayList(data.subList(fromIndex, toIndex), source, this)
    }
}
