# LIME IME Backlog

Public backlog for confirmed unresolved fixes and product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; completed, shipped, or closed scopes stay in their issue docs instead of here.

Last reviewed: 2026-07-15

## Pending fixes

- fix#139 iOS: fix stale host geometry after live LIME keyboard changes. A private 6.1.28 retest could not scroll a form to its true bottom, and the maintainer can reproduce LINE's message field becoming partly covered after rotating with LIME visible; rotating back preserves the overlap until the keyboard is dismissed and reopened. Instrument LIME's final root/input-view frame, `keyboardLayoutGuide`, keyboard frame notifications, and host insets across rotation, keyboard-size changes, and four-row/five-row layout switches. Do not cap or shrink layouts. Android is not in scope.

## Product work

- feat#144 iOS: add user-selectable split/two-hand operation for larger screens or landscape/tablet-like use, plus one-hand phone alignment modes that shift/fit the keyboard left or right while preserving the default full-width behavior when disabled. Existing iPad split hooks (`KeyboardView.splitMode`, `split_keyboard_mode`, `LayoutMetrics.KeyboardRow.splitGapFraction`, `_ipad` / `_ipad_narrow` variants) should be audited first; then add missing phone one-hand placement, settings/UI persistence, safe-area/orientation behavior, and regression checks. Android is not in scope.
