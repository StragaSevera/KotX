package ru.ought.kotx.runtime

import ru.ought.kotx.reaction.Reaction
import ru.ought.kotx.reaction.ReactionExecutionException
import ru.ought.kotx.reaction.ReactionErrorHandler
import ru.ought.kotx.reaction.ExecutableReaction
import ru.ought.kotx.tracking.Source

/** Routes reads to the active reaction and coordinates notifications and pending runs. */
internal object Runtime {
    // Intentionally execution-global for this single-threaded JVM/JS prototype.
    private var activeReaction: Reaction? = null
    val isTracking: Boolean get() = activeReaction != null

    fun recordRead(source: Source) {
        activeReaction?.recordRead(source)
    }

    fun <T> withReaction(reaction: Reaction, block: () -> T): T {
        val enclosing = activeReaction
        activeReaction = reaction
        try {
            return block()
        } finally {
            activeReaction = enclosing
        }
    }

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

    private var notificationDepth = 0
    fun notifyReactions(reactions: List<Reaction>) {
        // A ComputedProperty can notify its own subscribers. Wait for the outermost
        // notification to finish so all affected caches are stale before reactions run.
        notificationDepth++
        try {
            var failure: Throwable? = null
            for (reaction in reactions) {
                try {
                    reaction.react()
                } catch (error: Throwable) {
                    if (failure == null) failure = error
                }
            }
            failure?.let { throw it }
        } finally {
            notificationDepth--
            if (notificationDepth == 0) executePending()
        }
    }

    private var runningExecutables = false
    // One reaction may be reached through several computed paths from a single write.
    private val pendingExecutables = mutableListOf<ExecutableReaction>()

    fun enqueueExecutable(reaction: ExecutableReaction) {
        if (pendingExecutables.none { it === reaction }) pendingExecutables.add(reaction)
        if (notificationDepth == 0) executePending()
    }

    fun dequeueExecutable(reaction: ExecutableReaction) {
        pendingExecutables.removeAll { it === reaction }
    }

    private fun executePending() {
        // Future-proofing: an executable reaction may eventually write outside read tracking
        // (for example, an onChangeOf effect). If that write finishes notifying while
        // this loop is running, leave any newly queued reactions for this loop to run.
        if (runningExecutables) return
        runningExecutables = true
        val errors = mutableListOf<Throwable>()
        try {
            while (pendingExecutables.isNotEmpty()) {
                val reaction = pendingExecutables.first()
                pendingExecutables.removeAt(0)
                try {
                    reaction.execute()
                } catch (error: Throwable) {
                    errors.add(error)
                }
            }
        } finally {
            runningExecutables = false
        }
        when (errors.size) {
            0 -> Unit
            1 -> throw errors.single()
            else -> throw ReactionExecutionException(errors)
        }
    }
}
