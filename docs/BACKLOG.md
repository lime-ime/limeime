# LIME IME Backlog

Public backlog for confirmed unresolved fixes and product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; completed, shipped, or closed scopes stay in their issue docs instead of here.

Last reviewed: 2026-07-15

## Pending fixes

- fix#139 iOS: source fix `7c067c64` defers keyboard-height changes until after rotation settles, and the maintainer's LINE rotation retest passed. The private reporter's engineer confirmed that the affected host uses a cross-platform UI framework whose scroll/inset handling may be a second factor. Keep the exact framework private. Keep this pending through the next 6.1.31 TestFlight build and private bottom-reachability retest. If it still fails without rotation, compare fresh presentation versus in-place keyboard switching and collect privacy-safe host frame/inset diagnostics. Do not cap or shrink layouts. Android is not in scope.

## Product work

- feat#144 iOS: add user-selectable split/two-hand operation for larger screens or landscape/tablet-like use, plus one-hand phone alignment modes that shift/fit the keyboard left or right while preserving the default full-width behavior when disabled. Existing iPad split hooks (`KeyboardView.splitMode`, `split_keyboard_mode`, `LayoutMetrics.KeyboardRow.splitGapFraction`, `_ipad` / `_ipad_narrow` variants) should be audited first; then add missing phone one-hand placement, settings/UI persistence, safe-area/orientation behavior, and regression checks. Android is not in scope.
