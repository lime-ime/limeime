# IM Version Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store imported IM version/name metadata as a normal `im` table key-value row with `title = "version"` on both iOS and Android, and display that value in each IM detail page.

**Architecture:** Do not change the `im` table schema. Treat `version` as another metadata entry alongside `source`, `name`, `amount`, `import`, `selkey`, `endkey`, `spacestyle`, `imkeys`, and `imkeynames`. New imports should read version metadata from `.lime` `@version@|...` headers and `.cin` `%version ...` headers, persist it via existing `setImConfig(table, "version", value)` APIs, and make detail screens read `getImConfig(table, "version")` with a fallback for older data.

**Tech Stack:** Swift/GRDB/XCTest for `LimeIME-iOS`; Java/SQLite/Android instrumentation tests for `LimeStudio`; SwiftUI and Android Fragment UI detail screens.

**Implementation Status (2026-05-17):** Implemented for iOS and Android. `.lime` imports read `@version@|...`; `.cin` imports read canonical `%version ...`; legacy `%cname ...` remains a fallback when `%version` is missing. Detail screens now read `im.version` first, then legacy `{table}mapping_version`, then `im.source`, then `im.name`.

---

## Current State

iOS:

- `IMDetailView` currently reads version text from shared app-group `UserDefaults` using key `im.tableNick + "mapping_version"`.
- `LimeDB.getImConfig(_:_:)` and `SearchServer.getImConfig(_:_:)` can already read arbitrary `im` metadata rows by `code` and `title`.
- `LimeDB.importFromAttachedDB(sourcePath:tableName:)` copies all source `im` rows for the imported table, so a source DB row with `title = "version"` would already survive DB imports.
- `LimeDB.importTxtFile(at:tableName:)` currently imports mapping rows only. It skips `%...` headers and does not parse `@version@`, `%version`, `%cname`, `selkey`, `endkey`, `spacestyle`, or keyname metadata.

Android:

- `ImDetailFragment` currently reads version text from default `SharedPreferences` using key `tableCode + "mapping_version"`.
- `LimeDB.importTxtTable(...)` already parses `.lime` `@version@|...` and legacy `.cin` `%cname ...`, but stores the value in `im.title = "name"` via `setImConfig(table, "name", imname)`. It does not yet support canonical `.cin` `%version ...`.
- Android exports currently write `@version@|...` when the supplied config row title is `LIME.IM_FULL_NAME`/`name`, not from a dedicated `version` row.

## Desired Behavior

- The canonical version metadata lives in `im` as:

```text
code = <table name>
title = "version"
desc = <version text from import file>
```

- `.lime` import reads:

```text
@version@|Some Version Text
```

and stores `Some Version Text` as `setImConfig(table, "version", "Some Version Text")`.

- `.cin` import reads the new canonical tag:

```text
%version Some Version Text
```

or equivalent delimiter-supported forms, and stores the parsed value as `version`.

- Legacy `.cin` `%cname Some Display Name` remains supported as a fallback source for `name`, and may populate `version` only when `%version` is absent.
- New imports may continue to set `name` to the same value when no better name exists, preserving legacy behavior and IM list labels.
- Existing databases are not migrated in bulk. The detail UI should use a compatibility fallback so old installed tables still show useful data.

## Compatibility Fallback

When displaying IM detail version:

1. Read `im.title = "version"` via `getImConfig(table, "version")`.
2. If empty, read legacy shared preference `{table}mapping_version`.
3. If empty, read `im.title = "source"` for old imports where the filename is the only persisted metadata.
4. If empty, optionally read `im.title = "name"` only for old imports where `name` came from `@version@` or legacy `%cname`.
5. If still empty, show `—` on iOS and `-` on Android to match existing platform conventions.

For exports:

1. Prefer `im.title = "version"` for `@version@|...`.
2. Fall back to `im.title = "name"` if `version` is absent so existing exports do not regress.

## Files To Modify

- `LimeIME-iOS/Shared/Database/LimeDB.swift`
  - Parse and persist `version` metadata during text import.
  - Preserve existing import behavior for `name`, `amount`, `source`, `import`, and key metadata.
  - Export `@version@` from `version` first, then `name` fallback.

- `LimeIME-iOS/LimeSettings/Views/IMDetailView.swift`
  - Replace direct `UserDefaults` version lookup with DB metadata lookup through `DBServer` or `SearchServer`.
  - Keep shared preference fallback.

- `LimeIME-iOS/LimeTests/LimeDBTest.swift`
  - Add tests for `.lime` `@version@` import, `.cin` `%version` import, legacy `.cin` `%cname` fallback, and export preference for `version`.

- `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java`
  - Persist parsed `@version@` / `%version` values to `im.title = "version"`.
  - Treat `%cname` as legacy display-name metadata and as a version fallback only when `%version` is absent.
  - Keep legacy `name` population where needed for display compatibility.
  - Export `@version@` from `version` first, then `name` fallback.

- `LimeStudio/app/src/main/java/org/limeime/ui/view/ImDetailFragment.java`
  - Read `SearchServer`/`LimeDB` IM config `version` first, then legacy shared preference, then `name` fallback.

- `LimeStudio/app/src/androidTest/java/org/limeime/LimeDBTest.java`
  - Add Android coverage for import/export metadata behavior.

- `docs/PREFS_TABLE.md`
  - Update the IM Detail version row note: version is canonical in `im.title = "version"`; old `{table}mapping_version` preference is fallback only.

## Task 1: iOS Text Import Persists Version Metadata

**Files:**

- Modify: `LimeIME-iOS/Shared/Database/LimeDB.swift`
- Test: `LimeIME-iOS/LimeTests/LimeDBTest.swift`

- [ ] **Step 1: Add failing XCTest for `.lime` `@version@` import**

Add a test near the existing IM config/import tests:

```swift
func testImportTxtFileStoresVersionMetadataFromLimeHeader() throws {
    let db = try makeLimeDB()
    let importURL = FileManager.default.temporaryDirectory
        .appendingPathComponent(UUID().uuidString + ".lime")
    defer { try? FileManager.default.removeItem(at: importURL) }

    let content = """
    @version@|My Custom Table 2026.05
    @selkey@|123456789
    %chardef begin
    aa|測
    ab|試
    %chardef end
    """
    try content.write(to: importURL, atomically: true, encoding: .utf8)

    try db.importTxtFile(at: importURL.path, tableName: LIME.DB_TABLE_CUSTOM)

    XCTAssertEqual(db.getImConfig(LIME.DB_TABLE_CUSTOM, "version"), "My Custom Table 2026.05")
    XCTAssertEqual(db.getImConfig(LIME.DB_TABLE_CUSTOM, "selkey"), "123456789")
    XCTAssertEqual(db.getImConfig(LIME.DB_TABLE_CUSTOM, "amount"), "2")
}
```

- [ ] **Step 2: Add failing XCTest for `.cin` `%version` import**

```swift
func testImportTxtFileStoresVersionMetadataFromCinVersion() throws {
    let db = try makeLimeDB()
    let importURL = FileManager.default.temporaryDirectory
        .appendingPathComponent(UUID().uuidString + ".cin")
    defer { try? FileManager.default.removeItem(at: importURL) }

    let content = """
    %version 大易測試版 1.2.3
    %cname 大易測試表
    %selkey 123456789
    %chardef begin
    a 測
    b 試
    %chardef end
    """
    try content.write(to: importURL, atomically: true, encoding: .utf8)

    try db.importTxtFile(at: importURL.path, tableName: LIME.DB_TABLE_CUSTOM)

    XCTAssertEqual(db.getImConfig(LIME.DB_TABLE_CUSTOM, "version"), "大易測試版 1.2.3")
    XCTAssertEqual(db.getImConfig(LIME.DB_TABLE_CUSTOM, "name"), "大易測試表")
    XCTAssertEqual(db.getImConfig(LIME.DB_TABLE_CUSTOM, "selkey"), "123456789")
    XCTAssertEqual(db.getImConfig(LIME.DB_TABLE_CUSTOM, "amount"), "2")
}
```

- [ ] **Step 3: Add failing XCTest for legacy `.cin` `%cname` fallback**

```swift
func testImportTxtFileUsesCinCnameAsVersionFallbackWhenVersionMissing() throws {
    let db = try makeLimeDB()
    let importURL = FileManager.default.temporaryDirectory
        .appendingPathComponent(UUID().uuidString + ".cin")
    defer { try? FileManager.default.removeItem(at: importURL) }

    let content = """
    %cname 舊格式輸入法名稱
    %chardef begin
    a 測
    %chardef end
    """
    try content.write(to: importURL, atomically: true, encoding: .utf8)

    try db.importTxtFile(at: importURL.path, tableName: LIME.DB_TABLE_CUSTOM)

    XCTAssertEqual(db.getImConfig(LIME.DB_TABLE_CUSTOM, "version"), "舊格式輸入法名稱")
    XCTAssertEqual(db.getImConfig(LIME.DB_TABLE_CUSTOM, "name"), "舊格式輸入法名稱")
}
```

- [ ] **Step 4: Run iOS failing tests**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' -only-testing:LimeTests/LimeDBTest/testImportTxtFileStoresVersionMetadataFromLimeHeader -only-testing:LimeTests/LimeDBTest/testImportTxtFileStoresVersionMetadataFromCinVersion -only-testing:LimeTests/LimeDBTest/testImportTxtFileUsesCinCnameAsVersionFallbackWhenVersionMissing test
```

Expected: all three tests fail because `version`, `selkey`, and `amount` are not persisted by iOS text import.

- [ ] **Step 5: Implement metadata parsing in `importTxtFile`**

Inside `LimeDB.importTxtFile(at:tableName:progress:)`, add local metadata variables before the loop:

```swift
let sourceName = URL(fileURLWithPath: path).lastPathComponent
var version = ""
var name = ""
var selkey = ""
var endkey = ""
var spacestyle = ""
```

Add a helper inside `LimeDB`:

```swift
private func parseMetadataLine(_ trimmed: String, delimiter: Character?) -> (key: String, value: String)? {
    if trimmed.hasPrefix("@") {
        let delimiterString = String(delimiter ?? (trimmed.contains("|") ? "|" : "\t"))
        let parts = trimmed.components(separatedBy: delimiterString)
        guard parts.count >= 2 else { return nil }
        let key = parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "@"))
            .lowercased()
        let value = parts.dropFirst().joined(separator: delimiterString)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : (key, value)
    }

    let lower = trimmed.lowercased()
    for prefix in ["%version", "%cname", "%selkey", "%endkey", "%spacestyle"] where lower.hasPrefix(prefix) {
        let rawValue = String(trimmed.dropFirst(prefix.count))
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let key = String(prefix.dropFirst())
        return rawValue.isEmpty ? nil : (key, rawValue)
    }

    return nil
}
```

In the import loop, before skipping `%` or `@` lines, parse metadata:

```swift
if let meta = parseMetadataLine(trimmed, delimiter: delimiterDetected ? detectedDelimiter : nil) {
    switch meta.key {
    case "version":
        version = meta.value
        if name.isEmpty { name = meta.value }
    case "cname":
        if name.isEmpty { name = meta.value }
        if version.isEmpty { version = meta.value }
    case "name":
        name = meta.value
    case "selkey":
        selkey = meta.value
    case "endkey":
        endkey = meta.value
    case "spacestyle":
        spacestyle = meta.value
    default:
        break
    }
    continue
}
```

After the final batch flush, persist metadata:

```swift
if !importCancelled {
    setImConfig(tableName, "source", sourceName)
    setImConfig(tableName, "version", version.isEmpty ? sourceName : version)
    setImConfig(tableName, "name", name.isEmpty ? sourceName : name)
    setImConfig(tableName, "amount", String(totalInserted))
    setImConfig(tableName, "import", Date().description)
    if !selkey.isEmpty { setImConfig(tableName, "selkey", selkey) }
    if !endkey.isEmpty { setImConfig(tableName, "endkey", endkey) }
    if !spacestyle.isEmpty { setImConfig(tableName, "spacestyle", spacestyle) }
}
```

Keep existing record import semantics: only rows inside `%chardef begin` / `%chardef end` are imported, comment lines are ignored, and mapping records still use `flushBatch`.

- [ ] **Step 6: Run iOS import tests again**

Run the same `xcodebuild` command from Step 3.

Expected: all three import metadata tests pass.

- [ ] **Step 7: Commit iOS import change**

```bash
git add LimeIME-iOS/Shared/Database/LimeDB.swift LimeIME-iOS/LimeTests/LimeDBTest.swift
git commit -m "feat: store imported IM version metadata on iOS"
```

Do not add any co-author trailer.

## Task 2: iOS Detail Page Reads `im.version`

**Files:**

- Modify: `LimeIME-iOS/LimeSettings/Views/IMDetailView.swift`
- Test: `LimeIME-iOS/LimeTests/LimeDBTest.swift` or existing UI-adjacent tests if available

- [ ] **Step 1: Add a small metadata resolver**

In `IMDetailView`, replace the direct-only `UserDefaults` implementation with a resolver that reads DB metadata first:

```swift
private var mappingVersion: String {
    let table = im.tableNick
    let server = DBServer.shared
    if let search = server.makeSearchServer() {
        let version = search.getImConfig(table, "version")
        if !version.isEmpty { return version }

        let name = search.getImConfig(table, "name")
        if !name.isEmpty { return name }
    }

    let legacy = sharedUD?.string(forKey: table + "mapping_version") ?? ""
    return legacy.isEmpty ? "—" : legacy
}
```

If `DBServer.makeSearchServer()` is not suitable in this view because of lifecycle or threading, add a narrow proxy to `DBServer`:

```swift
func getImConfig(_ imCode: String, _ field: String) -> String {
    datasource?.getImConfig(imCode, field) ?? ""
}
```

Then call `DBServer.shared.getImConfig(table, "version")` from the view.

- [ ] **Step 2: Verify iOS build**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' build
```

Expected: build succeeds.

- [ ] **Step 3: Commit iOS detail change**

```bash
git add LimeIME-iOS/LimeSettings/Views/IMDetailView.swift LimeIME-iOS/Shared/Database/DBServer.swift
git commit -m "fix: read IM detail version from metadata"
```

Do not add any co-author trailer.

## Task 3: iOS Export Writes Dedicated Version Row

**Files:**

- Modify: `LimeIME-iOS/Shared/Database/LimeDB.swift`
- Test: `LimeIME-iOS/LimeTests/LimeDBTest.swift`

- [ ] **Step 1: Add failing export test**

```swift
func testExportTxtTableUsesVersionMetadataForVersionHeader() throws {
    let db = try makeLimeDB()
    db.setTableName(LIME.DB_TABLE_CUSTOM)
    db.addOrUpdateMappingRecord("aa", "測")
    db.setImConfig(LIME.DB_TABLE_CUSTOM, "name", "Friendly Name")
    db.setImConfig(LIME.DB_TABLE_CUSTOM, "version", "Version 2.0")

    let exportURL = FileManager.default.temporaryDirectory
        .appendingPathComponent(UUID().uuidString + ".lime")
    defer { try? FileManager.default.removeItem(at: exportURL) }

    let configs = db.getImConfigList(LIME.DB_TABLE_CUSTOM, nil)
    XCTAssertTrue(db.exportTxtTable(LIME.DB_TABLE_CUSTOM, targetFile: exportURL, imConfig: configs))

    let output = try String(contentsOf: exportURL, encoding: .utf8)
    XCTAssertTrue(output.contains("@version@|Version 2.0"))
    XCTAssertFalse(output.contains("@version@|Friendly Name"))
}
```

- [ ] **Step 2: Run failing export test**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' -only-testing:LimeTests/LimeDBTest/testExportTxtTableUsesVersionMetadataForVersionHeader test
```

Expected: fail because export currently maps `name` to `@version@`.

- [ ] **Step 3: Update export logic**

In `exportTxtTable(_:targetFile:imConfig:)`, replace the loop that writes metadata with logic that computes values first:

```swift
let configByTitle = Dictionary(uniqueKeysWithValues: configs.map { ($0.title, $0.desc) })
let version = configByTitle["version"] ?? configByTitle["name"] ?? ""
if !version.isEmpty { lines.append("@version@|\(version)") }
if let selkey = configByTitle["selkey"], !selkey.isEmpty { lines.append("@selkey@|\(selkey)") }
if let endkey = configByTitle["endkey"], !endkey.isEmpty { lines.append("@endkey@|\(endkey)") }
if let spacestyle = configByTitle["spacestyle"], !spacestyle.isEmpty { lines.append("@spacestyle@|\(spacestyle)") }
```

- [ ] **Step 4: Run export test**

Run the command from Step 2.

Expected: pass.

- [ ] **Step 5: Commit iOS export change**

```bash
git add LimeIME-iOS/Shared/Database/LimeDB.swift LimeIME-iOS/LimeTests/LimeDBTest.swift
git commit -m "fix: export dedicated IM version metadata on iOS"
```

Do not add any co-author trailer.

## Task 4: Android Text Import Persists Dedicated Version Row

**Files:**

- Modify: `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java`
- Test: `LimeStudio/app/src/androidTest/java/org/limeime/LimeDBTest.java`

- [ ] **Step 1: Add failing Android import test**

Add instrumentation tests near existing `setImConfig` or export tests:

```java
@Test
public void testImportTxtTableStoresVersionMetadataFromLimeHeader() throws Exception {
    File file = File.createTempFile("lime-version", ".lime", getContext().getCacheDir());
    try {
        String content = "@version@|My Android Table 2026.05\n"
                + "@selkey@|123456789\n"
                + "%chardef begin\n"
                + "aa|測\n"
                + "ab|試\n"
                + "%chardef end\n";
        writeUtf8(file, content);

        limeDB.importTxtTable(file, LIME.DB_TABLE_CUSTOM);

        assertEquals("My Android Table 2026.05",
                limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "version"));
        assertEquals("123456789",
                limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "selkey"));
    } finally {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
```

Add a second test for canonical `.cin` `%version` support:

```java
@Test
public void testImportTxtTableStoresVersionMetadataFromCinVersion() throws Exception {
    File file = File.createTempFile("lime-version", ".cin", getContext().getCacheDir());
    try {
        String content = "%version 大易測試版 1.2.3\n"
                + "%cname 大易測試表\n"
                + "%selkey 123456789\n"
                + "%chardef begin\n"
                + "a 測\n"
                + "b 試\n"
                + "%chardef end\n";
        writeUtf8(file, content);

        limeDB.importTxtTable(file, LIME.DB_TABLE_CUSTOM);

        assertEquals("大易測試版 1.2.3",
                limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "version"));
        assertEquals("大易測試表",
                limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "name"));
        assertEquals("123456789",
                limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "selkey"));
    } finally {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
```

Add a third test for old `%cname`-only files:

```java
@Test
public void testImportTxtTableUsesCinCnameAsVersionFallbackWhenVersionMissing() throws Exception {
    File file = File.createTempFile("lime-version-legacy", ".cin", getContext().getCacheDir());
    try {
        String content = "%cname 舊格式輸入法名稱\n"
                + "%chardef begin\n"
                + "a 測\n"
                + "%chardef end\n";
        writeUtf8(file, content);

        limeDB.importTxtTable(file, LIME.DB_TABLE_CUSTOM);

        assertEquals("舊格式輸入法名稱",
                limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "version"));
        assertEquals("舊格式輸入法名稱",
                limeDB.getImConfig(LIME.DB_TABLE_CUSTOM, "name"));
    } finally {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
```

Use the test class existing context/helper style. If there is no `writeUtf8` helper, add:

```java
private void writeUtf8(File file, String content) throws IOException {
    try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
        writer.write(content);
    }
}
```

- [ ] **Step 2: Run failing Android test**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=net.toload.main.hd.LimeDBTest
```

Expected: fail because Android currently stores parsed header text in `name`, not `version`, and does not parse `%version`.

- [ ] **Step 3: Update Android import finalization**

In `LimeDB.importTxtTable(...)`, rename the local metadata variable from `imname` to a clearer pair if desired:

```java
String version = "";
String imname = "";
```

When parsing `@version@`:

```java
if (codeLower.contains("@version@")) {
    version = word.trim();
    if (imname.isEmpty()) imname = version;
}
```

When parsing `%version`:

```java
if (codeLower.contains("%version")) {
    version = word.trim();
    if (imname.isEmpty()) imname = version;
    continue;
}
```

When parsing `%cname`, keep it as display-name metadata and version fallback:

```java
if (codeLower.contains("%cname")) {
    imname = word.trim();
    if (version.isEmpty()) version = imname;
    continue;
}
```

In the final metadata section, add:

```java
if (version.isEmpty()) {
    version = filename.getName();
}
setImConfig(table, "version", version);
```

Keep existing `setImConfig(table, "name", ...)` behavior so IM list labels do not change.

- [ ] **Step 4: Run Android import test**

Run the command from Step 2.

Expected: the new import metadata tests pass.

- [ ] **Step 5: Commit Android import change**

```bash
git add LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java LimeStudio/app/src/androidTest/java/org/limeime/LimeDBTest.java
git commit -m "feat: store imported IM version metadata on Android"
```

Do not add any co-author trailer.

## Task 5: Android Detail Page Reads `im.version`

**Files:**

- Modify: `LimeStudio/app/src/main/java/org/limeime/ui/view/ImDetailFragment.java`
- Test: `LimeStudio/app/src/androidTest/java/org/limeime/IntegrationTestSearchServerDBServer.java` or `LimeStudio/app/src/androidTest/java/org/limeime/LimeDBTest.java`

- [ ] **Step 1: Add metadata lookup helper in `ImDetailFragment`**

Locate the current SharedPreferences block:

```java
android.content.SharedPreferences versionSp =
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
String version = versionSp.getString(tableCode + "mapping_version", "-");
```

Replace with:

```java
String version = "";
try {
    if (searchServer != null) {
        version = searchServer.getImConfig(tableCode, "version");
        if (version == null || version.isEmpty()) {
            version = searchServer.getImConfig(tableCode, "name");
        }
    }
} catch (Exception ignored) {
    version = "";
}

if (version == null || version.isEmpty()) {
    android.content.SharedPreferences versionSp =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
    version = versionSp.getString(tableCode + "mapping_version", "-");
}
if (version == null || version.isEmpty()) version = "-";
if (txtImVersion != null) txtImVersion.setText(version);
```

Prefer the existing field/member used by the fragment for `SearchServer`; do not create a second DB object if the fragment already has one.

- [ ] **Step 2: Run Android build/test for compile safety**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: build succeeds.

- [ ] **Step 3: Commit Android detail change**

```bash
git add LimeStudio/app/src/main/java/org/limeime/ui/view/ImDetailFragment.java
git commit -m "fix: read IM detail version from metadata on Android"
```

Do not add any co-author trailer.

## Task 6: Android Export Writes Dedicated Version Row

**Files:**

- Modify: `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java`
- Test: `LimeStudio/app/src/androidTest/java/org/limeime/LimeDBTest.java`

- [ ] **Step 1: Add failing export test**

```java
@Test
public void testExportTxtTableUsesVersionMetadataForVersionHeader() throws Exception {
    limeDB.setImConfig(LIME.DB_TABLE_CUSTOM, "name", "Friendly Name");
    limeDB.setImConfig(LIME.DB_TABLE_CUSTOM, "version", "Version 2.0");
    limeDB.addOrUpdateMappingRecord("aa", "測");

    ArrayList<ImConfig> configs = new ArrayList<>(
            limeDB.getImConfigList(LIME.DB_TABLE_CUSTOM, null));
    File out = File.createTempFile("lime-export-version", ".lime", getContext().getCacheDir());
    try {
        limeDB.exportTxtTable(LIME.DB_TABLE_CUSTOM, out, configs);
        String output = readUtf8(out);
        assertTrue(output.contains("@version@|Version 2.0"));
        assertFalse(output.contains("@version@|Friendly Name"));
    } finally {
        //noinspection ResultOfMethodCallIgnored
        out.delete();
    }
}
```

If needed, add:

```java
private String readUtf8(File file) throws IOException {
    byte[] bytes = Files.readAllBytes(file.toPath());
    return new String(bytes, StandardCharsets.UTF_8);
}
```

- [ ] **Step 2: Run failing Android export test**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=net.toload.main.hd.LimeDBTest#testExportTxtTableUsesVersionMetadataForVersionHeader
```

Expected: fail if export still maps only `name`/`LIME.IM_FULL_NAME` to `@version@`.

- [ ] **Step 3: Update export metadata selection**

In the Android export path that currently writes:

```java
String s = "@version@|" + i.getDesc();
```

change the surrounding logic to first collect:

```java
String version = "";
String name = "";
String selkey = "";
String endkey = "";
String spacestyle = "";
```

For each config:

```java
if ("version".equals(i.getTitle())) version = i.getDesc();
else if (LIME.IM_FULL_NAME.equals(i.getTitle()) || "name".equals(i.getTitle())) name = i.getDesc();
else if ("selkey".equals(i.getTitle())) selkey = i.getDesc();
else if ("endkey".equals(i.getTitle())) endkey = i.getDesc();
else if ("spacestyle".equals(i.getTitle())) spacestyle = i.getDesc();
```

Then write:

```java
String exportVersion = !version.isEmpty() ? version : name;
if (!exportVersion.isEmpty()) {
    buf.write("@version@|" + exportVersion);
    buf.newLine();
}
```

Keep existing selkey/endkey/spacestyle export behavior.

- [ ] **Step 4: Run Android export test**

Run the command from Step 2.

Expected: pass.

- [ ] **Step 5: Commit Android export change**

```bash
git add LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java LimeStudio/app/src/androidTest/java/org/limeime/LimeDBTest.java
git commit -m "fix: export dedicated IM version metadata on Android"
```

Do not add any co-author trailer.

## Task 7: Documentation And Full Verification

**Files:**

- Modify: `docs/PREFS_TABLE.md`
- Keep: `docs/IM_VERSION.md`

- [ ] **Step 1: Update preference documentation**

In `docs/PREFS_TABLE.md`, update the IM Detail version note to say:

```markdown
IM Detail `版本` is canonical in the `im` table as `title = "version"` and `desc = <version text>`.
The legacy `{table}mapping_version` preference is retained only as a display fallback for older installs.
New `.lime` and `.cin` imports populate `im.version` from `@version@` and `%version`; legacy `%cname` is a fallback when `%version` is missing.
```

- [ ] **Step 2: Run targeted iOS tests**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' -only-testing:LimeTests/LimeDBTest test
```

Expected: all `LimeDBTest` tests pass.

- [ ] **Step 3: Run iOS build**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' build
```

Expected: build succeeds.

- [ ] **Step 4: Run targeted Android tests**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=net.toload.main.hd.LimeDBTest
```

Expected: `LimeDBTest` passes on the connected emulator/device.

- [ ] **Step 5: Run Android build**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: build succeeds.

- [ ] **Step 6: Manual smoke test**

On both platforms:

1. Import a `.lime` file containing `@version@|Smoke Version 1`.
2. Open the imported IM detail page.
3. Confirm `版本` displays `Smoke Version 1`.
4. Import a `.cin` file containing `%version Smoke Version 2`.
5. Open the imported IM detail page.
6. Confirm `版本` displays `Smoke Version 2`.
7. Export the IM as text.
8. Confirm the exported file contains `@version@|Smoke Version 2`.

- [ ] **Step 7: Commit docs**

```bash
git add docs/PREFS_TABLE.md docs/IM_VERSION.md
git commit -m "docs: plan IM version metadata storage"
```

Do not add any co-author trailer.

## Risks And Notes

- `%version` is the new canonical `.cin` tag. `%cname` historically behaved like a display name on Android, even though the comment says it was used as `mapping_version`; keep populating `name` for compatibility, and use `%cname` as the `version` fallback only when `%version` is absent.
- iOS text import is currently simpler than Android text import. Adding metadata parsing should stay narrow: do not attempt to fully port Android scoring, keyname, or related import behavior unless a test proves it is required for version metadata.
- DB imports from `.limedb` already copy source `im` rows on iOS when present. Android should be checked during implementation, but the expected behavior is that DB imports preserve `im.title = "version"` if the source DB includes it.
- Avoid schema migrations. The existing key-value `im` table is enough.
- Avoid large data backfills. Display fallback handles old installs without rewriting user databases.

## Self-Review

- Spec coverage: The plan covers no schema change, both iOS and Android, new imports from `.lime` and `.cin`, detail-page display, export preservation, docs, and verification.
- Placeholder scan: No unfinished placeholder markers or open-ended implementation placeholders remain; all tasks name concrete files and commands.
- Type consistency: The canonical field name is consistently `version`, stored as `im.title = "version"` and read through existing `getImConfig(table, "version")` APIs.
