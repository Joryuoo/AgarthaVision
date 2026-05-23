# ADR-002: Supabase + Roboflow for Phase 1 MVP; Self-Hosted Stack Deferred to Phase 2

## Status

Accepted. 2026-05-23.

Supersedes the self-hosted backend architecture previously described in [04_CLOUD_BACKEND_PLAN.md](../04_CLOUD_BACKEND_PLAN.md) (v0).

## Context

The original plan ([04_CLOUD_BACKEND_PLAN.md](../04_CLOUD_BACKEND_PLAN.md) v0) called for a
self-hosted FastAPI + Celery + Redis + PostgreSQL + MinIO + YOLO stack. For a 5-person team
under a tight MVP timeline, that's months of work before the mobile app can do anything
end-to-end. Three pressures forced a re-evaluation:

1. **Time.** The MVP demo window is weeks, not months.
2. **Inference model.** The team already has a trained egg-detection model. The remaining
   work is "host it somewhere reachable from the phone," not "train a model."
3. **UX pivot.** The capture flow shifted from single-snapshot to **continuous video with
   per-frame inference** (1 frame / 2s). Continuous inference makes per-frame round-trips
   to a self-hosted backend untenable — every frame is a new HTTP request, network jitter
   matters, and Celery queueing adds latency the user feels.

Three architectural decisions were on the table:

- **Database:** self-hosted Postgres vs. Supabase Postgres
- **Object storage:** self-hosted MinIO vs. Supabase Storage vs. cloud provider
- **Inference:** self-hosted YOLO server vs. Roboflow Hosted Inference vs. on-device

## Decision

**For Phase 1 MVP, ship on managed services:**

| Concern        | Phase 1                                | Phase 2 target                    |
|----------------|----------------------------------------|-----------------------------------|
| Auth           | Supabase Auth (email/password)         | Self-hosted OIDC                  |
| Database       | Supabase Postgres                      | Self-hosted PostgreSQL 16         |
| Object storage | Supabase Storage                       | Self-hosted MinIO / NAS           |
| Inference      | Roboflow Hosted (Public workspace)     | Self-hosted YOLO on local GPU     |
| Region         | `ap-southeast-1` (Singapore) if available, US East otherwise | Philippine-region or on-prem      |

**No self-hosted backend in Phase 1.** The mobile app talks to Supabase and Roboflow directly.

## Rationale

### Why Supabase

- One vendor covers auth + Postgres + object storage + RLS — three concerns, one SDK.
- The Kotlin SDK (`supabase-kt`) integrates cleanly with Hilt and Compose.
- Row-Level Security with `auth.uid()` solves per-medtech data scoping without writing
  middleware. Saves significant time on access-control bugs.
- Local development is `supabase start` (Docker) — same API surface as production. No
  bespoke dev setup to document for each teammate.
- Free tier supports the MVP demo window: 500 MB DB, 1 GB Storage, 50k MAU, 2 GB egress.

### Why Roboflow Hosted (Public workspace)

- The team already has a trained egg-detection model. Roboflow is its native host.
- Hosted Inference is a single HTTP endpoint — no SDK, no streaming, no WebSocket. Trivial
  to integrate.
- **Public workspace = unlimited free inference.** This is essential for continuous-mode
  (1 frame / 2s) — the private free tier (~1,000 inferences / month) burns in under a day
  at MVP usage. Math:

    ```
    1 frame every 2s × 5-min session × 5 sessions/day × 5 medtechs
    = 30 × 5 × 5 × 5 inferences/day
    = 3,750 inferences/day
    ```

  The public-workspace cost is **model + training data visibility** (anyone with the URL
  can view the dataset). This is accepted for the demo window — there's no patient PII
  in the training images themselves.

### Why not on-device inference

- A YOLO model that detects parasite eggs in microscopy is ~50 MB and CPU-heavy on Android.
  Battery + thermal cost at 1 inference / 2s during a multi-minute session is significant.
- Tuning + retraining cycles are faster when the model is hosted and the mobile client just
  consumes an API.
- Phase 2 reconsiders this — on-device makes sense once the model is locked and the
  privacy/cost equation flips.

## Consequences

### Easier

- Ship Phase 1 in weeks instead of months.
- No DevOps burden: no Docker compose to maintain, no servers to provision, no Celery
  workers to restart, no Postgres to back up.
- Real-time features (Postgres LISTEN/NOTIFY via Supabase Realtime) come essentially free
  if needed later.
- Authentication is a solved problem — no bespoke JWT issuance, no refresh-token logic.
- Roboflow's model-versioning UI gives the team a path to swap models without redeploying
  the mobile app (the `BuildConfig.ROBOFLOW_VERSION` becomes the swap point).

### Harder

- **Vendor lock-in.** Supabase migration to self-hosted Postgres requires unwinding RLS
  policies, Auth schema, and Storage URLs. Moderate cost. Roboflow lock-in is lower — the
  model can be exported and self-hosted.
- **Data residency / DPA compliance.** Patient microscopy images are PHI under the
  Philippines Data Privacy Act 2012. Supabase regions are extraterritorial. For an MVP
  demo with synthetic / research-consented samples this is tolerable; **for any deployment
  with real patient PHI, this decision must be revisited.**
- **Model + training data privacy.** Roboflow Public workspaces are world-readable. This
  applies only to the model and dataset, not to inferences (Roboflow does not store images
  sent to its inference endpoint). Acceptable for the demo window.
- **Continuous inference cost at scale.** The free tier is unlimited only for the Public
  workspace. The instant the team needs a Private workspace (e.g., to protect a model with
  competitive value), the cost model changes radically — see math above.

### Migration path to Phase 2

The Phase 2 work is non-trivial but well-bounded:

1. **Backend skeleton.** A FastAPI service implementing the same API contract that
   `supabase-kt` calls today: `samples`, `sessions`, `detections` CRUD with the same JSON
   shape. Implement RLS-equivalent access checks in middleware.
2. **Postgres migration.** `pg_dump` from Supabase → restore to self-hosted Postgres. RLS
   policies need to be reimplemented in middleware since they live in Supabase's
   `auth.uid()` scope.
3. **Object storage migration.** Move bucket objects from Supabase Storage to MinIO. Update
   `storage_path` values in the `samples` table to new URLs.
4. **Inference migration.** Self-host the YOLO model behind a thin HTTP endpoint that
   mirrors Roboflow's response shape. The mobile `RoboflowClient` becomes the new
   client; only the base URL changes.
5. **Mobile app config.** `BuildConfig.SUPABASE_URL` and `ROBOFLOW_*` get repointed to
   the self-hosted endpoints. The Kotlin SDK calls stay the same since they are HTTP
   underneath.
6. **Phase 2 features.** Reintroduce the deferred Phase 1 scope: EPG calculations,
   detection edit/reject flow with audit history, admin dashboard, DOH-formatted PDF
   reports, `SyncQueue` + WorkManager for offline-first.

The mobile codebase will need touching only at the data-layer boundary (Supabase client
swap + Roboflow URL swap). The UI, Use Cases, and domain models stay the same. This is
the intended payoff of the layered architecture in [02_PROJECT_ARCHITECTURE.md](../02_PROJECT_ARCHITECTURE.md).

### Phase 2 schema reintroduction

Phase 1 collapses the SDD's 11-entity model into 3 tables (`sessions`, `samples`,
`detections`). Phase 2 reintroduces the dropped entities as separate migrations:

- `users` (auth bridge), `devices`, `inference_requests`, `epg_calculations`,
  `validation_records`, `reports`, `report_samples`, `sync_queue`.

The Phase 1 tables remain backward-compatible — Phase 2 additions are additive migrations,
not destructive rewrites.

## Risks accepted

| Risk                                                  | Why accepted                                              |
|-------------------------------------------------------|-----------------------------------------------------------|
| PHI in a US/SG-region Supabase instance               | MVP demo uses synthetic or research-consent samples only. |
| Model is publicly viewable in Roboflow workspace      | Demo window only; private workspace planned post-funding. |
| Single point of failure on Roboflow uptime            | Capture preview remains usable when cloud is down; recording stops gracefully. |
| Vendor cost spike beyond demo window                  | Tracked in Phase 2 plan — self-host before scaling.       |
| Roboflow API rate limits during heavy demo            | Public workspace is unmetered, but rate limits may still apply. Verify before each demo. |

## Open questions

- Who maintains the Roboflow workspace (account, model versions)? Decision: **Tabada**.
  Add to AGENTS.md when confirmed.
- Who manages the Supabase production project? Decision: **Joryuoo** (was original backend
  owner). Re-scope his Sprint 1 work from "build FastAPI" to "operate Supabase + own the
  schema migrations."
- When is Phase 2 triggered? Decision: **after the MVP demo lands a funding/support
  commitment**, or when patient PHI enters the system — whichever comes first.
