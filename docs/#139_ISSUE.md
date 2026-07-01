# Issue #139: iOS TestFlight Array10 numeric keyboard, bottom coverage, and keyboard-size behavior

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/139
- Classification: `bug` + `Usability`
- Source: maintainer-created issue from a private email/TestFlight report. Do not expose reporter identity or private app details in public comments or docs.
- Current state: closed by maintainer/project-account on 2026-07-01 after maintainer confirmation that the email-reported issue was fixed in a newer version. Retained closure comments: https://github.com/lime-ime/limeime/issues/139#issuecomment-4849077209 and https://github.com/lime-ime/limeime/issues/139#issuecomment-4849080116. iOS simulator investigation done 2026-06-29 (see "Simulator investigation findings" below) could not reproduce the reporter's exact numeric-field symptom; LIME's numeric-field routing is not reached from web fields. Source changes kept on `master` split pure number routing for Android parity and make the keyboard extension ASCII-capable. No active public retest/watch remains; if the private reporter still sees the Array10 numeric-keyboard, bottom-coverage, or keyboard-size symptoms in a newer TestFlight build, reopen or create a fresh follow-up with the TestFlight version, iOS version, and non-sensitive field details.
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

The issue is closed after maintainer/project-account confirmation. Do not post another public acknowledgement or Android APK retest request for this private-email/TestFlight report. If the private reporter later says a newer TestFlight build still has the Array10 numeric-keyboard, bottom-coverage, or keyboard-size problem, reopen or create a fresh follow-up and ask privately for the TestFlight version, iOS version, exact field markup when available, and a non-sensitive screenshot/video frame.

## Simulator investigation findings (2026-06-29)

Reproduced field-by-field on an iPhone 17 Pro Max simulator (iOS 26.5) with LIME
enabled and the 注音 (Zhuyin) IM active, driving Safari via `idb` against
`docs/keyboard-type-field-test.html` (expanded to a full `type` × `inputmode` ×
`pattern` matrix, including the reporter's literal `type="num"`). Each web field
was focused with LIME active and the resulting keyboard observed.

Observed behaviour (web fields in Safari, LIME active):

| Web field | Keyboard shown |
| --- | --- |
| `type=text` | LIME IM keyboard (注音; would be `phone_simple` for Array10) |
| `type="num"` (invalid type → treated as text) | LIME IM keyboard |
| `type="number"` (bare, no `inputmode`) | **LIME IM keyboard** (= `phone_simple` for Array10) — correct |
| `type="number" inputmode="numeric"` | **iOS system numeric pad** (LIME replaced) |
| `inputmode="numeric"` (on a text field) | iOS system numeric pad |
| `pattern="[0-9]*"` | iOS system numeric pad |

Conclusions:

1. **A bare `type="number"` / `type="num"` field already works.** LIME keeps the
   active-IM keyboard, which for Array10 is `phone_simple` — exactly the keypad the
   reporter wants. No bug in this case.
2. **Fields with `inputmode="numeric"` / `"decimal"` or `pattern="[0-9]*"` are
   taken over by iOS.** iOS substitutes its own system numeric keyboard for these;
   the LIME extension is never invoked, so LIME cannot change what is shown. This is
   Apple platform behaviour (third-party keyboards are not used for number-pad input),
   independent of LIME's routing.
3. **No tested web field routes to LIME's `symbols1` or English layout.** The
   reported "switches to a generic English/symbol keyboard" is therefore **not**
   produced by LIME's `layoutIdForCurrentInputField` routing. The earlier
   `.numberPad`/`.decimalPad`/`.asciiCapableNumberPad` → `symbols1`/`phone_number`
   analysis is **unobservable on iOS** — that code path is not reached from Safari /
   WebView numeric inputs, nor from native numeric fields (iOS system-replaces both).
   The `.numberPad`/`.decimalPad` → `phone_number` split is nonetheless **kept in the
   code** for parity with Android's `TYPE_CLASS_NUMBER → phone_number` (it is the correct
   behaviour should LIME ever be shown for those types); it simply can't be verified on iOS.
4. **Could not reproduce the reporter's exact symptom.** Remaining unknowns: the
   reporter's exact field markup (`inputmode`/`pattern`?) and iOS/Safari version. The
   test used 注音 rather than Array10, but routing for the numeric branches is
   IM-independent, so Array10 behaves the same (bare `type=number` → `phone_simple`).

Related platform note (settled during this investigation): LIME's keyboard
extension `Info.plist` had `IsASCIICapable = false`, so iOS substituted its own
keyboard for `.asciiCapable` text fields; this was changed to `true` so LIME shows
for ASCII-capable text fields instead of the iOS built-in keyboard. This is a
separate behaviour improvement, not the #139 fix.

Tooling left in the tree for this investigation (uncommitted):

- `docs/keyboard-type-field-test.html` — full field matrix (kept).
- The `.numberPad`/`.decimalPad` → `phone_number` routing split in
  `KeyboardViewController.layoutIdForCurrentInputField`, plus its unit test
  `testNumberFieldRoutingSplitsPureNumberFromAsciiCapable`, is **kept** (Android parity;
  unverifiable on iOS as above).
- The temporary `NSLog("LIME-KBTYPE …")` instrumentation used during the investigation
  has been **removed**. Note for next time: the LimeKeyboard extension target does
  **not** define `DEBUG`, so a `#if DEBUG` guard is compiled out there — leave such a log
  un-gated, or add `DEBUG` to that target's Debug config.

## Closure notes (2026-07-01)

- Live issue state: closed at 2026-07-01T00:22:50Z by `limeimetw`; labels remain `bug` and `Usability`, assignee remains `jrywu`.
- Retained project-account comments say the issue is closed per maintainer confirmation, and that if a newer TestFlight build still shows the Array10 numeric-keyboard, bottom-coverage, or keyboard-size symptoms, the reporter should provide the test version plus screen/steps and the team can track it again.
- Source/fix evidence visible on GitHub: commit `0863e6f2233518fb7cd23f406ddae94d867a4f7d` (`#139: iOS numeric-field routing investigation + Android-parity split`) updated the iOS keyboard extension routing, ASCII-capable setting, related tests, and this investigation doc.
- Backlog state: remove `fix#139 iOS` from active pending fixes; treat any remaining validation as normal TestFlight/App Store release QA or a new follow-up if the private reporter reproduces the problem again.

## What to communicate to the reporter (private email)

Ask (without exposing private app details publicly):

1. The exact numeric field markup — specifically whether it uses
   `inputmode="numeric"`/`"decimal"` or `pattern="[0-9]*"`, or is a bare
   `type="number"`/`type="num"` with neither.
2. iOS version, and whether the field is in Safari or an in-app web view.
3. If shareable, a frame from the video showing the keyboard.

Explanation to give, depending on the answer:

- **If the field uses `inputmode`/`pattern` (or is a native number-pad field):** iOS
  shows its own numeric keyboard for those, and third-party keyboards (LIME / Array10)
  cannot be displayed there. This is an Apple restriction, not a LIME bug, and cannot
  be fixed in LIME. Suggest a normal text field (or removing `inputmode`/`pattern`) if
  the host wants the Array10 keypad available.
- **If the field is a bare `type="number"`/`type="num"`:** LIME keeps the Array10
  `phone_simple` keypad as expected — this already works in the current build. Ask the
  reporter to retest on the latest TestFlight build.

(The keyboard-size and bottom-content portions of #139 are separate and not covered
by this investigation.)
