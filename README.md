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

Creates an `ObservableProperty<T>` handle with a mutable `.value` and writable
property delegation. Reads made during a tracked evaluation
establish a dependency. Assignments notify dependent Computeds and autoruns unless
the new value is equal to the old value under Kotlin's `==`.

```kotlin
var name by observable("Ada")
name = "Grace"
```

### `computed { expression }`

Creates a lazy `PropertyView<T>` with a read-only `.value` and property delegation. Its dependencies are discovered from
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

### Views and observable collections

A View provides live read-only access without introducing a Computed. Mutable
handles return a cached protected wrapper from `asView()`; a View returns itself.
The specific interfaces are `PropertyView`, `ListView`, `MapView`, and `SetView`.

```kotlin
class Editor {
    private val writableTitle = observable("Untitled")
    private var titleValue by writableTitle
    val title by writableTitle.asView()

    private val writableTags = observableListOf<String>()
    val tags = writableTags.asView()

    fun rename(title: String) { titleValue = title }
    fun addTag(tag: String) { writableTags.add(tag) }
}
```

`observableListOf`, `observableMapOf`, and `observableSetOf` create standard mutable
collections with whole-collection tracking. Maps and sets preserve insertion order.
Reads through their Views, iterators, and other standard collection operations track
the same source. Mutation through iterators, list ranges, map keys, values, and entries
also notifies observers. Read-only wrappers protect those nested access paths too.
Views are shallow: element fields remain writable and need their own observability.

Bulk member operations such as `addAll`, `removeAll(Collection)`, `retainAll`, `putAll`,
and `clear` notify once after completion. Partial changes before failure remain and
are notified before the exception escapes. Standard library extensions that perform
multiple member calls (for example, predicate removal or sorting) can notify for each
call; there is no public Action API yet.

Equal replacements store the new instance without notifying, for properties, list
slots, and map values. As a deliberate consequence, observers may retain subscriptions
to the old element's observable fields until another change causes reevaluation.
Adding an existing set member or removing a missing item does not notify.

Outside structural changes invalidate iterators and list ranges. Mutations through
a range update its ancestors; sibling ranges can be invalidated. Collection equality
and hashing use current contents, so a View's hash can change with its backing source.
All access still requires a single thread. Writes during tracked evaluation are
rejected even when they would be no-ops.

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

Use `ReactionErrorPolicy.Global` to bypass a receiver scope's handler and use the
current global handler on each later failure. If the global handler is absent or
throws, the autorun unsubscribes. Initial failures still unsubscribe and propagate.

### Reaction error handlers

Set the application-global handler through `KotXRuntime`. Setting another handler
replaces it; `removeGlobalErrorHandler()` removes it. Existing reactions use the current handler.

```kotlin
KotXRuntime.setGlobalErrorHandler(ReactionErrorHandler { error, subscription ->
    logger.error(error)
})
KotXRuntime.removeGlobalErrorHandler()
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

## Experimental tracking protocol

External libraries can implement `SourceReadListener` to receive tracked source reads
and `InvalidationListener` to receive change notifications. `Reaction` combines both
roles. A `Source` registers invalidation listeners by identity with `addListener` and
`removeListener`. Both operations are idempotent. Listeners can call
`subscribe(source)` and `unsubscribe(source)` to delegate to those methods.
Implementers own cleanup. These extension interfaces are experimental and single-threaded.

`KotXRuntime.withReadListener(listener) { ... }` selects the listener for
synchronous calls to `reportRead(source)`, returns the block result, and restores the
enclosing listener even when the block throws. The caller manages subscriptions.

A Reaction can use the optional `DependencyTracker`:

```kotlin
class IntegrationReaction : Reaction, AutoCloseable {
    private val tracker = DependencyTracker(this)
    override fun onSourceRead(source: Source) = tracker.recordDependency(source)
    override fun onInvalidated() { /* Mark the integration's state stale. */ }
    fun <T> track(block: () -> T): T = tracker.runAsActive(block)
    override fun close() = tracker.clearSources()
}
```

The tracker subscribes its owner to the sources the owner reads, deduplicates sources by identity, and removes old
dependencies after each evaluation. Failed evaluations retain reads made before the
failure. Clear subscriptions on disposal. Reentrant use of one tracker is unsupported.
Custom implementations can instead use scoped tracking and manage dependencies themselves.
Tracking does not propagate through coroutine suspension. Observable writes remain
forbidden while a source read listener is active.

`SubscriptionSource` supplies identity-based subscription storage. Subclasses call
`reportRead()` from reads and `notifySubscribers()` after publishing new state. It is a
public building block, as are `StoredObservableProperty` and `ComputedProperty`.

`ExecutableReaction` adds `execute()`. Use `KotXRuntime.notifyInvalidationListeners(...)` to coordinate
invalidation, `enqueueReaction(...)` to request work, and `removePendingReaction(...)`
to cancel it. Requests deduplicate by identity while pending. Notification finishes
before queued work runs; requests outside notification run synchronously. Custom
implementations own disposal and error handling. A failed invalidation does not skip
other subscribers. Runs are drained even after a notification failure; failures escape
to the caller. If notification and draining both fail, the draining failure escapes.

`Autorun.start { ... }` returns a public managed `Autorun`, with the same initialization,
error-policy overloads and disposal rules as `autorun`. Its constructor is private so
initial execution and failed-start cleanup cannot be skipped. Framework-specific
scheduling remains future work.

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
- Actions, configurable schedulers, reactions, coroutine
  integration, and React integration are not implemented yet.

These are current implementation properties, not compatibility guarantees. The API
and semantics may change before the first stable release.

## Using the library

KotX releases are available as Git tags on GitHub. From the consuming project's
root, check out the release you want:

```sh
git clone --branch v0.0.1 --single-branch https://github.com/StragaSevera/KotX.git vendor/kotx
```

Add the checkout as a Gradle composite build in `settings.gradle.kts`:

```kotlin
includeBuild("vendor/kotx")
```

In `build.gradle.kts`:

```kotlin
dependencies {
    implementation("ru.ought.kotx:core:0.0.1")
}
```

In a Kotlin Multiplatform project, put the dependency in
`kotlin.sourceSets.commonMain.dependencies`. The Git checkout determines the
version that Gradle builds. You can also keep that checkout as a Git submodule
to record the exact KotX commit in your project.

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

## Source layout and visibility

Packages follow responsibilities. Beneath `src/commonMain/kotlin/`:

- `observable/`: writable property and collection types, factories, and storage.
- `view/`: live read-only property and collection capabilities and their wrappers.
- `tracking/`: Source, subscription storage, and dependency tracking.
- `reaction/`: Reaction, ExecutableReaction, Autorun, and error policies.
- `computed/`: lazy Computed storage and evaluation.
- `runtime/`: internal singleton coordination and private bookkeeping.

The root package retains `observable`, `computed`, `autorun`, and the `KotXRuntime`
facade. Collection factories are in `ru.ought.kotx.observable`; error handling is in
`ru.ought.kotx.reaction`. Import named types from their responsibility packages.
This is an intentional experimental package migration; old imports are not aliased.
For example:

```kotlin
import ru.ought.kotx.observable.observableListOf
import ru.ought.kotx.reaction.Autorun
import ru.ought.kotx.tracking.SubscriptionSource
```

Reusable primitives are public when they have a coherent contract. Mutable bookkeeping
is private. Collection implementation wrappers and mutation helpers remain internal,
within their responsibility packages. No package is organized solely by visibility.

Package directories omit the shared `ru/ought/kotx` prefix. Common tests cover public
behavior in `api/` and focused runtime invariants in `unit/` on JVM and Node.js.

The wider project goals and planned concurrency model are documented in the
[project brief](../Docs/01.%20Project%20brief.md) in the parent workspace.

Error callbacks can receive the subscription for selective disposal:

```kotlin
autorun(onError = { error, subscription ->
    println(error)
    if (error is IllegalStateException) subscription.close()
}) { println(state.count) }
```

Callbacks that return normally keep the subscription unless they close it.
Throwing callbacks unsubscribe and forward failures through scoped/global fallback
handlers. Initial failures always unsubscribe and escape. Creation helpers pass an
explicit internal context containing the handler retained by each autorun.

`withReactionErrorHandler` supplies a `ReactionScope` receiver. Direct `autorun`
calls in its block use the receiver's helpers and retain its handler; nested scopes
use their own handler. No ambient scoped handler is stored in RuntimeEngine.
Ordinary functions called from the block do not inherit the receiver. To share the
scope with a helper, declare it as a `ReactionScope` extension and call its member
`autorun`. Top-level `autorun` and `Autorun.start` use default global handling.

`ReactionErrorHandler` is the shared callback contract for explicit, scoped, and
global handling. It receives the error and failing subscription, which it may close:

```kotlin
val handler = ReactionErrorHandler { error, subscription ->
    println(error)
    if (error is IllegalStateException) subscription.close()
}
KotXRuntime.setGlobalErrorHandler(handler)
KotXRuntime.removeGlobalErrorHandler() // Removes the handler; does not close reactions.
```


When multiple queued reactions leak execution failures, the runtime finishes the queue
and throws `ReactionExecutionException`. Its `failures` list preserves execution order;
the first failure is the cause and the remaining failures are suppressed exceptions.
A single leaked failure is rethrown directly.


`KotXRuntime` is the public interface for runtime configuration and custom reactive
primitives. Library implementation uses the internal `RuntimeEngine`, which owns
tracking state, notification snapshots, pending execution, and global error handling.
Both entry points use the same operation names and notification semantics.

Concrete collections can also be constructed directly:

```kotlin
val items = ObservableArrayList(listOf("first", "second"))
val counts = ObservableLinkedHashMap(mapOf("first" to 1))
val selected = ObservableLinkedHashSet(setOf("first"))
```

Each class also has an empty constructor. Constructors copy supplied collections;
later changes to the input do not bypass observation. Backing storage and tracking
remain private. The existing collection factories return the common observable
interfaces. Array lists provide indexed storage; linked hash maps and sets preserve
insertion order.
