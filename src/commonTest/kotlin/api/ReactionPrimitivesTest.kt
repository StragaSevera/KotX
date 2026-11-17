package ru.ought.kotx.api

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.ought.kotx.KotXRuntime
import ru.ought.kotx.reaction.Autorun
import ru.ought.kotx.reaction.Reaction
import ru.ought.kotx.reaction.ExecutableReaction
import ru.ought.kotx.tracking.Source
import ru.ought.kotx.tracking.SubscriptionSource

private class PublishedSource : SubscriptionSource() {
    var value = 0
        private set
    val hasSubscribers: Boolean get() = observed
    fun read(): Int {
        reportRead()
        return value
    }
    fun write(value: Int) {
        this.value = value
        notifySubscribers()
    }
}

private class PublicExecutable : ExecutableReaction {
    var runs = 0
    override fun onSourceRead(source: Source) = Unit
    override fun onInvalidated() = KotXRuntime.enqueueReaction(this)
    override fun execute() { runs++ }
    override fun equals(other: Any?) = other is PublicExecutable
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

    test("public queue executes immediately outside notification") {
        val reaction = PublicExecutable()
        KotXRuntime.enqueueReaction(reaction)
        reaction.runs.shouldBe(1)
    }

    test("all invalidations complete before public executable work starts") {
        val events = mutableListOf<String>()
        val executable = object : ExecutableReaction {
            override fun onSourceRead(source: Source) = Unit
            override fun onInvalidated() {
                events.add("first invalidated")
                KotXRuntime.enqueueReaction(this)
            }
            override fun execute() { events.add("execute") }
        }
        val second = object : Reaction {
            override fun onSourceRead(source: Source) = Unit
            override fun onInvalidated() { events.add("second invalidated") }
        }
        KotXRuntime.notifyInvalidationListeners(listOf(executable, second))
        events.shouldBe(listOf("first invalidated", "second invalidated", "execute"))
    }

    test("public queue distinguishes equal executable reactions by identity") {
        val first = PublicExecutable()
        val second = PublicExecutable()
        KotXRuntime.notifyInvalidationListeners(listOf(first, first, second))
        first.runs.shouldBe(1)
        second.runs.shouldBe(1)
    }

    test("public cancellation removes queued work before execution") {
        val pending = PublicExecutable()
        val canceller = object : Reaction {
            override fun onSourceRead(source: Source) = Unit
            override fun onInvalidated() = KotXRuntime.removePendingReaction(pending)
        }
        KotXRuntime.notifyInvalidationListeners(listOf(pending, canceller))
        pending.runs.shouldBe(0)
    }

    test("public notifications retain their snapshot when a callback changes the input") {
        val reactions = mutableListOf<Reaction>()
        val events = mutableListOf<String>()
        val first = object : Reaction {
            override fun onSourceRead(source: Source) = Unit
            override fun onInvalidated() {
                events.add("first")
                reactions.clear()
            }
        }
        val second = object : Reaction {
            override fun onSourceRead(source: Source) = Unit
            override fun onInvalidated() { events.add("second") }
        }
        reactions.addAll(listOf(first, second))

        KotXRuntime.notifyInvalidationListeners(reactions)

        events.shouldBe(listOf("first", "second"))
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

    test("custom invalidation failure does not skip remaining subscribers") {
        val healthy = PublicExecutable()
        val failing = object : Reaction {
            override fun onSourceRead(source: Source) = Unit
            override fun onInvalidated() { error("invalidation failure") }
        }
        shouldThrow<IllegalStateException> {
            KotXRuntime.notifyInvalidationListeners(listOf(failing, healthy))
        }
        healthy.runs.shouldBe(1)
    }

}
