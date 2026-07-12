# Issue #46 — Dark Keyboard Theme Doesn't Tint Navigation Bar

## Context

Reported by SmithCCho on Samsung A16 / Android 16 / LimeIME v6.0.0:

> 深色鍵盤樣式，導覽列沒變色。（sweetlime有變色）
> *"With the dark keyboard theme, the navigation bar doesn't change color. (sweetlime does change color.)"*

When the user picks the **Dark** keyboard theme, the IME's keyboard area renders dark gray (`#FF373737`), but the system navigation bar at the bottom of the screen stays light. The visual result is a sharp light strip glued under a dark keyboard, looking broken. An older theme variant the reporter calls "sweetlime" did tint the bar — meaning the platform supports it; LimeIME v6 just isn't doing it.

The intended outcome: every keyboard theme should paint the system navigation bar to match its keyboard background color, and the nav bar icons should be light or dark depending on the brightness of that background.

**Related:** this issue is a direct consequence of the edge-to-edge inset handler documented in `EDGE_TO_EDGE_REVIEW.md` § 2 (LIMEService). That review marked the IME as "✅ implemented" without noting that the inset-padded region needs its own background tint. Section 2 has been updated to cross-reference this issue.

## Root Cause

There are actually **two independent causes** for the light strip under a dark keyboard. The first is the obvious one in `setNavigationBarIconsDark()`. The second is in the edge-to-edge inset handler in `onCreateInputView()` and is the one that actually produces the visible strip on API 35+ devices like the reporter's.

### Cause A — `setNavigationBarIconsDark()` is theme-blind

`LIMEService.setNavigationBarIconsDark()` is the only place that touches the IME window's nav bar, and it has two problems:

**File:** `app/src/main/java/org/limeime/LIMEService.java:4392-4417`

```java
private void setNavigationBarIconsDark() {
    android.app.Dialog dialog = getWindow();
    if (dialog != null) {
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            View decorView = window.getDecorView();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                WindowInsetsControllerCompat windowInsetsController =
                    WindowCompat.getInsetsController(window, decorView);
                // hard-coded: always assume light background → dark icons
                windowInsetsController.setAppearanceLightNavigationBars(true);
            } else {
                window.setNavigationBarColor(0xFFFFFFFF); // hard-coded white
            }
        }
    }
}
```

Problems:

1. **It never sets the navigation bar background color on API 23+.** It only flips the *icon* appearance. The bar background therefore stays whatever the host app / system chose — usually system white in light mode.
2. **It is theme-blind.** It doesn't read `mKeyboardThemeIndex` (`LIMEService.java:4383`); it does the same thing regardless of which of the 6 themes is active.
3. **The light-icon assumption is wrong for the Dark theme.** Even if the background were correctly set to `#FF373737`, calling `setAppearanceLightNavigationBars(true)` would force *dark* icons on a *dark* bar — invisible.

Called from `LIMEService.onCreateInputView()` at line 430.

### Cause B — the edge-to-edge inset handler leaves the padded strip unpainted

**File:** `app/src/main/java/org/limeime/LIMEService.java:409-426`

```java
if (inputView != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
    ViewCompat.setOnApplyWindowInsetsListener(mCandidateInInputView, (v, insets) -> {
        int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
        v.setPadding(v.getPaddingLeft(), 0, v.getPaddingRight(), bottomInset);
        return insets;
    });
}
```

On API 35+ the IME window draws edge-to-edge, so the framework hands the IME a `bottomInset` equal to the gesture-bar height. The handler pads `mCandidateInInputView` by that amount so the bottom row of keys isn't covered. But the container's background is **transparent**, so the padded strip has no fill — whatever is behind the IME window (the host app's own nav bar, or the system default) shows through. That is the lighter gray strip the reporter sees.

`EDGE_TO_EDGE_REVIEW.md` documents this inset handler as "✅ implemented" but does not note that the padded region needs its own background tint to match the active theme — issue #46 is the direct consequence of that gap.

Setting `window.setNavigationBarColor()` on the IME dialog window does **not** fix cause B on its own: on the reporter's Samsung A16 / Android 16 device, the IME window ignores it (verified — applying `setDecorFitsSystemWindows(false)` + `FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS` + `setNavigationBarColor(bgColor)` produced no visible change). The fix has to paint the container itself.

The 6 themes and their background colors are already defined:

| Theme (index)        | Style                       | Background color (`res/values/colors.xml`) |
|---|---|---|
| Light (0)            | `LIMETheme_Light`           | `keyboard_background_light` `#FFC8C8C8` |
| **Dark (1)**         | `LIMETheme_Dark`            | `keyboard_background_dark` `#FF373737` |
| Pink (2)             | `LIMETheme_Pink`            | `keyboard_background_pink` `#FFFAD5E5` |
| TechBlue (3)         | `LIMETheme_TechBlue`        | `keyboard_background_tech_blue` `#FFC5DBEC` |
| FashionPurple (4)    | `LIMETheme_FashionPurple`   | `keyboard_background_fashion_purple` `#FFB0ACD5` |
| RelaxGreen (5)       | `LIMETheme_RelaxGreen`      | `keyboard_background_relax_green` `#FF8DC63F` |

The theme array is at `LIMEService.java:4374-4381`.

## Fix Strategy

Rewrite the helper into a single theme-aware method that, on every input view creation and re-show:

1. Paints `mCandidateInInputView`'s background with the active theme's keyboard background color — this is what actually makes the padded bottom strip match the keyboard visually (addresses Cause B).
2. Also sets the IME window's navigation bar color and light/dark icon appearance — belt-and-braces for devices/OEMs that do honor it, and to keep icons readable on light themes (addresses Cause A).
3. Picks light vs dark icons based on the background color's Rec. 709 luminance.

### Change — `LIMEService.java`

**Replace** `setNavigationBarIconsDark()` (lines 4392-4417) with `applyNavigationBarTheme()`:

```java
/**
 * Issue #46: Tint the system navigation bar to match the active keyboard theme,
 * and pick light/dark nav-bar icons based on the background's luminance so the
 * icons remain visible. Called from onCreateInputView() and onStartInputView().
 */
private void applyNavigationBarTheme() {
    android.app.Dialog dialog = getWindow();
    if (dialog == null) return;
    android.view.Window window = dialog.getWindow();
    if (window == null) return;

    // Let the IME window draw under the system bars — required for any chance of
    // setNavigationBarColor() painting. Not sufficient on its own on API 35+ IMEs,
    // hence the container-background paint below.
    WindowCompat.setDecorFitsSystemWindows(window, false);
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);

    int bgColor = getKeyboardBackgroundColorForCurrentTheme();
    window.setNavigationBarColor(bgColor);

    // THE actual fix: onCreateInputView() pads mCandidateInInputView by bottomInset
    // to clear the gesture bar, but leaves that strip transparent. Paint the
    // container background with the theme color so the strip matches the keyboard.
    if (mCandidateInInputView != null) {
        mCandidateInInputView.setBackgroundColor(bgColor);
    }

    // Luminance test (sRGB perceptual): values >= 0.5 are "light" backgrounds
    // and need DARK icons; darker backgrounds need LIGHT icons.
    boolean lightBackground = isColorLight(bgColor);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        // setAppearanceLightNavigationBars(true) ⇒ DARK icons (for LIGHT bar)
        controller.setAppearanceLightNavigationBars(lightBackground);
    }
    // API 21–22 cannot toggle nav-bar icon brightness; the colored bar alone
    // still gives the user the matching look.
}

private int getKeyboardBackgroundColorForCurrentTheme() {
    int colorRes;
    switch (mKeyboardThemeIndex) {
        case 1:  colorRes = R.color.keyboard_background_dark;            break;
        case 2:  colorRes = R.color.keyboard_background_pink;            break;
        case 3:  colorRes = R.color.keyboard_background_tech_blue;       break;
        case 4:  colorRes = R.color.keyboard_background_fashion_purple;  break;
        case 5:  colorRes = R.color.keyboard_background_relax_green;     break;
        case 0:
        default: colorRes = R.color.keyboard_background_light;           break;
    }
    return ContextCompat.getColor(this, colorRes);
}

private static boolean isColorLight(int color) {
    int r = (color >> 16) & 0xFF;
    int g = (color >>  8) & 0xFF;
    int b =  color        & 0xFF;
    // Rec. 709 luma
    double luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
    return luma >= 0.5;
}
```

**Update the call site** at `LIMEService.java:430`:

```java
applyNavigationBarTheme();
```

**Also call from `onStartInputView()`** so the bar updates immediately when the user switches theme without recreating the input view. Search for the existing override and add the call near the end (after `mKeyboardThemeIndex` has been refreshed from preferences).

If `ContextCompat` is not yet imported in this file, add:
```java
import androidx.core.content.ContextCompat;
```

No XML changes are needed — the existing `colors.xml` entries are reused.

## Critical Files

| File | Lines | Purpose |
|---|---|---|
| `app/src/main/java/org/limeime/LIMEService.java` | 90 (add `ContextCompat` import), 430 + 701 (call sites), 4393–4457 (replace `setNavigationBarIconsDark()` with `applyNavigationBarTheme()` + helpers) | Implement `applyNavigationBarTheme()` and call it. |
| `app/src/main/res/values/colors.xml` | 38, 54, 68, 76, 86, 96 | Source of the 6 keyboard background colors — read-only. |
| `docs/EDGE_TO_EDGE_REVIEW.md` | § 2 (LIMEService) | Cross-references this issue; notes that the inset-padded container needs its own background tint. |

## Implementation Status

✅ **Shipped** in this branch:
- `applyNavigationBarTheme()` is called from both `onCreateInputView()` (`LIMEService.java:431`) and `onStartInputView()` (`LIMEService.java:701`).
- It paints `mCandidateInInputView.setBackgroundColor(bgColor)` — **this is the change that actually makes the strip dark**.
- It also calls `WindowCompat.setDecorFitsSystemWindows(window, false)`, adds `FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS`, clears `FLAG_TRANSLUCENT_NAVIGATION`, and sets `window.setNavigationBarColor(bgColor)` — these are belt-and-braces for OEMs/API levels that do honor nav-bar color on IME windows, and a no-op on those that don't.
- Light/dark icon brightness is picked from Rec. 709 luma of the background.

## Verification

1. Build & install on the reporter's device class (Samsung / Android 16) or any phone with a visible gesture bar.
2. **Dark theme** (Settings → LimeIME → Keyboard theme → Dark): open any text field. The nav bar must be `#FF373737` and its icons must be **light/white** so they're visible.
3. **Light theme:** nav bar must be `#FFC8C8C8` with dark icons.
4. **Pink, TechBlue, FashionPurple, RelaxGreen:** each must paint the nav bar to its respective color; icons must auto-pick the readable contrast.
5. **Switch themes while the IME is showing** (open Settings, change theme, return to text field) — the bar should reflect the new theme on the next `onStartInputView()`.
6. **Gesture nav vs 3-button nav:** verify both look correct.
7. **Light/Dark system mode toggle:** ensure the IME's nav bar tint isn't overridden by system DayNight after the IME hides (host app should reclaim its own nav bar color, which is the standard IME contract).
8. **Logcat sanity:** add a temporary `Log.i(TAG, "applyNavigationBarTheme bg=#" + Integer.toHexString(bgColor) + " lightBg=" + lightBackground)` during testing to confirm the right color/luma is chosen for each theme.
