# LIME IME Backlog

Public backlog for confirmed unresolved fixes and product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; completed, shipped, or closed scopes stay in their issue docs instead of here.

Last reviewed: 2026-07-11

## Pending fixes

- fix#153 iOS: Array30 `w#` symbol input fails to show the expected Array-style symbol candidates when `#` is a digit key. Compare iOS digit-key routing, composing-code normalization, and lookup/candidate generation with the working Android path; preserve ordinary numeric input and non-Array behavior. Track in `docs/#153_ISSUE.md`. Android is reported working and requires no behavior change.

## Product work

- feat#144 iOS: add user-selectable split/two-hand operation for larger screens or landscape/tablet-like use, plus one-hand phone alignment modes that shift/fit the keyboard left or right while preserving the default full-width behavior when disabled. Existing iPad split hooks (`KeyboardView.splitMode`, `split_keyboard_mode`, `LayoutMetrics.KeyboardRow.splitGapFraction`, `_ipad` / `_ipad_narrow` variants) should be audited first; then add missing phone one-hand placement, settings/UI persistence, safe-area/orientation behavior, and regression checks. Android is not in scope.
