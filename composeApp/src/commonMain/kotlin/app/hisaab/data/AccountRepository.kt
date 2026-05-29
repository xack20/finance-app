package app.hisaab.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.Account
import app.hisaab.domain.AccountKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class AccountRepository(private val db: HisaabDatabase) {

    fun observeActive(): Flow<List<Account>> =
        db.accountQueriesQueries.observeActiveAccounts().asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    suspend fun add(
        name: String,
        kind: AccountKind,
        institution: String?,
        currency: String = "BDT",
    ): String {
        val id = randomId()
        db.accountQueriesQueries.insertAccount(
            id = id,
            name = name,
            kind = kind.name,
            institution = institution,
            currency = currency,
            balance_tracking = 1L,
            created_at = Clock.System.now().toEpochMilliseconds(),
        )
        return id
    }

    /**
     * Non-suspending variant for use inside a [app.hisaab.db.HisaabDatabase.transaction] block.
     * Identical to [add] but callable synchronously from within a DB transaction.
     * Called by AccountMatcher's atomic auto-create path.
     */
    fun addBlocking(
        name: String,
        kind: AccountKind,
        institution: String?,
        currency: String = "BDT",
    ): String {
        val id = randomId()
        db.accountQueriesQueries.insertAccount(
            id = id,
            name = name,
            kind = kind.name,
            institution = institution,
            currency = currency,
            balance_tracking = 1L,
            created_at = Clock.System.now().toEpochMilliseconds(),
        )
        return id
    }

    suspend fun rename(id: String, name: String) {
        db.accountQueriesQueries.renameAccount(name, id)
    }

    suspend fun archive(id: String) {
        db.accountQueriesQueries.archiveAccount(Clock.System.now().toEpochMilliseconds(), id)
    }

    suspend fun ensureDefaultCashAccount(): String {
        val existing = db.accountQueriesQueries.observeActiveAccounts().executeAsList()
        existing.firstOrNull { it.kind == AccountKind.CASH.name }?.let { return it.id }
        return add(name = "Cash", kind = AccountKind.CASH, institution = null)
    }

    private fun migrations.Account.toDomain(): Account = Account(
        id = id,
        name = name,
        kind = AccountKind.valueOf(kind),
        institution = institution,
        currency = currency,
        balanceTracking = balance_tracking == 1L,
        createdAt = created_at,
        archivedAt = archived_at,
    )

    private fun randomId(): String {
        val bytes = kotlin.random.Random.Default.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
