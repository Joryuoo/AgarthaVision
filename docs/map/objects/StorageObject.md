# StorageObject

**One sentence.** The uploaded JPEG behind a sample, held in Supabase's own `storage.objects`
table in the private `samples` bucket. The app never names this type — it only ever holds a
`storage_path` string.

## Why this shape

Sample images are the training corpus, so they are private, owner-scoped, and **undeletable by
design**. The whole access model is carried by the object key: `{user_id}/{sample_id}.jpg`.
Because RLS validates the first path segment against `auth.uid()`, the path is not a naming
convention — it *is* the permission check. Get the path wrong and the upload is rejected.

This is the only object in the map that no migration in this repo creates. Supabase owns the
physical table; this repo owns only the policies on it.

## Shape

**Not created here.** No project migration creates `storage.objects`
(`schema.ts:426-428`). What this repo defines is the four policy decisions in
`supabase/migrations/0003_storage_rls.sql`:

| Policy | Rule | Line |
|---|---|---|
| INSERT | `bucket_id = 'samples'` and `(storage.foldername(name))[1] = auth.uid()::text` | `0003_storage_rls.sql:12-19` |
| SELECT | same predicate | `0003_storage_rls.sql:22-29` |
| UPDATE | same predicate in both `using` and `with check` — needed for upsert | `0003_storage_rls.sql:32-43` |
| DELETE | **deliberately absent** | `0003_storage_rls.sql:45-46` |

The bucket is private and must exist before these policies run
(`supabase/migrations/0003_storage_rls.sql:2`, `:6-7`).

Supabase-managed columns worth knowing — `id`, `bucket_id`, `name`, `owner` / `owner_id`,
`metadata`, `path_tokens`, `version` — are catalogued at `schema.ts:439-475`. Do not write any
of them directly.

**Client side**, the object is touched in exactly two places, both in one file:

- Upload with `upsert = true` to `"$userId/${sample.sampleId}.jpg"`, where `userId` comes from
  the **live Supabase session**, not from the Room row —
  `data/supabase/SampleRemoteDataSource.kt:33-39`.
- Read via a signed URL valid for **15 minutes** —
  `data/supabase/SampleRemoteDataSource.kt:51-55`, `:159`.

Images are resized to **640×640 at JPEG quality 80** before upload
(`data/supabase/SyncSampleUseCase.kt:58-83`).

## Connected to

- **Pointed at by** [`Sample`](Sample.md) via `storage_path`, 1 → 0..1. A sample that has never
  synced has no object.
- **Scoped by** [`Profile`](Profile.md) — but through `auth.uid()` in the policy, not through a
  foreign key. There is no FK from `storage.objects` to `profiles`.
- **Looks like but is not** the local JPEG. `SampleImageStore` writes device files under
  `filesDir/users/{owner}/samples/{sampleId}.jpg`
  (`data/local/SampleImageStore.kt:15-20`) — a different path, a different lifetime, and the
  one that gets read first. Unowned captures go under a literal `local` folder
  (`domain/usecase/capture/PersistFlaggedFrameUseCase.kt:66-67`), which has no remote
  equivalent.

## If you change this

**Hits**
- The upload path construction and the INSERT policy together. They must agree character for
  character or every upload 403s — `data/supabase/SampleRemoteDataSource.kt:35` against
  `0003_storage_rls.sql:16-19`.
- The signed-URL read path in Sample Detail, which is the fallback when the local file is gone
  (`domain/usecase/records/ResolveSampleImageSourceUseCase.kt:17-41`).
- The bucket name constant, which is duplicated in the policy text and in
  `data/supabase/SampleRemoteDataSource.kt:155`.

**Does not hit**
- `samples.storage_path` on rows already synced. Renaming or re-pathing objects does not
  rewrite the pointers; existing rows keep pointing at keys that no longer resolve.
- Local image display. `ResolveSampleImageSourceUseCase` prefers the on-device file and only
  reaches Storage when that file is missing — most reads never touch Storage at all.
- Sample deletion. There is no DELETE policy, so no client-side change can remove an object.
  Removing that protection requires a new migration and contradicts `constraints.md` C8.

## Surfaces

Written once per sample by `SyncSampleUseCase` → `SampleRemoteDataSource.syncSample`. Read only
by Sample Detail, and only when the local file is absent. No screen lists objects; no report
includes them.

## See

`supabase/migrations/0003_storage_rls.sql`,
`app/src/main/java/com/agarthavision/data/supabase/SampleRemoteDataSource.kt:28-55`,
`data/supabase/SyncSampleUseCase.kt:51-83`, `schema.ts:424-475`.
