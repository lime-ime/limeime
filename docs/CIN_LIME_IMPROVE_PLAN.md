# CIN / LIME Import Export Improvement Plan

> **For agentic workers:** Execute this plan in Claude **goal mode** — pick the next unchecked (`- [ ]`) task, drive it to done, then check it off before moving on. Dispatch the implementation work to **Sonnet** subagents (`Agent` tool with `model: sonnet`), one focused task per agent; reserve the main session for sequencing, review, and marking steps complete. Steps use checkbox syntax for tracking.

**Goal:** Preserve Android's long-standing `.cin` / `.lime` behavior as the compatibility baseline, align iOS importer/exporter behavior to that baseline, and then extend both platforms consistently for `.lime` `@cname@` metadata and escaped literal `|` / `@` values.

**Issue response:** This plan responds to #58 and #59.

**Reference specification:** [CIN_LIME_SPEC.md](CIN_LIME_SPEC.md)

## 1. Compatibility Baseline

- [x] Treat Android `LimeDB.importTxtTable(...)` as the reference behavior for `.cin` and `.lime` imports.
- [x] Treat Android `LimeDB.exportTxtTable(...)` as the reference behavior for `.lime` regular-table and related-table exports.
- [ ] Before changing iOS import/export behavior, write parity notes comparing iOS `LimeDB.swift` behavior with Android for:
  - `.cin` metadata: `%version`, `%cname`, `%selkey`, `%endkey`, `%spacestyle`
  - `.cin` `%keyname` block handling
  - `.cin` `%chardef begin/end` handling
  - `.lime` metadata: `@version@`, `@cname@`, `@selkey@`, `@endkey@`, `@spacestyle@`
  - mapping fields: `code`, `word`, `score`, `basescore`
  - related-table text format
  - delimiter detection and parsing edge cases
- [x] Make Android behavior the default answer when iOS currently differs, unless there is an explicit platform constraint.
- [ ] If iOS intentionally differs from Android, document the reason in [CIN_LIME_SPEC.md](CIN_LIME_SPEC.md) next to the relevant format section and add tests that lock in the intentional difference.

## 2. Format Version and Escaping Rules

- [x] Define a versioned escaped format marker for `.lime`, recommended:

```text
@format@|lime-text-v2
```

- [x] Keep current unescaped parsing for files without `@format@|lime-text-v2`.
- [x] Define v2 escape sequences:
  - `\\` = literal backslash
  - `\|` = literal pipe
  - `\@` = literal at-sign
  - `\%` = literal percent
  - `\t` = tab
  - `\n` = newline, only if multiline field support is intentionally implemented
- [x] Apply escape decoding after delimiter detection and field splitting.
- [x] Apply escape encoding during export when a field contains the active delimiter, backslash, starts with `@` in the code field, or starts with reserved CIN metadata prefixes.

## 3. Android Importer

Source file:

- `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java`

Methods / blocks:

- `importTxtTable(...)`
- metadata block around `codeLower.startsWith("@")`
- `.cin` metadata block for `%version`, `%cname`, `%selkey`, `%endkey`, `%spacestyle`
- `identifyDelimiter(List<String> src)`

Tasks:

- [x] Add local import state for `.lime` display name, for example `String cname = "";` or reuse `imname` explicitly for `@cname@`.
- [x] In the `codeLower.startsWith("@")` block, add exact handling for `@cname@`:

```java
} else if (codeLower.equals("@cname@")) {
    imname = word.trim();
}
```

- [x] Change `.lime` metadata matching from `contains("@version@")` style to exact-key matching after parsing the first field. Keep compatibility only where needed.
- [x] Add `@format@` parsing before record insertion. When value is `lime-text-v2`, enable escaped field parsing for the rest of the import.
- [x] Introduce a small tokenizer helper near `identifyDelimiter`, for example:

```java
private List<String> splitEscapedFields(String line, String delimiter, boolean escapedFormat)
```

It should preserve empty fields and treat `\|` as data when delimiter is `|`.
- [x] Replace repeated regular mapping calls like `line.split("\\|")[0]`, `line.split("\\|")[1]`, `line.split(delimiter_symbol)[0]`, and score parsing with the tokenizer result.
- [x] Replace related-table `line.split("\\|")` with the same tokenizer when v2 is active.
- [x] For `.cin`, decide whether escape support is v2-only under `@format@` or via a CIN-style marker. If no marker is chosen, leave `.cin` v1 behavior unchanged and document that escaping is `.lime` v2 only.
- [x] When finalizing IM config, make `@cname@` set `setImConfig(table, "name", imname)` just like `%cname`.
- [ ] Add Android tests covering:
  - `.lime` `@cname@|Display Name` imports into `im.title = "name"`.
  - old `.lime` without `@format@` still imports unchanged.
  - v2 `aa|word\|with\|pipes|0|0`.
  - v2 `\@code|word|0|0` imports code `@code` as a mapping, not metadata.
  - related v2 row with escaped pipe in `pword` or `cword`, if related escaping is enabled.

## 4. Android Exporter

Source file:

- `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java`

Method / blocks:

- `exportTxtTable(...)`
- regular-table metadata export around `@version@`, `@selkey@`, `@endkey@`, `@spacestyle@`
- regular record export `w.getCode() + "|" + w.getWord() + "|" + ...`
- related record export `w.getPword() + "|" + w.getCword() + "|" + ...`

Tasks:

- [x] Read `name` / `LIME.IM_FULL_NAME` from `imConfig` separately from `version`.
- [x] Write `@cname@|<name>` when a display name exists.
- [x] If exporting escaped v2 by default, write `@format@|lime-text-v2` before metadata that may need escaping.
- [x] Add an `escapeField(String value, String delimiter)` helper near the export code.
- [x] Use `escapeField(...)` for all exported metadata values and record fields in v2 mode.
- [x] Preserve v1 export behavior if v2 export is gated behind an option or future setting.
- [x] Update export JavaDoc above `exportTxtTable(...)` so header list includes `@cname@` and optional `@format@`.

## 5. iOS Importer

Source file:

- `LimeIME-iOS/Shared/Database/LimeDB.swift`

Methods:

- `importTxtFile(at:tableName:progress:)`
- `identifyDelimiter(_:)`
- `parseMetadataLine(_:delimiter:)`
- `flushBatch(_:tableName:)`

Tasks:

- [x] Align iOS import behavior to Android `importTxtTable(...)` before adding new v2 escaping behavior.
- [x] Ensure `parseMetadataLine` returns key `"cname"` for `@cname@|Display Name`. The current `@...@` trimming logic should already produce `cname`; verify with tests.
- [x] In the metadata switch inside `importTxtFile`, handle `"cname"` by setting the local `name` value:

```swift
case "cname":
    name = meta.value
```

- [x] Decide whether `@cname@` should override `%cname` / `"name"` or only fill when `name.isEmpty`; document and test the chosen precedence.
- [x] Import `.cin` `%keyname begin/end` into `imkeys` and `imkeynames`, matching Android's behavior, or document why iOS cannot use that metadata.
- [x] Align `.cin` handling so iOS accepts the same `%chardef begin/end` records Android accepts.
- [x] Align `.lime` handling with Android's legacy behavior. Android accepts mapping rows outside `%chardef`; if iOS keeps requiring `%chardef`, document that as an intentional portability rule and test it.
- [x] Align score and basescore import policy with Android. Android parses optional `score` and `basescore`; iOS currently imports only `code` and `word`.
- [x] Add local import state for `format`, detect `@format@|lime-text-v2`, and enable escaped parsing after that line.
- [x] Add a Swift tokenizer helper, for example:

```swift
private func splitEscapedFields(_ line: String, delimiter: Character, escapedFormat: Bool) -> [String]
```

- [x] Replace `trimmed.components(separatedBy: String(detectedDelimiter))` in `importTxtFile` with the tokenizer.
- [x] Expand the import batch tuple from `(code: String, word: String)` if score/basescore import should become Android-compatible:

```swift
[(code: String, word: String, score: Int, baseScore: Int)]
```

- [x] Update `flushBatch` to insert `score` and `basescore` when available, matching Android, or document the intentional gap.
- [ ] Add XCTest coverage in `LimeIME-iOS/LimeTests/LimeDBTest.swift` for:
  - `.lime` `@cname@|Display Name` imports into `im.title = "name"`.
  - Android parity for `.cin` `%version` / `%cname` / `%selkey` / `%endkey` / `%spacestyle`.
  - Android parity for `.cin` `%keyname` to `imkeys` / `imkeynames`, if implemented.
  - Android parity for `score` and `basescore` import, if implemented.
  - old `.lime` without `@format@` still imports unchanged.
  - v2 escaped pipe in word.
  - v2 escaped leading `@` in code.
  - metadata precedence between `@version@`, `@cname@`, `%version`, and `%cname`.

### 5.1 Completed 2026-07-22 — iOS importer parity gaps closed

Six iOS-only `importTxtFile` gaps against the Android `importTxtTable` oracle were fixed in `LimeIME-iOS/Shared/Database/LimeDB.swift` (Android untouched). Each has a regression test in `LimeIME-iOS/LimeTests/LimeDBTest.swift` (§10a); the full `LimeTests/LimeDBTable` XCTest gate passes on iPhone 17 Pro (iOS 26.5).

1. **Pre-import wipe** — added `DELETE FROM <table>` + `resetImConfig(table)` before the loop (mirrors Android). Without it, the table has no UNIQUE index so `INSERT OR IGNORE` never fired and re-importing the same file doubled every mapping. Test: `testImportReplacesRowsInsteadOfDuplicatingOnReimport`.
2. **UTF-8 BOM strip** — first-line `\u{FEFF}` is removed (`.whitespacesAndNewlines` trimming does not). Test: `testImportStripsUtf8BomFromFirstLine`.
3. **Tolerant `%chardef`/`%keyname` matching** — `hasPrefix("%chardef") && hasSuffix("begin"/"end")` so `%chardef  begin` with extra spaces still opens the block (previously imported zero records). Test: `testImportAcceptsChardefBeginWithExtraSpaces`.
4. **v1 `@`-metadata value re-join** — new `splitLimeMetadataFields` helper re-joins trailing fields so a v1 `@imkeynames@|ㄅ|ㄆ|ㄇ` keeps its full value instead of truncating at the first `|`. Test: `testImportV1MetadataValueWithPipesIsNotTruncated`.
5. **Comma/space metadata delimiter fallback** — the same helper tries `|`, tab, comma, space in order, so `@cname@,Name` / `@cname@ Name` headers parse. Test: `testImportCommaDelimitedMetadataIsParsed`.
6. **Multi-space collapse for space-delimited `.lime`** — runs of 2–5 spaces collapse to one before splitting, so aligned columns (`aa   詞`) no longer yield an empty word. Test: `testImportCollapsesMultipleSpacesInSpaceDelimitedLime`.

Still open from §5's test list: `.cin` `%version/%selkey/%endkey/%spacestyle` parity, `%keyname`→`imkeys/imkeynames` parity, score/basescore parity, and `@version@`/`@cname@`/`%version`/`%cname` precedence tests.

## 6. iOS Exporter

Source file:

- `LimeIME-iOS/Shared/Database/LimeDB.swift`

Method:

- `exportTxtTable(_:targetFile:imConfig:)`

Tasks:

- [x] Align iOS export behavior to Android `exportTxtTable(...)` before adding new v2 escaping behavior.
- [x] Read `name` from `imConfig` independently of `version`.
- [x] Write `@cname@|<name>` after `@version@|...` when a display name exists.
- [x] Align regular-table export fields with Android: `code|word|score|basescore`.
- [x] Align related-table export fields with Android: `pword|cword|basescore|userscore`.
- [x] Decide whether iOS should keep wrapping exported `.lime` records in `%chardef begin/end`. Android export currently does not wrap regular-table rows. If iOS keeps the wrapper for portability, document the intentional difference.
- [x] Add an `escapeField(_ value: String, delimiter: Character) -> String` helper.
- [x] If v2 export is enabled, write `@format@|lime-text-v2` and encode metadata values plus `code`, `word`, `pword`, and `cword`.
- [x] Keep current `%chardef begin/end` wrapping for Android/iOS portability unless the importer policy changes.
- [ ] Add XCTest export assertions that output contains `@cname@|...`, and that v2 export escapes literal pipe and leading at-sign.

## 7. Test Plan

Required principle: every importer/exporter modification must have direct parser/export tests and integrated export-then-reimport tests. Round-trip tests are required for both `.cin` and `.lime` on both Android and iOS.

Android test targets:

- `LimeStudio/app/src/test/...` for JVM tests if LimeDB can be isolated with Robolectric or a test SQLite context.
- `LimeStudio/app/src/androidTest/...` for instrumentation tests when Android context/resources/database setup are required.

iOS test target:

- `LimeIME-iOS/LimeTests/LimeDBTest.swift`

Android importer/exporter tests:

- [ ] Add `.lime` importer test: import `@version@`, `@cname@`, `@selkey@`, `@endkey@`, `@spacestyle@`, then verify `im` metadata rows and mapping rows.
- [ ] Add `.cin` importer test: import `%version`, `%cname`, `%selkey`, `%endkey`, `%spacestyle`, `%keyname`, and `%chardef`, then verify `im` metadata rows, `imkeys`, `imkeynames`, and mapping rows.
- [ ] Add `.lime` exporter test: seed mappings plus metadata, export, and assert the file contains `@version@`, `@cname@`, selection metadata, and `code|word|score|basescore`.
- [ ] Add `.cin` exporter test if a `.cin` exporter is implemented. If Android only exports `.lime`, document this and make the `.cin` integrated round trip use a checked-in/spec-created `.cin` fixture as the first leg.
- [x] Add v2 escaping tests for `.lime`: escaped pipe in code, escaped pipe in word, escaped leading `@` in code, and old v1 file unchanged.

Android integrated round-trip tests:

- [ ] `.lime` export-then-reimport: create a source table with metadata and mappings, export to `.lime`, import into a fresh table/database, then assert metadata and mappings match the source.
- [ ] `.cin` export-then-reimport: export to `.cin` if `.cin` export exists; otherwise import a `.cin` fixture, export to the closest supported text format, reimport, and assert the imported `.cin` metadata/mappings survive the round trip according to the documented export capability.
- [ ] `.lime` v2 export-then-reimport: include literal `|` and leading `@` in mapping data, export v2, reimport, and assert exact equality.
- [ ] Related table export-then-reimport: export related phrase rows, import into a fresh related table, and assert `pword`, `cword`, `basescore`, and `userscore`.

iOS importer/exporter tests:

- [ ] Add `.lime` importer test: import `@version@`, `@cname@`, `@selkey@`, `@endkey@`, `@spacestyle@`, then verify `im` metadata rows and mapping rows match Android expectations.
- [ ] Add `.cin` importer test: import `%version`, `%cname`, `%selkey`, `%endkey`, `%spacestyle`, `%keyname`, and `%chardef`, then verify metadata and mapping rows match Android expectations.
- [x] Add `.lime` exporter test: seed mappings plus metadata, export, and assert output matches Android field order and metadata policy, except documented intentional differences.
- [ ] Add `.cin` exporter test if a `.cin` exporter is implemented. If iOS only exports `.lime`, document this and make the `.cin` integrated round trip use a fixture-first flow.
- [ ] Add v2 escaping tests for `.lime`: escaped pipe in code, escaped pipe in word, escaped leading `@` in code, and old v1 file unchanged.

iOS integrated round-trip tests:

- [ ] `.lime` export-then-reimport: create a source table with metadata and mappings, export to `.lime`, import into a fresh table/database, then assert metadata and mappings match the source.
- [ ] `.cin` export-then-reimport: export to `.cin` if `.cin` export exists; otherwise import a `.cin` fixture, export to the closest supported text format, reimport, and assert the imported `.cin` metadata/mappings survive the round trip according to the documented export capability.
- [ ] `.lime` v2 export-then-reimport: include literal `|` and leading `@` in mapping data, export v2, reimport, and assert exact equality.
- [ ] Related table export-then-reimport: export related phrase rows, import into a fresh related table, and assert `pword`, `cword`, `basescore`, and `userscore`.

Cross-platform parity tests:

- [ ] Android-exported `.lime` imports correctly on iOS.
- [ ] iOS-exported `.lime` imports correctly on Android.
- [ ] Android `.cin` fixture imports identically on Android and iOS.
- [ ] For every intentional iOS difference from Android, add a named test and a spec note explaining why the difference exists.

## 8. Shared Acceptance Criteria

- [ ] Android legacy `.cin` / `.lime` import behavior remains unchanged for existing files.
- [ ] Android legacy `.lime` export behavior remains unchanged unless v2 export is explicitly enabled.
- [ ] iOS import behavior matches Android for all non-v2 `.cin` / `.lime` cases, except differences explicitly documented in the spec.
- [ ] iOS export behavior matches Android for all non-v2 `.lime` cases, except differences explicitly documented in the spec.
- [x] Android and iOS both import `.lime` `@cname@|Display Name` as the IM display name.
- [x] Android and iOS both export `.lime` display name as `@cname@|Display Name`.
- [ ] Old `.cin` and `.lime` files without `@format@|lime-text-v2` import exactly as before.
- [x] Escaped v2 `.lime` files can represent literal `|` in code/word.
- [x] Escaped v2 `.lime` files can represent a code that begins with literal `@`.
- [ ] Exported v2 files can be re-imported on both Android and iOS without losing code, word, score/basescore policy, or IM metadata.
- [ ] Android has integrated export-then-reimport coverage for `.lime`.
- [ ] Android has integrated export-then-reimport coverage for `.cin`, or a documented fixture-first equivalent if `.cin` export is intentionally unsupported.
- [ ] iOS has integrated export-then-reimport coverage for `.lime`.
- [ ] iOS has integrated export-then-reimport coverage for `.cin`, or a documented fixture-first equivalent if `.cin` export is intentionally unsupported.
