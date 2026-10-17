-- 0013 · Super admins read patients de-identified at the database, not only in the console
--
-- Run via: Supabase dashboard → SQL Editor → paste → Run. By hand, once, per C6.
-- Target: project `agarthavision` (zxojfpfarhhoxjjicphi).
-- Requires: 0012_deidentified_reads.sql, **and an Admin Console deployed with 14zcqntjvky**.
--
-- ── Why ──────────────────────────────────────────────────────────────────────
-- Step 3 of the rollout in 0012. Until now `is_admin()` granted a super admin every row of
-- `patients` (names, sex, birthdates), every session label, every sample's free-text note and
-- every report file. The console never showed them (D19, constraint #14 there), but anyone
-- holding a super admin login and calling the API directly could read them (14zcqntjvjw).
--
-- After this file a super admin reads patients, sessions and samples only through the
-- de-identified views from 0012, and no longer reads report files. Everything else they read
-- today is unchanged: patient links, detections, findings, predictions, report rows (without the
-- file), profiles, sample frames, and barangay_prevalence().
--
-- ── Who is affected ──────────────────────────────────────────────────────────
-- * **Medtechs: nobody.** Every policy below keeps its non-admin branch exactly as it was.
-- * **Organization admins: nobody.** They read through the console's own policies
--   (admin/0002_patient_scoping.sql), which never used is_admin() and are not touched.
-- * **Super admins:** as above. No app build signs a super admin in, so the phone is unaffected.
--
-- ── Not additive, deliberately ───────────────────────────────────────────────
-- 0007, 0008 and 0010 only ever added policies, because they widened or restricted access for
-- someone who keeps the rest. This file removes access, and a permissive policy can only be
-- narrowed by changing it: Postgres ORs permissive policies, so adding one cannot take a row
-- away. Each `alter policy` below restates the policy's own expression minus its is_admin()
-- branch, nothing else.
--
-- ── Break-glass, later ───────────────────────────────────────────────────────
-- A time-limited support grant would be one more permissive SELECT policy on these tables
-- (`patients`, `sessions`, `samples`) reading a grants table. Nothing here rules that out.

begin;

-- ── patients ─────────────────────────────────────────────────────────────────
alter policy "patients_select_linked"
on public.patients
using (
    exists (
        select 1 from public.patient_users pu
        where pu.patient_id = id and pu.user_id = auth.uid()
    )
);

-- ── sessions: the label carries the patient's initials, sex and age ──────────
alter policy "sessions_select_own"
on public.sessions
using ( auth.uid() = user_id );

-- ── samples: user_note is free text ──────────────────────────────────────────
alter policy "samples_select_own"
on public.samples
using ( auth.uid() = user_id );

-- 0007's helper answered "yes" for any super admin, which carried the old access into
-- samples_select_via_patient, reports_select_via_patient and both buckets' via-patient
-- policies. A super admin's report rows come from reports_select_own, which keeps its branch.
create or replace function public.can_read_session(p_session_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.sessions se
        where se.id = p_session_id
          and (se.user_id = auth.uid() or public.is_linked_to_patient(se.patient_id))
    );
$$;

-- ── Rows under a sample: unchanged for super admins ──────────────────────────
-- These checked `is_admin()` *inside* a subquery on `samples`, which runs through samples' own
-- RLS. With the super admin branch gone from samples, that subquery would find nothing and a
-- super admin would lose detections, findings and predictions too. They carry no identity, so
-- the admin branch moves outside the subquery and keeps exactly today's access.
alter policy "detections_select_via_sample"
on public.detections
using (
    public.is_admin(auth.uid())
    or exists (
        select 1 from public.samples s
        where s.id = sample_id and s.user_id = auth.uid()
    )
);

alter policy "findings_select_via_sample"
on public.sample_species_findings
using (
    public.is_admin(auth.uid())
    or exists (
        select 1 from public.samples s
        where s.id = sample_id and s.user_id = auth.uid()
    )
);

alter policy "predictions_select_via_sample"
on public.predictions
using (
    public.is_admin(auth.uid())
    or exists (
        select 1 from public.samples s
        where s.id = sample_id and s.user_id = auth.uid()
    )
);

-- ── Report files print the patient's name ────────────────────────────────────
-- The rows stay readable (counts, species, dates). The file does not. Nothing is deleted (C8):
-- this removes a read policy, not an object.
drop policy "reports: admin read all" on storage.objects;

-- ── session_label_duplicates() returned labels ───────────────────────────────
-- Same diagnostic, same guard, minus the label: the session ids identify each duplicate group
-- and are enough to find and fix it. Nothing in either app calls it.
drop function public.session_label_duplicates();

create function public.session_label_duplicates()
returns table (patient_id uuid, sessions integer, session_ids uuid[])
language plpgsql
stable
security definer
set search_path = public
as $$
begin
    if not public.is_admin(auth.uid()) then
        raise exception 'session_label_duplicates() requires the admin role';
    end if;

    return query
    select s.patient_id, count(*)::integer, array_agg(s.id order by s.started_at, s.id)
    from public.sessions s
    where s.label is not null and btrim(s.label) <> ''
    group by s.patient_id, s.label
    having count(*) > 1;
end;
$$;

revoke execute on function public.session_label_duplicates() from public;
grant execute on function public.session_label_duplicates() to authenticated;

commit;
