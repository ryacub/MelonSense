# MelonSense

MelonSense is a native Android app for judging watermelons with visual cues, knock-test audio, and personal outcome learning.

## Current MVP

- Kotlin Android app with Jetpack Compose and Material 3.
- Photo-first visual assessment with local packaged models.
- Knock-test audio scoring.
- Combined result screen.
- `I Picked This` history capture.
- Sweetness and texture outcome ratings.
- Local training exports and Python retraining tools.

Start with [docs/mvp-runbook.md](docs/mvp-runbook.md) for the current operating
path, data gates, and next-loop candidates.

## Build

```bash
./gradlew :app:assembleStableDebug
```

For sideloading, use the ABI split APK that matches the target device. See
[docs/android-packaging-size.md](docs/android-packaging-size.md).
