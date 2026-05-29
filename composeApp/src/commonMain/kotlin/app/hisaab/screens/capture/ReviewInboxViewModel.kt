package app.hisaab.screens.capture

import app.hisaab.data.AccountRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.domain.CandidateTransaction
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

    /**
     * One-shot message for the UI to display after [confirmAllHighConfidence] completes.
     * Emits a human-readable summary (e.g. "Confirmed 3, 1 needs an account") and resets to
     * null after the UI has consumed it. The screen should clear this after showing a snackbar.
     */
    private val _bulkResult = MutableStateFlow<String?>(null)
    val bulkResult: StateFlow<String?> = _bulkResult.asStateFlow()

    /** Clear the bulk-result message after the UI has consumed it (e.g. after snackbar dismiss). */
    fun clearBulkResult() { _bulkResult.value = null }

    fun confirmAllHighConfidence(threshold: Double) {
        scope.launch {
            val candidates = inboxRepo.getPending()
                .filter { (it.confidence ?: 0.0) >= threshold }
            var confirmed = 0
            var skipped = 0
            candidates.forEach { c ->
                if (confirmCandidate(c.id)) confirmed++ else skipped++
            }
            _bulkResult.value = when {
                confirmed == 0 && skipped == 0 -> null
                skipped == 0 -> "Confirmed $confirmed"
                confirmed == 0 -> "$skipped need an account — edit to post"
                else -> "Confirmed $confirmed, $skipped need an account"
            }
        }
    }
}
