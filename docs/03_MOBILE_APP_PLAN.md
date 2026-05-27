# AgarthaVision · Mobile App Implementation Plan (Phase 1 MVP)

> Sprint-by-sprint plan for the Android application. **Phase 1 MVP** ships
> continuous-video capture with a self-hosted FastAPI inference container and
> Supabase sync. Phase 2 (owned-hardware inference, DOH-formatted reports)
> is out of scope.
>
> Authority: [AGENTS.md](../AGENTS.md), [ADR-002](adr/002-supabase-and-roboflow-for-mvp.md),
> and [ADR-003](adr/003-self-hosted-inference-container.md) govern the architectural
> choices in this doc. Cloud architecture in
> [04_CLOUD_BACKEND_PLAN.md](04_CLOUD_BACKEND_PLAN.md).

---

## Sprint Roadmap

The Phase 1 MVP collapses the original 6-sprint plan into 3 working sprints + Sprint 0
scaffold. Validation is no longer a separate sprint — it's the verification step inside
the capture flow.

| Sprint | Duration | Focus                                                              |
|--------|----------|--------------------------------------------------------------------|
| 0      | 1 week   | Project scaffold, CI, theming, navigation, Supabase + Inference keys |
| 1      | 3 weeks  | Auth + continuous capture + cloud inference + verify + sync         |
| 2      | 2 weeks  | Records browser + session reports + CSV export                      |
| 3      | 1 week   | Polish, integration testing, bug sweep, demo prep                   |

---

## Sprint 0 — Scaffold and Foundation

### 0.1 Project Initialization

1. Clone the repo and set up the Gradle structure from [02_PROJECT_ARCHITECTURE.md](02_PROJECT_ARCHITECTURE.md).
2. Add all dependencies via the version catalog.
3. Verify `bun run build` succeeds with an empty `MainActivity`.

### 0.2 KomoUI Theme Integration

1. Create `ui/theme/Color.kt` (from [05_DESIGN_SYSTEM_KOMOUI.md](05_DESIGN_SYSTEM_KOMOUI.md) §2) containing `AgarthaLightStyles : KomoStyles`.
2. Create `ui/theme/Radius.kt` (§4).
3. Create `ui/theme/Type.kt` — bundle Geist and JetBrains Mono TTFs in `res/font/`.
4. Create `ui/theme/Theme.kt` exposing `AgarthaVisionTheme`:

```kotlin
@Composable
fun AgarthaVisionTheme(content: @Composable () -> Unit) {
    KomoTheme(
        isDarkTheme         = false,
        komoLightColors     = AgarthaLightStyles,
        komoDarkColors      = AgarthaLightStyles,
        materialLightColors = AgarthaMaterialColorScheme,
        materialDarkColors  = AgarthaMaterialColorScheme,
        komoRadius          = AgarthaRadius,
        typography          = AgarthaTypography,
        content             = content,
    )
}
```

5. Wrap `MainActivity.setContent` with `AgarthaVisionTheme`.

### 0.3 Supabase + Inference Configuration

1. Use the Supabase project DMKuZu provisioned (URL + anon key shared via team chat — see [04_CLOUD_BACKEND_PLAN.md](04_CLOUD_BACKEND_PLAN.md) §8).
2. Use the inference container DMKuZu deployed (Bearer key + droplet IP shared via team chat — see [ADR-003](adr/003-self-hosted-inference-container.md)).
3. `BuildConfig` fields in `app/build.gradle.kts` read from `local.properties`:

```kotlin
buildTypes {
    debug {
        buildConfigField("String", "SUPABASE_URL",      "\"${project.findProperty("SUPABASE_URL_DEV")      ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${project.findProperty("SUPABASE_ANON_KEY_DEV") ?: ""}\"")
        buildConfigField("String", "INFERENCE_URL",     "\"${project.findProperty("INFERENCE_URL_DEV")     ?: ""}\"")
        buildConfigField("String", "INFERENCE_API_KEY", "\"${project.findProperty("INFERENCE_API_KEY")     ?: ""}\"")
    }
    release {
        buildConfigField("String", "SUPABASE_URL",      "\"${project.findProperty("SUPABASE_URL_PROD")      ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${project.findProperty("SUPABASE_ANON_KEY_PROD") ?: ""}\"")
        buildConfigField("String", "INFERENCE_URL",     "\"${project.findProperty("INFERENCE_URL_PROD")     ?: ""}\"")
        buildConfigField("String", "INFERENCE_API_KEY", "\"${project.findProperty("INFERENCE_API_KEY")      ?: ""}\"")
    }
}
```

Real secrets live in `local.properties` (gitignored) — see [01_ENVIRONMENT_SETUP.md](01_ENVIRONMENT_SETUP.md) §B.6.

### 0.4 Navigation Shell

1. Create `ui/navigation/AgarthaNavGraph.kt` with route definitions.
2. Wire up a `NavHost` in `MainActivity` with placeholder routes: `Login`, `Capture`, `Records`, `Settings`.
3. Phone navigation: bottom nav bar (Capture · Records · Settings) — only visible after login.

### 0.5 Room Database Shell

1. Create `core/database/AgarthaDatabase.kt` with `@Database(entities = [SampleEntity::class, DetectionEntity::class, SessionEntity::class], version = 1, exportSchema = true)`.
2. Create the three entity stubs in `data/local/entity/` with field shells matching §5 of this doc.
3. Create stub DAOs in `data/local/dao/`.
4. Run `bun run build` to verify Room compiles + schema exports to `app/schemas/`.

### 0.6 Hilt Setup

1. Create `AgarthaVisionApp.kt` with `@HiltAndroidApp`.
2. Create `DatabaseModule` and `SupabaseModule` in `core/di/`.
3. Annotate `MainActivity` with `@AndroidEntryPoint`.

### Acceptance Criteria — Sprint 0

- App launches and shows the Login screen placeholder.
- Supabase + Inference env vars are wired (debug build can `println(BuildConfig.SUPABASE_URL)` and `println(BuildConfig.INFERENCE_URL)` without crash).
- `bun run build` and `bun run lint` pass.
- Room schema `1.json` exists in `app/schemas/`.

---

## Sprint 1 — Auth + Continuous Capture + Inference + Verify + Sync

> The core MVP loop. A medtech logs in, starts a recording session, frames flow to
> the inference container at 1 fps every 2 seconds, detections surface as Sonner toasts, the medtech
> taps to verify, verified samples sync to Supabase.

### Key Deliverables

- `LoginScreen` + `LoginViewModel` (Supabase Auth)
- `CaptureScreen` + `CaptureViewModel` (continuous-frame `ImageAnalysis`)
- `VerificationSheet` + `VerificationViewModel`
- `InferenceApi` (Retrofit + OkHttp) — talks to the self-hosted FastAPI container
- `SupabaseSyncManager`
- Room entities: `SampleEntity`, `DetectionEntity`, `SessionEntity`

### 1.0 Login Screen

First screen after splash. Email + password.

| Field        | Source                                                |
|--------------|-------------------------------------------------------|
| Email        | `TextField`, validates with simple regex              |
| Password     | `TextField` with `KeyboardType.Password`              |
| Submit       | `Button(lg, primary)` — "Log in"                      |

Behavior:
- On submit, `LoginViewModel` calls `client.auth.signInWith(Email)`.
- On success: persist session (handled by `supabase-kt`), navigate to `Capture`.
- On failure: `Sonner` with the error message.
- On cold start with a valid persisted session: skip Login, go straight to `Capture`.

There is no sign-up flow in MVP. Accounts are provisioned manually in the Supabase dashboard by an admin. Document this in [04_CLOUD_BACKEND_PLAN.md](04_CLOUD_BACKEND_PLAN.md) §6.

### 1.1 Session Lifecycle

A `SessionManager` (`@Singleton`, app-scoped) tracks the active recording session:

```kotlin
@Singleton
class SessionManager @Inject constructor(
    private val supabase: SupabaseClient,
    private val sessionDao: SessionDao,
    private val deviceIdProvider: DeviceIdProvider,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    suspend fun startSession(): SessionEntity { /* ... */ }
    suspend fun stopSession()                  { /* ... */ }
}

sealed interface SessionState {
    data object Idle : SessionState
    data class Recording(val session: SessionEntity, val startedAt: Instant) : SessionState
}
```

`startSession()`:
1. Generate UUID for `session_id`.
2. Insert `SessionEntity` locally with `started_at = Instant.now()`.
3. Insert the same row into Supabase `sessions` table via `supabase-kt`.
4. Transition state to `Recording`.

`stopSession()`: set `ended_at`, update locally + remotely.

### 1.2 CameraX Integration

`core/camera/CameraManager.kt` exposes a single use case: bind preview + start frame analysis.

```kotlin
@Singleton
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun bindAnalysis(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: ImageAnalysis.Analyzer,
    ): Camera {
        val provider = ProcessCameraProvider.getInstance(context).await()

        val preview = Preview.Builder().build().apply {
            surfaceProvider = previewView.surfaceProvider
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(Size(640, 640))      // matches inference container input
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .apply {
                setAnalyzer(Dispatchers.IO.asExecutor(), analyzer)
            }

        provider.unbindAll()
        return provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageAnalysis,
        )
    }
}
```

Note: this is `ImageAnalysis`, **not** `ImageCapture`. There is no shutter button in the MVP — the capture is continuous while a session is recording.

### 1.3 Frame Sampling

A `FrameSampler` is the `ImageAnalysis.Analyzer` implementation. It enforces the 1-frame-per-2-seconds throttle and dispatches sampled frames to `InferFrameUseCase`.

```kotlin
class FrameSampler @Inject constructor(
    private val inferFrameUseCase: InferFrameUseCase,
    private val sessionManager: SessionManager,
) : ImageAnalysis.Analyzer {

    private val intervalMs = 2_000L
    @Volatile private var lastSentAt = 0L
    @Volatile private var inFlight = false

    override fun analyze(image: ImageProxy) {
        val state = sessionManager.state.value
        if (state !is SessionState.Recording) { image.close(); return }

        val now = System.currentTimeMillis()
        if (now - lastSentAt < intervalMs || inFlight) { image.close(); return }

        lastSentAt = now
        inFlight = true

        val jpegBytes = image.toJpegBytes()    // extension fn; encodes from YUV/RGBA
        image.close()                          // CRITICAL — backpressure relies on this

        CoroutineScope(Dispatchers.IO).launch {
            try {
                inferFrameUseCase(state.session.id, jpegBytes)
            } finally {
                inFlight = false
            }
        }
    }
}
```

`SAMPLE_INTERVAL_MS = 2_000L` is a single tunable constant. Do not bake it in elsewhere.

### 1.4 Inference Call

`InferFrameUseCase` is the only thing that talks to the inference container. The
container endpoint is `POST /infer` with a raw JPEG body and a `Bearer` token
header (injected by the OkHttp interceptor in `InferenceModule`). See
[ADR-003](adr/003-self-hosted-inference-container.md).

```kotlin
class InferFrameUseCase @Inject constructor(
    private val inference: InferenceApi,
    private val flaggedFrameStore: FlaggedFrameStore,
) {
    suspend operator fun invoke(sessionId: UUID, jpegBytes: ByteArray) {
        val response = runCatching {
            inference.infer(
                image = jpegBytes.toRequestBody("image/jpeg".toMediaType()),
            )
        }.getOrElse {
            // network error → bubble up; CaptureViewModel decides whether to stop the session
            throw InferenceConnectionException(it)
        }

        val predictions = response.body()?.predictions.orEmpty()
        if (predictions.isEmpty()) return    // discard frame, no toast, no storage

        flaggedFrameStore.add(
            FlaggedFrame(
                sessionId   = sessionId,
                capturedAt  = Instant.now(),
                jpegBytes   = jpegBytes,
                predictions = predictions,
            )
        )
    }
}
```

`FlaggedFrameStore` is an in-memory `MutableStateFlow<List<FlaggedFrame>>`. It's *not* the Room database — flagged frames only become Room rows after the user verifies them. The store is cleared on logout or new-session start; flagged-but-unverified frames do not survive process death.

> **Phase 1 vs Phase 2 — persistence scope.** Phase 1 flagged frames are deliberately
> **transient**: they live only in memory and do **not**
> survive logout or app restart. The expectation that "I should see my previous
> session's flagged frames after logging back in" is a **Phase 2** deliverable
> (per-account persistent queue keyed by `user_id`, surviving restarts). Phase 1
> keeps the surface area small — only **verified** samples graduate to Room and
> can be revisited later (Sprint 2 Records screen).

### 1.5 Detection Toasts

The Capture screen observes `flaggedFrameStore.state` and shows a `Sonner` toast each time a new flagged frame arrives:

```
"egg detected · 0.91 · Ascaris lumbricoides   [view]"
```

Tapping "view" opens the `VerificationSheet` (bottom sheet, snap point 95%) at the most recent flagged frame.

If multiple frames flag in quick succession, the toasts collapse — `Sonner` debounces 1.5 seconds.

#### Persistent verification badge + Queue Sheet

The top-app-bar verification action (`FactCheck` icon) is **always visible**, not
gated on queue-non-empty — it functions like a persistent inbox/hamburger
affordance. When the queue is empty the count badge bubble is hidden but the icon
stays interactive. The badge bubble appears when `flaggedFrames.size > 0` and
shows the unverified count.

Tapping the badge:
1. Stops the recording session (parity with toast-tap).
2. Opens the **`VerificationQueueSheet`** — a `Drawer` (bottom sheet) listing every
   entry in `FlaggedFrameStore.state`. Each row: thumbnail · top-prediction label
   · confidence · captured-at time · trailing delete `IconButton`.
3. Tapping a row dismisses the queue sheet and opens `VerificationSheet` for that
   frame. Tapping the row's trash icon removes the frame from the store (used for
   duplicates the expert has already verified upstream).
4. The Sonner toast remains the primary "new detection" surface during recording;
   the queue sheet is the catch-up surface when the expert is ready to batch-verify.

### 1.6 Verification Sheet

> **Verification is human-in-the-loop correction, not a binary accept/reject.** Per
> [ADR-004](adr/004-verification-as-hitl-correction.md), every flagged sample
> persists — including ones the expert disagrees with — so the dataset captures
> labeled false positives, misclassifications, and box-placement errors for the next
> retraining cycle.

`VerificationSheet` is a `Drawer` (bottom sheet) showing one flagged frame at a time,
with a toggleable bounding-box overlay and a per-detection stepped questionnaire:

```
┌────────────────────────────────────────┐
│  Flagged Frame · 1 of 4                │
├────────────────────────────────────────┤
│                                        │
│   [Frame with DetectionOverlay         │
│    (toggleable bounding boxes)]        │
│                                        │
├────────────────────────────────────────┤
│  Detection 1 of 2                      │
│  Model predicted: Ascaris lumbricoides │
│  Confidence: ████████░░ 0.91           │
├────────────────────────────────────────┤
│  Q1. Is there a parasitic egg          │
│      in this bounding box?             │
│      [▼ Yes]                           │
│                                        │
│  Q2. Is the bounding box               │
│      correctly placed?                 │
│      [▼ Yes]                           │
│                                        │
│  Q3. What species?                     │
│      [▼ Ascaris lumbricoides]          │
│      (Other → free-text field appears) │
├────────────────────────────────────────┤
│  [Previous detection] [Next detection] │
│                                        │
│  Q4 (frame-level, optional):           │
│  Did the model miss any eggs?          │
│  [▼ No]                                │
├────────────────────────────────────────┤
│  [Cancel]                  [Submit]    │
└────────────────────────────────────────┘
```

Opening the sheet **stops the recording session** (per ADR-002 §UX). The capture window
remains visible behind the sheet.

#### Question flow (per detection)

Each model-predicted bounding box gets its own three-question pass. Answers branch as follows:

| Q | Prompt | Options | Branching → `verdict` |
|---|---|---|---|
| Q1 | "Is there a parasitic egg in this bounding box?" | Yes / No | `No` → `FALSE_POSITIVE`, skip Q2/Q3 |
| Q2 | "Is the bounding box correctly placed?" | Yes / No | `No` → `BOX_INCORRECT`, skip Q3 |
| Q3 | "What species?" | Ascaris lumbricoides / Trichuris trichiura / Hookworm / Other (free text) | If `expert_class ≠ model_class` → `WRONG_CLASS`. Else → `CONFIRMED`. |

Optional frame-level Q4 (one per frame, not per detection):

| Q | Prompt | Options | Effect |
|---|---|---|---|
| Q4 | "Did the model miss any eggs in this frame?" | Yes / No | `Yes` sets `samples.needs_reannotation = true` so the sample is queued for offline annotation. Does not block submit. |

In-app drawing of new or corrected bounding boxes is **out of scope for Phase 1** —
`BOX_INCORRECT` and Q4=Yes samples are re-annotated offline (CVAT / Label Studio)
before retraining.

#### Submit / cancel actions

| Action | Effect |
|---|---|
| Submit | Persist `SampleEntity` to Room (status `VERIFIED`) with one `DetectionEntity` per box, each carrying its expert `verdict` + `expert_class`. Enqueue Supabase upload regardless of verdict mix. |
| Cancel | Sheet closes; flagged frame stays in `FlaggedFrameStore`; session stays stopped; expert can revisit later from the verification chip. |
| Next / Prev (frame) | Chevrons next to the "Flagged Frame · N of M" header. Navigate between flagged frames in the queue. Per-detection answers stay scoped to the frame they were entered against. Clamps at queue edges. |
| Next / Prev (detection) | Buttons under the frame image. Navigate between detections **within** the current frame. Independent from frame-level navigation. |
| Delete frame | Destructive button next to Cancel/Submit (Coral). Removes the current frame from `FlaggedFrameStore` after an `AlertDialog` confirmation. If the queue is non-empty afterwards, the sheet auto-advances to the next frame; otherwise the sheet dismisses. Use case: the expert recognises a duplicate of a previously-verified sample and wants to discard without persisting. |

Submitting a frame where **every** detection is `FALSE_POSITIVE` still results in a
persisted sample — that's the labeled-false-positive case, not a deletion. Reports
later filter on the `verdict` column to compute precision and per-species accuracy.

#### Q1 wording is canonical (do not paraphrase)

Q1 must read **"Is there a parasitic egg in this bounding box?"** — not
"in this frame". The verdict model in
[ADR-004](adr/004-verification-as-hitl-correction.md) is **per-detection** (one
verdict per box). Rephrasing to "frame" collapses the per-box semantic and breaks
the `FALSE_POSITIVE` / `BOX_INCORRECT` / `WRONG_CLASS` / `CONFIRMED` branching.
If "this bounding box" reads ambiguously on screen, the box-overlay rendering bug
is the cause — fix the overlay, not the copy.

#### Deferred UX improvements

The following are not in Sprint 1 scope and live here so Sprint 2+ can pick them up:

- **Lift Sonner above persistent bottom controls.** On `CaptureScreen` the default
  bottom-center Sonner overlays the Stop Recording button. Either pad the host by
  `cardPadding * 2` (≈ 48 dp) or move it to top-center for screens that have a
  persistent action button. Cross-referenced in
  [05_DESIGN_SYSTEM_KOMOUI.md](05_DESIGN_SYSTEM_KOMOUI.md) §"Sonner (toast)".
- **Swipeable pager between flagged frames inside `VerificationSheet`.** Frame-level
  prev/next chevrons cover the explicit case today; a horizontal pager would
  feel smoother but is out of scope until the queue sheet pattern has been
  observed in real medtech use.

### 1.7 Sample Lifecycle

Per [ADR-004](adr/004-verification-as-hitl-correction.md), rejection is no longer a
terminal "deleted" state — it's a `verdict = FALSE_POSITIVE` on a persisted detection.
The sample-level lifecycle is now strictly forward-only once flagged:

```
┌─────────────────┐
│  CANDIDATE      │  In-memory only. The current frame being analyzed.
└────────┬────────┘
         │
         ├─ no model detection → discarded (no trace)
         │
         └─ predictions non-empty
                 ▼
         ┌─────────────────┐
         │  FLAGGED        │  In-memory only (FlaggedFrameStore).
         │  (pending)      │  Surfaces in Sonner toast + Verification queue.
         └────────┬────────┘
                  │ expert submits VerificationSheet
                  ▼
         ┌──────────────────────────────────┐
         │  VERIFIED                        │  In Room.
         │  - 1 SampleEntity                │  Each DetectionEntity carries a
         │  - N DetectionEntity rows,       │  per-box verdict ∈
         │    each with its own verdict     │  {CONFIRMED, FALSE_POSITIVE,
         └──────────────┬───────────────────┘    WRONG_CLASS, BOX_INCORRECT}.
                        │ enqueued for sync
                        ▼
                 ┌──────────────┐
                 │   SYNCED     │  In Room + persisted in Supabase
                 └──────────────┘  (samples + detections rows).
```

There is **no `REJECTED` sample state**. A "rejected" detection is just a persisted
`DetectionEntity` with `verdict = FALSE_POSITIVE`. The sample-level decision "is this
a positive finding?" is answered at read time by inspecting the child detections.

Encode the sample state as `domain/model/SampleStatus.kt`:

```kotlin
enum class SampleStatus(val value: String) {
    FLAGGED("flagged"),               // in FlaggedFrameStore (in-memory only)
    VERIFIED("verified"),             // in Room, awaiting Supabase sync
    SYNCED("synced"),                 // in Room + Supabase
    SYNC_FAILED("sync_failed"),       // in Room, last sync attempt failed
}
```

Encode the per-detection verdict as `domain/model/DetectionVerdict.kt`:

```kotlin
enum class DetectionVerdict(val value: String) {
    CONFIRMED("confirmed"),           // expert agrees on existence, box, and class
    FALSE_POSITIVE("false_positive"), // expert says no egg in this box
    WRONG_CLASS("wrong_class"),       // egg present but model picked the wrong species — see expert_class
    BOX_INCORRECT("box_incorrect"),   // egg in frame but box is misplaced; queued for offline re-annotation
}
```

`DetectionEntity` gains two new columns to mirror Supabase ([0002 migration](../supabase/migrations/0002_verification_fields.sql)):

- `verdict: String` — non-null, defaults to `CONFIRMED` for migration compatibility
- `expert_class: String?` — nullable; only set when `verdict = WRONG_CLASS`

`SampleEntity` gains one new column:

- `needs_reannotation: Boolean` — non-null, defaults to `false`; set to `true` when Q4 = Yes (false-negative flag)

The CANDIDATE state and any in-flight inference frames never touch Room. The
`FlaggedFrameStore` is purely in-memory and transient; rows graduate to Room only
on Submit.

### 1.8 Supabase Sync

`SyncSampleUseCase` runs immediately on verify (see [04_CLOUD_BACKEND_PLAN.md](04_CLOUD_BACKEND_PLAN.md) §7).

1. Resize the JPEG to 640×640 if it isn't already.
2. Upload to Supabase Storage at `{user_id}/{sample_id}.jpg`.
3. Insert `samples` row.
4. Insert `detections` rows.
5. Update local Room row's status to `SYNCED`.

If any step fails, status becomes `SYNC_FAILED` and a retry is attempted next time a session starts.

### 1.9 Connection-loss Handling

A `NetworkMonitor` (`core/connectivity/NetworkMonitor.kt`) observes **inference-container
connectivity specifically** — not just OS-level network availability. It actively
polls `GET <INFERENCE_URL>/health` every 10 seconds while a recording session is
active. This probe style catches three failure modes a passive Android
`ConnectivityManager` listener would miss:

- The GPU droplet was destroyed (per the cost-control runbook in [`inference/README.md`](../inference/README.md), DMKuZu tears down the droplet after every session)
- The container crashed or OOM'd but the droplet is still reachable
- `INFERENCE_URL` in `local.properties` points at an old or wrong host

When the probe fails twice in a row, or when `InferFrameUseCase` throws
`InferenceConnectionException` (mapped from any Retrofit `IOException` /
`HttpException` ≥ 500):

1. The current recording session stops (`SessionManager.stopSession()`).
2. A persistent `Alert` banner appears: *"Cloud connection lost. Recording stopped."*
3. The capture preview remains live (like the system camera app — user can still see through the lens).
4. Already-flagged-but-unverified frames in `FlaggedFrameStore` remain available — the expert can still open `VerificationSheet` and submit verdicts for them; submission writes to Room locally and queues for Supabase sync.
5. Records / Reports screens stay fully usable (read from Room).
6. A "Resume Recording" button surfaces. Tapping it triggers an immediate `/health` probe; on success the session restarts, on failure the banner persists.

`InferenceConnectionException` lives in `domain/usecase/inference/` and is thrown by a
small mapping helper (e.g. `NetworkErrorMapper`) so `InferFrameUseCase` doesn't have
to handle the Retrofit-to-domain translation inline.

Supabase connectivity is **a separate concern** — losing Supabase (auth or sync)
during a recording does not stop the session; it queues writes for later retry via
`SyncSampleUseCase`. Only inference-container connectivity loss stops recording, because
without inference the camera produces no usable signal.

GPS permission handling is the same as before: requested on first session start; on
denial, GPS fields stay null. No first-capture race because there's no per-capture
permission flow — the dialog appears before recording starts.

### 1.10 Metadata Binding (per VERIFIED sample)

| Field         | Source                                              |
|---------------|-----------------------------------------------------|
| `id`          | `UUID.randomUUID().toString()`                      |
| `session_id`  | `SessionManager.state.session.id`                   |
| `user_id`     | `supabase.auth.currentUserOrNull()?.id`             |
| `captured_at` | `FlaggedFrame.capturedAt` (when the frame analyzed) |
| `verified_at` | `Instant.now()` at verify-tap                       |
| `device_id`   | `DeviceIdProvider.id` (`Settings.Secure.ANDROID_ID`)|
| `gps_*`       | `LocationProvider.getCurrentLocation()` at verify   |
| `storage_path`| Computed at upload                                  |
| `inference_model_version` | Returned by `/infer` (or pinned via `BuildConfig` if the server reports a static version) |

### 1.11 Acceptance Criteria — Sprint 1

- Cold start with no session → shows Login screen.
- Successful login navigates to Capture screen.
- Cold start with valid persisted session → skips Login.
- Starting a recording session activates the camera preview and begins frame sampling at 2-second intervals.
- A frame containing a detectable egg surfaces as a Sonner toast within ~3 seconds.
- Tapping the toast (or the in-app-bar verification chip) opens the Verification sheet **and stops recording**.
- Submitting the Verification sheet writes a row to Room (sample status `VERIFIED`) with one DetectionEntity per box (each carrying its expert `verdict`), and uploads to Supabase (sample status → `SYNCED`) — regardless of verdict mix, per [ADR-004](adr/004-verification-as-hitl-correction.md).
- A frame whose detections are all marked `FALSE_POSITIVE` still persists and syncs — it becomes labeled training data, not a deletion.
- Losing inference-container connectivity mid-session stops recording and shows the "Cloud connection lost" banner; the capture preview stays visible; already-flagged-but-unsubmitted frames remain available in the verification queue.
- Tapping "Resume Recording" after connectivity returns restarts the session.
- GPS coordinates populated on verify if permission granted; null if denied — never throws.

---

## Sprint 2 — Records Browser + Session Reports

> The medtech reviews past sessions, browses verified samples, and exports session
> summaries as CSV for offline filing.

### Key Deliverables

- `RecordsScreen` — list of past sessions (most recent first)
- `SessionDetailScreen` — list of verified samples in a session
- `SampleDetailScreen` — full-size image + detection overlay + metadata
- `ExportSessionUseCase` — generates a CSV per session
- Pagination + offline caching

### 2.1 Records List

Two-level navigation:

```
RecordsScreen (list of sessions)
   └── SessionDetailScreen (list of samples in selected session)
         └── SampleDetailScreen (single sample, full-size image)
```

Each session row:

```
┌────────────────────────────────────────────────────────┐
│  Session 2026-05-22 · 14:22 → 14:51                   │
│  4 verified samples · 2 species                        │
│  Brgy. San Roque · 9.65°N 123.86°E                    │
└────────────────────────────────────────────────────────┘
```

Components: `Card (resting)` · `Badge` · `Skeleton` (during load) · `EpgReadout` (deferred — see §2.3).

### 2.2 Sample Detail

Reuses `MicroscopyViewport` + `DetectionOverlay` from Sprint 1. The image is loaded from Supabase Storage (signed URL) with Coil. Local cache covers offline access.

Tabs: **Image** · **Detections** · **Metadata**.

The **Audit** tab from the old plan is deferred — verification in the MVP is one-shot, no edit history.

### 2.3 Session Report (CSV Export)

`ExportSessionUseCase` queries all verified samples in a session and emits a CSV:

```csv
sample_id,captured_at,verified_at,class_label,confidence,gps_lat,gps_lng,gps_accuracy,storage_path
```

EPG calculations are deferred to Phase 2 — they require:
- A trusted volumetric multiplier (Kato-Katz prep-method dependent)
- Per-species egg counts at session level
- DOH validation of the formula

For Phase 1 MVP, the CSV is a raw export. The medtech aggregates externally.

### 2.4 Acceptance Criteria — Sprint 2

- Records screen lists past sessions, most recent first.
- Tapping a session shows the list of verified samples in it.
- Tapping a sample opens the full image with detection overlay.
- Image loads from Supabase Storage; cached locally for offline re-open.
- "Export session as CSV" produces a valid CSV file in `Downloads/`.

---

## Sprint 3 — Polish, Integration Testing, Demo Prep

### 3.1 End-to-End Flow Test

Walk through the full loop on a real device:

1. Cold start → Login.
2. Start session → 5-minute recording with sample slides under microscope.
3. Verify ~5 flagged frames; reject 2.
4. Stop session.
5. Records → confirm session appears with correct sample count.
6. Open a sample → confirm image + detections + metadata.
7. Export CSV → confirm shape.

### 3.2 Edge Cases

| Scenario                                | Expected behavior                                        |
|-----------------------------------------|----------------------------------------------------------|
| Camera permission denied                | Show explanation; offer Retry button.                    |
| Location permission denied              | Recording proceeds; GPS fields stay null.                |
| Inference times out (> 30s per frame)  | Treat as detection-empty; do not crash.                  |
| Inference returns error (4xx / 5xx)    | Stop session; show "Cloud connection lost" banner.       |
| Supabase upload fails on verify        | Mark `SYNC_FAILED`; retry on next session start.         |
| App killed mid-session                  | Session marked `ended_at = null`; auto-closed on relaunch.|
| Low storage                             | Block session start; show alert.                         |

### 3.3 UI Polish

- All screens use theme tokens (`MaterialTheme.styles.*`).
- Geist for labels and sentences; JetBrains Mono for IDs, timestamps, GPS, confidence.
- No hardcoded colors anywhere except [Color.kt](../app/src/main/java/com/agarthavision/ui/theme/Color.kt).

### 3.4 Acceptance Criteria — Sprint 3

- Full E2E walkthrough succeeds on a Pixel 6 (or equivalent).
- All edge cases in §3.2 produce the expected behavior.
- `bun run lint` and `bun run test` are green.

---

## Room Entity Quick Reference (Phase 1)

The local schema is intentionally smaller than the original SDD's 11-entity model.
Phase 2 will reintroduce the rest as needed.

| Entity              | Purpose                                                                  |
|---------------------|--------------------------------------------------------------------------|
| `SessionEntity`     | One row per recording session. Mirrors the Supabase `sessions` row.      |
| `SampleEntity`      | One row per verified flagged frame. Mirrors Supabase `samples`.          |
| `DetectionEntity`   | One row per detection within a sample. Mirrors Supabase `detections`.    |

Deferred to Phase 2: `UserEntity`, `DeviceEntity`, `InferenceRequestEntity`, `EPGCalculationEntity`, `ValidationRecordEntity`, `ReportEntity`, `ReportSampleEntity`, `SyncQueueEntity`. Several of these are obviated by Phase 1's simpler model (e.g. inference is synchronous so there's no `InferenceRequest`; verification has no edit history so there's no `ValidationRecord`).

---

## What This Doc Replaces (Phase 1)

The previous version planned a 6-sprint, snapshot-capture, own-backend, full-validation
workflow. ADR-002 cuts that down to MVP scope. Specifically dropped from Phase 1:

| Was                                      | Now                                                  |
|------------------------------------------|------------------------------------------------------|
| `BiologicalWindowChip` countdown timer   | Removed entirely. Window enforcement is off-app.     |
| Snapshot capture via `ImageCapture`      | Continuous `ImageAnalysis` sampled at 2-second intervals. |
| Own-backend payload upload pipeline      | Direct self-hosted inference container + Supabase sync from mobile. |
| Separate Validate/Approve/Edit/Reject sprint | Verify / Reject inline during capture.           |
| `SyncQueueEntity` + `SyncWorker` retry   | One-shot retry at next session start. No WorkManager. |
| EPG calculation, false-positive marking  | Deferred to Phase 2.                                  |
| Admin dashboard, DOH PDF reports         | Deferred to Phase 2. CSV export only.                 |

See [ADR-002](adr/002-supabase-and-roboflow-for-mvp.md) and
[ADR-003](adr/003-self-hosted-inference-container.md) for the migration path back
to the full SDD model in Phase 2.
