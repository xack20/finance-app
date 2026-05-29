package app.hisaab.screens.capture

import app.hisaab.data.AccountRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.domain.CandidateTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A pending candidate enriched with display names for the card UI. */
data class ReviewCandidate(
    val candidate: CandidateTransaction,
    val accountName: String?,
    val categoryName: String?,
)

/**
 * Drives the Review inbox. Posting is delegated to [confirmCandidate] (AppContainer.confirmCandidate
 * in production), which atomically creates the linked txn and marks the candidate CONFIRMED in one
 * db transaction. The VM never builds a NewTransaction or calls a repo link method.
 */
class ReviewInboxViewModel(
    private val inboxRepo: CaptureInboxRepository,
    accountRepo: AccountRepository,
    categoryRepo: CategoryRepository,
    private val confirmCandidate: suspend (candidateId: String) -> Boolean,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    val pending: StateFlow<List<ReviewCandidate>> = combine(
        inboxRepo.observePending(),
        accountRepo.observeActive(),
        categoryRepo.observeAll(),
    ) { candidates, accounts, cats ->
        val accountsById = accounts.associateBy { it.id }
        val catsById = cats.associateBy { it.id }
        candidates.map { c ->
            ReviewCandidate(
                candidate = c,
                accountName = c.proposedAccountId?.let { accountsById[it]?.name },
                categoryName = c.proposedCategoryId?.let { catsById[it]?.name },
            )
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun dismiss(candidateId: String) {
        scope.launch { inboxRepo.markDismissed(candidateId) }
    }

    fun confirm(candidateId: String) {
        scope.launch { confirmCandidate(candidateId) }
    }

    fun confirmAllHighConfidence(threshold: Double) {
        scope.launch {
            inboxRepo.getPending()
                .filter { (it.confidence ?: 0.0) >= threshold }
                .forEach { confirmCandidate(it.id) }
        }
    }
}
