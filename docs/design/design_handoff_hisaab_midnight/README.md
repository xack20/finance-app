# Handoff: Hisaab — "Midnight" redesign (complete app)

## Overview
Hisaab (হিসাব — "accounts/reckoning") is a **privacy-first personal-finance app for Bangladesh**. Everything stays on-device (SQLCipher-encrypted, biometric-gated, 24-word recovery). This bundle is a **complete UI redesign** in a new visual language we call **"Midnight"** — a dark neo-bank aesthetic with one electric‑lime accent, oversized numerals, glass cards, and a tactile keypad entry.

It covers **every screen**: onboarding, lock/restore, Today, Insights (Month), keypad Entry, Transaction detail, Assistant (with voice), Review inbox, People, and the full Settings suite (Accounts, Categories, Budgets, Auto‑capture, Cloud consent, Recovery reveal).

## About the design files
The files in this bundle are **design references built in HTML/React (via in-browser Babel)** — prototypes that show the intended look, layout, copy, and interactions. They are **not production code to copy verbatim**. Your task is to **recreate these designs inside your app's real environment** — Kotlin Multiplatform + Compose Multiplatform, per the original project (`HisaabTheme`, `LocalHisaabPalette`, the state-driven `AppViewModel`/`MainGraph` navigation). Reuse the codebase's existing components, theme, and navigation; treat the HTML as the source of truth for *appearance and behavior*, not structure.

If you're targeting a different stack, port the tokens and per-screen specs below into that stack's idioms.

## Fidelity
**High-fidelity.** Final colors, typography, spacing, radii, and interaction states are all specified. Recreate the UI faithfully using your codebase's libraries. The one exception: data is mock/illustrative (sample transactions, balances, people) — wire to real repositories.

---

## Design tokens

### Color (dark theme — the default and primary theme)
| Token | Hex | Role |
|---|---|---|
| `bg` | `#0A0B0E` | App canvas |
| `bg-2` | `#101218` | Inset / keypad / track |
| `surface` | `#161922` | Cards, sheets |
| `surface-2` | `#1D212B` | Raised controls |
| `hair` | `rgba(255,255,255,0.07)` | Hairline borders |
| `hair-2` | `rgba(255,255,255,0.04)` | Inner dividers |
| `glass` | `rgba(255,255,255,0.045)` | Glass control fill |
| `text` | `#F3F5F8` | Primary text |
| `muted` | `#98A0AD` | Secondary text |
| `faint` | `#5C6470` | Tertiary text / placeholders |
| `lime` (accent) | `#CBF24A` | Brand, CTAs, active states |
| `on-lime` | `#0A0B0E` | Text/icon on lime |
| `lime-soft` | `rgba(203,242,74,0.12)` | Accent tint backgrounds |
| `pos` | `#46E08A` | Income / positive |
| `neg` | `#FF6B5C` | Expense / destructive |
| `pos-soft` | `rgba(70,224,138,0.12)` | Positive tint |
| `neg-soft` | `rgba(255,107,92,0.12)` | Negative tint |

**Category hues** (charts, glyph chips): violet `#8B7CFF`, blue `#4EA8FF`, teal `#2DD4BF`, amber `#FFB13C`, pink `#FF6FB5`, lime `#CBF24A`, rose `#FF6B5C`, slate `#8A93A3`.

Glyph chips render the icon at the category hue over a `color-mix(in srgb, <hue> 16%, transparent)` fill, radius 14.

### Typography
- **Display** — `Space Grotesk` (400/500/600/700), letter-spacing −0.02em. Used for headlines, hero amounts, big numbers.
- **Sans (UI/body)** — `Hanken Grotesk` (400/450/500/600/700).
- **Mono (tabular figures)** — `Space Mono` (400/700), `font-variant-numeric: tabular-nums`. Used for all money figures and codes.
- **Bengali** — `Hind Siliguri` / `Noto Sans Bengali` fallbacks (wordmark হিসাব, Taka mark ৳ U+09F3).

Representative sizes: hero balance 50px display/600; screen titles 30px display/600; section eyebrow 11px sans/600 uppercase +0.14em tracking `faint`; body 15–16px; list amount 16px mono/700; money sign uses `−`/`+` prefix.

### Shape & elevation
- Radii: field `16`, card `22`, sheet top `26`, pill `999`.
- Card: `surface` fill, `1px solid hair`, shadow `0 1px 0 rgba(255,255,255,.04) inset, 0 20px 50px -24px rgba(0,0,0,.8)`.
- Lime glow (FAB, focus): `0 0 0 1px rgba(203,242,74,.5), 0 8px 30px -6px rgba(203,242,74,.45)`.
- Gutter (screen horizontal padding): `20px`.
- Floating dock height ≈ `76px`; tab screens reserve ~`104px` bottom padding behind it.

### Motion
- `fade` 0.28s ease (screen enter, slight translateY).
- `up` 0.28s cubic-bezier(.2,.9,.2,1) (sheets, keypad entry).
- `pulse` (typing dots, listening bars), `glow` (assistant orb, listening mic).
- Tap feedback: `transform: scale(.97)` 0.12s.

---

## Global structure & navigation
State-driven root (no nav library at root), mirroring the original `AppState`:
`splash → onboarding(welcome→otp→biometric→recovery→profile) → auth`; plus `lock` and `recovery` (restore) entry states. A cold start with a stored key always routes to **lock**.

**Authenticated shell**: a **floating glass dock** (pill, blur) with 4 tabs — **Today, Month/Insights, People, Settings** — and a **center lime FAB** that opens a chooser sheet (**Add manually** → keypad Entry, **Ask the assistant** → Assistant). Detail routes (entry, txn, review, person, agent, settings/*) push over the tab and hide the dock.

---

## Screens

### Onboarding
- **Splash** — centered হিসাব wordmark in lime + three pulsing dots; auto-advances.
- **Welcome** — wordmark eyebrow, display headline "Your money, only yours.", muted subtitle, lime privacy chip ("End-to-end private, on-device"); bottom: phone field (`+880` mono prefix) + **Continue** (disabled <9 digits). Error variant shows a `neg` line.
- **OTP** — eyebrow "Verify", headline, "Sent to +880…" mono line, 6 segmented digit cells (active cell lime ring), on-screen numeric keypad, **Verify** (enabled at 6) + "Resend code". Error variant: cells `neg`, "Invalid code".
- **Biometric** — glowing fingerprint tile, eyebrow "Secure", headline "Unlock with your face or finger", **Enable biometric** + "Skip (not recommended)". Unavailable variant disables the button ("Not available on this device").
- **Recovery phrase** — eyebrow, headline "Write these 24 words down", `neg` warning, 2‑col numbered word chips, sticky acknowledgement checkbox row + **I've saved them** (gated on checkbox).
- **Profile** — eyebrow "Almost done", headline "What should we call you?", name field, English/বাংলা toggle (selected = lime fill), **Start Hisaab →** (gated on name).
- **Capture opt-in** — standalone card (also embedded on Today): sms glyph, title "Log transactions automatically", privacy copy, **Turn on** + **Maybe later**.

### Lock & recovery
- **Lock** — lock tile, lime wordmark, "Hisaab is locked", **Unlock with biometric** (finger icon). "No fingerprints enrolled" variant adds a `neg` eyebrow.
- **Restore from phrase** — eyebrow "Recover", headline, 2‑col 24 numbered word inputs, sticky **Restore** (gated until all 24 filled, shows count).

### Today (home)
- Header: "Good evening" + name; right: search icon button + gradient avatar (initials).
- **Hero balance card**: eyebrow "TOTAL BALANCE", huge mono balance, `pos` delta pill ("+৳62,250 this month"), and an **in/out bar** (green segment = in %, red = out) with In/Out labels.
- **Account strip**: horizontal scroll of mini cards (name, KIND, mono balance; negative balances in `neg`), each with a hued icon tile.
- **Review banner**: lime-tinted, lime sparkle tile, "1 transaction to review / Auto-captured from SMS", chevron → Review inbox.
- **Capture opt-in card** (dismissible) — see above.
- **Feed**: "Today" + date; rows = glyph chip, merchant (+ lime sparkle if auto-captured), "account · category" sub, signed mono amount (income in `pos`). Row → Transaction detail.

### Month / Insights
- Month switcher (‹ "May 2026" ›).
- **Net card**: eyebrow "Net this month", hero mono net, `pos` delta pill, **per-day bar chart** (tallest bar solid lime, others lime @28%) with ৳0 / ৳peak axis labels.
- **Spending by category**: rows with name, mono amount, % and a hued progress bar.
- **Budgets**: row of **circular rings** (SVG stroke-dashoffset), color escalates accent→amber(≥80%)→`neg`(≥100%), center % + spent/cap below.
- **Recurring**: merchant, "n× · avg ৳…", last-seen date.

### Entry (keypad) — the signature interaction
- Top: close (×) / "New entry".
- Kind chips (scroll): Expense / Income / Transfer / Lend / Borrow (selected = white fill on dark).
- **Big live amount**: `৳` + Space Grotesk 64px, grouped thousands; color follows kind (Income `pos`, Expense `text`, transfer/lend `lime`).
- **Category quick-row**: glyph tiles, selected = hue border, full opacity.
- **Detail chips** (scroll, tap to open a sheet): Account, **To** (Transfer only), **Person** (Lend/Borrow only), Date, Merchant, Note, Tags, Receipt (toggles), Split. Chips turn lime-tinted when set.
- Sheets: account picker (check on selected); merchant/note/date inputs; tags (chip input); person (name + "contacts coming soon"); **Split editor** (numbered amount rows summing to parent — header turns `pos` when matched, +Add part, Remove/Save).
- **Numeric keypad** (3×4, ⌫) on a raised `bg-2` panel + full-width **Save entry** (disabled at 0).

### Transaction detail
- Top: back / "Transaction" / **Delete** (`neg`).
- Glyph + signed hero mono amount (income `pos`), kind label.
- Detail card: Merchant, Category, Account, When (mono), Notes.
- If auto-captured: **CAPTURED** provenance card (sparkle eyebrow, Parsed by / Model / Confidence / From, expandable "Show original SMS").
- Delete confirmation dialog.

### Assistant
- Header: back button, glowing lime sparkle orb, "Assistant / On-device · private", **New** (clears thread).
- Thread: user bubbles = lime (dark text), AI bubbles = surface; typing = three pulsing lime dots.
- **Empty** state: big sparkle, "Ask about your money", example copy, suggestion chips.
- Suggestion chips row (hidden when unavailable).
- **Input bar**: text field + circular **send** (↑, lime when text) + circular **mic** (voice). Mic tap → **listening** state: mic turns lime + glow, an animated "Listening…" equalizer appears, field border lime; auto-fills a sample after ~1.8s (replace with real STT).
- **Unavailable** variant: gate card "Add a cloud model + API key…"; input + mic disabled.

### Review inbox
- Top: "Review" / Close.
- **Confirm all high-confidence** button (shown when any candidate ≥85%).
- Candidate cards: signed hero amount (credit `pos`), "merchant · category", "sender · account" + confidence pill (`pos` ≥85%, else amber), expandable "Show original SMS", and **Confirm** / **Edit** / **Dismiss** (`neg` text).
- Empty state: check tile + "Nothing to review".

### People
- **List**: header "People" + "+ Add"; rows = gradient avatar (initials), name, "Owes you / You owe / Settled", signed mono balance (`pos`/`neg`/muted). Add-person sheet (name + "Or pick from contacts").
- **Detail**: avatar + balance card ("They owe you / You owe them / Settled" + hero amount); History rows (lend/borrow icon tile, "Lent/Borrowed ৳… · note · status", **Settle** pill). Settle sheet: amount field + account radios.

### Settings suite
- **Settings**: title; grouped cards — Privacy (Lock timeout → sheet, Biometric toggle, Recovery phrase → Reveal), Data (Accounts/Categories/Budgets), Capture (Auto-capture), About (Version), and **Sign out** (`neg`, confirm dialog). Lock-timeout sheet: Immediate/30s/5min/Never with check.
- **Accounts**: cards with name, KIND·currency, mono balance; CARD cards add Outstanding(`neg`)/Available(`pos`)/Due chips. Add-account sheet (name, kind chips, CARD reveals credit-limit/statement/due day).
- **Categories**: glyph + name + "Default" badge; add-category sheet (name + icon picker).
- **Budgets**: name + "৳cap/mo · since YYYY-MM" + Archive; add-budget sheet (category radio list excluding salary/transfer + monthly cap).
- **Auto-capture**: master SMS toggle; **permission-denied** warning card (Grant); Engine cards (On-device ✓ / Cloud); when Cloud → provider radios (Claude/OpenAI/Gemini), API-key field + **Validate** (checking/valid/invalid status), Model row, Cloud-consent row; Privacy (Redact PII toggle + copy); Trust (Always-review toggle; when off, auto-post confidence slider 50–99%); Senders (new-senders prompt, per-sender map toggles, **+ Add a sender** inline form: Sender ID + Display name); **Import last 90 days**. **iOS** variant shows an "SMS capture isn't available on iOS" explainer only.
- **Cloud consent**: "Your bank SMS text will be sent to Claude…" copy; **I agree** ↔ granted state with **Revoke**.
- **Recovery reveal**: `neg` warning + 2‑col 24-word grid (biometric-gated; "No fingerprints enrolled" error variant).

---

## Interactions & state
- **Navigation**: state machine (phase/tab/stack). Onboarding & detail routes push/pop; tabs reset the stack. Position persists to `localStorage` in the prototype — in the app, mirror the original `AppViewModel`/`MainGraph` model.
- **Auto-lock**: 30s background → lock (original behavior; not simulated here).
- **Validation**: phone ≥9 digits; OTP exactly 6; name non-blank; budget needs category + cap>0; split must sum to parent and all parts >0; API key validate is async (checking→valid/invalid).
- **Listening (voice)**: toggle on mic; show equalizer + lime emphasis; integrate platform STT (`SpeechToText`) in place of the demo timeout.
- **Confidence colors**: <80% accent, 80–99% amber, ≥100% `neg` (budgets); ≥85% high-confidence (capture).

## Assets
No raster assets. Icons are a small inline stroke set (24×24, 1.7–2.0 stroke) — map to your icon library (Material Symbols / SF Symbols / lucide). Avatars are initials on a violet→blue gradient. Wordmark is the Bengali text হিসাব set in the display face.

## Files in this bundle
- `Hisaab Neo.html` — entry point (loads fonts, theme, scripts).
- `src/neo-theme.css` — all design tokens (the source of truth for color/type/shape).
- `src/neo.jsx` — primitives (Card, NBtn, Money, NGlyph, dock, phone frame) + Today, Month, Entry (keypad + split), Assistant.
- `src/neo-ui.jsx` — form primitives (field, row, toggle, sheet, dialog, segmented, check/radio, top bar).
- `src/neo-onboarding.jsx` — splash, welcome, otp, biometric, recovery, profile, capture opt-in, lock, restore.
- `src/neo-detail.jsx` — transaction detail, review inbox, review card, agent consent, people, person detail.
- `src/neo-settings.jsx` — settings hub + accounts/categories/budgets/auto-capture/cloud-consent/recovery-reveal.
- `src/neo-app.jsx` — router, screen registry, catalog sidebar, persistence.
- `src/data.js` — mock data + `taka()` money formatter (thousands-grouped, signed).
- `frames/android-frame.jsx` — device status bar / nav pill only.

> Open `Hisaab Neo.html` to interact with every screen; use the left sidebar to jump to any state.

## Generating reference screenshots
This bundle ships the interactive HTML rather than static images (it's the higher-fidelity reference). To capture stills for tickets/Figma:
1. Open `Hisaab Neo.html` in a browser.
2. Use the left sidebar to navigate to any screen/state (it lists all ~35 entries grouped by area).
3. Screenshot the phone frame (macOS ⌘⇧4, Windows Win+Shift+S), or use browser DevTools device capture.

The sidebar is a dev-only catalog — it is **not** part of the product UI; ignore it when implementing.

## Build order
See `BUILD_ORDER.md` for a phased, checkbox implementation sequence (foundations → shell → onboarding → home → entry → assistant → people → settings → polish), with a per-screen acceptance checklist.
