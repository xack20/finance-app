# Hisaab — Tech Debt & Carryover from P0a/P0b

**Status:** Tracking doc — items here are deliberate deferrals or known gaps from prior phases.
**Last updated:** 2026-05-28
**Convention:** Each item carries a target phase. Items unresolved by v1 launch must move to Phase 2 backlog.

---

## Critical (must close in P0c)

These break the end-to-end story or compromise the architecture if not addressed before P0c ships.

| # | Item | Origin | Why critical | Target |
|---|---|---|---|---|
| C1 | `DatabaseDriverFactory.createDriver()` is never called after onboarding completes — encrypted DB is built but never opened in the running app | P0b T3 | Core promise (E2EE local storage) is unrealised | **P0c** |
| C2 | `UserProfile` from `ProfileSetupScreen` is discarded — name/locale never persisted | P0b T12 | Onboarding has no output | **P0c** |
| C3 | `App.kt` uses `StubAuthRepository` (always returns success); real Supabase OTP is unused in the running app | P0b T14 | OTP is the entire account model | **P0c** |
| C4 | No DI — `App()` manually instantiates everything; cannot inject DB, contexts, repos | P0b T14 | Blocks every feature that needs DB or repos | **P0c** |
| C5 | `OnboardingViewModel.generateMasterSecret()` only holds `master_secret` in memory — never written to `SecureStorage` | P0b T10 | Onboarding "completes" without persisting the key; restart loses everything | **P0c** |
| C6 | Biometric `Enable` and `Skip` paths in `OnboardingGraph` are identical (neither actually calls `BiometricAuth.authenticate()`) | P0b T13 | Biometric setup is decorative, not functional | **P0c** |
| C7 | `MnemonicService` uses XOR checksum instead of standard BIP39 SHA256 checksum | P0b T5 | Recovery phrase is NOT cross-wallet-compatible — labelled "BIP39" but actually proprietary | **P0c** |
| C8 | `OnboardingKey` state in `AppState` has no UI — falls through to `TodayScreen` on new device | P0b T9 | New-device recovery doesn't work | **P0c** |
| C9 | App lifecycle (background → `Locked`) is never wired — `AppViewModel.onAppBackground()` is defined but never called | P0b T9 | App stays unlocked forever once authenticated | **P0c** |

---

## High (close in P0c or document why deferred)

Important gaps but not strictly blocking P0c functional value.

| # | Item | Origin | Notes | Target |
|---|---|---|---|---|
| H1 | `iOS SecureStorage` is a stub — load/save are no-ops | P0b T6 | Keychain cinterop deferred; iOS users cannot persist `master_secret` | P0c if iOS milestone else P0d |
| H2 | iOS app never run on simulator — Xcode project setup incomplete | P0a T23/T26 | Need Xcode wizard to create `iosApp.xcodeproj`, embed `ComposeApp.framework` | P0d (after Android is fully usable) |
| H3 | wasmJs / Web target never run in browser | P0a T29 | Compiles but unverified | P0d |
| H4 | `CryptoServiceTest` marked `@Ignore` — libsodium native lib can't load in JVM unit tests | P0b T4 | Needs `androidInstrumentedTest` setup to exercise on device | P0c |
| H5 | Real BD SMS provider (SSL Wireless / Infobip) not wired — using Supabase Test Mode | P0b T8 | Blocks public beta but not P0c development | P0d/P0e |
| H6 | 16 KB page-size alignment — `libsodium.so`, `libsqlcipher.so`, `libjnidispatch.so`, `libandroidx.graphics.path.so` not aligned | P0b emulator test | Runs in compatibility mode on Android 15+ pixel devices; breaks under future Android requiring 16 KB | P0d |

---

## Medium (track for P0d+)

| # | Item | Origin | Notes | Target |
|---|---|---|---|---|
| M1 | No locale switching wired — UI strings are hardcoded English | P0b T12 | `compose-resources` is added to deps but `stringResource()` not used; locale stored in `UserProfile` is unused | P0d |
| M2 | No persistence of biometric preference / lock policy (immediately / 30s / 5m) | P0b T9 | Spec mentions this; needs Settings screen | P0d |
| M3 | No accessibility audit — VoiceOver / TalkBack untested | P0b | Accessibility pass deferred per S8 spec | P0e |
| M4 | iOS `NativeSqliteDriver` + SQLCipher encryption never tested on simulator | P0b T3 | Hex keying path uses lossless conversion now (fixed in 1ba88e4) but unverified on device | P0c if iOS in scope |
| M5 | Web `WebWorkerDriver` (sql.js) never tested in browser | P0b T3 | Web is viewer-only in v1 | P0d |
| M6 | Screenshot prevention on `RecoveryPhraseScreen` not implemented — Android `FLAG_SECURE`, iOS blur overlay | P0b spec §13 | Recovery phrase visible to screenshots / screen recording | P0d |
| M7 | Argon2id timing benchmark on low-end devices — may need to drop to `m=32MB` if P99 > 3 s | P0b spec §13 | Currently uses `MEMLIMIT_MODERATE` (~64 MB) | P0d |
| M8 | `gradle/gradle-daemon-jvm.properties` toolchain set to JDK 21 only (any vendor) — works locally but CI must pin JDK 21 | P0b emulator session | Update CI config when CI exists | when CI exists |

---

## Low (Phase 2 candidates)

| # | Item | Origin | Notes | Target |
|---|---|---|---|---|
| L1 | Release build has `isMinifyEnabled = false` — SQLCipher key-handling code ships unobfuscated | P0b T3 review | Release builds far away; track for pre-launch | pre-launch |
| L2 | `expect class … in Beta` warnings everywhere — KT-61573 | P0a | Add `-Xexpect-actual-classes` flag when Kotlin moves it to stable | when stable |

---

## How this doc is maintained

- New tech debt is added when discovered, with origin commit/phase
- Items are closed by linking the commit/PR that resolves them and moving them under `## Closed`
- Reviewed at the start of each phase brainstorm
