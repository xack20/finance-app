package app.hisaab.screens.today

import app.hisaab.data.AccountRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.domain.MoneyTotals
import app.hisaab.domain.TransactionRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TransactionRowDisplay(
    val row: TransactionRow,
    val accountName: String,
    val merchantName: String?,
    val categoryName: String?,
    val categoryColor: String?,
)

class TodayViewModel(
    txnRepo: TransactionRepository,
    accountRepo: AccountRepository,
    categoryRepo: CategoryRepository,
    merchantRepo: MerchantRepository,
    inboxRepo: CaptureInboxRepository,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
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
            )
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingCount: StateFlow<Long> = inboxRepo.observePendingCount()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0L)
}
