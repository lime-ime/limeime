# LIME IME Backlog

Public backlog for confirmed unresolved fixes and product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; completed, shipped, or closed scopes stay in their issue docs instead of here.

Last reviewed: 2026-07-11

## Pending fixes

- fix#154 Android: recover cleanly when inline voice recognition reports an error. Ensure the listening indicator and candidate-strip error state terminate, normal typing remains responsive, voice input can be retried, and any delegated recognizer fallback runs at most once. Capture the reporter's device/provider/error details before narrowing the root cause. No iOS impact is confirmed by the Android report.
- fix#153 iOS: restore Array30 `w` + digit (`w0`–`w9`) symbol-code candidate lookup. The iOS composing path currently treats Array30's published `imkeys` as authoritative and rejects the digit before lookup, while Android has a dedicated guarded `w[0-9]*` exception. Add the equivalent narrow iOS dispatch rule and regression tests without making digits general Array30 roots. Android requires no source change unless parity testing finds a separate regression.

## Product work

- feat#144 iOS: add user-selectable split/two-hand operation for larger screens or landscape/tablet-like use, plus one-hand phone alignment modes that shift/fit the keyboard left or right while preserving the default full-width behavior when disabled. Existing iPad split hooks (`KeyboardView.splitMode`, `split_keyboard_mode`, `LayoutMetrics.KeyboardRow.splitGapFraction`, `_ipad` / `_ipad_narrow` variants) should be audited first; then add missing phone one-hand placement, settings/UI persistence, safe-area/orientation behavior, and regression checks. Android is not in scope.
