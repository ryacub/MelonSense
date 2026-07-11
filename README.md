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

Useful project docs:

- [CHANGELOG.md](CHANGELOG.md)
- [docs/training.md](docs/training.md)
- [docs/datasets.md](docs/datasets.md)
- [docs/emulator-media-qa.md](docs/emulator-media-qa.md)
- [docs/android-packaging-size.md](docs/android-packaging-size.md)

## Build

```bash
./gradlew :app:assembleStableDebug
```

For sideloading, use the ABI split APK that matches the target device. See
[docs/android-packaging-size.md](docs/android-packaging-size.md).
