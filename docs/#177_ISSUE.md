# Issue #177: iOS custom IM layout is stale and cannot switch to English

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/177
- Classification: bug (`Type-Defect`, `Usability`)
- State: open
- Scope: iOS source fix implemented on `fix/177-ios-custom-layout`; Xcode and device verification remain pending

## Problem statement

After importing a custom CIN table on iOS, switching from another LIME internal input method to `custom` can retain the previous input method's visible keyboard until the keyboard extension is reopened. The custom keyboard also displays a `中` mode key and provides no direct route to the English keyboard.

## Reproduction

1. Import an alphabetic custom CIN table.
2. Activate another LIME internal input method, such as Array 10.
3. Select Custom from the LIME input-method menu.
4. Observe whether the visible keyboard updates immediately.
5. On the Custom keyboard, inspect and use the language-mode key.

Expected behavior:

- Selecting Custom immediately loads its alphabetic composing layout.
- The Custom layout provides an `EN`/`abc` key that switches to the normal English keyboard.

## Source evidence and likely root cause

`LimeDB.seedCustomIM()` registered `custom` with `lime_abc`. That layout is the English runtime layout, so its mode key is `code: -10`, label `中`, which returns to a Chinese input method rather than entering English mode. A custom CIN table is itself a Chinese composing input method and needs a separate alphabetic layout whose mode key is `switchToEnglish` (`code: -9`).

The switch handlers also used optional layout loads. If the resolved layout ID was absent from the keyboard extension bundle, they skipped `setLayout`, allowing the previous input method's keyboard to remain visible. This code structure plausibly permits the reported stale-layout behavior. The exact device sequence still requires simulator/device reproduction.

Existing installations need special handling because their `custom` row may already contain the legacy `lime_abc` value. Repairing only newly inserted rows would not affect those installations.

## Fix

1. Add a dedicated custom layout family based on the existing alphabetic geometry:
   - `lime_custom`
   - `lime_custom_shift`
   - `lime_custom_ipad`
   - `lime_custom_ipad_shift`
   - `lime_custom_ipad_narrow`
   - `lime_custom_ipad_narrow_shift`
2. Give each variant exactly one `switchToEnglish` key, labelled `EN` on phone and `abc` on iPad, while preserving the corresponding `lime_abc` key geometry.
3. Register all six JSON files in the LimeKeyboard resource build phase.
4. Seed new `custom` rows with `lime_custom` and repair existing rows whose keyboard value is `lime_abc`, empty, or null.
5. Resolve legacy in-memory `custom → lime_abc` metadata to `lime_custom` immediately, so an existing user does not need to import again before receiving the runtime fix.
6. Route forward/backward internal-IM cycling, direct menu selection, and Chinese/English mode switching through a total layout resolver. A missing preferred layout now falls back to bundled `lime_abc` rather than retaining the previous keyboard.

## Existing coverage and regression gap

The existing iOS tests covered general layout loading, input-field layout selection, database synchronization, and active-IM reconciliation. They did not assert:

- the layout assigned to a seeded custom IM;
- the language-mode key across every custom device/shift variant;
- migration of an existing custom row;
- direct and cyclic internal-IM switching when a preferred layout cannot be loaded;
- LimeKeyboard target membership for a complete custom layout family.

New XCTest source/layout contract cases and `scripts/test_custom_layout_ios.py` cover those contracts. The Python suite is runnable on Linux; behavioral XCTest and extension UI verification still require Xcode.

## Platform impact

### iOS

Confirmed report scope. The affected seed path, JSON layouts, bundle resources, and `KeyboardViewController` switch paths are iOS-specific. The fix changes iOS source and resources only.

### Android

No matching behavior is reported. Android uses separate keyboard resources and switching code, so this iOS root cause does not directly apply and no Android source change is included. Android custom import and Chinese/English switching should still receive a parity smoke test during coordinated QA.

## Verification plan

### Automated

- Parse all six custom layouts.
- Verify each mirrors the matching `lime_abc` geometry except for layout ID and mode key.
- Verify exactly one `switchToEnglish` key and no `switchToIM` key in every custom variant.
- Verify every custom resource is in the LimeKeyboard Resources build phase.
- Verify new-row seeding and scoped legacy-row repair.
- Verify legacy runtime metadata resolves to `lime_custom`.
- Verify cyclic switching, direct menu switching, and Chinese/English switching use safe layout resolution.
- Run existing Linux-runnable layout/resource regression scripts.
- Run focused XCTest on macOS/Xcode.

### Simulator/device

- Test another IM → Custom and Custom → another IM in both directions.
- Test direct menu selection and next/previous cycling.
- Confirm visible layout, active table, root-key input, and candidate lookup immediately after each switch.
- Switch Custom → English → Custom and continue composing.
- Test a fresh database and an upgraded database containing `custom → lime_abc`.
- Test phone, regular iPad, and iPad-narrow layouts in normal and shifted states.
- Repeat from a cold extension launch and an already-running extension.

## Follow-up questions

Device model, iOS version, and exact LIME version remain useful for final device reproduction, but they do not block the source fix or regression coverage.

## Retest condition

Request user verification only after the fix is merged and available in a newer iOS build.
