# MelonSense MVP Runbook

This is the operating path for the current MelonSense MVP. It links to the
detailed goal docs instead of repeating every command.

## Current MVP Shape

MelonSense is a native Android picker app with:

- local visual inference through packaged PyTorch Lite models;
- photo-first assessment, then optional knock-test audio;
- combined visual/audio scoring;
- `I Picked This` history capture;
- sweetness and texture outcome selectors;
- local training export bundles with compressed media retention;
- Python tooling for dataset procurement, feedback conversion, and baseline
  retraining.

The app is usable as a quick picker now. Model learning is intentionally gated:
do not replace packaged models just because a training command emits a
candidate.

## Main User Loop

1. Build a stable ABI split APK.
2. Install the split APK matching the phone or emulator ABI.
3. Scan a melon visually.
4. Run the knock test when possible.
5. Review the combined result.
6. Tap `I Picked This` only for the melon actually chosen.
7. After eating it, rate sweetness and texture in History.
8. Export picked-history training data.
9. Convert and retrain locally.
10. Replace packaged models only after the quality gates pass.

Packaging details live in [Android Packaging Size](android-packaging-size.md).
The local emulator/media QA path lives in [Emulator Media QA](emulator-media-qa.md).

## Data Sources

Use [Dataset Qualification](datasets.md) as the procurement source of truth.
Only train from sources marked approved for the intended phase.

Current approved public visual roles:

- `roboflow-capstone-sweetness`: visual sweetness proxy.
- `roboflow-fyp-ripeness`: small prototype ripeness/overripe source.
- `roboflow-saysay-ripe-unripe`: current binary ripe/unripe seed.
- `lightly-fruits-detection`: watermelon detection/framing only.
- `fruits-360-watermelon`: generic watermelon visual pretraining only.

Do not use `roboflow-new-workspace-watermelon` until it is added to
`APPROVED_SOURCES` and passes sample audit. Do not use unclear-license sources
until license and access are verified.

Dataset staging commands and expected local folder shape live in
[Local Dataset Storage](../datasets/README.md).

## Training Path

Use [Visual Baseline Training](training.md) for the actual training commands.

The current supported training tracks are:

- `sweetness`: binary `sweet` / `not_sweet`;
- `ripeness`: research/evaluation track with object-detection-derived labels;
- `runtime_ripeness`: Android runtime track, currently binary `ripe` /
  `unripe`.

Picked-history feedback should enter through
`tools.training.picked_history_feedback` or `tools.training.real_data_loop`.
Audio artifacts can be staged into a separate picked-history audio manifest, but
visual training does not consume that audio manifest yet.

## Media Retention

The MVP stores training media only for picked-history learning:

- photos are captured as JPEG artifacts;
- knock audio is stored as gzip-compressed PCM16 artifacts;
- no video artifact is collected in the current MVP;
- retained media expires after 14 days;
- exports are for local training, not server submission.

The app should keep using image/audio artifact metadata already produced during
capture. Do not add active location collection to the MVP.

## Packaging Gates

Do not package a new visual model unless all of these are true:

- training data includes real phone captures, not emulator-only media;
- evaluation includes a grocery-store or home-capture phone holdout excluded
  from training;
- validation and holdout metrics improve over the packaged model;
- per-class behavior is acceptable, not only aggregate accuracy;
- the candidate does not inflate confidence on weak evidence;
- `visual-models.json` is updated with version, labels, byte size, and asset
  path;
- Android unit tests and packaged model smoke checks pass;
- a physical-device scan/knock/history/export smoke test passes.

The latest emulator-feedback sweetness candidate is not packageable. Details
are in [Model Quality Evaluation](model-quality-evaluation.md).

## Audio Position

Knock scoring is still heuristic. The app uses measured features now, but there
is no trained audio model worth packaging yet.

Do not replace the heuristic until there are enough physical-phone knock
captures across outcomes. The current minimum useful target is recorded in
[Audio Model Hardening](audio-model-hardening.md): 30 picked-history exports,
including at least 10 `sweet` and 10 `not_sweet` outcomes.

## Overripe Position

Runtime ripeness stays binary for MVP. Do not add `overripe` to the packaged
Android runtime model until the data gate in
[Overripe Class Strategy](overripe-class-strategy.md) is met.

Texture ratings such as `Soft` or `Mushy` can identify candidates for manual
review, but they are not supervised `overripe` labels.

## Physical Device Checklist

Use this when moving from emulator QA to real data collection:

- Install the ABI split APK matching the device, usually `arm64-v8a`.
- Confirm camera permission and photo capture.
- Confirm local visual inference returns evidence and score.
- Confirm microphone permission.
- Capture three real knocks in a quiet environment.
- Confirm combined result renders after audio analysis.
- Save only real chosen melons with `I Picked This`.
- Later rate sweetness and texture in History.
- Export the training bundle.
- Pull the bundle locally and run the real-data loop.
- Keep the export for training only, then delete it within 14 days.

## Remaining Blockers

- No representative physical-phone grocery-store dataset exists yet.
- No physical-phone audio dataset exists yet.
- Emulator media proves plumbing, not real model quality.
- The History edit screen can persist a rating while not visibly leaving edit
  mode until restart.
- Packaged model smoke execution still needs an attached Android target.
- Universal APK remains large because PyTorch Lite packages all ABIs; ABI split
  APKs are the sideload path for now.

## Loop 3 Candidates

Recommended next loop:

1. Physical-device smoke and first real picked-history export.
2. History edit-state UX fix.
3. Real-data collection target: 10 chosen melons with sweetness/texture ratings.
4. Physical-phone holdout manifest setup.
5. Sweetness retraining with holdout metrics.
6. Audio dataset growth and feature audit.
7. Packaged model replacement only if quality gates pass.
8. Release-readiness pass: signing, app icon/name polish, privacy/retention
   copy, and install instructions.
