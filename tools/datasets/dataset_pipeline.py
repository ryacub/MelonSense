from __future__ import annotations

import argparse
import ast
import hashlib
import json
import os
import shutil
import sys
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Any


METADATA_FILE = "source_metadata.json"
MANIFEST_FILE = "manifest.jsonl"
IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp"}


@dataclass(frozen=True)
class DatasetSource:
    source_id: str
    source_url: str
    roboflow_workspace: str
    roboflow_project: str
    roboflow_version: int
    license: str
    attribution: str
    source_labels: dict[str, str]
    task_type: str


APPROVED_SOURCES: dict[str, DatasetSource] = {
    "roboflow-capstone-sweetness": DatasetSource(
        source_id="roboflow-capstone-sweetness",
        source_url="https://universe.roboflow.com/capstonesementara/sweetness-watermelon",
        roboflow_workspace="capstonesementara",
        roboflow_project="sweetness-watermelon",
        roboflow_version=1,
        license="CC BY 4.0",
        attribution="Sweetness Watermelon dataset by capstonesementara on Roboflow Universe",
        source_labels={
            "manis": "sweet",
            "tidak_manis": "not_sweet",
        },
        task_type="classification",
    ),
    "roboflow-fyp-ripeness": DatasetSource(
        source_id="roboflow-fyp-ripeness",
        source_url="https://universe.roboflow.com/fyp-bkvhr/watermelon-ripeness-grading",
        roboflow_workspace="fyp-bkvhr",
        roboflow_project="watermelon-ripeness-grading",
        roboflow_version=7,
        license="CC BY 4.0",
        attribution="Watermelon Ripeness Grading dataset by FYP on Roboflow Universe",
        source_labels={
            "underripe": "unripe",
            "ripe": "ripe",
            "overripe": "overripe",
            "watermelon": "watermelon_detection_only",
        },
        task_type="classification_or_detection",
    ),
    "roboflow-saysay-ripe-unripe": DatasetSource(
        source_id="roboflow-saysay-ripe-unripe",
        source_url="https://universe.roboflow.com/saysayroboflow/watermelon-ripe-semiripe-unripe",
        roboflow_workspace="saysayroboflow",
        roboflow_project="watermelon-ripe-semiripe-unripe",
        roboflow_version=6,
        license="CC BY 4.0",
        attribution="Watermelon-Ripe-SemiRipe-UnRipe dataset by saysayroboflow on Roboflow Universe",
        source_labels={
            "Ripe": "ripe",
            "unripe": "unripe",
        },
        task_type="object_detection",
    ),
}


def stage_archive(
    *,
    repo_root: Path,
    source_id: str,
    archive_path: Path,
    downloaded_date: str | None = None,
) -> dict[str, Any]:
    source = require_approved_source(source_id)
    if not archive_path.is_file():
        raise FileNotFoundError(f"Archive does not exist: {archive_path}")

    raw_dir = raw_source_dir(repo_root, source_id)
    raw_dir.mkdir(parents=True, exist_ok=True)
    staged_archive = raw_dir / archive_path.name
    if archive_path.resolve() != staged_archive.resolve():
        shutil.copy2(archive_path, staged_archive)

    metadata = build_metadata(
        source=source,
        downloaded_date=downloaded_date,
        checksum_sha256=sha256_file(staged_archive),
    )
    write_json(raw_dir / METADATA_FILE, metadata)
    return metadata


def download_archive(
    *,
    repo_root: Path,
    source_id: str,
    download_url: str,
    archive_name: str | None = None,
    downloaded_date: str | None = None,
) -> dict[str, Any]:
    require_approved_source(source_id)
    raw_dir = raw_source_dir(repo_root, source_id)
    raw_dir.mkdir(parents=True, exist_ok=True)
    target_name = archive_name or Path(download_url.split("?", 1)[0]).name or f"{source_id}.zip"
    archive_path = raw_dir / target_name

    urllib.request.urlretrieve(download_url, archive_path)
    return stage_archive(
        repo_root=repo_root,
        source_id=source_id,
        archive_path=archive_path,
        downloaded_date=downloaded_date,
    )


def download_roboflow_export(
    *,
    repo_root: Path,
    source_id: str,
    api_key: str,
    format_name: str = "folder",
    archive_name: str | None = None,
    downloaded_date: str | None = None,
) -> dict[str, Any]:
    source = require_approved_source(source_id)
    if not api_key:
        raise ValueError("Roboflow API key is required")
    api_url = (
        "https://api.roboflow.com/{workspace}/{project}/{version}/{format_name}?api_key={api_key}"
    ).format(
        workspace=urllib.parse.quote(source.roboflow_workspace, safe=""),
        project=urllib.parse.quote(source.roboflow_project, safe=""),
        version=source.roboflow_version,
        format_name=urllib.parse.quote(format_name, safe=""),
        api_key=urllib.parse.quote(api_key, safe=""),
    )
    payload = read_url_json(api_url)
    export_link = payload.get("export", {}).get("link")
    if not export_link:
        raise ValueError("Roboflow export response did not include export.link")
    return download_archive(
        repo_root=repo_root,
        source_id=source_id,
        download_url=export_link,
        archive_name=archive_name or f"{source_id}.zip",
        downloaded_date=downloaded_date,
    )


def convert_classification_tree(
    *,
    repo_root: Path,
    source_id: str,
    dataset_version: str,
) -> dict[str, Any]:
    source = require_approved_source(source_id)
    raw_dir = raw_source_dir(repo_root, source_id)
    metadata = read_required_metadata(raw_dir)
    records = build_classification_records(repo_root, raw_dir, metadata, source)
    if not records:
        raise ValueError(f"No supported image files found under {raw_dir}")

    output_dir = repo_root / "datasets" / "interim" / dataset_version
    output_dir.mkdir(parents=True, exist_ok=True)
    manifest_file = output_dir / MANIFEST_FILE
    with manifest_file.open("w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, sort_keys=True))
            handle.write("\n")

    manifest_summary = {
        "dataset_version": dataset_version,
        "source_id": source_id,
        "manifest_path": manifest_file.as_posix(),
        "record_count": len(records),
    }
    write_json(output_dir / "manifest_metadata.json", manifest_summary)
    return manifest_summary


def convert_yolo_detection_tree(
    *,
    repo_root: Path,
    source_id: str,
    dataset_version: str,
) -> dict[str, Any]:
    source = require_approved_source(source_id)
    raw_dir = raw_source_dir(repo_root, source_id)
    metadata = read_required_metadata(raw_dir)
    class_names = read_yolo_class_names(raw_dir / "data.yaml")
    records = build_yolo_detection_records(repo_root, raw_dir, metadata, source, class_names)
    if not records:
        raise ValueError(f"No supported YOLO image files found under {raw_dir}")

    output_dir = repo_root / "datasets" / "interim" / dataset_version
    output_dir.mkdir(parents=True, exist_ok=True)
    manifest_file = output_dir / MANIFEST_FILE
    with manifest_file.open("w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, sort_keys=True))
            handle.write("\n")

    manifest_summary = {
        "dataset_version": dataset_version,
        "source_id": source_id,
        "manifest_path": manifest_file.as_posix(),
        "record_count": len(records),
    }
    write_json(output_dir / "manifest_metadata.json", manifest_summary)
    return manifest_summary


def manifest_stats(manifest_file: Path) -> dict[str, Any]:
    records = read_manifest_records(manifest_file)
    label_counter = Counter(record.get("normalized_label", "unknown") for record in records)
    unknown_source_labels = sorted(
        {
            str(record.get("source_label", "unknown"))
            for record in records
            if record.get("normalized_label") == "unknown"
        },
    )
    return {
        "manifest_path": manifest_file.as_posix(),
        "record_count": len(records),
        "class_balance": dict(sorted(label_counter.items())),
        "unknown_count": label_counter.get("unknown", 0),
        "unknown_source_labels": unknown_source_labels,
    }


def sample_audit(
    manifest_file: Path,
    samples_per_label: int = 5,
) -> dict[str, Any]:
    if samples_per_label < 1:
        raise ValueError("samples_per_label must be at least 1")
    samples: dict[str, list[str]] = defaultdict(list)
    for record in read_manifest_records(manifest_file):
        label = str(record.get("normalized_label", "unknown"))
        if len(samples[label]) >= samples_per_label:
            continue
        image_path = record.get("image_path")
        if isinstance(image_path, str):
            samples[label].append(image_path)
    return {
        "manifest_path": manifest_file.as_posix(),
        "samples_per_label": samples_per_label,
        "samples": dict(sorted(samples.items())),
    }


def validate_gitignore(repo_root: Path) -> None:
    gitignore = repo_root / ".gitignore"
    if not gitignore.exists():
        raise ValueError(".gitignore is missing")
    lines = {
        line.strip()
        for line in gitignore.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.strip().startswith("#")
    }
    required = {
        "/datasets/*",
        "!/datasets/README.md",
        "/training-runs/",
        "/models/local/",
    }
    missing = sorted(required - lines)
    if missing:
        raise ValueError(f".gitignore missing dataset safety rules: {', '.join(missing)}")


def require_approved_source(source_id: str) -> DatasetSource:
    try:
        return APPROVED_SOURCES[source_id]
    except KeyError as error:
        raise ValueError(f"Dataset source is not approved: {source_id}") from error


def raw_source_dir(
    repo_root: Path,
    source_id: str,
) -> Path:
    return repo_root / "datasets" / "raw" / source_id


def build_metadata(
    *,
    source: DatasetSource,
    downloaded_date: str | None,
    checksum_sha256: str,
) -> dict[str, Any]:
    return {
        "source_id": source.source_id,
        "source_url": source.source_url,
        "license": source.license,
        "attribution": source.attribution,
        "downloaded_date": downloaded_date or date.today().isoformat(),
        "checksum_sha256": checksum_sha256,
    }


def build_classification_records(
    repo_root: Path,
    raw_dir: Path,
    metadata: dict[str, Any],
    source: DatasetSource,
) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for image_file in sorted(raw_dir.rglob("*")):
        if not image_file.is_file() or image_file.suffix.lower() not in IMAGE_SUFFIXES:
            continue
        source_label = infer_label(raw_dir, image_file)
        normalized_label = source.source_labels.get(source_label, "unknown")
        records.append(
            {
                "source_id": source.source_id,
                "source_url": metadata["source_url"],
                "license": metadata["license"],
                "image_path": image_file.relative_to(repo_root).as_posix(),
                "annotation_path": None,
                "source_label": source_label,
                "normalized_label": normalized_label,
                "task_type": "classification",
                "attribution": metadata["attribution"],
                "review_state": "needs_sample_audit",
            },
        )
    return records


def build_yolo_detection_records(
    repo_root: Path,
    raw_dir: Path,
    metadata: dict[str, Any],
    source: DatasetSource,
    class_names: list[str],
) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for split in ("train", "valid", "validation", "test"):
        images_dir = raw_dir / split / "images"
        labels_dir = raw_dir / split / "labels"
        if not images_dir.exists():
            continue
        for image_file in sorted(images_dir.rglob("*")):
            if not image_file.is_file() or image_file.suffix.lower() not in IMAGE_SUFFIXES:
                continue
            label_file = labels_dir / f"{image_file.stem}.txt"
            annotations = read_yolo_annotations(label_file, class_names, source)
            normalized_labels = sorted(
                {annotation["normalized_label"] for annotation in annotations},
            )
            source_labels = sorted({annotation["source_label"] for annotation in annotations})
            records.append(
                {
                    "source_id": source.source_id,
                    "source_url": metadata["source_url"],
                    "license": metadata["license"],
                    "image_path": image_file.relative_to(repo_root).as_posix(),
                    "annotation_path": label_file.relative_to(repo_root).as_posix()
                    if label_file.exists()
                    else None,
                    "source_label": single_or_mixed(source_labels),
                    "normalized_label": single_or_mixed(normalized_labels),
                    "normalized_labels": normalized_labels,
                    "split": split,
                    "task_type": "object_detection",
                    "annotations": annotations,
                    "attribution": metadata["attribution"],
                    "review_state": "needs_sample_audit",
                },
            )
    return records


def read_yolo_class_names(data_yaml: Path) -> list[str]:
    if not data_yaml.exists():
        raise FileNotFoundError(f"Missing YOLO data.yaml: {data_yaml}")
    for line in data_yaml.read_text(encoding="utf-8").splitlines():
        if not line.strip().startswith("names:"):
            continue
        _, raw_names = line.split(":", 1)
        names = ast.literal_eval(raw_names.strip())
        if not isinstance(names, list) or not all(isinstance(name, str) for name in names):
            raise ValueError("YOLO data.yaml names must be a list of strings")
        return names
    raise ValueError(f"YOLO data.yaml missing names: {data_yaml}")


def read_yolo_annotations(
    label_file: Path,
    class_names: list[str],
    source: DatasetSource,
) -> list[dict[str, Any]]:
    if not label_file.exists():
        return []
    annotations: list[dict[str, Any]] = []
    with label_file.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                continue
            parts = line.split()
            if len(parts) < 5 or len(parts) % 2 == 0:
                raise ValueError(f"Invalid YOLO row in {label_file}:{line_number}")
            class_id = int(parts[0])
            if class_id < 0 or class_id >= len(class_names):
                raise ValueError(f"Unknown YOLO class id in {label_file}:{line_number}: {class_id}")
            source_label = class_names[class_id]
            annotation = {
                "class_id": class_id,
                "source_label": source_label,
                "normalized_label": source.source_labels.get(source_label, "unknown"),
            }
            coordinates = [float(value) for value in parts[1:]]
            if len(parts) == 5:
                annotation["bbox_yolo"] = coordinates
            else:
                annotation["polygon_yolo"] = coordinates
            annotations.append(annotation)
    return annotations


def single_or_mixed(labels: list[str]) -> str:
    if not labels:
        return "unknown"
    if len(labels) == 1:
        return labels[0]
    return "mixed"


def infer_label(
    raw_dir: Path,
    image_file: Path,
) -> str:
    relative_parts = image_file.relative_to(raw_dir).parts
    if len(relative_parts) < 2:
        return "unknown"
    parent = relative_parts[-2]
    split_names = {"train", "valid", "validation", "test"}
    return "unknown" if parent.lower() in split_names else parent


def read_required_metadata(raw_dir: Path) -> dict[str, Any]:
    metadata_file = raw_dir / METADATA_FILE
    if not metadata_file.exists():
        raise FileNotFoundError(f"Missing required metadata file: {metadata_file}")
    metadata = json.loads(metadata_file.read_text(encoding="utf-8"))
    required = {
        "source_id",
        "source_url",
        "license",
        "attribution",
        "downloaded_date",
        "checksum_sha256",
    }
    missing = sorted(required - metadata.keys())
    if missing:
        raise ValueError(f"Metadata missing required fields: {', '.join(missing)}")
    return metadata


def read_manifest_records(manifest_file: Path) -> list[dict[str, Any]]:
    if not manifest_file.exists():
        raise FileNotFoundError(f"Manifest does not exist: {manifest_file}")
    records: list[dict[str, Any]] = []
    with manifest_file.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                continue
            record = json.loads(line)
            if not isinstance(record, dict):
                raise ValueError(f"Manifest line {line_number} is not an object")
            records.append(record)
    return records


def read_url_json(url: str) -> dict[str, Any]:
    with urllib.request.urlopen(url) as response:
        payload = json.loads(response.read().decode("utf-8"))
    if not isinstance(payload, dict):
        raise ValueError("Expected JSON object response")
    return payload


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_json(
    path: Path,
    payload: dict[str, Any],
) -> None:
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Stage and normalize MelonSense training datasets.")
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    subparsers = parser.add_subparsers(dest="command", required=True)

    stage_parser = subparsers.add_parser("stage-archive")
    stage_parser.add_argument("--source-id", required=True)
    stage_parser.add_argument("--archive", type=Path, required=True)
    stage_parser.add_argument("--downloaded-date")

    download_parser = subparsers.add_parser("download-archive")
    download_parser.add_argument("--source-id", required=True)
    download_parser.add_argument("--download-url", required=True)
    download_parser.add_argument("--archive-name")
    download_parser.add_argument("--downloaded-date")

    roboflow_parser = subparsers.add_parser("download-roboflow")
    roboflow_parser.add_argument("--source-id", required=True)
    roboflow_parser.add_argument("--format", default="folder", dest="format_name")
    roboflow_parser.add_argument("--archive-name")
    roboflow_parser.add_argument("--downloaded-date")
    roboflow_parser.add_argument("--api-key-env", default="ROBOFLOW_API_KEY")

    convert_parser = subparsers.add_parser("convert-classification")
    convert_parser.add_argument("--source-id", required=True)
    convert_parser.add_argument("--dataset-version", required=True)

    yolo_parser = subparsers.add_parser("convert-yolo-detection")
    yolo_parser.add_argument("--source-id", required=True)
    yolo_parser.add_argument("--dataset-version", required=True)

    stats_parser = subparsers.add_parser("manifest-stats")
    stats_parser.add_argument("--manifest", type=Path, required=True)

    audit_parser = subparsers.add_parser("sample-audit")
    audit_parser.add_argument("--manifest", type=Path, required=True)
    audit_parser.add_argument("--samples-per-label", type=int, default=5)

    subparsers.add_parser("validate-gitignore")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    repo_root = args.repo_root.resolve()

    if args.command == "stage-archive":
        result = stage_archive(
            repo_root=repo_root,
            source_id=args.source_id,
            archive_path=args.archive,
            downloaded_date=args.downloaded_date,
        )
    elif args.command == "download-archive":
        result = download_archive(
            repo_root=repo_root,
            source_id=args.source_id,
            download_url=args.download_url,
            archive_name=args.archive_name,
            downloaded_date=args.downloaded_date,
        )
    elif args.command == "download-roboflow":
        api_key = os.environ.get(args.api_key_env, "")
        if not api_key:
            raise ValueError(f"Environment variable is missing: {args.api_key_env}")
        result = download_roboflow_export(
            repo_root=repo_root,
            source_id=args.source_id,
            api_key=api_key,
            format_name=args.format_name,
            archive_name=args.archive_name,
            downloaded_date=args.downloaded_date,
        )
    elif args.command == "convert-classification":
        result = convert_classification_tree(
            repo_root=repo_root,
            source_id=args.source_id,
            dataset_version=args.dataset_version,
        )
    elif args.command == "convert-yolo-detection":
        result = convert_yolo_detection_tree(
            repo_root=repo_root,
            source_id=args.source_id,
            dataset_version=args.dataset_version,
        )
    elif args.command == "manifest-stats":
        result = manifest_stats(args.manifest)
    elif args.command == "sample-audit":
        result = sample_audit(
            manifest_file=args.manifest,
            samples_per_label=args.samples_per_label,
        )
    elif args.command == "validate-gitignore":
        validate_gitignore(repo_root)
        result = {"ok": True}
    else:
        raise ValueError(f"Unsupported command: {args.command}")

    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
