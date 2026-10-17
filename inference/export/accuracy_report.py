#!/usr/bin/env python3
"""Report precision / recall / mAP for the .pt checkpoint and each exported variant, side by side.

    python inference/export/accuracy_report.py \
        --data ~/datasets/agarthavision/data.yaml \
        --weights inference/weights/yolo26n-efficientnetv2s.pt \
        --models inference/export/out/yolo26n-effv2s-v1-tflite-fp32.tflite inference/export/out/yolo26n-effv2s-v1-tflite-int8.tflite

This is the number that answers "is the mobile model good enough?" — parity_check.py only
says how far the export drifted from the .pt checkpoint, which is not the same as being correct.

## Read the split warning

Point --data at a data.yaml whose `val` split is genuinely held out. If you point it at the
AgarthaVision training set, the absolute mAP will be flattering, because the model was fit
to those images. The fp32 -> fp16 -> int8 *deltas* remain meaningful — all three variants
see the same images — but the absolute numbers must not be quoted as real-world accuracy.
This script prints that caveat with its results so a number cannot escape without it.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path


def load_class_names(data_yaml: Path) -> list[str]:
    import yaml

    with data_yaml.open() as handle:
        spec = yaml.safe_load(handle)

    names = spec.get("names", [])
    if isinstance(names, dict):
        return [names[key] for key in sorted(names.keys())]
    return list(names)


def check_class_order(data_yaml: Path, manifest_path: Path | None) -> None:
    """Refuse to report per-class numbers against a mismatched class order.

    A dataset whose class indices disagree with the exported model's produces per-class
    metrics that look completely reasonable and attribute every score to the wrong species.
    """
    if manifest_path is None or not manifest_path.exists():
        print("-> No manifest given; skipping the class-order check.")
        return

    import json

    manifest_classes = json.loads(manifest_path.read_text()).get("class_names", [])
    data_classes = load_class_names(data_yaml)

    if not manifest_classes or not data_classes:
        print("!! Could not compare class orders (one side is empty).")
        return

    if manifest_classes != data_classes:
        print("!! CLASS ORDER MISMATCH")
        print(f"!!   manifest: {manifest_classes}")
        print(f"!!   data.yaml: {data_classes}")
        print("!! Per-class metrics below would be attributed to the wrong species.")
        print("!! Fix the dataset order or re-export before trusting anything here.")
        sys.exit(1)

    print(f"-> Class order matches: {data_classes}")


def evaluate(model_path: str, data_yaml: str, imgsz: int, split: str) -> dict | None:
    from ultralytics import YOLO

    print(f"\n=== Validating {model_path} on '{split}' ===")
    try:
        metrics = YOLO(model_path).val(data=data_yaml, imgsz=imgsz, split=split, verbose=False)
    except Exception as error:  # noqa: BLE001 - one variant failing should not stop the rest
        print(f"!! Validation FAILED for {model_path}: {type(error).__name__}: {error}")
        return None

    box = metrics.box
    return {
        "model": Path(model_path).name,
        "precision": float(box.mp),
        "recall": float(box.mr),
        "map50": float(box.map50),
        "map": float(box.map),
        "per_class_map50": [float(value) for value in getattr(box, "ap50", [])],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", required=True, help="Path to data.yaml")
    parser.add_argument("--weights", default="inference/weights/yolo26n-efficientnetv2s.pt")
    parser.add_argument("--models", nargs="*", default=[], help="Exported models to compare")
    parser.add_argument("--manifest", help="A build's <model_version>.json manifest, for the class-order check")
    parser.add_argument("--imgsz", type=int, default=640)
    parser.add_argument(
        "--split",
        default="val",
        help="Dataset split. Keep this as 'val' — see the note in this file's docstring.",
    )
    args = parser.parse_args()

    data_yaml = Path(args.data)
    if not data_yaml.exists():
        print(f"!! {data_yaml} not found.")
        return 1

    check_class_order(data_yaml, Path(args.manifest) if args.manifest else None)

    sys.path.insert(0, str(Path(__file__).parent))
    from export_mobile import patch_backbone_pretrained

    patch_backbone_pretrained()

    rows = []
    baseline = evaluate(args.weights, str(data_yaml), args.imgsz, args.split)
    if baseline:
        rows.append(baseline)
    for model in args.models:
        row = evaluate(model, str(data_yaml), args.imgsz, args.split)
        if row:
            rows.append(row)

    if not rows:
        print("\n!! Nothing validated successfully.")
        return 1

    print("\n=== Accuracy ===")
    header = f"{'model':<28} {'precision':>10} {'recall':>10} {'mAP@50':>10} {'mAP@50-95':>10}"
    print(header)
    print("-" * len(header))
    for row in rows:
        print(
            f"{row['model']:<28} {row['precision']:>10.4f} {row['recall']:>10.4f} "
            f"{row['map50']:>10.4f} {row['map']:>10.4f}"
        )

    if len(rows) > 1:
        reference = rows[0]
        print("\n=== Cost of export, relative to the .pt checkpoint ===")
        for row in rows[1:]:
            delta_map50 = row["map50"] - reference["map50"]
            delta_recall = row["recall"] - reference["recall"]
            print(
                f"{row['model']:<28} mAP@50 {delta_map50:+.4f}   recall {delta_recall:+.4f}"
            )
        print("\nRecall is the one to watch: it is the eggs the mobile model would miss.")

    class_names = load_class_names(data_yaml)
    if class_names and rows[0]["per_class_map50"]:
        print("\n=== Per-class mAP@50 ===")
        print(f"{'class':<28} " + " ".join(f"{row['model'][:14]:>16}" for row in rows))
        for index, name in enumerate(class_names):
            values = " ".join(
                f"{row['per_class_map50'][index]:>16.4f}"
                if index < len(row["per_class_map50"]) else f"{'n/a':>16}"
                for row in rows
            )
            print(f"{name:<28} {values}")

    print(f"\nNOTE: evaluated on the '{args.split}' split of {data_yaml}.")
    print("If that split is not held out from training, these absolute numbers are optimistic.")
    print("The deltas between variants remain valid — every variant saw the same images.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
