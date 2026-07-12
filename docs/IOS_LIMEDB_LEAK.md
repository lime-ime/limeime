# iOS LimeDB Access Leak

Status: implemented. The original keyboard and preference-layer leaks have been routed through `DBServer` and `SearchServer`.

## Rule

Production UI and controller code must not call `LimeDB` directly.

- DB management, import, export, backup, restore, and bootstrap operations go through `DBServer`.
- Query, search, candidate, IM config, keyboard config, emoji lookup, and learning operations go through `SearchServer`.
- `LimeDB` remains the SQL implementation detail behind `DBServer` and `SearchServer`.

This mirrors the Android architecture where `LIMEService.java` uses `SearchServer` for runtime IM/emoji queries and does not create or call `LimeDB` directly.

## Allowed Owners

These files are allowed to touch `LimeDB` directly:

- `LimeIME-iOS/Shared/Database/LimeDB.swift`
- `LimeIME-iOS/Shared/Database/DBServer.swift`
- `LimeIME-iOS/Shared/Search/SearchServer.swift`
- Tests and benchmarks that intentionally create isolated `LimeDB` fixtures.

## Original Production Leaks Addressed

### KeyboardViewController

File: `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`

The keyboard controller previously owned a direct database reference:

- `private var db: LimeDB?`

That reference has been removed. Keyboard database startup now goes through:

- `DBServer.shared.prepareKeyboardRuntimeDatabase()`

The returned runtime context provides:

- a prepared `SearchServer`
- activated IM configs
- the initial active IM
- initial IM capability flags

Direct DB setup and bootstrap leaks that were moved to `DBServer`:

- Creates `LimeDB(path:)` during `setupDatabase()`.
- Calls `importPhoneticIfNeeded(db:containerURL:)`.
- Calls `importRelatedIfNeeded(db:)`.
- Calls `limeDB.getAllImConfigs()`.
- Calls `buildFallbackIMList(db:)`.
- Creates `SearchServer(db: limeDB)` directly.

Direct DB management/import leaks that were moved to `DBServer`:

- `tableHasData("phonetic")`
- `importFromAttachedDB(sourcePath:tableName:)`
- `tableHasData("related")`
- `importDbRelated(_:)`
- fallback IM table detection via `tableHasData(_:)`

Direct IM config and metadata leaks that were moved to `SearchServer`:

- `limeDB.getKeyboardConfig(kbCode)`
- `limeDB.getImConfig("phonetic", "keyboard")`
- `db.detectIMCapabilities(tableName:)`
- `db?.imKeysForTable(activeIM)`
- repeated `imCapabilities(for:db:)` calls during IM restore/switching

Direct emoji query/search/learning leaks that were moved to `SearchServer`:

- `db?.loadRecentEmoji(limit:)`
- `db?.loadEmojiCategoryPages()`
- `db.loadEmojiCategoryPages()` in emoji prewarm
- `db?.searchEmoji(_:locale:limit:)`
- `db?.recordEmojiUsage(_:)`

UI type/constant leakage that was removed from `KeyboardViewController`:

- Uses `LimeDB.EMOJI_EN` when calling `SearchServer.injectEmoji(...)`.

### LIMEPreferenceManager

File: `LimeIME-iOS/Shared/Preferences/LIMEPreferenceManager.swift`

The direct DB overload was removed:

- `func syncIMActivatedState(db: LimeDB)`

Production and tests now use:

- `func syncIMActivatedState(dbServer: DBServer)`

### Protocol Type Coupling

File: `LimeIME-iOS/Shared/Database/LimeDBProtocol.swift`

`LimeDBProtocol` exposes concrete `LimeDB` nested types:

- `LimeDB.EmojiLocale`

This is less severe because the protocol is database-layer API, but it still leaks the concrete `LimeDB` type name into higher-level APIs. Prefer a standalone shared enum such as `EmojiLocale`.

## Impacted Tests And Fix Plan

The rule above does not ban direct `LimeDB` fixtures in tests. It bans production UI/controller code from depending on `LimeDB`. Tests should be moved to the same layer boundary as the behavior they validate.

### Tests That Should Stay Direct `LimeDB`

These tests intentionally validate SQL, schema, import/export, emoji table behavior, and low-level query semantics. Keep them direct and isolated:

- `LimeIME-iOS/LimeTests/LimeDBTest.swift`
- `LimeIME-iOS/LimeTests/DBServerTest.swift` cases that seed or inspect temp databases through `_datasourceForTesting`
- `LimeIME-iOS/LimeTests/EngineLatencyBenchmark.swift`
- `LimeIME-iOS/LimeTests/StrokeBenchmark.swift` when benchmarking the raw engine stack

Fix rule: keep direct `LimeDB(path:)` setup only inside fixture helpers such as `makeLimeDB()`. Do not copy this pattern into UI/controller tests.

### Tests That Should Move To `SearchServer`

Any test covering candidate lookup, IM metadata, keyboard config, related phrase lookup, emoji panel data, emoji search, or emoji usage should call `SearchServer` instead of `LimeDB` once wrappers exist.

Impacted areas:

- `LimeIME-iOS/LimeTests/SearchServerTest.swift`
- new or existing keyboard runtime tests that currently need `getKeyboardConfig`, `getImConfig`, `detectIMCapabilities`, `imKeysForTable`, `loadEmojiCategoryPages`, `loadRecentEmoji`, `searchEmoji`, or `recordEmojiUsage`

Fix rule:

```swift
let db = try LimeDB(path: tempURL.path)
let searchServer = SearchServer(db: db)

// Seed through LimeDB only when the test is constructing a fixture.
// Assert runtime behavior through SearchServer.
```

Add wrapper coverage before replacing controller calls:

- `SearchServer.getAllImKeyboardConfigList()` returns all active IM keyboard configs safely.
- `SearchServer.getKeyboard()` and `SearchServer.getKeyboardConfig(_:)` return keyboard config safely.
- `SearchServer.getImConfigList(_:_: )` and `SearchServer.getImConfig(_:_: )` return IM metadata safely.
- `SearchServer` returns IM capabilities and table keys safely for missing or invalid tables.
- `SearchServer.loadRecentEmoji(_:)`, `SearchServer.searchEmoji(_:locale:limit:)`, and `SearchServer.recordEmojiUsage(_:)` match the Android API names.
- `SearchServer.loadEmojiCategoryPages()` and `SearchServer.preloadEmojiCategoryPages()` follow the iOS-first category-page API. Android should add the same API shape; see [EMOJI_KEYBOARD.md](EMOJI_KEYBOARD.md).

### Tests That Should Move To `DBServer`

Any test covering keyboard startup, App Group database preparation, bundled DB copy, bundled phonetic/related import, or creating a `SearchServer` for the runtime database should call `DBServer`.

Impacted areas:

- `LimeIME-iOS/LimeTests/DBServerTest.swift`
- future `KeyboardViewController` startup tests
- `SetupImControllerTest.swift` and `ManageImControllerTest.swift` only when the behavior is file/database lifecycle rather than record search

Fix rule:

```swift
let db = try LimeDB(path: tempURL.path)
let dbServer = DBServer(_testDatasource: db)
let searchServer = try XCTUnwrap(dbServer.makeSearchServer())
```

Add DBServer coverage before changing `KeyboardViewController.setupDatabase()`:

- preparing the keyboard runtime database returns a usable `SearchServer`.
- bundled phonetic import is skipped when the table already has data.
- bundled related import is skipped when the table already has data.
- activated IM configs are loaded through the returned startup context.
- failures return safe defaults or typed errors without leaving the keyboard with a direct `LimeDB` fallback.

### `KeyboardViewControllerTest` Changes

`LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift` currently covers UI policies, JSON layout details, and emoji pagination. Those tests are not directly broken by removing `KeyboardViewController.db`.

When runtime database behavior is added to this test file, do not instantiate `LimeDB` in the controller path. Instead:

- inject a prepared `SearchServer` or a keyboard runtime context produced by `DBServer`;
- assert that the controller updates UI state from `SearchServer` results;
- keep layout and paginator tests independent from database setup.

### Architecture Guard Tests

After the refactor, add a small source-scanning XCTest or script-backed test that fails if production files reintroduce direct `LimeDB` usage outside the allowed owners.

The guard should:

- scan `LimeIME-iOS/LimeKeyboard` and `LimeIME-iOS/LimeSettings`;
- allow `LimeIME-iOS/Shared/Database/LimeDB.swift`, `DBServer.swift`, and `SearchServer.swift`;
- ignore `LimeIME-iOS/LimeTests`, `LimeIME-iOS/LimeUITests`, and benchmark fixtures;
- flag `LimeDB(`, `: LimeDB`, `LimeDB.`, and `LimeDB?` in production UI/controller files.

This makes the architectural rule executable and prevents the same leak from quietly returning.

## Completed Refactor

### Android SearchServer API Parity

Before adding a new iOS `SearchServer` wrapper, check `LimeStudio/app/src/main/java/org/limeime/SearchServer.java`. If Android already exposes the same operation, the iOS method name and contract should match Android as closely as Swift allows.

Existing Android APIs to mirror:

- `getAllImKeyboardConfigList()`
- `getImConfigList(String code, String configEntry)`
- `getImConfig(String imCode, String field)`
- `getKeyboard()`
- `getKeyboardConfig(String keyboard)`
- `emojiConvert(String code, int type)`
- `findEmojiForCandidate(String code, LimeDB.EmojiLocale locale, int limit)`
- `searchEmoji(String query, LimeDB.EmojiLocale locale, int limit)`
- `loadRecentEmoji(int limit)`
- `recordEmojiUsage(String value)`

Category-page browsing is the exception: it was designed and implemented on iOS first, so Android should sync to the iOS API shape documented in [EMOJI_KEYBOARD.md](EMOJI_KEYBOARD.md).

iOS-first category APIs:

- `loadEmojiCategoryPages()`
- `preloadEmojiCategoryPages()` or an equivalent category-page cache provider

iOS-only wrappers are allowed only when Android has no matching runtime API. Current examples:

- `detectIMCapabilities(tableName:)`, unless an Android `SearchServer` equivalent is added first
- `imKeysForTable(_:)`, unless an Android `SearchServer` equivalent is added first

### 1. Move Keyboard DB Bootstrap To DBServer

Done: `KeyboardViewController` no longer creates or imports `LimeDB`.

Added `DBServer.prepareKeyboardRuntimeDatabase()` for keyboard startup:

- Open/create the App Group runtime database.
- Copy bundled `lime.db` if missing.
- Import bundled phonetic data if the phonetic table has no rows.
- Import bundled related data if the related table has no rows.
- Return a `SearchServer` backed by the same datasource.
- Return activated IM configs.

The controller now calls one high-level method:

```swift
let context = DBServer.shared.prepareKeyboardRuntimeDatabase()
searchServer = context.searchServer
activatedIMs = context.activatedIMs
```

### 2. Move Runtime IM Queries To SearchServer

Done: IM metadata used by the keyboard goes through `SearchServer`.

Added or used `SearchServer` wrappers for:

- `getAllImKeyboardConfigList()`
- `getImConfigList(_:_: )`
- `getKeyboardConfig(_:)`
- `getImConfig(_:_: )`
- `detectIMCapabilities(tableName:)`
- `imKeysForTable(_:)`
- fallback IM list construction if still needed

`imCapabilities(for:db:)` was removed from `KeyboardViewController`.

### 3. Move Emoji Panel Data To SearchServer

Done: emoji category/recent/search/usage route through `SearchServer`.

Added `SearchServer` methods for:

- `emojiConvert(_:_: )`
- `findEmojiForCandidate(_:locale:limit:)`
- `searchEmoji(_:locale:limit:)`
- `loadRecentEmoji(_:)`
- `recordEmojiUsage(_:)`
- `loadEmojiCategoryPages()`
- `preloadEmojiCategoryPages()` or a cached category-page provider inside `SearchServer`

`KeyboardViewController` now keeps only UI-level fallback/presentation state. The DB-backed category cache and prewarm path are owned by `SearchServer`.

### 4. Remove KeyboardViewController.db

Done: the keyboard controller no longer retains a `LimeDB`.

Completed changes:

- Delete `private var db: LimeDB?`.
- Replace all `self.db` access with `searchServer` or `DBServer`.
- Keep only UI state in `KeyboardViewController`.

### 5. Remove Direct LimeDB Preference Overload

Done: preference sync uses the DB management layer.

- Removed `syncIMActivatedState(db: LimeDB)`.
- Tests now call `syncIMActivatedState(dbServer: DBServer(_testDatasource: db))`.

### 6. Move Emoji Constants Out Of LimeDB

Partial: UI/controller code no longer references `LimeDB.EMOJI_EN`. `SearchServer` still references `LimeDB.EMOJI_EN` and `LimeDB.EmojiLocale` internally because `LimeDBProtocol` still exposes the concrete nested type.

Remaining options:

- Move emoji locale/type constants into a shared model file.
- Or expose semantic `SearchServer` methods that hide the constants entirely.

Preferred direction:

```swift
searchServer.injectEnglishEmoji(...)
searchServer.searchEmoji(..., locale: .english)
```

This remaining type cleanup is lower priority than the production UI/controller leak because `SearchServer` is an allowed `LimeDB` owner.

## Verification

Fresh verification run:

- `xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' build 2>&1 | rg -n "\\*\\* BUILD|error:|Fatal error|warning:"`
  - Result: `** BUILD SUCCEEDED **`
  - Warnings: AppIntents metadata extraction skipped; no build errors.
- `xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:LimeTests/LIMEPreferenceManagerTest -only-testing:LimeTests/KeyboardViewControllerTest`
  - Result: `** TEST SUCCEEDED **`
  - Executed 54 tests, 0 failures.
- Production scan:
  - `LimeIME-iOS/LimeKeyboard`, `LimeIME-iOS/LimeSettings`, and `LimeIME-iOS/Shared/Preferences` no longer instantiate, store, or directly call `LimeDB`.

## Target Shape

Keyboard runtime:

```text
KeyboardViewController
  -> DBServer       for startup/import/database lifecycle
  -> SearchServer   for IM queries, candidates, emoji, learning
      -> LimeDB
```

Settings runtime:

```text
Settings Views / Controllers
  -> DBServer       for import/export/backup/restore/install
  -> SearchServer   for query/edit/search/config operations
      -> LimeDB
```

No production UI/controller should instantiate `LimeDB`, store `LimeDB`, or call `LimeDB` methods directly.
