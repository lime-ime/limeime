# Issue #177: iOS custom IM does not refresh its layout and cannot switch to English

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/177
- Classification: `bug`, `Type-Defect`, `Usability`
- State: open, confirmed plausible from a private support-email report and source inspection
- Source: private support email received 2026-07-20. The sender identity, imported table, screenshot, and recording remain private.
- Environment: iOS and current App Store LIME after an update. Exact iPhone model, iOS version, and LIME version were requested from the reporter.

## Problem statement

After importing a custom CIN table, switching from another LIME internal input method such as Array 10 to `custom` can leave the previous input method's visible keyboard layout on screen. Closing and reopening the keyboard then shows the custom IM's default layout.

The custom layout also presents a `中` mode key rather than an `abc` key, leaving the reporter without a way to switch from custom composition to the normal English keyboard.

These are related user-visible failures but may have separate causes:

1. The active table changes to `custom`, but the warm keyboard process does not immediately replace `currentLayout` with the resolved custom layout.
2. The custom IM is registered with `lime_abc`, whose current JSON uses `switchToIM` (`code: -10`, label `中`) instead of the Chinese-layout `switchToEnglish` action.

## Source evidence

### iOS custom registration and publication

- `LimeIME-iOS/LimeSettings/Views/IMInstallView.swift`
  - Successful custom `.cin`/`.lime` and database imports call `DBServer.shared.seedCustomIM()`.
- `LimeIME-iOS/Shared/Database/LimeDB.swift`
  - `seedCustomIM()` inserts `custom` with keyboard `lime_abc` only when no `custom` IM row exists.
- `LimeIME-iOS/Shared/Database/DBServer.swift`
  - `seedCustomIM()` writes through the current datasource. The import path separately publishes table and IM changes for the keyboard extension.

### iOS runtime switching

- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`
  - `switchToNextActivatedIM(forward:)` updates `activeIM`, reconfigures `SearchServer`, resolves the active layout, and calls `keyboardView.setLayout(...)` when the resolved layout differs from `currentLayout`.
  - `resolvedLayoutId(for:)` returns a directly loadable `keyboardId`, resolves a keyboard-table code through `imkb`, or falls back to `lime_<tableNick>`.
  - The report indicates that the warm switching path can retain the prior visible layout even though reopening the keyboard performs enough setup to resolve the custom layout.
- `LimeIME-iOS/LimeKeyboard/Layouts/lime_abc.json`
  - The mode key is `code: -10` with label `中`. In `KeyboardViewController`, `-10` maps to `switchToIM`, while normal Chinese layouts use the English-switch action and label `abc`.

## Relationship to earlier issues

- #119 fixed default keyboard assignment for known imported IMs. It treated the separately seeded `custom → lime_abc` path as valid, so it did not cover this runtime switch or custom mode-key behavior.
- #121 covered first activation after a cloud IM install showing a layout inconsistent with English runtime mode. This report concerns a user-imported custom IM, retention of the previous IM's layout, and the inability to enter English mode.

Issue #177 therefore remains a distinct follow-up rather than reopening either closed issue.

## Platform impact

### iOS

Confirmed plausible and user-visible. The report includes private media, but visual conclusions have not been inferred beyond the reporter's written description. Exact device/version details remain pending.

### Android

No matching report is known. Verify Android custom import, internal IM switching, and Chinese/English mode behavior for parity, but do not assume the iOS lifecycle defect applies.

## Proposed investigation

1. Add a focused test for resolving and applying `custom` after switching from a different active IM in a warm keyboard controller.
2. Capture `activeIM`, `activeIMIndex`, cached `activatedIMs.keyboardId`, resolved layout ID, and `currentLayout` before and after the switch.
3. Verify whether custom import publishes a new or updated IM row and whether a warm extension refreshes `activatedIMs` before switching.
4. Decide whether custom should use a dedicated Chinese-composition alphabet layout or whether `lime_abc` should carry `switchToEnglish` when used by `custom`.
5. Preserve normal English layout behavior and avoid changing `lime_abc` globally if it is also used as the actual English-mode layout.

## Verification plan

### iOS

- Import a sanitized alphabetic custom CIN fixture.
- Switch Array 10 → custom, another Chinese IM → custom, custom → another IM, and both forward/backward directions.
- Verify the visible layout, active table, accepted root keys, and candidate lookup immediately after every switch.
- Verify the custom layout offers a working path to English and that switching back restores custom composition.
- Repeat with a cold keyboard launch and an already-running keyboard extension.
- Test fresh installation and an existing database where `custom` metadata already exists.
- Device-test on the reporter's environment after exact versions are supplied.

### Android

- Verify custom CIN import, immediate internal-IM layout switching, and Chinese/English mode toggling.
- Keep Android out of the fix scope unless testing confirms equivalent behavior.

## Reporter follow-up

The support reply links issue #177, confirms receipt of one private recording and one image, and asks for the iPhone model, iOS version, and LIME version. The reporter may continue by email, and any result must be synchronized to the issue without exposing private identity or attachments.
