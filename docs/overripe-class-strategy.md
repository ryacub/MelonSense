# Overripe Class Strategy

Goal 13 decides whether `overripe` should enter the Android runtime model now.
Decision: keep Android runtime ripeness binary for now.

## Source Review

Reviewed on 2026-07-07:

| Source | Current finding | Use |
| --- | --- | --- |
| `roboflow-fyp-ripeness` | Roboflow lists 73 images, CC BY 4.0, and classes `ripe`, `Watermelon`, `overripe`, `underripe`. | Usable for prototype crop-level overripe experiments only. Too small for packaged runtime. |
| `roboflow-saysay-ripe-unripe` | Roboflow lists 4,649 images on the project page, model dataset `/3` with 9,326 images, CC BY 4.0, and visible classes `Ripe`, `unripe`. Repo pipeline currently qualifies version `6`; keep that pinned until a fresh export audit updates `tools/datasets/dataset_pipeline.py`. | Stronger binary ripe/unripe source. No usable overripe class. |
| `roboflow-new-workspace-watermelon` | Roboflow lists 12,958 images, CC BY 4.0, and classes `Ripe`, `Semi-Ripe`, `Un_Ripe`. This source is not in `APPROVED_SOURCES` yet. | Web-review candidate only until license/sample audit and pipeline registration. No overripe class. |
| App picked-history exports | Current UX records sweetness, texture, visual/audio scores, result label, and media. | Not a supervised overripe source. `Mushy` or `Soft` texture can queue candidate review, but should not auto-label overripe. |

## Runtime Decision

Do not add `overripe` to `app/src/main/assets/models/visual-models.json` yet.

Reasons:

- Current packaged runtime ripeness uses full-frame labels `ripe` and `unripe`.
- The only verified watermelon overripe source is small and object-detection
  oriented.
- Current local ripeness metrics are already weak; adding a scarce third class
  would make runtime confidence worse, not better.
- User feedback currently avoids extra outcome fields, so the app does not
  collect a clean overripe label.

The existing Android scorer already maps a future `overripe` model label to a
low pick score. That compatibility stays in place, but the packaged catalog
should remain binary until the data gate below is met.

## Data Gate

Add `overripe` back to runtime scoring only after all of these are true:

- At least 100 audited full-frame source-image groups per class:
  `overripe`, `ripe`, and `unripe`.
- At least 30 physical-phone holdout captures per class, from grocery-store or
  home-captured melons, excluded from training.
- At least two non-app public sources or one public source plus app-captured
  audited examples for each class.
- Manual audit passes on at least 50 samples per source before training.
- `grouped_stratified_phash` split reports no cross-split near duplicates.
- Runtime-ripeness validation and holdout macro F1 are both at least `0.70`,
  with no class recall below `0.60`.
- Overripe errors are biased conservative: an overripe melon should not commonly
  become `Strong Pick`.

## App-Collected Path

Keep the low-friction UX unchanged. Do not add a required overripe picker to the
history edit screen.

Instead:

1. Use `texture=Mushy` or `texture=Soft`, `resultLabel=Skip` or `Maybe`, and low
   final score as candidate-review filters.
2. Export those records with media.
3. Manually audit candidates outside the app into a separate overripe manifest.
4. Train and evaluate overripe only from audited labels, not raw texture ratings.

## Next Implementation Hook

When the gate is met:

1. Train `runtime_ripeness` with labels `overripe`, `ripe`, `unripe`.
2. Package a new versioned ripeness model asset.
3. Update `visual-models.json` labels in emitted training order.
4. Keep `LocalVisualModelScorer` conservative for `overripe`.
5. Run Android unit tests, packaged model smoke checks, and physical-device QA.
