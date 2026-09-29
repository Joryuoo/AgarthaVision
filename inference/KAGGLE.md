# Running the Inference Server on Kaggle

Fallback for when the AMD GPU droplet is unavailable. Runs [`server.py`](server.py) on a
free Kaggle GPU and exposes it to the Android app over a public tunnel.

**Dev / demo only.** The tunnel URL is ephemeral and Kaggle sessions are time-limited — do
not use this as a stable endpoint or for real patient data.

Use the ready-made notebook: [`kaggle_inference.ipynb`](kaggle_inference.ipynb). The steps
below are what that notebook automates.

---

## One-time prep

1. **Verify your phone** — Kaggle → *Settings → Phone Verification*. Internet access in
   notebooks is gated behind this, and you need it for the `pip git+` install and the tunnel.
2. **Upload the weights as a Dataset:**
   - Run `git lfs pull` locally first so `weights/yolo26n-efficientnetv2b0.pt` is the real ~15 MB file, not the
     LFS pointer.
   - Kaggle → *Datasets → New Dataset* → drag in `yolo26n-efficientnetv2b0.pt` (and
     `yolo26n-mobilenetv4convsmall.pt` if you want to try it).
   - Ignore the **"Switch to Models"** banner — a `.pt` is valid dataset content; that banner
     is just a non-blocking suggestion.
   - Title it exactly **`agartha-weights`** (lowercase, hyphenated) → mounts at
     `/kaggle/input/agartha-weights/yolo26n-efficientnetv2b0.pt`. Keep it **Private**. Click **Create**.

## Per-session steps

1. Open [`kaggle_inference.ipynb`](kaggle_inference.ipynb) in Kaggle (File → Import Notebook,
   or upload it).
2. Right sidebar: **Accelerator → GPU T4 x2**, **Internet → On**, **Add Input → agartha-weights**.
3. In the config cell, set `INFERENCE_API_KEY` to a strong secret (must match the app).
4. **Run all cells.** The last cell (cloudflared) runs forever and prints a
   `https://<random>.trycloudflare.com` URL — that's your public base URL. Leave it running.
5. Wire the app — in `local.properties`, then rebuild:
   ```properties
   INFERENCE_URL_DEV=https://<random>.trycloudflare.com
   INFERENCE_API_KEY=<the-same-secret>
   ```

The API contract is identical to the droplet (`GET /health`, `POST /infer` with a
`Bearer` token and raw JPEG body), so no app code changes are needed. HTTPS from the tunnel
also avoids the Android cleartext-traffic restriction.

---

## Gotchas

- **The URL changes every session.** New run = new `trycloudflare.com` URL = edit
  `local.properties` + rebuild. For a stable URL, use the ngrok variant below.
- **Idle shutdown.** Kaggle kills idle sessions (~20–40 min of no activity) and caps at
  12 h/session, 30 h/week GPU. The app's 2 s polling may not register as notebook activity —
  keep the notebook tab open and interact occasionally during a demo.
- **Weights path 404.** If the assert in the config cell fails, check the left **Input** panel
  for the real folder name and update `WEIGHTS_PATH`.
- **Start-up takes longer than one model load.** The server loads and warms up one model per
  GPU before it answers, so the first `/infer` is not slow any more. The launch cell waits 20 s.
- **Keep `server.py` in sync.** The notebook's `%%writefile` cell is a verbatim copy of
  [`server.py`](server.py); if the server changes, update both.

## Checking the queue

The server queues requests and runs them in batches, one worker per GPU (README.md, "Queue and
batching"). These checks need the real model, so they run here, not in `tests/`:

1. **Both GPUs load.** The GPU cell prints `cuda:0` and `cuda:1`, and the server's start-up log
   ends with `serving … on cuda:0, cuda:1`.
2. **`/health` answers during inference.** In a notebook cell, fire a burst and probe health
   while it runs:
   ```python
   import concurrent.futures, requests, time
   jpg = open("/kaggle/input/<a-sample-dataset>/sample.jpg", "rb").read()
   H = {"Authorization": f"Bearer {os.environ['INFERENCE_API_KEY']}", "Content-Type": "image/jpeg"}
   post = lambda _: requests.post("http://localhost:6767/infer", data=jpg, headers=H)
   with concurrent.futures.ThreadPoolExecutor(24) as pool:
       burst = pool.map(post, range(24))
       t = time.time(); requests.get("http://localhost:6767/health"); print("health", time.time() - t, "s")
       responses = list(burst)
   ```
3. **Batching and both GPUs.** For the same burst, print
   `[(r.status_code, r.headers.get("X-Inference-Batch-Size"), r.headers.get("X-Inference-Device")) for r in responses]`.
   Batch sizes above 1 show batching, and both `cuda:0` and `cuda:1` should appear. The log shows
   one `batch of N on cuda:K` line per batch, and `!nvidia-smi` during a burst shows both T4s busy.
4. **A full queue answers `503`.** Restart the server with `QUEUE_SIZE=2` and repeat the burst:
   some responses are `503` with a `Retry-After` header, and none hang.

## Optional: stable URL with your custom domain (Cloudflare Tunnels)

For a permanent, stable URL across sessions (e.g., `https://api.yourdomain.com`), you can use a **Named Cloudflare Tunnel** instead of the random `trycloudflare.com` URLs. Unlike ngrok, custom domains are completely free on Cloudflare.

1. Add your domain to a free [Cloudflare Zero Trust](https://dash.cloudflare.com) account.
2. Go to **Networks → Tunnels** and create a new tunnel, routing your domain to `http://localhost:8000`.
3. Cloudflare will provide a token. **Do not hardcode this token in the notebook!**
4. In Kaggle, go to **Add-ons → Secrets** and add a new secret named `CLOUDFLARE_TUNNEL_TOKEN` with your token.
5. Replace the default `cloudflared` cell in the notebook with this Python snippet to securely start your tunnel:

```python
import subprocess
from kaggle_secrets import UserSecretsClient

# Retrieve your secure token
token = UserSecretsClient().get_secret("CLOUDFLARE_TUNNEL_TOKEN")

# Download and start the tunnel
!wget -q https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -O cloudflared && chmod +x cloudflared
subprocess.Popen(["./cloudflared", "tunnel", "--no-autoupdate", "run", "--token", token])
print("Tunnel started in background. Traffic to your domain is now routed here.")
```

Your `INFERENCE_URL_DEV` will now stay stable every session.
