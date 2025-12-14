# API Compatibility Review (API 21-36)

## Summary
This document reviews the project's compatibility across Android API levels 21-36.

**Current Configuration:**
- `minSdkVersion`: 21 (Android 5.0 Lollipop)
- `targetSdkVersion`: 36 (Android 15)
- `compileSdkVersion`: 36

## ✅ Properly Handled APIs

### 1. **PackageInfo.versionCode** ✅
- **Location**: `MainActivity.java`, `SetupImFragment.java`
- **Status**: ✅ Fixed
- **Implementation**: Uses `PackageInfoCompat.getLongVersionCode()` which handles all API levels automatically
- **Compatibility**: Works on API 21-36

### 2. **Context.getDataDir()** ✅
- **Location**: `DBServer.java`
- **Status**: ✅ Fixed
- **Implementation**: Uses `ContextCompat.getDataDir()` with fallback to `getFilesDir().getParent()`
- **Compatibility**: Works on API 21-36

### 3. **Vibrator APIs** ✅
- **Location**: `LIMEService.java`
- **Status**: ✅ Fixed
- **Implementation**: 
  - API 31+: Uses `VibratorManager.getDefaultVibrator()`
  - API 26-30: Uses `VibrationEffect.createOneShot()`
  - API < 26: Uses deprecated `vibrate(long)` with `@SuppressWarnings`
- **Compatibility**: Works on API 21-36

### 4. **ConnectivityManager.getActiveNetwork()** ✅
- **Location**: `SetupImLoadDialog.java`
- **Status**: ✅ Fixed
- **Implementation**: 
  - API 23+: Uses `getActiveNetwork()` and `getNetworkCapabilities()`
  - API < 23: Uses deprecated `getActiveNetworkInfo()` with `@SuppressWarnings`
- **Compatibility**: Works on API 21-36

### 5. **WindowInsets for Navigation Bar** ✅
- **Location**: `LIMEService.java`
- **Status**: ✅ Fixed
- **Implementation**: Uses `ViewCompat.setOnApplyWindowInsetsListener()` with `WindowInsetsCompat`
- **Compatibility**: Works on API 21-36

### 6. **Fragment APIs** ✅
- **Locations**: Multiple fragment files
- **Status**: ✅ Fixed
- **Implementation**: 
  - Migrated to AndroidX Fragments
  - `onAttach(Context)` instead of `onAttach(Activity)`
  - `getParentFragmentManager()` instead of `getFragmentManager()`
- **Compatibility**: Works on API 21-36

### 7. **ProgressDialog** ✅
- **Location**: `ManageImFragment.java`, `ManageRelatedFragment.java`
- **Status**: ✅ Already Migrated
- **Implementation**: Both files use `ProgressBar` with visibility toggling instead of deprecated `ProgressDialog`
- **Compatibility**: Works on API 21-36

### 8. **Display.getSize() and Display.getDefaultDisplay()** ✅
- **Location**: `CandidateView.java`
- **Status**: ✅ Fixed with proper suppression
- **Implementation**: 
  - API 30+: Uses `WindowManager.getCurrentWindowMetrics()` (modern API)
  - API < 30: Uses deprecated `Display.getDefaultDisplay()` and `Display.getSize()` with `@SuppressWarnings("deprecation")`
- **Compatibility**: Works on API 21-36

### 9. **Edge-to-Edge Display (API 35+ Requirement)** ✅
- **Locations**: `MainActivity.java`, `LIMEPreferenceHC.java`, `NavigationDrawerFragment.java`, `LIMEService.java`
- **Status**: ✅ Fully Implemented
- **Implementation**:
  - **MainActivity**: 
    - Enabled edge-to-edge with `WindowCompat.setDecorFitsSystemWindows(getWindow(), false)`
    - Removed `android:fitsSystemWindows="true"` from `activity_main.xml`
    - Applied window insets to content container (`R.id.container`) - status bar inset only
    - ActionBar handles its own space automatically
    - Set status bar and navigation bar to transparent
    - ActionBar title properly displayed
  - **LIMEPreferenceHC**: 
    - Enabled edge-to-edge with `WindowCompat.setDecorFitsSystemWindows(getWindow(), false)`
    - Applied window insets to content view (`android.R.id.content`) for PreferenceFragment
    - Status bar inset only (ActionBar handles its own space)
    - Set status bar and navigation bar to transparent
    - Prevents ActionBar overlap with preference content
  - **NavigationDrawerFragment**: 
    - Applied window insets to drawer ListView via `setupDrawerInsets()` method
    - Top padding = status bar + ActionBar height (drawer extends behind ActionBar)
    - Bottom padding = navigation bar inset
    - Prevents drawer menu overlap with ActionBar
  - **LIMEService**: 
    - Already had window insets handling for IME keyboard
    - Applies bottom padding to keyboard input view for navigation bar
    - Prevents keyboard overlap with system gesture navigation bar
- **Compatibility**: Works on API 21-36 (required for API 35+)

### 10. **Window.setStatusBarColor() and setNavigationBarColor()** ⚠️
- **Locations**: `MainActivity.java`, `LIMEPreferenceHC.java`
- **Status**: ⚠️ Properly Suppressed
- **Implementation**: 
  - Methods are deprecated in API 35+ but still used for backward compatibility
  - Wrapped with `@SuppressWarnings("deprecation")` and API level check (LOLLIPOP+)
  - Used to set transparent colors for edge-to-edge display
- **Compatibility**: Works on API 21-36

## ⚠️ Deprecated APIs (Properly Suppressed)

### 1. **Drawable.getOpacity()** ✅
- **Location**: `LIMEKeyboard.java` (line 585)
- **Status**: ✅ Properly Suppressed
- **Implementation**: Method is annotated with `@SuppressWarnings("deprecation")` as required by Drawable interface
- **Note**: No alternative available - this method is part of the Drawable interface contract
- **Compatibility**: Works on API 21-36

### 2. **android.R.attr.state_long_pressable** ℹ️
- **Location**: Not currently used in codebase
- **Status**: ℹ️ Not Used
- **Note**: This deprecated attribute is not referenced anywhere in the codebase. If needed in the future for drawable state selectors, it should be used with `@SuppressWarnings("deprecation")` or avoided if possible.
- **Compatibility**: N/A (not used)

## 📋 Dependencies Review

### AndroidX Libraries
- ✅ `androidx.appcompat:appcompat:1.7.1` - Supports API 14+
- ✅ `androidx.core:core:1.15.0` - Supports API 21+ (provides compatibility methods)
- ✅ `androidx.preference:preference:1.2.1` - Supports API 14+
- ✅ `androidx.multidex:multidex:2.0.1` - Supports API 14+

### Third-Party Libraries
- ✅ `net.lingala.zip4j:zip4j:2.11.5` - Java library, no Android API dependencies

## 🔍 Potential Issues to Monitor

### 1. **Intent.ACTION_OPEN_DOCUMENT**
- **Location**: `SetupImLoadDialog.java`
- **Status**: ✅ Safe
- **Note**: Available since API 19, safe for API 21+

### 2. **ActivityResultLauncher**
- **Location**: Multiple files
- **Status**: ✅ Safe
- **Note**: Part of AndroidX Activity library, supports API 21+

### 3. **NotificationChannel**
- **Location**: `LIMEUtilities.java`
- **Status**: ✅ Safe
- **Note**: Properly checked with `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O`

## ✅ Code Quality Improvements Made

1. ✅ Fixed String concatenation in loops (StringBuilder usage)
2. ✅ Fixed resource leaks (try-with-resources for ZipFile)
3. ✅ Fixed unboxing NullPointerException risks
4. ✅ Fixed unreachable code conditions
5. ✅ Fixed catch parameter naming issues
6. ✅ Removed unused code
7. ✅ Implemented edge-to-edge display support for API 35+ compliance
8. ✅ Fixed ActionBar and content overlap issues with proper window insets handling
9. ✅ Fixed drawer menu overlap with ActionBar
10. ✅ Fixed LIMEPreferenceHC ActionBar overlap with preference content

## 📊 API Level Coverage

| API Level | Android Version | Status |
|-----------|----------------|--------|
| 21 | Lollipop | ✅ Supported |
| 22 | Lollipop MR1 | ✅ Supported |
| 23 | Marshmallow | ✅ Supported |
| 24-25 | Nougat | ✅ Supported |
| 26-27 | Oreo | ✅ Supported |
| 28 | Pie | ✅ Supported |
| 29 | Android 10 | ✅ Supported |
| 30 | Android 11 | ✅ Supported |
| 31 | Android 12 | ✅ Supported |
| 32-33 | Android 12L-13 | ✅ Supported |
| 34 | Android 14 | ✅ Supported |
| 35-36 | Android 15 | ✅ Supported |

## 🎯 Recommendations

1. **Monitor Deprecations**: Keep an eye on Android release notes for new deprecations
2. **Test on Multiple Devices**: Test on devices running API 21, 26, 30, and 36
3. **Update Dependencies**: Regularly update AndroidX libraries for latest compatibility fixes
4. **Code Reviews**: Continue reviewing new code for API compatibility

## ✅ Edge-to-Edge Compliance (API 35+)

The project is **fully compliant** with API 35+ edge-to-edge requirements:

- ✅ **MainActivity**: 
  - Edge-to-edge enabled with `WindowCompat.setDecorFitsSystemWindows()`
  - Window insets applied to content container
  - ActionBar title properly displayed
  - No content overlap issues
- ✅ **LIMEPreferenceHC**: 
  - Edge-to-edge enabled with `WindowCompat.setDecorFitsSystemWindows()`
  - Window insets applied to content view for PreferenceFragment
  - ActionBar no longer overlaps preference content
- ✅ **NavigationDrawerFragment**: 
  - Window insets applied to drawer ListView
  - Drawer menu properly spaced below ActionBar
  - No overlap with ActionBar or system bars
- ✅ **LIMEService**: 
  - IME keyboard has proper navigation bar insets
  - Keyboard doesn't overlap with gesture navigation bar
- ✅ **System Bars**: 
  - Status bar and navigation bar set to transparent
  - Content extends behind system bars as required
- ✅ **Content Spacing**: 
  - All content properly accounts for system bars
  - ActionBar spacing handled correctly
  - No white gaps or overlaps

**All Activities and Services Reviewed:**
- ✅ `MainActivity` - Edge-to-edge implemented
- ✅ `LIMEPreferenceHC` - Edge-to-edge implemented  
- ✅ `LIMEService` - Window insets for keyboard implemented
- ✅ `NavigationDrawerFragment` - Window insets for drawer implemented

For detailed edge-to-edge implementation, see `EDGE_TO_EDGE_REVIEW.md`.

## ✅ Conclusion

The project is **fully prepared** for API levels 21-36. All critical APIs have been:
- ✅ Wrapped with compatibility methods (ContextCompat, PackageInfoCompat)
- ✅ Protected with API level checks where needed
- ✅ Properly suppressed with `@SuppressWarnings` where no alternatives exist
- ✅ Using AndroidX libraries for backward compatibility
- ✅ **Edge-to-edge compliant for API 35+** (required for Android 15+)

The codebase follows Android best practices for multi-API-level support and meets all requirements for API 35+ edge-to-edge display.

