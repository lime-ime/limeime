# Function Call Chains: LimeDB.java and DBServer.java

## Overview

This document provides detailed function call chains for all major operations in `LimeDB.java`, `DBServer.java`, and `SearchServer.java`, showing both the **BEFORE** (current state) and **AFTER** (post-refactoring) states side by side. These chains help understand dependencies and demonstrate the improvements from refactoring.

**Note**: 
- `LimeHanConverter` and `EmojiConverter` use separate databases (`hanconvertv2.db` and `emoji.db` respectively) and are **excluded from refactoring**. They remain as independent helper classes.
- `SearchServer` is a search/caching layer that uses `LimeDB` for all database operations. It does not need refactoring as it already follows the correct pattern (no SQL operations outside `LimeDB`).

---

## LimeDB.java Call Chains

### 1. Database Initialization Chain

**BEFORE:**
```
LimeDB(Context context)
  ├─> super(context, LIME.DATABASE_NAME, DATABASE_VERSION)
  │   └─> SQLiteOpenHelper constructor
  ├─> mLIMEPref = new LIMEPreferenceManager(context)
  ├─> blackListCache = new ConcurrentHashMap<>()
  └─> openDBConnection(true)
      ├─> this.getWritableDatabase()
      │   ├─> onCreate(db) [if first time]
      │   │   └─> [Create all tables]
      │   └─> onUpgrade(db, oldVersion, newVersion) [if version changed]
      │       └─> checkAndUpdateRelatedTable()
      │           ├─> rawQuery("SELECT basescore FROM related")
      │           ├─> db.execSQL("ALTER TABLE related ADD basescore INTEGER")
      │           ├─> db.query("sqlite_master", "type='index' and name='related_idx_pword'")
      │           └─> db.execSQL("create index 'related_idx_pword' on related (pword)")
      └─> db = this.getWritableDatabase()
```

**AFTER:**
```
LimeDB(Context context)
  ├─> super(context, LIME.DATABASE_NAME, DATABASE_VERSION)
  │   └─> SQLiteOpenHelper constructor
  ├─> mLIMEPref = new LIMEPreferenceManager(context)
  ├─> blackListCache = new ConcurrentHashMap<>()
  └─> openDBConnection(true)
      ├─> this.getWritableDatabase()
      │   ├─> onCreate(db) [if first time]
      │   │   └─> [Create all tables]
      │   └─> onUpgrade(db, oldVersion, newVersion) [if version changed]
      │       └─> checkAndUpdateRelatedTable()
      │           ├─> rawQuery("SELECT basescore FROM related")
      │           ├─> db.execSQL("ALTER TABLE related ADD basescore INTEGER")
      │           ├─> db.query("sqlite_master", "type='index' and name='related_idx_pword'")
      │           └─> db.execSQL("create index 'related_idx_pword' on related (pword)")
      └─> db = this.getWritableDatabase()
```

**Status**: No changes - already well-structured

---

### 2. File Loading Chain

**BEFORE:**
```
importTxtTable(String table, LIMEProgressListener progressListener)
  ├─> checkDBConnection()
  │   └─> openDBConnection(false)
  ├─> countMapping(table)
  │   ├─> checkDBConnection()
  │   ├─> isValidTableName(table)
  │   └─> db.rawQuery("SELECT * FROM " + table, null)
  ├─> db.delete(table, null, null) [if count > 0]
  ├─> resetImInfo(table)
  │   └─> db.execSQL("DELETE FROM im WHERE code='" + im + "'")
  ├─> identifyDelimiter(fileLines) [private method]
  │   └─> [Count delimiter occurrences]
  ├─> holdDBConnection()
  ├─> db.beginTransaction()
  ├─> [File Reading Loop]
  │   ├─> getBaseScore(record)
  │   │   └─> hanConverter.getBaseScore(record) [Separate DB - unchanged]
  │   ├─> db.insert(table, null, cv)
  │   └─> [Progress updates]
  ├─> db.setTransactionSuccessful()
  ├─> db.endTransaction()
  ├─> unHoldDBConnection()
  ├─> setImInfo(table, "source", filename)
  │   ├─> checkDBConnection()
  │   ├─> removeImInfo(im, field)
  │   │   └─> removeImInfoOnDB(db, im, field)
  │   │       └─> db.delete("im", whereClause, whereArgs)
  │   └─> db.insert("im", null, cv)
  ├─> setImInfo(table, "name", imname)
  ├─> setImInfo(table, "amount", String.valueOf(count))
  ├─> setImInfo(table, "import", new Date().toString())
  └─> setIMKeyboard(table, kobj.getDescription(), kobj.getCode())
      ├─> checkDBConnection()
      └─> setIMKeyboardOnDB(db, im, value, keyboard)
          ├─> removeImInfoOnDB(dbin, im, LIME.DB_KEYBOARD)
          └─> dbin.insert(LIME.DB_TABLE_IM, null, cv)
```

**AFTER:**
```
importTxtTable(String table, LIMEProgressListener progressListener)
  ├─> checkDBConnection()
  │   └─> openDBConnection(false)
  ├─> countRecords(table, null, null) [NEW - Unified count method]
  │   ├─> checkDBConnection()
  │   ├─> isValidTableName(table)
  │   └─> db.rawQuery("SELECT COUNT(*) as count FROM " + table, null)
  ├─> db.delete(table, null, null) [if count > 0]
  ├─> resetImInfo(table)
  │   └─> db.execSQL("DELETE FROM im WHERE code='" + im + "'")
  ├─> identifyDelimiter(fileLines) [private method]
  │   └─> [Count delimiter occurrences]
  ├─> holdDBConnection()
  ├─> db.beginTransaction()
  ├─> [File Reading Loop]
  │   ├─> getBaseScore(record)
  │   │   └─> hanConverter.getBaseScore(record) [Separate DB - unchanged]
  │   ├─> addRecord(table, cv) [Parameterized insert - already using]
  │   └─> [Progress updates]
  ├─> db.setTransactionSuccessful()
  ├─> db.endTransaction()
  ├─> unHoldDBConnection()
  ├─> setImInfo(table, "source", filename)
  │   ├─> checkDBConnection()
  │   ├─> removeImInfo(im, field)
  │   │   └─> removeImInfoOnDB(db, im, field)
  │   │       └─> db.delete("im", whereClause, whereArgs)
  │   └─> db.insert("im", null, cv)
  ├─> setImInfo(table, "name", imname)
  ├─> setImInfo(table, "amount", String.valueOf(count))
  ├─> setImInfo(table, "import", new Date().toString())
  └─> setIMKeyboard(table, kobj.getDescription(), kobj.getCode())
      ├─> checkDBConnection()
      └─> setIMKeyboardOnDB(db, im, value, keyboard)
          ├─> removeImInfoOnDB(dbin, im, LIME.DB_KEYBOARD)
          └─> dbin.insert(LIME.DB_TABLE_IM, null, cv)
```

**Improvement**: Uses unified `countRecords()` method instead of `countMapping()`

---

### 3. Query Chain - getMappingByCode

**BEFORE:**
```
getMappingByCode(String code, boolean softKeyboard, boolean getAllRecords)
  ├─> checkDBConnection()
  ├─> preProcessingRemappingCode(code)
  │   ├─> mLIMEPref.getPhysicalKeyboardType()
  │   ├─> mLIMEPref.getPhoneticKeyboardType()
  │   ├─> [Build remapping tables]
  │   └─> [Apply remapping]
  ├─> code.toLowerCase(Locale.US)
  ├─> preProcessingForExtraQueryConditions(code)
  │   ├─> buildDualCodeList(code, keytablename)
  │   │   ├─> keysDualMap.get(keytablename)
  │   │   ├─> [Build tree of dual code variants]
  │   │   └─> checkBlackList(code)
  │   │       └─> blackListCache.get(cacheKey(code))
  │   └─> expandDualCode(code, keytablename)
  │       ├─> buildDualCodeList(code, keytablename)
  │       ├─> expandBetweenSearchClause(codeCol, dualcode)
  │       └─> db.query(tableName, col, selectValidCodeClause, ...)
  ├─> expandBetweenSearchClause(codeCol, code)
  │   └─> [Build OR conditions for partial matches]
  ├─> db.rawQuery(selectString, null)
  └─> buildQueryResult(query_code, codeorig, cursor, getAllRecords)
      ├─> getCursorString(cursor, FIELD_WORD)
      ├─> getCursorInt(cursor, FIELD_SCORE)
      ├─> getCursorString(cursor, FIELD_BASESCORE)
      ├─> getCursorString(cursor, FILE_EXACT_MATCH)
      └─> keyToKeyName(code, tableName, composingText) [if needed]
          ├─> getImInfo(table, "imkeys")
          ├─> getImInfo(table, "imkeynames")
          └─> [Convert code to key names]
```

**AFTER:**
```
getMappingByCode(String code, boolean softKeyboard, boolean getAllRecords)
  ├─> checkDBConnection()
  ├─> preProcessingRemappingCode(code)
  │   ├─> mLIMEPref.getPhysicalKeyboardType()
  │   ├─> mLIMEPref.getPhoneticKeyboardType()
  │   ├─> [Build remapping tables]
  │   └─> [Apply remapping]
  ├─> code.toLowerCase(Locale.US)
  ├─> preProcessingForExtraQueryConditions(code)
  │   ├─> buildDualCodeList(code, keytablename)
  │   │   ├─> keysDualMap.get(keytablename)
  │   │   ├─> [Build tree of dual code variants]
  │   │   └─> checkBlackList(code)
  │   │       └─> blackListCache.get(cacheKey(code))
  │   └─> expandDualCode(code, keytablename)
  │       ├─> buildDualCodeList(code, keytablename)
  │       ├─> expandBetweenSearchClause(codeCol, dualcode)
  │       └─> db.query(tableName, col, selectValidCodeClause, ...)
  ├─> expandBetweenSearchClause(codeCol, code)
  │   └─> [Build OR conditions for partial matches]
  ├─> db.rawQuery(selectString, null)
  └─> buildQueryResult(query_code, codeorig, cursor, getAllRecords)
      ├─> getCursorString(cursor, FIELD_WORD)
      ├─> getCursorInt(cursor, FIELD_SCORE)
      ├─> getCursorString(cursor, FIELD_BASESCORE)
      ├─> getCursorString(cursor, FILE_EXACT_MATCH)
      └─> keyToKeyName(code, tableName, composingText) [if needed]
          ├─> getImInfo(table, "imkeys")
          ├─> getImInfo(table, "imkeynames")
          └─> [Convert code to key names]
```

**Status**: No changes - already well-structured, complex query logic is appropriate here

---

### 4. Query Chain - getRelatedPhrase

**BEFORE:**
```
getRelatedPhrase(String pword, boolean getAllRecords)
  ├─> checkDBConnection()
  ├─> mLIMEPref.getSimiliarEnable()
  ├─> [If pword.length() > 1]
  │   └─> db.rawQuery(selectString, null)
  │       └─> selectString = "SELECT ... FROM related WHERE pword = '...' OR pword = '...' ORDER BY ..."
  └─> [Else]
      └─> db.query(LIME.DB_TABLE_RELATED, null, whereClause, ...)
          └─> [Build Mapping objects]
              ├─> getCursorString(cursor, LIME.DB_RELATED_COLUMN_ID)
              ├─> getCursorString(cursor, LIME.DB_RELATED_COLUMN_PWORD)
              ├─> getCursorString(cursor, LIME.DB_RELATED_COLUMN_CWORD)
              ├─> getCursorInt(cursor, LIME.DB_RELATED_COLUMN_USERSCORE)
              └─> getCursorInt(cursor, LIME.DB_RELATED_COLUMN_BASESCORE)
```

**AFTER:**
```
getRelatedPhrase(String pword, boolean getAllRecords)
  ├─> checkDBConnection()
  ├─> mLIMEPref.getSimiliarEnable()
  ├─> buildWhereClause(conditions) [NEW - Reusable helper]
  │   └─> [Build parameterized WHERE clause safely]
  ├─> [If pword.length() > 1]
  │   └─> db.rawQuery(selectString, whereArgs) [NEW - Parameterized]
  │       └─> selectString = "SELECT ... FROM related WHERE pword = ? OR pword = ? ORDER BY ..."
  └─> [Else]
      └─> queryWithPagination(LIME.DB_TABLE_RELATED, whereClause, whereArgs, orderBy, limit, offset) [NEW - Reusable helper]
          ├─> isValidTableName(table)
          └─> db.query(LIME.DB_TABLE_RELATED, null, whereClause, whereArgs, ...)
              └─> [Build Mapping objects]
                  ├─> getCursorString(cursor, LIME.DB_RELATED_COLUMN_ID)
                  ├─> getCursorString(cursor, LIME.DB_RELATED_COLUMN_PWORD)
                  ├─> getCursorString(cursor, LIME.DB_RELATED_COLUMN_CWORD)
                  ├─> getCursorInt(cursor, LIME.DB_RELATED_COLUMN_USERSCORE)
                  └─> getCursorInt(cursor, LIME.DB_RELATED_COLUMN_BASESCORE)
```

**Improvement**: Uses reusable query helpers and parameterized queries

---

### 5. User Dictionary Learning Chain

**BEFORE:**
```
addOrUpdateMappingRecord(String table, String code, String record, int score)
  ├─> checkDBConnection()
  ├─> isMappingExistOnDB(db, table, code, record)
  │   ├─> code.replaceAll("'", "''") [Escape]
  │   └─> db.query(table, null, whereClause, ...)
  │       └─> [If found]
  │           ├─> getCursorString(cursor, FIELD_CODE)
  │           ├─> getCursorString(cursor, FIELD_WORD)
  │           └─> getCursorInt(cursor, FIELD_SCORE)
  ├─> [If not exists]
  │   ├─> removeFromBlackList(code)
  │   │   └─> blackListCache.remove(cacheKey(code))
  │   ├─> [If phonetic table]
  │   │   └─> removeFromBlackList(noToneCode)
  │   └─> db.insert(table, null, cv)
  └─> [If exists]
      └─> db.update(table, cv, whereClause, null)

addOrUpdateRelatedPhraseRecord(String pword, String cword)
  ├─> checkDBConnection()
  ├─> mLIMEPref.getLearnRelatedWord()
  ├─> [Remove Chinese symbols if learning enabled]
  ├─> isRelatedPhraseExistOnDB(db, pword, cword)
  │   └─> db.query(LIME.DB_TABLE_RELATED, null, whereClause, ...)
  │       └─> [If found]
  │           ├─> getCursorString(cursor, LIME.DB_RELATED_COLUMN_ID)
  │           ├─> getCursorString(cursor, LIME.DB_RELATED_COLUMN_PWORD)
  │           ├─> getCursorString(cursor, LIME.DB_RELATED_COLUMN_CWORD)
  │           ├─> getCursorInt(cursor, LIME.DB_RELATED_COLUMN_BASESCORE)
  │           └─> getCursorInt(cursor, LIME.DB_RELATED_COLUMN_USERSCORE)
  ├─> [If not exists]
  │   └─> db.insert(LIME.DB_TABLE_RELATED, null, cv)
  └─> [If exists]
      └─> db.update(LIME.DB_TABLE_RELATED, cv, whereClause, null)
```

**AFTER:**
```
addOrUpdateMappingRecord(String table, String code, String record, int score)
  ├─> checkDBConnection()
  ├─> isValidTableName(table) [NEW - Validation]
  ├─> isMappingExistOnDB(db, table, code, record)
  │   ├─> [Use parameterized query - already safe]
  │   └─> db.query(table, null, whereClause, whereArgs, ...)
  │       └─> [If found]
  │           ├─> getCursorString(cursor, FIELD_CODE)
  │           ├─> getCursorString(cursor, FIELD_WORD)
  │           └─> getCursorInt(cursor, FIELD_SCORE)
  ├─> [If not exists]
  │   ├─> removeFromBlackList(code)
  │   │   └─> blackListCache.remove(cacheKey(code))
  │   ├─> [If phonetic table]
  │   │   └─> removeFromBlackList(noToneCode)
  │   └─> addRecord(table, cv) [NEW - Uses parameterized insert]
  └─> [If exists]
      └─> updateRecord(table, cv, whereClause, whereArgs) [NEW - Uses parameterized update]

addOrUpdateRelatedPhraseRecord(String pword, String cword)
  ├─> checkDBConnection()
  ├─> mLIMEPref.getLearnRelatedWord()
  ├─> [Remove Chinese symbols if learning enabled]
  ├─> isRelatedPhraseExistOnDB(db, pword, cword)
  │   └─> db.query(LIME.DB_TABLE_RELATED, null, whereClause, whereArgs, ...) [Parameterized]
  │       └─> [If found]
  │           ├─> getCursorString(cursor, LIME.DB_RELATED_COLUMN_ID)
  │           ├─> getCursorString(cursor, LIME.DB_RELATED_COLUMN_PWORD)
  │           ├─> getCursorString(cursor, LIME.DB_RELATED_COLUMN_CWORD)
  │           ├─> getCursorInt(cursor, LIME.DB_RELATED_COLUMN_BASESCORE)
  │           └─> getCursorInt(cursor, LIME.DB_RELATED_COLUMN_USERSCORE)
  ├─> [If not exists]
  │   └─> addRecord(LIME.DB_TABLE_RELATED, cv) [NEW - Uses parameterized insert]
  └─> [If exists]
      └─> updateRecord(LIME.DB_TABLE_RELATED, cv, whereClause, whereArgs) [NEW - Uses parameterized update]
```

**Improvement**: Uses centralized `addRecord()` and `updateRecord()` methods with parameterized queries

---

### 6. Backup/Import Chain

#### **Prepare Backup**

**BEFORE:**
```
prepareBackupDb(String sourcedbfile, String sourcetable)
  ├─> checkDBConnection()
  ├─> holdDBConnection()
  ├─> db.execSQL("attach database '" + sourcedbfile + "' as sourceDB")
  ├─> db.execSQL("insert into sourceDB.custom select * from " + sourcetable)
  ├─> db.execSQL("insert into sourceDB.im select * from im WHERE code='" + sourcetable + "'")
  ├─> db.execSQL("update sourceDB.im set code='" + sourcetable + "'")
  ├─> db.execSQL("detach database sourceDB")
  └─> unHoldDBConnection()

prepareBackupRelatedDb(String sourcedbfile)
  ├─> checkDBConnection()
  ├─> holdDBConnection()
  ├─> db.execSQL("attach database '" + sourcedbfile + "' as sourceDB")
  ├─> db.execSQL("insert into sourceDB.related select * from related")
  ├─> db.execSQL("detach database sourceDB")
  └─> unHoldDBConnection()
```

**AFTER:**
```
prepareBackup(File targetFile, List<String> tableNames, boolean includeRelated) [NEW - Unified]
  ├─> checkDBConnection()
  ├─> [Validate all table names]
  │   └─> isValidTableName(table) [for each table]
  ├─> holdDBConnection()
  ├─> db.execSQL("attach database '" + targetFile + "' as sourceDB")
  ├─> [For each table in tableNames]
  │   └─> db.execSQL("insert into sourceDB.custom select * from " + table)
  ├─> [If includeRelated]
  │   └─> db.execSQL("insert into sourceDB.related select * from related")
  ├─> db.execSQL("insert into sourceDB.im select * from im WHERE code IN (...)")
  ├─> db.execSQL("update sourceDB.im set code=...")
  ├─> db.execSQL("detach database sourceDB")
  └─> unHoldDBConnection()

[Old methods become wrappers]
prepareBackupDb(file, table) → prepareBackup(file, [table], false)
prepareBackupRelatedDb(file) → prepareBackup(file, null, true)
```

**Improvement**: Single unified method replaces 2 similar methods, more flexible

#### **Import Backup**

**BEFORE:**
```
importDb(File sourceDBFile, String tableName)
  ├─> checkDBConnection()
  ├─> deleteAll(imType)
  │   ├─> countMapping(table)
  │   └─> db.delete(table, null, null)
  ├─> db.execSQL("delete from im where code='" + imType + "'")
  ├─> holdDBConnection()
  ├─> db.execSQL("attach database '" + sourceDBFile + "' as sourceDB")
  ├─> db.execSQL("insert into " + imType + " select * from sourceDB.custom")
  ├─> db.execSQL("update sourceDB.im set code='" + imType + "'")
  ├─> db.execSQL("insert into im select * from sourceDB.im")
  ├─> db.execSQL("detach database sourceDB")
  └─> unHoldDBConnection()

importDbRelated(File sourcedbfile)
  ├─> checkDBConnection()
  ├─> deleteAll(LIME.DB_TABLE_RELATED)
  ├─> holdDBConnection()
  ├─> db.execSQL("attach database '" + sourcedbfile + "' as sourceDB")
  ├─> db.execSQL("insert into related select * from sourceDB.related")
  ├─> db.execSQL("detach database sourceDB")
  └─> unHoldDBConnection()

importDb(String sourceDBFile, String imType)
  ├─> checkDBConnection()
  ├─> deleteAll(imType)
  ├─> holdDBConnection()
  ├─> db.execSQL("attach database '" + sourceDBFile + "' as sourceDB")
  ├─> db.execSQL("insert into " + imType + " select * from sourceDB." + imType)
  ├─> db.execSQL("insert into im select * from sourceDB.im")
  ├─> db.execSQL("detach database sourceDB")
  └─> unHoldDBConnection()
```

**AFTER:**
```
importDb(File sourceFile, List<String> tableNames, boolean includeRelated, boolean overwriteExisting) [NEW - Unified]
  ├─> checkDBConnection()
  ├─> [Validate all table names]
  │   └─> isValidTableName(table) [for each table]
  ├─> [If overwriteExisting]
  │   ├─> [For each table in tableNames]
  │   │   └─> deleteAll(table)
  │   │       └─> countRecords(table, null, null) [NEW - Unified count]
  │   └─> db.execSQL("delete from im where code IN (...)")
  ├─> holdDBConnection()
  ├─> db.execSQL("attach database '" + sourceFile + "' as sourceDB")
  ├─> [For each table in tableNames]
  │   └─> db.execSQL("insert into " + table + " select * from sourceDB.custom")
  ├─> [If includeRelated]
  │   └─> db.execSQL("insert into related select * from sourceDB.related")
  ├─> db.execSQL("update sourceDB.im set code=...")
  ├─> db.execSQL("insert into im select * from sourceDB.im")
  ├─> db.execSQL("detach database sourceDB")
  └─> unHoldDBConnection()

[Old methods - LimeDB]
importDbRelated(file) → importDb(file, null, true, true)

[Removed from LimeDB - use importDb() directly]
~~importDb(file, imType)~~ → Removed
~~importDb(String, String)~~ → Removed

[Note: DBServer still provides wrappers as convenience methods]
DBServer.importDb(file, imType) → LimeDB.importDb(file, [imType], false, true)
DBServer.importDbRelated(file) → LimeDB.importDbRelated(file)
```

**Improvement**: Single unified method replaces 3 similar methods, more flexible with overwrite option

---

### 7. Count Operations Chain

**BEFORE:**
```
countMapping(String table)
  ├─> checkDBConnection()
  ├─> isValidTableName(table)
  │   └─> [Check against whitelist]
  └─> db.rawQuery("SELECT * FROM " + table, null)
      └─> cursor.getCount()

count(String table)
  ├─> checkDBConnection()
  └─> db.rawQuery("SELECT COUNT(*) as count FROM " + table, null)
      └─> getCursorInt(cursor, LIME.DB_TOTAL_COUNT)

getRecordSize(String table, String curQuery, boolean searchByCode)
  ├─> checkDBConnection()
  └─> db.rawQuery("SELECT COUNT(*) as count FROM " + table + " WHERE ...", null)
      └─> getCursorInt(cursor, LIME.DB_TOTAL_COUNT)

getRelatedSize(String pword)
  ├─> checkDBConnection()
  └─> db.rawQuery("SELECT COUNT(*) as count FROM related WHERE ...", null)
      └─> getCursorInt(cursor, LIME.DB_TOTAL_COUNT)
```

**AFTER:**
```
countRecords(String table, String whereClause, String[] whereArgs) [NEW - Unified]
  ├─> checkDBConnection()
  ├─> isValidTableName(table)
  │   └─> [Check against whitelist]
  └─> db.rawQuery("SELECT COUNT(*) as count FROM " + table + 
                  (whereClause != null ? " WHERE " + whereClause : ""), whereArgs)
      └─> getCursorInt(cursor, LIME.DB_TOTAL_COUNT)

[All count operations now delegate to countRecords()]
countMapping(table) → countRecords(table, null, null)
count(table) → countRecords(table, null, null)
getRecordSize(table, query, searchByCode) → countRecordsByWordOrCode(table, query, searchByCode) → countRecords(table, buildWhereClause(...), whereArgs)
getRelatedSize(pword) → countRecords(LIME.DB_TABLE_RELATED, buildWhereClause(...), whereArgs)
```

**Improvement**: Single unified method replaces 4 similar methods, 75% reduction in duplication

---

### 8. IM Info Operations Chain

**BEFORE:**
```
getImInfo(String im, String field)
  ├─> checkDBConnection()
  └─> db.rawQuery("SELECT * FROM im WHERE code='" + im + "' AND title='" + field + "'", null)
      └─> getCursorString(cursor, LIME.DB_IM_COLUMN_DESC)

setImInfo(String im, String field, String value)
  ├─> checkDBConnection()
  ├─> removeImInfo(im, field)
  │   └─> removeImInfoOnDB(db, im, field)
  │       └─> db.delete(LIME.DB_TABLE_IM, whereClause, whereArgs)
  └─> db.insert("im", null, cv)

removeImInfo(String im, String field)
  ├─> checkDBConnection()
  └─> removeImInfoOnDB(db, im, field)
      └─> db.delete(LIME.DB_TABLE_IM, whereClause, whereArgs)

resetImInfo(String im)
  ├─> checkDBConnection()
  └─> db.execSQL("DELETE FROM im WHERE code='" + im + "'")
```

**AFTER:**
```
getImInfo(String im, String field)
  ├─> checkDBConnection()
  └─> db.query(LIME.DB_TABLE_IM, null, whereClause, whereArgs, ...) [NEW - Parameterized]
      └─> getCursorString(cursor, LIME.DB_IM_COLUMN_DESC)

setImInfo(String im, String field, String value)
  ├─> checkDBConnection()
  ├─> removeImInfo(im, field)
  │   └─> removeImInfoOnDB(db, im, field)
  │       └─> deleteRecord(LIME.DB_TABLE_IM, whereClause, whereArgs) [Already parameterized]
  └─> addRecord("im", cv) [NEW - Uses parameterized insert]

removeImInfo(String im, String field)
  ├─> checkDBConnection()
  └─> removeImInfoOnDB(db, im, field)
      └─> deleteRecord(LIME.DB_TABLE_IM, whereClause, whereArgs) [Already parameterized]

resetImInfo(String im)
  ├─> checkDBConnection()
  └─> deleteRecord(LIME.DB_TABLE_IM, LIME.DB_IM_COLUMN_CODE + " = ?", new String[]{im}) [NEW - Parameterized]
```

**Improvement**: All operations use parameterized queries, consistent with security best practices

---

### 9. New Methods Added to LimeDB

#### **getRecordsFromSourceDB**

**BEFORE:**
```
SetupImLoadRunnable.loadWord(SQLiteDatabase sourcedb, String code)
  └─> sourcedb.query(code, null, null, null, null, null, order)
      └─> SQLiteDatabase.query() [Direct call from Runnable]
```

**AFTER:**
```
LimeDB.getRecordsFromSourceDB(SQLiteDatabase sourceDb, String tableName) [NEW]
  ├─> isValidTableName(tableName) [Validation]
  └─> sourceDb.query(tableName, null, null, null, null, null, order)
      └─> [Build Record objects]
          └─> Record.get(cursor)

[SetupImLoadRunnable.run() - AFTER]
  └─> datasource.getRecordsFromSourceDB(sourceDb, tableName)
```

**Improvement**: SQL operation centralized in `LimeDB` with validation

#### **getBackupTableRecords**

**BEFORE:**
```
SetupImLoadRunnable.run()
  └─> datasource.rawQuery("select * from " + backupTableName)
      └─> SQLiteDatabase.rawQuery() [Direct call from Runnable]
```

**AFTER:**
```
LimeDB.getBackupTableRecords(String backupTableName) [NEW]
  ├─> [Validate backup table name format]
  │   └─> backupTableName.endsWith("_user")
  ├─> isValidTableName(baseTableName) [Validation]
  └─> db.rawQuery("SELECT * FROM " + backupTableName, null)
      └─> [Returns Cursor]

[SetupImLoadRunnable.run() - AFTER]
  └─> datasource.getBackupTableRecords(backupTableName)
```

**Improvement**: SQL operation moved to `LimeDB`, with validation added

#### **restoreUserRecords**

**BEFORE:**
```
SetupImLoadDialog.onPostExecute()
  ├─> searchServer.countRecords(backupTableName)
  ├─> searchServer.getBackupTableRecords(backupTableName)
  ├─> Record.getList(cursorbackup)
  └─> [Loop: searchServer.addOrUpdateMappingRecord(...)] [IN DIALOG]
```

**AFTER:**
```
SetupImLoadDialog.onPostExecute()
  └─> searchServer.restoreUserRecords(imtype)
      └─> dbadapter.restoreUserRecords(table)
          ├─> [Validate table name]
          ├─> countRecords(backupTableName, null, null)
          ├─> getBackupTableRecords(backupTableName)
          ├─> Record.getList(cursorbackup)
          └─> [Loop: addOrUpdateMappingRecord(table, code, word, score)]
```

**Improvement**: All database operations centralized in `LimeDB`, UI code simplified to single method call

---

## DBServer.java Call Chains

### 1. Mapping Loading Chain

**BEFORE:**
```
importTxtTable(File sourcefile, String tablename, LIMEProgressListener progressListener)
  ├─> datasource.setFinish(false)
  ├─> datasource.setFilename(sourcefile)
  ├─> datasource.importTxtTable(tablename, progressListener)
  │   └─> [See LimeDB importTxtTable chain above]
  └─> resetCache()
      └─> SearchServer.resetCache(true)

importZippedDb(File compressedSourceDB, String imtype)
  ├─> LIMEUtilities.unzip(compressedSourceDB.getAbsolutePath(), targetDir, true)
  │   └─> [Unzip file to target directory]
  ├─> datasource.importDb(new File(unzipFilePaths.get(0)), [imtype], false, true)
  │   └─> [See LimeDB importDb chain above]
  └─> resetCache()
```

**AFTER:**
```
SetupImFragment.importTxtTable(File sourceFile, String imtype, boolean restoreUserRecords)
  ├─> handler.showProgress(false, message)
  ├─> DBServer.importTxtTable(sourceFile.getAbsolutePath(), imtype, progressListener)
  │   ├─> datasource.setFinish(false)
  │   ├─> datasource.setFilename(sourcefile)
  │   ├─> datasource.importTxtTable(tablename, progressListener)
  │   │   └─> [See LimeDB importTxtTable chain above - uses countRecords()]
  │   └─> resetCache()
  │       └─> SearchServer.resetCache(true)
  └─> [If restoreUserRecords: searchServer.restoreUserRecords(imtype)]

importZippedDb(File compressedSourceDB, String imtype)
  ├─> LIMEUtilities.unzip(compressedSourceDB.getAbsolutePath(), targetDir, true)
  │   └─> [Extract zip file to temporary directory]
  ├─> datasource.importDb(unzippedFile, [imtype], false, true) [NEW - Unified method]
  │   └─> [See LimeDB importDb chain above]
  └─> SearchServer.resetCache(true)

importZippedDbRelated(File compressedSourceDB)
  ├─> LIMEUtilities.unzip(compressedSourceDB.getAbsolutePath(), targetDir, true)
  │   └─> [Extract zip file to temporary directory]
  ├─> datasource.importDbRelated(unzippedFile)
  │   └─> [See LimeDB importDbRelated chain above]
  └─> SearchServer.resetCache(true)
```

**Improvement**: Uses unified `importDb()` method

---

### 2. Backup Chain

**BEFORE:**
```
backupDatabase(Uri uri)
  ├─> getDataDirPath()
  │   └─> ContextCompat.getDataDir(appContext)
  ├─> backupDefaultSharedPreference(fileSharedPrefsBackup)
  │   ├─> ObjectOutputStream(new FileOutputStream(sharePrefs))
  │   └─> output.writeObject(pref.getAll())
  ├─> [Build backup file list]
  ├─> datasource.holdDBConnection()
  ├─> closeDatabase()
  │   └─> datasource.close()
  ├─> LIMEUtilities.zip(tempZip.getAbsolutePath(), backupFileList, dataDir, true)
  │   └─> [Create zip file with database and preferences]
  ├─> [Copy tempZip to uri using ContentResolver]
  ├─> datasource.openDBConnection(true)
  └─> datasource.unHoldDBConnection()
```

**AFTER:**
```
backupDatabase(Uri uri)
  ├─> getDataDirPath()
  │   └─> ContextCompat.getDataDir(appContext)
  ├─> backupDefaultSharedPreference(fileSharedPrefsBackup)
  │   ├─> ObjectOutputStream(new FileOutputStream(sharePrefs))
  │   └─> output.writeObject(pref.getAll())
  ├─> [Build backup file list]
  ├─> datasource.holdDBConnection()
  ├─> closeDatabase()
  │   └─> datasource.close()
  ├─> LIMEUtilities.zip(tempZip.getAbsolutePath(), backupFileList, dataDir, true)
  │   └─> [Create zip file with database and preferences]
  ├─> [Copy tempZip to uri using ContentResolver]
  ├─> datasource.openDBConnection(true)
  └─> datasource.unHoldDBConnection()
```

**Status**: No changes - already well-structured

---

### 3. Restore Chain

**BEFORE:**
```
restoreDatabase(Uri uri)
  ├─> [Download uri to tempZip using ContentResolver]
  └─> restoreDatabase(tempZip.getAbsolutePath())

restoreDatabase(String srcFilePath)
  ├─> datasource.holdDBConnection()
  ├─> closeDatabase()
  ├─> LIMEUtilities.unzip(srcFilePath, dataDir, true)
  │   └─> [Extract zip file to data directory]
  ├─> datasource.openDBConnection(true)
  ├─> datasource.unHoldDBConnection()
  ├─> restoreDefaultSharedPreference(sharedPref)
  │   ├─> ObjectInputStream(new FileInputStream(sharePrefs))
  │   └─> [Restore preferences from backup]
  ├─> resetCache()
  └─> datasource.checkAndUpdateRelatedTable()
      └─> [See LimeDB checkAndUpdateRelatedTable chain]
```

**AFTER:**
```
restoreDatabase(Uri uri)
  ├─> [File download handled directly using ContentResolver]
  │   ├─> ContentResolver.openInputStream(uri)
  │   └─> [Copy stream to cache file]
  └─> restoreDatabase(tempZip.getAbsolutePath())

restoreDatabase(String srcFilePath)
  ├─> datasource.holdDBConnection()
  ├─> closeDatabase()
  ├─> LIMEUtilities.unzip(srcFilePath, dataDir, true)
  │   └─> [Extract zip file to data directory]
  ├─> datasource.openDBConnection(true)
  ├─> datasource.unHoldDBConnection()
  ├─> restoreDefaultSharedPreference(sharedPref)
  │   ├─> ObjectInputStream(new FileInputStream(sharePrefs))
  │   └─> [Restore preferences from backup]
  ├─> resetCache()
  └─> datasource.checkAndUpdateRelatedTable()
      └─> [See LimeDB checkAndUpdateRelatedTable chain]
```

**Improvement**: File download operation centralized

---

### 4. File Compression/Decompression Chain

**BEFORE:**
```
zip(File sourceFile, String targetFolder, String targetFile)
  ├─> [Create target folder if not exists]
  ├─> FileOutputStream(dest)
  ├─> ZipOutputStream(new BufferedOutputStream(dest))
  ├─> FileInputStream(sourceFile)
  ├─> BufferedInputStream(fi, BUFFER)
  ├─> out.putNextEntry(new ZipEntry(...))
  └─> [Copy file to zip]

unzip(File sourceFile, String targetFolder, String targetFile, boolean removeOriginal)
  ├─> [Create target folder if not exists]
  ├─> FileInputStream(sourceFile)
  ├─> ZipInputStream(new BufferedInputStream(fis))
  ├─> zis.getNextEntry()
  ├─> FileOutputStream(OutputFile)
  ├─> BufferedOutputStream(fos, buffer.length)
  └─> [Extract file from zip]
```

**AFTER:**
```
zip(File sourceFile, String targetFolder, String targetFile)
  ├─> [Create target folder if not exists]
  ├─> FileOutputStream(dest)
  ├─> ZipOutputStream(new BufferedOutputStream(dest))
  ├─> FileInputStream(sourceFile)
  ├─> BufferedInputStream(fi, BUFFER)
  ├─> out.putNextEntry(new ZipEntry(...))
  └─> [Copy file to zip]

unzip(File sourceFile, String targetFolder, String targetFile, boolean removeOriginal)
  ├─> [Create target folder if not exists]
  ├─> FileInputStream(sourceFile)
  ├─> ZipInputStream(new BufferedInputStream(fis))
  ├─> zis.getNextEntry()
  ├─> FileOutputStream(OutputFile)
  ├─> BufferedOutputStream(fos, buffer.length)
  └─> [Extract file from zip]
```

**Status**: No changes - already well-structured

---

### 5. IM Info Proxy Chain

**BEFORE:**
```
getImInfo(String im, String field)
  └─> datasource.getImInfo(im, field)
      └─> [See LimeDB getImInfo chain]

setImInfo(String im, String field, String value)
  └─> datasource.setImInfo(im, field, value)
      └─> [See LimeDB setImInfo chain]

removeImInfo(String im, String field)
  └─> datasource.removeImInfo(im, field)
      └─> [See LimeDB removeImInfo chain]

resetImInfo(String im)
  └─> datasource.resetImInfo(im)
      └─> [See LimeDB resetImInfo chain]
```

**AFTER:**
```
getImInfo(String im, String field)
  └─> datasource.getImInfo(im, field)
      └─> [See LimeDB getImInfo chain - now uses parameterized query]

setImInfo(String im, String field, String value)
  └─> datasource.setImInfo(im, field, value)
      └─> [See LimeDB setImInfo chain - now uses addRecord()]

removeImInfo(String im, String field)
  └─> datasource.removeImInfo(im, field)
      └─> [See LimeDB removeImInfo chain - now uses deleteRecord()]

resetImInfo(String im)
  └─> datasource.resetImInfo(im)
      └─> [See LimeDB resetImInfo chain - now uses deleteRecord()]
```

**Improvement**: All underlying operations use parameterized queries

---

### 6. New Methods Added to DBServer

#### **exportZippedDb**

**BEFORE:**
```
ShareDbRunnable.run()
  ├─> [Create cache directory - IN RUNNABLE]
  ├─> [Delete existing files - IN RUNNABLE]
  ├─> [Copy blank DB from raw resource - IN RUNNABLE]
  │   └─> FileInputStream / FileOutputStream operations
  ├─> datasource.prepareBackupDb(targetfile, imtype)
  │   └─> [SQL operations in LimeDB]
  └─> LIMEUtilities.zip(targetfilezip, targetfile, true) [IN RUNNABLE]
```

**AFTER:**
```
DBServer.exportZippedDb(String imType, File targetFile, ProgressCallback progressCallback) [NEW]
  ├─> [Create cache directory]
  ├─> [Delete existing files]
  ├─> [Copy blank DB from raw resource]
  │   └─> LIMEUtilities.copyFile(rawResource, targetFile)
  ├─> datasource.prepareBackupDb(targetfile, imtype)
  │   └─> [SQL operations in LimeDB - unchanged]
  └─> LIMEUtilities.zip(targetfilezip, targetfile, true)

[ShareDbRunnable.run() - AFTER]
  └─> DBServer.exportZippedDb(imType, targetFile, progressCallback)
```

**Improvement**: All file operations centralized in `DBServer`, Runnable becomes thin wrapper

#### **exportZippedDbRelated**

**BEFORE:**
```
ShareRelatedDbRunnable.run()
  ├─> [Create cache directory - IN RUNNABLE]
  ├─> [Delete existing files - IN RUNNABLE]
  ├─> [Copy blank DB from raw resource - IN RUNNABLE]
  │   └─> FileInputStream / FileOutputStream operations
  ├─> datasource.prepareBackupRelatedDb(targetfile)
  │   └─> [SQL operations in LimeDB]
  └─> LIMEUtilities.zip(targetfilezip, targetfile, true) [IN RUNNABLE]
```

**AFTER:**
```
DBServer.exportZippedDbRelated(File targetFile, ProgressCallback progressCallback) [NEW]
  ├─> [Create cache directory]
  ├─> [Delete existing files]
  ├─> [Copy blank DB from raw resource]
  │   └─> LIMEUtilities.copyRAWFile(rawResource, targetFile)
  ├─> datasource.prepareBackup(dbFile, null, true)
  │   └─> [See LimeDB prepareBackup chain above]
  ├─> LIMEUtilities.zip(targetFile, dbFile, true)
  │   └─> [Create zip file containing database]
  └─> [Clean up temp file]

DBServer.importZippedDbRelated(File compressedSourceDB) [NEW]
  ├─> LIMEUtilities.unzip(compressedSourceDB.getAbsolutePath(), targetDir, true)
  │   └─> [Extract zip file to temporary directory]
  ├─> datasource.importDbRelated(unzippedFile)
  │   └─> [See LimeDB importDbRelated chain above]
  └─> SearchServer.resetCache(true)

[ShareRelatedDbRunnable.run() - AFTER]
  └─> DBServer.exportZippedDbRelated(targetFile, progressCallback)

[SetupImLoadDialog.importDbRelated() - AFTER]
  └─> DBServer.importZippedDbRelated(unit)

[SetupImLoadDialog.importDefaultRelated() - AFTER]
  ├─> LIMEUtilities.copyRAWFile(rawResource, relatedDbPath)
  └─> DBServer.importZippedDbRelated(relatedDbPath)
```

**Improvement**: All file operations centralized in `DBServer`, Runnable becomes thin wrapper

#### **SetupImLoadRunnable - Restore User Records**

**BEFORE:**
```
SetupImLoadRunnable.run()
  ├─> downloadRemoteFile(ctx, url) [IN RUNNABLE]
  │   └─> LIMEUtilities.downloadRemoteFile()
  ├─> dbsrv.importZippedDb(tempfile, imtype) [IN DBSERVER]
  ├─> [Restore preference check - IN RUNNABLE]
  ├─> searchServer.checkBackuptable(imtype) [IN RUNNABLE]
  ├─> searchServer.getBackupTableRecords(backupTableName) [IN RUNNABLE]
  ├─> Record.getList(cursorbackup) [IN RUNNABLE]
  └─> [Loop: searchServer.addOrUpdateMappingRecord(...)] [IN RUNNABLE]
```

**AFTER:**
```
SetupImLoadRunnable.run()
  ├─> downloadRemoteFile(ctx, url) [IN RUNNABLE]
  │   └─> LIMEUtilities.downloadRemoteFile()
  ├─> dbsrv.importZippedDb(tempfile, imtype) [IN DBSERVER]
  └─> [If restorePreference - IN RUNNABLE]
      ├─> searchServer.checkBackuptable(imtype)
      └─> searchServer.restoreUserRecords(imtype) [NEW - Centralized in LimeDB]
          └─> dbadapter.restoreUserRecords(table)
              ├─> countRecords(backupTableName, null, null)
              ├─> getBackupTableRecords(backupTableName)
              ├─> Record.getList(cursorbackup)
              └─> [Loop: addOrUpdateMappingRecord(table, code, word, score)]
```

**Improvement**: Restore user records logic centralized in `LimeDB.restoreUserRecords()`, Runnable code simplified

#### **File Import Operations**

**BEFORE:**
```
MainActivity.onNewIntent()
  ├─> [File download to cache - IN ACTIVITY]
  │   └─> ContentResolver.openInputStream() / FileOutputStream
  ├─> [File type detection - IN ACTIVITY]
  ├─> [If .lime/.cin]
  │   └─> ImportDialog.newInstance(filePath)
  └─> [If .limedb]
      ├─> [Unzip logic - IN ACTIVITY]
      └─> datasource.importDb() / datasource.importDbRelated()
```

**AFTER:**
```
[MainActivity.onNewIntent() - AFTER]
  ├─> [File download handled directly in MainActivity using ContentResolver]
  └─> [Route to appropriate DBServer method based on file type]

[MainActivity.performLimedbImport() - AFTER]
  ├─> [If "related":]
  │   └─> DBServer.importZippedDbRelated(fileToImport)
  │       ├─> LIMEUtilities.unzip(filePath, tempDir, true)
  │       │   └─> [Extract zip file to temporary directory]
  │       └─> datasource.importDbRelated(dbFile)
  │           └─> [See LimeDB importDbRelated chain above]
  └─> [Otherwise:]
      └─> DBServer.importZippedDb(fileToImport, tableName)
          ├─> LIMEUtilities.unzip(filePath, tempDir, true)
          │   └─> [Extract zip file to temporary directory]
          └─> datasource.importDb(dbFile, [tableName], false, true)
              └─> [See LimeDB importDb chain above]

[Text file imports (.lime/.cin) - handled by ImportDialog]
  └─> DBServer.importTxtTable(sourceFile, tableName, progressListener)
      └─> [See DBServer importTxtTable chain above]
```

**Improvement**: File import operations moved to `DBServer`, file downloads handled directly in MainActivity

---

## SearchServer.java Call Chains

### 1. Search Server Initialization Chain

**BEFORE:**
```
SearchServer(Context context)
  ├─> mLIMEPref = new LIMEPreferenceManager(context)
  ├─> dbadapter = new LimeDB(context) [if null]
  │   └─> [See LimeDB initialization chain]
  └─> initialCache()
      └─> [Initialize all cache maps]
```

**AFTER:**
```
SearchServer(Context context)
  ├─> mLIMEPref = new LIMEPreferenceManager(context)
  ├─> dbadapter = new LimeDB(context) [if null]
  │   └─> [See LimeDB initialization chain - unchanged]
  └─> initialCache()
      └─> [Initialize all cache maps]
```

**Status**: No changes - already well-structured, uses `LimeDB` correctly

---

### 2. Search Query Chain - getMappingByCode

**BEFORE:**
```
SearchServer.getMappingByCode(String code, boolean softkeyboard, boolean getAllRecords)
  ├─> [Check reset cache flag]
  │   └─> initialCache() [if needed]
  ├─> [Check cache]
  │   └─> cache.get(cacheKey(code))
  ├─> [If cache miss or getAllRecords]
  │   └─> dbadapter.getMappingByCode(code, !isPhysicalKeyboardPressed, getAllRecords)
  │       └─> [See LimeDB getMappingByCode chain]
  ├─> [Cache result]
  │   └─> cache.put(cacheKey(code), resultList)
  ├─> [Build result list from cache]
  ├─> [Runtime phrase suggestion]
  │   └─> makeRunTimeSuggestion(code, resultList)
  │       ├─> dbadapter.getMappingByCode(...) [for related phrases]
  │       └─> dbadapter.isRelatedPhraseExist(pword, cword)
  └─> [Return combined results]
```

**AFTER:**
```
SearchServer.getMappingByCode(String code, boolean softkeyboard, boolean getAllRecords)
  ├─> [Check reset cache flag]
  │   └─> initialCache() [if needed]
  ├─> [Check cache]
  │   └─> cache.get(cacheKey(code))
  ├─> [If cache miss or getAllRecords]
  │   └─> dbadapter.getMappingByCode(code, !isPhysicalKeyboardPressed, getAllRecords)
  │       └─> [See LimeDB getMappingByCode chain - uses parameterized queries]
  ├─> [Cache result]
  │   └─> cache.put(cacheKey(code), resultList)
  ├─> [Build result list from cache]
  ├─> [Runtime phrase suggestion]
  │   └─> makeRunTimeSuggestion(code, resultList)
  │       ├─> dbadapter.getMappingByCode(...) [for related phrases]
  │       └─> dbadapter.isRelatedPhraseExist(pword, cword)
  └─> [Return combined results]
```

**Status**: No changes needed - already uses `LimeDB` correctly, benefits from `LimeDB` improvements automatically

---

### 3. User Learning Chain - addOrUpdateMappingRecord

**BEFORE:**
```
SearchServer.makeRunTimeSuggestion()
  └─> [When user selects suggestion]
      └─> dbadapter.addOrUpdateMappingRecord(code, record)
          └─> [See LimeDB addOrUpdateMappingRecord chain]
```

**AFTER:**
```
SearchServer.makeRunTimeSuggestion()
  └─> [When user selects suggestion]
      └─> dbadapter.addOrUpdateMappingRecord(code, record)
          └─> [See LimeDB addOrUpdateMappingRecord chain - now uses parameterized addRecord/updateRecord]
```

**Improvement**: Automatically benefits from `LimeDB`'s parameterized query improvements

---

### 4. Related Phrase Learning Chain

**BEFORE:**
```
SearchServer.makeRunTimeSuggestion()
  └─> [When building phrase suggestions]
      └─> dbadapter.addOrUpdateRelatedPhraseRecord(pword, cword)
          └─> [See LimeDB addOrUpdateRelatedPhraseRecord chain]
```

**AFTER:**
```
SearchServer.makeRunTimeSuggestion()
  └─> [When building phrase suggestions]
      └─> dbadapter.addOrUpdateRelatedPhraseRecord(pword, cword)
          └─> [See LimeDB addOrUpdateRelatedPhraseRecord chain - now uses parameterized addRecord/updateRecord]
```

**Improvement**: Automatically benefits from `LimeDB`'s parameterized query improvements

---

### 5. Cache Reset Chain

**BEFORE:**
```
DBServer.resetCache()
  └─> SearchServer.resetCache(true)
      └─> mResetCache = true
          └─> [Cache will be cleared on next getMappingByCode call]
              └─> initialCache()
                  └─> [Clear all cache maps]
```

**AFTER:**
```
DBServer.resetCache()
  └─> SearchServer.resetCache(true)
      └─> mResetCache = true
          └─> [Cache will be cleared on next getMappingByCode call]
              └─> initialCache()
                  └─> [Clear all cache maps]
```

**Status**: No changes - already well-structured

---

### 5.1 Reset Mapping Chain

**BEFORE:**
```
DBServer.resetMapping(String table)
  └─> datasource.deleteAll(table)
      └─> db.delete(table, null, null)
  └─> SearchServer.resetCache(true)
```

**AFTER:**
```
SearchServer.clearTable(String table)
  ├─> [Validate dbadapter is not null]
  └─> dbadapter.resetMapping(table)
      ├─> [Validate table name is not null/empty]
      ├─> [Validate table name using isValidTableName()]
      ├─> [Check database connection]
      ├─> deleteAll(table)
      │   ├─> [Wait for loadingMappingThread to stop]
      │   ├─> countRecords(table, null, null) [if > 0]
      │   ├─> db.delete(table, null, null)
      │   ├─> resetImInfo(table)
      │   └─> [Clear blackListCache]
      └─> SearchServer.resetCache(true)
          └─> mResetCache = true
```

**Improvement**: Added validation, error handling, and improved error messages. Method now validates table name to prevent SQL injection and checks database connection before proceeding.

---

### 6. Reset Lime Settings Chain

**BEFORE:**
```
NavigationDrawerFragment.onMenuItemSelected() [action_reset]
  └─> datasource.resetLimeSetting() [Direct LimeDB access]
      └─> LimeDB.resetLimeSetting()
          ├─> db.close()
          ├─> dbFile.deleteOnExit()
          ├─> LIMEUtilities.copyRAWFile() [Restore main database from raw resource]
          ├─> openDBConnection(true)
          ├─> emojiConverter.close()
          ├─> LIMEUtilities.copyRAWFile() [Restore emoji database from raw resource]
          ├─> emojiConverter = new EmojiConverter()
          ├─> hanConverter.close()
          └─> LIMEUtilities.copyRAWFile() [Restore han converter database from raw resource]
```

**AFTER:**
```
NavigationDrawerFragment.onMenuItemSelected() [action_reset]
  └─> searchServer.resetLimeSetting() [Via SearchServer]
      └─> LimeDB.resetLimeSetting()
          ├─> db.close()
          ├─> dbFile.deleteOnExit()
          ├─> LIMEUtilities.copyRAWFile() [Restore main database from raw resource]
          ├─> openDBConnection(true)
          ├─> emojiConverter.close()
          ├─> LIMEUtilities.copyRAWFile() [Restore emoji database from raw resource]
          ├─> emojiConverter = new EmojiConverter()
          ├─> hanConverter.close()
          └─> LIMEUtilities.copyRAWFile() [Restore han converter database from raw resource]
```

**Improvement**: Reset operation goes through SearchServer, maintaining architecture compliance

---

### 7. SearchServer Methods That Call LimeDB

| SearchServer Method | LimeDB Method Called | Purpose |
|---------------------|---------------------|---------|
| `getMappingByCode()` | `dbadapter.getMappingByCode()` | Get record mappings for code |
| `getRelatedPhrase()` | `dbadapter.getRelatedPhrase()` | Get related phrase suggestions |
| `hanConvert()` | `dbadapter.hanConvert()` | Convert traditional/simplified Chinese |
| `reverseLookup()` | `dbadapter.getCodeListStringByWord()` | Reverse lookup: record to code |
| `makeRunTimeSuggestion()` | `dbadapter.getMappingByCode()`, `dbadapter.isRelatedPhraseExist()` | Build phrase suggestions |
| `makeRunTimeSuggestion()` | `dbadapter.addOrUpdateMappingRecord()` | Learn user selections |
| `makeRunTimeSuggestion()` | `dbadapter.addOrUpdateRelatedPhraseRecord()` | Learn phrase patterns |
| `getSelkey()` | `dbadapter.getSelkey()` | Get selection key mapping |
| `keyToKeyName()` | `dbadapter.keyToKeyName()` | Convert code to key names |
| `clearTable(String table)` | `dbadapter.resetMapping(table)` | Clear mapping table (validates table name, deletes all records, clears cache) |
| `restoreUserRecords(String table)` | `dbadapter.restoreUserRecords(table)` | Restore user records from backup table to main table |
| `resetLimeSetting()` | `dbadapter.resetLimeSetting()` | Reset all LIME settings to factory defaults (main, emoji, han converter databases) |
| `getKeyboardList()` | `dbadapter.getKeyboardList()` | Get available keyboards |
| `getIm(String code, String type)` | `dbadapter.getIm(code, type)` | Get IM list by type (for UI components) |
| `getKeyboard()` | `dbadapter.getKeyboard()` | Get keyboard list (for UI components) |
| `getImInfo(String im, String field)` | `dbadapter.getImInfo(im, field)` | Get IM configuration info |
| `setImInfo(String im, String field, String value)` | `dbadapter.setImInfo(im, field, value)` | Set IM configuration info |
| `setIMKeyboard(String im, String value, String keyboard)` | `dbadapter.setIMKeyboard(im, value, keyboard)` | Set IM keyboard assignment |
| `removeImInfo(String im, String field)` | `dbadapter.removeImInfo(im, field)` | Remove IM configuration info |
| `resetImInfo(String im)` | `dbadapter.resetImInfo(im)` | Reset all IM information for a specific IM |
| `getKeyboardInfo(String keyboardCode, String field)` | `dbadapter.getKeyboardInfo(keyboardCode, field)` | Get keyboard information |
| `getKeyboardCode(String im)` | `dbadapter.getKeyboardCode(im)` | Get keyboard code assigned to an IM |
| `getKeyboardObj(String keyboard)` | `dbadapter.getKeyboardObj(keyboard)` | Get keyboard object information |
| `checkBackuptable(String table)` | `dbadapter.checkBackupTable(table)` | Check if backup table exists and has records |
| `backupUserRecords(String table)` | `dbadapter.backupUserRecords(table)` | Backup user-learned records to backup table |
| `resetCache()` | `dbadapter.resetCache()` | Reset SearchServer cache |
| `countRecords(String table)` | `dbadapter.countRecords(table, null, null)` | Count records in a table |
| `isValidTableName(String tableName)` | `dbadapter.isValidTableName(tableName)` | Validate table name |

**Status**: All `SearchServer` methods correctly use `LimeDB` - no refactoring needed

---

## Cross-Class Call Patterns

### Pattern 1: DBServer → LimeDB → SQLiteDatabase

**BEFORE:**
```
DBServer.method()
  └─> LimeDB.method()
      └─> SQLiteDatabase.operation()
```

**AFTER:**
```
DBServer.method()
  └─> LimeDB.method()
      └─> SQLiteDatabase.operation()
```

**Status**: No changes - already follows good pattern

### Pattern 2: DBServer → LIMEUtilities → File Operations

**BEFORE:**
```
DBServer.method()
  └─> LIMEUtilities.zip/unzip()
      └─> [File I/O operations]
```

**AFTER:**
```
DBServer.method()
  └─> LIMEUtilities.zip/unzip()
      └─> [File I/O operations]
```

**Status**: No changes - already follows good pattern

### Pattern 3: LimeDB → Helper Classes → SQLiteDatabase

**BEFORE:**
```
LimeDB.method()
  ├─> LimeHanConverter.method() [Separate DB - hanconvertv2.db - EXCLUDED FROM REFACTORING]
  │   └─> SQLiteDatabase.operation() [on hanconvertv2.db]
  └─> EmojiConverter.method() [Separate DB - emoji.db - EXCLUDED FROM REFACTORING]
      └─> SQLiteDatabase.operation() [on emoji.db]
```

**AFTER:**
```
LimeDB.method()
  ├─> LimeHanConverter.method() [Separate DB - hanconvertv2.db - EXCLUDED FROM REFACTORING]
  │   └─> SQLiteDatabase.operation() [on hanconvertv2.db]
  └─> EmojiConverter.method() [Separate DB - emoji.db - EXCLUDED FROM REFACTORING]
      └─> SQLiteDatabase.operation() [on emoji.db]
```

**Status**: No changes - helper classes remain independent

### Pattern 4: SearchServer → LimeDB (Already Correct)

**BEFORE:**
```
SearchServer.getMappingByCode()
  └─> dbadapter.getMappingByCode() [LimeDB static instance]
      └─> SQLiteDatabase.operation()
```

**AFTER:**
```
SearchServer.getMappingByCode()
  └─> dbadapter.getMappingByCode() [LimeDB static instance]
      └─> SQLiteDatabase.operation() [with parameterized queries]
```

**Status**: Already follows correct pattern - no refactoring needed, automatically benefits from `LimeDB` improvements

### Pattern 5: Files with SQL Operations → LimeDB (NEW)

**BEFORE:**
```
SetupImLoadRunnable.run()
  ├─> datasource.rawQuery("select * from " + backupTableName) [SQL IN RUNNABLE]
  └─> loadWord(sourcedb, code) [SQL IN RUNNABLE]
      └─> sourcedb.query(...) [Direct SQL call]
```

**AFTER:**
```
SetupImLoadRunnable.run()
  ├─> datasource.getBackupTableRecords(backupTableName) [SQL IN LIMEDB]
  │   └─> [Validation and SQL operation in LimeDB]
  └─> datasource.getRecordsFromSourceDB(sourceDb, tableName) [SQL IN LIMEDB]
      └─> [Validation and SQL operation in LimeDB]
```

**Improvement**: All SQL operations centralized in `LimeDB`

### Pattern 6: Files with File Operations → DBServer (NEW)

**BEFORE:**
```
ShareDbRunnable.run()
  ├─> [File copy - IN RUNNABLE]
  └─> LIMEUtilities.zip() [IN RUNNABLE]

MainActivity.onNewIntent()
  ├─> [File download - IN ACTIVITY]
  └─> [File import logic - IN ACTIVITY]
```

**AFTER:**
```
ShareDbRunnable.run()
  └─> DBServer.exportZippedDb() [File operations IN DBSERVER]
      ├─> [File copy - IN DBSERVER]
      └─> LIMEUtilities.zip() [IN DBSERVER]

MainActivity.onNewIntent()
  └─> DBServer.importZippedDb() / DBServer.importZippedDbRelated() [File operations IN DBSERVER]
      └─> [File import logic - IN DBSERVER]
```

**Improvement**: All file operations centralized in `DBServer`

---

## Critical Paths (Most Frequently Called)

### 1. Query Path (Hot Path)

**BEFORE:**
```
LIMEService.onKeyEvent()
  └─> SearchServer.getMappingByCode()
      ├─> [Check cache]
      ├─> [If cache miss]
      │   └─> LimeDB.getMappingByCode()
      │       ├─> preProcessingRemappingCode()
      │       ├─> preProcessingForExtraQueryConditions()
      │       ├─> expandDualCode()
      │       └─> db.rawQuery()
      └─> [Cache result and return]
```

**AFTER:**
```
LIMEService.onKeyEvent()
  └─> SearchServer.getMappingByCode()
      ├─> [Check cache]
      ├─> [If cache miss]
      │   └─> LimeDB.getMappingByCode()
      │       ├─> preProcessingRemappingCode()
      │       ├─> preProcessingForExtraQueryConditions()
      │       ├─> expandDualCode()
      │       └─> db.rawQuery() [with parameterized queries where applicable]
      └─> [Cache result and return]
```

**Status**: No changes needed - `SearchServer` already uses `LimeDB` correctly, automatically benefits from improvements

### 2. Learning Path

**BEFORE:**
```
LIMEService.onKeyEvent()
  └─> LimeDB.addOrUpdateMappingRecord()
      └─> db.insert() or db.update()
```

**AFTER:**
```
LIMEService.onKeyEvent()
  └─> LimeDB.addOrUpdateMappingRecord()
      ├─> isValidTableName(table) [NEW - Validation]
      └─> addRecord() or updateRecord() [NEW - Parameterized]
          └─> db.insert() or db.update()
```

**Improvement**: Added validation and uses parameterized methods

### 3. File Loading Path

**BEFORE:**
```
DBServer.importTxtTable()
  └─> LimeDB.importTxtTable()
      └─> [Transaction with many inserts]
```

**AFTER:**
```
DBServer.importTxtTable()
  └─> LimeDB.importTxtTable()
      └─> [Transaction with many inserts using addRecord()]
```

**Improvement**: Uses parameterized `addRecord()` method

---

## Refactoring Impact Analysis

### Methods That Will Change Callers

| Method | Current Callers | Before | After | Impact |
|--------|----------------|--------|-------|--------|
| `SetupImLoadRunnable.loadWord()` | 1 | SQL in Runnable | `LimeDB.getRecordsFromSourceDB()` | Low - Move to LimeDB |
| `SetupImLoadRunnable.setImInfo()` | 1 | Duplicate method | Use `LimeDB.setImInfo()` | Low - Remove duplicate |
| `ShareDbRunnable.run()` | 1 | File ops in Runnable | `DBServer.exportZippedDb()` | Medium - Move to DBServer |
| `ShareRelatedDbRunnable.run()` | 1 | File ops in Runnable | `DBServer.exportZippedDbRelated()` | Medium - Move to DBServer |
| `MainActivity.handleImportTxt()` | 1 | File ops in Activity | `DBServer.importZippedDb()` / `DBServer.importTxtTable()` | Medium - Move to DBServer |
| `LimeHanConverter.getBaseScore()` | 2 | - | - | **EXCLUDED** - Separate database |
| `EmojiConverter.convert()` | 1 | - | - | **EXCLUDED** - Separate database |

### Methods That Will Be Consolidated

| Old Methods | New Method | Before Count | After Count | Impact |
|-------------|------------|--------------|-------------|--------|
| `countMapping()`, `count()`, `getRecordSize()`, `getRelatedSize()` | `countRecords()` | 4 methods | 1 method | Medium - Update 10+ callers |
| `prepareBackupDb()`, `prepareBackupRelatedDb()` | `prepareBackup()` | 2 methods | 1 method | Low - Update 2 callers |
| `importDbRelated()` (LimeDB), `importDb()` (DBServer wrapper) | `importDb()` | 2 methods | 1 method | Medium - Update 5+ callers |

### New Methods to Add

| New Method | Location | Purpose | Replaces |
|------------|----------|---------|----------|
| `getRecordsFromSourceDB()` | LimeDB | Load records from external DB | `SetupImLoadRunnable.loadWord()` |
| `getBackupTableRecords()` | LimeDB | Get backup table records | `SetupImLoadRunnable.rawQuery()` |
| `restoreUserRecords()` | LimeDB | Restore user records from backup table | `SetupImLoadDialog` manual loop |
| `countRecords()` | LimeDB | Unified count with WHERE support | 4 count methods |
| `prepareBackup()` | LimeDB | Unified backup preparation | 2 backup methods |
| `importDb()` | LimeDB | Unified import with options | 3 import methods |
| `buildWhereClause()` | LimeDB | Build parameterized WHERE clause | Inline WHERE building |
| `queryWithPagination()` | LimeDB | Query with pagination support | Inline pagination logic |
| `exportZippedDb()` | DBServer | Export IM database to zipped file | `ShareDbRunnable.run()` |
| `exportZippedDbRelated()` | DBServer | Export related database to zipped file | `ShareRelatedDbRunnable.run()` |

---

## Notes

1. **Transaction Management**: Most write operations should be wrapped in transactions for performance
2. **Connection Holding**: Backup/restore operations hold the connection to prevent concurrent access
3. **Error Handling**: All database operations should check `checkDBConnection()` first
4. **Parameterized Queries**: All user-facing queries should use parameterized queries
5. **Table Name Validation**: All table names should be validated with `isValidTableName()`
6. **Helper Classes**: `LimeHanConverter` and `EmojiConverter` remain independent (separate databases)

---

## UI Components Call Chains

### 1. SetupImFragment Call Chain

**BEFORE:**
```
SetupImFragment.initialbutton()
  └─> datasource.getIm(null, LIME.IM_TYPE_NAME) [Direct LimeDB access]
      └─> LimeDB.getIm()
          └─> SQLiteDatabase.query()
```

**AFTER:**
```
SetupImFragment.initialbutton()
  └─> searchServer.getIm(null, LIME.IM_TYPE_NAME) [Via SearchServer]
      └─> LimeDB.getIm()
          └─> SQLiteDatabase.query()
```

**Improvement**: UI component uses SearchServer instead of direct LimeDB access

### 2. ManageImFragment Call Chain

**BEFORE:**
```
ManageImFragment.onCreateView()
  ├─> datasource.getIm(null, LIME.IM_TYPE_KEYBOARD) [Direct LimeDB access]
  │   └─> LimeDB.getIm()
  └─> datasource.getKeyboard() [Direct LimeDB access]
      └─> LimeDB.getKeyboard()
```

**AFTER:**
```
ManageImFragment.onCreateView()
  ├─> searchServer.getIm(null, LIME.IM_TYPE_KEYBOARD) [Via SearchServer]
  │   └─> LimeDB.getIm()
  └─> searchServer.getKeyboard() [Via SearchServer]
      └─> LimeDB.getKeyboard()
```

**Improvement**: All database operations go through SearchServer

### 3. ManageImKeyboardDialog Call Chain

**BEFORE:**
```
ManageImKeyboardDialog.onItemClick()
  ├─> datasource.getKeyboard() [Direct LimeDB access]
  │   └─> LimeDB.getKeyboard()
  └─> datasource.setImKeyboard(code, keyboard) [Direct LimeDB access]
      └─> LimeDB.setIMKeyboard()
          └─> SQLiteDatabase.update()
```

**AFTER:**
```
ManageImKeyboardDialog.onItemClick()
  ├─> searchServer.getKeyboard() [Via SearchServer]
  │   └─> LimeDB.getKeyboard()
  └─> searchServer.setIMKeyboard(code, keyboard) [Via SearchServer]
      └─> LimeDB.setIMKeyboard()
          └─> SQLiteDatabase.update()
```

**Improvement**: Configuration operations go through SearchServer

### 4. ImportDialog Call Chain

**BEFORE:**
```
ImportDialog.onCreateView()
  └─> datasource.getIm(null, LIME.IM_TYPE_NAME) [Direct LimeDB access]
      └─> LimeDB.getIm()
          └─> SQLiteDatabase.query()
```

**AFTER:**
```
ImportDialog.onCreateView()
  └─> searchServer.getIm(null, LIME.IM_TYPE_NAME) [Via SearchServer]
      └─> LimeDB.getIm()
          └─> SQLiteDatabase.query()
```

**Improvement**: Import dialog uses SearchServer

### 5. ShareDialog Call Chain

**BEFORE:**
```
ShareDialog.onCreateView()
  └─> datasource.getIm(null, LIME.IM_TYPE_NAME) [Direct LimeDB access]
      └─> LimeDB.getIm()
          └─> SQLiteDatabase.query()
```

**AFTER:**
```
ShareDialog.onCreateView()
  └─> searchServer.getIm(null, LIME.IM_TYPE_NAME) [Via SearchServer]
      └─> LimeDB.getIm()
          └─> SQLiteDatabase.query()
```

**Improvement**: Share dialog uses SearchServer

### 6. NavigationDrawerFragment Call Chain

**BEFORE:**
```
NavigationDrawerFragment.onCreateView()
  ├─> datasource = new LimeDB(this.getActivity()) [Direct LimeDB instantiation]
  └─> datasource.getIm(null, LIME.IM_TYPE_NAME) [Direct LimeDB access]
      └─> LimeDB.getIm()
          └─> SQLiteDatabase.query()

NavigationDrawerFragment.updateMenuItems()
  └─> datasource.getIm(null, LIME.IM_TYPE_NAME) [Direct LimeDB access]
      └─> LimeDB.getIm()
          └─> SQLiteDatabase.query()

NavigationDrawerFragment.onMenuItemSelected() [action_reset]
  └─> datasource.resetLimeSetting() [Direct LimeDB access]
      └─> LimeDB.resetLimeSetting()
          ├─> db.close()
          ├─> dbFile.deleteOnExit()
          ├─> LIMEUtilities.copyRAWFile() [Restore main database]
          ├─> openDBConnection(true)
          ├─> emojiConverter.close()
          ├─> LIMEUtilities.copyRAWFile() [Restore emoji database]
          ├─> emojiConverter = new EmojiConverter()
          ├─> hanConverter.close()
          └─> LIMEUtilities.copyRAWFile() [Restore han converter database]
```

**AFTER:**
```
NavigationDrawerFragment.onCreateView()
  ├─> searchServer = new SearchServer(this.getActivity()) [Via SearchServer]
  └─> searchServer.getIm(null, LIME.IM_TYPE_NAME) [Via SearchServer]
      └─> LimeDB.getIm()
          └─> SQLiteDatabase.query()

NavigationDrawerFragment.updateMenuItems()
  └─> searchServer.getIm(null, LIME.IM_TYPE_NAME) [Via SearchServer]
      └─> LimeDB.getIm()
          └─> SQLiteDatabase.query()

NavigationDrawerFragment.onMenuItemSelected() [action_reset]
  └─> searchServer.resetLimeSetting() [Via SearchServer]
      └─> LimeDB.resetLimeSetting()
          ├─> db.close()
          ├─> dbFile.deleteOnExit()
          ├─> LIMEUtilities.copyRAWFile() [Restore main database]
          ├─> openDBConnection(true)
          ├─> emojiConverter.close()
          ├─> LIMEUtilities.copyRAWFile() [Restore emoji database]
          ├─> emojiConverter = new EmojiConverter()
          ├─> hanConverter.close()
          └─> LIMEUtilities.copyRAWFile() [Restore han converter database]
```

**Improvement**: Navigation drawer uses SearchServer instead of direct LimeDB access, maintaining architecture compliance

### 7. SetupImLoadRunnable Call Chain

**BEFORE:**
```
SetupImLoadRunnable.run()
  ├─> downloadRemoteFile(ctx, url) [File operation in Runnable]
  │   └─> LIMEUtilities.downloadRemoteFile()
  ├─> dbsrv.importMapping(tempfile, imtype) [File operation]
  │   └─> DBServer.importMapping()
  ├─> datasource.rawQuery("select * from " + backupTableName) [SQL in Runnable]
  │   └─> SQLiteDatabase.rawQuery()
  ├─> datasource.setImInfo(im, field, value) [Direct LimeDB access]
  │   └─> LimeDB.setImInfo()
  └─> datasource.setIMKeyboard(im, value, keyboard) [Direct LimeDB access]
      └─> LimeDB.setIMKeyboard()
```

**AFTER:**
```
SetupImLoadRunnable.run()
  ├─> downloadRemoteFile(ctx, url) [File operation in Runnable]
  │   └─> LIMEUtilities.downloadRemoteFile()
  ├─> dbsrv.importZippedDb(tempfile, imtype) [File operation in DBServer]
  │   └─> LimeDB.importDb()
  ├─> searchServer.resetCache() [Via SearchServer]
  └─> [If restorePreference]
      ├─> searchServer.checkBackuptable(imtype) [Via SearchServer]
      └─> searchServer.restoreUserRecords(imtype) [Via SearchServer - Centralized in LimeDB]
          └─> dbadapter.restoreUserRecords(table)
```

**Improvement**: Restore user records logic centralized in `LimeDB.restoreUserRecords()`, file operations remain in Runnable and DBServer

---

## LIMEService Call Chains

### 1. LIMEService Initialization

**BEFORE:**
```
LIMEService.onCreate()
  └─> SearchSrv = new SearchServer(this)
      ├─> mLIMEPref = new LIMEPreferenceManager(context)
      ├─> dbadapter = new LimeDB(context) [if null]
      └─> initialCache()
```

**AFTER:**
```
LIMEService.onCreate()
  └─> SearchSrv = new SearchServer(this)
      ├─> mLIMEPref = new LIMEPreferenceManager(context)
      ├─> dbadapter = new LimeDB(context) [if null]
      └─> initialCache()
```

**Status**: Already correct - LIMEService uses SearchServer

### 2. LIMEService Key Event Handling

**BEFORE:**
```
LIMEService.onKeyEvent()
  └─> SearchSrv.getMappingByCode(code, softKeyboard, getAllRecords)
      ├─> [Check cache]
      ├─> [If cache miss]
      │   └─> dbadapter.getMappingByCode()
      │       └─> SQLiteDatabase.rawQuery()
      └─> [Cache result and return]
```

**AFTER:**
```
LIMEService.onKeyEvent()
  └─> SearchSrv.getMappingByCode(code, softKeyboard, getAllRecords)
      ├─> [Check cache]
      ├─> [If cache miss]
      │   └─> dbadapter.getMappingByCode()
      │       └─> SQLiteDatabase.rawQuery() [with parameterized queries]
      └─> [Cache result and return]
```

**Status**: Already correct - automatically benefits from LimeDB improvements

### 3. LIMEService Chinese Conversion

**BEFORE:**
```
LIMEService (conversion request)
  └─> SearchSrv.hanConvert(input)
      └─> dbadapter.hanConvert(input, hanOption)
          └─> LimeHanConverter.convert()
              └─> SQLiteDatabase (hanconvertv2.db)
```

**AFTER:**
```
LIMEService (conversion request)
  └─> SearchSrv.hanConvert(input)
      └─> LimeHanConverter.convert() [via LimeDB or directly]
          └─> SQLiteDatabase (hanconvertv2.db)
```

**Status**: Already correct - SearchServer can access LimeHanConverter directly or through LimeDB

### 4. LIMEService Emoji Conversion

**BEFORE:**
```
LIMEService (emoji request)
  └─> SearchSrv.emojiConvert(code, type)
      ├─> [Check emoji cache]
      ├─> [If cache miss]
      │   └─> dbadapter.emojiConvert(code, type)
      │       └─> EmojiConverter.convert()
      │           └─> SQLiteDatabase (emoji.db)
      └─> [Cache result and return]
```

**AFTER:**
```
LIMEService (emoji request)
  └─> SearchSrv.emojiConvert(code, type)
      ├─> [Check emoji cache]
      ├─> [If cache miss]
      │   └─> EmojiConverter.convert() [via LimeDB or directly]
      │       └─> SQLiteDatabase (emoji.db)
      └─> [Cache result and return]
```

**Status**: Already correct - SearchServer can access EmojiConverter directly or through LimeDB

---

## Complete Architecture Call Patterns

### Pattern 1: User Input → Search Results

**BEFORE:**
```
User types key
  └─> LIMEService.onKeyEvent()
      └─> SearchServer.getMappingByCode()
          ├─> [Check cache]
          ├─> [If cache miss]
          │   └─> LimeDB.getMappingByCode()
          │       └─> SQLiteDatabase.rawQuery()
          └─> [Cache result and return]
              └─> LIMEService displays candidates
```

**AFTER:**
```
User types key
  └─> LIMEService.onKeyEvent()
      └─> SearchServer.getMappingByCode()
          ├─> [Check cache]
          ├─> [If cache miss]
          │   └─> LimeDB.getMappingByCode()
          │       └─> SQLiteDatabase.rawQuery() [with parameterized queries]
          └─> [Cache result and return]
              └─> LIMEService displays candidates
```

**Status**: Already correct - automatically benefits from LimeDB improvements

### Pattern 2: UI Configuration → Database Update

**BEFORE:**
```
User changes IM keyboard in UI
  └─> ManageImKeyboardDialog.onItemClick()
      └─> LimeDB.setIMKeyboard() [Direct access - PROBLEM]
          └─> SQLiteDatabase.update()
```

**AFTER:**
```
User changes IM keyboard in UI
  └─> ManageImKeyboardDialog.onItemClick()
      └─> SearchServer.setIMKeyboard() [Via SearchServer]
          └─> LimeDB.setIMKeyboard()
              └─> SQLiteDatabase.update()
```

**Improvement**: UI components use SearchServer instead of direct LimeDB access

### Pattern 3: File Import → Database Update

**BEFORE:**
```
User imports .limedb file
  └─> MainActivity.handleImportTxt()
      ├─> [File download logic - IN ACTIVITY]
      ├─> [File unzip logic - IN ACTIVITY]
      └─> LimeDB.importDB() [Mixed file and DB operations]
          └─> SQLiteDatabase.execSQL()

User imports text file (.lime, .cin)
  └─> DBServer.importTxtTable()
      └─> LimeDB.importTxtTable()
          └─> [File reading and parsing logic]
```

**AFTER:**
```
User imports .limedb file
  └─> MainActivity.performLimedbImport()
      └─> DBServer.importZippedDb() / DBServer.importZippedDbRelated() [Centralized file operations]
          ├─> LIMEUtilities.unzip() [File unzip in DBServer]
          ├─> LimeDB.importDb() / LimeDB.importDbRelated() [SQL operations in LimeDB]
          │   ├─> SQLiteDatabase.execSQL("attach database...")
          │   ├─> SQLiteDatabase.execSQL("insert into...")
          │   └─> SQLiteDatabase.execSQL("detach database...")
          └─> SearchServer.resetCache()
              └─> [Clear all caches]

User imports text file (.lime, .cin)
  └─> DBServer.importTxtTable()
      └─> LimeDB.importTxtTable()
          ├─> [Read file line by line]
          ├─> [Parse delimiter and fields]
          ├─> [Insert records in transaction]
          └─> [Update IM information]
```

**Improvement**: All file operations in DBServer, clear separation of concerns

### Pattern 4: Database Export

**BEFORE:**
```
User exports IM database
  └─> ShareDbRunnable.run()
      ├─> [File copy operations - IN RUNNABLE]
      ├─> LimeDB.prepareBackupDb() [SQL operations]
      └─> LIMEUtilities.zip() [File operations - IN RUNNABLE]

User exports text file
  └─> ShareTxtRunnable / ShareRelatedTxtRunnable
      └─> LimeDB.list() / LimeDB.getAllRelatedRecords()
          └─> [Write to file]
```

**AFTER:**
```
User exports IM database
  └─> ShareDbRunnable.run()
      └─> DBServer.exportZippedDb() / DBServer.exportZippedDbRelated() [Centralized file operations]
          ├─> LIMEUtilities.copyRAWFile() [blank template]
          ├─> LimeDB.prepareBackup() [SQL operations in LimeDB]
          │   ├─> SQLiteDatabase.execSQL("attach database...")
          │   ├─> SQLiteDatabase.execSQL("insert into sourceDB.custom...")
          │   └─> SQLiteDatabase.execSQL("detach database...")
          ├─> LIMEUtilities.zip()
          └─> [Save to user-selected location]

User exports text file
  └─> ShareTxtRunnable / ShareRelatedTxtRunnable
      └─> SearchServer.exportTxtTable()
          └─> LimeDB.exportTxtTable()
              ├─> [Query all records]
              ├─> [Write IM info header]
              └─> [Write records to file]
```

**Improvement**: All file operations centralized in DBServer, text export goes through SearchServer

### Pattern 5: User Records Backup/Restore

**BEFORE:**
```
User reloads mapping file
  └─> SetupImLoadDialog
      ├─> [Manual backup loop in dialog]
      └─> [Manual restore loop in dialog]
```

**AFTER:**
```
User reloads mapping file
  └─> SetupImLoadDialog
      ├─> SearchServer.backupUserRecords()
      │   └─> LimeDB.backupUserRecords()
      │       └─> SQLiteDatabase.execSQL("create table {table}_user as...")
      ├─> [Import new mapping file]
      └─> SearchServer.restoreUserRecords()
          └─> LimeDB.restoreUserRecords()
              ├─> LimeDB.getBackupTableRecords()
              └─> SQLiteDatabase.insert() [restore user records]
```

**Improvement**: User records backup/restore logic centralized in `LimeDB`, accessed through `SearchServer`

### Pattern 6: Database Backup/Restore

**BEFORE:**
```
User triggers full backup
  └─> SetupImFragment (UI)
      └─> DBServer.backupDatabase()
          ├─> LimeDB.holdDBConnection()
          └─> LIMEUtilities.zip() [database files]

User triggers restore
  └─> DBServer.restoreDatabase()
      ├─> LIMEUtilities.unzip()
      └─> [Restore database files]
```

**AFTER:**
```
User triggers full backup
  └─> SetupImFragment (UI)
      └─> DBServer.backupDatabase()
          ├─> LimeDB.holdDBConnection()
          ├─> LIMEUtilities.zip() [database files]
          ├─> DBServer.backupDefaultSharedPreference()
          └─> [Save to user-selected location]

User triggers restore
  └─> DBServer.restoreDatabase()
      ├─> LIMEUtilities.unzip()
      ├─> [Restore database files]
      └─> DBServer.restoreDefaultSharedPreference()
```

**Improvement**: Full backup/restore includes shared preferences

### Pattern 7: Reset Lime Settings

**BEFORE:**
```
User triggers reset
  └─> NavigationDrawerFragment.onMenuItemSelected() [action_reset]
      └─> datasource.resetLimeSetting() [Direct LimeDB access]
          └─> LimeDB.resetLimeSetting()
              ├─> [Close and restore main database]
              ├─> [Close and restore emoji database]
              └─> [Close and restore han converter database]
```

**AFTER:**
```
User triggers reset
  └─> NavigationDrawerFragment.onMenuItemSelected() [action_reset]
      └─> searchServer.resetLimeSetting() [Via SearchServer]
          └─> LimeDB.resetLimeSetting()
              ├─> [Close and restore main database]
              ├─> [Close and restore emoji database]
              └─> [Close and restore han converter database]
```

**Improvement**: Reset operation goes through SearchServer, maintaining architecture compliance

### Pattern 8: Chinese Character Conversion

**BEFORE:**
```
User requests Traditional/Simplified conversion
  └─> LIMEService or UI Component
      └─> SearchServer.hanConvert()
          └─> LimeDB.hanConvert()
              └─> LimeHanConverter.convert()
                  └─> SQLiteDatabase (hanconvertv2.db)
```

**AFTER:**
```
User requests Traditional/Simplified conversion
  └─> LIMEService or UI Component
      └─> SearchServer.hanConvert()
          └─> LimeHanConverter.convert() [via LimeDB or directly]
              └─> SQLiteDatabase (hanconvertv2.db)
```

**Status**: Already correct - SearchServer can access LimeHanConverter directly or through LimeDB

### Pattern 9: Emoji Conversion

**BEFORE:**
```
User requests emoji suggestions
  └─> LIMEService or UI Component
      └─> SearchServer.emojiConvert()
          ├─> [Check emoji cache]
          ├─> [If cache miss]
          │   └─> LimeDB.emojiConvert()
          │       └─> EmojiConverter.convert()
          │           └─> SQLiteDatabase (emoji.db)
          └─> [Cache result and return]
```

**AFTER:**
```
User requests emoji suggestions
  └─> LIMEService or UI Component
      └─> SearchServer.emojiConvert()
          ├─> [Check emoji cache]
          ├─> [If cache miss]
          │   └─> EmojiConverter.convert() [via LimeDB or directly]
          │       └─> SQLiteDatabase (emoji.db)
          └─> [Cache result and return]
```

**Status**: Already correct - SearchServer can access EmojiConverter directly or through LimeDB

---

## Text Export Operations Call Chains

### 1. Export Regular Table to Text File

**BEFORE:**
```
ShareTxtRunnable.run()
  ├─> searchServer.list(table) [Returns Cursor]
  │   └─> LimeDB.list(table)
  │       └─> db.query(table, null, null, null, null, null, null)
  ├─> [Iterate cursor to build List<Record>]
  ├─> [Get IM info]
  └─> [Write to file with BufferedWriter]
      ├─> Write IM info headers
      └─> Write records: code|word|score|basescore
```

**AFTER:**
```
ShareTxtRunnable.run()
  └─> searchServer.exportTxtTable(table, targetFile, imInfo)
      └─> LimeDB.exportTxtTable(table, targetFile, imInfo)
          ├─> [Check if table == LIME.DB_TABLE_RELATED]
          ├─> getAllRecords(table) [Returns List<Record> for regular tables]
          │   └─> getRecords(table, null, false, 0, 0)
          ├─> [Delete existing file if exists]
          └─> [Write to file with BufferedWriter]
              ├─> Write IM info headers (for regular tables)
              └─> Write records: code|word|score|basescore (or pword+cword|basescore|userscore for related)
```

**Improvement**: Export logic centralized in LimeDB, unified method handles both regular and related tables

### 2. Export Related Table to Text File

**BEFORE:**
```
ShareRelatedTxtRunnable.run()
  ├─> searchServer.list(LIME.DB_TABLE_RELATED) [Returns Cursor]
  │   └─> LimeDB.list(LIME.DB_TABLE_RELATED)
  │       └─> db.query(LIME.DB_TABLE_RELATED, null, null, null, null, null, null)
  ├─> [Iterate cursor to build List<Related>]
  └─> [Write to file with BufferedWriter]
      └─> Write records: pword+cword|basescore|userscore
```

**AFTER:**
```
ShareRelatedTxtRunnable.run()
  └─> searchServer.exportTxtTable(LIME.DB_TABLE_RELATED, targetFile, null)
      └─> LimeDB.exportTxtTable(LIME.DB_TABLE_RELATED, targetFile, null)
          ├─> [Check if table == LIME.DB_TABLE_RELATED]
          ├─> getAllRelated() [Returns List<Related>]
          │   └─> getRelated(null, 0, 0)
          ├─> [Delete existing file if exists]
          └─> [Write to file with BufferedWriter]
              └─> Write records: pword+cword|basescore|userscore
```

**Improvement**: Export logic centralized in LimeDB, unified method handles both regular and related tables

### 3. List Operations Refactoring

**BEFORE:**
```
LimeDB.list(String table)
  └─> db.query(table, null, null, null, null, null, null)
      └─> Returns Cursor [Caller must iterate and close]

SearchServer.list(String table)
  └─> LimeDB.list(table)
      └─> Returns Cursor
```

**AFTER:**
```
LimeDB.getAllRecords(String table) [NEW]
  ├─> isValidTableName(table) [Validation]
  └─> getRecords(table, null, false, 0, 0)
      └─> Returns List<Record> [Type-safe, cursor managed internally]

LimeDB.getAllRelated() [NEW - Renamed from getAllRelatedRecords]
  └─> getRelated(null, 0, 0) [Renamed from loadRelated]
      └─> Returns List<Related>

LimeDB.getRelated(String pword, int maximum, int offset) [Renamed from loadRelated]
  └─> [Query related table with pagination]
      └─> Returns List<Related>

[SearchServer methods removed - logic moved to LimeDB]
```

**Improvement**: Methods return List instead of Cursor, more convenient and type-safe

---

## Summary of Improvements

| Aspect | Before | After | Benefit |
|--------|--------|-------|---------|
| **SQL Operations Location** | Scattered across 3+ files | Centralized in `LimeDB.java` | Single source of truth, easier to maintain |
| **File Operations Location** | Scattered across 4+ files | Centralized in `DBServer.java` | Single source of truth, easier to maintain |
| **Count Methods** | 4 similar methods | 1 unified method | 75% reduction in duplication |
| **Backup Methods** | 2 similar methods | 1 unified method | 50% reduction in duplication |
| **Import Methods** | 3 similar methods | 1 unified method | 67% reduction in duplication |
| **Query Helpers** | Inline logic in each method | Reusable helper methods | Easier to maintain, consistent behavior |
| **Runnable Classes** | 50-100 lines with mixed concerns | 1-5 lines, thin wrappers | Clear separation, easier to test |
| **Activity File Handling** | 50+ lines of file operations | 1 method call to DBServer | Cleaner Activity, better separation |
| **Parameterized Queries** | Some methods use string concatenation | All methods use parameterized queries | Enhanced security, prevents SQL injection |
| **UI Component Access** | Direct LimeDB access | All via SearchServer | Consistent access pattern, centralized caching |
| **SearchServer Integration** | Already uses LimeDB correctly | Automatically benefits from LimeDB improvements | No changes needed, follows correct pattern |
| **Text Export Operations** | Scattered in Runnable classes | Centralized in unified `LimeDB.exportTxtTable()` (handles both regular and related tables) | Single source of truth, easier to maintain |
| **List Operations** | Returns Cursor (`list()`) | Returns List (`getAllRecords()`, `getAllRelated()`) | More convenient, type-safe |
| **Related Phrase Methods** | `loadRelated()` | `getRelated()` | Consistent naming with other get methods |
