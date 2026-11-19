package ru.ought.kotx.reaction

import ru.ought.kotx.runtime.ReactionScheduler
import ru.ought.kotx.tracking.DependencyTracker
import ru.ought.kotx.tracking.Source

/**
 * A managed terminal Reaction. Construct through [start] so initial failure releases
 * subscriptions. Close to detach, cancel queued runs, and release captured callbacks;
 * closing is idempotent.
 */
class Autorun private constructor(
    effect: () -> Unit,
    errorHandler: ReactionErrorHandler,
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

    private var effect: (() -> Unit)? = effect
    private var errorHandler: ReactionErrorHandler? = errorHandler
    private val tracker = DependencyTracker(this)
    private var disposed = false

    override fun onSourceRead(source: Source) = tracker.recordDependency(source)

    override fun onInvalidated() {
        // Queue a run instead of executing immediately,
        // so Computeds can finish marking their caches stale first.
        if (!disposed) ReactionScheduler.enqueueReaction(this)
    }

    override fun execute() {
        if (disposed) return
        // Keep the current execution's error policy even if its effect closes itself.
        val handler = errorHandler ?: return
        try {
            doExecute()
        } catch (failure: Throwable) {
            handler.handle(failure, this)
        }
    }

    private fun doExecute() {
        val currentEffect = effect ?: return
        try {
            tracker.runAsActive(currentEffect)
        } finally {
            // An effect can close its own handle during a later execution. Its remaining
            // reads must not leave newly collected subscriptions behind.
            if (disposed) tracker.clearSources()
        }
    }

    override fun close() {
        // Remove queued work as well as subscriptions; a closed consumer never reruns.
        disposed = true
        effect = null
        errorHandler = null
        ReactionScheduler.removePendingReaction(this)
        tracker.clearSources()
    }
}
