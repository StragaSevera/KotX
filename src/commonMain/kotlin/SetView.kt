package ru.ought.kotx

/** A live, shallow, read-only observable set. */
interface SetView<out T> : Set<T> {
    fun asView(): SetView<T> = this
}

internal class ReadOnlySetView<T>(private val source: Set<T>) : AbstractSet<T>(), SetView<T> {
    override val size: Int get() = source.size
    override fun contains(element: T): Boolean = source.contains(element)
    override fun iterator(): Iterator<T> {
        val iterator = source.iterator()
        return object : Iterator<T> {
            override fun hasNext() = iterator.hasNext()
            override fun next() = iterator.next()
        }
    }
}
