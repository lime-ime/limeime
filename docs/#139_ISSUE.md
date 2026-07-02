# Issue #139: iOS TestFlight Array10 numeric keyboard, bottom coverage, and keyboard-size behavior

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/139
- Classification: `bug` + `Usability`
- Source: maintainer-created issue from a private email/TestFlight report. Do not expose reporter identity or private app details in public comments or docs.
- Current state: reopened by `limeimetw` on 2026-07-01 after the private reporter said iOS TestFlight 6.1.27 still covers bottom content; retained reopen comment: https://github.com/lime-ime/limeime/issues/139#issuecomment-4857781866. On 2026-07-02 the reporter added that the coverage is not limited to Array10: Dayi also covers bottom content, and the covered range is larger. Treat the active follow-up as an iOS bottom-content coverage symptom affecting at least some table-keyboard layouts. The earlier numeric-field report remains unresolved only as private-reporter context: iOS simulator investigation done 2026-06-29 (see "Simulator investigation findings" below) could not reproduce the exact numeric-field symptom, and LIME's numeric-field routing is not reached from tested web fields.
- Public acknowledgement: not needed. This is a maintainer-created tracking issue for private-email evidence.

## Problem statement

An iOS TestFlight reporter using the Array10 (`行列10`) input method reported three keyboard issues:

1. In an input field reported as `input type="num"`, LIME switches away from the Array10 phone-style numeric keyboard and shows a generic English/symbol-style keyboard. The reporter expects the Array10 phone-style numeric keyboard to remain available because it can directly input numbers.
2. Bottom page or content areas in the host app cannot be fully displayed because the keyboard covers them.
3. The keyboard-size preference appears to enlarge only the English keyboard. The Array10 phone-style numeric keyboard does not appear to change size consistently.

The issue body says the reporter supplied videos in the email thread, but automated GitHub intake did not expose those attachments. Current analysis is therefore based on the public issue summary, the retained project-account comments, private email snippets, and source inspection, not on frame-by-frame video review. On 2026-07-01 the reporter added through the private channel that TestFlight 6.1.27 still covers bottom content, while the native iOS keyboard and other third-party keyboards reportedly do not. On 2026-07-02 the reporter added that Dayi also shows the bottom-coverage symptom and appears to cover a larger range than Array10.

## Source evidence inspected

### iOS input-type routing

- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`
  - `updateInputModeForCurrentField()` sets `mEnglishOnly = true` for `.numberPad`, `.decimalPad`, `.asciiCapableNumberPad`, and `.phonePad`.
  - Current `layoutIdForCurrentInputField(...)` routes `.phonePad`, `.numberPad`, and `.decimalPad` to `phone_number`, keeps `.asciiCapableNumberPad` on `symbols1`, routes English-only non-numeric fields to the configured English layout, and routes Chinese mode with at least one activated IM to `resolvedActiveLayoutId`.
  - Comments in the numeric routes explicitly say the pure-number split is kept for Android parity even though iOS system-replaces tested numeric fields before the extension can observe them.
- `LimeIME-iOS/Shared/Models/KeyboardTypePolicy.swift` mirrors the forced-English category in a separately tested helper. Runtime routing currently uses the inline `KeyboardViewController` switches above rather than calling that helper directly.
- `LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift` includes coverage for the forced-English categories and current numeric-field routing split.

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

The original numeric-keyboard hypothesis was narrowed by simulator investigation: tested Safari/WebView numeric field shapes either keep LIME active for bare `type=number` / invalid `type=num` or are taken over by the iOS system numeric keyboard for `inputmode` / `pattern` cases where third-party keyboards are not invoked. Keep the source changes that split pure numeric routing and make the extension ASCII-capable, but do not treat the numeric-field route as the current active defect without newer private details.

The keyboard-size portion is less certain. The inspected size-scaling code appears layout-agnostic and should apply to `phone_simple`, but it depends on `loadSettings()` / `applyFeedbackSettings()` running after the preference changes and on the extension accepting the updated height. The issue needs device/TestFlight reproduction before claiming a specific code defect there.

The bottom-content coverage portion is now the active follow-up. The 6.1.27 reports say native and other third-party keyboards do not cover the same bottom content, and that both Array10 and Dayi can cover bottom content with Dayi covering a larger range. This makes a LIME custom-keyboard height/safe-area interaction plausible and suggests the investigation should compare layout-specific row counts, candidate-bar state, and preferred-height calculations across table keyboards rather than treating the problem as Array10-only. Still distinguish a LIME extension-height problem from private host-app content-inset behavior until reproduced with a non-sensitive test field or measurable keyboard frame/height evidence.

## Proposed fix / investigation plan

1. Reproduce the bottom-coverage symptom on iOS TestFlight or simulator with Array10 and Dayi active, preferably in a non-sensitive test view that mimics the private app's bottom input/content area.
2. Measure the extension height path: `KeyboardView.preferredHeight`, `activeCandidateBarHeight`, `emojiSearchHeaderHeight`, layout row counts, per-layout row heights, and `KeyboardViewController.applyHeight()`'s `view.heightAnchor` constant.
3. Compare LIME's reported custom-keyboard height/frame for Array10, Dayi, native iOS, and another third-party keyboard on the same device/orientation when possible.
4. Check whether candidate bar, emoji/search header state, keyboard-size preference, orientation, safe-area, layout-specific row count, or iPhone compatibility scaling can leave an excessive or stale height constraint after layout changes.
5. Keep the numeric-field investigation separate. Ask privately for exact field markup only if the reporter still sees the numeric-keyboard symptom in addition to the confirmed 6.1.27 bottom-coverage follow-up.

## Verification plan

- Add source-level coverage or a lightweight controller test to ensure keyboard-size preference changes propagate to `KeyboardView.keySizeScale` and `applyHeight()` for `phone_simple` without leaving stale height.
- Add or update focused tests for any discovered height/safe-area calculation change.
- Manual TestFlight/simulator verification:
  - Array10 and Dayi normal text fields show the intended layouts without covering the test app's bottom content beyond the expected keyboard frame.
  - Candidate bar, expanded candidates, emoji/search states, and keyboard-size changes do not leave excessive keyboard height.
  - Keyboard-size settings visibly affect English and Array10 phone-style layouts.
  - Bottom content in a reproducible test app remains visible or scroll-adjustable when the keyboard is shown.

## Platform impact

- iOS: confirmed report scope. The active follow-up is bottom-content coverage on TestFlight 6.1.27, now reported for Array10 and Dayi with Dayi covering a larger range. Numeric-field behavior remains documented but is not the currently reproduced defect after simulator investigation.
- Android: analogous Android input-type routing was inspected and already sends number/phone fields through phone-style keyboard modes. No Android change is implied unless separate Android evidence appears.

## Follow-up / retest condition

The issue is open after the private reporter's TestFlight 6.1.27 bottom-coverage follow-up. Do not post another generic public acknowledgement or Android APK retest request for this private-email/TestFlight report. A focused public note is acceptable when new private evidence changes the tracking scope without exposing sender identity or private app details. Next public update after that should wait for a focused iOS source fix, TestFlight build, or maintainer/private-reporter clarification. If more private details are needed, ask for iOS version, device/orientation, keyboard-size setting, whether the candidate bar/emoji/search panel is visible, which input method/layout is active, and a non-sensitive screen frame showing the covered bottom area.

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

- State at closure event, superseded by the reopen below: closed at 2026-07-01T00:22:50Z by `limeimetw`; labels remained `bug` and `Usability`, assignee remained `jrywu`.
- Retained project-account comments say the issue is closed per maintainer confirmation, and that if a newer TestFlight build still shows the Array10 numeric-keyboard, bottom-coverage, or keyboard-size symptoms, the reporter should provide the test version plus screen/steps and the team can track it again.
- Source/fix evidence visible on GitHub: commit `0863e6f2233518fb7cd23f406ddae94d867a4f7d` (`#139: iOS numeric-field routing investigation + Android-parity split`) updated the iOS keyboard extension routing, ASCII-capable setting, related tests, and this investigation doc.
- Backlog state: remove `fix#139 iOS` from active pending fixes; treat any remaining validation as normal TestFlight/App Store release QA or a new follow-up if the private reporter reproduces the problem again.

## Reopen notes (2026-07-01)

- Live issue state: reopened at 2026-07-01T16:38:20Z by `limeimetw` after project-account comment https://github.com/lime-ime/limeime/issues/139#issuecomment-4857781866.
- New private-reporter fact: iOS TestFlight 6.1.27 still covers bottom content; reporter says the native iOS keyboard and other third-party keyboards do not cover the same bottom content.
- Current tracking scope: iOS bottom-content coverage / keyboard height or safe-area behavior. Numeric-field routing remains a documented prior investigation, not the active 6.1.27 failure unless the reporter provides new numeric-field evidence.
- Backlog state: restore `fix#139 iOS` as an active/pending iOS follow-up for bottom-content coverage. No Android APK retest applies.

## Follow-up evidence (2026-07-02)

- New private-reporter fact: iOS 26.6 beta / TestFlight 6.1.27 shows the bottom-content coverage symptom not only with Array10, but also with Dayi, and the Dayi covered range is reportedly larger.
- Public issue update: post a scoped note that the private reporter expanded the affected layout evidence to Dayi as well, without exposing the reporter's email, private app, or video content.
- Investigation implication: compare Array10 and Dayi layout heights/row counts/candidate-bar state under the same device, orientation, and keyboard-size setting. Do not keep the active scope Array10-only.

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
