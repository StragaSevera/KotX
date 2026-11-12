package ru.ought.kotx.internal

/** A terminal graph consumer with an explicit lifetime controlled by its caller. */
internal class Autorun(private val effect: () -> Unit) : RunnableReaction, AutoCloseable {
    private val dependencies = ReactionDependencies(this)
    private var disposed = false

    override fun run() {
        if (disposed) return
        try {
            dependencies.trackReads(effect)
        } finally {
            // An effect can close its own handle during a later execution. Its remaining
            // reads must not leave newly collected subscriptions behind.
            if (disposed) dependencies.clear()
        }
    }

    override fun recordRead(source: Source) = dependencies.recordRead(source)

    override fun react() {
        // Queue a run so Computeds can finish marking their caches stale first.
        if (!disposed) Runtime.enqueueReaction(this)
    }

    override fun close() {
        // Remove queued work as well as subscriptions; a closed consumer never reruns.
        disposed = true
        Runtime.removePendingReaction(this)
        dependencies.clear()
    }
}
