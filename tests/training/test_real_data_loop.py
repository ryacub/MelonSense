import contextlib
import io
import tempfile
import unittest
from pathlib import Path

from tools.training import real_data_loop


class RealDataLoopTest(unittest.TestCase):
    def test_run_requires_real_export_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)

            with self.assertRaisesRegex(FileNotFoundError, "Missing real app export manifest"):
                real_data_loop.run_first_real_data_loop(
                    export_manifest=root / "missing" / "manifest.jsonl",
                    feedback_manifest=root / "datasets" / "interim" / "feedback.jsonl",
                    repo_root=root,
                    output_root=root / "training-runs",
                    converter=converter_should_not_run,
                    trainer=trainer_should_not_run,
                )

    def test_run_rejects_non_sweetness_tracks(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            export_manifest = root / "training-exports" / "dataset-1" / "manifest.jsonl"
            export_manifest.parent.mkdir(parents=True)
            export_manifest.write_text("{}\n", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "Only the sweetness track"):
                real_data_loop.run_first_real_data_loop(
                    export_manifest=export_manifest,
                    feedback_manifest=root / "datasets" / "interim" / "feedback.jsonl",
                    repo_root=root,
                    output_root=root / "training-runs",
                    track="ripeness",
                    converter=converter_should_not_run,
                    trainer=trainer_should_not_run,
                )

    def test_run_converts_feedback_then_trains_with_generated_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            export_manifest = root / "training-exports" / "dataset-1" / "manifest.jsonl"
            feedback_manifest = root / "datasets" / "interim" / "picked-history-feedback-v0" / "manifest.jsonl"
            output_root = root / "training-runs"
            export_manifest.parent.mkdir(parents=True)
            export_manifest.write_text("{}\n", encoding="utf-8")
            calls: list[tuple[str, dict]] = []

            def fake_converter(*, export_manifest: Path, output_manifest: Path) -> dict:
                calls.append(
                    (
                        "convert",
                        {
                            "export_manifest": export_manifest,
                            "output_manifest": output_manifest,
                        },
                    ),
                )
                return {"record_count": 2, "class_balance": {"sweet": 1, "not_sweet": 1}}

            def fake_trainer(**kwargs: object) -> dict:
                calls.append(("train", kwargs))
                return {
                    "track": kwargs["track"],
                    "artifacts": {
                        "android_candidate": (output_root / "sweetness" / "model_mobile.ptl").as_posix(),
                    },
                }

            summary = real_data_loop.run_first_real_data_loop(
                export_manifest=export_manifest,
                feedback_manifest=feedback_manifest,
                repo_root=root,
                output_root=output_root,
                converter=fake_converter,
                trainer=fake_trainer,
            )

            self.assertEqual(["convert", "train"], [call[0] for call in calls])
            self.assertEqual(export_manifest.resolve(), calls[0][1]["export_manifest"])
            self.assertEqual(feedback_manifest.resolve(), calls[0][1]["output_manifest"])
            self.assertEqual([feedback_manifest.resolve()], calls[1][1]["extra_manifest_paths"])
            self.assertEqual("sweetness", calls[1][1]["track"])
            self.assertEqual(output_root.resolve(), calls[1][1]["output_root"])
            self.assertEqual(3, calls[1][1]["epochs"])
            self.assertEqual("strong", calls[1][1]["model_size"])
            self.assertEqual(feedback_manifest.resolve().as_posix(), summary["feedback_manifest"])
            self.assertEqual(2, summary["feedback"]["record_count"])
            self.assertEqual(
                (output_root / "sweetness" / "model_mobile.ptl").as_posix(),
                summary["android_candidate"],
            )

    def test_parse_args_defaults_feedback_manifest(self) -> None:
        args = real_data_loop.parse_args(
            [
                "--export-manifest",
                "/tmp/melonsense-export/manifest.jsonl",
            ],
        )

        self.assertEqual(Path("datasets/interim/picked-history-feedback-v0/manifest.jsonl"), args.feedback_manifest)
        self.assertEqual(Path("training-runs/visual-baseline"), args.output_root)
        self.assertEqual("sweetness", args.track)

    def test_parse_args_rejects_non_sweetness_tracks(self) -> None:
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            real_data_loop.parse_args(
                [
                    "--export-manifest",
                    "/tmp/melonsense-export/manifest.jsonl",
                    "--track",
                    "ripeness",
                ],
            )


def converter_should_not_run(*, export_manifest: Path, output_manifest: Path) -> dict:
    raise AssertionError("converter should not run")


def trainer_should_not_run(**kwargs: object) -> dict:
    raise AssertionError("trainer should not run")
