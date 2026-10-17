# Agentic Hisaab — design

Status: approved direction (user, 2026-10-17: "make our full system agentic… nothing can be deterministic";
"Yes proceed everything"). Calls marked *(my call)* were made on the user's behalf after they opted out of questions.

## Why

The capture engine is a stack of hand-written rules: bank SMS regex templates, a money-word pre-filter, a
confidence score, an auto-post threshold, a sender→account registry, a 10-minute cross-channel duplicate window.
Every gap between rules lands on the user as manual work: on 2026-10-17 the phone had 2,938 items in Review —
mostly promos Claude had already called "not a payment" — and Monthly looked empty. The user wants the app to
*think*, not follow rules.

## The rule: the agent decides, code guarantees

The agent (the user's chosen cloud model, Claude Opus 5.5 today) makes every judgment: is this a payment, which
account, which category, is it the same payment as another message, is it a transfer between own accounts, who
is the person, what to ask the user. Plain code is kept only where a judgment call would be wrong by nature:

- **Arithmetic.** Totals, balances, month sums are computed by SQL from the ledger. Models make arithmetic
  slips; over thousands of rows they compound.
- **Writing.** The agent *proposes*; code validates (amount > 0, account exists, message belongs to the batch,
  each message decided once) and commits a batch all-or-nothing. The transaction time is the message's
  received time, never a model-computed date.
- **Security.** Encryption, keys, login. One-time codes (OTP/PIN/verification codes) are masked on the phone
  before anything leaves it; the existing phone/account-number redaction still applies when enabled.
- **Spending.** A daily cap on AI calls (600/day *(my call)*), shown in Settings, so an 11,000-message import
  can't run up an unbounded bill. Hitting it pauses reading until tomorrow; nothing is lost.
- **Plumbing.** Reading SMS/email/notifications, exact-duplicate storage (same sender+body = same row),
  background service, sync. No judgment involved.

## Autonomy *(my call)*

The agent adds to the ledger on its own. The user can undo anything (delete the transaction; the original
message stays linked). It asks only what it cannot work out, as a question with optional quick answers. A
user's answer is fed back with the messages, and the agent can turn it into a lasting memory.

## Phases (order *(my call)*: capture first — that's where it hurts)

1. **Capture agent** — replaces templates, pre-filter, scoring, thresholds, sender mapping, duplicate cleanup,
   and the Review card pile with: a message queue → batched agent → validated commit; Questions instead of
   Review; agent memory ("What Hisaab has learned"); contacts (names for phone numbers in transfers); daily cap.
   Existing Review items are handed to the agent; existing sender→account mappings become memories.
2. **Today & Month** — numbers stay computed; the agent writes "Hisaab's take" (what changed, why, what to
   watch) and answers "why" questions in place.
3. **Setup, budgets, people** — agentic onboarding (which banks/wallets/cards you use → accounts), budget
   proposals from real spending, lending/borrowing from contact transfers, all as proposals the user approves.
4. **Everything else** — remaining screens get an agent entry point (voice entry already uses the model).

Each phase ships an APK on its own.

## Phase 1 detail

**Flow.** Every source (SMS receiver, notification listener, IMAP, iOS relay) hands a message to a queue:
insert into `capture_inbox` as `QUEUED` (unique on the existing dedup hash) and wake the worker. The worker
waits a few seconds (an SMS, its app notification and its email arrive together), then takes the oldest 25
queued messages and runs the agent:

- Context: today's date, accounts (name/kind/institution), categories, people, memories, and per message: id,
  channel, sender, received time, body (contact names added for phone numbers, then masked), plus any earlier
  question about it and the user's answer.
- Read tools: the chat agent's (accounts, balances, categories, recent transactions…) plus
  `find_similar_transactions` (same amount within ±2 days in the ledger) so it doesn't double-post a payment
  whose other copy was in an earlier batch.
- Decisions (one per message; several messages may share one when they are the same payment):
  `post_transaction`, `post_transfer`, `post_lend_borrow`, `create_account`, `skip` (with a reason: promo,
  OTP, balance alert, duplicate…), `ask` (question + optional quick answers), `remember` (a fact).
- Validation errors go back to the agent once to fix. Messages it leaves undecided stay queued; after 3
  attempts they become a question ("I couldn't work out this message").

**States.** `QUEUED` → `AUTO_POSTED` (first message of a posted payment, linked to its transaction) /
`DISMISSED` (skipped, or another copy of a posted payment; reason in `parse_error`) / `PENDING` (waiting on a
question). Answering re-queues the question's messages.

**No AI available** (no key, no consent, offline, cap reached): messages wait in the queue; the Sync status
says why. There is no rule-based fallback — the user chose fully agentic.

**Removed:** `CapturePipeline`, `SmsPreFilter`, `BankTemplate`, `ConfidenceScorer`, `AccountMatcher`,
`DuplicateCleanup`, waiting-sender registration, the Review card list with Confirm/Edit/"Confirm all ≥ N%", the
auto-post confidence slider, "New senders detected", "Find duplicate payments".

**UI.** Today banner "N questions from Hisaab" → Questions screen (question, the messages it's about, quick
answers, free-text answer, "Not a payment"). Auto-capture settings keep channels, permissions, email, AI
provider/key/consent, sync status and history import, and gain: contacts permission, "AI calls today: N of
600", and "What Hisaab has learned" (memories, deletable).

**Cost note.** Every message (not only "money-looking" ones) now goes to the cloud model, in batches of 25.
The ~11.5k-SMS history plus the ~2.9k old Review items is roughly 600 batches — about a day at the cap.

## Phase 1b — Claude via the Mac (subscription), 2026-10-17

The user ran out of API credit and asked to use their Claude subscription the way `local-lmcanvas` does: by
driving the Claude Code CLI installed on their Mac (`claude -p`, logged in with the subscription). A phone can't
run that CLI, so the Mac companion becomes the phone's model endpoint. Only the model call moves; the queue,
validation, ledger writes and questions stay on the phone.

- **Provider choice:** a new cloud provider "Claude via my Mac". When selected, every `AgentProvider.complete`
  (capture agent and chat assistant) goes to the Mac instead of `api.anthropic.com`. No API key is used.
- **Transport *(my call)*:** direct HTTP from the phone to the Mac (same Wi-Fi, or a Tailscale address), port
  47800. The user types the address the companion shows. Bodies are sealed with XChaCha20-Poly1305 under a
  bridge key derived (HKDF, "hisaab/bridge/v1") from the relay key both devices already derive from the
  recovery phrase — so the channel is confidential and authenticated even though it is plain HTTP. Requests
  carry an id and a send time; the Mac rejects anything older than 5 minutes or seen before.
- **On the Mac:** `claude -p --output-format json --system-prompt-file … --tools "" --strict-mcp-config
  --setting-sources "" --no-session-persistence`, run in an empty folder with `ANTHROPIC_API_KEY` /
  `ANTHROPIC_AUTH_TOKEN` removed from the environment (so the subscription login is used, never an API key),
  the conversation replayed as a transcript on stdin, one request at a time, 5-minute timeout.
- **Pairing:** the companion's existing recovery-phrase pairing. Supabase sign-in becomes optional
  ("bridge only") because an Android phone reads its own SMS.
- **Failures:** Mac unreachable → network error (the worker backs off and messages wait); subscription usage
  limit → rate-limited (backs off); a CLI error → provider error.
- **Rules:** personal use only — Anthropic doesn't allow products to offer claude.ai subscription login to
  others without approval; shipped builds for other people keep API keys.
