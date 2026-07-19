# Issue #172: iOS imported `liu7.cin` table cannot produce candidates

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/172
- Classification: `bug`, `Priority-Medium`, `Type-Defect`
- State: open
- Source: private support-email report summarized by the project account. The original table and screenshots remain private test evidence.
- Confirmed environment: iPhone 12, iOS 17.6, imported `liu7.cin`; the exact LIME version is not yet known.

## Problem statement

The user reports that importing `liu7.cin` completes, but selecting the imported input method does not produce characters. The current report does not yet distinguish among these failure stages:

1. the CIN parser imports zero or incomplete mapping rows;
2. the imported table is not fully registered or activated for the keyboard extension;
3. the keyboard sends roots that do not match the table's imported `imkeys`/key-name metadata;
4. mappings exist but are not published or queried from the runtime database used by the keyboard extension.

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

## Likely failure areas

The exact root cause is not yet proven. The strongest code-level risks are:

1. **Registration/activation gap:** text import writes mapping/config rows and publishes the table but does not explicitly rebuild `keyboard_state`. The later `seedCustomIM()` call can no-op because metadata rows already exist.
2. **CIN compatibility gap:** the supplied file may use a key-name, delimiter, encoding, or metadata shape not represented by current synthetic tests, resulting in imported rows or `imkeys` that do not match the keys sent by the keyboard.
3. **Runtime publication/query gap:** successful Settings-side row insertion may not guarantee that the keyboard extension selects and queries the newly imported table in the same session.

Do not attribute the report solely to missing `%cname` or to issue #160 without a reproduction from the supplied file.

## Proposed investigation and fix plan

1. Reproduce on a fresh iOS database with the private `liu7.cin` attachment while recording:
   - imported mapping-row count;
   - parsed `imkeys`, key names, selection keys, and sample mappings;
   - resulting `im` rows and resolved `ImConfig`;
   - `keyboard_state`, `active_im`, and the table selected by the keyboard extension;
   - candidate query results for one reporter-confirmed input code.
2. Add a sanitized minimal CIN fixture that preserves the failing format without publishing private user data.
3. Add a RED end-to-end regression test covering import through runtime candidate lookup.
4. Fix the narrow proven boundary. Depending on reproduction, this may require:
   - robust registration/activation and an explicit post-import state sync;
   - parser support for the supplied CIN format/encoding;
   - corrected `imkeys`/key-name handling;
   - or corrected publication/runtime table selection.
5. Keep existing imported metadata and user mappings intact, and avoid overwriting user-selected keyboard configuration.

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

Android has a separate Java import/runtime implementation and is not confirmed affected by this iOS report. Use the same sanitized CIN fixture to verify Android import count, metadata/default keyboard assignment, and candidate lookup before claiming platform parity or platform isolation. No Android APK retest is warranted from the current evidence.

## Retest condition

Do not ask the user to retest the currently installed build. Retest only after the private file reproduces a specific failure and a newer iOS build contains the corresponding fix. Route the request through the private support-email thread unless the reporter chooses to participate on GitHub.
