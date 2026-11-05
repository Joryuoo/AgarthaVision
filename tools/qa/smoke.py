"""Shared localhost fake-model or designated staging HTTP smoke, never an auth bypass."""
import argparse
import io
import json
import math
import os
from pathlib import Path
import platform
import socket
import subprocess
import sys
import threading
import time
import datetime as dt


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--local", action="store_true", help="Start localhost fake-model service with ephemeral JWT signing key")
    parser.add_argument("--output", default="build/qa/smoke.json")
    parser.add_argument("--requests", type=int, default=10)
    args = parser.parse_args()
    if not 1 <= args.requests <= 100:
        parser.error("--requests must be between 1 and 100")
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    result = {"date": dt.datetime.now(dt.timezone.utc).isoformat(), "mode": "localhost-fake-model" if args.local else "designated-staging",
              "revision": os.environ.get("QA_DEPLOYED_REVISION"), "environment": platform.platform(), "records": []}
    server = thread = sock = None
    failed = False
    try:
        import httpx
        from PIL import Image
        if args.local:
            import importlib.util
            root = Path(__file__).resolve().parents[2]
            spec = importlib.util.spec_from_file_location("qa_fixture", root / "inference/tests/conftest.py")
            fixture = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(fixture)
            import server as application
            import uvicorn
            application.INFERENCE_DEVICES = "cpu"
            application.MODEL_VERSION = "qa-fake-model-not-clinical-evidence"
            application.jwks.fetch_data = fixture.public_jwks
            token = fixture.user_token()
            sock = socket.socket()
            sock.bind(("127.0.0.1", 0))
            url = f"http://127.0.0.1:{sock.getsockname()[1]}"
            server = uvicorn.Server(uvicorn.Config(application.app, log_level="warning", access_log=False))
            thread = threading.Thread(target=server.run, kwargs={"sockets": [sock]}, daemon=True)
            thread.start()
            result["revision"] = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
        else:
            url = os.environ.get("QA_SMOKE_URL", "").rstrip("/")
            token = os.environ.get("QA_SMOKE_TOKEN", "")
            if not url or not token or not result["revision"]:
                raise RuntimeError("Missing QA_SMOKE_URL, QA_SMOKE_TOKEN or QA_DEPLOYED_REVISION; staging smoke was not executed")
            if not url.startswith("https://"):
                raise RuntimeError("Designated staging smoke requires HTTPS")
        with httpx.Client(timeout=10, follow_redirects=False, trust_env=False) as client:
            deadline = time.monotonic() + 30
            while True:
                try:
                    health = client.get(url + "/health", timeout=max(.1, min(10, deadline - time.monotonic())))
                    if health.status_code == 200:
                        break
                except httpx.HTTPError:
                    pass
                if time.monotonic() >= deadline:
                    raise RuntimeError("Readiness failed within bounded 30 second wait (each request timeout 10 seconds)")
                time.sleep(0.25)

            def record(case, expected, action):
                nonlocal failed
                try:
                    action()
                    actual, status = "assertions passed", "pass"
                except (AssertionError, httpx.HTTPError, ValueError, KeyError, TypeError) as error:
                    # No response body, headers or credentials in reports.
                    actual, status = type(error).__name__ + ": smoke assertion/request failed", "fail"
                    failed = True
                result["records"].append({"case_id": case, "requirement_id": "QA-api-smoke", "expected": expected, "actual": actual, "status": status})

            def check_health():
                assert health.status_code == 200 and health.json() == {"status": "ok"}

            buffer = io.BytesIO()
            Image.new("RGB", (64, 48), (200, 180, 160)).save(buffer, format="JPEG")
            image = buffer.getvalue()

            def reject_anonymous():
                response = client.post(url + "/infer", content=image, headers={"Content-Type": "image/jpeg"})
                assert response.status_code == 401

            durations = []

            def authenticated():
                start = time.perf_counter()
                response = client.post(url + "/infer", content=image, headers={"Content-Type": "image/jpeg", "Authorization": "Bearer " + token})
                durations.append((time.perf_counter() - start) * 1000)
                assert response.status_code == 200
                body = response.json()
                assert set(body) == {"model_version", "predictions", "image", "inference_ms"}
                assert isinstance(body["model_version"], str) and body["model_version"]
                assert body["image"] == {"width": 64, "height": 48}
                assert isinstance(body["inference_ms"], (int, float)) and math.isfinite(body["inference_ms"]) and body["inference_ms"] >= 0
                assert isinstance(body["predictions"], list)
                for box in body["predictions"]:
                    assert set(box) == {"class", "confidence", "x", "y", "width", "height"}
                    assert isinstance(box["class"], str)
                    assert all(isinstance(box[k], (int, float)) and math.isfinite(box[k]) for k in ("confidence", "x", "y", "width", "height"))
                    assert 0 <= box["confidence"] <= 1 and box["width"] > 0 and box["height"] > 0

            record("SMOKE-HEALTH", "GET /health: 200 status ok", check_health)
            record("SMOKE-AUTH-REJECT", "POST /infer without bearer: 401", reject_anonymous)
            start = time.perf_counter()
            for index in range(args.requests):
                record(f"SMOKE-INFER-{index + 1}", "authenticated synthetic 64x48 JPEG: 200 and app response schema", authenticated)
            elapsed = time.perf_counter() - start
            ordered = sorted(durations)
            percentile = lambda p: ordered[max(0, math.ceil(p * len(ordered)) - 1)] if ordered else None
            errors = sum(r["status"] == "fail" for r in result["records"] if r["case_id"].startswith("SMOKE-INFER"))
            result["performance"] = {"label": "HTTP smoke microbenchmark; not real-model/device performance evidence" if args.local else "staging synthetic HTTP microbenchmark",
                                     "workload": "synthetic 64x48 uniform JPEG", "concurrency": 1, "requests": args.requests,
                                     "duration_s": elapsed, "throughput_requests_s": args.requests / elapsed, "error_rate": errors / args.requests,
                                     "latency_ms": {"p50": percentile(.5), "p95": percentile(.95), "p99": percentile(.99)},
                                     "device_server_metrics": "unavailable: no device/GPU telemetry collected"}
    except Exception as error:
        failed = True
        # Restrict errors to safe known setup messages; network errors may contain URLs.
        message = str(error) if isinstance(error, (RuntimeError, ModuleNotFoundError)) else type(error).__name__
        result["records"].append({"case_id": "SMOKE-SETUP", "requirement_id": "QA-api-smoke", "expected": "environment ready", "actual": message, "status": "fail"})
    finally:
        if server:
            server.should_exit = True
        if thread:
            thread.join(timeout=10)
            if thread.is_alive():
                failed = True
                result["cleanup"] = "failed: local service thread did not stop"
            else:
                result["cleanup"] = "local service stopped"
        if sock:
            sock.close()
        result["status"] = "fail" if failed else "pass"
        output.write_text(json.dumps(result, indent=2), encoding="utf-8")
        print(f"Smoke {result['status']} ({result['mode']}): {output}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
