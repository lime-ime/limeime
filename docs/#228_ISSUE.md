# Issue #228: iPad Dayi semicolon key does not enter the 虫 root

## Current status

- Issue: https://github.com/lime-ime/limeime/issues/228
- Classification: confirmed iOS/iPadOS source defect, physical-device verification pending
- Affected layouts: Dayi English-face keyboard on full-width and narrow iPad
- Android: not affected by the source defect
- Reporter environment still unknown: LIME version, iPad model, iPadOS version, and host app/field

## Problem statement

The reporter says that the semicolon-position key on the iPad Dayi English-face keyboard cannot enter the `虫` root. The attached 2224 × 815 image highlights that key on a wide iPad keyboard.

Expected behavior: while Dayi is active in Chinese composing mode, tapping the key should send ASCII code `59` (`;`). Dayi metadata maps that code to the composing root name `虫`, and the table should return candidates whose code begins with `;`.

Actual source behavior: both unshifted iPad Dayi English-face resources send full-width colon code `65306` and expose full-width semicolon code `65307` as the secondary action. Neither code is the Dayi root code, so this position cannot enter `虫`.

## Reproduction

1. On iPad, activate Dayi in Chinese mode with the English-face `lime_dayi` keyboard selection.
2. Use either the full-width or narrow iPad layout.
3. Tap the semicolon-position key on the home row.
4. Observe that Dayi root code `;` (`虫`) is not entered.

The failure is source-reproducible from both affected resources. A physical-device reproduction is still required before claiming reporter-visible GREEN.

## Evidence

### Reporter evidence

- Community issue #228 reports the iPad Dayi English-keyboard behavior.
- The image identifies the semicolon-position key but does not establish the app version, iPad model, iPadOS version, host app, or exact emitted character.

### Cross-platform and device-family comparison

Working resources preserve ASCII semicolon as a Dayi composing root:

- iPhone `lime_dayi.json`: code `59`, label `;`
- Android `lime_dayi.xml`: code `59`, label `;`
- Android Dayi root-face `lime_dayi_sym.xml`: code `59`, visible `; / 虫`
- iOS Dayi root-face variants (`lime_dayi_sym*`): code `59`, visible `; / 虫`

The two failing English-face iPad resources diverge:

- `lime_dayi_ipad.json`: code `65306`, dual label `； / ：`, secondary code `65307`
- `lime_dayi_ipad_narrow.json`: the same incorrect dual full-width punctuation definition

The Dayi input contract is unambiguous:

- `LimeDB.DAYI_KEY` includes ASCII `;`.
- The corresponding `LimeDB.DAYI_CHAR` entry is `虫`.
- `Database/dayi.zip` contains an exact `; -> 虫` row and 548 rows whose codes begin with `;`.
- `acceptsIntoComposing(...)` already accepts code `59` when Dayi metadata is active.

## Root cause

The iPad generator's generic semicolon transformation replaced the working ASCII root key in unshifted `lime_dayi` with the generic iPad dual full-width punctuation key. The key remained visually recognizable as semicolon/colon, but its dispatched primary code changed from Dayi root code `59` to full-width colon code `65306`.

This is a layout-generation/resource defect, not a missing Dayi mapping, candidate-query defect, or installed metadata problem. The phone and Android resources provide the behavioral oracle.

## Why existing tests did not gate the defect

Two existing helper tests are directly relevant:

1. `testAcceptsIntoComposingUsesImportedSymbolRootsFromImkeys()` proves code `59` is accepted when present in `imkeys`.
2. `testAcceptsIntoComposingCharacterizesDayiPhoneAcceptance()` proves the standard Dayi key set accepts code `59`.

They inject code `59` directly. They do not inspect whether the iPad English-face resources dispatch that code, so they remained green while the rendered key sent `65306` instead. General layout-load coverage proves only that the JSON files decode and are packaged, not that their keys preserve the Dayi semantic contract.

## Applied fix

For both `lime_dayi_ipad.json` and `lime_dayi_ipad_narrow.json`:

- restore primary code `59`
- restore the single label `;`
- remove secondary code `65307`
- preserve the existing key position and width

`scripts/build_ipad_layouts.py` now preserves ASCII semicolon specifically for unshifted `lime_dayi`, preventing a full iPad regeneration from restoring the defect. The narrow-layout generator carries the corrected key into `lime_dayi_ipad_narrow.json`.

No controller, database, metadata, Android, iPhone, Dayi root-face, Shift, or symbol-page behavior changes.

## Regression coverage

`KeyboardViewControllerTest.testDayiIPadAlphabetLayoutsKeepSemicolonAsRootKeyWithoutDualSlide` loads both affected layouts and requires:

1. exactly one key with code `59`
2. label `;`
3. no secondary code
4. no `65306` or `65307` key in either unshifted layout

This closes the resource-to-key-code gap left by the existing acceptance-helper tests.

## Platform impact

### iOS / iPadOS

Confirmed source defect in the full-width and narrow iPad variants of the Dayi English-face layout. iPhone is not affected because `lime_dayi.json` already uses code `59`. The Dayi root-face iPad layouts are not affected because they also use code `59` with the `虫` sublabel.

### Android

Not affected. Android's corresponding Dayi English-face and root-face layouts already dispatch code `59`. Android is the behavioral oracle and needs only a parity spot-check, not a source change.

## Verification results and plan

Completed on this Linux host:

1. Focused resource contract against `origin/master`: **RED**, both affected layouts had zero code-`59` keys.
2. Same focused resource contract after the fix: **GREEN**.
3. Regenerated all 24 full iPad layouts: the correction persisted with no unrelated full-layout diff.
4. Regenerated narrow layouts: the corrected narrow Dayi key persisted. Two unrelated pre-existing non-idempotent narrow outputs were discarded from this branch.
5. `scripts/test_ipad_language_mode_key.py`: **4 tests passed**.
6. `scripts/test_custom_im_keyboard_ios.py`: **12 tests passed**.
7. Every iOS layout JSON file decoded successfully.
8. `git diff --check`: passed.

Still required:

1. Run the focused XCTest regression and full feasible iOS unit suite on macOS/Xcode.
2. On a physical iPad, verify both full-width and narrow Dayi English-face layouts in an ordinary text field.
3. Confirm tapping semicolon enters composing code `;`, displays root name `虫`, and returns Dayi candidates.
4. Round-trip Shift and symbol pages and confirm the corrected key remains intact.
5. Spot-check iPhone Dayi and Android Dayi for parity.
6. Ask the reporter to retest only after a newer iOS build contains the fix. PR acceptance is not gated on reporter response.

## Public response and follow-up

The routine acknowledgement is live at https://github.com/lime-ime/limeime/issues/228#issuecomment-5238071606. It asks for LIME version, iPad model, iPadOS version, host app, exact result after tapping, and a short recording if practical. These details remain useful for device verification but are not required to establish the source defect.
