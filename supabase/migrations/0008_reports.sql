-- 0008 · persisted session reports
--
-- Each row records one report generation event for a session. Multiple reports
-- per session are allowed (ordered by generated_at descending). The CSV file
-- itself stays local on the device; only this metadata + aggregate stats are
-- mirrored here ("row-only sync" per the Phase 1 REPORTS rollout in
-- mutable-growing-graham.md).
--
-- The `session` report_type is the only value supported in Phase 1.
-- `administrative` is reserved for the Phase 2 cross-session report variant.
--
-- See docs/ERD.md (Module 3.1) and docs/03_MOBILE_APP_PLAN.md.

create table public.reports (
    id                   uuid primary key default uuid_generate_v4(),
    session_id           uuid not null references public.sessions(id) on delete cascade,
    user_id              uuid not null references public.profiles(id),
    report_type          text not null default 'session' check (report_type in ('session')),
    generated_at         timestamptz not null default now(),
    total_samples        integer not null,
    total_eggs_confirmed integer not null,
    positive_species     text[] not null default '{}',
    epg_per_species      jsonb not null default '{}'::jsonb,
    csv_file_path        text,
    created_at           timestamptz not null default now()
);

create index reports_session_generated_idx
    on public.reports(session_id, generated_at desc);
create index reports_user_generated_idx
    on public.reports(user_id, generated_at desc);

alter table public.reports enable row level security;

create policy "reports_select_own"
on public.reports for select
using (
    auth.uid() = user_id
    or (select role from public.profiles where id = auth.uid()) = 'admin'
);

create policy "reports_insert_own"
on public.reports for insert
with check ( auth.uid() = user_id );

-- ── revert (commented) ────────────────────────────────────────────────────────
-- Paste these into the Supabase SQL editor to roll this migration back.
--
-- drop policy if exists "reports_insert_own" on public.reports;
-- drop policy if exists "reports_select_own" on public.reports;
-- drop index if exists reports_user_generated_idx;
-- drop index if exists reports_session_generated_idx;
-- drop table if exists public.reports;
