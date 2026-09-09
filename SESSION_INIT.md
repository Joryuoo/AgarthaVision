# SESSION_INIT

Read once at session start. Do not re-read. This file routes; it carries no content payload.

## What this is

AgarthaVision is a digital information and diagnostic system for Soil-Transmitted Helminth
(STH) surveillance, built by team `2526-sem2-cs342-02` at Cebu Institute of Technology. A
medical technologist mounts a phone to a microscope and captures frames of a fecal smear; a
YOLO-family model localises and classifies *Ascaris lumbricoides* / *Trichuris trichiura* /
Hookworm eggs; the app computes Eggs Per Gram from a Kato-Katz volumetric multiplier; and a
human validates every model output before it counts. Phase 1 — this repo — is an
Android/Kotlin + Jetpack Compose client on a Supabase backend with a self-hosted FastAPI
inference container. Phase 2 (deferred) moves capture and inference onto dedicated edge
hardware.

## Repo / vault boundary

This repo holds **as-built** documentation only: what the code does today, verified against
the code. Plans, the sprint backlog, task tracking and the ClickUp IDs referenced in commit
messages live in **ClickUp** — not here. Do not add a TODO file, a roadmap, or a sprint plan
to this repository.

## Where to go

| If you are... | Open |
|---|---|
| Changing the data model or writing a migration | `docs/map/effects/CONTEXT.md`, then the named object card |
| Changing capture (camera, frame sampling, manual snapshot) | `docs/map/processes/capture.md` |
| Changing inference (request/response contract, connectivity) | `docs/map/processes/infer.md` |
| Changing validation, verdicts, or the questionnaire | `docs/map/processes/validate.md` |
| Changing sync to Supabase (Storage or Postgres) | `docs/map/processes/sync.md` |
| Changing reports, CSV output, or EPG | `docs/map/processes/report.md` |
| Changing UI, theme, or design tokens | `docs/constraints.md` C11, then `docs/file-tree.md` |
| Setting up the environment or picking a version | `docs/stack.md` |
| Running a build, test, or lint | `docs/commands.md` |
| Writing a commit, branch, or PR | `docs/non-negotiables.md`, then `docs/constraints.md` C9 |
| Working out what a change breaks | `docs/map/effects/CONTEXT.md` |
| Checking whether something already ships | `docs/features.md` |
| Finding where a file lives | `docs/file-tree.md` |
| Reading what changed recently | `docs/CHANGELOG.md` |

## The 13 constraints — names only

Full statements and enforcement points live in `docs/constraints.md`.

C1 ViewModels call use cases · C2 Domain stays Android-free · C3 Interfaces in `domain/`,
implementations in `data/` · C4 Use cases return `Result<T>` · C5 `@Singleton` is a closed
list · C6 Migrations own the schema · C7 Human validation gates every AI output · C8 Nothing
is deleted · C9 Commit and branch format · C10 Never commit secrets · C11 One design system ·
C12 Build and test before pushing · C13 Code wins over docs.

## Rules of the road

- **Code is the source of truth.** Every shelf file cites `path:line`. Where a document and
  the code disagree, the code wins and you correct the document in the same change.
- Load the router, then **one** shelf file, then **one** card. Do not walk all of `docs/`.
- `docs/_archive/` is superseded. Never implement against anything inside it.
