package ru.ought.kotx.api

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe
import ru.ought.kotx.autorun
import ru.ought.kotx.observable

@Suppress("unused")

val ObservablePropertyTests by testSuite {
    test("equal property replacement stores the new instance silently") {
        val first = EqualItem(1)
        val source = observable(first)
        var runs = 0
        autorun { source.value; runs++ }.use {
            val second = EqualItem(1)
            source.value = second
            (source.value === second).shouldBe(true)
            runs.shouldBe(1)
        }
    }
}

private data class EqualItem(val id: Int)
