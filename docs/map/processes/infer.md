# infer

Asking the model what is in a frame.

**Input** — a sample row saved by capture with `inference_state = 'queued'`.
**Output** — the same row, now `ready` with the model's predictions on it, or `manual`.

**consumes** [`Session`](../objects/Session.md)
**produces** the model output on a sample [`capture`](capture.md) already saved

## The queue

Capture does not wait on a model (14zcqntj6ny). `CaptureFieldUseCase` saves the frame at once as
`FrameSource.MODEL` with `inference_state = 'queued'` and no predictions, wakes the queue, and
returns. The model output arrives later.

- **The queue is the sample rows.** `samples.inference_state` (Room only, added at version 23)
  is `queued` → `in_inference` → `ready`, or `manual`. There is no separate table, so the queue
  survives the app being killed and the phone rebooting.
- **One consumer, oldest first.** `InferenceQueueProcessor` (`domain/inference/`) runs in
  WorkManager passes (`data/inference/queue/`). Every capture and every app launch appends an
  `InferenceQueueWorker` to one unique chain (`APPEND_OR_REPLACE`), so passes run one after
  another, and the processor also holds a lock for the whole pass. Each pass first puts any
  `in_inference` row back to `queued`, which is how a frame interrupted by a killed process, or
  by WorkManager stopping a pass, is picked up again.
- **Background.** WorkManager runs a pass after the app is swiped away or killed, whenever
  Android allows the app background work, and again after a reboot. No network constraint, since
  the on-device model needs none, and never expedited (see [`sync`](sync.md)). On MIUI, background
  work needs the per-app Autostart permission, off by default, the same as sync. When it does not
  run, nothing is lost: the frames wait in Room on the phone for the next capture or app start.
- **Cloud first, then this phone.** Each frame goes to `RemoteInferenceEngine`. On any failure,
  a `503` included, the same frame goes to `OnDeviceInferenceEngine`.
- **Circuit breaker.** `CloudCircuitBreaker` opens after three cloud failures in a row. Frames
  then skip the cloud for 60 s, doubling per failed probe up to 10 minutes, so a dead server
  costs three timeouts rather than one per frame.
- **Both engines fail.** The frame goes back to the queue and waits 30 s, then twice as long each
  time up to 5 minutes. After 5 failed attempts it becomes a manual sample. `samples.inference_attempts`
  holds the count, so a crash loop cannot reset it. Other frames are not held up meanwhile. The
  wait is a separate delayed request, `InferenceRetryWorker`, which appends a pass when the
  earliest failed frame is due, so it never holds later captures behind it.
- **Every transition is one conditional UPDATE** (`SampleDao`, queue section). A result is
  written only if the row is still `in_inference`, so a cancel (`CancelInferenceUseCase`) or a
  delete that lands first makes the result a no-op. A cancelled sample is recorded manual
  (`is_manual = 1`, `inference_model_version = 'manual'`) and never gets a model output.
- **A pending sample is not a clean field.** It has no predictions, like a clean field, so every
  reader checks the state first: `ModelOutput.InProgress` on the Verification Screen,
  `isCleanField` false, and `SubmitVerificationUseCase` refuses it. Counts, the dashboard,
  session egg totals, reports and sync already exclude it, because it is still `flagged`.

## Movement

1. **POST the raw bytes.** The queue hands the JPEG to `RemoteInferenceEngine`, which wraps it
   as an `image/jpeg` request body and calls `InferenceApi.infer`
   (`data/inference/RemoteInferenceEngine.kt:38-40`). No multipart, no base64 — the body *is*
   the image (`data/remote/InferenceApi.kt:23-24`). Transport failures route through
   `NetworkErrorMapper` rather than being wrapped by hand.

   Both engines sit behind the `InferenceEngine` interface (`domain/inference/InferenceEngine.kt`).
   `core/di/InferenceModule.kt` hands them to the queue by type, and there is no unqualified
   `InferenceEngine` binding. See [On-device engine](#on-device-engine).
2. **Authenticate.** An OkHttp interceptor attaches
   `Authorization: Bearer <BuildConfig.INFERENCE_API_KEY>` to every request
   (`core/di/InferenceModule.kt`). The server compares it literally
   (`inference/server.py`). Timeouts are 5 s connect, 30 s read (`core/di/InferenceModule.kt`):
   nobody is waiting on the call, and the on-device model answers when the cloud cannot.
3. **Run the model.** The container loads the weights once at startup
   (`inference/server.py:18-21`) and returns, per box, `class`, `confidence`, and `x, y,
   width, height` from `box.xywh` — **centre-x, centre-y, width, height, in pixels**
   (`inference/server.py:44-57`), plus `model_version` and the image dimensions
   (`inference/server.py:59-63`).
4. **Apply no filter.** The server returns every box the model produced. There is no
   confidence threshold on either side; the human is the threshold
   (`../../constraints.md` C7).
5. **Record empties too.** An empty `predictions` list is still a result a model returned, so
   the row becomes `ready` like any other: a clean field is a normal negative result.
6. **Write it back.** Any answer, detections or none, lands on the row through
   `InferenceQueueRepository.complete`: the predictions as `predictions_json`, the engine's own
   version name (`…-cloud-fp32` or `…-tflite-fp16`), and the image dimensions. The predictions
   are domain `Prediction` values by this point, not the wire DTO —
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
counterpart, and the queue's fallback whenever the cloud fails or the circuit breaker is open.

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
- **The tap.** Capture saves and returns before any model runs. A failed `/infer` call only
  sends the frame to the on-device model. Only two consecutive `/health` failures raise the
  connection-loss banner.
- **Supabase.** Losing Supabase mid-session does not stop recording, and neither does losing
  the inference container.
