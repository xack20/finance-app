# Mac SMS Companion — E2EE Capture Relay (Design)

**Date:** 2026-06-02
**Status:** Proposed (awaiting build approval)
**Goal:** Give iPhone users automatic finance-SMS capture by reading SMS that iOS
*forwards* to a Mac, parsing them with the existing pipeline on the phone, and
surfacing the result in the iPhone ledger — without manual paste.

> Why this exists: iOS forbids third-party apps from reading SMS content (no API,
> App-Store-rejectable). The only legitimate automatic route to real SMS content is
> Apple "Text Message Forwarding" → a signed-in Mac's Messages app
> (`~/Library/Messages/chat.db`). This design relays those messages to the phone.

---

## 1. Decisions (locked)

- **Sync model:** narrow, one-directional **E2EE capture relay** (Mac → iPhone). NOT
  the full bidirectional multi-device op-log sync (that remains future work; this
  relay table can later be subsumed by it).
- **Where parsing happens:** the **iPhone parses**, not the Mac. The Mac relays the
  **raw** capture only (`sender`, `body`, `receivedAt`, `channel=SMS`). This keeps the
  Mac helper minimal (no ledger DB, no `AccountMatcher`, no LLM) and keeps the phone
  the single source of truth, reusing the existing `CapturePipeline`.
- **Encryption:** end-to-end. Supabase only ever stores ciphertext. Key material is
  derived on each device from the user's existing recovery phrase (mnemonic); the
  server never sees plaintext or keys.
- **Distribution:** the Mac app reads `chat.db`, so it **cannot be sandboxed** → ships
  as a notarized direct download (not the Mac App Store) and needs **Full Disk Access**.
- **On-device LLM:** N/A on Mac (MediaPipe is Android-only) and unnecessary — the Mac
  doesn't parse.

## 2. Architecture

```
 iPhone (SMS) ──forward──▶ Mac Messages.app ──▶ chat.db (SQLite, FDA)
                                                     │
                              Mac helper: read new rows since cursor
                                                     │  RawCapture(sender,body,ts,SMS)
                                                     ▼
                              encrypt (XChaCha20-Poly1305, relay key)
                                                     │  ciphertext blob
                                                     ▼
                           Supabase table  relay_captures  (RLS: owner only)
                                                     │  Realtime / poll
                                                     ▼
                 iPhone RemoteCaptureSource: pull → decrypt → RawCapture
                                                     │
                              EXISTING CapturePipeline.process(raw)
                                                     ▼
                        local ledger (auto-post) / review inbox  +  delete relay row
```

- The relay is a **transient queue**: once the phone ingests a row it **deletes** it
  (RLS permits deleting own rows). Optional server TTL (e.g. 14 days) as a backstop.
- The relay plugs into the **existing** `CaptureCoordinator` as just another
  `CaptureSource` on the phone — no pipeline changes needed.

## 3. Supabase relay table (new backend — user applies the SQL)

```sql
-- relay_captures: transient, append-only, E2EE. Server sees only ciphertext.
create table public.relay_captures (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references auth.users(id) on delete cascade,
  device_id    text not null,            -- source device (the Mac), for debugging
  nonce        text not null,            -- base64 of the 24-byte XChaCha20-Poly1305 nonce
  ciphertext   text not null,            -- base64 of the encrypted RawCapture JSON (AEAD tag included)
  created_at   timestamptz not null default now()
);
alter table public.relay_captures enable row level security;
create policy "own rows" on public.relay_captures
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create index on public.relay_captures (user_id, created_at);
```

- RLS ties every row to the authenticated user; a device authenticates to Supabase
  with the **same phone-OTP login** the app already uses.
- No plaintext columns. `device_id` is non-sensitive.

## 4. Crypto (E2EE)

- Root: the existing **master secret** (derived from the recovery-phrase mnemonic at
  onboarding; stored in Keychain/EncryptedPrefs on the phone). The Mac derives the
  **same** master secret by having the user enter the recovery phrase once.
- Relay key: `relayKey = HKDF-SHA256(masterSecret, info="hisaab/relay/v1", 32 bytes)`.
- Payload: `RawCapture` → canonical JSON → `XChaCha20-Poly1305(relayKey, nonce, json)`.
  Random 24-byte nonce per row. (libsodium multiplatform binding is already a dep.)
- Both devices derive `relayKey` independently from the shared mnemonic → no key
  exchange, no server-held secrets.

## 5. Mac helper app

- **Module:** new Gradle module `:macHelper` (Kotlin/JVM + optional Compose Desktop
  menu-bar UI). Reuses `commonMain` domain types + the crypto + Supabase-kt (JVM works
  via the Ktor CIO/Java engine). It does **not** embed the ledger or pipeline.
- **chat.db reader:** read `~/Library/Messages/chat.db` (SQLite via JDBC).
  - Tables: `message` (`text`, `attributedBody` blob fallback, `date` in Apple epoch =
    nanoseconds since 2001-01-01, `is_from_me`), `handle` (sender id), joins via
    `chat_message_join`/`chat_handle_join`.
  - Filter to **SMS** (forwarded green-bubble messages; `service = 'SMS'`), incoming
    only (`is_from_me = 0`), since a stored cursor (max `date` seen).
  - Text may live in `attributedBody` (NSAttributedString archive) when `text` is null
    on newer macOS → decode needed.
- **Pairing / setup UX:** (1) sign in to Supabase (phone OTP, same as app); (2) enter
  recovery phrase once → derive master secret → relay key (store derived key in macOS
  Keychain, never the phrase); (3) grant Full Disk Access (guided); (4) confirm "Text
  Message Forwarding" is on for this Mac.
- **Loop:** poll chat.db on change (or every N s) → new rows → `RawCapture` → encrypt →
  insert into `relay_captures`. Advance cursor only after a successful insert.
- **Distribution:** notarized `.dmg`; runs as a login-item / menu-bar agent.

## 6. iPhone / common ingest

- New `RemoteCaptureSource` (commonMain, behind an expect for the Supabase client):
  subscribe to `relay_captures` via Realtime (fallback: poll on app foreground +
  `catchUp`), decrypt each row → `RawCapture` → hand to the existing
  `CaptureCoordinator` handler (`CapturePipeline.process`), then **delete** the row.
- Dedup is automatic: `dedupHash = SHA256(sender+body)` already prevents double-posting
  if the same content arrives twice.
- Settings: a "Mac companion" section showing pairing status + an enable toggle
  (`relayCaptureEnabled` in `CaptureConfig`), mirroring the SMS/notification toggles.
- Works on **iOS, Android, and web** (the relay source is platform-agnostic), so it
  doubles as cross-device capture for any paired phone.

## 7. Phases (each independently verifiable)

- **P0 — this doc.**
- **P1 — Crypto + schema (no UI, no Mac):** relay encrypt/decrypt module in commonMain
  with round-trip unit tests; the `relay_captures` SQL migration file (user applies in
  their Supabase project). *Verifiable: unit tests green.*
- **P2 — Phone ingest:** `RemoteCaptureSource` + `relayCaptureEnabled` config + wire
  into `CaptureCoordinator`; Settings "Mac companion" pairing/status UI; ingest+delete.
  *Verifiable: a hand-inserted encrypted test row appears as a candidate on device.*
- **P3 — Mac helper:** `:macHelper` module, chat.db reader, Supabase login + mnemonic
  pairing, encrypt+push, cursor, FDA onboarding, menu-bar UI, notarized build.
  *Verifiable: a forwarded SMS on the Mac shows up in the iPhone review inbox.*
- **P4 — Hardening:** backfill window, attributedBody decode edge cases, error/empty
  states, relay TTL cleanup, dedup tests, docs + setup guide.

## 8. Security model

- Server (Supabase) is **zero-knowledge** of capture content: ciphertext + nonce only,
  RLS-scoped to the owner. Compromise of Supabase yields no readable SMS.
- The recovery phrase is entered on the Mac once and **never stored** (only the derived
  key, in Keychain). Same trust model as the phone.
- Full Disk Access is the user's explicit grant; the helper reads only `chat.db` and
  writes only encrypted rows for the signed-in user.
- Raw SMS bodies leave the phone's "on-device" boundary **only as ciphertext** to the
  user's own relay rows — consistent with the app's privacy-first stance.

## 9. Risks / open questions

- **Mac must be on + signed in + forwarding enabled** for capture to flow (inherent).
- `chat.db` schema varies by macOS version (`attributedBody` vs `text`, `service`
  values) — P3 must handle current macOS; verify on the user's version.
- Relay latency: capture appears on the phone when it next syncs (Realtime if open,
  else on next foreground) — acceptable for a ledger.
- Supabase egress/row cost is negligible (transient, deleted after ingest).
- Backfill scope on first pairing (how far back to read chat.db) — default 90 days to
  match SMS backfill; make it configurable.
