# MelonSense Goals

## Loop Rules

- Work one goal at a time in a git worktree.
- Merge directly to `main` and push after verification.
- Run an independent review for Android behavior, model logic, or complicated logic before merge.
- Stop only for a real blocker: missing user decision, missing secret/account action, failed verification needing judgment, or unsafe ambiguity.
- After each goal, update this file before moving to the next goal.

## Current Goal

6. Feedback export for retraining

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

- Build a training queue from picked history plus retained capture media.
- Export only Rated picks with sweetness and texture feedback.
- Copy retained photo/audio artifacts into a dataset bundle.
- Write JSONL rows containing scores, user feedback labels, media metadata, and retention timestamps.
- Stamp exported rows with schema version and `user_feedback` label source.
- Reject empty exports so no blank dataset bundle is treated as training data.
- Mark exported pick/capture rows as Exported after bundle creation.

## Known Tradeoffs

- PyTorch Lite makes the debug APK large because it packages native runtime libraries for multiple ABIs. Keep this acceptable for the MVP, then revisit TFLite or ABI-specific packaging during model replacement/versioning.
- The packaged model instrumentation smoke test compiles in CI/local builds, but executing it requires an attached Android device or emulator.
- Runtime ripeness is currently binary (`ripe`, `unripe`) because the full-frame labeled source data does not have a usable overripe class. Add overripe back after collecting or deriving full-frame overripe data.
