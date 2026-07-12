# LIME `.limedb` Specification

## Purpose

`.limedb` is LIME's portable database exchange format for input-method data and related phrases.

It is used for:

- Sharing or backing up one input-method mapping table.
- Importing a downloaded or user-provided input-method table.
- Sharing or backing up the related-phrase table.

It is not a full application backup format. It does not replace `lime.db`, user preferences, keyboard layout assets, or runtime settings.

## Container Format

A `.limedb` file is a ZIP archive.

Current importers expect the ZIP archive to contain exactly one SQLite database file. The inner filename is not semantic, but LIME exports use:

- `<tableName>.db` for a single input-method table, for example `custom.db`.
- `related.db` for related phrases.

If a ZIP contains zero files or more than one file, import is rejected.

## SQLite Encoding

The inner file is a SQLite database.

Text values are stored as SQLite `TEXT`. LIME does not require a separate text encoding marker inside `.limedb`; SQLite stores text internally and exposes it as Unicode strings through the platform SQLite APIs.

## Single IM Table Export Format

LIME's current export format stores mapping rows in a table named `custom`, even when the exported source table is not `custom`.

Required table:

```sql
CREATE TABLE custom (
    _id       INTEGER PRIMARY KEY AUTOINCREMENT,
    code      TEXT,
    word      TEXT,
    score     INTEGER DEFAULT 0,
    basescore INTEGER DEFAULT 0,
    code3r    TEXT
);
```

Required meaning:

- `code`: input code.
- `word`: candidate text.
- `score`: user/runtime score.
- `basescore`: base score used by ranking.
- `code3r`: optional reverse/auxiliary code field.

The importer also accepts a direct table format where rows are stored in a table whose name matches the selected target table. For example, when importing into `dayi`, LIME first tries `custom`; if `custom` does not exist, it falls back to `dayi`.

## IM Metadata Table

A single-IM `.limedb` may include an `im` table. LIME uses this to import display/config metadata for the target IM.

Expected table:

```sql
CREATE TABLE im (
    _id        INTEGER PRIMARY KEY AUTOINCREMENT,
    code       TEXT,
    title      TEXT,
    desc       TEXT,
    keyboard   TEXT,
    disable    BOOLEAN,
    selkey     TEXT,
    endkey     TEXT,
    spacestyle TEXT
);
```

Current Android export copies matching `im` rows from the runtime database into the exported database. Current iOS export creates the same table and copies matching `im` rows.

Android and iOS both merge `im` metadata as part of `.limedb` database import. This applies to the user-facing `.limedb` flow and to the generic iOS `importDb` helper, so metadata behavior stays aligned across platforms.

Import behavior:

- Android imports `code, title, desc, keyboard, disable, selkey, endkey, spacestyle` from the source `im` table.
- iOS imports the same columns when the source has an `im` table and a row for the target table.
- `_id` is not a stable interchange value. Importers should assign fresh row ids.
- For a single-target import, Android and iOS rewrite incoming `im.code` to the selected target table name before insertion.

## Related Phrase Format

Related phrase `.limedb` files store rows in `related`.

Expected table:

```sql
CREATE TABLE related (
    _id       INTEGER PRIMARY KEY AUTOINCREMENT,
    pword     TEXT,
    cword     TEXT,
    basescore INTEGER DEFAULT 0,
    score     INTEGER DEFAULT 0
);
```

Meaning:

- `pword`: previous/source word.
- `cword`: related/candidate word.
- `basescore`: base score.
- `score`: runtime/user score.

Related phrase import is separate from IM-table import. A related `.limedb` should be imported through the related import path.

## Keyboard Table

The `.limedb` import path does not import or update the `keyboard` table.

This is intentional current behavior. The imported `im.keyboard` value is only useful if the app already has a matching keyboard config in its runtime `keyboard` table or a hardcoded fallback for that keyboard code.

Consequences:

- Do not rely on `.limedb` to ship new keyboard layout definitions.
- Do not rely on `.limedb` to add `engkb`, `engshiftkb`, `imkb`, or layout-resource mappings.
- Keyboard configs must be provided by the app database migration/default seed or by code fallback.

Example: `wb` and `hs` can resolve `lime_abc` / `lime_abc_shift` because LIME seeds or falls back those keyboard configs in code. A custom `.limedb` that references an unknown `im.keyboard` code may import its mapping rows but fail to select the intended visual keyboard.

## Import Algorithm

For a single input-method import:

1. Unzip the `.limedb`.
2. Require exactly one inner SQLite database file.
3. Validate the target table name against LIME's allowed IM table names.
4. Clear the target mapping table when overwrite is requested.
5. Import mapping rows:
   - Prefer source table `custom`.
   - If `custom` is absent, use source table matching the target table name.
6. Import `im` metadata when present:
   - Delete existing target/incoming `im` rows.
   - Insert `code, title, desc, keyboard, disable, selkey, endkey, spacestyle`.
   - Do not copy source `_id`.
7. Do not import `keyboard`.
8. Reset runtime search/cache state after import.

For a related-phrase import:

1. Unzip the `.limedb`.
2. Require exactly one inner SQLite database file.
3. Clear `related`.
4. Insert rows from source `related`.
5. Reset runtime search/cache state after import.

## Export Algorithm

For a single input-method export:

1. Create a temporary SQLite database from the blank template.
2. Copy all rows from the source IM table into `custom`.
3. Copy matching `im` metadata rows into `im`.
4. Zip the temporary database as `<tableName>.db`.
5. Save the ZIP as `.limedb`.

For related export:

1. Create a temporary SQLite database from the related blank template.
2. Copy all rows from `related`.
3. Zip the temporary database as `related.db`.
4. Save the ZIP as `.limedb`.

## Compatibility Rules

Producers should:

- Use one SQLite database file per `.limedb` ZIP.
- Include a `custom` mapping table for single-IM exports.
- Include an `im` table when the table should carry display/config metadata.
- Avoid depending on the `keyboard` table being imported.
- Preserve the column names listed above.

Consumers should:

- Accept `custom` as the primary mapping table.
- Accept direct table-name import as a compatibility fallback.
- Ignore `_id` as an interchange identity.
- Treat unknown extra tables as optional data.
- Treat unknown extra columns as optional, unless a future migration explicitly requires them.

## Relationship To `.lime` / `.cin`

`.lime` and `.cin` are text table formats. `.limedb` is a zipped SQLite format.

Use `.lime` / `.cin` when a table should be human-readable or editable as text. Use `.limedb` when preserving SQLite fields such as `score`, `basescore`, `code3r`, and related metadata matters.

## Current Implementation References

- Android import/export: `LimeStudio/app/src/main/java/org/limeime/DBServer.java`
- Android table copy logic: `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java`
- iOS import/export: `LimeIME-iOS/Shared/Database/DBServer.swift`
- iOS table copy logic: `LimeIME-iOS/Shared/Database/LimeDB.swift`
