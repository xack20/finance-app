# M4 — Conversational Money Assistant (text + voice) — Design Spec

- **Date:** 2026-05-30
- **Status:** Draft (awaiting user review)
- **Milestone:** M4
- **Related:** `2026-05-27-hisaab-master-architecture-design.md` (frames M4 as "Agent runtime + voice"); M3 capture + LLM provider layer; `docs/tech-debt.md` (M13 on-device model, M15 deferred voice/email/OCR).

## 1. Goal

Let the user record and manage their finances by **talking or typing to an assistant** in plain English / Bengali / Banglish. The user describes activity as a free-form story ("paid 500 for lunch with cash, lent Karim 2000, and my BRAC card bill is due") or asks questions ("how much did I spend on food this month?"); a multi-turn LLM **agent** understands it, calls tools over the existing ledger, and proposes any writes for the user to review and apply.

This is the **full money-assistant** vision: the agent can **log**, **query**, and **manage** (accounts, budgets, categories, lend/borrow, **credit cards**) through a tool registry.

## 2. Decisions (from brainstorming, 2026-05-30)

| Decision | Choice | Rationale |
|---|---|---|
| Scope | Full money assistant — log + query + manage | User chose the complete vision over a one-shot extractor. |
| Brain | **Cloud agent, opt-in + BYO key**, behind the existing `cloudConsentAt` gate | On-device Gemma 1B can't drive reliable multi-turn tool-calling; on-device stays the default for **passive SMS capture**, the agent is an explicit, labeled cloud feature. |
| Loop | **Prompted JSON-tool (ReAct), vendor-agnostic** | One loop across all 3 providers, reusing forced-JSON output + `LlmJson`; keeps an on-device-agent path open. Reliability handled via guards (§5). |
| Write safety | **Batch review card → atomic Apply**; reads run freely | Nothing touches the ledger without the user's tap; reuses the multi-item review surface. |
| Voice | **On-device STT, input only** (no TTS, no cloud STT) | Audio never leaves the device; only transcribed text reaches the cloud agent. |
| Platform | **Android + iOS** | Requires standing up the iOS Xcode project (M4-0). |
| Memory | **Persist chat thread (encrypted)**; durable facts → `Person` records | No separate learned-memory subsystem in v1. |
| Credit cards | **Account-model foundation + agent integration** (this spec) | Added per user request ("I used to use credit cards a lot"); reuses `AccountKind.CARD` + `TxnKind.TRANSFER`. |

## 3. Architecture

New `commonMain` package **`app.hisaab.agent`** (shared across Android + iOS):

- **`AgentLoop`** — orchestrates one user turn: builds the request, runs the ReAct loop, emits a stream of `AgentStep` (tool-call, tool-result, final message, proposed writes).
- **`ToolRegistry`** + **`AgentTools.kt`** — each tool = name, JSON arg schema (injected into the prompt), and an executor bound to an **existing** repository.
- **`AgentPrompts.kt`** — system prompt: tool protocol, JSON action envelope, today's date (relative-date resolution), the user's real accounts + categories (incl. card metadata), English/Bengali/Banglish guidance.
- **`AgentJson.kt`** — decodes the model's envelope, extending `LlmJson` lenient-parse + fence-strip helpers.
- **`ProposedWrite` / `WriteBatch`** — staged write actions awaiting user **Apply**.

**Reused from M3 (no rewrite):** `LlmRouter.active()` (engine/consent/BYO-key gating — inherited whole); the 3 cloud adapters' transport/auth (`CloudHttp`, `LlmError`, the `apiKey:()->String?` read-at-call-time lambda); the `llm_api_key_<PROVIDER>` SecureStorage keys; the hardened atomic `db.transaction { candidate → txn }` post primitive and `TransactionRepository.addBlocking(captureId=...)`.

**New platform layer:** `expect class SpeechToText` (§9). **New iOS app shell** (§10). **New persistence:** `agent_conversation` + `agent_message` tables and credit-card `account` columns (§8, §10-cards).

## 4. The agent loop (prompted ReAct)

1. **Request** = system prompt (tools + protocol + accounts/categories + today + language) + persisted thread history + new user message.
2. `llmRouter.active()` → must resolve to a **cloud** provider. If on-device-only or no key → return an actionable message ("Turn on the cloud assistant and add an API key in Settings"), never a silent failure.
3. Model returns **one** JSON envelope per step:
   - `{"thought":"…","action":{"tool":"spend_by_category","args":{…}}}` → execute (read) → append result to the **in-memory** working transcript → loop; **or**
   - `{"thought":"…","final":{"message":"…","proposedWrites":[{ "tool":…, "args":… }]}}` → stop; surface message + review card.

## 5. Reliability guards (core mitigation for prompted tool-calling)

- **Writes never execute inside the loop.** Write tools only *describe* an intended mutation; they are collected as `ProposedWrite`s and committed **only** after the user taps **Apply**. The model can propose; only the user commits. This neutralizes most hallucinated/looping-tool risk for money.
- **Read tools are pure** (no side effects), so a stray/duplicate read call is harmless.
- **Bounded loop:** ≤ 6 iterations per user turn; exceeding → graceful "I need a bit more detail."
- **Validation:** every tool name checked against the registry; every arg validated against the tool's declared schema (unknown tool / bad args → rejected, fed back to the model once).
- **Self-repair:** a malformed envelope triggers one repair-retry with the parse error, then a graceful fallback message.
- **Token budget:** raise the per-call max tokens above the SMS path's small budget; cap conversation history sent (sliding window) to bound cost.

## 6. Tool registry → existing repositories

**Read tools (run freely, side-effect-free):**
- `query_transactions(filter)`, `spend_by_category(period)`, `account_balances()`, `person_balance(name)` (lend/borrow), `list_accounts()`, `list_categories()`, `find_merchant(name)`.
- `card_summary(account?)` — outstanding balance, available credit, statement + next due date (§10-cards).

**Write tools (collected as `ProposedWrite`, applied only on Apply):**
- `add_transaction(...)` / `add_split_transaction(...)` → `NewTransaction` / `NewSplitTransaction`.
- `record_lend_borrow(person, amount, kind)` → `LendBorrowRepository.record` + `PersonRepository.upsert`.
- `transfer(fromAccount, toAccount, amount)` → paired `TxnKind.TRANSFER` rows.
- `record_card_payment(fromAccount, card, amount)` → a `transfer` into the card (§10-cards).
- `create_account(name, kind, …card attrs)`, `set_budget(category, amount, period)`, `recategorize(txnId, category)`, `create_category(name, parent?)`.

Each write tool's executor builds a domain object inserted in the **single atomic `db.transaction`** on Apply, in FK-correct order.

## 7. Privacy posture

- The agent is a **clearly-labeled cloud feature** gated by `cloudConsentAt` + a stored BYO key; on-device remains the default for passive SMS capture.
- **On-device STT** means **audio never leaves the device** — only transcribed text does.
- The SMS `Redactor` is **not** applied to agent text (its ≥8-digit→`[ACCT]` rule corrupts the very amounts being parsed, and bare names leak). Instead the model receives only the conversation + **scoped tool results** (e.g., a single category total), never a full ledger dump. The cloud-data tradeoff is surfaced explicitly at consent time.

## 8. Data flow & persistence

**New tables (migration `4.sqm`, matching existing conventions — `TEXT` ids, `INTEGER` epoch-ms, `TEXT` enums):**

```sql
CREATE TABLE agent_conversation (
    id          TEXT NOT NULL PRIMARY KEY,
    title       TEXT,
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL
);

CREATE TABLE agent_message (
    id               TEXT NOT NULL PRIMARY KEY,
    conversation_id  TEXT NOT NULL REFERENCES agent_conversation(id),
    role             TEXT NOT NULL,           -- 'user' | 'assistant'
    content          TEXT NOT NULL,           -- user text (post-STT) or assistant final message
    applied_summary  TEXT,                    -- e.g. '{"posted":3,"accounts":1}' when a batch committed
    created_at       INTEGER NOT NULL
);
CREATE INDEX agent_message_conversation_idx ON agent_message(conversation_id, created_at);
```

**Persistence policy — persist the *thread*, not the *trace*:** store user messages, assistant final messages, and an `applied_summary` when a write batch commits. Intermediate ReAct steps (tool calls + results) stay **in-memory** during the loop and are discarded — keeping the encrypted DB lean and resume-context clean. Multiple conversations supported; UI defaults to the latest thread with optional "New chat."

**Agent-posted transactions:** `source = VOICE` (spoken) or `CHAT` (typed — new `TxnSource` enum value, **no migration**; column is free `TEXT`); `capture_id = NULL` (we do **not** reuse `capture_inbox`, avoiding its `dedup_hash` UNIQUE collision). Provenance = the source tag; durable people facts → `Person` records.

**One turn, end to end:**
1. **Input** → user types, or taps mic → `SpeechToText.transcribe(locale)` → transcript. Either path yields a user-message string.
2. **Persist** the user message (`role='user'`).
3. **`AgentLoop.run(conversationId, text)`** → load thread history → build prompt → `llmRouter.active()` → decode envelope → read tools loop in-memory; `final` stops with assistant message + `proposedWrites[]`. (Guards §5.)
4. **Persist** the assistant message (`role='assistant'`).
5. **Review** → non-empty `proposedWrites` → batch review card (editable rows, include/exclude).
6. **Apply** → all included writes commit in **one `db.transaction`** (FK-correct order); chat shows a confirmation and writes `applied_summary`.
7. **Errors** → `LlmError` mapped to actionable chat replies (no key / rate-limited / decode-failed → retry), never a silent drop.

## 9. Voice / Speech-to-text platform layer

New `expect class SpeechToText` in `app.hisaab.platform`, mirroring the `BiometricAuth` / `CaptureService` expect-actual + permission idiom:

- **API:** `suspend isAvailable()`, `suspend requestMicPermission()`, `suspend transcribe(languageTag): SttResult`, optional `partials: Flow<String>` (live transcript). `SttResult = Success(text) | PermissionDenied | NotAvailable | Cancelled | Error(msg)` (sealed, like `BiometricResult`).
- **Android actual:** `SpeechRecognizer` with `EXTRA_PREFER_OFFLINE = true`; `RECORD_AUDIO` granted via the exact `RequestMultiplePermissions` launcher + `MutableSharedFlow` + `.first()` + mutex pattern from `CaptureService.kt`; add `RECORD_AUDIO` to the manifest. Locale picked per recording (en-US / bn-BD).
- **iOS actual:** `SFSpeechRecognizer` + `AVAudioEngine`, `requiresOnDeviceRecognition = true`; `Info.plist` `NSSpeechRecognitionUsageDescription` + `NSMicrophoneUsageDescription`.
- **wasm:** unavailable stub. Wire into `expect AppContainer` + all actuals.

**Risk:** Bengali/Banglish on-device STT quality is unproven on real devices (master-arch §11 Q4); degrade gracefully to the chosen locale and let the user correct the transcript before sending.

## 10. iOS app-shell standup (largest net-new infra — sub-slice M4-0)

The repo currently has only `iOSApp.swift` + `ContentView.swift` — **no `.xcodeproj`**. M4 requires a real iOS project that hosts the Compose `UIViewController`, links the shared KMP framework, declares `Info.plist` usage strings (mic, speech) + outbound-network entitlement, and builds on simulator/device. SMS capture stays Android-only; iOS gets the agent + STT + the already-shared onboarding/lock/today UI. This is the **single riskiest chunk** and is scoped first so the rest of M4 has somewhere to run on iOS.

## 10-cards. Credit cards

Practical card support, scoped to what a daily card user needs; reuses existing primitives.

**Account-model foundation (migration `4.sqm`, add nullable columns to `account`):**
```sql
ALTER TABLE account ADD COLUMN credit_limit  REAL;     -- null for non-card accounts
ALTER TABLE account ADD COLUMN statement_day INTEGER;  -- 1..28/31, day of month statement closes
ALTER TABLE account ADD COLUMN due_day       INTEGER;  -- 1..28/31, day of month payment due
```
A credit card is `AccountKind.CARD` (**already exists**) with these attributes set. `Account` domain model + `AccountRepository` mapper/insert extended to carry them (nullable; non-card accounts leave them null).

**Debt-balance semantics:** for a `CARD`, the running balance represents **outstanding debt**: card **expenses increase** it, **payments/refunds decrease** it. `outstanding = Σ(card expenses) − Σ(payments into card)`. `available_credit = credit_limit − outstanding`. A small `CardSummary` use-case computes outstanding, available credit, current statement window, and **next due date** (derived from `statement_day` / `due_day` + today). (If a general per-account balance computation does not yet exist, this use-case introduces the card-specific computation; a full all-accounts balance view is out of v1 scope.)

**Bill payment = transfer:** paying a card bill is a `TxnKind.TRANSFER` from a bank/cash account into the card account (`record_card_payment` tool, or manual transfer). No new ledger primitive.

**Agent integration:** the prompt teaches the agent that cards are `CARD` accounts with limits/due dates; tools `card_summary` (read) and `record_card_payment` (write) plus the generic `add_transaction` (card as the account) and `create_account(kind=CARD, credit_limit, statement_day, due_day)`. The agent can answer "what's my card balance / available credit / when's it due" and record "spent 3000 on my BRAC card" or "paid 10000 toward my card."

**UI:** card accounts display outstanding + available credit + next due date (a card-aware account row/summary); creation flow accepts limit + statement/due day.

**Deferred (NOT v1):** interest/APR accrual modeling, statement PDF/import, rewards/points, EMI/installment plans, automated due-date **push** reminders (the agent answers due-date questions on demand instead).

> Scoping note for review: credit cards are cross-cutting (account model, not only the agent). They are included here per request; if preferred, the account-model foundation can be split into its own short milestone with M4 depending on it. Flag at review.

## 11. UI surface

- **Entry:** the `+` FAB (today → `navigate("entry")`) opens a chooser — **"Add manually"** / **"Ask the assistant"** → `AgentScreen`. (Lighter than a 5th bottom-nav tab.)
- **`AgentScreen`:** chat thread (user/assistant bubbles), text input + push-to-talk **mic** button (live partial transcript while recording), "New chat" in the top bar, persisted scrollback.
- **Review card** (inline in the thread when `proposedWrites` arrive), reusing `EntryViewModel` field widgets — one editable row per write with include/exclude and a single **Apply**:

```
┌─ Review 3 actions ─────────────────┐
│ ✓ Expense   ৳500    Lunch    Cash ✎ │
│ ✓ Lend      ৳2,000  to Karim      ✎ │
│ ✓ Card pay  ৳10,000 → BRAC card   ✎ │
│ ☐ Account   "BRAC card"  (excluded) │
│                   [ Apply 3 ] [Cancel]│
└─────────────────────────────────────┘
```

## 12. ViewModel + DI

- **`AgentViewModel`** → `state: StateFlow<AgentUiState>` (messages, isThinking, sttState, pendingBatch, error) + intents `onSend / onMicTap / onEditWrite / onToggleInclude / onApply / onNewChat`.
- **`AppContainer`** gains: `speechToText: SpeechToText`, `agentRepository` (conversation/message persistence), and an `AgentLoop` factory (wraps `llmRouter` + `ToolRegistry` bound to existing repos). Wired in all platform actuals.

## 13. Testing (TDD, commonTest-first)

- `AgentLoop` driven by a scripted `FakeLlmProvider` emitting action/final envelopes → assert tool dispatch, **every guard** (max-iters, invalid tool/args rejected, malformed→repair), `proposedWrites` collection, and **atomic Apply under the FK-enforced `TestDatabase`** (mixed batch incl. lend/borrow + card payment).
- Tool executors against in-memory repos; `card_summary` debt/available-credit/due-date math; `AgentJson` decode (fences, partial, multi-action); `ConversationRepository` round-trip.
- Reuse `FakeProviders` + Ktor `MockEngine`. STT faked in commonTest; real STT verified manually on emulator/simulator.
- Target ≥ 80% on the new `agent` package + card use-case.

## 14. v1 scope boundaries (explicitly NOT in v1)

No TTS; no cloud STT; no on-device *agent* (cloud-only; prompted-JSON loop keeps that door open); no cross-session learned memory; no changes to M3 SMS capture; `Redactor` not applied to agent text; credit-card interest/statements/rewards/EMI/push-reminders deferred (§10-cards).

## 15. Sub-slices (for the implementation plan)

- **M4-0** — iOS app-shell bootstrap (Xcode project + shared framework + Info.plist + entitlements).
- **M4-1** — agent core: `AgentLoop`, `ToolRegistry`, `AgentPrompts`, `AgentJson`, guards + **read** tools (commonTest-driven).
- **M4-2** — **write** tools + `ProposedWrite` + atomic batch Apply.
- **M4-3** — persistence: `4.sqm` (agent tables + card columns) + `ConversationRepository`.
- **M4-4** — credit-card foundation: `Account` attrs, `CardSummary` use-case, `card_summary` / `record_card_payment` tools, card UI bits.
- **M4-5** — `SpeechToText` expect/actual (Android + iOS) + permissions.
- **M4-6** — `AgentScreen` + review card UI + `AgentViewModel` + DI + FAB chooser.
- **M4-7** — cloud-agent consent/onboarding + `LlmError` UI mapping + polish.

## 16. Risks

- **Prompted tool-calling reliability** — mitigated by guards (§5) and propose-then-Apply; still needs real-model validation across the 3 vendors.
- **iOS standup** — no Xcode project today; M4-0 is prerequisite infra and the biggest unknown.
- **Bengali/Banglish STT + parse quality** — unproven; user-editable transcript before send is the safety valve.
- **Cloud cost/latency** — multi-step ReAct multiplies calls; bound by max-iters + history window.
- **Credit-card balance computation** — introduces account-balance math that may not fully exist yet; scoped to the card use-case in v1.

## 17. Open questions (for review)

1. Credit cards: keep folded into M4, or split the account-model foundation into its own milestone M4 depends on?
2. Default cloud provider/model suggestion for the agent (Claude / Gemini / OpenAI) given cost vs. tool-following quality?
3. Single rolling conversation vs. a conversations list in v1 UI?
