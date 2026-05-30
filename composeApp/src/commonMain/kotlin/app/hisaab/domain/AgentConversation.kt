// AgentConversation.kt
package app.hisaab.domain
import app.hisaab.agent.AppliedSummary
import app.hisaab.agent.ProposedWrite

enum class AgentRole { USER, ASSISTANT }

data class AgentConversation(
    val id: String,
    val title: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class AgentMessage(
    val id: String,
    val conversationId: String,
    val role: AgentRole,
    val content: String,
    val proposedWrites: List<ProposedWrite>,
    val appliedSummary: AppliedSummary?,
    val createdAt: Long,
)
