---
# M3-3: Tiered Parsing Pipeline + Bank Templates + LLM Contract Implementation Plan
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Hisaab's deterministic-first SMS parsing pipeline — pre-filter, Bangla-numeral normalization, per-bank regex templates (bKash/Nagad/Rocket/City/BRAC/DBBL), account auto-creation, confidence scoring, and a `CapturePipeline` that dedups, extracts (template → LLM), resolves account/merchant/category, scores, and atomically auto-posts (creating an already-linked transaction and marking the candidate `AUTO_POSTED` inside one DB transaction) or routes to PENDING — plus the `LlmProvider`/`LlmRouter` contract (with a `NoOpLlmRouter`) the pipeline compiles against, and a `CaptureEvent` SharedFlow that M3-5 collects to surface the auto-post snackbar.

**Architecture:** Pure, DB-free units (`SmsPreFilter`, `BanglaNumerals`, `BankTemplate`/`BankTemplates`, `ConfidenceScorer`) are corpus-tested directly with `kotlin.test`. The LLM layer ships interfaces only (`LlmProvider`, `LlmRouter`, `ParseRequest`, `LlmParseResult`, `ProviderId`) plus a `NoOpLlmRouter` returning `null`, so M3-4 can drop in real providers without touching the pipeline. `CapturePipeline` depends on M3-1 repos + these interfaces + the `HisaabDatabase` handle (for one-transaction atomicity per spec §7 step 8) + a `MutableSharedFlow<CaptureEvent>` it emits `AutoPosted` into, and is exercised with hand-written fakes and an in-memory `TestDatabase`.

**Tech Stack:** Kotlin Multiplatform (commonMain/commonTest), SQLDelight (in-memory `JdbcSqliteDriver` for tests via `TestDatabase`), kotlinx.coroutines + `kotlinx-coroutines-test` (`MutableSharedFlow`/`SharedFlow`), kotlinx.datetime `Clock`, `kotlin.random.Random` for IDs. No new third-party dependencies.

**Depends on:** M3-1 (migrations/3.sqm + `capture_inbox`/`sender_registry`/`capture_config` tables, the `ALTER TABLE txn ADD COLUMN capture_id`, the `TransactionQueries.sq` INSERT writing `capture_id`, `CaptureInboxRepository`, `SenderRepository`, `CaptureConfigRepository`, and all `domain/` types: `RawCapture`, `CaptureChannel`, `CaptureStatus`, `ParsedBy`, `Direction`, `BankType`, `EngineMode`, `CloudProvider`, `CandidateTransaction`, `SenderMapping`, `CaptureConfig`, plus `NewTransaction` with its trailing `captureId: String? = null` parameter — the P0c domain type in `domain/Transaction.kt`) and M3-2 (`CaptureService` expect, `CaptureCoordinator` with a `CaptureHandler` seam). M3-1 owns linking end-to-end: `NewTransaction.captureId` and `TransactionRepository.add` writing `capture_id` are M3-1's responsibility; this slice never edits `TransactionQueries.sq` and adds no `linkCapture`/`setCaptureId` method. M3-2 ships `CaptureCoordinator` with a `CaptureHandler` seam (a `suspend (RawCapture) -> Unit`); this slice provides the real `CapturePipeline` and wires `CaptureHandler { capturePipeline.process(it) }` in `AppContainer`. Assume all those contract types already exist and compile.

---

## File Structure

### Created — `llm/` (interface contracts; impls land in M3-4)
| File | Responsibility |
|------|----------------|
| `composeApp/src/commonMain/kotlin/app/hisaab/llm/ProviderId.kt` | `enum ProviderId { ON_DEVICE, CLOUD_CLAUDE, CLOUD_GEMINI, CLOUD_OPENAI }` |
| `composeApp/src/commonMain/kotlin/app/hisaab/llm/ParseRequest.kt` | `data class ParseRequest(text, senderHint, categories)` |
| `composeApp/src/commonMain/kotlin/app/hisaab/llm/LlmParseResult.kt` | `data class LlmParseResult(amount, direction, merchant, categoryId, balanceAfter, refNo, confidence, isFinancial)` |
| `composeApp/src/commonMain/kotlin/app/hisaab/llm/LlmProvider.kt` | `interface LlmProvider` (id/isAvailable/parse/categorize) |
| `composeApp/src/commonMain/kotlin/app/hisaab/llm/LlmRouter.kt` | `interface LlmRouter { suspend fun active(): LlmProvider? }` + `class NoOpLlmRouter : LlmRouter` (returns null) |

### Created — `capture/` (the pipeline; pure units + orchestrator)
| File | Responsibility |
|------|----------------|
| `composeApp/src/commonMain/kotlin/app/hisaab/capture/BanglaNumerals.kt` | `object BanglaNumerals { fun normalize(s): String }` — ০–৯ → 0–9 |
| `composeApp/src/commonMain/kotlin/app/hisaab/capture/PreFilterResult.kt` | `sealed interface PreFilterResult { NotFinancial / KnownTemplate / UnknownFinancial }` |
| `composeApp/src/commonMain/kotlin/app/hisaab/capture/SmsPreFilter.kt` | `class SmsPreFilter(senderRepo)` → classify a `RawCapture` |
| `composeApp/src/commonMain/kotlin/app/hisaab/capture/TemplateExtraction.kt` | `data class TemplateExtraction(amount, direction, merchant, refNo, balanceAfter, complete)` |
| `composeApp/src/commonMain/kotlin/app/hisaab/capture/BankTemplate.kt` | `class BankTemplate` (regex set) + `object BankTemplates` (seeded keys for 6 institutions) |
| `composeApp/src/commonMain/kotlin/app/hisaab/capture/AccountMatcher.kt` | `class AccountMatcher(accountRepo, senderRepo)` → resolve / auto-create + persist mapping |
| `composeApp/src/commonMain/kotlin/app/hisaab/capture/ConfidenceScorer.kt` | `object ConfidenceScorer { fun score(...) }` → 0..1 |
| `composeApp/src/commonMain/kotlin/app/hisaab/capture/CaptureEvent.kt` | `sealed interface CaptureEvent { data class AutoPosted(...) }` — emitted after a successful auto-post |
| `composeApp/src/commonMain/kotlin/app/hisaab/capture/Sha256.kt` | pure-Kotlin SHA-256 for the dedup hash |
| `composeApp/src/commonMain/kotlin/app/hisaab/capture/CapturePipeline.kt` | `class CapturePipeline.process(raw)` — dedup→prefilter→extract→resolve→score→route, auto-post in one `db.transaction { }` |

### Created — tests (`commonTest`)
| File | Responsibility |
|------|----------------|
| `composeApp/src/commonTest/kotlin/app/hisaab/capture/SmsCorpus.kt` | ~18 anonymized representative BD SMS strings + expected fields (test fixture object) |
| `composeApp/src/commonTest/kotlin/app/hisaab/capture/BanglaNumeralsTest.kt` | numeral normalization tests |
| `composeApp/src/commonTest/kotlin/app/hisaab/capture/BankTemplateTest.kt` | per-bank `extract()` corpus tests |
| `composeApp/src/commonTest/kotlin/app/hisaab/capture/SmsPreFilterTest.kt` | classify tests against fakes |
| `composeApp/src/commonTest/kotlin/app/hisaab/capture/ConfidenceScorerTest.kt` | scoring tests |
| `composeApp/src/commonTest/kotlin/app/hisaab/capture/AccountMatcherTest.kt` | auto-create + mapping persistence tests (in-memory DB) |
| `composeApp/src/commonTest/kotlin/app/hisaab/capture/CapturePipelineTest.kt` | full-pipeline routing tests (fakes + in-memory repos); asserts AutoPosted event, capture_id link, atomic auto-post |
| `composeApp/src/commonTest/kotlin/app/hisaab/capture/Sha256Test.kt` | SHA-256 known-vector tests |
| `composeApp/src/commonTest/kotlin/app/hisaab/capture/support/FakeLlm.kt` | `FakeLlmRouter` + `FakeLlmProvider` for pipeline tests |

### Modified
| File | Change |
|------|--------|
| `composeApp/src/commonMain/kotlin/app/hisaab/AppContainer.kt` | expose `llmRouter: LlmRouter`, `capturePipeline: CapturePipeline`, and `captureEvents: SharedFlow<CaptureEvent>` getters |
| `composeApp/src/androidMain/kotlin/app/hisaab/AppContainerAndroid.kt` | actual getters (NoOpLlmRouter + `MutableSharedFlow` backing `captureEvents`) + wire `CaptureHandler { capturePipeline.process(it) }` into `CaptureCoordinator` |
| `composeApp/src/iosMain/kotlin/app/hisaab/AppContainerIos.kt` | actual getters (NoOpLlmRouter + `MutableSharedFlow` backing `captureEvents`) |
| `composeApp/src/wasmJsMain/kotlin/app/hisaab/AppContainerWasm.kt` | actual getters (NoOpLlmRouter + `MutableSharedFlow` backing `captureEvents`) |

> **Note on generated query accessors:** SQLDelight generates one `Queries` property per `.sq` file, accessed as `db.<fileBaseNameLowercased>Queries`. M3-1 ships `CaptureInboxQueries.sq`, `SenderQueries.sq`, `CaptureConfigQueries.sq`, and owns `TransactionQueries.sq` (including the INSERT that writes `capture_id` from `NewTransaction.captureId`), so the repos already encapsulate all SQL — this slice never touches `.sq` files. The `db.transaction { }` used by `CapturePipeline` for atomic auto-post is the SQLDelight `Transacter.transaction` available on `HisaabDatabase`; it follows the same multi-write atomicity pattern `TransactionRepository` already uses for split parent+children writes.

---

### Task 1: LLM contract — enums + value types

**Files:**
- Create `composeApp/src/commonMain/kotlin/app/hisaab/llm/ProviderId.kt`
- Create `composeApp/src/commonMain/kotlin/app/hisaab/llm/ParseRequest.kt`
- Create `composeApp/src/commonMain/kotlin/app/hisaab/llm/LlmParseResult.kt`

- [ ] **Step 1.1** — Create `ProviderId.kt`:

```kotlin
package app.hisaab.llm

/**
 * Identifies which LLM engine produced a parse. Mirrors the cloud/on-device
 * options surfaced in CaptureConfig. Used by LlmProvider implementations
 * (M3-4) and recorded on CandidateTransaction.parsedBy.
 */
enum class ProviderId {
    ON_DEVICE,
    CLOUD_CLAUDE,
    CLOUD_GEMINI,
    CLOUD_OPENAI,
}
```

- [ ] **Step 1.2** — Create `ParseRequest.kt`. `Category` is the existing `app.hisaab.domain.Category`:

```kotlin
package app.hisaab.llm

import app.hisaab.domain.Category

/**
 * Input to an LlmProvider.parse() call. [text] is the (already redacted on the
 * cloud path) SMS body. [senderHint] is the raw sender id ("bKash", "01..."),
 * used as a parsing hint only. [categories] is the closed set of valid category
 * ids the model must map into (or return null).
 */
data class ParseRequest(
    val text: String,
    val senderHint: String?,
    val categories: List<Category>,
)
```

- [ ] **Step 1.3** — Create `LlmParseResult.kt`. `Direction` is the existing M3-1 `app.hisaab.domain.Direction`:

```kotlin
package app.hisaab.llm

import app.hisaab.domain.Direction

/**
 * Structured output from any LlmProvider.parse(), decoded from the vendor's
 * native structured-output mode (M3-4). [isFinancial] lets the model reject a
 * non-transaction message; [confidence] is the model's 0..1 self-report.
 * All transaction fields are nullable so a partial parse degrades to review.
 */
data class LlmParseResult(
    val amount: Double?,
    val direction: Direction?,
    val merchant: String?,
    val categoryId: String?,
    val balanceAfter: Double?,
    val refNo: String?,
    val confidence: Double,
    val isFinancial: Boolean,
)
```

- [ ] **Step 1.4** — Verify it compiles (no test yet; these are plain data holders):

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```
Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 1.5** — Commit:

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/llm/
git commit -m "feat: add LLM parse contract types (ProviderId, ParseRequest, LlmParseResult)"
```

---

### Task 2: LLM contract — provider + router interfaces + NoOpLlmRouter

**Files:**
- Create `composeApp/src/commonMain/kotlin/app/hisaab/llm/LlmProvider.kt`
- Create `composeApp/src/commonMain/kotlin/app/hisaab/llm/LlmRouter.kt`
- Create test `composeApp/src/commonTest/kotlin/app/hisaab/llm/NoOpLlmRouterTest.kt`

- [ ] **Step 2.1** — Write the failing test first. Create `composeApp/src/commonTest/kotlin/app/hisaab/llm/NoOpLlmRouterTest.kt`:

```kotlin
package app.hisaab.llm

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

class NoOpLlmRouterTest {

    @Test
    fun `active returns null so pipeline runs template-only`() = runTest {
        val router: LlmRouter = NoOpLlmRouter()
        assertNull(router.active())
    }
}
```

- [ ] **Step 2.2** — Run it; expect a COMPILE FAILURE (types don't exist yet):

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.NoOpLlmRouterTest"
```
Expected: **FAIL — unresolved reference: LlmRouter / NoOpLlmRouter**.

- [ ] **Step 2.3** — Create `LlmProvider.kt`:

```kotlin
package app.hisaab.llm

import app.hisaab.domain.Category

/**
 * One uniform interface over the on-device engine and each cloud vendor.
 * Implementations land in M3-4 (AndroidOnDeviceProvider + Claude/Gemini/OpenAI
 * Ktor adapters). The pipeline only ever sees this interface, so its routing
 * logic is fully unit-testable with fakes.
 */
interface LlmProvider {
    val id: ProviderId

    /** True when the engine can actually run (model downloaded / API key present). */
    suspend fun isAvailable(): Boolean

    /** Parse an SMS body into structured fields. Never throws for a "can't parse"; returns low-confidence/empty. */
    suspend fun parse(req: ParseRequest): LlmParseResult

    /** Map a merchant name to one of [categories] ids, or null when unsure. */
    suspend fun categorize(merchant: String, categories: List<Category>): String?
}
```

- [ ] **Step 2.4** — Create `LlmRouter.kt` with the `NoOpLlmRouter`:

```kotlin
package app.hisaab.llm

/**
 * Selects the active LlmProvider from CaptureConfig at call time, or null when
 * no engine is configured/available (template-only mode). M3-4 ships the real
 * DefaultLlmRouter; M3-3 ships NoOpLlmRouter so the pipeline compiles and runs
 * template-only until then.
 */
interface LlmRouter {
    suspend fun active(): LlmProvider?
}

/**
 * No-op router: always returns null, so CapturePipeline takes the template-only
 * path and routes unresolved financial messages to PENDING with parse_error.
 * Replaced by DefaultLlmRouter in M3-4.
 */
class NoOpLlmRouter : LlmRouter {
    override suspend fun active(): LlmProvider? = null
}
```

- [ ] **Step 2.5** — Run the test; expect PASS:

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.NoOpLlmRouterTest"
```
Expected: **PASS**.

- [ ] **Step 2.6** — Commit:

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/llm/ composeApp/src/commonTest/kotlin/app/hisaab/llm/
git commit -m "feat: add LlmProvider/LlmRouter interfaces + NoOpLlmRouter"
```

---

### Task 3: BanglaNumerals normalization

**Files:**
- Create test `composeApp/src/commonTest/kotlin/app/hisaab/capture/BanglaNumeralsTest.kt`
- Create `composeApp/src/commonMain/kotlin/app/hisaab/capture/BanglaNumerals.kt`

- [ ] **Step 3.1** — Write the failing test. Create `BanglaNumeralsTest.kt`:

```kotlin
package app.hisaab.capture

import kotlin.test.Test
import kotlin.test.assertEquals

class BanglaNumeralsTest {

    @Test
    fun `converts all bangla digits to ascii`() {
        assertEquals("0123456789", BanglaNumerals.normalize("০১২৩৪৫৬৭৮৯"))
    }

    @Test
    fun `leaves ascii digits and letters untouched`() {
        assertEquals("Tk 1500.50", BanglaNumerals.normalize("Tk 1500.50"))
    }

    @Test
    fun `converts mixed bangla and ascii in an amount`() {
        // "Tk ১,৫০০" -> "Tk 1,500"
        assertEquals("Tk 1,500", BanglaNumerals.normalize("Tk ১,৫০০"))
    }

    @Test
    fun `empty string returns empty`() {
        assertEquals("", BanglaNumerals.normalize(""))
    }
}
```

- [ ] **Step 3.2** — Run; expect COMPILE FAILURE:

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.capture.BanglaNumeralsTest"
```
Expected: **FAIL — unresolved reference: BanglaNumerals**.

- [ ] **Step 3.3** — Create `BanglaNumerals.kt`. Bengali digits are the contiguous Unicode block U+09E6 (০) … U+09EF (৯):

```kotlin
package app.hisaab.capture

/**
 * Normalizes Bengali (Bangla) digits ০–৯ (U+09E6..U+09EF) to ASCII 0–9.
 * Run on an SMS body before any amount/balance regex so templates only ever
 * match ASCII digits. Non-digit characters pass through unchanged.
 */
object BanglaNumerals {

    private const val BANGLA_ZERO = '\u09E6' // ০
    private const val BANGLA_NINE = '\u09EF' // ৯

    fun normalize(s: String): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                if (ch in BANGLA_ZERO..BANGLA_NINE) {
                    '0' + (ch - BANGLA_ZERO)
                } else {
                    ch
                },
            )
        }
        return sb.toString()
    }
}
```

- [ ] **Step 3.4** — Run; expect PASS:

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.capture.BanglaNumeralsTest"
```
Expected: **PASS (4 tests)**.

- [ ] **Step 3.5** — Commit:

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/capture/BanglaNumerals.kt composeApp/src/commonTest/kotlin/app/hisaab/capture/BanglaNumeralsTest.kt
git commit -m "feat: add BanglaNumerals normalization with tests"
```

---

### Task 4: The anonymized BD SMS corpus (test fixture)

**Files:**
- Create `composeApp/src/commonTest/kotlin/app/hisaab/capture/SmsCorpus.kt`

> Embedded as a Kotlin object in `commonTest` (not a file resource) because the project has no commonTest resource-loading helper and resource APIs differ per platform. This keeps the corpus pure-JVM-readable and drives both `BankTemplateTest` and `CapturePipelineTest`. All strings are anonymized (account/phone digits masked); amounts and structure mirror real bKash/Nagad/Rocket/City/BRAC/DBBL formats.

- [ ] **Step 4.1** — Create `SmsCorpus.kt`. `Direction` is the M3-1 `app.hisaab.domain.Direction`:

```kotlin
package app.hisaab.capture

import app.hisaab.domain.Direction

/**
 * Anonymized representative Bangladeshi MFS/bank SMS corpus. The crown-jewel
 * test asset: every BankTemplate change is validated against these. Account
 * numbers / phone numbers are masked; amounts and message structure mirror
 * real bKash / Nagad / Rocket / City Bank / BRAC Bank / DBBL formats.
 *
 * [expected] is the field set a fully-correct BankTemplate.extract() must
 * produce for [body] (after BanglaNumerals.normalize). [complete] marks rows a
 * template should resolve end-to-end (amount + direction present).
 */
object SmsCorpus {

    data class Sample(
        val name: String,
        val sender: String,
        val templateKey: String,
        val body: String,
        val expectedAmount: Double?,
        val expectedDirection: Direction?,
        val expectedRefNo: String?,
        val expectedBalanceAfter: Double?,
        val expectedComplete: Boolean,
    )

    val samples: List<Sample> = listOf(
        // ---- bKash ----
        Sample(
            name = "bkash_received_money",
            sender = "bKash",
            templateKey = "bkash",
            body = "You have received Tk 1,500.00 from 017XXXXXX89. Ref 9A1B2C3D4. Fee Tk 0.00. Balance Tk 3,250.50. TrxID 9A1B2C3D4 at 12/05/2026 14:33",
            expectedAmount = 1500.00,
            expectedDirection = Direction.CREDIT,
            expectedRefNo = "9A1B2C3D4",
            expectedBalanceAfter = 3250.50,
            expectedComplete = true,
        ),
        Sample(
            name = "bkash_payment",
            sender = "bKash",
            templateKey = "bkash",
            body = "Payment Tk 850.00 to SHWAPNO. Fee Tk 0.00. Balance Tk 2,400.50. TrxID 8K2L9M0P1 at 13/05/2026 10:05",
            expectedAmount = 850.00,
            expectedDirection = Direction.DEBIT,
            expectedRefNo = "8K2L9M0P1",
            expectedBalanceAfter = 2400.50,
            expectedComplete = true,
        ),
        Sample(
            name = "bkash_cashout",
            sender = "bKash",
            templateKey = "bkash",
            body = "Cash Out Tk 2,000.00 to Agent 017XXXXXX12. Fee Tk 36.50. Balance Tk 364.00. TrxID 7C3D5E6F8 at 14/05/2026 18:20",
            expectedAmount = 2000.00,
            expectedDirection = Direction.DEBIT,
            expectedRefNo = "7C3D5E6F8",
            expectedBalanceAfter = 364.00,
            expectedComplete = true,
        ),
        Sample(
            name = "bkash_send_money",
            sender = "bKash",
            templateKey = "bkash",
            body = "Send Money Tk 500.00 to 018XXXXXX44. Fee Tk 5.00. Balance Tk 1,000.00. TrxID 6B2A1Z9Y0 at 15/05/2026 09:11",
            expectedAmount = 500.00,
            expectedDirection = Direction.DEBIT,
            expectedRefNo = "6B2A1Z9Y0",
            expectedBalanceAfter = 1000.00,
            expectedComplete = true,
        ),

        // ---- Nagad ----
        Sample(
            name = "nagad_received",
            sender = "NAGAD",
            templateKey = "nagad",
            body = "Money Received. Amount: Tk 3,000.00 Sender: 019XXXXXX33 Ref: NGD55667 Balance: Tk 5,120.75 TxnID: NGD55667 13/05/2026 11:42",
            expectedAmount = 3000.00,
            expectedDirection = Direction.CREDIT,
            expectedRefNo = "NGD55667",
            expectedBalanceAfter = 5120.75,
            expectedComplete = true,
        ),
        Sample(
            name = "nagad_payment",
            sender = "NAGAD",
            templateKey = "nagad",
            body = "Payment Successful. Amount: Tk 1,200.00 To: DARAZ Balance: Tk 3,920.75 TxnID: NGD77881 13/05/2026 16:02",
            expectedAmount = 1200.00,
            expectedDirection = Direction.DEBIT,
            expectedRefNo = "NGD77881",
            expectedBalanceAfter = 3920.75,
            expectedComplete = true,
        ),
        Sample(
            name = "nagad_cashout",
            sender = "NAGAD",
            templateKey = "nagad",
            body = "Cash Out. Amount: Tk 1,000.00 Charge: Tk 11.50 Balance: Tk 2,898.25 TxnID: NGD90011 14/05/2026 19:47",
            expectedAmount = 1000.00,
            expectedDirection = Direction.DEBIT,
            expectedRefNo = "NGD90011",
            expectedBalanceAfter = 2898.25,
            expectedComplete = true,
        ),

        // ---- Rocket (DBBL Mobile Banking) ----
        Sample(
            name = "rocket_credited",
            sender = "Rocket",
            templateKey = "rocket",
            body = "Tk 2,500.00 credited to your A/C. TxnId 4455667788. Balance Tk 6,000.00. 12/05/2026 13:00",
            expectedAmount = 2500.00,
            expectedDirection = Direction.CREDIT,
            expectedRefNo = "4455667788",
            expectedBalanceAfter = 6000.00,
            expectedComplete = true,
        ),
        Sample(
            name = "rocket_debited",
            sender = "Rocket",
            templateKey = "rocket",
            body = "Tk 750.00 debited from your A/C for payment. TxnId 9988776655. Balance Tk 5,250.00. 12/05/2026 13:30",
            expectedAmount = 750.00,
            expectedDirection = Direction.DEBIT,
            expectedRefNo = "9988776655",
            expectedBalanceAfter = 5250.00,
            expectedComplete = true,
        ),

        // ---- City Bank ----
        Sample(
            name = "citybank_debit_card",
            sender = "City Bank",
            templateKey = "citybank",
            body = "Your A/C XXXX1234 is debited BDT 4,300.00 on 12-05-2026 at AGORA. Avail Bal BDT 18,700.00. Ref 30021145",
            expectedAmount = 4300.00,
            expectedDirection = Direction.DEBIT,
            expectedRefNo = "30021145",
            expectedBalanceAfter = 18700.00,
            expectedComplete = true,
        ),
        Sample(
            name = "citybank_credit_salary",
            sender = "City Bank",
            templateKey = "citybank",
            body = "Your A/C XXXX1234 is credited BDT 55,000.00 on 01-05-2026 SALARY. Avail Bal BDT 73,700.00. Ref 30019902",
            expectedAmount = 55000.00,
            expectedDirection = Direction.CREDIT,
            expectedRefNo = "30019902",
            expectedBalanceAfter = 73700.00,
            expectedComplete = true,
        ),

        // ---- BRAC Bank ----
        Sample(
            name = "bracbank_debit",
            sender = "BRAC BANK",
            templateKey = "bracbank",
            body = "Dear Customer, BDT 6,750.00 has been debited from A/C XXXX9988 on 12-05-2026. Available Balance BDT 41,250.00. TXN 7781234",
            expectedAmount = 6750.00,
            expectedDirection = Direction.DEBIT,
            expectedRefNo = "7781234",
            expectedBalanceAfter = 41250.00,
            expectedComplete = true,
        ),
        Sample(
            name = "bracbank_credit",
            sender = "BRAC BANK",
            templateKey = "bracbank",
            body = "Dear Customer, BDT 12,000.00 has been credited to A/C XXXX9988 on 13-05-2026. Available Balance BDT 53,250.00. TXN 7785678",
            expectedAmount = 12000.00,
            expectedDirection = Direction.CREDIT,
            expectedRefNo = "7785678",
            expectedBalanceAfter = 53250.00,
            expectedComplete = true,
        ),

        // ---- DBBL (Dutch-Bangla Bank) ----
        Sample(
            name = "dbbl_debit",
            sender = "DBBL",
            templateKey = "dbbl",
            body = "Dear Customer, your account XXXX5566 has been debited by Tk 980.00 on 12/05/2026. Balance Tk 22,020.00. Ref DB445566",
            expectedAmount = 980.00,
            expectedDirection = Direction.DEBIT,
            expectedRefNo = "DB445566",
            expectedBalanceAfter = 22020.00,
            expectedComplete = true,
        ),
        Sample(
            name = "dbbl_credit",
            sender = "DBBL",
            templateKey = "dbbl",
            body = "Dear Customer, your account XXXX5566 has been credited by Tk 7,500.00 on 14/05/2026. Balance Tk 29,520.00. Ref DB447788",
            expectedAmount = 7500.00,
            expectedDirection = Direction.CREDIT,
            expectedRefNo = "DB447788",
            expectedBalanceAfter = 29520.00,
            expectedComplete = true,
        ),

        // ---- Bangla numeral variant (bKash) ----
        Sample(
            name = "bkash_bangla_digits",
            sender = "bKash",
            templateKey = "bkash",
            body = "You have received Tk ১,২৫০.০০ from 017XXXXXX01. Balance Tk ৪,৫০০.০০. TrxID 5Z6Y7X8W9 at 16/05/2026 08:00",
            expectedAmount = 1250.00,
            expectedDirection = Direction.CREDIT,
            expectedRefNo = "5Z6Y7X8W9",
            expectedBalanceAfter = 4500.00,
            expectedComplete = true,
        ),

        // ---- Partial / incomplete (template can't fully resolve -> falls to LLM) ----
        Sample(
            name = "bkash_promo_no_amount",
            sender = "bKash",
            templateKey = "bkash",
            body = "Recharge any operator from bKash and get cashback! Dial *247#.",
            expectedAmount = null,
            expectedDirection = null,
            expectedRefNo = null,
            expectedBalanceAfter = null,
            expectedComplete = false,
        ),

        // ---- Non-financial promo (pre-filter should drop) ----
        Sample(
            name = "robi_promo_nonfinancial",
            sender = "ROBI",
            templateKey = "",
            body = "Enjoy 5GB internet at Tk 199! Buy now, valid 30 days. Dial *123#.",
            expectedAmount = null,
            expectedDirection = null,
            expectedRefNo = null,
            expectedBalanceAfter = null,
            expectedComplete = false,
        ),
    )

    fun byName(name: String): Sample = samples.first { it.name == name }
    fun byTemplate(key: String): List<Sample> = samples.filter { it.templateKey == key }
}
```

- [ ] **Step 4.2** — Verify the test fixture compiles (depends only on M3-1 `Direction`):

```bash
./gradlew :composeApp:compileDebugUnitTestKotlinAndroid
```
Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 4.3** — Commit:

```bash
git add composeApp/src/commonTest/kotlin/app/hisaab/capture/SmsCorpus.kt
git commit -m "test: add anonymized BD SMS corpus fixture (18 samples)"
```

---

### Task 5: TemplateExtraction + BankTemplate + BankTemplates (the parser of record)

**Files:**
- Create `composeApp/src/commonMain/kotlin/app/hisaab/capture/TemplateExtraction.kt`
- Create test `composeApp/src/commonTest/kotlin/app/hisaab/capture/BankTemplateTest.kt`
- Create `composeApp/src/commonMain/kotlin/app/hisaab/capture/BankTemplate.kt`

- [ ] **Step 5.1** — Create `TemplateExtraction.kt` first (the return type the test asserts on):

```kotlin
package app.hisaab.capture

import app.hisaab.domain.Direction

/**
 * Result of running a BankTemplate over an SMS body. [complete] is true only
 * when the template fully resolved a transaction (amount + direction present);
 * a partial/false result falls through to the LLM in CapturePipeline.
 */
data class TemplateExtraction(
    val amount: Double?,
    val direction: Direction?,
    val merchant: String?,
    val refNo: String?,
    val balanceAfter: Double?,
    val complete: Boolean,
)
```

- [ ] **Step 5.2** — Write the failing test. Create `BankTemplateTest.kt`. It loops the corpus so adding a sample auto-extends coverage:

```kotlin
package app.hisaab.capture

import app.hisaab.domain.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BankTemplateTest {

    private fun extract(sample: SmsCorpus.Sample): TemplateExtraction {
        val template = BankTemplates.forKey(sample.templateKey)
        assertNotNull(template, "No template for key='${sample.templateKey}' (${sample.name})")
        return template.extract(BanglaNumerals.normalize(sample.body))
    }

    @Test
    fun `every complete corpus sample extracts amount direction and ref`() {
        SmsCorpus.samples
            .filter { it.expectedComplete }
            .forEach { sample ->
                val r = extract(sample)
                assertEquals(sample.expectedAmount, r.amount, "amount mismatch: ${sample.name}")
                assertEquals(sample.expectedDirection, r.direction, "direction mismatch: ${sample.name}")
                assertEquals(sample.expectedRefNo, r.refNo, "refNo mismatch: ${sample.name}")
                assertEquals(sample.expectedBalanceAfter, r.balanceAfter, "balance mismatch: ${sample.name}")
                assertTrue(r.complete, "should be complete: ${sample.name}")
            }
    }

    @Test
    fun `bkash promo without amount is not complete`() {
        val r = extract(SmsCorpus.byName("bkash_promo_no_amount"))
        assertNull(r.amount)
        assertNull(r.direction)
        assertTrue(!r.complete)
    }

    @Test
    fun `bkash payment captures merchant name`() {
        val r = extract(SmsCorpus.byName("bkash_payment"))
        assertEquals("SHWAPNO", r.merchant)
        assertEquals(Direction.DEBIT, r.direction)
    }

    @Test
    fun `nagad payment captures merchant name`() {
        val r = extract(SmsCorpus.byName("nagad_payment"))
        assertEquals("DARAZ", r.merchant)
    }

    @Test
    fun `forKey returns null for unknown key`() {
        assertNull(BankTemplates.forKey("unknown_bank"))
    }

    @Test
    fun `keys lists all six seeded templates`() {
        assertEquals(
            setOf("bkash", "nagad", "rocket", "citybank", "bracbank", "dbbl"),
            BankTemplates.keys.toSet(),
        )
    }
}
```

- [ ] **Step 5.3** — Run; expect COMPILE FAILURE:

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.capture.BankTemplateTest"
```
Expected: **FAIL — unresolved reference: BankTemplate / BankTemplates**.

- [ ] **Step 5.4** — Create `BankTemplate.kt`. Each template is a set of regexes; `extract()` finds amount, direction (via debit/credit keyword sets), ref, balance, and optional merchant. Regexes are anchored loosely against the corpus formats and operate on Bangla-normalized text:

```kotlin
package app.hisaab.capture

import app.hisaab.domain.Direction

/**
 * A per-institution regex template. extract() runs over an already
 * Bangla-normalized SMS body and returns structured fields. A "complete"
 * extraction requires at minimum amount + direction; otherwise CapturePipeline
 * falls through to the LLM.
 *
 * Patterns are intentionally bounded (no catastrophic backtracking): each grabs
 * a money group "N,NNN.NN" near an anchor keyword.
 */
class BankTemplate(
    val key: String,
    private val amountRegex: Regex,
    private val debitKeywords: List<Regex>,
    private val creditKeywords: List<Regex>,
    private val refRegex: Regex?,
    private val balanceRegex: Regex?,
    private val merchantRegex: Regex?,
) {

    fun extract(body: String): TemplateExtraction {
        val amount = amountRegex.find(body)?.let { parseMoney(it.groupValues[1]) }
        val direction = resolveDirection(body)
        val refNo = refRegex?.find(body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        val balance = balanceRegex?.find(body)?.let { parseMoney(it.groupValues[1]) }
        val merchant = merchantRegex?.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        val complete = amount != null && amount > 0.0 && direction != null
        return TemplateExtraction(
            amount = amount,
            direction = direction,
            merchant = merchant,
            refNo = refNo,
            balanceAfter = balance,
            complete = complete,
        )
    }

    private fun resolveDirection(body: String): Direction? {
        if (creditKeywords.any { it.containsMatchIn(body) }) return Direction.CREDIT
        if (debitKeywords.any { it.containsMatchIn(body) }) return Direction.DEBIT
        return null
    }

    private fun parseMoney(raw: String): Double? =
        raw.replace(",", "").trim().toDoubleOrNull()
}

/**
 * In-app seeded templates for v1: bKash, Nagad, Rocket + City Bank, BRAC Bank,
 * DBBL. CDN-delivered patterns are deferred. Keyed by the sender_registry
 * template_key the pre-filter resolves.
 */
object BankTemplates {

    // Reusable money group: "1,500.00" / "55,000" / "199"
    private const val MONEY = "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"

    // ---- bKash ----
    private val BKASH = BankTemplate(
        key = "bkash",
        // First Tk-amount in the body is the transaction amount (Fee/Balance come later with their own anchors).
        amountRegex = Regex("(?:received|Payment|Cash Out|Send Money)[^0-9]*Tk\\s*$MONEY", RegexOption.IGNORE_CASE),
        creditKeywords = listOf(Regex("received", RegexOption.IGNORE_CASE)),
        debitKeywords = listOf(
            Regex("Payment", RegexOption.IGNORE_CASE),
            Regex("Cash Out", RegexOption.IGNORE_CASE),
            Regex("Send Money", RegexOption.IGNORE_CASE),
        ),
        refRegex = Regex("TrxID\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE),
        balanceRegex = Regex("Balance\\s*Tk\\s*$MONEY", RegexOption.IGNORE_CASE),
        merchantRegex = Regex("Payment\\s*Tk\\s*[0-9.,]+\\s*to\\s*([A-Za-z][A-Za-z &]+?)\\.", RegexOption.IGNORE_CASE),
    )

    // ---- Nagad ----
    private val NAGAD = BankTemplate(
        key = "nagad",
        amountRegex = Regex("Amount:\\s*Tk\\s*$MONEY", RegexOption.IGNORE_CASE),
        creditKeywords = listOf(Regex("Money Received", RegexOption.IGNORE_CASE)),
        debitKeywords = listOf(
            Regex("Payment Successful", RegexOption.IGNORE_CASE),
            Regex("Cash Out", RegexOption.IGNORE_CASE),
        ),
        refRegex = Regex("TxnID:\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE),
        balanceRegex = Regex("Balance:\\s*Tk\\s*$MONEY", RegexOption.IGNORE_CASE),
        merchantRegex = Regex("To:\\s*([A-Za-z][A-Za-z &]+?)\\s+Balance", RegexOption.IGNORE_CASE),
    )

    // ---- Rocket ----
    private val ROCKET = BankTemplate(
        key = "rocket",
        amountRegex = Regex("Tk\\s*$MONEY\\s*(?:credited|debited)", RegexOption.IGNORE_CASE),
        creditKeywords = listOf(Regex("credited", RegexOption.IGNORE_CASE)),
        debitKeywords = listOf(Regex("debited", RegexOption.IGNORE_CASE)),
        refRegex = Regex("TxnId\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE),
        balanceRegex = Regex("Balance\\s*Tk\\s*$MONEY", RegexOption.IGNORE_CASE),
        merchantRegex = null,
    )

    // ---- City Bank ----
    private val CITYBANK = BankTemplate(
        key = "citybank",
        amountRegex = Regex("(?:debited|credited)\\s*BDT\\s*$MONEY", RegexOption.IGNORE_CASE),
        creditKeywords = listOf(Regex("is credited", RegexOption.IGNORE_CASE)),
        debitKeywords = listOf(Regex("is debited", RegexOption.IGNORE_CASE)),
        refRegex = Regex("Ref\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE),
        balanceRegex = Regex("Avail Bal\\s*BDT\\s*$MONEY", RegexOption.IGNORE_CASE),
        merchantRegex = Regex("at\\s*([A-Z][A-Z &]+?)\\.\\s*Avail", RegexOption.IGNORE_CASE),
    )

    // ---- BRAC Bank ----
    private val BRACBANK = BankTemplate(
        key = "bracbank",
        amountRegex = Regex("BDT\\s*$MONEY\\s*has been", RegexOption.IGNORE_CASE),
        creditKeywords = listOf(Regex("credited to", RegexOption.IGNORE_CASE)),
        debitKeywords = listOf(Regex("debited from", RegexOption.IGNORE_CASE)),
        refRegex = Regex("TXN\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE),
        balanceRegex = Regex("Available Balance\\s*BDT\\s*$MONEY", RegexOption.IGNORE_CASE),
        merchantRegex = null,
    )

    // ---- DBBL ----
    private val DBBL = BankTemplate(
        key = "dbbl",
        amountRegex = Regex("(?:debited|credited) by\\s*Tk\\s*$MONEY", RegexOption.IGNORE_CASE),
        creditKeywords = listOf(Regex("credited by", RegexOption.IGNORE_CASE)),
        debitKeywords = listOf(Regex("debited by", RegexOption.IGNORE_CASE)),
        refRegex = Regex("Ref\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE),
        balanceRegex = Regex("Balance\\s*Tk\\s*$MONEY", RegexOption.IGNORE_CASE),
        merchantRegex = null,
    )

    private val byKey: Map<String, BankTemplate> = listOf(
        BKASH, NAGAD, ROCKET, CITYBANK, BRACBANK, DBBL,
    ).associateBy { it.key }

    val keys: List<String> = byKey.keys.toList()

    fun forKey(key: String): BankTemplate? = byKey[key]
}
```

- [ ] **Step 5.5** — Run; expect PASS:

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.capture.BankTemplateTest"
```
Expected: **PASS (6 tests)**. If any corpus row fails, fix the *regex* (not the corpus expectation) until green — the corpus is the contract.

- [ ] **Step 5.6** — Commit:

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/capture/TemplateExtraction.kt composeApp/src/commonMain/kotlin/app/hisaab/capture/BankTemplate.kt composeApp/src/commonTest/kotlin/app/hisaab/capture/BankTemplateTest.kt
git commit -m "feat: add BankTemplate + 6 seeded BD bank/MFS templates with corpus tests"
```

---

### Task 6: PreFilterResult + SmsPreFilter

**Files:**
- Create `composeApp/src/commonMain/kotlin/app/hisaab/capture/PreFilterResult.kt`
- Create test `composeApp/src/commonTest/kotlin/app/hisaab/capture/SmsPreFilterTest.kt`
- Create `composeApp/src/commonMain/kotlin/app/hisaab/capture/SmsPreFilter.kt`

> Per the shared contract, `SmsPreFilter.classify(raw, mapping)` takes the already-looked-up `SenderMapping?` (the pipeline does the repo lookup). The class still takes `senderRegistry` (the `SenderRepository`) in its constructor for the contract signature, but `classify` is pure given the mapping argument.

- [ ] **Step 6.1** — Create `PreFilterResult.kt`. `SenderMapping` is the M3-1 `app.hisaab.domain.SenderMapping`:

```kotlin
package app.hisaab.capture

import app.hisaab.domain.SenderMapping

/**
 * Outcome of the deterministic financial gate. NotFinancial is dropped before
 * any LLM/cloud call. KnownTemplate runs BankTemplate first. UnknownFinancial
 * goes straight to the LLM (or PENDING if no LLM).
 */
sealed interface PreFilterResult {
    /** Known promo sender (is_financial=0) or no money signal. Dropped. */
    data object NotFinancial : PreFilterResult

    /** Mapped sender with a usable BankTemplate key. */
    data class KnownTemplate(val mapping: SenderMapping, val templateKey: String) : PreFilterResult

    /** Has a money signal but no template; mapping may be null (brand-new sender). */
    data class UnknownFinancial(val mapping: SenderMapping?) : PreFilterResult
}
```

- [ ] **Step 6.2** — Write the failing test. Create `SmsPreFilterTest.kt`. It uses a tiny fake `SenderRepository` (the contract type from M3-1). Since `SenderRepository` is a concrete class taking `HisaabDatabase`, the test builds it over an in-memory DB and seeds via `upsert`:

```kotlin
package app.hisaab.capture

import app.hisaab.data.SenderRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.BankType
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import app.hisaab.domain.SenderMapping
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmsPreFilterTest {

    private fun raw(sender: String, body: String) =
        RawCapture(sender = sender, body = body, receivedAt = 1000L, channel = CaptureChannel.SMS)

    private fun mapping(
        sender: String,
        bankType: BankType,
        isFinancial: Boolean,
        templateKey: String?,
    ) = SenderMapping(
        id = "m_$sender",
        senderId = sender,
        displayName = sender,
        bankType = bankType,
        isFinancial = isFinancial,
        templateKey = templateKey,
        accountId = null,
        createdAt = 1000L,
    )

    @Test
    fun `known template sender with money signal classifies KnownTemplate`() = runTest {
        val filter = SmsPreFilter(SenderRepository(TestDatabase.create()))
        val sample = SmsCorpus.byName("bkash_received_money")
        val result = filter.classify(
            raw(sample.sender, sample.body),
            mapping(sample.sender, BankType.BKASH, isFinancial = true, templateKey = "bkash"),
        )
        assertTrue(result is PreFilterResult.KnownTemplate)
        assertEquals("bkash", (result as PreFilterResult.KnownTemplate).templateKey)
    }

    @Test
    fun `promo sender flagged not financial is dropped`() = runTest {
        val filter = SmsPreFilter(SenderRepository(TestDatabase.create()))
        val sample = SmsCorpus.byName("robi_promo_nonfinancial")
        val result = filter.classify(
            raw(sample.sender, sample.body),
            mapping(sample.sender, BankType.OTHER, isFinancial = false, templateKey = null),
        )
        assertEquals(PreFilterResult.NotFinancial, result)
    }

    @Test
    fun `unknown sender with money signal classifies UnknownFinancial`() = runTest {
        val filter = SmsPreFilter(SenderRepository(TestDatabase.create()))
        val result = filter.classify(
            raw("0152233", "BDT 1,200.00 debited TrxID AB12"),
            mapping = null,
        )
        assertTrue(result is PreFilterResult.UnknownFinancial)
    }

    @Test
    fun `unknown sender without money signal is dropped`() = runTest {
        val filter = SmsPreFilter(SenderRepository(TestDatabase.create()))
        val result = filter.classify(
            raw("FRIEND", "are you coming to dinner tonight?"),
            mapping = null,
        )
        assertEquals(PreFilterResult.NotFinancial, result)
    }

    @Test
    fun `financial mapped sender without template falls to UnknownFinancial`() = runTest {
        val filter = SmsPreFilter(SenderRepository(TestDatabase.create()))
        val result = filter.classify(
            raw("SOMEBANK", "Your account credited BDT 500.00 TxnID 9"),
            mapping("SOMEBANK", BankType.BANK, isFinancial = true, templateKey = null),
        )
        assertTrue(result is PreFilterResult.UnknownFinancial)
    }
}
```

- [ ] **Step 6.3** — Run; expect COMPILE FAILURE:

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.capture.SmsPreFilterTest"
```
Expected: **FAIL — unresolved reference: SmsPreFilter**.

- [ ] **Step 6.4** — Create `SmsPreFilter.kt`:

```kotlin
package app.hisaab.capture

import app.hisaab.data.SenderRepository
import app.hisaab.domain.RawCapture
import app.hisaab.domain.SenderMapping

/**
 * Deterministic, offline "is this a financial transaction?" gate. Drops
 * non-financial messages BEFORE any LLM/cloud call. classify() is pure given
 * the pre-resolved [mapping] (the pipeline does the repo lookup); the
 * SenderRepository is held for future sender-promotion lookups and to match the
 * shared constructor contract.
 *
 * A money signal = a currency token (Tk / ৳ / BDT), a TrxID/TxnID/TXN ref, or a
 * debit/credit keyword. A sender explicitly flagged is_financial=0 is always
 * NotFinancial regardless of body.
 */
class SmsPreFilter(
    @Suppress("unused") private val senderRegistry: SenderRepository,
) {

    fun classify(raw: RawCapture, mapping: SenderMapping?): PreFilterResult {
        // Explicit promo/non-financial sender → drop.
        if (mapping != null && !mapping.isFinancial) return PreFilterResult.NotFinancial

        val body = BanglaNumerals.normalize(raw.body)
        if (!hasMoneySignal(body)) return PreFilterResult.NotFinancial

        val templateKey = mapping?.templateKey
        if (mapping != null && !templateKey.isNullOrBlank() && BankTemplates.forKey(templateKey) != null) {
            return PreFilterResult.KnownTemplate(mapping, templateKey)
        }
        return PreFilterResult.UnknownFinancial(mapping)
    }

    private fun hasMoneySignal(body: String): Boolean {
        if (CURRENCY.containsMatchIn(body) && AMOUNT.containsMatchIn(body)) return true
        if (REF.containsMatchIn(body)) return true
        if (DIRECTION.containsMatchIn(body)) return true
        return false
    }

    private companion object {
        val CURRENCY = Regex("\\b(?:Tk|BDT)\\b|\u09F3", RegexOption.IGNORE_CASE) // ৳ = U+09F3
        val AMOUNT = Regex("[0-9][0-9,]*(?:\\.[0-9]{1,2})?")
        val REF = Regex("\\b(?:TrxID|TxnID|TxnId|TXN)\\b", RegexOption.IGNORE_CASE)
        val DIRECTION = Regex("\\b(?:credited|debited|received|Cash Out|Send Money|Payment)\\b", RegexOption.IGNORE_CASE)
    }
}
```

- [ ] **Step 6.5** — Run; expect PASS:

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.capture.SmsPreFilterTest"
```
Expected: **PASS (5 tests)**.

- [ ] **Step 6.6** — Commit:

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/capture/PreFilterResult.kt composeApp/src/commonMain/kotlin/app/hisaab/capture/SmsPreFilter.kt composeApp/src/commonTest/kotlin/app/hisaab/capture/SmsPreFilterTest.kt
git commit -m "feat: add SmsPreFilter financial gate with tests"
```

---

### Task 7: ConfidenceScorer

**Files:**
- Create test `composeApp/src/commonTest/kotlin/app/hisaab/capture/ConfidenceScorerTest.kt`
- Create `composeApp/src/commonMain/kotlin/app/hisaab/capture/ConfidenceScorer.kt`

- [ ] **Step 7.1** — Write the failing test. Create `ConfidenceScorerTest.kt`:

```kotlin
package app.hisaab.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfidenceScorerTest {

    @Test
    fun `complete template with resolved fields and known sender scores high`() {
        val score = ConfidenceScorer.score(
            templateComplete = true,
            fieldsResolved = true,
            llmConfidence = null,
            senderKnown = true,
        )
        assertTrue(score >= 0.9, "expected >= 0.9 but was $score")
    }

    @Test
    fun `llm-only unknown is capped at 0_7`() {
        val score = ConfidenceScorer.score(
            templateComplete = false,
            fieldsResolved = true,
            llmConfidence = 0.95,
            senderKnown = false,
        )
        assertTrue(score <= 0.7, "expected <= 0.7 but was $score")
    }

    @Test
    fun `unresolved fields drag score below auto-post threshold`() {
        val score = ConfidenceScorer.score(
            templateComplete = true,
            fieldsResolved = false,
            llmConfidence = null,
            senderKnown = true,
        )
        assertTrue(score < 0.85, "expected < 0.85 but was $score")
    }

    @Test
    fun `nothing resolved scores zero`() {
        assertEquals(
            0.0,
            ConfidenceScorer.score(
                templateComplete = false,
                fieldsResolved = false,
                llmConfidence = null,
                senderKnown = false,
            ),
        )
    }

    @Test
    fun `output is always clamped to 0_1`() {
        val high = ConfidenceScorer.score(true, true, 1.0, true)
        val low = ConfidenceScorer.score(false, false, 0.0, false)
        assertTrue(high in 0.0..1.0)
        assertTrue(low in 0.0..1.0)
    }
}
```

- [ ] **Step 7.2** — Run; expect COMPILE FAILURE:

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.capture.ConfidenceScorerTest"
```
Expected: **FAIL — unresolved reference: ConfidenceScorer**.

- [ ] **Step 7.3** — Create `ConfidenceScorer.kt`. Template-complete path can reach 0.9+; LLM-only unknown is hard-capped at 0.7; field-resolution and sender-known modulate the rest:

```kotlin
package app.hisaab.capture

/**
 * Blends parse signals into a 0..1 confidence used by the auto-post gate.
 *
 * - A fully-matched template from a known sender, with account+merchant+category
 *   resolved, reaches 0.9+ (auto-post territory).
 * - An LLM-only parse of an unknown message is hard-capped at 0.7 so it always
 *   routes to review, per design D5.
 * - Unresolved downstream fields (no account/merchant/category) pull the score
 *   below the default 0.85 threshold.
 */
object ConfidenceScorer {

    private const val TEMPLATE_BASE = 0.6
    private const val SENDER_KNOWN_BONUS = 0.2
    private const val FIELDS_RESOLVED_BONUS = 0.2
    private const val LLM_ONLY_CAP = 0.7

    fun score(
        templateComplete: Boolean,
        fieldsResolved: Boolean,
        llmConfidence: Double?,
        senderKnown: Boolean,
    ): Double {
        val raw = if (templateComplete) {
            TEMPLATE_BASE +
                (if (senderKnown) SENDER_KNOWN_BONUS else 0.0) +
                (if (fieldsResolved) FIELDS_RESOLVED_BONUS else 0.0)
        } else if (llmConfidence != null) {
            // LLM-only path: scale the model's self-report by field resolution, then cap.
            val base = llmConfidence * (if (fieldsResolved) 1.0 else 0.6)
            minOf(base, LLM_ONLY_CAP)
        } else {
            0.0
        }
        return raw.coerceIn(0.0, 1.0)
    }
}
```

- [ ] **Step 7.4** — Run; expect PASS:

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.capture.ConfidenceScorerTest"
```
Expected: **PASS (5 tests)**.

- [ ] **Step 7.5** — Commit:

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/capture/ConfidenceScorer.kt composeApp/src/commonTest/kotlin/app/hisaab/capture/ConfidenceScorerTest.kt
git commit -m "feat: add ConfidenceScorer with tests"
```

---

### Task 8: AccountMatcher (auto-create MFS/BANK + persist mapping)

**Files:**
- Create test `composeApp/src/commonTest/kotlin/app/hisaab/capture/AccountMatcherTest.kt`
- Create `composeApp/src/commonMain/kotlin/app/hisaab/capture/AccountMatcher.kt`

> `AccountMatcher.resolve(mapping, bankType)` returns the account id for a mapped sender, else auto-creates an account (`MFS` for bKash/Nagad/Rocket, `BANK` for banks) and persists the mapping via `SenderRepository.setAccount`. Constructor takes the existing `AccountRepository` + M3-1 `SenderRepository`.

- [ ] **Step 8.1** — Write the failing test. Create `AccountMatcherTest.kt`:

```kotlin
package app.hisaab.capture

import app.hisaab.data.AccountRepository
import app.hisaab.data.SenderRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.BankType
import app.hisaab.domain.SenderMapping
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AccountMatcherTest {

    private fun mapping(
        sender: String,
        bankType: BankType,
        accountId: String?,
    ) = SenderMapping(
        id = "m_$sender",
        senderId = sender,
        displayName = sender,
        bankType = bankType,
        isFinancial = true,
        templateKey = null,
        accountId = accountId,
        createdAt = 1000L,
    )

    @Test
    fun `returns existing mapped account without creating a new one`() = runTest {
        val db = TestDatabase.create()
        val accountRepo = AccountRepository(db)
        val senderRepo = SenderRepository(db)
        val existingId = accountRepo.add("My bKash", app.hisaab.domain.AccountKind.MFS, "bKash")
        val matcher = AccountMatcher(accountRepo, senderRepo)

        val resolved = matcher.resolve(mapping("bKash", BankType.BKASH, accountId = existingId), BankType.BKASH)

        assertEquals(existingId, resolved)
        assertEquals(1, accountRepo.observeActive().first().size)
    }

    @Test
    fun `auto-creates MFS account for bKash and persists mapping`() = runTest {
        val db = TestDatabase.create()
        val accountRepo = AccountRepository(db)
        val senderRepo = SenderRepository(db)
        senderRepo.upsert(mapping("bKash", BankType.BKASH, accountId = null))
        val matcher = AccountMatcher(accountRepo, senderRepo)

        val resolved = matcher.resolve(senderRepo.findBySenderId("bKash"), BankType.BKASH)

        assertNotNull(resolved)
        val accounts = accountRepo.observeActive().first()
        assertEquals(1, accounts.size)
        assertEquals(app.hisaab.domain.AccountKind.MFS, accounts.first().kind)
        // mapping persisted
        assertEquals(resolved, senderRepo.findBySenderId("bKash")?.accountId)
    }

    @Test
    fun `auto-creates BANK account for a bank sender`() = runTest {
        val db = TestDatabase.create()
        val accountRepo = AccountRepository(db)
        val senderRepo = SenderRepository(db)
        senderRepo.upsert(mapping("BRAC BANK", BankType.BANK, accountId = null))
        val matcher = AccountMatcher(accountRepo, senderRepo)

        val resolved = matcher.resolve(senderRepo.findBySenderId("BRAC BANK"), BankType.BANK)

        assertNotNull(resolved)
        assertEquals(app.hisaab.domain.AccountKind.BANK, accountRepo.observeActive().first().first().kind)
    }

    @Test
    fun `null mapping returns null (forces review)`() = runTest {
        val db = TestDatabase.create()
        val matcher = AccountMatcher(AccountRepository(db), SenderRepository(db))
        assertNull(matcher.resolve(mapping = null, bankType = BankType.OTHER))
    }
}
```

- [ ] **Step 8.2** — Run; expect COMPILE FAILURE:

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.capture.AccountMatcherTest"
```
Expected: **FAIL — unresolved reference: AccountMatcher**.

- [ ] **Step 8.3** — Create `AccountMatcher.kt`. Maps `BankType` → `AccountKind`; uses the existing `AccountRepository.add` and M3-1 `SenderRepository.setAccount`:

```kotlin
package app.hisaab.capture

import app.hisaab.data.AccountRepository
import app.hisaab.data.SenderRepository
import app.hisaab.domain.AccountKind
import app.hisaab.domain.BankType
import app.hisaab.domain.SenderMapping

/**
 * Resolves the destination account for a captured SMS. If the sender is already
 * mapped to an account, returns it. Otherwise auto-creates an account
 * (MFS for bKash/Nagad/Rocket, BANK for banks/cards) and persists the mapping
 * so the next SMS from that sender resolves instantly. A null mapping (brand-new
 * unmapped sender) returns null, forcing the candidate to review.
 */
class AccountMatcher(
    private val accountRepo: AccountRepository,
    private val senderRepo: SenderRepository,
) {

    suspend fun resolve(mapping: SenderMapping?, bankType: BankType): String? {
        if (mapping == null) return null
        mapping.accountId?.takeIf { it.isNotBlank() }?.let { return it }

        val kind = accountKindFor(bankType)
        val accountId = accountRepo.add(
            name = mapping.displayName,
            kind = kind,
            institution = mapping.displayName,
        )
        senderRepo.setAccount(senderId = mapping.senderId, accountId = accountId)
        return accountId
    }

    private fun accountKindFor(bankType: BankType): AccountKind = when (bankType) {
        BankType.BKASH, BankType.NAGAD, BankType.ROCKET -> AccountKind.MFS
        BankType.BANK -> AccountKind.BANK
        BankType.CARD -> AccountKind.CARD
        BankType.OTHER -> AccountKind.BANK
    }
}
```

- [ ] **Step 8.4** — Run; expect PASS:

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.capture.AccountMatcherTest"
```
Expected: **PASS (4 tests)**.

- [ ] **Step 8.5** — Commit:

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/capture/AccountMatcher.kt composeApp/src/commonTest/kotlin/app/hisaab/capture/AccountMatcherTest.kt
git commit -m "feat: add AccountMatcher auto-create + mapping persistence with tests"
```

---

### Task 9: CaptureEvent (auto-post event surfaced to M3-5)

**Files:**
- Create `composeApp/src/commonMain/kotlin/app/hisaab/capture/CaptureEvent.kt`

> Per the fixed contract (R3): no constructor callback. The pipeline emits a structured `CaptureEvent.AutoPosted` into an injected `MutableSharedFlow<CaptureEvent>`; M3-5's snackbar host collects `appContainer.captureEvents`. `AutoPosted` carries exactly the fields the snackbar needs (txnId for Undo, candidateId to dismiss, amount/sender/direction for the "+৳500 · bKash · auto-added" copy). `Direction` is the M3-1 `app.hisaab.domain.Direction`.

- [ ] **Step 9.1** — Create `CaptureEvent.kt`:

```kotlin
package app.hisaab.capture

import app.hisaab.domain.Direction

/**
 * Events emitted by CapturePipeline for the UI layer to react to. The pipeline
 * pushes these into a MutableSharedFlow injected by AppContainer; M3-5's
 * snackbar host collects AppContainer.captureEvents and shows the quiet
 * "auto-added · Undo" snackbar. Decouples the pure pipeline from any UI/ctor
 * callback (R3): the pipeline never holds a lambda or a Composable.
 */
sealed interface CaptureEvent {

    /**
     * A high-confidence candidate was auto-posted to the ledger.
     * [txnId] is the posted ledger transaction (Undo deletes it); [candidateId]
     * is the capture_inbox row (Undo sets it DISMISSED); [amount]/[sender]/
     * [direction] drive the snackbar copy.
     */
    data class AutoPosted(
        val txnId: String,
        val candidateId: String,
        val amount: Double,
        val sender: String,
        val direction: Direction,
    ) : CaptureEvent
}
```

- [ ] **Step 9.2** — Verify it compiles (depends only on M3-1 `Direction`):

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```
Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 9.3** — Commit:

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/capture/CaptureEvent.kt
git commit -m "feat: add CaptureEvent.AutoPosted for snackbar surfacing"
```

---

### Task 10: Fakes for pipeline tests (FakeLlmRouter + FakeLlmProvider)

**Files:**
- Create `composeApp/src/commonTest/kotlin/app/hisaab/capture/support/FakeLlm.kt`

- [ ] **Step 10.1** — Create `FakeLlm.kt`. Hand-written fakes (per testing rules: fakes over mocks). `FakeLlmRouter` returns whatever provider it's given (or null); `FakeLlmProvider` returns a queued canned result:

```kotlin
package app.hisaab.capture.support

import app.hisaab.domain.Category
import app.hisaab.llm.LlmParseResult
import app.hisaab.llm.LlmProvider
import app.hisaab.llm.LlmRouter
import app.hisaab.llm.ParseRequest
import app.hisaab.llm.ProviderId

/**
 * A router that returns a fixed provider (or null for the no-LLM path).
 * Records how many times active() was called for assertions.
 */
class FakeLlmRouter(private val provider: LlmProvider?) : LlmRouter {
    var activeCallCount: Int = 0
        private set

    override suspend fun active(): LlmProvider? {
        activeCallCount++
        return provider
    }
}

/**
 * A provider that returns a canned parse result and category. Records the last
 * ParseRequest so tests can assert the pipeline passed redacted-and-correct text.
 */
class FakeLlmProvider(
    override val id: ProviderId = ProviderId.CLOUD_CLAUDE,
    private val available: Boolean = true,
    private val result: LlmParseResult,
    private val category: String? = null,
) : LlmProvider {

    var lastRequest: ParseRequest? = null
        private set

    override suspend fun isAvailable(): Boolean = available

    override suspend fun parse(req: ParseRequest): LlmParseResult {
        lastRequest = req
        return result
    }

    override suspend fun categorize(merchant: String, categories: List<Category>): String? = category
}
```

- [ ] **Step 10.2** — Verify the test source compiles:

```bash
./gradlew :composeApp:compileDebugUnitTestKotlinAndroid
```
Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 10.3** — Commit:

```bash
git add composeApp/src/commonTest/kotlin/app/hisaab/capture/support/FakeLlm.kt
git commit -m "test: add FakeLlmRouter + FakeLlmProvider fakes"
```

---

### Task 11: CapturePipeline.process — the orchestrator (atomic auto-post, linked txn, AutoPosted event)

**Files:**
- Create `composeApp/src/commonMain/kotlin/app/hisaab/capture/Sha256.kt`
- Create test `composeApp/src/commonTest/kotlin/app/hisaab/capture/Sha256Test.kt`
- Create test `composeApp/src/commonTest/kotlin/app/hisaab/capture/CapturePipelineTest.kt`
- Create `composeApp/src/commonMain/kotlin/app/hisaab/capture/CapturePipeline.kt`

> **Final constructor (R4, verbatim):**
> `CapturePipeline(db: HisaabDatabase, inboxRepo, senderRepo, accountMatcher, preFilter, llmRouter, txnRepo, configRepo, captureEvents: MutableSharedFlow<CaptureEvent>)`.
> `merchantRepo` is intentionally NOT a parameter — `TransactionRepository` already owns merchant upsert via `NewTransaction.merchantName` (merchant upsert happens at post-time inside `txnRepo.add`), so a `merchantRepo` ctor param would be dead.
>
> **Atomicity (R2, spec §7 step 8):** the auto-post path performs `{ create linked txn ; mark candidate AUTO_POSTED }` inside a single `db.transaction { }`, following the existing multi-write atomicity pattern `TransactionRepository` uses for split parent+children writes. The transaction is the only place `db` is used; only `AppContainer` constructs the pipeline, so passing `db` is a pure addition with no downstream ripple.
>
> **Linking (R1):** the auto-post path creates the txn ALREADY LINKED via `NewTransaction(..., source = TxnSource.SMS, captureId = candidateId)`. There is no `linkCapture`/`setCaptureId` step and no `TransactionQueries.sq` edit here — M3-1 owns `NewTransaction.captureId` and the INSERT that writes `capture_id`.
>
> **Event (R3):** after a successful auto-post, the pipeline emits `CaptureEvent.AutoPosted(...)` into the injected `captureEvents` flow. No `onAutoPost` ctor callback exists.

- [ ] **Step 11.1** — Create the pure SHA-256 helper used by dedup. Create `composeApp/src/commonMain/kotlin/app/hisaab/capture/Sha256.kt` (no `java.security` — pure Kotlin so it runs in commonMain/JVM tests):

```kotlin
package app.hisaab.capture

/**
 * Minimal pure-Kotlin SHA-256 for the capture dedup hash. Avoids java.security
 * so it compiles for every KMP target and runs in commonTest. Not used for any
 * security-critical hashing — only as a stable dedup key over sender|body.
 */
object Sha256 {

    fun hexOf(input: String): String {
        val bytes = digest(input.encodeToByteArray())
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    private val K = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
    )

    private fun digest(msg: ByteArray): ByteArray {
        var h0 = 0x6a09e667; var h1 = -0x4498517b; var h2 = 0x3c6ef372; var h3 = -0x5ab00ac6
        var h4 = 0x510e527f; var h5 = -0x64fa9774; var h6 = 0x1f83d9ab; var h7 = 0x5be0cd19

        val ml = msg.size.toLong() * 8
        val withOne = msg + byteArrayOf(0x80.toByte())
        val padLen = ((56 - withOne.size % 64) + 64) % 64
        val padded = withOne + ByteArray(padLen) + ByteArray(8) { ((ml ushr (56 - it * 8)) and 0xFF).toByte() }

        val w = IntArray(64)
        var i = 0
        while (i < padded.size) {
            for (t in 0 until 16) {
                w[t] = (padded[i + t * 4].toInt() and 0xFF shl 24) or
                    (padded[i + t * 4 + 1].toInt() and 0xFF shl 16) or
                    (padded[i + t * 4 + 2].toInt() and 0xFF shl 8) or
                    (padded[i + t * 4 + 3].toInt() and 0xFF)
            }
            for (t in 16 until 64) {
                val s0 = w[t - 15].rotr(7) xor w[t - 15].rotr(18) xor (w[t - 15] ushr 3)
                val s1 = w[t - 2].rotr(17) xor w[t - 2].rotr(19) xor (w[t - 2] ushr 10)
                w[t] = w[t - 16] + s0 + w[t - 7] + s1
            }
            var a = h0; var b = h1; var c = h2; var d = h3
            var e = h4; var f = h5; var g = h6; var hh = h7
            for (t in 0 until 64) {
                val s1 = e.rotr(6) xor e.rotr(11) xor e.rotr(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + K[t] + w[t]
                val s0 = a.rotr(2) xor a.rotr(13) xor a.rotr(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h0 += a; h1 += b; h2 += c; h3 += d; h4 += e; h5 += f; h6 += g; h7 += hh
            i += 64
        }
        return intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7).flatMap { v ->
            listOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
        }.toByteArray()
    }

    private fun Int.rotr(n: Int): Int = (this ushr n) or (this shl (32 - n))
}
```

- [ ] **Step 11.2** — Add the SHA-256 known-vector test. Create `composeApp/src/commonTest/kotlin/app/hisaab/capture/Sha256Test.kt`:

```kotlin
package app.hisaab.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class Sha256Test {

    @Test
    fun `known vector for empty string`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hexOf(""),
        )
    }

    @Test
    fun `known vector for abc`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hexOf("abc"),
        )
    }

    @Test
    fun `different inputs produce different hashes`() {
        assertNotEquals(Sha256.hexOf("bKash|Payment Tk 1"), Sha256.hexOf("bKash|Payment Tk 2"))
    }
}
```

- [ ] **Step 11.3** — Run the SHA-256 test; expect PASS:

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.capture.Sha256Test"
```
Expected: **PASS (3 tests)**. If the empty/abc vectors fail, the SHA-256 implementation has a bug — fix `Sha256.kt`, not the test.

- [ ] **Step 11.4** — Write the failing pipeline test. Create `CapturePipelineTest.kt` covering all required cases plus the three R-fix assertions: known-template auto-post (asserts (i) `AutoPosted` event emitted, (ii) the ledger txn carries `capture_id`, (iii) the auto-post is atomic), unknown→LLM, no-LLM→PENDING with parse_error, duplicate skip, low-confidence→PENDING, alwaysReview→PENDING, non-financial dropped. The pipeline takes the R4 ctor (`db` first, `captureEvents` last; no `merchantRepo`). The test backs `captureEvents` with a `MutableSharedFlow(extraBufferCapacity = 16)` and reads emitted events via `replayCache` after `process` returns (the buffer + replay make the emission observable without a collector):

```kotlin
package app.hisaab.capture

import app.hisaab.capture.support.FakeLlmProvider
import app.hisaab.capture.support.FakeLlmRouter
import app.hisaab.data.AccountRepository
import app.hisaab.data.CaptureConfigRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.SenderRepository
import app.hisaab.data.TagRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.BankType
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CaptureStatus
import app.hisaab.domain.Direction
import app.hisaab.domain.RawCapture
import app.hisaab.domain.SenderMapping
import app.hisaab.llm.LlmParseResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CapturePipelineTest {

    private class Fixture(
        val db: HisaabDatabase,
        val inboxRepo: CaptureInboxRepository,
        val senderRepo: SenderRepository,
        val accountRepo: AccountRepository,
        val categoryRepo: CategoryRepository,
        val merchantRepo: MerchantRepository,
        val txnRepo: TransactionRepository,
        val configRepo: CaptureConfigRepository,
        val events: MutableSharedFlow<CaptureEvent>,
    )

    private suspend fun fixture(): Fixture {
        val db = TestDatabase.create()
        val categoryRepo = CategoryRepository(db)
        categoryRepo.ensureDefaults()
        val merchantRepo = MerchantRepository(db)
        val tagRepo = TagRepository(db)
        return Fixture(
            db = db,
            inboxRepo = CaptureInboxRepository(db),
            senderRepo = SenderRepository(db),
            accountRepo = AccountRepository(db),
            categoryRepo = categoryRepo,
            merchantRepo = merchantRepo,
            txnRepo = TransactionRepository(db, merchantRepo, tagRepo),
            configRepo = CaptureConfigRepository(db),
            events = MutableSharedFlow(extraBufferCapacity = 16),
        )
    }

    private fun pipeline(
        f: Fixture,
        router: FakeLlmRouter,
    ): CapturePipeline = CapturePipeline(
        db = f.db,
        inboxRepo = f.inboxRepo,
        senderRepo = f.senderRepo,
        accountMatcher = AccountMatcher(f.accountRepo, f.senderRepo),
        preFilter = SmsPreFilter(f.senderRepo),
        llmRouter = router,
        txnRepo = f.txnRepo,
        configRepo = f.configRepo,
        captureEvents = f.events,
    )

    private fun raw(sender: String, body: String, ts: Long = 1000L) =
        RawCapture(sender = sender, body = body, receivedAt = ts, channel = CaptureChannel.SMS)

    private suspend fun seedSender(
        f: Fixture,
        sender: String,
        bankType: BankType,
        templateKey: String?,
        accountId: String? = null,
        isFinancial: Boolean = true,
    ) {
        f.senderRepo.upsert(
            SenderMapping(
                id = "m_$sender",
                senderId = sender,
                displayName = sender,
                bankType = bankType,
                isFinancial = isFinancial,
                templateKey = templateKey,
                accountId = accountId,
                createdAt = 1000L,
            ),
        )
    }

    @Test
    fun `known-template high-confidence message auto-posts atomically, links capture_id, and emits AutoPosted`() = runTest {
        val f = fixture()
        seedSender(f, "bKash", BankType.BKASH, templateKey = "bkash")
        val sample = SmsCorpus.byName("bkash_received_money")

        pipeline(f, FakeLlmRouter(null)).process(raw(sample.sender, sample.body))

        val candidate = f.inboxRepo.observeRecent(10).first().single()
        assertEquals(CaptureStatus.AUTO_POSTED, candidate.status)
        assertEquals(1500.0, candidate.amount)
        assertEquals(Direction.CREDIT, candidate.direction)
        assertNotNull(candidate.proposedAccountId)

        // (iii) atomic auto-post: exactly one ledger txn now exists alongside the AUTO_POSTED candidate.
        val txns = f.txnRepo.observeRecent(10).first()
        assertEquals(1, txns.size)
        // (ii) the txn was created ALREADY LINKED to its candidate (NewTransaction.captureId).
        assertEquals(candidate.id, txns.single().captureId)

        // (i) AutoPosted event emitted into the injected SharedFlow.
        val event = f.events.replayCache.single()
        assertTrue(event is CaptureEvent.AutoPosted)
        val posted = event as CaptureEvent.AutoPosted
        assertEquals(candidate.id, posted.candidateId)
        assertEquals(txns.single().id, posted.txnId)
        assertEquals(1500.0, posted.amount)
        assertEquals("bKash", posted.sender)
        assertEquals(Direction.CREDIT, posted.direction)
    }

    @Test
    fun `unknown financial message routes to the LLM`() = runTest {
        val f = fixture()
        seedSender(f, "NEWBANK", BankType.BANK, templateKey = null)
        val provider = FakeLlmProvider(
            result = LlmParseResult(
                amount = 999.0,
                direction = Direction.DEBIT,
                merchant = "GROCERY",
                categoryId = "food",
                balanceAfter = 5000.0,
                refNo = "Z123",
                confidence = 0.95,
                isFinancial = true,
            ),
        )
        val router = FakeLlmRouter(provider)

        pipeline(f, router).process(raw("NEWBANK", "Your A/C debited BDT 999.00 TxnID Z123 at GROCERY"))

        assertEquals(1, router.activeCallCount)
        assertNotNull(provider.lastRequest)
        val candidate = f.inboxRepo.observeRecent(10).first().single()
        assertEquals(999.0, candidate.amount)
        // LLM-only unknown is capped <= 0.7 -> below 0.85 threshold -> PENDING
        assertEquals(CaptureStatus.PENDING, candidate.status)
        // No auto-post -> no event.
        assertTrue(f.events.replayCache.isEmpty())
    }

    @Test
    fun `no LLM and incomplete template saves PENDING with parse_error`() = runTest {
        val f = fixture()
        seedSender(f, "NEWBANK", BankType.BANK, templateKey = null)

        pipeline(f, FakeLlmRouter(null)).process(raw("NEWBANK", "Your A/C debited BDT 50.00 TxnID Q1"))

        val candidate = f.inboxRepo.observeRecent(10).first().single()
        assertEquals(CaptureStatus.PENDING, candidate.status)
        assertEquals("needs_manual", candidate.parseError)
    }

    @Test
    fun `duplicate message is skipped`() = runTest {
        val f = fixture()
        seedSender(f, "bKash", BankType.BKASH, templateKey = "bkash")
        val sample = SmsCorpus.byName("bkash_payment")
        val p = pipeline(f, FakeLlmRouter(null))

        p.process(raw(sample.sender, sample.body))
        p.process(raw(sample.sender, sample.body)) // exact duplicate

        assertEquals(1, f.inboxRepo.observeRecent(10).first().size)
    }

    @Test
    fun `low-confidence parse routes to PENDING`() = runTest {
        val f = fixture()
        seedSender(f, "NEWBANK", BankType.BANK, templateKey = null)
        val provider = FakeLlmProvider(
            result = LlmParseResult(
                amount = 200.0,
                direction = Direction.DEBIT,
                merchant = null,
                categoryId = null,
                balanceAfter = null,
                refNo = null,
                confidence = 0.4,
                isFinancial = true,
            ),
        )
        pipeline(f, FakeLlmRouter(provider)).process(raw("NEWBANK", "debited BDT 200.00 TxnID L1"))

        assertEquals(CaptureStatus.PENDING, f.inboxRepo.observeRecent(10).first().single().status)
    }

    @Test
    fun `alwaysReview forces PENDING even for a high-confidence template`() = runTest {
        val f = fixture()
        f.configRepo.setAlwaysReview(true)
        seedSender(f, "bKash", BankType.BKASH, templateKey = "bkash")
        val sample = SmsCorpus.byName("bkash_received_money")

        pipeline(f, FakeLlmRouter(null)).process(raw(sample.sender, sample.body))

        val candidate = f.inboxRepo.observeRecent(10).first().single()
        assertEquals(CaptureStatus.PENDING, candidate.status)
        assertEquals(0, f.txnRepo.observeRecent(10).first().size)
        assertTrue(f.events.replayCache.isEmpty())
    }

    @Test
    fun `non-financial promo sender is dropped entirely`() = runTest {
        val f = fixture()
        seedSender(f, "ROBI", BankType.OTHER, templateKey = null, isFinancial = false)
        val sample = SmsCorpus.byName("robi_promo_nonfinancial")

        pipeline(f, FakeLlmRouter(null)).process(raw(sample.sender, sample.body))

        assertEquals(0, f.inboxRepo.observeRecent(10).first().size)
    }
}
```

- [ ] **Step 11.5** — Run; expect COMPILE FAILURE:

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.capture.CapturePipelineTest"
```
Expected: **FAIL — unresolved reference: CapturePipeline**.

- [ ] **Step 11.6** — Create `CapturePipeline.kt` with the R4 ctor. The auto-post `{ create linked txn ; mark candidate AUTO_POSTED }` runs inside one `db.transaction { }`; the txn is created already linked via `NewTransaction.captureId`; after a committed auto-post the pipeline emits `CaptureEvent.AutoPosted`:

```kotlin
package app.hisaab.capture

import app.hisaab.data.CaptureConfigRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.SenderRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.BankType
import app.hisaab.domain.CandidateTransaction
import app.hisaab.domain.CaptureStatus
import app.hisaab.domain.Category
import app.hisaab.domain.Direction
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.ParsedBy
import app.hisaab.domain.RawCapture
import app.hisaab.domain.SenderMapping
import app.hisaab.domain.TxnKind
import app.hisaab.domain.TxnSource
import app.hisaab.llm.LlmParseResult
import app.hisaab.llm.LlmProvider
import app.hisaab.llm.LlmRouter
import app.hisaab.llm.ParseRequest
import app.hisaab.llm.ProviderId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Orchestrates one captured message end-to-end:
 * dedup -> pre-filter -> extract (template, then LLM) -> resolve
 * account/merchant/category -> score -> route (auto-post vs PENDING).
 *
 * Depends on M3-1 repos + the LLM interfaces + the HisaabDatabase handle (used
 * ONLY to make the auto-post path atomic, per spec §7 step 8) + a
 * MutableSharedFlow<CaptureEvent> it emits AutoPosted into. Idempotent by
 * construction: the dedup_hash unique index makes a re-delivered message a
 * no-op.
 *
 * Auto-post atomicity: { create linked txn ; mark candidate AUTO_POSTED } runs
 * inside one db.transaction { } (same pattern TransactionRepository uses for
 * split parent+children). The txn is created ALREADY LINKED via
 * NewTransaction.captureId — there is no separate link step.
 */
class CapturePipeline(
    private val db: HisaabDatabase,
    private val inboxRepo: CaptureInboxRepository,
    private val senderRepo: SenderRepository,
    private val accountMatcher: AccountMatcher,
    private val preFilter: SmsPreFilter,
    private val llmRouter: LlmRouter,
    private val txnRepo: TransactionRepository,
    private val configRepo: CaptureConfigRepository,
    private val captureEvents: MutableSharedFlow<CaptureEvent>,
) {

    suspend fun process(raw: RawCapture) {
        val dedupHash = dedupHash(raw.sender, raw.body)
        // 1. Dedup
        if (inboxRepo.findByDedupHash(dedupHash) != null) return

        // 2. Pre-filter
        val mapping = senderRepo.findBySenderId(raw.sender)
        when (val verdict = preFilter.classify(raw, mapping)) {
            is PreFilterResult.NotFinancial -> return
            is PreFilterResult.KnownTemplate -> extractKnown(raw, dedupHash, verdict.mapping, verdict.templateKey)
            is PreFilterResult.UnknownFinancial -> extractUnknown(raw, dedupHash, verdict.mapping)
        }
    }

    // ---- Known-template path (template first, LLM for gaps) ----

    private suspend fun extractKnown(
        raw: RawCapture,
        dedupHash: String,
        mapping: SenderMapping,
        templateKey: String,
    ) {
        val template = BankTemplates.forKey(templateKey)
        val normalized = BanglaNumerals.normalize(raw.body)
        val extraction = template?.extract(normalized)

        if (extraction != null && extraction.complete) {
            finalize(
                raw = raw,
                dedupHash = dedupHash,
                mapping = mapping,
                amount = extraction.amount,
                direction = extraction.direction,
                merchant = extraction.merchant,
                refNo = extraction.refNo,
                balanceAfter = extraction.balanceAfter,
                parsedBy = ParsedBy.TEMPLATE,
                model = null,
                llmConfidence = null,
                templateComplete = true,
                parseError = null,
            )
            return
        }
        // Partial template -> fall through to LLM (or PENDING if none).
        extractUnknown(raw, dedupHash, mapping, templatePartial = extraction)
    }

    // ---- Unknown / partial path (LLM, else PENDING) ----

    private suspend fun extractUnknown(
        raw: RawCapture,
        dedupHash: String,
        mapping: SenderMapping?,
        templatePartial: TemplateExtraction? = null,
    ) {
        val provider = llmRouter.active()
        if (provider == null) {
            savePending(
                raw = raw,
                dedupHash = dedupHash,
                amount = templatePartial?.amount,
                direction = templatePartial?.direction,
                merchant = templatePartial?.merchant,
                refNo = templatePartial?.refNo,
                balanceAfter = templatePartial?.balanceAfter,
                parsedBy = null,
                model = null,
                parseError = "needs_manual",
            )
            return
        }

        val result = runLlm(provider, raw, mapping)
        if (result == null || !result.isFinancial) {
            savePending(
                raw = raw,
                dedupHash = dedupHash,
                amount = templatePartial?.amount,
                direction = templatePartial?.direction,
                merchant = templatePartial?.merchant,
                refNo = templatePartial?.refNo,
                balanceAfter = templatePartial?.balanceAfter,
                parsedBy = null,
                model = null,
                parseError = "llm_error",
            )
            return
        }

        finalize(
            raw = raw,
            dedupHash = dedupHash,
            mapping = mapping,
            amount = result.amount ?: templatePartial?.amount,
            direction = result.direction ?: templatePartial?.direction,
            merchant = result.merchant ?: templatePartial?.merchant,
            refNo = result.refNo ?: templatePartial?.refNo,
            balanceAfter = result.balanceAfter ?: templatePartial?.balanceAfter,
            parsedBy = parsedByFor(provider.id),
            model = provider.id.name,
            llmConfidence = result.confidence,
            templateComplete = false,
            parseError = null,
            llmCategoryId = result.categoryId,
        )
    }

    private suspend fun runLlm(
        provider: LlmProvider,
        raw: RawCapture,
        mapping: SenderMapping?,
    ): LlmParseResult? = runCatching {
        provider.parse(
            ParseRequest(
                text = raw.body,
                senderHint = mapping?.senderId ?: raw.sender,
                categories = defaultCategories(),
            ),
        )
    }.getOrNull()

    // ---- Shared finalize: resolve fields, score, route, post ----

    private suspend fun finalize(
        raw: RawCapture,
        dedupHash: String,
        mapping: SenderMapping?,
        amount: Double?,
        direction: Direction?,
        merchant: String?,
        refNo: String?,
        balanceAfter: Double?,
        parsedBy: ParsedBy,
        model: String?,
        llmConfidence: Double?,
        templateComplete: Boolean,
        parseError: String?,
        llmCategoryId: String? = null,
    ) {
        // Sanity: amount must be positive and direction known to even consider posting.
        val sane = amount != null && amount > 0.0 && direction != null
        val accountId = accountMatcher.resolve(mapping, mapping?.bankType ?: BankType.OTHER)
        val categoryId = resolveCategory(llmCategoryId, merchant)
        val fieldsResolved = sane && accountId != null

        val confidence = ConfidenceScorer.score(
            templateComplete = templateComplete,
            fieldsResolved = fieldsResolved,
            llmConfidence = llmConfidence,
            senderKnown = mapping != null,
        )

        val config = configRepo.get()
        val now = Clock.System.now().toEpochMilliseconds()
        val candidateId = randomId()
        val canAutoPost = !config.alwaysReview &&
            fieldsResolved &&
            confidence >= config.autoPostThreshold

        if (canAutoPost) {
            // Non-null after the fieldsResolved gate above.
            val resolvedAccountId = accountId!!
            val resolvedAmount = amount!!
            val resolvedDirection = direction!!
            val candidate = candidate(
                id = candidateId,
                raw = raw,
                dedupHash = dedupHash,
                status = CaptureStatus.AUTO_POSTED,
                confidence = confidence,
                parsedBy = parsedBy,
                model = model,
                parseError = parseError,
                amount = resolvedAmount,
                direction = resolvedDirection,
                balanceAfter = balanceAfter,
                refNo = refNo,
                accountId = resolvedAccountId,
                categoryId = categoryId,
                merchant = merchant,
                now = now,
            )

            // R2: one DB transaction — create the ALREADY-LINKED txn (R1) and mark
            // the candidate AUTO_POSTED together, so neither can exist without the
            // other. Mirrors TransactionRepository's split parent+children pattern.
            var txnId = ""
            db.transaction {
                txnId = txnRepo.addBlocking(
                    NewTransaction(
                        accountId = resolvedAccountId,
                        amount = resolvedAmount,
                        ts = raw.receivedAt,
                        merchantName = merchant,
                        categoryId = categoryId,
                        source = TxnSource.SMS,
                        notes = null,
                        kind = kindFor(resolvedDirection),
                        captureId = candidateId,
                    ),
                )
                inboxRepo.insertCandidateBlocking(candidate)
            }

            captureEvents.emit(
                CaptureEvent.AutoPosted(
                    txnId = txnId,
                    candidateId = candidateId,
                    amount = resolvedAmount,
                    sender = raw.sender,
                    direction = resolvedDirection,
                ),
            )
        } else {
            inboxRepo.insertCandidate(
                candidate(
                    id = candidateId,
                    raw = raw,
                    dedupHash = dedupHash,
                    status = CaptureStatus.PENDING,
                    confidence = confidence,
                    parsedBy = parsedBy,
                    model = model,
                    parseError = parseError,
                    amount = amount,
                    direction = direction,
                    balanceAfter = balanceAfter,
                    refNo = refNo,
                    accountId = accountId,
                    categoryId = categoryId,
                    merchant = merchant,
                    now = now,
                ),
            )
        }
    }

    private suspend fun savePending(
        raw: RawCapture,
        dedupHash: String,
        amount: Double?,
        direction: Direction?,
        merchant: String?,
        refNo: String?,
        balanceAfter: Double?,
        parsedBy: ParsedBy?,
        model: String?,
        parseError: String,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        inboxRepo.insertCandidate(
            candidate(
                id = randomId(),
                raw = raw,
                dedupHash = dedupHash,
                status = CaptureStatus.PENDING,
                confidence = null,
                parsedBy = parsedBy,
                model = model,
                parseError = parseError,
                amount = amount,
                direction = direction,
                balanceAfter = balanceAfter,
                refNo = refNo,
                accountId = null,
                categoryId = null,
                merchant = merchant,
                now = now,
            ),
        )
    }

    // ---- helpers ----

    private fun resolveCategory(llmCategoryId: String?, merchant: String?): String? {
        val valid = DEFAULT_CATEGORY_IDS.toSet()
        llmCategoryId?.takeIf { it in valid }?.let { return it }
        // Template fast-path rule map (no LLM): a few obvious merchants -> category.
        val m = merchant?.lowercase() ?: return null
        return when {
            m.contains("shwapno") || m.contains("agora") || m.contains("daraz") -> "shopping"
            else -> null
        }
    }

    private fun defaultCategories(): List<Category> = DEFAULT_CATEGORY_IDS.map {
        Category(id = it, name = it, parentId = null, color = null, icon = null, isDefault = true)
    }

    private fun kindFor(direction: Direction): TxnKind = when (direction) {
        Direction.DEBIT -> TxnKind.EXPENSE
        Direction.CREDIT -> TxnKind.INCOME
    }

    private fun parsedByFor(id: ProviderId): ParsedBy = when (id) {
        ProviderId.ON_DEVICE -> ParsedBy.ON_DEVICE
        ProviderId.CLOUD_CLAUDE -> ParsedBy.CLOUD_CLAUDE
        ProviderId.CLOUD_GEMINI -> ParsedBy.CLOUD_GEMINI
        ProviderId.CLOUD_OPENAI -> ParsedBy.CLOUD_OPENAI
    }

    @Suppress("LongParameterList")
    private fun candidate(
        id: String,
        raw: RawCapture,
        dedupHash: String,
        status: CaptureStatus,
        confidence: Double?,
        parsedBy: ParsedBy?,
        model: String?,
        parseError: String?,
        amount: Double?,
        direction: Direction?,
        balanceAfter: Double?,
        refNo: String?,
        accountId: String?,
        categoryId: String?,
        merchant: String?,
        now: Long,
    ): CandidateTransaction = CandidateTransaction(
        id = id,
        receivedAt = raw.receivedAt,
        channel = raw.channel,
        sender = raw.sender,
        rawBody = raw.body,
        dedupHash = dedupHash,
        status = status,
        confidence = confidence,
        parsedBy = parsedBy,
        model = model,
        parseError = parseError,
        amount = amount,
        direction = direction,
        currency = "BDT",
        balanceAfter = balanceAfter,
        refNo = refNo,
        proposedAccountId = accountId,
        proposedCategoryId = categoryId,
        proposedMerchant = merchant,
        createdAt = now,
    )

    private fun dedupHash(sender: String, body: String): String {
        val normalized = sender.trim().lowercase() + "|" + body.trim()
        return Sha256.hexOf(normalized)
    }

    private fun randomId(): String {
        val bytes = Random.Default.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    private companion object {
        val DEFAULT_CATEGORY_IDS = listOf(
            "food", "transport", "bills", "salary", "lend", "borrow",
            "health", "education", "shopping", "entertainment", "other", "transfer",
        )
    }
}
```

> **Repo methods used inside the transaction:** `db.transaction { }` runs synchronously, so the two writes inside it use the repos' non-suspending transaction-body forms: `TransactionRepository.addBlocking(NewTransaction)` (the synchronous core of `add`, already used by P0c when composing parent+children inside a transaction) and `CaptureInboxRepository.insertCandidateBlocking(candidate)` (the synchronous core of `insertCandidate`). Both are M3-1/P0c-owned operations that issue a single SQLDelight statement each; calling them inside an enclosing `db.transaction { }` joins them into one atomic unit. Outside the transaction (the PENDING / parse-error paths) the pipeline keeps using the ordinary suspending `insertCandidate`. `txnRepo.add` already performs the merchant upsert from `NewTransaction.merchantName`, so no `merchantRepo` is needed (R4).

- [ ] **Step 11.7** — Run the pipeline test; expect PASS:

```bash
./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.capture.CapturePipelineTest"
```
Expected: **PASS (7 tests)**, including the auto-post test's three R-fix assertions (AutoPosted emitted, `txn.captureId == candidate.id`, exactly one txn alongside the AUTO_POSTED candidate). Debug iteratively if a route is wrong (verify `ConfidenceScorer` thresholds vs the default `autoPostThreshold=0.85`, and that the in-memory `CaptureConfigRepository.get()` returns defaults).

- [ ] **Step 11.8** — Commit:

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/capture/CapturePipeline.kt \
        composeApp/src/commonMain/kotlin/app/hisaab/capture/Sha256.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/capture/CapturePipelineTest.kt \
        composeApp/src/commonTest/kotlin/app/hisaab/capture/Sha256Test.kt
git commit -m "feat: add CapturePipeline orchestrator (atomic linked auto-post + AutoPosted event) with full routing tests"
```

---

### Task 12: Wire LlmRouter + CapturePipeline + captureEvents into AppContainer (all targets)

**Files:**
- Modify `composeApp/src/commonMain/kotlin/app/hisaab/AppContainer.kt` (add three getters after `insightRepository`, ~line 61)
- Modify `composeApp/src/androidMain/kotlin/app/hisaab/AppContainerAndroid.kt`
- Modify `composeApp/src/iosMain/kotlin/app/hisaab/AppContainerIos.kt`
- Modify `composeApp/src/wasmJsMain/kotlin/app/hisaab/AppContainerWasm.kt`

> M3-1 already exposes `captureInboxRepository`, `senderRepository`, `captureConfigRepository` on `AppContainer`. This task assumes those getters exist (they are M3-1's responsibility). It adds `llmRouter`, `capturePipeline`, and `captureEvents`. Per R3, `captureEvents` is a `SharedFlow<CaptureEvent>` exposed publicly, backed by a private `MutableSharedFlow<CaptureEvent>(extraBufferCapacity = 16)` that is passed into the pipeline's `captureEvents` ctor argument so the pipeline emits into the very flow M3-5 collects. The backing flow and `llmRouter` are stable singletons; `capturePipeline` is recomputed per access like the repos (so it always binds the live DB) but always passes the same singleton flow.

- [ ] **Step 12.1** — In `AppContainer.kt` (commonMain expect), add the three getters to the `expect class AppContainer` body, right after the `insightRepository` declaration:

```kotlin
    val insightRepository: InsightRepository

    // M3-3: tiered parsing pipeline + LLM router contract + auto-post event stream.
    val llmRouter: app.hisaab.llm.LlmRouter
    val capturePipeline: app.hisaab.capture.CapturePipeline
    val captureEvents: kotlinx.coroutines.flow.SharedFlow<app.hisaab.capture.CaptureEvent>
```

- [ ] **Step 12.2** — In `AppContainerAndroid.kt`, add the actuals right after the `insightRepository` getter (~line 82). The `MutableSharedFlow` backing field and `llmRouter` are stable singletons; `capturePipeline` is recomputed per access (binds the live DB) and receives the live DB handle + the singleton event flow:

```kotlin
    actual val insightRepository: InsightRepository
        get() = InsightRepository(requireDb())

    // M3-3: NoOpLlmRouter ships now; M3-4 replaces with DefaultLlmRouter.
    actual val llmRouter: app.hisaab.llm.LlmRouter = app.hisaab.llm.NoOpLlmRouter()

    // M3-3: backing flow the pipeline emits AutoPosted into; M3-5 collects captureEvents.
    private val captureEventsFlow =
        kotlinx.coroutines.flow.MutableSharedFlow<app.hisaab.capture.CaptureEvent>(extraBufferCapacity = 16)
    actual val captureEvents: kotlinx.coroutines.flow.SharedFlow<app.hisaab.capture.CaptureEvent> =
        captureEventsFlow

    actual val capturePipeline: app.hisaab.capture.CapturePipeline
        get() = app.hisaab.capture.CapturePipeline(
            db = requireDb(),
            inboxRepo = captureInboxRepository,
            senderRepo = senderRepository,
            accountMatcher = app.hisaab.capture.AccountMatcher(accountRepository, senderRepository),
            preFilter = app.hisaab.capture.SmsPreFilter(senderRepository),
            llmRouter = llmRouter,
            txnRepo = transactionRepository,
            configRepo = captureConfigRepository,
            captureEvents = captureEventsFlow,
        )
```

- [ ] **Step 12.3** — In `AppContainerIos.kt`, add the identical actual block after `insightRepository` (~line 77):

```kotlin
    actual val insightRepository: InsightRepository
        get() = InsightRepository(requireDb())

    // M3-3: NoOpLlmRouter ships now; M3-4 replaces with DefaultLlmRouter.
    actual val llmRouter: app.hisaab.llm.LlmRouter = app.hisaab.llm.NoOpLlmRouter()

    // M3-3: backing flow the pipeline emits AutoPosted into; M3-5 collects captureEvents.
    private val captureEventsFlow =
        kotlinx.coroutines.flow.MutableSharedFlow<app.hisaab.capture.CaptureEvent>(extraBufferCapacity = 16)
    actual val captureEvents: kotlinx.coroutines.flow.SharedFlow<app.hisaab.capture.CaptureEvent> =
        captureEventsFlow

    actual val capturePipeline: app.hisaab.capture.CapturePipeline
        get() = app.hisaab.capture.CapturePipeline(
            db = requireDb(),
            inboxRepo = captureInboxRepository,
            senderRepo = senderRepository,
            accountMatcher = app.hisaab.capture.AccountMatcher(accountRepository, senderRepository),
            preFilter = app.hisaab.capture.SmsPreFilter(senderRepository),
            llmRouter = llmRouter,
            txnRepo = transactionRepository,
            configRepo = captureConfigRepository,
            captureEvents = captureEventsFlow,
        )
```

- [ ] **Step 12.4** — In `AppContainerWasm.kt`, add the identical actual block after `insightRepository` (~line 77):

```kotlin
    actual val insightRepository: InsightRepository
        get() = InsightRepository(requireDb())

    // M3-3: NoOpLlmRouter ships now; M3-4 replaces with DefaultLlmRouter.
    actual val llmRouter: app.hisaab.llm.LlmRouter = app.hisaab.llm.NoOpLlmRouter()

    // M3-3: backing flow the pipeline emits AutoPosted into; M3-5 collects captureEvents.
    private val captureEventsFlow =
        kotlinx.coroutines.flow.MutableSharedFlow<app.hisaab.capture.CaptureEvent>(extraBufferCapacity = 16)
    actual val captureEvents: kotlinx.coroutines.flow.SharedFlow<app.hisaab.capture.CaptureEvent> =
        captureEventsFlow

    actual val capturePipeline: app.hisaab.capture.CapturePipeline
        get() = app.hisaab.capture.CapturePipeline(
            db = requireDb(),
            inboxRepo = captureInboxRepository,
            senderRepo = senderRepository,
            accountMatcher = app.hisaab.capture.AccountMatcher(accountRepository, senderRepository),
            preFilter = app.hisaab.capture.SmsPreFilter(senderRepository),
            llmRouter = llmRouter,
            txnRepo = transactionRepository,
            configRepo = captureConfigRepository,
            captureEvents = captureEventsFlow,
        )
```

- [ ] **Step 12.5** — Compile all targets to confirm the expect/actual match across platforms:

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosArm64 :composeApp:compileKotlinWasmJs
```
Expected: **BUILD SUCCESSFUL**. (If the iOS/wasm tasks are named differently in this project, use `./gradlew :composeApp:assembleDebug` plus `./gradlew :composeApp:compileKotlinMetadata` to cover commonMain expect/actual.)

- [ ] **Step 12.6** — Commit:

```bash
git add composeApp/src/commonMain/kotlin/app/hisaab/AppContainer.kt \
        composeApp/src/androidMain/kotlin/app/hisaab/AppContainerAndroid.kt \
        composeApp/src/iosMain/kotlin/app/hisaab/AppContainerIos.kt \
        composeApp/src/wasmJsMain/kotlin/app/hisaab/AppContainerWasm.kt
git commit -m "feat: expose llmRouter (NoOp) + capturePipeline + captureEvents on AppContainer"
```

---

### Task 13: Wire the real CapturePipeline into M3-2's CaptureCoordinator via the CaptureHandler seam

**Files:**
- Modify `composeApp/src/androidMain/kotlin/app/hisaab/AppContainerAndroid.kt` (the place where M3-2 constructs/exposes `CaptureCoordinator`)

> Definite contract (resolving the prior "if M3-2 wires" hedge): M3-2 ships `CaptureCoordinator(captureService, handler: CaptureHandler, configRepo)` where `CaptureHandler` is the seam `fun interface CaptureHandler { suspend fun handle(raw: RawCapture) }`. M3-2 constructs the coordinator with a no-op handler placeholder. M3-3 owns replacing that placeholder with the real pipeline: the Android `captureCoordinator` getter must pass `handler = app.hisaab.capture.CaptureHandler { capturePipeline.process(it) }`.

- [ ] **Step 13.1** — Inspect how M3-2 exposes the coordinator and its handler seam:

```bash
grep -rn "CaptureCoordinator\|CaptureHandler\|captureCoordinator\|captureService" composeApp/src/androidMain/kotlin/app/hisaab/AppContainerAndroid.kt composeApp/src/commonMain/kotlin/app/hisaab/AppContainer.kt composeApp/src/commonMain/kotlin/app/hisaab/capture/CaptureCoordinator.kt
```
Expected: a `captureCoordinator` getter, a `captureService` field, and the `CaptureHandler` fun interface — all from M3-2.

- [ ] **Step 13.2** — Edit the existing Android `captureCoordinator` getter so its `handler` argument is the real pipeline (do NOT duplicate the getter — edit M3-2's existing one):

```kotlin
    actual val captureCoordinator: app.hisaab.capture.CaptureCoordinator
        get() = app.hisaab.capture.CaptureCoordinator(
            captureService = captureService,
            handler = app.hisaab.capture.CaptureHandler { capturePipeline.process(it) },
            configRepo = captureConfigRepository,
        )
```

- [ ] **Step 13.3** — Compile Android to confirm the coordinator binds the real pipeline through the handler seam:

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```
Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 13.4** — Commit:

```bash
git add composeApp/src/androidMain/kotlin/app/hisaab/AppContainerAndroid.kt
git commit -m "feat: wire real CapturePipeline into CaptureCoordinator via CaptureHandler seam"
```

---

### Task 14: Full verification

**Files:** none (verification only)

- [ ] **Step 14.1** — Run the entire unit test suite:

```bash
./gradlew :composeApp:testDebugUnitTest
```
Expected: **BUILD SUCCESSFUL** — all new tests (`NoOpLlmRouterTest`, `BanglaNumeralsTest`, `BankTemplateTest`, `SmsPreFilterTest`, `ConfidenceScorerTest`, `AccountMatcherTest`, `Sha256Test`, `CapturePipelineTest`) plus all pre-existing tests pass with no regressions.

- [ ] **Step 14.2** — Build the debug APK to confirm the full Android compile (including expect/actual wiring and CaptureCoordinator handler binding):

```bash
./gradlew :composeApp:assembleDebug
```
Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 14.3** — Confirm the working tree is clean and review the commit log for this slice:

```bash
git status --short
git log --oneline -13
```
Expected: clean tree; commits for LLM contract, BanglaNumerals, corpus, BankTemplate, SmsPreFilter, ConfidenceScorer, AccountMatcher, CaptureEvent, fakes, CapturePipeline+Sha256, AppContainer wiring, and CaptureCoordinator handler wiring.

- [ ] **Step 14.4** — Final sanity check against the slice scope: confirm each deliverable exists — `llm/ProviderId.kt`, `llm/ParseRequest.kt`, `llm/LlmParseResult.kt`, `llm/LlmProvider.kt`, `llm/LlmRouter.kt` (with `NoOpLlmRouter`), `capture/BanglaNumerals.kt`, `capture/PreFilterResult.kt`, `capture/SmsPreFilter.kt`, `capture/TemplateExtraction.kt`, `capture/BankTemplate.kt` (6 seeded templates), `capture/AccountMatcher.kt`, `capture/ConfidenceScorer.kt`, `capture/CaptureEvent.kt`, `capture/CapturePipeline.kt`, `capture/Sha256.kt`, and the `commonTest` corpus + 8 test files:

```bash
ls composeApp/src/commonMain/kotlin/app/hisaab/llm/ composeApp/src/commonMain/kotlin/app/hisaab/capture/ composeApp/src/commonTest/kotlin/app/hisaab/capture/ composeApp/src/commonTest/kotlin/app/hisaab/llm/
```
Expected: all listed files present.

- [ ] **Step 14.5** — Confirm the four review fixes hold by inspection — no `linkCapture`/`setCaptureId`/`onAutoPost`/`merchantRepo` ctor references remain, the auto-post is one transaction, and no `.sq` file was edited in this slice:

```bash
grep -rn "linkCapture\|setCaptureId\|onAutoPost\|merchantRepo" composeApp/src/commonMain/kotlin/app/hisaab/capture/ composeApp/src/androidMain/kotlin/app/hisaab/ composeApp/src/iosMain/kotlin/app/hisaab/ composeApp/src/wasmJsMain/kotlin/app/hisaab/ ; \
grep -n "db.transaction" composeApp/src/commonMain/kotlin/app/hisaab/capture/CapturePipeline.kt ; \
git diff --name-only HEAD~13..HEAD -- '*.sq'
```
Expected: the first grep returns NOTHING (R1/R3/R4 satisfied — no link methods, no callback, no dead merchantRepo); the second grep shows the single `db.transaction { }` wrapping the auto-post writes (R2 satisfied); the `git diff` over this slice's commits lists NO `.sq` files (M3-1 owns `NewTransaction.captureId` and the INSERT writing `capture_id`).
