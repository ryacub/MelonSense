# MelonSense Goals

## Loop Rules

- Work one goal at a time in a git worktree.
- Merge directly to `main` and push after verification.
- Run an independent review for Android behavior, model logic, or complicated logic before merge.
- Stop only for a real blocker: missing user decision, missing secret/account action, failed verification needing judgment, or unsafe ambiguity.
- After each goal, update this file before moving to the next goal.

## Current Goal

8. Model replacement/versioning in app

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

- Treat `app/src/main/assets/models/visual-models.json` as the packaged visual model catalog source of truth.
- Load catalog id, version, track assets, labels, byte sizes, and weights from the packaged manifest at app startup.
- Report the loaded catalog id/version through `LocalModelInfo`.
- Keep a validated fallback catalog so malformed packaged metadata does not prevent visual fallback behavior.
- Document how to replace packaged `model_mobile.ptl` assets and bump the runtime catalog version.

## Known Tradeoffs

- PyTorch Lite makes the debug APK large because it packages native runtime libraries for multiple ABIs. Keep this acceptable for the MVP, then revisit TFLite or ABI-specific packaging during model replacement/versioning.
- The packaged model instrumentation smoke test compiles in CI/local builds, but executing it requires an attached Android device or emulator.
- Runtime ripeness is currently binary (`ripe`, `unripe`) because the full-frame labeled source data does not have a usable overripe class. Add overripe back after collecting or deriving full-frame overripe data.
