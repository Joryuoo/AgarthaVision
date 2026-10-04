#!/usr/bin/env python3
"""Build the frames and cloud reference the on-device parity test compares against.

    python inference/export/make_parity_fixture.py \
        --images path/to/dataset/images/val \
        --count 20

Writes into app/src/androidTest/assets/fixtures/ (gitignored, like all capture fixtures):

    parity_00.jpg ... parity_NN.jpg   frames shaped exactly as the capture screen shapes them
    parity_reference.json             what the cloud server returns for each of those frames

Each frame goes through the same steps as `core/util/ImageExtensions.kt`: centre-crop to a
square, downscale to 640 (never up), JPEG at quality 80. The reference is then produced the
way `inference/server.py` produces it: decode those exact JPEG bytes, call model(img) with
Ultralytics' defaults, report centre-based xywh. So the device test compares the phone
against the cloud on byte-identical input, which is the comparison that matters.

Then run the test on a connected phone:

    ./gradlew :app:connectedDebugAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.class=com.agarthavision.data.inference.ondevice.OnDeviceInferenceParityTest
    adb logcat -d -s OnDeviceParity OnDeviceInference
"""

from __future__ import annotations

import argparse
import io
import json
import sys
from pathlib import Path

CAPTURE_FRAME_SIZE_PX = 640
CAPTURE_JPEG_QUALITY = 80
CLOUD_MODEL_VERSION = "yolo26n-effv2s-v1-cloud-fp32"


def capture_frame(path: Path) -> bytes:
    """One dataset image as the capture screen would have posted it."""
    from PIL import Image

    image = Image.open(path).convert("RGB")
    side = min(image.width, image.height)
    left = (image.width - side) // 2
    top = (image.height - side) // 2
    square = image.crop((left, top, left + side, top + side))
    target = min(side, CAPTURE_FRAME_SIZE_PX)
    if target != side:
        square = square.resize((target, target), Image.BILINEAR)
    buffer = io.BytesIO()
    square.save(buffer, format="JPEG", quality=CAPTURE_JPEG_QUALITY)
    return buffer.getvalue()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--weights", default="inference/weights/yolo26n-efficientnetv2s.pt")
    parser.add_argument("--images", required=True, help="Directory of labelled frames, ideally a held-out split")
    parser.add_argument("--count", type=int, default=20)
    parser.add_argument("--out", default="app/src/androidTest/assets/fixtures")
    args = parser.parse_args()

    sys.path.insert(0, str(Path(__file__).parent))
    from export_mobile import collect_images, patch_backbone_pretrained

    images = collect_images(Path(args.images), args.count)
    if not images:
        print(f"!! No images under {args.images}")
        return 1

    patch_backbone_pretrained()
    from PIL import Image
    from ultralytics import YOLO

    model = YOLO(args.weights)
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    for stale in out_dir.glob("parity_*"):
        stale.unlink()

    frames = []
    for index, source in enumerate(images):
        jpeg = capture_frame(source)
        name = f"parity_{index:02d}.jpg"
        (out_dir / name).write_bytes(jpeg)

        # Exactly server.py's path from request body to response.
        img = Image.open(io.BytesIO(jpeg)).convert("RGB")
        result = model(img, verbose=False, agnostic_nms=True)[0]
        predictions = []
        for box in result.boxes:
            x, y, w, h = box.xywh[0].tolist()
            predictions.append({
                "class": result.names[int(box.cls)],
                "confidence": round(float(box.conf), 4),
                "x": round(x, 2),
                "y": round(y, 2),
                "width": round(w, 2),
                "height": round(h, 2),
            })
        frames.append({"file": name, "source": source.name, "predictions": predictions})
        print(f"-> {name} ({source.name}): {len(predictions)} boxes")

    reference = {"model_version": CLOUD_MODEL_VERSION, "frames": frames}
    (out_dir / "parity_reference.json").write_text(json.dumps(reference, indent=2) + "\n", encoding="utf-8")
    total = sum(len(frame["predictions"]) for frame in frames)
    print(f"\n-> {len(frames)} frames, {total} reference boxes in {out_dir}")
    if total == 0:
        print("!! The cloud model found nothing. Parity is meaningless on these frames.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
