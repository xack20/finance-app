package app.hisaab.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.BankType
import app.hisaab.domain.SenderMapping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.random.Random

class SenderRepository(private val db: HisaabDatabase) {

    private val queries get() = db.senderQueriesQueries

    suspend fun findBySenderId(senderId: String): SenderMapping? =
        queries.findBySenderId(senderId).executeAsOneOrNull()?.toDomain()

    fun observeAll(): Flow<List<SenderMapping>> =
        queries.observeAll().asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    suspend fun upsert(m: SenderMapping) {
        // Two-step upsert for SQLite 3.18 compat (no ON CONFLICT ... DO UPDATE support).
        // INSERT OR IGNORE ensures the row exists; UPDATE then patches descriptive fields
        // without touching account_id (preserving user mappings).
        queries.insertSenderIfNotExists(
            id = m.id.ifBlank { randomId() },
            sender_id = m.senderId,
            display_name = m.displayName,
            bank_type = m.bankType.name,
            is_financial = if (m.isFinancial) 1L else 0L,
            template_key = m.templateKey,
            account_id = m.accountId,
            created_at = if (m.createdAt > 0L) m.createdAt else now(),
        )
        queries.updateSenderFields(
            display_name = m.displayName,
            bank_type = m.bankType.name,
            is_financial = if (m.isFinancial) 1L else 0L,
            template_key = m.templateKey,
            sender_id = m.senderId,
        )
    }

    suspend fun setAccount(senderId: String, accountId: String) {
        queries.setAccount(accountId, senderId)
    }

    suspend fun seedKnownSenders() {
        SEED.forEach { seed ->
            if (queries.findBySenderId(seed.senderId).executeAsOneOrNull() == null) {
                queries.insertSenderIfNotExists(
                    id = randomId(),
                    sender_id = seed.senderId,
                    display_name = seed.displayName,
                    bank_type = seed.bankType.name,
                    is_financial = 1L,
                    template_key = seed.templateKey,
                    account_id = null,
                    created_at = now(),
                )
            }
        }
    }

    private fun migrations.Sender_registry.toDomain(): SenderMapping = SenderMapping(
        id = id,
        senderId = sender_id,
        displayName = display_name,
        bankType = BankType.valueOf(bank_type),
        isFinancial = is_financial == 1L,
        templateKey = template_key,
        accountId = account_id,
        createdAt = created_at,
    )

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private fun randomId(): String {
        val bytes = Random.Default.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    private data class Seed(
        val senderId: String,
        val displayName: String,
        val bankType: BankType,
        val templateKey: String?,
    )

    private companion object {
        // Known BD MFS + bank SMS sender IDs. Template keys consumed by M3-3 BankTemplates.
        val SEED = listOf(
            Seed("bKash", "bKash", BankType.BKASH, "bkash"),
            Seed("NAGAD", "Nagad", BankType.NAGAD, "nagad"),
            Seed("Rocket", "Rocket", BankType.ROCKET, "rocket"),
            Seed("CITY BANK", "City Bank", BankType.BANK, "city"),
            Seed("BRAC BANK", "BRAC Bank", BankType.BANK, "brac"),
            Seed("DBBL", "Dutch-Bangla Bank", BankType.BANK, "dbbl"),
        )
    }
}
