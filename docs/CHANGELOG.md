# Changelog

Reconstructed from `git log`. **This project has never cut a release** — there are no tags,
no `versionName` bump beyond the initial `0.1.0-mvp` (`app/build.gradle.kts:36`), and no
release branch. What follows is grouped by the work that actually landed, dated from the
commits themselves. Nothing here is invented.

Verify any entry with `git log --oneline --reverse`.

---

## feat/build-optimization — cloud-only inference, bug fixes for validation · 2026-09-06

Cut from `staging`. Carries forward the documentation shelf, the admin storage policy fix,
the offline-access fixes and the tooling templates, and leaves on-device inference behind.

**Mobile compute deferred.** An on-device TFLite path was built and benchmarked on a Redmi
Note 11 over 14 real capture frames, 3 iterations, 42 frames per engine, 0 failures:

- Device infer p50 **20,800 ms** (p95 21,804, max 29,389) against cloud end-to-end p50
  **2,784 ms** — 7.5x slower, and 10x slower than the 2-second capture cadence the app
  samples at. The GPU delegate never ran: TFLite built it, rejected the graph over a
  dynamic-sized tensor, and fell back to XNNPACK CPU, so that is the CPU ceiling. The
  exported artifact is 78 MB against the 40-45 MB the export README predicted.
- 0.00% recall against cloud with **zero boxes matched** at IoU 0.5, while detection counts
  agreed on 13 of 14 frames including the negative. That pattern is a coordinate-decode
  fault, not a weak model — fixable, and irrelevant, because fixing geometry does not fix
  21 seconds.

The engine, selector, benchmark harness and Python export tooling are preserved on
`feat/offline-inference`. Rationale and next steps are in the vault note
`offline-inference-deferred.md`.

**Kept from that branch, because neither needs TFLite:**

- The domain inference abstraction — `domain/inference/` types, and `FlaggedFrame` now holds
  domain `Prediction` values instead of the wire `PredictionDto`, so `domain/` no longer
  imports a response type.
- `RemoteInferenceEngine`, now the cloud call path. `InferFrameUseCase` routes through it
  rather than reaching into `InferenceApi` itself (C1), transport errors go through
  `NetworkErrorMapper`, and the container's `inference_ms` reaches the app so cloud compute
  is separable from network time.

**Also in this branch:**

- Fixed: the Settings screen was unreachable — the route was registered but no tab or
  affordance pointed at it. The bottom bar now carries a fourth tab, and tab labels moved
  from hardcoded literals to `strings.xml`.
- Fixed: C11 and `non-negotiables.md` claimed "no icon library" while
  `material-icons-extended` has been a declared dependency and is used in four screens.
- Fixed: `features.md` claimed the bottom bar shows on every screen but Login and Capture;
  it is gated on `bottomBarRoutes`, so drill-downs hide it too.
- Restored the ignore rules for model artifacts and capture fixtures, which had arrived with
  the dropped on-device commits.

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
