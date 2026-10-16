# Hisaab — Product Idea & Business Plan

**Status:** Draft v0.1
**Date:** 2026-05-27
**Author:** Zakaria Hossain
**Companion technical spec:** [`docs/superpowers/specs/2026-05-27-hisaab-master-architecture-design.md`](./superpowers/specs/2026-05-27-hisaab-master-architecture-design.md)

---

## 1. Vision

> *Your phone already knows what you spend.
> Hisaab reads it, files it, and tells you what it means — without sending a single message to the cloud.*

Hisaab is a privacy-first AI financial guide for Bangladesh and the South Asian diaspora. Every bank SMS, every mobile-money receipt, every email statement, every spoken word becomes a clean ledger entry — captured automatically, parsed on-device, organized into a single timeline, and read by a careful AI advisor that genuinely knows the user. The backend can never see the data.

## 2. The problem

Personal money in Bangladesh is fragmented across:

- **Mobile financial services** — bKash, Nagad, Rocket
- **4-6 bank accounts**, savings, FDR
- **2-3 credit / debit cards**
- **Cash transactions** that never touch any system
- **Informal lending and borrowing** — the "khata" everyone still keeps with family, colleagues, neighbours
- **Buy-now-pay-later, micro-loans, salary advances**
- **Foreign currency**, for the diaspora subset

Today's options are unsatisfying:

| Player class | What they do | What they miss |
|---|---|---|
| Local SMS parsers (Moneyview, Walnut) | Auto-parse bank SMS in India | No coaching, no lend/borrow, India-only, no privacy story |
| Premium dashboards (Copilot Money, Monarch) | Beautiful UI + AI insights | US-centric, no SMS, no BD ecosystem |
| Conversational coaches (Cleo, Origin) | Chat-first AI guidance | No real auto-capture, generic advice, foreign cloud |
| Lend/borrow trackers (Splitwise, Tricount) | Group expenses | Single-purpose, not a ledger |
| Generic budget apps | Manual entry | Friction kills adoption |

**Nothing today combines** auto-capture from SMS + email + voice, informal lend/borrow as a first-class concept, proactive on-device coaching, and a privacy posture where data never leaves the device.

## 3. Target user

### Primary persona — "Dhaka professional"

- Age 24–40, urban (Dhaka, Chittagong, Sylhet)
- Earns ৳40k–৳200k/month, mixed sources: salary + side income / freelance / family contributions
- 3-5 bank or MFS accounts; routine bKash/Nagad user
- ৳2L–৳10L in liquid savings; growing
- Lends and borrows informally — typical month touches 5-15 people for amounts ৳200–৳20,000
- Already comfortable with English-language fintech UIs (Pathao, Foodpanda, Daraz, Robi)
- Owns a recent mid-to-flagship Android (Samsung A/M series, Xiaomi, OnePlus, Pixel)

### Secondary persona — "Diaspora sender"

- Bangladeshi in US/UK/UAE/Saudi/Malaysia
- Manages dual-currency life: local earnings + remittances home
- Wants one place for both sides of their financial picture

### Tertiary persona — "South Asia urban professional" (Phase 2 expansion)

- Indian, Pakistani, Sri Lankan equivalents of the primary persona
- Reachable once BD product is proven

## 4. Value proposition — five pillars

1. **Quiet capture** — SMS (Android), Gmail, voice, manual, OCR; everything parsed on-device by a small LLM
2. **One ledger** — every spend, income, lend, borrow in one timeline; bKash, Nagad, Rocket, banks, cash, cards unified
3. **An advisor who knows you** — proactive on-device coaching; warns before overspend, suggests savings moves, drafts WhatsApp reminders for lent money
4. **Editorial premium** — magazine-quality design language (typography by GT Sectra, calm motion); reads like a serious financial newspaper
5. **Privacy as the headline** — "we cannot read your data" is a screen on first run, not a footnote

## 5. Competitive positioning

| Competitor | Their strength | Hisaab's edge |
|---|---|---|
| Moneyview, Walnut | SMS auto-parse at scale (India) | Privacy posture, agentic coaching, lend/borrow, BD-native ecosystem |
| Copilot Money, Monarch | Premium UI, dashboards, AI overlays | Regional integration (SMS/email > Plaid), lower price, on-device privacy |
| Cleo, Origin, Era | Conversational AI coaching | Real auto-capture, regional banks/MFS, on-device privacy |
| Splitwise | Group lend/borrow | Full ledger, not a single-purpose tool |
| CRED (India) | Premium card UX, brand | Full ledger, not credit-card-only, BD ecosystem, accessible price |
| Bangladeshi alternatives (Khorch, Hishab Manager, etc.) | Local familiarity | Premium UI, agentic AI, design quality, English-Bangla parity |

## 6. Monetization

Three tiers, BD-friendly pricing:

| Tier | Price | Includes |
|---|---|---|
| **Hisaab Free** | ৳0 | Manual entry, voice input, monthly review, basic lend/borrow, 1 device |
| **Hisaab Plus** | ৳249/mo · ৳2,400/yr (~$2.5/mo) | SMS + Gmail auto-parse, advisor agent, unlimited devices, exports, custom categories |
| **Hisaab Family** | ৳499/mo · ৳4,800/yr (~$5/mo) | Up to 4 members, shared lend/borrow, household budget, child sub-accounts |

Payment rails:

- **bKash Payment Gateway** + **SSLCommerz** for BD users (mobile money and card)
- **Apple In-App Purchase** + **Google Play Billing** where store policies require (covers store cut)
- **Stripe** for diaspora web subscribers

Pricing rationale: BD willingness-to-pay for SaaS is ~৳200–৳500/month for premium utility apps. Anchor lower than Bangla streaming (~৳399 for Toffee Premium) to drive adoption; price family plan close to a single Netflix subscription.

## 7. Brand identity

| Element | Choice |
|---|---|
| Name | **Hisaab** (Bengali: account, calculation) |
| Wordmark typography | GT Sectra display, cream/terracotta palette |
| Voice | Calm, considered, literate — reads like FT Weekend, not like Cleo |
| Tagline candidates | "Your money, told well." / "The ledger that reads itself." |
| Brand colour | Terracotta `#ad6b2a` against cream `#faf7f2` |
| Tone in advisor copy | Brief, observational, never preachy, occasionally dry humour |

Full design system in the technical spec.

## 8. Phased roadmap (12 months)

| Phase | Window | Ship |
|---|---|---|
| **P0 — Foundation** | M1–2 | KMP + Compose Multiplatform scaffold, SQLDelight schema, E2EE storage, Supabase auth |
| **P1 — Ledger core** | M2–4 | Manual entry, lend/borrow, monthly stats, categories |
| **P2 — Capture engine** | M3–5 | SMS parser (Android), Gmail parser, on-device LLM categorizer |
| **P3 — Agent runtime + voice** | M4–6 | KMP agent wrapper, conversational input, voice STT |
| **P4 — Advisor + notifications** | M5–7 | Proactive coaching, comparative analytics, push triggers |
| **P5 — iOS + Web parity, polish** | M6–9 | iOS via Compose Multiplatform + SwiftUI bridges; Web (viewer); onboarding; localization (bn + en); billing |
| **P6 — Closed beta** | M8–9 | 200 Dhaka beta users; instrumentation; bug bash |
| **P7 — Public launch** | M10–11 | Play Store + App Store; landing site; press; influencer beta |
| **P8 — Open banking pilot** | M11–12 | bKash B2B partnership; sandbox; Phase 2 prep (investments, FDR) |

Single-developer execution: stretch by 30%. Two developers: hit roughly on plan.

## 9. Success metrics (12 months post-launch)

| Metric | Target |
|---|---|
| Paying users in Bangladesh | 10,000 |
| Free → Plus conversion | 8% within 30 days of install |
| Month 3 retention (Plus) | 80% |
| Average savings identified by advisor per active user | ৳3,000/mo |
| Play Store + App Store rating | > 4.7 |
| Support tickets per active user per month | < 1% |
| NPS | > 50 |

## 10. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Google Play SMS permission rejected | Blocks Android v1 capture story | Strong privacy story in submission; manual paste / forwarding fallback flow |
| On-device LLM too slow on mid-tier Androids | Poor UX | Tiered model strategy; defer parsing to charging + idle windows; show explicit progress |
| Bangladesh open banking framework slow to mature | Delays bank-direct sync | bKash partnership; SMS coverage substitutes; not on critical path for v1 |
| BD users don't value the privacy story | Differentiation weaker than expected | A/B test landing pages; lean on diaspora users for whom privacy is a stronger draw; keep speed and capture quality as primary draws |
| Existing competitor (Moneyview) enters BD | Lost first-mover advantage | Ship fast; privacy + lend/borrow + advisor are real moats; BD-native UX |
| Single-founder execution risk | Project never ships | Phased shipping; KMP code share; clear MVP within "whole product" |
| Apple App Store rejects under "financial advice" rules | iOS launch blocked | Position as personal recordkeeping, not advice; disclaimers; advisor as observational, not prescriptive |
| Advisor LLM hallucination giving bad financial guidance | Trust + legal exposure | Constrain advisor output to deterministic statistics; LLM phrases only what is statistically true; explicit disclaimers |
| Battery drain from background advisor jobs | App killed by user | Charging + idle scheduling by default; user-configurable cadence |
| Font licensing (GT Sectra commercial) | Legal / cost | Purchase or substitute with Tiempos / Recoleta — both equally good for the editorial direction |

## 11. Why now

- Compose Multiplatform is stable on Android and iOS, with Web (WASM) in Beta — a single Kotlin codebase across all three platforms is finally pragmatic in 2026, with web as the riskiest target
- Gemini Nano (Android) and Apple Foundation Models (iOS) make on-device agentic AI viable for the first time
- Privacy regulation pressure (EU, US state laws) is making the "cloud reads everything" model unfashionable; users globally expect more
- Bangladesh's smartphone penetration crossed 70% in 2025; MFS user base crossed 100M accounts; SMS-based transaction notifications standardized
- Moneyview's IPO in March 2026 demonstrated the addressable market; nobody has yet brought their playbook to Bangladesh with a privacy + agentic twist

## 12. Out of scope (v1)

These are intentionally excluded from v1 — they may become Phase 2/3 work:

- Investment tracking, stocks, FDR portfolios
- Tax preparation
- Multi-user shared books / small-business accounting (Hisaab is for individuals and households, not businesses)
- Cryptocurrency tracking
- Direct bank API integration via Plaid-equivalent (waiting on BD Open Banking maturity)
- Group expense splitting beyond family plan basic sharing

## 13. Open product questions

1. Family plan implementation — separate logical books with shared lend/borrow view, or fully shared books?
2. Diaspora pricing — keep BD pricing or local-currency pricing for US/UK?
3. Should the advisor occasionally surface explicit *opportunities* (e.g., "you could move ৳50,000 to a higher-yield savings") or strictly stay observational?
4. Bengali-numeral default — should we detect locale once and stick, or expose a setting prominently?
5. Lent-money reminder tone — should the advisor draft suggested WhatsApp messages for the user, or strictly notify the user?

These are answerable in product testing; flagged so we don't lose them.
