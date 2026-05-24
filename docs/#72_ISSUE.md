# Issue #72: Add user-provided Haha Cangjie as a downloadable IM

## Problem statement

Issue #72 asks LIME to provide 哈哈倉頡 as an available input method table. The reporter describes Haha Cangjie as a four-code Cangjie method with shorter codes, high-frequency bubbling, duplicate-code avoidance, and phrase input support.

Issue URL: https://github.com/lime-ime/limeime/issues/72

Current labels: `enhancement`, `question`.

The maintainer direction in comment `4518137053` is that LIME should not treat this as a preinstalled/bundled IM. The project can follow the existing downloadable-table model: host a `.limedb` table on GitHub and add it to the downloadable IM list if source, license, compatibility, and table quality are acceptable.

## User-provided table files

Latest reporter attachments from comment `4526116904`:

- `haha_20260523_162540.txt`
- `haha_20260523_162540.lime.txt`

Local inspection copies:

- `.Codex/txt/issue72_haha_20260523_162540_cin.txt`
- `.Codex/txt/issue72_haha_20260523_162540_lime.txt`
- `.Codex/txt/cj4_haha_20260523_162540.lime`

Format findings:

- `haha_20260523_162540.txt` is named `.txt`, but the content is a CIN table.
  - It begins with comment/license metadata.
  - It includes `%gen_inp`, `%ename haha_cangjie`, `%cname 哈哈倉頡`, `%version 20260523_162540`, `%selkey 234567890`, `%keyname begin/end`, and `%chardef begin`.
  - It contains 33,075 lines.
- `haha_20260523_162540.lime.txt` is named `.lime.txt`, but the content is LIME text format.
  - It begins with `@format@|lime-text-v2`.
  - It includes `@version@|20260523_162540`, `@cname@|哈哈倉頡`, `@selkey@|123456789`, `@endkey@|abcdefghijklmnopqrstuvwxyz`, `@spacestyle@|0`, `%chardef begin`, and pipe-delimited `code|word|score|basescore` rows.
  - It contains 33,028 lines.
  - It contains 33,021 `%chardef` records.

The first file is the better source-of-truth for original CIN metadata, including `%keyname`. The second file is the planned source for LIME import testing because it is already LIME text v2 and carries explicit `@format@|lime-text-v2`, `@version@`, and `@cname@` metadata.

GitHub only allowed the reporter to upload the LIME text table with a `.txt` suffix. For local work, save the second attachment to `.Codex/txt/`, then copy/rename the working import artifact to a `.lime` filename before pushing it to the Android emulator.

Current local working import artifact:

- `.Codex/txt/cj4_haha_20260523_162540.lime`

Verified header:

```text
@format@|lime-text-v2
@version@|20260523_162540
@cname@|哈哈倉頡
@selkey@|123456789
@endkey@|abcdefghijklmnopqrstuvwxyz
@spacestyle@|0
%chardef begin
```

## License and source notes

The provided CIN file declares:

- License: `CC BY 4.0`
- Table author: `尹卂`
- Author email: `i@ejsoon.vip`
- Update URL: `https://ejsoon.vip/haha`

Reporter explanation:

- 哈哈倉頡 is based on 尹卂倉頡.
- 尹卂倉頡 is based on open-source Yahoo Kimo Cangjie.
- Yahoo Kimo Cangjie is based on open-source 中標倉頡（三代）.
- Reporter says this lineage avoids the copyright concerns associated with 大新倉頡-derived four-code Cangjie tables.

Before publishing, verify the upstream page still states the same license and that CC BY 4.0 attribution requirements can be satisfied in LIME's database/catalog documentation.

Completed before merge/release:

- Added acknowledgement/attribution for the new 哈哈倉頡 table in [LICENSE.md](../LICENSE.md), including table author `尹卂`, license `CC BY 4.0`, and source/update URL `https://ejsoon.vip/haha`.

## Likely product shape

Treat Haha Cangjie as a user-contributed downloadable IM, not a built-in default.

Approved category shape:

- Add a new downloadable IM entry for `哈哈倉頡`.
- Create a new public category/family: `四碼倉頡`.
- Use internal table name `cj4`.
- Avoid adding other four-code Cangjie tables until their source and license are independently verified.

Do not use `4cj` as the actual database table name. SQLite rejects unquoted identifiers that begin with a digit, and both Android and iOS currently interpolate allowlisted table names into SQL without identifier quoting in many paths. `cj4` follows the existing style of `cj`, `cj5`, and `array10` while avoiding a broad SQL quoting audit.

## Proposed solution

1. Validate the supplied table.
   - Use `haha_20260523_162540.lime.txt` as the planned import source.
   - Save it locally under `.Codex/txt/` and create a working `.lime` copy for emulator import, because the GitHub attachment is suffixed `.txt` only for upload compatibility.
   - Confirm the file has `@format@|lime-text-v2`, `@version@|20260523_162540`, and `@cname@|哈哈倉頡`.
   - Confirm record count, metadata, selection keys, ordering scores, phrase codes beginning with `z`, and punctuation codes beginning with `x`.
   - Keep the CIN attachment as reference material for license comments and `%keyname` metadata, but do not use it as the first import source unless the `.lime` file fails validation.

2. Produce a downloadable `.limedb`.
   - Import the `.lime` source into a new table code: `cj4`.
   - Preserve display name `哈哈倉頡`.
   - Preserve version `20260523_162540`.
   - Preserve selection/end/space metadata.
   - Assign the existing Cangjie keyboard layout (`cj`). The source `%keyname` mapping is identical to the built-in Cangjie key map, so do not create a duplicate `cj4` keyboard row.
   - Export/package as `.limedb`.
   - Commit the generated `.limedb` under the repo's `Database/` folder so the GitHub raw URL can be used by both catalogs.

3. Add catalog entries.
   - Android: update the downloadable IM list/catalog and any constants required by `ImInstallFragment`, `SetupImController`, and related cloud database metadata.
   - iOS: update `IMCatalog` / install UI equivalents so the table appears in the same downloadable list.
   - Both platforms should link to the committed `Database/` artifact through the GitHub raw URL.
   - Documentation: add attribution/license notes near the downloadable IM documentation or catalog metadata.

4. Expand database schema and code allowlists for `cj4`.
   - Follow the seed DB contract in [LIME_DB_103.md](LIME_DB_103.md).
   - Follow the explicit DB 104 schema plan in [LIME_DB_104.md](LIME_DB_104.md).
   - Bump the main DB schema version from 103 to 104. Existing users keep their app-private `lime.db`, so they need an in-place 104 migration that adds the `cj4` table before the downloadable catalog can import into it.
   - Add a real empty `cj4` mapping table to the bundled Android raw seed `LimeStudio/app/src/main/res/raw/lime.db`.
   - Reuse the existing `keyboard.code='cj'` row. Do not add `keyboard.code='cj4'`.
   - Copy the upgraded Android raw seed to the shared/iOS seed `Database/lime.db`, matching the DB 103 workflow.
   - Keep bundled seed `im` empty and keep bundled `cj4` empty. Do not preinstall Haha Cangjie metadata or mapping rows in `lime.db`.
   - Put `cj4` metadata and mapping rows in the generated downloadable `Database/cj4.limedb`, not in the schema-only seed.
   - Add Android runtime migration logic: when existing DB version is less than 104, create `cj4` schema/indexes idempotently and set DB version to 104 without changing user IM data.
   - Add iOS runtime migration logic: when existing DB `PRAGMA user_version` is less than 104, create `cj4` schema/indexes idempotently and set `user_version` to 104 without changing user IM data.
   - Add `cj4` constants to Android `LIME.java` and all Android table-name allowlists.
   - Add `cj4` to iOS `LimeDB.isValidTableName`, `LimeDB.userMappingTables`, `DBServer` seed/restore logic if needed, and `IMCatalog`.
   - Ensure backup/restore, text import, `.limedb` import/export, table clearing, record browsing, search, and user-learning backup paths accept `cj4`.

5. Verify import/runtime behavior.
   - Confirm Android can download/install the table.
   - Confirm iOS can download/install the table.
   - Confirm common single-character codes produce expected candidates.
   - Confirm phrase codes starting with `z` work.
   - Confirm duplicate-code candidate ordering matches the supplied scores.

## Explicit DB 104 schema change

DB version 104 adds one new mapping table, `cj4`, for four-code Cangjie tables. It should mirror the existing `cj` table schema so current mapping/import/search code can treat it like a Cangjie-family table without special row parsing.

Full DB 104 migration details, including the `keyboard` table row, are tracked in [LIME_DB_104.md](LIME_DB_104.md).

Current `cj` schema in `LimeStudio/app/src/main/res/raw/lime.db`:

```sql
CREATE TABLE cj (
    _id INTEGER primary key autoincrement,
    code text,
    code3r text,
    word text,
    related text,
    score integer,
    'basescore' type integer
);
CREATE INDEX [cj_idx_code] ON [cj] ([code]);
```

DB 104 must add this schema:

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

- The `cj4` table must be empty after migration.
- The migration must not insert `im` metadata rows.
- The migration must be idempotent and safe to run on DBs that already have `cj4`.
- The migration must remove any stale pre-release `keyboard.code='cj4'` row, because `cj4` reuses `keyboard.code='cj'`.
- The migration must preserve all existing user tables, mappings, `im` metadata, learning scores, related phrases, and emoji tables.
- Android and iOS must both run this migration for any existing user DB with version `< 104`.
- Fresh bundled seed DBs must include the empty `cj4` table and `cj4_idx_code` index with `PRAGMA user_version = 104`.
- The downloadable `Database/cj4.limedb`, not the bundled seed `lime.db`, is responsible for installing 哈哈倉頡 metadata and mapping rows.
- DB 104 must not add `keyboard.code='cj4'`; it should delete that stale row if present. `cj4` uses the existing `keyboard.code='cj'` row and Cangjie layouts: `lime_cj`, `lime_cj_shift`, `lime_cj_number`, and `lime_cj_number_shift`.

## Implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 哈哈倉頡 as downloadable IM `cj4` under the new public category `四碼倉頡`, using the reporter-provided LIME v2 text table as source and publishing a generated `.limedb` in `Database/`.

**Architecture:** Treat `cj4` as a first-class mapping table in bundled `lime.db` and in both platform allowlists/catalogs, extending the DB 103 schema-only seed contract with a DB 104 migration. Fresh installs get an empty `cj4` table from the bundled 104 seed; existing users get the same empty table through idempotent runtime migration from any DB version below 104. The `.lime` source is the conversion/import input, and the app catalogs should download the generated `Database/cj4.limedb` that carries the actual IM metadata and mapping rows.

**Tech stack:** Android Java/Kotlin-adjacent Java code with SQLite bundled DB, iOS Swift/GRDB bundled DB, LIME text v2 importer, `.limedb` export/import, GitHub raw database hosting.

### Current implementation status

- Implemented: DB 104 runtime migration on Android and iOS.
- Implemented: Android raw seed `LimeStudio/app/src/main/res/raw/lime.db` and shared seed `Database/lime.db` upgraded to `PRAGMA user_version = 104`.
- Implemented: both upgraded seeds contain an empty `cj4` table and `cj4_idx_code`; neither seed contains a duplicate `keyboard.code='cj4'` row.
- Implemented: stale pre-release `keyboard.code='cj4'` rows are removed by the DB 104 repair path, so existing tester/user DBs do not keep the duplicate keyboard picker entry.
- Implemented: Android and iOS catalogs include the `四碼倉頡` family and `哈哈倉頡` variant pointing to `Database/cj4.limedb`.
- Implemented: Android and iOS use the existing Cangjie keyboard id `cj` for `cj4`.
- Implemented: table validation/search tests include `cj4` as valid and `4cj` as invalid.
- Implemented: `Database/cj4.limedb` is generated from the local `.lime` artifact through the Android emulator import/export path.
- Verified: generated artifact size is 612,574 bytes after adding Cangjie `imkeys` / `imkeynames` metadata, shown in catalogs as `598 KB`.
- Verified: Android visual check shows `哈哈倉頡` installed with keyboard layout `倉頡輸入法鍵盤`, and the keyboard picker shows only the existing Cangjie keyboard entry with the selected-state radio indicator.
- Verified: iOS visual check shows the catalog category `四碼倉頡`, variant `哈哈倉頡`, `33,021 字`, and correct Chinese text. The displayed size is now `598 KB` after metadata patching.
- Fixed during iOS visual verification: `IMCatalog.swift` had been accidentally mojibake-encoded in this worktree; it is repaired so the only catalog diff is the new `cj4` family plus the required UTF-8 BOM.
- Remaining release-only check: after commit/push/merge, verify the final GitHub raw URL used by Android and iOS catalogs.
- Remaining public follow-up: ask the reporter to confirm candidate ordering and phrase/punctuation behavior with the generated `.limedb`.
- Skipped by maintainer direction for this pass: repeat iOS verification after the `isLimeDB` cleanup, and resolve/rerun the Android API 37 target-SDK compatibility assertion.

### Task 1: Prepare the local LIME source artifact

**Files:**

- Read: `.Codex/txt/issue72_haha_20260523_162540_lime.txt`
- Create: `.Codex/txt/haha_20260523_162540.lime`

- [x] Copy the downloaded reporter attachment to a `.lime` working filename:

```bash
cp .Codex/txt/issue72_haha_20260523_162540_lime.txt .Codex/txt/haha_20260523_162540.lime
```

- [x] Verify the LIME v2 metadata:

```bash
sed -n '1,8p' .Codex/txt/haha_20260523_162540.lime
```

Expected first lines:

```text
@format@|lime-text-v2
@version@|20260523_162540
@cname@|哈哈倉頡
@selkey@|123456789
@endkey@|abcdefghijklmnopqrstuvwxyz
@spacestyle@|0
%chardef begin
```

### Task 2: Add `cj4` to Android database constants and allowlists

**Files:**

- Modify: `LimeStudio/app/src/main/java/net/toload/main/hd/global/LIME.java`
- Modify: `LimeStudio/app/src/main/java/net/toload/main/hd/limedb/LimeDB.java`
- Test: Android table-name tests if present, otherwise add focused coverage near existing LimeDB tests.

- [x] Add constants:

```java
public static final String DB_TABLE_CJ4 = "cj4";
public static final String IM_CJ4 = "cj4";
```

- [x] Add `LIME.DB_TABLE_CJ4` to Android `LimeDB.isValidTableName(String tableName)`.

- [x] Add a failing test/assertion that `cj4` is valid and `4cj` is invalid.

- [x] Run the relevant Android test target.

### Task 3: Add `cj4` to iOS table validation and table lists

**Files:**

- Modify: `LimeIME-iOS/Shared/Database/LimeDB.swift`
- Modify: `LimeIME-iOS/Shared/Database/DBServer.swift` if seed/restore lists need explicit entries.
- Test: `LimeIME-iOS/LimeTests/LimeDBTest.swift` or existing validation tests.

- [x] Add `cj4` to `isValidTableName(_:)`.

- [x] Add `cj4` to `userMappingTables` / mapping-table lists used by backup, restore, import, and install flows.

- [x] Add a failing XCTest that `cj4` is valid and `4cj` is invalid.

- [x] Run the focused iOS build/test compile target.

### Task 4: Add DB 104 migration and expand bundled `lime.db` schema for `cj4`

**Files:**

- Modify: `LimeStudio/app/src/main/java/net/toload/main/hd/limedb/LimeDB.java`
- Modify: `LimeIME-iOS/Shared/Database/LimeDB.swift`
- Modify: `LimeStudio/app/src/main/res/raw/lime.db`
- Modify: `Database/lime.db`
- Create/update working SQL notes only under `.Codex/txt/`.
- Reference: `docs/LIME_DB_103.md`
- Reference: `docs/LIME_DB_104.md`

- [x] Read [LIME_DB_103.md](LIME_DB_103.md) before touching raw DB files. Reuse the same seed discipline, but bump the version to `104` for this feature.

- [x] Change Android `LimeDB.DATABASE_VERSION` from `103` to `104`.

- [x] Add Android `onUpgrade` handling for `oldVersion < 104`:

```java
if (oldVersion < 104) {
    ensureCj4Table(dbin);
}
```

- [x] Implement Android `ensureCj4Table(SQLiteDatabase db)` idempotently using the existing Cangjie mapping schema as the template. It must create the table and required indexes only if missing, and must not insert `im` metadata or mapping rows.

- [x] Ensure Android `cj4` import assigns the existing `cj` keyboard; do not add `keyboard.code='cj4'`.

Expected Android migration SQL:

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

- [x] Change iOS `CURRENT_DB_VERSION` from `103` to `104`.

- [x] Add iOS migration handling for `version < 104` in the current database migration path.

- [x] Implement iOS `ensureCj4Table` idempotently using the existing Cangjie mapping schema as the template. It must create the table and required indexes only if missing, and must not insert `im` metadata or mapping rows.

- [x] Ensure iOS catalog/install metadata assigns the existing `cj` keyboard; do not add `keyboard.code='cj4'`.

Expected iOS migration SQL:

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

- [x] Confirm Android raw seed is upgraded to DB 104:

```bash
sqlite3 LimeStudio/app/src/main/res/raw/lime.db "PRAGMA integrity_check; PRAGMA user_version;"
```

Expected:

```text
ok
104
```

- [x] Inspect the schema of `cj` / `cj5` and create empty `cj4` with the same mapping-table columns and indexes as the closest Cangjie table.

- [x] Verify `cj4` table schema exactly matches the DB 104 schema section above.

- [x] Do not add `cj4` rows to the bundled seed `im` table.

- [x] Do not prefill bundled seed `cj4` with mappings. The bundled seed stays schema-only for IMs.

- [x] Set `PRAGMA user_version = 104`.

- [x] Copy the upgraded Android raw seed to the shared/iOS seed, following the same seed-copy model documented in [LIME_DB_103.md](LIME_DB_103.md):

```bash
cp LimeStudio/app/src/main/res/raw/lime.db Database/lime.db
```

- [x] Verify the copied seed is byte-identical:

```bash
shasum -a 256 LimeStudio/app/src/main/res/raw/lime.db Database/lime.db
```

- [x] Re-open both bundled databases with `sqlite3` and verify `cj4` exists, `im` remains empty, `cj4` remains empty, and `user_version` is `104`:

```bash
sqlite3 LimeStudio/app/src/main/res/raw/lime.db "PRAGMA integrity_check; PRAGMA user_version; SELECT COUNT(*) FROM im; SELECT COUNT(*) FROM cj4;"
sqlite3 Database/lime.db "PRAGMA integrity_check; PRAGMA user_version; SELECT COUNT(*) FROM im; SELECT COUNT(*) FROM cj4;"
```

- [x] Verify both bundled databases do not have `keyboard.code='cj4'` and still have `keyboard.code='cj'`.

- [x] Add migration tests that open a DB 103 file without `cj4`, run the production DB open/migration path, and assert:

```text
PRAGMA user_version = 104
cj4 table exists
SELECT COUNT(*) FROM cj4 = 0
keyboard.code='cj4' does not exist
keyboard.code='cj' exists
existing user IM tables and im metadata are preserved
```

### Task 5: Import `.lime` into Android emulator and export `.limedb`

**Files:**

- Input: `.Codex/txt/haha_20260523_162540.lime`
- Output candidate: `.Codex/txt/cj4.limedb`
- Later commit target: `Database/cj4.limedb`

- [x] Push `.Codex/txt/haha_20260523_162540.lime` to the Android emulator.

- [x] Import it into table `cj4` using the Android LIME import flow.

- [x] Verify imported metadata in Android:

```text
name/cname: 哈哈倉頡
version: 20260523_162540
selkey: 123456789
endkey: abcdefghijklmnopqrstuvwxyz
spacestyle: 0
```

- [x] Export the installed table as `.limedb`.

- [x] Place the verified export at `Database/cj4.limedb` for commit. This file, not the raw seed `lime.db`, contains the `cj4` IM metadata and mapping rows for catalog download.

### Task 6: Add Android downloadable catalog entry

**Files:**

- Modify: `LimeStudio/app/src/main/java/net/toload/main/hd/global/LIME.java`
- Modify: `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/ImInstallFragment.java`
- Modify related Android catalog/install controller files if the URL/category model is split.

- [x] Add a GitHub raw URL constant for `Database/cj4.limedb`.

- [x] Add a new public category/family `四碼倉頡`.

- [x] Add variant:

```text
tableName: cj4
displayName: 哈哈倉頡
keyboard: cj
download: Database/cj4.limedb
```

- [x] Verify the Android installed-table screen shows `哈哈倉頡`; raw GitHub download screen requires post-push URL verification.

### Task 7: Add iOS downloadable catalog entry

**Files:**

- Modify: `LimeIME-iOS/LimeSettings/IMCatalog.swift`
- Modify: `LimeIME-iOS/LimeSettings/Controllers/IMStoreView.swift` only if category rendering needs adjustment.

- [x] Add category/family `四碼倉頡`.

- [x] Add variant:

```text
tableName: cj4
imName: cj4
displayName: 哈哈倉頡
keyboardId: cj
downloadURL: GitHub raw Database/cj4.limedb
```

- [x] Verify the iOS install/catalog screen visually shows `四碼倉頡` and `哈哈倉頡`.

### Task 8: End-to-end verification

**Files:**

- Verify: Android app/test APK
- Verify: iOS settings app/simulator
- Verify: `Database/cj4.limedb`

- [x] Android: import/install `哈哈倉頡` through the emulator import/export path and verify installed metadata/table.

- [x] Android: verify phrase/punctuation data exists in the generated `.limedb`; reporter should do final candidate-order UX confirmation.

- [x] Android: verify phrase/punctuation data exists in the generated `.limedb`; reporter should do final candidate-order UX confirmation.

- [ ] iOS: install from final GitHub raw catalog URL after commit/push and repeat sample checks. Skipped for this pass by maintainer direction; perform later if needed.

- [x] Export/import round-trip on Android and compare metadata with the source `.lime`.

- [ ] Ask the reporter to verify candidate ordering and phrase behavior with the generated `.limedb`.

## Superpowers workflow

Use Superpowers in this order:

1. `superpowers:brainstorming`
   - Done: product shape is new `四碼倉頡` family.
   - Done: table code is `cj4`; public display name is `哈哈倉頡`.
   - Done: source-of-truth import file is the reporter's LIME v2 text attachment, locally renamed from `.lime.txt` to `.lime`.
   - Capture license/attribution policy.

2. `superpowers:writing-plans`
   - Convert the approved design into a step-by-step implementation plan.
   - Include explicit Android, iOS, database packaging, catalog, docs, and verification tasks.

3. `superpowers:test-driven-development`
   - Add focused tests for format conversion/import where feasible before changing importer/catalog behavior.
   - For catalog-only changes, define verification commands and manual test cases first.

4. `superpowers:verification-before-completion`
   - Run build/test checks before saying the new IM is ready.
   - Confirm generated `.limedb` imports on both platforms.

If the implementation is split across independent Android/iOS/catalog packaging tasks, `superpowers:subagent-driven-development` may be useful after the implementation plan exists.

## Follow-up questions

- Should LIME publish only `Database/cj4.limedb`, or also keep the renamed `.lime` source somewhere for traceability?
- Where should CC BY 4.0 attribution appear in the app/catalog/docs?
- Should the reporter be asked to confirm that the converted `.limedb` preserves their intended candidate ordering and phrase behavior?

## Verification plan

Table validation:

1. Rename/copy `haha_20260523_162540.lime.txt` to a local `.lime` artifact.
2. Confirm it begins with `@format@|lime-text-v2` and includes `@version@|20260523_162540`.
3. Import the `.lime` artifact into Android emulator table `cj4`.
4. Compare metadata:
   - name: `哈哈倉頡`
   - version: `20260523_162540`
   - selection keys
   - end keys
   - space style
5. Compare record counts and a sample of candidate ordering across common codes.
6. Verify phrase entries beginning with `z`, punctuation entries beginning with `x`, and duplicate-resolution codes containing `z`.

Packaging verification:

1. Generate the candidate `.limedb`.
2. Install it through Android's downloadable IM flow.
3. Install it through iOS's downloadable IM flow.
4. Confirm the IM appears as `哈哈倉頡`.
5. Confirm a known sample set from the table:
   - single-character codes such as `a`, `aa`, `ab`
   - phrase codes beginning with `z`
   - punctuation codes beginning with `x`
   - duplicate-resolution examples containing `z`
6. Export the installed table back to `.lime` and compare key metadata and representative mappings.

Seed DB verification:

1. Verify Android raw seed `LimeStudio/app/src/main/res/raw/lime.db` has `PRAGMA user_version = 104`.
2. Verify shared/iOS seed `Database/lime.db` has `PRAGMA user_version = 104`.
3. Verify both seeds include empty `cj4` schema.
4. Verify both seeds keep `im` empty and `cj4` empty.
5. Verify both seeds reuse `keyboard.code='cj'` and do not include `keyboard.code='cj4'`.
6. Verify both seeds remain byte-identical after copying Android raw seed to `Database/lime.db`.

Existing-user migration verification:

1. Prepare or copy a DB 103 user database without `cj4`.
2. Open it through Android production DB initialization and verify `cj4` is created and existing IM data remains.
3. Open it through iOS production DB initialization and verify `cj4` is created and existing IM data remains.
4. Verify both migrated databases reuse `keyboard.code='cj'` and do not include `keyboard.code='cj4'`.
5. Verify both migrated databases report `PRAGMA user_version = 104`.

Public follow-up:

After producing a test `.limedb`, ask the reporter to import it and verify:

- candidate ordering
- phrase input behavior
- punctuation behavior
- whether the chosen family/category name matches user expectations

## Current status

Implementation-ready for review/commit. The user-provided LIME v2 table has been converted into `Database/cj4.limedb`, DB 104 schema support is implemented on Android and iOS, bundled seeds are upgraded to version 104 with an empty `cj4` schema, and `cj4` reuses the existing `cj` keyboard rather than creating a duplicate keyboard layout. Android visual verification confirms the installed `哈哈倉頡` table resolves to `倉頡輸入法鍵盤`; the keyboard picker no longer shows the stale `四碼倉頡輸入法鍵盤` duplicate and now displays the selected-state radio indicator. iOS visual verification confirms the install/catalog screen renders `四碼倉頡` and `哈哈倉頡` correctly.

Remaining release-only work: commit/push the branch changes, verify the final GitHub raw URL from both catalogs after merge, and ask the reporter to confirm candidate ordering and phrase/punctuation behavior. The 哈哈倉頡 CC BY 4.0 acknowledgement has been added to `LICENSE.md`.
