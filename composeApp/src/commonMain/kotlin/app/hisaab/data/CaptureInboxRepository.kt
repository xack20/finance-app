package app.hisaab.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import app.hisaab.domain.Direction
import app.hisaab.domain.ParsedBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CaptureInboxRepository(private val db: HisaabDatabase) {

    private val queries get() = db.captureInboxQueriesQueries

    suspend fun insertCandidate(c: CandidateTransaction) {
        queries.insertCandidate(
            id = c.id,
            received_at = c.receivedAt,
            channel = c.channel.name,
            sender = c.sender,
            raw_body = c.rawBody,
            dedup_hash = c.dedupHash,
            status = c.status.name,
            confidence = c.confidence,
            parsed_by = c.parsedBy?.name,
            model = c.model,
            parse_error = c.parseError,
            amount = c.amount,
            direction = c.direction?.name,
            currency = c.currency,
            balance_after = c.balanceAfter,
            ref_no = c.refNo,
            proposed_account_id = c.proposedAccountId,
            proposed_category_id = c.proposedCategoryId,
            proposed_merchant = c.proposedMerchant,
            created_at = c.createdAt,
        )
    }

    suspend fun findByDedupHash(hash: String): CandidateTransaction? =
        queries.findByDedupHash(hash).executeAsOneOrNull()?.toDomain()

    fun observePending(): Flow<List<CandidateTransaction>> =
        queries.observePending().asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    fun observeRecent(limit: Int): Flow<List<CandidateTransaction>> =
        queries.observeRecent(limit.toLong()).asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    fun observePendingCount(): Flow<Long> =
        queries.observePendingCount().asFlow().mapToOne(Dispatchers.Default)

    suspend fun getById(id: String): CandidateTransaction? =
        queries.getById(id).executeAsOneOrNull()?.toDomain()

    suspend fun markAutoPosted(id: String) {
        queries.updateStatus(CaptureStatus.AUTO_POSTED.name, id)
    }

    suspend fun markConfirmed(id: String) {
        queries.updateStatus(CaptureStatus.CONFIRMED.name, id)
    }

    suspend fun markDismissed(id: String) {
        queries.updateStatus(CaptureStatus.DISMISSED.name, id)
    }

    suspend fun purgeRaw(id: String) {
        queries.purgeRaw(id)
    }

    private fun migrations.Capture_inbox.toDomain(): CandidateTransaction = CandidateTransaction(
        id = id,
        receivedAt = received_at,
        channel = CaptureChannel.valueOf(channel),
        sender = sender,
        rawBody = raw_body,
        dedupHash = dedup_hash,
        status = CaptureStatus.valueOf(status),
        confidence = confidence,
        parsedBy = parsed_by?.let { ParsedBy.valueOf(it) },
        model = model,
        parseError = parse_error,
        amount = amount,
        direction = direction?.let { Direction.valueOf(it) },
        currency = currency,
        balanceAfter = balance_after,
        refNo = ref_no,
        proposedAccountId = proposed_account_id,
        proposedCategoryId = proposed_category_id,
        proposedMerchant = proposed_merchant,
        createdAt = created_at,
    )
}
