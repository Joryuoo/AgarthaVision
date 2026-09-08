# Stage 03 — QA (Layer 2 contract)

> **ARCHIVED AND STALE.** This file describes a superseded three-stage pipeline. Do not
> implement against it. The paths in the Inputs table below no longer resolve — the root
> `CONTEXT.md`, `AGENTS.md`, and the `stages/` location they were written for are all gone.
> The live entry point is `SESSION_INIT.md` at the repository root; the live rules are
> `docs/constraints.md` and `docs/non-negotiables.md`. See `../../CONTEXT.md` for why this
> folder is retained.

## Inputs

| Kind | File / source | What to load |
|---|---|---|
| ~~Layer 4 (working)~~ | ~~`../02_implement/output/implementation_notes.md`~~ | **Stale.** The stage pipeline no longer runs; `output/` is empty |
| ~~Layer 3 (reference)~~ | ~~`../../CONTEXT.md`~~ | **Stale — file deleted.** Nearest live equivalent: `/docs/constraints.md` |
| ~~Layer 3 (reference)~~ | ~~`../../AGENTS.md`~~ | **Stale — file deleted.** Nearest live equivalent: `/docs/non-negotiables.md`, `/SESSION_INIT.md` |

## Process

Verify the implementation and produce a pre-PR artifact.

1. Load `implementation_notes.md` — know exactly which files changed before running anything.
2. Run `bun run lint` (`ktlintCheck detekt`). Zero violations required.
3. Run `bun run test` (`testDebugUnitTest`). Zero failures required.
4. Work through the QA Checklist below against the changed files. Mark each item pass/fail.
5. Verify that `CONTEXT.md` and/or `schema.ts` are updated if any public contract, design rule, or architecture decision changed.
6. Draft a PR description: title (under 70 chars) + summary bullets + test-plan checklist.
7. Confirm the branch name follows the convention and all commits are formatted as `[type][ClickUp-ID][Lastname] Task title`.

**Human review gate:** The developer signs off on the QA report, then opens the PR against `staging`.

---

## QA Checklist

### 1. Architecture Layer Compliance
- [ ] No ViewModel imports Room / Retrofit / Supabase — only Use Cases
- [ ] No Android import in any file under `domain/`
- [ ] All (new/changed) Use Cases have exactly one public `invoke` / `execute` entry point
- [ ] All (new/changed) Use Cases return a sealed `Result<T>`
- [ ] Repository interfaces live in `domain/repository/`; impls live in `data/repository/`
- [ ] New entities in `data/local/entity/`; domain models in `domain/model/`; mappers in `data/local/mapper/`
- [ ] `@Singleton` used only for approved components (database, HTTP, SupabaseClient, SessionManager, FlaggedFrameStore, FrameSampler)

### 2. Design System Compliance
- [ ] No raw hex colors in app code (only in palette definition file)
- [ ] No raw `fontSize` or `fontWeight` — MaterialTheme typography tokens only
- [ ] All spacing values are 8-px multiples: 4, 8, 12, 16, 20, 24, 32, 48 dp
- [ ] Radius values: sm=8, md=12, lg=16, pill=999 dp only
- [ ] Species names are italic (`FontStyle.Italic`)
- [ ] Capture screen is dark/immersive; all other screens follow light/dark theme preference
- [ ] No new charting library or icon library added to Gradle dependencies
- [ ] No hardcoded user-facing strings — all in `strings.xml`

### 3. Code Style & Testing
- [ ] `bun run lint` passes: 0 ktlint violations, 0 Detekt findings
- [ ] `bun run test` passes: 0 failures
- [ ] One public type per file (unless sealed class group or enum + extensions)
- [ ] KDoc on every public class / interface / function / property
- [ ] Test method names read as sentences
- [ ] Error markers include owner name: `// TODO(lastname)`, `// FIXME(lastname)`, `// HACK(lastname)`

### 4. Schema & Migrations (if data model changed)
- [ ] Room migration written and registered in `AgarthaDatabase` (new schema version exported to `app/schemas/`)
- [ ] Supabase SQL migration file committed to `supabase/migrations/` with correct number prefix
- [ ] Migration NOT applied programmatically — requires manual run in Supabase dashboard SQL editor
- [ ] `schema.ts` updated to reflect new columns / tables / enums
- [ ] `CONTEXT.md` updated if the migration adds a new ADR or changes an existing one

### 5. Git Hygiene & Documentation Sync
- [ ] Branch name follows convention (`feat/…`, `fix/…`, `refactor/…`, `docs/…`, `ci/…`, `test/…`)
- [ ] All commits follow format: `[type][ClickUp-ID][Lastname] Task title`
- [ ] PR targets `staging` (never `main` directly)
- [ ] PR diff is ≤ ~400 lines (split into multiple PRs if larger)
- [ ] No `local.properties`, `.idea/`, or build artifacts committed
- [ ] `CONTEXT.md` updated if contract, design rule, or ADR changed
- [ ] `AGENTS.md` updated if a new non-negotiable or source-of-truth rule was added

---

## Outputs

`qa_report.md` → `output/`

```markdown
# QA Report: <task name>

## Lint
`bun run lint` result: PASS / FAIL

## Tests
`bun run test` result: PASS / FAIL

## Architecture compliance
(Copy QA checklist above with results filled in)

## PR draft

**Title:** [feat][CU-XXXXXX][Lastname] Description
**Branch:** feat/<scope>-<desc>
**Target:** staging
```
