package ru.ought.kotx.api

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.ought.kotx.KotXRuntime
import ru.ought.kotx.reaction.Autorun
import ru.ought.kotx.reaction.Reaction
import ru.ought.kotx.reaction.RunnableReaction
import ru.ought.kotx.tracking.Source
import ru.ought.kotx.tracking.SubscriptionSource

private class PublishedSource : SubscriptionSource() {
    var value = 0
        private set
    val hasSubscribers: Boolean get() = observed
    fun read(): Int {
        onRead()
        return value
    }
    fun write(value: Int) {
        this.value = value
        notifySubscribers()
    }
}

private class PublicRunnable : RunnableReaction {
    var runs = 0
    override fun recordRead(source: Source) = Unit
    override fun react() = KotXRuntime.enqueueReaction(this)
    override fun run() { runs++ }
    override fun equals(other: Any?) = other is PublicRunnable
    override fun hashCode() = 0
}

@Suppress("unused")
val ReactionPrimitivesTests by testSuite {
    test("public autorun construction observes a custom managed source") {
        val source = PublishedSource()
        val values = mutableListOf<Int>()
        Autorun.start { values.add(source.read()) }.use {
            source.write(1)
            values.shouldBe(listOf(0, 1))
        }
        source.hasSubscribers.shouldBe(false)
    }
    test("failed public autorun construction releases subscriptions") {
        val source = PublishedSource()
        shouldThrow<IllegalStateException> {
            Autorun.start {
                source.read()
                error("initial failure")
            }
        }
        source.hasSubscribers.shouldBe(false)
    }
    test("public queue distinguishes equal runnable reactions by identity") {
        val first = PublicRunnable()
        val second = PublicRunnable()
        KotXRuntime.notifyReactions(listOf(first, first, second))
        first.runs.shouldBe(1)
        second.runs.shouldBe(1)
    }
    test("public cancellation removes queued work before execution") {
        val pending = PublicRunnable()
        val canceller = object : Reaction {
            override fun recordRead(source: Source) = Unit
            override fun react() = KotXRuntime.removePendingReaction(pending)
        }
        KotXRuntime.notifyReactions(listOf(pending, canceller))
        pending.runs.shouldBe(0)
    }
    test("all invalidations complete before public runnable work starts") {
        val events = mutableListOf<String>()
        val runnable = object : RunnableReaction {
            override fun recordRead(source: Source) = Unit
            override fun react() {
                events.add("first invalidated")
                KotXRuntime.enqueueReaction(this)
            }
            override fun run() { events.add("run") }
        }
        val second = object : Reaction {
            override fun recordRead(source: Source) = Unit
            override fun react() { events.add("second invalidated") }
        }
        KotXRuntime.notifyReactions(listOf(runnable, second))
        events.shouldBe(listOf("first invalidated", "second invalidated", "run"))
    }
    test("custom invalidation failure does not skip remaining subscribers") {
        val healthy = PublicRunnable()
        val failing = object : Reaction {
            override fun recordRead(source: Source) = Unit
            override fun react() { error("invalidation failure") }
        }
        shouldThrow<IllegalStateException> {
            KotXRuntime.notifyReactions(listOf(failing, healthy))
        }
        healthy.runs.shouldBe(1)
    }
    test("public queue executes immediately outside notification") {
        val reaction = PublicRunnable()
        KotXRuntime.enqueueReaction(reaction)
        reaction.runs.shouldBe(1)
    }
}
