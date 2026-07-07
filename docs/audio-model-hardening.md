# Audio Model Hardening

Goal 12 hardens the knock-test path without pretending we have enough real
watermelon audio for a trained model yet.

## Current Data Position

The only current app-collected audio sample in the loop is emulator-generated.
It proves export and conversion plumbing, but it is not representative training
data. Public visual datasets do not validate knock behavior, and the reviewed
acoustic paper did not expose raw downloadable audio.

Audio training should start from picked-history exports where the user selected
`I Picked This` and later rated sweetness and texture. Those exports include the
audio artifact plus outcome context.

## App Heuristic

The Android app still uses a local heuristic, but it now uses measured features
instead of placeholder resonance wording:

- valid knock count
- average peak amplitude
- average RMS amplitude
- estimated knock frequency
- frequency spread across valid knocks

The score remains conservative when fewer than three valid knocks exist. With
three knocks, consistent frequencies increase score and confidence; wide
frequency spread reduces confidence because the signal is less repeatable.

This is not a trained audio model. It is a safer feature-based bridge until real
labeled knocks exist.

## Labeled Audio Import

Picked-history exports can now produce a separate audio manifest:

```sh
python3 -m tools.training.picked_history_feedback \
  --export-manifest /path/to/training-exports/dataset-<timestamp>/manifest.jsonl \
  --output-manifest datasets/interim/picked-history-feedback-v0/manifest.jsonl \
  --audio-output-manifest datasets/interim/picked-history-audio-v0/manifest.jsonl
```

The audio manifest preserves:

- `audio_path`
- sweetness label mapped to `sweet` / `not_sweet`
- texture rating
- result label
- visual and audio scores
- valid knock count
- estimated frequency
- source artifact metadata

## Packaging Decision

Do not package a trained audio model yet. The app should continue using the
feature heuristic until we have enough real phone captures across outcomes.

Minimum next useful dataset target:

- 30 picked-history exports with audio artifacts
- at least 10 `not_sweet` and 10 `sweet` outcomes
- mixed texture ratings
- physical-phone recordings, not emulator audio

## Deferred

- Trainable audio feature extraction from raw PCM windows.
- Audio model metrics and threshold tuning.
- Replacing heuristic audio scoring with a packaged local audio model.
