# LIME IME Backlog

Public backlog for confirmed unresolved fixes and product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; completed, shipped, or closed scopes stay in their issue docs instead of here.

Last reviewed: 2026-07-15

## Pending fixes

- fix#139 iOS: continue investigating host-app bottom content coverage after the attempted keyboard-height reporting fix. A private reporter reproduced the issue on LIME 6.1.28, iPhone 17 Pro Max, and iOS 26.6 beta 4 across keyboard sizes from minimum to extra large, with the scrollbar unable to reach the bottom. Use the private recording for device instrumentation, compare LIME's published root/input-view frame with the rendered keyboard height, and verify against third-party keyboards that do not reproduce the behavior. Android is not in scope.

## Product work

- feat#144 iOS: add user-selectable split/two-hand operation for larger screens or landscape/tablet-like use, plus one-hand phone alignment modes that shift/fit the keyboard left or right while preserving the default full-width behavior when disabled. Existing iPad split hooks (`KeyboardView.splitMode`, `split_keyboard_mode`, `LayoutMetrics.KeyboardRow.splitGapFraction`, `_ipad` / `_ipad_narrow` variants) should be audited first; then add missing phone one-hand placement, settings/UI persistence, safe-area/orientation behavior, and regression checks. Android is not in scope.
