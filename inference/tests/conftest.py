"""Runs server.py against a fake model, so its queue can be tested without a GPU or the fork.

The fork is installed from GitHub and needs CUDA to be useful, and neither is needed to test
what this suite covers: the queue, the batching, the 503s and the event loop staying free. The
real model is exercised on Kaggle (KAGGLE.md, "Checking the queue").
"""
import os
import sys
import threading
import time
import types
from pathlib import Path

os.environ.setdefault("INFERENCE_API_KEY", "test-key")
os.environ.setdefault("INFERENCE_DEVICES", "cuda:0,cuda:1")


class FakeBox:
    def __init__(self) -> None:
        self.conf = 0.91
        self.cls = 0
        self.xywh = [types.SimpleNamespace(tolist=lambda: [120.0, 80.0, 30.0, 20.0])]


class FakeResult:
    names = {0: "Ascaris lumbricoides"}

    def __init__(self) -> None:
        self.boxes = [FakeBox()]


class FakeYOLO:
    """Records every batch it is given, and can be made slow or broken."""

    lock = threading.Lock()
    batches: list[tuple[str, int]] = []
    delay_s = 0.0
    fail = False

    def __init__(self, weights_path: str) -> None:
        self.weights_path = weights_path

    def predict(self, images, device, verbose):
        with FakeYOLO.lock:
            FakeYOLO.batches.append((device, len(images)))
        time.sleep(FakeYOLO.delay_s)
        if FakeYOLO.fail:
            raise RuntimeError("CUDA out of memory")
        return [FakeResult() for _ in images]


sys.modules["ultralytics"] = types.SimpleNamespace(YOLO=FakeYOLO)
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
