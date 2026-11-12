# KotX console prototype

An experimental, single-threaded Kotlin reactive state library. The core targets JVM
and JavaScript; the console example runs on JVM. React integration is the next
experiment and is not included here yet.

```kotlin
import ru.ought.kotx.autorun
import ru.ought.kotx.computed
import ru.ought.kotx.observable

class Counter {
    var count by observable(0L)
    var delta by observable(1L)
    val nextCount by computed { count + delta }
}

val state = Counter()
val subscription = autorun {
    println("${state.count}: delta=${state.delta}, next=${state.nextCount}")
}
state.delta = 3L
state.count = state.nextCount
subscription.close()
```

`autorun` returns standard `AutoCloseable`. Call `close()` repeatedly safely, or
use `autorun { ... }.use { ... }` to close automatically when a scope ends.

## Run on Windows

Use a JDK supported by Gradle (this prototype is verified with JDK 26).
From `Examples/KotXConsoleExample`, set a short process-local temporary directory
before Gradle:

```powershell
$env:TEMP = Join-Path (Resolve-Path '../..') '.gradle\tmp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null
.\gradlew.bat run --console=plain
```

The program starts at count 0 and delta 1. Every five seconds it adds delta to count.
Enter a signed 64-bit integer to change delta; count changes only on a tick.
Invalid input preserves state. Enter `quit` or send end-of-input to exit. The app
stops ticking, disposes the autorun, and prints one explicit final summary.
Input is read on a daemon thread and queued; all KotX operations run on the main
event loop. A blocked terminal reader may remain until the process exits.
Arithmetic that would overflow the count or next count is rejected or skipped.

On macOS/Linux, use `./gradlew` with the equivalent tasks.

## Check

From `KotX`, run the library tests:

```powershell
$env:TEMP = Join-Path $PWD '.gradle\tmp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null
.\gradlew.bat jvmTest jsNodeTest
```

JVM and Node run the same common core tests. The browser target is configured for
the future React consumer; no browser tests or React adapter are implemented yet.
Gradle downloads Node by default. To use Node already on PATH, add
`'-Pkotx.useSystemNode=true'` to the PowerShell command. JS dependencies use npm.

From `Examples/KotXConsoleExample`, use the TEMP/TMP setup above for that
directory and run `.\gradlew.bat test installDist`.

## Prototype semantics and limits

- `observable` uses Kotlin structural equality (`==`) to suppress equal assignments.
- `autorun` executes immediately and after each relevant write. Dependency sets
  follow the reads of the latest execution, including conditional branches.
- Computed values are lazy and cache results while observed. Unobserved reads
  evaluate afresh and release subscriptions. Disposal releases computed chains
  that no longer have consumers.
- One write invalidates the whole dependent graph before effects run, avoiding
  stale computed reads and duplicate runs through diamond dependencies.
- Computed invalidation reruns consumers even when the resulting value is equal;
  computed result equality suppression is not implemented.
- Reads must be synchronous. Use one thread for every operation across all state
  in this runtime. Cross-thread use, coroutine tracking, scheduling configuration,
  actions, observable collections, and `reaction` are not implemented.
- Observable writes inside computed expressions or autoruns throw. Perform state
  changes in ordinary application/event-handler code.
- Computed cycles throw `IllegalStateException`. Errors propagate to the caller.
  An autorun that fails on its initial run is disposed automatically. Later errors
  keep subscriptions to reads made before the error; other queued autoruns still
  execute, then the first error is rethrown. Computed errors are cached while
  observed until a dependency changes. Recovery requires changing a dependency
  that was actually read before the error.
- Dispose every successful autorun when its consumer stops. Nested autoruns have
  independent lifetimes; disposing the outer autorun does not dispose the inner.

The public API is provisional. The broader concurrency and collection goals remain
future work, not guarantees of this prototype.

## Build layout

- `KotX/`: standalone Kotlin Multiplatform library build with JVM and JS targets.
- `Examples/KotXConsoleExample/`: standalone JVM application build. It uses
  `includeBuild("../../KotX")` to resolve `ru.ought.kotx:core:0.1.0-SNAPSHOT`
  from the local library source.

Kotlin files omit the shared package directories: for example,
`KotX/src/commonMain/kotlin/KotX.kt` declares `package ru.ought.kotx`.
That file contains the public factories. The `internal/` directory separates
observable storage, computed caching, autorun lifetime, dependency collection,
source subscriptions, and runtime coordination into focused files. These classes
have Kotlin `internal` visibility and are outside the public API.

Build configuration follows the official [Kotlin Multiplatform setup](https://kotlinlang.org/docs/multiplatform/multiplatform-dsl-reference.html)
and [Kotlin/JS test setup](https://kotlinlang.org/docs/js-running-tests.html).
