-- AgarthaVision — Supabase Storage RLS for the `samples` bucket
-- Bucket `samples` is private; these policies scope all access to the uploading user's folder.
-- Path convention: {user_id}/{sample_id}.jpg  (see docs/04_CLOUD_BACKEND_PLAN.md §4.2)
--
-- Apply via: Supabase dashboard → SQL Editor → paste → Run.
-- Prerequisite: bucket `samples` must already exist (create via dashboard or CLI:
--   supabase storage create samples --public=false)
--
-- Reversible: yes (see "Rollback" block at the bottom — commented out by default).

-- INSERT: users can upload only into their own folder inside the samples bucket
create policy "samples: insert own folder"
on storage.objects
for insert
to authenticated
with check (
    bucket_id = 'samples'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
);

-- SELECT: users can read only objects inside their own folder
create policy "samples: select own folder"
on storage.objects
for select
to authenticated
using (
    bucket_id = 'samples'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
);

-- UPDATE: needed for overwrite/upsert flows
create policy "samples: update own folder"
on storage.objects
for update
to authenticated
using (
    bucket_id = 'samples'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
)
with check (
    bucket_id = 'samples'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
);

-- No DELETE policy: samples persist indefinitely per ADR-004 (false positives are
-- retained as labeled training data; deletion would destroy the retraining corpus).


-- ── Rollback (commented out — uncomment + run if this migration must be reverted)

-- drop policy if exists "samples: update own folder" on storage.objects;
-- drop policy if exists "samples: select own folder" on storage.objects;
-- drop policy if exists "samples: insert own folder" on storage.objects;
