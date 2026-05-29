# AgarthaVision

<p align="center">
  <!-- Replace this placeholder with the final logo asset when it is ready. -->
  <img src="docs/assets/agartha-logo.svg" alt="AgarthaVision logo" width="112" />
</p>

<p align="center">
  <strong>Clinical microscopy assistant for AI-supported Soil-Transmitted Helminth surveillance.</strong>
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/ANDROID-0B1118?style=for-the-badge&logo=android&logoColor=white&labelColor=0B1118" />
  <img alt="Kotlin" src="https://img.shields.io/badge/KOTLIN-2457D6?style=for-the-badge&logo=kotlin&logoColor=white&labelColor=2457D6" />
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/JETPACK_COMPOSE-1697A6?style=for-the-badge&logo=jetpackcompose&logoColor=white&labelColor=1697A6" />
  <img alt="KomoUI" src="https://img.shields.io/badge/KOMOUI-1E3FD9?style=for-the-badge&logoColor=white&labelColor=1E3FD9" />
  <img alt="Supabase Postgres" src="https://img.shields.io/badge/SUPABASE_POSTGRES-16A34A?style=for-the-badge&logo=supabase&logoColor=white&labelColor=16A34A" />
  <img alt="FastAPI Inference" src="https://img.shields.io/badge/FASTAPI_INFERENCE-0F766E?style=for-the-badge&logo=fastapi&logoColor=white&labelColor=0F766E" />
  <img alt="CameraX" src="https://img.shields.io/badge/CAMERAX-C2410C?style=for-the-badge&logo=android&logoColor=white&labelColor=C2410C" />
  <img alt="MVP Sprint 2" src="https://img.shields.io/badge/MVP_SPRINT_2-374151?style=for-the-badge&logoColor=white&labelColor=374151" />
</p>

## Overview

AgarthaVision is a mobile diagnostic-support and surveillance platform for fecal smear microscopy workflows. It helps medical technologists capture microscope frames, run AI-assisted parasite egg detection, verify results through a human-in-the-loop workflow, compute session-level Eggs Per Gram (EPG), and archive structured records for reporting.

The project is designed as a decision-support and preprocessing tool. It does not replace qualified medical judgment, final diagnosis, or laboratory validation.

## Current MVP Scope

Phase 1 focuses on the Android application and a lightweight managed-services backend:

- Email/password authentication through Supabase Auth.
- Continuous microscope feed analysis with CameraX `ImageAnalysis`.
- Synchronous inference through a self-hosted FastAPI container.
- Human-in-the-loop verification for AI detections.
- Manual capture for specimens missed by the model.
- Session-as-smear records with verified samples, repeat flags, user notes, and EPG summaries.
- CSV/session report generation backed by local Room data and Supabase sync.

Phase 2 work, including owned hardware deployment and a fuller self-hosted backend stack, is documented but intentionally deferred.

## Architecture

AgarthaVision uses MVVM with Clean Architecture boundaries inside a single Android application module.

```text
app/
  src/main/java/com/agarthavision/
    core/       Shared platform services, DI, database, networking, utilities
    domain/     Pure Kotlin models, repositories, and use cases
    data/       Room, Supabase, Retrofit, mappers, and repository implementations
    ui/         Jetpack Compose screens, ViewModels, navigation, and theme
    worker/     Background work hooks reserved for sync-related workflows

supabase/
  migrations/  Phase 1 Postgres schema and RLS migrations

inference/
  Dockerfile   Self-hosted FastAPI inference container
  server.py    GET /health and POST /infer
  weights/     Model weights used by the inference image
```

Key rules:

- ViewModels call domain use cases, not data-layer classes directly.
- Domain models remain pure Kotlin and do not import Android APIs.
- Room entities and remote DTOs are mapped into domain models.
- Supabase is the Phase 1 Auth, Postgres, and Storage provider.
- The inference service is stateless: it receives a frame, returns predictions, and does not persist images.

## Tech Stack

| Area | Technology |
| --- | --- |
| Language | Kotlin 2.2.10 |
| Platform | Android, min SDK 26, target SDK 36 |
| UI | Jetpack Compose, Material 3, KomoUI |
| Architecture | MVVM, Clean Architecture, Hilt |
| Local data | Room, DataStore |
| Camera | CameraX |
| Networking | Retrofit, OkHttp, Ktor client |
| Cloud data | Supabase Auth, PostgREST, Storage |
| Inference | FastAPI container with custom Ultralytics model weights |
| Tooling | Gradle Kotlin DSL, Bun scripts, ktlint, detekt, commitlint |

## Getting Started

### Prerequisites

- JDK 21
- Android Studio or IntelliJ IDEA with Android SDK 36
- Bun
- Git
- Access to the team Supabase and inference credentials

### Setup

1. Clone the repository.

   ```bash
   git clone https://github.com/Joryuoo/AgarthaVision.git
   cd AgarthaVision
   ```

2. Install project tooling and Git hooks.

   ```bash
   bun install
   ```

3. Create your local secrets file.

   ```bash
   cp local.properties.example local.properties
   ```

4. Fill `local.properties` with the Supabase and inference values shared by the team. Never commit real keys.

5. Open the project in Android Studio and let Gradle sync, or verify from the command line:

   ```bash
   ./gradlew assembleDebug
   ```

On Windows PowerShell, use `.\gradlew.bat assembleDebug` if the shell does not resolve `./gradlew`.

## Common Commands

| Command | Purpose |
| --- | --- |
| `bun run build` | Build the debug APK |
| `bun run compile` | Compile debug Kotlin |
| `bun run test` | Run debug unit tests |
| `bun run lint` | Run ktlint and detekt |
| `bun run lint:fix` | Apply ktlint formatting |
| `bun run install:device` | Install debug build on a connected device |
| `bun run clean` | Clean Gradle outputs |

## Backend and Data

Supabase migrations live in `supabase/migrations/` and are applied through the Supabase dashboard for Phase 1. The current schema includes sessions, samples, detections, storage RLS policies, manual-capture support, nullable detection boxes, and persisted session reports.

The inference container lives in `inference/`. It exposes:

- `GET /health` for connectivity checks.
- `POST /infer` for frame inference using bearer-token authentication.

The mobile app persists verified samples locally with Room, uploads images to Supabase Storage, and writes sample/detection/report metadata to Supabase Postgres when sync succeeds.

## Project Status

The latest progress report records Sprint 2 as the active implementation phase. Core records browsing, sample detail, CSV export, session-as-smear semantics, manual capture, EPG calculation, user notes, and persisted session reports are implemented. Open follow-ups include Supabase image rehydration after local cache loss, clearer report-sync retry behavior, and remaining Phase 2 planning items.

See `docs/progress/sprint2_progress_report.md` for the current code-vs-spec audit.

## Documentation

The project source of truth lives in `docs/`:

- `docs/00_PROJECT_OVERVIEW.md` - scope, objectives, stack, and ownership.
- `docs/01_ENVIRONMENT_SETUP.md` - local setup, secrets, tooling, and onboarding.
- `docs/02_PROJECT_ARCHITECTURE.md` - architecture, package boundaries, and dependency rules.
- `docs/03_MOBILE_APP_PLAN.md` - sprint-by-sprint Android MVP plan.
- `docs/04_CLOUD_BACKEND_PLAN.md` - Supabase and inference-container plan.
- `docs/agartha-design-system.md` - definitive UI tokens and interaction rules.
- `docs/06_GIT_WORKFLOW_AND_CI.md` - branch, commit, PR, and CI expectations.
- `docs/07_TEAM_CONVENTIONS.md` - Kotlin style, naming, KDoc, and tests.
- `docs/adr/` - architecture decision records.
- `docs/progress/` - implementation audits and sprint status.

When a rule is unclear or missing, update the relevant source-of-truth document before changing the implementation.

## Git Workflow

This repository follows the documented GitHub Flow process:

- Branch from `develop` for feature, fix, docs, test, and CI work.
- Use conventional commits enforced by commitlint and Husky.
- Run lint, tests, and build checks before opening a pull request.
- Keep source changes aligned with the relevant plan, ADR, or design-system document.

## Contributors

- Beansman
- Joryuoo
- IgnisFrostburn
- jojseph
- Tabada
- DMKuZu

## Disclaimer

AgarthaVision is built for academic and clinical workflow support. It is not a standalone diagnostic authority and must be used under the review of qualified medical personnel.
