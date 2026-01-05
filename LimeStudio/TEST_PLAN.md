# Test Plan: Architecture Refactoring Validation

## Executive Summary

This document outlines a comprehensive testing strategy to validate the refactored LimeIME architecture. The testing plan ensures that all architectural boundaries are respected, all layers function correctly, and the migration from direct `LimeDB` access to `SearchServer`/`DBServer` is complete and correct.

## Test Objectives

1. **Architecture Compliance**: Verify that all components follow the new architecture (no direct `LimeDB` access from UI components)
2. **Functionality Preservation**: Ensure all existing functionality works after refactoring
3. **Layer Isolation**: Verify each layer (LimeDB, DBServer, SearchServer) works independently
4. **Integration**: Test interactions between layers
5. **Performance**: Ensure no performance regression
6. **Code Quality**: Verify parameterized queries, proper error handling, resource refactoring of core IME logic

  - ✅ Early detection of learning bugs
  - ✅ Performance regression prevention
  - ✅ Comprehensive validation of critical user flows


## Current Method Summary

### LimeDB Methods

**Core Database Operations:**
- `countRecords(String table, String whereClause, String[] whereArgs)`
- `addRecord(String table, ContentValues values)`
- `updateRecord(String table, ContentValues values, String whereClause, String[] whereArgs)`
- `deleteRecord(String table, String whereClause, String[] whereArgs)`

**Unified Methods (Wrappers):**
- `countMapping(String table)`
- `getRecordSize(String query, String code, String record)`
- `getRelatedSize(String pword)`
- `getRelated(String pword, int maximum, int offset)`
- `getAllRelated()`

**Backup/Import Operations:**
- `prepareBackup(File sourcedbfile, List<String> tableNames, boolean includeRelated)`
- `prepareBackupDb(String sourcedbfile, String sourcetable)`
- `prepareBackupRelatedDb(String sourcedbfile)`
- `importDb(File sourcedbfile, List<String> tableNames, boolean overwriteExisting, boolean includeRelated)`
- `importDbRelated(File sourcedbfile)`
- `exportTxtTable(String table, File targetFile)`
- `importTxtTable(String table, File sourceFile)`

**Helper Methods:**
- `getBackupTableRecords(String backupTableName)`
- `buildWhereClause(Map<String, String> conditions)`
- `queryWithPagination(String table, String[] columns, String selection, String[] selectionArgs, String orderBy, int limit, int offset)`
- `isValidTableName(String tableName)`

### DBServer Methods

**File Export Operations:**
- `exportZippedDb(String tableName, File targetFile, LIMEProgressListener progressListener)`
- `exportZippedDbRelated(File targetFile, LIMEProgressListener progressListener)`

**File Import Operations:**
- `importZippedDb(File compressedSourceDB, String tableName)`
- `importZippedDbRelated(File compressedSourceDB)`
- `importDb(File sourceDBFile, String tableName)`
- `importDbRelated(File sourceDBFile)`

**Backup/Restore Operations:**
- `backupDatabase(String imType, File targetFile)`
- `restoreDatabase(String imType, File sourceFile)`
- `backupDefaultSharedPreference()`
- `restoreDefaultSharedPreference()`

### SearchServer Methods

**UI-Compatible Methods:**
- `getIm(String code, String type)`
- `getKeyboard()`
- `getImInfo(String imConfig, String field)`
- `setImInfo(String imConfig, String field, String value)`
- `setIMKeyboard(String imConfig, String value, String keyboard)`
- `setIMKeyboard(String imConfig, Keyboard keyboard)`
- `isValidTableName(String tableName)`

**Search Operations:**
- `getMappingByCode(String code, boolean softKeyboard, boolean getAllRecords)`
- `getRecords(String table, String query, int limit, int offset)`
- `getRelated(String pword, int maximum, int offset)`
- `getRelatedByWord(String pword, int maximum, int offset)`
- `getRelatedById(long id)`
- `countRecordsRelated(String pword)`

**Record Management:**
- `addRecord(String table, ContentValues values)`
- `updateRecord(String table, ContentValues values, String whereClause, String[] whereArgs)`
- `deleteRecord(String table, String whereClause, String[] whereArgs)`
- `clearTable(String table)`

**Backup/Restore Operations:**
- `backupUserRecords(String table)`
- `restoreUserRecords(String table)`
- `checkBackuptable(String table)`
- `getBackupTableRecords(String backupTableName)`

**Converter Integration:**
- `hanConvert(String input)`
- `emojiConvert(String input)`

---

## Comprehensive Test List by Phase

This section provides a quick reference of all test lists organized by phase, similar to the detailed format used in Phases 5 and 6.

### Phase 1: LimeDB Layer Tests (See Section "Phase 1" for details)

**Objective**: Test all SQL operations in LimeDB.java are correct and use parameterized queries.

**Test File**: LimeDBTest.java
**Test Coverage**: 40+ test methods

#### **1.1 Core Database Operations** (9 tests)
  - [x] Test: `countRecords()` with various WHERE clauses ✅ `testLimeDBCountRecordsWithNullWhereClause()`, `testLimeDBCountRecordsWithWhereClause()`, `testLimeDBCountRecordsWithMultipleConditions()`, `testLimeDBCountRecordsWithInvalidTableName()`, `testLimeDBCountRecordsWithEmptyTable()`
  - [x] Test: `addRecord()` with ContentValues ✅ `testLimeDBAddRecordDeleteRecordUpdateRecordBranches()`, `testLimeDBAddRecordWithInvalidInputs()`
  - [x] Test: `updateRecord()` with ContentValues ✅ `testLimeDBAddRecordDeleteRecordUpdateRecordBranches()`, `testLimeDBUpdateRecordWithMultipleRecords()`, `testLimeDBUpdateRecordWithInvalidInputs()`, `testLimeDBUpdateRecordWithNoMatchingRecords()`
  - [x] Test: `deleteRecord()` with parameterized queries ✅ `testLimeDBAddRecordDeleteRecordUpdateRecordBranches()`, `testLimeDBDeleteRecordWithMultipleRecords()`, `testLimeDBDeleteRecordWithInvalidInputs()`, `testLimeDBDeleteRecordWithNoMatchingRecords()`

#### **1.2 Unified Methods (Wrappers)** (8 tests)
  - [x] Test: `countMapping()` delegates to `countRecords()` ✅ `testLimeDBCountMappingDelegatesToCountRecords()`
  - [x] Test: `getRecordSize()` delegates to `countRecords()` ✅ `testLimeDBGetRecordSizeDelegatesToCountRecords()`
  - [x] Test: `getRelatedSize()` delegates to `countRecords()` ✅ `testLimeDBGetRelatedSizeDelegatesToCountRecords()`, `testLimeDBGetRelatedSizeEdgeCases()`
  - [x] Test: `getRelated()` method ✅ `testLimeDBGetRelated()`
  - [x] Test: `getAllRelated()` method ✅ `testLimeDBGetAllRelated()`

#### **1.3 Backup/Import Operations** (15 tests)
  - [x] Test: `prepareBackup()` unified method ✅ `testLimeDBPrepareBackupWithSingleTable()`, `testLimeDBPrepareBackupWithMultipleTables()`, `testLimeDBPrepareBackupWithIncludeRelated()`, `testLimeDBPrepareBackupWithInvalidTableName()`
  - [x] Test: `importDb()` unified method ✅ `testLimeDBImportDBWithSingleTable()`, `testLimeDBImportDbWithMultipleTables()`, `testLimeDBImportBackupWithOverwriteExisting()`, `testLimeDBImportDbWithOverwriteExistingFalse()`, `testLimeDBImportDbWithIncludeRelated()`, `testLimeDBImportBackupWithInvalidFile()`
  - [x] Test: `exportTxtTable()` with related table ✅ `testLimeDBExportTxtTableWithRelatedTable()`
  - [x] Test: `exportTxtTable()` / `importTxtTable()` pair for related table ✅ `testLimeDBExportTxtTableRelatedAndImportTxtTableWithDataConsistency()`
  - [x] Test: Wrapper methods delegate correctly ✅ `testLimeDBPrepareBackupDbDelegatesToPrepareBackup()`, `testLimeDBPrepareBackupRelatedDbDelegatesToPrepareBackup()`, `testLimeDBImportDbRelatedDelegatesToImportDb()`, `testLimeDBImportBackupRelatedDbDelegatesToImportBackup()`, `testLimeDBWrapperMethodsDelegationComplete()`

#### **1.4 Helper Methods** (10 tests)
  - [x] Test: `getBackupTableRecords()` ✅ `testLimeDBGetBackupTableRecordsWithValidBackupTable()`, `testLimeDBGetBackupTableRecordsWithInvalidFormat()`, `testLimeDBGetBackupTableRecordsWithInvalidBaseTableName()`
  - [x] Test: `buildWhereClause()` helper ✅ `testLimeDBBuildWhereClauseWithEmptyMap()`, `testLimeDBBuildWhereClauseWithSingleCondition()`, `testLimeDBBuildWhereClauseWithMultipleConditions()`, `testLimeDBBuildWhereClauseWithNullMap()`
  - [x] Test: `queryWithPagination()` helper ✅ `testLimeDBQueryWithPaginationWithLimitAndOffset()`, `testLimeDBQueryWithPaginationWithNoLimit()`, `testLimeDBQueryWithPaginationWithInvalidTableName()`, `testLimeDBQueryWithPaginationWithWhereClause()`

#### **1.5 Table Name Validation** (4 tests)
  - [x] Test: `isValidTableName()` ✅ `testLimeDBIsValidTableNameWithAllValidTables()`, `testLimeDBIsValidTableNameWithInvalidTables()`, `testLimeDBIsValidTableNameWithSQLInjectionAttempts()`

#### **1.6 SQL Injection Prevention** (5 tests)
  - [x] Test: All methods use parameterized queries ✅ `testLimeDBSQLInjectionPreventionInCountRecords()`, `testLimeDBSQLInjectionPreventionInTableName()`, `testLimeDBSQLInjectionPreventionInAddRecord()`, `testLimeDBSQLInjectionPreventionInUpdateRecord()`, `testLimeDBSQLInjectionPreventionInDeleteRecord()`

---

### Phase 2: DBServer Layer Tests (See Section "Phase 2" for details)

**Objective**: Test all file operations are centralized in DBServer.java.

**Test File**: DBServerTest.java
**Test Coverage**: 35+ test methods

#### **2.1 File Export Operations** (15 tests)
  - [x] Test: `exportZippedDb()` ✅ `testDBServerExportImDatabaseWithValidTableName()`, `testDBServerExportImDatabaseWithInvalidTableName()`, `testDBServerExportImDatabaseWithProgressCallback()`, `testDBServerExportZippedDbWithNullTableName()`, `testDBServerExportZippedDbWithNullTargetFile()`, `testDBServerExportZippedDbWithDataIntegrity()`, `testDBServerExportZippedDbWithExistingTargetFile()`, `testDBServerExportZippedDbAndImportWithDataConsistency()`
  - [x] Test: `exportZippedDbRelated()` ✅ `testDBServerExportRelatedDatabase()`, `testDBServerExportZippedDbRelatedAndImportWithDataConsistency()`
  - [x] Test: `exportZippedDbRelated()` / `importZippedDbRelated()` pair ✅ `testDBServerExportZippedDbRelatedAndImportWithDataConsistency()`

#### **2.2 File Import Operations** (20+ tests)
  - [x] Test: `importTxtTable(String filename, ...)` ✅ `testDBServerImportTxtTableWithStringFilename()`, `testDBServerImportTxtTableWithInvalidTableName()`, `testDBServerImportTxtTableWithEmptyFile()`, `testDBServerImportTxtTableWithProgressListener()`, `testDBServerImportTxtTableDelegatesToLimeDB()`, `testDBServerExportTxtTableAndImportTxtTablePair()`, `testDBServerExportTxtTableRelatedAndImportTxtTablePair()`
  - [x] Test: `importTxtTable(File sourcefile, ...)` ✅ `testDBServerImportTxtTableWithFile()`, `testDBServerImportTxtTableWithNullFile()`, `testDBServerImportTxtTableWithNonExistentFile()`
  - [x] Test: `importDb(File sourcedb, String tableName)` ✅ `testDBServerImportDbWithUncompressedDatabase()`, `testDBServerImportDbWithNullSourceDb()`, `testDBServerImportDbWithNonExistentFile()`, `testDBServerImportBackupDbDelegation()`
  - [x] Test: `importDbRelated(File sourcedb)` ✅ `testDBServerImportDbRelatedWithUncompressedDatabase()`, `testDBServerImportBackupRelatedDbDelegation()`
  - [x] Test: `importZippedDb()` ✅ `testDBServerExportZippedDbAndImportWithDataConsistency()`
  - [x] Test: `importZippedDbRelated()` ✅ `testDBServerExportZippedDbRelatedAndImportWithDataConsistency()`

#### **2.3 Backup/Restore Operations** (8 tests)
  - [x] Test: `backupDatabase()` ✅ `testDBServerBackupDatabaseWithUri()`, `testDBServerBackupDatabaseWithNullUri()`, `testDBServerBackupDatabaseWithDataIntegrity()`, `testDBServerBackupDatabaseAndRestoreWithDataConsistency()`
  - [x] Test: `restoreDatabase()` ✅ `testDBServerRestoreDatabaseWithUri()`, `testDBServerRestoreDatabaseWithNullUri()`, `testDBServerRestoreDatabaseWithDataIntegrity()`, `testDBServerRestoreDatabaseWithStringPath()`, `testDBServerBackupDatabaseAndRestoreWithDataConsistency()`

#### **2.4 Shared Preferences Operations** (4 tests)
  - [x] Test: `backupDefaultSharedPreference()` ✅ `testDBServerBackupDefaultSharedPreference()`, `testDBServerBackupDefaultSharedPreferenceWithNullFile()`, `testDBServerBackupDefaultSharedPreferenceAndRestorePair()`
  - [x] Test: `restoreDefaultSharedPreference()` ✅ `testDBServerRestoreDefaultSharedPreference()`, `testDBServerRestoreDefaultSharedPreferenceWithNonExistentFile()`, `testDBServerBackupDefaultSharedPreferenceAndRestorePair()`

#### **2.5 User Records Backup/Restore** (5 tests)
  - [x] Test: User records backup/restore via LimeDB ✅ `testDBServerBackupUserRecordsViaLimeDB()`, `testDBServerBackupUserRecordsWithInvalidTableName()`, `testDBServerRestoreUserRecordsViaLimeDB()`, `testDBServerBackupUserRecordsAndRestoreUserRecordsPair()`, `testDBServerGetBackupTableRecords()`, `testDBServerCheckBackupTable()`

---

### Phase 3: SearchServer Layer Tests (See Section "Phase 3" for details)

**Objective**: Test SearchServer as single interface for all database operations.
**Test File**: SearchServerTest.java

#### **3.1 getMappingByCode (22 tests implemented; +9 new to reach 100% coverage)**
- **[x] 3.1.1 Input guards**:
  - `test_3_1_1_1_getMappingByCode_null_or_empty_returns_empty` – returns an empty result when the code or table name is null or empty. ✅
  - `test_3_1_1_2_getMappingByCode_null_dbadapter_returns_empty` – short-circuits to empty when dbadapter is missing. ✅
- **[x] 3.1.2 Keyboard mode flip**:
  -  `test_3_1_2_1_getMappingByCode_soft_vs_physical_toggles_flag` – flips the soft/physical keyboard flag to choose the correct query path. ✅
- **[x] 3.1.3 Cache paths**:
  -  `test_3_1_3_1_getMappingByCode_cache_miss_hits_db` – on cache miss, queries the database and populates caches. ✅
  - `test_3_1_3_2_getMappingByCode_cache_hit_returns_cached` – returns cached mappings without re-querying the database. ✅
  - `test_3_1_3_3_getMappingByCode_prefetch_warms_cache` – prefetch warms caches for the next lookup. ✅
  - `test_3_1_3_4_getMappingByCode_table_change_resets_cache` – table change invalidates caches before fetching. ✅
  - `test_3_1_3_5_getMappingByCode_getAllRecords_refreshes_has_more_and_keynamecache` – getAllRecords refreshes has-more metadata and keyname cache. ✅
- **[x] 3.1.4 Remap/dual-key**:
  - `test_3_1_4_1_getMappingByCode_phonetic_eten26_remap` – remaps phonetic/eten26 codes to their canonical form. ✅
  - `test_3_1_4_2_getMappingByCode_dual_key_expansion` – expands dual-key combinations into full code variants. ✅
- **[x] 3.1.5 Runtime suggestions toggle**:
  - `test_3_1_5_1_getMappingByCode_runtime_suggestion_enabled` – runtime suggestion path is used when enabled. ✅
  - `test_3_1_5_2_getMappingByCode_runtime_suggestion_disabled` – runtime suggestion logic is skipped when disabled. ✅
  - `test_3_1_5_3_getMappingByCode_self_mapping_creation` – creates self-mapping entries to seed phrase building when needed. ✅
  - `test_3_1_5_4_getMappingByCode_abandon_phrase_suggestion_on_prefetch` – abandons phrase suggestions when prefetch says to stop. ✅
- **[x] 3.1.6 English fallback**:
  - `test_3_1_6_1_getMappingByCode_long_code_english_fallback` – long codes trigger English suggestion fallback. ✅
  - `test_3_1_6_2_getMappingByCode_english_suggestion_threshold_clears_runtime_stack` – English suggestion threshold clears runtime suggestion stack. ✅
- **[x] 3.1.7 Fallbacks & ordering**:
  - `test_3_1_7_1_getMappingByCode_wayback_fallback_when_empty` – uses wayback fallback when primary search is empty. ✅
  - `test_3_1_7_2_getMappingByCode_result_sorting_basescore` – sorts results by base score for consistent ordering. ✅
- **[x] 3.1.8 getMappingByCodeFromCacheOrDB**:
  - `test_3_1_8_1_getMappingByCode_cachekey_and_remapcache_population` – populates cache keys and remap cache during fetch. ✅
  - `test_3_1_8_2_getMappingByCode_db_fallback_exception_safe` – database fallback path remains exception-safe. ✅
- **[x] 3.1.9 English suggestions cache**:
  - `test_3_1_9_1_getEnglishSuggestions_cache_put_and_hit` – caches English suggestions and reuses them on hits. ✅
  - `test_3_1_9_2_getEnglishSuggestions_fast_skip_after_empty_prefix` – empty-prefix probes short-circuit and cache the no-match state. ✅

- **[x] 3.1.10 New branches to close coverage gaps (planned add)**:
  - `test_3_1_10_1_null_pref_returns_empty` – mLIMEPref null guard returns empty list without DB access. ✅
  - `test_3_1_10_2_abandon_phrase_reset_single_char` – abandonPhraseSuggestion=true with smart input triggers clear on 1-char code. ✅
  - `test_3_1_10_3_prefetch_skips_runtime_suggestion` – prefetchCache=true skips makeRunTimeSuggestion branch. ✅
  - `test_3_1_10_4_getAllRecords_refreshes_hasMore_branch` – hasMoreRecords marker forces DB reload when getAllRecords is true. ✅
  - `test_3_1_10_5_wayback_loop_terminates_on_prefix_hit` – empty result walks back prefixes and exits the do/while branch. ✅
  - `test_3_1_10_6_english_suggestion_empty_path` – long code with empty engcache exercises null/empty englishSuggestions branch. ✅
  - `test_3_1_10_7_bestSuggestion_inserted_when_high_score` – populated bestSuggestionStack inserts best suggestion ahead of result list. ✅
  - `test_3_1_10_8_remapcache_updates_on_exact_match` – exact-match remap updates coderemapcache in helper path. ✅
  - `test_3_1_10_9_db_exception_returns_safe_list` – DB exception triggers catch block and returns non-null list. ✅

#### **3.2 Runtime suggestions core (9–11 tests)**
- **[x] 3.2.1 makeRunTimeSuggestion**:
  - `test_3_2_1_1_makeRunTimeSuggestion_empty_list` – returns no suggestions when mapping list is empty. ✅
  - `test_3_2_1_2_makeRunTimeSuggestion_depth_cap` – enforces stack depth limits while building suggestions. ✅
  - `test_3_2_1_3_makeRunTimeSuggestion_disabled_flag` – bypasses suggestion building when the feature flag is off. ✅
  - `test_3_2_1_4_makeRunTimeSuggestion_algorithmic_merge` – merges candidates using the runtime suggestion algorithm. ✅
  - `test_3_2_1_5_makeRunTimeSuggestion_backspace_prunes_stack` – backspace removes the latest stack frame. ✅
  - `test_3_2_1_6_makeRunTimeSuggestion_adds_unrelated_phrase` – adds unrelated phrases when no exact match found. ✅
  - `test_3_2_1_7_makeRunTimeSuggestion_skips_low_basescore_remaining` – skips low basescore phrases on remaining code search. ✅
  - `test_3_2_1_8_makeRunTimeSuggestion_reorders_on_highest_score` – reorders suggestions to place highest score last. ✅
  - `test_3_2_1_9_makeRunTimeSuggestion_snapshot_with_multiple_history` – snapshot restoration with multiple history items. ✅
  - `test_3_2_1_10_makeRunTimeSuggestion_snapshot_prefix_matching` – snapshot restoration with prefix matching. ✅
- **[x] 3.2.2 clearRunTimeSuggestion**:
  - `test_3_2_2_1_clearRunTimeSuggestion_full_reset` – full reset clears best suggestion and stacks. ✅
  - `test_3_2_2_2_clearRunTimeSuggestion_partial_reset` – partial reset clears transient state but keeps best suggestion when requested. ✅
- **[x] 3.2.3 getRealCodeLength**:
  - `test_3_2_3_1_getRealCodeLength_tone_stripping` – strips tone markers before measuring code length. ✅
  - `test_3_2_3_2_getRealCodeLength_dual_code` – handles dual-code entries when computing length. ✅
  - `test_3_2_3_3_getRealCodeLength_runtime_phrase_learning` – uses real code length to gate phrase learning. ✅
- **[x] 3.2.4 lcs**:
  - `test_3_2_4_1_lcs_identical_partial_none_empty` – validates LCS output for identical, partial, disjoint, and empty strings. ✅
- **[x] 3.2.5 getCodeListStringFromWord**:
  - `test_3_2_5_1_getCodeListStringFromWord_found` – returns concatenated code list when the word exists. ✅
  - `test_3_2_5_2_getCodeListStringFromWord_not_found` – returns empty when the word has no codes. ✅
  - `test_3_2_5_3_getCodeListStringFromWord_with_notification` – triggers notification logic when reverse lookup notification is enabled
- **3.2.6 postFinishInput** (0% coverage) 🔴:
  - Called after input session finishes; orchestrates learning and phrase building
  - `test_3_2_6_1_postFinishInput_null_scorelist` – null scorelist returns safely
  - `test_3_2_6_2_postFinishInput_empty_list` – empty scorelist skips learning
  - `test_3_2_6_3_postFinishInput_triggers_learning_paths` – calls learnRelatedPhrase and learnLDPhrase
  - `test_3_2_6_4_postFinishInput_snapshot_restoration` – restores suggestion snapshots correctly
  - `test_3_2_6_5_postFinishInput_with_scorelist` – creates background thread for learning with non-null scorelist

#### **3.3 Cache utilities (9 tests)**
- **[x] 3.3.1 initialCache/resetCache flag**:
  - `test_3_3_1_1_initialCache_recreates_all_maps` – initial cache call recreates all internal maps.
  - `test_3_3_1_2_resetCache_flag_triggers_initialCache_on_next_query` – reset flag forces cache rebuild on the next lookup.
  - `test_3_3_1_3_initialCache_handles_exception` – handles RemoteException during clear() and continues initialization
- **[x] 3.3.2 prefetchCache**:
  - `test_3_3_2_1_prefetchCache_numbers` – warms numeric mappings for faster number input.
  - `test_3_3_2_2_prefetchCache_symbols` – warms symbol mappings for symbol input paths.
  - `test_3_3_2_3_prefetchCache_both` – warms both number and symbol mappings together.
- **[x] 3.3.3 removeRemappedCodeCachedMappings**:
  - `test_3_3_3_1_removeRemappedCodeCachedMappings_invalidates_entries` – invalidates remapped-code cache entries.
- **[x] 3.3.4 updateSimilarCodeCache**:
  - `test_3_3_4_1_updateSimilarCodeCache_drops_prefix_entries` – drops prefix entries to avoid stale similar-code hits.
  - `test_3_3_4_2_updateSimilarCodeCache_prefetch_single_char` – prefetches single-character similar-code cache entries.
- **[x] 3.3.5 updateScoreCache**:
  - `test_3_3_5_1_updateScoreCache_learning_invalidation` – learning updates refresh the score cache and mark stale entries.
  - `test_3_3_5_2_updateScoreCache_exact_match_reordering` – exact match sorting and reordering logic
  - `test_3_3_5_3_updateScoreCache_related_phrase_record` – related phrase records trigger cache removal
  - `test_3_3_5_4_updateScoreCache_exact_match_no_reorder_needed` – score increment without reordering
  - `test_3_3_5_5_updateScoreCache_code_not_in_cache` – handles code not present in cache
  - `test_3_3_5_6_updateScoreCache_exact_match_jump_multiple_positions` – multi-position jump in ordering
  - `test_3_3_5_7_updateScoreCache_exact_match_large_score_increase` – large score increases
  - `test_3_3_5_8_updateScoreCache_score_increment_small` – small score increments
  - `test_3_3_5_9_updateScoreCache_related_phrase_removal_cache_hit` – related phrase removal from cache
  - `test_3_3_5_10_updateScoreCache_exact_match_at_position_zero` – updating item at position 0
  - `test_3_3_5_11_updateScoreCache_exact_match_jump_to_end` – score increment at end of list
  - `test_3_3_5_12_updateScoreCache_physical_keyboard_sort_preference` – physical keyboard sort preferences
  - `test_3_3_5_13_updateScoreCache_exact_match_reorder_with_insertion` – reordering with insertion at specific position
  - `test_3_3_5_14_updateScoreCache_sort_disabled_soft_keyboard` – sort disabled on soft keyboard
  - `test_3_3_5_15_updateScoreCache_sort_disabled_physical_keyboard` – sort disabled on physical keyboard
  - `test_3_3_5_16_updateScoreCache_sort_disabled_updates_score` – score updates when sorting disabled
  - `test_3_3_5_17_updateScoreCache_related_removal_path` – related phrase removal code path
  - `test_3_3_5_18_updateScoreCache_reorder_without_insert` – reordering without insertion
  - `test_3_3_5_19_updateScoreCache_partial_match` – partial match records remove cached entries
  - `test_3_3_5_20_updateScoreCache_sorting_disabled` – updates score without reordering when sorting is disabled

#### **3.4 Records/search CRUD (11 tests)**
- **[x] 3.4.1 getRecords**:
  - `test_3_4_1_1_getRecords_pagination_bounds` – enforces limit/offset bounds when paging results.
  - `test_3_4_1_2_getRecords_empty_result` – returns empty collections when no rows match.
  - `test_3_4_1_3_getRecords_query_filter` – applies query filters to narrow returned records.
- **[x] 3.4.2 related lookups**:
  - `test_3_4_2_1_getRelated_pagination_empty` – handles empty related lookups with pagination inputs.
  - `test_3_4_2_2_countRecordsRelated_accuracy` – counts related records accurately for a given phrase.
  - `test_3_4_2_3_hasRelated_true_false_paths` – covers true/false paths for related record existence checks.
- **[x] 3.4.3 counts**:
  - `test_3_4_3_1_countRecordsByWordOrCode_code_vs_word` – differentiates counting by code versus word fields.
  - `test_3_4_3_2_countRecords_filters_empty_word` – handles empty-word inputs without errors.
- **[x] 3.4.4 add/update/delete/clear**:
  - `test_3_4_4_1_add_update_delete_valid_table` – exercises add, update, and delete on valid tables.
  - `test_3_4_4_2_add_update_delete_invalid_table` – guards add/update/delete against invalid table names.
  - `test_3_4_4_3_clearTable_behavior` – verifies clearTable wipes data and related caches.
- **3.4.5 setTableName/isValidTableName** (52%/50% coverage) 🔴:
  - Table name management and validation
  - `test_3_4_5_1_setTableName_null_or_empty_ignores` – null/empty table names are ignored safely
  - `test_3_4_5_2_setTableName_valid_code_switches_table` – valid table code switches to new table
  - `test_3_4_5_3_setTableName_resets_cache_on_switch` – table switch invalidates caches
  - `test_3_4_5_4_setTableName_boolean_flags_affect_behavior` – resetCache/resetPhrase flags control reset behavior
  - `test_3_4_5_5_isValidTableName_custom_table_true` – custom table names validate as true
  - `test_3_4_5_6_isValidTableName_builtin_tables_true` – built-in table names (user, related, backup) validate as true
  - `test_3_4_5_7_isValidTableName_invalid_names_false` – invalid/sql-injection names validate as false
  - `test_3_4_5_8_isValidTableName_null_dbadapter` – null dbadapter returns false
- **3.4.6 updateSimilarCodeCache** (0% coverage) 🔴:
  - Removes cached entries for similar code prefixes during updates
  - `test_3_4_6_1_updateSimilarCodeCache_code_length_1` – handles single character code edge case
  - `test_3_4_6_2_updateSimilarCodeCache_longer_code` – removes cached substring entries for longer codes

#### **3.5 IM/keyboard config helpers (17 tests)**
- **[x] 3.5.1 getImConfigList/getAllImKeyboardConfigList**:
  - `test_3_5_1_1_getImConfigList_null_dbadapter` – null dbadapter returns empty config lists safely.
  - `test_3_5_1_2_getImConfigList_null_filters` – no filters returns the full config list.
  - `test_3_5_1_3_getImConfigList_specific_code` – filtering by code returns only matching IM configs.
  - `test_3_5_1_4_getImConfigList_keyboard_field` – keyboard field filter returns matching keyboards.
  - `test_3_5_1_5_getAllImKeyboardConfigList_keyboard_field` – all IM keyboard configs include keyboard-specific fields.
- **[x] 3.5.2 getImConfig/setImConfig**:
  - `test_3_5_2_1_getImConfig_null_db_or_code` – null dbadapter or code yields safe defaults.
  - `test_3_5_2_2_setImConfig_persists_value` – setting config persists and can be read back.
  - `test_3_5_2_3_getImConfig_invalid_field` – invalid field lookups return empty values without crashing.
  - `test_3_5_2_4_setImConfig_null_dbadapter_returns_false` – null dbadapter returns false
  - `test_3_5_2_5_setImConfig_valid_dbadapter_delegates` – valid dbadapter delegates to database
  - `test_3_5_2_6_setImConfig_special_characters` – handles SQL injection attempts and special characters safely
- **[x] 3.5.3 setIMKeyboard (String/Keyboard)**:
  - `test_3_5_3_1_setIMKeyboard_string_overload` – string overload updates the IM keyboard selection.
  - `test_3_5_3_2_setIMKeyboard_object_overload` – object overload applies keyboard objects correctly, including null/missing IM cases.
  - `test_3_5_3_3_setIMKeyboard_string_null_dbadapter` – handles null dbadapter without throwing exception
  - `test_3_5_3_4_setIMKeyboard_string_valid_dbadapter` – delegates to dbadapter.setIMConfigKeyboard() with valid dbadapter
  - `test_3_5_3_5_setIMKeyboard_keyboard_null_dbadapter` – handles null dbadapter with Keyboard object
  - `test_3_5_3_6_setIMKeyboard_keyboard_valid_dbadapter` – delegates Keyboard object to dbadapter
  - `test_3_5_3_7_setIMKeyboard_string_calls_setIMConfigKeyboard` – string overload calls setIMConfigKeyboard correctly
  - `test_3_5_3_8_setIMKeyboard_string_null_or_empty_keyboardcode` – handles null/empty keyboard code safely
  - `test_3_5_3_9_setIMKeyboard_keyboard_null_object` – handles null Keyboard object
  - `test_3_5_3_10_setIMKeyboard_keyboard_missing_fields` – handles Keyboard with missing/null fields
  - `test_3_5_3_11_setIMConfigKeyboard_string_null_dbadapter` – setIMConfigKeyboard handles null dbadapter
- **[x] 3.5.4 getKeyboardConfigList/keyToKeyname**:
  - `test_3_5_4_1_getKeyboardConfigList_roundtrip` – keyboard config list round-trips through retrieval and caching.
  - `test_3_5_4_2_keyToKeyname_cache_hit_miss` – key-to-keyname resolves from cache first and falls back to lookup.
- **[x] 3.5.5 getSelkey**:
  - `test_3_5_5_1_getSelkey_phonetic_vs_nonphonetic` – selection keys differ between phonetic and non-phonetic modes.
  - `test_3_5_5_2_getSelkey_number_symbol_combos` – number/symbol combinations map to the right selection keys.
  - `test_3_5_5_3_getSelkey_invalid_db_value_fallback` – invalid db values fall back to defaults safely.
  - `test_3_5_5_4_getSelkey_cache_reuse` – selection key cache is reused across calls.
- **[x] 3.5.6 checkPhoneticKeyboardSetting**:
  - `test_3_5_6_1_checkPhoneticKeyboardSetting_pref_db_mismatch_hsu_eten_eten26_standard` – resolves phonetic keyboard setting across pref/db mismatches for all layouts.
  - `test_3_5_6_2_checkPhoneticKeyboardSetting_null_dbadapter` – handles null dbadapter without throwing exception
  - `test_3_5_6_3_checkPhoneticKeyboardSetting_valid_dbadapter` – delegates to dbadapter successfully
  - `test_3_5_6_4_checkPhoneticKeyboardSetting_calls_setIMConfigKeyboard` – calls setIMConfigKeyboard on dbadapter
  - `test_3_5_6_5_checkPhoneticKeyboardSetting_getKeyboardInfo_called` – delegates keyboard info lookup to dbadapter
- **3.5.7 getKeyboard/getKeyboardInfo/getKeyboardConfig** (0% coverage) 🔴:
  - Keyboard configuration access methods
  - `test_3_5_7_1_getKeyboard_null_dbadapter` – null dbadapter returns null safely
  - `test_3_5_7_2_getKeyboard_returns_keyboard_object` – returns Keyboard object from database
  - `test_3_5_7_3_getKeyboardInfo_null_inputs` – null code/field returns null
  - `test_3_5_7_4_getKeyboardInfo_valid_lookup` – returns correct field value for keyboard
  - `test_3_5_7_5_getKeyboardConfig_null_code` – null code returns null
  - `test_3_5_7_6_getKeyboardConfig_valid_code` – returns ImConfig for valid keyboard code
- **3.5.8 getImAllConfigList/removeImInfo/resetImConfig** (0% coverage) 🔴:
  - IM configuration access and modification methods
  - `test_3_5_8_1_getImAllConfigList_null_field` – null field returns null
  - `test_3_5_8_2_getImAllConfigList_valid_field` – returns list of ImConfig objects
  - `test_3_5_8_3_removeImInfo_null_inputs` – null code/field skips removal safely
  - `test_3_5_8_4_removeImInfo_removes_field` – removes field from config
  - `test_3_5_8_5_resetImConfig_null_code` – null code skips reset safely
  - `test_3_5_8_6_resetImConfig_restores_defaults` – resets config to defaults
  - `test_3_5_8_7_removeImInfo_null_dbadapter` – handles null dbadapter without crashing
  - `test_3_5_8_8_resetImConfig_null_dbadapter` – handles null dbadapter without crashing
- **3.5.9 restoredToDefault** (0% coverage) 🔴:
  - Checks if IM config has been restored to default state
  - `test_3_5_9_1_restoredToDefault_no_changes` – returns true when no changes made
  - `test_3_5_9_2_restoredToDefault_after_reset` – returns true after resetImConfig()
  - `test_3_5_9_3_restoredToDefault_null_dbadapter` – handles null dbadapter without crashing
- **3.5.10 getKeyboardInfo/getImAllConfigList/getKeyboardConfig** (0% coverage) 🔴:
  - Keyboard and IM configuration access methods
  - `test_3_5_10_1_getKeyboardInfo_null_dbadapter` – returns null when dbadapter is null
  - `test_3_5_10_2_getImAllConfigList_null_dbadapter` – returns null when dbadapter is null
  - `test_3_5_10_3_getKeyboardConfig_null_dbadapter` – returns null when dbadapter is null
  - `test_3_5_10_4_getKeyboardInfo_with_valid_dbadapter` – delegates to dbadapter successfully
  - `test_3_5_10_5_getImAllConfigList_with_valid_dbadapter` – delegates to dbadapter successfully
  - `test_3_5_10_6_getKeyboardConfig_with_valid_dbadapter` – delegates to dbadapter successfully

#### **3.6 Backup/restore + converters (12 tests)**
- **[x] 3.6.1 backupUserRecords/restoreUserRecords**:
  - `test_3_6_1_1_backupUserRecords_null_db_or_invalid_table` – guards backup/restore when dbadapter is null or table is invalid.
  - `test_3_6_1_2_restoreUserRecords_empty_backup` – handles empty backups gracefully during restore.
  - `test_3_6_1_3_restoreUserRecords_data_consistency` – restoring from backup preserves user record data fidelity.
  - `test_3_6_1_4_restoreUserRecords_success` – successful delegation to dbadapter.
  - `test_3_6_1_5_restoreUserRecords_null_and_exception` – null dbadapter returns 0; exception during restore is caught and returns 0 (covers lines 2095-2098).
- **[x] 3.6.2 checkBackupTable/getBackupTableRecords**:
  - `test_3_6_2_1_checkBackupTable_invalid_name` – invalid backup table names return false without side effects.
  - `test_3_6_2_2_getBackupTableRecords_empty_backup` – empty backup tables return empty cursors.
  - `test_3_6_2_3_getBackupTableRecords_happy_cursor` – valid backup tables return expected cursor contents.
- **[x] 3.6.3 hanConvert**:
  - `test_3_6_3_1_hanConvert_empty_input` – empty input returns empty conversion result.
  - `test_3_6_3_2_hanConvert_mixed_unsupported` – mixed or unsupported characters are ignored or passed through safely.
  - `test_3_6_3_3_hanConvert_correctness` – verifies correct Han conversion between variants.
- **[x] 3.6.4 emojiConvert**:
  - `test_3_6_4_1_emojiConvert_null_empty` – null or empty inputs return empty emoji output.
  - `test_3_6_4_2_emojiConvert_cache_hit` – cache hits return stored emoji conversions.
  - `test_3_6_4_3_emojiConvert_db_fallback_type_variation` – falls back to DB lookup with type variations when cache misses.

#### **3.7 Learning Methods (56 tests)**
- **[x] 3.7.1 learnRelatedPhraseAndUpdateScore() Tests** (6 tests):
  - `test_3_7_1_1_learnRelatedPhraseAndUpdateScore_null_mapping` – null mapping handled safely.
  - `test_3_7_1_2_learnRelatedPhraseAndUpdateScore_updates_score` – updates mapping score in database.
  - `test_3_7_1_3_learnRelatedPhraseAndUpdateScore_calls_learnRelatedPhrase` – calls learnRelatedPhrase with scorelist.
  - `test_3_7_1_4_learnRelatedPhraseAndUpdateScore_null_dbadapter` – null dbadapter handled safely.
  - `test_3_7_1_5_learnRelatedPhraseAndUpdateScore_updates_cache` – updates score cache after learning.
  - `test_3_7_1_6_learnRelatedPhraseAndUpdateScore_concurrent_access` – thread-safe concurrent score updates.

- **[x] 3.7.2 learnRelatedPhrase() Tests** (17 tests):
  - `test_3_7_2_1_learnRelatedPhrase_null_list` – null list handled safely.
  - `test_3_7_2_2_learnRelatedPhrase_empty_list` – empty list returns without processing.
  - `test_3_7_2_3_learnRelatedPhrase_single_mapping` – single mapping doesn't trigger pair learning.
  - `test_3_7_2_4_learnRelatedPhrase_pref_disabled` – preference disabled prevents learning.
  - `test_3_7_2_5_learnRelatedPhrase_consecutive_words` – learns consecutive word pairs correctly.
  - `test_3_7_2_6_learnRelatedPhrase_null_mappings_skipped` – skips null mappings in list.
  - `test_3_7_2_7_learnRelatedPhrase_empty_word_skipped` – skips mappings with empty words.
  - `test_3_7_2_8_learnRelatedPhrase_record_type_filters` – filters record types correctly for learning.
  - `test_3_7_2_9_learnRelatedPhrase_unit2_accepts_punctuation_emoji` – accepts punctuation and emoji for unit2.
  - `test_3_7_2_10_learnRelatedPhrase_calls_addOrUpdateRelatedPhraseRecord` – delegates to dbadapter correctly.
  - `test_3_7_2_11_learnRelatedPhrase_high_score_triggers_LD` – high score (>20) triggers LD phrase learning.
  - `test_3_7_2_12_learnRelatedPhrase_multiple_pairs` – learns multiple consecutive pairs.
  - `test_3_7_2_13_learnRelatedPhrase_high_score_but_LD_disabled` – high score but LD learning disabled (covers getLearnPhrase() false branch).
  - `test_3_7_2_14_learnRelatedPhrase_null_words` – **MERGED**: unit1/unit2 with null word breaks learning (covers both unit.getWord() == null branches).
  - `test_3_7_2_15_learnRelatedPhrase_invalid_record_types` – **MERGED**: unit1/unit2 invalid record types + English suggestion (covers COMPOSING_CODE and ENGLISH_SUGGESTION filters).
  - `test_3_7_2_16_learnRelatedPhrase_score_below_threshold` – score <= 20 doesn't trigger LD learning (covers score threshold branch).
  - `test_3_7_2_17_learnRelatedPhrase_record_type_and_LD_filters` – **MERGED**: LD disabled, unit1 emoji not allowed, unit2 emoji/punctuation allowed (covers emoji and punctuation filters).

- **[x] 3.7.3 learnLDPhrase() Tests** (10 merged tests):
  - `test_3_7_3_1_learnLDPhrase_input_validation` – null/empty arrays, empty phrase lists, and size >= 5 inputs are skipped safely.
  - `test_3_7_3_2_learnLDPhrase_length_boundaries` – learns 2-, 3-, and 4-character phrases within size limits.
  - `test_3_7_3_3_learnLDPhrase_unit1_validation` – null unit1, empty word, and English (code == word) short-circuit learning.
  - `test_3_7_3_4_learnLDPhrase_reverse_lookup` – null IDs trigger reverse lookup; failed lookups halt learning.
  - `test_3_7_3_5_learnLDPhrase_multi_char_scenarios` – multi-char baseWord/word2 success paths plus partial reverse-lookup failure that still learns the first word.
  - `test_3_7_3_6_learnLDPhrase_phonetic_and_cache` – phonetic tone removal, QPCode path, addOrUpdateMappingRecord, cache invalidation, and non-phonetic table flow.
  - `test_3_7_3_7_learnLDPhrase_reverse_lookup_failures` – baseCode/code2 null or empty (including substring bounds) stop learning.
  - `test_3_7_3_8_learnLDPhrase_unit2_validation` – unit2 composing code or empty word block learning; partial/related phrase reverse lookup succeeds; English suggestions are rejected.
  - `test_3_7_3_9_learnLDPhrase_multi_char_reverse_lookup_fails` – multi-char baseWord reverse-lookup failure prevents learning.
  - `test_3_7_3_10_learnLDPhrase_remaining_branches` – covers unit1 partial/null/empty code, related record, baseWord length >= 5 guard, null unit2, word2 length edge, code2 empty/null, partial/related unit2 lookups, phonetic LD/QP length guards, and non-phonetic baseCode length guard.





### Phase 4: UI Component Tests (See Section "Phase 4" for details)

**Objective**: Test UI components use SearchServer/DBServer, not LimeDB directly.

**Test Files**: Multiple test files (see below)
**Test Coverage**: 80+ test methods across all UI components

#### **4.1 Architecture Compliance Tests** (5 tests)
  - [x] Test: No direct LimeDB in UI components ✅
    - `ManageImFragmentTest.testNoDirectLimeDBAccess`
    - `SetupImFragmentTest` (all tests)
  - [x] Test: Controller-driven architecture ✅
    - `SetupImControllerFlowsTest` (all tests)
    - `ManageImControllerTest` (all tests)

#### **4.2 SetupImFragment** (15 tests)
  - [x] **Test File**: SetupImFragmentTest.java
    - [x] Fragment initialization
      - Verifies SetupImFragment initializes correctly. ✅
      - Mapping: `SetupImFragmentTest` (fragment initialization)
    - [x] Button state management
      - Verifies SetupImFragment manages button states. ✅
      - Mapping: `SetupImFragmentTest` (button state)
    - [x] Text file import flow (Controller-driven)
      - Handles text file import via controller. ✅
      - Mapping: `SetupImFragmentTest` (import)
    - [x] Zipped database import flow
      - Handles zipped database import. ✅
      - Mapping: `SetupImFragmentTest` (zipped db)
    - [x] Download and import flow
      - Handles download and import scenarios. ✅
      - Mapping: `SetupImFragmentTest` (download)
    - [x] Error handling
      - Verifies error scenarios are handled gracefully. ✅
      - Mapping: `SetupImFragmentTest` (error handling)
    - [x]  Zipped database import flow
      - Handles zipped database import.✅
      - `SetupImFragmentTest` (zipped db)
    - [x]  Download and import flow
      - Handles download and import scenarios.✅
      - `SetupImFragmentTest` (download)
    - [x]  Error handling
      - Verifies error scenarios are handled gracefully.✅
      - `SetupImFragmentTest` (error handling)
  - [x] Test: Backup/restore operations ✅
    - Backup/restore operations
      - Validates backup and restore flows, ensuring all controller-driven operations work as intended.
      - `SetupImControllerFlowsTest` (comprehensive workflow)

- **4.3 ManageImFragment** (12 tests)
  - [x] **Test File**: ManageImFragmentTest.java
    - [x] IM/keyboard loading
      - Ensures IM/keyboard loading uses controller logic. ✅
      - Mapping: `ManageImFragmentTest.testIMKeyboardLoadingUsesController`
    - [x] Asynchronous record loading (thread-safe)
      - Verifies async record loading is thread-safe. ✅
      - Mapping: `ManageImFragmentTest.testAsynchronousRecordLoadingIsThreadSafe`
    - [x] Record management (add/edit/delete)
      - Confirms add/edit/delete operations delegate to controller. ✅
      - Mapping: `ManageImFragmentTest.testRecordManagementDelegatesToController`
    - [x] Controller integration
      - Validates controller integration for fragment. ✅
      - Mapping: `ManageImControllerTest` (controller integration)

- **4.4 ManageImKeyboardDialog** (5 tests)
  - [x] **Test File**: ManageImKeyboardDialogTest.java
    - [x] Dialog class existence
      - Confirms dialog class is present and instantiable. ✅
      - Mapping: `ManageImKeyboardDialogTest.testManageImKeyboardDialogClassExists`
    - [x] Activity provides controller
      - Verifies dialog receives controller from activity. ✅
      - Mapping: `ManageImKeyboardDialogTest.testActivityProvidesController`
    - [x] Controller keyboard APIs
      - Ensures dialog can access controller keyboard APIs. ✅
      - Mapping: `ManageImKeyboardDialogTest.testControllerKeyboardApisExist`

- **4.5 ImportDialog** (8 tests)
  - [x] **Test File**: ImportDialogTest.java
    - [x] Import operations
      - Tests import dialog operations. ✅
      - Mapping: `ImportDialogTest` (import operations)
    - [x] IM list population via SearchServer
      - Tests IM list population from SearchServer. ✅
      - Mapping: `ImportDialogTest` (IM list population)
    - [x] Listener-based callbacks to SetupImController
      - Tests listener callback integration. ✅
      - Mapping: `ImportDialogTest` (listener callbacks)

- **4.6 ShareDialog** (10 tests)
  - [x] **Test File**: ShareDialogTest.java, ShareManagerTest.java
    - [x] Share as zipped database (.limedb)
      - Validates sharing IM as zipped database. ✅
      - Mapping: `ShareDialogTest` (share as .limedb)
    - [x] Share as text file (.lime)
      - Validates sharing IM as text file. ✅
      - Mapping: `ShareDialogTest` (share as .lime)
    - [x] Share Related as zipped database
      - Validates sharing related data as zipped database. ✅
      - Mapping: `ShareDialogTest` (share related as zipped db)
    - [x] Share Related as text file
      - Validates sharing related data as text file. ✅
      - Mapping: `ShareDialogTest` (share related as text file)
    - [x] Uses ShareManager and DBServer
      - Ensures ShareManager and DBServer integration for sharing workflows. ✅
      - Mapping: `ShareManagerTest` (manager and DBServer integration)

- **4.7 SetupImLoadDialog** (10 tests)
  - [x] **Test File**: SetupImLoadDialogTest.java
    - [x] File selection - .limedb import
      - Verifies file selection for .limedb import. ✅
      - Mapping: `SetupImLoadDialogTest` (file selection)
    - [x] File selection - .lime/.cin import
      - Verifies file selection for .lime/.cin import. ✅
      - Mapping: `SetupImLoadDialogTest` (file selection)
    - [x] Download button - remote .limedb import
      - Verifies download and import of remote .limedb files. ✅
      - Mapping: `SetupImLoadDialogTest` (download)
    - [x] Backup/restore learning checkboxes
      - Verifies backup/restore learning checkbox logic. ✅
      - Mapping: `SetupImLoadDialogTest` (backup/restore)
    - [x] File type validation
      - Verifies file type validation logic. ✅
      - Mapping: `SetupImLoadDialogTest` (validation)
    - [x] Error handling
      - Verifies error handling in SetupImLoadDialog. ✅
      - Mapping: `SetupImLoadDialogTest` (error handling)

- **4.8 MainActivity** (5 tests)
  - [x] **Test File**: MainActivityTest.java
    - [x] Component coordination and getters
      - Tests MainActivity's coordination of components and getter methods. ✅
      - Mapping: `MainActivityTest` (component coordination)
    - [x] Singleton controller/manager creation
      - Tests singleton creation of controller/manager in MainActivity. ✅
      - Mapping: `MainActivityTest` (singleton creation)

#### **4.9 VoiceInputActivity** (35 tests) 
  - [x] **Test File**: VoiceInputActivityTest.java
    - [x] Activity creation and initialization
      - Verifies activity creation and initialization sequence.✅
      - Mapping: `VoiceInputActivityTest.testActivityCreationAndInitialization`
    - [x] Activity destruction
      - Ensures proper destruction and cleanup of activity.✅
      - Mapping: `VoiceInputActivityTest.testActivityDestruction`
    - [x] Activity finishes after launch
      - Checks activity finishes as expected after launch.✅
      - Mapping: `VoiceInputActivityTest.testActivityFinishesAfterLaunch`
    - [x] RecognizerIntent constants
      - Validates RecognizerIntent constants are correct.✅
      - Mapping: `VoiceInputActivityTest.testRecognizerIntentConstants`
    - [x] RecognizerIntent availability check
      - Ensures RecognizerIntent is available on device.✅
      - Mapping: `VoiceInputActivityTest.testRecognizerIntentAvailabilityCheck`
    - [x] Activity handles RecognizerIntent unavailable
      - Tests error handling when RecognizerIntent is missing.✅
      - Mapping: `VoiceInputActivityTest.testActivityHandlesRecognizerIntentUnavailable`
    - [x] Valid recognition results
      - Verifies handling of valid voice recognition results.✅
      - Mapping: `VoiceInputActivityTest.testValidRecognitionResults`
    - [x] Null recognition results
      - Checks handling of null recognition results.✅
      - Mapping: `VoiceInputActivityTest.testNullRecognitionResults`
    - [x] Empty recognition results
      - Ensures empty recognition results are handled gracefully.✅
      - Mapping: `VoiceInputActivityTest.testEmptyRecognitionResults`
    - [x] Broadcast intent action
      - Tests broadcast intent action for communication.✅
      - Mapping: `VoiceInputActivityTest.testBroadcastIntentAction`
    - [x] Broadcast intent extra
      - Verifies broadcast intent extras are correct. ✅
      - Mapping: `VoiceInputActivityTest.testBroadcastIntentExtra`
    - [x] Broadcast receiver integration
      - Ensures broadcast receiver integration works. ✅
      - Mapping: `VoiceInputActivityTest.testBroadcastReceiverIntegration`
    - [x] Transparent window configuration
      - Validates transparent window configuration. ✅
      - Mapping: `VoiceInputActivityTest.testTransparentWindowConfiguration`
    - [x] Activity finishes without crash
      - Ensures activity can finish without crashing. ✅
      - Mapping: `VoiceInputActivityTest.testActivityFinishesWithoutCrash`
    - [x] Default locale
      - Checks default locale handling. ✅
      - Mapping: `VoiceInputActivityTest.testDefaultLocale`
    - [x] Locale formatting
      - Verifies locale formatting logic. ✅
      - Mapping: `VoiceInputActivityTest.testLocaleFormatting`
    - [x] Architecture compliance (no direct LimeDB access)
      - Confirms architecture compliance (no direct LimeDB access). ✅
      - Mapping: `VoiceInputActivityTest.testVoiceInputActivityDoesNotAccessLimeDB`
    - [x] Separation of concerns
      - Ensures separation of concerns in activity logic. ✅
      - Mapping: `VoiceInputActivityTest.testSeparationOfConcerns`
    - [x] Multiple activity launches
      - Tests multiple launches of the activity. ✅
      - Mapping: `VoiceInputActivityTest.testMultipleActivityLaunches`
    - [x] Long recognized text
      - Verifies handling of long recognized text. ✅
      - Mapping: `VoiceInputActivityTest.testLongRecognizedText`
    - [x] Recognized text with Unicode
      - Ensures Unicode text is handled in recognition results. ✅
      - Mapping: `VoiceInputActivityTest.testRecognizedTextWithUnicode`

#### **4.10 Additional UI Component Tests**
  - [x] **Adapter Tests**: ManageImAdapterTest.java, ManageRelatedAdapterTest.java
    - [x] ManageImAdapter DiffUtil and item handling
      - Tests DiffUtil and item handling logic in ManageImAdapter. ✅
      - Mapping: `ManageImAdapterTest` (DiffUtil, item handling)
    - [x] ManageRelatedAdapter DiffUtil and item handling
      - Tests DiffUtil and item handling logic in ManageRelatedAdapter. ✅
      - Mapping: `ManageRelatedAdapterTest` (DiffUtil, item handling)
  - [x] **Dialog Tests**: ManageImAddDialogTest.java, ManageImEditDialogTest.java, ManageRelatedAddDialogTest.java, ManageRelatedEditDialogTest.java
    - [x] ManageImAddDialog input validation
      - Verifies input validation logic in ManageImAddDialog. ✅
      - Mapping: `ManageImAddDialogTest` (input validation)
    - [x] ManageImEditDialog input validation
      - Verifies input validation logic in ManageImEditDialog. ✅
      - Mapping: `ManageImEditDialogTest` (input validation)
    - [x] ManageRelatedAddDialog input validation
      - Verifies input validation logic in ManageRelatedAddDialog. ✅
      - Mapping: `ManageRelatedAddDialogTest` (input validation)
    - [x] ManageRelatedEditDialog input validation
      - Verifies input validation logic in ManageRelatedEditDialog. ✅
      - Mapping: `ManageRelatedEditDialogTest` (input validation)
  - [x] **Navigation Tests**: NavigationDrawerFragmentTest.java, NavigationManagerTest.java
    - [x] NavigationDrawerFragment menu and navigation
      - Tests menu and navigation logic in navigation drawer. ✅
      - Mapping: `NavigationDrawerFragmentTest` (menu, navigation)
    - [x] NavigationManager menu and navigation
      - Tests menu and navigation logic in navigation manager. ✅
      - Mapping: `NavigationManagerTest` (menu, navigation)
  - [x] **Manager Tests**: ProgressManagerTest.java, ShareManagerTest.java
    - [x] ProgressManager progress and sharing workflow
      - Validates progress and sharing workflow logic in ProgressManager. ✅
      - Mapping: `ProgressManagerTest` (progress, sharing workflow)
    - [x] ShareManager progress and sharing workflow
      - Validates progress and sharing workflow logic in ShareManager. ✅
      - Mapping: `ShareManagerTest` (progress, sharing workflow)
  - [x] **Handler Tests**: IntentHandlerTest.java
    - [x] IntentHandler intent validation and import flow
      - Verifies intent validation and import flow logic in IntentHandler. ✅
      - Mapping: `IntentHandlerTest` (intent validation, import flow)
  - [x] **Preference Tests**: LIMEPreferenceTest.java
    - [x] LIMEPreference settings and preference change listener
      - Verifies LIMEPreference settings and preference change listener logic. ✅
      - Mapping: `LIMEPreferenceTest` (settings, preference change listener)
  - [x] **Handler Tests**: IntentHandlerTest.java
    - IntentHandler intent validation and import flow✅
      - Tests intent validation and import flow logic in handler.
      - `IntentHandlerTest` (intent validation, import flow)
  - [x] **Preference Tests**: LIMEPreferenceTest.java
    - LIMEPreference settings and preference change listener✅
      - Verifies settings and preference change listener logic in preferences.
      - `LIMEPreferenceTest` (settings, preference change listener)

---

### Phase 5: IME Logic Tests on Android Platform (See Section "Phase 5" for details)

**Objective**: Test the core Input Method Engine (IME) logic on Android platform.

**Test File**: LIMEServiceTest.java
**Test Coverage**: 191 test methods planned across 18 subsections + 103 regression/mock tests (5.19-5.24) = **294 total tests**

#### **5.1 LIMEService Lifecycle Tests** (15 tests)
  - [x] 5.1.1 Service initialization (7 tests)
    - `test_5_1_1_1_ServiceInitialization` - Verifies LIMEService can be instantiated and basic initialization completes ✅
    - `test_5_1_1_2_ServiceAvailability` - Tests service availability check methods return expected values ✅
    - `test_5_1_1_3_DelayConstant` - Validates DELAY_UPDATE_SUGGESTIONS constant is properly defined ✅
    - `test_5_1_1_4_ResourceAccess` - Ensures service can access application resources correctly ✅
    - `test_5_1_1_5_SystemServiceAccess` - Tests access to system services (InputMethodManager, etc.) ✅
    - `test_5_1_1_6_StaticConstants` - Verifies all static constants are initialized with expected values ✅
    - `test_5_1_1_7_Instantiation` - Tests LIMEService instantiation without crashes ✅
  - [x] 5.1.2 Input session lifecycle (5 tests)
    - `test_5_1_2_1_IMListHandling` - Tests IM list loading and management during session ✅
    - `test_5_1_2_2_LifecycleMethods` - Verifies onCreate/onDestroy lifecycle methods execute correctly ✅
    - `test_5_1_2_3_InputMethodServiceMethods` - Tests InputMethodService base class method integration ✅
    - `test_5_1_2_4_OnFinishInput` - Validates onFinishInput() properly cleans up input session state ✅
    - `test_5_1_2_5_OnStartInputView` - Tests onStartInputView() initializes input view correctly ✅
  - [x] 5.1.3 Configuration change handling (3 tests)
    - `test_5_1_3_1_ConfigurationChangeHandling` - Tests service handles configuration changes (rotation, etc.) correctly ✅
    - `test_5_1_3_2_ConfigurationConstants` - Verifies Configuration-related constants are properly defined ✅
    - `test_5_1_3_3_OnConfigurationChanged` - Tests onConfigurationChanged() updates UI state appropriately ✅

#### **5.2 Soft Keyboard / Keyboard View Tests** (20 tests)
  - [x] 5.2.1 Keyboard view creation (6 tests)
    - `test_5_2_1_1_KeyboardViewCreation` - Tests onCreateInputView() creates keyboard view successfully ✅
    - `test_5_2_1_2_KeyboardThemeConstants` - Verifies keyboard theme constants are properly initialized ✅
    - `test_5_2_1_3_IsKeyboardViewHidden` - Tests isKeyboardViewHidden() returns correct visibility state ✅
    - `test_5_2_1_4_RestoreKeyboardViewIfHidden` - Validates restoreKeyboardViewIfHidden() shows hidden keyboard ✅
    - `test_5_2_1_5_OnCreateInputView` - Tests onCreateInputView() complete flow including theme application ✅
  - [x] 5.2.2 Keyboard switching (2 tests)
    - `test_5_2_2_1_KeyboardSwitching` - Tests keyboard switching between different layouts (phonetic, dayi, etc.) ✅
  - [x] 5.2.3 Keyboard key handling (8 tests)
    - `test_5_2_3_1_KeyboardKeyHandling` - Tests onKey() handles keyboard key presses correctly ✅
    - `test_5_2_3_2_KeyCodeConstants` - Verifies KeyEvent.KEYCODE_* constants are properly defined ✅
    - `test_5_2_3_3_KeyEventKeyCodes` - Tests KeyEvent keycode handling for all input keys ✅
    - `test_5_2_3_4_KeyCharacterMapConstants` - Validates KeyCharacterMap constants for character mapping ✅
    - `test_5_2_3_5_OnPress` - Tests onPress() feedback method fires on key press ✅
    - `test_5_2_3_6_OnRelease` - Tests onRelease() cleanup method fires on key release ✅
  - [x] 5.2.4 Keyboard layout variants (2 tests)
    - `test_5_2_4_1_KeyboardModeConstants` - Verifies keyboard mode constants (portrait, landscape) are defined ✅
    - `test_5_2_4_2_SplitKeyboardConstants` - Tests split keyboard layout constants are properly initialized ✅
  - [x] 5.2.5 Shift and meta key handling (2 tests)
    - `test_5_2_5_1_KeyEventMetaStateConstants` - Validates KeyEvent.META_* state constants for shift/alt/ctrl ✅
    - `test_5_2_5_2_UpdateShiftKeyState` - Tests updateShiftKeyState() updates keyboard shift state correctly ✅

#### **5.3 Candidate View / Candidate Window Tests** (11 tests)
  - [x] 5.3.1 Candidate view display (3 tests)
    - `test_5_3_1_1_CandidateViewDisplay` - Tests candidate view displays candidates correctly ✅
    - `test_5_3_1_2_SetCandidatesViewShown` - Validates setCandidatesViewShown() shows/hides candidate window ✅
    - `test_5_3_1_3_OnCreateCandidatesView` - Tests onCreateCandidatesView() creates candidate view successfully ✅
  - [x] 5.3.2 Candidate selection (2 tests)
    - `test_5_3_2_1_PickHighlightedCandidate` - Tests pickHighlightedCandidate() commits highlighted candidate ✅
    - `test_5_3_2_2_PickCandidateManually` - Validates pickCandidateManually() commits user-selected candidate ✅
  - [x] 5.3.3 Candidate list operations (4 tests)
    - `test_5_3_3_1_CandidateListOperations` - Tests candidate list add/remove/clear operations ✅
    - `test_5_3_3_2_UpdateCandidateViewWidthConstraint` - Validates updateCandidateViewWidthConstraint() adjusts view width ✅
    - `test_5_3_3_3_UpdateCandidates` - Tests updateCandidates() refreshes candidate display with new data ✅
    - `test_5_3_3_4_UpdateCandidatesOverload` - Tests updateCandidates() overload with pagination parameters ✅

#### **5.4 Input Handling and Text Composition Tests** (10 tests)
  - [x] 5.4.1 Physical keyboard input (4 tests)
    - `test_5_4_1_1_KeyEventFlags` - Verifies KeyEvent flag constants (ACTION_DOWN, ACTION_UP) are valid ✅
    - `test_5_4_1_2_KeyEventCreation` - Tests KeyEvent object creation with various parameters ✅
    - `test_5_4_1_3_OnKey` - Tests onKey() processes physical keyboard input correctly ✅
    - `test_5_4_1_4_OnKeyBranches` - Validates onKey() branches for different key types (letter, number, special) ✅
  - [x] 5.4.2 Composing text management (3 tests)
    - `test_5_4_2_1_ComposingTextManagement` - Tests composing text buffer management (add, delete, clear) ✅
    - `test_5_4_2_2_ComposingTextHandling` - Validates composing text handling during input process ✅
    - `test_5_4_2_3_ComposingTextOperations` - Tests composing text operations with InputConnection ✅
  - [x] 5.4.3 Composing text edge cases (2 tests)
    - `test_5_4_3_1_ComposingTextEdgeCases` - Tests composing text edge cases (empty, max length, unicode) ✅
    - `test_5_4_3_2_OnText` - Validates onText() handles raw text input correctly ✅

#### **5.5 English Prediction and Mixed Input Tests** (5 tests)
  - [x] 5.5.1 English prediction mode (4 tests)
    - `test_5_5_1_1_TempEnglishWordOperations` - Tests tempEnglishWord buffer operations (add, get, clear) ✅
    - `test_5_5_1_2_TempEnglishListOperations` - Validates tempEnglishList management for English suggestions ✅
    - `test_5_5_1_3_SetSuggestions` - Tests setSuggestions() updates English prediction list ✅
    - `test_5_5_1_4_ResetTempEnglishWord` - Validates resetTempEnglishWord() clears English prediction state ✅
  - [x] 5.5.2 Language mode switching (1 test)
    - `test_5_5_2_1_LanguageModeSwitching` - Tests switching between Chinese and English input modes ✅

#### **5.6 Chinese Han Conversion and Emoji Tests** (5 tests)
  - [x] 5.6.1 Han conversion (Traditional ↔ Simplified) (3 tests)
    - `test_5_6_1_1_UnicodeSurrogateHandling` - Tests Unicode surrogate pair handling in Han conversion ✅
    - `test_5_6_1_2_HanConvertOptions` - Validates Han conversion options (Traditional→Simplified, Simplified→Traditional) ✅
    - `test_5_6_1_3_UnicodeHandling` - Tests Unicode character handling in conversion process ✅
  - [x] 5.6.2 Emoji input (2 tests)
    - `test_5_6_2_1_EmojiModeSetting` - Tests emoji mode enable/disable setting ✅
    - `test_5_6_2_2_EmojiDisplayPositionSetting` - Validates emoji display position preference (inline, separate) ✅

#### **5.7 Audio/Haptic Feedback Tests** (6 tests)
  - [x] 5.7.1 Sound feedback (2 tests)
    - `test_5_7_1_1_SoundFeedback` - Tests sound feedback triggers on key press ✅
    - `test_5_7_1_2_AudioManagerSoundEffects` - Validates AudioManager.playSoundEffect() integration ✅
  - [x] 5.7.2 Vibration feedback (3 tests)
    - `test_5_7_2_1_VibrationFeedback` - Tests vibration feedback triggers on key press ✅
    - `test_5_7_2_2_VibrationEffectCompatibility` - Validates VibrationEffect API compatibility (API 26+) ✅
    - `test_5_7_2_3_VibratorManagerCompatibility` - Tests VibratorManager compatibility (API 31+) ✅
  - [x] 5.7.3 Audio and haptic coordination (1 test)
    - `test_5_7_3_1_DoVibrateSound` - Tests doVibrateSound() coordinates both audio and haptic feedback ✅

#### **5.8 Swipe Gesture Tests** (2 tests)
  - [x] 5.8.1 Swipe gestures (2 tests)
    - `test_5_8_1_1_SwipeMethods` - Tests swipe gesture detection (left, right, up, down) ✅
    - `test_5_8_1_2_SwipeMethodsDirect` - Validates direct swipe method invocation and handling ✅

#### **5.9 Voice Input Integration Tests** (11 tests)
  - [x] 5.9.1 Voice input launch (7 tests)
    - `test_5_9_1_1_VoiceInputIntentCreation` - Tests voice input Intent creation with correct action ✅
    - `test_5_9_1_2_VoiceInputActivityAvailability` - Validates voice input activity availability check ✅
    - `test_5_9_1_3_VoiceRecognitionAvailability` - Tests RecognizerIntent.ACTION_RECOGNIZE_SPEECH availability ✅
    - `test_5_9_1_4_VoiceIMEDetection` - Validates detection of installed voice IME ✅
    - `test_5_9_1_5_VoiceInputActivityConstants` - Tests VoiceInputActivity constant definitions ✅
    - `test_5_9_1_6_VoiceInputActivityIntentFlags` - Validates Intent flags for voice input launch ✅
    - `test_5_9_1_7_VoiceInputConstants` - Tests voice input related constants (actions, extras) ✅
  - [x] 5.9.2 Voice input result handling (3 tests)
    - `test_5_9_2_1_VoiceInputBroadcastReceiver` - Tests broadcast receiver for voice input results ✅
    - `test_5_9_2_2_VoiceInputIMEIdStorage` - Validates storage and retrieval of voice IME ID ✅
    - `test_5_9_2_3_VoiceInputBroadcastReceiverRegistration` - Tests broadcast receiver registration/unregistration ✅

#### **5.10 IM Picker and Options Menu Tests** (1 test)
  - [x] 5.10.1 IM picker (1 test)
    - `test_5_10_1_1_IMPicker` - Tests IM picker dialog display and IM selection ✅

#### **5.11 Fullscreen Mode Tests** (2 tests)
  - [x] 5.11.1 Fullscreen editing mode (2 tests)
    - `test_5_11_1_1_DisplayMetricsHandling` - Tests DisplayMetrics access and screen dimension handling ✅
    - `test_5_11_1_2_OnEvaluateFullscreenMode` - Validates onEvaluateFullscreenMode() returns correct fullscreen state ✅

#### **5.12 Window Insets and Layout Tests** (1 test)
  - [x] 5.12.1 Window insets handling (1 test)
    - `test_5_12_1_1_WindowInsetsHandling` - Tests WindowInsets handling for proper IME positioning ✅

#### **5.13 Input Connection Integration Tests** (6 tests)
  - [x] 5.13.1 InputConnection operations (6 tests)
    - `test_5_13_1_1_EditorInfoTypeMasks` - Verifies EditorInfo.TYPE_MASK_* constants for input type detection ✅
    - `test_5_13_1_2_EditorInfoTypeClasses` - Tests EditorInfo.TYPE_CLASS_* constants (text, number, phone) ✅
    - `test_5_13_1_3_EditorInfoVariations` - Validates EditorInfo.TYPE_*_VARIATION_* constants ✅
    - `test_5_13_1_4_EditorInfoCreation` - Tests EditorInfo object creation and field initialization ✅
    - `test_5_13_1_5_OnDisplayCompletions` - Tests onDisplayCompletions() handles completion suggestions ✅
    - `test_5_13_1_6_KeyDownUp` - Validates keyDownUp() sends key events to InputConnection ✅

#### **5.14 Mapping and Record Handling Tests** (4 tests)
  - [x] 5.14.1 Mapping data handling (4 tests)
    - `test_5_14_1_1_MappingDataHandling` - Tests Mapping object creation and field access ✅
    - `test_5_14_1_2_MappingRecordTypes` - Validates Mapping.RECORD_TYPE_* constants (normal, user, related) ✅
    - `test_5_14_1_3_MappingOperations` - Tests Mapping operations (getWord, getCode, getScore) ✅
    - `test_5_14_1_4_RequestFullRecords` - Tests requestFullRecords flag handling in queries ✅

#### **5.15 Character Validation Tests** (3 tests)
  - [x] 5.15.1 Character type validation (3 tests)
    - `test_5_15_1_1_CharacterTypeValidation` - Tests Character.isLetter/isDigit/isWhitespace validation ✅
    - `test_5_15_1_2_CharacterValidationEdgeCases` - Validates character validation edge cases (unicode, emoji) ✅
    - `test_5_15_1_3_ValidationHelpers` - Tests helper methods for character type checking ✅

#### **5.16 Preference Integration Tests** (6 tests)
  - [x] 5.16.1 Preference manager integration (6 tests)
    - `test_5_16_1_1_PreferenceManagerIntegration` - Tests LIMEPref integration and preference access ✅
    - `test_5_16_1_2_ShowArrowKeysSetting` - Validates show arrow keys preference get/set ✅
    - `test_5_16_1_3_SplitKeyboardSetting` - Tests split keyboard preference get/set ✅
    - `test_5_16_1_4_SelkeyOptionSetting` - Validates selection key option preference ✅
    - `test_5_16_1_5_PreferenceDefaultValues` - Tests preference default value initialization ✅
    - `test_5_16_1_6_PreferenceBoundaryValues` - Validates preference boundary value handling ✅

#### **5.17 SearchServer Integration Tests (via LIMEService)** (1 test)
  - [x] 5.17.1 SearchServer lookup from LIMEService (1 test)
    - `test_5_17_1_1_SearchServerLookup` - Tests SearchServer access and query operations from LIMEService ✅

#### **5.18 Error Handling and Edge Cases** (11 tests)
  - [x] 5.18.1 Null input handling (2 tests)
    - `test_5_18_1_1_NullInputHandling` - Tests null input parameter handling without crashes ✅
    - `test_5_18_1_2_MappingNullHandling` - Validates null Mapping object handling ✅
  - [x] 5.18.2 Empty string handling (1 test)
    - `test_5_18_2_1_EmptyStringHandling` - Tests empty string input handling without errors ✅
  - [x] 5.18.3 Boundary conditions (5 tests)
    - `test_5_18_3_1_CandidateIndexValidation` - Validates candidate index boundary checking (0 to size-1) ✅
    - `test_5_18_3_2_StringLengthEdgeCases` - Tests string length edge cases (empty, max length) ✅
    - `test_5_18_3_3_IndexBoundsValidation` - Tests index bounds validation in all list operations ✅
    - `test_5_18_3_4_ListOperationsEdgeCases` - Validates list operation edge cases (empty, single item) ✅
    - `test_5_18_3_5_StringBuilderEdgeCases` - Tests StringBuilder edge cases in composing text ✅

 **Phase 5 Regression Extensions (RegressionTest.java, moved from Phase 8)**


#### **5.19 IM Switching (LIMEService)** (1 test) **COMPLETED**
  - [x] Switch between IM types - `test_5_19_SwitchBetweenIM` - Tests switching between different IM types (phonetic, dayi, array) ✅

#### **5.20 LIMEService Voice Input Integration** (15 tests) **COMPLETED**
  - [x] Voice input launch - `test_5_20_1_VoiceInputLaunch` - Tests launching voice input activity from keyboard ✅
  - [x] Voice IME unavailable fallback - `test_5_20_2_VoiceIMEUnavailableFallback` - Validates fallback behavior when voice IME unavailable ✅
  - [x] Voice input intent configuration - `test_5_20_3_VoiceInputIntentConfiguration` - Tests voice input Intent configuration (language, prompts) ✅
  - [x] IME change monitoring setup - `test_5_20_4_IMEChangeMonitoringSetup` - Validates IME change monitoring for voice input completion ✅
  - [x] Switch back to LIME - `test_5_20_5_SwitchBackToLIME` - Tests automatic switch back to LIME after voice input ✅
  - [x] Voice input broadcast receiver - `test_5_20_6_VoiceInputBroadcastReceiver` - Validates broadcast receiver receives voice input results ✅
  - [x] Voice input null IMM - `test_5_20_7_VoiceInputNullIMM` - Tests null InputMethodManager handling in voice input ✅
  - [x] Voice input security exception - `test_5_20_8_VoiceInputSecurityException` - Validates SecurityException handling in voice input launch ✅
  - [x] Voice input receiver unregister error - `test_5_20_9_VoiceInputReceiverUnregisterError` - Tests error handling in receiver unregistration ✅
  - [x] Voice input monitoring timeout - `test_5_20_10_VoiceInputMonitoringTimeout` - Validates timeout handling for voice input monitoring ✅
  - [x] Voice input from candidate view - `test_5_20_11_VoiceInputFromCandidateView` - Tests voice input launch from candidate view button ✅
  - [x] Voice input results insertion - `test_5_20_12_VoiceInputResultsInsertion` - Validates voice recognition results insertion into text ✅
  - [x] Voice input with composing text - `test_5_20_13_VoiceInputWithComposingText` - Tests voice input with existing composing text ✅
  - [x] Multiple voice input invocations - `test_5_20_14_MultipleVoiceInputInvocations` - Validates multiple consecutive voice input invocations ✅
  - [x] Voice input disabled preference - `test_5_20_15_VoiceInputDisabledPreference` - Tests voice input behavior when disabled in preferences ✅

#### **5.21 LIMEService IME Selection and Options Menu** (22 tests) **COMPLETED**
  - [x] Options menu invocation - `test_5_21_1_OptionsMenuInvocation` - Tests options menu display on long-press or menu key ✅
  - [x] IM picker menu item selection - `test_5_21_2_IMPickerMenuItemSelection` - Validates IM picker menu item triggers picker dialog ✅
  - [x] Settings menu item selection - `test_5_21_3_SettingsMenuItemSelection` - Tests settings menu item launches settings activity ✅
  - [x] Han converter menu item selection - `test_5_21_4_HanConverterMenuItemSelection` - Validates Han converter menu item toggles conversion mode ✅
  - [x] IM picker dialog creation - `test_5_21_5_IMPickerDialogCreation` - Tests IM picker dialog creation with IM list ✅
  - [x] IM selection from picker - `test_5_21_6_IMSelectionFromPicker` - Validates IM selection from picker switches active IM ✅
  - [x] IM picker empty list - `test_5_21_7_IMPickerEmptyList` - Tests IM picker behavior with empty IM list ✅
  - [x] IM picker dialog dismissal - `test_5_21_8_IMPickerDialogDismissal` - Validates IM picker dialog dismissal without selection ✅
  - [x] Build activated IM list filtering - `test_5_21_9_BuildActivatedIMListFiltering` - Tests buildActivatedIMList() filters enabled IMs only ✅
  - [x] Switch to next IM forward - `test_5_21_10_SwitchToNextIMForward` - Validates switchToNextIM(forward=true) cycles to next IM ✅
  - [x] Switch to next IM backward - `test_5_21_11_SwitchToNextIMBackward` - Tests switchToNextIM(forward=false) cycles to previous IM ✅
  - [x] IM switching single IM - `test_5_21_12_IMSwitchingSingleIM` - Validates IM switching behavior with only one enabled IM ✅
  - [x] Build activated IM list empty state - `test_5_21_13_BuildActivatedIMList_EmptyState` - Tests buildActivatedIMList() with no preferences (all IMs activated by default) ✅
  - [x] Build activated IM list with state - `test_5_21_14_BuildActivatedIMList_WithState` - Validates buildActivatedIMList() with specific enabled IMs from preferences ✅
  - [x] Switch to next IM forward detailed - `test_5_21_15_SwitchToNextIM_Forward` - Tests detailed forward IM switching through multiple IMs ✅
  - [x] Switch to next IM backward detailed - `test_5_21_16_SwitchToNextIM_Backward` - Validates detailed backward IM switching through multiple IMs ✅
  - [x] Switch to next IM wrap forward - `test_5_21_17_SwitchToNextIM_WrapForward` - Tests IM switching wraps from last to first IM ✅
  - [x] Switch to next IM wrap backward - `test_5_21_18_SwitchToNextIM_WrapBackward` - Validates IM switching wraps from first to last IM ✅
  - [x] Switch to next IM single IM detailed - `test_5_21_19_SwitchToNextIM_SingleIM` - Tests IM switching with only one enabled IM (should remain on same IM) ✅
  - [x] Build activated IM list index bounds - `test_5_21_20_BuildActivatedIMList_IndexOutOfBounds` - Validates buildActivatedIMList() handles invalid IM indices gracefully ✅
  - [x] Switch to next IM active not in list - `test_5_21_21_SwitchToNextIM_ActiveNotInList` - Tests switching when current IM is not in activated list ✅
  - [x] Build activated IM list cache hit - `test_5_21_22_BuildActivatedIMList_CacheHit` - Validates buildActivatedIMList() uses cached results when preferences unchanged ✅

#### **5.22 LIMEService Theme and UI Styling** (6 tests) **COMPLETED**
  - [x] Keyboard theme retrieval - `test_5_22_1_KeyboardThemeRetrieval` - Tests getKeyboardTheme() retrieves current theme ID ✅
  - [x] Theme application to keyboard - `test_5_22_2_ThemeApplicationToKeyboard` - Validates theme application updates keyboard colors/styles ✅
  - [x] Invalid theme ID handling - `test_5_22_3_InvalidThemeIDHandling` - Tests invalid theme ID falls back to default theme ✅
  - [x] Navigation bar icon styling - `test_5_22_4_NavigationBarIconStyling` - Validates navigation bar icon color styling (light/dark) ✅
  - [x] Navigation bar styling API level - `test_5_22_5_NavigationBarStylingAPILevel` - Tests navigation bar styling compatibility (API 26+) ✅
  - [x] Navigation bar styling exception - `test_5_22_6_NavigationBarStylingException` - Validates exception handling in navigation bar styling ✅

#### **5.23 LIMEService Methods with Mock Dependencies** (50 tests) **COMPLETED**
  - [x] Clear suggestions with mocks - `test_5_23_1_ClearSuggestionsWithMockCandidateView` - Tests clearSuggestions() with mocked candidate view ✅
  - [x] Finish composing with mocks - `test_5_23_2_FinishComposingWithMocks` - Validates finishComposing() with mocked dependencies ✅
  - [x] Handle shift with mocks - `test_5_23_3_HandleShiftWithMocks` - Tests handleShift() with mocked keyboard state ✅
  - [x] Do vibrate sound with mocks - `test_5_23_4_DoVibrateSoundWithMocks` - Validates doVibrateSound() with mocked audio/vibration ✅
  - [x] Switch keyboard with mocks - `test_5_23_5_SwitchKeyboardWithMocks` - Tests switchKeyboard() with mocked keyboard switcher ✅
  - [x] Initial IM keyboard with mocks - `test_5_23_6_InitialIMKeyboardWithMocks` - Validates initial IM keyboard setup with mocks ✅
  - [x] Initial view and switcher with mocks - `test_5_23_7_InitialViewAndSwitcherWithMocks` - Tests view and switcher initialization with mocks ✅
  - [x] Hide candidate view with mocks - `test_5_23_8_HideCandidateViewWithMocks` - Validates hiding candidate view with mocks ✅
  - [x] Toggle caps lock with mocks - `test_5_23_9_ToggleCapsLockWithMocks` - Tests caps lock toggle with mocked keyboard ✅
  - [x] Update shift key state with mocks - `test_5_23_10_UpdateShiftKeyStateWithMocks` - Validates shift key state updates with mocks ✅
  - [x] Restore keyboard view with mocks - `test_5_23_11_RestoreKeyboardViewWithMocks` - Tests keyboard view restoration with mocks ✅
  - [x] Is keyboard view hidden with mocks - `test_5_23_12_IsKeyboardViewHiddenWithMocks` - Validates keyboard visibility check with mocks ✅
  - [x] Set suggestions with mocks - `test_5_23_13_SetSuggestionsWithMocks` - Tests setSuggestions() with mocked candidate view ✅
  - [x] Handle character with mocks - `test_5_23_14_HandleCharacterWithMocks` - Validates character handling with mocked input ✅
  - [x] Handle selkey with mocks - `test_5_23_15_HandleSelkeyWithMocks` - Tests selection key handling with mocks ✅
  - [x] Pick candidate manually with mocks - `test_5_23_16_PickCandidateManuallyWithMocks` - Validates manual candidate selection with mocks ✅
  - [x] Switch Chi/Eng with mocks - `test_5_23_17_SwitchChiEngWithMocks` - Tests Chinese/English mode switching with mocks ✅
  - [x] On press with mocks - `test_5_23_18_OnPressWithMocks` - Validates onPress() callback with mocks ✅
  - [x] Update candidates with mocks - `test_5_23_19_UpdateCandidatesWithMocks` - Tests updateCandidates() with mocked candidate view ✅
  - [x] Update related phrase with mocks - `test_5_23_20_UpdateRelatedPhraseWithMocks` - Validates related phrase updates with mocks ✅
  - [x] Show IM picker with mocks - `test_5_23_21_ShowIMPickerWithMocks` - Tests IM picker display with mocks ✅
  - [x] Show Han convert picker with mocks - `test_5_23_22_ShowHanConvertPickerWithMocks` - Validates Han converter picker with mocks ✅
  - [x] Switch to next activated IM with mocks - `test_5_23_23_SwitchToNextActivatedIMWithMocks` - Tests IM switching with mocks ✅
  - [x] Build activated IM list with mocks - `test_5_23_24_BuildActivatedIMListWithMocks` - Validates IM list building with mocks ✅
  - [x] Start voice input with mocks - `test_5_23_25_StartVoiceInputWithMocks` - Tests voice input launch with mocks ✅
  - [x] Launch recognizer intent with mocks - `test_5_23_26_LaunchRecognizerIntentWithMocks` - Validates recognizer Intent launch with mocks ✅
  - [x] Vibrate with mocks - `test_5_23_27_VibrateWithMocks` - Tests vibration with mocked Vibrator ✅
  - [x] Check toggle caps lock with mocks - `test_5_23_28_CheckToggleCapsLockWithMocks` - Validates caps lock toggle check with mocks ✅
  - [x] Init candidate view with mocks - `test_5_23_29_InitCandidateViewWithMocks` - Tests candidate view initialization with mocks ✅
  - [x] Show candidate view with mocks - `test_5_23_30_ShowCandidateViewWithMocks` - Validates candidate view display with mocks ✅
  - [x] Set candidates view shown with mocks - `test_5_23_31_SetCandidatesViewShownWithMocks` - Tests setCandidatesViewShown() with mocks ✅
  - [x] On compute insets with mocks - `test_5_23_32_OnComputeInsetsWithMocks` - Validates onComputeInsets() with mocks ✅
  - [x] On evaluate fullscreen mode with mocks - `test_5_23_33_OnEvaluateFullscreenModeWithMocks` - Tests fullscreen mode evaluation with mocks ✅
  - [x] On create with mocks - `test_5_23_34_OnCreateWithMocks` - Validates onCreate() lifecycle with mocks ✅
  - [x] On initialize interface with mocks - `test_5_23_35_OnInitializeInterfaceWithMocks` - Tests onInitializeInterface() with mocks ✅
  - [x] On create candidates view with mocks - `test_5_23_36_OnCreateCandidatesViewWithMocks` - Validates onCreateCandidatesView() with mocks ✅
  - [x] Handle options with mocks - `test_5_23_37_HandleOptionsWithMocks` - Tests handleOptions() with mocked menu ✅
  - [x] Handle options lambda with mocks - `test_5_23_38_HandleOptionsLambdaWithMocks` - Validates handleOptions() lambda callback with mocks ✅
  - [x] Commit typed with mocks - `test_5_23_39_CommitTypedWithMocks` - Tests commitTyped() with mocked input connection ✅
  - [x] Init on start input with mocks - `test_5_23_40_InitOnStartInputWithMocks` - Validates initOnStartInput() with mocks ✅
  - [x] Translate key down with mocks - `test_5_23_41_TranslateKeyDownWithMocks` - Tests translateKeyDown() with mocked keyboard events ✅
  - [x] Translate key down lambda with mocks - `test_5_23_42_TranslateKeyDownLambdaWithMocks` - Validates translateKeyDown() lambda with mocks ✅
  - [x] Get vibrator with mocks - `test_5_23_43_GetVibratorWithMocks` - Tests getVibrator() with mocked system service ✅
  - [x] Update English prediction with mocks - `test_5_23_44_UpdateEnglishPredictionWithMocks` - Validates updateEnglishPrediction() with mocks ✅
  - [x] On text with mocks - `test_5_23_45_OnTextWithMocks` - Tests onText() with mocked input ✅
  - [x] Restore keyboard lambda with mocks - `test_5_23_46_RestoreKeyboardLambdaWithMocks` - Validates restoreKeyboard() lambda with mocks ✅
  - [x] Switch back to LIME with mocks - `test_5_23_47_SwitchBackToLIMEWithMocks` - Tests switchBackToLIME() with mocked IMM ✅
  - [x] Start monitoring IME changes lambda with mocks - `test_5_23_48_StartMonitoringIMEChangesLambdaWithMocks` - Validates IME change monitoring with mocks ✅
  - [x] Register voice input receiver with mocks - `test_5_23_49_RegisterVoiceInputReceiverWithMocks` - Tests voice input receiver registration with mocks ✅
  - [x] Set navigation bar icons dark with mocks - `test_5_23_50_SetNavigationBarIconsDarkWithMocks` - Validates navigation bar icon styling with mocks ✅

#### **5.24 LIMEService with StubActivity Context Tests** (9 tests) **COMPLETED**
  - [x] onCreate with Context - `test_5_24_1_OnCreateWithContext` - Tests LIMEService.onCreate() executes successfully (initializes SearchSrv, mLIMEPref, buildActivatedIMList, voice receiver) (lines 254-285) ✅
  - [x] showIMPicker with Context - `test_5_24_2_ShowIMPickerWithContext` - Tests showIMPicker() dialog method (lines 2203-2248) ✅
  - [x] showHanConvertPicker with Context - `test_5_24_3_ShowHanConvertPickerWithContext` - Tests showHanConvertPicker() dialog method (lines 2162-2191) ✅
  - [x] handleOptions with Context - `test_5_24_4_HandleOptionsWithContext` - Tests handleOptions() options menu dialog (lines 1910-2024) ✅
  - [x] launchSettings with Context - `test_5_24_5_LaunchSettingsWithContext` - Tests launchSettings() Intent creation and launch (lines 2023-2032) ✅
  - [x] launchRecognizerIntent with Context - `test_5_24_6_LaunchRecognizerIntentWithContext` - Tests launchRecognizerIntent() voice recognition (lines 3931-3945) ✅
  - [x] vibrate with Context - `test_5_24_7_VibrateWithContext` - Tests vibrate() Vibrator system service access and API branching (lines 3684-3698) ✅
  - [x] doVibrateSound with Context - `test_5_24_8_DoVibrateSoundWithContext` - Tests doVibrateSound() audio/vibration preferences (lines 3701-3730) ✅
  - [x] switchToNextActivatedIM with Context - `test_5_24_9_SwitchToNextActivatedIMWithContext` - Tests switchToNextActivatedIM() IM switching logic (lines 2035-2088) ✅


**Phase 5 Total**: 191 existing tests + 121 regression/mock tests (5.19-5.24) = **312 tests** (Target: 90% coverage)

---

### Phase 6: Integration Tests (See Section "Phase 6" for details)

**Objective**: Test interactions between layers with real implementations.

**Precondition**: Both LIME.IM_PHONETIC and LIME.IM_DAYI cloud data preloaded at test class startup.

**Test Coverage**: 60+ test methods

#### **6.1 SearchServer → LimeDB Integration** (5 tests)
  - [x] Test: Complete search flow with REAL IM data (3 tests)
  - [x] Test: Configuration operations (3 tests)

#### **6.2 DBServer → LimeDB Integration** (6 tests)
  - [x] Test: Export flow with REAL IM data (3 tests)
  - [x] Test: Import flow (4 tests)

#### **6.3 UI → SearchServer Integration (Complete Flow)** (25 tests)
  - **6.3.1 Basic UI Operation Flow** (3 tests)
    - [x] Test: Complete UI operation flow (3 tests)
  - **6.3.2 Remote Import + Hot Path Queries** (8 tests)
    - [x] Precondition: Cloud URLs available (2 tests)
    - [x] Test: Download + Import smallest IM DBs (2 tests)
    - [x] Test: Hot path query latency and caching (4 tests)
    - [x] Test: Error handling on network failures (1 test)
  - **6.3.3 Learning Path (User Records) — Query Behavior** (3 tests)
    - [x] Precondition: Table imported (1 test)
    - [x] Test: Learned entries influence results (2 tests)
    - [x] Test: Cache respects learning updates (1 test)

#### **6.4 UI → DBServer → LimeDB Integration** (6 tests)
  - [x] Test: Complete file operation flow (6 tests)

#### **6.5 Backup Path (User Records) — Before Overwrite** (4 tests)
  - [x] Test: Explicit backup on clear table (2 tests)
  - [x] Test: Backup during import (restore flag path) (2 tests)

#### **6.6 Restore Path (User Records) — After Import** (5 tests)
  - [x] Test: Restore after import (3 tests)
  - [x] Test: No-restore path (1 test)
  - [x] Test: Error handling (2 tests)
  - [x] Test: UI refresh after restore (1 test)

---

### Phase 7: Architecture Compliance Tests (See Section "Phase 7" for details)

**Objective**: Verify architectural boundaries are respected.

**Test Coverage**: 10+ test methods

#### **7.1 Static Analysis Tests** (3 tests)
  - [x] Test: No direct LimeDB access from UI
  - [x] Test: No SQL operations outside LimeDB
  - [x] Test: No file operations outside DBServer

#### **7.2 Runtime Architecture Tests** (2 tests)
  - [x] Test: Component initialization
  - [x] Test: Method call tracing

---

### Phase 8: Regression Tests - Core IME End-to-End Workflows (See Section "Phase 8" for details)

**Objective**: Ensure core user-facing IME functionality still works after refactoring. These are the most critical tests for LIME IME's query and learning logic.

**Test Coverage**: 38+ test methods covering complete input → query → learning workflows

**Test File**: `IntegrationTestIMELogic.java`

**Note**: Edge case testing (null handling, empty data, invalid input) is already covered by Phase 1-2 unit tests in LimeDBTest and SearchServerTest. Phase 8 focuses on end-to-end user workflows with real IM data.

#### **8.1 Soft Keyboard Input Integration** (2 tests) ✅ **COMPLETED**
  - [x] Soft keyboard input → query → candidates - `test_8_1_SoftKeyboardInputWithRealData` ✅
  - [x] Incremental composing text - `test_8_1_IncrementalComposingText` ✅

#### **8.2 Hard Keyboard Input Integration** (1 test) ✅ **COMPLETED**
  - [x] Hardware keyboard input - `test_8_2_HardwareKeyboardInput` ✅

#### **8.3 Query and Caching Path** (1 test) ✅ **COMPLETED**
  - [x] Query latency and caching - `test_8_3_QueryLatencyAndCaching` ✅

#### **8.4 Learning Path Integration** (27 tests) ✅ **COMPLETED**
  - [x] Score update after selection - `test_8_4_ScoreUpdateAfterSelection` ✅
  - **8.4.1 learnRelatedPhraseAndUpdateScore() Tests** (4 tests) ✅
    - [x] Basic functionality - `test_8_4_1_LearnRelatedPhraseAndUpdateScore` ✅
    - [x] With null mapping - `test_8_4_1_LearnWithNullMapping` ✅
    - [x] Thread safety - `test_8_4_1_LearnThreadSafety` ✅
    - [x] Score accumulation - `test_8_4_1_ScoreAccumulation` ✅
  - **8.4.2 learnRelatedPhrase() Tests** (6 tests) ✅
    - [x] Consecutive word learning - `test_8_4_2_LearnRelatedPhraseConsecutive` ✅
    - [x] With preference disabled - `test_8_4_2_LearnRelatedPhraseDisabled` ✅
    - [x] With single word - `test_8_4_2_LearnRelatedPhraseSingleWord` ✅
    - [x] Skips null/empty mappings - `test_8_4_2_LearnRelatedPhraseSkipNull` ✅
    - [x] With punctuation symbols - `test_8_4_2_LearnRelatedPhraseWithPunctuation` ✅
    - [x] Triggers LD phrase learning - `test_8_4_2_LearnRelatedPhraseTriggersLD` ✅
  - **8.4.3 learnLDPhrase() Tests** (9 tests) ✅
    - [x] Basic two-character phrase - `test_8_4_3_LearnLDPhraseTwoChar` ✅
    - [x] Three-character phrase - `test_8_4_3_LearnLDPhraseThreeChar` ✅
    - [x] Four-character phrase limit - `test_8_4_3_LearnLDPhraseFourCharLimit` ✅
    - [x] Skips English mixed mode - `test_8_4_3_LearnLDPhraseSkipsEnglish` ✅
    - [x] With multi-character base word - `test_8_4_3_LearnLDPhraseMultiCharBase` ✅
    - [x] Handles null codes via reverse lookup - `test_8_4_3_LearnLDPhraseReverseLookup` ✅
    - [x] Abandons on failed reverse lookup - `test_8_4_3_LearnLDPhraseAbandonOnFailedLookup` ✅
    - [x] Skips partial match records - `test_8_4_3_LearnLDPhraseSkipsPartialMatch` ✅
    - [x] Skips composing code and English - `test_8_4_3_LearnLDPhraseSkipsComposing` ✅
  - **8.4.4 Integration Tests: Complete Learning Flow** (3 tests) ✅
    - [x] Complete flow - score → related → LD phrase - `test_8_4_4_CompleteLearningFlow` ✅
    - [x] Learning flow with preference combinations - `test_8_4_4_LearningPreferenceCombinations` ✅
    - [x] Learning persistence across IME sessions - `test_8_4_4_LearningPersistenceAcrossSessions` ✅

**Phase 8 Total**: 31 existing tests (8.1-8.4). Sections 5.19-5.22 are tracked under Phase 5 metrics.

**Note**: Sections 8.9+ were removed to avoid duplicating Phase 3 and Phase 5 tests. Phase 8 regression tests focus on end-to-end workflows that uniquely benefit from the real IM data environment (8.1-8.4). Additional coverage will come from Phase 3.16-3.17 unit tests (SearchServer) and Phase 5.27-5.31 unit/integration tests (LIMEService) - those phases already include regression tests where real data provides value.

---

### Phase 9: Performance Tests (See Section "Phase 9" for details)

**Objective**: Ensure no performance regression.

**Test Coverage**: 10+ benchmark tests

#### **9.1 Database Operation Benchmarks** (3 tests)
  - [x] Benchmark: Count operations
  - [x] Benchmark: Search operations
  - [x] Benchmark: Backup/import operations

#### **9.2 File Operation Benchmarks** (2 tests)
  - [x] Benchmark: Export operations
  - [x] Benchmark: Import operations

#### **9.3 Memory Usage** (1 test)
  - [x] Test: Memory leaks

---

## Total Test Coverage Summary

| Phase | Test Methods | Status | Priority |
|-------|--------------|--------|----------|
| Phase 1: LimeDB Layer | 40+ | ✅ Completed | High |
| Phase 2: DBServer Layer | 35+ | ✅ Completed | High |
| Phase 3: SearchServer Layer | 93 (50 existing + 43 new) | 🟡 Partial | High |
| Phase 4: UI Component Tests | 50+ | 🟡 Partial | Medium |
| Phase 5: IME Logic Tests | 225 (incl. RegressionTest 5.1-5.4 from Phase 8) | ⚠️ Planned | High |
| Phase 6: Integration Tests | 22+ | ✅ Completed | High |
| Phase 7: Architecture Compliance | 10+ | ✅ Completed | High |
| Phase 8: Regression Tests (Core IME End-to-End) | 31 (8.1-8.4 complete; 5.1-5.4 tracked under Phase 5) | ✅ Completed | **Critical** |
| Phase 9: Performance Tests | 6 | ✅ Completed | Medium |
| **TOTAL** | **~521 tests (reclassified 5.1-5.4 to Phase 5)** | | |

**Legend:**
- ✅ Completed - All tests implemented and passing
- 🟡 Partial - Some tests implemented
- ⚠️ Planned - Tests exist but need verification
- ❌ Not Started - Tests not yet implemented

**Coverage Improvement Summary:**

Based on JaCoCo coverage report (December 27, 2025), the following improvements are planned:

- **SearchServer.java**: Current 50% → Target 70-80% (+20-30% gain via 43 new tests)
  - Missing: Runtime suggestion logic, caching internals, learning algorithms, English prediction

- **LIMEService.java**: Current 29% → Target 60-70% (+31-40% gain via 78 new tests)
  - Missing: Input handling core, candidate management, keyboard switching, composing text, learning integration, options menu

- **Overall Project**: Current 41% → Target 55-60% (+14-19% gain via 121 new tests)

**Testing Strategy:**

- SearchServer: Unit testing with mocked LimeDB, test cache/learning algorithms in isolation
- LIMEService: Integration testing with real InputMethodService framework, MockInputConnection for text verification
- Priority: Critical path first (input handling, candidates) → Learning logic → Caching → UI features

**Risk Mitigation:**

- Critical hot paths (executed every keystroke) currently have 0% coverage
- Learning algorithms (user data integrity) untested
- Cache corruption risks in production without validation tests

ew = ~521 tests** | **~75% → Target 85%** | |
IME End-to-End) | 28 | ✅ Completed | **Critical** |
 40+ | ✅ Completed | High |
| Phase 2: DBServer Layer | 35+ | ✅ Completed | High |
| Phase 3: SearchServer Layer | 50+ | ✅ Completed | High |
| Phase 4: UI Component Tests | 50+ | 🟡 Partial (VoiceInputActivity done) | Medium |
| Phase 5: IME Logic Tests | 113 | ⚠️ Planned (needs verification) | High |
| Phase 6: Integration Tests | 22+ | ✅ Completed (SearchServer-DBServer paths) | High |
| Phase 7: Architecture Compliance | 10+ | ✅ Completed | High |
| Phase 8: Regression Tests (Core IME End-to-End) | 38 | ✅ Completed | **Critical** |
| Phase 9: Performance Tests | 6 | ✅ Completed | Medium |
| **TOTAL** | **~400 tests** | **~75% Complete** | |

**Legend:**
- ✅ Completed - All tests implemented and passing
- 🟡 Partial - Some tests implemented
- ⚠️ Planned - Tests exist but need verification
- ❌ Not Started - Tests not yet implemented

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         LIMEService (InputMethodService)                     │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │  Input Handling Layer                                                   │ │
│  │  - onKey() / onKeyDown() / onKeyUp()                                    │ │
│  │  - Composing text management                                            │ │
│  │  - InputConnection operations                                           │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────┐  ┌──────────────────────────────────────┐ │
│  │  LIMEKeyboardView            │  │  CandidateView / CandidateViewContainer│ │
│  │  (Soft Keyboard)             │  │  (Candidate Window)                    │ │
│  │  - LIMEKeyboard              │  │  - Candidate selection                 │ │
│  │  - LIMEBaseKeyboard          │  │  - Related phrase display              │ │
│  │  - LIMEKeyboardSwitcher      │  │  - CandidateViewHandler                │ │
│  └──────────────────────────────┘  └──────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              SearchServer                                    │
│          (Unified interface for all database search operations)              │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                                 LimeDB                                       │
│                    (SQL operations / parameterized queries)                  │
│  ┌────────────────────────────┐  ┌────────────────────────────────────────┐ │
│  │  LimeHanConverter          │  │  EmojiConverter                        │ │
│  │  (hanconvertv2.db)         │  │  (emoji.db)                            │ │
│  └────────────────────────────┘  └────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
                               SQLiteDatabase (main)


UI Components → SearchServer → LimeDB → SQLiteDatabase (main)
                              ├─> LimeHanConverter → SQLiteDatabase (hanconvertv2.db)
                              └─> EmojiConverter → SQLiteDatabase (emoji.db)

UI Components → DBServer → LimeDB → SQLiteDatabase (main)
```

---

## Test Architecture

### Test Source File Structure

The test architecture follows the same package structure as the main source code:

```
app/src/androidTest/java/net/toload/main/hd/
├── LimeDBTest.java                    ✅ EXISTS
├── DBServerTest.java                  ✅ EXISTS
├── LIMEServiceTest.java               ✅ EXISTS
├── ApplicationTest.java               ✅ EXISTS
├── SearchServerTest.java              ✅ EXISTS
├── VoiceInputActivityTest.java        ✅ EXISTS
├── ArchitectureComplianceTest.java    ❌ MISSING
├── IntegrationTest.java               ❌ MISSING
├── PerformanceTest.java               ❌ MISSING
└── ui/
    ├── SetupImFragmentTest.java       ✅ EXISTS
    ├── ManageImFragmentTest.java      ❌ MISSING
    ├── ManageRelatedFragmentTest.java ❌ MISSING
    ├── ManageImKeyboardDialogTest.java ❌ MISSING
    ├── ImportDialogTest.java          ❌ MISSING
    ├── ShareDialogTest.java           ❌ MISSING
    ├── SetupImLoadDialogTest.java     ❌ MISSING
    └── [Other Dialog Tests]          ❌ MISSING
```


## Test Coverage Goals

### Code Coverage Targets

- **LIMEService**: 90%+ coverage
- **SearchServer**: 90%+ coverage
- **LimeDB**: 90%+ coverage
- **DBServer**: 85%+ coverage
- **UI Components**: 70%+ coverage (critical paths)

### Test Type Distribution

- **Unit Tests**: 60%
- **Integration Tests**: 25%
- **Architecture Compliance Tests**: 10%
- **Performance Tests**: 5%

---

## Test Execution Strategy

### Continuous Integration

- Run unit tests on every commit
- Run integration tests on pull requests
- Run architecture compliance tests on pull requests
- Run performance tests nightly

### Test Execution Order

1. **Fast Tests First** (Unit tests)
2. **Integration Tests** (Medium speed)
3. **Architecture Compliance Tests** (Static analysis)
4. **Performance Tests** (Slow, run separately)

### Test Data Management

- Use in-memory databases for unit tests
- Use test fixtures for integration tests
- Clean up test data after each test
- Use test-specific database files

---

## Success Criteria

### Architecture Compliance

- ✅ Zero direct `LimeDB` access from UI components
- ✅ Zero SQL operations outside `LimeDB.java` (except converters)
- ✅ Zero file operations outside `DBServer.java` (except utilities)
- ✅ All UI components use `SearchServer` for database operations
- ✅ All UI components use `DBServer` for file operations

### Functionality

- ✅ All existing functionality works
- ✅ No regression in behavior
- ✅ Error handling is correct
- ✅ Edge cases are handled

### Performance

- ✅ No more than 5% performance regression
- ✅ Memory usage is stable (no leaks)
- ✅ Cache hit rates maintained or improved

### Code Quality

- ✅ All methods use parameterized queries
- ✅ Table names are validated
- ✅ Resources are properly managed
- ✅ Error messages are clear

---

## Test Tools and Frameworks

### Testing Frameworks

- **JUnit 4**: Unit and integration tests
- **AndroidJUnit4**: Android-specific tests
- **Mockito**: Mocking dependencies (if needed)
- **Espresso**: UI component tests (if needed)

### Static Analysis Tools

- **Android Lint**: Code analysis
- **Custom Scripts**: Architecture violation detection
- **Reflection**: Runtime architecture verification

### Performance Testing

- **Android Profiler**: Memory and CPU profiling
- **Custom Benchmarks**: Performance measurement
- **Before/After Comparison**: Regression detection

---

## Test Maintenance

### Test Updates

- Update tests when architecture changes
- Update tests when new features are added
- Remove obsolete tests
- Refactor tests for maintainability

### Test Documentation

- Document test purpose and scope
- Document test data requirements
- Document known limitations
- Document test execution instructions

---

## Risk Mitigation

### Test Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Incomplete test coverage | High | Set coverage targets, use coverage tools |
| False positives in architecture tests | Medium | Manual review of violations |
| Performance test flakiness | Medium | Run multiple times, use averages |
| Test maintenance burden | Medium | Keep tests simple, well-documented |

---

## Conclusion

This comprehensive test plan ensures that:

1. **Architecture is validated**: All boundaries are respected
2. **Functionality is preserved**: No regressions introduced
3. **Quality is maintained**: Code follows best practices
4. **Performance is acceptable**: No significant degradation

The phased approach allows for incremental testing and validation, ensuring the refactored architecture is robust and maintainable.

---

## Appendix: Test Checklist

### Quick Reference Checklist

- [x] All LimeDB unit tests pass (including export/import tests for related tables)
- [x] All DBServer unit tests pass (including import/export pair tests with data consistency)
- [x] All SearchServer unit tests pass (comprehensive coverage implemented)
- [ ] All LIMEService IME logic tests verified (Phase 5 tests exist but need verification - see Phase 5)
- [ ] All integration tests pass (including IME integration with real-world IM data - see Section 6.7)
- [ ] Architecture compliance tests pass (no violations)
- [ ] All UI component tests pass
- [ ] All regression tests pass
- [ ] Performance benchmarks meet targets
- [ ] Code coverage meets targets
- [x] Documentation is updated (TEST_PLAN_ARCHITECTURE.md updated)

---

**Document Version**: 1.2  
**Last Updated**: 2025-12-29  
**Status**: In Progress - SearchServer tests completed, LimeDB export/import tests completed; Phase 5 IME Logic Tests planned (tests exist in LIMEServiceTest.java, need verification); IME integration tests planned (Section 6.7)

