package app.hisaab.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.AgentMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class AgentMemoryRepository(private val db: HisaabDatabase) {

    private val queries get() = db.agentMemoryQueriesQueries

    fun observeAll(): Flow<List<AgentMemory>> =
        queries.observeAll().asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { it.toDomain() } }

    suspend fun all(): List<AgentMemory> = queries.observeAll().executeAsList().map { it.toDomain() }

    /** Blocking: call inside a db.transaction (the capture committer). */
    fun addBlocking(fact: String) {
        val bytes = kotlin.random.Random.Default.nextBytes(16)
        val id = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        queries.insertMemory(id, fact, Clock.System.now().toEpochMilliseconds())
    }

    suspend fun delete(id: String) {
        queries.deleteMemory(id)
    }

    private fun migrations.Agent_memory.toDomain() = AgentMemory(id = id, fact = fact, createdAt = created_at)
}
