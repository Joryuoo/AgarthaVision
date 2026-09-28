#!/usr/bin/env python3
"""Compare an exported model against the .pt checkpoint on the same images.

    python inference/export/parity_check.py \
        --weights inference/weights/yolo26n-efficientnetv2b0.pt \
        --model inference/export/out/yolo26n-effv2b0-v1-tflite-fp32.tflite \
        --images path/to/test/images

Answers one question: did conversion change what the model finds? Runs both through the
same Ultralytics predict API, pairs boxes by IoU, and reports what moved.

This is Tier A of the comparison — it isolates *export* loss, with no phone involved. If
numbers here are bad, the problem is the conversion, not the device. Run it before putting
anything on a phone.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".bmp"}
MATCH_IOU = 0.5


def iou(a, b) -> float:
    """IoU of two xyxy boxes."""
    left = max(a[0], b[0])
    top = max(a[1], b[1])
    right = min(a[2], b[2])
    bottom = min(a[3], b[3])
    if right <= left or bottom <= top:
        return 0.0
    intersection = (right - left) * (bottom - top)
    area_a = (a[2] - a[0]) * (a[3] - a[1])
    area_b = (b[2] - b[0]) * (b[3] - b[1])
    union = area_a + area_b - intersection
    return intersection / union if union > 0 else 0.0


def boxes_of(result):
    """(xyxy, confidence, class index) for each detection."""
    if result.boxes is None or len(result.boxes) == 0:
        return []
    return [
        (box.xyxy[0].tolist(), float(box.conf), int(box.cls))
        for box in result.boxes
    ]


def collect_images(root: Path, limit: int) -> list[Path]:
    if root.is_file():
        return [root]
    found = sorted(p for p in root.rglob("*") if p.suffix.lower() in IMAGE_SUFFIXES)
    return found[:limit]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--weights", default="inference/weights/yolo26n-efficientnetv2b0.pt")
    parser.add_argument("--model", required=True, help="Exported .tflite or .onnx")
    parser.add_argument("--images", required=True, help="Image file or directory")
    parser.add_argument("--imgsz", type=int, default=640)
    parser.add_argument("--conf", type=float, default=0.25)
    parser.add_argument("--iou", type=float, default=0.7)
    parser.add_argument("--limit", type=int, default=50)
    parser.add_argument(
        "--max-mean-iou-drop",
        type=float,
        default=0.10,
        help="Fail if mean IoU on matched boxes falls below 1 - this",
    )
    parser.add_argument(
        "--max-miss-rate",
        type=float,
        default=0.10,
        help="Fail if this fraction of the reference model's boxes go missing",
    )
    args = parser.parse_args()

    images = collect_images(Path(args.images), args.limit)
    if not images:
        print(f"!! No images found under {args.images}")
        return 1

    sys.path.insert(0, str(Path(__file__).parent))
    from export_mobile import patch_backbone_pretrained

    patch_backbone_pretrained()
    from ultralytics import YOLO

    print(f"-> Reference: {args.weights}")
    reference = YOLO(args.weights)
    print(f"-> Exported:  {args.model}")
    exported = YOLO(args.model)

    predict_args = {"imgsz": args.imgsz, "conf": args.conf, "iou": args.iou, "verbose": False}

    matched = missed = invented = 0
    ious: list[float] = []
    conf_deltas: list[float] = []
    class_agreements = 0

    for image in images:
        ref_boxes = boxes_of(reference.predict(str(image), **predict_args)[0])
        exp_boxes = boxes_of(exported.predict(str(image), **predict_args)[0])

        remaining = list(exp_boxes)
        for ref_box, ref_conf, ref_cls in ref_boxes:
            best_index, best_iou = -1, 0.0
            for index, (exp_box, _, _) in enumerate(remaining):
                overlap = iou(ref_box, exp_box)
                if overlap > best_iou:
                    best_index, best_iou = index, overlap

            if best_index >= 0 and best_iou >= MATCH_IOU:
                _, exp_conf, exp_cls = remaining.pop(best_index)
                matched += 1
                ious.append(best_iou)
                conf_deltas.append(abs(ref_conf - exp_conf))
                if ref_cls == exp_cls:
                    class_agreements += 1
            else:
                missed += 1

        invented += len(remaining)

    reference_total = matched + missed
    mean_iou = sum(ious) / len(ious) if ious else 0.0
    mean_conf_delta = sum(conf_deltas) / len(conf_deltas) if conf_deltas else 0.0
    class_rate = class_agreements / matched if matched else 0.0
    miss_rate = missed / reference_total if reference_total else 0.0

    print("\n=== Parity: exported model vs the .pt checkpoint ===")
    print(f"images                 {len(images)}")
    print(f"reference detections   {reference_total}")
    print(f"matched                {matched}")
    print(f"missed by export       {missed}  ({miss_rate:.1%} of reference)")
    print(f"invented by export     {invented}")
    print(f"mean IoU on matches    {mean_iou:.4f}")
    print(f"mean |conf delta|      {mean_conf_delta:.4f}")
    print(f"class agreement        {class_rate:.1%}")

    print("\nNote: 'missed' means the export lost a detection the .pt checkpoint found — the direction")
    print("that silently lowers an egg count. 'Invented' boxes are caught during validation.")
    print("They are not equivalent and are deliberately not netted off.")

    failures = []
    if reference_total and mean_iou < (1.0 - args.max_mean_iou_drop):
        failures.append(f"mean IoU {mean_iou:.4f} below {1.0 - args.max_mean_iou_drop:.4f}")
    if miss_rate > args.max_miss_rate:
        failures.append(f"miss rate {miss_rate:.1%} above {args.max_miss_rate:.1%}")

    if failures:
        print("\n!! PARITY FAILED: " + "; ".join(failures))
        print("!! Do not ship this export. Try fp16 instead of int8, or check the manifest's")
        print("!! input size and class order against the model.")
        return 1

    if reference_total == 0:
        print("\n!! Neither model detected anything. Parity is meaningless on these images —")
        print("!! use frames that actually contain eggs.")
        return 1

    print("\n-> PARITY OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
