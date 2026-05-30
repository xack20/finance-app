# Hisaab — Tech Debt & Carryover

**Status:** Tracking doc — items here are deliberate deferrals or known gaps from prior phases.
**Last updated:** 2026-05-31 (M4 — conversational agent: M4-1…M4-4 + M4-6 shipped; + a full carry-forward / tech-debt sweep)
**Convention:** Each item carries a target phase. Items unresolved by v1 launch must move to Phase 2 backlog.

> **M3 shipped (2026-05-29):** SMS capture (Android: receiver + backfill + NotificationListener, lock-safe catch-up), tiered parsing (deterministic pre-filter → 6 bank templates → LLM), on-device (MediaPipe Gemma) + cloud BYO-key (Claude/Gemini/OpenAI) providers with redaction + consent, confidence-gated auto-post + Review inbox, Settings/onboarding UX. 239 unit tests + instrumented suites green. M3 closed no prior debt (net-new feature); its residuals are tracked as H7–H8, M13–M17, L3 below.

> **M4 shipped (2026-05-30→31):** conversational money agent — `AgentProvider.complete()` + `AgentLoop` (ReAct, db-free) + read tools + `WriteBatchCommitter` (atomic FK-ordered Apply) + agent persistence (`5.sqm`) + credit cards (`6.sqm`, signed `cardOutstanding`, `CardSummaryCalculator`) + `AgentScreen`/`ReviewCard`/`AgentViewModel` + DI + paired-leg transfer entry. A subsequent tech-debt sweep (2026-05-31) closed the items in **Closed → M4 sweep** below.

---

## Critical (open)

_(none — all P0a/P0b critical items closed by P0c; see `## Closed` below)_

---

## High (open)

Important gaps but not blocking ongoing development.

| # | Item | Origin | Notes | Target |
|---|---|---|---|---|
| H1 | `iOS SecureStorage` is a stub — load/save are no-ops | P0b T6 | Keychain cinterop deferred; iOS users cannot persist `master_secret`. (iOS now compiles, so this can be implemented + simulator-verified; security-critical, so left as an honest stub rather than shipping unverified cinterop) | P0d / M4-0 |
| H2 | ~~iOS app never run on simulator~~ **RESOLVED (M4 sweep)** | P0a T23/T26 | `iosApp/project.yml` (xcodegen) generates the Xcode project + links the static `ComposeApp.framework`. App **builds + launches on the iPhone 17 simulator** and renders the Compose Welcome screen. See **S10** below | done |
| H3 | wasmJs target **gated off** (`-Phisaab.enableWasm`, default off) | P0a T29; M4 sweep | Root cause pinned: **libsodium (all crypto) publishes no wasmJs artifact in any version** — a hard upstream blocker. SQLDelight fixed via the 2.1.0 bump (publishes wasm-js variants). Gating it off also clears the KMP-metadata resolution errors that polluted the iOS build. Re-enable when upstream ships wasm crypto, or add a WebCrypto/libsodium.js expect-actual seam | P0d / upstream |
| H5 | Real BD SMS provider (SSL Wireless / Infobip) not wired — using Supabase Test Mode | P0b T8 | Blocks public beta but not internal dev | P0d/P0e |
| H6 | 16 KB page-size alignment — `libsodium.so`, `libsqlcipher.so`, `libjnidispatch.so`, `libandroidx.graphics.path.so`, and now MediaPipe's `libllm_inference_engine_jni.so` not aligned | P0b emulator test; M3-4 | Runs in compatibility mode on Android 15+ pixel devices; breaks under future Android requiring 16 KB | P0d |
| H7 | On-device Gemini Nano (AICore) is a runtime `Class.forName` availability probe only — no actual Nano inference path; `play-services-aicore:16.0.0-alpha05` artifact does not exist in Google Maven | M3-4 | MediaPipe Gemma is the real on-device path; Nano returns unavailable on all devices. Wire the real AICore path when a valid GA artifact exists | when AICore GA |
| H8 | Google Play `READ_SMS`/`RECEIVE_SMS` Permissions Declaration / rejection risk | M3 spec §10 | Launch-gate for public release; `NotificationListenerService` fallback keeps the app functional if SMS access is rejected; no blocker for internal dev / sideload | pre-launch |

---

## Medium (track for P0d+)

| # | Item | Origin | Notes | Target |
|---|---|---|---|---|
| M1 | No locale switching wired — UI strings are hardcoded English | P0b T12 | `compose-resources` is added to deps but `stringResource()` not used; locale stored in `UserProfile` is unused | P0d |
| M2 | `AppViewModel.lockTimeoutMs` is fixed at construction — Settings UI persists the choice but it only applies on next app start | P0c-3 T22 | Make AppViewModel re-read on background or expose mutable lockTimeout | P0d |
| M3 | No accessibility audit — VoiceOver / TalkBack untested | P0b | Accessibility pass deferred per S8 spec | P0e |
| M4 | iOS DB encryption: API fixed, but **SQLCipher binary not yet linked** | P0b T3; M4 sweep | The driver now uses the correct sqliter-1.3.3 `encryptionConfig = Encryption(key)` and **compiles**, but sqliter links the *system* libsqlite3 where `PRAGMA key` is a NO-OP → the DB is NOT actually encrypted. iOS must NOT ship to users until SQLCipher is linked (CocoaPods `pod 'SQLCipher'` + SQLDelight `linkSqlite=false`) and verified on a simulator. Loudly commented in DatabaseDriverFactory.ios | P0d / M4-0 |
| M5 | Web `WebWorkerDriver` (sql.js) never tested in browser | P0b T3 | Web is viewer-only in v1 | P0d |
| M6 | Screenshot prevention on `RecoveryPhraseScreen` and `RecoveryPhraseRevealScreen` not implemented — Android `FLAG_SECURE`, iOS blur overlay | P0b/P0c-3 | Recovery phrase visible to screenshots / screen recording | P0d |
| M7 | Argon2id timing benchmark on low-end devices — may need to drop to `m=32MB` if P99 > 3 s | P0b spec §13 | Currently uses `MEMLIMIT_MODERATE` (~64 MB) | P0d |
| M8 | `gradle/gradle-daemon-jvm.properties` toolchain set to JDK 21 only (any vendor) — works locally but CI must pin JDK 21 | P0b emulator session | Update CI config when CI exists | when CI exists |
| M9 | `OnboardingViewModelTest` and `SettingsViewModelTest` are `@Ignore`d — both depend on `AppContainer` which has Android-specific actuals | P0c-1 T7 / P0c-3 T22 | Either expose a fakeable AppContainer abstraction in commonTest, or write instrumented tests | P0d |
| M10 | EntryScreen date/time picker is a static "now" field — tap is no-op | P0c-2 T19 | Wire material3 DatePicker / TimePicker bottom sheets | P0d polish |
| M11 | EntryScreen merchant input has no autocomplete dropdown — `MerchantRepository.searchByPrefix` exists but not wired | P0c-2 T19 | Wire `ExposedDropdownMenu` over the suspending search | P0d polish |
| M12 | TransactionDetailScreen edit button doesn't exist; only delete works | P0c-2 T19 | Add edit route reusing EntryScreen with prefilled state | P0d |
| M13 | MediaPipe Gemma model (`gemma-3-1b-it-int4.task`) is NOT bundled and there is no in-app download flow | M3-4 | `ModelManager.isAvailable()` returns false until a model file is manually present, so on-device LLM is inert by default; cloud BYO-key + templates cover v1. Wire a WorkManager/Wi-Fi-gated model download | M-later |
| M14 | iOS SMS capture unavailable (Apple restriction) — `PASTE` channel + paste/share-sheet intake UI stubbed; iOS on-device `createOnDeviceProvider()` returns null (Foundation Models not wired) | M3 spec §6/§9 | Build the iOS paste intake + Foundation Models provider in a later slice | later M3 / P0d |
| M15 | Email / Voice / Receipt-OCR capture engines deferred | M3 spec §2 | The `LlmProvider`/`CaptureSource` abstractions are built to reuse; only the SMS slice shipped | later M3 |
| M16 | CDN-delivered parser-pattern updates (`parser_patterns_versions`) deferred — bank templates ship in-app | M3 spec §7 | New bank/format support requires an app release until the publish pipeline exists | post-launch |
| M17 | `Redactor` masks an unformatted 8+ digit amount as an account number | M3-4 review | Rare (BD SMS amounts are usually comma-formatted) but would drop the amount field for cloud parsing of such messages; documented by a test. Tighten the heuristic with context | P0d polish |

---

## Low (Phase 2 candidates)

| # | Item | Origin | Notes | Target |
|---|---|---|---|---|
| L1 | Release build has `isMinifyEnabled = false` — SQLCipher key-handling code ships unobfuscated | P0b T3 review | Release builds far away; track for pre-launch | pre-launch |
| L5 | Gradle **configuration cache** intermittently fails serializing AGP `JdkImageInput` (`:composeApp:androidJdkImage`), triggered by a "JVM has changed" invalidation | M4 sweep | Pre-existing AGP×config-cache flake, **not** introduced by M4 (a `--no-configuration-cache` run is clean). Workaround: that flag. Revisit on the next AGP bump | when AGP fixes |
| L3 | Two `AutoCaptureViewModel` instances exist when navigating AutoCaptureScreen → CloudConsentScreen | M3-5 review | Benign given the reactive DB-backed config flow keeps both consistent; consolidate via a shared nav-scoped VM if consent gains in-memory state | P0d polish |
| L4 | `AndroidOnDeviceProvider.createOnDeviceProvider()` caches a single engine but its `redact` lambda is bound on first construction — a second `AppContainer` (test only) reuses the first's lambda | M3-4 review | Test-only hazard; production has one `AppContainer`. Add a cache-reset seam if multi-container tests are added | when needed |

---

## Closed

### Closed in P0c (2026-05-28 → 2026-05-29)

| # | Item | Resolved by | Verified |
|---|---|---|---|
| C1 | `DatabaseDriverFactory.createDriver()` never called after onboarding completes | P0c-1 T5 + T7, P0c-2 T16 | `LockScreen.attemptUnlock` / `OnboardingViewModel.completeProfile` / `RecoveryEntryScreen.attemptRestore` all call `container.openDatabase(secret)`; default Cash + 12 categories seeded on first open |
| C2 | `UserProfile` from `ProfileSetupScreen` discarded | P0c-1 T7 | `OnboardingViewModel.completeProfile` inserts `user_profile` row via `db.hisaabDatabaseQueries.insertUserProfile` |
| C3 | `App.kt` uses `StubAuthRepository` instead of real Supabase OTP | P0c-1 T8 | `StubAuthRepository.kt` deleted; `AppContainer.authRepository = SupabaseAuthRepository()` |
| C4 | No DI | P0c-1 T5 | Hand-wired `AppContainer` + `LocalAppContainer` composition local; all screens consume `LocalAppContainer.current` |
| C5 | `master_secret` only in memory — never persisted | P0c-1 T7 | `OnboardingViewModel.enrollBiometric` / `skipBiometric` both call `container.secureStorage.storeMasterSecret(secret)` |
| C6 | Biometric Enable/Skip paths identical | P0c-1 T7 | Enable path now calls `container.biometricAuth.authenticate("Set up Hisaab", "Confirm to enable biometric unlock")` |
| C7 | `MnemonicService` uses XOR checksum instead of standard BIP39 SHA256 | P0c-1 T2 | Switched to `Hash.sha256(entropy)`; P0c-3 T23 instrumented test verifies BIP39 canonical vector (all zeros → 23×abandon + art) on real device |
| C8 | `OnboardingKey` state has no UI | P0c-1 T9 | New `RecoveryEntryScreen` — 24-input grid, BIP39 word validation, decode + open DB |
| C9 | App lifecycle never triggers Locked | P0c-1 T4 + T6 + T8 | `AppLifecycle` expect/actual + `ProcessLifecycleOwner` Android observer; `AppViewModel.lockTimeoutMs` timer (30s default); `App.kt` collects `container.lifecycle.events()` |
| H4 | `CryptoServiceTest` marked `@Ignore` due to libsodium native lib unavailable in JVM | P0c-3 T23 | 12 instrumented tests under `androidInstrumentedTest` all pass on Pixel_10_Pro emulator (4 CryptoService + 5 MnemonicService + 3 BlobCrypto) |

### Closed in M4 sweep (2026-05-31)

| # | Item | Resolved by | Verified |
|---|---|---|---|
| S1 | `kotlinx-datetime` used in 26 commonMain files but never declared — floated to 0.7.1 on iOS (where `Clock.System` was removed) → the iOS compile failure | Pinned `kotlinx-datetime = 0.6.1` in `commonMain` | Android 307 unit tests green; iOS `Clock.System` errors gone (per-target compile) |
| S2 | JVM-only `"%.2f".format()` in `commonMain/AccountsScreen.kt` broke iOS/wasm | Added multiplatform `Double.toMoneyString()`; replaced both call sites | Compiles; helper is pure-Kotlin |
| S3 | Espresso 3.5.0 (transitive) `InputManager.getInstance` `NoSuchMethodException` broke all Compose instrumented tests on API 37 | Pinned `espresso-core 3.7.0` on `androidInstrumentedTest` | Dependency on classpath; awaits an API-37 instrumented run |
| S4 | `AgentViewModel` error/confirmation banners were sticky (only cleared at send start) and could coexist | One mutually-exclusive status; cleared on every send path + at `onApply` start; `onDismissBanner()` added | +2 unit tests |
| S5 | `!!` in `EntryViewModel` (×2), `CapturePipeline` (×3), `RecoveryPhraseRevealScreen` (×2) — no-`!!` rule | `requireNotNull` with messages / local-val capture | 307 tests green |
| S6 | `CaptureCoordinator` swallowed per-item handler failures silently | Optional `onItemError` hook (default no-op); Android `AppContainer` logs dropped items | +1 unit test |
| S7 | Dead code: `ToolRegistry.isKnown()`, `CardSummary` data class | Removed (YAGNI; `AgentLoop` dispatches via `read`/`isWrite`/else; `CardSummaryCalculator` kept) | 307 tests green |
| L2 | `expect class … in Beta` warnings (KT-61573) | Added `-Xexpect-actual-classes` to `kotlin { compilerOptions }` | Warnings silenced |
| S8 | M4 master-spec migration-numbering claimed one combined `4.sqm`; reality is `4`=transfer_group_id, `5`=agent tables, `6`=card columns | Corrected the spec (§9, §11, M4-3 line) | Matches `.sqm` files |
| S9 | iOS Kotlin/Native target had **never compiled** (Clock.System ×27, JVM `%02x`.format, missing `@ExperimentalForeignApi` opt-in, wrong SQLCipher API, wasmJs-variant pollution) | datetime forced to 0.6.1 (resolutionStrategy); multiplatform hex; `@file:OptIn(ExperimentalForeignApi)` on BiometricAuth; `encryptionSpec` → the real sqliter-1.3.3 `encryptionConfig = Encryption(key)`; wasmJs gated; AppLifecycle implemented via NSNotificationCenter | **`compileKotlinIosSimulatorArm64` BUILD SUCCESSFUL** (29 errors → 0) |
| S10 | iOS framework would not **link**, and there was no Xcode app (H2) — so the app had never built or run | nav-compose `2.8.0-alpha10 → 2.9.2` (the alpha had a Kotlin/Native inline-codegen bug failing the framework link) + multiplatform nav-args (`SavedState.read`); `iosApp/project.yml` (xcodegen) for the Xcode app target | **`xcodebuild` BUILD SUCCEEDED + the app launches on the iPhone 17 simulator** and renders the Compose Welcome screen |

---

## How this doc is maintained

- New tech debt is added when discovered, with origin commit/phase
- Items are closed by linking the commit/PR that resolves them and moving them under `## Closed`
- Reviewed at the start of each phase brainstorm
