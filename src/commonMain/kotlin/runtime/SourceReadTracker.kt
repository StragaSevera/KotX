package ru.ought.kotx.runtime

import ru.ought.kotx.reaction.SourceReadListener
import ru.ought.kotx.tracking.Source

/** Routes source reads within nested synchronous tracking scopes. */
internal object SourceReadTracker {
    // Intentionally execution-global for this single-threaded JVM/JS prototype.
    private var activeReadListener: SourceReadListener? = null
    val isTracking: Boolean get() = activeReadListener != null

    fun reportRead(source: Source) {
        activeReadListener?.onSourceRead(source)
    }

    fun <T> withReadListener(listener: SourceReadListener, block: () -> T): T {
        val enclosing = activeReadListener
        activeReadListener = listener
        try {
            return block()
        } finally {
            activeReadListener = enclosing
        }
    }

}
