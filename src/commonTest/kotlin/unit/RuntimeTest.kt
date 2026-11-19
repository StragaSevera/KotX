package ru.ought.kotx.unit

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.ought.kotx.reaction.Reaction
import ru.ought.kotx.reaction.ReactionExecutionException
import ru.ought.kotx.reaction.ExecutableReaction
import ru.ought.kotx.runtime.ReactionScheduler
import ru.ought.kotx.runtime.InvalidationDispatcher
import ru.ought.kotx.tracking.Source

private class CountingExecutableReaction(
    private val action: () -> Unit = {},
) : ExecutableReaction {
    var runCount = 0

    override fun onInvalidated() {
        ReactionScheduler.enqueueReaction(this)
    }

    override fun execute() {
        runCount++
        action()
    }

    override fun onSourceRead(source: Source) = Unit
}

@Suppress("unused")
val RuntimeTests by testSuite {
    test("one notification wave queues a executable reaction once") {
        val reaction = CountingExecutableReaction()

        InvalidationDispatcher.notifyInvalidationListeners(listOf(reaction, reaction))

        reaction.runCount.shouldBe(1)
    }

    test("pending reactions can be removed before the queue runs") {
        val pending = CountingExecutableReaction()
        val remover = object : Reaction {
            override fun onInvalidated() {
                ReactionScheduler.removePendingReaction(pending)
            }

            override fun onSourceRead(source: Source) = Unit
        }

        InvalidationDispatcher.notifyInvalidationListeners(listOf(pending, remover))

        pending.runCount.shouldBe(0)
    }

    test("work queued while draining is run by the same drain loop") {
        val second = CountingExecutableReaction()
        val first = CountingExecutableReaction {
            InvalidationDispatcher.notifyInvalidationListeners(listOf(second))
        }

        InvalidationDispatcher.notifyInvalidationListeners(listOf(first))

        first.runCount.shouldBe(1)
        second.runCount.shouldBe(1)
    }

    test("a failing reaction does not prevent later reactions from running") {
        val failure = CountingExecutableReaction { error("reaction failed") }
        val healthy = CountingExecutableReaction()

        shouldThrow<IllegalStateException> {
            InvalidationDispatcher.notifyInvalidationListeners(listOf(failure, healthy))
        }

        failure.runCount.shouldBe(1)
        healthy.runCount.shouldBe(1)
    }

    test("multiple execution failures are retained in execution order after the queue finishes") {
        val firstError = IllegalStateException("first")
        val secondError = IllegalArgumentException("second")
        val first = CountingExecutableReaction { throw firstError }
        val second = CountingExecutableReaction { throw secondError }
        val healthy = CountingExecutableReaction()

        val failure = shouldThrow<ReactionExecutionException> {
            InvalidationDispatcher.notifyInvalidationListeners(listOf(first, second, healthy))
        }

        failure.failures.shouldBe(listOf(firstError, secondError))
        failure.cause.shouldBe(firstError)
        failure.suppressedExceptions.shouldBe(listOf(secondError))
        healthy.runCount.shouldBe(1)

        ReactionScheduler.enqueueReaction(healthy)
        healthy.runCount.shouldBe(2)
    }

}
