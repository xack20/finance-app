package app.hisaab

import android.content.Context
import androidx.fragment.app.FragmentActivity
import app.cash.sqldelight.db.SqlDriver
import app.hisaab.auth.AuthRepository
import app.hisaab.auth.SupabaseAuthRepository
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

actual class AppContainer(
    private val context: Context,
    activity: FragmentActivity,
) {
    actual val secureStorage: SecureStorage = SecureStorage(context)
    actual val biometricAuth: BiometricAuth = BiometricAuth(activity)
    actual val contactPicker: ContactPicker = ContactPicker(activity)
    actual val imagePicker: ImagePicker = ImagePicker(activity)
    actual val fileStore: PlatformFileStore = PlatformFileStore(context)
    actual val lifecycle: AppLifecycle = AppLifecycle()

    actual val cryptoService: CryptoService = CryptoService()
    actual val mnemonicService: MnemonicService = MnemonicService()
    actual val blobCrypto: BlobCrypto = BlobCrypto()

    actual val authRepository: AuthRepository = SupabaseAuthRepository()
    actual val databaseDriverFactory: DatabaseDriverFactory = DatabaseDriverFactory(context)

    private var cachedDriver: SqlDriver? = null
    private var cachedDatabase: HisaabDatabase? = null
    private var cachedMasterSecret: ByteArray? = null

    actual fun masterSecretInMemory(): ByteArray? = cachedMasterSecret?.copyOf()

    actual fun openDatabase(masterSecret: ByteArray): HisaabDatabase {
        cachedDatabase?.let { return it }
        cachedMasterSecret = masterSecret.copyOf()
        val keyCopy = cryptoService.deriveDbKey(masterSecret)
        val driver = databaseDriverFactory.createDriver(keyCopy)
        // DatabaseDriverFactory zeroes keyCopy after SQLCipher copies it (P0b T3 fix).
        cachedDriver = driver
        return HisaabDatabase(driver).also { cachedDatabase = it }
    }

    actual fun databaseOrNull(): HisaabDatabase? = cachedDatabase

    actual fun closeDatabase() {
        cachedDriver?.close()
        cachedDriver = null
        cachedDatabase = null
        cachedMasterSecret?.fill(0)
        cachedMasterSecret = null
    }
}
