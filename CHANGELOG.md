# Changelog

All notable project changes should be recorded here.

## Unreleased

### Added

- Native Android MVP with photo-first visual assessment, knock-test audio,
  combined scoring, picked-history capture, and sweetness/texture feedback.
- Local PyTorch Lite visual inference with a versioned model catalog.
- Training export pipeline for picked-history feedback, including retained media
  artifacts.
- Python dataset and training tools for public dataset staging, picked-history
  conversion, visual baseline training, and first real-data-loop runs.
- Emulator media QA documentation for camera, knock audio, export, and training
  pipeline verification.
- Android packaging size documentation and ABI split APK generation for lower
  sideload/install friction.

### Changed

- Unrated history outcomes now require explicit sweetness and texture selections before saving.
- Assessment progress now survives recreation, and starting a new scan invalidates stale knock and result state.
- Visual scoring now dampens low-confidence local model predictions toward
  neutral instead of over-trusting weak classifications.
- Knock-test scoring now uses measured valid knock count, peak/RMS amplitude,
  estimated frequency, and frequency spread.
- Runtime ripeness remains binary until the overripe data gate is met.
- README now points to practical project docs instead of planning docs.

### Removed

- Planning-only goal/runbook docs from the repository.

### Known Constraints

- Current retrained sweetness candidate from emulator feedback is not
  packageable.
- Audio scoring is still heuristic until enough real phone knock samples exist.
- First representative training loop still needs physical-device, real-melon
  data.
