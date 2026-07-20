# Issue #148: Android Shift double-tap Caps Lock timing

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/148
- Classification: `question` + `Usability`
- Reporter: `01disney`
- Reported version/device: LIME IME v6.1.27, Android 16, Poco F6 Pro
- Current state: closed as completed by maintainer `jrywu` on 2026-07-18 after the v6.1.28 delivery/retest request in https://github.com/lime-ime/limeime/issues/148#issuecomment-4916998522; no reporter confirmation was posted before closure

## Problem statement

The reporter expected English Shift to cycle through three states with single taps:

1. lowercase
2. first-letter uppercase / shifted
3. all-uppercase / Caps Lock

They also reported that the current quick double-tap Shift gesture sometimes enters Caps Lock and sometimes does not, and that it feels less intuitive than a single-tap cycle.

## Decision

Keep Android LatinIME parity:

- Single Shift tap from unshifted enters shifted / one-shot Shift.
- Single Shift tap from shifted returns to unshifted.
- Double-tap Shift enters Caps Lock.
- Single Shift tap while Caps Lock exits Caps Lock.
- Do not change to a single-tap three-state cycle.

This matches the behavior documented in `docs/ANDROID_IPHONE_KEYBOARD.md` and `docs/manuals/keyboard-input.md`.

## LIME implementation after parity fix

Relevant Android code:

- `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`
  - `handleShift()` owns Shift key transitions.
  - `isShiftDoubleTap()` compares the current `SystemClock.uptimeMillis()` with `mLastShiftTime`.
  - The timeout source is `ViewConfiguration.getDoubleTapTimeout()`.
  - `shiftDoubleTapWindowAfterKey(...)` cancels the pending Shift double-tap window on any non-Shift key.
  - `nextShiftTapState(...)` implements the state table.
- `LimeStudio/app/src/androidTest/java/org/limeime/LIMEServiceTest.java`
  - `singleShiftTapTogglesBetweenShiftedAndUnshiftedOnly()`
  - `doubleShiftTapEntersShiftLockAndSingleTapUnlocks()`
- `LimeStudio/app/src/test/java/org/limeime/AcceptsIntoComposingTest.java`
  - `shiftDoubleTapWindow_nonShiftCancelsPendingShiftTap()`
  - `shiftDoubleTapWindow_shiftKeepsPendingShiftTap()`

The pure state table already matched the intended behavior. The new focused unit coverage locks down the real timing-window rule that LatinIME applies.

Relevant iOS code:

- `LimeIME-iOS/Shared/Models/KeyLayout.swift`
  - `ShiftTapPolicy.nextState(...)` mirrors Android's pure state table.
  - `ShiftDoubleTapPolicy` mirrors Android's double-tap-window cancellation helper.
- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`
  - `handleShift()` applies `ShiftTapPolicy`.
  - `isShiftDoubleTap()` compares `Date().timeIntervalSinceReferenceDate` with `lastShiftTapTime`.
  - `clearShiftState()` resets `lastShiftTapTime`, but normal character input does not.
- `LimeIME-iOS/LimeKeyboard/LayoutMetrics.swift`
  - `LayoutMetrics.Gesture.shiftDoubleTapTimeout` is `0.3`.
- `LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift`
  - `testSingleShiftTapTogglesBetweenShiftedAndUnshiftedOnly()`
  - `testDoubleShiftTapEntersShiftLockAndSingleTapUnlocks()`
  - `testShiftDoubleTapWindowNonShiftCancelsPendingShiftTap()`
  - `testShiftDoubleTapWindowShiftKeepsPendingShiftTap()`

iOS also delivers normal plain keys, including Shift, on touch-down. Its timing feel should therefore be close to Android: the double-tap window starts at the first Shift press, not at Shift release.

## Android LatinIME comparison

LatinIME uses the same Android timeout source:

- `TimerHandler.startDoubleTapShiftKeyTimer()` uses `ViewConfiguration.getDoubleTapTimeout()`.
- `KeyboardState.onPressKey(...)` starts the double-tap timer on Shift press.
- `KeyboardState.onPressKey(...)` cancels the double-tap timer when a non-Shift key is pressed.
- `KeyboardState.onPressShift()` enters Shift Lock when the second Shift press arrives while the timer is active.

Timing parity means:

```text
Shift press -> start Android double-tap timeout
second Shift press before timeout expires -> Caps Lock
any non-Shift key before the second Shift -> cancel the pending double-tap
```

Do not add a custom longer timeout for parity. `ViewConfiguration.getDoubleTapTimeout()` is normally around 300 ms and is the platform default.

## Fixed gap

Before this fix, Android LIME stored only `mLastShiftTime` and iOS stored the same idea as `lastShiftTapTime`. Neither implementation explicitly canceled the pending double-tap window when a non-Shift key was pressed.

That meant a very fast sequence such as:

```text
Shift -> letter -> Shift
```

could still look like a Shift double-tap to LIME if the two Shift presses were within the platform double-tap timeout. LatinIME cancels that window on the intervening non-Shift key, and LIME now does the same on Android and iOS.

This gap is about LatinIME parity and false Caps Lock detection. It is not a reason to implement the reporter's requested single-tap three-state cycle.

## Implemented minimal change

The #148 hardening stays intentionally small:

1. In `LIMEService.onKey(...)`, a non-Shift key resets `mLastShiftTime` to `-1`.
2. In iOS `KeyboardViewController.onKey(primaryCode:)`, a non-Shift key resets `lastShiftTapTime` to `0`.
3. Focused Android and iOS tests prove `Shift -> non-Shift -> Shift` does not keep the first Shift eligible for Caps Lock.
4. The timeout itself remains unchanged.

Skipped for now:

- No `+100 ms` tolerance.
- No release-based timing.
- No single-tap lowercase -> shifted -> Caps Lock cycle.

Add a tolerance only if users still report failed double-taps after the LatinIME cancellation behavior is in place and verified on-device.

## Verification plan

- Automated coverage:
  - Android focused unit test: `./gradlew :app:testDebugUnitTest --tests org.limeime.AcceptsIntoComposingTest`
  - iOS focused XCTest: `KeyboardViewControllerTest/testShiftDoubleTapWindowNonShiftCancelsPendingShiftTap`
  - iOS focused XCTest: `KeyboardViewControllerTest/testShiftDoubleTapWindowShiftKeepsPendingShiftTap`
- Manual Android smoke test:
  - Tap Shift once: shifted keyboard appears.
  - Tap Shift again slowly: returns to unshifted, not Caps Lock.
  - Double-tap Shift quickly: Caps Lock appears.
  - Tap Shift once while Caps Lock: exits Caps Lock.
  - Tap Shift, type a letter, tap Shift quickly: should not enter Caps Lock from the first Shift tap.
- Manual iOS smoke test:
  - Repeat the same five Shift checks on iPhone and iPad layouts.

## Closure state

- Source hardening was delivered in v6.1.28 and the Google Play update/retest path was posted for the reporter.
- Maintainer `jrywu` closed the issue as completed on 2026-07-18.
- The closure is maintainer-confirmed delivery, not reporter-verified behavior on the reported Poco F6 Pro / Android 16 device.
- No active retest watch remains unless the reporter reopens the issue or adds new double-tap reliability evidence.

## Public response note

If replying on the issue, say that LIME will keep the Android-standard double-tap Caps Lock behavior. If a code change is made, describe it as improving double-tap reliability / LatinIME parity, not as changing Shift into a three-state single-tap cycle.
