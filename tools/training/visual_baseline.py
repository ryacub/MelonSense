from __future__ import annotations

import argparse
import json
import random
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np
import torch
from PIL import Image, ImageOps
from torch import nn
from torch.utils.data import DataLoader, Dataset


IMAGE_SIZE = 96
BATCH_SIZE = 32
TRACKS = {
    "sweetness": {
        "labels": ["not_sweet", "sweet"],
        "manifests": ["datasets/interim/visual-sweetness-v0/manifest.jsonl"],
        "sample_source": "image",
    },
    "ripeness": {
        "labels": ["overripe", "ripe", "unripe"],
        "manifests": [
            "datasets/interim/visual-ripeness-fyp-v0/manifest.jsonl",
            "datasets/interim/visual-ripeness-saysay-v0/manifest.jsonl",
        ],
        "sample_source": "annotation",
    },
}


@dataclass(frozen=True)
class Sample:
    image_path: Path
    label: str
    split: str
    crop: tuple[float, float, float, float] | None


class ImageClassificationDataset(Dataset[tuple[torch.Tensor, torch.Tensor]]):
    def __init__(
        self,
        samples: list[Sample],
        labels: list[str],
        image_size: int = IMAGE_SIZE,
    ) -> None:
        self.samples = samples
        self.label_to_index = {label: index for index, label in enumerate(labels)}
        self.image_size = image_size

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, index: int) -> tuple[torch.Tensor, torch.Tensor]:
        sample = self.samples[index]
        image = load_image_tensor(sample, self.image_size)
        label = torch.tensor(self.label_to_index[sample.label], dtype=torch.long)
        return image, label


class SmallVisualClassifier(nn.Module):
    def __init__(self, class_count: int) -> None:
        super().__init__()
        self.network = nn.Sequential(
            nn.Conv2d(3, 16, kernel_size=3, padding=1),
            nn.ReLU(),
            nn.MaxPool2d(2),
            nn.Conv2d(16, 32, kernel_size=3, padding=1),
            nn.ReLU(),
            nn.MaxPool2d(2),
            nn.Conv2d(32, 64, kernel_size=3, padding=1),
            nn.ReLU(),
            nn.AdaptiveAvgPool2d((1, 1)),
            nn.Flatten(),
            nn.Linear(64, class_count),
        )

    def forward(self, images: torch.Tensor) -> torch.Tensor:
        return self.network(images)


def collect_image_samples(
    *,
    repo_root: Path,
    manifest_paths: list[Path],
    allowed_labels: list[str],
) -> list[Sample]:
    allowed = set(allowed_labels)
    samples: list[Sample] = []
    for record in read_manifest_records(manifest_paths):
        label = str(record.get("normalized_label", "unknown"))
        if label not in allowed:
            continue
        samples.append(
            Sample(
                image_path=repo_root / str(record["image_path"]),
                label=label,
                split=str(record.get("split", infer_split_from_path(str(record["image_path"])))),
                crop=None,
            ),
        )
    return samples


def collect_annotation_samples(
    *,
    repo_root: Path,
    manifest_paths: list[Path],
    allowed_labels: list[str],
) -> list[Sample]:
    allowed = set(allowed_labels)
    samples: list[Sample] = []
    for record in read_manifest_records(manifest_paths):
        split = str(record.get("split", infer_split_from_path(str(record["image_path"]))))
        for annotation in record.get("annotations", []):
            label = str(annotation.get("normalized_label", "unknown"))
            if label not in allowed:
                continue
            crop = annotation_crop(annotation)
            if crop is None:
                continue
            samples.append(
                Sample(
                    image_path=repo_root / str(record["image_path"]),
                    label=label,
                    split=split,
                    crop=crop,
                ),
            )
    return samples


def read_manifest_records(manifest_paths: list[Path]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for manifest_path in manifest_paths:
        with manifest_path.open("r", encoding="utf-8") as handle:
            for line in handle:
                if line.strip():
                    records.append(json.loads(line))
    return records


def annotation_crop(annotation: dict[str, Any]) -> tuple[float, float, float, float] | None:
    bbox = annotation.get("bbox_yolo")
    if isinstance(bbox, list) and len(bbox) == 4:
        center_x, center_y, width, height = [float(value) for value in bbox]
        return clamp_crop(
            center_x - width / 2,
            center_y - height / 2,
            center_x + width / 2,
            center_y + height / 2,
        )
    polygon = annotation.get("polygon_yolo")
    if isinstance(polygon, list) and len(polygon) >= 6 and len(polygon) % 2 == 0:
        xs = [float(value) for value in polygon[0::2]]
        ys = [float(value) for value in polygon[1::2]]
        return clamp_crop(min(xs), min(ys), max(xs), max(ys))
    return None


def clamp_crop(
    left: float,
    top: float,
    right: float,
    bottom: float,
) -> tuple[float, float, float, float]:
    return (
        max(0.0, min(1.0, left)),
        max(0.0, min(1.0, top)),
        max(0.0, min(1.0, right)),
        max(0.0, min(1.0, bottom)),
    )


def infer_split_from_path(image_path: str) -> str:
    parts = Path(image_path).parts
    for split in ("train", "valid", "validation", "test"):
        if split in parts:
            return "valid" if split == "validation" else split
    return "train"


def split_samples(samples: list[Sample]) -> tuple[list[Sample], list[Sample], list[Sample]]:
    by_split: dict[str, list[Sample]] = {"train": [], "valid": [], "test": []}
    for sample in samples:
        split = "valid" if sample.split == "validation" else sample.split
        by_split.setdefault(split, []).append(sample)
    if by_split["valid"] and by_split["test"]:
        return by_split["train"], by_split["valid"], by_split["test"]
    shuffled = list(samples)
    random.Random(17).shuffle(shuffled)
    valid_start = int(len(shuffled) * 0.7)
    test_start = int(len(shuffled) * 0.85)
    return shuffled[:valid_start], shuffled[valid_start:test_start], shuffled[test_start:]


def load_image_tensor(sample: Sample, image_size: int) -> torch.Tensor:
    with Image.open(sample.image_path) as original:
        image = ImageOps.exif_transpose(original).convert("RGB")
        if sample.crop is not None:
            width, height = image.size
            left, top, right, bottom = sample.crop
            box = (
                int(left * width),
                int(top * height),
                max(int(right * width), int(left * width) + 1),
                max(int(bottom * height), int(top * height) + 1),
            )
            image = image.crop(box)
        image = image.resize((image_size, image_size))
        array = np.asarray(image, dtype=np.float32) / 255.0
    return torch.from_numpy(array).permute(2, 0, 1)


def compute_classification_metrics(
    *,
    labels: list[str],
    samples: list[Sample],
    truth: list[int],
    predictions: list[int],
    probabilities: list[list[float]],
    max_failed_examples: int,
) -> dict[str, Any]:
    matrix = [[0 for _ in labels] for _ in labels]
    for expected, predicted in zip(truth, predictions):
        matrix[expected][predicted] += 1

    f1_scores: list[float] = []
    for index in range(len(labels)):
        true_positive = matrix[index][index]
        false_positive = sum(matrix[row][index] for row in range(len(labels)) if row != index)
        false_negative = sum(matrix[index][col] for col in range(len(labels)) if col != index)
        precision = true_positive / (true_positive + false_positive) if true_positive + false_positive else 0.0
        recall = true_positive / (true_positive + false_negative) if true_positive + false_negative else 0.0
        f1_scores.append(2 * precision * recall / (precision + recall) if precision + recall else 0.0)

    failed_examples = []
    for sample, expected, predicted, probability in zip(samples, truth, predictions, probabilities):
        if expected == predicted:
            continue
        failed_examples.append(
            {
                "image_path": sample.image_path.as_posix(),
                "actual": labels[expected],
                "predicted": labels[predicted],
                "confidence": max(probability),
                "split": sample.split,
            },
        )
        if len(failed_examples) >= max_failed_examples:
            break

    return {
        "accuracy": sum(1 for expected, predicted in zip(truth, predictions) if expected == predicted)
        / len(truth)
        if truth
        else 0.0,
        "macro_f1": sum(f1_scores) / len(f1_scores) if f1_scores else 0.0,
        "class_balance": dict(sorted(Counter(labels[index] for index in truth).items())),
        "confusion_matrix": {
            label: {labels[col]: matrix[row][col] for col in range(len(labels))}
            for row, label in enumerate(labels)
        },
        "failed_examples": failed_examples,
    }


def train_track(
    *,
    repo_root: Path,
    track: str,
    output_root: Path,
    epochs: int,
    max_samples_per_class: int | None,
    image_size: int,
    batch_size: int,
) -> dict[str, Any]:
    if track not in TRACKS:
        raise ValueError(f"Unsupported track: {track}")
    config = TRACKS[track]
    labels = list(config["labels"])
    manifest_paths = [repo_root / manifest for manifest in config["manifests"]]
    if config["sample_source"] == "annotation":
        samples = collect_annotation_samples(
            repo_root=repo_root,
            manifest_paths=manifest_paths,
            allowed_labels=labels,
        )
    else:
        samples = collect_image_samples(
            repo_root=repo_root,
            manifest_paths=manifest_paths,
            allowed_labels=labels,
        )
    samples = limit_samples_per_class(samples, max_samples_per_class)
    train_samples, valid_samples, test_samples = split_samples(samples)
    if not train_samples or not test_samples:
        raise ValueError(f"Track {track} does not have enough samples to train and test")

    seed_everything(17)
    device = select_device()
    model = SmallVisualClassifier(len(labels)).to(device)
    train_loader = DataLoader(
        ImageClassificationDataset(train_samples, labels, image_size),
        batch_size=batch_size,
        shuffle=True,
    )
    optimizer = torch.optim.Adam(model.parameters(), lr=0.001)
    loss_fn = nn.CrossEntropyLoss(weight=class_weights(train_samples, labels).to(device))
    history = []
    for epoch in range(epochs):
        model.train()
        total_loss = 0.0
        for images, targets in train_loader:
            images = images.to(device)
            targets = targets.to(device)
            optimizer.zero_grad()
            loss = loss_fn(model(images), targets)
            loss.backward()
            optimizer.step()
            total_loss += float(loss.item()) * images.shape[0]
        history.append({"epoch": epoch + 1, "loss": total_loss / len(train_samples)})

    test_metrics = evaluate_model(
        model=model,
        samples=test_samples,
        labels=labels,
        image_size=image_size,
        batch_size=batch_size,
        device=device,
    )
    valid_metrics = (
        evaluate_model(
            model=model,
            samples=valid_samples,
            labels=labels,
            image_size=image_size,
            batch_size=batch_size,
            device=device,
        )
        if valid_samples
        else None
    )

    run_dir = output_root / track
    run_dir.mkdir(parents=True, exist_ok=True)
    torch_model_path = run_dir / "model.pt"
    torchscript_path = run_dir / "model_torchscript.pt"
    mobile_path = run_dir / "model_mobile.ptl"
    torch.save(model.state_dict(), torch_model_path)
    export_model(model, labels, image_size, torchscript_path, mobile_path)

    summary = {
        "track": track,
        "labels": labels,
        "sample_source": config["sample_source"],
        "sample_counts": {
            "all": len(samples),
            "train": len(train_samples),
            "valid": len(valid_samples),
            "test": len(test_samples),
        },
        "class_balance": {
            "all": label_balance(samples),
            "train": label_balance(train_samples),
            "valid": label_balance(valid_samples),
            "test": label_balance(test_samples),
        },
        "history": history,
        "valid_metrics": valid_metrics,
        "test_metrics": test_metrics,
        "artifacts": {
            "torch_state_dict": torch_model_path.as_posix(),
            "torchscript": torchscript_path.as_posix(),
            "android_candidate": mobile_path.as_posix(),
            "android_candidate_format": "torchscript_lite",
            "tflite": None,
            "tflite_note": "TensorFlow/TFLite converter is not installed in this Python 3.14 environment.",
        },
    }
    write_json(run_dir / "metrics.json", summary)
    return summary


def limit_samples_per_class(
    samples: list[Sample],
    max_samples_per_class: int | None,
) -> list[Sample]:
    if max_samples_per_class is None:
        return samples
    counts: Counter[str] = Counter()
    limited = []
    for sample in samples:
        if counts[sample.label] >= max_samples_per_class:
            continue
        limited.append(sample)
        counts[sample.label] += 1
    return limited


def class_weights(samples: list[Sample], labels: list[str]) -> torch.Tensor:
    counts = Counter(sample.label for sample in samples)
    weights = [len(samples) / max(counts[label], 1) for label in labels]
    return torch.tensor(weights, dtype=torch.float32)


def evaluate_model(
    *,
    model: nn.Module,
    samples: list[Sample],
    labels: list[str],
    image_size: int,
    batch_size: int,
    device: torch.device,
) -> dict[str, Any]:
    loader = DataLoader(
        ImageClassificationDataset(samples, labels, image_size),
        batch_size=batch_size,
        shuffle=False,
    )
    truth: list[int] = []
    predictions: list[int] = []
    probabilities: list[list[float]] = []
    model.eval()
    with torch.no_grad():
        for images, targets in loader:
            logits = model(images.to(device))
            batch_probabilities = torch.softmax(logits, dim=1).cpu()
            truth.extend(int(target) for target in targets)
            predictions.extend(int(value) for value in batch_probabilities.argmax(dim=1))
            probabilities.extend(batch_probabilities.tolist())
    return compute_classification_metrics(
        labels=labels,
        samples=samples,
        truth=truth,
        predictions=predictions,
        probabilities=probabilities,
        max_failed_examples=25,
    )


def export_model(
    model: nn.Module,
    labels: list[str],
    image_size: int,
    torchscript_path: Path,
    mobile_path: Path,
) -> None:
    model = model.cpu().eval()
    example = torch.zeros(1, 3, image_size, image_size)
    scripted = torch.jit.trace(model, example)
    scripted.save(torchscript_path)
    try:
        from torch.utils.mobile_optimizer import optimize_for_mobile

        optimize_for_mobile(scripted).save(mobile_path)
    except Exception:
        scripted.save(mobile_path)
    write_json(
        torchscript_path.with_name("labels.json"),
        {"labels": labels, "input_shape": [1, 3, image_size, image_size]},
    )


def label_balance(samples: list[Sample]) -> dict[str, int]:
    return dict(sorted(Counter(sample.label for sample in samples).items()))


def seed_everything(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)


def select_device() -> torch.device:
    if torch.backends.mps.is_available():
        return torch.device("mps")
    if torch.cuda.is_available():
        return torch.device("cuda")
    return torch.device("cpu")


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train MelonSense baseline visual models.")
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    parser.add_argument("--track", choices=sorted(TRACKS), required=True)
    parser.add_argument("--output-root", type=Path, default=Path("training-runs") / "visual-baseline")
    parser.add_argument("--epochs", type=int, default=3)
    parser.add_argument("--max-samples-per-class", type=int)
    parser.add_argument("--image-size", type=int, default=IMAGE_SIZE)
    parser.add_argument("--batch-size", type=int, default=BATCH_SIZE)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    summary = train_track(
        repo_root=args.repo_root.resolve(),
        track=args.track,
        output_root=(args.repo_root / args.output_root).resolve()
        if not args.output_root.is_absolute()
        else args.output_root,
        epochs=args.epochs,
        max_samples_per_class=args.max_samples_per_class,
        image_size=args.image_size,
        batch_size=args.batch_size,
    )
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
