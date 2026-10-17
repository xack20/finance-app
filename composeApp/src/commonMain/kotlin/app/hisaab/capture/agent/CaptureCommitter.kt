package app.hisaab.capture.agent

import app.hisaab.capture.CaptureEvent
import app.hisaab.data.AccountRepository
import app.hisaab.data.AgentMemoryRepository
import app.hisaab.data.AgentQuestionRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureStatus
import app.hisaab.domain.Direction
import app.hisaab.domain.NewLendBorrow
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TxnKind
import app.hisaab.domain.TxnSource
import kotlinx.coroutines.flow.first

/**
 * The only writer for capture-agent decisions: one db.transaction per batch, so a batch lands whole or not at
 * all. Accounts are created first so posts in the same batch can use them. A payment is dated and linked to its
 * earliest message; the other messages of the same decision are dismissed as copies.
 */
class CaptureCommitter(
    private val db: HisaabDatabase,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val txns: TransactionRepository,
    private val persons: PersonRepository,
    private val lendBorrow: LendBorrowRepository,
    private val questions: AgentQuestionRepository,
    private val memory: AgentMemoryRepository,
) {
    /** @throws IllegalArgumentException when a decision names an account that doesn't exist (nothing is written). */
    suspend fun commit(batch: List<CandidateTransaction>, decisions: List<CaptureDecision>): List<CaptureEvent.AutoPosted> {
        // Reads happen before the transaction (Flow.first is suspend; the transaction block is not).
        val accountIds = accounts.observeActive().first().associate { it.name.lowercase() to it.id }.toMutableMap()
        val categoryList = categories.observeAll().first()
        val categoryIds = categoryList.associate { it.id.lowercase() to it.id } +
            categoryList.associate { it.name.lowercase() to it.id }
        val byId = batch.associateBy { it.id }
        val events = mutableListOf<CaptureEvent.AutoPosted>()

        db.transaction {
            decisions.filterIsInstance<CaptureDecision.CreateAccount>().forEach { d ->
                accountIds[d.name.lowercase()] = accounts.addBlocking(d.name, d.kind, d.institution)
            }
            fun account(name: String) = accountIds[name.lowercase()] ?: throw IllegalArgumentException("unknown account '$name'")
            fun category(name: String?) = name?.let { categoryIds[it.trim().lowercase()] }
            /** The earliest message carries the payment; the rest are copies of it. */
            fun primary(d: CaptureDecision): CandidateTransaction {
                val messages = d.messageIds.mapNotNull { byId[it] }.sortedBy { it.receivedAt }
                val first = messages.first()
                messages.drop(1).forEach {
                    db.captureInboxQueriesQueries.markDecided(CaptureStatus.DISMISSED.name, "same payment as ${first.id}", it.id)
                }
                return first
            }

            decisions.forEach { d ->
                when (d) {
                    is CaptureDecision.PostTransaction -> {
                        val message = primary(d)
                        val accountId = account(d.account)
                        val categoryId = category(d.category)
                        val txnId = txns.addBlocking(
                            NewTransaction(
                                accountId = accountId, amount = d.amount, ts = message.receivedAt,
                                merchantName = d.merchant, categoryId = categoryId, source = TxnSource.SMS,
                                notes = d.notes, kind = d.kind, captureId = message.id,
                            ),
                        )
                        val direction = if (d.kind == TxnKind.INCOME) Direction.CREDIT else Direction.DEBIT
                        db.captureInboxQueriesQueries.linkPosted(
                            d.amount, direction.name, accountId, categoryId, d.merchant, MODEL_LABEL, message.id,
                        )
                        events += CaptureEvent.AutoPosted(txnId, message.id, d.amount, message.sender, direction, message.receivedAt)
                    }
                    is CaptureDecision.PostTransfer -> {
                        val message = primary(d)
                        txns.transferBlocking(
                            account(d.fromAccount), account(d.toAccount), d.amount, ts = message.receivedAt,
                            notes = d.notes, source = TxnSource.SMS, captureId = message.id,
                        )
                        db.captureInboxQueriesQueries.markDecided(CaptureStatus.AUTO_POSTED.name, null, message.id)
                    }
                    is CaptureDecision.PostLendBorrow -> {
                        val message = primary(d)
                        val personId = persons.findByNameBlocking(d.person) ?: persons.addManualBlocking(d.person)
                        // shortcut: the lend/borrow txn isn't linked to its message (no "Show original"); link it
                        // if users ask for the source of a loan entry.
                        lendBorrow.recordBlocking(
                            NewLendBorrow(
                                personId = personId, amount = d.amount, direction = d.direction,
                                accountId = account(d.account), purpose = d.purpose, ts = message.receivedAt,
                                dueDate = null,
                            ),
                        )
                        db.captureInboxQueriesQueries.markDecided(CaptureStatus.AUTO_POSTED.name, null, message.id)
                    }
                    is CaptureDecision.Skip -> d.messageIds.forEach {
                        db.captureInboxQueriesQueries.markDecided(CaptureStatus.DISMISSED.name, d.reason, it)
                    }
                    is CaptureDecision.Ask -> questions.askBlocking(d.question, d.options, d.messageIds)
                    is CaptureDecision.Remember -> memory.addBlocking(d.fact)
                    is CaptureDecision.CreateAccount -> Unit // created first, above
                }
            }
        }
        return events
    }

    private companion object {
        const val MODEL_LABEL = "capture-agent"
    }
}
