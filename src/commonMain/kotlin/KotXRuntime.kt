package ru.ought.kotx

import ru.ought.kotx.reaction.ExecutableReaction
import ru.ought.kotx.reaction.InvalidationListener
import ru.ought.kotx.reaction.SourceReadListener
import ru.ought.kotx.reaction.ReactionErrorHandler
import ru.ought.kotx.runtime.SourceReadTracker
import ru.ought.kotx.runtime.ReactionErrorReporter
import ru.ought.kotx.runtime.ReactionScheduler
import ru.ought.kotx.runtime.InvalidationDispatcher
import ru.ought.kotx.tracking.Source

/**
 * Public runtime configuration and coordination for custom sources and reactions.
 * Library implementation delegates directly to the internal runtime modules.
 */
object KotXRuntime {
    /**
     * Notifies a snapshot of invalidation [listeners], then drains queued work after outermost
     * notification completes. All subscribers are visited even if one throws.
     * Implementations publish their state before calling this single-threaded operation.
     */
    fun notifyInvalidationListeners(listeners: List<InvalidationListener>) =
        InvalidationDispatcher.notifyInvalidationListeners(listeners)

    /**
     * Queues by identity during notification; otherwise runs synchronously.
     * Repeated requests before execution coalesce. Runs must manage their own errors.
     */
    fun enqueueReaction(reaction: ExecutableReaction) = ReactionScheduler.enqueueReaction(reaction)

    /** Cancels pending work by identity. Already executing work cannot be cancelled. */
    fun removePendingReaction(reaction: ExecutableReaction) = ReactionScheduler.removePendingReaction(reaction)

    /** Reports a source read to the active listener when a tracking scope is active. */
    fun reportRead(source: Source) = SourceReadTracker.reportRead(source)

    /**
     * Routes synchronous source reads to [listener] for [block] and restores the enclosing
     * listener even on failure. Returns the result and propagates exceptions.
     * Callers manage subscriptions separately. Tracking is scoped to synchronous execution.
     */
    fun <T> withReadListener(listener: SourceReadListener, block: () -> T): T =
        SourceReadTracker.withReadListener(listener, block)

    /** The current application-global fallback for later reaction failures. */
    val globalErrorHandler: ReactionErrorHandler?
        get() = ReactionErrorReporter.globalErrorHandler

    /** Sets or replaces the global handler, including for existing reactions. */
    fun setGlobalErrorHandler(handler: ReactionErrorHandler) {
        ReactionErrorReporter.globalErrorHandler = handler
    }

    /** Removes the global handler without closing existing reactions. */
    fun removeGlobalErrorHandler() {
        ReactionErrorReporter.globalErrorHandler = null
    }
}
