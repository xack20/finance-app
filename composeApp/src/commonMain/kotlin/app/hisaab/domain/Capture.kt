package app.hisaab.domain

/** A raw captured message before any parsing. Produced by CaptureService (M3-2). */
data class RawCapture(
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val channel: CaptureChannel,
)

/** How a capture entered the app. */
enum class CaptureChannel { SMS, NOTIFICATION, PASTE }

/** Lifecycle of a capture candidate. */
enum class CaptureStatus { PENDING, AUTO_POSTED, CONFIRMED, DISMISSED }

/** Which engine produced the parse. */
enum class ParsedBy { TEMPLATE, ON_DEVICE, CLOUD_CLAUDE, CLOUD_GEMINI, CLOUD_OPENAI }

/** Money flow direction extracted from a capture. */
enum class Direction { DEBIT, CREDIT }

/** Classification of a known sender. */
enum class BankType { BKASH, NAGAD, ROCKET, BANK, CARD, OTHER }

/** User-selected parsing engine. */
enum class EngineMode { ON_DEVICE, CLOUD }

/** Selected cloud LLM vendor (when EngineMode.CLOUD). */
enum class CloudProvider { CLAUDE, GEMINI, OPENAI }

/**
 * A parsed capture candidate: the raw message plus extracted fields, status,
 * confidence, and provenance. Mirrors the capture_inbox table.
 */
data class CandidateTransaction(
    val id: String,
    val receivedAt: Long,
    val channel: CaptureChannel,
    val sender: String,
    val rawBody: String,
    val dedupHash: String,
    val status: CaptureStatus,
    val confidence: Double?,
    val parsedBy: ParsedBy?,
    val model: String?,
    val parseError: String?,
    val amount: Double?,
    val direction: Direction?,
    val currency: String,
    val balanceAfter: Double?,
    val refNo: String?,
    val proposedAccountId: String?,
    val proposedCategoryId: String?,
    val proposedMerchant: String?,
    val createdAt: Long,
)

/** A known/learned sender → account mapping + pre-filter allowlist entry. */
data class SenderMapping(
    val id: String,
    val senderId: String,
    val displayName: String,
    val bankType: BankType,
    val isFinancial: Boolean,
    val templateKey: String?,
    val accountId: String?,
    val createdAt: Long,
)

/** Single-row capture/engine configuration. API key stays in SecureStorage. */
data class CaptureConfig(
    val captureEnabled: Boolean,
    val engineMode: EngineMode,
    val onDeviceModel: String,
    val cloudProvider: CloudProvider?,
    val cloudModel: String?,
    val redactionEnabled: Boolean,
    val alwaysReview: Boolean,
    val autoPostThreshold: Double,
    val cloudConsentAt: Long?,
    val retainRawBody: Boolean,
    val lastSmsCursor: Long,
    val updatedAt: Long,
)
