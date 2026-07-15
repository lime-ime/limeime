# LIME IME Backlog

Public backlog for confirmed unresolved fixes and product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; completed, shipped, or closed scopes stay in their issue docs instead of here.

Last reviewed: 2026-07-15

## Pending fixes

- fix#139 iOS: commit `7c067c64` fixes the LINE rotation path, but the maintainer's 6.1.31 build 11 retest still fails when switching in place from Apple's shorter keyboard to the taller LIME keyboard: LINE keeps the old composer position and LIME covers the whole field until dismiss/reopen. Investigate keyboard-frame publication during an active input-mode switch, then retest both rotation and Apple→LIME transitions plus the private bottom-reachability case. Keep the private host framework confidential. Do not cap or shrink layouts. Android is not in scope.

## Product work

- feat#144 iOS: add user-selectable split/two-hand operation for larger screens or landscape/tablet-like use, plus one-hand phone alignment modes that shift/fit the keyboard left or right while preserving the default full-width behavior when disabled. Existing iPad split hooks (`KeyboardView.splitMode`, `split_keyboard_mode`, `LayoutMetrics.KeyboardRow.splitGapFraction`, `_ipad` / `_ipad_narrow` variants) should be audited first; then add missing phone one-hand placement, settings/UI persistence, safe-area/orientation behavior, and regression checks. Android is not in scope.
