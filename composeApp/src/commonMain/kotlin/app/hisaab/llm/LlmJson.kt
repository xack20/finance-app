package app.hisaab.llm

import app.hisaab.domain.Direction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Shared serialization for the model's structured output. All three cloud adapters
 * (and the on-device provider) decode the model's JSON text into [LlmParseResult]
 * through [decodeResult], which also applies sanity checks (amount > 0, valid
 * direction enum, length caps, clamped confidence).
 */
object LlmJson {

    const val MERCHANT_MAX = 64
    const val REF_MAX = 48

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Serializable
    data class LlmParseDto(
        val amount: Double? = null,
        val direction: String? = null,
        val merchant: String? = null,
        @SerialName("categoryId") val categoryId: String? = null,
        val balanceAfter: Double? = null,
        val refNo: String? = null,
        val confidence: Double = 0.0,
        val isFinancial: Boolean = false,
    )

    @Serializable
    data class CategorizeDto(@SerialName("categoryId") val categoryId: String? = null)

    /** Strips ```json fences and isolates the first {...} block. */
    internal fun isolateJson(raw: String): String {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else cleaned
    }

    fun decodeResult(rawContent: String): LlmParseResult {
        val dto = try {
            json.decodeFromString(LlmParseDto.serializer(), isolateJson(rawContent))
        } catch (e: Exception) {
            throw LlmException(LlmError.Decode(e.message ?: "malformed json"))
        }
        return LlmParseResult(
            amount = dto.amount?.takeIf { it > 0.0 },
            direction = dto.direction?.let { runCatching { Direction.valueOf(it.uppercase()) }.getOrNull() },
            merchant = dto.merchant?.trim()?.takeIf { it.isNotEmpty() }?.take(MERCHANT_MAX),
            categoryId = dto.categoryId?.trim()?.takeIf { it.isNotEmpty() },
            balanceAfter = dto.balanceAfter,
            refNo = dto.refNo?.trim()?.takeIf { it.isNotEmpty() }?.take(REF_MAX),
            confidence = dto.confidence.coerceIn(0.0, 1.0),
            isFinancial = dto.isFinancial,
        )
    }

    fun decodeCategoryId(rawContent: String, allowed: Set<String>): String? {
        val dto = try {
            json.decodeFromString(CategorizeDto.serializer(), isolateJson(rawContent))
        } catch (e: Exception) {
            return null
        }
        return dto.categoryId?.trim()?.takeIf { it in allowed }
    }
}
