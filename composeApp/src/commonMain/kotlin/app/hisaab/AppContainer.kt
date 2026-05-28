package app.hisaab

import androidx.compose.runtime.staticCompositionLocalOf
import app.hisaab.auth.AuthRepository
import app.hisaab.crypto.BlobCrypto
import app.hisaab.crypto.CryptoService
import app.hisaab.crypto.MnemonicService
import app.hisaab.db.DatabaseDriverFactory
import app.hisaab.db.HisaabDatabase
import app.hisaab.platform.AppLifecycle
import app.hisaab.platform.BiometricAuth
import app.hisaab.platform.ContactPicker
import app.hisaab.platform.ImagePicker
import app.hisaab.platform.PlatformFileStore
import app.hisaab.platform.SecureStorage

/**
 * Hand-wired DI root. One instance per Application lifetime.
 *
 * P0c-1 wiring: cryptography, secure storage, biometric, DB factory,
 * Supabase auth, lifecycle observer. P0c-2 Task 16 adds repository instances.
 *
 * `database` is null until `openDatabase(masterSecret)` succeeds.
 * Subsequent calls re-use the cached driver until `closeDatabase()` is called
 * (on background lock or sign-out).
 */
expect class AppContainer {
    val secureStorage: SecureStorage
    val biometricAuth: BiometricAuth
    val contactPicker: ContactPicker
    val imagePicker: ImagePicker
    val fileStore: PlatformFileStore
    val lifecycle: AppLifecycle

    val cryptoService: CryptoService
    val mnemonicService: MnemonicService
    val blobCrypto: BlobCrypto

    val authRepository: AuthRepository
    val databaseDriverFactory: DatabaseDriverFactory

    /** Master secret cached in memory while DB is open. null when locked or pre-onboarding. */
    fun masterSecretInMemory(): ByteArray?

    /** Opens (or re-opens) the encrypted DB with the given key. Idempotent. */
    fun openDatabase(masterSecret: ByteArray): HisaabDatabase

    /** Returns the currently-open DB, or null if locked / pre-onboarding. */
    fun databaseOrNull(): HisaabDatabase?

    /** Closes the driver and zeroes the in-memory master_secret. */
    fun closeDatabase()
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided — wrap App() in CompositionLocalProvider(LocalAppContainer provides ...)")
}
