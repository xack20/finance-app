package app.hisaab.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.AgentQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class AgentQuestionRepository(private val db: HisaabDatabase) {

    private val queries get() = db.agentQuestionQueriesQueries
    private val inbox get() = db.captureInboxQueriesQueries
    private val optionsSerializer = ListSerializer(String.serializer())

    fun observeOpen(): Flow<List<AgentQuestion>> =
        queries.observeOpen().asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { it.toDomain() } }

    fun observeOpenCount(): Flow<Long> = queries.observeOpenCount().asFlow().mapToOne(Dispatchers.Default)

    suspend fun getById(id: String): AgentQuestion? = queries.getById(id).executeAsOneOrNull()?.toDomain()

    /** Creates an open question and parks [messageIds] on it. Blocking: call inside a db.transaction. */
    fun askBlocking(question: String, options: List<String>, messageIds: List<String>): String {
        val id = randomId()
        queries.insertQuestion(id, question, Json.encodeToString(optionsSerializer, options), now())
        messageIds.forEach { inbox.parkOnQuestion(id, it) }
        return id
    }

    /** Records the answer and hands the question's messages back to the agent, which sees the answer. */
    suspend fun answer(id: String, answer: String) {
        db.transaction {
            queries.markAnswered(answer, now(), id)
            inbox.requeueQuestion(id)
        }
    }

    /**
     * The user says a message the agent skipped is a real payment: it goes back to the agent with that answer
     * attached (as an already-answered question), so the next batch posts it.
     */
    suspend fun markAsPayment(messageId: String) {
        db.transaction {
            val id = randomId()
            queries.insertQuestion(id, MARKED_PAYMENT_QUESTION, "[]", now())
            queries.markAnswered(MARKED_PAYMENT_ANSWER, now(), id)
            inbox.requeueWithQuestion(id, messageId)
        }
    }

    /** "Not a payment": closes the question and drops its messages. */
    suspend fun dismiss(id: String) {
        db.transaction {
            queries.markAnswered(DISMISSED_ANSWER, now(), id)
            inbox.dismissQuestion(id)
        }
    }

    private fun migrations.Agent_question.toDomain() = AgentQuestion(
        id = id,
        question = question,
        options = runCatching { Json.decodeFromString(optionsSerializer, options) }.getOrDefault(emptyList()),
        answer = answer,
        createdAt = created_at,
    )

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private fun randomId(): String {
        val bytes = kotlin.random.Random.Default.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    companion object {
        const val DISMISSED_ANSWER = "Not a payment"
        private const val MARKED_PAYMENT_QUESTION = "You skipped this message. Is it a real payment?"
        private const val MARKED_PAYMENT_ANSWER = "Yes — it is a real payment. Post it."
    }
}
