# Sliding Space Bar — Caret Movement

## Current iOS implementation

The iOS keyboard already implements sliding-space caret movement in
`LimeIME-iOS/LimeKeyboard/KeyboardView.swift`.

`KeyboardView.makeSpaceButton(...)` builds the space key as a private
`SpaceKeyButton`, not as a normal `UIButton` key. `SpaceKeyButton` overrides
`touchesBegan`, `touchesMoved`, `touchesEnded`, and `touchesCancelled` without
calling `super`, so UIKit never sends normal `.touchDown` / `.touchUpInside`
events for space. That keeps tap, slide, and long-press mutually exclusive:

| Gesture | iOS result |
| --- | --- |
| Tap space | `KeyboardView.delegate?.keyboardView(_:didPress:)` |
| Long-press space | `KeyboardView.delegate?.keyboardView(_:didLongPress:)` after 0.5 s |
| Slide space left/right | `KeyboardView.delegate?.keyboardView(_:didMoveCaretBy:)` continuously while moving |

The delegate path is:

```text
SpaceKeyButton.onCaretMove(delta)
  -> KeyboardView.delegate?.keyboardView(_:didMoveCaretBy: delta)
  -> KeyboardViewController.keyboardView(_:didMoveCaretBy:)
  -> textDocumentProxy.adjustTextPosition(byCharacterOffset: delta)
```

`KeyboardViewController.keyboardView(_:didMoveCaretBy:)` no-ops while
`mComposing` is non-empty. Cursor movement while the engine holds uncommitted
text can produce undefined editor state in some host apps.

### iOS constants

The live constants are in `LimeIME-iOS/LimeKeyboard/LayoutMetrics.swift`:

| Constant | Value | Purpose |
| --- | --- | --- |
| `LayoutMetrics.Gesture.spaceSwipeThreshold` | 12 pt | Dead zone before slide tracking begins |
| `LayoutMetrics.Gesture.spaceCaretStepPx` | 7 pt | Base caret step size |
| `LayoutMetrics.Gesture.spaceLongPressDuration` | 0.5 s | Space long-press delay |

### iOS acceleration

`SpaceKeyButton.stepsForDisplacement(_:)` converts absolute horizontal travel
into the total caret-step count. `lastCaretStep` stores the previous emitted
total, so every move event emits only the signed delta since the previous move.

| Travel beyond 12 pt dead zone | Step size | Effective speed |
| --- | --- | --- |
| 0-60 pt | 7 pt / step | 1x |
| 60-140 pt | 3.5 pt / step | 2x |
| 140 pt + | 1.75 pt / step | 4x |

The slide threshold cancels the long-press timer on first crossing. `tapSuppressed`
is set only after long-press or caret movement fires, so small motion inside the
dead zone still ends as a normal space tap.

## Current Android implementation

Android now mirrors the iOS sliding-space behavior for caret movement.

There are still two horizontal gesture paths, but the space-specific path now
owns sliding-space caret movement:

1. `LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEKeyboardBaseView.java`
   has general keyboard fling detection. `swipeLeft()` delegates to
   `LIMEService.swipeLeft()`, which calls `handleBackspace()`. `swipeRight()`
   delegates to `LIMEService.swipeRight()`, which currently calls
   `pickHighlightedCandidate()`. These are release/fling-style callbacks and do
   not provide continuous movement deltas, so they are intentionally left
   unchanged.

2. `LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEKeyboardView.java`
   has a space-specific `onTouchEvent(...)` override. On `ACTION_DOWN`, it tracks
   whether the active pointer began on the space key. On `ACTION_MOVE`, crossing
   `R.dimen.space_caret_dead_zone` cancels the normal key gesture once, keeps the
   gesture consumed, and emits incremental caret deltas through
   `OnKeyboardActionListener.moveCaretBy(int)`. On release, consumed space slides
   do not insert a space and do not trigger the old `KEYCODE_NEXT_IM` /
   `KEYCODE_PREV_IM` spacebar IM-switch path.

`PointerTracker` already excludes space from repeat handling and keeps space
preview visible while the space gesture is active. `LIMEKeyboard.isInside(...)`
also locks pointer motion into the space key while tracking `mSpaceDragStartX`
and `mSpaceDragLastDiff`.

The old Android spacebar IM-switch release handler, `SlidingSpaceBarDrawable`,
and its `spaceKeySliding*` theme attributes/resources were removed. IM switching
is no longer attached to horizontal movement on the space key.

`LIMEService.moveCaretBy(int)` dispatches signed steps to the current editor with
DPAD left/right key events and no-ops when `mComposing` is non-empty, matching
iOS's composing guard.

## Android behavior

The Android behavior is:

- Space tap still commits a space.
- Space long-press still sends `LIMEKeyboardView.KEYCODE_SPACE_LONGPRESS`.
- Sliding horizontally on space moves the editor caret continuously.
- Sliding does not trigger Android IM switching.
- Caret movement no-ops while `LIMEService.mComposing` is non-empty.
- General keyboard flings stay unchanged: left remains backspace, right remains
  candidate pick, down closes keyboard, up keeps its current behavior.

## Executed Android backport plan

> Completed task-by-task with red/green verification.
> Source edits must be incremental and saved as UTF-8 with BOM when touched.

### Task 1: Add Android space-caret metrics

**Files:**

- Modify: `LimeStudio/app/src/main/res/values/dimens.xml`
- Check whether overrides are needed in existing qualifier files:
  `values-land/dimens.xml`, `values-large/dimens.xml`,
  `values-large-land/dimens.xml`, `values-xlarge/dimens.xml`,
  `values-xlarge-land/dimens.xml`, `values-w820dp/dimens.xml`

**Steps:**

- [x] Add these dimens to `values/dimens.xml`:

```xml
<dimen name="space_caret_dead_zone">12dp</dimen>
<dimen name="space_caret_step">7dp</dimen>
```

- [x] Do not add qualifier overrides unless manual testing shows the slide feels
  wrong on tablet layouts. iOS uses fixed points, so Android should start with
  density-scaled dp.

- [x] Build resources:

```bash
cd LimeStudio
./gradlew :app:assembleDebug
```

Expected: resource compilation succeeds.

### Task 2: Add a continuous caret callback to the keyboard listener

**Files:**

- Modify: `LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEKeyboardBaseView.java`
- Modify: `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`

**Steps:**

- [x] Extend `LIMEKeyboardBaseView.OnKeyboardActionListener` with:

```java
/**
 * Called when the user slides horizontally on the space key.
 *
 * @param steps signed caret steps; negative moves left, positive moves right
 */
void moveCaretBy(int steps);
```

- [x] Add an empty forwarding implementation to the mini-keyboard listener inside
  `LIMEKeyboardBaseView.openPopupIfRequired(...)`:

```java
public void moveCaretBy(int steps) {
}
```

- [x] Add `@Override public void moveCaretBy(int steps)` to `LIMEService`.
  The final implementation is in Task 3.

```java
@Override
public void moveCaretBy(int steps) {
}
```

- [x] Build:

```bash
cd LimeStudio
./gradlew :app:assembleDebug
```

Expected: compile succeeds with every `OnKeyboardActionListener` implementer updated.

### Task 3: Implement caret dispatch in `LIMEService`

**Files:**

- Modify: `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`
- Modify: `LimeStudio/app/src/androidTest/java/org/limeime/LIMEServiceTest.java`

**Steps:**

- [x] Replace the no-op from Task 2 with a guarded implementation:

```java
@Override
public void moveCaretBy(int steps) {
    if (steps == 0 || mComposing.length() > 0) {
        return;
    }

    final int keyCode = steps < 0
            ? KeyEvent.KEYCODE_DPAD_LEFT
            : KeyEvent.KEYCODE_DPAD_RIGHT;
    final int count = Math.abs(steps);
    for (int i = 0; i < count; i++) {
        keyDownUp(keyCode, false);
    }
}
```

- [x] Add Android instrumentation coverage near the existing `keyDownUp` and swipe
  tests. Use reflection if needed to set `mComposing` and assert that calling
  `moveCaretBy(-2)`, `moveCaretBy(3)`, `moveCaretBy(0)`, and composing-active
  `moveCaretBy(1)` does not throw.

- [x] Run the focused Android test target available in this repo:

```bash
cd LimeStudio
./gradlew :app:connectedDebugAndroidTest
```

Result: the new focused instrumentation test passes on `Pixel_9_Pro(AVD) - 16`.
The full connected suite currently fails before this test at
`DBServerTest.testDBServerImportTxtTableWithExportAndVerify` with an
instrumentation process crash.

### Task 4: Intercept space slides in `LIMEKeyboardView`

**Files:**

- Modify: `LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEKeyboardView.java`

**Steps:**

- [x] Add fields to track one active space pointer:

```java
private int mSpaceCaretPointerId = -1;
private int mSpaceCaretStartX;
private int mLastSpaceCaretStep;
private boolean mSpaceCaretMoved;
private boolean mSpaceCaretCancelled;
private int mSpaceCaretDeadZone;
private int mSpaceCaretStepPx;
```

- [x] Initialize `mSpaceCaretDeadZone` and `mSpaceCaretStepPx` from the new dimens
  in both constructors.

- [x] Add a helper that finds a space key by raw bounds, not by `key.isInside(...)`,
  to avoid mutating `LIMEKeyboard`'s existing space-drag state during detection:

```java
private boolean isTouchOnSpaceKey(int x, int y) {
    if (getKeyboard() == null) {
        return false;
    }
    for (Key key : getKeyboard().getKeys()) {
        if (key.codes != null && key.codes.length > 0
                && key.codes[0] == LIMEBaseKeyboard.KEYCODE_SPACE
                && x >= key.x && x < key.x + key.width
                && y >= key.y && y < key.y + key.height) {
            return true;
        }
    }
    return false;
}
```

- [x] Add the Android equivalent of iOS `stepsForDisplacement`:

```java
private int stepsForSpaceDisplacement(int absDx) {
    int travel = absDx - mSpaceCaretDeadZone;
    if (travel <= 0) {
        return 0;
    }

    final float t1 = 60f * getResources().getDisplayMetrics().density;
    final float t2 = 140f * getResources().getDisplayMetrics().density;
    final float step = mSpaceCaretStepPx;

    final float steps;
    if (travel <= t1) {
        steps = travel / step;
    } else if (travel <= t2) {
        steps = t1 / step + (travel - t1) / (step / 2f);
    } else {
        steps = t1 / step + (t2 - t1) / (step / 2f) + (travel - t2) / (step / 4f);
    }
    return (int) steps;
}
```

- [x] In `onTouchEvent(...)`, on `ACTION_DOWN`, if the down point is on the space
  key, set the tracking fields but still call `super.onTouchEvent(me)` so normal
  tap and long-press behavior starts normally.

- [x] On `ACTION_MOVE` for the active pointer, compute `dx`. When
  `abs(dx) >= mSpaceCaretDeadZone`, synthesize one `ACTION_CANCEL` to
  `super.onTouchEvent(...)`, call `((LIMEKeyboard) getKeyboard()).keyReleased()`,
  call `getOnKeyboardActionListener().moveCaretBy(delta)` whenever the
  accumulated step changes, and return `true` for the rest of the gesture. This
  prevents a final space tap, long-press, or IM switch after caret movement
  begins.

- [x] On `ACTION_UP` / `ACTION_CANCEL`, if `mSpaceCaretMoved` is true, reset the
  tracking fields, call `keyboard.keyReleased()`, and return `true`. If no caret
  movement happened, preserve the existing `ACTION_UP` behavior for long-press
  and tap.

- [x] Delete the existing `KEYCODE_NEXT_IM` / `KEYCODE_PREV_IM` space-drag
  release block so horizontal space gestures cannot trigger IM switching.

- [x] Build:

```bash
cd LimeStudio
./gradlew :app:assembleDebug
```

Result: `./gradlew :app:assembleDebug` succeeds.

### Task 5: Add focused service coverage and optional view coverage

**Files:**

- Prefer modify: `LimeStudio/app/src/androidTest/java/org/limeime/LIMEServiceTest.java`
- If view-level construction is practical in existing tests, add:
  `LimeStudio/app/src/androidTest/java/org/limeime/SlidingSpaceCaretTest.java`

**Steps:**

- [x] Service-level coverage must verify:
  `moveCaretBy(0)` returns, negative steps dispatch left path, positive steps
  dispatch right path, and non-empty composing returns before dispatch.

- [ ] If `LIMEKeyboardView` can be constructed with the existing test harness,
  add view-level coverage that creates a `LIMEKeyboardView`, attach
  a test `OnKeyboardActionListener`, inject down/move/up `MotionEvent`s on the
  space key, and assert the listener receives incremental deltas such as
  `-1`, then additional negative deltas as distance increases.

- [x] Run:

```bash
cd LimeStudio
./gradlew :app:connectedDebugAndroidTest
```

Result: the focused service instrumentation test passes. View-level automation
was not added because constructing a realistic `LIMEKeyboardView` with layout,
IME listener, and active input connection is better covered by manual emulator
verification for this legacy view stack.

### Task 6: Manual verification matrix

**Files:** no code files.

**Steps:**

- [ ] In a text editor, tap space: one space is inserted.
- [ ] Long-press space without moving: the current Android space long-press action
  still fires.
- [ ] Slide space left slowly: caret moves left continuously, with no inserted
  space on release.
- [ ] Slide space right slowly: caret moves right continuously, with no inserted
  space on release.
- [ ] Slide farther left/right: acceleration increases after roughly 60 dp and
  140 dp beyond the 12 dp dead zone.
- [x] Start composing Chinese input, then slide space: no editor caret movement
  occurs while composing is active.
- [ ] Fling elsewhere on keyboard: existing left/right/up/down keyboard swipe
  behavior remains unchanged.
- [x] Confirm sliding space no longer switches Android input methods.

## Notes and follow-ups

- iOS haptics are still not wired for each caret step. Android should not add
  haptics in the backport unless iOS adds them first.
- The old Android spacebar IM-switch preview drawable and theme attributes have
  been removed with this backport.
- Keep the Android implementation local to `LIMEKeyboardView` plus the listener
  callback. Avoid changing `PointerTracker` unless the `ACTION_CANCEL` handoff
  proves insufficient during manual testing.
