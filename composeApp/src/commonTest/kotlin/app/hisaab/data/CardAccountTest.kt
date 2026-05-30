package app.hisaab.data
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CardAccountTest {
    @Test fun `card account persists credit metadata and non-card leaves it null`() = runTest {
        val db = TestDatabase.create()
        val accounts = AccountRepository(db)
        val cardId = accounts.add("VISA", AccountKind.CARD, "BRAC",
            creditLimit = 50000.0, statementDay = 5, dueDay = 20)
        accounts.add("Cash", AccountKind.CASH, null)
        val all = accounts.observeActive().first()
        val card = all.single { it.id == cardId }
        assertEquals(50000.0, card.creditLimit)
        assertEquals(5, card.statementDay)
        assertEquals(20, card.dueDay)
        val cash = all.single { it.name == "Cash" }
        assertNull(cash.creditLimit); assertNull(cash.statementDay); assertNull(cash.dueDay)
    }
}
