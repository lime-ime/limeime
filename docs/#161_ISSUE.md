# Issue #161: Related-Phrase Management Search Cannot Find Multi-Character Entries

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/161
- Classification: `bug` + `question` + `Usability`
- Reporter platform: Android, based on the attached Settings screenshots
- State: source-confirmed, fix prepared on `fix/161-related-search-management`
- Retest condition: wait for a newer Android APK containing the fix

## Problem

The original question asked how to create an iOS text-replacement / Samsung text-shortcut equivalent. LIME already supports custom code-to-text mappings through each input method's `瀏覽 / 編輯資料表` screen, so the issue was initially answered and closed as a question.

The reporter then described a separate concrete problem in `關聯字管理`: previously created relation records cannot be found reliably when the parent text contains two or more characters. Their screenshots show an existing `台中 → 市` row, while searching `台中` returns unrelated rows such as `台 → 中` and `台 → 中市`; searching a longer phrase can return no rows. With 184,591 records across 1,846 pages, manual paging is not practical for editing or deletion.

Latest evidence: https://github.com/lime-ime/limeime/issues/161#issuecomment-5010422241

## Reproduction

1. Open `輸入法` → `關聯字庫` → `瀏覽 / 編輯關聯字庫`.
2. Ensure a row exists with a multi-character parent, for example `台中 → 市`.
3. Search for `台中` or the combined phrase `台中市`.
4. Observe that the expected row is missing or that the result set follows first-character relation lookup semantics instead of free-text management search.
5. Without a search result, the row cannot be opened and deleted without paging through the full dataset.

## Root cause

Confirmed by source tracing on both platforms.

The management search boxes pass their free-text query to the same database method used for runtime related-candidate lookup:

- Android: `ManageRelatedFragment` → `ManageImController.loadRelatedPhrases()` → `SearchServer.getRelatedByWord()` → `LimeDB.getRelated()`
- iOS: `RelatedListView` → `ManageRelatedController.loadRelated()` → `DBServer.getRelated()` → `LimeDB.getRelated()`

`getRelated()` intentionally interprets a multi-character runtime lookup as:

- first code point: exact `pword`
- remaining text: `cword` prefix

Therefore `台中` becomes `pword = 台 AND cword LIKE 中%`, which cannot find a stored `台中 → 市` row. The runtime behavior is valid for candidate generation but is the wrong query model for a record-management search box.

The iOS management controller also reported the unfiltered total record count while showing filtered rows, so pagination metadata could remain at the full 1,846-page size during a search.

## Fix

Keep runtime `getRelated()` unchanged. Add a management-only search path on Android and iOS that:

- searches `pword`
- searches `cword`
- searches the concatenated `pword + cword` phrase
- uses parameterized, literal `LIKE` matching with `%`, `_`, and backslash escaped
- applies the same filter to the displayed rows and total count
- preserves score ordering and pagination

Both management controllers now call this dedicated path. Runtime candidate lookup continues to use the existing split-first-code-point semantics.

## Platform impact

### Android

Confirmed affected by reporter screenshots and source inspection. The Android Settings management UI currently delegates free-text search to runtime relation lookup. A focused instrumentation regression test covers multi-character parent search, combined-phrase search, unrelated-query exclusion, and filtered count.

### iOS

Source-confirmed affected by the same query-model mismatch. The SwiftUI relation manager also used runtime `getRelated()` and calculated total pages from the unfiltered table count. Matching source and XCTest coverage are included. Full Xcode/XCTest execution is still required on macOS because this Linux environment has no Swift/Xcode toolchain.

## Test-driven verification

### RED

The Android regression test was added before production code. `:app:compileDebugAndroidTestJavaWithJavac` failed because `searchRelatedForManagement()` and `countRelatedForManagement()` did not exist, proving the old API could not express management search behavior.

### GREEN

On the Pixel 9 Pro Android 16 emulator:

```bash
./gradlew :app:compileDebugJavaWithJavac \
  :app:compileDebugAndroidTestJavaWithJavac \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.limeime.LimeDBTest#testRelatedManagementSearchFindsMultiCharacterParentAndCombinedPhrase
```

Result: `BUILD SUCCESSFUL`, 1/1 focused instrumentation test passed. The full Android `LimeDBTest` instrumentation class also passed 215/215 tests on the same emulator, and `:app:testDebugUnitTest` passed. Existing unchecked/deprecation warnings only.

## Acceptance criteria

- [x] Searching a multi-character parent such as `台中` finds `台中 → 市`.
- [x] Searching the combined phrase `台中市` finds `台中 → 市`.
- [x] Unrelated text does not return the record.
- [x] Filtered total count uses the same predicate as filtered rows.
- [x] Runtime related-candidate lookup semantics remain unchanged.
- [x] Android focused instrumentation regression passes.
- [ ] iOS XCTest passes on macOS/Xcode.
- [ ] Reporter confirms the fix in a newer Android build.

## Public follow-up

The issue should be reopened as a bug. A public reply should explain that the search behavior is now confirmed as a management-search defect and that deletion is available by opening the matching row after search. Because public replies require Jeremy's exact-draft approval, do not post that reply until approved.
