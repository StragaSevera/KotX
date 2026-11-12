package ru.ought.kotx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KotXTest {
    @Test fun useClosesSubscriptionsEvenWhenItsBodyThrows() {
        var count by observable(0)
        val values = mutableListOf<Int>()
        assertFailsWith<IllegalArgumentException> {
            autorun { values.add(count) }.use {
                count = 1
                throw IllegalArgumentException("consumer failed")
            }
        }
        count = 2
        assertEquals(listOf(0, 1), values)
    }

    @Test fun observableRunsImmediatelySuppressesEqualWritesAndDisposes() {
        var count by observable(0)
        val values = mutableListOf<Int>()
        val subscription = autorun { values.add(count) }
        count = 0
        count = 1
        subscription.close()
        subscription.close()
        count = 2
        assertEquals(listOf(0, 1), values)
        assertEquals(2, count)
    }

    @Test fun computedIsLazyAndCachesWhileObserved() {
        var count by observable(1)
        var evaluations = 0
        val doubled by computed { evaluations++; count * 2 }
        assertEquals(0, evaluations)
        val values = mutableListOf<Int>()
        val subscription = autorun { values.add(doubled + doubled) }
        assertEquals(1, evaluations)
        count = 2
        assertEquals(2, evaluations)
        assertEquals(listOf(4, 8), values)
        subscription.close()
        count = 3
        assertEquals(2, evaluations)
        assertEquals(6, doubled)
        assertEquals(3, evaluations)
    }

    @Test fun dynamicDependenciesAreReplaced() {
        var left by observable(1)
        var right by observable(10)
        var useLeft by observable(true)
        val chosen by computed { if (useLeft) left else right }
        val values = mutableListOf<Int>()
        val subscription = autorun { values.add(chosen) }
        useLeft = false
        left = 2
        right = 11
        assertEquals(listOf(1, 10, 11), values)
        subscription.close()
    }

    @Test fun directAutorunDependenciesAreReplaced() {
        var enabled by observable(true)
        var count by observable(0)
        val values = mutableListOf<Int>()
        val subscription = autorun { values.add(if (enabled) count else -1) }
        enabled = false
        count = 1
        assertEquals(listOf(0, -1), values)
        subscription.close()
    }

    @Test fun directAndDiamondDependenciesProduceOneFreshResult() {
        var count by observable(1)
        val doubled by computed { count * 2 }
        val tripled by computed { count * 3 }
        val sum by computed { doubled + tripled }
        val values = mutableListOf<String>()
        // Direct source read subscribes the reaction before the computed graph.
        val subscription = autorun { values.add("$count/$sum/$doubled") }
        count = 2
        assertEquals(listOf("1/5/2", "2/10/4"), values)
        subscription.close()
    }

    @Test fun nestedAutorunRestoresEnclosingTracking() {
        var outer by observable(0)
        var inner by observable(0)
        val values = mutableListOf<Int>()
        var nested: AutoCloseable? = null
        val subscription = autorun {
            if (nested == null) nested = autorun { inner }
            values.add(outer)
        }
        inner = 1
        outer = 1
        assertEquals(listOf(0, 1), values)
        subscription.close()
        nested!!.close()
    }

    @Test fun failedInitialAutorunDoesNotLeakAndTrackingIsRestored() {
        var count by observable(0)
        var runs = 0
        assertFailsWith<IllegalStateException> {
            autorun { count; runs++; error("failure") }
        }
        count = 1
        assertEquals(1, runs)
        val subscription = autorun { count; runs++ }
        count = 2
        assertEquals(3, runs)
        subscription.close()
    }

    @Test fun failedReactionDoesNotPreventOtherReactionsAndCanRecover() {
        var count by observable(0)
        val failing = autorun { check(count != 1) }
        val values = mutableListOf<Int>()
        val healthy = autorun { values.add(count) }
        assertFailsWith<IllegalStateException> { count = 1 }
        count = 2
        assertEquals(listOf(0, 1, 2), values)
        failing.close()
        healthy.close()
    }

    @Test fun computedErrorsRecoverWhenTheirDependenciesChange() {
        var count by observable(0)
        val result by computed { check(count != 1); count * 2 }
        val values = mutableListOf<Int>()
        val subscription = autorun { values.add(result) }
        assertFailsWith<IllegalStateException> { count = 1 }
        count = 2
        assertEquals(listOf(0, 4), values)
        subscription.close()
    }

    @Test fun nullableComputedAndUnobservedReadsWork() {
        var text by observable<String?>(null)
        val result by computed { text }
        assertEquals(null, result)
        text = "hello"
        assertEquals("hello", result)
    }

    @Test fun computedCyclesFailClearly() {
        class Cycle {
            val first: Int by computed { second }
            val second: Int by computed { first }
        }
        assertFailsWith<IllegalStateException> { Cycle().first }
    }

    @Test fun writesInsideTrackedEvaluationFailWithoutChangingState() {
        var count by observable(0)
        assertFailsWith<IllegalStateException> { autorun { count++ } }
        assertEquals(0, count)
        count = 1
        assertEquals(1, count)
    }
}
