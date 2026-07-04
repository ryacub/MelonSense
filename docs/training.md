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

## Commands

```sh
python3 -m tools.training.visual_baseline \
  --track sweetness \
  --epochs 3
```

```sh
python3 -m tools.training.visual_baseline \
  --track ripeness \
  --epochs 3
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
environment. TFLite export is not attempted unless TensorFlow or another TFLite
converter is available locally.
