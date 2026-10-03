-- 0008 · A medtech can read the name of a colleague whose records they can read
--
-- Run via: Supabase dashboard → SQL Editor → paste → Run. By hand, once, per C6.
-- Target: project `agarthavision` (zxojfpfarhhoxjjicphi).
-- Requires: 0007_patient_shared_history.sql.
--
-- ── Why ──────────────────────────────────────────────────────────────────────
-- 0007 lets a medtech read a colleague's sessions, samples and reports on a patient they are
-- both assigned to. The phone shows those records read-only and has to say whose they are, or
-- the medtech cannot tell why the edit buttons are missing (14zcqntjph6). `profiles_select_own`
-- lets a user read only their own profile, so today the phone has an author id and no name.
--
-- ── What is readable ─────────────────────────────────────────────────────────
-- A colleague's profile row, and only when the reader can already see something that colleague
-- authored: a session or a report on a patient the reader is assigned to. A medtech on another
-- patient, or in another laboratory, stays invisible. The row holds `id`, `full_name`, `role`
-- and `created_at`; the phone selects `id` and `full_name` only.
--
-- Additive, like 0007: a second permissive SELECT policy beside `profiles_select_own`, which is
-- unchanged. No write policy is added.
--
-- ── Recursion ────────────────────────────────────────────────────────────────
-- The check reads `sessions` and `reports`, never `profiles`, so it cannot recurse into this
-- policy the way the inline role subquery that legacy-dev/0004 fixed did. It is security definer
-- for the same reason 0007's helpers are.

begin;

create or replace function public.shares_patient_history_with(p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
            select 1
            from public.sessions se
            where se.user_id = p_user_id
              and public.is_linked_to_patient(se.patient_id)
        )
        or exists (
            select 1
            from public.reports r
            join public.sessions se on se.id = r.session_id
            where r.user_id = p_user_id
              and public.is_linked_to_patient(se.patient_id)
        );
$$;

revoke execute on function public.shares_patient_history_with(uuid) from public;
grant execute on function public.shares_patient_history_with(uuid) to authenticated;

create policy "profiles_select_colleague"
on public.profiles for select
using ( public.shares_patient_history_with(id) );

commit;
