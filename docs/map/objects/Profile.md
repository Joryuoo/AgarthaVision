---
type: object
status: verified
verified: 2026-10-01
commit: b64271d2
entity: supabase/migrations/0001_init.sql
---

# Profile

**One sentence.** The person behind a login — one row per medtech or admin, created
automatically when their login is, and kept after the login is deleted so they stay the author
of their work. Product calls the person a *medtech*; the table
is `public.profiles` and the role lives in a text column, not a Postgres enum.

## Why this shape

`auth.users` is Supabase-owned and cannot carry application columns, so `profiles` exists
purely to hang a `role` and a display name off an auth identity, and to give every other table
a foreign key it is allowed to reference. It is created by a trigger rather than by the app so
a row always exists before the first query — there is no sign-up flow to hook into.

The role column is where all admin capability lives, and nothing in the Android app reads it.

**A profile outlives its login** (`0010_profile_outlives_login.sql`, 14zcqntjph8). Offboarding
a medtech deletes their login, which frees the email for another laboratory. Until 0010 that was
impossible: `profiles.id` referenced `auth.users` ON DELETE CASCADE, and every authored row
refused the cascade. Now `id` is the person's permanent id with no foreign key, and `account_id`
says which login, if any, the person currently has. It is `text` with no foreign key into the
provider's schema so it survives a move away from Supabase (D7); the two trigger functions on
`auth.users` are the only Supabase-specific part.

## Shape

| Field | Constraint | Cited |
|---|---|---|
| `id` | PK. FK → `auth.users(id)` ON DELETE CASCADE until 0010, which drops it | `supabase/migrations/0001_init.sql:35`, `0010_profile_outlives_login.sql:48-64` |
| `account_id` | nullable text, UNIQUE; the login's subject id, null once the login is deleted | `supabase/migrations/0010_profile_outlives_login.sql:42-46` |
| `full_name` | nullable text | `supabase/migrations/0001_init.sql:36` |
| `role` | NOT NULL, default `medtech`, CHECK in (`medtech`, `admin`) | `supabase/migrations/0001_init.sql:37` |
| `created_at` | NOT NULL, default `now()` | `supabase/migrations/0001_init.sql:38` |

Auto-creation: `handle_new_user()` inserts `(id, 'medtech')` on every `auth.users` insert —
`supabase/migrations/0001_init.sql:42-52` — and since 0010 also `account_id = id`
(`0010_profile_outlives_login.sql:66-73`). `handle_deleted_user()` sets `account_id` to null when
a login is deleted (`:75-87`). Note neither populates `full_name`, so that column is null in
practice.

**Nothing reads `account_id` yet.** Every policy still compares `auth.uid()` with `profiles.id`
and the `user_id` columns, which holds while each profile's id equals its login id. Reconnecting
a rehire's new login to their old profile needs policies that resolve the profile through
`account_id`; until then a returning medtech gets a fresh profile.

Admin reads go through the SECURITY DEFINER helper `public.is_admin(uuid)`, added to break the
policy recursion the original inline subquery caused —
`supabase/migrations/0001_init.sql:58-72`, carried over from
`legacy-dev/0004_fix_profiles_rls_recursion.sql`.

Documented shape: `schema.ts` (`Profile`).

**No Room mirror.** Identity on-device is a DataStore-cached `LocalIdentity`
(`app/src/main/java/com/agarthavision/domain/model/LocalIdentity.kt`,
`SupabaseAuthRepository.kt::observeLocalIdentity`), not a `profiles` row.

## Connected to

- **Creates / links** → [`Patient`](Patient.md) via `patients.created_by` and `patient_users`.
- **Owns** → [`Session`](Session.md), [`Sample`](Sample.md), [`Report`](Report.md) via
  `user_id`.
- **Has** at most one login in `auth.users`, through `account_id`; none once it is deleted.
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
- Sign-in, if you add a required column with no default — `handle_new_user()` inserts only
  `id`, `role` and `account_id`.
- Offboarding, if anything brings back a cascade from `auth.users` to `profiles`: deleting a
  login would again fail for every medtech who authored a row.

**Does not hit**
- The Android app's login flow. It reads `auth.users` metadata for a display name
  (`SupabaseAuthRepository.kt::cacheIdentity`), never the `profiles` row. Adding a
  `profiles` column changes nothing client-side until something queries it.
- Room. There is no local mirror to migrate.

## Surfaces

Written by the Postgres trigger only. Read by RLS policies. **No screen reads it, no sync
writes it, no report includes it.** The `admin` role has no UI anywhere in the app.

## See

`supabase/migrations/0001_init.sql:30-72`, `supabase/migrations/0010_profile_outlives_login.sql`,
`schema.ts` (`Profile`).
