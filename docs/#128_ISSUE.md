# Issue #128: Android typing vibration/sound feedback regression

## Summary

Community reporter `s9228034david-spec` reports that on a Samsung A55, enabling **喜好設定 / 打字震動** does not produce any vibration while typing with the LIME Android soft keyboard.

**Status: closed / GitHub Release v6.1.26 includes the PR #133 haptic/sound-volume follow-up, pending optional device QA only.** PR #132 merged to `master` as merge commit `e0659dac3670e42b0970cae54fdc7fd299c2a19e`, and Android APK `LIMEHD2026-6.1.24.apk` contains that Samsung-oriented haptic fix. The original Samsung reporter later confirmed v6.1.24 restored both vibration and sound on Samsung A55 / Android 16 / One UI 8.5, and Jeremy verified on Samsung A17 that vibration works and the vibration-level preference changes the pulse. Jeremy then reported a new Pixel / Android 17 regression where both keypress vibration and keypress sound stopped working after the Samsung fix. PR #133 merged to `master` as merge commit `6791f14a06047047c39dc53875ce2ebaebcf1327`, restoring the Google/Pixel API 31+ system keyboard-tap haptic path while preserving Samsung/raw-pulse routing, adding the Samsung sound-volume preference, and closing #128. GitHub APK `LIMEHD2026-6.1.25.apk` first delivered PR #133, and GitHub Release `v6.1.26` now carries the same follow-up in the current public GitHub APK. Remaining verification is device QA for Pixel / Android 17 plus Samsung A17 sound-volume and stronger-pulse behavior. The separate Pixel regular-key sound audibility follow-up PR #134 (`7205b3f49014501c6f615253d04bc5a29dfad211`) was closed unmerged as outdated on 2026-06-30. A live compare showed that head commit is not on `master`, and no replacement public `master` commit was verified from the close event, so this audibility follow-up is still outside v6.1.26 until a separate fix/new build lands. The original community reporter already confirmed the Samsung A55 issue on v6.1.24, so no duplicate public reporter retest is needed unless new evidence appears.

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

Pixel regression path: Jeremy reports Pixel / Android 17 now has both keypress vibration and keypress sound not working after the Samsung fix, while Pixel vibration worked before. The follow-up restores the pre-Samsung Google/Pixel API 31+ system keyboard-tap path when `mInputView` exists, while keeping Samsung and unknown OEMs on direct raw pulses. Because sound shares the same `onPress(...)` / `doVibrateSound(...)` path and was not changed by the haptic split, any remaining Pixel sound failure still needs targeted debugging.

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
- **Samsung A17 follow-up added in PR #133:** runtime vibration maps the stored preference values to stronger pulse durations without changing the persisted values. Multiple Android system-volume attempts (`playSoundEffect(sound)`, explicit `STREAM_SYSTEM`, `STREAM_MUSIC`, and `-1.0f`) were still not enough on device, so LIME now has a `keypress_sound_volume` preference: system default by default, with custom 10% / 25% / 50% / 75% / 100% levels when Samsung/system behavior is too loud. These PR #133-only follow-ups first reached the repo APK in v6.1.25 and are also present in GitHub Release v6.1.26. ⏳
- **Java compile gates:** `:app:compileDebugJavaWithJavac` and `:app:compileDebugAndroidTestJavaWithJavac` both passed during PR #132 verification.
- **Regression follow-up:** PR #133 (`6791f14a06047047c39dc53875ce2ebaebcf1327`) restores Google/Pixel API 31+ view haptics while preserving Samsung raw pulses and adds the Samsung sound-volume preference / stronger vibration mapping. GitHub Release `v6.1.26` now carries this follow-up in the public GitHub APK and remains available for Pixel / Android 17 and Samsung A17 device QA. Pixel regular-key sound audibility, if still too quiet, is no longer tracked by open PR #134 because that PR was closed unmerged as outdated on 2026-06-30. Treat it as a separate future fix/device-QA follow-up until a replacement public `master` commit and newer build are verified.
- **Not verified:** API 31–32 (Android 12 / 12L) — no device available; see the API 31–32 caveat above.

## Current status

- **Closed / APK-delivered, awaiting optional device QA.** #128 remains closed after the PR #133 merge. GitHub APK `LIMEHD2026-6.1.25.apk` first contained merge commit `6791f14a06047047c39dc53875ce2ebaebcf1327` and the PR #133 haptic/sound-volume follow-up, and GitHub Release `v6.1.26` now carries the same follow-up in the current public GitHub APK.
- Samsung root cause remains valid for the Samsung path: device vibrator reports an empty supported-effects table, so predefined `VibrationEffect`s were dropped at the HAL. The original "Samsung returns `false` from `performHapticFeedback`" hypothesis was disproven on hardware (it returns `true`).
- PR #133 follow-up: stored `vibrate_level` values remain `20/30/40/50/60` for compatibility, but runtime maps them to `30/40/50/60/70` ms pulses; keypress sound defaults to Android's one-arg `playSoundEffect(...)` path and adds a LIME-owned `keypress_sound_volume` preference for custom scalar levels when Samsung/system behavior is too loud.
- Current Android GitHub APK: `LIMEHD2026-6.1.26.apk` on GitHub Release `v6.1.26`, GitHub Contents blob SHA `cd7d820891ab8859e3cdd6916f946f5c701ff5e9`, size 7,407,267 bytes, release/downloaded SHA-256 `ac765155f70e938c747e8afd299301a2250d5167587e3222c74be94e0a9d929c`, release link https://github.com/lime-ime/limeime/releases/download/v6.1.26/LIMEHD2026-6.1.26.apk.
- Next verification: optional Pixel / Android 17 keypress vibration and sound QA on v6.1.26; optional Samsung A17 stronger vibration levels and `keypress_sound_volume` QA. Pixel regular-key sound audibility was tracked in PR #134 (`7205b3f49014501c6f615253d04bc5a29dfad211`), but that PR is now closed unmerged as outdated. It should not be considered included in v6.1.26 unless a replacement public `master` fix is verified and a newer APK is built.
- No iOS/TestFlight retest is implied by this Android report. No duplicate public retest request is needed for the original Samsung A55 reporter because they already confirmed the original issue fixed on v6.1.24.
