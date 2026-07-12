# emoji.db v2 — Data Rebuild & Upgrade Plan

## Context

LimeIME's `emoji.db` ships in [Database/emoji.db](Database/emoji.db) and is consumed by:

- iOS: [LimeIME-iOS/Shared/Database/LimeDB.swift:2104-2143](LimeIME-iOS/Shared/Database/LimeDB.swift#L2104-L2143) (`emojiConvert(_, _)`)
- Android: [LimeStudio/app/src/main/java/org/limeime/limedb/EmojiConverter.java](LimeStudio/app/src/main/java/org/limeime/limedb/EmojiConverter.java) (`convert(tag, emoji)`)

The DB is **outdated and structurally too narrow** to fix the bugs reported in [issue #29](https://github.com/lime-ime/limeime/issues/29) (open since 2018). Verified against today's DB:

```text
sqlite> SELECT COUNT(DISTINCT value) FROM en WHERE value GLOB '*[🇦-🇿]*';
0
sqlite> SELECT tag, value FROM tw WHERE tag IN ('旗', '國旗');
國旗|🎏  旗|📪  旗|📬  旗|📭  旗|🏁  國旗|🏳  國旗|🏴  國旗|🚩
```

Zero regional-indicator country flags (🇯🇵🇺🇸🇮🇹🇰🇷…) are indexed in either `en` or `tw`. The `旗`/`國旗` tags only point at misc flags, never country flags. The reporter — an Array IM user — typed an Array sequence yielding `國旗` and got `🎏` instead of `🇯🇵🇺🇸🇮🇹…`.

This document plans the rebuild plus the cross-platform upgrade path. The keyboard UI work (emoji panel, launcher key, layout edits) lives separately in [docs/EMOJI_KEYBOARD.md](docs/EMOJI_KEYBOARD.md) — that plan **depends on this one shipping first or in the same release**.

## Goal

- Regenerate the emoji dataset from authoritative sources (Unicode 17.0 / Emoji 17.0).
- **emoji.db becomes a build-time data source only** — at runtime, the emoji tables live inside the existing user-writable **lime.db** alongside the IM tables. This reuses the existing schema-upgrade pattern (`LimeDB.onUpgrade`) and the existing user-data backup/restore pattern, no new file lifecycle invented.
- Add **category** (`group_name`, `subgroup`) and **multilingual names** (`name_en`, `name_tw`) so future UI can group and label.
- Add an **FTS index** over name + tag columns so keyword search is fast and broad (tokenized prefix matching with diacritic folding). iOS can use FTS5 through its SQLite stack; Android platform SQLite should create FTS4 directly unless the project later bundles its own SQLite.
- **User score / recents** (the per-emoji `last_used`, `use_count` columns) are written back into a sibling `emoji_user` table in lime.db. This deliberately follows the existing IM user-record convention (`<table>_user`) so emoji recents survive emoji-data replacement the same way IM user records survive table reloads.
- Rewire candidate-bar emoji injection on **both platforms** to use the new FTS-backed `findEmojiForCandidate` API. Issue #29's reporter platform (Android Array IM) gets the broader matching in this release.

## Ship order

This DB plan is an **independent, self-contained PR** — it solves issue #29 on both platforms by itself, no UI changes required. The keyboard-UI work in [docs/EMOJI_KEYBOARD.md](EMOJI_KEYBOARD.md) is a follow-up PR built on top.

1. **PR 1 (this plan)**: build script + new `emoji.db` + FTS-backed schema (FTS5 on iOS, FTS4 on Android platform SQLite) + `findEmojiForCandidate` APIs on both platforms + candidate-bar rewiring + Android cache-wipe hook.
2. **PR 2 ([EMOJI_KEYBOARD.md](EMOJI_KEYBOARD.md))**: iOS + Android emoji keyboard UI — candidate-bar launcher, emoji panel, emoji search mode, recent category, category bookmarks, and backspace.

Splitting this way:

- Lets PR 1 ship to users on its own and immediately fix the 7-year-old #29 complaint.
- Keeps PR 2's review surface focused on UI changes (no SQL/build-script churn mixed in).
- The cross-platform emoji panel UI builds on PR 1's already-in-place FTS-backed search API.

## Non-goals

- Simplified Chinese (`cn`) tags — dropped per v1 scope.
- Pinyin / bopomofo phonetic search columns — dropped per v1 scope.
- New IM-specific emoji curation — the rebuild uses CLDR/emojibase data verbatim.
- Schema versioning beyond `BuildConfig.VERSION_CODE` on Android (no `PRAGMA user_version`, no `SQLiteOpenHelper.onUpgrade` migration).

## Sources

| Source | Purpose | URL |
|---|---|---|
| **emojibase-data** 17.0.0 | Pre-merged CLDR + Unicode JSON, locale-keyed | https://github.com/milesj/emojibase ; `npm view emojibase-data` confirms latest = `17.0.0` |
| **emoji-test.txt** 17.0 | Canonical emoji order, group/subgroup names, qualified status | https://unicode.org/Public/emoji/17.0/emoji-test.txt |
| **CLDR `zh_Hant.xml`** | Fallback for any TW translation missing from emojibase | https://github.com/unicode-org/cldr/tree/main/common/annotations |

Locales loaded: **`en` + `zh-hant`** only. `zh` (Simplified) is intentionally not loaded.

## New schema (added to lime.db)

Three tables and one FTS virtual table, all created inside the existing user-writable **lime.db** by the idempotent open-path check (path 1 below — `createEmojiTables` from `ensureCurrentDatabase()`, not `onUpgrade`). No separate `emoji_meta` table is needed — version tracking reuses the existing `im` table (see path 2).

```sql
-- Immutable emoji dataset. Wholesale-replaced when emoji-data version advances (path 2).
CREATE TABLE emoji_data (
    value      TEXT PRIMARY KEY,    -- glyph (e.g. "😀") — primary key, deduped
    cp         TEXT NOT NULL,       -- comma-sep codepoints e.g. "1F600"
    group_name TEXT NOT NULL,       -- "Smileys & Emotion" — 9 standard CLDR groups
    subgroup   TEXT NOT NULL,       -- "face-smiling"
    sort_order INTEGER NOT NULL,    -- canonical Unicode emoji-test.txt order
    name_en    TEXT,                -- "grinning face" (CLDR tts annotation)
    name_tw    TEXT,                -- "露齒笑臉" (CLDR tts, zh-Hant)
    tags_en    TEXT,                -- pipe-sep keywords ("grin|grinning face|smile")
    tags_tw    TEXT,                -- pipe-sep TW keywords + CJK expansion tokens ("旗|國旗|國|日本" for 🇯🇵)
    version    REAL NOT NULL        -- emoji version when introduced (1.0..17.0)
);
CREATE INDEX idx_emoji_group ON emoji_data(group_name, sort_order);

-- iOS preferred FTS5 index.
CREATE VIRTUAL TABLE emoji_fts USING fts5(
    name_en, name_tw,
    tags_en, tags_tw,
    content='emoji_data',
    content_rowid='rowid',
    tokenize='unicode61 remove_diacritics 1'
);

-- Android platform SQLite index. This is created directly on Android because
-- FTS4 is available in AOSP platform SQLite while FTS5 is not guaranteed.
CREATE VIRTUAL TABLE emoji_fts USING fts4(
    name_en, name_tw,
    tags_en, tags_tw,
    tokenize=unicode61 "remove_diacritics=1",
    content=emoji_data
);

-- User-writable score table. Mirrors the IM backup table convention:
-- logical IM code/table name `emoji`, user-record table `emoji_user`.
-- Preserved across emoji-data version upgrades by the same user-record lifecycle.
CREATE TABLE emoji_user (
    value      TEXT PRIMARY KEY REFERENCES emoji_data(value),
    last_used  INTEGER,             -- unix epoch seconds (0 if never)
    use_count  INTEGER NOT NULL DEFAULT 0
);
```

**`emoji.db` build artifact schema**: same `emoji_data` table (flat data, no FTS) **plus** an `im` table containing metadata rows:

```sql
-- Rows baked into emoji.db at build time:
-- code='emoji', title='version',  desc='17.0'
-- code='emoji', title='name',     desc='Emoji 17.0 Dataset'
-- code='emoji', title='source',   desc='emoji.db'
-- code='emoji', title='amount',   desc='<row count>'
-- code='emoji', title='import',   desc='<build timestamp>'
```

These follow the exact same schema as every other IM database's `im` table, so the emoji refresh can reuse LimeIME's existing ATTACH/copy pattern and copy the `im` metadata rows alongside `emoji_data`. At runtime, lime.db's `im` table holds the canonical emoji-dataset version in the `title='version'` row for `code='emoji'`.

## Build script

New file: `scripts/build_emoji_db.py`.

```python
#!/usr/bin/env python3
# build_emoji_db.py — rebuild Database/emoji.db from CLDR/emojibase + emoji-test.txt
# Usage: python3 scripts/build_emoji_db.py --emoji-test .Codex/txt/emoji-test-17.0.txt --en-json .Codex/txt/emojibase-en-17.0.json --tw-json .Codex/txt/emojibase-zh-hant-17.0.json --output Database/emoji.db --copy-to LimeStudio/app/src/main/res/raw/emoji.db --version 17.0
# Locales: en + zh-Hant (TW). Simplified Chinese is dropped per v1 scope.
```

Steps:

1. Download or vendor pinned source inputs into `.Codex/txt/`: `emoji-test-17.0.txt`, `emojibase-en-17.0.json`, and `emojibase-zh-hant-17.0.json`. Use `emojibase-data@17.0.0` for the two locale JSON files and `https://unicode.org/Public/emoji/17.0/emoji-test.txt` for canonical order.
2. Join by `hexcode` / codepoint sequence; resolve missing TW translations via CLDR `zh_Hant.xml` fallback.
3. Expand Traditional Chinese search keywords before writing `tags_tw`: keep the full CLDR/emojibase keywords (`國旗`, `日本`, `美國`) and add unique single-Han-character tokens from those keywords (`國`, `旗`, `日`, `本`, `美`). This makes one-character candidate-bar searches work with both iOS FTS5 and Android FTS4, because SQLite `unicode61` prefix matching does not find characters in the middle of a multi-character CJK token unless that character is indexed as its own token.
4. Write `Database/emoji.db` containing one table named `emoji_data` with the same columns as `emoji_data` above (no FTS, no views, no user table — those all live in lime.db at runtime). The shipped emoji.db is purely a data source for the runtime import.
5. Bump `LimeDB.DATABASE_VERSION` and the new `EMOJI_DATA_VERSION` constant (see "Upgrade path" below) so the next app launch triggers schema/data migration.
6. Run once per emoji-version bump; commit the resulting `Database/emoji.db` plus the corresponding `LimeStudio/app/src/main/res/raw/emoji.db`. If an iOS bundle copy is later added as a standalone file, pass another `--copy-to <path>` for that destination.

## Keyword matching

Both query paths use the same logical FTS index inside lime.db. iOS creates `emoji_fts` as FTS5; Android platform SQLite creates `emoji_fts` as FTS4. Callers always query the same logical table name.

The current `EmojiConverter.convert(tag, EMOJI_TW)` does `WHERE tag = ?` — exact match only. Issue #29's reporter typed an Array sequence yielding `國旗` and got only `🎏` because no other rows had `tag = '國旗'` exactly. Even after the data rebuild lights up the right tags, exact-match still misses partials (`旗` ≠ `國旗`, `笑` ≠ `笑容`).

FTS with `unicode61` tokenization plus build-time CJK keyword expansion fixes that:

- English splits at word boundaries (`flag-japan` → `flag`, `japan`).
- CJK keywords are indexed in both full and single-character forms (`國旗|國|旗`). So a prefix query such as `國*` returns emoji tagged with `國旗`, `美國`, `中華民國`, etc., and `旗*` returns every emoji whose expanded `tags_tw` includes `旗`.
- `café` → `cafe` at index time, so searching `cafe` finds `café` rows.

### Path 1 — Panel search bar (new in v1)

```sql
SELECT d.value FROM emoji_fts f
JOIN emoji_data d ON d.rowid = f.rowid
LEFT JOIN emoji_user u ON u.value = d.value
WHERE emoji_fts MATCH ?            -- e.g. '國*' or 'flag*' or '笑*'
ORDER BY
    (u.last_used IS NULL),         -- recent picks first
    u.last_used DESC,
    d.sort_order ASC               -- canonical Unicode order for the rest
LIMIT 200;
```

Order is deterministic and easy to reason about. No BM25 ranking needed.

Query construction has two entry points. They share sanitization and FTS escaping, but **do not share the same minimum length rule**:

- Trim whitespace and split ASCII whitespace into terms.
- Escape or drop FTS control characters from user/candidate text before building `MATCH`.
- If sanitization leaves no searchable token, return an empty result instead of running a broad query.

**Emoji panel search field** (`searchEmoji(query, locale)`) is explicit search, so it starts matching from a single character:

- English: keep one-character ASCII alphabetic tokens and append `*` (`c` → `c*`, `cr` → `cr*`, `cry` → `cry*`).
- Chinese/CJK: keep one-character CJK tokens and append `*` (`國` → `國*`, `笑` → `笑*`).

**Inline candidate-bar emoji injection** (`findEmojiForCandidate`) is passive suggestion, so English is intentionally less eager:

- English in the Chinese IM candidate bar or English keyboard candidate path: drop one-character ASCII alphabetic tokens before building the query (`c` returns no inline emoji); start at two characters (`cr` → `cr*`, `cry` → `cry*`, `flag` → `flag*`).
- Chinese IM candidates: keep one-character CJK tokens because they are real IM candidates (`國` → `國*`, `笑` → `笑*`).
- For multi-character Chinese candidates, query both the full candidate and the first Chinese character. Example: candidate `國旗` builds `國旗* OR 國*`; candidate `日本` builds `日本* OR 日*`. This preserves exact/full-word relevance while still finding emoji through the first Chinese character expansion.

### Path 2 — Candidate-bar emoji injection (rewired in v1)

New API on both platforms:

- iOS: `LimeDB.findEmojiForCandidate(_ candidate: String, locale: EmojiLocale, limit: Int = 8) -> [String]`
- Android: `LimeDB.findEmojiForCandidate(String candidate, EmojiLocale locale, int limit)`

Both run the same FTS query above (with the limit applied). Caller rewires:

- iOS: `SearchServer.injectEmoji` ([SearchServer.swift:722-754](LimeIME-iOS/Shared/Search/SearchServer.swift#L722-L754)) calls `findEmojiForCandidate` instead of `emojiConvert`.
- Android: `LIMEService.java` candidate-bar emoji injection (around L2471-2530 + L2665-2720) calls `findEmojiForCandidate` instead of `EmojiConverter.convert`.

Concrete behavior change after v1:

- Candidate `國旗` → all 250+ regional-indicator country flags 🇯🇵🇺🇸🇮🇹… (was: 🎏 only)
- Candidate `國` → country flags and any other upstream TW keywords expanded to `國` (was: nothing)
- Candidate `笑` → 😀😄😁🤣😆😅 family (was: only exact-`笑` rows)

The legacy `EmojiConverter.convert(tag, emoji)` stays in place for any other callers; it's reimplemented as a thin wrapper over a `WHERE tag IN (...)` style join against `emoji_data` so legacy semantics are preserved.

### FTS backend by platform

FTS5 is preferred on iOS because it is available through the system SQLite stack. Ranking still does not depend on BM25 — order comes from `emoji_user.last_used` (recents) and `emoji_data.sort_order` (canonical Unicode).

Android platform SQLite should create `emoji_fts` as FTS4 directly. FTS4 is available in AOSP platform SQLite; FTS5 is compile-option dependent and is not guaranteed by Android API level. The public API and SQL query shape stay the same; only the virtual table module differs by platform.

No `androidx.sqlite:sqlite-bundled` dependency is required for v1. If the project later wants Android FTS5 specifically, bundled SQLite should be considered as a separate decision.

## Upgrade path

Two distinct mechanisms, both reusing existing LimeIME patterns. **emoji.db at runtime is gone** — emoji tables live inside lime.db and follow lime.db's lifecycle.

### Path 1 — emoji schema creation (open-path, NOT `onUpgrade`)

> **Updated:** emoji schema creation is **state-gated on every open**, not version-gated in
> `onUpgrade`. The original plan put a `if (oldVersion < 103) createEmojiTables(...)` line in
> `onUpgrade`; that line has since been **removed** because it was insufficient (a restored
> DB can claim a current `user_version` but carry stale/missing emoji schema, skipping
> `onUpgrade` — the #88 crash family). See the shared rule in
> [ENG_AUTO_COMPLETION.md](ENG_AUTO_COMPLETION.md) "Bundled-payload version principle".

Emoji tables are created idempotently from the open-path funnel `ensureCurrentDatabase()`
([LimeDB.java](../LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java)), which
runs on **every open, restore, and factory reset**:

```java
// ensureCurrentDatabase() -> refreshEmojiDataIfNeeded() -> ensureEmojiTables():
createEmojiTables(db, /*forceRecreate=*/ false);   // CREATE TABLE IF NOT EXISTS — idempotent
```

`createEmojiTables` creates `emoji_data`, `idx_emoji_group`, the `emoji_fts` virtual table
(Android platform FTS4, iOS FTS5), and `emoji_user`, all with `IF NOT EXISTS` / usability
probe so it is safe to run on every open. Because it is gated on **actual schema state**
(missing/unusable tables are repaired) rather than `PRAGMA user_version`, a restored DB that
lies about its version is still fixed. `LimeDB.DATABASE_VERSION` is currently 104; the emoji
schema does **not** depend on that number.

iOS GRDB runs the equivalent idempotent ensure in `ensureCurrentDatabase()`; the same SQL
applies.

Android should also remove any stale standalone runtime copy at `context.getDatabasePath("emoji.db")` after the schema exists and the emoji refresh path is wired. This is the "Android cache-wipe hook" in the ship-order list: the bundled `R.raw.emoji` remains as the read-only import source, but the old writable `emoji.db` copy is obsolete and should not continue serving queries.

### Path 2 — emoji-data version refresh (first-time and future Emoji 18, 19, …)

A constant `EMOJI_DATA_VERSION = "17.0"` is baked into the app at build time (Java constant on Android, Swift constant on iOS). At app start (early in `LIMEService.onCreate` on Android, equivalent init point on iOS), check the `im` table:

```sql
SELECT desc FROM im WHERE code = 'emoji' AND title = 'version'
```

- If no row exists (first run after path 1 created the empty tables) → run the import.
- If `desc` differs from `EMOJI_DATA_VERSION` (a new emoji.db shipped, e.g. Emoji 18 release) → run the import.
- If equal → skip; nothing to do.

The import reuses the existing IM user-record lifecycle: keep the `_user` table intact while the immutable base data is replaced, then reconcile it after import. For emoji, `emoji_user` is the persistent user-record table analogous to an IM table's `<table>_user` backup table; it is **not** cleared during refresh. The refresh still needs an emoji-specific ATTACH/copy method because `emoji_data` is not a normal IM mapping table and `emoji_user` has emoji-specific columns, but the preservation rule is the same as IM reloads: replace base data, preserve user records, drop only user rows whose base record no longer exists. Because emoji.db ships with an `im` table containing the metadata rows (including `title='version', desc='17.0'`), the refresh copies both the `emoji_data` rows and the `im` version row in the same transaction:

```sql
BEGIN;
-- 1. Keep emoji_user intact. It is the emoji user-record table, analogous to
--    an IM table's <table>_user backup table.

-- 2. Clear emoji_data, the FTS index, and the im metadata rows for code='emoji'.
DELETE FROM emoji_data;
DELETE FROM im WHERE code = 'emoji';

-- 3. Re-import via importDb() pattern: ATTACH emoji.db, copy emoji_data + im rows.
ATTACH DATABASE '<path-to-bundled-emoji.db>' AS src;
INSERT INTO emoji_data SELECT * FROM src.emoji_data;
INSERT INTO emoji_fts(emoji_fts) VALUES('rebuild');   -- iOS FTS5 and Android FTS4 both support rebuild
INSERT INTO im (code, title, desc, keyboard, disable, selkey, endkey, spacestyle)
    SELECT code, title, desc, keyboard, disable, selkey, endkey, spacestyle
    FROM src.im;                   -- copies version/name/source/amount/import rows
DETACH DATABASE src;

-- 4. Reconcile preserved user records. Keep recents only for emoji still present
--    in the freshly imported base dataset.
DELETE FROM emoji_user
WHERE value NOT IN (SELECT value FROM emoji_data);
COMMIT;
```

The `im` table's `title='version'` row for `code='emoji'` is stamped by step 3 (copied from emoji.db's `im` table), not by a separate INSERT. This reuses LimeIME's existing IM-table user-record preservation model instead of inventing a separate emoji metadata or backup mechanism. The only thing that changes between Emoji 17.0 → 18.0 is the constant `EMOJI_DATA_VERSION` and the shipped `R.raw.emoji` / iOS bundle file.

### Bundled emoji.db location

- iOS: `LimeKeyboard.appex/emoji.db` (app bundle, read-only). The runtime `ATTACH DATABASE` in path 2 attaches this read-only file as the import source.
- Android: `R.raw.emoji` extracted to a temporary file before `ATTACH` (Android raw resources can't be opened by SQLite directly; existing `LIMEUtilities.copyRAWFile` already handles this for other DBs).

The emoji.db file is never written to and never used as a runtime DB.

## Risks / open questions

- **emoji.db build reproducibility**: pin upstream `emoji-test.txt` version (17.0) and `emojibase-data@17.0.0` so the script produces a deterministic build artifact across machines. Record both versions in the script header.
- **Emoji 17.0 font fallback (accepted by user)**: iOS 19+ ships Emoji 17.0 system fonts; iOS 18 users will see `▢` for 17.0-only glyphs. Same on Android pre-15. Confirmed acceptable.
- **CN drop impact on existing candidate-bar emoji**: existing `LimeDB.emojiConvert(EMOJI_CN)` calls return empty after v2 ships. Audit `SearchServer.injectEmoji` ([SearchServer.swift:722-754](LimeIME-iOS/Shared/Search/SearchServer.swift#L722-L754)) to confirm the EN+TW path remains correct when CN returns nothing — likely fine since the existing logic chains EN → TW → CN as a fallback.
- **Path 2 timing**: the emoji-data version check / import must run early — before any `findEmojiForCandidate` or candidate-bar injection — otherwise the first call after an upgrade hits empty/stale tables. Ideally in `LIMEService.onCreate` (Android) and the LimeDB initializer (iOS), gated behind a dispatch-once-per-process flag.
- **Path 2 atomicity**: the entire preserve-user-records → clear base data → re-import → reconcile sequence is wrapped in a single SQL transaction. If the app crashes mid-import, the previous data version remains intact and path 2 retries on next launch.
- **lime.db growth**: adding `emoji_data` (~1500 rows × ~10 columns), `emoji_fts` (iOS FTS5 or Android FTS4 index over those rows), and `emoji_user` adds maybe 1–2 MB to lime.db. Acceptable; lime.db already varies in size across IM types.
- **Android FTS implementation**: Android platform SQLite uses FTS4 directly. Keep this encapsulated behind helper methods that create, rebuild, and query `emoji_fts` by logical table name so callers do not branch on the backend.
- **ATTACH on iOS GRDB**: confirm GRDB's `DatabaseQueue` allows `ATTACH DATABASE` mid-session. Standard SQLite supports it; verify GRDB doesn't restrict it.
- **`R.raw.emoji` extraction on Android**: Android raw resources aren't files SQLite can open directly. Path 2 must extract `R.raw.emoji` to a temporary file (e.g. `cacheDir`) before `ATTACH`, then delete the temp on completion. `LIMEUtilities.copyRAWFile` is already used elsewhere for this.

## Verification

End-to-end manual test on **both** platforms (iOS bundles new build, Android installs upgraded APK over an existing install with cached `emoji.db`):

### Build artifact (run once)

1. Build script produces `Database/emoji.db` and the matching `LimeStudio/app/src/main/res/raw/emoji.db` copy. Additional bundle copies can be produced by passing extra `--copy-to` destinations.
2. `sqlite3 Database/emoji.db ".schema"` shows one `emoji_data` table and one `im` table (no FTS, no views) — the `im` table holds the version/name/source metadata rows for `code='emoji'`.
3. Spot-check 5+ post-Unicode-13 entries: 🫠 (14.0), 🫨 (15.0), 🪿 (15.0), 🫩 (16.0), and at least one 17.0 row, all present with correct `version`.
4. Sanity-check tags: `SELECT COUNT(DISTINCT value) FROM emoji_data WHERE tags_en LIKE '%|flag|%' OR tags_en LIKE 'flag|%' OR tags_en LIKE '%|flag' OR tags_en = 'flag';` → ≥ 250.

### Path 1 — lime.db schema upgrade

5. **Android**: install the APK. On first launch, the open-path `ensureCurrentDatabase()` → `refreshEmojiDataIfNeeded()` runs; verify via `adb shell sqlite3 /data/data/.../databases/lime.db ".schema"` that `emoji_data`, `emoji_fts`, `emoji_user` tables now exist. Old IM tables (mappings, related, im, etc.) are unchanged.
6. **iOS**: equivalent — install a fresh build, open lime.db (the writable copy in App Group container), confirm the same three new tables exist with the existing IM tables untouched.

### Path 2 — emoji-data version refresh

7. **First-time install** (or first launch after path 1 created empty tables): `SELECT desc FROM im WHERE code='emoji' AND title='version'` returns no rows. App start triggers import. After import: `SELECT COUNT(*) FROM emoji_data` ≥ 1500; `SELECT COUNT(*) FROM emoji_fts` matches; `SELECT desc FROM im WHERE code='emoji' AND title='version'` = `'17.0'`.
8. **Refresh on emoji-data version bump** (simulate by manually updating `im` row to `desc='16.0'` and restarting): app start detects mismatch, runs path 2. Verify (a) rows manually inserted into `emoji_user` for emoji that still exist in the new `emoji_data` survive the refresh, (b) rows manually inserted into `emoji_user` for deleted/nonexistent emoji are pruned, (c) `emoji_data` now reflects the new dataset, (d) `im` version row updated to `'17.0'`.
9. **Atomicity check**: kill the app process mid-import (logcat or breakpoint just after the `BEGIN`). On next launch, `emoji_data` should be either the old complete state or the new complete state, never half. The `im` version row reflects whichever state is actually present.

### Issue #29 fixes (functional)

10. On the **phonetic** or **Array** layout, type a sequence whose Chinese candidate is `國旗` → country flags 🇯🇵🇺🇸🇮🇹🇰🇷… appear among injected emoji candidates in the candidate bar (was 🎏 only). Repeat for `日本` → 🇯🇵, `美國` → 🇺🇸, `笑` → 😀😄😁🤣 family, `國` (single char) → country flags and any other upstream TW keywords expanded to `國` (broader-match validation).
11. In English inline candidate paths (Chinese IM candidate bar and English keyboard candidate path), verify `cry` emoji are found by `cr` and `cry`, but not by bare `c`.
12. In the emoji keyboard search box, verify prefix search starts from one English character: `c`, `cr`, and `cry` all search as `c*`, `cr*`, and `cry*`.
13. In Chinese IM inline candidate injection, verify matching also runs on the first Chinese character of a longer candidate: `國旗` searches with both `國旗*` and `國*`; `日本` searches with both `日本*` and `日*`.
14. Confirm `SearchServer.injectEmoji` (iOS) and the Android candidate-injection sites now call `findEmojiForCandidate` (grep for the new symbol).
15. Mark issue #29 fixed in the PR description.

### Recents writeback (sanity)

16. Tap an emoji from the candidate bar or emoji panel. Verify `emoji_user` now has a row for that glyph with `last_used` ≈ now and `use_count = 1`. Tap again → `use_count = 2`, `last_used` updated.
17. Search for that emoji's category (e.g. tap a recently picked smiley, then search `smile`) → it appears at the top because of the `ORDER BY last_used DESC` clause.

## Decisions — resolved

1. **Runtime DB location**: emoji tables live inside the existing **lime.db**. Standalone `emoji.db` is a build-time data source only.
2. **Locales**: `en` + `zh-Hant` (TW). Simplified Chinese (`cn`) dropped, no `tags_pinyin`, no `tags_bopomofo`.
3. **Target Emoji version**: 17.0. iOS 18 / Android pre-15 show `▢` for 17.0-only glyphs — accepted.
4. **Search backend**: iOS FTS5, Android platform SQLite FTS4. No bundled-SQLite dep for v1.
5. **Ordering**: `(emoji_user.last_used IS NULL), emoji_user.last_used DESC, emoji_data.sort_order ASC`. Recents first, then canonical Unicode order.
6. **Candidate-bar broadening — both platforms**: rewired to `findEmojiForCandidate` (FTS-backed; iOS FTS5, Android FTS4) on iOS (`SearchServer.injectEmoji`) and Android (`LIMEService` injection sites L2471-2530 + L2665-2720).
7. **Recents writeback**: `emoji_user` table inside lime.db, written on every emoji insertion. Reuses LimeIME's existing IM-table user-record lifecycle and `<table>_user` convention.
8. **English matching thresholds**: inline candidate-bar emoji injection starts English prefix matching at two characters (`cr*`, `cry*`) and deliberately ignores bare one-character English (`c`). The emoji keyboard search box starts at one character (`c*`, `cr*`, `cry*`) because the user is explicitly searching.
9. **Chinese candidate matching**: Chinese inline candidate injection keeps one-character CJK matching and also queries the first Chinese character of longer candidates (`國旗* OR 國*`, `日本* OR 日*`).
10. **Upgrade path 1**: emoji schema is created idempotently in the open-path `ensureCurrentDatabase()` → `refreshEmojiDataIfNeeded()` → `createEmojiTables(db, false)`, gated on actual schema state (not `PRAGMA user_version`), so it runs on every open/restore/factory-reset. The original version-gated `onUpgrade(oldVersion<103)` emoji line was removed (insufficient for restores that lie about their version — the #88 family). See the shared rule in [ENG_AUTO_COMPLETION.md](ENG_AUTO_COMPLETION.md) "Bundled-payload version principle".
11. **Upgrade path 2**: emoji-data version refresh detected via `im` table (`code='emoji', title='version'`), triggered by mismatch with build-time constant `EMOJI_DATA_VERSION`. emoji.db ships with its own `im` metadata rows; the emoji-specific refresh method uses the existing ATTACH/copy pattern to copy them alongside `emoji_data`. Reuses LimeIME's existing IM-table user-record preservation approach by keeping `emoji_user` intact and reconciling it after import.

## File map

| Concern | File | Notes |
|---|---|---|
| Build script (new) | `scripts/build_emoji_db.py` | Generates `Database/emoji.db` from emojibase-data + emoji-test.txt + CLDR fallback. One `emoji_data` table + one `im` table with version/name/source rows for `code='emoji'`; no FTS, no views. |
| Shared DB asset | `Database/emoji.db` and `LimeStudio/app/src/main/res/raw/emoji.db` | Updated by the build script (`--copy-to` can add more destinations if a future bundle copy becomes a standalone file) |
| iOS: schema migration | [LimeIME-iOS/Shared/Database/LimeDB.swift](LimeIME-iOS/Shared/Database/LimeDB.swift) | Bump lime.db schema version; add migration that creates `emoji_data` / `emoji_fts` (FTS5) / `emoji_user`. Path 1. |
| iOS: data version refresh | [LimeIME-iOS/Shared/Database/LimeDB.swift](LimeIME-iOS/Shared/Database/LimeDB.swift) | At init, query `im` table (`code='emoji', title='version'`) vs `EMOJI_DATA_VERSION` constant; if different, ATTACH bundled emoji.db and run preserve-user-records / clear / import / reconcile transaction (copies `im` rows + `emoji_data`, preserves `emoji_user`). Path 2. |
| iOS: new search API | [LimeIME-iOS/Shared/Database/LimeDB.swift](LimeIME-iOS/Shared/Database/LimeDB.swift) | Add `findEmojiForCandidate(_:locale:limit:)`, `searchEmoji(_:locale:)`, `loadAllEmoji()`, `recordEmojiUsage(_:)` (writes to `emoji_user`) |
| iOS: candidate-bar caller | [LimeIME-iOS/Shared/Search/SearchServer.swift:722-754](LimeIME-iOS/Shared/Search/SearchServer.swift#L722-L754) | Rewire `injectEmoji` to call `findEmojiForCandidate` instead of `emojiConvert` |
| Android: schema creation | [LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java](LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java) (`ensureCurrentDatabase` → `refreshEmojiDataIfNeeded` → `createEmojiTables`) | Idempotent open-path create of the three emoji tables (`emoji_data`, `emoji_fts`, `emoji_user`), gated on schema state not `user_version`. `emoji_fts` uses Android platform FTS4 directly. No `onUpgrade` emoji line. Path 1. |
| Android: data version refresh | `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java` (new method, called from `LIMEService.onCreate`) | Query `im` table (`code='emoji', title='version'`) vs `BuildConfig.EMOJI_DATA_VERSION`; if different, run an emoji-specific ATTACH/copy refresh that preserves `emoji_user` and prunes only orphaned rows after import. Path 2. |
| Android: new search API | [LimeStudio/app/src/main/java/org/limeime/limedb/EmojiConverter.java](LimeStudio/app/src/main/java/org/limeime/limedb/EmojiConverter.java) | Refactor: stop being a separate `SQLiteOpenHelper`; become a thin façade that runs queries against lime.db's emoji tables. Adds `findEmojiForCandidate`, `search`, `loadAll`, `recordUsage`. Legacy `convert(tag, emoji)` stays as a wrapper. |
| Android: candidate-bar caller | [LimeStudio/app/src/main/java/org/limeime/LIMEService.java](LimeStudio/app/src/main/java/org/limeime/LIMEService.java) (around L2471-2530 + L2665-2720) | Rewire candidate-bar emoji injection to call `findEmojiForCandidate` instead of `EmojiConverter.convert` |
| Android: drop runtime emoji.db copy | [LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java:4699-4710](LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java#L4699-L4710) (`checkEmojiDB`) | Now obsolete — emoji is in lime.db. Either remove or repurpose to call the path-2 refresh check. |
| Android: drop separate emoji.db restore | [LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java:5706-5710](LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java#L5706) (in `restoredToDefault`) | Remove the standalone-emoji.db lines; emoji data refresh happens via path 2 instead |
| iOS bundle inclusion | LimeIME-iOS Xcode project (`LimeKeyboard` extension target → "Copy Bundle Resources") | `emoji.db` already included as a build asset; no build-system change needed |
