import json
import tempfile
import unittest
from pathlib import Path

from tools.training import visual_baseline


class VisualBaselineTest(unittest.TestCase):
    def test_collect_image_samples_filters_to_allowed_labels(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            repo_root = Path(tmp_dir)
            manifest = repo_root / "manifest.jsonl"
            write_manifest(
                manifest,
                [
                    {"image_path": "sweet.jpg", "normalized_label": "sweet", "split": "train"},
                    {"image_path": "unknown.jpg", "normalized_label": "unknown", "split": "train"},
                    {"image_path": "mixed.jpg", "normalized_label": "mixed", "split": "train"},
                    {"image_path": "not.jpg", "normalized_label": "not_sweet", "split": "test"},
                ],
            )

            samples = visual_baseline.collect_image_samples(
                repo_root=repo_root,
                manifest_paths=[manifest],
                allowed_labels=["not_sweet", "sweet"],
            )

            self.assertEqual(
                [
                    visual_baseline.Sample(
                        image_path=repo_root / "sweet.jpg",
                        label="sweet",
                        split="train",
                        crop=None,
                    ),
                    visual_baseline.Sample(
                        image_path=repo_root / "not.jpg",
                        label="not_sweet",
                        split="test",
                        crop=None,
                    ),
                ],
                samples,
            )

    def test_collect_annotation_samples_uses_detection_boxes_and_polygons(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            repo_root = Path(tmp_dir)
            manifest = repo_root / "manifest.jsonl"
            write_manifest(
                manifest,
                [
                    {
                        "image_path": "image.jpg",
                        "normalized_label": "mixed",
                        "split": "train",
                        "annotations": [
                            {
                                "normalized_label": "ripe",
                                "bbox_yolo": [0.5, 0.5, 0.25, 0.5],
                            },
                            {
                                "normalized_label": "watermelon_detection_only",
                                "bbox_yolo": [0.5, 0.5, 1.0, 1.0],
                            },
                            {
                                "normalized_label": "overripe",
                                "polygon_yolo": [0.1, 0.2, 0.4, 0.2, 0.4, 0.6, 0.1, 0.6],
                            },
                        ],
                    },
                ],
            )

            samples = visual_baseline.collect_annotation_samples(
                repo_root=repo_root,
                manifest_paths=[manifest],
                allowed_labels=["overripe", "ripe", "unripe"],
            )

            self.assertEqual(2, len(samples))
            self.assertEqual("ripe", samples[0].label)
            self.assertEqual((0.375, 0.25, 0.625, 0.75), samples[0].crop)
            self.assertEqual("overripe", samples[1].label)
            self.assertEqual((0.1, 0.2, 0.4, 0.6), samples[1].crop)

    def test_classification_metrics_include_macro_f1_confusion_and_failures(self) -> None:
        samples = [
            visual_baseline.Sample(Path("a.jpg"), "sweet", "test", None),
            visual_baseline.Sample(Path("b.jpg"), "sweet", "test", None),
            visual_baseline.Sample(Path("c.jpg"), "not_sweet", "test", None),
        ]

        metrics = visual_baseline.compute_classification_metrics(
            labels=["not_sweet", "sweet"],
            samples=samples,
            truth=[1, 1, 0],
            predictions=[1, 0, 0],
            probabilities=[
                [0.1, 0.9],
                [0.6, 0.4],
                [0.8, 0.2],
            ],
            max_failed_examples=5,
        )

        self.assertAlmostEqual(2 / 3, metrics["accuracy"])
        self.assertAlmostEqual(2 / 3, metrics["macro_f1"])
        self.assertEqual(
            {
                "not_sweet": {"not_sweet": 1, "sweet": 0},
                "sweet": {"not_sweet": 1, "sweet": 1},
            },
            metrics["confusion_matrix"],
        )
        self.assertEqual({"not_sweet": 1, "sweet": 2}, metrics["class_balance"])
        self.assertEqual(
            [
                {
                    "image_path": "b.jpg",
                    "actual": "sweet",
                    "predicted": "not_sweet",
                    "confidence": 0.6,
                    "split": "test",
                },
            ],
            metrics["failed_examples"],
        )


def write_manifest(path: Path, records: list[dict[str, object]]) -> None:
    path.write_text(
        "".join(json.dumps(record, sort_keys=True) + "\n" for record in records),
        encoding="utf-8",
    )


if __name__ == "__main__":
    unittest.main()
