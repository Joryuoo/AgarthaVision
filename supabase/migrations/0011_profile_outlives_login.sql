-- 0011 · A profile outlives its login
--
-- Run via: Supabase dashboard → SQL Editor → paste → Run. By hand, once, per C6.
-- Target: project `agarthavision` (zxojfpfarhhoxjjicphi).
-- Requires: 0001_init.sql. Independent of 0007–0010; numbered after them only because they
-- are already taken on another branch.
--
-- ── Why ──────────────────────────────────────────────────────────────────────
-- A medtech who leaves a laboratory is offboarded by deleting their login (14zcqntjph8,
-- 14zcqntjnt1). That frees the email for the next laboratory that hires them, or for this one
-- if they come back. But the person must survive the login: they stay the author of every
-- patient, session, sample and report they made (C8, D11).
--
-- 0001 made that impossible. `profiles.id` was the login id itself, `references auth.users(id)
-- on delete cascade`, so deleting a login tried to delete the profile, and every authored row
-- (`patients.created_by`, `sessions.user_id`, `samples.user_id`, `reports.user_id`, all
-- NO ACTION) refused it. Offboarding anyone who had done any work failed.
--
-- ── What this does ───────────────────────────────────────────────────────────
-- `profiles.id` stops being tied to a login and becomes the person's permanent id. Nothing that
-- references it changes. A new column, `profiles.account_id`, holds the login it belongs to,
-- and is set to null when that login is deleted. A null `account_id` means "no longer has a
-- login" — the profile and all its authorship stay.
--
-- **Provider-neutral on purpose (D7).** `account_id` is `text` with no foreign key into the
-- provider's schema, so it can hold a Supabase, Cognito, Better Auth or self-hosted subject id
-- alike. The Supabase-specific parts are two trigger functions on `auth.users`: one fills
-- `account_id` for a new login, one clears it when a login is deleted. Moving provider means
-- replacing those two functions; no table changes.
--
-- **Today nothing reads `account_id`.** Every row has `account_id = id`, and every policy still
-- compares `auth.uid()` with `profiles.id` and the `user_id` columns, which stays correct while
-- the two are equal. Reconnecting a rehire's *new* login to their *old* profile is the step
-- that needs policies to resolve the profile through `account_id`. That is a separate ticket;
-- until it lands, a returning medtech is given a fresh profile like any new hire.
--
-- **Safe to run before the app ships anything.** No app build reads or writes `profiles`
-- (docs/map/objects/Profile.md), and the column is invisible to every existing query.

begin;

alter table public.profiles add column account_id text;

update public.profiles set account_id = id::text where account_id is null;

create unique index profiles_account_id_key on public.profiles (account_id);

-- Drop the 0001 foreign key from profiles.id to auth.users, by what it is rather than by its
-- default name, so the cascade cannot survive under a different name.
do $$
declare
    fk text;
begin
    for fk in
        select conname
        from pg_constraint
        where conrelid = 'public.profiles'::regclass
          and contype = 'f'
          and confrelid = 'auth.users'::regclass
    loop
        execute format('alter table public.profiles drop constraint %I', fk);
    end loop;
end;
$$;

-- New logins: same row as before, now also recording which login it is.
create or replace function public.handle_new_user()
returns trigger as $$
begin
    insert into public.profiles (id, role, account_id) values (new.id, 'medtech', new.id::text);
    return new;
end;
$$ language plpgsql security definer set search_path = public;

-- Deleted logins: the profile stays, only the link goes. One UPDATE by a unique key and nothing
-- else, because an error here would roll back the delete of the login itself.
create or replace function public.handle_deleted_user()
returns trigger as $$
begin
    update public.profiles set account_id = null where account_id = old.id::text;
    return old;
end;
$$ language plpgsql security definer set search_path = public;

create trigger on_auth_user_deleted
after delete on auth.users
for each row execute function public.handle_deleted_user();

commit;
