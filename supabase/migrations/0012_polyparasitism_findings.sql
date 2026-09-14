-- 0012 · sample_species_findings, detections.species_touched, and the UPDATE policies
--        an editable sample needs in order to re-sync
--
-- Run via: Supabase dashboard → SQL Editor → paste → Run.
--
-- ── Why a findings table ──────────────────────────────────────────────────────
-- One field can hold eggs of more than one species. Dr. Bayron, 2026-09-07: "It is normal to
-- see different egg types within the same field." The app recorded exactly one species per
-- frame, so a mixed field could not be written down at all.
--
-- The count is per species AND stage, never a frame total. WHO infection-intensity
-- thresholds are species-specific and differ by more than an order of magnitude — Ascaris
-- heavy is >= 50,000 EPG, hookworm heavy is >= 4,000 — so a single combined per-field count
-- cannot be graded and is clinically uninterpretable.
--
-- Two shapes were rejected; recorded here so they are not re-proposed:
--   * a single `samples.lpf_count` integer — cannot represent a mixed field at all.
--   * a count column on `detections` — that table is one row per detected egg, so the
--     per-species count from the model is already COUNT(*) GROUP BY species, and a manual
--     capture has no detection rows to hang a count on. `bbox_*` is nullable precisely so a
--     medtech can tag a frame without drawing a box (`0007_detection_bbox_nullable.sql`).
--
-- Zero findings rows is a valid and meaningful state: a clean field. There is deliberately
-- no "no egg" species value — a sentinel would have to be excluded from every species-keyed
-- aggregation forever.
--
-- ── Why species_touched ───────────────────────────────────────────────────────
-- It is a provenance flag, not a verdict. Input fields are pre-filled from the model output
-- so the medtech edits only what is wrong (model-assisted pre-labelling). That pre-fill means
-- an untouched submission yields verdict = CONFIRMED via
-- `data/local/mapper/VerificationMapper.kt`, so "a human did not object" would be stored as
-- "a human confirmed this" — and `detections` doubles as the retraining corpus. This column
-- separates the two without adding a verdict value: true iff the medtech made a deliberate
-- species selection on that box, including re-picking the pre-filled value. Retraining
-- should weight false rows lower. `verdict` keeps its existing meaning, untouched.
--
-- Note also that `expert_class` is, from this change on, populated for BOX_INCORRECT rows
-- too, not only WRONG_CLASS as `0002_verification_fields.sql` describes. An egg with a
-- misplaced box is still an egg and still has to be counted, so the species question is now
-- asked whenever the medtech says the box contains an egg. No DDL change is owed — 0002 is
-- applied and C6 forbids editing it — and the widening is documented in schema.ts and
-- docs/map/objects/Detection.md.
--
-- ── Why the UPDATE policies ───────────────────────────────────────────────────
-- They are not optional extras. Verified samples become re-editable (ticket 86d4ab4vm), so a
-- sample can sync more than once and every remote write in
-- `data/supabase/SampleRemoteDataSource.kt` becomes an upsert. An upsert that conflicts
-- performs an UPDATE, and `0001_init.sql` created select and insert policies only for these
-- two tables — `sessions` got `sessions_update_own`, `samples` and `detections` never did.
-- Without these, every re-sync of an edited sample is silently rejected by RLS.
--
-- See schema.ts, docs/map/objects/Finding.md, docs/map/objects/Detection.md,
-- docs/map/processes/validate.md, and tickets 86d4ab4tq / 86d4ab4vm.

-- ── sample_species_findings ──────────────────────────────────────────────────
-- One row per (species, stage) the medtech logged on one frame, carrying that pair's
-- low-power-field egg count.
create table public.sample_species_findings (
    id        uuid primary key default uuid_generate_v4(),
    sample_id uuid not null references public.samples(id) on delete cascade,

    -- The canonical class name, or free text when the dropdown does not cover the species.
    -- Same convention and same free-text tolerance as detections.class_label /
    -- detections.expert_class, so the two group together.
    species   text not null check (length(btrim(species)) > 0),

    -- Null when the species has no defined stage set. Values match EggStage.remoteValue and
    -- reuse the list chosen for ticket 86d4a6jwy (`0010_verification_stage.sql`); the Ascaris
    -- morphology values CORTICATED / DECORTICATED / FERTILIZED stay deferred and can be added
    -- later without breaking stored rows, since storage is by string value.
    stage     text check (stage in ('UNFERTILIZED', 'UNEMBRYONATED', 'EMBRYONATED', 'LARVATED')),

    -- Eggs of this species and stage in this one low-power field. Always positive: a count of
    -- zero is the absence of a row, not a row holding zero.
    egg_count integer not null check (egg_count > 0)
);

-- Uniqueness is expressed as two partial indexes rather than one
-- `unique nulls not distinct (...)` constraint, because that form needs PostgreSQL 15 and
-- these do not. Nothing upserts this table — the client deletes a sample's rows and reinserts
-- them, because a species the medtech removed on re-open must actually disappear — so no
-- PostgREST `on_conflict` target is required and the weaker form costs nothing.
create unique index sample_species_findings_unique_staged
    on public.sample_species_findings (sample_id, species, stage)
    where stage is not null;

create unique index sample_species_findings_unique_unstaged
    on public.sample_species_findings (sample_id, species)
    where stage is null;

create index sample_species_findings_sample_idx  on public.sample_species_findings (sample_id);
create index sample_species_findings_species_idx on public.sample_species_findings (species);

-- ── detections.species_touched ───────────────────────────────────────────────
alter table public.detections
    add column species_touched boolean not null default false;

comment on column public.detections.species_touched is
    'True when the medtech made a deliberate species selection on this box, including re-picking the pre-filled model class. False means the model-assisted pre-fill was submitted untouched: a non-objection, not a confirmation. Training-weight signal only; verdict is unaffected.';

-- ── Row Level Security ───────────────────────────────────────────────────────
alter table public.sample_species_findings enable row level security;

-- Scoped through the parent sample's user_id, the same pattern as detections in 0001.
create policy "findings_select_via_sample"
on public.sample_species_findings for select
using (
    exists (
        select 1 from public.samples s
        where s.id = sample_id
        and (
            s.user_id = auth.uid()
            or (select role from public.profiles where id = auth.uid()) = 'admin'
        )
    )
);

create policy "findings_insert_via_sample"
on public.sample_species_findings for insert
with check (
    exists (select 1 from public.samples s where s.id = sample_id and s.user_id = auth.uid())
);

create policy "findings_update_via_sample"
on public.sample_species_findings for update
using (
    exists (select 1 from public.samples s where s.id = sample_id and s.user_id = auth.uid())
)
with check (
    exists (select 1 from public.samples s where s.id = sample_id and s.user_id = auth.uid())
);

-- A count is a current statement, not evidence. When a medtech re-opens a verified sample and
-- removes a species they logged in error, the superseded row must go. This is not a breach of
-- C8: the things C8 protects — the JPEG and the detection rows — are still never deleted.
create policy "findings_delete_via_sample"
on public.sample_species_findings for delete
using (
    exists (select 1 from public.samples s where s.id = sample_id and s.user_id = auth.uid())
);

-- Re-syncing an edited sample updates rows that already exist.
create policy "samples_update_own"
on public.samples for update
using ( auth.uid() = user_id )
with check ( auth.uid() = user_id );

create policy "detections_update_via_sample"
on public.detections for update
using (
    exists (select 1 from public.samples s where s.id = sample_id and s.user_id = auth.uid())
)
with check (
    exists (select 1 from public.samples s where s.id = sample_id and s.user_id = auth.uid())
);

-- ── revert (commented) ────────────────────────────────────────────────────────
-- Paste these into the Supabase SQL editor to roll this migration back.
--
-- drop policy if exists "detections_update_via_sample" on public.detections;
-- drop policy if exists "samples_update_own"           on public.samples;
-- drop policy if exists "findings_delete_via_sample"   on public.sample_species_findings;
-- drop policy if exists "findings_update_via_sample"   on public.sample_species_findings;
-- drop policy if exists "findings_insert_via_sample"   on public.sample_species_findings;
-- drop policy if exists "findings_select_via_sample"   on public.sample_species_findings;
-- alter table public.detections drop column if exists species_touched;
-- drop table if exists public.sample_species_findings;
