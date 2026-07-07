# AGENTS

This file is the entry point for contributors and automated agents. It also serves as the
ICM workspace map (Layer 0) — read this before starting any task.

---

## Development Workspace

AgarthaVision uses a three-stage ICM pipeline for sprint work: **scope → implement → QA**.
Every stage produces a markdown artifact a human can inspect and edit before the next stage runs.

### Stages (in order)

1. `stages/01_scope/` — Analyse a backlog item; write a scoped implementation plan
2. `stages/02_implement/` — Execute the plan; write code; record implementation notes
3. `stages/03_qa/` — Run lint + tests; verify architecture + design compliance; draft the PR

Read the target stage's `CONTEXT.md` to load exactly the right inputs for that step.
A human reviews each stage's `output/` before the next stage runs — that is the gate.

### How to run the pipeline

1. When a task arrives, go to `stages/01_scope/` and read its `CONTEXT.md`.
2. Complete stages in order: 01 → 02 → 03.
3. Load only the files named in the current stage's Inputs table — nothing else.
4. Write output to that stage's `output/` folder.
5. Do not load files from another stage unless the current contract explicitly says to.

### Shared resources

| Path | What it is |
|---|---|
| `_config/voice.md` | Clinical voice and tone — load when writing user-facing text or docs |
| `_config/conventions.md` | Kotlin style, naming, KDoc, commit, branch rules — load for all coding stages |
| `CONTEXT.md` | Full project reference: tech stack, architecture rules, design system, ADRs |
| `schema.ts` | Ground-truth data model for Supabase, Room, and domain enums |
| `TODO.md` | Implementation state, Sprint 3 backlog, Phase 2 roadmap |

---

## Source of Truth

The former documentation tree has been consolidated into three root files:

- `CONTEXT.md` — project overview, tech stack, architecture rules, design system, Supabase/inference contracts, git workflow, coding conventions, and ADR summaries.
- `schema.ts` — ground-truth data model for Supabase, Room, domain enums, Storage, and relationships.
- `TODO.md` — current implementation state, known issues, Sprint 3 backlog, Phase 2 roadmap, and deferred documentation notes.

Read the relevant root file before changing behavior. If a rule, data contract, or backlog item changes, update the matching root file in the same work.

## Precedence Rules

- For database columns, constraints, RLS, and CHECKs, `supabase/migrations/*.sql` wins.
- For readable data-model summaries and Room/domain mismatches, use `schema.ts`.
- For architecture, design, security, workflow, and coding standards, use `CONTEXT.md`.
- For implementation status and next work, use `TODO.md`.
- The Clinical Microscopy design system in `CONTEXT.md` is the UI authority. The old KomoUI design guidance is superseded.

## Required Compliance

- Keep MVVM + Clean Architecture boundaries intact: ViewModels call use cases, domain stays Android-free, and data implementations stay behind repository interfaces.
- Follow the single design system in `CONTEXT.md`: Inter typography, CIT-U maroon/gold accent, clinical restrained UI, and MaterialTheme consolidation.
- Keep Supabase/Auth/Storage/inference behavior aligned with `CONTEXT.md` and `schema.ts`.
- Keep Kotlin style, naming, KDoc, tests, branch names, and conventional commits aligned with `CONTEXT.md`.
- Treat `TODO.md` as the active implementation audit before starting Sprint 3 or Phase 2 work.

## Non-Negotiables

- Do not invent new conventions without updating `CONTEXT.md`.
- Do not change schema behavior without updating the migration SQL and `schema.ts`.
- Do not claim a backlog item is complete without checking the codebase and updating `TODO.md`.
- Do not reintroduce a second UI theme or new KomoUI-only surface.
- Never commit real secrets; use `local.properties` locally and CI `-P` properties.

## How to Work

1. Read `CONTEXT.md`, `schema.ts`, and/or `TODO.md` depending on the task.
2. Inspect the relevant code and migrations; trust actual code over stale assumptions.
3. Implement the change within the documented package boundaries.
4. Add or update focused tests when behavior changes.
5. Update the root source-of-truth file when the public contract, design rule, or implementation status changes.
