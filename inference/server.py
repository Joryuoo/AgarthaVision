import asyncio
import io
import logging
import os
import time
from contextlib import asynccontextmanager
from dataclasses import dataclass

from fastapi import Depends, FastAPI, HTTPException, Request, Response
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from PIL import Image
from ultralytics import YOLO

API_KEY = os.environ["INFERENCE_API_KEY"]
WEIGHTS_PATH = os.environ.get("WEIGHTS_PATH", "weights/yolo26n-efficientnetv2b0.pt")
MODEL_VERSION = os.environ.get("MODEL_VERSION", "yolo26n-effv2b0-v1-cloud-fp32")

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
    return model.predict(images, device=device, verbose=False)


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
security = HTTPBearer()


def verify_key(creds: HTTPAuthorizationCredentials = Depends(security)) -> None:
    if creds.credentials != API_KEY:
        raise HTTPException(status_code=401, detail="Invalid API key")


def decode(raw: bytes) -> Image.Image:
    return Image.open(io.BytesIO(raw)).convert("RGB")


def busy(detail: str) -> HTTPException:
    return HTTPException(status_code=503, detail=detail, headers={"Retry-After": str(RETRY_AFTER_S)})


@app.get("/health")
async def health() -> dict:
    # Runs on the event loop, which inference no longer blocks, so it answers during a batch.
    return {"status": "ok"}


@app.post("/infer")
async def infer(request: Request, response: Response, _: None = Depends(verify_key)) -> dict:
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
