# LIME IME Backlog

Public backlog for confirmed unresolved fixes and product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; completed, shipped, or closed scopes stay in their issue docs instead of here.

Last reviewed: 2026-07-18

## Pending fixes
- fix#161 iOS/reporter follow-up: Android GitHub APK v6.1.33 includes the escaped `pword` prefix management search, runtime parity, and manual add/update/delete cache invalidation follow-ups, and issue #161 is open pending reporter confirmation. iOS source includes the corresponding management/runtime/cache-reset changes but still needs corrected-source XCTest/Xcode Cloud validation and a verified newer TestFlight/App Store build. See `docs/#161_ISSUE.md`.
- fix#160 iOS: the shared-catalog keyboard option `limenumsym` (`LIME+數字符號鍵盤`) renders the ordinary number-row QWERTY layout instead of its dedicated number/symbol layout. Android is reporter-confirmed working. PR #162 merged the missing phone/iPad resources as `c56593f8e3fd76b4a800b66b12ab76b7a6b96f46`; follow-up PR #164 merged the source-independent semantic oracle and corrected full/narrow-iPad punctuation preservation as `66b1577f0c58eee1359d5e921ce57ebaeca9a68d`. Remaining work is phone/full-iPad/narrow-iPad device verification, a newer TestFlight/App Store build containing both merges, and reporter confirmation. Android is not in scope. See `docs/#160_ISSUE.md`.

## Product work

- feat#N04 Web/store support: create one canonical LIME support area for Google Play (`org.limeime`) and Apple App Store (`6784694460`) users. Make email to `limeimetw@gmail.com` the primary private-support path, retain the manual/FAQ and public GitHub options, add Android/iOS reporting guidance and scam/privacy warnings, and link back to the exact official store listings. After deployment, update both stores' support metadata to the same verified URL instead of maintaining separate support systems. Until then, use direct email support for reports requiring `.cin` files, screenshots, recordings, or private details. See `docs/FEAT_#N04.md`.
- feat#144 iOS: add user-selectable split/two-hand operation for larger screens or landscape/tablet-like use, plus one-hand phone alignment modes that shift/fit the keyboard left or right while preserving the default full-width behavior when disabled. Existing iPad split hooks (`KeyboardView.splitMode`, `split_keyboard_mode`, `LayoutMetrics.KeyboardRow.splitGapFraction`, `_ipad` / `_ipad_narrow` variants) should be audited first; then add missing phone one-hand placement, settings/UI persistence, safe-area/orientation behavior, and regression checks. Android is not in scope.
