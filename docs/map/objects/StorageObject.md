---
type: object
status: verified
verified: 2026-09-29
commit: feaa4803
entity: supabase/migrations/0001_init.sql
---

# StorageObject

**One sentence.** A file in Supabase's own `storage.objects` table — the uploaded JPEG behind a
sample, in the private `samples` bucket, or a report file in the private `reports` bucket. The
app never names this type — it only ever holds a path string.

## Why this shape

Sample images are the training corpus, so they are private, owner-scoped, and **undeletable by
design**. The whole access model is carried by the object key: `{user_id}/{sample_id}.jpg`.
Because RLS validates the first path segment against `auth.uid()`, the path is not a naming
convention — it *is* the permission check. Get the path wrong and the upload is rejected.
Report files follow the same rule with `{user_id}/{report_id}.{pdf|csv}`.

No migration in this repo creates `storage.objects`. Supabase owns the physical table; this repo
owns only the buckets' policies (and, for `reports`, the bucket row itself).

## Shape

**`samples` bucket** — policies in `supabase/migrations/0001_init.sql` (carried over from
`legacy-dev/0003_storage_rls.sql`). The bucket itself is created outside the file.

| Policy | Rule | Line |
|---|---|---|
| INSERT | `bucket_id = 'samples'` and `(storage.foldername(name))[1] = auth.uid()::text` | `0001_init.sql:506-511` |
| SELECT | same predicate | `0001_init.sql:513-518` |
| UPDATE | same predicate in both `using` and `with check` — needed for upsert | `0001_init.sql:521-530` |
| SELECT (admin) | `bucket_id = 'samples'` and `is_admin(auth.uid())` | `0001_init.sql:534-536` |
| DELETE | **deliberately absent** | `0001_init.sql:538-540` |

**`reports` bucket** — created and policied by `supabase/migrations/0003_reports_bucket.sql`:
private, 10 MB limit, PDF and CSV only, visibility mirroring `reports_select_own` (owner or
admin). Written and read by `data/supabase/ReportRemoteDataSource.kt`. See [`Report`](Report.md).

Supabase-managed columns worth knowing — `id`, `bucket_id`, `name`, `owner` / `owner_id`,
`metadata`, `path_tokens`, `version` — are catalogued in `schema.ts` (`StorageObject`). Do not
write any of them directly.

**Client side, samples bucket**, all in `data/supabase/SampleRemoteDataSource.kt`:

- Upload with `upsert = true` to `"$userId/${sample.sampleId}.jpg"`, where `userId` comes from
  the **live Supabase session**, not from the Room row (`::syncSample`).
- Download with the session's own auth, for the background image cache (`::downloadSampleImage`,
  called by `CacheSampleImagesUseCase`).
- A signed URL valid for **15 minutes**, for opening one sample (`::createSignedSampleImageUrl`).

Images are resized to **640×640 at JPEG quality 80** before upload
(`SyncSampleUseCase.kt::resizeToSyncJpeg`).

## Connected to

- **Pointed at by** [`Sample`](Sample.md) via `storage_path`, 1 → 0..1. A sample that has never
  synced has no object.
- **Pointed at by** [`Report`](Report.md), by derivation from its id — no key column.
- **Scoped by** [`Profile`](Profile.md) — but through `auth.uid()` in the policy, not through a
  foreign key. There is no FK from `storage.objects` to `profiles`.
- **Looks like but is not** the local JPEG. `SampleImageStore` writes device files under
  `filesDir/users/{owner}/samples/{sampleId}.jpg` (`SampleImageStore.kt::persistJpeg`) — a
  different path, a different lifetime, and the one that gets read first. The code still has an
  unowned `local` folder (`PersistFlaggedFrameUseCase.UNOWNED_FOLDER`); mandatory login means
  new captures always have an owner, and it has no remote equivalent.

## If you change this

**Hits**
- The upload path construction and the INSERT policy together. They must agree character for
  character or every upload 403s — `SampleRemoteDataSource.kt::syncSample` (`storagePath`)
  against `0001_init.sql:506-511`.
- The image read paths: the background cache (`CacheSampleImagesUseCase`), the Coil fetcher
  (`ui/image/SampleImageFetcher.kt`), and the signed-URL fallback
  (`domain/usecase/records/ResolveSampleImageSourceUseCase.kt`).
- The bucket name constant `SAMPLES_BUCKET` in `SampleRemoteDataSource`, which the policy text
  repeats as a literal.

**Does not hit**
- `samples.storage_path` on rows already synced. Renaming or re-pathing objects does not
  rewrite the pointers; existing rows keep pointing at keys that no longer resolve.
- Local image display. The on-device file is preferred, and Storage is reached only when that
  file is missing — most reads never touch Storage at all.
- Sample deletion. There is no DELETE policy, so no client-side change can remove an object.
  Removing that protection requires a new migration and contradicts `constraints.md` C8.

## Surfaces

Written once per sample by `SyncSampleUseCase` → `SampleRemoteDataSource.syncSample`, and once
per report file by `ReportRemoteDataSource`. Sample objects are read by the background image
cache after a pull and by Sample Detail when the local file is absent. No screen lists objects.

## See

`supabase/migrations/0001_init.sql:502-540`, `supabase/migrations/0003_reports_bucket.sql`,
`data/supabase/SampleRemoteDataSource.kt`, `data/supabase/SyncSampleUseCase.kt`,
`schema.ts` (`StorageObject`).
