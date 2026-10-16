# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Hisaab — privacy-first AI personal-finance app (Bangladesh-first). Kotlin Multiplatform + Compose Multiplatform,
SQLDelight on SQLCipher, Supabase, libsodium. Product/architecture context lives in `docs/superpowers/specs/`
(start with `2026-05-27-hisaab-master-architecture-design.md`), per-phase implementation plans in
`docs/superpowers/plans/`, known carryover in `docs/tech-debt.md`, design-system context in
`docs/design/PROJECT-CONTEXT.md`.

## Commands

Gradle needs JDK 17+; the repo's scripts default `JAVA_HOME` to `~/.sdkman/candidates/java/21.0.10-amzn`.

```bash
./gradlew :composeApp:assembleDebug                      # Android APK
./gradlew :composeApp:testDebugUnitTest                  # commonTest on the JVM — the main test loop
./gradlew :composeApp:testDebugUnitTest --tests 'app.hisaab.capture.CapturePipelineTest'   # single class
./gradlew :composeApp:testDebugUnitTest --tests '*CapturePipelineTest.someTestName'        # single test
./gradlew :composeApp:connectedDebugAndroidTest          # androidInstrumentedTest (crypto, SMS glue) — needs device
./gradlew :composeApp:verifySqlDelightMigration          # migrations must reproduce the schema
./gradlew :macHelper:jvmTest                             # Mac companion tests
./gradlew :macHelper:run                                 # run Mac companion locally (unsigned)
macHelper/build-and-sign.sh                              # stable-signed .app so Full Disk Access survives rebuilds
./gradlew -Phisaab.enableWasm=true :composeApp:wasmJsBrowserDevelopmentRun   # wasm is gated OFF (libsodium has no wasm artifact)
```

`commonTest` depends on the JVM-only JDBC SQLite driver (`data/support/TestDatabase.kt`), so run tests through the
Android JVM target rather than the iOS test tasks.

**iOS:** `iosApp/project.yml` (XcodeGen) is the source of truth for the Xcode project — edit it, then
`cd iosApp && xcodegen generate && pod install`, and open `iosApp.xcworkspace` (not the `.xcodeproj`). The Xcode
pre-build script runs `linkDebugFramework{IosArm64|IosSimulatorArm64}` plus `syncComposeResourcesForIos` (without
the latter the app crashes on missing font resources). Device setup/signing: `docs/iphone-test-runbook.md`.

**Local config (all gitignored):**
- Android: `local.properties` → `supabase.url`, `supabase.anon_key` (become `BuildConfig` fields).
- iOS: `cp iosApp/Secrets.xcconfig.example iosApp/Secrets.xcconfig`; keep the `https:/$()/` escape or the URL
  silently truncates at `//`.
- macHelper: `HISAAB_SUPABASE_URL` / `HISAAB_SUPABASE_ANON_KEY` env vars or `~/.hisaab/companion.properties`.
  Signing/notarization only turns on when `HISAAB_MAC_SIGN_IDENTITY` is set (see `macHelper/README.md`).
- `**/appPass*` files are plaintext credential scratch files — never read, print, or commit them.

## Modules

- **`:composeApp`** — the app (Android, iOS; wasmJs behind `-Phisaab.enableWasm=true`). Almost all logic is in
  `commonMain`; platforms supply `actual` implementations under `androidMain` / `iosMain`.
- **`:sharedCore`** — deliberately tiny KMP module (Android/iOS/JVM): crypto, `RawCapture`, relay codec, IMAP/email
  parsing. Exists so `:macHelper` seals captures with the *exact* same code as the phone (byte-identical
  ciphertext). Keep its deps to libsodium + kotlinx-serialization; don't pull Compose/data into it.
- **`:macHelper`** — Compose Desktop (JVM) macOS companion. iOS apps can't read SMS/email, so the Mac reads
  forwarded SMS from `~/Library/Messages/chat.db` (needs Full Disk Access) and bank emails via IMAP, seals each with
  `sharedCore`'s `RelayCaptureCrypto` (key derived from the user's 24-word recovery phrase), and pushes ciphertext
  to Supabase `relay_captures` (`supabase/migrations/`). The phone pulls via `RelayCaptureSource` and deletes rows
  after ingesting. Distributed outside the App Store.

## Architecture (composeApp)

- **DI:** `AppContainer` is an `expect class` (hand-wired, no DI framework) with actuals in
  `androidMain/.../AppContainerAndroid.kt` and `iosMain/.../AppContainerIos.kt`; Compose reads it via
  `LocalAppContainer`. New services get wired in **both** actuals.
- **Encrypted DB lifecycle:** the SQLCipher DB is keyed by a master secret derived from the recovery mnemonic.
  `databaseOrNull()` is null until unlock (`openDatabase`); `closeDatabase()` on background-lock/sign-out zeroes the
  in-memory key. Code touching repositories must tolerate the locked state. On iOS, `linkSqlite=false` +
  the `SQLCipher` CocoaPod supply the `sqlite3_*` symbols so `PRAGMA key` actually encrypts.
- **SQLDelight:** `deriveSchemaFromMigrations` + `verifyMigrations` are on. Schema lives in
  `commonMain/sqldelight/migrations/N.sqm`; the `.sq` files under `sqldelight/app/hisaab/db/` hold queries only.
  Schema changes = a new numbered `.sqm` (never edit an existing one). Repositories in `data/` wrap the queries;
  multi-write operations use `db.transaction { }`.
- **Capture pipeline** (`capture/`): platform `CaptureSource`s (Android SMS receiver, notification listener, in-app
  IMAP; iOS relay) → `CaptureCoordinator` (runs only while the DB is open and capture is enabled) →
  `CapturePipeline.process`: dedup hash → `SmsPreFilter` → `BankTemplate` regex extraction, LLM fallback →
  account/merchant/category resolution → `ConfidenceScorer` → either auto-post (txn insert + mark candidate
  `AUTO_POSTED` in one transaction, txn linked via `NewTransaction.captureId`) or leave `PENDING` in the inbox for
  user confirmation (`AppContainer.confirmCandidate*`).
- **LLM layer** (`llm/`): `LlmRouter`/`DefaultLlmRouter` picks on-device (MediaPipe on Android; iOS bridge via
  `iosApp/OnDeviceAgentBridge.swift`) or a BYO-key cloud provider (`llm/cloud/`: Claude, Gemini, OpenAI). Cloud
  requires recorded consent **and** a key in `SecureStorage` (`llm_api_key_<PROVIDER>`); otherwise returns null and
  callers fall back to template-only. `Redactor` masks PII before cloud calls when redaction is enabled — privacy
  rules are in spec §6–7.
- **Agent** (`agent/`): vendor-agnostic ReAct `AgentLoop`. Read tools execute in-loop; write tools are only
  *proposed* in the final turn. `WriteBatchCommitter` is the sole writer on Apply: one all-or-nothing
  transaction, FK-ordered, with entities referenced by name. `AgentRuntime` is built per use via
  `AppContainer.agentRuntime()`.
- **UI:** "Midnight" design system — tokens in `design/` (`HisaabTheme`, colors, typography, spacing) and shared
  components in `design/components/`; screens in `screens/<feature>/`. Use these instead of raw Material widgets.

## Gotchas

- `kotlinx-datetime` is force-pinned to **0.6.1** in both `composeApp` and `macHelper` (supabase-kt 3.0.3 needs
  `kotlinx.datetime.Instant`/`Clock`, which moved to `kotlin.time` in 0.7). Don't bump it or migrate call sites.
- macHelper bundles the full JDK module set (`includeAllModules`) because sqlite-jdbc/jakarta.mail reach modules
  via reflection; don't trim it.
- Ad-hoc-signed macHelper builds lose the Full Disk Access grant every rebuild — use `build-and-sign.sh`.

## Conventions

Conventional commits with optional scope, e.g. `fix(mac): ...`, `feat: ...`, `perf(mac): ...`. `.editorconfig`:
4-space indent, 120-col lines, trailing commas allowed in Kotlin.
