-- 0016 · A patient report pools only that patient's own sessions
--
-- Run via: Supabase dashboard → SQL Editor → paste → Run. By hand, once, per C6.
-- Target: project `agarthavision` (zxojfpfarhhoxjjicphi).
-- Requires: 0015_patient_reports.sql (this file replaces one of its policies).
--
-- ── Why ──────────────────────────────────────────────────────────────────────
-- 0015's `reports_insert_own_session` lets a patient report in when the writer authored every
-- session in `session_ids`. It never asks whose patient those sessions are. A medtech linked to
-- two patients (0007) could therefore file a report on patient A that pools a session of
-- patient B. When A and B belong to different laboratories, B's egg counts and species land in
-- a report Lab A's org admin can read by `patient_id` (console admin/0004). The phone never
-- builds such a report; a buggy or modified client could, because the database accepted it
-- (14zcqntk68x).
--
-- ── What this does ───────────────────────────────────────────────────────────
-- 1. `reports_insert_own_session` is recreated. A session report is checked exactly as before.
--    A patient report must name at least one session, and every session it names must be
--    authored by the writer AND belong to the report's `patient_id`. An empty or null
--    `session_ids` is refused: a report that pools nothing vouches for nothing, and the phone
--    never sends one (GeneratePatientReportUseCase fails with "No verified samples" first).
-- 2. `sessions.patient_id` can no longer change once written. Without this, (1) can be walked
--    around: move the Lab B session onto patient A, file the report, move it back.
--    `sessions_update_own` (0001) checks only the author, so the move was allowed. The phone
--    never moves a session between patients; its upserts resend the same `patient_id`, which
--    still passes. The trigger binds every role, the table owner included; a session filed
--    under the wrong patient is corrected by hand in the SQL editor with the trigger disabled
--    for that one statement.
--
-- Only inserts and one kind of update are tightened. No existing row is changed (C8).
--
-- ── Before applying: count the rows the old policy let in ────────────────────
-- Read-only. Report the numbers; fix nothing in place (C8).
--
--   select
--       count(*) filter (where coalesce(cardinality(r.session_ids), 0) = 0) as pools_nothing,
--       count(*) filter (where exists (
--           select 1 from unnest(r.session_ids) sid
--           join public.sessions se on se.id = sid
--           where se.patient_id <> r.patient_id)) as pools_another_patients_session,
--       count(*) filter (where exists (
--           select 1 from unnest(r.session_ids) sid
--           join public.sessions se on se.id = sid
--           where se.user_id <> r.user_id)) as pools_a_colleagues_session,
--       count(*) as patient_reports
--   from public.reports r
--   where r.report_type = 'patient';
--
-- ── Rollout ──────────────────────────────────────────────────────────────────
-- Independent of any build: it only refuses writes the phone never makes, so it can go live
-- before or after one.

begin;

drop policy "reports_insert_own_session" on public.reports;

-- Still RESTRICTIVE, so it is ANDed with `reports_insert_own` (0015), which keeps checking the
-- author and, for a patient report, the writer's `patient_users` link to the patient.
create policy "reports_insert_own_session"
on public.reports as restrictive for insert
with check (
    case
        when session_id is not null then
            exists (select 1 from public.sessions se where se.id = session_id and se.user_id = auth.uid())
        else
            coalesce(cardinality(session_ids), 0) > 0
            and not exists (
                select 1 from unnest(session_ids) as sid
                where not exists (
                    select 1 from public.sessions se
                    where se.id = sid
                      and se.user_id = auth.uid()
                      and se.patient_id = reports.patient_id
                )
            )
    end
);

create or replace function public.sessions_patient_is_fixed()
returns trigger
language plpgsql
as $$
begin
    if new.patient_id is distinct from old.patient_id then
        raise exception 'A session cannot move to another patient (session %).', old.id
            using errcode = 'check_violation';
    end if;
    return new;
end;
$$;

create trigger sessions_patient_is_fixed
before update of patient_id on public.sessions
for each row execute function public.sessions_patient_is_fixed();

commit;
