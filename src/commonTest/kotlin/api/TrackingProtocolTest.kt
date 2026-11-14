package ru.ought.kotx.api

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.ought.kotx.*
import ru.ought.kotx.observable.*
import ru.ought.kotx.view.*
import ru.ought.kotx.tracking.*
import ru.ought.kotx.reaction.*
import ru.ought.kotx.computed.*

private class ExternalSource : Source {
    val subscribers = mutableListOf<Reaction>()
    override fun subscribe(reaction: Reaction) {
        if (subscribers.none { it === reaction }) subscribers.add(reaction)
    }
    override fun unsubscribe(reaction: Reaction) {
        subscribers.removeAll { it === reaction }
    }
    fun read() = KotXRuntime.recordRead(this)
    override fun equals(other: Any?) = other is ExternalSource
    override fun hashCode() = 0
}

private class ExternalReaction : Reaction {
    val reads = mutableListOf<Source>()
    var invalidations = 0
    override fun recordRead(source: Source) { reads.add(source) }
    override fun react() { invalidations++ }
    override fun equals(other: Any?) = other is ExternalReaction
    override fun hashCode() = 0
}

@Suppress("unused")
val TrackingProtocolTests by testSuite {
    test("scoped activation routes reads without subscribing") {
        val source = ExternalSource()
        val reaction = ExternalReaction()
        KotXRuntime.withReaction(reaction) { source.read() }
        reaction.reads.size.shouldBe(1)
        (reaction.reads.single() === source).shouldBe(true)
        source.subscribers.size.shouldBe(0)
    }
    test("reads outside tracking do nothing") {
        val source = ExternalSource()
        val reaction = ExternalReaction()
        KotXRuntime.withReaction(reaction) { source.read() }
        source.read()
        reaction.reads.size.shouldBe(1)
    }
    test("nested activation restores the enclosing reaction after failure") {
        val outer = ExternalReaction()
        val inner = ExternalReaction()
        val source = ExternalSource()
        KotXRuntime.withReaction(outer) {
            shouldThrow<IllegalStateException> {
                KotXRuntime.withReaction(inner) {
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
    test("scoped activation returns the block result") {
        KotXRuntime.withReaction(ExternalReaction()) { 42 }.shouldBe(42)
    }
    test("external reactions receive invalidation from core observables") {
        val property = observable(1)
        val reaction = ExternalReaction()
        KotXRuntime.withReaction(reaction) { property.value }
        val source = reaction.reads.single()
        source.subscribe(reaction)
        property.value = 2
        reaction.invalidations.shouldBe(1)
        source.unsubscribe(reaction)
    }
    test("core subscriptions distinguish equal reactions by identity") {
        val property = observable(1)
        val first = ExternalReaction()
        val second = ExternalReaction()
        KotXRuntime.withReaction(first) { property.value }
        val source = first.reads.single()
        source.subscribe(first)
        source.subscribe(first)
        source.subscribe(second)
        source.unsubscribe(first)
        source.unsubscribe(first)
        property.value = 2
        first.invalidations.shouldBe(0)
        second.invalidations.shouldBe(1)
        source.unsubscribe(second)
    }
    test("optional trackers distinguish equal sources and replace dependencies") {
        val first = ExternalSource()
        val second = ExternalSource()
        val reaction = object : Reaction {
            val tracker = DependencyTracker(this)
            override fun recordRead(source: Source) = tracker.recordRead(source)
            override fun react() = Unit
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
}
