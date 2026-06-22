# Issue #128: Android typing vibration preference has no effect on Samsung A55

## Summary

Community reporter `s9228034david-spec` reports that on a Samsung A55, enabling **喜好設定 / 打字震動** does not produce any vibration while typing with the LIME Android soft keyboard.

Issue: https://github.com/lime-ime/limeime/issues/128

## Reporter environment and reproduction

- Device: Samsung A55 (`SamSung A55` in the report)
- LIME version: 6.1.22
- Android / One UI version: Android 16 / One UI 8.5
- Platform: Android soft keyboard path
- Reported setting: **喜好設定 / 打字震動** is enabled
- System haptics: Samsung `觸控震動` is enabled
- Control path: LIME `打字音效` works when enabled, so the soft-key `onPress(...)` / sound-feedback path is firing
- Observed behavior: pressing keyboard keys produces no vibration response

## Relevant Android code path

The Android preference UI exposes the key-feedback settings in `LimeStudio/app/src/main/res/xml/preference.xml`:

- `vibrate_on_keypress` (`打字震動`) defaults to `true`
- `vibrate_level` (`震動強度`) is present but hidden on Android API 31+ because the app uses system haptic feedback there
- `sound_on_keypress` (`打字音效`) is separate

`LIMEPreferenceManager.getVibrateOnKeyPressed()` reads `vibrate_on_keypress`, and `LIMEService.loadSettings()` copies it into `hasVibration`. The soft-keyboard press path calls `LIMEService.onPress(...)`, which calls `doVibrateSound(...)`. When `hasVibration` is true, `doVibrateSound(...)` calls `vibrate(vibrateLevel)`.

For Android 12+ / API 31+, `LIMEService.vibrate(...)` currently calls:

```java
mInputView.performHapticFeedback(
        HapticFeedbackConstants.KEYBOARD_TAP,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
```

For older Android versions, it falls back to `Vibrator.vibrate(...)` / `VibrationEffect`.

## Root cause / fix hypothesis

This is a plausible Android haptic-feedback compatibility bug rather than a missing preference wiring issue. The preference, manifest permission (`android.permission.VIBRATE`), and soft-keyboard `onPress` path are wired, but the Android 12+ path delegates only to `View.performHapticFeedback(KEYBOARD_TAP, FLAG_IGNORE_VIEW_SETTING)` and then returns without checking whether the view haptic call was actually performed. On Samsung / One UI devices, that path may still be gated by Samsung's system keyboard/touch vibration settings, device haptic policy, or the specific feedback constant. The reporter's Samsung A55 / Android 16 / One UI 8.5 behavior suggests the app-level `打字震動` switch can be enabled while the view haptic path produces no vibration.

The source-level fix hypothesis is to keep the Android 12+ view haptic path as the first attempt, but use its boolean return value. If the view haptic call returns `false`, fall back to direct `Vibrator.vibrate(...)` with `VibrationAttributes.USAGE_TOUCH` so the IME keypress vibration is still tagged as user touch feedback. This keeps successful devices on the existing path while giving Samsung/One UI devices a second haptic route.

## Platform impact

### Android

Confirmed reporter platform. Current Android source contains an intended vibration path for soft-key presses, but Android 12+ devices use the view haptic pipeline instead of direct `Vibrator.vibrate(...)`. The proposed Android change adds a guarded direct-vibrator fallback only when the view haptic call declines the keypress feedback. Since keypress sound works, the issue remains scoped to the vibration branch / platform haptic call rather than soft-key events.

### iOS

No direct iOS impact is expected from this report. The referenced preference keys and `LIMEService` haptic implementation are Android-specific. iOS keyboard haptics, if any, use a separate Swift/iOS code path and are not validated by the Samsung A55 report.

## Follow-up questions for the reporter

Already answered by the reporter:

1. LIME 6.1.22.
2. Android 16 / One UI 8.5.
3. Samsung `觸控震動` is enabled.
4. LIME `打字音效` works when enabled.

Still useful if needed: a short logcat around LIME key presses with filters for `LIMEService`, `Vibrator`, and `HapticFeedback`, or maintainer reproduction on a Samsung / One UI 8.5 device.

## Implemented source change in PR workflow

The bug-fix branch for #128 changes `LIMEService.vibrate(...)` so Android 12+ soft-key vibration:

1. Calls `mInputView.performHapticFeedback(KEYBOARD_TAP, FLAG_IGNORE_VIEW_SETTING)` as before.
2. Reads the boolean return value.
3. If the view haptic call returns `false`, gets the default vibrator and calls `Vibrator.vibrate(...)` with a predefined keypress effect plus `VibrationAttributes.USAGE_TOUCH`.
4. Avoids duplicate feedback when the view haptic call succeeds.

The branch adds instrumentation compile coverage for the fallback decision. Actual vibration strength/feel still needs manual verification on Android 12+ hardware, ideally the reporter's Samsung / One UI 8.5 family.

## Verification plan

- Android instrumentation compile coverage for the fallback decision: API 31+ falls back only when view haptics return `false`, and does not duplicate direct vibration after a successful view haptic call.
- Java compile gates: `:app:compileDebugJavaWithJavac` and `:app:compileDebugAndroidTestJavaWithJavac`.
- Manual Android verification on API 31+:
  - Samsung A55 / One UI if available
  - a non-Samsung Android 12+ device or emulator for comparison
  - `打字震動` on/off
  - system touch/keyboard vibration on/off when available
  - `打字音效` on/off as a control path
- Confirm no haptic attempt is made for physical-keyboard-only input, because `onPress(...)` is only the soft-keyboard path.

## Current status

- Classified as plausible Android bug.
- Reporter supplied device/version/system-setting details in https://github.com/lime-ime/limeime/issues/128#issuecomment-4761943111: LIME 6.1.22, Android 16, One UI 8.5, Samsung `觸控震動` enabled, and LIME keypress sound works.
- Bug-fix PR workflow started for an Android haptic fallback change. Reporter retest should wait until the PR is merged and a newer Android APK/Play build contains the change.
- No iOS/TestFlight retest is implied by this Android report.
