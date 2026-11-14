package ru.ought.kotx.runtime

import ru.ought.kotx.reaction.Reaction
import ru.ought.kotx.reaction.RunnableReaction
import ru.ought.kotx.tracking.DependencyTracker
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

    private var notificationDepth = 0
    private var runningPendingReactions = false
    private var globalReactionErrorHandler: ((Throwable) -> Unit)? = null
    var scopedReactionErrorHandler: ((Throwable) -> Unit)? = null
        private set
    // One reaction may be reached through several computed paths from a single write.
    private val pendingReactions = mutableListOf<RunnableReaction>()

    fun enqueueReaction(reaction: RunnableReaction) {
        if (pendingReactions.none { it === reaction }) pendingReactions.add(reaction)
        if (notificationDepth == 0) runPendingReactions()
    }

    fun removePendingReaction(reaction: RunnableReaction) {
        pendingReactions.removeAll { it === reaction }
    }

    fun installReactionErrorHandler(effect: (Throwable) -> Unit): AutoCloseable {
        check(globalReactionErrorHandler == null) { "A global reaction error handler is already installed" }
        globalReactionErrorHandler = effect
        return object : AutoCloseable {
            private var closed = false

            override fun close() {
                if (closed) return
                closed = true
                if (globalReactionErrorHandler === effect) globalReactionErrorHandler = null
            }
        }
    }

    fun <T> withReactionErrorHandler(handler: (Throwable) -> Unit, block: () -> T): T {
        val enclosing = scopedReactionErrorHandler
        scopedReactionErrorHandler = handler
        try {
            return block()
        } finally {
            scopedReactionErrorHandler = enclosing
        }
    }

    /** Reports an error when a global handler exists and returns whether it succeeded. */
    fun reportReactionError(error: Throwable): Boolean {
        val handler = globalReactionErrorHandler ?: return false
        return try {
            handler(error)
            true
        } catch (_: Throwable) {
            false
        }
    }

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
            if (notificationDepth == 0) runPendingReactions()
        }
    }

    private fun runPendingReactions() {
        // Future-proofing: a runnable reaction may eventually write outside read tracking
        // (for example, an onChangeOf effect). If that write finishes notifying while
        // this loop is running, leave any newly queued reactions for this loop to run.
        if (runningPendingReactions) return
        runningPendingReactions = true
        var failure: Throwable? = null
        try {
            while (pendingReactions.isNotEmpty()) {
                val reaction = pendingReactions.first()
                pendingReactions.removeAt(0)
                try {
                    // Terminal reaction implementations contain user-code failures at
                    // their own boundary; unexpected runtime failures remain visible.
                    reaction.run()
                } catch (error: Throwable) {
                    if (failure == null) failure = error
                }
            }
        } finally {
            runningPendingReactions = false
        }
        failure?.let { throw it }
    }
}
