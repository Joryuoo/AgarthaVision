-- 0019 · Require non-empty full_name for every profile
--
-- Run via: Supabase dashboard → SQL Editor → paste → Run. By hand, once, per C6.
-- Target: project `agarthavision` (zxojfpfarhhoxjjicphi).
-- Requires: 0014_super_admins.sql, 0018_writes_need_active_lab_member.sql
--
-- ── Why ──────────────────────────────────────────────────────────────────────
-- `profiles.full_name` was nullable since 0001, and the `handle_new_user()` trigger
-- inserted only `id` and `account_id` without setting `full_name`. As a result,
-- medtech profiles could be created with no name (e.g., via open sign-up or admin
-- user creation without metadata).
--
-- Any screen or report displaying the medtech's name (colleague attribution on
-- shared records, session reports, patient reports) then had to fall back to
-- "another medtech", email, or raw UUIDs.
--
-- ── What this does ───────────────────────────────────────────────────────────
-- 1. Backfills any existing profiles where `full_name` is null or blank using
--    `auth.users.raw_user_meta_data->>'full_name'`, falling back to a designated
--    placeholder 'Pending Profile Setup' (never leaking email handles). Profiles needing
--    real names can be found with `WHERE full_name = 'Pending Profile Setup'`.
-- 2. Enforces `NOT NULL` on `profiles.full_name` and adds a check constraint
--    ensuring `trim(full_name) <> ''`.
-- 3. Updates `handle_new_user()` to extract `full_name` from `raw_user_meta_data->>'full_name'`
--    or default to 'Pending Profile Setup' without failing, allowing the profile to exist
--    briefly during onboarding invitation acceptance (`auth.admin.createUser`) before the accept
--    RPC (`console_accept_invitation`) updates the profile with the user's entered full_name.
-- 4. Secures execute privileges on `handle_new_user()` consistent with 0017.

begin;

-- 1. Backfill existing profiles
update public.profiles p
set full_name = coalesce(
    nullif(trim(u.raw_user_meta_data->>'full_name'), ''),
    'Pending Profile Setup'
)
from auth.users u
where p.id = u.id
  and (p.full_name is null or trim(p.full_name) = '');

-- Fallback for any orphaned profiles without matching auth.users (e.g. deleted logins per 0011)
update public.profiles
set full_name = 'Pending Profile Setup'
where full_name is null or trim(full_name) = '';

-- 2. Enforce NOT NULL and non-blank check constraint
alter table public.profiles
    alter column full_name set not null,
    add constraint profiles_full_name_not_blank check (trim(full_name) <> '');

-- 3. Update handle_new_user trigger function
-- Inserts a profile for new auth.users rows without raising exceptions so console invitation
-- acceptance (auth.admin.createUser) and dashboard additions do not abort. If full_name is
-- absent in raw_user_meta_data, defaults to 'Pending Profile Setup' so the row satisfies NOT NULL
-- until the accept RPC (console_accept_invitation) updates it with the entered full_name.
create or replace function public.handle_new_user()
returns trigger as $$
declare
    v_full_name text;
begin
    v_full_name := nullif(trim(new.raw_user_meta_data->>'full_name'), '');

    insert into public.profiles (id, account_id, full_name)
    values (new.id, new.id::text, coalesce(v_full_name, 'Pending Profile Setup'));

    return new;
end;
$$ language plpgsql security definer set search_path = public;

revoke execute on function public.handle_new_user() from public, anon, authenticated;

comment on column public.profiles.full_name is
    'Required medtech display name (0019_require_profile_full_name.sql). Must be non-blank.';

commit;
