# Issue #119: Text import keyboard layout mapping gaps

## Current status

- GitHub issue: https://github.com/lime-ime/limeime/issues/119
- Reporter/source: `limeimetw` maintainer-created tracking issue
- Classification: `bug`, `Type-Defect`, `Usability`
- Current state: open and assigned to `jrywu`
- Source fix status: PR #120 was closed unmerged after branch `fix/119-import-default-keyboards` was merged directly into `master` as commit `66c2b88aede9c1d988a3f76d94af3586c0d8eec3` (`Merge branch 'fix/119-import-default-keyboards'`). The branch head commit `cf1ab6db3c1aaf86e258a560f104eaf8a91e4364` is now an ancestor of `master`.
- Build/release status: no newer Android APK has been observed after `LIMEHD2026-6.1.21.apk` (GitHub Contents blob SHA `a8838c47b4186956536cd4c8aa4e3931d579d1da`, size 14055188 bytes), so the merged source fix is not yet known to be in a reporter-testable Android APK. iOS delivery still requires normal TestFlight/App Store build verification.
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

## Source fix implemented on `master`

The merged source fix defines explicit imported-table default keyboard mappings on both Android and iOS:

- `phonetic` → platform/user phonetic preference on Android; `phonetic` in the iOS helper.
- `dayi` → `dayisym`.
- `cj`, `cj4`, `cj5`, `ecj`, and `scj` → `cjnum`.
- `array` → `arraynum`.
- `array10` → `phonenum`.
- `wb` → `wb`.
- `hs` → `hs`.
- `ez` → `ez`.
- `pinyin` → `limenum`.
- unknown/custom fallback → Android preference-based `limenum`/`lime`; iOS `lime` fallback.

Android now uses `getDefaultKeyboardCodeForImportedIM(table)` before `setIMConfigKeyboard(...)`, with regression coverage for the mapping helper.

iOS now has `defaultKeyboardCodeForImportedIM(_:)` and `applyDefaultKeyboardForImportedIM(_:)`, writing a keyboard config row after text import instead of relying only on runtime `lime_<tableNick>` fallback. The merge also preserved the newer `scj.limedb` catalog artifact while applying the intended `cjnum` keyboard mapping.

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

No community retest request is needed because #119 is maintainer-created from code-path inspection. The source fix is on `master`, but no newer Android APK has been observed after the merge and iOS delivery still needs normal TestFlight/App Store build verification. Close the maintainer-created issue directly after maintainer/local verification or after a build containing the fix is available and verified; do not ask a community reporter to retest.
