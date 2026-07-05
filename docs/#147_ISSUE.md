# Issue #147: iOS hybrid English candidate 0 lowercases typed capital letters

## Problem statement

Maintainer-created issue #147 reports that iOS hybrid English input does not preserve the user's typed capitalization in candidate 0. When the user types English with capital letters, the first candidate should echo the original typed text, but the iOS candidate strip can show the value lowercased instead.

Issue: https://github.com/lime-ime/limeime/issues/147

## Reported reproduction

1. Use LIME on iOS with hybrid English input / Chinese IM candidate flow.
2. Type an English string containing uppercase letters, such as `ABC` or `iPhone`.
3. Observe candidate 0 in the candidate strip.

Expected: candidate 0 preserves the typed string, such as `ABC` or `iPhone`.

Actual: candidate 0 is lowercased, such as `abc` or `iphone`.

## Evidence summary

The report is a maintainer-created iOS bug tracking issue. The user-visible symptom affects English proper nouns, abbreviations, account names, brand names, and other mixed English text where capitalization matters.

There are no public reporter comments yet. No public acknowledgement is needed because the issue was created by the project account.

## Code inspection notes

Relevant iOS candidate construction is in `LimeIME-iOS/Shared/Search/SearchServer.swift`:

- `getMappingByCode(_:)` prepends a `Mapping.RecordType.composingCode` echo candidate so candidate 0 represents the current typed code.
- In the phonetic-table branch, the echo candidate is currently constructed with `code.lowercased()` for both `code` and `word`.
- In the non-phonetic branch, the echo candidate is constructed with the original `code` for both `code` and `word`.
- `assembleResultList(echo:dbResults:)` places the echo mapping at index 0 before optional runtime or English suggestions and DB results.

Relevant database lookup behavior is in `LimeIME-iOS/Shared/Database/LimeDB.swift`:

- `getMappingByCode` lowercases the query after preprocessing so lookup remains case-insensitive for table matching.
- That lookup normalization should not require lowercasing the separate user-facing composing-code echo.

Android comparison from `LimeStudio/app/src/main/java/net/toload/main/hd/SearchServer.java`:

- Android builds the self/composing-code candidate with `self.setWord(code)` and `self.setCode(code)`, preserving the original typed casing.
- Android then marks that mapping as `RECORD_COMPOSING_CODE` for mixed English input.

## Existing test and coverage assessment

Current iOS tests cover that `getMappingByCode("abc")` returns a composing-code echo when results exist, but the assertion only compares `result[0].word.lowercased()` with `"abc"`. That allows an all-lowercase echo and does not guard the reported uppercase preservation path.

A focused regression test should call the iOS search path with uppercase or mixed-case English input where DB results exist and assert that candidate 0's `word` and `code` preserve the original typed string.

## Likely root cause

Likely iOS-only composing-code echo bug: the phonetic-table branch of `SearchServer.getMappingByCode(_:)` lowercases the echo candidate before it is inserted at candidate 0. The DB lookup can stay normalized/lowercased, but candidate 0 should remain a user-facing echo of the typed input.

The non-phonetic iOS branch and Android both preserve the original typed `code` for the composing-code candidate, so the phonetic iOS lowercasing looks like the inconsistent path that can produce the reported `ABC` -> `abc` and `iPhone` -> `iphone` behavior.

## Proposed solution / investigation plan

1. Add an iOS regression test for mixed-case composing-code echo preservation in hybrid English input.
2. Change the iOS phonetic-table echo construction in `SearchServer.getMappingByCode(_:)` to preserve the original typed `code` for the user-facing `Mapping.code` and `Mapping.word` fields.
3. Keep lookup/cache keys and DB queries case-normalized so table lookup and candidate ranking behavior do not regress.
4. Verify English suggestion insertion still receives the correct echo code for long mixed English input.
5. Verify candidate 0 selection commits the original typed capitalization.

## Follow-up questions

No reporter clarification is needed for initial triage. The issue is maintainer-created and the source path is narrow enough for implementation.

## Platform impact

### iOS

Confirmed reported platform. The suspected path is iOS `SearchServer.getMappingByCode(_:)` composing-code echo construction for phonetic/hybrid candidate flow.

### Android

Android comparison was inspected. Android already preserves the original typed `code` in its composing-code mapping (`SearchServer.java` uses `self.setWord(code)` and `self.setCode(code)`), so the same lowercased-candidate-0 root cause does not appear to apply to Android. Android parity risk is low unless future shared behavior changes are made.

## Verification plan

- iOS: add and run a focused XCTest that verifies candidate 0 preserves uppercase/mixed-case typed English, for example `ABC` and `iPhone`, when mapping results are available.
- iOS: manually verify candidate display and candidate 0 commit behavior in the keyboard extension for mixed English input.
- iOS: verify lowercase table lookup still returns the expected Chinese candidates and English suggestions.
- Android: no APK retest applies for this iOS-only source path, but keep Android behavior unchanged.

## Retest condition

Do not ask anyone to retest an Android APK for this issue. Because this is maintainer-created and iOS-only, close or update the issue only after the iOS source fix is merged and verified in the relevant iOS/TestFlight/App Store delivery path.
