# Android Packaging Size

Goal 14 measures and reduces install friction from the local inference runtime.

## Measurement

Measured on 2026-07-07 with:

```sh
./gradlew :app:assembleStableDebug
```

Before ABI splits, the universal stable debug APK was:

```text
app-stable-debug.apk: 264,698,771 bytes / 252 MB
```

Uncompressed contributors:

```text
lib/: 243,851,488 bytes
assets/models/: 1,137,068 bytes
```

Native library size by ABI:

```text
arm64-v8a: 61,544,584 bytes
armeabi-v7a: 48,340,888 bytes
x86: 68,382,664 bytes
x86_64: 65,583,352 bytes
```

Largest entries were PyTorch Lite native libraries:

```text
lib/x86/libpytorch_jni_lite.so: 67,234,104 bytes
lib/x86_64/libpytorch_jni_lite.so: 64,355,192 bytes
lib/arm64-v8a/libpytorch_jni_lite.so: 60,418,760 bytes
lib/armeabi-v7a/libpytorch_jni_lite.so: 47,630,448 bytes
```

The packaged model assets are not the size issue:

```text
visual-runtime-ripeness-v0.ptl: 568,133 bytes
visual-sweetness-fa99cb0.ptl: 568,323 bytes
```

## Decision

Keep PyTorch Lite for MVP and enable ABI split APKs.

Reasoning:

- PyTorch Lite is currently required for local visual inference.
- Replacing PyTorch with TFLite would be a model-runtime migration, not a size
  pass.
- ABI-specific APKs remove the unused native libraries for sideload and emulator
  QA while preserving a universal APK fallback.
- The app stays local/offline and does not add server inference dependency.

## Result

After enabling ABI splits, `assembleStableDebug` emits:

```text
app-stable-arm64-v8a-debug.apk: 82,245,023 bytes / 78 MB
app-stable-armeabi-v7a-debug.apk: 69,054,523 bytes / 66 MB
app-stable-x86-debug.apk: 89,092,379 bytes / 85 MB
app-stable-x86_64-debug.apk: 86,275,565 bytes / 82 MB
app-stable-universal-debug.apk: 264,698,771 bytes / 252 MB
```

`assembleStableRelease` also emits split unsigned APKs:

```text
app-stable-arm64-v8a-release-unsigned.apk: 75,535,322 bytes / 72 MB
app-stable-armeabi-v7a-release-unsigned.apk: 62,344,822 bytes / 59 MB
app-stable-x86-release-unsigned.apk: 82,382,678 bytes / 79 MB
app-stable-x86_64-release-unsigned.apk: 79,565,864 bytes / 76 MB
app-stable-universal-release-unsigned.apk: 257,989,070 bytes / 246 MB
```

Each ABI APK contains only its target ABI native libraries. The universal APK
still contains all four ABI directories and remains available for fallback.

## QA Install Guidance

Use the ABI-specific APK that matches the target:

```sh
adb install -r app/build/outputs/apk/stable/debug/app-stable-arm64-v8a-debug.apk
```

For the local `R645_API36_PlayStore` emulator, use the `arm64-v8a` APK. Use the
universal APK only when target ABI is unknown.

## Next Size Gate

Revisit runtime migration only if one of these becomes true:

- arm64 APK exceeds 120 MB;
- Play/App Bundle distribution becomes a goal;
- TFLite export is available with equal or better model quality;
- PyTorch Lite blocks install on target physical devices.
