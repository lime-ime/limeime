# Test Plan: Architecture Refactoring Validation

## Executive Summary

This document outlines a comprehensive testing strategy to validate the refactored LimeIME architecture. The testing plan ensures that all architectural boundaries are respected, all layers function correctly, and the migration from direct `LimeDB` access to `SearchServer`/`DBServer` is complete and correct.

## Test Objectives

1. **Architecture Compliance**: Verify that all components follow the new architecture (no direct `LimeDB` access from UI components)
2. **Functionality Preservation**: Ensure all existing functionality works after refactoring
3. **Layer Isolation**: Verify each layer (LimeDB, DBServer, SearchServer) works independently
4. **Integration**: Test interactions between layers
5. **Performance**: Ensure no performance regression
6. **Code Quality**: Verify parameterized queries, proper error handling, resource management

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
- `exportZippedDb(String imType, File targetFile, LIMEProgressListener progressListener)`
- `exportZippedDbRelated(File targetFile, LIMEProgressListener progressListener)`

**File Import Operations:**
- `importZippedDb(File compressedSourceDB, String imtype)`
- `importZippedDbRelated(File compressedSourceDB)`
- `importDb(File sourceDBFile, String imType)`
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
- `getImInfo(String im, String field)`
- `setImInfo(String im, String field, String value)`
- `setIMKeyboard(String im, String value, String keyboard)`
- `setIMKeyboard(String im, Keyboard keyboard)`
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

## Architecture Overview

```
LIMEService → SearchServer → LimeDB → SQLiteDatabase (main)
                              ├─> LimeHanConverter → SQLiteDatabase (hanconvertv2.db)
                              └─> EmojiConverter → SQLiteDatabase (emoji.db)

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

#### ❌ UI Component Tests (7+ files)

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
      - Architecture compliance (uses `SearchServer` and `DBServer`, not `LimeDB`)
      - File download and import flow
      - Progress handling
    - **Priority**: **LOW**

11. **`ManageImAddDialogTest.java`** ❌ (Optional)
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ManageImAddDialog`
    - **Priority**: **LOW**

12. **`ManageImEditDialogTest.java`** ❌ (Optional)
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ManageImEditDialog`
    - **Priority**: **LOW**

13. **`ManageRelatedAddDialogTest.java`** ❌ (Optional)
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ManageRelatedAddDialog`
    - **Priority**: **LOW**

14. **`ManageRelatedEditDialogTest.java`** ❌ (Optional)
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ManageRelatedEditDialog`
    - **Priority**: **LOW**

#### ❌ Runnable Class Tests (7 files)

15. **`SetupImLoadRunnableTest.java`** ❌
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `SetupImLoadRunnable`
    - **Required Tests**:
      - Architecture compliance (uses `SearchServer` and `DBServer`, not `LimeDB`)
      - Download and import flow via `LIMEUtilities.downloadRemoteFile()` and `DBServer.importZippedDb()`
      - Backup restoration via `SearchServer.restoreUserRecords()` (delegates to LimeDB)
      - Backup data retrieval via `SearchServer.getBackupTableRecords()` (delegates to LimeDB)
      - Record updates via `SearchServer.addOrUpdateMappingRecord()` (delegates to LimeDB)
      - **Note**: These are new methods added to SearchServer that delegate to LimeDB
    - **Priority**: **MEDIUM**

16. **`ShareDbRunnableTest.java`** ❌
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ShareDbRunnable`
    - **Required Tests**:
      - Architecture compliance (uses `DBServer.exportZippedDb()`, not direct file ops)
    - **Priority**: **LOW**

17. **`ShareRelatedDbRunnableTest.java`** ❌
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ShareRelatedDbRunnable`
    - **Required Tests**:
      - Architecture compliance (uses `DBServer.exportZippedDbRelated()`, not direct file ops)
    - **Priority**: **LOW**

18. **`ShareTxtRunnableTest.java`** ❌ (Optional)
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ShareTxtRunnable`
    - **Priority**: **LOW**

19. **`ShareRelatedTxtRunnableTest.java`** ❌ (Optional)
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ShareRelatedTxtRunnable`
    - **Priority**: **LOW**

20. **`ManageImRunnableTest.java`** ❌ (Optional)
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ManageImRunnable`
    - **Priority**: **LOW**

21. **`ManageRelatedRunnableTest.java`** ❌ (Optional)
    - **Location**: `app/src/androidTest/java/net/toload/main/hd/ui/`
    - **Purpose**: Tests for `ManageRelatedRunnable`
    - **Priority**: **LOW**

### Test File Summary

| Category | Existing | Missing | Total Needed |
|----------|----------|---------|--------------|
| **Core Layer Tests** | 5 | 0 | 5 |
| **Architecture Tests** | 0 | 1 | 1 |
| **Integration Tests** | 0 | 1 | 1 |
| **Performance Tests** | 0 | 1 | 1 |
| **UI Fragment Tests** | 1 | 2 | 3 |
| **UI Dialog Tests** | 0 | 5-9 | 5-9 |
| **Runnable Tests** | 0 | 2-7 | 2-7 |
| **TOTAL** | **6** | **11-21** | **17-27** |

### Test File Priority

#### 🔴 **CRITICAL** (Must Have)
1. `SearchServerTest.java` - ✅ Central interface layer - COMPLETED
2. `ArchitectureComplianceTest.java` - Architecture validation
3. `IntegrationTest.java` - Layer interaction validation

#### 🟡 **HIGH** (Should Have)
4. `ManageImFragmentTest.java` - Major UI component
5. Update `SetupImFragmentTest.java` - Add architecture compliance tests

#### 🟢 **MEDIUM** (Nice to Have)
6. `ManageRelatedFragmentTest.java`
7. `ManageImKeyboardDialogTest.java`
8. `ImportDialogTest.java`
9. `ShareDialogTest.java`
10. `SetupImLoadRunnableTest.java`

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

- [x] **Test: `exportZippedDb(String imType, File targetFile, Runnable progressCallback)`** - `testDBServerExportImDatabaseWithValidImType()`, `testDBServerExportImDatabaseWithInvalidImType()`, `testDBServerExportImDatabaseWithProgressCallback()`, `testDBServerExportZippedDbWithNullImType()`, `testDBServerExportZippedDbWithNullTargetFile()`, `testDBServerExportZippedDbWithDataIntegrity()`, `testDBServerExportZippedDbWithExistingTargetFile()` added
  - Test exporting single IM database
  - Test with valid imType (e.g., "custom", "cj", "phonetic")
  - Test with invalid imType (should fail gracefully)
  - Test with null imType (should fail gracefully)
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

- [x] **Test: `importDb(File sourcedb, String imtype)`** - `testDBServerImportDbWithUncompressedDatabase()`, `testDBServerImportDbWithNullSourceDb()`, `testDBServerImportDbWithNonExistentFile()` added
  - Test importing uncompressed database file
  - Test with valid imType
  - Test with invalid imType (should fail gracefully)
  - Test with null sourcedb (should fail gracefully)
  - Test with non-existent file (should fail gracefully)
  - Test with invalid database file format (should fail gracefully)
  - Test data integrity after import (records match source)
  - Test overwrite behavior (existing records replaced)
  - Test cache reset after import

- [x] **Test: `importZippedDb(File compressedSourceDB, String imtype)`**
  - Test importing compressed database (.limedb) file
  - Test unzip and import flow
  - Test with valid imType
  - Test with invalid imType (should fail gracefully)
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
  - Test with (String im, String value, String keyboard)
  - Test with (String im, Keyboard keyboard)
  - Test delegation to `LimeDB.setIMKeyboard()` / `setImKeyboard()`
  - Test with null dbadapter (should fail gracefully)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `isValidTableName()`**
  - Test delegation to `LimeDB.isValidTableName()`
  - Test with valid table names
  - Test with invalid table names
  - Test with null dbadapter (should return false)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `getImList(String code)`**
  - Test retrieving IM list for specific code
  - Test delegation to `LimeDB.getImList()`
  - Test with null dbadapter (should return null)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `removeImInfo(String im, String field)`**
  - Test removing IM info field
  - Test delegation to `LimeDB.removeImInfo()`
  - Test with null dbadapter (should fail gracefully)
  - **Status**: Tests implemented in SearchServerTest.java

- [x] **Test: `resetImInfo(String im)`**
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

- [x] **Test: `getKeyboardCode(String im)`**
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

- [x] **Test: `exportTxtTable(String table, File targetFile, List<Im> imInfo)`**
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
- `getImList()` - Returns ImObj list (throws RemoteException, internal use)
- `clear()` - Clears cache (throws RemoteException, internal use)
- `getEnglishSuggestions(String word)` - Gets English suggestions (throws RemoteException, internal use)
- `getSelkey()` - Gets selection key (throws RemoteException, internal use)
- `checkPhoneticKeyboardSetting()` - Checks phonetic keyboard setting (internal use)

---

### Phase 4: UI Component Tests

**Objective**: Verify UI components use `SearchServer` and `DBServer`, not direct `LimeDB` access.

#### 4.1 Architecture Compliance Tests

- [ ] **Test: No LimeDB instances in UI components**
  - Verify `SetupImFragment` uses `SearchServer` only
  - Verify `ManageImFragment` uses `SearchServer` only
  - Verify `ManageImKeyboardDialog` uses `SearchServer` only
  - Verify `ImportDialog` uses `SearchServer` only
  - Verify `ShareDialog` uses `SearchServer` only
  - Verify `SetupImLoadRunnable` uses `SearchServer` and `DBServer`
  - Verify `MainActivity` uses `SearchServer` only
  - (Use static analysis or reflection to verify)

#### 4.2 SetupImFragment Tests

- [ ] **Test: Fragment initialization**
  - Test `SearchServer` is created
  - Test no `LimeDB` instance exists
  - Test `initialbutton()` uses `SearchServer.getIm()`

- [ ] **Test: Button state management**
  - Test buttons enabled/disabled based on table existence
  - Test uses `SearchServer.countMapping()` to check table status
  - Test button click handlers work correctly

#### 4.3 ManageImFragment Tests

- [ ] **Test: IM list loading**
  - Test uses `SearchServer.getIm()` to load IMs
  - Test uses `SearchServer.getKeyboard()` to load keyboards
  - Test no direct `LimeDB` access

- [ ] **Test: Record management**
  - Test uses `SearchServer.getRecords()` for record loading (delegates to `LimeDB.getRecords()`)
  - Test uses `SearchServer.getRecordSize()` for count
  - Test uses `SearchServer.deleteRecord()` for deletion

#### 4.4 ManageImKeyboardDialog Tests

- [ ] **Test: Keyboard assignment**
  - Test uses `SearchServer.getKeyboard()` to load keyboards
  - Test uses `SearchServer.setIMKeyboard()` to assign keyboard
  - Test no direct `LimeDB` access

#### 4.5 ImportDialog Tests

- [ ] **Test: Import operations**
  - Test uses `SearchServer.getIm()` to get IM list
  - Test uses `SearchServer.countMapping()` to check table status
  - Test uses `DBServer` for file import operations
  - Test import mode (text vs file) handling

#### 4.6 ShareDialog Tests

- [ ] **Test: Share operations**
  - Test uses `SearchServer.getIm()` to get IM list
  - Test uses `DBServer.exportZippedDb()` for database export
  - Test uses `DBServer.exportZippedDbRelated()` for related export

#### 4.7 SetupImLoadRunnable Tests

- [x] **Test: Download and import flow** (refactored)
  - Test uses `LIMEUtilities.downloadRemoteFile()` and `DBServer.importZippedDb()` for complete flow
  - Test uses `SearchServer.restoreUserRecords()` for backup restoration
  - Test uses `SearchServer.getBackupTableRecords()` for backup data retrieval
  - Test restore preference logic

#### 4.8 MainActivity Tests

- [ ] **Test: File import handling**
  - Test uses direct ContentResolver operations for downloads
  - Test uses `SearchServer.isValidTableName()` for validation
  - Test uses `SearchServer.getIm()` for IM list

---

### Phase 5: Integration Tests

**Objective**: Test interactions between layers with real implementations.

#### 5.1 SearchServer → LimeDB Integration

- [ ] **Test: Complete search flow**
  - Test `SearchServer.getMappingByCode()` → `LimeDB.getRecords()`
  - Test caching behavior
  - Test error handling and propagation

- [ ] **Test: Configuration operations**
  - Test `SearchServer.setImInfo()` → `LimeDB.setImInfo()`
  - Test `SearchServer.getImInfo()` → `LimeDB.getImInfo()`
  - Test data persistence

#### 5.2 DBServer → LimeDB Integration

- [ ] **Test: Export flow**
  - Test `DBServer.exportZippedDb()` → `LimeDB.prepareBackup()`
  - Test file creation and zip integrity
  - Test data completeness

- [ ] **Test: Import flow**
  - Test `DBServer.importZippedDb()` → `LimeDB.importDb()`
  - Test `DBServer.importZippedDbRelated()` → `LimeDB.importDbRelated()`
  - Test data integrity after import
  - Test overwrite behavior

#### 5.3 UI → SearchServer → LimeDB Integration

- [ ] **Test: Complete UI operation flow**
  - Test UI component → `SearchServer` → `LimeDB` → Database
  - Test data flow and transformations
  - Test error handling at each layer

#### 5.4 UI → DBServer → LimeDB Integration

- [ ] **Test: Complete file operation flow**
  - Test UI component → `DBServer` → `LimeDB` → Database
  - Test file operations and database updates
  - Test progress callbacks

---

### Phase 6: Architecture Compliance Tests

**Objective**: Verify architectural boundaries are respected.

#### 6.1 Static Analysis Tests

- [ ] **Test: No direct LimeDB access from UI**
  - Use static analysis to find `new LimeDB()` in UI package
  - Verify all instances are in `SearchServer` or `DBServer` only
  - Report any violations

- [ ] **Test: No SQL operations outside LimeDB**
  - Search for SQL keywords (`execSQL`, `rawQuery`, `query`, `insert`, `update`, `delete`) in non-LimeDB files
  - Verify all SQL is in `LimeDB.java` (except `LimeHanConverter` and `EmojiConverter`)
  - Report any violations

- [ ] **Test: No file operations outside DBServer**
  - Search for file operations (`FileOutputStream`, `FileInputStream`, `LIMEUtilities.zip`, `LIMEUtilities.unzip`) in non-DBServer files
  - Verify all file operations are in `DBServer.java` (except utilities)
  - Report any violations

#### 6.2 Runtime Architecture Tests

- [ ] **Test: Component initialization**
  - Verify UI components initialize `SearchServer`, not `LimeDB`
  - Verify `LIMEService` initializes `SearchServer`, not `LimeDB`
  - Use reflection to inspect component fields

- [ ] **Test: Method call tracing**
  - Trace method calls from UI components
  - Verify calls go through `SearchServer` or `DBServer`
  - Verify no direct calls to `LimeDB` from UI

---

### Phase 7: Regression Tests

**Objective**: Ensure existing functionality still works.

#### 7.1 Core Functionality Tests

- [ ] **Test: IM configuration**
  - Test creating new IM
  - Test updating IM settings
  - Test deleting IM
  - Test keyboard assignment

- [ ] **Test: Word dictionary operations**
  - Test adding records
  - Test searching records
  - Test updating record scores
  - Test deleting records
  - Test pagination

- [ ] **Test: Related phrase operations**
  - Test adding related phrases
  - Test searching related phrases
  - Test updating related phrases
  - Test deleting related phrases

- [x] **Test: File import/export**
  - Test importing `.lime` files
  - Test importing `.cin` files
  - Test importing `.limedb` files
  - Test exporting databases
  - Test backup/restore
  - Test exporting related table to text format (`pword|cword|basescore|userscore`)
  - Test importing related table from text format (supports pipe-delimited format)

- [ ] **Test: Chinese conversion**
  - Test `hanConvert()` through `SearchServer`
  - Test various input strings
  - Test conversion options

- [ ] **Test: Emoji conversion**
  - Test `emojiConvert()` through `SearchServer`
  - Test various input strings

#### 7.2 Edge Cases

- [ ] **Test: Null handling**
  - Test all methods with null parameters
  - Verify graceful error handling
  - Verify no NullPointerException

- [ ] **Test: Empty data**
  - Test operations on empty tables
  - Test operations on empty files
  - Verify correct behavior

- [ ] **Test: Invalid input**
  - Test with invalid table names
  - Test with invalid file formats
  - Test with invalid parameters
  - Verify error handling

---

### Phase 8: Performance Tests

**Objective**: Ensure no performance regression.

#### 8.1 Database Operation Benchmarks

- [ ] **Benchmark: Count operations**
  - Compare `countRecords()` vs old `countMapping()`
  - Measure performance on large tables
  - Target: No more than 5% regression

- [ ] **Benchmark: Search operations**
  - Compare search through `SearchServer` vs direct `LimeDB`
  - Measure cache hit rates
  - Target: Same or better performance

- [ ] **Benchmark: Backup/import operations**
  - Compare new unified methods vs old methods
  - Measure time for large databases
  - Target: No more than 5% regression

#### 8.2 File Operation Benchmarks

- [ ] **Benchmark: Export operations**
  - Measure time for exporting large databases
  - Compare new `DBServer` methods vs old Runnable methods
  - Target: Same or better performance

- [ ] **Benchmark: Import operations**
  - Measure time for importing large files
  - Compare new `DBServer` methods vs old methods
  - Target: Same or better performance

#### 8.3 Memory Usage

- [ ] **Test: Memory leaks**
  - Test long-running operations
  - Monitor memory usage
  - Verify proper resource cleanup
  - Target: No memory leaks

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

- **LimeDB**: 90%+ coverage
- **DBServer**: 85%+ coverage
- **SearchServer**: 85%+ coverage (new methods)
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
- [ ] All integration tests pass
- [ ] Architecture compliance tests pass (no violations)
- [ ] All UI component tests pass
- [ ] All regression tests pass
- [ ] Performance benchmarks meet targets
- [ ] Code coverage meets targets
- [x] Documentation is updated (TEST_PLAN_ARCHITECTURE.md updated)

---

**Document Version**: 1.1  
**Last Updated**: 2025-01-XX  
**Status**: In Progress - SearchServer tests completed, LimeDB export/import tests completed

