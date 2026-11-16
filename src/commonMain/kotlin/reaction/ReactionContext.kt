package ru.ought.kotx.reaction

import ru.ought.kotx.runtime.Runtime

/** Creation-time configuration explicitly passed by reaction helpers. */
internal class ReactionContext(val errorHandler: ReactionErrorHandler) {
    companion object {
        private val defaultContext = ReactionContext { error, subscription ->
            if (!Runtime.reportReactionError(error, subscription)) subscription.close()
        }
        private val ignoreContext = ReactionContext { _, _ -> }
        private val unsubscribeContext = ReactionContext { _, subscription -> subscription.close() }

        fun defaultHandler(scoped: ReactionErrorHandler? = null): ReactionContext =
            if (scoped == null) defaultContext else callbacks(listOf(scoped))

        fun errorHandler(handler: ((Throwable) -> Unit)?, scoped: ReactionErrorHandler? = null): ReactionContext =
            if (handler == null) errorHandler(ReactionErrorPolicy.Ignore)
            else errorHandler({ error, _ -> handler(error) }, scoped)

        fun errorHandler(policy: ReactionErrorPolicy): ReactionContext =
            when (policy) {
                ReactionErrorPolicy.Global -> defaultContext
                ReactionErrorPolicy.Ignore -> ignoreContext
                ReactionErrorPolicy.Unsubscribe -> unsubscribeContext
            }

        fun errorHandler(handler: ReactionErrorHandler, scoped: ReactionErrorHandler? = null): ReactionContext {
            val handlers = mutableListOf(handler)
            if (scoped != null) handlers.add(scoped)
            return callbacks(handlers)
        }

        private fun callbacks(handlers: List<ReactionErrorHandler>): ReactionContext =
            ReactionContext(ReactionErrorHandler { failure, subscription ->
                var error = failure
                for (handler in handlers) {
                    try {
                        handler.handle(error, subscription)
                        return@ReactionErrorHandler
                    } catch (handlerFailure: Throwable) {
                        subscription.close()
                        error = handlerFailure
                    }
                }
                Runtime.reportReactionError(error, subscription)
            })
    }
}
