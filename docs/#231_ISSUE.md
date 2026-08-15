# Issue #231: iOS Easy Input Phone Popup Roots Emit Display Text Instead of Table Codes

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/231
- Classification: `bug`
- Confirmed scope: iOS iPhone Easy Input (`lime_ez`) popup construction
- Android impact: no corresponding source defect identified
- iPad impact: the direct `-` and `=` root keys do not use the affected phone popup path
- Runtime evidence: source-confirmed, with iPhone UI/device reproduction still requested

## Problem Statement

On the iPhone Easy Input layout, `儿` and `母` are exposed through long-press popups on the `1` and `2` keys. Their table query codes are `-` and `=` respectively, but selecting the displayed root can emit the displayed Chinese character rather than the code required by the Easy Input table. This prevents the selected root from participating in the intended candidate lookup.

The same encoding convention is present on the phone layout for the `3` through `6` popup roots, so those keys must be checked in the same investigation even though the live issue specifically names `儿` and `母`. Keys `3`, `5`, and `6` also have distinct display roots and table codes. Key `4` uses `]` for both, but the current scalar parser can still expose the separator characters as stray popup keys.

## Reproduction Steps

1. Enable and select Easy Input on an iPhone.
2. Enter Chinese mode.
3. Long-press `1`, then select the popup item displayed as `儿`.
4. Observe whether the composing code/candidate lookup receives `-` or the displayed `儿`.
5. Repeat with `2` and `母`, whose required table code is `=`.
6. Repeat the same check for the popup roots on `3`, `4`, `5`, and `6`.

Expected: selecting a displayed root sends its Easy Input table code and updates candidates through the normal composing path.

Observed from source: the popup creates independent keys from every Unicode scalar and dispatches the selected scalar directly, so the display root is not associated with the required table code.

## Evidence Summary

`LimeKeyboard/Layouts/lime_ez.json` encodes the first two alternates as:

```json
"popupCharacters": "-\\n儿"
"popupCharacters": "=\\n母"
```

After JSON decoding, these are literal Android-style `\\n`-separated strings. The phone layout uses the same convention for six number-row popup roots:

| Phone key | Table code prefix | Display root |
|---|---:|---|
| `1` | `-` | `儿` |
| `2` | `=` | `母` |
| `3` | `[` | `匚` |
| `4` | `]` | `]` |
| `5` | `'` | `Ｌ` |
| `6` | `\\` | `ㄏ` |

`KeyboardViewController.resolvePopupLayout(for:)` explicitly routes `popupKeyboard == "popup_template"` to `popupCharLayout(for:)`, passing the key's `popupCharacters`. That helper currently maps every Unicode scalar to `KeyDef(code: scalar, label: scalar)`. For `-\\n儿`, it creates four popup keys: `-`, `\\`, `n`, and `儿`. `firePopupKey(_:)` then inserts the selected positive key's Unicode scalar directly through `textDocumentProxy.insertText` after clearing composition. It does not preserve a distinct lookup code and display label or route an IM-root popup through candidate lookup.

The iPad Easy Input layouts expose code `45` (`-`) and code `61` (`=`) as direct keys, so the reported iPhone popup construction is the confirmed source boundary rather than a shared table-data failure.

## Existing Test Coverage And Gap

The inspected iOS suite has one nearby layout-fixture test, `testET41PopupDigitsShowOnPhoneLongPressKeyLabels`, which verifies raw `popupCharacters` metadata for another input method. Generic popup touch tests cover hit testing and gesture dispatch. None of the inspected tests exercises Android-style `code\\ndisplay` decoding into a popup `KeyDef`, verifies that a display root carries a different emitted code, or checks the Easy Input `1`/`2` user path.

The current helper is structurally fragile for any popup entry where display text differs from emitted text because its scalar-by-scalar representation cannot express that distinction.

## Likely Root Cause

The root cause is confirmed at the source boundary: iOS reused Android-originated `popupCharacters` strings without implementing their code/display separator semantics. The generic iOS helper assumes that each scalar is both the visible label and emitted character. Easy Input requires one popup item whose visible root and emitted table code differ.

Physical-device evidence is still useful to confirm the rendered popup arrangement and all affected phone keys, but it is not required to establish that the current helper cannot satisfy the layout contract.

## Proposed Solution

1. Move popup-character decoding into a focused, testable helper shared by popup layout resolution.
2. Preserve ordinary popup strings such as accented-letter lists as one key per character.
3. Recognize the Android-style escaped separator used by converted LIME layouts and create one `KeyDef` with the decoded table code plus the display root label.
4. Correctly handle an escaped backslash table code before the separator.
5. Add explicit dispatch semantics for IM-root popup keys so their positive table codes enter `onKey` or the equivalent composing/candidate path instead of using `firePopupKey(_:)`'s ordinary direct-insertion branch. Keep ordinary character alternates on the existing direct-insertion path.
6. Avoid changing iPad direct-key behavior or unrelated standard character popups.

The implementation must be driven by a failing behavioral regression before the production helper is changed.

## Follow-up Questions

- Which LIME version and iOS version first reproduced this on an iPhone?
- Does the popup visibly include stray `\\` or `n` items, or only the intended display root?
- Do the `3` through `6` Easy Input popup roots fail in the same way?

These questions refine runtime scope but do not block source correction.

## Platform Impact

### iOS

- **iPhone:** source-confirmed defect in the `lime_ez` popup path. At minimum, `儿` and `母` cannot be represented with the required distinct display label and emitted code. Keys `3`, `5`, and `6` have the same display/code divergence. Key `4` shares the separator-parsing defect even though its display and code are both `]`. All six encoded popups require regression coverage and runtime verification.
- **iPad:** no matching source defect identified for `儿` and `母` because the iPad layout provides direct code `45` and `61` keys. Verify full and narrow iPad layouts remain unchanged after any shared popup-parser change.

### Android

Android's `lime_ez.xml` uses the established XML `popupCharacters` convention from which the iOS JSON was converted. Android does not use `KeyboardViewController.popupCharLayout(for:)`, and the issue reports no Android failure. No Android source change is currently indicated. Run a focused Android Easy Input long-press smoke check to ensure the established behavior remains the parity oracle.

## Verification Plan

### Automated RED/GREEN coverage

1. Add a focused test proving that `-\\n儿` resolves to one popup key with code `45` and label `儿`.
2. Add the equivalent `=\\n母` assertion with code `61` and label `母`.
3. Add an escaped-backslash case for the `6` key.
4. Add a standard multi-character popup case such as accented letters to prove each ordinary character remains independently selectable.
5. Add an Easy Input layout contract test covering all six encoded phone popup roots and forbidding stray `\\`/`n` keys.
6. Prove selected root keys enter the composing/table lookup path instead of inserting their display labels as committed text.

### Runtime verification

- iPhone portrait: long-press `1` and `2`, select `儿` and `母`, and verify composing codes/candidates use `-` and `=`.
- iPhone portrait: verify the popup roots on `3` through `6`.
- iPhone: verify an ordinary accented-letter popup remains unchanged.
- Full and narrow iPad: verify direct Easy Input root keys still emit the expected codes.
- Android phone: smoke-test the equivalent Easy Input popups as the established behavior reference.

## Acceptance Criteria

- [ ] `儿` is displayed while emitting Easy Input code `-`.
- [ ] `母` is displayed while emitting Easy Input code `=`.
- [ ] No literal separator components appear as selectable popup keys.
- [ ] The remaining encoded Easy Input phone roots are handled correctly.
- [ ] Ordinary one-character-per-alternate popups remain unchanged.
- [ ] Popup root selection participates in candidate lookup rather than directly committing the display root.
- [ ] Full and narrow iPad direct-root behavior remains unchanged.
- [ ] Focused XCTest coverage passes, followed by simulator/device verification.
