package ru.ought.kotx.reaction

import ru.ought.kotx.runtime.Runtime
import ru.ought.kotx.tracking.DependencyTracker
import ru.ought.kotx.tracking.Source

/**
 * A managed terminal Reaction. Construct through [start] so initial failure releases
 * subscriptions. Close to detach and cancel queued runs; closing is idempotent.
 */
class Autorun private constructor(
    private val effect: () -> Unit,
    private val errorHandler: ReactionErrorHandler,
) : ExecutableReaction, AutoCloseable {
    companion object {
        /**
         * Starts immediately; initial failures unsubscribe and escape.
         * Later failures use the current global error handler. Without a handler, or if
         * it throws, the autorun unsubscribes. Later failures do not escape.
         */
        fun start(effect: () -> Unit): Autorun = start(ReactionContext.defaultHandler(), effect)

        /** Handles later failures; null ignores them. */
        fun start(onError: ((Throwable) -> Unit)?, effect: () -> Unit): Autorun =
            start(ReactionContext.errorHandler(onError), effect)

        /** Applies the policy to later failures. */
        fun start(onError: ReactionErrorPolicy, effect: () -> Unit): Autorun =
            start(ReactionContext.errorHandler(onError), effect)

        internal fun start(context: ReactionContext, effect: () -> Unit): Autorun {
            val autorun = Autorun(effect, context.errorHandler)
            try {
                autorun.doExecute()
                return autorun
            } catch (failure: Throwable) {
                // No handle escapes when the initial run fails, so release its subscriptions.
                autorun.close()
                throw failure
            }
        }
    }

    private val tracker = DependencyTracker(this)
    private var disposed = false

    override fun recordRead(source: Source) = tracker.recordRead(source)

    override fun react() {
        // Queue a run instead of executing immediately,
        // so Computeds can finish marking their caches stale first.
        if (!disposed) Runtime.enqueueExecutable(this)
    }

    override fun execute() {
        if (disposed) return
        try {
            doExecute()
        } catch (failure: Throwable) {
            errorHandler.handle(failure, this)
        }
    }

    private fun doExecute() {
        try {
            tracker.runAsActive(effect)
        } finally {
            // An effect can close its own handle during a later execution. Its remaining
            // reads must not leave newly collected subscriptions behind.
            if (disposed) tracker.clearSources()
        }
    }

    override fun close() {
        // Remove queued work as well as subscriptions; a closed consumer never reruns.
        disposed = true
        Runtime.dequeueExecutable(this)
        tracker.clearSources()
    }
}
