# Issue #181: iPad narrow Chinese-mode layouts show the wrong language key

## Status

- Issue: https://github.com/lime-ime/limeime/issues/181
- Pull request: https://github.com/lime-ime/limeime/pull/183
- Classification: bug, usability, iPad narrow layout generation
- Platform: iOS iPad layouts only. Phone JSON and Android XML resources are unchanged.
- State: fixed on PR #183. Not yet released.

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
- Regression suite passes after restoring the symbol resources untouched.
- `scripts/test_number_symbol_layout_ios.py`: six tests pass.
- `scripts/test_build_emoji_db.py`: six tests pass.
- All layout JSON files parse.
- Generator rerun is deterministic.
- Final generated-resource diff contains exactly the five narrow Chinese-mode layouts.
- `git diff --check` passes.
- Xcode/XCTest and rendered iPad verification remain pending.
