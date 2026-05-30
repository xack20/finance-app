// ReadToolsTest.kt
package app.hisaab.agent
import app.hisaab.data.AccountRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertTrue

class ReadToolsTest {
    @Test fun `list_accounts returns account names as json`() = runTest {
        val db = TestDatabase.create()
        val accounts = AccountRepository(db); accounts.add("Cash", AccountKind.CASH, null)
        val out = ListAccountsTool(accounts).execute(JsonObject(emptyMap()))
        assertTrue("Cash" in out)
    }
    @Test fun `account_balances returns names with balances`() = runTest {
        val db = TestDatabase.create()
        val accounts = AccountRepository(db); accounts.add("Cash", AccountKind.CASH, null)
        val out = AccountBalancesTool(accounts).execute(JsonObject(emptyMap()))
        assertTrue("Cash" in out && "balance" in out)
    }
}
