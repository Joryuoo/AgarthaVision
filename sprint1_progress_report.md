# Sprint 1 Progress Report (Capture + Local Persistence)

Date: 2026-05-23

## Summary

§1.1 CameraX integration is complete. §1.2 is in progress — MicroscopyViewport, BiologicalWindowChip, and the theme system are done; CaptureScreen, CaptureViewModel, and the capture button are still pending. §1.3 LocationProvider is done (Bundle 2). §1.4 Room persistence is pending (Bundle 1 in progress).

## Checklist

### 1.1 CameraX integration
- [x] core/camera/CameraManager.kt exists and binds preview
- [x] Image capture saves to app-internal storage

### 1.2 Capture screen
- [ ] CaptureScreen UI exists with MicroscopyViewport + overlays
- [ ] Capture button saves snapshot and shows Sonner toast
- [ ] Recent thumbnail renders from latest capture

### 1.3 Metadata binding
- [ ] CaptureSampleUseCase binds UUID, timestamp, device ID, session ID, GPS
- [x] LocationProvider used for GPS data

### 1.4 Local persistence (Room)
- [ ] SampleEntity exists with SDD fields
- [ ] SampleDao exists with insert/query methods
- [ ] Room database includes SampleEntity

### 1.5 Acceptance criteria
- [x] Camera preview works on device/emulator
- [x] Capture writes JPEG to internal storage
- [ ] Room row created with status CAPTURED and full metadata
- [x] GPS coordinates populated or null on denial
- [x] Biological window chip counts down
- [ ] Thumbnail updates after each capture

## Evidence From Codebase

- [CameraManager.kt](app/src/main/java/com/agarthavision/core/camera/CameraManager.kt) — complete. Binds preview via `ProcessCameraProvider`, captures JPEG to `filesDir/captures/UUID.jpg`.
- [MicroscopyViewport.kt](app/src/main/java/com/agarthavision/ui/components/MicroscopyViewport.kt) — complete. Wires CameraManager to PreviewView; passes `ImageCapture` via `onReady`.
- [BiologicalWindowChip.kt](app/src/main/java/com/agarthavision/ui/components/BiologicalWindowChip.kt) — complete. Countdown timer with critical color switch at ≤600 seconds.
- [LocationProvider.kt](app/src/main/java/com/agarthavision/domain/repository/LocationProvider.kt) + [FusedLocationProvider.kt](app/src/main/java/com/agarthavision/core/location/FusedLocationProvider.kt) — complete. Returns `null` on denial / timeout; never throws `SecurityException`.
- CaptureScreen composable is not present (ui/capture is empty).
- CaptureViewModel is not present.
- Room entities and DAOs are stubs at [entity.kt](app/src/main/java/com/agarthavision/data/local/entity.kt) and [dao.kt](app/src/main/java/com/agarthavision/data/local/dao.kt).
- DatabaseModule is still commented out at [DatabaseModule.kt](app/src/main/java/com/agarthavision/core/di/DatabaseModule.kt).

## Risks / Blockers

- §1.4 Room persistence (SampleEntity, SampleDao, AgarthaDatabase) still missing — Bundle 1 is the next unblocked task.
- MainActivity constructs `CameraManager(context)` directly ([MainActivity.kt:32](app/src/main/java/com/agarthavision/MainActivity.kt#L32)) — bypasses Hilt `@Singleton` scope. Must be fixed when CaptureViewModel is wired.

## Suggested Next Steps

1. Land Bundle 1: SampleEntity, SampleDao, AgarthaDatabase, SampleRepository, DatabaseModule, RepositoryModule.
2. Build CaptureScreen + CaptureViewModel + CaptureSampleUseCase (wires LocationProvider + SampleRepository).
3. Add capture button and thumbnail display to complete §1.2.
