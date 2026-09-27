"""The inference server's queue (14zcqntj6p2): batching, one worker per GPU, 503 backpressure,
a /health that answers mid-inference, and an unchanged API contract.

Run from `inference/`: `pip install -r requirements-test.txt && pytest tests`.
"""
import asyncio
import io
import time

import httpx
import pytest
from PIL import Image

import server
from conftest import FakeYOLO

AUTH = {"Authorization": "Bearer test-key", "Content-Type": "image/jpeg"}


def jpeg() -> bytes:
    buffer = io.BytesIO()
    Image.new("RGB", (64, 48), (200, 180, 160)).save(buffer, format="JPEG")
    return buffer.getvalue()


@pytest.fixture(autouse=True)
def reset(monkeypatch):
    FakeYOLO.batches.clear()
    FakeYOLO.delay_s = 0.0
    FakeYOLO.fail = False
    monkeypatch.setattr(server, "INFERENCE_DEVICES", "cuda:0,cuda:1")
    monkeypatch.setattr(server, "MAX_BATCH_SIZE", 8)
    monkeypatch.setattr(server, "MAX_QUEUE_DELAY_MS", 15.0)
    monkeypatch.setattr(server, "QUEUE_SIZE", 32)
    monkeypatch.setattr(server, "REQUEST_TIMEOUT_S", 25.0)


def serve(test):
    """Runs [test] against the app with its lifespan, workers and all, on one event loop."""

    async def main():
        async with server.lifespan(server.app):
            FakeYOLO.batches.clear()  # forget the start-up warm-up passes
            transport = httpx.ASGITransport(app=server.app)
            async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
                return await test(client)

    return asyncio.run(main())


# ── the contract ─────────────────────────────────────────────────────────────────────────────


def test_infer_keeps_the_response_shape_the_app_parses():
    async def test(client):
        return await client.post("/infer", content=jpeg(), headers=AUTH)

    response = serve(test)

    assert response.status_code == 200
    body = response.json()
    assert set(body) == {"model_version", "predictions", "image", "inference_ms"}
    assert body["model_version"] == "yolo12n-effv2s-v1-cloud-fp32"
    assert body["image"] == {"width": 64, "height": 48}
    assert body["predictions"] == [{
        "class": "Ascaris lumbricoides",
        "confidence": 0.91,
        "x": 120.0,
        "y": 80.0,
        "width": 30.0,
        "height": 20.0,
    }]


def test_a_wrong_key_is_refused():
    async def test(client):
        return await client.post("/infer", content=jpeg(), headers={**AUTH, "Authorization": "Bearer nope"})

    assert serve(test).status_code == 401


def test_an_empty_or_unreadable_body_is_a_400_not_a_500():
    async def test(client):
        empty = await client.post("/infer", content=b"", headers=AUTH)
        garbage = await client.post("/infer", content=b"not a jpeg", headers=AUTH)
        return empty, garbage

    empty, garbage = serve(test)

    assert empty.status_code == 400
    assert garbage.status_code == 400


# ── the event loop stays free ────────────────────────────────────────────────────────────────


def test_health_answers_while_an_inference_is_running():
    FakeYOLO.delay_s = 0.5

    async def test(client):
        inference = asyncio.create_task(client.post("/infer", content=jpeg(), headers=AUTH))
        await asyncio.sleep(0.1)  # the batch is now on its GPU thread
        started = time.perf_counter()
        health = await client.get("/health")
        health_s = time.perf_counter() - started
        still_running = not inference.done()
        await inference
        return health, health_s, still_running

    health, health_s, still_running = serve(test)

    assert health.status_code == 200
    assert health.json() == {"status": "ok"}
    assert still_running, "the inference should still have been running when /health answered"
    assert health_s < 0.2


# ── micro-batching and the second GPU ────────────────────────────────────────────────────────


def test_concurrent_requests_are_batched_together():
    FakeYOLO.delay_s = 0.2

    async def test(client):
        return await asyncio.gather(*[
            client.post("/infer", content=jpeg(), headers=AUTH) for _ in range(12)
        ])

    responses = serve(test)

    assert all(r.status_code == 200 for r in responses)
    assert sum(size for _, size in FakeYOLO.batches) == 12
    assert max(size for _, size in FakeYOLO.batches) > 1
    assert max(int(r.headers["X-Inference-Batch-Size"]) for r in responses) > 1


def test_a_batch_never_exceeds_the_limit():
    FakeYOLO.delay_s = 0.1
    server.MAX_BATCH_SIZE = 3

    async def test(client):
        return await asyncio.gather(*[
            client.post("/infer", content=jpeg(), headers=AUTH) for _ in range(10)
        ])

    responses = serve(test)

    assert all(r.status_code == 200 for r in responses)
    assert max(size for _, size in FakeYOLO.batches) <= 3


def test_both_gpus_are_used_under_concurrent_load():
    FakeYOLO.delay_s = 0.2
    server.MAX_BATCH_SIZE = 2

    async def test(client):
        return await asyncio.gather(*[
            client.post("/infer", content=jpeg(), headers=AUTH) for _ in range(8)
        ])

    responses = serve(test)

    assert {device for device, _ in FakeYOLO.batches} == {"cuda:0", "cuda:1"}
    assert {r.headers["X-Inference-Device"] for r in responses} == {"cuda:0", "cuda:1"}


# ── backpressure ─────────────────────────────────────────────────────────────────────────────


def test_a_full_queue_answers_503_with_retry_after_instead_of_hanging():
    FakeYOLO.delay_s = 0.5
    server.INFERENCE_DEVICES = "cuda:0"
    server.MAX_BATCH_SIZE = 1
    server.QUEUE_SIZE = 2

    async def test(client):
        started = time.perf_counter()
        responses = await asyncio.gather(*[
            client.post("/infer", content=jpeg(), headers=AUTH) for _ in range(6)
        ])
        return responses, time.perf_counter() - started

    responses, _ = serve(test)

    rejected = [r for r in responses if r.status_code == 503]
    assert rejected, "some requests should have found the queue full"
    assert all(r.headers["Retry-After"] == str(server.RETRY_AFTER_S) for r in rejected)
    assert all(r.status_code in (200, 503) for r in responses)


def test_a_request_that_waits_too_long_gets_503_not_a_tunnel_timeout():
    FakeYOLO.delay_s = 0.5
    server.REQUEST_TIMEOUT_S = 0.2

    async def test(client):
        return await client.post("/infer", content=jpeg(), headers=AUTH)

    response = serve(test)

    assert response.status_code == 503
    assert "Retry-After" in response.headers


def test_a_failed_batch_fails_its_requests_and_the_worker_carries_on():
    async def test(client):
        FakeYOLO.fail = True
        failed = await client.post("/infer", content=jpeg(), headers=AUTH)
        FakeYOLO.fail = False
        recovered = await client.post("/infer", content=jpeg(), headers=AUTH)
        return failed, recovered

    failed, recovered = serve(test)

    assert failed.status_code == 500
    assert recovered.status_code == 200
