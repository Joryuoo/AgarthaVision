-- 0004 · The model's own output, stored beside the human's ruling on it
--
-- Run via: Supabase dashboard → SQL Editor → paste → Run. By hand, once, per C6.
-- Target: project `agarthavision` (zxojfpfarhhoxjjicphi).
--
-- ── Why ──────────────────────────────────────────────────────────────────────
-- Until now the model's raw output lived only in the Room column `samples.predictions_json`,
-- on the device that captured the frame. The server kept the model's claim only inside the
-- detection row it produced, and a redraw overwrote that row's box — so for every redrawn
-- egg the model's geometry was gone from the corpus, and "did a human draw this box?" could
-- only be answered by comparing floats against a column the server never had
-- (14zcqnthrx8).
--
-- This table is that output, one row per model box. Each prediction-backed detection
-- points at the prediction it rules on; a detection with no prediction is an egg the
-- medtech added. Together with the app no longer writing the model's box onto a
-- BOX_INCORRECT row the medtech did not redraw (14zcqnthrx6), every linked row answers its
-- own provenance:
--
--   prediction_id set, verdict <> BOX_INCORRECT, box set  → the model's box, kept
--   prediction_id set, verdict  = BOX_INCORRECT, box set  → the medtech redrew it
--   prediction_id set, verdict  = BOX_INCORRECT, box null → rejected, not redrawn
--   prediction_id null,                          box set  → the medtech drew it (added egg)
--   prediction_id null,                          box null → counted, never located
--
-- ── Only verified samples ────────────────────────────────────────────────────
-- A prediction references public.samples, and a sample row exists only once it has been
-- verified — unverified frames are never pushed (0001_init.sql, the samples table). The app
-- pushes a sample's predictions in the same sync call as the sample itself, so a prediction
-- for a frame nobody reviewed cannot exist here.
--
-- ── Immutable ────────────────────────────────────────────────────────────────
-- A model's output is a fact about one inference run and never changes. There is no UPDATE
-- and no DELETE policy; the client writes with ON CONFLICT DO NOTHING, so a re-sync of an
-- edited sample is a no-op here rather than an overwrite.

begin;

create table public.predictions (
    -- Client-supplied and derived from (sample_id, ordinal), the same way detection ids are,
    -- so a re-push lands on the same row.
    id          uuid primary key,
    sample_id   uuid not null references public.samples(id) on delete cascade,
    -- Index into the frame's prediction list, in the order the model returned it. The
    -- matching detection's id is derived from the same ordinal.
    ordinal     integer not null check (ordinal >= 0),
    -- The model's label, exactly as the inference server named it.
    class_label text not null,
    confidence  real not null check (confidence between 0 and 1),
    -- Centre-x, centre-y, width, height in source-image pixels — the same space as
    -- detections.bbox_*. Never null: the model always places a box.
    bbox_x      real not null,
    bbox_y      real not null,
    bbox_w      real not null,
    bbox_h      real not null,

    unique (sample_id, ordinal),
    -- Target of the composite FK below, which is what stops a detection pointing at another
    -- sample's prediction.
    unique (id, sample_id)
);

create index predictions_sample_idx on public.predictions(sample_id);

alter table public.detections
    add column prediction_id uuid;

alter table public.detections
    add constraint detections_prediction_fkey
    foreign key (prediction_id, sample_id)
    references public.predictions (id, sample_id);

-- One ruling per prediction.
create unique index detections_prediction_idx
    on public.detections(prediction_id)
    where prediction_id is not null;

comment on column public.detections.prediction_id is
    'The model prediction this row rules on. Null when the medtech added the egg, or on a row written before 0004 whose provenance could not be established.';


-- ── Row Level Security ───────────────────────────────────────────────────────
-- Same shape as detections: ownership is decided through the parent sample.
alter table public.predictions enable row level security;

create policy "predictions_select_via_sample"
on public.predictions for select
using (
    exists (
        select 1 from public.samples s
        where s.id = sample_id
          and ( s.user_id = auth.uid() or public.is_admin(auth.uid()) )
    )
);

create policy "predictions_insert_via_sample"
on public.predictions for insert
with check (
    exists (select 1 from public.samples s where s.id = sample_id and s.user_id = auth.uid())
);

-- Deliberately no UPDATE and no DELETE policy. See "Immutable" above.


-- ── Backfill ─────────────────────────────────────────────────────────────────
-- Existing rows predate this table, so their model output has to be recovered from the
-- detections themselves. What this assumes, stated so nobody has to reverse-engineer it:
--
-- 1. A detection is prediction-backed iff its id is the one VerificationMapper derives for a
--    box ordinal: java.util.UUID.nameUUIDFromBytes("<sample_id>#box#<ordinal>"), which is an
--    MD5 of that string with the version nibble set to 3 and the variant bits to 10.
-- 2. On such a row, class_label and confidence are the model's — the app never overwrites
--    either. class_label is the canonical species name the app stored, which may differ in
--    spelling from the server's raw label; EggSpecies.fromClassLabel reads both.
-- 3. The box is the model's too, **except on BOX_INCORRECT**, where it may be a redraw and
--    nothing on the server can tell which. A sample with any such row is left entirely
--    unlinked, because a partial prediction set would give the app the wrong ordinals.
--    Those rows keep prediction_id null: provenance unknown, not "added by a human".
--
-- When this was written the live table held 23 detections, 8 of them prediction-backed
-- across 4 samples. This inserts 5 predictions on 3 samples; the fourth
-- (f14f3504-0d9f-433e-a0c2-c960a4e5801b) carries a BOX_INCORRECT row and is left unlinked.

create function pg_temp.java_name_uuid(name text) returns uuid
language sql immutable as $$
    select (
        substr(h, 1, 12) || '3' || substr(h, 14, 3)
        || substr('89ab', ((('x' || lpad(substr(h, 17, 1), 8, '0'))::bit(32)::int & 3) + 1), 1)
        || substr(h, 18, 15)
    )::uuid
    from (select md5(name) as h) digest
$$;

create temporary table legacy_model_rows on commit drop as
select d.id as detection_id, d.sample_id, n.ordinal, d.class_label, d.confidence,
       d.bbox_x, d.bbox_y, d.bbox_w, d.bbox_h, d.verdict
from public.detections d
cross join lateral generate_series(0, 199) as n(ordinal)
where d.id = pg_temp.java_name_uuid(d.sample_id::text || '#box#' || n.ordinal);

create temporary table legacy_complete_samples on commit drop as
select sample_id
from legacy_model_rows
group by sample_id
having bool_and(verdict <> 'BOX_INCORRECT' and bbox_x is not null and bbox_y is not null
                and bbox_w is not null and bbox_h is not null)
   -- Contiguous from zero: every ordinal the model produced has its row.
   and max(ordinal) = count(*) - 1;

insert into public.predictions
    (id, sample_id, ordinal, class_label, confidence, bbox_x, bbox_y, bbox_w, bbox_h)
select pg_temp.java_name_uuid(m.sample_id::text || '#prediction#' || m.ordinal),
       m.sample_id, m.ordinal, m.class_label, m.confidence,
       m.bbox_x, m.bbox_y, m.bbox_w, m.bbox_h
from legacy_model_rows m
join legacy_complete_samples c using (sample_id);

update public.detections d
set prediction_id = pg_temp.java_name_uuid(m.sample_id::text || '#prediction#' || m.ordinal)
from legacy_model_rows m
join legacy_complete_samples c using (sample_id)
where d.id = m.detection_id;

commit;
