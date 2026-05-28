-- 0007 · allow null bbox on manual-capture detections
--
-- AI-confirmed detections always have a bounding box (from the inference server).
-- Manual captures (samples.is_manual = true) do not — the medtech tags the whole
-- frame as a species without drawing a box. Offline annotation can fill these in
-- later if needed (samples.needs_reannotation = true). Per ADR-005.
--
-- See docs/adr/005-session-as-smear-manual-capture-and-repeat-flag.md and
-- docs/04_CLOUD_BACKEND_PLAN.md §4.1.5.

alter table public.detections
    alter column bbox_x drop not null,
    alter column bbox_y drop not null,
    alter column bbox_w drop not null,
    alter column bbox_h drop not null;

-- ── revert (commented) ────────────────────────────────────────────────────────
-- Paste these into the Supabase SQL editor to roll this migration back.
-- NOTE: only safe to revert if no rows currently hold null bbox values.
-- Run `SELECT count(*) FROM detections WHERE bbox_x IS NULL` first to confirm.
--
-- alter table public.detections
--     alter column bbox_x set not null,
--     alter column bbox_y set not null,
--     alter column bbox_w set not null,
--     alter column bbox_h set not null;
