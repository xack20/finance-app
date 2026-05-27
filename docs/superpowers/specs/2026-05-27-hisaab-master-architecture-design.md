# Hisaab — Master Architecture Design

**Status:** Draft v0.1 (for review)
**Date:** 2026-05-27
**Author:** Zakaria Hossain
**Product context:** [`docs/idea.md`](../../idea.md)

---

## Summary

Hisaab is a cross-platform commercial finance app (Android + iOS + Web) for Bangladesh, built on **Kotlin Multiplatform + Compose Multiplatform**. All sensitive data processing — SMS parsing, email parsing, transaction categorization, the AI advisor — runs **on-device**. The backend (**Supabase**) holds only end-to-end-encrypted blobs it cannot decrypt. Privacy-max is both the technical architecture and the marketing line.

This document defines the system shape — subsystems, technology choices, data model overview, security model, and internal phasing. Per-subsystem detailed specs are written as each phase begins (e.g., `docs/superpowers/specs/YYYY-MM-DD-capture-engine-design.md`).

## 1. Context and goals

### Goals

- Capture every financial event automatically where possible (SMS, email, voice, manual, OCR)
- Unify into one timeline ledger including informal lend/borrow as a first-class concept
- Run an agentic AI advisor that knows the user's history and coaches proactively
- Premium editorial UI on all three platforms, with calm motion
- E2EE backend storage; on-device LLMs for all sensitive processing
- Ship "whole product" v1 in 9–12 months

### Non-goals (v1)

- Investment tracking, FDR, stock portfolios — Phase 2
- Direct bank API integration via Plaid-style sync — Phase 2 (BD Open Banking framework still maturing)
- Tax preparation — Phase 3+
- Group/household features beyond Family plan basic sharing — Phase 3+
- Cryptocurrency tracking — out of scope
- Multi-user shared books / small-business accounting — out of scope

## 2. Constraints

- **iOS cannot read SMS** (Apple platform restriction). iOS users rely on email, voice, manual, and "forward bank SMS via Share Sheet."
- **Google Play restricts SMS permission** to apps where it is essential — requires Google review. Must justify in submission.
- **Privacy-max means LLM advisor quality is bounded by on-device model capability** — Gemini Nano (Android), Apple Foundation Models (iOS), bundled Gemma 3n fallback.
- **Bangladesh open banking framework is nascent**; bKash/Nagad APIs require partnership.
- **Bangladesh data residency rules are not strict in 2026** but watch for changes.

## 3. High-level architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│   Clients (Android, iOS, Web/WASM)                                      │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │ Compose Multiplatform UI                                          │  │
│  │   • Editorial design system (tokens, components)                  │  │
│  │   • Today / Month / Chat / Lend / Settings surfaces               │  │
│  ├───────────────────────────────────────────────────────────────────┤  │
│  │ KMP Shared Core (Kotlin, common module)                           │  │
│  │   • Ledger model (SQLDelight + SQLCipher)                         │  │
│  │   • Capture engines (SMS, email, voice, OCR, manual) – common     │  │
│  │     surfaces, platform-specific implementations                   │  │
│  │   • Agent Runtime (LLM abstraction, tool calls, memory)           │  │
│  │   • Sync client (E2EE op-log ⇄ Supabase Realtime)                 │  │
│  │   • Crypto (libsodium via multiplatform-crypto)                   │  │
│  │   • Domain services (categorizer, advisor, analytics)             │  │
│  ├───────────────────────────────────────────────────────────────────┤  │
│  │ Platform bridges (expect/actual)                                  │  │
│  │   Android: AICore (Gemini Nano), MediaPipe LLM, SmsRetriever,     │  │
│  │            NotificationListenerService, WorkManager, ML Kit       │  │
│  │   iOS: FoundationModels framework, llama.cpp via Swift,           │  │
│  │        SFSpeechRecognizer, BGTaskScheduler, Vision OCR            │  │
│  │   Web: WebLLM (viewer-only mode), WebCrypto                       │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                            │
                            │ TLS + per-record E2EE
                            ▼
┌─────────────────────────────────────────────────────────────────────────┐
│   Supabase                                                              │
│   • Auth: phone OTP (BD SMS provider), Google, Apple                    │
│   • Postgres: encrypted_ops, device_keys, push_tokens, subscriptions    │
│   • Storage: E2EE attachments (receipt OCR images)                      │
│   • Realtime: per-user channel for op-log sync notifications            │
│   • Edge Functions: push trigger, billing webhook, parser CDN           │
└─────────────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────────┐
│   External services                                                     │
│   • FCM (Android push) + APNS (iOS push)                                │
│   • bKash Payment Gateway, SSLCommerz (BD payments)                     │
│   • Apple IAP, Google Play Billing (store policy)                       │
│   • Stripe (diaspora subscriptions)                                     │
│   • Sentry (PII-safe crash reporting only)                              │
└─────────────────────────────────────────────────────────────────────────┘
```

## 4. Subsystems

### S1. Ledger Core — local-first canonical store

**Responsibility:** the single source of truth for the user's financial timeline.

- **Storage:** SQLDelight (KMP-native, type-safe SQL), encrypted at rest with SQLCipher (256-bit key derived from master key)
- **Entities (overview — full schema in `S1` detailed spec):** Account, Transaction, Category, Tag, Merchant, Person, LendBorrow, Goal, RecurringRule, Insight
- **Conflict handling:** operation log (CRDT-lite) per record. Each mutation recorded as an op with `(deviceId, lamportTs, recordId, field, value)`. Last-write-wins per field on conflict, with full op history retained for audit.
- **Migrations:** SQLDelight migration files versioned in `sqldelight/migrations/`; one migration per schema bump.

### S2. Capture Engines — per-channel ingestion

Each engine produces a *Candidate Transaction* posted to a unified Inbox queue. The user reviews/approves; the agent runtime classifies confident candidates automatically.

| Engine | Platform | Mechanism |
|---|---|---|
| SMS | Android only | `NotificationListenerService` + `SmsRetrieverApi` + content provider read; regex per-bank/per-MFS templates; ambiguous candidates routed through on-device LLM categorizer |
| Email | Android, iOS | Gmail OAuth + Push notifications via Pub/Sub; on-device IMAP fetch; HTML parsed locally; on-device LLM extracts transactions |
| Voice | Android, iOS | Android `SpeechRecognizer` on-device mode; iOS `SFSpeechRecognizer` on-device mode; transcript → agent runtime → tool call |
| Receipt OCR | Android, iOS | ML Kit (Android) / Vision (iOS) text extraction; structured extraction via on-device LLM |
| Manual | All | Form UI with smart defaults: recent merchants, category prediction, lend/borrow shortcut |
| Forward / paste | iOS, Web | User pastes or shares an SMS body into Hisaab; parsed by same regex + LLM pipeline as Android SMS |

### S3. On-Device Agent Runtime

**Responsibility:** the LLM brain for parsing, conversational input, Q&A, and the advisor.

KMP module exposing a single interface:

```kotlin
interface AgentBackend {
    suspend fun complete(
        messages: List<Message>,
        tools: List<Tool>,
        constraints: GenerationConstraints,
    ): Flow<AgentEvent>  // Token | ToolCall | Done | Error
}
```

Concrete implementations (`expect/actual`):

- `GeminiNanoBackend` — Android, via AICore (preferred when available)
- `AppleFoundationModelsBackend` — iOS, via FoundationModels framework (preferred when Apple Intelligence available)
- `MediaPipeBackend` — Android fallback (Gemma 3n, ~600MB compressed)
- `LlamaCppBackend` — iOS fallback (Llama 3.2 1B, bundled or downloaded on first use)
- `WebLLMBackend` — Web (viewer-only mode; not used for parsing)
- `ClaudeCloudBackend` — opt-in cloud fallback for deep questions; called only with anonymized aggregates and explicit user consent

**Agent types:**

| Agent | Lifespan | Memory |
|---|---|---|
| SMS / email categorizer | Stateless | None — pure function |
| Q&A agent | Per-conversation | Conversation buffer only |
| Conversational input agent | Per-conversation | Conversation buffer only |
| Advisor agent | Long-running | Persistent encrypted memory in local DB |
| Planning agent | Long-running | Persistent goals + plan in local DB |

**Tool registry:** `addTransaction`, `addIncome`, `addLendBorrow`, `queryByCategory`, `queryByDateRange`, `queryByMerchant`, `setReminder`, `setGoal`, `summarizeMonth`, `summarizeYear`, `compareMonths`, `suggestSavingsAction`, `draftReminderMessage`.

**Tool execution:** all tools execute purely against local SQLDelight tables. No network calls from agent tools.

### S4. Sync + E2EE

**Responsibility:** keep user's data consistent across devices without exposing it to the backend.

- **Master key:** derived from user passphrase + per-device salt via Argon2id (m=64MB, t=3, p=2)
- **Per-record key:** derived via HKDF from master key + recordId
- **Blob encryption:** XChaCha20-Poly1305 (libsodium via [multiplatform-crypto](https://github.com/ionspin/kotlin-multiplatform-libsodium) — confirm via Context7 at implementation time)
- **Sync protocol:** clients publish encrypted op-log entries to Supabase Postgres; other devices subscribe via Supabase Realtime, decrypt, replay
- **Conflict resolution:** last-write-wins per field, op log retained for audit
- **Initial sync:** new device receives the full op-log on registration; replays to materialize local DB
- **Push triggers (server-side):** Edge Function on op insert sends content-less push: "New activity from device X"

### S5. Backend (Supabase)

**Responsibility:** auth, encrypted blob storage, billing state, push triggers, parser updates. Backend is intentionally thin and dumb.

- **Auth:**
  - Phone OTP primary (Supabase Auth + BD SMS provider: SSL Wireless or Banglalink Aggregator)
  - Google OAuth secondary (for diaspora and Gmail capture)
  - Apple OAuth secondary (for iOS App Store policy)
  - All auth produces a Supabase session; passphrase is separate (E2EE key derivation only)
- **Postgres tables:**
  - `users` — id, phone, displayName (E2EE), locale, theme, createdAt
  - `device_keys` — userId, deviceId, publicKey, registeredAt
  - `encrypted_ops` — id, userId, deviceId, lamportTs, payload (E2EE blob), createdAt
  - `push_tokens` — userId, deviceId, fcmToken, apnsToken
  - `subscriptions` — userId, tier, provider, externalId, status, currentPeriodEnd
  - `parser_patterns_versions` — version, payload, releasedAt (CDN-style updates for new bank SMS formats)
- **Storage bucket:** `attachments` (E2EE blobs for receipt OCR images)
- **Realtime:** per-user channel on `encrypted_ops` for live sync
- **Edge Functions:**
  - `push_trigger` — fires content-less push when new op inserted from another device
  - `billing_webhook` — handles bKash, SSLCommerz, Stripe, Apple, Google webhooks; updates subscription state
  - `parser_pattern_publish` — publishes new regex templates for newly-encountered banks
  - `subscription_renew` — daily cron to mark expired subscriptions

### S6. Advisor Agent

**Responsibility:** proactive financial coaching.

- Runs locally on a schedule (Android `WorkManager` "expedited" → "deferrable"; iOS `BGTaskScheduler`)
- Cadence: daily review at user-preferred time (default 9 PM); weekly summary on Saturday; monthly review on the 1st
- Inputs: last 90 days of structured transactions, persistent advisor memory
- Outputs: `Insight` records (type, payload, surfaceState) inserted into local DB; server is notified via op-log; server fires content-less push
- **Hallucination guard:** the LLM only *phrases* what deterministic SQL queries return. The advisor cannot fabricate numbers — every number in its output traces to a queried tool result.

### S7. Notification Engine

- **Local notifications:** scheduled by platform schedulers (Android `NotificationManagerCompat`, iOS `UNUserNotificationCenter`)
- **Remote push:** FCM + APNS via Supabase Edge Function; payloads carry trigger only ("New insight ready"), never sensitive content
- **Settings:** per-category opt-out (advisor, lend reminders, overspend warnings, weekly summary), quiet hours, frequency caps
- **Permission ask:** deferred — never on first launch; asked after first meaningful insight is generated

### S8. UI / Design System

- **Tokens** in KMP common module: `HisaabColors`, `HisaabTypography`, `HisaabSpacing`, `HisaabMotion`, `HisaabShapes`
- **Components** built on Compose Multiplatform Material 3 baseline, theme-overridden to Editorial Premium:
  - `EditorialScreen`, `LedgerRow`, `AmountText`, `AdvisorCard`, `VoiceInputBar`, `BarChart`, `LineChart`
- **Typography:** GT Sectra (display, license required) or Tiempos Headline (substitute) + Inter (UI) + JetBrains Mono (tabular numerals) + Noto Serif Bengali (display bn) + Hind Siliguri (UI bn)
- **Palette:**
  - Light: cream `#faf7f2`, ink `#1a1a1a`, warm-muted `#6f6453`, terracotta `#ad6b2a`, gold `#c8964a`, forest `#2e7d4f`, russet `#b5402c`
  - Dark: bg `#0f0c08`, fg `#f5ede0`, muted `#b3a288`, terracotta `#d68945`
- **Mode:** auto via system; manual override in settings
- **Motion:** calm, no bounce; full motion spec in design system module

### S9. Billing + Subscriptions

- **Apple In-App Purchase** + **Google Play Billing** mandatory for store distribution
- **bKash Payment Gateway** + **SSLCommerz** for BD users via web onboarding (bypasses store cut where store policy allows)
- **Stripe** for diaspora web subscribers
- **Subscription state** persisted on backend (encrypted user metadata), synced to client on next sync
- **Receipt validation:** server-side via provider webhooks; never client-side

### S10. Localization

- **Strings:** Compose Resources `stringResource` system
- **Locales v1:** `en`, `bn`
- **Number formatting:** respects locale; Bengali numerals (`২,৩৪০`) for `bn`, Latin (`2,340`) for `en`; manual override in settings
- **Date formatting:** per-locale; Bengali month names for `bn` locale
- **Currency:** ৳ default for v1; multi-currency for diaspora deferred to Phase 2

## 5. Data model overview

```
User ┐
     ├──< Account >──┐
     │              ├──< Transaction >── Category, Merchant, Tag*
     │              │              └── Person (when lend/borrow linked)
     ├──< Person >──< LendBorrow >── Transaction*
     ├──< Goal >── Transaction* (allocations)
     ├──< RecurringRule >── Account, Category
     └──< Insight >
```

Core entity fields (high-level; full DDL in S1 spec):

- **User** — id, phone, displayName, locale, theme, createdAt
- **Account** — id, name, kind (CASH | BANK | MFS | CARD | GOAL), institution, currency, balanceTracking (bool)
- **Transaction** — id, accountId, amount, currency, ts, merchantId, categoryId, tagIds[], source (SMS | EMAIL | VOICE | MANUAL | OCR | RECURRING), rawData (E2EE local-only), notes
- **Category** — id, name, parentId, color, icon, isDefault
- **Merchant** — id, name, normalizedName, defaultCategoryId
- **Person** — id, name, contactRef, balance (net lend/borrow)
- **LendBorrow** — id, personId, amount, direction (LENT | BORROWED), purpose, ts, dueDate, status (OPEN | SETTLED | PARTIAL), linkedTransactionIds[]
- **Goal** — id, name, targetAmount, currentAmount, deadline, accountId
- **RecurringRule** — id, kind (SALARY | RENT | SUBSCRIPTION | EMI | OTHER), amount, schedule (cron-like), accountId, categoryId
- **Insight** — id, generatedAt, type (REVIEW | WARNING | SUGGESTION | SUMMARY), payload (JSON), surfaceState (UNSEEN | SEEN | ACTED)

## 6. On-device AI strategy (tiered)

| Tier | Model | Devices | Quality |
|---|---|---|---|
| 1 — Native AI | Gemini Nano (Android), Apple Foundation Models (iOS) | Android 14+ on Pixel 8+/Galaxy S24+; iOS on A17 Pro / M-series | Best |
| 2 — Bundled fallback | Gemma 3n via MediaPipe LLM Inference (Android), Llama 3.2 1B via llama.cpp (iOS) | All other supported devices | Good |
| 3 — Cloud (opt-in) | Claude Sonnet via backend proxy | Any device, explicit user consent only | Best, but exfiltrates aggregates |

**Cloud tier rules:**

- Off by default
- User toggle in Settings → AI → Allow deep cloud answers
- Sent payload: category totals, time-series sums, anonymized merchant types (e.g., "ride-hailing" not "Uber"). Never raw SMS, never contact names, never raw transaction descriptions.
- Round-trip happens via Supabase Edge Function → Anthropic API; Edge Function strips identifying headers

## 7. Privacy / security model

- All sensitive data (SMS bodies, email bodies, transaction descriptions, contact names, advisor memory) is on-device only
- Backend stores only E2EE ciphertext; backend operators cannot decrypt user data
- Account recovery: passphrase + email + 2FA; recovery key escrowed encrypted-to-passphrase (lose passphrase = data unrecoverable, by design — communicated clearly at onboarding)
- Key derivation: Argon2id (m=64MB, t=3, p=2)
- Per-blob encryption: XChaCha20-Poly1305
- Threat model: backend compromise yields ciphertext only; lost device yields nothing if Secure Enclave / Keystore is used; phishing the passphrase is the only credible exposure path
- Crash reporter (Sentry): no PII; numeric / structural data only; opt-in
- App Store / Play Store privacy nutrition labels: "Data Not Collected" should be achievable in full

## 8. Internal phasing within v1

| Month | Milestone | Verification |
|---|---|---|
| M1 | KMP scaffold; Compose Multiplatform nav shell; SQLDelight + SQLCipher; Supabase auth (phone OTP) | App opens; user can sign up; empty Today screen |
| M2 | Ledger core entities; manual entry; lend/borrow basic; monthly stats | User can add transaction manually; see monthly total |
| M3 | Capture engines: SMS (Android) regex pipeline; Gmail OAuth + fetch + parse; on-device LLM categorizer (Tier 2) | SMS auto-creates transactions on a test device with seeded bank SMS |
| M4 | Agent runtime (KMP); platform backends (Gemini Nano, Foundation Models, fallback); voice STT; conversational input flow | User can say "spent 500 on coffee" → transaction created |
| M5 | Advisor agent (scheduled, deterministic-then-phrased); notification engine; comparative analytics | First daily review notification fires on day 30 |
| M6 | iOS Compose Multiplatform UI polish + SwiftUI bridges (haptics, share sheet, App Intents); Web viewer | iOS feature parity except SMS; Web shows synced data |
| M7 | Onboarding flow; settings; design system polish; localization (bn + en); accessibility pass | Bengali UI complete; VoiceOver works; high-contrast mode works |
| M8 | Billing integration (bKash + SSLCommerz + IAP + Play Billing); subscription state sync; closed beta in Dhaka with 200 users | First paying users |
| M9 | Performance pass: cold start, battery, storage, sync efficiency | Cold start < 2s on mid-tier; advisor jobs only on charging+idle |
| M10 | Store review prep: SMS permission justification, App Privacy labels, screenshots, listing copy | Submitted to Play + App Store |
| M11 | Public launch: marketing site, press, influencer beta, ASO | 1k paying users by month end |
| M12 | Open banking pilot (bKash B2B sandbox); investment tracking spike (Phase 2 prep) | Pilot integration runs end-to-end with one test user |

Single-developer execution: stretch +30% on each phase. Two developers: tracks roughly on plan.

## 9. Per-platform considerations

### Android

- Minimum SDK 26 (Android 8.0) for baseline; AICore requires 33 (Android 13) for Gemini Nano
- SMS permission requires Play Console permissions justification; submit with detailed privacy explanation
- `NotificationListenerService` requires user to enable in system settings (acceptable friction — explain why)
- `WorkManager` for background advisor; obey doze and app standby
- `Keystore` (StrongBox where available) for master-key protection

### iOS

- Minimum iOS 17 baseline; Apple Intelligence requires iOS 18 + A17 Pro / M-series
- No SMS access — feature-flag SMS UI off on iOS; surface email + voice + manual + forward as primary capture paths
- App Store review: position as personal recordkeeping; include disclaimers; the advisor is observational, not prescriptive
- `BGTaskScheduler` for background advisor; iOS is stricter about background time
- `Keychain` with Secure Enclave for master-key protection
- App Intents integration: "Hey Siri, add 500 to food" → triggers conversational input agent

### Web

- Compose Multiplatform Web (Kotlin/WASM) is **Beta** as of 2026 (verified via Context7 lookup at spec time). Initial bundle estimated ~2-3 MB. Treat as the highest-risk target in v1. **Fallback plan:** if WASM bundle size, performance, or interop becomes blocking, ship a Next.js/React web viewer that reads the same E2EE blobs via TypeScript decryption (libsodium-js).
- WebLLM is too heavy for production v1; Web is **viewer-only** for synced data
- Capture on Web: manual entry, paste-SMS, Gmail OAuth (server-mediated email fetch decrypted in WASM)
- Service worker for offline viewing; no on-device LLM
- WebCrypto for E2EE key handling

## 10. Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Google Play SMS permission rejected | Medium | High | Strong privacy submission; manual-paste fallback flow; pre-submit Google Play Console consultation |
| On-device LLM too slow on mid-tier Androids | Medium | Medium | Tiered model strategy; queue + retry on charging+idle; explicit progress UI |
| Compose Multiplatform on iOS rough edges | Medium | Medium | SwiftUI bridges for polish-critical screens; 20% time buffer; track CMP iOS issue tracker |
| Compose Multiplatform Web still Beta in 2026 | High | Medium | Web is viewer-only in v1 (limits exposure); fallback to Next.js/React client reading the same E2EE blobs if WASM blocks shipping |
| WebLLM too heavy for web users | High | Low | Web is viewer-only of synced data; not blocking |
| Bangladesh banks change SMS format | High | Medium | Per-pattern versioning + CDN delivery; user-report broken-parsing button |
| User loses passphrase | Medium | High | Clear warning + paper recovery sheet at onboarding; optional secret-sharing escrow Phase 2 |
| Battery drain from background advisor | Medium | Medium | Charging + idle scheduling default; user-configurable cadence |
| Apple rejects under "financial advice" rules | Low | High | Position as recordkeeping not advice; disclaimers in advisor text |
| LLM hallucination in advisor giving bad numbers | High (if not guarded) | High | Hallucination guard: LLM phrases deterministic tool output; never invents numbers |
| Font licensing (GT Sectra commercial) | Low | Low | Purchase or substitute with Tiempos / Recoleta |
| KMP code-share less than expected (50% vs 75%) | Medium | Medium | Accept it; the value is still real |
| Supabase scaling cost at 100k users | Low (v1) | Medium | Self-host on Hetzner / DigitalOcean if needed (open-source escape hatch) |

## 11. Open technical questions

1. **Op-log compaction strategy** — at what point do we compact and rewrite history? Affects sync cost and audit.
2. **When to upgrade beyond CRDT-lite** — v1 commits to CRDT-lite (op-log with last-write-wins per field, full op history retained for audit). Open: at what scale or failure mode do we adopt a full CRDT library (Automerge, Yjs)? Revisit at 10k+ active users or first multi-device merge bug.
3. **Receipt OCR for Bengali product names** — ML Kit Bengali support TBD; may need Vision (iOS) + custom model fallback.
4. **Voice STT quality for Bengali** — measure native Android `SpeechRecognizer` vs on-device Whisper-tiny; pick on real-device benchmarks.
5. **Backup / export format** — JSON, OFX, custom encrypted archive? Decide before public launch.
6. **Multi-currency for diaspora** — Phase 2 design; defer the schema decision (`amount + currency` per Transaction is already in v1, so it scales).
7. **Family-plan implementation** — separate logical books with shared lend/borrow view, or fully shared books? Affects S4 sync design.
8. **Cloud Claude fallback transport** — Edge Function pass-through vs direct from client (with anti-abuse rate limiting)?

## 12. References (to consult via Context7 at implementation time)

- Kotlin Multiplatform / Compose Multiplatform — current API surface, iOS interop best practices, WASM target stability
- Supabase Kotlin SDK — auth, postgres, realtime, storage
- SQLDelight — KMP setup, migrations, coroutine integration
- SQLCipher — KMP support, key handling
- MediaPipe LLM Inference — Android setup, Gemma 3n quantization
- Android AICore — Gemini Nano availability matrix, capabilities surface
- Apple FoundationModels framework — tool calling API, availability detection
- llama.cpp — iOS Swift wrapper, model bundling
- libsodium / multiplatform-crypto-libsodium — KMP bindings, XChaCha20-Poly1305 usage
- ML Kit — Android text recognition, Bengali script support
- iOS Vision framework — text recognition, Bengali script support
- bKash Payment Gateway — API surface, sandbox onboarding
- SSLCommerz — integration patterns
- FCM + APNS via Supabase Edge Functions

## 13. What this spec does NOT cover

Per-subsystem detailed specs are written as each phase begins:

- `docs/superpowers/specs/<date>-foundation-design.md` — KMP scaffold, schema, encryption
- `docs/superpowers/specs/<date>-ledger-core-design.md` — full data model, operations API, lend/borrow domain
- `docs/superpowers/specs/<date>-capture-engine-design.md` — SMS regex catalogue, Gmail OAuth flow, OCR pipeline, manual entry UX
- `docs/superpowers/specs/<date>-agent-runtime-design.md` — `AgentBackend` interface, tool registry, prompt patterns, hallucination guard
- `docs/superpowers/specs/<date>-advisor-design.md` — insight types, scheduling, prompt design, anti-hallucination strategy
- `docs/superpowers/specs/<date>-sync-e2ee-design.md` — op-log format, key derivation, CRDT-lite rules, recovery
- `docs/superpowers/specs/<date>-design-system-design.md` — design tokens, component catalogue, motion
- `docs/superpowers/specs/<date>-billing-design.md` — provider matrix, subscription state machine, webhook handling
- `docs/superpowers/specs/<date>-localization-design.md` — string surface, locale switching, Bengali numerals
- `docs/superpowers/specs/<date>-platform-bridges-design.md` — expect/actual per platform; Android, iOS, Web bridges

Each per-subsystem spec follows the same brainstorming → spec → plan → implement cycle.
