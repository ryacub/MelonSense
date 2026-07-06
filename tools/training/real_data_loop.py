from __future__ import annotations

import argparse
import json
from collections.abc import Callable
from pathlib import Path
from typing import Any

from tools.training import picked_history_feedback, visual_baseline


DEFAULT_FEEDBACK_MANIFEST = Path("datasets/interim/picked-history-feedback-v0/manifest.jsonl")
DEFAULT_OUTPUT_ROOT = Path("training-runs/visual-baseline")


Converter = Callable[..., dict[str, Any]]
Trainer = Callable[..., dict[str, Any]]


def run_first_real_data_loop(
    *,
    export_manifest: Path,
    feedback_manifest: Path,
    repo_root: Path,
    output_root: Path,
    track: str = "sweetness",
    epochs: int = 3,
    max_samples_per_class: int | None = None,
    image_size: int = visual_baseline.IMAGE_SIZE,
    batch_size: int = visual_baseline.BATCH_SIZE,
    model_size: str = "strong",
    seed: int = 17,
    converter: Converter = picked_history_feedback.convert_feedback_export,
    trainer: Trainer = visual_baseline.train_track,
) -> dict[str, Any]:
    if track != "sweetness":
        raise ValueError("Only the sweetness track is supported for picked-history feedback")

    export_manifest = export_manifest.resolve()
    if not export_manifest.is_file():
        raise FileNotFoundError(f"Missing real app export manifest: {export_manifest}")

    repo_root = repo_root.resolve()
    feedback_manifest = resolve_path(repo_root, feedback_manifest)
    output_root = resolve_path(repo_root, output_root)
    feedback = converter(export_manifest=export_manifest, output_manifest=feedback_manifest)
    training = trainer(
        repo_root=repo_root,
        track=track,
        output_root=output_root,
        epochs=epochs,
        max_samples_per_class=max_samples_per_class,
        image_size=image_size,
        batch_size=batch_size,
        model_size=model_size,
        seed=seed,
        extra_manifest_paths=[feedback_manifest],
    )
    android_candidate = training.get("artifacts", {}).get("android_candidate")
    return {
        "export_manifest": export_manifest.as_posix(),
        "feedback_manifest": feedback_manifest.as_posix(),
        "feedback": feedback,
        "training": training,
        "android_candidate": android_candidate,
    }


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the first MelonSense real-data training loop.")
    parser.add_argument("--export-manifest", type=Path, required=True)
    parser.add_argument("--feedback-manifest", type=Path, default=DEFAULT_FEEDBACK_MANIFEST)
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    parser.add_argument("--output-root", type=Path, default=DEFAULT_OUTPUT_ROOT)
    parser.add_argument("--track", choices=["sweetness"], default="sweetness")
    parser.add_argument("--epochs", type=int, default=3)
    parser.add_argument("--max-samples-per-class", type=int)
    parser.add_argument("--image-size", type=int, default=visual_baseline.IMAGE_SIZE)
    parser.add_argument("--batch-size", type=int, default=visual_baseline.BATCH_SIZE)
    parser.add_argument("--model-size", choices=["small", "strong"], default="strong")
    parser.add_argument("--seed", type=int, default=17)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    summary = run_first_real_data_loop(
        export_manifest=args.export_manifest,
        feedback_manifest=args.feedback_manifest,
        repo_root=args.repo_root,
        output_root=args.output_root,
        track=args.track,
        epochs=args.epochs,
        max_samples_per_class=args.max_samples_per_class,
        image_size=args.image_size,
        batch_size=args.batch_size,
        model_size=args.model_size,
        seed=args.seed,
    )
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


def resolve_path(repo_root: Path, path: Path) -> Path:
    return path.resolve() if path.is_absolute() else (repo_root / path).resolve()


if __name__ == "__main__":
    raise SystemExit(main())
