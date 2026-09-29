# Stack

As-built. Every version below is read from a build file in this repo, not from memory. The
version catalog `gradle/libs.versions.toml` is the single source for library versions; the
line references point at it.

## Toolchain

| Thing | Version | Read from |
|---|---|---|
| Kotlin | 2.2.10 | `libs.versions.toml` `kotlin` |
| Android Gradle Plugin | 9.2.1 | `libs.versions.toml` `agp` |
| Gradle wrapper | 9.4.1 | `gradle/wrapper/gradle-wrapper.properties` (`distributionUrl`) |
| JDK / toolchain | 21 | `gradle/gradle-daemon-jvm.properties` (`toolchainVersion`), `app/build.gradle.kts` `compileOptions` |
| KSP | 2.2.10-2.0.2 | `libs.versions.toml` `ksp` |
| compileSdk | 36 (minor API 1) | `app/build.gradle.kts` `compileSdk` |
| minSdk / targetSdk | 26 / 36 | `app/build.gradle.kts` `defaultConfig` |
| App version | `versionCode 1`, `versionName 0.1.0-mvp` | `app/build.gradle.kts` `defaultConfig` |

Single Gradle module: `:app` (`settings.gradle.kts` `include`). Namespace and applicationId are both
`com.agarthavision` (`app/build.gradle.kts` `namespace`, `applicationId`).

## Android libraries

| Area | Library | Version | Catalog key |
|---|---|---|---|
| UI | Compose BOM | 2026.02.01 | `compose-bom` |
| UI | `foundation-layout` (pinned outside the BOM) | 1.11.2 | `foundation-layout` |
| DI | Hilt | 2.59.2 | `hilt` |
| Local DB | Room | 2.7.0 | `room` |
| Camera | CameraX | 1.6.1 | `camerax` |
| HTTP | Retrofit | 2.11.0 | `retrofit` |
| HTTP | OkHttp | 4.12.0 | `okhttp` |
| Cloud | supabase-kt BOM (auth, postgrest, storage) | 3.0.3 | `supabase` |
| Cloud | Ktor client (OkHttp engine) | 3.0.3 | `ktor` |
| Async | Coroutines | 1.9.0 | `coroutines` |
| Async | kotlinx-datetime (`strictly`) | 0.6.1 | `kotlinx-datetime` |
| Nav | Navigation Compose | 2.8.5 | `navigation` |
| Lifecycle | lifecycle-viewmodel / runtime compose | 2.8.7 | `lifecycle` |
| Images | Coil | 2.7.0 | `coil` |
| Prefs | DataStore Preferences | 1.1.2 | `datastore` |
| Background | WorkManager | 2.10.0 | `workmanager` |
| EXIF | androidx exifinterface | 1.4.2 | `exifinterface` |
| On-device ML | LiteRT (`CompiledModel` API, GPU accelerator built in) | 2.2.0 | `litert` |

**Room is at schema version 23** (`core/database/AgarthaDatabase.kt`), reached from 22 by the
project's first hand-written migration (`core/database/Migrations.kt`). Older installs still fall
back to a destructive rebuild.

The app-wide Coil `ImageLoader` is configured in `AgarthaVisionApp.newImageLoader()`: disk
cache fixed at 250MB (matching Coil's own maximum clamp of ~2% of disk space, capped between
10MB and 250MB, so devices with less storage still get the full 250MB instead of a scaled-down
amount, while larger-storage devices aren't given more than Coil would already grant; samples
are resized to 640x640 JPEG at ~50-150KB each, so 250MB holds several thousand images), memory
cache at 25% of the memory class, and `respectCacheHeaders(false)`
because sample image storage paths are stable/content-addressed (uploads `upsert` to the same
path, so a cached response never needs revalidation). There is deliberately no OkHttp response
cache configured on the inference client or on any Supabase-backed client: the inference API is
POST-only and its `/health` check must always hit the network rather than serve a stale cached
"online" result, and caching Supabase responses would mean writing patient PII to unencrypted
disk (see `docs/patient-pii-position.md`, Position 4, on at-rest encryption being out of scope
for Phase 1).
- Version 10 added `sessions.psgc_barangay_code` and the `psgc_barangays` reference table.
- **Version 11 is deliberately left free** for the reverted egg-stage ticket (86d4a6jwy), so that when it returns it does not collide with existing builds carrying that version.
- Version 12 added `sample_species_findings`, `detections.species_touched`, and `samples.deleted_at`.
- Version 13 added the patient-based schema (`patients`, `patient_users`, `sessions.patient_id`, and dropped `sessions.notes`, `sessions.ended_at`, `sessions.psgc_barangay_code`, `sessions.claim_exempt`, and GPS columns).
- Version 14 added `species_suggestions`, the offline autocomplete index.
- Version 15 added `reports.lpf_per_species_json` (LPF density metrics).
- Version 16 declared the samples-to-sessions foreign key with `NO_ACTION` (`SampleEntity.kt` `foreignKeys`).
- Version 22 dropped `detections.species_touched` and `detections.verified_by_user`. **Version 21 is left free**: `development` briefly carried a `21.json` of a different shape.

`play-services-location` was removed along with the GPS columns (`gradle/libs.versions.toml`).

**WorkManager runs sync** as of 86d4brr1f. `data/sync/SyncWorker` is a `@HiltWorker` behind
the pure-Kotlin `domain/sync/SyncScheduler` port, enqueued as unique work with a network
constraint and exponential backoff. `androidx.hilt:hilt-work` supplies `@HiltWorker`, and
WorkManager's default initializer is removed in the manifest so `AgarthaVisionApp`'s
`Configuration.Provider` can hand it the `HiltWorkerFactory`. See `map/processes/sync.md`.
The inference queue runs through WorkManager the same way (14zcqntj6ny). See
`map/processes/infer.md`.

Two JSON stacks coexist by design: **Gson** for Room JSON columns and the Retrofit converter
(`core/di/InferenceModule.kt::provideGson`, `::provideInferenceRetrofit`), **kotlinx.serialization** for Supabase row shapes
(`data/supabase/SampleRemoteDataSource.kt::SampleInsertRow`).

## Quality tooling

| Tool | Version | Catalog key |
|---|---|---|
| ktlint Gradle plugin | 12.1.2 | `ktlint-plugin` |
| detekt | 1.23.7 | `detekt` |
| Robolectric | 4.16 | `robolectric` |
| Roborazzi | 1.74.0 | `roborazzi` |
| JUnit 4 | 4.13.2 | `junit` |
| mockito-kotlin | 5.4.0 | `mockito-kotlin` |
| Turbine | 1.1.0 | `turbine` |
| Espresso | 3.7.0 | `espresso` |

Detekt config: `detekt.yml`, applied at both the root and `:app`
(the `detekt {}` block in `build.gradle.kts` and in `app/build.gradle.kts`) with `buildUponDefaultConfig = true`.
It configures complexity, exceptions, naming, and style only — no architecture rules.

Robolectric backs the Compose UI tests under `app/src/test/`, so screen-level tests run on
the JVM in `:app:testDebugUnitTest` rather than needing a device. Roborazzi verifies UI
screenshot goldens in `:app:verifyRoborazziDebug`. It is not used by, and not permitted in,
`domain/` — see `constraints.md` C2.

Gradle behaviour flags worth knowing: configuration cache and build cache are both on, KSP2 is
on (`gradle.properties`: `org.gradle.configuration-cache`, `org.gradle.caching`, `ksp.useKSP2`).

## Node-side tooling

`package.json` is a script wrapper around Gradle plus the Husky hook installer
(`package.json` `scripts`). Runtime is **Bun**.

**Lockfile drift:** `bun.lock`'s `devDependencies` records devDependencies — `@commitlint/cli`,
`@commitlint/config-conventional`, `@types/bun`, `husky`, `lint-staged` — that `package.json`
no longer declares. `bun install` from the current `package.json` installs nothing. See
`commands.md`.

## Backend and inference

- **Supabase** (managed): Auth, Postgres, Storage. Client plugins installed at
  `core/di/SupabaseModule.kt::provideSupabaseClient`. Schema: `supabase/migrations/`, applied by
  hand in the dashboard (C6); which file describes which project is in
  [`file-tree.md`](file-tree.md#supabasemigrations). No Supabase CLI in Phase 1.
- **Inference container** (self-hosted): FastAPI + Uvicorn + Ultralytics YOLO on PyTorch,
  image based on `rocm/pytorch:latest` (`inference/Dockerfile` `FROM`), serving on port 8000
  (`inference/Dockerfile` `EXPOSE`, `CMD`). Endpoints `GET /health` and `POST /infer`
  (`inference/server.py::health`, `::infer`). Bearer-token auth (`inference/server.py::verify_key`).
  Weights baked in at `inference/weights/yolo26n-efficientnetv2b0.pt` (YOLO26-nano on an
  EfficientNetV2-B0 backbone, from the fork's `feat/optimized-inference`); default model version string
  `yolo26n-effv2b0-v1-cloud-fp32` (`inference/server.py` `MODEL_VERSION`).

## Build-time configuration

Four `BuildConfig` fields per build type — `SUPABASE_URL`, `SUPABASE_ANON_KEY`,
`INFERENCE_URL`, `INFERENCE_API_KEY` — populated from `local.properties`
(`app/build.gradle.kts` `buildTypes`). Debug reads the `*_DEV` keys, release the `*_PROD` keys.
Both suffixed keys are documented in `local.properties.example` (PB-01).
A missing key becomes an empty string.
