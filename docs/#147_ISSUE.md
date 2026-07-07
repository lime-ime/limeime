# Issue #147: iOS Chinese input candidate 0 should preserve capital letters

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/147
- Classification: `bug` + `Usability`
- Source: maintainer-created iOS tracking issue by `limeimetw`
- Current state: open and assigned to `jrywu`
- Public acknowledgement: not needed because this is a maintainer-created internal tracking issue with no community reporter to acknowledge

## Problem statement

In iOS Chinese input / mixed input mode, candidate 0 is the raw composing-code echo. It should preserve the exact casing typed by the user. If the user types `ABC` or `iPhone`, candidate 0 should display and commit `ABC` or `iPhone`, not `abc` or `iphone`.

Lowercasing for lookup, cache keys, and database matching is fine. The bug is only that the user-visible candidate 0 echo is lowercased.

## Source evidence inspected

### iOS Chinese candidate path

- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`
  - `updateCandidates()` sends the current composing buffer `mComposing` to `SearchServer.getMappingByCode(...)`.
  - `setSuggestions(_:)` shows the returned list as normal Chinese candidates.
  - Candidate 0 is expected to remain a `Mapping.RecordType.composingCode` record so tapping it commits the raw mixed-mode letters through the existing Chinese candidate path.

- `LimeIME-iOS/Shared/Search/SearchServer.swift`
  - `getMappingByCode(_:)` prepends a composing-code echo candidate as candidate 0.
  - In the phonetic-table branch, the echo is currently built with `code.lowercased()` for both `code` and `word`.
  - Both phonetic and non-phonetic branches cache the assembled final candidate list under a lowercased key. Because the cached list includes candidate 0, a cache hit can replay an echo from an earlier casing instead of the current typed casing.

### Android comparison

- `LimeStudio/app/src/main/java/net/toload/main/hd/SearchServer.java`
  - Android creates the composing-code echo fresh in `getMappingByCode(...)`:
    - `self.setWord(code)`
    - `self.setCode(code)`
    - `self.setComposingCodeRecord()`
  - This uses the original typed `code`, so candidate 0 preserves casing.
- `LimeStudio/app/src/main/java/net/toload/main/hd/limedb/LimeDB.java`
  - Android lowercases inside database lookup with `code = code.toLowerCase(Locale.US)`.
  - That means Android lowercases for query matching but keeps candidate 0 display based on the original input.
- Android caches database result lists, then adds the `self` echo outside the cached DB results. iOS currently caches the assembled list that already contains candidate 0, so iOS needs to rebuild or replace candidate 0 on cache returns.

### Existing iOS tests

- `LimeIME-iOS/LimeTests/SearchServerTest.swift`
  - `test_3_1_5_3_getMappingByCode_self_mapping_creation()` checks `result[0].word.lowercased()`, which cannot catch this bug.
  - Add exact-case assertions for candidate 0.
  - Add a cache-hit case so `abc` followed by `ABC`, or the reverse, returns candidate 0 for the current typed casing.

## Likely root cause / investigation hypothesis

iOS is lowercasing the composing-code echo in the phonetic `SearchServer.getMappingByCode(_:)` branch and may return stale echo casing from the assembled-list cache. Android avoids this by lowercasing only in the DB query layer and creating candidate 0 fresh from the original typed code after fetching cached/DB results.

## Proposed fix

1. Keep lookup and cache normalization lowercase.
2. Build the composing-code echo from the original typed `code`:
   - `Mapping(id: 0, code: code, word: code, recordType: Mapping.RecordType.composingCode)`
3. Ensure cache hits return a fresh candidate 0 echo for the current typed `code` by replacing cached index 0 before returning.
4. Do not change candidate 0 to `englishSuggestion`. In Chinese input mode it must stay `composingCode`.
5. Leave English keyboard prediction out of this issue unless a separate repro shows a separate English-prediction bug.

## Verification plan

- Add or update `SearchServerTest`:
  - `getMappingByCode("ABC")` returns candidate 0 `word == "ABC"` when candidates exist.
  - `getMappingByCode("iPhone")` returns candidate 0 `word == "iPhone"` when candidates exist.
  - Cache case: call `getMappingByCode("abc")`, then `getMappingByCode("ABC")`; the second result candidate 0 is `ABC`.
  - Reverse cache case if cheap: call `ABC`, then `abc`; the second result candidate 0 is `abc`.
- Manual iOS keyboard verification after a TestFlight or local simulator build:
  - In Chinese input / mixed input mode, type `ABC` and confirm candidate 0 shows `ABC`, not `abc`.
  - Type `iPhone` and confirm candidate 0 shows `iPhone`, not `iphone`.
  - Tap candidate 0 and confirm the raw typed text is committed exactly as displayed.
  - Confirm normal Chinese candidates still appear after candidate 0 and lookup behavior is unchanged.

## Platform impact

- iOS: affected. The fix is in `SearchServer.getMappingByCode(_:)` and its cache return behavior.
- Android: reference behavior already lowercases only for lookup and creates candidate 0 from the original typed code. No Android change or APK retest is needed.

## Follow-up / retest condition

Keep the issue open until an iOS source fix lands and is verified in an iOS build/TestFlight. Do not post an Android APK retest request. No public acknowledgement is needed unless a maintainer wants to add a progress note after the fix is available.
