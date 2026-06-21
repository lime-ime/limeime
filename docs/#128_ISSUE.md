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

## Working hypothesis

This is a plausible Android haptic-feedback compatibility bug rather than a missing preference wiring issue. The preference, manifest permission (`android.permission.VIBRATE`), and soft-keyboard `onPress` path are wired, but the Android 12+ path delegates to `View.performHapticFeedback(KEYBOARD_TAP, FLAG_IGNORE_VIEW_SETTING)`. On Samsung / One UI devices, that path may still be gated by Samsung's system keyboard/touch vibration settings, device haptic policy, or the specific feedback constant. The reporter's Samsung A55 behavior suggests the app-level `打字震動` switch can be enabled while the actual system haptic call produces no vibration.

This root cause is not confirmed yet because there is no logcat or local reproduction showing whether `performHapticFeedback(...)` returns `false`, but the reporter's follow-up narrows the problem to Android 16 / One UI 8.5 with Samsung `觸控震動` enabled and LIME keypress sound working.

## Platform impact

### Android

Confirmed reporter platform. Current Android source contains an intended vibration path for soft-key presses, but Android 12+ devices use the view haptic pipeline instead of direct `Vibrator.vibrate(...)`. The Samsung A55 / Android 16 / One UI 8.5 report is enough to track as a plausible Android bug requiring device/settings verification and possibly a fallback or device-compatible haptic strategy. Since keypress sound works, the investigation should prioritize the vibration branch / platform haptic call rather than assuming soft-key events are missing.

### iOS

No direct iOS impact is expected from this report. The referenced preference keys and `LIMEService` haptic implementation are Android-specific. iOS keyboard haptics, if any, use a separate Swift/iOS code path and are not validated by the Samsung A55 report.

## Follow-up questions for the reporter

Already answered by the reporter:

1. LIME 6.1.22.
2. Android 16 / One UI 8.5.
3. Samsung `觸控震動` is enabled.
4. LIME `打字音效` works when enabled.

Still useful if needed: a short logcat around LIME key presses with filters for `LIMEService`, `Vibrator`, and `HapticFeedback`, or maintainer reproduction on a Samsung / One UI 8.5 device.

## Proposed investigation / fix direction

1. Add temporary diagnostic logging around the Android 12+ `performHapticFeedback(...)` call to capture its boolean return value and the active SDK/device path.
2. Reproduce on an Android 12+ device/emulator and, ideally, a Samsung / One UI device.
3. Compare `KEYBOARD_TAP` with other appropriate haptic constants and review whether Samsung devices require a different code path.
4. If `performHapticFeedback(...)` fails or is system-gated despite the app preference, evaluate a guarded fallback to `Vibrator.vibrate(...)` with `VibrationAttributes.USAGE_TOUCH` on API 33+ and the safest available pre-33 fallback.
5. Keep `vibrate_level` hidden on API 31+ unless the chosen Android 12+ implementation can reliably honor app-level intensity.

## Verification plan

- Android unit or instrumentation coverage where practical for preference-to-service state: `vibrate_on_keypress` enabled/disabled controls whether `doVibrateSound(...)` attempts feedback.
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
- Awaiting maintainer reproduction, diagnostic logcat, or a haptic-feedback implementation change.
- Do not ask the reporter to retest until a newer Android APK contains a relevant haptic-feedback change.
- No iOS/TestFlight retest is implied by this Android report.
