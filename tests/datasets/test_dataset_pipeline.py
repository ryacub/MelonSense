import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from tools.datasets import dataset_pipeline


class DatasetPipelineTest(unittest.TestCase):
    def test_stage_archive_requires_approved_source_and_writes_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            repo_root = Path(tmp_dir)
            archive = repo_root / "sweetness.zip"
            archive.write_bytes(b"fake archive")

            metadata = dataset_pipeline.stage_archive(
                repo_root=repo_root,
                source_id="roboflow-capstone-sweetness",
                archive_path=archive,
                downloaded_date="2026-07-03",
            )

            expected_checksum = hashlib.sha256(b"fake archive").hexdigest()
            raw_dir = repo_root / "datasets" / "raw" / "roboflow-capstone-sweetness"
            metadata_file = raw_dir / "source_metadata.json"
            staged_archive = raw_dir / "sweetness.zip"

            self.assertEqual("roboflow-capstone-sweetness", metadata["source_id"])
            self.assertEqual("CC BY 4.0", metadata["license"])
            self.assertEqual(expected_checksum, metadata["checksum_sha256"])
            self.assertTrue(staged_archive.exists())
            self.assertTrue(metadata_file.exists())
            self.assertEqual(metadata, json.loads(metadata_file.read_text(encoding="utf-8")))

    def test_stage_archive_rejects_unapproved_source(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            repo_root = Path(tmp_dir)
            archive = repo_root / "unknown.zip"
            archive.write_bytes(b"fake archive")

            with self.assertRaisesRegex(ValueError, "not approved"):
                dataset_pipeline.stage_archive(
                    repo_root=repo_root,
                    source_id="random-scrape",
                    archive_path=archive,
                    downloaded_date="2026-07-03",
                )

    def test_convert_classification_tree_writes_normalized_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            repo_root = Path(tmp_dir)
            raw_dir = repo_root / "datasets" / "raw" / "roboflow-capstone-sweetness"
            (raw_dir / "train" / "manis").mkdir(parents=True)
            (raw_dir / "valid" / "tidak_manis").mkdir(parents=True)
            sweet_image = raw_dir / "train" / "manis" / "sweet.jpg"
            not_sweet_image = raw_dir / "valid" / "tidak_manis" / "not_sweet.png"
            sweet_image.write_bytes(b"sweet")
            not_sweet_image.write_bytes(b"not sweet")
            (raw_dir / "source_metadata.json").write_text(
                json.dumps(
                    {
                        "source_id": "roboflow-capstone-sweetness",
                        "source_url": "https://universe.roboflow.com/capstonesementara/sweetness-watermelon",
                        "license": "CC BY 4.0",
                        "attribution": "Sweetness Watermelon dataset by capstonesementara on Roboflow Universe",
                        "downloaded_date": "2026-07-03",
                        "checksum_sha256": "abc123",
                    },
                ),
                encoding="utf-8",
            )

            manifest = dataset_pipeline.convert_classification_tree(
                repo_root=repo_root,
                source_id="roboflow-capstone-sweetness",
                dataset_version="visual-sweetness-v0",
            )

            manifest_file = repo_root / "datasets" / "interim" / "visual-sweetness-v0" / "manifest.jsonl"
            records = [json.loads(line) for line in manifest_file.read_text(encoding="utf-8").splitlines()]

            self.assertEqual(2, manifest["record_count"])
            self.assertEqual(2, len(records))
            self.assertEqual(
                {
                    "sweet",
                    "not_sweet",
                },
                {record["normalized_label"] for record in records},
            )
            self.assertEqual({"classification"}, {record["task_type"] for record in records})
            self.assertTrue(all(record["image_path"].startswith("datasets/raw/") for record in records))
            self.assertTrue(all(record["review_state"] == "needs_sample_audit" for record in records))

    def test_validate_gitignore_blocks_raw_dataset_artifacts(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            repo_root = Path(tmp_dir)
            (repo_root / ".gitignore").write_text(
                "\n".join(
                    [
                        "/datasets/*",
                        "!/datasets/README.md",
                        "/training-runs/",
                        "/models/local/",
                    ],
                ),
                encoding="utf-8",
            )

            dataset_pipeline.validate_gitignore(repo_root)

            (repo_root / ".gitignore").write_text("!/datasets/README.md\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "datasets"):
                dataset_pipeline.validate_gitignore(repo_root)

    def test_download_roboflow_export_uses_api_key_and_stages_returned_link(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            repo_root = Path(tmp_dir)

            with (
                mock.patch.object(
                    dataset_pipeline,
                    "read_url_json",
                    return_value={"export": {"link": "https://example.com/export.zip"}},
                ) as read_url_json,
                mock.patch.object(dataset_pipeline, "download_archive") as download_archive,
            ):
                download_archive.return_value = {"source_id": "roboflow-capstone-sweetness"}

                result = dataset_pipeline.download_roboflow_export(
                    repo_root=repo_root,
                    source_id="roboflow-capstone-sweetness",
                    api_key="secret-key",
                    format_name="folder",
                    archive_name="sweetness.zip",
                    downloaded_date="2026-07-03",
                )

            request_url = read_url_json.call_args.args[0]
            self.assertIn("capstonesementara/sweetness-watermelon/1/folder", request_url)
            self.assertIn("api_key=secret-key", request_url)
            download_archive.assert_called_once_with(
                repo_root=repo_root,
                source_id="roboflow-capstone-sweetness",
                download_url="https://example.com/export.zip",
                archive_name="sweetness.zip",
                downloaded_date="2026-07-03",
            )
            self.assertEqual({"source_id": "roboflow-capstone-sweetness"}, result)

    def test_manifest_stats_counts_labels_and_unknowns(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            manifest_file = Path(tmp_dir) / "manifest.jsonl"
            write_manifest_records(
                manifest_file,
                [
                    {"normalized_label": "sweet", "source_label": "manis"},
                    {"normalized_label": "not_sweet", "source_label": "tidak_manis"},
                    {"normalized_label": "not_sweet", "source_label": "tidak_manis"},
                    {"normalized_label": "unknown", "source_label": "unexpected"},
                ],
            )

            stats = dataset_pipeline.manifest_stats(manifest_file)

            self.assertEqual(4, stats["record_count"])
            self.assertEqual({"sweet": 1, "not_sweet": 2, "unknown": 1}, stats["class_balance"])
            self.assertEqual(1, stats["unknown_count"])
            self.assertEqual(["unexpected"], stats["unknown_source_labels"])

    def test_sample_audit_returns_examples_per_label(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            manifest_file = Path(tmp_dir) / "manifest.jsonl"
            write_manifest_records(
                manifest_file,
                [
                    {"normalized_label": "sweet", "image_path": "datasets/raw/source/train/manis/1.jpg"},
                    {"normalized_label": "sweet", "image_path": "datasets/raw/source/train/manis/2.jpg"},
                    {"normalized_label": "not_sweet", "image_path": "datasets/raw/source/train/tidak_manis/3.jpg"},
                ],
            )

            audit = dataset_pipeline.sample_audit(manifest_file, samples_per_label=1)

            self.assertEqual(
                {
                    "sweet": ["datasets/raw/source/train/manis/1.jpg"],
                    "not_sweet": ["datasets/raw/source/train/tidak_manis/3.jpg"],
                },
                audit["samples"],
            )

    def test_convert_yolo_detection_tree_writes_detection_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            repo_root = Path(tmp_dir)
            raw_dir = repo_root / "datasets" / "raw" / "roboflow-fyp-ripeness"
            (raw_dir / "train" / "images").mkdir(parents=True)
            (raw_dir / "train" / "labels").mkdir(parents=True)
            image = raw_dir / "train" / "images" / "melon.jpg"
            label = raw_dir / "train" / "labels" / "melon.txt"
            image.write_bytes(b"image")
            label.write_text("1 0.5 0.5 0.25 0.25\n", encoding="utf-8")
            (raw_dir / "data.yaml").write_text(
                "train: ../train/images\n"
                "val: ../valid/images\n"
                "test: ../test/images\n"
                "nc: 3\n"
                "names: ['overripe', 'ripe', 'underripe']\n",
                encoding="utf-8",
            )
            (raw_dir / "source_metadata.json").write_text(
                json.dumps(
                    {
                        "source_id": "roboflow-fyp-ripeness",
                        "source_url": "https://universe.roboflow.com/fyp-bkvhr/watermelon-ripeness-grading",
                        "license": "CC BY 4.0",
                        "attribution": "Watermelon Ripeness Grading dataset by FYP on Roboflow Universe",
                        "downloaded_date": "2026-07-03",
                        "checksum_sha256": "abc123",
                    },
                ),
                encoding="utf-8",
            )

            manifest = dataset_pipeline.convert_yolo_detection_tree(
                repo_root=repo_root,
                source_id="roboflow-fyp-ripeness",
                dataset_version="visual-ripeness-v0",
            )

            manifest_file = repo_root / "datasets" / "interim" / "visual-ripeness-v0" / "manifest.jsonl"
            records = [json.loads(line) for line in manifest_file.read_text(encoding="utf-8").splitlines()]

            self.assertEqual(1, manifest["record_count"])
            self.assertEqual("object_detection", records[0]["task_type"])
            self.assertEqual("train", records[0]["split"])
            self.assertEqual("ripe", records[0]["source_label"])
            self.assertEqual("ripe", records[0]["normalized_label"])
            self.assertEqual("datasets/raw/roboflow-fyp-ripeness/train/labels/melon.txt", records[0]["annotation_path"])
            self.assertEqual(
                [
                    {
                        "bbox_yolo": [0.5, 0.5, 0.25, 0.25],
                        "class_id": 1,
                        "normalized_label": "ripe",
                        "source_label": "ripe",
                    },
                ],
                records[0]["annotations"],
            )

    def test_convert_yolo_detection_tree_accepts_polygon_rows(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            repo_root = Path(tmp_dir)
            raw_dir = repo_root / "datasets" / "raw" / "roboflow-saysay-ripe-unripe"
            (raw_dir / "train" / "images").mkdir(parents=True)
            (raw_dir / "train" / "labels").mkdir(parents=True)
            image = raw_dir / "train" / "images" / "melon.jpg"
            label = raw_dir / "train" / "labels" / "melon.txt"
            image.write_bytes(b"image")
            label.write_text("0 0.1 0.2 0.3 0.4 0.5 0.6\n", encoding="utf-8")
            (raw_dir / "data.yaml").write_text(
                "train: ../train/images\n"
                "nc: 2\n"
                "names: ['Ripe', 'unripe']\n",
                encoding="utf-8",
            )
            (raw_dir / "source_metadata.json").write_text(
                json.dumps(
                    {
                        "source_id": "roboflow-saysay-ripe-unripe",
                        "source_url": "https://universe.roboflow.com/saysayroboflow/watermelon-ripe-semiripe-unripe",
                        "license": "CC BY 4.0",
                        "attribution": "Watermelon-Ripe-SemiRipe-UnRipe dataset by saysayroboflow on Roboflow Universe",
                        "downloaded_date": "2026-07-03",
                        "checksum_sha256": "abc123",
                    },
                ),
                encoding="utf-8",
            )

            dataset_pipeline.convert_yolo_detection_tree(
                repo_root=repo_root,
                source_id="roboflow-saysay-ripe-unripe",
                dataset_version="visual-ripeness-saysay-v0",
            )

            manifest_file = repo_root / "datasets" / "interim" / "visual-ripeness-saysay-v0" / "manifest.jsonl"
            records = [json.loads(line) for line in manifest_file.read_text(encoding="utf-8").splitlines()]

            self.assertEqual("Ripe", records[0]["source_label"])
            self.assertEqual("ripe", records[0]["normalized_label"])
            self.assertEqual(
                [0.1, 0.2, 0.3, 0.4, 0.5, 0.6],
                records[0]["annotations"][0]["polygon_yolo"],
            )

    def test_read_yolo_class_names_accepts_inline_yaml_list(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            data_yaml = Path(tmp_dir) / "data.yaml"
            data_yaml.write_text(
                "names: - Apple - Banana - Grape - Orange - Pineapple - Watermelon "
                "nc: 6 test: test/images train: train/images val: valid/images\n",
                encoding="utf-8",
            )

            names = dataset_pipeline.read_yolo_class_names(data_yaml)

            self.assertEqual(
                ["Apple", "Banana", "Grape", "Orange", "Pineapple", "Watermelon"],
                names,
            )


def write_manifest_records(
    manifest_file: Path,
    records: list[dict[str, object]],
) -> None:
    manifest_file.write_text(
        "".join(json.dumps(record, sort_keys=True) + "\n" for record in records),
        encoding="utf-8",
    )


if __name__ == "__main__":
    unittest.main()
