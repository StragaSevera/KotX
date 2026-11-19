package ru.ought.kotx.runtime

import ru.ought.kotx.reaction.ExecutableReaction
import ru.ought.kotx.reaction.ReactionExecutionException

/** Queues and executes pending reactions after invalidation completes. */
internal object ReactionScheduler {
    private var runningExecutables = false
    // One reaction may be reached through several computed paths from a single write.
    private val pendingExecutables = mutableListOf<ExecutableReaction>()

    fun enqueueReaction(reaction: ExecutableReaction) {
        if (pendingExecutables.none { it === reaction }) pendingExecutables.add(reaction)
        if (!InvalidationDispatcher.isInvalidating()) executePending()
    }

    fun removePendingReaction(reaction: ExecutableReaction) {
        pendingExecutables.removeAll { it === reaction }
    }

    internal fun executePending() {
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
