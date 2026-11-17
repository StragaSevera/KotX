package ru.ought.kotx.api

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import ru.ought.kotx.autorun
import ru.ought.kotx.computed
import ru.ought.kotx.observable

@Suppress("unused")

val ComputedTests by testSuite {
    testSuite("computed evaluation") {
        test("is lazy") {
            var count by observable(1)
            var evaluations = 0
            val doubled by computed { evaluations++; count * 2 }

            evaluations.shouldBe(0)
            doubled.shouldBe(2)
            evaluations.shouldBe(1)
        }

        test("caches repeated reads during an autorun") {
            var count by observable(1)
            var evaluations = 0
            val doubled by computed { evaluations++; count * 2 }
            autorun { doubled + doubled }.use {
                evaluations.shouldBe(1)
            }
        }

        test("reevaluates when an observed dependency changes") {
            var count by observable(1)
            var evaluations = 0
            val doubled by computed { evaluations++; count * 2 }
            val values = mutableListOf<Int>()
            autorun { values.add(doubled) }.use {
                evaluations.shouldBe(1)
                count = 2

                evaluations.shouldBe(2)
                values.shouldContainExactly(listOf(2, 4))
            }
        }

        test("becomes lazy after its last observer closes") {
            var count by observable(1)
            var evaluations = 0
            val doubled by computed { evaluations++; count * 2 }
            val subscription = autorun { doubled }

            evaluations.shouldBe(1)
            subscription.close()
            count = 3

            evaluations.shouldBe(1)
            // Triggering the evaluation
            doubled.shouldBe(6)
            evaluations.shouldBe(2)
        }
    }

    test("unobserved computed reads reflect dependency changes") {
        var text by observable<String?>(null)
        val result by computed { text }

        result.shouldBe(null)
        text = "hello"

        result.shouldBe("hello")
    }

    test("computed can return null") {
        var text by observable<String?>(null)
        val result by computed { text }

        result.shouldBe(null)
    }

    test("two consumers share one cached evaluation") {
        var count by observable(1)
        var evaluations = 0
        val doubled by computed {
            evaluations++
            count * 2
        }
        autorun { doubled }.use {
            autorun { doubled }.use {
                evaluations.shouldBe(1)
                count = 2
                evaluations.shouldBe(2)
            }
        }
    }

    test("closing one consumer preserves the Computed cache and remaining subscription") {
        var count by observable(1)
        var evaluations = 0
        val doubled by computed {
            evaluations++
            count * 2
        }
        val values = mutableListOf<Int>()
        val first = autorun { doubled }
        try {
            autorun { values.add(doubled) }.use {
                first.close()
                doubled.shouldBe(2)
                evaluations.shouldBe(1)
                count = 2
                values.shouldBe(listOf(2, 4))
                evaluations.shouldBe(2)
            }
        } finally {
            first.close()
        }
    }

    test("computed replaces conditional dependencies") {
        var left by observable(1)
        var right by observable(10)
        var useLeft by observable(true)
        val chosen by computed { if (useLeft) left else right }
        val values = mutableListOf<Int>()
        autorun { values.add(chosen) }.use {
            useLeft = false
            left = 2
            right = 11
            values.shouldContainExactly(listOf(1, 10, 11))
        }
    }

    test("direct computed reads propagate errors") {
        var shouldFail by observable(false)
        val result by computed {
            check(!shouldFail)
            1
        }

        result.shouldBe(1)
        shouldFail = true

        shouldThrow<IllegalStateException> { result }
    }

    test("computed dependency cycles throw") {
        class Cycle {
            val first: Int by computed { second }
            val second: Int by computed { first }
        }
        shouldThrow<IllegalStateException> { Cycle().first }
    }

}
