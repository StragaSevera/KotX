package ru.ought.kotx.internal

/**
 * Tracks dependencies through reads and responds when one may have changed.
 *
 * A source asks its subscribers to [react] when its value may have changed.
 * While this is the active reaction, sources report their reads through [recordRead].
 */
internal interface Reaction {
    /** Responds to a notification, for example, by marking a cache stale or queuing a run. */
    fun react()

    /** Records a source read as a dependency of the current evaluation. */
    fun recordRead(source: Source)
}

/** A reaction that can queue work for the runtime to execute after notifications finish. */
internal interface RunnableReaction : Reaction {
    /** Executes the reaction's work, for example, by evaluating an autorun block. */
    fun run()
}
