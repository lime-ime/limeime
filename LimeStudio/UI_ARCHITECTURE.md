# LimeIME UI Architecture - MVC Pattern

## Overview

This document describes the Model-View-Controller (MVC) architecture of the LimeIME user interface components. The architecture improves code organization, testability, and maintainability through clear separation of concerns.

**Architecture Status**: ✅ **IMPLEMENTED**  
**Last Updated**: December 26, 2025  
**Version**: 1.1 - Major refactoring: Moved handlers/managers to ui/ level, enhanced threading safety, moved NavigationDrawerCallbacks to NavigationManager

## Architecture Goals

The UI architecture is designed to achieve the following objectives:

1. **Clean Separation of Concerns** - Distinct layers for View, Controller/Handler, and Model
2. **Testability** - Controllers and managers can be unit tested without Android context
3. **Maintainability** - Clear responsibilities make code easier to understand and modify
4. **Reusability** - Shared controllers and managers across multiple views
5. **Code Organization** - Consistent patterns and structure throughout the codebase
6. **Data Integrity** - Centralized data access through controllers and managers

## Architecture Layers

### Layer 1: View Layer

The View layer consists of all user-facing components that display data and receive user input.

**Components:**
- **Activities**: `MainActivity` - Main coordinator and dependency container
- **Fragments**: 
  - `NavigationDrawerFragment` - Navigation menu
  - `SetupImFragment` - IM setup and configuration
  - `ManageImFragment` - IM record management
  - `ManageRelatedFragment` - Related phrase management
- **Dialogs**:
  - `ImportDialog` - Import IM table
  - `ShareDialog` - Share IM and related table
  - `ManageImAddDialog` - Add/edit IM records
  - `SetupImLoadDialog` - Load IM files
  - `ManageImKeyboardDialog` - Keyboard management
  
**Preference Screen:**
- **Activity**: `LIMEPreference` (hosts the preferences UI)
- **Fragment**: `LIMEPreference.PrefsFragment` (extends `PreferenceFragmentCompat`)
- **Resources**: Loads from `res/xml/preference.xml` (and `res/xml-v17/preference.xml`)

**Preference Responsibilities:**
- Inflate preferences from XML and display settings
- Register/unregister `OnSharedPreferenceChangeListener`
- React to specific keys (e.g., `phonetic_keyboard_type`) and update runtime behavior via `SearchServer`
- Persist and restore settings through shared preferences

**Responsibilities:**
- Display data to users
- Receive and process user input
- Delegate business logic to controllers/managers
- Implement view interfaces for callback notifications
- Handle UI-specific operations (animations, transitions)

**Key Constraint:** Views do NOT directly access Model layer (SearchServer, DBServer) for operations managed by controllers (backup, restore, share, progress dialogs)

### Layer 2: Controller/Handler/Manager Layer

This layer mediates between View and Model, handling business logic and specialized operations.

#### Controllers

**BaseController (Abstract Base Class)**
- **Location**: `net.toload.main.hd.ui.controller.BaseController`
- **Thread-Safe UI Operation Methods** (NEW):
  - `handleError(view, message, exception)` - Posts error handling to main thread
  - `showProgress(view, message)` - Posts progress overlay display to main thread
  - `hideProgress(view)` - Posts progress overlay hide to main thread
  - `updateProgress(view, percentage, status)` - Posts progress updates to main thread
  - `showToast(view, message, length)` - Posts toast display to main thread
  
- **Key Feature**: All UI operations use `mainHandler.post()` to ensure main thread execution
  - Safe to call from any thread (background executors, callbacks, etc.)
  - Prevents "Can't toast on a thread that has not called Looper.prepare()" crashes
  
- **Foundation for all controllers**
- **Error handling patterns**
- **View interface management**
- **Activity context management**

**SetupImController**
- **Location**: `net.toload.main.hd.ui.controller.SetupImController`
- **New Feature**: Implements `ImportDialog.OnImportIMSelectedListener`
  - Added method: `onImportDialogImSelected(String tableName, boolean restoreUserRecords)` - handles import dialog table selection
  - Added method: `setFileToImport(File file)` - stores file for import
  - Moved from MainActivity to controller for better separation of concerns
  
- **Navigation Operations**:
  - Fragment switching (ManageIm, Setup, etc.)
  - Navigation drawer management
  - ActionBar title updates
  - Button state calculation and menu loading
  
- **Import Operations**:
  - External file imports: text files (.lime/.cin) via `importTxtTable(File, tableName, boolean restoreUserRecords)`
  - External zipped database imports: `.limedb` files via `importZippedDb(File, tableName, boolean restoreUserRecords)`
  - Local file imports from SetupImLoadDialog
  - Remote download and import via `downloadAndImportZippedDb(tableName, url, boolean restoreUserRecords)` with progress tracking
  
- **Backup/Restore Operations**:
  - Complete backup workflow: user records backup + database backup via `performBackup(Uri)`
  - Complete restore workflow: database restore + menu refresh via `performRestore(Uri)`
  - Recovery operations
  - Conditional backup-before-import for external imports when user opts to restore
  
- **Export Operations**:
  - Export IM tables as zipped database via `exportZippedDb(String, Uri)`
  - Export related table as zipped database via `exportZippedDbRelated(Uri)`
  
- **Dependencies**: `DBServer`, `SearchServer`
- **Threading**: Uses `ExecutorService` for download operations; all UI updates via BaseController thread-safe methods

**ManageImController**
- **Location**: `net.toload.main.hd.ui.controller.ManageImController`
- **Threading Fix** (NEW):
  - Background executor thread now posts UI updates to main thread via `mainHandler.post()`
  - Prevents UI thread violations when displaying records from background search operations
  
- **Asynchronous Record Management**:
  - Load records asynchronously via `loadRecordsAsync(String table, String query, boolean searchByCode, int offset, int limit)`
  - Create, read, update, delete IM records synchronously
  - Search and filter records with progress callbacks
  - Count operations with table validation
  
- **Related Phrase Management**:
  - Load related phrases via `loadRelatedPhrases(String pWord, int offset, int limit)`
  - Create, read, update, delete related phrases
  - Search within phrases
  - Phrase associations
  
- **Keyboard Operations**:
  - Keyboard list retrieval via `getKeyboardList()`
  - Keyboard selection management via `setIMKeyboard(String table, Keyboard keyboard)`
  - Layout switching
  
- **Data Refresh & Progress**:
  - Record list refreshes with progress callbacks
  - Related phrase list refreshes with progress callbacks
  - Progress notifications through view interfaces
  
- **Dependencies**: `SearchServer`
- **Threading**: Uses reusable `ExecutorService` field for asynchronous record loading; all UI updates via `mainHandler.post()`
- **View Interfaces**: Implements callbacks for progress tracking and UI updates

#### Handlers

**IntentHandler** (Moved to `ui/` level)
- **Location**: `net.toload.main.hd.ui.IntentHandler`
- **External File Imports**:
  - Validates file types (txt, db, zip)
  - Determines target table
  - Imports file content
  - Triggers view refresh after import
  
- **Intent Processing**:
  - ACTION_SEND intents (file sharing)
  - ACTION_VIEW intents (file opening)
  - Data validation before import
  
- **Dependencies**: `SearchServer`, `DBServer`
- **Threading**: Safe for main thread calls; delegates heavy I/O to DBServer

#### Managers

**ProgressManager** (Moved to `ui/` level)
- **Location**: `net.toload.main.hd.ui.ProgressManager`
- **Dialog Lifecycle**:
  - Show progress overlay/dialog with message
  - Update progress message
  - Update progress percentage
  - Dismiss dialog
  
- **Operation Feedback**:
  - Long-running operation notifications
  - User feedback during processing
  - Cancellation handling
  
- **Dependencies**: `Activity`, Android views/dialogs
- **Threading**: All operations wrapped in `runOnUiThread()`

**ShareManager** (Moved to `ui/` level)
- **Location**: `net.toload.main.hd.ui.ShareManager`
- **Export Operations**:
  - Export IM records as text file
  - Export IM records as database file
  - Export related phrases as text file
  - Export related phrases as database file
  
- **Share Intent Operations**:
  - Create share intent
  - Launch share dialog
  - Handle share targets
  
- **Operation Feedback**:
  - Uses ProgressManager for long operations
  - Success/failure notifications
  - File URI generation
  
- **Dependencies**: `MainActivity`, `SetupImController`, `ProgressManager`, `DBServer`, `SearchServer`
- **Threading**: Safe for main thread; heavy operations delegated to controllers

**NavigationManager** (Moved to `ui/` level, now implements NavigationDrawerCallbacks)
- **Location**: `net.toload.main.hd.ui.NavigationManager`
- **New Feature**: Implements `NavigationDrawerFragment.NavigationDrawerCallbacks`
  - Added method: `onNavigationDrawerItemSelected(int position)` - handles drawer item selection
  - Centralizes all navigation logic away from MainActivity
  
- **Fragment Navigation**:
  - Create appropriate fragments based on selection
  - Manage fragment transactions
  - Handle back navigation
  
- **Navigation State**:
  - Track current fragment
  - Manage navigation history
  - ActionBar updates
  
- **Dependencies**: `Activity`, `SetupImController`, `MainActivity`
- **Threading**: Safe for main thread; all fragment operations on main thread

#### View Interfaces

**ViewUpdateListener (Base Interface)**
```java
public interface ViewUpdateListener {
    void onError(String message);
    void onProgress(int percentage, String status);
}
```

**MainActivityView**
```java
public interface MainActivityView extends ViewUpdateListener {
    void showProgressDialog(String message);
    void hideProgressDialog();
    void navigateToFragment(int position);
}
```

**SetupImView**
```java
public interface SetupImView extends ViewUpdateListener {
    void updateButtonStates(Map<String, Boolean> states);
    void refreshImList();
}
```

**ManageImView**
```java
public interface ManageImView extends ViewUpdateListener {
    void displayRecords(List<Record> records);
    void updateRecordCount(int count);
    void refreshRecordList();
}
```

**ManageRelatedView**
```java
public interface ManageRelatedView extends ViewUpdateListener {
    void displayRelatedPhrases(List<Related> phrases);
    void refreshPhraseList();
}
```

**NavigationDrawerView**
```java
public interface NavigationDrawerView extends ViewUpdateListener {
    void updateMenuItems(List<NavigationMenuItem> items);
    void setSelectedItem(int position);
}
```

### Layer 3: Model Layer

The Model layer provides data access and business operations without UI concerns.

**Components:**
- **SearchServer** - Database query operations
  - Search and filter records
  - Record count operations
  - Record retrieval by ID, type, or range
  - Keyboard operations
  - Related phrase queries
  
- **DBServer** - File-level database operations
  - Database import from files
  - Database export to files
  - Database backup and restore
  - Table-level operations
  - Backup of user records
  
- **LimeDB** - SQL abstraction layer
  - SQL query execution
  - Table schema management
  - Record serialization/deserialization
  - Transaction management
  
- **LIMEPreferenceManager** - Application preferences
  - User settings persistence
  - Preference queries
  - Default value management

**Characteristics:**
- No Android framework dependencies (except Context for file operations)
- No direct view component references
- Exception handling at this layer (not re-thrown to views)
- Null-safe return values (empty collections instead of null)

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    VIEW LAYER (Refactored)                      │
│                                                                  │
│                    ┌─────────────────────┐                      │
│                    │   MainActivity      │                      │
│                    │   (Coordinator)     │                      │
│                    │                     │                      │
│                    │  Provides Getters:  │                      │
│                    │  • getSetupImCtrl() │                      │
│                    │  • getManageImCtrl()│                      │
│                    │  • getShareManager()│                      │
│                    │  • getProgressMgr() │                      │
│                    └──────────┬──────────┘                      │
│                               │                                  │
│         ┌─────────────────────┼─────────────────────┐           │
│         │                     │                     │           │
│  ┌──────▼──────┐      ┌──────▼──────┐      ┌──────▼──────┐   │
│  │ Fragments   │      │  Dialogs     │      │  Handlers    │   │
│  │ - SetupIm   │      │  - Import    │      │  - Intent    │   │
│  │ - ManageIm  │      │  - Share     │      │  - Progress  │   │
│  │ - ManageRel │      │  - Add/Edit  │      │  - Navigate  │   │
│  │ - Navigation│      │              │      │              │   │
│  └─────────────┘      └──────────────┘      └──────────────┘   │
│         │                     │                     │           │
│         └─────────────────────┼─────────────────────┘           │
│                               │                                  │
└───────────────────────────────┼──────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│              CONTROLLER/HANDLER/MANAGER LAYER ✅                │
│                                                                  │
│  ┌────────────────────┐  ┌────────────────────┐               │
│  │ SetupImController  │  │ ManageImController │               │
│  │ • Navigation       │  │ • Record CRUD      │               │
│  │ • Fragments        │  │ • Related CRUD     │               │
│  │ • Import/Export    │  │ • Search/Filter    │               │
│  │ • Backup/Restore   │  │ • Menu Refresh     │               │
│  │ • Button States    │  │ • Keyboard Ops     │               │
│  └────────┬───────────┘  └────────┬────────────┘               │
│           │                       │                             │
│  ┌────────┴───────────────────────┴──────────────┐             │
│  │         Specialized Handlers/Managers          │             │
│  │  ┌──────────────┐  ┌───────────────────────┐ │             │
│  │  │IntentHandler │  │ ProgressDialogManager │ │             │
│  │  │ShareManager  │  │ NavigationManager     │ │             │
│  │  └──────────────┘  └───────────────────────┘ │             │
│  └────────────────────────────────────────────────┘             │
│                            │                                     │
└────────────────────────────┼─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    MODEL LAYER (Unchanged)                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │ SearchServer │  │   DBServer    │  │  LIMEPrefMgr  │        │
│  │              │  │               │  │               │        │
│  │ - DB Ops     │  │ - File Ops    │  │ - Preferences │        │
│  │ - Search     │  │ - Import/Exp  │  │               │        │
│  │ - CRUD       │  │ - Backup      │  │               │        │
│  └──────┬───────┘  └──────┬───────┘  └───────────────┘        │
│         │                  │                                     │
│         └──────────┬───────┘                                     │
│                    │                                             │
│                    ▼                                             │
│              ┌──────────┐                                       │
│              │  LimeDB  │                                       │
│              │          │                                       │
│              │ - SQL    │                                       │
│              │ - Schema │                                       │
│              └──────────┘                                       │
└─────────────────────────────────────────────────────────────────┘

✅ Data Flow:
  View → Controller/Handler/Manager → Model → Database
  View ← Controller/Handler/Manager ← Model ← Database
```

## Design Patterns

### Delegation Pattern

Views delegate operations to controllers/managers through clearly defined interfaces:

```java
// Fragment delegates to controller via MainActivity getter
SetupImController controller = mainActivity.getSetupImController();
controller.performBackup(uri);

// Dialog delegates to manager via MainActivity getter
ShareManager shareManager = mainActivity.getShareManager();
shareManager.exportAndShareImTable(selectedTable);

// Manager delegates to ProgressDialogManager
ProgressDialogManager progressMgr = mainActivity.getProgressDialogManager();
progressMgr.show("Exporting...");
```

### Singleton Pattern

Controllers and managers are created once at the Activity level and reused:

```java
// MainActivity onCreate()
private SetupImController setupImController;
private ManageImController manageImController;

public void onCreate(Bundle savedInstanceState) {
    // ... setup ...
    manageImController = new ManageImController(searchServer);
    setupImController = new SetupImController(dbServer, searchServer);
}

// Getter provides access to all components
public SetupImController getSetupImController() {
    return setupImController;
}
```

### Factory Pattern

Controllers and managers create fragments and dialogs as needed:

```java
// NavigationManager creates fragments based on type
public Fragment createFragment(int position) {
    switch (position) {
        case NAV_SETUP: return new SetupImFragment();
        case NAV_MANAGE: return new ManageImFragment();
        // ...
    }
}

// ShareManager creates export files
private File createExportFile(String type) {
    String filename = type + "_export_" + System.currentTimeMillis();
    return new File(cacheDir, filename);
}
```

### Callback Pattern

Views implement interfaces for controller callbacks:

```java
// Fragment implements SetupImView
public class SetupImFragment extends Fragment implements SetupImView {
    @Override
    public void updateButtonStates(Map<String, Boolean> states) {
        // Update UI with new button states
    }
    
    @Override
    public void onError(String message) {
        // Display error to user
    }
}

// Controller notifies view of changes
setupView.updateButtonStates(states);
setupView.refreshImList();
setupView.onError("Operation failed");
```

## Data Flow

### Import Operation Flow

There are two distinct import flows depending on how the user initiates the operation:

#### Case 1: External Import (ACTION_SEND / ACTION_VIEW)

User shares file from another app or opens file with LimeIME. Two scenarios based on file type:

##### Case 1a: Text File Import (.lime / .cin)

User receives or opens .lime or .cin text file:

```
External App (shares .lime/.cin file)
    ↓
IntentHandler.processIntent() (validates intent/file)
    ├─ Extract URI and file info
    ├─ Validate file type and scheme
    ├─ Convert InputStream to File
    └─ handleImportTxt()
    ↓
ImportDialog.newInstanceForFile() (shows dialog)
    ├─ Display available IM types
    ├─ Only empty IM tables are enabled (non-empty disabled)
    └─ User selects target IM table
    ↓
AlertDialog: "Restore learning data?" (same strings as 1b)
    ├─ Cancel
    ├─ Restore (restoreUserRecords = true)
    └─ Don't Restore (restoreUserRecords = false)
    ↓
SetupImController.onImportDialogImSelected(tableName, restoreUserRecords) (callback)
    ↓
SetupImFragment.importTxtTable(restoreUserRecords)
    ↓
SetupImController.importTxtTable(file, tableName, restoreUserRecords)
    ├─ SearchServer.isValidTableName() (validation)
    ├─ SearchServer.backupUserRecords() (if restore enabled)
    ├─ DBServer.importTxtTable() (file import)
    ├─ SearchServer.resetCache() (clear search cache after import)
    ├─ SearchServer.checkBackupTable()
    └─ SearchServer.restoreUserRecords()
    ↓
SetupImView.refreshButtonState() (UI update)
```

##### Case 1b: Zipped Database Import (.limedb)

User receives or opens .limedb zipped database file:

```
External App (shares .limedb file)
    ↓
IntentHandler.processIntent() (validates intent/file)
    ├─ Extract URI and file info
    ├─ Validate file type (application/zip) and extension
    ├─ Convert InputStream to File
    └─ handleLimedbImport()
    ↓
Extract table name from filename:
    ├─ If table is "related": Use importZippedDbRelated() path
    └─ If table is IM table: Use importZippedDb() path with validation
    ↓
For IM tables - Check target table:
    ├─ If table has data: Single AlertDialog (same strings as 1a)
    │   ├─ Cancel
    │   ├─ Restore (restoreUserRecords = true)
    │   └─ Don't Restore (restoreUserRecords = false)
    └─ If table empty: Proceed directly (restoreUserRecords = false)
    ↓
SetupImFragment.importZippedDb(restoreUserRecords)
    ↓
SetupImController.importZippedDb(restoreUserRecords)
    ├─ If IM table:
    │   ├─ SearchServer.isValidTableName() (validation for IM tables)
    │   ├─ SearchServer.backupUserRecords() (if restoreUserRecords)
    │   ├─ DBServer.importZippedDb() (database import)
    │   ├─ SearchServer.resetCache() (clear search cache after import)
    │   └─ SearchServer.restoreUserRecords() (if restoreUserRecords = true)
    └─ If related table:
        ├─ DBServer.importZippedDbRelated() (related table import)
        └─ SearchServer.resetCache() (clear search cache after import)
    ↓
SetupImView.refreshButtonState() (UI update)
```

#### Case 2: Internal Import (SetupImView Buttons)

User clicks IM buttons in SetupImFragment to initiate import. The flow starts with a dialog:

```
SetupImView (user clicks IM button - e.g., Array, CJ, Pinyin)
    ↓
SetupImFragment.onClick()
    ↓
SetupImLoadDialog.newInstance(tableName) (shows dialog)
    ├─ Display local file import button
    ├─ Display download buttons (with different options)
    └─ Restore learning checkbox
```

User then has two choices in SetupImLoadDialog:

##### Case 2a: Local Zipped Database (.limedb)

User clicks "Custom" button and selects local .limedb file:

```
SetupImLoadDialog (user clicks Custom/local file button)
    ↓
selectMappingFile() - Launch ACTION_OPEN_DOCUMENT
    ↓
User selects .limedb file
    ↓
SetupImLoadDialog.handleFileSelection()
    ↓
SetupImFragment.importDb()
    ↓
SetupImController.importZippedDb()
    ├─ MainActivityView.showProgressDialog()
    ├─ DBServer.importZippedDb() or importZippedDbRelated()
    ├─ SearchServer.resetCache()
    └─ MainActivityView.hideProgressDialog()
    ↓
SetupImController.refreshMenu()
NavigationDrawerView.updateMenuItems()
SetupImFragment.initialImButtons() (refresh button states)
```

##### Case 2b: Local Text Table (.cin/.lime)

User clicks "Custom" button and selects local .cin or .lime file:

```
SetupImLoadDialog (user clicks Custom/local file button)
    ↓
selectMappingFile() - Launch ACTION_OPEN_DOCUMENT
    ↓
User selects .cin/.lime file
    ↓
SetupImLoadDialog.handleFileSelection()
    ├─ Check restore learning checkbox
    └─ SetupImFragment.importTxtTable()
    ↓
SetupImController.importTxtTable()
    ├─ SearchServer.isValidTableName() (validation)
    ├─ MainActivityView.showProgressDialog()
    ├─ SearchServer.backupUserRecords() (if restore enabled)
    ├─ DBServer.importTxtTable() (file import)
    ├─ SearchServer.checkBackupTable()
    ├─ SearchServer.restoreUserRecords()
    └─ MainActivityView.hideProgressDialog()
    ↓
SetupImController.refreshMenu()
SetupImFragment.initialImButtons() (refresh button states)
```

##### Case 2c: Download from Remote (.limedb)

User clicks one of the download buttons (e.g., "15,945 from Phonetic Big5"):

```
SetupImLoadDialog (user clicks download button)
    ↓
SetupImLoadDialog.downloadAndImportZippedDb()
    ├─ Check network availability
    ├─ Check restore learning checkbox
    └─ SetupImFragment.downloadAndImportZippedDb()
    ↓
SetupImController.downloadAndImportZippedDb()
    ├─ MainActivityView.showProgressDialog()
    ├─ LIMEUtilities.downloadRemoteFile() (download from cloud URL)
    ├─ NavigationDrawerView.onProgress() (show download %)
    ├─ DBServer.importZippedDb() (import downloaded file)
    ├─ SearchServer.resetCache()
    ├─ SearchServer.checkBackupTable()
    ├─ SearchServer.restoreUserRecords() (if restore enabled)
    └─ MainActivityView.hideProgressDialog()
    ↓
SetupImController.refreshMenu()
NavigationDrawerView.updateMenuItems()
SetupImFragment.initialImButtons() (refresh button states)
```

### Export (Share To) Operation Flow

This is the opposite of import - exporting IM and related data for sharing with other apps.

Common start:

```
SetupImView (user clicks Share To button)
    ↓
ShareDialog pops up
    ├─ Display list of IMs (Array, CJ, Pinyin, etc.)
    ├─ Display Related table option
    └─ User selects IM or Related
    ↓
```

#### Case 1: Export IM Table

User selects an IM table in ShareDialog:

##### Case 1a: Export as Zipped Database (.limedb)

User clicks IM button (e.g., Array) → Format selection dialog → Chooses .limedb:

```
ShareDialog (user clicks IM button - e.g., Array)
    ↓
AlertDialog pops up (export format selection)
    ├─ "Export as Zipped Database (.limedb)" button
    └─ "Export as Text Table (.lime)" button
    ↓
User selects .limedb option
    ↓
ShareManager.exportAndShareImTable(tableName)
    ├─ ProgressDialogManager.show()
    ├─ DBServer.exportZippedDb() (create zipped database file)
    ├─ ProgressDialogManager.dismiss()
    └─ Create share intent
    ↓
System share dialog (user selects target app to share)
```

##### Case 1b: Export as Text Table (.lime)

User clicks IM button (e.g., Array) → Format selection dialog → Chooses .lime:

```
ShareDialog (user clicks IM button - e.g., Array)
    ↓
AlertDialog pops up (export format selection)
    ├─ "Export as Zipped Database (.limedb)" button
    └─ "Export as Text Table (.lime)" button
    ↓
User selects .lime option
    ↓
ShareManager.shareImAsText(tableName)
    ├─ ProgressDialogManager.show()
    ├─ SearchServer.getAllImKeyboardConfig() (get IM data)
    ├─ SearchServer.exportTxtTable() (export Im as text)
    ├─ ProgressDialogManager.dismiss()
    └─ Create share intent
    ↓
System share dialog (user selects target app to share)
```

#### Case 2: Export Related Table

User selects Related in ShareDialog (only .limedb option available):

```
ShareDialog (user clicks Related button)
    ↓
AlertDialog pops up (export format selection)
    └─ "Export as Zipped Database (.limedb)" button only
    ↓
User selects .limedb option (only choice)
    ↓
ShareManager.shareRelatedAsDatabase()
    ├─ ProgressDialogManager.show()
    ├─ DBServer.exportZippedDbRelated() (create zipped database file)
    ├─ ProgressDialogManager.dismiss()
    └─ Create share intent
    ↓
System share dialog (user selects target app to share)
```

### Backup Operation Flow

```
SetupImView (user clicks Backup button)
    ↓
SetupImFragment.backupLocalDrive()
    ├─ Launch ACTION_CREATE_DOCUMENT intent (file picker)
    └─ User selects save location
    ↓
ActivityResultLauncher callback (backupLauncher)
    ↓
SetupImFragment.performBackup(uri)
    ↓
SetupImController.performBackup() (complete workflow)
    ├─ SearchServer.backupUserRecords(CUSTOM) (backup user records)
    ├─ MainActivityView.showProgressDialog()
    └─ DBServer.backupDatabase(uri)
        ├─ Backup shared preferences
        ├─ Hold and close database connection
        ├─ LIMEUtilities.zip() (zip database files)
        ├─ Copy zip to user-selected URI
        ├─ Reopen database connection
        └─ Show notification
    ↓
MainActivityView.hideProgressDialog()
```

### Restore Operation Flow

```
SetupImView (user clicks Restore button)
    ↓
SetupImFragment.restoreLocalDrive()
    ├─ Launch ACTION_GET_CONTENT intent (file picker)
    └─ User selects backup file
    ↓
ActivityResultLauncher callback (restoreLauncher)
    ↓
SetupImFragment.performRestore(uri)
    ↓
SetupImController.performRestore() (complete workflow)
    ├─ MainActivityView.showProgressDialog()
    └─ DBServer.restoreDatabase(uri)
        ├─ Copy URI content to temp file
        ├─ Hold and close database connection
        ├─ LIMEUtilities.unzip() (extract to data directory)
        ├─ Restore shared preferences
        ├─ Reopen database connection
        ├─ SearchServer.resetCache()
        ├─ Check and update related table
        └─ Show notification
    ↓
MainActivityView.hideProgressDialog()
SetupImController.refreshMenu()
NavigationDrawerView.updateMenuItems() (menu refresh)
```

## Key Architectural Principles

### 1. Single Responsibility

Each component has a clearly defined purpose:
- **Controller**: Orchestrate operations, coordinate between view and model
- **Manager**: Handle specialized UI operations (progress, sharing, navigation)
- **Handler**: Process specific event types (intents, callbacks)
- **View**: Display data and receive user input
- **Model**: Provide data access without UI concerns

### 2. Dependency Injection

Controllers and managers receive dependencies through constructors:

```java
// Constructor explicitly shows dependencies
public SetupImController(DBServer dbServer, SearchServer searchServer) {
    this.dbServer = dbServer;
    this.searchServer = searchServer;
}
```

### 3. Interface Segregation

Views implement only the interfaces they need:

```java
// SetupImFragment implements only SetupImView
public class SetupImFragment extends Fragment implements SetupImView {
    // Receives callbacks only for button states and IM list
}

// ManageImFragment implements only ManageImView
public class ManageImFragment extends Fragment implements ManageImView {
    // Receives callbacks only for records and record count
}
```

### 4. Dependency Inversion

Views depend on abstractions (interfaces) not concrete classes:

```java
// View depends on interface, not concrete controller
SetupImView view = fragment;
controller.setSetupImView(view); // Pass interface reference

// Manager depends on interface, not concrete view
ProgressDialogManager progressMgr; // Abstract operation
```

### 5. Null Safety

Controllers and managers return safe defaults instead of null:

```java
// Return empty config list instead of null
public List<ImConfig> getImAllConfigList(String table) {
    List<ImConfig> configs = searchServer.getImConfigs(table);
    return configs != null ? configs : Collections.emptyList();
}
```

## Benefits

### Code Quality
- Clear separation of concerns improves maintainability
- Consistent patterns across components
- Easier to locate and understand code
- Reduced code duplication through reuse

### Testability
- Controllers can be unit tested without Android context
- Mock implementations of interfaces for testing
- No direct dependencies on Activities/Fragments
- Predictable behavior and error handling

### Maintainability
- Changes to business logic isolated to controllers
- UI updates isolated to view implementations
- Model layer remains stable and independent
- Clear interfaces define expected behavior

### Scalability
- Easy to add new features by adding controllers
- Reuse existing controllers and managers
- Extend without modifying existing code
- Support for multiple UI layers (if needed)

### User Experience
- Consistent error handling and feedback
- Progress dialogs for long operations
- Responsive UI through controller delegation
- Predictable navigation and state management

## Current Implementation Status

### ✅ Fully Implemented Components

**Controllers:**
- ✅ BaseController (abstract base)
- ✅ SetupImController (navigation, setup, backup/restore, button states)
- ✅ ManageImController (record CRUD, search, related phrases)

**Handlers/Managers:**
- ✅ IntentHandler (external file imports)
- ✅ ProgressDialogManager (progress dialogs)
- ✅ ShareManager (export and share operations)
- ✅ NavigationManager (fragment navigation)

**View Interfaces:**
- ✅ ViewUpdateListener (base)
- ✅ MainActivityView (main activity operations)
- ✅ SetupImView (setup fragment operations)
- ✅ ManageImView (manage fragment operations)
- ✅ ManageRelatedView (related phrases operations)
- ✅ NavigationDrawerView (navigation operations)

**Refactored Views:**
- ✅ MainActivity (delegates to controllers/managers)
- ✅ NavigationDrawerFragment (uses NavigationManager)
- ✅ SetupImFragment (uses SetupImController)
- ✅ ManageImFragment (uses ManageImController)
- ✅ ManageRelatedFragment (uses ManageImController)
- ✅ ImportDialog (uses ManageImController)
- ✅ ShareDialog (uses ShareManager)
- ✅ LIMEPreference + PrefsFragment (preference screen)

## Architecture Validation

The implemented architecture achieves all design goals:

1. ✅ **Separation of Concerns**: View, Controller/Handler/Manager, and Model layers are clearly separated
2. ✅ **Testability**: Controllers are standalone and testable
3. ✅ **Maintainability**: Responsibilities are clearly defined
4. ✅ **Reusability**: Shared controller instances across views
5. ✅ **Code Organization**: Consistent patterns throughout
6. ✅ **Data Integrity**: All model access through controllers

**Data Flow:** View → Controller/Handler/Manager → Model → Database ✅

**Build Status:** All builds successful ✅

---

**Document Version**: 1.1  
**Architecture Version**: 1.1  
**Status**: Complete and Implemented with Threading Safety  
**Last Updated**: December 26, 2025

## Recent Refactoring (v1.1)

**December 26, 2025 - Major Architecture Cleanup:**

1. **File Relocation**:
   - Moved `IntentHandler.java` from `ui/handler/` to `ui/` level
   - Moved `NavigationManager.java` from `ui/handler/` to `ui/` level
   - Moved `ProgressManager.java` from `ui/handler/` to `ui/` level
   - Moved `ShareManager.java` from `ui/handler/` to `ui/` level
   - Updated all imports across the project

2. **Threading Safety Enhancements**:
   - Added thread-safe UI operation methods to `BaseController`:
     - `handleError(view, message, exception)`
     - `showProgress(view, message)`
     - `hideProgress(view)`
     - `updateProgress(view, percentage, status)`
     - `showToast(view, message, length)`
   - All methods post to main thread via `mainHandler`
   - Fixed `ManageImController` threading issue: wrapped UI calls in `mainHandler.post()`

3. **Callback Refactoring**:
   - Moved `NavigationDrawerCallbacks` implementation from `MainActivity` to `NavigationManager`
   - `NavigationManager` now implements `NavigationDrawerFragment.NavigationDrawerCallbacks`
   - Added method: `onNavigationDrawerItemSelected(int position)`
   - Removed `onNavigationDrawerItemSelected()` from MainActivity
   - `SetupImController` now implements `ImportDialog.OnImportIMSelectedListener`
   - Added method: `onImportDialogImSelected(String tableName, boolean restoreUserRecords)`
   - Added method: `setFileToImport(File file)`
   - Removed import callback from MainActivity

4. **API Additions**:
   - `MainActivity.getNavigationManager()` - getter for NavigationManager
   - Added comprehensive javadoc to MainActivity
