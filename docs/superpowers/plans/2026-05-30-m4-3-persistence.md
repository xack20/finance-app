# M4-3 — Agent Conversation Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist the agent chat thread — user messages, assistant final messages, the turn's `proposedWrites`, and the `applied_summary` of what committed — in the SQLCipher DB, so conversations survive app restarts and the next turn's history includes what was proposed/applied.

**Architecture:** A new migration `5.sqm` adds two tables (`agent_conversation`, `agent_message`). `proposedWrites` and `appliedSummary` are stored as JSON `TEXT` (via `LlmJson.json`); `ProposedWrite`/`AppliedSummary` become `@Serializable`. A new `ConversationRepository` (mirroring the existing repo conventions) owns create/append/observe, decoding the JSON columns back into typed values. Read-tool calls/results are NOT persisted (in-memory only, per spec §11).

**Tech Stack:** Kotlin Multiplatform, SQLDelight 2 + SQLCipher, kotlinx.serialization (`LlmJson.json`), kotlinx-coroutines `Flow`, kotlin.test + kotlinx-coroutines-test.

**Scope (M4-3):** `5.sqm` agent tables; `AgentQueries.sq`; `ProposedWrite`/`AppliedSummary` `@Serializable` + a JSON codec; `ConversationRepository` (create/append-user/append-assistant/observe-messages/observe-conversations/latest); round-trip tests.

**Out of scope (later):** wiring the repo into a turn (`AgentScreen`/`AgentViewModel`/DI) → **M4-6**; card columns → M4-4 (`6.sqm`); STT → M4-5. `TxnSource.CHAT` already exists (M4-2). The committer's `AppliedSummary` already exists (M4-2).

**Migration numbering:** M4-2 used `4.sqm` (`transfer_group_id`). **M4-3 → `5.sqm` (agent tables).** M4-4 → `6.sqm` (card columns).

---

## Grounded codebase facts (verified — rely on these)

- Migrations dir has `1.sqm`..`4.sqm`. Next = `5.sqm`. Schema is derived from migrations; a table defined in a `.sqm` generates a row type in the **`migrations`** package (e.g. `migrations.Capture_inbox`). So `agent_conversation`→`migrations.Agent_conversation`, `agent_message`→`migrations.Agent_message`.
- A `.sq` queries file at `composeApp/src/commonMain/sqldelight/app/hisaab/db/<Name>Queries.sq` generates the accessor `db.<name>QueriesQueries` (e.g. `AgentQueries.sq` → `db.agentQueriesQueries`).
- Repo convention (see `CaptureInboxRepository`): `class XRepository(private val db: HisaabDatabase) { private val queries get() = db.<x>QueriesQueries; ... }`. Writes are `suspend`; reads return `Flow` via `queries.observeX().asFlow().mapToList(Dispatchers.Default).map { it.map { r -> r.toDomain() } }`; one-shot reads use `.executeAsOneOrNull()`/`.executeAsList()`. Enums stored as `.name`, decoded with `valueOf`. Each repo has a private `randomId()` (16 random bytes → hex).
- `LlmJson.json` is a configured `kotlinx.serialization.json.Json` (`ignoreUnknownKeys`, `isLenient`, `coerceInputValues`). `import app.hisaab.llm.LlmJson`.
- `ProposedWrite` (in `app.hisaab.agent.Tools`) = `data class ProposedWrite(val tool: String, val args: JsonObject)` — NOT yet `@Serializable`. `JsonObject` is natively serializable inside a `Json` format.
- `AppliedSummary` (in `app.hisaab.agent.WriteBatchCommitter`) = `data class AppliedSummary(accountsCreated, categoriesCreated, transactionsAdded, lendBorrowsRecorded, transfers)` (all `Int`, default 0) — NOT yet `@Serializable`.
- No existing repo serializes JSON into a TEXT column — this slice introduces that pattern with `LlmJson.json`.
- `kotlinx.datetime.Clock` is available (`Clock.System.now().toEpochMilliseconds()`).
- `TestDatabase.create()` (in `app.hisaab.data.support`) applies ALL migrations (incl. the new `5.sqm`) and enables FK enforcement.

---

## File Structure

- Create `composeApp/src/commonMain/sqldelight/migrations/5.sqm` — the two agent tables + index.
- Create `composeApp/src/commonMain/sqldelight/app/hisaab/db/AgentQueries.sq` — conversation/message queries.
- Modify `composeApp/src/commonMain/kotlin/app/hisaab/agent/Tools.kt` — `@Serializable` on `ProposedWrite`.
- Modify `composeApp/src/commonMain/kotlin/app/hisaab/agent/WriteBatchCommitter.kt` — `@Serializable` on `AppliedSummary`.
- Create `composeApp/src/commonMain/kotlin/app/hisaab/agent/AgentPersistence.kt` — `AgentWriteCodec` (JSON encode/decode for proposedWrites + appliedSummary).
- Create `composeApp/src/commonMain/kotlin/app/hisaab/domain/AgentConversation.kt` — `AgentRole`, `AgentConversation`, `AgentMessage`.
- Create `composeApp/src/commonMain/kotlin/app/hisaab/data/ConversationRepository.kt` — the repo.
- Tests: `composeApp/src/commonTest/kotlin/app/hisaab/data/AgentQueriesTest.kt`, `composeApp/src/commonTest/kotlin/app/hisaab/agent/AgentWriteCodecTest.kt`, `composeApp/src/commonTest/kotlin/app/hisaab/data/ConversationRepositoryTest.kt`.

---

## Task 1: `5.sqm` agent tables + `AgentQueries.sq`

**Files:**
- Create: `composeApp/src/commonMain/sqldelight/migrations/5.sqm`
- Create: `composeApp/src/commonMain/sqldelight/app/hisaab/db/AgentQueries.sq`
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/data/AgentQueriesTest.kt`

- [ ] **Step 1: Write the failing test** (exercises the schema + queries directly, no repo yet):

```kotlin
// AgentQueriesTest.kt
package app.hisaab.data
import app.hisaab.data.support.TestDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentQueriesTest {
    @Test fun `conversation and message round-trip at the query level`() {
        val db = TestDatabase.create()
        val q = db.agentQueriesQueries
        q.insertConversation(id = "c1", title = "First", created_at = 100L, updated_at = 100L)
        q.insertMessage(id = "m1", conversation_id = "c1", role = "USER", content = "hi",
            proposed_writes = null, applied_summary = null, created_at = 110L)
        q.insertMessage(id = "m2", conversation_id = "c1", role = "ASSISTANT", content = "hello",
            proposed_writes = "[]", applied_summary = null, created_at = 120L)

        val convos = q.observeConversations().executeAsList()
        assertEquals(1, convos.size)
        assertEquals("First", convos.single().title)

        val msgs = q.observeMessages("c1").executeAsList()
        assertEquals(2, msgs.size)
        assertEquals("hi", msgs.first().content)         // ordered by created_at ASC
        assertEquals("ASSISTANT", msgs.last().role)
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.AgentQueriesTest" --console=plain`
Expected: FAIL — `agentQueriesQueries` / the queries unresolved.

- [ ] **Step 3: Create `5.sqm`:**

```sql
-- M4-3: agent conversation persistence. Stores the chat thread so it survives restarts
-- and the next turn's history includes prior proposed writes + what actually committed.
-- Read-tool calls/results are NOT persisted (in-memory only).
CREATE TABLE agent_conversation (
    id         TEXT NOT NULL PRIMARY KEY,
    title      TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE agent_message (
    id              TEXT NOT NULL PRIMARY KEY,
    conversation_id TEXT NOT NULL REFERENCES agent_conversation(id),
    role            TEXT NOT NULL,                 -- 'USER' | 'ASSISTANT' (TxnSource-style .name)
    content         TEXT NOT NULL,
    proposed_writes TEXT,                          -- JSON array of {tool,args}; null/absent = none
    applied_summary TEXT,                          -- JSON of AppliedSummary; null = nothing applied
    created_at      INTEGER NOT NULL
);
CREATE INDEX agent_message_conversation_idx ON agent_message(conversation_id, created_at);
```

- [ ] **Step 4: Create `AgentQueries.sq`:**

```sql
insertConversation:
INSERT INTO agent_conversation(id, title, created_at, updated_at) VALUES (?, ?, ?, ?);

touchConversation:
UPDATE agent_conversation SET updated_at = ? WHERE id = ?;

observeConversations:
SELECT * FROM agent_conversation ORDER BY updated_at DESC;

latestConversation:
SELECT * FROM agent_conversation ORDER BY updated_at DESC LIMIT 1;

insertMessage:
INSERT INTO agent_message(id, conversation_id, role, content, proposed_writes, applied_summary, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?);

observeMessages:
SELECT * FROM agent_message WHERE conversation_id = ? ORDER BY created_at ASC;
```

- [ ] **Step 5: Run the test to confirm it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.AgentQueriesTest" --console=plain`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/sqldelight/migrations/5.sqm composeApp/src/commonMain/sqldelight/app/hisaab/db/AgentQueries.sq composeApp/src/commonTest/kotlin/app/hisaab/data/AgentQueriesTest.kt
git commit -m "feat(data): 5.sqm agent_conversation/agent_message tables + AgentQueries"
```

---

## Task 2: `@Serializable` proposed-writes/summary + JSON codec

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/agent/Tools.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/hisaab/agent/WriteBatchCommitter.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/agent/AgentPersistence.kt`
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/agent/AgentWriteCodecTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// AgentWriteCodecTest.kt
package app.hisaab.agent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentWriteCodecTest {
    @Test fun `proposed writes round-trip through JSON`() {
        val writes = listOf(
            ProposedWrite("add_transaction", buildJsonObject { put("account", "Cash"); put("amount", 500.0) }),
            ProposedWrite("transfer", buildJsonObject { put("fromAccount", "Bank"); put("toAccount", "Cash"); put("amount", 1000.0) }),
        )
        val encoded = AgentWriteCodec.encodeWrites(writes)
        assertTrue(encoded != null && encoded.contains("add_transaction"))
        val decoded = AgentWriteCodec.decodeWrites(encoded)
        assertEquals(2, decoded.size)
        assertEquals("add_transaction", decoded.first().tool)
        assertEquals("Cash", (decoded.first().args["account"] as JsonPrimitive).content)
    }
    @Test fun `empty writes encode to null and decode to empty`() {
        assertNull(AgentWriteCodec.encodeWrites(emptyList()))
        assertTrue(AgentWriteCodec.decodeWrites(null).isEmpty())
        assertTrue(AgentWriteCodec.decodeWrites("").isEmpty())
    }
    @Test fun `applied summary round-trips`() {
        val s = AppliedSummary(accountsCreated = 1, transactionsAdded = 2, transfers = 1)
        val encoded = AgentWriteCodec.encodeSummary(s)
        assertEquals(s, AgentWriteCodec.decodeSummary(encoded))
        assertNull(AgentWriteCodec.encodeSummary(null))
        assertNull(AgentWriteCodec.decodeSummary(null))
    }
    @Test fun `malformed json decodes safely`() {
        assertTrue(AgentWriteCodec.decodeWrites("not json").isEmpty())
        assertNull(AgentWriteCodec.decodeSummary("not json"))
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.agent.AgentWriteCodecTest" --console=plain`
Expected: FAIL — `AgentWriteCodec` unresolved (and `ProposedWrite`/`AppliedSummary` not serializable).

- [ ] **Step 3: Add `@Serializable`**

In `Tools.kt`, annotate `ProposedWrite` (add `import kotlinx.serialization.Serializable`):
```kotlin
@Serializable
data class ProposedWrite(val tool: String, val args: JsonObject)
```
In `WriteBatchCommitter.kt`, annotate `AppliedSummary` (add `import kotlinx.serialization.Serializable`):
```kotlin
@Serializable
data class AppliedSummary(
    val accountsCreated: Int = 0,
    val categoriesCreated: Int = 0,
    val transactionsAdded: Int = 0,
    val lendBorrowsRecorded: Int = 0,
    val transfers: Int = 0,
)
```

- [ ] **Step 4: Create `AgentPersistence.kt`**

```kotlin
// AgentPersistence.kt
package app.hisaab.agent
import app.hisaab.llm.LlmJson
import kotlinx.serialization.builtins.ListSerializer

/** Encodes/decodes a turn's proposedWrites and appliedSummary to the JSON TEXT columns in agent_message.
 *  Decode is tolerant: malformed/empty JSON yields empty/null rather than throwing. */
object AgentWriteCodec {
    private val writesSerializer = ListSerializer(ProposedWrite.serializer())

    fun encodeWrites(writes: List<ProposedWrite>): String? =
        if (writes.isEmpty()) null else LlmJson.json.encodeToString(writesSerializer, writes)

    fun decodeWrites(raw: String?): List<ProposedWrite> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { LlmJson.json.decodeFromString(writesSerializer, raw) }.getOrDefault(emptyList())

    fun encodeSummary(summary: AppliedSummary?): String? =
        summary?.let { LlmJson.json.encodeToString(AppliedSummary.serializer(), it) }

    fun decodeSummary(raw: String?): AppliedSummary? =
        if (raw.isNullOrBlank()) null
        else runCatching { LlmJson.json.decodeFromString(AppliedSummary.serializer(), raw) }.getOrNull()
}
```

- [ ] **Step 5: Run the test to confirm it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.agent.AgentWriteCodecTest" --console=plain`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/agent/Tools.kt composeApp/src/commonMain/kotlin/app/hisaab/agent/WriteBatchCommitter.kt composeApp/src/commonMain/kotlin/app/hisaab/agent/AgentPersistence.kt composeApp/src/commonTest/kotlin/app/hisaab/agent/AgentWriteCodecTest.kt
git commit -m "feat(agent): @Serializable ProposedWrite/AppliedSummary + AgentWriteCodec"
```

---

## Task 3: `ConversationRepository` + domain types + round-trip

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/domain/AgentConversation.kt`
- Create: `composeApp/src/commonMain/kotlin/app/hisaab/data/ConversationRepository.kt`
- Test: `composeApp/src/commonTest/kotlin/app/hisaab/data/ConversationRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// ConversationRepositoryTest.kt
package app.hisaab.data
import app.hisaab.agent.AppliedSummary
import app.hisaab.agent.ProposedWrite
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.AgentRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationRepositoryTest {
    @Test fun `append and observe a full turn round-trips proposedWrites + appliedSummary`() = runTest {
        val db = TestDatabase.create()
        val repo = ConversationRepository(db)
        val convId = repo.createConversation(title = "Lunch")
        repo.appendUserMessage(convId, "spent 500 on lunch")
        repo.appendAssistantMessage(
            conversationId = convId,
            content = "Logged 500 for lunch.",
            proposedWrites = listOf(ProposedWrite("add_transaction",
                buildJsonObject { put("account", "Cash"); put("amount", 500.0); put("kind", "EXPENSE") })),
            appliedSummary = AppliedSummary(transactionsAdded = 1),
        )
        val msgs = repo.observeMessages(convId).first()
        assertEquals(2, msgs.size)
        val user = msgs.first(); val assistant = msgs.last()
        assertEquals(AgentRole.USER, user.role)
        assertTrue(user.proposedWrites.isEmpty()); assertNull(user.appliedSummary)
        assertEquals(AgentRole.ASSISTANT, assistant.role)
        assertEquals("add_transaction", assistant.proposedWrites.single().tool)
        assertEquals(1, assistant.appliedSummary?.transactionsAdded)
    }

    @Test fun `conversations are listed newest-updated first and latest is resolvable`() = runTest {
        val db = TestDatabase.create()
        var t = 0L
        val repo = ConversationRepository(db, now = { ++t })   // strictly increasing → deterministic ordering
        val a = repo.createConversation("A")          // A.updated_at = 1
        repo.createConversation("B")                  // B.updated_at = 2
        repo.appendUserMessage(a, "touch A so it floats to the top")  // A.updated_at = 3
        val convos = repo.observeConversations().first()
        assertEquals(2, convos.size)
        assertEquals(a, convos.first().id)            // A updated last (3 > 2)
        assertEquals(a, repo.latestConversationId())
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.ConversationRepositoryTest" --console=plain`
Expected: FAIL — `ConversationRepository`/`AgentRole` unresolved.

- [ ] **Step 3: Create the domain types** — `AgentConversation.kt`:

```kotlin
// AgentConversation.kt
package app.hisaab.domain
import app.hisaab.agent.AppliedSummary
import app.hisaab.agent.ProposedWrite

enum class AgentRole { USER, ASSISTANT }

data class AgentConversation(
    val id: String,
    val title: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class AgentMessage(
    val id: String,
    val conversationId: String,
    val role: AgentRole,
    val content: String,
    val proposedWrites: List<ProposedWrite>,
    val appliedSummary: AppliedSummary?,
    val createdAt: Long,
)
```

- [ ] **Step 4: Create `ConversationRepository.kt`**

```kotlin
// ConversationRepository.kt
package app.hisaab.data
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.hisaab.agent.AgentWriteCodec
import app.hisaab.agent.AppliedSummary
import app.hisaab.agent.ProposedWrite
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.AgentConversation
import app.hisaab.domain.AgentMessage
import app.hisaab.domain.AgentRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.random.Random

class ConversationRepository(
    private val db: HisaabDatabase,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private val queries get() = db.agentQueriesQueries

    suspend fun createConversation(title: String? = null): String {
        val id = randomId(); val ts = now()
        queries.insertConversation(id = id, title = title, created_at = ts, updated_at = ts)
        return id
    }

    suspend fun appendUserMessage(conversationId: String, content: String): String =
        appendMessage(conversationId, AgentRole.USER, content, emptyList(), null)

    suspend fun appendAssistantMessage(
        conversationId: String,
        content: String,
        proposedWrites: List<ProposedWrite>,
        appliedSummary: AppliedSummary?,
    ): String = appendMessage(conversationId, AgentRole.ASSISTANT, content, proposedWrites, appliedSummary)

    private fun appendMessage(
        conversationId: String,
        role: AgentRole,
        content: String,
        proposedWrites: List<ProposedWrite>,
        appliedSummary: AppliedSummary?,
    ): String {
        val id = randomId(); val ts = now()
        queries.insertMessage(
            id = id,
            conversation_id = conversationId,
            role = role.name,
            content = content,
            proposed_writes = AgentWriteCodec.encodeWrites(proposedWrites),
            applied_summary = AgentWriteCodec.encodeSummary(appliedSummary),
            created_at = ts,
        )
        queries.touchConversation(updated_at = ts, id = conversationId)
        return id
    }

    fun observeMessages(conversationId: String): Flow<List<AgentMessage>> =
        queries.observeMessages(conversationId).asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    fun observeConversations(): Flow<List<AgentConversation>> =
        queries.observeConversations().asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    suspend fun latestConversationId(): String? =
        queries.latestConversation().executeAsOneOrNull()?.id

    private fun migrations.Agent_message.toDomain(): AgentMessage = AgentMessage(
        id = id,
        conversationId = conversation_id,
        role = runCatching { AgentRole.valueOf(role) }.getOrDefault(AgentRole.USER),
        content = content,
        proposedWrites = AgentWriteCodec.decodeWrites(proposed_writes),
        appliedSummary = AgentWriteCodec.decodeSummary(applied_summary),
        createdAt = created_at,
    )

    private fun migrations.Agent_conversation.toDomain(): AgentConversation = AgentConversation(
        id = id, title = title, createdAt = created_at, updatedAt = updated_at,
    )

    private fun randomId(): String {
        val bytes = Random.Default.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
```

> The generated one-shot accessor is `queries.latestConversation().executeAsOneOrNull()` — if SQLDelight names the generated function differently, match the actual name. The generated row types are `migrations.Agent_message` / `migrations.Agent_conversation` (column-name access: `conversation_id`, `proposed_writes`, `applied_summary`, `updated_at`, etc.). If a generated property name differs, adjust the mapper accordingly (the build will tell you).

- [ ] **Step 5: Run the test to confirm it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.data.ConversationRepositoryTest" --console=plain`
Expected: PASS (2 tests).

- [ ] **Step 6: Run the full agent+data sweep (regression)**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.agent.*" --tests "app.hisaab.data.*" --console=plain`
Expected: PASS (all M4-1 + M4-2 + M4-3 tests green).

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/domain/AgentConversation.kt composeApp/src/commonMain/kotlin/app/hisaab/data/ConversationRepository.kt composeApp/src/commonTest/kotlin/app/hisaab/data/ConversationRepositoryTest.kt
git commit -m "feat(data): ConversationRepository — persist/observe agent thread (writes + summary)"
```

---

## Acceptance criteria (M4-3 done)

- `5.sqm` creates `agent_conversation` + `agent_message` (+ index); `TestDatabase` (all migrations) builds and the query-level round-trip passes.
- `ProposedWrite`/`AppliedSummary` are `@Serializable`; `AgentWriteCodec` round-trips both and decodes malformed/empty JSON safely (no throw).
- `ConversationRepository` persists a full turn (user msg + assistant msg with `proposedWrites` + `appliedSummary`) and reads it back intact; conversations list newest-updated-first; `latestConversationId()` resolves.
- Full agent+data suite stays green.

## Next slices (separate plans)

- **M4-4:** `6.sqm` card columns + `cardOutstanding`/`CardSummary` + `card_summary`/`record_card_payment` real semantics + card UI.
- **M4-6:** `AgentScreen` + `AgentViewModel` + DI (all 3 actuals) wires `AgentLoop` → `ConversationRepository` (persist user msg → run loop → persist assistant msg + proposedWrites) → review card → `WriteBatchCommitter` Apply → persist `applied_summary`.
- Deferred write tools (`set_budget`/`recategorize`/`add_split_transaction`); M4-5 STT; M4-7 consent; M4-0 iOS wrapper.
