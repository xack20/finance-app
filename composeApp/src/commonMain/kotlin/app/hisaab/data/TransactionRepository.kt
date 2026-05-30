package app.hisaab.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.hisaab.db.HisaabDatabase
import app.hisaab.db.ObserveRecentTopLevel
import app.hisaab.domain.MoneyTotals
import app.hisaab.domain.NewSplitTransaction
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TransactionPatch
import app.hisaab.domain.TransactionRow
import app.hisaab.domain.TxnKind
import app.hisaab.domain.TxnSource
import app.hisaab.domain.YearMonth
import app.hisaab.util.dayRangeMs
import app.hisaab.util.monthRangeMs
import app.hisaab.util.todayRangeMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.random.Random

class TransactionRepository(
    private val db: HisaabDatabase,
    private val merchantRepo: MerchantRepository,
    private val tagRepo: TagRepository,
) {

    fun observeRecent(limit: Int = 50): Flow<List<TransactionRow>> =
        db.transactionQueriesQueries.observeRecentTopLevel(limit.toLong()).asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    /**
     * Reactive single-row lookup by id. Emits the row whenever it changes and emits null if the
     * transaction is deleted. Used by [app.hisaab.screens.transaction.TransactionDetailViewModel]
     * so the detail screen works for transactions that fall outside the recent-500 window.
     */
    fun observeById(id: String): Flow<TransactionRow?> =
        db.transactionQueriesQueries.observeTxnById(id).asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { row -> row?.toFullDomain() }

    fun observeForDay(epochDayMs: Long): Flow<List<TransactionRow>> {
        val (start, end) = dayRangeMs(epochDayMs)
        return db.transactionQueriesQueries.observeTxnsForDayRange(start, end).asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toFullDomain() } }
    }

    fun observeForMonth(yearMonth: YearMonth): Flow<List<TransactionRow>> {
        val (start, end) = monthRangeMs(yearMonth)
        return db.transactionQueriesQueries.observeTxnsForDayRange(start, end).asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toFullDomain() } }
    }

    suspend fun add(input: NewTransaction): String {
        val id = randomId()
        val merchantId = input.merchantName?.takeIf { it.isNotBlank() }
            ?.let { merchantRepo.upsertByName(it, defaultCategoryId = input.categoryId) }
        db.transactionQueriesQueries.insertTxn(
            id = id,
            account_id = input.accountId,
            amount = input.amount,
            currency = input.currency,
            ts = input.ts,
            merchant_id = merchantId,
            category_id = input.categoryId,
            source = input.source.name,
            notes = input.notes,
            kind = input.kind.name,
            parent_txn_id = null,
            capture_id = input.captureId,
            transfer_group_id = null,
        )
        if (input.tagNames.isNotEmpty()) {
            val tagIds = input.tagNames.map { tagRepo.upsertByName(it) }
            tagRepo.linkToTxn(id, tagIds)
        }
        return id
    }

    /**
     * Non-suspending variant for use inside a [HisaabDatabase.transaction] block.
     * Performs the same INSERT as [add] but skips async merchant upsert and tag linking — those
     * are either handled by the pipeline before calling this or irrelevant for auto-post.
     * Called by CapturePipeline's atomic auto-post (R2).
     */
    fun addBlocking(input: NewTransaction): String {
        val id = randomId()
        db.transactionQueriesQueries.insertTxn(
            id = id,
            account_id = input.accountId,
            amount = input.amount,
            currency = input.currency,
            ts = input.ts,
            merchant_id = null, // merchant upsert happens outside the transaction
            category_id = input.categoryId,
            source = input.source.name,
            notes = input.notes,
            kind = input.kind.name,
            parent_txn_id = null,
            capture_id = input.captureId,
            transfer_group_id = null,
        )
        return id
    }

    suspend fun addSplits(parentId: String, children: List<NewSplitTransaction>) {
        val parent = db.transactionQueriesQueries.getTxn(parentId).executeAsOneOrNull()
            ?: error("Parent txn $parentId not found")
        children.forEach { child ->
            db.transactionQueriesQueries.insertTxn(
                id = randomId(),
                account_id = parent.account_id,
                amount = child.amount,
                currency = parent.currency,
                ts = parent.ts,
                merchant_id = parent.merchant_id,
                category_id = child.categoryId,
                source = parent.source,
                notes = child.notes,
                kind = child.kind.name,
                parent_txn_id = parentId,
                capture_id = null,
                transfer_group_id = null,
            )
        }
    }

    suspend fun update(id: String, patch: TransactionPatch) {
        val current = db.transactionQueriesQueries.getTxn(id).executeAsOneOrNull() ?: return
        val merchantId = patch.merchantName?.takeIf { it.isNotBlank() }
            ?.let { merchantRepo.upsertByName(it) } ?: current.merchant_id
        db.transactionQueriesQueries.updateTxn(
            amount = patch.amount ?: current.amount,
            ts = patch.ts ?: current.ts,
            merchant_id = merchantId,
            category_id = patch.categoryId ?: current.category_id,
            notes = patch.notes ?: current.notes,
            kind = patch.kind?.name ?: current.kind,
            id = id,
        )
    }

    /** Direct one-shot lookup by id (not a Flow), safe to call from a suspend init block. */
    suspend fun getById(id: String): TransactionRow? =
        db.transactionQueriesQueries.getTxn(id).executeAsOneOrNull()?.toFullDomain()

    suspend fun delete(id: String) {
        // FK ON DELETE CASCADE handles txn_tag, child splits, and attachments.
        db.transactionQueriesQueries.deleteTxn(id)
    }

    fun observeTodayNet(): Flow<MoneyTotals> {
        val (start, end) = todayRangeMs()
        return db.transactionQueriesQueries.sumByKindForRange(start, end).asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                val income = rows.firstOrNull { it.kind == TxnKind.INCOME.name }?.total ?: 0.0
                val expense = rows.firstOrNull { it.kind == TxnKind.EXPENSE.name }?.total ?: 0.0
                MoneyTotals(income = income, expense = expense, net = income - expense)
            }
    }

    // ---- mappers ----

    // observeRecentTopLevel generates a custom projection: app.hisaab.db.ObserveRecentTopLevel
    // Column order (from generated code): id, account_id, amount, currency, ts, merchant_id,
    // category_id, source, notes, kind, parent_txn_id, capture_id, transfer_group_id
    private fun ObserveRecentTopLevel.toDomain(): TransactionRow = TransactionRow(
        id = id,
        accountId = account_id,
        accountName = "",       // resolved by ViewModel
        amount = amount,
        currency = currency,
        ts = ts,
        merchantId = merchant_id,
        merchantName = null,
        categoryId = category_id,
        categoryName = null,
        categoryColor = null,
        source = TxnSource.valueOf(source),
        notes = notes,
        kind = TxnKind.valueOf(kind),
        parentTxnId = parent_txn_id,
        captureId = capture_id,
        transferGroupId = transfer_group_id,
    )

    // observeTxnsForDayRange is SELECT * so it returns migrations.Txn
    // Field order: id, account_id, amount, currency, ts, merchant_id, category_id, source, notes, parent_txn_id, kind, capture_id, transfer_group_id
    private fun migrations.Txn.toFullDomain(): TransactionRow = TransactionRow(
        id = id,
        accountId = account_id,
        accountName = "",       // resolved by ViewModel
        amount = amount,
        currency = currency,
        ts = ts,
        merchantId = merchant_id,
        merchantName = null,
        categoryId = category_id,
        categoryName = null,
        categoryColor = null,
        source = TxnSource.valueOf(source),
        notes = notes,
        kind = TxnKind.valueOf(kind),
        parentTxnId = parent_txn_id,
        captureId = capture_id,
        transferGroupId = transfer_group_id,
    )

    private fun randomId(): String {
        val bytes = Random.Default.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
