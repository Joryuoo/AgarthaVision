# AgarthaVision · Project Overview

> A Digital Information and Diagnostic System for Soil-Transmitted Helminth Surveillance.
>
> **Team Code:** 2526-sem2-cs342-02
> **Repository:** https://github.com/Joryuoo/AgarthaVision.git

A medtech opens the app, logs in, and sees a live camera viewport trained on a microscope slide. They tap **Start Session** — the app begins sampling one frame every two seconds and sending each to a cloud AI model. When the model detects a parasite egg, a banner appears: *"Egg detected — tap to review."* The session continues running while the medtech reviews flagged frames, accepting or rejecting each detection. When they finish, verified samples and their bounding-box metadata sync to the cloud database. No manual counting, no paper forms mid-session.

**Phase 1 (current MVP):** Mobile capture + Supabase (Auth + Postgres + Storage) + a **self-hosted FastAPI inference container** running on a rented GPU droplet. Rebuildable on any GPU provider via a public image on GitHub Container Registry.
**Phase 2:** Self-hosted inference on owned hardware (local GPU), self-hosted PostgreSQL in Philippine region, EPG calculations, DOH-formatted reports, offline-first sync queue.

---

## 1. MVP Scope

### Phase 1 (current — managed services)

No dedicated backend to operate. The mobile app talks directly to Supabase and Roboflow.

| SRS Module | Name                              | Phase 1?     | Owner Deliverable                                         |
|------------|-----------------------------------|--------------|-----------------------------------------------------------|
| 1A         | Mobile Capture Client             | **Yes**      | Android app — continuous capture, per-frame inference, verify + sync |
| 2A         | Cloud Backend                     | **Supabase** | Managed Postgres + Auth + Storage; schema migrations owned by Joryuoo |
| 2A (AI)    | Inference                         | **Self-hosted container** | FastAPI + custom Ultralytics (YOLOv26 head + EfficientNetV2 backbone) on a rented GPU droplet. Model trained by Tabada; container built/deployed by DMKuZu. Per [ADR-003](adr/003-self-hosted-inference-container.md). |
| 3          | Verification (in-app)             | **Yes**      | `VerificationSheet` — medtech accepts or rejects flagged frames |
| 4          | Reporting & Admin                 | **No**       | Phase 2                                                   |
| 1B         | Dedicated Hardware Capture (IoT)  | No           | Phase 2 / SE2                                             |
| 2B         | On-Device AI Processor            | No           | Phase 2 (after model is locked)                           |

### Phase 2 (deferred — self-hosted stack)

Triggered by funding/support commitment or first deployment with real patient PHI.
Self-hosted FastAPI + PostgreSQL + MinIO + YOLO; EPG calculations; DOH-formatted PDF reports;
offline-first `SyncQueue` + WorkManager; advanced HITL dashboard.
See [ADR-002](adr/002-supabase-and-roboflow-for-mvp.md) for the migration path.

---

## 2. Objectives → Implementation Mapping

Each General Objective from the Project Proposal maps to concrete MVP deliverables.

### GO-1: Standardized Optical Input Pipeline

> Phase 2 targets the IoT hardware node. For MVP, the mobile client establishes the
> **transactional data pipeline** that Phase 2 inherits.

| Specific Objective                           | MVP Implementation                                                  |
|----------------------------------------------|---------------------------------------------------------------------|
| Deploy capture node (Phase 2)                | Deferred — mobile client serves as interim capture source           |
| Execute local edge inference (Phase 2)       | Deferred — cloud inference API handles this for MVP                 |
| **Pipeline architecture itself**             | UUID generation, metadata binding, payload encapsulation, state machine — all built in MVP and reused in Phase 2 |

### GO-2: Traceability Coverage

| Specific Objective                           | Phase 1 Implementation                                              | Phase 2 |
|----------------------------------------------|---------------------------------------------------------------------|---------|
| 100% metadata completeness                   | UUID, timestamp, GPS, device ID, session ID bound at capture time   | —       |
| ≥ 99.9% sync rate                            | Direct upload on verification (online-only for demo window)         | `SyncQueue` + WorkManager offline-first retry |

### GO-3: Diagnostic Workflow Efficiency (HITL Pipeline)

| Specific Objective                           | Phase 1 Implementation                                              | Phase 2 |
|----------------------------------------------|---------------------------------------------------------------------|---------|
| Reduce processing time ≥ 30%                 | Roboflow inference flags eggs in ~1-2 s per frame; medtech verifies flagged frames only — no manual counting | — |
| Interactive verification                     | `VerificationSheet` — annotated JPEG + bounding boxes; accept or reject; syncs to Supabase on accept | Full HITL dashboard, reclassification, audit log |

### GO-4: Standardized Reporting & Mapping

| Specific Objective                           | Phase 1 Implementation | Phase 2 |
|----------------------------------------------|------------------------|---------|
| EPG within 10% of manual counts              | Deferred               | Automated EPG calc in backend; medtech override |
| DOH reports within 5 min of validation       | Deferred               | Background PDF generation on validation |

---

## 3. Tech Stack Summary

### Android App (this is the primary deliverable)

| Layer          | Technology                                              |
|----------------|---------------------------------------------------------|
| Language       | Kotlin 2.2.x                                           |
| UI Framework   | Jetpack Compose (BOM latest) + KomoUI 0.3.0            |
| Architecture   | MVVM + Use Cases + Repository pattern                   |
| DI             | Hilt                                                    |
| Database       | Room / SQLite                                           |
| Camera         | CameraX                                                 |
| Networking     | Retrofit + OkHttp                                       |
| Background     | WorkManager                                             |
| Async          | Kotlin Coroutines + Flow                                |
| Navigation     | Compose Navigation                                      |
| Preferences    | DataStore                                               |
| Design Tokens  | Clinical Pulse via KomoUI `ShadcnColors` / `ShadcnRadius` |
| Fonts          | Geist (display/body) + JetBrains Mono (data/mono)       |
| Min SDK        | 26                                                      |
| Target SDK     | 35                                                      |

### Phase 1 Cloud

| Concern        | Technology                                                   |
|----------------|--------------------------------------------------------------|
| Auth           | Supabase Auth (email/password, JWT, auto-refresh via supabase-kt) |
| Database       | Supabase Postgres (managed) — `sessions`, `samples`, `detections` |
| Object Storage | Supabase Storage — `samples` bucket (verified JPEGs)         |
| Inference      | Self-hosted FastAPI container on rented GPU droplet (default: DigitalOcean MI300X AMD). Public image on GHCR — `docker run` on any GPU provider. |
| SDK (Android)  | `supabase-kt` BOM + `ktor-client-okhttp` + Retrofit (inference) |
| Secrets        | `local.properties` (gitignored); CI via Gradle `-P` flags    |

### Phase 2 Cloud (self-hosted — deferred)

| Layer          | Technology                                              |
|----------------|---------------------------------------------------------|
| API Framework  | FastAPI                                                 |
| AI / ML        | Self-hosted YOLO (local GPU)                            |
| Database       | Self-hosted PostgreSQL 16                               |
| Object Storage | MinIO / NAS                                             |
| Task Queue     | Celery + Redis                                          |

### Tooling

| Tool           | Purpose                                                 |
|----------------|---------------------------------------------------------|
| Bun            | Task runner, git hooks, scripts, lint orchestration     |
| GitHub Actions | CI — build, lint, test on every PR                      |
| ktlint         | Kotlin linter (enforced via CI)                         |
| Detekt         | Static analysis for Kotlin                              |

---

## 4. Team Roles

With 5 members, assign primary ownership areas. Everyone touches all modules, but
one person is the **point of contact** for each area.

| Github Username  | Primary Area                                      | Secondary Area              |
|------------------|---------------------------------------------------|-----------------------------|
| Beansman         | Module 3 — In-app verification (VerificationSheet) + Login screen | Design system / theming |
| Joryuoo          | Supabase schema migrations + `SyncSampleUseCase` (RLS policies) | API contracts |
| IgnisFrostburn   | Module 1A — CameraX (`bindAnalysis`) + `FrameSampler` + `CaptureScreen` | Detection overlay |
| jojseph          | Module 4 — Records + CSV export (Sprint 2)        | Sync queue / WorkManager (Phase 2) |
| Tabada           | Model training (custom Ultralytics: YOLOv26 + EfficientNetV2) + handoff of `best.pt` | Model versioning |
| DMKuZu           | Architecture + DevOps + CI/CD + **inference container** (Dockerfile, FastAPI server, GHCR image, GPU droplet ops) | Git workflow, code review |

> **Phase 1 re-scope (May 2026):** Joryuoo's original "build FastAPI backend" scope was
> replaced by "operate the Supabase project and own SQL schema migrations" per
> [ADR-002](adr/002-supabase-and-roboflow-for-mvp.md). The Roboflow inference path was
> then replaced by a self-hosted FastAPI container per
> [ADR-003](adr/003-self-hosted-inference-container.md) because the team's custom model
> (YOLOv26 + EfficientNetV2) is incompatible with Roboflow Hosted Inference.

> **Phase 1 re-scope:** Joryuoo's original "build FastAPI backend" scope has been replaced by
> "operate the Supabase project and own all SQL schema migrations" per
> [ADR-002](adr/002-supabase-and-roboflow-for-mvp.md). Tabada owns the Roboflow workspace
> and model versioning.

> **Note:** These are suggested assignments. Adjust based on each member's strengths
> and interests. The point is that every module has a single accountable owner.

---

## 5. Plan Documents Index

| File                              | Contents                                               |
|-----------------------------------|--------------------------------------------------------|
| `00_PROJECT_OVERVIEW.md`          | This file — scope, objectives, stack, team             |
| `01_ENVIRONMENT_SETUP.md`         | IDE setup (IntelliJ / VSCode / AS), SDK, Bun, emulator |
| `02_PROJECT_ARCHITECTURE.md`      | MVVM layers, package structure, Gradle modules, DI     |
| `03_MOBILE_APP_PLAN.md`           | Feature-by-feature Android implementation plan         |
| `04_CLOUD_BACKEND_PLAN.md`        | Phase 1: Supabase + Roboflow setup, schema, API contracts; Phase 2 migration path |
| `05_DESIGN_SYSTEM_KOMOUI.md`      | Updated Clinical Pulse components guide (KomoUI 0.3.0) |
| `06_GIT_WORKFLOW_AND_CI.md`       | Branching, PRs, GitHub Actions, release process        |
| `07_TEAM_CONVENTIONS.md`          | Kotlin standards, commit messages, documentation       |
| `adr/001-fused-location-and-instant.md` | LocationProvider interface + Instant over Long   |
| `adr/002-supabase-and-roboflow-for-mvp.md` | Phase 1 managed-services decision + Phase 2 migration path |
| `adr/003-self-hosted-inference-container.md` | Pivot from Roboflow to self-hosted inference container (custom model) |
