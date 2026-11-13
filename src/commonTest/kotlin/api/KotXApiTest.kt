package ru.ought.kotx.api

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import ru.ought.kotx.*

@Suppress("unused")
val KotXApiTests by testSuite {
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

        test("closing twice is harmless") {
            val subscription = autorun { }

            subscription.close()
            subscription.close()
        }
    }

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

    test("use closes the autorun when its block throws") {
        var count by observable(0)
        val values = mutableListOf<Int>()
        shouldThrow<IllegalArgumentException> {
            autorun { values.add(count) }.use {
                count = 1
                throw IllegalArgumentException("consumer failed")
            }
        }
        count = 2
        values.shouldContainExactly(listOf(0, 1))
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

    testSuite("terminal reaction error handling") {
        test("failed initial autorun does not remain subscribed") {
            var shouldFail by observable(true)
            var runs = 0
            shouldThrow<IllegalStateException> {
                autorun {
                    runs++
                    check(!shouldFail)
                }
            }

            shouldFail = false

            runs.shouldBe(1)
        }

        test("a new autorun tracks dependencies after an initial autorun fails") {
            var count by observable(0)
            shouldThrow<IllegalStateException> {
                autorun { count; error("failure") }
            }
            var runs = 0
            autorun { count; runs++ }.use {
                count = 2

                runs.shouldBe(2)
            }
        }

        test("default autorun unsubscribes after a later failure") {
            var count by observable(0)
            var runs = 0
            autorun {
                runs++
                check(count != 1)
            }.use {
                count = 1
                count = 2

                runs.shouldBe(2)
            }
        }

        test("failed autorun does not prevent another queued autorun from running") {
            var count by observable(0)
            val failing = autorun { check(count != 1) }
            val values = mutableListOf<Int>()
            val healthy = autorun { values.add(count) }

            count = 1

            values.shouldContainExactly(listOf(0, 1))
            failing.close()
            healthy.close()
        }

        test("null error handler keeps a failed autorun subscribed") {
            var count by observable(0)
            val values = mutableListOf<Int>()
            autorun(onError = null) {
                check(count != 1)
                values.add(count)
            }.use {
                count = 1
                count = 2

                values.shouldContainExactly(listOf(0, 2))
            }
        }

        test("autorun with null error handler recovers after its computed dependency stops failing") {
            var count by observable(0)
            val result by computed { check(count != 1); count * 2 }
            val values = mutableListOf<Int>()
            autorun(onError = null) { values.add(result) }.use {
                count = 1
                count = 2
                values.shouldContainExactly(listOf(0, 4))
            }
        }

        test("initial autorun failure throws without invoking the later-error callback") {
            val errors = mutableListOf<Throwable>()

            shouldThrow<IllegalStateException> {
                autorun(onError = { errors.add(it) }) { error("initial failure") }
            }

            errors.size.shouldBe(0)
        }

        test("explicit callback reports an error and remains subscribed") {
            var count by observable(0)
            val errors = mutableListOf<Throwable>()
            val values = mutableListOf<Int>()

            autorun(onError = { errors.add(it) }) {
                check(count != 1)
                values.add(count)
            }.use {
                count = 1
                count = 2

                errors.size.shouldBe(1)
                values.shouldContainExactly(listOf(0, 2))
            }
        }

        test("global handler reports errors and keeps default autoruns subscribed") {
            var count by observable(0)
            val values = mutableListOf<Int>()
            val errors = mutableListOf<Throwable>()
            val handler = KotXRuntime.installReactionErrorHandler { errors.add(it) }
            val subscription = autorun {
                check(count != 1)
                values.add(count)
            }
            try {
                count = 1
                count = 2

                errors.size.shouldBe(1)
                values.shouldContainExactly(listOf(0, 2))
            } finally {
                subscription.close()
                handler.close()
            }
        }

        test("default autorun unsubscribes on error after the global handler closes") {
            var count by observable(0)
            var runs = 0
            val handler = KotXRuntime.installReactionErrorHandler { }
            val subscription = autorun {
                runs++
                check(count != 1)
            }
            try {
                handler.close()
                count = 1
                count = 2

                runs.shouldBe(2)
            } finally {
                subscription.close()
                handler.close()
            }
        }

        test("ignore policy remains subscribed") {
            var count by observable(0)
            val values = mutableListOf<Int>()

            autorun(onError = ReactionErrorPolicy.Ignore) {
                check(count != 1)
                values.add(count)
            }.use {
                count = 1
                count = 2

                values.shouldContainExactly(listOf(0, 2))
            }
        }

        test("unsubscribe policy bypasses the global handler") {
            var count by observable(0)
            var runs = 0
            val globalErrors = mutableListOf<Throwable>()
            val handler = KotXRuntime.installReactionErrorHandler { globalErrors.add(it) }
            val subscription = autorun(onError = ReactionErrorPolicy.Unsubscribe) {
                runs++
                check(count != 1)
            }
            try {
                count = 1
                count = 2

                runs.shouldBe(2)
                globalErrors.size.shouldBe(0)
            } finally {
                subscription.close()
                handler.close()
            }
        }

        test("autorun created in a handler scope retains that handler") {
            var count by observable(0)
            val errors = mutableListOf<Throwable>()
            val subscription = withReactionErrorHandler({ errors.add(it) }) {
                autorun { check(count != 1) }
            }

            subscription.use {
                count = 1
                count = 2

                errors.size.shouldBe(1)
            }
        }

        test("scoped handler takes precedence over the global handler") {
            var count by observable(0)
            val globalErrors = mutableListOf<Throwable>()
            val scopedErrors = mutableListOf<Throwable>()
            val registration = KotXRuntime.installReactionErrorHandler { globalErrors.add(it) }
            val subscription = withReactionErrorHandler({ scopedErrors.add(it) }) {
                autorun { check(count != 1) }
            }
            try {
                count = 1

                scopedErrors.size.shouldBe(1)
                globalErrors.size.shouldBe(0)
            } finally {
                subscription.close()
                registration.close()
            }
        }

        test("explicit handler takes precedence over scoped and global handlers") {
            var count by observable(0)
            val globalErrors = mutableListOf<Throwable>()
            val scopedErrors = mutableListOf<Throwable>()
            val explicitErrors = mutableListOf<Throwable>()
            val registration = KotXRuntime.installReactionErrorHandler { globalErrors.add(it) }
            val subscription = withReactionErrorHandler({ scopedErrors.add(it) }) {
                autorun(onError = { explicitErrors.add(it) }) { check(count != 1) }
            }
            try {
                count = 1

                explicitErrors.size.shouldBe(1)
                scopedErrors.size.shouldBe(0)
                globalErrors.size.shouldBe(0)
            } finally {
                subscription.close()
                registration.close()
            }
        }

        test("throwing handlers fall back from explicit to scoped to global") {
            var count by observable(0)
            var runs = 0
            val explicitFailure = IllegalArgumentException("explicit handler failed")
            val scopedFailure = IllegalStateException("scoped handler failed")
            val scopedErrors = mutableListOf<Throwable>()
            val globalErrors = mutableListOf<Throwable>()
            val registration = KotXRuntime.installReactionErrorHandler { globalErrors.add(it) }
            val subscription = withReactionErrorHandler({ error ->
                scopedErrors.add(error)
                throw scopedFailure
            }) {
                autorun(onError = { throw explicitFailure }) {
                    runs++
                    check(count != 1)
                }
            }
            try {
                count = 1
                count = 2

                runs.shouldBe(2)
                scopedErrors.shouldContainExactly(listOf(explicitFailure))
                globalErrors.shouldContainExactly(listOf(scopedFailure))
            } finally {
                subscription.close()
                registration.close()
            }
        }

        test("successful scoped handler stops an explicit-handler failure before global") {
            var count by observable(0)
            val explicitFailure = IllegalArgumentException("explicit handler failed")
            val scopedErrors = mutableListOf<Throwable>()
            val globalErrors = mutableListOf<Throwable>()
            val registration = KotXRuntime.installReactionErrorHandler { globalErrors.add(it) }
            val subscription = withReactionErrorHandler({ scopedErrors.add(it) }) {
                autorun(onError = { throw explicitFailure }) { check(count != 1) }
            }
            try {
                count = 1

                scopedErrors.shouldContainExactly(listOf(explicitFailure))
                globalErrors.size.shouldBe(0)
            } finally {
                subscription.close()
                registration.close()
            }
        }

        test("a failing explicit handler unsubscribes and reports its failure globally") {
            var count by observable(0)
            var runs = 0
            val handlerFailure = IllegalArgumentException("handler failed")
            val globalErrors = mutableListOf<Throwable>()
            val registration = KotXRuntime.installReactionErrorHandler { globalErrors.add(it) }
            val subscription = autorun(onError = { throw handlerFailure }) {
                runs++
                check(count != 1)
            }
            try {
                count = 1
                count = 2

                runs.shouldBe(2)
                globalErrors.shouldContainExactly(listOf(handlerFailure))
            } finally {
                subscription.close()
                registration.close()
            }
        }

        test("only one global handler can be installed") {
            val registration = KotXRuntime.installReactionErrorHandler { }
            try {
                shouldThrow<IllegalStateException> {
                    KotXRuntime.installReactionErrorHandler { }
                }
            } finally {
                registration.close()
            }
        }

        test("current global handler exposes its registration until closed") {
            val handlerEffect: (Throwable) -> Unit = { }
            val handler = KotXRuntime.installReactionErrorHandler(handlerEffect)

            KotXRuntime.currentReactionErrorHandler.shouldBe(handler)
            handler.effect.shouldBe(handlerEffect)
            handler.isClosed.shouldBe(false)

            KotXRuntime.currentReactionErrorHandler!!.close()

            handler.isClosed.shouldBe(true)
            KotXRuntime.currentReactionErrorHandler.shouldBe(null)
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

    test("computed can return null") {
        var text by observable<String?>(null)
        val result by computed { text }

        result.shouldBe(null)
    }

    test("unobserved computed reads reflect dependency changes") {
        var text by observable<String?>(null)
        val result by computed { text }

        result.shouldBe(null)
        text = "hello"

        result.shouldBe("hello")
    }

    test("computed dependency cycles throw") {
        class Cycle {
            val first: Int by computed { second }
            val second: Int by computed { first }
        }
        shouldThrow<IllegalStateException> { Cycle().first }
    }

    test("writes inside tracked evaluation fail without changing state") {
        var count by observable(0)
        shouldThrow<IllegalStateException> { autorun { count++ } }
        count.shouldBe(0)
        count = 1
        count.shouldBe(1)
    }
}
