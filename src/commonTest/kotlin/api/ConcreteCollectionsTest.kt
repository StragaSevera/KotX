package ru.ought.kotx.api

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe
import ru.ought.kotx.observable.ObservableArrayList
import ru.ought.kotx.observable.ObservableLinkedHashMap
import ru.ought.kotx.observable.ObservableLinkedHashSet

@Suppress("unused")
val ConcreteCollectionsTests by testSuite {
    test("array list construction isolates input storage") {
        val input = mutableListOf(1, 2)
        val list = ObservableArrayList(input)
        input.clear()
        list.toList().shouldBe(listOf(1, 2))
        list.subList(0, 1).clear()
        list.toList().shouldBe(listOf(2))
        ObservableArrayList<Int>().size.shouldBe(0)
    }
    test("linked hash map construction isolates input and preserves insertion order") {
        val input = linkedMapOf("b" to 2, "a" to 1)
        val map = ObservableLinkedHashMap(input)
        input.clear()
        map.keys.toList().shouldBe(listOf("b", "a"))
        map["a"].shouldBe(1)
        ObservableLinkedHashMap<String, Int>().size.shouldBe(0)
    }
    test("linked hash set construction isolates input and preserves insertion order") {
        val input = mutableListOf(2, 1, 2)
        val set = ObservableLinkedHashSet(input)
        input.clear()
        set.toList().shouldBe(listOf(2, 1))
        ObservableLinkedHashSet<Int>().size.shouldBe(0)
    }
}
