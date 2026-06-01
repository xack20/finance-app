package app.hisaab.screens.transaction

import app.hisaab.data.AccountRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.domain.CandidateTransaction
import app.hisaab.screens.today.TransactionRowDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionDetailViewModel(
    private val txnId: String,
    private val txnRepo: TransactionRepository,
    accountRepo: AccountRepository,
    categoryRepo: CategoryRepository,
    merchantRepo: MerchantRepository,
    private val inboxRepo: CaptureInboxRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    // Use observeById so the detail screen works for ANY transaction regardless of its position
    // in history, and reacts to deletion (emits null → screen can pop/handle).
    val row: StateFlow<TransactionRowDisplay?> = combine(
        txnRepo.observeById(txnId),
        accountRepo.observeActive(),
        categoryRepo.observeAll(),
        merchantRepo.observeAll(),
    ) { txn, accounts, cats, merchants ->
        if (txn == null) {
            null
        } else {
            val account = accounts.firstOrNull { it.id == txn.accountId }
            val cat = txn.categoryId?.let { id -> cats.firstOrNull { it.id == id } }
            val mer = txn.merchantId?.let { id -> merchants.firstOrNull { it.id == id } }
            TransactionRowDisplay(
                row = txn,
                accountName = account?.name ?: "?",
                merchantName = mer?.name,
                categoryName = cat?.name,
                categoryColor = cat?.color,
                categoryIcon = cat?.icon,
            )
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private val _provenance = MutableStateFlow<CandidateTransaction?>(null)
    val provenance: StateFlow<CandidateTransaction?> = _provenance.asStateFlow()

    init {
        scope.launch {
            // Load the txn directly (not via the combine Flow) so provenance loads
            // even before the screen's WhileSubscribed window starts.
            val txn = txnRepo.getById(txnId) ?: return@launch
            txn.captureId?.let { capId ->
                _provenance.value = inboxRepo.getById(capId)
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        scope.launch {
            txnRepo.delete(txnId)
            onDone()
        }
    }
}
