# Issue #147: iOS hybrid English candidate 0 should preserve capital letters

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/147
- Classification: `bug` + `Usability`
- Source: maintainer-created iOS tracking issue by `limeimetw`
- Current state: open and assigned to `jrywu`
- Public acknowledgement: not needed because this is a maintainer-created internal tracking issue with no community reporter to acknowledge

## Problem statement

In iOS English / hybrid-English input, candidate 0 should preserve the exact casing typed by the user. If the user types `ABC` or `iPhone`, the first candidate should remain `ABC` or `iPhone` so acronyms, names, brands, accounts, and other case-sensitive text can be committed without being changed to lowercase.

The current reported behavior is that candidate 0 is normalized to lowercase, for example `ABC` becomes `abc` and `iPhone` becomes `iphone`.

## Source evidence inspected

### iOS English prediction path

- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`
  - `handleEnglishCharacter(code:char:)` appends the typed character to `tempEnglishWord` using `insertChar`, which can preserve one-shot Shift / caps-lock casing.
  - `updateEnglishPrediction()` reads `word = tempEnglishWord`, validates that the host text before the cursor has that suffix, asks `UITextChecker` for completions, and currently builds `mappings` only from those completions.
  - The current iOS prediction code does not prepend a self / composing-code candidate for `word` before the `UITextChecker` suggestions. When `UITextChecker` returns lowercased suggestions, the candidate list can therefore omit the exact typed-case word.
  - If `UITextChecker` returns no completions, `updateEnglishPrediction()` clears suggestions instead of showing the typed word as candidate 0.
  - `commitEnglishSuggestion(_:)` commits only the suffix after `tempEnglishWord`, then appends a space. This is compatible with a candidate whose `word` equals `tempEnglishWord` only if the self-candidate selection path is handled carefully so it does not duplicate text or append an unwanted space.

### iOS SearchServer composing-code path

- `LimeIME-iOS/Shared/Search/SearchServer.swift`
  - `getMappingByCode(_:)` prepends a `Mapping.RecordType.composingCode` echo candidate when table lookup returns results.
  - In the phonetic-table branch, the echo candidate is currently constructed with `code.lowercased()` for both `code` and `word`.
  - In the non-phonetic branch, the echo candidate is constructed with the original `code` for both `code` and `word`.
- This is a second casing-sensitive path to audit if the reported symptom is reproduced while composing through the Chinese IM candidate flow rather than pure English prediction. Lookup/cache normalization can remain lowercase, but the user-visible composing echo should preserve typed casing if it is used as candidate 0.

### Android comparison

- `LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java`
  - `buildEnglishPredictionCandidates(String word, List<Mapping> suggestions)` creates a first `Mapping` whose `word` is the passed non-empty `word`, marks it as a composing-code record, and then appends any suggestion records. It returns an empty list for null or empty input.
  - Android `updateEnglishPrediction()` calls `buildEnglishPredictionCandidates(tempEnglishWord.toString(), suggestions)`, so the first candidate is based on the raw typed English buffer.
- `LimeStudio/app/src/androidTest/java/net/toload/main/hd/LIMEServiceTest.java`
  - `englishPredictionCandidatesKeepComposingWordWhenSuggestionsAreEmpty()` asserts that the typed word remains the only candidate when suggestions are empty.
  - `englishPredictionCandidatesKeepSuggestionsAfterComposingWord()` asserts that suggestion records follow the typed self candidate.

### Existing iOS tests

- `LimeIME-iOS/LimeTests/SearchServerTest.swift` has provider-injection coverage for English suggestions through `SearchServer.getEnglishSuggestions(...)`, but that does not cover `KeyboardViewController.updateEnglishPrediction()` building the visible English candidate list.
- `LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift` has source-level coverage for English layout, auto-capitalization, browse-only lists, and candidate-selection policy, but no focused guard that iOS English prediction prepends the exact typed-case self candidate.
- Existing SearchServer tests that compare `result[0].word.lowercased()` also would not catch a regression where the candidate word has already been lowercased.

## Likely root cause / investigation hypothesis

The higher-confidence iOS root cause is a parity gap with Android's English prediction candidate builder. Android intentionally inserts the typed English buffer as candidate 0 before dictionary / suggestion results, while iOS currently displays only `UITextChecker` completions. Because `UITextChecker` can normalize suggestions to lowercase and iOS has no typed-word self candidate in front of them, mixed-case input can lose its original casing in the candidate strip.

There is also a separate iOS `SearchServer` phonetic-table echo path that lowercases the composing-code echo. If reproduction shows the problem occurs through Chinese IM composing rather than English prediction, that branch should also be fixed or covered so candidate 0 remains a user-facing echo of the typed input.

Keep the final implementation scoped to the path verified by tests/manual reproduction. Do not change lookup normalization unless a focused test proves it is part of the visible-candidate bug.

## Proposed fix / investigation plan

1. Add an iOS helper equivalent to Android `buildEnglishPredictionCandidates(...)` that prepends a self / composing-code `Mapping` for the exact `tempEnglishWord` before appending `UITextChecker` suggestions.
2. Use the helper from `updateEnglishPrediction()` so `ABC`, `iPhone`, and suggestion-empty inputs still show the exact typed string as candidate 0.
3. Decide whether selecting the self candidate should be a no-op / explicit commit path or can safely use the existing English suggestion suffix-commit path. Preserve current text in the host field and avoid duplicating the typed word or adding an unexpected space.
4. Add focused iOS tests for:
   - typed word candidate 0 preserves casing when suggestions are empty
   - suggestions appear after the self candidate
   - mixed-case examples such as `ABC` and `iPhone` are not lowercased by candidate construction
   - selecting candidate 0 does not duplicate the already-inserted typed word
5. Audit the `SearchServer` phonetic composing-code echo if reproduction or tests show the issue goes through that Chinese IM candidate path.

## Verification plan

- Add or update iOS unit tests for the candidate builder / English prediction list ordering described above.
- Manual iOS keyboard verification after a TestFlight or local simulator build:
  - In English / hybrid-English input, type `ABC` and confirm candidate 0 shows `ABC`, not `abc`.
  - Type `iPhone` and confirm candidate 0 shows `iPhone`, not `iphone`.
  - Confirm regular English completions still appear after candidate 0.
  - Confirm selecting candidate 0 does not duplicate the typed word and does not break punctuation-after-picked-English behavior.
- If the Chinese IM composing path is also changed, manually verify table/phonetic lookup still returns expected Chinese candidates and candidate ordering.

## Platform impact

- iOS: confirmed tracking scope. The inspected iOS English prediction path lacks Android's typed self-candidate builder, and the iOS SearchServer phonetic echo has a separate lowercasing risk if that path is involved.
- Android: analogous Android English prediction code already prepends the raw typed English word via `buildEnglishPredictionCandidates(...)`, and Android tests guard the suggestion-empty and suggestion-present cases. No Android APK retest applies unless Android is changed separately.

## Follow-up / retest condition

Keep the issue open until an iOS source fix lands and is verified in an iOS build/TestFlight. Do not post an Android APK retest request. No public acknowledgement is needed unless a maintainer wants to add a progress note after the fix is available.
