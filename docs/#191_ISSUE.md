# Issue #191: iOS Hsu English keyboard renders the symbol layout

## Status

- Issue: https://github.com/lime-ime/limeime/issues/191
- Classification: confirmed iOS bug
- Reporter: `Poul9`
- Android: reporter-confirmed working and used as the behavioral oracle
- iOS: source fix merged to `master` in PR #192 as `9a50eb7509e937c2e2e51574ddcdc9d91b1369ee`
- Delivery boundary: iOS v6.1.36 build 25 passed the required Xcode Cloud test/archive actions on the release commit, is App Store eligible, and is submitted for review. It is not publicly released yet.
- Pending: App Store approval/public rollout, reporter confirmation, ETEN26 verification, and full/narrow iPad runtime verification

## Problem statement

When the iOS Phonetic input method is configured as `許氏（英文）`, the visible keyboard remains the Hsu symbol-face layout instead of changing to the English-face layout. The reporter confirms that the equivalent Android setting works normally.

## Reported reproduction

1. On iOS, open the Phonetic input-method settings.
2. Select `許氏（英文）`.
3. Open or return to the LIME keyboard.
4. Observe that the visible keyboard is still the symbol-face Hsu keyboard.

Expected: the Phonetic IM remains active for Hsu composition, but the visible keys use the configured generic English-face keyboard. Newly selected English variants seed the 5-row `limenum` keyboard, while the 4-row `lime` keyboard remains selectable in the per-IM keyboard picker.

## Evidence and root cause

The defect is confirmed in the iOS layout-resolution source.

Before the final cross-platform seed change, Settings persisted different keyboard codes for the two Hsu choices in `LimeIME-iOS/LimeSettings/Views/IMDetailView.swift`:

- `hsu` (`許氏（英文）`) -> `lime` or `limenum`
- `hsu_symbol` (`許氏（符號）`) -> `hsu`

Android preserves this distinction in `LimeDB.resolvePhoneticKeyboardCode()` and in the Settings mapping. That matches the reporter's working Android result.

Before the fix, iOS overrode the persisted distinction in `KeyboardViewController.phoneticSpecialLayoutId(for:)`. The `kbType.hasPrefix("hsu")` branch routed both `hsu` and `hsu_symbol` directly to `lime_hsu`, so `resolvedLayoutId(for:)` never reached the English `lime`/`limenum` keyboard code for the reported selection.

The same over-broad helper also routed both `eten26` and `eten26_symbol` to `lime_et26`, despite Settings and Android distinguishing the English and symbol variants. No separate ETEN26 runtime report is available, but the source-level routing defect was identical.

## Existing test coverage and why it missed the bug

`KeyboardViewControllerTest.testPhoneticSpecialLayoutIdMapsEt41AndSiblingsFromPref` explicitly expected both Hsu variants to resolve to `lime_hsu` and both ETEN26 variants to resolve to `lime_et26`. That expectation preserved the pre-existing inline behavior during issue #156's ETEN 41-key refactor, but it encoded the incorrect English/symbol conflation instead of checking the Settings and Android contract.

There was no regression test requiring English Hsu/ETEN26 choices to fall through to their persisted generic keyboard code.

## Implemented solution

PR #192 keeps direct preference routing only where the visible layout can be selected unambiguously:

- `et_41` / `eten` -> `lime_et_41`
- `eten26_symbol` / legacy `et26` -> `lime_et26`
- `hsu_symbol` -> `lime_hsu`
- English `eten26` and `hsu` -> no special override, allowing `resolvedLayoutId(for:)` to resolve the persisted `lime`/`limenum` keyboard selection

The resolver correction is iOS-only. It preserves Hsu/ETEN26 composition remapping, which continues to use `phonetic_keyboard_type`, and changes only the visible layout routing. The merged PR also contains the documented cross-platform product change that seeds `limenum` when an English Hsu/ETEN26 type is selected.

## Follow-on defects found during runtime verification

Two additional defects surfaced once the resolver correction made the English variants depend on the persisted keyboard code. Both were fixed in the merged PR.

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

Completed before merge:

1. Focused RED/GREEN source-level coverage proves that English Hsu/ETEN26 variants do not route to the symbol layouts, while explicit symbol variants still do.
2. The XCTest resolver contract covers persisted English layouts and the dedicated symbol/ETEN 41-key layouts.
3. The focused phonetic, custom-IM, and number/symbol Python regression suites pass.
4. The full `LimeTests/KeyboardViewControllerTest` XCTest gate passes on an iPhone simulator.
5. Android `:app:compileDebugJavaWithJavac` passes for the cross-platform seed change.

### iOS runtime

On iPhone, full iPad, and narrow iPad:

1. Select `許氏（英文）`. Confirm it seeds the five-row English face and Hsu composition still produces candidates. Then manually select the four-row LIME keyboard and confirm that persisted choice also renders correctly.
2. Select `許氏（符號）`. Confirm the dedicated Hsu symbol-face layout still appears and composes normally.
3. Repeat the equivalent English/symbol checks for ETEN26 because the same routing branch was corrected.
4. Switch away and back, then close/reopen the keyboard, and confirm each selection persists.

The maintainer verified the corrected Hsu English face on iPhone on first show and after dismiss/reopen. Full and narrow iPad checks remain pending. Request reporter verification only after a newer iOS build containing the fix is available.

## Platform impact

### iOS

Confirmed affected by the reporter and by direct source inspection. The routing fix is iOS-only and is merged to `master`. Maintainer iPhone verification passed. iOS v6.1.36 build 25 passed the required Xcode Cloud actions and is submitted for App Store review; both iPad layout tiers, public rollout, and reporter confirmation remain pending.

### Android

Reporter-confirmed unaffected by the routing defect: Android already distinguishes English and symbol keyboard codes in both Settings and runtime resolution, and it reads the keyboard code column (never the kv desc), so neither follow-on defect applies. Android remained the behavioral oracle for this issue.

One deliberate product change is applied to both platforms together (not a defect fix): the 英文 variants now always seed the 5-row `limenum` keyboard instead of switching on `number_row_in_english` — modern phones fit five rows, and the 4-row LIME keyboard stays selectable through the per-IM keyboard picker. Android sites: `LimeDB.resolvePhoneticKeyboardCode()`, `LIMEPreference.onSharedPreferenceChanged()`, `ImDetailFragment.applyPhoneticKeyboardType()`. iOS site: `IMDetailView.updatePhoneticKeyboard()`. The default/fallback arms (`getFallbackDefaultKeyboardCode`, imported-IM defaults) are intentionally unchanged. Existing installs keep their persisted keyboard until the phonetic type is re-picked.
