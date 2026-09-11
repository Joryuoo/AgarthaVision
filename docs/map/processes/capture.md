# capture

Getting a frame off the microscope and into a state a human can review.

**Input** — a live camera stream and an active session.
**Output** — a `flagged` [`Sample`](../objects/Sample.md) row plus a JPEG on disk, or nothing.

**consumes** [`Session`](../objects/Session.md)
**produces** [`Sample`](../objects/Sample.md) (status `flagged`)

## Movement

1. **Bind the camera.** `CaptureScreen` calls `CameraManager.bindAnalysis` with the
   `FrameSampler` as analyzer. Only `Preview` and `ImageAnalysis` are bound — there is **no
   `ImageCapture` use case**, so there is no hardware shutter (`core/camera/CameraManager.kt`).
   Both use cases are pinned to the same 4:3 aspect-ratio strategy so they share one field of
   view; the analyzer asks for 640×640 with `FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER`, which
   prefers a stream at or above that size so the frame is only ever downscaled. Backpressure
   `KEEP_ONLY_LATEST` (`core/camera/CameraManager.kt`).
2. **Cache every frame.** `FrameSampler.analyze` converts the `ImageProxy` to JPEG bytes and
   stores them in `latestFrameBytes` on *every* frame (`core/camera/FrameSampler.kt`). That is
   all it does now — there is no timer, no session gate, and no auto-dispatch to inference. It
   runs on a single background thread owned by `CameraManager`, not the main one — encoding
   every frame on the UI thread was visible as preview jank.
   `toJpegBytes` rotates by `imageInfo.rotationDegrees`, centre-crops to a square, then
   downscales to 640 (`core/util/ImageExtensions.kt:38-83`), so every device posts the same
   geometry. Cropping before scaling is what keeps the image from stretching. A device that
   cannot supply 640 is encoded at its native square size rather than upscaled.
3. **Wait for the tap.** Capture is medtech-triggered, one frame per field — a fecal smear is
   read by choosing ~10 likely fields, not by sweeping the slide continuously, so the old
   2-second timer was removed. The shutter calls `CaptureViewModel.onCapture`, which snapshots
   `latestFrameBytes` and hands it to `CaptureFieldUseCase` (`ui/capture/CaptureViewModel.kt`,
   `domain/usecase/capture/CaptureFieldUseCase.kt`). No active session, or no frame cached yet,
   sets an error and returns without capturing.
4. **Infer once, then record whatever the server said.** `CaptureFieldUseCase` calls the
   injected `InferenceEngine` (bound to the cloud `RemoteInferenceEngine`) a single time — what
   happens inside is [`infer`](infer.md).
   Capture source is decided by whether the server was *consulted*, not by what it found: any
   response — **including zero detections** — builds a `FrameSource.MODEL` frame (JPEG,
   predictions, model version, image dimensions). A clean field is a normal negative result and
   must be recorded, not discarded. An `InferenceConnectionException` (container unreachable)
   builds a `FrameSource.MANUAL` frame with no predictions, so a lost connection is a Manual
   Capture rather than a silent failure.
   (`domain/usecase/capture/CaptureFieldUseCase.kt`).
5. **Persist.** A recorded frame (AI Capture or Manual Capture) goes to `FlaggedFrameStore.add`, which runs
   `PersistFlaggedFrameUseCase`: it writes the JPEG under
   `filesDir/users/{owner}/samples/{sampleId}.jpg` and inserts a `SampleEntity` with
   `status = flagged` (`data/repository/FlaggedFrameStore.kt:77-79`,
   `domain/usecase/capture/PersistFlaggedFrameUseCase.kt:23-62`,
   `data/local/SampleImageStore.kt:15-20`). Owner is the cached identity or `null`; unowned
   frames go under a literal `local` folder
   (`domain/usecase/capture/PersistFlaggedFrameUseCase.kt:66-67`). The raw predictions are
   cached in `predictions_json`.

## One entrance, two outcomes

There is no longer a separate no-model entrance. Every tap runs inference, and whether the
server was reached decides what is recorded: any server response — zero detections included —
is an **AI Capture** (`FrameSource.MODEL`); an `InferenceConnectionException` is a **Manual
Capture** (`FrameSource.MANUAL`). A clean field is recorded like any other AI Capture, since a
negative result is still a result. The `InferenceConnectionException` the call already throws on
transport failure *is* the AI-vs-Manual classifier — there is no separate timeout or signal.
`SubmitManualCaptureUseCase` remains the pattern for turning a recorded frame into a verified
sample downstream. Aggregating clean fields as an LPF-density denominator is a separate concern
(86d4a6jxw).

## Discarding

A flagged frame can be dropped before verification: the JPEG is deleted and the row removed
(`domain/usecase/capture/DeleteFlaggedSampleUseCase.kt:11-15`,
`data/repository/FlaggedFrameStore.kt:80-84`). This is the only deletion the system permits —
nothing verified is deletable (`../../constraints.md` C8).

## Hits

- **The flagged queue is Room-backed, not in-memory.** `FlaggedFrameStore` observes
  `samples WHERE status = 'flagged'` and rebuilds `FlaggedFrame` objects, re-reading the JPEG
  off disk each time (`data/repository/FlaggedFrameStore.kt:58-74`, `:101-119`). Any older
  claim that flagged frames are transient and lost on process death is stale.
- **The queue is invisible to a never-signed-in device.** The observing flow returns an empty
  list when the cached identity is null (`data/repository/FlaggedFrameStore.kt:64-71`), and
  every flagged DAO query filters on `user_id` (`data/local/dao/SampleDao.kt:69`, `:78`). Frames
  captured with no cached identity are written to Room but never appear in the queue — a real
  gap against the offline-first intent, not a design decision.
- Inference now runs once per shutter tap, not on a timer. A field costs exactly one
  inference call; there is no idle sampling burning the GPU droplet between taps.
- Changing the analysis resolution changes the coordinate space of every bounding box, since
  the server returns pixel coordinates.
- Changing the crop, the aspect-ratio strategy, or `PreviewView.scaleType` breaks
  `CaptureFrameBoundary`. The brackets it draws are only truthful because preview and analysis
  share a field of view and the preview is `FIT_CENTER` — under `FILL_CENTER` the analysed
  region is wider than the display and no on-screen box can mark it
  (`ui/components/CaptureFrameBoundary.kt`, `ui/components/MicroscopyViewport.kt:46-49`).

## Does not hit

- **Sync.** Nothing in capture talks to Supabase. A flagged sample is purely local until
  [`validate`](validate.md) submits it.
- **EPG or reports.** Every aggregate query excludes `status = 'flagged'`
  (`data/local/dao/DetectionDao.kt:41`, `data/local/dao/SampleDao.kt:51`). An unverified frame
  counts for nothing — that is `../../constraints.md` C7 enforced structurally.
- **Detection rows.** None exist yet. Predictions live only as cached JSON on the sample until
  a human rules on them.
