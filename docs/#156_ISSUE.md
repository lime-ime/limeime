# Issue #156: iOS Phonetic et41 keyboard layout still shows standard layout

## Status

- Issue: https://github.com/lime-ime/limeime/issues/156
- Classification: bug, usability
- State: open
- Assignee: `jrywu`
- Platform: iOS confirmed by maintainer-created report. Android is not reported in this issue.
- Public acknowledgement: not needed because this is an internal maintainer-created tracking issue.

## Problem statement

When the user sets the iOS Phonetic input method to the `et41` / ETEN 41-key keyboard layout, the visible keyboard still shows the standard Phonetic layout.

## Reproduction from report

1. Open LIME iOS settings.
2. Select the Phonetic / BPMF input method.
3. Set the Phonetic keyboard layout to `et41`.
4. Open the LIME iOS keyboard and switch to Phonetic.
5. The visible keyboard remains the standard Phonetic layout.

## Expected behavior

The visible Phonetic keyboard should use the selected ETEN 41-key layout after the setting is selected and persisted. If the keyboard extension is already warm, the change should be applied the next time the keyboard is shown or input starts.

## Source evidence inspected

### Settings-side selection path

- `LimeIME-iOS/LimeSettings/Views/IMDetailView.swift`
  - Lines 92-95 define the Phonetic-only picker state and options, including `et_41` with the label `倚天 41 鍵`.
  - Lines 193-204 render the Phonetic keyboard-type picker and call `updatePhoneticKeyboard(type:)` when the picker value changes.
  - Lines 493-532 map picker values to keyboard-table codes and write the Phonetic `im.keyboard` config. The `et_41` / `eten` branch maps to `targetCode = "phoneticet41"`, then calls `DBServer.shared.setImConfigKeyboard("phonetic", kb)`.
- `LimeIME-iOS/LimeSettings/Controllers/ManageImController.swift`
  - Lines 203-219 load the selected keyboard for an IM and normalize stored `im.keyboard` / `imkb` values for the Settings checkmark.
  - Lines 238-244 write a selected keyboard via `DBServer.setImConfigKeyboard(...)` and mark the keyboard cache dirty.
- `LimeIME-iOS/LimeSettings/Views/KeyboardPickerView.swift`
  - Lines 84-91 update `LIMEPreferenceManager.shared.phoneticKeyboardType = kb.code` when the generic keyboard picker is used for the Phonetic IM. That path stores the keyboard-table code, not the IMDetail picker value, so it should be checked for consistency with `et_41` / `phoneticet41` handling.

### Keyboard-extension layout resolution path

- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`
  - Lines 644-647 call `refreshPhoneticKeyboardPrefs()` on `initOnStartInput()` so Settings changes can affect a warm keyboard extension.
  - Lines 957-999 re-read `phonetic_keyboard_type` and Phonetic `im.keyboard`, update the `activatedIMs` cache, and immediately swap layouts if Phonetic is active.
  - Lines 1001-1032 resolve the visible layout. The resolver special-cases only `eten26` / `et26` to `lime_et26` and `hsu*` to `lime_hsu`. It does not explicitly special-case `et_41` / `eten`, so ETEN 41-key visibility depends on `activatedIMs[idx].keyboardId` resolving through `searchServer.getKeyboardConfig(kbCode)?.imkb` to a loadable layout.
- `LimeIME-iOS/LimeKeyboard/Layouts/` contains the expected ETEN 41-key JSON layouts: `lime_et_41.json`, shifted, iPad, and iPad narrow variants.
- `LimeIME-iOS/Shared/Database/LimeDB.swift`
  - Lines 1225-1244 include hardcoded fallback keyboard configs for `lime` and standard `phonetic`, but no hardcoded fallback for `phoneticet41` in the inspected fallback block. If the keyboard catalog row for `phoneticet41` is missing from the extension-visible database/source, ETEN 41-key can fall back to standard Phonetic layout even though the JSON layout exists.
  - Lines 1617-1621 make `checkPhoneticKeyboardSetting()` a no-op on iOS, so Android's physical-keyboard preference reconciliation path does not repair iOS mismatches.
  - Lines 1828-1887 already use `phoneticKeyboardType` to remap ETEN 41-key input codes (`et_41` / `eten`). That means the lookup/remapping side may use ETEN semantics while the visible layout still remains standard if layout resolution is stale.

### Existing test coverage

- `LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift` includes layout-fixture coverage for `lime_et_41` and shifted/iPad variants, but the inspected tests do not directly gate that choosing Phonetic `et_41` resolves the active visible layout to `lime_et_41` instead of `lime_phonetic`.
- Existing preference tests cover `LIMEPreferenceManager.phoneticKeyboardType` round-tripping, but do not cover the full Settings picker → Phonetic `im.keyboard` → keyboard-extension visible layout chain.

## Likely root cause / investigation hypothesis

The maintainer report is consistent with a split between the Phonetic keyboard-type preference used for remapping and the visible keyboard layout selected by the keyboard extension.

Possible failure points, in priority order:

1. `phoneticet41` is missing, disabled, or has an unexpected `imkb` value in the keyboard config source visible to the iOS keyboard extension.
2. `updatePhoneticKeyboard(type:)` writes the shared preference and/or app-side IM config, but the keyboard extension reads a stale `im.keyboard` value through `DBServer.shared.getImConfig("phonetic", "keyboard")`.
3. `refreshPhoneticKeyboardPrefs()` runs, but the Phonetic entry in `activatedIMs` is not updated before `resolvedLayoutId(for:)` chooses the visible layout.
4. The ETEN 41-key path needs an explicit visible-layout resolver branch, similar to the existing ETEN26/HSU branches, so `et_41` / `eten` can resolve to `lime_et_41` even when the DB keyboard config route is incomplete.

## Proposed fix / investigation plan

1. Verify the iOS keyboard config source contains `phoneticet41` and that its `imkb` points to `lime_et_41` or the correct ETEN 41-key layout family.
2. Add regression coverage that selecting Phonetic `et_41` / `eten` resolves the visible iOS layout away from standard Phonetic and toward the ETEN 41-key layout.
3. Ensure `refreshPhoneticKeyboardPrefs()` updates both the remap preference and the visible layout cache before the layout is applied.
4. If the keyboard config source cannot reliably provide the visible layout, add an explicit `et_41` / `eten` resolver path in `resolvedLayoutId(for:)` that loads the ETEN 41-key layout.
5. Check the generic `KeyboardPickerView` Phonetic path so writing `phoneticKeyboardType = kb.code` does not leave the IMDetail picker value and visible layout state inconsistent.

## Verification plan

### iOS

1. Add focused tests for Phonetic layout resolution:
   - `standard` resolves to the standard Phonetic layout.
   - `et_41` / `eten` resolves to the ETEN 41-key layout, not standard.
   - Existing `eten26` and `hsu` behavior remains unchanged.
2. Test the app setting flow so choosing `et41` writes both the persisted preference and the Phonetic `im.keyboard` config expected by the keyboard extension.
3. On an iOS simulator/device, choose Phonetic `et41`, open the keyboard, and confirm the visible keys match ETEN 41-key layout.
4. Switch away and back, or close/reopen the keyboard, and confirm the selected layout persists.

### Android

No Android source change or APK retest is indicated by the current report. Android can be used only as a reference for the intended Phonetic keyboard routing.

## Platform impact

### iOS

Affected. The likely fix is in the Settings → shared preference / DB config write path, keyboard extension layout refresh path, or visible layout resolver.

### Android

Not in scope unless later evidence shows the same setting mismatch on Android. The existing Android preference routing uses a different Java settings/service path and is not implicated by the inspected iOS source.

## Follow-up / release state

- Keep issue #156 open until the iOS source fix is implemented and verified in a TestFlight/App Store build.
- No public retest request is needed now because the issue is maintainer-created.
- `docs/BACKLOG.md` should track `fix#156 iOS` while implementation remains pending.
