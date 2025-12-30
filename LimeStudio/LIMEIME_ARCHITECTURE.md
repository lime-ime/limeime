# LimeIME Architecture

## Overview

This document describes the overall architecture of the LimeIME (LIME Input Method Engine) Android application, focusing on the relationships and interactions between the core components: `LIMEService`, `DBServer`, `SearchServer`, and UI components.

## Architecture Principles

1. **Separation of Concerns**: Each component has a clear, focused responsibility
2. **Single Interface**: `SearchServer` is the single interface for all database operations
3. **Centralized Operations**: 
   - `LimeDB`: All low-level SQL operations
   - `DBServer`: All file operations (zip, unzip, import, export, backup, restore)
   - `SearchServer`: All database operations (search, query, configuration)
4. **No Direct Database Access**: UI components and `LIMEService` should not call `LimeDB` directly

---

## Core Components

### 1. LIMEService

**Role**: Android Input Method Service - handles user input and provides IME functionality

**Responsibilities:**
- Handle keyboard input events
- Display candidate suggestions
- Manage input method lifecycle
- Coordinate with SearchServer for search operations
- Manage UI state and keyboard switching

**Dependencies:**
- `SearchServer` - For all database operations (search, configuration)
- `LIMEPreferenceManager` - For user preferences
- Android IME framework

**Key Methods:**
- `onKeyEvent()` - Handles key presses
- `onCreate()` - Initializes SearchServer
- `switchKeyboard()` - Switches between input methods

**Architecture Position:**
```
LIMEService (IME Service Layer)
  └─> SearchServer (Database Interface Layer)
      └─> LimeDB (SQL Operations Layer)
```

---

### 2. SearchServer

**Role**: Single interface for all database operations - search, query, and configuration

**Responsibilities:**
- Provide search/query operations with caching
- Provide IM configuration operations
- Manage cache for performance
- Coordinate runtime phrase suggestions
- Handle user dictionary learning

**Dependencies:**
- `LimeDB` - For all SQL operations (main database and helper converters)
- `LIMEPreferenceManager` - For user preferences
- `Context` - For application context

**Helper Converters (Separate Databases):**
- `LimeHanConverter` - Manages separate `hanconvertv2.db` for Traditional/Simplified Chinese conversion
- `EmojiConverter` - Manages separate `emoji.db` for emoji conversion
- **Note**: `SearchServer` can access these converters directly or through `LimeDB`. They manage their own separate databases.

**Key Operations:**

#### Search/Query Operations
- `getMappingByCode()` - Search record mappings by code (with caching)
- `getRelatedPhrase()` - Get related phrase suggestions
- `getEnglishSuggestions()` - Get English record suggestions
- `addLDPhrase()` - Add learning dictionary phrase
- `learnRelatedPhraseAndUpdateScore()` - Learn phrase patterns

#### Configuration Operations
- `getKeyboardList()` - Get list of available keyboards
- `getImList()` - Get list of available input methods
- `getIm(code, type)` - Get IM list by type (for UI)
- `getKeyboard()` - Get keyboard list (for UI)
- `getImInfo()`, `setImInfo()` - Get/set IM configuration info
- `setIMKeyboard()` - Set IM keyboard assignment
- `getTablename()`, `setTablename()` - Manage active IM table (part of search logic)
- `getSelkey()` - Get selection key mapping (with caching)
- `keyToKeyname()` - Convert code to key names (with caching)

#### Utility Operations
- `hanConvert()` - Traditional/Simplified Chinese conversion (uses separate `hanconvertv2.db`)
- `emojiConvert()` - Emoji conversion (uses separate `emoji.db`, with caching)
- `getCodeListStringFromWord()` - Reverse lookup (record to code)

**Architecture Position:**
```
SearchServer (Database Interface Layer)
  ├─> Receives requests from LIMEService
  ├─> Receives requests from UI Components
  ├─> Delegates to LimeDB for SQL operations (main database)
  ├─> Accesses LimeHanConverter for Chinese conversion (separate database)
  └─> Accesses EmojiConverter for emoji conversion (separate database)
```

**State Management:**
- Maintains `tablename` state (current active IM table)
- Maintains `hasNumberMapping`, `hasSymbolMapping` flags
- Manages multiple caches (mapping cache, emoji cache, keyname cache, etc.)

---

### 3. DBServer

**Role**: Singleton service for database file operations

**Responsibilities:**
- Handle file import/export operations
- Manage database backup/restore
- Coordinate file compression/decompression
- Manage database file operations (zip, unzip)

**Dependencies:**
- `LimeDB` - For database operations during import/export
- `LIMEUtilities` - For file operations (zip, unzip)
- `SearchServer` - For cache invalidation after operations
- `Context` - For application context

**Key Operations:**

#### File Import Operations
- `importTxtTable(String filename, String tablename, LIMEProgressListener)` - Import text mapping file (.lime, .cin) from file path
- `importTxtTable(File sourcefile, String tablename, LIMEProgressListener)` - Import text mapping file (.lime, .cin) from File object
- `importDb(File sourcedb, String tableName)` - Import uncompressed database file into specified IM table
- `importZippedDb(File compressedSourceDB, String tableName)` - Import compressed database file (.limedb) into specified IM table (handles unzip)
- `importDbRelated(File sourcedb)` - Import uncompressed related database file into related table
- `importZippedDbRelated(File compressedSourceDB)` - Import compressed related database file (.limedb) into related table (handles unzip)

#### File Export Operations
- `exportZippedDb(String tableName, File targetFile, Runnable progressCallback)` - Export IM database to zipped .limedb file (renamed from `exportImDatabase`)
- `exportZippedDbRelated(File targetFile, Runnable progressCallback)` - Export related phrase database to zipped .limedb file (renamed from `exportRelatedDatabase`)

#### Backup/Restore Operations
- `backupDatabase(Uri uri)` - Backup entire database and preferences to URI (for Android backup service)
- `restoreDatabase(Uri uri)` - Restore database and preferences from URI (for Android backup service)
- `restoreDatabase(String srcFilePath)` - Restore database and preferences from file path
- `backupDefaultSharedPreference(File sharePrefs)` - Backup shared preferences to file
- `restoreDefaultSharedPreference(File sharePrefs)` - Restore shared preferences from file

#### File Operations
- `zip()` - Compress file to zip (renamed from `compressFile`)
- `unzip()` - Decompress zip file (renamed from `decompressFile`)

**Architecture Position:**
```
DBServer (File Operations Layer)
  ├─> Receives requests from UI Components
  ├─> Uses LimeDB for database operations
  ├─> Uses LIMEUtilities for file operations
  └─> Notifies SearchServer to reset cache after operations
```

**Singleton Pattern:**
- Uses double-checked locking for thread-safe singleton
- Stores ApplicationContext to prevent memory leaks
- Shared instance across all components

---

### 4. LimeDB

**Role**: SQL operations layer for all database queries and updates

**Responsibilities:**
- Execute all SQL operations on the main database
- Manage database schema and migrations
- Provide low-level database access methods
- Handle database connection management
- Validate table names to prevent SQL injection

**Dependencies:**
- `SQLiteDatabase` - Main database (lime.db)
- `LimeHanConverter` - Separate database for Chinese conversion (hanconvertv2.db)
- `EmojiConverter` - Separate database for emoji conversion (emoji.db)
- `Context` - For application context

**Key Operations:**

#### Database Import Operations
- `importDb(File sourceFile, List<String> tableNames, boolean includeRelated, boolean overwriteExisting)` - Unified method to import database tables from backup file (supports backup format and direct format)
- `importDbRelated(File sourcedbfile)` - Import related phrase data from backup database file (convenience wrapper)
- `importTxtTable(String table, LIMEProgressListener progressListener)` - Import text mapping file (.lime, .cin, delimited text) into database table

#### Database Export Operations
- `exportTxtTable(String table, File targetFile, List<Im> imInfo)` - Export database table to text file format (.lime format with IM info header)

#### Database Backup Operations
- `prepareBackup(File targetFile, List<String> tableNames, boolean includeRelated)` - Unified method to prepare backup of database tables (creates backup database file with attached sourceDB)
- `prepareBackupDb(String sourcedbfile, String sourcetable)` - Prepare backup for single table (deprecated wrapper, use `prepareBackup()`)
- `prepareBackupRelatedDb(String sourcedbfile)` - Prepare backup for related table (deprecated wrapper, use `prepareBackup()`)
- `backupUserRecords(String table)` - Backup user-learned records (score > 0) to backup table (table + "_user")

#### Database Restore Operations
- `restoreUserRecords(String table)` - Restore user-learned records from backup table (table + "_user") back to main table
- `getBackupTableRecords(String backupTableName)` - Get all records from backup table (returns Cursor)

#### Backup Table Management
- `checkBackupTable(String table)` - Check if backup table exists and has records (returns boolean)

**Architecture Position:**
```
LimeDB (SQL Operations Layer)
  ├─> SQLiteDatabase (main database - lime.db)
  ├─> LimeHanConverter (separate database - hanconvertv2.db)
  └─> EmojiConverter (separate database - emoji.db)
```

**Important Notes:**
- All SQL operations are centralized in `LimeDB`
- Table name validation is enforced to prevent SQL injection
- Database connection management (hold/release) is handled internally
- Methods use parameterized queries where possible
- Backup format: Uses `sourceDB.custom` table for mapping data
- Direct format: Uses `sourceDB.{tableName}` for direct table-to-table copy

---

### 5. UI Components

**Role**: User interface for configuration and management

**Components:**
- `MainActivity` - Main settings activity
- `SetupImFragment` - Setup input methods
- `ManageImFragment` - Manage IM record dictionary
- `ManageRelatedFragment` - Manage related phrases
- `ImportDialog` - Import text/files
- `ShareDialog` - Share/export databases
- `SetupImLoadDialog` - Load IM from remote/download
- `SetupImFragment` - Main UI fragment for IM setup, handles text file imports via `importTxtTable()`
- Various dialogs and fragments

**Responsibilities:**
- Display IM configuration options
- Allow users to import/export databases
- Manage record dictionaries
- Configure input method settings

**Dependencies:**
- `SearchServer` - For all database operations (search, configuration)
- `DBServer` - For file operations (import, export, backup, restore)
- Android UI framework

**Architecture Position:**
```
UI Components (Presentation Layer)
  ├─> SearchServer (for database operations)
  │   └─> LimeDB (SQL operations)
  └─> DBServer (for file operations)
      └─> LimeDB (SQL operations)
```

**Important**: UI components should **NOT** create `LimeDB` instances directly. All database operations (including `hanConvert` and `emojiConvert`) should go through `SearchServer`.

---

## Component Relationships

### Relationship Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Android Application                      │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                       │
        ▼                     ▼                       ▼
┌──────────────┐    ┌──────────────┐      ┌─────────────────┐
│ LIMEService  │    │ UI Components │      │  DBServer       │
│  (IME Layer) │    │ (UI Layer)    │      │ (File Ops)      │
└──────────────┘    └──────────────┘      └─────────────────┘
        │                     │                       │
        │                     │                       │
        └─────────────────────┼──────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │  SearchServer   │
                    │ (DB Interface)  │
                    └─────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                       │
        ▼                     ▼                       ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│    LimeDB       │  │ LimeHanConverter│  │ EmojiConverter  │
│  (SQL Layer)    │  │ (hanconvertv2.db)│  │ (emoji.db)      │
└─────────────────┘  └─────────────────┘  └─────────────────┘
        │                     │                       │
        ▼                     ▼                       ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  SQLiteDatabase │  │  SQLiteDatabase │  │  SQLiteDatabase │
│  (Main DB)      │  │ (hanconvertv2.db)│  │ (emoji.db)      │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

### Data Flow Patterns

#### Pattern 1: User Input → Search Results

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

#### Pattern 2: UI Configuration → Database Update

```
User changes IM keyboard in UI
  └─> ManageImKeyboardDialog.onItemClick()
      └─> SearchServer.setIMKeyboard()
          └─> LimeDB.setIMKeyboard()
              └─> SQLiteDatabase.update()
```

#### Pattern 3: File Import → Database Update

```
User imports .limedb file
  └─> MainActivity.performLimedbImport()
      └─> DBServer.importZippedDb() / DBServer.importZippedDbRelated()
          ├─> LIMEUtilities.unzip()
          ├─> LimeDB.importDb() / LimeDB.importDbRelated()
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

#### Pattern 4: Database Export → File

```
User exports database
  └─> ShareDialog (UI)
      └─> DBServer.exportZippedDb() / DBServer.exportZippedDbRelated()
          ├─> LIMEUtilities.copyRAWFile() [blank template]
          ├─> LimeDB.prepareBackup()
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

#### Pattern 5: User Records Backup/Restore

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

#### Pattern 6: Database Backup/Restore

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

#### Pattern 7: Chinese Character Conversion

```
User requests Traditional/Simplified conversion
  └─> LIMEService or UI Component
      └─> SearchServer.hanConvert()
          └─> LimeHanConverter.convert() [via LimeDB or directly]
              └─> SQLiteDatabase (hanconvertv2.db)
```

**Note**: `SearchServer` can access `LimeHanConverter` directly or through `LimeDB`. The converter manages its own separate database.

#### Pattern 8: Emoji Conversion

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

**Note**: `SearchServer` can access `EmojiConverter` directly or through `LimeDB`. The converter manages its own separate database.

---

## Component Responsibilities Summary

| Component | Primary Responsibility | Key Operations | Dependencies |
|-----------|----------------------|----------------|-------------|
| **LIMEService** | IME functionality | Handle input, display candidates | SearchServer, LIMEPreferenceManager |
| **SearchServer** | Database interface | Search, query, configuration, conversions | LimeDB, LIMEPreferenceManager |
| **DBServer** | File operations | Import, export, backup, restore | LimeDB, LIMEUtilities, SearchServer |
| **LimeDB** | SQL operations | All database queries, imports, exports, backups, restores | SQLiteDatabase (main), LimeHanConverter, EmojiConverter |
| **LimeHanConverter** | Chinese conversion | Traditional/Simplified conversion | SQLiteDatabase (hanconvertv2.db) |
| **EmojiConverter** | Emoji conversion | Emoji suggestions | SQLiteDatabase (emoji.db) |
| **UI Components** | User interface | Display, configuration, management | SearchServer, DBServer |

---

## Access Patterns

### ✅ Correct Access Patterns

```java
// LIMEService accessing database
SearchServer searchSrv = new SearchServer(this);
List<Mapping> results = searchSrv.getMappingByCode(code, softKeyboard, getAllRecords);

// UI Component accessing database
SearchServer searchServer = new SearchServer(getActivity());
List<Im> imList = searchServer.getIm(null, LIME.IM_TYPE_NAME);

// UI Component accessing file operations
DBServer dbServer = DBServer.getInstance(activity);
dbServer.importZippedDb(file, tableName);
dbServer.importZippedDbRelated(file);

// LIMEService or UI Component accessing conversions
SearchServer searchServer = new SearchServer(context);
String converted = searchServer.hanConvert(input);  // Chinese conversion
List<Mapping> emojis = searchServer.emojiConvert(code, type);  // Emoji conversion
```

### ❌ Incorrect Access Patterns (Should Be Avoided)

```java
// ❌ Direct LimeDB access from UI
LimeDB datasource = new LimeDB(getActivity());
List<Im> imList = datasource.getIm(null, LIME.IM_TYPE_NAME);  // WRONG

// ❌ Direct LimeDB access from LIMEService
LimeDB db = new LimeDB(this);
List<Mapping> results = db.getMappingByCode(...);  // WRONG
```

**Why avoid direct access:**
- Bypasses caching in SearchServer
- Inconsistent access patterns
- Harder to add validation/transformation
- Duplicates database connection management

---

## State Management

### SearchServer State

**Static State (Shared across instances):**
```java
private static String tablename = "";           // Current active IM table
private static boolean hasNumberMapping;        // IM supports number keys
private static boolean hasSymbolMapping;        // IM supports symbol keys
private static LimeDB dbadapter = null;        // Shared LimeDB instance
```

**Instance State:**
```java
private final LIMEPreferenceManager mLIMEPref;  // Preferences
private final Context mContext;                  // Application context
```

**Cache State:**
```java
private static ConcurrentHashMap<String, List<Mapping>> cache;        // Mapping cache
private static ConcurrentHashMap<String, List<Mapping>> engcache;     // English cache
private static ConcurrentHashMap<String, List<Mapping>> emojicache;    // Emoji cache
private static ConcurrentHashMap<String, String> keynamecache;          // Keyname cache
private final HashMap<String, String> selKeyMap;                       // Selkey cache
```

### DBServer State

**Singleton State:**
```java
private static volatile DBServer instance = null;  // Singleton instance
private final Context appContext;                  // Application context
protected LimeDB datasource = null;                // Shared LimeDB instance
```

---

## Lifecycle and Initialization

### SearchServer Lifecycle

```java
// Created per-instance (not singleton)
SearchServer searchServer = new SearchServer(context);
  ├─> Creates/gets shared LimeDB instance
  ├─> Initializes LIMEPreferenceManager
  └─> Initializes caches

// Used throughout component lifetime
searchServer.getMappingByCode(...);
searchServer.setTablename(...);

// Caches persist until reset
SearchServer.resetCache(true);  // Clears all caches
```

### DBServer Lifecycle

```java
// Singleton - get instance
DBServer dbServer = DBServer.getInstance(context);
  ├─> Creates singleton instance (first call only)
  ├─> Creates/gets shared LimeDB instance
  └─> Stores ApplicationContext

// Used for file operations
dbServer.importZippedDb(...);
dbServer.importZippedDbRelated(...);
dbServer.exportZippedDb(...);

// Instance persists for app lifetime
```

### LIMEService Lifecycle

```java
// Created by Android system
LIMEService.onCreate()
  └─> new SearchServer(this)
      └─> [SearchServer initialized]

// Used during IME operation
LIMEService.onKeyEvent()
  └─> searchSrv.getMappingByCode(...)

// Destroyed by Android system
LIMEService.onDestroy()
  └─> [SearchServer instance can be garbage collected]
```

---

## Cache Management

### SearchServer Cache Strategy

**Cache Types:**
1. **Mapping Cache** - Caches `getMappingByCode()` results
   - Key: `tablename + keyboardType + code`
   - Invalidated when: Table changes, database updated

2. **Emoji Cache** - Caches `emojiConvert()` results
   - Key: `code + type`
   - Persists until reset

3. **Keyname Cache** - Caches `keyToKeyname()` results
   - Key: `tablename + keyboardType + code`
   - Persists until reset

4. **Selkey Cache** - Caches `getSelkey()` results
   - Key: `tablename`
   - Persists until reset

**Cache Invalidation:**
```java
// When database is updated
DBServer.importTxtTable()
  └─> SearchServer.resetCache(true)
      └─> [All caches cleared]

// When IM table changes
SearchServer.setTablename()
  └─> [Cache keys will use new tablename]
```

---

## Thread Safety

### SearchServer

**Thread Safety Considerations:**
- Static caches use `ConcurrentHashMap` (thread-safe)
- Static state (`tablename`, flags) - potential race conditions
- Instance methods - not thread-safe (create per-thread if needed)

**Current Usage:**
- `LIMEService` creates one instance (main thread)
- UI components create instances as needed (main thread)
- Search operations may be called from background threads

**Recommendation:**
- Most operations are called from main/UI thread
- If background threads are used, ensure proper synchronization

### DBServer

**Thread Safety:**
- Singleton uses double-checked locking (thread-safe)
- File operations should be on background threads
- Database operations coordinate with LimeDB connection management

---

## Error Handling

### SearchServer Error Handling

```java
// Methods throw RemoteException for compatibility
public List<Mapping> getMappingByCode(...) throws RemoteException {
    try {
        // ... operation
    } catch (Exception e) {
        Log.e(TAG, "Error in search operation", e);
        return null;  // or empty list
    }
}
```

### DBServer Error Handling

```java
// Methods return boolean/null for error indication
public void importZippedDb(File file, String tableName) {
    try {
        // ... operation
    } catch (Exception e) {
        Log.e(TAG, "Error importing file", e);
        return false;
    }
}
```

---

## Migration Status

### Current State

**✅ Already Using SearchServer:**
- `LIMEService` - All operations through SearchServer
- Some UI components - Partial usage

**❌ Still Using Direct LimeDB:**
- `SetupImFragment` - Uses `datasource.getIm()`
- `ManageImFragment` - Uses `datasource.getIm()`, `datasource.getKeyboard()`
- `ManageImKeyboardDialog` - Uses `datasource.getKeyboard()`, `datasource.setImKeyboard()`
- `ImportDialog` - Uses `datasource.getIm()`
- `ShareDialog` - Uses `datasource.getIm()`
- `SetupImLoadRunnable` - Uses `datasource.setImInfo()`, `datasource.setIMKeyboard()`

### Target State

**All components should use:**
- `SearchServer` for all database operations
- `DBServer` for all file operations
- No direct `LimeDB` access

---

## Best Practices

### 1. Always Use SearchServer for Database Operations

```java
// ✅ Correct
SearchServer searchServer = new SearchServer(context);
List<Im> imList = searchServer.getIm(null, LIME.IM_TYPE_NAME);

// ❌ Incorrect
LimeDB datasource = new LimeDB(context);
List<Im> imList = datasource.getIm(null, LIME.IM_TYPE_NAME);
```

### 2. Use DBServer for File Operations

```java
// ✅ Correct
DBServer dbServer = DBServer.getInstance(context);
dbServer.importZippedDb(file, tableName);
dbServer.importZippedDbRelated(file);

// ❌ Incorrect
// Don't implement file operations in UI components
```

### 3. Create SearchServer Per-Component

```java
// ✅ Correct - Each component creates its own instance
public class SetupImFragment extends Fragment {
    private SearchServer searchServer;
    
    public void onCreateView(...) {
        searchServer = new SearchServer(getActivity());
    }
}
```

### 4. Use DBServer Singleton

```java
// ✅ Correct - Use singleton
DBServer dbServer = DBServer.getInstance(context);

// ❌ Incorrect - Don't create new instances
DBServer dbServer = new DBServer(context);  // Constructor is private
```

---

## Summary

### Architecture Layers

1. **Presentation Layer**: UI Components, LIMEService
2. **Service Layer**: SearchServer (database), DBServer (files)
3. **Data Access Layer**: LimeDB (SQL operations)
4. **Storage Layer**: 
   - SQLiteDatabase (main database)
   - LimeHanConverter → SQLiteDatabase (hanconvertv2.db)
   - EmojiConverter → SQLiteDatabase (emoji.db)

### Key Principles

1. **SearchServer** is the single interface for all database operations
2. **DBServer** handles all file operations
3. **No direct LimeDB access** from UI or LIMEService
4. **Clear separation** of concerns between components
5. **Centralized caching** in SearchServer
6. **Consistent access patterns** across all components

### Benefits

- ✅ Single interface for database operations
- ✅ Centralized caching and performance optimization
- ✅ Consistent access patterns
- ✅ Easier to add validation/transformation
- ✅ Better maintainability
- ✅ Clear component responsibilities

This architecture provides a clean, maintainable, and scalable foundation for the LimeIME application.

