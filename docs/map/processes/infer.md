# infer

Asking the model what is in a frame.

**Input** — raw JPEG bytes and the active session id.
**Output** — a flagged frame added to the queue, or silence.

**consumes** [`Session`](../objects/Session.md)
**produces** nothing durable by itself — it hands off to [`capture`](capture.md) step 6

## Movement

1. **POST the raw bytes.** `InferFrameUseCase` wraps the JPEG as an `image/jpeg` request body
   and calls `InferenceApi.infer` (`domain/usecase/capture/InferFrameUseCase.kt:29-36`). No
   multipart, no base64 — the body *is* the image (`data/remote/InferenceApi.kt:23-24`).
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
5. **Discard empties.** An empty `predictions` array returns early — no toast, no row, nothing
   persisted (`domain/usecase/capture/InferFrameUseCase.kt:43-44`). Most frames end here.
6. **Flag.** A non-empty response builds a `FlaggedFrame` carrying the JPEG, the predictions,
   the model version, and the image dimensions, and adds it to the store
   (`domain/usecase/capture/InferFrameUseCase.kt:46-56`).

## The contract

`POST /infer` → `{ predictions: [{ class, confidence, x, y, width, height }],
image: { width, height }, model_version }`. Client side that is
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

## Hits

- **Any response-shape change breaks two files at once**: the DTO
  (`data/remote/dto/InferenceResponseDto.kt`) and the entity mapper
  (`data/local/mapper/VerificationMapper.kt:36-39`), which copies `x, y, width, height`
  straight into `bbox_x/y/w/h` with no transformation.
- **Class-label strings are load-bearing.** `EggSpecies.fromClassLabel` matches canonical names
  and a small alias set (`domain/model/EggSpecies.kt:7-22`). An unrecognised label is preserved
  raw in `class_label` and behaves as a `WRONG_CLASS` candidate — renaming a model class
  silently changes every verdict computation and every EPG grouping.
- The bearer key name. `app/build.gradle.kts:66` reads `INFERENCE_API_KEY_DEV`, while
  `local.properties.example:26` documents `INFERENCE_API_KEY`. Following the example yields an
  empty token and a 401 on every frame (`../../constraints.md` C10).

## Does not hit

- **Persistence.** The inference service is stateless — it never stores an image and never sees
  a sample id (`inference/server.py:34-63`). Nothing about a schema change reaches it.
- **The capture loop's liveness.** A failed inference call is logged and swallowed; capture
  keeps sampling (`core/camera/FrameSampler.kt:72-79`). Only two consecutive `/health` failures
  stop recording — a single `/infer` failure does not.
- **Supabase.** Losing Supabase mid-session does not stop recording. Only losing the inference
  container does.
