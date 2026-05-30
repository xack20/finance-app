package app.hisaab.agent
import app.hisaab.data.AccountRepository
import app.hisaab.data.BudgetRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class CreateCardAccountTest {
    @Test fun `create_account with card fields creates a CARD with limit and dates`() = runTest {
        val db = TestDatabase.create()
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val accounts = AccountRepository(db)
        val committer = WriteBatchCommitter(db, accounts, CategoryRepository(db),
            PersonRepository(db), txns, LendBorrowRepository(db, txns), BudgetRepository(db))
        committer.apply(listOf(
            ProposedWrite("create_account", buildJsonObject {
                put("name", "VISA"); put("kind", "CARD")
                put("creditLimit", 50000.0); put("statementDay", 5); put("dueDay", 20)
            }),
        ))
        val card = accounts.observeActive().first().single { it.name == "VISA" }
        assertEquals(AccountKind.CARD, card.kind)
        assertEquals(50000.0, card.creditLimit)
        assertEquals(5, card.statementDay)
        assertEquals(20, card.dueDay)
    }
}
