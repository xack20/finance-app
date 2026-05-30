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
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TxnKind
import app.hisaab.domain.TxnSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** set_budget / recategorize / add_split_transaction — the M4 deferred write tools (parse + apply). */
class DeferredWriteToolsTest {
    private fun committer(db: HisaabDatabase): WriteBatchCommitter {
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        return WriteBatchCommitter(db, AccountRepository(db), CategoryRepository(db),
            PersonRepository(db), txns, LendBorrowRepository(db, txns), BudgetRepository(db))
    }

    @Test
    fun `parse handles the three deferred write tools`() {
        assertEquals(
            WriteIntent.SetBudget("Food", 500.0, null),
            WriteIntent.parse(ProposedWrite("set_budget",
                buildJsonObject { put("category", "Food"); put("amount", 500.0) })),
        )
        assertEquals(
            WriteIntent.SetBudget("Food", 500.0, "2025-01"),
            WriteIntent.parse(ProposedWrite("set_budget",
                buildJsonObject { put("category", "Food"); put("amount", 500.0); put("month", "2025-01") })),
        )
        assertEquals(
            WriteIntent.Recategorize("t1", "Food"),
            WriteIntent.parse(ProposedWrite("recategorize",
                buildJsonObject { put("transactionId", "t1"); put("category", "Food") })),
        )
        assertEquals(
            WriteIntent.AddSplitTransaction("Cash", 100.0, "EXPENSE",
                listOf(WriteIntent.SplitLeg("A", 60.0), WriteIntent.SplitLeg("B", 40.0))),
            WriteIntent.parse(ProposedWrite("add_split_transaction", buildJsonObject {
                put("account", "Cash"); put("amount", 100.0)
                putJsonArray("splits") {
                    addJsonObject { put("category", "A"); put("amount", 60.0) }
                    addJsonObject { put("category", "B"); put("amount", 40.0) }
                }
            })),
        )
    }

    @Test
    fun `set_budget records a monthly cap for a category created in the same batch`() = runTest {
        val db = TestDatabase.create()
        CategoryRepository(db).ensureDefaults()
        val s = committer(db).apply(listOf(
            ProposedWrite("create_category", buildJsonObject { put("name", "Subscriptions") }),
            ProposedWrite("set_budget", buildJsonObject { put("category", "Subscriptions"); put("amount", 1500.0) }),
        ))
        assertEquals(1, s.categoriesCreated)
        assertEquals(1, s.budgetsSet)
        val budgets = BudgetRepository(db).observeActive().first()
        assertEquals(1, budgets.size)
        assertEquals(1500.0, budgets.first().monthlyCapAmount)
        assertEquals("Subscriptions", budgets.first().categoryName)
    }

    @Test
    fun `recategorize changes an existing transaction's category`() = runTest {
        val db = TestDatabase.create()
        val cats = CategoryRepository(db)
        cats.ensureDefaults()
        // Target an EXISTING default category (no create_category) to avoid name/id ambiguity.
        val target = cats.observeAll().first().first()
        val accounts = AccountRepository(db)
        accounts.add("Cash", AccountKind.CASH, null)
        val cashId = accounts.observeActive().first().first { it.name == "Cash" }.id
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val txnId = txns.add(NewTransaction(
            accountId = cashId, amount = 200.0, ts = 0L, merchantName = null,
            categoryId = null, notes = null, kind = TxnKind.EXPENSE, source = TxnSource.CHAT,
        ))

        val s = committer(db).apply(listOf(
            ProposedWrite("recategorize", buildJsonObject { put("transactionId", txnId); put("category", target.name) }),
        ))

        assertEquals(1, s.recategorized)
        assertEquals(target.id, txns.getById(txnId)?.categoryId)
    }

    @Test
    fun `add_split_transaction creates a parent split across categories`() = runTest {
        val db = TestDatabase.create()
        CategoryRepository(db).ensureDefaults()
        AccountRepository(db).add("Cash", AccountKind.CASH, null)

        val s = committer(db).apply(listOf(
            ProposedWrite("create_category", buildJsonObject { put("name", "Groceries") }),
            ProposedWrite("create_category", buildJsonObject { put("name", "Household") }),
            ProposedWrite("add_split_transaction", buildJsonObject {
                put("account", "Cash"); put("amount", 1000.0); put("kind", "EXPENSE")
                putJsonArray("splits") {
                    addJsonObject { put("category", "Groceries"); put("amount", 600.0) }
                    addJsonObject { put("category", "Household"); put("amount", 400.0) }
                }
            }),
        ))

        assertEquals(1, s.splitTransactionsAdded)
        val cats = CategoryRepository(db).observeAll().first()
        val groceriesId = cats.first { it.name == "Groceries" }.id
        val householdId = cats.first { it.name == "Household" }.id
        val recent = TransactionRepository(db, MerchantRepository(db), TagRepository(db)).observeRecent().first()
        val parent = recent.first { it.amount == 1000.0 } // observeRecent is top-level only (parent_txn_id IS NULL)

        // The two child legs must actually be inserted under the parent with resolved categories.
        val children = db.transactionQueriesQueries.observeChildSplits(parent.id).executeAsList()
        assertEquals(2, children.size, "both split legs should be inserted under the parent")
        assertEquals(setOf(600.0, 400.0), children.map { it.amount }.toSet())
        assertEquals(setOf(groceriesId, householdId), children.mapNotNull { it.category_id }.toSet())
        children.forEach { assertEquals(parent.id, it.parent_txn_id) }
    }

    @Test
    fun `recategorize on a missing transaction rolls the batch back`() = runTest {
        val db = TestDatabase.create()
        val cats = CategoryRepository(db)
        cats.ensureDefaults()
        val before = cats.observeAll().first().size

        // recategorizeBlocking error()s on a missing id; the whole db.transaction must roll back,
        // so the create_category in the same batch is undone too.
        assertFailsWith<IllegalStateException> {
            committer(db).apply(listOf(
                ProposedWrite("create_category", buildJsonObject { put("name", "ZZTemp") }),
                ProposedWrite("recategorize", buildJsonObject { put("transactionId", "does-not-exist"); put("category", "ZZTemp") }),
            ))
        }
        assertEquals(before, cats.observeAll().first().size, "batch must roll back — no category created")
    }

    @Test
    fun `set_budget honors an explicit valid month and falls back on an out-of-range one`() = runTest {
        val db = TestDatabase.create()
        CategoryRepository(db).ensureDefaults()
        committer(db).apply(listOf(
            ProposedWrite("create_category", buildJsonObject { put("name", "Travel") }),
            ProposedWrite("set_budget", buildJsonObject { put("category", "Travel"); put("amount", 2000.0); put("month", "2025-01") }),
        ))
        val travel = BudgetRepository(db).observeActive().first().first { it.categoryName == "Travel" }
        assertEquals("2025-01", travel.startsMonth.value)

        // An out-of-range month (13) must NOT abort the batch and must NOT be persisted verbatim.
        val db2 = TestDatabase.create()
        CategoryRepository(db2).ensureDefaults()
        val s = committer(db2).apply(listOf(
            ProposedWrite("create_category", buildJsonObject { put("name", "Gifts") }),
            ProposedWrite("set_budget", buildJsonObject { put("category", "Gifts"); put("amount", 500.0); put("month", "2025-13") }),
        ))
        assertEquals(1, s.budgetsSet, "out-of-range month must not abort the batch")
        val gifts = BudgetRepository(db2).observeActive().first().first { it.categoryName == "Gifts" }
        assertEquals(false, gifts.startsMonth.value == "2025-13", "invalid month must fall back to the current month")
    }
}
