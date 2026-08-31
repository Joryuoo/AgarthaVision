# Changelog

Reconstructed from `git log`. **This project has never cut a release** — there are no tags,
no `versionName` bump beyond the initial `0.1.0-mvp` (`app/build.gradle.kts:36`), and no
release branch. What follows is grouped by the work that actually landed, dated from the
commits themselves. Nothing here is invented.

Verify any entry with `git log --oneline --reverse`.

---

## Unreleased — documentation architecture · 2026-08-31

Replaced the previous agent-documentation setup with a router plus a shelf.

- Added `SESSION_INIT.md` as the single session entry point, and `docs/` with a contract in
  each subfolder.
- Archived the three-stage scope/implement/QA pipeline to `docs/_archive/stages`.
- Added the rules layer (`constraints.md`, `non-negotiables.md`, `stack.md`, `commands.md`),
  the inventory layer (`features.md`, this file, `file-tree.md`), and the system map
  (`map/objects`, `map/processes`, `map/effects`).
- Retired the root `AGENTS.md` and `CONTEXT.md`.

## Tooling and hooks · 2026-08-11

- Husky git hooks: `pre-commit` runs compile → unit tests → `assembleDebug` → ktlint + detekt;
  `pre-push` runs `assembleDebug`. `JAVA_HOME` auto-detection with an Android Studio JBR
  fallback.
- **The `commit-msg` hook was added and then removed in the same day's work** — commit
  format is no longer checked by anything. See `constraints.md` C9.
- Documentation consolidation: the previous multi-file documentation tree collapsed into two
  root files, `CONTEXT.md` and `schema.ts`.

## Detekt debt cleanup · 2026-07-10

Mechanical fixes, targeted DI suppressions, and parameter bundling to reach a zero-violation
detekt run.

## Offline access and identity · 2026-07-09

The largest behavioural change in the project's history.

- The app now opens on the Dashboard; the login wall is gone
  (`ui/navigation/AgarthaNavGraph.kt:121`).
- Sessions, manual captures, and verification work fully offline; a cached `LocalIdentity`
  attributes offline work to the last signed-in medtech.
- Unowned rows are claimed at the next login, cascading sessions → samples → reports, followed
  by a trigger-based sync pass.
- Per-session "link to account" opt-out (`claim_exempt`).
- Sign-out: revokes the token *and* clears the cached identity; blocked while a session is
  active.
- New Settings screen: account, data and sync, appearance, about.
- Room schema v8 — `sessions.supabase_status`, `sessions.claim_exempt`, nullable
  `samples.user_id`. **No Supabase migration**; all three are Room-only.

## Rebrand · 2026-07-07

CIT-U maroon and gold replace the previous cobalt palette, with a light/dark mode toggle
persisted in DataStore. The earlier KomoUI design system is removed from both the app code and
the Gradle dependency graph.

## UI polish and branding · 2026-06-13 → 2026-06-27

Deprecated files deleted, records theming cleaned up, raw confidence values removed from the
medtech-facing UI, dialog corner radius fixed, custom adaptive launcher icon added, global
screen padding and keyboard clipping fixed. Kaggle setup added for inference experimentation.
The first agent-documentation setup ("ICM for AI tools") landed on 2026-06-22 — that is the
structure this change replaces.

## Reports · 2026-05-29

Persisted session reports as first-class rows in Room and Supabase (migration
`0008_reports.sql`), row-only sync with the CSV staying local, and the full UI screen revamp
against the design system.

## EPG, manual capture, sessions-as-smears · 2026-05-28

- Session redefined as one fecal smear; `sessions.label` added (`0005_session_label.sql`).
- Manual capture: `samples.is_manual` (`0006`) with nullable detection bounding boxes
  (`0007`).
- EPG shipped via the hardcoded Kato-Katz multiplier of 24.
- Verification queue converted from a sheet to a full screen; shutter button; species dropdown
  backed by the `EggSpecies` enum.

## Records browser · 2026-05-27 → 2026-05-29

Records list, session detail, sample detail with a signed-URL Storage fallback for images,
records filter bar, and local sample persistence.

## Sprint 1 — capture, auth, sync · 2026-05-25 → 2026-05-26

Supabase login flow, continuous frame sampling wired to inference, recording sessions
persisted to Supabase, `SyncSampleUseCase`, inference connection monitoring, and the
verification sheet scaffold. Migrations `0001`–`0004` land in this window.

## Project initialization · 2026-05-22

Gradle setup, project structure, README.
