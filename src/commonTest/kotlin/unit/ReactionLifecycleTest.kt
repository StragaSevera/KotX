package ru.ought.kotx.unit

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.ought.kotx.computed.ComputedProperty
import ru.ought.kotx.reaction.Autorun
import ru.ought.kotx.reaction.Reaction
import ru.ought.kotx.tracking.Source
import ru.ought.kotx.tracking.SubscriptionSource

private class LifecycleSource : SubscriptionSource() {
    val hasSubscribers: Boolean get() = observed
    fun read() = reportRead()
}

private class PassiveReaction : Reaction {
    var invalidations = 0
    override fun onSourceRead(source: Source) = Unit
    override fun onInvalidated() { invalidations++ }
}

@Suppress("unused")
val ReactionLifecycleTests by testSuite {
    testSuite("Computed subscriptions") {
        test("detaching the last consumer releases an entire Computed chain") {
            val source = LifecycleSource()
            val first = ComputedProperty { source.read(); 1 }
            val second = ComputedProperty { first.value + 1 }
            val consumer = PassiveReaction()
            consumer.subscribe(second)
            try {
                second.value.shouldBe(2)
                source.hasSubscribers.shouldBe(true)
            } finally {
                consumer.unsubscribe(second)
            }
            source.hasSubscribers.shouldBe(false)
        }

        test("observed failures remain cached until invalidation") {
            val failure = IllegalStateException("evaluation failed")
            var evaluations = 0
            val computed = ComputedProperty<Int> {
                evaluations++
                throw failure
            }
            val consumer = PassiveReaction()
            consumer.subscribe(computed)
            try {
                repeat(2) {
                    (shouldThrow<IllegalStateException> { computed.value } === failure).shouldBe(true)
                }
                evaluations.shouldBe(1)
                computed.onInvalidated()
                shouldThrow<IllegalStateException> { computed.value }
                evaluations.shouldBe(2)
            } finally {
                consumer.unsubscribe(computed)
            }
        }

        test("repeated invalidation notifies consumers only once while stale") {
            val computed = ComputedProperty { 1 }
            val consumer = PassiveReaction()
            consumer.subscribe(computed)
            try {
                computed.value
                computed.onInvalidated()
                computed.onInvalidated()
                consumer.invalidations.shouldBe(1)
            } finally {
                consumer.unsubscribe(computed)
            }
        }

        test("failed unobserved evaluation releases dependencies") {
            val source = LifecycleSource()
            val computed = ComputedProperty<Int> {
                source.read()
                error("evaluation failed")
            }
            shouldThrow<IllegalStateException> { computed.value }
            source.hasSubscribers.shouldBe(false)
        }
    }

    testSuite("Autorun disposal") {
        test("closed autoruns ignore direct execution and invalidation") {
            var runs = 0
            val reaction = Autorun.start { runs++ }

            reaction.close()
            reaction.execute()
            reaction.onInvalidated()

            runs.shouldBe(1)
        }

        test("self-disposal preserves error handling for the current execution") {
            val failure = IllegalStateException("effect failed")
            val errors = mutableListOf<Throwable>()
            var fail = false
            lateinit var reaction: Autorun
            reaction = Autorun.start(onError = { errors.add(it) }) {
                if (fail) {
                    reaction.close()
                    throw failure
                }
            }
            try {
                fail = true
                reaction.execute()
                errors.shouldBe(listOf(failure))
            } finally {
                reaction.close()
            }
        }

        test("closing during execution releases sources read after close") {
            val source = LifecycleSource()
            var closeDuringExecution = false
            lateinit var reaction: Autorun
            reaction = Autorun.start {
                if (closeDuringExecution) reaction.close()
                source.read()
            }
            try {
                closeDuringExecution = true
                reaction.execute()
                source.hasSubscribers.shouldBe(false)
            } finally {
                reaction.close()
            }
        }
    }
}
