package app.hisaab.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.random.Random

class TagRepository(private val db: HisaabDatabase) {

    fun observeAll(): Flow<List<Tag>> =
        db.transactionQueriesQueries.observeAllTags().asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    suspend fun upsertByName(name: String): String {
        val existing = db.transactionQueriesQueries.findTagByName(name).executeAsOneOrNull()
        if (existing != null) return existing.id
        val id = randomId()
        db.transactionQueriesQueries.insertTag(
            id = id,
            name = name,
            color = null,
            created_at = Clock.System.now().toEpochMilliseconds(),
        )
        return id
    }

    suspend fun linkToTxn(txnId: String, tagIds: List<String>) {
        db.transactionQueriesQueries.unlinkTxnTags(txnId)
        tagIds.forEach { tagId ->
            db.transactionQueriesQueries.linkTxnTag(txn_id = txnId, tag_id = tagId)
        }
    }

    fun observeTagsForTxn(txnId: String): Flow<List<Tag>> =
        db.transactionQueriesQueries.observeTagsForTxn(txnId).asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    private fun migrations.Tag.toDomain(): Tag = Tag(
        id = id,
        name = name,
        color = color,
        createdAt = created_at,
    )

    private fun randomId(): String {
        val bytes = Random.Default.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
