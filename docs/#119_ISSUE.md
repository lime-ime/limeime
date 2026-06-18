# Issue #119: Text import keyboard layout mapping gaps

## Current status

- GitHub issue: https://github.com/lime-ime/limeime/issues/119
- Reporter/source: `limeimetw` maintainer-created tracking issue
- Classification: `bug`, `Type-Defect`, `Usability`
- Current state: open and assigned to `jrywu`
- Public acknowledgement: none needed because this is maintainer-created internal tracking

## Problem statement

Text import of `.lime` / `.cin` tables can leave imported IMs with missing or unintended keyboard layouts.

The issue affects Android and iOS differently:

- Android text import already writes a `keyboard` config row after import, but `scj` and `pinyin` currently rely on generic fallback instead of an explicit intended mapping.
- iOS text import stores table data and metadata but does not assign keyboard rows for known imported IMs, so the keyboard can fall back to `lime_<tableNick>` layouts that are missing or not the intended parity layout.

## Source evidence inspected

### Android text import path

- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/controller/SetupImController.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/DBServer.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/limedb/LimeDB.java#importTxtTable`

In `LimeDB.importTxtTable()`, after metadata rows are written, Android chooses a keyboard config and calls `setIMConfigKeyboard(table, kConfig.getDescription(), kConfig.getCode())`.

Relevant inspected block: `LimeDB.java` around lines 4248-4304.

Observed mappings in the current Android text-import block:

- `phonetic` chooses a keyboard based on `phonetic_keyboard_type` and `number_row_in_english`.
- `dayi` maps to `dayisym`.
- `cj4`, `cj5`, and `ecj` map to `cj`.
- `array` maps to `arraynum`.
- `array10` maps to `phonenum`.
- `wb` maps to `wb`.
- `hs` maps to `hs`.
- When no direct keyboard row exists, fallback is `limenum` if `number_row_in_english` is true, otherwise `lime`.

There is no explicit `scj` or `pinyin` mapping in that block. They currently reach the generic fallback path when no keyboard row is found.

### iOS text import path

- `LimeIME-iOS/LimeSettings/Views/IMInstallView.swift#handleFileImport`
- `LimeIME-iOS/LimeSettings/Controllers/SetupImController.swift#importTxtFile`
- `LimeIME-iOS/Shared/Database/DBServer.swift#importTxtFile`
- `LimeIME-iOS/Shared/Database/LimeDB.swift#importTxtFile`
- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#resolvedLayoutId`

`IMInstallView.handleFileImport()` calls `setupController.importTxtFile(url:tableName:restoreLearning:)` for text imports. `SetupImController.importTxtFile()` delegates to `DBServer.importTxtFile`, which delegates to `LimeDB.importTxtFile()`.

In `LimeDB.importTxtFile()` around lines 3355-3366, iOS stores source/version/name/amount/import and optional metadata such as `selkey`, `endkey`, `limeendkey`, `spacestyle`, `imkeys`, and `imkeynames`. It does not call `setIMConfigKeyboard(...)` or `registerIM(...)` for known IMs after text import.

`getAllImConfigs()` reads the keyboard from the `im` row whose `title == "keyboard"`, falling back to the seed row's keyboard column. If text import never writes either a seed row or a keyboard row for the imported IM, the keyboard ID is empty or unavailable.

At runtime, `KeyboardViewController.resolvedLayoutId(for:)` returns `lime_<tableNick>` when no activated IM config is available or when the keyboard ID cannot resolve to a loadable layout or keyboard-table row. That fallback is incorrect for some imported IMs.

## Likely root cause

The Android and iOS text-import flows do not share a complete, explicit IM-to-keyboard mapping after text import.

Android has a mostly complete post-import keyboard assignment block, but `scj` and `pinyin` are only covered indirectly by the generic fallback. That makes the intended behavior fragile and unclear.

iOS does not run an equivalent post-import keyboard assignment step for known IMs. It imports the table and metadata but leaves layout selection to runtime fallback, which can produce missing layouts such as `lime_cj4`, `lime_cj5`, `lime_ecj`, `lime_scj`, or `lime_pinyin`, or unintended layouts such as `lime_dayi` / `lime_array` where Android uses `lime_dayi_sym` / `lime_array_number`.

## Platform impact analysis

### Android

Confirmed by code inspection. Android text import writes a keyboard row, but `scj` and `pinyin` should be audited and made explicit if the intended mapping is `limenum` / `lime_number` or another pinyin-specific mapping. The current fallback usually avoids a missing-layout crash when `number_row_in_english` is enabled, but it is not self-documenting and can diverge from cloud/database import expectations.

### iOS

Confirmed by code inspection. iOS text import can leave known imported IMs without a keyboard config row, and runtime fallback can resolve to layouts that are absent or inconsistent with Android/cloud import behavior. This is the higher-risk platform because text import currently lacks the Android-style post-import assignment step.

## Existing test coverage assessment

### Android

There are Android instrumentation tests for IM management and database/import behavior, but this triage did not find a focused test that imports `.lime` / `.cin` for each known IM and asserts the resulting `im.title = "keyboard"` row and loaded layout mapping.

### iOS

Existing iOS tests cover `.lime` / `.cin` parsing, metadata import, database import, and IM config reading in several areas, including `LimeDBTest.swift`, `SetupImControllerTest.swift`, and `DBServerTest.swift`. Current focused coverage appears to emphasize `custom` text imports and cloud `.zip` import behavior. The reported known-IM text-import keyboard assignment matrix is not directly gated.

## Code fragility assessment

The iOS path is fragile because `importTxtFile()` mutates metadata rows but does not centralize keyboard assignment, while runtime layout resolution contains fallback and compatibility redirects in `KeyboardViewController`. This spreads the behavior across Settings import, database metadata, IM list construction, and keyboard runtime.

The Android path is less fragile because one post-import block already assigns keyboards, but missing explicit cases can still cause behavior drift or make future mapping changes hard to audit.

## Proposed fix / investigation plan

1. Define a shared intended text-import mapping table for known IMs, matching Android/cloud import expectations where practical:
   - `phonetic` → current phonetic keyboard preference.
   - `dayi` → `dayisym`.
   - `cj`, `cj4`, `cj5`, `ecj` → `cj`.
   - `scj` → intended fast-Cangjie/number fallback, likely `limenum` / `lime_number` unless maintainer confirms `cj` parity.
   - `array` → `arraynum`.
   - `array10` → `phonenum` / `phone_simple`.
   - `wb` → `wb`.
   - `hs` → `hs`.
   - `ez` → `ez` when available.
   - `pinyin` → intended pinyin/default-number mapping, likely `limenum` / `lime_number` unless a dedicated pinyin layout is introduced.
2. Add explicit Android cases for `scj` and `pinyin` if the maintainer confirms the intended fallback should be durable behavior.
3. Add an iOS post-text-import assignment step that writes `im.title = "keyboard"` via `setIMConfigKeyboard(...)` or an equivalent helper after successful import of known IMs.
4. Ensure iOS custom import remains unchanged: `custom` should keep the existing `seedCustomIM()` / `lime_abc` behavior.
5. Sync the keyboard extension / activated IM state after iOS import so the newly written keyboard row is visible without requiring an app restart.

## Suggested regression coverage

### Android

Add or extend import tests to cover known IM text import and assert the assigned keyboard code after import, especially:

- `scj`
- `pinyin`
- `dayi`
- `array`
- `array10`
- `cj4` / `cj5` / `ecj`

### iOS

Add unit tests around `LimeDB.importTxtFile()` / `SetupImController.importTxtFile()` or a lower-level helper to assert that known text imports write the expected keyboard row and that `getAllImConfigs()` returns the intended `keyboardId`.

Priority cases:

- `cj4`, `cj5`, `ecj` resolve to `cj` / `lime_cj`.
- `dayi` resolves to `dayisym` / `lime_dayi_sym`.
- `array` resolves to `arraynum` / `lime_array_number`.
- `array10` stays `phonenum` / `phone_simple`.
- `scj` and `pinyin` resolve to the maintainer-confirmed intended keyboard instead of missing per-table fallback layouts.
- `custom` remains `lime_abc` through the existing custom seeding path.

## Verification plan

1. Import small `.lime` and `.cin` fixtures for affected known IMs on Android.
2. Inspect the Android `im` table and confirm the `keyboard` row stores the intended keyboard code.
3. Switch to each imported Android IM and confirm the visible layout matches the expected IM layout.
4. Import the same text fixtures on iOS.
5. Inspect the iOS `im` table and confirm `getAllImConfigs()` returns the intended `keyboardId`.
6. Switch to each imported iOS IM in the keyboard extension and confirm `resolvedLayoutId(for:)` loads a real, intended layout.
7. Confirm `custom` text import behavior is unchanged on both platforms.

## Follow-up / retest condition

No community retest request is needed because #119 is maintainer-created from code-path inspection. After a source fix lands, verify via Android instrumentation/unit checks and iOS unit/simulator checks. If an Android APK or iOS TestFlight build later includes the fix, close the maintainer-created issue directly after maintainer/local verification rather than asking a reporter to retest.
