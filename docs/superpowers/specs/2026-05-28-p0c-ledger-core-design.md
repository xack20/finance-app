# Hisaab — P0c: Ledger Core, Foundation Closure & First Real Functionality

**Status:** Approved
**Date:** 2026-05-28
**Phase:** P0c — Foundation completion + manual entry + Today + lend/borrow + monthly stats
**Depends on:** [`2026-05-27-hisaab-master-architecture-design.md`](./2026-05-27-hisaab-master-architecture-design.md), [`2026-05-28-p0b-foundation-auth-design.md`](./2026-05-28-p0b-foundation-auth-design.md)
**Closes:** [`docs/tech-debt.md`](../../tech-debt.md) items C1–C9 (Critical) and H4 (`@Ignore`d crypto tests)

---

## 1. Summary

P0c is the broadest phase yet. It closes the nine critical foundation gaps left after P0b and delivers the first phase of real user functionality. At the end of P0c, a user on a fresh install can complete onboarding, land on a Today screen with a default Cash account auto-created, record transactions (including lend/borrow with optional contact picker), see persons with net balances, browse a Month screen with category bars + per-day chart + recurring detection + budget progress, and set per-category budgets. The app re-locks after 30 seconds in the background and unlocks via biometric. The encrypted database actually opens, persists everything across kills, and the recovery phrase is real BIP39.

---

## 2. Scope

### Delivers

**Foundation closures (must-ship):**

- Hand-wired `AppContainer` DI root
- Encrypted DB actually opens after onboarding completes
- `master_secret` written to `SecureStorage` (biometric-gated on Android)
- `UserProfile` persisted to DB at end of onboarding
- Real `SupabaseAuthRepository` wired in `App.kt` (replaces `StubAuthRepository`, deleted)
- Biometric `Enable` path actually calls `BiometricAuth.authenticate()`
- `MnemonicService` switched to standard BIP39 SHA256 checksum
- `RecoveryEntryScreen` for new-device recovery (closes `OnboardingKey` state)
- App lifecycle lock — 30s default; configurable in Settings (Immediate / 30s / 5m / Never)
- `CryptoServiceTest` moved to `androidInstrumentedTest` and passing

**Ledger core (new functionality):**

- `AccountRepository` + auto-create default Cash (BDT) account on first DB open
- 12 seed categories (`Food`, `Transport`, `Bills`, `Salary`, `Lend`, `Borrow`, `Health`, `Education`, `Shopping`, `Entertainment`, `Other`, `Transfer`)
- `CategoryRepository`, `MerchantRepository`, `TagRepository`, `TransactionRepository`, `PersonRepository`, `LendBorrowRepository`, `BudgetRepository`, `AttachmentRepository`, `InsightRepository`
- Rich `EntryScreen` — amount, account, kind (`EXPENSE` | `INCOME` | `TRANSFER` | `LEND` | `BORROW` | `SETTLEMENT`), category, date/time, merchant w/ autocomplete, notes, tag chips, receipt attachment (E2EE blob), splits sub-sheet
- Lend/Borrow toggle on the entry form pivots the same form (reveals Person + Due-date fields)
- `TodayScreen` rewrite — today's in/out/net + recent transactions grouped by day
- `TransactionDetailScreen` — view, edit, delete (long-press from Today)
- `MonthScreen` — month switcher, in/out/net + previous-month delta, category bar chart, per-day line chart, recurring detection list, budget progress bars
- `PeopleListScreen` + `PersonDetailScreen` — phone-as-dedup, net balances, settle action
- `SettingsScreen` + AccountsScreen / CategoriesScreen / BudgetsScreen / RecoveryPhraseRevealScreen
- V2 schema migration: `tag`, `txn_tag`, `attachment`, `budget` tables; `txn.parent_txn_id`, `txn.kind`, `account.archived_at` columns

### Explicitly out of scope

- iOS Keychain full implementation (still stub) — deferred to P0d
- iOS Xcode wizard / simulator verification — P0d
- Web browser verification — P0d
- SMS / email / voice / OCR capture engines — M3
- Sync / Realtime — M4
- Advisor agent (LLM phrasing) — M5
- Real BD SMS provider — M5
- Locale string resources (`stringResource()` wiring) — P0d
- 16 KB native library alignment — P0d

---

## 3. Architecture decisions

| Decision | Choice | Rationale |
|---|---|---|
| DI | Hand-wired `AppContainer` (no Koin) | ~10-node graph; explicit is easier to debug with native libsodium / SQLCipher loading |
| First-account model | Auto-create default Cash (BDT) on first DB open | Zero-friction time-to-value; account management discoverable in Settings |
| Transaction entry form | Rich (amount, account, kind, category, date/time, merchant, notes, tags, attachment, splits, lend/borrow toggle) | One form to learn; lend/borrow as a flag, not a separate flow |
| Person model | Free-text name + optional contact picker; phone-as-dedup | Bangladeshi lending is informal; manual names required for non-contact persons |
| Monthly stats scope | Full insights (numbers + category bars + per-day chart + recurring + budget vs actual) | Deterministic queries today; M5 advisor wraps with phrasing later |
| Lock timeout | 30s default; Settings exposes Immediate / 30s / 5m / Never | Matches banking app norms; immediate is too strict for realistic use |
| Domain layer | Repository-per-table + ViewModel-per-screen; no use case layer | Use cases added later when there are real second consumers (voice/SMS parsers in M3) |
| Splits | Parent + child rows in same `txn` table via `parent_txn_id` | One table, one timeline query (`WHERE parent_txn_id IS NULL`) |
| Attachments | XChaCha20-Poly1305 file on disk + IV in encrypted DB | Defence in depth — disk file is meaningless without DB key |
| Transaction kind | New `txn.kind` column with values `EXPENSE` / `INCOME` / `TRANSFER` / `LEND` / `BORROW` / `SETTLEMENT` | Separates user intent from `source` (which captures how the row was created) |

---

## 4. Schema additions (V2 migration)

`composeApp/src/commonMain/sqldelight/migrations/2.sqm`:

```sql
-- Tags (many-to-many with transactions)
CREATE TABLE tag (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    color TEXT,
    created_at INTEGER NOT NULL
);

CREATE TABLE txn_tag (
    txn_id TEXT NOT NULL REFERENCES txn(id) ON DELETE CASCADE,
    tag_id TEXT NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    PRIMARY KEY (txn_id, tag_id)
);
CREATE INDEX idx_txn_tag_tag ON txn_tag(tag_id);

-- Attachments (E2EE blob on disk; IV + path in DB)
CREATE TABLE attachment (
    id TEXT NOT NULL PRIMARY KEY,
    txn_id TEXT NOT NULL REFERENCES txn(id) ON DELETE CASCADE,
    mime_type TEXT NOT NULL,
    file_path TEXT NOT NULL,
    encrypted_iv BLOB NOT NULL,
    size_bytes INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX idx_attachment_txn ON attachment(txn_id);

-- Budgets (per-category monthly cap)
CREATE TABLE budget (
    id TEXT NOT NULL PRIMARY KEY,
    category_id TEXT NOT NULL REFERENCES category(id) ON DELETE CASCADE,
    monthly_cap_amount REAL NOT NULL,
    currency TEXT NOT NULL DEFAULT 'BDT',
    starts_month TEXT NOT NULL,       -- 'YYYY-MM'
    archived_at INTEGER,
    created_at INTEGER NOT NULL
);
CREATE INDEX idx_budget_category ON budget(category_id);

-- Splits + kind on existing txn
ALTER TABLE txn ADD COLUMN parent_txn_id TEXT REFERENCES txn(id) ON DELETE CASCADE;
ALTER TABLE txn ADD COLUMN kind TEXT NOT NULL DEFAULT 'EXPENSE';
CREATE INDEX idx_txn_parent ON txn(parent_txn_id);

-- Account archived flag (preserve historical txn links)
ALTER TABLE account ADD COLUMN archived_at INTEGER;
```

**Default category seed (in Kotlin, called from `AppContainer.openDatabase()`):**

```kotlin
private val DEFAULT_CATEGORIES = listOf(
    Category("food",          "Food & dining",  null, "#ad6b2a", "🍽", true),
    Category("transport",     "Transport",      null, "#5b8fb9", "🚗", true),
    Category("bills",         "Bills",          null, "#7a5c9e", "🧾", true),
    Category("salary",        "Salary",         null, "#2e7d4f", "💼", true),
    Category("lend",          "Lent",           null, "#c8964a", "↗",  true),
    Category("borrow",        "Borrowed",       null, "#b5402c", "↙",  true),
    Category("health",        "Health",         null, "#d68945", "🩺", true),
    Category("education",     "Education",      null, "#6b8e23", "📚", true),
    Category("shopping",      "Shopping",       null, "#9e6b9e", "🛍", true),
    Category("entertainment", "Entertainment",  null, "#c8964a", "🎬", true),
    Category("other",         "Other",          null, "#6f6453", "•",  true),
    Category("transfer",      "Transfer",       null, "#6f6453", "⇄",  true),
)
```

`is_default = 1` for all seed categories. `CategoryRepository.ensureDefaults()` uses `INSERT OR IGNORE` so re-seeding is a no-op.

---

## 5. Foundation closures (the 9 critical items)

| # | Tech debt | Fix |
|---|---|---|
| C1 + C2 + C4 + C5 | DB never opens; profile discarded; no DI; key not persisted | New `AppContainer` owns `SecureStorage`, `BiometricAuth`, `AuthRepository`, `CryptoService`, `MnemonicService`, `DatabaseDriverFactory`, lazily-opened `HisaabDatabase`. `OnboardingViewModel.completeProfile()` writes `master_secret` to `SecureStorage`, derives `db_key`, opens DB via `AppContainer.openDatabase(masterSecret)`, inserts `UserProfile`, transitions to `Authenticated`. `App()` reads `LocalAppContainer.current` from a `CompositionLocal`. |
| C3 | StubAuthRepository | Delete `StubAuthRepository.kt`. `AppContainer` exposes `SupabaseAuthRepository` instance. Stays in Supabase test mode for P0c. |
| C6 | Biometric Enable/Skip identical | `BiometricSetupScreen` Enroll → `BiometricAuth.authenticate("Set up Hisaab", "Confirm to enable biometric unlock")` → on success enable biometric flag in `SecureStorage`, write secret. Skip → write secret without the biometric flag (in P0c "Skip" means "no lock — open on relaunch"; password fallback is P0d). |
| C7 | Non-standard BIP39 checksum | Switch `MnemonicService.encode/decode` to libsodium `crypto_hash_sha256` for the checksum byte. Tests updated to use canonical BIP39 vectors (e.g., entropy `0x00…00` → 24 words starting with "abandon"). |
| C8 | OnboardingKey has no UI | New `RecoveryEntryScreen`: scrollable column of 24 `OutlinedTextField`s with BIP39 prefix autocomplete. Decode → derive `db_key` → try open DB. On failure (wrong phrase / no encrypted_ops yet for this device) → friendly error. On success → emit `Authenticated`. |
| C9 | Lifecycle never triggers Locked | Two-part wiring: (a) `AppLifecycle` expect class — Android observer of `ProcessLifecycleOwner`, calls `appViewModel.onAppBackground()` / `onForeground()`. (b) `AppViewModel` introduces a `lockTimeoutSeconds` (configurable; default 30) — on background it starts a timer; if foreground arrives before expiry, no lock; if expiry fires, close DB and emit `Locked`. `LockScreen` calls `BiometricAuth.authenticate()` → loads secret → re-opens DB → emits `Authenticated`. |

---

## 6. Repository layer

All in `commonMain/data/`. Each constructor-injected by `AppContainer`.

```kotlin
class AccountRepository(private val db: HisaabDatabase) {
    fun observeActive(): Flow<List<Account>>
    suspend fun add(name: String, kind: AccountKind, institution: String?, currency: String = "BDT"): String
    suspend fun rename(id: String, name: String)
    suspend fun archive(id: String)
    suspend fun ensureDefaultCashAccount(): String        // idempotent
}

class CategoryRepository(private val db: HisaabDatabase) {
    fun observeAll(): Flow<List<Category>>
    suspend fun ensureDefaults()                          // seeds 12 defaults via INSERT OR IGNORE
    suspend fun add(name: String, color: String?, icon: String?, parentId: String?): String
}

class MerchantRepository(private val db: HisaabDatabase) {
    fun observeAll(): Flow<List<Merchant>>
    suspend fun upsertByName(name: String, defaultCategoryId: String? = null): String  // dedup by normalized_name
    fun searchByPrefix(prefix: String): Flow<List<Merchant>>
}

class TagRepository(private val db: HisaabDatabase) {
    fun observeAll(): Flow<List<Tag>>
    suspend fun upsertByName(name: String): String
    suspend fun linkToTxn(txnId: String, tagIds: List<String>)
    fun observeTagsForTxn(txnId: String): Flow<List<Tag>>
}

class TransactionRepository(
    private val db: HisaabDatabase,
    private val merchantRepo: MerchantRepository,
    private val tagRepo: TagRepository,
) {
    fun observeRecent(limit: Int = 50): Flow<List<TransactionRow>>
    fun observeForDay(epochDayMs: Long): Flow<List<TransactionRow>>
    fun observeForMonth(yearMonth: YearMonth): Flow<List<TransactionRow>>
    suspend fun add(input: NewTransaction): String
    suspend fun addSplits(parentId: String, children: List<NewSplitTransaction>)
    suspend fun update(id: String, patch: TransactionPatch)
    suspend fun delete(id: String)
    fun observeTodayNet(): Flow<MoneyTotals>
}

class PersonRepository(private val db: HisaabDatabase) {
    fun observeAll(): Flow<List<PersonWithBalance>>
    suspend fun upsertFromContact(name: String, phone: String?): String   // phone-as-dedup
    suspend fun addManual(name: String): String                            // never auto-merges
    fun observeById(id: String): Flow<PersonWithBalance?>
}

class LendBorrowRepository(
    private val db: HisaabDatabase,
    private val txnRepo: TransactionRepository,
) {
    suspend fun record(input: NewLendBorrow): String                      // writes lend_borrow + linked txn
    suspend fun settle(lendBorrowId: String, settlementAmount: Double, accountId: String)
    fun observeForPerson(personId: String): Flow<List<LendBorrowRow>>
    fun observeOpen(): Flow<List<LendBorrowRow>>
}

class BudgetRepository(private val db: HisaabDatabase) {
    fun observeActive(): Flow<List<BudgetRow>>
    suspend fun set(categoryId: String, monthlyCapAmount: Double, startsMonth: YearMonth): String
    suspend fun archive(id: String)
}

class AttachmentRepository(
    private val db: HisaabDatabase,
    private val blobCrypto: BlobCrypto,
    private val masterSecretProvider: () -> ByteArray?,
    private val fileStore: PlatformFileStore,
) {
    suspend fun attach(txnId: String, imageBytes: ByteArray, mimeType: String): String
    suspend fun decrypt(attachmentId: String): ByteArray?
    suspend fun delete(attachmentId: String)
}

class InsightRepository(private val db: HisaabDatabase) {
    fun computeMonthlyTotals(yearMonth: YearMonth): Flow<MonthlyTotals>      // in / out / net + previous-month delta
    fun computeCategoryBreakdown(yearMonth: YearMonth): Flow<List<CategorySlice>>
    fun computePerDaySpend(yearMonth: YearMonth): Flow<List<DayBucket>>
    fun detectRecurring(): Flow<List<RecurringHit>>                           // group by merchant, count ≥ 3
    fun computeBudgetProgress(yearMonth: YearMonth): Flow<List<BudgetProgress>>
}
```

Domain models live in `commonMain/domain/` as plain `data class`es so UI never imports DB internals.

---

## 7. Platform expect/actual additions

| Interface | commonMain | androidMain | iosMain | wasmJsMain |
|---|---|---|---|---|
| `SecureStorage` | EXISTING | EXISTING | stub (P0b) | EXISTING |
| `BiometricAuth` | EXISTING | EXISTING | EXISTING | no-op |
| `ContactPicker` | NEW expect class | `ContactsContract` query w/ `READ_CONTACTS` permission | stub returning empty | no-op |
| `ImagePicker` | NEW expect class | `ActivityResultContracts.PickVisualMedia` | stub | no-op |
| `PlatformFileStore` | NEW expect class | `context.filesDir/attachments/` | `NSDocumentDirectory` | no-op |
| `AppLifecycle` | NEW expect class | `ProcessLifecycleOwner` observer | `UIApplicationDidEnterBackground` (stub for now) | no-op |

---

## 8. Screen structure & navigation

```
App() — composes by AppState:
├── Loading        → SplashScreen
├── Unauthenticated / Onboarding → OnboardingGraph (existing)
├── OnboardingKey  → RecoveryEntryScreen (NEW — closes C8)
├── Locked         → LockScreen → BiometricAuth.authenticate() → Authenticated
└── Authenticated  → MainGraph (NEW)

MainGraph — bottom-nav with 4 tabs + modal entry:
├── tab: Today          → TodayScreen
├── tab: Month          → MonthScreen
├── tab: People         → PeopleListScreen → PersonDetailScreen
├── tab: Settings       → SettingsScreen
│                       → AccountsScreen
│                       → CategoriesScreen
│                       → BudgetsScreen
│                       → RecoveryPhraseRevealScreen (biometric-gated)
└── modal (FAB):        → EntryScreen (new transaction / lend / borrow)
                        → TransactionDetailScreen
```

### EntryScreen — the flagship

```
┌─────────────────────────────────────────┐
│ ← Cancel              New entry    Save │
├─────────────────────────────────────────┤
│ [Expense] [Income] [Lend] [Borrow] [⇄]  │  Kind selector (segmented)
│                                         │
│             ৳ 0                          │  Big editable amount (hero)
│                                         │
│ Account     › Cash                      │
│ Category    › Food & dining             │
│ When        › Today, 6:42 PM            │
│ Merchant    › [autocomplete dropdown]   │
│ Notes       │ (multiline)               │
│ Tags        › [chip input]              │
│ Photo       › 📎 Add receipt            │
│ Split       › Make this a split entry   │  → SplitEditorSheet
│                                         │
│ (visible only when kind=Lend/Borrow:)   │
│ Person      › [contact picker or new]   │
│ Due date    › Optional                  │
└─────────────────────────────────────────┘
```

`EntryViewModel` holds `EntryFormState`. The Lend/Borrow segments don't open a different form — they reveal Person + Due-date fields. `Save` writes either a plain `txn` row (Expense/Income/Transfer) or a `lend_borrow` row + linked `txn` (Lend/Borrow/Settlement).

---

## 9. File map

```
composeApp/src/commonMain/
├── kotlin/app/hisaab/
│   ├── App.kt                                  MODIFY  ← LocalAppContainer + state routing
│   ├── AppContainer.kt                         NEW     ← hand-wired DI root
│   ├── AppViewModel.kt                         MODIFY  ← lock timer, lifecycle hooks, open/close DB
│   ├── auth/
│   │   ├── StubAuthRepository.kt               DELETE
│   │   ├── SupabaseAuthRepository.kt           EXISTING
│   │   └── AuthRepository.kt                   EXISTING
│   ├── crypto/
│   │   ├── CryptoService.kt                    EXISTING
│   │   ├── MnemonicService.kt                  MODIFY  ← real SHA256 checksum (C7)
│   │   └── BlobCrypto.kt                       NEW     ← XChaCha20-Poly1305 attachment encryption
│   ├── data/                                   NEW     ← 10 repository files
│   ├── domain/                                 NEW     ← 11 domain model files
│   ├── platform/
│   │   ├── SecureStorage.kt                    EXISTING
│   │   ├── BiometricAuth.kt                    EXISTING
│   │   ├── ContactPicker.kt                    NEW expect
│   │   ├── ImagePicker.kt                      NEW expect
│   │   ├── PlatformFileStore.kt                NEW expect
│   │   └── AppLifecycle.kt                     NEW expect
│   └── screens/
│       ├── LockScreen.kt                       MODIFY  ← actually calls BiometricAuth
│       ├── onboarding/OnboardingViewModel.kt   MODIFY  ← write secret to SecureStorage, call BiometricAuth (C5+C6)
│       ├── recovery/RecoveryEntryScreen.kt     NEW     ← C8
│       ├── main/{MainGraph,MainViewModel}.kt   NEW
│       ├── today/{TodayScreen,TodayViewModel}.kt          REWRITE TodayScreen
│       ├── entry/{EntryScreen,EntryViewModel,EntryFormState,SplitEditorSheet,MerchantAutocomplete}.kt  NEW
│       ├── transaction/{TransactionDetailScreen,TransactionDetailViewModel}.kt  NEW
│       ├── month/{MonthScreen,MonthViewModel,CategoryBarChart,PerDayLineChart,RecurringList,BudgetProgressList}.kt  NEW
│       ├── people/{PeopleListScreen,PersonDetailScreen,PeopleViewModel}.kt  NEW
│       └── settings/{SettingsScreen,SettingsViewModel,AccountsScreen,CategoriesScreen,BudgetsScreen,RecoveryPhraseRevealScreen}.kt  NEW
└── sqldelight/
    ├── migrations/2.sqm                        NEW
    └── app/hisaab/db/
        ├── HisaabDatabase.sq                   MODIFY
        ├── AccountQueries.sq                   NEW
        ├── TransactionQueries.sq               NEW
        ├── PersonQueries.sq                    NEW
        ├── LendBorrowQueries.sq                NEW
        ├── BudgetQueries.sq                    NEW
        └── InsightQueries.sq                   NEW

composeApp/src/androidMain/kotlin/app/hisaab/
├── AppContainerAndroid.kt                      NEW     ← actual constructor with Context
├── MainActivity.kt                              MODIFY  ← lifecycle hooks
├── platform/ContactPicker.kt                    NEW     ← ContactsContract
├── platform/ImagePicker.kt                      NEW     ← PickVisualMedia
├── platform/PlatformFileStore.kt                NEW
└── platform/AppLifecycle.kt                     NEW

composeApp/src/iosMain/kotlin/app/hisaab/
├── platform/ContactPicker.kt                    NEW     ← stub (returns empty)
├── platform/ImagePicker.kt                      NEW     ← stub
├── platform/PlatformFileStore.kt                NEW     ← NSDocumentDirectory
└── platform/AppLifecycle.kt                     NEW     ← stub

composeApp/src/wasmJsMain/kotlin/app/hisaab/
└── platform/{ContactPicker,ImagePicker,PlatformFileStore,AppLifecycle}.kt  NEW  ← all no-op

composeApp/src/commonTest/kotlin/app/hisaab/
├── crypto/MnemonicServiceTest.kt               MODIFY  ← BIP39 test vectors
├── data/                                       NEW     ← 6 repository tests using JdbcSqliteDriver in-memory
├── screens/                                    NEW     ← 4 ViewModel tests
└── AppViewModelTest.kt                          MODIFY  ← lock-timeout tests

composeApp/src/androidInstrumentedTest/kotlin/app/hisaab/crypto/
└── CryptoServiceInstrumentedTest.kt            NEW     ← un-ignores libsodium tests (H4)

gradle/libs.versions.toml                       MODIFY  ← + sqldelight-sqlite-driver (test dep)
composeApp/build.gradle.kts                     MODIFY  ← commonTest gets jdbc-sqlite-driver
docs/tech-debt.md                               MODIFY  ← move C1–C9 + H4 to ## Closed
```

---

## 10. Verification gate

P0c is complete when the following pass on a fresh install on Android:

1. App launch → SplashScreen → WelcomeScreen
2. Complete 5-screen onboarding (real Supabase OTP in test mode) → `master_secret` in Keystore → DB opens → UserProfile row inserted → default Cash account auto-created → 12 default categories seeded → MainGraph (Today tab)
3. Tap FAB → EntryScreen → add an Expense: amount ৳150, merchant "Aarong" (autocomplete suggests after second char on next entry), category Food → Save → see row on Today, today's `out` total updates
4. Add a Lend transaction → person Karim (typed manually) → ৳500 → Save → People tab shows Karim with balance `-৳500` (you lent)
5. Add a Settlement against Karim → balance returns to ৳0; lend_borrow status becomes SETTLED
6. Tap Month tab → see in/out/net + previous-month delta (0 for fresh install) + category bar showing Food + Lent + per-day chart with two bars
7. Set a budget on Food = ৳5000 → return to Month → budget progress bar shows ৳150 / ৳5000
8. Kill app → relaunch → biometric prompt → Today restored with all rows
9. Background app for 30s → foreground → LockScreen → biometric → Today restored
10. Open Settings → Recovery phrase reveal → biometric prompt → 24-word phrase displayed (matches the one shown at onboarding)
11. All unit + instrumented tests green: `./gradlew :composeApp:testDebugUnitTest :composeApp:connectedDebugAndroidTest`
12. `./gradlew :composeApp:assembleDebug` BUILD SUCCESSFUL
13. `docs/tech-debt.md` items C1–C9 + H4 moved to ## Closed

---

## 11. Open questions deferred to P0d

- iOS Keychain full implementation (H1) and iOS simulator verification (H2)
- Web browser verification (H3)
- Real BD SMS provider (H5)
- 16 KB native library alignment (H6)
- Locale string resources (`stringResource()` wiring) — M1 in tech debt
- Lock policy persistence + Settings UI for it (M2)
- Accessibility audit (M3)
- iOS SQLCipher verification on simulator (M4)
- Screenshot prevention on RecoveryPhraseScreen (M6)
- Argon2id timing benchmark on low-end devices (M7)
