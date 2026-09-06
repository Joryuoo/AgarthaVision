# capture

Getting a frame off the microscope and into a state a human can review.

**Input** — a live camera stream and an active session.
**Output** — a `flagged` [`Sample`](../objects/Sample.md) row plus a JPEG on disk, or nothing.

**consumes** [`Session`](../objects/Session.md)
**produces** [`Sample`](../objects/Sample.md) (status `flagged`)

## Movement

1. **Bind the camera.** `CaptureScreen` calls `CameraManager.bindAnalysis` with the
   `FrameSampler` as analyzer (`ui/capture/CaptureScreen.kt:312`). Only `Preview` and
   `ImageAnalysis` are bound — there is **no `ImageCapture` use case**, so there is no shutter
   anywhere in Phase 1 (`core/camera/CameraManager.kt:63-116`). Both use cases are pinned to
   the same 4:3 aspect-ratio strategy so they share one field of view; the analyzer asks for
   640×640 with `FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER`, which prefers a stream at or above
   that size so the frame is only ever downscaled. Backpressure `KEEP_ONLY_LATEST`
   (`core/camera/CameraManager.kt:84-101`).
2. **Cache every frame.** `FrameSampler.analyze` converts the `ImageProxy` to JPEG bytes and
   stores them in `latestFrameBytes` on *every* frame, before any throttling
   (`core/camera/FrameSampler.kt:57-62`). This cache is what manual capture snapshots.
   `toJpegBytes` rotates by `imageInfo.rotationDegrees`, centre-crops to a square, then
   downscales to 640 (`core/util/ImageExtensions.kt:38-83`), so every device posts the same
   geometry. Cropping before scaling is what keeps the image from stretching. A device that
   cannot supply 640 is encoded at its native square size rather than upscaled.
3. **Gate on session state.** If the session is not `Active`, or inference is paused, the frame
   is dropped here (`core/camera/FrameSampler.kt:64`). Pausing is driven by
   `SessionManager.pauseInference()` whenever a sheet or child screen comes forward
   (`core/session/SessionManager.kt:104-109`, `ui/capture/CaptureViewModel.kt:161-163`).
4. **Throttle.** One frame per 2000 ms, and **skip rather than queue** if a request is already
   in flight (`core/camera/FrameSampler.kt:36`, `:66-68`). A slow network reduces the sampling
   rate; it never builds a backlog.
5. **Dispatch to inference** on an IO scope, swallowing failures with a log so capture keeps
   running (`core/camera/FrameSampler.kt:72-79`). What happens next is
   [`infer`](infer.md).
6. **Persist, if flagged.** `PersistFlaggedFrameUseCase` writes the JPEG under
   `filesDir/users/{owner}/samples/{sampleId}.jpg` and inserts a `SampleEntity` with
   `status = flagged` (`domain/usecase/capture/PersistFlaggedFrameUseCase.kt:23-62`,
   `data/local/SampleImageStore.kt:15-20`). Owner is the cached identity or `null`; unowned
   frames go under a literal `local` folder
   (`domain/usecase/capture/PersistFlaggedFrameUseCase.kt:66-67`). The raw predictions are
   cached in `predictions_json`.

## Manual capture — the second entrance

Same output, no model. `CaptureViewModel.onManualCapture` reads the cached
`latestFrameBytes`, builds a `FlaggedFrame` with `source = MANUAL` and empty predictions, and
pushes it through the same store (`ui/capture/CaptureViewModel.kt:129-155`). It requires an
active session and at least one frame already seen; otherwise it sets an error and returns.

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
- Changing the sampling interval changes inference cost directly. GPU droplets bill by the
  second.
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
