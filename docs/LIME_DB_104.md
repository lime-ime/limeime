# LIME DB 104 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade LIME's main database schema to version 104 so both fresh installs and existing users have an empty `cj4` mapping table before installing the downloadable 哈哈倉頡 `.limedb`.

**Architecture:** DB 104 extends the DB 103 schema-only seed contract. Bundled `lime.db` remains schema-only for user-facing IM data: no installed IM metadata and no mapping rows. DB 104 adds only the `cj4` table schema and its code index. 哈哈倉頡 reuses the existing Cangjie keyboard row `keyboard.code='cj'`; the actual IM metadata and mappings are delivered later through `Database/cj4.limedb`.

**Tech Stack:** Android Java + SQLite raw resource, iOS Swift + GRDB, bundled SQLite seeds, LIME `.limedb` catalog download/install flow.

---

## Current Status

- Android `DATABASE_VERSION` and iOS `CURRENT_DB_VERSION` are bumped to `104`.
- Android and iOS open/upgrade paths run idempotent DB 104 repair for the `cj4` schema.
- Android table-name allowlists, iOS table-name allowlists, search tests, and catalog entries include `cj4`.
- `Database/lime.db` and `LimeStudio/app/src/main/res/raw/lime.db` are upgraded to user_version `104` with an empty `cj4` table and no duplicate `cj4` keyboard row.
- The downloadable data artifact `Database/cj4.limedb` is generated from `.Codex/txt/cj4_haha_20260523_162540.lime` through the Android emulator import/export path.
- The generated artifact is round-trip verified through Android `.limedb` import and is 612,574 bytes after adding Cangjie `imkeys` / `imkeynames` metadata, shown in catalogs as `598 KB`.
- Pending: after commit/push/merge, verify the final GitHub raw URL used by Android and iOS catalogs.

## Decisions

- Bump main DB version from `103` to `104` on Android and iOS.
- Existing users must upgrade in place from any DB version `< 104`.
- Fresh installs must receive a bundled seed DB with `PRAGMA user_version = 104`.
- Add a new empty mapping table named `cj4`.
- Add a `cj4_idx_code` index on `cj4(code)`.
- Reuse the existing `keyboard.code='cj'` row because the source `%keyname` mapping matches built-in Cangjie.
- Do not add any `im` row for `cj4` to bundled `lime.db`.
- Do not prefill `cj4` mapping rows in bundled `lime.db`.
- The downloadable `Database/cj4.limedb` installs 哈哈倉頡 metadata and mappings.
- Do not add a `keyboard.code='cj4'` row and do not add new XML/JSON keyboard layouts for the first release.

## Why DB 104 Is Required

The bundled raw DB is copied only when a user starts from scratch or performs a factory reset. Existing users keep their app-private `lime.db`; replacing the raw resource does not modify their database.

Because the catalog import target will be `cj4`, existing users must receive an in-place migration before they download/import 哈哈倉頡. Otherwise the `.limedb` import flow may reject `cj4` as invalid or fail because the table does not exist.

## Runtime Migration Order

DB 104 must not be implemented as a standalone upgrade path that assumes DB 103 is already complete. Android and iOS should use one cumulative repair path on every production database open, after restore, and after factory reset.

Required algorithm:

```text
ensureCurrentDatabase()
  read current DB version

  if version < 102:
    run existing 102 migration

  if version < 103 or emoji schema is missing:
    run DB 103 schema repair
    run emoji data refresh if emoji/version is missing or old

  if version < 104 or cj4 schema is missing:
    run DB 104 schema repair

  set database user_version/version to 104
```

This means a user upgrading from any version below 103 must be brought directly to 104 during the same app launch/open. The 103 and 104 work is merged into one cumulative migration pass, but the implementation should keep the repair helpers separate and ordered so each schema version remains understandable.

The `<102` path is legacy LIME 3-era compatibility. Keep the existing migration behavior so a very old database can still upgrade directly to 104, but do not expand this feature into a broad pre-102 compatibility project. DB 104 work should focus on preserving the existing `<102` behavior while making 103-to-104 and schema-repair paths reliable.

The version check alone is not enough:

- A restored DB may claim version `103` but still be missing emoji schema.
- A restored DB may claim version `104` but still be missing `cj4`.
- Therefore 103 and 104 repair helpers must check actual schema objects and be idempotent.

## Schema Change

### `cj4` Mapping Table

`cj4` mirrors the existing `cj` table shape:

```sql
CREATE TABLE IF NOT EXISTS cj4 (
    _id INTEGER primary key autoincrement,
    code text,
    code3r text,
    word text,
    related text,
    score integer,
    'basescore' type integer
);

CREATE INDEX IF NOT EXISTS cj4_idx_code ON cj4 (code);
```

Migration requirements:

- `cj4` must remain empty after schema migration.
- The migration must preserve all existing user data.
- The migration must be idempotent.
- The migration must not add `im` rows.
- The migration must work on restored old databases as well as normal app upgrades.

### Keyboard Resolution

Current DB 103 `keyboard` schema already contains the Cangjie row:

```sql
CREATE TABLE IF NOT EXISTS "keyboard"(
    [_id] INTEGER PRIMARY KEY AUTOINCREMENT,
    [code] text,
    [name] text,
    [desc] text,
    [type] text,
    [image] TEXT,
    [imkb] TEXT,
    [imshiftkb] TEXT,
    [engkb] TEXT,
    [engshiftkb] TEXT,
    [symbolkb] TEXT,
    [symbolshiftkb] TEXT,
    [defaultkb] TEXT,
    [defaultshiftkb] TEXT,
    [extendedkb] TEXT,
    [extendedshiftkb] TEXT,
    [disable] boolean
);

CREATE UNIQUE INDEX [keyboard_code_idx] ON [keyboard] ([code]);
```

DB 104 must not insert a new keyboard row. It must also delete any stale pre-release `keyboard.code='cj4'` row if one exists. `cj4` import/export/catalog metadata should store `im.keyboard = 'cj'` so it resolves to the existing row:

```text
code=cj
name=倉頡
imkb=lime_cj
imshiftkb=lime_cj_shift
extendedkb=lime_cj_number
extendedshiftkb=lime_cj_number_shift
```

If a future release needs a dedicated four-code Cangjie keyboard layout, it can add a separate keyboard row in a later schema version. DB 104 intentionally avoids that because it would create a duplicate keyboard picker entry with identical key names.

## Android Implementation

### Task 1: Add DB Constants And Table Validation

**Files:**

- Modify: `LimeStudio/app/src/main/java/net/toload/main/hd/global/LIME.java`
- Modify: `LimeStudio/app/src/main/java/net/toload/main/hd/limedb/LimeDB.java`

- [x] Add constants:

```java
public static final String DB_TABLE_CJ4 = "cj4";
public static final String IM_CJ4 = "cj4";
```

- [x] Add `LIME.DB_TABLE_CJ4` to `LimeDB.isValidTableName(String tableName)`.

- [x] Add `cj4` to any Android table lists used by backup, restore, sharing, import, export, and install flows.

### Task 2: Add Android DB 104 Migration

**Files:**

- Modify: `LimeStudio/app/src/main/java/net/toload/main/hd/limedb/LimeDB.java`
- Test: Android DB migration/instrumentation tests.

- [x] Change `DATABASE_VERSION` from `103` to `104`.

- [x] Make Android runtime migration cumulative. The production DB open/repair path must run the existing 102 and 103 repairs first, then run 104 repair, then set the DB version to 104.

- [x] Preserve the existing Android `<102` migration behavior. Do not remove it, but do not broaden it unless a focused test exposes a real blocker.

- [x] In `onUpgrade(SQLiteDatabase dbin, int oldVersion, int newVersion)` or the shared `ensureCurrentDatabase()` method, use this order:

```java
if (oldVersion < 102) {
    upgradeTo102(dbin);
}

if (oldVersion < 103 || isEmojiSchemaMissing(dbin)) {
    ensureEmojiSchema(dbin);
    refreshEmojiDataIfNeeded(dbin);
}

if (oldVersion < 104) {
    ensureCj4Schema(dbin);
}
```

- [x] Also call `ensureCj4Schema(dbin)` when DB version is already `104` but either `cj4` or `cj4_idx_code` is missing. This handles restored or damaged databases.

- [x] Implement `ensureCj4Schema(SQLiteDatabase db)`:

```java
private void ensureCj4Schema(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE IF NOT EXISTS " + LIME.DB_TABLE_CJ4 + " (" +
            "_id INTEGER primary key autoincrement, " +
            "code text, " +
            "code3r text, " +
            "word text, " +
            "related text, " +
            "score integer, " +
            "'basescore' type integer)");
    db.execSQL("CREATE INDEX IF NOT EXISTS cj4_idx_code ON " + LIME.DB_TABLE_CJ4 + " (code)");
}
```

- [x] Make sure the migration does not insert any `im` rows, does not insert any `cj4` mapping rows, and removes any stale `keyboard.code='cj4'` row.

- [x] Add Android migration tests for:
  - DB `< 102` can still upgrade all the way to 104 in one production open if an existing fixture is available.
  - DB `103` without `cj4` upgrades to 104.
  - DB `104` missing `cj4` repairs `cj4` without changing user data.
  - DB `104` with a stale duplicate `keyboard.code='cj4'` deletes that row; existing `keyboard.code='cj'` remains available.

### Task 3: Upgrade Android Raw Seed

**Files:**

- Modify: `LimeStudio/app/src/main/res/raw/lime.db`

- [x] Apply the same schema changes to the Android raw seed.

- [x] Set `PRAGMA user_version = 104`.

- [x] Verify:

```bash
sqlite3 LimeStudio/app/src/main/res/raw/lime.db "PRAGMA integrity_check;"
sqlite3 LimeStudio/app/src/main/res/raw/lime.db "PRAGMA user_version;"
sqlite3 LimeStudio/app/src/main/res/raw/lime.db "SELECT COUNT(*) FROM im;"
sqlite3 LimeStudio/app/src/main/res/raw/lime.db "SELECT COUNT(*) FROM cj4;"
sqlite3 LimeStudio/app/src/main/res/raw/lime.db "SELECT COUNT(*) FROM keyboard WHERE code='cj4';"
sqlite3 LimeStudio/app/src/main/res/raw/lime.db "SELECT code,name,imkb,imshiftkb,extendedkb,extendedshiftkb FROM keyboard WHERE code='cj';"
```

Expected:

```text
ok
104
0
0
cj|倉頡|lime_cj|lime_cj_shift|lime_cj_number|lime_cj_number_shift
```

## iOS Implementation

### Task 4: Add iOS Table Validation And Keyboard Awareness

**Files:**

- Modify: `LimeIME-iOS/Shared/Database/LimeDB.swift`
- Modify: `LimeIME-iOS/Shared/Database/DBServer.swift` if seed/restore lists need explicit entries.
- Modify: `LimeIME-iOS/LimeSettings/IMCatalog.swift`

- [x] Add `cj4` to `isValidTableName(_:)`.

- [x] Add `cj4` to user mapping table lists used by backup, restore, install, import, export, and table management.

- [x] Treat `cj4` as Cangjie-family where code has hardcoded Cangjie key behavior, for example existing switch/case groups containing `cj`, `scj`, `cj5`, and `ecj`.

- [x] Use keyboard id `cj` in the catalog variant.

### Task 5: Add iOS DB 104 Migration

**Files:**

- Modify: `LimeIME-iOS/Shared/Database/LimeDB.swift`
- Test: `LimeIME-iOS/LimeTests/LimeDBTest.swift` or `DBServerTest.swift`.

- [x] Change `CURRENT_DB_VERSION` from `103` to `104`.

- [x] Make iOS runtime migration cumulative. The production `LimeDB(path:)` open/repair path must run the existing 102 and 103 repairs first, then run 104 repair, then set `PRAGMA user_version = 104`.

- [x] Preserve the existing iOS `<102` migration behavior. Do not remove it, but do not broaden it unless a focused test exposes a real blocker.

- [x] In the iOS migration path, use this order:

```swift
if version < 102 {
    migrateTo102()
}

if version < 103 || emojiSchemaMissing() {
    ensureEmojiSchema()
    refreshEmojiDataIfNeeded()
}

if version < 104 || cj4SchemaMissing() {
    ensureCj4Schema()
}

setUserVersion(104)
```

- [x] Implement an idempotent `ensureCj4Schema` method:

```sql
CREATE TABLE IF NOT EXISTS cj4 (
    _id INTEGER primary key autoincrement,
    code text,
    code3r text,
    word text,
    related text,
    score integer,
    'basescore' type integer
);

CREATE INDEX IF NOT EXISTS cj4_idx_code ON cj4 (code);
```

- [x] Do not insert `im` rows, do not insert `cj4` mappings, and remove any stale `keyboard.code='cj4'` row.

- [x] Add iOS migration tests for:
  - DB `< 102` can still upgrade all the way to 104 in one production open if an existing fixture is available.
  - DB `103` without `cj4` upgrades to 104.
  - DB `104` missing `cj4` repairs `cj4` without changing user data.
  - DB `104` with a stale duplicate `keyboard.code='cj4'` row deletes it and still resolves Cangjie through `keyboard.code='cj'`.

### Task 6: Copy Shared Seed

**Files:**

- Read source: `LimeStudio/app/src/main/res/raw/lime.db`
- Modify output: `Database/lime.db`

- [x] After the Android raw seed satisfies DB 104, copy it to the shared seed:

```bash
cp LimeStudio/app/src/main/res/raw/lime.db Database/lime.db
```

- [x] Verify byte identity:

```bash
shasum -a 256 LimeStudio/app/src/main/res/raw/lime.db Database/lime.db
```

- [x] Verify shared seed:

```bash
sqlite3 Database/lime.db "PRAGMA integrity_check;"
sqlite3 Database/lime.db "PRAGMA user_version;"
sqlite3 Database/lime.db "SELECT COUNT(*) FROM im;"
sqlite3 Database/lime.db "SELECT COUNT(*) FROM cj4;"
sqlite3 Database/lime.db "SELECT COUNT(*) FROM keyboard WHERE code='cj4';"
sqlite3 Database/lime.db "SELECT code,name,imkb,imshiftkb,extendedkb,extendedshiftkb FROM keyboard WHERE code='cj';"
```

Expected:

```text
ok
104
0
0
cj|倉頡|lime_cj|lime_cj_shift|lime_cj_number|lime_cj_number_shift
```

## Catalog And `.limedb` Relationship

DB 104 only prepares the target table. It does not install 哈哈倉頡.

The actual downloadable IM artifact should be:

```text
Database/cj4.limedb
```

That `.limedb` should contain:

- A populated `custom` or `cj4` source table depending on the existing `.limedb` export/import convention.
- `im` metadata for `cj4`.
- Display name: `哈哈倉頡`.
- Version: `20260523_162540`.
- Keyboard: `cj`.
- Selection keys: `123456789`.
- End keys: `abcdefghijklmnopqrstuvwxyz`.
- Space style: `0`.

Both Android and iOS catalogs should link to the committed GitHub raw URL for `Database/cj4.limedb`.

## Verification Matrix

Fresh install seed:

- [x] Android raw `lime.db` has `PRAGMA user_version = 104`.
- [x] Shared `Database/lime.db` has `PRAGMA user_version = 104`.
- [x] Both seeds have empty `im`.
- [x] Both seeds have empty `cj4`.
- [x] Both seeds have `cj4_idx_code`.
- [x] Both seeds do not have duplicate `keyboard.code='cj4'` rows.
- [x] Both seeds retain the existing `keyboard.code='cj'` row.

Existing user upgrade:

- [x] Existing `<102` migration behavior is preserved so a very old DB can still reach DB 104 in one pass.
- [x] A DB 103 file without `cj4` opens through Android production DB initialization and becomes DB 104.
- [x] A DB 103 file without `cj4` opens through iOS production DB initialization and becomes DB 104.
- [x] A DB 104 file missing `cj4` repairs `cj4` on Android and iOS.
- [x] A DB 104 file with a stale `keyboard.code='cj4'` row removes it because `cj4` uses `keyboard.code='cj'`.
- [x] Existing `im` rows survive.
- [x] Existing user mapping rows survive.
- [x] Emoji tables/data survive.
- [x] `cj4` exists and is empty after migration.
- [x] `keyboard.code='cj4'` does not exist after migration.

Download/install:

- [x] Android can install `Database/cj4.limedb` into table `cj4`.
- [x] iOS can install `Database/cj4.limedb` into table `cj4` during branch/raw URL verification.
- [x] After install, `im` metadata for `cj4` exists.
- [x] The selected keyboard resolves through `keyboard.code='cj'`.
- [ ] Typing sample Cangjie codes returns 哈哈倉頡 candidates. Reporter should do final candidate-order UX confirmation.

## Tests To Update

The DB 104 change affects tests that hardcode DB 103, schema-only seed assumptions, valid table allowlists, keyboard catalog contents, and IM catalog variants.

### Android Tests

Update or replace `LimeStudio/app/src/androidTest/java/net/toload/main/hd/LimeDB103IntegrationTest.java`:

- Rename to `LimeDB104IntegrationTest` or keep the class name only if renaming is too disruptive.
- Change expected DB version from `103` to `104`.
- Change `freshInstallCopies103SeedAndRefreshesEmojiData` to assert:
  - `PRAGMA user_version = 104`
  - `cj4` table exists
  - `SELECT COUNT(*) FROM cj4 = 0`
  - `keyboard.code='cj4'` does not exist
  - `keyboard.code='cj'` exists
  - bundled seed `im` remains schema-only except emoji rows after runtime refresh
- Change `openingVersion102DatabaseAddsEmojiSchemaAndData` to assert the DB lands on 104 in one open, while preserving 102/103 behavior.
- Change `openingVersion103DatabaseRepairsMissingEmojiSchema` to assert both emoji repair and `cj4` repair happen, then version is 104.
- Add a case for a DB that claims version `104` but is missing `cj4`; opening it must repair `cj4`.
- Add a case for a DB that claims version `104` and has stale `keyboard.code='cj4'`; opening it must remove that row and keep using `keyboard.code='cj'`.
- Update restore and factory reset tests to expect version 104 and `cj4`/keyboard schema.
- Review assertions that say the bundled seed must keep core IM rows. DB 103 documentation says the seed should be schema-only for user-facing IM data; DB 104 should preserve that discipline. If runtime emoji refresh adds `im code='emoji'`, assert emoji rows specifically, not installed IM rows.

Update `LimeStudio/app/src/androidTest/java/net/toload/main/hd/LimeDBTest.java`:

- Add `LIME.DB_TABLE_CJ4` to valid table-name assertions.
- Add `cj4_user` to valid backup-table suffix assertions.
- Keep `4cj` invalid.
- Add or update keyboard config assertions so `getKeyboardConfig("cj4")` is absent and `getKeyboardConfig("cj")` returns the Cangjie-layout row.
- If any test enumerates all valid mapping tables, add `cj4`.

Review Android controller/catalog tests:

- `IntegrationTestSearchServerDBServer.java`: add coverage that an IM row for table `cj4` uses keyboard `cj` and resolves to `keyboard.code='cj'`.
- `ManageImControllerTest.java`: update keyboard-list expectations only if they assert exact counts or exact keyboard codes.
- `SetupImControllerFlowsTest.java`: add `cj4` only if it asserts valid/invalid import table choices.
- Catalog/install UI tests should assert the new `四碼倉頡` family and `哈哈倉頡` variant once catalog code is added.

### iOS Tests

Update `LimeIME-iOS/LimeTests/LimeDBTest.swift`:

- Add `cj4` to the valid table-name list in `testIsValidTableNameValidTables`.
- Add `cj4_user` to backup-table suffix validation if the test covers `_user` backup tables.
- Keep `4cj` invalid.
- Update DB 103 integrated tests under `MARK: - 36. DB 103 integrated seed / upgrade / restore paths`:
  - Rename the section/functions to DB 104, or keep names only if a rename is too noisy.
  - Change expected database version from `103` to `104`.
  - Assert `cj4` table exists and is empty.
  - Assert `keyboard.code='cj4'` does not exist and `keyboard.code='cj'` exists.
  - Assert version `102` or `103` fixture opens to version 104 in one pass.
  - Add repair cases for version 104 DBs missing `cj4`.
- Update helper names such as `makeDB103SeedVariant` / `rawDB103Stats` if their names become misleading.

Update iOS SearchServer/controller/catalog tests:

- `SearchServerTest.swift`: add `cj4` to built-in valid table coverage if the test lists built-ins.
- `LimeDBTest.swift` keyboard config tests: assert `getKeyboardConfig("cj4")` is nil and `getKeyboardConfig("cj")` returns Cangjie layouts.
- `SetupImControllerTest.swift` / install catalog tests: add expectations for `四碼倉頡` and `哈哈倉頡` after `IMCatalog` changes.
- `DBServerTest.swift`: add `cj4` constants/coverage only where backup/restore/import tests enumerate built-in mapping tables.

### Test Priority

Primary DB 104 acceptance tests:

1. DB 103 without `cj4` migrates to 104 and creates empty `cj4`.
2. DB 104 missing `cj4` repairs `cj4`.
3. DB 104 with stale `keyboard.code='cj4'` removes it and keeps using existing `keyboard.code='cj'`.
4. Fresh seed is 104 and schema-only for `cj4`.
5. `cj4` is valid and `4cj` is invalid.

Legacy coverage:

- Preserve `<102` one-pass upgrade to 104, but treat it as a smoke test if a fixture is easy to maintain. Do not expand pre-102 compatibility beyond existing behavior for this feature.

## Acceptance Criteria

- DB version is 104 on both platform seeds.
- Existing users migrate to 104 in place.
- `cj4` table schema exactly matches this document.
- `cj4_idx_code` exists.
- `keyboard.code='cj4'` does not exist; `cj4` points to existing `keyboard.code='cj'`.
- Bundled seed `im` remains empty.
- Bundled seed `cj4` remains empty.
- Downloadable `Database/cj4.limedb` installs 哈哈倉頡 data and metadata.
