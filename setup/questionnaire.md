# Workspace Setup — Completed Questionnaire

## 1. Domain / what does this workflow produce?

Reviewed, lint-clean, test-passing Android app code for AgarthaVision — a clinical microscopy
AI assistant for Soil-Transmitted Helminth (STH) surveillance. Each pipeline run produces
a scoped plan, implementation, and QA report for one sprint backlog item.

## 2. Steps in order (input to deliverable)

1. **Scope** — Analyse a sprint backlog item; write a concrete plan (which files, which layers, test impact)
2. **Implement** — Write code within MVVM + Clean Architecture; produce implementation notes
3. **QA** — Run lint + tests; verify architecture + design compliance; draft the PR description

## 3. Human review breakpoints

- After stage 1 (`scope.md`): developer confirms the plan before any code is written
- After stage 2 (diff review): developer inspects the diff before QA begins
- After stage 3 (`qa_report.md`): developer signs off, then opens the PR against `develop`

## 4. What stays the same every run (Layer 3 — the factory)

- MVVM + Clean Architecture rules (seven hard rules, package map, sample lifecycle)
- Clinical Microscopy design system (Inter, cobalt #1E3FD9, 8-px grid, component conventions)
- Coding conventions (ktlint, Detekt, naming, KDoc, error handling, resource rules)
- Clinical voice (restrained, scientifically precise, italic binomials, tabular numerals)
- Git and commit conventions (conventional commits, branch naming, PR rules)
- Data model (schema.ts — Supabase tables, Room entities, domain enums)
- AGENTS.md compliance rules and source-of-truth precedence

## 5. What changes every run (Layer 4 — the product)

- The specific backlog item being worked on (provided by the developer)
- `01_scope/output/scope.md` — scoped implementation plan for this run
- `02_implement/output/implementation_notes.md` — changed files and decisions for this run
- `03_qa/output/qa_report.md` — lint/test results, compliance checklist, PR draft for this run

---

Project: AgarthaVision · Team: 2526-sem2-cs342-02
Repo: https://github.com/Joryuoo/AgarthaVision.git
Workspace initialized: 2026-06-22
