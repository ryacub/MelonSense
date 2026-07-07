# Dataset Qualification

Last reviewed: 2026-07-07

This document tracks candidate training data for MelonSense. It is a procurement
filter, not a download log. Raw data stays out of git under `datasets/`.

## Decision Rules

- Do not train on a dataset until its license and source are recorded here.
- Prefer datasets with explicit ripeness labels over generic watermelon images.
- Treat object detection labels as weak supervision for our final score labels.
- Do not mix public data with user-captured training exports without preserving
  source and consent metadata.
- Audio knock data is a separate track; visual datasets do not validate the knock
  model.
- Keep attribution text with every downloaded copy.

## Candidate Sources

| ID | Source | Type | License status | Labels | Fit | Decision |
| --- | --- | --- | --- | --- | --- | --- |
| `roboflow-fyp-ripeness` | https://universe.roboflow.com/fyp-bkvhr/watermelon-ripeness-grading | Watermelon object detection | CC BY 4.0 listed on source page | `ripe`, `Watermelon`, `overripe`, `underripe` | High semantic fit, small size: 73 images | Approved for prototype visual-ripeness experiments with attribution |
| `roboflow-saysay-ripe-unripe` | https://universe.roboflow.com/saysayroboflow/watermelon-ripe-semiripe-unripe | Watermelon object detection/model | CC BY 4.0 listed on source page | `Ripe`, `unripe`; source page lists 4,649 preview images and model dataset `/3` with 9,326 images; repo pipeline currently pins version `6` | Best current visual seed; label taxonomy needs audit because page shows 2 classes despite name saying semiripe | Approved for visual pretraining after class audit using the pinned pipeline version until a fresh export audit changes it |
| `roboflow-new-workspace-watermelon` | https://universe.roboflow.com/new-workspace-frsur/watermelon-piuio-e283h | Watermelon object detection | CC BY 4.0 listed on source page | `Ripe`, `Semi-Ripe`, `Un_Ripe`; source page lists 12,958 images and 2 dataset versions | Large watermelon ripeness-adjacent source, but no overripe class | Web-review candidate only; not in `APPROVED_SOURCES` and not approved as overripe data |
| `fruits-360` | https://github.com/Horea94/Fruit-Images-Dataset and https://data.mendeley.com/datasets/rp73yg93n8/3 | Fruit classification | GitHub mirror lists MIT license; Mendeley source should be checked before download | Fruit/vegetable classes including `Watermelon`; not ripeness | Useful for generic fruit/watermelon visual embedding only | Approved only for generic visual pretraining, not ripeness scoring |
| `lightly-fruits-detection` | https://github.com/lightly-ai/dataset_fruits_detection | Fruit object detection | CC0-1.0 listed on source page | YOLOv8 annotations for Apple, Grapes, Pineapple, Orange, Banana, Watermelon | Useful for watermelon detection/framing, not ripeness | Approved for detection pretraining |
| `xuebinjing-melon-ripeness` | https://github.com/XuebinJing/Melon-Ripeness-Detection | Melon ripeness detection | License not verified in source page reviewed; full dataset hosted via Baidu link | YOLO-format ripeness/melon annotations | Potentially useful but source/access/licensing are unclear | Needs permission/license verification before use |
| `ethz-watermelon-acoustic-paper` | https://www.research-collection.ethz.ch/server/api/core/bitstreams/faea1915-160d-4e1d-9cf7-35969137ac85/content | Research paper on knock acoustics | Paper is accessible; downloadable raw audio dataset not found in reviewed source | Ripe/unripe acoustic signals described in paper | Useful feature guidance, not directly trainable data | Use as modeling reference only |
| `roboflow-capstone-sweetness` | https://universe.roboflow.com/capstonesementara/sweetness-watermelon | Watermelon sweetness classification/model | CC BY 4.0 listed on source page | `manis`, `tidak_manis`; source page lists 700 images and dataset version with 500 images | Closest public visual proxy for sweetness; labels need translation and sample audit | Approved for visual sweetness experiments with attribution |

## Approved Initial Use

1. Use `roboflow-fyp-ripeness` to prototype crop-level visual ripeness with
   `overripe`, but do not treat it as enough for runtime overripe packaging.
2. Use `roboflow-saysay-ripe-unripe` to prototype binary visual ripeness.
3. Hold `roboflow-new-workspace-watermelon` as a web-review-only
   binary/semi-ripe candidate until a sample audit confirms label quality and
   the source is added to `APPROVED_SOURCES`.
4. Use `lightly-fruits-detection` to improve watermelon detection/framing.
5. Use `fruits-360` only if we need a broad fruit/watermelon visual baseline.
6. Use `roboflow-capstone-sweetness` as a separate visual sweetness proxy; do
   not merge it with ripeness labels until sample quality is reviewed.
7. Do not use `xuebinjing-melon-ripeness` until licensing and download access
   are verified.
8. Do not claim audio model training is covered by public data yet. Start audio
   with our app-collected knock recordings and user outcome ratings.

## Target Mapping To MelonSense

Public visual datasets should map into an interim schema before training:

```text
source_id
source_url
license
image_path
annotation_path
source_label
normalized_label
task_type
attribution
review_state
```

Recommended normalized labels:
- `unripe`
- `ripe`
- `overripe`
- `watermelon_detection_only`
- `unknown`

MelonSense app-collected exports remain the only current source for:
- sweetness rating
- texture rating
- knock audio artifact
- user-confirmed "picked this" outcome

App-collected exports are not a clean overripe source yet. Texture ratings such
as `Mushy` or `Soft` can identify candidates for manual review, but they should
not be auto-mapped to `overripe` for supervised training.

## Next Procurement Steps

1. Download only approved sources into `datasets/raw/<source-id>/`.
2. Save each source license and citation text beside the raw download.
3. Create a checksum file for each archive or exported folder.
4. Run a manual label audit on at least 50 samples per source before training.
5. Convert approved records into `datasets/interim/visual-ripeness-v0/`.
6. Keep all generated manifests out of git unless they contain metadata only and
   no raw image/audio paths that expose local machine details.

## Notes From Source Review

- Roboflow `watermelon ripeness grading` lists CC BY 4.0, 73 images, 7 dataset
  versions, and classes for ripe, watermelon, overripe, and underripe.
- Roboflow `Watermelon-Ripe-SemiRipe-UnRipe` lists CC BY 4.0, 4.6k images, 5
  dataset versions, 3 models, model dataset `/3` with 9,326 images, and two
  visible classes: Ripe and unripe. The repo pipeline still pins Roboflow
  version `6`; update that only after a fresh export audit.
- Roboflow `Watermelon` by `new-workspace-frsur` lists CC BY 4.0, 12,958
  images, 2 dataset versions, and classes Ripe, Semi-Ripe, and Un_Ripe.
  It is not overripe data and is not registered in `APPROVED_SOURCES`.
- Roboflow `Sweetness Watermelon` lists CC BY 4.0, 700 images, 4 dataset
  versions, one model, and two classes: `manis` and `tidak_manis`.
- Fruits-360 GitHub mirror lists 90,483 images, 131 fruit/vegetable classes,
  100x100 image size, and MIT license; it includes Watermelon but not
  watermelon ripeness labels.
- `lightly-ai/dataset_fruits_detection` lists 8,479 YOLOv8 fruit detection
  images and CC0-1.0 license.
- The acoustic paper describes collecting ripe/unripe thump audio and reports
  mobile-device classification, but the reviewed source did not expose raw audio
  files for download.
