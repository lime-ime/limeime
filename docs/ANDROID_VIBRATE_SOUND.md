# Android keypress vibration and sound history

This file records the Android keypress vibration and sound combinations tried while debugging #128 and related Pixel/Samsung haptic behavior.

Issue: https://github.com/lime-ime/limeime/issues/128

Primary code path:

- Soft-key press enters `LIMEService.onPress(int primaryCode)`.
- `onPress(...)` calls `doVibrateSound(primaryCode)`.
- Vibration depends on `hasVibration`, `LIMEPreferenceManager.getVibrateLevel()`, `getVibrator()`, and `vibrate(long)`.
- Sound depends on `hasSound`, `mAudioManager`, and `AudioManager.playSoundEffect(...)`.
- Because vibration and sound share `onPress(...)` and `doVibrateSound(...)`, a device where both fail should be debugged as a feedback-path or preference-state problem before changing only the vibration primitive.

## Current split-path follow-up

Samsung path is confirmed fixed on v6.1.24. The original Samsung A55 / Android 16 / One UI 8.5 reporter confirmed that both `打字震動` and `打字音效` work after reinstalling v6.1.24.

Jeremy then reported a Pixel / Android 17 regression after the Samsung fix: both keypress vibration and keypress sound now do not work. Pixel vibration worked before the Samsung-oriented change.

The follow-up keeps Samsung and unknown OEMs on the direct `createOneShot(...)` raw-pulse path, but restores Google/Pixel API 31+ to system keyboard-tap view haptics when `mInputView` exists. This intentionally does not use a fallback based on `performHapticFeedback(...)` returning `false`, because Samsung hardware showed that call can return `true` while the HAL still drops the effect.

`vibrate_level` is now hidden only for devices using system keyboard-tap view haptics. It remains visible for Samsung/raw-pulse devices where the setting controls the one-shot duration.

For the Pixel / Android 17 regression, first verify:

- `onPress(...)` is called for soft-key presses.
- `doVibrateSound(...)` is called from `onPress(...)`.
- `hasVibration` and `hasSound` are loaded from preferences as expected.
- `vibrate_level` state is understood. It is hidden only on the Google/Pixel API 31+ system keyboard-tap path, and visible on Samsung/raw-pulse paths where app duration is used.
- `mAudioManager` is non-null when `hasSound` is true.
- `mInputView` is present when view haptics are tested.
- `getVibrator()` returns a usable vibrator.
- `logcat` and `dumpsys vibrator_manager` show whether vibration calls are ignored, classified as background or unknown usage, unsupported by HAL, or never issued.

Useful filters:

```bash
adb logcat -s LIMEService VibratorService VibratorManagerService HapticFeedback AudioManager
adb shell dumpsys vibrator_manager
```

## Combination matrix

### 0. Older direct vibration and lazy sound path

Representative commits:

- Present before the 2026 haptic iterations.
- Rearranged but effectively visible around `090ead6`.

Behavior:

- `getVibrator()` selected `VibratorManager.getDefaultVibrator()` on API 31+.
- `getVibrator()` selected `VIBRATOR_SERVICE` below API 31.
- API 26+ used `VibrationEffect.createOneShot(duration, DEFAULT_AMPLITUDE)`.
- API <26 used legacy `vibrator.vibrate(duration)`.
- `doVibrateSound(...)` lazily initialized `mAudioManager` when null.
- Sound used `AudioManager.playSoundEffect(...)` with standard, delete, return, or spacebar effects.

Known reason for changing:

- Needed harder initialization and null safety around service startup and sound feedback.

### 1. Eager vibrator and AudioManager initialization

Commit:

- `94a835a` — `Improve voice input, vibration and audio init`

Behavior:

- `onCreate()` eagerly initialized vibrator service.
- API 31+ used `VibratorManager.getDefaultVibrator()`.
- API <31 used `VIBRATOR_SERVICE`.
- `onCreate()` eagerly initialized `mAudioManager`.
- `doVibrateSound(...)` no longer re-created `mAudioManager` inside the method.
- Sound only played when `hasSound && mAudioManager != null`.
- Vibration primitive stayed direct `createOneShot(...)` on API 26+ and legacy `vibrate(duration)` below API 26.

Known reason for changing:

- Reduce null crashes and make first keypress feedback more reliable.

### 2. Robust vibrator lookup and eager vibration preference load

Commit:

- `deec710` — `Enhance vibrator, voice locale & IME handling`

Behavior:

- Loaded `hasVibration = mLIMEPref.getVibrateOnKeyPressed()` in `onCreate()` so first keypress had the preference state.
- Added more logging around vibrator and sound initialization.
- Kept API 31+ `VibratorManager` path.
- Added fallback attempts when `mVibrator` was null.
- Added duration validation and exception logging in `vibrate(long)`.
- Kept sound through already-initialized `mAudioManager` and `AudioManager.playSoundEffect(...)`.

Known reason for changing:

- Needed clearer runtime diagnosis and safer behavior when vibrator service was null or first-keypress preference state was not ready.

### 3. Predefined haptic effects for API 29+

Commit:

- `f14ed4d` — `Bug fixed on vibration & voice input API handling`

Behavior:

- Added `mapDurationToVibrationEffect(long duration)`.
- API 29+ mapped the user duration setting to predefined effects:
  - 20 ms or 30 ms to `EFFECT_TICK`.
  - 40 ms to `EFFECT_CLICK`.
  - 50 ms or 60 ms to `EFFECT_HEAVY_CLICK`.
- API 26–28 used `VibrationEffect.createOneShot(duration, DEFAULT_AMPLITUDE)`.
- API <26 used legacy `vibrator.vibrate(duration)`.
- Sound path stayed `hasSound && mAudioManager != null` plus `AudioManager.playSoundEffect(...)`.

Expected benefit:

- Use hardware-optimized predefined effects, especially on Pixel LRA motors.

Known downside found later:

- Samsung Android 16 hardware can report an empty supported-effects table. On those devices, predefined effects are accepted by the framework but dropped at the HAL as `ignored_unsupported`.

### 4. API 33+ predefined effect with `VibrationAttributes.USAGE_TOUCH`

Commit:

- `eed3b20` — `Fixed vibrate not working on API 33+`

Behavior:

- API 29+ still used `createPredefined(...)` from the duration mapping.
- API 33+ wrapped predefined effects with `VibrationAttributes.Builder().setUsage(USAGE_TOUCH)`.
- API 29–32 used predefined effects without attributes.
- API 26–28 used `createOneShot(...)`.
- API <26 used legacy `vibrate(duration)`.

Expected benefit:

- Avoid Android 13+ treating direct IME-service vibration as unknown or background feedback.

Known downside found later:

- `USAGE_TOUCH` did not solve Samsung HAL unsupported predefined effects. If the device does not support the predefined effect table, the effect can still be ignored.

### 5. API 33+ keyboard-view haptics with ignore-view and ignore-global flags

Commit:

- `8952c7f` — `Use View haptics on Android 13+ to fix virtual key vibrating feedback`

Behavior:

- API 33+ skipped direct vibrator and called:

```java
mInputView.performHapticFeedback(
        HapticFeedbackConstants.KEYBOARD_TAP,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                | HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
```

- API 29–32 used predefined `VibrationEffect` effects.
- API 26–28 used `createOneShot(...)`.
- API <26 used legacy `vibrate(duration)`.

Expected benefit:

- Route keypress haptics through the attached IME keyboard view instead of direct service vibration.
- This was intended to help Pixel and Android 13+ where direct IME-service vibration could be restricted.

Known downside found later:

- `FLAG_IGNORE_GLOBAL_SETTING` is deprecated on newer APIs and does not provide a reliable modern override.
- Samsung Android 16 later showed `performHapticFeedback(KEYBOARD_TAP, ...)` could return true while the HAL still dropped the effect as unsupported.

### 6. API 31+ keyboard-view haptics with ignore-view only

Commit:

- `4b71b3f` — `Fix Android 12+ (API 31+) virtual key vibrating feedback with haptic handling`

Behavior:

- Lowered the view-haptic path from API 33+ to API 31+.
- API 31+ called:

```java
mInputView.performHapticFeedback(
        HapticFeedbackConstants.KEYBOARD_TAP,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
```

- Removed `FLAG_IGNORE_GLOBAL_SETTING`.
- API 29–30 used predefined `VibrationEffect` effects.
- API 26–28 used `createOneShot(...)`.
- API <26 used legacy `vibrate(duration)`.
- `vibrate_level` preference was hidden on API 31+ because view haptics are system-controlled and app duration is not directly used on that path.

Resolved stale-state note:

- The old API 31+ unconditional hiding in `LimeStudio/app/src/main/java/net/toload/main/hd/ui/LIMEPreference.java` was stale after PR #132 removed the universal API 31+ `performHapticFeedback(...)` path.
- `vibrate_level` is now hidden only for the Google/Pixel system keyboard-tap path.
- Raw-pulse paths keep `vibrate_level` visible because `doVibrateSound(...)` passes `mLIMEPref.getVibrateLevel()` into the one-shot duration.

Expected benefit:

- Cover Android 12 and 12L, where direct service vibration can be classified as `USAGE_UNKNOWN` and restricted.
- Keep Pixel/API 31+ on the attached view-haptic pipeline.

Known downside found later:

- Samsung Android 16 accepted the `KEYBOARD_TAP` view haptic at the framework level, but the vibrator HAL dropped it as unsupported. Return value alone was not enough to prove vibration was felt.

### 7. Samsung fallback attempt when view haptic returns false

PR #132 intermediate commits:

- `d6c81ad` — `fix(android): add haptic fallback for Samsung devices`
- `ae03745` — `fix(android): gate haptic vibration attributes by API level`
- `a03caa5` — `test: cover Android 16 haptic fallback`

Behavior attempted:

- API 31+ first called `performHapticFeedback(KEYBOARD_TAP, FLAG_IGNORE_VIEW_SETTING)`.
- Added `shouldUseDirectVibrationFallbackForSdk(int sdkInt, boolean viewHapticPerformed)`.
- Intended fallback rule: if API 31+ view haptic returned false, fall back to direct vibrator.
- API 33+ direct fallback used predefined effect plus `VibrationAttributes.USAGE_TOUCH`.
- API 29–32 direct fallback used predefined effect without attributes after `ae03745`, because the attributes overload is API 33+ only.
- API 26–28 used `createOneShot(...)`.
- API <26 used legacy `vibrate(duration)`.

Expected benefit:

- Preserve the Pixel/API 31+ view-haptic behavior when it worked.
- Add a Samsung fallback only when the view haptic was reported as not performed.

Why it was rejected:

- Hardware debugging on Samsung SM-A1760 / Android 16 showed `performHapticFeedback(...)` returned true, but `dumpsys vibrator_manager` still showed `ignored_unsupported` and `played: null`.
- The fallback never ran on the Samsung failure mode because the framework return value was true.
- The fallback still used predefined effects, which were the unsupported primitive on that Samsung HAL.

### 8. Samsung fix: direct `createOneShot(...)` everywhere

Commits:

- `750c9e5` inside PR #132 — `fix(android): #128 use createOneShot for keypress vibration`
- `e0659da` merge commit — `fix(android): #128 use createOneShot for keypress vibration (#132)`

Behavior:

- Removed `performHapticFeedback(...)` view-haptic path.
- Removed `mapDurationToVibrationEffect(...)`.
- Removed `shouldUseDirectVibrationFallbackForSdk(...)`.
- API 33+ uses:

```java
VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH)
vibrator.vibrate(effect, attributes)
```

- API 26–32 uses `VibrationEffect.createOneShot(duration, DEFAULT_AMPLITUDE)` without attributes.
- API <26 uses legacy `vibrator.vibrate(duration)`.
- Sound path stays `hasSound && mAudioManager != null` plus `AudioManager.playSoundEffect(...)`.

Samsung verification:

- On Samsung SM-A1760 / Android 16, `dumpsys vibrator_manager` changed from `ignored_unsupported` / `played: null` to `finished` / `played: CLICK(MEDIUM, with fallback)` / `usage: TOUCH`.
- Original Samsung A55 / Android 16 / One UI 8.5 reporter confirmed v6.1.24 restored both typing vibration and typing sound.

Current concern:

- Pixel / Android 17 now reports both vibration and sound not working after this change.
- Since sound did not change in PR #132, the Pixel regression may involve `onPress(...)`, preference state, `mInputView`, `mAudioManager`, or platform feedback routing rather than only the vibration primitive.

### 9. Split follow-up: Pixel view haptics, Samsung raw pulse

Behavior:

- Google/Pixel API 31+ with `mInputView` uses `performHapticFeedback(KEYBOARD_TAP, FLAG_IGNORE_VIEW_SETTING)`.
- Google/Pixel API 33+ also uses `FLAG_IGNORE_GLOBAL_SETTING`, matching the earlier Pixel-compatible path.
- Samsung and unknown OEMs continue to use direct raw pulses.
- The Samsung branch never depends on the boolean result from `performHapticFeedback(...)`.
- `vibrate_level` is hidden only for the system keyboard-tap view-haptic path; it remains visible for Samsung/raw-pulse devices.

## Sound combinations tried

The sound path has changed much less than vibration.

### A. Lazy AudioManager path

Representative state before `94a835a`:

```java
if (mAudioManager == null) {
    mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
}
if (hasSound) {
    mAudioManager.playSoundEffect(sound, FX_VOLUME);
}
```

Risk:

- Needed null assertions or null checks to avoid crashes when the service was not available.

### B. Eager AudioManager with null-gated playback

Introduced by `94a835a` and still effectively current:

```java
mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
...
if (hasSound && mAudioManager != null) {
    mAudioManager.playSoundEffect(sound, FX_VOLUME);
}
```

Current key mapping:

- Standard key: `AudioManager.FX_KEYPRESS_STANDARD`.
- Delete key: `AudioManager.FX_KEYPRESS_DELETE`.
- Enter key: `AudioManager.FX_KEYPRESS_RETURN`.
- Space key: `AudioManager.FX_KEYPRESS_SPACEBAR`.
- Volume: `1.0f`.

Current Pixel implication:

- If Pixel / Android 17 sound is also failing, check whether `hasSound` is false, `mAudioManager` is null, `onPress(...)` is not firing, or Android 17 is suppressing IME keypress sound effects.

## Debugging checklist for the next Pixel pass

1. Add temporary log lines around `onPress(...)` and `doVibrateSound(...)`:
   - primary code
   - `hasVibration`
   - `hasSound`
   - `vibrate_level`
   - `mInputView != null`
   - `mVibrator != null`
   - `mAudioManager != null`
   - selected vibration branch
   - selected sound effect
2. Test Pixel / Android 17 on current `master`.
3. Test the same Pixel against `4b71b3f`, the API 31+ view-haptic version.
4. Test the same Pixel against `8952c7f`, the API 33+ view-haptic version with ignore-global flag.
5. If sound fails only on current `master`, compare feedback preference load and `onPress(...)` behavior.
6. If sound works but vibration fails on a view-haptic commit, compare `dumpsys vibrator_manager` entries for `KEYBOARD_TAP`, direct `createOneShot`, usage `TOUCH`, and usage `UNKNOWN`.
7. If Pixel sound still fails on the split path, continue debugging the shared feedback path; the haptic branch does not change `AudioManager.playSoundEffect(...)`.

## Short conclusion

The history is not a simple linear improvement. Pixel compatibility pushed the code toward `performHapticFeedback(...)` on API 31+ because direct service vibration can be restricted. Samsung Android 16 pushed the code away from view haptics and predefined effects because its HAL accepted those calls but dropped them as unsupported. The current code therefore uses a conservative split: Google/Pixel API 31+ view haptics when an input view exists, and raw pulses for Samsung and unknown OEMs. Any remaining Pixel sound failure should be debugged in the shared keypress feedback path.
