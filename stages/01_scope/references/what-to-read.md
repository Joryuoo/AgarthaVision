# What to Read — Stage 01 (Scope)

Use this to locate the source files relevant to the task before writing the scope plan.

## Package map

Source root: `app/src/main/java/com/agarthavision/`

```
core/      camera · connectivity · database · di · location · session · util
domain/    model · repository · usecase/{auth,capture,inference,verify,records,reports}
data/      local/{dao,entity,mapper} · remote/{InferenceApi,dto} · repository · supabase
ui/        capture · components · dashboard · login · navigation · records · sessions
           settings · theme · verify
```

## Where to look by concern

| Concern | Location |
|---|---|
| Auth / session restore | `data/repository/AuthRepositoryImpl`, `domain/usecase/auth/` |
| Camera loop / sampling | `core/camera/CameraManager.kt`, `core/camera/FrameSampler.kt` |
| Inference request/response | `data/remote/InferenceApi.kt`, `domain/usecase/inference/` |
| Capture metadata binding | `domain/usecase/capture/`, `core/location/`, `core/session/SessionManager` |
| Flagged-frame queue + toast | `core/camera/FlaggedFrameStore`, `ui/capture/` |
| Verification sheet | `ui/verify/VerificationSheet`, `domain/usecase/verify/SubmitVerificationUseCase` |
| Manual capture | `domain/usecase/verify/SubmitManualCaptureUseCase`, `ui/verify/ManualCaptureSheet` |
| Sample sync | `domain/usecase/verify/SyncSampleUseCase`, `data/supabase/`, `data/remote/` |
| Report sync | `domain/usecase/records/SyncReportUseCase`, `data/supabase/` |
| EPG calculation | `domain/usecase/records/SessionEggCountUseCase`, `domain/model/EpgCalculator` |
| Records list + session detail | `ui/records/`, `ui/sessions/SessionDetailScreen` |
| Sample detail / image | `domain/usecase/records/ResolveSampleImageSourceUseCase` |
| CSV report generation | `domain/usecase/records/GenerateSessionReportUseCase` |
| Persisted report rows | `data/local/entity/ReportEntity`, `data/local/dao/ReportDao`, `data/supabase/ReportRemoteDataSource` |
| Settings screen | `ui/settings/` (placeholder — not fully implemented; see TODO.md) |
| Room database + migrations | `core/database/AgarthaDatabase`, `app/schemas/` (v1–v7) |
| Supabase migrations | `supabase/migrations/0001_*.sql` through `0008_reports.sql` |
| Design tokens | `ui/theme/AppColors.kt`, `ui/theme/Type.kt` |
| DI modules | `core/di/` |
| Build scripts | `package.json` (Bun), `build.gradle.kts` |

## Data model summary

Tables (Supabase Postgres): `profiles`, `sessions`, `samples`, `detections`, `reports`
Room entities: `SessionEntity`, `SampleEntity`, `DetectionEntity`, `ReportEntity`
Current Room schema version: 7 (schemas exported to `app/schemas/`)
Latest Supabase migration: `0008_reports.sql`
Next Room migration: v8 | Next Supabase migration: `0009_<name>.sql`

Full column detail: `../../schema.ts`
RLS authority: `supabase/migrations/*.sql` (takes precedence over schema.ts for constraints)

## Sample lifecycle (reference)

```
CANDIDATE (in-memory)
  └─ predictions non-empty → FLAGGED (FlaggedFrameStore, transient)
        └─ medtech submits  → VERIFIED (Room: SampleEntity + DetectionEntity rows)
              └─ sync        → SYNCED (Room + Supabase)
                    └─ fail  → SYNC_FAILED (retried on next session start)
```

No REJECTED state — a rejection is a DetectionEntity with verdict = FALSE_POSITIVE.
