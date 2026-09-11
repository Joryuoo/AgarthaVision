# Constraints

Thirteen rules this codebase is built on. Each one states what it is, why it exists, and
**what actually enforces it**. Several are enforced by nothing but review — those say so
plainly, and one is measurably not being followed. Terse absolutes live in
[`non-negotiables.md`](non-negotiables.md); this file explains.

Order is stable. `SESSION_INIT.md` lists C1–C13 by name.

---

## C1 — ViewModels call use cases, not the data layer

A ViewModel must not reach Room, Retrofit, or Supabase directly. It calls a use case, exposes
a single `StateFlow<ScreenState>`, and emits one-shot events on a `SharedFlow`.

**Enforcement:** review only. No lint rule, no detekt rule, no CI check. `detekt.yml` covers
complexity, naming, and magic numbers — there is no import-boundary rule in it
(`detekt.yml:1-49`).

**As-built:** the literal rule holds — no file under `ui/` imports `androidx.room`,
`retrofit2`, or `io.github.jan.*`. The spirit is bent in four ViewModels that inject the
data-layer `FlaggedFrameStore` directly (`app/src/main/java/com/agarthavision/ui/capture/CaptureViewModel.kt:9`,
`ui/verify/VerificationViewModel.kt:5`, `ui/verify/VerificationQueueViewModel.kt:5`,
`ui/verify/ManualCaptureViewModel.kt:5`). The composable that once rendered a wire DTO no
longer does — `FrameWithBoxes` takes domain `Prediction` values
(`ui/verify/FrameWithBoxes.kt:15`), and nothing under `ui/` imports from `data/remote/dto/`.

## C2 — `domain/` stays Android-free

Nothing under `domain/` may import an Android API. The domain layer must be unit-testable on
the JVM without Robolectric.

**Scope:** this constrains `domain/` only. It is not a repo-wide ban on Robolectric — the
Compose UI tests under `app/src/test/java/com/agarthavision/ui/verify/` use it deliberately
so they run in `:app:testDebugUnitTest` instead of needing a device. See `commands.md`.

**Enforcement:** review only. **This one actually holds** — zero files under
`app/src/main/java/com/agarthavision/domain/` import `android.*`.

**Caveat:** the domain layer *does* import the data layer in eleven files, which the same
architecture section forbids in spirit — see C3.

## C3 — Repository interfaces in `domain/`, implementations in `data/`

Interfaces live in `domain/repository/`; implementations live in `data/repository/`, wired by
Hilt `@Binds`. Room entities live in `data/local/entity/`, domain models in `domain/model/`,
and mappers convert between them.

**Enforcement:** the Hilt binding module is the only mechanical check, and it only proves the
bindings exist, not that the boundary is respected
(`app/src/main/java/com/agarthavision/core/di/DatabaseModule.kt:77-120`).

**As-built:** the interface/implementation split is clean. The layering below it is not:
eleven `domain/` files import `com.agarthavision.data.*`, including use cases that call DAOs
directly rather than going through a repository —
`domain/usecase/verify/SubmitVerificationUseCase.kt:3-6`,
`domain/usecase/auth/ClaimLocalDataUseCase.kt:3-5`,
`domain/usecase/sync/SyncPendingDataUseCase.kt:4-9`. `ClaimLocalDataUseCase` documents the
deviation and defers the fix to Phase 2 (`domain/usecase/auth/ClaimLocalDataUseCase.kt:16-19`).

## C4 — Use cases return `Result<T>`

A use case has one public entry point (`operator fun invoke` or `suspend fun execute`) and
returns `Result<T>` so the caller handles both branches. Never swallow an exception.

**Enforcement:** review only, and **it is not holding.** Many of the roughly thirty
files under `domain/usecase/` never mention `Result<` — for example
`domain/usecase/reports/SessionEggCountUseCase.kt:19`, which returns a bare data class. Treat
C4 as the target shape for new code, not a description of the existing code. Newer capture code
follows it: `domain/usecase/capture/CaptureFieldUseCase.kt` returns `Result<FrameSource>`.

## C5 — `@Singleton` is a closed list

`@Singleton` is for the database, OkHttp, Retrofit, Gson, the Supabase client, and the
app-scoped services `SessionManager`, `FlaggedFrameStore`, `FrameSampler`, `CameraManager`,
`NetworkMonitor`, `SampleImageStore`. Repositories and use cases are unscoped.

**Enforcement:** review only. The scoped set is visible at
`core/di/DatabaseModule.kt:41-53`, `core/di/InferenceModule.kt:33-76`,
`core/di/SupabaseModule.kt:23-33`, and on the classes themselves
(`core/session/SessionManager.kt:29`, `core/camera/FrameSampler.kt:28`,
`data/repository/FlaggedFrameStore.kt:40`).

## C6 — Migrations own the schema

`supabase/migrations/*.sql` is the authority for Postgres tables, nullability, defaults,
foreign keys, CHECKs, and RLS. Do not change schema behaviour without updating both the
migration SQL **and** `schema.ts`. Migrations are numbered, committed, and run **manually** in
the Supabase dashboard SQL editor — never applied programmatically
(`supabase/migrations/0001_init.sql:2`). Room is a separate mirror: a Room-shape change means
bumping `AgarthaDatabase.version` (`core/database/AgarthaDatabase.kt:44`).

**Enforcement:** review only. There is no migration runner, no schema-diff test, and no CI.
`schema.ts` is documentation and is never compiled (`schema.ts:4-5`).

**Known drift, code wins:** `schema.ts` names `samples.timestamp`, `samples.image_path`,
`samples.created_at`, `samples.gps_lat/gps_lng/gps_accuracy_m`, and `detections.created_at`
(`schema.ts:282-340`, `schema.ts:319`). None of those columns exist in Postgres. The
migration creates `captured_at`, `gps_latitude`, `gps_longitude`, `gps_accuracy` and no
`created_at` (`supabase/migrations/0001_init.sql:43-55`), and the insert row confirms it
(`data/supabase/SampleRemoteDataSource.kt:100-127`). Those `schema.ts` names describe the
Room entity, not Postgres.

## C7 — Human validation gates every AI output

No model output counts as a finding until a human confirms it. This is a clinical
requirement, not a preference: the app is decision support, not a diagnostic authority. The
inference server applies **no confidence filter** — the expert is the threshold. Every
detection carries a per-box verdict from the medtech
(`supabase/migrations/0002_verification_fields.sql:30-32`), computed from the questionnaire at
`data/local/mapper/VerificationMapper.kt:10-17`. A frame only becomes a `Sample` when the
medtech submits (`domain/usecase/verify/SubmitVerificationUseCase.kt:34-44`).

**Enforcement:** structural — there is no code path that writes a `verified` sample without a
submission. That is the strongest enforcement in this list; keep it that way.

## C8 — Nothing is deleted

A rejection is data, not a deletion. Rejected detections persist with
`verdict = FALSE_POSITIVE` so the `detections` table doubles as the retraining corpus. There
is no `REJECTED` sample state.

**Enforcement:** structural, at the storage layer — `0003_storage_rls.sql` deliberately
creates no DELETE policy for the `samples` bucket
(`supabase/migrations/0003_storage_rls.sql:45-46`). Postgres rows are likewise
insert-and-update only (`data/supabase/SampleRemoteDataSource.kt:40-43`).

**Local exception:** unverified flagged frames *can* be discarded on-device before
submission (`data/repository/FlaggedFrameStore.kt:80-99`). Nothing that has been verified is
deletable.

## C9 — Commit and branch format

Commits: `[type][ClickUp-ID][Lastname]: Task title` — note the colon before the title.
Types: `feat enhancements fix security docs ui ux uiux refactor test ci chore`. Branches:
cut from `staging` as `feat/<description>`, `fix/…`, `refactor/…`, `docs/…`, `ci/…`,
`test/…`. PRs target `staging`, never `main`.

**A branch name carries no ClickUp ID.** It describes the work, not the ticket — good:
`feat/verified-findings-reporting`; bad: `feat/86d4a6jwy-verification-logging`. One branch
can carry commits for several tickets, so an ID baked into the name is wrong the moment a
second ticket lands on it, and the ID is already captured per commit by the subject format
above. Nothing enforces this at push time; it is a review check.

**Enforcement:** `.husky/commit-msg` checks the subject line against exactly the type list
above (`.husky/commit-msg:14-17`). Merge, revert, fixup, and squash subjects are skipped
because git writes those itself; only the first line is checked, so bodies are free-form.
A rejected commit prints the format, the type list with a gloss for each, and the subject
that failed.

**History predates the colon.** Every commit before `12509f8` uses the older
`[type][ClickUp-ID][Lastname] Task title` shape with no colon — the hook only sees new
commits, so the log is mixed and that is expected, not drift.

**Caveat:** `commitlint.config.js` is still committed and still extends
`@commitlint/config-conventional` with a scope enum (`commitlint.config.js:1-16`) — a
*conventional-commit* shape that contradicts the bracket format above and is wired to no
hook. `lint-staged.config.js` is likewise unreferenced by any hook. Both are dead
configuration; neither describes what actually runs.

## C10 — Never commit secrets

Supabase URLs, anon keys, and the inference bearer token live in `local.properties`, which is
gitignored (`.gitignore:3`, `.gitignore:15`). They reach the app as `BuildConfig` fields read
at build time (`app/build.gradle.kts:19-21`, `app/build.gradle.kts:47-93`). A missing property
resolves to an empty string rather than failing the build. CI passes them as Gradle `-P`
properties. `local.properties.example` is the committed template and holds placeholders only.

**Enforcement:** `.gitignore` plus review. There is no secret-scanning step, because there is
no CI at all — no `.github/` directory exists in this repository.

**Drift:** `app/build.gradle.kts:67` and `:90` read `INFERENCE_API_KEY_DEV` /
`INFERENCE_API_KEY_PROD`, but `local.properties.example:26` documents a single
`INFERENCE_API_KEY`. Following the example file yields an empty bearer token. The build file
wins.

## C11 — One design system

One theme, two modes. CIT-U maroon + gold, Inter typography, tabular numerals, italic
binomials, 8-px spacing grid, radii `8 / 12 / 16 / 999`. Raw hex appears **only** in the
palette definition; screens read the mode-aware `AgarthaTheme.colors.*` rather than
`AppColors.*` directly, so both modes resolve. Capture is exempt — it stays dark and
immersive regardless of the toggle. No second theme and no charting library: small dataviz
is hand-built inline SVG. Icons are mixed and deliberately so — `material-icons-extended`
(`app/build.gradle.kts:122`) supplies utility glyphs inside screens (chevrons, back arrows,
filter, flag), while the bottom bar, brand marks and anything read as house identity are
hand-authored 1.7-stroke outline drawables in `res/drawable/`. Match the neighbours: a new
tab or brand icon is drawn, a new in-screen affordance may come from Material.

**Enforcement:** review only. The token definitions are the single source —
`ui/theme/Color.kt`, `ui/theme/Palette.kt`, `ui/theme/Spacing.kt`, `ui/theme/Theme.kt`,
`ui/theme/Type.kt`.

## C12 — Build and test before pushing

Compile, unit tests, a debug APK, and lint must pass before a commit lands, and the debug
build must pass again before a push.

**Enforcement:** the strongest mechanical enforcement in the repo. `.husky/pre-commit` runs
`:app:compileDebugKotlin`, `:app:verifyRoborazziDebug`, `assembleDebug`, then
`:app:ktlintCheck :app:detekt`, aborting on any failure. The second step is the full unit
test suite with screenshot goldens compared, not a separate screenshot pass. `.husky/pre-push` runs
`assembleDebug`. Both require a JDK and the Android SDK; both are bypassed by
`git commit --no-verify`, which is the correct move for a docs-only change that touches no
Kotlin, Gradle, or SQL.

## C13 — Code wins over docs

Where a document and the code disagree, the code is right and the document is a bug. Fix the
document in the same change. Never propagate a claim from a document into a card without
opening the file it describes.

**Enforcement:** none mechanical — this is the working rule that makes the rest of the shelf
trustworthy. Every load-bearing claim in `docs/` carries a `path:line` citation precisely so
this rule is checkable.

**Live examples of documents losing:** the `schema.ts` column names in C6; the
`INFERENCE_API_KEY` name in C10; the "flagged frames are transient / in-memory" claim, which
is wrong — `FlaggedFrameStore` is Room-backed
(`data/repository/FlaggedFrameStore.kt:33-34`, `:58-74`); and the EPG counting rule, where the
prose says "confirmed detections" but the query counts everything that is not a false
positive (`data/local/dao/DetectionDao.kt:43`).
