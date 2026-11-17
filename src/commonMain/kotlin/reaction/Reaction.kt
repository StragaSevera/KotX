package ru.ought.kotx.reaction

import ru.ought.kotx.tracking.DependencyTracker

/**
 * An experimental consumer that owns its dependencies and responds to invalidation.
 * Implementers manage subscription cleanup and notification behavior themselves;
 * [DependencyTracker] is an optional helper.
 */
interface Reaction : SourceReadListener, InvalidationListener
