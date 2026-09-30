---
type: object
status: verified
verified: 2026-09-30
commit: b64271d2
entity: supabase/migrations/0001_init.sql
---

# Profile

**One sentence.** The user record attached to a Supabase Auth account — one row per medtech or
admin, created automatically on first sign-in. Product calls the person a *medtech*; the table
is `public.profiles` and the role lives in a text column, not a Postgres enum.

## Why this shape

`auth.users` is Supabase-owned and cannot carry application columns, so `profiles` exists
purely to hang a `role` and a display name off an auth identity, and to give every other table
a foreign key it is allowed to reference. It is created by a trigger rather than by the app so
a row always exists before the first query — there is no sign-up flow to hook into.

The role column is where all admin capability lives, and nothing in the Android app reads it.

## Shape

| Field | Constraint | Cited |
|---|---|---|
| `id` | PK, FK → `auth.users(id)`, ON DELETE CASCADE | `supabase/migrations/0001_init.sql:35` |
| `full_name` | nullable text | `supabase/migrations/0001_init.sql:36` |
| `role` | NOT NULL, default `medtech`, CHECK in (`medtech`, `admin`) | `supabase/migrations/0001_init.sql:37` |
| `created_at` | NOT NULL, default `now()` | `supabase/migrations/0001_init.sql:38` |

Auto-creation: `handle_new_user()` inserts `(id, 'medtech')` on every `auth.users` insert —
`supabase/migrations/0001_init.sql:42-52`. Note it never populates `full_name`, so that column
is null in practice.

Admin reads go through the SECURITY DEFINER helper `public.is_admin(uuid)`, added to break the
policy recursion the original inline subquery caused —
`supabase/migrations/0001_init.sql:58-72`, carried over from
`legacy-dev/0004_fix_profiles_rls_recursion.sql`.

Documented shape: `schema.ts` (`Profile`).

**Colleagues can read each other's name, and only then.** `profiles_select_colleague`
(`supabase/migrations/0008_colleague_names.sql:54-56`) adds a SELECT beside
`profiles_select_own` for a colleague who authored a session or report on a patient the reader
is assigned to (`shares_patient_history_with`, `:29-49`). It exists so a colleague's read-only
record can name its author (14zcqntjph6).

**Room holds colleagues' names only.** Identity on-device is a DataStore-cached `LocalIdentity`
(`app/src/main/java/com/agarthavision/domain/model/LocalIdentity.kt`,
`SupabaseAuthRepository.kt::observeLocalIdentity`), not a `profiles` row. The `colleagues` table
(`data/local/entity/ColleagueEntity.kt`, Room v24) caches `id` and `full_name` of those
colleagues, filled by the pull (`data/supabase/ProfileRemoteDataSource.kt::fetchColleagues`) and
read through `ColleagueRepository`. It is a label, never a permission. Because
`handle_new_user()` writes no name, a colleague reads as "another medtech" until something sets
`full_name`.

## Connected to

- **Creates / links** → [`Patient`](Patient.md) via `patients.created_by` and `patient_users`.
- **Owns** → [`Session`](Session.md), [`Sample`](Sample.md), [`Report`](Report.md) via
  `user_id`.
- **Owned by** `auth.users`, 1 → 0..1.
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
  `id` and `role`.

**Does not hit**
- The Android app's login flow. It reads `auth.users` metadata for a display name
  (`SupabaseAuthRepository.kt::cacheIdentity`), never the `profiles` row. Adding a
  `profiles` column changes nothing client-side until something queries it.
- Room. There is no local mirror to migrate.

## Surfaces

Written by the Postgres trigger only. Read by RLS policies. **No screen reads it, no sync
writes it, no report includes it.** The `admin` role has no UI anywhere in the app.

## See

`supabase/migrations/0001_init.sql:30-72`, `schema.ts` (`Profile`).
