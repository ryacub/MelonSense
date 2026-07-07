# Emulator Media QA

This runbook proves the MelonSense camera, knock, picked-history, export, and
training loop on an Android emulator. It is for pipeline QA, not model-quality
data collection.

## What Worked

Use the emulator with host audio enabled:

```sh
emulator @R645_API36_PlayStore \
  -no-window \
  -gpu swiftshader_indirect \
  -no-snapshot-load \
  -camera-back virtualscene \
  -virtualscene-poster wall=/tmp/melonsense-watermelon-poster.png \
  -virtualscene-poster table=/tmp/melonsense-watermelon-poster.png \
  -allow-host-audio \
  -no-boot-anim
```

After boot, enable host mic input explicitly:

```sh
adb emu avd hostmicon
```

The universal debug APK can still fail install on this AVD with
`INSTALL_FAILED_INSUFFICIENT_STORAGE`. For emulator QA, a temporary arm64-only
APK works:

```sh
cp app/build/outputs/apk/stable/debug/app-stable-debug.apk /tmp/melonsense-arm64-media-qa.apk
zip -d /tmp/melonsense-arm64-media-qa.apk 'lib/armeabi-v7a/*' 'lib/x86/*' 'lib/x86_64/*'
"$HOME/Library/Android/sdk/build-tools/37.0.0/apksigner" sign \
  --ks "$HOME/.android/debug.keystore" \
  --ks-pass pass:android \
  --key-pass pass:android \
  /tmp/melonsense-arm64-media-qa.apk
adb install -r /tmp/melonsense-arm64-media-qa.apk
```

For knock capture, host audio worked only after raising host input/output volume
and playing a loud synthetic WAV during each capture window:

```sh
osascript -e 'set volume output volume 100'
osascript -e 'set volume input volume 100'

(sleep 0.03; afplay -v 10 /tmp/melonsense-knock.wav) &
adb shell input tap <capture-knock-button-x> <capture-knock-button-y>
```

Observed emulator knock evidence:

```text
1 / 3 valid knocks: peak 32767, estimated 275 Hz
2 / 3 valid knocks: peak 32767, estimated 265 Hz
3 / 3 valid knocks: peak 32767, estimated 219 Hz
```

Observed combined result:

```text
Recommendation: Strong Pick
Visual score: 90
Audio score: 100
```

Export bundle pulled from the emulator:

```text
/data/user/0/com.ryacub.melonsense/files/training-exports/dataset-1783400945486/manifest.jsonl
```

Pull command:

```sh
mkdir -p /tmp/melonsense-export-pull
adb exec-out run-as com.ryacub.melonsense \
  tar -C files/training-exports -cf - dataset-1783400945486 \
  | tar -C /tmp/melonsense-export-pull -xf -
```

Training command:

```sh
python3 -m tools.training.real_data_loop \
  --export-manifest /tmp/melonsense-export-pull/dataset-1783400945486/manifest.jsonl \
  --epochs 1 \
  --max-samples-per-class 50
```

Observed training output:

```text
feedback.record_count: 1
feedback.class_balance.sweet: 1
training.sample_counts.all: 100
training.test_metrics.accuracy: 0.4666666666666667
android_candidate: training-runs/visual-baseline/sweetness/model_mobile.ptl
android_candidate_format: torchscript_lite
```

## MCP Findings

The available mobile MCP can see the emulator once it is booted, but its exposed
tools are basic device/app controls. It does not expose camera-scene injection
or microphone-frame injection.

The Android emulator skill scripts are useful for semantic navigation, UI
mapping, install, permissions, screenshots, and logs. They do not replace the
emulator media controls.

External Android MCP servers such as
[nim444/mcp-android-server-python](https://github.com/nim444/mcp-android-server-python)
and [mobile-next/mobile-mcp](https://github.com/mobile-next/mobile-mcp) are also
primarily ADB/UIAutomator/accessibility automation layers. They would help
standardize app navigation, screenshots, and log capture, but they do not solve
MelonSense's core media problem by themselves.

## gRPC Finding

The emulator ships `emulator_controller.proto`, including `injectAudio`, and can
start a gRPC bridge. The rough upstream flow is documented in Android emulator
gRPC examples:

- [Emulator over gRPC examples](https://gist.github.com/mrk-han/fa5c6e8951919b7efc1ba99fcd10496e)
- [AOSP emulator gRPC Python package](https://android.googlesource.com/platform/external/qemu/+/emu-master-dev/android/android-grpc/python/aemu-grpc)

On local emulator `36.4.9.0`, both a direct `injectAudio` attempt and a simpler
format-on-every-packet stream reset the gRPC connection and killed the emulator.
Do not use gRPC audio injection for the current loop unless the emulator version
is changed and retested.

## Caveats

- Emulator-generated media proves the app loop and training pipeline, not
  grocery-store model quality.
- The virtual-scene poster flag started virtual scene, but the custom poster was
  not visible in the default camera view during this run.
- The History outcome save updated persisted state, but the edit screen did not
  visually exit edit mode until app restart. Treat this as a separate UX/QA bug.
- PyTorch native libraries make the universal APK too large for this AVD; the
  packaging-size goal still matters.
