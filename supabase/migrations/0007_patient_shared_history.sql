-- 0007 · A patient's history is readable by every medtech assigned to the patient
--
-- Run via: Supabase dashboard → SQL Editor → paste → Run. By hand, once, per C6.
-- Target: project `agarthavision` (zxojfpfarhhoxjjicphi).
-- Requires: 0001–0006.
--
-- ── Why ──────────────────────────────────────────────────────────────────────
-- The lab owns the patient; `user_id` on every clinical row stays what it always was, the
-- author. Until now every clinical table was readable by its author only (plus admins), so a
-- medtech assigned to a patient through `patient_users` saw the patient and none of what a
-- colleague had recorded for them: not the sessions, not the samples, not the reports. Two
-- medtechs working the same patient each saw half a history (14zcqntjph5).
--
-- This file widens **reads** to "anyone linked to the patient". It does not touch a single
-- write policy: inserting and updating stay author-only, so a colleague's record is read-only
-- on the server as well as on the phone (14zcqntjph6). A medtech sees only patients they are
-- assigned to — `patients_select_linked` is unchanged — and, through the new policies, the full
-- history of exactly those patients.
--
-- ── The decision this reverses ───────────────────────────────────────────────
-- 0003_reports_bucket.sql made report visibility "owner or admin, deliberately not
-- patient-linked", so that a colleague could not read a report. That decision is reversed
-- here, for the report rows and for the `reports` bucket together, because a bucket policy
-- wider than the row policy would hand out a PDF whose record the reader cannot see, and a
-- narrower one would list a report the reader cannot open. The two change as one.
--
-- ── Additive only ────────────────────────────────────────────────────────────
-- No existing policy is dropped or altered. Every new policy is a second permissive SELECT
-- beside the author-only one, and Postgres ORs them. That keeps the author's own read access
-- exactly as it was, which matters: a medtech unassigned from a patient (offboarding reassigns
-- patients) may still hold edited, unpushed work for it, and an upsert that lands on an existing
-- row is checked against the SELECT policies as well as the UPDATE policy. What they lose is the
-- patient itself and their colleagues' records for it.
--
-- ── Helpers ──────────────────────────────────────────────────────────────────
-- Security definer, like is_admin(): a policy on `samples` that reads `sessions` and
-- `patient_users` through their own RLS would evaluate three policy trees per row, and the
-- patient_users policy only shows a user their own links anyway. Each helper answers one
-- question about auth.uid() and nothing else, so granting execute leaks nothing a caller could
-- not already find out by selecting the row.

begin;

create or replace function public.is_linked_to_patient(p_patient_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.patient_users pu
        where pu.patient_id = p_patient_id
          and pu.user_id = auth.uid()
    );
$$;

-- Author, linked to the session's patient, or admin.
create or replace function public.can_read_session(p_session_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select public.is_admin(auth.uid())
        or exists (
            select 1
            from public.sessions se
            where se.id = p_session_id
              and (se.user_id = auth.uid() or public.is_linked_to_patient(se.patient_id))
        );
$$;

-- Author of the sample, or anyone who can read its session.
create or replace function public.can_read_sample(p_sample_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.samples sa
        where sa.id = p_sample_id
          and (sa.user_id = auth.uid() or public.can_read_session(sa.session_id))
    );
$$;

-- A `samples` bucket object is readable when the sample row that points at it is. The row
-- stores the object key verbatim in storage_path (`{user_id}/{sample_id}.jpg`), so this matches
-- on the key rather than re-deriving it from the name.
create or replace function public.can_read_sample_object(p_name text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.samples sa
        where sa.storage_path = p_name
          and public.can_read_sample(sa.id)
    );
$$;

-- A `reports` bucket object is `{user_id}/{report_id}.{pdf|csv}` (0003). The report id comes from
-- the file name and the author from the folder, and both must match the row, so a name cannot
-- borrow another report's visibility. The uuid is checked before it is cast: a malformed name
-- is simply not readable, rather than an error from inside a storage policy.
create or replace function public.can_read_report_object(p_name text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.reports r
        where r.id = (
                case
                    when split_part(storage.filename(p_name), '.', 1)
                         ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
                    then split_part(storage.filename(p_name), '.', 1)::uuid
                end
            )
          and r.user_id::text = (storage.foldername(p_name))[1]
          and public.can_read_session(r.session_id)
    );
$$;

revoke execute on function public.is_linked_to_patient(uuid)   from public;
revoke execute on function public.can_read_session(uuid)       from public;
revoke execute on function public.can_read_sample(uuid)        from public;
revoke execute on function public.can_read_sample_object(text) from public;
revoke execute on function public.can_read_report_object(text) from public;

grant execute on function public.is_linked_to_patient(uuid)   to authenticated;
grant execute on function public.can_read_session(uuid)       to authenticated;
grant execute on function public.can_read_sample(uuid)        to authenticated;
grant execute on function public.can_read_sample_object(text) to authenticated;
grant execute on function public.can_read_report_object(text) to authenticated;

-- The object-key lookup above runs on every read of a samples-bucket object.
create index if not exists samples_storage_path_idx on public.samples (storage_path);


-- ── Table read policies ──────────────────────────────────────────────────────
-- Each is added beside the author-only SELECT policy its table already has, and nothing from
-- 0001 or 0004 is dropped. Permissive policies are ORed, so a row is readable when either
-- holds: the reader wrote it (the old policy), or the reader is assigned to its patient (these).

create policy "sessions_select_via_patient"
on public.sessions for select
using ( public.is_linked_to_patient(patient_id) );

create policy "samples_select_via_patient"
on public.samples for select
using ( public.can_read_session(session_id) );

create policy "detections_select_via_patient"
on public.detections for select
using ( public.can_read_sample(sample_id) );

create policy "findings_select_via_patient"
on public.sample_species_findings for select
using ( public.can_read_sample(sample_id) );

create policy "predictions_select_via_patient"
on public.predictions for select
using ( public.can_read_sample(sample_id) );

create policy "reports_select_via_patient"
on public.reports for select
using ( public.can_read_session(session_id) );


-- ── Storage ──────────────────────────────────────────────────────────────────
-- Added beside the own-folder and admin SELECT policies from 0001 and 0003, which stay.
-- As above, these only ever widen reads. Neither bucket gains an
-- INSERT, UPDATE or DELETE policy: C8, and a colleague's file is read-only.

create policy "samples: select via patient"
on storage.objects for select to authenticated
using ( bucket_id = 'samples' and public.can_read_sample_object(name) );

create policy "reports: select via patient"
on storage.objects for select to authenticated
using ( bucket_id = 'reports' and public.can_read_report_object(name) );

commit;
