package ru.ought.kotx

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import ru.ought.kotx.internal.Autorun
import ru.ought.kotx.internal.AutorunErrorPolicy as InternalAutorunErrorPolicy
import ru.ought.kotx.internal.ComputedProperty
import ru.ought.kotx.internal.ObservableProperty

/**
 * A delegated mutable property whose reads are tracked by computed values and autoruns.
 * Assignments equal under Kotlin's `==` do not notify consumers.
 * This experimental runtime requires all state access to happen on one thread.
 */
fun <T> observable(initial: T): ReadWriteProperty<Any?, T> = ObservableProperty(initial)

/**
 * A **lazy** derived property that discovers dependencies by tracking reads in [computation].
 * When a dependency changes, the cached result becomes stale and subscribers are
 * notified. The expression is evaluated only when the property is read, so a
 * synchronous autorun can cause reevaluation before the original write returns.
 *
 * Results are cached while observed; unobserved reads evaluate afresh so unused
 * computed values do not keep subscriptions alive. Expressions must not write state.
 */
fun <T> computed(computation: () -> T): ReadOnlyProperty<Any?, T> = ComputedProperty(computation)

/**
 * Runs [effect] immediately, then synchronously after its observable dependencies change.
 * Dependencies follow each execution's reads, including conditional branches.
 * Close the returned handle (or use `use {}`) to unsubscribe; closing is idempotent.
 * The effect may consume state but must not write observable properties.
 */
fun autorun(effect: () -> Unit): AutoCloseable =
    Autorun.start(effect, InternalAutorunErrorPolicy.Default)

/**
 * Runs [effect] as an autorun with explicit error handling for executions after the first.
 * A null [onError] discards later errors and keeps the autorun subscribed.
 */
fun autorun(onError: ((Throwable) -> Unit)?, effect: () -> Unit): AutoCloseable =
    Autorun.start(
        effect,
        onError?.let { InternalAutorunErrorPolicy.Callbacks(listOf(it)) }
            ?: InternalAutorunErrorPolicy.Ignore,
    )

/** Runs [effect] as an autorun using explicit later-execution error handling. */
fun autorun(onError: ReactionErrorPolicy, effect: () -> Unit): AutoCloseable =
    Autorun.start(
        effect,
        when (onError) {
            ReactionErrorPolicy.Ignore -> InternalAutorunErrorPolicy.Ignore
            ReactionErrorPolicy.Unsubscribe -> InternalAutorunErrorPolicy.Unsubscribe
        },
    )
