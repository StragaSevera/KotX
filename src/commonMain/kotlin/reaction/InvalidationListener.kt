package ru.ought.kotx.reaction

import ru.ought.kotx.tracking.Source

/** Subscribes to a source and receives notifications when that source may have changed. */
fun interface InvalidationListener {
    fun onInvalidated()

    fun subscribe(source: Source) = source.addListener(this)
    fun unsubscribe(source: Source) = source.removeListener(this)
}
