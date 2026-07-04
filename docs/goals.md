# MelonSense Goals

## Loop Rules

- Work one goal at a time in a git worktree.
- Merge directly to `main` and push after verification.
- Run an independent review for Android behavior, model logic, or complicated logic before merge.
- Stop only for a real blocker: missing user decision, missing secret/account action, failed verification needing judgment, or unsafe ambiguity.
- After each goal, update this file before moving to the next goal.

## Current Goal

1. Android local visual inference MVP

## Queue

1. Android local visual inference MVP
2. Camera/photo assessment UX
3. History + "I Picked This"
4. Knock-test audio MVP
5. Combined visual/audio scoring
6. Feedback export for retraining
7. Retraining pipeline using picked-history data
8. Model replacement/versioning in app

## Acceptance For Current Goal

- Package the leakage-safe visual model artifacts in the Android app.
- Run local visual inference from captured photo artifacts.
- Convert local model labels and confidence into the existing `VisualScanResult`.
- Use the full-frame runtime ripeness model for Android; keep annotation-crop ripeness for training research until a detector/cropper exists.
- Preserve existing audio and final assessment behavior.
- Fall back without crashing if photo/model inference is unavailable.
- Add tests for model metadata and visual result mapping.

## Known Tradeoffs

- PyTorch Lite makes the debug APK large because it packages native runtime libraries for multiple ABIs. Keep this acceptable for the MVP, then revisit TFLite or ABI-specific packaging during model replacement/versioning.
- The packaged model instrumentation smoke test compiles in CI/local builds, but executing it requires an attached Android device or emulator.
- Runtime ripeness is currently binary (`ripe`, `unripe`) because the full-frame labeled source data does not have a usable overripe class. Add overripe back after collecting or deriving full-frame overripe data.
