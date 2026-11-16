package ru.ought.kotx.observable

import ru.ought.kotx.view.ListView

/**
 * A mutable observable list with whole-collection tracking and shallow elements.
 * Bulk member operations notify once, including partial changes before failure.
 * Access is single-threaded; outside structural changes invalidate iterators and ranges.
 */
interface ObservableList<T> : MutableList<T>, ListView<T> {
    override fun asView(): ListView<T>
    override fun subList(fromIndex: Int, toIndex: Int): ObservableList<T>
}

fun <T> observableListOf(vararg elements: T): ObservableList<T> = ObservableArrayList(elements.asList())
