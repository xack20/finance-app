# Hisaab — Project Context & Page Catalog

> **Purpose of this doc:** a complete redesign brief. It captures what Hisaab is, the current
> design system, the navigation/app-state model, and **every page** with its use case, states,
> and key UI elements. Pair it with the screenshots in `docs/design/screenshots/android/`.
>
> _Generated from source analysis of `composeApp/src/commonMain`._

---

## 1. What Hisaab is

**Hisaab** (হিসাব — "accounts / reckoning") is a **privacy-first personal-finance app for Bangladesh**.
The headline promise is *"Your money, only yours."* Everything stays on the device: transactions live
in a **SQLCipher-encrypted** local database whose key derives from a user **master secret**, protected
by **device biometrics** and a **24-word recovery phrase**. There is no server-side copy of financial data.

**Core capabilities**
- **Capture** — manual entry plus *automatic* capture. On Android, incoming bank/MFS SMS (bKash, Nagad,
  Rocket, bank alerts) flow through a **tiered parsing pipeline** (deterministic bank templates →
  on-device LLM → optional bring-your-own-key cloud LLM). High-confidence results **auto-post**; the rest
  land in a **Review inbox**.
- **Assistant** — a conversational money agent that answers questions and *proposes ledger writes*
  (set budget, recategorize, add split, transfers). The user reviews proposed writes before they apply.
- **Ledger** — accounts (cash / bank / card / MFS), categories, budgets, people & lend-borrow tracking,
  and monthly **insight charts**.

**Platforms:** Kotlin Multiplatform + Compose Multiplatform.
- **Android** — primary, fully featured (SMS capture, biometric, on-device LLM).
- **iOS** — builds + runs; SMS capture is unavailable (Apple restriction) so intake is paste/share-sheet.
- **wasm** — gated off (no wasm crypto artifact upstream).

---

## 2. Design system (current — "Midnight")

The current visual language is **Midnight** — a dark, neo-bank aesthetic: a near-black ink canvas, an
electric-lime accent, a geometric grotesk display face for headlines and amounts, and monospace tabular
figures for money. Bengali (the হিসাব wordmark, ৳ glyphs) is covered by bundled Hind Siliguri / Noto Sans
Bengali faces. `HisaabTheme { }` provides `LocalHisaabPalette` + a Material 3 theme; **dark is the shipped
default** and a derived light variant exists (AA-tuned in `ContrastTest`). Screenshots are captured in dark
Midnight. Source of truth: `HisaabColors.kt` · `HisaabShapes.kt` · `HisaabTypography.kt`.

### Color palette

| Token | Role | Dark (default) | Light (derived) |
|---|---|---|---|
| `background` | app canvas (ink) | `#0A0B0E` | `#F6F8FB` |
| `surface` | cards / sheets | `#161922` | `#FFFFFF` |
| `onBackground` | primary text | `#F3F5F8` | `#13161B` |
| `muted` | secondary text | `#98A0AD` | `#5A6573` |
| `faint` | tertiary text (AA-tuned) | `#737B88` | `#79828F` |
| `hair` | hairline border (α over bg) | white @ 7% | ink @ 8% |
| `accent` | brand / primary actions (lime) | `#CBF24A` | `#4E6A10` (dimmed for AA text) |
| `accentDim` | hover / pressed accent | `#A9CE37` | `#5E7E12` |
| `onAccent` | text on the lime fill | `#0A0B0E` | `#0A0B0E` |
| `positive` | income / credit | `#46E08A` | `#18854A` |
| `negative` | expense / warnings | `#FF6B5C` | `#C2392A` |
| `rule`, `gold` | legacy roles (being retired) | `#242833`, `#A9CE37` | `#DDE2E8`, `#5E7E12` |

### Typography

| Style | Family | Size / weight | Use |
|---|---|---|---|
| `heroAmount` | Space Grotesk (display) | 50sp SemiBold, -1.5 tracking | hero amounts / big numbers |
| `title` | Space Grotesk (display) | 30sp SemiBold, -0.6 tracking | screen headlines |
| `body` | Hanken Grotesk (ui) | 15sp Normal | body copy |
| `eyebrow` / `label` | Hanken Grotesk (ui) | 11sp Medium/SemiBold, +1.5 tracking | eyebrow labels |
| `tabular` | Space Mono (mono), `tnum lnum` | 16sp Bold | money figures, aligned columns |

> Families are real bundled fonts (OFL 1.1, see `composeApp/THIRD_PARTY_FONT_LICENSES.md`), built in
> composition via `HisaabTypography.families()`: display = Space Grotesk, ui = Hanken Grotesk, mono =
> Space Mono; each carries a Bengali face (Hind Siliguri / Noto Sans Bengali) at a matching weight.

### Shapes

- `field` = 16dp · `card` = 22dp · `sheet` = 26dp top corners · `pill` = fully rounded (999dp).
- Material 3 mapping: small = `field` (16dp) · medium = `card` (22dp) · large = 26dp.

### Recurring UI idioms

- **Eyebrow label** (tiny tracked accent text) above a grotesk headline on most screens.
- **Hairline-bordered surface cards** (`rule` border, `surface` fill) rather than heavy shadows.
- **Full-width pill/rounded primary buttons**, accent fill, disabled until inputs valid.
- **Inline `CircularProgressIndicator`** inside buttons for loading.
- Money rendered with the **mono tabular** style; income `positive`, expense `negative`.

---

## 3. Navigation & app-state model

The **root is state-driven** — there is *no* navigation library at the root. `AppViewModel` exposes a
sealed `AppState`, and `App.kt` switches on it inside `HisaabTheme`:

| `AppState` | Renders | Notes |
|---|---|---|
| `Loading` | `SplashScreen` | initial determination |
| `Unauthenticated` / `Onboarding(step)` | `OnboardingGraph` | first-run funnel |
| `OnboardingKey` | `RecoveryEntryScreen` | signed-in but no local key → restore from 24 words |
| `Locked` | `LockScreen` | biometric unlock; opens the encrypted DB |
| `Authenticated` | `MainGraph` | the app shell (only reachable with an open DB) |

**Gating logic** (`AppViewModel.init`): not signed in → onboarding funnel; signed in but no master
secret on device → recovery-entry; signed in **with** a master secret → **Locked** (a cold start *always*
locks, because the SQLCipher DB is closed and the key only lives in memory while unlocked). Two paths open
the DB and go straight to `Authenticated`: finishing onboarding (`completeProfile`) and a successful unlock.
**Auto-lock:** 30s in the background closes the DB and returns to `Locked`.

**Onboarding sub-flow** (`OnboardingGraph`, internal NavHost): `welcome → otp → biometric →
recovery_phrase → profile`.

**Authenticated shell** (`MainGraph`, internal NavHost): bottom-nav tabs **Today / Month / People**, plus
**Assistant** and **Settings**, plus pushed routes `entry`, `txn/{id}`, `review`, `person/{id}`,
`settings/{accounts,categories,budgets,auto-capture,auto-capture/consent,recovery}`.

---

## 4. Page catalog

Pages are grouped by area. For each: **purpose**, **use case** (when the user sees it / what they do),
**key UI elements**, and **states** worth designing for. `auth` = only reachable after unlock.


### Contents

- **Onboarding & first run**
  - [WelcomeScreen](#welcomescreen)
  - [OtpScreen](#otpscreen)
  - [BiometricSetupScreen](#biometricsetupscreen)
  - [RecoveryPhraseScreen](#recoveryphrasescreen)
  - [ProfileSetupScreen](#profilesetupscreen)
  - [CaptureOptInCard](#captureoptincard)
- **System, navigation & lock**
  - [SplashScreen](#splashscreen)
  - [OnboardingGraph (Welcome entry)](#onboardinggraph-welcome-entry)
  - [RecoveryEntryScreen](#recoveryentryscreen)
  - [LockScreen](#lockscreen)
  - [MainGraph (authenticated shell)](#maingraph-authenticated-shell)
- **Home — Today & Month**
  - [Today](#today)
  - [ReviewBadge](#reviewbadge)
  - [Month](#month)
  - [CategoryBarChart](#categorybarchart)
  - [PerDayLineChart](#perdaylinechart)
  - [BudgetProgressList](#budgetprogresslist)
  - [RecurringList](#recurringlist)
  - [CaptureOptInCard](#captureoptincard)
- **Entry & transaction detail**
  - [EntryScreen (New Entry)](#entryscreen-new-entry)
  - [SplitEditorSheet](#spliteditorsheet)
  - [PersonPickerSheet](#personpickersheet)
  - [TransactionDetailScreen](#transactiondetailscreen)
- **Settings**
  - [SettingsScreen](#settingsscreen)
  - [LockTimeoutSheet](#locktimeoutsheet)
  - [AccountsScreen](#accountsscreen)
  - [AddAccountSheet](#addaccountsheet)
  - [CategoriesScreen](#categoriesscreen)
  - [AddCategorySheet](#addcategorysheet)
  - [BudgetsScreen](#budgetsscreen)
  - [AddBudgetSheet](#addbudgetsheet)
  - [AutoCaptureScreen](#autocapturescreen)
  - [EnginePicker](#enginepicker)
  - [AddSenderInline](#addsenderinline)
  - [CloudConsentScreen](#cloudconsentscreen)
  - [RecoveryPhraseRevealScreen](#recoveryphraserevealscreen)
  - [SettingRow (shared component)](#settingrow-shared-component)
- **Assistant, capture review & people**
  - [Assistant Chat (AgentScreen)](#assistant-chat-agentscreen)
  - [Agent Consent Dialog](#agent-consent-dialog)
  - [Review Card (proposed writes)](#review-card-proposed-writes)
  - [Review Inbox (capture review)](#review-inbox-capture-review)
  - [Auto-Post Undo Snackbar Host](#auto-post-undo-snackbar-host)
  - [People List](#people-list)
  - [Person Detail](#person-detail)

_Total: 44 pages / components catalogued._

---


## Onboarding & first run

> **Area notes.** OnboardingGraph.kt is the host: a Compose NavHost with startDestination "welcome" and routes welcome -> otp -> biometric -> recovery_phrase -> profile. It is NOT itself a screenshot target — it wires the stateless screens to OnboardingViewModel (state: StateFlow<OnboardingState>) and owns navigation.

Navigation is partly imperative and partly side-effect-driven:
- welcome->otp and otp->biometric happen via LaunchedEffect blocks reacting to state.otpSent / state.otpVerified (set by sendOtp/verifyOtp on success). The screens' own callbacks do NOT navigate; they only call the ViewModel.
- biometric->recovery_phrase is explicit navController.navigate("recovery_phrase") inside enrollBiometric's onSuccess/onSkip and skipBiometric's onDone.
- recovery_phrase->profile is explicit navController.navigate("profile") after acknowledgePhraseWrittenDown().
- profile has no navTo route; completeProfile invokes the graph's onComplete() to exit the onboarding graph entirely (handed off to MainGraph elsewhere).

In the live graph BiometricSetupScreen is always passed isAvailable=true (the unavailable state is reachable only by overriding that arg in isolation). RecoveryPhraseScreen's words come from viewModel.generateRecoveryPhrase(), which returns emptyList() unless a master_secret was generated during biometric enroll — so in a real run the phrase only populates after the biometric step; for isolated rendering pass a literal 24-word list.

All six composables are stateless and renderable WITHOUT a ViewModel or DB: each takes plain primitives (String/Boolean/List/String?) plus lambda callbacks, and manages its transient input via internal remember. There is no separate XxxContent wrapper — the screen composables themselves are the stateless content layer. The only true ViewModel/DB dependencies (Supabase auth, CryptoService/MnemonicService, BiometricPrompt, encrypted SQLDelight DB via container.openDatabase) live entirely in OnboardingViewModel/OnboardingGraph and are bypassed when rendering the screens directly. OnboardingState (data class in OnboardingViewModel.kt) maps 1:1 to these params: phone, otpSent, otpVerified, biometricEnabled, recoveryPhrase, phraseAcknowledged, isLoading, error. Every screen must be wrapped in HisaabTheme { } so LocalHisaabPalette (HisaabColors.Palette with background/surface/onBackground/muted/rule/accent/negative) and HisaabSpacing.gutter (20.dp) resolve.


### WelcomeScreen
<a id="welcomescreen"></a>
**`welcome` · public · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/WelcomeScreen.kt`

**Purpose.** First-run landing: brand intro + phone number entry to begin phone-OTP signup.

**Use case.** Shown as the start destination of OnboardingGraph when there is no persisted master_secret. The user reads the privacy pitch, types a Bangladesh phone number (auto-prefixed +880), and taps Continue to request an OTP. It exists to capture the phone identity that anchors the account.

**Key UI elements.**
- Brand eyebrow 'হিসাব · Hisaab' in accent
- Display headline 'Your money, only yours.'
- Muted subtitle copy
- Optional error line (negative color)
- Phone OutlinedTextField with +880 prefix and placeholder '1X XXXX XXXX'
- Full-width 'Continue' button (disabled until >=10 digits) with inline CircularProgressIndicator when loading

**States to design.** empty (default, button disabled) · valid phone entered (button enabled) · loading (spinner in button) · error (e.g. 'Network error' shown above field)

**Navigation.** to OtpScreen


### OtpScreen
<a id="otpscreen"></a>
**`otp` · public · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/OtpScreen.kt`

**Purpose.** Enter the 6-digit OTP sent to the phone number to verify ownership.

**Use case.** Reached automatically after sendOtp succeeds (OnboardingGraph navigates here via a LaunchedEffect on state.otpSent). The user types the 6-digit code, taps Verify, or taps Resend code. Exists to confirm the user controls the phone number before provisioning the account.

**Key UI elements.**
- Accent eyebrow 'Verify'
- Headline 'Enter the code we sent you'
- Muted 'Sent to <phone>' line
- OTP OutlinedTextField (digits only, max 6, NumberPassword keyboard)
- Optional error line (negative)
- Full-width 'Verify' button (disabled until exactly 6 digits) with inline spinner when loading
- Centered 'Resend code' TextButton

**States to design.** awaiting input (default, Verify disabled) · 6 digits entered (Verify enabled) · loading (spinner in Verify button) · error (e.g. 'Invalid code')

**Navigation.** from WelcomeScreen; to BiometricSetupScreen


### BiometricSetupScreen
<a id="biometricsetupscreen"></a>
**`biometric` · public · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/BiometricSetupScreen.kt`

**Purpose.** Offer to enable biometric (face/finger) unlock; or skip.

**Use case.** Reached after OTP verification (LaunchedEffect on state.otpVerified). On Enroll the ViewModel generates a master_secret, prompts the platform BiometricPrompt, and persists the secret; the user can also Skip (not recommended). Exists to bind device biometrics to the on-device encryption key.

**Key UI elements.**
- Accent eyebrow 'Secure'
- Centered headline 'Unlock with your face or finger'
- Muted explanatory copy
- Optional error text (negative)
- Primary button reading 'Enable biometric' or 'Not available on this device' when isAvailable=false (disabled in that case)
- Centered 'Skip (not recommended)' TextButton

**States to design.** available (enroll button enabled) · unavailable (button shows 'Not available on this device', disabled) · loading (enroll disabled during prompt) · error (e.g. biometric hardware error message)

**Navigation.** from OtpScreen; to RecoveryPhraseScreen


### RecoveryPhraseScreen
<a id="recoveryphrasescreen"></a>
**`recovery_phrase` · public · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/RecoveryPhraseScreen.kt`

**Purpose.** Display the 24-word recovery mnemonic and require the user to confirm they wrote it down.

**Use case.** Reached after biometric enroll/skip. Shows the master_secret encoded as a 24-word BIP39-style mnemonic in a 2-column grid; the user must check the acknowledgement box before the 'I've saved them' button enables. Exists so the user can recover their on-device data — losing the words means losing the data.

**Key UI elements.**
- Accent eyebrow 'Recovery'
- Headline 'Write these 24 words down'
- Warning line '⚠ Lose these = lose your data' (negative)
- 2-column LazyVerticalGrid of numbered word chips (bordered surface cards)
- Acknowledgement Checkbox row 'I've written down all 24 words in a safe place'
- Full-width 'I've saved them' button (disabled until checkbox checked)

**States to design.** unacknowledged (checkbox off, button disabled) · acknowledged (checkbox on, button enabled) · empty words list (renders no chips — degenerate)

**Navigation.** from BiometricSetupScreen; to ProfileSetupScreen


### ProfileSetupScreen
<a id="profilesetupscreen"></a>
**`profile` · public · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/ProfileSetupScreen.kt`

**Purpose.** Final step: collect display name and language, then create the encrypted user profile.

**Use case.** Reached after the recovery phrase is acknowledged. The user enters a name and picks English or বাংলা, then taps 'Start Hisaab →' which triggers completeProfile (opens the encrypted DB, inserts UserProfile, and calls onComplete to leave onboarding). Exists to personalize the account and finalize setup.

**Key UI elements.**
- Accent eyebrow 'Almost done'
- Headline 'What should we call you?'
- Name OutlinedTextField
- 'Language' label
- Two-button language toggle row (English / বাংলা) where the selected one uses accent fill and the other surface fill
- Full-width 'Start Hisaab →' button (disabled until name non-blank) with inline spinner when loading

**States to design.** empty name (button disabled, English selected by default) · name entered (button enabled) · Bangla selected (বাংলা button accent-filled) · loading (spinner in button)

**Navigation.** from RecoveryPhraseScreen


### CaptureOptInCard
<a id="captureoptincard"></a>
**`embedded-component` · public · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/CaptureOptInCard.kt`

**Purpose.** Soft, skippable card offering automatic SMS-based transaction capture (bKash/Nagad/bank).

**Use case.** Not a full screen — an embedded card the host (onboarding completion screen or the Today screen, per MainGraph) shows once, gated by a 'capture_optin_seen' flag in SecureStorage. The user taps 'Turn on' to enable on-device SMS reading or 'Maybe later' to dismiss; both callbacks should set the seen flag so it never reappears. Exists to drive opt-in to the automatic logging feature without being intrusive.

**Key UI elements.**
- Rounded bordered surface card
- Title 'Log transactions automatically'
- Muted body copy describing on-device bKash/Nagad/bank SMS reading and privacy
- Row with accent 'Turn on' Button and muted 'Maybe later' TextButton

**States to design.** default (only renderable state — purely presentational, no internal state)


## System, navigation & lock

> **Area notes.** AUTH/LOCK STATE MACHINE (AppViewModel.kt, sealed class AppState: Loading, Unauthenticated, Onboarding(step), OnboardingKey, Locked, Authenticated). The ROOT switch lives in App.kt: HisaabTheme { ... when(state) { Loading->SplashScreen(); Unauthenticated|Onboarding->OnboardingGraph(vm, onComplete=onOnboardingComplete); OnboardingKey->RecoveryEntryScreen(onRecovered=onBiometricUnlockSuccess); Locked->LockScreen(onUnlock=onBiometricUnlockSuccess); Authenticated->MainGraph() } }. There is NO nav library at the root — state is the navigator. AppViewModel is built in a remember{} in App.kt with authRepository=container.authRepository, hasMasterSecret={ container.secureStorage.loadMasterSecret()!=null }, onLock={ container.closeDatabase() }, then .init().

WHAT GATES EACH SCREEN — AppViewModel.init() (runs on Dispatchers.Main scope.launch): if !authRepository.isSignedIn() -> Unauthenticated (Onboarding funnel); else if !hasMasterSecret() -> OnboardingKey (RecoveryEntryScreen); else -> Locked. IMPORTANT (matches the cold-start-lock-model memory): a returning signed-in user with a persisted master_secret ALWAYS routes to Locked, never straight to Authenticated, because on cold start the SQLCipher DB is closed — the master_secret only lives in memory while unlocked and is zeroed on background. Going straight to Authenticated would render MainGraph against a closed DB and crash (requireDb -> 'Database not open'). The two paths that DO go straight to Authenticated both open the DB inline first: onOnboardingComplete() (after OnboardingViewModel.completeProfile() calls container.openDatabase(secret)) and onBiometricUnlockSuccess() (after LockScreen.openAndContinue() / RecoveryEntryScreen.attemptRestore() call container.openDatabase). LOCK TIMEOUT: AppViewModel.onAppBackground() only arms if state is Authenticated; it cancels any prior lockJob, launches delay(lockTimeoutMs=30_000L), then onLock() (closeDatabase) and sets state=Locked. onAppForeground() cancels the pending lockJob. The lifecycle events feeding this come from container.lifecycle.events() collected in App.kt (LifecycleEvent.Background/Foreground).

ONBOARDING SUB-FLOW (OnboardingGraph.kt, internal rememberNavController NavHost, start 'welcome'): welcome(WelcomeScreen, onSendOtp=vm.sendOtp) -> otp(OtpScreen, onVerify=vm.verifyOtp, onResend) -> biometric(BiometricSetupScreen) -> recovery_phrase(RecoveryPhraseScreen, words=vm.generateRecoveryPhrase()) -> profile(ProfileSetupScreen, onComplete=vm.completeProfile{ onComplete() }). Navigation is driven reactively: LaunchedEffect(state.otpSent){ navigate('otp') } and LaunchedEffect(state.otpVerified){ navigate('biometric') }. OnboardingViewModel(container) holds the master_secret in a private var across steps (generated in enrollBiometric/skipBiometric, encoded to 24 BIP39 words in generateRecoveryPhrase, zeroed after completeProfile inserts UserProfile). Note completeProfile uses state.phone as a placeholder supabase_user_id.

CAN THE LIVE APP PASS THE SUPABASE PHONE-OTP GATE WITHOUT A REAL PHONE? NO. The production AuthRepository is SupabaseAuthRepository (auth/SupabaseAuthRepository.kt): sendOtp -> supabaseClient.auth.signInWith(OTP){ phone=... } (sends a real SMS via Supabase GoTrue), verifyOtp -> auth.verifyPhoneOtp(OtpType.Phone.SMS, phone, token) (requires the real SMS code), isSignedIn -> auth.currentSessionOrNull()!=null. So the live onboarding gate cannot be passed without a real phone receiving the SMS code — there is no dev/bypass backdoor in the repository. For TESTS this is bypassed entirely by FakeAuthRepository (commonTest/auth/FakeAuthRepository.kt): sendOtp returns Result.success unless otpError set; verifyOtp sets signedIn=true and returns success unless verifyError set; isSignedIn returns the in-memory flag. Inject FakeAuthRepository into AppViewModel/OnboardingViewModel to drive the state machine deterministically without any network/phone. This means screenshotting any post-OTP screen in an instrumented/live context requires either the fake repo or starting from an already-authenticated container.

EXACT INSTRUMENTED RENDER PATTERN (from androidInstrumentedTest/kotlin/app/hisaab/ui/AgentScreenTest.kt): class annotated @OptIn(ExperimentalTestApi::class); JUnit4 rule `@get:org.junit.Rule val composeRule = createComposeRule()`. Each test calls composeRule.setContent { CompositionLocalProvider(LocalHisaabPalette provides HisaabColors.Light) { <StatelessContentComposable>(state=..., callbacks...) } }. Note it does NOT use HisaabTheme{} — it provides LocalHisaabPalette directly with HisaabColors.Light (so MaterialTheme defaults apply, only the palette CompositionLocal is satisfied). Assertions use composeRule.onNodeWithText(\"...\").assertIsDisplayed(), onNodeWithTag(\"agent_send\").performClick() / onNodeWithTag(\"agent_mic\").assertIsNotEnabled() — testTags are explicit strings on the composables (e.g. 'agent_send', 'agent_mic'). captureToImage() is NOT used anywhere in this test (no screenshot/bitmap assertions); verification is purely semantics-tree (text + tag + enabled state). The key enabler is that AgentScreen has a stateless AgentScreenContent(state: AgentUiState, onInput, onSend, onMicTap, onNewChat, onConsent, onToggleInclude, onEditWrite, onApply, onClose) that renders from a plain data state with no ViewModel/DB. For THIS group, SplashScreen, WelcomeScreen, and OtpScreen follow the same fully-stateless pattern and can be rendered identically (provide LocalHisaabPalette, pass no-op callbacks + sample state). LockScreen and RecoveryEntryScreen do NOT have a stateless Content overload and read LocalAppContainer.current, so they additionally require CompositionLocalProvider(LocalAppContainer provides <fake container>); MainGraph requires a fully wired container with an OPEN DB and is not practically renderable in isolation.


### SplashScreen
<a id="splashscreen"></a>
**`AppState.Loading (root, not a nav route)` · public · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/SplashScreen.kt`

**Purpose.** Loading placeholder shown while AppViewModel.init() resolves the auth/lock state machine.

**Use case.** Shown for a brief moment on cold start while AppViewModel.init() runs the suspend isSignedIn()/hasMasterSecret() checks on Dispatchers.Main to decide the initial AppState. The user does nothing; it auto-replaces with Onboarding, RecoveryEntry, Lock, or Main. It exists so the app never flashes a wrong screen before the state machine resolves.

**Key UI elements.**
- Centered Bengali wordmark Text 'হিসাব' (accent color, 32sp)
- CircularProgressIndicator (accent color)
- Full-screen Box backed by palette.background

**States to design.** Loading (only state — static spinner)

**Navigation.** from app launch (initial AppState.Loading); to OnboardingGraph (Unauthenticated/Onboarding), RecoveryEntryScreen (OnboardingKey), LockScreen (Locked), MainGraph (Authenticated)


### OnboardingGraph (Welcome entry)
<a id="onboardinggraph-welcome-entry"></a>
**`AppState.Unauthenticated / AppState.Onboarding -> internal NavHost startDestination 'welcome'` · public · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/OnboardingGraph.kt`

**Purpose.** First-run sign-up funnel host; entry point is the WelcomeScreen phone-number capture.

**Use case.** Shown when AuthRepository.isSignedIn() is false (no Supabase session). The user enters a +880 phone number and taps Continue to request an SMS OTP. It exists as the front door of the privacy-first onboarding funnel (welcome -> otp -> biometric -> recovery_phrase -> profile). The internal NavHost advances on OnboardingState.otpSent/otpVerified flags via LaunchedEffect.

**Key UI elements.**
- WelcomeScreen: 'হিসাব · Hisaab' kicker, 'Your money, only yours.' display headline, OutlinedTextField with +880 prefix and phone placeholder
- Continue Button enabled only when phone.length>=10 && !isLoading
- Inline error Text (palette.negative) when state.error set
- OTP step (after send): code OutlinedTextField (<=6 digits), Verify button (enabled at exactly 6 digits), Resend code TextButton

**States to design.** Welcome idle (empty phone) · Welcome loading (isLoading=true, spinner in Continue button) · Welcome error (state.error non-null) · OTP entry idle · OTP loading · OTP error

**Navigation.** from SplashScreen (AppState.Unauthenticated/Onboarding); to OTP step (internal, on otpSent), biometric/recovery_phrase/profile steps (internal, post-verify), MainGraph (AppState.Authenticated via onOnboardingComplete after completeProfile)


### RecoveryEntryScreen
<a id="recoveryentryscreen"></a>
**`AppState.OnboardingKey (root, not a nav route)` · public · ViewModel-backed** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/recovery/RecoveryEntryScreen.kt`

**Purpose.** 24-word BIP39 recovery-phrase entry to re-derive the master_secret on a device that is signed in but has no stored key.

**Use case.** Shown when isSignedIn() is true but secureStorage.loadMasterSecret() is null (e.g. reinstall/restore where the Supabase session survived but the encrypted key did not). The user types their 24-word phrase; on Restore the words are validated against BIP39_WORDLIST, decoded to a master_secret, stored, biometric_enabled set false, and the SQLCipher DB is opened, then onRecovered() flips state to Authenticated.

**Key UI elements.**
- 'Recover' kicker + 'Enter your 24-word recovery phrase' headline + privacy subtext
- LazyColumn of 24 numbered OutlinedTextFields (label = 1..24)
- Inline error Text (blank-field / not-in-wordlist / invalid-phrase messages)
- Full-width Restore Button (spinner when isLoading)

**States to design.** Empty (24 blank fields) · Validation error: missing words · Validation error: words not in BIP39 wordlist · Restore loading (spinner) · Decode/checksum failure error

**Navigation.** from SplashScreen (AppState.OnboardingKey: signed in, no master_secret); to MainGraph (AppState.Authenticated via onBiometricUnlockSuccess after successful restore)


### LockScreen
<a id="lockscreen"></a>
**`AppState.Locked (root, not a nav route)` · public · ViewModel-backed** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/LockScreen.kt`

**Purpose.** Biometric (or key-only) unlock gate that loads the persisted master_secret and opens the encrypted DB.

**Use case.** Shown on cold start when a master_secret is persisted (AppState.Locked is the default for a returning signed-in user) and after the 30s background lock timeout fires (onAppBackground -> delay(lockTimeoutMs) -> onLock closes DB -> Locked). LaunchedEffect auto-prompts unlock on first composition. If biometric_enabled != 'true' it opens the DB directly; otherwise it runs container.biometricAuth.authenticate() and opens the DB on Success, then onUnlock() flips to Authenticated.

**Key UI elements.**
- 'Hisaab is locked' headline (headlineSmall)
- Inline error Text (palette.negative) for not-available / biometric error / missing-key
- 'Unlock with biometric' Button (accent), shows spinner + disabled while isLoading

**States to design.** Auto-prompting / loading (spinner, button disabled) · Idle after UserCancelled (button tappable to retry) · Error: biometric not available · Error: biometric Error.message · Error: 'Unable to load encryption key. Sign in again.'

**Navigation.** from SplashScreen (AppState.Locked: signed in + master_secret present, cold start), background lock timeout (AppViewModel.onAppBackground from Authenticated); to MainGraph (AppState.Authenticated via onBiometricUnlockSuccess on unlock success)


### MainGraph (authenticated shell)
<a id="maingraph-authenticated-shell"></a>
**`AppState.Authenticated (root) -> internal NavHost startDestination MainTab.TODAY` · 🔒 auth-only · ViewModel-backed** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/main/MainGraph.kt`

**Purpose.** Post-auth app shell: Scaffold with 4-tab bottom NavigationBar, add-FAB chooser, nested NavHost, and the auto-post snackbar host.

**Use case.** Shown only once the DB is open and AppState is Authenticated (after onboarding completion or successful unlock/restore). It hosts the entire authenticated experience: a bottom NavigationBar across Today/Month/People/Settings (icons •/☷/○/⚙), a '+' FloatingActionButton on Today/Month/People that opens a DropdownMenu ('Add manually' -> entry, 'Ask the assistant' -> agent), and a nested NavHost wiring detail/settings/review/entry/txn/agent routes. The nav bar + FAB are hidden on detail routes (showNavAndFab gate).

**Key UI elements.**
- Bottom NavigationBar with 4 NavigationBarItems (Today/Month/People/Settings)
- '+' FloatingActionButton (Today/Month/People only)
- Add chooser DropdownMenu ('Add manually' onBackground, 'Ask the assistant' accent)
- Nested NavHost (startDestination Today)
- AutoPostSnackbarHost pinned BottomCenter for Undo snackbars

**States to design.** Today tab selected (nav+FAB visible) · Month tab selected · People tab selected · Settings tab selected (FAB hidden, nav visible) · Detail route active (nav+FAB hidden) · FAB chooser DropdownMenu expanded

**Navigation.** from SplashScreen (AppState.Authenticated direct), LockScreen (after unlock), RecoveryEntryScreen (after restore), OnboardingGraph (after onOnboardingComplete); to TodayScreen / MonthScreen / PeopleListScreen / SettingsScreen (tabs), EntryScreen, AgentScreen, ReviewInboxScreen, TransactionDetailScreen, PersonDetailScreen, settings/* and CloudConsentScreen (nested routes), LockScreen (on 30s background timeout, via AppViewModel)


## Home — Today & Month

> **Area notes.** Today and Month are sibling primary tabs in MainGraph's NavHost (screens/main/MainGraph.kt). MainTab.TODAY.name is the NavHost startDestination, so Today is the post-unlock landing surface; both are reached via the bottom navigation bar, never via push/pop. Both screens require an unlocked/authenticated session (they read live repositories from LocalAppContainer) — requiresAuth=true. The shared + FAB belongs to MainGraph (not the screens) and is shown on TODAY/MONTH/PEOPLE; it opens a DropdownMenu navigating to \"entry\" (manual add) or AGENT_ROUTE (assistant). Today's outbound nav: onTxnClick -> \"txn/{id}\", onReview -> \"review\", onAutoCapture -> \"settings/auto-capture\". MonthScreen() takes no nav params and is purely read-only analytics. Neither screen has a stateless XxxContent split — each constructs its ViewModel inline from LocalAppContainer.current, so isolated full-screen rendering requires a fake AppContainer with repositories returning canned Flows (Month is keyed off YearMonth via flatMapLatest; Today combines four repo Flows to build TransactionRowDisplay). The practical isolation targets are the six leaf composables — ReviewBadge, CategoryBarChart, PerDayLineChart, BudgetProgressList, RecurringList, CaptureOptInCard — which are all stateless, accept plain domain lists plus a HisaabColors.Palette, and render under HisaabTheme without any ViewModel or DB. NetCell, TxnTitle/TxnRow, SectionLabel and the chart sub-rows are private and not externally callable. Note ReviewBadge takes a Long count and renders nothing when count <= 0L.


### Today
<a id="today"></a>
**`tab:TODAY (MainTab.TODAY.name) — NavHost startDestination in MainGraph` · 🔒 auth-only · ViewModel-backed** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/today/TodayScreen.kt`

**Purpose.** Home tab: today's net in/out/net totals plus a chronological list of recent transactions.

**Use case.** Shown as the app's landing tab after unlock. The user sees today's income/expense/net at a glance, scans recent entries, taps a row to open its detail, taps the 'N to review' badge to triage auto-captured transactions, and (if SMS capture is available and not yet enabled) can opt into auto-capture from an inline card. The + FAB (owned by MainGraph) adds a manual entry or opens the assistant.

**Key UI elements.**
- 'Today' display-small title in a SpaceBetween header row
- ReviewBadge pill ('N to review', accent background) top-right, only when pendingCount > 0
- Three NetCell columns: In (positive color), Out (negative color), Net (onBackground), each '৳<int>'
- Optional CaptureOptInCard (surface card, 'Turn on' / 'Maybe later') between totals and list
- HorizontalDivider rule
- LazyColumn of TxnRow items: merchant/category name, account · category subtitle, optional gold 'auto' tag, signed ৳amount; divider between rows
- Empty state: centered 'No entries yet. Tap + to record your first.'

**States to design.** Empty (recent list empty, pendingCount 0, no opt-in card) · Populated list with mixed income/expense rows and an 'auto'-tagged captured row · Review badge visible (pendingCount > 0) · Capture opt-in card visible (captureEnabled=false, captureOptInSeen=false, smsSupported=true)

**Navigation.** from bottom-tab bar (MainGraph) — default start tab, Month tab, People tab, Settings tab; to txn/{id} (transaction detail, via row tap), review (capture review, via ReviewBadge), settings/auto-capture (via CaptureOptInCard 'Turn on'), entry / AGENT_ROUTE (via shared + FAB in MainGraph)


### ReviewBadge
<a id="reviewbadge"></a>
**`embedded-component (rendered in Today header)` · public · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/today/ReviewBadge.kt`

**Purpose.** Pill button showing the count of auto-captured transactions awaiting review; entry point to the review queue.

**Use case.** Sits in the Today header. When auto-capture has parsed SMS into pending transactions, this 'N to review' pill appears so the user can jump to the review screen. It self-hides when there is nothing pending, so it never shows a zero badge.

**Key UI elements.**
- Rounded (999.dp) accent-filled pill
- 'N to review' text in palette.background color
- clickable -> onClick

**States to design.** Hidden (count <= 0L — composable returns early, renders nothing) · Visible with count (e.g. '3 to review')

**Navigation.** from Today screen header; to review (via onClick wired to navController.navigate("review") in MainGraph)


### Month
<a id="month"></a>
**`tab:MONTH (MainTab.MONTH.name) in MainGraph NavHost` · 🔒 auth-only · ViewModel-backed** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/month/MonthScreen.kt`

**Purpose.** Monthly insights tab: month switcher, totals with month-over-month delta, and category / per-day / budget / recurring breakdowns.

**Use case.** Reached via the Month bottom tab. The user pages between months with the ‹ › chevrons; each month shows In/Out/Net totals, a delta vs last month, the top categories as horizontal bars, a per-day spending bar chart, budget progress bars, and detected recurring merchants. A vertically scrolling read-only analytics surface — no row-level actions.

**Key UI elements.**
- Month switcher Row: ‹ IconButton, formatted 'Month Year' headline, › IconButton
- Three NetCell totals (In/Out/Net)
- Delta line '↑/↓ ৳<int> vs last month' colored positive/negative
- SectionLabel headers (accent, letter-spaced): CATEGORIES, PER-DAY SPENDING, BUDGETS, RECURRING
- CategoryBarChart (top 5 slices)
- PerDayLineChart (Canvas bar chart with ৳0 / ৳max axis labels)
- BudgetProgressList (per-budget progress bars)
- RecurringList (detected recurring merchants)

**States to design.** Fully populated month (totals + 5 categories + per-day buckets + budgets + recurring hits) · Empty month (zero totals, each child shows its own empty copy: 'No expenses this month', 'No spending this month', 'Set a budget…', 'No patterns detected…') · Positive delta vs last month (↑ green) / negative delta (↓ red)

**Navigation.** from bottom-tab bar (MainGraph); to none directly (read-only; shared + FAB in MainGraph navigates to entry / AGENT_ROUTE)


### CategoryBarChart
<a id="categorybarchart"></a>
**`embedded-component (Month CATEGORIES section)` · public · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/month/CategoryBarChart.kt`

**Purpose.** Horizontal bar list of spend-by-category: name, amount, percent, and a proportional progress bar tinted by the category color.

**Use case.** Renders the top categories for the selected month inside Month. Each row shows the category name, ৳total, percent of spend, and a bar whose fill width equals percent and whose color is parsed from the category's hex (falling back to accent).

**Key UI elements.**
- Per-slice row: name (weighted), ৳<int> total, '<int>%'
- 6.dp track (palette.rule) with colored fill at percent width
- Empty: 'No expenses this month'

**States to design.** Empty list ('No expenses this month') · Several slices with distinct hex colors · Slice with null/invalid categoryColor (falls back to palette.accent)

**Navigation.** from Month screen


### PerDayLineChart
<a id="perdaylinechart"></a>
**`embedded-component (Month PER-DAY SPENDING section)` · public · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/month/PerDayLineChart.kt`

**Purpose.** Canvas bar chart of daily spending across the month with ৳0 and ৳max axis labels.

**Use case.** Visualizes per-day spend for the selected month in Month. Bars are positioned by epochDay offset and scaled to the max bucket; a baseline rule anchors the bottom. Gives a quick read of spending spikes within the month.

**Key UI elements.**
- 120.dp Canvas drawing accent bars + a rule baseline
- Footer Row with '৳0' and '৳<max> max' labels
- Empty: 'No spending this month'

**States to design.** Empty list ('No spending this month') · Multiple buckets with one tall spike (max) and several shorter days · Single bucket (dayRange coerced to 1)

**Navigation.** from Month screen


### BudgetProgressList
<a id="budgetprogresslist"></a>
**`embedded-component (Month BUDGETS section)` · public · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/month/BudgetProgressList.kt`

**Purpose.** Per-budget progress rows: category, spent/cap, percent, and a color-coded bar (accent < 80%, gold 80-99%, negative >= 100%).

**Use case.** Shows how the user is tracking against each monthly category budget in Month. The bar color escalates as the budget fills (accent -> gold near the cap -> negative when over), giving an at-a-glance over/under-budget signal. The fill width is clamped to 100%.

**Key UI elements.**
- Per-budget row: categoryName (weighted), '৳spent / ৳cap', '<int>%'
- 6.dp track with color-coded fill clamped at 100%
- Divider between rows
- Empty: 'Set a budget in Settings → Budgets.'

**States to design.** Empty list (CTA copy) · Under budget (<80%, accent bar) · Near limit (80-99%, gold bar) · Over budget (>=100%, negative bar, fill clamped to 100%)

**Navigation.** from Month screen


### RecurringList
<a id="recurringlist"></a>
**`embedded-component (Month RECURRING section)` · public · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/month/RecurringList.kt`

**Purpose.** List of detected recurring merchants with occurrence count, average amount, and last-seen MM-DD date.

**Use case.** Surfaces merchants the insight engine flags as recurring (3+ transactions in 90 days) inside Month, helping the user spot subscriptions and habitual spend. Each row shows the merchant, '<n> times · avg ৳<amount>', and a formatted last-seen date.

**Key UI elements.**
- Per-hit row: merchantName, '<count> times · avg ৳<int>' subtitle
- Right-aligned last-seen MM-DD (or '—' when ts=0)
- Divider between rows
- Empty: 'No patterns detected yet — needs 3+ transactions per merchant in the last 90 days.'

**States to design.** Empty list (explanatory copy) · Several recurring hits with last-seen dates · Hit with lastSeenTs=0 (renders '—')

**Navigation.** from Month screen


### CaptureOptInCard
<a id="captureoptincard"></a>
**`embedded-component (conditionally embedded in Today, between totals and the list)` · public · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/onboarding/CaptureOptInCard.kt`

**Purpose.** Soft, skippable card prompting the user to enable on-device SMS auto-capture.

**Use case.** Appears inline on Today only when SMS capture is supported, not yet enabled, and the user hasn't dismissed it. 'Turn on' dismisses the card and navigates to the auto-capture settings flow; 'Maybe later' just dismisses (persisted via TodayViewModel.dismissOptIn so it never reappears). Lives in the onboarding package but is reused by Today.

**Key UI elements.**
- Surface card (16.dp rounded, rule border)
- 'Log transactions automatically' titleMedium
- Privacy explanation body (bKash/Nagad/bank SMS, on-device)
- 'Turn on' accent Button + 'Maybe later' TextButton

**States to design.** Default (only state — purely presentational)

**Navigation.** from Today screen (conditional), onboarding completion flow; to settings/auto-capture (via onTurnOn from Today)


## Entry & transaction detail

> **Area notes.** All four surfaces live behind the authenticated MainGraph NavHost (the app routes through Locked/Authenticated cold-start states before MainGraph mounts), so all requireAuth=true. Routes are wired in /Users/xack/Projects/finance-app/composeApp/src/commonMain/kotlin/app/hisaab/screens/main/MainGraph.kt. EntryScreen is a single composable serving both 'entry' (no candidate, from Today + FAB → 'Add manually') and 'entry?candidateId={id}' (from Review Inbox onEdit) via a nullable navArgument defaulting to null; candidateId triggers EntryViewModel.prefillFromCandidate. TransactionDetailScreen is route 'txn/{id}', reached from a Today transaction row tap. SplitEditorSheet and PersonPickerSheet are NOT nav routes — they are in-screen ModalBottomSheets toggled by showSplitSheet/showPersonSheet local state inside EntryScreen; PersonPickerSheet only appears for LEND/BORROW kinds. Both detail and entry return via navController.popBackStack() through their onDone callback. Stateless-rendering summary: SplitEditorSheet and PersonPickerSheet (and the EntryFields.kt building blocks KindSelector/AmountField/AccountPicker/CategoryPicker/NotesField) render in isolation under HisaabTheme with literal params; EntryScreen and TransactionDetailScreen instantiate their own ViewModels from LocalAppContainer and have no extracted stateless Content composable, so they require either a seeded AppContainer or a small refactor to render representatively (the underlying state objects — EntryFormState, TransactionRowDisplay, CandidateTransaction — are all plain data classes trivially constructed from literals).


### EntryScreen (New Entry)
<a id="entryscreen-new-entry"></a>
**`entry?candidateId={candidateId}` · 🔒 auth-only · ViewModel-backed** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/entry/EntryScreen.kt`

**Purpose.** Full-screen manual transaction composer for adding or confirming-with-edits a transaction.

**Use case.** Shown when the user taps the + FAB then 'Add manually' on the Today tab, or taps Edit on a pending candidate in the Review Inbox (which passes candidateId for prefill). The user picks a kind (Expense/Income/Lend/Borrow/Transfer), types a hero amount, selects account/category/date, optionally adds merchant, notes, tags, a receipt photo, and splits, then taps Save in the top bar. It exists as the single canonical write path for hand-entered money events and one-tap confirmation of parsed captures.

**Key UI elements.**
- TopAppBar with 'New entry' title, 'Cancel' nav button, and 'Save' action (CircularProgressIndicator while saving; disabled until isValid)
- KindSelector row of accent/surface chip Buttons (Expense, Income, Lend, Borrow, Transfer)
- Hero AmountField: large 48sp '৳' prefix + BasicTextField with '0' placeholder
- AccountPicker tappable FieldRow (+ second 'To' AccountPicker only when kind==TRANSFER)
- CategoryPicker tappable row, 'When' date FieldRow, Merchant OutlinedTextField, NotesField
- TagChipInput (OutlinedTextField + AssistChip FlowRow), AttachmentRow ('Add receipt' / 'Attached — tap to remove'), Split FieldRow ('Single entry' / 'N parts')
- LEND/BORROW-only Person + Due date FieldRows
- Inline error Text in palette.negative
- Bottom sheets: SplitEditorSheet and PersonPickerSheet

**States to design.** Default empty EXPENSE form (Save disabled, amount placeholder '0') · Valid filled form (amount>0 + account selected → Save enabled in accent) · TRANSFER kind selected (second 'To' AccountPicker row appears) · LEND/BORROW kind selected (Person + Due date rows appear) · With tags and 'Attached' receipt state · isSaving=true (Save shows CircularProgressIndicator) · error set (negative-colored error text below Split row) · Prefilled-from-candidate (amount/account/category/merchant seeded via prefillFromCandidate)

**Navigation.** from Today (+ FAB → 'Add manually'), Review Inbox (onEdit → entry?candidateId=...); to popBackStack on Cancel or successful Save (onDone), SplitEditorSheet (in-screen sheet), PersonPickerSheet (in-screen sheet)


### SplitEditorSheet
<a id="spliteditorsheet"></a>
**`sheet:SplitEditorSheet (ModalBottomSheet from EntryScreen Split row)` · 🔒 auth-only · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/entry/SplitEditorSheet.kt`

**Purpose.** Bottom sheet to break a single transaction amount into multiple amount parts that must sum to the parent.

**Use case.** Opens when the user taps the 'Split' FieldRow in EntryScreen. The user edits per-part amount rows, adds/removes parts, and the header live-validates that children sum to the parent (turns positive-colored when matched). Save is enabled only when sums match and every part >0; 'Remove splits' clears them. It exists so one receipt total can be allocated across categories.

**Key UI elements.**
- 'Split into parts' accent header
- Live 'Parent: ৳X — children: ৳Y' validation line (positive when matched, else muted)
- Numbered SplitRow list: index + Amount OutlinedTextField + '×' remove TextButton
- '+ Add part' TextButton
- Bottom action Row: 'Remove splits' TextButton + accent 'Save' Button (enabled only when matches && all amounts>0)

**States to design.** Empty initial (seeds two zero-amount rows; Save disabled) · Children sum mismatch (header muted, Save disabled) · Children sum matched (header positive, Save enabled) · Many parts after several '+ Add part' taps

**Navigation.** from EntryScreen (Split FieldRow tap); to EntryScreen via onSave(splits) or onDismiss (dismisses sheet)


### PersonPickerSheet
<a id="personpickersheet"></a>
**`sheet:PersonPickerSheet (ModalBottomSheet from EntryScreen Person row; LEND/BORROW only)` · 🔒 auth-only · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/entry/EntryScreen.kt`

**Purpose.** Bottom sheet to attach a counterparty to a lend/borrow entry by typing a new person's name.

**Use case.** Opens when the user taps the 'Person' FieldRow, which is only visible when kind is LEND or BORROW. The user types a name and taps 'Add' to create a manual person; a placeholder note states the contact picker arrives in P0c-3. It exists to capture who money was lent to or borrowed from.

**Key UI elements.**
- 'Person' accent header
- Name OutlinedTextField
- Accent 'Add' Button (disabled until name non-blank)
- Muted 'Contact picker comes in P0c-3.' placeholder note

**States to design.** Empty input (Add disabled) · Name typed (Add enabled in accent)

**Navigation.** from EntryScreen (Person FieldRow tap, LEND/BORROW kinds only); to EntryScreen via onNewPerson(name) or onDismiss (dismisses sheet)


### TransactionDetailScreen
<a id="transactiondetailscreen"></a>
**`txn/{id}` · 🔒 auth-only · ViewModel-backed** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/transaction/TransactionDetailScreen.kt`

**Purpose.** Read-only detail view of a single transaction with signed hero amount, field rows, optional capture provenance, and a delete action.

**Use case.** Shown when the user taps a transaction row on the Today tab (onTxnClick → txn/{id}). The user reviews the amount, kind, merchant, category, account, timestamp and notes; if the transaction came from a parsed capture, a 'CAPTURED' provenance block shows the parsing engine/model/confidence/sender and lets them expand the original SMS. The user can delete via the top-bar 'Delete' action (confirmed by an AlertDialog). It exists as the inspect-and-delete surface for any single transaction.

**Key UI elements.**
- TopAppBar 'Transaction' title, 'Back' nav button, negative-colored 'Delete' action
- Signed hero amount Text at 56sp (+/positive for INCOME/LEND/SETTLEMENT, −/negative otherwise)
- Kind label
- DetailRow list: Merchant, Category, Account, When, Notes (Notes only if present)
- Optional ProvenanceBlock: 'CAPTURED' header, Parsed-by engine, Model, Confidence %, From sender, expandable 'Show original SMS ▾' raw body
- Delete confirmation AlertDialog ('Delete this transaction? This can't be undone.')

**States to design.** Loading/not-found: display==null → centered 'Transaction not found' (also the initial null before flow emits) · Loaded expense (negative red −৳ amount, no provenance) · Loaded income/lend (positive green +৳ amount) · Loaded with provenance/CAPTURED block (collapsed 'Show original SMS ▾') · Provenance raw SMS expanded · Delete confirmation AlertDialog open

**Navigation.** from Today (transaction row tap → txn/{id}); to popBackStack on Back, or on confirmed Delete (onDone)


## Settings

> **Area notes.** Settings is a navigation sub-graph rooted at SettingsScreen (a top-level destination in MainGraph). SettingsScreen exposes five forward callbacks wired by MainGraph: onAccounts->AccountsScreen, onCategories->CategoriesScreen, onBudgets->BudgetsScreen, onRecoveryReveal->RecoveryPhraseRevealScreen, onAutoCapture->AutoCaptureScreen, plus onSignedOut which (after SettingsViewModel.signOut clears the DB/master-secret) routes back to the auth/onboarding root. AutoCaptureScreen further navigates to CloudConsentScreen via its onConsent callback. Every leaf screen takes onBack -> SettingsScreen. All screens are wrapped in HisaabTheme which provides LocalHisaabPalette; they read colors via LocalHisaabPalette.current and the whole group requires an authenticated/unlocked session (an open DB is needed for the repository-backed screens). Renderability tiers: (1) Fully stateless / no DB — CloudConsentScreen (plain params), EnginePicker (public), SettingRow (public, shared); plus private-but-pure sheets/forms (LockTimeoutSheet, AddAccountSheet, AddCategorySheet, AddBudgetSheet, AddSenderInline) that only need exposing/extracting. (2) Needs a container with fake repositories exposing Flows but NOT an open SQLCipher DB — SettingsScreen (secureStorage only), AccountsScreen/CategoriesScreen/BudgetsScreen (repository.observe* Flows), RecoveryPhraseRevealScreen (biometricAuth + secureStorage + mnemonicService), AutoCaptureScreen (the iOS/SMS-unsupported branch needs only captureService.capabilities() lacking SMS; the full Android body needs an AutoCaptureViewModel built from fake CaptureConfigRepository/SenderRepository/AccountRepository/LlmRouter). The central blocker for full-screen isolated rendering is that AppContainer is an `expect class`, so representative state requires either a JVM/Android `actual` AppContainer or a hand-rolled fake that satisfies the members each screen touches (SecureStorage, AuthRepository, AccountRepository, CategoryRepository, BudgetRepository, CaptureConfigRepository, SenderRepository, CaptureService, LlmRouter, BiometricAuth, MnemonicService) backed by MutableStateFlows — no actual encrypted DB open is required for these settings screens.


### SettingsScreen
<a id="settingsscreen"></a>
**`settings (top-level destination in MainGraph)` · 🔒 auth-only · ViewModel-backed** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/SettingsScreen.kt`

**Purpose.** Settings hub: privacy controls, data sub-screens, capture entry, about, and sign out.

**Use case.** Shown when the user opens the Settings tab. They adjust lock timeout, toggle biometric unlock, reveal their recovery phrase, drill into Accounts/Categories/Budgets/Auto-capture, see the app version, and sign out. It is the navigation root for the whole settings group.

**Key UI elements.**
- 'Settings' displaySmall heading
- SECTION labels: Privacy / Data / Capture / About (accent, letter-spaced)
- SettingRow rows (label left muted, value/chevron right) for Lock timeout, Recovery phrase, Accounts, Categories, Budgets, Auto-capture, Version
- SettingToggleRow with Material3 Switch for Biometric unlock
- Sign out TextButton in negative color
- LockTimeoutSheet ModalBottomSheet (Immediate / 30 seconds / 5 minutes / Never with a check mark)
- Sign-out AlertDialog warning about clearing encrypted data

**States to design.** Default (lock=30s, biometric off) · Biometric on · Lock timeout sheet open (showing current selection check) · Sign-out confirmation dialog open

**Navigation.** from MainGraph bottom-nav / settings tab; to AccountsScreen, CategoriesScreen, BudgetsScreen, RecoveryPhraseRevealScreen, AutoCaptureScreen, onSignedOut -> auth/onboarding root


### LockTimeoutSheet
<a id="locktimeoutsheet"></a>
**`sheet (private ModalBottomSheet inside SettingsScreen)` · 🔒 auth-only · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/SettingsScreen.kt`

**Purpose.** Bottom sheet to pick the auto-lock timeout.

**Use case.** Opens when the user taps the 'Lock timeout' row in Settings. Presents four fixed options (Immediate, 30 seconds, 5 minutes, Never); tapping one calls viewModel.setLockTimeoutMs and dismisses. The currently active option shows a check mark.

**Key UI elements.**
- 'Lock timeout' accent caption
- Four clickable rows: Immediate / 30 seconds / 5 minutes / Never
- Trailing check (accent) on the current selection
- HorizontalDivider rules between rows

**States to design.** Current = 30 seconds (default) · Current = Immediate · Current = Never

**Navigation.** from SettingsScreen Lock timeout row


### AccountsScreen
<a id="accountsscreen"></a>
**`accounts (settings sub-destination)` · 🔒 auth-only · ViewModel-backed** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/AccountsScreen.kt`

**Purpose.** Manage financial accounts: list, add (with card fields), rename, archive.

**Use case.** Opened from Settings > Accounts. Lists active accounts with kind+currency subtitle; CARD accounts also show an outstanding/available/next-due summary row. Tapping a row opens a rename dialog; '+ Add' opens a sheet to create CASH/BANK/MFS/CARD/GOAL accounts (CARD reveals credit-limit/statement-day/due-day fields); 'Archive' archives an account.

**Key UI elements.**
- TopAppBar 'Accounts' with Back nav and '+ Add' action
- LazyColumn of account rows (name + 'KIND · CURRENCY' subtitle)
- Per-row 'Archive' TextButton (negative)
- CardSummaryRow chips: Outstanding (negative), Available (positive), Due (muted) for CARD kind
- AddAccountSheet ModalBottomSheet: Name field, Kind chip-buttons, conditional CARD fields (credit limit, statement day 1-28, due day 1-28), Add button
- RenameAccountDialog AlertDialog with text field

**States to design.** Empty list · List with non-card accounts · List including a CARD account showing summary chips · AddAccountSheet open (CASH selected) · AddAccountSheet open (CARD selected, card fields visible) · RenameAccountDialog open

**Navigation.** from SettingsScreen Accounts row; to onBack -> SettingsScreen


### AddAccountSheet
<a id="addaccountsheet"></a>
**`sheet (private ModalBottomSheet inside AccountsScreen)` · 🔒 auth-only · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/AccountsScreen.kt`

**Purpose.** Create a new account, with conditional credit-card fields.

**Use case.** Opens from the '+ Add' action on AccountsScreen. The user types a name, picks a kind via chip buttons; selecting CARD reveals credit-limit (optional) and statement/due day fields (digit-filtered, max 2 chars, clamped 1..28). Add is enabled only when name is non-blank.

**Key UI elements.**
- 'New account' accent caption
- OutlinedTextField Name
- Kind chip-button row (CASH/BANK/MFS/CARD/GOAL) with selected = accent
- Conditional: Credit limit field, Statement day + Due day fields (CARD only)
- Add Button (accent, disabled when name blank)

**States to design.** CASH selected (no card fields) · CARD selected (card fields shown) · Add disabled (blank name)

**Navigation.** from AccountsScreen '+ Add'


### CategoriesScreen
<a id="categoriesscreen"></a>
**`categories (settings sub-destination)` · 🔒 auth-only · ViewModel-backed** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/CategoriesScreen.kt`

**Purpose.** List spending categories and add new ones.

**Use case.** Opened from Settings > Categories. Shows all categories with optional emoji icon and a 'default' badge on seeded categories. '+ Add' opens a sheet to create a category with a name and optional emoji.

**Key UI elements.**
- TopAppBar 'Categories' with Back and '+ Add'
- LazyColumn rows: optional emoji (28dp), name, 'default' badge (muted, letter-spaced) for isDefault
- HorizontalDivider rules
- AddCategorySheet ModalBottomSheet: Name field, Emoji (optional) field, Add button

**States to design.** List with default + custom categories · Category with emoji icon vs without · AddCategorySheet open

**Navigation.** from SettingsScreen Categories row; to onBack -> SettingsScreen


### AddCategorySheet
<a id="addcategorysheet"></a>
**`sheet (private ModalBottomSheet inside CategoriesScreen)` · 🔒 auth-only · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/CategoriesScreen.kt`

**Purpose.** Create a new category with a name and optional emoji.

**Use case.** Opens from the '+ Add' action on CategoriesScreen. User enters a name and optional emoji; Add is enabled only when the name is non-blank, then calls onAdd(name, icon-or-null).

**Key UI elements.**
- 'New category' accent caption
- OutlinedTextField Name
- OutlinedTextField Emoji (optional)
- Add Button (accent, disabled when name blank)

**States to design.** Empty (Add disabled) · Name + emoji filled (Add enabled)

**Navigation.** from CategoriesScreen '+ Add'


### BudgetsScreen
<a id="budgetsscreen"></a>
**`budgets (settings sub-destination)` · 🔒 auth-only · ViewModel-backed** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/BudgetsScreen.kt`

**Purpose.** List monthly category budgets and add new ones; archive existing.

**Use case.** Opened from Settings > Budgets. Empty state prompts 'No budgets yet. Tap + Add.' Otherwise lists each budget (category name, '৳cap/month · since YYYY-MM', Archive button). '+ Add' opens a sheet to pick a category (radio list, excluding salary/transfer) and enter a monthly cap; saved for the current YearMonth.

**Key UI elements.**
- TopAppBar 'Budgets' with Back and '+ Add'
- Empty-state centered muted text
- LazyColumn rows: category name + '৳{cap}/month · since {startsMonth.value}' subtitle
- Per-row 'Archive' TextButton (negative)
- AddBudgetSheet ModalBottomSheet: RadioButton category list, Monthly cap (৳) field, Save button

**States to design.** Empty (no budgets) · List with one+ budgets · AddBudgetSheet open (category radio list + cap field)

**Navigation.** from SettingsScreen Budgets row; to onBack -> SettingsScreen


### AddBudgetSheet
<a id="addbudgetsheet"></a>
**`sheet (private ModalBottomSheet inside BudgetsScreen)` · 🔒 auth-only · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/BudgetsScreen.kt`

**Purpose.** Create a monthly budget: pick a category and set a cap.

**Use case.** Opens from '+ Add' on BudgetsScreen. User selects a category via radio buttons and enters a monthly cap (digit/dot filtered). Save is enabled only when amount > 0 and a category is selected.

**Key UI elements.**
- 'New budget' accent caption
- 'Category' label + RadioButton list (accent selected color)
- OutlinedTextField 'Monthly cap (৳)'
- Save Button (accent, disabled until amount>0 and category chosen)

**States to design.** No amount (Save disabled) · Category selected + valid amount (Save enabled) · Empty category list (no rows)

**Navigation.** from BudgetsScreen '+ Add'


### AutoCaptureScreen
<a id="autocapturescreen"></a>
**`auto-capture (settings sub-destination)` · 🔒 auth-only · ViewModel-backed** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/AutoCaptureScreen.kt`

**Purpose.** Configure SMS auto-capture: engine, cloud provider+key, privacy/trust, sender mappings, history backfill.

**Use case.** Opened from Settings > Auto-capture. On iOS (SMS unsupported) shows an explainer card and a disabled 'paste coming soon' affordance. On Android: master toggle to capture from SMS (requests permission), Engine picker (On-device vs Cloud), and when Cloud: provider radios, API-key field + Validate, model row, and a Cloud consent row. Privacy section toggles PII redaction; Trust section toggles always-review and (when off) shows an auto-post confidence slider with reset-to-default. Senders section lists/maps/toggles senders with a 'new sender detected' prompt and an add-sender inline form. History section imports the last 90 days.

**Key UI elements.**
- TopAppBar 'Auto-capture' with Back
- IosUnavailableExplainer card (when SMS unsupported)
- Master ToggleRow 'Capture transactions from SMS' + permission-denied warning
- ENGINE EnginePicker (RadioRow On-device / Cloud)
- PROVIDER radios + ApiKeyField (BasicTextField password) + Validate button + KeyValidation status text + Model SettingRow + Cloud consent SettingRow
- PRIVACY redaction ToggleRow + explainer
- TRUST always-review ToggleRow + auto-post confidence Slider (0.5..0.99) + Reset-to-default
- SENDERS: NewSenderPrompt card, per-sender Switch + 'Map -> {account}' chips, AddSenderInline form
- HISTORY 'Import last 90 days' button (enabled only when capture on)

**States to design.** iOS / SMS unsupported (explainer only) · Android, capture off · Permission denied warning · Engine = On-device · Engine = Cloud, provider chosen, key saved, KeyValidation VALID/CHECKING/INVALID · Always-review off (confidence slider visible, with/without reset button) · Unmapped senders prompt + mapping chips · Add-sender inline form open

**Navigation.** from SettingsScreen Auto-capture row; to onBack -> SettingsScreen, onConsent -> CloudConsentScreen


### EnginePicker
<a id="enginepicker"></a>
**`embedded-component (public composable, used in AutoCaptureScreen)` · 🔒 auth-only · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/AutoCaptureScreen.kt`

**Purpose.** The On-device vs Cloud engine selection rows.

**Use case.** Embedded in AutoCaptureScreen's Engine section. Public specifically so Compose UI tests can render the real engine-picker rows without the full screen. Tapping a row selects the engine mode.

**Key UI elements.**
- RadioRow 'On-device (private, offline)'
- RadioRow 'Cloud (your own API key)'
- Accent check on the selected mode
- HorizontalDivider rules

**States to design.** On-device selected · Cloud selected

**Navigation.** from AutoCaptureScreen Engine section


### AddSenderInline
<a id="addsenderinline"></a>
**`embedded-component (private inline form inside AutoCaptureScreen Senders section)` · 🔒 auth-only · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/AutoCaptureScreen.kt`

**Purpose.** Inline form to manually add an SMS sender mapping.

**Use case.** Revealed when the user taps 'Add a sender' in the Senders section. Two BasicTextFields (Sender ID e.g. 'BRAC BANK', Display name) with Add/Cancel; Add is allowed only when senderId is non-blank, falling back to senderId for the display name if blank.

**Key UI elements.**
- BasicTextField Sender ID + caption
- BasicTextField Display name + caption
- Add (accent) / Cancel (muted) TextButtons

**States to design.** Empty · Sender ID filled

**Navigation.** from AutoCaptureScreen Senders section 'Add a sender'


### CloudConsentScreen
<a id="cloudconsentscreen"></a>
**`cloud-consent (settings sub-destination, reached from AutoCaptureScreen)` · 🔒 auth-only · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/CloudConsentScreen.kt`

**Purpose.** Explain cloud parsing data flow and capture grant/revoke consent.

**Use case.** Reached from the 'Cloud consent' row in AutoCaptureScreen when Cloud engine is selected. Explains that bank SMS text goes directly from device to the chosen provider via the user's API key (Hisaab servers never see it) and that redaction masks account/phone numbers. If not yet granted, shows an 'I agree' button (then navigates back); if granted, shows confirmation + a 'Revoke consent' button.

**Key UI elements.**
- TopAppBar 'Cloud consent' with Back
- titleMedium explainer referencing {providerName}
- Muted body paragraph about direct-to-provider / redaction
- If granted: 'Consent granted.' (positive) + 'Revoke consent' TextButton (negative)
- If not granted: 'I agree — use cloud parsing' Button (accent, full width)

**States to design.** Consent NOT granted (agree button) · Consent granted (revoke button)

**Navigation.** from AutoCaptureScreen Cloud consent row (onConsent); to onBack / onGrant -> back to AutoCaptureScreen


### RecoveryPhraseRevealScreen
<a id="recoveryphraserevealscreen"></a>
**`recovery-reveal (settings sub-destination)` · 🔒 auth-only · ViewModel-backed** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/RecoveryPhraseRevealScreen.kt`

**Purpose.** Biometric-gated reveal of the 24-word recovery phrase.

**Use case.** Reached from Settings > Recovery phrase. On entry it prompts biometric auth; on success it loads the master secret, encodes it to 24 mnemonic words, and shows them in a 2-column numbered grid with a warning. On biometric error/unavailable it shows an error; on user-cancel it navigates back. A loading spinner shows while words are null and no error.

**Key UI elements.**
- TopAppBar 'Recovery phrase' with Back
- Negative warning line about keeping words private
- CircularProgressIndicator (loading)
- LazyVerticalGrid (2 cols) of bordered word chips: index (accent) + word
- Error text (negative) on failure

**States to design.** Loading (biometric prompt in flight, words null, no error) · Success (24-word grid) · Error (master secret missing / biometric not available / error message)

**Navigation.** from SettingsScreen Recovery phrase row; to onBack -> SettingsScreen (also on BiometricResult.UserCancelled)


### SettingRow (shared component)
<a id="settingrow-shared-component"></a>
**`embedded-component (public shared row, no own route)` · public · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/settings/SettingsComponents.kt`

**Purpose.** Shared label/value row with optional tap target and trailing divider.

**Use case.** Reusable building block used across SettingsScreen and AutoCaptureScreen for label-on-left / value-on-right rows (e.g. 'Lock timeout · 30 seconds', 'Model · default'). Not a navigable screen; documented because it defines the visual language of the settings group and is independently renderable.

**Key UI elements.**
- Label (muted, weight 1)
- Value (onBackground)
- HorizontalDivider (rule) below

**States to design.** Tappable (onClick != null) · Non-tappable (onClick == null)

**Navigation.** from SettingsScreen, AutoCaptureScreen


## Assistant, capture review & people

> **Area notes.** All screens live under the single MainGraph NavHost (/Users/xack/Projects/finance-app/composeApp/src/commonMain/kotlin/app/hisaab/screens/main/MainGraph.kt) and are wrapped in HisaabTheme (provides LocalHisaabPalette) higher up; they all require an unlocked/authenticated session (this is the post-auth main graph). Routes: bottom-nav tabs use MainTab enum names (TODAY/MONTH/PEOPLE/SETTINGS); other destinations are string routes. The agent flow is reached only via the floating '+' chooser ('Ask the assistant' → route 'agent'), not from a tab; AgentConsentDialog and ReviewCard are not their own routes — they render inside AgentScreen (consent when gate=NeedsConsent, review card when state.review is non-empty). ReviewInbox is route 'review', reached from Today's onReview, and its Edit deep-links into 'entry?candidateId={id}'. PersonDetail is 'person/{id}', reached from the People tab. AutoPostSnackbarHost is not navigable at all — it is mounted once at the NavHost's bottom-center and reacts to AppContainer.captureEvents regardless of the current route. Renderability split: AgentScreen (via AgentScreenContent), ReviewCard, AgentConsentDialog, and ReviewInboxScreen (via ReviewInboxContent) all have clean VM/DB-free rendering paths driven by plain data classes + callbacks. PeopleListScreen, PersonDetailScreen, and AutoPostSnackbarHost have NO stateless content composable — they each read LocalAppContainer directly and build their own ViewModel/flows, so isolated rendering requires a fake AppContainer supplied through LocalAppContainer (emitting fixed flows) or rendering their private stateless sub-composables (PersonRow, LendBorrowRowItem, SettleDialog, AddPersonSheet) in a harness with hand-built domain values.


### Assistant Chat (AgentScreen)
<a id="assistant-chat-agentscreen"></a>
**`agent` · 🔒 auth-only · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/agent/AgentScreen.kt`

**Purpose.** Conversational AI assistant chat that turns natural-language requests into reviewable finance write actions.

**Use case.** Reached from the global '+' FAB chooser ('Ask the assistant') on Today/Month/People. The user types or dictates a request (e.g. 'I lent Karim 2000'), the assistant replies and proposes write actions in an inline ReviewCard, and the user applies them. Exists to give a low-friction NL entry path alongside manual entry.

**Key UI elements.**
- TopAppBar titled 'Assistant' with 'Close' nav button and 'New chat' action
- Chat thread (LazyColumn) of left/right speech bubbles keyed by message id
- inFlight 'thinking' bubble with CircularProgressIndicator
- Inline ReviewCard when state.review is non-empty
- Gate banner (Unavailable) / consent dialog (NeedsConsent)
- Error line (negative) and confirmation line (positive)
- Bottom input bar: OutlinedTextField + circular send (↑) button + mic (🎤) button

**States to design.** Empty conversation (no messages, Ready gate) · Thread with user + assistant messages · inFlight (thinking bubble visible, send disabled) · Review present (ReviewCard rendered with proposed writes + checkboxes) · Error banner (e.g. invalid key / network) · Confirmation 'Saved.' · Gate: Unavailable banner ('Add a cloud model + API key…') · Gate: NeedsConsent (AgentConsentDialog overlay) · Voice listening (mic tinted accent) vs voiceAvailable=false (mic disabled)

**Navigation.** from Today (+ FAB → 'Ask the assistant'), Month (+ FAB → 'Ask the assistant'), People (+ FAB → 'Ask the assistant'); to popBackStack on Close (returns to previous tab)


### Agent Consent Dialog
<a id="agent-consent-dialog"></a>
**`dialog (shown inside AgentScreen when gate == AgentAvailability.NeedsConsent)` · 🔒 auth-only · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/agent/AgentConsentDialog.kt`

**Purpose.** Privacy disclosure opt-in that explains what data leaves the device before the cloud assistant can be used.

**Use case.** Appears the first time the user tries to use the assistant without prior consent (gate=NeedsConsent). Lists what is sent (account/category names, named people, amounts, conversation) vs what never leaves (raw SMS, full ledger, audio). User taps 'Turn on assistant' to consent or 'Not now' to back out (closes the agent screen).

**Key UI elements.**
- AlertDialog titled 'Turn on the assistant'
- Body paragraph describing it as a cloud feature
- 'What leaves your device' bulleted list
- 'What never leaves your device' bulleted list
- Confirm button 'Turn on assistant' (accent)
- Dismiss button 'Not now' (muted)

**States to design.** Default (single static state — no internal variants)

**Navigation.** from Assistant Chat (AgentScreen) when gate=NeedsConsent; to onConsent → AgentViewModel.onConsent() clears gate (stays on chat), onDismiss → closes the agent screen


### Review Card (proposed writes)
<a id="review-card-proposed-writes"></a>
**`embedded-component (rendered inside AgentScreen's message thread)` · 🔒 auth-only · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/agent/ReviewCard.kt`

**Purpose.** Editable confirmation card listing the assistant's proposed write actions before they are committed.

**Use case.** Embedded in the agent chat thread after the assistant proposes write actions. One row per ProposedWrite with an include checkbox, a human-readable summary (e.g. 'Lent ৳2000 to Karim'), an editable amount field where applicable, and a single 'Apply' button (disabled when nothing is included). Lets the user vet/tweak/deselect each action before applying.

**Key UI elements.**
- Surface card titled 'Review & apply'
- Per-write row: Checkbox (testTag review_toggle_N) + summary label
- AmountField (testTag review_amount_N) for writes carrying an 'amount' arg
- Apply button (testTag review_apply), disabled when included set is empty

**States to design.** Single write with amount, included · Multiple mixed writes (transfer, add_transaction, record_lend_borrow, create_account) · Nothing included (Apply disabled) · Write without amount arg (no AmountField row)

**Navigation.** from Assistant Chat (AgentScreen) — appears when state.review is non-empty; to onApply → AgentViewModel.onApply() commits writes; on success clears review and shows 'Saved.' confirmation (stays on chat)


### Review Inbox (capture review)
<a id="review-inbox-capture-review"></a>
**`review` · 🔒 auth-only · stateless render** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/capture/ReviewInboxScreen.kt`

**Purpose.** Triage queue for auto-captured SMS transactions that Hisaab wasn't confident enough to auto-post.

**Use case.** Reached from Today's 'Review' action. Shows pending parsed SMS candidates as cards (amount, merchant/sender, account, confidence %, expandable original SMS). User confirms a card (posts the txn), swipes left to dismiss, edits (opens Entry prefilled), or taps 'Confirm all high-confidence' to bulk-post anything ≥0.85.

**Key UI elements.**
- TopAppBar 'Review' with 'Close'
- 'Confirm all high-confidence' button (only when any candidate ≥0.85)
- LazyColumn of swipe-to-dismiss CandidateCard items
- CandidateCard: large signed amount, merchant·category, sender·account·confidence, optional parseError line, 'Show original SMS ▾' expander, Confirm + Edit buttons
- Empty state: 'Nothing to review…' centered message
- Swipe background: red 'Dismiss' panel

**States to design.** Empty (no pending) — empty-state text · Pending list with mixed CREDIT/DEBIT candidates · High-confidence present (bulk Confirm button shown) · Candidate with parseError ('Needs a manual fix') · Candidate with amount=null ('Amount unknown') · Raw SMS expanded vs collapsed

**Navigation.** from Today (onReview action); to onBack → popBackStack (Today), onEdit → entry?candidateId={id} (EntryScreen prefilled)


### Auto-Post Undo Snackbar Host
<a id="auto-post-undo-snackbar-host"></a>
**`embedded-component (mounted once at NavHost bottom-center in MainGraph)` · 🔒 auth-only · ViewModel-backed** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/capture/AutoPostSnackbarHost.kt`

**Purpose.** Transient Undo affordance shown whenever a captured SMS transaction is auto-posted in the background.

**Use case.** Mounted once alongside the main NavHost. Collects AppContainer.captureEvents; for each CaptureEvent.AutoPosted it shows a short snackbar like '+৳500 · bKash · auto-added' with an Undo action. Tapping Undo deletes the posted transaction and marks the candidate DISMISSED. Always present app-wide so a high-confidence auto-capture is reversible.

**Key UI elements.**
- SnackbarHost anchored bottom-center, full width
- Snackbar message '±৳amount · sender · auto-added'
- 'Undo' action label
- Dismiss action

**States to design.** Idle (no snackbar) · Visible auto-posted snackbar with Undo (CREDIT '+' vs DEBIT '−')

**Navigation.** from Always mounted with MainGraph NavHost (no navigation); to No navigation; Undo mutates data only


### People List
<a id="people-list"></a>
**`PEOPLE (bottom-nav tab)` · 🔒 auth-only · ViewModel-backed** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/people/PeopleListScreen.kt`

**Purpose.** Tab listing everyone the user has lent to or borrowed from, with each person's net balance.

**Use case.** One of the four bottom-nav tabs. Lists people with their net balance (green '+৳' = they owe you, red '−৳' = you owe them, 'Settled' at zero). User taps a row to open PersonDetail, or taps '+ Add' to open the AddPersonSheet (manual name entry, or pick from contacts when the contact picker is available).

**Key UI elements.**
- Header 'People' (displaySmall) + '+ Add' TextButton
- LazyColumn of PersonRow items with name, optional contactRef, signed balance / 'Settled'
- HorizontalDivider between rows
- Empty state: 'No one yet. Lend or borrow to add a person.'
- AddPersonSheet (ModalBottomSheet): name field, Add button, optional 'Or pick from contacts'

**States to design.** Empty (no people) · List with positive/negative/settled balances · Person row with contactRef shown · AddPersonSheet open (contact picker available vs not)

**Navigation.** from Bottom navigation (People tab); to person/{id} (PersonDetail) on row click, AddPersonSheet (in-screen modal) on '+ Add', contact picker (platform) from sheet


### Person Detail
<a id="person-detail"></a>
**`person/{id}` · 🔒 auth-only · ViewModel-backed** · file: `composeApp/src/commonMain/kotlin/app/hisaab/screens/people/PersonDetailScreen.kt`

**Purpose.** Per-person ledger showing net balance and the full lend/borrow history, with settle actions.

**Use case.** Opened by tapping a person on the People tab. Shows a big net balance with a 'They owe you' / 'You owe them' / 'Settled' label, optional contact ref, and a History list of LendBorrowRow items (Lent/Borrowed amount, purpose, status). Each non-settled row has a 'Settle' button opening a SettleDialog to record a repayment against a chosen account.

**Key UI elements.**
- TopAppBar with person name + 'Back'
- Balance label + large signed amount (positive/negative/muted)
- Optional contact ref line
- 'History' section header
- LazyColumn of LendBorrowRowItem (direction+amount, purpose, status, optional 'Settle')
- Empty history: 'No records yet.'
- Loading: centered CircularProgressIndicator (detail==null)
- SettleDialog: amount field + account RadioButtons + Settle/Cancel

**States to design.** Loading (detail null → spinner) · Positive balance (They owe you) · Negative balance (You owe them) · Settled (৳0) · History with OPEN / PARTIAL / SETTLED rows · Empty history · SettleDialog open

**Navigation.** from People List (row click); to onBack → popBackStack (People), SettleDialog (in-screen dialog) from a row's 'Settle'


---

## Appendix A — screenshot render recipes

How each page is rendered in the instrumented screenshot harness (`ScreenshotTourTest`). Stateless pages render directly with synthetic state; ViewModel-backed pages require a DB-backed `AppContainer` provided via `LocalAppContainer`.


**WelcomeScreen** — stateless
> Call WelcomeScreen(onSendOtp: (phone: String) -> Unit, isLoading: Boolean = false, error: String? = null). No ViewModel/state class needed — it owns its own phone text via remember. Pass onSendOtp = {} and vary isLoading/error directly. States: default WelcomeScreen(onSendOtp={}); loading WelcomeScreen(onSendOtp={}, isLoading=true); error WelcomeScreen(onSendOtp={}, error="Couldn't send code. Try again."). To show the enabled button / filled field for a screenshot you must drive the internal TextField (the field is internal remember state, so the empty and error variants are the directly controllable ones). Wrap in HisaabTheme { }.

**OtpScreen** — stateless
> Call OtpScreen(phone: String, onVerify: (token: String) -> Unit, onResend: () -> Unit, isLoading: Boolean = false, error: String? = null). Stateless; otp text is internal remember. Representative: OtpScreen(phone="+8801712345678", onVerify={}, onResend={}); loading OtpScreen(phone="+8801712345678", onVerify={}, onResend={}, isLoading=true); error OtpScreen(phone="+8801712345678", onVerify={}, onResend={}, error="Invalid code"). Wrap in HisaabTheme { }.

**BiometricSetupScreen** — stateless
> Call BiometricSetupScreen(isAvailable: Boolean, onEnroll: () -> Unit, onSkip: () -> Unit, isLoading: Boolean = false, error: String? = null). Fully stateless — pass flags directly. Representative: available BiometricSetupScreen(isAvailable=true, onEnroll={}, onSkip={}); unavailable BiometricSetupScreen(isAvailable=false, onEnroll={}, onSkip={}); error BiometricSetupScreen(isAvailable=true, onEnroll={}, onSkip={}, error="Too many attempts. Try again later."). In the live graph isAvailable is hardcoded true. Wrap in HisaabTheme { }.

**RecoveryPhraseScreen** — stateless
> Call RecoveryPhraseScreen(words: List<String>, onAcknowledged: () -> Unit). Stateless; the acknowledged checkbox is internal remember. Build a representative 24-word list inline, e.g. words = listOf("abandon","ability","able","about","above","absent","absorb","abstract","absurd","abuse","access","accident","account","accuse","achieve","acid","acoustic","acquire","across","act","action","actor","actress","actual") and pass onAcknowledged={}. The checked/enabled-button state is reached by toggling the in-UI checkbox. Wrap in HisaabTheme { }.

**ProfileSetupScreen** — stateless
> Call ProfileSetupScreen(onComplete: (name: String, locale: String) -> Unit, isLoading: Boolean = false). Stateless; name + locale (default 'en') are internal remember, so the language toggle and name field are exercised in-UI. Representative: default ProfileSetupScreen(onComplete={_,_->}); loading ProfileSetupScreen(onComplete={_,_->}, isLoading=true). Wrap in HisaabTheme { }.

**CaptureOptInCard** — stateless
> Call CaptureOptInCard(onTurnOn: () -> Unit, onMaybeLater: () -> Unit, modifier: Modifier = Modifier). Fully stateless and presentational. Render inside a padded container/surface: HisaabTheme { Box(Modifier.background(LocalHisaabPalette.current.background).padding(22.dp)) { CaptureOptInCard(onTurnOn={}, onMaybeLater={}) } }. Only one visual state.

**SplashScreen** — stateless
> SplashScreen() takes ZERO params and reads only LocalHisaabPalette. Render directly: wrap in HisaabTheme { SplashScreen() } (or CompositionLocalProvider(LocalHisaabPalette provides HisaabColors.Light/Dark) { SplashScreen() } as AgentScreenTest does). No ViewModel, no DB, no callbacks. Both themes worth capturing via HisaabTheme(darkTheme=true/false).

**OnboardingGraph (Welcome entry)** — stateless
> The GRAPH itself needs OnboardingViewModel(container) (constructor: OnboardingViewModel(container: AppContainer, authRepository=..., cryptoService=..., mnemonicService=...)) — container is hard to build in isolation, so render the leaf screens directly instead. WelcomeScreen(onSendOtp:(String)->Unit, isLoading:Boolean=false, error:String?=null) and OtpScreen(phone:String, onVerify:(String)->Unit, onResend:()->Unit, isLoading:Boolean=false, error:String?=null) are FULLY stateless: wrap in HisaabTheme {}. Representative states: WelcomeScreen(onSendOtp={}, isLoading=false, error=null) for idle; error="Invalid number" for error; isLoading=true for spinner. OtpScreen(phone="+8801712345678", onVerify={}, onResend={}, error="Wrong code").

**RecoveryEntryScreen** — ViewModel-backed
> NOT stateless — RecoveryEntryScreen(onRecovered:()->Unit) reads LocalAppContainer.current and calls container.mnemonicService.decode(), container.secureStorage.storeMasterSecret/storeString, container.openDatabase(). The visual layout (24 fields, headings, button) renders fine with just HisaabTheme + a provided LocalAppContainer, but any Restore tap touches the container. To render in isolation, provide CompositionLocalProvider(LocalAppContainer provides <fake AppContainer>) inside HisaabTheme; the empty/validation-error states (blank fields, not-in-wordlist) need NO container interaction since attemptRestore() validates BIP39 before touching the container, so those states screenshot without a real container as long as one is provided to satisfy LocalAppContainer.current. Pass onRecovered={}.

**LockScreen** — ViewModel-backed
> NOT stateless — LockScreen(onUnlock:()->Unit) reads LocalAppContainer.current and calls container.secureStorage.loadMasterSecret/loadString, container.biometricAuth.authenticate(), container.openDatabase(). It also fires attemptUnlock() in a LaunchedEffect on first composition, so it immediately invokes the container. To render the LOCKED visual in isolation you must supply CompositionLocalProvider(LocalAppContainer provides <fake AppContainer>) inside HisaabTheme and a fake BiometricAuth that suspends/returns a controllable BiometricResult (e.g. NotAvailable to land on the error state, or UserCancelled to land on the idle retry state). Pass onUnlock={}. There is no XxxContent(state,callbacks) overload — the error/loading states are driven by internal remember{} + container calls.

**MainGraph (authenticated shell)** — ViewModel-backed
> NOT renderable in isolation as a whole — MainGraph() takes no params but each composable() destination reads LocalAppContainer.current and builds real ViewModels (AgentViewModel(conversationRepo, runtime, ...), AutoCaptureViewModel(...), TodayScreen/MonthScreen/etc.) that require the open SQLCipher DB and the full AppContainer graph (requireDb crashes if the DB is closed — this is exactly why cold start routes to Locked, not Authenticated). To exercise it you need a fully wired AppContainer with an OPEN database provided via LocalAppContainer; per the test DB FK memory, seed an in-memory/SQLCipher DB with PRAGMA foreign_keys=ON and representative UserProfile/accounts/transactions. For screenshots, render the individual leaf screens (TodayScreen, AgentScreenContent, etc.) directly instead; the shell chrome (NavigationBar/FAB) itself only needs HisaabTheme + a NavController but pulls in the destinations transitively.

**Today** — ViewModel-backed
> No stateless TodayContent exists — TodayScreen() takes only nav lambdas (onTxnClick, onReview, onAutoCapture) and builds TodayViewModel inline from LocalAppContainer.current. To render the whole screen in isolation you must supply an AppContainer whose repositories return canned Flows: txnRepo.observeTodayNet() -> MoneyTotals(2500.0, 1800.0, 700.0); txnRepo.observeRecent(50) -> a list of TransactionRow (e.g. id="t1", accountId="a1", amount=320.0, kind=TxnKind.EXPENSE, merchantId="m1", categoryId="c1", captureId="cap1" to show the 'auto' tag); accountRepo.observeActive(), categoryRepo.observeAll(), merchantRepo.observeAll() returning matching Account/Category/Merchant rows so the join in TodayViewModel.recent resolves names/colors; inboxRepo.observePendingCount() -> 3 for the badge; captureConfigRepo.observe() -> config with captureEnabled=false; loadOptInSeen={false}, smsCapable=true to surface CaptureOptInCard. Far simpler in isolation: render the leaf composables directly under HisaabTheme — ReviewBadge(count=3L, palette=palette, onClick={}) and CaptureOptInCard(onTurnOn={}, onMaybeLater={}) — both are stateless. The private NetCell/TxnRow are not callable externally. Wrap in HisaabTheme so LocalHisaabPalette resolves.

**ReviewBadge** — stateless
> Fully stateless: ReviewBadge(count = 3L, palette = LocalHisaabPalette.current, onClick = {}). Wrap in HisaabTheme to provide the palette. Note count is Long; passing count <= 0L renders nothing, so use a positive value to screenshot the visible state.

**Month** — ViewModel-backed
> No stateless MonthContent — MonthScreen() takes no params and builds MonthViewModel inline from LocalAppContainer.current (insightRepository, budgetRepository). For full-screen isolation supply an AppContainer whose InsightRepository returns canned Flows keyed by YearMonth: computeMonthlyTotals -> MonthlyTotals(YearMonth.of(2026,5), income=42000.0, expense=31000.0, net=11000.0, previousMonthNet=9000.0); computeCategoryBreakdown -> List<CategorySlice> (e.g. CategorySlice("c1","Food","#E5484D",12000.0,38.0), …); computePerDaySpend -> List<DayBucket>(DayBucket(epochDay, total)…); detectRecurring -> List<RecurringHit>(RecurringHit("m1","Netflix",4,499.0,<ms>)); computeBudgetProgress(ym, budgetRepo) -> List<BudgetProgress>(BudgetProgress(BudgetRow(...,categoryName="Food",monthlyCapAmount=15000.0,...), spent=12000.0, percent=80.0)). The four sub-charts are each independently renderable stateless composables and are the easiest screenshot targets (see their entries). Wrap in HisaabTheme.

**CategoryBarChart** — stateless
> Stateless: CategoryBarChart(slices = listOf(CategorySlice("c1","Food","#E5484D",12000.0,38.0), CategorySlice("c2","Transport","#0091FF",6000.0,19.0), CategorySlice("c3","Misc",null,3000.0,9.0)), palette = LocalHisaabPalette.current). Wrap in HisaabTheme. Pass a null/short hex to exercise parseColorOrAccent fallback; empty list for the empty state.

**PerDayLineChart** — stateless
> Stateless: PerDayLineChart(buckets = listOf(DayBucket(20240, 200.0), DayBucket(20245, 1500.0), DayBucket(20250, 600.0), DayBucket(20255, 900.0)), palette = LocalHisaabPalette.current). epochDay values just need to span a range; the tallest total defines the 'max' label. Wrap in HisaabTheme. Empty list for empty state.

**BudgetProgressList** — stateless
> Stateless: BudgetProgressList(budgets = listOf(BudgetProgress(BudgetRow("b1","c1","Food",15000.0,"BDT",YearMonth.of(2026,1),null,0L), spent=9000.0, percent=60.0), BudgetProgress(BudgetRow("b2","c2","Transport",4000.0,"BDT",YearMonth.of(2026,1),null,0L), spent=3600.0, percent=90.0), BudgetProgress(BudgetRow("b3","c3","Dining",5000.0,"BDT",YearMonth.of(2026,1),null,0L), spent=6000.0, percent=120.0)), palette = LocalHisaabPalette.current). The three percents exercise the accent/gold/negative branches. Wrap in HisaabTheme. Empty list for CTA state.

**RecurringList** — stateless
> Stateless: RecurringList(items = listOf(RecurringHit("m1","Netflix",4,499.0, <epochMillis e.g. 1748000000000>), RecurringHit("m2","Grameenphone",6,300.0, 0L)), palette = LocalHisaabPalette.current). formatLastSeen converts ms via the system timezone; ts=0L renders '—'. Wrap in HisaabTheme. Empty list for the empty state.

**CaptureOptInCard** — stateless
> Stateless: CaptureOptInCard(onTurnOn = {}, onMaybeLater = {}). Wrap in HisaabTheme for LocalHisaabPalette. In Today it is gated by !captureEnabled && !captureOptInSeen && smsSupported, but the composable itself takes no state and always renders the same card.

**EntryScreen (New Entry)** — ViewModel-backed
> No stateless Content wrapper exists — EntryScreen(candidateId: String? = null, onDone: () -> Unit) builds EntryViewModel internally from LocalAppContainer.current and observes container.accountRepository.observeActive() + container.categoryRepository.observeAll(). To render in isolation you must (a) provide LocalHisaabPalette (via HisaabTheme) and a LocalAppContainer whose repos return non-empty Flows, OR (b) refactor to extract a stateless EntryContent(state: EntryFormState, accounts: List<Account>, categories: List<Category>, callbacks...). EntryFormState is a plain data class easily built: EntryFormState(kind = TxnKind.EXPENSE, amount = "450", accountId = "acc1", categoryId = "cat1", merchantName = "Shwapno", notes = "Groceries", tagNames = listOf("weekly")). Account sample: Account(id="acc1", name="bKash", kind=AccountKind.MFS, institution=null, currency="BDT", balanceTracking=true, createdAt=0L, archivedAt=null). Category sample: Category(id="cat1", name="Food", parentId=null, color="#AD6B2A", icon="🍽", isDefault=true). The constituent field composables (KindSelector, AmountField, AccountPicker, CategoryPicker, NotesField in EntryFields.kt) ARE individually stateless and render directly under HisaabTheme with literal params.

**SplitEditorSheet** — stateless
> Fully stateless and renderable in isolation under HisaabTheme. Signature: SplitEditorSheet(initialSplits: List<NewSplitTransaction>, parentAmount: Double, categories: List<Category>, onSave: (List<NewSplitTransaction>) -> Unit, onDismiss: () -> Unit). Internal row state is local mutableStateListOf. Build representative state with literals: parentAmount = 500.0, initialSplits = listOf(NewSplitTransaction(300.0, "cat1", null, TxnKind.EXPENSE), NewSplitTransaction(200.0, "cat2", null, TxnKind.EXPENSE)) for the matched/Save-enabled state, categories = emptyList() is fine (category picker not wired into SplitRow). Pass empty initialSplits to see the two-zero-row default. Note: ModalBottomSheet requires a host with material3 ExperimentalMaterial3Api opt-in.

**PersonPickerSheet** — stateless
> Stateless private composable; signature PersonPickerSheet(onNewPerson: (String) -> Unit, onDismiss: () -> Unit, palette: HisaabColors.Palette). It is private to EntryScreen.kt, so isolated rendering requires either making it internal/public or copying it into a preview. Build state with literals: pass palette = HisaabColors.Light (or .Dark) and no-op callbacks; internal text input is local mutableStateOf. No ViewModel or repo needed.

**TransactionDetailScreen** — ViewModel-backed
> No stateless Content wrapper — TransactionDetailScreen(txnId: String, onDone: () -> Unit) builds TransactionDetailViewModel from LocalAppContainer.current, which combines txnRepo.observeById(txnId) + accountRepo.observeActive() + categoryRepo.observeAll() + merchantRepo.observeAll() into a StateFlow<TransactionRowDisplay?> and loads provenance from inboxRepo.getById(captureId). The rendered body depends only on a TransactionRowDisplay? (state.row) and a CandidateTransaction? (provenance), both plain data classes — extract a stateless content composable taking those two, or seed an in-memory backing store. Build the loaded state with TransactionRowDisplay(row = TransactionRow(id="t1", accountId="acc1", accountName="bKash", amount=450.0, currency="BDT", ts=1717000000000L, merchantId="m1", merchantName="Shwapno", categoryId="cat1", categoryName="Food", categoryColor="#AD6B2A", source=TxnSource.SMS, notes="Weekly groceries", kind=TxnKind.EXPENSE, parentTxnId=null, captureId="c1"), accountName="bKash", merchantName="Shwapno", categoryName="Food", categoryColor="#AD6B2A"). For the CAPTURED block build CandidateTransaction(id="c1", receivedAt=1717000000000L, channel=CaptureChannel.SMS, sender="bKash", rawBody="You have paid Tk 450.00 to SHWAPNO...", dedupHash="h", status=CaptureStatus.CONFIRMED, confidence=0.92, parsedBy=ParsedBy.CLOUD_CLAUDE, model="claude-3-5", parseError=null, amount=450.0, direction=Direction.DEBIT, currency="BDT", balanceAfter=null, refNo=null, proposedAccountId="acc1", proposedCategoryId="cat1", proposedMerchant="Shwapno", createdAt=0L). For the not-found state, render with state.row == null. The DetailRow and ProvenanceBlock helpers are stateless (ProvenanceBlock takes a CandidateTransaction + palette) but are private to the file.

**SettingsScreen** — ViewModel-backed
> No stateless content composable exists. SettingsScreen(onAccounts, onCategories, onBudgets, onRecoveryReveal, onAutoCapture, onSignedOut: () -> Unit) internally does `remember { SettingsViewModel(LocalAppContainer.current) }`. SettingsViewModel(container, scope) only touches container.secureStorage.loadString/storeString in its init + setters and container.authRepository/closeDatabase/secureStorage in signOut — it does NOT open the DB. So to render: provide LocalAppContainer with a fake/real AppContainer whose secureStorage returns null/'true' for keys 'lock_timeout_ms' and 'biometric_enabled', wrap in HisaabTheme, pass no-op lambdas for the six nav callbacks. AppContainer is an `expect class`, so a fake requires a JVM/Android `actual` or a test double exposing at minimum a SecureStorage and AuthRepository. The two collected StateFlows (lockTimeoutMs Long, biometricEnabled Boolean) come straight from secureStorage, so seeding storeString('lock_timeout_ms','300000') and ('biometric_enabled','true') produces the 5-minutes + biometric-on variant. The LockTimeoutSheet and sign-out AlertDialog are private and gated by local mutableStateOf booleans (showLockSheet/showSignOutDialog) — only reachable via taps, not via a param.

**LockTimeoutSheet** — stateless
> LockTimeoutSheet is `private` so not callable from outside the file, but it is pure: LockTimeoutSheet(current: Long, onPick: (Long)->Unit, onDismiss: ()->Unit, palette: HisaabColors.Palette). To screenshot in isolation you'd copy/expose it or extract it; call inside HisaabTheme with current=30_000L (or 0L / Long.MAX_VALUE) and LocalHisaabPalette.current for the palette, no-op onPick/onDismiss. No ViewModel or DB needed. In practice it is reached by toggling showLockSheet=true in SettingsScreen.

**AccountsScreen** — ViewModel-backed
> No stateless content composable. AccountsScreen(onBack: ()->Unit) reads LocalAppContainer.current and binds `container.accountRepository.observeActive()` (Flow<List<Account>>) plus calls container.accountRepository.cardOutstanding(id) inside CardSummaryRow. To render representative state you must provide a container whose accountRepository.observeActive() emits a fixed list, e.g. Account(id="a1", name="Cash Wallet", kind=AccountKind.CASH, institution=null, currency="BDT", balanceTracking=true, createdAt=0, archivedAt=null) and a CARD one Account(id="c1", name="BRAC Visa", kind=AccountKind.CARD, institution="BRAC", currency="BDT", balanceTracking=true, createdAt=0, archivedAt=null, creditLimit=100000.0, statementDay=5, dueDay=20); cardOutstanding("c1") should return e.g. 23450.0 so chips render. Cleanest path: implement a FakeAccountRepository : AccountRepository backing observeActive with a MutableStateFlow, and expose it via a test AppContainer actual (or a hand-rolled fake AppContainer). AddAccountSheet/RenameAccountDialog are private, gated by local mutableStateOf (showAddSheet / renamingAccount); reachable only via taps.

**AddAccountSheet** — stateless
> Private but self-contained and ViewModel-free: AddAccountSheet(onAdd: (name, kind, creditLimit, statementDay, dueDay)->Unit, onDismiss: ()->Unit) holds all its own remembered state (name, kind, creditLimitText, statementDayText, dueDayText) and reads LocalHisaabPalette.current. To screenshot in isolation, expose/extract it and call within HisaabTheme with no-op onAdd/onDismiss. The CARD variant is produced purely by tapping the CARD chip (kind local state). No DB.

**CategoriesScreen** — ViewModel-backed
> No stateless content composable. CategoriesScreen(onBack: ()->Unit) binds `container.categoryRepository.observeAll()` (Flow<List<Category>>). Provide a container whose categoryRepository.observeAll() emits e.g. listOf(Category(id="food", name="Food", parentId=null, color=null, icon="🍔", isDefault=true), Category(id="misc", name="Misc", parentId=null, color=null, icon=null, isDefault=false)). Build via a FakeCategoryRepository backed by a MutableStateFlow and a test/fake AppContainer. AddCategorySheet is private, gated by showAddSheet mutableStateOf — reachable only by tapping '+ Add'. Wrap in HisaabTheme.

**AddCategorySheet** — stateless
> Private, ViewModel-free, self-contained: AddCategorySheet(onAdd: (String, String?)->Unit, onDismiss: ()->Unit) keeps its own name/icon remembered state and reads LocalHisaabPalette.current. Expose/extract and call within HisaabTheme with no-op callbacks; no DB. Normally reached by tapping '+ Add' on CategoriesScreen.

**BudgetsScreen** — ViewModel-backed
> No stateless content composable. BudgetsScreen(onBack: ()->Unit) binds `container.budgetRepository.observeActive()` (Flow<List<BudgetRow>>) and `container.categoryRepository.observeAll()`. Empty state: have observeActive() emit emptyList(). Populated: emit listOf(BudgetRow(id="b1", categoryId="food", categoryName="Food", monthlyCapAmount=8000.0, currency="BDT", startsMonth=YearMonth.of(2026,5), archivedAt=null, createdAt=0)) — note the row reads budget.startsMonth.value (the raw "YYYY-MM" String) and monthlyCapAmount.toInt(). For AddBudgetSheet seed categoryRepository.observeAll() with a couple Category rows (it filters out ids 'salary' and 'transfer'). Build via FakeBudgetRepository + FakeCategoryRepository backed by MutableStateFlows and a fake/test AppContainer; wrap in HisaabTheme. AddBudgetSheet is private, gated by showAddSheet.

**AddBudgetSheet** — stateless
> Private but ViewModel-free and takes its data as a param: AddBudgetSheet(categories: List<Category>, onAdd: (String, Double)->Unit, onDismiss: ()->Unit); selectedCategoryId/amount are local remembered state, palette from LocalHisaabPalette.current. Expose/extract and call within HisaabTheme passing e.g. listOf(Category("food","Food",null,null,"🍔",true)) and no-op callbacks. No DB.

**AutoCaptureScreen** — ViewModel-backed
> No stateless content composable. AutoCaptureScreen(onBack, onConsent: ()->Unit) builds an AutoCaptureViewModel from many container seams. The iOS/unsupported branch is the EASIEST to render: it is gated by `container.captureService.capabilities().contains(CaptureChannel.SMS)` being false, so a container whose captureService.capabilities() omits SMS renders just IosUnavailableExplainer with no further VM data needed. For the Android body you must construct AutoCaptureViewModel(configRepo, senderRepo, accountRepo, hasSmsPermission, requestSmsPermission, runBackfill, loadApiKey, storeApiKey, clearApiKey, router: LlmRouter, onStartCapture, onStopCapture, scope) — config is `configRepo.observe(): Flow<CaptureConfig?>` (screen early-returns until non-null), so seed CaptureConfig(captureEnabled=false, engineMode=EngineMode.ON_DEVICE, onDeviceModel="gemma", cloudProvider=null, cloudModel=null, redactionEnabled=true, alwaysReview=true, autoPostThreshold=0.85, cloudConsentAt=null, retainRawBody=false, lastSmsCursor=0, updatedAt=0). For Cloud variant set engineMode=CLOUD, cloudProvider=CloudProvider.CLAUDE. senders via senderRepo.observeAll() emitting SenderMapping(id, senderId="bKash", displayName="bKash", bankType=BankType.BKASH, isFinancial=true, templateKey=null, accountId=null, createdAt=0) to trigger the unmapped prompt; accounts via accountRepo.observeActive(). Useful sub-composables ARE public and pure: EnginePicker(mode, palette, onSelect) renders the two engine RadioRows in isolation under HisaabTheme with no VM. Building the full screen requires fakes for CaptureConfigRepository/SenderRepository/AccountRepository/LlmRouter and a fake CaptureService.

**EnginePicker** — stateless
> Public and fully pure: EnginePicker(mode: EngineMode, palette: HisaabColors.Palette, onSelect: (EngineMode)->Unit). Render directly under HisaabTheme with mode=EngineMode.ON_DEVICE (or CLOUD), palette=LocalHisaabPalette.current, no-op onSelect. No ViewModel, no DB — this is the cleanest isolated render in the group besides CloudConsentScreen.

**AddSenderInline** — stateless
> Private but pure: AddSenderInline(palette: HisaabColors.Palette, onAdd: (senderId, displayName)->Unit, onCancel: ()->Unit) keeps its own remembered fields. Expose/extract and call under HisaabTheme with LocalHisaabPalette.current and no-op callbacks; no DB. Normally reached via the 'Add a sender' TextButton (local showAddSender mutableStateOf).

**CloudConsentScreen** — stateless
> FULLY STATELESS — this is the only screen in the group that needs no ViewModel or DB. CloudConsentScreen(providerName: String, consentGranted: Boolean, onGrant: ()->Unit, onRevoke: ()->Unit, onBack: ()->Unit) takes plain data and callbacks; the caller (AutoCaptureScreen flow in MainGraph) owns the AutoCaptureViewModel. Render directly under HisaabTheme: providerName="Claude", consentGranted=false (agree variant) or true (revoke variant), no-op callbacks. Reads only LocalHisaabPalette.current.

**RecoveryPhraseRevealScreen** — ViewModel-backed
> No stateless content composable; all state is local mutableStateOf (words, error) driven by a LaunchedEffect. RecoveryPhraseRevealScreen(onBack: ()->Unit) calls container.biometricAuth.authenticate(...), then container.secureStorage.loadMasterSecret() and container.mnemonicService.encode(secret). To force each state, provide a container with fakes: (Loading) biometricAuth.authenticate suspends/never returns -> spinner; (Success) biometricAuth returns BiometricResult.Success, secureStorage.loadMasterSecret() returns a 32-byte ByteArray, mnemonicService.encode returns a fixed List<String> of 24 words -> grid; (Error) loadMasterSecret() returns null -> 'Master secret missing' OR authenticate returns BiometricResult.NotAvailable / BiometricResult.Error(message). Requires fake BiometricAuth + SecureStorage + MnemonicService wired into a test AppContainer; wrap in HisaabTheme. The success path does not open the DB, only secure storage + mnemonic encode.

**SettingRow (shared component)** — stateless
> Public and fully pure: SettingRow(label: String, value: String, palette: HisaabColors.Palette, onClick: (()->Unit)? = null). Render under HisaabTheme with label="Lock timeout", value="30 seconds", palette=LocalHisaabPalette.current, onClick=null or a no-op. No ViewModel, no DB.

**Assistant Chat (AgentScreen)** — stateless
> Render AgentScreenContent(state: AgentUiState, onInput:(String)->Unit, onSend:()->Unit, onMicTap:()->Unit, onNewChat:()->Unit, onConsent:()->Unit, onToggleInclude:(Int)->Unit, onEditWrite:(Int,JsonObject)->Unit, onApply:()->Unit, onClose:()->Unit) wrapped in HisaabTheme. No VM/DB needed — AgentUiState is a plain data class with all defaults. Build representative state directly: AgentUiState(conversationId="c1", messages=listOf(AgentMessage(id="m1", conversationId="c1", role=AgentRole.USER, content="I lent Karim 2000", proposedWrites=emptyList(), appliedSummary=null, createdAt=0L), AgentMessage(id="m2", conversationId="c1", role=AgentRole.ASSISTANT, content="Got it — review below.", proposedWrites=emptyList(), appliedSummary=null, createdAt=1L)), gate=AgentAvailability.Ready, voiceAvailable=true). For the review state set review=listOf(ProposedWrite("record_lend_borrow", buildJsonObject{ put("kind","lend"); put("amount",2000.0); put("person","Karim") })) and reviewIncluded=setOf(0). For other states set inFlight=true, or error="Network problem…", or confirmation="Saved.", or gate=AgentAvailability.Unavailable("Add a cloud model + API key in Settings…"), or gate=AgentAvailability.NeedsConsent. Pass {} no-op callbacks. The full AgentScreen(vm,onClose) wrapper needs an AgentViewModel built from ConversationRepository + AgentRuntime + setConsent + SpeechToText (sourced from LocalAppContainer in MainGraph) — avoid it for isolated rendering.

**Agent Consent Dialog** — stateless
> Render AgentConsentDialog(onConsent:()->Unit, onDismiss:()->Unit) directly inside HisaabTheme with two no-op {} callbacks. Fully stateless — no VM, DB, or AgentUiState required; only reads LocalHisaabPalette which HisaabTheme provides.

**Review Card (proposed writes)** — stateless
> Render ReviewCard(writes: List<ProposedWrite>, included: Set<Int>, onToggle:(Int)->Unit, onEdit:(Int,JsonObject)->Unit, onApply:()->Unit, modifier) in HisaabTheme. Pure stateless. Build writes with kotlinx.serialization.json.buildJsonObject, e.g. listOf(ProposedWrite("add_transaction", buildJsonObject{ put("kind","expense"); put("amount",500.0); put("account","Cash"); put("category","Food") }), ProposedWrite("transfer", buildJsonObject{ put("amount",1000.0); put("fromAccount","Bank"); put("toAccount","Cash") }), ProposedWrite("record_lend_borrow", buildJsonObject{ put("kind","lend"); put("amount",2000.0); put("person","Karim") })); included=setOf(0,1,2); no-op callbacks. Depends on AmountField from app.hisaab.screens.entry (also stateless). For Apply-disabled state pass included=emptySet().

**Review Inbox (capture review)** — stateless
> Render ReviewInboxContent(pending: List<ReviewCandidate>, onBack:()->Unit, onConfirm:(String)->Unit, onConfirmAllHighConfidence:()->Unit, onDismiss:(String)->Unit, onEdit:(String)->Unit) in HisaabTheme — no AppContainer/VM. Build ReviewCandidate(candidate=CandidateTransaction(id="c1", receivedAt=0L, channel=CaptureChannel.SMS, sender="bKash", rawBody="You have received Tk 500…", dedupHash="h", status=CaptureStatus.PENDING, confidence=0.92, parsedBy=ParsedBy.TEMPLATE, model=null, parseError=null, amount=500.0, direction=Direction.CREDIT, currency="BDT", balanceAfter=null, refNo=null, proposedAccountId="a1", proposedCategoryId="cat1", proposedMerchant="Rahim Store", createdAt=0L), accountName="bKash", categoryName="Food"). For empty state pass emptyList(); for the bulk button include a candidate with confidence>=0.85; for error state set parseError="missing amount" and amount=null. The full ReviewInboxScreen(onBack,onEdit) wrapper builds ReviewInboxViewModel from LocalAppContainer (captureInboxRepository, accountRepository, categoryRepository, confirmCandidate) — avoid for isolated rendering.

**Auto-Post Undo Snackbar Host** — ViewModel-backed
> No stateless content split and no params other than modifier. AutoPostSnackbarHost(modifier) calls LocalAppContainer.current and collects container.captureEvents.filterIsInstance<CaptureEvent.AutoPosted>() in a LaunchedEffect, so it cannot render its snackbar without an AppContainer that emits an AutoPosted event. To exercise it you must provide a fake AppContainer via LocalAppContainer whose captureEvents is a MutableSharedFlow and emit CaptureEvent.AutoPosted(amount, sender, direction, txnId, candidateId), plus stub transactionRepository.delete and captureInboxRepository.markDismissed for the Undo path. For a pure visual screenshot of just the snackbar, it is simpler to drive a Material3 SnackbarHostState directly with the same label format from labelFor() rather than mount this host.

**People List** — ViewModel-backed
> PeopleListScreen(onPersonClick:(String)->Unit) has no stateless content split: it builds PeopleViewModel from LocalAppContainer (personRepository, lendBorrowRepository, accountRepository) and reads container.contactPicker. To render the real screen, provide a fake AppContainer via LocalAppContainer whose personRepository.observeAll() emits a fixed List<PersonWithBalance> — e.g. listOf(PersonWithBalance(Person("p1","Karim","+8801…"), 2000.0), PersonWithBalance(Person("p2","Rahim",null), -500.0), PersonWithBalance(Person("p3","Sadia",null), 0.0)) — and contactPicker.isAvailable() returning a chosen Boolean; emptyList() drives the empty state. For a pure-Compose screenshot without a container, the simplest path is to extract/replicate the PersonRow + header into a harness fed PersonWithBalance values, since PersonRow itself is a private stateless composable.

**Person Detail** — ViewModel-backed
> PersonDetailScreen(personId:String, onBack:()->Unit) has no stateless content split: it builds PeopleViewModel from LocalAppContainer and reads viewModel.personDetail(personId) (combines personRepository.observeById + lendBorrowRepository.observeForPerson) and viewModel.activeAccounts (accountRepository.observeActive). To render the real screen supply a fake AppContainer via LocalAppContainer whose observeById(id) emits PersonWithBalance(Person("p1","Karim","+8801…"), 2000.0) and observeForPerson(id) emits e.g. listOf(LendBorrowRow("l1","p1","Karim",2000.0,LendBorrowDirection.LENT,"Lunch",0L,null,LendBorrowStatus.OPEN), LendBorrowRow("l2","p1","Karim",500.0,LendBorrowDirection.BORROWED,null,0L,null,LendBorrowStatus.SETTLED)); observeActive() emits a couple of Account rows for the SettleDialog. Emit null from observeById to show the loading spinner; emit empty records for the empty-history state. The private LendBorrowRowItem and SettleDialog composables are stateless and can be rendered directly in a harness with hand-built LendBorrowRow / Account values if a container is undesirable.
