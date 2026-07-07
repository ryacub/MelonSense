from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any


SCHEMA_VERSION = 1
LABEL_SOURCE = "user_feedback"
SOURCE_DATASET = "picked_history_feedback"
SWEETNESS_LABELS = {
    "Bland": "not_sweet",
    "Mild": "not_sweet",
    "Good": "sweet",
    "Sweet": "sweet",
    "VerySweet": "sweet",
}


def convert_feedback_export(
    *,
    export_manifest: Path,
    output_manifest: Path,
) -> dict[str, Any]:
    records = [
        record
        for export_record in read_jsonl(export_manifest)
        for record in export_record_to_visual_records(export_record, export_manifest=export_manifest)
    ]
    if not records:
        raise ValueError("No usable photo feedback records found in export manifest")

    output_manifest.parent.mkdir(parents=True, exist_ok=True)
    with output_manifest.open("w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, sort_keys=True))
            handle.write("\n")

    return {
        "record_count": len(records),
        "class_balance": dict(sorted(Counter(record["normalized_label"] for record in records).items())),
    }


def convert_audio_feedback_export(
    *,
    export_manifest: Path,
    output_manifest: Path,
) -> dict[str, Any]:
    records = [
        record
        for export_record in read_jsonl(export_manifest)
        for record in export_record_to_audio_records(export_record, export_manifest=export_manifest)
    ]
    if not records:
        raise ValueError("No usable audio feedback records found in export manifest")

    output_manifest.parent.mkdir(parents=True, exist_ok=True)
    with output_manifest.open("w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, sort_keys=True))
            handle.write("\n")

    return {
        "record_count": len(records),
        "class_balance": dict(sorted(Counter(record["normalized_label"] for record in records).items())),
    }


def export_record_to_visual_records(
    record: dict[str, Any],
    *,
    export_manifest: Path,
) -> list[dict[str, Any]]:
    if record.get("schemaVersion") != SCHEMA_VERSION or record.get("labelSource") != LABEL_SOURCE:
        return []
    sweetness = str(record.get("sweetness", ""))
    label = SWEETNESS_LABELS.get(sweetness)
    if label is None:
        return []

    pick_history_id = record.get("pickHistoryId")
    output_records: list[dict[str, Any]] = []
    for artifact in record.get("artifacts", []):
        if artifact.get("kind") != "Photo":
            continue
        image_path = resolve_artifact_path(artifact, export_manifest=export_manifest)
        if image_path is None:
            continue
        output_records.append(
            {
                "image_path": image_path.as_posix(),
                "normalized_label": label,
                "normalized_labels": [label],
                "source_dataset": SOURCE_DATASET,
                "source_id": SOURCE_DATASET,
                "task_type": "classification",
                "split": "train",
                "review_state": "user_labeled",
                "group_key": f"pick-history:{pick_history_id}",
                "label_source": LABEL_SOURCE,
                "pick_history_id": pick_history_id,
                "result_label": record.get("resultLabel"),
                "feedback_sweetness": sweetness,
                "feedback_texture": record.get("texture"),
                "created_at_millis": record.get("createdAtMillis"),
                "visual_score": record.get("visualScore"),
                "visual_confidence_percent": record.get("visualConfidencePercent"),
                "audio_score": record.get("audioScore"),
                "audio_confidence_percent": record.get("audioConfidencePercent"),
                "valid_knocks": record.get("validKnocks"),
                "estimated_frequency_hz": record.get("estimatedFrequencyHz"),
                "final_confidence_percent": record.get("finalConfidencePercent"),
                "training_export_status": record.get("trainingExportStatus"),
                "training_capture_status": record.get("trainingCaptureStatus"),
                "retention_expires_at_millis": record.get("retentionExpiresAtMillis"),
                "export_created_at_millis": record.get("exportCreatedAtMillis"),
                "artifact_exported_path": artifact.get("path"),
                "artifact_source_path": artifact.get("sourcePath"),
                "artifact_mime_type": artifact.get("mimeType"),
                "artifact_byte_size": artifact.get("byteSize"),
                "artifact_captured_at_millis": artifact.get("capturedAtMillis"),
                "artifact_last_modified_at_millis": artifact.get("lastModifiedAtMillis"),
                "artifact_width": artifact.get("width"),
                "artifact_height": artifact.get("height"),
            },
        )
    return output_records


def export_record_to_audio_records(
    record: dict[str, Any],
    *,
    export_manifest: Path,
) -> list[dict[str, Any]]:
    if record.get("schemaVersion") != SCHEMA_VERSION or record.get("labelSource") != LABEL_SOURCE:
        return []
    sweetness = str(record.get("sweetness", ""))
    label = SWEETNESS_LABELS.get(sweetness)
    if label is None:
        return []

    pick_history_id = record.get("pickHistoryId")
    output_records: list[dict[str, Any]] = []
    for artifact in record.get("artifacts", []):
        if artifact.get("kind") != "Audio":
            continue
        audio_path = resolve_artifact_path(artifact, export_manifest=export_manifest)
        if audio_path is None:
            continue
        output_records.append(
            {
                "audio_path": audio_path.as_posix(),
                "normalized_label": label,
                "normalized_labels": [label],
                "source_dataset": SOURCE_DATASET,
                "source_id": SOURCE_DATASET,
                "task_type": "audio_classification",
                "split": "train",
                "review_state": "user_labeled",
                "group_key": f"pick-history:{pick_history_id}",
                "label_source": LABEL_SOURCE,
                "pick_history_id": pick_history_id,
                "result_label": record.get("resultLabel"),
                "feedback_sweetness": sweetness,
                "feedback_texture": record.get("texture"),
                "created_at_millis": record.get("createdAtMillis"),
                "visual_score": record.get("visualScore"),
                "visual_confidence_percent": record.get("visualConfidencePercent"),
                "audio_score": record.get("audioScore"),
                "audio_confidence_percent": record.get("audioConfidencePercent"),
                "valid_knocks": record.get("validKnocks"),
                "estimated_frequency_hz": record.get("estimatedFrequencyHz"),
                "final_confidence_percent": record.get("finalConfidencePercent"),
                "training_export_status": record.get("trainingExportStatus"),
                "training_capture_status": record.get("trainingCaptureStatus"),
                "retention_expires_at_millis": record.get("retentionExpiresAtMillis"),
                "export_created_at_millis": record.get("exportCreatedAtMillis"),
                "artifact_exported_path": artifact.get("path"),
                "artifact_source_path": artifact.get("sourcePath"),
                "artifact_mime_type": artifact.get("mimeType"),
                "artifact_byte_size": artifact.get("byteSize"),
                "artifact_captured_at_millis": artifact.get("capturedAtMillis"),
                "artifact_last_modified_at_millis": artifact.get("lastModifiedAtMillis"),
                "artifact_sample_rate_hz": artifact.get("sampleRateHz"),
                "artifact_duration_millis": artifact.get("durationMillis"),
            },
        )
    return output_records


def resolve_artifact_path(
    artifact: dict[str, Any],
    *,
    export_manifest: Path,
) -> Path | None:
    raw_path = artifact.get("path")
    if not raw_path:
        return None
    artifact_path = Path(str(raw_path))
    if artifact_path.is_file():
        return artifact_path

    bundled_path = export_manifest.parent / "media" / artifact_path.name
    if bundled_path.is_file():
        return bundled_path
    return None


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Convert MelonSense picked-history export data into training manifests.")
    parser.add_argument("--export-manifest", type=Path, required=True)
    parser.add_argument("--output-manifest", type=Path)
    parser.add_argument("--audio-output-manifest", type=Path)
    args = parser.parse_args(argv)
    if args.output_manifest is None and args.audio_output_manifest is None:
        parser.error("at least one of --output-manifest or --audio-output-manifest is required")
    return args


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    summary: dict[str, Any] = {}
    if args.output_manifest is not None:
        summary = convert_feedback_export(
            export_manifest=args.export_manifest,
            output_manifest=args.output_manifest,
        )
    if args.audio_output_manifest is not None:
        summary["audio"] = convert_audio_feedback_export(
            export_manifest=args.export_manifest,
            output_manifest=args.audio_output_manifest,
        )
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
