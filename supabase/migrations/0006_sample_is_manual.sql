-- 0006 · add is_manual flag to samples
--
-- A "manual" sample is a snapshot the medtech took explicitly via the Capture
-- button on CaptureScreen, without AI inference. Counted in EPG; the detection
-- row uses null bbox columns (see 0007). Per ADR-005.
--
-- See docs/adr/005-session-as-smear-manual-capture-and-repeat-flag.md and
-- docs/04_CLOUD_BACKEND_PLAN.md §4.1.4.
--
-- NOTE: samples.is_repeat is intentionally NOT added to Supabase — it's a
-- Room-only workflow flag (the medtech's "I already counted this one" marker
-- for sifting through outputs). Repeats never sync.

alter table public.samples
    add column is_manual boolean not null default false;

create index if not exists samples_session_manual_idx
    on public.samples(session_id, is_manual);

-- ── revert (commented) ────────────────────────────────────────────────────────
-- Paste these into the Supabase SQL editor to roll this migration back.
--
-- drop index if exists samples_session_manual_idx;
-- alter table public.samples drop column is_manual;
