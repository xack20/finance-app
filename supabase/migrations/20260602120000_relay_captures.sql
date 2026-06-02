-- Mac→phone E2EE capture relay (see docs/superpowers/specs/2026-06-02-mac-sms-companion-relay-design.md)
--
-- Apply in your Supabase project: SQL Editor → paste → Run, or `supabase db push`.
-- The server is ZERO-KNOWLEDGE: it stores only the XChaCha20-Poly1305 nonce + ciphertext.
-- Plaintext SMS never reaches the server. Rows are transient — the phone deletes each row
-- after it ingests the capture (RLS allows owners to delete their own rows). created_at +
-- the index support a server-side TTL cleanup job as a backstop.

create table if not exists public.relay_captures (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users (id) on delete cascade,
  device_id   text not null,            -- source device label (e.g. the Mac); non-sensitive
  nonce       bytea not null,           -- 24-byte XChaCha20-Poly1305 nonce
  ciphertext  bytea not null,           -- encrypted RawCapture JSON (includes AEAD tag)
  created_at  timestamptz not null default now()
);

alter table public.relay_captures enable row level security;

-- Owner-only: a device may read/insert/delete only its signed-in user's rows.
drop policy if exists "relay_captures owner access" on public.relay_captures;
create policy "relay_captures owner access"
  on public.relay_captures
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

create index if not exists relay_captures_user_created_idx
  on public.relay_captures (user_id, created_at);

-- Optional backstop cleanup (uncomment if pg_cron is enabled): delete rows older than 14 days
-- in case a device never drained them.
-- select cron.schedule(
--   'relay_captures_ttl',
--   '0 * * * *',
--   $$delete from public.relay_captures where created_at < now() - interval '14 days'$$
-- );
