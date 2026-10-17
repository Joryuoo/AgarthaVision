-- 0014 · Super admins are recorded in their own table, not in profiles.role
--
-- Run via: Supabase dashboard → SQL Editor → paste → Run. By hand, once, per C6.
-- Target: project `agarthavision` (zxojfpfarhhoxjjicphi).
-- Requires: 0001_init.sql and 0011_profile_outlives_login.sql. Independent of 0012–0013;
-- numbered after them only because they are already taken on another branch.
--
-- ── Why ──────────────────────────────────────────────────────────────────────
-- A super admin was `profiles.role = 'admin'` (14zcqntjwje, Admin decision D22). That column
-- is a weak home for it:
--   * `profiles` is the permanent author record. Who authored a sample and who may run the
--     platform are separate facts that change at different times.
--   * There is no UPDATE policy on `profiles` today. The obvious one for "let a medtech edit
--     their name", `using (auth.uid() = id)`, would let anyone make themselves a super admin.
--   * A text column records no grant: not who granted it, when, or when it was revoked.
--   * Colleagues (0008) and organization admins (console admin/0002) can read profile rows,
--     so they can see who the super admins are.
--
-- ── What this does ───────────────────────────────────────────────────────────
-- `super_admins` holds one row per grant. A user is a super admin while they hold a row with
-- `revoked_at is null`. `is_admin()` reads it, with the same signature, so none of its callers
-- change: the app's policies, `barangay_prevalence()`, 0012's views, and the console's
-- policies and RPCs. Every user with `role = 'admin'` before this file holds one active row
-- after it, and nobody else does, so `is_admin()` answers the same for everyone.
--
-- `profiles.role` is retired, not dropped: it stays in place and grants nothing. Dropping it
-- is a separate, later decision.
--
-- ── Who may touch the table ──────────────────────────────────────────────────
-- Nobody signed in. RLS is on with no policy, and `anon` and `authenticated` hold no privilege
-- on it, so no client can read, insert, update or delete a row. Only `is_admin()` reads it,
-- as security definer. Grants are made by hand in the SQL editor, or later through security
-- definer functions the console adds.
--
-- Revoking is a tombstone (C8): `revoked_at` is set and the row stays. A trigger refuses a
-- delete, a truncate, and any update other than revoking an active grant, for the table owner
-- too.
--
-- ── Rollout ──────────────────────────────────────────────────────────────────
-- Same shape as 0012/0013. (1) This file. (2) The console switches its own gate from
-- `profiles.role` to `is_admin()` (14zcqntjwjf). Until (2) ships the console still reads
-- `role`, so a super admin granted by hand in between needs both: an active row here, which
-- the database honours, and `role = 'admin'`, which the console's gate reads.

begin;

create table public.super_admins (
    id         uuid primary key default uuid_generate_v4(),
    -- Profiles, never the provider's login table: a profile outlives its login (0011), and
    -- the table stays provider-neutral (D7).
    user_id    uuid not null references public.profiles(id),
    granted_by uuid references public.profiles(id),
    granted_at timestamptz not null default now(),
    revoked_by uuid references public.profiles(id),
    revoked_at timestamptz,
    constraint super_admins_revoked_by_needs_revoked_at
        check (revoked_by is null or revoked_at is not null),
    constraint super_admins_revoked_after_granted
        check (revoked_at is null or revoked_at >= granted_at)
);

comment on table public.super_admins is
    'One row per super admin grant (D22). A user is a super admin while they hold a row with '
    'revoked_at is null. Read only by is_admin(); no client may read or write it.';
comment on column public.super_admins.granted_by is
    'Who granted it. Null on the rows 0014_super_admins.sql copied from profiles.role: those '
    'grants predate this table, and granted_at on them is when the table was created.';

-- At most one active grant per person. Also the index is_admin() uses, inside many policies.
create unique index super_admins_active_user_key
    on public.super_admins (user_id)
    where revoked_at is null;

alter table public.super_admins enable row level security;
revoke all on public.super_admins from public, anon, authenticated;


-- ── Revoking is a tombstone ──────────────────────────────────────────────────
create or replace function public.super_admins_tombstone_only()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    if tg_op in ('DELETE', 'TRUNCATE') then
        raise exception 'super_admins rows are never deleted; revoke a grant by setting revoked_at';
    end if;
    if old.revoked_at is not null then
        raise exception 'super_admins: grant % is already revoked; grant again with a new row', old.id;
    end if;
    if new.revoked_at is null
        or new.id is distinct from old.id
        or new.user_id is distinct from old.user_id
        or new.granted_by is distinct from old.granted_by
        or new.granted_at is distinct from old.granted_at then
        raise exception 'super_admins: the only update allowed is revoking an active grant';
    end if;
    return new;
end;
$$;

create trigger super_admins_tombstone_only
before update or delete on public.super_admins
for each row execute function public.super_admins_tombstone_only();

create trigger super_admins_no_truncate
before truncate on public.super_admins
for each statement execute function public.super_admins_tombstone_only();


-- ── Backfill ─────────────────────────────────────────────────────────────────
-- Every super admin today, one active row each. granted_by stays null: see its comment.
insert into public.super_admins (user_id)
select p.id
from public.profiles p
where p.role = 'admin';


-- ── is_admin() reads the table ───────────────────────────────────────────────
-- Same signature, still security definer with a fixed search_path, still a plain exists:
-- the recursion fix from legacy-dev/0004 holds, and nothing reads `profiles` any more. The
-- parameter is named like the column, so it is qualified with the function's name.
create or replace function public.is_admin(user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.super_admins sa
        where sa.user_id = is_admin.user_id
          and sa.revoked_at is null
    );
$$;


-- ── The new-user trigger decides no tier ─────────────────────────────────────
-- 0011's function minus the role: a new profile takes the column's default and nothing reads
-- it. Being a super admin is a row in super_admins, never something a sign-up decides.
create or replace function public.handle_new_user()
returns trigger as $$
begin
    insert into public.profiles (id, account_id) values (new.id, new.id::text);
    return new;
end;
$$ language plpgsql security definer set search_path = public;

comment on column public.profiles.role is
    'Retired by 0014_super_admins.sql: grants nothing. Super admins are rows in '
    'public.super_admins, read through is_admin().';

commit;
