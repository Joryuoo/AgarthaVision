# Sprint 2 Progress Report

**Last updated:** 2026-05-27
**Spec authority:** [../03_MOBILE_APP_PLAN.md §2](../03_MOBILE_APP_PLAN.md) (Sprint 2)
**Architecture authority:** [../02_PROJECT_ARCHITECTURE.md](../02_PROJECT_ARCHITECTURE.md) · [../adr/004-verification-as-hitl-correction.md](../adr/004-verification-as-hitl-correction.md)

> **2026-05-27 sprint kickoff.** Sprint 1 closed with on-device verification +
> the v2 inference container live on GHCR; see
> [sprint1_progress_report.md](sprint1_progress_report.md). A doc↔code audit
> immediately after closeout surfaced eight conformance gaps — three were
> hot-fixed (`Roboflow` doc scrub, Target SDK 35→36, `FlaggedFrameStore` disk
> cache promise dropped) and four were fixed in code (KomoUI `Button`+`Badge`
> in `CaptureScreen`, color tokens in `MicroscopyViewport`/`FrameWithBoxes`,
> Sonner lifted to top-center, ViewModel KDoc backfilled). One low-priority
> item (extracting `CaptureScreen` hardcoded strings to `strings.xml`) was
> deferred to this sprint as carry-over **C-1**.

> **2026-05-27 Phase 2 capture model clarified.** Per kuzu's direction baked
> into [00_PROJECT_OVERVIEW.md](../00_PROJECT_OVERVIEW.md) Phase 2 section:
> in Phase 2 the phone's camera is no longer used. An external **modular
> camera** is physically attached to a dedicated on-prem **inference
> hardware** (the IoT capture node, SRS Module 1B); the mobile phone receives
> frames + detections from that hardware over **USB OTG**. The mobile app
> becomes verification + reporting only. **No Sprint 2 work depends on this**
> — it is forward context for Sprint 3+ and Phase-2 planning.

> **2026-05-27 codebase scan.** `RecordsScreen`, `RecordsViewModel`, and
> `GetRecordsUseCase` now exist and provide a Room-backed, current-user-scoped
> list of verified samples. Rows expose the primary non-false-positive detection,
> confidence, captured timestamp, and sync status; species filtering is wired
> through filter chips; date-range filtering exists in the ViewModel but has no
> UI control yet. The full Sprint 2 browser is still incomplete: there is no
> session list/detail route, no `SampleDetailScreen`, no persisted-JPEG detail
> overlay, and no CSV export use case. Carry-over security items C-4 and C-5
> are fixed in code (`SampleDao`/`SessionDao` are user-scoped; JPEGs persist
> under `filesDir/users/{userId}/samples/`). C-1 remains open.

> **2026-05-27 Sprint 2 implementation pass.** The records flow now matches
> the Sprint 2 session-first spec: `RecordsScreen` lists current-user sessions,
> `SessionDetailScreen` lists verified samples for a selected session, and
> `SampleDetailScreen` shows the persisted JPEG with detection boxes plus
> Detections / Metadata tabs. CSV export landed as `ExportSessionUseCase`,
> with Android Downloads writing isolated behind `SessionReportRepository`.
> `GetSessionSamplesUseCase` and `GetSampleDetailUseCase` enforce current-user
> read scoping, and focused unit coverage was added for records summaries and
> CSV generation. Carry-over C-1 is also complete: remaining `CaptureScreen`
> UI strings were moved to `strings.xml`.

> **2026-05-27 post-merge codebase scan.** The downloaded remote
> `CaptureScreen` / `AgarthaNavGraph` changes were reconciled without dropping
> Sprint 2 navigation: `CaptureScreen` now exposes a History action that routes
> to `records`, while `AgarthaNavGraph` still keeps `records/session/{sessionId}`
> and `records/sample/{sampleId}`. The scan also found two remaining spec
> caveats: `SampleDetailScreen` renders the locally persisted JPEG from
> `Sample.filePath` but has no Supabase signed-URL fallback if that file is
> missing, and the records list is not paged yet. Date filtering is implemented
> against session start date in the session-first browser.

---

## Sprint 2 — Records Browser + Session Reports

Each track depends on Sprint 1's Room schema (`AgarthaDatabase` v4 with ADR-004
verification fields) and the `SyncSampleUseCase` from Joryuoo's 1.8 PR.

| §    | Deliverable                                                                                                  | Owner    | Status |
|------|--------------------------------------------------------------------------------------------------------------|----------|--------|
| 2.1  | `RecordsScreen` + `RecordsViewModel` — Room-backed list of verified samples, filter by date/species          | jojseph  | ✅ done — session-first list, Capture→Records shortcut, species filter chips, inclusive session-date text inputs, and route to `SessionDetailScreen`; pagination still deferred |
| 2.2  | `SampleDetailScreen` + `SampleDetailViewModel` — full-screen view of one sample with detections + metadata + JPEG | jojseph  | ⚠️ mostly done — sample detail route, local persisted JPEG display, detection overlay, Detections tab, Metadata tab; Supabase signed-URL fallback not implemented |
| 2.3  | `SessionReportUseCase` — raw CSV export per spec; EPG + DOH PDF deferred to Phase 2                          | jojseph  | ✅ done as `ExportSessionUseCase` — writes raw CSV through `SessionReportRepository` |

### Sprint 2 Acceptance Criteria (from [03_MOBILE_APP_PLAN.md §2.4](../03_MOBILE_APP_PLAN.md))

| Criterion | Status |
|---|---|
| Records list shows all verified samples for the current user, newest first | ✅ current-user session list via `SessionRepository.observeAllSessions(userId)`; session samples remain user-scoped in detail |
| Each row exposes class label, confidence, captured-at, sync status | ✅ `SessionDetailScreen` sample rows show primary class, confidence, captured-at, and `SyncStatusBadge` |
| Tapping a row opens `SampleDetailScreen` with bounding-box overlay on the persisted JPEG | ✅ `records/sample/{sampleId}` route displays `AsyncImage(File(sample.filePath))` with detection boxes |
| Image loads from Supabase Storage; cached locally for offline re-open | ⚠️ local cached JPEG path works; no Supabase Storage signed-URL fallback exists when `Sample.filePath` is unavailable |
| Filter by date range works against `samples.timestamp` | ⚠️ implemented as inclusive text controls against `sessions.started_at`; sample rows remain newest first within session |
| Filter by species works against `detections.expert_class` (falling back to `class_label` when verdict is `CONFIRMED`) | ✅ session summaries aggregate detection species labels; filter matches labels from expert class or model class |
| `SessionReportUseCase` produces a CSV with the columns listed in §2.3 of the spec | ✅ implemented as `ExportSessionUseCase`; columns match §2.3 |
| All Sprint 2 work passes `./gradlew :app:test` with new test coverage for the new use cases / ViewModels | ✅ `:app:testDebugUnitTest` green; new records/export use case tests added |

---

## Carry-overs from Sprint 1

These items were intentionally deferred from the Sprint 1 closeout audit; they
have **no acceptance-criteria impact** on Sprint 2 tracks 2.1–2.3 but should be
picked up opportunistically (especially C-1, which is one file).

| Track | Item                                                                                                              | Priority | Source                                                                                       |
|-------|-------------------------------------------------------------------------------------------------------------------|----------|----------------------------------------------------------------------------------------------|
| C-1   | Extract hardcoded strings in `CaptureScreen.kt` to `strings.xml` (`capture_title`, `capture_rec`, `capture_start`, `capture_stop`, `capture_verify_action_desc`, `capture_permission_required`, `capture_allow_camera`) | low      | ✅ done — capture UI strings now live in `strings.xml`                  |
| C-2   | If permission-screen `MaterialTheme.styles.background`/`foreground` look wrong in dark mode, add explicit dark-tokens or a comment overriding the camera-convention default | low      | Sprint-1 audit · item 6 follow-up                                                            |
| C-3   | **Phase 2 architectural marker** — per-account persistent flagged-frame queue (survives logout/restart, keyed by `user_id`). NOT Sprint 2 work; recorded here so it stays on radar for the Phase 2 planning doc | scope marker | [00_PROJECT_OVERVIEW.md Phase 1 vs 2 table](../00_PROJECT_OVERVIEW.md), [03_MOBILE_APP_PLAN.md §1.5](../03_MOBILE_APP_PLAN.md) |
| C-4   | **Security gap.** `SampleDao.getSamplesPendingSync()` and `SessionDao.observeAllSessions()` do not filter by `user_id`. RecordsScreen MUST filter by current user when it lands (which fixes C-4 implicitly for the read path) — the sync-side query is the remaining concern | **medium** | ✅ fixed — `getSamplesPendingSync(userId)`, `observeAllSessions(userId)`, and `GetRecordsUseCase` read path are user-scoped |
| C-5   | **Per-user JPEG scoping.** `SampleImageStore` writes to global `filesDir/samples/`. Move to `filesDir/users/{userId}/samples/` so cross-user access on a shared device is impossible. SampleDetailScreen will need the user-scoped path | medium   | ✅ fixed — `SampleImageStore.persistJpeg()` writes to `filesDir/users/{userId}/samples/` |

**Cross-reference:** C-4 and C-5 were prerequisites for safe shared-device records
work. Both are fixed in the scanned codebase; `SampleDetailScreen` should consume
the existing user-scoped `imagePath` rather than introducing a new global cache path.

---

## Risks / blockers

| Risk | Likelihood | Mitigation |
|---|---|---|
| Schema migration needed beyond `AgarthaDatabase` v4 (e.g. for filter indices on `samples.timestamp` or `detections.expert_class`) | medium | Coordinate with Joryuoo before writing migrations; existing destructive fallback (`fallbackToDestructiveMigration(dropAllTables = true)`) is fine for Phase 1 dev but a real migration is required before any real-data deployment |
| v2 inference container retirement could orphan unverifiable flagged frames during long demos | low | Existing `NetworkMonitor` latches the connection-loss banner; verify flagged-but-unverified frames remain accessible (already true — see Sprint 1) |
| Records read-path slow on devices with hundreds of verified samples | low–medium | Current session summaries perform per-session/per-sample detection lookups. Add a joined Room projection or Paging 3 if measured slow |
| Sample detail overlay accuracy for legacy pixel-space boxes | low–medium | Current detail overlay expects normalized boxes. If older rows contain raw pixel-space boxes, add image-dimension metadata or a normalizing migration |
| Sample detail cannot rehydrate images after local cache loss | medium | Add a Supabase Storage signed-URL/download fallback keyed by `storage_path`, then cache the JPEG back into the per-user local sample directory |

---

## Pre-sprint checklist

- [x] All Sprint-1 docs↔code mismatches from the post-closeout audit confirmed resolved, including C-1 string extraction.
- [x] Sprint-1 progress report row **1.10** (Metadata binding) marked complete with verification step.
- [ ] PR-template / branch convention from [06_GIT_WORKFLOW_AND_CI.md](../06_GIT_WORKFLOW_AND_CI.md) reaffirmed with the team — Sprint-1 closeout commits (`610e8e49` "feat: sprint 1 tasks finished", `f4b464a` "chore(dashboard): ux modification for sample verification") had several conventions slip (KomoUI Button, strings, colors, KDoc) that a self-review pass would have caught.
- [x] Decide on C-4 / C-5 inclusion in Sprint 2 scope — both were fixed in code before this scan.
- [ ] Confirm jojseph has cycles for all three tracks — if not, redistribute or descope 2.3 (CSV export is the natural cut).

---

## Verification

- ✅ `./gradlew.bat :app:compileDebugKotlin`
- ✅ `./gradlew.bat :app:testDebugUnitTest`
- ✅ `./gradlew.bat :app:ktlintCheck :app:detekt`

---

## 2026-05-28 — Sprint 2 Addition: session-as-smear, EPG, Manual Capture, user_note

Sprint 2 expanded with [ADR-005](../adr/005-session-as-smear-manual-capture-and-repeat-flag.md):
a session now equals one fecal smear, EPG ships with a hardcoded Kato-Katz
multiplier of 24, the medtech can capture frames manually without AI, and the
existing-but-never-wired `samples.user_note` Supabase column finally flows
end-to-end. Implemented in the documented order (source-of-truth + ADR + SQL
first, progress doc last) over tracks 2.4–2.15.

### Schema (Supabase + Room)

| Migration | Purpose |
|---|---|
| `0005_session_label.sql` | Adds `sessions.label` (smear name) + `sessions_user_started_idx`. `sessions.notes` retained for in-session observations |
| `0006_sample_is_manual.sql` | Adds `samples.is_manual boolean not null default false` + `samples_session_manual_idx` |
| `0007_detection_bbox_nullable.sql` | Drops `NOT NULL` on `detections.bbox_x/y/w/h` to permit manual-capture rows with no drawn box |

All three migrations were pushed to Supabase manually with the commented revert
script ready for rollback. Room schema bumped 4 → 5 with destructive fallback;
`SessionEntity.label`, `SampleEntity.userNote/isManual/isRepeat`, and nullable
`DetectionEntity.bboxX/Y/W/H` cover the local mirror. `samples.is_repeat`
is **Room-only** and never syncs (workflow flag for repeat-suspected smears).

### Implementation tracks (DOCS-FIRST → PROGRESS-LAST)

| Track | Deliverable | Key files |
|---|---|---|
| 2.4 | Session label + Room v5 | `0005_session_label.sql`, `SessionEntity`, `SessionRemoteDataSource`, `AgarthaDatabase` |
| 2.5 | Sample flags catch-up | `0006_sample_is_manual.sql`, `SampleEntity.userNote/isManual/isRepeat` |
| 2.6 | Nullable detection bbox | `0007_detection_bbox_nullable.sql`, `DetectionEntity`, `Detection`, null-safe `FrameWithBoxes`/`SampleDetailScreen`/`ExportSessionUseCase` |
| 2.7 | SessionPicker UX | `ui/sessions/SessionPickerScreen.kt`, `SessionPickerViewModel.kt`, `SessionDao.observeActiveAndRecent`, nav refactor Login → Picker → Capture |
| 2.8 | Session lifecycle flip | `SessionState.Active(session, startedAt, isInferenceRunning)`, `endSession(notes)` confirm dialog, no more Start/Stop Recording |
| 2.9 | Auto-inference + manual snapshot | `DisposableEffect` + `LifecycleEventObserver` on Capture, `FrameSampler.latestFrameBytes: StateFlow<ByteArray?>` (`@Singleton`) |
| 2.10 | Mark-as-Repeat + user-note | VerificationSheet kebab (`Mark/Unmark as repeat`), Repeat outline badge, Notes (optional) `Input` |
| 2.11 | Queue filter chips + badges | `QueueFilter` enum (`ALL · FLAGGED · MANUAL · REPEAT`), `FrameSource` enum, `FlaggedFrame.source/markedAsRepeat`, AI/Manual + Repeat pills on rows |
| 2.12 | EPG | `core/util/EpgCalculator.kt` (single-fn, multiplier=24), `domain/usecase/reports/SessionEggCountUseCase.kt`, EPG card on SessionDetail, Reports icon on Capture |
| 2.13 | Manual Capture E2E | `ui/verify/ManualSheet.kt` + `ManualCaptureViewModel`, `domain/usecase/verify/SubmitManualCaptureUseCase`, sample row with `is_manual = true` + one CONFIRMED detection with null bbox |
| 2.14 | user_note wired | `SampleRemoteDataSource` no longer hard-codes `null`; `SampleDetailScreen` Metadata tab + CSV export pick it up |
| 2.15 | Unit tests | `EpgCalculatorTest`, `SessionEggCountUseCaseTest`, `SubmitManualCaptureUseCaseTest`, `SubmitVerificationUseCaseTest`, `SessionPickerViewModelTest`, `VerificationViewModelTest`, `VerificationQueueFilterTest`, `CaptureViewModelTest` — **72/72 passing** |

### EPG semantics (Phase 1)

`EpgCalculator.epg(count) = count * 24` where `count` is the number of
`CONFIRMED` detections in the session, **excluding samples with `is_repeat = true`**.
Manual-capture samples (`is_manual = true`) count toward EPG; repeat-marked
samples never do. Phase 2 will swap the hardcoded multiplier for a
`prep_methods` table (open in [00_PROJECT_OVERVIEW.md](../00_PROJECT_OVERVIEW.md)).

### Verdict semantics (clarification)

Manual-capture detections write `verdict = CONFIRMED` because the medtech
explicitly picked the species — there is no model prediction to judge.
`samples.needs_reannotation = true` carries the "boxes need to be drawn
offline" semantics, **not** a verdict. Verified in
[`DetectionVerdict`](../../app/src/main/java/com/agarthavision/domain/model/DetectionVerdict.kt):
the enum still has exactly four values (`CONFIRMED`, `FALSE_POSITIVE`,
`WRONG_CLASS`, `BOX_INCORRECT`). No new verdict was added.

### Verification

- ✅ `./gradlew :app:compileDebugKotlin`
- ✅ `./gradlew :app:compileDebugUnitTestKotlin`
- ✅ `./gradlew :app:testDebugUnitTest` — 72 passing, 0 failing
- ✅ Supabase migrations 0005/0006/0007 applied manually by Joryuoo

### Open / follow-up

- Multiplier=24 citation needs the exact paper/DOH bulletin reference written into ADR-005's footnote.
- Phase 2 manual-capture data source (OTG-attached inference hardware) is the snapshot source once the phone camera is removed; ADR-005 lists this as open.
- Three top-bar icons on Capture (Reports + History + Queue) is at the comfort limit — Settings/Help will need an overflow menu in future work.
