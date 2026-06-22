# Stage 03 — QA (Layer 2 contract)

## Inputs

| Kind | File / source | What to load |
|---|---|---|
| Layer 4 (working) | `../02_implement/output/implementation_notes.md` | Changed files and decisions |
| Layer 3 (reference) | `../../CONTEXT.md` | §3 Architecture Rules, §6 Git Workflow, §7 Coding Conventions |
| Layer 3 (reference) | `../../AGENTS.md` | Compliance rules and source-of-truth precedence |
| Layer 3 (reference) | `references/qa-checklist.md` | Architecture + design compliance checklist |
| Layer 3 (reference) | `references/build-commands.md` | Lint, test, and build commands |

## Process

Verify the implementation and produce a pre-PR artifact.

1. Load `implementation_notes.md` — know exactly which files changed before running anything.
2. Run `bun run lint` (`ktlintCheck detekt`). Zero violations required.
3. Run `bun run test` (`testDebugUnitTest`). Zero failures required.
4. Work through `qa-checklist.md` against the changed files. Mark each item pass/fail.
5. Verify that `CONTEXT.md` and/or `schema.ts` are updated if any public contract, design rule, or architecture decision changed.
6. Verify that `TODO.md` is updated: the backlog item is marked ✅; any resolved Known Issues are updated.
7. Draft a PR description: title (under 70 chars) + summary bullets + test-plan checklist.
8. Confirm the branch name follows the convention and all commits are conventional.

**Human review gate:** The developer signs off on the QA report, then opens the PR against `develop`.

## Outputs

`qa_report.md` → `output/`

```
# QA Report: <task name>

## Lint
`bun run lint` result: PASS / FAIL
Violations (if any):
- ...

## Tests
`bun run test` result: PASS / FAIL
Failures (if any):
- ...

## Architecture compliance
(Copy qa-checklist.md with results filled in)

## Documentation sync
- [ ] CONTEXT.md updated (if contract, rule, or ADR changed)
- [ ] schema.ts updated (if data model changed)
- [ ] TODO.md updated (backlog item marked ✅; known issues updated)
- [ ] AGENTS.md updated (if a new non-negotiable or source-of-truth rule was added)

## PR draft

**Title:** feat(scope): description

**Summary:**
- ...
- ...

**Test plan:**
- [ ] ...
- [ ] ...

**Branch:** feat/<scope>-<desc>
**Target:** develop
```
