# Exporting the model for on-device inference

`best.pt` is a PyTorch checkpoint. It runs where PyTorch runs (a droplet, a Kaggle notebook)
and nowhere else. Android has no PyTorch, so running on the phone means **exporting**:
rewriting the same trained weights into LiteRT's format, once, on a computer.

```
best.pt ──[ export_mobile.py ]──▶ yolo12n-effv2s-v1-tflite-fp16.tflite + .json
(PyTorch, server only)            yolo12n-effv2s-v1-tflite-int8.tflite + .json   (run on the phone)
```

Nothing about training changes. This is a format translation.

## Version names

`<arch>-v<training-version>-<target>-<precision>`, recorded per sample in
`samples.inference_model_version`:

| Path | Version |
|---|---|
| Cloud (`server.py`, PyTorch) | `yolo12n-effv2s-v1-cloud-fp32` |
| On device | `yolo12n-effv2s-v1-tflite-fp16`, `yolo12n-effv2s-v1-tflite-int8` |
| No model result | `manual` |

`yolo12n-effv2s` is the architecture `best.pt` was trained from (`effnet-v12.yaml`): an
EfficientNetV2-S backbone with a YOLOv12-nano `A2C2f` neck and a standard `Detect` head. **Bump
`v1` whenever `best.pt` changes**, in `export_mobile.py`'s `DEFAULT_VERSION_PREFIX`, in
`server.py` and the notebook, and in `OnDeviceModels`, so the same `v1` always means the same
weights.

---

## Setup

Export must run against **the pinned fork**, not stock `ultralytics`. Stock has never heard
of `EfficientNetV2Backbone` and cannot reconstruct `best.pt` at all.

```bash
python -m venv .venv-export && source .venv-export/bin/activate
pip install -r inference/export/requirements-export.txt
git lfs install && git lfs pull      # best.pt is a 133-byte pointer until you do this
```

The TensorFlow stack this pulls in is large. It is deliberately kept out of
`inference/requirements.txt` so the serving container stays lean.

## Run it

```bash
python inference/export/export_mobile.py \
    --weights inference/weights/best.pt \
    --calib-images path/to/dataset/images/val
```

Without `--calib-images` only fp16 is built. Calibrate int8 on held-out frames: 200 by default,
spread across the directory so every species is represented.

Outputs land in `inference/export/out/` (gitignored). To ship, copy each `.tflite` and its
`.json` into `app/src/main/assets/models/`. The `.tflite` files are committed through Git LFS
(`app/src/main/assets/models/.gitattributes`).

The script refuses to write a manifest for a build that is not what its name says. Each file
must load in LiteRT, take and return float32, contain weights of its precision, and emit
normalised box geometry.

## Then verify, in this order

**1. Did export change what the model finds?** No phone needed.

```bash
python inference/export/parity_check.py \
    --weights inference/weights/best.pt \
    --model inference/export/out/yolo12n-effv2s-v1-tflite-fp16.tflite \
    --images path/to/dataset/images/val
```

**2. Is it still accurate?** Needs the labelled set.

```bash
python inference/export/accuracy_report.py \
    --data path/to/data.yaml \
    --manifest inference/export/out/yolo12n-effv2s-v1-tflite-fp16.json \
    --models inference/export/out/yolo12n-effv2s-v1-tflite-fp16.tflite \
             inference/export/out/yolo12n-effv2s-v1-tflite-int8.tflite
```

Point `--data` at a **held-out** split. Absolute mAP measured on training images is
flattering. The fp32 → fp16 → int8 *deltas* stay valid regardless, since every variant sees
the same images.

**3. Does the phone agree with the cloud, and how fast is it?**

```bash
python inference/export/make_parity_fixture.py --images path/to/dataset/images/val --count 20
./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.agarthavision.data.inference.ondevice.OnDeviceInferenceParityTest
adb logcat -d -s OnDeviceParity OnDeviceInference
```

The fixture script shapes frames exactly as the capture screen does and records what the cloud
returns for them. The test runs both bundled precisions on the phone against that reference
and logs median and p90 latency per precision. Fixtures are patient-adjacent imagery and stay
gitignored.

---

## Why the pipeline is spelled out instead of `model.export(format="tflite")`

Ultralytics' TFLite export hands its ONNX to onnx2tf. **onnx2tf 2.x's default backend emits a
model that is fp16 end to end** (fp16 input, activations and output). LiteRT's CPU kernels
reject fp16 convolutions, so that file cannot fall back from GPU to CPU, and it does not even
load in the reference interpreter. Ultralytics pins `onnx2tf<1.29` to avoid this, but that pin
cannot be satisfied alongside the TensorFlow the rest of the stack needs.

So `export_mobile.py` runs the steps itself:

1. **Ultralytics → ONNX**, with `tf_wrapper` applied first. That is what Ultralytics' own
   TFLite export does: the head divides box geometry by the input size, so it comes out
   normalised to 0..1. For int8 this is essential: the box rows share one quantisation scale
   with the 0..1 class scores, and pixel coordinates up to 640 would crush the scores.
2. **onnx2tf with `-tb tf_converter`** → SavedModel plus the standard fp16 build (fp16 weights
   dequantised to fp32 on load, fp32 activations and I/O).
3. **TFLiteConverter on the SavedModel** with a representative dataset → int8 weights and
   activations, float32 I/O. Calibration frames are letterboxed exactly as the app does it.

## What to expect from this model

**Normalised boxes.** The TFLite head emits `[1, 7, 8400]`: `cx, cy, w, h` as fractions of the
input, then three sigmoid class scores. The manifest's `box_coordinates: "normalized"` tells the
app's decoder to scale them. The first on-device attempt read them as pixels and matched zero
boxes against the cloud.

**Not end-to-end.** There is no one-to-one head (`end2end` is unset in `effnet-v12.yaml`), so
the app runs NMS itself, with the container's defaults: conf 0.25, IoU 0.7, class-aware, at
most 300 boxes.

**`dynamic=False` is load-bearing.** timm's `tf_efficientnetv2_s` uses `Conv2dSame`, which
emulates TensorFlow SAME padding with a runtime `F.pad` computed from the input's dims. Traced
at a fixed size those fold into constant `Pad` nodes; with dynamic axes they become
shape-dependent ops that onnx2tf mangles. The app feeds a fixed 640x640 anyway.

**`pretrained=True` is the backbone's constructor default.** Every reconstruction downloads
~80 MB of ImageNet weights that `best.pt` immediately overwrites. `patch_backbone_pretrained()`
disables it, so export neither wastes the bandwidth nor fails offline.

**int8 versus squeeze-excite.** EfficientNetV2's SE blocks and SiLU activations are the
textbook int8 accuracy sink. If int8 disappoints, look there before assuming the export is bad.

**The GPU may not take the whole graph.** The neck's `A2C2f` blocks use attention
(`BATCH_MATMUL`, `SOFTMAX`). LiteRT places ops the GPU cannot run on the CPU, which works but
costs speed. The first attempt's GPU delegate rejected its graph outright over a dynamic-sized
tensor; the per-frame log says which accelerators actually loaded.

Sizes as exported: fp16 ≈ 39 MB (the fp32 graph is ≈ 79 MB).
