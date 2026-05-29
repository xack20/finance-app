package app.hisaab.agent

enum class Role { SYSTEM, USER, ASSISTANT, TOOL }

data class ChatMessage(val role: Role, val content: String)

/** Multi-turn chat capability for the agent loop. Implemented by cloud providers (Gemini first).
 *  Distinct from LlmProvider.parse() (the SMS single-shot path). On-device throws NotSupported. */
interface AgentProvider {
    /** Returns the assistant's raw text (expected to be one JSON envelope). Throws LlmException on transport/key errors. */
    suspend fun complete(messages: List<ChatMessage>, maxTokens: Int = 1024): String
}
