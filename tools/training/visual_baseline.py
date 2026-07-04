from __future__ import annotations

import argparse
import hashlib
import json
import random
import subprocess
import sys
from collections import Counter
from dataclasses import dataclass, replace
from itertools import combinations
from pathlib import Path
from typing import Any

import numpy as np
import torch
from PIL import Image, ImageOps
from torch import nn
from torch.utils.data import DataLoader, Dataset


IMAGE_SIZE = 96
BATCH_SIZE = 32
INPUT_LAYOUT = "rgb_chw"
NORMALIZATION = "float32_0_1"
RESIZE_MODE = "nearest"
RESAMPLE_FILTER = Image.Resampling.NEAREST
GROUPED_PHASH_SPLIT_STRATEGY = "grouped_stratified_phash"
TRACKS = {
    "sweetness": {
        "labels": ["not_sweet", "sweet"],
        "manifests": ["datasets/interim/visual-sweetness-v0/manifest.jsonl"],
        "sample_source": "image",
        "split_strategy": GROUPED_PHASH_SPLIT_STRATEGY,
    },
    "ripeness": {
        "labels": ["overripe", "ripe", "unripe"],
        "manifests": [
            "datasets/interim/visual-ripeness-fyp-v0/manifest.jsonl",
            "datasets/interim/visual-ripeness-saysay-v0/manifest.jsonl",
        ],
        "sample_source": "annotation",
        "split_strategy": GROUPED_PHASH_SPLIT_STRATEGY,
    },
    "runtime_ripeness": {
        "labels": ["ripe", "unripe"],
        "manifests": [
            "datasets/interim/visual-ripeness-fyp-v0/manifest.jsonl",
            "datasets/interim/visual-ripeness-saysay-v0/manifest.jsonl",
        ],
        "sample_source": "image",
        "split_strategy": GROUPED_PHASH_SPLIT_STRATEGY,
    },
}


@dataclass(frozen=True)
class Sample:
    image_path: Path
    label: str
    split: str
    crop: tuple[float, float, float, float] | None
    source_dataset: str = "unknown"
    group_key: str | None = None


@dataclass(frozen=True)
class ExportResult:
    torchscript_path: Path
    android_candidate_path: Path
    android_candidate_format: str
    mobile_export_error: str | None


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


class StrongVisualClassifier(nn.Module):
    def __init__(self, class_count: int) -> None:
        super().__init__()
        self.network = nn.Sequential(
            nn.Conv2d(3, 32, kernel_size=3, padding=1),
            nn.BatchNorm2d(32),
            nn.ReLU(),
            nn.Conv2d(32, 32, kernel_size=3, padding=1),
            nn.BatchNorm2d(32),
            nn.ReLU(),
            nn.MaxPool2d(2),
            nn.Dropout2d(0.05),
            nn.Conv2d(32, 64, kernel_size=3, padding=1),
            nn.BatchNorm2d(64),
            nn.ReLU(),
            nn.Conv2d(64, 64, kernel_size=3, padding=1),
            nn.BatchNorm2d(64),
            nn.ReLU(),
            nn.MaxPool2d(2),
            nn.Dropout2d(0.1),
            nn.Conv2d(64, 128, kernel_size=3, padding=1),
            nn.BatchNorm2d(128),
            nn.ReLU(),
            nn.AdaptiveAvgPool2d((1, 1)),
            nn.Flatten(),
            nn.Dropout(0.25),
            nn.Linear(128, class_count),
        )

    def forward(self, images: torch.Tensor) -> torch.Tensor:
        return self.network(images)


def create_model(model_size: str, class_count: int) -> nn.Module:
    if model_size == "small":
        return SmallVisualClassifier(class_count)
    if model_size == "strong":
        return StrongVisualClassifier(class_count)
    raise ValueError(f"Unsupported model size: {model_size}")


def collect_image_samples(
    *,
    repo_root: Path,
    manifest_paths: list[Path],
    allowed_labels: list[str],
) -> list[Sample]:
    allowed = set(allowed_labels)
    samples: list[Sample] = []
    for manifest_path in manifest_paths:
        for record in read_manifest_records([manifest_path]):
            label = str(record.get("normalized_label", "unknown"))
            if label not in allowed:
                continue
            image_path = str(record["image_path"])
            source_dataset = source_dataset_for_record(record, manifest_path, repo_root)
            samples.append(
                Sample(
                    image_path=repo_root / image_path,
                    label=label,
                    split=str(record.get("split", infer_split_from_path(image_path))),
                    crop=None,
                    source_dataset=source_dataset,
                    group_key=group_key_for_record(record, image_path, source_dataset),
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
    for manifest_path in manifest_paths:
        for record in read_manifest_records([manifest_path]):
            image_path = str(record["image_path"])
            split = str(record.get("split", infer_split_from_path(image_path)))
            source_dataset = source_dataset_for_record(record, manifest_path, repo_root)
            group_key = group_key_for_record(record, image_path, source_dataset)
            for annotation in record.get("annotations", []):
                label = str(annotation.get("normalized_label", "unknown"))
                if label not in allowed:
                    continue
                crop = annotation_crop(annotation)
                if crop is None:
                    continue
                samples.append(
                    Sample(
                        image_path=repo_root / image_path,
                        label=label,
                        split=split,
                        crop=crop,
                        source_dataset=source_dataset,
                        group_key=group_key,
                    ),
                )
    return samples


def collect_holdout_samples(
    *,
    repo_root: Path,
    manifest_paths: list[Path],
    allowed_labels: list[str],
    sample_source: str,
) -> list[Sample]:
    collector = collect_annotation_samples if sample_source == "annotation" else collect_image_samples
    return [
        replace(sample, split="holdout")
        for sample in collector(
            repo_root=repo_root,
            manifest_paths=manifest_paths,
            allowed_labels=allowed_labels,
        )
    ]


def source_dataset_for_record(record: dict[str, Any], manifest_path: Path, repo_root: Path) -> str:
    for key in ("source_dataset", "source_id", "dataset_id"):
        value = record.get(key)
        if value:
            return str(value)
    if manifest_path.parent == repo_root:
        return manifest_path.stem
    return manifest_path.parent.name


def group_key_for_record(record: dict[str, Any], image_path: str, source_dataset: str) -> str:
    for key in ("group_key", "source_image_id", "original_image_id", "original_image_path"):
        value = record.get(key)
        if value:
            return f"{source_dataset}:{value}"
    return f"{source_dataset}:{canonical_image_name(image_path)}"


def canonical_image_name(image_path: str) -> str:
    name = Path(image_path).name
    if ".rf." in name:
        return name.split(".rf.", maxsplit=1)[0]
    return name


def read_manifest_records(manifest_paths: list[Path]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for manifest_path in manifest_paths:
        with manifest_path.open("r", encoding="utf-8") as handle:
            for line in handle:
                if line.strip():
                    records.append(json.loads(line))
    return records


def labeled_only_records(
    records: list[dict[str, Any]],
    allowed_labels: list[str],
) -> list[dict[str, Any]]:
    allowed = set(allowed_labels)
    labeled: list[dict[str, Any]] = []
    for record in records:
        next_record = dict(record)
        annotations = record.get("annotations")
        if isinstance(annotations, list):
            kept_annotations = [
                dict(annotation)
                for annotation in annotations
                if str(annotation.get("normalized_label", "unknown")) in allowed
            ]
            if not kept_annotations:
                continue
            next_record["annotations"] = kept_annotations
            labels = sorted({str(annotation["normalized_label"]) for annotation in kept_annotations})
        else:
            label = str(record.get("normalized_label", "unknown"))
            if label not in allowed:
                continue
            labels = [label]

        next_record["normalized_labels"] = labels
        next_record["normalized_label"] = labels[0] if len(labels) == 1 else "mixed"
        labeled.append(next_record)
    return labeled


def write_manifest(path: Path, records: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, sort_keys=True) + "\n")


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
        return (
            assign_split(by_split["train"], "train"),
            assign_split(by_split["valid"], "valid"),
            assign_split(by_split["test"], "test"),
        )
    shuffled = list(samples)
    random.Random(17).shuffle(shuffled)
    valid_start = int(len(shuffled) * 0.7)
    test_start = int(len(shuffled) * 0.85)
    return (
        assign_split(shuffled[:valid_start], "train"),
        assign_split(shuffled[valid_start:test_start], "valid"),
        assign_split(shuffled[test_start:], "test"),
    )


def stratified_split_samples(
    samples: list[Sample],
    *,
    train_ratio: float = 0.7,
    valid_ratio: float = 0.15,
    seed: int = 17,
) -> tuple[list[Sample], list[Sample], list[Sample]]:
    by_label: dict[str, list[Sample]] = {}
    for sample in samples:
        by_label.setdefault(sample.label, []).append(sample)
    scarce_labels = {
        label: len(label_samples)
        for label, label_samples in by_label.items()
        if len(label_samples) < 3
    }
    if scarce_labels:
        raise ValueError(
            "Stratified split requires at least 3 samples per class for train/valid/test: "
            + ", ".join(f"{label}={count}" for label, count in sorted(scarce_labels.items()))
        )

    train: list[Sample] = []
    valid: list[Sample] = []
    test: list[Sample] = []
    rng = random.Random(seed)
    for label_samples in by_label.values():
        shuffled = list(label_samples)
        rng.shuffle(shuffled)
        train_count, valid_count = split_counts(
            len(shuffled),
            train_ratio=train_ratio,
            valid_ratio=valid_ratio,
        )
        train.extend(assign_split(shuffled[:train_count], "train"))
        valid.extend(assign_split(shuffled[train_count : train_count + valid_count], "valid"))
        test.extend(assign_split(shuffled[train_count + valid_count :], "test"))
    rng.shuffle(train)
    rng.shuffle(valid)
    rng.shuffle(test)
    return train, valid, test


def grouped_stratified_split_samples(
    samples: list[Sample],
    *,
    train_ratio: float = 0.7,
    valid_ratio: float = 0.15,
    seed: int = 17,
) -> tuple[list[Sample], list[Sample], list[Sample]]:
    groups: dict[str, list[Sample]] = {}
    for sample in samples:
        groups.setdefault(sample.group_key or sample.image_path.as_posix(), []).append(sample)

    groups_by_label: dict[str, list[list[Sample]]] = {}
    for group_samples in groups.values():
        label_counts = Counter(sample.label for sample in group_samples)
        group_label = sorted(label_counts.items(), key=lambda item: (-item[1], item[0]))[0][0]
        groups_by_label.setdefault(group_label, []).append(group_samples)

    train: list[Sample] = []
    valid: list[Sample] = []
    test: list[Sample] = []
    rng = random.Random(seed)
    for label_groups in groups_by_label.values():
        shuffled = list(label_groups)
        rng.shuffle(shuffled)
        if len(shuffled) < 3:
            train.extend(assign_split(flatten_groups(shuffled), "train"))
            continue
        train_count, valid_count = split_counts(
            len(shuffled),
            train_ratio=train_ratio,
            valid_ratio=valid_ratio,
        )
        train.extend(assign_split(flatten_groups(shuffled[:train_count]), "train"))
        valid.extend(assign_split(flatten_groups(shuffled[train_count : train_count + valid_count]), "valid"))
        test.extend(assign_split(flatten_groups(shuffled[train_count + valid_count :]), "test"))
    rng.shuffle(train)
    rng.shuffle(valid)
    rng.shuffle(test)
    return train, valid, test


def apply_perceptual_hash_groups(
    samples: list[Sample],
    *,
    image_size: int = 32,
    hamming_threshold: int = 8,
) -> list[Sample]:
    if not samples:
        return []

    entries: list[Sample] = []
    seen_paths: set[Path] = set()
    for sample in samples:
        if sample.image_path in seen_paths:
            continue
        seen_paths.add(sample.image_path)
        entries.append(sample)

    parent = list(range(len(entries)))

    def find(index: int) -> int:
        while parent[index] != index:
            parent[index] = parent[parent[index]]
            index = parent[index]
        return index

    def union(left: int, right: int) -> None:
        left_root = find(left)
        right_root = find(right)
        if left_root != right_root:
            parent[right_root] = left_root

    by_existing_group: dict[str, int] = {}
    for index, sample in enumerate(entries):
        existing_group = sample.group_key or sample.image_path.as_posix()
        if existing_group in by_existing_group:
            union(by_existing_group[existing_group], index)
        else:
            by_existing_group[existing_group] = index

    hashes = [perceptual_hash(sample, image_size=image_size) for sample in entries]
    for left, right in combinations(range(len(entries)), 2):
        if hamming_distance(hashes[left], hashes[right]) <= hamming_threshold:
            union(left, right)

    cluster_members: dict[int, list[str]] = {}
    for index, sample in enumerate(entries):
        cluster_members.setdefault(find(index), []).append(sample.group_key or sample.image_path.as_posix())
    cluster_key_by_root = {
        root: "phash:" + hashlib.sha1("|".join(sorted(members)).encode("utf-8")).hexdigest()
        for root, members in cluster_members.items()
    }
    cluster_key_by_path = {
        sample.image_path: cluster_key_by_root[find(index)]
        for index, sample in enumerate(entries)
    }
    return [
        replace(sample, group_key=cluster_key_by_path[sample.image_path])
        for sample in samples
    ]


def scarce_grouped_class_counts(samples: list[Sample]) -> dict[str, int]:
    groups: dict[str, list[Sample]] = {}
    for sample in samples:
        groups.setdefault(sample.group_key or sample.image_path.as_posix(), []).append(sample)

    groups_by_label: dict[str, int] = Counter()
    for group_samples in groups.values():
        label_counts = Counter(sample.label for sample in group_samples)
        group_label = sorted(label_counts.items(), key=lambda item: (-item[1], item[0]))[0][0]
        groups_by_label[group_label] += 1
    return dict(sorted((label, count) for label, count in groups_by_label.items() if count < 3))


def flatten_groups(groups: list[list[Sample]]) -> list[Sample]:
    return [sample for group in groups for sample in group]


def assign_split(samples: list[Sample], split: str) -> list[Sample]:
    return [replace(sample, split=split) for sample in samples]


def split_counts(
    sample_count: int,
    *,
    train_ratio: float,
    valid_ratio: float,
) -> tuple[int, int]:
    if sample_count <= 0:
        return 0, 0
    if sample_count == 1:
        return 1, 0
    if sample_count == 2:
        return 1, 0

    train_count = int(sample_count * train_ratio)
    valid_count = int(sample_count * valid_ratio)
    test_count = sample_count - train_count - valid_count
    if train_count == 0:
        train_count = 1
        test_count -= 1
    if valid_count == 0:
        valid_count = 1
        test_count -= 1
    if test_count == 0:
        test_count = 1
        train_count -= 1
    while train_count + valid_count + test_count > sample_count:
        if train_count >= valid_count and train_count > 1:
            train_count -= 1
        elif valid_count > 1:
            valid_count -= 1
        else:
            test_count -= 1
    return train_count, valid_count


def crop_audit_plan(
    samples: list[Sample],
    *,
    samples_per_label: int,
) -> dict[str, list[Sample]]:
    plan: dict[str, list[Sample]] = {}
    for sample in samples:
        label_samples = plan.setdefault(sample.label, [])
        if len(label_samples) >= samples_per_label:
            continue
        label_samples.append(sample)
    return dict(sorted(plan.items()))


def write_crop_audit_images(
    samples: list[Sample],
    *,
    output_dir: Path,
    image_size: int,
    samples_per_label: int,
) -> dict[str, list[str]]:
    written: dict[str, list[str]] = {}
    for label, label_samples in crop_audit_plan(samples, samples_per_label=samples_per_label).items():
        label_dir = output_dir / label
        label_dir.mkdir(parents=True, exist_ok=True)
        written[label] = []
        for index, sample in enumerate(label_samples):
            filename = f"{index:03d}-{sample.image_path.stem}.jpg"
            output_path = label_dir / filename
            save_sample_image(sample, output_path=output_path, image_size=image_size)
            written[label].append(output_path.as_posix())
    return written


def save_sample_image(sample: Sample, *, output_path: Path, image_size: int) -> None:
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
        image = image.resize((image_size, image_size), resample=RESAMPLE_FILTER)
        image.save(output_path, format="JPEG", quality=90)


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
        image = image.resize((image_size, image_size), resample=RESAMPLE_FILTER)
        array = np.asarray(image, dtype=np.float32) / 255.0
    return torch.from_numpy(array).permute(2, 0, 1)


def perceptual_hash(sample: Sample, *, image_size: int = 32) -> str:
    with Image.open(sample.image_path) as original:
        image = ImageOps.exif_transpose(original).convert("L")
        image = image.resize((image_size, image_size))
        array = np.asarray(image, dtype=np.float32)
    average = float(array.mean())
    bits = ["1" if value >= average else "0" for value in array.flatten()]
    return f"{int(''.join(bits), 2):0{image_size * image_size // 4}x}"


def hamming_distance(left: str, right: str) -> int:
    return (int(left, 16) ^ int(right, 16)).bit_count()


def audit_split_duplicates(
    samples: list[Sample],
    *,
    image_size: int = 32,
    hamming_threshold: int = 8,
) -> dict[str, Any]:
    by_image: dict[tuple[Path, str], Sample] = {}
    for sample in samples:
        by_image.setdefault((sample.image_path, sample.split), sample)

    entries = [
        {
            "image_path": sample.image_path.as_posix(),
            "split": sample.split,
            "source_dataset": sample.source_dataset,
            "group_key": sample.group_key or sample.image_path.as_posix(),
            "perceptual_hash": perceptual_hash(sample, image_size=image_size),
        }
        for sample in by_image.values()
    ]
    duplicates = []
    for left, right in combinations(entries, 2):
        if left["split"] == right["split"]:
            continue
        distance = hamming_distance(str(left["perceptual_hash"]), str(right["perceptual_hash"]))
        if distance <= hamming_threshold:
            duplicates.append(
                {
                    "left": left["image_path"],
                    "right": right["image_path"],
                    "splits": sorted({str(left["split"]), str(right["split"])}),
                    "hamming_distance": distance,
                    "left_group_key": left["group_key"],
                    "right_group_key": right["group_key"],
                }
            )
    return {
        "hash_algorithm": f"average_hash_{image_size}x{image_size}",
        "hamming_threshold": hamming_threshold,
        "sampled_image_count": len(entries),
        "cross_split_near_duplicate_count": len(duplicates),
        "cross_split_near_duplicates": duplicates[:50],
    }


def compute_classification_metrics(
    *,
    labels: list[str],
    samples: list[Sample],
    truth: list[int],
    predictions: list[int],
    probabilities: list[list[float]],
    max_failed_examples: int,
) -> dict[str, Any]:
    return compute_classification_metrics_internal(
        labels=labels,
        samples=samples,
        truth=truth,
        predictions=predictions,
        probabilities=probabilities,
        max_failed_examples=max_failed_examples,
        include_source_breakdown=True,
    )


def compute_classification_metrics_internal(
    *,
    labels: list[str],
    samples: list[Sample],
    truth: list[int],
    predictions: list[int],
    probabilities: list[list[float]],
    max_failed_examples: int,
    include_source_breakdown: bool,
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

    metrics = {
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
    if include_source_breakdown:
        metrics["by_source_dataset"] = source_dataset_metrics(
            labels=labels,
            samples=samples,
            truth=truth,
            predictions=predictions,
            probabilities=probabilities,
            max_failed_examples=max_failed_examples,
        )
    return metrics


def source_dataset_metrics(
    *,
    labels: list[str],
    samples: list[Sample],
    truth: list[int],
    predictions: list[int],
    probabilities: list[list[float]],
    max_failed_examples: int,
) -> dict[str, Any]:
    by_source: dict[str, list[int]] = {}
    for index, sample in enumerate(samples):
        by_source.setdefault(sample.source_dataset, []).append(index)
    return {
        source_dataset: compute_classification_metrics_internal(
            labels=labels,
            samples=[samples[index] for index in indices],
            truth=[truth[index] for index in indices],
            predictions=[predictions[index] for index in indices],
            probabilities=[probabilities[index] for index in indices],
            max_failed_examples=max_failed_examples,
            include_source_breakdown=False,
        )
        for source_dataset, indices in sorted(by_source.items())
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
    model_size: str,
    seed: int = 17,
    holdout_manifest_paths: list[Path] | None = None,
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
    samples = apply_perceptual_hash_groups(samples)
    if config.get("split_strategy") == GROUPED_PHASH_SPLIT_STRATEGY:
        train_samples, valid_samples, test_samples = grouped_stratified_split_samples(samples, seed=seed)
    else:
        train_samples, valid_samples, test_samples = split_samples(samples)
    if not train_samples or not test_samples:
        raise ValueError(f"Track {track} does not have enough samples to train and test")

    seed_everything(seed)
    device = select_device()
    model = create_model(model_size, len(labels)).to(device)
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
    holdout_samples = (
        collect_holdout_samples(
            repo_root=repo_root,
            manifest_paths=holdout_manifest_paths,
            allowed_labels=labels,
            sample_source=str(config["sample_source"]),
        )
        if holdout_manifest_paths
        else []
    )
    holdout_metrics = (
        evaluate_model(
            model=model,
            samples=holdout_samples,
            labels=labels,
            image_size=image_size,
            batch_size=batch_size,
            device=device,
        )
        if holdout_samples
        else None
    )

    run_dir = output_root / track
    run_dir.mkdir(parents=True, exist_ok=True)
    torch_model_path = run_dir / "model.pt"
    torchscript_path = run_dir / "model_torchscript.pt"
    mobile_path = run_dir / "model_mobile.ptl"
    torch.save(model.state_dict(), torch_model_path)
    export_result = export_model(model, labels, image_size, torchscript_path, mobile_path)

    summary = {
        "track": track,
        "labels": labels,
        "model_size": model_size,
        "sample_source": config["sample_source"],
        "split_strategy": config.get("split_strategy", "manifest"),
        "sample_counts": {
            "all": len(samples),
            "train": len(train_samples),
            "valid": len(valid_samples),
            "test": len(test_samples),
            "holdout": len(holdout_samples),
        },
        "class_balance": {
            "all": label_balance(samples),
            "train": label_balance(train_samples),
            "valid": label_balance(valid_samples),
            "test": label_balance(test_samples),
            "holdout": label_balance(holdout_samples),
        },
        "scarce_grouped_class_counts": scarce_grouped_class_counts(samples),
        "duplicate_audit": audit_split_duplicates(train_samples + valid_samples + test_samples + holdout_samples),
        "metadata": build_run_metadata(
            repo_root=repo_root,
            code_repo_root=code_repo_root(),
            manifest_paths=manifest_paths,
            holdout_manifest_paths=holdout_manifest_paths or [],
            track=track,
            seed=seed,
            image_size=image_size,
            batch_size=batch_size,
            epochs=epochs,
            model_size=model_size,
            class_balance=label_balance(samples),
        ),
        "history": history,
        "valid_metrics": valid_metrics,
        "test_metrics": test_metrics,
        "holdout_metrics": holdout_metrics,
        "artifacts": {
            "torch_state_dict": torch_model_path.as_posix(),
            "torchscript": export_result.torchscript_path.as_posix(),
            "android_candidate": export_result.android_candidate_path.as_posix(),
            "android_candidate_format": export_result.android_candidate_format,
            "mobile_export_error": export_result.mobile_export_error,
            "tflite": None,
            "tflite_note": "TensorFlow/TFLite converter is not installed in this Python 3.14 environment.",
        },
    }
    write_json(run_dir / "metrics.json", summary)
    return summary


def build_run_metadata(
    *,
    repo_root: Path,
    code_repo_root: Path | None = None,
    manifest_paths: list[Path],
    holdout_manifest_paths: list[Path] | None = None,
    track: str,
    seed: int,
    image_size: int,
    batch_size: int,
    epochs: int,
    model_size: str,
    class_balance: dict[str, int],
) -> dict[str, Any]:
    code_root = code_repo_root or repo_root
    return {
        "git_commit": git_commit(code_root),
        "git_dirty": git_dirty(code_root),
        "data_git_commit": git_commit(repo_root),
        "data_git_dirty": git_dirty(repo_root),
        "track": track,
        "seed": seed,
        "model_config": {
            "model_size": model_size,
            "image_size": image_size,
            "batch_size": batch_size,
            "epochs": epochs,
            "input_layout": INPUT_LAYOUT,
            "normalization": NORMALIZATION,
            "resize_mode": RESIZE_MODE,
        },
        "class_balance": class_balance,
        "manifests": [
            {
                "path": manifest_path.as_posix(),
                "role": "training",
                "sha256": sha256_file(manifest_path),
            }
            for manifest_path in manifest_paths
        ]
        + [
            {
                "path": manifest_path.as_posix(),
                "role": "holdout",
                "sha256": sha256_file(manifest_path),
            }
            for manifest_path in (holdout_manifest_paths or [])
        ],
    }


def code_repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def git_commit(repo_root: Path) -> str:
    try:
        return subprocess.check_output(
            ["git", "-C", repo_root.as_posix(), "rev-parse", "HEAD"],
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return "unavailable"


def git_dirty(repo_root: Path) -> bool | None:
    try:
        status = subprocess.check_output(
            ["git", "-C", repo_root.as_posix(), "status", "--short"],
            text=True,
            stderr=subprocess.DEVNULL,
        )
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None
    return bool(status.strip())


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


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
) -> ExportResult:
    model = model.cpu().eval()
    example = torch.zeros(1, 3, image_size, image_size)
    scripted = torch.jit.trace(model, example)
    scripted.save(torchscript_path)
    android_candidate_format = "torchscript_lite"
    mobile_export_error = None
    try:
        from torch.utils.mobile_optimizer import optimize_for_mobile

        optimize_for_mobile(scripted)._save_for_lite_interpreter(str(mobile_path))
    except Exception as error:
        android_candidate_format = "torchscript"
        mobile_export_error = str(error)
        scripted.save(mobile_path)
    write_json(
        torchscript_path.with_name("labels.json"),
        {"labels": labels, "input_shape": [1, 3, image_size, image_size]},
    )
    return ExportResult(
        torchscript_path=torchscript_path,
        android_candidate_path=mobile_path,
        android_candidate_format=android_candidate_format,
        mobile_export_error=mobile_export_error,
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
    subcommands = parser.add_subparsers(dest="command")

    train_parser = subcommands.add_parser("train", help="Train a visual model.")
    add_common_args(train_parser)
    train_parser.add_argument("--track", choices=sorted(TRACKS), required=True)
    train_parser.add_argument("--output-root", type=Path, default=Path("training-runs") / "visual-baseline")
    train_parser.add_argument("--epochs", type=int, default=3)
    train_parser.add_argument("--max-samples-per-class", type=int)
    train_parser.add_argument("--image-size", type=int, default=IMAGE_SIZE)
    train_parser.add_argument("--batch-size", type=int, default=BATCH_SIZE)
    train_parser.add_argument("--model-size", choices=["small", "strong"], default="strong")
    train_parser.add_argument("--seed", type=int, default=17)
    train_parser.add_argument("--holdout-manifest", type=Path, action="append")

    manifest_parser = subcommands.add_parser("labeled-manifest", help="Write a labeled-only manifest.")
    add_common_args(manifest_parser)
    manifest_parser.add_argument("--manifest", type=Path, action="append", required=True)
    manifest_parser.add_argument("--output-manifest", type=Path, required=True)
    manifest_parser.add_argument("--allowed-label", action="append", required=True)

    audit_parser = subcommands.add_parser("crop-audit", help="Write annotation crop audit images.")
    add_common_args(audit_parser)
    audit_parser.add_argument("--track", choices=sorted(TRACKS), required=True)
    audit_parser.add_argument("--output-dir", type=Path, required=True)
    audit_parser.add_argument("--image-size", type=int, default=IMAGE_SIZE)
    audit_parser.add_argument("--samples-per-label", type=int, default=24)

    if not argv or argv[0] not in {"train", "labeled-manifest", "crop-audit", "-h", "--help"}:
        argv = ["train", *argv]
    return parser.parse_args(argv)


def add_common_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    repo_root = args.repo_root.resolve()
    if args.command in {None, "train"}:
        summary = train_track(
            repo_root=repo_root,
            track=args.track,
            output_root=(repo_root / args.output_root).resolve()
            if not args.output_root.is_absolute()
            else args.output_root,
            epochs=args.epochs,
            max_samples_per_class=args.max_samples_per_class,
            image_size=args.image_size,
            batch_size=args.batch_size,
            model_size=args.model_size,
            seed=args.seed,
            holdout_manifest_paths=[
                (repo_root / manifest).resolve() if not manifest.is_absolute() else manifest
                for manifest in (args.holdout_manifest or [])
            ],
        )
        print(json.dumps(summary, indent=2, sort_keys=True))
        return 0
    if args.command == "labeled-manifest":
        manifest_paths = [
            (repo_root / manifest).resolve() if not manifest.is_absolute() else manifest
            for manifest in args.manifest
        ]
        output_manifest = (
            (repo_root / args.output_manifest).resolve()
            if not args.output_manifest.is_absolute()
            else args.output_manifest
        )
        records = labeled_only_records(
            read_manifest_records(manifest_paths),
            allowed_labels=args.allowed_label,
        )
        write_manifest(output_manifest, records)
        print(json.dumps({"output_manifest": output_manifest.as_posix(), "records": len(records)}, sort_keys=True))
        return 0
    if args.command == "crop-audit":
        config = TRACKS[args.track]
        if config["sample_source"] != "annotation":
            raise ValueError("crop-audit only supports annotation-sourced tracks")
        labels = list(config["labels"])
        manifest_paths = [repo_root / manifest for manifest in config["manifests"]]
        samples = collect_annotation_samples(
            repo_root=repo_root,
            manifest_paths=manifest_paths,
            allowed_labels=labels,
        )
        output_dir = (repo_root / args.output_dir).resolve() if not args.output_dir.is_absolute() else args.output_dir
        written = write_crop_audit_images(
            samples,
            output_dir=output_dir,
            image_size=args.image_size,
            samples_per_label=args.samples_per_label,
        )
        print(json.dumps({"output_dir": output_dir.as_posix(), "written": written}, indent=2, sort_keys=True))
        return 0
    raise ValueError(f"Unsupported command: {args.command}")


if __name__ == "__main__":
    raise SystemExit(main())
