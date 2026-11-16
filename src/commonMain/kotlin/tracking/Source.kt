package ru.ought.kotx.tracking

import ru.ought.kotx.reaction.Reaction

/**
 * An observable dependency. Implementations need to ensure subscription correctness.
 * Subscribe and unsubscribe must be idempotent and compare reactions by identity.
 * All access currently requires one thread. Implementations publish state before notification.
 */
interface Source {
    fun subscribe(reaction: Reaction)
    fun unsubscribe(reaction: Reaction)
}
