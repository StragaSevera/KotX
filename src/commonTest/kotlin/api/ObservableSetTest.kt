package ru.ought.kotx.api

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.ought.kotx.observable.ObservableLinkedHashSet
import ru.ought.kotx.autorun
import ru.ought.kotx.observable
import ru.ought.kotx.observable.observableSetOf

@Suppress("unused")
val ObservableSetTests by testSuite {
    test("adding an item triggers autorun") {
        val items = observableSetOf<Int>()
        val values = mutableListOf<Set<Int>>()
        autorun { values.add(items.toSet()) }.use {
            items.add(1)

            values.shouldBe(listOf(emptySet(), setOf(1)))
        }
    }

    test("removing an item triggers autorun") {
        val items = observableSetOf(1)
        val values = mutableListOf<Set<Int>>()
        autorun { values.add(items.toSet()) }.use {
            items.remove(1)

            values.shouldBe(listOf(setOf(1), emptySet()))
        }
    }

    test("clearing triggers autorun") {
        val items = observableSetOf(1)
        val values = mutableListOf<Set<Int>>()
        autorun { values.add(items.toSet()) }.use {
            items.clear()
            values.shouldBe(listOf(setOf(1), emptySet()))
        }
    }

    test("membership reads establish dependencies") {
        val items = observableSetOf<Int>()
        val values = mutableListOf<Boolean>()
        autorun { values.add(items.contains(1)) }.use {
            items.add(1)
            values.shouldBe(listOf(false, true))
        }
    }

    test("set bulk operations batch notifications and suppress no-ops") {
        val items = observableSetOf(1)
        val values = mutableListOf<List<Int>>()
        autorun { values.add(items.toList()) }.use {
            items.add(1)
            items.remove(9)
            items.addAll(listOf(2, 3))
            items.retainAll(listOf(3))
            values.shouldBe(listOf(listOf(1), listOf(1, 2, 3), listOf(3)))
        }
    }

    test("a retained View stays live and reports reads") {
        val items = observableSetOf<Int>()
        val view = items.asView()
        val values = mutableListOf<Set<Int>>()
        autorun { values.add(view.toSet()) }.use {
            items.add(1)
            view.toSet().shouldBe(setOf(1))
            values.shouldBe(listOf(emptySet(), setOf(1)))
        }
    }

    test("element property changes notify element observers without notifying collection observers") {
        class Item {
            var text by observable("first")
        }
        val item = Item()
        val items = observableSetOf(item)
        var collectionRuns = 0
        val texts = mutableListOf<String>()
        val collectionObserver = autorun {
            items.toSet()
            collectionRuns++
        }
        val elementObserver = autorun { texts.add(items.first().text) }
        try {
            item.text = "second"

            collectionRuns.shouldBe(1)
            texts.shouldBe(listOf("first", "second"))
        } finally {
            elementObserver.close()
            collectionObserver.close()
        }
    }

    test("read-only sets protect iteration") {
        val source = observableSetOf(1)
        val view = source.asView()
        (view is MutableSet<*>).shouldBe(false)
        (view.iterator() is MutableIterator<*>).shouldBe(false)
        (view === source.asView()).shouldBe(true)
    }

    test("read-only view compares by contents") {
        val view = observableSetOf(1, 2).asView()

        (view == setOf(2, 1)).shouldBe(true)
    }

    test("read-only view hashes by contents") {
        val view = observableSetOf(1, 2).asView()

        view.hashCode().shouldBe(setOf(2, 1).hashCode())
    }

    test("linked hash set construction isolates input and preserves insertion order") {
        val input = mutableListOf(2, 1, 2)
        val set = ObservableLinkedHashSet(input)
        input.clear()
        set.toList().shouldBe(listOf(2, 1))
        ObservableLinkedHashSet<Int>().size.shouldBe(0)
    }

    test("iterator removal triggers autorun") {
        val items = observableSetOf(1)
        val values = mutableListOf<Set<Int>>()
        autorun { values.add(items.toSet()) }.use {
            val iterator = items.iterator()
            iterator.next()
            iterator.remove()
            values.shouldBe(listOf(setOf(1), emptySet()))
        }
    }

    test("set iteration preserves insertion order after reinsertion") {
        val items = observableSetOf(1, 2)
        items.remove(1)
        items.add(1)
        items.toList().shouldBe(listOf(2, 1))
    }

    test("clearing an empty collection does not trigger autorun") {
        val items = observableSetOf<Int>()
        var runs = 0
        autorun { items.size; runs++ }.use {
            items.clear()
            runs.shouldBe(1)
        }
    }

    test("set iterators reject outside changes") {
        val items = observableSetOf(1)
        val iterator = items.iterator()
        items.add(2)
        shouldThrow<ConcurrentModificationException> { iterator.hasNext() }
    }

    testSuite("writes during tracked evaluation") {
        val illegalOperations: List<Pair<String, (MutableSet<Int>) -> Unit>> = listOf(
            "duplicate addition" to { items -> items.add(1) },
            "empty bulk removal" to { items -> items.removeAll(emptyList()) },
        )

        for ((name, operation) in illegalOperations) {
            test("rejects $name") {
                val items = observableSetOf(1)

                shouldThrow<IllegalStateException> {
                    autorun { operation(items) }
                }
            }
        }
    }

}
