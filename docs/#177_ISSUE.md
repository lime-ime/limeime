# Issue #177: iOS custom IM does not refresh its layout and cannot switch to English

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/177
- Classification: `bug`, `Type-Defect`, `Usability`
- State: closed, source-fixed and publicly delivered, with physical-device validation still pending. PR #180 and the iPad-narrow follow-up from PR #183 are included in iOS v6.1.35 build 22. Historical Xcode Cloud verification applies to that release candidate, not to every later head.
- Closeout: project-account defect tracker, not a community-created bug report. Preserve the existing closed state. No GitHub reporter-retest request or seven-day waiting period applies.
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
- PR #183 subsequently corrected the five affected iPad-narrow Chinese-mode resources to provide the required `abc` English switch while preserving the symbol overlays. The source acceptance criteria are complete.

## Remaining verification

- [x] Correct the iPad-narrow fallback so every selected `lime_number` variant provides a working English switch.
- [x] Add a semantic assertion that requires the correct mode key across committed iPad layout variants.
- [x] Run iOS tests and archive through Xcode Cloud on v6.1.35 candidate `5eaa5953afaa328dbbb4faedfd758ea65ed8bbf2`. GitHub retains both successful action checks and overall workflow status. This is separate from the App Store build-22 delivery readback.
- [x] Verify App Store delivery of v6.1.35 build 22. App Store Connect now records it as `READY_FOR_SALE` / `READY_FOR_DISTRIBUTION`, with build `945e8c3d-bb34-47d1-b613-0a98a12d87df` (`VALID`, `APP_STORE_ELIGIBLE`).
- [ ] Complete physical-device verification of the reported custom-IM path. Do not treat source acceptance, CI success, public delivery, or issue closure as runtime-resolution proof.
- Verify on iPhone and iPad:
  - another IM → custom switches the layout immediately
  - custom → another IM switches back immediately
  - forward and backward cyclic switching
  - direct menu switching
  - custom Chinese composition → English → custom composition
  - fresh and upgraded databases
  - user-selected custom keyboard layouts remain preserved

## Evidence reconciliation — 2026-10-07

- Both accepted fixing merges are ancestors of `origin/master`, `v6.1.35`, and `v6.1.38`. PR #183's merge is `bc05231f3d17f8300e30c7c84e5f5fe94bfa63ee`. The narrow English-switch source gap found immediately after PR #180 was corrected by that separate follow-up, not left as new implementation work under #177.
- Live GitHub check runs on the v6.1.35 candidate `5eaa5953afaa328dbbb4faedfd758ea65ed8bbf2` retain successful Xcode Cloud TEST and ARCHIVE results and a successful overall workflow status, linked to historical run `1837ed7f-85be-43ec-9cbb-1d5bb68e8b84`. Apple currently returns 404 for that historical run and empty product/workflow run collections. Preserve the historical pass with its GitHub evidence, without claiming fresh Apple action-level re-verification.
- App Store Connect confirms the delivered v6.1.35 build 22. Its current v6.1.38 version also reports `READY_FOR_SALE` with build 1, and the Taiwan public lookup independently shows v6.1.38. The GitHub release tag is source-containment evidence, not by itself the provenance of the later App Store replacement build.
- Focused Linux checks on documentation base `0d1d47eb994f313b31284a0e00fa0fe9e0e22908` passed: custom-IM contract (12), all-iPad mode-key contract (4), and number/symbol layout contract (6). These are structural checks, not rendered keyboard-extension tests.
- This reconciliation changes documentation only. No new executable head or candidate is introduced, so no new exact-head native gate is created by this transaction. Future executable changes retain their normal native verification requirements.
- Canonical `fix#177` belongs under `Fixed — pending validation or delivery`, retaining the custom-IM device scenarios above. Android remains the working reference and requires no source change. The resource-generation correction remains separately attributed to #181 / PR #183.
