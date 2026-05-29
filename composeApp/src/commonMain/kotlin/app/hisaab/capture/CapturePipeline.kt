package app.hisaab.capture

import app.hisaab.data.CaptureConfigRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.SenderRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.BankType
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureStatus
import app.hisaab.domain.Category
import app.hisaab.domain.Direction
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.ParsedBy
import app.hisaab.domain.RawCapture
import app.hisaab.domain.SenderMapping
import app.hisaab.domain.TxnKind
import app.hisaab.domain.TxnSource
import app.hisaab.llm.LlmParseResult
import app.hisaab.llm.LlmProvider
import app.hisaab.llm.LlmRouter
import app.hisaab.llm.ParseRequest
import app.hisaab.llm.ProviderId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Orchestrates one captured message end-to-end:
 * dedup -> pre-filter -> extract (template, then LLM) -> resolve
 * account/merchant/category -> score -> route (auto-post vs PENDING).
 *
 * Depends on M3-1 repos + the LLM interfaces + the HisaabDatabase handle (used
 * ONLY to make the auto-post path atomic, per spec §7 step 8) + a
 * MutableSharedFlow<CaptureEvent> it emits AutoPosted into. Idempotent by
 * construction: the dedup_hash unique index makes a re-delivered message a
 * no-op.
 *
 * Auto-post atomicity: { create linked txn ; mark candidate AUTO_POSTED } runs
 * inside one db.transaction { } (same pattern TransactionRepository uses for
 * split parent+children). The txn is created ALREADY LINKED via
 * NewTransaction.captureId — there is no separate link step.
 */
class CapturePipeline(
    private val db: HisaabDatabase,
    private val inboxRepo: CaptureInboxRepository,
    private val senderRepo: SenderRepository,
    private val accountMatcher: AccountMatcher,
    private val preFilter: SmsPreFilter,
    private val llmRouter: LlmRouter,
    private val txnRepo: TransactionRepository,
    private val configRepo: CaptureConfigRepository,
    private val captureEvents: MutableSharedFlow<CaptureEvent>,
) {

    suspend fun process(raw: RawCapture) {
        val dedupHash = dedupHash(raw.sender, raw.body)
        // 1. Dedup
        if (inboxRepo.findByDedupHash(dedupHash) != null) return

        // 2. Pre-filter
        val mapping = senderRepo.findBySenderId(raw.sender)
        when (val verdict = preFilter.classify(raw, mapping)) {
            is PreFilterResult.NotFinancial -> return
            is PreFilterResult.KnownTemplate -> extractKnown(raw, dedupHash, verdict.mapping, verdict.templateKey)
            is PreFilterResult.UnknownFinancial -> extractUnknown(raw, dedupHash, verdict.mapping)
        }
    }

    // ---- Known-template path (template first, LLM for gaps) ----

    private suspend fun extractKnown(
        raw: RawCapture,
        dedupHash: String,
        mapping: SenderMapping,
        templateKey: String,
    ) {
        val template = BankTemplates.forKey(templateKey)
        val normalized = BanglaNumerals.normalize(raw.body)
        val extraction = template?.extract(normalized)

        if (extraction != null && extraction.complete) {
            finalize(
                raw = raw,
                dedupHash = dedupHash,
                mapping = mapping,
                amount = extraction.amount,
                direction = extraction.direction,
                merchant = extraction.merchant,
                refNo = extraction.refNo,
                balanceAfter = extraction.balanceAfter,
                parsedBy = ParsedBy.TEMPLATE,
                model = null,
                llmConfidence = null,
                templateComplete = true,
                parseError = null,
            )
            return
        }
        // Partial template -> fall through to LLM (or PENDING if none).
        extractUnknown(raw, dedupHash, mapping, templatePartial = extraction)
    }

    // ---- Unknown / partial path (LLM, else PENDING) ----

    private suspend fun extractUnknown(
        raw: RawCapture,
        dedupHash: String,
        mapping: SenderMapping?,
        templatePartial: TemplateExtraction? = null,
    ) {
        val provider = llmRouter.active()
        if (provider == null) {
            savePending(
                raw = raw,
                dedupHash = dedupHash,
                amount = templatePartial?.amount,
                direction = templatePartial?.direction,
                merchant = templatePartial?.merchant,
                refNo = templatePartial?.refNo,
                balanceAfter = templatePartial?.balanceAfter,
                parsedBy = null,
                model = null,
                parseError = "needs_manual",
            )
            return
        }

        val result = runLlm(provider, raw, mapping)
        if (result == null || !result.isFinancial) {
            savePending(
                raw = raw,
                dedupHash = dedupHash,
                amount = templatePartial?.amount,
                direction = templatePartial?.direction,
                merchant = templatePartial?.merchant,
                refNo = templatePartial?.refNo,
                balanceAfter = templatePartial?.balanceAfter,
                parsedBy = null,
                model = null,
                parseError = "llm_error",
            )
            return
        }

        finalize(
            raw = raw,
            dedupHash = dedupHash,
            mapping = mapping,
            amount = result.amount ?: templatePartial?.amount,
            direction = result.direction ?: templatePartial?.direction,
            merchant = result.merchant ?: templatePartial?.merchant,
            refNo = result.refNo ?: templatePartial?.refNo,
            balanceAfter = result.balanceAfter ?: templatePartial?.balanceAfter,
            parsedBy = parsedByFor(provider.id),
            model = provider.id.name,
            llmConfidence = result.confidence,
            templateComplete = false,
            parseError = null,
            llmCategoryId = result.categoryId,
        )
    }

    private suspend fun runLlm(
        provider: LlmProvider,
        raw: RawCapture,
        mapping: SenderMapping?,
    ): LlmParseResult? = runCatching {
        provider.parse(
            ParseRequest(
                text = raw.body,
                senderHint = mapping?.senderId ?: raw.sender,
                categories = defaultCategories(),
            ),
        )
    }.getOrNull()

    // ---- Shared finalize: resolve fields, score, route, post ----

    @Suppress("LongParameterList")
    private suspend fun finalize(
        raw: RawCapture,
        dedupHash: String,
        mapping: SenderMapping?,
        amount: Double?,
        direction: Direction?,
        merchant: String?,
        refNo: String?,
        balanceAfter: Double?,
        parsedBy: ParsedBy,
        model: String?,
        llmConfidence: Double?,
        templateComplete: Boolean,
        parseError: String?,
        llmCategoryId: String? = null,
    ) {
        // Sanity: amount must be positive and direction known to even consider posting.
        val sane = amount != null && amount > 0.0 && direction != null
        val accountId = accountMatcher.resolve(mapping, mapping?.bankType ?: BankType.OTHER)
        val categoryId = resolveCategory(llmCategoryId, merchant)
        val fieldsResolved = sane && accountId != null

        val confidence = ConfidenceScorer.score(
            templateComplete = templateComplete,
            fieldsResolved = fieldsResolved,
            llmConfidence = llmConfidence,
            senderKnown = mapping != null,
        )

        val config = configRepo.get()
        val now = Clock.System.now().toEpochMilliseconds()
        val candidateId = randomId()
        val canAutoPost = !config.alwaysReview &&
            fieldsResolved &&
            confidence >= config.autoPostThreshold

        if (canAutoPost) {
            // Non-null after the fieldsResolved gate above.
            val resolvedAccountId = accountId!!
            val resolvedAmount = amount!!
            val resolvedDirection = direction!!
            val candidate = candidate(
                id = candidateId,
                raw = raw,
                dedupHash = dedupHash,
                status = CaptureStatus.AUTO_POSTED,
                confidence = confidence,
                parsedBy = parsedBy,
                model = model,
                parseError = parseError,
                amount = resolvedAmount,
                direction = resolvedDirection,
                balanceAfter = balanceAfter,
                refNo = refNo,
                accountId = resolvedAccountId,
                categoryId = categoryId,
                merchant = merchant,
                now = now,
            )

            // R2: one DB transaction — create the ALREADY-LINKED txn (R1) and insert
            // the candidate together, so neither can exist without the other.
            var txnId = ""
            db.transaction {
                txnId = txnRepo.addBlocking(
                    NewTransaction(
                        accountId = resolvedAccountId,
                        amount = resolvedAmount,
                        ts = raw.receivedAt,
                        merchantName = merchant,
                        categoryId = categoryId,
                        source = TxnSource.SMS,
                        notes = null,
                        kind = kindFor(resolvedDirection),
                        captureId = candidateId,
                    ),
                )
                inboxRepo.insertCandidateBlocking(candidate)
            }

            captureEvents.emit(
                CaptureEvent.AutoPosted(
                    txnId = txnId,
                    candidateId = candidateId,
                    amount = resolvedAmount,
                    sender = raw.sender,
                    direction = resolvedDirection,
                ),
            )
        } else {
            inboxRepo.insertCandidate(
                candidate(
                    id = candidateId,
                    raw = raw,
                    dedupHash = dedupHash,
                    status = CaptureStatus.PENDING,
                    confidence = confidence,
                    parsedBy = parsedBy,
                    model = model,
                    parseError = parseError,
                    amount = amount,
                    direction = direction,
                    balanceAfter = balanceAfter,
                    refNo = refNo,
                    accountId = accountId,
                    categoryId = categoryId,
                    merchant = merchant,
                    now = now,
                ),
            )
        }
    }

    private suspend fun savePending(
        raw: RawCapture,
        dedupHash: String,
        amount: Double?,
        direction: Direction?,
        merchant: String?,
        refNo: String?,
        balanceAfter: Double?,
        parsedBy: ParsedBy?,
        model: String?,
        parseError: String,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        inboxRepo.insertCandidate(
            candidate(
                id = randomId(),
                raw = raw,
                dedupHash = dedupHash,
                status = CaptureStatus.PENDING,
                confidence = null,
                parsedBy = parsedBy,
                model = model,
                parseError = parseError,
                amount = amount,
                direction = direction,
                balanceAfter = balanceAfter,
                refNo = refNo,
                accountId = null,
                categoryId = null,
                merchant = merchant,
                now = now,
            ),
        )
    }

    // ---- helpers ----

    private fun resolveCategory(llmCategoryId: String?, merchant: String?): String? {
        val valid = DEFAULT_CATEGORY_IDS.toSet()
        llmCategoryId?.takeIf { it in valid }?.let { return it }
        // Template fast-path rule map (no LLM): a few obvious merchants -> category.
        val m = merchant?.lowercase() ?: return null
        return when {
            m.contains("shwapno") || m.contains("agora") || m.contains("daraz") -> "shopping"
            else -> null
        }
    }

    private fun defaultCategories(): List<Category> = DEFAULT_CATEGORY_IDS.map {
        Category(id = it, name = it, parentId = null, color = null, icon = null, isDefault = true)
    }

    private fun kindFor(direction: Direction): TxnKind = when (direction) {
        Direction.DEBIT -> TxnKind.EXPENSE
        Direction.CREDIT -> TxnKind.INCOME
    }

    private fun parsedByFor(id: ProviderId): ParsedBy = when (id) {
        ProviderId.ON_DEVICE -> ParsedBy.ON_DEVICE
        ProviderId.CLOUD_CLAUDE -> ParsedBy.CLOUD_CLAUDE
        ProviderId.CLOUD_GEMINI -> ParsedBy.CLOUD_GEMINI
        ProviderId.CLOUD_OPENAI -> ParsedBy.CLOUD_OPENAI
    }

    @Suppress("LongParameterList")
    private fun candidate(
        id: String,
        raw: RawCapture,
        dedupHash: String,
        status: CaptureStatus,
        confidence: Double?,
        parsedBy: ParsedBy?,
        model: String?,
        parseError: String?,
        amount: Double?,
        direction: Direction?,
        balanceAfter: Double?,
        refNo: String?,
        accountId: String?,
        categoryId: String?,
        merchant: String?,
        now: Long,
    ): CandidateTransaction = CandidateTransaction(
        id = id,
        receivedAt = raw.receivedAt,
        channel = raw.channel,
        sender = raw.sender,
        rawBody = raw.body,
        dedupHash = dedupHash,
        status = status,
        confidence = confidence,
        parsedBy = parsedBy,
        model = model,
        parseError = parseError,
        amount = amount,
        direction = direction,
        currency = "BDT",
        balanceAfter = balanceAfter,
        refNo = refNo,
        proposedAccountId = accountId,
        proposedCategoryId = categoryId,
        proposedMerchant = merchant,
        createdAt = now,
    )

    private fun dedupHash(sender: String, body: String): String {
        val normalized = sender.trim().lowercase() + "|" + body.trim()
        return Sha256.hexOf(normalized)
    }

    private fun randomId(): String {
        val bytes = Random.Default.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    private companion object {
        val DEFAULT_CATEGORY_IDS = listOf(
            "food", "transport", "bills", "salary", "lend", "borrow",
            "health", "education", "shopping", "entertainment", "other", "transfer",
        )
    }
}
