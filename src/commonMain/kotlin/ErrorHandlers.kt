package ru.ought.kotx

import ru.ought.kotx.internal.Runtime

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

/** Process-wide configuration for the KotX reactive runtime. */
object KotXRuntime {
    /** The active global handler, or null when no handler is installed. */
    var currentReactionErrorHandler: ReactionErrorHandler? = null
        private set

    /**
     * Installs the application-global handler for later reaction errors.
     * Only one global handler may be installed at a time. It remains active until closed.
     */
    fun installReactionErrorHandler(effect: (Throwable) -> Unit): ReactionErrorHandler {
        val runtimeHandler = Runtime.installReactionErrorHandler(effect)
        return ReactionErrorHandler(effect, runtimeHandler) { closed ->
            if (currentReactionErrorHandler === closed) currentReactionErrorHandler = null
        }.also { currentReactionErrorHandler = it }
    }
}

/**
 * Associates [handler] with default-policy autoruns created synchronously inside [block].
 * Those autoruns retain this handler after [block] returns.
 */
fun <T> withReactionErrorHandler(handler: (Throwable) -> Unit, block: () -> T): T =
    Runtime.withReactionErrorHandler(handler, block)
