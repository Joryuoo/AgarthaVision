# AgarthaVision · Project Overview

> A Digital Information and Diagnostic System for Soil-Transmitted Helminth Surveillance.
>
> **Team Code:** 2526-sem2-cs342-02
> **Repository:** https://github.com/Joryuoo/AgarthaVision.git

---

## 1. MVP Scope — Phase 1 (Cloud Architecture)

This plan covers the **Phase 1 Minimum Viable Product**: a mobile capture client backed by
a cloud inference API, with a full HITL verification dashboard and DOH-compliant reporting.

| SRS Module | Name                              | In MVP? | Owner Deliverable                          |
|------------|-----------------------------------|---------|--------------------------------------------|
| 1A         | Mobile Capture Client             | Yes     | Android app — capture + transmit           |
| 2A         | Cloud Computing Backend           | Yes     | FastAPI + YOLO inference + EPG calc        |
| 3          | Verification Dashboard            | Yes     | Android app — HITL review + validate       |
| 4          | System Admin & Data Storage       | Yes     | Android app — reports + export             |
| 1B         | Dedicated Hardware Capture (IoT)  | No      | Phase 2 / SE2                              |
| 2B         | On-Device AI Processor            | No      | Phase 2 / SE2                              |

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

| Specific Objective                           | MVP Implementation                                                  |
|----------------------------------------------|---------------------------------------------------------------------|
| 100% metadata completeness                   | `CaptureMetadataBinder` — UUID, timestamp, GPS, device ID, session ID bound at capture time |
| ≥ 99.9% sync rate                            | `SyncQueueManager` + WorkManager retry with idempotency keys        |

### GO-3: Diagnostic Workflow Efficiency (HITL Pipeline)

| Specific Objective                           | MVP Implementation                                                  |
|----------------------------------------------|---------------------------------------------------------------------|
| Reduce processing time ≥ 30%                 | Cloud inference returns results in seconds; HITL dashboard enables rapid approve/edit/reject |
| Interactive verification dashboard           | Module 3 — annotated image viewer, bounding box overlay, reclassification, false positive marking, EPG recalc |

### GO-4: Standardized Reporting & Mapping

| Specific Objective                           | MVP Implementation                                                  |
|----------------------------------------------|---------------------------------------------------------------------|
| EPG within 10% of manual counts              | Automated EPG calculation in cloud backend; medtech override in dashboard |
| DOH reports within 5 min of validation       | Background report generation triggered on validation; PDF export    |

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

### Cloud Backend (separate deployment)

| Layer          | Technology                                              |
|----------------|---------------------------------------------------------|
| Language       | Python 3.11+                                            |
| API Framework  | FastAPI                                                 |
| AI / ML        | PyTorch + Ultralytics (YOLO)                            |
| Database       | PostgreSQL                                              |
| Object Storage | S3-compatible (MinIO for dev, cloud bucket for prod)    |
| Task Queue     | Celery + Redis (for async inference)                    |
| Auth           | JWT bearer tokens                                       |

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

| Github Username                | Primary Area                          | Secondary Area             |
|-----------------------|---------------------------------------|----------------------------|
| Beansman  | Module 3 — Verification Dashboard     | Design system / theming    |
| Joryuoo    | Module 2A — Cloud Backend + AI        | Database schema            |
| jojseph  | Module 1A — Mobile Capture Client     | CameraX integration        |
| IgnisFrostburn     | Module 4 — Reporting + Admin          | Sync queue / offline       |
| DMKuZu   | Architecture + DevOps + CI/CD         | Git workflow, code review  |

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
| `04_CLOUD_BACKEND_PLAN.md`        | FastAPI + YOLO inference + database + API contracts     |
| `05_DESIGN_SYSTEM_KOMOUI.md`      | Updated Clinical Pulse components guide (KomoUI 0.3.0) |
| `06_GIT_WORKFLOW_AND_CI.md`       | Branching, PRs, GitHub Actions, release process        |
| `07_TEAM_CONVENTIONS.md`          | Kotlin standards, commit messages, documentation       |
