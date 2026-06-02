# Testing Hisaab on iPhone 15 Pro — end-to-end runbook

**Goal:** get *old + new finance SMS* and *old (12mo) + new finance emails* flowing into Hisaab
on your iPhone 15 Pro.

## What's possible on iPhone (read this first)

iOS gives apps **no** access to SMS, in-app email (IMAP), or notifications. So the iPhone app
**captures nothing on its own** — the only automatic channel is:

> **your Mac (Hisaab companion) → encrypted Supabase relay → iPhone**

| Channel | On iPhone | Notes |
|---|---|---|
| **SMS** (old + new) | ✅ via Mac | Mac reads forwarded SMS from `chat.db`; **backfills the entire forwarded history**. |
| **Email** (old 12mo + new) | ✅ via Mac | Mac runs IMAP and pushes bank emails; first connect backfills **last 12 months**. |
| **Notifications** | ❌ not possible | iOS has no notification API; the Mac can't read iPhone notifications. **Android-only** — out of scope for this test. |

Email is configured **on the Mac**, *not* on the phone (the phone has no IMAP).

---

## Prerequisites (one-time)

- **Mac** with Xcode (already installed: Xcode 26.5) — required to build/sign the iPhone app.
- **iPhone 15 Pro** already paired in Xcode; bundle id `app.hisaab`.
- The Mac's **Messages app signed into the same Apple ID** as the iPhone (needed for Text Message
  Forwarding).
- A **personal Gmail / Outlook.com / Yahoo / iCloud** mailbox with an **app-specific password**
  (Workspace/M365 accounts need OAuth — not supported).
- ⚠️ **Free-signing expiry:** the iPhone signing profile is a free "Personal Team" cert that
  **expires 2026-06-08**. After that the app stops launching — just re-Run from Xcode to renew
  (7-day cycle). A paid Apple Developer account removes the limit but isn't required.

---

## Step 0 — Create the Supabase relay table (once)

In your Supabase project: **SQL Editor → paste the contents of**
`supabase/migrations/20260602120000_relay_captures.sql` **→ Run.** (Or `supabase db push`.)

This is the zero-knowledge relay: it stores only encrypted nonce+ciphertext, RLS owner-only. The
phone deletes each row after ingesting it.

---

## Step 1 — Put Hisaab on the iPhone

```bash
cd /Users/xack/Projects/finance-app/iosApp
# project + pods are already generated; only re-run these if the build complains:
#   xcodegen generate && pod install
open iosApp.xcworkspace
```

In Xcode:
1. Select your **iPhone 15 Pro** as the run destination.
2. **Signing & Capabilities** should show team **Zakaria Foysal (P3MU5B7G4D)**, Automatic signing.
3. Press **Run (⌘R)**. The Kotlin framework auto-builds via the pre-build script (first build is
   slow).
4. On the phone, first launch is blocked until you trust the profile:
   **Settings ▸ General ▸ VPN & Device Management ▸ (your Apple ID) ▸ Trust**, then reopen the app.

## Step 2 — First-run setup on the iPhone

1. **Sign in** with your phone number (Supabase sends an OTP).
2. Complete onboarding. **Write down the 24-word recovery phrase** it shows — you'll type it into
   the Mac to pair (it derives the shared encryption key). Same phrase = same key.
3. Go to **Settings ▸ Auto-capture** and turn on **"Capture SMS forwarded to your Mac."**
   (This only arms once you're signed in *and* a recovery phrase/master key exists — both true now.)

## Step 3 — Turn on Text Message Forwarding (iPhone)

**Settings ▸ Messages ▸ Text Message Forwarding ▸** toggle **your Mac ON**. A code appears in the
Mac's Messages app — enter it on the phone. (If the Mac isn't listed, sign Messages on the Mac into
the same Apple ID and enable Messages in iCloud.)

This is what mirrors your SMS into the Mac's `chat.db`, where the companion reads them.
> History note: only SMS that Text Message Forwarding has *already mirrored* to this Mac can be
> backfilled. Messages from before forwarding was on won't be in `chat.db`. iMessage-delivered bank
> alerts (blue, not green) are excluded — only true SMS.

## Step 4 — Run the Mac companion

The Supabase config is already written to `~/.hisaab/companion.properties` (same project as the
phone), and the app is built at:

```
macHelper/build/compose/binaries/main/app/HisaabCompanion.app
```

1. **Launch it:** it's unsigned, so right-click → **Open** the first time (Gatekeeper).
2. **Grant Full Disk Access:** System Settings ▸ Privacy & Security ▸ **Full Disk Access** ▸ add
   **HisaabCompanion** (needed to read `~/Library/Messages/chat.db`). Quit and reopen the app.
3. **Sign in** with the **same phone number** (OTP) → same Supabase account.
4. **Pair:** enter the **same 24-word recovery phrase** from the iPhone (Step 2).
5. **Connect email:** enter your mailbox address + **app-specific password**. This kicks off the
   **12-month backfill** and includes email in the push loop.

## Step 5 — Verify

- The Mac companion shows **"Synced N item(s)"** every ~20s as it pushes.
- On the iPhone, open the **capture inbox** — historical SMS and last-12-months bank emails appear
  (auto-posted, or queued for review depending on confidence).
- The phone drains and deletes each relay row after ingesting it (check Supabase
  `relay_captures` shrinks back toward empty).

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| Mac stuck on "needs config" | `~/.hisaab/companion.properties` missing/blank — re-run the prep, or set `HISAAB_SUPABASE_URL` / `HISAAB_SUPABASE_ANON_KEY` env vars. |
| Mac: "SMS sync error" reading chat.db | Full Disk Access not granted to **HisaabCompanion.app** (Step 4.2). Quit/reopen after granting. |
| Nothing arrives on the phone | (a) iPhone relay toggle off; (b) phone not signed in / no recovery phrase; (c) phone and Mac signed into **different** accounts; (d) phrase mismatch (decrypts to a different key → rows can't be opened). |
| No old SMS | Text Message Forwarding wasn't on long enough to mirror history into `chat.db`. |
| Email pulls nothing | Wrong/again-needed **app-specific password**, or provider not in {Gmail, Outlook.com, Yahoo, iCloud}. |
| App won't launch after a few days | Free signing expired (2026-06-08) — re-Run from Xcode. |

## Want a wider email window than 12 months?

Change `EMAIL_BACKFILL_MS` in
`macHelper/src/jvmMain/kotlin/app/hisaab/mac/CompanionController.kt` (currently 365 days), then
rebuild: `./gradlew :macHelper:createDistributable`. Delete
`~/Library/Application Support/HisaabCompanion/email_cursor` to re-trigger the first-connect
backfill.
