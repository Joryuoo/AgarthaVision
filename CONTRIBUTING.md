# Contributing to AgarthaVision

AgarthaVision is built by team `2526-sem2-cs342-02` at Cebu Institute of Technology as an
academic capstone project. The repository is public, but day-to-day task tracking happens in
ClickUp, not GitHub Issues — see the note on external contributions below.

This document covers workflow and conventions. For what the project is and how the codebase is
organized, start at [`SESSION_INIT.md`](SESSION_INIT.md) and [`README.md`](README.md).

## Before you start

- Read [`docs/non-negotiables.md`](docs/non-negotiables.md) — the terse list of things this
  codebase never does. Each line points at the constraint in
  [`docs/constraints.md`](docs/constraints.md) that explains why and what enforces it.
- Read [`docs/features.md`](docs/features.md) to see what already ships before proposing
  something that might already exist.
- Do not add a `TODO.md`, roadmap, or sprint plan to this repository. Tasks are tracked in
  ClickUp by the core team.

## External contributions

If you're not on the core team and want to propose a change:

1. Open a GitHub issue that stays problem-focused: context, how to reproduce (if applicable),
   actual vs. expected behavior, and acceptance criteria. If you suspect a root cause or have an
   implementation idea, include it, but label it as a hint rather than a requirement — the fix
   might belong somewhere you don't expect.
2. Wait for a maintainer to confirm scope before opening a PR — this avoids wasted work on
   something that conflicts with an in-flight ticket or a deliberate design decision (several are
   recorded in `docs/constraints.md`, e.g. "no server-side confidence filter" or "nothing is
   deleted").
3. Follow the setup, branch, commit, and PR conventions below.

## Development setup

See [`README.md`](README.md#getting-started) for prerequisites and initial setup
(JDK 21, Android Studio, Bun, `local.properties`). Never commit `local.properties` or any real
Supabase/inference credential — see C10 in `docs/constraints.md`.

## Branching

- Branch from `development`, named `<type>/<description>` — e.g. `feat/verified-findings-reporting`,
  `fix/report-upsert-skip-existing`.
- A branch name describes the work, not a ticket ID. One branch can carry commits for more than
  one ticket, and the ticket ID is already captured per commit (see Commits below).
- Types: `feat enhancements fix security docs ui ux uiux refactor test ci chore`.

## Commits

Format, enforced by `.husky/commit-msg`:

```
[type][ClickUp-ID][Lastname]: Task title
```

Example: `[feat][86d4a6mpt][Ledon]: Add the settings screen`

- `type` is one of the types listed above.
- Core team members use the real ClickUp task ID. External contributors without ClickUp access
  should reference the GitHub issue number instead (e.g. `[fix][gh-42][Yourname]: Fix crash on
  empty barangay list`) — a maintainer will reconcile it with a ClickUp ID on merge if needed.
- Commits are sole-authored: no `Co-Authored-By` trailer, no AI/tool attribution line, in the
  commit subject, body, or the PR description.
- `git commit --no-verify` bypasses the hook and is acceptable for a docs-only change that
  touches no Kotlin, Gradle, or SQL (the hook gates on a full Android build). It is not a
  general-purpose escape hatch.

## Before you push

Build and test locally — this is enforced by `.husky/pre-commit` and `.husky/pre-push`, but
don't rely on the hook alone:

```bash
bun run build
bun run test
bun run lint
```

On Windows PowerShell, use `.\gradlew.bat assembleDebug` if the shell does not resolve
`./gradlew`. See [`docs/commands.md`](docs/commands.md) for the full command reference.

## Pull requests

- Target `development` — never open a PR directly against `staging` or `main`.
- CI (`.github/workflows/build-and-test.yml`) runs `:app:verifyRoborazziDebug` on every PR;
  it must pass.
- If your change affects behavior a doc in `docs/` describes, update that doc in the same PR —
  code wins over docs, but a doc left stale is a bug (C13).
- If a Roborazzi screenshot golden fails because of an intentional UI change, re-record it with
  `./gradlew :app:recordRoborazziDebug` and review the diff before committing the new image.

## Code standards

The full set of architectural, clinical, and process constraints — with what actually enforces
each one — lives in [`docs/constraints.md`](docs/constraints.md). A few worth knowing up front:

- ViewModels call domain use cases, not Room/Retrofit/Supabase directly.
- `domain/` stays free of Android imports.
- No model output counts as a finding until a human confirms it — never add a server-side
  confidence filter.
- Nothing verified is hard-deleted; a rejection or duplicate is tombstoned, not removed.
- One design system, one theme — see C11 for icon and color-token rules before adding a new one
  of either.
- User-facing strings go in `strings.xml`, not hardcoded.

## Code of conduct

Be respectful and constructive in issues, PRs, and reviews. Disagreement about approach is
normal and welcome; personal attacks, harassment, or dismissiveness are not.
