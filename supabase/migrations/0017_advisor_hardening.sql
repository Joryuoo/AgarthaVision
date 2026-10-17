-- 0017 · Signed-out visitors reach no app table or function; is_admin() answers only about yourself
--
-- Run via: Supabase dashboard → SQL Editor → paste → Run. By hand, once, per C6.
-- Target: project `agarthavision` (zxojfpfarhhoxjjicphi).
-- Requires: 0001_init.sql through 0016_patient_report_own_patient_sessions.sql.
--
-- ── Why ──────────────────────────────────────────────────────────────────────
-- Supabase's security advisor on the live project, 2026-10-15 (14zcqntnqxe):
--
-- 1. `sessions_patient_is_fixed()` (0016) has a mutable search_path.
-- 2. Every app security-definer function is executable by `anon`, so each one is a
--    `/rest/v1/rpc/...` endpoint a signed-out visitor can call. `is_admin(uuid)` lets anyone,
--    signed in or not, ask whether any user ID belongs to a super admin.
-- 3. `anon` still holds Supabase's default full rights on every app table. RLS refuses it
--    today (a signed-out read of `patients` returns []), but if RLS were ever turned off on
--    one table, that table would be open to the internet.
--
-- ── What this does ───────────────────────────────────────────────────────────
-- 1. `sessions_patient_is_fixed()` gets a fixed, empty search_path. Its body names no table.
-- 2. The trigger functions lose EXECUTE for everyone but the owner. A trigger fires whatever
--    the caller's EXECUTE rights are, so signup, account deletion, patient registration and
--    session labelling are unchanged.
-- 3. The policy helpers and the two admin RPCs lose EXECUTE for `anon` and PUBLIC and keep it
--    for `authenticated`: every policy that calls them runs as the signed-in user.
-- 4. `anon` loses every right on the app's tables. Nothing signed out reads or writes them:
--    the app signs in before its first query, and the console's one signed-out call
--    (`console_invitation_by_token`) is a security-definer function.
-- 5. `is_admin(uuid)` answers a signed-in client only about themselves. The check moves to
--    `app_private.is_admin(uuid)`, in a schema the API does not expose. `public.is_admin` keeps
--    its signature and becomes security invoker, so it can tell who is asking:
--    * called from a policy or by a client (`authenticated`), it answers for `auth.uid()` and
--      is false for anyone else. Every policy and view passes `auth.uid()`, and the console's
--      fallback gate (`src/adapters/supabase/auth.ts`) passes the signed-in user's own ID;
--    * called inside a security-definer function (the console's RPCs, which ask about an
--      invitee or an actor), it runs as the function's owner and answers for anyone, as before.
--
-- Nothing reads differently for a signed-in user. No row is changed (C8).
--
-- ── Rollout ──────────────────────────────────────────────────────────────────
-- Independent of any build. Apply after the console's admin/ migrations, as live already has
-- them; the console's db tests run against this file.

begin;

-- ── 1. A fixed search_path ───────────────────────────────────────────────────
alter function public.sessions_patient_is_fixed() set search_path = '';


-- ── 2. Trigger functions: nobody calls them directly ─────────────────────────
revoke execute on function public.handle_new_user() from public, anon, authenticated;
revoke execute on function public.handle_deleted_user() from public, anon, authenticated;
revoke execute on function public.link_patient_creator() from public, anon, authenticated;
revoke execute on function public.dedupe_session_label() from public, anon, authenticated;
revoke execute on function public.sessions_patient_is_fixed() from public, anon, authenticated;


-- ── 3. Policy helpers and admin RPCs: signed-in users only ───────────────────
revoke execute on function public.can_read_report_object(text) from public, anon;
revoke execute on function public.can_read_sample(uuid) from public, anon;
revoke execute on function public.can_read_sample_object(text) from public, anon;
revoke execute on function public.can_read_session(uuid) from public, anon;
revoke execute on function public.is_linked_to_patient(uuid) from public, anon;
revoke execute on function public.shares_patient_history_with(uuid) from public, anon;
revoke execute on function public.barangay_prevalence() from public, anon;
revoke execute on function public.session_label_duplicates() from public, anon;

grant execute on function public.can_read_report_object(text) to authenticated;
grant execute on function public.can_read_sample(uuid) to authenticated;
grant execute on function public.can_read_sample_object(text) to authenticated;
grant execute on function public.can_read_session(uuid) to authenticated;
grant execute on function public.is_linked_to_patient(uuid) to authenticated;
grant execute on function public.shares_patient_history_with(uuid) to authenticated;
grant execute on function public.barangay_prevalence() to authenticated;
grant execute on function public.session_label_duplicates() to authenticated;


-- ── 4. App tables: nothing for signed-out visitors ───────────────────────────
revoke all on table
    public.patients,
    public.patient_users,
    public.sessions,
    public.samples,
    public.detections,
    public.predictions,
    public.sample_species_findings,
    public.reports,
    public.profiles
from anon;


-- ── 5. is_admin(): about yourself only ───────────────────────────────────────
-- Not in the API's exposed schemas, so no client can call anything here by URL.
create schema if not exists app_private;
revoke all on schema app_private from public, anon;
grant usage on schema app_private to authenticated, service_role;

-- 0014's check, unchanged, under a name no client can reach.
create or replace function app_private.is_admin(p_user uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.super_admins sa
        where sa.user_id = p_user
          and sa.revoked_at is null
    );
$$;

revoke execute on function app_private.is_admin(uuid) from public, anon;
grant execute on function app_private.is_admin(uuid) to authenticated, service_role;

-- Security invoker, so `current_user` is whoever is asking: a client or a policy runs as
-- `authenticated`, a security-definer caller runs as its owner. Only a client is limited.
create or replace function public.is_admin(user_id uuid)
returns boolean
language sql
stable
security invoker
set search_path = ''
as $$
    select case
        when current_user in ('anon', 'authenticated')
            and is_admin.user_id is distinct from (select auth.uid())
        then false
        else app_private.is_admin(is_admin.user_id)
    end;
$$;

revoke execute on function public.is_admin(uuid) from public, anon;
grant execute on function public.is_admin(uuid) to authenticated, service_role;

commit;
