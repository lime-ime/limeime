# LimeDB Gap Analysis — Java vs Swift

**Source of truth:** `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java` (~5,700 lines)  
**iOS port:** `LimeIME-iOS/Shared/Database/LimeDB.swift`  
**Analysis date:** 2026-04-09  
**Goal:** 100% functional parity with Java source

---

## Background

Two gap analyses were run: one before the 2026-04-09 fix session and one after. The session fixed all
six original gap categories. This document records the **post-fix state** — what was fixed, what
remains open, and what is intentionally not ported.

---

## What Was Fixed (2026-04-09 Session)

| Gap | Fix Applied |
|-----|-------------|
| Tone detection in `getMappingByCode` for phonetic table | Swift now checks `code3r` column when no tone, strips mid-tones when tone not in last position |
| ETEN/ETEN26/HSU remap table values | Filled from Java source (were identity/passthrough placeholders) |
| ETEN26/HSU dual-key tables | Added `ETEN26_DUALKEY`, `ETEN26_DUALKEY_REMAP`, `HSU_DUALKEY`, `HSU_DUALKEY_REMAP` constants |
| Missing constants | Added `DUALCODE_COMPOSING_LIMIT = 16`, `DUALCODE_NO_CHECK_LIMIT = 2` |
| Blacklist cache | Added `blackListCache: [String: Bool]`, `cacheKey()`, `checkBlackList()`, `removeFromBlackList()` |
| Dual-code expansion | Added `preProcessingForExtraQueryConditions()`, `buildDualCodeList()`, `expandDualCode()`, `buildNewCode()` for ETEN26 and HSU phonetic on iOS |
| `getMappingByCode` dual-code wiring | Now calls `preProcessingForExtraQueryConditions()` before the SQL query; injects extra OR clauses |
| `resetCache()` | New method clearing all caches (blacklist, remap, dual-code, key-name) |
| `restoredToDefault()` | Resets all mapping table scores to 0, deletes related-phrase learning, clears caches |
| DB hold wait mechanism | `checkDBConnection()` now polls up to 5 s on background threads; returns immediately on main thread |
| `phoneticKeyboardType` `didSet` | Now also clears `keysDualMap` and `blackListCache` on type change |
| New instance fields | Added `keysDualMap`, `lastCode`, `lastValidDualCodeList`, `blackListCache` |

---

## Remaining Gaps

### Section 1 — Methods Missing in Swift

| Java Method | Line | Swift Status | Notes |
|---|---|---|---|
| `recordFromCursor(Cursor)` | 473 | **N/A** | Android Cursor helper; GRDB row-mapping replaces it |
| `recordListFromCursor(Cursor)` | 494 | **N/A** | Same |
| `relatedFromCursor(Cursor)` | 513 | **N/A** | Same |
| `relatedListFromCursor(Cursor)` | 529 | **N/A** | Same |
| `getCursorString(Cursor, String)` | 428 | **N/A** | Same |
| `getCursorInt(Cursor, String)` | 450 | **N/A** | Same |
| `onUpgrade(SQLiteDatabase, int, int)` | 609 | **MISSING** | Swift `migrate()` creates tables but has no versioned upgrade path for existing older DBs |
| `getDictionaryAll()` | 2878 | **MISSING** | Returns all dictionary entries; requires `dictionary` table to be bundled on iOS |
| `identifyDelimiter(List<String>)` | 4109 | **MISSING** | Java auto-detects `\|`, `\t`, `,`, space as delimiter; Swift hardcodes `\t` |
| `checkEmojiDB()` | 4675 | **MISSING** | Lazy-init for emoji converter; `emoji.db` is already bundled — implementation needed |
| `checkHanDB()` | 4688 | **MISSING** | Lazy-init for han converter; iOS will use built-in `CFStringTransform` instead of `hanconvertv2.db` — implementation needed |
| `setPhysicalKeyboardPressed(boolean)` | — | **N/A** | iOS has no hardware keyboard |

---

### Section 2 — SQL Query Differences

| Query | Java | Swift | Gap |
|---|---|---|---|
| `importTxtTable` pipe-format | Reads `code\|word\|score\|basescore` lines after delimiter auto-detection | `importTxtFile` only parses `%chardef begin/end` CJ format with `\t` separator | Pipe-delimited `.txt` mapping files silently import nothing |
| `getMappingByCode` phonetic tone | `code3r` column fallback + mid-tone strip | **Fixed — same** | None |
| `getRelatedPhrase` two-branch | Two-branch: raw SQL with OR for `pword.length > 1` | **Same logic** | None |
| Dictionary FTS | `SELECT word FROM dictionary WHERE word MATCH '...'` | Stub: checks `tableExists("dictionary")`, returns `[]` gracefully | Requires `dictionary` table on iOS |
| Backup/restore | ATTACH/DETACH SQL | VACUUM INTO / ATTACH | Different mechanism, same result |

---

### Section 3 — Behavioral Differences

| Aspect | Java | Swift | Gap |
|---|---|---|---|
| **Text import delimiter** | `identifyDelimiter()` auto-detects `\|`, `\t`, `,`, space | Hardcoded `\t` only | Pipe-delimited user mapping files import nothing |
| **Sort suggestions preference** | `getMappingByCode` checks `mLIMEPref.getSortSuggestions()` and `getPhysicalKeyboardSortSuggestions()` | No preference check; always sorts by score+basescore | User cannot turn off score-based sorting |
| **Similar code candidates cap** | `buildQueryResult` reads `mLIMEPref.getSimilarCodeCandidates()` to cap partial matches | No cap | Swift may return more partial candidates than expected |
| **Learn related words gate** | `addOrUpdateRelatedPhraseRecord` gated by `mLIMEPref.getLearnRelatedWord()` | Always learns | User cannot disable related-phrase learning |
| **Chinese symbol filtering** | Strips `ChineseSymbol.chineseSymbols` before inserting into `related` table | No filtering | Punctuation may be recorded as related phrases |
| **Reverse lookup table preference** | `getCodeListStringByWord` reads `mLIMEPref.getRerverseLookupTable()` | Always uses `currentTableName` | iOS ignores reverse-lookup table preference |
| **Import cancellation** | `importTxtTable` has `threadAborted` flag to cancel mid-import | `importTxtFile` has no cancellation | iOS import cannot be aborted once started |
| **Import background thread** | `importTxtTable` spawns its own `Thread` with `LIMEProgressListener` callbacks | `importTxtFile` is synchronous; caller must dispatch | iOS caller must manage threading |
| **Physical keyboard paths** | Full code remapping for Milestone, DesireZ, Chacha, XperiaPro | No-op | N/A on iOS |
| **Debug logging** | `DEBUG` constant controls extensive `Log.i` output | No logging | Not a functional gap |

---

### Section 4 — Data Model Differences

| Item | Java | Swift | Gap |
|---|---|---|---|
| `blackListCache` thread safety | `ConcurrentHashMap<String, Boolean>` | `[String: Bool]` (not intrinsically thread-safe) | Concurrent mutation could corrupt the cache; GRDB serial queue mitigates in practice but not formally safe |
| `lastValidDualCodeList` visibility | Readable by `SearchServer` / `DBServer` callers to display composing-code hints | `private var lastValidDualCodeList: String?` — inaccessible to callers | Composing-hint display cannot read validated dual-code string |
| `keysReMap` structure | `HashMap<String, HashMap<String, String>>` — string keys | Split into `remapCacheInitial` + `remapCacheFinal` (`[String: [Character: Character]]`) | Same semantics; no functional gap |
| `FIELD_*` constants | Defined as `public static final String` | Inlined as string literals in queries | Same DB schema; no functional gap |

---

### Section 5 — Threading / Async Differences

| Aspect | Java | Swift | Gap |
|---|---|---|---|
| `synchronized` write methods | `addOrUpdateMappingRecord`, `addOrUpdateRelatedPhraseRecord`, `addScore` are `synchronized` | No `synchronized`; GRDB `DatabaseQueue` serializes all writes | Equivalent safety via GRDB; no functional gap |
| Import threading | Explicit `Thread` with `threadAborted` cancellation and `LIMEProgressListener` callbacks | Synchronous with optional `((Int) -> Void)` progress closure | No cancellation; caller must manage background dispatch |
| DB hold wait | Up to 5,000 ms (50 × 100 ms), immediate on main Looper | Up to 5,000 ms (50 × 100 ms), immediate on `Thread.isMainThread` | Fixed — equivalent |

---

### Section 6 — Feature / Config Gaps

| Feature | Java | Swift | Gap |
|---|---|---|---|
| **Han conversion** (`hanConvert`) | Full character-by-character TC↔SC via `hanconvertv2.db` (`TCSC` / `SCTC` tables) | Stub: `return input` | iOS will use built-in `CFStringTransform` (`kCFStringTransformToSimplifiedChinese` / `ToTraditionalChinese`) — no external DB needed |
| **Emoji conversion** (`emojiConvert`) | Lookup by tag via `emoji.db` (`cn` / `en` / `tw` tables) | Stub: `return []` | `emoji.db` is already bundled on iOS — implementation needed |
| **Base score lookup** (`getBaseScore`) | Queries `TCSC.score` in `hanconvertv2.db` to seed `basescore` on import | Stub: `return 0` | `hanconvertv2.db` is not being bundled; `getBaseScore` will need an alternative approach (e.g. fixed score table or omit entirely) |
| **Dictionary suggestions** (`getEnglishSuggestions`, `getDictionaryAll`) | Queries `dictionary` FTS table | Stub: returns `[]` | `dictionary` table not bundled on iOS |
| **Delimiter auto-detection** | `identifyDelimiter()` inspects first lines of import file | Not implemented | See TODO below |
| **Schema version upgrade** | `onUpgrade(db, old, new)` adds `basescore` column, inserts `wb`/`hs` keyboard rows for DB < v102 | `migrate()` creates tables from scratch; no upgrade logic | Existing older DBs missing columns or keyboard rows are silently broken |
| **Sort preference** | `getSortSuggestions()` + `getPhysicalKeyboardSortSuggestions()` | No preference | User cannot control suggestion sort order |
| **Learn related preference** | `getLearnRelatedWord()` gates `addOrUpdateRelatedPhraseRecord` | Always on | User cannot disable learning |
| **Chinese symbol filter** | `ChineseSymbol.chineseSymbols` filtered before `related` insert | Not implemented | Punctuation recorded as related phrases |

---

## Converter Databases

### `hanconvertv2.db` — NOT being bundled on iOS
Android bundles this as a raw resource for TC↔SC conversion and base-score lookup. On iOS, **TC↔SC conversion will use the built-in `CFStringTransform` API** (`kCFStringTransformToSimplifiedChinese` / `kCFStringTransformToTraditionalChinese`), which covers the `hanConvert` use case without any external DB.

The `getBaseScore` use case (seeding `basescore` on import) has no direct iOS equivalent without the DB. Options: omit basescore seeding on import (scores default to 0 and learn over time), or ship a compact static lookup table in code.

### `emoji.db` — already bundled on iOS
- **Tables:** `cn`, `en`, `tw` — one table per locale
- **Schema:** `tag TEXT, value TEXT` — `tag` is a search keyword (e.g. `笑`), `value` is one emoji character
- **Used for:** `emojiConvert(String, int)` — given a tag and locale (CN=3, EN=1, TW=2), returns a list of emoji `Mapping` objects for the candidate bar
- **Status:** DB is present; `emojiConvert` in Swift is still a stub returning `[]` — implementation needed

---

## TODO — All Open Items

- [x] **Fix `importTxtFile` delimiter auto-detection**  
  `identifyDelimiter()` added: checks first data line for `|`, `\t`, `,`, space in priority order. `importCancelled` flag added; `importTxtFileAsync` wrapper added.

- [x] **Add schema version upgrade in `migrate()`**  
  `upgradeIfNeeded()` reads `PRAGMA user_version`; for version < 102 adds `basescore` column to all mapping tables and inserts default `wb`/`hs` keyboard rows. Stamps version 102 on completion.

- [x] **Expose `lastValidDualCodeList`**  
  Changed to `private(set) var` — readable by `SearchServer` after `getMappingByCode`.

- [x] **Add sort-suggestions preference**  
  `SearchServer.sortSuggestions: Bool` (default `true`) applied in `getMappingByCode`; wired from `KeyboardViewController.loadSettings()`.

- [x] **Add similar-code candidates cap**  
  `LimeDB.similarCodeCandidatesCap: Int`, sourced from the `similiar_list` pref (default `20`; NOT gated by `similiar_enable` — Android parity, #5 / `8a338e7b`). It gates the range query in `getMappingByCode`: `cap ≤ 0` → exact-match-only (no `expandBetweenSearchClause`); `cap > 0` → between-search capped at that many partial matches.

- [x] **Add learn-related-words preference gate**  
  `LimeDB.learnRelatedWords: Bool` (default `true`) checked at top of `addOrUpdateRelatedPhraseRecord`.

- [x] **Add Chinese symbol filtering before related-phrase insert**  
  `LimeDB.chineseSymbolsToFilter` set; `addOrUpdateRelatedPhraseRecord` skips learning when either word is punctuation.

- [x] **Add reverse-lookup table preference to `getCodeListStringByWord`**  
  Added optional `table: String?` parameter; falls back to `currentTableName` if nil.

- [x] **Add import cancellation flag and async wrapper**  
  `importCancelled: Bool` property checked inside import loop; `importTxtFileAsync(at:tableName:progress:completion:)` dispatches to background queue.

- [x] **Thread-safe blacklist cache**  
  All `blackListCache` reads and writes guarded with `blackListLock: NSLock`.

- [x] **Implement `hanConvert` using iOS built-in `CFStringTransform`**  
  `hanConvert` now applies `"Hant-Hans"` / `"Hans-Hant"` CFStringTransform. No external DB needed.

- [x] **`getBaseScore` decision: always 0**  
  `hanconvertv2.db` is not bundled on iOS. `getBaseScore` returns 0; scores accumulate through user learning. Documented in code comment.

- [x] **Implement `emojiConvert` backed by `emoji.db`**  
  `emoji.db` bundled; `LimeDB.emojiConvert` queries `cn`/`en`/`tw` tables by `tag` column via lazy `DatabaseQueue`.

- [ ] **Bundle `dictionary` table and implement `getDictionaryAll` / `getEnglishSuggestions`**  
  Not bundled on iOS. `getEnglishSuggestions` stub gracefully returns `[]` when table absent. `UITextChecker` replaces it for the keyboard; full FTS dictionary would be a future enhancement.

---

## LimeDBTest Gap Analysis

**Android test file:** `LimeStudio/app/src/androidTest/java/org/limeime/LimeDBTest.java`  
**iOS test file:** `LimeIME-iOS/LimeIMETests/LimeDBTest.swift`  
**Android test count:** 181  
**iOS test count:** 181  
**Count parity:** exact match — but 2 Android tests are absent and 3 iOS-only tests were added in their place

---

### Tests Missing in iOS (present in Android, absent in Swift)

| Android Test | What It Tests |
|---|---|
| `testLimeDBAddOrUpdateMappingRecordEdgeCases` | Null code, null word, empty strings, and very long input strings passed to `addOrUpdateMappingRecord`; verifies no crash and no record inserted |
| `testLimeDBAddOrUpdateMappingRecordWithScore` | The 4-argument overload `addOrUpdateMappingRecord(table, code, word, score)` with an explicit score; verifies score is stored correctly and update branch increments it |

---

### Tests Present Only in iOS (no Android equivalent)

| iOS Test | What It Tests |
|---|---|
| `testLimeDBAddOrUpdateRelatedPhraseRecordWithLearnEnabled` | Related phrase score progression over multiple calls with learning enabled |
| `testLimeDBDropBackupTable` | `dropBackupTable()` removes the backup table and `checkBackupTable()` then returns false |
| `testLimeDBImportDbWithSingleTableAndVerify` | Import from attached DB into a single table and verify the imported rows are queryable |

---

### Behavioral / Assertion Differences in Shared Tests

| Test | Android | iOS | Difference |
|---|---|---|---|
| `testLimeDBRawQuery` | Uses `android.database.Cursor`; tests `cursor.close()` lifecycle and null-cursor handling | Uses row-dict result list; no cursor lifecycle | Platform difference — correct to differ |
| `testLimeDBCursorHelperMethods` | Tests `getCursorString`, `getCursorInt`, `relatedFromCursor`, `recordFromCursor` directly | Tests `Mapping` / `Related` struct accessors instead | Android Cursor helpers not ported; iOS tests the GRDB equivalent |
| `testLimeDBHanConvertEdgeCases` | Null input expected to throw `NullPointerException` (caught and asserted) | Null / empty input returns the input unchanged (no throw) | iOS will use `CFStringTransform`; null/empty should return empty string gracefully — test needs updating once implemented |
| `testLimeDBKeyToKeyNameEdgeCases` | Null code expected to throw an exception | Nil code returns empty string | iOS handles nil gracefully; Android throws |
| `testLimeDBPreProcessingRemappingCodeEdgeCases` | Focuses on null/empty string handling | Adds assertions for shifted-key remap (`"!@#"` → `"123"`) not present in Android test | iOS test is more thorough on remap behaviour |
| `testLimeDBAddOrUpdateMappingRecord` | Exact count assertion before and after insert | Uses `>=` comparison; slightly looser | Minor strictness difference |
| `testLimeDBGetRelatedSizeEdgeCases` | Multi-character `pword`/`cword` filtering via `StringBuilder` WHERE clauses | Tests via `countRecords` with `ifnull()` SQL | Same intent, different implementation detail |

---

### Functional Areas Untested in iOS

- **Explicit-score overload of `addOrUpdateMappingRecord`** — the 4-argument form `(table, code, word, score)` has no test in iOS (missing `testLimeDBAddOrUpdateMappingRecordWithScore`)
- **Boundary / edge-case inputs to `addOrUpdateMappingRecord`** — null code, null word, very long strings (missing `testLimeDBAddOrUpdateMappingRecordEdgeCases`)
- **Android Cursor lifecycle** — `cursor.close()` and null-cursor paths; not applicable on iOS but no equivalent GRDB lifecycle test covers the same concern

---

### LimeDBTest TODO

- [x] **Add `testLimeDBAddOrUpdateMappingRecordWithScore`**  
  Added. Verifies insert with explicit score 10, update to 20, and `score=-1` auto-increment above 20.

- [x] **Add `testLimeDBAddOrUpdateMappingRecordEdgeCases`**  
  Added. Verifies empty code, empty word, both empty, 1000-char code, and SQL metacharacter word — no crash, no insert for invalid inputs, table survives injection attempt.

- [x] **Align `testLimeDBHanConvert` with CFStringTransform implementation**  
  Added `testLimeDBHanConvertTraditionalToSimplified` (愛→爱), `testLimeDBHanConvertSimplifiedToTraditional` (爱→愛), `testLimeDBHanConvertNoConversion` (option 0 = passthrough). `testLimeDBHanConvertEdgeCases` updated.

- [x] **Align `testLimeDBGetBaseScore` with always-0 decision**  
  `testLimeDBGetBaseScoreAlwaysZero` added — documents and asserts that all inputs return 0 (iOS decision: no hanconvertv2.db bundled).

- [x] **Align `testLimeDBEmojiConvert` with emoji.db implementation**  
  `testLimeDBEmojiConvertReturnsResults` verifies non-crash and that any returned records are non-empty emoji records. `testLimeDBEmojiConvertEdgeCases` verifies empty tag returns `[]` and all locales don't crash.

- [ ] **Align `testLimeDBGetEnglishSuggestions` with final implementation**  
  Still returns `[]` gracefully when `dictionary` table absent. Will need updating if dictionary table is ever bundled.

---

## Intentionally Not Ported (N/A on iOS)

| Java Feature | Reason |
|---|---|
| `recordFromCursor` / `relatedFromCursor` / all Cursor helpers | GRDB row-mapping replaces the Android Cursor pattern entirely |
| Physical keyboard device types (Milestone, Milestone2, Milestone3, DesireZ, Chacha, XperiaPro) | iOS has no hardware keyboard |
| `isPhysicalKeyboardPressed` flag and all physical-KB code paths | Same |
| `LIMEProgressListener` interface | Replaced by Swift `((Int) -> Void)?` closure |
| `android.os.Looper` main-thread detection | Replaced by `Thread.isMainThread` |
| `android.content.Context`, `Resources`, `R.raw.*` | iOS uses `Bundle.main` |
| `synchronized` keyword on write methods | GRDB `DatabaseQueue` provides equivalent serialization |
