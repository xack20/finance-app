# M4-4 — Credit Cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `AccountKind.CARD` accounts first-class — store card metadata (credit limit, statement day, due day), compute signed card outstanding + available credit, derive the next payment due date, expose a `card_summary` read tool, and let `create_account` create cards. Canonical rule: a card **purchase** is an `EXPENSE` on the card (counts as spend + raises outstanding); a card **bill payment** is a `TRANSFER` into the card (reduces outstanding, NOT counted as spend).

**Architecture:** Migration `6.sqm` adds 3 nullable columns to `account`. `cardOutstanding` is a per-account signed `SUM` (EXPENSE +, INCOME/TRANSFER −) over `parent_txn_id IS NULL`. A pure `CardSummaryCalculator` computes the next due date from `statement_day`/`due_day` and a reference date. A `card_summary` `ReadTool` surfaces it to the agent. `create_account` gains optional card fields. Card *purchases* and *payments* already work via M4-2's `add_transaction` (EXPENSE on card) and `record_card_payment`/`transfer` (TRANSFER into card) — this slice adds the card-specific reporting on top.

**Tech Stack:** Kotlin Multiplatform, SQLDelight 2 + SQLCipher, kotlinx.serialization (`LlmJson.json`), kotlinx-datetime (`LocalDate`), kotlin.test + kotlinx-coroutines-test.

**Scope (M4-4):** `6.sqm` card columns + account ripple; `cardOutstanding` query + `availableCredit`; bill-payment-≠-spend test; `CardSummary` + `CardSummaryCalculator` (due-date algorithm) + edge-case tests; `card_summary` read tool; `create_account` card fields (WriteIntent + descriptor + committer).

**Out of scope (later):** card **UI** (AccountsScreen creation inputs, card detail) → **M4-6**; interest/statements/rewards/EMI/reminders → deferred (spec §18).

**Migration numbering:** M4-2 `4.sqm` (transfer_group_id), M4-3 `5.sqm` (agent tables). **M4-4 → `6.sqm` (card columns).**

---

## Grounded codebase facts (verified — rely on these)

- `account` final columns: `id, name, kind, institution, currency, balance_tracking, created_at, archived_at` (archived_at added in `2.sqm`). Next migration = `6.sqm`.
- `AccountQueries.sq` `insertAccount` = 7 columns `(id, name, kind, institution, currency, balance_tracking, created_at)`. `observeActiveAccounts`/`getAccountById` are `SELECT *` → `migrations.Account` (maps by column name; an appended column is auto-included).
- `Account` (domain) = `data class Account(id, name, kind: AccountKind, institution: String?, currency: String, balanceTracking: Boolean, createdAt: Long, archivedAt: Long?)`. `AccountKind{CASH,BANK,MFS,CARD,GOAL}`.
- `AccountRepository(db)`: `add(name, kind, institution, currency="BDT"): String` (suspend) + `addBlocking(name, kind, institution, currency="BDT"): String` (sync) — both call `db.accountQueriesQueries.insertAccount(... balance_tracking = 1L, created_at = ...)`. `observeActive(): Flow<List<Account>>`. `accountBalances(): Map<String,Double>` (M4-1). Private `migrations.Account.toDomain()` and `randomId()`. Accessor `db.accountQueriesQueries`.
- Spend queries in `InsightQueries.sq` (`categoryBreakdownForRange`, daily, monthly, per-category) ALL filter `kind = 'EXPENSE'` and `parent_txn_id IS NULL`. So `TRANSFER` rows (card payments) are NEVER counted as spend, and `EXPENSE` rows on a card ARE.
- `cardOutstanding` model (per-account signed sum), modeled on `accountBalances`:
  ```sql
  SELECT COALESCE(SUM(CASE kind
    WHEN 'EXPENSE'  THEN amount     -- purchase raises debt
    WHEN 'INCOME'   THEN -amount    -- refund lowers debt
    WHEN 'TRANSFER' THEN -amount    -- payment into the card lowers debt
    ELSE 0 END), 0.0)
  FROM txn WHERE account_id = ? AND parent_txn_id IS NULL;
  ```
- `kotlinx.datetime.LocalDate` is available: `LocalDate(year, monthNumber, day)`, `.year`, `.monthNumber` (1..12), `.dayOfMonth`, `.atStartOfDayIn(tz)`, `LocalDate.parse("YYYY-MM-DD")`. `Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date` gives today's `LocalDate` (see `util/DateRange.kt`, `MonthViewModel`).
- From M4-1: `ReadTool { name; description; paramsDoc; suspend execute(JsonObject): String }`; the `jsonArray { addJsonObject { put(...) } }` + `argStr` helpers in `ReadTools.kt`. `LlmJson.json`.
- From M4-2: `WriteIntent.CreateAccount(name, kind)` + `WriteIntent.parse` + private `JsonObject.str`/`dbl` helpers in `WriteTools.kt`; `agentWriteDescriptors()` includes `create_account`; `WriteBatchCommitter` pass 1 calls `accounts.addBlocking(i.name, kind, institution=null)` and increments a local `accts` counter.
- `TestDatabase.create()` applies all migrations (incl. `6.sqm`), FK-enforced. `CategoryRepository(db).ensureDefaults()` seeds `lend`/`borrow`/`transfer` categories (needed when a test inserts txns referencing those category ids, e.g. a transfer leg).

---

## File Structure

- Create `composeApp/src/commonMain/sqldelight/migrations/6.sqm` — 3 card columns.
- Modify `composeApp/src/commonMain/sqldelight/app/hisaab/db/AccountQueries.sq` — `insertAccount` (+3 cols), new `cardOutstanding`.
- Modify `composeApp/src/commonMain/kotlin/app/hisaab/domain/Account.kt` — +3 nullable card fields.
- Modify `composeApp/src/commonMain/kotlin/app/hisaab/data/AccountRepository.kt` — `add`/`addBlocking` (+3 nullable params), `toDomain` (+3), new `cardOutstanding(accountId)`.
- Create `composeApp/src/commonMain/kotlin/app/hisaab/domain/CardSummary.kt` — `CardSummary` + `CardSummaryCalculator`.
- Create `composeApp/src/commonMain/kotlin/app/hisaab/agent/CardSummaryTool.kt` — the `card_summary` read tool.
- Modify `composeApp/src/commonMain/kotlin/app/hisaab/agent/WriteTools.kt` — `CreateAccount` +3 fields, parser, descriptor.
- Modify `composeApp/src/commonMain/kotlin/app/hisaab/agent/WriteBatchCommitter.kt` — pass card fields to `addBlocking`.
- Tests: `composeApp/src/commonTest/kotlin/app/hisaab/data/CardAccountTest.kt`, `CardOutstandingTest.kt`, `composeApp/src/commonTest/kotlin/app/hisaab/domain/CardSummaryCalculatorTest.kt`, `composeApp/src/commonTest/kotlin/app/hisaab/agent/CardSummaryToolTest.kt`, `composeApp/src/commonTest/kotlin/app/hisaab/agent/CreateCardAccountTest.kt`.

---

## Task 1: `6.sqm` card columns + account ripple

**Files:**
- Create: `composeApp/src/commonMain/sqldelight/migrations/6.sqm`
- Modify: `composeApp/src/commonMain/sqldelight/app/hisaab/db/AccountQueries.sq`
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/domain/Account.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/data/AccountRepository.kt`
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/data/CardAccountTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// CardAccountTest.kt
package app.hisaab.data
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CardAccountTest {
    @Test fun `card account persists credit metadata; non-card leaves it null`() = runTest {
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
```

- [ ] **Step 2: Run to confirm fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.CardAccountTest" --console=plain`
Expected: FAIL — `creditLimit`/named card params unresolved.

- [ ] **Step 3: Create `6.sqm`:**

```sql
-- M4-4: credit-card metadata on account. A card is AccountKind.CARD with these set;
-- non-card accounts leave them NULL. Days are constrained to 1..28 at creation (caller-enforced).
ALTER TABLE account ADD COLUMN credit_limit  REAL;
ALTER TABLE account ADD COLUMN statement_day INTEGER;
ALTER TABLE account ADD COLUMN due_day       INTEGER;
```

- [ ] **Step 4: Update `AccountQueries.sq`** — extend `insertAccount` to 10 columns (append the 3 card columns last):

```sql
insertAccount:
INSERT INTO account(id, name, kind, institution, currency, balance_tracking, created_at, credit_limit, statement_day, due_day)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
```

Add the per-card outstanding query:

```sql
cardOutstanding:
SELECT COALESCE(SUM(CASE kind
  WHEN 'EXPENSE'  THEN amount
  WHEN 'INCOME'   THEN -amount
  WHEN 'TRANSFER' THEN -amount
  ELSE 0 END), 0.0) AS outstanding
FROM txn WHERE account_id = ? AND parent_txn_id IS NULL;
```

- [ ] **Step 5: Update `Account.kt`** — add 3 nullable fields (after `archivedAt`):

```kotlin
    val archivedAt: Long?,
    val creditLimit: Double? = null,
    val statementDay: Int? = null,
    val dueDay: Int? = null,
)
```

> The DB columns `statement_day`/`due_day` are `INTEGER` → SQLDelight generates `Long?`; the domain uses `Int?`, so the mapper converts (`statement_day?.toInt()`).

- [ ] **Step 6: Update `AccountRepository.kt`**

(a) `add` and `addBlocking` gain 3 nullable-default params and pass them to `insertAccount`. For `add`:

```kotlin
suspend fun add(
    name: String,
    kind: AccountKind,
    institution: String?,
    currency: String = "BDT",
    creditLimit: Double? = null,
    statementDay: Int? = null,
    dueDay: Int? = null,
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
        credit_limit = creditLimit,
        statement_day = statementDay?.toLong(),
        due_day = dueDay?.toLong(),
    )
    return id
}
```

Apply the SAME 3 params + the SAME 3 trailing `insertAccount` args to `addBlocking` (identical body shape).

(b) Add the outstanding accessor:

```kotlin
/** Signed card debt: purchases (EXPENSE) raise it; refunds (INCOME) and payments (TRANSFER) lower it. */
suspend fun cardOutstanding(accountId: String): Double =
    db.accountQueriesQueries.cardOutstanding(accountId).executeAsOne()
```

> If SQLDelight types the `COALESCE(...)` column as nullable `Double?`, use `.executeAsOne() ?: 0.0`; if non-null `Double`, the bare `.executeAsOne()` is correct (mirror what M4-1's `accountBalances` did — it dropped the elvis because COALESCE typed non-null). Let the build decide.

(c) Extend `toDomain` (+3 fields, converting INTEGER→Int):

```kotlin
        archivedAt = archived_at,
        creditLimit = credit_limit,
        statementDay = statement_day?.toInt(),
        dueDay = due_day?.toInt(),
    )
```

- [ ] **Step 7: Run to confirm pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.CardAccountTest" --tests "app.hisaab.data.AccountRepositoryTest" --tests "app.hisaab.data.AccountBalanceTest" --console=plain`
Expected: PASS (new test + existing account tests still green — proves the ripple didn't break callers).

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/sqldelight/migrations/6.sqm composeApp/src/commonMain/sqldelight/app/hisaab/db/AccountQueries.sq composeApp/src/commonMain/kotlin/app/hisaab/domain/Account.kt composeApp/src/commonMain/kotlin/app/hisaab/data/AccountRepository.kt composeApp/src/commonTest/kotlin/app/hisaab/data/CardAccountTest.kt
git commit -m "feat(data): 6.sqm card columns (credit_limit/statement_day/due_day) + account ripple + cardOutstanding"
```

> If a DIRECT `insertAccount(...)` call exists elsewhere (e.g. a test calling the generated query directly, like `TagRepositoryTest` did for `insertTxn` in M4-2 T1), add the 3 trailing args there and include that file in the commit. `add`/`addBlocking` callers are unaffected (new params are nullable-default).

---

## Task 2: bill-payment-≠-spend + cardOutstanding signed math

**Files:**
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/data/CardOutstandingTest.kt`

> No new production code — `cardOutstanding` landed in Task 1. This is the canonical-rule acceptance test (spec §9/§17): a purchase raises outstanding AND counts as spend; a payment lowers outstanding AND does NOT count as spend.

- [ ] **Step 1: Write the test**

```kotlin
// CardOutstandingTest.kt
package app.hisaab.data
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TxnKind
import app.hisaab.domain.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class CardOutstandingTest {
    @Test fun `purchase raises outstanding and counts as spend; payment lowers outstanding and is not spend`() = runTest {
        val db = TestDatabase.create()
        val categories = CategoryRepository(db)
        categories.ensureDefaults()                       // seed "transfer" (payment leg FK)
        val foodId = categories.add("Food", null, null, null)   // explicit category for the purchase
        val accounts = AccountRepository(db)
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val insights = InsightRepository(db)
        val card = accounts.add("VISA", AccountKind.CARD, null, creditLimit = 50000.0, statementDay = 5, dueDay = 20)
        val bank = accounts.add("Bank", AccountKind.BANK, null)

        val ms = 1_770_000_000_000L                        // a fixed instant; derive its month below
        val ym = monthOf(ms)

        // card PURCHASE: EXPENSE on the card.
        txns.add(NewTransaction(accountId = card, amount = 3000.0, ts = ms, merchantName = null,
            categoryId = foodId, notes = null, kind = TxnKind.EXPENSE))
        assertEquals(3000.0, accounts.cardOutstanding(card))
        assertEquals(3000.0, monthlySpend(insights, ym))   // purchase counts as spend

        // card BILL PAYMENT: TRANSFER bank → card.
        db.transaction { txns.transferBlocking(bank, card, 2000.0, ts = ms, notes = null) }
        assertEquals(1000.0, accounts.cardOutstanding(card))  // 3000 − 2000
        assertEquals(3000.0, monthlySpend(insights, ym))      // UNCHANGED — payment is not spend
    }

    private suspend fun monthlySpend(insights: InsightRepository, ym: YearMonth): Double =
        insights.computeCategoryBreakdown(ym).first().sumOf { it.total }

    private fun monthOf(ms: Long): YearMonth {
        val ldt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
        return YearMonth.of(ldt.year, ldt.monthNumber)
    }
}
```

> **Implementer note:** verify `InsightRepository.computeCategoryBreakdown(YearMonth): Flow<List<CategorySlice>>` and `CategorySlice.total` (confirmed in M4-1). Verify `CategoryRepository.add(name, color, icon, parentId): String` returns the new id (confirmed). If `Instant`/`toLocalDateTime` imports differ, match `util/DateRange.kt`. The two `cardOutstanding` assertions + the unchanged-spend assertion are the essential checks; if seeding/accessor names need tweaks, fix the TEST, not production. Surface any real `cardOutstanding` math bug.

- [ ] **Step 2: Run to confirm pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.CardOutstandingTest" --console=plain`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonTest/kotlin/app/hisaab/data/CardOutstandingTest.kt
git commit -m "test(data): card purchase = spend + raises outstanding; payment = not spend + lowers outstanding"
```

---

## Task 3: `CardSummary` + `CardSummaryCalculator` (due-date algorithm)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/domain/CardSummary.kt`
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/domain/CardSummaryCalculatorTest.kt`

> Pure date math, fully deterministic (takes a reference `today`). Rule (spec §9): the statement closes on `statementDay` monthly; the most recent close ≤ today is this month (if `today.dayOfMonth >= statementDay`) else last month; the due date is `dueDay` in the close's month, advancing one month when `dueDay <= statementDay`; clamp the day to the month length.

- [ ] **Step 1: Write the failing test**

```kotlin
// CardSummaryCalculatorTest.kt
package app.hisaab.domain
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CardSummaryCalculatorTest {
    private fun due(today: String, statementDay: Int, dueDay: Int): LocalDate =
        CardSummaryCalculator.nextDueDate(LocalDate.parse(today), statementDay, dueDay)

    @Test fun `due later in same month when dueDay greater than statementDay`() {
        assertEquals(LocalDate(2026, 3, 20), due("2026-03-10", statementDay = 5, dueDay = 20))
    }
    @Test fun `before this month's close, due is in the prior cycle (Feb)`() {
        assertEquals(LocalDate(2026, 2, 20), due("2026-03-03", statementDay = 5, dueDay = 20))
    }
    @Test fun `due advances to next month when dueDay not greater than statementDay`() {
        assertEquals(LocalDate(2026, 4, 10), due("2026-03-26", statementDay = 25, dueDay = 10))
    }
    @Test fun `year rolls over December close to January due`() {
        assertEquals(LocalDate(2027, 1, 10), due("2026-12-27", statementDay = 25, dueDay = 10))
    }
    @Test fun `day clamps to last day of a short month`() {
        // close Feb 15; dueDay 31 > 15 → due Feb 31 → clamp 28 (2026 not leap)
        assertEquals(LocalDate(2026, 2, 28), due("2026-02-20", statementDay = 15, dueDay = 31))
    }
    @Test fun `leap February clamps to 29`() {
        assertEquals(LocalDate(2028, 2, 29), due("2028-02-20", statementDay = 15, dueDay = 31))
    }
}
```

- [ ] **Step 2: Run to confirm fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.domain.CardSummaryCalculatorTest" --console=plain`
Expected: FAIL — `CardSummaryCalculator` unresolved.

- [ ] **Step 3: Implement `CardSummary.kt`**

```kotlin
// CardSummary.kt
package app.hisaab.domain
import kotlinx.datetime.LocalDate

/** Card reporting snapshot. Fields are null when the account isn't a configured card. */
data class CardSummary(
    val outstanding: Double,
    val creditLimit: Double?,
    val availableCredit: Double?,     // creditLimit - outstanding (null if no limit)
    val statementDay: Int?,
    val dueDay: Int?,
    val nextDueDate: LocalDate?,      // null if statementDay/dueDay missing
)

/** Pure next-due-date math. statementDay/dueDay are expected in 1..28 but the day is clamped defensively. */
object CardSummaryCalculator {
    fun nextDueDate(today: LocalDate, statementDay: Int, dueDay: Int): LocalDate {
        // 1. month of the most recent statement close (on statementDay) that is <= today
        var closeYear = today.year
        var closeMonth = today.monthNumber
        if (today.dayOfMonth < statementDay) {
            if (closeMonth == 1) { closeYear -= 1; closeMonth = 12 } else closeMonth -= 1
        }
        // 2. due date is in the close month, advancing one month when dueDay <= statementDay
        var dueYear = closeYear
        var dueMonth = closeMonth
        if (dueDay <= statementDay) {
            if (dueMonth == 12) { dueYear += 1; dueMonth = 1 } else dueMonth += 1
        }
        // 3. clamp the day to the month length
        val day = minOf(dueDay, lastDayOfMonth(dueYear, dueMonth))
        return LocalDate(dueYear, dueMonth, day)
    }

    private fun lastDayOfMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        else -> if (isLeapYear(year)) 29 else 28
    }

    private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}
```

- [ ] **Step 4: Run to confirm pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.domain.CardSummaryCalculatorTest" --console=plain`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/domain/CardSummary.kt composeApp/src/commonTest/kotlin/app/hisaab/domain/CardSummaryCalculatorTest.kt
git commit -m "feat(domain): CardSummary + CardSummaryCalculator next-due-date math (edge cases pinned)"
```

---

## Task 4: `card_summary` read tool

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/agent/CardSummaryTool.kt`
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/agent/CardSummaryToolTest.kt`

> Surfaces a card's outstanding / available / next-due to the agent. Resolves the card by name from `AccountRepository.observeActive()`, computes outstanding via `cardOutstanding`, the due date via `CardSummaryCalculator` using an injectable "today". Models on M4-1's `AccountBalancesTool`.

- [ ] **Step 1: Write the failing test**

```kotlin
// CardSummaryToolTest.kt
package app.hisaab.agent
import app.hisaab.data.AccountRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TxnKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertTrue

class CardSummaryToolTest {
    @Test fun `card_summary returns outstanding and available credit for a card by name`() = runTest {
        val db = TestDatabase.create()
        val accounts = AccountRepository(db)
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        accounts.add("VISA", AccountKind.CARD, null, creditLimit = 50000.0, statementDay = 5, dueDay = 20)
        val cardId = accounts.observeActive().first().first { it.name == "VISA" }.id
        txns.add(NewTransaction(accountId = cardId, amount = 3000.0, ts = 1L, merchantName = null,
            categoryId = null, notes = null, kind = TxnKind.EXPENSE))
        val out = CardSummaryTool(accounts).execute(buildJsonObject { put("card", "VISA") })
        assertTrue("3000" in out)          // outstanding
        assertTrue("47000" in out)         // available = 50000 - 3000
    }
}
```

- [ ] **Step 2: Run to confirm fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.agent.CardSummaryToolTest" --console=plain`
Expected: FAIL — `CardSummaryTool` unresolved.

- [ ] **Step 3: Implement `CardSummaryTool.kt`**

```kotlin
// CardSummaryTool.kt
package app.hisaab.agent
import app.hisaab.data.AccountRepository
import app.hisaab.domain.AccountKind
import app.hisaab.domain.CardSummaryCalculator
import app.hisaab.llm.LlmJson
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CardSummaryTool(
    private val accounts: AccountRepository,
    private val today: () -> LocalDate = {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    },
) : ReadTool {
    override val name = "card_summary"
    override val description = "Outstanding balance, available credit and next due date for a credit card"
    override val paramsDoc = "{ \"card\": string }"

    override suspend fun execute(args: JsonObject): String {
        val cardName = (args["card"] as? JsonPrimitive)?.content?.lowercase().orEmpty()
        val account = accounts.observeActive().first()
            .firstOrNull { it.kind == AccountKind.CARD && it.name.lowercase() == cardName }
            ?: return LlmJson.json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("error", "no card named '$cardName'")
            })
        val outstanding = accounts.cardOutstanding(account.id)
        val limit = account.creditLimit
        val available = limit?.let { it - outstanding }
        val sd = account.statementDay
        val dd = account.dueDay
        val nextDue = if (sd != null && dd != null)
            CardSummaryCalculator.nextDueDate(today(), sd, dd).toString() else null
        return LlmJson.json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("name", account.name)
            put("outstanding", outstanding)
            limit?.let { put("creditLimit", it) }
            available?.let { put("availableCredit", it) }
            nextDue?.let { put("nextDueDate", it) }
        })
    }
}
```

- [ ] **Step 4: Run to confirm pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.agent.CardSummaryToolTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/agent/CardSummaryTool.kt composeApp/src/commonTest/kotlin/app/hisaab/agent/CardSummaryToolTest.kt
git commit -m "feat(agent): card_summary read tool (outstanding/available/next-due by card name)"
```

---

## Task 5: `create_account` card fields (write tool + committer)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/agent/WriteTools.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/agent/WriteBatchCommitter.kt`
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/agent/CreateCardAccountTest.kt`

> Lets the agent create a card with limits in one batch. Extends `WriteIntent.CreateAccount`, its parser, the descriptor, and the committer's account-creation pass.

- [ ] **Step 1: Write the failing test**

```kotlin
// CreateCardAccountTest.kt
package app.hisaab.agent
import app.hisaab.data.AccountRepository
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
            PersonRepository(db), txns, LendBorrowRepository(db, txns))
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
```

- [ ] **Step 2: Run to confirm fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.agent.CreateCardAccountTest" --console=plain`
Expected: FAIL — card fields not parsed/applied (limit stays null → assertEquals fails).

- [ ] **Step 3: Extend `WriteIntent.CreateAccount` + parser + descriptor** in `WriteTools.kt`

Change the variant:
```kotlin
    data class CreateAccount(
        val name: String, val kind: String,
        val creditLimit: Double? = null, val statementDay: Int? = null, val dueDay: Int? = null,
    ) : WriteIntent
```
Change the parser branch (reuse the existing `str`/`dbl` helpers):
```kotlin
                "create_account" -> a.str("name")?.let {
                    CreateAccount(it, a.str("kind") ?: "CASH",
                        a.dbl("creditLimit"), a.dbl("statementDay")?.toInt(), a.dbl("dueDay")?.toInt())
                }
```
Update the descriptor:
```kotlin
    WriteDescriptor("create_account", "Create a new account (cards may set creditLimit/statementDay/dueDay)",
        "{ \"name\": string, \"kind\": \"CASH|BANK|MFS|CARD\", \"creditLimit\": number?, \"statementDay\": 1-28?, \"dueDay\": 1-28? }"),
```

- [ ] **Step 4: Thread card fields through the committer** in `WriteBatchCommitter.kt` (pass 1, the `CreateAccount` handler). Replace the existing `accounts.addBlocking(i.name, kind, institution = null)` line with:

```kotlin
                val id = accounts.addBlocking(i.name, kind, institution = null,
                    creditLimit = i.creditLimit, statementDay = i.statementDay, dueDay = i.dueDay)
```

> `addBlocking` gained the 3 nullable card params in Task 1, so this compiles. Keep the surrounding lines (kind resolution, `accountIdByName[...] = id`, the counter increment) unchanged.

- [ ] **Step 5: Run to confirm pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.agent.CreateCardAccountTest" --tests "app.hisaab.agent.WriteToolsTest" --tests "app.hisaab.agent.WriteBatchCommitterTest" --tests "app.hisaab.agent.WriteBatchAtomicTest" --console=plain`
Expected: PASS (new test + existing write-tool/committer tests still green — confirms the CreateAccount change didn't break M4-2).

- [ ] **Step 6: Run the full agent+data sweep (regression)**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.agent.*" --tests "app.hisaab.data.*" --console=plain`
Expected: PASS (all M4-1/2/3/4 tests green).

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/agent/WriteTools.kt composeApp/src/commonMain/kotlin/app/hisaab/agent/WriteBatchCommitter.kt composeApp/src/commonTest/kotlin/app/hisaab/agent/CreateCardAccountTest.kt
git commit -m "feat(agent): create_account supports card fields (creditLimit/statementDay/dueDay)"
```

---

## Acceptance criteria (M4-4 done)

- `6.sqm` adds the 3 nullable card columns; a CARD account persists creditLimit/statementDay/dueDay, a non-card leaves them null; existing account tests stay green (ripple safe).
- `cardOutstanding` signed math: purchase (EXPENSE) raises it, payment (TRANSFER into card) lowers it; **paying a card bill does NOT change monthly spend** while it DOES reduce outstanding.
- `CardSummaryCalculator.nextDueDate` passes the pinned edge cases (dueDay>statementDay same-month, before-close prior-cycle/Feb, dueDay≤statementDay next-month, year rollover, short-month + leap clamp).
- `card_summary` read tool returns outstanding/available/next-due for a card by name.
- `create_account` creates a CARD with limits via the committer; M4-2 write tests stay green.
- Full agent+data suite green.

## Next slices (separate plans)

- **M4-5:** `SpeechToText` expect/actual (Android fail-closed + iOS) + permissions.
- **M4-6:** `AgentScreen` + review card + `AgentViewModel` + DI (all 3 actuals) wiring `AgentLoop` → `ConversationRepository` → `WriteBatchCommitter`; **card UI (AccountsScreen creation inputs + card detail)** lands here; rewire `EntryScreen` transfer button onto `transferBlocking`.
- **M4-7:** distinct agent consent + gate-state messages + `LlmError` UI. **M4-0:** iOS Xcode wrapper.
- Deferred write tools (`set_budget`/`recategorize`/`add_split_transaction`); Claude/OpenAI `complete()` parity.
