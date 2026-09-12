# Changelog

Reconstructed from `git log`. **This project has never cut a release** — there are no tags,
no `versionName` bump beyond the initial `0.1.0-mvp` (`app/build.gradle.kts:37`), and no
release branch. What follows is grouped by the work that actually landed, dated from the
commits themselves. Nothing here is invented.

Verify any entry with `git log --oneline --reverse`.

---

## refactor/never-ending-sessions — sessions stay open, the queue holds everything · 2026-09-13

`86d4ab4vm` with `86d4ab4tq`, `86d4ad75y` and a cherry-picked `86d4a6jwy`. The session
lifecycle, the verification queue model, and what "delete" means — from the 2026-09-07
consultation with Dr. Bayron.

**A session no longer ends.** One session is one fecal smear and the medtech keeps coming back
to it, so `stopSession` is gone and nothing writes `sessions.ended_at`. The column stays
nullable and `SessionRemoteDataSource.closeSession` stays with it, because sessions closed
before this are real history; `resumeSession` still refuses to reopen one. Three things fell out
of it: sign-out now detaches (`SessionManager.clearActive`) instead of being blocked forever,
`NetworkMonitor` polls `/health` only while a capture screen is mounted instead of forever, and
the active session id is persisted and restored at launch — without which a process restart
would come back idle with a smear still open and render the queue empty.

**The queue is the whole session, in two buckets.** Verified and Unverified; the AI/Manual split
was dropped as unnecessary (revised 2026-09-12). Verified samples stay visible and reopen with
the medtech's own previous answers, so an edit is a correction rather than a re-review. New
`QueueSample` carries no `ByteArray` — the old row rendered from bytes re-read off disk on every
emission, survivable while the queue drained and an OOM risk once it only grows.

**Delete replaces the repeat flag.** Long-press for batch select; an unverified frame is
hard-deleted, a verified sample is tombstoned via `samples.deleted_at`. `is_repeat` existed only
because deletion was impossible, and it is removed entirely — including from the report CSV,
which loses a column. The confirmation dialog states the split, because the two halves are
irreversible in different ways.

**One verification screen.** `ManualSheet`, `ManualCaptureViewModel` and
`SubmitManualCaptureUseCase` are deleted (~1,150 lines). A manual capture is just a frame with
no model output: no box means no box questions. Polyparasitism lands with it —
`sample_species_findings` records several species and stages per frame with per-species counts,
because WHO thresholds are species-specific and a combined per-field count cannot be graded.

**Schema:** Room 9 → 12 (skipping two contested numbers), migrations `0012` and `0013`, and
`AgarthaDatabaseSchemaTest` pinning the result so the collision that nearly shipped in September
cannot recur silently.

Two hazards worth knowing about, both fixed before they shipped: detection ids were random, so
re-saving an edited sample would have appended a second full set and doubled every egg count
with no error anywhere; and `updateSampleOnVerify` nulled `predictions_json`, which would have
left a reopened sample with no boxes to draw.

---

## fix/framesampler-stale-cache — camera frames carry a freshness stamp · 2026-09-11

`86d4au2n1`. A shutter tap landing before the analyzer delivered a frame for the *current*
camera binding recorded the **previous session's image** under the current `sessionId`.
`FrameSampler` is `@Singleton`, its cache was never reset, and `CaptureViewModel.onCapture`
guarded only against `null` — a stale array is not null, so the guard passed and the frame was
persisted. Reachable on a second session in one process, on re-entering the capture screen, and
on any camera rebind. In this app a session is a patient, and C8 makes a misattributed frame
permanent once it is verified.

- `FrameSampler` now publishes `CachedFrame(jpegBytes, elapsedRealtimeMs)` from `latestFrame`
  (was `latestFrameBytes`), stamped as the frame is encoded.
- `CaptureViewModel.onCapture` rejects anything older than `MAX_FRAME_AGE_MS` (1 s) with the
  existing "Waiting for a live frame" message. A bound analyzer delivers ~30 fps, so a live
  frame is never more than ~33 ms old; every stale path leaves a far longer gap. Fails closed
  with no lifecycle wiring to maintain, which is why this is a stamp and not a reset call.
- New `core/util/ElapsedClock.kt`, bound unscoped in `core/di/ClockModule.kt`. A seam over
  `SystemClock.elapsedRealtime()` so both classes stay unit-testable on the plain JVM —
  `app/build.gradle.kts` does not set `returnDefaultValues`. Unscoped keeps C5's `@Singleton`
  list closed.

`FrameSampler` gained no session knowledge; it remains a camera-layer component.

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

**Field-test fixes · 2026-09-06.** Four issues from a session on real hardware, plus the two
latent defects they exposed. Remaining findings are logged in the vault, not here.

- Fixed: the verification sheet's frame counter was stuck at `1/144` while Next/Previous
  changed the image underneath. `setFrame` never recomputed `frameIndexInQueue` — only the
  store collector did, and paging does not make the store re-emit. Both paths now share one
  helper, and the frame buttons dim at the ends of the queue.
- Fixed: the toast rendered top-center over the verification queue button. That button and its
  badge moved to the bottom-left slot, replacing a `FRAMES` pill that counted the same value.
- Fixed: the JPEG posted for inference had device-dependent geometry, was never rotated out of
  sensor orientation, and had no on-screen boundary. Preview and analysis now share a 4:3
  field of view, frames are rotated, centre-cropped square and downscaled to a uniform 640,
  and `CaptureFrameBoundary` marks the captured region. The preview moved to `FIT_CENTER`, so
  it is no longer edge-to-edge — under `FILL_CENTER` the analysed region is wider than the
  screen and no boundary could be honest.
- Fixed: detection toasts were verbose, queued behind one another, and could not be dismissed.
  Copy is now `{species} detected` through `EggSpecies.fromClassLabel`, they last 2s, a new one
  replaces the old, and either swipe dismisses. Manual capture gained a toast at all.
- Fixed: sharing a report meant leaving the app for a file manager. The generation snackbar
  carries a `Share` action, and reports moved from the Downloads root to
  `Documents/AgarthaVision/` — through `MediaStore` on API 29+, which is the only way to make
  a folder in shared storage. `shareReportCsv` handles both the `content://` and path shapes
  and now reports failures instead of returning silently.

**Second defect pass · 2026-09-07.** Six latent defects found while fixing the field-test
issues, plus ten items from a second round of testing.

*Correctness.*

- Fixed: marking a frame as repeat left the verification queue's Repeat filter and chip counts
  stale. `FlaggedFrame.equals` compared `sampleId` alone, so the Room re-emission compared equal
  to the list already held and `StateFlow` conflated it away. Equality now covers every property
  except `jpegBytes`, which must stay out — the store re-reads the JPEG from disk on each
  emission, so array reference equality would make a frame unequal to itself. Both sheets moved
  their `LaunchedEffect` key to `sampleId` in the same change: widening equality alone would have
  re-seeded the sheet on every emission and wiped in-progress answers.
- Fixed: the queue list keyed rows by `capturedAt` millis, so two frames captured in the same
  millisecond crashed it. Keyed by `sampleId` now.
- Fixed: the capture toast keyed on `capturedAt` too, so two frames sharing a millisecond
  produced no toast at all.
- Fixed: frame analysis ran on the main thread. `FrameSampler` JPEG-encodes every frame before
  throttling, and the capture-boundary work had made that heavier.
- Changed: repeat frames no longer block ending a session. Only unverified, non-repeat frames do.

*Frame cycling.*

- Fixed: Next/Previous walked the whole store, so paging from a detection could land on a manual
  capture while the AI sheet kept rendering. Each sheet now cycles its own source only.
- Added: the manual sheet gained the navigation the AI sheet always had — prev/next, a position
  readout, and delete advancing to a neighbour. `ManualCaptureViewModel` had no tests; it has six.

*Interface.*

- Changed: the capture toast moved below the top chrome instead of over it, and the vacated
  top-right slot became a Records shortcut to the active session.
- Removed: the session row kebab menu. Its export item was a stub, but note that link/unlink
  account and end-session-from-the-list had no other entry point and are now unreachable.
- Changed: session rows show unverified frame counts — repeats excluded, matching the
  end-of-session check — in place of the Active badge.
- Removed: the bottom bar's Sessions badge, which the nav graph had always passed 0.
- Fixed: the record and settings screens drew under the status bar. One cause — the root graph
  zeroes `contentWindowInsets` app-wide and those two screens never applied their own.
- Fixed: settings used a Switch for the theme while the dashboard used a sun/moon button; the
  empty report state showed two identical generate affordances and a subtitle that squeezed the
  pill out of shape; the two record screens each had their own stat-run composable; back was
  rendered six different ways; and sample detail's label rows had no left padding.

**Repeat filter and preview fixes · 2026-09-07.**

- Fixed: a frame marked repeat still appeared under the AI chip and was still reachable by
  Next/Previous, so a duplicate could be verified by accident. The AI category now means "AI
  frames still needing review"; repeats stay reachable under Repeat and All. Marking the open
  frame drops it from the cycle but keeps it on screen so the mark can be undone — it reports no
  position and both frame buttons dim, rather than showing `Frame 0/N`.
- Fixed: the manual capture preview used `ContentScale.Crop` where the AI sheet uses `Fit`, so a
  square 640x640 frame overflowed the landscape container and the rounded clip cut the top and
  bottom off. The medtech was labelling a specimen they could only partly see.
- Corrected in `constraints.md`: C1's as-built note claimed a composable still rendered a wire
  DTO. That stopped being true when `FlaggedFrame` moved to domain `Prediction` values; nothing
  under `ui/` imports from `data/remote/dto/`.

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
  format went unchecked from then until `12509f8` restored it. See `constraints.md` C9.
- Documentation consolidation: the previous multi-file documentation tree collapsed into two
  root files, `CONTEXT.md` and `schema.ts`.

## Detekt debt cleanup · 2026-07-10

Mechanical fixes, targeted DI suppressions, and parameter bundling to reach a zero-violation
detekt run.

## Offline access and identity · 2026-07-09

The largest behavioural change in the project's history.

- The app now opens on the Dashboard; the login wall is gone
  (`ui/navigation/AgarthaNavGraph.kt:120`).
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
