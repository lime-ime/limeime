# Issue #156: iOS Phonetic et41 keyboard layout still shows standard layout

## Status

- Issue: https://github.com/lime-ime/limeime/issues/156
- Classification: bug, usability
- State: open
- Assignee: `jrywu`
- Platform: iOS confirmed by maintainer report. Android is not reported in this issue.

## Problem statement

When the user sets the iOS Phonetic input method to the `et41` keyboard layout, the visible keyboard still shows the standard Phonetic layout.

## Reproduction from report

1. Open LIME iOS settings.
2. Select the Phonetic / BPMF input method.
3. Set the Phonetic keyboard layout to `et41`.
4. Open the LIME iOS keyboard and switch to Phonetic.
5. The visible keyboard remains the standard Phonetic layout.

## Expected behavior

The visible Phonetic keyboard should use the selected ETEN 41-key layout after the setting is selected and persisted. If the keyboard extension is already warm, the change should be applied the next time the keyboard is shown or input starts.

## Source evidence

`LimeIME-iOS/LimeSettings/Views/IMDetailView.swift` maps the Phonetic picker value to the keyboard-table code:

- `standard` → `phonetic`
- `et_41` / `eten` → `phoneticet41`
- `eten26` → `limenum` or `lime`
- `eten26_symbol` → `et26`
- `hsu` → `limenum` or `lime`
- `hsu_symbol` → `hsu`

`updatePhoneticKeyboard(type:)` writes `im.keyboard` for `phonetic` through `DBServer.shared.setImConfigKeyboard("phonetic", kb)`.

`LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift` has `refreshPhoneticKeyboardPrefs()` which re-reads:

1. `phonetic_keyboard_type` from shared defaults, for remapping/imkeys behavior.
2. The phonetic `im.keyboard` value from `DBServer.shared.getImConfig("phonetic", "keyboard")`, for visible layout behavior.

`resolvedLayoutId(for:)` then special-cases only some phonetic keyboard types:

- `eten26` / `et26` → `lime_et26`
- `hsu*` → `lime_hsu`

It does not explicitly special-case `et_41` / `eten`. The visible layout should therefore depend on the DB keyboard config for `phoneticet41` resolving to a loadable `imkb`. If that config is missing from the keyboard extension snapshot, resolves to the standard layout, or is not refreshed into `activatedIMs`, the UI will remain standard.

## Likely root cause / investigation hypothesis

High confidence: the setting path updates at least the picker preference, but the keyboard extension is still resolving the visible phonetic layout to the standard layout. Possible failure points:

1. The app writes `phonetic_keyboard_type` as `et_41`, but the DB-side `im.keyboard` update to `phoneticet41` does not reach the keyboard extension snapshot.
2. `phoneticet41` exists as a keyboard config but its `imkb` points to the same standard layout or to an unloaded layout ID.
3. `refreshPhoneticKeyboardPrefs()` does not run at the right time, or the active `activatedIMs` cache is not updated before `resolvedLayoutId(for:)` picks the layout.
4. The ETEN 41-key layout needs a direct resolver branch, similar to the existing ETEN26/HSU branches, if the DB config cannot reliably provide the layout ID.

## Proposed fix

- Verify the iOS keyboard config row for `phoneticet41` exists and points to the correct `imkb` layout.
- Add regression coverage that selecting `et_41` / `eten` resolves the visible phonetic layout away from the standard layout.
- Ensure `refreshPhoneticKeyboardPrefs()` updates both the remap preference and the visible layout cache before applying the layout.
- If the DB config path is unreliable, add an explicit `et_41` / `eten` resolver path in `resolvedLayoutId(for:)` that loads the correct ETEN 41-key layout.

## Verification plan

### iOS

1. Add focused tests for Phonetic layout resolution:
   - `standard` resolves to the standard Phonetic layout.
   - `et_41` / `eten` resolves to the ETEN 41-key layout, not standard.
   - Existing `eten26` and `hsu` behavior remains unchanged.
2. Test the app setting flow so choosing `et41` writes both the persisted preference and the phonetic `im.keyboard` config expected by the keyboard extension.
3. On an iOS simulator/device, choose Phonetic `et41`, open the keyboard, and confirm the visible keys match ETEN 41-key layout.
4. Switch away and back, or close/reopen the keyboard, and confirm the selected layout persists.

### Android

No Android source change is indicated. Android parity may be used as a reference for the intended Phonetic keyboard routing only.

## Platform impact

- iOS: affected. The fix is likely in the Settings → shared preference / DB config write path, keyboard extension layout refresh path, or visible layout resolver.
- Android: not in scope unless later evidence shows the same setting mismatch.
