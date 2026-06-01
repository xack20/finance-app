package app.hisaab.screens.today

import app.hisaab.data.AccountRepository
import app.hisaab.data.CaptureConfigRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.domain.MoneyTotals
import app.hisaab.domain.TransactionRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TransactionRowDisplay(
    val row: TransactionRow,
    val accountName: String,
    val merchantName: String?,
    val categoryName: String?,
    val categoryColor: String?,
    val categoryIcon: String? = null,
)

class TodayViewModel(
    txnRepo: TransactionRepository,
    accountRepo: AccountRepository,
    categoryRepo: CategoryRepository,
    merchantRepo: MerchantRepository,
    inboxRepo: CaptureInboxRepository,
    /** Reads the persisted opt-in seen flag. Seam keeps tests free of platform [SecureStorage]. */
    private val loadOptInSeen: () -> Boolean,
    /** Persists the opt-in seen flag. Seam keeps tests free of platform [SecureStorage]. */
    private val saveOptInSeen: () -> Unit,
    captureConfigRepo: CaptureConfigRepository,
    /** Whether the current platform supports SMS capture. Plain Boolean keeps tests simple. */
    smsCapable: Boolean,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    val todayNet: StateFlow<MoneyTotals> = txnRepo.observeTodayNet()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), MoneyTotals(0.0, 0.0, 0.0))

    val recent: StateFlow<List<TransactionRowDisplay>> = combine(
        txnRepo.observeRecent(50),
        accountRepo.observeActive(),
        categoryRepo.observeAll(),
        merchantRepo.observeAll(),
    ) { txns, accounts, cats, merchants ->
        val accountsById = accounts.associateBy { it.id }
        val catsById = cats.associateBy { it.id }
        val merchantsById = merchants.associateBy { it.id }
        txns.map { txn ->
            TransactionRowDisplay(
                row = txn,
                accountName = accountsById[txn.accountId]?.name ?: "?",
                merchantName = txn.merchantId?.let { merchantsById[it]?.name },
                categoryName = txn.categoryId?.let { catsById[it]?.name },
                categoryColor = txn.categoryId?.let { catsById[it]?.color },
                categoryIcon = txn.categoryId?.let { catsById[it]?.icon },
            )
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingCount: StateFlow<Long> = inboxRepo.observePendingCount()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** True once the user has dismissed the capture opt-in card (persisted across restarts). */
    private val _captureOptInSeen = MutableStateFlow(loadOptInSeen())
    val captureOptInSeen: StateFlow<Boolean> = _captureOptInSeen.asStateFlow()

    /** Whether SMS auto-capture is already enabled (from the live config). */
    val captureEnabled: StateFlow<Boolean> = captureConfigRepo.observe()
        .map { it.captureEnabled }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether the current platform supports SMS capture at all. */
    val smsSupported: Boolean = smsCapable

    /**
     * Persists the "seen" flag so the opt-in card never reappears, regardless of which
     * button the user tapped (Turn on or Maybe later). Both call-sites in TodayScreen must
     * call this before their respective navigation/no-op action.
     */
    fun dismissOptIn() {
        saveOptInSeen()
        _captureOptInSeen.value = true
    }
}
