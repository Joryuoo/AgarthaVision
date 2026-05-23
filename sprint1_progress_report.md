# Sprint 1 Progress Report (Capture + Local Persistence)

Date: 2026-05-23

## Summary

Sprint 1 work is not started yet. The capture feature set (CameraX, Capture screen, ViewModel, use case, and Room entities/DAOs) is missing or only stubbed. Existing work is still at Sprint 0 scaffolding.

## Checklist

### 1.1 CameraX integration
- [x] core/camera/CameraManager.kt exists and binds preview
- [x] Image capture saves to app-internal storage

### 1.2 Capture screen
- [x] CaptureScreen UI exists with MicroscopyViewport + overlays
- [x] Capture button saves snapshot and shows Sonner toast
- [x] Recent thumbnail renders from latest capture

### 1.3 Metadata binding
- [ ] CaptureSampleUseCase binds UUID, timestamp, device ID, session ID, GPS
- [ ] LocationProvider used for GPS data

### 1.4 Local persistence (Room)
- [ ] SampleEntity exists with SDD fields
- [ ] SampleDao exists with insert/query methods
- [ ] Room database includes SampleEntity

### 1.5 Acceptance criteria
- [ ] Camera preview works on device/emulator
- [ ] Capture writes JPEG to internal storage
- [ ] Room row created with status CAPTURED and full metadata
- [ ] GPS coordinates populated or null on denial
- [ ] Biological window chip counts down
- [ ] Thumbnail updates after each capture

## Evidence From Codebase

- Capture screen is not present (ui/capture is empty).
- Camera manager is not present (core/camera does not exist).
- Use case package is empty (domain/usecase/capture has no files).
- Room entities and DAOs are stubs at [app/src/main/java/com/agarthavision/data/local/entity.kt](app/src/main/java/com/agarthavision/data/local/entity.kt#L1-L3) and [app/src/main/java/com/agarthavision/data/local/dao.kt](app/src/main/java/com/agarthavision/data/local/dao.kt#L1-L3).
- Database package is empty (core/database has no files).
- Sample status enum exists at [app/src/main/java/com/agarthavision/domain/model/SampleStatus.kt](app/src/main/java/com/agarthavision/domain/model/SampleStatus.kt#L1-L22).
- MainActivity still renders a placeholder label at [app/src/main/java/com/agarthavision/MainActivity.kt](app/src/main/java/com/agarthavision/MainActivity.kt#L1-L18).

## Risks / Blockers

- Sprint 1 depends on Room entities/DAOs and a database class; these are missing.
- CameraX wrapper and capture flow are not started.

## Suggested Next Steps

1. Implement Room entities/DAOs and database shell per Sprint 0 and Sprint 1 requirements.
2. Add core/camera/CameraManager.kt and integrate CameraX preview + capture.
3. Build CaptureScreen + CaptureViewModel + CaptureSampleUseCase.
