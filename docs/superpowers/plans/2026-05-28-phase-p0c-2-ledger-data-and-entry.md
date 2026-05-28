# P0c-2 — Repository Layer + Entry Surface Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Part of P0c**, depends on **P0c-1** being shipped. After P0c-2 the user can record real transactions (Expense / Income / Transfer / Lend / Borrow / Settlement), see them on Today, tap a row for detail, edit, delete, and split.

**Goal of P0c-2:** Build the 10-repository data layer + MainGraph bottom-nav + TodayScreen + the flagship EntryScreen + TransactionDetailScreen + SplitEditorSheet.

**Architecture:** Each repository is constructor-injected by `AppContainer` (extended in Task 16). All write paths go through a repository. ViewModels collect `Flow<...>` from repos via `StateFlow`. Repository tests use `JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)` with `HisaabDatabase.Schema.create(driver)` — no SQLCipher in unit tests since the schema runs the same on plain SQLite.

**Tech Stack:** As P0c-1, plus `androidx.activity:activity-compose` (already in P0a) for `ActivityResultContracts.PickVisualMedia`, and `kotlinx.datetime` (added in P0c-1 Task 7).

**Spec:** [`docs/superpowers/specs/2026-05-28-p0c-ledger-core-design.md`](../specs/2026-05-28-p0c-ledger-core-design.md) §6 (repositories), §8 (Today / Entry / TransactionDetail), §9 (file map).

---

## P0c-2 file map (Tasks 10–19)

| Layer | New | Modified |
|---|---|---|
| commonMain/kotlin/app/hisaab/data/ | 10 repository files (`AccountRepository`, `CategoryRepository`, `MerchantRepository`, `TagRepository`, `TransactionRepository`, `PersonRepository`, `LendBorrowRepository`, `BudgetRepository`, `AttachmentRepository`, `InsightRepository`) | — |
| commonMain/kotlin/app/hisaab/screens/ | `main/MainGraph.kt`, `main/MainViewModel.kt`, `today/TodayViewModel.kt`, `entry/{EntryScreen, EntryViewModel, EntryFormState, MerchantAutocomplete, SplitEditorSheet}.kt`, `transaction/{TransactionDetailScreen, TransactionDetailViewModel}.kt` | `today/TodayScreen.kt` (rewrite), `App.kt` (render MainGraph for Authenticated) |
| commonMain/kotlin/app/hisaab/ | — | `AppContainer.kt` (expose 10 repositories + auto-seed on openDatabase) |
| commonMain/sqldelight/ | — | `LendBorrowQueries.sq` (add `getLendBorrowById`) |
| androidMain/kotlin/app/hisaab/platform/ | — | `ImagePicker.kt` (real `PickVisualMedia` integration — replaces P0c-1 stub) |
| commonTest/kotlin/app/hisaab/data/ | 9 repository tests + `support/TestDatabase.kt` | — |
| commonTest/kotlin/app/hisaab/screens/ | `entry/EntryViewModelTest.kt`, `today/TodayViewModelTest.kt`, `transaction/TransactionDetailViewModelTest.kt` | — |

10 tasks. Build order: TestDatabase helper → smallest repos → larger repos → AppContainer extension → MainGraph → screens.

---

## Task 10: AccountRepository + CategoryRepository + auto-seed

Create the in-memory test DB helper, then both repositories with TDD. Spec §6 has signatures.

**Files:**
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/data/support/TestDatabase.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/data/AccountRepository.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/data/CategoryRepository.kt`
- Create: 2 test files

- [ ] **Step 1: TestDatabase helper**

Create `composeApp/src/commonTest/kotlin/app/hisaab/data/support/TestDatabase.kt`:

```kotlin
package app.hisaab.data.support

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.hisaab.db.HisaabDatabase

object TestDatabase {
    fun create(): HisaabDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HisaabDatabase.Schema.create(driver)
        return HisaabDatabase(driver)
    }
}
```

- [ ] **Step 2: AccountRepository TDD**

Tests: `ensureDefaultCashAccount` idempotent; `add` writes correct kind/currency; `observeActive` excludes archived; `archive` sets `archived_at`.

```kotlin
class AccountRepositoryTest {
    @Test
    fun `ensureDefaultCashAccount is idempotent`() = runTest {
        val repo = AccountRepository(TestDatabase.create())
        val id1 = repo.ensureDefaultCashAccount()
        val id2 = repo.ensureDefaultCashAccount()
        assertEquals(id1, id2)
        assertEquals(1, repo.observeActive().first().size)
    }
    // … add 2-3 more tests covering add/archive
}
```

- [ ] **Step 3: AccountRepository implementation**

Follow spec §6 signature. Use `app.cash.sqldelight.coroutines.asFlow` + `mapToList(Dispatchers.Default)` for `observeActive`. Map SQLDelight row → `app.hisaab.domain.Account` via a private `toDomain()` helper. Use `LibsodiumRandom.buf(16).toByteArray().joinToString { hex }` for ID generation (consistent with `OnboardingViewModel`).

Key body for `ensureDefaultCashAccount`:

```kotlin
suspend fun ensureDefaultCashAccount(): String {
    val existing = db.accountQueries.observeActiveAccounts().executeAsList()
    existing.firstOrNull { it.kind == AccountKind.CASH.name }?.let { return it.id }
    return add(name = "Cash", kind = AccountKind.CASH, institution = null)
}
```

- [ ] **Step 4: CategoryRepository TDD + implementation**

Tests: `ensureDefaults()` seeds 12 + is idempotent; `observeAll()` returns alphabetical.

Implementation seeds the 12 defaults from spec §4 — define them as a `companion object` constant list of `(id, name, color, icon)` tuples and call `insertCategoryIfMissing` for each. Use stable string IDs (`"food"`, `"transport"`, etc.) so subsequent ensureDefaults calls are no-ops.

- [ ] **Step 5: Compile, run tests, commit**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.AccountRepositoryTest" \
                                         --tests "app.hisaab.data.CategoryRepositoryTest" --no-daemon 2>&1 | tail -15
git add composeApp/src/commonMain/kotlin/app/hisaab/data/AccountRepository.kt \
        composeApp/src/commonMain/kotlin/app/hisaab/data/CategoryRepository.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/data/
git commit -m "feat(data): AccountRepository + CategoryRepository + 12 default category seed + tests"
```

---

## Task 11: MerchantRepository + TagRepository

Spec §6 has signatures. Both are small (~50 lines each).

- [ ] **Step 1: MerchantRepository TDD**

Tests: `upsertByName` dedups by normalized name (case-insensitive: "Aarong" and "AARONG" merge); `searchByPrefix` returns max 10 ordered alphabetical; `observeAll` returns alphabetical.

Implementation:
- `upsertByName(name, defaultCategoryId)`: compute `normalizeMerchantName(name)` (helper from Task 1's `Merchant.kt`); call `findMerchantByNormalizedName(normalized)`; return existing id or `insertMerchant(randomId(), name, normalized, defaultCategoryId)`.
- `searchByPrefix(prefix)`: pass `normalizeMerchantName(prefix)` to `searchMerchantsByPrefix(?)`.

- [ ] **Step 2: TagRepository TDD**

Tests: `upsertByName` dedups by exact name (case-sensitive — tags are curated); `linkToTxn(txnId, tagIds)` first unlinks existing then re-links so re-saving the same edit doesn't double-link; `observeTagsForTxn` returns joined rows.

Implementation uses `db.transactionQueries.findTagByName(name)` + `insertTag` + `unlinkTxnTags(txnId)` + `linkTxnTag(txnId, tagId)` for each new tag.

- [ ] **Step 3: Compile, test, commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/data/MerchantRepository.kt \
        composeApp/src/commonMain/kotlin/app/hisaab/data/TagRepository.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/data/MerchantRepositoryTest.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/data/TagRepositoryTest.kt
git commit -m "feat(data): MerchantRepository (dedup) + TagRepository + tests"
```

---

## Task 12: TransactionRepository (the big one)

Most complex repository — merchant linking, tag linking, splits, three time-windowed observers, today's net aggregator.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/data/TransactionRepository.kt`
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/data/TransactionRepositoryTest.kt`

- [ ] **Step 1: Tests (write all failing first)**

Cover:
- `add(NewTransaction)` writes the row, calls `MerchantRepository.upsertByName` when merchantName is non-blank, calls `TagRepository.linkToTxn` when tagNames non-empty.
- `observeRecent(limit)` returns top-level rows only (WHERE parent_txn_id IS NULL) ordered by ts DESC.
- `observeForDay(epochDayMs)` filters to `[start, start + 86_400_000)` where start = day-floor of epochDayMs.
- `observeForMonth(YearMonth)` uses `kotlinx.datetime` to compute the month's epoch-ms range correctly (LocalDate(year, month, 1).atStartOfDayIn(currentSystemDefault TZ) … same for next month).
- `addSplits(parentId, children)` writes each child with `parent_txn_id = parentId`, same account/currency/ts as parent.
- `update(id, patch)` updates only non-null fields in TransactionPatch (use SQLDelight `getTxn` to load current values then call `updateTxn` with merged fields).
- `delete(id)` — cascades to txn_tag links and child splits (DB FK ON DELETE CASCADE).
- `observeTodayNet()` aggregates today's `SUM` by kind, returns `MoneyTotals(income=incomeSum, expense=expenseSum, net=incomeSum-expenseSum)`.

- [ ] **Step 2: Implementation**

Inject `merchantRepo` + `tagRepo` via constructor. The trickiest parts:

```kotlin
suspend fun add(input: NewTransaction): String {
    val id = randomId()
    val merchantId = input.merchantName?.takeIf { it.isNotBlank() }
        ?.let { merchantRepo.upsertByName(it, defaultCategoryId = input.categoryId) }
    db.transactionQueries.insertTxn(
        id = id, account_id = input.accountId, amount = input.amount,
        currency = input.currency, ts = input.ts,
        merchant_id = merchantId, category_id = input.categoryId,
        source = input.source.name, notes = input.notes,
        kind = input.kind.name, parent_txn_id = null,
    )
    if (input.tagNames.isNotEmpty()) {
        val tagIds = input.tagNames.map { tagRepo.upsertByName(it) }
        tagRepo.linkToTxn(id, tagIds)
    }
    return id
}

fun observeTodayNet(): Flow<MoneyTotals> {
    val (start, end) = todayRangeMs()
    return db.transactionQueries.sumByKindForRange(start, end).asFlow()
        .mapToList(Dispatchers.Default)
        .map { rows ->
            val income = rows.firstOrNull { it.kind == "INCOME" }?.total ?: 0.0
            val expense = rows.firstOrNull { it.kind == "EXPENSE" }?.total ?: 0.0
            MoneyTotals(income, expense, income - expense)
        }
}
```

The `TransactionRow` domain model includes `merchantName`, `categoryName`, `accountName`, `categoryColor` — but the `observeRecentTopLevel` query returns only IDs. The simplest approach is to populate `TransactionRow` with IDs and `null` names, and let the consumer (TodayViewModel, Task 18) resolve names by combining with `MerchantRepository.observeAll()` / `CategoryRepository.observeAll()` / `AccountRepository.observeActive()` flows.

This is a deliberate trade-off: keeps queries simple, pushes the join cost into Kotlin (which is fine for ≤200 rows on Today). If perf becomes an issue with growth, add a joined query to TransactionQueries.sq.

- [ ] **Step 3: Compile, test, commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/data/TransactionRepository.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/data/TransactionRepositoryTest.kt
git commit -m "feat(data): TransactionRepository — CRUD + splits + day/month windows + todayNet + tests"
```

---

## Task 13: PersonRepository + LendBorrowRepository (settle flow)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/data/PersonRepository.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/data/LendBorrowRepository.kt`
- Create: 2 test files
- Modify: `composeApp/src/commonMain/sqldelight/app/hisaab/db/LendBorrowQueries.sq` — add `getLendBorrowById:`

- [ ] **Step 1: Add the missing query**

Open `LendBorrowQueries.sq` (created in P0c-1 Task 1) and add:

```sql
getLendBorrowById:
SELECT * FROM lend_borrow WHERE id = ?;
```

- [ ] **Step 2: PersonRepository TDD**

Tests for phone-as-dedup:
- `upsertFromContact("Karim", "+8801712345678")` first call inserts; second call with same phone (even different name) returns same id.
- `addManual("Karim")` two calls create two persons (no dedup on plain name).
- `observeAll()` returns each person with computed balance from `getPersonBalance`.

Implementation: `upsertFromContact` queries `findPersonByPhone(phone)`; if hit, returns id; else inserts. `addManual` always inserts with null `contact_ref`. `observeAll` is the trickiest — needs to combine the `observeAllPersons()` flow with per-person balance. Two approaches:

**A (simpler):** Map each person to `PersonWithBalance(person, balance = db.lendBorrowQueries.getPersonBalance(person.id).executeAsOneOrNull()?.balance ?: 0.0)`. Re-emits when persons change, but not when lend_borrow rows change. Acceptable for P0c.

**B (better):** `combine(observeAllPersons().asFlow().mapToList, db.lendBorrowQueries.observeOpenLendBorrow().asFlow().mapToList)`, then resolve balances in Kotlin. Re-emits on either change.

Go with **B** since lend/borrow is the primary use case.

- [ ] **Step 3: LendBorrowRepository TDD**

Tests:
- `record(NewLendBorrow)` writes both lend_borrow + linked txn with `kind = LEND` or `kind = BORROW`.
- `record()` returns `Pair<String, String>` (lendBorrowId, txnId) — the txnId is used by EntryViewModel (Task 19) to attach images.
- `settle(lendBorrowId, settlementAmount, accountId)` writes a `SETTLEMENT` txn, links it, sets status to SETTLED (if full) or PARTIAL.
- `observeForPerson` returns the person's lend/borrow records DESC by ts.
- `observeOpen` returns all status != SETTLED.

- [ ] **Step 4: LendBorrowRepository implementation**

```kotlin
class LendBorrowRepository(
    private val db: HisaabDatabase,
    private val txnRepo: TransactionRepository,
) {
    suspend fun record(input: NewLendBorrow): Pair<String, String> {
        val lendBorrowId = randomId()
        val txnKind = if (input.direction == LendBorrowDirection.LENT) TxnKind.LEND else TxnKind.BORROW
        val categoryId = if (input.direction == LendBorrowDirection.LENT) "lend" else "borrow"
        db.lendBorrowQueries.insertLendBorrow(
            id = lendBorrowId, person_id = input.personId,
            amount = input.amount, direction = input.direction.name,
            purpose = input.purpose, ts = input.ts, due_date = input.dueDate,
            status = LendBorrowStatus.OPEN.name,
        )
        val txnId = txnRepo.add(NewTransaction(
            accountId = input.accountId, amount = input.amount,
            ts = input.ts, merchantName = null, categoryId = categoryId,
            notes = input.purpose, kind = txnKind,
        ))
        db.lendBorrowQueries.linkLendBorrowTxn(lendBorrowId, txnId)
        return lendBorrowId to txnId
    }

    suspend fun settle(lendBorrowId: String, settlementAmount: Double, accountId: String) {
        val current = db.lendBorrowQueries.getLendBorrowById(lendBorrowId).executeAsOne()
        val settlementTxnId = txnRepo.add(NewTransaction(
            accountId = accountId, amount = settlementAmount,
            ts = Clock.System.now().toEpochMilliseconds(),
            merchantName = null, categoryId = "transfer",
            notes = "Settlement", kind = TxnKind.SETTLEMENT,
        ))
        db.lendBorrowQueries.linkLendBorrowTxn(lendBorrowId, settlementTxnId)
        val newStatus = if (settlementAmount >= current.amount) LendBorrowStatus.SETTLED else LendBorrowStatus.PARTIAL
        db.lendBorrowQueries.updateLendBorrowStatus(newStatus.name, lendBorrowId)
    }

    fun observeForPerson(personId: String): Flow<List<LendBorrowRow>> = /* … */
    fun observeOpen(): Flow<List<LendBorrowRow>> = /* … */
}
```

- [ ] **Step 5: Compile, test, commit**

```bash
git add composeApp/src/commonMain/sqldelight/app/hisaab/db/LendBorrowQueries.sq \
        composeApp/src/commonMain/kotlin/app/hisaab/data/PersonRepository.kt \
        composeApp/src/commonMain/kotlin/app/hisaab/data/LendBorrowRepository.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/data/
git commit -m "feat(data): PersonRepository (phone-dedup) + LendBorrowRepository (record returns id pair, settle) + tests"
```

---

## Task 14: BudgetRepository + InsightRepository

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/data/BudgetRepository.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/data/InsightRepository.kt`
- Create: 2 test files

- [ ] **Step 1: BudgetRepository (small)**

Spec §6 signature. `set` inserts a new budget row. `observeActive` joins `observeActiveBudgets()` with category names. `archive` sets `archived_at`.

Tests cover happy paths: set + observe; archive; only-active visible.

- [ ] **Step 2: InsightRepository (5 aggregation methods)**

Spec §6 signature. Each method wraps a SQLDelight query from `InsightQueries.sq`:

```kotlin
fun computeMonthlyTotals(yearMonth: YearMonth): Flow<MonthlyTotals> {
    val (start, end) = monthRangeMs(yearMonth)
    val (prevStart, prevEnd) = monthRangeMs(yearMonth.previous())
    return combine(
        db.insightQueries.monthlySumByKind(start, end).asFlow().mapToList(Dispatchers.Default),
        db.insightQueries.monthlySumByKind(prevStart, prevEnd).asFlow().mapToList(Dispatchers.Default),
    ) { cur, prev ->
        val income = cur.firstOrNull { it.kind == "INCOME" }?.total ?: 0.0
        val expense = cur.firstOrNull { it.kind == "EXPENSE" }?.total ?: 0.0
        val prevIncome = prev.firstOrNull { it.kind == "INCOME" }?.total ?: 0.0
        val prevExpense = prev.firstOrNull { it.kind == "EXPENSE" }?.total ?: 0.0
        MonthlyTotals(yearMonth, income, expense, income - expense, prevIncome - prevExpense)
    }
}

fun computeCategoryBreakdown(yearMonth: YearMonth): Flow<List<CategorySlice>> {
    val (start, end) = monthRangeMs(yearMonth)
    return db.insightQueries.categoryBreakdownForRange(start, end).asFlow()
        .mapToList(Dispatchers.Default)
        .map { rows ->
            val total = rows.sumOf { it.total ?: 0.0 }
            rows.map { CategorySlice(
                categoryId = it.category_id, categoryName = it.category_name,
                categoryColor = it.category_color, total = it.total ?: 0.0,
                percent = if (total > 0) (it.total ?: 0.0) / total * 100.0 else 0.0,
            ) }
        }
}

fun computePerDaySpend(yearMonth: YearMonth): Flow<List<DayBucket>> { /* perDaySpendForRange */ }
fun detectRecurring(): Flow<List<RecurringHit>> {
    val ninetyDaysAgo = Clock.System.now().toEpochMilliseconds() - (90L * 86_400_000L)
    return db.insightQueries.recurringCandidates(ninetyDaysAgo).asFlow()
        .mapToList(Dispatchers.Default)
        .map { rows -> rows.map { RecurringHit(it.merchant_id, it.merchant_name, it.occurrence_count, it.avg_amount ?: 0.0, it.last_seen_ts ?: 0L) } }
}
fun computeBudgetProgress(yearMonth: YearMonth, budgetRepo: BudgetRepository): Flow<List<BudgetProgress>> {
    val (start, end) = monthRangeMs(yearMonth)
    return budgetRepo.observeActive().map { budgets ->
        budgets.map { b ->
            val spent = db.insightQueries.budgetSpendForCategory(b.categoryId, start, end).executeAsOneOrNull()?.total ?: 0.0
            BudgetProgress(b, spent, if (b.monthlyCapAmount > 0) spent / b.monthlyCapAmount * 100.0 else 0.0)
        }
    }
}

private fun monthRangeMs(ym: YearMonth): Pair<Long, Long> = TODO()  // same impl as TransactionRepository
```

Extract the `monthRangeMs` helper to a shared `commonMain/util/DateRange.kt` to avoid duplicating it between TransactionRepository and InsightRepository.

- [ ] **Step 3: Compile, test, commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/data/BudgetRepository.kt \
        composeApp/src/commonMain/kotlin/app/hisaab/data/InsightRepository.kt \
        composeApp/src/commonMain/kotlin/app/hisaab/util/DateRange.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/data/
git commit -m "feat(data): BudgetRepository + InsightRepository (5 aggregations) + DateRange helper + tests"
```

---

## Task 15: AttachmentRepository (BlobCrypto + PlatformFileStore)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/data/AttachmentRepository.kt`

Spec §6 signature. The trick: this repo needs the `master_secret` (in memory) at encrypt/decrypt time, which is held by `AppContainer`. Pass it via a `() -> ByteArray?` provider lambda.

```kotlin
class AttachmentRepository(
    private val db: HisaabDatabase,
    private val blobCrypto: BlobCrypto,
    private val masterSecretProvider: () -> ByteArray?,
    private val fileStore: PlatformFileStore,
    private val cryptoService: CryptoService,
) {
    suspend fun attach(txnId: String, imageBytes: ByteArray, mimeType: String): String {
        val secret = masterSecretProvider() ?: error("DB locked — cannot attach")
        val dbKey = cryptoService.deriveDbKey(secret)
        val (ciphertext, iv) = blobCrypto.encrypt(imageBytes, dbKey)
        dbKey.fill(0)
        val id = randomId()
        val relativePath = "$txnId/$id.bin"
        fileStore.writeBytes(relativePath, ciphertext)
        db.transactionQueries.insertAttachment(
            id = id, txn_id = txnId, mime_type = mimeType,
            file_path = relativePath, encrypted_iv = iv,
            size_bytes = imageBytes.size.toLong(),
            created_at = Clock.System.now().toEpochMilliseconds(),
        )
        return id
    }

    suspend fun decrypt(attachmentId: String): ByteArray? {
        val record = db.transactionQueries.getAttachment(attachmentId).executeAsOneOrNull() ?: return null
        val ciphertext = fileStore.readBytes(record.file_path) ?: return null
        val secret = masterSecretProvider() ?: error("DB locked")
        val dbKey = cryptoService.deriveDbKey(secret)
        val plaintext = blobCrypto.decrypt(ciphertext, dbKey, record.encrypted_iv)
        dbKey.fill(0)
        return plaintext
    }

    suspend fun delete(attachmentId: String) {
        val record = db.transactionQueries.getAttachment(attachmentId).executeAsOneOrNull() ?: return
        fileStore.delete(record.file_path)
        db.transactionQueries.deleteAttachment(attachmentId)
    }
}
```

No unit tests (libsodium native lib + PlatformFileStore not available in JVM tests). The instrumented test in P0c-3 Task 23 will exercise the encrypt/decrypt round-trip on device.

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/data/AttachmentRepository.kt
git commit -m "feat(data): AttachmentRepository (E2EE blob via BlobCrypto + PlatformFileStore)"
```

---

## Task 16: AppContainer exposes 10 repositories + auto-seed

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/AppContainer.kt`
- Modify: `composeApp/src/androidMain/kotlin/app/hisaab/AppContainerAndroid.kt`

- [ ] **Step 1: Add 10 repo getters to expect class**

Append to `expect class AppContainer { … }`:

```kotlin
val accountRepository: AccountRepository
val categoryRepository: CategoryRepository
val merchantRepository: MerchantRepository
val tagRepository: TagRepository
val transactionRepository: TransactionRepository
val personRepository: PersonRepository
val lendBorrowRepository: LendBorrowRepository
val budgetRepository: BudgetRepository
val attachmentRepository: AttachmentRepository
val insightRepository: InsightRepository
```

- [ ] **Step 2: Android actual — `get()` properties that throw if DB not open**

```kotlin
actual val accountRepository: AccountRepository get() = AccountRepository(requireDb())
actual val categoryRepository: CategoryRepository get() = CategoryRepository(requireDb())
actual val merchantRepository: MerchantRepository get() = MerchantRepository(requireDb())
actual val tagRepository: TagRepository get() = TagRepository(requireDb())
actual val transactionRepository: TransactionRepository get() = TransactionRepository(requireDb(), merchantRepository, tagRepository)
actual val personRepository: PersonRepository get() = PersonRepository(requireDb())
actual val lendBorrowRepository: LendBorrowRepository get() = LendBorrowRepository(requireDb(), transactionRepository)
actual val budgetRepository: BudgetRepository get() = BudgetRepository(requireDb())
actual val attachmentRepository: AttachmentRepository get() = AttachmentRepository(
    db = requireDb(),
    blobCrypto = blobCrypto,
    masterSecretProvider = ::masterSecretInMemory,
    fileStore = fileStore,
    cryptoService = cryptoService,
)
actual val insightRepository: InsightRepository get() = InsightRepository(requireDb())

private fun requireDb(): HisaabDatabase = cachedDatabase
    ?: error("Database not open — onboarding incomplete")
```

Each `get()` returns a fresh instance — they're stateless thin wrappers, so this is cheap.

- [ ] **Step 3: Auto-seed on openDatabase**

Update `openDatabase` to seed defaults the first time the DB is opened:

```kotlin
actual fun openDatabase(masterSecret: ByteArray): HisaabDatabase {
    cachedDatabase?.let { return it }
    cachedMasterSecret = masterSecret.copyOf()
    val keyCopy = cryptoService.deriveDbKey(masterSecret)
    val driver = databaseDriverFactory.createDriver(keyCopy)
    cachedDriver = driver
    val db = HisaabDatabase(driver)
    cachedDatabase = db
    // Idempotent auto-seed
    kotlinx.coroutines.runBlocking {
        categoryRepository.ensureDefaults()
        accountRepository.ensureDefaultCashAccount()
    }
    return db
}
```

`runBlocking` is acceptable inside `openDatabase` because the caller is already in a coroutine (from `OnboardingViewModel.completeProfile` or `LockScreen.attemptUnlock`).

- [ ] **Step 4: Compile, commit**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --no-daemon 2>&1 | tail -8
git add composeApp/src/commonMain/kotlin/app/hisaab/AppContainer.kt \
        composeApp/src/androidMain/kotlin/app/hisaab/AppContainerAndroid.kt
git commit -m "feat(app): AppContainer exposes 10 repositories + auto-seed Cash + 12 categories on openDatabase"
```

---

## Task 17: MainGraph + MainViewModel (bottom-nav scaffold)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/main/MainGraph.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/main/MainViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/App.kt` — `is AppState.Authenticated -> MainGraph()` instead of `TodayScreen()`

- [ ] **Step 1: MainViewModel**

```kotlin
package app.hisaab.screens.main

enum class MainTab { TODAY, MONTH, PEOPLE, SETTINGS }

class MainViewModel {
    var selectedTab = MainTab.TODAY
}
```

Plain class — selected tab state actually lives in `rememberNavController`'s back stack, so the ViewModel is minimal.

- [ ] **Step 2: MainGraph**

`Scaffold` with `NavigationBar` (4 tabs) at bottom + FAB (terracotta) that navigates to `"entry"` modal route.

```kotlin
@Composable
fun MainGraph() {
    val palette = LocalHisaabPalette.current
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in setOf(MainTab.TODAY.name, MainTab.MONTH.name, MainTab.PEOPLE.name, MainTab.SETTINGS.name)) {
                NavigationBar(containerColor = palette.surface) {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.name,
                            onClick = {
                                navController.navigate(tab.name) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(tab.iconChar(), fontSize = 18.sp) },
                            label = { Text(tab.label()) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = palette.accent,
                                selectedTextColor = palette.accent,
                                indicatorColor = palette.background,
                                unselectedIconColor = palette.muted,
                                unselectedTextColor = palette.muted,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute in setOf(MainTab.TODAY.name, MainTab.MONTH.name, MainTab.PEOPLE.name)) {
                FloatingActionButton(
                    onClick = { navController.navigate("entry") },
                    containerColor = palette.accent,
                ) { Text("+", color = palette.background, fontSize = 28.sp) }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MainTab.TODAY.name,
            modifier = Modifier.padding(padding),
        ) {
            composable(MainTab.TODAY.name) {
                TodayScreen(onTxnClick = { id -> navController.navigate("txn/$id") })
            }
            composable(MainTab.MONTH.name) { MonthScreenPlaceholder() }     // P0c-3
            composable(MainTab.PEOPLE.name) { PeopleScreenPlaceholder() }   // P0c-3
            composable(MainTab.SETTINGS.name) { SettingsScreenPlaceholder() } // P0c-3
            composable("entry") {
                EntryScreen(onDone = { navController.popBackStack() })
            }
            composable("txn/{id}") { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                TransactionDetailScreen(
                    txnId = id,
                    onDone = { navController.popBackStack() },
                    onEdit = { navController.navigate("entry?editId=$id") },  // future: prefilled entry
                )
            }
        }
    }
}

private fun MainTab.label() = when (this) {
    MainTab.TODAY -> "Today"; MainTab.MONTH -> "Month"
    MainTab.PEOPLE -> "People"; MainTab.SETTINGS -> "Settings"
}
private fun MainTab.iconChar() = when (this) {
    MainTab.TODAY -> "•"; MainTab.MONTH -> "☷"
    MainTab.PEOPLE -> "○"; MainTab.SETTINGS -> "⚙"
}

@Composable private fun MonthScreenPlaceholder() = PlaceholderText("Month — P0c-3")
@Composable private fun PeopleScreenPlaceholder() = PlaceholderText("People — P0c-3")
@Composable private fun SettingsScreenPlaceholder() = PlaceholderText("Settings — P0c-3")
@Composable private fun PlaceholderText(t: String) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(t) } }
```

- [ ] **Step 3: Update App.kt**

```kotlin
is AppState.Authenticated -> MainGraph()  // replaces TodayScreen()
```

- [ ] **Step 4: Compile and commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/screens/main/ \
        composeApp/src/commonMain/kotlin/app/hisaab/App.kt
git commit -m "feat(app): MainGraph bottom-nav scaffold + FAB to EntryScreen"
```

---

## Task 18: TodayScreen rewrite + TodayViewModel

**Files:**
- Rewrite: `composeApp/src/commonMain/kotlin/app/hisaab/screens/today/TodayScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/today/TodayViewModel.kt`
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/screens/today/TodayViewModelTest.kt`

- [ ] **Step 1: TodayViewModel TDD**

Tests:
- Empty state: `todayNet.value == MoneyTotals(0, 0, 0)`, `recent.value == []`.
- After adding one EXPENSE ৳150 via `TransactionRepository.add`, the new row appears in `recent.value` with resolved merchant/category/account names; `todayNet.value.expense == 150.0`.

- [ ] **Step 2: TodayViewModel implementation**

```kotlin
class TodayViewModel(
    private val txnRepo: TransactionRepository,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val merchantRepo: MerchantRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    val todayNet: StateFlow<MoneyTotals> =
        txnRepo.observeTodayNet()
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), MoneyTotals(0.0, 0.0, 0.0))

    val recent: StateFlow<List<TransactionRowDisplay>> = combine(
        txnRepo.observeRecent(50),
        accountRepo.observeActive(),
        categoryRepo.observeAll(),
        merchantRepo.observeAll(),
    ) { txns, accounts, cats, merchants ->
        val accountsById = accounts.associateBy { it.id }
        val catsById = cats.associateBy { it.id }
        val merchantsById = merchants.associateBy { it.id }
        txns.map { txn ->
            TransactionRowDisplay(
                row = txn,
                accountName = accountsById[txn.accountId]?.name ?: "?",
                merchantName = txn.merchantId?.let { merchantsById[it]?.name },
                categoryName = txn.categoryId?.let { catsById[it]?.name },
                categoryColor = txn.categoryId?.let { catsById[it]?.color },
            )
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
}

data class TransactionRowDisplay(
    val row: TransactionRow,
    val accountName: String,
    val merchantName: String?,
    val categoryName: String?,
    val categoryColor: String?,
)
```

- [ ] **Step 3: TodayScreen UI**

Rewrite `TodayScreen.kt`:

```kotlin
@Composable
fun TodayScreen(onTxnClick: (String) -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val viewModel = remember { TodayViewModel(
        txnRepo = container.transactionRepository,
        accountRepo = container.accountRepository,
        categoryRepo = container.categoryRepository,
        merchantRepo = container.merchantRepository,
    ) }
    val net by viewModel.todayNet.collectAsState()
    val recent by viewModel.recent.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(palette.background).padding(22.dp)) {
        Text("Today", style = MaterialTheme.typography.displaySmall, color = palette.onBackground)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            NetCell(label = "In", amount = net.income, color = palette.positive)
            NetCell(label = "Out", amount = net.expense, color = palette.negative)
            NetCell(label = "Net", amount = net.net, color = palette.onBackground)
        }
        Spacer(Modifier.height(24.dp))

        if (recent.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No entries yet.\nTap + to record your first.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.muted,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(recent) { display ->
                    TxnRow(display = display, palette = palette, onClick = { onTxnClick(display.row.id) })
                }
            }
        }
    }
}

@Composable
private fun NetCell(label: String, amount: Double, color: Color) { /* small column with muted label + colored amount */ }

@Composable
private fun TxnRow(display: TransactionRowDisplay, palette: HisaabColors.Palette, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(display.merchantName ?: display.categoryName ?: "—", color = palette.onBackground)
            Text(display.accountName, fontSize = 11.sp, color = palette.muted)
        }
        val (sign, color) = when (display.row.kind) {
            TxnKind.INCOME, TxnKind.LEND, TxnKind.SETTLEMENT -> "+" to palette.positive
            else -> "−" to palette.negative
        }
        Text("$sign৳${display.row.amount.toInt()}", color = color)
    }
}
```

Sectioning by day (Today / Yesterday / past dates) can be a follow-up polish in P0c-3 or P0d — for P0c-2 the recent list is fine flat.

- [ ] **Step 4: Compile, test, commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/screens/today/ \
        composeApp/src/commonTest/kotlin/app/hisaab/screens/today/
git commit -m "feat(today): rewrite TodayScreen with TodayViewModel + real transaction list"
```

---

## Task 19: EntryScreen flagship + EntryViewModel + supporting screens

The biggest screen-side task. Builds the rich entry form, splits sub-sheet, transaction detail, and wires the real ImagePicker.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/entry/EntryFormState.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/entry/EntryViewModel.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/entry/EntryScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/entry/MerchantAutocomplete.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/entry/SplitEditorSheet.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/transaction/TransactionDetailScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/screens/transaction/TransactionDetailViewModel.kt`
- Modify: `composeApp/src/androidMain/kotlin/app/hisaab/platform/ImagePicker.kt` (replace P0c-1 stub with real PickVisualMedia)
- Create test files

- [ ] **Step 1: EntryFormState data class** (spec §8 layout)

```kotlin
data class EntryFormState(
    val kind: TxnKind = TxnKind.EXPENSE,
    val amount: String = "",
    val accountId: String? = null,
    val categoryId: String? = null,
    val whenMs: Long = Clock.System.now().toEpochMilliseconds(),
    val merchantName: String = "",
    val notes: String = "",
    val tagNames: List<String> = emptyList(),
    val attachmentBytes: ByteArray? = null,
    val attachmentMimeType: String? = null,
    val splits: List<NewSplitTransaction> = emptyList(),
    val personId: String? = null,
    val newPersonName: String? = null,
    val newPersonPhone: String? = null,
    val dueDate: Long? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val isValid: Boolean
        get() = amount.toDoubleOrNull()?.let { it > 0 } == true && accountId != null
            && (kind !in setOf(TxnKind.LEND, TxnKind.BORROW)
                || personId != null
                || (newPersonName?.isNotBlank() == true))
}
```

- [ ] **Step 2: EntryViewModel — TDD**

Tests:
- Initial state: kind=EXPENSE, isValid=false.
- After setAmount("150") and setAccount("cash-id"), isValid=true for EXPENSE.
- For kind=LEND, isValid=false until person is set.
- `save()` for EXPENSE calls `txnRepo.add` with correct NewTransaction.
- `save()` for LEND with new person calls `personRepo.addManual` then `lendBorrowRepo.record`.
- `save()` with attachmentBytes calls `attachmentRepo.attach(txnId, bytes, mime)` after insert.
- `save()` with splits non-empty calls `txnRepo.addSplits(parentId, children)`.

- [ ] **Step 3: EntryViewModel implementation**

Pattern: hold `MutableStateFlow<EntryFormState>`. Per-field setters update via `_state.update`. `save(onDone)` parses amount, dispatches to the right repo path based on `kind`, attaches images via `attachmentRepo`, then calls `onDone()`.

For LEND/BORROW path, `lendBorrowRepo.record()` now returns `Pair<lendBorrowId, txnId>` (from Task 13 fix) — use the txnId for any attachment hookup.

- [ ] **Step 4: EntryScreen UI** (spec §8 layout)

```kotlin
@Composable
fun EntryScreen(onDone: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val viewModel = remember {
        EntryViewModel(
            txnRepo = container.transactionRepository,
            lendBorrowRepo = container.lendBorrowRepository,
            personRepo = container.personRepository,
            attachmentRepo = container.attachmentRepository,
            imagePicker = container.imagePicker,
            accountsFlow = container.accountRepository.observeActive(),
            categoriesFlow = container.categoryRepository.observeAll(),
            merchantSearchProvider = { prefix -> container.merchantRepository.searchByPrefix(prefix) },
        )
    }
    val state by viewModel.state.collectAsState()
    val accounts by container.accountRepository.observeActive().collectAsState(initial = emptyList())
    val categories by container.categoryRepository.observeAll().collectAsState(initial = emptyList())

    var showAccountSheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showSplitSheet by remember { mutableStateOf(false) }
    var showWhenSheet by remember { mutableStateOf(false) }
    var showPersonSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New entry") },
                navigationIcon = { TextButton(onClick = onDone) { Text("Cancel") } },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(onDone) },
                        enabled = state.isValid && !state.isSaving,
                    ) { Text("Save", color = if (state.isValid) palette.accent else palette.muted) }
                },
            )
        },
        containerColor = palette.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 22.dp).verticalScroll(rememberScrollState())) {

            // Kind segmented selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TxnKind.entries.filter { it != TxnKind.SETTLEMENT }.forEach { k ->
                    Button(
                        onClick = { viewModel.setKind(k) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.kind == k) palette.accent else palette.surface,
                            contentColor = if (state.kind == k) palette.background else palette.onBackground,
                        ),
                    ) { Text(k.label()) }
                }
            }
            Spacer(Modifier.height(24.dp))

            // Hero amount input
            BasicTextField(
                value = state.amount,
                onValueChange = viewModel::setAmount,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 56.sp,
                    color = palette.onBackground,
                ),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("৳ ", fontSize = 56.sp, color = palette.accent)
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(24.dp))

            // Field rows
            EntryFieldRow(label = "Account",
                value = accounts.firstOrNull { it.id == state.accountId }?.name ?: "Select",
                onClick = { showAccountSheet = true })
            EntryFieldRow(label = "Category",
                value = categories.firstOrNull { it.id == state.categoryId }?.name ?: "Select",
                onClick = { showCategorySheet = true })
            EntryFieldRow(label = "When",
                value = formatDateTime(state.whenMs),
                onClick = { showWhenSheet = true })

            // Merchant autocomplete
            MerchantAutocomplete(
                value = state.merchantName,
                onValueChange = viewModel::setMerchant,
                searchProvider = { container.merchantRepository.searchByPrefix(it) },
                palette = palette,
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )

            // Tags chip input
            TagChipInput(
                tags = state.tagNames,
                onAdd = viewModel::addTag,
                onRemove = viewModel::removeTag,
                palette = palette,
            )

            // Attachment
            AttachmentRow(
                hasAttachment = state.attachmentBytes != null,
                onPick = { viewModel.pickAttachment() },
                onClear = { viewModel.clearAttachment() },
                palette = palette,
            )

            // Split row
            EntryFieldRow(
                label = "Split",
                value = if (state.splits.isEmpty()) "Single entry" else "${state.splits.size} parts",
                onClick = { showSplitSheet = true },
            )

            // LEND/BORROW-only fields
            if (state.kind in setOf(TxnKind.LEND, TxnKind.BORROW)) {
                EntryFieldRow(
                    label = "Person",
                    value = state.personId?.let { "selected" } ?: state.newPersonName ?: "Pick",
                    onClick = { showPersonSheet = true },
                )
                EntryFieldRow(
                    label = "Due date",
                    value = state.dueDate?.let { formatDate(it) } ?: "Optional",
                    onClick = { /* show date picker */ },
                )
            }

            state.error?.let { Text(it, color = palette.negative, modifier = Modifier.padding(top = 12.dp)) }
        }
    }

    // Bottom sheets
    if (showAccountSheet) AccountPickerSheet(accounts, onPick = { viewModel.setAccount(it); showAccountSheet = false }, onDismiss = { showAccountSheet = false })
    if (showCategorySheet) CategoryPickerSheet(categories, onPick = { viewModel.setCategory(it); showCategorySheet = false }, onDismiss = { showCategorySheet = false })
    if (showSplitSheet) SplitEditorSheet(state.splits, parentAmount = state.amount.toDoubleOrNull() ?: 0.0, categories = categories, onSave = { viewModel.setSplits(it); showSplitSheet = false }, onDismiss = { showSplitSheet = false })
    if (showWhenSheet) DateTimePickerSheet(state.whenMs, onPick = { viewModel.setWhen(it); showWhenSheet = false }, onDismiss = { showWhenSheet = false })
    if (showPersonSheet) PersonPickerSheet(onExisting = { viewModel.setExistingPerson(it); showPersonSheet = false }, onNew = { name, phone -> viewModel.setNewPerson(name, phone); showPersonSheet = false }, onDismiss = { showPersonSheet = false })
}

@Composable
private fun EntryFieldRow(label: String, value: String, onClick: () -> Unit) {
    val palette = LocalHisaabPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = palette.muted, modifier = Modifier.weight(1f))
        Text(value, color = palette.onBackground)
        Spacer(Modifier.width(6.dp))
        Text("›", color = palette.muted)
    }
    Divider(color = palette.rule)
}

private fun TxnKind.label() = when (this) {
    TxnKind.EXPENSE -> "Expense"; TxnKind.INCOME -> "Income"
    TxnKind.LEND -> "Lend"; TxnKind.BORROW -> "Borrow"
    TxnKind.TRANSFER -> "Transfer"; TxnKind.SETTLEMENT -> "Settle"
}
```

The supporting composables (`AccountPickerSheet`, `CategoryPickerSheet`, `DateTimePickerSheet`, `PersonPickerSheet`, `TagChipInput`, `AttachmentRow`, `formatDateTime`/`formatDate` helpers) are simple modal bottom sheets each ~50 lines. Implement each as inline private composables in this file (or split into a `screens/entry/components/` subfolder if the file grows past 800 lines).

- [ ] **Step 5: MerchantAutocomplete shared composable**

Dropdown-style autocomplete: as user types, debounce 300ms, call `searchProvider(prefix)` which returns a `Flow<List<Merchant>>`. Render an `ExposedDropdownMenu` with up to 10 results. Tapping a result calls `onValueChange(merchant.name)`. Free-form text input (any string) is also accepted.

- [ ] **Step 6: SplitEditorSheet**

Modal `ModalBottomSheet`. Shows a list of split rows; each row is `(amount, categoryId, notes)`. "Add row" button. Footer shows `Σ children = ৳X / parent = ৳Y`. Save button enabled when sums match (allow ±0.01 tolerance for floating point). On save → `onSave(List<NewSplitTransaction>)`.

- [ ] **Step 7: TransactionDetailScreen + ViewModel**

Read-only display of one transaction by id. Show all fields. Buttons: Edit (navigate to `entry?editId=...` — prefilled entry is a P0d follow-up; for P0c the Edit button can navigate to a stub or simply re-open EntryScreen blank), Delete (confirmation `AlertDialog` → `txnRepo.delete(id)` → `onDone()`).

`TransactionDetailViewModel(txnId, container)` exposes a `StateFlow<TransactionRowDisplay?>` (resolved with names).

- [ ] **Step 8: Wire real ImagePicker on Android**

Replace `NotImplementedError` stub in `composeApp/src/androidMain/kotlin/app/hisaab/platform/ImagePicker.kt`:

```kotlin
actual class ImagePicker(private val activity: FragmentActivity) {
    private val resultFlow = MutableSharedFlow<PickedImage?>(replay = 0, extraBufferCapacity = 1)
    private val launcher: ActivityResultLauncher<PickVisualMediaRequest> =
        activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) {
                resultFlow.tryEmit(null)
                return@registerForActivityResult
            }
            val contentResolver = activity.contentResolver
            val mime = contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) {
                resultFlow.tryEmit(null)
            } else {
                resultFlow.tryEmit(PickedImage(bytes, mime))
            }
        }

    actual fun isAvailable(): Boolean = true

    actual suspend fun pickFromGallery(): PickedImage? {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        return resultFlow.first()
    }

    actual suspend fun captureFromCamera(): PickedImage? {
        // P0d enhancement — for P0c, alias to gallery picker
        return pickFromGallery()
    }
}
```

`registerForActivityResult` MUST be called during `Activity.onCreate` (before `STARTED` state). Since `ImagePicker` is constructed by `AppContainer` (which is constructed in `MainActivity.onCreate`), this should be fine.

- [ ] **Step 9: Compile, test, commit**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --no-daemon 2>&1 | tail -10
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.screens.entry.EntryViewModelTest" \
                                         --tests "app.hisaab.screens.transaction.*" --no-daemon 2>&1 | tail -10

git add composeApp/src/commonMain/kotlin/app/hisaab/screens/entry/ \
        composeApp/src/commonMain/kotlin/app/hisaab/screens/transaction/ \
        composeApp/src/commonTest/kotlin/app/hisaab/screens/entry/ \
        composeApp/src/commonTest/kotlin/app/hisaab/screens/transaction/ \
        composeApp/src/androidMain/kotlin/app/hisaab/platform/ImagePicker.kt
git commit -m "feat(entry): EntryScreen flagship + SplitEditorSheet + MerchantAutocomplete + TransactionDetail + ImagePicker actual"
```

---

## P0c-2 verification

After Task 19, on an Android emulator from a P0c-1-completed install:

1. App opens → onboarding (or biometric unlock if previously set up) → MainGraph
2. Bottom-nav shows 4 tabs (Today / Month / People / Settings)
3. Today tab shows "No entries yet" + the editorial header
4. FAB → EntryScreen → Amount ৳150, merchant "Aarong", category Food → Save → back on Today, see the row, today's expense = ৳150
5. Tap the row → TransactionDetailScreen → Delete → confirmation → row gone
6. FAB → kind Lend → enter Karim (no phone) + ৳500 → Save → row appears with category "Lent"
7. People tab placeholder still shows (filled in P0c-3)
8. `./gradlew :composeApp:testDebugUnitTest` — all P0c-2 repository + viewmodel tests pass

## Self-review

- **Spec §6 repositories:** all 10 implemented across Tasks 10–15. ✓
- **Spec §8 screens for P0c-2:** MainGraph (Task 17), TodayScreen rewrite (Task 18), EntryScreen + TransactionDetailScreen + SplitEditorSheet + MerchantAutocomplete (Task 19). ✓
- **Architecture choice — repository-per-table + ViewModel-per-screen:** consistent across all tasks. ✓
- **Auto-create default Cash + 12 seed categories:** Task 16 wires `ensureDefaultCashAccount()` + `ensureDefaults()` into `openDatabase`. ✓
- **Splits in same `txn` table via `parent_txn_id`:** Task 12 + Task 19 SplitEditorSheet. ✓
- **Attachment E2EE:** Task 15 (AttachmentRepository) + Task 19 step 8 (ImagePicker). ✓
- **Lend/borrow form on EntryScreen pivots same UI:** Task 19 step 4 — `if (state.kind in setOf(LEND, BORROW)) { … }`. ✓
- **`LendBorrowRepository.record` returns `Pair<lendBorrowId, txnId>`:** Task 13 step 4. Note the consumer in Task 19 uses this for attachment hookup. ✓
- **No placeholders.** All code blocks complete or explicitly outlined with implementation notes. Placeholder screens for Month/People/Settings in MainGraph are intentionally labelled "P0c-3" — they're not gaps, they're the next sub-plan.
