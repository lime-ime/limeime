# LIME IME Backlog

Public backlog for confirmed unresolved fixes and product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; completed, shipped, or closed scopes stay in their issue docs instead of here.

Last reviewed: 2026-07-12

## Pending fixes

- fix#139 iOS: continue investigating host-app bottom content coverage after the attempted keyboard-height reporting fix. A private reporter reproduced the issue on LIME 6.1.28, iPhone 17 Pro Max, and iOS 26.6 beta 4 across keyboard sizes from minimum to extra large, with the scrollbar unable to reach the bottom. Use the private recording for device instrumentation, compare LIME's published root/input-view frame with the rendered keyboard height, and verify against third-party keyboards that do not reproduce the behavior. Android is not in scope.

- fix#155 Android+iOS: restore Array30 `hg#` digit symbol-list lookup for the populated `hg0`, `hg1`, `hg2`, `hg8`, and `hg9` groups in `Database/array.limedb`. Both platforms currently guard only the older `w[0-9]*` symbol family. Add a narrow verified-prefix policy and regression tests without making digits general Array30 roots. Track in `docs/#155_ISSUE.md`.

- fix#153 iOS: restore Array30 `w` + digit (`w0`–`w9`) symbol-code candidate lookup. The iOS source fix and focused helper tests landed in commit `83c5d5af320469af262f98883280ede193c2d1b7`; verify the behavior in a newer TestFlight/App Store build before removing this delivery-QA item. Android already has the equivalent guarded `w[0-9]*` behavior.

## Product work

- feat#144 iOS: add user-selectable split/two-hand operation for larger screens or landscape/tablet-like use, plus one-hand phone alignment modes that shift/fit the keyboard left or right while preserving the default full-width behavior when disabled. Existing iPad split hooks (`KeyboardView.splitMode`, `split_keyboard_mode`, `LayoutMetrics.KeyboardRow.splitGapFraction`, `_ipad` / `_ipad_narrow` variants) should be audited first; then add missing phone one-hand placement, settings/UI persistence, safe-area/orientation behavior, and regression checks. Android is not in scope.
