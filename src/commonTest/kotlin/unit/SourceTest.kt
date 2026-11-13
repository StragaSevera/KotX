package ru.ought.kotx.unit

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import ru.ought.kotx.internal.Reaction
import ru.ought.kotx.internal.Source

private class ObservingSource : Source() {
    var unobservedCount = 0

    fun notifyChanged() = notifySubscribers()

    override fun onUnobserved() {
        unobservedCount++
    }
}

private class CountingReaction(private val response: () -> Unit = {}) : Reaction {
    var reactionCount = 0

    override fun react() {
        reactionCount++
        response()
    }

    override fun recordRead(source: Source) = Unit
}

@Suppress("unused")
val SourceTests by testSuite {
    test("duplicate subscriptions notify once") {
        val source = ObservingSource()
        val subscriber = CountingReaction()

        source.subscribe(subscriber)
        source.subscribe(subscriber)
        source.notifyChanged()

        subscriber.reactionCount.shouldBe(1)
    }

    test("becoming unobserved is reported only after the last subscriber leaves") {
        val source = ObservingSource()
        val first = CountingReaction()
        val second = CountingReaction()
        source.subscribe(first)
        source.subscribe(second)

        source.unsubscribe(first)
        source.unobservedCount.shouldBe(0)
        source.unsubscribe(second)
        source.unobservedCount.shouldBe(1)
        source.unsubscribe(second)
        source.unobservedCount.shouldBe(1)
    }

    test("notification traverses a stable subscriber snapshot") {
        val source = ObservingSource()
        val calls = mutableListOf<String>()
        val second = CountingReaction { calls.add("second") }
        val first = CountingReaction {
            calls.add("first")
            source.unsubscribe(second)
        }
        source.subscribe(first)
        source.subscribe(second)

        source.notifyChanged()

        calls.shouldContainExactly(listOf("first", "second"))
    }
}
