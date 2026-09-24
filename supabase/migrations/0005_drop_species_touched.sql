-- 0005 · Drop detections.species_touched
--
-- Run via: Supabase dashboard → SQL Editor → paste → Run. By hand, once, per C6.
-- Target: project `agarthavision` (zxojfpfarhhoxjjicphi).
-- Requires: 0004_predictions.sql.
--
-- ── Why ──────────────────────────────────────────────────────────────────────
-- The column was meant to keep "a human did not object" apart from "a human confirmed this"
-- on a pre-filled row. It never could: it recorded taps, not judgements. A medtech who reads
-- a row and agrees submits it untouched, so `false` meant both "agreed" and "never looked",
-- and on a CONFIRMED row `true` meant only that the checkbox was toggled off and on again.
-- Every row it marked for a real reason was already marked by something else:
--
--   verdict = WRONG_CLASS            → the medtech picked the species
--   prediction_id null (0004)        → an egg the medtech added
--   verdict = FALSE_POSITIVE         → the species is not a claim at all
--
-- Its other job, telling a hand-drawn box from the model's, belongs to `prediction_id` and
-- the box/verdict pairing documented in 0004 (14zcqnthrx8). Nothing reads the column.
--
-- ── Rollout ──────────────────────────────────────────────────────────────────
-- Apply only once no device runs a build older than the one that stopped sending the
-- column. An older build names `species_touched` in every detection upsert, and PostgREST
-- refuses a payload naming a column that does not exist, so those phones would stop syncing
-- verified samples. The new build neither sends nor reads it, so it works before and after.

begin;

alter table public.detections drop column species_touched;

commit;
