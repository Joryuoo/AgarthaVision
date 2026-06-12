# AGENTS

This file is the entry point for contributors and automated agents.

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
- Follow the single design system in `CONTEXT.md`: Inter typography, cobalt accent, clinical restrained UI, and MaterialTheme consolidation.
- Keep Supabase/Auth/Storage/inference behavior aligned with `CONTEXT.md` and `schema.ts`.
- Keep Kotlin style, naming, KDoc, tests, branch names, and conventional commits aligned with `CONTEXT.md`.
- Treat `TODO.md` as the active implementation audit before starting Sprint 3 or Phase 2 work.

## Non-Negotiables

- Do not invent new conventions without updating `CONTEXT.md`.
- Do not change schema behavior without updating the migration SQL and `schema.ts`.
- Do not claim a backlog item is complete without checking the codebase and updating `TODO.md`.
- Do not reintroduce a second UI theme or new KomoUI-only surface while the theme consolidation is in progress.
- Never commit real secrets; use `local.properties` locally and CI `-P` properties.

## How to Work

1. Read `CONTEXT.md`, `schema.ts`, and/or `TODO.md` depending on the task.
2. Inspect the relevant code and migrations; trust actual code over stale assumptions.
3. Implement the change within the documented package boundaries.
4. Add or update focused tests when behavior changes.
5. Update the root source-of-truth file when the public contract, design rule, or implementation status changes.
