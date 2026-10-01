# Change-impact index

*If you are changing X, open these.* One row per change surface. First-order impact only —
each row names the thing that surface breaks that you would not guess.

Cards live in `../objects/` and `../processes/`. Rules live in `../../constraints.md`.

---

## Changing the data model or writing a migration

**Open:** the object card for the entity · `../../constraints.md` C6 ·
`supabase/migrations/` (highest number wins) · the matching Room entity.

**Then check, in order:**
1. Does the column need to exist remotely at all? Several deliberately do not — `predictions_json`
   (its content syncs as `predictions` rows instead), `samples.status`,
   `samples.inference_state` and `samples.inference_attempts`, `reports.supabase_status`,
   `psgc_barangays`.
2. If it does, add it to the insert row in `data/supabase/*RemoteDataSource.kt`. **A column
   missing from the insert row is silently dropped, with no error.**
3. If it is a Room change, bump `AgarthaDatabase.version` **and write the `Migration`** into
   `core/database/Migrations.kt`'s `ALL_MIGRATIONS`. Every bump from 22 onward needs one.
4. Update `schema.ts` in the same change.
5. Write the numbered SQL file. It is applied by hand in the dashboard — never
   programmatically.

**The non-obvious break:** `core/di/DatabaseModule.kt` still has
`fallbackToDestructiveMigration(dropAllTables = false)` behind the hand-written migrations. A
bump with no `Migration` for it **wipes every device** instead of failing, including the
inference queue, whose frames exist nowhere else. From version 23 that is a data-loss
incident, so every bump ships a migration.

**The second non-obvious break:** that same wipe takes `psgc_barangays` with it. Reference
data has to be re-seedable, not just seeded — which is why `PsgcSeeder` gates on the row
count *as well as* the recorded vintage (`data/local/psgc/PsgcSeeder.kt::needsSeed`). A gate on
the vintage alone leaves the picker permanently empty after any future version bump.

## Changing the surveillance map or the barangay picker

**Open:** `../objects/PsgcBarangay.md` · `../objects/Patient.md` (the barangay code lives on the
patient, `supabase/migrations/0001_init.sql:100`) · `tools/psgc/README.md` ·
`tools/geo/README.md`.

**The non-obvious break:** the PSGC vintage is pinned, and the code list and any boundary
geometry that joins on it — the Admin Website's GeoJSON, and the on-device
`app/src/main/assets/geo/ph-{provinces,towns}-<vintage>.bin` assets `BoundaryRepository` reads
— must come from the **same release**. Move one without the other and the join silently fails
for every unit that changed — it looks like missing data, not a version mismatch. Newer code
lists on the legacy 9-digit PSGC do not join this dataset's 10-digit codes at all.
`GeoDataset.VINTAGE` must equal `PsgcDataset.VINTAGE`; `tools/geo/README.md` "Rebuilding for a
future PSGC vintage change" covers moving both together.

**Also easy to miss:** the barangay code is patient-locating data. The admin aggregation
suppresses figures below a minimum cell size, and that threshold is deliberately not a
parameter. Do not add a barangay to the patient-facing report.

## Changing RLS, auth, or ownership

**Open:** `../objects/Profile.md` · `../objects/StorageObject.md` · `supabase/migrations/0001_init.sql`
(policies from line 352) · `supabase/migrations/0007_patient_shared_history.sql` · `../processes/sync.md`.

**Reads are `author OR assigned OR admin`; writes are the author's.** 0007 adds a second
permissive SELECT policy beside each author-only one — sessions, samples, detections, findings,
predictions, reports and both buckets — so assignment through `patient_users` opens a patient's
whole history. Nothing from 0001–0006 is dropped. The phone repeats the rule in SQL, with an
`EXISTS` over `patient_users`, in every session-scoped read (`../processes/sync.md`, "the pull").
**Change one side and not the other** and either the phone shows rows the server refuses to
send, or the server sends rows no screen shows. Cross-patient views — Home, the dashboard, the
Records tab, the pending counts — stay the medtech's own work on purpose.

**Reading a colleague's row is not permission to change it.** Writes stay author-only on the
server, so the phone treats a colleague's session and sample as read-only: one rule,
`domain/model/RecordAuthorship.kt::isColleagueRecord`, enforced in `SessionManager::resumeSession`,
`OpenVerificationTargetUseCase`, `SubmitVerificationUseCase`, `DeleteQueueItemsUseCase` and
`SessionsViewModel::onRenameSession`, with the screens hiding the actions first. **A new write
path on a session or sample needs the same check**, or its edit is written locally and silently
refused on push. The push queues filter on `user_id`, so a colleague's row can never be pushed.

In the consolidated schema every table's admin path, `reports` included, resolves through
`public.is_admin(uuid)` (`supabase/migrations/0001_init.sql:58`, `:493-495`). The dev and prod
projects still run the legacy history, where `reports` reintroduced an inline
`(select role from profiles …)` subquery (`supabase/migrations/legacy-dev/0008_reports.sql:36-38`).
**A policy change made on those projects has to patch both styles or admin reads diverge by
table.**

**The non-obvious break:** the Storage object key *is* the permission check. The INSERT policy
compares `(storage.foldername(name))[1]` against `auth.uid()`
(`supabase/migrations/0001_init.sql:506-511`), and the client builds that path from the
signed-in user (`data/supabase/SampleRemoteDataSource.kt::syncSample`, `storagePath`). Change
either and every upload 403s.

## Changing sign-in, sign-out, or the login gate

**Open:** `../processes/sign-in.md` · `../objects/Profile.md`.

**The non-obvious break:** the gate reads the **cached** identity, not live auth, and only on
first run (`ResolveAuthGateUseCase`). Gate on `isAuthenticated()` instead and a medtech offline
with an expired token is locked out of their own device.

**The second one:** sign-out **deletes** the medtech's unsynced patients, sessions, samples and
reports (`DiscardUnsyncedDataUseCase`), and it must run before `signOut` clears the id it is
scoped by. Reorder `SignOutUseCase` and it silently discards nothing — and leaves another
medtech's rows stranded on the device.

**The third one:** a cached identity with no Supabase session reads as "the server refused this
account", and the phone wipes its synced data (`SupabaseAccountAccessRepository::checkAccountAccess`,
14zcqntjph8). Anything that leaves the app in that state on purpose — reordering
`SupabaseAuthRepository.kt::signOut` so the session goes first, a sign-in that caches the
identity before the session exists — wipes a working medtech's phone. And widening
`accessForRenewalStatus` past the server's own refusals wipes phones whenever the server is busy.
Narrowing `AccountWipeDao` back to "the account's rows" would discard unsynced field work every
time a medtech changes their password on another device.

**Changing a password signs out every other device of the account** (Supabase revokes their
sessions, 14zcqntjph9), and those devices go through the wipe above: synced data removed,
unsynced work kept, the login screen asking for the new password. On the phone that made the
change, `SupabaseAuthRepository.kt::changePassword` and `checkAccountAccess` share
`AuthSessionLock`. Drop it and a renewal of the old session, in flight while the password
changes, is refused and wipes the phone that changed it.

## Changing patients or the patient form

**Open:** `../processes/register-patient.md` · `../objects/Patient.md`.

**The non-obvious break:** a patient is visible only through `patient_users`. The local insert
writes the patient and its creator link in one transaction
(`PatientDao::insertPatientWithCreatorLink`); split them and a freshly saved patient vanishes
from its own creator's list.

**Also easy to miss:** a codenamed patient keeps the codename in `lastname` and `''` in
`firstname`, and every screen tells the two apart with `CodenameGenerator.isCodename`. A new
codename shape must still match that regex, and so must every codename already stored.

## Changing sessions or the active session

**Open:** `../processes/session-lifecycle.md` · `../objects/Session.md`.

**The non-obvious break:** capture files each frame under `SessionState.Active`'s id, and that
id is restored at launch from `ActiveSessionIdStore`. Anything that can leave the pointer on the
wrong smear files one patient's images under another's.

**Also easy to miss:** label uniqueness per patient is enforced on the device only (Room index
plus the ViewModel pre-check); Postgres does not constrain it.

## Changing the Home dashboard or My coverage

**Open:** `domain/usecase/home/` · `domain/usecase/coverage/` · `../objects/PsgcBarangay.md`.

**The non-obvious break:** Home counts come from their own queries —
`SessionRepository.observeSessionOutcomesBetween`, `DetectionRepository.observeModelRulingsBetween`
and `observeSessionFindingsBetween` — not from the report pipeline, so a change to the report's
counting rule does not move the Home tiles, and vice versa.

## Changing the capture pipeline

**Open:** `../processes/capture.md` · `core/camera/FrameSampler.kt` · `core/camera/CameraManager.kt`.

**The non-obvious break:** the flagged queue is **Room-backed**, not in-memory
(`data/repository/FlaggedFrameStore.kt::state`), and it is filtered by `user_id`, so it is
invisible on a device that has never signed in. Also: there is no `ImageCapture` use case —
the shutter reads a cached JPEG from the analysis stream (`ui/capture/CaptureViewModel.kt::onCapture`),
so anything that stops populating `FrameSampler.latestFrame` breaks capture without touching
capture code.

**The other one:** that cache is process-scoped and is **never reset**, so it routinely holds a
frame from a previous session or a previous camera binding. What keeps it safe is the freshness
stamp on `CachedFrame` and the age check in `CaptureViewModel.onCapture` — slow the analyzer
down, drop the stamp, or widen `MAX_FRAME_AGE_MS` and you reopen a clinical-data-integrity
fault, not a UI glitch: one patient's image filed under another's session, permanent once
verified (C8). `FrameSampler` must not gain session knowledge to compensate; the fix lives at
the read end on purpose (86d4au2n1).

## Changing the inference contract

**Open:** `../processes/infer.md` · `data/remote/dto/InferenceResponseDto.kt` ·
`inference/server.py` · `data/local/mapper/VerificationMapper.kt`.

Client and server must move together, and they are versioned independently — the server is a
container image, the client is an APK.

**The non-obvious break:** coordinates are **centre-x, centre-y, width, height in pixels**
(`box.xywh` in `inference/server.py::infer`), copied verbatim into `bbox_*`
(`data/local/mapper/VerificationMapper.kt::toDetectionEntities`), despite the KDoc on
`DetectionEntity` and the comment at `supabase/migrations/0001_init.sql:232` claiming they are
normalised. And renaming a model
class silently changes every verdict and every LPF grouping, because
`EggSpecies.fromClassLabel` matches on the literal string
(`domain/model/EggSpecies.kt`).

## Changing validation logic

**Open:** `../processes/validate.md` · `data/local/mapper/VerificationMapper.kt::computeVerdict` ·
`../objects/Detection.md` · `../objects/Finding.md` · `../../constraints.md` C7 and C8.

The whole clinical decision rule is one short `when` in one function. Treat it as the most
sensitive code in the repo.

**The non-obvious break:** verdicts are lowercase in Room and uppercase in Postgres
(`domain/model/DetectionVerdict.kt::DetectionVerdict`). Adding a value means the enum, the
Postgres CHECK, **and** every raw SQL string that names a verdict, in `DetectionDao`,
`CoverageDao`, `PatientDao` and `SessionDao` — `grep -n "false_positive'\|'confirmed'"
app/src/main/java/com/agarthavision/data/local/dao/*.kt` lists them. Those do not agree with each
other today: most count everything that is not `false_positive`, while
`SessionDao::observeSessionsPage` (the Sessions list egg total) counts only `confirmed` — see
[`validate`](../processes/validate.md#the-inconsistency-worth-knowing).

**The second one:** species-first counting. An added species row carries the field's **total**
(`VerificationAnswers.fieldTotal`), and `List<Finding>.toDetectionEntities` writes one detection
row per unboxed egg, keyed by species and slot. A submit at a lower total prunes the added slots
it no longer writes (`SubmitVerificationUseCase::invoke`); prediction-backed `#box#` rows are
never pruned.

## Changing sync

**Open:** `../processes/sync.md` · `data/supabase/SyncSampleUseCase.kt` ·
`domain/usecase/sync/SyncPendingDataUseCase.kt` · `Session.md`.

Push order is FK-safe and not incidental: patients → sessions → samples → reports.

**The non-obvious break:** sync is scheduled, not called. Everything except login goes through
`SyncScheduler.requestSync()` and lands in `data/sync/SyncWorker`, so a change here runs on
WorkManager's thread with WorkManager's retry policy, not on the caller's scope. Login is the
one direct awaited call and is deliberately so.

`WorkManagerSyncScheduler.isSyncing` is a **cold** flow and must stay one. Built eagerly it
touches `WorkManager.getInstance()` while Hilt is still field-injecting `AgarthaVisionApp`,
which calls back for a `HiltWorkerFactory` that the same pass has not assigned yet, and the app
dies at launch.

`ui/sessions/SessionsViewModel` reads `isSyncing` via `domain/usecase/sync/ObserveSyncInProgressUseCase`
to hold back a zero-count Sessions display while a sync is running, so changing what counts as
"syncing" affects the Sessions empty state.

**Corrected 86d4brr1f:** this section used to say a failed row waits for "login, app start while
authenticated, or connectivity returning". Login was real; **app start did not exist** until
86d4brr1f added it, and **connectivity returning still does not** — nothing observes
`ConnectivityObserver` to trigger a pass. The network constraint on the work request is the
closest thing, and it gates a pass that was already requested rather than starting one.

## Changing report generation or LPF density

**Open:** `../processes/report.md` · `Report.md` ·
`domain/usecase/records/GenerateSessionReportUseCase.kt` · `domain/usecase/reports/LpfAggregation.kt`.

**The non-obvious break:** egg count's real definition is the WHERE clause of
`data/local/dao/DetectionDao.kt::getConfirmedEggCountsForSession`, which counts every detection that is **not** a false
positive (`d.verdict != 'false_positive'`) — so `WRONG_CLASS` and `BOX_INCORRECT` boxes count as
eggs. Findings are aggregated per species across all session fields into an LPF `min..max` range
via `aggregateLpfPerSpecies`. Reports are snapshots and are never recomputed, so changing the
aggregation or qualitative descriptor rule changes future numbers only.

Report generation also **requires a signed-in identity** (`requireNotNull(currentLocalUserId())`
in `GenerateSessionReportUseCase::invoke`). The id is the cached one, so it works offline, but a
device that has never signed in cannot generate a report.

## Changing UI, theme, or design tokens

**Open:** `../../constraints.md` C11 · `ui/theme/` ·
`../../file-tree.md`.

Raw hex belongs only in the palette definition. Screens read `AgarthaTheme.colors.*` so both
modes resolve.

**The non-obvious break:** Capture is theme-exempt and stays dark regardless of the toggle. A
change that assumes every screen follows the preference will look correct everywhere except
the screen the product is actually about.

## Changing the build, versions, or tooling

**Open:** `../../stack.md` · `../../commands.md` · `gradle/libs.versions.toml`.

**The non-obvious break:** the `pre-commit` hook runs a full `assembleDebug` plus tests plus
lint, so any change that slows the build slows every commit. Pull requests are gated by GitHub
Actions CI (`.github/workflows/build-and-test.yml`), which runs `:app:verifyRoborazziDebug`.

## Changing commit, branch, or PR conventions

**Open:** `../../non-negotiables.md` · `../../constraints.md` C9.

**The non-obvious break:** `.husky/commit-msg` enforces the commit subject line format, but
branch names, ticket structure, and PR conventions are checked by review only. The committed
`commitlint.config.js` encodes a *conventional-commit* shape that contradicts the documented
`[type][ClickUp-ID][Lastname]` format while being wired to no hook.

