# Issue #128: Android typing vibration preference has no effect on Samsung A55

## Summary

Community reporter `s9228034david-spec` reports that on a Samsung A55, enabling **喜好設定 / 打字震動** does not produce any vibration while typing with the LIME Android soft keyboard.

**Status: reporter-confirmed fixed / closed on Android APK v6.1.24.** PR #132 merged to `master` as merge commit `e0659dac3670e42b0970cae54fdc7fd299c2a19e` and auto-closed the issue on 2026-06-22. Root cause was confirmed on a Samsung SM-A1760 (Android 16 / API 36): the device vibrator reports an empty supported-effects table, so predefined `VibrationEffect`s were silently dropped at the HAL. The merged fix switches keypress vibration to `VibrationEffect.createOneShot(...)`; vibration was verified on hardware. Android APK `LIMEHD2026-6.1.24.apk` contains the fix, and the reporter confirmed that both typing vibration and typing sound work after reinstalling on the original Samsung A55 / Android 16 / One UI 8.5 path.

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

Before this fix, `LIMEService.vibrate(...)` on Android 12+ / API 31+ called `mInputView.performHapticFeedback(KEYBOARD_TAP, FLAG_IGNORE_VIEW_SETTING)` and, on the direct-vibrator path, used `VibrationEffect.createPredefined(EFFECT_TICK / CLICK / HEAVY_CLICK)`. Both of those are predefined effects, which is exactly what fails on the reporter's device — see Root cause below. After this fix, `vibrate(...)` uses `VibrationEffect.createOneShot(...)` on all API levels.

## Root cause (confirmed on hardware)

Confirmed by on-device debugging on a Samsung **SM-A1760** (Android 16 / API 36, One UI). This is a different Samsung Galaxy A-series device from the reporter's Samsung A55, so it is strong same-platform evidence but not reporter-device retest confirmation. The cause is **not** what the first fix hypothesis assumed.

Two facts were verified from live `logcat` and `dumpsys vibrator_manager` while pressing keys:

1. **`performHapticFeedback(KEYBOARD_TAP, FLAG_IGNORE_VIEW_SETTING)` returns `true`, not `false`.** The system reports the haptic as performed. So any fix that only falls back "when the view haptic returns `false`" never runs on this device — the original fix premise was wrong.

2. **The device's vibrator reports an empty supported-effects table.** `dumpsys vibrator_manager` shows:

   ```
   VibratorInfo:
     capabilities = []
     supportedEffects = []
     supportedPrimitives = []
   ```

   Because of this, every predefined effect — `VibrationEffect.createPredefined(EFFECT_TICK / EFFECT_CLICK / EFFECT_HEAVY_CLICK)` and the `KEYBOARD_TAP` view haptic — is **accepted by the framework but dropped at the HAL** with status `ignored_unsupported`:

   ```
   ignored_unsupported | usage: UNKNOWN | net.toload.main.hd2026 |
   reason: performHapticFeedback(constant=3) ... | played: null
   ```

   The original code (and the first fix attempt) used `createPredefined(...)`, so the keypress vibration was silently discarded — the call "succeeds," nothing buzzes.

The Samsung `觸控震動` / `打字震動` preference wiring, `android.permission.VIBRATE`, and the `onPress → doVibrateSound → vibrate` path were all correct. The defect was the choice of vibration primitive, not the preference plumbing.

## Fix (implemented and verified)

Stop using predefined effects. Use `VibrationEffect.createOneShot(duration, DEFAULT_AMPLITUDE)` — a raw timed amplitude pulse the framework renders on any device with a basic vibrator (it falls back to a generic waveform when the effects table is empty). On API 33+, tag it with `VibrationAttributes.USAGE_TOUCH` so the IME-service vibration is not classified as background `USAGE_UNKNOWN`.

`LIMEService.vibrate(long)` now does:

- **API 33+ (Android 13+):** `createOneShot(duration, DEFAULT_AMPLITUDE)` + `VibrationAttributes(USAGE_TOUCH)`.
- **API 26–32 (Android 8–12L):** `createOneShot(duration, DEFAULT_AMPLITUDE)` without attributes (the `VibrationAttributes` overload is API 33+ only).
- **API <26:** legacy `vibrate(long)`.

The `performHapticFeedback` view-haptic path, the `shouldUseDirectVibrationFallbackForSdk` helper, and the `mapDurationToVibrationEffect` predefined-effect mapper were all removed — they were tied to the disproven hypothesis.

**Verification result:** after the fix, the same device's `dumpsys vibrator_manager` history shows the keypress pulses as `finished` / `played: CLICK(MEDIUM, with fallback)` / `usage: TOUCH` instead of `ignored_unsupported`, and vibration is felt on every keypress. Confirmed on SM-A1760 / Android 16.

### Caveat — API 31–32 (Android 12 / 12L)

The root-cause fix (`createOneShot`) applies to all API levels, but only API 33+ can tag the call `USAGE_TOUCH`. On API 31–32 the direct `Vibrator.vibrate()` from an IME service is still subject to `USAGE_UNKNOWN` background classification, and there is no attributes overload to avoid it. No Android 12 / 12L device was available to test, and the reporter is on API 36, so the 31–32 path is **not verified** and could still need follow-up if Android 12 / 12L evidence appears.

## Platform impact

### Android

Confirmed and fixed in source on a maintainer-tested Samsung SM-A1760 / Android 16 device, then reporter-confirmed on the original Samsung A55 / Android 16 / One UI 8.5 path after installing APK v6.1.24. The fix replaces predefined `VibrationEffect`s — which the tested Samsung device's vibrator HAL silently discards as `ignored_unsupported` — with `createOneShot(...)` raw pulses, tagged `USAGE_TOUCH` on API 33+. Since keypress sound always worked, the issue was scoped to the vibration primitive, not soft-key event delivery.

### iOS

No direct iOS impact is expected from this report. The referenced preference keys and `LIMEService` haptic implementation are Android-specific. iOS keyboard haptics, if any, use a separate Swift/iOS code path and are not validated by the Samsung A55 report.

## Follow-up questions for the reporter

Already answered by the reporter:

1. LIME 6.1.22.
2. Android 16 / One UI 8.5.
3. Samsung `觸控震動` is enabled.
4. LIME `打字音效` works when enabled.

Still useful if needed: a short logcat around LIME key presses with filters for `LIMEService`, `Vibrator`, and `HapticFeedback`, or maintainer reproduction on a Samsung / One UI 8.5 device.

## Implemented source change

`LIMEService.vibrate(long)` now uses `VibrationEffect.createOneShot(duration, DEFAULT_AMPLITUDE)` on every API level instead of predefined effects or `performHapticFeedback`:

1. **API 33+:** `createOneShot(...)` + `VibrationAttributes(USAGE_TOUCH)`.
2. **API 26–32:** `createOneShot(...)` without attributes (overload is API 33+ only).
3. **API <26:** legacy `vibrate(long)`.

Removed as part of the fix (all tied to the disproven "view haptic returns false" hypothesis): the `performHapticFeedback` view-haptic branch, the `shouldUseDirectVibrationFallbackForSdk` helper, the `mapDurationToVibrationEffect` predefined-effect mapper, and the `android12PlusKeypressHapticFallsBackWhenViewHapticReturnsFalse` unit test.

## Verification

- **On-device (SM-A1760 / Android 16, API 36):** confirmed via `dumpsys vibrator_manager` that keypress pulses changed from `ignored_unsupported` / `played: null` (before) to `finished` / `played: CLICK(MEDIUM, with fallback)` / `usage: TOUCH` (after). Vibration is felt on every keypress. ✅
- **Java compile gates:** `:app:compileDebugJavaWithJavac` and `:app:compileDebugAndroidTestJavaWithJavac` both pass.
- **Not verified:** API 31–32 (Android 12 / 12L) — no device available; see the API 31–32 caveat above. Pixel / non-Samsung API 33+ comparison is optional follow-up.

## Current status

- **Reporter-confirmed fixed on APK v6.1.24 / closed.** PR #132 merged as `e0659dac3670e42b0970cae54fdc7fd299c2a19e` and initially auto-closed the community issue before reporter retest. APK v6.1.24 contains the fix, and the reporter confirmed in https://github.com/lime-ime/limeime/issues/128#issuecomment-4779119438 that after reinstalling, both typing vibration and typing sound work normally.
- Root cause: device vibrator reports an empty supported-effects table, so predefined `VibrationEffect`s were dropped at the HAL. The original "Samsung returns `false` from `performHapticFeedback`" hypothesis was disproven on hardware (it returns `true`).
- Android APK `LIMEHD2026-6.1.24.apk` contains the merged `createOneShot` change. Verified GitHub Contents blob SHA `314f6f0d628b8d7e64a3625ca0950a32ee67acf2`, size 7,406,087 bytes, downloaded SHA-256 `33b59c1ced50d179d218807d74e40bd2efa669ef99fa7bf119a6cdfd827963c6`. Retest request: https://github.com/lime-ime/limeime/issues/128#issuecomment-4778915207. Closing acknowledgement: https://github.com/lime-ime/limeime/issues/128#issuecomment-4779130777.
- No iOS/TestFlight retest is implied by this Android report.
