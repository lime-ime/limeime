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

Before the source fix, the issue affected Android and iOS differently:

- Android text import already wrote a `keyboard` config row after import, but `scj` and `pinyin` relied on generic fallback instead of an explicit intended mapping.
- iOS text import stored table data and metadata but did not assign keyboard rows for known imported IMs, so the keyboard could fall back to `lime_<tableNick>` layouts that were missing or not the intended parity layout.

## Source evidence inspected

### Android text import path

- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/controller/SetupImController.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/DBServer.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/limedb/LimeDB.java#importTxtTable`

In `LimeDB.importTxtTable()`, after metadata rows are written, Android chooses a keyboard config and calls `setIMConfigKeyboard(table, kConfig.getDescription(), kConfig.getCode())`.

Relevant inspected block: `LimeDB.java` around lines 4248-4304.

Observed mappings in the pre-fix Android text-import block:

- `phonetic` chooses a keyboard based on `phonetic_keyboard_type` and `number_row_in_english`.
- `dayi` maps to `dayisym`.
- `cj4`, `cj5`, and `ecj` map to `cj`.
- `array` maps to `arraynum`.
- `array10` maps to `phonenum`.
- `wb` maps to `wb`.
- `hs` maps to `hs`.
- When no direct keyboard row exists, fallback is `limenum` if `number_row_in_english` is true, otherwise `lime`.

Before the source fix, there was no explicit `scj` or `pinyin` mapping in that block. They reached the generic fallback path when no keyboard row was found.

### iOS text import path

- `LimeIME-iOS/LimeSettings/Views/IMInstallView.swift#handleFileImport`
- `LimeIME-iOS/LimeSettings/Controllers/SetupImController.swift#importTxtFile`
- `LimeIME-iOS/Shared/Database/DBServer.swift#importTxtFile`
- `LimeIME-iOS/Shared/Database/LimeDB.swift#importTxtFile`
- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#resolvedLayoutId`

`IMInstallView.handleFileImport()` calls `setupController.importTxtFile(url:tableName:restoreLearning:)` for text imports. `SetupImController.importTxtFile()` delegates to `DBServer.importTxtFile`, which delegates to `LimeDB.importTxtFile()`.

Before the source fix, `LimeDB.importTxtFile()` around lines 3355-3366 stored source/version/name/amount/import and optional metadata such as `selkey`, `endkey`, `limeendkey`, `spacestyle`, `imkeys`, and `imkeynames`, but did not call `setIMConfigKeyboard(...)` or `registerIM(...)` for known IMs after text import.

`getAllImConfigs()` reads the keyboard from the `im` row whose `title == "keyboard"`, falling back to the seed row's keyboard column. If text import never writes either a seed row or a keyboard row for the imported IM, the keyboard ID is empty or unavailable.

At runtime, `KeyboardViewController.resolvedLayoutId(for:)` returns `lime_<tableNick>` when no activated IM config is available or when the keyboard ID cannot resolve to a loadable layout or keyboard-table row. That fallback is incorrect for some imported IMs.

## Root cause before source fix

The Android and iOS text-import flows did not share a complete, explicit IM-to-keyboard mapping after text import.

Android had a mostly complete post-import keyboard assignment block, but `scj` and `pinyin` were only covered indirectly by the generic fallback. That made the intended behavior fragile and unclear.

iOS did not run an equivalent post-import keyboard assignment step for known IMs. It imported the table and metadata but left layout selection to runtime fallback, which could produce missing layouts such as `lime_cj4`, `lime_cj5`, `lime_ecj`, `lime_scj`, or `lime_pinyin`, or unintended layouts such as `lime_dayi` / `lime_array` where Android used `lime_dayi_sym` / `lime_array_number`.

## Platform impact analysis

### Android

Pre-fix behavior was confirmed by code inspection. Android text import wrote a keyboard row, but `scj` and `pinyin` needed explicit mappings. The source fix now maps `scj` to `cjnum` and `pinyin` to `limenum`; remaining Android risk is build/device verification after the next APK that contains the fix.

### iOS

Pre-fix behavior was confirmed by code inspection. iOS text import could leave known imported IMs without a keyboard config row, and runtime fallback could resolve to layouts that were absent or inconsistent with Android/cloud import behavior. The source fix now writes the intended keyboard row after text import; remaining iOS risk is simulator/device verification after the next TestFlight/App Store build that contains the fix.

## Existing test coverage assessment

### Android

There are Android instrumentation tests for IM management and database/import behavior, but this triage did not find a focused test that imports `.lime` / `.cin` for each known IM and asserts the resulting `im.title = "keyboard"` row and loaded layout mapping.

### iOS

Existing iOS tests covered `.lime` / `.cin` parsing, metadata import, database import, and IM config reading in several areas, including `LimeDBTest.swift`, `SetupImControllerTest.swift`, and `DBServerTest.swift`. The source fix adds focused known-IM text-import keyboard assignment coverage.

## Code fragility assessment

Before the source fix, the iOS path was fragile because `importTxtFile()` mutated metadata rows but did not centralize keyboard assignment, while runtime layout resolution contained fallback and compatibility redirects in `KeyboardViewController`. This spread the behavior across Settings import, database metadata, IM list construction, and keyboard runtime.

Before the source fix, the Android path was less fragile because one post-import block already assigned keyboards, but missing explicit cases could still cause behavior drift or make future mapping changes hard to audit.

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

The merged source fix adds or extends import tests that cover known IM text-import default keyboard assignment, including:

- `scj`
- `pinyin`
- `dayi`
- `array`
- `array10`
- `cj`, `cj4`, `cj5`, and `ecj`

Remaining Android verification is device/emulator confirmation that switching to these imported IMs shows the intended visible keyboard layouts after a build containing merge commit `66c2b88aede9c1d988a3f76d94af3586c0d8eec3`.

### iOS

The merged source fix adds focused coverage for the mapping helper and real `importTxtFile(...)` keyboard-row assignment for representative imported tables.

Priority manual/simulator cases remain:

- `cj4`, `cj5`, `ecj`, and `scj` resolve to `cjnum` / Cangjie number-row layouts.
- `dayi` resolves to `dayisym` / `lime_dayi_sym`.
- `array` resolves to `arraynum` / `lime_array_number`.
- `array10` stays `phonenum` / `phone_simple`.
- `pinyin` resolves to `limenum` / `lime_number`.
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
