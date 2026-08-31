# Stage 01 — Scope (Layer 2 contract)

## Inputs

| Kind | File / source | What to load |
|---|---|---|
| Layer 4 (working) | Task description (user message or task item) | The full task text |
| Layer 3 (reference) | `../../CONTEXT.md` | §1 Project Overview, §3 Architecture Rules, §7 Coding Conventions |
| Layer 3 (reference) | `../../schema.ts` | Any entity or migration the task touches |

Load nothing else. Smaller context = better scope.

## Process

Produce a concrete implementation plan for the given task.

1. Identify affected layers: Presentation / Domain / Data / Core. Name each file that changes.
2. Check whether a Room migration and/or Supabase SQL migration is required.
3. Cross-reference existing architecture guidelines in `CONTEXT.md`.
4. For UI tasks: identify which design tokens apply (`AgarthaTheme.colors.*`, typography style, spacing, radius).
5. List all tests that must be added or updated.
6. Check each of the seven Architecture hard rules — any violation is a blocker; flag it explicitly.
7. Describe anything deferred (explicitly out of scope for this task).

**Human review gate:** The developer confirms or edits `output/scope.md` before stage 02 begins.
Do not proceed to stage 02 until the developer has approved.

## Outputs

`scope.md` → `output/`

```
# Scope: <task name>

## Summary
One or two sentences.

## Affected files
| File | Change description | Layer |
|---|---|---|

## Schema / migration
None  —OR—  Room v<N+1>: <what changes> + Supabase 00<NN>_<name>.sql: <what changes>

## Design tokens (UI tasks only)
List only tokens actually used.

## Tests to add / update
- ...

## Architecture checks
- [ ] No ViewModel imports Room / Retrofit / Supabase
- [ ] No Android import in domain/
- [ ] Use Case returns Result<T>
- [ ] Repository interface in domain/, impl in data/
- [ ] New entity in data/local/entity/, domain model in domain/model/

## Deferred
- ...
```
