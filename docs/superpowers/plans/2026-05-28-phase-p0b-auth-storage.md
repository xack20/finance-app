# P0b — Auth, Encrypted Storage & Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire SQLDelight + SQLCipher encrypted local DB, Supabase phone OTP auth, biometric unlock, 24-word BIP39 recovery phrase, and 5-screen onboarding flow so a user can sign up and arrive at the Today screen with a fully encrypted database.

**Architecture:** `DatabaseDriverFactory` (expect/actual) opens SQLCipher on Android/iOS; `CryptoService` derives a 32-byte DB key via Argon2id from a libsodium-generated `master_secret`; `master_secret` is stored in Keystore/Keychain gated by biometric; `AppViewModel` owns a sealed `AppState` that drives navigation; `OnboardingViewModel` sequences the 5 onboarding screens.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.10.1, SQLDelight 2.0.2, SQLCipher Android 4.5.4 / SQLCipher SPM, kotlin-multiplatform-libsodium 0.9.3, Supabase Kotlin SDK 3.0.3, AndroidX Biometric 1.2.0-alpha05, AndroidX Security Crypto 1.1.0-alpha06.

**Spec:** `docs/superpowers/specs/2026-05-28-p0b-foundation-auth-design.md`

---

## File map

```
composeApp/src/commonMain/kotlin/app/hisaab/
├── AppViewModel.kt                          NEW — sealed AppState, startup logic
├── App.kt                                   MODIFY — connect AppViewModel state
├── crypto/
│   ├── CryptoService.kt                     NEW — Argon2id + HKDF via libsodium
│   ├── MnemonicService.kt                   NEW — BIP39 encode/decode
│   └── Bip39Wordlist.kt                     NEW — 2048-word BIP39 English list
├── db/
│   └── DatabaseDriverFactory.kt             NEW — expect class
├── platform/
│   ├── SecureStorage.kt                     NEW — expect class
│   └── BiometricAuth.kt                     NEW — expect class
├── auth/
│   ├── AuthRepository.kt                    NEW — interface + AuthEvent
│   ├── SupabaseAuthRepository.kt            NEW — Supabase implementation
│   └── SupabaseClientProvider.kt            NEW — expect/actual for URL + key
└── screens/
    ├── today/TodayScreen.kt                 EXISTING (P0a)
    ├── SplashScreen.kt                      NEW
    ├── LockScreen.kt                        NEW
    └── onboarding/
        ├── OnboardingViewModel.kt            NEW
        ├── OnboardingGraph.kt                NEW
        ├── WelcomeScreen.kt                  NEW
        ├── OtpScreen.kt                      NEW
        ├── BiometricSetupScreen.kt           NEW
        ├── RecoveryPhraseScreen.kt           NEW
        └── ProfileSetupScreen.kt             NEW

composeApp/src/commonMain/sqldelight/
├── migrations/1.sqm                          NEW — V1 full schema (12 tables)
└── app/hisaab/HisaabDatabase.sq              NEW — named queries

composeApp/src/androidMain/kotlin/app/hisaab/
├── db/DatabaseDriverFactory.kt               NEW — actual (SQLCipher)
├── platform/SecureStorage.kt                 NEW — actual (EncryptedSharedPreferences)
├── platform/BiometricAuth.kt                 NEW — actual (BiometricPrompt)
└── auth/SupabaseClientProvider.kt            NEW — actual (BuildConfig)

composeApp/src/iosMain/kotlin/app/hisaab/
├── db/DatabaseDriverFactory.kt               NEW — actual (SQLCipher SPM)
├── platform/SecureStorage.kt                 NEW — actual (Keychain)
├── platform/BiometricAuth.kt                 NEW — actual (LAContext)
└── auth/SupabaseClientProvider.kt            NEW — actual (NSBundle)

composeApp/src/wasmJsMain/kotlin/app/hisaab/
├── db/DatabaseDriverFactory.kt               NEW — actual (sql.js)
├── platform/SecureStorage.kt                 NEW — actual (SessionStorage stub)
├── platform/BiometricAuth.kt                 NEW — actual (no-op)
└── auth/SupabaseClientProvider.kt            NEW — actual (env vars)

composeApp/src/commonTest/kotlin/app/hisaab/
├── crypto/CryptoServiceTest.kt               NEW
├── crypto/MnemonicServiceTest.kt             NEW
├── AppViewModelTest.kt                       NEW
├── auth/FakeAuthRepository.kt                NEW
└── screens/onboarding/OnboardingViewModelTest.kt  NEW

gradle/libs.versions.toml                     MODIFY — add SQLDelight, Supabase, libsodium, Ktor
composeApp/build.gradle.kts                   MODIFY — add deps, SQLDelight plugin, BuildConfig
```

---

## Task 1: Add dependencies to version catalog and build files

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/build.gradle.kts`

- [ ] **Step 1: Add versions and libraries to `gradle/libs.versions.toml`**

Open `gradle/libs.versions.toml`. In `[versions]`, add after the existing entries:

```toml
sqldelight        = "2.0.2"
sqlcipher-android = "4.5.4"
supabase          = "3.0.3"
libsodium         = "0.9.3"
ktor              = "3.0.0"
nav-compose       = "2.8.0-alpha10"
```

In `[libraries]`, add:

```toml
# SQLDelight
sqldelight-coroutines     = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-native-driver  = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
sqldelight-web-driver     = { module = "app.cash.sqldelight:web-worker-driver", version.ref = "sqldelight" }
sqlcipher-android         = { module = "net.zetetic:sqlcipher-android", version.ref = "sqlcipher-android" }

# Supabase
supabase-auth             = { module = "io.github.jan-tennert.supabase:auth-kt", version.ref = "supabase" }
supabase-postgrest        = { module = "io.github.jan-tennert.supabase:postgrest-kt", version.ref = "supabase" }
supabase-realtime         = { module = "io.github.jan-tennert.supabase:realtime-kt", version.ref = "supabase" }

# Ktor HTTP engine (required by Supabase SDK)
ktor-client-okhttp        = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin        = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }

# Crypto
libsodium-kmp             = { module = "com.ionspin.kotlin:multiplatform-crypto-libsodium-bindings", version.ref = "libsodium" }

# AndroidX
androidx-security-crypto  = { module = "androidx.security:security-crypto", version = "1.1.0-alpha06" }
androidx-biometric        = { module = "androidx.biometric:biometric", version = "1.2.0-alpha05" }

# Navigation
nav-compose               = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "nav-compose" }
```

In `[plugins]`, add:

```toml
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

- [ ] **Step 2: Apply SQLDelight plugin and add all dependencies in `composeApp/build.gradle.kts`**

Add `alias(libs.plugins.sqldelight)` to the `plugins {}` block:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.sqldelight)
}
```

Inside the `android { defaultConfig { ... } }` block, add BuildConfig for Supabase credentials (read from `local.properties`):

```kotlin
defaultConfig {
    // existing fields ...
    buildFeatures { buildConfig = true }
    val localProps = java.util.Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) load(f.inputStream())
    }
    buildConfigField("String", "SUPABASE_URL",
        "\"${localProps.getProperty("supabase.url", "")}\"")
    buildConfigField("String", "SUPABASE_ANON_KEY",
        "\"${localProps.getProperty("supabase.anon_key", "")}\"")
}
```

Add to `sourceSets` inside `kotlin { ... }`:

```kotlin
androidMain.dependencies {
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqlcipher.android)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
}
iosMain.dependencies {
    implementation(libs.sqldelight.native.driver)
    implementation(libs.ktor.client.darwin)
}
wasmJsMain.dependencies {
    implementation(libs.sqldelight.web.driver)
}
commonMain.dependencies {
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.components.resources)
    implementation(compose.components.uiToolingPreview)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.libsodium.kmp)
    implementation(libs.nav.compose)
}
```

Add the SQLDelight configuration block at the end of `composeApp/build.gradle.kts`:

```kotlin
sqldelight {
    databases {
        create("HisaabDatabase") {
            packageName.set("app.hisaab.db")
        }
    }
}
```

Also add to `local.properties` (git-ignored — only your machine):

```
supabase.url=https://YOUR_PROJECT_REF.supabase.co
supabase.anon_key=eyJhbGci...YOUR_ANON_KEY
```

Get these from your Supabase project dashboard → Settings → API. Enable Phone auth with Test OTP mode in Authentication → Providers → Phone.

- [ ] **Step 3: Verify Gradle sync**

```bash
cd /Users/xack/projects/finance-app
./gradlew :composeApp:dependencies --configuration commonMainImplementationDependenciesMetadata --no-daemon 2>&1 | grep -E "sqldelight|supabase|libsodium" | head -20
```

Expected: All three libraries appear in the dependency tree.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts
git commit -m "feat(deps): add SQLDelight, SQLCipher, Supabase, libsodium, nav-compose deps"
```

---

## Task 2: SQLDelight schema

**Files:**
- Create: `composeApp/src/commonMain/sqldelight/migrations/1.sqm`
- Create: `composeApp/src/commonMain/sqldelight/app/hisaab/HisaabDatabase.sq`

- [ ] **Step 1: Create the V1 migration**

Create `composeApp/src/commonMain/sqldelight/migrations/1.sqm`:

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
    kind TEXT NOT NULL,
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
    source TEXT NOT NULL,
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
    direction TEXT NOT NULL,
    purpose TEXT,
    ts INTEGER NOT NULL,
    due_date INTEGER,
    status TEXT NOT NULL DEFAULT 'OPEN'
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
    kind TEXT NOT NULL,
    amount REAL NOT NULL,
    schedule TEXT NOT NULL,
    account_id TEXT REFERENCES account(id),
    category_id TEXT REFERENCES category(id)
);

CREATE TABLE insight (
    id TEXT NOT NULL PRIMARY KEY,
    generated_at INTEGER NOT NULL,
    type TEXT NOT NULL,
    payload TEXT NOT NULL,
    surface_state TEXT NOT NULL DEFAULT 'UNSEEN'
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

- [ ] **Step 2: Create named queries file**

Create `composeApp/src/commonMain/sqldelight/app/hisaab/HisaabDatabase.sq`:

```sql
-- user_profile
insertUserProfile:
INSERT INTO user_profile(id, supabase_user_id, display_name, locale, theme, created_at)
VALUES (?, ?, ?, ?, ?, ?);

getUserProfile:
SELECT * FROM user_profile LIMIT 1;

hasUserProfile:
SELECT COUNT(*) FROM user_profile;

-- account
insertAccount:
INSERT INTO account(id, name, kind, institution, currency, balance_tracking, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?);

getAllAccounts:
SELECT * FROM account ORDER BY created_at ASC;
```

- [ ] **Step 3: Generate SQLDelight interfaces**

```bash
./gradlew :composeApp:generateCommonMainHisaabDatabaseInterface --no-daemon 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`. SQLDelight generates `HisaabDatabase.kt` under `build/generated/sqldelight/`.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/sqldelight/
git commit -m "feat(db): add SQLDelight V1 schema — 12 tables"
```

---

## Task 3: DatabaseDriverFactory expect/actual

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/db/DatabaseDriverFactory.kt`
- Create: `composeApp/src/androidMain/kotlin/app/hisaab/db/DatabaseDriverFactory.kt`
- Create: `composeApp/src/iosMain/kotlin/app/hisaab/db/DatabaseDriverFactory.kt`
- Create: `composeApp/src/wasmJsMain/kotlin/app/hisaab/db/DatabaseDriverFactory.kt`

- [ ] **Step 1: commonMain expect**

Create `composeApp/src/commonMain/kotlin/app/hisaab/db/DatabaseDriverFactory.kt`:

```kotlin
package app.hisaab.db

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(dbKey: ByteArray): SqlDriver
}
```

- [ ] **Step 2: Android actual**

Create `composeApp/src/androidMain/kotlin/app/hisaab/db/DatabaseDriverFactory.kt`:

```kotlin
package app.hisaab.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(dbKey: ByteArray): SqlDriver {
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(dbKey)
        return AndroidSqliteDriver(
            schema = HisaabDatabase.Schema,
            context = context,
            name = "hisaab.db",
            factory = factory,
        )
    }
}
```

- [ ] **Step 3: iOS actual**

Create `composeApp/src/iosMain/kotlin/app/hisaab/db/DatabaseDriverFactory.kt`:

```kotlin
package app.hisaab.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration

actual class DatabaseDriverFactory {
    actual fun createDriver(dbKey: ByteArray): SqlDriver {
        val keyHex = dbKey.joinToString("") { "%02x".format(it) }
        val config = DatabaseConfiguration(
            name = "hisaab.db",
            version = HisaabDatabase.Schema.version.toInt(),
            create = { connection ->
                wrapConnection(connection) { HisaabDatabase.Schema.create(it) }
            },
            upgrade = { connection, oldVersion, newVersion ->
                wrapConnection(connection) {
                    HisaabDatabase.Schema.migrate(it, oldVersion.toLong(), newVersion.toLong())
                }
            },
            extendedConfig = DatabaseConfiguration.Extended(
                encryptionSpec = DatabaseConfiguration.Extended.EncryptionSpec(keyHex),
            ),
        )
        return NativeSqliteDriver(config)
    }
}
```

- [ ] **Step 4: wasmJs actual**

Create `composeApp/src/wasmJsMain/kotlin/app/hisaab/db/DatabaseDriverFactory.kt`:

```kotlin
package app.hisaab.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

actual class DatabaseDriverFactory {
    // Web is viewer-only; no SQLCipher on WASM. dbKey ignored.
    actual fun createDriver(dbKey: ByteArray): SqlDriver =
        WebWorkerDriver(Worker(js("new URL('sqljs.worker.js', import.meta.url).href") as String))
}
```

- [ ] **Step 5: Compile check and commit**

```bash
./gradlew :composeApp:compileKotlinAndroid --no-daemon 2>&1 | tail -10
git add composeApp/src/commonMain/kotlin/app/hisaab/db/ \
        composeApp/src/androidMain/kotlin/app/hisaab/db/ \
        composeApp/src/iosMain/kotlin/app/hisaab/db/ \
        composeApp/src/wasmJsMain/kotlin/app/hisaab/db/
git commit -m "feat(db): add DatabaseDriverFactory expect/actual with SQLCipher"
```

---

## Task 4: CryptoService — Argon2id + HKDF

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/crypto/CryptoService.kt`
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/crypto/CryptoServiceTest.kt`

- [ ] **Step 1: Write failing tests**

Create `composeApp/src/commonTest/kotlin/app/hisaab/crypto/CryptoServiceTest.kt`:

```kotlin
package app.hisaab.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CryptoServiceTest {

    @Test
    fun `deriveDbKey returns 32 bytes`() {
        val service = CryptoService()
        val key = service.deriveDbKey(ByteArray(32) { it.toByte() })
        assertEquals(32, key.size)
    }

    @Test
    fun `deriveDbKey is deterministic for same input`() {
        val service = CryptoService()
        val secret = ByteArray(32) { 42 }
        val key1 = service.deriveDbKey(secret)
        val key2 = service.deriveDbKey(secret)
        assertTrue(key1.contentEquals(key2))
    }

    @Test
    fun `deriveDbKey differs for different inputs`() {
        val service = CryptoService()
        val k1 = service.deriveDbKey(ByteArray(32) { it.toByte() })
        val k2 = service.deriveDbKey(ByteArray(32) { (it + 1).toByte() })
        assertTrue(!k1.contentEquals(k2))
    }

    @Test
    fun `generateMasterSecret returns 32 bytes`() {
        val service = CryptoService()
        val secret = service.generateMasterSecret()
        assertEquals(32, secret.size)
        assertTrue(secret.any { it != 0.toByte() })
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.crypto.CryptoServiceTest" --no-daemon 2>&1 | tail -15
```

Expected: FAIL — `CryptoService not found`.

- [ ] **Step 3: Implement CryptoService**

Create `composeApp/src/commonMain/kotlin/app/hisaab/crypto/CryptoService.kt`:

```kotlin
package app.hisaab.crypto

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.pwhash.CryptoPasswordHash
import com.ionspin.kotlin.crypto.pwhash.PasswordHashArgon2id
import com.ionspin.kotlin.crypto.util.LibsodiumRandom
import com.ionspin.kotlin.crypto.generichash.GenericHash

class CryptoService {

    init {
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initializeWithCallback { }
        }
    }

    fun generateMasterSecret(): ByteArray = LibsodiumRandom.buf(32)

    fun deriveDbKey(masterSecret: ByteArray): ByteArray {
        require(masterSecret.size == 32) { "master_secret must be 32 bytes" }
        val salt = hkdf(masterSecret, "hisaab.db.salt".encodeToByteArray(), 16)
        return CryptoPasswordHash.pwhash(
            outputLength = 32,
            password = masterSecret,
            salt = salt,
            opsLimit = PasswordHashArgon2id.OPSLIMIT_MODERATE,
            memLimit = PasswordHashArgon2id.MEMLIMIT_MODERATE,
            algorithm = PasswordHashArgon2id.ALG_ARGON2ID13,
        )
    }

    fun hkdf(ikm: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        val state = GenericHash.genericHashInit(key = ikm, outlen = outputLength)
        GenericHash.genericHashUpdate(state, info)
        return GenericHash.genericHashFinal(state, outputLength)
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.crypto.CryptoServiceTest" --no-daemon 2>&1 | tail -15
```

Expected: `4 tests, 4 passed`.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/crypto/CryptoService.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/crypto/CryptoServiceTest.kt
git commit -m "feat(crypto): add CryptoService Argon2id key derivation + tests"
```

---

## Task 5: MnemonicService — BIP39

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/crypto/Bip39Wordlist.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/crypto/MnemonicService.kt`
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/crypto/MnemonicServiceTest.kt`

- [ ] **Step 1: Write failing tests**

Create `composeApp/src/commonTest/kotlin/app/hisaab/crypto/MnemonicServiceTest.kt`:

```kotlin
package app.hisaab.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MnemonicServiceTest {

    private val service = MnemonicService()

    @Test
    fun `encode 32 bytes produces 24 words`() {
        assertEquals(24, service.encode(ByteArray(32) { it.toByte() }).size)
    }

    @Test
    fun `all encoded words are in BIP39 wordlist`() {
        service.encode(ByteArray(32) { it.toByte() }).forEach { word ->
            assertTrue(word in BIP39_WORDLIST, "Word '$word' not in wordlist")
        }
    }

    @Test
    fun `encode then decode returns original bytes`() {
        val entropy = ByteArray(32) { (it * 7 + 3).toByte() }
        assertTrue(entropy.contentEquals(service.decode(service.encode(entropy))))
    }

    @Test
    fun `different entropy produces different words`() {
        val w1 = service.encode(ByteArray(32) { it.toByte() })
        val w2 = service.encode(ByteArray(32) { (it + 1).toByte() })
        assertTrue(w1 != w2)
    }
}
```

- [ ] **Step 2: Create Bip39Wordlist.kt**

Create `composeApp/src/commonMain/kotlin/app/hisaab/crypto/Bip39Wordlist.kt`.

This file must contain exactly 2048 words in canonical BIP39 order. Download the full list from:
```
https://raw.githubusercontent.com/trezor/python-mnemonic/master/src/mnemonic/wordlist/english.txt
```

Paste it as a Kotlin list:

```kotlin
package app.hisaab.crypto

val BIP39_WORDLIST: List<String> = listOf(
    "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract",
    // ... paste all 2048 words here ...
    "zoo"
)
```

The file must have exactly 2048 entries. Verify with:

```bash
grep -o '"[a-z]*"' composeApp/src/commonMain/kotlin/app/hisaab/crypto/Bip39Wordlist.kt | wc -l
```

Expected: `2048`.

- [ ] **Step 3: Implement MnemonicService**

Create `composeApp/src/commonMain/kotlin/app/hisaab/crypto/MnemonicService.kt`:

```kotlin
package app.hisaab.crypto

import com.ionspin.kotlin.crypto.generichash.GenericHash

class MnemonicService {

    fun encode(entropy: ByteArray): List<String> {
        require(entropy.size == 32) { "Entropy must be 32 bytes" }
        val checksum = GenericHash.genericHash(entropy, 32)[0]
        val bits = buildString {
            entropy.forEach { b -> append(b.toInt().and(0xFF).toString(2).padStart(8, '0')) }
            append(checksum.toInt().and(0xFF).toString(2).padStart(8, '0'))
        }
        return (0 until 24).map { i ->
            BIP39_WORDLIST[bits.substring(i * 11, i * 11 + 11).toInt(2)]
        }
    }

    fun decode(words: List<String>): ByteArray {
        require(words.size == 24) { "Expected 24 words, got ${words.size}" }
        val bits = buildString {
            words.forEach { word ->
                val idx = BIP39_WORDLIST.indexOf(word)
                require(idx >= 0) { "Unknown BIP39 word: $word" }
                append(idx.toString(2).padStart(11, '0'))
            }
        }
        return ByteArray(32) { i -> bits.substring(i * 8, i * 8 + 8).toInt(2).toByte() }
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.crypto.MnemonicServiceTest" --no-daemon 2>&1 | tail -15
```

Expected: `4 tests, 4 passed`.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/crypto/
git commit -m "feat(crypto): add MnemonicService BIP39 encode/decode + tests"
```

---

## Task 6: SecureStorage expect/actual

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/platform/SecureStorage.kt`
- Create: `composeApp/src/androidMain/kotlin/app/hisaab/platform/SecureStorage.kt`
- Create: `composeApp/src/iosMain/kotlin/app/hisaab/platform/SecureStorage.kt`
- Create: `composeApp/src/wasmJsMain/kotlin/app/hisaab/platform/SecureStorage.kt`

- [ ] **Step 1: commonMain expect**

Create `composeApp/src/commonMain/kotlin/app/hisaab/platform/SecureStorage.kt`:

```kotlin
package app.hisaab.platform

expect class SecureStorage {
    fun storeMasterSecret(secret: ByteArray)
    fun loadMasterSecret(): ByteArray?
    fun clearMasterSecret()
    fun storeString(key: String, value: String)
    fun loadString(key: String): String?
}
```

- [ ] **Step 2: Android actual**

Create `composeApp/src/androidMain/kotlin/app/hisaab/platform/SecureStorage.kt`:

```kotlin
package app.hisaab.platform

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

actual class SecureStorage(private val context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(true)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "hisaab_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    actual fun storeMasterSecret(secret: ByteArray) {
        prefs.edit().putString("master_secret",
            Base64.encodeToString(secret, Base64.NO_WRAP)).apply()
    }

    actual fun loadMasterSecret(): ByteArray? =
        prefs.getString("master_secret", null)
            ?.let { Base64.decode(it, Base64.NO_WRAP) }

    actual fun clearMasterSecret() {
        prefs.edit().remove("master_secret").apply()
    }

    actual fun storeString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun loadString(key: String): String? = prefs.getString(key, null)
}
```

- [ ] **Step 3: iOS actual**

Create `composeApp/src/iosMain/kotlin/app/hisaab/platform/SecureStorage.kt`:

```kotlin
package app.hisaab.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.Security.*

@OptIn(ExperimentalForeignApi::class)
actual class SecureStorage {

    actual fun storeMasterSecret(secret: ByteArray) {
        saveItem("master_secret", secret)
    }

    actual fun loadMasterSecret(): ByteArray? = loadItem("master_secret")

    actual fun clearMasterSecret() = deleteItem("master_secret")

    actual fun storeString(key: String, value: String) {
        val data = NSString.create(string = value)
            .dataUsingEncoding(NSUTF8StringEncoding) ?: return
        saveItem(key, data.toByteArray())
    }

    actual fun loadString(key: String): String? =
        loadItem(key)?.let { String(it, Charsets.UTF_8) }

    private fun saveItem(account: String, bytes: ByteArray) {
        deleteItem(account)
        memScoped {
            val data = bytes.toNSData()
            val query = mapOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrAccount to account,
                kSecAttrService to "app.hisaab",
                kSecValueData to data,
                kSecAttrAccessible to kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
            )
            SecItemAdd(query as CFDictionaryRef, null)
        }
    }

    private fun loadItem(account: String): ByteArray? {
        memScoped {
            val result = alloc<CFDictionaryRef?>()
            val query = mapOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrAccount to account,
                kSecAttrService to "app.hisaab",
                kSecReturnData to true,
                kSecMatchLimit to kSecMatchLimitOne,
            )
            val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
            if (status != errSecSuccess) return null
            return (result.value as? NSData)?.toByteArray()
        }
    }

    private fun deleteItem(account: String) {
        val query = mapOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrAccount to account,
            kSecAttrService to "app.hisaab",
        )
        SecItemDelete(query as CFDictionaryRef)
    }

    private fun ByteArray.toNSData(): NSData =
        NSData.create(bytes = this.toCValues(), length = this.size.toULong())

    private fun NSData.toByteArray(): ByteArray =
        ByteArray(this.length.toInt()).also { bytes ->
            this.bytes?.let { ptr ->
                for (i in bytes.indices) bytes[i] = (ptr as kotlinx.cinterop.ByteVar).value
            }
        }
}
```

- [ ] **Step 4: wasmJs actual**

Create `composeApp/src/wasmJsMain/kotlin/app/hisaab/platform/SecureStorage.kt`:

```kotlin
package app.hisaab.platform

actual class SecureStorage {
    actual fun storeMasterSecret(secret: ByteArray) {
        kotlinx.browser.sessionStorage.setItem("master_secret", secret.joinToString(","))
    }
    actual fun loadMasterSecret(): ByteArray? =
        kotlinx.browser.sessionStorage.getItem("master_secret")
            ?.split(",")?.map { it.toByte() }?.toByteArray()
    actual fun clearMasterSecret() {
        kotlinx.browser.sessionStorage.removeItem("master_secret")
    }
    actual fun storeString(key: String, value: String) {
        kotlinx.browser.sessionStorage.setItem(key, value)
    }
    actual fun loadString(key: String): String? =
        kotlinx.browser.sessionStorage.getItem(key)
}
```

- [ ] **Step 5: Compile check and commit**

```bash
./gradlew :composeApp:compileKotlinAndroid --no-daemon 2>&1 | tail -10
git add composeApp/src/commonMain/kotlin/app/hisaab/platform/SecureStorage.kt \
        composeApp/src/androidMain/kotlin/app/hisaab/platform/SecureStorage.kt \
        composeApp/src/iosMain/kotlin/app/hisaab/platform/SecureStorage.kt \
        composeApp/src/wasmJsMain/kotlin/app/hisaab/platform/SecureStorage.kt
git commit -m "feat(platform): add SecureStorage expect/actual"
```

---

## Task 7: BiometricAuth expect/actual

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/platform/BiometricAuth.kt`
- Create: `composeApp/src/androidMain/kotlin/app/hisaab/platform/BiometricAuth.kt`
- Create: `composeApp/src/iosMain/kotlin/app/hisaab/platform/BiometricAuth.kt`
- Create: `composeApp/src/wasmJsMain/kotlin/app/hisaab/platform/BiometricAuth.kt`

- [ ] **Step 1: commonMain expect**

Create `composeApp/src/commonMain/kotlin/app/hisaab/platform/BiometricAuth.kt`:

```kotlin
package app.hisaab.platform

sealed class BiometricResult {
    data object Success : BiometricResult()
    data class Error(val message: String) : BiometricResult()
    data object NotAvailable : BiometricResult()
    data object UserCancelled : BiometricResult()
}

expect class BiometricAuth {
    suspend fun authenticate(title: String, subtitle: String): BiometricResult
    fun isAvailable(): Boolean
}
```

- [ ] **Step 2: Android actual**

Create `composeApp/src/androidMain/kotlin/app/hisaab/platform/BiometricAuth.kt`:

```kotlin
package app.hisaab.platform

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual class BiometricAuth(private val activity: FragmentActivity) {

    actual fun isAvailable(): Boolean =
        BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS

    actual suspend fun authenticate(title: String, subtitle: String): BiometricResult =
        suspendCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                        cont.resume(BiometricResult.Success)
                    }
                    override fun onAuthenticationError(code: Int, msg: CharSequence) {
                        cont.resume(
                            if (code == BiometricPrompt.ERROR_USER_CANCELED ||
                                code == BiometricPrompt.ERROR_NEGATIVE_BUTTON)
                                BiometricResult.UserCancelled
                            else BiometricResult.Error(msg.toString())
                        )
                    }
                    override fun onAuthenticationFailed() { /* individual attempt failed, not terminal */ }
                }
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButtonText("Cancel")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .build()
            )
        }
}
```

- [ ] **Step 3: iOS actual**

Create `composeApp/src/iosMain/kotlin/app/hisaab/platform/BiometricAuth.kt`:

```kotlin
package app.hisaab.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import kotlin.coroutines.resume

actual class BiometricAuth {

    actual fun isAvailable(): Boolean =
        LAContext().canEvaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)

    actual suspend fun authenticate(title: String, subtitle: String): BiometricResult =
        suspendCancellableCoroutine { cont ->
            LAContext().evaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                localizedReason = title,
            ) { success, error ->
                cont.resume(
                    if (success) BiometricResult.Success
                    else BiometricResult.Error(error?.localizedDescription ?: "Auth failed")
                )
            }
        }
}
```

- [ ] **Step 4: wasmJs actual**

Create `composeApp/src/wasmJsMain/kotlin/app/hisaab/platform/BiometricAuth.kt`:

```kotlin
package app.hisaab.platform

actual class BiometricAuth {
    actual fun isAvailable(): Boolean = false
    actual suspend fun authenticate(title: String, subtitle: String): BiometricResult =
        BiometricResult.NotAvailable
}
```

- [ ] **Step 5: Compile check and commit**

```bash
./gradlew :composeApp:compileKotlinAndroid --no-daemon 2>&1 | tail -10
git add composeApp/src/commonMain/kotlin/app/hisaab/platform/BiometricAuth.kt \
        composeApp/src/androidMain/kotlin/app/hisaab/platform/BiometricAuth.kt \
        composeApp/src/iosMain/kotlin/app/hisaab/platform/BiometricAuth.kt \
        composeApp/src/wasmJsMain/kotlin/app/hisaab/platform/BiometricAuth.kt
git commit -m "feat(platform): add BiometricAuth expect/actual"
```

---

## Task 8: Supabase client + AuthRepository

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/auth/AuthRepository.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/auth/SupabaseClientProvider.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/auth/SupabaseAuthRepository.kt`
- Create: `composeApp/src/androidMain/kotlin/app/hisaab/auth/SupabaseClientProvider.kt`
- Create: `composeApp/src/iosMain/kotlin/app/hisaab/auth/SupabaseClientProvider.kt`
- Create: `composeApp/src/wasmJsMain/kotlin/app/hisaab/auth/SupabaseClientProvider.kt`
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/auth/FakeAuthRepository.kt`

**Before starting:** Create a free Supabase project at https://supabase.com. In the dashboard go to Authentication → Providers → Phone → enable, set to Test OTP mode. Add to `local.properties`:
```
supabase.url=https://YOUR_PROJECT_REF.supabase.co
supabase.anon_key=eyJhbGci...
```

- [ ] **Step 1: AuthRepository interface**

Create `composeApp/src/commonMain/kotlin/app/hisaab/auth/AuthRepository.kt`:

```kotlin
package app.hisaab.auth

import kotlinx.coroutines.flow.Flow

sealed class AuthEvent {
    data object SignedIn : AuthEvent()
    data object SignedOut : AuthEvent()
}

interface AuthRepository {
    suspend fun sendOtp(phone: String): Result<Unit>
    suspend fun verifyOtp(phone: String, token: String): Result<Unit>
    fun isSignedIn(): Boolean
    suspend fun signOut()
    fun authEvents(): Flow<AuthEvent>
}
```

- [ ] **Step 2: SupabaseClientProvider expect/actual**

Create `composeApp/src/commonMain/kotlin/app/hisaab/auth/SupabaseClientProvider.kt`:

```kotlin
package app.hisaab.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

expect fun supabaseUrl(): String
expect fun supabaseAnonKey(): String

val supabaseClient: SupabaseClient by lazy {
    createSupabaseClient(
        supabaseUrl = supabaseUrl(),
        supabaseKey = supabaseAnonKey(),
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
    }
}
```

Create `composeApp/src/androidMain/kotlin/app/hisaab/auth/SupabaseClientProvider.kt`:

```kotlin
package app.hisaab.auth

import app.hisaab.BuildConfig

actual fun supabaseUrl(): String = BuildConfig.SUPABASE_URL
actual fun supabaseAnonKey(): String = BuildConfig.SUPABASE_ANON_KEY
```

Create `composeApp/src/iosMain/kotlin/app/hisaab/auth/SupabaseClientProvider.kt`:

```kotlin
package app.hisaab.auth

import platform.Foundation.NSBundle

actual fun supabaseUrl(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("SUPABASE_URL") as? String ?: ""

actual fun supabaseAnonKey(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("SUPABASE_ANON_KEY") as? String ?: ""
```

Create `composeApp/src/wasmJsMain/kotlin/app/hisaab/auth/SupabaseClientProvider.kt`:

```kotlin
package app.hisaab.auth

actual fun supabaseUrl(): String = ""      // set via build config for WASM
actual fun supabaseAnonKey(): String = ""  // set via build config for WASM
```

- [ ] **Step 3: SupabaseAuthRepository**

Create `composeApp/src/commonMain/kotlin/app/hisaab/auth/SupabaseAuthRepository.kt`:

```kotlin
package app.hisaab.auth

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SupabaseAuthRepository : AuthRepository {

    private val auth get() = supabaseClient.auth

    override suspend fun sendOtp(phone: String): Result<Unit> = runCatching {
        auth.signInWith(OTP) { this.phone = phone }
    }

    override suspend fun verifyOtp(phone: String, token: String): Result<Unit> = runCatching {
        auth.verifyPhoneOtp(
            type = OtpType.Phone.SMS,
            phone = phone,
            token = token,
        )
    }

    override fun isSignedIn(): Boolean = auth.currentSessionOrNull() != null

    override suspend fun signOut() { auth.signOut() }

    override fun authEvents(): Flow<AuthEvent> =
        auth.sessionStatus.map { status ->
            when (status) {
                is SessionStatus.Authenticated -> AuthEvent.SignedIn
                else -> AuthEvent.SignedOut
            }
        }
}
```

- [ ] **Step 4: FakeAuthRepository for tests**

Create `composeApp/src/commonTest/kotlin/app/hisaab/auth/FakeAuthRepository.kt`:

```kotlin
package app.hisaab.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeAuthRepository : AuthRepository {
    var otpError: Throwable? = null
    var verifyError: Throwable? = null
    var signedIn: Boolean = false
    private val _events = MutableSharedFlow<AuthEvent>()

    override suspend fun sendOtp(phone: String): Result<Unit> {
        otpError?.let { return Result.failure(it) }
        return Result.success(Unit)
    }

    override suspend fun verifyOtp(phone: String, token: String): Result<Unit> {
        verifyError?.let { return Result.failure(it) }
        signedIn = true
        return Result.success(Unit)
    }

    override fun isSignedIn(): Boolean = signedIn
    override suspend fun signOut() { signedIn = false }
    override fun authEvents(): Flow<AuthEvent> = _events

    suspend fun emitSignedIn() = _events.emit(AuthEvent.SignedIn)
    suspend fun emitSignedOut() = _events.emit(AuthEvent.SignedOut)
}
```

- [ ] **Step 5: Compile check and commit**

```bash
./gradlew :composeApp:compileKotlinAndroid --no-daemon 2>&1 | tail -10
git add composeApp/src/commonMain/kotlin/app/hisaab/auth/ \
        composeApp/src/androidMain/kotlin/app/hisaab/auth/ \
        composeApp/src/iosMain/kotlin/app/hisaab/auth/ \
        composeApp/src/wasmJsMain/kotlin/app/hisaab/auth/ \
        composeApp/src/commonTest/kotlin/app/hisaab/auth/
git commit -m "feat(auth): add Supabase client, AuthRepository, SupabaseAuthRepository"
```

---

## Task 9: AppState + AppViewModel

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/AppViewModel.kt`
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/AppViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

Create `composeApp/src/commonTest/kotlin/app/hisaab/AppViewModelTest.kt`:

```kotlin
package app.hisaab

import app.hisaab.auth.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var fakeAuth: FakeAuthRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
        fakeAuth = FakeAuthRepository()
    }

    @AfterTest
    fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `initial state is Loading`() = runTest {
        val vm = AppViewModel(fakeAuth, hasMasterSecret = { false })
        assertIs<AppState.Loading>(vm.state.value)
    }

    @Test
    fun `no session emits Unauthenticated`() = runTest {
        fakeAuth.signedIn = false
        val vm = AppViewModel(fakeAuth, hasMasterSecret = { false })
        vm.init()
        dispatcher.scheduler.advanceUntilIdle()
        assertIs<AppState.Unauthenticated>(vm.state.value)
    }

    @Test
    fun `session with master secret emits Authenticated`() = runTest {
        fakeAuth.signedIn = true
        val vm = AppViewModel(fakeAuth, hasMasterSecret = { true })
        vm.init()
        dispatcher.scheduler.advanceUntilIdle()
        assertIs<AppState.Authenticated>(vm.state.value)
    }

    @Test
    fun `onAppBackground transitions Authenticated to Locked`() = runTest {
        fakeAuth.signedIn = true
        val vm = AppViewModel(fakeAuth, hasMasterSecret = { true })
        vm.init()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onAppBackground()
        assertIs<AppState.Locked>(vm.state.value)
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.AppViewModelTest" --no-daemon 2>&1 | tail -15
```

Expected: FAIL.

- [ ] **Step 3: Implement AppState + AppViewModel**

Create `composeApp/src/commonMain/kotlin/app/hisaab/AppViewModel.kt`:

```kotlin
package app.hisaab

import app.hisaab.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AppState {
    data object Loading : AppState()
    data object Unauthenticated : AppState()
    data class Onboarding(val step: OnboardingStep) : AppState()
    data object OnboardingKey : AppState()
    data object Locked : AppState()
    data object Authenticated : AppState()
}

enum class OnboardingStep { WELCOME, OTP, BIOMETRIC, RECOVERY_PHRASE, PROFILE }

class AppViewModel(
    private val authRepository: AuthRepository,
    private val hasMasterSecret: () -> Boolean,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state

    fun init() {
        scope.launch {
            _state.value = when {
                !authRepository.isSignedIn() -> AppState.Unauthenticated
                !hasMasterSecret() -> AppState.OnboardingKey
                else -> AppState.Authenticated
            }
        }
    }

    fun onOnboardingComplete() { _state.value = AppState.Authenticated }

    fun onAppBackground() {
        if (_state.value is AppState.Authenticated) _state.value = AppState.Locked
    }

    fun onBiometricUnlockSuccess() { _state.value = AppState.Authenticated }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.AppViewModelTest" --no-daemon 2>&1 | tail -15
```

Expected: `4 tests, 4 passed`.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/AppViewModel.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/AppViewModelTest.kt
git commit -m "feat(app): add AppState + AppViewModel with tests"
```

---

## Task 10: OnboardingViewModel

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/OnboardingViewModel.kt`
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/screens/onboarding/OnboardingViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

Create `composeApp/src/commonTest/kotlin/app/hisaab/screens/onboarding/OnboardingViewModelTest.kt`:

```kotlin
package app.hisaab.screens.onboarding

import app.hisaab.auth.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var fakeAuth: FakeAuthRepository

    @BeforeTest
    fun setup() { Dispatchers.setMain(dispatcher); fakeAuth = FakeAuthRepository() }

    @AfterTest
    fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `initial state has empty phone and otpSent false`() = runTest {
        val vm = OnboardingViewModel(fakeAuth)
        assertEquals("", vm.state.value.phone)
        assertFalse(vm.state.value.otpSent)
    }

    @Test
    fun `sendOtp success sets otpSent true`() = runTest {
        val vm = OnboardingViewModel(fakeAuth)
        vm.sendOtp("+8801700000000")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.otpSent)
    }

    @Test
    fun `sendOtp failure sets error`() = runTest {
        fakeAuth.otpError = RuntimeException("Network error")
        val vm = OnboardingViewModel(fakeAuth)
        vm.sendOtp("+8801700000000")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Network error", vm.state.value.error)
    }

    @Test
    fun `acknowledgePhraseWrittenDown sets phraseAcknowledged true`() = runTest {
        val vm = OnboardingViewModel(fakeAuth)
        assertFalse(vm.state.value.phraseAcknowledged)
        vm.acknowledgePhraseWrittenDown()
        assertTrue(vm.state.value.phraseAcknowledged)
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.screens.onboarding.OnboardingViewModelTest" --no-daemon 2>&1 | tail -15
```

Expected: FAIL.

- [ ] **Step 3: Implement OnboardingViewModel**

Create `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/OnboardingViewModel.kt`:

```kotlin
package app.hisaab.screens.onboarding

import app.hisaab.auth.AuthRepository
import app.hisaab.crypto.CryptoService
import app.hisaab.crypto.MnemonicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingState(
    val phone: String = "",
    val otpSent: Boolean = false,
    val otpVerified: Boolean = false,
    val recoveryPhrase: List<String> = emptyList(),
    val phraseAcknowledged: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class OnboardingViewModel(
    private val authRepository: AuthRepository,
    private val cryptoService: CryptoService = CryptoService(),
    private val mnemonicService: MnemonicService = MnemonicService(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    private var masterSecret: ByteArray? = null

    fun sendOtp(phone: String) {
        scope.launch {
            _state.update { it.copy(phone = phone, isLoading = true, error = null) }
            authRepository.sendOtp(phone)
                .onSuccess { _state.update { it.copy(otpSent = true, isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun verifyOtp(token: String) {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authRepository.verifyOtp(_state.value.phone, token)
                .onSuccess { _state.update { it.copy(otpVerified = true, isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun generateMasterSecret(onGenerated: (ByteArray) -> Unit) {
        val secret = cryptoService.generateMasterSecret()
        masterSecret = secret
        onGenerated(secret)
    }

    fun generateRecoveryPhrase(): List<String> {
        val secret = masterSecret ?: return emptyList()
        val words = mnemonicService.encode(secret)
        _state.update { it.copy(recoveryPhrase = words) }
        return words
    }

    fun acknowledgePhraseWrittenDown() {
        _state.update { it.copy(phraseAcknowledged = true) }
    }

    fun getMasterSecretAndClear(): ByteArray? {
        val secret = masterSecret?.copyOf()
        masterSecret?.fill(0)
        masterSecret = null
        return secret
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.screens.onboarding.OnboardingViewModelTest" --no-daemon 2>&1 | tail -15
```

Expected: `4 tests, 4 passed`.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/OnboardingViewModel.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/screens/onboarding/OnboardingViewModelTest.kt
git commit -m "feat(onboarding): add OnboardingViewModel with tests"
```

---

## Task 11: WelcomeScreen + OtpScreen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/WelcomeScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/OtpScreen.kt`

- [ ] **Step 1: Implement WelcomeScreen**

Create `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/WelcomeScreen.kt`:

```kotlin
package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette

@Composable
fun WelcomeScreen(
    onSendOtp: (phone: String) -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
) {
    val palette = LocalHisaabPalette.current
    var phone by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(HisaabSpacing.gutter.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("হিসাব · Hisaab", color = palette.accent, fontSize = 12.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Your money,\nonly yours.",
                color = palette.onBackground,
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Privacy-first finance for Bangladesh. Everything stays on your device.",
                color = palette.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column {
            error?.let {
                Text(it, color = palette.negative, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
            }
            Text("Phone number", color = palette.muted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("1X XXXX XXXX", color = palette.muted) },
                prefix = { Text("+880 ", color = palette.accent) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (phone.length >= 10) onSendOtp("+880$phone")
                }),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onSendOtp("+880$phone") },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = phone.length >= 10 && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = palette.background)
                else Text("Continue", color = palette.background)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
```

- [ ] **Step 2: Implement OtpScreen**

Create `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/OtpScreen.kt`:

```kotlin
package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette

@Composable
fun OtpScreen(
    phone: String,
    onVerify: (token: String) -> Unit,
    onResend: () -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
) {
    val palette = LocalHisaabPalette.current
    var otp by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(HisaabSpacing.gutter.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("Verify", color = palette.accent)
            Spacer(Modifier.height(12.dp))
            Text("Enter the code\nwe sent you", style = MaterialTheme.typography.headlineMedium, color = palette.onBackground)
            Spacer(Modifier.height(8.dp))
            Text("Sent to $phone", color = palette.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                value = otp,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) otp = it },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                placeholder = { Text("6-digit code", color = palette.muted) },
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = palette.negative, style = MaterialTheme.typography.labelSmall)
            }
        }
        Column {
            Button(
                onClick = { onVerify(otp) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = otp.length == 6 && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = palette.background)
                else Text("Verify", color = palette.background)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onResend, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Resend code", color = palette.muted)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
```

- [ ] **Step 3: Compile check and commit**

```bash
./gradlew :composeApp:compileKotlinAndroid --no-daemon 2>&1 | tail -10
git add composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/WelcomeScreen.kt \
        composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/OtpScreen.kt
git commit -m "feat(onboarding): add WelcomeScreen and OtpScreen"
```

---

## Task 12: BiometricSetupScreen + RecoveryPhraseScreen + ProfileSetupScreen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/BiometricSetupScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/RecoveryPhraseScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/ProfileSetupScreen.kt`

- [ ] **Step 1: BiometricSetupScreen**

Create `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/BiometricSetupScreen.kt`:

```kotlin
package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hisaab.design.LocalHisaabPalette

@Composable
fun BiometricSetupScreen(
    isAvailable: Boolean,
    onEnroll: () -> Unit,
    onSkip: () -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
) {
    val palette = LocalHisaabPalette.current
    Column(
        modifier = Modifier.fillMaxSize().background(palette.background).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f).wrapContentHeight(Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Secure", color = palette.accent)
            Spacer(Modifier.height(12.dp))
            Text("Unlock with your\nface or finger", style = MaterialTheme.typography.headlineMedium, color = palette.onBackground)
            Spacer(Modifier.height(12.dp))
            Text("Your biometric unlocks Hisaab. Your data never leaves this device.", color = palette.muted, style = MaterialTheme.typography.bodyMedium)
            error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = palette.negative) }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onEnroll,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = isAvailable && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            ) {
                Text(if (isAvailable) "Enable biometric" else "Not available on this device", color = palette.background)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Skip (not recommended)", color = palette.muted)
            }
        }
    }
}
```

- [ ] **Step 2: RecoveryPhraseScreen**

Create `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/RecoveryPhraseScreen.kt`:

```kotlin
package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.LocalHisaabPalette

@Composable
fun RecoveryPhraseScreen(
    words: List<String>,
    onAcknowledged: () -> Unit,
) {
    val palette = LocalHisaabPalette.current
    var acknowledged by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(palette.background).padding(22.dp),
    ) {
        Text("Recovery", color = palette.accent)
        Spacer(Modifier.height(8.dp))
        Text("Write these 24 words down", style = MaterialTheme.typography.headlineSmall, color = palette.onBackground)
        Spacer(Modifier.height(4.dp))
        Text("⚠ Lose these = lose your data", color = palette.negative, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(words) { index, word ->
                Row(
                    modifier = Modifier
                        .border(1.dp, palette.rule, MaterialTheme.shapes.small)
                        .background(palette.surface, MaterialTheme.shapes.small)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${index + 1}", color = palette.accent, fontSize = 11.sp, modifier = Modifier.width(20.dp))
                    Text(word, color = palette.onBackground, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, palette.rule, MaterialTheme.shapes.small)
                .background(palette.surface, MaterialTheme.shapes.small)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = acknowledged,
                onCheckedChange = { acknowledged = it },
                colors = CheckboxDefaults.colors(checkedColor = palette.accent),
            )
            Spacer(Modifier.width(8.dp))
            Text("I've written down all 24 words in a safe place", color = palette.onBackground, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onAcknowledged,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = acknowledged,
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
        ) {
            Text("I've saved them", color = palette.background)
        }
    }
}
```

- [ ] **Step 3: ProfileSetupScreen**

Create `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/ProfileSetupScreen.kt`:

```kotlin
package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hisaab.design.LocalHisaabPalette

@Composable
fun ProfileSetupScreen(
    onComplete: (name: String, locale: String) -> Unit,
    isLoading: Boolean = false,
) {
    val palette = LocalHisaabPalette.current
    var name by remember { mutableStateOf("") }
    var locale by remember { mutableStateOf("en") }

    Column(
        modifier = Modifier.fillMaxSize().background(palette.background).padding(22.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("Almost done", color = palette.accent)
            Spacer(Modifier.height(12.dp))
            Text("What should\nwe call you?", style = MaterialTheme.typography.headlineMedium, color = palette.onBackground)
            Spacer(Modifier.height(24.dp))
            Text("Your name", color = palette.muted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Name", color = palette.muted) },
            )
            Spacer(Modifier.height(20.dp))
            Text("Language", color = palette.muted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { locale = "en" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (locale == "en") palette.accent else palette.surface,
                        contentColor = if (locale == "en") palette.background else palette.onBackground,
                    ),
                ) { Text("English") }
                Button(
                    onClick = { locale = "bn" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (locale == "bn") palette.accent else palette.surface,
                        contentColor = if (locale == "bn") palette.background else palette.onBackground,
                    ),
                ) { Text("বাংলা") }
            }
        }
        Button(
            onClick = { onComplete(name, locale) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = name.isNotBlank() && !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = palette.background)
            else Text("Start Hisaab →", color = palette.background)
        }
    }
}
```

- [ ] **Step 4: Compile check and commit**

```bash
./gradlew :composeApp:compileKotlinAndroid --no-daemon 2>&1 | tail -10
git add composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/
git commit -m "feat(onboarding): add BiometricSetup, RecoveryPhrase, ProfileSetup screens"
```

---

## Task 13: SplashScreen + LockScreen + OnboardingGraph

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/SplashScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/LockScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/OnboardingGraph.kt`

- [ ] **Step 1: SplashScreen**

Create `composeApp/src/commonMain/kotlin/app/hisaab/screens/SplashScreen.kt`:

```kotlin
package app.hisaab.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.LocalHisaabPalette

@Composable
fun SplashScreen() {
    val palette = LocalHisaabPalette.current
    Box(
        modifier = Modifier.fillMaxSize().background(palette.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("হিসাব", color = palette.accent, fontSize = 32.sp)
            CircularProgressIndicator(color = palette.accent)
        }
    }
}
```

- [ ] **Step 2: LockScreen**

Create `composeApp/src/commonMain/kotlin/app/hisaab/screens/LockScreen.kt`:

```kotlin
package app.hisaab.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hisaab.design.LocalHisaabPalette

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val palette = LocalHisaabPalette.current
    Box(
        modifier = Modifier.fillMaxSize().background(palette.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("Hisaab is locked", style = MaterialTheme.typography.headlineSmall, color = palette.onBackground)
            Button(
                onClick = onUnlock,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            ) {
                Text("Unlock with biometric", color = palette.background)
            }
        }
    }
}
```

- [ ] **Step 3: OnboardingGraph**

Add navigation compose to `commonMain.dependencies` in `build.gradle.kts` (already done in Task 1 if you added `libs.nav.compose`). Then create:

Create `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/OnboardingGraph.kt`:

```kotlin
package app.hisaab.screens.onboarding

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun OnboardingGraph(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsState()

    NavHost(navController = navController, startDestination = "welcome") {
        composable("welcome") {
            WelcomeScreen(
                onSendOtp = { phone ->
                    viewModel.sendOtp(phone)
                },
                isLoading = state.isLoading,
                error = state.error,
            )
        }
        composable("otp") {
            OtpScreen(
                phone = state.phone,
                onVerify = { token -> viewModel.verifyOtp(token) },
                onResend = { viewModel.sendOtp(state.phone) },
                isLoading = state.isLoading,
                error = state.error,
            )
        }
        composable("biometric") {
            BiometricSetupScreen(
                isAvailable = true,
                onEnroll = {
                    viewModel.generateMasterSecret { _ ->
                        navController.navigate("recovery_phrase")
                    }
                },
                onSkip = {
                    viewModel.generateMasterSecret { _ ->
                        navController.navigate("recovery_phrase")
                    }
                },
            )
        }
        composable("recovery_phrase") {
            RecoveryPhraseScreen(
                words = viewModel.generateRecoveryPhrase(),
                onAcknowledged = {
                    viewModel.acknowledgePhraseWrittenDown()
                    navController.navigate("profile")
                },
            )
        }
        composable("profile") {
            ProfileSetupScreen(
                onComplete = { _, _ -> onComplete() },
                isLoading = state.isLoading,
            )
        }
    }

    // Advance to otp screen when OTP is sent
    LaunchedEffect(state.otpSent) {
        if (state.otpSent) navController.navigate("otp") { launchSingleTop = true }
    }
    // Advance to biometric when OTP verified
    LaunchedEffect(state.otpVerified) {
        if (state.otpVerified) navController.navigate("biometric") { launchSingleTop = true }
    }
}
```

- [ ] **Step 4: Compile check and commit**

```bash
./gradlew :composeApp:compileKotlinAndroid --no-daemon 2>&1 | tail -10
git add composeApp/src/commonMain/kotlin/app/hisaab/screens/
git commit -m "feat(app): add SplashScreen, LockScreen, OnboardingGraph"
```

---

## Task 14: Wire App() to AppViewModel + full build

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/App.kt`

- [ ] **Step 1: Update App.kt**

Open `composeApp/src/commonMain/kotlin/app/hisaab/App.kt` and replace its contents:

```kotlin
package app.hisaab

import androidx.compose.runtime.*
import app.hisaab.auth.FakeAuthRepository
import app.hisaab.screens.LockScreen
import app.hisaab.screens.SplashScreen
import app.hisaab.screens.onboarding.OnboardingGraph
import app.hisaab.screens.onboarding.OnboardingViewModel
import app.hisaab.screens.today.TodayScreen
import app.hisaab.design.HisaabTheme

@Composable
fun App() {
    HisaabTheme {
        // TODO P0b wiring: replace FakeAuthRepository with SupabaseAuthRepository
        // once Supabase credentials are configured in local.properties
        val authRepository = remember { FakeAuthRepository() }
        val appViewModel = remember {
            AppViewModel(
                authRepository = authRepository,
                hasMasterSecret = { false },
            ).also { it.init() }
        }
        val onboardingViewModel = remember { OnboardingViewModel(authRepository) }
        val state by appViewModel.state.collectAsState()

        when (state) {
            is AppState.Loading -> SplashScreen()
            is AppState.Unauthenticated,
            is AppState.Onboarding -> OnboardingGraph(
                viewModel = onboardingViewModel,
                onComplete = { appViewModel.onOnboardingComplete() },
            )
            is AppState.Locked -> LockScreen(
                onUnlock = { appViewModel.onBiometricUnlockSuccess() },
            )
            is AppState.Authenticated,
            is AppState.OnboardingKey -> TodayScreen()
        }
    }
}
```

Note: `FakeAuthRepository` is used here temporarily so the app compiles and shows screens without real Supabase credentials. Switch to `SupabaseAuthRepository()` once `local.properties` has valid credentials and the Supabase project has Phone auth enabled.

- [ ] **Step 2: Run all unit tests**

```bash
./gradlew :composeApp:testDebugUnitTest --no-daemon 2>&1 | tail -20
```

Expected: All tests pass (`CryptoServiceTest` × 4, `MnemonicServiceTest` × 4, `AppViewModelTest` × 4, `OnboardingViewModelTest` × 4 = 16 tests).

- [ ] **Step 3: Build the debug APK**

```bash
./gradlew :composeApp:assembleDebug --no-daemon 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Final commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/App.kt
git commit -m "feat(p0b): wire App() to AppViewModel state-driven navigation"
```

---

## Verification checklist

Before marking P0b complete:

- [ ] `./gradlew :composeApp:testDebugUnitTest` — 16+ tests pass
- [ ] `./gradlew :composeApp:assembleDebug` — BUILD SUCCESSFUL
- [ ] App launches → WelcomeScreen shown (FakeAuthRepository returns not-signed-in)
- [ ] Phone + Continue → OTP screen shown
- [ ] OTP entry (any 6 digits with FakeAuthRepository) → BiometricSetupScreen
- [ ] Recovery phrase screen shows 24 words; checkbox gates the CTA
- [ ] Profile screen → Today screen after "Start Hisaab →"
- [ ] Switch `App.kt` to `SupabaseAuthRepository()` + add Supabase credentials → real OTP works via Supabase test mode
