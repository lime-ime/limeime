# Issue #177: iOS custom IM does not refresh its layout and cannot switch to English

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/177
- Classification: `bug`, `Type-Defect`, `Usability`
- State: open, confirmed by source inspection
- Fix scope: iOS only. Android provides the expected reference behavior.
- PR #178 was closed unmerged because its dedicated `lime_custom*` layout family diverged from the established Android custom-IM model. A minimal Android-parity replacement is pending.

## Problem statement

After importing a custom CIN table, switching from another LIME internal input method such as Array 10 to `custom` can leave the previous input method's visible keyboard layout on screen. Closing and reopening the keyboard then displays a usable default layout.

The seeded custom layout can also present a `中` mode key rather than an English-switch key, leaving no path from custom Chinese composition to the English keyboard.

## Root cause

The iOS custom-IM defaults are inconsistent with Android in two places.

1. `LimeIME-iOS/Shared/Database/LimeDB.swift` keeps `defaultKeyboardCodeForImportedIM()` in parity with Android, but its switch omits `custom`. The custom import therefore falls through to `lime` instead of Android's `limenum`. iOS has no loadable `lime.json`, so a warm switch can fail to resolve and apply the intended custom layout, leaving the previous visible layout in place.
2. `seedCustomIM()` separately inserts a new `custom` row with `keyboard = lime_abc`. `lime_abc` is an English-mode layout whose mode key uses `switchToIM` (`code: -10`, label `中`), not the Chinese-composition layout action that switches to English.

Android's `getDefaultKeyboardCodeForImportedIM()` maps both `pinyin` and `custom` to `limenum`. On iOS, the existing `limenum` keyboard configuration resolves Chinese composition to `lime_number` / `lime_number_shift`, whose mode key uses `switchToEnglish` (`code: -9`). These existing resources provide the required behavior without a dedicated custom layout family.

## Proposed solution

1. Add `custom` to the iOS imported-IM default mapping and return `limenum`, matching Android.
2. Seed newly created custom IM rows with `limenum` instead of `lime_abc`.
3. Repair only known legacy/default custom values that this bug produced, including `lime` and `lime_abc`, while preserving any other keyboard configuration explicitly chosen by the user.
4. Apply the repair when the existing custom IM is next resolved or selected, not only during a fresh import, so existing installations recover immediately without re-importing their CIN table.
5. Reuse the current `lime_number` layout family for phone, iPad, and iPad-narrow modes. Do not add `lime_custom*` resources.

## Follow-up questions

- Which existing-user boundary should perform the one-time repair so that both a warm keyboard extension and the next explicit import receive the corrected metadata?
- Can the repair distinguish all known invalid defaults from a user-selected `lime_abc` value without overwriting an intentional choice? If not, migration scope must remain limited to values that can be identified safely.
- Does changing the persisted custom keyboard configuration publish an IM-change notification early enough for an already-running extension to refresh `activatedIMs` before applying the layout?

## Verification plan

### iOS

- Add a regression test proving `defaultKeyboardCodeForImportedIM("custom") == "limenum"`.
- Test a fresh custom import and confirm the persisted custom IM keyboard code is `limenum`.
- Test existing custom rows containing each known bad value (`lime` and `lime_abc`) and confirm they migrate to `limenum`.
- Test an existing custom row with another valid user-selected keyboard and confirm it is not overwritten.
- Test Array 10 → custom, another Chinese IM → custom, custom → another IM, forward/backward cycling, and direct menu selection.
- After every switch, confirm the visible layout, active IM, SearchServer table, accepted root keys, and candidate lookup agree immediately.
- Confirm custom Chinese composition uses `lime_number` / `lime_number_shift`, switches to English, and switches back to custom composition correctly.
- Repeat on iPhone, iPad, and iPad-narrow layouts, both after a cold launch and in an already-running keyboard extension.
- Confirm a legacy installation recovers on the next switch without requiring a custom-table re-import.

### Android

- No source change is expected.
- Keep the existing `custom → limenum` mapping covered and verify custom import, internal-IM switching, and Chinese/English mode toggling remain the parity reference.

## Platform impact

### iOS

Confirmed affected. Both default-assignment paths can select the wrong keyboard code, and warm switching can retain the previous visible layout when resolution fails. Existing installations need a conservative metadata repair in addition to correcting new imports.

### Android

Not affected by the identified root cause. Android already maps imported `custom` to `limenum`. Android behavior and tests should be used as the parity oracle, with no Android production-code change unless separate evidence appears.
