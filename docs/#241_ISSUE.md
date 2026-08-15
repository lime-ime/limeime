# Issue #241: Android soft Shift double-tap does not enter Caps Lock

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/241
- Classification: plausible Android bug, pending runtime reproduction
- Reporter: `walin333`
- Reported environment: Samsung Galaxy A54 5G, Android 14, LIME IME v6.1.38 from the GitHub Release channel
- Reported layouts: default Array and Phonetic keyboard configurations
- Live issue state at triage: open, no labels, assignees, comments, linked commits, or linked pull requests

## Problem statement

The reporter says that pressing the soft Shift key twice does not lock the keyboard in uppercase. Each Shift press only enables one-shot shifted input, and the reporter asks whether a setting is required. No setting is required for Caps Lock. The reported behavior conflicts with the documented behavior that a quick Shift double-tap enters Caps Lock and keeps the shifted layout active until Shift is tapped again.

The report identifies the device, Android version, app version, channel, and two tested input methods. It does not yet establish whether the test was performed while each Chinese layout was active or after switching to the English keyboard, how quickly the two taps occurred, what visual Shift states appeared, or whether both presses reached the app.

## Architecture preflight

### Accepted references reviewed

- `docs/ENGLISH_KB.md`, especially §3 “Shift State Machine”: LatinIME uses a double-tap timeout for Shift Lock and the current iOS implementation follows the same state model.
- `docs/IM_SERVICE.md`, especially §3 “State Model” and §4 “Key Event Dispatch”: `LIMEService` owns `mCapsLock` and routes soft Shift to `handleShift()`.
- `docs/manuals/keyboard-input.md`, §“Shift 與 Caps Lock”: a single tap enables one-shot shift, a double-tap enters Caps Lock, and a tap while locked exits Caps Lock.
- `docs/#148_ISSUE.md`: the accepted Android parity decision uses `ViewConfiguration.getDoubleTapTimeout()`, cancels the pending window on non-Shift input, and does not replace double-tap with a three-state single-tap cycle.

No successor, amendment, or supersession document changing the accepted double-tap behavior was found in the relevant keyboard documentation.

### Constraint ledger

| Constraint | Result |
| --- | --- |
| Required behavior | Two quick soft-Shift presses enter Caps Lock. One Shift press remains one-shot shift. A Shift press while locked exits Caps Lock. |
| Governing invariant | Shift state must remain consistent across the keyboard view, `LIMEKeyboardSwitcher`, and `mCapsLock`. A non-Shift key cancels the pending double-tap window. |
| Platform limitation | Android supplies the standard timeout through `ViewConfiguration.getDoubleTapTimeout()`. Touch/event timing and delivery can vary by device, but no Samsung-specific limitation is established. |
| Removable behavior | No accepted behavior is currently removable. In particular, do not replace double-tap with a single-tap three-state cycle or silently lengthen the platform timeout without runtime evidence. |
| Consequence of a change | Changing the timeout or state transitions without reproducing the failure could diverge from Android/LatinIME behavior, introduce accidental Caps Lock, or regress Shift chording. A fix should target the proven touch/timing boundary. |

## Current production flow

The v6.1.38 tag is commit `23e5c3aa8b7d15146991ac087b89014191f8d4d7`. The relevant Android Shift subsystem has no source difference between v6.1.38 and the triage-time `origin/master` head.

For devices reporting distinct multitouch:

1. `LIMEService.onPress(KEYCODE_SHIFT)` calls `handleShift()` on touch-down.
2. `handleShift()` delegates timing detection to `isShiftDoubleTap()`, which compares `SystemClock.uptimeMillis()` with `mLastShiftTime` using `ViewConfiguration.getDoubleTapTimeout()` and then stores the current Shift-tap time.
3. The first tap enters one-shot shift. Every Shift tap updates `mLastShiftTime` for the next transition.
4. A second Shift press inside the timeout should make `nextShiftTapState(...)` return shifted + Caps Lock.
5. The later `onKey(KEYCODE_SHIFT)` callback deliberately does not call `handleShift()` again on this path.
6. `onRelease(KEYCODE_SHIFT)` clears the pressed/chording flags. Its state-refresh path runs after a Shift chord or non-Shift release, and the one-shot reset is guarded by `!mCapsLock`, so the source path does not clear an active Caps Lock.

The timing-window helpers have JVM unit coverage and the pure state table has Android instrumentation coverage, but those tests do not prove the complete device touch-down → key callback → release sequence on the reported Samsung device.

## Likely root cause

The source state table appears capable of the documented result, so the report is not explained by a missing Caps Lock transition. The unresolved boundary is runtime integration: the second Shift touch may arrive outside the platform timeout, may not reach `onPress()` as expected, or a keyboard rebuild during `toggleShift()` may lose the visible locked state on this device/path.

This is a hypothesis, not a confirmed root cause. A screen recording and device/runtime event trace are needed before choosing a fix.

## Proposed solution

Do not change production behavior yet.

1. Reproduce on Android with the default Array and Phonetic configurations, testing both the active Chinese layout and the English keyboard reached from each IM.
2. Record the Shift icon/layout after the first press, second press, and first typed character.
3. Add or run an integration-level regression that exercises the distinct-multitouch callback order, not only `nextShiftTapState(...)`.
4. If both Shift presses arrive inside the Android timeout but Caps Lock is lost, fix the exact callback/state-reset boundary and retain the accepted state table.
5. If the presses are outside the timeout, first compare against another Android keyboard and Android's reported timeout before considering any product-level tolerance change.

## Follow-up questions

- Was Shift tested while the Chinese Array/Phonetic layout was visible, or after switching to the English keyboard?
- Does the Shift key/icon visibly change after each of the two taps?
- Can the reporter provide a short screen recording showing the two taps and then typing two letters/keys?
- Does the same behavior occur in a normal text field and in more than one host app?

## Verification plan

### Automated

- Run the JVM timing-helper tests: `./gradlew :app:testDebugUnitTest --tests org.limeime.AcceptsIntoComposingTest`.
- Run the Android instrumentation state-table tests on a device/emulator: `./gradlew :app:connectedDebugAndroidTest --tests org.limeime.LIMEServiceTest`.
- Add an Android integration/instrumentation regression for the actual distinct-multitouch callback sequence if reproduction identifies that boundary.
- Verify that `Shift → non-Shift → Shift` does not enter Caps Lock.
- Verify that Shift chording and one-shot shift remain unchanged.

### Manual Android

On the reported Samsung Galaxy A54 5G / Android 14 if available, and on a second Android device:

1. Test a normal text field with the default Array layout active.
2. Repeat with the default Phonetic layout active.
3. Switch to English from each IM and repeat.
4. Tap Shift once: one-shot shifted layout appears.
5. Quickly tap Shift twice: Caps Lock indicator appears and two subsequent letters/shifted keys remain shifted.
6. Tap Shift once while locked: return to unshifted.
7. Tap Shift twice slowly: the keyboard must not enter Caps Lock.
8. Tap Shift, type a letter, then tap Shift quickly: the first Shift must not remain eligible for Caps Lock.
9. Capture a screen recording at a known frame rate and, on a debug build, `adb logcat` entries for `handleShift()` and `LIMEKeyboard setShiftLocked:`. Compare the observed press interval with `ViewConfiguration.getDoubleTapTimeout()`.
10. Compare the same measured tap cadence with another Android keyboard.

## Platform impact

### Android

The reporter states that the failure occurs on Android 14 with v6.1.38 and two default IM configurations. No recording or event trace has been supplied, so the failure is reported but not runtime-confirmed. The relevant source is shared across Android IM layouts, so other Android layouts may be affected, but that broader scope is not yet reproduced.

### iOS

No iOS failure is reported. iOS has a separate Swift Shift state and touch-delivery implementation with the same intended double-tap behavior, but it uses a fixed 0.3-second timeout rather than Android's `ViewConfiguration` value. Treat iOS as unaffected unless separate runtime evidence reproduces the problem there. Any Android fix should preserve iOS behavior and should not add iOS-specific changes without an iOS failing test. If Android timing is changed, evaluate the resulting intentional parity difference explicitly.

## Backlog decision

Do not add `fix#241` yet. The expected behavior is established and the Android report is plausible, but the production source already contains the intended state transition and the failing runtime boundary is not identified. Add a confirmed Android backlog item only after reproduction or equivalent runtime evidence establishes the defect and implementation direction. No iOS backlog item is warranted from the current evidence.
