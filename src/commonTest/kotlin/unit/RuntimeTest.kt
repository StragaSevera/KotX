package ru.ought.kotx.unit

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.ought.kotx.internal.Reaction
import ru.ought.kotx.internal.RunnableReaction
import ru.ought.kotx.internal.Runtime
import ru.ought.kotx.internal.Source

private class CountingRunnableReaction(
    private val action: () -> Unit = {},
) : RunnableReaction {
    var runCount = 0

    override fun react() {
        Runtime.enqueueReaction(this)
    }

    override fun run() {
        runCount++
        action()
    }

    override fun recordRead(source: Source) = Unit
}

@Suppress("unused")
val RuntimeTests by testSuite {
    test("one notification wave queues a runnable reaction once") {
        val reaction = CountingRunnableReaction()

        Runtime.notifyReactions(listOf(reaction, reaction))

        reaction.runCount.shouldBe(1)
    }

    test("a failing reaction does not prevent later reactions from running") {
        val failure = CountingRunnableReaction { error("reaction failed") }
        val healthy = CountingRunnableReaction()

        shouldThrow<IllegalStateException> {
            Runtime.notifyReactions(listOf(failure, healthy))
        }

        failure.runCount.shouldBe(1)
        healthy.runCount.shouldBe(1)
    }

    test("pending reactions can be removed before the queue runs") {
        val pending = CountingRunnableReaction()
        val remover = object : Reaction {
            override fun react() {
                Runtime.removePendingReaction(pending)
            }

            override fun recordRead(source: Source) = Unit
        }

        Runtime.notifyReactions(listOf(pending, remover))

        pending.runCount.shouldBe(0)
    }

    test("work queued while draining is run by the same drain loop") {
        val second = CountingRunnableReaction()
        val first = CountingRunnableReaction {
            Runtime.notifyReactions(listOf(second))
        }

        Runtime.notifyReactions(listOf(first))

        first.runCount.shouldBe(1)
        second.runCount.shouldBe(1)
    }
}
