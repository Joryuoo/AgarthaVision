# ADR-004: Verification as Human-in-the-Loop Correction (Rejections Persist as Labeled Data)

## Status

Proposed. 2026-05-25.

Supersedes the implicit *"Reject = delete the frame entirely"* decision baked into
[03_MOBILE_APP_PLAN.md §1.6–§1.7](../03_MOBILE_APP_PLAN.md). The Supabase managed-services
choice from [ADR-002](002-supabase-and-roboflow-for-mvp.md) and the self-hosted
inference container from [ADR-003](003-self-hosted-inference-container.md) are unchanged.

## Context

AgarthaVision is a **human-in-the-loop (HITL) diagnostic support** system, not an
autonomous diagnostic device. Per [00_PROJECT_OVERVIEW.md](../00_PROJECT_OVERVIEW.md),
every model output exists to **supplement** an expert medical technologist's judgment —
the medtech is always the diagnostic authority.

The current spec treats the verification flow as a binary `Verify | Reject` choice,
where `Reject` means **discard the frame entirely (both in-memory and disk cache)**
(see [03_MOBILE_APP_PLAN.md §1.6 action table](../03_MOBILE_APP_PLAN.md) and the
`REJECTED (deleted)` terminal state in §1.7). The team's actual intent — agreed verbally
but never written down — is that rejections must persist as **labeled false positives**.
The spec drifted from the team's design.

The deletion behavior destroys four things the project genuinely needs:

| Lost artifact | Why it matters |
|---|---|
| Training data for retraining | False positives are the most valuable signal for the next model iteration — they teach the model what an egg is *not*. Deletion means the model never improves at distinguishing eggs from debris, bubbles, or fibers. |
| Precision metrics | `precision = TP / (TP + FP)`. Without persisted FPs, the Reports screen can only ever show what the medtech confirmed, never the model's true hit rate. |
| Audit trail | In a diagnostic context, there must be a durable record of what the AI claimed and what the human overrode. Deletion erases that trail. |
| Disagreement analysis | "Where does the model and the expert disagree?" is unanswerable if disagreements are erased. |

Compounding this, the server-side post-filter on `CONFIDENCE_THRESHOLD` (see
[`inference/server.py`](../../inference/server.py)) hides low-confidence detections
from the expert entirely — the experts never see the borderline calls that would be
most useful to label.

## Decision

The verification flow is redesigned as **per-detection expert correction**, and the
inference server is changed to surface all model detections (not just high-confidence
ones).

### 1. Per-detection verdict, not per-sample binary

Each `DetectionEntity` row gets a `verdict` field:

| Verdict | Meaning |
|---|---|
| `CONFIRMED` | Expert agrees: real egg, correct box, correct species |
| `FALSE_POSITIVE` | Expert disagrees: no egg in this bounding box |
| `WRONG_CLASS` | Expert agrees there's an egg in the box, but disagrees with the species — `expert_class` holds the corrected label |
| `BOX_INCORRECT` | Expert agrees there's an egg in this frame but the bounding box is misplaced — sample queued for offline re-annotation |

The sample-level `SampleStatus` enum loses `REJECTED`. The aggregate "is this a positive
finding?" question is now answered by reading the child detections' verdicts (any
`CONFIRMED` or `WRONG_CLASS` → positive finding; all `FALSE_POSITIVE` → negative finding).

### 2. Three-question dropdown verification flow

Per detected bounding box, the expert answers:

| Q | Prompt | Branching |
|---|---|---|
| Q1 | "Is there a parasitic egg in this bounding box?" | `Yes` → continue. `No` → `verdict = FALSE_POSITIVE`, skip Q2/Q3. |
| Q2 | "Is the bounding box correctly placed?" | `Yes` → continue. `No` → `verdict = BOX_INCORRECT`, skip Q3. |
| Q3 | "What species?" | `Ascaris lumbricoides` / `Trichuris trichiura` / `Hookworm` / `Other (free text)`. If species ≠ `model_class` → `verdict = WRONG_CLASS`; else `verdict = CONFIRMED`. |

Optional frame-level Q4 (false-negative flag, no in-app drawing):

> "Did the model miss any eggs in this frame?" — `Yes` flags the sample for offline annotation; `No` is the default.

Q4 result is stored on the sample row (`needs_reannotation` boolean) and does not block submission.

### 3. Server removes `CONFIDENCE_THRESHOLD` post-filter

`inference/server.py` no longer filters predictions below `CONFIDENCE_THRESHOLD`. The
model's internal objectness threshold already eliminates "nothing here" frames; everything
the model emits as a candidate is returned to the mobile client and shown to the expert.
The env var `CONFIDENCE_THRESHOLD` is removed.

This is consistent with the HITL principle: the expert — not the server — decides
whether a low-confidence call is a real detection.

### 4. Manual in-app box editing is deferred

Compose-based draggable-box editing is a 4–6 hour minimum effort for a usable
single-box editor (and a full day for multi-box with new-box drawing). For Sprint 1,
`BOX_INCORRECT` samples are simply flagged and routed to offline annotation tooling
(CVAT, Label Studio, or equivalent) before retraining.

### 5. Both verdicts sync to Supabase

`SyncSampleUseCase` uploads the JPEG + sample row + all detection rows regardless of
verdict mix. The Supabase `samples` + `detections` tables become the centralized
training-data corpus.

### 6. Species dropdown scope

Three trained classes (`Ascaris lumbricoides`, `Trichuris trichiura`, `Hookworm`) plus
an `Other` option with free-text specification. The free-text field captures
out-of-distribution observations (Schistosoma, Enterobius, Taenia, etc.) without
bloating the dropdown.

## Rationale

### Why per-detection verdicts (not per-sample)

A single sample frame can contain multiple model-predicted bounding boxes. The expert
may agree with some and disagree with others — e.g., model emits two boxes in one
frame, one is a real Ascaris, the other is a bubble. A per-sample verdict cannot
express this. A per-detection verdict can.

### Why dropdowns, not free-form text

Dropdowns produce structured data trivially queryable for retraining and metrics.
Free-form text would require NLP postprocessing and degrades label quality. The
`Other` species fallback is the only free-text field, and it's narrowly scoped.

### Why drop the server's `CONFIDENCE_THRESHOLD` filter

`CONFIDENCE_THRESHOLD` is a post-processing filter applied *after* the model has
already decided "yes, this is a candidate." Filtering at 0.4 hides exactly the
borderline calls (0.20–0.39) that retraining benefits from most. In a HITL system,
the human is the threshold.

### Why defer manual box editing

The retraining workflow does not depend on every box being corrected in-app. Offline
annotation tools (CVAT, Label Studio) handle pixel-level work far more ergonomically
than a phone touch surface. Sprint 1 captures the *label* (`BOX_INCORRECT`); the
annotation itself happens in the retraining pipeline.

### Why both verdicts go to Supabase

A centralized corpus is the simplest retraining pipeline: Tabada queries the
`detections` table by `verdict` to assemble a training set. Local-only false positives
would require a separate export workflow per device. The marginal storage cost
(JPEGs ~80 KB each post-resize, see [04_CLOUD_BACKEND_PLAN.md §10](../04_CLOUD_BACKEND_PLAN.md))
is acceptable for Phase 1 within Supabase's free tier.

## Consequences

### Easier

- Retraining has a single source of truth (the `detections` table) with structured labels.
- Precision / recall / per-species metrics become straightforward SQL queries against `detections.verdict`.
- The audit trail satisfies regulatory expectations for HITL diagnostic systems: every model claim and every expert override is preserved.
- The mobile data layer is largely unchanged — Retrofit DTOs already match the response shape; only the verification UI and the persistence write paths change.

### Harder

- Supabase storage grows faster. The free-tier ceiling (1 GB) accommodates ~12,000 samples at 80 KB each. With false positives counting toward the same budget, that ceiling shrinks — the team must monitor and resize aggressively (640×640, JPEG quality ≤ 80).
- The PHI retention question is reopened. ADR-002 accepted PHI risk only for synthetic / research-consent samples; persisting *false-positive* frames still subjects the same image-handling rules. No regression here, but it deserves a future ADR if real-patient PHI enters the pipeline.
- The mobile VerificationSheet UI is more complex than the original two-button design — stepped questionnaire, conditional branching, species dropdown with "Other" free text.
- A Supabase schema migration (`0002_verification_fields.sql`) and a Room schema migration are both required. Mobile teammates must coordinate the Room migration with the Sprint 1 VerificationSheet rebuild.
- The server image must be rebuilt (`v2`) without the `CONFIDENCE_THRESHOLD` env var and re-pushed to GHCR.

### Migration path to Phase 2

Unchanged from [ADR-003](003-self-hosted-inference-container.md): the inference
container migrates from rented droplets to owned GPU hardware. The verdict schema
travels with the Supabase Postgres schema; Phase 2's self-hosted Postgres replicates
the same tables.

## Risks accepted

| Risk | Why accepted |
|---|---|
| More Supabase storage consumption (false positives + confirmed samples both stored) | Resize-on-upload (640×640, JPEG ≤80 KB) keeps growth manageable. If the free tier is exceeded, paid Supabase tier is < $25/month — far below the cost of a retraining pipeline that lacks negative examples. |
| Removing the server-side confidence filter exposes more borderline detections to the expert (more verification work) | Verification work *is* the HITL value capture. The expert *should* see the borderline cases. If volume becomes unworkable, a *client-side* hint (visually de-emphasizing low-confidence boxes) can be added without changing the server contract. |
| `BOX_INCORRECT` samples need an offline annotation step before retraining | Acceptable. Offline annotation tools are mature and ergonomic; reproducing them in-app is not a Sprint 1 priority. |
| Schema migrations on both Supabase and Room introduce coordination work mid-sprint | Mitigated by writing this ADR + amending the docs *before* any code lands, so all teammates pick up the new spec at the same time. |

## Open questions

- **Free-text `Other` species moderation.** Should the free-text field be normalized server-side (lowercase, trim) or persisted verbatim? Decision: persist verbatim for Phase 1; normalize in the retraining query. Owner: Tabada at retraining time.
- **`BOX_INCORRECT` annotation tooling.** CVAT vs Label Studio vs custom? Decision deferred to the retraining sprint. Owner: Tabada.
- **PHI retention window.** Should false-positive frames purge after N days? Deferred until real-patient data enters the pipeline. Owner: future ADR-005.
- **Frame-level Q4 (false negatives) UX.** Where does the "model missed an egg" flag surface in the Reports view? Deferred to Sprint 2 (Reports browser). Owner: Beansman.
