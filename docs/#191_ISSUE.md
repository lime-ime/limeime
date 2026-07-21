# Issue #191: iOS Hsu English keyboard renders the symbol layout

## Status

- Issue: https://github.com/lime-ime/limeime/issues/191
- Classification: confirmed iOS bug
- Reporter: `Poul9`
- Android: reporter-confirmed working and used as the behavioral oracle
- iOS: source fix prepared; device and TestFlight/App Store verification remain pending

## Problem statement

When the iOS Phonetic input method is configured as `許氏（英文）`, the visible keyboard remains the Hsu symbol-face layout instead of changing to the English-face layout. The reporter confirms that the equivalent Android setting works normally.

## Reported reproduction

1. On iOS, open the Phonetic input-method settings.
2. Select `許氏（英文）`.
3. Open or return to the LIME keyboard.
4. Observe that the visible keyboard is still the symbol-face Hsu keyboard.

Expected: the Phonetic IM remains active for Hsu composition, but the visible keys use the configured generic English-face keyboard, with or without the number row according to `number_row_in_english`.

## Evidence and root cause

The defect is confirmed in the iOS layout-resolution source.

Settings correctly persists different keyboard codes for the two Hsu choices in `LimeIME-iOS/LimeSettings/Views/IMDetailView.swift`:

- `hsu` (`許氏（英文）`) -> `lime` or `limenum`
- `hsu_symbol` (`許氏（符號）`) -> `hsu`

Android preserves this distinction in `LimeDB.resolvePhoneticKeyboardCode()` and in the Settings mapping. That matches the reporter's working Android result.

iOS then overrides the persisted distinction in `KeyboardViewController.phoneticSpecialLayoutId(for:)`. The current `kbType.hasPrefix("hsu")` branch routes both `hsu` and `hsu_symbol` directly to `lime_hsu`, so `resolvedLayoutId(for:)` never reaches the English `lime`/`limenum` keyboard code for the reported selection.

The same over-broad helper also routes both `eten26` and `eten26_symbol` to `lime_et26`, despite Settings and Android distinguishing the English and symbol variants. No separate ETEN26 runtime report is available, but the source-level routing defect is identical.

## Existing test coverage and why it missed the bug

`KeyboardViewControllerTest.testPhoneticSpecialLayoutIdMapsEt41AndSiblingsFromPref` explicitly expected both Hsu variants to resolve to `lime_hsu` and both ETEN26 variants to resolve to `lime_et26`. That expectation preserved the pre-existing inline behavior during issue #156's ETEN 41-key refactor, but it encoded the incorrect English/symbol conflation instead of checking the Settings and Android contract.

There was no regression test requiring English Hsu/ETEN26 choices to fall through to their persisted generic keyboard code.

## Proposed solution

Keep direct preference routing only where the visible layout can be selected unambiguously:

- `et_41` / `eten` -> `lime_et_41`
- `eten26_symbol` / legacy `et26` -> `lime_et26`
- `hsu_symbol` -> `lime_hsu`
- English `eten26` and `hsu` -> no special override, allowing `resolvedLayoutId(for:)` to resolve the persisted `lime`/`limenum` keyboard selection

This is a narrow iOS-only correction. It preserves Hsu/ETEN26 composition remapping, which continues to use `phonetic_keyboard_type`, and changes only the visible layout routing.

## Follow-on defects found during runtime verification

Two additional defects surfaced once the resolver correction made the English variants depend on the persisted keyboard code. Both are fixed in the same branch.

### 1. Number-row-off dead end: no `lime` layout on iOS

With `number_row_in_english` off, Settings persists keyboard code `lime`, whose `imkb` is `lime` — a layout iOS never ported from Android's `lime.xml` (the same gap #177 routed around for custom imports). Resolution silently degraded to `lime_number` via `chineseLayoutCandidates`, so the number row showed regardless of the toggle, and the pickable "LIME 預設鍵盤" catalog row was equally unusable.

Fix: port `lime.json` / `lime_ipad.json` / `lime_ipad_narrow.json` (Chinese-IM face: lowercase letters, one `-9` EN key, never `-10`; base = `lime_number*` minus the phone number row). The pre-existing `lime_shift_ipad(_narrow).json` files were renamed to `lime_ipad(_narrow)_shift.json` — `LayoutLoader` composes `<base>_ipad_shift`, so the old names were unreachable — and all six files are registered in the LimeKeyboard Resources phase.

### 2. Warm re-show clobber: refresh compared the kv `desc`, not the keyboard code

Reported as "first popup shows the correct layout, re-show shows phonetic standard." `setIMConfigKeyboard` stores the kv row as `desc` = human description (e.g. `LIME+數字列鍵盤`) and `keyboard` = the code (`limenum`). `refreshPhoneticKeyboardPrefs` read the value back via `getImConfig("phonetic", "keyboard")`, which returns `desc` — the description never equals the cached `keyboardId`, so every warm re-show clobbered the cache with the description, and resolution dead-ended to `lime_phonetic`. Cold first shows escaped because `initOnStartInput` runs before `setupDatabase` populates `activatedIMs`, so the refresh bailed early. The bug was latent pre-fix: prefix-routed variants never consulted the persisted code, and for `standard` the dead end coincidentally resolved to the intended `lime_phonetic`. Android reads the keyboard column (`getKeyboard()`), never the desc.

Fix: read the code column — `getImConfigList("phonetic", "keyboard").first?.keyboard` — guarded by a RED/GREEN source regression in `scripts/test_phonetic_layout_ios.py`.

## Follow-up questions

No reporter clarification is required for source correction. Runtime verification should confirm whether the problem appears immediately after changing the setting and after closing/reopening the keyboard extension.

## Verification plan

### Automated

1. Add a focused RED/GREEN source-level regression proving that English Hsu/ETEN26 variants are not routed to the symbol layouts, while explicit symbol variants remain routed there.
2. Update the XCTest resolver contract so `hsu` and `eten26` retain their persisted layouts, while `hsu_symbol`, `eten26_symbol`, `et26`, and ETEN 41-key select their intended dedicated layouts.
3. Run the focused source-level regression and the existing custom-IM iOS routing checks.
4. Run the focused XCTest/Xcode gate on macOS/Xcode when available.

### iOS runtime

On iPhone, full iPad, and narrow iPad:

1. Select `許氏（英文）` with the English number row enabled and disabled. Confirm the visible keyboard uses the corresponding English-face layout while Hsu composition still produces candidates.
2. Select `許氏（符號）`. Confirm the dedicated Hsu symbol-face layout still appears and composes normally.
3. Repeat the equivalent English/symbol checks for ETEN26 because the same routing branch was corrected.
4. Switch away and back, then close/reopen the keyboard, and confirm each selection persists.

Request reporter verification only after a newer iOS build containing the fix is available.

## Platform impact

### iOS

Confirmed affected by the reporter and by direct source inspection. The fix is iOS-only. Phone and both iPad layout tiers require runtime verification because layout selection later applies device-family variants.

### Android

Reporter-confirmed unaffected. Android already distinguishes English and symbol keyboard codes in both Settings and runtime resolution, so no Android source change is needed. Android remains the behavioral oracle for this issue.
