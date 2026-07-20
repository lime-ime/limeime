# Issue #181: iPad language-mode and symbol exit keys diverge from their layout modes

## Status

- Issue: https://github.com/lime-ime/limeime/issues/181
- Classification: bug, usability, iPad layout generation
- Platform: iOS iPad layouts only. Phone JSON and Android XML resources are unchanged.
- State: corrected on a clean replacement branch after Android parity review. Not yet released.

## Correct layout categories

The resources are not one two-way category. There are three distinct modes:

1. **Chinese IM layouts and their number/shift pages** use `code = -9`, labelled `EN` or `abc`, to enter English mode. They must not use `code = -10` / `中`.
2. **English-mode layouts** (`lime_english*`, `lime_abc*`, `lime_email*`, and `lime_url*`) use `code = -10`, labelled `中`, to return to the active Chinese IM.
3. **`symbols1` / `symbols2` / `symbols3` are neutral symbol overlays, not Chinese IM layouts.** Following Android `symbols1.xml`, `symbols2.xml`, and `symbols3.xml`, they keep both explicit exits:
   - `code = -2`, labelled `EN`, exits symbol mode to English.
   - `code = -10`, labelled `中`, exits symbol mode to the active Chinese IM.
   - They must not be forced through the Chinese-layout `code = -9` invariant.

## Confirmed affected layouts

### Five narrow Chinese-mode layouts

| Layout | Before | Correct |
| --- | --- | --- |
| `lime_number_ipad_narrow` | `-10` / `中` | `-9` / `abc` |
| `lime_number_ipad_narrow_shift` | `-10` / `中` | `-9` / `abc` |
| `lime_number_symbol_ipad_narrow` | `-10` / `中` | `-9` / `abc` |
| `lime_number_symbol_ipad_narrow_shift` | `-10` / `中` | `-9` / `abc` |
| `lime_shift_ipad_narrow` | `-10` / `中` | `-9` / `abc` |

Their corresponding full iPad layouts already use `-9`, confirming a narrow-trimming defect.

### Six symbol overlays

The full and narrow `symbols1`, `symbols2`, and `symbols3` resources already carried the two required exit codes, but their `code = -2` exit was labelled `abc` instead of Android's `EN`. PR #182 initially over-applied the Chinese-layout rule by replacing their `code = -10` / `中` exit with `code = -9` / `EN`. That was incorrect because symbol overlays are not Chinese IM layouts.

The corrected resources now match Android semantics:

| Exit | Code | Label |
| --- | --- | --- |
| English | `-2` | `EN` |
| Active Chinese IM | `-10` | `中` |

## Root cause

`scripts/trim_ipad_layout.py` listed `lime_number`, `lime_number_symbol`, and `lime_shift` in `ENGLISH_BASES`. Narrow generation therefore replaced their valid Chinese-mode `-9` key with `-10` / `中`.

The symbol overlay contract was initially misclassified during the fix. Android source inspection confirms that symbol pages are neutral overlays with separate `EN` and `中` exits. The generator and regression test now encode that third category explicitly.

## Fix

- Remove `lime_number`, `lime_number_symbol`, and `lime_shift` from `ENGLISH_BASES`.
- Regenerate the five affected narrow Chinese-mode layouts with `-9` / `abc`.
- Preserve `-10` / `中` in all six full/narrow symbol overlays.
- Label each symbol overlay's existing `code = -2` exit `EN`, matching Android.
- Keep all widths, geometry, key ordering, and symbol characters unchanged.

## Regression test

`scripts/test_ipad_language_mode_key.py` scans every committed `*_ipad*.json` resource and enforces all three categories:

1. Chinese IM, number, and shift layouts have exactly one `-9` language switch and no `中` modifier.
2. English-mode layouts have exactly one `-10` / `中` language switch.
3. Symbol overlays have `-2` / `EN` plus `-10` / `中`, and no `-9`.

Run with:

```bash
python3 scripts/test_ipad_language_mode_key.py
```

## Verification performed

- Corrected RED test fails on the six over-corrected symbol resources before the follow-up.
- Corrected regression suite passes all four tests.
- `scripts/test_number_symbol_layout_ios.py`: six tests pass.
- `scripts/test_build_emoji_db.py`: six tests pass.
- All layout JSON files parse.
- Generator rerun is deterministic.
- `git diff --check` passes.
- Xcode/XCTest and rendered iPad verification remain pending.
