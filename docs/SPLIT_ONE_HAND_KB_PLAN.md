# Split & One-Handed Keyboard Implementation Plan (goal-mode)

> **Historical plan:** Issue #169 supersedes every `oneHandAvailable` and phone-size-gate
> instruction below. `SPLIT_ONE_HAND_KB.md` is authoritative: every phone uses the integrated
> phone profile, while Android tablets at `smallestScreenWidthDp >= 600` and every iPad retain
> independent tablet split and numpad-anchor profiles.

> **For agentic workers:** This plan is written for a single autonomous **goal-mode** run that
> finishes ALL tasks and ALL test gates in one session, without user checkpoints. Use
> superpowers:executing-plans (inline) or superpowers:subagent-driven-development. Steps use
> checkbox (`- [ ]`) syntax for tracking. Every task ends with a runnable gate; a gate must PASS
> before moving to the next task. If the same gate fails 3 times, STOP guessing and research
> (external docs / repo history) before the next attempt.

**Goal:** Implement docs/SPLIT_ONE_HAND_KB.md on both platforms — reach-capped tablet split keys,
phone one-handed mode (`one_hand_mode`) with restore chevron, tablet numpad anchoring
(`numpad_anchor`) — with prefs in settings app + in-keyboard menu (iOS cold/hot §1.8), plus unit
tests, in one autonomous run.

**Architecture:** All new geometry is pure math in one file per platform
(`ReachGeometry.java` / `enum ReachGeometry` appended to `LayoutMetrics.swift`), unit-tested
headlessly. Split key width becomes reach-capped at its existing single choke point. One-hand and
numpad anchoring are a horizontal post-transform (Android: key post-pass; iOS: row insets) — no
layout XML/JSON changes. Pref wiring clones `split_keyboard_mode` end to end on both platforms.

**Tech Stack:** Android Java (LimeStudio, Gradle, plain JUnit), iOS Swift (LimeIME-iOS,
xcodebuild via `.claude/scripts/ios-gate.sh`, XCTest).

## Global Constraints

- Spec: `docs/SPLIT_ONE_HAND_KB.md`. Pref values: `one_hand_mode` 0=off/1=left/2=right;
  `numpad_anchor` 0=fit/1=left/2=right/3=center. Labels: 單手鍵盤 (關閉/靠左/靠右),
  數字鍵盤位置 (滿版/靠左/靠右/置中). The two names are deliberately different — never label the
  tablet numpad option 單手鍵盤.
- **Constant correction vs spec:** use `ONE_HAND_MAX_W_MM = 60` (not 63). The spec's 63 + 4 mm
  gate margin puts the gate at 67 mm, excluding the 6.1" phones (~65 mm) its own acceptance
  criteria require. 60 + 4 → gate 64 mm: 6.1" (390 pt ≈ 64.7 mm) passes, mini/SE (≤62 mm) fail.
  Task 12 writes this correction back into the spec.
- **Decisions locked:** (a) one-hand mode applies in PORTRAIT only (replicates iOS built-in
  one-handed keyboard; landscape renders full width, the setting persists). (b) Numpad-based
  layouts are exactly: Android `phone_simple.xml`, `computer_simple.xml`, `phone_number.xml`;
  iOS layout ids with prefix `phone_simple` or `computer_simple`. Per-IM `*_number*` layers are
  10-column full-width layouts → ordinary, NOT numpad-based. (c) No candidate-bar/emoji width
  changes; keyboard height untouched by anchoring.
- Geometry constants (all are calibration knobs; single definition per platform):
  `SPLIT_HALF_MAX_MM=66`, `SPLIT_KEY_MIN_MM=9`, `SPLIT_KEY_MAX_MM=13`, `SPLIT_ROW_MAX_MM=12`,
  `ONE_HAND_MAX_W_MM=60`, `ONE_HAND_GATE_MARGIN_MM=4`, `NUMPAD_KEY_MM=14`,
  `NUMPAD_ANCHOR_MAX_FRACTION=0.40`. mm→px Android: `mm * xdpi / 25.4`. mm→pt iOS:
  iPad `.small` 6.42, `.medium`/`.large` 5.20, iPhone 6.0 pt/mm.
- Encoding (user CLAUDE.md): `.java`, `.json`, `.gradle`, `project.pbxproj` = UTF-8 **without**
  BOM. `.xml`, `.swift`, `.md` you edit = UTF-8 **with** BOM (most repo Swift files already have
  one — do not strip it). Never re-save a file you didn't edit.
- Never run `xcodegen` (project.yml is stale) — edit `LimeIME-iOS/LimeIME.xcodeproj/project.pbxproj`
  by hand. Never use `git checkout/restore/reset` to revert; never blank-and-rewrite a source file.
- iOS builds/tests ONLY via `.claude/scripts/ios-gate.sh` (wraps `GIT_CONFIG_COUNT=0`; logs to
  `.claude/txt/`, prints PASS/FAIL). Android gate: `cd LimeStudio && ./gradlew :app:testDebugUnitTest`
  and `:app:assembleDebug`.
- Commit after each task. Message style `feat(android): …` / `feat(ios): …`. **No Claude/Anthropic
  co-author trailer.**
- Do not edit `LimeIME-iOS/Version.xcconfig`.

---

## Phase 1 — Android

### Task 1: ReachGeometry (pure math) + unit test

**Files:**
- Create: `LimeStudio/app/src/main/java/org/limeime/keyboard/ReachGeometry.java`
- Test: `LimeStudio/app/src/test/java/org/limeime/ReachGeometryTest.java`

**Interfaces:**
- Produces (used by Tasks 2, 4, 5):
  `ReachGeometry.mmToPx(float mm, float xdpi) -> int`,
  `splitKeyWidth(int displayWidthPx, int keysInRow, int reservedColumns, float xdpi) -> int`,
  `oneHandAvailable(int screenWidthPx, float xdpi) -> boolean`,
  `oneHandWidth(int displayWidthPx, float xdpi) -> int`,
  `numpadWidth(int displayWidthPx, int columns, float xdpi) -> int`,
  constants `SPLIT_ROW_MAX_MM` etc. as listed in Global Constraints.

- [ ] **Step 1: Write the failing test**

`LimeStudio/app/src/test/java/org/limeime/ReachGeometryTest.java` (plain JUnit, same style as
`ImeEdgeToEdgePolicyTest.java` in the same folder; package `org.limeime`):

```java
package org.limeime;

import static org.junit.Assert.*;

import org.junit.Test;
import org.limeime.keyboard.ReachGeometry;

public class ReachGeometryTest {

    // 13"-class tablet landscape: 2560 px @ 264 xdpi. Legacy width 2560/17 = 151 px;
    // reach cap clamps the key to 13 mm = 135 px.
    @Test
    public void splitKeyWidthCapsOnLargeTablet() {
        assertEquals(135, ReachGeometry.splitKeyWidth(2560, 10, 7, 264f));
    }

    // Phone landscape: 1080 px @ 420 xdpi — legacy (83 px ≈ 5 mm) is already narrower
    // than the reach band, so the cap must not change it.
    @Test
    public void splitKeyWidthUnchangedOnPhone() {
        assertEquals(Math.round(1080f / 13f), ReachGeometry.splitKeyWidth(1080, 10, 3, 420f));
    }

    // Defensive: xdpi <= 0 (bogus DisplayMetrics) falls back to the legacy formula.
    @Test
    public void splitKeyWidthFallsBackToLegacyWithoutDpi() {
        assertEquals(Math.round(2560f / 17f), ReachGeometry.splitKeyWidth(2560, 10, 7, 0f));
    }

    // 6.7"-class portrait: 1290 px @ 460 xdpi ≈ 71 mm wide → gate (64 mm) passes,
    // one-hand width = 60 mm.
    @Test
    public void oneHandGateAndWidth() {
        assertTrue(ReachGeometry.oneHandAvailable(1290, 460f));
        assertEquals(ReachGeometry.mmToPx(60f, 460f), ReachGeometry.oneHandWidth(1290, 460f));
    }

    // mini-class: 1080 px @ 440 xdpi ≈ 62.3 mm → below the 64 mm gate.
    @Test
    public void oneHandGateExcludesNarrowPhones() {
        assertFalse(ReachGeometry.oneHandAvailable(1080, 440f));
    }

    // Large tablet: 5 × 14 mm = 70 mm wins under the 40% cap; small tablet: the
    // 40% cap wins.
    @Test
    public void numpadWidthClamps() {
        assertEquals(ReachGeometry.mmToPx(70f, 264f), ReachGeometry.numpadWidth(2560, 5, 264f));
        assertEquals(Math.round(1488 * 0.40f), ReachGeometry.numpadWidth(1488, 5, 326f));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd LimeStudio && ./gradlew :app:testDebugUnitTest --tests "org.limeime.ReachGeometryTest"`
Expected: FAIL — `ReachGeometry` does not exist (compile error).

- [ ] **Step 3: Implement**

`LimeStudio/app/src/main/java/org/limeime/keyboard/ReachGeometry.java` (UTF-8 **no BOM**; pure
Java, no Android imports — that is what keeps it plain-JUnit testable):

```java
package org.limeime.keyboard;

/**
 * SPLIT_ONE_HAND_KB: pure reach-geometry math shared by the split keyboard,
 * one-handed mode and numpad anchoring (docs/SPLIT_ONE_HAND_KB.md).
 * Physical millimetres are converted with the display's xdpi. Every constant
 * is a calibration knob — tune after on-device trials, do not scatter copies.
 */
public final class ReachGeometry {
    private ReachGeometry() {}

    public static final float SPLIT_HALF_MAX_MM = 66f;
    public static final float SPLIT_KEY_MIN_MM = 9f;
    public static final float SPLIT_KEY_MAX_MM = 13f;
    public static final float SPLIT_ROW_MAX_MM = 12f;
    public static final float ONE_HAND_MAX_W_MM = 60f;
    public static final float ONE_HAND_GATE_MARGIN_MM = 4f;
    public static final float NUMPAD_KEY_MM = 14f;
    public static final float NUMPAD_ANCHOR_MAX_FRACTION = 0.40f;

    public static int mmToPx(float mm, float xdpi) {
        return Math.round(mm * xdpi / 25.4f);
    }

    /**
     * Reach-capped split key width. Never wider than the legacy
     * reserved-columns width, so small screens keep today's behavior exactly.
     */
    public static int splitKeyWidth(int displayWidthPx, int keysInRow, int reservedColumns, float xdpi) {
        int legacy = Math.round((float) displayWidthPx / (keysInRow + reservedColumns));
        if (xdpi <= 0 || keysInRow <= 0) return legacy;
        int columnsInHalf = (keysInRow + 1) / 2;
        int capped = mmToPx(SPLIT_HALF_MAX_MM, xdpi) / columnsInHalf;
        capped = Math.max(mmToPx(SPLIT_KEY_MIN_MM, xdpi),
                 Math.min(capped, mmToPx(SPLIT_KEY_MAX_MM, xdpi)));
        return Math.min(legacy, capped);
    }

    /** Gate: show/apply one-hand mode only when shrinking is meaningful (≈5.5"+). */
    public static boolean oneHandAvailable(int screenWidthPx, float xdpi) {
        return xdpi > 0
                && screenWidthPx > mmToPx(ONE_HAND_MAX_W_MM + ONE_HAND_GATE_MARGIN_MM, xdpi);
    }

    public static int oneHandWidth(int displayWidthPx, float xdpi) {
        return Math.min(displayWidthPx, mmToPx(ONE_HAND_MAX_W_MM, xdpi));
    }

    public static int numpadWidth(int displayWidthPx, int columns, float xdpi) {
        int byKeys = columns * mmToPx(NUMPAD_KEY_MM, xdpi);
        int cap = Math.round(displayWidthPx * NUMPAD_ANCHOR_MAX_FRACTION);
        return Math.min(byKeys, cap);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd LimeStudio && ./gradlew :app:testDebugUnitTest --tests "org.limeime.ReachGeometryTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add LimeStudio/app/src/main/java/org/limeime/keyboard/ReachGeometry.java LimeStudio/app/src/test/java/org/limeime/ReachGeometryTest.java
git commit -m "feat(android): add ReachGeometry reach-based sizing math with unit tests"
```

### Task 2: Android split fine-tune + numpad split exclusion

**Files:**
- Modify: `LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEBaseKeyboard.java`
  (ctors ~:756-811, `parseKeyboardAttributes` ~:1344-1383)
- Modify: `LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEKeyboard.java:91-92`
- Modify: `LimeStudio/app/src/main/java/org/limeime/LIMEKeyboardSwitcher.java` (`getKeyboard` ~:289-317)

**Interfaces:**
- Consumes: `ReachGeometry.splitKeyWidth`, `ReachGeometry.mmToPx`, `SPLIT_ROW_MAX_MM` (Task 1).
- Produces: `LIMEBaseKeyboard(Context, int, int, float, int, int, boolean splitEligible)` — the
  new trailing ctor param; `LIMEKeyboard` mirrors it. Tasks 4–5 rely on
  `LIMEKeyboardSwitcher.isNumpadXml(int)` (private) added here.

- [ ] **Step 1: Add `mXdpi` and `splitEligible` to LIMEBaseKeyboard**

In the main ctor at `LIMEBaseKeyboard.java:771`, change the signature to append
`boolean splitEligible`, store xdpi from the already-available `DisplayMetrics dm` (line 772),
and gate the split decision (lines 806-808):

```java
public LIMEBaseKeyboard(Context context, int xmlLayoutResId, int modeId, float keySizeScale,
        int showArrowKeys, int splitKeyboard, boolean splitEligible) {
    DisplayMetrics dm = context.getResources().getDisplayMetrics();
    ...
    mXdpi = dm.xdpi;   // add next to the mDisplayWidth assignment
    ...
    mSplitKeyboard = splitEligible
            && ((mLandScape && mShowArrowKeys != 0)
            || (mLandScape && splitKeyboard == SPLIT_KEYBOARD_LANDSCAPD_ONLY)
            || splitKeyboard == SPLIT_KEYBOARD_ALWAYS);
```

Add the field near the other private fields: `private float mXdpi;`.
Update the convenience ctor at :760 to pass `true`:
`this(context, xmlLayoutResId, 0, keySizeScale, showArrowKeys, splitKeyboard, true);`
Then `grep -n "new LIMEBaseKeyboard(\|super(context" LimeStudio/app/src/main/java -r` and fix every
caller of the 6-arg ctor: `LIMEKeyboard.java:91-92` gains the same trailing `boolean splitEligible`
param and passes it to `super(...)`. The characters-template ctor (~:828) is a different path —
leave it; `mXdpi` defaults to 0 there, which `splitKeyWidth` treats as "legacy formula".

- [ ] **Step 2: Reach-cap the split key width and split row height**

In `parseKeyboardAttributes` replace line :1374:

```java
mSplitKeyWidth = Math.round((float) mDisplayWidth / (mKeysInRow + mReservedColumnsForSplitedKeyboard));
```

with:

```java
mSplitKeyWidth = ReachGeometry.splitKeyWidth(mDisplayWidth, mKeysInRow,
        mReservedColumnsForSplitedKeyboard, mXdpi);
```

Immediately after the `mDefaultHeight = getDimensionOrFraction(...)` assignment (~:1352-1354) add
the split-mode vertical reach cap:

```java
// SPLIT_ONE_HAND_KB: cap row height to the vertical thumb sweep in split mode.
if (mSplitKeyboard && mXdpi > 0)
    mDefaultHeight = Math.min(mDefaultHeight,
            ReachGeometry.mmToPx(ReachGeometry.SPLIT_ROW_MAX_MM, mXdpi));
```

(`leftSplitBorder`/`splitDistance`/`mSplitedKeyWidthScale` all derive from `mSplitKeyWidth` —
no other split math changes.)

- [ ] **Step 3: Numpad split exclusion in the switcher**

In `LIMEKeyboardSwitcher.java`, add near the top of the class:

```java
// SPLIT_ONE_HAND_KB: the numpad-based layouts never split (they anchor instead).
private boolean isNumpadXml(int xml) {
    return xml == R.xml.phone_simple || xml == R.xml.computer_simple || xml == R.xml.phone_number;
}
```

In `getKeyboard(KeyboardId)` (~:301-305) change the creation to:

```java
boolean numpadXml = isNumpadXml(id.mXml);
LIMEKeyboard keyboard = new LIMEKeyboard(
        mThemedContext, id.mXml, id.mMode, mKeySizeScale,
        mLIMEPref.getShowArrowKeys(),
        numpadXml ? LIMEBaseKeyboard.SPLIT_KEYBOARD_NEVER : mLIMEPref.getSplitKeyboard(),
        !numpadXml);
```

- [ ] **Step 4: Gate**

Run: `cd LimeStudio && ./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, all unit tests PASS.

- [ ] **Step 5: Commit**

```bash
git add -A LimeStudio/app/src/main
git commit -m "feat(android): reach-capped split key width and numpad split exclusion"
```

### Task 3: Android prefs (`one_hand_mode`, `numpad_anchor`)

**Files:**
- Modify: `LimeStudio/app/src/main/java/org/limeime/global/LIMEPreferenceManager.java`
  (next to `getSplitKeyboard` :645, `isStartupConfigPreferenceKey` :683)
- Modify: `LimeStudio/app/src/main/java/org/limeime/global/PreferenceBackupAdapter.java:47`
- Modify: `LimeStudio/app/src/main/res/xml/preference.xml` (after the `split_keyboard_mode` entry)
- Modify: `LimeStudio/app/src/main/res/values/strings_settings.xml` (next to split labels :649)
- Modify: `LimeStudio/app/src/main/java/org/limeime/ui/LIMEPreference.java` (visibility, pattern :214-216)

**Interfaces:**
- Produces: `LIMEPreferenceManager.getOneHandMode()/setOneHandMode(int)`,
  `getNumpadAnchor()/setNumpadAnchor(int)` — used by Tasks 4 and 5.

- [ ] **Step 1: Pref accessors**

Copy the exact body pattern of `getSplitKeyboard()`/`setSplitKeyboard(int)` (:645-657, including
how they obtain the `SharedPreferences` instance) and add below them:

```java
public int getOneHandMode(){
    // same sp acquisition line as getSplitKeyboard()
    return Integer.parseInt(sp.getString("one_hand_mode", "0"));
}

public void setOneHandMode(int mode){
    // same sp acquisition line as setSplitKeyboard()
    putStringAndBumpStartupConfigVersionIfChanged(sp, "one_hand_mode", Integer.toString(mode));
}

public int getNumpadAnchor(){
    return Integer.parseInt(sp.getString("numpad_anchor", "0"));
}

public void setNumpadAnchor(int mode){
    putStringAndBumpStartupConfigVersionIfChanged(sp, "numpad_anchor", Integer.toString(mode));
}
```

In `isStartupConfigPreferenceKey` (:683) add two cases beside `"split_keyboard_mode"`:

```java
case "one_hand_mode":
case "numpad_anchor":
```

In `PreferenceBackupAdapter.java` beside line :47 add:

```java
add("one_hand_mode", Type.INTEGER_AS_STRING);
add("numpad_anchor", Type.INTEGER_AS_STRING);
```

- [ ] **Step 2: preference.xml + strings**

In `preference.xml`, directly after the `split_keyboard_mode` `<ListPreference>` (same category),
add (mirroring its attributes exactly):

```xml
<ListPreference
    android:defaultValue="0"
    android:dialogTitle="@string/one_hand_mode"
    android:entries="@array/one_hand_mode_options"
    android:entryValues="@array/one_hand_mode_values"
    android:key="one_hand_mode"
    app:useSimpleSummaryProvider="true"
    android:layout="@layout/preference_value_chevron"
    android:title="@string/one_hand_mode" />
<ListPreference
    android:defaultValue="0"
    android:dialogTitle="@string/numpad_anchor"
    android:entries="@array/numpad_anchor_options"
    android:entryValues="@array/numpad_anchor_values"
    android:key="numpad_anchor"
    app:useSimpleSummaryProvider="true"
    android:layout="@layout/preference_value_chevron"
    android:title="@string/numpad_anchor" />
```

In `strings_settings.xml`, next to the split labels (:649-650) and the
`split_keyboard_options` arrays (:687) add, following the same style the split arrays use:

```xml
<string name="one_hand_mode">單手鍵盤</string>
<string name="one_hand_off">關閉</string>
<string name="one_hand_left">靠左</string>
<string name="one_hand_right">靠右</string>
<string name="numpad_anchor">數字鍵盤位置</string>
<string name="numpad_anchor_fit">滿版</string>
<string name="numpad_anchor_left">靠左</string>
<string name="numpad_anchor_right">靠右</string>
<string name="numpad_anchor_center">置中</string>

<string-array name="one_hand_mode_options">
    <item>@string/one_hand_off</item>
    <item>@string/one_hand_left</item>
    <item>@string/one_hand_right</item>
</string-array>
<string-array name="one_hand_mode_values">
    <item>0</item><item>1</item><item>2</item>
</string-array>
<string-array name="numpad_anchor_options">
    <item>@string/numpad_anchor_fit</item>
    <item>@string/numpad_anchor_left</item>
    <item>@string/numpad_anchor_right</item>
    <item>@string/numpad_anchor_center</item>
</string-array>
<string-array name="numpad_anchor_values">
    <item>0</item><item>1</item><item>2</item><item>3</item>
</string-array>
```

- [ ] **Step 3: Device-gated visibility in the settings app**

In `LIMEPreference.java`, in the same method that hides `vibrate_level` (:214-216), add:

```java
// SPLIT_ONE_HAND_KB: 單手鍵盤 = gated phones only; 數字鍵盤位置 = tablets only.
android.util.DisplayMetrics rdm = getResources().getDisplayMetrics();
boolean isTablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
androidx.preference.Preference oneHandPref = findPreference("one_hand_mode");
if (oneHandPref != null)
    oneHandPref.setVisible(!isTablet && org.limeime.keyboard.ReachGeometry.oneHandAvailable(
            Math.min(rdm.widthPixels, rdm.heightPixels), rdm.xdpi));
androidx.preference.Preference numpadAnchorPref = findPreference("numpad_anchor");
if (numpadAnchorPref != null)
    numpadAnchorPref.setVisible(isTablet);
```

- [ ] **Step 4: Gate + commit**

Run: `cd LimeStudio && ./gradlew :app:testDebugUnitTest :app:assembleDebug` — expected PASS.

```bash
git add -A LimeStudio/app/src/main
git commit -m "feat(android): one_hand_mode and numpad_anchor preferences"
```

### Task 4: Android anchoring engine + restore chevron

**Files:**
- Modify: `LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEBaseKeyboard.java`
  (constants :113-115 area; new method after `createArrowKeys` ~:1050)
- Modify: `LimeStudio/app/src/main/java/org/limeime/LIMEKeyboardSwitcher.java`
  (`getKeyboard` from Task 2; current-XML tracking near `mInputView.setKeyboard` :566)
- Modify: `LimeStudio/app/src/main/java/org/limeime/LIMEService.java` (keycode routing :2276)

**Interfaces:**
- Consumes: `ReachGeometry` (Task 1); `getOneHandMode()/getNumpadAnchor()` (Task 3);
  `isNumpadXml` (Task 2).
- Produces: `LIMEBaseKeyboard.KEYCODE_ONE_HAND_RESTORE = -120`,
  `LIMEBaseKeyboard.ANCHOR_LEFT/RIGHT/CENTER = 1/2/3`,
  `LIMEBaseKeyboard.applyHorizontalAnchor(int targetWidth, int anchor, boolean withRestoreChevron)`,
  `LIMEBaseKeyboard.getDisplayWidth()`, `LIMEKeyboardSwitcher.isNumpadKeyboard()` — used by Task 5.

- [ ] **Step 1: Keycode + anchor constants**

First `grep -rn "\-120" LimeStudio/app/src/main/java --include="*.java"` to confirm -120 is an
unused keycode (if taken, use the next free negative below every existing `KEYCODE_*`). Then in
`LIMEBaseKeyboard.java` below the `SPLIT_KEYBOARD_*` constants (:113-115) add:

```java
// SPLIT_ONE_HAND_KB
public static final int KEYCODE_ONE_HAND_RESTORE = -120;
public static final int ANCHOR_LEFT = 1;
public static final int ANCHOR_RIGHT = 2;
public static final int ANCHOR_CENTER = 3;
```

- [ ] **Step 2: applyHorizontalAnchor post-pass**

Add to `LIMEBaseKeyboard.java` (after `createArrowKeys`), plus a width getter:

```java
public int getDisplayWidth() {
    return mDisplayWidth;
}

/**
 * SPLIT_ONE_HAND_KB: shrink every key horizontally to targetWidth and pin the
 * block to an edge (or center). Pure post-pass over the loaded keys — vertical
 * geometry and keyboard height are untouched. With withRestoreChevron, the
 * vacated strip becomes a single restore key whose arrow points toward the
 * empty side (iOS built-in one-handed style).
 */
public void applyHorizontalAnchor(int targetWidth, int anchor, boolean withRestoreChevron) {
    if (targetWidth <= 0 || targetWidth >= mDisplayWidth) return;
    final float s = (float) targetWidth / (float) mDisplayWidth;
    final int free = mDisplayWidth - targetWidth;
    final int offset = (anchor == ANCHOR_RIGHT) ? free
                     : (anchor == ANCHOR_CENTER) ? free / 2 : 0;
    for (Key k : mKeys) {
        k.x = Math.round(k.x * s) + offset;
        k.width = Math.round(k.width * s);
        k.gap = Math.round(k.gap * s);
    }
    mTotalWidth = mDisplayWidth;   // full-width canvas; the strip stays background
    if (withRestoreChevron && anchor != ANCHOR_CENTER) {
        Row row = new Row(this);
        Key chevron = new Key(row);
        chevron.x = (anchor == ANCHOR_RIGHT) ? 0 : targetWidth;
        chevron.y = 0;
        chevron.width = free;
        chevron.height = mTotalHeight;
        chevron.gap = 0;
        chevron.modifier = true;
        chevron.codes = new int[]{KEYCODE_ONE_HAND_RESTORE};
        chevron.icon = (anchor == ANCHOR_RIGHT) ? mDrawableArrowLeft : mDrawableArrowRight;
        mKeys.add(chevron);
    }
}
```

- [ ] **Step 3: Apply from the switcher + track current XML**

In `LIMEKeyboardSwitcher.getKeyboard`, after the `new LIMEKeyboard(...)` block from Task 2 and
before `mKeyboards.put(id, keyboard);`:

```java
// SPLIT_ONE_HAND_KB: horizontal anchoring. One-hand = gated phones, portrait
// only (iOS built-in parity); numpad anchoring = tablets, numpad layouts only.
DisplayMetrics dm = mThemedContext.getResources().getDisplayMetrics();
boolean isTablet = mThemedContext.getResources().getConfiguration().smallestScreenWidthDp >= 600;
boolean portrait = dm.widthPixels < dm.heightPixels;
if (isTablet && numpadXml) {
    int anchor = mLIMEPref.getNumpadAnchor();
    if (anchor != 0)
        keyboard.applyHorizontalAnchor(
                ReachGeometry.numpadWidth(keyboard.getDisplayWidth(), 5, dm.xdpi),
                anchor, false);
} else if (!isTablet && portrait
        // spec precedence: an active split wins over one-hand. Portrait split
        // only happens with SPLIT_KEYBOARD_ALWAYS (arrow-split is landscape-only).
        && mLIMEPref.getSplitKeyboard() != LIMEBaseKeyboard.SPLIT_KEYBOARD_ALWAYS) {
    int mode = mLIMEPref.getOneHandMode();
    if (mode != 0 && ReachGeometry.oneHandAvailable(dm.widthPixels, dm.xdpi))
        keyboard.applyHorizontalAnchor(
                ReachGeometry.oneHandWidth(keyboard.getDisplayWidth(), dm.xdpi),
                mode == 1 ? LIMEBaseKeyboard.ANCHOR_LEFT : LIMEBaseKeyboard.ANCHOR_RIGHT,
                true);
}
```

Add the imports the file is missing (`android.util.DisplayMetrics`,
`org.limeime.keyboard.ReachGeometry` — check existing imports first).

Current-XML tracking: add field `private int mCurrentXmlId;` near the commented-out
`//private KeyboardId mCurrentId;` (:99). Then `grep -n "mInputView.setKeyboard(" LIMEKeyboardSwitcher.java`
and before EACH call that sets a newly resolved keyboard (the `:566` site where `kid` is in scope,
and the feat#124 `phone_simple` path ~:670), set `mCurrentXmlId = kid.mXml;`. Add:

```java
public boolean isNumpadKeyboard() {
    return isNumpadXml(mCurrentXmlId);
}
```

- [ ] **Step 4: Route the restore keycode in LIMEService**

In `LIMEService.java`, in the keycode dispatch beside `KEYCODE_OPTIONS` (:2276-2277), add:

```java
} else if (primaryCode == LIMEBaseKeyboard.KEYCODE_ONE_HAND_RESTORE) {
    // SPLIT_ONE_HAND_KB: chevron tap restores full width, persisted (spec).
    mLIMEPref.setOneHandMode(0);
    handleClose();
    mKeyboardSwitcher.resetKeyboards(true);
```

- [ ] **Step 5: Gate + commit**

Run: `cd LimeStudio && ./gradlew :app:testDebugUnitTest :app:assembleDebug` — expected PASS.

```bash
git add -A LimeStudio/app/src/main
git commit -m "feat(android): one-hand/numpad horizontal anchoring with restore chevron"
```

### Task 5: Android in-keyboard menu (segmented rows + exclusivity)

**Files:**
- Modify: `LimeStudio/app/src/main/res/layout/keyboard_menu_panel.xml` (clone `menu_split_block` :139-220)
- Modify: `LimeStudio/app/src/main/java/org/limeime/LIMEService.java` (`handleOptions` :3646-3790)

**Interfaces:**
- Consumes: `isNumpadKeyboard()` (Task 4), pref accessors (Task 3), strings (Task 3).

- [ ] **Step 1: Menu panel blocks**

In `keyboard_menu_panel.xml`, duplicate the entire `menu_split_block` container (:139 through its
closing tag) twice, directly after it, adapting ids/labels (keep every style/layout attribute
identical to the split block; reuse `@drawable/ic_split_24` for both new blocks —
`<!-- ponytail: reuse split icon; swap for dedicated icons if ever drawn -->`):

- Block 2: container id `menu_onehand_block`, title `@string/one_hand_mode`, toggle group id
  `onehand_toggle_group`, three buttons `onehand_opt_off` / `onehand_opt_left` /
  `onehand_opt_right` with texts `@string/one_hand_off` / `@string/one_hand_left` /
  `@string/one_hand_right`.
- Block 3: container id `menu_numpad_anchor_block`, title `@string/numpad_anchor`, toggle group id
  `numpad_anchor_toggle_group`, four buttons `numpad_anchor_opt_fit` / `numpad_anchor_opt_left` /
  `numpad_anchor_opt_right` / `numpad_anchor_opt_center` with texts
  `@string/numpad_anchor_fit` / `@string/numpad_anchor_left` / `@string/numpad_anchor_right` /
  `@string/numpad_anchor_center`.

Both containers default to `android:visibility="gone"` exactly like `menu_split_block`.

- [ ] **Step 2: handleOptions wiring**

In `LIMEService.handleOptions()`:

Replace the `hasSplitOption` line (:3679) and add the sibling gates:

```java
// SPLIT_ONE_HAND_KB exclusivity: numpad layouts show 數字鍵盤位置 only;
// ordinary layouts show 分離鍵盤 / 單手鍵盤. (spec: keyboard-menu rule)
final boolean isNumpadKb = mKeyboardSwitcher.isNumpadKeyboard();
final boolean isTablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
final boolean hasSplitOption = !isNumpadKb && !(isLandScape && mShowArrowKeys > 0);
final boolean hasOneHandOption = !isTablet
        && org.limeime.keyboard.ReachGeometry.oneHandAvailable(
                Math.min(displayWidth, displayHeight), dm.xdpi);
final boolean hasNumpadAnchorOption = isTablet && isNumpadKb;
```

After the split segmented block (:3733-3750), add the two new blocks with the identical
pending-array pattern:

```java
final int oneHandCurrent = clampIndex(mLIMEPref.getOneHandMode(), 3);
final int[] pendingOneHand = { oneHandCurrent };
if (hasOneHandOption) {
    panel.findViewById(R.id.menu_onehand_block).setVisibility(android.view.View.VISIBLE);
    com.google.android.material.button.MaterialButtonToggleGroup oneHandGroup =
            panel.findViewById(R.id.onehand_toggle_group);
    final int[] oneHandIds = { R.id.onehand_opt_off, R.id.onehand_opt_left, R.id.onehand_opt_right };
    oneHandGroup.check(oneHandIds[oneHandCurrent]);
    oneHandGroup.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
        if (!isChecked) return;
        for (int i = 0; i < oneHandIds.length; i++) {
            if (oneHandIds[i] == checkedId) { pendingOneHand[0] = i; break; }
        }
    });
    org.limeime.ui.view.SegmentedHanPreference.stackIfClipped(oneHandGroup);
}

final int anchorCurrent = clampIndex(mLIMEPref.getNumpadAnchor(), 4);
final int[] pendingAnchor = { anchorCurrent };
if (hasNumpadAnchorOption) {
    panel.findViewById(R.id.menu_numpad_anchor_block).setVisibility(android.view.View.VISIBLE);
    com.google.android.material.button.MaterialButtonToggleGroup anchorGroup =
            panel.findViewById(R.id.numpad_anchor_toggle_group);
    final int[] anchorIds = { R.id.numpad_anchor_opt_fit, R.id.numpad_anchor_opt_left,
            R.id.numpad_anchor_opt_right, R.id.numpad_anchor_opt_center };
    anchorGroup.check(anchorIds[anchorCurrent]);
    anchorGroup.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
        if (!isChecked) return;
        for (int i = 0; i < anchorIds.length; i++) {
            if (anchorIds[i] == checkedId) { pendingAnchor[0] = i; break; }
        }
    });
    org.limeime.ui.view.SegmentedHanPreference.stackIfClipped(anchorGroup);
}
```

Extend the dismiss listener (:3753-3763) so the keyboard rebuilds once for any geometry change:

```java
mOptionsDialog.setOnDismissListener(d -> {
    if (pendingHan[0] != hanCurrent) {
        handleHanConvertSelection(pendingHan[0]);
    }
    boolean geometryChanged = false;
    if (pendingSplit[0] != splitCurrent) {
        mLIMEPref.setSplitKeyboard(pendingSplit[0]);
        geometryChanged = true;
    }
    if (pendingOneHand[0] != oneHandCurrent) {
        mLIMEPref.setOneHandMode(pendingOneHand[0]);
        geometryChanged = true;
    }
    if (pendingAnchor[0] != anchorCurrent) {
        mLIMEPref.setNumpadAnchor(pendingAnchor[0]);
        geometryChanged = true;
    }
    if (geometryChanged) {
        invalidateStartupConfigSnapshot();
        handleClose();
        mKeyboardSwitcher.resetKeyboards(true);
    }
});
```

- [ ] **Step 3: Gate + commit**

Run: `cd LimeStudio && ./gradlew :app:testDebugUnitTest :app:assembleDebug` — expected PASS.

```bash
git add -A LimeStudio/app/src/main
git commit -m "feat(android): keyboard menu rows for one-hand and numpad anchor with layout exclusivity"
```

---

## Phase 2 — iOS

### Task 6: ReachGeometry in LayoutMetrics + unit test (with pbxproj registration)

**Files:**
- Modify: `LimeIME-iOS/LimeKeyboard/LayoutMetrics.swift` (append at end of file)
- Create: `LimeIME-iOS/LimeTests/ReachGeometryTests.swift`
- Modify: `LimeIME-iOS/LimeIME.xcodeproj/project.pbxproj` (register the test file)

**Interfaces:**
- Produces (used by Tasks 7, 8, 9):
  `ReachGeometry.ptPerMM(isPad: Bool, sizeClass: IPadSizeClass) -> CGFloat`,
  `splitHalfMaxFraction(viewWidth: CGFloat, sizeClass: IPadSizeClass) -> CGFloat`,
  `splitRowHeightCap(sizeClass: IPadSizeClass) -> CGFloat`,
  `oneHandAvailable(screenWidthPt: CGFloat) -> Bool`,
  `oneHandWidth(viewWidth: CGFloat) -> CGFloat`,
  `numpadAnchorWidth(viewWidth: CGFloat, columns: Int, sizeClass: IPadSizeClass) -> CGFloat`.
- Note: `LayoutMetrics.swift` is already compiled into BOTH `LimeKeyboard` and `LimeTests`
  targets (pbxproj lines ~139 and ~256) — only the NEW test file needs registration.

- [ ] **Step 1: Write the failing test**

`LimeIME-iOS/LimeTests/ReachGeometryTests.swift` (UTF-8 with BOM, GPL header comment copied from
`KeyDetectorTests.swift`; no `@testable import` — sources compile into the test target directly):

```swift
import XCTest
import CoreGraphics

final class ReachGeometryTests: XCTestCase {

    // 13" iPad landscape (1366 pt, .large): half cap = 66 mm × 5.20 = 343.2 pt,
    // fraction ≈ 0.251 — far below the legacy (1 − 0.06)/2 = 0.47.
    func testSplitHalfFractionCapsOnLargeIPad() {
        let f = ReachGeometry.splitHalfMaxFraction(viewWidth: 1366, sizeClass: .large)
        XCTAssertEqual(f, 66 * 5.20 / 1366, accuracy: 0.001)
        XCTAssertLessThan(f, 0.47)
    }

    // iPad mini portrait (744 pt, .small): cap 66 × 6.42 = 423.7 pt exceeds the
    // legacy half — legacy behavior must be preserved.
    func testSplitHalfFractionKeepsLegacyOnSmallIPad() {
        let f = ReachGeometry.splitHalfMaxFraction(viewWidth: 744, sizeClass: .small)
        XCTAssertEqual(f, (1 - LayoutMetrics.KeyboardRow.splitGapFraction) / 2, accuracy: 0.001)
    }

    // Gate = 64 mm × 6.0 = 384 pt: Pro-Max (430) and 6.1" (390) pass, mini/SE (375) fail.
    func testOneHandGate() {
        XCTAssertTrue(ReachGeometry.oneHandAvailable(screenWidthPt: 430))
        XCTAssertTrue(ReachGeometry.oneHandAvailable(screenWidthPt: 390))
        XCTAssertFalse(ReachGeometry.oneHandAvailable(screenWidthPt: 375))
    }

    func testOneHandWidth() {
        XCTAssertEqual(ReachGeometry.oneHandWidth(viewWidth: 430), 60 * 6.0, accuracy: 0.001)
        XCTAssertEqual(ReachGeometry.oneHandWidth(viewWidth: 300), 300, accuracy: 0.001)
    }

    // 11" landscape (1194 pt, .medium): 5 × 14 mm × 5.20 = 364 pt < 40% cap (477.6);
    // mini portrait (744 pt, .small): the 40% cap (297.6) wins.
    func testNumpadAnchorWidth() {
        XCTAssertEqual(ReachGeometry.numpadAnchorWidth(viewWidth: 1194, columns: 5, sizeClass: .medium),
                       5 * 14 * 5.20, accuracy: 0.001)
        XCTAssertEqual(ReachGeometry.numpadAnchorWidth(viewWidth: 744, columns: 5, sizeClass: .small),
                       744 * 0.40, accuracy: 0.001)
    }
}
```

- [ ] **Step 2: Register the test file in project.pbxproj**

Mimic the four `SyncContractTest.swift` entries exactly (pbxproj lines ~89 PBXBuildFile,
~392 PBXFileReference, ~609 LimeTests group child, ~1321 LimeTests Sources phase). Generate two
unused 24-hex IDs (e.g. `AB12CD34EF56AB12CD34EF01` / `AB12CD34EF56AB12CD34EF02` — first
`grep` the pbxproj to confirm they are absent), then add:

```text
AB12CD34EF56AB12CD34EF02 /* ReachGeometryTests.swift in Sources */ = {isa = PBXBuildFile; fileRef = AB12CD34EF56AB12CD34EF01 /* ReachGeometryTests.swift */; };
AB12CD34EF56AB12CD34EF01 /* ReachGeometryTests.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = ReachGeometryTests.swift; sourceTree = "<group>"; };
```

plus one line each in the LimeTests group's `children` list and the LimeTests target's
`PBXSourcesBuildPhase.files` list, next to the SyncContractTest lines. Keep pbxproj UTF-8 no BOM.

- [ ] **Step 3: Run test to verify it fails**

Run: `.claude/scripts/ios-gate.sh unit LimeTests/ReachGeometryTests`
Expected: FAIL — `ReachGeometry` unresolved (compile error). (If the failure is instead
"test class not found", the pbxproj registration is wrong — fix Step 2 first.)

- [ ] **Step 4: Implement**

Append to `LimeIME-iOS/LimeKeyboard/LayoutMetrics.swift` (top level, after the existing types):

```swift
/// SPLIT_ONE_HAND_KB: pure reach-geometry math (docs/SPLIT_ONE_HAND_KB.md).
/// mm → pt uses a per-device-class table; every constant is a calibration knob.
enum ReachGeometry {
    static let splitHalfMaxMM: CGFloat = 66
    static let splitRowMaxMM: CGFloat = 12
    static let oneHandMaxWidthMM: CGFloat = 60
    static let oneHandGateMarginMM: CGFloat = 4
    static let numpadKeyMM: CGFloat = 14
    static let numpadAnchorMaxFraction: CGFloat = 0.40

    /// iPad mini class = 163 pt/in, regular iPads = 132 pt/in, iPhone ≈ 153 pt/in.
    static func ptPerMM(isPad: Bool, sizeClass: IPadSizeClass) -> CGFloat {
        guard isPad else { return 6.0 }
        return sizeClass == .small ? 6.42 : 5.20
    }

    /// Per-half width fraction for split mode: the legacy (1 − gap)/2 half,
    /// capped by the two-hand thumb reach. Small iPads keep legacy behavior.
    static func splitHalfMaxFraction(viewWidth: CGFloat, sizeClass: IPadSizeClass) -> CGFloat {
        let legacy = (1 - LayoutMetrics.KeyboardRow.splitGapFraction) / 2
        guard viewWidth > 0 else { return legacy }
        let capPt = splitHalfMaxMM * ptPerMM(isPad: true, sizeClass: sizeClass)
        return min(legacy, capPt / viewWidth)
    }

    /// Vertical thumb-sweep cap on split-mode row height.
    static func splitRowHeightCap(sizeClass: IPadSizeClass) -> CGFloat {
        splitRowMaxMM * ptPerMM(isPad: true, sizeClass: sizeClass)
    }

    /// Gate: one-hand mode only where shrinking is meaningful (≈5.5"+ phones).
    static func oneHandAvailable(screenWidthPt: CGFloat) -> Bool {
        screenWidthPt > (oneHandMaxWidthMM + oneHandGateMarginMM) * ptPerMM(isPad: false, sizeClass: .small)
    }

    static func oneHandWidth(viewWidth: CGFloat) -> CGFloat {
        min(viewWidth, oneHandMaxWidthMM * ptPerMM(isPad: false, sizeClass: .small))
    }

    static func numpadAnchorWidth(viewWidth: CGFloat, columns: Int, sizeClass: IPadSizeClass) -> CGFloat {
        let byKeys = CGFloat(columns) * numpadKeyMM * ptPerMM(isPad: true, sizeClass: sizeClass)
        return min(byKeys, viewWidth * numpadAnchorMaxFraction)
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `.claude/scripts/ios-gate.sh unit LimeTests/ReachGeometryTests`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add LimeIME-iOS/LimeKeyboard/LayoutMetrics.swift LimeIME-iOS/LimeTests/ReachGeometryTests.swift LimeIME-iOS/LimeIME.xcodeproj/project.pbxproj
git commit -m "feat(ios): ReachGeometry reach-based sizing math with unit tests"
```

### Task 7: iOS split fine-tune (reach-capped halves + row height)

**Files:**
- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardView.swift` (`makeSplitRow` :776-843 and its call site)

**Interfaces:**
- Consumes: `ReachGeometry.splitHalfMaxFraction`, `splitRowHeightCap` (Task 6).

- [ ] **Step 1: Cap the half fraction in makeSplitRow**

In `makeSplitRow` replace the fraction computation (:797 and :802). Old:

```swift
let splitGapFraction = LayoutMetrics.KeyboardRow.splitGapFraction
...
let halfFraction = (halfPercent / total) * (1 - splitGapFraction)
```

New (the shrink ratio scales unequal halves proportionally; equal halves land exactly on the cap):

```swift
let splitGapFraction = LayoutMetrics.KeyboardRow.splitGapFraction
let viewWidth = bounds.width > 0 ? bounds.width : UIScreen.main.bounds.width
let legacyHalf = (1 - splitGapFraction) / 2
let capHalf = ReachGeometry.splitHalfMaxFraction(viewWidth: viewWidth,
                                                 sizeClass: LayoutLoader.iPadSizeClass)
let reachShrink = capHalf / legacyHalf   // ≤ 1; == 1 when the cap doesn't bind
...
let halfFraction = (halfPercent / total) * (1 - splitGapFraction) * reachShrink
```

- [ ] **Step 2: Cap split row height**

`grep -n "makeSplitRow(" LimeIME-iOS/LimeKeyboard/KeyboardView.swift` to find the call site in the
row-building loop. At that site the row height value (the variable passed as `rowHeight:` and used
for the row's height constraint) must be capped when `splitMode` is on, before both uses:

```swift
// SPLIT_ONE_HAND_KB: vertical thumb-sweep cap in split mode.
if splitMode {
    rowH = min(rowH, ReachGeometry.splitRowHeightCap(sizeClass: LayoutLoader.iPadSizeClass))
}
```

(`rowH` = whatever the local row-height variable is named there; apply to the same variable that
feeds the height constraint so the row container and buttons shrink together. Height flows
through the existing `applyHeight()` choke point — do NOT add any new height writer;
see docs/IOS_KB_HEIGHT.md.)

- [ ] **Step 3: Gate + commit**

Run: `.claude/scripts/ios-gate.sh build` — expected PASS.
Run: `.claude/scripts/ios-gate.sh unit LimeTests/KeyboardViewControllerTest` — expected PASS
(catches height-policy regressions).

```bash
git add LimeIME-iOS/LimeKeyboard/KeyboardView.swift
git commit -m "feat(ios): reach-capped split keyboard halves and split row height"
```

### Task 8: iOS pref plumbing (cold/hot §1.8 clone) + tests

**Files:**
- Modify: `LimeIME-iOS/Shared/Preferences/LIMEPreferenceManager.swift` (below `splitKeyboardMode` :126-129)
- Modify: `LimeIME-iOS/Shared/Database/SyncContract.swift` (`RelayPrefState` :120, `update` :149,
  `PrefInboxRecord` :167, `PrefInbox.write` :188, payload builder :287 and its parser)
- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift` (props :134 area,
  `loadSettings` :1260 area, `drainPrefInbox` :1164-1198)
- Test: `LimeIME-iOS/LimeTests/LIMEPreferenceManagerTest.swift`, `LimeIME-iOS/LimeTests/SyncContractTest.swift`

**Interfaces:**
- Produces: `LIMEPreferenceManager.oneHandMode: Int` / `numpadAnchor: Int`;
  `PrefInboxRecord.oneHand/numpadAnchor: Int?`; `PrefInbox.write(... oneHand: Int? = nil,
  numpadAnchor: Int? = nil ...)`; `RelayPrefState.oneHand/numpadAnchor: Int? = nil` and
  `update(hanConvert:splitKeyboard:oneHand:numpadAnchor:reverseLookupIM:reverseLookupValue:)`;
  KVC vars `oneHandMode: Int` / `numpadAnchor: Int`. Used by Tasks 9, 10.

- [ ] **Step 1: Write the failing tests**

In `LIMEPreferenceManagerTest.swift` add (mirroring how existing tests construct the manager):

```swift
func testOneHandAndNumpadAnchorAccessors() {
    prefs.oneHandMode = 2
    XCTAssertEqual(prefs.oneHandMode, 2)
    prefs.numpadAnchor = 3
    XCTAssertEqual(prefs.numpadAnchor, 3)
    XCTAssertEqual(LIMEPreferenceManager(defaults: freshDefaults()).oneHandMode, 0)  // default off
}
```

(`prefs` / `freshDefaults()` = this test class's existing fixture names — read the file first and
use its actual fixture; the assertions are what matters.)

In `SyncContractTest.swift` add, using the same `base`/`defaults` setup as the test at :57:

```swift
func testPrefInboxCarriesOneHandAndNumpadAnchor() throws {
    try PrefInbox.write(base: base, defaults: defaults, oneHand: 2)          // seq 1
    try PrefInbox.write(base: base, defaults: defaults, numpadAnchor: 3)     // seq 2, oneHand carries
    let rec = try XCTUnwrap(PrefInbox.read(base: base))
    XCTAssertEqual(rec.oneHand, 2)
    XCTAssertEqual(rec.numpadAnchor, 3)
    XCTAssertEqual(rec.seq, 2)
}
```

- [ ] **Step 2: Run to verify both fail**

Run: `.claude/scripts/ios-gate.sh unit LimeTests/LIMEPreferenceManagerTest` → FAIL (no such member).
Run: `.claude/scripts/ios-gate.sh unit LimeTests/SyncContractTest` → FAIL (no such parameter).

- [ ] **Step 3: Implement**

`LIMEPreferenceManager.swift`, below `splitKeyboardMode` (:129):

```swift
var oneHandMode: Int {
    get { intValue("one_hand_mode", default: 0) }
    set { defaults.set(newValue, forKey: "one_hand_mode") }
}

var numpadAnchor: Int {
    get { intValue("numpad_anchor", default: 0) }
    set { defaults.set(newValue, forKey: "numpad_anchor") }
}
```

`SyncContract.swift`:
- `RelayPrefState` (:120-121): add `var oneHand: Int? = nil` and `var numpadAnchor: Int? = nil`
  after `splitKeyboard` (defaulted → existing memberwise-init call sites still compile; optional
  Codable → old JSON still decodes).
- `update(...)` (:149-156): add params `oneHand: Int? = nil, numpadAnchor: Int? = nil` and thread
  them exactly like `splitKeyboard` (`oneHand ?? current?.oneHand`, note: keep them optional in
  the state rather than defaulting to 0, so "never set" stays distinguishable).
- `PrefInboxRecord` (:167-177): add `var oneHand: Int? = nil`, `var numpadAnchor: Int? = nil`.
- `PrefInbox.write` (:188-212): add params `oneHand: Int? = nil, numpadAnchor: Int? = nil`, merge
  like `hanConvert` (`oneHand ?? current?.oneHand`).
- Transport payload (:287): find the payload PARSER first (`grep -rn "han=" LimeIME-iOS --include="*.swift"`
  plus the `RelayPayloadTest.swift` expectations), then extend BOTH sides following the exact
  `split=` pattern: append `;oh=\(prefs.oneHand ?? 0);na=\(prefs.numpadAnchor ?? 0)` in the
  builder and parse the two keys wherever `split=` is parsed, defaulting to 0 when absent
  (backward compatibility with payloads from older keyboards). Update `RelayPayloadTest.swift`
  expectations accordingly.

`KeyboardViewController.swift`:
- Beside `splitKeyboardMode` (:134): `var oneHandMode: Int = 0` and `var numpadAnchor: Int = 0`.
- In `loadSettings()` beside :1260:

```swift
oneHandMode  = seededHotInt("one_hand_mode",  cold: d)   // §1.8 hot store
numpadAnchor = seededHotInt("numpad_anchor",  cold: d)   // §1.8 hot store
```

- In `drainPrefInbox()` after the `rec.splitKeyboard` block (:1175-1178):

```swift
if let oneHand = rec.oneHand {
    UserDefaults.standard.set(oneHand, forKey: "one_hand_mode")
    oneHandMode = oneHand
}
if let anchor = rec.numpadAnchor {
    UserDefaults.standard.set(anchor, forKey: "numpad_anchor")
    numpadAnchor = anchor
}
```

and extend the mirror call at :1193-1196 with `oneHand: oneHandMode, numpadAnchor: numpadAnchor`.

- [ ] **Step 4: Run to verify both pass**

Run: `.claude/scripts/ios-gate.sh unit LimeTests/LIMEPreferenceManagerTest` → PASS.
Run: `.claude/scripts/ios-gate.sh unit LimeTests/SyncContractTest` → PASS.
Run: `.claude/scripts/ios-gate.sh unit LimeTests/RelayPayloadTest` → PASS.

- [ ] **Step 5: Commit**

```bash
git add LimeIME-iOS/Shared LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift LimeIME-iOS/LimeTests
git commit -m "feat(ios): one_hand_mode and numpad_anchor cold/hot pref plumbing"
```

### Task 9: iOS anchoring + restore chevron in KeyboardView

**Files:**
- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardView.swift` (delegate protocol near :221, row
  constraints :747-748, new anchor API near `splitMode` :519)
- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift` (`viewWillLayoutSubviews` :571-577,
  delegate conformance)

**Interfaces:**
- Consumes: `ReachGeometry` (Task 6), KVC `oneHandMode`/`numpadAnchor` (Task 8).
- Produces: `KeyboardView.setHorizontalAnchor(leading: CGFloat, trailing: CGFloat,
  restoreChevron: Bool)`; `KeyboardViewDelegate.keyboardViewDidTapOneHandRestore()`;
  KVC `var isNumpadLayout: Bool` — used by Task 10.

- [ ] **Step 1: KeyboardView anchor API**

Near `var splitMode` (:519), read how its `didSet` triggers the row rebuild, then add using the
same rebuild call:

```swift
// SPLIT_ONE_HAND_KB horizontal anchoring: insets shrink the key block; the
// vacated strip is keyboard background, optionally holding the restore chevron.
private(set) var anchorLeadingInset: CGFloat = 0
private(set) var anchorTrailingInset: CGFloat = 0
private(set) var showRestoreChevron = false

func setHorizontalAnchor(leading: CGFloat, trailing: CGFloat, restoreChevron: Bool) {
    guard leading != anchorLeadingInset || trailing != anchorTrailingInset
            || restoreChevron != showRestoreChevron else { return }
    anchorLeadingInset = leading
    anchorTrailingInset = trailing
    showRestoreChevron = restoreChevron
    // trigger the same rebuild splitMode's didSet performs
}
```

Change the row constraints at :747-748 to honor the insets:

```swift
rowView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: anchorLeadingInset),
rowView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -anchorTrailingInset),
```

In the same rebuild function, after the rows are added, add the chevron button when anchored
(one inset is > 0 and `showRestoreChevron`):

```swift
if showRestoreChevron, anchorLeadingInset > 0 || anchorTrailingInset > 0 {
    let chevron = UIButton(type: .system)
    let onLeftStrip = anchorLeadingInset > 0   // block anchored right → strip on the left
    chevron.setImage(UIImage(systemName: onLeftStrip ? "chevron.left" : "chevron.right"),
                     for: .normal)
    chevron.tintColor = .secondaryLabel
    chevron.translatesAutoresizingMaskIntoConstraints = false
    chevron.addTarget(self, action: #selector(oneHandRestoreTapped), for: .touchUpInside)
    addSubview(chevron)
    NSLayoutConstraint.activate([
        chevron.widthAnchor.constraint(equalToConstant: max(anchorLeadingInset, anchorTrailingInset)),
        chevron.topAnchor.constraint(equalTo: topAnchor),
        chevron.bottomAnchor.constraint(equalTo: bottomAnchor),
        onLeftStrip ? chevron.leadingAnchor.constraint(equalTo: leadingAnchor)
                    : chevron.trailingAnchor.constraint(equalTo: trailingAnchor),
    ])
}
```

with the action + delegate method:

```swift
@objc private func oneHandRestoreTapped() {
    delegate?.keyboardViewDidTapOneHandRestore()
}
```

Add to the `KeyboardViewDelegate` protocol (:221 area):
`func keyboardViewDidTapOneHandRestore()`.

- [ ] **Step 2: KVC wiring (portrait-only one-hand, numpad anchor, split exclusivity)**

In `KeyboardViewController.swift` add the shared layout classifier (near `isOnPad` :248):

```swift
/// SPLIT_ONE_HAND_KB: the true numpad-grid layouts (spec decision — per-IM
/// *_number layers are 10-column full-width layouts and stay ordinary).
var isNumpadLayout: Bool {
    currentLayout.id.hasPrefix("phone_simple") || currentLayout.id.hasPrefix("computer_simple")
}
```

Replace the split application in `viewWillLayoutSubviews` (:574-576) with:

```swift
let isPad   = isOnPad
let numpad  = isNumpadLayout
let doSplit = isPad && !numpad
              && (splitKeyboardMode == 1 || (splitKeyboardMode == 2 && landscape))
keyboardView?.splitMode = doSplit

// SPLIT_ONE_HAND_KB horizontal anchoring.
var leading: CGFloat = 0, trailing: CGFloat = 0, chevron = false
let vw = view.bounds.width
if isPad {
    if numpad, numpadAnchor != 0 {
        let w = ReachGeometry.numpadAnchorWidth(viewWidth: vw, columns: 5,
                                                sizeClass: LayoutLoader.iPadSizeClass)
        let free = max(0, vw - w)
        switch numpadAnchor {
        case 1: trailing = free            // 靠左
        case 2: leading = free             // 靠右
        case 3: leading = free / 2; trailing = free / 2   // 置中
        default: break
        }
    }
} else if !landscape, oneHandMode != 0,
          ReachGeometry.oneHandAvailable(screenWidthPt: min(screen.width, screen.height)) {
    let free = max(0, vw - ReachGeometry.oneHandWidth(viewWidth: vw))
    if oneHandMode == 1 { trailing = free } else { leading = free }   // 靠左 / 靠右
    chevron = free > 0
}
keyboardView?.setHorizontalAnchor(leading: leading, trailing: trailing, restoreChevron: chevron)
```

Add the delegate implementation next to the other `KeyboardViewDelegate` methods:

```swift
func keyboardViewDidTapOneHandRestore() {
    // SPLIT_ONE_HAND_KB: chevron restores full width, persisted (§1.8 hot store + relay).
    oneHandMode = 0
    hotPrefs.oneHandMode = 0
    _ = try? relayPrefStore.update(oneHand: 0)
    view.setNeedsLayout()
}
```

- [ ] **Step 3: Gate + commit**

Run: `.claude/scripts/ios-gate.sh build` → PASS.
Run: `.claude/scripts/ios-gate.sh unit LimeTests/KeyboardViewControllerTest` → PASS.

```bash
git add LimeIME-iOS/LimeKeyboard
git commit -m "feat(ios): one-hand and numpad horizontal anchoring with restore chevron"
```

### Task 10: iOS surfaces — globe menu + LimeSettings

**Files:**
- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift` (`showGlobeMenu` :4411-4469)
- Modify: `LimeIME-iOS/LimeSettings/Views/PreferencesTabView.swift` (@AppStorage :42 area,
  pickers :171-178, onChange :283-292)

**Interfaces:**
- Consumes: everything from Tasks 8-9.

- [ ] **Step 1: Globe menu entries with exclusivity**

In `showGlobeMenu`, replace the 分離鍵盤 block (:4432-4439) with the exclusive trio:

```swift
// SPLIT_ONE_HAND_KB exclusivity: numpad layouts show 數字鍵盤位置 only;
// ordinary layouts show 分離鍵盤 (iPad) / 單手鍵盤 (gated iPhone).
let numpadLayout = isNumpadLayout
var pendingSplit = max(0, min(splitKeyboardMode, 2))
let splitStart = pendingSplit
if isOnPad && !numpadLayout {
    entries.append(.segmented(title: "分離鍵盤", icon: "rectangle.split.2x1",
                              labels: ["關閉", "開啟", "僅橫向"], selected: pendingSplit,
                              onSelect: { pendingSplit = $0 }))
}
var pendingOneHand = max(0, min(oneHandMode, 2))
let oneHandStart = pendingOneHand
let screenShort = min(UIScreen.main.bounds.width, UIScreen.main.bounds.height)
if !isOnPad && ReachGeometry.oneHandAvailable(screenWidthPt: screenShort) {
    entries.append(.segmented(title: "單手鍵盤", icon: "keyboard",
                              labels: ["關閉", "靠左", "靠右"], selected: pendingOneHand,
                              onSelect: { pendingOneHand = $0 }))
}
var pendingAnchor = max(0, min(numpadAnchor, 3))
let anchorStart = pendingAnchor
if isOnPad && numpadLayout {
    entries.append(.segmented(title: "數字鍵盤位置", icon: "rectangle.righthalf.inset.filled",
                              labels: ["滿版", "靠左", "靠右", "置中"], selected: pendingAnchor,
                              onSelect: { pendingAnchor = $0 }))
}
```

Extend the dismiss handler (:4449-4468) after the `pendingSplit` block:

```swift
if pendingOneHand != oneHandStart {
    self.oneHandMode = pendingOneHand
    self.hotPrefs.oneHandMode = pendingOneHand   // §1.8 hot store, not cold
    self.view.setNeedsLayout()
    changed = true
}
if pendingAnchor != anchorStart {
    self.numpadAnchor = pendingAnchor
    self.hotPrefs.numpadAnchor = pendingAnchor   // §1.8 hot store, not cold
    self.view.setNeedsLayout()
    changed = true
}
```

and extend the relay write (:4464-4466) — switch it to the merge-style `update` so all four prefs
persist:

```swift
if changed {
    _ = try? self.relayPrefStore.update(hanConvert: self.hanConvertOption,
                                        splitKeyboard: self.splitKeyboardMode,
                                        oneHand: self.oneHandMode,
                                        numpadAnchor: self.numpadAnchor)
}
```

- [ ] **Step 2: LimeSettings pickers**

In `PreferencesTabView.swift` add beside :42:

```swift
@AppStorage("one_hand_mode",  store: sharedDefaults) private var oneHandMode: Int = 0
@AppStorage("numpad_anchor",  store: sharedDefaults) private var numpadAnchor: Int = 0
```

beside the option tables (:83-84):

```swift
private let oneHandOptions      = [0, 1, 2]
private let oneHandLabels       = ["關閉", "靠左", "靠右"]
private let numpadAnchorOptions = [0, 1, 2, 3]
private let numpadAnchorLabels  = ["滿版", "靠左", "靠右", "置中"]
```

after the 分離鍵盤 picker block (:171-178):

```swift
// SPLIT_ONE_HAND_KB: 單手鍵盤 = gated iPhones; 數字鍵盤位置 = iPad only.
// 384 pt = ReachGeometry gate (64 mm × 6.0 pt/mm) — ReachGeometry lives in the
// keyboard target, so the settings app inlines the constant.
if UIDevice.current.userInterfaceIdiom != .pad,
   min(UIScreen.main.bounds.width, UIScreen.main.bounds.height) > 384 {
    Picker("單手鍵盤", selection: $oneHandMode) {
        ForEach(0..<oneHandOptions.count, id: \.self) { i in
            Text(oneHandLabels[i]).tag(oneHandOptions[i])
        }
    }
}
if UIDevice.current.userInterfaceIdiom == .pad {
    Picker("數字鍵盤位置", selection: $numpadAnchor) {
        ForEach(0..<numpadAnchorOptions.count, id: \.self) { i in
            Text(numpadAnchorLabels[i]).tag(numpadAnchorOptions[i])
        }
    }
}
```

Extend `writeHamburgerPrefInbox` (:288-292) and the onChange lines (:283-284):

```swift
.onChange(of: oneHandMode)  { newValue in writeHamburgerPrefInbox(oneHand: newValue) }
.onChange(of: numpadAnchor) { newValue in writeHamburgerPrefInbox(numpadAnchor: newValue) }

private func writeHamburgerPrefInbox(han: Int? = nil, split: Int? = nil,
                                     oneHand: Int? = nil, numpadAnchor: Int? = nil) {
    guard let base = FileManager.default.containerURL(
        forSecurityApplicationGroupIdentifier: LIMEPreferenceManager.suiteName) else { return }
    try? PrefInbox.write(base: base, defaults: sharedDefaults, hanConvert: han,
                         splitKeyboard: split, oneHand: oneHand, numpadAnchor: numpadAnchor)
}
```

- [ ] **Step 3: Gate + commit**

Run: `.claude/scripts/ios-gate.sh build` → PASS.

```bash
git add LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift LimeIME-iOS/LimeSettings/Views/PreferencesTabView.swift
git commit -m "feat(ios): globe menu and LimeSettings surfaces for one-hand and numpad anchor"
```

---

## Phase 3 — Full verification + docs

### Task 11: Full gates

- [ ] **Step 1: Android full gate**

Run: `cd LimeStudio && ./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, all tests PASS (including `ReachGeometryTest`).

- [ ] **Step 2: iOS full gate**

Run each; all must print PASS:

```bash
.claude/scripts/ios-gate.sh build
.claude/scripts/ios-gate.sh unit LimeTests/ReachGeometryTests
.claude/scripts/ios-gate.sh unit LimeTests/LIMEPreferenceManagerTest
.claude/scripts/ios-gate.sh unit LimeTests/SyncContractTest
.claude/scripts/ios-gate.sh unit LimeTests/RelayPayloadTest
.claude/scripts/ios-gate.sh unit LimeTests/RelayPrefSyncTest
.claude/scripts/ios-gate.sh unit LimeTests/KeyboardViewControllerTest
```

- [ ] **Step 3: Optional visual pass (do not block on it)**

If a booted simulator/emulator is available, use the `ios-visual-verify` / `android-visual-verify`
skills to spot-check: iPad split key width, numpad anchor left/right/center/fit, iPhone one-hand
left/right + chevron restore. If no device environment is available in this run, note that visual
verification is pending — the unit gates above are the goal-mode oracle
(docs reference: LimeUITests are Safari-driven and need manual IME enablement).

### Task 12: Spec corrections + close out

**Files:**
- Modify: `docs/SPLIT_ONE_HAND_KB.md`

- [ ] **Step 1: Write the implementation decisions back into the spec**

Three targeted edits:

1. In the geometry table, change the `ONE_HAND_MAX_W_MM` row to value `63 → 60` rationale:
   replace its row with
   `| \`ONE_HAND_MAX_W_MM\` | 60 | = \`REACH_ONE_HAND_MM\` — the far column center of 10 columns sits at ≈ 9.5/10 × 60 = 57 mm, inside the 60 mm zone; 63 + the 4 mm gate margin would have excluded 6.1" phones (~65 mm) that the acceptance criteria include |`
2. In Feature B, after the "Behavior:" paragraph add:
   `One-hand mode applies in portrait only, replicating the iOS built-in one-handed keyboard;
   in landscape the keyboard renders full width and the setting persists.`
3. In Feature C's "Applies to" list, replace the per-IM sentence with:
   `Per-IM \`*_number\` layers are 10-column full-width layouts and stay ordinary (they split /
   one-hand like any other layout); only the true numpad grids above anchor.`

- [ ] **Step 2: Final commit**

```bash
git add docs/SPLIT_ONE_HAND_KB.md
git commit -m "docs: record SPLIT_ONE_HAND_KB implementation decisions"
```

- [ ] **Step 3: Completion check (goal-mode exit criteria)**

All of the following must be true before declaring done:
- Every gate in Task 11 printed PASS, with the command output shown in the final report.
- All new prefs round-trip: hot-store write in the keyboard, PrefInbox app→keyboard, relay
  keyboard→app (covered by SyncContractTest/RelayPrefSyncTest/RelayPayloadTest).
- `git log --oneline` shows one commit per task, none carrying a Claude co-author trailer.
- Working tree clean (`git status`).
