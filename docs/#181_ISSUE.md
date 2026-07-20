# Issue #181: iPad Chinese-mode layouts show a 中 key instead of the English switch key

## Status

- Issue: https://github.com/lime-ime/limeime/issues/181
- Classification: bug, usability, iPad layout generation
- Platform: iOS iPad layouts only. The generated phone (`*.json`) layouts and all Android XML resources are unchanged.
- State: fixed in the generator sources; layouts regenerated. Not yet released.

## Problem statement

The iPad keyboard bottom row carries a *language mode* key whose job is to switch to the **other** mode. Its correct value therefore depends on the mode the layout itself represents:

- A Chinese-mode layout — and any number, symbol, or shift page reached **from** Chinese mode — must offer the way out to English: **code `-9` (`C_EN`), labelled `EN` or `abc`**.
- An English-mode layout (the `lime_english*`, `lime_abc*`, `lime_email*`, and `lime_url*` families) must offer the way back to Chinese: **code `-10` (`C_IM`), labelled `中`**.

Eleven committed iPad layouts had this backwards. They were not English-mode layouts, yet they shipped a `-10`/`中` key. That language-mode key could not switch directly to English because no `-9` key was present on those pages.

### Affected layouts (all 11 confirmed by the regression test before the fix)

| Layout | Was | Should be |
| --- | --- | --- |
| `lime_number_ipad_narrow` | `-10` / `中` | `-9` / `abc` |
| `lime_number_ipad_narrow_shift` | `-10` / `中` | `-9` / `abc` |
| `lime_number_symbol_ipad_narrow` | `-10` / `中` | `-9` / `abc` |
| `lime_number_symbol_ipad_narrow_shift` | `-10` / `中` | `-9` / `abc` |
| `lime_shift_ipad_narrow` | `-10` / `中` | `-9` / `abc` |
| `symbols1_ipad` | `-10` / `中` | `-9` / `EN` |
| `symbols1_ipad_narrow` | `-10` / `中` | `-9` / `EN` |
| `symbols2_ipad` | `-10` / `中` | `-9` / `EN` |
| `symbols2_ipad_narrow` | `-10` / `中` | `-9` / `EN` |
| `symbols3_ipad` | `-10` / `中` | `-9` / `EN` |
| `symbols3_ipad_narrow` | `-10` / `中` | `-9` / `EN` |

The corresponding **full** `lime_number_ipad`, `lime_number_symbol_ipad`, and `lime_shift_ipad` layouts were already correct (`-9`), which is what makes the narrow variants unambiguously a trimming defect rather than an intended difference.

### Not the same key

Symbol pages also carry a code `-2` (`C_SYM`) key that is *labelled* `abc`. That key returns to the letter page and is **not** a language mode key. It is preserved unchanged by this fix. Only codes `-9` and `-10` are language mode keys.

## Root cause

Two independent generator defects, both in the iPad layout pipeline.

### 1. `scripts/trim_ipad_layout.py` misclassified three Chinese-reachable bases as English

`trim_ipad_layout.py` derives every `*_ipad_narrow*.json` file from the corresponding full `*_ipad*.json` layout, and replaces the bottom row wholesale via `bottom_narrow_for()`. That function starts from `BOTTOM_NARROW_ZH` (which correctly carries `-9`/`abc`) and flips the key to `-10`/`中` when the layout's base id is in `ENGLISH_BASES`.

`ENGLISH_BASES` wrongly listed `lime_number`, `lime_number_symbol`, and `lime_shift` alongside the genuine English families. These three are number/symbol/shift **pages of the Chinese keyboard**, not English-mode layouts, so the narrow variants got their correct `-9` key overwritten with `-10`/`中` — while the full variants, which never pass through this function, kept `-9`. That single misclassification accounts for 5 of the 11 bad files.

### 2. `scripts/generate_ipad_layouts.py` hard-coded `C_IM`/`中` for symbol layouts

`bottom_row()` accepted a `left_cjk=True` flag used only by `make_symbols1/2/3()`. Under that flag it emitted `mk(C_IM, label='中', ...)` in the left slot. Symbol pages are reached from Chinese mode, so the escape hatch they need is `EN`, not `中`. This produced the three bad `symbols*_ipad.json` files.

`trim_ipad_layout.py` then repeated the same mistake independently for the narrow symbol layouts: `main()` does not trim symbol layouts through `trim_layout()`, it copies the phone symbol layout and overwrites the bottom row with the module-level `SYMBOL_BOTTOM_NARROW` constant — which also hard-coded `-10`/`中`. That accounts for the remaining three files.

## Fix

Generator sources only; no generated JSON was hand-edited.

- `scripts/trim_ipad_layout.py`
  - Removed `lime_number`, `lime_number_symbol`, and `lime_shift` from `ENGLISH_BASES`, leaving only the genuine English-mode families (`lime_english`, `lime_abc`, `lime_email`, `lime_url`, `lime_english_number`). Added a comment recording the invariant.
  - Changed the language mode entry of `SYMBOL_BOTTOM_NARROW` from `-10`/`中` to `-9`/`EN`.
- `scripts/generate_ipad_layouts.py`
  - `bottom_row()` now emits `mk(C_EN, label='EN', ...)` in the left slot. The flag was renamed `left_cjk` → `left_lang` because it no longer selects a CJK key, and its comment now explains the `-2` vs `-9` distinction.

Layouts were then regenerated with `generate_ipad_layouts.py` followed by `trim_ipad_layout.py`.

## Regression test

`scripts/test_ipad_language_mode_key.py` scans **every** committed `*_ipad.json` and `*_ipad_narrow*.json` layout and asserts both directions of the invariant, so neither side can silently regress:

1. Every iPad layout has exactly one language mode key.
2. Non-English-mode layouts expose `-9` labelled `EN` or `abc`, and no `-10`/`中`.
3. English-mode layouts (`lime_english*`, `lime_abc*`, `lime_email*`, and `lime_url*`) expose `-10` labelled `中`, so users can return to Chinese.
4. The code `-2` symbol-page `abc` keys survive.

Run with `python3 scripts/test_ipad_language_mode_key.py`.

## Platform impact analysis

- **iOS:** Confirmed affected on the iPad layout resources listed above, including full-size symbol pages and narrow number, number-symbol, shift, and symbol pages. Phone JSON layouts are outside this issue's explicitly iPad-only scope and remain unchanged. Device-family verification is still required on both full-size and narrow iPad configurations because the Linux regression test validates resource semantics rather than rendered UI behavior.
- **Android:** Not affected by the two iOS iPad generator defects fixed here. Android uses XML resources and a separate symbol-page contract with both page-navigation and language-mode controls, rather than `generate_ipad_layouts.py` or `trim_ipad_layout.py`. This iPad-specific contract must not be extrapolated to Android, and the fix makes no Android source or resource change.

## Verification performed

- Regression test: 11 subtest failures before the fix (exactly the table above); all 4 tests pass after.
- Generator determinism: re-running both generators a second time produces byte-identical output — no further diff.
- JSON parsing: all 131 layout files parse.
- `scripts/test_number_symbol_layout_ios.py` (issue #160 iPad/layout contract): 6 tests pass.
- `git diff --check`: clean.
- Diff audit: the only changed lines across the 11 layouts are `code`/`codes` `-10 → -9` and `label` `中 → EN`/`abc`. No unrelated generated churn, no width, geometry, or key-set changes.
