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

}
