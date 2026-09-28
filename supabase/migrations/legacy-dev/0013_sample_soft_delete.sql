-- 0013 · samples.deleted_at
--
-- Run via: Supabase dashboard → SQL Editor → paste → Run. Apply after 0012.
--
-- ── Why a tombstone and not a delete ─────────────────────────────────────────
-- A verified sample is never deleted (C8, docs/non-negotiables.md): `detections` doubles as
-- the retraining corpus and a rejection is a labelled FALSE_POSITIVE row, not a deletion.
-- But a medtech who captured the same egg twice needs the duplicate out of the queue, the
-- counts and the report. Setting `deleted_at` hides the sample everywhere a human or a report
-- looks, while its detections, its findings rows and its Storage object stay exactly where
-- they are.
--
-- This replaces the `samples.is_repeat` workaround, which existed only because there was no
-- way to remove a duplicate. That flag is dropped in the same change. NOTE for anyone who
-- finds `0006_sample_is_manual.sql` and is misled by its note that is_repeat is a deliberate
-- Room-only column: that is now stale. It was never synced, so dropping it owes no migration
-- here — only a Room version bump. 0006 is applied and is not edited (C6).
--
-- Unverified frames are NOT tombstoned. They are hard-deleted on-device before submission,
-- which is C8's existing local exception and touches no remote row.
--
-- ── What is deliberately NOT added ───────────────────────────────────────────
-- No DELETE policy for the `samples` Storage bucket. `0003_storage_rls.sql` creates none on
-- purpose and `0009_storage_admin_read.sql` restates the stance; a tombstoned sample keeps
-- its JPEG. Nothing here weakens that.
--
-- ── The rule this creates for every future query ─────────────────────────────
-- Every query that lists or counts samples must filter `deleted_at is null`. Miss one and a
-- deleted duplicate reappears in a report. On the client this is enforced by a naming rule
-- plus SoftDeleteGuardTest: a DAO method that SELECTs over `samples` must contain
-- `deleted_at is null` unless its name ends in `IncludingDeleted`.
--
-- See schema.ts, docs/map/objects/Sample.md, docs/constraints.md C8, and ticket 86d4ab4vm.

alter table public.samples add column deleted_at timestamptz;

-- Partial index: every list and count query filters on this, and the live rows are the only
-- ones any of them want.
create index samples_live_session_idx
    on public.samples (session_id)
    where deleted_at is null;

-- ── revert (commented) ────────────────────────────────────────────────────────
-- Paste this into the Supabase SQL editor to roll this migration back.
--
-- drop index if exists public.samples_live_session_idx;
-- alter table public.samples drop column if exists deleted_at;
