package app.hisaab.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.Merchant
import app.hisaab.domain.normalizeMerchantName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.random.Random

class MerchantRepository(private val db: HisaabDatabase) {

    fun observeAll(): Flow<List<Merchant>> =
        db.transactionQueriesQueries.observeAllMerchants().asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    suspend fun upsertByName(name: String, defaultCategoryId: String? = null): String {
        val normalized = normalizeMerchantName(name)
        val existing = db.transactionQueriesQueries.findMerchantByNormalizedName(normalized).executeAsOneOrNull()
        if (existing != null) return existing.id
        val id = randomId()
        db.transactionQueriesQueries.insertMerchant(
            id = id,
            name = name.trim(),
            normalized_name = normalized,
            default_category_id = defaultCategoryId,
        )
        return id
    }

    fun searchByPrefix(prefix: String): Flow<List<Merchant>> {
        val normalized = normalizeMerchantName(prefix)
        return db.transactionQueriesQueries.searchMerchantsByPrefix(normalized).asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }
    }

    private fun migrations.Merchant.toDomain(): Merchant = Merchant(
        id = id,
        name = name,
        normalizedName = normalized_name,
        defaultCategoryId = default_category_id,
    )

    private fun randomId(): String {
        val bytes = Random.Default.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
