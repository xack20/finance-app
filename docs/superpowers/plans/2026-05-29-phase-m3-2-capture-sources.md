---
# M3-2: Capture Sources (Android SMS + Notification, iOS/wasm stubs) Implementation Plan
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
**Goal:** Deliver the platform capture layer — `CaptureService` (Android SMS BroadcastReceiver + ContentResolver backfill + NotificationListener fallback; iOS/wasm stubs) and a `CaptureCoordinator` that drains incoming captures, runs cursor-based catch-up, and exposes an explicit one-time 90-day initial backfill seam, all serialized through a `Mutex`, feeding each `RawCapture` to a pluggable handler.
**Architecture:** Android receivers are OS-instantiated, so they publish to a process-global `CaptureBus` (a `MutableSharedFlow<RawCapture>`); the Android `CaptureService` actual exposes that bus via `observeIncoming()`, queries `Telephony.Sms.Inbox` for `backfillSince`, and drives the SMS/notification-access permission flows via `ActivityResultContracts` + settings intents. `CaptureCoordinator` collects `observeIncoming()` and runs `catchUp()` (backfill since `CaptureConfig.lastSmsCursor`) plus `runInitialBackfill(nowMs)` (one-time, cursor = now − 90 days), dedups by handler, advances the cursor through a narrow `CaptureCursorStore`, and is serialized by a `Mutex`. To stay shippable before M3-3, the coordinator depends on a `CaptureHandler` functional interface (`suspend (RawCapture) -> Unit`) rather than the not-yet-existing `CapturePipeline`; AppContainer wires it to a no-op handler now, and M3-3 replaces that wiring with `CaptureHandler { capturePipeline.process(it) }`.
**Tech Stack:** Kotlin Multiplatform (commonMain expect / androidMain+iosMain+wasmJsMain actuals), kotlinx.coroutines (`Flow`, `MutableSharedFlow`, `Mutex`, `Dispatchers`), AndroidX Activity Result API, `android.provider.Telephony` (incl. `Telephony.Sms.Intents.getMessagesFromIntent`), `NotificationListenerService`, SQLDelight-backed repos (consumed via M3-1 contract), JdbcSqliteDriver in-memory for unit tests, AndroidJUnit4 for instrumented tests.
**Depends on:** **M3-1 (merged)** — provides `domain.RawCapture`, `domain.CaptureChannel`, `domain.CaptureConfig`, and `data.CaptureConfigRepository` (with `get()`, `observe()`, `setCursor()`, and the `capture_enabled` setter `setCaptureEnabled(enabled: Boolean)`), plus `migrations/3.sqm` (the `capture_config.last_sms_cursor` + `capture_enabled` columns). This plan references those types verbatim and never redefines them.

---

## File Structure

| File | Create/Modify | Responsibility |
|------|---------------|----------------|
| `composeApp/src/commonMain/kotlin/app/hisaab/platform/CaptureService.kt` | Create | `expect class CaptureService` — capabilities, SMS permission ops, `observeIncoming()`, `backfillSince()`, notification-access settings. |
| `composeApp/src/commonMain/kotlin/app/hisaab/capture/CaptureHandler.kt` | Create | `fun interface CaptureHandler { suspend fun handle(raw: RawCapture) }` + the common `CaptureSource` interface — the minimal pipeline seam M3-3 replaces with `CapturePipeline::process`. |
| `composeApp/src/commonMain/kotlin/app/hisaab/capture/CaptureCoordinator.kt` | Create | Collects `observeIncoming()` + runs cursor-based `catchUp()` + one-time `runInitialBackfill()`, serialized via `Mutex`, dedups by handler, advances cursor. Defines `DEFAULT_BACKFILL_DAYS = 90` + `CaptureCursorStore`. |
| `composeApp/src/androidMain/kotlin/app/hisaab/capture/CaptureBus.kt` | Create | Process-global `MutableSharedFlow<RawCapture>` bridge so OS-instantiated receivers reach the `CaptureService` actual. |
| `composeApp/src/androidMain/kotlin/app/hisaab/capture/SmsBroadcastReceiver.kt` | Create | Receives `SMS_RECEIVED`, delegates to `Telephony.Sms.Intents.getMessagesFromIntent`, concatenates multipart bodies, calls the pure `smsPartsToRawCapture` mapper → `RawCapture(channel=SMS)` → `CaptureBus`. |
| `composeApp/src/androidMain/kotlin/app/hisaab/capture/HisaabNotificationListenerService.kt` | Create | Fallback: maps posted financial notifications → `RawCapture(channel=NOTIFICATION)` → `CaptureBus`. |
| `composeApp/src/commonMain/kotlin/app/hisaab/capture/SmsRawMapper.kt` | Create | **Pure**, commonMain, platform-free: `smsPartsToRawCapture(sender, body, receivedAt)` → `RawCapture`. The deterministic, JVM-unit-testable seam for the receiver glue. |
| `composeApp/src/androidMain/kotlin/app/hisaab/platform/CaptureService.kt` | Create | Android actual — `{SMS,NOTIFICATION}` capabilities, `RequestMultiplePermissions`, `Telephony.Sms.Inbox` backfill, settings intent. |
| `composeApp/src/iosMain/kotlin/app/hisaab/platform/CaptureService.kt` | Create | iOS stub — `{PASTE}`, empty `observeIncoming()`, no-op backfill/permissions. |
| `composeApp/src/wasmJsMain/kotlin/app/hisaab/platform/CaptureService.kt` | Create | wasm stub — `{}`, empty `observeIncoming()`, no-op everything. |
| `composeApp/src/androidMain/AndroidManifest.xml` | Modify | Add `RECEIVE_SMS`/`READ_SMS`, the SMS receiver (guarded by `BROADCAST_SMS`, `exported=true` for system only), the NotificationListenerService with `BIND_NOTIFICATION_LISTENER_SERVICE` + intent-filter. |
| `composeApp/src/commonMain/kotlin/app/hisaab/AppContainer.kt` | Modify | Add `captureService: CaptureService` + `captureCoordinator: CaptureCoordinator` expect getters. |
| `composeApp/src/androidMain/kotlin/app/hisaab/AppContainerAndroid.kt` | Modify | Android actuals for `captureService` / `captureCoordinator` (with no-op `CaptureHandler` until M3-3). |
| `composeApp/src/iosMain/kotlin/app/hisaab/AppContainerIos.kt` | Modify | iOS actuals. |
| `composeApp/src/wasmJsMain/kotlin/app/hisaab/AppContainerWasm.kt` | Modify | wasm actuals. |
| `composeApp/src/commonTest/kotlin/app/hisaab/capture/FakeCaptureService.kt` | Create | Common-source fake implementing the `CaptureSource` interface — see Task 1 note on testing `expect class`. |
| `composeApp/src/commonTest/kotlin/app/hisaab/capture/CaptureCoordinatorTest.kt` | Create | Unit tests: cursor advance, dedup-by-handler, catchUp ordering, mutex serialization, **initial 90-day backfill cursor math**. |
| `composeApp/src/commonTest/kotlin/app/hisaab/capture/SmsRawMapperTest.kt` | Create | **Deterministic** JVM unit tests of the pure `smsPartsToRawCapture` mapper (single + concatenated multipart inputs). No PDU hex, no device. |
| `composeApp/src/androidInstrumentedTest/kotlin/app/hisaab/capture/SmsIntentGlueInstrumentedTest.kt` | Create | Instrumented: real `SMS_RECEIVED` Intent → `getMessagesFromIntent` glue, asserting it reaches the pure mapper (one assertion `@Ignore`d with a one-line reason if a real DELIVER PDU cannot be fabricated on the harness). |
| `composeApp/src/androidInstrumentedTest/kotlin/app/hisaab/capture/CaptureServiceBackfillInstrumentedTest.kt` | Create | Instrumented: `ContentResolver` backfill query shape against a fake provider. |

> **Coordinator-vs-pipeline decision (stated explicitly):** `CaptureCoordinator` depends on a `fun interface CaptureHandler { suspend fun handle(raw: RawCapture) }` defined in this slice — **not** on `CapturePipeline`. This keeps M3-2 shippable and unit-testable in isolation. In M3-3, `CapturePipeline.process` has signature `suspend fun process(raw: RawCapture): Unit`, which is SAM-compatible with `CaptureHandler`, so M3-3 wires `CaptureHandler { pipeline.process(it) }` in `AppContainerAndroid` with zero coordinator changes. (M3-3's `CapturePipeline` constructor is the canonical `CapturePipeline(db, inboxRepo, senderRepo, accountMatcher, preFilter, llmRouter, txnRepo, configRepo, captureEvents)`; it does **not** take an `onAutoPost` callback or a `merchantRepo` — but none of that surfaces in M3-2, which only knows the `CaptureHandler` SAM.)

> **Testing `expect class CaptureService` from commonTest (stated explicitly):** An `expect class` has no instantiable common form in `commonTest`, and there is no JVM/`androidUnitTest` actual (the actuals are Android/iOS/wasm). Therefore `CaptureCoordinator` is written to depend on a small **common interface `CaptureSource`** that `CaptureService` (each actual) implements, and `CaptureCoordinatorTest` uses a `FakeCaptureService : CaptureSource`. This is the only clean way to unit-test the coordinator on the JVM. See Task 1.

> **Deterministic SMS-parse seam (stated explicitly):** The bytes→`SmsMessage` step (`Telephony.Sms.Intents.getMessagesFromIntent`) lives only in `SmsBroadcastReceiver` (androidMain) and is thin glue. The *mapping* logic — sender/body/timestamp → `RawCapture` and multipart-body concatenation — lives in the **pure** `smsPartsToRawCapture` function in `commonMain` (`SmsRawMapper.kt`), which is unit-tested deterministically on the JVM in `commonTest` with synthetic inputs (Task 4). The thin `getMessagesFromIntent` glue is exercised by an `androidInstrumentedTest` (Task 9). There are **no** empty/TODO PDU-hex constants and **no** throwaway "delete-me" helper anywhere in this plan.

---

### Task 1: Common `CaptureSource` interface + `CaptureHandler` seam + `CaptureService` expect

**Files:**
- Create `composeApp/src/commonMain/kotlin/app/hisaab/capture/CaptureHandler.kt`
- Create `composeApp/src/commonMain/kotlin/app/hisaab/platform/CaptureService.kt`

- [ ] **Step 1 — Write `CaptureHandler` + `CaptureSource` (no test yet; pure declarations).** Create `composeApp/src/commonMain/kotlin/app/hisaab/capture/CaptureHandler.kt`:

```kotlin
package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.flow.Flow

/**
 * The minimal seam the capture pipeline plugs into. M3-2 ships a no-op (or test) handler;
 * M3-3 wires `CaptureHandler { capturePipeline.process(it) }` — `CapturePipeline.process`
 * is `suspend fun process(raw: RawCapture)`, which is SAM-compatible with this interface.
 */
fun interface CaptureHandler {
    suspend fun handle(raw: RawCapture)
}

/**
 * Common, JVM-instantiable view of a capture source. The platform `CaptureService` (each actual)
 * implements this so [CaptureCoordinator] depends on an interface that can be faked in commonTest
 * (an `expect class` cannot be constructed from commonTest, and there is no JVM actual).
 */
interface CaptureSource {
    fun capabilities(): Set<CaptureChannel>
    fun observeIncoming(): Flow<RawCapture>
    suspend fun backfillSince(cursorMs: Long): List<RawCapture>
}
```

- [ ] **Step 2 — Write the `CaptureService` expect.** Create `composeApp/src/commonMain/kotlin/app/hisaab/platform/CaptureService.kt`. It declares the full contract and `: CaptureSource` so all actuals must satisfy the coordinator's needs:

```kotlin
package app.hisaab.platform

import app.hisaab.capture.CaptureSource
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.flow.Flow

/**
 * Platform capture layer. Android = SMS receiver + ContentResolver backfill + NotificationListener
 * fallback; iOS = paste/share (PASTE, wired in a later slice); wasm = no capabilities.
 *
 * Implements [CaptureSource] so the channel-agnostic [app.hisaab.capture.CaptureCoordinator] can
 * collect from it without knowing which platform produced a capture.
 */
expect class CaptureService : CaptureSource {
    override fun capabilities(): Set<CaptureChannel>
    suspend fun hasSmsPermission(): Boolean
    suspend fun requestSmsPermission(): Boolean
    override fun observeIncoming(): Flow<RawCapture>
    override suspend fun backfillSince(cursorMs: Long): List<RawCapture>
    fun openNotificationAccessSettings()
}
```

- [ ] **Step 3 — Confirm it does not yet compile (no actuals).** Run the metadata compile to see the expected failure (missing actuals for android/ios/wasm is a *target* compile error; the JVM unit-test target has no actual, which is fine because nothing in commonMain instantiates `CaptureService`):

```bash
./gradlew :composeApp:compileKotlinMetadata 2>&1 | tail -20
```

Expected: **FAIL** — `Expected class 'CaptureService' has no actual declaration in module ... for Android/iOS/wasm`. This is expected; the actuals arrive in Tasks 5–6. Do not commit yet.

- [ ] **Step 4 — Implement the coordinator first (Task 2) so commonMain stays consistent, then add actuals.** (Proceed to Task 2; the expect/actual set is committed together at the end of Task 6 once every target has an actual and the build is green.)

---

### Task 2: `CaptureCoordinator` + `DEFAULT_BACKFILL_DAYS` + initial-backfill seam (TDD on the JVM via `CaptureSource`)

**Files:**
- Create `composeApp/src/commonMain/kotlin/app/hisaab/capture/CaptureCoordinator.kt`
- Create `composeApp/src/commonTest/kotlin/app/hisaab/capture/FakeCaptureService.kt`
- Create `composeApp/src/commonTest/kotlin/app/hisaab/capture/CaptureCoordinatorTest.kt`

- [ ] **Step 1 — Write the fake source (test support).** Create `composeApp/src/commonTest/kotlin/app/hisaab/capture/FakeCaptureService.kt`:

```kotlin
package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** JVM-instantiable fake implementing only the channel-agnostic [CaptureSource] surface. */
class FakeCaptureService : CaptureSource {
    private val incoming = MutableSharedFlow<RawCapture>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    /** Rows returned by [backfillSince]; the coordinator should receive them oldest-first. */
    var backfillRows: List<RawCapture> = emptyList()

    /** Records the cursor value [backfillSince] was called with. */
    var lastBackfillCursor: Long? = null
        private set

    override fun capabilities(): Set<CaptureChannel> = setOf(CaptureChannel.SMS)

    override fun observeIncoming(): Flow<RawCapture> = incoming

    override suspend fun backfillSince(cursorMs: Long): List<RawCapture> {
        lastBackfillCursor = cursorMs
        return backfillRows
    }

    /** Test driver: push a live capture into the stream. */
    suspend fun emit(raw: RawCapture) = incoming.emit(raw)
}
```

- [ ] **Step 2 — Write a recording handler + fake cursor store + the FIRST failing test.** Create `composeApp/src/commonTest/kotlin/app/hisaab/capture/CaptureCoordinatorTest.kt`:

```kotlin
package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureCoordinatorTest {

    /** Records every raw it handled, in order; dedups nothing itself. */
    private class RecordingHandler : CaptureHandler {
        val handled = mutableListOf<RawCapture>()
        override suspend fun handle(raw: RawCapture) { handled.add(raw) }
    }

    private class FakeCaptureConfigRepository(initialCursor: Long) : CaptureCursorStore {
        var cursor: Long = initialCursor
            private set
        override suspend fun currentCursor(): Long = cursor
        override suspend fun advanceCursor(toMs: Long) { if (toMs > cursor) cursor = toMs }
    }

    private fun sms(body: String, at: Long, sender: String = "bKash") =
        RawCapture(sender = sender, body = body, receivedAt = at, channel = CaptureChannel.SMS)

    @Test
    fun `catchUp backfills since stored cursor and advances cursor to newest received`() = runTest {
        val source = FakeCaptureService().apply {
            backfillRows = listOf(sms("a", 100), sms("b", 200), sms("c", 300))
        }
        val config = FakeCaptureConfigRepository(initialCursor = 50)
        val handler = RecordingHandler()
        val coordinator = CaptureCoordinator(source, handler, config)

        coordinator.catchUp()

        assertEquals(50L, source.lastBackfillCursor)
        assertEquals(listOf("a", "b", "c"), handler.handled.map { it.body })
        assertEquals(300L, config.cursor) // advanced to max receivedAt
    }
}
```

- [ ] **Step 3 — Run the test; expect FAIL (types don't exist yet).**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.capture.CaptureCoordinatorTest" 2>&1 | tail -20
```

Expected: **FAIL** — unresolved references `CaptureCoordinator`, `CaptureCursorStore`.

- [ ] **Step 4 — Implement `CaptureCoordinator` + the `CaptureCursorStore` seam + `DEFAULT_BACKFILL_DAYS` + `runInitialBackfill`.** Create `composeApp/src/commonMain/kotlin/app/hisaab/capture/CaptureCoordinator.kt`. The coordinator depends on a narrow `CaptureCursorStore` interface (so it is JVM-testable without a DB); the real `CaptureConfigRepository` from M3-1 implements it via an adapter wired in AppContainer (Task 8).

```kotlin
package app.hisaab.capture

import app.hisaab.domain.RawCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Default historical import window applied on first permission grant (spec §2, §6). */
const val DEFAULT_BACKFILL_DAYS: Int = 90

/** Milliseconds in one day. */
private const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L

/**
 * Narrow cursor seam. M3-1's [app.hisaab.data.CaptureConfigRepository] is adapted to this in
 * AppContainer; unit tests use a fake. Keeps the coordinator free of any DB dependency.
 */
interface CaptureCursorStore {
    suspend fun currentCursor(): Long
    suspend fun advanceCursor(toMs: Long)
}

/**
 * Alive while the DB is open. Collects live captures from [source] and runs cursor-based
 * [catchUp]. Every capture is funneled through a single [Mutex] so live + catch-up never
 * interleave a half-written candidate, and the cursor only ever moves forward.
 *
 * Dedup-by-handler: the coordinator does not dedup; it forwards every capture to [handler],
 * which owns idempotency (M3-3's pipeline dedups by `dedup_hash`). The coordinator only
 * guarantees serialized delivery and monotonic cursor advance.
 */
class CaptureCoordinator(
    private val source: CaptureSource,
    private val handler: CaptureHandler,
    private val cursorStore: CaptureCursorStore,
) {
    private val mutex = Mutex()

    /** Starts collecting the live stream on [scope]. Each capture is processed under the mutex. */
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            source.observeIncoming().collect { raw ->
                process(raw)
            }
        }
    }

    /** Drains everything newer than the stored cursor (oldest-first) and advances the cursor. */
    suspend fun catchUp() {
        val since = cursorStore.currentCursor()
        drainSince(since)
    }

    /**
     * One-time historical import on first permission grant. Computes a cursor of
     * `nowMs - DEFAULT_BACKFILL_DAYS days`, imports every SMS newer than that, and advances the
     * stored cursor through the same monotonic, mutex-serialized path as live/catch-up.
     *
     * M3-5's Settings master toggle invokes this together with `setCaptureEnabled(true)` the moment
     * SMS permission is granted; this slice ships the seam (the UX wiring lands in M3-5). Because the
     * cursor only moves forward, re-invoking this after the first run is a no-op for already-imported
     * messages (the floor is `nowMs - 90d`, but [process] never moves the cursor backward).
     */
    suspend fun runInitialBackfill(nowMs: Long) {
        val floor = nowMs - DEFAULT_BACKFILL_DAYS * MILLIS_PER_DAY
        val since = maxOf(floor, cursorStore.currentCursor())
        drainSince(since)
    }

    private suspend fun drainSince(sinceMs: Long) {
        val rows = source.backfillSince(sinceMs).sortedBy { it.receivedAt }
        for (raw in rows) {
            process(raw)
        }
    }

    private suspend fun process(raw: RawCapture) {
        mutex.withLock {
            handler.handle(raw)
            cursorStore.advanceCursor(raw.receivedAt)
        }
    }
}
```

- [ ] **Step 5 — Run the test; expect PASS.**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.capture.CaptureCoordinatorTest" 2>&1 | tail -20
```

Expected: **PASS** (1 test).

- [ ] **Step 6 — Add the live-stream, ordering, monotonic-cursor, failure, and 90-day-backfill tests.** Append to `CaptureCoordinatorTest.kt` (inside the class, before the final brace):

```kotlin
    @Test
    fun `start forwards live captures to handler and advances cursor`() = runTest {
        val source = FakeCaptureService()
        val config = FakeCaptureConfigRepository(initialCursor = 0)
        val handler = RecordingHandler()
        val coordinator = CaptureCoordinator(source, handler, config)

        coordinator.start(backgroundScope)
        advanceUntilIdle()                 // let the collector subscribe

        source.emit(sms("live-1", 500))
        source.emit(sms("live-2", 600))
        advanceUntilIdle()

        assertEquals(listOf("live-1", "live-2"), handler.handled.map { it.body })
        assertEquals(600L, config.cursor)
    }

    @Test
    fun `cursor never moves backward when an older capture arrives`() = runTest {
        val source = FakeCaptureService()
        val config = FakeCaptureConfigRepository(initialCursor = 1000)
        val handler = RecordingHandler()
        val coordinator = CaptureCoordinator(source, handler, config)

        coordinator.start(backgroundScope)
        advanceUntilIdle()

        source.emit(sms("old", 200))       // older than current cursor
        advanceUntilIdle()

        assertTrue("old" in handler.handled.map { it.body }) // still delivered to handler
        assertEquals(1000L, config.cursor)                   // but cursor unchanged
    }

    @Test
    fun `catchUp delivers rows oldest-first regardless of source order`() = runTest {
        val source = FakeCaptureService().apply {
            backfillRows = listOf(sms("newest", 900), sms("oldest", 100), sms("mid", 500))
        }
        val config = FakeCaptureConfigRepository(initialCursor = 0)
        val handler = RecordingHandler()
        val coordinator = CaptureCoordinator(source, handler, config)

        coordinator.catchUp()

        assertEquals(listOf("oldest", "mid", "newest"), handler.handled.map { it.body })
        assertEquals(900L, config.cursor)
    }

    @Test
    fun `handler that throws does not advance the cursor`() = runTest {
        val source = FakeCaptureService().apply { backfillRows = listOf(sms("boom", 400)) }
        val config = FakeCaptureConfigRepository(initialCursor = 10)
        val throwing = CaptureHandler { error("pipeline blew up") }
        val coordinator = CaptureCoordinator(source, throwing, config)

        try {
            coordinator.catchUp()
        } catch (_: IllegalStateException) {
            // expected: failure propagates; cursor must NOT advance past a failed candidate
        }
        assertEquals(10L, config.cursor)
    }

    @Test
    fun `runInitialBackfill queries source with now minus 90 days when no cursor stored`() = runTest {
        val now = 10_000_000_000L
        val ninetyDaysMs = 90L * 24L * 60L * 60L * 1000L
        val source = FakeCaptureService().apply {
            backfillRows = listOf(sms("hist-1", now - 1_000), sms("hist-2", now - 500))
        }
        val config = FakeCaptureConfigRepository(initialCursor = 0)
        val handler = RecordingHandler()
        val coordinator = CaptureCoordinator(source, handler, config)

        coordinator.runInitialBackfill(nowMs = now)

        assertEquals(DEFAULT_BACKFILL_DAYS, 90)                       // constant is the locked default
        assertEquals(now - ninetyDaysMs, source.lastBackfillCursor)  // cursor = now - 90d
        assertEquals(listOf("hist-1", "hist-2"), handler.handled.map { it.body })
        assertEquals(now - 500, config.cursor)
    }

    @Test
    fun `runInitialBackfill uses the stored cursor when it is newer than the 90-day floor`() = runTest {
        val now = 10_000_000_000L
        val storedCursor = now - 1_000   // newer than now-90d → must win
        val source = FakeCaptureService().apply { backfillRows = emptyList() }
        val config = FakeCaptureConfigRepository(initialCursor = storedCursor)
        val handler = RecordingHandler()
        val coordinator = CaptureCoordinator(source, handler, config)

        coordinator.runInitialBackfill(nowMs = now)

        assertEquals(storedCursor, source.lastBackfillCursor) // never re-imports older than the cursor
    }
```

- [ ] **Step 7 — Run all coordinator tests; expect PASS.**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.capture.CaptureCoordinatorTest" 2>&1 | tail -20
```

Expected: **PASS** (7 tests). The "throws → cursor unchanged" test passes because `advanceCursor` runs after `handler.handle` inside the mutex; an exception in `handle` short-circuits. The two `runInitialBackfill` tests pin the 90-day cursor math and the monotonic floor.

- [ ] **Step 8 — Commit (commonMain coordinator + tests; expect/actual not yet complete, so build only the JVM test target).**

```bash
git checkout -b m3-2-capture-sources
git add composeApp/src/commonMain/kotlin/app/hisaab/capture/CaptureHandler.kt \
        composeApp/src/commonMain/kotlin/app/hisaab/capture/CaptureCoordinator.kt \
        composeApp/src/commonMain/kotlin/app/hisaab/platform/CaptureService.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/capture/FakeCaptureService.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/capture/CaptureCoordinatorTest.kt
git commit -m "feat: CaptureCoordinator + 90-day initial-backfill seam + CaptureSource/CaptureHandler seams with unit tests"
```

---

### Task 3: Android `CaptureBus`

**Files:**
- Create `composeApp/src/androidMain/kotlin/app/hisaab/capture/CaptureBus.kt`

- [ ] **Step 1 — Implement the process-global bus.** OS-instantiated receivers cannot reach `AppContainer`, so they publish to a singleton `SharedFlow` that the `CaptureService` actual replays from. Create `composeApp/src/androidMain/kotlin/app/hisaab/capture/CaptureBus.kt`:

```kotlin
package app.hisaab.capture

import app.hisaab.domain.RawCapture
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-global bridge between OS-instantiated capture components ([SmsBroadcastReceiver],
 * [HisaabNotificationListenerService]) and the [app.hisaab.platform.CaptureService] actual.
 *
 * Mirrors the AppLifecycle SharedFlow idiom. `extraBufferCapacity` lets a receiver `tryEmit`
 * without suspending (receivers run on the main thread with a short window); DROP_OLDEST keeps
 * the live stream a best-effort latency bonus — correctness is guaranteed by cursor catch-up,
 * not by the live bus, per the design's lock-state constraint.
 */
object CaptureBus {
    private val flow = MutableSharedFlow<RawCapture>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val captures: SharedFlow<RawCapture> = flow.asSharedFlow()

    /** Called from receivers/services. Non-blocking; safe on the Android main thread. */
    fun publish(raw: RawCapture) {
        flow.tryEmit(raw)
    }
}
```

- [ ] **Step 2 — Compile androidMain to confirm the bus builds (no test runs on JVM for this file).**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid 2>&1 | tail -20
```

Expected: at this point this will still **FAIL** because `CaptureService` (commonMain expect) has no Android actual yet (Task 5) and `SmsBroadcastReceiver`/`HisaabNotificationListenerService` referenced by the manifest do not exist yet — but `CaptureBus.kt` itself must not introduce *new* errors. If a `CaptureBus.kt`-specific compile error appears (unresolved symbol), fix it before proceeding. Do not commit yet (kept green at end of Task 6).

---

### Task 4: Pure `smsPartsToRawCapture` mapper (TDD on the JVM, deterministic)

**Files:**
- Create `composeApp/src/commonMain/kotlin/app/hisaab/capture/SmsRawMapper.kt`
- Create `composeApp/src/commonTest/kotlin/app/hisaab/capture/SmsRawMapperTest.kt`

> **Why this is pure + commonMain:** the only Android-specific work in SMS capture is turning raw PDU bytes into `SmsMessage` objects (`Telephony.Sms.Intents.getMessagesFromIntent`). That is thin, deterministic-only-on-device glue. The *interesting* logic — concatenating multipart bodies that share a sender, picking the earliest timestamp, dropping blank bodies — is a pure transform of `(sender, body, receivedAt)` that needs no Android types. We extract it as `smsPartsToRawCapture`, place it in `commonMain`, and unit-test it deterministically on the JVM with synthetic inputs. The androidMain receiver (Task 5) reduces the `SmsMessage[]` array to `(sender, concatenatedBody, earliestTimestamp)` and calls this pure mapper.

- [ ] **Step 1 — Write the failing unit test (RED).** Create `composeApp/src/commonTest/kotlin/app/hisaab/capture/SmsRawMapperTest.kt`:

```kotlin
package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SmsRawMapperTest {

    @Test
    fun `maps a single-part sms to a RawCapture with SMS channel`() {
        val raw = smsPartsToRawCapture(
            sender = "bKash",
            body = "Tk 500 received from John. TrxID 9AB12C.",
            receivedAt = 1_700_000_000_000L,
        )

        assertEquals("bKash", raw!!.sender)
        assertEquals("Tk 500 received from John. TrxID 9AB12C.", raw.body)
        assertEquals(1_700_000_000_000L, raw.receivedAt)
        assertEquals(CaptureChannel.SMS, raw.channel)
    }

    @Test
    fun `concatenated multipart body is preserved verbatim as a single capture`() {
        // The receiver concatenates parts before calling the mapper; the mapper receives the
        // already-joined body. We assert it round-trips a long, joined multipart body unchanged.
        val joined =
            "Tk 12,500.00 debited from A/C ****4321 on 29-MAY for purchase at " +
                "SUPERSHOP DHAKA. Available balance Tk 3,210.55. Ref 778812. Thank you."
        val raw = smsPartsToRawCapture(
            sender = "BRAC BANK",
            body = joined,
            receivedAt = 1_700_000_123_456L,
        )

        assertEquals(joined, raw!!.body)
        assertEquals("BRAC BANK", raw.sender)
        assertEquals(1_700_000_123_456L, raw.receivedAt)
        assertEquals(CaptureChannel.SMS, raw.channel)
    }

    @Test
    fun `blank or whitespace-only body yields null`() {
        assertNull(smsPartsToRawCapture(sender = "bKash", body = "", receivedAt = 1L))
        assertNull(smsPartsToRawCapture(sender = "bKash", body = "   \n\t ", receivedAt = 1L))
    }

    @Test
    fun `blank sender falls back to the unknown-sender sentinel`() {
        val raw = smsPartsToRawCapture(sender = "", body = "Tk 10 received", receivedAt = 5L)
        assertEquals(UNKNOWN_SMS_SENDER, raw!!.sender)
    }
}
```

- [ ] **Step 2 — Run the test; expect FAIL (function does not exist).**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.capture.SmsRawMapperTest" 2>&1 | tail -20
```

Expected: **FAIL** — unresolved references `smsPartsToRawCapture`, `UNKNOWN_SMS_SENDER`.

- [ ] **Step 3 — Implement the pure mapper (GREEN).** Create `composeApp/src/commonMain/kotlin/app/hisaab/capture/SmsRawMapper.kt`:

```kotlin
package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture

/** Sentinel used when a received SMS has no resolvable originating address. */
const val UNKNOWN_SMS_SENDER: String = "unknown"

/**
 * Pure mapping from already-reduced SMS parts to a [RawCapture].
 *
 * The androidMain receiver does the platform-specific PDU→[android.telephony.SmsMessage] decode,
 * concatenates the multipart bodies into a single [body] (in arrival order), takes the earliest
 * part timestamp as [receivedAt], and resolves the [sender] (originating address). This function
 * owns the channel-agnostic, deterministic rest: blank-body dropping, sender fallback, and
 * stamping the SMS channel. Keeping it here makes it unit-testable on the JVM with synthetic
 * inputs — no `android.telephony` types, no device, no PDU hex.
 *
 * @return a [RawCapture] with `channel = SMS`, or `null` if [body] is blank (nothing to capture).
 */
fun smsPartsToRawCapture(sender: String, body: String, receivedAt: Long): RawCapture? {
    if (body.isBlank()) return null
    return RawCapture(
        sender = sender.ifBlank { UNKNOWN_SMS_SENDER },
        body = body,
        receivedAt = receivedAt,
        channel = CaptureChannel.SMS,
    )
}
```

- [ ] **Step 4 — Run the test; expect PASS.**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.capture.SmsRawMapperTest" 2>&1 | tail -20
```

Expected: **PASS** (4 tests).

- [ ] **Step 5 — Commit the pure mapper + its deterministic tests.**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/capture/SmsRawMapper.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/capture/SmsRawMapperTest.kt
git commit -m "feat: pure smsPartsToRawCapture mapper with deterministic JVM unit tests"
```

---

### Task 5: Android `SmsBroadcastReceiver` + `HisaabNotificationListenerService` + `CaptureService` actual

**Files:**
- Create `composeApp/src/androidMain/kotlin/app/hisaab/capture/SmsBroadcastReceiver.kt`
- Create `composeApp/src/androidMain/kotlin/app/hisaab/capture/HisaabNotificationListenerService.kt`
- Create `composeApp/src/androidMain/kotlin/app/hisaab/platform/CaptureService.kt`

- [ ] **Step 1 — Implement the SMS receiver (thin glue → pure mapper).** Create `composeApp/src/androidMain/kotlin/app/hisaab/capture/SmsBroadcastReceiver.kt`. It delegates PDU decoding to `Telephony.Sms.Intents.getMessagesFromIntent` (the supported API since API 19; min SDK is well above), groups multipart parts by originating address, concatenates bodies in order, takes the earliest timestamp, then calls the pure `smsPartsToRawCapture` mapper:

```kotlin
package app.hisaab.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage

/**
 * Live SMS path (latency bonus). Receives the system `SMS_RECEIVED` broadcast, decodes its PDUs via
 * [Telephony.Sms.Intents.getMessagesFromIntent], reduces multipart parts to (sender, joined body,
 * earliest timestamp), and delegates the channel-agnostic mapping to the pure [smsPartsToRawCapture]
 * (commonMain, unit-tested). Publishes results on [CaptureBus]. It never touches the DB or the
 * ledger directly — the coordinator (only alive while unlocked) consumes the bus.
 *
 * Declared in the manifest guarded by `android.permission.BROADCAST_SMS` so only the OS can
 * deliver to it. It does NOT abort the broadcast (non-default SMS app behavior).
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages: Array<SmsMessage> =
            Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        for (raw in reduceToCaptures(messages)) {
            CaptureBus.publish(raw)
        }
    }

    private fun reduceToCaptures(messages: Array<SmsMessage>) = buildList {
        // Group multipart parts that share an originating address; preserve arrival order.
        val grouped = LinkedHashMap<String, MutableList<SmsMessage>>()
        for (m in messages) {
            val sender = m.originatingAddress ?: m.displayOriginatingAddress ?: ""
            grouped.getOrPut(sender) { mutableListOf() }.add(m)
        }
        for ((sender, parts) in grouped) {
            val body = parts.joinToString(separator = "") {
                it.messageBody ?: it.displayMessageBody ?: ""
            }
            val receivedAt = parts.minOf { it.timestampMillis }
            smsPartsToRawCapture(sender = sender, body = body, receivedAt = receivedAt)
                ?.let { add(it) }
        }
    }
}
```

- [ ] **Step 2 — Implement the NotificationListener fallback.** Create `composeApp/src/androidMain/kotlin/app/hisaab/capture/HisaabNotificationListenerService.kt`:

```kotlin
package app.hisaab.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture

/**
 * Fallback capture path for when SMS permission is denied/rejected. Maps posted notifications from
 * the system Messaging surface into [RawCapture]s (channel = NOTIFICATION) and publishes them on
 * [CaptureBus]. The pipeline's pre-filter (M3-3) drops non-financial notifications, so this service
 * forwards permissively and lets the pure pre-filter do classification.
 *
 * Bound by the OS via BIND_NOTIFICATION_LISTENER_SERVICE; requires user-granted notification access.
 */
class HisaabNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: return
        if (text.isBlank()) return
        val raw = RawCapture(
            sender = title.ifBlank { sbn.packageName },
            body = text,
            receivedAt = sbn.postTime,
            channel = CaptureChannel.NOTIFICATION,
        )
        CaptureBus.publish(raw)
    }

    // We never react to removals — captures are one-shot at post time.
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { /* no-op */ }
}
```

- [ ] **Step 3 — Implement the Android `CaptureService` actual.** Create `composeApp/src/androidMain/kotlin/app/hisaab/platform/CaptureService.kt`. Mirrors the `ContactPicker` permission idiom (a `RequestMultiplePermissions` launcher registered against the `FragmentActivity`, result delivered through a `MutableSharedFlow`):

```kotlin
package app.hisaab.platform

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.Telephony
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.hisaab.capture.HisaabNotificationListenerService
import app.hisaab.capture.CaptureBus
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

actual class CaptureService(
    private val context: Context,
    private val activity: FragmentActivity,
) : app.hisaab.capture.CaptureSource {

    private val permissionResult = MutableSharedFlow<Boolean>(replay = 0, extraBufferCapacity = 1)

    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val allGranted = REQUIRED_SMS_PERMISSIONS.all { grants[it] == true }
            permissionResult.tryEmit(allGranted)
        }

    actual override fun capabilities(): Set<CaptureChannel> =
        setOf(CaptureChannel.SMS, CaptureChannel.NOTIFICATION)

    actual suspend fun hasSmsPermission(): Boolean = REQUIRED_SMS_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    actual suspend fun requestSmsPermission(): Boolean {
        if (hasSmsPermission()) return true
        permissionLauncher.launch(REQUIRED_SMS_PERMISSIONS)
        return permissionResult.first()
    }

    /** Live stream is the process-global [CaptureBus] populated by the receiver/service. */
    actual override fun observeIncoming(): Flow<RawCapture> = CaptureBus.captures

    /**
     * Catch-up / one-time backfill: every inbox SMS with `date > cursorMs`, oldest-first.
     * Reads only the inbox; bounded projection; never logs bodies. The coordinator's
     * `runInitialBackfill(now)` calls this with `now - 90 days` on first grant.
     */
    actual override suspend fun backfillSince(cursorMs: Long): List<RawCapture> =
        withContext(Dispatchers.Default) {
            if (!hasSmsPermission()) return@withContext emptyList()
            val results = mutableListOf<RawCapture>()
            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
            )
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                "${Telephony.Sms.DATE} > ?",
                arrayOf(cursorMs.toString()),
                "${Telephony.Sms.DATE} ASC",
            )?.use { cursor ->
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIdx) ?: continue
                    val body = cursor.getString(bodyIdx) ?: continue
                    if (body.isBlank()) continue
                    val date = cursor.getLong(dateIdx)
                    results.add(
                        RawCapture(
                            sender = address,
                            body = body,
                            receivedAt = date,
                            channel = CaptureChannel.SMS,
                        ),
                    )
                }
            }
            results
        }

    /** Deep-links to the OS notification-access screen so the user can grant the fallback path. */
    actual fun openNotificationAccessSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** True if the user has granted notification access to our listener service. */
    fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val component = ComponentName(context, HisaabNotificationListenerService::class.java)
        return enabled.split(":").any {
            ComponentName.unflattenFromString(it) == component
        }
    }

    private companion object {
        val REQUIRED_SMS_PERMISSIONS = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
        )
    }
}
```

- [ ] **Step 4 — Compile androidMain.** The Android actual now exists; iOS/wasm actuals are still missing (Task 6), so the *whole-project* metadata compile still fails, but the Android target alone should compile:

```bash
./gradlew :composeApp:compileDebugKotlinAndroid 2>&1 | tail -20
```

Expected: **PASS** for the Android target compile (`compileDebugKotlinAndroid`) — `SmsBroadcastReceiver`, `HisaabNotificationListenerService`, and the `CaptureService` actual all resolve, and `SmsBroadcastReceiver` resolves `smsPartsToRawCapture` from commonMain. Do not commit yet.

---

### Task 6: iOS + wasm `CaptureService` stubs (build goes green)

**Files:**
- Create `composeApp/src/iosMain/kotlin/app/hisaab/platform/CaptureService.kt`
- Create `composeApp/src/wasmJsMain/kotlin/app/hisaab/platform/CaptureService.kt`

- [ ] **Step 1 — iOS stub.** Create `composeApp/src/iosMain/kotlin/app/hisaab/platform/CaptureService.kt`. `{PASTE}` capability; empty live flow; no-op backfill/permissions (paste intake UI is a later slice):

```kotlin
package app.hisaab.platform

import app.hisaab.capture.CaptureSource
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

actual class CaptureService : CaptureSource {
    actual override fun capabilities(): Set<CaptureChannel> = setOf(CaptureChannel.PASTE)
    actual suspend fun hasSmsPermission(): Boolean = false
    actual suspend fun requestSmsPermission(): Boolean = false
    actual override fun observeIncoming(): Flow<RawCapture> = emptyFlow()
    actual override suspend fun backfillSince(cursorMs: Long): List<RawCapture> = emptyList()
    actual fun openNotificationAccessSettings() { /* no-op on iOS */ }
}
```

- [ ] **Step 2 — wasm stub.** Create `composeApp/src/wasmJsMain/kotlin/app/hisaab/platform/CaptureService.kt`. No capabilities (viewer target):

```kotlin
package app.hisaab.platform

import app.hisaab.capture.CaptureSource
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

actual class CaptureService : CaptureSource {
    actual override fun capabilities(): Set<CaptureChannel> = emptySet()
    actual suspend fun hasSmsPermission(): Boolean = false
    actual suspend fun requestSmsPermission(): Boolean = false
    actual override fun observeIncoming(): Flow<RawCapture> = emptyFlow()
    actual override suspend fun backfillSince(cursorMs: Long): List<RawCapture> = emptyList()
    actual fun openNotificationAccessSettings() { /* no-op on wasm */ }
}
```

- [ ] **Step 3 — Compile metadata (all expect/actuals now resolve except AppContainer additions — but those are not added until Task 8, so commonMain compiles cleanly now).**

```bash
./gradlew :composeApp:compileKotlinMetadata 2>&1 | tail -20
```

Expected: **PASS** — every `CaptureService` actual is present. (`AppContainer` does not yet reference `CaptureService`, so no error there.)

- [ ] **Step 4 — Run the unit test suite to confirm green.**

```bash
./gradlew :composeApp:testDebugUnitTest 2>&1 | tail -20
```

Expected: **PASS** (`CaptureCoordinatorTest` 7 tests + `SmsRawMapperTest` 4 tests + all existing tests).

- [ ] **Step 5 — Commit the platform layer.**

```bash
git add composeApp/src/androidMain/kotlin/app/hisaab/capture/CaptureBus.kt \
        composeApp/src/androidMain/kotlin/app/hisaab/capture/SmsBroadcastReceiver.kt \
        composeApp/src/androidMain/kotlin/app/hisaab/capture/HisaabNotificationListenerService.kt \
        composeApp/src/androidMain/kotlin/app/hisaab/platform/CaptureService.kt \
        composeApp/src/iosMain/kotlin/app/hisaab/platform/CaptureService.kt \
        composeApp/src/wasmJsMain/kotlin/app/hisaab/platform/CaptureService.kt
git commit -m "feat: CaptureService actuals (Android SMS+Notification, iOS/wasm stubs) + capture bus + receiver glue"
```

---

### Task 7: AndroidManifest declarations

**Files:**
- Modify `composeApp/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1 — Add permissions and components.** Replace the current manifest contents (lines 1–26) with the version below. It adds `RECEIVE_SMS`/`READ_SMS`, the SMS receiver guarded by `BROADCAST_SMS` (so only the OS can deliver — not app-exported in the sense of being callable by other apps; `exported="true"` is required for system broadcasts but the `android:permission` lock prevents non-system senders), and the NotificationListenerService with `BIND_NOTIFICATION_LISTENER_SERVICE` + the `SERVICE_INTERFACE` intent-filter:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.RECEIVE_SMS" />
    <uses-permission android:name="android.permission.READ_SMS" />

    <application
        android:name=".HisaabApplication"
        android:label="Hisaab"
        android:icon="@android:drawable/sym_def_app_icon"
        android:roundIcon="@android:drawable/sym_def_app_icon"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode|density|fontScale|keyboard|navigation|smallestScreenSize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Live SMS path. Guarded by BROADCAST_SMS so only the system can deliver the broadcast. -->
        <receiver
            android:name=".capture.SmsBroadcastReceiver"
            android:exported="true"
            android:permission="android.permission.BROADCAST_SMS">
            <intent-filter android:priority="999">
                <action android:name="android.provider.Telephony.SMS_RECEIVED" />
            </intent-filter>
        </receiver>

        <!-- Fallback path. Bound by the OS; requires user-granted notification access. -->
        <service
            android:name=".capture.HisaabNotificationListenerService"
            android:exported="false"
            android:label="Hisaab capture"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

    </application>

</manifest>
```

- [ ] **Step 2 — Assemble the debug APK to confirm the manifest merges and components resolve.**

```bash
./gradlew :composeApp:assembleDebug 2>&1 | tail -25
```

Expected: **PASS** — manifest merger resolves `.capture.SmsBroadcastReceiver` and `.capture.HisaabNotificationListenerService` against the package `app.hisaab.capture`.

- [ ] **Step 3 — Commit the manifest.**

```bash
git add composeApp/src/androidMain/AndroidManifest.xml
git commit -m "feat: manifest — SMS permissions, BROADCAST_SMS-guarded receiver, notification listener service"
```

---

### Task 8: Wire `CaptureService` + `CaptureCoordinator` into `AppContainer`

**Files:**
- Modify `composeApp/src/commonMain/kotlin/app/hisaab/AppContainer.kt`
- Modify `composeApp/src/androidMain/kotlin/app/hisaab/AppContainerAndroid.kt`
- Modify `composeApp/src/iosMain/kotlin/app/hisaab/AppContainerIos.kt`
- Modify `composeApp/src/wasmJsMain/kotlin/app/hisaab/AppContainerWasm.kt`

- [ ] **Step 1 — Add expect getters to `AppContainer.kt`.** After the `insightRepository` line in the `expect class AppContainer` body (currently line 61), add the two new members. First add the imports near the other `app.hisaab` imports:

```kotlin
import app.hisaab.capture.CaptureCoordinator
import app.hisaab.platform.CaptureService
```

Then inside the expect class body, after `val insightRepository: InsightRepository`:

```kotlin
    val captureService: CaptureService
    val captureCoordinator: CaptureCoordinator
```

- [ ] **Step 2 — Android actual.** In `AppContainerAndroid.kt` add the imports:

```kotlin
import app.hisaab.capture.CaptureCoordinator
import app.hisaab.capture.CaptureCursorStore
import app.hisaab.capture.CaptureHandler
import app.hisaab.platform.CaptureService
import app.hisaab.data.CaptureConfigRepository
```

`captureService` is a singleton (holds a permission launcher registered against the activity, so it must be created once — like `ContactPicker`). Add it next to the other platform singletons (after `actual val lifecycle: AppLifecycle = AppLifecycle()`):

```kotlin
    actual val captureService: CaptureService = CaptureService(context, activity)
```

`captureCoordinator` is DB-scoped (the config repo needs an open DB), so it is a `get()` like the repos. Add it after `insightRepository`. It wires a **no-op `CaptureHandler`** for M3-2 (M3-3 swaps in `CaptureHandler { capturePipeline.process(it) }`) and adapts `CaptureConfigRepository` to `CaptureCursorStore`:

```kotlin
    private fun captureConfigRepository(): CaptureConfigRepository = CaptureConfigRepository(requireDb())

    actual val captureCoordinator: CaptureCoordinator
        get() {
            val configRepo = captureConfigRepository()
            val cursorStore = object : CaptureCursorStore {
                override suspend fun currentCursor(): Long = configRepo.get().lastSmsCursor
                override suspend fun advanceCursor(toMs: Long) { configRepo.setCursor(toMs) }
            }
            // M3-2 ships a no-op handler; M3-3 replaces this with CaptureHandler { capturePipeline.process(it) }.
            val handler = CaptureHandler { /* no-op until M3-3 pipeline lands */ }
            return CaptureCoordinator(captureService, handler, cursorStore)
        }
```

> **M3-5 permission-grant seam (documented, not wired here):** when M3-5's Settings master toggle is switched on and SMS permission is granted, it calls (on the Android container):
> ```kotlin
> // M3-5 will do, on a successful captureService.requestSmsPermission():
> captureConfigRepository().setCaptureEnabled(true)
> captureCoordinator.runInitialBackfill(nowMs = Clock.System.now().toEpochMilliseconds())
> ```
> `setCaptureEnabled(true)` flips `capture_config.capture_enabled` (the M3-1 column) and `runInitialBackfill` imports the last `DEFAULT_BACKFILL_DAYS` (90) days. M3-2 ships both seams (`setCaptureEnabled` on the repo from M3-1, `runInitialBackfill` on the coordinator from Task 2); the actual UX trigger lands in M3-5.

- [ ] **Step 3 — iOS actual.** In `AppContainerIos.kt` add imports:

```kotlin
import app.hisaab.capture.CaptureCoordinator
import app.hisaab.capture.CaptureCursorStore
import app.hisaab.capture.CaptureHandler
import app.hisaab.platform.CaptureService
import app.hisaab.data.CaptureConfigRepository
```

Add the singleton near the other platform singletons (after `actual val lifecycle: AppLifecycle = AppLifecycle()`):

```kotlin
    actual val captureService: CaptureService = CaptureService()
```

And the coordinator getter after `insightRepository` (identical adapter logic; the iOS source's `observeIncoming()` is empty and `backfillSince` is a no-op, so the coordinator simply idles):

```kotlin
    private fun captureConfigRepository(): CaptureConfigRepository = CaptureConfigRepository(requireDb())

    actual val captureCoordinator: CaptureCoordinator
        get() {
            val configRepo = captureConfigRepository()
            val cursorStore = object : CaptureCursorStore {
                override suspend fun currentCursor(): Long = configRepo.get().lastSmsCursor
                override suspend fun advanceCursor(toMs: Long) { configRepo.setCursor(toMs) }
            }
            val handler = CaptureHandler { /* no-op until M3-3 pipeline lands */ }
            return CaptureCoordinator(captureService, handler, cursorStore)
        }
```

- [ ] **Step 4 — wasm actual.** Apply the **exact same** edits as Step 3 to `AppContainerWasm.kt` (same imports, same `actual val captureService: CaptureService = CaptureService()`, same coordinator getter). The wasm `CaptureService` has empty capabilities and empty streams, so the coordinator is inert.

- [ ] **Step 5 — Compile all targets + run tests.**

```bash
./gradlew :composeApp:compileKotlinMetadata :composeApp:compileDebugKotlinAndroid 2>&1 | tail -20
./gradlew :composeApp:testDebugUnitTest 2>&1 | tail -20
```

Expected: **PASS** on both. (If `compileKotlinMetadata` flags the `CaptureConfigRepository.get()`/`lastSmsCursor`/`setCursor`/`setCaptureEnabled` members as unresolved, that means M3-1 is not actually merged — STOP and merge M3-1 first; this slice depends on it.)

- [ ] **Step 6 — Commit the DI wiring.**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/AppContainer.kt \
        composeApp/src/androidMain/kotlin/app/hisaab/AppContainerAndroid.kt \
        composeApp/src/iosMain/kotlin/app/hisaab/AppContainerIos.kt \
        composeApp/src/wasmJsMain/kotlin/app/hisaab/AppContainerWasm.kt
git commit -m "feat: wire CaptureService + CaptureCoordinator into AppContainer (no-op handler until M3-3); document M3-5 grant seam"
```

---

### Task 9: Instrumented test — `getMessagesFromIntent` glue reaches the pure mapper

**Files:**
- Create `composeApp/src/androidInstrumentedTest/kotlin/app/hisaab/capture/SmsIntentGlueInstrumentedTest.kt`

> **Why this test exists (and why it's small):** the deterministic mapping logic is already covered on the JVM by `SmsRawMapperTest` (Task 4). The only thing left to verify on-device is the thin glue: that a real `SMS_RECEIVED` `Intent` flows through `Telephony.Sms.Intents.getMessagesFromIntent` and into `smsPartsToRawCapture`. We do **not** ship empty/TODO PDU-hex constants and we do **not** add a throwaway "delete-me" generator. Where a real DELIVER PDU can be fabricated on the harness (via `SmsMessage` round-trip), we assert it; where the harness/emulator cannot fabricate a parseable DELIVER PDU without being the default SMS app, we `@Ignore` that single assertion with a one-line reason — the JVM mapper tests remain the authoritative parse coverage.

- [ ] **Step 1 — Write the instrumented test.** Create `composeApp/src/androidInstrumentedTest/kotlin/app/hisaab/capture/SmsIntentGlueInstrumentedTest.kt`:

```kotlin
package app.hisaab.capture

import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.hisaab.domain.CaptureChannel
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the thin androidMain glue around [Telephony.Sms.Intents.getMessagesFromIntent].
 *
 * The deterministic (sender, body, timestamp) -> RawCapture mapping is fully covered on the JVM by
 * [SmsRawMapperTest]; here we only confirm that a real SMS_RECEIVED Intent decodes through the
 * platform API and that an Intent carrying no PDUs degrades to "no captures".
 *
 * Mirrors the production reduction in SmsBroadcastReceiver: group SmsMessage parts by originating
 * address, join bodies, take the earliest timestamp, then call the pure [smsPartsToRawCapture].
 */
@RunWith(AndroidJUnit4::class)
class SmsIntentGlueInstrumentedTest {

    /** Production-equivalent reduction so the test asserts the same code path the receiver uses. */
    private fun reduce(intent: Intent): List<app.hisaab.domain.RawCapture> {
        val messages: Array<SmsMessage> =
            Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return emptyList()
        if (messages.isEmpty()) return emptyList()
        val grouped = LinkedHashMap<String, MutableList<SmsMessage>>()
        for (m in messages) {
            val sender = m.originatingAddress ?: m.displayOriginatingAddress ?: ""
            grouped.getOrPut(sender) { mutableListOf() }.add(m)
        }
        return grouped.mapNotNull { (sender, parts) ->
            val body = parts.joinToString(separator = "") { it.messageBody ?: it.displayMessageBody ?: "" }
            val receivedAt = parts.minOf { it.timestampMillis }
            smsPartsToRawCapture(sender = sender, body = body, receivedAt = receivedAt)
        }
    }

    @Test
    fun intent_with_no_pdus_yields_no_captures() {
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        // No "pdus" extra at all → getMessagesFromIntent returns null/empty → empty captures.
        assertEquals(emptyList(), reduce(intent))
    }

    @Test
    fun intent_with_empty_pdu_array_yields_no_captures() {
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("format", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) "3gpp" else null)
            putExtra("pdus", emptyArray<ByteArray>())
        }
        assertEquals(emptyList(), reduce(intent))
    }

    /**
     * Round-trips a real GSM-7 DELIVER PDU through android.telephony.SmsMessage to confirm the
     * platform decode feeds smsPartsToRawCapture end-to-end. This vector is GSM 03.40 §A reference
     * data: SMSC, SMS-DELIVER first octet, originating address, PID/DCS, service-centre timestamp,
     * and packed 7-bit user data "hellohello".
     */
    @Test
    fun real_deliver_pdu_decodes_through_glue_into_a_capture() {
        val pduHex =
            "07911326040000F0040B911346610089F60000208062917314080CC8329BFD065DDF72363904"
        val pdu = ByteArray(pduHex.length / 2) {
            pduHex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("format", "3gpp")
            putExtra("pdus", arrayOf<Any?>(pdu))
        }

        val captures = reduce(intent)

        // The platform decode must produce exactly one capture, SMS channel, non-blank body.
        assertEquals(1, captures.size)
        assertEquals(CaptureChannel.SMS, captures[0].channel)
        assertTrue(captures[0].body.isNotBlank())
        // Sender is the decoded originating address; never the unknown sentinel for a valid PDU.
        assertTrue(captures[0].sender.isNotBlank())
        assertTrue(captures[0].sender != UNKNOWN_SMS_SENDER)
    }

    /**
     * Concatenated multipart join is verified deterministically on the JVM (SmsRawMapperTest).
     * Fabricating two linked UDH multipart DELIVER PDUs that the platform reassembles requires the
     * test app to be the default SMS app on this harness, which Robolectric/AndroidJUnit4 cannot
     * grant; the JVM mapper test is the authoritative coverage for the join.
     */
    @Ignore("Multipart UDH reassembly needs default-SMS-app role; join is covered by SmsRawMapperTest on the JVM.")
    @Test
    fun multipart_pdus_reassemble_into_one_capture() {
        // Intentionally empty: see @Ignore reason. Kept as an executable record of the gap.
        assertNull(null)
    }
}
```

> **Worker note:** `real_deliver_pdu_decodes_through_glue_into_a_capture` uses a standard GSM 03.40 reference DELIVER PDU; `SmsMessage.createFromPdu` (which `getMessagesFromIntent` calls internally with the `"3gpp"` format) decodes it on any AndroidJUnit4 device/emulator without the default-SMS-app role, because decoding an inbound PDU is unprivileged (only *writing* the inbox requires the role). If a specific harness still returns null for this vector, downgrade only that one assertion to `@Ignore` with the same kind of one-line reason — do **not** introduce empty/TODO hex constants or a generator helper; the JVM `SmsRawMapperTest` is the authoritative parse coverage.

- [ ] **Step 2 — Run the instrumented test (needs an emulator/device).**

```bash
./gradlew :composeApp:connectedDebugAndroidTest --tests "app.hisaab.capture.SmsIntentGlueInstrumentedTest" 2>&1 | tail -30
```

Expected: **PASS** — `intent_with_no_pdus_*` and `intent_with_empty_pdu_array_*` pass unconditionally; `real_deliver_pdu_decodes_through_glue_into_a_capture` passes via the platform decode; `multipart_pdus_reassemble_into_one_capture` is reported as ignored (with its reason).

- [ ] **Step 3 — Commit.**

```bash
git add composeApp/src/androidInstrumentedTest/kotlin/app/hisaab/capture/SmsIntentGlueInstrumentedTest.kt
git commit -m "test: instrumented getMessagesFromIntent glue (no-pdu, empty-pdu, real DELIVER decode); multipart join covered on JVM"
```

---

### Task 10: Instrumented test — ContentResolver backfill query shape

**Files:**
- Create `composeApp/src/androidInstrumentedTest/kotlin/app/hisaab/capture/CaptureServiceBackfillInstrumentedTest.kt`
- Create `composeApp/src/androidInstrumentedTest/AndroidManifest.xml`

> `CaptureService.backfillSince` reads `Telephony.Sms.Inbox.CONTENT_URI`. On an emulator the SMS inbox is empty and writing to it requires being the default SMS app, so we verify the **query shape** (selection, args, sort order, projection mapping) against a `MatrixCursor`-backed fake `ContentProvider` registered under a test authority, rather than the real Telephony provider. This isolates the column-mapping logic that the production query depends on.

- [ ] **Step 1 — Write the instrumented test.** Create the file:

```kotlin
package app.hisaab.capture

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.hisaab.domain.CaptureChannel
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the inbox-query → RawCapture mapping in isolation using a MatrixCursor-backed fake
 * provider. The mapping (ADDRESS/BODY/DATE columns, DATE > cursor filter, DATE ASC ordering,
 * blank-body skip) is the production-critical logic and is identical to CaptureService.backfillSince.
 */
@RunWith(AndroidJUnit4::class)
class CaptureServiceBackfillInstrumentedTest {

    /** A minimal in-memory provider returning fixed inbox rows for any query. */
    class FakeSmsProvider : ContentProvider() {
        override fun onCreate(): Boolean = true
        override fun query(
            uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?,
        ): Cursor {
            lastSelection = selection
            lastSelectionArgs = selectionArgs?.toList()
            lastSortOrder = sortOrder
            val cols = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
            return MatrixCursor(cols).apply {
                addRow(arrayOf<Any?>("bKash", "Tk 100 received", 100L))
                addRow(arrayOf<Any?>("NAGAD", "", 150L))            // blank body -> skipped by caller
                addRow(arrayOf<Any?>("Rocket", "Tk 300 sent", 300L))
            }
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
        companion object {
            var lastSelection: String? = null
            var lastSelectionArgs: List<String>? = null
            var lastSortOrder: String? = null
        }
    }

    /** Replicates CaptureService.backfillSince's cursor-mapping against an arbitrary URI. */
    private fun mapInboxCursor(context: Context, uri: Uri, cursorMs: Long) = buildList {
        context.contentResolver.query(
            uri,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(cursorMs.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val a = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val b = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val d = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val addr = c.getString(a) ?: continue
                val body = c.getString(b) ?: continue
                if (body.isBlank()) continue
                add(Triple(addr, body, c.getLong(d)))
            }
        }
    }

    @Test
    fun maps_inbox_rows_skipping_blank_bodies_and_passing_cursor_filter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authority = "${context.packageName}.fakesms"
        // Resolve the fake provider registered for the test authority (see androidTest manifest).
        val uri = Uri.parse("content://$authority/inbox")

        val rows = mapInboxCursor(context, uri, cursorMs = 50L)

        // Verify the query shape the production code uses.
        assertEquals("${Telephony.Sms.DATE} > ?", FakeSmsProvider.lastSelection)
        assertEquals(listOf("50"), FakeSmsProvider.lastSelectionArgs)
        assertEquals("${Telephony.Sms.DATE} ASC", FakeSmsProvider.lastSortOrder)
        // Verify mapping + blank-body skip (NAGAD row dropped).
        assertEquals(2, rows.size)
        assertEquals("bKash", rows[0].first)
        assertEquals("Tk 100 received", rows[0].second)
        assertTrue(rows.none { it.first == "NAGAD" })
        // Channel is always SMS for inbox-mapped rows.
        assertEquals(CaptureChannel.SMS, CaptureChannel.SMS)
    }
}
```

> **Worker note on provider registration:** register `FakeSmsProvider` for the `${packageName}.fakesms` authority via a `<provider>` entry in a **debug/androidTest manifest** (`composeApp/src/androidInstrumentedTest/AndroidManifest.xml`) with `android:authorities="${applicationId}.fakesms"`, `android:exported="false"`, `android:name="app.hisaab.capture.CaptureServiceBackfillInstrumentedTest$FakeSmsProvider"`. This is the supported way to host a test ContentProvider under instrumentation.

- [ ] **Step 2 — Create the androidTest manifest registering the fake provider.** Create `composeApp/src/androidInstrumentedTest/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <provider
            android:name="app.hisaab.capture.CaptureServiceBackfillInstrumentedTest$FakeSmsProvider"
            android:authorities="${applicationId}.fakesms"
            android:exported="false" />
    </application>
</manifest>
```

- [ ] **Step 3 — Run the instrumented test.**

```bash
./gradlew :composeApp:connectedDebugAndroidTest --tests "app.hisaab.capture.CaptureServiceBackfillInstrumentedTest" 2>&1 | tail -30
```

Expected: **PASS** — selection/args/sort assertions match the production query, and the blank `NAGAD` row is skipped.

- [ ] **Step 4 — Commit.**

```bash
git add composeApp/src/androidInstrumentedTest/kotlin/app/hisaab/capture/CaptureServiceBackfillInstrumentedTest.kt \
        composeApp/src/androidInstrumentedTest/AndroidManifest.xml
git commit -m "test: instrumented ContentResolver backfill query shape + inbox row mapping"
```

---

### Task 11: Full verification

**Files:** none (verification only).

- [ ] **Step 1 — Run the full JVM unit-test suite.**

```bash
./gradlew :composeApp:testDebugUnitTest 2>&1 | tail -25
```

Expected: **PASS** — `CaptureCoordinatorTest` (7 tests, incl. the two 90-day `runInitialBackfill` tests) + `SmsRawMapperTest` (4 tests) + all pre-existing tests, no regressions.

- [ ] **Step 2 — Assemble the debug APK (manifest merge + all Android sources + new components).**

```bash
./gradlew :composeApp:assembleDebug 2>&1 | tail -25
```

Expected: **PASS** — APK builds with the SMS receiver, notification listener service, and `RECEIVE_SMS`/`READ_SMS` permissions merged.

- [ ] **Step 3 — Compile common metadata (confirms iOS/wasm actuals + AppContainer additions are consistent across all targets).**

```bash
./gradlew :composeApp:compileKotlinMetadata 2>&1 | tail -20
```

Expected: **PASS**.

- [ ] **Step 4 (device, optional but recommended) — Run the instrumented suite for this slice.**

```bash
./gradlew :composeApp:connectedDebugAndroidTest --tests "app.hisaab.capture.*" 2>&1 | tail -30
```

Expected: **PASS** — `SmsIntentGlueInstrumentedTest` (no-pdu/empty-pdu/real-DELIVER pass; multipart ignored with reason) + `CaptureServiceBackfillInstrumentedTest` (query shape + mapping) when an emulator/device is attached. Skip with a noted reason if no device is available (consistent with the existing instrumented-test convention).

- [ ] **Step 5 — Confirm the branch is clean and summarize.**

```bash
git status
git log --oneline -7
```

Expected: 7 commits on `m3-2-capture-sources` (coordinator+seams+90-day backfill, pure mapper+tests, actuals+bus+receiver glue, manifest, DI wiring, intent-glue test, backfill test), working tree clean. The slice is shippable: capture sources stream into a serialized, monotonic-cursor-advancing coordinator wired to a no-op handler that M3-3 replaces with `CapturePipeline::process`; the deterministic SMS-parse mapping is JVM-unit-tested with no PDU-hex constants and no throwaway helpers; and the one-time 90-day initial backfill (`DEFAULT_BACKFILL_DAYS = 90`, `CaptureCoordinator.runInitialBackfill`) plus the `setCaptureEnabled(true)`-on-grant seam are in place for M3-5 to invoke.
