# Change-impact index

*If you are changing X, open these.* One row per change surface. First-order impact only —
each row names the thing that surface breaks that you would not guess.

Cards live in `../objects/` and `../processes/`. Rules live in `../../constraints.md`.

---

## Changing the data model or writing a migration

**Open:** the object card for the entity · `../../constraints.md` C6 ·
`supabase/migrations/` (highest number wins) · the matching Room entity.

**Then check, in order:**
1. Does the column need to exist remotely at all? Several deliberately do not — `is_repeat`,
   `samples.status`, `sessions.claim_exempt`, `reports.supabase_status`.
2. If it does, add it to the insert row in `data/supabase/*RemoteDataSource.kt`. **A column
   missing from the insert row is silently dropped, with no error.**
3. If it is a Room change, bump `core/database/AgarthaDatabase.kt:34`.
4. Update `schema.ts` in the same change.
5. Write the numbered SQL file. It is applied by hand in the dashboard — never
   programmatically.

**The non-obvious break:** `core/di/DatabaseModule.kt:49` uses
`fallbackToDestructiveMigration(dropAllTables = true)`. A Room version bump **wipes every
device**, it does not migrate. Fine in Phase 1; a data-loss incident the day there is real
data.

## Changing RLS, auth, or ownership

**Open:** `Profile.md` · `StorageObject.md` · `supabase/migrations/0004_fix_profiles_rls_recursion.sql` ·
`../processes/sync.md`.

Every table's admin path resolves through `public.is_admin(uuid)` — except `reports`, which
reintroduced an inline `(select role from profiles …)` subquery
(`supabase/migrations/0008_reports.sql:36-38`). **Patch both styles or admin reads diverge by
table.**

**The non-obvious break:** the Storage object key *is* the permission check. The INSERT policy
compares `(storage.foldername(name))[1]` against `auth.uid()`
(`supabase/migrations/0003_storage_rls.sql:16-19`), and the client builds that path from the
live session (`data/supabase/SampleRemoteDataSource.kt:35`). Change either and every upload
403s.

## Changing the capture pipeline

**Open:** `../processes/capture.md` · `core/camera/FrameSampler.kt` · `core/camera/CameraManager.kt`.

**The non-obvious break:** the flagged queue is **Room-backed**, not in-memory
(`data/repository/FlaggedFrameStore.kt:58-74`), and it is filtered by `user_id`, so it is
invisible on a device that has never signed in. Also: there is no `ImageCapture` use case —
manual capture reads a cached JPEG from the analysis stream
(`ui/capture/CaptureViewModel.kt:135`), so anything that stops populating `latestFrameBytes`
breaks manual capture without touching manual-capture code.

## Changing the inference contract

**Open:** `../processes/infer.md` · `data/remote/dto/InferenceResponseDto.kt` ·
`inference/server.py` · `data/local/mapper/VerificationMapper.kt`.

Client and server must move together, and they are versioned independently — the server is a
container image, the client is an APK.

**The non-obvious break:** coordinates are **centre-x, centre-y, width, height in pixels**
(`inference/server.py:47`), copied verbatim into `bbox_*`
(`data/local/mapper/VerificationMapper.kt:36-39`), despite comments in `DetectionEntity.kt:13`
and `supabase/migrations/0001_init.sql:64` claiming they are normalised. And renaming a model
class silently changes every verdict and every EPG grouping, because
`EggSpecies.fromClassLabel` matches on the literal string
(`domain/model/EggSpecies.kt:15-22`).

## Changing validation logic

**Open:** `../processes/validate.md` · `data/local/mapper/VerificationMapper.kt:10-17` ·
`Detection.md` · `../../constraints.md` C7 and C8.

The whole clinical decision rule is seventeen lines in one function. Treat it as the most
sensitive code in the repo.

**The non-obvious break:** verdicts are lowercase in Room and uppercase in Postgres
(`domain/model/DetectionVerdict.kt:13-16`). Adding a value means the enum, the Postgres CHECK,
**and** every raw SQL string that names a verdict — `data/local/dao/DetectionDao.kt:43`, `:66`,
`:87`. Those three query strings do not agree with each other today.

## Changing sync

**Open:** `../processes/sync.md` · `data/supabase/SyncSampleUseCase.kt` ·
`domain/usecase/sync/SyncPendingDataUseCase.kt` · `Session.md`.

Push order is FK-safe and not incidental: sessions → samples → reports.

**The non-obvious break:** there are **no retries and no `Worker`**. WorkManager is a declared
dependency with no implementation (`app/build.gradle.kts:157`). A `sync_failed` row waits for a
foreground trigger — login, app start while authenticated, or connectivity returning. Also,
claim runs *before* sync and is what lets a nullable local `user_id` satisfy a NOT NULL remote
column.

## Changing report generation, CSV, or EPG

**Open:** `../processes/report.md` · `Report.md` ·
`domain/usecase/records/GenerateSessionReportUseCase.kt` · `core/util/EpgCalculator.kt`.

**The non-obvious break:** EPG's real definition is the WHERE clause at
`data/local/dao/DetectionDao.kt:33-52`, which counts every detection that is **not** a false
positive — so `WRONG_CLASS` and `BOX_INCORRECT` boxes count as eggs. Prose describing EPG as
"confirmed detections only" matches a different query
(`data/local/dao/DetectionDao.kt:66`). Reports are snapshots and are never recomputed, so
changing the multiplier or the rule changes future numbers only.

Report generation also **requires a live auth session**
(`GenerateSessionReportUseCase.kt:38`), unlike the rest of the offline-capable flows.

## Changing UI, theme, or design tokens

**Open:** `../../constraints.md` C11 · `app/src/main/java/com/agarthavision/ui/theme/` ·
`../../file-tree.md`.

Raw hex belongs only in the palette definition. Screens read `AgarthaTheme.colors.*` so both
modes resolve.

**The non-obvious break:** Capture is theme-exempt and stays dark regardless of the toggle. A
change that assumes every screen follows the preference will look correct everywhere except
the screen the product is actually about.

## Changing the build, versions, or tooling

**Open:** `../../stack.md` · `../../commands.md` · `gradle/libs.versions.toml`.

**The non-obvious break:** the `pre-commit` hook runs a full `assembleDebug` plus tests plus
lint, so any change that slows the build slows every commit. And there is **no CI** — no
`.github/` directory exists — so the hooks are the only gate that exists at all.

## Changing commit, branch, or PR conventions

**Open:** `../../non-negotiables.md` · `../../constraints.md` C9.

**The non-obvious break:** nothing enforces the convention. The `commit-msg` hook was removed,
and the committed `commitlint.config.js` encodes a *conventional-commit* shape that
contradicts the documented `[type][ClickUp-ID][Lastname]` format while being wired to no hook.
Do not assume a tool will catch a mistake here.
