# KotX

KotX is an experimental Kotlin Multiplatform state-management library inspired by
MobX. It makes ordinary Kotlin properties observable and discovers dependencies
automatically when Computeds and autoruns read them.

The current library targets JVM and JavaScript. Its public API is intentionally
small and provisional while the reactive semantics are developed and exercised by
real consumers.

## Example

```kotlin
import ru.ought.kotx.autorun
import ru.ought.kotx.computed
import ru.ought.kotx.observable

class Counter {
    var count by observable(0)
    val doubled by computed { count * 2 }
}

val counter = Counter()
val subscription = autorun {
    println("count=${counter.count}, doubled=${counter.doubled}")
}

counter.count = 1
counter.count = 2
subscription.close()
```

The autorun executes immediately, then synchronously after a dependency changes.
Close the returned `AutoCloseable` when the consumer stops, or use `use {}` when a
scope naturally owns the subscription.

## Implemented API

### `observable(initial)`

Creates a delegated mutable property. Reads made during a tracked evaluation
establish a dependency. Assignments notify dependent Computeds and autoruns unless
the new value is equal to the old value under Kotlin's `==`.

```kotlin
var name by observable("Ada")
name = "Grace"
```

### `computed { expression }`

Creates a lazy delegated read-only property. Its dependencies are discovered from
the observable and Computed properties read during each evaluation, so conditional
dependencies can change over time.

```kotlin
var usePrimary by observable(true)
var primary by observable("A")
var secondary by observable("B")

val selected by computed {
    if (usePrimary) primary else secondary
}
```

A Computed caches its result while it has subscribers. When it becomes unobserved,
it releases upstream subscriptions; a later unobserved read evaluates it afresh.

### `autorun { effect }`

Runs an effect immediately and tracks the properties it reads. A relevant change
reruns the effect synchronously. Dependencies that are no longer read are removed
after each execution.

```kotlin
val subscription = autorun {
    render(selected)
}

subscription.close()
```

Closing is idempotent and removes both subscriptions and pending work.

Later execution errors do not escape through the observable assignment that triggered
the run. Without an error handler, the autorun unsubscribes. Pass an explicit handler
to report the error and remain subscribed, or choose a policy:

```kotlin
autorun(onError = { error -> logger.error(error) }) {
    render(selected)
}

autorun(onError = null) { // Ignore later errors and remain subscribed.
    render(selected)
}

autorun(onError = ReactionErrorPolicy.Unsubscribe) {
    render(selected)
}
```

The initial execution remains part of the `autorun` call: if it fails, the call throws
and no subscription is returned.

### Reaction error handlers

Install one application-global handler through `KotXRuntime`. It remains active until
its idempotent registration handle is closed. Installing a second global handler while
one is active fails.

```kotlin
val registration = KotXRuntime.installReactionErrorHandler { error ->
    logger.error(error)
}

KotXRuntime.currentReactionErrorHandler?.close()
```

A default-policy autorun reports later failures to the currently installed global
handler and remains subscribed. If no global handler is installed, it unsubscribes.
Explicit autorun handling takes precedence over the global handler.
If an explicit handler throws, its failure passes to the captured scoped handler and
then to the global handler if each preceding handler also throws. A throwing handler
unsubscribes the autorun.

Use a scoped handler to associate one handler with a group of autoruns. The handler is
captured by default-policy autoruns created synchronously inside the block and remains
associated with them after the block returns:

```kotlin
val subscriptions = withReactionErrorHandler(::reportEditorError) {
    listOf(
        autorun { renderDocument() },
        autorun { renderToolbar() },
    )
}
```

Computed evaluation errors are not handled independently. A direct Computed read
throws to its caller; a read made by an autorun reaches that autorun's error boundary.
Invalidating a Computed does not itself evaluate it or throw.

## Current semantics and limitations

- All KotX state access must currently happen on one thread. The runtime is not yet
  safe for concurrent reads or writes.
- Dependency tracking is synchronous and does not propagate through coroutine
  suspension or launched child jobs.
- Observable writes inside a Computed expression or autorun are rejected with
  `IllegalStateException`.
- Computed values are lazy. Dependency changes invalidate their caches and notify
  downstream consumers; result-equality suppression is not implemented.
- One write invalidates the affected graph before queued autoruns execute, avoiding
  stale reads and duplicate runs through diamond-shaped dependencies.
- Computed dependency cycles fail with `IllegalStateException`.
- An autorun that fails during its initial execution is disposed automatically.
  Later failures never escape through observable assignments. Explicit, scoped, or
  global handling may keep it subscribed; the default without a handler unsubscribes.
- Computed failures are cached while observed until a dependency read before the
  failure changes.
- Nested autoruns have independent lifetimes. Closing an outer autorun does not
  close an autorun created inside it.
- Actions, configurable schedulers, reactions, observable collections, coroutine
  integration, and React integration are not implemented yet.

These are current implementation properties, not compatibility guarantees. The API
and semantics may change before the first stable release.

## Using the library

KotX is not published to a package repository yet. Consume it from source with a
Gradle composite build or include the project directly while development is in
progress. The artifact coordinates are currently:

```kotlin
dependencies {
    implementation("ru.ought.kotx:core:0.1.0-SNAPSHOT")
}
```

## Building and testing

From this directory on macOS or Linux:

```shell
bash gradlew jvmTest jsNodeTest
```

On Windows, use a short project-local temporary directory to avoid Java socket-path
failures:

```powershell
$env:TEMP = Join-Path $PWD '.gradle\tmp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null
.\gradlew.bat jvmTest jsNodeTest
```

Gradle downloads Node.js by default. Pass `-Pkotx.useSystemNode=true` to use the
`node` executable already on `PATH`.

The common test suite runs on both JVM and Node.js. Tests use TestBalloon as the
test framework and Kotest Assertions as the assertion library. Write Kotest
assertions as ordinary parenthesized calls:

```kotlin
actual.shouldBe(expected)
values.shouldContainExactly(expectedValues)
```

Do not use Kotest's infix assertion syntax.

## Source layout

- `src/commonMain/kotlin/KotX.kt` contains the public API.
- `src/commonMain/kotlin/internal/` contains dependency tracking, subscription
  bookkeeping, Computed caching, autorun lifetime, and runtime coordination.
- `src/commonTest/kotlin/api/` tests consumer-visible behavior through only the
  public KotX API.
- `src/commonTest/kotlin/unit/` tests focused invariants of internal components.

Kotlin source files deliberately omit package-directory nesting beneath the source
roots. Files declare the `ru.ought.kotx` package directly.

The wider project goals and planned concurrency model are documented in the
[project brief](../Docs/01.%20Project%20brief.md) in the parent workspace.
