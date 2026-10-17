# Android Background Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep SMS/email history imports, live SMS, email polling and Mac-relay sync running while Hisaab is minimized or the phone is locked, with per-channel sync status visible in the app and in a permanent notification.

**Architecture:** Move the DB + capture engine out of the per-Activity `AppContainerAndroid` into a process singleton `HisaabEngine`; an always-on `specialUse` foreground service keeps the process alive, opens the DB with the stored master secret, and drives capture; the UI lock stops closing the DB while that service runs. Pure, testable logic (status model, cursor rules, import resume, login guard, start policy) lives in `commonMain`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Android Service/Notification/PowerManager APIs, SQLDelight+SQLCipher, javax.mail (android-mail), kotlinx-coroutines, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-10-17-android-background-capture-design.md`

## Global Constraints

- Android only; iOS behavior unchanged (iOS actuals get no-op/Off implementations so it compiles).
- Foreground service type **`specialUse`** (not `dataSync`); subtype text: "Continuously syncs the user's bank SMS and email into their private, encrypted ledger".
- Email/relay poll every **30 s** while awake; partial wake lock **only while an import runs** (tag `hisaab:import`, 2 h safety timeout); no permanent wake lock.
- SMS cursor advances **only for `CaptureChannel.SMS`** captures; catch-up re-reads **24 h** before the cursor; periodic SMS catch-up every **15 min** while the service runs.
- Network failures surface as a status error only after **3 consecutive** failed passes; auth/permission/key errors surface immediately.
- One DB driver per process. UI lock keeps the DB open while background capture runs; otherwise it closes it exactly as today.
- Status labels: `Off` · `Importing X of Y` · `Up to date · last checked HH:MM` · `Error: <reason>`; idle notification text: `Watching SMS & email for transactions`.
- **Never run `connectedDebugAndroidTest` against the user's phone** (AGP uninstalls the app afterwards, wiping their encrypted ledger). On-device checks use `composeApp/publish-apk.sh` + `adb install -r` only.
- Every Android build is published with `composeApp/publish-apk.sh` (numbered APK to Drive).
- Test command prefix: `export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/21.0.10-amzn}"`; `ADB=~/Library/Android/sdk/platform-tools/adb`.

## Review Focus

1. **Process killed mid SMS import** (realme battery manager): on next service start the import resumes by itself — Task 1 test `importAllHistory resumes after being interrupted`.
2. **Wrong Gmail app password:** status shows the login error at once and the app stops hammering Gmail every 30 s until the credentials change — Task 4 tests on `ImapLoginGuard`.
3. **Phone locked during an import:** the import keeps going (DB stays open, wake lock held) — Task 5 device check step.
4. **Notification permission denied:** capture still turns on, the screen shows a warning row — Task 6 test `denied notification permission raises the warning but keeps capture on`.
5. **Service started from several places at once** (app open + boot + SMS broadcast): exactly one capture scope runs — Task 5 device check (`dumpsys` shows one service; no duplicated log lines per pass).

---

### Task 1: Coordinator — SMS-only cursor, catch-up overlap, resumable import

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/capture/CaptureCoordinator.kt`
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/capture/CaptureCoordinatorTest.kt`

**Interfaces:**
- Produces: `const val CATCH_UP_OVERLAP_MS = 24L * 60 * 60 * 1000`; `interface ImportStateStore { suspend fun isImportPending(): Boolean; suspend fun setImportPending(pending: Boolean) }`; new constructor param `importState: ImportStateStore` (default: in-memory no-op store that reports `false`); `suspend fun resumePendingImport()`; `suspend fun catchUp()` now drains from `max(0, cursor - CATCH_UP_OVERLAP_MS)`.

- [ ] **Step 1: Write the failing tests** (in `CaptureCoordinatorTest`)
  - `email and relay-style captures never move the SMS cursor`: start live collection (UnconfinedTestDispatcher) with cursor 100; emit `RawCapture(channel = EMAIL, receivedAt = 9_000)` → `assertEquals(100L, config.cursor)`; then an SMS at 500 → `assertEquals(500L, config.cursor)`.
  - `catchUp re-reads the 24 hours before the cursor`: cursor `= 2 * CATCH_UP_OVERLAP_MS` → `assertEquals(CATCH_UP_OVERLAP_MS, source.lastBackfillCursor)`; cursor `= 1_000` → `assertEquals(0L, …)`.
  - `importAllHistory resumes after being interrupted`: fake `ImportStateStore`; handler throws `CancellationException` on item 2 → `assertTrue(store.pending)`; then `resumePendingImport()` with a normal handler processes items and leaves `store.pending == false`.
  - `resumePendingImport does nothing when no import is pending`: `source.lastBackfillCursor == null`.
  - Update the existing `catchUp backfills since stored cursor…` expectation from `50L` to `0L` (overlap floor).
- [ ] **Step 2:** Run `./gradlew :composeApp:testDebugUnitTest --tests 'app.hisaab.capture.CaptureCoordinatorTest'` — expect compile failure (`CATCH_UP_OVERLAP_MS`, `ImportStateStore` unresolved).
- [ ] **Step 3: Implement.** In `process`: advance only when `raw.channel == CaptureChannel.SMS` (comment `shortcut: relay-forwarded SMS also carry channel SMS; harmless on Android where the Mac relay isn't used — tag captures by source if that changes`). `importAllHistory`: `setImportPending(true)` before draining, `setImportPending(false)` only after the drain completes (not in `finally`). `resumePendingImport()`: `if (importState.isImportPending()) importAllHistory()`.
- [ ] **Step 4:** Re-run the test class — all PASS; then the full `testDebugUnitTest` — 0 failures.
- [ ] **Step 5: Commit** `fix(capture): SMS-only cursor, 24h catch-up overlap, resumable history import`.

### Task 2: Sync status model (pure)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/capture/SyncStatus.kt`
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/capture/SyncStatusTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface ChannelStatus { data object Off; data class Importing(val done: Int, val total: Int); data class UpToDate(val lastCheckedMs: Long); data class Error(val reason: String) }`
  - `data class SyncStatuses(val sms: ChannelStatus = ChannelStatus.Off, val email: ChannelStatus = ChannelStatus.Off, val relay: ChannelStatus = ChannelStatus.Off)`
  - `class ChannelStatusTracker(private val now: () -> Long, private val networkFailureThreshold: Int = 3) { val status: StateFlow<ChannelStatus>; fun off(); fun importing(done: Int, total: Int); fun succeeded(); fun failed(reason: String, persistent: Boolean) }`
  - `fun ChannelStatus.label(timeZone: TimeZone = TimeZone.currentSystemDefault()): String`
  - `fun notificationText(statuses: SyncStatuses): String`

- [ ] **Step 1: Write the failing tests** (`SyncStatusTest`, fixed clock `now = { 1_700_000_000_000L }` → 22:13 UTC):
  - `succeeded reports up to date at the check time` → `UpToDate(1_700_000_000_000L)`.
  - `a persistent failure shows at once` → `failed("Email login rejected", persistent = true)` → `Error("Email login rejected")`.
  - `transient failures show only after three in a row` → after 2 calls status unchanged (`UpToDate`), after the 3rd `Error("timeout")`; a `succeeded()` in between resets the count.
  - `labels`: `Off.label()=="Off"`, `Importing(120, 900).label()=="Importing 120 of 900"`, `UpToDate(1_700_000_000_000L).label(TimeZone.UTC)=="Up to date · last checked 22:13"`, `Error("x").label()=="Error: x"`.
  - `notification prefers errors, then imports, then the idle line` → `SyncStatuses(sms = Importing(1, 9), email = Error("bad password"))` → `"Email: bad password"`; `SyncStatuses(email = Importing(120, 900))` → `"Importing email: 120 of 900"`; all `UpToDate`/`Off` → `"Watching SMS & email for transactions"`. Channel order for ties: SMS, email, relay; relay name in text: `"Mac relay"`.
- [ ] **Step 2:** Run `--tests 'app.hisaab.capture.SyncStatusTest'` — compile failure.
- [ ] **Step 3: Implement** in `SyncStatus.kt` (HH:MM via `kotlinx.datetime.Instant.fromEpochMilliseconds(..).toLocalDateTime(timeZone)`, zero-padded).
- [ ] **Step 4:** Test class PASS; full suite 0 failures.
- [ ] **Step 5: Commit** `feat(capture): per-channel sync status model`.

### Task 3: Extract `HisaabEngine` (behavior-identical refactor)

**Files:**
- Create: `composeApp/src/androidMain/kotlin/app/hisaab/engine/HisaabEngine.kt`
- Create: `composeApp/src/androidMain/kotlin/app/hisaab/capture/SmsCaptureSource.kt`
- Modify: `composeApp/src/androidMain/kotlin/app/hisaab/AppContainerAndroid.kt`, `composeApp/src/androidMain/kotlin/app/hisaab/platform/CaptureService.kt`

**Interfaces:**
- Produces: `class HisaabEngine private constructor(context: Context, authRepository: AuthRepository)` with `companion object { fun get(context: Context, authRepository: AuthRepository? = null): HisaabEngine; @VisibleForTesting fun resetForTests() }`. It owns, moved verbatim with their comments from `AppContainerAndroid`: `secureStorage`, `cryptoService`, `mnemonicService`, `blobCrypto`, `fileStore`, `authRepository`, `databaseDriverFactory`, the cached driver/DB/secret and `openDatabase`/`closeDatabase`/`databaseOrNull`/`masterSecretInMemory`, every repository getter, `audioRecorder`, `cloudTranscriber`, LLM providers + `llmRouter`, `agentRuntime()`, `captureEvents`, `capturePipeline`, `captureCoordinator`, relay/email sources, `startCapture`/`stopCapture`/`startHistoryImport`, `confirmCandidate`/`confirmCandidateWithEdits`, `emailImportProgress`.
- `class SmsCaptureSource(context: Context) : CaptureSource` — `capabilities()`, `observeIncoming()` (CaptureBus), `backfillSince()` and `suspend fun hasSmsPermission()` moved from `CaptureService`; the engine's composite uses it. Android `CaptureService` keeps the Activity-bound launchers and delegates reading to `SmsCaptureSource`.
- `AppContainerAndroid` keeps `biometricAuth`, `contactPicker`, `imagePicker`, `captureService`, `speechToText`, `lifecycle`, crash-report members; every other `actual` delegates (`actual val accountRepository get() = engine.accountRepository`, `actual fun openDatabase(s) = engine.openDatabase(s)`, …).

- [ ] **Step 1:** Move the code (no behavior change). `MainActivity` is unchanged: `AppContainer(applicationContext, this)` now obtains `HisaabEngine.get(context, authRepository)`.
- [ ] **Step 2:** Run `./gradlew :composeApp:testDebugUnitTest :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileDebugAndroidTestKotlinAndroid` — BUILD SUCCESSFUL, 0 test failures. Instrumented tests that build `AppContainer` call `HisaabEngine.resetForTests()` in `@Before`.
- [ ] **Step 3: Device smoke check:** `composeApp/publish-apk.sh && $ADB install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk`; ask the user to unlock, open Today/Settings → Auto-capture, toggle a setting; `$ADB logcat -b crash -d | grep -c FATAL` → `0`.
- [ ] **Step 4: Commit** `refactor(android): move DB + capture engine into process-scoped HisaabEngine`.

### Task 4: Sources publish status; login guard; positive logs

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/capture/ImapLoginGuard.kt`
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/capture/ImapLoginGuardTest.kt`, `composeApp/src/commonTest/kotlin/app/hisaab/capture/RelayCaptureSourceTest.kt` (create if absent)
- Modify: `composeApp/src/androidMain/kotlin/app/hisaab/capture/EmailCaptureSource.kt`, `composeApp/src/commonMain/kotlin/app/hisaab/capture/RelayCaptureSource.kt`, `composeApp/src/androidMain/kotlin/app/hisaab/engine/HisaabEngine.kt`, `composeApp/src/commonMain/kotlin/app/hisaab/AppContainer.kt` (+ both actuals)

**Interfaces:**
- Consumes: `ChannelStatusTracker`, `SyncStatuses` (Task 2); `CaptureCoordinator.importProgress` (existing).
- Produces: `class ImapLoginGuard { fun shouldAttempt(credentialsKey: String): Boolean; fun recordAuthFailure(credentialsKey: String); fun recordSuccess() }`; `RelayCaptureSource(…, status: ChannelStatusTracker? = null)`; `EmailCaptureSource(…, status: ChannelStatusTracker)` (keeps `progress` until Task 6); `HisaabEngine.syncStatuses: StateFlow<SyncStatuses>`; common `AppContainer.syncStatuses: StateFlow<SyncStatuses>` added alongside `emailImportProgress` (iOS: `MutableStateFlow(SyncStatuses())`) — Task 6 removes `emailImportProgress` and `EmailCaptureSource.progress` once the screen reads `syncStatuses`.

- [ ] **Step 1: Write the failing tests:**
  - `ImapLoginGuardTest`: `a rejected login blocks retries with the same credentials` (`recordAuthFailure("a:pw")` → `shouldAttempt("a:pw") == false`); `new credentials are tried again` (`shouldAttempt("a:pw2") == true`); `a success clears the block`.
  - `RelayCaptureSourceTest`: `relay status is Off when disabled` (enabled = false → `Off`); `a successful drain reports up to date`; `three failed fetches in a row surface an error` (transport throws 3× → `Error`).
- [ ] **Step 2:** Run both test classes — compile failure.
- [ ] **Step 3: Implement.**
  - `EmailCaptureSource.drainPage`: disabled/no creds → `status.off()`; `credentialsKey = "${cfg.email}:${cfg.password}".hashCode().toString()`; guard blocks → return false; `javax.mail.AuthenticationFailedException` → `guard.recordAuthFailure(key)`, `status.failed("Email login rejected — check the app password", persistent = true)`; other failures → `status.failed(e.message ?: "Email check failed", persistent = false)`; success → `guard.recordSuccess()` then `importing(done, total)` or `succeeded()`. Log per page: `Log.i("HisaabEmail", "page read=… captured=… remaining=…")`.
  - Relay: off/succeeded/failed(transient) in `drain()`.
  - Engine SMS tracker: `!captureEnabled` → `off()`; collect `captureCoordinator.importProgress` → `importing`/`succeeded()`; after each `catchUp()` → `succeeded()`; no SMS permission → `failed("SMS permission is off — tap Grant", persistent = true)`. Log `Log.i("HisaabCapture", "sms import $done/$total")` every 100 items and on completion.
  - `syncStatuses = combine(sms.status, email.status, relay.status, ::SyncStatuses).stateIn(engineScope, Eagerly, SyncStatuses())`.
- [ ] **Step 4:** Test classes PASS; full suite + iOS compile succeed.
- [ ] **Step 5: Commit** `feat(capture): publish per-channel sync status; stop retrying rejected IMAP logins`.

### Task 5: Always-on foreground service; DB survives the UI lock

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/capture/BackgroundPolicy.kt`, test `composeApp/src/commonTest/kotlin/app/hisaab/capture/BackgroundPolicyTest.kt`
- Create: `composeApp/src/androidMain/kotlin/app/hisaab/engine/CaptureForegroundService.kt`, `composeApp/src/androidMain/kotlin/app/hisaab/engine/BootReceiver.kt`
- Modify: `composeApp/src/androidMain/AndroidManifest.xml`, `HisaabEngine.kt`, `SmsBroadcastReceiver.kt`, `AppContainer.kt` (+ actuals), `App.kt`

**Interfaces:**
- Consumes: `SyncStatuses`, `notificationText` (Task 2); `HisaabEngine.syncStatuses` (Task 4); `CaptureCoordinator.resumePendingImport()` (Task 1).
- Produces: `fun backgroundCaptureWanted(cfg: CaptureConfig?, hasMasterSecret: Boolean): Boolean`; `HisaabEngine.backgroundActive: StateFlow<Boolean>`, `suspend fun startBackground(): Boolean`, `fun stopBackground()`; `object CaptureServiceStarter { fun start(context: Context) }` (wraps `ContextCompat.startForegroundService` in `runCatching`); common `AppContainer.lockUi()` (Android: keep the DB open when `backgroundActive`, only zero the UI's master-secret copy; else `closeDatabase()`; iOS: `closeDatabase()`).

- [ ] **Step 1: Write the failing test** `BackgroundPolicyTest`: `no secret means no background capture`; `null config means no background capture`; `any one channel on is enough` (each of captureEnabled / email / relay / notification alone → `true`); `every channel off stops it`.
- [ ] **Step 2:** Run it — compile failure.
- [ ] **Step 3: Implement.**
  - Service: `onStartCommand` → `startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` first, then `engine.startBackground()`; `false` → `stopSelf()`. Return `START_STICKY`. Collect `syncStatuses` → update notification text; hold/release the `hisaab:import` partial wake lock while any status is `Importing`.
  - `startBackground()`: load the master secret from `SecureStorage`; `backgroundCaptureWanted(cfg, secret != null)` else return false; `openDatabase(secret)` if closed; `startCapture()`; launch a loop: `resumePendingImport()`, then every 15 min `catchUp()`. Idempotent.
  - Channel `sync` (`IMPORTANCE_LOW`, name "Background sync"), ongoing, small icon `android.R.drawable.stat_notify_sync`, tap opens `MainActivity`.
  - Manifest: permissions `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; `<service … android:foregroundServiceType="specialUse" android:exported="false">` with `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="Continuously syncs the user's bank SMS and email into their private, encrypted ledger"/>`; `BootReceiver` for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.
  - Starters: `AppContainer.startCapture()` (UI path) and `BootReceiver` call `CaptureServiceStarter.start`; `SmsBroadcastReceiver` calls it after publishing.
  - `App.kt`: `onLock = { container.lockUi() }`. `openDatabase` on an already-open DB refreshes the UI's secret copy instead of returning early without it.
- [ ] **Step 4:** Policy test PASS; full suite + iOS + androidTest compile succeed.
- [ ] **Step 5: Device checks** (publish + `adb install -r`; user enables SMS capture once):
  - `$ADB shell dumpsys activity services app.hisaab | grep -c CaptureForegroundService` → `1` (Review Focus 5).
  - Start an import, lock the phone ~2 min: `$ADB logcat -d -s 'HisaabCapture:*' 'HisaabEmail:*'` shows progress lines continuing while locked (Review Focus 3).
  - `$ADB shell am kill app.hisaab` mid-import → service restarts (START_STICKY) and logs show the import resuming (Review Focus 1).
  - `$ADB reboot`; after boot, the service is running without opening the app.
  - `$ADB logcat -b crash -d | grep -c FATAL` → `0`.
- [ ] **Step 6: Commit** `feat(android): always-on capture service; DB stays open across the UI lock`.

### Task 6: Auto-capture UI — status lines, notification + background permissions

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/platform/CaptureService.kt` (+ Android/iOS actuals), `AutoCaptureViewModel.kt`, `AutoCaptureScreen.kt`, `MainGraph.kt`
- Delete: `KeepScreenOn.kt` (common) and its 3 actuals
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/screens/settings/AutoCaptureViewModelTest.kt`

**Interfaces:**
- Consumes: `AppContainer.syncStatuses`, `ChannelStatus.label()` (Tasks 2/4).
- Removes: `AppContainer.emailImportProgress` (+ actuals), `EmailCaptureSource.progress`, VM `smsImportProgress`/`emailImportProgress`.
- Produces: `CaptureService.requestNotificationPermission(): Boolean` (Android 13+ launcher registered at construction; below 33 → `true`; iOS → `true`), `isBackgroundRunningAllowed(): Boolean` (PowerManager), `openBackgroundRunningSettings()` (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `package:` URI); VM params `requestNotificationPermission: suspend () -> Boolean = { true }`, `syncStatuses: StateFlow<SyncStatuses>` (replace `smsImportProgress`/`emailImportProgress`); VM flow `notificationsDenied: StateFlow<Boolean>`.

- [ ] **Step 1: Write the failing tests:** `granting SMS capture also asks for notification permission` (counter == 1); `denied notification permission raises the warning but keeps capture on` (`notificationsDenied.value == true` and `captureEnabled == true` in the DB) (Review Focus 4).
- [ ] **Step 2:** Run the VM test class — compile failure.
- [ ] **Step 3: Implement.** Screen: three status lines (`SMS`, `Email`, `Mac relay` + `label()`, error lines in `palette.negative`); "Background running" row (`Allowed` / `Tap to allow` → `openBackgroundRunningSettings()`); notifications-off warning row ("Notifications are off — sync runs but you can't see its status. Tap to allow."); remove `KeepScreenOn`, the "Keep Hisaab open…" hint and `ImportProgressLine`; the Import button disables while `sms is Importing`.
- [ ] **Step 4:** VM tests PASS; full suite + iOS + androidTest compile succeed.
- [ ] **Step 5: Commit** `feat(settings): live per-channel sync status and background-running controls`.

### Task 7: Docs, tech debt, publish

**Files:**
- Modify: `docs/tech-debt.md`, `docs/superpowers/specs/2026-10-17-android-background-capture-design.md` (Status → implemented), `CLAUDE.md` (architecture: engine + service in two lines)

- [ ] **Step 1:** Add tech-debt rows: **H9** special-use FGS + `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` need Play policy justification (pre-launch); **M19** relay-forwarded SMS still advance the SMS cursor (Task 1 shortcut); **L7** notification small icon is the system sync glyph.
- [ ] **Step 2:** `composeApp/publish-apk.sh`; `$ADB install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk`; tell the user the new APK name.
- [ ] **Step 3: Commit** `docs: background capture shipped — tech debt + CLAUDE.md`.
