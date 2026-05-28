# Hisaab — M3 (Slice 1): SMS Capture Engine + LLM Parsing/Categorizer

**Status:** Approved
**Date:** 2026-05-29
**Phase:** M3 — "Quiet capture" engine, first slice (SMS only)
**Depends on:** [`2026-05-27-hisaab-master-architecture-design.md`](./2026-05-27-hisaab-master-architecture-design.md), [`2026-05-28-p0c-ledger-core-design.md`](./2026-05-28-p0c-ledger-core-design.md)
**Defers to later M3 slices:** Email capture, Voice capture, Receipt OCR, CDN-delivered parser patterns
**Relates to:** [`docs/tech-debt.md`](../../tech-debt.md) H5 (real BD SMS provider), and adds a new launch-gate risk (Play Store SMS permission)

---

## 1. Summary

This slice delivers Hisaab's headline differentiator: **automatic transaction capture from bank/MFS SMS, parsed by an LLM the user controls.** When a bKash, Nagad, Rocket, or bank SMS arrives, Hisaab captures it (Android), runs a tiered parse — a free, offline, deterministic pre-filter and template extractor, falling through to an LLM (on-device **or** the user's own cloud key for Claude/Gemini/GPT) for anything the templates don't fully resolve — and either **auto-posts** a high-confidence transaction to the ledger or routes a low-confidence one to a **Review inbox** for one-tap approval.

The user chooses the engine: **on-device by default** (zero network, fully private), or **cloud via bring-your-own API key** (opt-in, consented, redaction-on-by-default; Hisaab's own servers never see the data). The whole thing is built behind interfaces (`CaptureSource`, `LlmProvider`) so iOS (paste/share intake) and the deferred email/voice/OCR engines reuse the same pipeline and provider layer.

---

## 2. Scope

### Delivers

- **Capture sources (Android):** `READ_SMS`/`RECEIVE_SMS` primary path — a live `BroadcastReceiver` (latency) plus lock-safe **catch-up-on-unlock** via `ContentResolver` (correctness) — and a `NotificationListenerService` **fallback** for when SMS permission is denied. One-time historical **backfill** (default last 90 days).
- **Tiered parsing pipeline:** dedup → deterministic pre-filter (financial/not) → known-template extraction → LLM parse for the rest → account/merchant/category resolution → confidence scoring → route.
- **LLM provider layer:** one `LlmProvider` interface with an on-device implementation (Android: Gemini Nano via AICore → Gemma 3 1B via MediaPipe) and three cloud adapters (Claude, Gemini, OpenAI) over Ktor, each using the vendor's native structured-output mode. Bring-your-own key in `SecureStorage`; `Redactor` masks PII before any cloud call; explicit consent gate.
- **Trust model:** confidence-gated auto-post (high-confidence → ledger with "auto" chip + Undo; low-confidence → Review inbox), with a global "always review first" safety toggle.
- **Schema:** `migrations/3.sqm` — `capture_inbox`, `sender_registry`, `capture_config` tables + `ALTER TABLE txn ADD COLUMN capture_id`.
- **Repositories:** `CaptureInboxRepository`, `SenderRepository` (seeds known BD senders), `CaptureConfigRepository`.
- **UX:** auto-post snackbar + "auto" chip + provenance on TransactionDetail; Review inbox modal route; Settings → "Auto-capture" section (engine picker, key entry, redaction, always-review, sender management, consent, backfill); a soft onboarding opt-in card.
- **Tests:** unit coverage (80%+) over the pure pipeline units against a fixed anonymized BD SMS corpus + Ktor `MockEngine` cloud-adapter tests + fake-provider pipeline tests; instrumented tests for the SMS receiver/backfill and on-device parse; Compose UI tests for the inbox + Settings.

### Explicitly does NOT deliver (deferred to later M3 slices)

- Email capture (OAuth, HTML parse), Voice capture (STT), Receipt OCR.
- iOS paste/share **implementation** (the `PASTE` channel is designed-for and stubbed; actual intake UI is a later slice).
- Cloud **managed/freemium** tier (the provider abstraction supports it; only BYO key ships now).
- CDN-delivered parser-pattern updates (`parser_patterns_versions`); templates ship in-app for v1.
- Own-account transfer auto-detection; recurring/salary auto-rules from SMS.
- wasmJs capture (viewer target — no capabilities).

---

## 3. Design decisions (locked during brainstorm)

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | **Full LLM parsing**, user picks **on-device or cloud (Claude/Gemini/GPT)** | User intent; maximum capability and resilience to new SMS formats |
| D2 | **BYO API key now, managed tier later**; on-device default; cloud opt-in + consent + redaction | Preserves "Hisaab never sees your data" even for cloud; zero infra cost; future-proofed via the provider abstraction |
| D3 | **READ_SMS primary + NotificationListener fallback** | Reliability of full SMS access, with a path that still works if Play rejects SMS permission |
| D4 | **Confidence-gated auto-post** + Review inbox; global "always review" toggle | Delivers the "quiet capture" magic while protecting trust in a finance app |
| D5 | **Tiered pipeline: deterministic gate → LLM** | Private-by-default, offline-capable, cost-sane, necessary spam gate; LLM stays the parser of record for non-trivial messages |

---

## 4. Architecture

### Module layout (new packages under `app.hisaab.*`)

```text
app/hisaab/
├── capture/                      # the pipeline (commonMain, pure + testable)
│   ├── RawCapture.kt             # {sender, body, receivedAt, channel}
│   ├── CaptureChannel.kt         # SMS | NOTIFICATION | PASTE
│   ├── CaptureCoordinator.kt     # collects sources + on-unlock catch-up → pipeline (alive while DB open)
│   ├── CapturePipeline.kt        # orchestrator: dedup→prefilter→extract→resolve→score→route
│   ├── SmsPreFilter.kt           # deterministic "is this financial?" gate
│   ├── SenderRegistry.kt         # in-memory view over sender_registry (seed + user)
│   ├── BankTemplate.kt           # per-bank regex templates → structured fields
│   ├── BanglaNumerals.kt         # ০–৯ → 0–9 normalization
│   ├── AccountMatcher.kt         # sender → account (auto-create MFS/BANK on first sight)
│   ├── ConfidenceScorer.kt       # 0..1 from template strength + completeness + LLM self-report
│   └── CandidateTransaction.kt   # parsed result + status + confidence + provenance
├── llm/                          # provider layer
│   ├── LlmProvider.kt            # interface
│   ├── LlmRouter.kt              # selects active provider from CaptureConfig
│   ├── ParseRequest.kt / LlmParseResult.kt
│   ├── Redactor.kt               # pure PII masking before cloud
│   ├── Prompts.kt                # extraction + categorization prompts (+ on-device short variant)
│   ├── ModelManager.kt           # on-device model availability/download (androidMain-backed)
│   └── cloud/{ClaudeProvider,GeminiProvider,OpenAiProvider}.kt   # Ktor adapters
├── platform/
│   └── CaptureService.kt         # expect; Android=SMS+Notification, iOS=paste, wasm=none
└── data/
    ├── CaptureInboxRepository.kt
    ├── SenderRepository.kt
    └── CaptureConfigRepository.kt
```

Plus `androidMain`: `SmsBroadcastReceiver`, `HisaabNotificationListenerService`, `AndroidOnDeviceProvider`, `createOnDeviceProvider()` actual, `CaptureService` actual. `iosMain`/`wasmJsMain`: `CaptureService` stubs, `createOnDeviceProvider()` returning null (iOS Foundation Models impl is a later slice).

### Key abstractions (isolation contracts)

- **`CaptureService`** (expect) — emits a merged `Flow<RawCapture>` and offers backfill + permission ops. The pipeline never knows which channel produced a capture.
- **`LlmProvider`** (interface) — `isAvailable()`, `parse(ParseRequest): LlmParseResult`, `categorize(merchant, categories): String?`. On-device and each cloud vendor are interchangeable; cloud calls always pass through `Redactor` first.
- **`CapturePipeline`** — depends only on interfaces + repos, so its routing logic is fully unit-testable with fakes.

### Lock-state constraint (drives the capture design)

The DB is SQLCipher-encrypted and `master_secret` is **zeroed when the app backgrounds** (30s lock). **No parse can write to the ledger while locked.** Therefore:

- **Catch-up-on-unlock is the backbone:** on every DB open (unlock/foreground), `syncSinceLastCursor()` queries SMS newer than a stored cursor and feeds the pipeline. Cannot miss messages; battery-friendly.
- **The live `BroadcastReceiver` is a latency bonus** while unlocked; it never writes while locked.
- `NotificationListenerService` likewise only feeds the pipeline while unlocked; it is the fallback path.

---

## 5. Data model (`migrations/3.sqm`)

The capture tables live in the **same SQLCipher-encrypted DB** and are **excluded from the sync set** (only the ops-log syncs). Raw SMS is therefore encrypted-at-rest and local-only by construction — no separate `BlobCrypto` pass (that remains for file attachments).

```sql
-- 1. Candidate queue: every parsed candidate, posted or pending
CREATE TABLE capture_inbox (
  id              TEXT NOT NULL PRIMARY KEY,
  received_at     INTEGER NOT NULL,            -- SMS timestamp
  channel         TEXT NOT NULL,               -- SMS | NOTIFICATION | PASTE
  sender          TEXT NOT NULL,
  raw_body        TEXT NOT NULL,               -- encrypted at rest, never synced, purgeable
  dedup_hash      TEXT NOT NULL,               -- sha256(normSender|normBody)
  status          TEXT NOT NULL,               -- PENDING | AUTO_POSTED | CONFIRMED | DISMISSED
  confidence      REAL,                        -- 0..1
  parsed_by       TEXT,                        -- TEMPLATE | ON_DEVICE | CLOUD_CLAUDE | CLOUD_GEMINI | CLOUD_OPENAI
  model           TEXT,                        -- e.g. "gemini-nano"; null for template
  parse_error     TEXT,                        -- non-null → routed to PENDING for manual fix
  amount          REAL,
  direction       TEXT,                        -- DEBIT | CREDIT (→ TxnKind)
  currency        TEXT NOT NULL DEFAULT 'BDT',
  balance_after   REAL,
  ref_no          TEXT,                        -- trxID; secondary dedup signal
  proposed_account_id   TEXT,
  proposed_category_id  TEXT,
  proposed_merchant     TEXT,
  created_at      INTEGER NOT NULL
);
CREATE UNIQUE INDEX idx_capture_dedup ON capture_inbox(dedup_hash);
CREATE INDEX idx_capture_status ON capture_inbox(status, received_at);

-- 2. Known senders → account mapping + pre-filter allowlist (seeded, user-editable)
CREATE TABLE sender_registry (
  id            TEXT NOT NULL PRIMARY KEY,
  sender_id     TEXT NOT NULL,                 -- "bKash", "NAGAD", "BRAC BANK", "01..."
  display_name  TEXT NOT NULL,
  bank_type     TEXT NOT NULL,                 -- BKASH | NAGAD | ROCKET | BANK | CARD | OTHER
  is_financial  INTEGER NOT NULL DEFAULT 1,    -- pre-filter gate; promo senders → 0
  template_key  TEXT,                          -- which BankTemplate set applies
  account_id    TEXT,                          -- mapped account (null until mapped/auto-created)
  created_at    INTEGER NOT NULL
);
CREATE UNIQUE INDEX idx_sender_id ON sender_registry(sender_id);

-- 3. Single-row capture/engine config (API KEY itself stays in SecureStorage)
CREATE TABLE capture_config (
  id                  TEXT NOT NULL PRIMARY KEY DEFAULT 'singleton',
  capture_enabled     INTEGER NOT NULL DEFAULT 0,
  engine_mode         TEXT NOT NULL DEFAULT 'ON_DEVICE',   -- ON_DEVICE | CLOUD
  on_device_model     TEXT NOT NULL DEFAULT 'auto',        -- auto | gemini-nano | gemma-3-1b
  cloud_provider      TEXT,                                -- CLAUDE | GEMINI | OPENAI
  cloud_model         TEXT,
  redaction_enabled   INTEGER NOT NULL DEFAULT 1,
  always_review       INTEGER NOT NULL DEFAULT 0,
  auto_post_threshold REAL NOT NULL DEFAULT 0.85,
  cloud_consent_at    INTEGER,                             -- null = never consented
  retain_raw_body     INTEGER NOT NULL DEFAULT 1,
  last_sms_cursor     INTEGER NOT NULL DEFAULT 0,          -- max processed received_at
  updated_at          INTEGER NOT NULL
);

-- 4. Provenance link (one FK, one direction)
ALTER TABLE txn ADD COLUMN capture_id TEXT;   -- null = manual/non-captured
```

**Provenance:** the only link is `txn.capture_id → capture_inbox.id`, set atomically when a candidate posts. The inbox finds its txn via `WHERE capture_id = ?`. The ledger shows the "auto" badge from `capture_id IS NOT NULL` — so `observeRecent/ForDay/ForMonth` each select one extra column and `TransactionRow` gains `captureId: String?`; confidence/provider/raw are loaded from `capture_inbox` only when detail/inbox views need them (no heavy joins in hot list queries).

**New domain models** (`domain/`): `RawCapture` + `CaptureChannel`; `CandidateTransaction` + `CaptureStatus` + `ParsedBy`; `SenderMapping` + `BankType`; `CaptureConfig` + `EngineMode` + `CloudProvider`; `LlmParseResult`.

**Dedup/idempotency:** every insert computes `dedup_hash` and is a no-op if it exists (receiver double-fires, backfill/real-time overlap). `ref_no` is a secondary check.

**Raw retention:** default keep raw body (enables re-parse/re-categorize); `retain_raw_body=0` purges on CONFIRMED/DISMISSED for privacy-minimizing users.

---

## 6. Capture sources

```kotlin
// commonMain/platform/CaptureService.kt
expect class CaptureService {
    fun capabilities(): Set<CaptureChannel>          // Android {SMS,NOTIFICATION}; iOS {PASTE}; wasm {}
    suspend fun hasSmsPermission(): Boolean
    suspend fun requestSmsPermission(): Boolean       // RequestMultiplePermissions
    fun observeIncoming(): Flow<RawCapture>           // merged live stream
    suspend fun backfillSince(cursorMs: Long): List<RawCapture>
    fun openNotificationAccessSettings()
}
```

- **Android actual:** `SmsBroadcastReceiver` (PDU → `RawCapture`, channel=SMS) + `ContentResolver` query over `Telephony.Sms.Inbox` (backfill/catch-up) + `HisaabNotificationListenerService` (fallback; flushes while unlocked). Manifest gains `RECEIVE_SMS`, `READ_SMS`, and the notification-listener service with `BIND_NOTIFICATION_LISTENER_SERVICE`. The SMS receiver accepts only the system broadcast (`android:permission="android.permission.BROADCAST_SMS"`), not app-exported.
- **iOS actual:** `capabilities() = {PASTE}`; `observeIncoming()` drains a paste/share intake (UI built later; empty for now). SMS methods no-op.
- **wasmJs actual:** empty capabilities.
- **Cursor:** `capture_config.last_sms_cursor` (max processed `received_at`); advanced by both catch-up and the live receiver.
- **Permission flow:** enabling capture requests RECEIVE_SMS+READ_SMS → on grant sets `capture_enabled=1` and runs the initial 90-day backfill; on denial offers the Notification-access fallback.

---

## 7. Parsing pipeline

`CaptureCoordinator` (in `AppContainer`, alive only while DB open) collects `observeIncoming()` + the on-unlock catch-up and pushes each `RawCapture` through `CapturePipeline.process()`, serialized via a `Mutex`, each call in one DB transaction on `Dispatchers.Default`:

1. **Dedup** — `dedup_hash`; no-op if present (`ref_no` secondary).
2. **Pre-filter** (`SmsPreFilter`) → `NotFinancial` (drop), `KnownTemplate(mapping, templateKey)`, or `UnknownFinancial(mapping?)`. Non-financial = known promo sender (`is_financial=0`) or no money signal (`Tk`/`৳`/`BDT`/amount/`TrxID`/keywords). **Non-financial messages are dropped before any LLM/cloud call.**
3. **Extraction:**
   - *KnownTemplate* → `BankTemplate.extract(body)` (after `BanglaNumerals.normalize`) for amount/direction/merchant/refNo/balance. Full match → high base confidence; **partial → fall through to LLM** for the gaps.
   - *UnknownFinancial / partial* → `LlmRouter.active().parse(...)`. Cloud path runs `Redactor` first and requires recorded consent.
   - *No LLM configured AND template incomplete* → row saved `PENDING` with `parse_error="needs_manual"` (never silently dropped).
4. **Account mapping** (`AccountMatcher`) — `sender_registry.account_id`, else **auto-create** (`MFS` for bKash/Nagad/Rocket, `BANK` for banks) and persist the mapping; ambiguous → null → forces review.
5. **Merchant + category** — `normalizeMerchantName` + `MerchantRepository.upsert` **at post-time only**. Category resolution: the LLM returns a `categoryId` constrained to the 12 seeded ids (validated against the set; invalid → null). On the template-only fast path (no LLM call) a rule-based merchant→category map fills it, else null → review.
6. **Confidence** (`ConfidenceScorer`) — blends template strength (full ≈ 0.9+), field completeness, and LLM self-report (LLM-only unknowns capped ≤ 0.7). Output 0..1.
7. **Direction → kind** — DEBIT→`EXPENSE`, CREDIT→`INCOME` (salary hint → INCOME). Own-account transfer detection deferred.
8. **Route** — `always_review=false` AND `confidence ≥ auto_post_threshold` AND account+amount+direction resolved → **auto-post** (upsert merchant → `TransactionRepository.add(NewTransaction(source=SMS,…))` → set `txn.capture_id` → `status=AUTO_POSTED`, one transaction) + quiet "Undo" snackbar. Else → `status=PENDING` → Review inbox, badge++.

**`BankTemplate`s ship in-app** (commonMain) for v1 — seeded for bKash, Nagad, Rocket + major banks (City, BRAC, DBBL). CDN pattern delivery deferred.

`SmsPreFilter`, `BankTemplate`, `BanglaNumerals`, `ConfidenceScorer`, `AccountMatcher` are **pure, corpus-testable** units with no platform/DB deps.

---

## 8. LLM provider layer

```kotlin
interface LlmProvider {
    val id: ProviderId                       // ON_DEVICE | CLOUD_CLAUDE | CLOUD_GEMINI | CLOUD_OPENAI
    suspend fun isAvailable(): Boolean
    suspend fun parse(req: ParseRequest): LlmParseResult
    suspend fun categorize(merchant: String, categories: List<Category>): String?
}

data class ParseRequest(val text: String, val senderHint: String?, val categories: List<Category>)
// LlmParseResult: amount?, direction?, merchant?, categoryId?, balanceAfter?, refNo?, confidence, isFinancial
```

- **On-device (androidMain)** via `expect fun createOnDeviceProvider(): LlmProvider?` (null on iOS/wasm now). `ModelManager` resolves `on_device_model=auto` through the tier ladder:
  1. **Gemini Nano via AICore / ML Kit GenAI** — best/free/private; flagship-only (Pixel 8+/9, Galaxy S24/25), scarce in BD.
  2. **Gemma 3 1B (4-bit) via MediaPipe LLM Inference** — ~529 MB optional download (WorkManager, Wi-Fi-prompted, app files, progress UI), ~40–50 tok/s, Android 24+ with adequate RAM.
  3. neither → fall through to cloud (if configured) or template-only.
- **Cloud (commonMain via Ktor**, reusing the existing `HttpClient`): `ClaudeProvider`, `GeminiProvider`, `OpenAiProvider`. **One prompt, three transport adapters**, each using native structured output → decode into the **same `LlmParseResult`** via one `kotlinx.serialization` schema:
  - Claude → `tool_use` with `input_schema`
  - Gemini → `generationConfig.responseSchema` + `responseMimeType: application/json`
  - OpenAI → `response_format: { type: json_schema }`
  - Exact model IDs / SDK versions pinned during writing-plans (Context7 / vendor docs).
- **Key handling** — `SecureStorage` key `llm_api_key_<provider>`; never in DB, never logged, masked in UI; set/validated/cleared from Settings; cloud `isAvailable()` = key present.
- **`Redactor` (pure)** — before any cloud call when `redaction_enabled` (default on): masks account numbers (keep last 4), phone numbers, detectable names → placeholders, preserving amount/merchant/direction. Honest limitation: merchant + amount still leave the device.
- **Consent gate** — before the first cloud call and on provider change, record `cloud_consent_at`: *"Your bank SMS text will be sent to {provider}…; Hisaab's servers never see it; redaction: on."* No cloud call without it.
- **`Prompts.kt`** — one extraction system prompt (JSON per schema; `isFinancial=false` to reject; map to exactly one of the 12; `null` when unsure; self-report confidence) + BD few-shot examples + a shorter on-device variant; a separate merchant→category prompt.
- **Errors** — 15s timeout; network/401(→"key invalid")/429(→backoff, PENDING)/provider error → candidate `PENDING` with `parse_error`. Degrades to template-only or review; never drops a financial SMS, never crashes.
- **DI** — `AppContainer` exposes `LlmRouter` from `CaptureConfigRepository` + `SecureStorage` + `createOnDeviceProvider()` + cloud providers.

---

## 9. UX

- **Auto-post surfacing** — transient snackbar *"+৳500 · bKash · auto-added · Undo"* (Undo deletes txn, sets candidate `DISMISSED`); "auto" chip on Today rows; provenance block on TransactionDetail (engine/model, confidence, expandable raw SMS).
- **Review inbox** — badge on Today (*"3 to review"*) opens a modal route (MainGraph modal pattern). Candidate card: large editorial amount + direction color, proposed merchant, category chip, account, sender + expandable raw SMS, confidence indicator, `parse_error` if any. Actions: **Confirm** (posts), **Edit** (opens `EntryScreen` prefilled), **Dismiss** (swipe); "Confirm all high-confidence" bulk action.
- **Settings → "Auto-capture"** — master toggle (fires permission request; status + notification-access link on denial); engine picker (On-device: tier/model + download state; Cloud: provider radio + secure key field + Validate + model); redaction toggle (+ honest note); "always review first" toggle + advanced confidence slider (0.85); sender management (mapped account, enable/disable, add; "new sender detected" prompts); cloud consent state + Revoke; backfill ("Import last 90 days" + range).
- **Onboarding** — soft, skippable opt-in card: *"Hisaab can read your bKash/bank SMS to log transactions automatically — on-device by default, fully private. [Turn on] [Maybe later]"*.
- **iOS** — section explains SMS capture is unavailable; offers a "Paste a bank SMS" affordance (PASTE channel, wired later).

All Editorial Premium (cream `#faf7f2`, terracotta `#ad6b2a`, `LocalHisaabPalette`).

---

## 10. Privacy, security & error handling

**Privacy invariants:**
- Raw SMS lives only in the SQLCipher-encrypted DB, excluded from sync, purgeable; never reaches Hisaab's backend.
- On-device engine = zero network. Cloud engine = device → provider directly (BYO key); backend never sees it; redaction default-on; consent recorded, re-asked on provider change.
- Pre-filter drops non-financial SMS before any LLM/cloud call.
- API keys in `SecureStorage` only — never DB, never logs, masked in UI.

**Security:**
- `READ_SMS`/`RECEIVE_SMS` dangerous + Play-restricted → runtime request, master switch off by default. SMS receiver accepts only the system broadcast; not app-exported.
- `NotificationListenerService` requires user-granted notification access; bound service guarded.
- SMS body is untrusted input — bounded regex; **LLM output validated against schema + sanity checks** (amount > 0, known currency/direction, length caps) before posting; only structured fields consumed, so prompt-injection in an SMS can produce at worst a bad candidate (caught by sanity checks or review).
- Review/lint checklist: never log SMS bodies or API keys.

**Error handling (degrade, never crash, never silently drop):** permission denied → fallback + clear messaging; model not downloaded → prompt; offline + cloud-selected → `PENDING`, retry on connectivity; parse failure → `PENDING` with `parse_error`; duplicate → skipped; ambiguous account → review.

**Play Store risk (surfaced, not solved here):** `READ_SMS`/`RECEIVE_SMS` need a Permissions Declaration and face high scrutiny / possible rejection (in risk register). Mitigation baked in: the NotificationListener fallback keeps the app functional if SMS access is rejected. No blocker for internal dev / sideload now; tracked as a launch-gate risk.

---

## 11. Testing strategy

- **Unit (commonTest/JVM, 80%+):** `SmsPreFilter`, `BankTemplate.extract`, `BanglaNumerals`, `ConfidenceScorer`, `AccountMatcher`, `Redactor`, dedup hashing — against a **fixed corpus of anonymized real BD SMS** (the crown-jewel asset; assert exact field extraction). `CapturePipeline` with a fake `CaptureSource` + fake `LlmProvider` (auto-post vs review routing, no-LLM fallback, duplicate skip, `parse_error→PENDING`). Cloud adapters via Ktor `MockEngine` (request shape + response→`LlmParseResult` for all three vendors). Repos via in-memory `JdbcSqliteDriver`.
- **Instrumented (androidInstrumentedTest):** SMS PDU parsing, `ContentResolver` backfill, MediaPipe on-device parse (skipped if model absent), permission flow.
- **UI (Compose tests):** Review inbox confirm/edit/dismiss; Settings engine picker + key entry; emulator screenshot of the inbox.

---

## 12. Implementation decomposition (sub-plans)

Implementation will split into sub-plans, each independently shippable (mirroring P0c):

1. **M3-1 — Schema + repos + domain:** `migrations/3.sqm`, the three repos, domain models, `CaptureConfig` plumbing, sender seed. (No UI.)
2. **M3-2 — Capture sources:** `CaptureService` expect/actuals, SMS receiver + backfill + cursor, NotificationListener fallback, permissions, `CaptureCoordinator`.
3. **M3-3 — Pipeline + templates:** `SmsPreFilter`, `BankTemplate` (bKash/Nagad/Rocket + 3 banks), `BanglaNumerals`, `AccountMatcher`, `ConfidenceScorer`, `CapturePipeline`, the SMS corpus + tests.
4. **M3-4 — LLM providers:** `LlmProvider`/`LlmRouter`, `Redactor`, `Prompts`, on-device (AICore + MediaPipe) + 3 cloud adapters, `ModelManager`, MockEngine tests.
5. **M3-5 — UX:** auto-post snackbar + "auto" chip + provenance, Review inbox, Settings "Auto-capture" section, onboarding opt-in card, consent screen.

---

## 13. Open items to resolve during planning

- Exact cloud model IDs + SDK/runtime versions (Context7 at plan time).
- The anonymized BD SMS corpus — collect real bKash/Nagad/Rocket/bank samples to drive templates + tests.
- MediaPipe model packaging: bundled vs on-demand download default (leaning on-demand to keep APK < 50 MB).
- Confidence threshold calibration once the corpus exists (default 0.85 is a starting point).
