package ru.ought.kotx.api

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.ought.kotx.observable.ObservableArrayList
import ru.ought.kotx.autorun
import ru.ought.kotx.observable
import ru.ought.kotx.observable.observableListOf

private data class EqualListItem(val id: Int)

@Suppress("unused")
val ObservableListTests by testSuite {
    test("adding an item triggers autorun") {
        val items = observableListOf<Int>()
        val values = mutableListOf<List<Int>>()
        autorun { values.add(items.toList()) }.use {
            items.add(1)

            values.shouldBe(listOf(emptyList(), listOf(1)))
        }
    }

    test("replacing an item triggers autorun") {
        val items = observableListOf(1)
        val values = mutableListOf<List<Int>>()
        autorun { values.add(items.toList()) }.use {
            items[0] = 2

            values.shouldBe(listOf(listOf(1), listOf(2)))
        }
    }

    test("removing an item triggers autorun") {
        val items = observableListOf(1)
        val values = mutableListOf<List<Int>>()
        autorun { values.add(items.toList()) }.use {
            items.remove(1)

            values.shouldBe(listOf(listOf(1), emptyList()))
        }
    }

    test("indexed insertion triggers autorun") {
        val items = observableListOf(1)
        val values = mutableListOf<List<Int>>()
        autorun { values.add(items.toList()) }.use {
            items.add(0, 2)
            values.shouldBe(listOf(listOf(1), listOf(2, 1)))
        }
    }

    test("indexed removal triggers autorun") {
        val items = observableListOf(1)
        val values = mutableListOf<List<Int>>()
        autorun { values.add(items.toList()) }.use {
            items.removeAt(0)
            values.shouldBe(listOf(listOf(1), emptyList()))
        }
    }

    test("using methods of the collection establishes dependencies") {
        val items = observableListOf<Int>()
        val values = mutableListOf<Boolean>()
        autorun { values.add(items.contains(1)) }.use {
            items.add(1)
            values.shouldBe(listOf(false, true))
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
        val first = EqualListItem(1)
        val items = observableListOf(first)
        var runs = 0
        autorun { items[0]; runs++ }.use {
            val second = EqualListItem(1)
            items[0] = second
            (items[0] === second).shouldBe(true)
            runs.shouldBe(1)
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

    test("empty view reads observe later additions") {
        val items = observableListOf<Int>()
        val view = items.asView()
        val sizes = mutableListOf<Int>()
        autorun { sizes.add(view.size) }.use {
            items.add(1)
            sizes.shouldBe(listOf(0, 1))
        }
    }

    test("element property changes notify element observers without notifying collection observers") {
        class Item {
            var text by observable("first")
        }
        val item = Item()
        val items = observableListOf(item)
        var collectionRuns = 0
        val texts = mutableListOf<String>()
        val collectionObserver = autorun {
            items.toList()
            collectionRuns++
        }
        val elementObserver = autorun { texts.add(items[0].text) }
        try {
            item.text = "second"

            collectionRuns.shouldBe(1)
            texts.shouldBe(listOf("first", "second"))
        } finally {
            elementObserver.close()
            collectionObserver.close()
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

    test("read-only lists protect their iterators and nested ranges") {
        val source = observableListOf(1)
        val view = source.asView()
        (view is MutableList<*>).shouldBe(false)
        (view.iterator() is MutableIterator<*>).shouldBe(false)
        (view.listIterator() is MutableListIterator<*>).shouldBe(false)
        (view.subList(0, 1) is MutableList<*>).shouldBe(false)
        (view === source.asView()).shouldBe(true)
    }

    test("read-only view compares by contents") {
        val view = observableListOf(1, 2).asView()

        (view == listOf(1, 2)).shouldBe(true)
    }

    test("read-only view hashes by contents") {
        val view = observableListOf(1, 2).asView()

        view.hashCode().shouldBe(listOf(1, 2).hashCode())
    }

    test("array list construction isolates input storage") {
        val input = mutableListOf(1, 2)
        val list = ObservableArrayList(input)
        input.clear()
        list.toList().shouldBe(listOf(1, 2))
        list.subList(0, 1).clear()
        list.toList().shouldBe(listOf(2))
        ObservableArrayList<Int>().size.shouldBe(0)
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

    test("clearing an empty collection does not trigger autorun") {
        val items = observableListOf<Int>()
        var runs = 0
        autorun { items.size; runs++ }.use {
            items.clear()
            runs.shouldBe(1)
        }
    }

    test("removing an absent item does not trigger autorun") {
        val items = observableListOf(1)
        var runs = 0
        autorun { items.size; runs++ }.use {
            items.remove(9)
            runs.shouldBe(1)
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

    testSuite("writes during tracked evaluation") {
        val illegalOperations: List<Pair<String, (MutableList<Int>) -> Unit>> = listOf(
            "equal indexed replacement" to { items -> items[0] = 1 },
            "empty bulk addition" to { items -> items.addAll(emptyList()) },
            "absent element removal" to { items -> items.remove(9) },
            "empty range clearing" to { items -> items.subList(0, 0).clear() },
            "iterator addition" to { items -> items.listIterator().add(2) },
        )

        for ((name, operation) in illegalOperations) {
            test("rejects $name") {
                val items = observableListOf(1)

                shouldThrow<IllegalStateException> {
                    autorun { operation(items) }
                }
            }
        }
    }

}
