# Issue #228: iPad Dayi semicolon key does not enter the 虫 root

## Current status

- Issue: https://github.com/lime-ime/limeime/issues/228 (closed automatically when the fix merged)
- Classification: confirmed iOS/iPadOS source defect; fix confirmed on physical iPad and accepted by the maintainer
- Affected layouts: Dayi English-face keyboard on full-width and narrow iPad
- Android: not affected by the source defect
- Delivery: source merged to `master` in PR #229 as merge commit `696ecbaaff5fdc47d0b94baca5c8f5cacdcf10d4` and shipped in the public Taiwan App Store version 6.1.38 build 1
- Closeout: the reporter did not respond to the post-shipment retest request by the seven-day deadline of 2026-08-24 13:10:09 UTC+8. The auto-closed issue remains completed, and its validation watch is retired without a redundant GitHub comment or close action.

## Problem statement

The reporter says that the semicolon-position key on the iPad Dayi English-face keyboard cannot enter the `虫` root. The attached 2224 × 815 image highlights that key on a wide iPad keyboard.

Expected behavior: while Dayi is active in Chinese composing mode, tapping the key should send ASCII code `59` (`;`). Dayi metadata maps that code to the composing root name `虫`, and the table should return candidates whose code begins with `;`.

Actual source behavior: both unshifted iPad Dayi English-face resources send full-width colon code `65306` and expose full-width semicolon code `65307` as the secondary action. Neither code is the Dayi root code, so this position cannot enter `虫`.

## Reproduction

1. On iPad, activate Dayi in Chinese mode with the English-face `lime_dayi` keyboard selection.
2. Use either the full-width or narrow iPad layout.
3. Tap the semicolon-position key on the home row.
4. Observe that Dayi root code `;` (`虫`) is not entered.

The failure is source-reproducible from both affected resources. After the resource/generator fix, the maintainer confirmed the corrected behavior on a physical iPad.

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

`scripts/test_dayi_ipad_semicolon.py` provides a Linux-executable generator/resource contract. It regenerates the expected full and narrow Dayi layouts in memory, requires the committed JSON to match those outputs, and verifies the semantic key contract.

`KeyboardViewControllerTest.testDayiIPadAlphabetLayoutsKeepSemicolonAsRootKeyWithoutDualSlide` provides the corresponding native XCTest coverage. Both regressions require:

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

## Verification results

Completed:

1. Focused resource contract against `origin/master`: **RED**, both affected layouts had zero code-`59` keys.
2. Same focused resource contract after the fix: **GREEN**.
3. `scripts/build_ipad_layouts.py` regenerated all 24 full iPad layouts and left the worktree clean.
4. The layout diff gate confirmed that only `lime_dayi_ipad.json` and `lime_dayi_ipad_narrow.json` changed.
5. Dayi Shift and Dayi root-face full/narrow resources are byte-identical to `origin/master`.
6. `scripts/test_dayi_ipad_semicolon.py`: **2 tests passed**.
7. `scripts/test_number_symbol_layout_ios.py`: **6 tests passed**.
8. `scripts/test_ipad_language_mode_key.py`: **4 tests passed**.
9. `scripts/test_custom_im_keyboard_ios.py`: **12 tests passed**.
10. Every iOS layout JSON file decoded successfully; `git diff --check` and independent Codex review passed.
11. The maintainer confirmed the bug fixed on a physical iPad and accepted PR #229 for merge.
12. PR #229 merged to `master` as `696ecbaaff5fdc47d0b94baca5c8f5cacdcf10d4`; the focused 2/6/4/12 Python suites, all-layout JSON decode, generator-cleanliness check, and `git diff --check` passed again on the merge commit.

Native XCTest was not available on this Linux host. The maintainer accepted the PR based on the focused generator/resource gates, independent review, and successful physical-iPad verification.

## Delivery follow-up

1. The source fix is merged and `Fixes #228` closed the community issue automatically.
2. Jeremy selected locally uploaded App Store build `6.1.38(1)` after the fix merged and replaced build 0 with build 1 plus the #228 release note.
3. App Store Connect identifies build 1 as the selected `READY_FOR_SALE`, `VALID`, `APP_STORE_ELIGIBLE` build. The Taiwan storefront publishes version 6.1.38 with the #228 release note.
4. This locally uploaded build has no Xcode Cloud `sourceCommit.commitSha` relationship. Shipment attribution therefore follows the maintainer-confirmed post-merge build chronology rather than an API-attributed archive SHA.
5. A targeted post-shipment retest request was posted on 2026-08-17 at 13:10:09 UTC+8. The reporter did not respond by the seven-day deadline of 2026-08-24 at 13:10:09 UTC+8. Under the community auto-closed bug procedure, the existing closed state is preserved and the validation watch is complete.

## Public response and follow-up

- Routine acknowledgement: https://github.com/lime-ime/limeime/issues/228#issuecomment-5238071606
- Confirmed root cause and fix scope: https://github.com/lime-ime/limeime/issues/228#issuecomment-5238238350
- Post-shipment retest request: https://github.com/lime-ime/limeime/issues/228#issuecomment-5312101586
- No additional public closeout comment was posted because the fixing merge had already closed the issue.
