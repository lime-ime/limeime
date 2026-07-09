# LIME IME Backlog

Public backlog for confirmed unresolved fixes and product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; completed, shipped, or closed scopes stay in their issue docs instead of here.

Last reviewed: 2026-07-08

## Pending fixes

- fix#139 iOS: private-email/TestFlight bottom-content coverage remains pending. The attempted iPhone portrait height cap was abandoned because it made the `keyboard_size` preference ineffective on real device; `applyHeight()` must report the height derived from `KeyboardView.preferredHeight` without rewriting `KeyboardView.keySizeScale`. Continue investigating LIME custom-keyboard height / safe-area behavior across Array10 and Dayi. Track in `docs/#139_ISSUE.md`. No Android APK retest applies.

## Product work

- feat#144 iOS: add user-selectable split/two-hand operation for larger screens or landscape/tablet-like use, plus one-hand phone alignment modes that shift/fit the keyboard left or right while preserving the default full-width behavior when disabled. Existing iPad split hooks (`KeyboardView.splitMode`, `split_keyboard_mode`, `LayoutMetrics.KeyboardRow.splitGapFraction`, `_ipad` / `_ipad_narrow` variants) should be audited first; then add missing phone one-hand placement, settings/UI persistence, safe-area/orientation behavior, and regression checks. Android is not in scope.
