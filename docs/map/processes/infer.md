# infer

Asking the model what is in a frame.

**Input** — raw JPEG bytes and the active session id.
**Output** — a flagged frame added to the queue, or silence.

**consumes** [`Session`](../objects/Session.md)
**produces** nothing durable by itself — it hands off to [`capture`](capture.md) step 5

## Movement

1. **POST the raw bytes.** `CaptureFieldUseCase` hands the JPEG to `RemoteInferenceEngine`
   (`domain/usecase/capture/CaptureFieldUseCase.kt`), which wraps it as an `image/jpeg`
   request body and calls `InferenceApi.infer` (`data/inference/RemoteInferenceEngine.kt:38-40`).
   No multipart, no base64 — the body *is* the image (`data/remote/InferenceApi.kt:23-24`).
   Transport failures route through `NetworkErrorMapper` rather than being wrapped by hand.
   This runs once per shutter tap, not on a timer (see [`capture`](capture.md)).

   The engine sits behind the `InferenceEngine` interface (`domain/inference/InferenceEngine.kt`)
   and is injected directly, not chosen: capture still calls the cloud only. A second
   implementation, `OnDeviceInferenceEngine` (`data/inference/ondevice/`), runs a bundled TFLite
   build of the same weights with LiteRT and returns the same shape. It is provided by
   `core/di/InferenceModule.kt` but nothing routes to it yet; the background inference queue
   will. See [On-device engine](#on-device-engine).
2. **Authenticate.** An OkHttp interceptor attaches
   `Authorization: Bearer <BuildConfig.INFERENCE_API_KEY>` to every request
   (`core/di/InferenceModule.kt:41-46`). The server compares it literally
   (`inference/server.py:24-27`). Timeouts are 10 s connect, 30 s read
   (`core/di/InferenceModule.kt:30-31`).
3. **Run the model.** The container loads the weights once at startup
   (`inference/server.py:18-21`) and returns, per box, `class`, `confidence`, and `x, y,
   width, height` from `box.xywh` — **centre-x, centre-y, width, height, in pixels**
   (`inference/server.py:44-57`), plus `model_version` and the image dimensions
   (`inference/server.py:59-63`).
4. **Apply no filter.** The server returns every box the model produced. There is no
   confidence threshold on either side; the human is the threshold
   (`../../constraints.md` C7).
5. **Record empties too.** An empty `predictions` list is still a result the server returned, so
   it is recorded like any other: a clean field is a normal negative result, not a reason to
   skip the store (`domain/usecase/capture/CaptureFieldUseCase.kt`).
6. **Flag.** Any response — detections or none — builds a `FrameSource.MODEL` `FlaggedFrame`
   carrying the JPEG, the predictions (possibly empty), the model version, and the image
   dimensions, and adds it to the store (`domain/usecase/capture/CaptureFieldUseCase.kt`). The
   predictions are domain `Prediction` values by this point, not the wire DTO —
   `RemoteInferenceEngine` maps them (`data/inference/PredictionMapper.kt`), so nothing under
   `domain/` imports a response type.

## The contract

`POST /infer` → `{ predictions: [{ class, confidence, x, y, width, height }],
image: { width, height }, model_version, inference_ms }`. `inference_ms` is the server's own
compute time, optional so older containers still work; when absent the client books the whole
round trip as network (`data/inference/RemoteInferenceEngine.kt:54`, `inference/server.py`). Client side that is
`data/remote/dto/InferenceResponseDto.kt:11-29`; server side
`inference/server.py:59-63`. The shape is intentionally Roboflow-compatible so the hosting
backend can change without touching the mobile code — Roboflow itself is a dead path.

`GET /health` returns 200 once the model is loaded (`inference/server.py:29-31`).

## Connectivity

`NetworkMonitor` probes `/health` every 10 s for the duration of an active session; **two
consecutive failures** flip the status to disconnected
(`core/connectivity/NetworkMonitor.kt:59-79`), which surfaces as
`ui/capture/ConnectionLossBanner.kt`. The loop is cancelled and the status resets when the
session goes idle (`core/connectivity/NetworkMonitor.kt:44-49`).

## On-device engine

`OnDeviceInferenceEngine` (`data/inference/ondevice/OnDeviceInferenceEngine.kt`) is the offline
counterpart. It is built and tested but not yet called by capture.

- **Which model.** `assets/models/` bundles both precisions, each a `<model_version>.tflite` and
  a `<model_version>.json` manifest written by `inference/export/export_mobile.py`.
  `OnDeviceModels.SHIPPED` (`data/inference/ondevice/ModelStore.kt`) picks the one production
  runs; the manifest's `model_version` is what the sample records.
- **Loading.** Compiled once on first use, GPU first with LiteRT placing unsupported ops on the
  CPU, CPU-only if the GPU will not initialise. A missing asset, bad manifest or refused model
  makes `isAvailable()` false and `infer()` throw `InferenceConnectionException`; nothing
  crashes, and the failure is remembered rather than retried per frame. All model calls run on
  one dedicated thread.
- **Matching the cloud.** Preprocessing is Ultralytics' letterbox (114 grey, same rounding),
  a no-op for 640x640 captures. The head is not end-to-end, so `YoloOutputDecoder` runs
  per-class NMS with the container's own defaults (conf 0.25, IoU 0.7, 300 boxes), un-letterboxes,
  clips to the frame, and returns centre-based pixels like `server.py`. The TFLite head emits
  box geometry **normalised to 0..1**; the manifest's `box_coordinates` says so and the
  decoder scales it. Reading it as pixels was the zero-match fault the first attempt hit.
- **Latency.** Every frame logs `pre/infer/post/total` ms under tag `OnDeviceInference`.
  `OnDeviceInferenceParityTest` (androidTest) runs both precisions on the phone against the
  cloud's answers for the same frames; `inference/export/make_parity_fixture.py` builds those.

## Hits

- **Any response-shape change breaks two files at once**: the DTO
  (`data/remote/dto/InferenceResponseDto.kt`) and the entity mapper
  (`data/local/mapper/VerificationMapper.kt:36-39`), which copies `x, y, width, height`
  straight into `bbox_x/y/w/h` with no transformation.
- **Class-label strings are load-bearing.** `EggSpecies.fromClassLabel` matches canonical names
  and a small alias set (`domain/model/EggSpecies.kt:7-22`). An unrecognised label is preserved
  raw in `class_label` and behaves as a `WRONG_CLASS` candidate — renaming a model class
  silently changes every verdict computation and every species grouping.
- The bearer key name. `app/build.gradle.kts:67` reads `INFERENCE_API_KEY_DEV` and `:90` reads
  `INFERENCE_API_KEY_PROD`, aligned with `local.properties.example:35-36` (`../../constraints.md` C10).

## Does not hit

- **Persistence.** The inference service is stateless — it never stores an image and never sees
  a sample id (`inference/server.py:34-63`). Nothing about a schema change reaches it.
- **The tap's outcome.** A failed `/infer` call is not lost: `CaptureFieldUseCase` catches
  `InferenceConnectionException` and records the frame as a Manual Capture instead
  (`domain/usecase/capture/CaptureFieldUseCase.kt`). Recording continues regardless; only two
  consecutive `/health` failures raise the connection-loss banner.
- **Supabase.** Losing Supabase mid-session does not stop recording. Only losing the inference
  container does.
