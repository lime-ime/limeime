# LIME DB 105 Implementation Plan

> **For agentic workers:** Execute this plan in Claude **goal mode** — pick the next unchecked (`- [ ]`) task, drive it to done, then check it off before moving on. Dispatch the implementation work to **Sonnet** subagents (`Agent` tool with `model: sonnet`), one focused task per agent; reserve the main session for sequencing, review, and marking steps complete. Steps use checkbox syntax for tracking.

**Goals:**

1. **imkeys/imkeynames backfill.** Guarantee that every known standard IM table (大易 / 倉頡 family / 行列 / 注音 / …) always has `imkeys` and `imkeynames` metadata in the `im` table, even when the table entered the database from an old backup/`.limedb` that predates that metadata. The visible symptom is an exported `.lime` missing `@imkeys@` / `@imkeynames@` (reported for an old 大易 table); the same gap degrades cross-platform re-import and any consumer that reads the raw `im` rows.
2. **Move the per-load schema repairs to a one-time versioned 105 migration.** The schema/keyboard repairs — `createEmojiTables` (iOS), `ensureCj4Schema`, `ensureTricodeSchema`, `ensureComputerNumKeyboard`, `ensureCangjieSemicolonKeyboards`, `ensureLimeNumSym2Keyboard` — plus the DB-105 `imkeys` backfill previously ran **unconditionally on every open** in `ensureCurrentDatabase()`. DB 105 moves them into the **one-time `version < 105` migration** (`upgradeIfNeeded` / `onUpgrade`) and **removes them from the per-load path**. `ensureCurrentDatabase()` is gutted down to content-currency refreshes only (emoji / dictionary), which float free of `user_version` and must stay per-load.

**Architecture:** DB 105's `version < 105` branch runs the **full** idempotent schema set, and the version is stamped **only after it completes**, so `user_version = 105 ⟺ the DB is complete`. That guarantee is what makes the every-load net unnecessary: a fresh install ships at `user_version 104` (the bundled seed is **not** re-stamped to 105) and the migration completes it on first open; a restore of any pre-105 backup re-runs the migration via `init()->migrate()` (iOS) / `SQLiteOpenHelper.onUpgrade` (Android); and no "stale 105" DB can exist because 105 is only reached through the migration or a complete build. Goal 1's `imkeys` backfill for a table restored into an *already-105* live DB is covered by the IM-load-time fallback (export prologue) + the runtime in-memory `imKeysForTable`, so it too needs no per-load net.

**Why this is #88-safe (it is the #88 fix, not a violation):** the historical #88 restore-crash existed because emoji was added **without a version bump** — `user_version` could not distinguish "has emoji" from "doesn't," so a version-gated path skipped the repair. The every-load net was the band-aid. The real fix is exactly this: **bump the version so the stamp is meaningful, gate all schema on it, and stamp only after completion.** Emoji/dictionary content currency is the one thing that legitimately floats free of `user_version` (it can refresh in a patch release with no schema bump), so `refreshEmojiDataIfNeeded()` / `refreshDictionaryDataIfNeeded()` stay per-load — but they are cheap version-row-gated no-ops, not schema DDL.

**Tech Stack:** Android Java + SQLite, iOS Swift + GRDB, bundled SQLite seeds, LIME `.limedb` / `.lime` import-export and backup-restore flows.

**Reference:** [LIME_DB_104.md](LIME_DB_104.md), [LIMEDB_SPEC.md](LIMEDB_SPEC.md), [SEED_IM.md](SEED_IM.md), issue #186.

---

## Problem statement

1. A user restores (or installs a legacy `.limedb` for) a standard IM table — reported case: an **old 大易 (dayi)** table.
2. The old database predates `imkeys` / `imkeynames` storage, so its `im` table has **no** `imkeys` / `imkeynames` rows.
3. `importFromAttachedDB` (iOS) / `importDb` (Android) copy the source `im` rows verbatim, so the restored table stays without those rows.
4. `exportTxtTable` reads `imkeys` / `imkeynames` **only** from the stored `im` rows (`c.title == "imkeys" → c.desc`) with no fallback, so the exported `.lime` omits `@imkeys@` / `@imkeynames@`.

Downstream impact: the exported file is not self-contained; re-importing it into a table not recognised as the original standard table (cross-platform, renamed, or a generic `custom` target) loses the key→radical map; any reader of the raw `im` rows sees empty key metadata.

## Why the existing paths do not cover this

- **`upgradeIfNeeded` / `onUpgrade` (version-gated) does not help.** A table restored **into an already-current-version database** does not re-trigger any `version < N` branch — the database is already stamped at the current `user_version`. onUpgrade only fires while crossing a version boundary, not when new legacy data lands in an up-to-date DB.
- **The text importer's default backfill is import-only.** iOS `applyDefaultMetadataForStandardIM` and Android's inline default block (in `importTxtTable`) already write `dayi → DAYI_KEY / DAYI_CHAR` (and the other standard tables) when `!hasImportedImkeys` — but they run **only** from `.cin`/`.lime` text import, never from `.limedb`/backup restore.
- **`ensureCurrentDatabase()` runs at open, but not mid-session.** It is the correct idempotent home (it already repairs `cj4`/`tricode`), and it does run after restore *when the DB is reopened*. But a restore that populates a table in a live, already-open database can be followed by an export/render **before** the next `ensureCurrentDatabase()` — hence the additional load-time fallback.

## Decisions

- Bump main DB version `104 → 105` on Android (`DATABASE_VERSION`) and iOS (`CURRENT_DB_VERSION`).
- **Goal 2 — move the schema repairs to a one-time migration:** the `version < 105` branch of `upgradeIfNeeded` (iOS) / `onUpgrade` (Android) runs the **full** idempotent schema set (`createEmojiTables` [iOS], `ensureCj4Schema`, `ensureTricodeSchema`, `ensureComputerNumKeyboard`, `ensureCangjieSemicolonKeyboards`, `ensureLimeNumSym2Keyboard`) plus `ensureStandardIMKeyMetadata`, then the version is stamped. Completion ⟺ `user_version = 105`.
- **Remove the every-load net:** the schema/keyboard ensures + `imkeys` backfill are **removed** from `ensureCurrentDatabase()`, which is reduced to `refreshEmojiDataIfNeeded()` (+ `refreshDictionaryDataIfNeeded()` on Android). Safe because the version stamp is now authoritative (seed ships at 104 → migration completes on first open; restore re-runs the migration; no stale-105 DB exists). This is the #88 *fix* — the historical crash came from emoji being added with **no** version bump, which is exactly what a proper 105 migration corrects; emoji/dictionary *content* stays per-load because it legitimately floats free of `user_version`.
- **Seed stays at `user_version 104`:** the bundled `lime.db` is **not** re-stamped to 105. A fresh install triggers the `< 105` migration on first open, which completes it. (This reverts the earlier "re-stamp seed to 105" step, which would have left fresh installs incomplete once the net was removed.)
- Add one idempotent repair, `ensureStandardIMKeyMetadata(db)`, to **`ensureCurrentDatabase()`** on both platforms. It backfills `imkeys` / `imkeynames` for every **known standard** table that (a) exists with mapping rows and (b) is missing a non-empty `imkeys` or `imkeynames` `im` row.
- **`ensureStandardIMKeyMetadata` MUST write through the passed `db` handle directly** (`db.execute(...)`), exactly like `ensureCj4Schema(_ db:)`. It must **not** call `setImConfig` / `applyDefaultMetadataForStandardIM`, because those open their own `dbQueue.write` and this runs *inside* `ensureCurrentDatabase()`'s `dbQueue.write` block — GRDB `write` is non-reentrant, so a nested write **deadlocks**. (This is why the earlier "reuse `applyDefaultMetadataForStandardIM`" idea does not work for the ensure path.)
- **Never overwrite** an existing non-empty `imkeys` / `imkeynames` — user-customised or imported values always win. Backfill fills only the empty/absent case (same contract as `!hasImportedImkeys`).
- **Single source of truth for the defaults.** `applyDefaultMetadataForStandardIM` currently spells the `array` and `phonetic` `imkeys`/`imkeynames` as **inline string literals**, while `cj`/`dayi`/`ez` already use the `*_KEY`/`*_CHAR` constants. Prep task: extract the inline `array` + `phonetic` key/keyname literals into named constants (verify they equal the existing `ARRAY_KEY`/`ARRAY_CHAR` and `BPMF_KEY`/`BPMF_CHAR`, and reuse those if identical; otherwise add `ARRAY_*`/`BPMF_*` constants). Then both `applyDefaultMetadataForStandardIM` (instance/`setImConfig` path) and `ensureStandardIMKeyMetadata` (`db`-direct path) read the **same** constants — no duplicated literals.
- **iOS:** add the missing `imKeyNamesForTable(_:)` counterpart to `imKeysForTable(_:)` (in-memory default resolution for the extension), and implement `ensureStandardIMKeyMetadata` as a `db`-direct writer per the rule above.
- **Android:** extract the inline `imkeys`/`imkeynames` default switch out of `importTxtTable` into a reusable `applyStandardIMKeyDefaults(SQLiteDatabase db, String table, boolean hasImkeys, boolean hasImkeynames)` helper that writes via the passed `db`; call it from `importTxtTable` (unchanged behaviour), `ensureStandardIMKeyMetadata`, and the new paths.
- Add an **IM-load-time fallback** that calls the same idempotent backfill when an IM is loaded in a cold-write-safe (host-app) context — concretely at the **prologue of `SetupImController.exportIMAsText(tableNick:)`** (iOS), before `getImConfigList`, and the Android export equivalent. The call is a plain `ensureStandardIMKeyMetadata(table)` (its own `dbQueue.write`, not nested), so no deadlock. The keyboard **extension** never writes the cold `im` table; its runtime resolves defaults in memory via `imKeysForTable` / the new `imKeyNamesForTable`.
- Do **not** touch `keyboard`-code export, runtime bookkeeping (`amount`/`source`/`import`/`disable`), or the record/related formats. Scope is `imkeys`/`imkeynames` only.
- Bundled `lime.db` (both platforms) **stays at `user_version 104`** — not re-stamped, and not physically extended with the post-104 keyboards. The `< 105` migration completes it on first open. No new mapping rows or `im` rows are added to the seed.

## Known-table → default map

Backfill applies to the tables the importer/runtime already know how to default. Any table not in this set (including `custom`, `pinyin`, imported user tables) is left untouched — those legitimately have no built-in key map.

| Table(s) | imkeys default | imkeynames default |
|---|---|---|
| `dayi` | `DAYI_KEY` | `DAYI_CHAR` |
| `cj`, `cj4`, `cj5`, `ecj`, `scj` | `CJ_KEY` | `CJ_CHAR` |
| `array` | `ARRAY_KEY` (see prep task) | `ARRAY_CHAR` / extracted literal |
| `ez` | `EZ_KEY` | `EZ_CHAR` |
| `phonetic` | `BPMF_KEY` (base) | `BPMF_CHAR` / extracted literal |

**`array10` is intentionally excluded** — 行列10 "deliberately has NO keyname conversion" (LimeDB.swift comment; only `ARRAY10_KEY` exists, there is no `ARRAY10_CHAR`). Do not backfill `imkeynames` for `array10`. Any table not in this map (`custom`, `pinyin`, `array10`, imported user tables) is left untouched.

(`phonetic` variant keyboards — eten26/hsu/et_41 — remain resolved at runtime by `imKeysForTable` using `phoneticKeyboardType`; the stored default is the base 注音 map, matching current import behaviour. The `array`/`phonetic` values come from the constants produced by the prep task in Decisions, not from fresh literals.)

## Runtime repair order (both platforms)

One path does the schema work: the **one-time `version < 105` migration**. `ensureCurrentDatabase()` no longer touches schema — it is reduced to content-currency refreshes.

**One-time migration — `upgradeIfNeeded` (iOS) / `onUpgrade` (Android)** (runs only when stored `user_version < 105`; the version is stamped afterwards, so completion ⟺ 105):

```text
if version < 105:
    createEmojiTables            # iOS only; Android emoji lives in refreshEmojiDataIfNeeded
    ensureCj4Schema / ensureTricodeSchema
    ensureComputerNumKeyboard / ensureCangjieSemicolonKeyboards / ensureLimeNumSym2Keyboard
    ensureStandardIMKeyMetadata()   # goal 1: backfill imkeys/imkeynames for known standard tables
stamp user_version = 105            # iOS: explicit; Android: SQLiteOpenHelper stamps after onUpgrade
```

The branch runs the **full** idempotent schema set (not just the post-104 additions) so that any pre-105 DB — fresh 104 seed, in-place upgrade, or restored backup — reaches a complete 105 in one pass. A stale `cj4` keyboard row on a 104 DB, for example, is cleaned by `ensureCj4Schema` here because that DB crosses the `< 105` boundary.

**Every-load — `ensureCurrentDatabase()`** (content currency only; no schema DDL, no version stamp):

```text
ensureCurrentDatabase()
  refreshEmojiDataIfNeeded()          # gated on the emoji im-row version, not user_version
  refreshDictionaryDataIfNeeded()     # Android only; same content-version gate
```

These two floated free of `user_version` historically (that is the real root of #88) and stay per-load, but they are cheap version-row-gated no-ops when current — not the schema DDL that used to run every open.

**Seed:** the bundled `lime.db` ships at `user_version 104` (**not** re-stamped to 105). A fresh install opens it, the `< 105` migration completes it, and it is stamped 105. This is why the seed does not need to physically contain the post-104 keyboards.

## IM-load-time fallback

Because a restore into a live database can precede the next `ensureCurrentDatabase()`, add a lightweight idempotent guard at IM load:

- **Trigger:** when an IM table is loaded for use in a cold-write-safe context — i.e. the host app (Settings) IM activation / management load, and immediately before `exportTxtTable` assembles metadata. Not `setTableName` on the hot keyboard path, and never inside the keyboard extension.
- **Action:** call `ensureStandardIMKeyMetadata(table)` scoped to the single table; it is a no-op when the rows already exist, so it is safe to call on every load.
- **Keyboard extension runtime:** requires no DB write — `imKeysForTable(_:)` and the new `imKeyNamesForTable(_:)` already return the correct default in memory for a known table whose stored value is empty. (iOS adds `imKeyNamesForTable`; Android's runtime keyname resolver already covers this.)

## Tasks — iOS

Source: `LimeIME-iOS/Shared/Database/LimeDB.swift`

- [x] **Prep — single source of truth:** extract the inline `array` and `phonetic` `imkeys`/`imkeynames` literals from `applyDefaultMetadataForStandardIM` into constants. Diff them against the existing `ARRAY_KEY`/`ARRAY_CHAR` and `BPMF_KEY`/`BPMF_CHAR` constants; if identical, switch `applyDefaultMetadataForStandardIM` to use those constants; if not, add the missing `*_KEY`/`*_CHAR` constants. End state: no inline default literals remain in `applyDefaultMetadataForStandardIM`.
- [x] Bump `CURRENT_DB_VERSION` `104 → 105`.
- [x] Add `static func ensureStandardIMKeyMetadata(_ db: Database) throws` that iterates the known-table map (dayi / cj family / array / ez / phonetic — **not** array10), and for each table that has ≥1 mapping row, `db.execute` an `INSERT INTO im (code,title,desc)` for `imkeys` / `imkeynames` **only when** no non-empty row for that title/code exists. Writes via the passed `db` only — never `setImConfig` (deadlock; see Decisions). Sources values from the constants produced by the prep task.
- [x] Call `ensureStandardIMKeyMetadata` from `ensureCurrentDatabase()` (after the existing ensure calls, inside the same `dbQueue.write`). Keep the existing `ensureComputerNumKeyboard` / `ensureCangjieSemicolonKeyboards` / `ensureLimeNumSym2Keyboard` calls unconditional (do **not** move them out of the every-load path).
- [x] **Goal 2:** add a `version < 105` branch to `upgradeIfNeeded` that calls `ensureComputerNumKeyboard`, `ensureCangjieSemicolonKeyboards`, `ensureLimeNumSym2Keyboard` (formalizing the post-104 additions) and `ensureStandardIMKeyMetadata`. Additive only — the every-load net stays.
- [x] Add `func imKeyNamesForTable(_ tableName: String) -> String` mirroring `imKeysForTable(_:)` (defaults for dayi / cj family / array; phonetic base; `""` for array10 and unknown tables).
- [x] Add the IM-load-time fallback: call `ensureStandardIMKeyMetadata(table)` at the **prologue of `SetupImController.exportIMAsText(tableNick:)`** (before `getImConfigList`). Host-only by construction; not `setTableName`, not the extension.
- [x] **Keep the seed at `user_version 104`** (`LimeStudio/app/src/main/res/raw/lime.db`, the iOS `Copy lime.db to bundle` source). Do **not** re-stamp it to 105 — a fresh install runs the `< 105` migration on first open, which completes it. (An earlier iteration re-stamped it to 105; reverted, because a 105-stamped-but-incomplete seed breaks fresh installs once the per-load net is gone.) Root `Database/lime.db` is untracked / not a build input; left alone.
- [x] **Restore-guard threshold (found in full-suite verification):** the restore-schema-too-new guard in `SetupImController.validateRestoreDatabase` hardcoded `> 104` in two places, so a 105-stamped backup failed restore with `restoreSchemaTooNew(105)`. Bumped both to `> 105` in lock-step with the version bump. Android has no equivalent version guard (its `schema_version` refs are SQLite's internal cache PRAGMA), so this is iOS-only — **but the Android reviewer should confirm no analogous max-version restore check exists.**
- [x] **`.lime` round-trip test (#176 interaction):** `SetupImControllerTest.testExportLimeRemoveAndReimportRestoresSameCustomEntries` asserted a byte-exact `.lime` round-trip; #176's `getBaseScore` fallback re-scores a scoreless (basescore 0) row on re-import, so `.lime` round-trip is intentionally lossy on basescore (`.limedb` stays lossless). Updated the test to assert identity fidelity (code/word/score) + verify the re-scoring.
- [x] **Verify:** full iOS unit suite (`ios-gate.sh unit LimeTests`) green on a clean simulator — 1114 passed, 0 failed.

## Tasks — Android

Source: `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java`

- [x] Bump `DATABASE_VERSION` `104 → 105`.
- [x] Extract the inline `imkeys`/`imkeynames` standard-table default switch from `importTxtTable`'s finalize block into `applyStandardIMKeyDefaults(SQLiteDatabase db, String table, boolean hasImkeys, boolean hasImkeynames)` that writes via the passed `db`; call it from `importTxtTable` (no behaviour change) and `ensureStandardIMKeyMetadata`. Exclude `array10` (no keynames, mirrors iOS).
- [x] Add `ensureStandardIMKeyMetadata(SQLiteDatabase db)` (iterates the known-table map, backfills only empty/absent `imkeys`/`imkeynames` for tables with mapping rows) and call it from `ensureCurrentDatabase()` (keep `ensureComputerNumKeyboard` / `ensureCangjieSemicolonKeyboards` / `ensureLimeNumSym2Keyboard` unconditional there).
- [x] **Goal 2:** add an `oldVersion < 105` branch in `onUpgrade` calling `ensureComputerNumKeyboard`, `ensureCangjieSemicolonKeyboards`, `ensureLimeNumSym2Keyboard`, and `ensureStandardIMKeyMetadata`. Additive record only; respect the existing #88 comment (do not convert any ensure to pure version-gated). No post-104 *new-IM table* schema exists beyond these keyboard ensures — do not invent one; if a future new-IM `ensure*Schema` is added before 105 ships, include it here.
- [x] Add the IM-load-time fallback at the Android export entry point (the `exportTxtTable` caller in the share/export UI flow — the counterpart to iOS `exportIMAsText`), before the IM config is fetched.
- [x] Confirm the runtime keyname resolver (the `DAYI_KEY`/`DAYI_CHAR` switch, ~line 1800) already covers in-memory rendering for a metadata-less table; no keyboard-side DB write.
- [x] Seed stays at `user_version 104` (shared file with iOS — see the iOS task). `SQLiteOpenHelper.onUpgrade(104, 105)` completes and stamps a fresh install on first open.
- [x] **Verify (compile):** `:app:compileDebugJavaWithJavac` passes. _Instrumentation tests DEFERRED — need a device/emulator (not available in the build environment)._

## Test plan

- [x] **iOS restore round trip (primary):** build an old-style `.limedb` for `dayi` whose `im` table has **no** `imkeys`/`imkeynames`; restore it; open (runs `ensureCurrentDatabase`); assert the `im` table now has `imkeys = DAYI_KEY`, `imkeynames = DAYI_CHAR`; export `.lime`; assert the file contains `@imkeys@|…` and `@imkeynames@|…`.
- [x] **Restore-into-live-DB (fallback):** restore the same table into an already-open current DB without reopening; load/export the IM; assert the metadata is present (proves the load-time fallback, independent of `ensureCurrentDatabase`).
- [x] **No overwrite:** a table with a user-customised non-empty `imkeys` is left unchanged by the backfill.
- [x] **Non-standard table untouched:** a `custom` / imported user table gets no synthetic `imkeys`.
- [x] **In-place upgrade (goal 1+2):** a `user_version = 104` DB with a metadata-less dayi table and no `limenumsym2` keyboard row upgrades to 105 and gains both the imkeys/imkeynames defaults and the post-104 keyboard rows via `upgradeIfNeeded`/`onUpgrade`.
- [x] **Fresh-seed one-time migration:** the bundled seed ships at `user_version 104` and lacks the post-104 `limenumsym2` keyboard; opening it runs the `< 105` migration once and yields a complete 105 DB (`limenumsym2` present). Proves a fresh install is completed by the migration alone, with `ensureCurrentDatabase()` doing no schema work. (`testBundledSeedMigratesToCompleteV105OnFirstOpen`; replaces the obsolete "every-load net repairs stale-105" test.)
- [ ] **Android parity (DEFERRED — needs emulator):** repeat the restore round trip and the extracted-helper behaviour on Android; confirm `importTxtTable` output is unchanged after the extraction.
- [ ] **Cross-platform (DEFERRED — needs both platforms on device):** an Android-exported `.lime` from a repaired dayi table re-imports on iOS with keynames intact, and vice versa.

## Non-goals

- No change to `keyboard`-code export or runtime bookkeeping fields.
- No new seed mapping rows or `im` rows in bundled `lime.db`.
- No backfill for tables without a built-in key map (`custom`, `pinyin`, user-imported tables).
- No `imkeys`/`imkeynames` overwrite when a stored non-empty value already exists.
