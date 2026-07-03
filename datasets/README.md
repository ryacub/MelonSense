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
