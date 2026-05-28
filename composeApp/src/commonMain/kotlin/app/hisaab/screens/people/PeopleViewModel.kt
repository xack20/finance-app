package app.hisaab.screens.people

import app.hisaab.data.AccountRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.PersonRepository
import app.hisaab.domain.Account
import app.hisaab.domain.LendBorrowRow
import app.hisaab.domain.Person
import app.hisaab.domain.PersonWithBalance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PersonDetail(
    val person: Person,
    val records: List<LendBorrowRow>,
    val balance: Double,
)

class PeopleViewModel(
    private val personRepo: PersonRepository,
    private val lendBorrowRepo: LendBorrowRepository,
    accountRepo: AccountRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    val people: StateFlow<List<PersonWithBalance>> = personRepo.observeAll()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeAccounts: StateFlow<List<Account>> = accountRepo.observeActive()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun personDetail(personId: String): StateFlow<PersonDetail?> = combine(
        personRepo.observeById(personId),
        lendBorrowRepo.observeForPerson(personId),
    ) { pwb, records ->
        pwb?.let { PersonDetail(it.person, records, it.balance) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun settle(lendBorrowId: String, amount: Double, accountId: String) {
        scope.launch { lendBorrowRepo.settle(lendBorrowId, amount, accountId) }
    }

    fun addManualPerson(name: String, onAdded: (String) -> Unit = {}) {
        scope.launch {
            val id = personRepo.addManual(name)
            onAdded(id)
        }
    }

    fun upsertFromContact(name: String, phone: String?, onAdded: (String) -> Unit = {}) {
        scope.launch {
            val id = personRepo.upsertFromContact(name, phone)
            onAdded(id)
        }
    }
}
