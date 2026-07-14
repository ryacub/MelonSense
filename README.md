# MelonSense

MelonSense is an experimental native Android app for comparing watermelons with
local visual models, a knock-test audio heuristic, and personal outcome history.

> [!IMPORTANT]
> MelonSense is a personal-use alpha, not a reliable or scientifically validated
> ripeness detector. Current model results do not include a physical-phone,
> grocery-store holdout, and knock scoring is not yet backed by a trained audio
> model.

## Current MVP

- Photo-first assessment using packaged PyTorch Lite models on the device.
- Guided three-knock audio capture and local signal analysis.
- Combined `Strong Pick`, `Good Candidate`, `Maybe`, or `Skip` result.
- `I Picked This` history with sweetness and texture outcome ratings.
- Default-on training capture with app-private storage and 14-day media cleanup.
- Shareable training-data ZIP export and Python retraining tools.
- Versioned local model catalog with no server inference requirement.

The app requests camera and microphone access. It does not request location or
internet access, Android backup is disabled, and retained media stays in the
app's private storage unless the user explicitly shares an export.

## Install

Download an APK from the latest GitHub release. For most current Android phones,
use the `arm64-v8a` APK. Use the universal APK only when the device ABI is
unknown; it is substantially larger.

MelonSense requires Android 8.0 (API 26) or newer. Because releases are published
outside Google Play, Android may ask for permission to install an unknown app.

## Assessment Flow

1. Capture a clear photo of one watermelon.
2. Review the visual signal and continue to the knock test.
3. Record three knocks in a reasonably quiet environment.
4. Review the combined result and tap `I Picked This` when applicable.
5. After opening the melon, rate sweetness and texture from History.

Those outcome ratings can be exported for future model training. They do not
automatically retrain or replace the model on the phone.

## Build And Verify

```bash
./gradlew :app:testStableDebugUnitTest spotlessCheck \
  :app:compileStableDebugKotlin :app:lintStableDebug
./gradlew :app:assembleStableDebug
./gradlew :app:assembleStableRelease
python3 -m unittest discover -s tests -v
```

ABI-specific and universal APKs are written below
`app/build/outputs/apk/stable/`.

## Known Constraints

- Visual performance on public-dataset splits does not establish grocery-store
  accuracy.
- Audio scoring uses peak, RMS, estimated frequency, and consistency heuristics;
  it is not a trained ripeness model.
- Runtime ripeness remains binary until the overripe data gate is met.
- The PyTorch runtime makes ABI APKs roughly 60-85 MB and the universal APK about
  250 MB.
- Real-device testing with varied phones, stores, and watermelons remains the
  next model-quality milestone.

## Project References

- [Changelog](CHANGELOG.md)
- [Training pipeline](docs/training.md)
- [Dataset qualification](docs/datasets.md)
- [Model quality](docs/model-quality-evaluation.md)
- [Audio model hardening](docs/audio-model-hardening.md)
- [Emulator media QA](docs/emulator-media-qa.md)
- [Android packaging size](docs/android-packaging-size.md)

## License

No project license has been granted yet. Third-party datasets and model inputs
retain their respective licenses and attribution requirements.
