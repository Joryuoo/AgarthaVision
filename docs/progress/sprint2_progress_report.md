# Sprint 2 Progress Report

**Last updated:** 2026-05-28
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

---

## Sprint 2 — Records Browser + Session Reports

Each track depends on Sprint 1's Room schema (`AgarthaDatabase` v3 with ADR-004
verification fields) and the `SyncSampleUseCase` from Joryuoo's 1.8 PR.

| §    | Deliverable                                                                                                  | Owner    | Status |
|------|--------------------------------------------------------------------------------------------------------------|----------|--------|
| 2.1  | `RecordsScreen` + `RecordsViewModel` — Room-backed list of verified samples, filter by date/species          | jojseph  | ❌ not started |
| 2.2  | `SampleDetailScreen` + `SampleDetailViewModel` — full-screen view of one sample with detections + metadata + JPEG | jojseph  | ❌ not started |
| 2.3  | `SessionReportUseCase` — raw CSV export per spec; EPG + DOH PDF deferred to Phase 2                          | jojseph  | ❌ not started |

### Sprint 2 Acceptance Criteria (from [03_MOBILE_APP_PLAN.md §2.4](../03_MOBILE_APP_PLAN.md))

| Criterion | Status |
|---|---|
| Records list shows all verified samples for the current user, newest first | ⏳ |
| Each row exposes class label, confidence, captured-at, sync status | ⏳ |
| Tapping a row opens `SampleDetailScreen` with bounding-box overlay on the persisted JPEG | ⏳ |
| Filter by date range works against `samples.timestamp` | ⏳ |
| Filter by species works against `detections.expert_class` (falling back to `class_label` when verdict is `CONFIRMED`) | ⏳ |
| `SessionReportUseCase` produces a CSV with the columns listed in §2.3 of the spec | ⏳ |
| All Sprint 2 work passes `./gradlew :app:test` with new test coverage for the new use cases / ViewModels | ⏳ |

---

## Carry-overs from Sprint 1

These items were intentionally deferred from the Sprint 1 closeout audit; they
have **no acceptance-criteria impact** on Sprint 2 tracks 2.1–2.3 but should be
picked up opportunistically (especially C-1, which is one file).

| Track | Item                                                                                                              | Priority | Source                                                                                       |
|-------|-------------------------------------------------------------------------------------------------------------------|----------|----------------------------------------------------------------------------------------------|
| C-1   | Extract hardcoded strings in `CaptureScreen.kt` to `strings.xml` (`capture_title`, `capture_rec`, `capture_start`, `capture_stop`, `capture_verify_action_desc`, `capture_permission_required`, `capture_allow_camera`) | low      | [Sprint-1 audit · item 5](../../../.claude/plans/mutable-growing-graham.md)                  |
| C-2   | If permission-screen `MaterialTheme.styles.background`/`foreground` look wrong in dark mode, add explicit dark-tokens or a comment overriding the camera-convention default | low      | Sprint-1 audit · item 6 follow-up                                                            |
| C-3   | **Phase 2 architectural marker** — per-account persistent flagged-frame queue (survives logout/restart, keyed by `user_id`). NOT Sprint 2 work; recorded here so it stays on radar for the Phase 2 planning doc | scope marker | [00_PROJECT_OVERVIEW.md Phase 1 vs 2 table](../00_PROJECT_OVERVIEW.md), [03_MOBILE_APP_PLAN.md §1.5](../03_MOBILE_APP_PLAN.md) |
| C-4   | **Security gap.** `SampleDao.getSamplesPendingSync()` and `SessionDao.observeAllSessions()` do not filter by `user_id`. RecordsScreen MUST filter by current user when it lands (which fixes C-4 implicitly for the read path) — the sync-side query is the remaining concern | **medium** | Sprint-1 audit · DAO security gap                                                            |
| C-5   | **Per-user JPEG scoping.** `SampleImageStore` writes to global `filesDir/samples/`. Move to `filesDir/users/{userId}/samples/` so cross-user access on a shared device is impossible. SampleDetailScreen will need the user-scoped path | medium   | Sprint-1 audit · per-user JPEG scoping                                                       |

**Cross-reference:** C-4 and C-5 are *prerequisites* for 2.1/2.2 to be safe on
a shared-device deployment. Decide early in the sprint whether to fix them
inline (recommended) or behind an explicit "single-user device" assumption.

---

## Risks / blockers

| Risk | Likelihood | Mitigation |
|---|---|---|
| Schema migration needed beyond `AgarthaDatabase` v3 (e.g. for filter indices on `samples.timestamp` or `detections.expert_class`) | medium | Coordinate with Joryuoo before writing migrations; existing destructive fallback (`fallbackToDestructiveMigration(dropAllTables = true)`) is fine for Phase 1 dev but a real migration is required before any real-data deployment |
| v2 inference container retirement could orphan unverifiable flagged frames during long demos | low | Existing `NetworkMonitor` latches the connection-loss banner; verify flagged-but-unverified frames remain accessible (already true — see Sprint 1) |
| Records read-path slow on devices with hundreds of verified samples | low–medium | `SampleDao` queries should `LIMIT`/page. Consider Paging 3 only if measured slow |
| Pre-Sprint-2 carry-over C-4 ships late and exposes another user's verified samples in the Records list during multi-user testing | medium | Treat C-4 as a 2.1 sub-task — Records read path is the natural place to enforce `WHERE user_id = ?` filtering |

---

## Pre-sprint checklist

- [ ] All Sprint-1 docs↔code mismatches from the post-closeout audit confirmed resolved (PRs A + B merged).
- [ ] Sprint-1 progress report row **1.10** (Metadata binding) marked complete with verification step.
- [ ] PR-template / branch convention from [06_GIT_WORKFLOW_AND_CI.md](../06_GIT_WORKFLOW_AND_CI.md) reaffirmed with the team — Sprint-1 closeout commits (`610e8e49` "feat: sprint 1 tasks finished", `f4b464a` "chore(dashboard): ux modification for sample verification") had several conventions slip (KomoUI Button, strings, colors, KDoc) that a self-review pass would have caught.
- [ ] Decide on C-4 / C-5 inclusion in Sprint 2 scope (recommendation: include both as 2.1 prerequisites).
- [ ] Confirm jojseph has cycles for all three tracks — if not, redistribute or descope 2.3 (CSV export is the natural cut).
