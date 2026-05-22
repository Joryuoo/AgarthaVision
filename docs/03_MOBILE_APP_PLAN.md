# AgarthaVision · Mobile App Implementation Plan

> Feature-by-feature breakdown of the Android application across all four MVP modules.
> Each feature includes its SDD reference, key components, implementation steps, and acceptance criteria.

---

## Sprint Roadmap (suggested)

The implementation is ordered by dependency: you cannot validate a sample that hasn't
been captured, and you cannot generate a report from unvalidated data.

| Sprint | Duration | Focus                                      | Modules  |
|--------|----------|--------------------------------------------|----------|
| 0      | 1 week   | Project scaffold, CI, theming, navigation  | Core     |
| 1      | 2 weeks  | Live image acquisition + local persistence | 1A.1     |
| 2      | 2 weeks  | Payload transmission + sync queue          | 1A.2     |
| 3      | 2 weeks  | Diagnostic result rendering                | 3.1      |
| 4      | 2 weeks  | HITL validation (approve/edit/reject)      | 3.2      |
| 5      | 2 weeks  | Session reports + admin dashboard          | 4        |
| 6      | 1 week   | Polish, integration testing, bug sweep     | All      |

---

## Sprint 0 — Scaffold and Foundation

### 0.1 Project Initialization

1. Clone the repo and set up the Gradle structure from `02_PROJECT_ARCHITECTURE.md`.
2. Add all dependencies via the version catalog.
3. Verify `bun run build` succeeds with an empty `MainActivity`.

### 0.2 KomoUI Theme Integration

1. Create `ui/theme/AgarthaLightColors.kt` (from `05_DESIGN_SYSTEM_KOMOUI.md` §2).
2. Create `ui/theme/AgarthaRadius.kt` (from §4).
3. Create `ui/theme/AgarthaTypography.kt` — load Geist and JetBrains Mono via Google Fonts provider.
4. Create `ui/theme/AgarthaVisionTheme.kt` that wraps `KomoTheme`:

```kotlin
@Composable
fun AgarthaVisionTheme(content: @Composable () -> Unit) {
    KomoTheme(
        shadcnLightColors = AgarthaLightColors,
        shadcnRadius = AgarthaRadius,
    ) {
        MaterialTheme(typography = AgarthaTypography) {
            content()
        }
    }
}
```

5. Wrap `MainActivity.setContent` with `AgarthaVisionTheme`.

### 0.3 Navigation Shell

1. Create `ui/navigation/AgarthaNavGraph.kt` with all `Screen` routes.
2. Create `ui/components/BottomNavBar.kt` — the phone navigation bar.
3. Wire up empty placeholder screens for Capture, Queue, Validate, Reports, Settings.
4. Verify: tapping each bottom nav item navigates to the correct placeholder.

### 0.4 Room Database Shell

1. Create `core/database/AgarthaDatabase.kt` with `@Database` annotation listing all 11 entities from the SDD ERD.
2. Create stub entity classes in `data/local/entity/` (fields match the SDD schema exactly).
3. Create stub DAO interfaces in `data/local/dao/`.
4. Run `bun run build` to verify Room compiles the schema.

### 0.5 Hilt Setup

1. Create `AgarthaVisionApp.kt` with `@HiltAndroidApp`.
2. Create `DatabaseModule`, `NetworkModule` in `core/di/`.
3. Annotate `MainActivity` with `@AndroidEntryPoint`.

### Acceptance Criteria — Sprint 0

- App launches on device/emulator showing the bottom nav with 5 tabs.
- Tapping each tab shows a placeholder screen.
- `bun run build` and `bun run lint` pass.
- Commit messages pass commitlint.

---

## Sprint 1 — Module 1A.1: Live Image Acquisition

> SDD Reference: Module 1, Section 1.1

### Key Deliverables

- `CaptureScreen` — full-screen camera preview with microscopy grid overlay
- `CaptureViewModel` — manages capture state
- `CaptureSampleUseCase` — orchestrates metadata binding + local save
- Room persistence for `SAMPLES` entity

### Implementation Steps

**1.1 CameraX Integration**

Create `core/camera/CameraManager.kt`:

```kotlin
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var cameraProvider: ProcessCameraProvider? = null

    suspend fun bindPreview(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
    ): ImageCapture { /* ... */ }

    suspend fun captureImage(imageCapture: ImageCapture): File { /* ... */ }
}
```

- Use `ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY`.
- Save to app-internal storage: `context.filesDir/captures/{uuid}.jpg`.

**1.2 CaptureScreen Composable**

Components used (from design system):
- `MicroscopyViewport` (custom) — wraps `AndroidView(PreviewView)` with grid overlay
- `Button (lg, primary)` — Capture trigger (pill, thumb-reachable at bottom)
- `Badge` — LIVE indicator + magnification label
- `BiologicalWindowChip` (custom) — countdown timer in app bar
- `Slider` — manual focus assist (if device supports `CONTROL_AF_MODE_OFF`)
- `Sonner` — toast on "Snapshot saved"
- `Drawer` — capture options (exposure, focal step)

Layout:
```
┌────────────────────────────┐
│  App bar: BioWindowChip    │
├────────────────────────────┤
│                            │
│   MicroscopyViewport       │
│   (80% of screen)          │
│   + Grid overlay           │
│   + LIVE badge             │
│                            │
├────────────────────────────┤
│  Recent thumbnail | [  ●  ]│  ← Capture button
│                   | Start  │
└────────────────────────────┘
```

**1.3 Metadata Binding**

On capture, the `CaptureSampleUseCase` collects and binds:

| Field         | Source                                   |
|---------------|------------------------------------------|
| `sample_id`   | `UUID.randomUUID().toString()`           |
| `timestamp`   | `Instant.now()`                          |
| `device_id`   | `Settings.Secure.ANDROID_ID`             |
| `session_id`  | Generated once per app session           |
| `gps_lat/lng` | `LocationProvider` (fused location)      |
| `gps_accuracy`| From location result                     |
| `image_path`  | Local file path                          |
| `status`      | `SampleStatus.CAPTURED`                  |

**1.4 Local Persistence**

Insert a `SampleEntity` into Room with status `CAPTURED`. The image file is stored on disk;
the entity only holds the file path.

**1.5 Recent Capture Thumbnail**

Query the most recent sample from Room and display its thumbnail in the capture screen corner.

### Acceptance Criteria — Sprint 1

- Camera preview fills the viewport on a real device.
- Pressing Capture saves a JPEG to internal storage.
- A new row appears in the Room database with all metadata fields populated.
- GPS coordinates are populated (or gracefully null if location permission is denied).
- The biological window chip counts down from 60 minutes.
- The recent-capture thumbnail updates after each capture.

---

## Sprint 2 — Module 1A.2: Image Payload Transmission

> SDD Reference: Module 1, Section 1.2

### Key Deliverables

- `TransmitPayloadUseCase` — packages and sends the image to the cloud
- `SyncQueueManager` — offline queue with retry
- `SyncWorker` (WorkManager) — background upload
- Payload Processing Status Screen

### Implementation Steps

**2.1 Payload Packaging**

Create `data/remote/dto/PayloadDto.kt`:

```kotlin
data class PayloadDto(
    val sampleId: String,
    val sessionId: String,
    val deviceId: String,
    val timestamp: String,       // ISO 8601
    val gpsLatitude: Double?,
    val gpsLongitude: Double?,
    val gpsAccuracy: Float?,
    val imageBase64: String,     // or multipart — see API contract
)
```

**2.2 Retrofit API**

```kotlin
interface InferenceApi {
    @Multipart
    @POST("v1/inference/submit")
    suspend fun submitPayload(
        @Part image: MultipartBody.Part,
        @Part("metadata") metadata: RequestBody,
    ): Response<InferenceSubmitResponse>

    @GET("v1/inference/{requestId}/status")
    suspend fun getInferenceStatus(
        @Path("requestId") requestId: String,
    ): Response<InferenceStatusResponse>

    @GET("v1/inference/{requestId}/result")
    suspend fun getInferenceResult(
        @Path("requestId") requestId: String,
    ): Response<InferenceResultResponse>
}
```

**2.3 Transmission Flow**

```
CAPTURED → payload_packaged → (network check)
   ├─ online  → UPLOADED → PROCESSING → PROCESSED → PENDING_VALIDATION
   └─ offline → QUEUED_FOR_UPLOAD → (SyncWorker retries later)
```

**2.4 SyncQueueManager**

- Uses Room `SYNC_QUEUE` entity to track pending uploads.
- WorkManager `SyncWorker` runs with `NetworkType.CONNECTED` constraint.
- Retry policy: exponential backoff, max 5 attempts.
- Idempotency: each payload includes `sample_id` as the idempotency key.

**2.5 Status Screen**

After capture, show a processing status screen using:
- `Spinner` — animated loading indicator
- Text label — "Processing Sample" / "Analyzing Sample" / "Analysis Complete"
- `Button (primary)` — "Check Result" (navigates to ValidateScreen)
- `Button (secondary)` — "Back to Camera"

Poll `getInferenceStatus()` every 3 seconds until status is `PROCESSED` or `FAILED`.

### Acceptance Criteria — Sprint 2

- With network: captured sample uploads, receives inference results, status transitions to `PENDING_VALIDATION`.
- Without network: sample enters the sync queue; when network returns, `SyncWorker` uploads automatically.
- Status screen shows real-time progress.
- "Check Result" navigates to the validation screen with the correct sample ID.

---

## Sprint 3 — Module 3.1: Diagnostic Result Rendering

> SDD Reference: Module 2, Section 2.1

### Key Deliverables

- `ValidateScreen` — split view with annotated image and detection metadata
- `ValidateViewModel` — loads and holds diagnostic result state
- `FetchDiagnosticResultUseCase`
- Custom composables: `DetectionOverlay`, `EpgReadout`

### Implementation Steps

**3.1 Data Retrieval**

`FetchDiagnosticResultUseCase` fetches from Room:
- Sample record (image path, metadata)
- Inference result (model version, preprocessing, timing)
- List of detections (bounding boxes, class labels, confidence scores)
- EPG calculations (per species)

Assembles into a `DiagnosticResultState` data class for the ViewModel.

**3.2 ValidateScreen Layout**

```
┌─────────────────────────────────────┐
│  Header: SMP-0429 · PENDING        │
├─────────────────────────────────────┤
│                                     │
│  MicroscopyViewport                 │
│  + DetectionOverlay (bounding boxes)│
│                                     │
├─────────────────────────────────────┤
│  Tabs: Image | Detections |         │
│        Metadata | Audit             │
├─────────────────────────────────────┤
│  ┌ Selected Detection Card ───────┐ │
│  │ Trichuris trichiura            │ │
│  │ Confidence: ████████░░ 0.91    │ │
│  │ EPG: 1,284                     │ │
│  └────────────────────────────────┘ │
├─────────────────────────────────────┤
│  [Validate]  [Edit]  [Reject]       │
└─────────────────────────────────────┘
```

Components used:
- `Card (elevated)` — detection panel
- `MicroscopyViewport` + `DetectionOverlay` (custom)
- `EpgReadout` (custom) — large numeral display
- `Progress` — confidence bar (Clinical Blue fill)
- `Tabs` — Image / Detections / Metadata / Audit
- `Badge (outline)` — species taxonomy tag
- `Popover` — "What is EPG?" inline explainer

**3.3 Detection Overlay**

`DetectionOverlay.kt` renders bounding boxes on a `Canvas` layer over the microscopy image:
- Color-coded by species (using chart tokens)
- Tappable — selecting a box updates the detail card below
- Non-destructive — original image is never modified

**3.4 EpgReadout**

`EpgReadout.kt` — the hero numeral display from the moodboard:
- Display size (56 sp, Geist 500, tabular figures)
- Sub-label: "EPG · TRICHURIS TRICHIURA" in JetBrains Mono 10 sp uppercase

### Acceptance Criteria — Sprint 3

- Opening a processed sample renders the microscopy image with bounding box overlays.
- Tapping a bounding box highlights it and updates the detail card.
- EPG readout displays the calculated value in the correct typography.
- Tabs switch between Image, Detections, Metadata, and Audit views.
- Metadata tab shows detection ID, confidence, coordinates, model version, inference time.

---

## Sprint 4 — Module 3.2: Human-in-the-Loop Validation

> SDD Reference: Module 2, Section 2.2

### Key Deliverables

- Approve, Edit, and Reject workflows
- `ApproveSampleUseCase`, `EditDetectionUseCase`, `RejectFindingsUseCase`
- Detection reclassification and false positive marking
- EPG recalculation after edits
- Validation audit trail (append-only)

### Implementation Steps

**4.1 Approve Flow**

1. Medtech taps "Validate" → confirmation dialog (`AlertDialog`).
2. `ApproveSampleUseCase`:
   - Sets sample status to `VALIDATED`.
   - Creates a `ValidationRecord` with `action = APPROVED`.
   - Triggers background report generation.

**4.2 Edit Flow**

1. Medtech taps a bounding box → taps "Edit".
2. `Detection Edit Panel` (bottom sheet) offers:
   - **Reclassify** — `RadioGroup` with Ascaris / Trichuris / Hookworm / Artifact.
   - **Mark False Positive** — flags the detection, excludes from EPG.
3. On confirm:
   - `EditDetectionUseCase` updates the detection's `class_label`, preserves `original_class_label`.
   - Creates a `ValidationRecord` with `action = RECLASSIFIED` or `action = FALSE_POSITIVE`.
   - `ValidatedEPGRecalculationService` recomputes EPG using only non-flagged detections.
   - UI updates the overlay color and EPG readout in real-time.

**4.3 Reject Flow**

1. Medtech taps "Reject" → `AlertDialog` with rejection reason selector:
   - Excessive false positives
   - Widespread misclassification
   - Duplicate detections
   - Unreliable detection quality
   - Overall AI findings unusable
2. `RejectFindingsUseCase`:
   - Sets sample status to `FINDINGS_REJECTED`.
   - Creates a `ValidationRecord` with rejection reason.
   - Original image and metadata are preserved.

**4.4 Audit Trail**

Every validation action writes to `VALIDATION_RECORDS`:

```kotlin
data class ValidationRecordEntity(
    val id: String,           // UUID
    val sampleId: String,
    val detectionId: String?, // null for sample-level actions
    val userId: String,
    val actionType: String,   // APPROVED, RECLASSIFIED, FALSE_POSITIVE, REJECTED, UNUSABLE
    val previousValue: String?,
    val newValue: String?,
    val rejectionReason: String?,
    val remarks: String?,
    val timestamp: Instant,
)
```

The Audit tab in `ValidateScreen` uses `AuditTimeline` (custom composable) to render this chronologically.

### Acceptance Criteria — Sprint 4

- Approving a sample transitions its status to `VALIDATED` and triggers report generation.
- Reclassifying a detection updates the overlay color and recalculates EPG immediately.
- Marking a detection as false positive removes it from the EPG calculation.
- Rejecting requires a reason; original data is preserved.
- The Audit tab shows a complete chronological history of all actions.

---

## Sprint 5 — Module 4: Reporting and Administration

> SDD Reference: Module 3

### Key Deliverables

- Session-based report generation (for medtechs)
- Administrative aggregated report (for admins)
- PDF export
- Filterable records table

### Implementation Steps

**5.1 Session Report Generation**

`GenerateSessionReportUseCase`:
1. Queries all `VALIDATED` samples in the current diagnostic session.
2. Computes session summary: total captured, verified, excluded, positive, negative.
3. Computes parasite distribution: counts by Ascaris, Trichuris, Hookworm, Mixed.
4. Computes EPG summary: average, highest, lowest.
5. Persists to `REPORTS` and `REPORT_SAMPLES` tables.
6. Triggers PDF export via `PDFExportService`.

**5.2 Reports Screen**

```
┌──────────────────────────────────┐
│  Captured Samples Gallery        │
│  (thumbnail grid of session)     │
├──────────────────────────────────┤
│  [Generate Report] [View Reports]│
├──────────────────────────────────┤
│  Session Summary Card            │
│  Total: 24 · Verified: 22       │
│  Positive: 18 · Negative: 4     │
├──────────────────────────────────┤
│  Parasite Distribution           │
│  Ascaris: 8 · Trichuris: 6      │
│  Hookworm: 3 · Mixed: 1         │
├──────────────────────────────────┤
│  EPG Summary                     │
│  Avg: 842 · High: 2,104         │
│  Low: 124                        │
├──────────────────────────────────┤
│  [Download PDF]                  │
└──────────────────────────────────┘
```

Components: `Card (elevated)` for summary, `DatePicker` for range filter, `Button (outline)` for export.

**5.3 Administrative Dashboard**

For admin users:
- Summary cards: total processed, pending validation, active inference.
- EPG trend chart (using KomoUI Charts with `chart1–5` tokens).
- Detailed records table with filters (date, species, confidence, EPG range, status).
- Export to CSV/JSON.

**5.4 PDF Generation**

Use Android's `PdfDocument` API or a lightweight library like `iText` to generate
DOH-formatted reports. Save to `Downloads/` and offer share intent.

### Acceptance Criteria — Sprint 5

- Generating a session report produces a summary with correct aggregations.
- Only validated samples are included (pending/rejected excluded).
- PDF downloads with correct formatting.
- Admin dashboard shows system-wide statistics.
- Records table filters work correctly.
- Export produces valid CSV/JSON.

---

## Sprint 6 — Integration and Polish

### 6.1 End-to-End Flow Test

Walk through the complete workflow on a real device:
1. Capture → 2. Upload → 3. Wait for inference → 4. Review → 5. Validate → 6. Generate report

### 6.2 Offline Flow Test

1. Capture with airplane mode on.
2. Verify sample enters sync queue.
3. Re-enable network.
4. Verify SyncWorker uploads and receives results.

### 6.3 Edge Cases

- Camera permission denied — show explanation and request again.
- Location permission denied — capture succeeds with null GPS (acceptable per SRS).
- Backend timeout — inference status shows failure; retry available.
- Low storage — prevent new captures, show alert.

### 6.4 UI Polish

- Verify all screens match the Clinical Pulse moodboard.
- Test on different screen sizes (compact phone, large phone).
- Ensure all data text uses JetBrains Mono, all labels use Geist.
- Verify color tokens are used everywhere (no hardcoded hex values).

---

## Room Entity Quick Reference

These map directly to the SDD's unified ERD (11 entities):

| Entity                  | Primary Key     | Key Fields                                              |
|-------------------------|-----------------|---------------------------------------------------------|
| `UserEntity`            | `user_id`       | name, role, created_at                                  |
| `DeviceEntity`          | `device_id`     | model, os_version, camera_specs                         |
| `DiagnosticSessionEntity`| `session_id`   | user_id, device_id, started_at, ended_at                |
| `SampleEntity`          | `sample_id`     | session_id, user_id, device_id, status, image_path, gps, timestamp |
| `InferenceRequestEntity`| `inference_id`  | sample_id, model_version, status, processing_time       |
| `DetectionEntity`       | `detection_id`  | sample_id, inference_id, class_label, original_class_label, confidence, bbox_x/y/w/h, is_false_positive |
| `EPGCalculationEntity`  | `epg_id`        | sample_id, inference_id, species, raw_count, ai_epg, tech_validated_epg |
| `ValidationRecordEntity`| `validation_id` | sample_id, detection_id, user_id, action_type, previous/new_value, reason, timestamp |
| `ReportEntity`          | `report_id`     | session_id, user_id, report_type, status, file_path, generated_at |
| `ReportSampleEntity`    | composite       | report_id, sample_id (junction table)                   |
| `SyncQueueEntity`       | `queue_id`      | sample_id, payload_type, status, retry_count, last_attempted |
