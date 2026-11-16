package ru.ought.kotx

import ru.ought.kotx.reaction.ExecutableReaction
import ru.ought.kotx.reaction.Reaction
import ru.ought.kotx.reaction.ReactionErrorHandler
import ru.ought.kotx.runtime.RuntimeEngine
import ru.ought.kotx.tracking.Source

/**
 * Public runtime configuration and coordination for custom sources and reactions.
 * Library implementation delegates directly to the internal runtime engine.
 */
object KotXRuntime {
    /**
     * Invalidates a snapshot of [reactions], then drains queued work after outermost
     * notification completes. All subscribers are visited even if one throws.
     * Implementations publish their state before calling this single-threaded operation.
     */
    fun notifyReactions(reactions: List<Reaction>) = RuntimeEngine.notifyReactions(reactions)

    /**
     * Queues by identity during notification; otherwise runs synchronously.
     * Repeated requests before execution coalesce. Runs must manage their own errors.
     */
    fun enqueueReaction(reaction: ExecutableReaction) = RuntimeEngine.enqueueReaction(reaction)

    /** Cancels pending work by identity. Already executing work cannot be cancelled. */
    fun removePendingReaction(reaction: ExecutableReaction) = RuntimeEngine.removePendingReaction(reaction)

    /** Reports a source read to the active Reaction; does nothing outside tracking. */
    fun reportRead(source: Source) = RuntimeEngine.reportRead(source)

    /**
     * Routes synchronous reads to [reaction] for [block], restoring the enclosing
     * Reaction even on failure. Returns the result and propagates exceptions.
     * Does not manage subscriptions or propagate tracking through suspension.
     */
    fun <T> withReaction(reaction: Reaction, block: () -> T): T =
        RuntimeEngine.withReaction(reaction, block)

    /** The current application-global fallback for later reaction failures. */
    val globalErrorHandler: ReactionErrorHandler?
        get() = RuntimeEngine.globalErrorHandler

    /** Sets or replaces the global handler, including for existing reactions. */
    fun setGlobalErrorHandler(handler: ReactionErrorHandler) {
        RuntimeEngine.globalErrorHandler = handler
    }

    /** Removes the global handler without closing existing reactions. */
    fun removeGlobalErrorHandler() {
        RuntimeEngine.globalErrorHandler = null
    }
}
