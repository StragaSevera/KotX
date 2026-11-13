package ru.ought.kotx

/** A live, shallow, read-only observable list. */
interface ListView<out T> : List<T> {
    fun asView(): ListView<T> = this
    override fun subList(fromIndex: Int, toIndex: Int): ListView<T>
}

internal class ReadOnlyListView<T>(private val source: ObservableList<T>) : AbstractList<T>(), ListView<T> {
    override val size: Int get() = source.size
    override fun get(index: Int): T = source[index]
    override fun iterator(): Iterator<T> = listIterator()
    override fun listIterator(): ListIterator<T> = listIterator(0)
    override fun listIterator(index: Int): ListIterator<T> {
        val iterator = source.listIterator(index)
        return object : ListIterator<T> {
            override fun hasNext() = iterator.hasNext()
            override fun next() = iterator.next()
            override fun hasPrevious() = iterator.hasPrevious()
            override fun previous() = iterator.previous()
            override fun nextIndex() = iterator.nextIndex()
            override fun previousIndex() = iterator.previousIndex()
        }
    }
    override fun subList(fromIndex: Int, toIndex: Int): ListView<T> = source.subList(fromIndex, toIndex).asView()
}
