-- AgarthaVision — verification redesign (ADR-004)
-- Per docs/adr/004-verification-as-hitl-correction.md:
--   - Rejections persist as labeled FALSE_POSITIVE detections (not deleted)
--   - Each detection carries a per-box expert verdict
--   - Sample row gains needs_reannotation flag for frame-level false-negative reports
--   - Stale "roboflow_model_version" column is renamed to reflect the self-hosted
--     inference container chosen in ADR-003 (the column always held the self-hosted
--     version after ADR-003; only the name was lagging)
--   - detections.verified_by_user dropped (no data existed; replaced by verdict column)
--
-- Apply via: Supabase dashboard → SQL Editor → paste → Run.
-- Reversible: yes (see "Rollback" block at the bottom — commented out by default).

-- ── samples ──────────────────────────────────────────────────────────────────

-- Rename pre-ADR-003 column to reflect the self-hosted inference container.
alter table public.samples
    rename column roboflow_model_version to inference_model_version;

-- Frame-level false-negative flag set by VerificationSheet Q4 ("Did the model miss
-- any eggs?"). When true, the sample is queued for offline annotation (CVAT /
-- Label Studio) before retraining.
alter table public.samples
    add column needs_reannotation boolean not null default false;


-- ── detections ───────────────────────────────────────────────────────────────

-- Per-detection expert verdict. No default needed — all insertions supply a verdict.
alter table public.detections
    add column verdict text not null default 'CONFIRMED'
        check (verdict in ('CONFIRMED', 'FALSE_POSITIVE', 'WRONG_CLASS', 'BOX_INCORRECT'));

-- Expert's corrected class. NULL except when verdict = 'WRONG_CLASS', where it
-- holds the species the expert selected (Ascaris lumbricoides / Trichuris
-- trichiura / Hookworm / free-text "Other"). Stored verbatim — normalization
-- happens at retraining query time (see ADR-004 §Open questions).
alter table public.detections
    add column expert_class text;

-- Drop the pre-ADR-004 boolean — no data exists, so no backfill needed.
alter table public.detections
    drop column verified_by_user;

-- Retraining queries filter by verdict (e.g., "give me every FALSE_POSITIVE for
-- the next round of negative examples"); index it cheaply.
create index detections_verdict_idx on public.detections(verdict);


-- ── Rollback (commented out — uncomment + run if this migration must be reverted)

-- drop index if exists public.detections_verdict_idx;
-- alter table public.detections drop column if exists expert_class;
-- alter table public.detections drop column if exists verdict;
-- alter table public.detections add column verified_by_user boolean not null default true;
-- alter table public.samples    drop column if exists needs_reannotation;
-- alter table public.samples    rename column inference_model_version to roboflow_model_version;
