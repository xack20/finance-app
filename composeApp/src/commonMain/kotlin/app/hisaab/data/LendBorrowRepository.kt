package app.hisaab.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.LendBorrowDirection
import app.hisaab.domain.LendBorrowRow
import app.hisaab.domain.LendBorrowStatus
import app.hisaab.domain.NewLendBorrow
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TxnKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.random.Random

class LendBorrowRepository(
    private val db: HisaabDatabase,
    private val txnRepo: TransactionRepository,
) {

    /** Returns Pair(lendBorrowId, txnId). */
    suspend fun record(input: NewLendBorrow): Pair<String, String> {
        val lendBorrowId = randomId()
        val txnKind = if (input.direction == LendBorrowDirection.LENT) TxnKind.LEND else TxnKind.BORROW
        val categoryId = if (input.direction == LendBorrowDirection.LENT) "lend" else "borrow"
        db.lendBorrowQueriesQueries.insertLendBorrow(
            id = lendBorrowId,
            person_id = input.personId,
            amount = input.amount,
            direction = input.direction.name,
            purpose = input.purpose,
            ts = input.ts,
            due_date = input.dueDate,
            status = LendBorrowStatus.OPEN.name,
        )
        val txnId = txnRepo.add(
            NewTransaction(
                accountId = input.accountId,
                amount = input.amount,
                ts = input.ts,
                merchantName = null,
                categoryId = categoryId,
                notes = input.purpose,
                kind = txnKind,
            ),
        )
        db.lendBorrowQueriesQueries.linkLendBorrowTxn(lend_borrow_id = lendBorrowId, txn_id = txnId)
        return lendBorrowId to txnId
    }

    suspend fun settle(lendBorrowId: String, settlementAmount: Double, accountId: String) {
        val current = db.lendBorrowQueriesQueries.getLendBorrowById(lendBorrowId).executeAsOneOrNull()
            ?: error("lend_borrow $lendBorrowId not found")
        val settlementTxnId = txnRepo.add(
            NewTransaction(
                accountId = accountId,
                amount = settlementAmount,
                ts = Clock.System.now().toEpochMilliseconds(),
                merchantName = null,
                categoryId = "transfer",
                notes = "Settlement",
                kind = TxnKind.SETTLEMENT,
            ),
        )
        db.lendBorrowQueriesQueries.linkLendBorrowTxn(lend_borrow_id = lendBorrowId, txn_id = settlementTxnId)
        val newStatus = if (settlementAmount >= current.amount) LendBorrowStatus.SETTLED else LendBorrowStatus.PARTIAL
        db.lendBorrowQueriesQueries.updateLendBorrowStatus(status = newStatus.name, id = lendBorrowId)
    }

    fun observeForPerson(personId: String): Flow<List<LendBorrowRow>> =
        db.lendBorrowQueriesQueries.observeLendBorrowForPerson(personId).asFlow()
            .mapToList(Dispatchers.Default)
            .map { records ->
                val personName = db.personQueriesQueries.getPerson(personId).executeAsOneOrNull()?.name ?: ""
                records.map { it.toDomain(personName) }
            }

    fun observeOpen(): Flow<List<LendBorrowRow>> =
        db.lendBorrowQueriesQueries.observeOpenLendBorrow().asFlow()
            .mapToList(Dispatchers.Default)
            .map { records ->
                records.map { r ->
                    val name = db.personQueriesQueries.getPerson(r.person_id).executeAsOneOrNull()?.name ?: ""
                    r.toDomain(name)
                }
            }

    private fun migrations.Lend_borrow.toDomain(personName: String): LendBorrowRow = LendBorrowRow(
        id = id,
        personId = person_id,
        personName = personName,
        amount = amount,
        direction = LendBorrowDirection.valueOf(direction),
        purpose = purpose,
        ts = ts,
        dueDate = due_date,
        status = LendBorrowStatus.valueOf(status),
    )

    private fun randomId(): String {
        val bytes = Random.Default.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
