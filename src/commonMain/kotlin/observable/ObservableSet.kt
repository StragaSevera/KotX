package ru.ought.kotx.observable

import ru.ought.kotx.view.SetView

/** A single-threaded, insertion-ordered mutable set with whole-collection tracking. */
interface ObservableSet<T> : MutableSet<T>, SetView<T> {
    override fun asView(): SetView<T>
}

fun <T> observableSetOf(vararg elements: T): ObservableSet<T> = ObservableLinkedHashSet(elements.asList())
