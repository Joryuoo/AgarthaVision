# Agartha Inference Container

FastAPI server wrapping the custom Ultralytics fork (YOLOv26 + EfficientNetV2) for
egg detection. Runs on a DigitalOcean MI300X AMD GPU droplet.

> **Cost reminder:** The MI300X droplet bills ~$1.99/hr while running.
> **Destroy the droplet immediately after every test or demo session.**

---

## Prerequisites

- Docker (DMKuZu only — teammates do not need this)
- GHCR write access: `docker login ghcr.io -u <github-username> -p <PAT>`
- `git lfs install` run once on your machine (for `weights/best.pt`)
- `best.pt` from Tabada placed at `inference/weights/best.pt`

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
scp inference/weights/best.pt root@<droplet-ip>:/root/app/weights/
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

### Build the image

```bash
# From repo root:
docker build -t ghcr.io/DMKuZu/agartha-inference:v1 inference/
```

### Push to GHCR

```bash
docker push ghcr.io/DMKuZu/agartha-inference:v1
```

### Run on a fresh GPU droplet

```bash
docker run \
  --device=/dev/kfd \
  --device=/dev/dri \
  --group-add video \
  -p 8000:8000 -d \
  -e INFERENCE_API_KEY="<your-secret>" \
  -e CONFIDENCE_THRESHOLD="0.4" \
  ghcr.io/dmkuzu/agartha-inference:v1
```

---

## Environment variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `INFERENCE_API_KEY` | Yes | — | Bearer token checked on every `POST /infer` |
| `CONFIDENCE_THRESHOLD` | No | `0.4` | Minimum confidence to include a detection in the response |
| `WEIGHTS_PATH` | No | `weights/best.pt` | Path to the model weights file |

---

## Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/health` | None | Returns `{"status": "ok"}` — use for monitoring |
| `POST` | `/infer` | Bearer token | Send raw JPEG bytes; returns detections JSON |

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

When Tabada produces a new `best.pt`:

1. Replace `inference/weights/best.pt` (tracked via Git LFS)
2. Commit: `git commit -m "chore(inference): update model weights vX"`
3. Rebuild and push: `docker build ... && docker push ... ghcr.io/DMKuZu/agartha-inference:v2`
4. Restart the droplet with the new image tag
