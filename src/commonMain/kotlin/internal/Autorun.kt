package ru.ought.kotx.internal

/** A terminal graph consumer with an explicit lifetime controlled by its caller. */
internal class Autorun(private val effect: () -> Unit) : Subscriber, AutoCloseable {
    private val dependencies = Dependencies(this)
    private var disposed = false

    fun run() {
        if (disposed) return
        try {
            dependencies.collect(effect)
        } finally {
            // An effect can close its own handle during a later execution. Its remaining
            // reads must not leave newly collected subscriptions behind.
            if (disposed) dependencies.clear()
        }
    }

    override fun invalidate() {
        // Queue rather than execute here: computed invalidation must finish first.
        if (!disposed) Runtime.schedule(this)
    }

    override fun close() {
        // Remove queued work as well as subscriptions; a closed consumer never reruns.
        disposed = true
        Runtime.cancel(this)
        dependencies.clear()
    }
}
