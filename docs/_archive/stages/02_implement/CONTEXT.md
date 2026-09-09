# Stage 02 — Implement (Layer 2 contract)

> **ARCHIVED AND STALE.** This file describes a superseded three-stage pipeline. Do not
> implement against it. The paths in the Inputs table below no longer resolve — the root
> `CONTEXT.md`, `AGENTS.md`, and the `stages/` location they were written for are all gone.
> The live entry point is `SESSION_INIT.md` at the repository root; the live rules are
> `docs/constraints.md` and `docs/non-negotiables.md`. See `../../CONTEXT.md` for why this
> folder is retained.

## Inputs

| Kind | File / source | What to load |
|---|---|---|
| ~~Layer 4 (working)~~ | ~~`../01_scope/output/scope.md`~~ | **Stale.** The stage pipeline no longer runs; `output/` is empty |
| ~~Layer 3 (reference)~~ | ~~`../../CONTEXT.md`~~ | **Stale — file deleted.** Nearest live equivalent: `/docs/constraints.md` (C1–C5, C11) |
| ~~Layer 3 (reference)~~ | ~~`../../schema.ts`~~ | **Stale path.** `schema.ts` is at the repository root; see `/docs/map/objects/` |

## Process

Write code for every file listed in `scope.md`. Respect MVVM layer boundaries at all times.

1. Load `scope.md` — implement exactly what is listed; record any deviation in the notes.
2. Write Kotlin following ktlint (`android_studio` style, max 120 chars, 4-space indent, final newline).
3. Apply correct naming: `PascalCase` classes, `camelCase` functions/properties, `UPPER_SNAKE_CASE` constants.
4. Apply correct suffixes: `…Entity`, `…Dao`, `…UseCase`, `…Repository` / `…RepositoryImpl`, `…ViewModel`.
5. Add KDoc on every public class / interface / function / property. Private: only if non-obvious.
6. Use Cases return a sealed `Result<T>`; ViewModels expose a single `StateFlow<ScreenState>`.
7. For UI: use `AppColors.*`, MaterialTheme typography tokens, 8-px grid spacing, and design system radii. Never raw hex or hardcoded strings.
8. For UI composables: screen composables take a ViewModel via `hiltViewModel()` and read `collectAsStateWithLifecycle()`; content composables take plain state + callbacks and have a `@Preview`. `Modifier` is always last, defaulting to `Modifier`.
9. One-shot events (navigation, snackbar) via `SharedFlow` on the ViewModel.
10. If a Room migration is required: increment schema version, write the migration in `AgarthaDatabase`, export the schema JSON.
11. If a Supabase migration is required: write `supabase/migrations/00<NN>_<name>.sql`. Apply manually in dashboard — do NOT apply programmatically.
12. Write or update unit tests for all changed Use Cases and ViewModels. Test names read as sentences.

**Human review gate:** The developer reviews the diff before stage 03 begins.

## Outputs

`implementation_notes.md` → `output/`

```
# Implementation Notes: <task name>

## Changed files
| File | Change summary |
|---|---|

## Decisions made
- ...

## Deferred (not in this PR)
- ...

## Tests added / updated
| Test file | What it covers |
|---|---|
```
