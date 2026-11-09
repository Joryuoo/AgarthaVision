"""Runs server.py against a fake model, so its queue can be tested without a GPU or the fork.

The fork is installed from GitHub and needs CUDA to be useful, and neither is needed to test
what this suite covers: the queue, the batching, the 503s and the event loop staying free. The
real model is exercised on Kaggle (KAGGLE.md, "Checking the queue").

Auth is tested the same way: tokens are signed here with a throwaway ES256 key, and the server's
JWKS client is handed that key's public half instead of fetching Supabase's (the `jwks` fixture
in test_server.py).
"""
import os
import sys
import threading
import time
import types
import uuid
from pathlib import Path

import jwt
from cryptography.hazmat.primitives.asymmetric import ec

os.environ.setdefault("SUPABASE_URL", "https://test-project.supabase.co")
os.environ.setdefault("INFERENCE_DEVICES", "cuda:0,cuda:1")

ISSUER = os.environ["SUPABASE_URL"].rstrip("/") + "/auth/v1"

# Stands in for the project's signing key; the server only ever sees its public half.
SIGNING_KEY = ec.generate_private_key(ec.SECP256R1())
KID = "test-kid"


def public_jwks(private_key=SIGNING_KEY, kid: str = KID) -> dict:
    jwk = jwt.algorithms.ECAlgorithm.to_jwk(private_key.public_key(), as_dict=True)
    return {"keys": [{**jwk, "kid": kid, "alg": "ES256", "use": "sig"}]}


def user_token(key=SIGNING_KEY, kid: str = KID, algorithm: str = "ES256", expires_in: int = 3600, **claims) -> str:
    """A Supabase-shaped access token for a signed-in user. [claims] override any field, and a
    claim set to None is left out."""
    now = int(time.time())
    payload = {
        "iss": ISSUER,
        "sub": str(uuid.uuid4()),
        "aud": "authenticated",
        "role": "authenticated",
        "iat": now,
        "exp": now + expires_in,
        **claims,
    }
    payload = {name: value for name, value in payload.items() if value is not None}
    return jwt.encode(payload, key, algorithm=algorithm, headers={"kid": kid})


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
    agnostic_nms: list[bool] = []
    delay_s = 0.0
    fail = False

    def __init__(self, weights_path: str) -> None:
        self.weights_path = weights_path

    def predict(self, images, device, verbose, agnostic_nms=False):
        with FakeYOLO.lock:
            FakeYOLO.batches.append((device, len(images)))
            FakeYOLO.agnostic_nms.append(agnostic_nms)
        time.sleep(FakeYOLO.delay_s)
        if FakeYOLO.fail:
            raise RuntimeError("CUDA out of memory")
        return [FakeResult() for _ in images]


sys.modules["ultralytics"] = types.SimpleNamespace(YOLO=FakeYOLO)
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
