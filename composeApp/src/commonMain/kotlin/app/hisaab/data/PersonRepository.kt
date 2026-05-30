package app.hisaab.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.Person
import app.hisaab.domain.PersonWithBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.random.Random

class PersonRepository(private val db: HisaabDatabase) {

    fun observeAll(): Flow<List<PersonWithBalance>> {
        // Combine the person list with the open lend_borrow rows so balance
        // updates whenever lend_borrow changes.
        return combine(
            db.personQueriesQueries.observeAllPersons().asFlow().mapToList(Dispatchers.Default),
            db.lendBorrowQueriesQueries.observeOpenLendBorrow().asFlow().mapToList(Dispatchers.Default),
        ) { persons, _ ->
            persons.map { p ->
                val balance = db.lendBorrowQueriesQueries.getPersonBalance(p.id)
                    .executeAsOneOrNull() ?: 0.0
                PersonWithBalance(person = p.toDomain(), balance = balance)
            }
        }
    }

    fun observeById(id: String): Flow<PersonWithBalance?> = combine(
        db.personQueriesQueries.getPerson(id).asFlow().mapToOneOrNull(Dispatchers.Default),
        db.lendBorrowQueriesQueries.observeOpenLendBorrow().asFlow().mapToList(Dispatchers.Default),
    ) { person, _ ->
        person?.let {
            val balance = db.lendBorrowQueriesQueries.getPersonBalance(it.id)
                .executeAsOneOrNull() ?: 0.0
            PersonWithBalance(person = it.toDomain(), balance = balance)
        }
    }

    suspend fun upsertFromContact(name: String, phone: String?): String {
        if (phone != null) {
            val existing = db.personQueriesQueries.findPersonByPhone(phone).executeAsOneOrNull()
            if (existing != null) return existing.id
        }
        val id = randomId()
        db.personQueriesQueries.insertPerson(id = id, name = name, contact_ref = phone)
        return id
    }

    suspend fun addManual(name: String): String {
        val id = randomId()
        db.personQueriesQueries.insertPerson(id = id, name = name, contact_ref = null)
        return id
    }

    /** Case-insensitive exact name lookup; null if no person matches. Non-suspending (for committer use). */
    fun findByNameBlocking(name: String): String? =
        db.personQueriesQueries.findPersonByName(name).executeAsOneOrNull()?.id

    /** Non-suspending person insert (for committer use inside a db.transaction). Returns the new id. */
    fun addManualBlocking(name: String): String {
        val id = randomId()
        db.personQueriesQueries.insertPerson(id = id, name = name, contact_ref = null)
        return id
    }

    private fun migrations.Person.toDomain(): Person = Person(
        id = id,
        name = name,
        contactRef = contact_ref,
    )

    private fun randomId(): String {
        val bytes = Random.Default.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
