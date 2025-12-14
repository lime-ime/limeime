# Edge-to-Edge Compatibility Review (API 35+)

## Summary
This document reviews the project's edge-to-edge display compatibility for Android API 35+ (Android 15+). Starting with API 35, apps are required to display content edge-to-edge, meaning the app's interface extends behind system bars (status bar and navigation bar).

**Current Configuration:**
- `minSdkVersion`: 21 (Android 5.0 Lollipop)
- `targetSdkVersion`: 36 (Android 15)
- `compileSdkVersion`: 36

## ✅ Edge-to-Edge Implementation Status

### 1. **MainActivity** ✅
- **Location**: `app/src/main/java/net/toload/main/hd/MainActivity.java`
- **Status**: ✅ Implemented
- **Implementation**:
  - Enabled edge-to-edge using `WindowCompat.setDecorFitsSystemWindows(getWindow(), false)`
  - Removed `android:fitsSystemWindows="true"` from `activity_main.xml`
  - Added `setupEdgeToEdge()` method to handle window insets programmatically
  - Applied window insets to `DrawerLayout` to prevent content overlap
  - Set status bar and navigation bar colors to transparent
- **Compatibility**: Works on API 21-36 (backward compatible, required for API 35+)

### 2. **LIMEService (IME Keyboard)** ✅
- **Location**: `app/src/main/java/net/toload/main/hd/LIMEService.java`
- **Status**: ✅ Already Implemented
- **Implementation**: 
  - Uses `ViewCompat.setOnApplyWindowInsetsListener()` in `onCreateInputView()`
  - Applies bottom padding to keyboard view to account for navigation bar
  - Prevents keyboard overlap with system gesture navigation bar
- **Compatibility**: Works on API 21-36

### 3. **LIMEPreferenceHC (Settings Activity)** ✅
- **Location**: `app/src/main/java/net/toload/main/hd/limesettings/LIMEPreferenceHC.java`
- **Status**: ✅ Fully Implemented
- **Implementation**:
  - Enabled edge-to-edge using `WindowCompat.setDecorFitsSystemWindows(getWindow(), false)`
  - Added `setupEdgeToEdge()` method to handle window insets programmatically
  - Applied window insets to content view (`android.R.id.content`) for PreferenceFragment
  - Status bar inset only (ActionBar handles its own space)
  - Set status bar and navigation bar colors to transparent
  - ActionBar title properly displayed
  - Prevents ActionBar overlap with preference content
- **Compatibility**: Works on API 21-36 (backward compatible, required for API 35+)

### 4. **NavigationDrawerFragment** ✅
- **Location**: `app/src/main/java/net/toload/main/hd/NavigationDrawerFragment.java`
- **Status**: ✅ Implemented
- **Implementation**:
  - Added `setupDrawerInsets()` method to handle window insets for drawer ListView
  - Applies top padding = status bar + ActionBar height to prevent drawer menu overlap
  - Applies bottom padding for navigation bar
- **Compatibility**: Works on API 21-36

### 5. **Layout Files** ✅
- **Location**: `app/src/main/res/layout/activity_main.xml`
- **Status**: ✅ Updated
- **Changes**:
  - Removed `android:fitsSystemWindows="true"` from `DrawerLayout`
  - Window insets are now handled programmatically in `MainActivity`
- **Compatibility**: Works on API 21-36

## 📋 Implementation Details

### Window Insets Handling
The app uses `WindowInsetsCompat` to handle system bars properly:

1. **MainActivity**: 
   - Applies padding to content container (`R.id.container`)
   - Top padding = status bar inset only (ActionBar handles its own space)
   - Left/right/bottom padding = system bar insets
2. **LIMEPreferenceHC**: 
   - Applies padding to content view (`android.R.id.content`)
   - Top padding = status bar inset only (ActionBar handles its own space)
   - Left/right/bottom padding = system bar insets
3. **NavigationDrawerFragment**: 
   - Applies padding to drawer ListView
   - Top padding = status bar + ActionBar height (drawer extends behind ActionBar)
   - Bottom padding = navigation bar inset
4. **LIMEService**: 
   - Applies bottom padding to keyboard input view for navigation bar
   - Prevents keyboard overlap with gesture navigation bar

### System Bar Colors
- Status bar: Transparent (allows content to extend behind)
- Navigation bar: Transparent (allows content to extend behind)

### Edge-to-Edge Enablement
- `WindowCompat.setDecorFitsSystemWindows(window, false)` is called in `MainActivity.onCreate()`
- This allows the app to draw behind system bars on all supported API levels
- Required for API 35+ compliance

## 🔍 Components Reviewed

### Activities
- ✅ **MainActivity**: Fully implemented edge-to-edge support
- ✅ **LIMEPreferenceHC**: Fully implemented edge-to-edge support

### Services
- ✅ **LIMEService**: Already has window insets handling for keyboard

### Layouts
- ✅ **activity_main.xml**: Updated to remove `fitsSystemWindows`
- ⚠️ **Fragment layouts**: Reviewed - no changes needed (fragments inherit from parent activity)

### Dialogs
- ℹ️ **Dialog fragments**: Use standard dialog windows, not affected by edge-to-edge requirements

## ⚠️ Considerations

### DrawerLayout
- The `DrawerLayout` in `MainActivity` now handles window insets programmatically
- Padding is applied to prevent content from being obscured by system bars
- Drawer navigation should work correctly with edge-to-edge

### ActionBar/Toolbar
- The app uses `AppCompatActivity` with `ActionBar`
- ActionBar automatically handles edge-to-edge when using AppCompat themes
- No additional changes needed

### Fragment Content
- Fragments are displayed within the `FrameLayout` container in `MainActivity`
- They inherit the edge-to-edge behavior from the parent activity
- No individual fragment changes required

## 📊 API Level Coverage

| API Level | Android Version | Edge-to-Edge Status |
|-----------|----------------|---------------------|
| 21-34 | Lollipop - Android 14 | ✅ Supported (optional) |
| 35-36 | Android 15 | ✅ Required & Implemented |

## 🎯 Testing Recommendations

1. **Test on API 35+ devices**: Verify content is not obscured by system bars
2. **Test in both orientations**: Portrait and landscape modes
3. **Test with different navigation modes**: 
   - Gesture navigation
   - Button navigation
   - Three-button navigation
4. **Test DrawerLayout**: Ensure drawer opens correctly and content is accessible
5. **Test keyboard**: Verify IME keyboard doesn't overlap with navigation bar
6. **Test status bar**: Ensure content below status bar is readable

## ✅ Conclusion

The project is **fully prepared** for API 35+ edge-to-edge requirements:

- ✅ Edge-to-edge is enabled in `MainActivity` and `LIMEPreferenceHC`
- ✅ Window insets are properly handled in all activities and fragments
- ✅ System bars are transparent
- ✅ IME keyboard has proper insets handling
- ✅ Drawer menu has proper insets to prevent ActionBar overlap
- ✅ Layouts are updated for edge-to-edge
- ✅ Backward compatible with API 21-34

The implementation follows Android best practices for edge-to-edge display and ensures a consistent user experience across all supported API levels.

## 📝 Notes

- Edge-to-edge is enabled for all API levels (21-36) for consistency, even though it's only required for API 35+
- The implementation uses AndroidX compatibility libraries (`WindowCompat`, `ViewCompat`, `WindowInsetsCompat`) for backward compatibility
- No breaking changes were introduced - the app maintains full functionality on older API levels

