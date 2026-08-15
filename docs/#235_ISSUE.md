# Issue #235: Incomplete input code commits a prefix candidate instead of raw text

## Problem statement

In LIME 6.1.38, while a Chinese input method is active, a code that has no full-code mapping can leave a shorter prefix mapping selected by default. Pressing Space or Enter then commits that prefix candidate and leaves the remaining characters as raw text instead of committing the complete typed string.

The reporter supplied the concrete Array10 example `12345`: Space or Enter produces `一2345` instead of `12345`. They report the same class of failure with alphabetic input under another input method and on both Android and iOS.

## Reporter-supplied environment and reproduction

- App version: 6.1.38
- Platforms: Android and iOS
- Confirmed input method example: Array10
- Additional input method shown in the report: identity not stated
- Evidence: two video assets and three screenshots attached to the issue

Reproduction:

1. Activate Array10 in Chinese mode.
2. Type `12345`, where the complete sequence has no mapping but the prefix `1` maps to `一`.
3. Press Space or Enter.
4. Observe `一2345` instead of the expected raw string `12345`.

The reporter also demonstrates an alphabetic sequence such as `abcde` under another input method, indicating that the defect is not specific to Array10 digits.

## Evidence and source analysis

Both platforms intentionally prepend a composing-code echo candidate containing the complete typed code. Their default-selection policies then choose which candidate Space or Enter commits.

- Android `LIMEService.defaultHighlightedCandidateIndex(...)` promoted the candidate after the composing echo whenever that record was marked `partialMatchToCode`, even when its code represented only a shorter prefix. Separately, `defaultServiceSelectedCandidate(...)` selected the second candidate for every soft-keyboard result, so the commit target could disagree with the visible composing-code echo.
- iOS `CandidateSelectionPolicy.defaultHighlightedCandidateIndex(...)` had the same unconditional `partialMatchToCode` promotion. `KeyboardViewController.handleEnterOrSpace(...)` commits that selected candidate for non-phonetic Chinese input methods.
- Android and iOS search results classify non-exact database matches as partial-code records. Therefore a list shaped like `[raw "12345", partial "1" → "一"]` selected `一`, and the continuous-input commit path preserved the unconsumed suffix `2345`.

A focused Android regression using that exact candidate-list shape failed before the production change: expected selected index `0`, actual index `1`. Existing iOS candidate-selection tests covered exact-code promotion, code inequality for a punctuation record, arbitrary non-code candidates, and browse-only lists, but not a shorter `partialMatchToCode` record. Android had no focused unit test for this default-selection boundary.

## Root cause

The shared selection rule conflated two different conditions:

1. a candidate matching the complete typed code, which is a valid default commit target, and
2. a partial-code candidate matching only a shorter prefix, which must remain available for explicit selection but must not replace the raw composing text by default.

Android also maintained a second, broader service-side default selector that always chose the second soft-keyboard candidate. That duplicate policy allowed the commit target to remain wrong even if the candidate-view highlight chose the raw echo.

## Proposed solution

- Android: stop promoting a candidate solely because it is a partial-code record, and derive the service commit target from the same default-highlight policy used by the candidate view.
- iOS: stop promoting a candidate solely because it is a partial-code record.
- Preserve promotion when the first real candidate is an exact-code record or its non-empty code equals the complete composing-code echo. This retains exact Chinese mappings and the established full-width punctuation behavior.
- Keep partial candidates visible and manually selectable.

## Platform impact

### Android

Confirmed by the reporter on 6.1.38 and reproduced at the selection-policy boundary. Both the visible highlight and `selectedCandidate` service state participate in the failure, so Android needs the shared-policy change plus removal of the duplicate broader service selection behavior.

### iOS

Confirmed by the reporter on 6.1.38. Source has the same unconditional partial-record promotion and the non-phonetic Space/Enter path commits the selected candidate. iOS needs the equivalent shared-policy change. XCTest execution and device/simulator verification remain pending in an Xcode-capable environment.

## Follow-up questions

No reporter clarification is required to implement the confirmed cross-platform selection defect. If a future runtime check differs from the supplied examples, capture the exact active input method, candidate list, and Space-versus-Enter result separately.

## Verification plan

### Automated

- Android RED/GREEN unit test: `[composing "12345", partial "1" → "一"]` selects the composing candidate as both the visible default and service commit target.
- iOS XCTest: the equivalent candidate list keeps index `0` highlighted.
- Preserve existing exact-match behavior on both platforms, including `,`/`.` full-width punctuation candidates whose code equals the complete composing echo.
- Run Android unit tests, lint, Android-test compilation, and connected instrumentation when an emulator/device is available.
- Run the relevant iOS XCTest suite and Xcode build in an Xcode-capable environment.

### Reporter-visible runtime

On both Android and iOS:

1. With Array10 active, type `12345`, then test Space and Enter separately. Both must preserve `12345` rather than produce `一2345`.
2. Repeat with an alphabetic input method and an incomplete sequence such as the reporter's `abcde` example. The complete raw sequence must be committed.
3. Type a complete code with an exact Chinese mapping. Space/Enter must still commit the exact Chinese candidate.
4. Verify direct tapping of a partial prefix candidate still commits that candidate and retains the intended unconsumed suffix behavior.
5. Verify enabled full-width punctuation defaults remain unchanged.

Ask the reporter to retest only after a newer Android/iOS build containing the accepted fix is available.
