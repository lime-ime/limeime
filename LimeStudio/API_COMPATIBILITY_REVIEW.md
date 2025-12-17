# API Compatibility Review (API 21-36)

**Last Updated**: 2025-01-XX  
**Review Status**: ✅ Current and Verified

## Summary
This document reviews the project's compatibility across Android API levels 21-36.

**Current Configuration:**
- `minSdkVersion`: 21 (Android 5.0 Lollipop)
- `targetSdkVersion`: 36 (Android 16)
- `compileSdkVersion`: 36
- `Java Version`: 11 (sourceCompatibility & targetCompatibility)

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
- **Locations**: `MainActivity.java`, `LIMEPreferenceHC.java`, `LIMEService.java`
- **Status**: ⚠️ Properly Suppressed
- **Implementation**: 
  - Methods are deprecated in API 35+ but still used for backward compatibility
  - Wrapped with `@SuppressWarnings("deprecation")` and API level check (LOLLIPOP+)
  - Used to set transparent colors for edge-to-edge display
  - `LIMEService.java` uses `setNavigationBarColor()` for API 21-22 compatibility
- **Compatibility**: Works on API 21-36

### 11. **WindowInsetsControllerCompat for Navigation Bar Appearance** ✅
- **Location**: `LIMEService.java`
- **Status**: ✅ Properly Handled
- **Implementation**:
  - API 23+: Uses `WindowInsetsControllerCompat.setAppearanceLightNavigationBars()`
  - API 21-22: Uses deprecated `setNavigationBarColor()` with `@SuppressWarnings("deprecation")`
  - Properly handles navigation bar icon appearance across all API levels
- **Compatibility**: Works on API 21-36

## ⚠️ Deprecated APIs (Properly Suppressed)

### 1. **Drawable.getOpacity()** ✅
- **Location**: `LIMEKeyboard.java` (line 585)
- **Status**: ✅ Properly Suppressed
- **Implementation**: Method is annotated with `@SuppressWarnings("deprecation")` as required by Drawable interface
- **Note**: No alternative available - this method is part of the Drawable interface contract
- **Compatibility**: Works on API 21-36

### 2. **Display.getDefaultDisplay() and Display.getSize()** ✅
- **Location**: `CandidateView.java` (lines 253-256)
- **Status**: ✅ Properly Suppressed
- **Implementation**: 
  - Deprecated in API 30+, but necessary for API < 30
  - Wrapped with `@SuppressWarnings("deprecation")`
  - Only used when `SDK_INT < VERSION_CODES.R` (API 30)
  - Modern API uses `WindowManager.getCurrentWindowMetrics()` for API 30+
- **Compatibility**: Works on API 21-36

### 3. **Vibrator.vibrate(long)** ✅
- **Location**: `LIMEService.java` (line 3630)
- **Status**: ✅ Properly Suppressed
- **Implementation**: 
  - Deprecated in API 26+, but necessary for API < 26
  - Wrapped with `@SuppressWarnings("deprecation")`
  - Only used when `SDK_INT < VERSION_CODES.O` (API 26)
  - Modern API uses `VibrationEffect.createOneShot()` for API 26+
- **Compatibility**: Works on API 21-36

### 4. **Window.setNavigationBarColor()** ✅
- **Location**: `LIMEService.java` (line 3825)
- **Status**: ✅ Properly Suppressed
- **Implementation**: 
  - Deprecated in API 35+, but necessary for API 21-22
  - Wrapped with `@SuppressWarnings("deprecation")`
  - Only used when `SDK_INT < VERSION_CODES.M` (API 23)
  - Modern API uses `WindowInsetsControllerCompat` for API 23+
- **Compatibility**: Works on API 21-36

### 5. **android.R.attr.state_long_pressable** ℹ️
- **Location**: Not currently used in codebase
- **Status**: ℹ️ Not Used
- **Note**: This deprecated attribute is not referenced anywhere in the codebase. If needed in the future for drawable state selectors, it should be used with `@SuppressWarnings("deprecation")` or avoided if possible.
- **Compatibility**: N/A (not used)

## 📋 Dependencies Review

### AndroidX Libraries
- ✅ `androidx.appcompat:appcompat:1.7.1` - Supports API 14+ (compatible with minSdk 21)
- ✅ `androidx.core:core:1.15.0` - Supports API 21+ (provides compatibility methods like ContextCompat, WindowInsetsCompat)
- ✅ `androidx.legacy:legacy-support-v4:1.0.0` - Legacy support library (for backward compatibility)
- ✅ `androidx.preference:preference:1.2.1` - Supports API 14+ (compatible with minSdk 21)
- ✅ `androidx.multidex:multidex:2.0.1` - Supports API 14+ (required for multidex support)

### Third-Party Libraries
- ✅ `net.lingala.zip4j:zip4j:2.11.5` - Java library, no Android API dependencies
- ✅ `org.jetbrains.kotlin:kotlin-bom:2.2.21` - Kotlin BOM (for dependency management, though project uses Java)

### Test Dependencies
- ✅ `junit:junit:4.13.2` - JUnit 4 testing framework
- ✅ `androidx.test.ext:junit:1.2.1` - AndroidX JUnit extensions
- ✅ `androidx.test:runner:1.6.2` - Android test runner
- ✅ `androidx.test:rules:1.6.1` - Android test rules
- ✅ `androidx.test:monitor:1.8.0` - Android test monitor

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

### 4. **WindowManager.getCurrentWindowMetrics()**
- **Location**: `CandidateView.java`
- **Status**: ✅ Safe
- **Note**: Available since API 30 (R), properly checked with `SDK_INT >= VERSION_CODES.R`
- **Fallback**: Uses deprecated `Display.getDefaultDisplay()` and `Display.getSize()` for API < 30 with proper suppression

### 5. **VibratorManager**
- **Location**: `LIMEService.java`
- **Status**: ✅ Safe
- **Note**: Available since API 31 (S), properly checked with `SDK_INT >= VERSION_CODES.S`
- **Fallback**: Uses deprecated `Vibrator` service for API < 31

### 6. **WindowInsetsControllerCompat**
- **Location**: `LIMEService.java`
- **Status**: ✅ Safe
- **Note**: Part of AndroidX Core library, supports API 21+
- **Fallback**: Uses deprecated `setNavigationBarColor()` for API 21-22 with proper suppression

## ✅ Code Quality Improvements Made

1. ✅ Fixed String concatenation in loops (StringBuilder usage)
2. ✅ Fixed resource leaks (try-with-resources for ZipFile, TypedArray)
3. ✅ Fixed unboxing NullPointerException risks
4. ✅ Fixed unreachable code conditions
5. ✅ Fixed catch parameter naming issues
6. ✅ Removed unused code
7. ✅ Implemented edge-to-edge display support for API 35+ compliance
8. ✅ Fixed ActionBar and content overlap issues with proper window insets handling
9. ✅ Fixed drawer menu overlap with ActionBar
10. ✅ Fixed LIMEPreferenceHC ActionBar overlap with preference content
11. ✅ All deprecated APIs properly suppressed with `@SuppressWarnings("deprecation")`
12. ✅ All API level checks use proper version code constants (`VERSION_CODES.*`)
13. ✅ Modern APIs used when available with proper fallbacks for older versions

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
| 35 | Android 15 | ✅ Supported |
| 36 | Android 16 | ✅ Supported |

## 🎯 Recommendations

1. **Monitor Deprecations**: Keep an eye on Android release notes for new deprecations
2. **Test on Multiple Devices**: Test on devices running API 21, 26, 30, and 36
3. **Update Dependencies**: Regularly update AndroidX libraries for latest compatibility fixes
4. **Code Reviews**: Continue reviewing new code for API compatibility
5. **API Level Checks**: Always use `Build.VERSION_CODES.*` constants instead of magic numbers
6. **Deprecation Suppression**: Always add `@SuppressWarnings("deprecation")` when using deprecated APIs with proper API level checks
7. **Modern API Migration**: When new APIs become available, migrate gradually with proper fallbacks for older versions
8. **Documentation**: Update this document when adding new API level checks or handling deprecated APIs

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
- ✅ Wrapped with compatibility methods (ContextCompat, PackageInfoCompat, WindowInsetsCompat)
- ✅ Protected with API level checks where needed (using `Build.VERSION_CODES.*` constants)
- ✅ Properly suppressed with `@SuppressWarnings("deprecation")` where no alternatives exist
- ✅ Using AndroidX libraries for backward compatibility
- ✅ **Edge-to-edge compliant for API 35+** (required for Android 15+)
- ✅ Modern APIs used when available (WindowMetrics, VibratorManager, WindowInsetsControllerCompat)
- ✅ Proper fallbacks implemented for older API levels

### Verification Summary
- ✅ **Build Configuration**: Verified `minSdkVersion=21`, `targetSdkVersion=36`, `compileSdkVersion=36`
- ✅ **API Level Checks**: All checks use proper `VERSION_CODES` constants
- ✅ **Deprecated APIs**: All properly suppressed with annotations and API level guards
- ✅ **Compatibility Methods**: Using AndroidX compatibility libraries where available
- ✅ **Modern APIs**: Using latest APIs with proper fallbacks for older versions

The codebase follows Android best practices for multi-API-level support and meets all requirements for API 35+ edge-to-edge display. All API usage has been reviewed and verified for compatibility across the entire API range (21-36).

