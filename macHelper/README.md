# Hisaab Mac Companion (`:macHelper`)

Reads SMS that iOS forwards to this Mac (Apple **Text Message Forwarding** → Messages →
`~/Library/Messages/chat.db`), encrypts each one with your account key, and pushes it to Supabase
so your **iPhone** Hisaab app pulls and parses it. This is the only automatic way to get bank SMS
into Hisaab on iPhone (iOS won't let apps read SMS directly).

End-to-end encrypted: Supabase only ever stores ciphertext. The Mac derives the same key as the
phone from your **recovery phrase** (entered once, never stored). Design:
`docs/superpowers/specs/2026-06-02-mac-sms-companion-relay-design.md`.

## Prerequisites

- A Mac signed into the **same iCloud** as your iPhone, with **Text Message Forwarding** enabled
  (iPhone → Settings → Messages → Text Message Forwarding → toggle on this Mac).
- The Supabase `relay_captures` table applied (`supabase/migrations/20260602120000_relay_captures.sql`).
- JDK 17+ for building (`JAVA_HOME` set).
- For a distributable build: an **Apple Developer ID Application** certificate + an app-specific
  password (notarization is required so Gatekeeper will run a Full-Disk-Access app).

## Configure the Supabase project

The helper needs your project URL + anon key (the anon key is public-by-design; RLS protects data).
Either set env vars:

```sh
export HISAAB_SUPABASE_URL="https://YOUR-PROJECT.supabase.co"
export HISAAB_SUPABASE_ANON_KEY="YOUR-ANON-KEY"
```

…or create `~/.hisaab/companion.properties`:

```properties
supabase.url=https://YOUR-PROJECT.supabase.co
supabase.anon_key=YOUR-ANON-KEY
```

## Run locally (unsigned — for testing on your own Mac)

```sh
./gradlew :macHelper:run            # launches the app
./gradlew :macHelper:packageDmg     # builds an unsigned .dmg under macHelper/build/compose/binaries
```

An unsigned build still runs on the Mac that built it once you grant Full Disk Access.

## Build a signed + notarized .dmg (for distribution)

Provide your Developer ID via env (never commit these), then package — signing + notarization turn
on automatically when `HISAAB_MAC_SIGN_IDENTITY` is set:

```sh
export HISAAB_MAC_SIGN_IDENTITY="Developer ID Application: Your Name (TEAMID)"
export HISAAB_MAC_APPLE_ID="you@example.com"
export HISAAB_MAC_NOTARY_PASSWORD="app-specific-password"   # appleid.apple.com → App-Specific Passwords
export HISAAB_MAC_TEAM_ID="TEAMID"

./gradlew :macHelper:packageReleaseDmg     # signs (hardened runtime + entitlements.plist)
./gradlew :macHelper:notarizeReleaseDmg    # uploads to Apple for notarization
```

`entitlements.plist` grants the JVM the hardened-runtime exceptions it needs (JIT + loading the
bundled libsodium / sqlite-jdbc native libs).

## First-run setup (in the app)

1. **Grant Full Disk Access** — the app's "Open Full Disk Access settings" button takes you there;
   add the app, then relaunch (required to read `chat.db`).
2. **Sign in** with the same phone number as your Hisaab app (OTP).
3. **Pair** by entering your 24-word recovery phrase (derives the encryption key locally; never
   stored or sent).
4. Leave it running — every ~20s it reads new forwarded SMS and syncs them to your phone.

## Privacy

- SMS bodies leave this Mac only as **ciphertext** (XChaCha20-Poly1305) in your own relay rows;
  the phone deletes each row after ingesting it.
- The recovery phrase is used only to derive the key in memory; only the derived key is cached
  (macOS Keychain). The plaintext phrase is never persisted.
- Distributed **outside the Mac App Store** (Full Disk Access can't be sandboxed).
