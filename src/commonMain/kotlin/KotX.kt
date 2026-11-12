package ru.ought.kotx

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import ru.ought.kotx.internal.Autorun
import ru.ought.kotx.internal.ComputedProperty
import ru.ought.kotx.internal.ObservableProperty

/**
 * A delegated mutable property whose reads are tracked by computed values and autoruns.
 * Assignments equal under Kotlin's `==` do not notify consumers.
 * This experimental runtime requires all state access to happen on one thread.
 */
fun <T> observable(initial: T): ReadWriteProperty<Any?, T> = ObservableProperty(initial)

/**
 * A lazy derived property with dependencies discovered from reads in [expression].
 * Results are cached while observed; unobserved reads evaluate afresh so unused
 * computed values do not keep subscriptions alive. Expressions must not write state.
 */
fun <T> computed(expression: () -> T): ReadOnlyProperty<Any?, T> = ComputedProperty(expression)

/**
 * Runs [effect] immediately, then synchronously after its observable dependencies change.
 * Dependencies follow each execution's reads, including conditional branches.
 * Close the returned handle (or use `use {}`) to unsubscribe; closing is idempotent.
 * The effect may consume state but must not write observable properties.
 */
fun autorun(effect: () -> Unit): AutoCloseable {
    val reaction = Autorun(effect)
    try {
        reaction.run()
    } catch (failure: Throwable) {
        // The caller receives no handle when registration fails, so clean up here.
        reaction.close()
        throw failure
    }
    return reaction
}
