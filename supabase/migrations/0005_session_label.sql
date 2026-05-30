-- 0005 · add sessions.label
--
-- The fecal-smear name the medtech enters at session creation. Distinct from
-- sessions.notes (free-form observations during/after the session). One session
-- represents one fecal smear per ADR-005.
--
-- See docs/adr/005-session-as-smear-manual-capture-and-repeat-flag.md and
-- docs/04_CLOUD_BACKEND_PLAN.md §4.1.3.

alter table public.sessions
    add column label text;

create index if not exists sessions_user_started_idx
    on public.sessions(user_id, started_at desc);

-- ── revert (commented) ────────────────────────────────────────────────────────
-- Paste these into the Supabase SQL editor to roll this migration back.
--
-- drop index if exists sessions_user_started_idx;
-- alter table public.sessions drop column label;
