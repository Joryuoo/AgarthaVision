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
   - Run `git lfs pull` locally first so `weights/best.pt` is the real ~42 MB file, not the
     LFS pointer.
   - Kaggle → *Datasets → New Dataset* → drag in `best.pt`.
   - Ignore the **"Switch to Models"** banner — a `.pt` is valid dataset content; that banner
     is just a non-blocking suggestion.
   - Title it exactly **`agartha-weights`** (lowercase, hyphenated) → mounts at
     `/kaggle/input/agartha-weights/best.pt`. Keep it **Private**. Click **Create**.

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
- **First `/infer` is slow** (model warm-up); later calls are fast. A T4 runs this YOLO in
  tens of ms — far faster than the 1 frame / 2 s the app sends.
- **Keep `server.py` in sync.** The notebook's `%%writefile` cell is a verbatim copy of
  [`server.py`](server.py); if the server changes, update both.

## Optional: stable URL with ngrok

Replace the cloudflared cell with ngrok and a free static domain so the URL survives across
sessions (claim the domain once at <https://dashboard.ngrok.com>):

```python
!pip install -q pyngrok
from pyngrok import ngrok
ngrok.set_auth_token("<your-ngrok-authtoken>")
public = ngrok.connect(8000, domain="<your-name>.ngrok-free.app")
print(public.public_url)
import time
while True:
    time.sleep(3600)  # keep the cell (and tunnel) alive
```

Then `INFERENCE_URL_DEV` stays `https://<your-name>.ngrok-free.app` every session.
