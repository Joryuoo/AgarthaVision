---
type: object
status: verified
verified: 2026-10-02
commit: a9a68580
entity: supabase/migrations/0001_init.sql
---

# Profile

**One sentence.** The person behind a login — one row per medtech or admin, created
automatically when their login is, and kept after the login is deleted so they stay the author
of their work. Product calls the person a *medtech*; the table
is `public.profiles` and the role lives in a text column, not a Postgres enum.

## Why this shape

`auth.users` is Supabase-owned and cannot carry application columns, so `profiles` exists
purely to hang a display name (and, until 0014, a `role`) off an auth identity, and to give every other table
a foreign key it is allowed to reference. It is created by a trigger rather than by the app so
a row always exists before the first query — there is no sign-up flow to hook into.

**Super admin is not a profile field** (`0014_super_admins.sql`, 14zcqntjwje, D22). It is an
active row in [`super_admins`](#super-admins), and `role` is retired: it stays in place, grants
nothing, and nothing in the Android app reads it. Who authored a record and who may run the
platform are separate facts, and a column on the author record would be one self-editing
policy away from self-granted. An **org admin** is not a value here either. It is a role on the
membership in `organization_members`, which the Admin Console's own migration set adds. An org
admin's profile still says `medtech`, so an org admin can sign in on a phone. See
[`file-tree.md`](../../file-tree.md#the-admin-consoles-migrations--same-database-other-repository).

**A profile outlives its login** (`0011_profile_outlives_login.sql`, 14zcqntjph8, 14zcqntjvjx;
the rule is C8). Offboarding a medtech deletes their login, which frees the email for another
laboratory, or for this one if it rehires them. Until 0011 that was impossible: `profiles.id`
referenced `auth.users` ON DELETE CASCADE, and every authored row refused the cascade. Now `id` is the person's permanent id with no foreign key, and `account_id`
says which login, if any, the person currently has. It is `text` with no foreign key into the
provider's schema so it survives a move away from Supabase (D7); the two trigger functions on
`auth.users` are the only Supabase-specific part.

The rows that cascade *from* a profile never fire: nothing deletes a profile, and a deleted
login no longer reaches one. They are `patient_users.user_id` and the console's
`organization_members.user_id` (ON DELETE CASCADE), and the console's
`organizations.created_by`, `organization_members.added_by` and `admin_audit_log.actor_id`
(ON DELETE SET NULL); checked on `agarthavision` on 2026-10-01.

## Shape

| Field | Constraint | Cited |
|---|---|---|
| `id` | PK. FK → `auth.users(id)` ON DELETE CASCADE until 0011, which drops it | `supabase/migrations/0001_init.sql:35`, `0011_profile_outlives_login.sql:48-64` |
| `account_id` | nullable text, UNIQUE; the login's subject id, null once the login is deleted | `supabase/migrations/0011_profile_outlives_login.sql:42-46` |
| `full_name` | nullable text | `supabase/migrations/0001_init.sql:36` |
| `role` | NOT NULL, default `medtech`, CHECK in (`medtech`, `admin`). Retired by 0014: grants nothing | `supabase/migrations/0001_init.sql:37`, `0014_super_admins.sql:150-152` |
| `created_at` | NOT NULL, default `now()` | `supabase/migrations/0001_init.sql:38` |

Auto-creation: `handle_new_user()` inserts a profile on every `auth.users` insert —
`supabase/migrations/0001_init.sql:42-52` — since 0011 with `account_id = id`
(`0011_profile_outlives_login.sql:66-73`), and since 0014 naming no role, so `role` takes its
default (`0014_super_admins.sql:142-148`). `handle_deleted_user()` sets `account_id` to null when
a login is deleted (`:75-87`). Note neither populates `full_name`, so that column is null in
practice.

**Nothing reads `account_id` yet.** Every policy still compares `auth.uid()` with `profiles.id`
and the `user_id` columns, which holds while each profile's id equals its login id. Reconnecting
a rehire's new login to their old profile needs policies that resolve the profile through
`account_id`; until then a returning medtech gets a fresh profile.

Admin reads go through the SECURITY DEFINER helper `public.is_admin(uuid)`, added to break the
policy recursion the original inline subquery caused —
`supabase/migrations/0001_init.sql:58-72`, carried over from
`legacy-dev/0004_fix_profiles_rls_recursion.sql`. Since 0014 it reads `super_admins` instead of
`role`, with the same signature, so no caller changed (`0014_super_admins.sql:123-136`).

### Super admins

`public.super_admins` (`0014_super_admins.sql:47-75`) holds one row per grant: `user_id`,
`granted_by`, `granted_at`, `revoked_by`, `revoked_at`, all pointing at `profiles(id)`, never at
the login table, so a grant outlives a login like authorship does (0011, D7). A user is a super
admin while they hold a row with `revoked_at` null, and a partial unique index allows at most
one such row.

- **Nobody signed in can read or write it.** RLS is on with no policy, and `anon` and
  `authenticated` hold no privilege, so a colleague or an org admin can no longer learn who the
  super admins are from this table. Only `is_admin()` reads it.
- **Revoking is a tombstone (C8).** Setting `revoked_at` is the only update allowed; a trigger
  refuses a delete, a truncate, un-revoking and moving a grant, for the table owner too
  (`:79-108`). Granting again is a new row.
- **Grants are made by hand** in the SQL editor until the console adds guarded functions. 0014
  copied each `role = 'admin'` profile in as one active row with `granted_by` null (`:113-115`).
- **The console's gate still reads `role`** until 14zcqntjwjf switches it to `is_admin()`. Until
  then a super admin granted by hand needs both the row and `role = 'admin'`.
- **A super admin's profile cannot be deleted**: their grant references it. Nothing deletes a
  profile anyway (C8).

Documented shape: `schema.ts` (`Profile`, `SuperAdmin`).

**Colleagues can read each other's name, and only then.** `profiles_select_colleague`
(`supabase/migrations/0008_colleague_names.sql:54-56`) adds a SELECT beside
`profiles_select_own` for a colleague who authored a session or report on a patient the reader
is assigned to (`shares_patient_history_with`, `:29-49`). It exists so a colleague's read-only
record can name its author (14zcqntjph6).

**Room holds colleagues' names only.** Identity on-device is a DataStore-cached `LocalIdentity`
(`app/src/main/java/com/agarthavision/domain/model/LocalIdentity.kt`,
`SupabaseAuthRepository.kt::observeLocalIdentity`), not a `profiles` row. The `colleagues` table
(`data/local/entity/ColleagueEntity.kt`, Room v24) caches `id` and `full_name` of those
colleagues, filled by the pull (`data/supabase/ProfileRemoteDataSource.kt::fetchColleagues`, by
the ids `ColleagueDao::getColleagueIdsOnLinkedPatients` reads off the device, so an org admin's
phone never lists their laboratory's staff) and read through `ColleagueRepository`. It is a
label, never a permission. Because `handle_new_user()` writes no name, a colleague reads as
"another medtech" until something sets `full_name`.

## Connected to

- **Creates / links** → [`Patient`](Patient.md) via `patients.created_by` and `patient_users`.
- **Owns** → [`Session`](Session.md), [`Sample`](Sample.md), [`Report`](Report.md) via
  `user_id`.
- **Has** at most one login in `auth.users`, through `account_id`; none once it is deleted.
- **Holds** any number of `super_admins` grants over time, at most one active.
- **Scopes** [`StorageObject`](StorageObject.md) — Storage RLS keys off `auth.uid()`, not off
  `profiles`.
- **Looks like but is not** `LocalIdentity`. That is a DataStore cache of the last signed-in
  user, deliberately independent of live auth so offline work stays attributable. It has no
  `role` and no server round-trip.

## If you change this

**Hits**
- Every RLS policy that has an admin branch — in the consolidated schema they all resolve
  admin through `is_admin(auth.uid())` (policies from `0001_init.sql:352`).
- On the dev and prod projects, which still run the legacy history, `legacy-dev/0008_reports.sql:36-38`
  and `legacy-dev/0012` reintroduced the inline `(select role from profiles …)` subquery. A change
  to the role column's name or values there must patch both styles.
- The Admin Console. Its migrations read `id` and `full_name`, and super admin through
  `is_admin()`; its app gate reads `role` directly until 14zcqntjwjf. It assumes `id` *is* the
  login id: it compares `auth.uid()` with
  `organization_members.user_id` and joins `auth.users` on `id`. Resolving a profile through
  `account_id` instead (the rehire step above) breaks it without an error here. It also holds
  foreign keys to `profiles(id)`: `organization_members.user_id` cascades on delete, and the
  actor columns are set null. Since 0011 a deleted login no longer deletes the profile, so an
  offboarded medtech's membership stays, still `active`, with no login behind it. Its org-admin
  policy also lets an org admin read their laboratory's members.
- Sign-in, if you add a required column with no default — `handle_new_user()` inserts only
  `id` and `account_id`.
- Super admin, if anything makes `is_admin()` read `role` again, or adds an UPDATE policy on
  `super_admins`: both reopen what 0014 closed.
- Offboarding, if anything brings back a cascade from `auth.users` to `profiles`: deleting a
  login would again fail for every medtech who authored a row.

**Does not hit**
- The Android app's login flow. It reads `auth.users` metadata for a display name
  (`SupabaseAuthRepository.kt::cacheIdentity`), never the `profiles` row.
- Room, for a new column. The one client read (`ProfileRemoteDataSource.kt::fetchColleagues`)
  names its columns, `id` and `full_name`, so adding one changes nothing on the phone. Renaming
  either of those does: it breaks the pull, and the `colleagues` cache above mirrors them.

## Surfaces

Written by the Postgres trigger only. Read by RLS policies, and by the pull for colleagues'
names, which a colleague's read-only record shows as its author. **No sync writes it, no
report includes it, and nothing on the phone reads `role` or `super_admins`.** Super admin has
no UI anywhere in the app.

## See

`supabase/migrations/0001_init.sql:30-72`, `supabase/migrations/0011_profile_outlives_login.sql`,
`supabase/migrations/0014_super_admins.sql`, `schema.ts` (`Profile`, `SuperAdmin`).
