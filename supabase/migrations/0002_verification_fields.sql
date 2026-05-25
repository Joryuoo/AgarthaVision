-- AgarthaVision — verification redesign (ADR-004)
-- Per docs/adr/004-verification-as-hitl-correction.md:
--   - Rejections persist as labeled FALSE_POSITIVE detections (not deleted)
--   - Each detection carries a per-box expert verdict
--   - Sample row gains needs_reannotation flag for frame-level false-negative reports
--   - Stale "roboflow_model_version" column is renamed to reflect the self-hosted
--     inference container chosen in ADR-003 (the column always held the self-hosted
--     version after ADR-003; only the name was lagging)
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

-- Per-detection expert verdict. Default 'CONFIRMED' keeps the migration safe for
-- any rows already in dev: they pre-date this design and were always inserted as
-- "verified_by_user = true", which under the new model is CONFIRMED.
alter table public.detections
    add column verdict text not null default 'CONFIRMED'
        check (verdict in ('CONFIRMED', 'FALSE_POSITIVE', 'WRONG_CLASS', 'BOX_INCORRECT'));

-- Expert's corrected class. NULL except when verdict = 'WRONG_CLASS', where it
-- holds the species the expert selected (Ascaris lumbricoides / Trichuris
-- trichiura / Hookworm / free-text "Other"). Stored verbatim — normalization
-- happens at retraining query time (see ADR-004 §Open questions).
alter table public.detections
    add column expert_class text;

-- Mark the old boolean as deprecated; preserve it so existing rows in dev are
-- not lost and any pre-0002 code still reads sensible values.
comment on column public.detections.verified_by_user is
    'DEPRECATED as of ADR-004 (migration 0002). Use the verdict column instead. '
    'Pre-0002 rows defaulted to true, which maps to verdict = CONFIRMED; '
    'pre-0002 rows with verified_by_user = false are backfilled below to FALSE_POSITIVE.';

-- Backfill: in the unlikely event any dev row exists with verified_by_user = false
-- (the old "Reject = delete" flow would have deleted these, but a misfire is
-- possible), reflect that decision in the new verdict column.
update public.detections
    set verdict = 'FALSE_POSITIVE'
    where verified_by_user = false;

-- Retraining queries filter by verdict (e.g., "give me every FALSE_POSITIVE for
-- the next round of negative examples"); index it cheaply.
create index detections_verdict_idx on public.detections(verdict);


-- ── Rollback (commented out — uncomment + run if this migration must be reverted)

-- drop index if exists public.detections_verdict_idx;
-- alter table public.detections drop column if exists expert_class;
-- alter table public.detections drop column if exists verdict;
-- alter table public.samples    drop column if exists needs_reannotation;
-- alter table public.samples    rename column inference_model_version to roboflow_model_version;
