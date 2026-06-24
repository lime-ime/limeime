# Issue #128: Android typing vibration/sound feedback regression

## Summary

Community reporter `s9228034david-spec` reports that on a Samsung A55, enabling **喜好設定 / 打字震動** does not produce any vibration while typing with the LIME Android soft keyboard.

**Status: closed / follow-up Pixel sound fix in progress.** PR #132 merged to `master` as merge commit `e0659dac3670e42b0970cae54fdc7fd299c2a19e`, and Android APK `LIMEHD2026-6.1.24.apk` contains that Samsung-oriented haptic fix. The original Samsung reporter later confirmed v6.1.24 restored both vibration and sound on Samsung A55 / Android 16 / One UI 8.5, and Jeremy verified on Samsung A17 that vibration works and the vibration-level preference changes the pulse. Jeremy then reported a new Pixel / Android 17 regression where both keypress vibration and keypress sound stopped working after the Samsung fix. PR #133 merged to `master` as merge commit `6791f14a06047047c39dc53875ce2ebaebcf1327`, restoring the Google/Pixel API 31+ system keyboard-tap haptic path while preserving Samsung/raw-pulse routing, adding the Samsung sound-volume preference, and closing #128. GitHub APK `LIMEHD2026-6.1.25.apk` now contains PR #133; Jeremy confirmed Pixel vibration is OK on that follow-up, but normal character-key sound remains much quieter than Backspace / Space / Enter even with phone sound and LIME volume at maximum. The current Pixel sound follow-up routes regular character keys away from Android's near-silent `FX_KEYPRESS_STANDARD` sample and needs device APK verification. The original community reporter already confirmed the Samsung A55 issue on v6.1.24, so no duplicate public reporter retest is needed unless new evidence appears.

Issue: https://github.com/lime-ime/limeime/issues/128

## Reporter environment and reproduction

- Device: Samsung A55 (`SamSung A55` in the report)
- LIME version: 6.1.22
- Android / One UI version: Android 16 / One UI 8.5
- Platform: Android soft keyboard path
- Reported setting: **喜好設定 / 打字震動** is enabled
- System haptics: Samsung `觸控震動` is enabled
- Control path: LIME `打字音效` works when enabled, so the soft-key `onPress(...)` / sound-feedback path is firing
- Original observed behavior: pressing keyboard keys produces no vibration response
- Post-v6.1.24 regression report from Jeremy: Pixel / Android 17 now has both keypress vibration and keypress sound not working; Pixel vibration worked before the Samsung-oriented fix

## Relevant Android code path

The Android preference UI exposes the key-feedback settings in `LimeStudio/app/src/main/res/xml/preference.xml`:

- `vibrate_on_keypress` (`打字震動`) defaults to `true`
- `vibrate_level` (`震動強度`) is present; with the split-path follow-up, it is hidden only on Google/Pixel API 31+ devices using system keyboard-tap haptics, and remains visible on Samsung/raw-pulse paths where app duration is used
- `sound_on_keypress` (`打字音效`) is separate

`LIMEPreferenceManager.getVibrateOnKeyPressed()` reads `vibrate_on_keypress`, and `LIMEService.loadSettings()` copies it into `hasVibration`. The soft-keyboard press path calls `LIMEService.onPress(...)`, which calls `doVibrateSound(...)`. When `hasVibration` is true, `doVibrateSound(...)` calls `vibrate(vibrateLevel)`.

Before PR #132, the project had gone through several Android haptic combinations. Git history shows Pixel-oriented attempts that used `performHapticFeedback(KEYBOARD_TAP, FLAG_IGNORE_VIEW_SETTING)` on API 31+ because direct `Vibrator.vibrate()` from an IME service can be classified as `USAGE_UNKNOWN`, plus predefined `VibrationEffect` handling for API 29–30. Those combinations were changed after Samsung hardware showed predefined/view haptic effects were accepted by the framework but dropped by the HAL. After PR #132, `vibrate(...)` uses direct `VibrationEffect.createOneShot(...)` on all API levels, with `VibrationAttributes.USAGE_TOUCH` only on API 33+.

## Samsung root cause (confirmed on hardware)

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

## Samsung fix implemented in PR #132

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

Samsung path: confirmed and fixed on a maintainer-tested Samsung SM-A1760 / Android 16 device, the original reporter later confirmed v6.1.24 restored both vibration and sound on Samsung A55 / Android 16 / One UI 8.5, and Jeremy verified vibration plus the vibration-level preference on Samsung A17. The Samsung fix replaces predefined `VibrationEffect`s — which the tested Samsung device's vibrator HAL silently discards as `ignored_unsupported` — with `createOneShot(...)` raw pulses, tagged `USAGE_TOUCH` on API 33+.

Pixel regression path: Jeremy reports Pixel / Android 17 lost both keypress vibration and keypress sound after the Samsung fix, while Pixel vibration worked before. The PR #133 follow-up restores the pre-Samsung Google/Pixel API 31+ system keyboard-tap path when `mInputView` exists, while keeping Samsung and unknown OEMs on direct raw pulses. After PR #133, Pixel vibration is confirmed OK. Remaining Pixel sound evidence is narrower: regular character keys are nearly inaudible, while Backspace / Space / Enter remain audible even with phone sound and LIME keypress volume at maximum. That isolates sound to the Android effect constant used for normal keys (`FX_KEYPRESS_STANDARD`) rather than `onPress(...)`, `hasSound`, `mAudioManager`, or preference plumbing.

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

`LIMEService.vibrate(long)` now uses a conservative split path:

1. **Google/Pixel API 31+ with `mInputView`:** `performHapticFeedback(KEYBOARD_TAP, FLAG_IGNORE_VIEW_SETTING)`; API 33+ also uses `FLAG_IGNORE_GLOBAL_SETTING`, matching the earlier Pixel-compatible path.
2. **Samsung and unknown OEMs:** direct raw pulse. Samsung never depends on `performHapticFeedback(...)` returning `false`, because hardware showed it can return `true` while the HAL drops the effect.

Raw pulse behavior remains:

1. **API 33+:** `createOneShot(...)` + `VibrationAttributes(USAGE_TOUCH)`.
2. **API 26–32:** `createOneShot(...)` without attributes (overload is API 33+ only).
3. **API <26:** legacy `vibrate(long)`.

`vibrate_level` is now hidden only for the Google/Pixel system keyboard-tap path where app duration is not used. It remains visible on Samsung/raw-pulse paths because the duration preference controls the one-shot pulse.

## Verification

- **Samsung on-device (SM-A1760 / Android 16, API 36):** confirmed via `dumpsys vibrator_manager` that keypress pulses changed from `ignored_unsupported` / `played: null` (before) to `finished` / `played: CLICK(MEDIUM, with fallback)` / `usage: TOUCH` (after). Vibration was felt on every keypress. ✅
- **Original reporter Samsung A55 / Android 16 / One UI 8.5:** reporter confirmed v6.1.24 made both 打字震動 and 打字音效 work. ✅
- **Samsung A17 maintainer retest on v6.1.24:** Jeremy verified vibration works, `vibrate_level` changes the pulse, and sound works. `super weak` was barely perceptible on that build. ✅
- **Samsung A17 follow-up added in PR #133:** runtime vibration maps the stored preference values to stronger pulse durations without changing the persisted values. Multiple Android system-volume attempts (`playSoundEffect(sound)`, explicit `STREAM_SYSTEM`, `STREAM_MUSIC`, and `-1.0f`) were still not enough on device, so LIME now has a `keypress_sound_volume` preference: system default by default, with custom 10% / 25% / 50% / 75% / 100% levels when Samsung/system behavior is too loud. These PR #133-only follow-ups are now present in GitHub APK v6.1.25 and need device retest. ⏳
- **Java compile gates:** `:app:compileDebugJavaWithJavac` and `:app:compileDebugAndroidTestJavaWithJavac` both passed during PR #132 verification.
- **Regression follow-up:** PR #133 (`6791f14a06047047c39dc53875ce2ebaebcf1327`) restores Google/Pixel API 31+ view haptics while preserving Samsung raw pulses and adds the Samsung sound-volume preference / stronger vibration mapping. GitHub APK `LIMEHD2026-6.1.25.apk` contains this follow-up. Jeremy verified Pixel vibration is OK, but regular Pixel character-key sound remains far quieter than Backspace / Space / Enter. The next sound follow-up changes regular character keys to use the audible `FX_KEYPRESS_SPACEBAR` effect instead of Pixel/AOSP's near-silent `FX_KEYPRESS_STANDARD`, with an androidTest regression asserting the regular-key mapping.
- **Not verified:** API 31–32 (Android 12 / 12L) — no device available; see the API 31–32 caveat above.

## Current status

- **Closed / APK-delivered, Pixel sound follow-up in progress.** #128 remains closed after the PR #133 merge. GitHub APK `LIMEHD2026-6.1.25.apk` contains merge commit `6791f14a06047047c39dc53875ce2ebaebcf1327` and the PR #133 haptic/sound-volume follow-up. Pixel vibration is now OK; remaining Pixel sound issue is regular character-key audibility.
- Samsung root cause remains valid for the Samsung path: device vibrator reports an empty supported-effects table, so predefined `VibrationEffect`s were dropped at the HAL. The original "Samsung returns `false` from `performHapticFeedback`" hypothesis was disproven on hardware (it returns `true`).
- PR #133 follow-up in v6.1.25: stored `vibrate_level` values remain `20/30/40/50/60` for compatibility, but runtime maps them to `30/40/50/60/70` ms pulses; keypress sound defaults to Android's one-arg `playSoundEffect(...)` path and adds a LIME-owned `keypress_sound_volume` preference for custom scalar levels when Samsung/system behavior is too loud. The Pixel sound follow-up is separate: when the platform's standard-key sample itself is too quiet, volume scalar alone is insufficient, so regular keys avoid `FX_KEYPRESS_STANDARD`.
- Android APK `LIMEHD2026-6.1.25.apk` verified on `master`: GitHub Contents blob SHA `11476f674bfb859704f834ba159caae8c137e19e`, size 7,407,192 bytes, downloaded SHA-256 `d89cfffaf2f252a4eb4570fa3ad95866ccb4018e921fe9aaae0c465e8a94e66f`, raw link https://raw.githubusercontent.com/lime-ime/limeime/master/LimeStudio/app/release/LIMEHD2026-6.1.25.apk.
- Next verification: after the Pixel sound follow-up APK is built, verify Pixel / Android 17 vibration remains OK and regular character keys are as audible as Backspace / Space / Enter; also retest Samsung A17 stronger vibration levels and the new `keypress_sound_volume` preference.
- No iOS/TestFlight retest is implied by this Android report. No duplicate public retest request is needed for the original Samsung A55 reporter because they already confirmed the original issue fixed on v6.1.24.
