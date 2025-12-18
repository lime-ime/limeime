# Android Manifest Permission Review

**Generated**: 2025-01-XX  
**Project**: LimeIME  
**Target SDK**: 36 (Android 16)  
**Min SDK**: 21 (Android 5.0)

## Summary

This report reviews all permissions declared in `AndroidManifest.xml` and verifies their usage in the codebase. Permissions that are not used should be removed to minimize the app's permission footprint and improve user trust.

---

## ✅ Required Permissions (In Use)

### 1. **`POST_NOTIFICATIONS`** ✅ REQUIRED

**Location**: `AndroidManifest.xml:43`

**Usage**:
- `LIMEUtilities.java:352` - `showNotification()` method
- `DBServer.java:702` - `showNotificationMessage()` method
- `SearchServer.java:198` - Shows notifications

**Purpose**: Required for displaying notifications (API 33+). Used for backup/restore status, download progress, and search notifications.

**Status**: ✅ **REQUIRED** - Actively used

---

### 2. **`VIBRATE`** ✅ REQUIRED

**Location**: `AndroidManifest.xml:44`

**Usage**:
- `LIMEService.java:3624` - `getVibrator()` method
- `LIMEService.java:3645` - `vibrate()` method
- `LIMEService.java:3671` - Called on keypress
- `CandidateView.java:1473` - Vibration on candidate selection

**Purpose**: Provides haptic feedback when typing and selecting candidates.

**Status**: ✅ **REQUIRED** - Actively used

---

### 3. **`INTERNET`** ✅ REQUIRED

**Location**: `AndroidManifest.xml:45`

**Usage**:
- `SetupImLoadRunnable.java:423` - `downloadRemoteFile()` method
- `DBServer.java:483` - `downloadRemoteFile()` method
- Multiple download operations for input method databases

**Purpose**: Required for downloading input method databases and updates from remote servers.

**Status**: ✅ **REQUIRED** - Actively used

---

### 4. **`ACCESS_NETWORK_STATE`** ✅ REQUIRED

**Location**: `AndroidManifest.xml:50`

**Usage**:
- `SetupImLoadDialog.java:381` - `isNetworkAvailable()` method
- Uses `ConnectivityManager.getActiveNetwork()` and `getNetworkCapabilities()` (API 23+)
- Uses deprecated `getActiveNetworkInfo()` for API < 23

**Purpose**: Checks network availability before attempting downloads.

**Status**: ✅ **REQUIRED** - Actively used

---

## ❌ Unused Permissions (Should Be Removed)

### 5. **`READ_USER_DICTIONARY`** ❌ NOT USED

**Location**: `AndroidManifest.xml:46`

**Usage Search**: No usage found in codebase

**Analysis**:
- No calls to `UserDictionary.getUserDictionaryWords()` or similar APIs
- The app uses its own internal database (`LimeDB`) for word storage
- `addWord()` methods found are for internal database, not Android UserDictionary

**Recommendation**: ❌ **REMOVE** - Not used anywhere in the codebase

---

### 6. **`WAKE_LOCK`** ❌ NOT USED

**Location**: `AndroidManifest.xml:47`

**Usage Search**: No usage found in codebase

**Analysis**:
- No `PowerManager` usage found
- No `newWakeLock()` calls
- Only reference found is a comment about file locking in `LimeSQLiteOpenHelper.java:146` (SQLite file lock, not PowerManager wake lock)

**Recommendation**: ❌ **REMOVE** - Not used anywhere in the codebase

---

### 7. **`WRITE_USER_DICTIONARY`** ❌ NOT USED

**Location**: `AndroidManifest.xml:48`

**Usage Search**: No usage found in codebase

**Analysis**:
- No calls to `UserDictionary.addWord()` or `UserDictionary.updateUserDictionary()`
- The app uses its own internal database for word management
- All word operations are on internal `LimeDB` tables

**Recommendation**: ❌ **REMOVE** - Not used anywhere in the codebase

---

### 8. **`WRITE_SETTINGS`** ❌ NOT USED

**Location**: `AndroidManifest.xml:49`

**Usage Search**: No usage found in codebase

**Analysis**:
- Only **reading** `Settings.Secure.getString()` for `DEFAULT_INPUT_METHOD`
- No **writing** to `Settings.System`, `Settings.Secure`, or `Settings.Global`
- Reading `Settings.Secure` does not require `WRITE_SETTINGS` permission
- `WRITE_SETTINGS` is only needed for `Settings.System.put*()` operations

**Recommendation**: ❌ **REMOVE** - Only reading settings, not writing

---

### 9. **`ACCESS_WIFI_STATE`** ❌ NOT USED

**Location**: `AndroidManifest.xml:51`

**Usage Search**: No usage found in codebase

**Analysis**:
- No `WifiManager` usage found
- No calls to `getWifiState()`, `isWifiEnabled()`, or similar
- Network availability is checked via `ConnectivityManager` (which only requires `ACCESS_NETWORK_STATE`)

**Recommendation**: ❌ **REMOVE** - Not used anywhere in the codebase

---

## 📋 Permission Summary

| Permission | Status | Usage | Recommendation |
|------------|--------|-------|----------------|
| `POST_NOTIFICATIONS` | ✅ Required | Notifications for backup/restore/download | **KEEP** |
| `VIBRATE` | ✅ Required | Haptic feedback on keypress | **KEEP** |
| `INTERNET` | ✅ Required | Downloading input method databases | **KEEP** |
| `ACCESS_NETWORK_STATE` | ✅ Required | Check network before downloads | **KEEP** |
| `READ_USER_DICTIONARY` | ❌ Unused | No usage found | **REMOVE** |
| `WAKE_LOCK` | ❌ Unused | No usage found | **REMOVE** |
| `WRITE_USER_DICTIONARY` | ❌ Unused | No usage found | **REMOVE** |
| `WRITE_SETTINGS` | ❌ Unused | Only reading, not writing | **REMOVE** |
| `ACCESS_WIFI_STATE` | ❌ Unused | No usage found | **REMOVE** |

---

## 🔧 Recommended Actions

### Immediate Actions

1. **Remove unused permissions** from `AndroidManifest.xml`:
   - `READ_USER_DICTIONARY`
   - `WAKE_LOCK`
   - `WRITE_USER_DICTIONARY`
   - `WRITE_SETTINGS`
   - `ACCESS_WIFI_STATE`

### Benefits of Removal

- ✅ **Reduced permission footprint** - Users see fewer permissions at install
- ✅ **Improved user trust** - Fewer permissions = more trust
- ✅ **Better Play Store compliance** - Google Play requires justification for all permissions
- ✅ **Security** - Principle of least privilege

---

## 📝 Notes

### Settings.Secure Usage

The app reads `Settings.Secure.DEFAULT_INPUT_METHOD` in:
- `LIMEUtilities.java:432`
- `LIMEService.java:3825, 3954, 3980, 4003`

**Note**: Reading `Settings.Secure` does **not** require `WRITE_SETTINGS` permission. Only writing to `Settings.System` requires this permission.

### User Dictionary

The app maintains its own internal dictionary database (`LimeDB`) and does not use Android's `UserDictionary` API. All word management operations are performed on internal tables.

### Network State

The app uses `ACCESS_NETWORK_STATE` (required) but not `ACCESS_WIFI_STATE` (unused). The `ConnectivityManager` API used in `SetupImLoadDialog.isNetworkAvailable()` only requires `ACCESS_NETWORK_STATE`.

---

## ✅ Verification Checklist

- [x] All permissions in manifest reviewed
- [x] Codebase searched for permission usage
- [x] Unused permissions identified
- [x] Required permissions verified
- [x] Recommendations provided

**Conclusion**: 4 out of 9 permissions are required and actively used. 5 unused permissions have been removed to reduce the app's permission footprint.

