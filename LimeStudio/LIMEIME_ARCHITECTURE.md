# LimeIME Architecture

## Overview

This document describes the overall architecture of the LimeIME (LIME Input Method Engine) Android application, focusing on the relationships and interactions between the core components: `LIMEService`, `DBServer`, `SearchServer`, UI components, and the IME logic flow including query paths and learning paths.

## Architecture Principles

1. **Separation of Concerns**: Each component has a clear, focused responsibility
2. **Single Interface**: `SearchServer` is the single interface for all database operations
3. **Centralized Operations**:
   - `LimeDB`: All low-level SQL operations
   - `DBServer`: All file operations (zip, unzip, import, export, backup, restore)
   - `SearchServer`: All database operations (search, query, configuration)
4. **No Direct Database Access**: UI components and `LIMEService` should not call `LimeDB` directly

---

## Architecture Diagram

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

## Core Components

### 1. LIMEService

**Role**: Android Input Method Service - handles user input and provides IME functionality

**Responsibilities:**
- Handle keyboard input events (soft keyboard and hardware keyboard)
- Display candidate suggestions via CandidateView
- Manage input method lifecycle
- Coordinate with SearchServer for search operations
- Manage composing text and InputConnection operations
- Handle learning flow (score updates, related phrases, LD phrases)
- Manage UI state and keyboard switching

**Key Sub-components:**
- **LIMEKeyboardView**: Soft keyboard rendering and touch handling
  - LIMEKeyboard: Keyboard layout management
  - LIMEBaseKeyboard: Base keyboard functionality
  - LIMEKeyboardSwitcher: Keyboard type switching
- **CandidateView/CandidateViewContainer**: Candidate word display
  - Candidate selection handling
  - Related phrase display
  - CandidateViewHandler: Candidate list management

**Dependencies:**
- `SearchServer` - For all database operations (search, configuration)
- `LIMEPreferenceManager` - For user preferences
- Android IME framework

**Key Methods:**
- `onKey()` - Handles soft keyboard key presses
- `onKeyDown()` / `onKeyUp()` - Handles hardware keyboard events
- `onCreate()` - Initializes SearchServer
- `updateCandidates()` - Updates candidate list based on composing text
- `pickCandidateManually()` - Handles candidate selection
- `commitText()` - Commits selected text to InputConnection
- `switchKeyboard()` - Switches between input methods
- `postFinishInput()` - Triggers learning flow at session end

**Architecture Position:**
```
LIMEService (IME Service Layer)
  ├─> Input Handling Layer (onKey, onKeyDown, onKeyUp)
  ├─> LIMEKeyboardView (Soft Keyboard)
  ├─> CandidateView (Candidate Window)
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
- Handle user dictionary learning (score updates, related phrases, LD phrases)

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
- `learnRelatedPhraseAndUpdateScore()` - Learn phrase patterns and update scores

#### Learning Operations
- `learnRelatedPhraseAndUpdateScore(mapping)` - Update score and add to learning scorelist
- `learnRelatedPhrase(scorelist)` - Create related phrase records from consecutive word selections
- `learnLDPhrase(phraselist)` - Create LD (Learning Dictionary) phrase records with Quick Phrase codes

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
- Manages learning scorelist for session-based learning

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
- `exportTxtTable(String table, File targetFile, List<Im> imConfigInfo)` - Export database table to text file format (.lime format with IM info header)

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
- `MainActivity` - Main settings activity with MVC architecture
  - `NavigationManager` - Manages fragment navigation and IM list
  - `ProgressManager` - Manages progress overlay UI
  - `ShareManager` - Manages sharing/export operations
- `SetupImFragment` - Setup input methods
- `ManageImFragment` - Manage IM record dictionary
- `ManageRelatedFragment` - Manage related phrases
- `ImportDialog` - Import text/files
- `ShareDialog` - Share/export databases
- `SetupImLoadDialog` - Load IM from remote/download
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

## Data Transfer Objects (DTOs)

### Overview

The application uses a set of Data Transfer Objects (DTOs) to represent domain entities and transfer data between layers. All DTOs are pure POJOs (Plain Old Java Objects) with no database code - database operations are centralized in `LimeDB`.

### DTO Class Hierarchy

```
Mapping (Core unified data model)
    ├── Record (extends Mapping - UI alias for Setup/Manage IM)
    └── Related (extends Mapping - UI alias for Related Phrases)

Im (Input Method configuration)

Keyboard (Keyboard layout configuration)

ImKeyboardConfig (Lightweight IM + Keyboard pair)

NavigationMenuItem (wraps Im with navigation metadata)

ChineseSymbol (Utility class providing symbol data)
```

---

### Core DTOs

#### 1. Mapping

**Location**: `net.toload.main.hd.data.Mapping`

**Purpose**: Unified data model serving multiple purposes throughout the application

**Use Cases:**
- IME candidates (code → word mappings)
- Database records (for import/export and management UI)
- Related phrases (parent → child word associations)

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Unique identifier |
| `code` | String | Input code (e.g., "ㄋㄧ" for "你") |
| `codeorig` | String | Original code before processing |
| `code3r` | String | Code without tone keys |
| `word` | String | Output word/phrase |
| `pword` | String | Parent word (for related phrases) |
| `related` | String | Related phrase info |
| `score` | int | User score (learned frequency) |
| `basescore` | int | Base/initial score |
| `highLighted` | Boolean | UI highlight flag |
| `recordType` | int | Record type classification |

**Record Types:**

| Constant | Value | Description |
|----------|-------|-------------|
| `RECORD_COMPOSING_CODE` | 1 | User's current composing text |
| `RECORD_EXACT_MATCH_TO_CODE` | 2 | Exact match to input code |
| `RECORD_PARTIAL_MATCH_TO_CODE` | 3 | Partial/fuzzy match to code |
| `RECORD_RELATED_PHRASE` | 4 | Related phrase suggestion |
| `RECORD_ENGLISH_SUGGESTION` | 5 | English word suggestion |
| `RECORD_RUNTIME_BUILT_PHRASE` | 6 | Runtime-generated phrase |
| `RECORD_CHINESE_PUNCTUATION_SYMBOL` | 7 | Chinese punctuation |
| `RECORD_HAS_MORE_RECORDS_MARK` | 8 | "More results available" marker |
| `RECORD_EXACT_MATCH_TO_WORD` | 9 | Exact match to word (reverse) |
| `RECORD_PARTIAL_MATCH_TO_WORD` | 10 | Partial match to word (reverse) |
| `RECORD_COMPLETION_SUGGESTION_WORD` | 11 | Completion suggestion |
| `RECORD_EMOJI_WORD` | 12 | Emoji suggestion |

**Key Methods:**
```java
// Record type checkers
boolean isExactMatchToCodeRecord()
boolean isPartialMatchToCodeRecord()
boolean isRelatedPhraseRecord()
boolean isEnglishSuggestionRecord()
// ... and others

// Copy constructor
Mapping(Mapping other)

// Standard getters/setters
String getWord()
void setWord(String word)
int getScore()
void setScore(int score)
// ... etc
```

**Example Usage:**
```java
// Query path - display candidates
List<Mapping> candidates = searchServer.getMappingByCode("ㄋㄧ", softKeyboard, false);
for (Mapping mapping : candidates) {
    if (mapping.isExactMatchToCodeRecord()) {
        // Display: "你" (score: 150)
    }
}

// Learning path - update score
Mapping selected = candidates.get(0);
searchServer.learnRelatedPhraseAndUpdateScore(selected);
```

---

#### 2. Record

**Location**: `net.toload.main.hd.data.Record`

**Purpose**: UI alias for `Mapping` used in Setup/Manage IM contexts

**Inheritance**: `extends Mapping`

**Use Cases:**
- ManageImFragment (record dictionary management)
- Database record import/export operations
- Record editing dialogs

**Why Separate Class:**
- Provides semantic distinction - "Record" terminology in UI/database maintenance contexts
- Same underlying data structure as `Mapping`
- No additional fields or methods

**Example Usage:**
```java
// In ManageImFragment
List<Record> records = searchServer.getRecordsByWord(searchWord);
for (Record record : records) {
    // Display record in list
    String display = record.getCode() + " → " + record.getWord();
}
```

---

#### 3. Related

**Location**: `net.toload.main.hd.data.Related`

**Purpose**: UI alias for `Mapping` used in Related Phrases management

**Inheritance**: `extends Mapping`

**Additional Accessors:**
```java
String getPword()      // Parent word
String getCword()      // Child word (alias for word)
int getUserscore()     // User score (alias for score)
int getBasescore()     // Base score
```

**Use Cases:**
- ManageRelatedFragment (related phrase dictionary management)
- Related phrase learning operations
- Related phrase editing dialogs

**Example Usage:**
```java
// In ManageRelatedFragment
List<Related> phrases = searchServer.getRelatedPhrase("你");
for (Related phrase : phrases) {
    // Display: "你" → "好" (score: 25)
    String display = phrase.getPword() + " → " + phrase.getCword()
                   + " (score: " + phrase.getUserscore() + ")";
}
```

---

#### 4. Im

**Location**: `net.toload.main.hd.data.ImConfig`

**Purpose**: Represents an Input Method (IM) configuration

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | int | Database ID |
| `code` | String | IM identifier (e.g., "boshiamy", "phonetic") |
| `title` | String | Display title (e.g., "嘸蝦米") |
| `desc` | String | Description |
| `keyboard` | String | Assigned keyboard code |
| `disable` | boolean | Enable/disable flag |
| `selkey` | String | Selection keys (e.g., "123456789") |
| `endkey` | String | End keys configuration |
| `spacestyle` | String | Space key behavior |

**Key Methods:**
```java
// Factory method from database cursor
static Im get(Cursor cursor)

// Safe cursor parsing helpers
static String getCursorString(Cursor cursor, String column)
static int getCursorInt(Cursor cursor, String column)

// Standard getters/setters
String getCode()
String getDesc()
boolean getDisable()
```

**Example Usage:**
```java
// Load available IMs for setup
List<Im> imConfigList = searchServer.getImList(null, LIME.IM_FULL_NAME);
for (Im imConfig : imConfigList) {
    // Display: "嘸蝦米 (boshiamy)"
    String display = imConfig.getDesc() + " (" + imConfig.getCode() + ")";
}

// Configure IM keyboard
searchServer.setIMKeyboard(imConfig.getCode(), "bpmf");
```

**Database Schema:**
```sql
CREATE TABLE imConfig (
    id INTEGER PRIMARY KEY,
    code TEXT,
    title TEXT,
    desc TEXT,
    keyboard TEXT,
    disable INTEGER,
    selkey TEXT,
    endkey TEXT,
    spacestyle TEXT
)
```

---

#### 5. Keyboard

**Location**: `net.toload.main.hd.data.Keyboard`

**Purpose**: Represents keyboard layout configuration with support for different types and shift variants

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id`, `code`, `name` | String | Identity fields |
| `desc` | String | Description |
| `type` | String | Keyboard type |
| `image` | String | Image resource reference |
| `imkb`, `imshiftkb` | String | IM keyboard and shift variant |
| `engkb`, `engshiftkb` | String | English keyboard variants |
| `symbolkb`, `symbolshiftkb` | String | Symbol keyboard variants |
| `defaultkb`, `defaultshiftkb` | String | Default keyboard variants |
| `extendedkb`, `extendedshiftkb` | String | Extended keyboard variants |
| `disable` | boolean | Enable/disable flag |

**Key Methods:**
```java
// Get keyboard with optional number row
String getEngkb()
String getEngkb(boolean showNumberRow)

// Standard getters/setters
String getCode()
String getImkb()
String getImshiftkb()
```

**Example Usage:**
```java
// Load keyboards for selection
List<Keyboard> keyboards = searchServer.getKeyboardList();
for (Keyboard kb : keyboards) {
    // Display: "注音 (bpmf)"
    String display = kb.getName() + " (" + kb.getCode() + ")";
}

// Assign keyboard to IM
searchServer.setIMKeyboard(imCode, keyboard.getCode());
```

---

#### 6. ImKeyboardConfig

**Location**: `net.toload.main.hd.data.ImKeyboardConfig`

**Purpose**: Lightweight DTO containing only IM code and keyboard assignment

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `code` | String | IM code |
| `keyboard` | String | Keyboard assignment |

**Key Methods:**
```java
String getCode()
void setCode(String code)
String getKeyboard()
void setKeyboard(String keyboard)
```

**Example Usage:**
```java
// Simplified IM configuration transfer
ImKeyboardConfig config = new ImKeyboardConfig();
config.setCode("phonetic");
config.setKeyboard("bpmf");

// Used in lightweight contexts where full Im object is unnecessary
```

---

### Navigation UI DTOs

#### 7. NavigationMenuItem

**Location**: `net.toload.main.hd.ui.view.NavigationMenuItem`

**Purpose**: Wraps an `Im` object with navigation menu context

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `imConfig` | Im | The Im object (or null for fixed items) |
| `position` | int | Position in navigation menu |
| `isSelected` | boolean | Selection state |

**Key Methods:**
```java
Im getIm()
int getPosition()
boolean isSelected()
```

**Example Usage:**
```java
// In SetupImController
List<NavigationMenuItem> items = new ArrayList<>();

// Fixed items (imConfig = null)
items.add(new NavigationMenuItem(null, 0, false));  // Initial
items.add(new NavigationMenuItem(null, 1, false));  // Related

// IM items
for (int i = 0; i < imConfigList.size(); i++) {
    Im imConfig = imConfigList.get(i);
    items.add(new NavigationMenuItem(imConfig, i + 2, false));
}
```

**Position Convention:**
- Position 0: SetupImFragment (Initial)
- Position 1: ManageRelatedFragment (Related)
- Position 2+: ManageImFragment for specific IM tables

**Alternative Definition:**

There's also a nested class in `NavigationManager` with a slightly different structure:
```java
public static class NavigationMenuItem {
    private final String code;
    private final String title;
    private final int iconResId;
}
```

This nested class is used internally by `NavigationManager` for rendering menu items.

---

### Utility DTOs

#### 8. ChineseSymbol

**Location**: `net.toload.main.hd.data.ChineseSymbol`

**Purpose**: Utility class for Chinese punctuation symbol conversion

**Static Data:**
```java
private static String chineseSymbols = "。「」，『』、：；！～？";
```

**Key Methods:**
```java
// Convert English punctuation to Chinese
static char getSymbol(char symbol)
// Example: getSymbol('.') → '。'
// Example: getSymbol(',') → '，'

// Get complete list of Chinese symbols as Mapping objects
static List<Mapping> getChineseSymoblList()
```

**Conversion Mapping:**

| English | Chinese | Description |
|---------|---------|-------------|
| `.` | `。` | Period |
| `"` | `「」` | Quotation marks |
| `,` | `，` | Comma |
| `'` | `『』` | Single quotes |
| `/` | `、` | Enumeration comma |
| `:` | `：` | Colon |
| `;` | `；` | Semicolon |
| `!` | `！` | Exclamation |
| `~` | `～` | Tilde |
| `?` | `？` | Question mark |

**Example Usage:**
```java
// Convert user input to Chinese punctuation
char englishPunct = '.';
char chinesePunct = ChineseSymbol.getSymbol(englishPunct);
// Result: '。'

// Display Chinese punctuation candidates
List<Mapping> symbols = ChineseSymbol.getChineseSymoblList();
// Each Mapping has recordType = RECORD_CHINESE_PUNCTUATION_SYMBOL
```

---

## DTO Usage Patterns

### Pattern 1: Query Path (Code → Candidates)

```java
// Input: User types "ㄋㄧ"
String code = "ㄋㄧ";

// Query database via SearchServer
List<Mapping> candidates = searchServer.getMappingByCode(code, softKeyboard, false);

// Process candidates by record type
for (Mapping mapping : candidates) {
    if (mapping.isExactMatchToCodeRecord()) {
        // Exact match: "你", "尼", "呢" (sorted by score)
        candidateView.addCandidate(mapping);
    } else if (mapping.isRelatedPhraseRecord()) {
        // Related phrase: "你好"
        candidateView.addRelatedPhrase(mapping);
    }
}
```

### Pattern 2: Learning Path (Selection → Database)

```java
// User selects candidate
Mapping selected = candidates.get(selectedIndex);

// Update score (asynchronous)
searchServer.learnRelatedPhraseAndUpdateScore(selected);

// At session end, learn related phrases
List<Mapping> scorelist = getSessionScorelist();
searchServer.learnRelatedPhrase(scorelist);

// If qualified, learn LD phrases
searchServer.learnLDPhrase(LDPhraseListArray);
```

### Pattern 3: Configuration (IM Setup)

```java
// Load available IMs
List<Im> imConfigList = searchServer.getImList(null, LIME.IM_FULL_NAME);

// Load available keyboards
List<Keyboard> keyboards = searchServer.getKeyboardList();

// User selects IM and keyboard
Im selectedIm = imConfigList.get(userChoice);
Keyboard selectedKb = keyboards.get(userChoice);

// Save configuration
searchServer.setIMKeyboard(selectedIm.getCode(), selectedKb.getCode());
```

### Pattern 4: Navigation (Fragment Selection)

```java
// Build navigation menu
List<NavigationMenuItem> menuItems = new ArrayList<>();

// Add fixed items
menuItems.add(new NavigationMenuItem(null, 0, false));  // Setup
menuItems.add(new NavigationMenuItem(null, 1, false));  // Related

// Add IM items
List<Im> imConfigList = searchServer.getImList(null, LIME.IM_FULL_NAME);
for (int i = 0; i < imConfigList.size(); i++) {
    menuItems.add(new NavigationMenuItem(imConfigList.get(i), i + 2, false));
}

// User selects item
NavigationMenuItem selected = menuItems.get(position);
if (selected.getIm() != null) {
    // Navigate to ManageImFragment for specific IM
    String tableName = selected.getIm().getCode();
    navigateToManageIm(tableName);
}
```

### Pattern 5: Record Management (UI)

```java
// In ManageImFragment - search records
List<Record> records = searchServer.getRecordsByCode(searchCode);

// Display records in RecyclerView
for (Record record : records) {
    String display = String.format("%s → %s (score: %d)",
        record.getCode(),
        record.getWord(),
        record.getScore());
    adapter.addItem(display);
}

// User edits record
record.setWord(newWord);
record.setScore(newScore);
searchServer.updateRecord(record);
```

### Pattern 6: Related Phrase Management

```java
// In ManageRelatedFragment - load related phrases
List<Related> phrases = searchServer.getRelatedPhrase(parentWord);

// Display parent → child relationships
for (Related phrase : phrases) {
    String display = String.format("%s → %s (score: %d)",
        phrase.getPword(),
        phrase.getCword(),
        phrase.getUserscore());
    adapter.addItem(display);
}

// User deletes related phrase
searchServer.deleteRelatedPhrase(phrase.getPword(), phrase.getCword());
```

---

## DTO Design Principles

### 1. Separation of Concerns

DTOs contain **no business logic** - they are pure data containers:
- ✅ Getters and setters
- ✅ Record type checkers (simple boolean methods)
- ✅ Copy constructors
- ❌ Database operations (belong in LimeDB)
- ❌ Search logic (belongs in SearchServer)
- ❌ UI rendering (belongs in Views)

### 2. Inheritance for Semantic Clarity

`Record` and `Related` extend `Mapping` to provide:
- **Semantic distinction**: Different terminology in different UI contexts
- **Code reusability**: Same underlying data structure
- **Type safety**: Method signatures can specify `Record` vs `Related` vs `Mapping`

### 3. Composition Over Inheritance

`NavigationMenuItem` wraps `Im` rather than extending it:
- Adds navigation-specific metadata (position, selection state)
- Maintains clear separation between domain model (Im) and UI model (NavigationMenuItem)
- Allows `imConfig` to be null for fixed menu items

### 4. Lightweight Variants

`ImKeyboardConfig` demonstrates the DTO pattern for specific use cases:
- Contains only essential fields needed for keyboard assignment
- Avoids transferring unnecessary data
- Used in contexts where full `Im` object is overkill

### 5. Static Factory Methods

`Im.get(Cursor cursor)` pattern:
- Centralizes cursor parsing logic
- Provides safe extraction with null checks
- Reduces boilerplate in database layer

---

## DTO Validation and Constraints

### Mapping Constraints

- `code` and `word` should not be null for most record types
- `score` ≥ 0 (negative scores not allowed)
- `recordType` must be one of the defined constants (1-12)

### Im Constraints

- `code` must be unique (database primary key)
- `selkey` typically "123456789" or custom
- `disable` controls whether IM appears in selection UI

### Keyboard Constraints

- `code` must be unique
- At least one keyboard variant (`imkb`, `engkb`, etc.) should be defined
- `type` determines which keyboard variant is used

### Related Phrase Constraints

- `pword` (parent) and `cword` (child) must not be null
- Score accumulates with repeated phrase usage
- Bi-gram model: only consecutive word pairs are learned

### LD Phrase Constraints

- Phrase length: 2-4 characters only (enforced in learning logic)
- Cannot contain English suggestions (code.equals(word))
- Requires related phrase score > 20 to trigger creation
- Quick Phrase code built from first character of each unit

---

## Summary

### DTO Layer Benefits

1. **Clear data contracts**: DTOs define the shape of data transferred between layers
2. **Type safety**: Compiler catches mistakes in data access
3. **Semantic clarity**: Different DTO types (Mapping/Record/Related) clarify intent
4. **No database code**: DTOs remain pure - database operations in LimeDB
5. **Reusability**: Same DTOs used across IME, UI, and database layers
6. **Testability**: DTOs are simple POJOs, easy to construct in tests

### DTO Best Practices

✅ **DO:**
- Use `Mapping` for IME candidates and general database records
- Use `Record` in Setup/Manage IM UI contexts
- Use `Related` in Related Phrase management
- Use `Im` and `Keyboard` for configuration
- Use `NavigationMenuItem` for navigation state

❌ **DON'T:**
- Add database operations to DTOs
- Add business logic to DTOs (belongs in SearchServer/LimeDB)
- Create unnecessary DTO variants (prefer reuse)

---

## IME Logic Flow

### Query Path (User Input → Candidate Display)

The query path represents the flow from user input to candidate word display. This is the hot path for IME performance.

#### 1. Soft Keyboard Input Flow

```
User presses key on soft keyboard
  └─> LIMEService.onKey(primaryCode, keyCodes)
      ├─> Update composing text (append key code)
      ├─> Call updateCandidates()
      │   └─> SearchServer.getMappingByCode(code, softKeyboard, getAllRecords)
      │       ├─> [Check mapping cache]
      │       │   └─> Key: tablename + keyboardType + code
      │       ├─> [If cache miss]
      │       │   └─> LimeDB.getMappingByCode(code, tablename, ...)
      │       │       ├─> Apply remapping rules
      │       │       ├─> Apply dual-code expansion
      │       │       ├─> Apply blacklist filtering
      │       │       └─> Execute SQL query: SELECT * FROM {table} WHERE code=? ORDER BY score DESC
      │       ├─> [Cache result]
      │       └─> Return List<Mapping>
      └─> Display candidates in CandidateView
          ├─> Sort by score (learned entries first)
          ├─> Show related phrases (if available)
          └─> Enable candidate selection
```

**Key Characteristics:**
- **Cached**: Second query with same code uses cache (faster)
- **Sorted by score**: Learned words (score > 0) appear first
- **Blacklist filtering**: Removes unwanted candidates based on configuration
- **Dual-code expansion**: Expands compatible input codes (e.g., bopomofo variants)

#### 2. Hardware Keyboard Input Flow

```
User presses hardware key
  └─> LIMEService.onKeyDown(keyCode, event)
      ├─> translateKeyDown(keyCode, event)
      │   └─> Convert hardware key to IM code using keyboard layout
      ├─> Update composing text
      ├─> Call updateCandidates()
      │   └─> [Same as soft keyboard flow above]
      └─> Display candidates

User releases hardware key
  └─> LIMEService.onKeyUp(keyCode, event)
      └─> Finalize key input (handle shift/meta states)
```

**Special Key Handling:**
- **Enter**: Commit composing text
- **Backspace**: Delete last composing character
- **Space**: Auto-select first candidate or commit
- **Arrow keys**: Navigate candidate list
- **Selection keys (1-9, 0)**: Directly pick candidate by number

#### 3. Query Performance Optimization

**Cache Strategy:**
- First query (cold): ~10-50ms (database query)
- Subsequent queries (warm): ~1-5ms (cache hit)
- Cache key: `tablename + keyboardType + code`
- Cache invalidated on: table change, database update

**Database Optimization:**
- Index on `code` column for fast lookup
- Sort by `score DESC` to prioritize learned words
- Parameterized queries to prevent SQL injection

---

### Learning Path (Candidate Selection → Database Update)

The learning path represents how user selections are learned and stored in the database. This improves future candidate suggestions.

#### 1. Score Update Path

```
User selects candidate from list
  └─> LIMEService.pickCandidateManually(index)
      ├─> Get selected Mapping from candidate list
      ├─> Commit text to InputConnection
      │   └─> ic.commitText(mapping.word, 1)
      ├─> Add to learning scorelist
      │   └─> scorelist.add(mapping)
      └─> Update score (asynchronous)
          └─> SearchServer.learnRelatedPhraseAndUpdateScore(mapping)
              ├─> Add mapping to internal scorelist
              └─> Spawn background thread: updateScoreCache()
                  └─> For each mapping in cache queue:
                      ├─> Get current score from database
                      ├─> Increment score by 1
                      └─> Update database: UPDATE {table} SET score=? WHERE id=?
```

**Key Characteristics:**
- **Asynchronous**: Score updates happen in background thread (non-blocking)
- **Incremental**: Each selection increments score by 1
- **Persistent**: Scores stored in database, survive IME restart
- **Cache-aware**: Updated scores affect future queries (higher ranking)

#### 2. Related Phrase Learning Path

```
User completes input session (types multiple words)
  └─> LIMEService.postFinishInput()
      ├─> Check preference: LIMEPref.getLearnRelatedWord()
      ├─> [If enabled]
      │   └─> SearchServer.learnRelatedPhrase(scorelist)
      │       └─> For each consecutive pair (word1, word2) in scorelist:
      │           ├─> Skip if word1 or word2 is null/empty
      │           ├─> Skip if word2 is composing code or English suggestion
      │           ├─> Get unit1 mapping by reverse lookup (if needed)
      │           ├─> Get unit2 mapping by reverse lookup (if needed)
      │           ├─> Check if related phrase already exists
      │           ├─> [If exists]: Increment score
      │           │   └─> LimeDB.updateRelatedPhraseScore(unit1.word, unit2.word, score+1)
      │           └─> [If new]: Create related phrase record
      │               └─> LimeDB.insertRelatedPhrase(unit1.word, unit2.word, score=1)
      └─> Clear scorelist for next session
```

**Example:**
```
User types: "你好嗎" (3 words)
Selections: "你" → "好" → "嗎"

Related phrases learned:
- "你" → "好" (score=1)
- "好" → "嗎" (score=1)

Next time user types "你", system suggests "好" as related phrase
```

**Key Characteristics:**
- **Session-based**: Learns from consecutive word selections in same session
- **Bi-gram model**: Learns pairs of consecutive words
- **Preference-controlled**: Can be disabled via `LIMEPref.setLearnRelatedWord(false)`
- **Score accumulation**: Repeated phrases get higher scores

#### 3. LD Phrase (Learning Dictionary) Path

```
User completes input session with high-score related phrases
  └─> LIMEService.postFinishInput()
      ├─> Check preference: LIMEPref.getLearnPhrase()
      ├─> [If enabled and related phrase score > 20]
      │   └─> For each high-score related phrase:
      │       └─> SearchServer.addLDPhrase(unit, isLastUnit)
      │           └─> Add to LDPhraseListArray buffer
      └─> [At session end]
          └─> SearchServer.learnLDPhrase(LDPhraseListArray)
              ├─> Validate phrase (2-4 characters only)
              ├─> Skip if any unit is English (code.equals(word))
              ├─> Build phrase from consecutive words
              ├─> For each unit in phrase:
              │   ├─> Get base code (from unit or reverse lookup)
              │   ├─> Extract first character of code for QP code
              │   └─> Concatenate codes: baseCode += unit.code
              ├─> Build Quick Phrase (QP) code from first chars
              ├─> Create LD phrase record
              │   └─> LimeDB.insertLDPhrase(
              │         code=combinedCode,
              │         word=combinedWord,
              │         QPcode=firstCharsCode,
              │         score=1
              │       )
              └─> Clear LDPhraseListArray
```

**Example:**
```
User frequently types: "電" (ㄉㄧㄢ) → "腦" (ㄋㄠ)
After 20+ selections, related phrase score > 20

LD phrase created:
- word: "電腦"
- code: "ㄉㄧㄢㄋㄠ" (full combined code)
- QPcode: "ㄉㄋ" (Quick Phrase: first char of each)
- score: 1

User can now type:
- "ㄉㄧㄢㄋㄠ" → suggests "電腦" (full code)
- "ㄉㄋ" → suggests "電腦" (Quick Phrase code)
```

**Key Characteristics:**
- **Threshold-based**: Only activates when related phrase score > 20
- **Phrase length limit**: 2-4 characters (enforced)
- **Quick Phrase (QP) code**: Shortcut using first character of each word
- **Two access methods**: Full code or QP code
- **Preference-controlled**: Can be disabled via `LIMEPref.setLearnPhrase(false)`
- **Requires related learning**: LD phrases built from related phrase data

#### 4. Complete Learning Flow Integration

```
┌─────────────────────────────────────────────────────────────┐
│              User Input Session (Complete Flow)              │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │  User types:    │
                    │  "你好嗎"        │
                    │  (3 selections) │
                    └─────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
   Selection 1          Selection 2          Selection 3
   "你" (ㄋㄧ)          "好" (ㄏㄠ)          "嗎" (ㄇㄚ)
        │                     │                     │
        └─────────────────────┴─────────────────────┘
                              │
                              ▼
        ┌────────────────────────────────────────────┐
        │  Immediate: Score Update (Background)      │
        │  learnRelatedPhraseAndUpdateScore()        │
        │  - "你" score: 100 → 101                   │
        │  - "好" score: 85 → 86                     │
        │  - "嗎" score: 50 → 51                     │
        └────────────────────────────────────────────┘
                              │
                              ▼
        ┌────────────────────────────────────────────┐
        │  Session End: Related Phrase Learning      │
        │  learnRelatedPhrase(scorelist)             │
        │  [If LearnRelatedWord = true]              │
        │  - Create: "你" → "好" (score=1)           │
        │  - Create: "好" → "嗎" (score=1)           │
        └────────────────────────────────────────────┘
                              │
                              ▼
        ┌────────────────────────────────────────────┐
        │  Conditional: LD Phrase Learning           │
        │  learnLDPhrase(LDPhraseListArray)          │
        │  [If LearnPhrase = true AND score > 20]    │
        │  - Create: "你好" (QPcode="ㄋㄏ")          │
        │  - Create: "好嗎" (QPcode="ㄏㄇ")          │
        └────────────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │  Database State │
                    │  Updated        │
                    └─────────────────┘
```

**Learning Preferences:**

| Preference | Effect |
|-----------|--------|
| `LearnRelatedWord = true` | Creates related phrase records (bi-grams) |
| `LearnRelatedWord = false` | Only score updates, no related phrases |
| `LearnPhrase = true` | Creates LD phrases from high-score related phrases (requires LearnRelatedWord) |
| `LearnPhrase = false` | No LD phrases created |
| Both disabled | Only score updates via `learnRelatedPhraseAndUpdateScore()` |
| Both enabled | Full learning chain: score → related → LD |

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

#### Pattern 1: User Input → Search Results (Query Path)

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

#### Pattern 2: Candidate Selection → Learning (Learning Path)

```
User selects candidate
  └─> LIMEService.pickCandidateManually()
      ├─> Commit text to InputConnection
      ├─> SearchServer.learnRelatedPhraseAndUpdateScore()
      │   └─> [Background thread] Update score in database
      └─> [Session end] SearchServer.learnRelatedPhrase()
          ├─> Create related phrase records
          └─> [If qualified] SearchServer.learnLDPhrase()
              └─> Create LD phrase records with QP codes
```

#### Pattern 3: UI Configuration → Database Update

```
User changes IM keyboard in UI
  └─> ManageImKeyboardDialog.onItemClick()
      └─> SearchServer.setIMKeyboard()
          └─> LimeDB.setIMKeyboard()
              └─> SQLiteDatabase.update()
```

#### Pattern 4: File Import → Database Update

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

#### Pattern 5: Database Export → File

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

#### Pattern 6: User Records Backup/Restore

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

#### Pattern 7: Database Backup/Restore

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

#### Pattern 8: Chinese Character Conversion

```
User requests Traditional/Simplified conversion
  └─> LIMEService or UI Component
      └─> SearchServer.hanConvert()
          └─> LimeHanConverter.convert() [via LimeDB or directly]
              └─> SQLiteDatabase (hanconvertv2.db)
```

**Note**: `SearchServer` can access `LimeHanConverter` directly or through `LimeDB`. The converter manages its own separate database.

#### Pattern 9: Emoji Conversion

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
| **LIMEService** | IME functionality | Handle input, display candidates, learning flow | SearchServer, LIMEPreferenceManager |
| **SearchServer** | Database interface | Search, query, configuration, conversions, learning | LimeDB, LIMEPreferenceManager |
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
List<Im> imConfigList = searchServer.getIm(null, LIME.IM_TYPE_NAME);

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
List<Im> imConfigList = datasource.getIm(null, LIME.IM_TYPE_NAME);  // WRONG

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

**Learning State:**
```java
private ArrayList<Mapping> scorelist;           // Session learning scorelist
private ArrayList<Mapping> LDPhraseListArray;   // LD phrase buffer
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

// Learning at session end
LIMEService.postFinishInput()
  ├─> searchSrv.learnRelatedPhrase(scorelist)
  └─> searchSrv.learnLDPhrase(LDPhraseListArray)

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
List<Im> imConfigList = searchServer.getIm(null, LIME.IM_TYPE_NAME);

// ❌ Incorrect
LimeDB datasource = new LimeDB(context);
List<Im> imConfigList = datasource.getIm(null, LIME.IM_TYPE_NAME);
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
7. **Learning flow** enhances user experience through score updates, related phrases, and LD phrases

### Benefits

- ✅ Single interface for database operations
- ✅ Centralized caching and performance optimization
- ✅ Consistent access patterns
- ✅ Easier to add validation/transformation
- ✅ Better maintainability
- ✅ Clear component responsibilities
- ✅ Adaptive learning improves suggestion accuracy over time
- ✅ Multi-level learning (score, related, LD) provides comprehensive personalization

This architecture provides a clean, maintainable, and scalable foundation for the LimeIME application, with intelligent learning capabilities that adapt to user behavior.
