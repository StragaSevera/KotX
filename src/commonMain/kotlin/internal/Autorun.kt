package ru.ought.kotx.internal

/** A terminal graph consumer with an explicit lifetime controlled by its caller. */
internal sealed interface AutorunErrorPolicy {
    data object Default : AutorunErrorPolicy
    data object Ignore : AutorunErrorPolicy
    data object Unsubscribe : AutorunErrorPolicy
    class Callbacks(val handlers: List<(Throwable) -> Unit>) : AutorunErrorPolicy
}

internal class Autorun private constructor(
    private val effect: () -> Unit,
    private val errorPolicy: AutorunErrorPolicy,
) : RunnableReaction, AutoCloseable {
    companion object {
        fun start(effect: () -> Unit, errorPolicy: AutorunErrorPolicy): AutoCloseable {
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
