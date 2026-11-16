package ru.ought.kotx.api

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import ru.ought.kotx.autorun
import ru.ought.kotx.computed
import ru.ought.kotx.observable

@Suppress("unused")

val ReactiveGraphTests by testSuite {
    test("autorun runs once with fresh values through direct and diamond dependencies") {
        var count by observable(1)
        val doubled by computed { count * 2 }
        val tripled by computed { count * 3 }
        val sum by computed { doubled + tripled }
        val values = mutableListOf<String>()
        // Direct source read subscribes the reaction before the computed graph.
        autorun { values.add("$count/$doubled/$sum") }.use {
            count = 2
            values.shouldContainExactly(listOf("1/2/5", "2/4/10"))
        }
    }

    test("writes inside tracked evaluation fail without changing state") {
        var count by observable(0)
        shouldThrow<IllegalStateException> { autorun { count++ } }
        count.shouldBe(0)
        count = 1
        count.shouldBe(1)
    }
}
