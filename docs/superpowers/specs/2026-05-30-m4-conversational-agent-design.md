# M4 — Conversational Money Assistant (text + voice) — Design Spec

- **Date:** 2026-05-30
- **Status:** Draft (reviewed; adversarial review applied 2026-05-30)
- **Milestone:** M4
- **Related:** `2026-05-27-hisaab-master-architecture-design.md` (frames M4 as "Agent runtime + voice"); M3 capture + LLM provider layer; `docs/tech-debt.md`.

## 1. Goal

Let the user record and manage their finances by **talking or typing to an assistant** in English / Bengali / Banglish. The user describes activity as a free-form story ("paid 500 for lunch with cash, lent Karim 2000, and my BRAC card bill is due") or asks questions ("how much did I spend on food this month?"); a multi-turn LLM **agent** calls tools over the existing ledger and proposes any writes for the user to review and apply. Full money-assistant scope: **log + query + manage** (accounts, budgets, categories, lend/borrow, **credit cards**).

## 2. Decisions

| Decision | Choice | Notes |
|---|---|---|
| Scope | Full money assistant — log + query + manage | |
| Brain | **Cloud agent, opt-in + BYO key**, gated by a **distinct agent consent** (§12) | On-device stays default for SMS capture; agent is an explicit cloud feature. |
| Loop | **Prompted JSON-tool (ReAct), vendor-agnostic** | Reliability via guards (§6); on-device-agent path stays open. |
| Default model | **Gemini** (`gemini-2.0-flash`), key in SecureStorage `llm_api_key_GEMINI` | Reuses M3 `GeminiProvider`; other providers selectable. Key entered only via Settings UI — never source/`local.properties`/commit. |
| Write safety | **Batch review card → atomic Apply**; reads run freely; no-write-in-loop **structurally enforced** (§6) | |
| Voice | **On-device STT, input only**, **fail-closed** if no offline locale pack (§13) | Audio never leaves the device; only text does. |
| Platform | **Android + iOS** | iOS Compose interop already exists; only the Xcode wrapper is missing (mechanical — M4-0). |
| Memory | **Persist chat thread + proposed-write provenance** (encrypted) | Durable facts → `Person` records; no separate learned-memory store. |
| Credit cards | **Kept in M4** (per decision) | Built on net-new per-account balance + transfer-pair infra (M4-4), a hard predecessor of the agent card tools. |

## 3. Architecture — reuse vs. net-new (corrected after review)

New `commonMain` package **`app.hisaab.agent`**: `AgentLoop`, `ToolRegistry` + `AgentTools.kt`, `AgentPrompts.kt`, `AgentJson.kt`, `ProposedWrite`/`WriteBatch`, `WriteBatchCommitter` (§8).

**Genuinely reused from M3 (verified):** `LlmRouter.active()` engine/consent/BYO-key gating; `CloudHttp.llmConfigured()`/`mapHttpError()` + `LlmError`/`LlmException`; the `apiKey:()->String?` **constructor seam on each cloud provider** (not on `CloudHttp`); `llm_api_key_<PROVIDER>` SecureStorage keys; `LlmJson` lenient-parse + `isolateJson` fence-strip; the hardened atomic `db.transaction { candidate → txn }` primitive and `TransactionRepository.addBlocking`/`AccountRepository.addBlocking`; `AccountKind.CARD`, `TxnKind.TRANSFER` (enum values already exist).

**Net-new (must be built — NOT reuse):**
- An **agent chat/completion capability** on the provider layer (§4) — today's `LlmProvider` only does `parse()`/`categorize()`.
- **Transaction-safe write primitives** for lend/borrow, transfer pairs, and card payment, behind a single `WriteBatchCommitter` (§8) — today only plain `txn`/`account` `addBlocking` are transaction-safe.
- **Per-account balance queries** + a `CardSummary` use-case (§9) — no per-account balance computation exists anywhere today.
- A **`transfer_group_id`** link column on `txn` (§9, §11) — `parent_txn_id` is reserved for splits.
- `SpeechToText` platform layer (§13); the **iOS Xcode project wrapper** (§14; Compose↔UIViewController interop already exists).

## 4. Agent provider capability (net-new — MF1)

Add a chat entrypoint usable for the ReAct loop, separate from the SMS `parse()` path:

```kotlin
data class ChatMessage(val role: Role, val content: String)   // Role = SYSTEM | USER | ASSISTANT | TOOL
interface AgentProvider {                                       // implemented by the 3 cloud providers
    suspend fun complete(messages: List<ChatMessage>, maxTokens: Int): String   // raw assistant text (JSON envelope)
}
```

- **Gemini** (default): `generateContent` with `responseMimeType=application/json` (free-form JSON, **not** the SMS `responseSchema`). **Claude:** Messages API, no forced `record_transaction` tool. **OpenAI:** chat completions, `response_format: json_object`.
- The on-device provider throws `NotSupported` (the agent is cloud-only in v1); `FakeProviders` implements `complete()` so commonTest compiles.
- `AgentLoop` resolves a provider via `llmRouter.active()` and calls `complete()`. Decoding the envelope is `AgentJson` (extends `LlmJson`), **not** `decodeResult`.

## 5. The agent loop (prompted ReAct)

1. **Request** = system prompt (tool catalog + JSON protocol + the user's accounts/categories/**card metadata** + today's date + language) + persisted thread history (incl. prior **proposed writes**, §11) + new user message.
2. `complete(messages)` → one JSON envelope per step:
   - `{"thought":…,"action":{"tool":…,"args":…}}` → execute (read tool) → append result to the **in-memory** working transcript → loop; **or**
   - `{"thought":…,"final":{"message":…,"proposedWrites":[…]}}` → stop; surface message + review card.

## 6. Reliability + write safety

- **No-write-in-loop is structurally enforced** (not just asserted): write-tool executors receive **no `db` dependency** at loop time and return **pure `ProposedWrite` values**; the `db.transaction` handle is injected only into the `WriteBatchCommitter` at Apply (§8). Read executors are pure/side-effect-free. *Test:* a `db` that throws on write still completes a multi-write turn.
- **Loop bound:** ≤ 6 model iterations per user turn. A validation-rejection or one self-repair retry **consumes an iteration** (so the terminator is deterministic); "repair once" is **per distinct malformed response**, capped by the 6.
- **Validation:** every tool name checked against the registry; every arg validated against its declared schema; unknown tool/bad args → rejected and fed back once.
- **Cost/failure guards:** per-turn **token budget** + per-call **timeout** (confirm `CloudHttp` sets a non-default timeout); reuse the static system-prompt prefix for provider prompt-caching; on mid-loop failure the message **explicitly states "nothing was saved"** (earned by propose-then-Apply).
- **Apply:** re-validates each included write at commit time and commits only the rows shown in the reviewed batch (keyed to a batch id). Account- and category-**creation** rows default **unchecked**.

## 7. Tool registry → repositories (signatures corrected)

**Read (pure, run freely):** `query_transactions(filter)`, `spend_by_category(period)`, `person_balance(name)`, `list_accounts()`, `list_categories()`, `find_merchant(name)`, **`account_balances()`** and **`card_summary(account?)`** — both backed by **new per-account SUM queries** committed in M4-1/M4-4 (§9); the prior "all-accounts balance out of scope" line is removed.

**Write (pure `ProposedWrite` values; applied via §8):**
- `add_transaction` / `add_split_transaction` → `NewTransaction` / `NewSplitTransaction`.
- `record_lend_borrow(person, amount, kind, fromAccount)` → resolves `person` via `PersonRepository.addManual(name)` for new names + a **new name-match lookup** (none exists today); builds `NewLendBorrow(accountId=fromAccount, personId, ts, …)` (**`accountId` is required** — added to the tool args). *(`PersonRepository.upsert` does not exist; corrected.)*
- `transfer(fromAccount, toAccount, amount)` → a **paired** insert (§9).
- `record_card_payment(fromAccount, card, amount)` → a `transfer` into the card (§9).
- `create_account(name, kind, creditLimit?, statementDay?, dueDay?)`, `set_budget(category, amount, period)`, `recategorize(txnId, category)`, `create_category(name, parent?)`.

## 8. Write execution — `WriteBatchCommitter` (net-new — MF2)

A single committer owns one FK-ordered `db.transaction` and is the **sole Apply writer**. Insert order: **account → category → person → (txn | lend_borrow | transfer pair)**. It calls **new non-suspending, transaction-safe primitives** the plan must add:
- `LendBorrowRepository.recordBlocking(NewLendBorrow)` — person resolved before the transaction; no async `txnRepo.add`.
- `TransactionRepository.transferBlocking(from, to, amount, groupId)` — inserts both legs (§9).
- card payment reuses `transferBlocking` (into the card).

*Test (§17):* a mixed batch (expense + lend + card payment + account-create) commits all-or-nothing under the FK-enforced `TestDatabase`; a forced failure rolls everything back.

## 9. Transfers & credit cards (canonical model — MF5 + card must-fixes)

**Transfer ledger shape (net-new):** migration `4.sqm` adds `ALTER TABLE txn ADD COLUMN transfer_group_id TEXT;` + index. A transfer = **two rows** in one `db.transaction` sharing `transfer_group_id`: a debit leg on `from`, a credit leg on `to`, both `parent_txn_id IS NULL` (so they appear in feeds — **visually distinguished** as transfers in the UI, and excluded from spend because insights already filter `kind='EXPENSE'`). `TransactionRepository.toDomain/toFullDomain` mappers extended for the new column. The existing single-leg `TRANSFER` button in `EntryScreen` is rewired to this primitive (it currently posts a broken one-row transfer).

**Card account foundation (`6.sqm`):**
```sql
ALTER TABLE account ADD COLUMN credit_limit  REAL;
ALTER TABLE account ADD COLUMN statement_day INTEGER;
ALTER TABLE account ADD COLUMN due_day       INTEGER;
```
A card is `AccountKind.CARD` with these set (nullable; non-card accounts leave them null). **M4-4 touch points (enumerated):** `domain/Account.kt` (+3 nullable fields); `AccountQueries.insertAccount` → 10 columns (or `insertCardAccount`); `AccountRepository.add/addBlocking` + `toDomain`; `create_account` tool; `AccountsScreen` creation inputs.

**Signed balance math (no per-account query exists today — net-new, modeled on `getPersonBalance`):**
```sql
cardOutstanding:
SELECT COALESCE(SUM(CASE kind
  WHEN 'EXPENSE'  THEN amount      -- a card purchase increases debt
  WHEN 'INCOME'   THEN -amount     -- a refund to the card decreases debt
  WHEN 'TRANSFER' THEN -amount     -- the credit-leg (payment into the card) decreases debt
  ELSE 0 END), 0.0)
FROM txn WHERE account_id = ? AND parent_txn_id IS NULL;
```
`available_credit = credit_limit − outstanding`. **Canonical rule (stated in the prompt + tested):** a card **purchase** = one `EXPENSE` row on the **card** account (counts as spend in insights AND increases outstanding); a **bill payment** = a `TRANSFER` pair (bank debit + card credit) that **reduces outstanding** and is **not** counted as new spend. The agent must never model a card purchase as a transfer. *Test:* paying a card bill does not change monthly spend totals and does reduce outstanding.

**`CardSummary` due-date algorithm (edge cases pinned):** statement window = `[previous statement_day, current statement_day)`; next due date = the `due_day` in the cycle **after** the most recent statement close, advancing one month when `due_day <= statement_day`. Clamp any day to `min(day, lastDayOfMonth(month))`. Inputs constrained to **1..28** at creation to avoid most short-month ambiguity; clamping covers the rest. commonTest cases: Feb, 30-day months, and `statement_day > due_day` (kotlinx-datetime `LocalDate`).

## 10. Privacy posture (rationale corrected)

- The agent is a cloud feature gated by a **distinct agent consent** (§12). **On-device STT** → audio never leaves the device.
- The SMS `Redactor` is **not** applied to agent text — corrected rationale: it preserves amounts (matches only 8+ digit runs), so the real problems are **bare-name leakage** ("Karim" isn't masked) and that masking **breaks entity resolution**. Instead: the model receives only the conversation + **scoped tool results** + the user's account/category/card names; **people-name exposure is capped to names referenced in the current turn**, not the whole `Person` table.
- Consent copy lists exactly **what leaves the device** (account names, institutions, categories, people named this turn, amounts, card limits/dates, conversation history) and **what never does** (raw SMS, full ledger, audio).

## 11. Data flow & persistence

**Migrations (as shipped — split across three files):** `4.sqm` = `txn.transfer_group_id` (M4-2), `5.sqm` = the two agent tables below (M4-3), `6.sqm` = the 3 card columns (§9, M4-4).
```sql
CREATE TABLE agent_conversation ( id TEXT NOT NULL PRIMARY KEY, title TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL );
CREATE TABLE agent_message (
    id TEXT NOT NULL PRIMARY KEY,
    conversation_id TEXT NOT NULL REFERENCES agent_conversation(id),
    role TEXT NOT NULL,                 -- 'user' | 'assistant'
    content TEXT NOT NULL,
    proposed_writes TEXT,               -- JSON of the turn's proposedWrites (for multi-turn correctness + provenance)
    applied_summary TEXT,               -- JSON of what actually committed
    created_at INTEGER NOT NULL
);
CREATE INDEX agent_message_conversation_idx ON agent_message(conversation_id, created_at);
```
**Persistence policy (corrected — MF should-fix):** persist user messages, assistant final messages, **the turn's `proposedWrites`**, and `applied_summary`. The next turn's history therefore includes what the agent proposed and what the user excluded/edited — fixing flows like "actually make Karim 3000, not 2000" after a partial Apply. Intermediate **read**-tool calls/results stay in-memory (discarded). Edited-then-applied values are recorded in `applied_summary` (the source of truth for what posted). Multiple conversations supported; UI defaults to the latest thread + "New chat".

**Agent-posted txns:** `source = VOICE` (spoken) or `CHAT` (typed). **Adding `CHAT` to the `TxnSource` enum is a required, load-bearing code change** (no DB migration — column is free `TEXT`); since `TransactionRepository` maps via `TxnSource.valueOf(source)` (which throws on unknown values), add a **tolerant fallback** (`unknown → MANUAL`) in the mapper. `capture_id = NULL` (not reusing `capture_inbox`, whose `dedup_hash` UNIQUE index would collide).

**One turn:** input (typed or mic→`SpeechToText.transcribe`) → persist user msg → `AgentLoop.run` (read loop in-memory; `final` → assistant msg + `proposedWrites`) → persist assistant msg (+ proposedWrites) → review card → **Apply** via `WriteBatchCommitter` (one `db.transaction`) → persist `applied_summary` + confirmation. Errors → `LlmError` mapped to actionable replies.

## 12. Cloud-resolution gate & consent (distinct states — should-fix)

`llmRouter.active()` returns `LlmProvider?` and collapses several states to null. The agent re-reads `CaptureConfig` (`engineMode` / `cloudConsentAt` / `cloudProvider` + key presence) and branches a **distinct message per state**: engine is on-device-only · agent cloud not consented · no provider selected · no API key. **Agent consent is distinct** from the SMS-capture `cloudConsentAt` (different data leaves the device); M4-7 adds an agent-specific consent screen with the §10 disclosure list and records its own consent timestamp.

## 13. Voice / Speech-to-text platform layer

`expect class SpeechToText` in `app.hisaab.platform`, mirroring `BiometricAuth`/`CaptureService`: `suspend isAvailable()`, `suspend requestMicPermission()`, `suspend transcribe(languageTag): SttResult`, optional `partials: Flow<String>`. `SttResult = Success | PermissionDenied | NotAvailable | Cancelled | Error` (sealed).
- **Android:** `SpeechRecognizer`; **verify the offline language pack and fail closed as `NotAvailable`** if absent (do **not** silently fall back to network — preserves "audio never leaves device"). `RECORD_AUDIO` via the `RequestMultiplePermissions` + `MutableSharedFlow` + `.first()` + mutex pattern from `CaptureService.kt`; add `RECORD_AUDIO` to the manifest.
- **iOS:** `SFSpeechRecognizer` + `AVAudioEngine`, `requiresOnDeviceRecognition = true`; check on-device locale support, else degrade to text; `Info.plist` mic + speech usage strings.
- **wasm:** unavailable stub. UI surfaces "voice unavailable in this language on this device — please type." Editable transcript before send is the safety valve.

## 14. iOS app-shell (mechanical wrapper — M4-0, parallel/deferred)

Compose↔`UIViewController` interop already exists (`build.gradle.kts` iOS framework target, `MainViewController.kt`, `ContentView.swift`); only the **`.xcodeproj` wrapper + `Info.plist` + entitlements** are missing. This is mechanical and **gates none of M4-1..M4-3** (which run as JVM commonTest), so it runs in parallel or is deferred — it is **not** the long-pole. SMS capture stays Android-only; iOS gets agent + STT + the already-shared UI.

## 15. UI surface

- `+` FAB opens a chooser: "Add manually" / "Ask the assistant" → `AgentScreen`.
- `AgentScreen`: chat thread, text input + push-to-talk mic (live partial transcript), "New chat", persisted scrollback.
- **Review card:** N heterogeneous editable rows (expense / lend / card-pay / account-create) with include/exclude + a single **Apply**. Reuse here means **extracting the field composables** out of `EntryScreen`/`EntryViewModel` into reusable widgets (the `setAmount/setAccount/setCategory/setKind` setters map cleanly) — real work, **not** a drop-in of the single-entry `EntryViewModel`.

## 16. ViewModel + DI

`AgentViewModel` → `state: StateFlow<AgentUiState>` + intents `onSend/onMicTap/onEditWrite/onToggleInclude/onApply/onNewChat`. `AppContainer` (expect) gains `speechToText`, `agentRepository`, and an `AgentLoop` factory — **declared in all three actuals**: Android/iOS provide real impls; **wasm** provides real `agentRepository` (DB) + `AgentLoop` (HTTP) and a `SpeechToText` stub (so the shared module compiles).

## 17. Testing (TDD, commonTest-first)

- `AgentLoop` with a scripted `FakeLlmProvider.complete()` → tool dispatch, **every guard** (loop bound + iteration accounting, invalid tool/args, repair-once, **no-write-in-loop via a write-throwing db**), `proposedWrites` collection.
- `WriteBatchCommitter`: mixed batch (expense + lend/borrow + card payment + account-create) commits all-or-nothing under the **FK-enforced `TestDatabase`**; forced-failure rollback.
- `cardOutstanding`/`available_credit` signed math; **paying a card bill ≠ change in monthly spend, = reduce outstanding**; `CardSummary` due-date edge cases (Feb / 30-day / `statement_day>due_day`).
- `transferBlocking` paired-leg insert + `transfer_group_id`; `AgentJson` decode; `ConversationRepository` round-trip incl. `proposed_writes`; `TxnSource.valueOf` tolerant fallback.
- Reuse `FakeProviders` + Ktor `MockEngine`. STT faked in commonTest; real STT verified on device. Target ≥ 80% on `agent` + card use-case.

## 18. v1 scope boundaries (NOT in v1)

No TTS; no cloud STT; no on-device *agent* (cloud-only); no cross-session learned memory; no changes to M3 SMS capture; `Redactor` not applied to agent text; credit-card interest/statements/rewards/EMI/push-reminders deferred (the agent answers due-date questions on demand).

## 19. Sub-slices (reordered after review)

Dependency-ordered; agent core lands first as JVM commonTest, iOS in parallel:
- **M4-1** — agent core: `AgentProvider.complete()` on the 3 cloud adapters, `AgentLoop`, `ToolRegistry`, `AgentPrompts`, `AgentJson`, guards + **read tools** incl. per-account balance queries. *DoD:* loop + all guards green in commonTest with `FakeProviders`; a **one-vendor (Gemini) reliability spike** on real prompts before expanding the tool registry.
- **M4-2** — write tools (pure `ProposedWrite`) + `WriteBatchCommitter` + new blocking primitives (`recordBlocking`, `transferBlocking`) + `TxnSource.CHAT` + tolerant mapper. *DoD:* mixed-batch atomic Apply + rollback tests pass under FK-enforced DB.
- **M4-3** — persistence: `5.sqm` (agent tables; `transfer_group_id` is `4.sqm`/M4-2, card columns are `6.sqm`/M4-4) + `ConversationRepository` (incl. `proposed_writes`). *DoD:* round-trip + migration tests.
- **M4-4** — credit cards: `Account` attrs + threading touch points (§9), `cardOutstanding`/`CardSummary`, `card_summary`/`record_card_payment` tools, transfer-pair UI distinction, card UI. **Hard predecessor of the agent card tools.** *DoD:* card math + due-date + bill-payment-not-spend tests pass.
- **M4-5** — `SpeechToText` expect/actual (Android fail-closed + iOS) + permissions. *DoD:* on-device transcribe on a real device; unavailable-locale path degrades to text.
- **M4-6** — `AgentScreen` + extracted review-card widgets + `AgentViewModel` + DI (all 3 actuals) + FAB chooser. *DoD:* end-to-end propose→edit→Apply on Android.
- **M4-7** — distinct agent cloud consent screen (§10 disclosure) + gate-state messages (§12) + `LlmError` UI mapping + polish. *DoD:* each gate state shows its message; consent recorded.
- **M4-0** — iOS Xcode wrapper + Info.plist + entitlements (mechanical; parallel/deferred). *DoD:* app builds + runs on simulator with the agent.

## 20. Risks (re-prioritized)

1. **Prompted-ReAct reliability across vendors** (top) — mitigated by guards + propose-then-Apply; de-risked by the M4-1 Gemini spike.
2. **Bengali/Banglish STT + parse quality** (top) — fail-closed offline + editable transcript.
3. **Cloud cost/latency** — token budget, per-call timeout, prompt caching, history window.
4. **Card balance/transfer infra is net-new** — new schema + signed queries + paired-leg primitive; covered by M4-2/M4-4 tests.
5. **iOS wrapper** — mechanical, low risk; not blocking.

## 21. Open questions — resolved

1. **Cards:** kept in M4 (built on M4-4 foundation, a hard predecessor of the card tools). 
2. **Default model:** Gemini (`gemini-2.0-flash`).
3. **Conversations:** multi-conversation (single rolling default + "New chat"); the body commits to this — no longer open.
