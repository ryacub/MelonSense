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

Records labeled `unknown`, `mixed`, `watermelon_detection_only`,
`watermelon_generic`, and `fruit_detection_other` are excluded from these two
baseline classifiers.

Both baseline tracks use deterministic stratified train/valid/test splits so
each split keeps representation from every available class. Stratified training
requires at least three samples per class and fails fast when a class cannot
populate train, validation, and test. This intentionally overrides skewed
upstream split folders when their validation or test sets are not
class-balanced.

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

The default model is `--model-size strong`, a larger local PyTorch CNN with
batch normalization and dropout. Use `--model-size small` to reproduce the
original lightweight baseline.

The old command shape still works:

```sh
python3 -m tools.training.visual_baseline --track ripeness --epochs 3
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
