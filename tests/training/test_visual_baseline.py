import json
import tempfile
import unittest
from pathlib import Path

import torch

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

    def test_stratified_split_balances_each_class_across_splits(self) -> None:
        samples = [
            visual_baseline.Sample(Path(f"{label}-{index}.jpg"), label, "train", None)
            for label in ["overripe", "ripe", "unripe"]
            for index in range(10)
        ]

        train, valid, test = visual_baseline.stratified_split_samples(
            samples,
            train_ratio=0.6,
            valid_ratio=0.2,
            seed=17,
        )

        self.assertEqual({"overripe": 6, "ripe": 6, "unripe": 6}, visual_baseline.label_balance(train))
        self.assertEqual({"overripe": 2, "ripe": 2, "unripe": 2}, visual_baseline.label_balance(valid))
        self.assertEqual({"overripe": 2, "ripe": 2, "unripe": 2}, visual_baseline.label_balance(test))
        self.assertEqual({"train"}, {sample.split for sample in train})
        self.assertEqual({"valid"}, {sample.split for sample in valid})
        self.assertEqual({"test"}, {sample.split for sample in test})

    def test_stratified_split_rejects_classes_too_small_for_all_splits(self) -> None:
        samples = [
            visual_baseline.Sample(Path(f"ripe-{index}.jpg"), "ripe", "train", None)
            for index in range(2)
        ]

        with self.assertRaisesRegex(ValueError, "ripe=2"):
            visual_baseline.stratified_split_samples(samples)

    def test_labeled_only_records_filter_unknown_image_and_annotation_labels(self) -> None:
        records = [
            {
                "image_path": "a.jpg",
                "normalized_label": "unknown",
                "annotations": [
                    {"normalized_label": "ripe", "bbox_yolo": [0.5, 0.5, 0.1, 0.1]},
                    {"normalized_label": "watermelon_detection_only", "bbox_yolo": [0.5, 0.5, 1.0, 1.0]},
                ],
            },
            {
                "image_path": "b.jpg",
                "normalized_label": "unknown",
                "annotations": [
                    {"normalized_label": "unknown", "bbox_yolo": [0.5, 0.5, 1.0, 1.0]},
                ],
            },
        ]

        labeled_records = visual_baseline.labeled_only_records(
            records,
            allowed_labels=["overripe", "ripe", "unripe"],
        )

        self.assertEqual(1, len(labeled_records))
        self.assertEqual("ripe", labeled_records[0]["normalized_label"])
        self.assertEqual(
            [{"normalized_label": "ripe", "bbox_yolo": [0.5, 0.5, 0.1, 0.1]}],
            labeled_records[0]["annotations"],
        )

    def test_crop_audit_plan_limits_examples_per_label(self) -> None:
        samples = [
            visual_baseline.Sample(Path(f"{label}-{index}.jpg"), label, "train", (0.0, 0.0, 1.0, 1.0))
            for label in ["overripe", "ripe"]
            for index in range(3)
        ]

        plan = visual_baseline.crop_audit_plan(samples, samples_per_label=2)

        self.assertEqual(
            {
                "overripe": [
                    visual_baseline.Sample(Path("overripe-0.jpg"), "overripe", "train", (0.0, 0.0, 1.0, 1.0)),
                    visual_baseline.Sample(Path("overripe-1.jpg"), "overripe", "train", (0.0, 0.0, 1.0, 1.0)),
                ],
                "ripe": [
                    visual_baseline.Sample(Path("ripe-0.jpg"), "ripe", "train", (0.0, 0.0, 1.0, 1.0)),
                    visual_baseline.Sample(Path("ripe-1.jpg"), "ripe", "train", (0.0, 0.0, 1.0, 1.0)),
                ],
            },
            plan,
        )

    def test_export_model_reports_android_candidate_format(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            output_dir = Path(tmp_dir)
            torchscript_path = output_dir / "model_torchscript.pt"
            mobile_path = output_dir / "model_mobile.ptl"

            result = visual_baseline.export_model(
                visual_baseline.SmallVisualClassifier(class_count=2),
                labels=["not_sweet", "sweet"],
                image_size=8,
                torchscript_path=torchscript_path,
                mobile_path=mobile_path,
            )

            self.assertTrue(torchscript_path.exists())
            self.assertTrue(mobile_path.exists())
            self.assertIn(result.android_candidate_format, {"torchscript_lite", "torchscript"})
            loaded = torch.jit.load(result.android_candidate_path)
            self.assertEqual((1, 2), tuple(loaded(torch.zeros(1, 3, 8, 8)).shape))


def write_manifest(path: Path, records: list[dict[str, object]]) -> None:
    path.write_text(
        "".join(json.dumps(record, sort_keys=True) + "\n" for record in records),
        encoding="utf-8",
    )


if __name__ == "__main__":
    unittest.main()
