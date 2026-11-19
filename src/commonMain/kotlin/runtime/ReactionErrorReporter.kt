package ru.ought.kotx.runtime

import ru.ought.kotx.reaction.ReactionErrorHandler

/** Reports reaction failures to the application-global fallback handler. */
internal object ReactionErrorReporter {
    var globalErrorHandler: ReactionErrorHandler? = null
    /** Reports an error when a global handler exists and returns whether it succeeded. */
    fun reportReactionError(error: Throwable, subscription: AutoCloseable): Boolean {
        val handler = globalErrorHandler ?: return false
        return try {
            handler.handle(error, subscription)
            true
        } catch (_: Throwable) {
            false
        }
    }

}
