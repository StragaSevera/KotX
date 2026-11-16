package ru.ought.kotx.reaction

/** Explicit handling for errors from reaction executions after their initial run. */
enum class ReactionErrorPolicy {
    /**
     * Bypass the scope handler and use the current global handler on each later failure.
     * Unsubscribe if no global handler exists or it throws.
     */
    Global,

    /** Discard the error and keep the reaction subscribed. */
    Ignore,

    /** Discard the error and unsubscribe the reaction. */
    Unsubscribe,
}

/** Handles a later reaction failure; close [subscription] to unsubscribe that reaction. */
fun interface ReactionErrorHandler {
    fun handle(error: Throwable, subscription: AutoCloseable)
}

/**
 * Supplies a receiver whose autorun helpers retain [handler] after [block] returns.
 * Ordinary helper functions do not inherit this receiver's error handling.
 */
fun <T> withReactionErrorHandler(handler: (Throwable) -> Unit, block: ReactionScope.() -> T): T =
    withReactionErrorHandler({ error, _ -> handler(error) }, block)

/** Supplies a scope with a handler that can selectively unsubscribe reactions. */
fun <T> withReactionErrorHandler(handler: ReactionErrorHandler, block: ReactionScope.() -> T): T =
    ReactionScope(handler).block()
