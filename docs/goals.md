# MelonSense Goals

## Loop Rules

- Work one goal at a time in a git worktree.
- Merge directly to `main` and push after verification.
- Run an independent review for Android behavior, model logic, or complicated logic before merge.
- Stop only for a real blocker: missing user decision, missing secret/account action, failed verification needing judgment, or unsafe ambiguity.
- After each goal, update this file before moving to the next goal.

## Current Goal

15. Training and QA runbook polish

## Loop 1 Completed

1. Android local visual inference MVP
2. Camera/photo assessment UX
3. History + "I Picked This"
4. Knock-test audio MVP
5. Combined visual/audio scoring
6. Feedback export for retraining
7. Retraining pipeline using picked-history data
8. Model replacement/versioning in app

## Loop 2 Completed

9. Device QA + first real feedback export dry run
10. First real data loop
11. Model quality evaluation and threshold tuning
12. Audio model hardening with labeled knock samples
13. Overripe class data strategy
14. Android packaging size pass

## Loop 2 Queue

15. Training and QA runbook polish

## Acceptance For Current Goal

- Consolidate training, data procurement, emulator QA, and packaging docs into a final MVP runbook.
- Make the next physical-device/data-collection steps obvious.
- Preserve links to detailed goal docs without duplicating stale commands.
- Document remaining blockers and explicit next-loop candidates.

## Loop 2 Purpose

The MVP loop proved the app architecture and local training path. Loop 2 proves
the physical feedback loop: use the app on Android, collect real picked-history
feedback, retrain from that feedback, replace the packaged model, and harden the
weakest signal areas.

## Deferred Goal 9 Run Notes

- `./gradlew :app:assembleStableDebug` succeeded on the Goal 9 worktree.
- `adb devices -l` initially showed no connected devices.
- AVD `R645_API36_PlayStore` exists locally.
- Launching `R645_API36_PlayStore` with `-no-window -no-snapshot-load -no-audio -no-boot-anim` briefly exposed `emulator-5554` as `offline`, then the emulator disappeared from adb before `sys.boot_completed=1`.
- Removing stale AVD lock files and launching with `-no-window -gpu swiftshader_indirect -no-snapshot-load -no-audio -no-boot-anim` booted the emulator successfully.
- Installing the universal `app-stable-debug.apk` failed with `INSTALL_FAILED_INSUFFICIENT_STORAGE`; the APK is about 252 MB and the AVD had about 636 MB free under `/data`.
- A temporary arm64-only QA APK was created outside the repo at `/tmp/melonsense-arm64-debug.apk` by removing non-arm64 native libraries and re-signing with the debug keystore. Size was about 78 MB and install succeeded.
- App launch, Scan screen rendering, camera permission, photo capture, local visual inference, and visual evidence rendering passed on `emulator-5554`.
- Knock Test screen rendered after visual capture and received the visual score.
- Audio permission was granted, and `Capture Knock` executed without crash, but the emulator captured silence/near-silence: `Knock was too quiet: peak 8`.
- Relaunching the AVD with audio enabled and playing a short host-side synthetic knock WAV during capture still produced `peak 8`.
- Deferred blocker: the headless emulator cannot provide a usable microphone/knock signal, so combined result, "I Picked This", history rating, export bundle pull, and converter dry run remain blocked until a physical Android device or a working emulator audio-input path is available.
- Follow-up resolved the emulator audio-input path by using `-allow-host-audio`, `adb emu avd hostmicon`, max host input/output volume, and loud host WAV playback.

## Current Goal Run Notes

- Goal 10 completed with an emulator-generated app export, not representative grocery-store data. See `docs/emulator-media-qa.md`.
- Proper emulator config required `-allow-host-audio`, explicit `adb emu avd hostmicon`, max host input/output volume, and loud synthetic WAV playback during the 520 ms knock capture window.
- Observed valid knock captures: peaks `32767`, estimated frequencies `275 Hz`, `265 Hz`, and `219 Hz`.
- Combined app result reached `Recommendation: Strong Pick`, `Visual score: 90`, and `Audio score: 100`.
- Export succeeded at `/data/user/0/com.ryacub.melonsense/files/training-exports/dataset-1783400945486/manifest.jsonl`.
- Pulled bundle included manifest, one photo artifact, and one audio artifact.
- `python3 -m tools.training.real_data_loop --export-manifest /tmp/melonsense-export-pull/dataset-1783400945486/manifest.jsonl --epochs 1 --max-samples-per-class 50` succeeded from the main worktree.
- Converted feedback summary: `record_count=1`, `class_balance={"sweet": 1}`.
- Training summary: `sample_counts.all=100`, `test_metrics.accuracy=0.4666666666666667`, Android candidate `training-runs/visual-baseline/sweetness/model_mobile.ptl`, candidate format `torchscript_lite`.
- Emulator gRPC `injectAudio` is not usable on local emulator `36.4.9.0`: stream reset killed the emulator twice.
- Mobile/Android MCP tooling is useful for device discovery and UI automation, but available MCP tools do not provide camera-scene or mic-frame injection.
- New QA issue found: History outcome save persisted the row as `Rated` but the edit screen did not visibly leave edit mode until app restart.
- Goal 11 completed. See `docs/model-quality-evaluation.md`.
- Latest sweetness run is not packageable: validation accuracy `0.46153846153846156`, test accuracy `0.4666666666666667`, and every validation/test sample was predicted as `sweet`.
- Latest local ripeness metrics are also weak enough that visual-only confidence should stay conservative: test accuracy `0.16666666666666666`, macro F1 `0.1507936507936508`.
- Result-label thresholds stay unchanged at `85/70/55`, but local visual scoring now baseline-normalized confidence-adjusts each track score toward neutral before combining tracks.
- Do not package the emulator-feedback `training-runs/visual-baseline/sweetness/model_mobile.ptl` candidate.
- Goal 12 completed. See `docs/audio-model-hardening.md`.
- Knock scoring now uses measured valid count, average peak, average RMS, estimated knock frequency, and frequency spread instead of placeholder resonance wording.
- Audio score/confidence stay conservative when fewer than three valid knocks exist or when valid knocks have inconsistent estimated frequencies.
- Picked-history export conversion can now write `datasets/interim/picked-history-audio-v0/manifest.jsonl` via `--audio-output-manifest`.
- Do not package a trained audio model yet. Current usable audio data is emulator-generated and only proves plumbing, not model quality.
- Goal 13 completed. See `docs/overripe-class-strategy.md`.
- Runtime ripeness stays binary (`ripe`, `unripe`) because the only verified watermelon overripe public source is the small 73-image Roboflow FYP dataset and app picked-history data does not collect a clean overripe label.
- `roboflow-new-workspace-watermelon` was added as a large binary/semi-ripe candidate source, but it has no overripe class.
- Add `overripe` to Android runtime only after the documented gate is met: audited full-frame class balance, physical-phone holdout, leakage-safe split, validation/holdout macro F1, and conservative overripe error behavior.
- Goal 14 completed. See `docs/android-packaging-size.md`.
- Current universal stable debug APK is `264,698,771` bytes / about `252 MB`.
- Native libraries account for `243,851,488` uncompressed bytes; PyTorch Lite native libraries are the dominant contributor.
- Model assets are small by comparison: packaged visual models total about `1.1 MB`.
- ABI split APKs are enabled while keeping the universal APK fallback. Measured split APKs: arm64 `82,245,023` bytes / `78 MB`, armeabi-v7a `69,054,523` bytes / `66 MB`, x86 `89,092,379` bytes / `85 MB`, x86_64 `86,275,565` bytes / `82 MB`.
- Keep PyTorch Lite for MVP. Revisit TFLite/runtime migration only if arm64 exceeds `120 MB`, app-bundle distribution becomes a goal, or PyTorch blocks target-device install.

## Known Tradeoffs

- PyTorch Lite makes the universal APK large because it packages native runtime libraries for multiple ABIs. ABI split APKs are now available for sideload and emulator QA; keep PyTorch Lite for MVP.
- The packaged model instrumentation smoke test compiles in CI/local builds, but executing it requires an attached Android device or emulator.
- Runtime ripeness is currently binary (`ripe`, `unripe`) because the full-frame labeled source data does not have a usable overripe class. Add overripe back only after the gate in `docs/overripe-class-strategy.md`.
- Knock scoring is heuristic until real labeled audio samples exist.
- First retraining from picked-history data needs real user-rated photos; synthetic or public-only data is not enough to validate the personal picker loop.
