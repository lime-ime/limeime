# Issue #177: iOS custom IM does not refresh its layout and cannot switch to English

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/177
- Classification: `bug`, `Type-Defect`, `Usability`
- State: closed automatically when PR #180 merged, but follow-up source work remains
- Platform: iOS

## Problem statement

After importing a custom CIN table, switching from another LIME internal input method such as Array 10 to `custom` can leave the previous input method's keyboard layout visible. Closing and reopening the keyboard then shows a default layout.

The custom IM can also show a `中` mode key rather than an `abc` key, leaving no direct route from custom composition to the English keyboard.

## Root cause

Android is the reference implementation. `LimeDB.getDefaultKeyboardCodeForImportedIM()` groups `DB_TABLE_CUSTOM` with `DB_TABLE_PINYIN` and assigns keyboard code `limenum`. That keyboard configuration resolves Chinese composition to the existing `lime_number` / `lime_number_shift` layouts, whose mode key switches to English.

iOS diverged in two places:

1. `defaultKeyboardCodeForImportedIM()` omitted `custom`, so a text import fell through to keyboard code `lime`. Its `imkb` value is also `lime`, but iOS has no bundled `lime.json`. Layout loading therefore failed and left the previous IM's keyboard visible.
2. `seedCustomIM()` used `lime_abc`. That is a directly loadable English-mode layout whose mode key is `中` (`-10` / `switchToIM`), not the Chinese composition layout expected for a custom IM.

## Fix design

Match Android and reuse existing iOS resources. No new keyboard layout family is needed.

- Map imported `custom` tables to keyboard code `limenum`.
- Seed fresh `custom` registrations with `limenum`.
- Repair only known invalid historical values for `custom`: `NULL`, empty string, `lime`, and `lime_abc`.
- Preserve every other keyboard value as an explicit user selection.
- Apply the same narrow repair during runtime resolution so existing users recover on the next IM switch without re-importing.
- Resolve `limenum` through the existing keyboard catalog to `lime_number` / `lime_number_shift`.
- Enforce the mode invariant globally: while a Chinese IM is active, layout loading must never
  fall back to the preference-driven English runtime layout. If the resolved Chinese layout is
  unavailable, use the bundled generic Chinese composition layout `lime_number`, whose mode key
  explicitly switches to English.

## Source evidence

### Android

- `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java`
  - `getDefaultKeyboardCodeForImportedIM()` returns `limenum` for `DB_TABLE_CUSTOM`.
  - Import stores that keyboard code through `setIMConfigKeyboard()`.
- Android keyboard catalog
  - `limenum.imkb = lime_number`
  - `limenum.imshiftkb = lime_number_shift`
- `LimeStudio/app/src/main/res/xml/lime_number.xml`
  - The Chinese composition layout exposes `EN` with code `-9`.

### iOS

- `LimeIME-iOS/Shared/Database/LimeDB.swift`
  - `defaultKeyboardCodeForImportedIM()` previously had no `custom` case.
  - `seedCustomIM()` previously inserted `lime_abc`.
  - `getKeyboardConfig("limenum")` already resolves to `lime_number` / `lime_number_shift`.
- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`
  - `resolvedLayoutId(for:)` can resolve a keyboard catalog code through `imkb`.
  - A directly loadable `lime_abc` value bypasses that catalog resolution, so legacy data must be repaired before the direct-layout check.

## Regression coverage

- `custom` import defaults to `limenum`, matching Android.
- A fresh custom registration uses `limenum`.
- Legacy `NULL`, empty, `lime`, and `lime_abc` values repair to `limenum`.
- User-selected non-default keyboard values remain unchanged.
- Runtime resolution repairs affected values before layout loading.
- `limenum` resolves to bundled `lime_number` resources with an English-switch key.
- Chinese-mode layout candidates never contain `lime_abc` or `lime_english*`. Missing Chinese
  layouts fall back only to `lime_number`, not to an English runtime layout.
- No Android files change.

## Merge verification

- PR #180 merged with final head `a2851ec46ce6b30668177f5f2291c8b4c4d24aee` as merge commit `2bc71ac9f5bba7e3ef3b5826c997b5c4bf42c229`.
- Exact merged-tree Linux checks pass: the focused custom-IM contract (12 tests), emoji database contract (6 tests), number/symbol layout contract (6 tests), Python compilation, and `git diff --check`.
- The focused contract does not cover the actual iPad size-tier resource selected by `LayoutLoader`. Small and medium iPads select `lime_number_ipad_narrow.json` or `lime_number_ipad_narrow_shift.json` first. Both expose only code `-10` with label `中`, while the phone and full-iPad variants expose code `-9` with label `EN`.
- The merged fallback therefore still does not provide the required English switch on iPad-narrow. The issue's source acceptance criteria are not complete despite the automatic GitHub closure.

## Remaining verification

- Correct the iPad-narrow fallback so every selected `lime_number` variant provides a working English switch.
- Add an independent semantic assertion that follows `LayoutLoader` device-tier selection and requires code `-9` for every fallback variant.
- Run iOS XCTest through Xcode/Xcode Cloud.
- Verify on iPhone and iPad:
  - another IM → custom switches the layout immediately
  - custom → another IM switches back immediately
  - forward and backward cyclic switching
  - direct menu switching
  - custom Chinese composition → English → custom composition
  - fresh and upgraded databases
  - user-selected custom keyboard layouts remain preserved
