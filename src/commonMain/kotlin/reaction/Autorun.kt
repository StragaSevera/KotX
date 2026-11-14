package ru.ought.kotx.reaction

import ru.ought.kotx.runtime.Runtime
import ru.ought.kotx.tracking.DependencyTracker
import ru.ought.kotx.tracking.Source

/** A terminal graph consumer with an explicit lifetime controlled by its caller. */
private sealed interface AutorunErrorPolicy {
    data object Default : AutorunErrorPolicy
    data object Ignore : AutorunErrorPolicy
    data object Unsubscribe : AutorunErrorPolicy
    class Callbacks(val handlers: List<(Throwable) -> Unit>) : AutorunErrorPolicy
}

/**
 * A managed terminal Reaction. Construct through [start] so initial failure releases
 * subscriptions. Close to detach and cancel queued runs; closing is idempotent.
 */
class Autorun private constructor(
    private val effect: () -> Unit,
    private val errorPolicy: AutorunErrorPolicy,
) : RunnableReaction, AutoCloseable {
    companion object {
        /** Starts synchronously using scoped/global error handling for later failures. */
        fun start(effect: () -> Unit): Autorun = start(effect, AutorunErrorPolicy.Default)

        /** A null callback ignores later failures; initial failures always escape. */
        fun start(onError: ((Throwable) -> Unit)?, effect: () -> Unit): Autorun =
            start(effect, onError?.let { AutorunErrorPolicy.Callbacks(listOf(it)) } ?: AutorunErrorPolicy.Ignore)

        /** Starts with an explicit later-execution error policy. */
        fun start(onError: ReactionErrorPolicy, effect: () -> Unit): Autorun =
            start(effect, when (onError) {
                ReactionErrorPolicy.Ignore -> AutorunErrorPolicy.Ignore
                ReactionErrorPolicy.Unsubscribe -> AutorunErrorPolicy.Unsubscribe
            })

        private fun start(effect: () -> Unit, errorPolicy: AutorunErrorPolicy): Autorun {
            val scopedHandler = Runtime.scopedReactionErrorHandler
            val resolvedPolicy = when (errorPolicy) {
                AutorunErrorPolicy.Default -> scopedHandler?.let {
                    AutorunErrorPolicy.Callbacks(listOf(it))
                } ?: AutorunErrorPolicy.Default
                is AutorunErrorPolicy.Callbacks -> scopedHandler?.let {
                    AutorunErrorPolicy.Callbacks(errorPolicy.handlers + it)
                } ?: errorPolicy
                else -> errorPolicy
            }
            val autorun = Autorun(effect, resolvedPolicy)
            try {
                autorun.evaluate()
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

    override fun run() {
        if (disposed) return
        try {
            evaluate()
        } catch (failure: Throwable) {
            handleFailure(failure)
        }
    }

    private fun evaluate() {
        try {
            tracker.runAsActive(effect)
        } finally {
            // An effect can close its own handle during a later execution. Its remaining
            // reads must not leave newly collected subscriptions behind.
            if (disposed) tracker.clearSources()
        }
    }

    private fun handleFailure(failure: Throwable) {
        when (val policy = errorPolicy) {
            AutorunErrorPolicy.Default -> {
                if (!Runtime.reportReactionError(failure)) close()
            }
            AutorunErrorPolicy.Ignore -> Unit
            AutorunErrorPolicy.Unsubscribe -> close()
            is AutorunErrorPolicy.Callbacks -> {
                var error = failure
                for (handler in policy.handlers) {
                    try {
                        handler(error)
                        return
                    } catch (handlerFailure: Throwable) {
                        close()
                        error = handlerFailure
                    }
                }
                Runtime.reportReactionError(error)
            }
        }
    }

    override fun recordRead(source: Source) = tracker.recordRead(source)

    override fun react() {
        // Queue a run so Computeds can finish marking their caches stale first.
        if (!disposed) Runtime.enqueueReaction(this)
    }

    override fun close() {
        // Remove queued work as well as subscriptions; a closed consumer never reruns.
        disposed = true
        Runtime.removePendingReaction(this)
        tracker.clearSources()
    }
}
