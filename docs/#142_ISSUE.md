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

- `LimeStudio/app/src/main/java/org/limeime/LIMEKeyboardSwitcher.java`
  - `getKeyboardXMLID("phone")` maps to `R.xml.phone`.
  - `getKeyboardXMLID("phone_shift")` maps to `R.xml.phone_shift`.
  - `getKeyboardXMLID("phone_simple")` maps to `R.xml.phone_simple`.

The newer `phone_simple.xml` uses text labels for numeric keys and is likely not the same visual path.

## Existing test and coverage assessment

Current Android coverage appears stronger for keyboard routing and XML resolution than for rendered key-face contrast. The reported bug is visual/theme-dependent, so compile-time XML resource checks alone would not catch white glyph assets on light key backgrounds.

A useful regression test or verification target should cover either:

- replacing phone-key bitmap faces with normal labels/sublabels that follow theme text colors, or
- ensuring the bitmap assets have light-theme/dark-theme variants or tinting that preserves contrast.

Android v6.1.28 reporter verification later confirmed the reported `行列10` / `電話英文鍵盤` label-visibility path. Dark/dynamic-theme and iOS/iPad visual checks remain normal release-QA scope unless a separate report appears.

## Likely root cause

Likely Android-only visual regression or legacy-asset issue: `phone.xml` / `phone_shift.xml` use static `phone_*` bitmap drawables for digit/letter key faces. Those drawables are too light for the current light key background, so the labels become effectively invisible while key codes and composing behavior remain correct.

This is consistent with the reporter's note that typing and composing still work.

## Decided approach

**Abandon the `phone_*` bitmap key faces entirely.** Redraw the phone pad with
**standard two-line text keys** and expose the alphabet/symbol alternates through
the **mini popup keyboard** instead of the current multi-tap cycle on `codes`.

- **Labels:** `keyLabel="main\nsub"`. `LIMEKeyboardBaseView` already splits a
  `keyLabel` on `\n` into a main label + sub-label ([LIMEKeyboardBaseView.java:1109](../LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEKeyboardBaseView.java#L1109)),
  both drawn as theme text colors (`keySubLabelTextColorNormal`). No bitmap glyph
  is left to be invisible, so #142 disappears by construction.
- **Alternates:** `popupKeyboard="@xml/popup_template"` + `popupCharacters="…"`
  instead of the multi-code cycle. Selection uses the sticky-tap / hold-slide
  mini popup already shipped on Android — see [MINI_POPUP_KB.md](MINI_POPUP_KB.md) §8.

This is the exact pattern already live in [phone_simple.xml](../LimeStudio/app/src/main/res/xml/phone_simple.xml)
(cal key `keyLabel="+-*/\n="` + `popupKeyboard="@xml/popup_template"`
`popupCharacters="+-*/"`).

Files to change: `phone.xml`, `phone_shift.xml`.

**Dead code removed (post-conversion cleanup).** With the popup conversion, the
phone layouts were the last users of multi-tap `codes` cycling, so:
- The multi-tap cycling logic in `PointerTracker.java` (`checkMultiTap`,
  `resetMultiTap`, `mInMultiTap`/`mTapCount`/`mLastTapTime`/`mLastSentIndex`/
  `mPreviewLabel`, the `mInMultiTap` branches in `detectAndSendKey` /
  `getPreviewText`) was deleted, along with the now-unused
  `config_multi_tap_key_timeout` integer.
- All 104 `phone_*` / `phone_*_l` bitmap drawables (`phone_0`…`phone_9`,
  `phone_cal`, `phone_left`, `phone_right` × 4 densities) were deleted — nothing
  references them anymore.

**Status: Android implemented, released, and reporter-confirmed in v6.1.28.** Android `phone.xml` /
`phone_shift.xml` were rewritten to `keyLabel="<hint>\n<digit>"` +
`popup_template`/`popupCharacters`; every `phone_*` bitmap face was removed.
`./gradlew :app:processDebugResources` (with `--rerun-tasks`) passed, so AAPT
accepted the `\n` sub-labels and `&quot;`/`&amp;` entities. Reporter `gontera`
confirmed Android v6.1.28 restores the `電話英文鍵盤` layout behavior in
https://github.com/lime-ime/limeime/issues/142#issuecomment-4925621248.

**iOS/iPad source parity is implemented but remains release-QA scope.**
`phone.json` / `phone_shift.json` were rewritten to match the new layout design
(see the iOS + iPad section below), but no separate iOS/iPad reporter validation
is recorded for this Android-reported issue.

**Follow-up fix (flint preview stuck).** Because the letter/symbol keys now open
popups, the mini keyboard's own key-preview bubble (e.g. "Y") could be left
orphaned on screen after a flint/slide commit: it is normally hidden by the mini
keyboard's `onUpEvent`, but a flint commits via one touch stream while the
container is torn down by another. Fixed at the single teardown chokepoint —
`dismissPopupKeyboard()` now calls `mMiniKeyboard.dismissKeyPreview()` before
dismissing the container. This is a separate popup-teardown hardening note from
the reporter-confirmed #142 label-visibility fix.

## Target layout

Columns 2–4 are the phone pad; each letter/symbol key shows a text label and
opens a mini popup for its alternates (no more multi-tap cycling). Column 1
(`123` / `ABC`·`中` / Shift / done) and column 5 (delete / `=` cal / space /
return) keep their existing function keys.

| Row·Col | Primary (code) | Popup alternates | Replaces (old codes) |
|---------|----------------|------------------|----------------------|
| R1 C2 | `1` (49) | `( ) ' "` | digit-only |
| R1 C3 | `2` (50) | `a b c` | multi-tap `2abc` |
| R1 C4 | `3` (51) | `d e f` | multi-tap `3def` |
| R2 C2 | `4` (52) | `g h i` | multi-tap `4ghi` |
| R2 C3 | `5` (53) | `j k l` | multi-tap `5jkl` |
| R2 C4 | `6` (54) | `m n o` | multi-tap `6mno` |
| R3 C2 | `7` (55) | `p q r s` | multi-tap `7pqrs` |
| R3 C3 | `8` (56) | `t u v` | multi-tap `8tuv` |
| R3 C4 | `9` (57) | `w x y z` | multi-tap `9wxyz` |
| R4 C2 (left of 0) | `*` (42) | `< > ^ ~` | `( ) [ ]` |
| R4 C3 (0) | `0` (48) | `. , ? !` | `0 ~ ^ { }` |
| R4 C4 (right of 0) | `#` (35) | `@ $ % &` | `, ; ? \ .` |

The bottom row is `*` `0` `#` — a real phone keypad. The face shows the
phone-pad char; the extra symbols live in each key's popup.

**Symbol distribution — one category per key** (so a symbol's location is
guessable, and no symbol is reachable from two keys). Each popup is capped at
**4 alternates**, matching the 4-letter keys (`PQRS`, `WXYZ`) so the sub-label
fits the key face:

Shift is a **second symbol layer** — the face char (`1 * 0 #` / `=`) is unchanged
but the popup set differs, so the pad reaches two categories per key. Math lives
solely on the `=` (cal) key, so **no symbol is reachable from two keys**:

| Key | Unshifted | Shift |
|-----|-----------|-------|
| `1` | `( ) ' "` — round brackets & quotes | `[ ] { }` — square & curly brackets |
| `*` | `< > ^ ~` — compare / logic | `: ; _ \|` — marks & separators |
| `0` | `. , ? !` — sentence punctuation | `. , ? !` — (same; no 2nd set) |
| `#` | `@ $ % &` — web & currency | `£ € ¥ ¢` — foreign currency |
| `=` (cal) | `+ - * /` — math | `+ - * /` — (same; math) |

`0` and `=` repeat across layers because there is no second punctuation / math
group; the only symbols left unplaced are `\` and `` ` ``, which stay on the
`123` layer.

- **Function keys are unchanged.** Column 1 (`123` / `ABC`·`中` / **Shift** /
  done) and column 5 (delete / `=` cal / **space** / return) keep their current
  keys and icons. Shift and space stay exactly as they are today.
- Letter faces show the uppercase hint (`ABC`) as the sub-label; the popup emits
  lowercase in `phone.xml` and uppercase in `phone_shift.xml`, following Shift.
- **`phone_shift.xml` = capital letters + second symbol layer.** Versus
  `phone.xml`: every alphabet popup emits **uppercase** (`ABC`…`WXYZ`), column 1
  row 2 is the `中` mode key (code `-9`) in place of `ABC`, and the symbol popups
  carry their **shift** set — `1`(`[]{}`), `*`(`:;_|`), `#`(`£€¥¢`). The `0`
  (`.,?!`) and `=` cal (`+-*/`) keys and the face chars (`1 * 0 #`) are the same
  on both layers.

### Resolved layout decisions

- **Cal `=` key = the math key.** The column-5 cal key (face `=`, popup `+ - * /`,
  text label `+-*/\n=`) now owns arithmetic exclusively — the `*` pad key was
  moved off math to `< > ^ ~`, so nothing is reachable from two keys. Its old
  `phone_cal` bitmap is gone. No longer redundant; keep it.
- **Off the pad.** With both layers, only `\` and `` ` `` are not reachable from
  the pad; they stay on the `123` symbol layer.

## Follow-up questions

No reporter clarification remains for the original Android report. The reporter confirmed v6.1.28 fixed the `行列10` / `電話英文鍵盤` label-visibility problem.

If a future report appears for another theme, iOS/iPad, or another phone-keyboard layout, treat it as a new focused follow-up and ask for the exact LIME keyboard theme, platform/build, and screenshot or recording.

## Platform impact

### Android

Confirmed reported platform. The root path was Android-specific XML layout and bitmap drawable rendering in `phone.xml` / `phone_shift.xml`; v6.1.28 reporter confirmation verifies the reported Android `行列10` / `電話英文鍵盤` path.

### iOS + iPad

**Source-side parity implemented; release QA remains separate.** `LimeIME-iOS/LimeKeyboard/Layouts/phone.json` and
`phone_shift.json` were rewritten to match the Android design:

- Letter keys converted from multi-tap `codes` cycling to `popupKeyboard`
  (`@xml/popup_template` + `popupCharacters`); a plain tap types the digit, the
  popup gives the letters.
- The same base/shift symbol distribution as Android (`1`,`*`,`0`,`#`,`=`).
- **`label`/`sublabel` un-reverted.** iOS draws a tall key as *label small (top) /
  sublabel large (bottom)* ([KeyboardView.swift:1057](../LimeIME-iOS/LimeKeyboard/KeyboardView.swift#L1057)),
  so to show the digit/face big like Android, `label` now holds the **hint**
  (letters / popup chars) and `sublabel` holds the **face** char. (Previously
  swapped — letters rendered large, digit small.)

**iPad shares these files.** There is no `phone_ipad.json`; `LayoutLoader`
resolves `phone_ipad`→`phone`, so iPad loads the same `phone.json` /
`phone_shift.json`. No separate iPad phone layout exists or is needed.

## Verification result

Android/GitHub Release v6.1.28 contained the targeted phone-key label visibility fix. `limeimetw` posted the reporter retest request on 2026-07-08:

- https://github.com/lime-ime/limeime/issues/142#issuecomment-4917008967

Reporter `gontera` confirmed on 2026-07-09 that after updating to v6.1.28, the `電話英文鍵盤` layout works normally:

- https://github.com/lime-ime/limeime/issues/142#issuecomment-4925621248

`limeimetw` acknowledged the confirmation and closed the issue as completed:

- https://github.com/lime-ime/limeime/issues/142#issuecomment-4925638168

Verified scope: Android `行列10` with `電話英文鍵盤` on v6.1.28. The iOS/iPad parity changes remain source-side/release-QA scope unless a separate iOS report appears.
