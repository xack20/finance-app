---
# M3-1: Schema, Domain Models & Repositories Implementation Plan
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
**Goal:** Land the SMS-capture persistence foundation — `migrations/3.sqm` (three new tables + `txn.capture_id`), the capture domain models, the `NewTransaction.captureId` link field, three fully-tested repositories, and their `AppContainer` wiring — leaving the build green and all tests passing.
**Architecture:** A new SQLDelight migration adds `capture_inbox`, `sender_registry`, `capture_config` to the existing SQLCipher-encrypted `HisaabDatabase`, plus a nullable `capture_id` provenance column on `txn`. The existing P0c `NewTransaction` gains a trailing `captureId: String? = null` field so SMS auto-post (M3-3/M3-5) creates the txn ALREADY LINKED in one `add()` call — there is no separate link method. Pure Kotlin domain models in `domain/` describe captures, senders and engine config; three constructor-injected repositories in `data/` (mirroring `TransactionRepository`/`MerchantRepository`) wrap the generated queries and map rows to domain types. All three are exposed lazily on `AppContainer` exactly like the existing 10 repos. No pipeline, no LLM, no capture sources, no UI.
**Tech Stack:** Kotlin Multiplatform (commonMain), SQLDelight 2 with `deriveSchemaFromMigrations`, kotlinx-coroutines `Flow`, kotlin.test + kotlinx-coroutines-test + in-memory `JdbcSqliteDriver` for unit tests.
**Depends on:** Nothing new — builds directly on the merged P0c ledger core (migrations `1.sqm`/`2.sqm`, `HisaabDatabase`, the 10 existing repos, `AppContainer` expect/actuals). It is the first M3 slice; later M3 slices CONSUME the contract types defined here — in particular, M3-3's auto-post path calls `TransactionRepository.add(NewTransaction(..., source = TxnSource.SMS, captureId = candidate.id))` to write the linked transaction with no extra round-trip.
---

## File Structure

| File | Create/Modify | Responsibility |
|------|---------------|----------------|
| `composeApp/src/commonMain/sqldelight/migrations/3.sqm` | Create | Adds `capture_inbox`, `sender_registry`, `capture_config` tables + their indexes + `ALTER TABLE txn ADD COLUMN capture_id`. |
| `composeApp/src/commonMain/sqldelight/app/hisaab/db/CaptureInboxQueries.sq` | Create | All `capture_inbox` queries (insert, dedup lookup, observe pending/recent/count, get, status transitions, purge raw). |
| `composeApp/src/commonMain/sqldelight/app/hisaab/db/SenderQueries.sq` | Create | All `sender_registry` queries (find by sender_id, observe all, upsert, set account). |
| `composeApp/src/commonMain/sqldelight/app/hisaab/db/CaptureConfigQueries.sq` | Create | Single-row `capture_config` queries (observe, get, ensure singleton, field updates). |
| `composeApp/src/commonMain/sqldelight/app/hisaab/db/TransactionQueries.sq` | Modify | Add `capture_id` to the `insertTxn` column list + value placeholder, and to the `observeRecentTopLevel` projection (the `SELECT *` day/month queries already project the new column automatically). |
| `composeApp/src/commonMain/kotlin/app/hisaab/domain/Capture.kt` | Create | Capture domain models + enums: `RawCapture`, `CaptureChannel`, `CaptureStatus`, `ParsedBy`, `Direction`, `BankType`, `EngineMode`, `CloudProvider`, `CandidateTransaction`, `SenderMapping`, `CaptureConfig`. |
| `composeApp/src/commonMain/kotlin/app/hisaab/domain/Transaction.kt` | Modify | Add `captureId: String?` to `TransactionRow`, and add `captureId: String? = null` as the LAST field of `NewTransaction` (the M3 link field). |
| `composeApp/src/commonMain/kotlin/app/hisaab/data/TransactionRepository.kt` | Modify | Bind `capture_id = input.captureId` in the `add()` insert (`addSplits()` passes `capture_id = null` for child rows), and populate `captureId` in both `toDomain()`/`toFullDomain()` mappers. |
| `composeApp/src/commonMain/kotlin/app/hisaab/data/CaptureInboxRepository.kt` | Create | Candidate-queue repository: insert/dedup/observe/status-transition/purge. |
| `composeApp/src/commonMain/kotlin/app/hisaab/data/SenderRepository.kt` | Create | Sender-registry repository incl. `seedKnownSenders()` for bKash/Nagad/Rocket + City/BRAC/DBBL. |
| `composeApp/src/commonMain/kotlin/app/hisaab/data/CaptureConfigRepository.kt` | Create | Single-row engine/capture config repository. |
| `composeApp/src/commonMain/kotlin/app/hisaab/AppContainer.kt` | Modify | Add 3 `expect` getters. |
| `composeApp/src/androidMain/kotlin/app/hisaab/AppContainerAndroid.kt` | Modify | Add 3 `actual` lazy getters. |
| `composeApp/src/iosMain/kotlin/app/hisaab/AppContainerIos.kt` | Modify | Add 3 `actual` lazy getters. |
| `composeApp/src/wasmJsMain/kotlin/app/hisaab/AppContainerWasm.kt` | Modify | Add 3 `actual` lazy getters. |
| `composeApp/src/commonTest/kotlin/app/hisaab/data/CaptureInboxRepositoryTest.kt` | Create | Unit tests for `CaptureInboxRepository` (in-memory driver). |
| `composeApp/src/commonTest/kotlin/app/hisaab/data/SenderRepositoryTest.kt` | Create | Unit tests for `SenderRepository` incl. seed. |
| `composeApp/src/commonTest/kotlin/app/hisaab/data/CaptureConfigRepositoryTest.kt` | Create | Unit tests for `CaptureConfigRepository`. |

---

### Task 1: Migration `3.sqm` — schema for capture tables + `txn.capture_id`

Because `deriveSchemaFromMigrations=true` and `verifyMigrations=true`, the schema IS the migration files. The simplest verifiable "test" of this task is that SQLDelight can derive + verify the schema and generate code, which the existing `TransactionRepositoryTest` exercises via `HisaabDatabase.Schema.create`. We add the migration first, prove the build/codegen still works, then build query files on top.

**Files:**
- Create: `composeApp/src/commonMain/sqldelight/migrations/3.sqm`

- [ ] **Write the migration.** Create `composeApp/src/commonMain/sqldelight/migrations/3.sqm` with the complete schema (column types match the spec; `capture_config` uses a `DEFAULT 'singleton'` PK; indexes mirror the spec):

```sql
-- M3-1: SMS capture engine — candidate queue, sender registry, engine config.
-- These tables live in the same SQLCipher-encrypted DB and are excluded from
-- the sync set (only the ops-log syncs). Raw SMS is encrypted-at-rest, local-only.

-- 1. Candidate queue: every parsed candidate, posted or pending.
CREATE TABLE capture_inbox (
    id                    TEXT NOT NULL PRIMARY KEY,
    received_at           INTEGER NOT NULL,
    channel               TEXT NOT NULL,
    sender                TEXT NOT NULL,
    raw_body              TEXT NOT NULL,
    dedup_hash            TEXT NOT NULL,
    status                TEXT NOT NULL,
    confidence            REAL,
    parsed_by             TEXT,
    model                 TEXT,
    parse_error           TEXT,
    amount                REAL,
    direction             TEXT,
    currency              TEXT NOT NULL DEFAULT 'BDT',
    balance_after         REAL,
    ref_no                TEXT,
    proposed_account_id   TEXT,
    proposed_category_id  TEXT,
    proposed_merchant     TEXT,
    created_at            INTEGER NOT NULL
);
CREATE UNIQUE INDEX idx_capture_dedup ON capture_inbox(dedup_hash);
CREATE INDEX idx_capture_status ON capture_inbox(status, received_at);

-- 2. Known senders → account mapping + pre-filter allowlist (seeded, user-editable).
CREATE TABLE sender_registry (
    id            TEXT NOT NULL PRIMARY KEY,
    sender_id     TEXT NOT NULL,
    display_name  TEXT NOT NULL,
    bank_type     TEXT NOT NULL,
    is_financial  INTEGER NOT NULL DEFAULT 1,
    template_key  TEXT,
    account_id    TEXT,
    created_at    INTEGER NOT NULL
);
CREATE UNIQUE INDEX idx_sender_id ON sender_registry(sender_id);

-- 3. Single-row capture/engine config (API key itself stays in SecureStorage).
CREATE TABLE capture_config (
    id                  TEXT NOT NULL PRIMARY KEY DEFAULT 'singleton',
    capture_enabled     INTEGER NOT NULL DEFAULT 0,
    engine_mode         TEXT NOT NULL DEFAULT 'ON_DEVICE',
    on_device_model     TEXT NOT NULL DEFAULT 'auto',
    cloud_provider      TEXT,
    cloud_model         TEXT,
    redaction_enabled   INTEGER NOT NULL DEFAULT 1,
    always_review       INTEGER NOT NULL DEFAULT 0,
    auto_post_threshold REAL NOT NULL DEFAULT 0.85,
    cloud_consent_at    INTEGER,
    retain_raw_body     INTEGER NOT NULL DEFAULT 1,
    last_sms_cursor     INTEGER NOT NULL DEFAULT 0,
    updated_at          INTEGER NOT NULL
);

-- 4. Provenance link (one FK, one direction). null = manual / non-captured.
ALTER TABLE txn ADD COLUMN capture_id TEXT;
```

- [ ] **Verify migration + schema derivation (expected PASS — codegen succeeds).** Run the SQLDelight verification + generation task; it must succeed and not report a migration gap:
```bash
./gradlew :composeApp:generateCommonMainHisaabDatabaseInterface
```
Expected: `BUILD SUCCESSFUL`. (This derives schema 3 from `1.sqm`+`2.sqm`+`3.sqm`, verifies migration continuity, and regenerates `migrations.Txn` with the new `capture_id` field.)

- [ ] **Commit.**
```bash
git checkout -b m3-1-schema-domain-repos
git add composeApp/src/commonMain/sqldelight/migrations/3.sqm
git commit -m "feat: add migration 3.sqm for capture_inbox, sender_registry, capture_config + txn.capture_id"
```

---

### Task 2: `TransactionQueries.sq` + `Transaction.kt` + `TransactionRepository` — surface & write `txn.capture_id`

This task does three coupled things, all proven by the same test run:
1. **Surface** the new column on reads: the `SELECT *` day/month queries (`observeTxnsForDayRange`, `getTxn`) auto-project `capture_id`, so only the hand-listed `observeRecentTopLevel` projection needs the extra column, and `TransactionRow` gains `captureId: String?`.
2. **Write** the column: `NewTransaction` gains a trailing `captureId: String? = null` (the M3 link field per the fixed contract), the `insertTxn` query gains a 12th `capture_id` column/placeholder, and `TransactionRepository.add()` binds `capture_id = input.captureId`. SMS auto-post in M3-3 will therefore create the txn ALREADY LINKED via `add(NewTransaction(..., source = TxnSource.SMS, captureId = candidate.id))` — there is no `linkCapture`/`setCaptureId` method anywhere.
3. **Round-trip** proof: a txn added with a `captureId` surfaces as `TransactionRow.captureId` via `observeRecent`, and a manual txn surfaces `null`.

`addSplits()` writes `capture_id = null` for child rows (children are never independently capture-linked; provenance lives on the parent).

**Files:**
- Modify: `composeApp/src/commonMain/sqldelight/app/hisaab/db/TransactionQueries.sq` (the `insertTxn` query at lines 1–3 and the `observeRecentTopLevel` projection at lines 15–21)
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/domain/Transaction.kt` (the `NewTransaction` data class, ends line 18; the `TransactionRow` data class, ends line 52)
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/data/TransactionRepository.kt` (the `add()` insert at lines 52–64; the `addSplits()` insert at lines 76–88; both mappers at lines 126–164)

- [ ] **Write two failing tests** — one asserting a manual txn has `captureId == null`, one asserting a txn added with a `captureId` round-trips and surfaces it via `observeRecent`. Append both to `composeApp/src/commonTest/kotlin/app/hisaab/data/TransactionRepositoryTest.kt` (place them before the final closing `}` on line 210):
```kotlin
    @Test
    fun `manual txn has null captureId`() = runTest {
        val db = TestDatabase.create()
        val merchantRepo = MerchantRepository(db)
        val tagRepo = TagRepository(db)
        val txnRepo = TransactionRepository(db, merchantRepo, tagRepo)
        val accountId = seedAccount(db)
        txnRepo.add(
            NewTransaction(
                accountId = accountId,
                amount = 150.0,
                ts = 1000L,
                merchantName = null,
                categoryId = null,
                notes = null,
                kind = TxnKind.EXPENSE,
            ),
        )
        val row = txnRepo.observeRecent(50).first()[0]
        assertEquals(null, row.captureId)
    }

    @Test
    fun `txn added with captureId round-trips via observeRecent`() = runTest {
        val db = TestDatabase.create()
        val merchantRepo = MerchantRepository(db)
        val tagRepo = TagRepository(db)
        val txnRepo = TransactionRepository(db, merchantRepo, tagRepo)
        val accountId = seedAccount(db)
        val id = txnRepo.add(
            NewTransaction(
                accountId = accountId,
                amount = 500.0,
                ts = 2000L,
                merchantName = "bKash",
                categoryId = null,
                source = TxnSource.SMS,
                notes = null,
                kind = TxnKind.INCOME,
                captureId = "cand-42",
            ),
        )
        val row = txnRepo.observeRecent(50).first().first { it.id == id }
        assertEquals("cand-42", row.captureId)
        assertEquals(TxnSource.SMS, row.source)
    }
```

- [ ] **Run them (expected FAIL — compilation error).** `TransactionRow` has no `captureId` member and `NewTransaction` has no `captureId` parameter yet, so this fails to compile:
```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.TransactionRepositoryTest"
```
Expected: compile error `unresolved reference: captureId` (on both the `NewTransaction(...)` argument and the `row.captureId` access).

- [ ] **Add `capture_id` to the `insertTxn` query and the `observeRecentTopLevel` projection.** In `TransactionQueries.sq`, edit `insertTxn` (currently lines 1–3) to add the 12th column + placeholder:
```sql
insertTxn:
INSERT INTO txn(id, account_id, amount, currency, ts, merchant_id, category_id, source, notes, kind, parent_txn_id, capture_id)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
```
Then edit the `observeRecentTopLevel` query (currently lines 15–21) to add the column:
```sql
observeRecentTopLevel:
SELECT txn.id, txn.account_id, txn.amount, txn.currency, txn.ts, txn.merchant_id,
       txn.category_id, txn.source, txn.notes, txn.kind, txn.parent_txn_id, txn.capture_id
FROM txn
WHERE txn.parent_txn_id IS NULL
ORDER BY txn.ts DESC
LIMIT ?;
```

- [ ] **Add `captureId` to `NewTransaction` and `TransactionRow`.** In `domain/Transaction.kt`, add `captureId: String? = null` as the LAST property of `NewTransaction` (after `tagNames`):
```kotlin
data class NewTransaction(
    val accountId: String,
    val amount: Double,
    val currency: String = "BDT",
    val ts: Long,
    val merchantName: String?,
    val categoryId: String?,
    val source: TxnSource = TxnSource.MANUAL,
    val notes: String?,
    val kind: TxnKind,
    val tagNames: List<String> = emptyList(),
    val captureId: String? = null,
)
```
Then add `captureId: String? = null` as the last property of `TransactionRow` (after `parentTxnId`):
```kotlin
data class TransactionRow(
    val id: String,
    val accountId: String,
    val accountName: String,
    val amount: Double,
    val currency: String,
    val ts: Long,
    val merchantId: String?,
    val merchantName: String?,
    val categoryId: String?,
    val categoryName: String?,
    val categoryColor: String?,
    val source: TxnSource,
    val notes: String?,
    val kind: TxnKind,
    val parentTxnId: String?,
    val captureId: String? = null,
)
```

- [ ] **Bind `capture_id` in `add()` and `addSplits()`, and populate `captureId` in both mappers.** In `TransactionRepository.kt`:

In `add()` (the `insertTxn(...)` call currently ending `parent_txn_id = null,` on line 63), add the new last argument:
```kotlin
            kind = input.kind.name,
            parent_txn_id = null,
            capture_id = input.captureId,
        )
```
In `addSplits()` (the child `insertTxn(...)` call currently ending `parent_txn_id = parentId,` on line 87), child rows carry no provenance:
```kotlin
                kind = child.kind.name,
                parent_txn_id = parentId,
                capture_id = null,
            )
```
In `ObserveRecentTopLevel.toDomain()` (currently ends `parentTxnId = parent_txn_id,` on line 143), the regenerated projection now exposes `capture_id`:
```kotlin
        kind = TxnKind.valueOf(kind),
        parentTxnId = parent_txn_id,
        captureId = capture_id,
    )
```
In `migrations.Txn.toFullDomain()` (currently ends `parentTxnId = parent_txn_id,` on line 163), the `SELECT *` row now exposes `capture_id`:
```kotlin
        kind = TxnKind.valueOf(kind),
        parentTxnId = parent_txn_id,
        captureId = capture_id,
    )
```
Also update the column-order comments above each mapper to note the trailing `capture_id` column (the `toDomain` comment at lines 126–127 and the `toFullDomain` comment at line 147), appending `, capture_id` to the documented field lists.

- [ ] **Run the tests (expected PASS).**
```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.TransactionRepositoryTest"
```
Expected: `BUILD SUCCESSFUL`, all `TransactionRepositoryTest` tests green including `manual txn has null captureId` and `txn added with captureId round-trips via observeRecent`.

- [ ] **Commit.**
```bash
git add composeApp/src/commonMain/sqldelight/app/hisaab/db/TransactionQueries.sq \
        composeApp/src/commonMain/kotlin/app/hisaab/domain/Transaction.kt \
        composeApp/src/commonMain/kotlin/app/hisaab/data/TransactionRepository.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/data/TransactionRepositoryTest.kt
git commit -m "feat: add NewTransaction.captureId link field + surface txn.capture_id on TransactionRow"
```

---

### Task 3: Capture domain models + enums (`domain/Capture.kt`)

All capture domain types live in one focused file. These are pure value types (no DB, no platform). They must use the contract names verbatim. This task has no runtime test of its own — it is a compile target consumed by Tasks 4–6; correctness is proven when those repos compile and their tests pass. We verify compilation directly.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/domain/Capture.kt`

- [ ] **Write the domain file.** Create `composeApp/src/commonMain/kotlin/app/hisaab/domain/Capture.kt`:
```kotlin
package app.hisaab.domain

/** A raw captured message before any parsing. Produced by CaptureService (M3-2). */
data class RawCapture(
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val channel: CaptureChannel,
)

/** How a capture entered the app. */
enum class CaptureChannel { SMS, NOTIFICATION, PASTE }

/** Lifecycle of a capture candidate. */
enum class CaptureStatus { PENDING, AUTO_POSTED, CONFIRMED, DISMISSED }

/** Which engine produced the parse. */
enum class ParsedBy { TEMPLATE, ON_DEVICE, CLOUD_CLAUDE, CLOUD_GEMINI, CLOUD_OPENAI }

/** Money flow direction extracted from a capture. */
enum class Direction { DEBIT, CREDIT }

/** Classification of a known sender. */
enum class BankType { BKASH, NAGAD, ROCKET, BANK, CARD, OTHER }

/** User-selected parsing engine. */
enum class EngineMode { ON_DEVICE, CLOUD }

/** Selected cloud LLM vendor (when EngineMode.CLOUD). */
enum class CloudProvider { CLAUDE, GEMINI, OPENAI }

/**
 * A parsed capture candidate: the raw message plus extracted fields, status,
 * confidence, and provenance. Mirrors the capture_inbox table.
 */
data class CandidateTransaction(
    val id: String,
    val receivedAt: Long,
    val channel: CaptureChannel,
    val sender: String,
    val rawBody: String,
    val dedupHash: String,
    val status: CaptureStatus,
    val confidence: Double?,
    val parsedBy: ParsedBy?,
    val model: String?,
    val parseError: String?,
    val amount: Double?,
    val direction: Direction?,
    val currency: String,
    val balanceAfter: Double?,
    val refNo: String?,
    val proposedAccountId: String?,
    val proposedCategoryId: String?,
    val proposedMerchant: String?,
    val createdAt: Long,
)

/** A known/learned sender → account mapping + pre-filter allowlist entry. */
data class SenderMapping(
    val id: String,
    val senderId: String,
    val displayName: String,
    val bankType: BankType,
    val isFinancial: Boolean,
    val templateKey: String?,
    val accountId: String?,
    val createdAt: Long,
)

/** Single-row capture/engine configuration. API key stays in SecureStorage. */
data class CaptureConfig(
    val captureEnabled: Boolean,
    val engineMode: EngineMode,
    val onDeviceModel: String,
    val cloudProvider: CloudProvider?,
    val cloudModel: String?,
    val redactionEnabled: Boolean,
    val alwaysReview: Boolean,
    val autoPostThreshold: Double,
    val cloudConsentAt: Long?,
    val retainRawBody: Boolean,
    val lastSmsCursor: Long,
    val updatedAt: Long,
)
```

- [ ] **Verify it compiles (expected PASS).** Compile the common metadata so the new file is type-checked without needing a consumer yet:
```bash
./gradlew :composeApp:compileKotlinMetadata
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Commit.**
```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/domain/Capture.kt
git commit -m "feat: add capture domain models and enums"
```

---

### Task 4: `CaptureInboxQueries.sq` + `CaptureInboxRepository` + tests

The candidate-queue repository. Insert is dedup-aware (`INSERT OR IGNORE` on the unique `dedup_hash` index → no-op on duplicates, per spec §5). Status transitions are simple `UPDATE`s; `purgeRaw` blanks the body. IDs come from the candidate itself (the pipeline generates them), consistent with the contract signature `insertCandidate(c: CandidateTransaction)`.

**Files:**
- Create: `composeApp/src/commonMain/sqldelight/app/hisaab/db/CaptureInboxQueries.sq`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/data/CaptureInboxRepository.kt`
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/data/CaptureInboxRepositoryTest.kt`

- [ ] **Write the failing test file.** Create `composeApp/src/commonTest/kotlin/app/hisaab/data/CaptureInboxRepositoryTest.kt`:
```kotlin
package app.hisaab.data

import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import app.hisaab.domain.Direction
import app.hisaab.domain.ParsedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureInboxRepositoryTest {

    private fun candidate(
        id: String = "cand-1",
        dedupHash: String = "hash-1",
        status: CaptureStatus = CaptureStatus.PENDING,
        receivedAt: Long = 1_000L,
    ) = CandidateTransaction(
        id = id,
        receivedAt = receivedAt,
        channel = CaptureChannel.SMS,
        sender = "bKash",
        rawBody = "You have received Tk 500 from 01700000000. TrxID ABC123",
        dedupHash = dedupHash,
        status = status,
        confidence = 0.92,
        parsedBy = ParsedBy.TEMPLATE,
        model = null,
        parseError = null,
        amount = 500.0,
        direction = Direction.CREDIT,
        currency = "BDT",
        balanceAfter = 1500.0,
        refNo = "ABC123",
        proposedAccountId = "acc-1",
        proposedCategoryId = "cat-1",
        proposedMerchant = "bKash",
        createdAt = 2_000L,
    )

    @Test
    fun `insertCandidate then getById round-trips all fields`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureInboxRepository(db)
        repo.insertCandidate(candidate())
        val loaded = repo.getById("cand-1")
        assertNotNull(loaded)
        assertEquals(500.0, loaded.amount)
        assertEquals(Direction.CREDIT, loaded.direction)
        assertEquals(ParsedBy.TEMPLATE, loaded.parsedBy)
        assertEquals(CaptureStatus.PENDING, loaded.status)
        assertEquals("ABC123", loaded.refNo)
        assertEquals(0.92, loaded.confidence)
        assertEquals("bKash", loaded.proposedMerchant)
    }

    @Test
    fun `insertCandidate is a no-op on duplicate dedupHash`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureInboxRepository(db)
        repo.insertCandidate(candidate(id = "cand-1", dedupHash = "dup"))
        repo.insertCandidate(candidate(id = "cand-2", dedupHash = "dup"))
        assertNotNull(repo.getById("cand-1"))
        assertNull(repo.getById("cand-2"))
    }

    @Test
    fun `findByDedupHash returns the matching candidate`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureInboxRepository(db)
        repo.insertCandidate(candidate(dedupHash = "findme"))
        val found = repo.findByDedupHash("findme")
        assertNotNull(found)
        assertEquals("cand-1", found.id)
        assertNull(repo.findByDedupHash("missing"))
    }

    @Test
    fun `observePending only returns PENDING ordered by receivedAt desc`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureInboxRepository(db)
        repo.insertCandidate(candidate(id = "a", dedupHash = "a", receivedAt = 100))
        repo.insertCandidate(candidate(id = "b", dedupHash = "b", receivedAt = 300))
        repo.insertCandidate(
            candidate(id = "c", dedupHash = "c", status = CaptureStatus.AUTO_POSTED, receivedAt = 200),
        )
        val pending = repo.observePending().first()
        assertEquals(listOf("b", "a"), pending.map { it.id })
    }

    @Test
    fun `observePendingCount reflects PENDING rows only`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureInboxRepository(db)
        repo.insertCandidate(candidate(id = "a", dedupHash = "a"))
        repo.insertCandidate(
            candidate(id = "b", dedupHash = "b", status = CaptureStatus.CONFIRMED),
        )
        assertEquals(1L, repo.observePendingCount().first())
    }

    @Test
    fun `observeRecent returns newest first up to limit across all statuses`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureInboxRepository(db)
        repo.insertCandidate(candidate(id = "a", dedupHash = "a", receivedAt = 100))
        repo.insertCandidate(
            candidate(id = "b", dedupHash = "b", status = CaptureStatus.DISMISSED, receivedAt = 300),
        )
        repo.insertCandidate(candidate(id = "c", dedupHash = "c", receivedAt = 200))
        val recent = repo.observeRecent(2).first()
        assertEquals(listOf("b", "c"), recent.map { it.id })
    }

    @Test
    fun `markAutoPosted markConfirmed markDismissed transition status`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureInboxRepository(db)
        repo.insertCandidate(candidate(id = "a", dedupHash = "a"))
        repo.markAutoPosted("a")
        assertEquals(CaptureStatus.AUTO_POSTED, repo.getById("a")?.status)
        repo.markConfirmed("a")
        assertEquals(CaptureStatus.CONFIRMED, repo.getById("a")?.status)
        repo.markDismissed("a")
        assertEquals(CaptureStatus.DISMISSED, repo.getById("a")?.status)
    }

    @Test
    fun `purgeRaw blanks the raw body`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureInboxRepository(db)
        repo.insertCandidate(candidate(id = "a", dedupHash = "a"))
        repo.purgeRaw("a")
        val loaded = repo.getById("a")
        assertNotNull(loaded)
        assertTrue(loaded.rawBody.isEmpty())
    }
}
```

- [ ] **Run it (expected FAIL — `CaptureInboxRepository` does not exist).**
```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.CaptureInboxRepositoryTest"
```
Expected: compile error `unresolved reference: CaptureInboxRepository`.

- [ ] **Write the queries file.** Create `composeApp/src/commonMain/sqldelight/app/hisaab/db/CaptureInboxQueries.sq`:
```sql
insertCandidate:
INSERT OR IGNORE INTO capture_inbox(
    id, received_at, channel, sender, raw_body, dedup_hash, status, confidence,
    parsed_by, model, parse_error, amount, direction, currency, balance_after,
    ref_no, proposed_account_id, proposed_category_id, proposed_merchant, created_at
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

findByDedupHash:
SELECT * FROM capture_inbox WHERE dedup_hash = ? LIMIT 1;

getById:
SELECT * FROM capture_inbox WHERE id = ? LIMIT 1;

observePending:
SELECT * FROM capture_inbox WHERE status = 'PENDING' ORDER BY received_at DESC;

observeRecent:
SELECT * FROM capture_inbox ORDER BY received_at DESC LIMIT ?;

observePendingCount:
SELECT COUNT(*) FROM capture_inbox WHERE status = 'PENDING';

updateStatus:
UPDATE capture_inbox SET status = ? WHERE id = ?;

purgeRaw:
UPDATE capture_inbox SET raw_body = '' WHERE id = ?;
```

- [ ] **Write the repository.** Create `composeApp/src/commonMain/kotlin/app/hisaab/data/CaptureInboxRepository.kt`:
```kotlin
package app.hisaab.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import app.hisaab.domain.Direction
import app.hisaab.domain.ParsedBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CaptureInboxRepository(private val db: HisaabDatabase) {

    private val queries get() = db.captureInboxQueriesQueries

    suspend fun insertCandidate(c: CandidateTransaction) {
        queries.insertCandidate(
            id = c.id,
            received_at = c.receivedAt,
            channel = c.channel.name,
            sender = c.sender,
            raw_body = c.rawBody,
            dedup_hash = c.dedupHash,
            status = c.status.name,
            confidence = c.confidence,
            parsed_by = c.parsedBy?.name,
            model = c.model,
            parse_error = c.parseError,
            amount = c.amount,
            direction = c.direction?.name,
            currency = c.currency,
            balance_after = c.balanceAfter,
            ref_no = c.refNo,
            proposed_account_id = c.proposedAccountId,
            proposed_category_id = c.proposedCategoryId,
            proposed_merchant = c.proposedMerchant,
            created_at = c.createdAt,
        )
    }

    suspend fun findByDedupHash(hash: String): CandidateTransaction? =
        queries.findByDedupHash(hash).executeAsOneOrNull()?.toDomain()

    fun observePending(): Flow<List<CandidateTransaction>> =
        queries.observePending().asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    fun observeRecent(limit: Int): Flow<List<CandidateTransaction>> =
        queries.observeRecent(limit.toLong()).asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    fun observePendingCount(): Flow<Long> =
        queries.observePendingCount().asFlow().mapToOne(Dispatchers.Default)

    suspend fun getById(id: String): CandidateTransaction? =
        queries.getById(id).executeAsOneOrNull()?.toDomain()

    suspend fun markAutoPosted(id: String) {
        queries.updateStatus(CaptureStatus.AUTO_POSTED.name, id)
    }

    suspend fun markConfirmed(id: String) {
        queries.updateStatus(CaptureStatus.CONFIRMED.name, id)
    }

    suspend fun markDismissed(id: String) {
        queries.updateStatus(CaptureStatus.DISMISSED.name, id)
    }

    suspend fun purgeRaw(id: String) {
        queries.purgeRaw(id)
    }

    private fun migrations.Capture_inbox.toDomain(): CandidateTransaction = CandidateTransaction(
        id = id,
        receivedAt = received_at,
        channel = CaptureChannel.valueOf(channel),
        sender = sender,
        rawBody = raw_body,
        dedupHash = dedup_hash,
        status = CaptureStatus.valueOf(status),
        confidence = confidence,
        parsedBy = parsed_by?.let { ParsedBy.valueOf(it) },
        model = model,
        parseError = parse_error,
        amount = amount,
        direction = direction?.let { Direction.valueOf(it) },
        currency = currency,
        balanceAfter = balance_after,
        refNo = ref_no,
        proposedAccountId = proposed_account_id,
        proposedCategoryId = proposed_category_id,
        proposedMerchant = proposed_merchant,
        createdAt = created_at,
    )
}
```
> Note: the generated row class for `SELECT *` on `capture_inbox` is `migrations.Capture_inbox` (SQLDelight capitalizes the first segment of the snake_case table name when `deriveSchemaFromMigrations=true`, exactly like `migrations.Txn` and `migrations.Merchant`). The queries accessor is `db.captureInboxQueriesQueries` (file `CaptureInboxQueries.sq` → `captureInboxQueriesQueries`, mirroring `TransactionQueries.sq` → `transactionQueriesQueries`).

- [ ] **Run the test (expected PASS).**
```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.CaptureInboxRepositoryTest"
```
Expected: `BUILD SUCCESSFUL`, all 8 tests green. If the generated class name differs (compile error `unresolved reference: Capture_inbox`), inspect the generated source under `composeApp/build/generated/sqldelight/code/HisaabDatabase/commonMain/migrations/` and adjust the mapper receiver type to match the actual generated name, then re-run.

- [ ] **Commit.**
```bash
git add composeApp/src/commonMain/sqldelight/app/hisaab/db/CaptureInboxQueries.sq \
        composeApp/src/commonMain/kotlin/app/hisaab/data/CaptureInboxRepository.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/data/CaptureInboxRepositoryTest.kt
git commit -m "feat: add CaptureInboxRepository + queries with dedup-aware insert"
```

---

### Task 5: `SenderQueries.sq` + `SenderRepository` (incl. `seedKnownSenders()`) + tests

The sender-registry repository. `upsert` keys on the unique `sender_id` index; `setAccount` patches the mapped account; `seedKnownSenders()` is idempotent and inserts the BD MFS + bank senders from the spec (bKash/Nagad/Rocket + City/BRAC/DBBL) without overwriting user edits. IDs are generated by the repo (same hex-random pattern as the other repos), `createdAt` from `Clock`.

**Files:**
- Create: `composeApp/src/commonMain/sqldelight/app/hisaab/db/SenderQueries.sq`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/data/SenderRepository.kt`
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/data/SenderRepositoryTest.kt`

- [ ] **Write the failing test file.** Create `composeApp/src/commonTest/kotlin/app/hisaab/data/SenderRepositoryTest.kt`:
```kotlin
package app.hisaab.data

import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.BankType
import app.hisaab.domain.SenderMapping
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SenderRepositoryTest {

    private fun mapping(
        id: String = "s-1",
        senderId: String = "bKash",
        bankType: BankType = BankType.BKASH,
        accountId: String? = null,
    ) = SenderMapping(
        id = id,
        senderId = senderId,
        displayName = "bKash",
        bankType = bankType,
        isFinancial = true,
        templateKey = "bkash",
        accountId = accountId,
        createdAt = 1_000L,
    )

    @Test
    fun `upsert then findBySenderId round-trips`() = runTest {
        val db = TestDatabase.create()
        val repo = SenderRepository(db)
        repo.upsert(mapping())
        val found = repo.findBySenderId("bKash")
        assertNotNull(found)
        assertEquals(BankType.BKASH, found.bankType)
        assertEquals("bkash", found.templateKey)
        assertTrue(found.isFinancial)
        assertNull(repo.findBySenderId("UNKNOWN"))
    }

    @Test
    fun `upsert on existing senderId updates the row`() = runTest {
        val db = TestDatabase.create()
        val repo = SenderRepository(db)
        repo.upsert(mapping(id = "s-1", senderId = "NAGAD", bankType = BankType.NAGAD))
        repo.upsert(mapping(id = "s-2", senderId = "NAGAD", bankType = BankType.OTHER))
        val all = repo.observeAll().first().filter { it.senderId == "NAGAD" }
        assertEquals(1, all.size)
        assertEquals(BankType.OTHER, all[0].bankType)
    }

    @Test
    fun `setAccount maps an account to a sender`() = runTest {
        val db = TestDatabase.create()
        val repo = SenderRepository(db)
        repo.upsert(mapping(senderId = "bKash"))
        repo.setAccount("bKash", "acc-99")
        assertEquals("acc-99", repo.findBySenderId("bKash")?.accountId)
    }

    @Test
    fun `seedKnownSenders inserts bKash Nagad Rocket and three banks`() = runTest {
        val db = TestDatabase.create()
        val repo = SenderRepository(db)
        repo.seedKnownSenders()
        val all = repo.observeAll().first()
        val ids = all.map { it.senderId }.toSet()
        assertTrue("bKash" in ids)
        assertTrue("NAGAD" in ids)
        assertTrue("Rocket" in ids)
        assertTrue(ids.any { it.contains("CITY", ignoreCase = true) })
        assertTrue(ids.any { it.contains("BRAC", ignoreCase = true) })
        assertTrue(ids.any { it.contains("DBBL", ignoreCase = true) })
        assertEquals(BankType.BKASH, repo.findBySenderId("bKash")?.bankType)
        assertEquals(BankType.NAGAD, repo.findBySenderId("NAGAD")?.bankType)
        assertEquals(BankType.ROCKET, repo.findBySenderId("Rocket")?.bankType)
    }

    @Test
    fun `seedKnownSenders is idempotent and preserves user account mapping`() = runTest {
        val db = TestDatabase.create()
        val repo = SenderRepository(db)
        repo.seedKnownSenders()
        repo.setAccount("bKash", "acc-user")
        repo.seedKnownSenders()
        val all = repo.observeAll().first()
        assertEquals(all.size, all.map { it.senderId }.toSet().size) // no duplicate senderIds
        assertEquals("acc-user", repo.findBySenderId("bKash")?.accountId)
    }
}
```

- [ ] **Run it (expected FAIL — `SenderRepository` does not exist).**
```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.SenderRepositoryTest"
```
Expected: compile error `unresolved reference: SenderRepository`.

- [ ] **Write the queries file.** Create `composeApp/src/commonMain/sqldelight/app/hisaab/db/SenderQueries.sq`. `upsert` uses `INSERT ... ON CONFLICT(sender_id) DO UPDATE` so re-inserting the same `sender_id` updates the descriptive fields but never touches `account_id` (preserving user mappings — also what keeps `seedKnownSenders()` idempotent):
```sql
findBySenderId:
SELECT * FROM sender_registry WHERE sender_id = ? LIMIT 1;

observeAll:
SELECT * FROM sender_registry ORDER BY display_name ASC;

upsertSender:
INSERT INTO sender_registry(
    id, sender_id, display_name, bank_type, is_financial, template_key, account_id, created_at
) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(sender_id) DO UPDATE SET
    display_name = excluded.display_name,
    bank_type = excluded.bank_type,
    is_financial = excluded.is_financial,
    template_key = excluded.template_key;

setAccount:
UPDATE sender_registry SET account_id = ? WHERE sender_id = ?;
```
> Note: `upsertSender`'s `DO UPDATE` deliberately omits `account_id`, so a user's mapped account survives both `upsert` and `seedKnownSenders`. The `setAccount` query is the only path that changes `account_id`.

- [ ] **Write the repository.** Create `composeApp/src/commonMain/kotlin/app/hisaab/data/SenderRepository.kt`:
```kotlin
package app.hisaab.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.BankType
import app.hisaab.domain.SenderMapping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.random.Random

class SenderRepository(private val db: HisaabDatabase) {

    private val queries get() = db.senderQueriesQueries

    suspend fun findBySenderId(senderId: String): SenderMapping? =
        queries.findBySenderId(senderId).executeAsOneOrNull()?.toDomain()

    fun observeAll(): Flow<List<SenderMapping>> =
        queries.observeAll().asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    suspend fun upsert(m: SenderMapping) {
        queries.upsertSender(
            id = m.id.ifBlank { randomId() },
            sender_id = m.senderId,
            display_name = m.displayName,
            bank_type = m.bankType.name,
            is_financial = if (m.isFinancial) 1L else 0L,
            template_key = m.templateKey,
            account_id = m.accountId,
            created_at = if (m.createdAt > 0L) m.createdAt else now(),
        )
    }

    suspend fun setAccount(senderId: String, accountId: String) {
        queries.setAccount(accountId, senderId)
    }

    suspend fun seedKnownSenders() {
        SEED.forEach { seed ->
            if (queries.findBySenderId(seed.senderId).executeAsOneOrNull() == null) {
                queries.upsertSender(
                    id = randomId(),
                    sender_id = seed.senderId,
                    display_name = seed.displayName,
                    bank_type = seed.bankType.name,
                    is_financial = 1L,
                    template_key = seed.templateKey,
                    account_id = null,
                    created_at = now(),
                )
            }
        }
    }

    private fun migrations.Sender_registry.toDomain(): SenderMapping = SenderMapping(
        id = id,
        senderId = sender_id,
        displayName = display_name,
        bankType = BankType.valueOf(bank_type),
        isFinancial = is_financial == 1L,
        templateKey = template_key,
        accountId = account_id,
        createdAt = created_at,
    )

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private fun randomId(): String {
        val bytes = Random.Default.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    private data class Seed(
        val senderId: String,
        val displayName: String,
        val bankType: BankType,
        val templateKey: String?,
    )

    private companion object {
        // Known BD MFS + bank SMS sender IDs. Template keys consumed by M3-3 BankTemplates.
        val SEED = listOf(
            Seed("bKash", "bKash", BankType.BKASH, "bkash"),
            Seed("NAGAD", "Nagad", BankType.NAGAD, "nagad"),
            Seed("Rocket", "Rocket", BankType.ROCKET, "rocket"),
            Seed("CITY BANK", "City Bank", BankType.BANK, "city"),
            Seed("BRAC BANK", "BRAC Bank", BankType.BANK, "brac"),
            Seed("DBBL", "Dutch-Bangla Bank", BankType.BANK, "dbbl"),
        )
    }
}
```
> Note: the generated row class for `SELECT *` on `sender_registry` is `migrations.Sender_registry`; the queries accessor is `db.senderQueriesQueries` (file `SenderQueries.sq`). `kotlinx.datetime.Clock` is already used by `AccountRepository`, so it is available in commonMain.

- [ ] **Run the test (expected PASS).**
```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.SenderRepositoryTest"
```
Expected: `BUILD SUCCESSFUL`, all 5 tests green. (If the generated class name differs, adjust the `migrations.Sender_registry` receiver per the inspection note in Task 4.)

- [ ] **Commit.**
```bash
git add composeApp/src/commonMain/sqldelight/app/hisaab/db/SenderQueries.sq \
        composeApp/src/commonMain/kotlin/app/hisaab/data/SenderRepository.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/data/SenderRepositoryTest.kt
git commit -m "feat: add SenderRepository + queries with idempotent BD sender seed"
```

---

### Task 6: `CaptureConfigQueries.sq` + `CaptureConfigRepository` + tests

A single-row config table. The repo lazily ensures the singleton row exists (insert-or-ignore on first read/write), then `observe()`/`get()` return the typed `CaptureConfig`; each setter touches one field plus `updated_at`. Defaults match the migration (`engineMode=ON_DEVICE`, `autoPostThreshold=0.85`, `redactionEnabled=true`, etc.).

**Files:**
- Create: `composeApp/src/commonMain/sqldelight/app/hisaab/db/CaptureConfigQueries.sq`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/data/CaptureConfigRepository.kt`
- Create: `composeApp/src/commonTest/kotlin/app/hisaab/data/CaptureConfigRepositoryTest.kt`

- [ ] **Write the failing test file.** Create `composeApp/src/commonTest/kotlin/app/hisaab/data/CaptureConfigRepositoryTest.kt`:
```kotlin
package app.hisaab.data

import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.CloudProvider
import app.hisaab.domain.EngineMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureConfigRepositoryTest {

    @Test
    fun `get returns defaults on a fresh db`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureConfigRepository(db)
        val cfg = repo.get()
        assertFalse(cfg.captureEnabled)
        assertEquals(EngineMode.ON_DEVICE, cfg.engineMode)
        assertEquals("auto", cfg.onDeviceModel)
        assertNull(cfg.cloudProvider)
        assertTrue(cfg.redactionEnabled)
        assertFalse(cfg.alwaysReview)
        assertEquals(0.85, cfg.autoPostThreshold)
        assertNull(cfg.cloudConsentAt)
        assertTrue(cfg.retainRawBody)
        assertEquals(0L, cfg.lastSmsCursor)
    }

    @Test
    fun `setEngineMode persists`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureConfigRepository(db)
        repo.setEngineMode(EngineMode.CLOUD)
        assertEquals(EngineMode.CLOUD, repo.get().engineMode)
    }

    @Test
    fun `setCloudProvider persists provider and model`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureConfigRepository(db)
        repo.setCloudProvider(CloudProvider.CLAUDE, "claude-sonnet")
        val cfg = repo.get()
        assertEquals(CloudProvider.CLAUDE, cfg.cloudProvider)
        assertEquals("claude-sonnet", cfg.cloudModel)
        repo.setCloudProvider(null, null)
        val cleared = repo.get()
        assertNull(cleared.cloudProvider)
        assertNull(cleared.cloudModel)
    }

    @Test
    fun `setRedaction setAlwaysReview setCaptureEnabled toggle booleans`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureConfigRepository(db)
        repo.setRedaction(false)
        repo.setAlwaysReview(true)
        repo.setCaptureEnabled(true)
        val cfg = repo.get()
        assertFalse(cfg.redactionEnabled)
        assertTrue(cfg.alwaysReview)
        assertTrue(cfg.captureEnabled)
    }

    @Test
    fun `setAutoPostThreshold persists`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureConfigRepository(db)
        repo.setAutoPostThreshold(0.5)
        assertEquals(0.5, repo.get().autoPostThreshold)
    }

    @Test
    fun `recordConsent and setCursor persist`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureConfigRepository(db)
        repo.recordConsent(12_345L)
        repo.setCursor(99_999L)
        val cfg = repo.get()
        assertEquals(12_345L, cfg.cloudConsentAt)
        assertEquals(99_999L, cfg.lastSmsCursor)
        repo.clearConsent()
        assertEquals(null, repo.get().cloudConsentAt)
    }

    @Test
    fun `observe reflects updates`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureConfigRepository(db)
        repo.setEngineMode(EngineMode.CLOUD)
        assertEquals(EngineMode.CLOUD, repo.observe().first().engineMode)
    }
}
```

- [ ] **Run it (expected FAIL — `CaptureConfigRepository` does not exist).**
```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.CaptureConfigRepositoryTest"
```
Expected: compile error `unresolved reference: CaptureConfigRepository`.

- [ ] **Write the queries file.** Create `composeApp/src/commonMain/sqldelight/app/hisaab/db/CaptureConfigQueries.sq`. The singleton is materialized via `INSERT OR IGNORE` against the `'singleton'` PK default; every setter also stamps `updated_at`:
```sql
ensureSingleton:
INSERT OR IGNORE INTO capture_config(id, updated_at) VALUES ('singleton', ?);

getConfig:
SELECT * FROM capture_config WHERE id = 'singleton' LIMIT 1;

observeConfig:
SELECT * FROM capture_config WHERE id = 'singleton' LIMIT 1;

setEngineMode:
UPDATE capture_config SET engine_mode = ?, updated_at = ? WHERE id = 'singleton';

setCloudProvider:
UPDATE capture_config SET cloud_provider = ?, cloud_model = ?, updated_at = ? WHERE id = 'singleton';

setRedaction:
UPDATE capture_config SET redaction_enabled = ?, updated_at = ? WHERE id = 'singleton';

setAlwaysReview:
UPDATE capture_config SET always_review = ?, updated_at = ? WHERE id = 'singleton';

setAutoPostThreshold:
UPDATE capture_config SET auto_post_threshold = ?, updated_at = ? WHERE id = 'singleton';

setCaptureEnabled:
UPDATE capture_config SET capture_enabled = ?, updated_at = ? WHERE id = 'singleton';

recordConsent:
UPDATE capture_config SET cloud_consent_at = ?, updated_at = ? WHERE id = 'singleton';

clearConsent:
UPDATE capture_config SET cloud_consent_at = NULL, updated_at = ? WHERE id = 'singleton';

setCursor:
UPDATE capture_config SET last_sms_cursor = ?, updated_at = ? WHERE id = 'singleton';
```

- [ ] **Write the repository.** Create `composeApp/src/commonMain/kotlin/app/hisaab/data/CaptureConfigRepository.kt`. `ensure()` is called before every read/write so the singleton always exists; mappers translate the row to `CaptureConfig`:
```kotlin
package app.hisaab.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.CaptureConfig
import app.hisaab.domain.CloudProvider
import app.hisaab.domain.EngineMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.Clock

class CaptureConfigRepository(private val db: HisaabDatabase) {

    private val queries get() = db.captureConfigQueriesQueries

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private fun ensure() {
        queries.ensureSingleton(now())
    }

    fun observe(): Flow<CaptureConfig> =
        queries.observeConfig().asFlow()
            .onStart { ensure() }
            .mapToOne(Dispatchers.Default)
            .map { it.toDomain() }

    suspend fun get(): CaptureConfig {
        ensure()
        return queries.getConfig().executeAsOne().toDomain()
    }

    suspend fun setEngineMode(m: EngineMode) {
        ensure()
        queries.setEngineMode(m.name, now())
    }

    suspend fun setCloudProvider(p: CloudProvider?, model: String?) {
        ensure()
        queries.setCloudProvider(p?.name, model, now())
    }

    suspend fun setRedaction(enabled: Boolean) {
        ensure()
        queries.setRedaction(if (enabled) 1L else 0L, now())
    }

    suspend fun setAlwaysReview(enabled: Boolean) {
        ensure()
        queries.setAlwaysReview(if (enabled) 1L else 0L, now())
    }

    suspend fun setAutoPostThreshold(t: Double) {
        ensure()
        queries.setAutoPostThreshold(t, now())
    }

    suspend fun setCaptureEnabled(enabled: Boolean) {
        ensure()
        queries.setCaptureEnabled(if (enabled) 1L else 0L, now())
    }

    suspend fun recordConsent(ts: Long) {
        ensure()
        queries.recordConsent(ts, now())
    }

    suspend fun clearConsent() {
        ensure()
        queries.clearConsent(now())
    }

    suspend fun setCursor(ts: Long) {
        ensure()
        queries.setCursor(ts, now())
    }

    private fun migrations.Capture_config.toDomain(): CaptureConfig = CaptureConfig(
        captureEnabled = capture_enabled == 1L,
        engineMode = EngineMode.valueOf(engine_mode),
        onDeviceModel = on_device_model,
        cloudProvider = cloud_provider?.let { CloudProvider.valueOf(it) },
        cloudModel = cloud_model,
        redactionEnabled = redaction_enabled == 1L,
        alwaysReview = always_review == 1L,
        autoPostThreshold = auto_post_threshold,
        cloudConsentAt = cloud_consent_at,
        retainRawBody = retain_raw_body == 1L,
        lastSmsCursor = last_sms_cursor,
        updatedAt = updated_at,
    )
}
```
> Note: generated row class is `migrations.Capture_config`; accessor `db.captureConfigQueriesQueries`. `onStart { ensure() }` guarantees the singleton exists before the first emission of the `observe()` flow.

- [ ] **Run the test (expected PASS).**
```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.CaptureConfigRepositoryTest"
```
Expected: `BUILD SUCCESSFUL`, all 7 tests green. (Adjust `migrations.Capture_config` receiver if the generated name differs, per Task 4's inspection note.)

- [ ] **Commit.**
```bash
git add composeApp/src/commonMain/sqldelight/app/hisaab/db/CaptureConfigQueries.sq \
        composeApp/src/commonMain/kotlin/app/hisaab/data/CaptureConfigRepository.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/data/CaptureConfigRepositoryTest.kt
git commit -m "feat: add CaptureConfigRepository + queries with singleton row"
```

---

### Task 7: Wire the three repos into `AppContainer` (expect + 3 actuals)

Add `expect` getters to the common `AppContainer` and `actual` lazy getters to all three platform actuals, exactly mirroring the existing repo getters (`get() = Repo(requireDb())`). All three repos take only `HisaabDatabase`. No test is added here (these are DI getters with no logic); the build/compile across all targets is the verification.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/AppContainer.kt` (imports + `expect class` body, near lines 8–17 imports and 52–61 getters)
- Modify: `composeApp/src/androidMain/kotlin/app/hisaab/AppContainerAndroid.kt` (imports + getters near lines 11–20 and 57–82)
- Modify: `composeApp/src/iosMain/kotlin/app/hisaab/AppContainerIos.kt` (imports + getters near lines 9–18 and 52–77)
- Modify: `composeApp/src/wasmJsMain/kotlin/app/hisaab/AppContainerWasm.kt` (imports + getters near lines 9–18 and 52–77)

- [ ] **Add `expect` getters + imports in `AppContainer.kt`.** Add the three imports alongside the existing `app.hisaab.data.*` imports:
```kotlin
import app.hisaab.data.CaptureConfigRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.SenderRepository
```
Then add the three `expect` getters inside the `expect class AppContainer` body, immediately after `val insightRepository: InsightRepository` (line 60):
```kotlin
    val captureInboxRepository: CaptureInboxRepository
    val senderRepository: SenderRepository
    val captureConfigRepository: CaptureConfigRepository
```

- [ ] **Add `actual` getters + imports in `AppContainerAndroid.kt`.** Add the three imports alongside the existing `app.hisaab.data.*` imports, then add the getters after `actual val insightRepository: ...` block (after line 82):
```kotlin
    actual val captureInboxRepository: CaptureInboxRepository
        get() = CaptureInboxRepository(requireDb())
    actual val senderRepository: SenderRepository
        get() = SenderRepository(requireDb())
    actual val captureConfigRepository: CaptureConfigRepository
        get() = CaptureConfigRepository(requireDb())
```

- [ ] **Add `actual` getters + imports in `AppContainerIos.kt`.** Same three imports, and add the getters after `actual val insightRepository: ...` (after line 77):
```kotlin
    actual val captureInboxRepository: CaptureInboxRepository
        get() = CaptureInboxRepository(requireDb())
    actual val senderRepository: SenderRepository
        get() = SenderRepository(requireDb())
    actual val captureConfigRepository: CaptureConfigRepository
        get() = CaptureConfigRepository(requireDb())
```

- [ ] **Add `actual` getters + imports in `AppContainerWasm.kt`.** Same three imports and the same three getter blocks after `actual val insightRepository: ...` (after line 77):
```kotlin
    actual val captureInboxRepository: CaptureInboxRepository
        get() = CaptureInboxRepository(requireDb())
    actual val senderRepository: SenderRepository
        get() = SenderRepository(requireDb())
    actual val captureConfigRepository: CaptureConfigRepository
        get() = CaptureConfigRepository(requireDb())
```

- [ ] **Verify Android compiles (expected PASS).** The Android target compiles common + androidMain + the expect/actual match:
```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Verify the common metadata + (if toolchain available) other targets compile (expected PASS).** This catches any iOS/wasm expect/actual mismatch:
```bash
./gradlew :composeApp:compileKotlinMetadata
```
Expected: `BUILD SUCCESSFUL`. (If the environment has the iOS/wasm toolchains configured, also run `./gradlew :composeApp:compileKotlinWasmJs` to confirm the wasm actual; skip if the target is unavailable in this environment.)

- [ ] **Commit.**
```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/AppContainer.kt \
        composeApp/src/androidMain/kotlin/app/hisaab/AppContainerAndroid.kt \
        composeApp/src/iosMain/kotlin/app/hisaab/AppContainerIos.kt \
        composeApp/src/wasmJsMain/kotlin/app/hisaab/AppContainerWasm.kt
git commit -m "feat: expose capture inbox, sender, and config repositories on AppContainer"
```

---

### Task: Full verification

Run the complete unit-test suite and the Android debug assembly to confirm the slice leaves the build green and every test passing on its own.

**Files:** none (verification only).

- [ ] **Run the full unit-test suite (expected PASS).**
```bash
./gradlew :composeApp:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL` — all existing tests plus the new `CaptureInboxRepositoryTest` (8), `SenderRepositoryTest` (5), `CaptureConfigRepositoryTest` (7), and the two added `TransactionRepositoryTest` cases (`manual txn has null captureId`, `txn added with captureId round-trips via observeRecent`) all green.

- [ ] **Assemble the Android debug APK (expected PASS).**
```bash
./gradlew :composeApp:assembleDebug
```
Expected: `BUILD SUCCESSFUL` — migration `3.sqm` derives + verifies, all generated query interfaces compile, and the new repos + `AppContainer` wiring build into the app.

- [ ] **Final confirmation.** Confirm the working tree is clean and all task commits are present:
```bash
git status
git log --oneline -8
```
Expected: clean tree; commits for migration, `NewTransaction.captureId` + `capture_id` surfacing, domain models, the three repos, and AppContainer wiring all on branch `m3-1-schema-domain-repos`.

---

**Notes for the executing engineer:**
- The SQLDelight generated row class for any `SELECT *` is `migrations.<Pascal_snake>` — verified against the existing `migrations.Txn`, `migrations.Merchant`, `migrations.Account` usages. For the new tables that is `migrations.Capture_inbox`, `migrations.Sender_registry`, `migrations.Capture_config`. If codegen produces a different casing, inspect `composeApp/build/generated/sqldelight/code/HisaabDatabase/commonMain/migrations/` and adjust the mapper receiver types only.
- Queries-accessor naming is `db.<fileNameCamel>Queries` where the file is `<X>Queries.sq` → accessor `db.<x>QueriesQueries` (e.g. `CaptureInboxQueries.sq` → `db.captureInboxQueriesQueries`), exactly as `TransactionQueries.sq` → `db.transactionQueriesQueries`.
- `NewTransaction.captureId` is the ONLY linking mechanism in M3: the SMS auto-post path in M3-3 calls `TransactionRepository.add(NewTransaction(..., source = TxnSource.SMS, captureId = candidate.id))` so the txn is written ALREADY LINKED in the same insert. There is deliberately no `linkCapture`/`setCaptureId` query or repository method — do not add one.
- IDs follow the existing hex-`Random.nextBytes(16)` repo pattern — no `java.util.UUID` in commonMain.
- All new tests use the in-memory `JdbcSqliteDriver` via `TestDatabase.create()`; no libsodium/device dependency, so they run in `commonTest`/JVM cleanly.

Relevant absolute paths for this slice are listed in the File Structure table above.
