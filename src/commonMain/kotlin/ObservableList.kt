package ru.ought.kotx

import ru.ought.kotx.internal.CollectionSource

/**
 * A mutable observable list with whole-collection tracking and shallow elements.
 * Bulk member operations notify once, including partial changes before failure.
 * Access is single-threaded; outside structural changes invalidate iterators and ranges.
 */
interface ObservableList<T> : MutableList<T>, ListView<T> {
    override fun asView(): ListView<T>
    override fun subList(fromIndex: Int, toIndex: Int): ObservableList<T>
}

fun <T> observableListOf(vararg elements: T): ObservableList<T> = ObservableListImpl(elements.toMutableList())

internal class ObservableListImpl<T>(
    private val data: MutableList<T>,
    private val source: CollectionSource = CollectionSource(),
    private val parent: ObservableListImpl<T>? = null,
) : AbstractMutableList<T>(), ObservableList<T> {
    private var expectedVersion = source.version
    private val view: ListView<T> by lazy { ReadOnlyListView(this) }

    private fun read() {
        source.read()
        if (parent != null && expectedVersion != source.version) throw ConcurrentModificationException()
    }

    private fun changed(structural: Boolean = true) {
        source.changed(structural)
        var range: ObservableListImpl<T>? = this
        while (range != null) {
            range.expectedVersion = source.version
            range = range.parent
        }
    }

    override val size: Int get() {
        read()
        return data.size
    }
    override fun get(index: Int): T {
        read()
        return data[index]
    }
    override fun set(index: Int, element: T): T = source.mutate {
        read()
        val old = data[index]
        val different = old != element
        data[index] = element
        if (different) changed(false)
        old
    }
    override fun add(index: Int, element: T) = source.mutate {
        read()
        data.add(index, element)
        changed()
    }
    override fun add(element: T): Boolean = source.mutate {
        add(size, element)
        true
    }
    override fun removeAt(index: Int): T = source.mutate {
        read()
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
        read()
        if (index !in 0..data.size) throw IndexOutOfBoundsException("Index: $index")
        // Snapshot aliases before inserting so adding this list or one of its views works.
        val incoming = elements.toList()
        var position = index
        for (element in incoming) add(position++, element)
        incoming.isNotEmpty()
    }
    override fun clear() = source.mutate {
        read()
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
        read()
        if (index !in 0..size) throw IndexOutOfBoundsException()
        return object : MutableListIterator<T> {
            private var expected = source.version
            private var cursor = index
            private var last = -1
            private fun checkVersion() {
                read()
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
                if (last < cursor) cursor--
                last = -1
                expected = source.version
            }
            override fun add(element: T) = source.mutate {
                checkVersion()
                this@ObservableListImpl.add(cursor++, element)
                last = -1
                expected = source.version
            }
            override fun set(element: T) = source.mutate {
                checkVersion()
                check(last >= 0)
                this@ObservableListImpl[last] = element
            }
        }
    }
    override fun subList(fromIndex: Int, toIndex: Int): ObservableList<T> {
        read()
        return ObservableListImpl(data.subList(fromIndex, toIndex), source, this)
    }
    override fun asView(): ListView<T> = view
}
