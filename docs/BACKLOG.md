# LIME IME Backlog

Public backlog for confirmed unresolved fixes and product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; completed, shipped, or closed scopes stay in their issue docs instead of here.

Last reviewed: 2026-07-19

## Pending fixes
- fix#169 Android: restore the pre-v6.1.33 phone behavior where `分離鍵盤 = 開啟` renders a split keyboard in portrait and landscape, while `僅橫向` remains landscape-only. Preserve numpad split exclusion and prevent one-hand anchoring from overriding an active portrait split. Retest only after a newer targeted Android build is available. See `docs/#169_ISSUE.md`.
- fix#161 iOS/reporter follow-up: Android GitHub APK v6.1.33 includes the escaped `pword` prefix management search, runtime parity, and manual add/update/delete cache invalidation follow-ups, and issue #161 is open pending reporter confirmation. iOS source includes the corresponding management/runtime/cache-reset changes but still needs corrected-source XCTest/Xcode Cloud validation and a verified newer TestFlight/App Store build. See `docs/#161_ISSUE.md`.


## Product work

- feat#159 Android+iOS: add 三碼輸入法 to LIME's built-in/downloadable input-method catalog for the next coordinated release, targeting v6.1.34. Start from the contributor-provided table version `v.20260715.1`, identify the table author as `無書自通`, use `https://3code-type.github.io/` as the official source/update location, and preserve the stated free/non-commercial distribution condition. Configure the table for `LIME+數字符號鍵盤`, verify Android and iOS conversion/import, catalog metadata, attribution, default keyboard selection, and basic lookup/input behavior, then publish a reporter-testable build through the normal release gates. Source: GitHub issue #159.
- feat#N04 Web/store support: create one canonical LIME support area for Google Play (`org.limeime`) and Apple App Store (`6784694460`) users. Make email to `limeimetw@gmail.com` the primary private-support path, retain the manual/FAQ and public GitHub options, add Android/iOS reporting guidance and scam/privacy warnings, and link back to the exact official store listings. After deployment, update both stores' support metadata to the same verified URL instead of maintaining separate support systems. Until then, use direct email support for reports requiring `.cin` files, screenshots, recordings, or private details. See `docs/FEAT_#N04.md`.
- feat#144 iOS: add user-selectable split/two-hand operation for larger screens or landscape/tablet-like use, plus one-hand phone alignment modes that shift/fit the keyboard left or right while preserving the default full-width behavior when disabled. Existing iPad split hooks (`KeyboardView.splitMode`, `split_keyboard_mode`, `LayoutMetrics.KeyboardRow.splitGapFraction`, `_ipad` / `_ipad_narrow` variants) should be audited first; then add missing phone one-hand placement, settings/UI persistence, safe-area/orientation behavior, and regression checks. Android is not in scope.
