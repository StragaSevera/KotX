package ru.ought.kotx

import ru.ought.kotx.computed.ComputedProperty
import ru.ought.kotx.observable.ObservableProperty
import ru.ought.kotx.observable.StoredObservableProperty
import ru.ought.kotx.reaction.ReactionContext
import ru.ought.kotx.reaction.Autorun
import ru.ought.kotx.reaction.ReactionErrorHandler
import ru.ought.kotx.reaction.ReactionErrorPolicy
import ru.ought.kotx.view.PropertyView

/**
 * A delegated mutable property whose reads are tracked by computed values and autoruns.
 * Assignments equal under Kotlin's `==` do not notify consumers.
 * This experimental runtime requires all state access to happen on one thread.
 */
fun <T> observable(initial: T): ObservableProperty<T> = StoredObservableProperty(initial)

/**
 * A **lazy** derived property that discovers dependencies by tracking reads in [computation].
 * When a dependency changes, the cached result becomes stale and subscribers are
 * notified. The expression is evaluated only when the property is read, so a
 * synchronous autorun can cause reevaluation before the original write returns.
 *
 * Results are cached while observed; unobserved reads evaluate afresh so unused
 * computed values do not keep subscriptions alive. Expressions must not write state.
 */
fun <T> computed(computation: () -> T): PropertyView<T> = ComputedProperty(computation)

/**
 * Runs [effect] immediately, then synchronously after its observable dependencies change.
 * Dependencies follow each execution's reads, including conditional branches.
 * Close the returned handle (or use `use {}`) to unsubscribe; closing is idempotent.
 * The effect may consume state but must not write observable properties.
 * Initial failures unsubscribe and propagate to the caller. Later failures are reported
 * to the current global error handler; without a handler, or if that handler throws,
 * the autorun unsubscribes. Later failures do not propagate to the triggering write.
 */
fun autorun(effect: () -> Unit): AutoCloseable = Autorun.start(ReactionContext.defaultHandler(), effect)

/**
 * Runs [effect] as an autorun with explicit error handling for executions after the first.
 * A null [onError] discards later errors and keeps the autorun subscribed.
 */
fun autorun(onError: ((Throwable) -> Unit)?, effect: () -> Unit): AutoCloseable =
    Autorun.start(ReactionContext.errorHandler(onError), effect)

/** Runs [effect] as an autorun using explicit later-execution error handling. */
fun autorun(onError: ReactionErrorPolicy, effect: () -> Unit): AutoCloseable =
    Autorun.start(ReactionContext.errorHandler(onError), effect)

/**
 * Handles later failures with access to the subscription, which may be closed selectively.
 * A throwing handler unsubscribes the autorun and forwards the failure to fallback handlers.
 * Initial failures unsubscribe and escape to the caller.
 */
fun autorun(onError: (Throwable, AutoCloseable) -> Unit, effect: () -> Unit): AutoCloseable =
    Autorun.start(ReactionContext.errorHandler(onError), effect)

/** Uses a reusable handler for later failures. Initial failures unsubscribe and escape. */
fun autorun(onError: ReactionErrorHandler, effect: () -> Unit): AutoCloseable =
    Autorun.start(ReactionContext.errorHandler(onError), effect)
