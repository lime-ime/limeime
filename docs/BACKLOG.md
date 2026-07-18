# LIME IME Backlog

Public backlog for confirmed unresolved fixes and product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; completed, shipped, or closed scopes stay in their issue docs instead of here.

Last reviewed: 2026-07-18

## Pending fixes

- fix#139 iOS: resolve the remaining locked-portrait host-content reachability path after the private reporter found that only `keyboard_size = large` plus font size `extra large` reaches the true bottom, while changing either preference makes it unreachable again. Reproduce the passing and failing preference combinations, compare rendered keyboard geometry with UIKit's published frame/layout guide and the host scroll inset, and preserve reporter/app/video privacy. Android is not affected by this iOS keyboard-extension lifecycle path. See `docs/#139_ISSUE.md`.
- fix#160 iOS: the shared-catalog keyboard option `limenumsym` (`LIME+數字符號鍵盤`) renders the ordinary number-row QWERTY layout instead of its dedicated number/symbol layout. Android is reporter-confirmed working. PR #162 merged the missing phone/iPad resources to `master` as `c56593f8e3fd76b4a800b66b12ab76b7a6b96f46`, and Xcode Cloud run 16 succeeded for the PR head. A later semantic audit found that the merged iPad generator output loses required punctuation (`_` on full iPad Shift, `_` and `+` on narrow iPad Shift, and `=` on normal narrow iPad). A focused corrective branch now has source-independent RED/GREEN code-and-label assertions and regenerated resources. Remaining work is follow-up review/merge, device verification, release, and reporter confirmation. Android is not in scope. See `docs/#160_ISSUE.md`.

## Product work

- feat#N04 Web/store support: create one canonical LIME support area for Google Play (`org.limeime`) and Apple App Store (`6784694460`) users. Make email to `limeimetw@gmail.com` the primary private-support path, retain the manual/FAQ and public GitHub options, add Android/iOS reporting guidance and scam/privacy warnings, and link back to the exact official store listings. After deployment, update both stores' support metadata to the same verified URL instead of maintaining separate support systems. Until then, use direct email support for reports requiring `.cin` files, screenshots, recordings, or private details. See `docs/FEAT_#N04.md`.
- feat#144 iOS: add user-selectable split/two-hand operation for larger screens or landscape/tablet-like use, plus one-hand phone alignment modes that shift/fit the keyboard left or right while preserving the default full-width behavior when disabled. Existing iPad split hooks (`KeyboardView.splitMode`, `split_keyboard_mode`, `LayoutMetrics.KeyboardRow.splitGapFraction`, `_ipad` / `_ipad_narrow` variants) should be audited first; then add missing phone one-hand placement, settings/UI persistence, safe-area/orientation behavior, and regression checks. Android is not in scope.
