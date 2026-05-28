# Hisaab — P0b: Foundation Auth & Storage Design

**Status:** Approved  
**Date:** 2026-05-28  
**Phase:** P0b — Authentication, Encrypted Storage, Onboarding  
**Depends on:** [`2026-05-27-hisaab-master-architecture-design.md`](./2026-05-27-hisaab-master-architecture-design.md)

---

## 1. Summary

P0b adds the three pillars that make Hisaab functional: encrypted local storage (SQLDelight + SQLCipher), an authenticated backend session (Supabase phone OTP), and the first complete user journey (5-screen onboarding). At the end of P0b a user can install the app, sign up with their Bangladesh phone number, set up biometric unlock, save a recovery phrase, and arrive at the Today screen with a fully encrypted local database.

---

## 2. Scope

### Delivers

| Area | Detail |
|---|---|
| SQLDelight schema | Full schema — 12 tables — in `V1__initial.sq` |
| SQLCipher integration | `DatabaseDriverFactory` expect/actual — Android (sqlcipher-android AAR), iOS (SQLCipher SPM), Web (sql.js, no encryption) |
| Key derivation | `CryptoService` in commonMain via kotlin-multiplatform-libsodium; Argon2id derives DB key from master secret |
| Secure storage | `SecureStorage` expect/actual — Android EncryptedSharedPreferences (StrongBox), iOS SecItem Keychain (Secure Enclave) |
| Biometric auth | `BiometricAuth` expect/actual — Android BiometricPrompt, iOS LAContext |
| Recovery phrase | `MnemonicService` in commonMain; BIP39 24-word generation; display + acknowledgment |
| Supabase auth | `AuthRepository` backed by Supabase Kotlin SDK; phone OTP send + verify; dev test mode (no real SMS needed) |
| Onboarding UI | 5 Compose Multiplatform screens: Welcome, OTP, Biometric, RecoveryPhrase, Profile |
| App state routing | `AppViewModel` sealed `AppState`; state-driven navigation in `App()` |

### Explicitly out of scope

- Transaction entry, ledger queries, sync, push notifications, billing
- Real BD SMS provider wiring (deferred — Supabase test mode used in P0b)
- Recovery phrase re-entry flow (new-device recovery — deferred to P0c)
- Web biometric (Web is viewer-only; no biometric on Web in v1)

---

## 3. Architecture decisions

| Decision | Choice | Rationale |
|---|---|---|
| Passphrase model | Biometric-first; master secret in Keystore/Keychain | Frictionless daily UX; still true E2EE (key never leaves device) |
| Recovery model | 24-word BIP39 phrase at onboarding | Self-custody recovery; no server backdoor; lose phrase = data unrecoverable (clearly communicated) |
| Onboarding screens | 5 screens | Each step focused; recovery phrase gets its own dedicated moment |
| Schema scope | Full 12-table schema in P0b | Avoids breaking migrations when P0c adds transaction entry |
| SQLCipher approach | Native SQLCipher on both Android and iOS from P0b | No temporary unencrypted iOS data; no migration landmine later |
| Key derivation | HKDF-derived salt → Argon2id(master_secret, db_salt) | Deterministic salt means recovery phrase alone reconstructs db_key on any device |

---

## 4. Key derivation model

### Master secret

```
master_secret = libsodium.randombytes_buf(32)  // generated once at onboarding
```

### Derivation hierarchy

```
master_secret (32 bytes — single entropy root)
 ├─ db_key   = Argon2id(master_secret, HKDF(master_secret, "hisaab.db.salt"),
 │                      m=64MB, t=3, p=2) → 32 bytes              ← P0b
 ├─ sync_key = HKDF-SHA256(master_secret, "hisaab.sync.key")      ← S4 / P0c
 └─ op_key   = HKDF-SHA256(master_secret, "hisaab.op.key")        ← S4 / P0c
```

`db_salt` is derived deterministically — no extra storage needed. Recovery phrase alone reconstructs `db_key` on any device.

Argon2id parameters: m=64MB, t=3, p=2 (benchmark on low-end device; reduce to m=32MB if P99 > 3s).

### Secure storage

- **Android:** `EncryptedSharedPreferences` with `BIOMETRIC_STRONG` user authentication required (StrongBox where available)
- **iOS:** `SecItem` Keychain with `kSecAccessControlBiometryAny` + `kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly` + Secure Enclave

`master_secret` is zeroed from memory immediately after `db_key` derivation. `db_key` is zeroed on app background or explicit lock.

### Recovery phrase

24-word BIP39 encoding of `master_secret` (256 bits). Displayed on `RecoveryPhraseScreen` during onboarding. User must tick a checkbox before the CTA enables. Not stored anywhere after display — exists only in the user's written copy. Screenshot prevention: Android `FLAG_SECURE`; iOS blur overlay fallback.

---

## 5. SQLDelight + SQLCipher

### DatabaseDriverFactory (expect/actual)

```kotlin
// commonMain
expect class DatabaseDriverFactory(context: Any? = null) {
    fun createDriver(dbKey: ByteArray): SqlDriver
}
```

- **Android actual:** `SqlCipherOpenHelper`; passes `"x'${dbKey.toHex()}'"` via `PRAGMA key`
- **iOS actual:** `NativeSqliteDriver` wrapping SQLCipher.framework; calls `sqlite3_key()` before first query
- **wasmJs actual:** `WebWorkerDriver` (sql.js); no encryption; Web is viewer-only

### Schema — V1__initial.sq (12 tables: 10 domain entities + user_profile + encrypted_op + lend_borrow_txn junction)

```sql
CREATE TABLE user_profile (
    id TEXT NOT NULL PRIMARY KEY,
    supabase_user_id TEXT NOT NULL,
    display_name TEXT NOT NULL,
    locale TEXT NOT NULL DEFAULT 'en',
    theme TEXT NOT NULL DEFAULT 'auto',
    created_at INTEGER NOT NULL
);

CREATE TABLE account (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    kind TEXT NOT NULL,          -- CASH | BANK | MFS | CARD | GOAL
    institution TEXT,
    currency TEXT NOT NULL DEFAULT 'BDT',
    balance_tracking INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL
);

CREATE TABLE category (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    parent_id TEXT,
    color TEXT,
    icon TEXT,
    is_default INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE merchant (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    normalized_name TEXT NOT NULL,
    default_category_id TEXT REFERENCES category(id)
);

CREATE TABLE txn (
    id TEXT NOT NULL PRIMARY KEY,
    account_id TEXT NOT NULL REFERENCES account(id),
    amount REAL NOT NULL,
    currency TEXT NOT NULL DEFAULT 'BDT',
    ts INTEGER NOT NULL,
    merchant_id TEXT REFERENCES merchant(id),
    category_id TEXT REFERENCES category(id),
    source TEXT NOT NULL,        -- SMS | EMAIL | VOICE | MANUAL | OCR | RECURRING
    notes TEXT
);

CREATE TABLE person (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    contact_ref TEXT,
    balance REAL NOT NULL DEFAULT 0.0
);

CREATE TABLE lend_borrow (
    id TEXT NOT NULL PRIMARY KEY,
    person_id TEXT NOT NULL REFERENCES person(id),
    amount REAL NOT NULL,
    direction TEXT NOT NULL,     -- LENT | BORROWED
    purpose TEXT,
    ts INTEGER NOT NULL,
    due_date INTEGER,
    status TEXT NOT NULL DEFAULT 'OPEN'  -- OPEN | SETTLED | PARTIAL
);

CREATE TABLE lend_borrow_txn (
    lend_borrow_id TEXT NOT NULL REFERENCES lend_borrow(id),
    txn_id TEXT NOT NULL REFERENCES txn(id),
    PRIMARY KEY (lend_borrow_id, txn_id)
);

CREATE TABLE goal (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    target_amount REAL NOT NULL,
    current_amount REAL NOT NULL DEFAULT 0.0,
    deadline INTEGER,
    account_id TEXT REFERENCES account(id)
);

CREATE TABLE recurring_rule (
    id TEXT NOT NULL PRIMARY KEY,
    kind TEXT NOT NULL,          -- SALARY | RENT | SUBSCRIPTION | EMI | OTHER
    amount REAL NOT NULL,
    schedule TEXT NOT NULL,
    account_id TEXT REFERENCES account(id),
    category_id TEXT REFERENCES category(id)
);

CREATE TABLE insight (
    id TEXT NOT NULL PRIMARY KEY,
    generated_at INTEGER NOT NULL,
    type TEXT NOT NULL,          -- REVIEW | WARNING | SUGGESTION | SUMMARY
    payload TEXT NOT NULL,
    surface_state TEXT NOT NULL DEFAULT 'UNSEEN'  -- UNSEEN | SEEN | ACTED
);

CREATE TABLE encrypted_op (
    id TEXT NOT NULL PRIMARY KEY,
    device_id TEXT NOT NULL,
    lamport_ts INTEGER NOT NULL,
    record_id TEXT NOT NULL,
    field_name TEXT NOT NULL,
    payload TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
```

Notes:
- All timestamps are Unix epoch milliseconds (INTEGER)
- `txn` instead of `transaction` (SQLite reserved word)
- Migration file: `composeApp/src/commonMain/sqldelight/migrations/1.sqm`
- Schema file: `composeApp/src/commonMain/sqldelight/app/hisaab/HisaabDatabase.sq`

---

## 6. Supabase auth

### Client setup (commonMain)

```kotlin
val supabase = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
) {
    install(Auth)
    install(Postgrest)
    install(Realtime)
}
```

`SUPABASE_URL` and `SUPABASE_ANON_KEY` injected via `BuildConfig` (Android) and `xcconfig` (iOS) — never hardcoded.

### AuthRepository

```kotlin
interface AuthRepository {
    suspend fun sendOtp(phone: String): Result<Unit>
    suspend fun verifyOtp(phone: String, token: String): Result<Session>
    fun currentSession(): Session?
    suspend fun signOut()
    fun authStateChanges(): Flow<AuthChangeEvent>
}
```

### Development OTP

Supabase Auth test mode: OTPs appear in the Supabase dashboard log without sending a real SMS. P0b uses this. Real BD SMS provider (SSL Wireless / Infobip) is wired in a later phase — `AuthRepository` interface is unchanged.

---

## 7. App state & navigation

### Sealed AppState

```kotlin
sealed class AppState {
    data object Loading : AppState()
    data object Unauthenticated : AppState()
    data class Onboarding(val step: OnboardingStep) : AppState()
    data object OnboardingKey : AppState()   // new-device recovery — P0c
    data object Locked : AppState()
    data object Authenticated : AppState()
}

enum class OnboardingStep { WELCOME, OTP, BIOMETRIC, RECOVERY_PHRASE, PROFILE }
```

### AppViewModel startup

```
1. Check Supabase session
   - No session              → emit Unauthenticated
   - Session, no local key   → emit OnboardingKey
   - Session + key found     → derive db_key → open DB → emit Authenticated
2. On background lifecycle   → zero db_key → emit Locked
3. On foreground from Locked → biometric → re-derive → emit Authenticated
```

### Navigation table

| State | Renders |
|---|---|
| `Loading` | `SplashScreen` |
| `Unauthenticated` / `Onboarding` | `OnboardingGraph` |
| `OnboardingKey` | `RecoveryEntryScreen` (stub in P0b) |
| `Locked` | `LockScreen` |
| `Authenticated` | `MainGraph` → `TodayScreen` |

Navigation is purely state-driven — no imperative `navigate()` from ViewModels.

---

## 8. Onboarding UI — 5 screens

| Screen | File | Key behaviour |
|---|---|---|
| `WelcomeScreen` | `onboarding/WelcomeScreen.kt` | Phone input with +880 prefix, E.164 formatting, Continue → sendOtp |
| `OtpScreen` | `onboarding/OtpScreen.kt` | 6-box input, auto-advance on last digit, 60s resend timer |
| `BiometricSetupScreen` | `onboarding/BiometricSetupScreen.kt` | Calls `BiometricAuth.enroll()`; Skip option; failure → error + retry |
| `RecoveryPhraseScreen` | `onboarding/RecoveryPhraseScreen.kt` | Scrollable `LazyVerticalGrid` of 24 numbered words; checkbox required; `FLAG_SECURE` / blur overlay |
| `ProfileSetupScreen` | `onboarding/ProfileSetupScreen.kt` | Display name field; EN / বাং toggle; "Start Hisaab →" saves `UserProfile` → emits Authenticated |

### OnboardingViewModel responsibilities

1. `sendOtp(phone)` → `AuthRepository.sendOtp()`
2. `verifyOtp(token)` → `AuthRepository.verifyOtp()` → advance to `BIOMETRIC`
3. `setupBiometric()` → generate `master_secret` → `SecureStorage.storeMasterSecret()` → advance to `RECOVERY_PHRASE`
4. `generateRecoveryPhrase()` → `MnemonicService.encode(master_secret)` → 24 words
5. `confirmPhraseAcknowledged()` → advance to `PROFILE`
6. `completeProfile(name, locale)` → `CryptoService.deriveDbKey()` → open DB → insert `UserProfile` → emit `Authenticated`

---

## 9. expect/actual surface

| Interface | commonMain | androidMain | iosMain | wasmJsMain |
|---|---|---|---|---|
| `DatabaseDriverFactory` | expect class | SqlCipherOpenHelper | NativeSqliteDriver + SQLCipher.framework | WebWorkerDriver (sql.js) |
| `SecureStorage` | expect class | EncryptedSharedPreferences | SecItem Keychain | SessionStorage (no biometric) |
| `BiometricAuth` | expect class | BiometricPrompt | LAContext | No-op (returns success) |
| `PlatformInfo` | expect class | ✅ P0a | ✅ P0a | ✅ P0a |

---

## 10. Dependencies to add

```toml
# gradle/libs.versions.toml additions

[versions]
sqldelight           = "2.0.2"
sqlcipher-android    = "4.5.4"
supabase             = "3.0.3"
libsodium            = "0.9.3"

[libraries]
sqldelight-coroutines      = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-android-driver  = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-native-driver   = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
sqldelight-web-driver      = { module = "app.cash.sqldelight:web-worker-driver", version.ref = "sqldelight" }
sqlcipher-android          = { module = "net.zetetic:sqlcipher-android", version.ref = "sqlcipher-android" }
supabase-auth              = { module = "io.github.jan-tennert.supabase:auth-kt", version.ref = "supabase" }
supabase-postgrest         = { module = "io.github.jan-tennert.supabase:postgrest-kt", version.ref = "supabase" }
supabase-realtime          = { module = "io.github.jan-tennert.supabase:realtime-kt", version.ref = "supabase" }
libsodium-kmp              = { module = "com.ionspin.kotlin:multiplatform-crypto-libsodium-bindings", version.ref = "libsodium" }
androidx-security-crypto   = { module = "androidx.security:security-crypto", version = "1.1.0-alpha06" }
androidx-biometric         = { module = "androidx.biometric:biometric", version = "1.2.0-alpha05" }

[plugins]
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

iOS SPM dependency (added via Xcode or `Package.swift`):
```
https://github.com/sqlcipher/sqlcipher-swift  — version 4.5.4
```

---

## 11. File structure added in P0b

```
composeApp/src/
├── commonMain/kotlin/app/hisaab/
│   ├── AppViewModel.kt
│   ├── crypto/
│   │   ├── CryptoService.kt          ← Argon2id + HKDF via libsodium
│   │   └── MnemonicService.kt        ← BIP39 encode/decode
│   ├── db/
│   │   └── DatabaseDriverFactory.kt  ← expect class
│   ├── platform/
│   │   ├── SecureStorage.kt          ← expect class
│   │   └── BiometricAuth.kt          ← expect class
│   ├── auth/
│   │   ├── AuthRepository.kt         ← interface
│   │   └── SupabaseAuthRepository.kt ← implementation
│   └── screens/onboarding/
│       ├── OnboardingViewModel.kt
│       ├── OnboardingGraph.kt
│       ├── WelcomeScreen.kt
│       ├── OtpScreen.kt
│       ├── BiometricSetupScreen.kt
│       ├── RecoveryPhraseScreen.kt
│       └── ProfileSetupScreen.kt
├── commonMain/sqldelight/
│   ├── migrations/1.sqm              ← full schema V1
│   └── app/hisaab/HisaabDatabase.sq  ← named queries
├── androidMain/kotlin/app/hisaab/
│   ├── db/DatabaseDriverFactory.kt
│   ├── platform/SecureStorage.kt
│   └── platform/BiometricAuth.kt
├── iosMain/kotlin/app/hisaab/
│   ├── db/DatabaseDriverFactory.kt
│   ├── platform/SecureStorage.kt
│   └── platform/BiometricAuth.kt
└── wasmJsMain/kotlin/app/hisaab/
    ├── db/DatabaseDriverFactory.kt
    ├── platform/SecureStorage.kt
    └── platform/BiometricAuth.kt
```

---

## 12. Verification gate

P0b is complete when all of the following pass on a fresh install:

1. Phone number → OTP sent (visible in Supabase dashboard, test mode)
2. OTP entry → Supabase session created
3. Biometric enrolment → `master_secret` in Keystore/Keychain
4. Recovery phrase screen shows 24 BIP39 words; CTA disabled until checkbox ticked
5. Profile setup → `user_profile` row in encrypted DB
6. Today screen shown
7. Kill + relaunch → biometric prompt → DB reopens → Today screen
8. All 12 DB tables present
9. `./gradlew :composeApp:allTests` green

---

## 13. Open questions deferred

- **Recovery entry screen** — 24-word re-entry on new device. `OnboardingKey` state defined; UI is P0c.
- **Real BD SMS provider** — SSL Wireless / Infobip. Wired when P0b is stable.
- **Argon2id timing** — benchmark on Snapdragon 680-class device; reduce m to 32MB if P99 > 3s.
- **Screenshot prevention on RecoveryPhraseScreen** — Android `FLAG_SECURE`; iOS blur overlay. Implement during P0b.
