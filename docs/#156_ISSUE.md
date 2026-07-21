# Issue #156: iOS Phonetic et41 keyboard layout still shows standard layout

## Status

- Issue: https://github.com/lime-ime/limeime/issues/156
- Classification: bug, usability
- State: fixed in iOS v6.1.31; maintainer tracker closed
- Assignee: `jrywu`
- Platform: iOS only. Android is **not** affected (confirmed — see Root cause).
- Public acknowledgement: not needed; this is an internal maintainer-created tracking issue.

## Problem statement

When the user sets the iOS Phonetic input method to the `et41` / ETEN 41-key keyboard layout, the visible keyboard still shows the standard Phonetic layout.

## Reproduction from report

1. Open LIME iOS settings.
2. Select the Phonetic / BPMF input method.
3. Set the Phonetic keyboard layout to `倚天 41 鍵` (`et_41`).
4. Open the LIME iOS keyboard and switch to Phonetic.
5. The visible keyboard remains the standard Phonetic layout.

## Expected behavior

The visible Phonetic keyboard should use the selected ETEN 41-key layout after the setting is selected and persisted. If the keyboard extension is already warm, the change should be applied the next time the keyboard is shown or input starts.

## Root cause (confirmed)

**Not a database problem.** The keyboard catalog row is present and correct on *both* platforms:

```
keyboard.code = phoneticet41  →  imkb = lime_et_41
```

This row exists in Android's source-of-truth `LimeStudio/app/src/main/res/raw/lime.db` and in every iOS build-product `lime.db`. Because the catalog is shared/ported, a missing-row cause would break Android identically — and Android works. So the earlier "missing `phoneticet41` DB row" hypothesis is disproven, and `lime_et_41.json` (plus shifted/iPad variants) exists in `LimeIME-iOS/LimeKeyboard/Layouts/`.

**The cause is an iOS-only layout-resolution gap.** The two platforms resolve the visible phonetic layout differently:

- **Android** — `LimeDB.resolvePhoneticKeyboardCode()` reads the `phonetic_keyboard_type` preference *directly* and maps it to a keyboard code (`et_41`/`eten` → `phoneticet41`), then `LIMEKeyboardSwitcher` uses `kConfig.getImkb()` (= `lime_et_41`) generically. **One source of truth: the preference, recomputed on every keyboard show.**
- **iOS** — `KeyboardViewController.resolvedLayoutId(for:)` had explicit preference-driven branches only for `eten26` → `lime_et26` and `hsu` → `lime_hsu`. **`et_41` was omitted.** It therefore fell through to the *persisted `im.keyboard` config* path (`activatedIMs[idx].keyboardId` → `getKeyboardConfig(...)?.imkb`), a **second source of truth** that must be written by Settings (`setImConfigKeyboard`) and refreshed into the in-memory cache (`refreshPhoneticKeyboardPrefs()`) before it takes effect. When that round-trip is stale, resolution falls back to `lime_<tableNick>` = `lime_phonetic` — the standard layout.

For the #156 ETEN 41-key failure, `et_41` was the only affected type whose visible layout depended on the fragile config round-trip instead of direct preference routing. The sibling routing was outside that issue's reported scope and was preserved unchanged at the time.

## Fix landed for #156 (historical)

`LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`

The v6.1.31 fix added a pure helper `phoneticSpecialLayoutId(for:)` and consolidated the then-existing routing:

- `et_41` / `eten` → `lime_et_41`  ← the fix for #156
- `eten26*` / `et26` → `lime_et26`
- `hsu*` → `lime_hsu`
- otherwise → `nil` (standard; resolved via the keyboard-config path)

That change made ETEN 41-key preference-driven and left the prior ETEN26/HSU behavior unchanged.

## Subsequent English/symbol correction

Issue #191 later confirmed that the preserved prefix routing incorrectly conflated the English and symbol variants. The #191 change keeps direct preference routing for `et_41`/`eten`, `eten26_symbol`/legacy `et26`, and `hsu_symbol`, while English `eten26` and `hsu` use the persisted `lime`/`limenum` layout selected by Settings. The Settings-side write and `refreshPhoneticKeyboardPrefs()` therefore remain part of visible-layout resolution for those English variants. See `docs/#191_ISSUE.md`.

## Test coverage added for #156

`LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift`

- `testPhoneticSpecialLayoutIdMapsEt41AndSiblingsFromPref` — asserts `et_41`/`eten` → `lime_et_41`, `eten26`/`eten26_symbol` → `lime_et26`, `hsu`/`hsu_symbol` → `lime_hsu`, and `standard` → `nil` (proves standard is not misrouted to ETEN 41-key). Pure mapping test, no `Bundle`/`LayoutLoader` dependency, so it is deterministic in headless logic-test runs.

Gate: `ios-gate.sh unit LimeTests/KeyboardViewControllerTest/testPhoneticSpecialLayoutIdMapsEt41AndSiblingsFromPref` → PASS.

## Verification plan

### iOS

1. Unit: the added mapping test gates `et_41` → `lime_et_41` and that `eten26` / `hsu` / `standard` are unchanged. (Done — PASS.)
2. Simulator/device: choose Phonetic `倚天 41 鍵`, open the keyboard, switch to Phonetic, and confirm the visible keys match the ETEN 41-key layout (not standard). Maintainer accepted the fix for the v6.1.31 release.
3. Switch away and back, or close/reopen the keyboard, and confirm the selected layout persists.
4. Sanity-check the other phonetic types (`標準` / `倚天 26 鍵` / `許氏`) still resolve to their previous layouts.

### Android

No Android source change or APK retest is indicated. Android is confirmed unaffected and served as the parity reference for the fix.

## Resolution / release state

- Fix commit: `ccc99b30` (`#156 fix iOS Phonetic et41 visible layout to derive from pref`).
- Included in the published iOS-only GitHub release v6.1.31 and App Store build 10.
- Closed as a maintainer-created tracker after the maintainer accepted the v6.1.31 fix.
- Removed `fix#156 iOS` from `docs/BACKLOG.md`.
