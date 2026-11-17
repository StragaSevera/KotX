package ru.ought.kotx.unit

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import ru.ought.kotx.reaction.Reaction
import ru.ought.kotx.tracking.Source
import ru.ought.kotx.tracking.SubscriptionSource

private class ObservingSource : SubscriptionSource() {
    var unobservedCount = 0

    fun notifyChanged() = notifySubscribers()

    override fun onUnobserved() {
        unobservedCount++
    }
}

private class CountingReaction(private val response: () -> Unit = {}) : Reaction {
    var reactionCount = 0

    override fun onInvalidated() {
        reactionCount++
        response()
    }

    override fun onSourceRead(source: Source) = Unit
}

@Suppress("unused")
val SourceTests by testSuite {
    test("duplicate subscriptions notify once") {
        val source = ObservingSource()
        val subscriber = CountingReaction()

        source.addListener(subscriber)
        source.addListener(subscriber)
        source.notifyChanged()

        subscriber.reactionCount.shouldBe(1)
    }

    test("becoming unobserved is reported only after the last subscriber leaves") {
        val source = ObservingSource()
        val first = CountingReaction()
        val second = CountingReaction()
        source.addListener(first)
        source.addListener(second)

        source.removeListener(first)
        source.unobservedCount.shouldBe(0)
        source.removeListener(second)
        source.unobservedCount.shouldBe(1)
        source.removeListener(second)
        source.unobservedCount.shouldBe(1)
    }

    test("notification traverses a stable subscriber snapshot") {
        val source = ObservingSource()
        val calls = mutableListOf<String>()
        val second = CountingReaction { calls.add("second") }
        val first = CountingReaction {
            calls.add("first")
            source.removeListener(second)
        }
        source.addListener(first)
        source.addListener(second)

        source.notifyChanged()

        calls.shouldContainExactly(listOf("first", "second"))
    }
}
