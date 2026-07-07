import io
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

from tools.training import picked_history_feedback


class PickedHistoryFeedbackTest(unittest.TestCase):
    def test_convert_export_writes_visual_sweetness_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            manifest = root / "manifest.jsonl"
            photo = root / "media" / "42-photo.jpg"
            audio = root / "media" / "42-audio.pcm16.gz"
            photo.parent.mkdir()
            photo.write_bytes(b"photo")
            audio.write_bytes(b"audio")
            write_jsonl(
                manifest,
                [
                    export_record(
                        pick_id=42,
                        sweetness="VerySweet",
                        texture="Crisp",
                        artifacts=[
                            artifact("Photo", photo),
                            artifact("Audio", audio),
                        ],
                    ),
                ],
            )

            summary = picked_history_feedback.convert_feedback_export(
                export_manifest=manifest,
                output_manifest=root / "visual-feedback" / "manifest.jsonl",
            )

            records = read_jsonl(root / "visual-feedback" / "manifest.jsonl")
            self.assertEqual({"record_count": 1, "class_balance": {"sweet": 1}}, summary)
            self.assertEqual("sweet", records[0]["normalized_label"])
            self.assertEqual(["sweet"], records[0]["normalized_labels"])
            self.assertEqual(photo.as_posix(), records[0]["image_path"])
            self.assertEqual("picked_history_feedback", records[0]["source_dataset"])
            self.assertEqual("pick-history:42", records[0]["group_key"])
            self.assertEqual("user_feedback", records[0]["label_source"])
            self.assertEqual("VerySweet", records[0]["feedback_sweetness"])
            self.assertEqual("Crisp", records[0]["feedback_texture"])
            self.assertEqual(1_500, records[0]["created_at_millis"])
            self.assertEqual(74, records[0]["visual_score"])
            self.assertEqual(66, records[0]["visual_confidence_percent"])
            self.assertEqual(82, records[0]["audio_score"])
            self.assertEqual(91, records[0]["audio_confidence_percent"])
            self.assertEqual(3, records[0]["valid_knocks"])
            self.assertEqual(144, records[0]["estimated_frequency_hz"])
            self.assertEqual(79, records[0]["final_confidence_percent"])
            self.assertEqual("Exported", records[0]["training_export_status"])
            self.assertEqual("Exported", records[0]["training_capture_status"])
            self.assertEqual(10_000, records[0]["retention_expires_at_millis"])
            self.assertEqual(3_000, records[0]["export_created_at_millis"])
            self.assertEqual(photo.as_posix(), records[0]["artifact_exported_path"])
            self.assertEqual(photo.as_posix(), records[0]["artifact_source_path"])
            self.assertEqual("image/jpeg", records[0]["artifact_mime_type"])
            self.assertEqual(10, records[0]["artifact_byte_size"])
            self.assertEqual(1_000, records[0]["artifact_captured_at_millis"])
            self.assertEqual(1_100, records[0]["artifact_last_modified_at_millis"])
            self.assertEqual(640, records[0]["artifact_width"])
            self.assertEqual(480, records[0]["artifact_height"])

    def test_convert_export_writes_audio_feedback_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            manifest = root / "manifest.jsonl"
            audio = root / "media" / "42-audio.pcm16.gz"
            audio.parent.mkdir()
            audio.write_bytes(b"audio")
            write_jsonl(
                manifest,
                [
                    export_record(
                        pick_id=42,
                        sweetness="VerySweet",
                        texture="Crisp",
                        artifacts=[artifact("Audio", audio)],
                    ),
                ],
            )

            summary = picked_history_feedback.convert_audio_feedback_export(
                export_manifest=manifest,
                output_manifest=root / "audio-feedback" / "manifest.jsonl",
            )

            records = read_jsonl(root / "audio-feedback" / "manifest.jsonl")
            self.assertEqual({"record_count": 1, "class_balance": {"sweet": 1}}, summary)
            self.assertEqual(audio.as_posix(), records[0]["audio_path"])
            self.assertEqual("sweet", records[0]["normalized_label"])
            self.assertEqual(["sweet"], records[0]["normalized_labels"])
            self.assertEqual("Crisp", records[0]["feedback_texture"])
            self.assertEqual(82, records[0]["audio_score"])
            self.assertEqual(91, records[0]["audio_confidence_percent"])
            self.assertEqual(3, records[0]["valid_knocks"])
            self.assertEqual(144, records[0]["estimated_frequency_hz"])
            self.assertEqual("audio/pcm16+gzip", records[0]["artifact_mime_type"])

    def test_cli_writes_audio_manifest_when_photo_manifest_has_no_usable_records(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            manifest = root / "manifest.jsonl"
            audio = root / "media" / "42-audio.pcm16.gz"
            audio.parent.mkdir()
            audio.write_bytes(b"audio")
            write_jsonl(
                manifest,
                [
                    export_record(
                        pick_id=42,
                        sweetness="VerySweet",
                        texture="Crisp",
                        artifacts=[artifact("Audio", audio)],
                    ),
                ],
            )

            with redirect_stdout(io.StringIO()):
                exit_code = picked_history_feedback.main(
                    [
                        "--export-manifest",
                        manifest.as_posix(),
                        "--audio-output-manifest",
                        (root / "audio-feedback" / "manifest.jsonl").as_posix(),
                    ],
                )

            records = read_jsonl(root / "audio-feedback" / "manifest.jsonl")
            self.assertEqual(0, exit_code)
            self.assertEqual(audio.as_posix(), records[0]["audio_path"])

    def test_convert_export_resolves_pulled_bundle_media_paths(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            manifest = root / "manifest.jsonl"
            photo = root / "media" / "42-photo.jpg"
            device_photo = Path(
                "/data/user/0/com.ryacub.melonsense/files/training-exports/dataset-3000/media/42-photo.jpg",
            )
            photo.parent.mkdir()
            photo.write_bytes(b"photo")
            write_jsonl(
                manifest,
                [
                    export_record(
                        pick_id=42,
                        sweetness="Sweet",
                        texture="Crisp",
                        artifacts=[artifact("Photo", device_photo)],
                    ),
                ],
            )

            picked_history_feedback.convert_feedback_export(
                export_manifest=manifest,
                output_manifest=root / "manifest-out.jsonl",
            )

            records = read_jsonl(root / "manifest-out.jsonl")
            self.assertEqual(photo.as_posix(), records[0]["image_path"])
            self.assertEqual(device_photo.as_posix(), records[0]["artifact_exported_path"])

    def test_convert_export_maps_low_sweetness_to_not_sweet(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            manifest = root / "manifest.jsonl"
            photo = root / "media" / "7-photo.jpg"
            photo.parent.mkdir()
            photo.write_bytes(b"photo")
            write_jsonl(
                manifest,
                [
                    export_record(
                        pick_id=7,
                        sweetness="Mild",
                        texture="Soft",
                        artifacts=[artifact("Photo", photo)],
                    ),
                ],
            )

            picked_history_feedback.convert_feedback_export(
                export_manifest=manifest,
                output_manifest=root / "manifest-out.jsonl",
            )

            records = read_jsonl(root / "manifest-out.jsonl")
            self.assertEqual("not_sweet", records[0]["normalized_label"])

    def test_convert_export_rejects_empty_usable_feedback(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            manifest = root / "manifest.jsonl"
            write_jsonl(
                manifest,
                [
                    export_record(
                        pick_id=7,
                        sweetness="Sweet",
                        texture="Crisp",
                        artifacts=[artifact("Audio", root / "audio.gz")],
                    ),
                ],
            )

            with self.assertRaisesRegex(ValueError, "No usable photo feedback"):
                picked_history_feedback.convert_feedback_export(
                    export_manifest=manifest,
                    output_manifest=root / "manifest-out.jsonl",
                )

    def test_convert_export_skips_missing_photo_files(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            manifest = root / "manifest.jsonl"
            write_jsonl(
                manifest,
                [
                    export_record(
                        pick_id=7,
                        sweetness="Sweet",
                        texture="Crisp",
                        artifacts=[artifact("Photo", root / "missing.jpg")],
                    ),
                ],
            )

            with self.assertRaisesRegex(ValueError, "No usable photo feedback"):
                picked_history_feedback.convert_feedback_export(
                    export_manifest=manifest,
                    output_manifest=root / "manifest-out.jsonl",
                )


def export_record(
    *,
    pick_id: int,
    sweetness: str,
    texture: str,
    artifacts: list[dict],
) -> dict:
    return {
        "schemaVersion": 1,
        "labelSource": "user_feedback",
        "pickHistoryId": pick_id,
        "createdAtMillis": 1_500,
        "resultLabel": "GoodCandidate",
        "sweetness": sweetness,
        "texture": texture,
        "visualScore": 74,
        "visualConfidencePercent": 66,
        "audioScore": 82,
        "audioConfidencePercent": 91,
        "validKnocks": 3,
        "estimatedFrequencyHz": 144,
        "finalConfidencePercent": 79,
        "trainingExportStatus": "Exported",
        "trainingCaptureStatus": "Exported",
        "retentionExpiresAtMillis": 10_000,
        "exportCreatedAtMillis": 3_000,
        "artifacts": artifacts,
    }


def artifact(kind: str, path: Path) -> dict:
    return {
        "kind": kind,
        "path": path.as_posix(),
        "sourcePath": path.as_posix(),
        "mimeType": "image/jpeg" if kind == "Photo" else "audio/pcm16+gzip",
        "byteSize": 10,
        "capturedAtMillis": 1_000,
        "lastModifiedAtMillis": 1_100,
        "width": 640 if kind == "Photo" else None,
        "height": 480 if kind == "Photo" else None,
        "sampleRateHz": None,
        "durationMillis": None,
    }


def write_jsonl(path: Path, records: list[dict]) -> None:
    path.write_text("\n".join(json.dumps(record) for record in records) + "\n", encoding="utf-8")


def read_jsonl(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
