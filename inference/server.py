import asyncio
import io
import logging
import os
import time
from contextlib import asynccontextmanager
from dataclasses import dataclass

import jwt
from fastapi import Depends, FastAPI, HTTPException, Request, Response
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from PIL import Image
from ultralytics import YOLO

# Auth: the app sends the signed-in user's Supabase access token, and the server checks it
# against the project's public signing keys (JWKS). No shared secret. See README.md, "Auth".
SUPABASE_URL = os.environ["SUPABASE_URL"].rstrip("/")
JWT_ISSUER = f"{SUPABASE_URL}/auth/v1"
JWKS_URL = f"{JWT_ISSUER}/.well-known/jwks.json"
# Supabase's asymmetric signing algorithms. HS256 is left out on purpose: the project signs
# with ES256, so an HS256 token is a forgery, and accepting it would invite key confusion.
JWT_ALGORITHMS = ["ES256", "RS256"]
# How long the fetched key set is reused. A token with an unknown `kid` refetches it sooner,
# so a rotated key is picked up without a restart.
JWKS_CACHE_S = float(os.environ.get("JWKS_CACHE_S", "600"))
WEIGHTS_PATH = os.environ.get("WEIGHTS_PATH", "weights/yolo26n-mobilenetv4hybridlarge.pt")
MODEL_VERSION = os.environ.get("MODEL_VERSION", "yolo26n-mobv4hybridl-v1-cloud-fp32")

# Queue and micro-batching. See README.md, "Queue and batching", before changing these.
# Most frames per forward pass.
MAX_BATCH_SIZE = int(os.environ.get("MAX_BATCH_SIZE", "8"))
# How long a worker waits for companions after the first frame of a batch.
MAX_QUEUE_DELAY_MS = float(os.environ.get("MAX_QUEUE_DELAY_MS", "15"))
# Frames waiting for a GPU. A request that finds it full gets 503 at once.
QUEUE_SIZE = int(os.environ.get("QUEUE_SIZE", "32"))
# Kept below the app's 30 s read timeout, and far below Cloudflare's ~100 s 524.
REQUEST_TIMEOUT_S = float(os.environ.get("REQUEST_TIMEOUT_S", "25"))
# Sent with every 503, telling the client when to try again.
RETRY_AFTER_S = int(os.environ.get("RETRY_AFTER_S", "2"))
# Comma-separated torch devices, one worker each. Default: every visible GPU, else the CPU.
INFERENCE_DEVICES = os.environ.get("INFERENCE_DEVICES", "")

# uvicorn's own logger, so batch lines appear wherever uvicorn's output does (the notebook's).
log = logging.getLogger("uvicorn.error")


@dataclass
class Job:
    image: Image.Image
    future: asyncio.Future


def pick_devices() -> list[str]:
    if INFERENCE_DEVICES.strip():
        return [d.strip() for d in INFERENCE_DEVICES.split(",") if d.strip()]
    import torch

    if torch.cuda.is_available():
        return [f"cuda:{i}" for i in range(torch.cuda.device_count())]
    return ["cpu"]


def run_batch(model: YOLO, device: str, images: list[Image.Image]) -> list:
    # One forward pass for the whole batch. Ultralytics letterboxes each image itself.
    # agnostic_nms: an egg the model can't place would otherwise get one box per species it
    # considered (14zcqntk1vd). The app's on-device decoder applies the same rule.
    return model.predict(images, device=device, verbose=False, agnostic_nms=True)


async def worker(device: str, model: YOLO, queue: asyncio.Queue) -> None:
    """Take up to MAX_BATCH_SIZE queued frames and run them as one batch, forever."""
    loop = asyncio.get_running_loop()
    while True:
        batch = [await queue.get()]
        deadline = loop.time() + MAX_QUEUE_DELAY_MS / 1000
        while len(batch) < MAX_BATCH_SIZE:
            try:
                batch.append(queue.get_nowait())
                continue
            except asyncio.QueueEmpty:
                pass
            remaining = deadline - loop.time()
            if remaining <= 0:
                break
            await asyncio.sleep(min(remaining, 0.002))

        # A request that already timed out has nobody waiting for its answer.
        live = [job for job in batch if not job.future.done()]
        if not live:
            continue

        started_at = time.perf_counter()
        try:
            results = await asyncio.to_thread(run_batch, model, device, [j.image for j in live])
        except Exception as error:  # noqa: BLE001 - every waiting request must be answered
            log.exception("Batch of %d on %s failed", len(live), device)
            for job in live:
                if not job.future.done():
                    job.future.set_exception(error)
            continue
        compute_ms = (time.perf_counter() - started_at) * 1000
        log.info("batch of %d on %s in %.1f ms", len(live), device, compute_ms)

        for job, result in zip(live, results):
            if not job.future.done():
                job.future.set_result((result, compute_ms, len(live), device))


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.queue = asyncio.Queue(maxsize=QUEUE_SIZE)
    app.state.devices = pick_devices()
    app.state.workers = []
    warmup = Image.new("RGB", (640, 640))
    for device in app.state.devices:
        model = YOLO(WEIGHTS_PATH)
        # Loads the weights onto the device and builds its kernels now, not on the first frame.
        await asyncio.to_thread(run_batch, model, device, [warmup])
        app.state.workers.append(asyncio.create_task(worker(device, model, app.state.queue)))
    log.info("serving %s on %s", MODEL_VERSION, ", ".join(app.state.devices))
    yield
    for task in app.state.workers:
        task.cancel()


app = FastAPI(lifespan=lifespan)
# auto_error=False: a missing header is answered with 401 below on every FastAPI version
# (older ones answer 403 themselves, and Kaggle's FastAPI is not pinned).
security = HTTPBearer(auto_error=False)
# Short timeout: a JWKS fetch happens inside a request, which must answer within REQUEST_TIMEOUT_S.
jwks = jwt.PyJWKClient(JWKS_URL, lifespan=JWKS_CACHE_S, timeout=5)


def unauthorized(detail: str) -> HTTPException:
    return HTTPException(status_code=401, detail=detail, headers={"WWW-Authenticate": "Bearer"})


def verify_user(creds: HTTPAuthorizationCredentials | None = Depends(security)) -> dict:
    """Accept only a signed-in user's Supabase access token, and return its claims.

    A plain `def`, so FastAPI runs it on a thread: a JWKS fetch never blocks the event loop.
    """
    if creds is None:
        raise unauthorized("Missing bearer token")
    token = creds.credentials
    try:
        signing_key = jwks.get_signing_key_from_jwt(token)
    except jwt.PyJWKClientConnectionError:
        # Supabase cannot be reached, so the token cannot be judged either way. That is the
        # server's problem, not the caller's: 503, and the app falls back and retries.
        log.warning("JWKS unreachable at %s", JWKS_URL)
        raise busy("Cannot verify the token right now") from None
    except (jwt.PyJWKClientError, jwt.InvalidTokenError):
        # Not a JWT, or signed with a key this project does not publish.
        raise unauthorized("Invalid token") from None
    try:
        claims = jwt.decode(
            token,
            signing_key.key,
            algorithms=JWT_ALGORITHMS,
            audience="authenticated",
            issuer=JWT_ISSUER,
            options={"require": ["exp", "iss", "aud", "sub"]},
        )
    except jwt.ExpiredSignatureError:
        raise unauthorized("Token expired") from None
    except jwt.InvalidTokenError:
        raise unauthorized("Invalid token") from None
    # `aud` is "authenticated" for signed-in users, but `role` is what Supabase itself
    # authorises on, so check it too: an anon or service-role token is not a user.
    if claims.get("role") != "authenticated":
        raise unauthorized("Not a signed-in user")
    return claims


def decode(raw: bytes) -> Image.Image:
    return Image.open(io.BytesIO(raw)).convert("RGB")


def busy(detail: str) -> HTTPException:
    return HTTPException(status_code=503, detail=detail, headers={"Retry-After": str(RETRY_AFTER_S)})


@app.get("/health")
async def health() -> dict:
    # Runs on the event loop, which inference no longer blocks, so it answers during a batch.
    return {"status": "ok"}


@app.post("/infer")
async def infer(request: Request, response: Response, _: dict = Depends(verify_user)) -> dict:
    raw = await request.body()
    if not raw:
        raise HTTPException(status_code=400, detail="Empty request body")

    try:
        img = await asyncio.to_thread(decode, raw)
    except Exception:  # noqa: BLE001 - PIL raises several types for a bad image
        raise HTTPException(status_code=400, detail="Body is not a readable image") from None

    future = asyncio.get_running_loop().create_future()
    try:
        request.app.state.queue.put_nowait(Job(image=img, future=future))
    except asyncio.QueueFull:
        raise busy("Inference queue is full") from None

    try:
        result, compute_ms, batch_size, device = await asyncio.wait_for(future, timeout=REQUEST_TIMEOUT_S)
    except asyncio.TimeoutError:
        raise busy("Inference timed out in the queue") from None
    except Exception:  # noqa: BLE001 - the worker already logged the cause
        raise HTTPException(status_code=500, detail="Inference failed") from None

    # Headers, not body fields, so the response shape the app parses is unchanged. They are how
    # batching and the second GPU can be seen from outside: `curl -i`.
    response.headers["X-Inference-Batch-Size"] = str(batch_size)
    response.headers["X-Inference-Device"] = device

    predictions = []
    for box in result.boxes:
        conf = float(box.conf)
        cls_name = result.names[int(box.cls)]
        x, y, w, h = box.xywh[0].tolist()
        predictions.append({
            "class": cls_name,
            "confidence": round(conf, 4),
            "x": round(x, 2),
            "y": round(y, 2),
            "width": round(w, 2),
            "height": round(h, 2),
        })

    return {
        "model_version": MODEL_VERSION,
        "predictions": predictions,
        "image": {"width": img.width, "height": img.height},
        # Server compute only, excluding transport: the forward pass of the batch this frame
        # rode in. Lets the Android benchmark compare cloud compute against on-device compute
        # instead of against the client's network.
        "inference_ms": round(compute_ms, 2),
    }
