package ru.ought.kotx.api

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.ought.kotx.*

@Suppress("unused")
val CollectionsTests by testSuite {
    testSuite("list") {
        test("empty view reads observe later additions") {
            val items = observableListOf<Int>()
            val view = items.asView()
            val sizes = mutableListOf<Int>()
            autorun { sizes.add(view.size) }.use {
                items.add(1)
                sizes.shouldBe(listOf(0, 1))
            }
        }
        test("bulk operations publish only their completed contents") {
            val items = observableListOf(1)
            val values = mutableListOf<List<Int>>()
            autorun { values.add(items.toList()) }.use {
                items.addAll(listOf(2, 3))
                items.removeAll(listOf(1, 3))
                items.clear()
                values.shouldBe(listOf(listOf(1), listOf(1, 2, 3), listOf(2), emptyList()))
            }
        }
        test("equal replacement changes identity without notification") {
            val first = EqualCollectionItem(1)
            val items = observableListOf(first)
            var runs = 0
            autorun { items[0]; runs++ }.use {
                val second = EqualCollectionItem(1)
                items[0] = second
                (items[0] === second).shouldBe(true)
                runs.shouldBe(1)
            }
        }
        test("list iterators support tracked add set and remove") {
            val items = observableListOf(1, 2)
            val values = mutableListOf<List<Int>>()
            autorun { values.add(items.toList()) }.use {
                val iterator = items.listIterator()
                iterator.next()
                iterator.set(3)
                iterator.add(4)
                iterator.previous()
                iterator.remove()
                values.shouldBe(listOf(listOf(1, 2), listOf(3, 2), listOf(3, 4, 2), listOf(3, 2)))
            }
        }
        test("nested ranges mutate the parent and preserve ancestor ranges") {
            val items = observableListOf(0, 1, 2, 3)
            val range = items.subList(1, 4)
            val nested = range.subList(1, 2)
            val sizes = mutableListOf<Int>()
            autorun { sizes.add(items.size) }.use {
                nested.clear()
                range.toList().shouldBe(listOf(1, 3))
                sizes.shouldBe(listOf(4, 3))
            }
        }
        test("outside structural changes invalidate ranges and iterators") {
            val items = observableListOf(1, 2)
            val range = items.subList(0, 1)
            val iterator = items.iterator()
            items.add(3)
            shouldThrow<ConcurrentModificationException> { range.size }
            shouldThrow<ConcurrentModificationException> { iterator.hasNext() }
        }
        test("failed bulk removal publishes completed changes") {
            val items = observableListOf(1, 2, 3)
            val values = mutableListOf<List<Int>>()
            val membership = object : AbstractCollection<Int>() {
                override val size = 1
                override fun iterator() = listOf(1).iterator()
                override fun contains(element: Int): Boolean {
                    if (element == 2) error("membership failure")
                    return element == 1
                }
            }
            autorun { values.add(items.toList()) }.use {
                shouldThrow<IllegalStateException> { items.removeAll(membership) }
                values.shouldBe(listOf(listOf(1, 2, 3), listOf(2, 3)))
            }
        }
        test("read-only lists protect their iterators and nested ranges") {
            val source = observableListOf(1)
            val view = source.asView()
            (view is MutableList<*>).shouldBe(false)
            (view.iterator() is MutableIterator<*>).shouldBe(false)
            (view.listIterator() is MutableListIterator<*>).shouldBe(false)
            (view.subList(0, 1) is MutableList<*>).shouldBe(false)
            (view === source.asView()).shouldBe(true)
        }
        test("plain element mutation does not invalidate membership observers") {
            class Item(var text: String)
            val item = Item("a")
            val items = observableListOf(item)
            var runs = 0
            autorun { items[0].text; runs++ }.use {
                item.text = "b"
                runs.shouldBe(1)
            }
        }
        test("observable element fields remain writable through a view") {
            class Item { var text by observable("a") }
            val items = observableListOf(Item())
            val view = items.asView()
            val values = mutableListOf<String>()
            autorun { values.add(view[0].text) }.use {
                view[0].text = "b"
                values.shouldBe(listOf("a", "b"))
            }
        }
    }
    testSuite("map") {
        test("absent key reads use whole-map tracking") {
            val items = observableMapOf<String, Int>()
            val values = mutableListOf<Int?>()
            autorun { values.add(items.asView()["a"]) }.use {
                items["b"] = 1
                items["a"] = 2
                values.shouldBe(listOf(null, null, 2))
            }
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
        test("map bulk operations notify once") {
            val items = observableMapOf<String, Int>()
            val sizes = mutableListOf<Int>()
            autorun { sizes.add(items.size) }.use {
                items.putAll(mapOf("a" to 1, "b" to 2, "c" to 3))
                items.keys.removeAll(listOf("a", "b"))
                sizes.shouldBe(listOf(0, 3, 1))
            }
        }
        test("equal map replacements retain the new instance without notification") {
            val items = observableMapOf("a" to EqualCollectionItem(1))
            var runs = 0
            autorun { items["a"]; runs++ }.use {
                val second = EqualCollectionItem(1)
                items["a"] = second
                (items["a"] === second).shouldBe(true)
                val third = EqualCollectionItem(1)
                items.entries.first().setValue(third)
                (items["a"] === third).shouldBe(true)
                runs.shouldBe(1)
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
        test("map iteration preserves insertion order after reinsertion") {
            val items = observableMapOf("a" to 1, "b" to 2)
            items.remove("a")
            items["a"] = 3
            items.keys.toList().shouldBe(listOf("b", "a"))
        }
    }
    testSuite("set") {
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
        test("set iteration preserves insertion order after reinsertion") {
            val items = observableSetOf(1, 2)
            items.remove(1)
            items.add(1)
            items.toList().shouldBe(listOf(2, 1))
        }
        test("set iterators reject outside changes") {
            val items = observableSetOf(1)
            val iterator = items.iterator()
            items.add(2)
            shouldThrow<ConcurrentModificationException> { iterator.hasNext() }
        }
        test("read-only sets protect iteration") {
            val source = observableSetOf(1)
            val view = source.asView()
            (view is MutableSet<*>).shouldBe(false)
            (view.iterator() is MutableIterator<*>).shouldBe(false)
            (view === source.asView()).shouldBe(true)
        }
    }
    test("standard reads of empty collections establish dependencies") {
        val reads: List<(Collection<Int>) -> Any> = listOf(
            { it.isEmpty() }, { it.iterator().hasNext() }, { it.hashCode() },
            { it.toString() }, { it == listOf(1) },
        )
        for (read in reads) {
            val items = observableListOf<Int>()
            var runs = 0
            autorun { read(items.asView()); runs++ }.use {
                items.add(1)
                runs.shouldBe(2)
            }
        }
    }
    test("collection subscriptions are released after disposal") {
        val items = observableListOf(1)
        var runs = 0
        val subscription = autorun { items.asView().size; runs++ }
        subscription.close()
        items.add(2)
        runs.shouldBe(1)
    }
    test("dynamic collection dependencies stop following the old collection") {
        val first = observableListOf(1)
        val second = observableListOf(2)
        var useFirst by observable(true)
        val values = mutableListOf<Int>()
        autorun { values.add(if (useFirst) first.size else second.size) }.use {
            useFirst = false
            first.add(3)
            second.add(4)
            values.shouldBe(listOf(1, 1, 2))
        }
    }
    test("tracked writes reject no-ops through each mutable collection path") {
        val list = observableListOf(1)
        val map = observableMapOf("a" to 1)
        val set = observableSetOf(1)
        val writes: List<() -> Unit> = listOf(
            { list[0] = 1 }, { list.addAll(emptyList()) }, { list.remove(9) },
            { list.subList(0, 0).clear() }, { list.listIterator().add(2) },
            { map["a"] = 1 }, { map.putAll(emptyMap()) }, { map.keys.remove("missing") },
            { map.entries.first().setValue(1) }, { map.values.remove(9) },
            { set.add(1) }, { set.removeAll(emptyList()) },
        )
        for (write in writes) shouldThrow<IllegalStateException> { autorun { write() } }
    }
    test("collection views compare and hash by contents") {
        val list = observableListOf(1, 2).asView()
        val map = observableMapOf("a" to 1).asView()
        val set = observableSetOf(1, 2).asView()
        (list == listOf(1, 2)).shouldBe(true)
        list.hashCode().shouldBe(listOf(1, 2).hashCode())
        (map == mapOf("a" to 1)).shouldBe(true)
        map.hashCode().shouldBe(mapOf("a" to 1).hashCode())
        (set == setOf(2, 1)).shouldBe(true)
        set.hashCode().shouldBe(setOf(2, 1).hashCode())
    }
}

private data class EqualCollectionItem(val id: Int)
