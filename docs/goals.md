# MelonSense Goals

## Loop Rules

- Work one goal at a time in a git worktree.
- Merge directly to `main` and push after verification.
- Run an independent review for Android behavior, model logic, or complicated logic before merge.
- Stop only for a real blocker: missing user decision, missing secret/account action, failed verification needing judgment, or unsafe ambiguity.
- After each goal, update this file before moving to the next goal.

## Current Goal

9. Device QA + first real feedback export dry run

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

- Build and install a debuggable app on an Android emulator or device.
- Exercise the core picker path: camera capture, visual assessment, knock test, combined result, "I Picked This", history rating, and training export.
- Pull the generated export bundle from app storage when a device permits it.
- Run the picked-history converter against the exported manifest and confirm it produces a visual feedback manifest.
- Record any device, permission, camera, audio, or export blockers with exact reproduction details.
- If no emulator/device is available, stop with the build/install command evidence and the missing-environment blocker.

## Loop 2 Purpose

The MVP loop proved the app architecture and local training path. Loop 2 proves
the physical feedback loop: use the app on Android, collect real picked-history
feedback, retrain from that feedback, replace the packaged model, and harden the
weakest signal areas.

## Current Goal Run Notes

- `./gradlew :app:assembleStableDebug` succeeded on the Goal 9 worktree.
- `adb devices -l` initially showed no connected devices.
- AVD `R645_API36_PlayStore` exists locally.
- Launching `R645_API36_PlayStore` with `-no-window -no-snapshot-load -no-audio -no-boot-anim` briefly exposed `emulator-5554` as `offline`, then the emulator disappeared from adb before `sys.boot_completed=1`.
- Current blocker: no booted Android runtime is available for install, camera, audio, history, or export QA.

## Known Tradeoffs

- PyTorch Lite makes the debug APK large because it packages native runtime libraries for multiple ABIs. Keep this acceptable for the MVP, then revisit TFLite or ABI-specific packaging during the Android packaging size pass.
- The packaged model instrumentation smoke test compiles in CI/local builds, but executing it requires an attached Android device or emulator.
- Runtime ripeness is currently binary (`ripe`, `unripe`) because the full-frame labeled source data does not have a usable overripe class. Add overripe back after collecting or deriving full-frame overripe data.
- Knock scoring is heuristic until real labeled audio samples exist.
- First retraining from picked-history data needs real user-rated photos; synthetic or public-only data is not enough to validate the personal picker loop.
