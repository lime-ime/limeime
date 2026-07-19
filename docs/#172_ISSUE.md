# Issue #172: iOS imported `liu7.cin` table cannot produce candidates

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/172
- Classification: `bug`, `Priority-Medium`, `Type-Defect`
- State: open
- Assignee: `jrywu`
- Source: private support-email report summarized by the project account. The original table and screenshots remain private test evidence.
- Confirmed environment: iPhone 12, iOS 17.6, imported `liu7.cin`; the exact LIME version is not yet known.

## Problem statement

The user reports that importing `liu7.cin` completes, but selecting the imported input method does not produce characters. Inspection of the private UTF-8 fixture now identifies a concrete parser failure before candidate lookup: all 31,556 `%chardef` mapping rows align the code and output columns with repeated ASCII spaces, and the current iOS parser reads the first empty field after the code as the output. A static replay of the current parser therefore accepts zero mappings from those rows while still completing the metadata/import lifecycle.

The reporter's selected destination input method, visible imported-count message, runtime registration/activation state, and one exact user-entered code still need device confirmation. Those checks may reveal a secondary issue, but they are no longer prerequisites for establishing the fixture's repeated-space parser defect.

This is independent of issue #160, which concerns missing iOS keyboard-layout resources.

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
- No existing test was found that imports the private `liu7.cin` fixture or reproduces the reported no-output behavior end to end.
- No inspected CIN test uses repeated spaces between code and output. Existing one-space fixtures therefore do not exercise the empty-field behavior in `splitEscapedFields(...)`.

### Android comparison

- `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java` normalizes repeated spaces before parsing space-delimited CIN rows.
- Android does not share the exact adjacent-space failure path demonstrated by this fixture, although a focused parity test should preserve that existing behavior.

## Likely failure areas

The source-backed root cause for the supplied fixture is inconsistent repeated-space handling between the platform importers. Android collapses repeated spaces before reading CIN fields. iOS treats every space as an independent delimiter and assumes the output is always at index 1, so the private fixture's aligned mapping rows all produce an empty output and are skipped.

Registration/activation and runtime publication remain secondary device-level checks because the report does not yet include the visible import count or exact selected table. Do not attribute this issue to missing `%cname`, issue #160, or a registration-only failure unless post-parser testing finds separate evidence.

## Proposed investigation and fix plan

1. Add a sanitized RED CIN fixture with `%chardef` rows separated by two, five, and seven spaces. Assert the expected mappings are inserted and `amount` is non-zero.
2. Add repeated-space `%keyname` coverage and keep one-space, tab-delimited, metadata, comment, score/base-score, and `.lime` escaped-delimiter tests passing.
3. Add a dedicated CIN whitespace parser that treats an ASCII-whitespace run as one separator for `%keyname` and `%chardef` data rows. Do not globally alter pipe/tab/comma escaped-field behavior.
4. Decide whether a non-empty CIN mapping block that yields zero valid mappings should return an error or warning instead of a successful completion message.
5. Reproduce on a fresh iOS database with the private attachment, then verify the resulting `ImConfig`, `keyboard_state`, `active_im`, cold-to-hot publication, and candidate lookup for one reporter-confirmed code. Fix those boundaries only if they remain broken after mappings import correctly.
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

- RED/GREEN test with a sanitized fixture matching the supplied CIN structure.
- Verify import count and direct database lookup for the reporter-confirmed code.
- Verify registration, enabled state, active-IM selection, cold-to-hot publication, and keyboard-extension candidate lookup.
- Verify a fresh install and an existing user database.
- Device-test on iPhone/iOS 17 and a currently supported iOS version.
- Confirm through the private email thread using a newer TestFlight/App Store build after a targeted fix exists.

### Android

Android has a separate Java importer that already normalizes repeated spaces, so it is not expected to share this exact parser defect. Use the same sanitized fixture to verify import count, metadata/default keyboard assignment, and candidate lookup and preserve that behavior. No Android release change or APK retest is currently warranted for #172 unless the parity check finds a separate failure.

## Retest condition

Do not ask the user to retest the currently installed build. Retest only after a newer iOS build contains the repeated-space parser fix and passes the private-fixture import checks. Route the request through the private support-email thread unless the reporter chooses to participate on GitHub.
