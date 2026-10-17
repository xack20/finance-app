package app.hisaab.capture

import app.hisaab.data.CaptureInboxRepository
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureStatus

/** Same amount + direction from another channel within this window counts as one payment (no shared ID). */
const val DUPLICATE_WINDOW_MS: Long = 10L * 60 * 1000

/** parse_error marking a capture dismissed as a copy of another (live pipeline + cleanup). */
const val DUPLICATE_PARSE_ERROR = "duplicate"

internal fun normalizeRef(refNo: String?): String? = refNo?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

data class DuplicatePair(val keep: CandidateTransaction, val duplicate: CandidateTransaction)

/** What a cleanup run did and what it needs the user to confirm. */
data class DuplicateReport(val dismissedFromReview: Int, val ledgerDuplicates: List<DuplicatePair>)

/**
 * Pairs each later copy of a payment with the earliest one, using the live pipeline's rules: the same
 * transaction ID, or the same amount + direction from another channel within [DUPLICATE_WINDOW_MS] unless
 * both carry different IDs.
 */
fun findDuplicatePairs(rows: List<CandidateTransaction>): List<DuplicatePair> {
    val byRef = HashMap<String, CandidateTransaction>()
    val kept = ArrayList<CandidateTransaction>() // ascending receivedAt
    val pairs = mutableListOf<DuplicatePair>()
    for (row in rows.sortedBy { it.receivedAt }) {
        val ref = normalizeRef(row.refNo)
        val original = ref?.let { byRef[it] } ?: kept.asReversed().asSequence()
            .takeWhile { row.receivedAt - it.receivedAt <= DUPLICATE_WINDOW_MS }
            .firstOrNull { other ->
                other.amount != null && other.amount == row.amount && other.direction != null &&
                    other.direction == row.direction && other.channel != row.channel &&
                    (ref == null || normalizeRef(other.refNo) == null) // two different IDs = two payments
            }
        if (original != null) {
            pairs += DuplicatePair(keep = original, duplicate = row)
        } else {
            kept += row
            if (ref != null) byRef[ref] = row
        }
    }
    return pairs
}

/**
 * One-time cleanup for copies captured before cross-channel dedup existed. Review copies are dismissed
 * automatically (a ledger copy always wins over a Review one); pairs where both are already in the ledger are
 * only reported — [removeLedgerDuplicates] deletes them once the user confirms.
 */
class DuplicateCleanup(private val db: HisaabDatabase, private val inboxRepo: CaptureInboxRepository) {

    suspend fun scan(): DuplicateReport {
        var dismissed = 0
        val ledger = mutableListOf<DuplicatePair>()
        for (pair in findDuplicatePairs(inboxRepo.getLive())) {
            val reviewCopy = when {
                pair.duplicate.status == CaptureStatus.PENDING -> pair.duplicate
                pair.keep.status == CaptureStatus.PENDING -> pair.keep
                else -> null
            }
            if (reviewCopy != null) {
                inboxRepo.markDuplicateBlocking(reviewCopy.id)
                dismissed++
            } else {
                ledger += pair
            }
        }
        return DuplicateReport(dismissed, ledger)
    }

    /** Deletes each pair's later transaction and marks its capture a duplicate, all in one DB transaction. */
    suspend fun removeLedgerDuplicates(pairs: List<DuplicatePair>): Int {
        var removed = 0
        db.transaction {
            pairs.forEach { pair ->
                db.transactionQueriesQueries.findTxnIdsByCaptureId(pair.duplicate.id).executeAsList().forEach {
                    db.transactionQueriesQueries.deleteTxn(it) // FK cascade removes tags, splits, attachments
                }
                inboxRepo.markDuplicateBlocking(pair.duplicate.id)
                removed++
            }
        }
        return removed
    }
}
