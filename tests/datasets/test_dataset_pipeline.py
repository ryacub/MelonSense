import hashlib
import json
import tempfile
import unittest
from pathlib import Path

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


if __name__ == "__main__":
    unittest.main()
