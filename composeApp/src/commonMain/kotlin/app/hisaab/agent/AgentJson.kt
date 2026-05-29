// AgentJson.kt
package app.hisaab.agent
import app.hisaab.llm.LlmJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

class AgentDecodeException(message: String) : Exception(message)

@Serializable data class ToolCall(val tool: String, val args: JsonObject = JsonObject(emptyMap()))
@Serializable data class FinalResponse(val message: String = "", val proposedWrites: List<ToolCall> = emptyList())
@Serializable data class AgentEnvelope(
    val thought: String = "",
    val action: ToolCall? = null,
    val final: FinalResponse? = null,
)

object AgentJson {
    fun decodeEnvelope(raw: String): AgentEnvelope =
        try { LlmJson.json.decodeFromString(AgentEnvelope.serializer(), LlmJson.isolateJson(raw)) }
        catch (ex: Exception) { throw AgentDecodeException(ex.message ?: "malformed agent envelope") }
}
