# Spec: Hisaab "Midnight" Redesign — Compose Port

**Date:** 2026-05-31
**Status:** Approved (2026-05-31) — light palette to be derived + AA-verified during implementation
**Source of truth:** `docs/design/design_handoff_hisaab_midnight/` (Claude Design handoff — `README.md`, `BUILD_ORDER.md`, `src/neo-theme.css`, `src/*.jsx`, `CHAT_TRANSCRIPT.md`)
**Live prototype:** `Hisaab Neo.html` (served locally; open the sidebar to walk every screen + state)

---

## 1. Goal & decision trail

Replace Hisaab's current **Editorial Premium** (warm cream / amber / serif) UI with **"Midnight"** — a dark neo-bank visual language: deep ink canvas, one electric-lime accent, oversized Space Grotesk numerals, glass cards, a tactile numeric keypad entry, and a floating glass dock.

This direction was chosen and approved by the user in a Claude Design session (see `CHAT_TRANSCRIPT.md`):
- The first attempt (a beautification of the warm editorial system) was **rejected** — the user wanted "something completely newly designed with better UI/UX."
- "Midnight" was proposed on 4 hero screens and **approved**: *"This one is looking really good, can I get all pages and full complete version?"*
- All ~30 screens were then built in Midnight, with a full fidelity pass restoring every functional element against `PROJECT-CONTEXT.md`.

**This spec is the plan to port that approved prototype into the real Compose Multiplatform app.**

## 2. Scope

**In scope** — a pure **presentation re-skin** of all surfaces, in both a dark (primary) and a derived light theme:

- Theme foundations: color tokens, typography (real bundled fonts), shapes, spacing, elevation/glow.
- Shared component primitives (glass card, money text, glyph chip, sheets, dock + FAB, keypad, etc.).
- Every screen, area-by-area: onboarding + lock, Today, Month/Insights, keypad Entry + detail, Assistant + review, People, the Settings suite.
- Both themes verified per screen via the committed screenshot harness.

**Explicitly out of scope** (do **not** touch):

- Data layer / repositories / SQLDelight schema, crypto/E2EE, SMS capture pipeline, agent/LLM runtime, auth. All ViewModels and their state contracts stay as-is; only the composables that render them change.
- New product features. Fidelity target is the existing element set (per `PROJECT-CONTEXT.md`), restyled — not new functionality.
- iOS/wasm-specific behavior changes (the re-skin is `commonMain`; platform actuals are untouched beyond what theming requires).

**Non-goal risk to watch:** "while I'm in here" feature creep. If the prototype implies a capability the app doesn't have, it's restyled-not-added; note it as follow-up, don't build it.

## 3. Design tokens

Ported verbatim from `src/neo-theme.css`. The current `HisaabColors.Palette` (9 roles) is **extended** to carry the full Midnight set; both a `Midnight` (dark) and a derived `MidnightLight` instance are provided.

### 3.1 Dark palette (primary — exact from prototype)

| Role (new field) | Hex / value | css token |
|---|---|---|
| `background` | `#0A0B0E` | `--bg` |
| `backgroundInset` | `#101218` | `--bg-2` |
| `surface` | `#161922` | `--surface` (glass card base) |
| `surfaceRaised` | `#1D212B` | `--surface-2` |
| `hair` | `#FFFFFF` @ 7% | `--hair` |
| `hair2` | `#FFFFFF` @ 4% | `--hair-2` |
| `glass` | `#FFFFFF` @ 4.5% | `--glass` |
| `onBackground` (text) | `#F3F5F8` | `--text` |
| `muted` | `#98A0AD` | `--muted` |
| `faint` | `#5C6470` | `--faint` |
| `accent` (lime) | `#CBF24A` | `--lime` |
| `accentDim` | `#A9CE37` | `--lime-dim` |
| `onAccent` | `#0A0B0E` | `--on-lime` |
| `accentSoft` | `#CBF24A` @ 12% | `--lime-soft` |
| `positive` | `#46E08A` | `--pos` |
| `negative` | `#FF6B5C` | `--neg` |
| `positiveSoft` | `#46E08A` @ 12% | `--pos-soft` |
| `negativeSoft` | `#FF6B5C` @ 12% | `--neg-soft` |

**Category hues** (8, theme-independent vivid set; used by glyph chips + charts), kept as a companion `HisaabColors.categoryHues` map, not palette fields: violet `#8B7CFF`, blue `#4EA8FF`, teal `#2DD4BF`, amber `#FFB13C`, pink `#FF6FB5`, lime `#CBF24A`, rose `#FF6B5C`, slate `#8A93A3`. Glyph chips render the hue over a 16%-alpha fill of the same hue.

### 3.2 Light variant (derived — to be designed in the Midnight language)

The prototype is dark-only; the light variant is derived to the same structure and **must be contrast-verified** (see §9). Direction: near-white cool canvas, ink text, lime retained as the brand accent but used as **fill-with-dark-text** (not as text/icon color on white, which fails contrast). Draft targets (refined during implementation against WCAG AA):

| Role | Draft light value |
|---|---|
| `background` | `#F6F8FB` |
| `backgroundInset` | `#EDF0F4` |
| `surface` | `#FFFFFF` |
| `surfaceRaised` | `#FFFFFF` (elevation via shadow, not fill) |
| `hair` | `#0A0B0E` @ 8% |
| `onBackground` | `#13161B` |
| `muted` | `#5A6573` |
| `faint` | `#8A93A3` |
| `accent` | `#5E7E12` (lime-dim'd for AA text/icon use) |
| `accentFill` | `#CBF24A` (chips/CTAs, with dark `onAccent` text) |
| `positive` / `negative` | `#1F9D57` / `#D14535` (AA on white) |

> Light-variant hexes are **provisional**; the implementation plan includes a contrast pass that may adjust them. The dark palette is fixed (matches the approved prototype).

### 3.3 Shape, spacing, elevation

- Radii: card `22dp` (from `10dp`), field `16dp`, sheet top `26dp` (from `18dp`), pill `999dp`.
- Gutter (screen horizontal padding): `20dp` (from `22dp`). Keep the 4dp spacing scale.
- `shadowCard`: inset 1px white@4% + `0 20px 50px -24px black@80%`.
- `glowLime`: 1px lime@50% ring + `0 8px 30px -6px lime@45%` — FAB, focus rings, assistant orb.
- Dock height ~`76dp`; tab screens reserve ~`104dp` bottom padding behind the floating dock.

## 4. Typography

Current `HisaabTypography` uses **system fallbacks** (real fonts were deferred and never bundled). Midnight bundles them for the first time, as Compose `FontFamily` from `composeResources/font/` (works across Android/iOS/wasm):

- **Display / numerals** — Space Grotesk (400/500/600/700), tracking −0.02em.
- **UI / body** — Hanken Grotesk (400/500/600/700).
- **Mono / money** — Space Mono (400/700), `tnum` tabular figures.
- **Bengali** — Hind Siliguri (display/UI fallback) + Noto Sans Bengali (mono fallback) for the হিসাব wordmark and ৳ (U+09F3).

Type scale rebuilt to Midnight: hero amount Space Grotesk ~50sp/600 (−0.03em), title 30sp/600, eyebrow 11sp/600 +0.14em uppercase `faint`, body 15–16sp, money mono 16sp/700 tabular, signed with `−`/`+` prefix.

> **Perf note / rule exception:** 4 families exceeds the global "max 2 fonts" rule. Justified as brand-defining (it is the redesign). Mitigation: bundle only the weights actually used, prefer variable/subset where available, and lazy-load non-critical weights. Bundle-size impact is tracked as a checklist item.

## 5. Theming architecture

- Extend `HisaabColors.Palette` with the new fields (§3.1). All existing screens keep compiling (they reference existing field names; new fields are additive).
- Replace the `Light`/`Dark` instances with `MidnightLight`/`Midnight` (keep the old Editorial palettes in the file, commented/parked, for reference — do not delete).
- `HisaabTheme(darkTheme)` keeps its signature; it now selects Midnight palettes and maps the expanded set into the Material3 `ColorScheme` (lime→primary, onAccent→onPrimary, surfaceRaised→surfaceVariant, hair→outline, etc.).
- `LocalHisaabPalette` stays the access path; screens read the new roles from it. Add a `LocalHisaabElevation`/glow helper if needed for the glass/glow treatment.
- Theme selection: dark is the app default; honor system + an in-app Settings toggle (Settings already has the surface for it).

## 6. Component primitives (new shared composables)

Built once, reused everywhere (mirror `src/neo.jsx` + `src/neo-ui.jsx`), under `commonMain/.../design/components/`:

- `GlassCard` (surface fill, hair border, card shadow), `SurfaceCard` variants.
- `MoneyText` (mono tabular, signed, grouped thousands, ৳ mark, color-by-sign).
- `GlyphChip` (category icon at hue over 16% fill, radius 14).
- Buttons: `PrimaryButton` (lime), `GlassButton`, `DarkButton`.
- Form: `Field`, `TextArea`, `Toggle`, `Check`, `Radio`, `Segmented`, `Row`, `Sheet` (modal bottom), `Dialog`, `TopBar`, `Section`/eyebrow.
- **`FloatingDock`** (4 tabs: Today/Month/People/Settings) + **center lime FAB** → chooser sheet (Add manually → Entry, Ask assistant → Assistant).
- **`Keypad`** (3×4 + ⌫) for the signature Entry interaction.
- Motion: fade-in (0.28s), sheet up (0.28s), tap `scale(0.97)`, pulse (typing/listening), glow (assistant/mic) — via Compose animation, honoring reduced-motion.

Icons: map the prototype's 24×24 stroke set to the existing icon library (Material Symbols).

## 7. Navigation & interaction changes

The app uses state-driven nav (`AppViewModel` `AppState` + `MainGraph`, no nav library). Midnight keeps that model but changes the **shell**:

- Authenticated shell gets the **floating glass dock + center FAB chooser** (replaces the current flat tab treatment). Detail routes (entry, txn, review, person, agent, settings/*) push over the tab and hide the dock — same push/pop semantics as today, restyled.
- **Entry** becomes the tactile keypad with tap-to-open detail chips (Account, To, Person, Date, Merchant, Note, Tags, Receipt, Split) — restyle of the existing Entry VM, not a data change.
- Cold-start-with-key → Lock; 30s background → Lock — unchanged behavior.

## 8. Screen porting plan (area-by-area, per `BUILD_ORDER.md`)

Each area is restyled against the per-screen specs in `docs/design/design_handoff_hisaab_midnight/README.md`, wired to the existing ViewModels, then screenshot-verified in both themes before moving on.

1. **Foundations** — tokens, fonts, shapes/spacing, elevation, shared primitives.
2. **Shell & nav** — scaffold, floating dock, FAB chooser, state wiring.
3. **Onboarding & lock** — splash, welcome (+loading/error), OTP (+error), biometric (+unavailable), recovery phrase, profile, capture opt-in, lock (+no-biometric), restore.
4. **Home** — Today (hero balance, in/out bar, account strip, review banner, capture card, feed) + Month/Insights (net card, per-day bar chart, category bars, budget rings, recurring).
5. **Entry & detail** — keypad Entry + all detail-chip sheets + Split editor; Transaction detail with capture provenance.
6. **Assistant & review** — assistant chat (+empty, +unavailable gate, **mic/voice**), review card, Review inbox.
7. **People** — list + person detail + settle.
8. **Settings suite** — hub, accounts, categories, budgets, auto-capture (engine/privacy/trust/senders), cloud consent, recovery reveal.
9. **Polish** — motion, empty/error states, both-theme contrast pass, screenshot regen.

## 9. Testing & verification

- **Screenshot harness** (`ScreenshotTourTest` + `ScreenshotVmTest`, just committed): regenerate every screen after each area, in **both themes**, and visually diff against the prototype. This is the acceptance gate per area.
- **Existing test suite stays green** — since logic/VMs are untouched, the 323 unit + 46 instrumented tests must keep passing (re-run after foundations and at the end). Any breakage means the re-skin leaked into logic — fix that.
- **Accessibility:** verify WCAG AA contrast for both themes (lime-on-dark passes; lime-as-text-on-light does not — that's why the light variant dims the accent for text). Test TalkBack labels on new components; honor reduced-motion.
- **Per-platform smoke:** Android emulator (primary) + iOS simulator render check after foundations and at the end.

## 10. Risks & open considerations

- **Light variant is genuinely new design work** (prototype is dark-only). Token hexes in §3.2 are provisional and get a contrast pass. This is the largest source of uncertainty.
- **Font bundle weight** — 4 families; mitigate by shipping only used weights/subsets. Track against the app asset budget.
- **Scope size** — ~30 screens. Mitigated by area-by-area phasing with a screenshot gate, so we can pause/review between areas.
- **Surface drift** — the app may have screens/states the prototype simplified (or vice-versa). Source of truth for *elements* is `PROJECT-CONTEXT.md`; source of truth for *appearance* is the prototype. Where they disagree, keep the app's elements, restyle to the prototype.
- **wasm** stays gated (H3, unrelated to this work).

## 11. Acceptance criteria

- All ~30 screens render in Midnight in both dark and light, matching the prototype's layout/spec, with no console/log errors.
- All functional elements from `PROJECT-CONTEXT.md` present (no fidelity gaps — e.g. the Assistant mic).
- Existing unit + instrumented suites still green; screenshots regenerated and committed.
- Both themes pass WCAG AA contrast on text and interactive elements.
- No changes outside the presentation layer (diff touches `design/`, `screens/`, theme, resources — not data/crypto/capture/agent/auth).
