package ru.ought.kotx.reaction

import ru.ought.kotx.tracking.Source

/** Receives reports of source reads during a scoped synchronous evaluation. */
fun interface SourceReadListener {
    fun onSourceRead(source: Source)
}
