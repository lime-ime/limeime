# Issue #139: iOS TestFlight Array10 numeric keyboard, bottom coverage, and keyboard-size behavior

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/139
- Classification: `bug` + `Usability`
- Source: maintainer-created issue from a private email/TestFlight report. Do not expose reporter identity or private app details in public comments or docs.
- Current state: open, needs iOS investigation and TestFlight verification.
- Public acknowledgement: not needed. This is a maintainer-created tracking issue for private-email evidence.

## Problem statement

An iOS TestFlight reporter using the Array10 (`行列10`) input method reported three keyboard issues:

1. In an input field reported as `input type="num"`, LIME switches away from the Array10 phone-style numeric keyboard and shows a generic English/symbol-style keyboard. The reporter expects the Array10 phone-style numeric keyboard to remain available because it can directly input numbers.
2. Bottom page or content areas in the host app cannot be fully displayed because the keyboard covers them.
3. The keyboard-size preference appears to enlarge only the English keyboard. The Array10 phone-style numeric keyboard does not appear to change size consistently.

The issue body says the reporter supplied videos in the email thread, but automated GitHub intake did not expose those attachments. Current analysis is therefore based on the public issue summary and source inspection, not on frame-by-frame video review.

## Source evidence inspected

### iOS input-type routing

- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`
  - `updateInputModeForCurrentField()` sets `mEnglishOnly = true` for `.numberPad`, `.decimalPad`, `.asciiCapableNumberPad`, and `.phonePad`.
  - `layoutIdForCurrentInputField(...)` then routes:
    - `.phonePad` to `phone_number`
    - `.numberPad`, `.decimalPad`, and `.asciiCapableNumberPad` to `symbols1`
    - English-only non-numeric fields to the configured English layout
    - Chinese mode with at least one activated IM to `resolvedActiveLayoutId`
  - `applyLayoutForCurrentInputField()` uses that static routing and therefore cannot currently choose the active IM's Array10 `phone_simple` layout for numeric fields.
- `LimeIME-iOS/Shared/Models/KeyboardTypePolicy.swift` mirrors the forced-English category in a separately tested helper. Runtime routing currently uses the inline `KeyboardViewController` switches above rather than calling that helper directly.
- `LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift` currently asserts the forced-English categories and the live layout resolver's `.numberPad` -> `symbols1` behavior, so the current test suite protects the behavior the reporter is challenging.

### Array10 phone-style layout availability

- `KeyboardViewController.resolvedLayoutId(for:)` already maps `array10` to `phone_simple` when the DB keyboard config is stale or missing a direct loadable layout. Comments explicitly describe `array10` as using the phone-style fallback.
- `LimeIME-iOS/LimeKeyboard/Layouts/phone_simple.json` exists and contains a four-row phone-style numeric layout with digits, punctuation, `ABC`, `123`, delete, space, dismiss, and return keys.

### iOS keyboard-size path

- `PreferencesTabView.swift` stores `keyboard_size` as values `1.2`, `1.1`, `1`, `0.9`, and `0.8`.
- `KeyboardViewController.loadSettings()` parses `keyboard_size` into `keyboardSize`.
- `applyFeedbackSettings()` assigns `keyboardView.keySizeScale = keyboardSize` and calls `applyHeight()` when scale changes.
- `KeyboardView.keySizeScale` rebuilds rows and its `rowHeight` / `bottomRowHeight` multiply by the scale.
- `KeyboardView.preferredHeight` sums actual row heights, and `KeyboardViewController.applyHeight()` updates the extension view height constraint from that preferred height plus the candidate bar.

This code path suggests all JSON-backed keyboard layouts, including `phone_simple`, should receive the same row-height scale once `applyFeedbackSettings()` runs. The reporter's observation may indicate a stale settings application path, an input-type switch that is showing a different layout than expected, a host extension height cap, or a visual comparison issue caused by candidate-bar/row-count differences rather than a simple missing scale multiplier.

### Android comparison

- Android `LIMEService.getRestrictedFieldKeyboardMode(...)` routes `TYPE_CLASS_NUMBER` to `LIMEKeyboardSwitcher.MODE_PHONE`, while non-number restricted fields use text/symbol behavior.
- Android `TYPE_CLASS_PHONE` also routes to `MODE_PHONE`.

This makes the iOS `.numberPad` -> `symbols1` route different from Android's number-field behavior, and it supports investigating an iOS-specific parity fix. No Android source change is implied by this iOS TestFlight report.

## Likely root cause / investigation hypothesis

The numeric-keyboard portion is likely caused by iOS treating numeric fields as forced-English and routing `.numberPad` / `.decimalPad` / `.asciiCapableNumberPad` to `symbols1` without considering the active IM. For Array10, the active IM's resolved layout can be `phone_simple`, but that path is bypassed once `mEnglishOnly` is set for numeric fields.

The keyboard-size portion is less certain. The inspected size-scaling code appears layout-agnostic and should apply to `phone_simple`, but it depends on `loadSettings()` / `applyFeedbackSettings()` running after the preference changes and on the extension accepting the updated height. The issue needs device/TestFlight reproduction before claiming a specific code defect there.

The bottom-content coverage portion may be an iOS custom-keyboard height/safe-area interaction, a host-app layout issue, or a LIME height problem if LIME reports a larger-than-expected extension height after size/candidate-bar changes. Because the affected app is private, the first implementation pass should focus on reproducible keyboard-extension behavior in local/simulator test fields and ask the private reporter for non-sensitive reproduction details if needed.

## Proposed fix / investigation plan

1. Reproduce on iOS TestFlight or simulator with Array10 active and fields using number, decimal, ASCII-capable number, and phone keyboard types.
2. Decide whether iOS numeric fields should mirror Android by routing `.numberPad` / `.decimalPad` / `.asciiCapableNumberPad` to an active numeric/phone-style layout when the active IM is Array10, while keeping email/password and other restricted text fields forced English.
3. Update `layoutIdForCurrentInputField(...)` / `updateInputModeForCurrentField()` and tests so Array10 numeric fields can use `phone_simple` or an equivalent numeric layout when appropriate.
4. Verify that switching from numeric fields back to normal text fields restores the active IM layout and that forced-English fields such as email/password still behave correctly.
5. Separately reproduce keyboard-size changes on `phone_simple` and confirm whether `keySizeScale` and `applyHeight()` update the extension height after changing the preference.
6. For bottom coverage, measure the extension height and confirm whether the private app respects iOS keyboard inset notifications. If only the private app fails to adjust content for the keyboard, treat that as host-app behavior unless LIME is reporting an excessive or stale height.

## Verification plan

- Add or update iOS unit tests around `layoutIdForCurrentInputField(...)` for Array10/numeric-field routing instead of only the current generic `.numberPad` -> `symbols1` assertion.
- Add source-level coverage or a lightweight controller test to ensure keyboard-size preference changes propagate to `KeyboardView.keySizeScale` and `applyHeight()` for `phone_simple`.
- Manual TestFlight/simulator verification:
  - Array10 normal text field shows the Array10 phone-style layout.
  - Array10 number/decimal fields keep an appropriate numeric/phone-style layout and can input digits directly.
  - Email/password fields remain forced English where appropriate.
  - Keyboard-size settings visibly affect English and Array10 phone-style layouts.
  - Bottom content in a reproducible test app remains visible or scroll-adjustable when the keyboard is shown.

## Platform impact

- iOS: confirmed report scope. The likely routing issue is in iOS keyboard-extension code and current iOS tests assert the existing numeric-field behavior.
- Android: analogous Android input-type routing was inspected and already sends number/phone fields through phone-style keyboard modes. No Android change is implied unless separate Android evidence appears.

## Follow-up / retest condition

Keep the issue open and assigned for iOS investigation. No public comment or reporter retest request is needed until a newer TestFlight build contains a targeted iOS fix or the maintainer needs additional non-private reproduction details from the email reporter.
