# Build order & checklist — Hisaab "Midnight"

A suggested implementation sequence for Claude Code. Each item is a discrete, reviewable PR-sized chunk. Check them off in order — earlier phases unblock later ones. Targets Compose Multiplatform (`HisaabTheme` / `LocalHisaabPalette` / `MainGraph`); adapt names to your stack.

## Phase 0 — Foundations (do first)
- [ ] **Tokens**: port `src/neo-theme.css` into the palette. Update `LocalHisaabPalette` (dark is the primary theme) with: bg `#0A0B0E`, bg-2 `#101218`, surface `#161922`, surface-2 `#1D212B`, hair `rgba(255,255,255,.07)`, text `#F3F5F8`, muted `#98A0AD`, faint `#5C6470`, accent/lime `#CBF24A`, on-lime `#0A0B0E`, pos `#46E08A`, neg `#FF6B5C`, + category hues.
- [ ] **Type**: register `Space Grotesk` (display/numbers), `Hanken Grotesk` (UI), `Space Mono` (tabular money), Bengali fallback. Define text styles: hero amount (Space Grotesk 50/600, −0.03em), title (30/600), eyebrow (11/600, +0.14em, uppercase, faint), body (15–16), money (mono 16/700 tabular).
- [ ] **Shape/elevation**: radii field 16 / card 22 / sheet 26 / pill 999; card border + shadow; lime glow; gutter 20.
- [ ] **Money formatter**: `taka(n, {sign})` — grouped thousands, `−`/`+` prefix, ৳ mark (see `src/data.js`).
- [ ] **Shared components**: Card, primary/glass/dark Button, Money text, category Glyph chip, Field, TextArea, Toggle, Check, Radio, Row, Sheet (modal bottom), Dialog, Segmented, TopBar, Section/eyebrow. (Mirror `src/neo.jsx` + `src/neo-ui.jsx`.)

## Phase 1 — Shell & navigation
- [ ] **Device-agnostic scaffold** + **floating dock** (Today / Month / People / Settings) with center lime FAB → chooser (Add manually / Ask assistant).
- [ ] Wire the **state machine**: splash → onboarding → lock/recovery → auth; push/pop detail routes hide the dock; cold-start-with-key → lock; 30s background → lock.

## Phase 2 — Onboarding & lock
- [ ] Splash, Welcome (+ loading/error), OTP (+ error), Biometric (+ unavailable), Recovery phrase, Profile, Capture opt-in.
- [ ] Lock (+ no-biometric), Restore-from-phrase.

## Phase 3 — Home
- [ ] **Today**: hero balance card + in/out bar, account strip, review banner, capture opt-in card, transaction feed (auto tag). Wire to repositories.
- [ ] **Month/Insights**: net card + per-day bar chart, category bars, **budget rings**, recurring list. Empty states per section.

## Phase 4 — Entry & detail (the signature interaction)
- [ ] **Keypad Entry**: kind chips, live big amount (color by kind), category quick-row, numeric keypad, Save.
- [ ] **Detail chips → sheets**: Account, To (Transfer), Person (Lend/Borrow), Date, Merchant, Note, Tags, Receipt, **Split editor** (sum-to-parent validation).
- [ ] **Transaction detail**: signed hero amount, detail rows, CAPTURED provenance + Show-original-SMS, delete dialog.

## Phase 5 — Assistant & review
- [ ] **Assistant**: thread + typing dots, empty state, suggestion chips, input bar with **send + mic (voice/listening state)**, unavailable gate. Wire STT + agent runtime.
- [ ] **Agent consent** dialog; **Review card** (proposed writes, include checkboxes, Apply).
- [ ] **Review inbox**: confirm-all-high-confidence, candidate cards (confidence pill, Show SMS, Confirm / Edit / **Dismiss**), empty state. Edit deep-links to prefilled Entry.

## Phase 6 — People
- [ ] People list (avatars, signed balances, add-person sheet).
- [ ] Person detail (balance card, history, Settle sheet with account radios).

## Phase 7 — Settings
- [ ] Settings hub (Privacy / Data / Capture / About / Sign-out) + lock-timeout sheet + sign-out dialog.
- [ ] Accounts (+ CARD summary chips, add-account sheet with CARD fields).
- [ ] Categories (+ add sheet w/ icon picker).
- [ ] Budgets (+ add sheet, category radios).
- [ ] **Auto-capture**: master toggle, permission-denied warning, engine picker, Cloud (provider radios + API key + Validate + model + consent), redaction toggle, always-review + confidence slider, senders (map toggles + add-sender inline), import 90 days. iOS unavailable variant.
- [ ] Cloud consent (agree ↔ revoke), Recovery reveal (biometric-gated grid + error).

## Phase 8 — Polish
- [ ] Motion (fade/up/pulse/glow, tap scale), focus rings (lime), loading spinners in buttons.
- [ ] Accessibility: hit targets ≥44dp, contrast on dark, content descriptions for icon buttons.
- [ ] Empty/error/loading states for every data-backed screen.
- [ ] Light theme (optional) if you want parity with the prototype's token system.

## Acceptance check per screen
For each screen, confirm against `Hisaab Neo.html` (open it side-by-side):
1. Layout & spacing match (gutter 20, card radius 22).
2. Exact token colors (no off-palette values).
3. Typography roles correct (display vs sans vs mono).
4. All interactive elements present (cross-check the README's per-screen element lists).
5. States covered (empty / error / loading / disabled).
