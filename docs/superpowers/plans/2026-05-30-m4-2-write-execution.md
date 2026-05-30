# M4-2 — Write Tools + WriteBatchCommitter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the agent's proposed writes (the `List<ProposedWrite>` M4-1's `AgentLoop` already collects in `final`) into a real, all-or-nothing ledger commit via a single `WriteBatchCommitter`, backed by new transaction-safe blocking primitives.

**Architecture:** The loop never touches the DB — write tools remain inert `ProposedWrite` *values* (M4-1). At Apply time a `WriteBatchCommitter` owns ONE `db.transaction { }`, parses each `ProposedWrite` by tool name, resolves entity references by name (creating accounts/categories/persons first), and inserts in FK order: **account → category → person → (txn | lend_borrow | transfer pair)**. New non-suspending primitives (`transferBlocking`, `recordBlocking`, blocking person/category create) make every step callable inside the synchronous `db.transaction` block. No-write-in-loop is structurally enforced because `AgentLoop` has no `db` and the committer is only constructed at Apply.

**Tech Stack:** Kotlin Multiplatform, SQLDelight 2 + SQLCipher (FK-enforced), kotlinx.serialization (`LlmJson.json`), kotlin.test + kotlinx-coroutines-test.

**Scope of this sub-slice (M4-2):** `transfer_group_id` column + ripple; `TxnSource.CHAT` + tolerant mapper; `transferBlocking`; `recordBlocking` + blocking person/category create + person name lookup; `ProposedWrite` arg parsing; `WriteBatchCommitter` for `create_account`, `create_category`, `add_transaction`, `record_lend_borrow`, `transfer`, `record_card_payment` (transfer-into-CARD alias); write-tool descriptors registered; atomic-apply + rollback + no-write-in-loop tests.

**Explicitly OUT of scope (later slices / fast-follow):**
- Agent tables (`agent_conversation`/`agent_message`) + `ConversationRepository` + `applied_summary` persistence → **M4-3** (`5.sqm`).
- Card metadata columns (`credit_limit`/`statement_day`/`due_day`), `cardOutstanding`/`CardSummary`, card UI → **M4-4** (`6.sqm`). (A card *payment* here is just a TRANSFER into an `AccountKind.CARD` account — it needs no card columns.)
- Write tools `set_budget`, `recategorize`, `add_split_transaction` → fast-follow (mechanical adds once the committer framework exists; tracked, not built here).
- `AgentScreen` / review-card UI / `AgentViewModel` / `EntryScreen` transfer-button rewire → **M4-6**.

**Migration numbering note:** the M4 spec §11 bundled `4.sqm` = agent tables + `transfer_group_id` + card columns. We split by slice: **M4-2 → `4.sqm` (`transfer_group_id` only)**, M4-3 → `5.sqm` (agent tables), M4-4 → `6.sqm` (card columns). Nothing is shipped, so renumbering is safe.

---

## Grounded codebase facts (verified — rely on these)

- `txn` columns today (final schema): `id, account_id, amount, currency, ts, merchant_id, category_id, source, notes, kind, parent_txn_id, capture_id`. `kind`/`parent_txn_id` added in `2.sqm`, `capture_id` in `3.sqm`. Next migration = `4.sqm`.
- `TransactionQueries.sq` `insertTxn` lists those 12 columns. `observeRecentTopLevel` is an explicit projection of `txn.id, account_id, amount, currency, ts, merchant_id, category_id, source, notes, kind, parent_txn_id, capture_id` (→ generated type `app.hisaab.db.ObserveRecentTopLevel`). `getTxn`/`observeTxnById`/`observeTxnsForDayRange` are `SELECT *` → generated type `migrations.Txn` (maps by column name, so an appended column is picked up automatically once the mapper reads it).
- `TransactionRepository(db, merchantRepo, tagRepo)`: `add(NewTransaction)` (suspend), `addBlocking(NewTransaction)` (sync, used inside `db.transaction`), `addSplits(parentId, children)`. All three call `db.transactionQueriesQueries.insertTxn(... parent_txn_id=..., capture_id=...)`. Two private mappers: `ObserveRecentTopLevel.toDomain()` and `migrations.Txn.toFullDomain()`.
- `NewTransaction(accountId, amount, currency="BDT", ts, merchantName, categoryId, source=TxnSource.MANUAL, notes, kind, tagNames=emptyList(), captureId=null)`.
- `TxnSource { SMS, EMAIL, VOICE, MANUAL, OCR, RECURRING }` (no `CHAT`). Mappers do `TxnSource.valueOf(source)` (throws on unknown).
- `TransactionRow(... source: TxnSource, ..., parentTxnId: String?, captureId: String? = null)`.
- `LendBorrowRepository(db, txnRepo)`: `record(NewLendBorrow): Pair<String,String>` (suspend) inserts lend_borrow → `txnRepo.add(...)` → `linkLendBorrowTxn`. Categories used: `"lend"`/`"borrow"`.
- `NewLendBorrow(personId, amount, direction: LendBorrowDirection{LENT,BORROWED}, accountId, purpose, ts, dueDate)`. `LendBorrowStatus{OPEN,SETTLED,PARTIAL}`.
- `LendBorrowQueries.sq`: `insertLendBorrow(id, person_id, amount, direction, purpose, ts, due_date, status)`; `linkLendBorrowTxn(lend_borrow_id, txn_id)` (INSERT OR IGNORE).
- `PersonRepository(db)`: `addManual(name): String` (suspend; just `insertPerson(id, name, contact_ref=null)`), `upsertFromContact`, `observeAll()`. **No name-match lookup.** `PersonQueries.sq` has `insertPerson`, `observeAllPersons`, `findPersonByPhone` — **no `findPersonByName`**.
- `CategoryRepository(db)`: `add(name, color, icon, parentId): String` (suspend); `Category(id, name, parentId, color, icon, isDefault)`; `observeAll()`.
- `AccountRepository(db)`: `add(name, kind, institution, currency="BDT")` (suspend) + `addBlocking(...)` (sync); `Account(id, name, kind: AccountKind, ...)`; `AccountKind{CASH,BANK,MFS,CARD,GOAL}`; accessor `db.accountQueriesQueries`.
- `db.transaction { ... }` is a **synchronous** block (see `CapturePipeline.kt:245`). Everything inside must be blocking (no `suspend` calls). The query accessor names follow `db.<file>QueriesQueries` (e.g. `db.transactionQueriesQueries`, `db.lendBorrowQueriesQueries`, `db.personQueriesQueries`, `db.categoryQueriesQueries`, `db.accountQueriesQueries`).
- From M4-1: `ProposedWrite(val tool: String, val args: JsonObject)` (in `app.hisaab.agent.Tools`); `ToolRegistry(readTools, writeDescriptors)`; `WriteDescriptor(name, description, paramsDoc)`; `AgentLoop` returns `AgentTurnResult(finalMessage, proposedWrites, cappedOut, updatedHistory)`. `TestDatabase.create()` (in `app.hisaab.data.support`) enables `PRAGMA foreign_keys = ON`.

---

## File Structure

- Create `composeApp/src/commonMain/sqldelight/migrations/4.sqm` — `transfer_group_id` column + index.
- Modify `composeApp/src/commonMain/sqldelight/app/hisaab/db/TransactionQueries.sq` — `insertTxn` (+`transfer_group_id`), `observeRecentTopLevel` (+ projection).
- Modify `composeApp/src/commonMain/kotlin/app/hisaab/domain/Transaction.kt` — `TxnSource.CHAT`; `TransactionRow.transferGroupId`.
- Modify `composeApp/src/commonMain/kotlin/app/hisaab/data/TransactionRepository.kt` — `insertTxn` callers (+`transfer_group_id`), both mappers (+ tolerant `TxnSource`, + `transferGroupId`), new `transferBlocking`.
- Modify `composeApp/src/commonMain/sqldelight/app/hisaab/db/PersonQueries.sq` — `findPersonByName`.
- Modify `composeApp/src/commonMain/kotlin/app/hisaab/data/PersonRepository.kt` — `findByNameBlocking`, `addManualBlocking`.
- Modify `composeApp/src/commonMain/kotlin/app/hisaab/data/CategoryRepository.kt` — `addBlocking`.
- Modify `composeApp/src/commonMain/kotlin/app/hisaab/data/LendBorrowRepository.kt` — `recordBlocking`.
- Create `composeApp/src/commonMain/kotlin/app/hisaab/agent/WriteTools.kt` — `WriteDescriptor`s for the 6 tools + `agentWriteDescriptors()` + `WriteIntent` parser.
- Create `composeApp/src/commonMain/kotlin/app/hisaab/agent/WriteBatchCommitter.kt` — the committer + `AppliedSummary`.
- Tests: `composeApp/src/commonTest/kotlin/app/hisaab/data/TransferGroupColumnTest.kt`, `TxnSourceMapperTest.kt`, `TransferBlockingTest.kt`, `LendBorrowBlockingTest.kt`, `composeApp/src/commonTest/kotlin/app/hisaab/agent/WriteToolsTest.kt`, `WriteBatchCommitterTest.kt`, `WriteBatchAtomicTest.kt`, `NoWriteInLoopTest.kt`.

---

## Task 1: `transfer_group_id` column + ripple

**Files:**
- Create: `composeApp/src/commonMain/sqldelight/migrations/4.sqm`
- Modify: `composeApp/src/commonMain/sqldelight/app/hisaab/db/TransactionQueries.sq`
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/domain/Transaction.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/data/TransactionRepository.kt`
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/data/TransferGroupColumnTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// TransferGroupColumnTest.kt
package app.hisaab.data
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TxnKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

class TransferGroupColumnTest {
    @Test fun `recent txns expose a nullable transferGroupId`() = runTest {
        val db = TestDatabase.create()
        val accounts = AccountRepository(db)
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val cash = accounts.add("Cash", AccountKind.CASH, null)
        txns.add(NewTransaction(accountId = cash, amount = 100.0, ts = 1L, merchantName = null,
            categoryId = null, notes = null, kind = TxnKind.EXPENSE))
        val row = txns.observeRecent(10).first().single()
        assertNull(row.transferGroupId)   // plain txns have no transfer group
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.TransferGroupColumnTest" --console=plain`
Expected: FAIL — `transferGroupId` unresolved on `TransactionRow`.

- [ ] **Step 3: Add the migration** — create `composeApp/src/commonMain/sqldelight/migrations/4.sqm`:

```sql
-- M4-2: transfer ledger shape. A transfer is two txn rows sharing a transfer_group_id
-- (debit leg on the source account, credit leg on the destination), both kind='TRANSFER'
-- and parent_txn_id IS NULL. Reserved separately from parent_txn_id (which is for splits).
ALTER TABLE txn ADD COLUMN transfer_group_id TEXT;
CREATE INDEX idx_txn_transfer_group ON txn(transfer_group_id);
```

- [ ] **Step 4: Thread the column through `TransactionQueries.sq`**

Update `insertTxn` to include the new column (append it last to match ALTER order):

```sql
insertTxn:
INSERT INTO txn(id, account_id, amount, currency, ts, merchant_id, category_id, source, notes, kind, parent_txn_id, capture_id, transfer_group_id)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
```

Update `observeRecentTopLevel` projection to select it:

```sql
observeRecentTopLevel:
SELECT txn.id, txn.account_id, txn.amount, txn.currency, txn.ts, txn.merchant_id,
       txn.category_id, txn.source, txn.notes, txn.kind, txn.parent_txn_id, txn.capture_id,
       txn.transfer_group_id
FROM txn
WHERE txn.parent_txn_id IS NULL
ORDER BY txn.ts DESC
LIMIT ?;
```

- [ ] **Step 5: Add the domain field** — in `Transaction.kt`, add to `TransactionRow` (after `captureId`):

```kotlin
    val captureId: String? = null,
    val transferGroupId: String? = null,
```

- [ ] **Step 6: Update `TransactionRepository.kt`**

(a) Every `insertTxn(...)` call (`add`, `addBlocking`, `addSplits`) gains a trailing `transfer_group_id = null` argument. Example for `add`:

```kotlin
        db.transactionQueriesQueries.insertTxn(
            id = id,
            account_id = input.accountId,
            amount = input.amount,
            currency = input.currency,
            ts = input.ts,
            merchant_id = merchantId,
            category_id = input.categoryId,
            source = input.source.name,
            notes = input.notes,
            kind = input.kind.name,
            parent_txn_id = null,
            capture_id = input.captureId,
            transfer_group_id = null,
        )
```

Apply the same trailing `transfer_group_id = null` to the `addBlocking` and `addSplits` `insertTxn` calls.

(b) Both mappers gain the new field. In `ObserveRecentTopLevel.toDomain()` and `migrations.Txn.toFullDomain()` add (after `captureId = capture_id,`):

```kotlin
        captureId = capture_id,
        transferGroupId = transfer_group_id,
```

- [ ] **Step 7: Run the test to confirm it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.TransferGroupColumnTest" --tests "app.hisaab.data.TransactionRepositoryTest" --tests "app.hisaab.data.AccountBalanceTest" --console=plain`
Expected: PASS (new test + the existing txn/balance tests still green — confirms the ripple didn't break callers).

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/sqldelight/migrations/4.sqm composeApp/src/commonMain/sqldelight/app/hisaab/db/TransactionQueries.sq composeApp/src/commonMain/kotlin/app/hisaab/domain/Transaction.kt composeApp/src/commonMain/kotlin/app/hisaab/data/TransactionRepository.kt composeApp/src/commonTest/kotlin/app/hisaab/data/TransferGroupColumnTest.kt
git commit -m "feat(data): add txn.transfer_group_id column + thread through insert/mappers"
```

---

## Task 2: `TxnSource.CHAT` + tolerant source mapper

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/domain/Transaction.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/data/TransactionRepository.kt`
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/data/TxnSourceMapperTest.kt`

> The agent posts txns with `source = CHAT` (typed) or `VOICE` (spoken). `CHAT` is new. The mappers call `TxnSource.valueOf(source)` which throws on unknown values — so a tolerant fallback (`unknown → MANUAL`) future-proofs the column (free `TEXT`, no migration).

- [ ] **Step 1: Write the failing test**

```kotlin
// TxnSourceMapperTest.kt
package app.hisaab.data
import app.hisaab.domain.TxnSource
import kotlin.test.Test
import kotlin.test.assertEquals

class TxnSourceMapperTest {
    @Test fun `CHAT is a known source`() {
        assertEquals(TxnSource.CHAT, TxnSource.valueOf("CHAT"))
    }
    @Test fun `unknown source falls back to MANUAL`() {
        assertEquals(TxnSource.MANUAL, txnSourceOrManual("SOME_FUTURE_SOURCE"))
    }
    @Test fun `known source parses normally`() {
        assertEquals(TxnSource.VOICE, txnSourceOrManual("VOICE"))
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.TxnSourceMapperTest" --console=plain`
Expected: FAIL — `TxnSource.CHAT` and `txnSourceOrManual` unresolved.

- [ ] **Step 3: Add the enum value** — in `Transaction.kt`:

```kotlin
enum class TxnSource { SMS, EMAIL, VOICE, MANUAL, OCR, RECURRING, CHAT }
```

- [ ] **Step 4: Add the tolerant mapper helper + use it**

In `TransactionRepository.kt`, add a file-private top-level helper (above the class):

```kotlin
/** Tolerant source decode: unknown stored values (e.g. a source written by a newer build) map to MANUAL. */
internal fun txnSourceOrManual(raw: String): TxnSource =
    runCatching { TxnSource.valueOf(raw) }.getOrDefault(TxnSource.MANUAL)
```

Then replace `TxnSource.valueOf(source)` with `txnSourceOrManual(source)` in BOTH mappers (`ObserveRecentTopLevel.toDomain()` and `migrations.Txn.toFullDomain()`).

- [ ] **Step 5: Run the test to confirm it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.TxnSourceMapperTest" --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/domain/Transaction.kt composeApp/src/commonMain/kotlin/app/hisaab/data/TransactionRepository.kt composeApp/src/commonTest/kotlin/app/hisaab/data/TxnSourceMapperTest.kt
git commit -m "feat(data): add TxnSource.CHAT + tolerant source mapper (unknown -> MANUAL)"
```

---

## Task 3: `TransactionRepository.transferBlocking` (paired-leg insert)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/data/TransactionRepository.kt`
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/data/TransferBlockingTest.kt`

> A transfer = two `TRANSFER` rows in ONE caller-owned `db.transaction`, sharing a `transfer_group_id`: a leg on `from` and a leg on `to`. Both `parent_txn_id IS NULL`. Non-suspending so the `WriteBatchCommitter` can call it inside its transaction.

- [ ] **Step 1: Write the failing test**

```kotlin
// TransferBlockingTest.kt
package app.hisaab.data
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.TxnKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransferBlockingTest {
    @Test fun `transfer inserts two TRANSFER legs sharing a group id`() = runTest {
        val db = TestDatabase.create()
        val accounts = AccountRepository(db)
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val bank = accounts.add("Bank", AccountKind.BANK, null)
        val cash = accounts.add("Cash", AccountKind.CASH, null)
        lateinit var groupId: String
        db.transaction { groupId = txns.transferBlocking(bank, cash, 500.0, ts = 10L, notes = null) }
        val rows = txns.observeRecent(10).first()
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.kind == TxnKind.TRANSFER })
        assertTrue(rows.all { it.transferGroupId == groupId })
        assertNotNull(rows.firstOrNull { it.accountId == bank })
        assertNotNull(rows.firstOrNull { it.accountId == cash })
    }
}
```

> `db.transaction { }` is the synchronous transaction block used throughout the codebase (e.g. `CapturePipeline.kt`). `transferBlocking` itself does no transaction management — it is meant to run inside one.

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.TransferBlockingTest" --console=plain`
Expected: FAIL — `transferBlocking` unresolved.

- [ ] **Step 3: Implement `transferBlocking`** — add to `TransactionRepository`:

```kotlin
/**
 * Inserts a transfer as two TRANSFER legs (debit on [fromAccountId], credit on [toAccountId])
 * sharing a generated transfer_group_id. NON-suspending: MUST be called inside a db.transaction { }
 * (e.g. by WriteBatchCommitter). Returns the shared transfer_group_id.
 */
fun transferBlocking(
    fromAccountId: String,
    toAccountId: String,
    amount: Double,
    ts: Long,
    notes: String?,
    currency: String = "BDT",
): String {
    val groupId = randomId()
    fun leg(accountId: String) = db.transactionQueriesQueries.insertTxn(
        id = randomId(),
        account_id = accountId,
        amount = amount,
        currency = currency,
        ts = ts,
        merchant_id = null,
        category_id = "transfer",
        source = TxnSource.CHAT.name,
        notes = notes,
        kind = TxnKind.TRANSFER.name,
        parent_txn_id = null,
        capture_id = null,
        transfer_group_id = groupId,
    )
    leg(fromAccountId)
    leg(toAccountId)
    return groupId
}
```

> `category_id = "transfer"` matches the existing convention (`LendBorrowRepository.settle` uses the seeded `"transfer"` category id). `source = CHAT` because transfers in this slice originate from the agent; the UI rewire (M4-6) can pass a source later if needed.

- [ ] **Step 4: Run the test to confirm it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.TransferBlockingTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/data/TransactionRepository.kt composeApp/src/commonTest/kotlin/app/hisaab/data/TransferBlockingTest.kt
git commit -m "feat(data): transferBlocking — paired TRANSFER legs sharing transfer_group_id"
```

---

## Task 4: Blocking lend/borrow + person/category create + person name lookup

**Files:**
- Modify: `composeApp/src/commonMain/sqldelight/app/hisaab/db/PersonQueries.sq`
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/data/PersonRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/data/CategoryRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/data/LendBorrowRepository.kt`
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/data/LendBorrowBlockingTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// LendBorrowBlockingTest.kt
package app.hisaab.data
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.LendBorrowDirection
import app.hisaab.domain.NewLendBorrow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LendBorrowBlockingTest {
    @Test fun `recordBlocking creates lend_borrow + linked txn inside a transaction`() = runTest {
        val db = TestDatabase.create()
        val accounts = AccountRepository(db)
        val persons = PersonRepository(db)
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val lendBorrow = LendBorrowRepository(db, txns)
        val cash = accounts.add("Cash", AccountKind.CASH, null)
        val karim = persons.addManual("Karim")
        db.transaction {
            lendBorrow.recordBlocking(
                NewLendBorrow(personId = karim, amount = 2000.0, direction = LendBorrowDirection.LENT,
                    accountId = cash, purpose = "loan", ts = 5L, dueDate = null),
            )
        }
        // The person's open balance reflects the lent amount.
        val person = persons.observeAll().first().single { it.person.id == karim }
        assertEquals(2000.0, person.balance)
    }

    @Test fun `findByNameBlocking matches case-insensitively`() = runTest {
        val db = TestDatabase.create()
        val persons = PersonRepository(db)
        val karim = persons.addManual("Karim")
        assertEquals(karim, persons.findByNameBlocking("karim"))
        assertEquals(null, persons.findByNameBlocking("nobody"))
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.LendBorrowBlockingTest" --console=plain`
Expected: FAIL — `recordBlocking` / `findByNameBlocking` unresolved.

- [ ] **Step 3: Add the person name query** — append to `PersonQueries.sq`:

```sql
findPersonByName:
SELECT * FROM person WHERE LOWER(name) = LOWER(?) LIMIT 1;
```

- [ ] **Step 4: Add blocking person methods** — in `PersonRepository`:

```kotlin
/** Case-insensitive exact name lookup; null if no person matches. Non-suspending (for committer use). */
fun findByNameBlocking(name: String): String? =
    db.personQueriesQueries.findPersonByName(name).executeAsOneOrNull()?.id

/** Non-suspending person insert (for committer use inside a db.transaction). Returns the new id. */
fun addManualBlocking(name: String): String {
    val id = randomId()
    db.personQueriesQueries.insertPerson(id = id, name = name, contact_ref = null)
    return id
}
```

- [ ] **Step 5: Add blocking category create** — in `CategoryRepository` (mirror its existing suspend `add`, which uses `db.insightQueriesQueries.insertCategoryIfMissing` — NOT a `categoryQueriesQueries` accessor):

```kotlin
/** Non-suspending category insert (for committer use inside a db.transaction). Returns the new id. */
fun addBlocking(name: String, color: String?, icon: String?, parentId: String?): String {
    val id = randomId()
    db.insightQueriesQueries.insertCategoryIfMissing(
        id = id,
        name = name,
        parent_id = parentId,
        color = color,
        icon = icon,
        is_default = 0L,
    )
    return id
}
```

> This is the exact call the suspend `add` makes (verified): the query is `insertCategoryIfMissing` on `db.insightQueriesQueries`. Reuse the file's existing private `randomId()` helper.

- [ ] **Step 6: Add `recordBlocking`** — in `LendBorrowRepository` (mirror `record`, but `addBlocking` + no suspend):

```kotlin
/**
 * Non-suspending lend/borrow record (for WriteBatchCommitter, inside a db.transaction).
 * Person must already exist (resolve before the transaction). Returns Pair(lendBorrowId, txnId).
 */
fun recordBlocking(input: NewLendBorrow): Pair<String, String> {
    val lendBorrowId = randomId()
    val txnKind = if (input.direction == LendBorrowDirection.LENT) TxnKind.LEND else TxnKind.BORROW
    val categoryId = if (input.direction == LendBorrowDirection.LENT) "lend" else "borrow"
    db.lendBorrowQueriesQueries.insertLendBorrow(
        id = lendBorrowId,
        person_id = input.personId,
        amount = input.amount,
        direction = input.direction.name,
        purpose = input.purpose,
        ts = input.ts,
        due_date = input.dueDate,
        status = LendBorrowStatus.OPEN.name,
    )
    val txnId = txnRepo.addBlocking(
        NewTransaction(
            accountId = input.accountId,
            amount = input.amount,
            ts = input.ts,
            merchantName = null,
            categoryId = categoryId,
            notes = input.purpose,
            kind = txnKind,
            source = TxnSource.CHAT,
        ),
    )
    db.lendBorrowQueriesQueries.linkLendBorrowTxn(lend_borrow_id = lendBorrowId, txn_id = txnId)
    return lendBorrowId to txnId
}
```

> Add `import app.hisaab.domain.TxnSource` to `LendBorrowRepository.kt` if not already present (it sets `source = TxnSource.CHAT`). Reuse the file's existing `randomId()` helper.

- [ ] **Step 7: Run the test to confirm it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.LendBorrowBlockingTest" --console=plain`
Expected: PASS (2 tests).

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/sqldelight/app/hisaab/db/PersonQueries.sq composeApp/src/commonMain/kotlin/app/hisaab/data/PersonRepository.kt composeApp/src/commonMain/kotlin/app/hisaab/data/CategoryRepository.kt composeApp/src/commonMain/kotlin/app/hisaab/data/LendBorrowRepository.kt composeApp/src/commonTest/kotlin/app/hisaab/data/LendBorrowBlockingTest.kt
git commit -m "feat(data): blocking lend/borrow + person/category create + findPersonByName"
```

---

## Task 5: Write-tool descriptors + arg parsing (`WriteTools.kt`)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/agent/WriteTools.kt`
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/agent/WriteToolsTest.kt`

> The model proposes writes as `ProposedWrite(tool, args: JsonObject)`. This task defines the catalog (`WriteDescriptor`s the prompt advertises) and a typed parser per tool. The committer (Task 6) consumes the parsed intents. Parsing here is pure and unit-testable without a DB.

- [ ] **Step 1: Write the failing test**

```kotlin
// WriteToolsTest.kt
package app.hisaab.agent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class WriteToolsTest {
    @Test fun `descriptors advertise the six write tools`() {
        val names = agentWriteDescriptors().map { it.name }.toSet()
        assertTrue(names.containsAll(setOf(
            "create_account", "create_category", "add_transaction",
            "record_lend_borrow", "transfer", "record_card_payment")))
    }
    @Test fun `parses add_transaction args`() {
        val w = ProposedWrite("add_transaction", buildJsonObject {
            put("account", "Cash"); put("amount", 500.0); put("kind", "EXPENSE")
            put("category", "Food"); put("notes", "lunch")
        })
        val intent = WriteIntent.parse(w)
        assertTrue(intent is WriteIntent.AddTransaction)
        intent as WriteIntent.AddTransaction
        assertEquals("Cash", intent.account); assertEquals(500.0, intent.amount)
        assertEquals("EXPENSE", intent.kind); assertEquals("Food", intent.category)
    }
    @Test fun `parses transfer args`() {
        val w = ProposedWrite("transfer", buildJsonObject {
            put("fromAccount", "Bank"); put("toAccount", "Cash"); put("amount", 1000.0)
        })
        assertTrue(WriteIntent.parse(w) is WriteIntent.Transfer)
    }
    @Test fun `unknown tool parses to null`() {
        assertNull(WriteIntent.parse(ProposedWrite("frobnicate", buildJsonObject {})))
    }
    @Test fun `missing required field parses to null`() {
        // add_transaction without amount
        assertNull(WriteIntent.parse(ProposedWrite("add_transaction", buildJsonObject { put("account", "Cash") })))
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.agent.WriteToolsTest" --console=plain`
Expected: FAIL — `agentWriteDescriptors` / `WriteIntent` unresolved.

- [ ] **Step 3: Implement `WriteTools.kt`**

```kotlin
// WriteTools.kt
package app.hisaab.agent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content
private fun JsonObject.dbl(key: String): Double? =
    (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }
private fun JsonObject.lng(key: String): Long? = (this[key] as? JsonPrimitive)?.content?.toLongOrNull()

/** Parsed, typed form of a ProposedWrite. The committer consumes these; the loop never does. */
sealed interface WriteIntent {
    data class CreateAccount(val name: String, val kind: String) : WriteIntent
    data class CreateCategory(val name: String, val parent: String?) : WriteIntent
    data class AddTransaction(
        val account: String, val amount: Double, val kind: String,
        val category: String?, val merchant: String?, val notes: String?, val ts: Long?,
    ) : WriteIntent
    data class RecordLendBorrow(
        val person: String, val amount: Double, val kind: String, val fromAccount: String, val purpose: String?,
    ) : WriteIntent
    data class Transfer(val fromAccount: String, val toAccount: String, val amount: Double, val notes: String?) : WriteIntent
    data class CardPayment(val fromAccount: String, val card: String, val amount: Double) : WriteIntent

    companion object {
        /** Returns null for unknown tools or missing required fields (committer skips/reports null). */
        fun parse(w: ProposedWrite): WriteIntent? {
            val a = w.args
            return when (w.tool) {
                "create_account" -> a.str("name")?.let { CreateAccount(it, a.str("kind") ?: "CASH") }
                "create_category" -> a.str("name")?.let { CreateCategory(it, a.str("parent")) }
                "add_transaction" -> {
                    val account = a.str("account"); val amount = a.dbl("amount")
                    if (account != null && amount != null)
                        AddTransaction(account, amount, a.str("kind") ?: "EXPENSE",
                            a.str("category"), a.str("merchant"), a.str("notes"), a.lng("ts"))
                    else null
                }
                "record_lend_borrow" -> {
                    val person = a.str("person"); val amount = a.dbl("amount"); val from = a.str("fromAccount")
                    if (person != null && amount != null && from != null)
                        RecordLendBorrow(person, amount, a.str("kind") ?: "LENT", from, a.str("purpose"))
                    else null
                }
                "transfer" -> {
                    val from = a.str("fromAccount"); val to = a.str("toAccount"); val amount = a.dbl("amount")
                    if (from != null && to != null && amount != null) Transfer(from, to, amount, a.str("notes")) else null
                }
                "record_card_payment" -> {
                    val from = a.str("fromAccount"); val card = a.str("card"); val amount = a.dbl("amount")
                    if (from != null && card != null && amount != null) CardPayment(from, card, amount) else null
                }
                else -> null
            }
        }
    }
}

/** Prompt-facing catalog of the write tools this slice supports. */
fun agentWriteDescriptors(): List<WriteDescriptor> = listOf(
    WriteDescriptor("create_account", "Create a new account", "{ \"name\": string, \"kind\": \"CASH|BANK|MFS|CARD\" }"),
    WriteDescriptor("create_category", "Create a spending category", "{ \"name\": string, \"parent\": string? }"),
    WriteDescriptor("add_transaction", "Record an expense or income",
        "{ \"account\": string, \"amount\": number, \"kind\": \"EXPENSE|INCOME\", \"category\": string?, \"merchant\": string?, \"notes\": string? }"),
    WriteDescriptor("record_lend_borrow", "Record money lent to / borrowed from a person",
        "{ \"person\": string, \"amount\": number, \"kind\": \"LENT|BORROWED\", \"fromAccount\": string, \"purpose\": string? }"),
    WriteDescriptor("transfer", "Move money between two accounts",
        "{ \"fromAccount\": string, \"toAccount\": string, \"amount\": number }"),
    WriteDescriptor("record_card_payment", "Pay a credit-card bill from an account",
        "{ \"fromAccount\": string, \"card\": string, \"amount\": number }"),
)
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.agent.WriteToolsTest" --console=plain`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/agent/WriteTools.kt composeApp/src/commonTest/kotlin/app/hisaab/agent/WriteToolsTest.kt
git commit -m "feat(agent): write-tool descriptors + typed WriteIntent arg parser"
```

---

## Task 6: `WriteBatchCommitter` (FK-ordered atomic apply)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/agent/WriteBatchCommitter.kt`
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/agent/WriteBatchCommitterTest.kt`

> The sole Apply writer. Owns ONE `db.transaction { }`. Order: create accounts → create categories → resolve/create persons → txn/lend_borrow/transfer/card-payment. References to accounts/categories are by NAME, resolved against a map of (existing + just-created) names so a `create_account` in the same batch can be referenced by a later `add_transaction`. Card payment = `transferBlocking` into the named CARD account.

- [ ] **Step 1: Write the failing test (happy path; the mixed-batch + rollback live in Task 7)**

```kotlin
// WriteBatchCommitterTest.kt
package app.hisaab.agent
import app.hisaab.data.AccountRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.db.HisaabDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class WriteBatchCommitterTest {
    private fun committer(db: HisaabDatabase): WriteBatchCommitter {
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        return WriteBatchCommitter(db, AccountRepository(db), CategoryRepository(db),
            PersonRepository(db), txns, LendBorrowRepository(db, txns))
    }

    @Test fun `applies a create_account then add_transaction referencing it by name`() = runTest {
        val db = TestDatabase.create()
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val summary = committer(db).apply(listOf(
            ProposedWrite("create_account", buildJsonObject { put("name", "Wallet"); put("kind", "CASH") }),
            ProposedWrite("add_transaction", buildJsonObject {
                put("account", "Wallet"); put("amount", 250.0); put("kind", "EXPENSE"); put("notes", "snacks")
            }),
        ))
        assertEquals(1, summary.accountsCreated)
        assertEquals(1, summary.transactionsAdded)
        val row = txns.observeRecent(10).first().single()
        assertEquals(250.0, row.amount)
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.agent.WriteBatchCommitterTest" --console=plain`
Expected: FAIL — `WriteBatchCommitter` unresolved.

- [ ] **Step 3: Implement `WriteBatchCommitter.kt`**

```kotlin
// WriteBatchCommitter.kt
package app.hisaab.agent
import app.hisaab.data.AccountRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.AccountKind
import app.hisaab.domain.LendBorrowDirection
import app.hisaab.domain.NewLendBorrow
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TxnKind
import app.hisaab.domain.TxnSource
import kotlinx.coroutines.flow.first

/** What actually committed (basis for §11 applied_summary persistence in M4-3). */
data class AppliedSummary(
    val accountsCreated: Int = 0,
    val categoriesCreated: Int = 0,
    val transactionsAdded: Int = 0,
    val lendBorrowsRecorded: Int = 0,
    val transfers: Int = 0,
)

/**
 * Sole Apply writer for an agent turn. Commits a batch of ProposedWrites in ONE db.transaction,
 * FK-ordered (accounts → categories → persons → txn/lend_borrow/transfer). Entity references are by
 * name, resolved against existing rows plus rows created earlier in the same batch. All-or-nothing:
 * any failure inside the transaction rolls the whole batch back.
 */
class WriteBatchCommitter(
    private val db: HisaabDatabase,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val persons: PersonRepository,
    private val txns: TransactionRepository,
    private val lendBorrow: LendBorrowRepository,
) {
    /** @throws IllegalArgumentException if a referenced account cannot be resolved (rolls back). */
    suspend fun apply(writes: List<ProposedWrite>): AppliedSummary {
        // Snapshot existing names BEFORE the transaction (reads are fine outside it).
        val accountIdByName = accounts.observeActive().first()
            .associate { it.name.lowercase() to it.id }.toMutableMap()
        val categoryIdByName = categories.observeAll().first()
            .associate { it.name.lowercase() to it.id }.toMutableMap()

        val intents = writes.mapNotNull { WriteIntent.parse(it) }
        var s = AppliedSummary()

        db.transaction {
            // 1. accounts
            intents.filterIsInstance<WriteIntent.CreateAccount>().forEach { i ->
                val kind = runCatching { AccountKind.valueOf(i.kind) }.getOrDefault(AccountKind.CASH)
                val id = accounts.addBlocking(i.name, kind, institution = null)
                accountIdByName[i.name.lowercase()] = id
                s = s.copy(accountsCreated = s.accountsCreated + 1)
            }
            // 2. categories
            intents.filterIsInstance<WriteIntent.CreateCategory>().forEach { i ->
                val parentId = i.parent?.let { categoryIdByName[it.lowercase()] }
                val id = categories.addBlocking(i.name, color = null, icon = null, parentId = parentId)
                categoryIdByName[i.name.lowercase()] = id
                s = s.copy(categoriesCreated = s.categoriesCreated + 1)
            }
            fun acct(name: String): String = accountIdByName[name.lowercase()]
                ?: throw IllegalArgumentException("unknown account '$name'")
            fun cat(name: String?): String? = name?.let { categoryIdByName[it.lowercase()] }
            // 3. txn / lend_borrow / transfer / card payment
            intents.forEach { i ->
                when (i) {
                    is WriteIntent.AddTransaction -> {
                        txns.addBlocking(NewTransaction(
                            accountId = acct(i.account), amount = i.amount, ts = i.ts ?: 0L,
                            merchantName = i.merchant, categoryId = cat(i.category), notes = i.notes,
                            kind = runCatching { TxnKind.valueOf(i.kind) }.getOrDefault(TxnKind.EXPENSE),
                            source = TxnSource.CHAT,
                        ))
                        s = s.copy(transactionsAdded = s.transactionsAdded + 1)
                    }
                    is WriteIntent.Transfer -> {
                        txns.transferBlocking(acct(i.fromAccount), acct(i.toAccount), i.amount, ts = 0L, notes = i.notes)
                        s = s.copy(transfers = s.transfers + 1)
                    }
                    is WriteIntent.CardPayment -> {
                        txns.transferBlocking(acct(i.fromAccount), acct(i.card), i.amount, ts = 0L, notes = "card payment")
                        s = s.copy(transfers = s.transfers + 1)
                    }
                    is WriteIntent.RecordLendBorrow -> {
                        val personId = persons.findByNameBlocking(i.person) ?: persons.addManualBlocking(i.person)
                        val dir = runCatching { LendBorrowDirection.valueOf(i.kind) }.getOrDefault(LendBorrowDirection.LENT)
                        lendBorrow.recordBlocking(NewLendBorrow(
                            personId = personId, amount = i.amount, direction = dir,
                            accountId = acct(i.fromAccount), purpose = i.purpose, ts = 0L, dueDate = null,
                        ))
                        s = s.copy(lendBorrowsRecorded = s.lendBorrowsRecorded + 1)
                    }
                    is WriteIntent.CreateAccount, is WriteIntent.CreateCategory -> { /* already handled above */ }
                }
            }
        }
        return s
    }
}
```

> Note on the `when (i)`: it is exhaustive over the sealed `WriteIntent`. The `CreateAccount`/`CreateCategory` branch is a no-op here (handled in passes 1–2) but must be present for exhaustiveness (no `else`). If the compiler flags the lambda inside `db.transaction { }` capturing `var s` — it won't (the transaction lambda is a normal inline lambda) — but if any closure issue arises, accumulate counts in local `var`s and build the summary after the block.

- [ ] **Step 4: Run the test to confirm it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.agent.WriteBatchCommitterTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/agent/WriteBatchCommitter.kt composeApp/src/commonTest/kotlin/app/hisaab/agent/WriteBatchCommitterTest.kt
git commit -m "feat(agent): WriteBatchCommitter — FK-ordered atomic apply of proposed writes"
```

---

## Task 7: Atomic-apply + rollback + no-write-in-loop integration tests

**Files:**
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/agent/WriteBatchAtomicTest.kt`
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/agent/NoWriteInLoopTest.kt`

> These are the slice's acceptance tests (spec §6, §8, §17). No new production code — they exercise Tasks 1–6.

- [ ] **Step 1: Write the mixed-batch + rollback test**

```kotlin
// WriteBatchAtomicTest.kt
package app.hisaab.agent
import app.hisaab.data.AccountRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.AccountKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WriteBatchAtomicTest {
    private fun committer(db: HisaabDatabase): WriteBatchCommitter {
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        return WriteBatchCommitter(db, AccountRepository(db), CategoryRepository(db),
            PersonRepository(db), txns, LendBorrowRepository(db, txns))
    }

    @Test fun `mixed batch commits all rows together`() = runTest {
        val db = TestDatabase.create()
        val accounts = AccountRepository(db)
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        val bank = accounts.add("Bank", AccountKind.BANK, null)
        accounts.add("VISA", AccountKind.CARD, null)   // a card account (no card columns needed for a payment)
        val summary = committer(db).apply(listOf(
            ProposedWrite("create_account", buildJsonObject { put("name", "Cash"); put("kind", "CASH") }),
            ProposedWrite("add_transaction", buildJsonObject { put("account", "Cash"); put("amount", 300.0); put("kind", "EXPENSE") }),
            ProposedWrite("record_lend_borrow", buildJsonObject {
                put("person", "Karim"); put("amount", 2000.0); put("kind", "LENT"); put("fromAccount", "Cash") }),
            ProposedWrite("record_card_payment", buildJsonObject { put("fromAccount", "Bank"); put("card", "VISA"); put("amount", 1500.0) }),
        ))
        assertEquals(1, summary.accountsCreated)
        assertEquals(1, summary.transactionsAdded)
        assertEquals(1, summary.lendBorrowsRecorded)
        assertEquals(1, summary.transfers)
        // expense(1) + lend leg(1) + transfer pair(2) = 4 top-level rows
        assertEquals(4, txns.observeRecent(20).first().size)
    }

    @Test fun `a failed write rolls the whole batch back`() = runTest {
        val db = TestDatabase.create()
        val txns = TransactionRepository(db, MerchantRepository(db), TagRepository(db))
        // Second write references an account that is never created → resolution throws → rollback.
        assertFailsWith<IllegalArgumentException> {
            committer(db).apply(listOf(
                ProposedWrite("create_account", buildJsonObject { put("name", "Cash"); put("kind", "CASH") }),
                ProposedWrite("add_transaction", buildJsonObject { put("account", "Ghost"); put("amount", 99.0); put("kind", "EXPENSE") }),
            ))
        }
        // Rollback: the "Cash" account from the same batch must NOT persist.
        assertTrue(AccountRepository(db).observeActive().first().none { it.name == "Cash" })
        assertEquals(0, txns.observeRecent(20).first().size)
    }
}
```

- [ ] **Step 2: Write the no-write-in-loop test (structural enforcement, spec §6)**

```kotlin
// NoWriteInLoopTest.kt
package app.hisaab.agent
import app.hisaab.agent.support.FakeAgentProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoWriteInLoopTest {
    @Test fun `agent loop collects proposed writes without touching any database`() = runTest {
        // AgentLoop has NO db dependency: a multi-write turn completes and returns inert ProposedWrites.
        val registry = ToolRegistry(readTools = emptyList(), writeDescriptors = agentWriteDescriptors())
        val provider = FakeAgentProvider(listOf(
            """{"thought":"log both","final":{"message":"done","proposedWrites":[{"tool":"add_transaction","args":{"account":"Cash","amount":500,"kind":"EXPENSE"}},{"tool":"transfer","args":{"fromAccount":"Bank","toAccount":"Cash","amount":1000}}]}}""",
        ))
        val result = AgentLoop(provider, registry, systemPrompt = "SYS", maxIterations = 6)
            .run(history = emptyList(), userMessage = "spent 500 and moved 1000")
        assertEquals("done", result.finalMessage)
        assertEquals(2, result.proposedWrites.size)
        assertTrue(result.proposedWrites.all { it.tool == "add_transaction" || it.tool == "transfer" })
        // No db exists in this test at all — writes are pure values until WriteBatchCommitter runs.
    }
}
```

- [ ] **Step 3: Run both tests to confirm they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.agent.WriteBatchAtomicTest" --tests "app.hisaab.agent.NoWriteInLoopTest" --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 4: Run the whole new-feature suite (regression sweep)**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.agent.*" --tests "app.hisaab.data.*" --console=plain`
Expected: PASS (all M4-1 + M4-2 agent/data tests green).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonTest/kotlin/app/hisaab/agent/WriteBatchAtomicTest.kt composeApp/src/commonTest/kotlin/app/hisaab/agent/NoWriteInLoopTest.kt
git commit -m "test(agent): mixed-batch atomic apply + rollback + no-write-in-loop"
```

---

## Acceptance criteria (M4-2 done)

- `transfer_group_id` column exists; transfers post as two `TRANSFER` legs sharing it; existing txn/balance tests still green (ripple safe).
- `TxnSource.CHAT` exists; the source mapper tolerates unknown values (→ MANUAL).
- `transferBlocking` and `recordBlocking` are non-suspending and work inside a `db.transaction`.
- `WriteBatchCommitter` commits a heterogeneous batch (create_account + expense + lend/borrow + card-payment-as-transfer) **all-or-nothing** under the FK-enforced `TestDatabase`; a forced failure rolls everything back.
- No-write-in-loop is structurally proven: `AgentLoop` (no `db`) returns inert `ProposedWrite`s; the DB is touched only by `WriteBatchCommitter` at Apply.

## Deferred / next

- Write tools `set_budget`, `recategorize`, `add_split_transaction` (mechanical adds onto the committer + descriptors).
- **M4-3:** `5.sqm` agent tables + `ConversationRepository` + `applied_summary` persistence (consumes `AppliedSummary`).
- **M4-4:** `6.sqm` card columns + `cardOutstanding`/`CardSummary` + real card-payment semantics tests (bill payment ≠ spend) + card UI.
- **M4-6:** `AgentScreen`/review-card/`AgentViewModel`/DI; rewire `EntryScreen` single-leg transfer button onto `transferBlocking`.
