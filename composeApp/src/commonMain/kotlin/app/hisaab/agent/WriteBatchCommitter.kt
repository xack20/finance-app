// WriteBatchCommitter.kt
package app.hisaab.agent
import app.hisaab.data.AccountRepository
import app.hisaab.data.BudgetRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.LendBorrowDirection
import app.hisaab.domain.NewLendBorrow
import app.hisaab.domain.NewSplitTransaction
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TxnKind
import app.hisaab.domain.TxnSource
import app.hisaab.domain.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/** What actually committed (basis for §11 applied_summary persistence in M4-3). */
@Serializable
data class AppliedSummary(
    val accountsCreated: Int = 0,
    val categoriesCreated: Int = 0,
    val transactionsAdded: Int = 0,
    val lendBorrowsRecorded: Int = 0,
    val transfers: Int = 0,
    val budgetsSet: Int = 0,
    val recategorized: Int = 0,
    val splitTransactionsAdded: Int = 0,
)

/**
 * Sole Apply writer for an agent turn. Commits a batch of ProposedWrites in ONE db.transaction,
 * FK-ordered (accounts → categories → persons → txn/lend_borrow/transfer). Entity references are by
 * name, resolved against existing rows plus rows created earlier in the same batch. All-or-nothing:
 * any failure inside the transaction rolls the whole batch back.
 */
class WriteBatchCommitter(
    private val db: HisaabDatabase,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val persons: PersonRepository,
    private val txns: TransactionRepository,
    private val lendBorrow: LendBorrowRepository,
    private val budgets: BudgetRepository,
) {
    /** @throws IllegalArgumentException if a referenced account cannot be resolved (rolls back). */
    suspend fun apply(writes: List<ProposedWrite>): AppliedSummary {
        // Reads happen BEFORE the transaction (Flow.first is suspend; the transaction block is not).
        val accountIdByName = accounts.observeActive().first()
            .associate { it.name.lowercase() to it.id }.toMutableMap()
        val categoryIdByName = categories.observeAll().first()
            .associate { it.name.lowercase() to it.id }.toMutableMap()
        val now = Clock.System.now().toEpochMilliseconds()
        val nowDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val currentMonth = YearMonth.of(nowDate.year, nowDate.monthNumber)

        val intents = writes.mapNotNull { WriteIntent.parse(it) }

        // Use plain var counters — Kotlin lambdas cannot reassign captured vars from outer scope.
        var accts = 0
        var cats = 0
        var txnsAdded = 0
        var lbs = 0
        var xfers = 0
        var budgetsSet = 0
        var recats = 0
        var splits = 0

        db.transaction {
            // 1. accounts
            intents.filterIsInstance<WriteIntent.CreateAccount>().forEach { i ->
                val kind = runCatching { AccountKind.valueOf(i.kind) }.getOrDefault(AccountKind.CASH)
                val id = accounts.addBlocking(i.name, kind, institution = null,
                    creditLimit = i.creditLimit, statementDay = i.statementDay, dueDay = i.dueDay)
                accountIdByName[i.name.lowercase()] = id
                accts++
            }
            // 2. categories
            intents.filterIsInstance<WriteIntent.CreateCategory>().forEach { i ->
                val parentId = i.parent?.let { categoryIdByName[it.lowercase()] }
                val id = categories.addBlocking(i.name, color = null, icon = null, parentId = parentId)
                categoryIdByName[i.name.lowercase()] = id
                cats++
            }
            fun acct(name: String): String = accountIdByName[name.lowercase()]
                ?: throw IllegalArgumentException("unknown account '$name'")
            fun cat(name: String?): String? = name?.let { categoryIdByName[it.lowercase()] }
            // 3. txn / lend_borrow / transfer / card payment
            intents.forEach { i ->
                when (i) {
                    is WriteIntent.AddTransaction -> {
                        txns.addBlocking(NewTransaction(
                            accountId = acct(i.account), amount = i.amount, ts = i.ts ?: now,
                            merchantName = i.merchant, categoryId = cat(i.category), notes = i.notes,
                            kind = runCatching { TxnKind.valueOf(i.kind) }.getOrDefault(TxnKind.EXPENSE),
                            source = TxnSource.CHAT,
                        ))
                        txnsAdded++
                    }
                    is WriteIntent.Transfer -> {
                        txns.transferBlocking(acct(i.fromAccount), acct(i.toAccount), i.amount, ts = now, notes = i.notes)
                        xfers++
                    }
                    is WriteIntent.CardPayment -> {
                        txns.transferBlocking(acct(i.fromAccount), acct(i.card), i.amount, ts = now, notes = "card payment")
                        xfers++
                    }
                    is WriteIntent.RecordLendBorrow -> {
                        val personId = persons.findByNameBlocking(i.person) ?: persons.addManualBlocking(i.person)
                        val dir = runCatching { LendBorrowDirection.valueOf(i.kind) }.getOrDefault(LendBorrowDirection.LENT)
                        lendBorrow.recordBlocking(NewLendBorrow(
                            personId = personId, amount = i.amount, direction = dir,
                            accountId = acct(i.fromAccount), purpose = i.purpose, ts = now, dueDate = null,
                        ))
                        lbs++
                    }
                    is WriteIntent.SetBudget -> {
                        val catId = cat(i.category) ?: throw IllegalArgumentException("unknown category '${i.category}'")
                        // YearMonth(String) only checks "YYYY-MM" shape, not that MM is 1..12. The agent
                        // supplies this string, so validate the month range too; anything invalid/malformed
                        // falls back to the current month rather than persisting a nonsensical budget period.
                        val month = i.month
                            ?.let { runCatching { require(it.substringAfter('-').toInt() in 1..12); YearMonth(it) }.getOrNull() }
                            ?: currentMonth
                        budgets.setBlocking(catId, i.amount, month)
                        budgetsSet++
                    }
                    is WriteIntent.Recategorize -> {
                        val catId = cat(i.category) ?: throw IllegalArgumentException("unknown category '${i.category}'")
                        txns.recategorizeBlocking(i.transactionId, catId)
                        recats++
                    }
                    is WriteIntent.AddSplitTransaction -> {
                        val kind = runCatching { TxnKind.valueOf(i.kind) }.getOrDefault(TxnKind.EXPENSE)
                        val parentId = txns.addBlocking(NewTransaction(
                            accountId = acct(i.account), amount = i.amount, ts = now,
                            merchantName = null, categoryId = null, notes = null, kind = kind,
                            source = TxnSource.CHAT,
                        ))
                        txns.addSplitsBlocking(parentId, i.splits.map { leg ->
                            NewSplitTransaction(amount = leg.amount, categoryId = cat(leg.category),
                                notes = leg.notes, kind = kind)
                        })
                        splits++
                    }
                    is WriteIntent.CreateAccount, is WriteIntent.CreateCategory -> { /* handled in passes 1-2 */ }
                }
            }
        }
        return AppliedSummary(
            accountsCreated = accts,
            categoriesCreated = cats,
            transactionsAdded = txnsAdded,
            lendBorrowsRecorded = lbs,
            transfers = xfers,
            budgetsSet = budgetsSet,
            recategorized = recats,
            splitTransactionsAdded = splits,
        )
    }
}
