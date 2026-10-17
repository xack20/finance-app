# Android Background Capture — Design

**Date:** 2026-10-17 · **Status:** implemented 2026-10-17 (plan `docs/superpowers/plans/2026-10-17-android-background-capture.md`) · **Platform:** Android only (iOS unchanged)

## Intent

The user wants every capture process — history imports, new-SMS processing, email sync, Mac-relay sync —
to keep running while Hisaab is minimized or the phone is locked. Today all of it stops ~30 s after the app
leaves the screen. The user chose **"always on, live"**: a permanent background service with a visible
notification that checks email every 30 s, accepting the battery cost and the ever-present notification.

Test device: realme RMX3910, **Android 16 (SDK 36)**, realme UI 7.0. App `targetSdk` 35.

## Why it stops today

- The encrypted DB, capture coordinator and capture sources live in `AppContainer`
  (`androidMain/.../AppContainerAndroid.kt`), which `MainActivity.onCreate` constructs **per Activity**.
- `AppViewModel.onAppBackground()` → after `lockTimeoutMs` (30 s) → `onLock` → `AppContainer.closeDatabase()`
  → `stopCapture()` cancels the capture scope, closes the driver, zeroes the in-memory master secret.

## Security fact this design rests on

The master secret is stored in `EncryptedSharedPreferences` under a Keystore AES-256-GCM `MasterKey` built
**without** `setUserAuthenticationRequired` (`androidMain/.../platform/SecureStorage.kt`). The fingerprint
gates the **UI**, not the key: the app can already load the secret without the user. So opening the DB in the
background does **not** change at-rest security. What changes: the derived DB key stays in process memory
while the background service runs, not only while the UI is unlocked. The user accepted this tradeoff.

## Goals

1. All capture work runs with the app minimized / phone locked: SMS + email history imports, live SMS,
   email polling (30 s), Mac-relay pull.
2. One DB connection per process, shared by UI and background work, so UI flows see background writes live.
3. The UI lock is unchanged: viewing the app still requires biometric/lock after 30 s in the background.
4. Progress is visible in the notification ("Importing email: 120 of 900").
5. No data loss if Android kills the process: SMS stay in the system inbox and the cursor catch-up recovers
   them; email/relay cursors resume; the pipeline dedups.

## Non-goals

- iOS background capture (unchanged; iOS keeps the Mac relay pull while open).
- Changing at-rest encryption or making the key fingerprint-bound.
- Play Store compliance (special-use FGS justification, SMS permission declaration, battery-exemption policy)
  — tracked as pre-launch tech debt; this build targets sideload testing.

## Anti-goals (failure even if it "works")

- Two DB drivers on the same file (UI would miss background writes; write contention).
- The DB closing under a running import or pipeline write.
- A capture processed twice concurrently (must stay behind the coordinator's single mutex).
- Background work silently dying with no visible state (notification must reflect running/idle/errors).

## Design

### 1. Process-scoped engine (`HisaabEngine`, androidMain)

A process singleton (`HisaabEngine.get(context)`, application context only — no Activity) that owns:
`SecureStorage`, crypto services, DB open/close, capture-side repositories, LLM providers + router, capture
pipeline, capture sources (SMS read/observe, email, relay), the `CaptureCoordinator`, the capture scope and
history import. Everything moved verbatim from `AppContainerAndroid` (same behavior, same comments).

`AppContainerAndroid` keeps only Activity-bound pieces (biometric, pickers, SMS **permission request**,
speech) and **delegates** DB/repo/capture members to the engine. The SMS source is split: reading
(`backfillSince`, `observeIncoming` via `CaptureBus`) moves to the engine; `requestSmsPermission()` stays on
the Activity-bound `CaptureService` (it needs `registerForActivityResult`).

### 2. DB lifetime decoupled from the UI lock

The engine opens the DB when either the UI unlocks **or** the background service starts (loading the master
secret from `SecureStorage`). On UI lock: the lock screen shows as today and the UI's master-secret copy is
zeroed, but the **DB stays open while the background service is running**. If no capture channel is enabled
(service not running), UI lock closes the DB exactly as today.

### 3. Always-on foreground service (`CaptureForegroundService`)

- `foregroundServiceType="specialUse"` — not `dataSync`, which Android 15+ caps at 6 h/day for apps targeting
  SDK 35, and which can't be started from `BOOT_COMPLETED`.
- Runs while **any** capture channel is enabled (SMS, notifications, email, relay) and onboarding is complete
  (master secret stored). `START_STICKY`.
- On start: `startForeground` (low-importance "Sync" channel), open DB via engine, start coordinator + catch-up.
- Notification text: idle → "Watching SMS & email for transactions"; importing → "Importing SMS: X of Y" /
  "Importing email: X of Y" (from `CaptureCoordinator.importProgress` / `EmailCaptureSource.progress`).
- Stops itself when every capture channel is disabled, on sign-out, or if no master secret exists.

### 4. Start triggers

- App unlock/open, and turning on any capture toggle → start service.
- `BOOT_COMPLETED` receiver → start service (allowed for `specialUse`).
- `SmsBroadcastReceiver`: if the service isn't running, try to start it; if Android refuses (background-start
  restriction), do nothing — the SMS stays in the inbox and the next start's catch-up imports it.

### 5. Permissions & settings

Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` (+ subtype property), `POST_NOTIFICATIONS`,
`RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Turning on capture also requests notification
permission (Android 13+) and offers "Allow background running" → the system battery-optimization exemption
dialog (realme otherwise kills background apps).

### 6. UI

Auto-capture screen: drop the "Keep Hisaab open while importing" hint and `KeepScreenOn` (no longer needed);
progress lines stay. Add a "Background running: allowed / tap to allow" row.

### 7. Per-channel sync status (user request: "check both SMS and email are syncing or not")

Today the user can't tell whether a channel is working: progress shows only while an import runs, and success
is never logged. Each channel (SMS, email, Mac relay) publishes a status the screen and notification render:

- `Off` · `Importing X of Y` · `Up to date · last checked HH:MM` · `Error: <reason>` (e.g. the IMAP login
  failure text, "SMS permission revoked"), cleared on the next successful pass.
- Shown as one line per channel on the Auto-capture screen; the notification shows the most important one
  (an error, else an import, else "Watching SMS & email").
- Positive logcat lines per pass (`HisaabEmail`: page read/captured/remaining; `HisaabCapture`: import
  done/total) so on-device verification over adb doesn't depend on the UI.

## Decided by the user

- Always-on live sync with a permanent notification (over 15-min periodic or imports-only).
- Background DB access without the fingerprint, UI lock unchanged.
- **CPU awake only during imports:** a partial wake lock is held while an SMS/email history import runs (so it
  finishes with the screen off) and released when caught up. Idle email/relay polling is every 30 s while the
  device is awake and best-effort while it sleeps (Doze may stretch it to minutes). No permanent wake lock.
  New SMS stay instant (broadcast-driven). `WAKE_LOCK` permission added.
- Today's uncommitted work is committed first; this feature builds on that clean base.

## Calls made after the builder check (mine)

- **SMS cursor only moves for SMS.** Today `CaptureCoordinator.process` advances the single `last_sms_cursor`
  with every source's `receivedAt` (email, relay, notifications), so a later email can push the cursor past an
  SMS that missed the live bus — losing it for good once the service is always on. Only `CaptureChannel.SMS`
  captures advance it (email has its own UID cursor; relay deletes rows on ingest; notifications have no
  history). No migration needed. Plus a periodic SMS catch-up that re-reads the last 24 h before the cursor
  (dedup makes overlap free), so an SMS dropped from `CaptureBus` during a long import is still recovered.
- **Which errors surface.** Actionable, persistent ones: IMAP login rejected, SMS permission revoked, LLM key
  missing/rejected, relay session expired. Network failures surface only after 3 consecutive failed passes
  (~90 s), so the notification doesn't flicker on blips. Cleared on the next successful pass.
- **Notification permission denied (Android 13+).** Capture still turns on; the Auto-capture screen shows a
  warning row ("Notifications are off — sync runs but you can't see its status; tap to allow"). The per-channel
  status lines (§7) keep it from being invisible.
- **Tests vs a process singleton.** The engine gets a test-only reset so instrumented tests stay isolated.

## Builder's calls (marked as mine)

- Engine as a hand-wired singleton (matches the codebase's no-DI-framework `AppContainer` style).
- Notification wording and channel importance.
- Whether `AppViewModel`'s lock path asks the engine "is background capture active?" or the engine owns that
  decision — whichever keeps `AppViewModel` platform-free.

## Testing

- Unit (commonTest/JVM): lock decision (close DB only when background capture inactive); service start/stop
  conditions from config + secret presence; notification text from progress states.
- On device (adb on the RMX3910): service running in `dumpsys activity services`; import continues with the
  screen off; a test SMS processed while locked; process kill → restart → catch-up; no crash entries.

## Risks

- Battery use (30 s email polling, permanent process). Accepted by the user.
- realme/ColorOS may still kill the service without the battery exemption; the "Allow background" row
  mitigates this.
- Play policy (special-use FGS, battery exemption) — pre-launch tech debt.
- Refactor size: `AppContainerAndroid` (~450 lines) splits; behavior must stay identical for the UI.
