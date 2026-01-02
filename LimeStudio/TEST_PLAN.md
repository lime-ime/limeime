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

- **1.1 Core Database Operations** (9 tests)
  - [x] Test: `countRecords()` with various WHERE clauses ✅ `testLimeDBCountRecordsWithNullWhereClause()`, `testLimeDBCountRecordsWithWhereClause()`, `testLimeDBCountRecordsWithMultipleConditions()`, `testLimeDBCountRecordsWithInvalidTableName()`, `testLimeDBCountRecordsWithEmptyTable()`
  - [x] Test: `addRecord()` with ContentValues ✅ `testLimeDBAddRecordDeleteRecordUpdateRecordBranches()`, `testLimeDBAddRecordWithInvalidInputs()`
  - [x] Test: `updateRecord()` with ContentValues ✅ `testLimeDBAddRecordDeleteRecordUpdateRecordBranches()`, `testLimeDBUpdateRecordWithMultipleRecords()`, `testLimeDBUpdateRecordWithInvalidInputs()`, `testLimeDBUpdateRecordWithNoMatchingRecords()`
  - [x] Test: `deleteRecord()` with parameterized queries ✅ `testLimeDBAddRecordDeleteRecordUpdateRecordBranches()`, `testLimeDBDeleteRecordWithMultipleRecords()`, `testLimeDBDeleteRecordWithInvalidInputs()`, `testLimeDBDeleteRecordWithNoMatchingRecords()`

- **1.2 Unified Methods (Wrappers)** (8 tests)
  - [x] Test: `countMapping()` delegates to `countRecords()` ✅ `testLimeDBCountMappingDelegatesToCountRecords()`
  - [x] Test: `getRecordSize()` delegates to `countRecords()` ✅ `testLimeDBGetRecordSizeDelegatesToCountRecords()`
  - [x] Test: `getRelatedSize()` delegates to `countRecords()` ✅ `testLimeDBGetRelatedSizeDelegatesToCountRecords()`, `testLimeDBGetRelatedSizeEdgeCases()`
  - [x] Test: `getRelated()` method ✅ `testLimeDBGetRelated()`
  - [x] Test: `getAllRelated()` method ✅ `testLimeDBGetAllRelated()`

- **1.3 Backup/Import Operations** (15 tests)
  - [x] Test: `prepareBackup()` unified method ✅ `testLimeDBPrepareBackupWithSingleTable()`, `testLimeDBPrepareBackupWithMultipleTables()`, `testLimeDBPrepareBackupWithIncludeRelated()`, `testLimeDBPrepareBackupWithInvalidTableName()`
  - [x] Test: `importDb()` unified method ✅ `testLimeDBImportDBWithSingleTable()`, `testLimeDBImportDbWithMultipleTables()`, `testLimeDBImportBackupWithOverwriteExisting()`, `testLimeDBImportDbWithOverwriteExistingFalse()`, `testLimeDBImportDbWithIncludeRelated()`, `testLimeDBImportBackupWithInvalidFile()`
  - [x] Test: `exportTxtTable()` with related table ✅ `testLimeDBExportTxtTableWithRelatedTable()`
  - [x] Test: `exportTxtTable()` / `importTxtTable()` pair for related table ✅ `testLimeDBExportTxtTableRelatedAndImportTxtTableWithDataConsistency()`
  - [x] Test: Wrapper methods delegate correctly ✅ `testLimeDBPrepareBackupDbDelegatesToPrepareBackup()`, `testLimeDBPrepareBackupRelatedDbDelegatesToPrepareBackup()`, `testLimeDBImportDbRelatedDelegatesToImportDb()`, `testLimeDBImportBackupRelatedDbDelegatesToImportBackup()`, `testLimeDBWrapperMethodsDelegationComplete()`

- **1.4 Helper Methods** (10 tests)
  - [x] Test: `getBackupTableRecords()` ✅ `testLimeDBGetBackupTableRecordsWithValidBackupTable()`, `testLimeDBGetBackupTableRecordsWithInvalidFormat()`, `testLimeDBGetBackupTableRecordsWithInvalidBaseTableName()`
  - [x] Test: `buildWhereClause()` helper ✅ `testLimeDBBuildWhereClauseWithEmptyMap()`, `testLimeDBBuildWhereClauseWithSingleCondition()`, `testLimeDBBuildWhereClauseWithMultipleConditions()`, `testLimeDBBuildWhereClauseWithNullMap()`
  - [x] Test: `queryWithPagination()` helper ✅ `testLimeDBQueryWithPaginationWithLimitAndOffset()`, `testLimeDBQueryWithPaginationWithNoLimit()`, `testLimeDBQueryWithPaginationWithInvalidTableName()`, `testLimeDBQueryWithPaginationWithWhereClause()`

- **1.5 Table Name Validation** (4 tests)
  - [x] Test: `isValidTableName()` ✅ `testLimeDBIsValidTableNameWithAllValidTables()`, `testLimeDBIsValidTableNameWithInvalidTables()`, `testLimeDBIsValidTableNameWithSQLInjectionAttempts()`

- **1.6 SQL Injection Prevention** (5 tests)
  - [x] Test: All methods use parameterized queries ✅ `testLimeDBSQLInjectionPreventionInCountRecords()`, `testLimeDBSQLInjectionPreventionInTableName()`, `testLimeDBSQLInjectionPreventionInAddRecord()`, `testLimeDBSQLInjectionPreventionInUpdateRecord()`, `testLimeDBSQLInjectionPreventionInDeleteRecord()`

---

### Phase 2: DBServer Layer Tests (See Section "Phase 2" for details)

**Objective**: Test all file operations are centralized in DBServer.java.

**Test File**: DBServerTest.java
**Test Coverage**: 35+ test methods

- **2.1 File Export Operations** (15 tests)
  - [x] Test: `exportZippedDb()` ✅ `testDBServerExportImDatabaseWithValidTableName()`, `testDBServerExportImDatabaseWithInvalidTableName()`, `testDBServerExportImDatabaseWithProgressCallback()`, `testDBServerExportZippedDbWithNullTableName()`, `testDBServerExportZippedDbWithNullTargetFile()`, `testDBServerExportZippedDbWithDataIntegrity()`, `testDBServerExportZippedDbWithExistingTargetFile()`, `testDBServerExportZippedDbAndImportWithDataConsistency()`
  - [x] Test: `exportZippedDbRelated()` ✅ `testDBServerExportRelatedDatabase()`, `testDBServerExportZippedDbRelatedAndImportWithDataConsistency()`
  - [x] Test: `exportZippedDbRelated()` / `importZippedDbRelated()` pair ✅ `testDBServerExportZippedDbRelatedAndImportWithDataConsistency()`

- **2.2 File Import Operations** (20+ tests)
  - [x] Test: `importTxtTable(String filename, ...)` ✅ `testDBServerImportTxtTableWithStringFilename()`, `testDBServerImportTxtTableWithInvalidTableName()`, `testDBServerImportTxtTableWithEmptyFile()`, `testDBServerImportTxtTableWithProgressListener()`, `testDBServerImportTxtTableDelegatesToLimeDB()`, `testDBServerExportTxtTableAndImportTxtTablePair()`, `testDBServerExportTxtTableRelatedAndImportTxtTablePair()`
  - [x] Test: `importTxtTable(File sourcefile, ...)` ✅ `testDBServerImportTxtTableWithFile()`, `testDBServerImportTxtTableWithNullFile()`, `testDBServerImportTxtTableWithNonExistentFile()`
  - [x] Test: `importDb(File sourcedb, String tableName)` ✅ `testDBServerImportDbWithUncompressedDatabase()`, `testDBServerImportDbWithNullSourceDb()`, `testDBServerImportDbWithNonExistentFile()`, `testDBServerImportBackupDbDelegation()`
  - [x] Test: `importDbRelated(File sourcedb)` ✅ `testDBServerImportDbRelatedWithUncompressedDatabase()`, `testDBServerImportBackupRelatedDbDelegation()`
  - [x] Test: `importZippedDb()` ✅ `testDBServerExportZippedDbAndImportWithDataConsistency()`
  - [x] Test: `importZippedDbRelated()` ✅ `testDBServerExportZippedDbRelatedAndImportWithDataConsistency()`

- **2.3 Backup/Restore Operations** (8 tests)
  - [x] Test: `backupDatabase()` ✅ `testDBServerBackupDatabaseWithUri()`, `testDBServerBackupDatabaseWithNullUri()`, `testDBServerBackupDatabaseWithDataIntegrity()`, `testDBServerBackupDatabaseAndRestoreWithDataConsistency()`
  - [x] Test: `restoreDatabase()` ✅ `testDBServerRestoreDatabaseWithUri()`, `testDBServerRestoreDatabaseWithNullUri()`, `testDBServerRestoreDatabaseWithDataIntegrity()`, `testDBServerRestoreDatabaseWithStringPath()`, `testDBServerBackupDatabaseAndRestoreWithDataConsistency()`

- **2.4 Shared Preferences Operations** (4 tests)
  - [x] Test: `backupDefaultSharedPreference()` ✅ `testDBServerBackupDefaultSharedPreference()`, `testDBServerBackupDefaultSharedPreferenceWithNullFile()`, `testDBServerBackupDefaultSharedPreferenceAndRestorePair()`
  - [x] Test: `restoreDefaultSharedPreference()` ✅ `testDBServerRestoreDefaultSharedPreference()`, `testDBServerRestoreDefaultSharedPreferenceWithNonExistentFile()`, `testDBServerBackupDefaultSharedPreferenceAndRestorePair()`

- **2.5 User Records Backup/Restore** (5 tests)
  - [x] Test: User records backup/restore via LimeDB ✅ `testDBServerBackupUserRecordsViaLimeDB()`, `testDBServerBackupUserRecordsWithInvalidTableName()`, `testDBServerRestoreUserRecordsViaLimeDB()`, `testDBServerBackupUserRecordsAndRestoreUserRecordsPair()`, `testDBServerGetBackupTableRecords()`, `testDBServerCheckBackupTable()`

---

### Phase 3: SearchServer Layer Tests (See Section "Phase 3" for details)

**Objective**: Test SearchServer as single interface for all database operations.

**Test File**: SearchServerTest.java
**Test Coverage**: See below for explicit mapping of test plan items to test method names.

#### 3.1 UI-Compatible Methods
  - [x] Test: `getImConfigList()`
    - Verify that retrieving IM configurations with a null code returns all available input methods. ✅
      - `testSearchServerGetImConfigListWithNullCode`
    - Verify that retrieving IM configurations with a specific code returns only the matching input method. ✅
      - `testSearchServerGetImConfigListWithSpecificCode`
    - Verify that retrieving IM configurations with a type filter returns only input methods matching the specified type (e.g., name or keyboard). ✅
      - `testSearchServerGetImConfigListWithTypeFilter`
  - [x] Test: `getKeyboard()`
    - Verify that retrieving keyboards returns all available keyboard configurations. ✅
      - `testSearchServerGetKeyboard`
  - [x] Test: `getImConfig()` / `setImConfig()`
    - Verify that retrieving IM configuration info returns the correct field value for an existing input method. ✅
      - `testSearchServerGetImConfigListInfo`
    - Verify that retrieving IM configuration info for a non-existent input method returns an empty string or null. ✅
      - `testSearchServerGetImInfoWithNonExistentImList`
    - Verify that retrieving IM configuration info for a non-existent field returns an empty string or null. ✅
      - `testSearchServerGetImConfigListInfoWithNonExistentField`
    - Verify that setting IM configuration info correctly updates the field value. ✅
      - `testSearchServerSetImConfig`
    - Verify that updating an existing IM configuration field correctly changes its value. ✅
      - `testSearchServerSetImConfigUpdateExisting`
    - `testSearchServerGetImConfigListInfo`
    - `testSearchServerGetImInfoWithNonExistentImList`
    - `testSearchServerGetImConfigListInfoWithNonExistentField`
    - `testSearchServerSetImConfig`
    - `testSearchServerSetImConfigUpdateExisting`
  - [x] Test: `setIMKeyboard()`
    - Verify that setting IM keyboard with string parameters works as expected. ✅
      - `testSearchServerSetIMKeyboardWithStringParameters`
    - Verify that setting IM keyboard with a Keyboard object works as expected. ✅
      - `testSearchServerSetIMKeyboardWithKeyboardObject`
  - [x] Test: `isValidTableName()`
    - Verify that validating a table name returns true for valid names and false for invalid ones. ✅
      - `testSearchServerIsValidTableName`
    - Verify that validating a null table name returns false. ✅
      - `testSearchServerIsValidTableNameWithNull`
  - [x] Additional methods
    - Verify that removing IM info deletes the correct configuration. ✅
      - `testSearchServerRemoveImInfo`
    - Verify that resetting IM configuration restores default settings. ✅
      - `testSearchServerResetImConfig`
    - Verify that restoring IM configuration to default works as expected. ✅
      - `testSearchServerRestoredToDefault`
    - Verify that retrieving keyboard info returns the correct details. ✅
      - `testSearchServerGetKeyboardInfo`
    - Verify that retrieving keyboard configuration returns the correct settings. ✅
      - `testSearchServerGetKeyboardConfig`
    - Verify that retrieving IM list and keyboard configuration by code returns the correct data. ✅
      - `testSearchServerGetImListKeyboardConfigKeyboardListWithCode`
    - Verify that retrieving the table name returns the expected value. ✅
      - `testSearchServerGetTablename`
    - Verify that setting the table name updates it correctly. ✅
      - `testSearchServerSetTableName`
    - Verify that converting a key to a key name returns the correct result. ✅
      - `testSearchServerKeyToKeyname`
    - Verify that initializing the cache sets up the correct initial state. ✅
      - `testSearchServerInitialCache`
    - Verify that post-finish input processing works as expected. ✅
      - `testSearchServerPostFinishInput`
    - Verify that retrieving the keyboard configuration list returns all configurations. ✅
      - `testSearchServerGetKeyboardConfigList`
    - Verify that retrieving the IM list and keyboard configuration list returns all relevant data. ✅
      - `testSearchServerGetImListKeyboardConfigKeyboardList`

#### 3.2 Search Operations
  - [x] Test: `getMappingByCode()`
    - Verify that searching for a mapping by code returns the correct mapping. ✅
      - `testSearchServerGetMappingByCode`
  - [x] Test: `getRecords()`
    - Verify that retrieving records returns the correct set of records for a table. ✅
      - `testSearchServerGetRecords`
    - Verify that querying records with a specific query returns only matching records. ✅
      - `testSearchServerGetRecordsWithQuery`
    - Verify that paginated record retrieval returns the correct subset of records. ✅
      - `testSearchServerGetRecordsWithPagination`
  - [x] Test: `getRelated()`
    - Verify that retrieving related words returns the correct related entries. ✅
      - `testSearchServerGetRelated`
    - Verify that retrieving related words with pagination returns the correct subset. ✅
      - `testSearchServerGetRelatedByWordWithPagination`
  - [x] Test: `countRecordsRelated()`
    - Verify that counting related records returns the correct count. ✅
      - `testSearchServerCountRecordsRelated`

#### 3.3 Record Management
  - [x] Test: `addRecord()`
    - Verify that adding a record inserts the correct data into the table. ✅
      - `testSearchServerAddRecord`
  - [x] Test: `updateRecord()`
    - Verify that updating a record modifies the correct data in the table. ✅
      - `testSearchServerUpdateRecord`
  - [x] Test: `deleteRecord()`
    - Verify that deleting a record removes the correct data from the table. ✅
      - `testSearchServerDeleteRecord`
  - [x] Test: `clearTable()`
    - Verify that clearing a table removes all records (covered by setup/teardown in other tests). ✅
  - [x] Additional methods
    - Verify that retrieving a record by ID returns the correct record. ✅
      - `testSearchServerGetRecord`
    - Verify that adding or updating a mapping record works as expected. ✅
      - `testSearchServerAddOrUpdateMappingRecord`
    - Verify that counting records by word or code returns the correct count. ✅
      - `testSearchServerCountRecordsByWordOrCode`

#### 3.4 Backup/Restore Operations
  - [x] Test: `backupUserRecords()`
    - Verify that backing up user records and restoring maintains data consistency. ✅
      - `testSearchServerBackupUserRecordsAndRestoreWithDataConsistency`
    - Verify that backing up user records saves the correct data. ✅
      - `testSearchServerBackupUserRecords`
  - [x] Test: `restoreUserRecords()`
    - Verify that restoring user records loads the correct data. ✅
      - `testSearchServerRestoreUserRecords`
  - [x] Test: `checkBackuptable()`
    - Verify that checking the backup table confirms its integrity. ✅
      - `testSearchServerCheckBackupTable`
  - [x] Test: `getBackupTableRecords()`
    - Verify that retrieving records from the backup table returns the correct data. ✅
      - `testSearchServerGetBackupTableRecords`

#### 3.5 Converter Integration
  - [x] Test: `hanConvert()`
    - Verify that Han character conversion works for standard and various inputs. ✅
      - `testSearchServerHanConvert`
    - Verify that Han character conversion works for a variety of input cases. ✅
      - `testSearchServerHanConvertWithVariousInputs`
  - [x] Test: `emojiConvert()`
    - Verify that emoji conversion works for standard and various inputs. ✅
      - `testSearchServerEmojiConvert`
    - Verify that emoji conversion works for a variety of input cases. ✅
      - `testSearchServerEmojiConvertWithVariousInputs`

#### 3.6 Cache Management
  - [x] Test: `resetCache()`
    - Verify that resetting the cache clears all cached data. ✅
      - `testSearchServerResetCache`
    - Verify that cache invalidation works as expected. ✅
      - `testSearchServerResetCacheInvalidation`
  - [x] Test: `initialCache()`
    - Verify that initializing the cache sets up the correct initial state. ✅
      - `testSearchServerInitialCache`

#### 3.7 Export Operations
  - [x] Test: `exportTxtTable()`
    - Verify that exporting a table to TXT format works correctly. ✅
      - `testSearchServerExportTxtTable`
    - Verify that exporting a table with related data to TXT format works correctly. ✅
      - `testSearchServerExportTxtTableWithRelatedTable`

#### 3.8 Delegation Tests
  - [x] Delegation to LimeDB
    - Verify that delegation to LimeDB is covered by all above method-specific tests. ✅

#### 3.9 Null Handling Tests
  - [x] Null dbadapter handling
    - Verify that retrieving IM configurations with a null dbadapter returns expected results. ✅
      - `testSearchServerGetImConfigListWithNullDbadapter`
    - Verify that retrieving keyboards with a null dbadapter returns expected results. ✅
      - `testSearchServerGetKeyboardWithNullDbadapter`
    - Verify that retrieving IM configuration info with a null dbadapter returns expected results. ✅
      - `testSearchServerGetImConfigListInfoWithNullDbadapter`
    - Verify that validating table names with a null dbadapter returns expected results. ✅
      - `testSearchServerIsValidTableNameWithNullDbadapter`

#### 3.10 Runtime Suggestion and Caching Tests
  - [x] `makeRunTimeSuggestion()`
    - Verify that creating a runtime suggestion with valid input produces correct suggestions. ✅
      - `testSearchServerRuntimeSuggestionCreationWithValidInput`
    - Verify that runtime suggestions work with multi-character input. ✅
      - `testSearchServerRuntimeSuggestionWithMultiCharacterInput`
    - Verify that runtime suggestions handle backspace behavior correctly. ✅
      - `testSearchServerRuntimeSuggestionBackspaceBehavior`
  - [x] `clearRunTimeSuggestion()`
    - Verify that clearing runtime suggestions on new composition resets the suggestion state. ✅
      - `testSearchServerClearRuntimeSuggestionOnNewComposition`
    - Verify that clearing runtime suggestions with abandon flag works as expected. ✅
      - `testSearchServerClearRuntimeSuggestionWithAbandon`
  - [x] `prefetchCache()`
    - Verify that prefetching cache with number mapping works as expected. ✅
      - `testSearchServerPrefetchCacheWithNumberMapping`
    - Verify that prefetching cache with symbol mapping works as expected. ✅
      - `testSearchServerPrefetchCacheWithSymbolMapping`
    - Verify that prefetching cache with both mappings works as expected. ✅
      - `testSearchServerPrefetchCacheWithBothMappings`
  - [x] `updateScoreCache()`
    - Verify that updating the score cache via learning updates scores correctly. ✅
      - `testSearchServerUpdateScoreCacheViaLearning`
    - Verify that multiple learning updates to the score cache are handled correctly. ✅
      - `testSearchServerUpdateScoreCacheMultipleLearning`
  - [x] `removeRemappedCodeCachedMappings()`
    - Verify that removing remapped code cache invalidates the cache as expected. ✅
      - `testSearchServerRemoveRemappedCodeCacheInvalidation`

#### 3.11 Learning Algorithm Tests
  - [x] `learnRelatedPhraseAndUpdateScore()`
    - Verify that learning a related phrase with a valid mapping updates scores correctly. ✅
      - `testSearchServerLearnRelatedPhraseWithValidMapping`
    - Verify that learning a related phrase with a null mapping is handled gracefully. ✅
      - `testSearchServerLearnRelatedPhraseWithNullMapping`
    - Verify that learning related phrases is thread-safe. ✅
      - `testSearchServerLearnRelatedPhraseThreadSafety`
    - Verify that learning related phrase score accumulation works as expected. ✅
      - `testSearchServerLearnRelatedPhraseScoreAccumulation`
  - [x] `learnRelatedPhrase()`
    - Verify that learning a related phrase with two words works as expected. ✅
      - `testSearchServerLearnRelatedPhraseTwoWords`
    - Verify that learning a related phrase with multiple words works as expected. ✅
      - `testSearchServerLearnRelatedPhraseMultipleWords`
    - Verify that learning a related phrase with preference disabled is handled correctly. ✅
      - `testSearchServerLearnRelatedPhrasePreferenceDisabled`
    - Verify that learning a related phrase with null mappings is handled gracefully. ✅
      - `testSearchServerLearnRelatedPhraseNullMappings`
    - Verify that learning a related phrase with punctuation is handled correctly. ✅
      - `testSearchServerLearnRelatedPhrasePunctuation`
  - [x] `learnLDPhrase()`
    - Verify that learning an LD phrase with two characters works as expected. ✅
      - `testSearchServerLearnLDPhraseTwoCharacter`
    - Verify that learning an LD phrase with three characters works as expected. ✅
      - `testSearchServerLearnLDPhraseThreeCharacter`
    - Verify that learning an LD phrase with a four-character limit is handled correctly. ✅
      - `testSearchServerLearnLDPhraseFourCharacterLimit`
    - Verify that learning an LD phrase skips English as expected. ✅
      - `testSearchServerLearnLDPhraseSkipEnglish`
    - Verify that learning an LD phrase with reverse lookup works as expected. ✅
      - `testSearchServerLearnLDPhraseReverseLookup`
    - Verify that learning an LD phrase with failed lookup is handled gracefully. ✅
      - `testSearchServerLearnLDPhraseFailedLookup`

#### 3.12 English Prediction Tests
  - [x] `getEnglishSuggestions()`
    - Verify that English suggestions for a valid word are generated correctly. ✅
      - `testSearchServerEnglishSuggestionsValidWord`
    - Verify that English suggestions for a single character are generated correctly. ✅
      - `testSearchServerEnglishSuggestionsSingleChar`
    - Verify that English suggestions are retrieved from cache when available. ✅
      - `testSearchServerEnglishSuggestionsCacheHit`
    - Verify that no-match optimization for English suggestions works as expected. ✅
      - `testSearchServerEnglishSuggestionsNoMatchesOptimization`
    - Verify that English suggestions for an empty string are handled gracefully. ✅
      - `testSearchServerEnglishSuggestionsEmptyString`
  - [x] English dictionary integration
    - Verify that English dictionary integration in query works as expected. ✅
      - `testSearchServerEnglishDictionaryIntegrationInQuery`
    - Verify that clearing the English dictionary cache works as expected. ✅
      - `testSearchServerEnglishDictionaryCacheClearing`
    - Verify that consecutive prefix queries in the English dictionary are handled correctly. ✅
      - `testSearchServerEnglishDictionaryConsecutivePrefixes`

#### 3.13 Runtime Suggestion Class Tests
  - [x] `runTimeSuggestion.addExactMatch()`
    - Verify that adding an exact match to runtime suggestions works as expected. ✅
      - `testSearchServerRunTimeSuggestionAddExactMatchValid`
    - Verify that adding multiple exact matches to runtime suggestions works as expected. ✅
      - `testSearchServerRunTimeSuggestionAddExactMatchMultiple`
  - [x] `runTimeSuggestion.checkRemainingCode()`
    - Verify that checking remaining code in runtime suggestions works as expected. ✅
      - `testSearchServerRunTimeSuggestionCheckRemainingCodeValid`
    - Verify that phrase building in runtime suggestions is handled correctly. ✅
      - `testSearchServerRunTimeSuggestionCheckRemainingCodePhraseBuilding`
    - Verify that related verification in runtime suggestions works as expected. ✅
      - `testSearchServerRunTimeSuggestionCheckRemainingCodeRelatedVerification`
  - [x] `runTimeSuggestion.getBestSuggestion()`
    - Verify that getting the best suggestion from runtime suggestions works as expected. ✅
      - `testSearchServerRunTimeSuggestionGetBestSuggestion`
    - Verify that getting the best suggestion when none exist is handled gracefully. ✅
      - `testSearchServerRunTimeSuggestionGetBestSuggestionEmpty`
  - [x] `runTimeSuggestion.clear()`
    - Verify that clearing runtime suggestions is covered by the above tests. ✅
  - [x] Test: `runTimeSuggestion.clear()` suggestion clearing (2 tests)
    - Verify that clearing runtime suggestions on new composition works as expected. ✅
      - `testSearchServerClearRuntimeSuggestionOnNewComposition`
    - Verify that clearing runtime suggestions with abandon flag works as expected. ✅
      - `testSearchServerClearRuntimeSuggestionWithAbandon`

- **3.14 Advanced Runtime Suggestion Coverage (90% Goal)** (25 tests) **NEW - FOR 90% COVERAGE**
  - [x] Test: `makeRunTimeSuggestion()` comprehensive coverage (3 tests)
    - Verify runtime suggestion creation with valid input. ✅
      - `testSearchServerRuntimeSuggestionCreationWithValidInput`
    - Verify runtime suggestion building with multi-character input. ✅
      - `testSearchServerRuntimeSuggestionWithMultiCharacterInput`
    - Verify runtime suggestion cleanup on backspace. ✅
      - `testSearchServerRuntimeSuggestionBackspaceBehavior`
  - [x] Test: `clearRunTimeSuggestion()` state management (2 tests)
    - Verify suggestion clearing on new composition. ✅
      - `testSearchServerClearRuntimeSuggestionOnNewComposition`
    - Verify suggestion abandonment scenario. ✅
      - `testSearchServerClearRuntimeSuggestionWithAbandon`
  - [x] Test: `getRealCodeLength()` code length calculation (5 tests)
    - Verify basic code length calculation. ✅
      - `test_3_14_11_GetRealCodeLengthBasic`
    - Verify tone marker detection and stripping for Phonetic IM. ✅
      - `test_3_14_12_GetRealCodeLengthToneCodes`
    - Verify dual-mapped code handling. ✅
      - `test_3_14_13_GetRealCodeLengthDualMapped`
    - Verify null mapping handling. ✅
      - `test_3_14_14_GetRealCodeLengthNullMapping`
    - Verify edge cases (empty code, special characters). ✅
      - `test_3_14_15_GetRealCodeLengthEdgeCases`
  - [x] Test: `getRealCodeLength()` code length calculation (5 tests)
    - [x] Test with basic mapping - simple code length ✅
      - `test_3_14_11_GetRealCodeLengthBasic`
    - [x] Test with tone codes - tone marker detection and stripping ✅
      - `test_3_14_12_GetRealCodeLengthToneCodes`
    - [x] Test with dual-mapped codes - both code1/code2 handling ✅
      - `test_3_14_13_GetRealCodeLengthDualMapped`
    - [x] Test with null mapping - defensive null handling ✅
      - `test_3_14_14_GetRealCodeLengthNullMapping`
    - [x] Test with edge cases - empty code, special characters ✅
      - `test_3_14_15_GetRealCodeLengthEdgeCases`
  - [x] Test: `lcs()` Longest Common Subsequence (5 tests)
    - [x] Test with identical strings - 100% match ✅
      - `test_3_14_16_LcsIdenticalStrings`
    - [x] Test with partial overlap - substring matching ✅
      - `test_3_14_17_LcsPartialOverlap`
    - [x] Test with no overlap - zero length result ✅
      - `test_3_14_18_LcsNoOverlap`
    - [x] Test with empty strings - boundary conditions ✅
      - `test_3_14_19_LcsEmptyStrings`
    - [x] Test recursive depth - performance with long strings ✅
      - `test_3_14_20_LcsRecursiveDepth`
  - [x] Test: `getCodeListStringFromWord()` reverse lookup (3 tests)
    - [x] Test with valid word - code list generation ✅
      - `test_3_14_21_GetCodeListStringFromWordValid`
    - [x] Test with multi-character word - multi-code handling ✅
      - `test_3_14_22_GetCodeListStringFromWordMultiChar`
    - [x] Test with word not in database - empty result handling ✅
      - `test_3_14_23_GetCodeListStringFromWordNotFound`
  - [x] Test: `clearRunTimeSuggestion()` state management (2 tests)
    - [x] Test with clearBestSuggestion=true - full state reset ✅
      - `test_3_14_24_ClearRunTimeSuggestionFullReset`
    - [x] Test with clearBestSuggestion=false - partial reset ✅
      - `test_3_14_25_ClearRunTimeSuggestionPartialReset`

- **3.15 Advanced Search Coverage (90% Goal)** (15 tests) **NEW - FOR 90% COVERAGE**
  - [ ] Test: `getMappingByCode()` branch coverage improvements (10 tests)
    - [x] Test physical keyboard path - softKeyboard=false ✅
      - `testSearchServerGetMappingByCode` (with softKeyboard=false)
    - [x] Test virtual keyboard path - softKeyboard=true ✅
      - `testSearchServerGetMappingByCode` (with softKeyboard=true)
    - [x] Test with prefetchCache=true - cache warming ✅
      - `testSearchServerGetMappingByCode` (with prefetchCache=true)
    - [x] Test with prefetchCache=false - direct query ✅
      - `testSearchServerGetMappingByCode` (with prefetchCache=false)
    - [x] Test getAllRecords=true - full record set ✅
      - `testSearchServerGetMappingByCode` (with getAllRecords=true)
    - [x] Test getAllRecords=false - limited results ✅
      - `testSearchServerGetMappingByCode` (with getAllRecords=false)
    - [x] Test with remapped codes - code transformation ✅
      - `testSearchServerGetMappingByCode` (remapped codes)
    - [x] Test phrase suggestion abandonment - max depth cutoff ✅
      - `testSearchServerGetMappingByCode` (phrase suggestion cutoff)
    - [x] Test cache hit scenario - cached mapping return ✅
      - `testSearchServerGetMappingByCode` (cache hit)
    - [x] Test cache miss scenario - database query fallback ✅
      - `testSearchServerGetMappingByCode` (cache miss)
  - [ ] Test: `updateScoreCache()` comprehensive coverage (5 tests)
    - [x] Test score update with related phrases - list updates ✅
      - `testSearchServerUpdateScoreCacheViaLearning`
    - [x] Test score recalculation logic - score formula ✅
      - `testSearchServerUpdateScoreCacheMultipleLearning`
    - [x] Test cache invalidation on update - dirty flag handling ✅
      - `testSearchServerRemoveRemappedCodeCacheInvalidation`, `testSearchServerResetCacheInvalidation`
    - [x] Test concurrent updates - thread safety ✅
      - `testSearchServerLearnRelatedPhraseThreadSafety`
    - [x] Test with null mapping - defensive handling ✅
      - `testSearchServerLearnRelatedPhraseWithNullMapping`

- **3.16 SearchServer Uncovered Branches** (30 tests) **NEW - FOR 90% COVERAGE**
  - [ ] Test: `getMappingByCode()` comprehensive branch coverage (15 tests)
    - [ ] Test all boolean parameter combinations (8 tests)
      - softKeyboard true/false × prefetchCache true/false × getAllRecords true/false
      - `test_3_16_1_GetMappingByCodeBooleanCombinations`
    - [ ] Test cache scenarios (3 tests)
      - Cache hit scenario
      - Cache miss with database fallback
      - Prefetch cache population
      - `test_3_16_2_GetMappingByCodeCacheHit`
      - `test_3_16_3_GetMappingByCodeCacheMiss`
      - `test_3_16_4_GetMappingByCodePrefetchCache`
    - [ ] Test code remapping paths (2 tests)
      - Remapped code transformation
      - Original code preservation
      - `test_3_16_5_GetMappingByCodeRemapping`
    - [ ] Test phrase depth limits (1 test)
      - Max phrase suggestion abandonment
      - `test_3_16_6_GetMappingByCodeDepthLimit`
    - [ ] Test English prediction integration (1 test)
      - English prediction trigger in search
      - `test_3_16_7_GetMappingByCodeEnglishPrediction`
  - [ ] Test: `updateScoreCache()` and learning (10 tests)
    - [ ] Score recalculation with related phrases (3 tests)
      - Single related phrase score update
      - Multiple related phrases score calculation
      - Score formula verification
      - `test_3_16_8_UpdateScoreCacheSinglePhrase`
      - `test_3_16_9_UpdateScoreCacheMultiplePhrases`
      - `test_3_16_10_UpdateScoreCacheFormula`
    - [ ] Cache invalidation triggers (3 tests)
      - Invalidation after score update
      - Invalidation after mapping removal
      - Invalidation after cache reset
      - `test_3_16_11_UpdateScoreCacheInvalidation`
      - `test_3_16_12_RemoveMappingCacheInvalidation`
      - `test_3_16_13_ResetCacheInvalidation`
    - [ ] Concurrent updates (2 tests)
      - Thread-safe score updates
      - Concurrent cache access
      - `test_3_16_14_UpdateScoreCacheThreadSafety`
      - `test_3_16_15_UpdateScoreCacheConcurrentAccess`
    - [ ] Edge cases (2 tests)
      - Null mapping handling
      - Empty score list handling
      - `test_3_16_16_UpdateScoreCacheNullMapping`
      - `test_3_16_17_UpdateScoreCacheEmptyList`
  - [ ] Test: Additional method coverage (5 tests)
    - [ ] `clearTable()` error handling (2 tests)
      - Clear non-existent table
      - Clear with backup option
      - `test_3_16_18_ClearTableNonExistent`
      - `test_3_16_19_ClearTableWithBackup`
    - [ ] `getSelkey()` edge cases (2 tests)
      - Different keyboard configurations
      - Null keyboard handling
      - `test_3_16_20_GetSelkeyDifferentKeyboards`
      - `test_3_16_21_GetSelkeyNullKeyboard`
    - [ ] `learnLDPhrase()` remaining branches (1 test)
      - Edge case branch coverage
      - `test_3_16_22_LearnLDPhraseRemainingBranches`

- **3.17 SearchServer Runtime Suggestion Engine** (25 tests: 10 unit + 15 regression) **NEW - FOR 90% COVERAGE**

  **SearchServerTest.java** (10 unit tests):
  - [ ] Test: `makeRunTimeSuggestion()` basic coverage (5 tests)
    - [ ] Empty mapping list handling
      - `test_3_17_1_MakeRunTimeSuggestionEmptyList`
    - [ ] Stack depth limit enforcement
      - `test_3_17_2_MakeRunTimeSuggestionStackDepth`
    - [ ] Null/edge case handling
      - `test_3_17_3_MakeRunTimeSuggestionNullCases`
    - [ ] Algorithm correctness with controlled data
      - `test_3_17_4_MakeRunTimeSuggestionAlgorithm`
    - [ ] doRunTimeSuggestion flag disabled
      - `test_3_17_5_MakeRunTimeSuggestionDisabled`
  - [ ] Test: `getRealCodeLength()` coverage (3 tests)
    - [ ] Null/empty code handling
      - `test_3_17_6_GetRealCodeLengthNull`
    - [ ] Basic code length calculation
      - `test_3_17_7_GetRealCodeLengthBasic`
    - [ ] Edge cases with special characters
      - `test_3_17_8_GetRealCodeLengthEdgeCases`
  - [ ] Test: State management (2 tests)
    - [ ] `clearRunTimeSuggestion()` unit tests
      - `test_3_17_9_ClearRunTimeSuggestionUnit`
    - [ ] `getCodeListStringFromWord()` with mock data
      - `test_3_17_10_GetCodeListStringFromWordMock`

  **RegressionTest.java** (15 integration tests with real IM data):
  - [ ] Test: Runtime suggestion with real PHONETIC IM (8 tests)
    - [ ] Single-word suggestions from PHONETIC table
      - Type "su3" and verify suggestions
      - `test_3_17_11_RuntimeSuggestionPhoneticSingleWord`
    - [ ] Multi-word phrase building (2-word)
      - Type "ni3hao3" and verify 2-word phrase suggestion
      - `test_3_17_12_RuntimeSuggestionPhoneticTwoWords`
    - [ ] Multi-word phrase building (3-word)
      - Type complete 3-word phrase codes
      - `test_3_17_13_RuntimeSuggestionPhoneticThreeWords`
    - [ ] Multi-word phrase building (4+ words)
      - Type long phrase codes
      - `test_3_17_14_RuntimeSuggestionPhoneticLongPhrase`
    - [ ] Tone code handling in real scenarios
      - Test with tone markers (3, 4, 6, 7)
      - `test_3_17_15_RuntimeSuggestionPhoneticToneCodes`
    - [ ] LCS matching with actual phrases
      - Verify longest common subsequence logic
      - `test_3_17_16_RuntimeSuggestionPhoneticLCS`
    - [ ] Backspace during suggestion building
      - Build suggestion then backspace
      - `test_3_17_17_RuntimeSuggestionPhoneticBackspace`
    - [ ] Suggestion abandonment workflow
      - Start suggestion then query different code
      - `test_3_17_18_RuntimeSuggestionPhoneticAbandon`
  - [ ] Test: Runtime suggestion with real DAYI IM (4 tests)
    - [ ] Array-based IM suggestion flow
      - Type DAYI codes and verify suggestions
      - `test_3_17_19_RuntimeSuggestionDayiFlow`
    - [ ] Different code structures vs Phonetic
      - Compare DAYI vs Phonetic suggestion behavior
      - `test_3_17_20_RuntimeSuggestionDayiStructure`
    - [ ] Real phrase combinations in DAYI
      - Multi-character phrase building
      - `test_3_17_21_RuntimeSuggestionDayiPhrases`
    - [ ] DAYI-specific edge cases
      - Test DAYI special codes
      - `test_3_17_22_RuntimeSuggestionDayiEdgeCases`
  - [ ] Test: Cross-IM suggestion testing (3 tests)
    - [ ] Switch from Phonetic to DAYI during composition
      - Build suggestion in Phonetic, switch to DAYI
      - `test_3_17_23_RuntimeSuggestionCrossIMSwitch`
    - [ ] Suggestion persistence across IM switches
      - Verify suggestion state after IM change
      - `test_3_17_24_RuntimeSuggestionCrossIMPersistence`
    - [ ] Learning integration with runtime suggestions
      - Commit suggested phrase and verify learning
      - `test_3_17_25_RuntimeSuggestionCrossIMLearning`

**Phase 3 Total**: 50+ existing tests + 43 new (3.14-3.15) + 55 new (3.16-3.17) = **148 tests** (Target: 90% coverage)

ate to LimeDB ✅ Comprehensive delegation tests implemented in SearchServerTest.java

### Phase 4: UI Component Tests (See Section "Phase 4" for details)

**Objective**: Test UI components use SearchServer/DBServer, not LimeDB directly.

**Test Files**: Multiple test files (see below)
**Test Coverage**: 80+ test methods across all UI components

- **4.1 Architecture Compliance Tests** (5 tests)
  - [x] Test: No direct LimeDB in UI components ✅
    - `ManageImFragmentTest.testNoDirectLimeDBAccess`
    - `SetupImFragmentTest` (all tests)
  - [x] Test: Controller-driven architecture ✅
    - `SetupImControllerFlowsTest` (all tests)
    - `ManageImControllerTest` (all tests)

- **4.2 SetupImFragment** (15 tests)
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

- **4.9 VoiceInputActivity** (35 tests) ✅ **COMPLETED**
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

- **4.10 Additional UI Component Tests**
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
**Test Coverage**: 113 test methods planned across 18 subsections

- **5.1 LIMEService Lifecycle Tests** (12 tests)
  - [x] Test: Service initialization (4 tests) ✅
    - Mapping: `testLIMEServiceConstants`, `testLIMEServiceAvailability`
  - [x] Test: Input session lifecycle (4 tests) ✅
    - Mapping: `testLIMEServicePreferenceIntegration`, `testLIMEServiceSearchServerIntegration`
  - [x] Test: Configuration change handling (4 tests) ✅
    - Mapping: `testLIMEServiceConfigurationHandling`

- **5.2 Soft Keyboard / Keyboard View Tests** (20 tests)
  - [x] Test: Keyboard view creation (3 tests) ✅
    - Mapping: `testLIMEServiceKeyboardConstants`
  - [x] Test: Keyboard switching (4 tests) ✅
    - Mapping: `testLIMEServiceKeyboardConstants`
  - [x] Test: Keyboard key handling (4 tests) ✅
    - Mapping: `testLIMEServiceKeyEventHandling`
  - [x] Test: Keyboard layout variants (4 tests) ✅
    - Mapping: `testLIMEServiceKeyboardConstants`
  - [x] Test: Shift and meta key handling (3 tests) ✅
    - Mapping: `testLIMEServiceKeyboardConstants`

- **5.3 Candidate View / Candidate Window Tests** (12 tests)
  - [x] Test: Candidate view display (3 tests) ✅
    - Mapping: `testLIMEServiceCandidateViewHandler`
  - [x] Test: Candidate selection (4 tests) ✅
    - Mapping: `testLIMEServiceCandidateViewHandler`
  - [x] Test: Candidate list operations (4 tests) ✅
    - Mapping: `testLIMEServiceCandidateViewHandler`
  - [x] Test: Related phrase suggestions (3 tests) ✅
    - Mapping: `testLIMEServiceCandidateViewHandler`

- **5.4 Input Handling and Text Composition Tests** (16 tests)
  - [x] Test: Physical keyboard input (4 tests) ✅
    - Mapping: `testLIMEServiceKeyEventHandling`
  - [x] Test: Composing text management (5 tests) ✅
    - Mapping: `testLIMEServiceEditorInfoHandling`
  - [x] Test: Composing text edge cases (4 tests) ✅
    - Mapping: `testLIMEServiceEditorInfoHandling`
  - [x] Test: Text commit operations (4 tests) ✅
    - Mapping: `testLIMEServiceEditorInfoHandling`

- **5.5 English Prediction and Mixed Input Tests** (8 tests)
  - [x] Test: English prediction mode (5 tests) ✅
    - Mapping: `testLIMEServiceEditorInfoHandling`
  - [x] Test: Language mode switching (3 tests) ✅
    - Mapping: `testLIMEServiceEditorInfoHandling`

- **5.6 Chinese Han Conversion and Emoji Tests** (5 tests)
  - [x] Test: Han conversion (Traditional ↔ Simplified) (3 tests) ✅
    - Mapping: `testLIMEServiceEditorInfoHandling`
  - [x] Test: Emoji input (3 tests) ✅
    - Mapping: `testLIMEServiceEditorInfoHandling`

- **5.7 Audio/Haptic Feedback Tests** (6 tests)
  - [x] Test: Sound feedback (3 tests) ✅
    - Mapping: `testLIMEServiceAudioManagerCompatibility`
  - [x] Test: Vibration feedback (3 tests) ✅
    - Mapping: `testLIMEServiceVibratorCompatibility`

- **5.8 Swipe Gesture Tests** (4 tests)
  - [x] Test: Swipe gestures (4 tests) ✅
    - Mapping: `testLIMEServiceKeyEventHandling`

- **5.9 Voice Input Integration Tests** (5 tests)
  - [x] Test: Voice input launch (3 tests) ✅
    - Mapping: `testLIMEServiceEditorInfoHandling`
  - [x] Test: Voice input result handling (3 tests) ✅
    - Mapping: `testLIMEServiceEditorInfoHandling`

- **5.10 IM Picker and Options Menu Tests** (5 tests)
  - [x] Test: IM picker (3 tests) ✅
    - Mapping: `testLIMEServiceInputMethodManager`
  - [x] Test: Options menu (3 tests) ✅
    - Mapping: `testLIMEServiceInputMethodManager`

- **5.11 Fullscreen Mode Tests** (3 tests)
  - [x] Test: Fullscreen editing mode (3 tests) ✅
    - Mapping: `testLIMEServiceEditorInfoHandling`

- **5.12 Window Insets and Layout Tests** (3 tests)
  - [x] Test: Window insets handling (3 tests) ✅
    - Mapping: `testLIMEServiceWindowInsetsHandling`

- **5.13 Input Connection Integration Tests** (5 tests)
  - [x] Test: InputConnection operations (5 tests) ✅
    - Mapping: `testLIMEServiceEditorInfoHandling`

- **5.14 Mapping and Record Handling Tests** (3 tests)
  - [x] Test: Mapping data handling (3 tests) ✅
    - Mapping: `testLIMEServiceSearchServerIntegration`

- **5.15 Character Validation Tests** (4 tests)
  - [x] Test: Character type validation (4 tests) ✅
    - Mapping: `testLIMEServiceKeyEventHandling`

- **5.16 Preference Integration Tests** (4 tests)
  - [x] Test: Preference manager integration (4 tests) ✅
    - Mapping: `testLIMEServicePreferenceIntegration`

- **5.17 SearchServer Integration Tests (via LIMEService)** (3 tests)
  - [x] Test: SearchServer lookup from LIMEService (3 tests) ✅
    - Mapping: `testLIMEServiceSearchServerIntegration`

- **5.18 Error Handling and Edge Cases** (9 tests)
  - [x] Test: Null input handling (3 tests) ✅
    - Mapping: `testLIMEServiceEditorInfoHandling`
  - [x] Test: Empty string handling (3 tests) ✅
    - Mapping: `testLIMEServiceEditorInfoHandling`
  - [x] Test: Boundary conditions (3 tests) ✅
    - Mapping: `testLIMEServiceEditorInfoHandling`

- **5.27 LIMEService Key Event Handling** (25 tests: 15 unit + 10 regression) **NEW - FOR 90% COVERAGE**

  **LIMEServiceTest.java** (15 unit tests):
  - [ ] Test: Basic key event handling (8 tests)
    - [ ] `onKeyDown()` basic event processing
      - `test_5_27_1_OnKeyDownBasicEvents`
    - [ ] `onKeyUp()` basic event handling
      - `test_5_27_2_OnKeyUpBasicEvents`
    - [ ] `translateKeyDown()` key code translation
      - `test_5_27_3_TranslateKeyDownTranslation`
    - [ ] Meta state tracking without keyboard view
      - `test_5_27_4_MetaStateTracking`
    - [ ] Key event sequence handling
      - `test_5_27_5_KeyEventSequence`
    - [ ] Hardware keyboard detection
      - `test_5_27_6_HardwareKeyboardDetection`
    - [ ] Key repeat events
      - `test_5_27_7_KeyRepeatEvents`
    - [ ] Invalid KeyEvent handling
      - `test_5_27_8_InvalidKeyEvents`
  - [ ] Test: Mode-specific key handling (4 tests)
    - [ ] English mode key processing
      - `test_5_27_9_EnglishModeKeys`
    - [ ] Chinese mode key processing
      - `test_5_27_10_ChineseModeKeys`
    - [ ] Mode switching logic
      - `test_5_27_11_ModeSwitchingLogic`
    - [ ] Symbol mode key handling
      - `test_5_27_12_SymbolModeKeys`
  - [ ] Test: Edge cases (3 tests)
    - [ ] Null KeyEvent handling
      - `test_5_27_13_NullKeyEvent`
    - [ ] Invalid key codes
      - `test_5_27_14_InvalidKeyCodes`
    - [ ] Concurrent key events
      - `test_5_27_15_ConcurrentKeyEvents`

  **RegressionTest.java** (10 integration tests):
  - [ ] Test: Real keyboard input flow (6 tests)
    - [ ] Type real PHONETIC codes ("su3", "hao3")
      - `test_5_27_16_RegressionPhoneticTyping`
    - [ ] Type real DAYI codes
      - `test_5_27_17_RegressionDayiTyping`
    - [ ] Modifier keys with keyboard view active
      - `test_5_27_18_RegressionModifierKeys`
    - [ ] Special keys (enter, space, backspace) with candidates
      - `test_5_27_19_RegressionSpecialKeys`
    - [ ] Key sequence producing candidates
      - `test_5_27_20_RegressionKeySequenceCandidates`
    - [ ] Hardware keyboard with real lookup
      - `test_5_27_21_RegressionHardwareKeyboard`
  - [ ] Test: Keyboard view integration (4 tests)
    - [ ] Key highlighting and visual feedback
      - `test_5_27_22_RegressionKeyHighlighting`
    - [ ] Shift/caps lock state reflection on keyboard
      - `test_5_27_23_RegressionShiftCapsState`
    - [ ] Keyboard layout switching during typing
      - `test_5_27_24_RegressionLayoutSwitching`
    - [ ] Long press behavior
      - `test_5_27_25_RegressionLongPress`

- **5.28 LIMEService Text Commit and Character Input** (20 tests - RegressionTest) **NEW - FOR 90% COVERAGE**

  **RegressionTest.java** (20 integration tests with real IM data):
  - [ ] Test: commitTyped() with real learning (8 tests)
    - [ ] Commit single character from PHONETIC table
      - `test_5_28_1_CommitPhoneticSingleChar`
    - [ ] Commit multi-character phrase
      - `test_5_28_2_CommitMultiCharPhrase`
    - [ ] Learning phrase after commit
      - `test_5_28_3_CommitWithLearning`
    - [ ] Verify learned phrase score increases
      - `test_5_28_4_LearnedPhraseScoreIncrease`
    - [ ] Commit with related phrases
      - `test_5_28_5_CommitRelatedPhrases`
    - [ ] Commit and verify phrase appears in suggestions
      - `test_5_28_6_CommitPhraseSuggestionUpdate`
    - [ ] Commit empty composition
      - `test_5_28_7_CommitEmptyComposition`
    - [ ] Commit with punctuation
      - `test_5_28_8_CommitWithPunctuation`
  - [ ] Test: handleCharacter() with real candidates (7 tests)
    - [ ] Character input building composing text
      - `test_5_28_9_CharacterInputComposing`
    - [ ] Candidate list updates as typing progresses
      - `test_5_28_10_CandidateListUpdates`
    - [ ] Symbol handling from real symbol tables
      - `test_5_28_11_SymbolHandling`
    - [ ] Punctuation insertion
      - `test_5_28_12_PunctuationInsertion`
    - [ ] PHONETIC IM character processing
      - `test_5_28_13_PhoneticCharProcessing`
    - [ ] DAYI IM character processing
      - `test_5_28_14_DayiCharProcessing`
    - [ ] Special character handling
      - `test_5_28_15_SpecialCharacters`
  - [ ] Test: handleSelkey() with real candidates (5 tests)
    - [ ] Select candidate 1-9 from real lookup results
      - `test_5_28_16_SelectCandidateKeys`
    - [ ] Selection with phrase learning
      - `test_5_28_17_SelectionWithLearning`
    - [ ] Invalid selection handling
      - `test_5_28_18_InvalidSelection`
    - [ ] Empty candidate list selection
      - `test_5_28_19_EmptyCandidateSelection`
    - [ ] Selection key in different modes
      - `test_5_28_20_SelectionKeyModes`

- **5.29 LIMEService UI and Options Integration** (30 tests: 15 unit + 15 regression) **NEW - FOR 90% COVERAGE**

  **LIMEServiceTest.java** (15 unit tests):
  - [ ] Test: Options menu logic (6 tests)
    - [ ] `handleOptions()` menu creation
      - `test_5_29_1_HandleOptionsMenuCreation`
    - [ ] Menu item selection logic
      - `test_5_29_2_MenuItemSelection`
    - [ ] Settings launch intent
      - `test_5_29_3_SettingsLaunchIntent`
    - [ ] Han conversion option
      - `test_5_29_4_HanConversionOption`
    - [ ] IM picker option
      - `test_5_29_5_ImPickerOption`
    - [ ] Edge cases (null context)
      - `test_5_29_6_OptionsEdgeCases`
  - [ ] Test: Keyboard switching logic (5 tests)
    - [ ] `switchKeyboard()` state changes
      - `test_5_29_7_SwitchKeyboardState`
    - [ ] Layout ID validation
      - `test_5_29_8_LayoutIdValidation`
    - [ ] Keyboard initialization after switch
      - `test_5_29_9_KeyboardInitAfterSwitch`
    - [ ] Keyboard theme changes
      - `test_5_29_10_KeyboardThemeChange`
    - [ ] Invalid keyboard ID handling
      - `test_5_29_11_InvalidKeyboardId`
  - [ ] Test: Candidate systems logic (4 tests)
    - [ ] `updateCandidates()` logic
      - `test_5_29_12_UpdateCandidatesLogic`
    - [ ] `updateRelatedPhrase()` state management
      - `test_5_29_13_UpdateRelatedPhraseState`
    - [ ] `buildCompletionList()` logic
      - `test_5_29_14_BuildCompletionListLogic`
    - [ ] Candidate view visibility logic
      - `test_5_29_15_CandidateViewVisibility`

  **RegressionTest.java** (15 integration tests):
  - [ ] Test: Real IM switching workflow (8 tests)
    - [ ] Switch from PHONETIC to DAYI with active composition
      - `test_5_29_16_RegressionPhoneticToDayi`
    - [ ] Switch from DAYI to PHONETIC
      - `test_5_29_17_RegressionDayiToPhonetic`
    - [ ] IM picker dialog with real IM list
      - `test_5_29_18_RegressionImPickerDialog`
    - [ ] Han conversion picker (simplified/traditional)
      - `test_5_29_19_RegressionHanConversion`
    - [ ] Keyboard layout switch during typing
      - `test_5_29_20_RegressionLayoutSwitch`
    - [ ] Verify candidates update after IM switch
      - `test_5_29_21_RegressionCandidatesAfterSwitch`
    - [ ] Complete workflow: switch IM and continue typing
      - `test_5_29_22_RegressionSwitchAndContinue`
    - [ ] IM switch persistence across sessions
      - `test_5_29_23_RegressionSwitchPersistence`
  - [ ] Test: English prediction with real workflow (4 tests)
    - [ ] `updateEnglishPrediction()` during typing
      - `test_5_29_24_RegressionEnglishPrediction`
    - [ ] English word suggestions in real input
      - `test_5_29_25_RegressionEnglishSuggestions`
    - [ ] Switch between Chinese and English input
      - `test_5_29_26_RegressionChiEngSwitch`
    - [ ] English mode with QWERTY keyboard
      - `test_5_29_27_RegressionEnglishMode`
  - [ ] Test: Candidate selection in real session (3 tests)
    - [ ] `pickCandidateManually()` with real candidates
      - `test_5_29_28_RegressionManualPick`
    - [ ] Related phrase display after selection
      - `test_5_29_29_RegressionRelatedAfterPick`
    - [ ] Learning integration after manual pick
      - `test_5_29_30_RegressionLearningAfterPick`

- **5.30 LIMEService Input State and Lifecycle** (25 tests: 15 unit + 10 regression) **NEW - FOR 90% COVERAGE**

  **LIMEServiceTest.java** (15 unit tests):
  - [ ] Test: Input initialization (6 tests)
    - [ ] Text input type
      - `test_5_30_1_InputTypeText`
    - [ ] Password input type
      - `test_5_30_2_InputTypePassword`
    - [ ] Email input type
      - `test_5_30_3_InputTypeEmail`
    - [ ] URL input type
      - `test_5_30_4_InputTypeUrl`
    - [ ] `initOnStartInput()` state setup
      - `test_5_30_5_InitOnStartInput`
    - [ ] Input capabilities detection
      - `test_5_30_6_InputCapabilities`
  - [ ] Test: Input lifecycle (5 tests)
    - [ ] `onStartInputView()` initialization
      - `test_5_30_7_OnStartInputView`
    - [ ] `onFinishInput()` cleanup
      - `test_5_30_8_OnFinishInput`
    - [ ] `onUpdateSelection()` handling
      - `test_5_30_9_OnUpdateSelection`
    - [ ] Input session state transitions
      - `test_5_30_10_SessionStateTransitions`
    - [ ] Multiple input sessions
      - `test_5_30_11_MultipleInputSessions`
  - [ ] Test: Shift and caps lock (4 tests)
    - [ ] `handleShift()` logic
      - `test_5_30_12_HandleShiftLogic`
    - [ ] `toggleCapsLock()` state changes
      - `test_5_30_13_ToggleCapsLockState`
    - [ ] `updateShiftKeyState()` logic
      - `test_5_30_14_UpdateShiftKeyState`
    - [ ] Shift state persistence
      - `test_5_30_15_ShiftStatePersistence`

  **RegressionTest.java** (10 integration tests):
  - [ ] Test: Complete input sessions (6 tests)
    - [ ] Full session: start → type → select → commit → finish
      - `test_5_30_16_RegressionFullSession`
    - [ ] Multiple sessions with learning persistence
      - `test_5_30_17_RegressionMultipleSessions`
    - [ ] Input type switching (text → email → URL)
      - `test_5_30_18_RegressionInputTypeSwitch`
    - [ ] Keyboard initialization with PHONETIC table
      - `test_5_30_19_RegressionPhoneticInit`
    - [ ] Keyboard initialization with DAYI table
      - `test_5_30_20_RegressionDayiInit`
    - [ ] Session cleanup and resource release
      - `test_5_30_21_RegressionSessionCleanup`
  - [ ] Test: Shift/caps integration (4 tests)
    - [ ] Shift key with keyboard view visual feedback
      - `test_5_30_22_RegressionShiftVisual`
    - [ ] Caps lock during real typing session
      - `test_5_30_23_RegressionCapsLockTyping`
    - [ ] State persistence across input sessions
      - `test_5_30_24_RegressionStatePersistence`
    - [ ] Shift with different input types
      - `test_5_30_25_RegressionShiftInputTypes`

- **5.31 LIMEService Remaining Features** (20 tests: 10 unit + 10 regression) **NEW - FOR 90% COVERAGE**

  **LIMEServiceTest.java** (10 unit tests):
  - [ ] Test: Voice input logic (5 tests)
    - [ ] `startVoiceInput()` intent creation
      - `test_5_31_1_StartVoiceInputIntent`
    - [ ] `launchRecognizerIntent()` error handling
      - `test_5_31_2_LaunchRecognizerError`
    - [ ] IME switching detection
      - `test_5_31_3_ImeSwitchingDetection`
    - [ ] Voice input availability check
      - `test_5_31_4_VoiceInputAvailable`
    - [ ] Voice input cancel handling
      - `test_5_31_5_VoiceInputCancel`
  - [ ] Test: Backspace logic (3 tests)
    - [ ] `handleBackspace()` state changes
      - `test_5_31_6_HandleBackspaceState`
    - [ ] Composition cleanup logic
      - `test_5_31_7_CompositionCleanup`
    - [ ] Backspace with empty composition
      - `test_5_31_8_BackspaceEmpty`
  - [ ] Test: Miscellaneous (2 tests)
    - [ ] `onEvaluateFullscreenMode()` logic
      - `test_5_31_9_EvaluateFullscreenMode`
    - [ ] Vibration/sound settings
      - `test_5_31_10_VibrationSoundSettings`

  **RegressionTest.java** (10 integration tests):
  - [ ] Test: Voice input integration (4 tests)
    - [ ] Voice input flow with real IME switching
      - `test_5_31_11_RegressionVoiceInputFlow`
    - [ ] Return from voice input with text
      - `test_5_31_12_RegressionVoiceInputReturn`
    - [ ] IME monitoring and restoration
      - `test_5_31_13_RegressionImeMonitoring`
    - [ ] Voice input with active composition
      - `test_5_31_14_RegressionVoiceWithComposition`
  - [ ] Test: Backspace in real sessions (4 tests)
    - [ ] Backspace during composing with real candidates
      - `test_5_31_15_RegressionBackspaceComposing`
    - [ ] Backspace with phrase abandonment
      - `test_5_31_16_RegressionBackspaceAbandon`
    - [ ] Backspace in selection mode
      - `test_5_31_17_RegressionBackspaceSelection`
    - [ ] Multi-character backspace
      - `test_5_31_18_RegressionBackspaceMultiChar`
  - [ ] Test: Real environment features (2 tests)
    - [ ] Completion suggestions from input history
      - `test_5_31_19_RegressionCompletionHistory`
    - [ ] Vibration/sound during real typing
      - `test_5_31_20_RegressionVibrationTyping`

**Phase 5 Total**: 109 existing tests + 120 new (5.27-5.31) = **229 tests** (Target: 90% coverage)

---

### Phase 6: Integration Tests (See Section "Phase 6" for details)

**Objective**: Test interactions between layers with real implementations.

**Precondition**: Both LIME.IM_PHONETIC and LIME.IM_DAYI cloud data preloaded at test class startup.

**Test Coverage**: 60+ test methods

- **6.1 SearchServer → LimeDB Integration** (5 tests)
  - [x] Test: Complete search flow with REAL IM data (3 tests)
  - [x] Test: Configuration operations (3 tests)

- **6.2 DBServer → LimeDB Integration** (6 tests)
  - [x] Test: Export flow with REAL IM data (3 tests)
  - [x] Test: Import flow (4 tests)

- **6.3 UI → SearchServer Integration (Complete Flow)** (25 tests)
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

- **6.4 UI → DBServer → LimeDB Integration** (6 tests)
  - [x] Test: Complete file operation flow (6 tests)

- **6.5 Backup Path (User Records) — Before Overwrite** (4 tests)
  - [x] Test: Explicit backup on clear table (2 tests)
  - [x] Test: Backup during import (restore flag path) (2 tests)

- **6.6 Restore Path (User Records) — After Import** (5 tests)
  - [x] Test: Restore after import (3 tests)
  - [x] Test: No-restore path (1 test)
  - [x] Test: Error handling (2 tests)
  - [x] Test: UI refresh after restore (1 test)

---

### Phase 7: Architecture Compliance Tests (See Section "Phase 7" for details)

**Objective**: Verify architectural boundaries are respected.

**Test Coverage**: 10+ test methods

- **7.1 Static Analysis Tests** (3 tests)
  - [x] Test: No direct LimeDB access from UI
  - [x] Test: No SQL operations outside LimeDB
  - [x] Test: No file operations outside DBServer

- **7.2 Runtime Architecture Tests** (2 tests)
  - [x] Test: Component initialization
  - [x] Test: Method call tracing

---

### Phase 8: Regression Tests - Core IME End-to-End Workflows (See Section "Phase 8" for details)

**Objective**: Ensure core user-facing IME functionality still works after refactoring. These are the most critical tests for LIME IME's query and learning logic.

**Test Coverage**: 38+ test methods covering complete input → query → learning workflows

**Test File**: `IntegrationTestIMELogic.java`

**Note**: Edge case testing (null handling, empty data, invalid input) is already covered by Phase 1-2 unit tests in LimeDBTest and SearchServerTest. Phase 8 focuses on end-to-end user workflows with real IM data.

- **8.1 Soft Keyboard Input Integration** (2 tests) ✅ **COMPLETED**
  - [x] Soft keyboard input → query → candidates - `test_8_1_SoftKeyboardInputWithRealData` ✅
  - [x] Incremental composing text - `test_8_1_IncrementalComposingText` ✅

- **8.2 Hard Keyboard Input Integration** (1 test) ✅ **COMPLETED**
  - [x] Hardware keyboard input - `test_8_2_HardwareKeyboardInput` ✅

- **8.3 Query and Caching Path** (1 test) ✅ **COMPLETED**
  - [x] Query latency and caching - `test_8_3_QueryLatencyAndCaching` ✅

- **8.4 Learning Path Integration** (27 tests) ✅ **COMPLETED**
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

- **8.5 IM Switching with Real Data** (1 test) ✅ **COMPLETED**
  - [x] Switch between IM types - `test_8_5_SwitchBetweenIM` ✅

- **8.6 LIMEService Voice Input Integration** (15 tests) ✅ **COMPLETED**
  - [x] Voice input launch - `test_8_6_1_VoiceInputLaunch` ✅
  - [x] Voice IME unavailable fallback - `test_8_6_2_VoiceIMEUnavailableFallback` ✅
  - [x] Voice input intent configuration - `test_8_6_3_VoiceInputIntentConfiguration` ✅
  - [x] IME change monitoring setup - `test_8_6_4_IMEChangeMonitoringSetup` ✅
  - [x] Switch back to LIME - `test_8_6_5_SwitchBackToLIME` ✅
  - [x] Voice input broadcast receiver - `test_8_6_6_VoiceInputBroadcastReceiver` ✅
  - [x] Voice input null IMM - `test_8_6_7_VoiceInputNullIMM` ✅
  - [x] Voice input security exception - `test_8_6_8_VoiceInputSecurityException` ✅
  - [x] Voice input receiver unregister error - `test_8_6_9_VoiceInputReceiverUnregisterError` ✅
  - [x] Voice input monitoring timeout - `test_8_6_10_VoiceInputMonitoringTimeout` ✅
  - [x] Voice input from candidate view - `test_8_6_11_VoiceInputFromCandidateView` ✅
  - [x] Voice input results insertion - `test_8_6_12_VoiceInputResultsInsertion` ✅
  - [x] Voice input with composing text - `test_8_6_13_VoiceInputWithComposingText` ✅
  - [x] Multiple voice input invocations - `test_8_6_14_MultipleVoiceInputInvocations` ✅
  - [x] Voice input disabled preference - `test_8_6_15_VoiceInputDisabledPreference` ✅

- **8.7 LIMEService IME Selection and Options Menu** (12 tests) ✅ **COMPLETED**
  - [x] Options menu invocation - `test_8_7_1_OptionsMenuInvocation` ✅
  - [x] IM picker menu item selection - `test_8_7_2_IMPickerMenuItemSelection` ✅
  - [x] Settings menu item selection - `test_8_7_3_SettingsMenuItemSelection` ✅
  - [x] Han converter menu item selection - `test_8_7_4_HanConverterMenuItemSelection` ✅
  - [x] IM picker dialog creation - `test_8_7_5_IMPickerDialogCreation` ✅
  - [x] IM selection from picker - `test_8_7_6_IMSelectionFromPicker` ✅
  - [x] IM picker empty list - `test_8_7_7_IMPickerEmptyList` ✅
  - [x] IM picker dialog dismissal - `test_8_7_8_IMPickerDialogDismissal` ✅
  - [x] Build activated IM list filtering - `test_8_7_9_BuildActivatedIMListFiltering` ✅
  - [x] Switch to next IM forward - `test_8_7_10_SwitchToNextIMForward` ✅
  - [x] Switch to next IM backward - `test_8_7_11_SwitchToNextIMBackward` ✅
  - [x] IM switching single IM - `test_8_7_12_IMSwitchingSingleIM` ✅

- **8.8 LIMEService Theme and UI Styling** (6 tests) ✅ **COMPLETED**
  - [x] Keyboard theme retrieval - `test_8_8_1_KeyboardThemeRetrieval` ✅
  - [x] Theme application to keyboard - `test_8_8_2_ThemeApplicationToKeyboard` ✅
  - [x] Invalid theme ID handling - `test_8_8_3_InvalidThemeIDHandling` ✅
  - [x] Navigation bar icon styling - `test_8_8_4_NavigationBarIconStyling` ✅
  - [x] Navigation bar styling API level - `test_8_8_5_NavigationBarStylingAPILevel` ✅
  - [x] Navigation bar styling exception - `test_8_8_6_NavigationBarStylingException` ✅

**Phase 8 Total**: 65 existing tests (8.1-8.8) = **65 tests** (Comprehensive regression coverage with real IM data)

**Note**: Sections 8.9+ were removed to avoid duplicating Phase 3 and Phase 5 tests. Phase 8 regression tests focus on end-to-end workflows that uniquely benefit from the real IM data environment (8.1-8.8). Additional coverage will come from Phase 3.16-3.17 unit tests (SearchServer) and Phase 5.27-5.31 unit/integration tests (LIMEService) - those phases already include regression tests where real data provides value.

---

### Phase 9: Performance Tests (See Section "Phase 9" for details)

**Objective**: Ensure no performance regression.

**Test Coverage**: 10+ benchmark tests

- **9.1 Database Operation Benchmarks** (3 tests)
  - [x] Benchmark: Count operations
  - [x] Benchmark: Search operations
  - [x] Benchmark: Backup/import operations

- **9.2 File Operation Benchmarks** (2 tests)
  - [x] Benchmark: Export operations
  - [x] Benchmark: Import operations

- **9.3 Memory Usage** (1 test)
  - [x] Test: Memory leaks

---

## Total Test Coverage Summary

| Phase | Test Methods | Status | Priority |
|-------|--------------|--------|----------|
| Phase 1: LimeDB Layer | 40+ | ✅ Completed | High |
| Phase 2: DBServer Layer | 35+ | ✅ Completed | High |
| Phase 3: SearchServer Layer | 93 (50 existing + 43 new) | 🟡 Partial | High |
| Phase 4: UI Component Tests | 50+ | 🟡 Partial | Medium |
| Phase 5: IME Logic Tests | 191 (113 existing + 78 new) | ⚠️ Planned | High |
| Phase 6: Integration Tests | 22+ | ✅ Completed | High |
| Phase 7: Architecture Compliance | 10+ | ✅ Completed | High |
| Phase 8: Regression Tests (Core IME End-to-End) | 65 (8.1-8.8 complete) | ✅ Completed | **Critical** |
| Phase 9: Performance Tests | 6 | ✅ Completed | Medium |
| **TOTAL** | **~521 tests** | | |

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

### Existing Test Files

#### ✅ Core Layer Tests (5 files)

1. **`LimeDBTest.java`** ✅
   - **Location**: `app/src/androidTest/java/net/toload/main/hd/`
   - **Purpose**: Unit tests for `LimeDB` SQL operations layer
   - **Coverage**: Tests direct `LimeDB` operations (appropriate for unit testing the SQL layer)
   - **Status**: Exists and tests low-level database operations

2. **`DBServerTest.java`** ✅
   - **Location**: `app/src/androidTest/java/net/toload/main/hd/`
   - **Purpose**: Unit tests for `DBServer` file operations layer
   - **Coverage**: Tests file operations, import/export, backup/restore
   - **Status**: Exists and tests file management operations

3. **`LIMEServiceTest.java`** ✅
   - **Location**: `app/src/androidTest/java/net/toload/main/hd/`
   - **Purpose**: Tests for `LIMEService` IME functionality
   - **Coverage**: Tests `LIMEService` integration with `SearchServer`
   - **Status**: Exists and includes `SearchServer` integration tests

4. **`ApplicationTest.java`** ✅
   - **Location**: `app/src/androidTest/java/net/toload/main/hd/`
   - **Purpose**: Application-level tests
   - **Coverage**: Basic application functionality
   - **Status**: Exists

5. **`SetupImFragmentTest.java`** ✅
   - **Location**: `app/src/androidTest/java/net/toload/main/hd/`
   - **Purpose**: Tests for `SetupImFragment` UI component
   - **Coverage**: Button functionality tests
   - **Status**: Exists but may need updates for architecture compliance

### Missing Test Files

#### ❌ Critical Missing Tests (9+ files)

1. **`SearchServerTest.java`** ✅ **COMPLETED**
   - **Location**: `app/src/androidTest/java/net/toload/main/hd/`
   - **Purpose**: Unit tests for `SearchServer` layer
   - **Coverage**: Comprehensive tests implemented including:
     - UI-compatible methods (`getIm()`, `getKeyboard()`, `getImInfo()`, `setImInfo()`, `setIMKeyboard()`, `isValidTableName()`)
     - Delegation to `LimeDB` methods (verified)
     - Null `dbadapter` handling (all methods tested)
     - Converter integration (`hanConvert()`, `emojiConvert()`)
     - Search operations (`getMappingByCode()`, `getRecords()`, `getRelated()`)
     - Related phrase operations (`countRecordsRelated()`, `getRelatedByWord()`, `getRelatedById()`)
     - Backup/restore operations (`backupUserRecords()`, `restoreUserRecords()`, `checkBackuptable()`)
     - Record management (`addRecord()`, `updateRecord()`, `deleteRecord()`, `clearTable()`)
   - **Status**: ✅ Exists with comprehensive test coverage

2. **`ArchitectureComplianceTest.java`** ❌ **HIGH PRIORITY**
   - **Location**: `app/src/androidTest/java/net/toload/main/hd/`
   - **Purpose**: Architecture boundary compliance tests
   - **Required Tests**:
     - Static analysis: No direct `LimeDB` access from UI components
     - Static analysis: No SQL operations outside `LimeDB.java`
     - Static analysis: No file operations outside `DBServer.java`
     - Runtime verification: Component initialization checks
     - Runtime verification: Method call tracing
   - **Priority**: **HIGH** - Ensures architecture boundaries are respected

3. **`IntegrationTest.java`** ❌ **HIGH PRIORITY**
   - **Location**: `app/src/androidTest/java/net/toload/main/hd/`
   - **Purpose**: Integration tests for layer interactions
   - **Required Tests**:
     - `SearchServer` → `LimeDB` integration
     - `DBServer` → `LimeDB` integration
     - UI → `SearchServer` → `LimeDB` integration
     - UI → `DBServer` → `LimeDB` integration
     - Complete operation flows
   - **Priority**: **HIGH** - Validates layer interactions

4. **`PerformanceTest.java`** ❌ **MEDIUM PRIORITY**
   - **Location**: `app/src/androidTest/java/net/toload/main/hd/`
   - **Purpose**: Performance benchmarks and regression tests
   - **Required Tests**:
     - Database operation benchmarks
     - File operation benchmarks
     - Memory usage tests
     - Cache performance tests
   - **Priority**: **MEDIUM** - Important but not blocking

#### ❌ UI Component Tests (6+ files)

5. **`ManageImFragmentTest.java`** ❌
   - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
   - **Purpose**: Tests for `ManageImFragment`
   - **Required Tests**:
     - Architecture compliance (uses `SearchServer`, not `LimeDB`)
     - IM list loading via `SearchServer.getIm()`
     - Keyboard loading via `SearchServer.getKeyboard()`
     - Record management via `SearchServer.getRecords()` (delegates to `LimeDB.getRecords()`)
     - Word deletion via `SearchServer.deleteRecord()`
   - **Priority**: **HIGH** - Major UI component

6. **`ManageRelatedFragmentTest.java`** ❌
   - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
   - **Purpose**: Tests for `ManageRelatedFragment`
   - **Required Tests**:
     - Architecture compliance (uses `SearchServer`, not `LimeDB`)
     - Related phrase loading via `SearchServer.getRelated()`
     - Related phrase operations via `SearchServer`
   - **Priority**: **MEDIUM**

7. **`ManageImKeyboardDialogTest.java`** ❌
   - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
   - **Purpose**: Tests for `ManageImKeyboardDialog`
   - **Required Tests**:
     - Architecture compliance (uses `SearchServer`, not `LimeDB`)
     - Keyboard loading via `SearchServer.getKeyboard()`
     - Keyboard assignment via `SearchServer.setIMKeyboard()`
   - **Priority**: **MEDIUM**

8. **`ImportDialogTest.java`** ❌
   - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
   - **Purpose**: Tests for `ImportDialog`
   - **Required Tests**:
     - Architecture compliance (uses `SearchServer` and `DBServer`, not `LimeDB`)
     - IM list loading via `SearchServer.getIm()`
     - Table status checking via `SearchServer.countMapping()`
     - File import via `DBServer`
   - **Priority**: **MEDIUM**

9. **`ShareDialogTest.java`** ❌
   - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
   - **Purpose**: Tests for `ShareDialog`
   - **Required Tests**:
     - Architecture compliance (uses `SearchServer` and `DBServer`, not `LimeDB`)
     - IM list loading via `SearchServer.getIm()`
     - Database export via `DBServer.exportZippedDb()`
     - Related export via `DBServer.exportZippedDbRelated()`
   - **Priority**: **MEDIUM**

10. **`SetupImLoadDialogTest.java`** ❌
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `SetupImLoadDialog`
    - **Required Tests**:
      - Architecture compliance (delegates to `SetupImController`, no direct DB/file ops)
      - Local file selection flow (.limedb, .lime, .cin)
      - Download buttons for remote .limedb files
      - Backup/restore learning checkbox behavior
      - File type validation (.limedb, .lime, .cin, .txt)
      - Delegation to fragment methods: `importDb()`, `importTxtTable()`, `downloadAndImportZippedDb()`
      - Error handling for invalid files, missing permissions
    - **Priority**: **MEDIUM**

11. **`VoiceInputActivityTest.java`** ✅ **COMPLETED**
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/`
    - **Purpose**: Tests for `VoiceInputActivity`
    - **Required Tests**:
      - Activity lifecycle (onCreate, onDestroy)
      - RecognizerIntent creation, launch, and availability checks
      - Voice recognition result handling (success, cancel, error, empty results)
      - Broadcast communication with LIMEService
      - Transparent window configuration
      - Error handling (unavailable, ActivityNotFoundException, generic exceptions)
      - Architecture compliance (no direct database access, broadcast-only communication)
    - **Priority**: **MEDIUM**
    - **Status**: ✅ Implemented with 35 test methods covering all 10 subsections plus edge cases

12. **`ManageImAddDialogTest.java`** ❌ (Optional)
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ManageImAddDialog`
    - **Priority**: **LOW**

13. **`ManageImEditDialogTest.java`** ❌ (Optional)
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ManageImEditDialog`
    - **Priority**: **LOW**

14. **`ManageRelatedAddDialogTest.java`** ❌ (Optional)
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ManageRelatedAddDialog`
    - **Priority**: **LOW**

15. **`ManageRelatedEditDialogTest.java`** ❌ (Optional)
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ManageRelatedEditDialog`
    - **Priority**: **LOW**

#### ❌ Runnable Class Tests (6 files)

16. **Note**: `SetupImLoadRunnable` removed - download/import operations now in `SetupImController.downloadAndImportZippedDb()` - See Section 4.7 for controller tests

17. **`ShareDbRunnableTest.java`** ❌
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ShareDbRunnable`
    - **Required Tests**:
      - Architecture compliance (uses `DBServer.exportZippedDb()`, not direct file ops)
    - **Priority**: **LOW**

18. **`ShareRelatedDbRunnableTest.java`** ❌
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ShareRelatedDbRunnable`
    - **Required Tests**:
      - Architecture compliance (uses `DBServer.exportZippedDbRelated()`, not direct file ops)
    - **Priority**: **LOW**

19. **`ShareTxtRunnableTest.java`** ❌ (Optional)
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ShareTxtRunnable`
    - **Priority**: **LOW**

20. **`ShareRelatedTxtRunnableTest.java`** ❌ (Optional)
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ShareRelatedTxtRunnable`
    - **Priority**: **LOW**

21. **`ManageImRunnableTest.java`** ⛔ **REMOVED**
    - **Note**: `ManageImRunnable` has been removed; tests for this runnable are no longer applicable. See `ImController.loadRecordsAsync` in the test plan for async loading tests.

22. **`ManageRelatedRunnableTest.java`** ❌ (Optional)
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ManageRelatedRunnable`
    - **Priority**: **LOW**

### Test File Summary

| Category | Existing | Missing | Total Needed |
|----------|----------|---------|--------------|
| **Core Layer Tests** | 5 | 0 | 5 |
| **IME Logic Tests (Phase 5)** | 1⚠️ | 0 | 1 (needs verification) |
| **Architecture Tests** | 0 | 1 | 1 |
| **Integration Tests** | 0 | 1 | 1 |
| **Performance Tests** | 0 | 1 | 1 |
| **UI Fragment Tests** | 1 | 2 | 3 |
| **UI Activity Tests** | 1 | 0 | 1 |
| **UI Dialog Tests** | 0 | 5-9 | 5-9 |
| **Controller Tests** | 0 | 2 | 2 |
| **TOTAL** | **8** | **10-20** | **18-28** |

### Test File Priority

#### 🔴 **CRITICAL** (Must Have)
1. `SearchServerTest.java` - ✅ Central interface layer - COMPLETED
2. `LIMEServiceTest.java` - ⚠️ IME logic and input handling - EXISTS (needs verification against Phase 5 plan)
3. `ArchitectureComplianceTest.java` - Architecture validation
4. `IntegrationTest.java` - Layer interaction validation (including IME integration tests - see Section 6.7)

#### 🟡 **HIGH** (Should Have)
5. `ManageImFragmentTest.java` - Major UI component
5. Update `SetupImFragmentTest.java` - Add architecture compliance tests

#### 🟢 **MEDIUM** (Nice to Have)
6. `ManageRelatedFragmentTest.java`
7. `ManageImKeyboardDialogTest.java`
8. `ImportDialogTest.java`
9. `ShareDialogTest.java`
10. `VoiceInputActivityTest.java` - ✅ Voice input integration - COMPLETED
11. `SetupImController` download tests (see Section 4.7)

#### ⚪ **LOW** (Optional)
11. `PerformanceTest.java`
12. `SetupImLoadDialogTest.java`
13. Other dialog and runnable tests

---

## Test Strategy

### 1. Unit Tests (Layer Isolation)

Test each layer independently with mocked dependencies.

### 2. Integration Tests (Layer Interaction)

Test interactions between layers with real implementations.

### 3. Architecture Compliance Tests

Verify architectural boundaries are respected (no violations).

### 4. Regression Tests

Ensure existing functionality still works.

### 5. Performance Tests

Benchmark critical operations before/after refactoring.

---

## Test Plan by Layer

### Phase 1: LimeDB Layer Tests

**Objective**: Test all SQL operations in `LimeDB.java` are correct and use parameterized queries.

#### 1.1 Core Database Operations

- [x] **Test: `countRecords()` with various WHERE clauses**
  - Test with null WHERE clause (count all records) - ✅ `testLimeDBCountRecordsWithNullWhereClause()`
  - Test with simple WHERE clause - ✅ `testLimeDBCountRecordsWithWhereClause()`
  - Test with multiple conditions - ✅ `testLimeDBCountRecordsWithMultipleConditions()` (comprehensive test with AND/OR)
  - Test with parameterized arguments - ✅ `testLimeDBCountRecordsWithWhereClause()` (uses parameterized queries)
  - Test with invalid table names (should return 0) - ✅ `testLimeDBCountRecordsWithInvalidTableName()`
  - Test with empty table (should return 0) - ✅ `testLimeDBCountRecordsWithEmptyTable()`

- [x] **Test: `addRecord()` with ContentValues**
  - Test adding valid record - ✅ `testLimeDBAddRecordDeleteRecordUpdateRecordBranches()`
  - Test with invalid table name (should return -1) - ✅ `testLimeDBAddRecordWithInvalidInputs()`
  - Test with null ContentValues (should return -1) - ✅ `testLimeDBAddRecordWithInvalidInputs()`
  - Test with missing required columns - ✅ Covered in invalid input tests
  - Test transaction rollback on error - ✅ Covered in error handling tests

- [x] **Test: `updateRecord()` with ContentValues**
  - Test updating existing record - ✅ `testLimeDBAddRecordDeleteRecordUpdateRecordBranches()`
  - Test with WHERE clause matching multiple records - ✅ `testLimeDBUpdateRecordWithMultipleRecords()` (comprehensive test)
  - Test with invalid table name (should return -1) - ✅ `testLimeDBUpdateRecordWithInvalidInputs()`
  - Test with no matching records (should return 0) - ✅ `testLimeDBUpdateRecordWithNoMatchingRecords()` (explicit test)
  - Test parameterized WHERE arguments - ✅ `testLimeDBAddRecordDeleteRecordUpdateRecordBranches()` (uses parameterized queries)

- [x] **Test: `deleteRecord()` with parameterized queries**
  - Test deleting single record - ✅ `testLimeDBAddRecordDeleteRecordUpdateRecordBranches()`
  - Test deleting multiple records - ✅ `testLimeDBDeleteRecordWithMultipleRecords()` (comprehensive test)
  - Test with invalid table name (should return -1) - ✅ `testLimeDBDeleteRecordWithInvalidInputs()`
  - Test with no matching records (should return 0) - ✅ `testLimeDBDeleteRecordWithNoMatchingRecords()` (explicit test)
  - Test parameterized WHERE arguments - ✅ `testLimeDBAddRecordDeleteRecordUpdateRecordBranches()` (uses parameterized queries)

#### 1.2 Unified Methods (Wrappers)

- [x] **Test: `countMapping()` delegates to `countRecords()`**
  - Verify it calls `countRecords(table, null, null)` - ✅ `testLimeDBCountMappingDelegatesToCountRecords()`
  - Test with valid table name - ✅ Covered in delegation test
  - Test with invalid table name - ✅ Covered in edge case tests

- [x] **Test: `count()` delegates to `countRecords()`**
  - **Status**: `count()` method does not exist in LimeDB (was removed during refactoring)
  - **Note**: Use `countRecords()` directly instead of `count()` wrapper

- [x] **Test: `getRecordSize()` delegates to `countRecords()`**
  - Test with null query (all records) - ✅ `testLimeDBGetRecordSizeDelegatesToCountRecords()`
  - Test with query by code - ✅ `testLimeDBGetRecordSizeDelegatesToCountRecords()`
  - Test with query by record - ✅ `testLimeDBGetRecordSizeDelegatesToCountRecords()`
  - Test WHERE clause construction - ✅ Covered in delegation test
  - **Note**: `getRecordSize()` is a wrapper method that delegates to unified `countRecords()`

- [x] **Test: `getRelatedSize()` delegates to `countRecords()`**
  - Test with null pword (all records) - ✅ `testLimeDBGetRelatedSizeDelegatesToCountRecords()`
  - Test with single character pword - ✅ `testLimeDBGetRelatedSizeEdgeCases()`
  - Test with multi-character pword - ✅ `testLimeDBGetRelatedSizeEdgeCases()`
  - Test WHERE clause construction - ✅ Covered in delegation test
  - **Note**: `getRelatedSize()` is a wrapper method that delegates to unified `countRecords()`

- [x] **Test: `getRelated()` method**
  - Test loading related phrases with pagination - ✅ `testLimeDBGetRelated()`
  - Test delegation to `LimeDB.getRelated()` - ✅ Covered in getRelated test
  - Test with various pword parameters - ✅ Covered in getRelated test

- [x] **Test: `getAllRelated()` method**
  - Test retrieving all related phrases - ✅ `testLimeDBGetAllRelated()`
  - Test returns `List<Related>` objects - ✅ Covered in getAllRelated test

#### 1.3 Backup/Import Operations

- [x] **Test: `prepareBackup()` unified method**
  - Test with single table name - ✅ `testLimeDBPrepareBackupWithSingleTable()`
  - Test with multiple table names - ✅ `testLimeDBPrepareBackupWithMultipleTables()`
  - Test with null table names (all tables) - ✅ Covered in prepareBackup tests
  - Test with `includeRelated=true` - ✅ `testLimeDBPrepareBackupWithIncludeRelated()`
  - Test with `includeRelated=false` - ✅ `testLimeDBPrepareBackupWithSingleTable()` (uses false)
  - Test with invalid table names (should fail gracefully) - ✅ `testLimeDBPrepareBackupWithInvalidTableName()`
  - Test file creation and data integrity - ✅ Covered in all prepareBackup tests

- [x] **Test: `importDb()` unified method**
  - Test importing single table - ✅ `testLimeDBImportDBWithSingleTable()`
  - Test importing multiple tables - ✅ `testLimeDBImportDbWithMultipleTables()` (explicit test)
  - Test with `overwriteExisting=true` - ✅ `testLimeDBImportBackupWithOverwriteExisting()`
  - Test with `overwriteExisting=false` - ✅ `testLimeDBImportDbWithOverwriteExistingFalse()` (explicit test)
  - Test with `includeRelated=true` - ✅ `testLimeDBImportDbWithIncludeRelated()` (explicit test)
  - Test with `includeRelated=false` - ✅ `testLimeDBImportDBWithSingleTable()` (uses false)
  - Test with invalid backup file (should fail gracefully) - ✅ `testLimeDBImportBackupWithInvalidFile()`
  - Test data integrity after import - ✅ Covered in importDb tests and pair tests

- [x] **Test: `exportTxtTable()` with related table**
  - Test exporting related table to text file format
  - Test format: `pword|cword|basescore|userscore` (pipe-delimited)
  - Test file creation and data integrity
  - Test with empty related table (should return false)
  - **Status**: `testLimeDBExportTxtTableWithRelatedTable()` implemented

- [x] **Test: `exportTxtTable()` / `importTxtTable()` pair for related table**
  - Test export/import cycle with data consistency verification
  - Test format: `pword|cword|basescore|userscore` (pipe-delimited)
  - Test backward compatibility with legacy format `pword+cword|basescore|userscore`
  - Test data integrity after import (record count and specific records match)
  - **Status**: `testLimeDBExportTxtTableRelatedAndImportTxtTableWithDataConsistency()` implemented

- [x] **Test: Wrapper methods delegate correctly**
  - `prepareBackupDb()` → `prepareBackup()` (LimeDB wrapper) - ✅ `testLimeDBPrepareBackupDbDelegatesToPrepareBackup()`
  - `prepareBackupRelatedDb()` → `prepareBackup()` (LimeDB wrapper) - ✅ `testLimeDBPrepareBackupRelatedDbDelegatesToPrepareBackup()`
  - `LimeDB.importDbRelated(File)` → `LimeDB.importDb(File, List<String>, boolean, boolean)` (LimeDB wrapper) - ✅ `testLimeDBImportDbRelatedDelegatesToImportDb()`, `testLimeDBImportBackupRelatedDbDelegatesToImportBackup()`
  - Comprehensive wrapper delegation test - ✅ `testLimeDBWrapperMethodsDelegationComplete()`
  - **Note**: `DBServer.importDb(File, String)` and `DBServer.importDbRelated(File)` wrapper tests are in DBServerTest.java

#### 1.4 Helper Methods


- [x] **Test: `getBackupTableRecords()`** - Available in both LimeDB and SearchServer
  - Test with valid backup table name (ends with "_user") - ✅ `testLimeDBGetBackupTableRecordsWithValidBackupTable()`
  - Test with invalid format (should return null) - ✅ `testLimeDBGetBackupTableRecordsWithInvalidFormat()`
  - Test with invalid base table name (should return null) - ✅ `testLimeDBGetBackupTableRecordsWithInvalidBaseTableName()`
  - Test cursor validity and data access - ✅ Covered in getBackupTableRecords tests
  - Test delegation from SearchServer to LimeDB - ✅ Tests implemented in SearchServerTest.java
  - Test with null dbadapter in SearchServer (should return null) - ✅ Tests implemented in SearchServerTest.java
  - **Note**: `getBackupTableRecords()` exists in both LimeDB and SearchServer; SearchServer delegates to LimeDB

- [x] **Test: `buildWhereClause()` helper**
  - Test with empty map (should return null) - ✅ `testLimeDBBuildWhereClauseWithEmptyMap()`
  - Test with single condition - ✅ `testLimeDBBuildWhereClauseWithSingleCondition()`
  - Test with multiple conditions - ✅ `testLimeDBBuildWhereClauseWithMultipleConditions()`
  - Test parameter array construction - ✅ Covered in buildWhereClause tests
  - Test with null map - ✅ `testLimeDBBuildWhereClauseWithNullMap()`

- [x] **Test: `queryWithPagination()` helper**
  - Test with limit and offset - ✅ `testLimeDBQueryWithPaginationWithLimitAndOffset()`
  - Test with no limit (limit=0) - ✅ `testLimeDBQueryWithPaginationWithNoLimit()`
  - Test with invalid table name (should return null) - ✅ `testLimeDBQueryWithPaginationWithInvalidTableName()`
  - Test ORDER BY clause - ✅ Covered in queryWithPagination tests
  - Test WHERE clause integration - ✅ `testLimeDBQueryWithPaginationWithWhereClause()`

#### 1.5 Table Name Validation

- [x] **Test: `isValidTableName()`**
  - Test all valid table names from whitelist - ✅ `testLimeDBIsValidTableNameWithAllValidTables()`
  - Test invalid table names - ✅ `testLimeDBIsValidTableNameWithInvalidTables()`
  - Test null/empty strings - ✅ Covered in isValidTableName tests
  - Test SQL injection attempts (should reject) - ✅ `testLimeDBIsValidTableNameWithSQLInjectionAttempts()`

#### 1.6 SQL Injection Prevention

- [x] **Test: All methods use parameterized queries**
  - Verify no string concatenation in WHERE clauses - ✅ `testLimeDBSQLInjectionPreventionInCountRecords()`
  - Verify table names are validated before use - ✅ `testLimeDBSQLInjectionPreventionInTableName()`
  - Test with malicious input (SQL injection attempts) - ✅ All SQL injection prevention tests
  - Verify all user input is parameterized - ✅ `testLimeDBSQLInjectionPreventionInAddRecord()`, `testLimeDBSQLInjectionPreventionInUpdateRecord()`, `testLimeDBSQLInjectionPreventionInDeleteRecord()`

---

### Phase 2: DBServer Layer Tests

**Objective**: Test all file operations are centralized in `DBServer.java`.

#### 2.1 File Export Operations

- [x] **Test: `exportZippedDb(String tableName, File targetFile, Runnable progressCallback)`** - `testDBServerExportImDatabaseWithValidTableName()`, `testDBServerExportImDatabaseWithInvalidTableName()`, `testDBServerExportImDatabaseWithProgressCallback()`, `testDBServerExportZippedDbWithNullTableName()`, `testDBServerExportZippedDbWithNullTargetFile()`, `testDBServerExportZippedDbWithDataIntegrity()`, `testDBServerExportZippedDbWithExistingTargetFile()` added
  - Test exporting single IM database
  - Test with valid tableName (e.g., "custom", "cj", "phonetic")
  - Test with invalid tableName (should fail gracefully)
  - Test with null tableName (should fail gracefully)
  - Test with null targetFile (should fail gracefully)
  - Test file creation in cache directory
  - Test zip file integrity (can be unzipped and contains valid database)
  - Test progress callback invocation
  - Test cleanup of temporary files after export
  - Test data integrity: exported database contains correct records
  - Test with existing targetFile (should delete and recreate)
  - Test error handling (e.g., insufficient storage, permission denied)

- [x] **Test: `exportZippedDbRelated(File targetFile, Runnable progressCallback)`** - `testDBServerExportRelatedDatabase()` exists
- [x] **Test: `exportZippedDbRelated()` / `importZippedDbRelated()` pair** - `testDBServerExportZippedDbRelatedAndImportWithDataConsistency()` added
  - Test exporting related phrase database
  - Test file creation in cache directory
  - Test zip file integrity
  - Test progress callback invocation
  - Test cleanup of temporary files
  - Test data integrity: exported database contains correct related records

#### 2.2 File Import Operations

- [x] **Test: `importTxtTable(String filename, String tablename, LIMEProgressListener)`** - `testDBServerImportTxtTableWithStringFilename()`, `testDBServerImportTxtTableWithInvalidTableName()`, `testDBServerImportTxtTableWithEmptyFile()`, `testDBServerImportTxtTableWithProgressListener()` added
  - Test importing .lime file from file path
  - Test importing .cin file from file path
  - Test with valid table name
  - Test with invalid table name (should fail gracefully)
  - Test with null filename (should fail gracefully)
  - Test with non-existent file (should fail gracefully)
  - Test progress listener updates throughout import
  - Test data integrity after import (record count matches file)
  - Test with empty file (should handle gracefully)
  - Test with malformed file (should handle gracefully)
  - Test cache reset after import

- [x] **Test: `importTxtTable(File sourcefile, String tablename, LIMEProgressListener)`** - `testDBServerImportTxtTableWithFile()`, `testDBServerImportTxtTableWithNullFile()`, `testDBServerImportTxtTableWithNonExistentFile()` added
  - Test importing .lime file from File object
  - Test importing .cin file from File object
  - Test with valid table name
  - Test with invalid table name (should fail gracefully)
  - Test with null File (should fail gracefully)
  - Test with non-existent File (should fail gracefully)
  - Test progress listener updates throughout import
  - Test data integrity after import
  - Test cache reset after import

- [x] **Test: `importDb(File sourcedb, String tableName)`** - `testDBServerImportDbWithUncompressedDatabase()`, `testDBServerImportDbWithNullSourceDb()`, `testDBServerImportDbWithNonExistentFile()` added
  - Test importing uncompressed database file
  - Test with valid tableName
  - Test with invalid tableName (should fail gracefully)
  - Test with null sourcedb (should fail gracefully)
  - Test with non-existent file (should fail gracefully)
  - Test with invalid database file format (should fail gracefully)
  - Test data integrity after import (records match source)
  - Test overwrite behavior (existing records replaced)
  - Test cache reset after import

- [x] **Test: `importZippedDb(File compressedSourceDB, String tableName)`**
  - Test importing compressed database (.limedb) file
  - Test unzip and import flow
  - Test with valid tableName
  - Test with invalid tableName (should fail gracefully)
  - Test with invalid zip file (should fail gracefully)
  - Test with zip containing multiple files (should use first file)
  - Test with empty zip file (should fail gracefully)
  - Test data integrity after import
  - Test cache reset after import
  - Test cleanup of temporary unzipped files

- [x] **Test: `importDbRelated(File sourcedb)`** - `testDBServerImportDbRelatedWithUncompressedDatabase()` added
  - Test importing uncompressed related database file
  - Test with valid related database file
  - Test with null sourcedb (should fail gracefully)
  - Test with non-existent file (should fail gracefully)
  - Test with invalid database file format (should fail gracefully)
  - Test data integrity after import (related records match source)
  - Test cache reset after import

- [x] **Test: `importZippedDbRelated(File compressedSourceDB)`**
  - Test importing compressed related database (.limedb) file
  - Test unzip and import flow
  - Test with invalid zip file (should fail gracefully)
  - Test with zip containing multiple files (should use first file)
  - Test with empty zip file (should fail gracefully)
  - Test data integrity after import
  - Test cache reset after import
  - Test cleanup of temporary unzipped files

#### 2.3 Import/Export Pair Tests (Data Consistency)

- [x] **Test: `exportZippedDb()` / `importZippedDb()` pair** - `testDBServerExportZippedDbAndImportWithDataConsistency()` added
  - Test export then import cycle
  - Test data consistency: record count matches before and after
  - Test data consistency: specific records match before and after
  - Test with multiple tables
  - Test with empty table (should handle gracefully)

- [x] **Test: `exportZippedDbRelated()` / `importZippedDbRelated()` pair** - `testDBServerExportZippedDbRelatedAndImportWithDataConsistency()` added
  - Test export then import cycle for related table
  - Test data consistency: record count matches before and after
  - Test data consistency: specific related records match before and after

- [x] **Test: `exportTxtTable()` / `importTxtTable()` pair** - `testDBServerExportTxtTableAndImportTxtTablePair()`, `testDBServerExportTxtTableRelatedAndImportTxtTablePair()` added
  - Test export then import cycle for text format
  - Test data consistency: record count matches before and after
  - Test data consistency: specific records match before and after
  - Test with related table format (`pword|cword|basescore|userscore`)
  - Test with regular table format (`code|word|score|basescore`)
  - Test IM info header preservation (for regular tables)

#### 2.4 Backup/Restore Operations

- [x] **Test: `backupDatabase(Uri uri)`** - `testDBServerBackupDatabaseWithUri()`, `testDBServerBackupDatabaseWithNullUri()`, `testDBServerBackupDatabaseWithDataIntegrity()` added
  - Test backing up entire database and preferences to URI
  - Test with valid URI
  - Test with null URI (should fail gracefully)
  - Test database file backup
  - Test journal file backup
  - Test shared preferences backup
  - Test zip file creation
  - Test database connection hold during backup
  - Test cleanup of temporary files
  - Test notification messages
  - Test error handling (e.g., insufficient storage, permission denied)

- [x] **Test: `restoreDatabase(Uri uri)`** - `testDBServerRestoreDatabaseWithUri()`, `testDBServerRestoreDatabaseWithNullUri()`, `testDBServerRestoreDatabaseWithDataIntegrity()` added
  - Test restoring database and preferences from URI
  - Test with valid URI containing backup
  - Test with null URI (should fail gracefully)
  - Test with invalid backup file (should fail gracefully)
  - Test database file restoration
  - Test journal file restoration
  - Test shared preferences restoration
  - Test unzip operation
  - Test database connection reopening after restore
  - Test cleanup of temporary files
  - Test notification messages
  - Test error handling

- [x] **Test: `restoreDatabase(String srcFilePath)`** - `testDBServerRestoreDatabaseWithStringPath()` added
  - Test restoring database and preferences from file path
  - Test with valid backup file path
  - Test with null path (should fail gracefully)
  - Test with non-existent file (should fail gracefully)
  - Test with invalid backup file format (should fail gracefully)
  - Test database file restoration
  - Test shared preferences restoration
  - Test unzip operation
  - Test database connection reopening after restore
  - Test cleanup of temporary files
  - Test error handling

- [x] **Test: `backupDatabase()` / `restoreDatabase()` pair** - `testDBServerBackupDatabaseAndRestoreWithDataConsistency()` added
  - Test backup then restore cycle
  - Test data consistency: database state matches before and after
  - Test shared preferences consistency
  - Test with existing data (should preserve or overwrite based on operation)

- [x] **Test: `backupDefaultSharedPreference(File sharePrefs)`** - `testDBServerBackupDefaultSharedPreference()`, `testDBServerBackupDefaultSharedPreferenceWithNullFile()` added
  - Test backing up shared preferences to file
  - Test with valid File
  - Test with null File (should fail gracefully)
  - Test file creation
  - Test serialization of preferences
  - Test with existing file (should delete and recreate)
  - Test error handling (e.g., insufficient storage, permission denied)

- [x] **Test: `restoreDefaultSharedPreference(File sharePrefs)`** - `testDBServerRestoreDefaultSharedPreference()`, `testDBServerRestoreDefaultSharedPreferenceWithNonExistentFile()` added
  - Test restoring shared preferences from file
  - Test with valid backup file
  - Test with null File (should fail gracefully)
  - Test with non-existent file (should fail gracefully)
  - Test with invalid file format (should fail gracefully)
  - Test deserialization of preferences
  - Test preference values match backup
  - Test error handling

- [x] **Test: `backupDefaultSharedPreference()` / `restoreDefaultSharedPreference()` pair** - `testDBServerBackupDefaultSharedPreferenceAndRestorePair()` added
  - Test backup then restore cycle for shared preferences
  - Test preference consistency: values match before and after
  - Test with various preference types (String, int, boolean, etc.)

#### 2.5 User Records Backup/Restore (via LimeDB)

- [x] **Test: `backupUserRecords(String table)`** (LimeDB method used by DBServer) - `testDBServerBackupUserRecordsViaLimeDB()`, `testDBServerBackupUserRecordsWithInvalidTableName()` added
  - Test backing up user-learned records (score > 0) to backup table
  - Test with valid table name
  - Test with invalid table name (should fail gracefully)
  - Test backup table creation (`{table}_user`)
  - Test only records with score > 0 are backed up
  - Test backup table contains correct records
  - Test with empty table (should create empty backup table)
  - Test with existing backup table (should drop and recreate)

- [x] **Test: `restoreUserRecords(String table)`** (LimeDB method used by DBServer) - `testDBServerRestoreUserRecordsViaLimeDB()` added
  - Test restoring user-learned records from backup table
  - Test with valid table name
  - Test with invalid table name (should fail gracefully)
  - Test with non-existent backup table (should return 0)
  - Test records are restored correctly
  - Test score preservation
  - Test with empty backup table (should return 0)
  - Test with existing records (should update scores)

- [x] **Test: `backupUserRecords()` / `restoreUserRecords()` pair** - `testDBServerBackupUserRecordsAndRestoreUserRecordsPair()` added
  - Test backup then restore cycle for user records
  - Test data consistency: user records match before and after
  - Test integration with mapping file reload workflow

- [x] **Test: `getBackupTableRecords(String backupTableName)`** (LimeDB method used by DBServer) - `testDBServerGetBackupTableRecords()` added
  - Test retrieving all records from backup table
  - Test with valid backup table name (ends with "_user")
  - Test with invalid format (should return null)
  - Test with invalid base table name (should return null)
  - Test cursor validity and data access
  - Test with empty backup table (should return empty cursor)

- [x] **Test: `checkBackupTable(String table)`** (LimeDB method used by DBServer) - `testDBServerCheckBackupTable()` added
  - Test checking if backup table exists and has records
  - Test with valid table name
  - Test with invalid table name (should return false)
  - Test with non-existent backup table (should return false)
  - Test with empty backup table (should return false)
  - Test with backup table containing records (should return true)

#### 2.6 Download and Import Integration

- [x] **Test: Download and Import Flow**
  - Test download via `LIMEUtilities.downloadRemoteFile()`
  - Test import via `DBServer.importZippedDb()`
  - Test with invalid URL (should fail gracefully)
  - Test with `restorePreference=true`
  - Test with `restorePreference=false`
  - Test progress listener updates throughout
  - Test error handling at each step
  - Test cleanup on failure

#### 2.7 Method Delegation

- [x] **Test: DBServer wrapper methods delegate to LimeDB unified methods** - `testDBServerImportBackupDbDelegation()`, `testDBServerImportBackupRelatedDbDelegation()`, `testDBServerImportTxtTableDelegatesToLimeDB()` added
  - `DBServer.importDb(File, String)` → `LimeDB.importDb(File, List<String>, boolean, boolean)` (DBServer wrapper)
  - `DBServer.importDbRelated(File)` → `LimeDB.importDbRelated(File)` → `LimeDB.importDb(File, List<String>, boolean, boolean)` (DBServer wrapper)
  - `DBServer.importZippedDb(File, String)` → unzip → `LimeDB.importDb(File, List<String>, boolean, boolean)`
  - `DBServer.importZippedDbRelated(File)` → unzip → `LimeDB.importDbRelated(File)`
  - `DBServer.importTxtTable(File, String, LIMEProgressListener)` → `LimeDB.importTxtTable(String, LIMEProgressListener)`
  - `DBServer.exportZippedDb(String, File, Runnable)` → `LimeDB.prepareBackup(File, List<String>, boolean)` → zip
  - `DBServer.exportZippedDbRelated(File, Runnable)` → `LimeDB.prepareBackup(File, null, true)` → zip

- [x] **Test: LimeDB wrapper methods delegate to unified methods** - `testLimeDBImportBackupRelatedDbDelegatesToImportBackup()`, `testLimeDBPrepareBackupDbDelegatesToPrepareBackup()`, `testLimeDBPrepareBackupRelatedDbDelegatesToPrepareBackup()` exist in LimeDBTest.java
  - `LimeDB.importDbRelated(File)` → `LimeDB.importDb(File, List<String>, boolean, boolean)` (LimeDB wrapper)
  - `LimeDB.prepareBackupDb(String, String)` → `LimeDB.prepareBackup(File, List<String>, boolean)` (LimeDB wrapper)
  - `LimeDB.prepareBackupRelatedDb(String)` → `LimeDB.prepareBackup(File, null, true)` (LimeDB wrapper)

#### 2.8 File Operation Isolation

- [x] **Test: No file operations outside DBServer** - `testDBServerRunnableClassesUseDBServerForFileOperations()`, `testDBServerMainActivityUsesDBServerForFileOperations()`, `testDBServerLimeDBOnlyHasTextFileOperations()`, `testDBServerUIFragmentsUseDBServerForFileOperations()` added
  - Verify Runnable classes don't have file operations (except using DBServer methods)
  - Verify MainActivity doesn't have file operations (except using DBServer methods)
  - Verify UI components don't have file operations (except using DBServer methods)
  - Verify LimeDB doesn't have file operations (except text file import/export which are database operations)
  - (This is an architecture compliance test)

---

### Phase 3: SearchServer Layer Tests

**Objective**: Test `SearchServer` as the single interface for all database operations.

#### 3.1 UI-Compatible Methods

- [x] **Test: `getIm()`**
  - Test with null code (all IMs)
  - Test with specific code
  - Test with type filter (LIME.IM_TYPE_NAME, LIME.IM_TYPE_KEYBOARD)
  - Test delegation to `LimeDB.getIm()`
  - Test with null dbadapter (should return empty list)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `getKeyboard()`**
  - Test retrieving all keyboards
  - Test delegation to `LimeDB.getKeyboard()`
  - Test with null dbadapter (should return empty list)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `getImInfo()`**
  - Test retrieving IM info field
  - Test with non-existent IM (should return null)
  - Test with non-existent field (should return null)
  - Test delegation to `LimeDB.getImInfo()`
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `setImInfo()`**
  - Test setting IM info field
  - Test updating existing field
  - Test delegation to `LimeDB.setImInfo()`
  - Test with null dbadapter (should fail gracefully)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `setIMKeyboard()` overloads**
  - Test with (String imConfig, String value, String keyboard)
  - Test with (String imConfig, Keyboard keyboard)
  - Test delegation to `LimeDB.setIMKeyboard()` / `setImKeyboard()`
  - Test with null dbadapter (should fail gracefully)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `isValidTableName()`**
  - Test delegation to `LimeDB.isValidTableName()`
  - Test with valid table names
  - Test with invalid table names
  - Test with null dbadapter (should return false)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `getImAllConfigList(String code)`**
  - Test retrieving IM config list for specific code
  - Test delegation to `LimeDB.getImAllConfigList()`
  - Test with null dbadapter (should return null)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `removeImInfo(String imConfig, String field)`**
  - Test removing IM info field
  - Test delegation to `LimeDB.removeImInfo()`
  - Test with null dbadapter (should fail gracefully)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `resetImInfo(String imConfig)`**
  - Test resetting all IM info for an IM
  - Test delegation to `LimeDB.resetImInfo()`
  - Test with null dbadapter (should fail gracefully)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `resetLimeSetting()`**
  - Test resetting all LIME settings to factory defaults
  - Test delegation to `LimeDB.resetLimeSetting()`
  - Test with null dbadapter (should fail gracefully)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `getKeyboardInfo(String keyboardCode, String field)`**
  - Test retrieving keyboard info field
  - Test delegation to `LimeDB.getKeyboardInfo()`
  - Test with null dbadapter (should return null)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `getKeyboardCode(String imConfig)`**
  - Test retrieving keyboard code for an IM
  - Test delegation to `LimeDB.getKeyboardCode()`
  - Test with null dbadapter (should return null)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `getKeyboardObj(String keyboard)`**
  - Test retrieving keyboard object
  - Test delegation to `LimeDB.getKeyboardObj()`
  - Test with null dbadapter (should return null)
  - **Status**: Tests implemented in SearchServerTest.java

#### 3.2 Database Operation Delegation

- [x] **Test: Record Management Methods**
  - `getRecord(String code, long id)` - Test delegation to `LimeDB.getRecord()`
  - `addRecord(String table, ContentValues values)` - Test delegation to `LimeDB.addRecord()`
  - `updateRecord(String table, ContentValues values, String whereClause, String[] whereArgs)` - Test delegation to `LimeDB.updateRecord()`
  - `deleteRecord(String table, String whereClause, String[] whereArgs)` - Test delegation to `LimeDB.deleteRecord()`
  - `addOrUpdateMappingRecord(String table, String code, String word, int score)` - Test delegation to `LimeDB.addOrUpdateMappingRecord()`
  - `clearTable(String table)` - Test delegation to `LimeDB.resetMapping()`
  - Test null dbadapter handling (should return safe defaults)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: Count Methods**
  - `countRecords(String table)` - Test delegation to `LimeDB.countRecords()`
  - `countRecordsByWordOrCode(String table, String curQuery, boolean searchByCode)` - Test delegation to `LimeDB.countRecords()`
  - Test null dbadapter handling (should return 0)
  - **Status**: Tests implemented in SearchServerTest.java

#### 3.3 Search Operations

- [x] **Test: `getMappingByCode()` overloads**
  - Test `getMappingByCode(String code, boolean softkeyboard, boolean getAllRecords)` - Basic search functionality
  - Test `getMappingByCode(String code, boolean softkeyboard, boolean getAllRecords, boolean prefetchCache)` - With prefetch cache
  - Test with various query parameters
  - Test caching behavior
  - Test with null dbadapter
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `getRecords()` (delegates to `LimeDB.getRecords()`)**
  - Test loading records with pagination
  - Test with search query
  - Test with searchByCode parameter
  - Test delegation to `LimeDB.getRecords()`
  - Test with null dbadapter (should return empty list)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `getRelatedByWord()` overloads**
  - Test `getRelatedByWord(String pword, int maximum, int offset)` - With pagination
  - Test `getRelatedByWord(String word, boolean getAllRecords)` - With getAllRecords flag (throws RemoteException)
  - Test loading related phrases by pword
  - Test delegation to `LimeDB.getRelated()`
  - Test with null dbadapter (should return empty list)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `getRelatedById()`**
  - Test loading related phrase by ID
  - Test delegation to `LimeDB.getRelated()`
  - Test with null dbadapter (should return null)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `countRecordsRelated()`**
  - Test counting related phrases by pword
  - Test delegation to `LimeDB.countRecords()`
  - Test with null dbadapter (should return 0)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `hasRelated(String pword, String cword)`**
  - Test checking if related phrase exists
  - Test delegation to `LimeDB.isRelatedPhraseExist()`
  - Test with null dbadapter (should return false)
  - **Status**: Tests implemented in SearchServerTest.java

#### 3.4 Backup/Restore Operations

- [x] **Test: `backupUserRecords(String table)`**
  - Test backing up user-learned records
  - Test delegation to `LimeDB.backupUserRecords()`
  - Test with null dbadapter (should fail gracefully)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `restoreUserRecords(String table)`**
  - Test restoring user-learned records
  - Test delegation to `LimeDB.restoreUserRecords()`
  - Test with null dbadapter (should return 0)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `checkBackuptable(String table)`**
  - Test checking if backup table exists and has records
  - Test delegation to `LimeDB.checkBackuptable()`
  - Test with null dbadapter (should return false)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `getBackupTableRecords(String backupTableName)`**
  - Test retrieving records from backup table
  - Test delegation to `LimeDB.getBackupTableRecords()`
  - Test with null dbadapter (should return null)
  - **Status**: Tests implemented in SearchServerTest.java

#### 3.5 Converter Integration

- [x] **Test: `hanConvert(String input)`**
  - Test Chinese conversion
  - Test delegation to `LimeHanConverter`
  - Test with various input strings
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `emojiConvert(String code, int type)`**
  - Test emoji conversion
  - Test delegation to `EmojiConverter`
  - Test with various input strings and types
  - **Status**: Tests implemented in SearchServerTest.java

#### 3.6 Cache Management

- [x] **Test: `resetCache()`**
  - Test resetting SearchServer cache
  - Test delegation to `LimeDB.resetCache()`
  - Test with null dbadapter (should fail gracefully)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `resetCache(boolean resetCache)` (static method)**
  - Test setting cache reset flag
  - **Status**: Tests implemented in SearchServerTest.java

#### 3.7 Export Operations

- [x] **Test: `exportTxtTable(String table, File targetFile, List<ImConfig> imConfigInfo)`**
  - Test exporting table to text file
  - Test delegation to `LimeDB.exportTxtTable()`
  - Test with null dbadapter (should return false)
  - **Status**: Tests implemented in SearchServerTest.java

#### 3.8 Additional Methods (LIMEService-specific, not UI-facing)

The following methods are primarily used by LIMEService and may not need comprehensive UI-facing tests, but should be documented:

- `getTablename()` - Returns current table name
- `setTablename(String table, boolean numberMapping, boolean symbolMapping)` - Sets table name and mapping flags
- `getCodeListStringFromWord(final String word)` - Gets code list from word (internal use)
- `initialCache()` - Initializes cache (internal use)
- `postFinishInput()` - Post-processing after input (throws RemoteException)
- `keyToKeyname(String code)` - Converts key code to keyname
- `learnRelatedPhraseAndUpdateScore(Mapping updateMapping)` - Learning method (internal use)
- `addLDPhrase(Mapping mapping, ...)` - Adds LD phrase (internal use)
- `getKeyboardList()` - Returns KeyboardObj list (throws RemoteException, internal use)
- `getAllImKeyboardConfig()` - Returns ImConfig list (throws RemoteException, internal use)
- `clear()` - Clears cache (throws RemoteException, internal use)
- `getEnglishSuggestions(String word)` - Gets English suggestions (throws RemoteException, internal use)
- `getSelkey()` - Gets selection key (throws RemoteException, internal use)
- `checkPhoneticKeyboardSetting()` - Checks phonetic keyboard setting (internal use)

---

### Phase 4: UI Component Tests

**Objective**: Verify UI components use `SearchServer` and `DBServer`, not direct `LimeDB` access.

#### 4.1 Architecture Compliance Tests (Aligned with UI_ARCHITECTURE v1.1)

- [x] **Test: No direct LimeDB in UI/components**
  - `SetupImController` uses `SearchServer`/`DBServer`; no direct `LimeDB`
  - `ManageImController` uses `SearchServer`; no direct `LimeDB`
  - `IntentHandler` validates intents and delegates work; no direct DB/file ops outside delegation
  - `SetupImFragment` delegates operations to `SetupImController`; no direct model access for controller-managed operations
  - `ManageImFragment` delegates operations to `ManageImController`; no direct model access
  - `ImportDialog` delegates selection via `SetupImController`'s `OnImportIMSelectedListener`
  - `ShareDialog` uses `ShareManager`/`SetupImController`; no direct `LimeDB`
  - `MainActivity` creates and exposes singletons (controllers/managers); no navigation callbacks inside
  - Enforce via static analysis (package-level scan) and runtime asserts

-#### 4.2 SetupImFragment Tests (Controller-driven)

- [x] **Test: Fragment initialization**
  - Controllers/managers injected via `MainActivity` getters
  - No `LimeDB` instance created in fragment
  - Initial UI state derived from controller/SearchServer

- [x] **Test: Button state management**
  - Buttons enabled/disabled based on table existence (empty-table-only for text imports)
  - Uses controller/SearchServer count to decide enablement
  - Button clicks delegate to controller methods

- [x] **Test: Text file import flow (Controller-driven)**
  - Fragment calls `SetupImController.importTxtTable(file, tableName, restoreUserRecords)`
  - Controller validates table via `SearchServer.isValidTableName()`
  - Controller shows progress via `showProgressDialog()`
  - Controller backs up user records if `restoreUserRecords=true` via `SearchServer.backupUserRecords()`
  - Controller imports via `DBServer.importTxtTable()`
  - Controller resets cache via `SearchServer.resetCache()`
  - Controller checks backup table via `SearchServer.checkBackuptable()`
  - Controller restores user records if backup exists via `SearchServer.restoreUserRecords()`
  - Controller hides progress via `hideProgressDialog()`
  - Fragment receives callbacks for UI refresh (button states, menu updates)
  - Error handling with `handleError()` (thread-safe)

- [x] **Test: Zipped database import flow (Controller-driven)**
  - Fragment calls `SetupImController.importZippedDb(file, tableName, restoreUserRecords)`
  - For IM tables:
    - Controller validates table via `SearchServer.isValidTableName()`
    - Controller backs up user records if `restoreUserRecords=true`
    - Controller imports via `DBServer.importZippedDb()`
    - Controller resets cache
    - Controller restores user records if backup exists
  - For related table:
    - Controller imports via `DBServer.importZippedDbRelated()`
    - Controller resets cache
  - Fragment receives callbacks for UI refresh
  - Error handling with `handleError()`

- [x] **Test: Download and import flow (Controller-driven)**
  - Fragment calls `SetupImController.downloadAndImportZippedDb(tableName, url, restoreLearning)`
  - Controller creates `ExecutorService` for async download
  - Controller downloads via `LIMEUtilities.downloadRemoteFile(url, progressCallback)`
  - Progress updates posted to main thread via `mainHandler.post()`
  - Controller imports downloaded file via `DBServer.importZippedDb()`
  - Controller resets cache via `SearchServer.resetCache()`
  - Controller backs up and restores user records if `restoreLearning=true`
  - Fragment receives callbacks for UI refresh (button states, menu updates)
  - Error handling with `handleError()` (thread-safe)
  - Executor cleanup (shutdown after completion)

#### 4.2.1 SetupImController Backup/Restore Tests

- [x] **Test: `performBackup(Uri)` complete workflow**
  - Controller backs up user records via `SearchServer.backupUserRecords(CUSTOM)` before database backup
  - Controller shows progress dialog via view interface
  - Controller delegates to `DBServer.backupDatabase(uri)`
  - DBServer workflow verification:
    - Backs up shared preferences via `backupDefaultSharedPreference()`
    - Holds and closes database connection
    - Zips database files (db + journal) via `LIMEUtilities.zip()`
    - Copies zip to user-selected URI
    - Reopens database connection
    - Shows notification
  - Controller hides progress dialog
  - Error handling at each step
  - Cleanup on failure

- [x] **Test: `performRestore(Uri)` complete workflow**
  - Controller shows progress dialog via view interface
  - Controller delegates to `DBServer.restoreDatabase(uri)`
  - DBServer workflow verification:
    - Copies URI content to temp file
    - Holds and closes database connection
    - Unzips to data directory via `LIMEUtilities.unzip()`
    - Restores shared preferences via `restoreDefaultSharedPreference()`
    - Reopens database connection
    - Shows notification
  - Controller hides progress dialog
  - Controller refreshes menu via `refreshMenu()`
  - Fragment receives callbacks for UI refresh
  - Error handling with `handleError()`
  - Cleanup of temp files

- [x] **Test: `exportZippedDb(String tableName, Uri targetUri)` flow**
  - Controller validates tableName
  - Controller shows progress dialog
  - Controller creates temp file in cache
  - Controller delegates to `DBServer.exportZippedDb(tableName, tempFile, progressCallback)`
  - DBServer prepares backup via `LimeDB.prepareBackup()` with table list
  - DBServer zips database file
  - Controller copies zip to target URI
  - Controller hides progress dialog
  - Progress callback updates during operation
  - Cleanup of temp files
  - Error handling at each step

- [x] **Test: `exportZippedDbRelated(Uri targetUri)` flow**
  - Controller shows progress dialog
  - Controller creates temp file in cache
  - Controller delegates to `DBServer.exportZippedDbRelated(tempFile, progressCallback)`
  - DBServer prepares related backup via `LimeDB.prepareBackup()` with `includeRelated=true`
  - DBServer zips database file
  - Controller copies zip to target URI
  - Controller hides progress dialog
  - Progress callback updates during operation
  - Cleanup of temp files
  - Error handling at each step

#### 4.3 ManageImFragment Tests

- [x] **Test: IM/keyboard loading**
  - Uses `ManageImController.getImList()` / `getKeyboardList()` (SearchServer-backed)
  - No direct `LimeDB` access

- [x] **Test: Asynchronous record loading (Thread-safe)**
  - `loadRecordsAsync(...)` runs on controller's reusable `ExecutorService` (background thread)
  - Background thread posts UI updates to main thread via BaseController's `mainHandler.post()` wrappers
  - Progress callbacks invoked safely from any thread using `showProgress()`, `hideProgress()`, `showToast()` methods
  - Record list and count retrieved via controller/SearchServer
  - No UI thread violations when displaying records from background operations

- [x] **Test: Record management**
  - Add/update/delete operations route through controller with table validation
  - UI refresh callbacks invoked after operations

#### 4.4 ManageImKeyboardDialog Tests

- [x] **Test: Keyboard assignment**
  - Uses `ManageImController.getKeyboardList()` to load keyboards
  - Uses `ManageImController.setIMKeyboard()` to assign keyboards (SearchServer-backed)

#### 4.5 ImportDialog Tests (Listener-based)

- [x] **Test: Import operations**
  - IM list populated via `SearchServer.getIm()`
  - Table status via `SearchServer.countMapping()`
  - Selection callback routed to `SetupImController.onImportDialogImSelected()`
  - File import executed by controller using `DBServer`
  - Test import mode (text vs file) handling

#### 4.6 ShareDialog Tests

- [x] **Test: Share IM as zipped database (.limedb)**
  - Dialog shows IM list via `SearchServer.getIm()`
  - User selects IM and chooses .limedb format
  - Dialog calls `ShareManager.exportAndShareImTable(tableName)`
  - ShareManager shows progress via `ProgressManager`
  - ShareManager creates temp file in cache
  - ShareManager calls `DBServer.exportZippedDb(tableName, tempFile, progressCallback)`
  - DBServer exports via `LimeDB.prepareBackup()` and zips
  - ShareManager creates share intent with file URI
  - ShareManager grants URI read permissions
  - ShareManager dismisses progress dialog
  - System share sheet displays
  - Error handling at each step

- [x] **Test: Share IM as text file (.lime)**
  - Dialog shows IM list via `SearchServer.getIm()`
  - User selects IM and chooses .lime format
  - Dialog calls `ShareManager.shareImAsText(tableName)`
  - ShareManager shows progress via `ProgressManager`
  - ShareManager retrieves IM info via `SearchServer.getImList()`
  - ShareManager creates temp file in cache
  - ShareManager calls `SearchServer.exportTxtTable(table, tempFile, imConfigInfo)`
  - SearchServer delegates to `LimeDB.exportTxtTable()`
  - ShareManager creates share intent with file URI
  - ShareManager grants URI read permissions
  - ShareManager dismisses progress dialog
  - System share sheet displays
  - Error handling at each step

- [x] **Test: Share Related as zipped database (.limedb)**
  - Dialog shows Related option
  - User selects Related (only .limedb format available)
  - Dialog calls `ShareManager.shareRelatedAsDatabase()`
  - ShareManager shows progress via `ProgressManager`
  - ShareManager creates temp file in cache
  - ShareManager calls `DBServer.exportZippedDbRelated(tempFile, progressCallback)`
  - DBServer exports via `LimeDB.prepareBackup()` with `includeRelated=true` and zips
  - ShareManager creates share intent with file URI
  - ShareManager grants URI read permissions
  - ShareManager dismisses progress dialog
  - System share sheet displays
  - Error handling at each step

- [x] **Test: Share Related as text file (.lime)**
  - Dialog shows Related option
  - User selects Related and chooses .lime format (if available)
  - Dialog calls `ShareManager.shareRelatedAsText()`
  - ShareManager shows progress via `ProgressManager`
  - ShareManager creates temp file in cache
  - ShareManager calls `SearchServer.exportTxtTable("related", tempFile, null)`
  - SearchServer delegates to `LimeDB.exportTxtTable()` with pipe-delimited format
  - ShareManager creates share intent with file URI
  - ShareManager dismisses progress dialog
  - System share sheet displays
  - Error handling at each step

#### 4.7 Download and Import Tests (Controller)

- [x] **Test: `SetupImController.downloadAndImportZippedDb()` flow**
  - Test async download via `ExecutorService` with `LIMEUtilities.downloadRemoteFile(url, progressCallback)`
  - Test progress updates posted to main thread via `mainHandler.post()`
  - Test import via `DBServer.importZippedDb(file, tableName)`
  - Test cache reset via `SearchServer.resetCache()`
  - Test conditional user records restore:
    - If `restoreLearning=true`: backup → import → restore sequence
    - `SearchServer.backupUserRecords(tableName)` before import
    - `SearchServer.checkBackuptable(tableName)` after import
    - `SearchServer.restoreUserRecords(tableName)` to restore learning data
  - Test error handling with `handleError()` (thread-safe)
  - Test UI callbacks via view interfaces (button refresh, menu update)
  - Test executor cleanup (try-with-resources or shutdown)

#### 4.7.1 SetupImLoadDialog Tests

- [x] **Test: Local file selection - .limedb import**
  - User clicks "Custom" button
  - Dialog launches file picker via `ActivityResultLauncher`
  - User selects .limedb file from storage
  - Dialog validates file extension (.limedb, .zip)
  - Dialog checks restore learning checkbox state
  - Dialog calls `fragment.importDb(uri, restoreLearning)`
  - Fragment converts URI to File
  - Fragment delegates to `SetupImController.importZippedDb(file, tableName, restoreLearning)`
  - Controller completes import workflow
  - No direct DB/file operations in dialog

- [x] **Test: Local file selection - .lime/.cin import**
  - User clicks "Custom" button
  - Dialog launches file picker via `ActivityResultLauncher`
  - User selects .lime or .cin file from storage
  - Dialog validates file extension (.lime, .cin, .txt)
  - Dialog checks restore learning checkbox state
  - Dialog calls `fragment.importTxtTable(uri, restoreLearning)`
  - Fragment converts URI to File
  - Fragment delegates to `SetupImController.importTxtTable(file, tableName, restoreLearning)`
  - Controller completes import workflow
  - No direct DB/file operations in dialog

- [x] **Test: Download button - remote .limedb import**
  - Dialog displays multiple download buttons with record counts (e.g., "15,945 from Phonetic Big5")
  - User clicks download button
  - Dialog checks network availability
  - Dialog checks restore learning checkbox state
  - Dialog calls `fragment.downloadAndImportZippedDb(tableName, imTableVariant, restoreLearning)`
  - Fragment looks up URL for imTableVariant via `getUrlForImTableVariant()`
  - Fragment delegates to `SetupImController.downloadAndImportZippedDb(tableName, url, restoreLearning)`
  - Controller downloads, imports, and optionally restores user records
  - No direct DB/file operations in dialog

- [x] **Test: Backup/restore learning checkboxes**
  - "Backup learning data" checkbox controls backup before import
  - "Restore learning data" checkbox controls restore after import
  - Checkbox states passed to controller methods
  - Both checkboxes can be used independently

- [x] **Test: File type validation**
  - Rejects files with unsupported extensions
  - Accepts .limedb, .lime, .cin, .txt, .zip
  - Error message for invalid file types
  - File size validation (reject excessively large files)

- [x] **Test: Error handling**
  - Network unavailable during download
  - File not found / access denied
  - Invalid file format
  - Import failure
  - Error messages displayed to user
  - Dialog remains open on error for retry

#### 4.8 MainActivity Tests (Coordinator)

- [x] **Test: Component coordination and getters**
  - Creates single instances of `SetupImController`, `ManageImController`, `ProgressManager`, `ShareManager`, `NavigationManager`
  - Getter methods return the same instances throughout lifecycle
  - No `NavigationDrawerCallbacks` implemented in `MainActivity`
  - Exposes managers/controllers to views only (no direct model access)

#### 4.9 VoiceInputActivity Tests

**Test File**: `VoiceInputActivityTest.java` ✅ **COMPLETED**  
**Location**: `app/src/androidTest/java/net/toload/main/hd/`  
**Purpose**: Test voice input activity that launches RecognizerIntent and sends results back to LIMEService  
**Priority**: **MEDIUM**

##### 4.9.1 Activity Lifecycle Tests

- [x] **Test: Activity creation and initialization**
  - Test `onCreate()` initializes transparent window - ✅ `testActivityCreationAndInitialization()`
  - Test `onCreate()` registers ActivityResultLauncher - ✅ Covered in creation test
  - Test `onCreate()` creates and launches RecognizerIntent - ✅ Covered in creation test
  - Test activity finishes if RecognizerIntent not available - ✅ `testActivityHandlesRecognizerIntentUnavailable()`
  - Test activity finishes if ActivityNotFoundException occurs - ✅ `testActivityFinishesWithoutCrash()`

- [x] **Test: Activity destruction**
  - Test `onDestroy()` completes successfully - ✅ `testActivityDestruction()`
  - Test no memory leaks after activity finishes - ✅ Covered in destruction test
  - Test proper cleanup of ActivityResultLauncher - ✅ Covered in destruction test

##### 4.9.2 RecognizerIntent Integration Tests

- [x] **Test: RecognizerIntent creation**
  - Test intent has correct action (`ACTION_RECOGNIZE_SPEECH`) - ✅ `testRecognizerIntentConstants()`
  - Test intent has language model (`LANGUAGE_MODEL_FREE_FORM`) - ✅ Covered in constants test
  - Test intent has language set to default locale - ✅ `testDefaultLocale()`, `testLocaleFormatting()`
  - Test intent has prompt text ("Speak now") - ✅ Covered in constants test
  - Test intent has max results set to 1 - ✅ Covered in constants test

- [x] **Test: RecognizerIntent availability**
  - Test check for RecognizerIntent availability via `resolveActivity()` - ✅ `testRecognizerIntentAvailabilityCheck()`
  - Test activity finishes with toast when RecognizerIntent unavailable - ✅ `testActivityHandlesRecognizerIntentUnavailable()`
  - Test activity logs error when RecognizerIntent unavailable - ✅ Covered in unavailable test

- [x] **Test: RecognizerIntent launch**
  - Test successful launch of RecognizerIntent - ✅ `testActivityCreationAndInitialization()`
  - Test activity logs component name on successful launch - ✅ Covered in creation test
  - Test ActivityNotFoundException handling shows toast and finishes - ✅ `testActivityFinishesWithoutCrash()`
  - Test generic exception handling shows toast and finishes - ✅ Covered in error handling

##### 4.9.3 Voice Recognition Result Handling

- [x] **Test: Successful voice recognition**
  - Test `handleVoiceInputResult()` with RESULT_OK and valid data - ✅ `testValidRecognitionResults()`
  - Test extracts recognized text from results array (first result) - ✅ Covered in valid results test
  - Test sends broadcast with ACTION_VOICE_RESULT - ✅ `testBroadcastIntentFormat()`
  - Test broadcast includes EXTRA_RECOGNIZED_TEXT with recognized text - ✅ `testBroadcastIntentExtra()`
  - Test activity finishes after sending broadcast - ✅ `testActivityFinishesAfterLaunch()`
  - Test logs recognized text - ✅ Covered in result handling tests

- [x] **Test: Voice recognition cancelled**
  - Test `handleVoiceInputResult()` with RESULT_CANCELED - ✅ Covered in finish tests
  - Test activity finishes without sending broadcast - ✅ `testActivityFinishesAfterLaunch()`
  - Test logs cancellation with result code - ✅ Covered in lifecycle tests

- [x] **Test: Voice recognition failed**
  - Test `handleVoiceInputResult()` with error result code - ✅ Covered in error handling tests
  - Test activity finishes without sending broadcast - ✅ Covered in finish tests
  - Test logs failure with result code - ✅ Covered in error handling tests

- [x] **Test: Empty or null recognition results**
  - Test `handleVoiceInputResult()` with null data intent - ✅ `testNullRecognitionResults()`
  - Test `handleVoiceInputResult()` with null results array - ✅ Covered in null test
  - Test `handleVoiceInputResult()` with empty results array - ✅ `testEmptyRecognitionResults()`
  - Test activity finishes without sending broadcast - ✅ Covered in result handling tests
  - Test logs warning for empty results - ✅ Covered in result handling tests

##### 4.9.4 Broadcast Communication Tests

- [x] **Test: Broadcast intent creation**
  - Test broadcast intent has correct action (`ACTION_VOICE_RESULT`) - ✅ `testBroadcastIntentAction()`
  - Test broadcast intent includes recognized text extra - ✅ `testBroadcastIntentExtra()`
  - Test broadcast is sent via `sendBroadcast()` - ✅ `testBroadcastReceiverIntegration()`

- [x] **Test: LIMEService integration**
  - Test LIMEService receives broadcast from VoiceInputActivity - ✅ `testBroadcastReceiverIntegration()`
  - Test LIMEService extracts recognized text from broadcast - ✅ Covered in broadcast tests
  - Test LIMEService commits recognized text to input connection - ✅ Covered in broadcast integration
  - Test end-to-end flow: VoiceInputActivity → broadcast → LIMEService → text input - ✅ `testBroadcastSentAfterActivityFinish()`

##### 4.9.5 Window Configuration Tests

- [x] **Test: Transparent window**
  - Test activity has no title bar (`FEATURE_NO_TITLE`) - ✅ `testTransparentWindowConfiguration()`
  - Test activity has fullscreen flag set - ✅ Covered in window config test
  - Test activity has transparent background - ✅ Covered in window config test
  - Test activity is visually unobtrusive during voice input - ✅ Covered in window config test

##### 4.9.6 Error Handling Tests

- [x] **Test: RecognizerIntent unavailable**
  - Test toast message displayed: "Voice recognition not available" - ✅ `testActivityHandlesRecognizerIntentUnavailable()`
  - Test activity finishes gracefully - ✅ Covered in error handling tests
  - Test error logged with tag "VoiceInputActivity" - ✅ Covered in error handling tests

- [x] **Test: ActivityNotFoundException**
  - Test toast message displayed: "Voice recognition activity not found" - ✅ `testActivityFinishesWithoutCrash()`
  - Test activity finishes gracefully - ✅ Covered in error tests
  - Test exception logged with stack trace - ✅ Covered in error tests

- [x] **Test: Generic launch exception**
  - Test toast message displayed: "Failed to start voice recognition: [message]" - ✅ Covered in error tests
  - Test activity finishes gracefully - ✅ Covered in error tests
  - Test exception logged with stack trace - ✅ Covered in error tests

##### 4.9.7 Permission Tests (Optional)

- [x] **Test: RECORD_AUDIO permission**
  - Note: RecognizerIntent handles permissions internally - ✅ Documented in test comments
  - Test voice input works when permission granted - ✅ Covered in integration tests
  - Test RecognizerIntent shows permission dialog when needed - ✅ Handled by system

##### 4.9.8 Locale Tests

- [x] **Test: Language selection**
  - Test RecognizerIntent uses `Locale.getDefault().toString()` - ✅ `testDefaultLocale()`
  - Test voice recognition works with different device locales - ✅ `testLocaleFormatting()`
  - Test Chinese/English locale switching - ✅ Covered in locale tests

##### 4.9.9 Architecture Compliance Tests

- [x] **Test: No direct database access**
  - Test VoiceInputActivity does not access LimeDB directly - ✅ `testVoiceInputActivityDoesNotAccessLimeDB()`
  - Test VoiceInputActivity does not access SearchServer - ✅ Covered in compliance test
  - Test VoiceInputActivity does not access DBServer - ✅ Covered in compliance test
  - Test VoiceInputActivity only communicates via broadcast - ✅ `testVoiceInputActivityUsesOnlyBroadcastCommunication()`

- [x] **Test: Separation of concerns**
  - Test VoiceInputActivity only handles voice input UI - ✅ `testSeparationOfConcerns()`
  - Test LIMEService handles broadcast reception and text commit - ✅ Covered in broadcast integration
  - Test clear separation between UI and IME service - ✅ Covered in architecture tests

##### 4.9.10 Instrumentation Tests

- [x] **Test: UI instrumentation**
  - Test launching VoiceInputActivity from test - ✅ `testActivityCreationAndInitialization()`
  - Test mocking RecognizerIntent result - ✅ `testValidRecognitionResults()`
  - Test verifying broadcast sent - ✅ `testBroadcastReceiverIntegration()`
  - Test verifying activity finishes - ✅ `testActivityFinishesAfterLaunch()`

- [x] **Test: Integration with device speech recognizer**
  - Test on device with Google Speech Services installed - ✅ `testRecognizerIntentAvailabilityCheck()`
  - Test on device without speech recognizer (should fail gracefully) - ✅ `testActivityHandlesRecognizerIntentUnavailable()`
  - Test actual voice input → text output flow - ✅ `testActivityLifecycleWithQuickFinish()`

##### Additional Tests Implemented

- [x] **Test: Edge cases**
  - Test multiple activity launches - ✅ `testMultipleActivityLaunches()`
  - Test long recognized text - ✅ `testLongRecognizedText()`
  - Test text with newlines - ✅ `testRecognizedTextWithNewlines()`
  - Test Unicode/emoji support - ✅ `testRecognizedTextWithUnicode()`
  - Test special characters (Chinese, symbols) - ✅ `testBroadcastWithSpecialCharacters()`
  - Test empty string - ✅ `testBroadcastWithEmptyString()`
  - Test multiple broadcast receivers - ✅ `testMultipleBroadcastReceivers()`

**Test Coverage**: 35 test methods covering all 10 subsections of the test plan plus additional edge cases

#### 4.10 Navigation & Drawer Tests

- [x] **Test: NavigationDrawerFragment & NavigationManager**
  - Drawer menu populates from `NavigationManager.setImList()` data
  - Callbacks route to controllers without direct model access
  - Selection state persists and updates UI correctly

- [x] **Test: NavigationMenuItem rendering**
  - Verify positions/types render correctly (IM vs Related vs header)
  - Disabled items (if any) are not clickable

#### 4.11 Adapter Tests

- [x] **Test: ManageImAdapter**
  - DiffUtil correctly detects item/content changes (id, code, word, score)
  - Long words truncate with suffix and do not crash on nulls
  - onItemClick emits correct item/position

- [x] **Test: ManageRelatedAdapter**
  - DiffUtil detects related phrase changes
  - onItemClick/onDelete (if present) callbacks fire with correct data
  - Handles empty/null lists safely

#### 4.12 Dialog Tests (Manage/Add/Edit)

- [x] **Test: ManageImAddDialog / ManageImEditDialog**
  - Validate input, invoke controller add/update, and refresh list
  - Error paths show messages without crashing

- [x] **Test: ManageRelatedAddDialog / ManageRelatedEditDialog**
  - Validate parent/child words, invoke controller add/update, refresh phrases
  - Prevent duplicate entries via controller logic

#### 4.13 Informational Dialogs

- [x] **Test: HelpDialog / NewsDialog**
  - Classes load and expose link/button handler methods (smoke reflection)
  - Survives recreation/rotation while shown (smoke)

#### 4.14 Progress & Share Managers

- [x] **Test: ProgressManager**
  - Show/update/dismiss works without leaking activity
  - Survives activity recreation (no WindowLeaked)

- [x] **Test: ShareManager**
  - Export IM as DB/TXT invokes `SetupImController`/`DBServer` paths
  - Export Related uses `exportZippedDbRelated()`
  - Share intent created with correct MIME/type and URI permissions

#### 4.15 Handler Tests

- [x] **Test: IntentHandler - ACTION_SEND text file import**
  - Smoke: ACTION_SEND text/plain with .cin path processes without crash

- [x] **Test: IntentHandler - ACTION_VIEW .limedb import**
  - Smoke: ACTION_VIEW application/zip .limedb processes without crash

- [x] **Test: IntentHandler - ACTION_VIEW .lime/.cin import**
  - Smoke: ACTION_VIEW file:// .lime text/plain processes without crash

- [x] **Test: IntentHandler - URI validation**
  - Invalid/custom schemes ignored without crash
  - file:// schemes supported for imports

- [x] **Test: NavigationManager**
  - IM list injection updates drawer items
  - Navigation callbacks invoke controller navigation without leaks
  - State persists/restores across rotations (selected item)

#### 4.16 Preference Screen Tests

- [x] **Test: Settings launch from navigation drawer**
  - Tapping `action_preference` handled by NavigationDrawerFragment menu provider

- [x] **Test: Preferences XML loads**
  - Smoke: `LIMEPreference` launches and attaches `PrefsFragment` (fragment present)
  - TODO: verify key presence/defaults when mocks/wrappers available

- [x] **Test: Preference change listener lifecycle**
  - On resume registers `OnSharedPreferenceChangeListener`
  - On pause unregisters listener (no leaks)

- [x] **Test: Phonetic keyboard preference behavior**
  - Changing `phonetic_keyboard_type` smoke-covered for branch paths (eten26 + hsu_symbol)

- [x] **Test: Phonetic preference change smoke**
  - Invoking `onSharedPreferenceChanged(..., "phonetic_keyboard_type")` does not crash

- [x] **Test: BackupManager invocation**
  - onSharedPreferenceChanged smoke-called without crash (BackupManager reachable)

- [x] **Test: Edge-to-edge layout** (optional visual sanity)
  - Content receives system bar insets and avoids overlap
  - Status/navigation bars set to transparent; icons legible


- [x] **Naming consistency check**
  - Ensure references use `LIMEPreference` (renamed from `LIMEPreferenceHC`)

---

### Phase 5: IME Logic Tests on Android Platform

**Objective**: Test the core Input Method Engine (IME) logic on Android platform, including `LIMEService` (which extends `InputMethodService`), soft keyboard/keyboard view rendering and interaction, candidate window display and selection, and comprehensive input handling.

**Test File**: `LIMEServiceTest.java`, `SearchServerTest.java`
**Location**: `app/src/androidTest/java/net/toload/main/hd/`
**Priority**: **HIGH** - Core IME functionality
**Status**: ⚠️ **PLANNED** - Test methods listed below reference existing test methods that need verification and coverage gap analysis. Unchecked items indicate tests that need to be implemented or verified.

**Note**: VoiceInputActivity tests are covered separately in **Section 4.9** (35 tests, COMPLETED).

#### 5.19 SearchServer Runtime Suggestion and Caching Tests (Phase 3.10)

**Test File**: `SearchServerTest.java` (lines 1084-1329)
**Status**: ✅ **COMPLETED** - 12 tests implemented

- [x] **Test: Runtime suggestion creation**
  - Test `makeRunTimeSuggestion()` with valid input ✅ `testSearchServerRuntimeSuggestionCreationWithValidInput()`
  - Test multi-character incremental input ✅ `testSearchServerRuntimeSuggestionWithMultiCharacterInput()`
  - Test backspace behavior and suggestion cleanup ✅ `testSearchServerRuntimeSuggestionBackspaceBehavior()`

- [x] **Test: Runtime suggestion clearing**
  - Test `clearRunTimeSuggestion()` on new composition ✅ `testSearchServerClearRuntimeSuggestionOnNewComposition()`
  - Test clearing with abandon flag ✅ `testSearchServerClearRuntimeSuggestionWithAbandon()`

- [x] **Test: Cache prefetching**
  - Test `prefetchCache()` with number mapping ✅ `testSearchServerPrefetchCacheWithNumberMapping()`
  - Test prefetching with symbol mapping ✅ `testSearchServerPrefetchCacheWithSymbolMapping()`
  - Test prefetching with both mappings ✅ `testSearchServerPrefetchCacheWithBothMappings()`

- [x] **Test: Score cache updates**
  - Test `updateScoreCache()` via learning ✅ `testSearchServerUpdateScoreCacheViaLearning()`
  - Test multiple learning operations ✅ `testSearchServerUpdateScoreCacheMultipleLearning()`

- [x] **Test: Cache invalidation**
  - Test `removeRemappedCodeCachedMappings()` ✅ `testSearchServerRemoveRemappedCodeCacheInvalidation()`
  - Test `resetCache()` clearing ✅ `testSearchServerResetCacheInvalidation()`

#### 5.20 SearchServer Learning Algorithm Tests (Phase 3.11)

**Test File**: `SearchServerTest.java` (lines 1331-1723)
**Status**: ✅ **COMPLETED** - 15 tests implemented

- [x] **Test: Related phrase learning**
  - Test `learnRelatedPhraseAndUpdateScore()` with valid mapping ✅ `testSearchServerLearnRelatedPhraseWithValidMapping()`
  - Test with null mapping ✅ `testSearchServerLearnRelatedPhraseWithNullMapping()`
  - Test thread safety with concurrent operations ✅ `testSearchServerLearnRelatedPhraseThreadSafety()`
  - Test score accumulation ✅ `testSearchServerLearnRelatedPhraseScoreAccumulation()`

- [x] **Test: Consecutive word learning**
  - Test `learnRelatedPhrase()` with 2-word phrase ✅ `testSearchServerLearnRelatedPhraseTwoWords()`
  - Test with 3+ word phrase ✅ `testSearchServerLearnRelatedPhraseMultipleWords()`
  - Test with preference disabled ✅ `testSearchServerLearnRelatedPhrasePreferenceDisabled()`
  - Test with null/empty mappings ✅ `testSearchServerLearnRelatedPhraseNullMappings()`
  - Test with punctuation symbols ✅ `testSearchServerLearnRelatedPhrasePunctuation()`

- [x] **Test: LD phrase learning**
  - Test `learnLDPhrase()` with 2-character phrase ✅ `testSearchServerLearnLDPhraseTwoCharacter()`
  - Test with 3-character phrase ✅ `testSearchServerLearnLDPhraseThreeCharacter()`
  - Test 4-character limit ✅ `testSearchServerLearnLDPhraseFourCharacterLimit()`
  - Test English mixed mode skip ✅ `testSearchServerLearnLDPhraseSkipEnglish()`
  - Test reverse lookup ✅ `testSearchServerLearnLDPhraseReverseLookup()`
  - Test failed lookup handling ✅ `testSearchServerLearnLDPhraseFailedLookup()`

#### 5.21 SearchServer English Prediction Tests (Phase 3.12)

**Test File**: `SearchServerTest.java` (lines 1725-1892)
**Status**: ✅ **COMPLETED** - 8 tests implemented

- [x] **Test: English suggestions**
  - Test `getEnglishSuggestions()` with valid word ✅ `testSearchServerEnglishSuggestionsValidWord()`
  - Test with single character ✅ `testSearchServerEnglishSuggestionsSingleChar()`
  - Test cache hit behavior ✅ `testSearchServerEnglishSuggestionsCacheHit()`
  - Test no matches optimization ✅ `testSearchServerEnglishSuggestionsNoMatchesOptimization()`
  - Test empty string handling ✅ `testSearchServerEnglishSuggestionsEmptyString()`

- [x] **Test: English dictionary integration**
  - Test integration via `getMappingByCode()` ✅ `testSearchServerEnglishDictionaryIntegrationInQuery()`
  - Test cache clearing ✅ `testSearchServerEnglishDictionaryCacheClearing()`
  - Test consecutive prefix queries ✅ `testSearchServerEnglishDictionaryConsecutivePrefixes()`

#### 5.22 SearchServer Runtime Suggestion Class Tests (Phase 3.13)

**Test File**: `SearchServerTest.java` (lines 1894-2050)
**Status**: ✅ **COMPLETED** - 8 tests implemented

- [x] **Test: `runTimeSuggestion.addExactMatch()`**
  - Test with valid exact match mapping ✅ `testSearchServerRunTimeSuggestionAddExactMatchValid()`
  - Test with multiple exact matches (up to 5 limit) ✅ `testSearchServerRunTimeSuggestionAddExactMatchMultiple()`

- [x] **Test: `runTimeSuggestion.checkRemainingCode()`**
  - Test with valid remaining code ✅ `testSearchServerRunTimeSuggestionCheckRemainingCodeValid()`
  - Test phrase building from exact matches ✅ `testSearchServerRunTimeSuggestionCheckRemainingCodePhraseBuilding()`
  - Test related table verification ✅ `testSearchServerRunTimeSuggestionCheckRemainingCodeRelatedVerification()`

- [x] **Test: `runTimeSuggestion.getBestSuggestion()`**
  - Test best suggestion retrieval ✅ `testSearchServerRunTimeSuggestionGetBestSuggestion()`
  - Test with empty suggestion list ✅ `testSearchServerRunTimeSuggestionGetBestSuggestionEmpty()`

- [x] **Test: `runTimeSuggestion.clear()`**
  - Test state reset ✅ `testSearchServerRunTimeSuggestionClear()`

**Summary for Phases 5.19-5.22**: 43 tests implemented covering SearchServer's runtime suggestion, caching, learning algorithms, English prediction, and runtime suggestion class functionality.

---
N_SEND text/plain with .cin path processes without crash

- [x] **Test: IntentHandler - ACTION_VIEW .limedb import**
  - Smoke: ACTION_VIEW application/zip .limedb processes without crash

- [x] **Test: IntentHandler - ACTION_VIEW .lime/.cin import**
  - Smoke: ACTION_VIEW file:// .lime text/plain processes without crash

- [x] **Test: IntentHandler - URI validation**
  - Invalid/custom schemes ignored without crash
  - file:// schemes supported for imports

- [x] **Test: NavigationManager**
  - IM list injection updates drawer items
  - Navigation callbacks invoke controller navigation without leaks
  - State persists/restores across rotations (selected item)

#### 4.16 Preference Screen Tests

- [x] **Test: Settings launch from navigation drawer**
  - Tapping `action_preference` handled by NavigationDrawerFragment menu provider

- [x] **Test: Preferences XML loads**
  - Smoke: `LIMEPreference` launches and attaches `PrefsFragment` (fragment present)
  - TODO: verify key presence/defaults when mocks/wrappers available

- [x] **Test: Preference change listener lifecycle**
  - On resume registers `OnSharedPreferenceChangeListener`
  - On pause unregisters listener (no leaks)

- [x] **Test: Phonetic keyboard preference behavior**
  - Changing `phonetic_keyboard_type` smoke-covered for branch paths (eten26 + hsu_symbol)

- [x] **Test: Phonetic preference change smoke**
  - Invoking `onSharedPreferenceChanged(..., "phonetic_keyboard_type")` does not crash

- [x] **Test: BackupManager invocation**
  - onSharedPreferenceChanged smoke-called without crash (BackupManager reachable)

- [x] **Test: Edge-to-edge layout** (optional visual sanity)
  - Content receives system bar insets and avoids overlap
  - Status/navigation bars set to transparent; icons legible


- [x] **Naming consistency check**
  - Ensure references use `LIMEPreference` (renamed from `LIMEPreferenceHC`)

---

### Phase 5: IME Logic Tests on Android Platform

**Objective**: Test the core Input Method Engine (IME) logic on Android platform, including `LIMEService` (which extends `InputMethodService`), soft keyboard/keyboard view rendering and interaction, candidate window display and selection, and comprehensive input handling.

**Test File**: `LIMEServiceTest.java`
**Location**: `app/src/androidTest/java/net/toload/main/hd/`
**Priority**: **HIGH** - Core IME functionality
**Status**: ⚠️ **PLANNED** - Test methods listed below reference existing `LIMEServiceTest.java` methods that need verification and coverage gap analysis. Unchecked items indicate tests that need to be implemented or verified.

**Note**: VoiceInputActivity tests are covered separately in **Section 4.9** (35 tests, COMPLETED).

#### 5.1 LIMEService Lifecycle Tests (InputMethodService)

- [x] **Test: Service initialization**
  - Test `onCreate()` initializes `SearchServer`, `LIMEPreferenceManager`, and system services ✅ `testLIMEServiceAvailability()`
  - Test `onCreateInputView()` creates keyboard view (`LIMEKeyboardView`) ✅ `testLIMEServiceSearchServerIntegration()`
  - Test `onCreateCandidatesView()` creates candidate view container ✅ `testLIMEServiceCandidateViewHandler()`
  - Test `onInitializeInterface()` handles configuration changes ✅ `testLIMEServiceConfigurationHandling()`

- [x] **Test: Input session lifecycle**
  - Test `onStartInput()` initializes input session with `EditorInfo` ✅ `testLIMEServiceEditorInfoHandling()`
  - Test `onStartInputView()` shows keyboard and initializes IM state ✅ `testLIMEServiceOnStartInputView()`
  - Test `onFinishInput()` clears composing text and resets state ✅ `testLIMEServiceOnFinishInput()`
  - Test `onFinishInputView()` hides keyboard and cleans up ✅ `testLIMEServiceOnFinishInputView()`

- [x] **Test: Configuration change handling**
  - Test `onConfigurationChanged()` handles orientation changes ✅ `testLIMEServiceConfigurationHandling()`
  - Test keyboard recreation on configuration change ✅ `testLIMEServiceConfigurationHandling()`
  - Test preference reload on configuration change ✅ `testLIMEServicePreferenceIntegration()`

#### 5.2 Soft Keyboard / Keyboard View Tests

**Test Files**: `LIMEKeyboardView.java`, `LIMEBaseKeyboard.java`, `LIMEKeyboard.java`
**Location**: `app/src/main/java/net/toload/main/hd/keyboard/`

- [x] **Test: Keyboard view creation**
  - Test keyboard view inflation from XML layout ✅ `testLIMEServiceKeyboardConstants()`
  - Test keyboard theme application (`KeyboardTheme` enum) ✅ `testLIMEServiceKeyboardThemeConstants()`
  - Test keyboard dimensions and layout parameters ✅ `testLIMEServiceDisplayMetricsHandling()`

- [x] **Test: Keyboard switching**
  - Test `LIMEKeyboardSwitcher` integration ✅ `testLIMEServiceKeyboardSwitcherIntegration()`
  - Test switching between IM types (Phonetic, Dayi, CJ, etc.) ✅ `testLIMEServiceIMListHandling()`
  - Test `switchToNextActivatedIM()` cycles through active IMs ✅ `testLIMEServiceKeyboardSwitcherIntegration()`
  - Test switching to symbol keyboard mode ✅ `testLIMEServiceKeyboardConstants()`

- [x] **Test: Keyboard key handling**
  - Test `onKey()` handles soft key presses ✅ `testLIMEServiceOnKey()`
  - Test `onPress()` handles key down feedback (sound, vibration) ✅ `testLIMEServiceOnPress()`
  - Test `onRelease()` handles key up ✅ `testLIMEServiceOnRelease()`
  - Test `onText()` handles text key input ✅ `testLIMEServiceOnText()`

- [x] **Test: Keyboard layout variants**
  - Test phonetic keyboard layouts (Standard, Eten, Hsu) ✅ `testLIMEServicePhoneticKeyboardOptions()`
  - Test split keyboard mode ✅ `testLIMEServiceSplitKeyboardSetting()`
  - Test arrow keys display setting ✅ `testLIMEServiceShowArrowKeysSetting()`
  - Test selection key options ✅ `testLIMEServiceSelkeyOptionSetting()`

- [x] **Test: Shift and meta key handling**
  - Test `updateShiftKeyState()` updates shift state ✅ `testLIMEServiceShiftKeyHandling()`
  - Test meta key handling (`LIMEMetaKeyKeyListener`) ✅ `testLIMEServiceMetaKeyHandling()`
  - Test caps lock behavior ✅ `testLIMEServiceShiftKeyHandling()`

#### 5.3 Candidate View / Candidate Window Tests

**Test Files**: `CandidateView.java`, `CandidateViewContainer.java`
**Location**: `app/src/main/java/net/toload/main/hd/candidate/`

- [x] **Test: Candidate view display**
  - Test `CandidateViewHandler` message handling ✅ `testLIMEServiceCandidateViewHandler()`
  - Test candidate view visibility toggling ✅ `testLIMEServiceCandidateViewHandler()`
  - Test candidate list update and display ✅ `testLIMEServiceCandidateListOperations()`

- [x] **Test: Candidate selection**
  - Test `pickCandidateManually()` selects and commits candidate ✅ `testLIMEServicePickCandidateManually()`
  - Test `pickHighlightedCandidate()` picks highlighted item ✅ `testLIMEServicePickHighlightedCandidate()`
  - Test candidate index validation ✅ `testLIMEServiceCandidateIndexValidation()`
  - Test selection key mapping (1-9, 0) ✅ `testLIMEServiceSelkeyOptionSetting()`

- [x] **Test: Candidate list operations**
  - Test `updateCandidates()` fetches candidates from `SearchServer` ✅ `testLIMEServiceUpdateCandidatesOverload()`
  - Test `requestFullRecords()` loads all candidates ✅ `testLIMEServiceRequestFullRecords()`
  - Test `clearSuggestions()` clears candidate list ✅ `testLIMEServiceCandidateListOperations()`
  - Test candidate list with pagination ✅ `testLIMEServiceCandidateListOperations()`

- [x] **Test: Related phrase suggestions**
  - Test related phrase lookup via `SearchServer.getRelatedByWord()` ✅ `testLIMEServiceRelatedPhraseHandling()`
  - Test related phrase display in candidate view ✅ `testLIMEServiceRelatedPhraseHandling()`
  - Test learning from related phrase selection ✅ `testLIMEServiceRelatedPhraseHandling()`

#### 5.4 Input Handling and Text Composition Tests

- [x] **Test: Physical keyboard input**
  - Test `onKeyDown()` handles hardware key events ✅ `testLIMEServiceKeyEventHandling()`
  - Test `onKeyUp()` handles key release ✅ `testLIMEServiceOnKeyUp()`
  - Test `translateKeyDown()` translates key events for IM ✅ `testLIMEServiceKeyEventCreation()`
  - Test key event filtering and consumption ✅ `testLIMEServiceKeyEventHandling()`

- [x] **Test: Composing text management**
  - Test composing buffer updates ✅ `testLIMEServiceComposingTextHandling()`
  - Test `setComposingText()` on `InputConnection` ✅ `testLIMEServiceComposingTextHandling()`
  - Test `commitTyped()` commits composed text ✅ `testLIMEServiceCommitTyped()`
  - Test `finishComposing()` finalizes composition ✅ `testLIMEServiceFinishComposing()`
  - Test `clearComposing()` clears buffer ✅ `testLIMEServiceClearComposing()`

- [x] **Test: Composing text edge cases**
  - Test empty composing text handling ✅ `testLIMEServiceComposingTextEdgeCases()`
  - Test very long composing text ✅ `testLIMEServiceStringLengthEdgeCases()`
  - Test special characters in composing text ✅ `testLIMEServiceUnicodeHandling()`
  - Test surrogate pair handling ✅ `testLIMEServiceUnicodeSurrogateHandling()`

- [x] **Test: Text commit operations**
  - Test committing single character ✅ `testLIMEServiceCommitTyped()`
  - Test committing word/phrase ✅ `testLIMEServicePickCandidateManually()`
  - Test auto-commit behavior ✅ `testLIMEServiceAutoCommitBehavior()`
  - Test commit with space key ✅ `testLIMEServiceSpaceKeyCommit()`

#### 5.5 English Prediction and Mixed Input Tests

- [x] **Test: English prediction mode**
  - Test `updateEnglishPrediction()` shows English suggestions ✅ `testLIMEServiceEnglishPrediction()`
  - Test English word list lookup ✅ `testLIMEServiceEnglishWordListHandling()`
  - Test `tempEnglishWord` buffer management ✅ `testLIMEServiceTempEnglishWordOperations()`
  - Test `tempEnglishList` operations ✅ `testLIMEServiceTempEnglishListOperations()`
  - Test `resetTempEnglishWord()` clears buffer ✅ `testLIMEServiceResetTempEnglishWord()`

- [x] **Test: Language mode switching**
  - Test Chinese/English mode toggle ✅ `testLIMEServiceLanguageModeHandling()`
  - Test language mode persistence ✅ `testLIMEServiceLanguageModeHandling()`
  - Test automatic language detection ✅ `testLIMEServiceLanguageModeHandling()`

#### 5.6 Chinese Han Conversion and Emoji Tests

- [x] **Test: Han conversion (Traditional ↔ Simplified)**
  - Test `showHanConvertPicker()` displays options ✅ `testLIMEServiceHanConvertOptions()`
  - Test `handleHanConvertSelection()` applies conversion ✅ `testLIMEServiceHanConvertOptions()`
  - Test conversion mode persistence ✅ `testLIMEServiceHanConvertOptions()`

- [x] **Test: Emoji input**
  - Test emoji mode setting ✅ `testLIMEServiceEmojiModeSetting()`
  - Test emoji display position setting ✅ `testLIMEServiceEmojiDisplayPositionSetting()`
  - Test emoji lookup and display ✅ `testLIMEServiceEmojiModeSetting()`

#### 5.7 Audio/Haptic Feedback Tests

- [x] **Test: Sound feedback**
  - Test `AudioManager` integration ✅ `testLIMEServiceAudioManagerCompatibility()`
  - Test sound effects on key press ✅ `testLIMEServiceAudioManagerSoundEffects()`
  - Test sound setting respect ✅ `testLIMEServiceAudioManagerCompatibility()`

- [x] **Test: Vibration feedback**
  - Test `Vibrator` integration ✅ `testLIMEServiceVibratorCompatibility()`
  - Test vibration on key press ✅ `testLIMEServiceVibratorCompatibility()`
  - Test vibration setting respect ✅ `testLIMEServiceVibratorCompatibility()`

#### 5.8 Swipe Gesture Tests

- [x] **Test: Swipe gestures**
  - Test `swipeDown()` (typically hides keyboard) ✅ `testLIMEServiceSwipeMethods()`
  - Test `swipeUp()` gesture handling ✅ `testLIMEServiceSwipeMethods()`
  - Test `swipeLeft()` gesture handling ✅ `testLIMEServiceSwipeMethods()`
  - Test `swipeRight()` gesture handling ✅ `testLIMEServiceSwipeMethods()`

#### 5.9 Voice Input Integration Tests

- [x] **Test: Voice input launch**
  - Test `VoiceInputActivity` intent creation ✅ `testLIMEServiceVoiceInputIntentCreation()`
  - Test `VoiceInputActivity` availability check ✅ `testLIMEServiceVoiceInputActivityAvailability()`
  - Test voice recognition availability ✅ `testLIMEServiceVoiceRecognitionAvailability()`

- [x] **Test: Voice input result handling**
  - Test broadcast receiver for voice results ✅ `testLIMEServiceVoiceInputBroadcastReceiver()`
  - Test voice IME detection ✅ `testLIMEServiceVoiceIMEDetection()`
  - Test recognized text commit to input ✅ `testLIMEServiceVoiceInputBroadcastReceiver()`

#### 5.10 IM Picker and Options Menu Tests

- [x] **Test: IM picker**
  - Test `showIMPicker()` displays IM list ✅ `testLIMEServiceIMPickerOptions()`
  - Test `handleIMSelection()` switches IM ✅ `testLIMEServiceIMListHandling()`
  - Test activated IM list building ✅ `testLIMEServiceIMListHandling()`

- [x] **Test: Options menu**
  - Test `handleOptions()` shows options dialog ✅ `testLIMEServiceHandleOptions()`
  - Test preferences navigation ✅ `testLIMEServiceLaunchSettings()`
  - Test `launchSettings()` opens settings ✅ `testLIMEServiceLaunchSettings()`

#### 5.11 Fullscreen Mode Tests

- [x] **Test: Fullscreen editing mode**
  - Test `onEvaluateFullscreenMode()` returns correct value ✅ `testLIMEServiceFullscreenModeEvaluation()`
  - Test fullscreen mode based on screen size ✅ `testLIMEServiceDisplayMetricsHandling()`
  - Test `onDisplayCompletions()` in fullscreen ✅ `testLIMEServiceOnDisplayCompletions()`

#### 5.12 Window Insets and Layout Tests

- [x] **Test: Window insets handling**
  - Test `onComputeInsets()` calculates correct insets ✅ `testLIMEServiceWindowInsetsHandling()`
  - Test keyboard height calculation ✅ `testLIMEServiceDisplayMetricsHandling()`
  - Test candidate view positioning ✅ `testLIMEServiceCandidateViewHandler()`

#### 5.13 Input Connection Integration Tests

- [x] **Test: InputConnection operations**
  - Test `commitText()` on InputConnection ✅ `testLIMEServiceCommitTyped()`
  - Test `deleteSurroundingText()` for backspace ✅ `testLIMEServiceBackspaceHandling()`
  - Test `sendKeyEvent()` for special keys ✅ `testLIMEServiceKeyDownUp()`
  - Test `setSelection()` for cursor movement ✅ `testLIMEServiceCursorMovement()`
  - Test `onUpdateSelection()` handles cursor changes ✅ `testLIMEServiceOnUpdateSelection()`

#### 5.14 Mapping and Record Handling Tests

- [x] **Test: Mapping data handling**
  - Test `Mapping` object creation and manipulation ✅ `testLIMEServiceMappingHandling()`
  - Test mapping result processing ✅ `testLIMEServiceMappingHandling()`
  - Test score-based sorting of candidates ✅ `testLIMEServiceMappingHandling()`

#### 5.15 Character Validation Tests

- [x] **Test: Character type validation**
  - Test `isValidLetter()` for letter detection ✅ `testLIMEServiceValidationHelpers()`
  - Test `isValidDigit()` for digit detection ✅ `testLIMEServiceValidationHelpers()`
  - Test `isValidSymbol()` for symbol detection ✅ `testLIMEServiceValidationHelpers()`
  - Test character validation edge cases ✅ `testLIMEServiceCharacterValidationEdgeCases()`

#### 5.16 Preference Integration Tests

- [x] **Test: Preference manager integration**
  - Test `LIMEPreferenceManager` initialization ✅ `testLIMEServicePreferenceIntegration()`
  - Test preference default values ✅ `testLIMEServicePreferenceDefaultValues()`
  - Test preference boundary values ✅ `testLIMEServicePreferenceBoundaryValues()`
  - Test `loadSettings()` applies preferences ✅ `testLIMEServicePreferenceIntegration()`

#### 5.17 SearchServer Integration Tests (via LIMEService)

- [x] **Test: SearchServer lookup from LIMEService**
  - Test `SearchServer.getMappingByCode()` integration ✅ `testLIMEServiceSearchServerIntegration()`
  - Test cache reset after IM switch ✅ `testLIMEServiceSearchServerIntegration()`
  - Test table name configuration ✅ `testLIMEServiceSearchServerIntegration()`

#### 5.18 Error Handling and Edge Cases

- [x] **Test: Null input handling**
  - Test null `InputConnection` handling ✅ `testLIMEServiceNullInputHandling()`
  - Test null `EditorInfo` handling ✅ `testLIMEServiceEditorInfoCreation()`
  - Test null mapping results ✅ `testLIMEServiceMappingHandling()`

- [x] **Test: Empty string handling**
  - Test empty composing text ✅ `testLIMEServiceEmptyStringHandling()`
  - Test empty candidate list ✅ `testLIMEServiceCandidateListOperations()`
  - Test empty search results ✅ `testLIMEServiceEmptyStringHandling()`

- [x] **Test: Boundary conditions**
  - Test index bounds validation ✅ `testLIMEServiceIndexBoundsValidation()`
  - Test list operations edge cases ✅ `testLIMEServiceListOperationsEdgeCases()`
  - Test StringBuilder edge cases ✅ `testLIMEServiceStringBuilderEdgeCases()`

**Proposed Test Coverage**: 113 test methods planned across all 18 subsections of IME logic (needs verification against existing `LIMEServiceTest.java`)

---

### Phase 6: Integration Tests

**Objective**: Test interactions between layers with real implementations.

**Precondition**: Both `LIME.IM_PHONETIC` and `LIME.IM_DAYI` cloud data are downloaded and imported at test class startup via `@BeforeClass` setup. If an IM table already contains data, it is cleared first to ensure a consistent starting state before (re)import. This guarantees all Phase 6 tests run against fresh, real production IM data.

- [x] **Test: Both cloud IM tables preloaded** ✅
  - `IntegrationTestSearchServerDBServer.setUpClass()` downloads `LIME.IM_PHONETIC` and `LIME.IM_DAYI` ✅
  - `IntegrationTestUISearchServer.setUpClass()` downloads both tables ✅
  - `IntegrationTestUIDBServer.setUpClass()` downloads both tables ✅
  - `IntegrationTestBackupRestore.setUpClass()` downloads both tables ✅
  - Pre-import cleanup: `downloadAndWaitForImport()` clears table if non-empty ✅
  - Precondition verified: Both tables have records before tests execute ✅

#### 6.1 SearchServer → LimeDB Integration

- [x] **Test: Complete search flow with REAL IM data** ✅ `IntegrationTestSearchServerDBServer.test_6_1_CompleteSearchFlow()`
  - Test `SearchServer.getMappingByCode()` → `LimeDB.getRecords()` with production phonetic data ✅
  - Test caching behavior with real dataset ✅ `IntegrationTestSearchServerDBServer.test_6_1_CachingBehavior()`
  - Test error handling and propagation ✅ `IntegrationTestSearchServerDBServer.test_6_1_ErrorHandlingPropagation()`

- [x] **Test: Configuration operations** ✅
  - Test `SearchServer.setImInfo()` → `LimeDB.setImInfo()` ✅ `IntegrationTestSearchServerDBServer.test_6_1_ConfigurationSetImInfo()`
  - Test `SearchServer.getImInfo()` → `LimeDB.getImInfo()` ✅ `IntegrationTestSearchServerDBServer.test_6_1_ConfigurationGetImInfo()`
  - Test data persistence ✅ `IntegrationTestSearchServerDBServer.test_6_1_DataPersistence()`

#### 6.2 DBServer → LimeDB Integration

- [x] **Test: Export flow with REAL IM data** ✅ `IntegrationTestSearchServerDBServer.test_5_2_ExportFlow()`
  - Test `DBServer.exportZippedDb()` → `LimeDB.prepareBackup()` with production phonetic data ✅
  - Test file creation and zip integrity ✅ `IntegrationTestSearchServerDBServer.test_5_2_ZipIntegrity()`
  - Test data completeness ✅ `IntegrationTestSearchServerDBServer.test_5_2_DataCompleteness()`

- [x] **Test: Import flow** ✅
  - Test `DBServer.importZippedDb()` → `LimeDB.importDb()` round-trip with production data ✅ `IntegrationTestSearchServerDBServer.test_5_2_ImportFlowZippedDb()`
  - Test `DBServer.importZippedDbRelated()` → `LimeDB.importDbRelated()` ✅ `IntegrationTestSearchServerDBServer.test_5_2_ImportFlowRelated()`
  - Test data integrity after import ✅ `IntegrationTestSearchServerDBServer.test_5_2_DataIntegrityAfterImport()`
  - Test overwrite behavior ✅ `IntegrationTestSearchServerDBServer.test_5_2_OverwriteBehavior()`

#### 6.3 UI → SearchServer Integration (Complete Flow)

**Objective**: Test complete UI integration flows including remote import, hot path queries, and user learning behavior. Both PHONETIC and DAYI tables are available from Phase 6 precondition.

##### 6.3.1 Basic UI Operation Flow

- [x] **Test: Complete UI operation flow** ✅ `UISearchServerIntegrationTest.test_5_3_1_CompleteUIOperationFlow()`
  - Test UI component → `SearchServer` → `LimeDB` → Database ✅
  - Test data flow and transformations ✅ `UISearchServerIntegrationTest.test_5_3_1_DataFlowTransformations()`
  - Test error handling at each layer ✅ `UISearchServerIntegrationTest.test_5_3_1_ErrorHandlingAtEachLayer()`

##### 6.3.2 Remote Import + Hot Path Queries (Phonetic, Dayi)

- [x] **Precondition: Cloud URLs available** ✅ `UISearchServerIntegrationTest.test_5_3_2_0_CloudURLsAvailable()`
  - Source: `SetupImFragment.getUrlForImTableVariant(String imTableVariant)` ✅
  - Verify URLs exist for `LIME.IM_PHONETIC` and `LIME.IM_DAYI` ✅ `UISearchServerIntegrationTest.test_5_3_2_0b_IMTypeConstantsExist()`

- [x] **Test: Download + Import smallest IM DBs** ✅ `UISearchServerIntegrationTest.test_5_3_2_DownloadAndImportPhonetic()`, `UISearchServerIntegrationTest.test_5_3_2_DownloadAndImportDayi()`
  - Invoke `SetupImController.downloadAndImportZippedDb(tableName, url, restoreLearning=false)` ✅
  - Use `tableName` = `LIME.IM_PHONETIC` then `LIME.IM_DAYI` ✅
  - Assert: `SearchServer.getImInfo(table, "amount")` > 0 for both tables ✅ (polled until import completes)
  - Note: UI progress and menu refresh are indirectly verified by successful import; detailed UI checks covered in UI tests

- [x] **Test: Hot path query latency and caching** ✅ `UISearchServerIntegrationTest.test_5_3_2_HotPathQueryLatency()`
  - After import, call `SearchServer.getMappingByCode(code, softKeyboard=false, getAllRecords=false)` ✅
  - Measure initial query latency vs subsequent queries (cache warm) ✅ `UISearchServerIntegrationTest.test_5_3_2_CacheWarmthVerification()`
  - Assert: subsequent query is faster (cache hit path) ✅
  - Validate dual-code expansion and blacklist checks are functional (no crash, reasonable result set) ✅ `UISearchServerIntegrationTest.test_5_3_2_DualCodeExpansionAndBlacklist()`

- [x] **Test: Error handling on network failures** ✅ `UISearchServerIntegrationTest.test_5_3_2_ErrorHandlingOnNetworkFailures()`
  - Simulate invalid URL for IM type ✅
  - Assert: IM `amount` unchanged and operation completes without crash ✅
  - Note: User-visible messages are handled by controller; data layer remains consistent ✅

- **References**
  - [URL mapping source: SetupImFragment.getUrlForImTableVariant](app/src/main/java/net/toload/main/hd/ui/view/SetupImFragment.java#L858-L891)
  - [IM type constants: LIME.IM_PHONETIC](app/src/main/java/net/toload/main/hd/global/LIME.java#L166), [LIME.IM_DAYI](app/src/main/java/net/toload/main/hd/global/LIME.java#L154)
  - [Cloud URLs: LIME.DATABASE_CLOUD_IM_PHONETIC](app/src/main/java/net/toload/main/hd/global/LIME.java#L90), [LIME.DATABASE_CLOUD_IM_DAYI](app/src/main/java/net/toload/main/hd/global/LIME.java#L98)

##### 6.3.3 Learning Path (User Records) — Query Behavior

- [x] **Precondition: Table imported** ✅ `UISearchServerIntegrationTest.test_5_3_3_0_PreconditionTableImported()`
  - Ensure `LIME.IM_PHONETIC` or `LIME.IM_DAYI` imported via 5.3.2 ✅

- [x] **Test: Learned entries influence results** ✅ `UISearchServerIntegrationTest.test_5_3_3_LearnedEntriesInfluenceResults()`
  - Add a user-learned mapping (via UI flow if available, or by preloading a sentinel learned record) ✅
  - Query via `SearchServer.getMappingByCode(code, false, false)`; assert learned entry surfaces with appropriate score ✅
  - Validate blacklist/dual-code expansion does not suppress learned entry unless configured ✅ `UISearchServerIntegrationTest.test_5_3_3_BlacklistNotSuppressLearned()`

- [x] **Test: Cache respects learning updates** ✅ `UISearchServerIntegrationTest.test_5_3_3_CacheRespectsLearningUpdates()`
  - Query cold → warm; then add learned entry; re-query and assert updated results (cache invalidation) ✅

Call Chain Details (from FUNCTION_CALL_CHAINS.md):
- `getMappingByCode()` hot path touches: remapping → dual-code expansion → blacklist → `db.rawQuery` → build result; cache improves subsequent calls
- Learned data alters scoring and availability used in result construction

#### 6.4 UI → DBServer → LimeDB Integration

- [x] **Test: Complete file operation flow** ✅
  - Test UI component → `DBServer` → `LimeDB` → Database ✅ `UIDBServerIntegrationTest.test_5_4_CompleteFileOperationFlow_Export()`, `UIDBServerIntegrationTest.test_5_4_CompleteFileOperationFlow_Import()`
  - Test file operations and database updates ✅ `UIDBServerIntegrationTest.test_5_4_FileOperationsAndDatabaseUpdates()`
  - Test progress callbacks ✅ `UIDBServerIntegrationTest.test_5_4_ProgressCallbacks()`
  - Test multiple file operations in sequence ✅ `UIDBServerIntegrationTest.test_5_4_MultipleFileOperationsSequence()`
  - Test error handling ✅ `UIDBServerIntegrationTest.test_5_4_ErrorHandlingInFileOperations()`
  - Test related table operations ✅ `UIDBServerIntegrationTest.test_5_4_RelatedTableFileOperations()`

#### 6.5 Backup Path (User Records) — Before Overwrite

- [x] **Precondition: Table has existing learned records** ✅
  - Ensure learned records exist for target table (e.g., `LIME.IM_PHONETIC`) ✅

- [x] **Test: Explicit backup on clear table** ✅ `BackupRestoreIntegrationTest.test_5_5_ExplicitBackupOnClearTable()`
  - Invoke `SetupImController.clearTable(tableName, backupUserRecords=true)` ✅
  - Assert: `SearchServer.backupUserRecords(tableName)` executed prior to deletion ✅
  - Assert: backup table exists and contains learned entries (`SearchServer.checkBackuptable(tableName)`) ✅
  - Test backup table structure and content ✅ `BackupRestoreIntegrationTest.test_5_5_BackupTableStructureAndContent()`

- [x] **Test: Backup during import (restore flag path)** ✅ `BackupRestoreIntegrationTest.test_5_5_BackupDuringImportWithRestoreFlag()`
  - Invoke `SetupImController.importZippedDb(file, tableName, restoreUserRecords=true)` with non-empty table ✅
  - Assert: `SearchServer.backupUserRecords(tableName)` called before import transaction ✅
  - Test multiple backups overwrite behavior ✅ `BackupRestoreIntegrationTest.test_5_5_MultipleBackupsOverwrite()`

Call Chain Details (from FUNCTION_CALL_CHAINS.md):
- `SetupImController.clearTable(...)` → `SearchServer.backupUserRecords(table)` → `LimeDB.backupUserRecords()`
- `importZippedDb(...)` (restore=true & count>0) → backup → import → optional restore

#### 6.6 Restore Path (User Records) — After Import

- [x] **Precondition: Backup table present** ✅
  - Ensure backup exists from 6.5 ✅

- [x] **Test: Restore after import** ✅ `BackupRestoreIntegrationTest.test_5_6_RestoreAfterImport()`
  - Invoke `SetupImController.importZippedDb(file, tableName, restoreUserRecords=true)` ✅
  - Assert: `SearchServer.checkBackuptable(tableName)` then `SearchServer.restoreUserRecords(tableName)` executed ✅ `BackupRestoreIntegrationTest.test_5_6_CheckBackupTableBeforeRestore()`
  - Assert: learned entries reappear post-import (counts or sentinel check) ✅ `BackupRestoreIntegrationTest.test_5_6_RestorePreservesLearnedEntries()`

- [x] **Test: No-restore path** ✅ `BackupRestoreIntegrationTest.test_5_6_NoRestorePath()`
  - Import with `restoreUserRecords=false` ✅
  - Assert: no `backup/restore` calls; learned entries do not persist ✅

- [x] **Test: Error handling** ✅ `BackupRestoreIntegrationTest.test_5_6_RestoreWithNoBackup()`
  - Test restore with no backup present ✅
  - Test complete backup → restore workflow ✅ `BackupRestoreIntegrationTest.test_5_6_CompleteBackupRestoreWorkflow()`

- [x] **Test: UI refresh after restore** ✅ `BackupRestoreIntegrationTest.test_5_6_7_UIRefreshAfterRestore()`
  - Assert: System ready for `refreshMenu()` and `refreshSetupImButtonStates()` calls ✅
  - Verify data layer ready for `NavigationDrawerView.updateMenuItems()` operations ✅
  - Note: Full UI-level refresh testing would be in UI instrumentation tests ✅

Call Chain Details (from FUNCTION_CALL_CHAINS.md):
- `checkBackuptable(table)` → `restoreUserRecords(table)` via `SearchServer` → `LimeDB.restoreUserRecords()`
- Improvement noted: restore logic centralized in `LimeDB`, Runnable simplified

---

### Phase 7: Architecture Compliance Tests

**Objective**: Verify architectural boundaries are respected.

#### 7.1 Static Analysis Tests

- [x] **Test: No direct LimeDB access from UI** ✅ `testNoDirectLimeDBInUIComponents()`
  - Use static analysis to find `new LimeDB()` in UI package
  - Verify all instances are in `SearchServer` or `DBServer` only
  - Report any violations
  - ✅ Implemented in `ArchitectureComplianceTest.java`

- [x] **Test: No SQL operations outside LimeDB** ✅ `test_7_1_2_NoSQLOperationsOutsideLimeDB()`
  - Search for SQL keywords (`execSQL`, `rawQuery`, `query`, `insert`, `update`, `delete`) in non-LimeDB files
  - Verify all SQL is in `LimeDB.java` (except `LimeHanConverter` and `EmojiConverter`)
  - Report any violations
  - ✅ Implemented in `ArchitectureComplianceTest.java`

- [x] **Test: No file operations outside DBServer** ✅ `test_7_1_3_NoFileOperationsOutsideDBServer()`
  - Search for file operations (`FileOutputStream`, `FileInputStream`, `LIMEUtilities.zip`, `LIMEUtilities.unzip`) in non-DBServer files
  - Verify all file operations are in `DBServer.java` (except utilities)
  - Report any violations
  - ✅ Implemented in `ArchitectureComplianceTest.java`

#### 7.2 Runtime Architecture Tests

- [x] **Test: Component initialization** ✅ `test_7_2_1_ComponentInitialization()`
  - Verify UI components initialize `SearchServer`, not `LimeDB`
  - Verify `LIMEService` initializes `SearchServer`, not `LimeDB`
  - Use reflection to inspect component fields
  - ✅ Implemented in `ArchitectureComplianceTest.java`

- [x] **Test: Method call tracing** ✅ `test_7_2_2_MethodCallTracing()`
  - Trace method calls from UI components
  - Verify calls go through `SearchServer` or `DBServer`
  - Verify no direct calls to `LimeDB` from UI
  - ✅ Implemented in `ArchitectureComplianceTest.java`

---

### Phase 8: Regression Tests - Core IME End-to-End Workflows

**Test File**: `RegressionTest.java` ✅ **COMPLETED (28/28 tests)**
**Location**: `app/src/androidTest/java/net/toload/main/hd/`
**Test Count**: 28 test methods

**Objective**: Ensure core user-facing IME functionality still works after refactoring. These are the most critical tests for LIME IME's query and learning logic - testing complete input → query → candidate → learning workflows.

**Note**: Edge case testing (null handling, empty data, invalid input) is already covered by Phase 1-2 unit tests in `LimeDBTest` and `SearchServerTest`. Phase 8 focuses on end-to-end user workflows with real IM data.

**Precondition**: IM tables (PHONETIC/DAYI) preloaded from Phase 6 precondition.

#### 8.1 Soft Keyboard Input Integration with Real IM Data

// The following regression tests were removed or moved to earlier phases (1, 3, or 5) as they do not require real data and are already covered in those phases:
// - Soft keyboard input → query → candidates with real IM data
// - Soft keyboard candidate selection → commit → learning
// - Soft keyboard composing text with real query results
// See phases 1, 3, and 5 for their new locations or coverage.

#### 8.2 Hard Keyboard Input Integration with Real IM Data ✅ **COMPLETED** (1 test)

- [x] **Test: Hardware keyboard `onKeyDown()` → query → candidates** ✅ `RegressionTest.test_8_2_HardwareKeyboardInput()`
  - Simulate hardware key events (`KeyEvent.ACTION_DOWN`)
  - Invoke `LIMEService.onKeyDown()` → `translateKeyDown()` → query path
  - Assert: key events translated to IM codes correctly
  - Assert: candidates fetched from real IM data
  - Test with QWERTY keyboard layout mapping
  - Note: onKeyUp and special key handling verified via key event handling logic

#### 8.3 Query and Caching Path with Real Data ✅ **COMPLETED** (1 test)

- [x] **Test: Hot query path latency verification** ✅ `RegressionTest.test_8_3_QueryLatencyAndCaching()`
  - First query (cold cache): measure latency with real phonetic data
  - Subsequent queries (warm cache): verify faster response
  - Assert: cache hit improves query performance
  - Test with production-sized IM tables (15,000+ records)
  - Note: Query result accuracy covered by basic query tests

#### 8.4 Learning Path Integration ✅ **COMPLETED** (27 tests)

**8.4.0 Basic Learning Tests** ✅ **COMPLETED** (3 tests)

- [x] **Test: Score update after candidate selection** ✅ `RegressionTest.test_8_4_ScoreUpdateAfterSelection()`
  - Select candidate word → verify score incremented in DB
  - Query same code again → selected word appears higher
  - Test score persistence across IME sessions

- [x] **Test: Related phrase learning** ✅ Covered in learning/score tests
  - Select word → verify `learnRelatedPhraseAndUpdateScore()` called
  - Query related phrase → verify learned phrases appear
  - Test related phrase display in candidate view

- [x] **Test: User record backup/restore with learning data** ✅ Verified via DB persistence
  - Create learned entries via candidate selection
  - Backup user records → reimport IM table → restore
  - Assert: learned scores restored correctly
  - Verify end-to-end learning persistence

**8.4.1 learnRelatedPhraseAndUpdateScore() Method Tests** ✅ **COMPLETED** (4 tests)

- [x] **Test: learnRelatedPhraseAndUpdateScore basic functionality** ✅ `RegressionTest.test_8_4_1_LearnRelatedPhraseAndUpdateScore()`
  - Create test mapping with code="test", word="測試", score=100
  - Invoke `SearchServer.learnRelatedPhraseAndUpdateScore(mapping)`
  - Assert: mapping added to scorelist (internal state)
  - Assert: updateScoreCache() spawned in background thread
  - Verify: score updated asynchronously in database
  - Query mapping again → verify score incremented

- [x] **Test: learnRelatedPhraseAndUpdateScore with null mapping** ✅ `RegressionTest.test_8_4_1_LearnWithNullMapping()`
  - Invoke `SearchServer.learnRelatedPhraseAndUpdateScore(null)`
  - Assert: no exception thrown
  - Assert: scorelist remains valid (null-safe)
  - Verify: no database updates triggered

- [x] **Test: learnRelatedPhraseAndUpdateScore thread safety** ✅ `RegressionTest.test_8_4_1_LearnThreadSafety()`
  - Invoke `learnRelatedPhraseAndUpdateScore()` multiple times rapidly (10+ calls)
  - Assert: all mappings added to scorelist
  - Assert: all background threads complete successfully
  - Verify: no race conditions in scorelist updates
  - Assert: final database state consistent

- [x] **Test: learnRelatedPhraseAndUpdateScore score accumulation** ✅ `RegressionTest.test_8_4_1_ScoreAccumulation()`
  - Select same word multiple times (3+ selections)
  - Invoke `learnRelatedPhraseAndUpdateScore()` for each selection
  - Query word after each selection
  - Assert: score increases monotonically
  - Verify: learned word moves higher in candidate list

**8.4.2 learnRelatedPhrase() Method Tests (via public API)** ✅ **COMPLETED** (6 tests)

- [x] **Test: learnRelatedPhrase consecutive word learning** ✅ `RegressionTest.test_8_4_2_LearnRelatedPhraseConsecutive()`
  - Enable "Learn related words" preference: `LIMEPref.setLearnRelatedWord(true)`
  - Select two consecutive words: "你" (code=ㄋㄧ), then "好" (code=ㄏㄠ)
  - Trigger learning via `postFinishInput()` or session completion
  - Query database: `dbadapter.getMappingByWord("你", tablename)`
  - Assert: related phrase record exists linking "你" → "好"
  - Assert: score for related phrase > 0
  - Verify: subsequent query for "你" suggests "好" as related phrase

- [x] **Test: learnRelatedPhrase with preference disabled** ✅ `RegressionTest.test_8_4_2_LearnRelatedPhraseDisabled()`
  - Disable "Learn related words": `LIMEPref.setLearnRelatedWord(false)`
  - Select two consecutive words: "測" → "試"
  - Trigger learning via session completion
  - Query database for related phrase records
  - Assert: no related phrase records created
  - Verify: preference flag correctly prevents learning

- [x] **Test: learnRelatedPhrase with single word (edge case)** ✅ `RegressionTest.test_8_4_2_LearnRelatedPhraseSingleWord()`
  - Enable "Learn related words" preference
  - Select only one word in session: "單"
  - Trigger learning via session completion
  - Assert: no related phrase records created (requires 2+ words)
  - Verify: scorelist.size() == 1, no phrase learning triggered

- [x] **Test: learnRelatedPhrase skips null/empty mappings** ✅ `RegressionTest.test_8_4_2_LearnRelatedPhraseSkipNull()`
  - Create scorelist with valid word "你", null entry, valid word "好"
  - Trigger `learnRelatedPhrase(scorelist)`
  - Assert: null entries skipped gracefully
  - Assert: no related phrase created for null → "好"
  - Verify: valid adjacent pairs still learned

- [x] **Test: learnRelatedPhrase with punctuation symbols** ✅ `RegressionTest.test_8_4_2_LearnRelatedPhraseWithPunctuation()`
  - Select: "測試" (word) → "。" (Chinese punctuation)
  - Trigger learning
  - Assert: related phrase created for "測試" → "。"
  - Verify: `isChinesePunctuationSymbolRecord()` allows unit2 to be punctuation
  - Test with emoji: "測試" → "😊" → verify emoji allowed

- [x] **Test: learnRelatedPhrase triggers LD phrase learning** ✅ `RegressionTest.test_8_4_2_LearnRelatedPhraseTriggersLD()`
  - Enable "Learn phrases": `LIMEPref.setLearnPhrase(true)`
  - Select consecutive words: "電" → "腦" (to create "電腦")
  - Trigger learning multiple times to build score > 20
  - Assert: `addLDPhrase()` called for both units
  - Verify: LD phrase buffer populated (LDPhraseListArray)
  - Assert: LD phrase learning triggered when related phrase score > 20

**8.4.3 learnLDPhrase() Method Tests (via public API)** ✅ **COMPLETED** (10 tests)

- [x] **Test: learnLDPhrase basic two-character phrase** ✅ `RegressionTest.test_8_4_3_LearnLDPhraseTwoChar()`
  - Enable "Learn phrases": `LIMEPref.setLearnPhrase(true)`
  - Build phrase: select "電" (code=ㄉㄧㄢ) → "腦" (code=ㄋㄠ)
  - Populate LDPhraseListArray via `addLDPhrase(unit1, false)`, `addLDPhrase(unit2, true)`
  - Trigger `learnLDPhrase(LDPhraseListArray)` via `postFinishInput()`
  - Query database for phrase "電腦"
  - Assert: LD phrase record created with combined code
  - Assert: Quick Phrase (QP) code created (first char of each: "ㄉㄋ")
  - Verify: typing "ㄉㄋ" suggests "電腦"

- [x] **Test: learnLDPhrase three-character phrase** ✅ `RegressionTest.test_8_4_3_LearnLDPhraseThreeChar()`
  - Build phrase: "中" → "華" → "民" (3 chars)
  - Populate LDPhraseListArray
  - Trigger `learnLDPhrase()`
  - Assert: LD phrase "中華民" created
  - Assert: QP code combines first chars of all three codes
  - Verify: typing QP code suggests "中華民"

- [x] **Test: learnLDPhrase four-character phrase limit** ✅ `RegressionTest.test_8_4_3_LearnLDPhraseFourCharLimit()`
  - Build phrase: "中" → "華" → "民" → "國" (4 chars, at limit)
  - Trigger `learnLDPhrase()`
  - Assert: LD phrase "中華民國" created (size < 5 allowed)
  - Build phrase: "一" → "二" → "三" → "四" → "五" (5 chars, exceeds limit)
  - Assert: phrase NOT learned (size check: phraselist.size() < 5)
  - Verify: 4-character limit enforced

- [x] **Test: learnLDPhrase skips English mixed mode** ✅ `RegressionTest.test_8_4_3_LearnLDPhraseSkipsEnglish()`
  - Build phrase: unit1 with code="test", word="test" (English pass-through)
  - Trigger `learnLDPhrase()`
  - Assert: phrase learning abandoned (code.equals(word) check)
  - Verify: no LD phrase created for English input

- [x] **Test: learnLDPhrase with multi-character base word** ✅ `RegressionTest.test_8_4_3_LearnLDPhraseMultiCharBase()`
  - Build phrase: unit1.word="電腦" (2-char word) → unit2.word="網路" (2-char word)
  - Trigger `learnLDPhrase()`
  - Assert: baseCode rebuilt by reverse lookup (getMappingByWord for each char)
  - Assert: QPCode constructed from first char of each lookup
  - Verify: combined phrase "電腦網路" learned with correct codes

- [x] **Test: learnLDPhrase handles null codes via reverse lookup** ✅ `RegressionTest.test_8_4_3_LearnLDPhraseReverseLookup()`
  - Create mapping with unit.id=null, unit.code=null, unit.word="電"
  - Trigger `learnLDPhrase()`
  - Assert: reverse lookup via `dbadapter.getMappingByWord("電", tablename)` succeeds
  - Assert: baseCode populated from lookup result
  - Verify: phrase learning proceeds with reconstructed code

- [x] **Test: learnLDPhrase abandons on failed reverse lookup** ✅ `RegressionTest.test_8_4_3_LearnLDPhraseAbandonOnFailedLookup()`
  - Create mapping with unit.word="X" (non-existent character in DB)
  - Trigger `learnLDPhrase()`
  - Assert: reverse lookup returns empty list
  - Assert: phrase learning abandoned (break statement)
  - Verify: no partial/invalid LD phrase created

- [x] **Test: learnLDPhrase skips partial match records** ✅ `RegressionTest.test_8_4_3_LearnLDPhraseSkipsPartialMatch()`
  - Create mapping with `unit.isPartialMatchToCodeRecord()=true`
  - Trigger `learnLDPhrase()`
  - Assert: triggers reverse lookup path (id check logic)
  - Verify: partial match records handled via code reconstruction

- [x] **Test: learnLDPhrase skips composing code and English suggestions** ✅ `RegressionTest.test_8_4_3_LearnLDPhraseSkipsComposing()`
  - Build phrase: unit1="測" → unit2.isComposingCodeRecord()=true
  - Trigger `learnLDPhrase()`
  - Assert: learning breaks when unit2 is composing code
  - Test with unit2.isEnglishSuggestionRecord()=true
  - Assert: learning breaks for English suggestions

**8.4.4 Integration Tests: Complete Learning Flow** ✅ **COMPLETED** (3 tests)

- [x] **Test: Complete learning flow - score → related → LD phrase** ✅ `RegressionTest.test_8_4_4_CompleteLearningFlow()`
  - Enable all learning preferences
  - Simulate user typing sentence: "我愛台灣" (4 words)
  - For each word selection:
    - Invoke `learnRelatedPhraseAndUpdateScore(mapping)`
    - Assert: score updated
  - Trigger `postFinishInput()` to complete session
  - Assert: `learnRelatedPhrase(scorelist)` called internally
  - Assert: related phrases created: "我"→"愛", "愛"→"台", "台"→"灣"
  - Assert: if score > 20, `addLDPhrase()` triggered
  - Assert: `learnLDPhrase(LDPhraseListArray)` called
  - Verify: LD phrases created for qualified pairs
  - Query all learned data:
    - Individual word scores incremented
    - Related phrase records exist
    - LD phrase records created with QP codes

- [x] **Test: Learning flow with preference combinations** ✅ `RegressionTest.test_8_4_4_LearningPreferenceCombinations()`
  - Test Case 1: LearnRelatedWord=true, LearnPhrase=false
    - Assert: related phrases learned, no LD phrases
  - Test Case 2: LearnRelatedWord=false, LearnPhrase=true
    - Assert: no related phrases, no LD phrases (LD requires related)
  - Test Case 3: Both disabled
    - Assert: only score updates (via learnRelatedPhraseAndUpdateScore)
  - Test Case 4: Both enabled
    - Assert: full learning chain activated

- [x] **Test: Learning flow persistence across IME sessions** ✅ `RegressionTest.test_8_4_4_LearningPersistenceAcrossSessions()`
  - Session 1: Type "測試" → verify learning
  - Clear cache, simulate IME restart
  - Session 2: Query "測試" again
  - Assert: learned score persisted
  - Assert: related phrases from Session 1 still available
  - Type continuation: "測試" → "成功"
  - Assert: new related phrase "測試"→"成功" learned
  - Verify: cumulative learning across sessions

**Test Coverage Summary for Learning Path**: ✅ **COMPLETED: 23/23 tests** (1 basic + 4 learnRelatedPhraseAndUpdateScore + 6 learnRelatedPhrase + 9 learnLDPhrase + 3 integration)

#### 8.5 IM Switching with Real Data ✅ **COMPLETED** (1 test)

- [x] **Test: Switch between IM types with cached data** ✅ `RegressionTest.test_8_5_SwitchBetweenIM()`
  - Start with Phonetic IM → query → switch to Dayi
  - Assert: cache reset on IM switch
  - Assert: new IM table loaded correctly
  - Test `switchToNextActivatedIM()` with multiple IMs
  - Note: IM configuration changes (keyboard layout switching) covered implicitly in IM switching & Setup tests

---

#### 8.6 Voice Input Integration ✅ **COMPLETED** (15 tests)

**Objective**: Achieve 90% coverage of LIMEService voice input subsystem (currently 0% coverage, ~250 instructions).

**8.6.1 Voice Input Launch and IME Switching** (6 tests)

- [x] **Test: Voice input launch from LIMEService** - `test_8_6_1_VoiceInputLaunch` ✅
  - Trigger `LIMEService.startVoiceInput()` from option menu
  - Verify voice IME detection on Android 16+ via InputMethodManager
  - Assert: voice-capable IME found in enabled input methods
  - Verify `switchInputMethod()` called with voice IME ID
  - Assert: IME change monitoring started via ContentObserver
  - Test fallback to RecognizerIntent when voice IME unavailable

- [x] **Test: Voice IME unavailable fallback** - `test_8_6_2_VoiceIMEUnavailableFallback` ✅
  - Simulate environment with no voice-capable IME installed
  - Trigger `LIMEService.startVoiceInput()`
  - Verify fallback to `launchRecognizerIntent()` path
  - Assert: VoiceInputActivity launched with RecognizerIntent
  - Verify FLAG_ACTIVITY_NEW_TASK set correctly
  - Test exception handling (ActivityNotFoundException, SecurityException)

- [x] **Test: Voice input intent configuration** - `test_8_6_3_VoiceInputIntentConfiguration` ✅
  - Verify `getVoiceIntent()` creates correct intent
  - Assert: ACTION_RECOGNIZE_SPEECH action set
  - Assert: LANGUAGE_MODEL_FREE_FORM model used
  - Assert: EXTRA_MAX_RESULTS configured
  - Verify prompt text set for user display
  - Test locale handling and propagation

- [x] **Test: IME change monitoring setup** - `test_8_6_4_IMEChangeMonitoringSetup` ✅
  - Trigger voice input → verify `startMonitoringIMEChanges()` called
  - Assert: ContentObserver registered on Settings.Secure.DEFAULT_INPUT_METHOD
  - Verify observer callback implementation (onChange())
  - Test automatic LIME restoration logic on IME change
  - Verify observer runs on correct Handler thread
  - Test concurrent monitoring prevention

- [x] **Test: Switch back to LIME after voice input** - `test_8_6_5_SwitchBackToLIME` ✅
  - Complete voice input → verify `switchBackToLIME()` invoked
  - Assert: LIME IME ID validation (getPackageName() + component)
  - Verify `setInputMethod()` or `switchInputMethod()` called
  - Assert: monitoring stopped via `stopMonitoringIMEChanges()`
  - Test InputMethodManager null handling
  - Verify complete voice → LIME transition workflow

- [x] **Test: Voice input broadcast receiver integration** - `test_8_6_6_VoiceInputBroadcastReceiver` ✅
  - Verify `registerVoiceInputReceiver()` called onCreate()
  - Assert: BroadcastReceiver registered for ACTION_VOICE_RESULT
  - Simulate voice recognition result broadcast
  - Verify recognized text extracted from intent extras
  - Assert: `commitText()` called on InputConnection with result
  - Verify `unregisterVoiceInputReceiver()` called onDestroy()

**8.6.2 Voice Input Error Handling** (4 tests)

- [x] **Test: Voice input with null InputMethodManager** - `test_8_6_7_VoiceInputNullIMM` ✅
  - Simulate null InputMethodManager scenario
  - Trigger `startVoiceInput()`
  - Assert: defensive null check prevents crash
  - Verify fallback behavior to RecognizerIntent
  - Test error message or toast displayed to user

- [x] **Test: Voice input SecurityException handling** - `test_8_6_8_VoiceInputSecurityException` ✅
  - Mock SecurityException during IME switch attempt
  - Trigger `launchRecognizerIntent()`
  - Assert: exception caught and handled gracefully
  - Verify user notified of permission issue
  - Test IME remains functional after error

- [x] **Test: Voice input receiver unregistration error** - `test_8_6_9_VoiceInputReceiverUnregisterError` ✅
  - Call `unregisterVoiceInputReceiver()` when receiver not registered
  - Assert: IllegalArgumentException caught
  - Verify no crash on onDestroy() with unregistered receiver
  - Test idempotent cleanup behavior

- [x] **Test: Voice input monitoring timeout** - `test_8_6_10_VoiceInputMonitoringTimeout` ✅
  - Start voice input → monitoring active
  - Simulate extended time without IME change
  - Verify monitoring auto-stops after timeout/threshold
  - Assert: observer unregistered correctly
  - Test cleanup prevents memory leaks

**8.6.3 Voice Input Integration with UI** (5 tests)

- [x] **Test: Voice input invocation from CandidateView** - `test_8_6_11_VoiceInputFromCandidateView` ✅
  - Trigger voice input button in CandidateView
  - Verify `LIMEService.startVoiceInput()` delegation
  - Assert: complete workflow from UI → service → voice IME
  - Test voice input available check before launch
  - Verify UI feedback (button enabled/disabled state)

- [x] **Test: Voice input results insertion to InputConnection** - `test_8_6_12_VoiceInputResultsInsertion` ✅
  - Simulate voice recognition result: "測試文字"
  - Verify broadcast received by `voiceInputReceiver`
  - Assert: `getCurrentInputConnection().commitText("測試文字", 1)` called
  - Verify composing text cleared before insertion
  - Test with null InputConnection (defensive handling)

- [x] **Test: Voice input with ongoing composing text** - `test_8_6_13_VoiceInputWithComposingText` ✅
  - User has composing text "ㄅㄆ" active
  - Trigger voice input → switch to voice IME
  - Verify composing text state saved or cleared
  - Complete voice input → switch back to LIME
  - Assert: composing text state restored or reset appropriately
  - Test candidate view state across transitions

- [x] **Test: Multiple voice input invocations** - `test_8_6_14_MultipleVoiceInputInvocations` ✅
  - Trigger voice input → complete → switch back to LIME
  - Trigger voice input again (second invocation)
  - Verify duplicate monitoring prevention
  - Assert: previous observer cleaned up before new one
  - Test no resource leaks across multiple launches
  - Verify state reset between invocations

- [x] **Test: Voice input disabled via preferences** - `test_8_6_15_VoiceInputDisabledPreference` ✅
  - Set voice input disabled in LIMEPreference
  - Attempt to trigger `startVoiceInput()`
  - Assert: voice input launch prevented
  - Verify preference check at entry point
  - Test voice button hidden in UI when disabled

#### 8.7 IME Selection and Options Menu ✅ **COMPLETED** (12 tests)

**Objective**: Achieve 90% coverage of LIMEService IME picker and options menu subsystems (currently 0% coverage, ~200 instructions).

**Test File**: `LIMEServiceTest.java` (lines 3255-3493)
**Status**: ✅ **COMPLETED** - 12 tests implemented

**8.7.1 Options Menu Display and Handling** (4 tests)

- [x] **Test: Options menu invocation from LIMEService** - `test_8_7_1_OptionsMenuInvocation` ✅
  - Trigger option key event in LIMEService
  - Verify `handleOptions()` called
  - Assert: options menu created with all items (IM picker, settings, voice, Han converter)
  - Test menu item click handlers registered
  - Verify menu displayed to user

- [x] **Test: IM picker menu item selection** - `test_8_7_2_IMPickerMenuItemSelection` ✅
  - Open options menu → select "IM Picker" item
  - Verify `showIMPicker()` invoked
  - Assert: AlertDialog created with IM list
  - Test IM list population via `buildActivatedIMList()`
  - Verify SearchServer.getImList() delegation

- [x] **Test: Settings menu item selection** - `test_8_7_3_SettingsMenuItemSelection` ✅
  - Open options menu → select "Settings" item
  - Verify `launchSettings()` invoked
  - Assert: LIMEPreference activity launched
  - Test intent configuration (activity component)
  - Verify startActivity() call

- [x] **Test: Han converter menu item selection** - `test_8_7_4_HanConverterMenuItemSelection` ✅
  - Open options menu → select "Han Converter" item
  - Verify `showHanConvertPicker()` invoked
  - Assert: conversion type dialog displayed
  - Test conversion types (Simplified, Traditional, None)
  - Verify selection callback `handleHanConvertSelection()`

**8.7.2 IM Picker Dialog and Selection** (5 tests)

- [x] **Test: IM picker dialog creation and display** - `test_8_7_5_IMPickerDialogCreation` ✅
  - Trigger `showIMPicker()` from options or keyboard key
  - Verify `buildActivatedIMList()` called to populate list
  - Assert: AlertDialog builder used with IM names
  - Test dialog title set ("Select Input Method" or localized)
  - Assert: dialog.show() called
  - Verify current IM highlighted in list

- [x] **Test: IM selection from picker dialog** - `test_8_7_6_IMSelectionFromPicker` ✅
  - Open IM picker → select different IM (e.g., Phonetic → Dayi)
  - Verify `handleIMSelection(index)` callback invoked
  - Assert: SearchServer.setImInfo() called with new IM code
  - Verify keyboard re-initialized via `initialIMKeyboard()`
  - Test UI refresh after IM switch (view updates)

- [x] **Test: IM picker with empty IM list** - `test_8_7_7_IMPickerEmptyList` ✅
  - Simulate scenario with no activated IMs in database
  - Trigger `showIMPicker()`
  - Assert: empty list handled gracefully (no crash)
  - Verify user notified (toast or dialog message)
  - Test fallback behavior

- [x] **Test: IM picker dialog dismissal** - `test_8_7_8_IMPickerDialogDismissal` ✅
  - Open IM picker → user cancels dialog
  - Verify no IM switch occurs
  - Assert: current IM remains active
  - Test dialog dismissed correctly
  - Verify no state changes on cancellation

- [x] **Test: buildActivatedIMList() filtering** - `test_8_7_9_BuildActivatedIMListFiltering` ✅
  - Populate database with 5 IMs (3 enabled, 2 disabled in preferences)
  - Trigger `buildActivatedIMList()`
  - Assert: only enabled IMs appear in list (3 items)
  - Verify preference filter application
  - Test current IM matching and highlighting
  - Assert: list ordering matches database order

**8.7.3 IM Switching Logic** (3 tests)

- [x] **Test: Switch to next activated IM (forward)** - `test_8_7_10_SwitchToNextIMForward` ✅
  - Current IM: Phonetic, activated list: [Phonetic, Dayi, CJ]
  - Trigger `switchToNextActivatedIM(forward=true)`
  - Assert: IM switches to Dayi (next in list)
  - Verify SearchServer.setImInfo() called
  - Test keyboard reloaded for new IM
  - Verify UI updated to reflect new IM

- [x] **Test: Switch to next activated IM (backward)** - `test_8_7_11_SwitchToNextIMBackward` ✅
  - Current IM: Dayi, activated list: [Phonetic, Dayi, CJ]
  - Trigger `switchToNextActivatedIM(forward=false)`
  - Assert: IM switches to Phonetic (previous in list)
  - Test wrap-around at list boundaries
  - Verify state persistence

- [x] **Test: IM switching with single IM** - `test_8_7_12_IMSwitchingSingleIM` ✅
  - Activated list contains only one IM: [Phonetic]
  - Trigger `switchToNextActivatedIM()`
  - Assert: no switch occurs (remain on Phonetic)
  - Verify defensive handling of single-IM scenario
  - Test no crash or error

#### 8.8 Theme and UI Styling ✅ **COMPLETED** (6 tests)

**Objective**: Achieve 90% coverage of LIMEService theme and navigation bar styling subsystems (currently 0% coverage, ~50 instructions).

**Test File**: `LIMEServiceTest.java` (lines 3495-3685)
**Status**: ✅ **COMPLETED** - 6 tests implemented

**8.8.1 Theme Retrieval and Application** (3 tests)

- [x] **Test: Keyboard theme retrieval from preferences** - `test_8_8_1_KeyboardThemeRetrieval` ✅
  - Trigger `getKeyboardTheme()` during keyboard initialization
  - Verify SharedPreferences.getString() called for theme key
  - Assert: theme ID retrieved correctly
  - Test default theme fallback when preference not set
  - Verify theme ID validation

- [x] **Test: Theme application to keyboard view** - `test_8_8_2_ThemeApplicationToKeyboard` ✅
  - Set theme in preferences (e.g., "dark_theme")
  - Trigger `initialViewAndSwitcher()` → `getKeyboardTheme()`
  - Assert: theme resource ID loaded
  - Verify theme applied to keyboard view
  - Test theme change on preference update

- [x] **Test: Invalid theme ID handling** - `test_8_8_3_InvalidThemeIDHandling` ✅
  - Set invalid theme ID in preferences
  - Trigger `getKeyboardTheme()`
  - Assert: fallback to default theme
  - Verify no crash with invalid resource ID
  - Test defensive validation logic

**8.8.2 Navigation Bar Styling** (3 tests)

- [x] **Test: Navigation bar icon styling on input start** - `test_8_8_4_NavigationBarIconStyling` ✅
  - Trigger `onStartInputView()` → `setNavigationBarIconsDark()`
  - Verify WindowInsetsController usage (Android API level check)
  - Assert: APPEARANCE_LIGHT_NAVIGATION_BARS set based on theme
  - Test light icon mode for dark theme
  - Verify navigation bar appearance updated
  - Test 6 themes: Light, Dark, Pink, TechBlue, FashionPurple, RelaxGreen

- [x] **Test: Navigation bar styling API level compatibility** - `test_8_8_5_NavigationBarStylingAPILevel` ✅
  - Test `setNavigationBarIconsDark()` on different Android versions
  - Verify API level checks (Build.VERSION.SDK_INT)
  - Assert: feature skipped gracefully on older Android
  - Test no crash on unsupported API levels
  - Test theme index updates for all 6 themes

- [x] **Test: Navigation bar styling exception handling** - `test_8_8_6_NavigationBarStylingException` ✅
  - Mock SecurityException during navigation bar API call
  - Trigger `setNavigationBarIconsDark()`
  - Assert: exception caught and handled gracefully
  - Verify IME continues functioning despite styling error
  - Test fallback behavior

**Test Coverage**: 88 test methods total (29 existing in 8.1-8.5 + 15 LIMEService↔SearchServer + 12 SearchServer regression + 15 voice input + 12 IME selection + 6 theme)

- 8.1 Soft Keyboard Input: 2 tests ✅ COMPLETED
- 8.2 Hard Keyboard Input: 1 test ✅ COMPLETED
- 8.3 Query and Caching: 1 test ✅ COMPLETED
- 8.4 Learning Path Integration: 27 tests ✅ COMPLETED
- 8.5 IM Switching: 1 test ✅ COMPLETED
- 8.6 Voice Input Integration: 15 tests (6 launch/switching + 4 error handling + 5 UI integration) ✅ **COMPLETED**
- 8.7 IME Selection and Options Menu: 12 tests (4 options menu + 5 IM picker + 3 IM switching) ✅ **COMPLETED**
- 8.8 Theme and UI Styling: 6 tests (3 theme + 3 navigation bar) ✅ **COMPLETED**

**Note**: Phase 8 regression tests provide comprehensive end-to-end coverage of:
1. **Core IME workflows** (8.1-8.5): User input → candidate selection → learning with real IM data
2. **LIMEService peripheral features** (8.6-8.8): Voice input, IME selection UI, theme/styling

All tests use real-world production IM data (PHONETIC/DAYI tables) to ensure regression coverage matches actual user workflows. Additional coverage improvements will come from Phase 3.16-3.17 (SearchServer unit tests) and Phase 5.27-5.31 (LIMEService unit/integration tests).

**Coverage Improvement Estimate**:
- **SearchServer**: Current 54% → Target 90% (+36%, ~1,300 instructions covered via Phase 3.16-3.17 tests)
- **LIMEService**: Current 38% → Target 90% (+52%, ~3,800 instructions covered via Phase 5.27-5.31 + Phase 8.6-8.8 tests)
- **Overall Project**: Current 41% → Target 75%+ (+34%, ~5,100 instructions)

---

### Phase 9: Performance Tests

**Objective**: Ensure no performance regression using real-world production data.

**Data Strategy**: All Phase 9 tests use **real-world production IM table data** downloaded from cloud storage, matching the Phase 8 RegressionTest approach. This ensures realistic performance measurements on actual production-size datasets rather than synthetic test data.

**Setup (@BeforeClass)**:
- Downloads PHONETIC IM table from cloud if not present (`LIME.DATABASE_CLOUD_IM_PHONETIC`)
- Downloads DAYI IM table from cloud if not present (`LIME.DATABASE_CLOUD_IM_DAYI`)
- Verifies both tables have records before running tests
- Reuses existing tables if already populated

**Test Data Sources**:
- **PHONETIC table**: Used for count, backup/import, export, and memory leak tests
- **DAYI table**: Used for search and import operations tests
- Both tables contain production-size datasets (thousands of real IM entries)

#### 9.1 Database Operation Benchmarks

- [x] **Benchmark: Count operations** - `test_9_1_1_benchmarkCountOperations()`
  - Measure performance on real PHONETIC IM table with production data
  - Tests `countRecords()` on actual large table
  - Target: No more than 100ms per operation
  - ✅ Implemented in `PerformanceTest.java` using real-world data

- [x] **Benchmark: Search operations** - `test_9_1_2_benchmarkSearchOperations()`
  - Compare search through `SearchServer` vs direct `LimeDB` on real DAYI table
  - Measure SearchServer overhead with production data
  - Target: Less than 50ms per operation, less than 20% overhead
  - ✅ Implemented in `PerformanceTest.java` using real-world data

- [x] **Benchmark: Backup/import operations** - `test_9_1_3_benchmarkBackupImportOperations()`
  - Test backup and import on real PHONETIC IM table
  - Measure time for production-size databases
  - Target: Less than 1000ms per operation
  - ✅ Implemented in `PerformanceTest.java` using real-world data

#### 9.2 File Operation Benchmarks

- [x] **Benchmark: Export operations** - `test_9_2_1_benchmarkExportOperations()`
  - Measure time for exporting real PHONETIC IM table
  - Tests `DBServer.exportZippedDb()` with production data
  - Target: Less than 2000ms per operation
  - ✅ Implemented in `PerformanceTest.java` using real-world data

- [x] **Benchmark: Import operations** - `test_9_2_2_benchmarkImportOperations()`
  - Measure time for importing real DAYI IM table from exported file
  - Tests `DBServer.importDb()` with production-size files
  - Target: Less than 2000ms per operation
  - ✅ Implemented in `PerformanceTest.java` using real-world data

#### 9.3 Memory Usage

- [x] **Test: Memory leaks** - `test_9_3_1_testMemoryLeaks()`
  - Test long-running operations (100 iterations) on real PHONETIC table
  - Performs search, count, add/update/delete operations
  - Monitor memory usage with Runtime API
  - Verify proper resource cleanup
  - Target: Less than 5MB increase for 100 iterations
  - ✅ Implemented in `PerformanceTest.java` using real-world data

---

## Test Implementation Todo List

### Step 1: Create Test Infrastructure
- [ ] Create test database fixtures
- [ ] Create mock objects for dependencies
- [ ] Create test data generators
- [ ] Create assertion helpers
- [ ] Create `LimeDBTestBase` - Common setup for LimeDB tests
- [ ] Create `DBServerTestBase` - Common setup for DBServer tests
- [ ] Create `SearchServerTestBase` - Common setup for SearchServer tests
- [ ] Create `UIComponentTestBase` - Common setup for UI tests

### Step 2: Implement Unit Tests (Phases 1-3) - Priority: High

#### 2.1 LimeDB Tests (Phase 1) - ✅ COMPLETED
- [x] Implement core operations tests (`countRecords()`, `addRecord()`, `updateRecord()`, `deleteRecord()`) - ✅ All implemented
- [x] Implement unified methods tests (wrapper methods) - ✅ All implemented
- [x] Implement helper methods tests (`buildWhereClause()`, `queryWithPagination()`) - ✅ All implemented
- [x] Implement table validation tests (`isValidTableName()`) - ✅ All implemented
- [x] Implement backup/import operations tests (`prepareBackup()`, `importDb()`) - ✅ All implemented
- [x] Implement `getBackupTableRecords()` tests - ✅ All implemented
- [x] Implement SQL injection prevention tests - ✅ All implemented

#### 2.2 DBServer Tests (Phase 2)
- [x] Implement export operations tests (`exportZippedDb()`, `exportZippedDbRelated()`) - All tests implemented
- [x] Implement import/export pair tests with data consistency verification - All pair tests implemented
- [x] Implement backup/restore pair tests with data consistency verification - All pair tests implemented
- [x] Download and import integration tests - All tests implemented
- [ ] Implement method delegation tests

#### 2.3 SearchServer Tests (Phase 3) - ✅ COMPLETED
- [x] Create `SearchServerTest.java` file - ✅ Created
- [x] Implement UI-compatible methods tests (`getIm()`, `getKeyboard()`, `getImInfo()`, `setImInfo()`, `setIMKeyboard()`, `isValidTableName()`) - ✅ Implemented
- [x] Implement delegation tests (verify methods delegate to `LimeDB`) - ✅ Implemented
- [x] Implement null `dbadapter` handling tests - ✅ Implemented (all methods tested)
- [x] Implement converter integration tests (`hanConvert()`, `emojiConvert()`) - ✅ Implemented
- [x] Implement search operations tests (`getMappingByCode()`, `getRecords()`, `getRelated()`) - ✅ Implemented
- [x] Implement related phrase operations tests (`countRecordsRelated()`, `getRelatedByWord()`, `getRelatedById()`) - ✅ Implemented
- [x] Implement backup/restore operations tests (`backupUserRecords()`, `restoreUserRecords()`, `checkBackuptable()`, `getBackupTableRecords()`) - ✅ Implemented
- [x] Implement record management tests (`addRecord()`, `updateRecord()`, `deleteRecord()`, `clearTable()`) - ✅ Implemented
- [x] Implement IM info management tests (`removeImInfo()`, `resetImInfo()`, `resetLimeSetting()`, `resetCache()`) - ✅ Implemented

### Step 3: Implement Integration Tests (Phase 5) - Priority: High
- [ ] Create `IntegrationTest.java` file
- [ ] Implement `SearchServer` → `LimeDB` integration tests
- [ ] Implement `DBServer` → `LimeDB` integration tests
- [ ] Implement UI → `SearchServer` → `LimeDB` integration tests
- [ ] Implement UI → `DBServer` → `LimeDB` integration tests
- [ ] Test complete operation flows
- [ ] Test error propagation between layers

### Step 4: Implement Architecture Compliance Tests (Phase 6) - Priority: High
- [ ] Create `ArchitectureComplianceTest.java` file
- [ ] Implement static analysis: No direct `LimeDB` access from UI components
- [ ] Implement static analysis: No SQL operations outside `LimeDB.java`
- [ ] Implement static analysis: No file operations outside `DBServer.java`
- [ ] Implement runtime verification: Component initialization checks
- [ ] Implement runtime verification: Method call tracing
- [ ] Implement automated violation detection

### Step 5: Implement UI Component Tests (Phase 4) - Priority: Medium
- [ ] Update `SetupImFragmentTest.java` - Add architecture compliance tests
- [ ] Create `ManageImFragmentTest.java` - Architecture compliance and functionality
- [ ] Create `ManageRelatedFragmentTest.java` - Architecture compliance and functionality
- [ ] Create `ManageImKeyboardDialogTest.java` - Architecture compliance and functionality
- [ ] Create `ImportDialogTest.java` - Architecture compliance and functionality
- [ ] Create `ShareDialogTest.java` - Architecture compliance and functionality
- [ ] Create `SetupImLoadDialogTest.java` - Architecture compliance and functionality
- [ ] Test architecture compliance for each UI component (uses `SearchServer`/`DBServer`, not `LimeDB`)
- [ ] Test functional behavior for each component
- [ ] Test integration with `SearchServer`/`DBServer`

### Step 6: Implement Regression Tests (Phase 7) - Priority: High
- [ ] Implement core functionality tests (IM configuration, record dictionary, related phrases)
- [ ] Implement file import/export regression tests
- [ ] Implement Chinese conversion regression tests
- [ ] Implement emoji conversion regression tests
- [ ] Implement edge case tests (null handling, empty data, invalid input)

### Step 7: Implement Performance Tests (Phase 8) - Priority: Medium
- [ ] Create `PerformanceTest.java` file
- [ ] Implement database operation benchmarks (`countRecords()`, search operations)
- [ ] Implement file operation benchmarks (export/import)
- [ ] Implement memory usage tests
- [ ] Compare before/after performance
- [ ] Monitor for regressions

---

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

