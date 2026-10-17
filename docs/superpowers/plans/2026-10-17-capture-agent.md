# Capture Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the rule-based capture engine with a batched agent that reads every captured message, decides
what it is, writes validated ledger changes on its own, and asks the user only what it can't work out.

**Architecture:** Sources → `CaptureQueue` (store `QUEUED`) → `CaptureAgentWorker` (debounce, daily cap, drain
in batches of 25) → `CaptureAgent` (prompt + read tools via the existing `AgentLoop`, one repair round) →
`CaptureDecisions` (parse + validate, pure) → `CaptureCommitter` (one `db.transaction`). Questions and memories
are two new tables. The chat agent's JSON-envelope protocol and `AgentProvider` are reused unchanged.

**Tech Stack:** Kotlin Multiplatform, SQLDelight 2.1 (SQLCipher), kotlinx-serialization JSON, coroutines.

**Spec:** `docs/superpowers/specs/2026-10-17-agentic-hisaab-design.md` (Phase 1 detail)

## Global Constraints

- Agent proposes, code commits; transaction `ts` = first message's `received_at`, never a model-supplied date.
- Batch size 25; debounce 5 s; daily cap 600 provider calls (local day); 3 attempts before an undecided message
  becomes a question; one repair round on validation errors.
- OTP/PIN/verification codes are masked on-device before any cloud call, regardless of the redaction setting.
- Schema change = new `migrations/10.sqm` only. Wire every new service in both `HisaabEngine` (Android) and
  `AppContainerIos`. Never run `connectedDebugAndroidTest` on the user's phone.
- Use the Midnight design components (`design/components/`) for new UI.

## Review Focus

1. A batch where the model returns malformed JSON twice → messages stay `QUEUED` with `attempts` bumped, no
   partial writes (test in Task 7).
2. The same payment as SMS + email in one batch → one transaction, the other message `DISMISSED` (Task 6).
3. Answering a question while the worker is mid-batch → answered messages are re-queued, not lost (Task 2/8).
4. Provider missing / key rejected / cap reached → worker stops cleanly, status says why, queue intact (Task 8).
5. Agent names an account that doesn't exist and doesn't create it → validation error fed back, not a crash
   (Task 5/7).

---

### Task 1: Schema — queue states, questions, memory, call budget

**Files:** Create `composeApp/src/commonMain/sqldelight/migrations/10.sqm`,
`sqldelight/app/hisaab/db/AgentQuestionQueries.sq`, `AgentMemoryQueries.sq`; modify `CaptureInboxQueries.sq`,
`CaptureConfigQueries.sq`, `domain/Capture.kt` (`CaptureStatus.QUEUED`). Test:
`commonTest/.../data/CaptureAgentMigrationTest.kt`.

10.sqm: `capture_inbox` + `question_id TEXT`, `attempts INTEGER NOT NULL DEFAULT 0`; `capture_config` +
`agent_calls_day TEXT`, `agent_calls_count INTEGER NOT NULL DEFAULT 0`; tables `agent_question(id PK, question,
options TEXT /*JSON array*/, status TEXT /*OPEN|ANSWERED*/, answer, created_at, answered_at)` and
`agent_memory(id PK, fact, created_at)`; data: `PENDING → QUEUED`; each `sender_registry` row with an account
→ memory "Messages from <sender_id> are for the account <account.name>".

- [ ] Test `old Review items are handed to the agent and sender mappings become memories`: create schema, insert
  PENDING + AUTO_POSTED rows and a mapped sender, `Schema.migrate(driver, 10, 11)`; assert PENDING→QUEUED,
  AUTO_POSTED unchanged, one memory containing the sender and account name. Run → FAIL (no migration).
- [ ] Write 10.sqm + queries; `./gradlew :composeApp:verifySqlDelightMigration` and the test → PASS.

### Task 2: Repositories

**Interfaces — Produces:**
- `CaptureInboxRepository`: `suspend fun nextQueued(limit: Int): List<CandidateTransaction>`,
  `fun observeQueuedCount(): Flow<Long>`, `fun insertQueuedBlocking(c)` via existing insert.
- `AgentQuestionRepository(db)`: `fun observeOpen(): Flow<List<AgentQuestion>>`,
  `fun observeOpenCount(): Flow<Long>`, `suspend fun messagesFor(questionId): List<CandidateTransaction>`,
  `suspend fun answer(questionId: String, answer: String)` (one transaction: ANSWERED + re-queue its messages
  with attempts 0), `suspend fun dismiss(questionId)` (ANSWERED "not a payment" + messages DISMISSED).
- `AgentMemoryRepository(db)`: `observeAll()`, `suspend fun all(): List<AgentMemory>`, `suspend fun delete(id)`.
- `CaptureConfigRepository`: `suspend fun agentCallsToday(today: String): Int`,
  `suspend fun recordAgentCall(today: String)` (resets when the day changes).
- Domain: `data class AgentQuestion(id, question, options: List<String>, messageIds…)`,
  `data class AgentMemory(id, fact, createdAt)`.

- [ ] Tests (`AgentQuestionRepositoryTest`, `AgentMemoryRepositoryTest`, `AgentCallBudgetTest`):
  `answering re-queues the question's messages`, `dismissing a question dismisses its messages`,
  `calls reset on a new day`. FAIL → implement → PASS.

### Task 3: On-device message preparation

**Files:** modify `llm/Redactor.kt` (`fun maskSecrets(text): String`); create `capture/ContactEnricher.kt`
(`fun enrichWithContacts(text: String, lookup: (String) -> String?): String`).

- [ ] Tests: `one-time codes are masked but amounts stay` ("Your bKash OTP is 482913" → no 482913;
  "Tk 4,829.00 sent" unchanged; Bangla "কোড ৫৫৬৬" masked); `a known phone number gets the contact name`
  ("Sent Tk 500 to 01712345678" + lookup → contains "(contact: Rahim)"). FAIL → implement → PASS.

### Task 4: `CaptureQueue`

**Produces:** `class CaptureQueue(inbox: CaptureInboxRepository, onQueued: () -> Unit) : CaptureHandler` —
dedup hash (existing `dedupHash(sender, body)`), insert `QUEUED`, call `onQueued` only for new rows.

- [ ] Tests: `a message is queued once`, `a repeat does not wake the agent`. FAIL → implement → PASS.

### Task 5: `CaptureDecisions` (parse + validate, pure)

**Produces:** `sealed interface CaptureDecision` — `PostTransaction(messageIds, account, amount, kind,
category?, merchant?, notes?)`, `PostTransfer(messageIds, fromAccount, toAccount, amount)`,
`PostLendBorrow(messageIds, person, amount, kind, account, purpose?)`, `CreateAccount(name, kind,
institution?)`, `Skip(messageIds, reason)`, `Ask(messageIds, question, options)`, `Remember(fact)`;
`fun parseDecisions(writes: List<ProposedWrite>, batchIds: Set<String>, knownAccounts: Set<String>):
DecisionResult(decisions, errors: List<String>, undecided: Set<String>)`. Rules: ids outside the batch → error;
a message in two decisions → error; amount ≤ 0 → error; account not known and not created in the batch →
error (case-insensitive).

- [ ] Tests for each rule plus `the same payment by SMS and email is one decision`. FAIL → implement → PASS.

### Task 6: `CaptureCommitter`

**Produces:** `class CaptureCommitter(db, accounts, txns, persons, lendBorrow, categories, questions, memory)`
with `suspend fun commit(batch: List<CandidateTransaction>, decisions: List<CaptureDecision>):
List<CaptureEvent.AutoPosted>` — one `db.transaction`: accounts first; posts insert txn with `captureId` = first
message (status AUTO_POSTED), other ids DISMISSED "same payment as <first>"; transfer legs via
`transferBlocking` and the out-leg linked; Skip → DISMISSED reason; Ask → question row + messages PENDING with
`question_id`; Remember → memory row. Source `TxnSource.SMS`.

- [ ] Tests: `a post adds one linked transaction`, `copies of a posted payment are dismissed`,
  `a transfer moves money between own accounts`, `ask creates a question and parks the messages`,
  `a new account can be created and used in the same batch`, `a failing write rolls back the whole batch`.
  FAIL → implement → PASS.

### Task 7: `CaptureAgent`

**Files:** create `capture/agent/CaptureAgent.kt`, `CaptureAgentPrompt.kt`; add
`FindSimilarTransactionsTool` to `agent/ReadTools.kt`. Raise `ClaudeProvider.AGENT_TIMEOUT_MS` to 120 s.

**Produces:** `class CaptureAgent(provider: AgentProvider, tools: ToolRegistry, context: suspend () ->
AgentContext, committer: CaptureCommitter, inbox, prepare: (CandidateTransaction) -> String, today: () ->
String)` with `suspend fun process(batch): BatchOutcome(posted, skipped, asked, undecided)`. Undecided:
attempts+1, at 3 → `Ask("I couldn't work out this message — what is it?")`.

- [ ] Tests with a scripted fake provider: `a batch is decided and committed`, `the prompt carries memories,
  accounts and earlier answers`, `a validation error gets one repair round`, `malformed replies leave the batch
  queued with attempts bumped`, `a message undecided three times becomes a question`. FAIL → implement → PASS.

### Task 8: `CaptureAgentWorker`

**Produces:** `class CaptureAgentWorker(scope-less; inbox, agentFactory: suspend () -> CaptureAgent?,
budget, status: ChannelStatusTracker, debounceMs = 5_000, batchSize = 25, dailyCap = 600)` with
`fun kick()`, `suspend fun runUntilIdle()`, `fun start(scope)`. Status: Importing(done, total) while draining,
UpToDate when empty, Error "Add a cloud AI key and consent to read messages" / "Daily AI limit reached —
continues tomorrow" / key rejected (`LlmError.InvalidKey`).

- [ ] Tests (virtual time): `kicks within the debounce window run one drain`, `stops at the daily cap with the
  queue intact`, `no provider leaves messages queued and says why`, `a rejected key stops the drain`.
  FAIL → implement → PASS.

### Task 9: Wiring, contacts, removal

- Android `HisaabEngine` + iOS `AppContainerIos`: coordinator handler = `CaptureQueue`; worker started with
  capture and kicked after imports/answers; provider = `llmRouter.active() as? AgentProvider`.
- Contacts: `READ_CONTACTS` in the manifest; `ContactLookup` (Android `PhoneLookup`; iOS returns null);
  `CaptureService.hasContactsPermission()/requestContactsPermission()`.
- Delete `CapturePipeline`, `SmsPreFilter`, `PreFilterResult`, `BankTemplate`, `ConfidenceScorer`,
  `AccountMatcher`, `DuplicateCleanup`, `WaitingSenders`, `assignAccountToPending`, and their tests.
- [ ] `testDebugUnitTest`, `compileKotlinIosArm64`, `compileDebugAndroidTestKotlin` → all green.

### Task 10: UI

- `screens/capture/QuestionsScreen.kt` + `QuestionsViewModel` replace `ReviewInboxScreen`/VM (route kept):
  question, its messages (sender + body, expandable), quick-answer chips, free-text answer, "Not a payment".
- Today banner: "N questions from Hisaab" (open questions); hidden at 0.
- Auto-capture: remove threshold slider, always-review, "New senders detected", "Find duplicate payments";
  add Contacts row, "AI calls today: N of 600", "What Hisaab has learned" (delete per memory); Sync's Cloud AI
  line shows the worker status ("Reading messages: done of total").
- [ ] VM tests: `answering sends the answer and clears the card`, `memory delete`. FAIL → implement → PASS.

### Task 11: Ship

- [ ] Update `CLAUDE.md` capture section and `docs/tech-debt.md`; full suite + iOS/androidTest compile;
  `composeApp/publish-apk.sh`; install over adb; verify on the phone: queue draining (notification), questions
  appear, Monthly fills, 0 crashes.
