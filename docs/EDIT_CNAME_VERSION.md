# Edit IM Name And Version Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users edit an input method's display name and version name in iOS LIME Settings, persisting both values into the `im` table as `title = "name"` and `title = "version"` rows.

**Architecture:** Keep database writes in the Model/Controller layers and keep SwiftUI as a thin view. iOS follows the Android storage contract: metadata lives in the `im` table, with `code = tableNick`, `title = metadata key`, and `desc = edited value`. Android already supports the DB write path but only displays these values, so this plan changes iOS only.

**Tech Stack:** Swift, SwiftUI, GRDB-backed `LimeDB`, `DBServer`, `ManageImController`, XCTest, Xcode command line builds.

---

## Context

Android findings:

- `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java` imports metadata using `setImConfig(table, "version", version)` and `setImConfig(table, "name", imname)`.
- Android `LimeDB.setImConfig(imCode, field, value)` writes to the `im` table by deleting the existing `code + title` row and inserting the new value into `desc`.
- Android `SearchServer.setImConfig(...)` exposes the write path, but currently returns `false` even after success, so callers should not depend on that boolean.
- Android `ImDetailFragment` displays `name` and `version` with `TextView`; it does not provide user editing.

iOS findings:

- `LimeIME-iOS/Shared/Database/LimeDB.swift` already implements `setImConfig(_:_:)`.
- `LimeIME-iOS/Shared/Search/SearchServer.swift` already exposes `setImConfig(...)`.
- `LimeIME-iOS/Shared/Database/DBServer.swift` exposes `getImConfig(...)` but does not expose a generic `setImConfig(...)` proxy.
- `LimeIME-iOS/LimeSettings/Views/IMDetailView.swift` displays name and version as read-only `LabeledContent`.
- `LimeDB.getAllImConfigs()` currently treats `name` as a key/value metadata row, but not `version`; add `version` to the key/value field set so an edited version row cannot be mistaken for a seed/display row.

## Files

- Modify: `LimeIME-iOS/Shared/Database/DBServer.swift`
  - Add `setImConfig(_ imCode: String, _ field: String, _ value: String)` proxy.
- Modify: `LimeIME-iOS/Shared/Database/LimeDB.swift`
  - Add `"version"` to `kvFields` in `getAllImConfigs()`.
- Modify: `LimeIME-iOS/LimeSettings/Controllers/ManageImController.swift`
  - Add an async metadata update method that validates input, writes `name` and `version`, invalidates IM list state, and marks keyboard cache dirty.
- Modify: `LimeIME-iOS/LimeSettings/Views/IMDetailView.swift`
  - Add editable state, a sheet or alert-style edit form, save handling, and local UI refresh.
- Modify: `LimeIME-iOS/LimeSettings/Views/IMListView.swift`
  - No direct feature logic expected. It should refresh via `ManageImController.invalidate()`. Only touch this file if destination refresh state proves stale.
- Test: `LimeIME-iOS/LimeTests/LimeDBTest.swift`
  - Add a regression test proving an edited `version` row does not become the display label/seed row.
- Test: `LimeIME-iOS/LimeTests/DBServerTest.swift`
  - Add a test for the new DBServer metadata write proxy.
- Test: `LimeIME-iOS/LimeTests/ManageImControllerTest.swift`
  - Add tests for successful metadata update and validation.

---

### Task 1: Protect `version` As IM Metadata

**Files:**
- Modify: `LimeIME-iOS/Shared/Database/LimeDB.swift`
- Test: `LimeIME-iOS/LimeTests/LimeDBTest.swift`

- [ ] **Step 1: Write the failing regression test**

Add this test near `testGetAllImConfigsLabelPrefersNameRow()` in `LimeIME-iOS/LimeTests/LimeDBTest.swift`:

```swift
func testGetAllImConfigsDoesNotUseVersionAsSeedRow() throws {
    let db = try makeLimeDB()
    db.setImConfig(LIME.DB_TABLE_CUSTOM, "version", "Version 9.9")
    db.setImConfig(LIME.DB_TABLE_CUSTOM, "name", "Friendly Custom")
    let keyboard = KeyboardConfig(id: 1,
                                  code: "lime",
                                  name: "LIME",
                                  desc: "LIME Keyboard",
                                  type: "qwerty",
                                  image: "",
                                  imkb: "lime",
                                  imshiftkb: "lime_shift",
                                  engkb: "lime_abc",
                                  engshiftkb: "lime_abc_shift",
                                  symbolkb: "symbols",
                                  symbolshiftkb: "symbols_shift",
                                  isDisabled: false)
    db.setImConfigKeyboard(LIME.DB_TABLE_CUSTOM, keyboard)

    let configs = try db.getAllImConfigs()
    let custom = configs.first(where: { $0.tableNick == LIME.DB_TABLE_CUSTOM })

    XCTAssertNotNil(custom)
    XCTAssertEqual(custom?.label, "Friendly Custom")
    XCTAssertNotEqual(custom?.label, "Version 9.9")
}
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' -only-testing:LimeTests/LimeDBTest/testGetAllImConfigsDoesNotUseVersionAsSeedRow test
```

Expected before implementation: the test may fail because `"version"` can be treated as a seed row, or it may expose missing constructor details that must be aligned with the existing `KeyboardConfig` initializer in `LimeIME-iOS/Shared/Models/Keyboard.swift`.

- [ ] **Step 3: Implement the metadata-field fix**

In `LimeIME-iOS/Shared/Database/LimeDB.swift`, update the `kvFields` set in `getAllImConfigs()` to include `"version"`:

```swift
let kvFields: Set<String> = ["keyboard","disable","selkey","endkey","spacestyle",
                             "imkeys","imkeynames","name","label","version"]
```

- [ ] **Step 4: Run the regression test**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' -only-testing:LimeTests/LimeDBTest/testGetAllImConfigsDoesNotUseVersionAsSeedRow test
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 5: Commit**

```bash
git add LimeIME-iOS/Shared/Database/LimeDB.swift LimeIME-iOS/LimeTests/LimeDBTest.swift
git commit -m "fix: treat IM version as metadata"
```

---

### Task 2: Add DBServer Metadata Write Proxy

**Files:**
- Modify: `LimeIME-iOS/Shared/Database/DBServer.swift`
- Test: `LimeIME-iOS/LimeTests/DBServerTest.swift`

- [ ] **Step 1: Write the failing DBServer test**

Add this test in `LimeIME-iOS/LimeTests/DBServerTest.swift` near other IM config proxy tests:

```swift
func testDBServerSetImConfigPersistsMetadata() throws {
    let db = try makeLimeDB()
    let server = DBServer(_testDatasource: db)

    server.setImConfig(LIME.DB_TABLE_CUSTOM, "name", "Edited Name")
    server.setImConfig(LIME.DB_TABLE_CUSTOM, "version", "Edited Version")

    XCTAssertEqual(server.getImConfig(LIME.DB_TABLE_CUSTOM, "name"), "Edited Name")
    XCTAssertEqual(server.getImConfig(LIME.DB_TABLE_CUSTOM, "version"), "Edited Version")
}
```

If `DBServerTest` uses a different helper name than `makeLimeDB()`, use the existing local helper that returns a temporary `LimeDB`.

- [ ] **Step 2: Run the failing DBServer test**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' -only-testing:LimeTests/DBServerTest/testDBServerSetImConfigPersistsMetadata test
```

Expected before implementation: compile failure, `Value of type 'DBServer' has no member 'setImConfig'`.

- [ ] **Step 3: Add the DBServer proxy**

In `LimeIME-iOS/Shared/Database/DBServer.swift`, add this method next to `getImConfig(...)`:

```swift
func setImConfig(_ imCode: String, _ field: String, _ value: String) {
    datasource?.setImConfig(imCode, field, value)
}
```

- [ ] **Step 4: Run the DBServer test**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' -only-testing:LimeTests/DBServerTest/testDBServerSetImConfigPersistsMetadata test
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 5: Commit**

```bash
git add LimeIME-iOS/Shared/Database/DBServer.swift LimeIME-iOS/LimeTests/DBServerTest.swift
git commit -m "feat: expose IM metadata writes through DBServer"
```

---

### Task 3: Add Controller Method For Editing Metadata

**Files:**
- Modify: `LimeIME-iOS/LimeSettings/Controllers/ManageImController.swift`
- Test: `LimeIME-iOS/LimeTests/ManageImControllerTest.swift`

- [ ] **Step 1: Write successful update test**

Add this test to `LimeIME-iOS/LimeTests/ManageImControllerTest.swift`:

```swift
func testUpdateIMMetadataPersistsNameAndVersion() async throws {
    let db = try makeLimeDB()
    let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

    let result = await controller.updateIMMetadata(tableNick: LimeIME.LIME.DB_TABLE_CUSTOM,
                                                   name: "Edited Custom",
                                                   version: "Version 2026.05")

    guard case .success = result else {
        XCTFail("Expected metadata update to succeed, got \(result)")
        return
    }
    XCTAssertEqual(db.getImConfig(LimeIME.LIME.DB_TABLE_CUSTOM, "name"), "Edited Custom")
    XCTAssertEqual(db.getImConfig(LimeIME.LIME.DB_TABLE_CUSTOM, "version"), "Version 2026.05")
}
```

- [ ] **Step 2: Write validation test**

Add this test to the same file:

```swift
func testUpdateIMMetadataRejectsEmptyName() async throws {
    let db = try makeLimeDB()
    let controller = await LimeIME.ManageImController(dbServer: LimeIME.DBServer(_testDatasource: db))

    let result = await controller.updateIMMetadata(tableNick: LimeIME.LIME.DB_TABLE_CUSTOM,
                                                   name: "   ",
                                                   version: "Version 2026.05")

    guard case .failure = result else {
        XCTFail("Expected empty name validation failure, got \(result)")
        return
    }
    XCTAssertNil(db.getImConfig(LimeIME.LIME.DB_TABLE_CUSTOM, "name"))
    XCTAssertNil(db.getImConfig(LimeIME.LIME.DB_TABLE_CUSTOM, "version"))
}
```

- [ ] **Step 3: Run the failing controller tests**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' -only-testing:LimeTests/ManageImControllerTest/testUpdateIMMetadataPersistsNameAndVersion -only-testing:LimeTests/ManageImControllerTest/testUpdateIMMetadataRejectsEmptyName test
```

Expected before implementation: compile failure, `Value of type 'ManageImController' has no member 'updateIMMetadata'`.

- [ ] **Step 4: Implement the controller method**

In `LimeIME-iOS/LimeSettings/Controllers/ManageImController.swift`, add this method after `setKeyboard(forIM:keyboard:)`:

```swift
func updateIMMetadata(tableNick: String, name: String, version: String) async -> Result<Void, Error> {
    let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
    let trimmedVersion = version.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !tableNick.isEmpty else {
        return .failure(ControllerError.validation("輸入法代碼不能為空"))
    }
    guard !trimmedName.isEmpty else {
        return .failure(ControllerError.validation("名稱不能為空"))
    }

    let server = self.dbServer
    await Task.detached(priority: .userInitiated) {
        server.setImConfig(tableNick, "name", trimmedName)
        server.setImConfig(tableNick, "version", trimmedVersion)
    }.value

    ManageImController.markKeyboardCacheDirty()
    invalidate()
    return .success(())
}
```

- [ ] **Step 5: Run the controller tests**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' -only-testing:LimeTests/ManageImControllerTest/testUpdateIMMetadataPersistsNameAndVersion -only-testing:LimeTests/ManageImControllerTest/testUpdateIMMetadataRejectsEmptyName test
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 6: Commit**

```bash
git add LimeIME-iOS/LimeSettings/Controllers/ManageImController.swift LimeIME-iOS/LimeTests/ManageImControllerTest.swift
git commit -m "feat: add IM metadata edit controller"
```

---

### Task 4: Add Editing UI In IMDetailView

**Files:**
- Modify: `LimeIME-iOS/LimeSettings/Views/IMDetailView.swift`

- [ ] **Step 1: Add local editable state**

In `IMDetailView`, add state properties near existing `@State` properties:

```swift
@State private var displayName: String
@State private var displayVersion: String = "—"
@State private var editName: String
@State private var editVersion: String
@State private var showMetadataEditor = false
@State private var metadataError: String?
@State private var isSavingMetadata = false
```

Update `init(im:onRefresh:onDeleted:)` to initialize the new state:

```swift
init(im: IMRow, onRefresh: (() -> Void)? = nil, onDeleted: (() -> Void)? = nil) {
    self.im = im
    self.onRefresh = onRefresh
    self.onDeleted = onDeleted
    _displayName = State(initialValue: im.label)
    _editName = State(initialValue: im.label)
    _editVersion = State(initialValue: "")
}
```

- [ ] **Step 2: Add metadata loading helper**

Add this helper inside `IMDetailView`:

```swift
private func refreshMetadataFields() {
    let version = mappingVersion
    displayName = im.label
    displayVersion = version
    editName = displayName
    editVersion = version == "—" ? "" : version
}
```

- [ ] **Step 3: Replace read-only name/version rows with editable row**

In the `Section(header: Text("輸入法資訊"))`, replace:

```swift
LabeledContent("名稱", value: im.label)
if im.tableNick != "related" {
    LabeledContent("版本", value: mappingVersion)
}
```

with:

```swift
if im.tableNick == "related" {
    LabeledContent("名稱", value: displayName)
} else {
    Button {
        metadataError = nil
        editName = displayName
        editVersion = displayVersion == "—" ? "" : displayVersion
        showMetadataEditor = true
    } label: {
        VStack(alignment: .leading, spacing: 6) {
            LabeledContent("名稱", value: displayName)
            LabeledContent("版本", value: displayVersion)
        }
    }
}
```

- [ ] **Step 4: Load metadata during task**

At the beginning of the existing `.task { ... }` block, add:

```swift
refreshMetadataFields()
```

- [ ] **Step 5: Add the editor sheet**

Add this modifier to the view chain near the existing `.sheet(isPresented: $showShareSheet, ...)`:

```swift
.sheet(isPresented: $showMetadataEditor) {
    NavigationStack {
        Form {
            Section(header: Text("輸入法資訊")) {
                TextField("名稱", text: $editName)
                TextField("版本", text: $editVersion)
            }
            if let metadataError {
                Section {
                    Text(metadataError)
                        .foregroundColor(.red)
                }
            }
        }
        .navigationTitle("編輯輸入法資訊")
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("取消") { showMetadataEditor = false }
                    .disabled(isSavingMetadata)
            }
            ToolbarItem(placement: .confirmationAction) {
                Button {
                    saveMetadata()
                } label: {
                    if isSavingMetadata {
                        ProgressView()
                    } else {
                        Text("儲存")
                    }
                }
                .disabled(isSavingMetadata)
            }
        }
    }
}
```

- [ ] **Step 6: Add save helper**

Add this helper inside `IMDetailView`:

```swift
private func saveMetadata() {
    metadataError = nil
    isSavingMetadata = true
    Task {
        let result = await manageImController.updateIMMetadata(tableNick: im.tableNick,
                                                               name: editName,
                                                               version: editVersion)
        await MainActor.run {
            isSavingMetadata = false
            switch result {
            case .success:
                displayName = editName.trimmingCharacters(in: .whitespacesAndNewlines)
                let trimmedVersion = editVersion.trimmingCharacters(in: .whitespacesAndNewlines)
                displayVersion = trimmedVersion.isEmpty ? "—" : trimmedVersion
                showMetadataEditor = false
                onRefresh?()
            case .failure(let error):
                metadataError = error.localizedDescription
            }
        }
    }
}
```

- [ ] **Step 7: Build the app**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 8: Commit**

```bash
git add LimeIME-iOS/LimeSettings/Views/IMDetailView.swift
git commit -m "feat: edit IM name and version in settings"
```

---

### Task 5: Verify Export Uses Edited Values

**Files:**
- Test: `LimeIME-iOS/LimeTests/LimeDBTest.swift`

- [ ] **Step 1: Add integration-style export test**

Add this test near the existing export metadata tests in `LimeIME-iOS/LimeTests/LimeDBTest.swift`:

```swift
func testExportTxtTableUsesEditedNameAndVersionMetadata() throws {
    let db = try makeLimeDB()
    db.setTableName(LIME.DB_TABLE_CUSTOM)
    db.addOrUpdateMappingRecord("aa", "測")
    db.setImConfig(LIME.DB_TABLE_CUSTOM, "name", "Edited Friendly Name")
    db.setImConfig(LIME.DB_TABLE_CUSTOM, "version", "Edited Version 2026")

    let exportURL = FileManager.default.temporaryDirectory
        .appendingPathComponent(UUID().uuidString + ".lime")
    defer { try? FileManager.default.removeItem(at: exportURL) }

    let configs = db.getImConfigList(LIME.DB_TABLE_CUSTOM, nil)
    XCTAssertTrue(db.exportTxtTable(LIME.DB_TABLE_CUSTOM, targetFile: exportURL, imConfig: configs))

    let output = try String(contentsOf: exportURL, encoding: .utf8)
    XCTAssertTrue(output.contains("@version@|Edited Version 2026"))
    XCTAssertTrue(output.contains("@cname@|Edited Friendly Name"))
}
```

- [ ] **Step 2: Run export test**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' -only-testing:LimeTests/LimeDBTest/testExportTxtTableUsesEditedNameAndVersionMetadata test
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 3: Commit**

```bash
git add LimeIME-iOS/LimeTests/LimeDBTest.swift
git commit -m "test: verify edited IM metadata exports"
```

---

### Task 6: Final Verification

**Files:**
- No additional file edits expected.

- [ ] **Step 1: Run focused tests**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' -only-testing:LimeTests/LimeDBTest/testGetAllImConfigsDoesNotUseVersionAsSeedRow -only-testing:LimeTests/LimeDBTest/testExportTxtTableUsesEditedNameAndVersionMetadata -only-testing:LimeTests/DBServerTest/testDBServerSetImConfigPersistsMetadata -only-testing:LimeTests/ManageImControllerTest/testUpdateIMMetadataPersistsNameAndVersion -only-testing:LimeTests/ManageImControllerTest/testUpdateIMMetadataRejectsEmptyName test
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 2: Run full iOS build**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Manual simulator check**

Run the Settings app in Simulator, then verify:

1. Open `輸入法`.
2. Open an installed IM detail page.
3. Tap the `輸入法資訊` name/version row.
4. Edit `名稱` and `版本`.
5. Tap `儲存`.
6. Confirm the detail page updates immediately.
7. Navigate back to `管理輸入法`.
8. Confirm the list label uses the edited name.
9. Export the IM as `.lime`.
10. Confirm the exported text contains `@cname@|<edited name>` and `@version@|<edited version>`.

- [ ] **Step 4: Commit final verification note if needed**

If manual verification requires small test or UI text adjustments, commit only those edits:

```bash
git add LimeIME-iOS/LimeSettings LimeIME-iOS/LimeTests
git commit -m "fix: polish IM metadata editing"
```

---

## Self-Review

Spec coverage:

- User can edit IM name: covered by Task 4.
- User can edit version name: covered by Task 4.
- Modified values are written into the `im` table: covered by Tasks 2 and 3.
- Export uses modified values: covered by Task 5.
- Android behavior checked: recorded in Context; no Android code change planned because Android has the storage API but no current editable UI.
- iOS display-list stability: covered by Task 1.

Placeholder scan:

- No `TBD`, `TODO`, or unspecified "add tests" steps remain.
- Each code-changing step includes concrete code.
- Each verification step includes a concrete command and expected result.

Type consistency:

- `DBServer.setImConfig(...)` delegates to `LimeDBProtocol.setImConfig(...)`, which already exists.
- `ManageImController.updateIMMetadata(...)` returns `Result<Void, Error>`, matching existing controller patterns.
- `IMDetailView.saveMetadata()` consumes that result and updates local display state.

