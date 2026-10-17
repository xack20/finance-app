package app.hisaab.capture

import app.hisaab.data.CaptureInboxRepository
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureStatus
import app.hisaab.domain.RawCapture
import kotlinx.datetime.Clock
import kotlin.random.Random

/** Same message = same row. Unchanged from the rule-based pipeline so messages it already handled stay handled. */
fun captureDedupHash(sender: String, body: String): String = Sha256.hexOf(sender.trim().lowercase() + "|" + body.trim())

/**
 * Every source hands its messages here: the message is stored QUEUED for the capture agent (exact repeats are
 * ignored) and [onQueued] wakes the agent's worker. No judgment happens here — that's the agent's job.
 */
class CaptureQueue(
    private val inbox: CaptureInboxRepository,
    private val onQueued: () -> Unit,
) : CaptureHandler {

    override suspend fun handle(raw: RawCapture) {
        val hash = captureDedupHash(raw.sender, raw.body)
        if (inbox.findByDedupHash(hash) != null) return
        val now = Clock.System.now().toEpochMilliseconds()
        inbox.insertCandidate(
            CandidateTransaction(
                id = Random.Default.nextBytes(16).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') },
                receivedAt = raw.receivedAt, channel = raw.channel, sender = raw.sender, rawBody = raw.body,
                dedupHash = hash, status = CaptureStatus.QUEUED, confidence = null, parsedBy = null, model = null,
                parseError = null, amount = null, direction = null, currency = "BDT", balanceAfter = null, refNo = null,
                proposedAccountId = null, proposedCategoryId = null, proposedMerchant = null, createdAt = now,
            ),
        )
        onQueued()
    }
}
