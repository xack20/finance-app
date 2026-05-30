// ConversationRepository.kt
package app.hisaab.data
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.hisaab.agent.AgentWriteCodec
import app.hisaab.agent.AppliedSummary
import app.hisaab.agent.ProposedWrite
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.AgentConversation
import app.hisaab.domain.AgentMessage
import app.hisaab.domain.AgentRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.random.Random

class ConversationRepository(
    private val db: HisaabDatabase,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private val queries get() = db.agentQueriesQueries

    suspend fun createConversation(title: String? = null): String {
        val id = randomId(); val ts = now()
        queries.insertConversation(id = id, title = title, created_at = ts, updated_at = ts)
        return id
    }

    suspend fun appendUserMessage(conversationId: String, content: String): String =
        appendMessage(conversationId, AgentRole.USER, content, emptyList(), null)

    suspend fun appendAssistantMessage(
        conversationId: String,
        content: String,
        proposedWrites: List<ProposedWrite>,
        appliedSummary: AppliedSummary?,
    ): String = appendMessage(conversationId, AgentRole.ASSISTANT, content, proposedWrites, appliedSummary)

    private fun appendMessage(
        conversationId: String,
        role: AgentRole,
        content: String,
        proposedWrites: List<ProposedWrite>,
        appliedSummary: AppliedSummary?,
    ): String {
        val id = randomId(); val ts = now()
        queries.insertMessage(
            id = id,
            conversation_id = conversationId,
            role = role.name,
            content = content,
            proposed_writes = AgentWriteCodec.encodeWrites(proposedWrites),
            applied_summary = AgentWriteCodec.encodeSummary(appliedSummary),
            created_at = ts,
        )
        queries.touchConversation(updated_at = ts, id = conversationId)
        return id
    }

    fun observeMessages(conversationId: String): Flow<List<AgentMessage>> =
        queries.observeMessages(conversationId).asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    fun observeConversations(): Flow<List<AgentConversation>> =
        queries.observeConversations().asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    suspend fun latestConversationId(): String? =
        queries.latestConversation().executeAsOneOrNull()?.id

    private fun migrations.Agent_message.toDomain(): AgentMessage = AgentMessage(
        id = id,
        conversationId = conversation_id,
        role = runCatching { AgentRole.valueOf(role) }.getOrDefault(AgentRole.USER),
        content = content,
        proposedWrites = AgentWriteCodec.decodeWrites(proposed_writes),
        appliedSummary = AgentWriteCodec.decodeSummary(applied_summary),
        createdAt = created_at,
    )

    private fun migrations.Agent_conversation.toDomain(): AgentConversation = AgentConversation(
        id = id, title = title, createdAt = created_at, updatedAt = updated_at,
    )

    private fun randomId(): String {
        val bytes = Random.Default.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
