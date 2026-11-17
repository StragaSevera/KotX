package ru.ought.kotx.api

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import ru.ought.kotx.autorun
import ru.ought.kotx.observable

@Suppress("unused")
val AutorunTests by testSuite {
    testSuite("autorun lifecycle") {
        test("runs once when created") {
            var count by observable(1)
            val values = mutableListOf<Int>()
            autorun { values.add(count) }.use {
                values.shouldContainExactly(listOf(1))
            }
        }

        test("suppresses runs for equal writes") {
            var count by observable(1)
            val values = mutableListOf<Int>()
            autorun { values.add(count) }.use {
                count = 1
                count = 2

                values.shouldContainExactly(listOf(1, 2))
            }
        }

        test("stops running after close") {
            var count by observable(1)
            val values = mutableListOf<Int>()
            val subscription = autorun { values.add(count) }

            subscription.close()
            count = 2

            values.shouldContainExactly(listOf(1))
            count.shouldBe(2)
        }

        test("autorun can unsubscribe itself during a later execution") {
            var count by observable(0)
            val values = mutableListOf<Int>()
            lateinit var subscription: AutoCloseable
            subscription = autorun {
                values.add(count)
                if (count == 2) subscription.close()
            }
            try {
                count = 1
                count = 2
                count = 3

                values.shouldContainExactly(listOf(0, 1, 2))
            } finally {
                subscription.close()
            }
        }

        test("closing twice leaves the autorun unsubscribed") {
            var count by observable(1)
            val values = mutableListOf<Int>()
            val subscription = autorun { values.add(count) }

            subscription.close()
            subscription.close()
            count = 2

            values.shouldContainExactly(listOf(1))
        }
    }

    test("changes to either dependency trigger autorun") {
        var first by observable(1)
        var second by observable(10)
        val values = mutableListOf<Int>()
        autorun { values.add(first + second) }.use {
            first = 2
            second = 20
            values.shouldBe(listOf(11, 12, 22))
        }
    }

    test("repeated reads produce only one execution per change") {
        var count by observable(1)
        val values = mutableListOf<Int>()
        autorun { values.add(count + count) }.use {
            count = 2
            values.shouldBe(listOf(2, 4))
        }
    }

    test("autorun replaces conditional dependencies") {
        var enabled by observable(true)
        var count by observable(0)
        val values = mutableListOf<Int>()
        autorun { values.add(if (enabled) count else -1) }.use {
            enabled = false
            count = 1
            values.shouldContainExactly(listOf(0, -1))
        }
    }

    test("closing one subscriber leaves another subscriber active") {
        var count by observable(0)
        val firstValues = mutableListOf<Int>()
        val secondValues = mutableListOf<Int>()
        val first = autorun { firstValues.add(count) }
        lateinit var second: AutoCloseable
        try {
            second = autorun { secondValues.add(count) }
            count = 1
            first.close()
            count = 2
            firstValues.shouldBe(listOf(0, 1))
            secondValues.shouldBe(listOf(0, 1, 2))
        } finally {
            first.close()
            second.close()
        }
    }

    testSuite("nested autoruns") {
        test("nested autorun tracks its own dependencies") {
            var inner by observable(0)
            val values = mutableListOf<Int>()
            var nested: AutoCloseable? = null
            val outer = autorun {
                if (nested == null) nested = autorun { values.add(inner) }
            }

            inner = 1

            values.shouldContainExactly(listOf(0, 1))
            outer.close()
            nested!!.close()
        }

        test("nested autorun restores enclosing tracking") {
            var outerValue by observable(0)
            var innerValue by observable(0)
            val values = mutableListOf<Int>()
            var nested: AutoCloseable? = null
            val outer = autorun {
                if (nested == null) nested = autorun { innerValue }
                values.add(outerValue)
            }

            innerValue = 1
            outerValue = 1

            values.shouldContainExactly(listOf(0, 1))
            outer.close()
            nested!!.close()
        }

        test("closing the enclosing autorun does not close the nested autorun") {
            var inner by observable(0)
            val values = mutableListOf<Int>()
            var nested: AutoCloseable? = null
            val outer = autorun {
                if (nested == null) nested = autorun { values.add(inner) }
            }

            outer.close()
            inner = 1

            values.shouldContainExactly(listOf(0, 1))
            nested!!.close()
        }
    }

    test("an autorun with no initial Sources does not react to later changes") {
        var enabled = false
        var count by observable(0)
        var runs = 0
        autorun {
            runs++
            if (enabled) count
        }.use {
            enabled = true
            count = 1
            runs.shouldBe(1)
        }
    }

    test("an execution with no Sources removes all previous dependencies") {
        var enabled = true
        var count by observable(0)
        var runs = 0
        autorun {
            runs++
            if (enabled) count
        }.use {
            enabled = false
            count = 1
            runs.shouldBe(2)
            enabled = true
            count = 2
            runs.shouldBe(2)
        }
    }

}
