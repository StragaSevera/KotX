package ru.ought.kotx.reaction

import ru.ought.kotx.runtime.Runtime

/** Explicit handling for errors from reaction executions after their initial run. */
enum class ReactionErrorPolicy {
    /** Discard the error and keep the reaction subscribed. */
    Ignore,

    /** Discard the error and unsubscribe the reaction. */
    Unsubscribe,
}

/** The currently installed global reaction error handler and its lifetime. */
class ReactionErrorHandler internal constructor(
    val effect: (Throwable) -> Unit,
    private val runtimeRegistration: AutoCloseable,
    private val onClosed: (ReactionErrorHandler) -> Unit,
) : AutoCloseable {
    var isClosed: Boolean = false
        private set

    override fun close() {
        if (isClosed) return
        isClosed = true
        runtimeRegistration.close()
        onClosed(this)
    }
}

/**
 * Associates [handler] with default-policy autoruns created synchronously inside [block].
 * Those autoruns retain this handler after [block] returns.
 */
fun <T> withReactionErrorHandler(handler: (Throwable) -> Unit, block: () -> T): T =
    Runtime.withReactionErrorHandler(handler, block)
