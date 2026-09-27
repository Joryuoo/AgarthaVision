#!/usr/bin/env python3
"""Export best.pt to TFLite for the Android app, one model and one manifest per precision.

Run this on a machine with the pinned Ultralytics fork installed (see
requirements-export.txt), not inside the serving container.

    python inference/export/export_mobile.py \
        --weights inference/weights/best.pt \
        --calib-images path/to/dataset/images/val

Outputs land in inference/export/out/ (gitignored), each named after the build's
inference_model_version:

    yolo12n-effv2s-v1-tflite-fp16.tflite / .json    fp16 weights, fp32 activations and I/O
    yolo12n-effv2s-v1-tflite-int8.tflite / .json    int8 weights and activations, fp32 I/O

Copy the pairs you ship into app/src/main/assets/models/.

Version names follow <arch>-v<training-version>-<target>-<precision>. Bump the training
version whenever best.pt changes, so the same v1 always means the same trained weights.

Why this does not call Ultralytics' own `format="tflite"`: Ultralytics hands the ONNX to
onnx2tf, and onnx2tf 2.x's default backend emits a model that is fp16 *end to end* (fp16
input, activations and output). LiteRT's CPU kernels reject fp16 convolutions, so that file
cannot fall back from GPU to CPU, and it will not even load in the reference interpreter.
Ultralytics pins onnx2tf<1.29 to avoid this, but the pin cannot be satisfied alongside the
TensorFlow this venv needs. So the pipeline is spelled out here instead:

    best.pt --Ultralytics--> ONNX --onnx2tf (tf_converter)--> SavedModel + fp16 TFLite
                                                SavedModel --TFLiteConverter + calibration--> int8

That produces the standard layouts no matter which onnx2tf is installed, and every build is
checked afterwards for the dtypes its name promises.
"""

from __future__ import annotations

import argparse
import collections
import json
import shutil
import subprocess
import sys
from pathlib import Path

import numpy as np

DEFAULT_IMGSZ = 640
# Ultralytics' own predict defaults: conf falls back to 0.25, iou is 0.7 in cfg/default.yaml.
# The server calls model(img) with no overrides, so matching them here is what keeps cloud and
# on-device results comparable rather than merely similar.
DEFAULT_CONF = 0.25
DEFAULT_IOU = 0.7
DEFAULT_MAX_DET = 300
DEFAULT_VERSION_PREFIX = "yolo12n-effv2s-v1-tflite"
DEFAULT_CALIB_COUNT = 200

# Ultralytics letterboxes onto (114, 114, 114). The app pads with the same grey.
LETTERBOX_FILL = 114

IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".bmp"}

# Normalised boxes can overhang the frame edge slightly; pixel boxes are in the hundreds.
NORMALIZED_GEOMETRY_LIMIT = 2.0

# The weight dtype each precision label must actually contain. Checked after export, because
# a file named fp16 that was really fp32 has shipped before and nothing noticed.
EXPECTED_WEIGHT_DTYPE = {"fp16": "float16", "int8": "int8"}


def patch_backbone_pretrained() -> None:
    """Stop the timm backbone downloading ImageNet weights it is about to overwrite.

    EfficientNetV2Backbone.__init__ defaults to pretrained=True, so every reconstruction of
    the architecture — including the one YOLO() does before loading your checkpoint — pulls
    ~80MB from HuggingFace. Those weights are then immediately replaced by best.pt's. The
    download is pure waste, and it makes export fail offline for a reason that has nothing
    to do with export.
    """
    try:
        from ultralytics.nn.modules import block
    except ImportError:
        print("!! Could not import ultralytics.nn.modules.block; skipping the pretrained patch.")
        return

    backbone = getattr(block, "EfficientNetV2Backbone", None)
    if backbone is None:
        print("!! EfficientNetV2Backbone not found — is this the DMKuZu fork?")
        return

    original_init = backbone.__init__

    def patched_init(self, pretrained=False):  # noqa: FBT002 - mirrors the original signature
        original_init(self, pretrained=False)

    backbone.__init__ = patched_init
    print("-> Patched EfficientNetV2Backbone to skip the pretrained download.")


def letterbox(path: Path, size: int) -> np.ndarray:
    """One image as the model sees it: RGB, letterboxed to size x size, scaled to 0..1, NHWC.

    Same arithmetic as Ultralytics' LetterBox (scale-up allowed, centred padding) and as the
    app's FramePreprocessor, so calibration sees the input distribution the phone produces.
    """
    from PIL import Image

    image = Image.open(path).convert("RGB")
    scale = min(size / image.width, size / image.height)
    new_width, new_height = round(image.width * scale), round(image.height * scale)
    resized = image.resize((new_width, new_height), Image.BILINEAR)
    canvas = Image.new("RGB", (size, size), (LETTERBOX_FILL,) * 3)
    left = round((size - new_width) / 2 - 0.1)
    top = round((size - new_height) / 2 - 0.1)
    canvas.paste(resized, (left, top))
    return (np.asarray(canvas, dtype=np.float32) / 255.0)[None]


def collect_images(root: Path, limit: int) -> list[Path]:
    found = sorted(p for p in root.rglob("*") if p.suffix.lower() in IMAGE_SUFFIXES)
    if len(found) <= limit:
        return found
    # Evenly spaced rather than the first N: file names sort by class here, and calibrating
    # on one species would tune the activation ranges to it alone.
    step = len(found) / limit
    return [found[int(i * step)] for i in range(limit)]


def inspect_tflite(path: Path) -> dict:
    """Read the I/O shapes and the tensor dtypes back out of an exported file.

    allocate_tensors() doubles as a load test: it is exactly where the fp16-activation build
    failed, so a file that gets past it will at least run on LiteRT's CPU kernels.
    """
    from ai_edge_litert.interpreter import Interpreter

    interpreter = Interpreter(model_path=str(path))
    interpreter.allocate_tensors()
    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]
    dtypes = collections.Counter(detail["dtype"].__name__ for detail in interpreter.get_tensor_details())

    # One run on a flat grey frame. Normalised geometry stays near 0..1 whatever the content;
    # pixel geometry reaches into the hundreds.
    grey = np.full(input_detail["shape"], LETTERBOX_FILL / 255.0, dtype=input_detail["dtype"])
    interpreter.set_tensor(input_detail["index"], grey)
    interpreter.invoke()
    geometry_max = float(np.abs(interpreter.get_tensor(output_detail["index"])[0, :4]).max())
    return {
        "geometry_max": geometry_max,
        "input_shape": [int(d) for d in input_detail["shape"]],
        "input_dtype": input_detail["dtype"].__name__,
        "output_shape": [int(d) for d in output_detail["shape"]],
        "output_dtype": output_detail["dtype"].__name__,
        "tensor_dtypes": dict(dtypes),
    }


def verify(label: str, path: Path) -> dict | None:
    """Check a build is what its name says. Returns its details, or None to discard it."""
    try:
        info = inspect_tflite(path)
    except Exception as error:  # noqa: BLE001 - any load failure disqualifies the build
        print(f"!! The {label} build does not load in LiteRT: {error}")
        return None

    print(
        f"-> {label}: input {info['input_shape']} {info['input_dtype']}, "
        f"output {info['output_shape']} {info['output_dtype']}"
    )
    print(f"   tensor dtypes: {info['tensor_dtypes']}")

    problems = []
    expected = EXPECTED_WEIGHT_DTYPE[label]
    if not info["tensor_dtypes"].get(expected):
        problems.append(f"no {expected} tensors")
    # The app feeds and reads float32 for every precision; one pre/post-processing path.
    if info["input_dtype"] != "float32" or info["output_dtype"] != "float32":
        problems.append("I/O is not float32")
    if info["geometry_max"] > NORMALIZED_GEOMETRY_LIMIT:
        problems.append(f"box geometry reaches {info['geometry_max']:.1f}, so it is not normalised")
    if problems:
        print(f"!! The {label} build is mislabelled ({', '.join(problems)}); discarding it.")
        return None
    return info


def build_manifest(names, args, label: str, model_file: str, info: dict) -> dict:
    """Derive the manifest from the model itself rather than from assumptions.

    Class names especially: a hand-written list that disagrees with the model's own ordering
    produces per-class results that look plausible and are entirely wrong.
    """
    if isinstance(names, dict):
        class_names = [names[key] for key in sorted(names.keys())]
    else:
        class_names = list(names)

    return {
        "model_version": f"{args.version_prefix}-{label}",
        "model_file": model_file,
        "precision": label,
        "input_width": args.imgsz,
        "input_height": args.imgsz,
        "output_shape": info["output_shape"],
        # [1, 4 + classes, anchors]: cx, cy, w, h, then one sigmoid score per class. Ultralytics'
        # TFLite head divides the box by the input size, so geometry is normalised to 0..1.
        # The head is not end-to-end, so the app must run NMS.
        "output_layout": "xywh_cls",
        "box_coordinates": "normalized",
        "normalization_scale": 255.0,
        "letterbox": True,
        "class_names": class_names,
        "conf_threshold": args.conf,
        "iou_threshold": args.iou,
        "max_detections": args.max_det,
    }


def export_onnx(model, args) -> Path:
    # dynamic=False is load-bearing. The backbone is timm's `tf_efficientnetv2_s`, whose
    # Conv2dSame emulates TensorFlow SAME padding with a runtime F.pad computed from the
    # input's spatial dims. Traced at a fixed size those fold into constant Pad nodes and
    # convert cleanly; with dynamic axes they become shape-dependent ops that onnx2tf
    # mangles. The app feeds a fixed 640x640 anyway.
    #
    # tf_wrapper is what Ultralytics' own TFLite export applies before its ONNX step: it swaps
    # the head's box decode for one that divides by the input size, so geometry comes out
    # normalised to 0..1. That matters for int8, where the box rows share one quantisation
    # scale with the 0..1 class scores; pixel coordinates up to 640 would crush the scores.
    from ultralytics.utils.export.tensorflow import tf_wrapper

    tf_wrapper(model.model)
    produced = model.export(format="onnx", imgsz=args.imgsz, dynamic=False, simplify=True)
    return Path(produced)


def convert_saved_model(onnx_path: Path, work_dir: Path) -> Path:
    """ONNX -> SavedModel plus fp32/fp16 TFLite, through TensorFlow's own converter."""
    if work_dir.exists():
        shutil.rmtree(work_dir)
    command = [
        sys.executable, "-m", "onnx2tf",
        "-i", str(onnx_path),
        "-o", str(work_dir),
        "-tb", "tf_converter",  # standard fp16: fp16 weights, dequantised to fp32 on load
        "-osd",                  # keep the signature so the int8 pass can reload the SavedModel
        "-n",
    ]
    print(f"\n=== onnx2tf ===\n   {' '.join(command)}")
    subprocess.run(command, check=True)
    return work_dir


def convert_int8(saved_model: Path, target: Path, images: list[Path], imgsz: int) -> None:
    """Full-integer weights and activations, float32 I/O, calibrated on real frames."""
    import tensorflow as tf

    def representative_dataset():
        for index, image in enumerate(images, start=1):
            if index % 50 == 0:
                print(f"   calibrating {index}/{len(images)}")
            yield [letterbox(image, imgsz)]

    converter = tf.lite.TFLiteConverter.from_saved_model(str(saved_model))
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = representative_dataset
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    # Left at float32 on purpose: the app then shares one pre/post-processing path across
    # precisions, and the quantise/dequantise at the edges costs nothing measurable.
    converter.inference_input_type = tf.float32
    converter.inference_output_type = tf.float32
    target.write_bytes(converter.convert())


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--weights", default="inference/weights/best.pt", help="Path to best.pt")
    parser.add_argument("--out", default="inference/export/out", help="Output directory")
    parser.add_argument("--imgsz", type=int, default=DEFAULT_IMGSZ, help="Square input size")
    parser.add_argument("--conf", type=float, default=DEFAULT_CONF, help="Decode confidence threshold")
    parser.add_argument("--iou", type=float, default=DEFAULT_IOU, help="NMS IoU threshold")
    parser.add_argument("--max-det", type=int, default=DEFAULT_MAX_DET, help="Detections kept per frame")
    parser.add_argument(
        "--version-prefix",
        default=DEFAULT_VERSION_PREFIX,
        help="Version name minus the precision, which is appended per build",
    )
    parser.add_argument("--calib-images", help="Directory of frames for int8 calibration. Without it, int8 is skipped.")
    parser.add_argument("--calib-count", type=int, default=DEFAULT_CALIB_COUNT, help="Frames used for calibration")
    args = parser.parse_args()

    weights = Path(args.weights)
    if not weights.exists():
        print(f"!! {weights} not found.")
        return 1
    if weights.stat().st_size < 1_000_000:
        print(f"!! {weights} is only {weights.stat().st_size} bytes — that is the Git LFS pointer.")
        print("!! Run `git lfs install && git lfs pull` first.")
        return 1

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    patch_backbone_pretrained()
    from ultralytics import YOLO

    print(f"-> Loading {weights}")
    model = YOLO(str(weights))
    names = model.names

    onnx_path = export_onnx(model, args)
    saved_model = convert_saved_model(onnx_path, out_dir / "saved_model")

    builds: dict[str, Path | None] = {}

    fp16_target = out_dir / f"{args.version_prefix}-fp16.tflite"
    shutil.copy2(saved_model / f"{onnx_path.stem}_float16.tflite", fp16_target)
    builds["fp16"] = fp16_target

    if args.calib_images:
        images = collect_images(Path(args.calib_images), args.calib_count)
        if not images:
            print(f"!! No images under {args.calib_images}; skipping int8.")
        else:
            print(f"\n=== int8 (calibrating on {len(images)} frames) ===")
            int8_target = out_dir / f"{args.version_prefix}-int8.tflite"
            convert_int8(saved_model, int8_target, images, args.imgsz)
            builds["int8"] = int8_target
    else:
        print("\n-> Skipping int8: no --calib-images given, and int8 needs calibration frames.")

    print("\n=== Verify ===")
    for label, path in list(builds.items()):
        info = verify(label, path)
        if info is None:
            path.unlink()
            builds[label] = None
            continue
        manifest = build_manifest(names, args, label, path.name, info)
        path.with_suffix(".json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

    print("\n=== Summary ===")
    for label, path in builds.items():
        size = f"({path.stat().st_size / (1024 * 1024):.1f} MB)" if path else ""
        print(f"  {label:5s} {'OK  ' + str(path) if path else 'FAILED'} {size}")

    if not any(builds.values()):
        print("\n!! Nothing exported.")
        return 1

    print("\nNext: python inference/export/parity_check.py --model <build>.tflite --images <frames>")
    return 0


if __name__ == "__main__":
    sys.exit(main())
