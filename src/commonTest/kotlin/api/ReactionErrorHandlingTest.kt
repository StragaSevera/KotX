package ru.ought.kotx.api

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import ru.ought.kotx.KotXRuntime
import ru.ought.kotx.autorun
import ru.ought.kotx.computed
import ru.ought.kotx.observable
import ru.ought.kotx.reaction.ReactionErrorPolicy
import ru.ought.kotx.reaction.withReactionErrorHandler

@Suppress("unused")

val ReactionErrorHandlingTests by testSuite {
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
            try {
                var count by observable(0)
                val values = mutableListOf<Int>()
                val errors = mutableListOf<Throwable>()
                KotXRuntime.setGlobalErrorHandler(ru.ought.kotx.reaction.ReactionErrorHandler { error, _ -> errors.add(error) })
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
                    KotXRuntime.removeGlobalErrorHandler()
                }
            } finally {
                KotXRuntime.removeGlobalErrorHandler()
            }
        }

        test("Global policy bypasses the scope and uses the current global handler") {
            try {
                var count by observable(0)
                val scopedErrors = mutableListOf<Throwable>()
                val globalErrors = mutableListOf<Throwable>()
                val subscription = withReactionErrorHandler({ scopedErrors.add(it) }) {
                    autorun(onError = ReactionErrorPolicy.Global) { check(count == 0) }
                }
                try {
                    KotXRuntime.setGlobalErrorHandler { error, _ -> globalErrors.add(error) }
                    count = 1
                    count = 2
                    globalErrors.size.shouldBe(2)
                    scopedErrors.size.shouldBe(0)
                } finally {
                    subscription.close()
                    KotXRuntime.removeGlobalErrorHandler()
                }
            } finally {
                KotXRuntime.removeGlobalErrorHandler()
            }
        }

        test("Global policy unsubscribes without a global handler despite a scope handler") {
            var count by observable(0)
            var runs = 0
            val errors = mutableListOf<Throwable>()
            withReactionErrorHandler({ errors.add(it) }) {
                autorun(onError = ReactionErrorPolicy.Global) {
                    runs++
                    check(count == 0)
                }
            }.use {
                count = 1
                count = 2
                runs.shouldBe(2)
                errors.size.shouldBe(0)
            }
        }

        test("Global policy unsubscribes when the global handler throws") {
            try {
                var count by observable(0)
                var runs = 0
                KotXRuntime.setGlobalErrorHandler { _, _ -> error("handler failure") }
                val subscription = autorun(onError = ReactionErrorPolicy.Global) {
                    runs++
                    check(count == 0)
                }
                try {
                    count = 1
                    count = 2
                    runs.shouldBe(2)
                } finally {
                    subscription.close()
                    KotXRuntime.removeGlobalErrorHandler()
                }
            } finally {
                KotXRuntime.removeGlobalErrorHandler()
            }
        }

        test("default autorun unsubscribes on error after the global handler is removed") {
            try {
                var count by observable(0)
                var runs = 0
                KotXRuntime.setGlobalErrorHandler(ru.ought.kotx.reaction.ReactionErrorHandler { _, _ -> })
                val subscription = autorun {
                    runs++
                    check(count != 1)
                }
                try {
                    KotXRuntime.removeGlobalErrorHandler()
                    count = 1
                    count = 2

                    runs.shouldBe(2)
                } finally {
                    subscription.close()
                    KotXRuntime.removeGlobalErrorHandler()
                }
            } finally {
                KotXRuntime.removeGlobalErrorHandler()
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
            try {
                var count by observable(0)
                var runs = 0
                val globalErrors = mutableListOf<Throwable>()
                KotXRuntime.setGlobalErrorHandler(ru.ought.kotx.reaction.ReactionErrorHandler { error, _ -> globalErrors.add(error) })
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
                    KotXRuntime.removeGlobalErrorHandler()
                }
            } finally {
                KotXRuntime.removeGlobalErrorHandler()
            }
        }

        test("error callback selectively unsubscribes the autorun") {
            var count by observable(0)
            val errors = mutableListOf<Throwable>()
            autorun(onError = { error, subscription ->
                errors.add(error)
                if (count == 2) subscription.close()
            }) {
                check(count == 0)
            }.use {
                count = 1
                count = 2
                count = 3
                errors.size.shouldBe(2)
            }
        }

        test("nested receiver scopes retain their own handlers") {
            var count by observable(0)
            val outerErrors = mutableListOf<Throwable>()
            val innerErrors = mutableListOf<Throwable>()
            val subscriptions = withReactionErrorHandler({ outerErrors.add(it) }) {
                val outer = autorun { check(count == 0) }
                val inner = withReactionErrorHandler({ innerErrors.add(it) }) {
                    autorun { check(count == 0) }
                }
                listOf(outer, inner)
            }
            try {
                count = 1
                outerErrors.size.shouldBe(1)
                innerErrors.size.shouldBe(1)
            } finally {
                subscriptions.forEach { it.close() }
            }
        }

        test("ordinary helpers do not inherit a receiver scope handler") {
            var count by observable(0)
            val errors = mutableListOf<Throwable>()
            fun watch(): AutoCloseable = autorun { check(count == 0) }
            val subscription = withReactionErrorHandler({ errors.add(it) }) { watch() }
            subscription.use {
                count = 1
                errors.size.shouldBe(0)
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
            try {
                var count by observable(0)
                val globalErrors = mutableListOf<Throwable>()
                val scopedErrors = mutableListOf<Throwable>()
                KotXRuntime.setGlobalErrorHandler(ru.ought.kotx.reaction.ReactionErrorHandler { error, _ -> globalErrors.add(error) })
                val subscription = withReactionErrorHandler({ scopedErrors.add(it) }) {
                    autorun { check(count != 1) }
                }
                try {
                    count = 1

                    scopedErrors.size.shouldBe(1)
                    globalErrors.size.shouldBe(0)
                } finally {
                    subscription.close()
                    KotXRuntime.removeGlobalErrorHandler()
                }
            } finally {
                KotXRuntime.removeGlobalErrorHandler()
            }
        }

        test("explicit handler takes precedence over scoped and global handlers") {
            try {
                var count by observable(0)
                val globalErrors = mutableListOf<Throwable>()
                val scopedErrors = mutableListOf<Throwable>()
                val explicitErrors = mutableListOf<Throwable>()
                KotXRuntime.setGlobalErrorHandler(ru.ought.kotx.reaction.ReactionErrorHandler { error, _ -> globalErrors.add(error) })
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
                    KotXRuntime.removeGlobalErrorHandler()
                }
            } finally {
                KotXRuntime.removeGlobalErrorHandler()
            }
        }

        test("throwing handlers fall back from explicit to scoped to global") {
            try {
                var count by observable(0)
                var runs = 0
                val explicitFailure = IllegalArgumentException("explicit handler failed")
                val scopedFailure = IllegalStateException("scoped handler failed")
                val scopedErrors = mutableListOf<Throwable>()
                val globalErrors = mutableListOf<Throwable>()
                KotXRuntime.setGlobalErrorHandler(ru.ought.kotx.reaction.ReactionErrorHandler { error, _ -> globalErrors.add(error) })
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
                    KotXRuntime.removeGlobalErrorHandler()
                }
            } finally {
                KotXRuntime.removeGlobalErrorHandler()
            }
        }

        test("successful scoped handler stops an explicit-handler failure before global") {
            try {
                var count by observable(0)
                val explicitFailure = IllegalArgumentException("explicit handler failed")
                val scopedErrors = mutableListOf<Throwable>()
                val globalErrors = mutableListOf<Throwable>()
                KotXRuntime.setGlobalErrorHandler(ru.ought.kotx.reaction.ReactionErrorHandler { error, _ -> globalErrors.add(error) })
                val subscription = withReactionErrorHandler({ scopedErrors.add(it) }) {
                    autorun(onError = { throw explicitFailure }) { check(count != 1) }
                }
                try {
                    count = 1

                    scopedErrors.shouldContainExactly(listOf(explicitFailure))
                    globalErrors.size.shouldBe(0)
                } finally {
                    subscription.close()
                    KotXRuntime.removeGlobalErrorHandler()
                }
            } finally {
                KotXRuntime.removeGlobalErrorHandler()
            }
        }

        test("a failing explicit handler unsubscribes and reports its failure globally") {
            try {
                var count by observable(0)
                var runs = 0
                val handlerFailure = IllegalArgumentException("handler failed")
                val globalErrors = mutableListOf<Throwable>()
                KotXRuntime.setGlobalErrorHandler(ru.ought.kotx.reaction.ReactionErrorHandler { error, _ -> globalErrors.add(error) })
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
                    KotXRuntime.removeGlobalErrorHandler()
                }
            } finally {
                KotXRuntime.removeGlobalErrorHandler()
            }
        }

        test("replacing the global handler affects existing autoruns") {
            try {
                var count by observable(0)
                val firstErrors = mutableListOf<Throwable>()
                val secondErrors = mutableListOf<Throwable>()
                KotXRuntime.setGlobalErrorHandler(ru.ought.kotx.reaction.ReactionErrorHandler { error, _ -> firstErrors.add(error) })
                val subscription = autorun { check(count == 0) }
                try {
                    count = 1
                    KotXRuntime.setGlobalErrorHandler(ru.ought.kotx.reaction.ReactionErrorHandler { error, _ -> secondErrors.add(error) })
                    count = 2
                    firstErrors.size.shouldBe(1)
                    secondErrors.size.shouldBe(1)
                } finally {
                    subscription.close()
                    KotXRuntime.removeGlobalErrorHandler()
                }
            } finally {
                KotXRuntime.removeGlobalErrorHandler()
            }
        }

        test("one handler selectively unsubscribes scoped and global reactions") {
            try {
                var count by observable(0)
                val errors = mutableListOf<Throwable>()
                val handler = ru.ought.kotx.reaction.ReactionErrorHandler { error, subscription ->
                    errors.add(error)
                    if (count == 2) subscription.close()
                }
                KotXRuntime.setGlobalErrorHandler(handler)
                val global = autorun { check(count == 0) }
                val scoped = withReactionErrorHandler(handler) { autorun { check(count == 0) } }
                try {
                    count = 1
                    count = 2
                    count = 3
                    errors.size.shouldBe(4)
                } finally {
                    scoped.close()
                    global.close()
                    KotXRuntime.removeGlobalErrorHandler()
                }
            } finally {
                KotXRuntime.removeGlobalErrorHandler()
            }
        }

        test("global handler can be set and removed") {
            try {
                val handler = ru.ought.kotx.reaction.ReactionErrorHandler { _, _ -> }
                try {
                    KotXRuntime.setGlobalErrorHandler(handler)
                    KotXRuntime.globalErrorHandler.shouldBe(handler)
                    KotXRuntime.removeGlobalErrorHandler()
                    KotXRuntime.globalErrorHandler.shouldBe(null)
                } finally {
                    KotXRuntime.removeGlobalErrorHandler()
                }
            } finally {
                KotXRuntime.removeGlobalErrorHandler()
            }
        }

    }

}
