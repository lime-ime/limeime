# LIME DB 103 Implementation Plan

> **Historical record — shipped.** This plan was implemented and shipped (the #88
> restore-crash fixes came out of it). It is kept for its DB-103 contract and the
> Required Integrated Test Matrix, which the passing `LimeDB103IntegrationTest` cases
> still map to. **One detail has since moved:** emoji schema creation / data refresh is
> now triggered purely by the open-path `ensureCurrentDatabase()` check (gated on actual
> schema state + the `im` version row), **not** by the version-gated
> `onUpgrade(oldVersion < 103)` line — that emoji `onUpgrade` line has been removed
> because it was insufficient for restores that lie about their `user_version` (the #88
> family). Task 2's intent ("check for missing emoji tables, not only `PRAGMA
> user_version`") is the direction that was completed. For the current, unified rule see
> [EMOJI_DB_V2.md](EMOJI_DB_V2.md) and [ENG_AUTO_COMPLETION.md](ENG_AUTO_COMPLETION.md)
> "Bundled-payload version principle". The DB version is now 104 (`cj4`), not 103.
>
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make DB version 103 reliable for fresh installs, app upgrades, and user restores on Android and iOS without losing user-restored IM data or emoji data.

**Architecture:** `lime.db` remains the main application database and fresh-install seed, but the bundled seed is schema-only for input methods. The seed keeps the IM table schemas needed by runtime install/import paths, while all bundled IM metadata and mapping rows are cleared. Users install IM data later by download, import, or restore. `emoji.db` remains the bundled emoji data upgrade payload so existing and restored user databases can refresh emoji data non-destructively when the app ships a newer emoji version.

**Tech Stack:** Android Java + SQLite, iOS Swift + GRDB, bundled SQLite resources, `.Codex/scripts` verification scripts.

---

## Decisions

- `lime.db` must be upgraded to `PRAGMA user_version = 103` on both Android and iOS bundled seeds.
- Bundled `lime.db` must not include installed IM metadata or bundled IM mapping data. The `im`, `dayi`, and `phonetic` tables exist but are empty.
- Bundled `lime.db` must keep the `dayi` and `phonetic` table indexes so post-install imports use the same schema shape as a populated runtime DB.
- Bundled `lime.db` must include the 103 emoji schema, but it must not include emoji data.
- `emoji.db` must stay bundled. It is the authoritative emoji data payload for runtime refresh and future Emoji 18.0+ upgrades.
- Runtime DB open must repair old databases:
  - DBs older than 102 get the 102 migrations.
  - DBs older than 103 or missing emoji tables get the emoji schema.
  - DBs with missing or old `emoji/version` metadata import emoji data from bundled `emoji.db`.
- Restore paths must reopen the restored DB and immediately run the same migration/emoji refresh gate.
- Every upgrade, restore, repair, factory-reset, and emoji-refresh entry point must have integrated test coverage on both Android and iOS.
- Emoji refresh must preserve `emoji_user` usage rows when possible and delete only usage rows whose emoji no longer exists in the new `emoji_data`.

## Current Problem

Android fresh installs copy `LimeStudio/app/src/main/res/raw/lime.db` when the app-private DB file is missing. The seed must be a valid DB 103 schema-only database: no preinstalled IM metadata, no bundled IM data rows, and no old DB 102 emoji-schema gaps. The `dayi` and `phonetic` tables remain present as empty schema because the Android IM install/import implementation expects those target tables to exist.

The same class of problem exists after restore: a user may restore an old backup from before DB 103. That restored `lime.db` may have no emoji tables at all. Therefore fresh-install seed correctness is not enough; every restore must run the migration and emoji refresh gate too.

## Target DB 103 Contract

Main `lime.db` must satisfy:

- `PRAGMA integrity_check` returns `ok`.
- `PRAGMA user_version` returns `103`.
- Core tables exist, including `im`, `keyboard`, `related`, and generic mapping tables.
- The bundled seed is schema-only for IMs:
  - `im` exists and has zero rows.
  - `dayi` exists and has zero rows.
  - `phonetic` exists and has zero rows.
  - `dayi_idx_code`, `phonetic_idx_code`, and `phonetic_idx_code3r` exist.
  - No installed IM metadata or IM mapping data is bundled in `lime.db`.
- Emoji schema exists:
  - `emoji_data`
  - `emoji_fts`
  - `emoji_user`
  - `idx_emoji_group`
- Emoji data is empty in bundled `lime.db`; runtime refresh fills it from bundled `emoji.db`.

Bundled `emoji.db` must satisfy:

- `emoji_data` exists and has rows.
- `im WHERE code='emoji'` exists and includes `title='version'`.
- The version row matches the app constant, currently `17.0`.

## Shared Runtime Algorithm

Every database open after install, upgrade, factory reset, or restore should run:

```text
ensureCurrentDatabase()
  read PRAGMA user_version
  if version < 102:
    apply 102 migration
  if version < 103 or emoji schema missing:
    create emoji schema
  set PRAGMA user_version = 103

  read main DB emoji/version from im
  if emoji/version != bundled EMOJI_DATA_VERSION:
    import emoji data and emoji metadata from bundled emoji.db
    rebuild emoji_fts
    preserve emoji_user rows that still reference existing emoji_data values
```

This algorithm must be idempotent. Running it on an already-current DB must not duplicate keyboard rows, IM rows, indexes, or emoji metadata. It may import `code='emoji'` metadata from bundled `emoji.db`, but it must not auto-install user-facing IM metadata or IM mapping rows into a fresh schema-only bundled seed.

## Prepared Seed DB State

Current prepared seed files:

- Android seed: `LimeStudio/app/src/main/res/raw/lime.db`
- Shared/iOS seed: `Database/lime.db`
- Emoji payload: `LimeStudio/app/src/main/res/raw/emoji.db` and `Database/emoji.db`

The prepared `lime.db` seed state is:

| Object | Seed state |
| --- | --- |
| `PRAGMA user_version` | `103` |
| `im` | table exists, zero rows |
| `dayi` | table exists, zero rows |
| `phonetic` | table exists, zero rows |
| `dayi_idx_code` | exists |
| `phonetic_idx_code` | exists |
| `phonetic_idx_code3r` | exists |
| `emoji_data` | table exists, zero rows |
| `emoji_fts` | virtual table exists |
| `emoji_user` | table exists, zero rows |
| `idx_emoji_group` | exists |

The prepared `emoji.db` payload state is:

| Object | Payload state |
| --- | --- |
| `emoji_data` | populated, currently 1707 rows |
| `im WHERE code='emoji'` | contains emoji metadata rows |
| `emoji/version` | currently `17.0` |

Do not add bundled user-facing IM metadata rows to `lime.db`. Do not prefill `dayi`, `phonetic`, or `emoji_data` in `lime.db`. Runtime install/import/refresh paths are responsible for those rows:

- IM rows come from user download, import, or restore.
- Emoji rows come from bundled `emoji.db` through the runtime emoji refresh path.

## Required Integrated Test Matrix

Every row below must be covered by an integrated test on Android and iOS. Unit tests may cover helper details, but they are not enough for acceptance.

| Path | Android integrated test | iOS integrated test | Required assertions |
| --- | --- | --- | --- |
| Fresh app DB creation from bundled `lime.db` | `LimeDBTest` or `DBServerTest` creates app DB from raw resource | `DBServerTest` creates shared DB from bundled resource | `user_version=103`, bundled seed `im`, `dayi`, and `phonetic` exist and are empty, required `dayi`/`phonetic` indexes exist, emoji schema present, emoji data refresh succeeds without adding user-facing IMs |
| Existing DB upgrade from version `<102` | Open a real copied old DB through production DB layer | Open a real copied old DB through `LimeDB(path:)` or `DBServer` | 102 rows repaired, 103 schema present, user/restored IM metadata preserved |
| Existing DB upgrade from version `102` | Open real 102 DB through production DB layer | Open real 102 DB through production DB layer | emoji schema created, emoji data imported, version stamped 103 |
| Version `103` DB missing emoji schema | Open damaged 103 DB through production DB layer | Open damaged 103 DB through production DB layer | missing emoji tables repaired even though version is already 103 |
| Emoji data refresh | Open DB with old/missing `emoji/version` | Open DB with old/missing `emoji/version` | `emoji_data` populated from `emoji.db`, `im code='emoji'` metadata replaced once |
| Emoji user preservation | Refresh DB with existing `emoji_user` rows | Refresh DB with existing `emoji_user` rows | valid usage rows remain, invalid rows are pruned |
| User restore from old backup | Restore real backup archive through UI/server restore path | Restore real backup archive through `DBServer.restoreDatabase` | restored DB repaired, emoji data imported, original IM/user data preserved |
| Factory reset / restore bundled DB | Trigger production factory-reset path | Trigger `restoreBundledDatabase()` | reset DB is 103, `im`, `dayi`, and `phonetic` exist and are empty, no user-facing IM metadata exists, emoji schema present, emoji refresh succeeds |
| Idempotent second open | Open repaired DB twice | Open repaired DB twice | no duplicate `keyboard` rows, no duplicate `im code='emoji'` rows, no data loss |

## Android Plan

### Task 1: Build a Repeatable Seed DB Upgrade Script

**Files:**
- Create or modify: `.Codex/scripts/upgrade_android_lime_seed_to_103.sh`
- Modify output: `LimeStudio/app/src/main/res/raw/lime.db`
- Read source: `LimeStudio/app/src/main/res/raw/emoji.db`

- [ ] Create a script with a header explaining purpose and usage.
- [ ] Validate `lime.db` exists before editing.
- [ ] Validate `emoji.db` exists and contains `emoji_data`.
- [ ] Create missing emoji tables in `lime.db`.
- [ ] Create `idx_emoji_group`.
- [ ] Create/rebuild `emoji_fts`.
- [ ] Clear all rows from `im` in bundled `lime.db`.
- [ ] Keep `dayi` and `phonetic` table schemas in `lime.db`.
- [ ] Clear all rows from `dayi` and `phonetic` in bundled `lime.db`.
- [ ] Keep `dayi_idx_code`, `phonetic_idx_code`, and `phonetic_idx_code3r`.
- [ ] Set `PRAGMA user_version = 103`.
- [ ] Do not import emoji data into bundled `lime.db` unless explicitly requested later.
- [ ] Run `PRAGMA integrity_check`.

Expected script checks:

```sh
sqlite3 LimeStudio/app/src/main/res/raw/lime.db "PRAGMA integrity_check;"
sqlite3 LimeStudio/app/src/main/res/raw/lime.db "PRAGMA user_version;"
sqlite3 LimeStudio/app/src/main/res/raw/lime.db "SELECT COUNT(*) FROM im;"
sqlite3 LimeStudio/app/src/main/res/raw/lime.db "SELECT 'dayi', COUNT(*) FROM dayi UNION ALL SELECT 'phonetic', COUNT(*) FROM phonetic;"
sqlite3 LimeStudio/app/src/main/res/raw/lime.db "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name IN ('dayi','phonetic') ORDER BY name;"
sqlite3 LimeStudio/app/src/main/res/raw/lime.db "SELECT name FROM sqlite_master WHERE name IN ('emoji_data','emoji_fts','emoji_user','idx_emoji_group') ORDER BY name;"
```

### Task 2: Strengthen Android Runtime Migration

**Files:**
- Modify: `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java`
- Test: `LimeStudio/app/src/androidTest/java/org/limeime/LimeDBTest.java`
- Integrated test: `LimeStudio/app/src/androidTest/java/org/limeime/DBServerTest.java`

- [ ] Add a public or package-visible repair method, for example `ensureCurrentDatabase()`.
- [ ] Move the `oldVersion < 103` emoji schema creation into a method that also checks for missing emoji tables, not only `PRAGMA user_version`.
- [ ] Ensure `refreshEmojiDataIfNeeded()` runs after schema repair.
- [ ] Keep `refreshEmojiDataIfNeeded()` importing from `R.raw.emoji`.
- [ ] Do not auto-install IM metadata or IM mapping data during fresh DB open. IM data is installed only by download, import, or restore.
- [ ] Preserve `emoji_user` during refresh:

```sql
DELETE FROM emoji_user
WHERE value NOT IN (SELECT value FROM emoji_data);
```

- [ ] Verify refresh does not duplicate `im WHERE code='emoji'`.
- [ ] Add integrated instrumentation tests for every Android row in the Required Integrated Test Matrix.
- [ ] Each Android integrated test must use a real SQLite file copied into an app/test database path and exercise the production DB open or restore path; direct helper-only tests do not satisfy this requirement.

### Task 3: Run Android Repair After Restore

**Files:**
- Modify: `LimeStudio/app/src/main/java/org/limeime/DBServer.java`
- Possibly modify: `LimeStudio/app/src/main/java/org/limeime/ui/controller/SetupImController.java`
- Test: `LimeStudio/app/src/androidTest/java/org/limeime/DBServerTest.java`

- [ ] Find every path that replaces the main `lime.db`.
- [ ] After restore completes and DB connection reopens, call `datasource.ensureCurrentDatabase()`.
- [ ] Reset search cache after repair.
- [ ] Add an integrated restore regression test that restores a real archive containing a DB with no emoji tables and verifies:
  - `PRAGMA user_version = 103`
  - `emoji_data` exists
  - `emoji/version` equals `EMOJI_DATA_VERSION`
  - pre-existing IM rows still exist
- [ ] Add integrated tests for every Android restore-like DB replacement path, including user restore and factory reset / restore bundled DB.

### Task 4: Android Verification

- [ ] Run seed DB checks:

```sh
sh .Codex/scripts/check_android_lime_seed_db.sh
sqlite3 LimeStudio/app/src/main/res/raw/lime.db "PRAGMA integrity_check; PRAGMA user_version;"
```

- [ ] Run focused instrumentation tests:

```sh
cd LimeStudio
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=net.toload.main.hd.LimeDBTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=net.toload.main.hd.DBServerTest
```

- [ ] Run build:

```sh
cd LimeStudio
./gradlew :app:assembleDebug
```

## iOS Plan

### Task 5: Copy the Android 103 Seed DB to the Shared iOS Seed

**Files:**
- Read source: `LimeStudio/app/src/main/res/raw/lime.db`
- Modify output: `Database/lime.db`
- iOS bundle copy target: `LimeIME-iOS/LimeIME.xcodeproj/project.pbxproj` already copies `Database/lime.db`.

- [ ] Do not run a second iOS-specific DB upgrade script.
- [ ] First make the Android raw seed DB satisfy the DB 103 contract.
- [ ] Copy the Android raw seed DB into the shared root seed:

```sh
cp LimeStudio/app/src/main/res/raw/lime.db Database/lime.db
```

- [ ] Verify the copied file is byte-identical:

```sh
shasum -a 256 LimeStudio/app/src/main/res/raw/lime.db Database/lime.db
```

- [ ] Leave emoji data refresh source as `Database/emoji.db`; do not duplicate emoji data or IM data into the seed unless explicitly requested later.
- [ ] Verify iOS bundled `emoji.db` still copies into the app bundle.

### Task 6: Strengthen iOS Runtime Migration

**Files:**
- Modify: `LimeIME-iOS/Shared/Database/LimeDB.swift`
- Test: `LimeIME-iOS/LimeTests/LimeDBTest.swift`
- Integrated test: `LimeIME-iOS/LimeTests/DBServerTest.swift`

- [ ] Keep `CURRENT_DB_VERSION = 103`.
- [ ] Make migration check missing emoji tables even when `PRAGMA user_version` is already `103`.
- [ ] Ensure `refreshEmojiDataIfNeeded()` runs after schema migration on every `LimeDB(path:)` initialization.
- [ ] Keep `Bundle.main.url(forResource: "emoji", withExtension: "db")` as the data source.
- [ ] Do not auto-install IM metadata or IM mapping data during fresh DB open. IM data is installed only by download, import, or restore.
- [ ] Preserve `emoji_user` on refresh.
- [ ] Add tests for:
  - DB `102` without emoji tables migrates to `103`.
  - DB `103` missing emoji tables repairs itself.
  - Old or missing `emoji/version` imports from bundled `emoji.db`.
  - `emoji_user` usage rows are preserved for emoji values still present after refresh.
- [ ] Add integrated tests for every iOS row in the Required Integrated Test Matrix.
- [ ] Each iOS integrated test must use a real SQLite file copied into an app-group-style or injected DB path and exercise `LimeDB(path:)`, `DBServer`, or the production restore path; direct helper-only tests do not satisfy this requirement.

### Task 7: Run iOS Repair After Restore

**Files:**
- Modify: `LimeIME-iOS/Shared/Database/DBServer.swift`
- Test: `LimeIME-iOS/LimeTests/DBServerTest.swift`

- [ ] In `restoreDatabase(srcFilePath:)`, after moving restored `lime.db` into place and reopening `datasource`, call the same migration/refresh path.
- [ ] In `restoreBundledDatabase()`, after copying bundled `lime.db` and reopening `datasource`, call the same migration/refresh path.
- [ ] Keep the existing `lime_db_restored_at` notification behavior for keyboard reload.
- [ ] Add an integrated restore regression test with an old DB archive missing emoji tables and verify the restored runtime DB has:
  - `PRAGMA user_version = 103`
  - `emoji_data` exists and has rows imported from bundled `emoji.db`
  - `emoji/version` equals `EMOJI_DATA_VERSION`
  - pre-existing IM rows still exist
- [ ] Add integrated tests for every iOS restore-like DB replacement path, including user restore and `restoreBundledDatabase()`.

### Task 8: iOS Verification

- [ ] Run DB checks:

```sh
sqlite3 Database/lime.db "PRAGMA integrity_check; PRAGMA user_version;"
sqlite3 Database/lime.db "SELECT COUNT(*) FROM im;"
sqlite3 Database/lime.db "SELECT 'dayi', COUNT(*) FROM dayi UNION ALL SELECT 'phonetic', COUNT(*) FROM phonetic;"
sqlite3 Database/lime.db "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name IN ('dayi','phonetic') ORDER BY name;"
sqlite3 Database/lime.db "SELECT name FROM sqlite_master WHERE name IN ('emoji_data','emoji_fts','emoji_user','idx_emoji_group') ORDER BY name;"
sqlite3 Database/emoji.db "SELECT COUNT(*) FROM emoji_data; SELECT desc FROM im WHERE code='emoji' AND title='version';"
```

- [ ] Run focused tests:

```sh
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'platform=iOS Simulator,name=iPhone 16' test -only-testing:LimeTests/LimeDBTest
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'platform=iOS Simulator,name=iPhone 16' test -only-testing:LimeTests/DBServerTest
```

- [ ] Run build:

```sh
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' build
```

## Future Emoji 18.0 Upgrade Path

When a new emoji release ships:

- Update `emoji.db` with new emoji data and `im` metadata.
- Bump Android `EMOJI_DATA_VERSION`.
- Bump iOS `EMOJI_DATA_VERSION`.
- Optionally update bundled `lime.db` seeds to keep schema/version current, but do not need to duplicate emoji data there.
- Runtime users get the new data because their main DB `emoji/version` differs from the bundled constant.
- Restored old backups get the same repair and refresh behavior.

## Acceptance Criteria

- Fresh Android install uses a schema-only bundled seed: `im`, `dayi`, and `phonetic` exist with zero rows before runtime import/refresh, and required IM indexes exist.
- Fresh iOS install uses the same schema-only bundled seed as Android: `im`, `dayi`, and `phonetic` exist with zero rows before runtime import/refresh, and required IM indexes exist.
- User-facing IM rows and IM mapping data appear only after user download, import, or restore.
- Restoring a pre-103 DB on Android creates emoji schema and imports emoji data.
- Restoring a pre-103 DB on iOS creates emoji schema and imports emoji data.
- Every Required Integrated Test Matrix row has Android integrated coverage.
- Every Required Integrated Test Matrix row has iOS integrated coverage.
- Future emoji data upgrades require replacing `emoji.db` and bumping one version constant per platform.
- `emoji_user` survives data refresh for still-valid emoji values.
- No destructive full-DB replacement happens during emoji refresh.

## Open Risks

- Android Java source files in this repo may contain BOMs that `javac` rejects; verify before adding source edits.
- iOS restore must avoid reopening stale GRDB queues against replaced files; keep the current close/reopen sequencing.
- Android platform SQLite creates `emoji_fts` as FTS4 directly; iOS may use FTS5. Cross-platform tests should assert behavior, while Android legacy-restore tests may assert FTS4 to prevent reintroducing the FTS5-first crash path.
