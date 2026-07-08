# LIME IME Backlog

Public backlog for confirmed unresolved fixes and product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; completed, shipped, or closed scopes stay in their issue docs instead of here.

Last reviewed: 2026-07-08

## Pending fixes

- fix#139 iOS: private-email/TestFlight bottom-content coverage remains pending. The attempted iPhone portrait height cap was abandoned because it made the `keyboard_size` preference ineffective on real device; `applyHeight()` must report the height derived from `KeyboardView.preferredHeight` without rewriting `KeyboardView.keySizeScale`. Continue investigating LIME custom-keyboard height / safe-area behavior across Array10 and Dayi. Track in `docs/#139_ISSUE.md`. No Android APK retest applies.
- fix#145 Android: Android tablet bottom Space/function row can be hidden or clipped on LIME 6.1.27 with Android 10 / iPlay 30 / Boshiamy standard keyboard, normal keyboard size, and split keyboard off; rotation or switching IMEs restores it. Investigate stale IME window/insets or input-view measurement around `LIMEService.onCreateInputView()`, `CandidateInInputViewContainer`, and `LIMEKeyboardBaseView.onMeasure()`. Track in `docs/#145_ISSUE.md`. No reporter retest until a newer Android APK or Google Play build contains a targeted fix.

## Product work

- feat#N03 Android + iOS: extend the existing long-press `123` shortcut to switch directly to the phone/simple numeric keyboard (`phone_simple`) for all IM keyboards/layouts that have an independent `123` key, not only English. Exclude phone-family layouts (`phone*`, including `phone_simple` and phone English/shift variants). Normal tap on `123` keeps the existing symbol-keyboard behavior; only long-press gains the direct phone-simple shortcut. Audit each platform's layout XML/Swift definitions rather than assuming every keyboard exposes `123` the same way.
- feat#143 Android + iOS: replace the old Cangjie-family semicolon-behavior preference with explicit selectable keyboard layouts: `cj_semi` for the standard Cangjie layout and `cj_num_semi` for the Cangjie numeric layout, both with global semicolon behavior. Keep existing `cj` and `cj_num` layouts unchanged. Remove/deprecate the old preference from settings/UI and runtime checks, register/list the new layout IDs on both platforms, persist and restore imported/default layouts using the new IDs, and migrate or fall back safely for upgraded installs that still have the older preference. Track in `docs/#140_ISSUE.md` / issue #143.
- feat#144 iOS: add user-selectable split/two-hand operation for larger screens or landscape/tablet-like use, plus one-hand phone alignment modes that shift/fit the keyboard left or right while preserving the default full-width behavior when disabled. Existing iPad split hooks (`KeyboardView.splitMode`, `split_keyboard_mode`, `LayoutMetrics.KeyboardRow.splitGapFraction`, `_ipad` / `_ipad_narrow` variants) should be audited first; then add missing phone one-hand placement, settings/UI persistence, safe-area/orientation behavior, and regression checks. Android is not in scope.
