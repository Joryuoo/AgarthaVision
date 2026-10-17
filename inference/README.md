# Agartha Inference Container

FastAPI server wrapping the custom Ultralytics fork (YOLO26-nano on an EfficientNetV2-S
backbone) for egg detection. Runs on a DigitalOcean MI300X AMD GPU droplet,
or on a free Kaggle T4 x2 ([`KAGGLE.md`](KAGGLE.md)).

> **Cost reminder:** The MI300X droplet bills ~$1.99/hr while running.
> **Destroy the droplet immediately after every test or demo session.**

---

## Prerequisites

- Docker (DMKuZu only — teammates do not need this)
- GHCR write access: `docker login ghcr.io -u <github-username> -p <PAT>`
- `git lfs install` run once on your machine (for `weights/yolo26n-efficientnetv2s.pt`)
- The trained weights from Tabada at `inference/weights/yolo26n-efficientnetv2s.pt`

---

## Development workflow (fast — no Docker build cycles)

Use this during active development. Spin up a droplet, install dependencies directly,
and iterate on `server.py` without rebuilding a multi-GB image each time.

### 1. Provision a DO MI300X droplet via the web UI

### 2. SSH into the droplet

```bash
ssh root@<droplet-ip>
```

### 3. Install dependencies on the droplet

```bash
# Install custom Ultralytics fork (replace with your GitHub fork URL + SHA)
pip install git+https://github.com/DMKuZu/ultralytics@<commit-sha>
pip install fastapi "uvicorn[standard]" pillow numpy
```

If you're actively editing the fork locally, rsync it instead:

```bash
# From your local machine:
rsync -av --exclude='.git' ~/path/to/ultralytics/ root@<droplet-ip>:/app/ultralytics/
ssh root@<droplet-ip> "pip install -e /app/ultralytics"
```

### 4. Upload weights and server to the droplet

```bash
# From your local machine (repo root):
scp inference/weights/yolo26n-efficientnetv2s.pt root@<droplet-ip>:/root/app/weights/
scp inference/server.py root@<droplet-ip>:/root/app/
```

### 5. Run the server

```bash
# On the droplet:
mkdir -p /app/weights
cd /app
INFERENCE_API_KEY=<your-secret> uvicorn server:app --host 0.0.0.0 --port 8000
```

### 6. Smoke test from your local machine

```bash
# Health check (no auth)
curl http://<droplet-ip>:8000/health

# Inference with a sample JPEG
curl -X POST \
  -H "Authorization: Bearer <your-secret>" \
  -H "Content-Type: image/jpeg" \
  --data-binary @/path/to/sample.jpg \
  http://<droplet-ip>:8000/infer
```

Expected response when eggs are detected:
```json
{
  "predictions": [
    {"class": "egg", "confidence": 0.92, "x": 320.0, "y": 240.0, "width": 80.0, "height": 60.0}
  ],
  "image": {"width": 640, "height": 640}
}
```

Expected response when nothing is detected:
```json
{"predictions": [], "image": {"width": 640, "height": 640}}
```

### 7. Share with teammates

Once the server is running, share via the same channel as the Supabase keys:

```
INFERENCE_URL_DEV=http://<droplet-ip>:8000
INFERENCE_API_KEY=<your-secret>
```

Teammates paste these into their `local.properties`. No code changes needed.

### 8. Destroy the droplet when done

Do this every time. The image is on GHCR — rebuilding takes one `docker run`.

---

## Demo workflow (Docker — freeze the working setup)

Run this once `server.py` is confirmed working on the droplet directly.

### Build the image and Push to GHCR

```bash
# From repo root:
git lfs install && git lfs pull
docker build -t ghcr.io/dmkuzu/agartha-inference:v1 inference/
docker push ghcr.io/dmkuzu/agartha-inference:v1
```

### Run on a fresh GPU droplet

```bash
docker run \
  --device=/dev/kfd \
  --device=/dev/dri \
  --group-add video \
  -p 8000:8000 -d \
  -e INFERENCE_API_KEY="<your-secret>" \
  -e MODEL_VERSION="yolo26n-effv2s-v1-cloud-fp32" \
  ghcr.io/dmkuzu/agartha-inference:v1
```

---

## Environment variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `INFERENCE_API_KEY` | Yes | — | Bearer token checked on every `POST /infer` |
| `WEIGHTS_PATH` | No | `weights/yolo26n-efficientnetv2s.pt` | Path to the model weights file |
| `MODEL_VERSION` | No | `yolo26n-effv2s-v1-cloud-fp32` | Reported as `model_version` and stored per sample. Naming scheme: `export/README.md` |
| `MAX_BATCH_SIZE` | No | `8` | Most frames in one forward pass |
| `MAX_QUEUE_DELAY_MS` | No | `15` | How long a worker waits for more frames after the first of a batch |
| `QUEUE_SIZE` | No | `32` | Frames waiting for a GPU. When full, `POST /infer` answers `503` at once |
| `REQUEST_TIMEOUT_S` | No | `25` | Longest a request waits for its result before `503`. Keep it below the app's 30 s read timeout |
| `RETRY_AFTER_S` | No | `2` | `Retry-After` sent with every `503` |
| `INFERENCE_DEVICES` | No | every visible GPU, else `cpu` | Comma-separated torch devices, one worker each, e.g. `cuda:0,cuda:1` |

There is no confidence threshold, on purpose: the server returns every box the model produced
and the medtech is the threshold (`docs/constraints.md` C7).

---

## Queue and batching

`POST /infer` never runs the model on the request itself:

1. The JPEG is decoded off the event loop and put on a **bounded queue** (`QUEUE_SIZE`). A full
   queue answers **`503` with `Retry-After`** at once, instead of letting requests pile up.
2. **One worker per GPU** (`INFERENCE_DEVICES`) pulls from that one queue. Each has its own copy
   of the model, loaded and warmed up at start-up.
3. A worker takes the first waiting frame, waits up to `MAX_QUEUE_DELAY_MS` for more, and runs up
   to `MAX_BATCH_SIZE` of them as **one batched forward pass**. This is Triton's
   `max_batch_size` / `max_queue_delay` pattern. Ultralytics letterboxes each image itself.
4. The forward pass runs on a worker thread, so the event loop is free: **`/health` answers
   during inference**, and new requests are still accepted or refused promptly.
5. A request waits at most `REQUEST_TIMEOUT_S` for its result, then gets `503` too. That is well
   under Cloudflare's ~100 s `524`.

The app treats any failure, a `503` included, as "the cloud cannot answer right now": its circuit
breaker counts it and the frame goes to the on-device model. The server's queue is in memory and
dies with the process. That is fine, because the phone's queue is the durable one and it retries.

At a few phones sending frames seconds apart, most batches have one frame. What helps now is the
unblocked event loop, the fast `503` and the second GPU. Batching pays off as usage grows. If a
T4 runs out of memory at batch 8, lower `MAX_BATCH_SIZE`. If `503`s appear under normal load,
raise `QUEUE_SIZE`, but not so far that `QUEUE_SIZE / MAX_BATCH_SIZE` batches take longer than
`REQUEST_TIMEOUT_S`.

Every successful response carries two headers, so batching can be seen from outside with
`curl -i`: `X-Inference-Batch-Size` (how many frames rode in its batch) and
`X-Inference-Device` (which GPU ran it). The server also logs a `batch of N on cuda:K in X ms`
line per batch.

### Tests

`tests/` runs the server against a fake model, so no GPU or Ultralytics fork is needed:

```bash
cd inference
pip install -r requirements-test.txt
pytest tests
```

They cover the response shape, `/health` answering mid-inference, batching, both GPUs being
used, the `503`s and a failed batch. How the real model behaves on two T4s is checked on Kaggle
(`KAGGLE.md`, "Checking the queue").

---

## Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/health` | None | Returns `{"status": "ok"}`, promptly even during inference. Use for monitoring |
| `POST` | `/infer` | Bearer token | Send raw JPEG bytes; returns detections JSON. `400` for an empty or unreadable body, `401` for a wrong key, `503` + `Retry-After` when the queue is full or the wait times out |

### `POST /infer` request

- `Content-Type: image/jpeg`
- `Authorization: Bearer <INFERENCE_API_KEY>`
- Body: raw JPEG bytes (no multipart/form-data)

### `POST /infer` response shape

Matches `InferenceResponseDto` in the Android app exactly:

```json
{
  "predictions": [
    {
      "class": "egg",
      "confidence": 0.92,
      "x": 320.0,
      "y": 240.0,
      "width": 80.0,
      "height": 60.0
    }
  ],
  "image": {"width": 640, "height": 640}
}
```

- `x`, `y` — center coordinates of the bounding box
- `width`, `height` — box dimensions (not corners)
- Empty `predictions: []` when no detections pass the threshold

---

## Updating the model weights

When Tabada produces new weights:

1. Replace `inference/weights/yolo26n-efficientnetv2s.pt` (tracked via Git LFS)
2. Bump the training version (`v1` → `v2`) in `MODEL_VERSION`'s default in `server.py` and the
   notebook, so cloud results from the new weights are distinguishable
3. Re-export the on-device models under the same version and replace them in
   `app/src/main/assets/models/` (`export/README.md`)
4. Commit: `git commit -m "chore(inference): update model weights vX"`
5. Rebuild and push: `docker build ... && docker push ... ghcr.io/DMKuZu/agartha-inference:v2`
6. Restart the droplet with the new image tag
