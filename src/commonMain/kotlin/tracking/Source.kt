package ru.ought.kotx.tracking

import ru.ought.kotx.reaction.InvalidationListener

/**
 * An observable dependency. Implementations need to ensure subscription correctness.
 * Adding and removing listeners must be idempotent and compare listeners by identity.
 * All access currently requires one thread. Implementations publish state before notification.
 */
interface Source {
    fun addListener(listener: InvalidationListener)
    fun removeListener(listener: InvalidationListener)
}
