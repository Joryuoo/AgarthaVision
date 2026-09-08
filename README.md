# AgarthaVision

<p align="center">
  <img src="branding/agarthavision-logo-mark-transparent.svg" alt="AgarthaVision logo" width="112" />
</p>

<p align="center">
  <strong>Clinical microscopy assistant for AI-supported Soil-Transmitted Helminth surveillance.</strong>
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/ANDROID-0B1118?style=for-the-badge&logo=android&logoColor=white&labelColor=0B1118" />
  <img alt="Kotlin" src="https://img.shields.io/badge/KOTLIN-2457D6?style=for-the-badge&logo=kotlin&logoColor=white&labelColor=2457D6" />
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/JETPACK_COMPOSE-1697A6?style=for-the-badge&logo=jetpackcompose&logoColor=white&labelColor=1697A6" />
  <img alt="Material 3" src="https://img.shields.io/badge/MATERIAL_3-1E3FD9?style=for-the-badge&logoColor=white&labelColor=1E3FD9" />
  <img alt="Supabase Postgres" src="https://img.shields.io/badge/SUPABASE_POSTGRES-16A34A?style=for-the-badge&logo=supabase&logoColor=white&labelColor=16A34A" />
  <img alt="FastAPI Inference" src="https://img.shields.io/badge/FASTAPI_INFERENCE-0F766E?style=for-the-badge&logo=fastapi&logoColor=white&labelColor=0F766E" />
  <img alt="CameraX" src="https://img.shields.io/badge/CAMERAX-C2410C?style=for-the-badge&logo=android&logoColor=white&labelColor=C2410C" />
</p>

## Staging / Dev Rules

- **No `TODO.md` in repository**: Do not create or re-introduce a `TODO.md` file in the project directory. All tasks and sprint items are managed in ClickUp.
- **Commit Message Format**: Format all commit messages using the following structure:
  `[type][ClickUp-ID][Lastname] Task title`
  *(e.g. `[feat][CU-869234][Beansman] Implement settings screen account section` or `[chore][CU-869235][DMKuZu] Detekt cleanup chore`)*
- **Build Before Push**: Always build and test your branch locally (`bun run build` / `.\gradlew.bat assembleDebug` and `bun run test`) before pushing changes to the repository.

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
| UI | Jetpack Compose, Material 3, Agartha components |
| Architecture | MVVM, Clean Architecture, Hilt |
| Local data | Room |
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

The current root audit records Sprint 3 hardening as the active implementation phase. Core records browsing, sample detail with Supabase image fallback, CSV export, session-as-smear semantics, manual capture, EPG calculation, user notes, persisted session reports, light/dark theme toggle, detekt 0-violation cleanup, and the production Settings screen are implemented. Open follow-ups include physical-device E2E verification, GitHub Actions CI setup, `:app:ktlintCheck` Gradle wiring fix, launcher icon regeneration, and remaining Phase 2 roadmap items. Active tasks and sprint items are tracked in ClickUp.

## Documentation

Start at `SESSION_INIT.md`. It is the single entry point for both contributors and automated
agents: what the project is, the repo/ClickUp boundary, a routing table from situation to
file, and the thirteen project constraints by name.

Everything it routes to lives under `docs/`:

- `docs/constraints.md` and `docs/non-negotiables.md` - the rules, with their enforcement points.
- `docs/stack.md` and `docs/commands.md` - versions and everything runnable.
- `docs/features.md`, `docs/CHANGELOG.md`, `docs/file-tree.md` - what ships, what changed, where things live.
- `docs/map/` - object cards, process cards, and the change-impact index.
- `schema.ts` - ground-truth data model for Supabase, Room, domain enums, Storage, and relationships.

*(Sprint backlog items and tasks are managed in ClickUp).*

Documentation here is as-built and cited to `path:line`. Where a document and the code
disagree, the code wins - fix the document in the same change.

## Git Workflow

This repository follows the documented GitHub Flow process:

- Branch from `staging` for feature, fix, docs, test, and CI work.
- Use conventional commits formatted as `[type][ClickUp-ID][Lastname] Task title`.
- Run lint, tests, and build checks before opening a pull request.
- Target `staging` for PRs (never `main` directly).
- Keep source changes aligned with the relevant plan, ADR, or design-system document.

## Contributors

- Beansman
- Joryuoo
- IgnisFrostburn
- jojseph
- kazuretsu

## Disclaimer

AgarthaVision is built for academic and clinical workflow support. It is not a standalone diagnostic authority and must be used under the review of qualified medical personnel.
