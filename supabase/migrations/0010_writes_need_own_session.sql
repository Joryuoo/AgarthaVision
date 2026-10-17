-- 0010 · A sample or report can only be written into the writer's own session
--
-- Run via: Supabase dashboard → SQL Editor → paste → Run. By hand, once, per C6.
-- Target: project `agarthavision` (zxojfpfarhhoxjjicphi).
-- Requires: 0007_patient_shared_history.sql.
--
-- ── Why ──────────────────────────────────────────────────────────────────────
-- `samples_insert_own`, `samples_update_own` and `reports_insert_own` (0001) check only that
-- the row's `user_id` is the caller. None of them checks whose session the row hangs off. That
-- was harmless while a medtech could read only their own sessions, because they never learned
-- a colleague's session id. 0007 hands those ids out with the shared patient history, so a
-- buggy or outdated build could write its own sample or report into a colleague's session, and
-- every medtech assigned to the patient would then see it there. The phone already refuses this
-- (isColleagueRecord, 14zcqntjph6); this makes the server refuse it too.
--
-- ── Additive only ────────────────────────────────────────────────────────────
-- No existing policy is dropped or altered. Each new policy is RESTRICTIVE, so Postgres ANDs it
-- with the permissive policy beside it: a write must pass both the old author check and this
-- session check. Before this file was written, no sample or report on the server belonged to a
-- session another user authored, so no existing row is affected and a re-sync of any existing
-- row still passes.
--
-- The check reads `sessions` through its own RLS. That is enough: the session in question is
-- the caller's own, and `sessions_select_own` always shows a user their own rows.

begin;

create policy "samples_insert_own_session"
on public.samples as restrictive for insert
with check (
    exists (select 1 from public.sessions se where se.id = session_id and se.user_id = auth.uid())
);

-- An upsert that conflicts runs an UPDATE, and an UPDATE can move a sample to another session.
create policy "samples_update_own_session"
on public.samples as restrictive for update
using (
    exists (select 1 from public.sessions se where se.id = session_id and se.user_id = auth.uid())
)
with check (
    exists (select 1 from public.sessions se where se.id = session_id and se.user_id = auth.uid())
);

create policy "reports_insert_own_session"
on public.reports as restrictive for insert
with check (
    exists (select 1 from public.sessions se where se.id = session_id and se.user_id = auth.uid())
);

commit;
