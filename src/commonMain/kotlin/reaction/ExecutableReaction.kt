package ru.ought.kotx.reaction

/**
 * A Reaction whose work can be queued through KotXRuntime.enqueueReaction.
 * Implementations own disposal and error handling. An execution must ignore disposed work.
 * Queueing is deduplicated by identity until the queued work is taken for execution.
 */
interface ExecutableReaction : Reaction {
    fun execute()
}
