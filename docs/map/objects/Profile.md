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
| `id` | PK, FK → `auth.users(id)`, ON DELETE CASCADE | `supabase/migrations/0001_init.sql:11` |
| `full_name` | nullable text | `supabase/migrations/0001_init.sql:12` |
| `role` | NOT NULL, default `medtech`, CHECK in (`medtech`, `admin`) | `supabase/migrations/0001_init.sql:13` |
| `created_at` | NOT NULL, default `now()` | `supabase/migrations/0001_init.sql:14` |

Auto-creation: `handle_new_user()` inserts `(id, 'medtech')` on every `auth.users` insert —
`supabase/migrations/0001_init.sql:18-28`. Note it never populates `full_name`, so that column
is null in practice.

Admin reads go through the SECURITY DEFINER helper `public.is_admin(uuid)`, added to break the
policy recursion the original inline subquery caused —
`supabase/migrations/0004_fix_profiles_rls_recursion.sql:4-18`.

Documented shape: `schema.ts:146-158`.

**No Room mirror.** Identity on-device is a DataStore-cached `LocalIdentity`
(`app/src/main/java/com/agarthavision/domain/model/LocalIdentity.kt`,
`data/repository/SupabaseAuthRepository.kt:31-42`), not a `profiles` row.

## Connected to

- **Owns** → [`Session`](Session.md), [`Sample`](Sample.md), [`Report`](Report.md) via
  `user_id` (`schema.ts:546-581`).
- **Owned by** `auth.users`, 1 → 0..1 (`schema.ts:541-545`).
- **Scopes** [`StorageObject`](StorageObject.md) — Storage RLS keys off `auth.uid()`, not off
  `profiles`.
- **Looks like but is not** `LocalIdentity`. That is a DataStore cache of the last signed-in
  user, deliberately independent of live auth so offline work stays attributable. It has no
  `role` and no server round-trip.

## If you change this

**Hits**
- Every RLS policy on `sessions`, `samples`, `detections`, `reports` — they all resolve admin
  through `is_admin(auth.uid())` (`0004_fix_profiles_rls_recursion.sql:25-57`).
- `0008_reports.sql:36-38`, which reintroduced the inline `(select role from profiles …)`
  subquery instead of `is_admin()`. Any change to the role column's name or values must patch
  both styles.
- Sign-in, if you add a required column with no default — `handle_new_user()` inserts only
  `id` and `role`.

**Does not hit**
- The Android app's login flow. It reads `auth.users` metadata for a display name
  (`data/repository/SupabaseAuthRepository.kt:76`), never the `profiles` row. Adding a
  `profiles` column changes nothing client-side until something queries it.
- Room. There is no local mirror to migrate.

## Surfaces

Written by the Postgres trigger only. Read by RLS policies. **No screen reads it, no sync
writes it, no report includes it.** The `admin` role has no UI anywhere in the app.

## See

`supabase/migrations/0001_init.sql:10-28`,
`supabase/migrations/0004_fix_profiles_rls_recursion.sql`, `schema.ts:146-158`.
