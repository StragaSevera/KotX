package ru.ought.kotx.api

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.ought.kotx.observable.ObservableLinkedHashMap
import ru.ought.kotx.autorun
import ru.ought.kotx.observable
import ru.ought.kotx.observable.observableMapOf

@Suppress("unused")
val ObservableMapTests by testSuite {
    test("adding a key triggers autorun") {
        val items = observableMapOf<String, Int>()
        val values = mutableListOf<Map<String, Int>>()
        autorun { values.add(items.toMap()) }.use {
            items["a"] = 1

            values.shouldBe(listOf(emptyMap(), mapOf("a" to 1)))
        }
    }

    test("replacing a value triggers autorun") {
        val items = observableMapOf("a" to 1)
        val values = mutableListOf<Map<String, Int>>()
        autorun { values.add(items.toMap()) }.use {
            items["a"] = 2

            values.shouldBe(listOf(mapOf("a" to 1), mapOf("a" to 2)))
        }
    }

    test("removing a key triggers autorun") {
        val items = observableMapOf("a" to 1)
        val values = mutableListOf<Map<String, Int>>()
        autorun { values.add(items.toMap()) }.use {
            items.remove("a")

            values.shouldBe(listOf(mapOf("a" to 1), emptyMap()))
        }
    }

    test("clearing triggers autorun") {
        val items = observableMapOf("a" to 1)
        val values = mutableListOf<Map<String, Int>>()
        autorun { values.add(items.toMap()) }.use {
            items.clear()
            values.shouldBe(listOf(mapOf("a" to 1), emptyMap()))
        }
    }

    test("key membership reads establish dependencies") {
        val items = observableMapOf<String, Int>()
        val values = mutableListOf<Boolean>()
        autorun { values.add(items.containsKey("a")) }.use {
            items["a"] = 1
            values.shouldBe(listOf(false, true))
        }
    }

    test("value membership reads establish dependencies") {
        val items = observableMapOf<String, Int>()
        val values = mutableListOf<Boolean>()
        autorun { values.add(items.containsValue(1)) }.use {
            items["a"] = 1
            values.shouldBe(listOf(false, true))
        }
    }

    test("map bulk operations notify once") {
        val items = observableMapOf<String, Int>()
        val sizes = mutableListOf<Int>()
        autorun { sizes.add(items.size) }.use {
            items.putAll(mapOf("a" to 1, "b" to 2, "c" to 3))
            items.keys.removeAll(listOf("a", "b"))
            sizes.shouldBe(listOf(0, 3, 1))
        }
    }

    test("absent key reads use whole-map tracking") {
        val items = observableMapOf<String, Int>()
        val values = mutableListOf<Int?>()
        autorun { values.add(items.asView()["a"]) }.use {
            items["b"] = 1
            items["a"] = 2
            values.shouldBe(listOf(null, null, 2))
        }
    }

    test("equal map replacements retain the new instance without notification") {
        val items = observableMapOf("a" to EqualMapItem(1))
        var runs = 0
        autorun { items["a"]; runs++ }.use {
            val second = EqualMapItem(1)
            items["a"] = second
            (items["a"] === second).shouldBe(true)
            val third = EqualMapItem(1)
            items.entries.first().setValue(third)
            (items["a"] === third).shouldBe(true)
            runs.shouldBe(1)
        }
    }

    test("a retained View stays live and reports reads") {
        val items = observableMapOf<String, Int>()
        val view = items.asView()
        val values = mutableListOf<Map<String, Int>>()
        autorun { values.add(view.toMap()) }.use {
            items["a"] = 1
            view.toMap().shouldBe(mapOf("a" to 1))
            values.shouldBe(listOf(emptyMap(), mapOf("a" to 1)))
        }
    }

    test("element property changes notify element observers without notifying collection observers") {
        class Item {
            var text by observable("first")
        }
        val item = Item()
        val items = observableMapOf("a" to item)
        var collectionRuns = 0
        val texts = mutableListOf<String>()
        val collectionObserver = autorun {
            items.toMap()
            collectionRuns++
        }
        val elementObserver = autorun { texts.add(items.getValue("a").text) }
        try {
            item.text = "second"

            collectionRuns.shouldBe(1)
            texts.shouldBe(listOf("first", "second"))
        } finally {
            elementObserver.close()
            collectionObserver.close()
        }
    }

    test("read-only maps protect entries keys values and iterators") {
        val items = observableMapOf("a" to 1)
        val view = items.asView()
        (view is MutableMap<*, *>).shouldBe(false)
        (view.entries is MutableSet<*>).shouldBe(false)
        (view.entries.first() is MutableMap.MutableEntry<*, *>).shouldBe(false)
        (view.keys is MutableSet<*>).shouldBe(false)
        (view.values is MutableCollection<*>).shouldBe(false)
        (view.entries.iterator() is MutableIterator<*>).shouldBe(false)
        (view === items.asView()).shouldBe(true)
    }

    test("read-only view compares by contents") {
        val view = observableMapOf("a" to 1).asView()

        (view == mapOf("a" to 1)).shouldBe(true)
    }

    test("read-only view hashes by contents") {
        val view = observableMapOf("a" to 1).asView()

        view.hashCode().shouldBe(mapOf("a" to 1).hashCode())
    }

    test("linked hash map construction isolates input and preserves insertion order") {
        val input = linkedMapOf("b" to 2, "a" to 1)
        val map = ObservableLinkedHashMap(input)
        input.clear()
        map.keys.toList().shouldBe(listOf("b", "a"))
        map["a"].shouldBe(1)
        ObservableLinkedHashMap<String, Int>().size.shouldBe(0)
    }

    test("map mutations through entries keys and values notify") {
        val items = observableMapOf("a" to 1, "b" to 2, "c" to 3)
        val values = mutableListOf<Map<String, Int>>()
        autorun { values.add(items.toMap()) }.use {
            items.entries.first().setValue(4)
            items.keys.remove("b")
            items.values.remove(3)
            val iterator = items.entries.iterator()
            iterator.next()
            iterator.remove()
            values.shouldBe(listOf(mapOf("a" to 1, "b" to 2, "c" to 3), mapOf("a" to 4, "b" to 2, "c" to 3), mapOf("a" to 4, "c" to 3), mapOf("a" to 4), emptyMap()))
        }
    }

    test("map iteration preserves insertion order after reinsertion") {
        val items = observableMapOf("a" to 1, "b" to 2)
        items.remove("a")
        items["a"] = 3
        items.keys.toList().shouldBe(listOf("b", "a"))
    }

    test("removing an absent key does not trigger autorun") {
        val items = observableMapOf("a" to 1)
        var runs = 0
        autorun { items.size; runs++ }.use {
            items.remove("missing")
            runs.shouldBe(1)
        }
    }

    test("clearing an empty collection does not trigger autorun") {
        val items = observableMapOf<String, Int>()
        var runs = 0
        autorun { items.size; runs++ }.use {
            items.clear()
            runs.shouldBe(1)
        }
    }

    testSuite("writes during tracked evaluation") {
        val illegalOperations: List<Pair<String, (MutableMap<String, Int>) -> Unit>> = listOf(
            "equal key replacement" to { items -> items["a"] = 1 },
            "empty bulk insertion" to { items -> items.putAll(emptyMap()) },
            "absent key removal" to { items -> items.keys.remove("missing") },
            "equal entry replacement" to { items -> items.entries.first().setValue(1) },
            "absent value removal" to { items -> items.values.remove(9) },
        )

        for ((name, operation) in illegalOperations) {
            test("rejects $name") {
                val items = observableMapOf("a" to 1)

                shouldThrow<IllegalStateException> {
                    autorun { operation(items) }
                }
            }
        }
    }

}

private data class EqualMapItem(val id: Int)
