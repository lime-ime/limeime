# Issue #142: Android phone English keyboard key labels are nearly invisible in light theme

## Problem statement

Community reporter `gontera` reports that in LIME 6.1.27, when the Android `行列10` input method is changed to the `電話英文鍵盤` keyboard layout, some key labels are not visibly rendered even though typing and composing still work.

Issue: https://github.com/lime-ime/limeime/issues/142

## Reported reproduction

1. Use LIME 6.1.27 on Android.
2. Configure `行列10` to use the `電話英文鍵盤` keyboard layout.
3. Open the keyboard.
4. Observe that several digit/letter keys appear blank or nearly blank, while key input and composition still function.

## Evidence summary

The attached screenshot shows a phone/T9-style keyboard. The left-side function keys (`123`, `ABC`, Shift, keyboard/menu) and right-side action keys are visible, but the central phone-key labels such as `1`, `ABC 2`, `DEF 3`, `GHI 4`, `JKL 5`, `MNO 6`, `PQRS 7`, `TUV 8`, `WXYZ 9`, and punctuation labels appear white or near-white against light key backgrounds. This makes the keys look blank even though the keyboard still accepts input.

## Code inspection notes

Android `phone.xml` and `phone_shift.xml` render most phone-key faces as bitmap drawables:

- `LimeStudio/app/src/main/res/xml/phone.xml`
- `LimeStudio/app/src/main/res/xml/phone_shift.xml`

Examples include `@drawable/phone_1`, `@drawable/phone_2`, `@drawable/phone_3`, `@drawable/phone_cal`, `@drawable/phone_left`, `@drawable/phone_0`, and `@drawable/phone_right`.

Those bitmap key faces appear to be drawn with light glyphs that do not adapt to the light keyboard key background. This matches the screenshot: icon-based central keys are nearly invisible, while text-label and standard icon keys are still visible.

Relevant Android routing:

- `LimeStudio/app/src/main/java/net/toload/main/hd/LIMEKeyboardSwitcher.java`
  - `getKeyboardXMLID("phone")` maps to `R.xml.phone`.
  - `getKeyboardXMLID("phone_shift")` maps to `R.xml.phone_shift`.
  - `getKeyboardXMLID("phone_simple")` maps to `R.xml.phone_simple`.

The newer `phone_simple.xml` uses text labels for numeric keys and is likely not the same visual path.

## Existing test and coverage assessment

Current Android coverage appears stronger for keyboard routing and XML resolution than for rendered key-face contrast. The reported bug is visual/theme-dependent, so compile-time XML resource checks alone would not catch white glyph assets on light key backgrounds.

A useful regression test or verification target should cover either:

- replacing phone-key bitmap faces with normal labels/sublabels that follow theme text colors, or
- ensuring the bitmap assets have light-theme/dark-theme variants or tinting that preserves contrast.

Manual visual verification is still needed for light theme, dark theme, and any dynamic/accent theme combination that changes key backgrounds.

## Likely root cause

Likely Android-only visual regression or legacy-asset issue: `phone.xml` / `phone_shift.xml` use static `phone_*` bitmap drawables for digit/letter key faces. Those drawables are too light for the current light key background, so the labels become effectively invisible while key codes and composing behavior remain correct.

This is consistent with the reporter's note that typing and composing still work.

## Proposed solution / investigation plan

1. Reproduce or visually inspect Android `phone` and `phone_shift` layouts under the current 6.1.27 light keyboard theme.
2. Prefer replacing central phone-key bitmap faces with text-based `keyLabel`/secondary-label rendering if supported by the Android keyboard view, so label colors follow the active theme.
3. If text-based phone labels are not feasible, add theme-appropriate drawable variants or tinting for `phone_*` assets.
4. Verify that key codes remain unchanged for both lowercase and shifted phone layouts.
5. Add a lightweight guard where practical so `phone` / `phone_shift` resource mapping stays covered, but do not rely on non-visual compile checks as the only verification.

## Follow-up questions

No reporter clarification is needed for initial triage. The screenshot and code path are enough to track this as a plausible Android visual bug.

If reproduction differs by theme, ask later for the exact LIME keyboard theme and Android light/dark mode only if the maintainer cannot reproduce with the default light appearance.

## Platform impact

### Android

Confirmed reported platform. The suspected path is Android-specific XML layout and bitmap drawable rendering in `phone.xml` / `phone_shift.xml`.

### iOS

Possible parity check only. The iOS phone layouts (`LimeIME-iOS/LimeKeyboard/Layouts/phone.json` and `phone_shift.json`) use text `label` / `sublabel` fields rather than Android `phone_*` bitmap drawables, so the same bitmap-contrast root cause does not directly apply. iOS should still be visually checked if the Android fix changes shared layout expectations or if a matching iOS report appears.

## Verification plan

- Android: open `行列10` with `電話英文鍵盤` in the light keyboard theme and verify all phone-key digit/letter labels are readable.
- Android: repeat with Shift enabled to verify uppercase phone labels remain readable.
- Android: verify input behavior is unchanged for keys `1` through `9`, `0`, punctuation, Shift, Space, Delete, Return, `123`, and `ABC` / `中` mode switching.
- Android: run the usual Gradle compile check from `LimeStudio/` after any source/XML changes.
- iOS: no immediate fix is indicated, but visually compare iOS phone layout during release QA if phone layout parity is touched.

## Retest condition

Do not ask the reporter to retest 6.1.27 again. Ask for reporter confirmation only after a newer Android APK or Google Play build contains a targeted phone-key label visibility fix.
