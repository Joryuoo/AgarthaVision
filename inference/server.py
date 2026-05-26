import io
import os

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from PIL import Image
from ultralytics import YOLO

API_KEY = os.environ["INFERENCE_API_KEY"]
WEIGHTS_PATH = os.environ.get("WEIGHTS_PATH", "weights/best.pt")
MODEL_VERSION = os.environ.get("MODEL_VERSION", "yolov26-efficientnetv2-v1")

app = FastAPI()
security = HTTPBearer()
model: YOLO | None = None


@app.on_event("startup")
def load_model() -> None:
    global model
    model = YOLO(WEIGHTS_PATH)


def verify_key(creds: HTTPAuthorizationCredentials = Depends(security)) -> None:
    if creds.credentials != API_KEY:
        raise HTTPException(status_code=401, detail="Invalid API key")


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/infer")
async def infer(request: Request, _: None = Depends(verify_key)) -> dict:
    raw = await request.body()
    if not raw:
        raise HTTPException(status_code=400, detail="Empty request body")

    img = Image.open(io.BytesIO(raw)).convert("RGB")
    results = model(img)[0]

    predictions = []
    for box in results.boxes:
        conf = float(box.conf)
        cls_name = results.names[int(box.cls)]
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
    }
