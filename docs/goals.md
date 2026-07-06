# MelonSense Goals

## Loop Rules

- Work one goal at a time in a git worktree.
- Merge directly to `main` and push after verification.
- Run an independent review for Android behavior, model logic, or complicated logic before merge.
- Stop only for a real blocker: missing user decision, missing secret/account action, failed verification needing judgment, or unsafe ambiguity.
- After each goal, update this file before moving to the next goal.

## Current Goal

10. First real data loop

## Loop 1 Completed

1. Android local visual inference MVP
2. Camera/photo assessment UX
3. History + "I Picked This"
4. Knock-test audio MVP
5. Combined visual/audio scoring
6. Feedback export for retraining
7. Retraining pipeline using picked-history data
8. Model replacement/versioning in app

## Loop 2 Queue

9. Device QA + first real feedback export dry run
10. First real data loop
11. Model quality evaluation and threshold tuning
12. Audio model hardening with labeled knock samples
13. Overripe class data strategy
14. Android packaging size pass
15. Training and QA runbook polish

## Acceptance For Current Goal

- Start from at least one real app-generated `training-exports/<bundle>/manifest.jsonl` bundle containing user-rated picked-history feedback.
- Convert the real bundle with `python3 -m tools.training.picked_history_feedback`.
- Confirm the converted visual feedback manifest contains usable photo-backed sweetness records.
- Run visual baseline training with the converted manifest via `--extra-manifest`.
- Record the resulting metrics and Android candidate artifact path.
- If no real export bundle is available, stop with the missing-data blocker and keep Goal 9's physical-device/microphone blocker linked.

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

## Current Goal Run Notes

- Targeted local search found no existing pulled app export bundle under the repo, `/tmp`, `~/Downloads`, or `~/Desktop`.
- `adb devices -l` showed no connected Android device.
- Current blocker: Goal 10 requires a real app-generated picked-history export bundle. That bundle depends on finishing the physical-device flow blocked in Goal 9, or manually providing an export copied from a device where camera and microphone capture work.

## Known Tradeoffs

- PyTorch Lite makes the debug APK large because it packages native runtime libraries for multiple ABIs. Keep this acceptable for the MVP, then revisit TFLite or ABI-specific packaging during the Android packaging size pass.
- The packaged model instrumentation smoke test compiles in CI/local builds, but executing it requires an attached Android device or emulator.
- Runtime ripeness is currently binary (`ripe`, `unripe`) because the full-frame labeled source data does not have a usable overripe class. Add overripe back after collecting or deriving full-frame overripe data.
- Knock scoring is heuristic until real labeled audio samples exist.
- First retraining from picked-history data needs real user-rated photos; synthetic or public-only data is not enough to validate the personal picker loop.
