# Stack

As-built. Every version below is read from a build file in this repo, not from memory. The
version catalog `gradle/libs.versions.toml` is the single source for library versions; the
line references point at it.

## Toolchain

| Thing | Version | Read from |
|---|---|---|
| Kotlin | 2.2.10 | `gradle/libs.versions.toml:2` |
| Android Gradle Plugin | 9.2.1 | `gradle/libs.versions.toml:3` |
| Gradle wrapper | 9.4.1 | `gradle/wrapper/gradle-wrapper.properties` (`distributionUrl`) |
| JDK / toolchain | 21 | `gradle/gradle-daemon-jvm.properties` (`toolchainVersion`), `app/build.gradle.kts:94-97` |
| KSP | 2.2.10-2.0.2 | `gradle/libs.versions.toml:22` |
| compileSdk | 36 (minor API 1) | `app/build.gradle.kts:24-29` |
| minSdk / targetSdk | 26 / 36 | `app/build.gradle.kts:33-34` |
| App version | `versionCode 1`, `versionName 0.1.0-mvp` | `app/build.gradle.kts:35-36` |

Single Gradle module: `:app` (`settings.gradle.kts:26`). Namespace and applicationId are both
`com.agarthavision` (`app/build.gradle.kts:23`, `:32`).

## Android libraries

| Area | Library | Version | Line |
|---|---|---|---|
| UI | Compose BOM | 2026.02.01 | `libs.versions.toml:4` |
| UI | `foundation-layout` (pinned outside the BOM) | 1.11.2 | `libs.versions.toml:29` |
| DI | Hilt | 2.59.2 | `libs.versions.toml:5` |
| Local DB | Room | 2.7.0 | `libs.versions.toml:6` |
| Camera | CameraX | 1.6.1 | `libs.versions.toml:9` |
| HTTP | Retrofit | 2.11.0 | `libs.versions.toml:7` |
| HTTP | OkHttp | 4.12.0 | `libs.versions.toml:8` |
| Cloud | supabase-kt BOM (auth, postgrest, storage) | 3.0.3 | `libs.versions.toml:30` |
| Cloud | Ktor client (OkHttp engine) | 3.0.3 | `libs.versions.toml:31` |
| Async | Coroutines | 1.9.0 | `libs.versions.toml:12` |
| Async | kotlinx-datetime (`strictly`) | 0.6.1 | `libs.versions.toml:13` |
| Nav | Navigation Compose | 2.8.5 | `libs.versions.toml:14` |
| Lifecycle | lifecycle-viewmodel / runtime compose | 2.8.7 | `libs.versions.toml:15` |
| Images | Coil | 2.7.0 | `libs.versions.toml:16` |
| Prefs | DataStore Preferences | 1.1.2 | `libs.versions.toml:11` |
| Background | WorkManager | 2.10.0 | `libs.versions.toml:10` |
| Location | play-services-location | 21.3.0 | `libs.versions.toml:24` |
| EXIF | androidx exifinterface | 1.4.2 | `libs.versions.toml:21` |

**WorkManager is a ghost.** It is declared as a dependency (`app/build.gradle.kts:157`) but no
`Worker` exists anywhere in `app/src/main/`. Phase 1 sync is foreground and trigger-based;
the durable queue is Phase 2. See `map/processes/sync.md`.

Two JSON stacks coexist by design: **Gson** for Room JSON columns and the Retrofit converter
(`core/di/InferenceModule.kt:35`, `:69`), **kotlinx.serialization** for Supabase row shapes
(`data/supabase/SampleRemoteDataSource.kt:99-152`).

## Quality tooling

| Tool | Version | Line |
|---|---|---|
| ktlint Gradle plugin | 12.1.2 | `libs.versions.toml:23` |
| detekt | 1.23.7 | `libs.versions.toml:27` |
| JUnit 4 | 4.13.2 | `libs.versions.toml:106` |
| mockito-kotlin | 5.4.0 | `libs.versions.toml:25` |
| Turbine | 1.1.0 | `libs.versions.toml:26` |
| Espresso | 3.7.0 | `libs.versions.toml:20` |
| Robolectric | 4.16 | `libs.versions.toml:28` |

Detekt config: `detekt.yml`, applied at both the root and `:app`
(`build.gradle.kts:11-14`, `app/build.gradle.kts:13-16`) with `buildUponDefaultConfig = true`.
It configures complexity, exceptions, naming, and style only — no architecture rules.

Robolectric backs the Compose UI tests under `app/src/test/`, so screen-level tests run on
the JVM in `:app:testDebugUnitTest` rather than needing a device. It is not used by, and not
permitted in, `domain/` — see `constraints.md` C2.

Gradle behaviour flags worth knowing: configuration cache and build cache are both on, KSP2 is
on (`gradle.properties:10-11`, `:20`).

## Node-side tooling

`package.json` is a script wrapper around Gradle plus the Husky hook installer
(`package.json:5-19`). Runtime is **Bun**.

**Lockfile drift:** `bun.lock:7-13` records devDependencies — `@commitlint/cli`,
`@commitlint/config-conventional`, `@types/bun`, `husky`, `lint-staged` — that `package.json`
no longer declares. `bun install` from the current `package.json` installs nothing. See
`commands.md`.

## Backend and inference

- **Supabase** (managed): Auth, Postgres, Storage. Client plugins installed at
  `core/di/SupabaseModule.kt:30-32`. Schema is `supabase/migrations/0001`–`0008`, applied by
  hand in the dashboard. No Supabase CLI in Phase 1.
- **Inference container** (self-hosted): FastAPI + Uvicorn + Ultralytics YOLO on PyTorch,
  image based on `rocm/pytorch:latest` (`inference/Dockerfile:1`), serving on port 8000
  (`inference/Dockerfile:10-11`). Endpoints `GET /health` and `POST /infer`
  (`inference/server.py:29`, `:34`). Bearer-token auth (`inference/server.py:24-27`).
  Weights baked in at `inference/weights/best.pt`; default model version string
  `yolov26-efficientnetv2-v1` (`inference/server.py:11`).

## Build-time configuration

Four `BuildConfig` fields per build type — `SUPABASE_URL`, `SUPABASE_ANON_KEY`,
`INFERENCE_URL`, `INFERENCE_API_KEY` — populated from `local.properties`
(`app/build.gradle.kts:46-92`). Debug reads the `*_DEV` keys, release the `*_PROD` keys.
A missing key becomes an empty string. See `constraints.md` C10, including the
`INFERENCE_API_KEY` naming drift.
