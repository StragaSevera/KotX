package ru.ought.kotx.reaction

import ru.ought.kotx.tracking.Source
/**
 * An experimental consumer that owns its dependencies and responds to invalidation.
 * Implementers manage subscription cleanup and notification behavior themselves;
 * [DependencyTracker] is an optional helper. This protocol may change before stabilization.
 */
interface Reaction {
    /** Records a source as a dependency during a scoped synchronous evaluation. */
    fun recordDependency(source: Source)

    /** Responds to possible change, without implying immediate reevaluation. */
    fun react()
}
