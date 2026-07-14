# LIME IME Backlog

Public backlog for confirmed unresolved fixes and product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; completed, shipped, or closed scopes stay in their issue docs instead of here.

Last reviewed: 2026-07-14

## Pending fixes

- fix#139 iOS: continue investigating host-app bottom content coverage after the attempted keyboard-height reporting fix. A private reporter reproduced the issue on LIME 6.1.28, iPhone 17 Pro Max, and iOS 26.6 beta 4 across keyboard sizes from minimum to extra large, with the scrollbar unable to reach the bottom. Use the private recording for device instrumentation, compare LIME's published root/input-view frame with the rendered keyboard height, and verify against third-party keyboards that do not reproduce the behavior. Android is not in scope.
- fix#157 iOS: fix hamburger reverse-lookup selection so changes take effect immediately in the current keyboard session and persist across keyboard reopen/switch cycles. The current iOS keyboard picker writes the extension-private hot preference store, but the committed-candidate reverse-lookup path and parent menu label can still read shared/cold defaults. Use the same hot reverse-lookup source for menu display and lookup execution, preserve relay/Settings synchronization, and verify with an iOS TestFlight/App Store build. Android is not in scope.

## Product work
- fix#157 iOS: reverse lookup selection from the keyboard hamburger menu is not persistent and does not take effect immediately. Investigate the keyboard-owned hot preference store, app→keyboard `PrefInbox`, relay synchronization, and stale-value overwrite risks. Track in `docs/#157_ISSUE.md`.

## Product work

- feat#144 iOS: add user-selectable split/two-hand operation for larger screens or landscape/tablet-like use, plus one-hand phone alignment modes that shift/fit the keyboard left or right while preserving the default full-width behavior when disabled. Existing iPad split hooks (`KeyboardView.splitMode`, `split_keyboard_mode`, `LayoutMetrics.KeyboardRow.splitGapFraction`, `_ipad` / `_ipad_narrow` variants) should be audited first; then add missing phone one-hand placement, settings/UI persistence, safe-area/orientation behavior, and regression checks. Android is not in scope.
