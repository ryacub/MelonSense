# Model Quality Evaluation

Goal 11 evaluates the latest visual/audio outputs and threshold behavior after
the first emulator-driven picked-history export.

## Latest Sweetness Run

Command:

```sh
python3 -m tools.training.real_data_loop \
  --export-manifest /tmp/melonsense-export-pull/dataset-1783400945486/manifest.jsonl \
  --epochs 1 \
  --max-samples-per-class 50
```

Summary:

```text
feedback.record_count: 1
feedback.class_balance: {"sweet": 1}
sample_counts.all: 100
test_metrics.accuracy: 0.4666666666666667
test_metrics.macro_f1: 0.3181818181818182
valid_metrics.accuracy: 0.46153846153846156
valid_metrics.macro_f1: 0.3157894736842105
android_candidate: training-runs/visual-baseline/sweetness/model_mobile.ptl
android_candidate_format: torchscript_lite
```

The retrained sweetness model is not packageable. It predicted every validation
and test sample as `sweet`, producing zero true negatives for `not_sweet`.
The single emulator-generated feedback sample proves the pipeline, but it is
not representative grocery-store training data.

## Ripeness State

The latest checked local ripeness metrics are also not strong enough to justify
raising confidence in visual-only decisions:

```text
test_metrics.accuracy: 0.16666666666666666
test_metrics.macro_f1: 0.1507936507936508
```

Runtime ripeness remains binary in-app because the current full-frame labels do
not support a reliable overripe class.

## Threshold Decision

Keep result-label thresholds unchanged for now:

```text
Strong Pick: combined score >= 85
Good Candidate: combined score >= 70
Maybe: combined score >= 55
Skip: combined score < 55
```

Those thresholds are only defensible if upstream visual scores are not inflated
by low-confidence model labels. The app now converts max-softmax confidence
into certainty above the random baseline before moving each local visual track
score away from neutral:

```text
random_baseline = 100 / label_count
certainty = (confidence_percent - random_baseline) / (100 - random_baseline)
adjusted = 50 + ((raw_label_score - 50) * certainty)
```

This keeps a low-confidence positive label from carrying a high pick score while
preserving strong positive scores when confidence is actually high.

## Packaging Decision

Do not replace packaged Android models with the latest emulator-feedback
candidate.

Reasons:

- The feedback sample is synthetic emulator media, not real picker data.
- The sweetness run predicts all validation/test samples as `sweet`.
- No phone-captured grocery-store holdout exists yet.
- The model would make recommendations look more confident without improving
real-world signal.

The current app should keep the checked-in model catalog until we have multiple
real user-rated picks and a holdout set that reflects grocery-store capture.

## Follow-Ups

- Collect real phone captures before packaging a retrained sweetness model.
- Add a grocery-store holdout manifest before treating metrics as meaningful.
- Investigate the History edit screen not visibly exiting edit mode after a
  successful save.
- Revisit audio scoring after collecting labeled knock samples from real fruit.
