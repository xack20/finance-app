package app.hisaab

import android.content.Context
import androidx.fragment.app.FragmentActivity
import app.cash.sqldelight.db.SqlDriver
import app.hisaab.auth.AuthRepository
import app.hisaab.auth.SupabaseAuthRepository
import app.hisaab.crypto.BlobCrypto
import app.hisaab.crypto.CryptoService
import app.hisaab.crypto.MnemonicService
import app.hisaab.data.AccountRepository
import app.hisaab.data.AttachmentRepository
import app.hisaab.data.BudgetRepository
import app.hisaab.data.CaptureConfigRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.InsightRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.SenderRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
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

    private fun requireDb(): HisaabDatabase = cachedDatabase
        ?: error("Database not open — onboarding incomplete")

    actual val accountRepository: AccountRepository
        get() = AccountRepository(requireDb())
    actual val categoryRepository: CategoryRepository
        get() = CategoryRepository(requireDb())
    actual val merchantRepository: MerchantRepository
        get() = MerchantRepository(requireDb())
    actual val tagRepository: TagRepository
        get() = TagRepository(requireDb())
    actual val transactionRepository: TransactionRepository
        get() = TransactionRepository(requireDb(), merchantRepository, tagRepository)
    actual val personRepository: PersonRepository
        get() = PersonRepository(requireDb())
    actual val lendBorrowRepository: LendBorrowRepository
        get() = LendBorrowRepository(requireDb(), transactionRepository)
    actual val budgetRepository: BudgetRepository
        get() = BudgetRepository(requireDb())
    actual val attachmentRepository: AttachmentRepository
        get() = AttachmentRepository(
            db = requireDb(),
            blobCrypto = blobCrypto,
            masterSecretProvider = ::masterSecretInMemory,
            fileStore = fileStore,
            cryptoService = cryptoService,
        )
    actual val insightRepository: InsightRepository
        get() = InsightRepository(requireDb())
    actual val captureInboxRepository: CaptureInboxRepository
        get() = CaptureInboxRepository(requireDb())
    actual val senderRepository: SenderRepository
        get() = SenderRepository(requireDb())
    actual val captureConfigRepository: CaptureConfigRepository
        get() = CaptureConfigRepository(requireDb())

    actual fun openDatabase(masterSecret: ByteArray): HisaabDatabase {
        cachedDatabase?.let { return it }
        cachedMasterSecret = masterSecret.copyOf()
        val keyCopy = cryptoService.deriveDbKey(masterSecret)
        val driver = databaseDriverFactory.createDriver(keyCopy)
        // DatabaseDriverFactory zeroes keyCopy after SQLCipher copies it (P0b T3 fix).
        cachedDriver = driver
        val db = HisaabDatabase(driver)
        cachedDatabase = db
        // Idempotent auto-seed of default Cash account + 12 default categories.
        // runBlocking is acceptable here because callers (OnboardingViewModel.completeProfile,
        // LockScreen.attemptUnlock, RecoveryEntryScreen.attemptRestore) are already in a coroutine.
        kotlinx.coroutines.runBlocking {
            categoryRepository.ensureDefaults()
            accountRepository.ensureDefaultCashAccount()
        }
        return db
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
