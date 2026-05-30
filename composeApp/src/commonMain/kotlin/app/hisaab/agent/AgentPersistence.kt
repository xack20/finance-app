// AgentPersistence.kt
package app.hisaab.agent
import app.hisaab.llm.LlmJson
import kotlinx.serialization.builtins.ListSerializer

/** Encodes/decodes a turn's proposedWrites and appliedSummary to the JSON TEXT columns in agent_message.
 *  Decode is tolerant: malformed/empty JSON yields empty/null rather than throwing. */
object AgentWriteCodec {
    private val writesSerializer = ListSerializer(ProposedWrite.serializer())

    fun encodeWrites(writes: List<ProposedWrite>): String? =
        if (writes.isEmpty()) null else LlmJson.json.encodeToString(writesSerializer, writes)

    fun decodeWrites(raw: String?): List<ProposedWrite> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { LlmJson.json.decodeFromString(writesSerializer, raw) }.getOrDefault(emptyList())

    fun encodeSummary(summary: AppliedSummary?): String? =
        summary?.let { LlmJson.json.encodeToString(AppliedSummary.serializer(), it) }

    fun decodeSummary(raw: String?): AppliedSummary? =
        if (raw.isNullOrBlank()) null
        else runCatching { LlmJson.json.decodeFromString(AppliedSummary.serializer(), raw) }.getOrNull()
}
