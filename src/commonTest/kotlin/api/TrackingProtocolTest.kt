package ru.ought.kotx.api

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.ought.kotx.KotXRuntime
import ru.ought.kotx.observable
import ru.ought.kotx.reaction.InvalidationListener
import ru.ought.kotx.reaction.Reaction
import ru.ought.kotx.reaction.SourceReadListener
import ru.ought.kotx.tracking.DependencyTracker
import ru.ought.kotx.tracking.Source

private class ExternalSource : Source {
    val subscribers = mutableListOf<InvalidationListener>()
    override fun addListener(listener: InvalidationListener) {
        if (subscribers.none { it === listener }) subscribers.add(listener)
    }
    override fun removeListener(listener: InvalidationListener) {
        subscribers.removeAll { it === listener }
    }
    fun read() = KotXRuntime.reportRead(this)
    override fun equals(other: Any?) = other is ExternalSource
    override fun hashCode() = 0
}

private class ExternalReaction : Reaction {
    val reads = mutableListOf<Source>()
    var invalidations = 0
    override fun onSourceRead(source: Source) { reads.add(source) }
    override fun onInvalidated() { invalidations++ }
    override fun equals(other: Any?) = other is ExternalReaction
    override fun hashCode() = 0
}

@Suppress("unused")
val TrackingProtocolTests by testSuite {
    test("scoped activation routes reads without subscribing") {
        val source = ExternalSource()
        val reads = mutableListOf<Source>()
        val listener = SourceReadListener { reads.add(it) }
        KotXRuntime.withSourceReadListener(listener) { source.read() }
        reads.size.shouldBe(1)
        (reads.single() === source).shouldBe(true)
        source.subscribers.size.shouldBe(0)
    }

    test("scoped activation returns the block result") {
        KotXRuntime.withSourceReadListener(ExternalReaction()) { 42 }.shouldBe(42)
    }

    test("invalidation-only listeners receive notifications from core observables") {
        val property = observable(1)
        val reads = mutableListOf<Source>()
        KotXRuntime.withSourceReadListener({ reads.add(it) }) { property.value }
        val source = reads.single()
        var invalidations = 0
        val listener = InvalidationListener { invalidations++ }
        listener.subscribe(source)
        property.value = 2
        invalidations.shouldBe(1)
        listener.unsubscribe(source)
    }

    test("reads outside tracking do nothing") {
        val source = ExternalSource()
        val reaction = ExternalReaction()
        KotXRuntime.withSourceReadListener(reaction) { source.read() }
        source.read()
        reaction.reads.size.shouldBe(1)
    }

    test("core subscriptions distinguish equal reactions by identity") {
        val property = observable(1)
        val first = ExternalReaction()
        val second = ExternalReaction()
        KotXRuntime.withSourceReadListener(first) { property.value }
        val source = first.reads.single()
        first.subscribe(source)
        first.subscribe(source)
        second.subscribe(source)
        first.unsubscribe(source)
        first.unsubscribe(source)
        property.value = 2
        first.invalidations.shouldBe(0)
        second.invalidations.shouldBe(1)
        second.unsubscribe(source)
    }

    test("optional trackers distinguish equal sources and replace dependencies") {
        val first = ExternalSource()
        val second = ExternalSource()
        val reaction = object : Reaction {
            val tracker = DependencyTracker(this)
            override fun onSourceRead(source: Source) = tracker.recordDependency(source)
            override fun onInvalidated() = Unit
        }
        reaction.tracker.runAsActive {
            first.read()
            first.read()
            second.read()
        }
        first.subscribers.size.shouldBe(1)
        second.subscribers.size.shouldBe(1)
        reaction.tracker.runAsActive { second.read() }
        first.subscribers.size.shouldBe(0)
        reaction.tracker.clearSources()
        second.subscribers.size.shouldBe(0)
    }

    test("nested activation restores the enclosing reaction after failure") {
        val outer = ExternalReaction()
        val inner = ExternalReaction()
        val source = ExternalSource()
        KotXRuntime.withSourceReadListener(outer) {
            shouldThrow<IllegalStateException> {
                KotXRuntime.withSourceReadListener(inner) {
                    source.read()
                    error("failure")
                }
            }
            source.read()
        }
        source.read()
        outer.reads.size.shouldBe(1)
        inner.reads.size.shouldBe(1)
    }

}
