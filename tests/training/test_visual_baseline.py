import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image
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
                        source_dataset="manifest",
                        group_key="manifest:sweet.jpg",
                    ),
                    visual_baseline.Sample(
                        image_path=repo_root / "not.jpg",
                        label="not_sweet",
                        split="test",
                        crop=None,
                        source_dataset="manifest",
                        group_key="manifest:not.jpg",
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
            self.assertEqual("manifest", samples[0].source_dataset)
            self.assertEqual("manifest:image.jpg", samples[0].group_key)
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

    def test_grouped_stratified_split_keeps_same_source_image_in_one_split(self) -> None:
        samples = [
            visual_baseline.Sample(Path("shared.jpg"), "ripe", "train", (0.0, 0.0, 0.5, 0.5), group_key="shared"),
            visual_baseline.Sample(Path("shared.jpg"), "ripe", "train", (0.5, 0.5, 1.0, 1.0), group_key="shared"),
            visual_baseline.Sample(Path("ripe-a.jpg"), "ripe", "train", None, group_key="ripe-a"),
            visual_baseline.Sample(Path("ripe-b.jpg"), "ripe", "train", None, group_key="ripe-b"),
            visual_baseline.Sample(Path("unripe-a.jpg"), "unripe", "train", None, group_key="unripe-a"),
            visual_baseline.Sample(Path("unripe-b.jpg"), "unripe", "train", None, group_key="unripe-b"),
            visual_baseline.Sample(Path("unripe-c.jpg"), "unripe", "train", None, group_key="unripe-c"),
        ]

        train, valid, test = visual_baseline.grouped_stratified_split_samples(
            samples,
            train_ratio=1 / 3,
            valid_ratio=1 / 3,
            seed=5,
        )

        split_by_group = {}
        for split_name, split_samples in [("train", train), ("valid", valid), ("test", test)]:
            for sample in split_samples:
                split_by_group.setdefault(sample.group_key, set()).add(split_name)
                self.assertEqual(split_name, sample.split)
        self.assertEqual({"shared": 1}, {"shared": len(split_by_group["shared"])})

    def test_grouped_stratified_split_keeps_scarce_grouped_class_in_train(self) -> None:
        samples = [
            visual_baseline.Sample(Path("overripe-a.jpg"), "overripe", "train", None, group_key="overripe-a"),
            visual_baseline.Sample(Path("overripe-b.jpg"), "overripe", "train", None, group_key="overripe-b"),
            visual_baseline.Sample(Path("ripe-a.jpg"), "ripe", "train", None, group_key="ripe-a"),
            visual_baseline.Sample(Path("ripe-b.jpg"), "ripe", "train", None, group_key="ripe-b"),
            visual_baseline.Sample(Path("ripe-c.jpg"), "ripe", "train", None, group_key="ripe-c"),
        ]

        train, valid, test = visual_baseline.grouped_stratified_split_samples(
            samples,
            train_ratio=1 / 3,
            valid_ratio=1 / 3,
            seed=5,
        )

        self.assertEqual(2, visual_baseline.label_balance(train)["overripe"])
        self.assertNotIn("overripe", visual_baseline.label_balance(valid))
        self.assertNotIn("overripe", visual_baseline.label_balance(test))

    def test_duplicate_audit_flags_near_duplicate_hashes_across_splits(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            repo_root = Path(tmp_dir)
            train_image = repo_root / "train.jpg"
            test_image = repo_root / "test.jpg"
            Image.new("RGB", (16, 16), (10, 220, 20)).save(train_image)
            Image.new("RGB", (16, 16), (12, 218, 22)).save(test_image)
            samples = [
                visual_baseline.Sample(train_image, "ripe", "train", None, group_key="train"),
                visual_baseline.Sample(test_image, "ripe", "test", None, group_key="test"),
            ]

            audit = visual_baseline.audit_split_duplicates(samples, image_size=16, hamming_threshold=4)

            self.assertEqual(1, audit["cross_split_near_duplicate_count"])
            self.assertEqual(["test", "train"], sorted(audit["cross_split_near_duplicates"][0]["splits"]))

    def test_perceptual_hash_grouping_assigns_near_duplicates_to_same_group(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            repo_root = Path(tmp_dir)
            first = repo_root / "first.jpg"
            second = repo_root / "second.jpg"
            Image.new("RGB", (16, 16), (10, 220, 20)).save(first)
            Image.new("RGB", (16, 16), (12, 218, 22)).save(second)

            grouped = visual_baseline.apply_perceptual_hash_groups(
                [
                    visual_baseline.Sample(first, "ripe", "train", None, group_key="first"),
                    visual_baseline.Sample(second, "ripe", "train", None, group_key="second"),
                ],
                image_size=16,
                hamming_threshold=4,
            )

            self.assertEqual(1, len({sample.group_key for sample in grouped}))

    def test_load_image_tensor_matches_android_preprocessing_golden_values(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            image_path = Path(tmp_dir) / "input.png"
            image = Image.new("RGB", (4, 4))
            for y in range(4):
                for x in range(4):
                    image.putpixel((x, y), (x * 10, y * 20, (x + y) * 30))
            image.save(image_path)

            tensor = visual_baseline.load_image_tensor(
                visual_baseline.Sample(
                    image_path=image_path,
                    label="sweet",
                    split="test",
                    crop=(0.0, 0.0, 0.5, 0.5),
                ),
                image_size=2,
            )

            self.assertEqual((3, 2, 2), tuple(tensor.shape))
            expected = [
                0.0 / 255.0,
                10.0 / 255.0,
                0.0 / 255.0,
                10.0 / 255.0,
                0.0 / 255.0,
                0.0 / 255.0,
                20.0 / 255.0,
                20.0 / 255.0,
                0.0 / 255.0,
                30.0 / 255.0,
                30.0 / 255.0,
                60.0 / 255.0,
            ]
            for actual, expected_value in zip(tensor.flatten().tolist(), expected):
                self.assertAlmostEqual(expected_value, actual, places=6)

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

    def test_metrics_report_dataset_breakdown(self) -> None:
        samples = [
            visual_baseline.Sample(Path("saysay-a.jpg"), "ripe", "test", None, source_dataset="saysay"),
            visual_baseline.Sample(Path("saysay-b.jpg"), "unripe", "test", None, source_dataset="saysay"),
            visual_baseline.Sample(Path("fyp-a.jpg"), "ripe", "test", None, source_dataset="fyp"),
        ]

        metrics = visual_baseline.compute_classification_metrics(
            labels=["ripe", "unripe"],
            samples=samples,
            truth=[0, 1, 0],
            predictions=[0, 0, 1],
            probabilities=[[0.9, 0.1], [0.7, 0.3], [0.4, 0.6]],
            max_failed_examples=5,
        )

        self.assertEqual(1 / 2, metrics["by_source_dataset"]["saysay"]["accuracy"])
        self.assertEqual(0.0, metrics["by_source_dataset"]["fyp"]["accuracy"])

    def test_run_metadata_includes_git_manifest_hashes_holdout_hashes_and_model_config(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            repo_root = Path(tmp_dir)
            manifest = repo_root / "manifest.jsonl"
            holdout_manifest = repo_root / "holdout.jsonl"
            write_manifest(manifest, [{"image_path": "a.jpg", "normalized_label": "sweet"}])
            write_manifest(holdout_manifest, [{"image_path": "b.jpg", "normalized_label": "sweet"}])

            metadata = visual_baseline.build_run_metadata(
                repo_root=repo_root,
                manifest_paths=[manifest],
                holdout_manifest_paths=[holdout_manifest],
                track="sweetness",
                seed=17,
                image_size=96,
                batch_size=32,
                epochs=3,
                model_size="strong",
                class_balance={"sweet": 1},
            )

            self.assertEqual("sweetness", metadata["track"])
            self.assertEqual("strong", metadata["model_config"]["model_size"])
            self.assertEqual("unavailable", metadata["git_commit"])
            self.assertEqual(64, len(metadata["manifests"][0]["sha256"]))
            self.assertEqual("training", metadata["manifests"][0]["role"])
            self.assertEqual("holdout", metadata["manifests"][1]["role"])

    def test_parse_image_holdout_manifest_collects_samples_without_training_split(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            repo_root = Path(tmp_dir)
            manifest = repo_root / "holdout.jsonl"
            write_manifest(
                manifest,
                [
                    {
                        "image_path": "phone.jpg",
                        "normalized_label": "sweet",
                        "source_dataset": "phone-grocery-holdout",
                    },
                ],
            )

            samples = visual_baseline.collect_holdout_samples(
                repo_root=repo_root,
                manifest_paths=[manifest],
                allowed_labels=["sweet", "not_sweet"],
                sample_source="image",
            )

            self.assertEqual(1, len(samples))
            self.assertEqual("holdout", samples[0].split)
            self.assertEqual("phone-grocery-holdout", samples[0].source_dataset)

    def test_parse_annotation_holdout_manifest_collects_crops_without_training_split(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            repo_root = Path(tmp_dir)
            manifest = repo_root / "holdout.jsonl"
            write_manifest(
                manifest,
                [
                    {
                        "image_path": "phone.jpg",
                        "normalized_label": "mixed",
                        "source_dataset": "phone-grocery-holdout",
                        "annotations": [
                            {"normalized_label": "ripe", "bbox_yolo": [0.5, 0.5, 0.25, 0.25]},
                        ],
                    },
                ],
            )

            samples = visual_baseline.collect_holdout_samples(
                repo_root=repo_root,
                manifest_paths=[manifest],
                allowed_labels=["ripe", "unripe"],
                sample_source="annotation",
            )

            self.assertEqual(1, len(samples))
            self.assertEqual("holdout", samples[0].split)
            self.assertEqual("ripe", samples[0].label)
            self.assertEqual((0.375, 0.375, 0.625, 0.625), samples[0].crop)

    def test_duplicate_audit_includes_holdout_split(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            repo_root = Path(tmp_dir)
            train_image = repo_root / "train.jpg"
            holdout_image = repo_root / "holdout.jpg"
            Image.new("RGB", (16, 16), (10, 220, 20)).save(train_image)
            Image.new("RGB", (16, 16), (12, 218, 22)).save(holdout_image)

            audit = visual_baseline.audit_split_duplicates(
                [
                    visual_baseline.Sample(train_image, "ripe", "train", None),
                    visual_baseline.Sample(holdout_image, "ripe", "holdout", None),
                ],
                image_size=16,
                hamming_threshold=4,
            )

            self.assertEqual(1, audit["cross_split_near_duplicate_count"])
            self.assertEqual(["holdout", "train"], sorted(audit["cross_split_near_duplicates"][0]["splits"]))


def write_manifest(path: Path, records: list[dict[str, object]]) -> None:
    path.write_text(
        "".join(json.dumps(record, sort_keys=True) + "\n" for record in records),
        encoding="utf-8",
    )


if __name__ == "__main__":
    unittest.main()
