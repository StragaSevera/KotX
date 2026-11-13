package ru.ought.kotx.unit

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.ought.kotx.internal.DependencyTracker
import ru.ought.kotx.internal.Reaction
import ru.ought.kotx.internal.Runtime
import ru.ought.kotx.internal.Source

private class ReadableSource : Source() {
    fun read() = onRead()
    fun notifyChanged() = notifySubscribers()
}

private class TrackingReaction : Reaction {
    val tracker = DependencyTracker(this)
    var reactionCount = 0

    override fun react() {
        reactionCount++
    }

    override fun recordRead(source: Source) {
        tracker.recordRead(source)
    }
}

@Suppress("unused")
val DependencyTrackerTests by testSuite {
    test("a new evaluation replaces obsolete dependencies") {
        val reaction = TrackingReaction()
        val first = ReadableSource()
        val second = ReadableSource()

        reaction.tracker.runAsActive { first.read() }
        first.notifyChanged()
        reaction.tracker.runAsActive { second.read() }
        first.notifyChanged()
        reaction.reactionCount.shouldBe(1)

        second.notifyChanged()
        reaction.reactionCount.shouldBe(2)
    }

    test("reads made before a failed evaluation remain subscribed") {
        val reaction = TrackingReaction()
        val source = ReadableSource()

        shouldThrow<IllegalStateException> {
            reaction.tracker.runAsActive {
                source.read()
                error("evaluation failed")
            }
        }
        source.notifyChanged()

        reaction.reactionCount.shouldBe(1)
    }

    test("nested tracking restores the enclosing reaction") {
        val outer = TrackingReaction()
        val inner = TrackingReaction()
        val outerSource = ReadableSource()
        val innerSource = ReadableSource()

        outer.tracker.runAsActive {
            inner.tracker.runAsActive { innerSource.read() }
            outerSource.read()
        }
        innerSource.notifyChanged()
        outerSource.notifyChanged()

        inner.reactionCount.shouldBe(1)
        outer.reactionCount.shouldBe(1)
        Runtime.activeReaction.shouldBe(null)
    }

    test("clearing sources removes every subscription") {
        val reaction = TrackingReaction()
        val source = ReadableSource()
        reaction.tracker.runAsActive { source.read() }

        reaction.tracker.clearSources()
        source.notifyChanged()

        reaction.reactionCount.shouldBe(0)
    }
}
