-- AgarthaVision — Supabase Storage admin read access for the `samples` bucket
-- Bucket `samples` is private and `0003_storage_rls.sql` scopes every operation to the
-- uploading user's folder, so admins could list sample rows but load zero images.
-- The table policies in `0001`, `0004` and `0008` already grant admins cross-user reads;
-- only Storage was missed. This migration adds the matching admin SELECT policy so the
-- admin console can render images captured by any user.
-- Path convention: {user_id}/{sample_id}.jpg  (see schema.ts).
--
-- Apply via: Supabase dashboard → SQL Editor → paste → Run.
-- Prerequisites:
--   - `0003_storage_rls.sql` must already be applied (creates the owner-scoped policies).
--   - `0004_fix_profiles_rls_recursion.sql` must already be applied (defines
--     `public.is_admin(uuid)` and grants execute to `authenticated`).
--
-- Additive only: Postgres OR's together every permissive policy for the same command,
-- so this new SELECT policy sits alongside "samples: select own folder" from `0003`.
-- Nothing is dropped or replaced and medtech behaviour is unchanged — a medtech still
-- matches only the own-folder policy, while an admin matches this one for any folder.
--
-- Reversible: yes (see "Rollback" block at the bottom — commented out by default).

-- SELECT: admins can read every object in the samples bucket, regardless of folder
create policy "samples: admin read all"
on storage.objects
for select
to authenticated
using (
    bucket_id = 'samples'
    and public.is_admin(auth.uid())
);

-- Read-only by design: no admin INSERT/UPDATE/DELETE policy is added. Writes stay
-- owner-scoped through `0003`, and samples persist indefinitely per ADR-004 (false
-- positives are retained as labeled training data; deletion would destroy the
-- retraining corpus).


-- ── Rollback (commented out — uncomment + run if this migration must be reverted)

-- drop policy if exists "samples: admin read all" on storage.objects;
