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
--    `auth.users.raw_user_meta_data->>'full_name'`, falling back to the email
--    username prefix, and finally 'Medtech' so no existing medtech is locked out.
-- 2. Enforces `NOT NULL` on `profiles.full_name` and adds a check constraint
--    ensuring `trim(full_name) <> ''`.
-- 3. Updates `handle_new_user()` to extract `full_name` from the new user's metadata
--    (`raw_user_meta_data->>'full_name'` or `'name'`), and raises an explicit error
--    ('A full name is required for every medtech profile.') if missing or blank.
-- 4. Secures execute privileges on `handle_new_user()` consistent with 0017.

begin;

-- 1. Backfill existing profiles
update public.profiles p
set full_name = coalesce(
    nullif(trim(u.raw_user_meta_data->>'full_name'), ''),
    nullif(trim(u.raw_user_meta_data->>'name'), ''),
    nullif(trim(split_part(u.email, '@', 1)), ''),
    'Medtech'
)
from auth.users u
where p.id = u.id
  and (p.full_name is null or trim(p.full_name) = '');

-- Fallback for any orphaned profiles without matching auth.users (e.g. deleted logins per 0011)
update public.profiles
set full_name = 'Medtech'
where full_name is null or trim(full_name) = '';

-- 2. Enforce NOT NULL and non-blank check constraint
alter table public.profiles
    alter column full_name set not null,
    add constraint profiles_full_name_not_blank check (trim(full_name) <> '');

-- 3. Update handle_new_user trigger function
create or replace function public.handle_new_user()
returns trigger as $$
declare
    v_full_name text;
begin
    v_full_name := trim(coalesce(
        new.raw_user_meta_data->>'full_name',
        new.raw_user_meta_data->>'name'
    ));

    if v_full_name is null or v_full_name = '' then
        raise exception 'A full name is required for every medtech profile.'
            using errcode = '23502'; -- not_null_violation
    end if;

    insert into public.profiles (id, account_id, full_name)
    values (new.id, new.id::text, v_full_name);

    return new;
end;
$$ language plpgsql security definer set search_path = public;

revoke execute on function public.handle_new_user() from public, anon, authenticated;

comment on column public.profiles.full_name is
    'Required medtech display name (0019_require_profile_full_name.sql). Must be non-blank.';

commit;
