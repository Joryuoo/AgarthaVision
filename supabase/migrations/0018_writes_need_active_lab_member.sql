-- 0018 · Field data is written only by an active member of an active laboratory
--
-- Run via: Supabase dashboard → SQL Editor → paste → Run. By hand, once, per C6.
-- Target: project `agarthavision` (zxojfpfarhhoxjjicphi).
-- Requires: 0017_advisor_hardening.sql, and the console's admin/0001_organizations.sql
-- (`organizations`, `organization_members`), which live already has.
--
-- ── Why ──────────────────────────────────────────────────────────────────────
-- Every write policy since 0001 asks only whether the row is the caller's own
-- (`patients_insert_own`: `auth.uid() = created_by`, and the same shape for sessions, samples,
-- detections, predictions, findings, reports and both storage buckets). None asks whether the
-- caller still belongs to a laboratory, so three kinds of account could keep adding field data
-- (14zcqntnqpa):
-- * a medtech an org admin deactivated, until their session ends;
-- * any account made through the open sign-up, which belongs to no laboratory;
-- * every member of a laboratory a super admin deactivated.
-- That data reaches the super admin dashboard, the prevalence map, the research export and the
-- training snapshots: it skews prevalence figures and can poison the model's training data.
--
-- Also, 35 of the app's policies are written `TO public`, which includes `anon`. They relied on
-- `auth.uid()` being null to shut a signed-out visitor out.
--
-- ── What this does ───────────────────────────────────────────────────────────
-- 1. `require_active_lab_member()` is true when the caller is an active member, of any role, of
--    an active laboratory: the rule of the console's `console_member_org()`. Otherwise it
--    raises 42501 with `not_active_lab_member` in the message, so the phone can tell "your
--    account is no longer active" apart from any other refused write. It lives in
--    `app_private` (0017), which the API does not expose, so it is no RPC endpoint.
-- 2. A RESTRICTIVE policy calls it on every insert, update and delete the app's policies allow
--    on field data, and on inserts and updates of the `samples` and `reports` buckets. Being
--    restrictive, it is ANDed with the existing policies, which keep checking ownership exactly
--    as before.
-- 3. The 35 `TO public` policies are moved to `TO authenticated`. Their rules are unchanged.
--
-- Reads are unchanged: a deactivated medtech still reads their own records until signed out,
-- as the console's people page promises. No existing row is changed (C8).
--
-- Who it reaches: every write the phone makes is now refused for an account that is not an
-- active member of an active laboratory, including its queued offline work. The console's own
-- writes run inside security-definer functions as the table owner and are unaffected.
--
-- ── Before applying: find active writers who would be refused ────────────────
-- Read-only. Anyone listed has written field data in the last 30 days but has no active
-- membership of an active laboratory. Each needs an org admin to add or reactivate them first,
-- or their next sync is refused.
--
--   select p.id, p.full_name, m.status as member_status, o.status as organization_status,
--          max(s.started_at) as last_session
--   from public.sessions s
--   join public.profiles p on p.id = s.user_id
--   left join public.organization_members m on m.user_id = p.id
--   left join public.organizations o on o.id = m.organization_id
--   where s.started_at > now() - interval '30 days'
--     and not (coalesce(m.status, '') = 'active' and coalesce(o.status, '') = 'active')
--   group by p.id, p.full_name, m.status, o.status
--   order by last_session desc;
--
-- ── Rollout ──────────────────────────────────────────────────────────────────
-- Applies on its own; the phone's clearer message for a refused write ships in the app build
-- that names this file. Until that build is installed, a refused sync shows the generic error.

begin;

-- ── 1. The rule ──────────────────────────────────────────────────────────────
create or replace function app_private.require_active_lab_member()
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
begin
    if exists (
        select 1
        from public.organization_members m
        join public.organizations o on o.id = m.organization_id
        where m.user_id = auth.uid()
          and m.status = 'active'
          and o.status = 'active'
    ) then
        return true;
    end if;
    raise exception 'not_active_lab_member: this account is not an active member of an active laboratory'
        using errcode = 'insufficient_privilege';
end;
$$;

revoke execute on function app_private.require_active_lab_member() from public, anon;
grant execute on function app_private.require_active_lab_member() to authenticated;


-- ── 2. Every field-data write needs it ───────────────────────────────────────
-- `(select ...)` runs the check once per statement, not once per row.
create policy "patients_writer_is_active_member_insert"
on public.patients as restrictive for insert to authenticated
with check ((select app_private.require_active_lab_member()));

create policy "patients_writer_is_active_member_update"
on public.patients as restrictive for update to authenticated
using ((select app_private.require_active_lab_member()));

create policy "sessions_writer_is_active_member_insert"
on public.sessions as restrictive for insert to authenticated
with check ((select app_private.require_active_lab_member()));

create policy "sessions_writer_is_active_member_update"
on public.sessions as restrictive for update to authenticated
using ((select app_private.require_active_lab_member()));

create policy "samples_writer_is_active_member_insert"
on public.samples as restrictive for insert to authenticated
with check ((select app_private.require_active_lab_member()));

create policy "samples_writer_is_active_member_update"
on public.samples as restrictive for update to authenticated
using ((select app_private.require_active_lab_member()));

create policy "detections_writer_is_active_member_insert"
on public.detections as restrictive for insert to authenticated
with check ((select app_private.require_active_lab_member()));

create policy "detections_writer_is_active_member_update"
on public.detections as restrictive for update to authenticated
using ((select app_private.require_active_lab_member()));

create policy "predictions_writer_is_active_member_insert"
on public.predictions as restrictive for insert to authenticated
with check ((select app_private.require_active_lab_member()));

create policy "findings_writer_is_active_member_insert"
on public.sample_species_findings as restrictive for insert to authenticated
with check ((select app_private.require_active_lab_member()));

create policy "findings_writer_is_active_member_update"
on public.sample_species_findings as restrictive for update to authenticated
using ((select app_private.require_active_lab_member()));

create policy "findings_writer_is_active_member_delete"
on public.sample_species_findings as restrictive for delete to authenticated
using ((select app_private.require_active_lab_member()));

create policy "reports_writer_is_active_member_insert"
on public.reports as restrictive for insert to authenticated
with check ((select app_private.require_active_lab_member()));

-- Storage holds other buckets too: only the app's two are checked, row by row, so an upload
-- elsewhere never calls the rule.
create policy "samples and reports: writer is active member (insert)"
on storage.objects as restrictive for insert to authenticated
with check (
    case when bucket_id in ('samples', 'reports') then app_private.require_active_lab_member() else true end
);

create policy "samples and reports: writer is active member (update)"
on storage.objects as restrictive for update to authenticated
using (
    case when bucket_id in ('samples', 'reports') then app_private.require_active_lab_member() else true end
);


-- ── 3. Signed-in users only ──────────────────────────────────────────────────
alter policy "patients_insert_own" on public.patients to authenticated;
alter policy "patients_select_linked" on public.patients to authenticated;
alter policy "patients_update_linked" on public.patients to authenticated;
alter policy "patient_users_select_own" on public.patient_users to authenticated;
alter policy "profiles_select_colleague" on public.profiles to authenticated;
alter policy "profiles_select_own" on public.profiles to authenticated;
alter policy "sessions_insert_own" on public.sessions to authenticated;
alter policy "sessions_select_own" on public.sessions to authenticated;
alter policy "sessions_select_via_patient" on public.sessions to authenticated;
alter policy "sessions_update_own" on public.sessions to authenticated;
alter policy "samples_insert_own" on public.samples to authenticated;
alter policy "samples_insert_own_session" on public.samples to authenticated;
alter policy "samples_select_own" on public.samples to authenticated;
alter policy "samples_select_via_patient" on public.samples to authenticated;
alter policy "samples_update_own" on public.samples to authenticated;
alter policy "samples_update_own_session" on public.samples to authenticated;
alter policy "detections_insert_via_sample" on public.detections to authenticated;
alter policy "detections_select_via_patient" on public.detections to authenticated;
alter policy "detections_select_via_sample" on public.detections to authenticated;
alter policy "detections_update_via_sample" on public.detections to authenticated;
alter policy "predictions_insert_via_sample" on public.predictions to authenticated;
alter policy "predictions_select_via_patient" on public.predictions to authenticated;
alter policy "predictions_select_via_sample" on public.predictions to authenticated;
alter policy "findings_delete_via_sample" on public.sample_species_findings to authenticated;
alter policy "findings_insert_via_sample" on public.sample_species_findings to authenticated;
alter policy "findings_select_via_patient" on public.sample_species_findings to authenticated;
alter policy "findings_select_via_sample" on public.sample_species_findings to authenticated;
alter policy "findings_update_via_sample" on public.sample_species_findings to authenticated;
alter policy "reports_insert_own" on public.reports to authenticated;
alter policy "reports_insert_own_session" on public.reports to authenticated;
alter policy "reports_select_own" on public.reports to authenticated;
alter policy "reports_select_via_patient" on public.reports to authenticated;
alter policy "reports: insert own folder" on storage.objects to authenticated;
alter policy "reports: select own folder" on storage.objects to authenticated;
alter policy "reports: update own folder" on storage.objects to authenticated;

commit;
