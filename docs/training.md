# Visual Baseline Training

Baseline training consumes local manifest files under `datasets/interim/` and
writes ignored artifacts under `training-runs/visual-baseline/`.

## Tracks

Sweetness uses image-level classification records from:

```text
datasets/interim/visual-sweetness-v0/manifest.jsonl
```

Allowed labels:

```text
not_sweet
sweet
```

Ripeness uses object-detection annotation crops from:

```text
datasets/interim/visual-ripeness-fyp-v0/manifest.jsonl
datasets/interim/visual-ripeness-saysay-v0/manifest.jsonl
```

Allowed labels:

```text
overripe
ripe
unripe
```

Android runtime ripeness uses full-image classification records from the same
ripeness manifests. It is currently binary because the public full-image labels
do not provide a usable overripe class:

```text
ripe
unripe
```

Records labeled `unknown`, `mixed`, `watermelon_detection_only`,
`watermelon_generic`, and `fruit_detection_other` are excluded from these two
baseline classifiers.

Both baseline tracks use deterministic leakage-safe train/valid/test splits.
The splitter groups records by source image first, then clusters exact or
near-duplicate images with perceptual hashes before assigning train,
validation, or test. This keeps annotation crops and augmented copies from the
same visual source in one split instead of leaking related images across
evaluation boundaries.

The run summary reports:

```text
duplicate_audit.cross_split_near_duplicate_count
scarce_grouped_class_counts
split_strategy
test_metrics.by_source_dataset
valid_metrics.by_source_dataset
metadata.git_commit
metadata.manifests[*].sha256
metadata.model_config
```

The duplicate audit currently compares pairs of unique images directly. That is
acceptable for the current local corpora and is reported as
`duplicate_audit.sampled_image_count`; if this grows into tens of thousands of
images, replace the direct comparison with a bucketed hash index before trusting
runtime.

Leakage-safe runs should report `split_strategy` as
`grouped_stratified_phash`. A plain `stratified` value means the run predates
the source-image and perceptual-hash grouping controls.

Scarce grouped classes are kept leakage-safe even when they cannot be balanced
across all splits. For the current public ripeness data, overripe has only a
small number of source-image groups, so the summary must be checked before
treating overripe validation/test metrics as representative.

## Commands

```sh
python3 -m tools.training.visual_baseline train \
  --track sweetness \
  --epochs 3
```

```sh
python3 -m tools.training.visual_baseline train \
  --track ripeness \
  --epochs 3
```

```sh
python3 -m tools.training.visual_baseline train \
  --track runtime_ripeness \
  --max-samples-per-class 1500 \
  --epochs 8
```

The default model is `--model-size strong`, a larger local PyTorch CNN with
batch normalization and dropout. Use `--model-size small` to reproduce the
original lightweight baseline.

The old command shape still works:

```sh
python3 -m tools.training.visual_baseline --track ripeness --epochs 3
```

To evaluate a future grocery-store phone-capture holdout manifest without
training on it:

```sh
python3 -m tools.training.visual_baseline train \
  --track sweetness \
  --holdout-manifest datasets/interim/phone-grocery-holdout-v0/manifest.jsonl
```

To write a labeled-only manifest from one or more source manifests:

```sh
python3 -m tools.training.visual_baseline labeled-manifest \
  --manifest datasets/interim/visual-ripeness-saysay-v0/manifest.jsonl \
  --output-manifest datasets/interim/visual-ripeness-saysay-labeled-v0/manifest.jsonl \
  --allowed-label overripe \
  --allowed-label ripe \
  --allowed-label unripe
```

To write crop-quality audit images for annotation-sourced tracks:

```sh
python3 -m tools.training.visual_baseline crop-audit \
  --track ripeness \
  --output-dir training-runs/visual-baseline/ripeness/crop-audit \
  --samples-per-label 24
```

## Picked-History Feedback

The Android app exports picked-history feedback as `manifest.jsonl` bundles.
Convert a bundle into an interim visual sweetness manifest before retraining:

```sh
python3 -m tools.training.picked_history_feedback \
  --export-manifest /path/to/training-exports/dataset-<timestamp>/manifest.jsonl \
  --output-manifest datasets/interim/picked-history-feedback-v0/manifest.jsonl
```

Then include the converted feedback in a sweetness run:

```sh
python3 -m tools.training.visual_baseline train \
  --track sweetness \
  --extra-manifest datasets/interim/picked-history-feedback-v0/manifest.jsonl
```

The converter only emits photo-backed records with app export
`schemaVersion=1` and `labelSource=user_feedback`. Sweetness ratings map to
the binary sweetness track as:

```text
Bland, Mild -> not_sweet
Good, Sweet, VerySweet -> sweet
```

Each run writes:

```text
training-runs/visual-baseline/<track>/metrics.json
training-runs/visual-baseline/<track>/model.pt
training-runs/visual-baseline/<track>/model_torchscript.pt
training-runs/visual-baseline/<track>/model_mobile.ptl
training-runs/visual-baseline/<track>/labels.json
```

`model_mobile.ptl` is the first Android-compatible candidate in this
environment. `metrics.json` reports whether that candidate was exported as
`torchscript_lite` or fell back to plain `torchscript` if mobile optimization
failed. TFLite export is not attempted unless TensorFlow or another TFLite
converter is available locally.

## Preprocessing Contract

Training and Android inference share this preprocessing contract:

```text
input layout: RGB CHW
normalization: float32 0..1
default image size: 96x96
crop: normalized left/top/right/bottom converted to pixel bounds
resize mode: nearest
```

Python and Android unit tests both use the same small golden RGB input to guard
against crop, channel-order, and normalization drift.
