# Issue #47 — Soft Keyboard Right-Side Clipping

## Context

The on-screen soft keyboard is clipped on the right edge — the rightmost column of keys (e.g. `p`, `0`, backspace area) is partially cut off and not fully visible. This affects usability because keys near the right edge are hard to tap or visually confusing.

The screenshot evidence (Android phone, Chrome address bar, Traditional Chinese UI) shows a standard QWERTY layout where the last column extends past the visible viewport. The issue happens because the keyboard's internal coordinates are computed against the **full display width**, but the IME's actual on-screen container can be narrower (display cutouts, gesture-nav insets, foldable hinge area, freeform/multi-window mode, etc.).

## Root Cause

In `app/src/main/java/org/limeime/keyboard/LIMEBaseKeyboard.java:769`:

```java
mDisplayWidth = dm.widthPixels;
```

`dm.widthPixels` is read from the **context's display metrics**, which on modern Android (especially with display cutouts, three-button vs gesture nav differences, or split/freeform windows) does **not** match the actual width the IME input view receives.

Each key's position is then computed cumulatively from this `mDisplayWidth`:

- `LIMEBaseKeyboard.java:777` — `mDefaultWidth = mDisplayWidth / DEFAULT_KEYBOARD_COLUMNS;`
- Percentage widths (`10%p`, `15%p`, etc.) in the XML are resolved against `mDisplayWidth`.
- `LIMEBaseKeyboard.java:1264` — `x += key.gap + key.width;`
- `LIMEBaseKeyboard.java:1273-1274` — `mTotalWidth` accumulates the rightmost edge.

Then in `LIMEKeyboardBaseView.java:870-883`, `onMeasure()` only **clamps the view's measured dimension** to the parent's width spec — it does **not** rescale the keys themselves:

```java
int width = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();
if (MeasureSpec.getSize(widthMeasureSpec) < width + DEFAULT_PREVIEW_TOP_PADDING_PX) {
    width = MeasureSpec.getSize(widthMeasureSpec);   // view shrinks
}
setMeasuredDimension(width, ...);                    // but keys stay at original x
```

So when `mDisplayWidth > actual IME container width`, the view shrinks to fit, but every key still draws at the originally computed (too-large) x coordinate. The rightmost column ends up drawn outside the view bounds and appears clipped.

A second contributor is **rounding accumulation**: percentage widths are rounded per key, so 10×`10%p` may sum to a few pixels more than `mDisplayWidth`. On a perfectly matched display this is invisible, but it stacks on top of the primary cause.

## Fix Strategy

Make the keyboard's reference width match the **actual IME container width**, not `dm.widthPixels`. There are two complementary changes:

### Change 1 — Use the real available width when constructing the keyboard

**File:** `app/src/main/java/org/limeime/keyboard/LIMEBaseKeyboard.java` (around lines 767–779)

Replace `dm.widthPixels` with a call that returns the actual usable width for the IME window. On Android 11+ this is `WindowManager.getCurrentWindowMetrics().getBounds().width()` minus the relevant insets (`systemBars() | displayCutout()`); on older versions, fall back to `dm.widthPixels`.

Add a small helper (private static) inside `LIMEBaseKeyboard`:

```java
private static int getUsableDisplayWidth(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            WindowMetrics metrics = wm.getCurrentWindowMetrics();
            Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            return metrics.getBounds().width() - insets.left - insets.right;
        }
    }
    return context.getResources().getDisplayMetrics().widthPixels;
}
```

Then:

```java
mDisplayWidth = getUsableDisplayWidth(context);
```

This eliminates the dominant overflow source — cutouts and gesture insets no longer cause the keyboard to be wider than the container.

### Change 2 — Rescale (or clamp) in `onMeasure` when the parent width is still smaller

**File:** `app/src/main/java/org/limeime/keyboard/LIMEKeyboardBaseView.java` (lines 869–884)

Even after Change 1, edge cases remain (rounding accumulation, multi-window resizing after construction, orientation change races). Detect the mismatch and rebuild the keyboard at the actual width, or apply a uniform horizontal scale to every key when drawing.

Minimal approach — rebuild the keyboard if the gap is non-trivial:

```java
@Override
public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    if (mKeyboard == null) {
        setMeasuredDimension(getPaddingLeft() + getPaddingRight(),
                             getPaddingTop() + getPaddingBottom());
        return;
    }
    int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
    int desired = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();

    if (parentWidth > 0 && desired > parentWidth) {
        // Keyboard wider than container -> rescale keys to fit.
        mKeyboard.resize(parentWidth - getPaddingLeft() - getPaddingRight(),
                         mKeyboard.getHeight());
        desired = parentWidth;
    }
    setMeasuredDimension(desired,
            mKeyboard.getHeight() + getPaddingTop() + getPaddingBottom());
}
```

`LIMEBaseKeyboard` already has a `resize(int newWidth, int newHeight)` path used for split-keyboard work — verify it correctly recomputes per-key `x` and `width`. If it does not currently scale all keys uniformly, add a small helper that walks `mKeys` and multiplies `key.x`, `key.width`, and `key.gap` by `newWidth / mTotalWidth`.

(If `resize()` is too invasive, the alternative is a draw-time `canvas.scale(parentWidth / mTotalWidth, 1f)` in `onDraw` of `LIMEKeyboardBaseView`. This avoids touching key data but adds float scaling to every draw and slightly fuzzes touch hit-testing — Change 1 + a real resize is preferred.)

## Critical Files

| File | Purpose |
|---|---|
| `app/src/main/java/org/limeime/keyboard/LIMEBaseKeyboard.java` | Source of `mDisplayWidth`; key width calculation. Modify constructor (line ~767–779) and verify `resize()`. |
| `app/src/main/java/org/limeime/keyboard/LIMEKeyboardBaseView.java` | `onMeasure()` (line 869–884) — clamp/rescale when parent width is smaller. |
| `app/src/main/java/org/limeime/LIMEKeyboardSwitcher.java` (line 264) | Where `LIMEKeyboard` is instantiated — confirm the context passed in is the IME service context (so `WindowManager` returns the IME window, not the underlying app's). |

No XML changes are required — percentage layouts stay correct once the reference width is right.

## Verification

1. **Build & install** the debug APK on the device that exhibited the bug (the one in the screenshot).
2. **Visual check, portrait & landscape:** open Chrome's address bar (the screenshot scenario) and confirm the rightmost column (`p`, `0`, backspace) is fully visible with no clipping.
3. **Display cutout devices:** test on a device with a notch / hole-punch in landscape — the keyboard must not extend under the cutout.
4. **Gesture nav vs 3-button nav:** toggle the system navigation mode in Settings → System → Gestures and re-open the keyboard. No clipping in either mode.
5. **Multi-window / split-screen:** launch the IME in split-screen and shrink the window. Keyboard should rescale, not clip.
6. **Logcat sanity:** the existing `Log.i(TAG, "Width = ...")` line in `onMeasure` should report a width equal to the IME container width, and `mTotalWidth` ≤ `mDisplayWidth`.
7. **Regression check:** type in several IMEs (Lime Chinese, English, phone-pad, symbol) to verify all layout XMLs render correctly at the new reference width.
