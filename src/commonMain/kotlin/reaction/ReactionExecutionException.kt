package ru.ought.kotx.reaction

/**
 * Multiple failures that escaped queued reaction execution.
 * [failures] retains every failure in execution order; the queue finishes before this is thrown.
 */
class ReactionExecutionException internal constructor(failures: List<Throwable>) :
    RuntimeException("${failures.size} failures escaped reaction execution", failures.first()) {
    val failures: List<Throwable> = failures.toList()

    init {
        failures.drop(1).forEach { addSuppressed(it) }
    }
}
