package ru.ought.kotx.reaction

/** Creates autoruns with a retained error handler, without ambient runtime state. */
class ReactionScope internal constructor(private val handler: ReactionErrorHandler) {
    /** Runs immediately and uses the scope handler for later failures. */
    fun autorun(effect: () -> Unit): AutoCloseable =
        Autorun.start(ReactionContext.defaultHandler(scoped = handler), effect)

    /**
     * Sends later reaction errors to [onError]. If it returns normally, the scope handler
     * is not invoked. Only if [onError] throws does the scope handler receive that thrown
     * exception, rather than the original reaction error. Null ignores errors entirely.
     */
    fun autorun(onError: ((Throwable) -> Unit)?, effect: () -> Unit): AutoCloseable =
        Autorun.start(ReactionContext.errorHandler(onError, handler), effect)

    /** Applies an explicit policy instead of the scope handler. */
    fun autorun(onError: ReactionErrorPolicy, effect: () -> Unit): AutoCloseable =
        Autorun.start(ReactionContext.errorHandler(onError), effect)

    /**
     * Sends later reaction errors and the subscription to [onError] for selective disposal.
     * If it returns normally, the scope handler is not invoked. Only if [onError] throws
     * does the scope handler receive that thrown exception, rather than the original error.
     */
    fun autorun(onError: ReactionErrorHandler, effect: () -> Unit): AutoCloseable =
        Autorun.start(ReactionContext.errorHandler(onError, handler), effect)
}

