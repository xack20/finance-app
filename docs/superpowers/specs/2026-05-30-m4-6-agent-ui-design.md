# M4-6 — Conversational Agent UI + Wiring — Design Spec

- **Date:** 2026-05-30
- **Status:** Draft (approved in brainstorming)
- **Milestone:** M4, sub-slice M4-6
- **Related:** `2026-05-30-m4-conversational-agent-design.md` (master M4 spec, §15 UI / §16 ViewModel+DI); M4-1 agent core, M4-2 write execution, M4-3 persistence, M4-4 credit cards (all merged to `main`).

## 1. Goal

Turn the fully-built-and-tested agent core into a feature the user can actually use: a chat screen where the user types a money "story" or question, the agent calls read tools over the ledger and proposes writes, and the user reviews + applies them — with the conversation persisted. This is the integration slice that wires M4-1…M4-4 together behind a Compose UI and the DI root.

## 2. Scope

**In M4-6 (everything, one slice — per user decision):**
- `AgentRuntime` — the integration seam: provider resolution + assembled `ToolRegistry` + `AgentLoop` + `WriteBatchCommitter`.
- `AgentViewModel` — turn lifecycle + persistence + review-card state + intents.
- `AgentScreen` (Compose) — chat thread, text input bar, push-to-talk mic **button present but disabled** ("voice coming soon"), persisted scrollback, "New chat".
- **Review Card** — N heterogeneous editable rows (expense / income / transfer / card-payment / lend-borrow / account-create / category-create) with include/exclude toggles + a single **Apply**, reusing field composables **extracted from `EntryScreen`**.
- `+` **FAB chooser** — "Add manually" (existing `EntryScreen`) / "Ask the assistant" (`AgentScreen`).
- **Card UI** — create-card inputs (creditLimit/statementDay/dueDay, days constrained 1..28) in `AccountsScreen`; surface `card_summary` (outstanding / available / next due) on the card's account view.
- **DI** — `AppContainer` (expect) gains `conversationRepository`, an `agentRuntime` factory, and a `speechToText` placeholder; declared in **all three actuals** (Android/iOS/wasm).
- **Minimal agent-consent gate** — a distinct opt-in before any cloud turn (see §6).
- **`EntryScreen` transfer rewire** — replace the broken single-row TRANSFER post with `transferBlocking` (paired legs).

**Deferred — touchpoints only, not built here:**
- Real on-device STT → **M4-5** (the mic button is wired to a disabled state + a `speechToText` DI placeholder).
- Polished consent disclosure screen + per-gate-state error messages + full `LlmError`→UI mapping → **M4-7** (M4-6 ships a minimal but real gate).
- iOS Xcode wrapper → **M4-0** (M4-6 declares the iOS actuals so the shared module compiles; running on iOS waits for M4-0).

**Out of scope (per master spec §18):** voice output/TTS, cloud STT, on-device agent, cross-session learned memory, deferred write tools (`set_budget`/`recategorize`/`add_split_transaction`), Claude/OpenAI `complete()` parity.

## 3. Reuse (verified — all on `main`)

- **Agent core (M4-1):** `AgentLoop(provider: AgentProvider, registry: ToolRegistry, systemPrompt, maxIterations=6, maxTokens=1024).run(history, userMessage): AgentTurnResult(finalMessage, proposedWrites, cappedOut, updatedHistory)`; `AgentPrompts.system(reg, accounts, categories, todayIso, language)`; read tools `ListAccountsTool/AccountBalancesTool/ListCategoriesTool/FindMerchantTool/QueryTransactionsTool/SpendByCategoryTool/PersonBalanceTool` + `CardSummaryTool` (M4-4); `ToolRegistry(readTools, writeDescriptors)`. `GeminiProvider : LlmProvider, AgentProvider`.
- **Write execution (M4-2):** `WriteBatchCommitter(db, accounts, categories, persons, txns, lendBorrow).apply(writes: List<ProposedWrite>): AppliedSummary`; `agentWriteDescriptors()`; `WriteIntent.parse`.
- **Persistence (M4-3):** `ConversationRepository(db, now)` — `createConversation/appendUserMessage/appendAssistantMessage/observeMessages/observeConversations/latestConversationId`; `AgentMessage(role, content, proposedWrites, appliedSummary, …)`.
- **Existing UI/DI patterns:** `AppContainer` (expect) + Android/iOS/wasm actuals (hand-wired DI); `MainGraph` routing; `EntryViewModel`/`EntryScreen` (field setters `setAmount/setAccount/setCategory/setKind` — the composables to extract); `ReviewInboxScreen`/`ReviewInboxViewModel` (the closest review/confirm surface to mirror); `AccountsScreen`; the app theme/palette. ViewModel pattern: `state: StateFlow<…>` + intent functions, `scope` on `Dispatchers.Main`.
- **Provider/key/consent plumbing:** `llmRouter.active(): LlmProvider?`; BYO key in `SecureStorage` (`llm_api_key_<PROVIDER>`); `CaptureConfigRepository` (engine/cloud-provider/consent for SMS — the agent consent is **distinct**, see §6).

## 4. Components

- **`AgentRuntime`** (`commonMain`, `app.hisaab.agent`): constructed per-turn or held by the VM. Responsibilities: (a) resolve an `AgentProvider` for the agent (selected cloud provider + BYO key), returning a typed unavailability reason if none; (b) hold the assembled `ToolRegistry` (all read tools + write descriptors) and build the system prompt via `AgentPrompts`; (c) `run(history, userMessage): AgentTurnResult` delegating to `AgentLoop`; (d) `apply(writes): AppliedSummary` delegating to `WriteBatchCommitter`. The DB handle is injected ONLY into the committer at apply — `AgentLoop` stays db-free (preserves M4-2's no-write-in-loop guarantee). Single responsibility, unit-testable with `FakeAgentProvider` + in-memory repos + `TestDatabase`.
- **`AgentViewModel`**: `state: StateFlow<AgentUiState>` (current conversation id, messages, input text, in-flight flag, review-card rows, gate state, error) + intents `onSend/onMicTap/onEditWrite/onToggleInclude/onApply/onNewChat/onConsent`. Pure-Kotlin; commonTest'd.
- **`AgentScreen` + `ReviewCard`** (Compose, `commonMain`): observe `AgentUiState`; render thread (user/assistant bubbles), input bar (text + disabled mic), and the review card. Follows the existing theme/components.
- **Extracted Entry field widgets** (`commonMain`): pull amount/account/category/kind/notes editors out of `EntryScreen` into reusable composables used by BOTH `EntryScreen` and `ReviewCard` (DRY; master §15).
- **FAB chooser**: in the main scaffold — a small chooser sheet/menu routing to manual entry vs the agent.
- **Card UI**: `AccountsScreen` create-card inputs (kind=CARD reveals creditLimit/statementDay/dueDay; days validated 1..28); a card summary view (outstanding/available/next-due) backed by `CardSummaryTool`/`AccountRepository.cardOutstanding` + `CardSummaryCalculator`.
- **DI additions** to `AppContainer` (expect) + 3 actuals: `conversationRepository: ConversationRepository`; `agentRuntime(...)` factory (needs db-bound repos + the provider resolver + the committer); `speechToText` placeholder (a no-op/`NotAvailable` stub until M4-5).

## 5. Turn lifecycle & data flow

1. User opens `AgentScreen` (via FAB chooser). VM loads the latest conversation (or starts one) and observes its messages.
2. `onSend(text)`: persist the user message (`ConversationRepository.appendUserMessage`); set in-flight.
3. **Gate check** (§6): if the agent isn't consented or no provider/key resolves → show the gate/"set up assistant" state; do not call the cloud.
4. `AgentRuntime.run(history, text)`: builds messages (system prompt + persisted history + new user msg), runs `AgentLoop` (read tools execute in-loop; writes only collected as `ProposedWrite`s).
5. Persist the assistant final message + its `proposedWrites` (`appendAssistantMessage`). Render the assistant reply + the **Review Card** populated from `proposedWrites`.
6. User edits row values / toggles include. `onApply`: `AgentRuntime.apply(includedWrites)` → `WriteBatchCommitter` (one `db.transaction`, all-or-nothing) → persist `applied_summary` → confirmation (e.g., "Saved 3 items"). On failure: rolled back + "nothing was saved" (earned by propose-then-Apply).
7. `onNewChat`: create a fresh conversation. Intermediate read-tool calls are NOT persisted (in-memory only, master §11).

## 6. Provider resolution & consent gate (minimal)

The agent is a **cloud** feature whose data exposure (account/category names, people named this turn, amounts, card limits/dates, conversation) differs from SMS capture, so it requires a **distinct agent consent** (master §10/§12).
- **Minimal gate (M4-6):** before the first cloud turn, show a concise consent prompt listing what leaves the device / what never does (raw SMS, full ledger, audio); record an agent-consent flag (a dedicated key in `SecureStorage` or a `CaptureConfig`-style field — plan decides). The agent runs only if **consented AND** a cloud `AgentProvider` resolves (selected provider + non-blank BYO key). Otherwise an inline message points to Settings.
- **Provider resolution:** resolve the configured cloud provider as an `AgentProvider` using the stored key; `GeminiProvider` already implements `AgentProvider`. If the active provider is on-device/none → "the assistant needs a cloud model + key" state.
- **Deferred to M4-7:** the polished disclosure screen, per-state distinct messages (engine on-device-only · not consented · no provider · no key), and full `LlmError`→message mapping.

## 7. Tool registry assembly

A single place builds the production `ToolRegistry`: read tools constructed over the db-bound repos (`ListAccountsTool(accountRepo)`, `AccountBalancesTool(accountRepo)`, `ListCategoriesTool(categoryRepo)`, `FindMerchantTool(merchantRepo)`, `QueryTransactionsTool(txnRepo)`, `SpendByCategoryTool(insightRepo)`, `PersonBalanceTool(personRepo)`, `CardSummaryTool(accountRepo)`) + `writeDescriptors = agentWriteDescriptors()`. The system prompt is built from the registry + live accounts/categories/today/language. (Currently nothing assembles this in production — M4-6 adds it.)

## 8. Card UI

- `AccountsScreen`: the create-account form, when kind=CARD, reveals optional `creditLimit` (number) + `statementDay`/`dueDay` (int pickers constrained 1..28). Submits via `AccountRepository.add(... creditLimit, statementDay, dueDay)`.
- A card summary view (in the account row/detail) shows outstanding, available credit, and next due date, computed via `AccountRepository.cardOutstanding` + `CardSummaryCalculator` (or `CardSummaryTool` output). Read-only display; no new domain logic (all built in M4-4).

## 9. `EntryScreen` transfer rewire

The current TRANSFER option posts a single broken row. Rewire the manual transfer path to `TransactionRepository.transferBlocking(from, to, amount, ts, notes)` inside a `db.transaction` (paired legs sharing `transfer_group_id`), consistent with the agent's transfer. Keep the manual-entry UX; only the persistence call changes.

## 10. Testing

- **commonTest (subagent TDD loop):** `AgentRuntime` (FakeAgentProvider scripted turns + in-memory repos + `WriteBatchCommitter` over FK-enforced `TestDatabase`): run→propose, apply→commit+persist, gate (no consent / no provider), error/rollback ("nothing saved"). `AgentViewModel`: send→persist→review→edit/toggle→apply→persist `applied_summary`, new-chat, gate state, in-flight. ToolRegistry assembly (all expected tools present). Extracted Entry widgets: pure-logic where applicable.
- **Instrumented / emulator (`androidInstrumentedTest`, mirrors `ReviewInboxScreenTest`):** `AgentScreen` renders thread + input; mic shows disabled; `ReviewCard` renders heterogeneous rows, edit/toggle/Apply callbacks fire; FAB chooser routes; card-create inputs validate.
- Reuse `FakeAgentProvider`; manual end-to-end (propose→edit→Apply on the emulator) with the rotated Gemini key + the dev test-OTP for sign-in.

## 11. Error handling

- Transport/key/decoding errors from the loop → `LlmError` mapped to a short, actionable assistant message; the turn ends with no writes ("nothing was saved").
- Apply failures (FK/validation) → committer rolls back the whole batch; VM surfaces the error + leaves the review card intact for retry.
- Gate-not-met → no cloud call; inline guidance.

## 12. Sub-task outline (for writing-plans)

Rough ordering: (1) extract Entry field composables; (2) `AgentRuntime` + provider resolver + registry assembly (commonTest); (3) `AgentViewModel` + turn lifecycle + gate (commonTest); (4) DI additions across 3 actuals (`conversationRepository`, `agentRuntime`, `speechToText` stub); (5) `AgentScreen` + chat thread (Compose + instrumented); (6) `ReviewCard` reusing the extracted widgets (Compose + instrumented); (7) minimal consent gate UI; (8) FAB chooser + `MainGraph` route; (9) card-create inputs + card summary in `AccountsScreen`; (10) `EntryScreen` transfer rewire; (11) wire into navigation + manual emulator smoke. (writing-plans finalizes tasks + exact signatures.)

## 13. Next slices

M4-5 STT (replaces the disabled mic); M4-7 consent screen + gate-state messages + `LlmError` UI; M4-0 iOS Xcode wrapper; fast-follows: deferred write tools, Claude/OpenAI `complete()` parity.
