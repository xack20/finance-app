---
# M3-4: LLM Providers (on-device + cloud BYO-key) + Redaction + Consent — Implementation Plan
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the real LLM provider layer — a pure PII `Redactor`, the extraction/categorize `Prompts`, three Ktor cloud adapters (Claude/Gemini/OpenAI) using each vendor's native structured-output mode decoding into one `LlmParseResult`, an Android on-device provider (MediaPipe Gemma + AICore/Gemini Nano availability), and `DefaultLlmRouter` — then wire `DefaultLlmRouter` into `AppContainer`, replacing the M3-3 `NoOpLlmRouter` so `CapturePipeline` uses real LLMs.

**Architecture:** All providers implement the M3-3 `LlmProvider` interface and produce the same `LlmParseResult` via one `kotlinx.serialization` schema. Cloud adapters live in `commonMain/llm/cloud/` over the existing Ktor `HttpClient`; each takes an `HttpClient` plus an `apiKey` provider (`() -> String?`), enforces a 15 s timeout, and maps HTTP errors (401 → invalid key, 429 → retryable, etc.) to a typed `LlmError`. The on-device provider is an `expect fun createOnDeviceProvider(): LlmProvider?` (real on Android via MediaPipe LLM Inference + `ModelManager`; `null` on iOS/wasm). `DefaultLlmRouter` reads `CaptureConfig` from `CaptureConfigRepository`, gates cloud usage behind recorded consent + a present API key in `SecureStorage` (`llm_api_key_<PROVIDER>`), and returns the active provider. `Redactor` always runs inside cloud `parse()` before the network call.

**Tech Stack:** Kotlin Multiplatform, Ktor client 3.0.3 (+ ContentNegotiation/JSON, MockEngine for tests), kotlinx.serialization, MediaPipe `tasks-genai` + Play Services AICore (androidMain), kotlin.test + kotlinx-coroutines-test + Turbine-free Flow `.first()` assertions, SQLDelight in-memory `JdbcSqliteDriver` for repo-backed tests.

**Depends on:** M3-1 (domain models, `CaptureConfigRepository`, `SecureStorage.loadString`/`storeString`), M3-3 (`llm/` interface contracts: `LlmProvider`, `LlmRouter`, `ProviderId`, `ParseRequest`, `LlmParseResult`, and the shipped `NoOpLlmRouter`). Both must be merged first; their contract types are assumed to exist verbatim.

---

## File Structure

### Build / dependencies
- `gradle/libs.versions.toml` — **Modify:** add `kotlinx-serialization` version + `ktor-client-core`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`, `ktor-client-mock`, `mediapipe-genai`, `play-services-aicore` library coordinates and the serialization plugin.
- `composeApp/build.gradle.kts` — **Modify:** apply `kotlin-serialization` plugin; add Ktor + serialization to `commonMain`, MockEngine to `commonTest`, MediaPipe/AICore to `androidMain`.

### commonMain — `app/hisaab/llm/`
- `llm/LlmError.kt` — **Create:** typed sealed error hierarchy for provider failures (invalid key, rate limited, network, decode, unavailable).
- `llm/Redactor.kt` — **Create:** `object Redactor { fun redact(text: String): String }` — pure PII masking (account numbers keep last 4, phone numbers, names → placeholders).
- `llm/Prompts.kt` — **Create:** `object Prompts { fun extractionSystem(categories): String ; fun categorize(merchant, categories): String }` — extraction system prompt constrained to the 12 category ids + BD few-shot + short on-device variant; categorize prompt.
- `llm/LlmJson.kt` — **Create:** shared `@Serializable LlmParseDto` + `Json` instance + DTO→`LlmParseResult` mapper with sanity validation, reused by all three cloud adapters.
- `llm/DefaultLlmRouter.kt` — **Create:** `class DefaultLlmRouter(configRepo, secureStorage, onDeviceProvider, claude, gemini, openai) : LlmRouter` — selects provider from `CaptureConfig` + consent gate.
- `llm/cloud/ClaudeProvider.kt` — **Create:** Ktor adapter, `tool_use` `input_schema` structured output.
- `llm/cloud/GeminiProvider.kt` — **Create:** Ktor adapter, `responseSchema` + `responseMimeType: application/json`.
- `llm/cloud/OpenAiProvider.kt` — **Create:** Ktor adapter, `response_format: json_schema`.
- `llm/CloudHttp.kt` — **Create:** shared `buildLlmHttpClient()` factory (15 s timeout + JSON ContentNegotiation) and `mapHttpError(status): LlmError`.
- `llm/OnDeviceProvider.kt` — **Create:** `expect fun createOnDeviceProvider(): LlmProvider?`.

### androidMain
- `llm/OnDeviceProvider.android.kt` — **Create:** `actual fun createOnDeviceProvider(): LlmProvider? = AndroidOnDeviceProvider(...)`.
- `llm/ModelManager.kt` — **Create:** availability check (AICore/Gemini Nano probe) + MediaPipe model-file resolution.
- `llm/AndroidOnDeviceProvider.kt` — **Create:** `LlmProvider` over MediaPipe `LlmInference` (Gemma path).

### iosMain / wasmJsMain
- `llm/OnDeviceProvider.ios.kt` — **Create:** `actual fun createOnDeviceProvider(): LlmProvider? = null`.
- `llm/OnDeviceProvider.wasmJs.kt` — **Create:** `actual fun createOnDeviceProvider(): LlmProvider? = null`.

### DI wiring
- `AppContainer.kt` — **Modify:** add `val llmRouter: LlmRouter` expect getter.
- `AppContainerAndroid.kt` — **Modify:** build `DefaultLlmRouter` with real `createOnDeviceProvider()` + three cloud providers (replacing the M3-3 `NoOpLlmRouter` wiring); pass it into `CapturePipeline`.
- `AppContainerIos.kt` / `AppContainerWasm.kt` — **Modify:** same `DefaultLlmRouter` wiring with `createOnDeviceProvider()` returning `null`.

### Tests
- `commonTest/llm/RedactorTest.kt` — **Create:** pure PII masking unit tests.
- `commonTest/llm/PromptsTest.kt` — **Create:** prompt-content unit tests (12 ids present, JSON instruction, few-shot, short variant).
- `commonTest/llm/LlmJsonTest.kt` — **Create:** DTO decode + sanity-validation unit tests.
- `commonTest/llm/cloud/ClaudeProviderTest.kt` — **Create:** MockEngine request-shape + response-decode + error-mapping.
- `commonTest/llm/cloud/GeminiProviderTest.kt` — **Create:** ditto for Gemini.
- `commonTest/llm/cloud/OpenAiProviderTest.kt` — **Create:** ditto for OpenAI.
- `commonTest/llm/DefaultLlmRouterTest.kt` — **Create:** provider selection + consent/key gating, using a fake `CaptureConfigRepository`-shaped seam and an in-memory `SecureStorage` double.
- `androidInstrumentedTest/llm/AndroidOnDeviceProviderTest.kt` — **Create:** on-device parse, skipped if model absent.

> **MODEL ID / SDK VERSION NOTE (confirm via Context7 at implementation time):** The plan uses concrete current best-guess values so every step is runnable. Confirm and pin these before merging:
> - Claude: `claude-3-5-haiku-latest` (Messages API `2023-06-01` anthropic-version header).
> - Gemini: `gemini-2.0-flash` (v1beta `generateContent`).
> - OpenAI: `gpt-4o-mini` (Chat Completions `response_format: json_schema`).
> - MediaPipe: `com.google.mediapipe:tasks-genai:0.10.24`, Gemma model file `gemma-3-1b-it-int4.task`.
> - AICore probe: `com.google.android.gms:play-services-aicore:16.0.0-alpha05` (presence check only; full Gemini Nano path is a follow-up — this slice only does the availability gate and falls through to MediaPipe).

---

### Task 1: Add serialization + Ktor + MediaPipe dependencies

**Files:**
- Modify: `/Users/xack/projects/finance-app/gradle/libs.versions.toml`
- Modify: `/Users/xack/projects/finance-app/composeApp/build.gradle.kts`

- [ ] **Step 1.1 — Add versions + libraries to the catalog.** Edit `gradle/libs.versions.toml`.

  Under `[versions]`, after the `ktor = "3.0.3"` line, add:
  ```toml
  kotlinx-serialization = "1.7.3"
  mediapipe-genai       = "0.10.24"
  play-services-aicore  = "16.0.0-alpha05"
  ```

  Under `[libraries]`, after the existing Ktor block (the `ktor-client-darwin` line), add:
  ```toml
  ktor-client-core                 = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
  ktor-client-content-negotiation  = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
  ktor-serialization-kotlinx-json  = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
  ktor-client-mock                 = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
  kotlinx-serialization-json       = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }

  # On-device LLM (Android)
  mediapipe-genai          = { module = "com.google.mediapipe:tasks-genai", version.ref = "mediapipe-genai" }
  play-services-aicore     = { module = "com.google.android.gms:play-services-aicore", version.ref = "play-services-aicore" }
  ```

  Under `[plugins]`, after the `sqldelight = ...` line, add:
  ```toml
  kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
  ```

- [ ] **Step 1.2 — Apply plugin + dependencies in the module build file.** Edit `/Users/xack/projects/finance-app/composeApp/build.gradle.kts`.

  In the `plugins { }` block, after `alias(libs.plugins.sqldelight)`, add:
  ```kotlin
  alias(libs.plugins.kotlin.serialization)
  ```

  In `commonMain.dependencies { }`, after `implementation(libs.nav.compose)`, add:
  ```kotlin
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
  ```

  In `commonTest.dependencies { }`, after `implementation(libs.sqldelight.sqlite.driver)`, add:
  ```kotlin
  implementation(libs.ktor.client.mock)
  ```

  In `androidMain.dependencies { }`, after `implementation(libs.androidx.biometric)`, add:
  ```kotlin
  implementation(libs.mediapipe.genai)
  implementation(libs.play.services.aicore)
  ```

- [ ] **Step 1.3 — Verify the build still resolves.** Run:
  ```bash
  ./gradlew :composeApp:testDebugUnitTest
  ```
  Expected: **PASS** (compiles and existing tests still green; new deps resolved). If MediaPipe/AICore coordinates fail to resolve, confirm exact artifact versions via Context7 and update the catalog before proceeding.

- [ ] **Step 1.4 — Commit.**
  ```bash
  git checkout -b m3-4-llm-providers
  git add gradle/libs.versions.toml composeApp/build.gradle.kts
  git commit -m "chore: add kotlinx-serialization, Ktor client core/json/mock, MediaPipe + AICore deps for M3-4"
  ```

---

### Task 2: `LlmError` typed error hierarchy

**Files:**
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonMain/kotlin/app/hisaab/llm/LlmError.kt`
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonTest/kotlin/app/hisaab/llm/LlmErrorTest.kt`

- [ ] **Step 2.1 — Write the failing test.** Create `LlmErrorTest.kt`:
  ```kotlin
  package app.hisaab.llm

  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertFalse
  import kotlin.test.assertTrue

  class LlmErrorTest {

      @Test
      fun `invalid key is not retryable`() {
          assertFalse(LlmError.InvalidKey.retryable)
      }

      @Test
      fun `rate limited is retryable`() {
          assertTrue(LlmError.RateLimited.retryable)
      }

      @Test
      fun `network error is retryable`() {
          assertTrue(LlmError.Network("boom").retryable)
      }

      @Test
      fun `message describes the failure`() {
          assertEquals("API key is invalid", LlmError.InvalidKey.message)
          assertTrue(LlmError.ProviderError(500, "down").message.contains("500"))
      }
  }
  ```

- [ ] **Step 2.2 — Run it (expect FAIL — `LlmError` does not exist yet).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.LlmErrorTest"
  ```
  Expected: **FAIL** (compilation: unresolved reference `LlmError`).

- [ ] **Step 2.3 — Create `LlmError.kt` (minimal implementation).**
  ```kotlin
  package app.hisaab.llm

  /**
   * Typed cloud/provider failure. Carried by [LlmException] so callers
   * (CapturePipeline via LlmRouter) can map to a candidate parse_error.
   */
  sealed class LlmError(val message: String, val retryable: Boolean) {
      data object InvalidKey : LlmError("API key is invalid", retryable = false)
      data object RateLimited : LlmError("Rate limited; will retry", retryable = true)
      data object Unavailable : LlmError("Provider unavailable", retryable = false)
      data class Network(val detail: String) : LlmError("Network error: $detail", retryable = true)
      data class ProviderError(val status: Int, val detail: String) :
          LlmError("Provider error ($status): $detail", retryable = status >= 500)
      data class Decode(val detail: String) : LlmError("Failed to decode response: $detail", retryable = false)
  }

  /** Thrown by providers on failure; [error] holds the typed cause. */
  class LlmException(val error: LlmError) : Exception(error.message)
  ```

- [ ] **Step 2.4 — Run it (expect PASS).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.LlmErrorTest"
  ```
  Expected: **PASS**.

- [ ] **Step 2.5 — Commit.**
  ```bash
  git add composeApp/src/commonMain/kotlin/app/hisaab/llm/LlmError.kt composeApp/src/commonTest/kotlin/app/hisaab/llm/LlmErrorTest.kt
  git commit -m "feat: add LlmError typed failure hierarchy for LLM providers"
  ```

---

### Task 3: `Redactor` — pure PII masking

**Files:**
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonMain/kotlin/app/hisaab/llm/Redactor.kt`
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonTest/kotlin/app/hisaab/llm/RedactorTest.kt`

- [ ] **Step 3.1 — Write the failing test.** Create `RedactorTest.kt`:
  ```kotlin
  package app.hisaab.llm

  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertFalse
  import kotlin.test.assertTrue

  class RedactorTest {

      @Test
      fun `masks account number keeping last 4`() {
          val out = Redactor.redact("Payment to A/C 1234567890 successful")
          assertTrue(out.contains("[ACCT *7890]"), out)
          assertFalse(out.contains("1234567890"))
      }

      @Test
      fun `masks bd phone number keeping last 4`() {
          val out = Redactor.redact("Sent to 01712345678 from bKash")
          assertTrue(out.contains("[PHONE *5678]"), out)
          assertFalse(out.contains("01712345678"))
      }

      @Test
      fun `preserves amount and currency markers`() {
          val out = Redactor.redact("You have received Tk 1,500.00 from 01712345678")
          assertTrue(out.contains("Tk 1,500.00"), out)
          assertTrue(out.contains("[PHONE *5678]"), out)
      }

      @Test
      fun `masks capitalised name tokens to NAME placeholder`() {
          // "Mr Rahim Uddin" — two-word capitalised proper-name run after a title.
          val out = Redactor.redact("Cash out to Mr Rahim Uddin agent")
          assertTrue(out.contains("[NAME]"), out)
          assertFalse(out.contains("Rahim Uddin"), out)
      }

      @Test
      fun `does not mask known financial keywords`() {
          val out = Redactor.redact("bKash Payment TrxID 9AB12CD34")
          assertTrue(out.contains("TrxID 9AB12CD34"), out)
          assertTrue(out.contains("bKash"), out)
      }

      @Test
      fun `is idempotent`() {
          val once = Redactor.redact("Sent to 01712345678 A/C 1234567890")
          val twice = Redactor.redact(once)
          assertEquals(once, twice)
      }

      @Test
      fun `short digit runs like trx amounts are not treated as accounts`() {
          val out = Redactor.redact("Fee Tk 5.00 ref 123")
          assertTrue(out.contains("ref 123"), out)
          assertFalse(out.contains("[ACCT"), out)
      }
  }
  ```

- [ ] **Step 3.2 — Run it (expect FAIL — `Redactor` missing).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.RedactorTest"
  ```
  Expected: **FAIL** (unresolved reference `Redactor`).

- [ ] **Step 3.3 — Create `Redactor.kt`.**
  ```kotlin
  package app.hisaab.llm

  /**
   * Pure PII masking applied before any cloud LLM call (when redaction is enabled).
   *
   * Masks, in order:
   *  - BD phone numbers (01XXXXXXXXX / +88 01XXXXXXXXX) -> [PHONE *NNNN]
   *  - account numbers (digit runs >= 8, optionally after A/C) -> [ACCT *NNNN]
   *  - capitalised proper-name runs after a title (Mr/Mrs/Md/Mst) -> [NAME]
   *
   * Deliberately preserves amounts (with currency markers Tk/৳/BDT), TrxID/refs,
   * and merchant words. Honest limitation: amount + merchant still leave the device.
   *
   * Idempotent: re-running over already-masked text is a no-op (placeholders contain
   * no maskable digit/name runs).
   */
  object Redactor {

      private const val ACCT_MIN_DIGITS = 8

      // +88 01XXXXXXXXX or 01XXXXXXXXX (BD mobile, 11 digits starting 01).
      private val phoneRegex = Regex("""(?:\+?88)?0?1[3-9]\d{8}""")

      // Any run of >= 8 digits (account numbers, card numbers); after phones are masked.
      private val acctRegex = Regex("""\d{$ACCT_MIN_DIGITS,}""")

      // Title + one or two Capitalised words (proper name run).
      private val nameRegex =
          Regex("""\b(?:Mr|Mrs|Ms|Md|Mst|Mister|Miss)\.?\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+)?""")

      fun redact(text: String): String {
          var out = text

          out = phoneRegex.replace(out) { m ->
              val digits = m.value.filter { it.isDigit() }
              "[PHONE *${digits.takeLast(4)}]"
          }

          out = acctRegex.replace(out) { m ->
              "[ACCT *${m.value.takeLast(4)}]"
          }

          out = nameRegex.replace(out) { _ -> "[NAME]" }

          return out
      }
  }
  ```

- [ ] **Step 3.4 — Run it (expect PASS).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.RedactorTest"
  ```
  Expected: **PASS**. (Note: the phone regex runs before the account regex, so an 11-digit BD mobile is masked as `[PHONE …]`, never as `[ACCT …]`.)

- [ ] **Step 3.5 — Commit.**
  ```bash
  git add composeApp/src/commonMain/kotlin/app/hisaab/llm/Redactor.kt composeApp/src/commonTest/kotlin/app/hisaab/llm/RedactorTest.kt
  git commit -m "feat: add pure Redactor PII masking (phone, account last-4, names)"
  ```

---

### Task 4: `Prompts` — extraction + categorize prompts

**Files:**
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonMain/kotlin/app/hisaab/llm/Prompts.kt`
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonTest/kotlin/app/hisaab/llm/PromptsTest.kt`

The 12 seeded category ids are fixed (from `CategoryRepository`): `food, transport, bills, salary, lend, borrow, health, education, shopping, entertainment, other, transfer`.

- [ ] **Step 4.1 — Write the failing test.** Create `PromptsTest.kt`:
  ```kotlin
  package app.hisaab.llm

  import app.hisaab.domain.Category
  import kotlin.test.Test
  import kotlin.test.assertTrue

  class PromptsTest {

      private val categories = listOf(
          cat("food", "Food & dining"),
          cat("transport", "Transport"),
          cat("bills", "Bills"),
          cat("salary", "Salary"),
          cat("other", "Other"),
      )

      private fun cat(id: String, name: String) =
          Category(id = id, name = name, parentId = null, color = null, icon = null, isDefault = true)

      @Test
      fun `extraction prompt lists every category id`() {
          val p = Prompts.extractionSystem(categories)
          categories.forEach { assertTrue(p.contains("\"${it.id}\""), "missing id ${it.id}") }
      }

      @Test
      fun `extraction prompt instructs strict json and isFinancial reject path`() {
          val p = Prompts.extractionSystem(categories)
          assertTrue(p.contains("JSON"))
          assertTrue(p.contains("isFinancial"))
          assertTrue(p.contains("confidence"))
      }

      @Test
      fun `extraction prompt contains a BD few-shot example`() {
          val p = Prompts.extractionSystem(categories)
          assertTrue(p.contains("bKash") || p.contains("Tk") || p.contains("৳"), "no BD few-shot")
      }

      @Test
      fun `on-device short variant is shorter than full and still names ids`() {
          val full = Prompts.extractionSystem(categories)
          val short = Prompts.extractionSystemShort(categories)
          assertTrue(short.length < full.length, "short not shorter")
          assertTrue(short.contains("\"food\""))
          assertTrue(short.contains("JSON"))
      }

      @Test
      fun `categorize prompt names merchant and constrains to ids`() {
          val p = Prompts.categorize("Aarong", categories)
          assertTrue(p.contains("Aarong"))
          assertTrue(p.contains("\"food\""))
      }
  }
  ```

- [ ] **Step 4.2 — Run it (expect FAIL — `Prompts` missing).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.PromptsTest"
  ```
  Expected: **FAIL** (unresolved reference `Prompts`).

- [ ] **Step 4.3 — Create `Prompts.kt`.**
  ```kotlin
  package app.hisaab.llm

  import app.hisaab.domain.Category

  /**
   * The single extraction system prompt (full + on-device short variant) and the
   * merchant categorize prompt. All variants constrain category output to exactly
   * the provided category ids (the 12 seeded ids) and require strict JSON.
   */
  object Prompts {

      private fun idList(categories: List<Category>): String =
          categories.joinToString(", ") { "\"${it.id}\" (${it.name})" }

      private fun idArray(categories: List<Category>): String =
          categories.joinToString(", ") { "\"${it.id}\"" }

      /** Full extraction system prompt with BD few-shot. */
      fun extractionSystem(categories: List<Category>): String = buildString {
          appendLine("You extract structured fields from a single Bangladeshi bank/MFS SMS.")
          appendLine("Reply with ONE JSON object and nothing else. No markdown, no prose.")
          appendLine()
          appendLine("Schema (all keys required; use null when unknown):")
          appendLine("""{"amount": number|null, "direction": "DEBIT"|"CREDIT"|null,""")
          appendLine(""" "merchant": string|null, "categoryId": string|null,""")
          appendLine(""" "balanceAfter": number|null, "refNo": string|null,""")
          appendLine(""" "confidence": number, "isFinancial": boolean}""")
          appendLine()
          appendLine("Rules:")
          appendLine("- If the message is not a financial transaction (promo/OTP/balance-only), set isFinancial=false and all other fields null except confidence.")
          appendLine("- direction: money leaving the user = DEBIT, money arriving = CREDIT.")
          appendLine("- categoryId MUST be exactly one of: ${idList(categories)}. Use null if unsure.")
          appendLine("- confidence is your 0..1 self-estimate that the extraction is correct.")
          appendLine("- Amounts may use Tk, ৳ or BDT and Bangla digits; output a plain number.")
          appendLine()
          appendLine("Examples:")
          appendLine("SMS: \"You have received Tk 1,500.00 from 017XXXXXXXX. Fee Tk 0.00. Balance Tk 3,210.50. TrxID 9AB12CD34\"")
          appendLine("""JSON: {"amount":1500.0,"direction":"CREDIT","merchant":null,"categoryId":null,"balanceAfter":3210.5,"refNo":"9AB12CD34","confidence":0.95,"isFinancial":true}""")
          appendLine("SMS: \"Payment Tk 320 to SHWAPNO successful via bKash. Balance Tk 1,008.\"")
          appendLine("""JSON: {"amount":320.0,"direction":"DEBIT","merchant":"Shwapno","categoryId":"food","balanceAfter":1008.0,"refNo":null,"confidence":0.9,"isFinancial":true}""")
          appendLine("SMS: \"Get 20% cashback this Eid! Recharge now with bKash.\"")
          appendLine("""JSON: {"amount":null,"direction":null,"merchant":null,"categoryId":null,"balanceAfter":null,"refNo":null,"confidence":0.99,"isFinancial":false}""")
      }

      /** Shorter variant for the on-device model (token-constrained). */
      fun extractionSystemShort(categories: List<Category>): String = buildString {
          appendLine("Extract fields from a Bangladeshi bank/MFS SMS. Reply with ONE JSON object only.")
          appendLine("""Keys: amount(number|null), direction("DEBIT"|"CREDIT"|null), merchant(string|null),""")
          appendLine(""" categoryId(one of [${idArray(categories)}] or null), balanceAfter(number|null),""")
          appendLine(""" refNo(string|null), confidence(0..1 number), isFinancial(boolean).""")
          appendLine("Promo/OTP/non-money -> isFinancial=false, others null. DEBIT=money out, CREDIT=money in.")
      }

      /** Merchant -> categoryId prompt for the template-only categorize fast path. */
      fun categorize(merchant: String, categories: List<Category>): String = buildString {
          appendLine("Pick the single best category id for this merchant.")
          appendLine("Merchant: \"$merchant\"")
          appendLine("Allowed ids: [${idArray(categories)}].")
          appendLine("Reply with ONE JSON object: {\"categoryId\": string|null}. Use null if none fit.")
      }
  }
  ```

- [ ] **Step 4.4 — Run it (expect PASS).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.PromptsTest"
  ```
  Expected: **PASS**.

- [ ] **Step 4.5 — Commit.**
  ```bash
  git add composeApp/src/commonMain/kotlin/app/hisaab/llm/Prompts.kt composeApp/src/commonTest/kotlin/app/hisaab/llm/PromptsTest.kt
  git commit -m "feat: add Prompts (extraction full + on-device short + categorize) constrained to 12 category ids"
  ```

---

### Task 5: `LlmJson` — shared DTO + decode + sanity validation

**Files:**
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonMain/kotlin/app/hisaab/llm/LlmJson.kt`
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonTest/kotlin/app/hisaab/llm/LlmJsonTest.kt`

This decodes the model's JSON text into the M3-3 `LlmParseResult` and applies sanity checks (amount > 0, valid direction, length caps). All three adapters reuse it.

- [ ] **Step 5.1 — Write the failing test.** Create `LlmJsonTest.kt`:
  ```kotlin
  package app.hisaab.llm

  import app.hisaab.domain.Direction
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertFailsWith
  import kotlin.test.assertNull
  import kotlin.test.assertTrue

  class LlmJsonTest {

      @Test
      fun `decodes a full financial result`() {
          val json = """
            {"amount":1500.0,"direction":"CREDIT","merchant":"Shwapno","categoryId":"food",
             "balanceAfter":3210.5,"refNo":"9AB12CD34","confidence":0.95,"isFinancial":true}
          """.trimIndent()
          val r = LlmJson.decodeResult(json)
          assertEquals(1500.0, r.amount)
          assertEquals(Direction.CREDIT, r.direction)
          assertEquals("Shwapno", r.merchant)
          assertEquals("food", r.categoryId)
          assertEquals(3210.5, r.balanceAfter)
          assertEquals("9AB12CD34", r.refNo)
          assertEquals(0.95, r.confidence)
          assertTrue(r.isFinancial)
      }

      @Test
      fun `tolerates json wrapped in markdown fences`() {
          val json = "```json\n{\"amount\":10.0,\"direction\":\"DEBIT\",\"confidence\":0.5,\"isFinancial\":true}\n```"
          val r = LlmJson.decodeResult(json)
          assertEquals(10.0, r.amount)
          assertEquals(Direction.DEBIT, r.direction)
      }

      @Test
      fun `non-financial result yields nulls`() {
          val json = """{"amount":null,"direction":null,"confidence":0.99,"isFinancial":false}"""
          val r = LlmJson.decodeResult(json)
          assertNull(r.amount)
          assertNull(r.direction)
          assertEquals(false, r.isFinancial)
      }

      @Test
      fun `negative or zero amount is sanitised to null`() {
          val json = """{"amount":-5.0,"direction":"DEBIT","confidence":0.8,"isFinancial":true}"""
          val r = LlmJson.decodeResult(json)
          assertNull(r.amount)
      }

      @Test
      fun `confidence is clamped to 0_1`() {
          val r = LlmJson.decodeResult("""{"amount":1.0,"direction":"DEBIT","confidence":5.0,"isFinancial":true}""")
          assertEquals(1.0, r.confidence)
          val r2 = LlmJson.decodeResult("""{"amount":1.0,"direction":"DEBIT","confidence":-2.0,"isFinancial":true}""")
          assertEquals(0.0, r2.confidence)
      }

      @Test
      fun `over-long merchant is truncated`() {
          val long = "x".repeat(200)
          val r = LlmJson.decodeResult("""{"merchant":"$long","confidence":0.5,"isFinancial":true}""")
          assertTrue((r.merchant?.length ?: 0) <= 64)
      }

      @Test
      fun `garbage throws decode LlmException`() {
          val ex = assertFailsWith<LlmException> { LlmJson.decodeResult("not json at all") }
          assertTrue(ex.error is LlmError.Decode)
      }
  }
  ```

- [ ] **Step 5.2 — Run it (expect FAIL — `LlmJson` missing).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.LlmJsonTest"
  ```
  Expected: **FAIL** (unresolved reference `LlmJson`).

- [ ] **Step 5.3 — Create `LlmJson.kt`.**
  ```kotlin
  package app.hisaab.llm

  import app.hisaab.domain.Direction
  import kotlinx.serialization.SerialName
  import kotlinx.serialization.Serializable
  import kotlinx.serialization.json.Json

  /**
   * Shared serialization for the model's structured output. All three cloud adapters
   * (and the on-device provider) decode the model's JSON text into [LlmParseResult]
   * through [decodeResult], which also applies sanity checks (amount > 0, valid
   * direction enum, length caps, clamped confidence).
   */
  object LlmJson {

      const val MERCHANT_MAX = 64
      const val REF_MAX = 48

      val json = Json {
          ignoreUnknownKeys = true
          isLenient = true
          coerceInputValues = true
      }

      @Serializable
      data class LlmParseDto(
          val amount: Double? = null,
          val direction: String? = null,
          val merchant: String? = null,
          @SerialName("categoryId") val categoryId: String? = null,
          val balanceAfter: Double? = null,
          val refNo: String? = null,
          val confidence: Double = 0.0,
          val isFinancial: Boolean = false,
      )

      @Serializable
      data class CategorizeDto(@SerialName("categoryId") val categoryId: String? = null)

      /** Strips ```json fences and isolates the first {...} block. */
      private fun isolateJson(raw: String): String {
          val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
          val start = cleaned.indexOf('{')
          val end = cleaned.lastIndexOf('}')
          return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else cleaned
      }

      fun decodeResult(rawContent: String): LlmParseResult {
          val dto = try {
              json.decodeFromString(LlmParseDto.serializer(), isolateJson(rawContent))
          } catch (e: Exception) {
              throw LlmException(LlmError.Decode(e.message ?: "malformed json"))
          }
          return LlmParseResult(
              amount = dto.amount?.takeIf { it > 0.0 },
              direction = dto.direction?.let { runCatching { Direction.valueOf(it.uppercase()) }.getOrNull() },
              merchant = dto.merchant?.trim()?.takeIf { it.isNotEmpty() }?.take(MERCHANT_MAX),
              categoryId = dto.categoryId?.trim()?.takeIf { it.isNotEmpty() },
              balanceAfter = dto.balanceAfter,
              refNo = dto.refNo?.trim()?.takeIf { it.isNotEmpty() }?.take(REF_MAX),
              confidence = dto.confidence.coerceIn(0.0, 1.0),
              isFinancial = dto.isFinancial,
          )
      }

      fun decodeCategoryId(rawContent: String, allowed: Set<String>): String? {
          val dto = try {
              json.decodeFromString(CategorizeDto.serializer(), isolateJson(rawContent))
          } catch (e: Exception) {
              return null
          }
          return dto.categoryId?.trim()?.takeIf { it in allowed }
      }
  }
  ```

- [ ] **Step 5.4 — Run it (expect PASS).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.LlmJsonTest"
  ```
  Expected: **PASS**.

- [ ] **Step 5.5 — Commit.**
  ```bash
  git add composeApp/src/commonMain/kotlin/app/hisaab/llm/LlmJson.kt composeApp/src/commonTest/kotlin/app/hisaab/llm/LlmJsonTest.kt
  git commit -m "feat: add LlmJson shared DTO decode + sanity validation into LlmParseResult"
  ```

---

### Task 6: `CloudHttp` — shared client factory + HTTP error mapping

**Files:**
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonMain/kotlin/app/hisaab/llm/CloudHttp.kt`
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonTest/kotlin/app/hisaab/llm/CloudHttpTest.kt`

- [ ] **Step 6.1 — Write the failing test.** Create `CloudHttpTest.kt`:
  ```kotlin
  package app.hisaab.llm

  import io.ktor.http.HttpStatusCode
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertTrue

  class CloudHttpTest {

      @Test
      fun `401 maps to invalid key`() {
          assertEquals(LlmError.InvalidKey, mapHttpError(HttpStatusCode.Unauthorized, ""))
      }

      @Test
      fun `403 maps to invalid key`() {
          assertEquals(LlmError.InvalidKey, mapHttpError(HttpStatusCode.Forbidden, ""))
      }

      @Test
      fun `429 maps to rate limited`() {
          assertEquals(LlmError.RateLimited, mapHttpError(HttpStatusCode.TooManyRequests, ""))
      }

      @Test
      fun `500 maps to retryable provider error`() {
          val e = mapHttpError(HttpStatusCode.InternalServerError, "down")
          assertTrue(e is LlmError.ProviderError && e.retryable)
      }

      @Test
      fun `400 maps to non-retryable provider error`() {
          val e = mapHttpError(HttpStatusCode.BadRequest, "bad")
          assertTrue(e is LlmError.ProviderError && !e.retryable)
      }
  }
  ```

- [ ] **Step 6.2 — Run it (expect FAIL — `mapHttpError` missing).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.CloudHttpTest"
  ```
  Expected: **FAIL** (unresolved reference `mapHttpError`).

- [ ] **Step 6.3 — Create `CloudHttp.kt`.**
  ```kotlin
  package app.hisaab.llm

  import io.ktor.client.HttpClient
  import io.ktor.client.plugins.HttpTimeout
  import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
  import io.ktor.http.HttpStatusCode
  import io.ktor.serialization.kotlinx.json.json

  /** Cloud LLM request timeout (spec §8). */
  const val LLM_TIMEOUT_MS: Long = 15_000

  /**
   * Wraps an existing engine-backed [HttpClient] config-less; cloud providers receive
   * a shared client. This factory configures JSON negotiation + a 15s timeout on top
   * of whatever engine the caller's client uses. In production we reuse AppContainer's
   * client; tests pass a MockEngine-backed client and call configure() equivalently.
   */
  fun HttpClient.llmConfigured(): HttpClient = config {
      install(ContentNegotiation) { json(LlmJson.json) }
      install(HttpTimeout) {
          requestTimeoutMillis = LLM_TIMEOUT_MS
          connectTimeoutMillis = LLM_TIMEOUT_MS
          socketTimeoutMillis = LLM_TIMEOUT_MS
      }
  }

  /** Maps a non-2xx HTTP status to a typed [LlmError]. */
  fun mapHttpError(status: HttpStatusCode, body: String): LlmError = when (status.value) {
      401, 403 -> LlmError.InvalidKey
      429 -> LlmError.RateLimited
      else -> LlmError.ProviderError(status.value, body.take(200))
  }
  ```

- [ ] **Step 6.4 — Run it (expect PASS).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.CloudHttpTest"
  ```
  Expected: **PASS**.

- [ ] **Step 6.5 — Commit.**
  ```bash
  git add composeApp/src/commonMain/kotlin/app/hisaab/llm/CloudHttp.kt composeApp/src/commonTest/kotlin/app/hisaab/llm/CloudHttpTest.kt
  git commit -m "feat: add CloudHttp shared Ktor config (15s timeout + json) and HTTP error mapping"
  ```

---

### Task 7: `ClaudeProvider` — Messages API `tool_use` adapter

**Files:**
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonMain/kotlin/app/hisaab/llm/cloud/ClaudeProvider.kt`
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonTest/kotlin/app/hisaab/llm/cloud/ClaudeProviderTest.kt`

Constructor: `HttpClient` + `apiKey: () -> String?`. Uses Claude's `tools` with an `input_schema` forcing the model to call a `record_transaction` tool; decodes the tool-call `input` object as the LLM JSON.

- [ ] **Step 7.1 — Write the failing test (MockEngine).** Create `ClaudeProviderTest.kt`:
  ```kotlin
  package app.hisaab.llm.cloud

  import app.hisaab.domain.Category
  import app.hisaab.domain.Direction
  import app.hisaab.llm.LlmError
  import app.hisaab.llm.LlmException
  import app.hisaab.llm.ParseRequest
  import app.hisaab.llm.ProviderId
  import io.ktor.client.HttpClient
  import io.ktor.client.engine.mock.MockEngine
  import io.ktor.client.engine.mock.respond
  import io.ktor.http.HttpHeaders
  import io.ktor.http.HttpStatusCode
  import io.ktor.http.headersOf
  import io.ktor.utils.io.readUTF8Line
  import kotlinx.coroutines.test.runTest
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertFailsWith
  import kotlin.test.assertTrue

  class ClaudeProviderTest {

      private val categories = listOf(
          Category("food", "Food & dining", null, null, null, true),
          Category("transport", "Transport", null, null, null, true),
      )

      private fun jsonHeaders() =
          headersOf(HttpHeaders.ContentType, "application/json")

      @Test
      fun `parse sends tool_use request and decodes tool input`() = runTest {
          var capturedBody = ""
          var capturedAuth: String? = null
          val engine = MockEngine { request ->
              capturedBody = (request.body as io.ktor.http.content.TextContent).text
              capturedAuth = request.headers["x-api-key"]
              respond(
                  content = """
                    {"content":[
                       {"type":"tool_use","name":"record_transaction",
                        "input":{"amount":320.0,"direction":"DEBIT","merchant":"Shwapno",
                                 "categoryId":"food","balanceAfter":1008.0,"refNo":null,
                                 "confidence":0.9,"isFinancial":true}}]}
                  """.trimIndent(),
                  status = HttpStatusCode.OK,
                  headers = jsonHeaders(),
              )
          }
          val provider = ClaudeProvider(HttpClient(engine), apiKey = { "sk-ant-test" })

          val result = provider.parse(
              ParseRequest(text = "Payment Tk 320 to Shwapno", senderHint = "bKash", categories = categories),
          )

          assertEquals(ProviderId.CLOUD_CLAUDE, provider.id)
          assertEquals(320.0, result.amount)
          assertEquals(Direction.DEBIT, result.direction)
          assertEquals("food", result.categoryId)
          assertEquals("sk-ant-test", capturedAuth)
          // body carries the model id, tools/input_schema and the (redacted) SMS text
          assertTrue(capturedBody.contains("\"tools\""), capturedBody)
          assertTrue(capturedBody.contains("input_schema"), capturedBody)
          assertTrue(capturedBody.contains("claude"), capturedBody)
      }

      @Test
      fun `401 maps to invalid key`() = runTest {
          val engine = MockEngine { respond("unauthorized", HttpStatusCode.Unauthorized) }
          val provider = ClaudeProvider(HttpClient(engine), apiKey = { "bad" })
          val ex = assertFailsWith<LlmException> {
              provider.parse(ParseRequest("x", null, categories))
          }
          assertEquals(LlmError.InvalidKey, ex.error)
      }

      @Test
      fun `429 maps to rate limited`() = runTest {
          val engine = MockEngine { respond("slow down", HttpStatusCode.TooManyRequests) }
          val provider = ClaudeProvider(HttpClient(engine), apiKey = { "k" })
          val ex = assertFailsWith<LlmException> {
              provider.parse(ParseRequest("x", null, categories))
          }
          assertEquals(LlmError.RateLimited, ex.error)
      }

      @Test
      fun `isAvailable false when no key`() = runTest {
          val provider = ClaudeProvider(HttpClient(MockEngine { respond("", HttpStatusCode.OK) }), apiKey = { null })
          assertEquals(false, provider.isAvailable())
      }
  }
  ```

- [ ] **Step 7.2 — Run it (expect FAIL — `ClaudeProvider` missing).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.cloud.ClaudeProviderTest"
  ```
  Expected: **FAIL** (unresolved reference `ClaudeProvider`).

- [ ] **Step 7.3 — Create `ClaudeProvider.kt`.**
  ```kotlin
  package app.hisaab.llm.cloud

  import app.hisaab.domain.Category
  import app.hisaab.llm.LlmError
  import app.hisaab.llm.LlmException
  import app.hisaab.llm.LlmJson
  import app.hisaab.llm.LlmParseResult
  import app.hisaab.llm.ParseRequest
  import app.hisaab.llm.ProviderId
  import app.hisaab.llm.Prompts
  import app.hisaab.llm.Redactor
  import app.hisaab.llm.llmConfigured
  import app.hisaab.llm.mapHttpError
  import io.ktor.client.HttpClient
  import io.ktor.client.request.header
  import io.ktor.client.request.post
  import io.ktor.client.request.setBody
  import io.ktor.client.statement.bodyAsText
  import io.ktor.http.ContentType
  import io.ktor.http.contentType
  import io.ktor.http.isSuccess
  import kotlinx.serialization.json.add
  import kotlinx.serialization.json.addJsonObject
  import kotlinx.serialization.json.buildJsonObject
  import kotlinx.serialization.json.jsonArray
  import kotlinx.serialization.json.jsonObject
  import kotlinx.serialization.json.put
  import kotlinx.serialization.json.putJsonArray
  import kotlinx.serialization.json.putJsonObject

  /**
   * Claude Messages API adapter. Forces structured output via a single tool with an
   * input_schema; reads the tool_use block's `input` object as the LLM JSON.
   *
   * MODEL ID: confirm via Context7. Current best-guess: claude-3-5-haiku-latest.
   */
  class ClaudeProvider(
      httpClient: HttpClient,
      private val apiKey: () -> String?,
      private val model: String = "claude-3-5-haiku-latest",
      private val redact: Boolean = true,
  ) : app.hisaab.llm.LlmProvider {

      private val client = httpClient.llmConfigured()
      private val endpoint = "https://api.anthropic.com/v1/messages"
      private val anthropicVersion = "2023-06-01"

      override val id = ProviderId.CLOUD_CLAUDE

      override suspend fun isAvailable(): Boolean = !apiKey().isNullOrBlank()

      override suspend fun parse(req: ParseRequest): LlmParseResult {
          val key = apiKey() ?: throw LlmException(LlmError.InvalidKey)
          val text = if (redact) Redactor.redact(req.text) else req.text
          val system = Prompts.extractionSystem(req.categories)
          val userMsg = buildString {
              req.senderHint?.let { appendLine("Sender: $it") }
              append("SMS: \"$text\"")
          }

          val payload = buildJsonObject {
              put("model", model)
              put("max_tokens", 512)
              put("system", system)
              put("tool_choice", buildJsonObject {
                  put("type", "tool")
                  put("name", "record_transaction")
              })
              putJsonArray("tools") {
                  addJsonObject {
                      put("name", "record_transaction")
                      put("description", "Record the extracted transaction fields from the SMS.")
                      put("input_schema", parseInputSchema(req.categories))
                  }
              }
              putJsonArray("messages") {
                  addJsonObject {
                      put("role", "user")
                      put("content", userMsg)
                  }
              }
          }

          val response = client.post(endpoint) {
              header("x-api-key", key)
              header("anthropic-version", anthropicVersion)
              contentType(ContentType.Application.Json)
              setBody(LlmJson.json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), payload))
          }
          if (!response.status.isSuccess()) {
              throw LlmException(mapHttpError(response.status, response.bodyAsText()))
          }
          val bodyText = response.bodyAsText()
          val root = LlmJson.json.parseToJsonElement(bodyText).jsonObject
          val toolInput = root["content"]?.jsonArray
              ?.firstOrNull { it.jsonObject["type"]?.toString()?.contains("tool_use") == true }
              ?.jsonObject?.get("input")
              ?: throw LlmException(LlmError.Decode("no tool_use block"))
          return LlmJson.decodeResult(toolInput.toString())
      }

      override suspend fun categorize(merchant: String, categories: List<Category>): String? {
          val key = apiKey() ?: return null
          val payload = buildJsonObject {
              put("model", model)
              put("max_tokens", 64)
              putJsonArray("messages") {
                  addJsonObject {
                      put("role", "user")
                      put("content", Prompts.categorize(merchant, categories))
                  }
              }
          }
          val response = client.post(endpoint) {
              header("x-api-key", key)
              header("anthropic-version", anthropicVersion)
              contentType(ContentType.Application.Json)
              setBody(LlmJson.json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), payload))
          }
          if (!response.status.isSuccess()) return null
          val text = LlmJson.json.parseToJsonElement(response.bodyAsText()).jsonObject["content"]
              ?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.toString()?.trim('"') ?: return null
          return LlmJson.decodeCategoryId(text, categories.map { it.id }.toSet())
      }

      private fun parseInputSchema(categories: List<Category>) = buildJsonObject {
          put("type", "object")
          putJsonObject("properties") {
              putJsonObject("amount") { put("type", "number") }
              putJsonObject("direction") {
                  put("type", "string"); putJsonArray("enum") { add("DEBIT"); add("CREDIT") }
              }
              putJsonObject("merchant") { put("type", "string") }
              putJsonObject("categoryId") {
                  put("type", "string"); putJsonArray("enum") { categories.forEach { add(it.id) } }
              }
              putJsonObject("balanceAfter") { put("type", "number") }
              putJsonObject("refNo") { put("type", "string") }
              putJsonObject("confidence") { put("type", "number") }
              putJsonObject("isFinancial") { put("type", "boolean") }
          }
          putJsonArray("required") { add("confidence"); add("isFinancial") }
      }
  }
  ```

- [ ] **Step 7.4 — Run it (expect PASS).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.cloud.ClaudeProviderTest"
  ```
  Expected: **PASS**.

- [ ] **Step 7.5 — Commit.**
  ```bash
  git add composeApp/src/commonMain/kotlin/app/hisaab/llm/cloud/ClaudeProvider.kt composeApp/src/commonTest/kotlin/app/hisaab/llm/cloud/ClaudeProviderTest.kt
  git commit -m "feat: add ClaudeProvider (Messages API tool_use structured output) over Ktor"
  ```

---

### Task 8: `GeminiProvider` — `responseSchema` + `responseMimeType` adapter

**Files:**
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonMain/kotlin/app/hisaab/llm/cloud/GeminiProvider.kt`
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonTest/kotlin/app/hisaab/llm/cloud/GeminiProviderTest.kt`

Gemini puts the structured JSON text in `candidates[0].content.parts[0].text`. Key goes in the query string (`?key=`).

- [ ] **Step 8.1 — Write the failing test.** Create `GeminiProviderTest.kt`:
  ```kotlin
  package app.hisaab.llm.cloud

  import app.hisaab.domain.Category
  import app.hisaab.domain.Direction
  import app.hisaab.llm.LlmError
  import app.hisaab.llm.LlmException
  import app.hisaab.llm.ParseRequest
  import app.hisaab.llm.ProviderId
  import io.ktor.client.HttpClient
  import io.ktor.client.engine.mock.MockEngine
  import io.ktor.client.engine.mock.respond
  import io.ktor.http.HttpHeaders
  import io.ktor.http.HttpStatusCode
  import io.ktor.http.headersOf
  import kotlinx.coroutines.test.runTest
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertFailsWith
  import kotlin.test.assertTrue

  class GeminiProviderTest {

      private val categories = listOf(
          Category("food", "Food & dining", null, null, null, true),
          Category("salary", "Salary", null, null, null, true),
      )

      @Test
      fun `parse sends responseSchema and decodes candidate text`() = runTest {
          var capturedBody = ""
          var capturedUrl = ""
          val engine = MockEngine { request ->
              capturedBody = (request.body as io.ktor.http.content.TextContent).text
              capturedUrl = request.url.toString()
              respond(
                  content = """
                    {"candidates":[{"content":{"parts":[
                       {"text":"{\"amount\":50000.0,\"direction\":\"CREDIT\",\"merchant\":null,\"categoryId\":\"salary\",\"balanceAfter\":52000.0,\"refNo\":null,\"confidence\":0.92,\"isFinancial\":true}"}
                    ]}}]}
                  """.trimIndent(),
                  status = HttpStatusCode.OK,
                  headers = headersOf(HttpHeaders.ContentType, "application/json"),
              )
          }
          val provider = GeminiProvider(HttpClient(engine), apiKey = { "g-key" })

          val r = provider.parse(ParseRequest("Salary credited Tk 50000", "BANK", categories))

          assertEquals(ProviderId.CLOUD_GEMINI, provider.id)
          assertEquals(50000.0, r.amount)
          assertEquals(Direction.CREDIT, r.direction)
          assertEquals("salary", r.categoryId)
          assertTrue(capturedUrl.contains("key=g-key"), capturedUrl)
          assertTrue(capturedBody.contains("responseSchema"), capturedBody)
          assertTrue(capturedBody.contains("application/json"), capturedBody)
      }

      @Test
      fun `403 maps to invalid key`() = runTest {
          val engine = MockEngine { respond("forbidden", HttpStatusCode.Forbidden) }
          val provider = GeminiProvider(HttpClient(engine), apiKey = { "bad" })
          val ex = assertFailsWith<LlmException> { provider.parse(ParseRequest("x", null, categories)) }
          assertEquals(LlmError.InvalidKey, ex.error)
      }
  }
  ```

- [ ] **Step 8.2 — Run it (expect FAIL).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.cloud.GeminiProviderTest"
  ```
  Expected: **FAIL** (unresolved reference `GeminiProvider`).

- [ ] **Step 8.3 — Create `GeminiProvider.kt`.**
  ```kotlin
  package app.hisaab.llm.cloud

  import app.hisaab.domain.Category
  import app.hisaab.llm.LlmError
  import app.hisaab.llm.LlmException
  import app.hisaab.llm.LlmJson
  import app.hisaab.llm.LlmParseResult
  import app.hisaab.llm.ParseRequest
  import app.hisaab.llm.ProviderId
  import app.hisaab.llm.Prompts
  import app.hisaab.llm.Redactor
  import app.hisaab.llm.llmConfigured
  import app.hisaab.llm.mapHttpError
  import io.ktor.client.HttpClient
  import io.ktor.client.request.post
  import io.ktor.client.request.setBody
  import io.ktor.client.statement.bodyAsText
  import io.ktor.http.ContentType
  import io.ktor.http.contentType
  import io.ktor.http.isSuccess
  import kotlinx.serialization.json.JsonObject
  import kotlinx.serialization.json.JsonPrimitive
  import kotlinx.serialization.json.add
  import kotlinx.serialization.json.addJsonObject
  import kotlinx.serialization.json.buildJsonObject
  import kotlinx.serialization.json.jsonArray
  import kotlinx.serialization.json.jsonObject
  import kotlinx.serialization.json.jsonPrimitive
  import kotlinx.serialization.json.put
  import kotlinx.serialization.json.putJsonArray
  import kotlinx.serialization.json.putJsonObject

  /**
   * Gemini generateContent adapter. Uses generationConfig.responseSchema +
   * responseMimeType=application/json so the candidate text IS the structured JSON.
   *
   * MODEL ID: confirm via Context7. Current best-guess: gemini-2.0-flash (v1beta).
   */
  class GeminiProvider(
      httpClient: HttpClient,
      private val apiKey: () -> String?,
      private val model: String = "gemini-2.0-flash",
      private val redact: Boolean = true,
  ) : app.hisaab.llm.LlmProvider {

      private val client = httpClient.llmConfigured()
      private fun endpoint(key: String) =
          "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"

      override val id = ProviderId.CLOUD_GEMINI

      override suspend fun isAvailable(): Boolean = !apiKey().isNullOrBlank()

      override suspend fun parse(req: ParseRequest): LlmParseResult {
          val key = apiKey() ?: throw LlmException(LlmError.InvalidKey)
          val text = if (redact) Redactor.redact(req.text) else req.text
          val prompt = buildString {
              append(Prompts.extractionSystem(req.categories))
              appendLine()
              req.senderHint?.let { appendLine("Sender: $it") }
              append("SMS: \"$text\"")
          }

          val payload = buildJsonObject {
              putJsonArray("contents") {
                  addJsonObject {
                      putJsonArray("parts") { addJsonObject { put("text", prompt) } }
                  }
              }
              putJsonObject("generationConfig") {
                  put("responseMimeType", "application/json")
                  put("responseSchema", responseSchema(req.categories))
              }
          }

          val response = client.post(endpoint(key)) {
              contentType(ContentType.Application.Json)
              setBody(LlmJson.json.encodeToString(JsonObject.serializer(), payload))
          }
          if (!response.status.isSuccess()) {
              throw LlmException(mapHttpError(response.status, response.bodyAsText()))
          }
          val candidateText = extractText(response.bodyAsText())
              ?: throw LlmException(LlmError.Decode("no candidate text"))
          return LlmJson.decodeResult(candidateText)
      }

      override suspend fun categorize(merchant: String, categories: List<Category>): String? {
          val key = apiKey() ?: return null
          val payload = buildJsonObject {
              putJsonArray("contents") {
                  addJsonObject {
                      putJsonArray("parts") { addJsonObject { put("text", Prompts.categorize(merchant, categories)) } }
                  }
              }
              putJsonObject("generationConfig") { put("responseMimeType", "application/json") }
          }
          val response = client.post(endpoint(key)) {
              contentType(ContentType.Application.Json)
              setBody(LlmJson.json.encodeToString(JsonObject.serializer(), payload))
          }
          if (!response.status.isSuccess()) return null
          val text = extractText(response.bodyAsText()) ?: return null
          return LlmJson.decodeCategoryId(text, categories.map { it.id }.toSet())
      }

      private fun extractText(body: String): String? =
          LlmJson.json.parseToJsonElement(body).jsonObject["candidates"]?.jsonArray
              ?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
              ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content

      private fun responseSchema(categories: List<Category>) = buildJsonObject {
          put("type", "OBJECT")
          putJsonObject("properties") {
              putJsonObject("amount") { put("type", "NUMBER") }
              putJsonObject("direction") {
                  put("type", "STRING"); putJsonArray("enum") { add("DEBIT"); add("CREDIT") }
              }
              putJsonObject("merchant") { put("type", "STRING") }
              putJsonObject("categoryId") {
                  put("type", "STRING"); putJsonArray("enum") { categories.forEach { add(it.id) } }
              }
              putJsonObject("balanceAfter") { put("type", "NUMBER") }
              putJsonObject("refNo") { put("type", "STRING") }
              putJsonObject("confidence") { put("type", "NUMBER") }
              putJsonObject("isFinancial") { put("type", "BOOLEAN") }
          }
          putJsonArray("required") { add("confidence"); add("isFinancial") }
      }
  }
  ```

- [ ] **Step 8.4 — Run it (expect PASS).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.cloud.GeminiProviderTest"
  ```
  Expected: **PASS**.

- [ ] **Step 8.5 — Commit.**
  ```bash
  git add composeApp/src/commonMain/kotlin/app/hisaab/llm/cloud/GeminiProvider.kt composeApp/src/commonTest/kotlin/app/hisaab/llm/cloud/GeminiProviderTest.kt
  git commit -m "feat: add GeminiProvider (responseSchema + json mime) over Ktor"
  ```

---

### Task 9: `OpenAiProvider` — Chat Completions `response_format: json_schema`

**Files:**
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonMain/kotlin/app/hisaab/llm/cloud/OpenAiProvider.kt`
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonTest/kotlin/app/hisaab/llm/cloud/OpenAiProviderTest.kt`

OpenAI returns the JSON in `choices[0].message.content`; key in `Authorization: Bearer`.

- [ ] **Step 9.1 — Write the failing test.** Create `OpenAiProviderTest.kt`:
  ```kotlin
  package app.hisaab.llm.cloud

  import app.hisaab.domain.Category
  import app.hisaab.domain.Direction
  import app.hisaab.llm.LlmError
  import app.hisaab.llm.LlmException
  import app.hisaab.llm.ParseRequest
  import app.hisaab.llm.ProviderId
  import io.ktor.client.HttpClient
  import io.ktor.client.engine.mock.MockEngine
  import io.ktor.client.engine.mock.respond
  import io.ktor.http.HttpHeaders
  import io.ktor.http.HttpStatusCode
  import io.ktor.http.headersOf
  import kotlinx.coroutines.test.runTest
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertFailsWith
  import kotlin.test.assertTrue

  class OpenAiProviderTest {

      private val categories = listOf(
          Category("food", "Food & dining", null, null, null, true),
          Category("transport", "Transport", null, null, null, true),
      )

      @Test
      fun `parse sends json_schema and decodes message content`() = runTest {
          var capturedBody = ""
          var capturedAuth: String? = null
          val engine = MockEngine { request ->
              capturedBody = (request.body as io.ktor.http.content.TextContent).text
              capturedAuth = request.headers[HttpHeaders.Authorization]
              respond(
                  content = """
                    {"choices":[{"message":{"role":"assistant",
                       "content":"{\"amount\":120.0,\"direction\":\"DEBIT\",\"merchant\":\"Uber\",\"categoryId\":\"transport\",\"balanceAfter\":null,\"refNo\":null,\"confidence\":0.88,\"isFinancial\":true}"}}]}
                  """.trimIndent(),
                  status = HttpStatusCode.OK,
                  headers = headersOf(HttpHeaders.ContentType, "application/json"),
              )
          }
          val provider = OpenAiProvider(HttpClient(engine), apiKey = { "sk-oai" })

          val r = provider.parse(ParseRequest("Uber ride Tk 120", null, categories))

          assertEquals(ProviderId.CLOUD_OPENAI, provider.id)
          assertEquals(120.0, r.amount)
          assertEquals(Direction.DEBIT, r.direction)
          assertEquals("transport", r.categoryId)
          assertEquals("Bearer sk-oai", capturedAuth)
          assertTrue(capturedBody.contains("json_schema"), capturedBody)
          assertTrue(capturedBody.contains("gpt-4o-mini"), capturedBody)
      }

      @Test
      fun `500 maps to retryable provider error`() = runTest {
          val engine = MockEngine { respond("server error", HttpStatusCode.InternalServerError) }
          val provider = OpenAiProvider(HttpClient(engine), apiKey = { "k" })
          val ex = assertFailsWith<LlmException> { provider.parse(ParseRequest("x", null, categories)) }
          assertTrue(ex.error is LlmError.ProviderError && ex.error.retryable)
      }
  }
  ```

- [ ] **Step 9.2 — Run it (expect FAIL).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.cloud.OpenAiProviderTest"
  ```
  Expected: **FAIL** (unresolved reference `OpenAiProvider`).

- [ ] **Step 9.3 — Create `OpenAiProvider.kt`.**
  ```kotlin
  package app.hisaab.llm.cloud

  import app.hisaab.domain.Category
  import app.hisaab.llm.LlmError
  import app.hisaab.llm.LlmException
  import app.hisaab.llm.LlmJson
  import app.hisaab.llm.LlmParseResult
  import app.hisaab.llm.ParseRequest
  import app.hisaab.llm.ProviderId
  import app.hisaab.llm.Prompts
  import app.hisaab.llm.Redactor
  import app.hisaab.llm.llmConfigured
  import app.hisaab.llm.mapHttpError
  import io.ktor.client.HttpClient
  import io.ktor.client.request.header
  import io.ktor.client.request.post
  import io.ktor.client.request.setBody
  import io.ktor.client.statement.bodyAsText
  import io.ktor.http.ContentType
  import io.ktor.http.contentType
  import io.ktor.http.isSuccess
  import kotlinx.serialization.json.JsonObject
  import kotlinx.serialization.json.add
  import kotlinx.serialization.json.addJsonObject
  import kotlinx.serialization.json.buildJsonObject
  import kotlinx.serialization.json.jsonArray
  import kotlinx.serialization.json.jsonObject
  import kotlinx.serialization.json.jsonPrimitive
  import kotlinx.serialization.json.put
  import kotlinx.serialization.json.putJsonArray
  import kotlinx.serialization.json.putJsonObject

  /**
   * OpenAI Chat Completions adapter using response_format json_schema (strict).
   *
   * MODEL ID: confirm via Context7. Current best-guess: gpt-4o-mini.
   */
  class OpenAiProvider(
      httpClient: HttpClient,
      private val apiKey: () -> String?,
      private val model: String = "gpt-4o-mini",
      private val redact: Boolean = true,
  ) : app.hisaab.llm.LlmProvider {

      private val client = httpClient.llmConfigured()
      private val endpoint = "https://api.openai.com/v1/chat/completions"

      override val id = ProviderId.CLOUD_OPENAI

      override suspend fun isAvailable(): Boolean = !apiKey().isNullOrBlank()

      override suspend fun parse(req: ParseRequest): LlmParseResult {
          val key = apiKey() ?: throw LlmException(LlmError.InvalidKey)
          val text = if (redact) Redactor.redact(req.text) else req.text
          val userMsg = buildString {
              req.senderHint?.let { appendLine("Sender: $it") }
              append("SMS: \"$text\"")
          }

          val payload = buildJsonObject {
              put("model", model)
              putJsonArray("messages") {
                  addJsonObject {
                      put("role", "system"); put("content", Prompts.extractionSystem(req.categories))
                  }
                  addJsonObject { put("role", "user"); put("content", userMsg) }
              }
              putJsonObject("response_format") {
                  put("type", "json_schema")
                  putJsonObject("json_schema") {
                      put("name", "transaction_extraction")
                      put("strict", true)
                      put("schema", jsonSchema(req.categories))
                  }
              }
          }

          val response = client.post(endpoint) {
              header("Authorization", "Bearer $key")
              contentType(ContentType.Application.Json)
              setBody(LlmJson.json.encodeToString(JsonObject.serializer(), payload))
          }
          if (!response.status.isSuccess()) {
              throw LlmException(mapHttpError(response.status, response.bodyAsText()))
          }
          val content = extractContent(response.bodyAsText())
              ?: throw LlmException(LlmError.Decode("no message content"))
          return LlmJson.decodeResult(content)
      }

      override suspend fun categorize(merchant: String, categories: List<Category>): String? {
          val key = apiKey() ?: return null
          val payload = buildJsonObject {
              put("model", model)
              putJsonArray("messages") {
                  addJsonObject { put("role", "user"); put("content", Prompts.categorize(merchant, categories)) }
              }
              putJsonObject("response_format") { put("type", "json_object") }
          }
          val response = client.post(endpoint) {
              header("Authorization", "Bearer $key")
              contentType(ContentType.Application.Json)
              setBody(LlmJson.json.encodeToString(JsonObject.serializer(), payload))
          }
          if (!response.status.isSuccess()) return null
          val content = extractContent(response.bodyAsText()) ?: return null
          return LlmJson.decodeCategoryId(content, categories.map { it.id }.toSet())
      }

      private fun extractContent(body: String): String? =
          LlmJson.json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray
              ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content

      private fun jsonSchema(categories: List<Category>) = buildJsonObject {
          put("type", "object")
          put("additionalProperties", false)
          putJsonObject("properties") {
              putJsonObject("amount") { putJsonArray("type") { add("number"); add("null") } }
              putJsonObject("direction") {
                  putJsonArray("type") { add("string"); add("null") }
                  putJsonArray("enum") { add("DEBIT"); add("CREDIT") }
              }
              putJsonObject("merchant") { putJsonArray("type") { add("string"); add("null") } }
              putJsonObject("categoryId") {
                  putJsonArray("type") { add("string"); add("null") }
                  putJsonArray("enum") { categories.forEach { add(it.id) } }
              }
              putJsonObject("balanceAfter") { putJsonArray("type") { add("number"); add("null") } }
              putJsonObject("refNo") { putJsonArray("type") { add("string"); add("null") } }
              putJsonObject("confidence") { put("type", "number") }
              putJsonObject("isFinancial") { put("type", "boolean") }
          }
          putJsonArray("required") {
              add("amount"); add("direction"); add("merchant"); add("categoryId")
              add("balanceAfter"); add("refNo"); add("confidence"); add("isFinancial")
          }
      }
  }
  ```

- [ ] **Step 9.4 — Run it (expect PASS).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.cloud.OpenAiProviderTest"
  ```
  Expected: **PASS**.

- [ ] **Step 9.5 — Commit.**
  ```bash
  git add composeApp/src/commonMain/kotlin/app/hisaab/llm/cloud/OpenAiProvider.kt composeApp/src/commonTest/kotlin/app/hisaab/llm/cloud/OpenAiProviderTest.kt
  git commit -m "feat: add OpenAiProvider (chat completions response_format json_schema) over Ktor"
  ```

---

### Task 10: `createOnDeviceProvider()` expect/actual (ios/wasm null)

**Files:**
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonMain/kotlin/app/hisaab/llm/OnDeviceProvider.kt`
- Create: `/Users/xack/projects/finance-app/composeApp/src/iosMain/kotlin/app/hisaab/llm/OnDeviceProvider.ios.kt`
- Create: `/Users/xack/projects/finance-app/composeApp/src/wasmJsMain/kotlin/app/hisaab/llm/OnDeviceProvider.wasmJs.kt`
- Create (Android actual placeholder, real impl in Task 12): `/Users/xack/projects/finance-app/composeApp/src/androidMain/kotlin/app/hisaab/llm/OnDeviceProvider.android.kt`
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonTest/kotlin/app/hisaab/llm/OnDeviceProviderTest.kt`

We introduce the `expect fun` now and stub the Android actual so all targets compile; the real MediaPipe body lands in Task 12. The common-test asserts the function is callable and returns null where unsupported (it runs on JVM/Android unit test; the Android actual placeholder returns null until Task 12, which only changes behavior on a real device).

- [ ] **Step 10.1 — Write the failing test.** Create `OnDeviceProviderTest.kt`:
  ```kotlin
  package app.hisaab.llm

  import kotlin.test.Test

  class OnDeviceProviderTest {
      @Test
      fun `createOnDeviceProvider is callable from common`() {
          // On JVM/Android unit-test host the model is absent, so this is null or a
          // provider whose isAvailable() is false. We only assert it compiles + runs.
          createOnDeviceProvider()
      }
  }
  ```

- [ ] **Step 10.2 — Run it (expect FAIL — `createOnDeviceProvider` missing).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.OnDeviceProviderTest"
  ```
  Expected: **FAIL** (unresolved reference `createOnDeviceProvider`).

- [ ] **Step 10.3 — Create the `expect` in commonMain (`OnDeviceProvider.kt`).**
  ```kotlin
  package app.hisaab.llm

  /**
   * Platform on-device LLM provider. Android returns a MediaPipe/AICore-backed
   * provider when a model is available; iOS and wasm return null (the iOS Foundation
   * Models path is a later slice). Callers must treat null as "no on-device engine".
   */
  expect fun createOnDeviceProvider(): LlmProvider?
  ```

- [ ] **Step 10.4 — Create iOS actual (`OnDeviceProvider.ios.kt`).**
  ```kotlin
  package app.hisaab.llm

  actual fun createOnDeviceProvider(): LlmProvider? = null
  ```

- [ ] **Step 10.5 — Create wasm actual (`OnDeviceProvider.wasmJs.kt`).**
  ```kotlin
  package app.hisaab.llm

  actual fun createOnDeviceProvider(): LlmProvider? = null
  ```

- [ ] **Step 10.6 — Create Android actual placeholder (`OnDeviceProvider.android.kt`).** Real MediaPipe wiring lands in Task 12.
  ```kotlin
  package app.hisaab.llm

  // Placeholder until Task 12 wires ModelManager + AndroidOnDeviceProvider.
  // Returns null so the router falls through to cloud/template until a model is present.
  actual fun createOnDeviceProvider(): LlmProvider? = null
  ```

- [ ] **Step 10.7 — Run it (expect PASS).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.OnDeviceProviderTest"
  ```
  Expected: **PASS**.

- [ ] **Step 10.8 — Verify all targets compile (so iOS/wasm actuals are valid).**
  ```bash
  ./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileKotlinWasmJs
  ```
  Expected: **PASS** (no missing-actual errors).

- [ ] **Step 10.9 — Commit.**
  ```bash
  git add composeApp/src/commonMain/kotlin/app/hisaab/llm/OnDeviceProvider.kt composeApp/src/iosMain/kotlin/app/hisaab/llm/OnDeviceProvider.ios.kt composeApp/src/wasmJsMain/kotlin/app/hisaab/llm/OnDeviceProvider.wasmJs.kt composeApp/src/androidMain/kotlin/app/hisaab/llm/OnDeviceProvider.android.kt composeApp/src/commonTest/kotlin/app/hisaab/llm/OnDeviceProviderTest.kt
  git commit -m "feat: add createOnDeviceProvider expect/actual (android placeholder, ios/wasm null)"
  ```

---

### Task 11: `DefaultLlmRouter` — provider selection + consent/key gate

**Files:**
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonMain/kotlin/app/hisaab/llm/DefaultLlmRouter.kt`
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonTest/kotlin/app/hisaab/llm/DefaultLlmRouterTest.kt`
- Create: `/Users/xack/projects/finance-app/composeApp/src/commonTest/kotlin/app/hisaab/llm/support/FakeProviders.kt`

`DefaultLlmRouter(configRepo, secureStorage, onDeviceProvider, claude, gemini, openai)` reads `CaptureConfig.get()`:
- `engineMode == ON_DEVICE` → return `onDeviceProvider` if its `isAvailable()`, else null (fall through to template-only).
- `engineMode == CLOUD` → require `cloudConsentAt != null` AND the selected provider's key present (`isAvailable()`); map `cloudProvider` to the matching adapter. No consent or no key → null.

To test against the M3-1 `CaptureConfigRepository` (which takes a `HisaabDatabase`), the router depends on an interface seam `ConfigSource` with `suspend fun get(): CaptureConfig`, implemented by an adapter over `CaptureConfigRepository`. The fake in tests implements `ConfigSource` directly. `SecureStorage` access uses only `loadString(key)`, which we double via a small `KeyStore` functional seam to keep the test in commonTest (SecureStorage is `expect` with no commonTest actual).

- [ ] **Step 11.1 — Write fakes (`support/FakeProviders.kt`).**
  ```kotlin
  package app.hisaab.llm.support

  import app.hisaab.domain.Category
  import app.hisaab.llm.LlmParseResult
  import app.hisaab.llm.LlmProvider
  import app.hisaab.llm.ParseRequest
  import app.hisaab.llm.ProviderId

  /** Fake provider with controllable availability + canned result. */
  class FakeProvider(
      override val id: ProviderId,
      private val available: Boolean,
      private val result: LlmParseResult = LlmParseResult(
          amount = 1.0, direction = null, merchant = null, categoryId = null,
          balanceAfter = null, refNo = null, confidence = 0.5, isFinancial = true,
      ),
  ) : LlmProvider {
      override suspend fun isAvailable(): Boolean = available
      override suspend fun parse(req: ParseRequest): LlmParseResult = result
      override suspend fun categorize(merchant: String, categories: List<Category>): String? = null
  }
  ```

- [ ] **Step 11.2 — Write the failing test (`DefaultLlmRouterTest.kt`).**
  ```kotlin
  package app.hisaab.llm

  import app.hisaab.domain.CaptureConfig
  import app.hisaab.domain.CloudProvider
  import app.hisaab.domain.EngineMode
  import app.hisaab.llm.support.FakeProvider
  import kotlinx.coroutines.test.runTest
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertNull

  class DefaultLlmRouterTest {

      private fun config(
          engineMode: EngineMode,
          cloudProvider: CloudProvider? = null,
          cloudConsentAt: Long? = null,
      ) = CaptureConfig(
          captureEnabled = true,
          engineMode = engineMode,
          onDeviceModel = "auto",
          cloudProvider = cloudProvider,
          cloudModel = null,
          redactionEnabled = true,
          alwaysReview = false,
          autoPostThreshold = 0.85,
          cloudConsentAt = cloudConsentAt,
          retainRawBody = true,
          lastSmsCursor = 0L,
          updatedAt = 0L,
      )

      private fun router(
          cfg: CaptureConfig,
          keys: Map<CloudProvider, String?> = emptyMap(),
          onDevice: LlmProvider? = null,
      ): DefaultLlmRouter = DefaultLlmRouter(
          configSource = { cfg },
          keyLoader = { provider -> keys[provider] },
          onDeviceProvider = onDevice,
          claude = FakeProvider(ProviderId.CLOUD_CLAUDE, available = keys[CloudProvider.CLAUDE] != null),
          gemini = FakeProvider(ProviderId.CLOUD_GEMINI, available = keys[CloudProvider.GEMINI] != null),
          openai = FakeProvider(ProviderId.CLOUD_OPENAI, available = keys[CloudProvider.OPENAI] != null),
      )

      @Test
      fun `on-device mode returns on-device provider when available`() = runTest {
          val od = FakeProvider(ProviderId.ON_DEVICE, available = true)
          val r = router(config(EngineMode.ON_DEVICE), onDevice = od)
          assertEquals(ProviderId.ON_DEVICE, r.active()?.id)
      }

      @Test
      fun `on-device mode returns null when model unavailable`() = runTest {
          val od = FakeProvider(ProviderId.ON_DEVICE, available = false)
          val r = router(config(EngineMode.ON_DEVICE), onDevice = od)
          assertNull(r.active())
      }

      @Test
      fun `on-device mode returns null when no provider on platform`() = runTest {
          val r = router(config(EngineMode.ON_DEVICE), onDevice = null)
          assertNull(r.active())
      }

      @Test
      fun `cloud mode returns selected provider when consented and key present`() = runTest {
          val r = router(
              config(EngineMode.CLOUD, CloudProvider.GEMINI, cloudConsentAt = 123L),
              keys = mapOf(CloudProvider.GEMINI to "g-key"),
          )
          assertEquals(ProviderId.CLOUD_GEMINI, r.active()?.id)
      }

      @Test
      fun `cloud mode returns null without consent`() = runTest {
          val r = router(
              config(EngineMode.CLOUD, CloudProvider.CLAUDE, cloudConsentAt = null),
              keys = mapOf(CloudProvider.CLAUDE to "k"),
          )
          assertNull(r.active())
      }

      @Test
      fun `cloud mode returns null when key missing`() = runTest {
          val r = router(
              config(EngineMode.CLOUD, CloudProvider.OPENAI, cloudConsentAt = 1L),
              keys = emptyMap(),
          )
          assertNull(r.active())
      }

      @Test
      fun `cloud mode returns null when no provider selected`() = runTest {
          val r = router(config(EngineMode.CLOUD, cloudProvider = null, cloudConsentAt = 1L))
          assertNull(r.active())
      }
  }
  ```

- [ ] **Step 11.3 — Run it (expect FAIL — `DefaultLlmRouter` missing).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.DefaultLlmRouterTest"
  ```
  Expected: **FAIL** (unresolved reference `DefaultLlmRouter`).

- [ ] **Step 11.4 — Create `DefaultLlmRouter.kt`.** Note the public production constructor takes `CaptureConfigRepository` + `SecureStorage`; the internal constructor takes the functional seams the test drives.
  ```kotlin
  package app.hisaab.llm

  import app.hisaab.data.CaptureConfigRepository
  import app.hisaab.domain.CaptureConfig
  import app.hisaab.domain.CloudProvider
  import app.hisaab.domain.EngineMode
  import app.hisaab.platform.SecureStorage

  /** SecureStorage key prefix for BYO cloud API keys: "llm_api_key_<PROVIDER>". */
  fun secureKeyFor(provider: CloudProvider): String = "llm_api_key_${provider.name}"

  /**
   * Selects the active LLM provider from CaptureConfig with a cloud consent + key gate.
   *
   * - ON_DEVICE: the on-device provider if available, else null (template-only fallback).
   * - CLOUD: the selected cloud adapter only when consent is recorded AND its key is
   *   present (provider.isAvailable()). Otherwise null.
   *
   * Internal ctor exposes functional seams (configSource/keyLoader) for unit testing
   * without an actual SecureStorage; the production ctor adapts the repo + SecureStorage.
   */
  class DefaultLlmRouter internal constructor(
      private val configSource: suspend () -> CaptureConfig,
      private val keyLoader: (CloudProvider) -> String?,
      private val onDeviceProvider: LlmProvider?,
      private val claude: LlmProvider,
      private val gemini: LlmProvider,
      private val openai: LlmProvider,
  ) : LlmRouter {

      constructor(
          configRepo: CaptureConfigRepository,
          secureStorage: SecureStorage,
          onDeviceProvider: LlmProvider?,
          claude: LlmProvider,
          gemini: LlmProvider,
          openai: LlmProvider,
      ) : this(
          configSource = { configRepo.get() },
          keyLoader = { provider -> secureStorage.loadString(secureKeyFor(provider)) },
          onDeviceProvider = onDeviceProvider,
          claude = claude,
          gemini = gemini,
          openai = openai,
      )

      override suspend fun active(): LlmProvider? {
          val cfg = configSource()
          return when (cfg.engineMode) {
              EngineMode.ON_DEVICE -> onDeviceProvider?.takeIf { it.isAvailable() }
              EngineMode.CLOUD -> activeCloud(cfg)
          }
      }

      private suspend fun activeCloud(cfg: CaptureConfig): LlmProvider? {
          if (cfg.cloudConsentAt == null) return null
          val provider = cfg.cloudProvider ?: return null
          if (keyLoader(provider).isNullOrBlank()) return null
          val adapter = when (provider) {
              CloudProvider.CLAUDE -> claude
              CloudProvider.GEMINI -> gemini
              CloudProvider.OPENAI -> openai
          }
          return adapter.takeIf { it.isAvailable() }
      }
  }
  ```

- [ ] **Step 11.5 — Run it (expect PASS).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest --tests "app.hisaab.llm.DefaultLlmRouterTest"
  ```
  Expected: **PASS**.

- [ ] **Step 11.6 — Commit.**
  ```bash
  git add composeApp/src/commonMain/kotlin/app/hisaab/llm/DefaultLlmRouter.kt composeApp/src/commonTest/kotlin/app/hisaab/llm/DefaultLlmRouterTest.kt composeApp/src/commonTest/kotlin/app/hisaab/llm/support/FakeProviders.kt
  git commit -m "feat: add DefaultLlmRouter with cloud consent + key gate and on-device fallback"
  ```

---

### Task 12: Android on-device provider — `ModelManager` + `AndroidOnDeviceProvider`

**Files:**
- Create: `/Users/xack/projects/finance-app/composeApp/src/androidMain/kotlin/app/hisaab/llm/ModelManager.kt`
- Create: `/Users/xack/projects/finance-app/composeApp/src/androidMain/kotlin/app/hisaab/llm/AndroidOnDeviceProvider.kt`
- Modify: `/Users/xack/projects/finance-app/composeApp/src/androidMain/kotlin/app/hisaab/llm/OnDeviceProvider.android.kt`

These are Android-only and depend on MediaPipe `LlmInference`, which cannot run on a JVM unit-test host — verified by build compilation here and the instrumented test in Task 13. `createOnDeviceProvider()` needs an Android `Context`; it is supplied via a process-global set from `HisaabApplication`/AppContainer (matching how `SecureStorage(context)` etc. are constructed). We add a minimal `AndroidLlmContext` holder.

- [ ] **Step 12.1 — Create `ModelManager.kt`.** Availability ladder: AICore/Gemini Nano presence (class probe) → MediaPipe Gemma model file present in app files dir → else unavailable. (Download/WorkManager UI is M3-5; here we only detect a pre-placed model file.)
  ```kotlin
  package app.hisaab.llm

  import android.content.Context
  import java.io.File

  /**
   * Resolves on-device model availability for the Android on-device provider.
   *
   * Tier ladder (spec §8):
   *  1. Gemini Nano via AICore — detected by presence of the AICore client class.
   *     (Full Nano inference path is a follow-up; this slice only probes availability.)
   *  2. Gemma 3 1B (int4) via MediaPipe — detected by the model .task file in app files.
   *  3. Neither -> unavailable (router falls through to cloud/template).
   */
  class ModelManager(private val context: Context) {

      /** Gemma model file name (downloaded on-demand in M3-5; pre-placed for now). */
      val gemmaModelFile: File
          get() = File(context.filesDir, GEMMA_FILE)

      fun isAiCorePresent(): Boolean = runCatching {
          Class.forName("com.google.android.gms.ai.aicore.GenerativeModel")
          true
      }.getOrDefault(false)

      fun isGemmaPresent(): Boolean = gemmaModelFile.exists() && gemmaModelFile.length() > 0L

      /** Any on-device path usable right now. */
      fun isAnyModelAvailable(): Boolean = isGemmaPresent() || isAiCorePresent()

      companion object {
          const val GEMMA_FILE = "gemma-3-1b-it-int4.task"
      }
  }
  ```

- [ ] **Step 12.2 — Create `AndroidOnDeviceProvider.kt`.** Uses MediaPipe `LlmInference` over the Gemma model; builds the short prompt; decodes via `LlmJson`. Inference and engine creation are lazy and guarded.
  ```kotlin
  package app.hisaab.llm

  import android.content.Context
  import app.hisaab.domain.Category
  import com.google.mediapipe.tasks.genai.llminference.LlmInference
  import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
  import kotlinx.coroutines.Dispatchers
  import kotlinx.coroutines.withContext

  /**
   * On-device LlmProvider backed by MediaPipe LLM Inference (Gemma 3 1B int4 .task).
   * isAvailable() is true only when a usable model file is present. parse() runs the
   * short prompt and decodes the JSON reply with the shared LlmJson validator.
   *
   * SDK: confirm via Context7. Current best-guess: tasks-genai 0.10.24.
   */
  class AndroidOnDeviceProvider(
      private val context: Context,
      private val modelManager: ModelManager,
      private val redact: Boolean = true,
  ) : LlmProvider {

      override val id = ProviderId.ON_DEVICE

      @Volatile private var engine: LlmInference? = null

      override suspend fun isAvailable(): Boolean = modelManager.isGemmaPresent()

      private fun engineOrNull(): LlmInference? {
          engine?.let { return it }
          if (!modelManager.isGemmaPresent()) return null
          return synchronized(this) {
              engine ?: runCatching {
                  val options = LlmInferenceOptions.builder()
                      .setModelPath(modelManager.gemmaModelFile.absolutePath)
                      .setMaxTokens(1024)
                      .build()
                  LlmInference.createFromOptions(context, options)
              }.getOrNull()?.also { engine = it }
          }
      }

      override suspend fun parse(req: ParseRequest): LlmParseResult = withContext(Dispatchers.Default) {
          val inference = engineOrNull() ?: throw LlmException(LlmError.Unavailable)
          val text = if (redact) Redactor.redact(req.text) else req.text
          val prompt = buildString {
              append(Prompts.extractionSystemShort(req.categories))
              appendLine()
              req.senderHint?.let { appendLine("Sender: $it") }
              append("SMS: \"$text\"")
          }
          val raw = runCatching { inference.generateResponse(prompt) }
              .getOrElse { throw LlmException(LlmError.Decode(it.message ?: "inference failed")) }
          LlmJson.decodeResult(raw)
      }

      override suspend fun categorize(merchant: String, categories: List<Category>): String? =
          withContext(Dispatchers.Default) {
              val inference = engineOrNull() ?: return@withContext null
              val raw = runCatching { inference.generateResponse(Prompts.categorize(merchant, categories)) }
                  .getOrNull() ?: return@withContext null
              LlmJson.decodeCategoryId(raw, categories.map { it.id }.toSet())
          }
  }
  ```

- [ ] **Step 12.3 — Replace the Android actual to wire a real provider via a context holder.** Edit `OnDeviceProvider.android.kt`:
  ```kotlin
  package app.hisaab.llm

  import android.content.Context

  /**
   * Process-global Application context for the on-device provider. Set once from
   * AppContainerAndroid before the router is built (the app context outlives all
   * lock/unlock cycles, so a static reference is safe and matches how other Android
   * platform deps receive their Context).
   */
  object AndroidLlmContext {
      @Volatile var appContext: Context? = null
  }

  actual fun createOnDeviceProvider(): LlmProvider? {
      val ctx = AndroidLlmContext.appContext ?: return null
      val manager = ModelManager(ctx)
      // Only expose the provider when an on-device model is actually usable.
      return if (manager.isAnyModelAvailable()) {
          AndroidOnDeviceProvider(ctx, manager)
      } else {
          null
      }
  }
  ```

- [ ] **Step 12.4 — Verify Android compiles (MediaPipe symbols resolve).**
  ```bash
  ./gradlew :composeApp:compileDebugKotlinAndroid
  ```
  Expected: **PASS** (MediaPipe + AICore classes resolve from the deps added in Task 1).

- [ ] **Step 12.5 — Verify common unit tests still green (no JVM dependence on MediaPipe).**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest
  ```
  Expected: **PASS** (the on-device classes are androidMain-only; `createOnDeviceProvider()` returns null on the unit-test host because `AndroidLlmContext.appContext` is unset).

- [ ] **Step 12.6 — Commit.**
  ```bash
  git add composeApp/src/androidMain/kotlin/app/hisaab/llm/ModelManager.kt composeApp/src/androidMain/kotlin/app/hisaab/llm/AndroidOnDeviceProvider.kt composeApp/src/androidMain/kotlin/app/hisaab/llm/OnDeviceProvider.android.kt
  git commit -m "feat: add Android on-device LLM provider (MediaPipe Gemma + AICore availability gate)"
  ```

---

### Task 13: Android instrumented test for the on-device provider (skips if model absent)

**Files:**
- Create: `/Users/xack/projects/finance-app/composeApp/src/androidInstrumentedTest/kotlin/app/hisaab/llm/AndroidOnDeviceProviderTest.kt`

- [ ] **Step 13.1 — Write the instrumented test.** It uses `org.junit.Assume` to skip cleanly when no Gemma model is present on the device.
  ```kotlin
  package app.hisaab.llm

  import androidx.test.core.app.ApplicationProvider
  import androidx.test.ext.junit.runners.AndroidJUnit4
  import app.hisaab.domain.Category
  import kotlinx.coroutines.runBlocking
  import org.junit.Assume.assumeTrue
  import org.junit.Test
  import org.junit.runner.RunWith
  import kotlin.test.assertNotNull
  import kotlin.test.assertTrue

  @RunWith(AndroidJUnit4::class)
  class AndroidOnDeviceProviderTest {

      private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

      private val categories = listOf(
          Category("food", "Food & dining", null, null, null, true),
          Category("salary", "Salary", null, null, null, true),
      )

      @Test
      fun availabilityProbeNeverThrows() {
          val manager = ModelManager(context)
          // Probe must be safe to call regardless of device.
          manager.isAnyModelAvailable()
          manager.isAiCorePresent()
      }

      @Test
      fun parsesWhenModelPresent() = runBlocking {
          val manager = ModelManager(context)
          assumeTrue("Gemma model not present on device; skipping on-device parse", manager.isGemmaPresent())

          val provider = AndroidOnDeviceProvider(context, manager)
          assertTrue(provider.isAvailable())
          val result = provider.parse(
              ParseRequest(
                  text = "You have received Tk 1500 from 017XXXXXXXX. TrxID 9AB12CD34",
                  senderHint = "bKash",
                  categories = categories,
              ),
          )
          assertNotNull(result)
          // A money SMS should parse as financial; exact fields depend on the model.
          assertTrue(result.isFinancial)
      }

      @Test
      fun createOnDeviceProviderReturnsNullWithoutContextOrModel() {
          // With no model placed and no global context set, factory returns null.
          AndroidLlmContext.appContext = null
          assertTrue(createOnDeviceProvider() == null)
      }
  }
  ```

- [ ] **Step 13.2 — Verify the instrumented test compiles (emulator not required to compile).**
  ```bash
  ./gradlew :composeApp:compileDebugAndroidTestKotlinAndroid
  ```
  Expected: **PASS**. (Full run is `./gradlew :composeApp:connectedDebugAndroidTest` on an emulator/device; the model-dependent test self-skips via `assumeTrue` when no model is present.)

- [ ] **Step 13.3 — Commit.**
  ```bash
  git add composeApp/src/androidInstrumentedTest/kotlin/app/hisaab/llm/AndroidOnDeviceProviderTest.kt
  git commit -m "test: add androidInstrumentedTest for on-device LLM provider (skips if model absent)"
  ```

---

### Task 14: Wire `DefaultLlmRouter` into `AppContainer`, replacing `NoOpLlmRouter`

**Files:**
- Modify: `/Users/xack/projects/finance-app/composeApp/src/commonMain/kotlin/app/hisaab/AppContainer.kt`
- Modify: `/Users/xack/projects/finance-app/composeApp/src/androidMain/kotlin/app/hisaab/AppContainerAndroid.kt`
- Modify: `/Users/xack/projects/finance-app/composeApp/src/iosMain/kotlin/app/hisaab/AppContainerIos.kt`
- Modify: `/Users/xack/projects/finance-app/composeApp/src/wasmJsMain/kotlin/app/hisaab/AppContainerWasm.kt`

M3-3 wired `CapturePipeline` with `NoOpLlmRouter`. This task replaces that with `DefaultLlmRouter`. The exact M3-3 lines (how the Ktor `HttpClient`, `captureConfigRepository`, and `capturePipeline` are constructed) are defined in M3-1/M3-3; the snippets below assume the names from the shared contract. Adjust the `requireDb()`/repo accessor names to whatever M3-1/M3-3 introduced if they differ.

- [ ] **Step 14.1 — Add the `llmRouter` expect getter.** In `AppContainer.kt`, inside the `expect class AppContainer { … }`, after the existing repo declarations (e.g. after the M3 capture repos added by M3-1/M3-3), add:
  ```kotlin
      val llmRouter: app.hisaab.llm.LlmRouter
  ```
  Keep it grouped with the other M3 capture members.

- [ ] **Step 14.2 — Wire Android actual.** In `AppContainerAndroid.kt`:

  Add imports near the top (after existing imports):
  ```kotlin
  import app.hisaab.llm.AndroidLlmContext
  import app.hisaab.llm.DefaultLlmRouter
  import app.hisaab.llm.LlmRouter
  import app.hisaab.llm.cloud.ClaudeProvider
  import app.hisaab.llm.cloud.GeminiProvider
  import app.hisaab.llm.cloud.OpenAiProvider
  import app.hisaab.llm.createOnDeviceProvider
  import io.ktor.client.HttpClient
  import io.ktor.client.engine.okhttp.OkHttp
  ```

  In the constructor body (e.g. right after `actual val lifecycle = ...`), set the process-global context the on-device factory needs:
  ```kotlin
      init {
          AndroidLlmContext.appContext = context.applicationContext
      }
  ```

  Add a shared LLM `HttpClient` field (OkHttp engine on Android):
  ```kotlin
      private val llmHttpClient: HttpClient = HttpClient(OkHttp)
  ```

  Add the `llmRouter` actual getter, reading each key from `secureStorage` via the `llm_api_key_<PROVIDER>` convention (delegated by `DefaultLlmRouter` itself, so providers receive a key lambda):
  ```kotlin
      actual val llmRouter: LlmRouter
          get() = DefaultLlmRouter(
              configRepo = captureConfigRepository,
              secureStorage = secureStorage,
              onDeviceProvider = createOnDeviceProvider(),
              claude = ClaudeProvider(llmHttpClient, apiKey = { secureStorage.loadString("llm_api_key_CLAUDE") }),
              gemini = GeminiProvider(llmHttpClient, apiKey = { secureStorage.loadString("llm_api_key_GEMINI") }),
              openai = OpenAiProvider(llmHttpClient, apiKey = { secureStorage.loadString("llm_api_key_OPENAI") }),
          )
  ```

  Replace the M3-3 `NoOpLlmRouter` reference in the `capturePipeline` getter with `llmRouter`. The M3-3 getter uses the canonical (R4) `CapturePipeline` constructor — `db` first, `captureEvents` last, **no** `merchantRepo`:
  ```kotlin
      // BEFORE (M3-3):
      actual val capturePipeline: CapturePipeline
          get() = CapturePipeline(
              db = requireDb(),
              inboxRepo = captureInboxRepository,
              senderRepo = senderRepository,
              accountMatcher = accountMatcher,
              preFilter = smsPreFilter,
              llmRouter = NoOpLlmRouter,            // <-- replace this
              txnRepo = transactionRepository,
              configRepo = captureConfigRepository,
              captureEvents = captureEventBus,      // the MutableSharedFlow<CaptureEvent> behind AppContainer.captureEvents
          )
  ```
  Change `llmRouter = NoOpLlmRouter` to `llmRouter = llmRouter`, and remove the now-unused `NoOpLlmRouter` import. Leave every other argument (including `db`, `captureEvents`) exactly as M3-3 wired it.

- [ ] **Step 14.3 — Wire iOS actual.** In `AppContainerIos.kt`, add imports:
  ```kotlin
  import app.hisaab.llm.DefaultLlmRouter
  import app.hisaab.llm.LlmRouter
  import app.hisaab.llm.cloud.ClaudeProvider
  import app.hisaab.llm.cloud.GeminiProvider
  import app.hisaab.llm.cloud.OpenAiProvider
  import app.hisaab.llm.createOnDeviceProvider
  import io.ktor.client.HttpClient
  import io.ktor.client.engine.darwin.Darwin
  ```
  Add the shared client and getter (Darwin engine on iOS), `createOnDeviceProvider()` returns null here:
  ```kotlin
      private val llmHttpClient: HttpClient = HttpClient(Darwin)

      actual val llmRouter: LlmRouter
          get() = DefaultLlmRouter(
              configRepo = captureConfigRepository,
              secureStorage = secureStorage,
              onDeviceProvider = createOnDeviceProvider(),
              claude = ClaudeProvider(llmHttpClient, apiKey = { secureStorage.loadString("llm_api_key_CLAUDE") }),
              gemini = GeminiProvider(llmHttpClient, apiKey = { secureStorage.loadString("llm_api_key_GEMINI") }),
              openai = OpenAiProvider(llmHttpClient, apiKey = { secureStorage.loadString("llm_api_key_OPENAI") }),
          )
  ```
  Replace `NoOpLlmRouter` with `llmRouter` in the `capturePipeline` getter exactly as in Step 14.2.

- [ ] **Step 14.4 — Wire wasm actual.** In `AppContainerWasm.kt`, the capture pipeline has no SMS source, but the router member must still exist to satisfy the expect. Use a no-engine client is unnecessary; since wasm capabilities are empty, return a router with null on-device and cloud providers built over a JS-engine client. To keep wasm minimal and avoid adding a wasm Ktor engine dependency, expose `llmRouter` as a `DefaultLlmRouter` whose providers are never reachable (capture disabled on wasm). Add imports:
  ```kotlin
  import app.hisaab.llm.DefaultLlmRouter
  import app.hisaab.llm.LlmRouter
  import app.hisaab.llm.cloud.ClaudeProvider
  import app.hisaab.llm.cloud.GeminiProvider
  import app.hisaab.llm.cloud.OpenAiProvider
  import app.hisaab.llm.createOnDeviceProvider
  import io.ktor.client.HttpClient
  ```
  Add:
  ```kotlin
      // wasmJs is a viewer target: no capture, but the router member is required by
      // the expect. A default HttpClient() picks the available JS engine; it is never
      // actually called because CaptureService has no capabilities on wasm.
      private val llmHttpClient: HttpClient = HttpClient()

      actual val llmRouter: LlmRouter
          get() = DefaultLlmRouter(
              configRepo = captureConfigRepository,
              secureStorage = secureStorage,
              onDeviceProvider = createOnDeviceProvider(),
              claude = ClaudeProvider(llmHttpClient, apiKey = { secureStorage.loadString("llm_api_key_CLAUDE") }),
              gemini = GeminiProvider(llmHttpClient, apiKey = { secureStorage.loadString("llm_api_key_GEMINI") }),
              openai = OpenAiProvider(llmHttpClient, apiKey = { secureStorage.loadString("llm_api_key_OPENAI") }),
          )
  ```
  Replace `NoOpLlmRouter` with `llmRouter` in the `capturePipeline` getter (if M3-3 wired a pipeline on wasm; if it did not, skip the pipeline edit on wasm).
  > If `HttpClient()` with no explicit engine fails to compile on wasm (no engine on classpath), add `implementation(libs.ktor.client.js)` to the `named("wasmJsMain").dependencies` block in `build.gradle.kts` (and a `ktor-client-js` catalog entry), or, simpler, keep wasm's `llmRouter` providers constructed lazily so the engine is only resolved when used. Prefer the lazy approach to avoid shipping an unused engine.

- [ ] **Step 14.5 — Verify common unit tests + all targets compile.**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest
  ./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileKotlinWasmJs :composeApp:compileDebugKotlinAndroid
  ```
  Expected: **PASS** for all (no missing-actual errors; `NoOpLlmRouter` no longer referenced; `CapturePipeline` now receives `DefaultLlmRouter`).

- [ ] **Step 14.6 — Commit.**
  ```bash
  git add composeApp/src/commonMain/kotlin/app/hisaab/AppContainer.kt composeApp/src/androidMain/kotlin/app/hisaab/AppContainerAndroid.kt composeApp/src/iosMain/kotlin/app/hisaab/AppContainerIos.kt composeApp/src/wasmJsMain/kotlin/app/hisaab/AppContainerWasm.kt composeApp/build.gradle.kts gradle/libs.versions.toml
  git commit -m "feat: wire DefaultLlmRouter into AppContainer, replacing NoOpLlmRouter so CapturePipeline uses real LLMs"
  ```

---

### Task 15: Full verification

**Files:** none (verification only).

- [ ] **Step 15.1 — Run the full common unit-test suite.**
  ```bash
  ./gradlew :composeApp:testDebugUnitTest
  ```
  Expected: **PASS** — all of: `LlmErrorTest`, `RedactorTest`, `PromptsTest`, `LlmJsonTest`, `CloudHttpTest`, `ClaudeProviderTest`, `GeminiProviderTest`, `OpenAiProviderTest`, `DefaultLlmRouterTest`, `OnDeviceProviderTest`, plus all pre-existing M3-1/M3-2/M3-3 and P0c tests.

- [ ] **Step 15.2 — Assemble the debug APK (Android target with MediaPipe + on-device provider).**
  ```bash
  ./gradlew :composeApp:assembleDebug
  ```
  Expected: **PASS** (MediaPipe/AICore deps link; no R8 issues — release minify is off per build config).

- [ ] **Step 15.3 — Compile iOS + wasm targets (KMP completeness).**
  ```bash
  ./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileKotlinWasmJs
  ```
  Expected: **PASS**.

- [ ] **Step 15.4 — (Optional, requires emulator) Run instrumented on-device test.**
  ```bash
  ./gradlew :composeApp:connectedDebugAndroidTest --tests "app.hisaab.llm.AndroidOnDeviceProviderTest"
  ```
  Expected: **PASS** — `availabilityProbeNeverThrows` and `createOnDeviceProviderReturnsNullWithoutContextOrModel` pass unconditionally; `parsesWhenModelPresent` self-skips (`assumeTrue`) unless a Gemma model file is present in the app files dir.

- [ ] **Step 15.5 — Final review against scope.** Confirm: `Redactor` (account last-4 / phone / name masking), `Prompts` (extraction full + short on-device variant + categorize, all constrained to the 12 ids with BD few-shot), three cloud adapters using each vendor's native structured output decoding into one `LlmParseResult` via `LlmJson`, 15 s timeout + error mapping (401/403→invalid key, 429→retryable, 5xx→retryable provider error), `createOnDeviceProvider()` (Android MediaPipe Gemma + AICore probe; ios/wasm null), `DefaultLlmRouter` with consent + key gate, and `DefaultLlmRouter` wired into `AppContainer` replacing `NoOpLlmRouter`. All keys read from `SecureStorage` as `llm_api_key_<PROVIDER>`. MockEngine tests cover request body shape per vendor + response decode + error mapping; `Redactor`/`Prompts`/`LlmJson` are pure-unit tested; on-device covered by a self-skipping instrumented test.

- [ ] **Step 15.6 — Push the branch (only if the user asks).**
  ```bash
  git push -u origin m3-4-llm-providers
  ```

---

**Implementation notes for the executing engineer**

- **Confirm model IDs/SDK versions via Context7 before merge** (see the boxed note after File Structure). The plan ships runnable best-guess values: `claude-3-5-haiku-latest`, `gemini-2.0-flash`, `gpt-4o-mini`, MediaPipe `tasks-genai:0.10.24` with `gemma-3-1b-it-int4.task`, AICore `play-services-aicore:16.0.0-alpha05`. If any artifact version fails to resolve in Task 1, update the catalog and re-run before continuing.
- **No `java.util.UUID`** is introduced; this slice creates no DB ids (it has no repo writes of its own — it only reads config and routes).
- **No mutation**: providers are stateless except the lazily-cached MediaPipe `engine` (a platform resource handle, not domain state).
- **Security**: SMS bodies and API keys are never logged anywhere in this slice; keys are read on demand via `() -> String?` lambdas and never held in fields. `Redactor` runs inside every cloud `parse()` before the network call (toggleable via the `redact` ctor flag, defaulted on; M3-5 will pass `CaptureConfig.redactionEnabled` through if desired).
- **The M3-3 contract lines** referenced in Task 14 (`captureConfigRepository`, `capturePipeline`, `NoOpLlmRouter`, `CapturePipeline(...)` argument names) come from M3-1/M3-3. If their actual member names differ at execution time, adapt the wiring snippet to match — the only hard requirement is that `CapturePipeline` is constructed with `llmRouter = llmRouter` (the new `DefaultLlmRouter`) instead of `NoOpLlmRouter`.

Plan file is not written to disk per instructions; the complete markdown plan is the message above. Relevant absolute paths it will create/modify are all rooted at `/Users/xack/projects/finance-app/composeApp/src/{commonMain,androidMain,iosMain,wasmJsMain,commonTest,androidInstrumentedTest}/kotlin/app/hisaab/llm/` plus `/Users/xack/projects/finance-app/gradle/libs.versions.toml` and `/Users/xack/projects/finance-app/composeApp/build.gradle.kts`.
