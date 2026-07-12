# Issue #75: Cangjie keyboard shows number/symbol keyboard remnants behind keys

## Problem statement

Community reporter `ejmoog` reports that on Android 15 with LIME IME 6.1.7, the Cangjie soft keyboard can show the number/symbol keyboard behind the Cangjie layout after switching modes.

Primary reported reproduction:

1. Start from the normal Cangjie Chinese keyboard.
2. Tap the numeric keyboard (`123` / number keyboard) once.
3. Switch back to Chinese.
4. The Chinese/Cangjie keys are visible, but a previous number/symbol keyboard layer remains visible underneath/behind the current keyboard.

The screenshot shows Cangjie keys in the foreground while another keyboard layer or toolbar controls remain visible through/around the keys, suggesting a stale keyboard view/invalidation or mode-state problem rather than an input conversion issue.

Additional reporter follow-up on the same issue: in English mode, long-pressing some European/accented letter popup keys can leave the popup visible indefinitely if the user does not select one; switching back to Chinese does not dismiss it. This mini-keyboard dismissal issue is a separate bug from the Cangjie symbol-layer artifact and should be checked on both Android and iOS. The reporter also suggests an option to disable accented/European letter popup display because many Traditional Chinese users may not need it. Current product decision: do not remove the popup mini-keyboard and do not add another preference for it, because LIME already has many preferences. Instead, fix the dismissal bug so touching outside the mini-keyboard dismisses it.

## Likely root cause

For the Cangjie number/symbol remnants, likely area is Android keyboard switching and redraw state in:

- `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`
- `LimeStudio/app/src/main/java/org/limeime/LIMEKeyboardSwitcher.java`
- keyboard XML layouts for Cangjie and number/symbol modes under `LimeStudio/app/src/main/res/xml/`

Relevant observations from source inspection:

- `LIMEService.switchKeyboard(...)` routes symbol/number and Chinese/English transitions through `LIMEKeyboardSwitcher`.
- `KEYCODE_SWITCH_TO_SYMBOL_MODE` calls `mKeyboardSwitcher.toggleSymbols()`.
- `KEYCODE_SWITCH_TO_IM_MODE` sets `mEnglishOnly = false` and calls `initialIMKeyboard()`.
- `LIMEKeyboardSwitcher.setKeyboardMode(...)` sets a new `LIMEKeyboard` on the existing `LIMEKeyboardView`, then calls `mInputView.setKeyboard(mInputView.getKeyboard())` as an invalidation path.
- Symbol mode handling can leave internal `mIsSymbols` / `mIsChinese` state transitions non-obvious. Returning to Chinese through `initialIMKeyboard()` should set `isSymbol=false`, but stale drawing can still appear if the old keyboard surface is not fully invalidated or if transparent key backgrounds expose prior content.

Most plausible technical causes to check:

1. `LIMEKeyboardView` does not fully clear its canvas/background before drawing the new keyboard after a number/symbol -> Cangjie transition.
2. `LIMEKeyboardSwitcher` leaves symbol/number mode state inconsistent when returning to Chinese, causing the wrong keyboard/background to be cached or redrawn.
3. A Cangjie keyboard theme/key background is partially transparent, making stale pixels from the previous keyboard visible after layout switching.
4. The Cangjie symbol-layer artifact appears Android-specific. Current simulator attempts did not reproduce it, so treat the screenshot as the strongest evidence and ask for device/theme details only if needed.

For the mini-keyboard dismissal bug, likely area is the popup lifecycle on both platforms:

- Android: `LIMEKeyboardBaseView.onLongPress(...)` opens `mMiniKeyboardPopup`. Before the fix, dismissal happened through mini-key selection/cancel, `closing()`, or `handleBack()`, but the main keyboard touch path had no explicit outside-tap dismissal for multi-key popups.
- iOS: `KeyboardViewController.showPopupKeyboard(...)` creates a full-size outside-tap overlay under `PopupKeyboardView`. The overlay must remain hittable in a keyboard extension; transparent touch targets can be ignored by iOS custom keyboards.

## Proposed solution

Investigate and fix the keyboard switch path rather than changing input-method mapping logic:

1. Treat the Cangjie symbol-layer artifact as Android-specific. Reproduce on Android 15 / 6.1.7 with Cangjie if possible:
   - Cangjie keyboard -> `123` numeric/symbol keyboard -> switch back to Chinese.
   - Test with default and any custom keyboard themes if possible.
   - Note: maintainer could not reproduce this artifact in simulator yet.
2. Reproduce the long-press popup path on both Android and iOS:
   - English keyboard -> long-press a key with accented/European popup choices -> do not select an alternative -> switch to Chinese.
   - Confirm whether the mini-keyboard popup remains visible and whether Back/outside tap dismisses it.
3. Add temporary logging around `LIMEService.switchKeyboard(...)`, `initialIMKeyboard()`, and `LIMEKeyboardSwitcher.setKeyboardMode(...)` for:
   - `primaryCode`
   - `mIsChinese`
   - `mIsSymbols`
   - selected keyboard XML id/name
   - active IM code
4. Verify that returning to Cangjie uses `isSymbol=false`, `isIm=true`, and `R.xml.lime_cj` / Cangjie layout.
5. For the Android Cangjie artifact, force a clean redraw when keyboard mode changes from symbol/number to Chinese:
   - clear symbol state when explicitly returning to IM mode if needed;
   - invalidate the whole `LIMEKeyboardView` / parent input view;
   - ensure the keyboard view/background is opaque or clears the canvas before drawing keys.
   - Since the maintainer could not reproduce this in the simulator, treat this as a defensive rendering fix and ask the reporter to verify on the affected device.
6. For mini-keyboard popups:
   - dismiss active mini-keyboard popups on outside tap;
   - dismiss active mini-keyboard popups before or during keyboard mode changes;
   - keep mini-key selection behavior unchanged.
7. Do not add a new preference for disabling accented/European popup alternatives at this time. The preferred fix is behavioral: the popup remains available, but the user can tap outside it to dismiss it.
8. Avoid broad cache resets unless needed; prefer a targeted mode-state, popup-dismissal, or redraw fix.

## Follow-up questions

The current report includes reproduction steps, screenshots, app version, and Android version. Ask only if needed after attempted reproduction:

- Device model and whether a custom keyboard theme/keyboard size/split-keyboard option is enabled.
- Whether the Cangjie number/symbol layering happens every time after `123` -> Chinese, or only after rotating/changing apps.
- Whether the same layering issue appears in non-Cangjie keyboards.
- Which English keys leave the accented/European popup stuck, whether tapping Back or another non-key area dismisses it, and whether the same behavior appears on Android, iOS, or both.

## Verification plan

- On Android 15, install the fixed build and confirm:
  - Cangjie -> numeric/symbol keyboard -> back to Chinese does not leave number/symbol remnants behind.
  - Repeated switches do not accumulate stale keyboard layers.
  - English long-press accented/European popup does not remain stuck after switching to Chinese or changing keyboard mode.
  - Arrow keys, candidate strip, emoji key, microphone key, and `123` key still render correctly.
  - Other layouts (English, Array, Dayi, phonetic) still switch normally.
- Completed: a 6.1.8 retest request was posted, and the reporter confirmed the issue resolved in one overall confirmation comment. Treat the issue as verified/resolved, without inferring separate itemized confirmation for each path.

## Resolution / follow-up status

- Fix commit `9230916` is included in APK `LIMEHD2026-6.1.8.apk`.
- Reporter `ejmoog` confirmed in comment `4514371698` on 2026-05-22 that after installing 6.1.8, the issue was resolved.
- Issue #75 is closed after reporter confirmation. No active retest/watch remains unless the reporter reopens or new evidence appears.

## Historical reporter reply draft

This draft was useful before the 6.1.8 retest; do not post it again now that the reporter has confirmed the issue resolved.

Thank you for the detailed report and screenshots. We could not reproduce the Cangjie overlap issue in the simulator, but we found a possible rendering path where stale keyboard pixels may remain after switching layouts, so we added a defensive redraw fix. Could you help verify whether the next test build fixes the `123` / symbol keyboard overlapping behind Cangjie on your device?

For the long-press popup mini-keyboard, we are not going to remove the popup or add another preference for disabling it, because LIME already has many preferences. However, we fixed the bug: after the next build, tapping outside the popup mini-keyboard should dismiss it.
