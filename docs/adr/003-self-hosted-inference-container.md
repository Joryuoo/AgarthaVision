# ADR-003: Self-Hosted Inference Container Replaces Roboflow Hosted Inference

## Status

Accepted. 2026-05-24.

Supersedes the **Roboflow Hosted Inference** decision from
[ADR-002](002-supabase-and-roboflow-for-mvp.md). The Supabase portion of ADR-002
(Auth + Postgres + Storage) is unchanged.

## Context

ADR-002 chose Roboflow Hosted Inference for Phase 1 because the team "already had a
trained model" and Roboflow is its native host. That assumption broke. The team's actual
model is a **custom Ultralytics fork** with:

- A **YOLOv26** detection head
- An **EfficientNetV2** backbone

Roboflow Hosted Inference only runs vanilla Ultralytics architectures registered in its
public model zoo. A custom `nn.Module` graph (custom layers + non-stock backbone) cannot
be uploaded — the hosted runtime has no way to construct the model. We learned this when
trying to deploy `best.pt` to a Roboflow workspace.

The Supabase decision was unaffected. Only the inference path needs to move.

## Decision

Phase 1 inference runs in a **self-hosted FastAPI container**, runnable on any
Docker-capable GPU host.

| Concern             | Choice                                                                |
|---------------------|-----------------------------------------------------------------------|
| Runtime             | FastAPI + Uvicorn + custom Ultralytics fork + PyTorch                 |
| Endpoint shape      | `POST /infer` (raw JPEG body, `Authorization: Bearer <key>`) + `GET /health` |
| Response shape      | Roboflow-compatible (same JSON keys: `predictions[]`, `image`)        |
| Distribution        | Public Docker image on **GitHub Container Registry** (`ghcr.io/joryuoo/agartha-inference:<tag>`) |
| Weights             | Baked into the image at build time (Git LFS in `inference/weights/`)  |
| Default host        | DigitalOcean **MI300X (AMD)** GPU droplet — pending ROCm validation   |
| NVIDIA fallback     | RunPod A5000 (~$0.69/hr) or Lambda Labs A10 (~$0.50/hr)               |
| Container owner     | **DMKuZu** (build, push, deploy, droplet ops, ROCm validation)        |
| Weights owner       | **Tabada** (model training; hands `best.pt` to DMKuZu for packaging)  |

The mobile data-layer is provider-agnostic. The `InferenceApi` Retrofit interface and
`InferenceResponseDto` are shaped around the response, not against a specific server.
Switching GPU providers is a `local.properties` change, not a code change.

## Rationale

### Why a self-hosted container

- The custom model architecture leaves no managed-inference option. PyTorch checkpoint
  files are not self-contained — the runtime must be able to construct the model graph.
  That requires our Ultralytics fork to be on the runtime, which only happens in a
  container we control.
- A container is the smallest portable unit that carries PyTorch + the fork + the
  weights as one artifact. Anything less (e.g., shipping just the `.pt` file to a
  managed runtime) doesn't work.
- Containers are the lingua franca of GPU rental — every viable provider supports
  `docker run --gpus all`. No lock-in.

### Why GHCR public

- Free for public images. Private images require a Personal Access Token at every
  cold start on a new droplet — friction we don't want during a demo.
- The model weights inside the public image are world-readable. This is the **same
  trade-off** ADR-002 accepted for Roboflow Public workspaces (where the model and
  dataset are visible to anyone with the URL). We are not regressing on privacy.

### Why bake weights into the image

- Cold start = `docker pull` + `python server.py` + PyTorch JIT (~10s). No S3 download,
  no auth dance, no external dependency at startup.
- Demo failure modes drop sharply when the only failure point is "did the image pull
  succeed?"
- Tradeoff: image size grows from ~3 GB (base) to ~6–10 GB (with weights). `docker pull`
  on a fresh droplet takes 2–3 minutes — still inside the time budget for a demo setup.

### Why DigitalOcean MI300X by default

- The team's preference, and a familiar provider.
- **Real risk:** ROCm + custom Ultralytics is unreliable for non-stock architectures.
  Many Ultralytics ops have ROCm fallbacks that either fail or run extremely slowly.
  **DMKuZu must validate inference on ROCm before committing to the MI300X path.**
  If it doesn't work, switch the base image to `pytorch/pytorch:2.x-cuda12-cudnn8-runtime`
  and rent NVIDIA from RunPod or Lambda Labs. The mobile-side code does not change.

### Why Bearer-token auth

- The droplet's `:8000` is publicly reachable. Without auth, anyone on the internet
  could hammer the inference endpoint and burn the demo droplet's resources.
- A static API key in `Authorization: Bearer <key>` is the cheapest layer that blocks
  random traffic without adding TLS or a reverse proxy.
- For real PHI workloads (Phase 2), this gets upgraded to mTLS or a proper API gateway.

## Consequences

### Easier

- The mobile code is unchanged from what ADR-002 imagined. Only the URL and auth header
  change. `FrameSampler`, `InferFrameUseCase`, `FlaggedFrameStore` all stay the same.
- Portable across providers. If DigitalOcean has an outage during the demo, DMKuZu can
  pivot to RunPod with one `docker run` command.
- Full control over the model. Tuning, retraining, or swapping the head is a
  rebuild-and-repush, not a Roboflow upload (which couldn't happen anyway with a
  custom architecture).

### Harder

- **GPU rental costs add up.** DigitalOcean MI300X is ~$1.99/hr; even cheap NVIDIA
  options are ~$0.50–0.70/hr. The team must remember to **destroy the droplet** after
  every test or demo session. Bills don't pause when you walk away.
- **ROCm validation is a gate.** If the custom Ultralytics fork doesn't work on ROCm,
  DMKuZu absorbs a switch to NVIDIA, which means a different base image and a
  re-test cycle.
- **Manual deploy.** No CI/CD for the inference image in MVP. Every weights or model
  change requires DMKuZu to `docker build && docker push` manually.
- **Operational responsibility shifts onto the team.** Roboflow handled uptime and
  scaling. With self-hosting, the team owns the droplet lifecycle.

### Migration path to Phase 2

The container is the migration path. Move it from a rented droplet onto owned GPU
hardware (Module 2B in the SRS — local edge inference). The image, the FastAPI
contract, and the mobile client all stay the same. Only the host changes.

## Risks accepted

| Risk | Why accepted |
|------|--------------|
| Model weights are world-readable in a public GHCR image | Same trade-off as a Roboflow Public workspace; ADR-002 already accepted this |
| ROCm + custom Ultralytics may not work | Validated first by DMKuZu before commit; NVIDIA fallback is documented |
| Static API key is weak auth | Sufficient for synthetic-sample demo; PHI workloads require mTLS / gateway in Phase 2 |
| Droplet idle cost if someone forgets to destroy it | Documented in `inference/README.md` runbook + cost table in `04_CLOUD_BACKEND_PLAN.md` §10 |
| Single droplet = single point of failure on demo day | DMKuZu keeps the GHCR image available so a replacement droplet is one `docker run` away |
| No CI/CD | Manual build/push is acceptable while the model isn't changing weekly |

## Open questions

- ROCm validation outcome: does the custom Ultralytics fork run on MI300X? Owner: **DMKuZu**. Resolution before the inference image is finalized.
- Should we add the `Authorization` header check to `/health` too, or leave it open so monitoring tools can hit it? Decision deferred — for MVP, leave `/health` open.
- When the model changes (Tabada retrains and produces a new `best.pt`), how do we version the image tag? Decision: semantic version (`v1`, `v2`, ...) tied to the model release, not the date.
