# Issue #169: Android phone split keyboard no longer appears

## Status

Open community bug, labeled `bug` and `Usability`, assigned to `jrywu`.

## Problem statement

The reporter uses LIME 6.1.33 on Android 16 / Samsung One UI 8.5. Split keyboard worked before the 6.1.33 update. After updating, selecting `分離鍵盤` still renders the normal full-width keyboard. Turning the option off, restarting, and enabling it again does not restore the split layout.

The report does not state the device orientation. The regression is nevertheless source-identifiable for portrait phones: v6.1.33 intentionally changed `split_keyboard_mode = 開啟` so phone split rendering is allowed only in landscape, even though older versions treated `開啟` as always enabled.

## Current observations and likely root cause

Commit `01aed41b57690cc54b1f39e248aba1643ca01543` (`fix(android): one-hand/split geometry — in-place rebuild, orientation gating, anchored expanded popup`) changed `LIMEBaseKeyboard` so `SPLIT_KEYBOARD_ALWAYS` now requires either a tablet or landscape orientation:

```java
splitKeyboard == SPLIT_KEYBOARD_ALWAYS && (tablet || mLandScape)
```

Before that commit, `SPLIT_KEYBOARD_ALWAYS` enabled split rendering in both orientations. The same commit changed the phone menu and one-hand precedence around the new landscape-only assumption. These changes are included in v6.1.33 and match the reported version boundary.

The preference itself still persists correctly through `LIMEPreferenceManager.getSplitKeyboard()` / `setSplitKeyboard()`. Restarting therefore cannot restore the previous portrait behavior because the renderer now ignores `ALWAYS` on phones in portrait.

This is a source-backed root-cause hypothesis for the portrait-phone path. The reporter has not yet confirmed whether the failed display is portrait-only, landscape-only, or both.

## Proposed solution

Restore the semantic distinction between the two existing split modes on Android phones:

- `開啟` / `SPLIT_KEYBOARD_ALWAYS`: render split keyboard in portrait and landscape, as before v6.1.33.
- `僅橫向` / `SPLIT_KEYBOARD_LANDSCAPD_ONLY`: render split keyboard only in landscape.
- Keep numpad layouts excluded from split mode.
- Ensure one-hand anchoring does not override an active portrait split keyboard.

Extract the split-eligibility decision into a small deterministic helper and cover phone/tablet, portrait/landscape, preference mode, arrow-key, and split-ineligible layout cases with unit tests. Add a focused regression test that fails on current `master`: a split-eligible phone in portrait with `SPLIT_KEYBOARD_ALWAYS` must enable split rendering.

## Follow-up questions

If device verification is still needed after the source fix, ask the reporter to confirm:

1. Whether the failed display occurs in portrait, landscape, or both.
2. Which input method and keyboard layout were active.
3. Whether `分離鍵盤` was set to `開啟` or `僅橫向`.
4. Whether the active layout was a normal alphabetic/per-input-method layout or a numeric keypad layout.

Do not request a retest until a newer Android build contains the targeted fix.

## Verification plan

### Automated Android checks

1. Add a RED unit test proving `SPLIT_KEYBOARD_ALWAYS` enables a split-eligible phone keyboard in portrait.
2. Verify GREEN after the smallest eligibility fix.
3. Preserve coverage for:
   - phone portrait `LANDSCAPE_ONLY` remains unsplit
   - phone landscape `LANDSCAPE_ONLY` splits
   - tablet `ALWAYS` splits
   - split-ineligible/numpad layouts never split
   - existing landscape arrow-key split behavior remains unchanged
4. Run the focused unit tests, Android Java compile, Android test compile, and `git diff --check`.

### Device checks

On a phone-sized Android device or emulator:

1. With an ordinary non-numpad layout in portrait, set `分離鍵盤` to `開啟` and verify that the split keyboard appears immediately.
2. Rotate to landscape and verify split remains active.
3. Set `分離鍵盤` to `僅橫向`, return to portrait, and verify the full-width keyboard appears.
4. Verify one-hand mode does not replace an active portrait `開啟` split keyboard.
5. Verify numeric keypad layouts remain unsplit.

## Platform impact

### Android

Confirmed report platform and affected source path. The regression was introduced by Android-only changes in `LIMEBaseKeyboard` and `LIMEKeyboardSwitcher` included in v6.1.33. A newer Android APK or Google Play build containing the fix will be required for reporter confirmation. The reporter's distribution channel is not yet known, so no APK link or Play-specific retest instruction should be posted yet.

### iOS

The analogous iOS path was inspected. `KeyboardViewController` applies split mode only when `isOnPad` is true, and LimeSettings exposes `分離鍵盤` only on iPad. iPhone split rendering is not an existing iOS behavior, so the Android phone regression and proposed Android semantic restoration do not directly change iOS. Existing iPad split behavior should remain under normal release QA, but no iOS code change or TestFlight retest is required for the reported Android scope.
