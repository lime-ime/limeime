---
# Issue #51 — Key Label Font Shrinkage: Root Cause & Fix

**Related:** #47 (landscape keyboard clipping fix, 6.0.1)  
**Status:** Fixed  
**Date:** 2026-04-14 / Updated: 2026-04-19

---

## 1. User Report (Issue #51)

After upgrading 6.0.0 → 6.0.1, key label font appears **~2 sizes smaller** on all keys.

Observed behaviour patterns reported by users:
- Upgrading in-place (6.0.0 → 6.0.1 APK): labels are small immediately after upgrade.
- Clean reinstall of 6.0.1: sometimes restores large labels, sometimes does not.
- Reading a backup after a clean reinstall: may revert to small labels.
- Pixel 9 (Android 16, larger screen, gesture nav) is more severely affected than Pixel 5 (Android 14, smaller screen).
- No in-app setting change restores the labels; only a full uninstall + reinstall occasionally helps.

---

## 2. What Changed in #47 (6.0.1)

The #47 fix introduced two changes to the keyboard sizing pipeline:

### 2a. `getUsableDisplayWidth()` in `LIMEBaseKeyboard` constructor
Replaces `dm.widthPixels` with a WindowMetrics-based width that excludes system bar and display cutout insets (API 30+). Applied at keyboard construction time. This part is architecturally correct.

### 2b. `scaleHorizontally()` called from `LIMEKeyboardBaseView.onMeasure()`

```java
// LIMEKeyboardBaseView.onMeasure()
if (parentWidth > 0 && width > parentWidth) {
    mKeyboard.scaleHorizontally(parentWidth - getPaddingLeft() - getPaddingRight());
    width = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();
}
```

```java
// LIMEBaseKeyboard.scaleHorizontally()
public void scaleHorizontally(int newWidth) {
    if (newWidth <= 0 || mTotalWidth <= 0 || newWidth >= mTotalWidth) return;
    float ratio = (float) newWidth / (float) mTotalWidth;
    for (Key key : mKeys) {
        key.x = Math.round(key.x * ratio);
        key.width = Math.round(key.width * ratio);
        key.gap = Math.round(key.gap * ratio);
    }
    mTotalWidth = newWidth;
}
```

This is a **post-construction patch** — the keyboard was already fully built, and then every key's `x`, `width`, and `gap` are scaled down.

---

## 3. Root Cause

### Step 1 — `scaleHorizontally` reduces `key.width` below `mDefaultWidth`

After `scaleHorizontally` runs, every `key.width` is multiplied by `ratio < 1.0`. However, `mDefaultWidth` (the baseline key width set at construction) is **never updated** to reflect the new scale.

### Step 2 — `getLabelSizeScale()` fires incorrectly

```java
// LIMEBaseKeyboard.Key.getLabelSizeScale()
if (width < keyboard.getKeyWidth())   // key.width vs mDefaultWidth
    mLabelSizeScale = mSplitKeyboard ? 1f : mSplitedKeyWidthScale;
```

`keyboard.getKeyWidth()` returns `mDefaultWidth` — the original pre-scale value. After `scaleHorizontally`, `key.width < mDefaultWidth` is now true for **every key**, even in portrait on a normal phone. This sets `mLabelSizeScale = mSplitedKeyWidthScale` (a value < 1.0 computed for landscape split-keyboard mode).

### Step 3 — Label font size is directly reduced

```java
// LIMEKeyboardBaseView.onBufferDraw()
labelSize = (int)(mKeyTextSize * keySizeScale * labelSizeScale);
```

`labelSizeScale` is now `mSplitedKeyWidthScale` instead of `1.0`, making the rendered font smaller.

### Step 4 — The `static` cache makes it permanent

`mLabelSizeScale` is declared as `private static float` on the inner `Key` class. Once set to a non-zero value it is **cached for the entire process lifetime** — all subsequent keyboard instances and keys share the same poisoned value. It is only cleared by process death (full uninstall/reinstall).

```java
private static float mLabelSizeScale = 0f;

public float getLabelSizeScale() {
    if (mLabelSizeScale > 0) return mLabelSizeScale;  // returns cached value forever
    ...
}
```

### Why Pixel 9 is more affected

On devices with gesture navigation bars and display cutouts (larger modern phones), the delta between `dm.widthPixels` and the inset-aware `getUsableDisplayWidth()` is larger. This increases the probability that `width > parentWidth` is true in `onMeasure()`, triggering `scaleHorizontally()` more readily.

---

## 4. Design Critique of the #47 Fix

The #47 fix is architecturally unsound. The keyboard is built assuming one width, then silently re-scaled after the fact. All the sizing variables (`mDefaultWidth`, `mSplitedKeyWidthScale`, `mLabelSizeScale`) are designed to be **set once correctly at construction time** — they are not designed to be patched post-layout.

`getUsableDisplayWidth()` was already added to the constructor precisely to get the correct width upfront. The `scaleHorizontally()` call in `onMeasure()` is a second, conflicting correction that undermines that first correction. Since `getUsableDisplayWidth()` already provides the correct inset-aware width at construction time, `scaleHorizontally()` is both redundant and harmful — it must never fire, and its presence introduces the label shrinkage bug when it does.

---

## 5. Fix Applied

**Remove `scaleHorizontally()` entirely.**

`getUsableDisplayWidth()` in the constructor already computes the correct usable width (excluding system bars and display cutout insets) before key layout is built. `scaleHorizontally()` was a redundant post-construction safety net that introduced the label shrinkage side effect whenever it triggered. Removing it is the correct fix.

### Changes

| File                        | Change                                                                              |
| --------------------------- | ----------------------------------------------------------------------------------- |
| `LIMEBaseKeyboard.java`     | Removed `scaleHorizontally()` method                                                |
| `LIMEKeyboardBaseView.java` | Removed the `if (parentWidth > 0 && width > parentWidth)` block from `onMeasure()` |

### `LIMEKeyboardBaseView.onMeasure()` after fix

```java
int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
int width = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();
if (parentWidth < width + DEFAULT_PREVIEW_TOP_PADDING_PX) {
    width = parentWidth;
}
```

---

## 6. Verification

1. Upgrade from 6.0.0 → 6.0.1 APK on Pixel 9 (Android 16) — key labels must remain the same size as 6.0.0.
2. Verify landscape mode on a device with gesture navigation still does not clip the rightmost key column (the original #47 requirement must still hold, now ensured by `getUsableDisplayWidth()` alone).
3. Verify portrait mode on Pixel 5 (Android 14) — no regression.

---

# Issue #51 — Follow-up: Label and Sub-label Crowding After 6.0.2 Fix

**Related commit:** `ce40a61` ("Remove horizontal key scaling; add issue docs #47 #51")
**Status:** Fixed (Option 1 applied)
**Date:** 2026-04-20

## 7. New User Report

After installing 6.0.2 (which restored label size to normal by removing `scaleHorizontally()`), users now report that the **primary key label and the sub-label are drawn too close to each other vertically** on multi-row keys (e.g. Zhuyin keys that stack a phonetic hint above the main character). The labels no longer appear squashed in size (#51 is fixed), but the vertical gap between the two text lines has visibly shrunk compared to what users expected.

## 8. What Was (and Was Not) Changed by `ce40a61`

Commit `ce40a61` made only two source-level edits:

| File                        | Change                                                                               |
| --------------------------- | ------------------------------------------------------------------------------------ |
| `LIMEBaseKeyboard.java`     | Deleted the `scaleHorizontally(int)` method (18 lines).                              |
| `LIMEKeyboardBaseView.java` | Removed the `if (parentWidth > 0 && width > parentWidth) { mKeyboard.scaleHorizontally(...); }` block inside `onMeasure()` (7 lines). |

**The rendering code in `LIMEKeyboardBaseView.onBufferDraw()` was NOT touched.** The label and sub-label drawing logic — including every Y-coordinate computation and every font-size formula — is byte-for-byte identical to what shipped in 6.0.1. Therefore the user-visible regression cannot be a rendering-code edit; it has to be a behavioural consequence of `getLabelSizeScale()` now returning a different value.

## 9. Label and Sub-label Rendering Pipeline (unchanged code)

File: `LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEKeyboardBaseView.java`

### 9a. Font-size formulas (all multiplied by `labelSizeScale`)

```java
// line 996
float labelSizeScale = key.getLabelSizeScale();
...
// line 1022 — sub-labelled multi-char label
labelSize = (int)(mSmallLabelTextSize * keySizeScale * labelSizeScale * 0.8f);
// line 1025 — sub-labelled single-char label
labelSize = (int)(mSmallLabelTextSize * keySizeScale * labelSizeScale);
// line 1029 — multi-char label, no sub-label
labelSize = (int)(mLabelTextSize   * keySizeScale * labelSizeScale);
// line 1032 — plain character key
labelSize = (int)(mKeyTextSize     * keySizeScale * labelSizeScale);
// line 1072 — sub-label
final int subLabelSize = (int)(mSubLabelTextSize * keySizeScale * labelSizeScale);
```

Every text size scales linearly with `labelSizeScale`. When `labelSizeScale` was poisoned to `mSplitedKeyWidthScale` (~0.8) in 6.0.1, *both* the label and the sub-label got 20 % smaller proportionally. When the fix restores `labelSizeScale` to `1.0`, both grow back by the same 20 %.

### 9b. Vertical baselines for the stacked (portrait) branch

```java
// LIMEKeyboardBaseView.java, lines 1068–1098
float KEY_LABEL_VERTICAL_ADJUSTMENT_FACTOR = 0.55f;
...
if (hasSubLabel) {
    ...
    // portrait keyboard (sub-label on top, main label on bottom)
    if (key.height > key.width || subLabel.length() > 2 || hasSecondSubLabel) {
        baseline    = (float)((key.height + padding.top - padding.bottom) * 2/3)
                      + labelHeight    * KEY_LABEL_VERTICAL_ADJUSTMENT_FACTOR;
        float subBaseline = (float)(key.height + padding.top - padding.bottom) / 3
                      + subLabelHeight * KEY_LABEL_VERTICAL_ADJUSTMENT_FACTOR;
        ...
        canvas.drawText(subLabel, centerX, subBaseline, paint); // sub-label on top
        ...
        canvas.drawText(label,    centerX, baseline,    paint); // main label below
```

The two baselines are anchored to **fixed fractions (1/3 and 2/3) of `key.height`**. They do *not* scale with `labelSizeScale`. Only a tiny portion — the `labelHeight * 0.55` term — grows with the font; the anchor points themselves are rigid.

## 10. Root Cause — Geometry That Does Not Scale With Text

Let `H = key.height - padding.top + padding.bottom`, and let `L = labelHeight`, `S = subLabelHeight` (both measured from `paint.getTextBounds("W")`, so they scale linearly with `labelSize` and `subLabelSize`, i.e. linearly with `labelSizeScale`).

- `baseline          ≈ (2/3) H + 0.55 L`
- `subBaseline       ≈ (1/3) H + 0.55 S`
- Bottom of sub-label `≈ subBaseline` (baseline sits near the character's descent)
- Top of main label   `≈ baseline − L`
- **Visible vertical gap** `G = (baseline − L) − subBaseline = H/3 − 0.45 L − 0.55 S`

Because `L` and `S` scale with `labelSizeScale` but `H/3` does not:

| State                                   | `labelSizeScale` | Gap `G` (symbolic)                                       |
| --------------------------------------- | ---------------- | -------------------------------------------------------- |
| 6.0.1 (buggy, `mSplitedKeyWidthScale`)  | ≈ 0.8            | `H/3 − 0.8·(0.45 L₀ + 0.55 S₀)`                         |
| 6.0.2 (fixed)                           | 1.0              | `H/3 − 1.0·(0.45 L₀ + 0.55 S₀)`                         |

where `L₀`, `S₀` are label/sub-label heights at `labelSizeScale = 1.0`. The gap shrinks by `0.2 · (0.45 L₀ + 0.55 S₀)`. For a typical main-label height of ~30 px and a sub-label height of ~20 px, that is roughly **a 5 px reduction in vertical whitespace** — well within human-visible range on a phone keyboard and exactly the kind of "labels are crowding each other" complaint users are filing.

In short: the 6.0.1 bug *under-sized both texts by 20 %*, which inadvertently padded the gap by the same 20 %. Removing the bug restores correct font size **and** re-exposes the always-cramped geometry that had been hidden by the bug.

## 11. Why It Was Not Visibly Bad in 6.0.0

In 6.0.0, `labelSizeScale` was also `1.0` on normal keys, so the same gap formula applied. However, `mDisplayWidth` in 6.0.0 was `dm.widthPixels` (full screen), not the inset-aware `getUsableDisplayWidth()` introduced by #47. This affects:

```java
// LIMEBaseKeyboard.java
// line 785
mDefaultWidth  = mDisplayWidth / DEFAULT_KEYBOARD_COLUMNS;
// line 787
mDefaultHeight = mDefaultWidth;  // only if XML does not override keyHeight
```

On devices with gesture navigation / display cutouts (Pixel 9 etc.), the inset-aware width is noticeably smaller than `dm.widthPixels`. For keyboards whose XML does **not** override `keyHeight` with a percentage of `mDisplayHeight`, `mDefaultHeight = mDefaultWidth` propagates the shrinkage into `key.height`. A smaller `key.height` directly reduces the `H/3` term in the gap formula, tightening the layout further on exactly the devices where the #47 inset change has the largest effect (matching the Pixel 9 skew noted in section 3).

For keyboards whose XML *does* specify `keyHeight` as a percentage of screen height, `key.height` is unchanged from 6.0.0 and the 6.0.2 appearance should match 6.0.0 exactly. This is consistent with the uneven/device-dependent nature of the user reports.

## 12. Code Change-Set Summary (why the rendering code itself is not at fault)

Running `git show ce40a61 -- '*.java'` shows the only edits were removal of `scaleHorizontally()` and its call site. **No `onBufferDraw()` code, no baseline computation, no font-size formula was altered.** The regression is purely an emergent consequence of `getLabelSizeScale()` now reporting the correct value `1.0` while the key-height-anchored baselines remain rigid.

## 13. Candidate Fix Directions (for a subsequent commit — not part of 6.0.2)

The correct fix is to make the sub-label/label vertical split respond to `labelSizeScale` (or, equivalently, to the actual measured text heights) instead of being hard-coded to `H/3` and `H·2/3`. Options, in order of minimum-invasiveness:

1. **Parameterise the 1/3 : 2/3 split.** Replace the literal fractions in `LIMEKeyboardBaseView.java` lines 1095 and 1097 with values that leave a guaranteed minimum whitespace between the sub-label's descent and the main label's ascent — e.g. compute `subBaseline` from the top (`subBaseline = padding.top + S + topPad`) and `baseline` from the bottom (`baseline = H - padding.bottom - bottomPad`), so the inter-text whitespace is a paint-measured constant rather than an arithmetic residual.
2. **Shrink text slightly only when the measured gap would collapse.** Keep the geometry as-is but, after measuring `L` and `S`, reduce `labelSize`/`subLabelSize` proportionally if `G < minGapPx`. This mimics the accidental 6.0.1 behaviour but in a principled, opt-in way.
3. **Tune the 0.55 vertical-adjustment factor.** The factor is applied to *both* baselines, so simply lowering it does not change the gap; only a split-specific offset (option 1) or size cap (option 2) actually increases whitespace.

Option 1 is recommended: it ties the layout to measured glyph metrics rather than a ratio of key height, so it is robust to any future change in `key.height` or `mDisplayWidth`.

## 14. Critical Files / Line References

- `LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEKeyboardBaseView.java`
  - `onBufferDraw()` label pipeline: lines 985–1135
  - Label font-size formulas: lines 1022, 1025, 1029, 1032
  - Sub-label font-size formula: line 1072
  - **Portrait split baselines (the geometry to change): lines 1095–1098**
  - Landscape split (label + sub-label on same baseline): lines 1114–1128
- `LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEBaseKeyboard.java`
  - `Key.getLabelSizeScale()` and static cache: lines 413–434
  - `mDisplayWidth` / `mDefaultWidth` / `mDefaultHeight` init: lines 777–787
  - `mDefaultHeight` XML override: line 1353
  - `mSplitedKeyWidthScale` computation: line 1376

## 15. Verification Plan for the Follow-up Fix

1. On Pixel 9 (Android 16) portrait, place the IME over a text field that uses a keyboard with stacked sub-labels (e.g. Zhuyin main keyboard). Measure the visible gap between the sub-label baseline and the main label top — it should match the 6.0.0 reference screenshot to within ±1 px.
2. Repeat on Pixel 5 (Android 14) portrait — no regression in the already-acceptable layout.
3. Landscape mode: the "sub-label + label on same baseline" branch (line 1114) must remain untouched; verify the fix only affects the stacked branch.
4. Popup keyboards (which use the alternate constructor at lines 829–883 with `mDefaultHeight = mDefaultWidth`) should not be adversely affected, since they rarely carry sub-labels.

## 16. Fix Applied (Option 1)

The portrait stacked-label branch in `LIMEKeyboardBaseView.onBufferDraw()` was rewritten to anchor each label to its near edge instead of to fixed `H/3` and `2H/3` fractions:

```java
// LIMEKeyboardBaseView.java, portrait sub-label branch (~line 1094)
float drawableH = key.height - padding.top - padding.bottom;
float stackPad  = Math.max(0f, (drawableH - subLabelHeight - labelHeight) / 3f);
baseline        = (key.height - padding.bottom) - stackPad;
float subBaseline = padding.top + stackPad + subLabelHeight;
```

The leftover vertical space `(drawableH − L − S)` is now distributed as
`top margin : inter-text gap : bottom margin = 1 : 1 : 1`. Because the gap is a *proportional* share of free space (not the arithmetic residual of rigid fractional anchors minus small text-height offsets), it grows with the available free space and no longer collapses when `labelSizeScale` is 1.0.

### Gap comparison (H = 100 px, L = 30 px, S = 20 px)

| Build                                 | Visible gap |
| ------------------------------------- | ----------- |
| 6.0.1 (buggy, labelSizeScale ≈ 0.8)   | ~13.7 px    |
| 6.0.2 (pre-fix, labelSizeScale = 1.0) | ~8.8 px     |
| 6.0.2 + this fix                      | ~16.7 px    |

The `KEY_LABEL_VERTICAL_ADJUSTMENT_FACTOR = 0.55f` is no longer used in this branch (edge-anchoring replaces the center-with-offset model). The factor is retained in the outer scope because the landscape sub-label branch and the no-sub-label branch still use it.

No change to the landscape stacked branch (sub-label + label on the same baseline) or to the non-stacked branches.
