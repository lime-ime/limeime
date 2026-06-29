# ET41 New Layout Plan

Reference: GitHub issue #137

## Goal

Adjust the ET41 phone layout so it matches the requested 倚天 41-key reference more closely.

The change is focused on Row 1, Row 4, and the bottom row. Row 2 and Row 3 must remain unchanged.

## Current Layout

Source files:

- Android: `LimeStudio/app/src/main/res/xml/lime_et_41.xml`
- iOS JSON: `LimeIME-iOS/LimeKeyboard/Layouts/lime_et_41.json`

### Row 1

```text
1/˙  2/ˊ  3/ˇ  4/ˋ  5/-  6/-  7/ㄑ  8/ㄢ  9/ㄣ  0/ㄤ
```

### Row 2

No change.

```text
q/ㄟ  w/ㄝ  e/一  r/ㄜ  t/ㄊ  y/ㄡ  u/ㄩ  i/ㄞ  o/ㄛ  p/ㄆ
```

### Row 3

No change.

```text
a/ㄚ  s/ㄙ  d/ㄉ  f/ㄈ  g/ㄐ  h/ㄏ  j/ㄖ  k/ㄎ  l/ㄌ  ;/ㄗ
```

### Row 4

```text
Shift  z/ㄠ  x/ㄨ  c/ㄒ  v/ㄍ  b/ㄅ  n/ㄋ  m/ㄇ  '/ㄘ  Delete
```

### Bottom Row

```text
KeyboardDown  EN  Space  ,/ㄓ  ./ㄔ  //ㄕ  -/ㄥ  =/ㄦ  Return
```

## Proposed Layout

### Row 1

Remove the visible `5` and `6` keys from the ET41 Chinese layout row. Move `-/ㄥ` and `=/ㄦ` from the bottom row to the right side of Row 1. Keep long-press access to `5` and `6` without adding visible hint text, because Row 1 is crowded.

```text
1/˙  2/ˊ  3/ˇ  4/ˋ  7/ㄑ  8/ㄢ  9/ㄣ  0/ㄤ  -/ㄥ  =/ㄦ
```

### Row 2

No change.

```text
q/ㄟ  w/ㄝ  e/一  r/ㄜ  t/ㄊ  y/ㄡ  u/ㄩ  i/ㄞ  o/ㄛ  p/ㄆ
```

### Row 3

No change.

```text
a/ㄚ  s/ㄙ  d/ㄉ  f/ㄈ  g/ㄐ  h/ㄏ  j/ㄖ  k/ㄎ  l/ㄌ  ;/ㄗ
```

### Row 4

Remove `Shift` from the left side of Row 4. Move `,/ㄓ`, `./ㄔ`, and `//ㄕ` from the bottom row to the right side of Row 4.

```text
z/ㄠ  x/ㄨ  c/ㄒ  v/ㄍ  b/ㄅ  n/ㄋ  m/ㄇ  ,/ㄓ  ./ㄔ  //ㄕ
```

### Bottom Row

Move `Shift` to the right of `KeyboardDown`. Move `'/ㄘ` from Row 4 to the bottom row near the right-side action keys.

```text
KeyboardDown  Shift  EN  Space  '/ㄘ  Delete  Return
```

## Move List

```text
5/- and 6/-: removed from visible ET41 Chinese layout Row 1
-/ㄥ: Bottom row -> Row 1 right side; long-press enters 5, no visible hint
=/ㄦ: Bottom row -> Row 1 right side; long-press enters 6, no visible hint
Shift: Row 4 left -> Bottom row immediately after KeyboardDown
,/ㄓ: Bottom row -> Row 4 right side
./ㄔ: Bottom row -> Row 4 right side
//ㄕ: Bottom row -> Row 4 right side
'/ㄘ: Row 4 right side -> Bottom row near Delete/Return
Delete: Row 4 right side -> Bottom row
Row 2: unchanged
Row 3: unchanged
```

## Implementation Notes

- Apply the same layout intent to Android and iOS phone ET41 layouts.
- Android source layout is `lime_et_41.xml`.
- iOS generated/runtime layout is `lime_et_41.json`.
- Check whether any generated layout scripts need the same source update before editing generated JSON directly.
- Do not change ET26, standard phonetic, Hsu, or iPad layouts unless a follow-up issue explicitly requests that.

## Validation

- Verify Row 2 and Row 3 are byte-for-byte semantically unchanged.
- Verify `ㄘ` no longer appears on Row 4.
- Verify `ㄘ` appears on the bottom row as `'/ㄘ`.
- Verify `ㄥ` and `ㄦ` appear on Row 1 as `-/ㄥ` and `=/ㄦ`, with no visible `5`/`6` hint text.
- Verify long-press on `-/ㄥ` enters `5`, and long-press on `=/ㄦ` enters `6`.
- Verify Row 4 contains `,/ㄓ`, `./ㄔ`, and `//ㄕ`.
- Visually check the ET41 layout against the image attached in issue #137.
