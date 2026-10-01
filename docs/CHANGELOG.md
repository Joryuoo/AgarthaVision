# Changelog

Reconstructed from `git log`. **This project has never cut a release** — there are no tags,
no `versionName` bump beyond the initial `0.1.0-mvp` (`app/build.gradle.kts:37`), and no
release branch. What follows is grouped by the work that actually landed, dated from the
commits themselves. Nothing here is invented.

Verify any entry with `git log --oneline --reverse`.

---

## feat/change-password — change password in Settings · 2026-10-01

`14zcqntjph9`.

- **Settings → Change password.** Current password, new, confirm, each with show/hide. Empty
  fields, a mismatched confirmation and an unchanged password are caught on the phone.
- **Online only.** Offline the screen says it needs a connection and the button is disabled; a
  connection lost mid-request says the password was not changed.
- **The current password is checked first**, by signing in with it again, so a wrong one is
  refused on its field before anything changes. The provider's strength rules, and its own
  explanation, land under the new password.
- **This phone stays signed in.** Local data, the cached identity and unsynced work are
  untouched. Supabase signs out the account's other sessions; they need the new password.

## docs/sprint-2-alignment — the shelf matches the code again · 2026-09-29

`14zcqntjg5f`. Docs only; no code changed.

An audit of `development` @ `feaa4803` found the shelf's structure sound and its content
drifting: most Kotlin line citations pointed at the wrong code after later edits.

- **Citations by symbol.** Every Kotlin, Gradle, Python, TOML and `schema.ts` citation is now
  `path::symbol`, never a line number. Only SQL migrations keep `path:line`, since an applied
  migration never changes. Every legacy migration reference carries its `legacy-dev/` prefix.
- **Card frontmatter.** Every card under `docs/map/` opens with `type`, `status`
  (`stub` / `verified` / `stale`), `verified` and `commit`. A PR that touches cited code updates
  the card or marks it stale (`docs/CONTEXT.md` house rule 5).
- **Four new process cards:** `sign-in`, `register-patient`, `session-lifecycle`,
  `delete-sample`, plus effects rows for sign-in, patients, sessions and the Home dashboard.
- **Drift fixed**, among others: `validate.md` rewritten for the checkbox questions, species-first
  counting and the pending-inference lock; the Finding card's `stage` is live, not dormant;
  report files do reach the `reports` bucket; C2, C4, C5 and C6 restated as the code has them;
  the session label format; the screen list; the `samples` Storage policies re-cited to the
  consolidated schema.
- **Code findings recorded, not fixed:** a tombstone never reaches Postgres (`SampleInsertRow`
  has no `deleted_at`); the Sessions list egg total counts only `confirmed` while the report
  counts every non-false-positive; four ViewModels call repositories directly (C1).
- **`connectedAndroidTest` is a non-negotiable now:** it uninstalls the app and wipes unsynced
  samples. `AGENTS.example.md` gains a tool-agnostic "keep the shelf current" section.

## feat/server-micro-batching — the inference server queues, batches and uses both GPUs · 2026-09-27

`14zcqntj6p2`.

`/infer` used to run the model on the event loop. Every inference blocked uvicorn, `/health` hung
behind it, nothing bounded the pile-up, and the second T4 sat idle.

- **A bounded queue** (`QUEUE_SIZE`, 32) sits in front of the model. When it is full, the server
  answers `503` with `Retry-After` at once. A request that waits longer than `REQUEST_TIMEOUT_S`
  (25 s, under the app's 30 s read timeout and Cloudflare's ~100 s) gets a `503` too.
- **Micro-batching:** a worker takes up to `MAX_BATCH_SIZE` (8) frames, waiting at most
  `MAX_QUEUE_DELAY_MS` (15) for companions, and runs them as one forward pass.
- **One worker per GPU**, each with its own model loaded and warmed up at start-up, all pulling
  from the same queue.
- JPEG decoding and the forward pass run off the event loop, so **`/health` answers during
  inference**.
- **The contract is unchanged:** the same endpoints, auth and response body. An unreadable body
  is now a `400`, not a `500`. `X-Inference-Batch-Size` and `X-Inference-Device` headers make
  batching visible from outside.
- `tests/` covers all of the above against a fake model: 10 tests, no GPU needed. The notebook's
  `%%writefile` cell is regenerated from `server.py`, and its GPU cell lists every device.

## feat/verify-pending-inference — a pending sample says so, and the medtech can stop waiting · 2026-09-27

`14zcqntj6p1`.

- **Pending is visible.** While a sample waits on inference, its Model Output section shows a
  spinner and "Queued for inference", or "Frame is in inference" once it is the frame being
  run: the same split as the queue's badge. Read off the frame's own `InferenceState`. There is no
  second flag. `isResolving` keeps its own meaning (a frame still being fetched), because
  merging the two would conflate a frame with no model output yet and a frame with no image
  yet.
- **Annotation is locked meanwhile.** Add species, remarks and Submit are disabled, and the view
  model ignores those inputs too. The model pre-fills the species, so anything entered before
  it lands would collide with it.
- **Cancel inference, confirmed.** The dialog says the sample will never get a model output.
  Confirming calls `CancelInferenceUseCase`, the frame switches to manual in place, and it can
  be annotated at once. Dismissing leaves it pending. When the result beats the tap, there is
  nothing to cancel and the result shows.
- **The result updates in place.** When the store re-emits the open frame with its output,
  the section, the boxes and the pre-filled answers appear without leaving the screen. This
  only happens while the frame is pending, so a medtech's answers are never overwritten.
- **#78 still holds.** Nothing can be edited while pending, so leaving never asks then.
  Cancelling is not an edit. Edits after a cancel are guarded as usual.
- The "no model output" copy now covers a cancelled frame and one the queue gave up on, not
  only an unreachable server.

---

## feat/queue-inference-badges — every queue row says where its model output is · 2026-09-27

`14zcqntj6p0`.

Each Verification Queue row's badge now shows the sample's inference state: **Queued**, **In
inference**, **Ready** or **Manual**. It replaces the "AI-suggested" / "Manual" pill, which could
only say whether a model was ever involved.

- Four states, not two. Frames run one at a time and the on-device path takes seconds each, so
  labelling every waiting frame "In inference" would misstate what the system is doing.
- The label is always drawn, and the tone only reinforces it: neutral for queued, gold for in
  inference, the accent tint for ready (as AI rows had) and the warning tint for manual (as
  manual rows had). All come from theme tokens.
- It updates live. The queue is a Room query and `QueueSample` compares by value, so a row
  moves Queued → In inference → Ready without leaving the screen.
- Every row stays tappable, pending ones included.

---

## feat/instant-capture — the shutter confirms at once and never spins · 2026-09-27

`14zcqntj6nz`.

A tap saves the frame and shows "Frame captured" immediately. The model output comes later from
the background inference queue, so there is nothing left for the shutter to wait on.

- **No busy state.** `CaptureState.isBusy` is gone, and with it the shutter's dimmed
  travelling arc, the "Capturing sample" description, the back button's disabled state and the
  system-back lock. The medtech can capture field after field without pausing.
- **A double tap saves one sample.** The busy state was also what stopped a second tap from
  saving the same cached frame again. `CaptureViewModel` now saves each cached frame once, by
  identity; the next tap takes the analyzer's next frame. A failed save can be retried.
- **Taps less than 750 ms apart save once (2026-09-29).** Identity alone was not enough: the
  analyzer replaces the frame every ~33 ms, so a fast double tap lands on a new, near-identical
  frame, and rapid tapping saved 9 samples of one field in 1.25 s on a Redmi Note 11. The
  cooldown drops those taps. Moving to the next field takes far longer than 750 ms.
- **The save still survives leaving the screen.** It runs under `NonCancellable`, so a medtech
  who leaves in the milliseconds it takes loses only the confirmation, not the frame.
- **One confirmation for every tap.** `CaptureOutcome` no longer carries a source. Whether a
  model answers is decided later by the queue, so "Frame captured · model unavailable" is gone.
  The toast's action still opens the exact sample that tap wrote.
- `ConnectionLossBanner` is unchanged. Whether it still earns its place now that the phone can
  answer offline is an open question for Tabada.

---

## feat/background-inference-queue — capture saves at once, and the model answers later · 2026-09-27

`14zcqntj6ny`.

**Capture waits on nothing.** `CaptureFieldUseCase` saves the frame as `queued` and returns. It
used to run inference first, so every tap waited on the network (up to 40 s against a dead
server), and an unreachable server turned the frame into a Manual Capture that could never get a
model output.

**A queue in Room, with one consumer.** `samples.inference_state` holds `queued` →
`in_inference` → `ready`, or `manual`. `InferenceQueueProcessor` takes one frame at a time,
oldest first: the cloud first, the on-device model when the cloud fails for any reason, a `503`
included. `CloudCircuitBreaker` skips the cloud for 60 s after three failures in a row, doubling
per failed probe up to 10 minutes. It runs through WorkManager (`WorkManagerInferenceQueue`), like
sync: each capture and each app launch appends an `InferenceQueueWorker` pass to one unique chain,
so passes never overlap, and `InferenceRetryWorker` wakes the queue when a failed frame is due. A
frame interrupted by a killed process is put back in the queue on the next pass. The queue keeps
going after the app is swiped away, whenever Android allows it background work; on MIUI that needs
Autostart, as sync does. The queue itself is the rows in Room, on the phone.

**When both engines fail**, the frame waits 30 s, then twice as long each time up to 5 minutes,
and becomes manual after 5 attempts (`samples.inference_attempts`). Other frames keep moving.

**Cancel is atomic and final.** Every transition is one conditional UPDATE. A result is written
only if the row is still `in_inference`, so a cancel or delete that lands first discards it.
`CancelInferenceUseCase` records the sample manual (`inference_model_version = 'manual'`). The
Verification Screen's UI for it is a separate ticket.

**A pending sample is never a clean field.** It shows as `ModelOutput.InProgress`,
`isCleanField` is false, and `SubmitVerificationUseCase` refuses it. Counts, reports, the dashboard
and sync already skip it because it is still `flagged`.

**Room 23, the first hand-written migration.** `MIGRATION_22_23` adds the two columns and marks
existing manual rows `manual`. From here the phone holds frames that exist nowhere else, so every
bump ships a `Migration` (C6). Destructive fallback stays only for installs older than 22.

**Connect timeout 10 s → 5 s.** Nobody waits on the call now, and the phone answers when the
cloud cannot.

**The connection-loss banner no longer says captures become Manual.** It reads "Cloud model
unreachable · Captures are still recorded. This phone reads them instead, more slowly."

---

## feat/offline-inference-engine — the on-device model is back, and it agrees with the cloud · 2026-09-27

`14zcqntj6nw` with `14zcqntj6nx`.

**Inference versions name the engine and the precision.** `<arch>-v<training-version>-<target>-<precision>`:
the cloud reports `yolo12n-effv2s-v1-cloud-fp32` (`server.py`, the notebook), on-device results
`yolo12n-effv2s-v1-tflite-fp16` or `-int8`. The old `yolov26-efficientnetv2-v1` named the wrong
architecture: `best.pt` was trained from `effnet-v12.yaml`, a YOLOv12-nano neck on EfficientNetV2-S,
and its head is not NMS-free.

**The export tooling is ported from `feat/offline-inference` and fixed.** Two faults behind the
2026-09-06 deferral were export faults, not the phone's:

- The "fp16" file was byte-identical to fp32 (78 MB). onnx2tf 2.x also emits a model that is fp16
  end to end, which LiteRT's CPU kernels reject. `export_mobile.py` now drives onnx2tf's
  `tf_converter` backend and TensorFlow's converter itself, and refuses a build whose dtypes,
  I/O or box geometry do not match its name. fp16 is 39.4 MB, int8 22.8 MB.
- The TFLite head emits box geometry normalised to 0..1, and the old decoder read it as pixels:
  the "zero boxes matched" result. Manifests now say `box_coordinates: normalized`.

Both builds ship in `app/src/main/assets/models/` through Git LFS, each with a manifest named after
its version. Model artifacts are no longer ignored there; capture fixtures still are.

**`OnDeviceInferenceEngine`** (`data/inference/ondevice/`), on LiteRT 2.2's `CompiledModel` API.
It compiles once, GPU first with CPU placement for unsupported ops, and runs on one thread.
Preprocessing is Ultralytics' letterbox, and decoding runs NMS with the container's defaults. A
missing or refused model reports unavailable instead of crashing. It is provided but not routed
to yet: capture still calls the cloud only.

Measured on a Redmi Note 11 against the cloud model's answers for the same 20 capture-shaped
frames (`OnDeviceInferenceParityTest`):

| | fp16 | int8 |
|---|---|---|
| Matched / missed / invented | 20 / 0 / 0 | 20 / 0 / 0 |
| Mean IoU, mean confidence delta | 0.994, 0.0007 | 0.918, 0.027 |
| Infer median (p90) | 4,885 ms (4,904) | 4,836 ms (4,856) |

All 829 ops run on the GPU in one partition, against 20.8 s per frame on CPU last time. int8 buys
no speed on the GPU and costs box accuracy, so fp16 is `OnDeviceModels.SHIPPED`. On the desktop,
fp16 matched all 607 of `best.pt`'s detections across the 600 val images (mean IoU 0.993).

**Build:** `android.uniquePackageNames=false`, because `litert` and `litert-api` share a namespace
that AGP 9 rejects ([LiteRT#6965](https://github.com/google-ai-edge/LiteRT/issues/6965)).

**2026-09-28: new weights, YOLO26-nano on EfficientNetV2-B0.** `best.pt` is replaced by
`inference/weights/yolo26n-efficientnetv2b0.pt` (val mAP50-95 0.913), which the cloud and the phone
both run. `yolo26n-mobilenetv4convsmall.pt` (0.901) is committed beside it as the next on-device
candidate. Versions are now `yolo26n-effv2b0-v1-cloud-fp32` and `yolo26n-effv2b0-v1-tflite-fp32`.
The tables above measure the old model.

- **Fork pin `c2b1563` → `1fd2043`** (`feat/optimized-inference`) in `requirements.txt`, the export
  requirements and the notebook. The old pin has no `TimmBackbone` and cannot load either file.
- **The phone ships fp32**, 26.9 MB, to see whether it runs the B0 model at all before trading
  accuracy for size. The old fp16 and int8 builds are removed. `export_mobile.py` now also writes
  the fp32 build, and patches `TimmBackbone` instead of `EfficientNetV2Backbone`.
- **Output layout unchanged.** These heads were trained with YOLO26's one-to-one branch off, so
  the TFLite output is still `[1, 7, 8400]` normalised, and the decoder and its NMS stand as they are.
- **Parity on the desktop**, 20 capture-shaped frames: fp32 matched 22 of 22 of the PyTorch
  checkpoint's detections at IoU 1.0000; fp16 22 of 22 at 0.9972. Not yet measured on the phone.

---

## feat/home-and-ui-redesign — offline province/town boundary geometry · 2026-09-27

`14zcqntj3bv` (Phase 8 of 10). No boundary data existed anywhere in the repo — the on-device
map planned for later phases needs province/town geometry keyed on the same PSGC codes the
barangay picker already ships. `tools/geo/` reverses the Negros Island Region and Sulu
renumbering to join a 2023-vintage shapefile onto the current q2_2026 PSGC dataset
(1,642/1,642 towns, 85 province-level units including the NCR pseudo-province), and packs the
result into two custom quantized binary assets well inside their 200 KB/900 KB budgets. New
pure-Kotlin `domain/geo` primitives (projection, hit-testing, view-fit, choropleth binning) and
a `BoundaryRepository` read them — no Room table, no schema change, no map UI yet.

---

## fix/detection-box-provenance — the model's output is stored, and a box says who drew it · 2026-09-24

`14zcqnthrx6` with `14zcqnthrx8`.

**A rejected box is no longer stored as where the egg is.** A `BOX_INCORRECT` row the medtech did
not redraw used to fall back to the model's geometry, so it passed the exhaustiveness rule
(`bbox_x is null`) and a frame carrying a box a human called misplaced qualified for background
sampling. It is now written with no box; a redraw still writes the medtech's.

**New `predictions` table** (`0004_predictions.sql`) — the model's raw output, one immutable row
per box, pushed in the same sync call as its sample and only for verified samples.
`detections.prediction_id` links each ruling to its claim; null means an egg the medtech added.
With the fix above, every linked row answers who drew its box by itself, so the reopen path's
half-pixel geometry comparison is gone. The table needs no Room mirror: the device keeps
`predictions_json`, rows are built from it on push and folded back into it on pull.

**`detections.species_touched` and `detections.verified_by_user` are dropped** (`0006`, Room
version 22). `species_touched` recorded taps, not judgements — a medtech who read a pre-filled
row and agreed submitted it untouched — and every row it marked for a real reason was already
marked by its verdict or a null `prediction_id`. `verified_by_user` had been gone from Postgres
since legacy `0002` and was hardcoded `true` in Room. Version 21 is skipped: `development`
briefly carried a `21.json` of another shape. The bump is destructive, so devices must sync
before installing.

**Found on the way:** every pull overwrote the capturing device's own `predictions_json` with
null, because the server's sample row has none. A pull now keeps the device's copy, and restores
it from `predictions` on a device that never had one — so a sample reopened elsewhere draws the
model's real boxes. The fallback that rebuilds predictions from detection rows now stops at a
row with no box instead of skipping it, which would have shifted every later ordinal.

**Deploy order:** apply `0004` before this build syncs. The backfill recomputes the derived ids in SQL and
finds 8 legacy prediction-backed detections across 4 samples; it links the 5 on the 3 samples
with no `BOX_INCORRECT` row. The fourth sample is left unlinked, because whether its box was
redrawn cannot be recovered from the server. Apply `0006` only once no device runs an older build:
those still send `species_touched`, and PostgREST rejects a payload naming a dropped column.

---

## refactor/never-ending-sessions — sessions stay open, the queue holds everything · 2026-09-14

`86d4ab4vm` with `86d4ab4tq` and `86d4ad75y`. The session
lifecycle, the verification queue model, and what "delete" means — from the 2026-09-07
consultation with Dr. Bayron.

**A session no longer ends.** One session is one fecal smear and the medtech keeps coming back
to it, so `stopSession` is gone and nothing writes `sessions.ended_at`. The column stays
nullable and `SessionRemoteDataSource.closeSession` stays with it, because sessions closed
before this are real history; `resumeSession` still refuses to reopen one. Three things fell out
of it: sign-out now detaches (`SessionManager.clearActive`) instead of being blocked forever,
`NetworkMonitor` polls `/health` only while a capture screen is mounted instead of forever, and
the active session id is persisted and restored at launch — without which a process restart
would come back idle with a smear still open and render the queue empty.

**The queue is the whole session, in two buckets.** Verified and Unverified; the AI/Manual split
was dropped as unnecessary (revised 2026-09-12). Verified samples stay visible and reopen with
the medtech's own previous answers, so an edit is a correction rather than a re-review. New
`QueueSample` carries no `ByteArray` — the old row rendered from bytes re-read off disk on every
emission, survivable while the queue drained and an OOM risk once it only grows.

**Delete replaces the repeat flag.** Long-press for batch select; an unverified frame is
hard-deleted, a verified sample is tombstoned via `samples.deleted_at`. `is_repeat` existed only
because deletion was impossible, and it is removed entirely — including from the report CSV,
which loses a column. The confirmation dialog states the split, because the two halves are
irreversible in different ways.

**One verification screen.** `ManualSheet`, `ManualCaptureViewModel` and
`SubmitManualCaptureUseCase` are deleted (~1,150 lines). A manual capture is just a frame with
no model output: no box means no box questions. Polyparasitism lands with it —
`sample_species_findings` records several species and stages per frame with per-species counts,
because WHO thresholds are species-specific and a combined per-field count cannot be graded.

**Schema:** Room 9 → 12 (skipping two contested numbers), migrations `0012` and `0013`, and
`AgarthaDatabaseSchemaTest` pinning the result so the collision that nearly shipped in September
cannot recur silently.

Two hazards worth knowing about, both fixed before they shipped: detection ids were random, so
re-saving an edited sample would have appended a second full set and doubled every egg count
with no error anywhere; and `updateSampleOnVerify` nulled `predictions_json`, which would have
left a reopened sample with no boxes to draw.

**Merged `staging` in on 2026-09-14**, which changed three of the decisions above.

- The branch had cherry-picked `86d4a6jwy` (the egg-stage dropdown) before staging reverted it.
  The merge did not raise that as a conflict — this branch had edited lines next to the reverted
  ones, so git kept both sides — so the revert is **re-applied here by hand**: no `EggStage`, no
  `detections.stage`, no stage in the CSV, and nothing in the UI asks for one.
  `sample_species_findings.stage` stays as a dormant, always-null column, because `0012` is
  applied and frozen under C6. Findings are keyed per species, not per species-and-stage.
- The silent species pre-fill is **replaced by 86d4auj84's explicit confirm step**, which does
  the same job better: the sheet asks "Is this egg *Ascaris lumbricoides*?" rather than filling
  the answer in and hoping the medtech notices. `detections.species_touched` stays and is now
  true on every row by construction — kept, and asserted, because a future path that writes a
  species without asking would show up as a false rather than entering the corpus unremarked.
- Two guards in the merged-in sheet were corrected. The question chain stopped at a misplaced
  box, which drops a real countable egg from the per-species count and leaves the frame
  unsubmittable, since `Finding.isComplete` asks for a species on a `BOX_INCORRECT` row; and
  `onSpeciesConfirmed` read its suggestion from `frame.predictions[currentDetectionIndex]`,
  which walks off the end of the list once a medtech appends a species the model never boxed.

The Sessions list needed the same treatment. `86d4aprzc`'s date filter and pagination exempt
"active" sessions from the filter as `ended_at IS NULL` — which matched every session once
sessions stopped ending, so the filter would have matched everything while still looking right.
The exemption is now pinned to the one active session id, and the header counts frames awaiting
review instead of open sessions.

## feature/editable-report — verification sheet layout · 2026-09-13

- The frame preview on both sheets fills the width between the side margins at the frame's
  own aspect ratio (`previewAspectRatio()`, `ui/verify/ModalSheetComponents.kt`) instead of a
  fixed-height strip that letterboxed a square field. Previous/Next frame moved beneath it
  (`FrameNavRow`).
- Submit is the brand maroon with white text; Discard is a neutral grey chip. It opens a
  confirmation dialog, so it no longer needs to look destructive itself (`SheetActionRow`).
- "Note" is now "Remarks" on both sheets, as a plain labelled field — the manual sheet's
  enclosing card is gone. Labels and the top-bar meta line dropped the platform monospace
  face for the app's Inter styles (`SheetSectionLabel`, `MonoSmallStyle`), matching the records
  screens.
- The AI sheet's species line is now a card — "SPECIES" small, the model's class large,
  position and provenance pill inside — followed on model frames by a caution that the result
  is AI-suggested and may be inaccurate (`DetectionCard`, `ui/verify/VerificationSheet.kt`).
  Manual frames get the card without the caution. The old "Prev/Next detection" pills became
  full-width "Previous egg / Next egg" buttons under the card, shown only when the frame has
  more than one box, each side dead at its end of the range. The Boxes toggle moved up to sit
  directly above the first question, which is about the highlighted box.
- The species step now asks "Is this egg *Ascaris lumbricoides*?" before offering a list. Yes
  records the model's species as the answer in one tap; only a no opens the "Which species is
  it?" picker, and a class the app cannot map to a species skips straight to it
  (`VerificationAnswers.speciesConfirmed`, `VerificationViewModel.onSpeciesConfirmed`).
- Reverted the egg-stage classification (86d4a6jwy, deprioritised): Room back to staging's v10,
  `detections.stage` and its migration gone. The species dropdown keeps type-to-search.

## feat/86d4ab4xr-sample-geospatial — PSGC barangay on sessions · 2026-09-11

Cut from `staging`. Serves the 4th general objective (DOH-compliant surveillance reports and
geospatial maps) and the SRS adjustment "samples should have location to allow for geospatial
mapping". The map itself belongs to the Admin Website (a separate project); this is the app's
share — the schema, the dataset and the picker.

**The GPS fix was never the answer.** Every sample has carried one since migration `0001` and
nothing has ever read it. It is taken at the moment of capture — the medtech at the microscope
— so it records where the smear was *read*, not where the infection came from; plotted, it
maps laboratories. It stays as audit provenance. Sessions now carry the patient's barangay
instead, which is also the unit STH surveillance actually decides on: prevalence per
administrative unit against the WHO 10% / 20% thresholds, never individual pins.

**Schema.** `sessions.psgc_barangay_code`, nullable, one column — a barangay code resolves
upward to city/municipality, province and region by itself. Stored as the canonical
zero-padded 10-digit PSGC with a `CHECK` to match, which is what keeps the Admin Website's
boundary join from silently missing every unit in regions 01–09. Room 8 → 9.

**Aggregation is an RPC, not a view,** because access has to be decided per row-owner and
`GRANT` cannot tell an admin from a medtech — both hold `authenticated`. It counts smears
rather than samples, and withholds figures below a minimum cell size: a barangay with one
smear is effectively an identified patient. PostGIS stays off; with PSGC as the key the
choropleth is a `GROUP BY`.

**The dataset ships in the APK** (42,010 barangays, 342 KB gzipped) and is Room-seeded on
first run, because medtechs collect where there is no signal. Pinned to **PSGC 2Q 2026**,
PSA's current release, and the pin is load-bearing: the boundary GeoJSON the admin map will
render has to join on the same vintage or it fails silently for the units that moved.

**The first cut of this was built at 4Q 2023 and was wrong by 1,763 barangays** — 4.2% of the
country. Its upstream stopped publishing in September 2024 and missed two reorganisations:
the Negros Island Region (RA 12000, 2Q 2024) took Negros Occidental, Negros Oriental,
Siquijor and Bacolod out of Regions VI and VII into region `18`, and Sulu left BARMM for
Region IX after the Supreme Court ruling. Both roll up to the *region* a surveillance map
aggregates on, and `sessions.psgc_barangay_code` is written once with nothing to backfill it.
Two popular alternatives, `psgc.gitlab.io` and `psgc.cloud`, are 9-digit and also pre-NIR;
9-digit is a different code system and does not join at all. Changing the vintage is a
dataset swap plus four constants: the seeder re-seeds when `PsgcDataset.VINTAGE` changes.

**Two things the real data forced.** Searching the dataset showed that matching the query as
one string returns nothing for "cebu city" — PSA spells it "City of Cebu" — so search matches
each term independently, which also narrows "lahug cebu" to a single barangay. And Manila's
14 sub-municipalities turn out to live in the *barangay* file as parents of its 897
barangays; they are rolled up to the chartered city for display and kept searchable.

**Conventions recorded:** the vintage pin and the aggregate-before-display privacy rule, both
in the new `docs/map/objects/PsgcBarangay.md` — the seventh object card, and the only one
with no Supabase table.

---

## fix/framesampler-stale-cache — camera frames carry a freshness stamp · 2026-09-11

`86d4au2n1`. A shutter tap landing before the analyzer delivered a frame for the *current*
camera binding recorded the **previous session's image** under the current `sessionId`.
`FrameSampler` is `@Singleton`, its cache was never reset, and `CaptureViewModel.onCapture`
guarded only against `null` — a stale array is not null, so the guard passed and the frame was
persisted. Reachable on a second session in one process, on re-entering the capture screen, and
on any camera rebind. In this app a session is a patient, and C8 makes a misattributed frame
permanent once it is verified.

- `FrameSampler` now publishes `CachedFrame(jpegBytes, elapsedRealtimeMs)` from `latestFrame`
  (was `latestFrameBytes`), stamped as the frame is encoded.
- `CaptureViewModel.onCapture` rejects anything older than `MAX_FRAME_AGE_MS` (1 s) with the
  existing "Waiting for a live frame" message. A bound analyzer delivers ~30 fps, so a live
  frame is never more than ~33 ms old; every stale path leaves a far longer gap. Fails closed
  with no lifecycle wiring to maintain, which is why this is a stamp and not a reset call.
- New `core/util/ElapsedClock.kt`, bound unscoped in `core/di/ClockModule.kt`. A seam over
  `SystemClock.elapsedRealtime()` so both classes stay unit-testable on the plain JVM —
  `app/build.gradle.kts` does not set `returnDefaultValues`. Unscoped keeps C5's `@Singleton`
  list closed.

`FrameSampler` gained no session knowledge; it remains a camera-layer component.

---

## feat/build-optimization — cloud-only inference, bug fixes for validation · 2026-09-06

Cut from `staging`. Carries forward the documentation shelf, the admin storage policy fix,
the offline-access fixes and the tooling templates, and leaves on-device inference behind.

**Mobile compute deferred.** An on-device TFLite path was built and benchmarked on a Redmi
Note 11 over 14 real capture frames, 3 iterations, 42 frames per engine, 0 failures:

- Device infer p50 **20,800 ms** (p95 21,804, max 29,389) against cloud end-to-end p50
  **2,784 ms** — 7.5x slower, and 10x slower than the 2-second capture cadence the app
  samples at. The GPU delegate never ran: TFLite built it, rejected the graph over a
  dynamic-sized tensor, and fell back to XNNPACK CPU, so that is the CPU ceiling. The
  exported artifact is 78 MB against the 40-45 MB the export README predicted.
- 0.00% recall against cloud with **zero boxes matched** at IoU 0.5, while detection counts
  agreed on 13 of 14 frames including the negative. That pattern is a coordinate-decode
  fault, not a weak model — fixable, and irrelevant, because fixing geometry does not fix
  21 seconds.

The engine, selector, benchmark harness and Python export tooling are preserved on
`feat/offline-inference`. Rationale and next steps are in the vault note
`offline-inference-deferred.md`.

**Kept from that branch, because neither needs TFLite:**

- The domain inference abstraction — `domain/inference/` types, and `FlaggedFrame` now holds
  domain `Prediction` values instead of the wire `PredictionDto`, so `domain/` no longer
  imports a response type.
- `RemoteInferenceEngine`, now the cloud call path. `InferFrameUseCase` routes through it
  rather than reaching into `InferenceApi` itself (C1), transport errors go through
  `NetworkErrorMapper`, and the container's `inference_ms` reaches the app so cloud compute
  is separable from network time.

**Also in this branch:**

- Fixed: the Settings screen was unreachable — the route was registered but no tab or
  affordance pointed at it. The bottom bar now carries a fourth tab, and tab labels moved
  from hardcoded literals to `strings.xml`.
- Fixed: C11 and `non-negotiables.md` claimed "no icon library" while
  `material-icons-extended` has been a declared dependency and is used in four screens.
- Fixed: `features.md` claimed the bottom bar shows on every screen but Login and Capture;
  it is gated on `bottomBarRoutes`, so drill-downs hide it too.
- Restored the ignore rules for model artifacts and capture fixtures, which had arrived with
  the dropped on-device commits.

**Field-test fixes · 2026-09-06.** Four issues from a session on real hardware, plus the two
latent defects they exposed. Remaining findings are logged in the vault, not here.

- Fixed: the verification sheet's frame counter was stuck at `1/144` while Next/Previous
  changed the image underneath. `setFrame` never recomputed `frameIndexInQueue` — only the
  store collector did, and paging does not make the store re-emit. Both paths now share one
  helper, and the frame buttons dim at the ends of the queue.
- Fixed: the toast rendered top-center over the verification queue button. That button and its
  badge moved to the bottom-left slot, replacing a `FRAMES` pill that counted the same value.
- Fixed: the JPEG posted for inference had device-dependent geometry, was never rotated out of
  sensor orientation, and had no on-screen boundary. Preview and analysis now share a 4:3
  field of view, frames are rotated, centre-cropped square and downscaled to a uniform 640,
  and `CaptureFrameBoundary` marks the captured region. The preview moved to `FIT_CENTER`, so
  it is no longer edge-to-edge — under `FILL_CENTER` the analysed region is wider than the
  screen and no boundary could be honest.
- Fixed: detection toasts were verbose, queued behind one another, and could not be dismissed.
  Copy is now `{species} detected` through `EggSpecies.fromClassLabel`, they last 2s, a new one
  replaces the old, and either swipe dismisses. Manual capture gained a toast at all.
- Fixed: sharing a report meant leaving the app for a file manager. The generation snackbar
  carries a `Share` action, and reports moved from the Downloads root to
  `Documents/AgarthaVision/` — through `MediaStore` on API 29+, which is the only way to make
  a folder in shared storage. `shareReportCsv` handles both the `content://` and path shapes
  and now reports failures instead of returning silently.

**Second defect pass · 2026-09-07.** Six latent defects found while fixing the field-test
issues, plus ten items from a second round of testing.

*Correctness.*

- Fixed: marking a frame as repeat left the verification queue's Repeat filter and chip counts
  stale. `FlaggedFrame.equals` compared `sampleId` alone, so the Room re-emission compared equal
  to the list already held and `StateFlow` conflated it away. Equality now covers every property
  except `jpegBytes`, which must stay out — the store re-reads the JPEG from disk on each
  emission, so array reference equality would make a frame unequal to itself. Both sheets moved
  their `LaunchedEffect` key to `sampleId` in the same change: widening equality alone would have
  re-seeded the sheet on every emission and wiped in-progress answers.
- Fixed: the queue list keyed rows by `capturedAt` millis, so two frames captured in the same
  millisecond crashed it. Keyed by `sampleId` now.
- Fixed: the capture toast keyed on `capturedAt` too, so two frames sharing a millisecond
  produced no toast at all.
- Fixed: frame analysis ran on the main thread. `FrameSampler` JPEG-encodes every frame before
  throttling, and the capture-boundary work had made that heavier.
- Changed: repeat frames no longer block ending a session. Only unverified, non-repeat frames do.

*Frame cycling.*

- Fixed: Next/Previous walked the whole store, so paging from a detection could land on a manual
  capture while the AI sheet kept rendering. Each sheet now cycles its own source only.
- Added: the manual sheet gained the navigation the AI sheet always had — prev/next, a position
  readout, and delete advancing to a neighbour. `ManualCaptureViewModel` had no tests; it has six.

*Interface.*

- Changed: the capture toast moved below the top chrome instead of over it, and the vacated
  top-right slot became a Records shortcut to the active session.
- Removed: the session row kebab menu. Its export item was a stub, but note that link/unlink
  account and end-session-from-the-list had no other entry point and are now unreachable.
- Changed: session rows show unverified frame counts — repeats excluded, matching the
  end-of-session check — in place of the Active badge.
- Removed: the bottom bar's Sessions badge, which the nav graph had always passed 0.
- Fixed: the record and settings screens drew under the status bar. One cause — the root graph
  zeroes `contentWindowInsets` app-wide and those two screens never applied their own.
- Fixed: settings used a Switch for the theme while the dashboard used a sun/moon button; the
  empty report state showed two identical generate affordances and a subtitle that squeezed the
  pill out of shape; the two record screens each had their own stat-run composable; back was
  rendered six different ways; and sample detail's label rows had no left padding.

**Repeat filter and preview fixes · 2026-09-07.**

- Fixed: a frame marked repeat still appeared under the AI chip and was still reachable by
  Next/Previous, so a duplicate could be verified by accident. The AI category now means "AI
  frames still needing review"; repeats stay reachable under Repeat and All. Marking the open
  frame drops it from the cycle but keeps it on screen so the mark can be undone — it reports no
  position and both frame buttons dim, rather than showing `Frame 0/N`.
- Fixed: the manual capture preview used `ContentScale.Crop` where the AI sheet uses `Fit`, so a
  square 640x640 frame overflowed the landscape container and the rounded clip cut the top and
  bottom off. The medtech was labelling a specimen they could only partly see.
- Corrected in `constraints.md`: C1's as-built note claimed a composable still rendered a wire
  DTO. That stopped being true when `FlaggedFrame` moved to domain `Prediction` values; nothing
  under `ui/` imports from `data/remote/dto/`.

## Unreleased — documentation architecture · 2026-08-31

Replaced the previous agent-documentation setup with a router plus a shelf.

- Added `SESSION_INIT.md` as the single session entry point, and `docs/` with a contract in
  each subfolder.
- Archived the three-stage scope/implement/QA pipeline to `docs/_archive/stages`.
- Added the rules layer (`constraints.md`, `non-negotiables.md`, `stack.md`, `commands.md`),
  the inventory layer (`features.md`, this file, `file-tree.md`), and the system map
  (`map/objects`, `map/processes`, `map/effects`).
- Retired the root `AGENTS.md` and `CONTEXT.md`.

## Tooling and hooks · 2026-08-11

- Husky git hooks: `pre-commit` runs compile → unit tests → `assembleDebug` → ktlint + detekt;
  `pre-push` runs `assembleDebug`. `JAVA_HOME` auto-detection with an Android Studio JBR
  fallback.
- **The `commit-msg` hook was added and then removed in the same day's work** — commit
  format went unchecked from then until `12509f8` restored it. See `constraints.md` C9.
- Documentation consolidation: the previous multi-file documentation tree collapsed into two
  root files, `CONTEXT.md` and `schema.ts`.

## Detekt debt cleanup · 2026-07-10

Mechanical fixes, targeted DI suppressions, and parameter bundling to reach a zero-violation
detekt run.

## Offline access and identity · 2026-07-09

The largest behavioural change in the project's history.

- The app now opens on the Dashboard; the login wall is gone
  (`ui/navigation/AgarthaNavGraph.kt:120`).
- Sessions, manual captures, and verification work fully offline; a cached `LocalIdentity`
  attributes offline work to the last signed-in medtech.
- Unowned rows are claimed at the next login, cascading sessions → samples → reports, followed
  by a trigger-based sync pass.
- Per-session "link to account" opt-out (`claim_exempt`).
- Sign-out: revokes the token *and* clears the cached identity; blocked while a session is
  active.
- New Settings screen: account, data and sync, appearance, about.
- Room schema v8 — `sessions.supabase_status`, `sessions.claim_exempt`, nullable
  `samples.user_id`. **No Supabase migration**; all three are Room-only.

## Rebrand · 2026-07-07

CIT-U maroon and gold replace the previous cobalt palette, with a light/dark mode toggle
persisted in DataStore. The earlier KomoUI design system is removed from both the app code and
the Gradle dependency graph.

## UI polish and branding · 2026-06-13 → 2026-06-27

Deprecated files deleted, records theming cleaned up, raw confidence values removed from the
medtech-facing UI, dialog corner radius fixed, custom adaptive launcher icon added, global
screen padding and keyboard clipping fixed. Kaggle setup added for inference experimentation.
The first agent-documentation setup ("ICM for AI tools") landed on 2026-06-22 — that is the
structure this change replaces.

## Reports · 2026-05-29

Persisted session reports as first-class rows in Room and Supabase (migration
`0008_reports.sql`), row-only sync with the CSV staying local, and the full UI screen revamp
against the design system.

## EPG, manual capture, sessions-as-smears · 2026-05-28

- Session redefined as one fecal smear; `sessions.label` added (`0005_session_label.sql`).
- Manual capture: `samples.is_manual` (`0006`) with nullable detection bounding boxes
  (`0007`).
- EPG shipped via the hardcoded Kato-Katz multiplier of 24.
- Verification queue converted from a sheet to a full screen; shutter button; species dropdown
  backed by the `EggSpecies` enum.

## Records browser · 2026-05-27 → 2026-05-29

Records list, session detail, sample detail with a signed-URL Storage fallback for images,
records filter bar, and local sample persistence.

## Sprint 1 — capture, auth, sync · 2026-05-25 → 2026-05-26

Supabase login flow, continuous frame sampling wired to inference, recording sessions
persisted to Supabase, `SyncSampleUseCase`, inference connection monitoring, and the
verification sheet scaffold. Migrations `0001`–`0004` land in this window.

## Project initialization · 2026-05-22

Gradle setup, project structure, README.
