# Issue #202: Array period key cannot select an ASCII period in Chinese mode

## Status

- Issue: https://github.com/lime-ime/limeime/issues/202
- Classification: confirmed cross-platform resource defect with Android runtime confirmation
- Android: reported behavior and affected source path are confirmed
- iOS: the same Array key and popup resources are affected in source; iPhone and iPad runtime verification is pending
- Fix state: the focused branch adds ASCII `.` to the shared Chinese punctuation popup on both platforms and adds a cross-platform resource contract test
- Delivery state: not merged or available in a release

## Problem statement

The Array keyboard's `./9⇣` key displays the ASCII period `.` as its main key label. In Chinese mode, tapping code 46 participates in LIME's composition/punctuation candidate path and selects the Chinese full stop `。` by default. Long-pressing the key opens the Chinese punctuation popup, but that popup contains `。` and the full-width period `．` without the displayed ASCII `.`. A user therefore cannot select the keyface character from this key in Chinese mode.

The `.` key is also Array root `9⇣`, so changing its tap path to bypass composition would risk breaking valid Array codes. The narrow fix preserves the existing tap/composition behavior and makes ASCII `.` directly selectable from the existing long-press popup.

## Reported reproduction

1. Activate the Array input method in Chinese mode.
2. Tap the `./9⇣` key and observe that the resulting punctuation is `。` rather than the displayed ASCII `.`.
3. Long-press the same key.
4. Observe that the popup offers `。` and `．`, but not ASCII `.`.

Expected: ASCII `.` is available from the displayed key without switching to English mode, while Array `9⇣` composition and existing Chinese punctuation remain available.

## Evidence

### Android

- `LimeStudio/app/src/main/res/xml/lime_array.xml` defines the Array key as code `46`, label `.`, sublabel `9⇣`, with `@xml/popup_c_punctuation`.
- `LimeStudio/app/src/main/java/org/limeime/data/ChineseSymbol.java` maps `.` to `。` in the Chinese-symbol path.
- `LimeStudio/app/src/main/res/xml/popup_c_punctuation.xml` contained `．` and `。` but no ASCII `.` before the fix.
- `LIMEService.isKeyInImkeys()` deliberately accepts comma and period as composition roots, so changing code 46 into unconditional literal output is not a safe resource-only correction.

### iOS

- `lime_array.json`, `lime_array_ipad.json`, and `lime_array_ipad_narrow.json` define the same code `46`, label `.`, sublabel `9⇣`, and Chinese punctuation popup relationship.
- `Shared/Database/LimeDB.swift` identifies `.` as Array root `9⇣` and injects `。` as a Chinese punctuation candidate for query code `.`.
- `popup_c_punctuation.json` likewise contained `．` and `。` but no ASCII `.` before the fix.
- Popup selection with code `0` commits the key label directly in `KeyboardViewController.firePopupKey`, so an ASCII `.` popup entry bypasses composition without changing the root key's tap semantics.

## Existing test coverage and why it missed the bug

Android tests cover the deliberate comma/period composition-root rule. iOS candidate tests cover the composing `.` echo and `。` punctuation candidate. Those tests protect tap/composition behavior, but neither platform had a contract asserting that the popup attached to the Array period key exposes ASCII `.` while preserving `．` and `。`.

The new `scripts/test_array_period_popup.py` check covers:

- the Android Array code-46 key's popup reference
- iOS phone, full-iPad, and narrow-iPad Array code-46 popup references
- exactly one ASCII `.` option on each platform
- preservation and matching order of `.`, `．`, and `。`

## Root cause

The keyboard and composition resources evolved around Chinese punctuation handling while retaining the ASCII root label required by Array. The shared Chinese punctuation popup omitted the ASCII character represented on the keyface. Existing tests covered composition and Chinese punctuation candidates, not the popup's literal-access contract.

## Implemented solution

Add one ASCII `.` entry immediately before `．` and `。` in both platform copies of `popup_c_punctuation`.

This preserves:

- code 46 as Array root `9⇣`
- the current tap/composition and default Chinese `。` behavior
- the existing full-width `．` and Chinese full stop `。` options
- Android/iOS period-option order

Because the popup resource is shared, other Chinese layouts that already use this punctuation popup also gain a direct ASCII-period option. No tap dispatch, candidate ranking, input-table data, or Array root mapping changes.

## Follow-up questions

No product decision is required for the narrow accessibility fix. A future change that makes a normal tap commit ASCII `.` directly would require a separate design proving that Array codes beginning with `9⇣` remain usable.

## Verification plan

### Automated

1. Preserve the pre-fix RED result: `FAIL: Android popup must contain ASCII period exactly once`.
2. Run `python3 scripts/test_array_period_popup.py` and require GREEN.
3. Parse the changed Android XML and iOS JSON resources.
4. Run feasible Android unit/compile gates and iOS tests when an Xcode environment is available.

### Android runtime

1. In Array Chinese mode, long-press `./9⇣` and select ASCII `.`.
2. Confirm the committed character is U+002E and not `。` or `．`.
3. Confirm normal tap still follows the existing Chinese punctuation/composition behavior.
4. Confirm Array codes using `9⇣` still compose normally.
5. Confirm `。` and `．` remain selectable from the popup.

### iOS runtime

Repeat the same checks on iPhone, full-size iPad layout, and narrow iPad layout. Verify the popup fits and all period options remain selectable.

Retest should be requested only after a released Android or iOS build contains the merged fix.

## Platform impact

### Android

Confirmed affected by the maintainer-created report and direct source inspection. The popup resource fix is implemented locally but still needs emulator/device verification, merge, and release delivery.

### iOS and iPad

The source has the same missing option across iPhone, full-iPad, and narrow-iPad Array layouts, so the resource defect is confirmed by parity inspection. Reporter-visible runtime behavior and popup geometry remain unverified until Xcode/device testing. The matching iOS popup resource fix is implemented locally but not merged or released.
