# Local Dataset Storage

This directory is for local-only training data staging. Raw datasets, generated
splits, exports, and model-training artifacts must not be committed to git.

Tracked here:
- This README only.

Store local data under paths like:
- `datasets/raw/<source-id>/`
- `datasets/interim/<source-id>/`
- `datasets/processed/<dataset-version>/`

Required metadata for every local dataset copy:
- Source URL
- Download date
- License and attribution text
- Allowed use notes
- Original archive checksum, when available
- Local transforms applied

Do not use a dataset for training until `docs/datasets.md` marks it as approved
for the intended training phase.

## Procurement Commands

Run commands from the repo root.

Validate that raw training artifacts are ignored by git:

```sh
python3 -m tools.datasets.dataset_pipeline validate-gitignore
```

Stage a manually downloaded Roboflow export archive:

```sh
python3 -m tools.datasets.dataset_pipeline stage-archive \
  --source-id roboflow-capstone-sweetness \
  --archive /path/to/roboflow-export.zip \
  --downloaded-date 2026-07-03
```

Download and stage from a temporary export URL:

```sh
python3 -m tools.datasets.dataset_pipeline download-archive \
  --source-id roboflow-capstone-sweetness \
  --download-url "https://example.com/export.zip" \
  --archive-name roboflow-capstone-sweetness.zip
```

Convert a classification-style folder tree into the normalized manifest:

```sh
python3 -m tools.datasets.dataset_pipeline convert-classification \
  --source-id roboflow-capstone-sweetness \
  --dataset-version visual-sweetness-v0
```

Expected classification layout:

```text
datasets/raw/roboflow-capstone-sweetness/
  source_metadata.json
  train/manis/*.jpg
  train/tidak_manis/*.jpg
  valid/manis/*.jpg
  valid/tidak_manis/*.jpg
```

The generated manifest stays local under:

```text
datasets/interim/<dataset-version>/manifest.jsonl
```
