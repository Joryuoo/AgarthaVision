# ADR-005: Session as Fecal Smear, Manual Capture, Repeat Flag, and Nullable Bbox

## Status

Proposed. 2026-05-28.

Builds on [ADR-004](004-verification-as-hitl-correction.md) (per-detection
verdict model) and [ADR-002](002-supabase-and-roboflow-for-mvp.md) /
[ADR-003](003-self-hosted-inference-container.md) (managed Supabase +
self-hosted inference container). Supersedes the Phase-2-deferred EPG
framing in [03_MOBILE_APP_PLAN.md §2.3](../03_MOBILE_APP_PLAN.md) — EPG
ships in Phase 1 Sprint 2 with a hardcoded Kato-Katz volumetric multiplier.

## Context

Sprint 1 closed with the AI HITL loop in place. Field-side usage by the
medtech surfaced four gaps in the existing model:

1. **Sessions don't represent fecal smears.** A `sessions` row spans
   "Start Recording" → "Stop Recording" with no constraint that one session
   corresponds to one Kato-Katz slide. EPG (Eggs Per Gram of feces) is
   meaningless without a *specimen* unit of aggregation. The schema as
   designed silently allows a medtech to swap slides mid-session, corrupting
   any subsequent egg count.

2. **No manual-capture path.** The medtech can only verify what the model
   flags. If the AI misses an entire frame the medtech wants to record, or
   if the medtech wants to capture pre-AI verification (a "what's left if
   you take away the AI" workflow for offline training or fallback
   diagnosis), there is no UI for it. Existing `samples` schema requires
   non-null bounding box columns, blocking schemaless manual captures.

3. **No duplicate-detection mechanism.** The 2-second `FrameSampler` will
   flag the same egg in many consecutive frames if the slide is stationary,
   producing multiple `samples` rows that all represent the same physical
   egg. Counting these naively overcounts EPG.

4. **`samples.user_note` is in the schema but never wired.** [`0001_init.sql:54`](../../supabase/migrations/0001_init.sql)
   declares `user_note text` on `samples`. [`SampleRemoteDataSource.toRow()`](../../app/src/main/java/com/agarthavision/data/supabase/SampleRemoteDataSource.kt)
   hard-codes `userNote = null` on every upload. No UI input exists.
   Medtech observations are lost.

5. **EPG calculations are tagged "Phase 2"** in 03_MOBILE_APP_PLAN.md, but
   the DOH-validated volumetric multiplier for Kato-Katz is documented and
   known. Deferring the entire arithmetic is over-cautious — the math is a
   single multiplication.

## Decision

The schema and UI gain five coordinated changes:

### 1. Session = fecal smear (add `sessions.label`, keep `sessions.notes`)

A `sessions` row represents one physical fecal smear / Kato-Katz slide.
The medtech enters a **`label`** at session creation (e.g. "Patient A · AM
slide"); EPG and CSV exports key off the session. `sessions.notes` was
already in the schema but unused — it is now wired as the *in-session
free-form observations* field, distinct from `label`. Both fields persist
to Supabase.

Sprint-2 UX additions:
- A pre-Capture **SessionPicker** screen lists the user's active +
  recent-30-days sessions, with a "Create new session" CTA that prompts
  for `label` (required) and `notes` (optional).
- `End Session` (the only button on `CaptureScreen` bottom-right after
  the lifecycle flip) confirms via AlertDialog and offers a `notes`
  editor so the medtech can record observations at session close.
- `Stop Recording` is removed as a concept. Inference pauses
  automatically whenever any sheet, picker, or full-screen child is in
  the foreground; only the explicit `End Session` action writes
  `ended_at`.

This rejects an alternative (a new `smears` table between `sessions` and
`samples`) on the grounds that one-slide-per-session is the operational
norm, and a separate entity adds migration cost without compensating
clarity. If field testing surfaces medtechs needing to swap slides within
a single session, Sprint 3+ can introduce `smears`; the EPG read path
(see §4) is portable to that change because it groups by `session_id`
today and would group by `smear_id` then.

### 2. Manual Capture (`samples.is_manual`, nullable bbox)

A new sample-level boolean **`samples.is_manual`** distinguishes
medtech-initiated snapshots from model-flagged frames. Manual samples
sync to Supabase identically to AI samples — they are real medtech
attributions and contribute to the training/audit dataset.

The accompanying detection row carries the medtech's species pick as
both `class_label` and `expert_class`, with `verdict = CONFIRMED` and
`confidence = 1.0`. **The four bounding-box columns
(`bbox_x/y/w/h`) become nullable**; manual rows write all four as `null`.

Considered and rejected:
- A sentinel bbox value `(0, 0, 0, 0)` (an earlier draft of this plan).
  Magic values rot. The cost of nullable columns is ~3 null-guards in
  consumers (`FrameWithBoxes`, `SampleDetailScreen`, CSV export). Worth
  it for honesty in the schema.
- Adding a `NEEDS_ANNOTATION` verdict to the
  [`DetectionVerdict`](../../app/src/main/java/com/agarthavision/domain/model/DetectionVerdict.kt)
  enum. The existing verdicts (`CONFIRMED`, `FALSE_POSITIVE`,
  `WRONG_CLASS`, `BOX_INCORRECT`) all describe the *medtech's judgement
  about a model prediction*. A manual capture has no model prediction to
  judge — `CONFIRMED` correctly conveys "medtech says there's an egg
  here". The "needs offline annotation" status is carried by
  `samples.needs_reannotation = true`, not by a verdict.

In Phase 2 the phone's camera is removed (per
[00_PROJECT_OVERVIEW.md](../00_PROJECT_OVERVIEW.md) Phase 2 paragraph).
The Manual Capture feature still ships, but the snapshot source flips
from the phone's CameraX preview to the OTG-attached on-prem inference
hardware. The UI / data layer is unchanged; only
`CameraManager.captureLatestFrame()` is replaced. This is **open** —
see "Phase-2 snapshot source" in §Status.

### 3. Mark-as-Repeat (`samples.is_repeat`, Room-only)

A second sample-level boolean **`samples.is_repeat`** lets the medtech
flag a sample as a duplicate of an egg already counted on the same
smear. Marked-as-repeat samples are persisted (for the medtech to sift
through and manually clean up later) but **excluded from EPG counts**.

This flag lives **only in Room** — it does not sync to Supabase. The
rationale:
- It is a workflow aid (let the medtech see "I already counted this
  one"), not a training-data signal.
- The same egg seen across N frames is not interesting as a
  Supabase-side artifact; the AI-confirmed detection in the first frame
  is the real record.
- Skipping the Supabase migration keeps the public schema smaller.

If field testing shows this loses too much HITL signal — e.g. if a
"these N frames depict the same egg" relationship would be useful for
future dedup models — a Sprint 3+ migration can promote `is_repeat` to
Supabase without breaking anything. ADR-004's argument *against*
discarding HITL data applies less strongly here because the AI's
disagreement-with-expert signal (verdicts) is still fully captured.

### 4. EPG arithmetic in Phase 1

A pure utility **`core/util/EpgCalculator.kt`** with:

```kotlin
object EpgCalculator {
    /** Kato-Katz volumetric multiplier per [TODO insert citation]. */
    const val MULTIPLIER = 24
    fun epg(eggCount: Int): Int = eggCount * MULTIPLIER
}
```

The eggs-per-session count comes from
**`domain/usecase/reports/SessionEggCountUseCase.kt`** querying Room:

```sql
SELECT COALESCE(d.expert_class, d.class_label) AS species, COUNT(*) AS count
FROM detections d JOIN samples s ON d.sample_id = s.sample_id
WHERE s.session_id = :sessionId
  AND s.is_repeat = 0
  AND d.verdict = 'confirmed'
GROUP BY species;
```

Total egg count and EPG are surfaced as a `Card` at the top of the
existing
[`SessionDetailScreen`](../../app/src/main/java/com/agarthavision/ui/records/SessionDetailScreen.kt).
A new "Reports" icon on `CaptureScreen`'s top app bar navigates to
`records/session/{currentSessionId}` — same screen, seeded with the
active session, so the medtech can see live EPG without leaving the
flow.

A durable `EPGCalculationEntity` (cached EPG-per-session row) is
**deferred to Phase 2** — for Phase 1 the calc is cheap and on-demand
from Room.

### 5. Wire `samples.user_note` (already in schema, never used)

Add `user_note: String?` to
[`SampleEntity.kt`](../../app/src/main/java/com/agarthavision/data/local/entity/SampleEntity.kt).
Add an optional `Input` field to both `VerificationSheet` and
`ManualSheet` ("Notes (optional)"). Stop force-setting `userNote = null`
in `SampleRemoteDataSource.toRow()`. Surface the value in the
SampleDetail Metadata tab and as a column in the
`ExportSessionUseCase` CSV.

## Consequences

### Positive

- One Kato-Katz slide ↔ one session, enforced by UX (no schema-level
  smear FK needed for MVP).
- EPG ships in Phase 1, not deferred. CSV export gains a per-species
  count + EPG column.
- Medtech-only workflow exists ("what's left if you take away the AI") —
  the system is useful even with no inference container reachable, with
  the same persistence + sync.
- Duplicate-egg overcounting has a manual mitigation (Mark-as-Repeat)
  with a clean local-only data model.
- `user_note` finally lives — medtech observations are now in the
  dataset.

### Negative / Costs

- Three Supabase migrations (`0005_session_label.sql`,
  `0006_sample_is_manual.sql`, `0007_detection_bbox_nullable.sql`) and
  one Room version bump (`v4 → v5`). Each migration ships with a
  commented revert block for manual rollback.
- Null bbox values require ~3 null-guards across consumers.
- Mark-as-Repeat relies on the medtech actually marking duplicates;
  there is no algorithmic dedup. Tolerated for MVP; revisit if field
  data shows undercounting.
- `samples.needs_reannotation = true` is now semantically dual-purpose:
  it means either (a) the AI missed eggs in this frame (Q4=Yes), or
  (b) this is a manual capture with no boxes drawn. Both share the
  meaning "boxes need to be drawn offline" — acceptable conflation.

### Open / Phase-2

- **Phase-2 manual-capture snapshot source.** In Phase 2 the phone
  camera is removed; manual capture must read from the OTG-attached
  inference hardware. The UI surface is unchanged but the
  `CameraManager.captureLatestFrame()` implementation flips. Spec
  follows in the Phase-2 planning sprint.
- **DOH-validated formula table** replaces the hardcoded `MULTIPLIER =
  24` in Phase 2 (different prep methods, configurable per session).
- **TODO** insert citation for the Kato-Katz multiplier `24` (the
  reference DMKuZu has on hand — paper / DOH bulletin / WHO guideline).

## References

- [ADR-002](002-supabase-and-roboflow-for-mvp.md) — Supabase + (originally) Roboflow for MVP
- [ADR-003](003-self-hosted-inference-container.md) — Self-hosted inference container
- [ADR-004](004-verification-as-hitl-correction.md) — Per-detection verdict model
- [03_MOBILE_APP_PLAN.md](../03_MOBILE_APP_PLAN.md) §1.1, §1.6, §2 — updated per this ADR
- [04_CLOUD_BACKEND_PLAN.md](../04_CLOUD_BACKEND_PLAN.md) — migrations 0005, 0006, 0007
- [docs/sprint2_addition_plan.md](../sprint2_addition_plan.md) — implementation plan (v3) that operationalises this ADR
