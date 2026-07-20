# Issue #172: iOS imported `liu7.cin` table cannot produce candidates

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/172
- Classification: `bug`, `Priority-Medium`, `Type-Defect`
- State: closed/source fixed by maintainer after PR #174 merged; Android and iOS v6.1.34 delivery are verified, while private-reporter verification remains pending
- Assignee: `jrywu`
- Fix: PR #174, merged as `f5110419456235acdc075825757b7ceaf6ada133` on 2026-07-19
- Source: private support-email report summarized by the project account. The original table and screenshots remain private test evidence.
- Confirmed environment: iPhone 12, iOS 17.6, imported `liu7.cin`; the exact LIME version is not yet known.
- Latest reporter evidence: after updating LIME on 2026-07-19, the problem still reproduced and the reporter supplied four additional screenshots. The updated LIME version remains unconfirmed, and that test preceded iOS v6.1.34, which contains the parser fix.

## Problem statement

The user reports that importing `liu7.cin` completes, but selecting the imported input method does not produce characters. Inspection of the private UTF-8 fixture now identifies a concrete parser failure before candidate lookup: its `%chardef` mapping rows align the code and output columns with repeated ASCII spaces, and the pre-fix iOS parser read the first empty field after the code as the output. A static replay of that parser therefore accepted zero mappings from the aligned rows while still completing the metadata/import lifecycle.

The reporter's selected destination input method, visible imported-count message, runtime registration/activation state, and one exact user-entered code still need device confirmation. Those checks may reveal a secondary issue, but they are no longer prerequisites for establishing the fixture's repeated-space parser defect.

## Source evidence inspected

### iOS import and registration path

- `LimeIME-iOS/LimeSettings/Views/IMInstallView.swift`
  - Text files are routed through `SetupImController.importTxtFile(...)`.
  - For the `custom` destination, the view calls `seedCustomIM()` only after import succeeds.
- `LimeIME-iOS/LimeSettings/Controllers/SetupImController.swift`
  - Both text-import entry points call `LimeDB.importTxtFile(...)`, write an install lifecycle record, and call `markTableChangedAndPublish(...)`.
  - These entry points do not themselves call `registerIM(...)` or rebuild `keyboard_state`.
- `LimeIME-iOS/Shared/Database/LimeDB.swift`
  - `importTxtFile(...)` parses `%chardef`, `%keyname`, `%version`, `%cname`, selection/end-key metadata, inserts mapping rows, writes `im` metadata, and assigns a default keyboard.
  - The first space-separated CIN data row selects a single space as `detectedDelimiter`. `splitEscapedFields(...)` emits an empty field for every adjacent delimiter, while mapping insertion assumes `parts[1]` is the output. For the private fixture's aligned rows, `parts[1]` is empty and every mapping row is skipped.
  - Import completion still writes `source`, `version`, `name`, `amount`, and keyboard metadata. A non-empty CIN file can therefore complete with `amount = 0` without throwing an error.
  - `setImConfig(...)` creates key-value rows in `im`.
  - `applyDefaultKeyboardForImportedIM(...)` creates a `title="keyboard"` row with the keyboard code in the `keyboard` column.
  - `seedCustomIM()` returns when *any* `im` row already exists for `custom`. Because text import has already written metadata and a keyboard row, the post-import seed call normally does not create its synthetic registration row.
  - `getAllImConfigs()` can still synthesize an `ImConfig` from key-value rows, but its seed-row fallback and keyboard resolution should be tested against the exact imported file and a fresh user database.
- `LimeIME-iOS/Shared/Preferences/LIMEPreferenceManager.swift`
  - `syncIMActivatedState(dbServer:)` rebuilds the keyboard extension's enabled IM state, but the text-import success path does not explicitly call it.

### Existing tests and remaining gap

- `LimeIME-iOS/LimeTests/LimeDBTest.swift` covers basic CIN parsing, `%version`/`%cname`/`%selkey` metadata, comment skipping, mapping lookup, default keyboard selection, and some `getAllImConfigs()` behavior.
- The inspected tests use small synthetic tables and do not cover the full Settings-to-keyboard-extension flow for a realistic imported CIN file: import, registration/activation, cold-to-hot publication, active-IM selection, key acceptance, and candidate lookup.
- Before this fix, no CIN test used repeated spaces between code and output. Existing one-space fixtures therefore did not exercise the empty-field behavior in `splitEscapedFields(...)`.
- The merged sanitized tests cover mapping rows with arbitrary runs of ASCII spaces and tabs at the parser boundary, but no automated test imports the private fixture or drives the full Settings-to-keyboard-extension path end to end.

### Android comparison

- `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java` had the same adjacent-space behavior in its CIN-specific space branch: `splitEscapedFields(...)` preserved empty fields and the importer read `parts[1]` as the output.
- A focused Android instrumentation test using sanitized aligned rows failed RED with an empty mapping list, confirming that Android shared the parser defect even though the support report covers iOS only.

## Likely failure areas

The source-backed parser defect identified for the supplied fixture is repeated-space handling in both platform importers. Each treated every space as an independent delimiter and assumed the output was always at index 1, so aligned mapping rows produced an empty output and were skipped.

Registration/activation and runtime publication remain secondary device-level checks because the report does not yet include the visible import count or exact selected table. Do not attribute this issue to missing `%cname` or a registration-only failure unless post-parser testing finds separate evidence.

## Merged implementation and remaining release QA

Completed on `master` through PR #174:

1. Added sanitized Android and iOS mapping fixtures covering spaces, tabs, and mixed horizontal-whitespace runs between CIN fields.
2. Changed both CIN importers to treat any non-empty `[ \t]+` run as one separator without changing `.lime` delimiter handling.
3. Scoped iOS normalization to CIN imports so the shared unescaped `.lime` delimiter path remains unchanged.
4. Android verification reported the focused instrumentation test and all 216 `LimeDBTest` tests passing, plus unit tests, instrumentation-test compilation, lint, and `git diff --check`. Swift/Xcode was unavailable, so the new iOS test was not run before merge.
5. Maintainer `jrywu` merged PR #174 as `f5110419456235acdc075825757b7ceaf6ada133` and closed the public maintainer-created tracking issue. There was no public issue comment.
6. GitHub Release v6.1.34 targets `d45aa437b6356bfef5079ceebbfcd8d295a300b8` and contains the Android parity fix. Its verified GitHub testing-track asset is `LIMEHD2026-6.1.34.apk` (7,112,576 bytes, SHA-256 `d16d7fde5d634d655148396c657e8ffab5f3868f434f705f9568855da4e3e84f`). No Android reporter retest is required for this iOS-origin report.
7. App Store Connect reports iOS v6.1.34 build 19 as `READY_FOR_SALE`, `VALID`, and `APP_STORE_ELIGIBLE`. The public Taiwan, US, and Hong Kong App Store pages show v6.1.34 in version history with the repeated-space/Tab CIN import fix. Apple's lookup endpoint was still returning v6.1.33 during the initial propagation check, so the direct storefront pages are the current delivery evidence.

Remaining release QA and follow-up:

1. Ask the private reporter to update to iOS v6.1.34, re-import `liu7.cin`, and report whether candidate output now works.
2. If the fixed build still fails, obtain one exact input code and expected character, then verify the resulting `ImConfig`, `keyboard_state`, `active_im`, cold-to-hot publication, and candidate lookup. Reopen this issue or create a focused follow-up only for a reproducible remaining failure.
3. Run the new iOS whitespace test and broader iOS `LimeDBTest` suite in Xcode/Xcode Cloud when the next validation lane is available.
4. Restore focused `%keyname` coverage and an iOS legacy unescaped `.lime` empty-field compatibility regression before relying on those safeguards as automated release gates. They were described in the PR but are not present in the merged tree.
5. Decide separately whether a non-empty CIN mapping block that yields zero valid mappings should return an error or warning instead of a successful completion message.
6. Keep existing imported metadata and user mappings intact, and avoid overwriting user-selected keyboard configuration.

## Follow-up questions

Request or confirm through the private support-email thread:

- exact LIME version used;
- one input-code sequence and expected character from `liu7.cin`;
- whether the imported input method appears in the installed/enabled list and is visibly selected;
- whether the key taps appear in the composing/candidate area;
- whether reselecting LIME or reopening the host app changes the result.

## Verification plan

### iOS

- Run the merged sanitized parser fixture in Xcode/Xcode Cloud. It was added but not executed before merge.
- Verify import count and direct database lookup for the reporter-confirmed code using the private fixture.
- Verify registration, enabled state, active-IM selection, cold-to-hot publication, and keyboard-extension candidate lookup.
- Verify a fresh install and an existing user database.
- Device-test on iPhone/iOS 17 and a currently supported iOS version.
- Confirm through the private email thread using App Store iOS v6.1.34, which contains merge commit `f5110419456235acdc075825757b7ceaf6ada133`.

### Android

Android has a separate Java importer but shared this parser defect. Its focused instrumentation fixture covers spaces, tabs, and mixed runs, and the PR reports that the focused test and all 216 `LimeDBTest` tests passed. No Android reporter retest is currently required because the support report is iOS-only, but the next Android build should include the parity fix.

## Retest condition

Ask the user to retest with App Store iOS v6.1.34, which contains merge commit `f5110419456235acdc075825757b7ceaf6ada133`. Route the request through the private support-email thread unless the reporter chooses to participate on GitHub. Reopen this issue or create a focused follow-up only if the fixed build still fails.
