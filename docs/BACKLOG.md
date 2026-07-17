# LIME IME Backlog

Public backlog for confirmed unresolved fixes and product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; completed, shipped, or closed scopes stay in their issue docs instead of here.

Last reviewed: 2026-07-17

## Pending fixes

- fix#139 iOS: commit `7c067c64` fixes the LINE rotation path, and the private reporter's 6.1.31 retest now reaches the true bottom in landscape, but locked portrait still fails. The maintainer's build 11 retest also fails when switching in place from Apple's shorter keyboard to the taller LIME keyboard: LINE keeps the old composer position and LIME covers the whole field until dismiss/reopen. Investigate keyboard-frame publication during an active input-mode switch, then compare fresh LIME presentation against in-place switching in locked portrait. Keep the private host framework confidential. Do not cap or shrink layouts. Android is not in scope.

## Product work

- feat#N04 Web/store support: create one canonical LIME support area for Google Play (`org.limeime`) and Apple App Store (`6784694460`) users. Make email to `limeimetw@gmail.com` the primary private-support path, retain the manual/FAQ and public GitHub options, add Android/iOS reporting guidance and scam/privacy warnings, and link back to the exact official store listings. After deployment, update both stores' support metadata to the same verified URL instead of maintaining separate support systems. Until then, use direct email support for reports requiring `.cin` files, screenshots, recordings, or private details. See `docs/FEAT_#N04.md`.
- feat#144 iOS: add user-selectable split/two-hand operation for larger screens or landscape/tablet-like use, plus one-hand phone alignment modes that shift/fit the keyboard left or right while preserving the default full-width behavior when disabled. Existing iPad split hooks (`KeyboardView.splitMode`, `split_keyboard_mode`, `LayoutMetrics.KeyboardRow.splitGapFraction`, `_ipad` / `_ipad_narrow` variants) should be audited first; then add missing phone one-hand placement, settings/UI persistence, safe-area/orientation behavior, and regression checks. Android is not in scope.
