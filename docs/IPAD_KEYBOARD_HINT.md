# iPad Keyboard Popup Hint Keys

This document defines the iPad popup-hint metadata work. It is about the
`popupKeyboard` / `popupCharacters` metadata that makes the key show the small
`...` popup hint and opens an alternate popup on long press.

It is not about iPad dual-row slide keys. Dual-row keys use `longPressCode`
and labels like `!\n1`; those rules stay in [IPAD_KEYBOARD.md](IPAD_KEYBOARD.md).

## Scope

The iPad layout generators must preserve popup metadata for real popup keys
when the corresponding key survives in the generated iPad layout.

No Swift or Java change is expected:

- Swift already renders the `...` hint when `popupKeyboard` is non-empty.
- Android XML and phone JSON already carry the source popup metadata.
- The missing piece is the Python iPad generators dropping or clearing metadata.

## Exclusions

Do not add popup metadata for these cases:

- `EN`, `ABC`, `中`, `中文`, `.?123`, `123`, or other mode/modifier keys.
- `123 -> phone_simple` long press. iPad intentionally does not use that popup.
- Phone keypad layouts (`phone.json`, `phone_shift.json`, `phone_number.json`,
  `phone_simple.json`). There are no `phone*_ipad.json` files; iPad falls back
  to the same phone JSON.
- ET41 digit/root-row popup encodings, such as `- 5` and `= 6`. The iPad
  generator turns those into visible primary iPad top-row keys.
- EZ digit/root-row popup encodings, such as the special digit row `1` through
  `6` alternates. They are root/digit encoding data, not iPad popup hints.
- HS/EZ/ET41 digit/root-row special encodings for the same reason.

Do not exclude ordinary punctuation popup keys just because a layout is
shifted or special. Punctuation keys like `.`, `>`, and `;` are real popup
hint keys.

## Expected Popup-Hint Keys

The following source popup keys should be represented in generated iPad layouts
where that layout is generated and the physical key remains present.

### English

- `lime_english`: `e y u i o a s c n`, `.`
- `lime_english_shift`: `E Y U I O A S C N`, `>`
- `lime_english_number`: `e y u i o a s c n`, `.`
- `lime_english_number_shift`: `E Y U I O A S C N`, `>`

### ABC And Number

- `lime_abc`: `.`
- `lime_abc_shift`: `;`
- `lime_number`: `.`

### Chinese IM

- `lime_phonetic`: `.`
- `lime_phonetic_shift`: `>`
- `lime_array`: `.`
- `lime_array_shift`: `>`
- `lime_array_number`: `.`
- `lime_array_number_shift`: `>`
- `lime_cj`: `.`
- `lime_cj_shift`: `;`
- `lime_cj_number`: `.`
- `lime_cj_number_shift`: `;`
- `lime_dayi`: `.`
- `lime_dayi_shift`: `>`
- `lime_dayi_sym`: `.`
- `lime_dayi_sym_shift`: `>`
- `lime_et26`: `.`
- `lime_hsu`: `.`

### Symbols

- `symbols1`: `.`

### Special/Hand-Authored iPad Layouts

`lime_ez_ipad*.json`, `lime_hs_ipad*.json`, and `lime_wb_ipad*.json` are
special hand-authored or excluded-from-generator cases. They still follow the
same popup rule for ordinary punctuation keys:

- `lime_ez`: `.`
- `lime_ez_shift`: `>`
- `lime_hs`: `.`
- `lime_hs_shift`: `.`
- `lime_wb_shift`: `;`

Do not preserve their digit/root-row encoding popups:

- `lime_ez`: the special digit row `1` through `6` alternates.
- `lime_hs`: digit/root-row special encodings.
- `lime_wb`: any future digit/root-row special encodings.

## Generator Ownership

- `scripts/convert_keyboard_layouts.py`: Android XML to phone JSON. It should
  keep phone JSON exact and preserve phone popup metadata.
- `scripts/generate_ipad_layouts.py`: English, ABC, symbols, and misc full iPad
  layouts. It must copy approved popup metadata from phone/source layouts into
  generated full iPad layouts.
- `scripts/build_ipad_layouts.py`: generated Chinese IM full iPad layouts. It
  must preserve approved punctuation popup metadata when punctuation keys are
  transformed or promoted.
- `scripts/trim_ipad_layout.py`: full iPad to narrow iPad. It should preserve
  popup metadata from full layouts. It should not need behavior changes unless
  the checker proves it drops metadata.

## Gate Criteria

This task is complete only when both checks pass.

### 1. Exact Generation Check

Command:

```bash
python3 .Codex/scripts/check_layout_generation_exact.py
```

Pass criteria:

- The scripts regenerate the checked-in phone `.json`, full `_ipad.json`, and
  `_ipad_narrow.json` files byte-for-byte.
- No layout file is added or deleted by generation.
- This check must pass before the hint-key work starts, and again after the
  generated iPad JSON with popup metadata is checked in.

### 2. Popup-Hint Delta Check

Command:

```bash
python3 .Codex/scripts/check_ipad_popup_hint_delta.py
```

The checker compares a pre-hint baseline against the candidate regenerated
iPad JSON.

Pass criteria:

- The file set is unchanged.
- Row order and key order are unchanged.
- Every key is the same except for approved popup metadata.
- The only fields allowed to change are:
  - `popupKeyboard`
  - `popupCharacters`
- Those fields may change only on approved keys from this document.
- Approved keys are matched by layout id, row index, key index, `code`, and
  `label` from the baseline, so a moved or rewritten key does not pass by
  accident.
- The new popup metadata must match the source phone layout metadata for that
  key, unless this document explicitly defines a generated punctuation
  equivalent.
- Narrow iPad layouts must preserve the same popup metadata as their full iPad
  source layout after `trim_ipad_layout.py` runs.

Fail criteria:

- Any non-popup field changes, including:
  - `code`
  - `codes`
  - `label`
  - `sublabel`
  - `widthPercent`
  - `icon`
  - `isModifier`
  - `isRepeatable`
  - `isSticky`
  - `longPressCode`
- Any popup metadata appears on excluded keys such as `123`, `EN`, ET41
  digit/root-row encodings, or EZ digit/root-row encodings.
- Any approved key is missing its expected `popupKeyboard`.
- Any unapproved key gains, loses, or changes `popupKeyboard` or
  `popupCharacters`.
- Any full iPad popup metadata is lost in the corresponding narrow iPad layout.
