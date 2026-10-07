# Issue #181: iPad narrow Chinese-mode layouts show the wrong language key

## Status

- Issue: https://github.com/lime-ime/limeime/issues/181
- Pull request: https://github.com/lime-ime/limeime/pull/183
- Classification: bug, usability, iPad narrow layout generation
- Platform: iOS iPad layouts only. Phone JSON and Android XML resources are unchanged.
- State: PR #183 merged and issue #181 closed. Source-fixed and publicly delivered in iOS v6.1.35 build 22, with rendered full/narrow iPad device validation still pending.
- Origin and closeout: non-private maintainer/project-account defect tracker. Preserve its accepted source closure. No GitHub reporter-retest request or seven-day wait applies, and closure does not prove rendered runtime resolution.
- Final PR head: `dc8fa0159903b60137daf1ebd775d8db539382e1`
- Merge commit: `bc05231f3d17f8300e30c7c84e5f5fe94bfa63ee`

## Correct layout categories

1. **Chinese IM layouts and their number/shift pages** use `code = -9`, labelled `EN` or `abc`, to enter English mode. They must not use `code = -10` / `中`.
2. **English-mode layouts** (`lime_english*`, `lime_abc*`, `lime_email*`, and `lime_url*`) use `code = -10`, labelled `中`, to return to the active Chinese IM.
3. **`symbols1` / `symbols2` / `symbols3` are neutral symbol overlays, not Chinese IM layouts.** Their full and narrow iPad resources are intentionally unchanged by this fix:
   - `code = -2`, labelled `abc`
   - `code = -10`, labelled `中`
   - no `code = -9`

Android symbol XML provides the same two exit codes but labels `code = -2` as `EN`. The iPad `abc` label is intentional platform presentation and must not be normalized to Android's text.

## Confirmed affected layouts

Exactly five narrow Chinese-mode layouts were incorrect:

| Layout | Before | Correct |
| --- | --- | --- |
| `lime_number_ipad_narrow` | `-10` / `中` | `-9` / `abc` |
| `lime_number_ipad_narrow_shift` | `-10` / `中` | `-9` / `abc` |
| `lime_number_symbol_ipad_narrow` | `-10` / `中` | `-9` / `abc` |
| `lime_number_symbol_ipad_narrow_shift` | `-10` / `中` | `-9` / `abc` |
| `lime_shift_ipad_narrow` | `-10` / `中` | `-9` / `abc` |

Their full iPad siblings already use `-9`, confirming a narrow-trimming defect.

## Root cause

`scripts/trim_ipad_layout.py` listed `lime_number`, `lime_number_symbol`, and `lime_shift` in `ENGLISH_BASES`. Narrow generation therefore replaced their valid Chinese-mode `-9` key with `-10` / `中`.

## Fix

- Remove `lime_number`, `lime_number_symbol`, and `lime_shift` from `ENGLISH_BASES`.
- Regenerate the five affected narrow Chinese-mode layouts with `-9` / `abc`.
- Keep every full/narrow `symbols1`, `symbols2`, and `symbols3` resource byte-for-byte unchanged.
- Keep all widths, geometry, key ordering, and symbols unchanged.

## Regression test

`scripts/test_ipad_language_mode_key.py` scans every committed `*_ipad*.json` resource and enforces:

1. Chinese IM, number, and shift layouts have exactly one `-9` language switch and no `中` modifier.
2. English-mode layouts have exactly one `-10` / `中` language switch.
3. Symbol overlays retain the intended iPad pair `-2` / `abc` plus `-10` / `中`, contain no `-9`, and preserve Android's two exit codes.

## Verification performed

- Corrected RED test failed on all six symbol resources while PR #183 changed their intended `abc` labels.
- The merged tree is identical to the final PR head for this change.
- `scripts/test_ipad_language_mode_key.py`: four tests pass across all 84 iPad resources.
- `scripts/test_number_symbol_layout_ios.py`: six tests pass on the merged tree.
- `scripts/test_build_emoji_db.py`: six tests pass on the merged tree.
- All 137 layout JSON files parse.
- Generator rerun is deterministic.
- Final generated-resource diff contains exactly the five narrow Chinese-mode layouts.
- `git diff --check` passes.
- Source merge is complete. Historical Xcode Cloud TEST/ARCHIVE checks and overall workflow status pass on v6.1.35 candidate `5eaa5953afaa328dbbb4faedfd758ea65ed8bbf2`. Separately, App Store delivery of v6.1.35 build 22 is verified. Rendered full/narrow iPad physical-device verification remains pending.

## Evidence reconciliation — 2026-10-07

- Merge `bc05231f3d17f8300e30c7c84e5f5fe94bfa63ee` is contained in `origin/master`, `v6.1.35`, and `v6.1.38`. Current `lime_number_ipad_narrow.json` retains `-9` / `abc`. Do not reimplement this accepted correction under #177 or #181.
- Live GitHub check runs and overall workflow status on v6.1.35 candidate `5eaa5953afaa328dbbb4faedfd758ea65ed8bbf2` retain successful Xcode Cloud TEST and ARCHIVE evidence for historical run `1837ed7f-85be-43ec-9cbb-1d5bb68e8b84`. Apple currently returns 404 for the historical run and empty product/workflow run collections, so this is retained exact-candidate evidence, not a fresh action-level Apple readback.
- App Store Connect records v6.1.35 as `READY_FOR_SALE` / `READY_FOR_DISTRIBUTION`, attached to build 22 (`945e8c3d-bb34-47d1-b613-0a98a12d87df`, `VALID`, `APP_STORE_ELIGIBLE`). The former waiting-for-review wording is historical, not the current delivery state. Current v6.1.38 is independently visible through the Taiwan public lookup and attached to valid eligible build 1 in App Store Connect.
- Linux contracts on documentation base `0d1d47eb994f313b31284a0e00fa0fe9e0e22908` passed: custom IM (12), all-iPad mode keys (4), and number/symbol layouts (6). They do not replace rendered device tests.
- No new executable head is introduced by this documentation-only reconciliation. The historical native pass is not an outstanding unexecuted gate and is not generalized to later executable changes. Keep canonical `fix#181` in `Fixed — pending validation or delivery` for rendered full/narrow iPad checks, preserving neutral symbol-overlay behavior and keeping #177's custom-IM workflow separate.
