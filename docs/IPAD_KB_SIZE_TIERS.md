# IPAD_KB_SIZE_TIERS — Per-size iPad keyboard tiers (13" / 11" / 7")

Status: PLAN — not yet implemented

Sibling docs:

- [`IPAD_KEYBOARD.md`](IPAD_KEYBOARD.md) — current iPad layout (13" only).
- [`IPAD_KB_LAYOUT_COVERTER.md`](IPAD_KB_LAYOUT_COVERTER.md) — `scripts/build_ipad_layouts.py` rules.

This document is additive: the 13" plan stands. This plan adds two
smaller tiers (iPad 11" and iPad mini) on top of it.

---

## 1. Goal

Today's `_ipad.json` files render correctly on the iPad 13" (12.9") but
the cells are visibly **too tall** on iPad 11" and especially on iPad
mini, because the same dimension constants and the same 14-cell layouts
are used on much narrower screens.

The user-facing requirement:

1. **5 rows on every iPad tier, every layout.** Apple drops to 4 rows
   on smaller iPads but that surrenders the dedicated digit row, which
   is daily-driver value for Chinese IMs that use 40+ root keys
   (Dayi, ET41).
2. **Cells must stay close to square** — not "tall" — on every tier.
   This is the actual readability constraint, more important than
   matching a specific cell count.
3. **Phone behavior bit-for-bit unchanged.** **iPad 13" behavior
   bit-for-bit unchanged** (today's shipped layout = the new `large`
   tier).
4. **No 4-row variant.** No layout family is dropped; user keeps the
   IM they have.

---

## 2. The square-cell invariant

Cell width on iPad is fundamentally tied to screen width:

```
cell_width = (printable_target / 100) × screen_width
```

With a fixed `printable_target = 7%` (see §7 for why), cell widths come
out to:

| Device | Screen width (portrait) | 7% cell width |
|---|---|---|
| iPad 13" / Pro 12.9" / Air 13" | 1024 pt | **71.7 pt** |
| iPad 11" / Pro 11 / Air 11 | 834 pt | **58.4 pt** |
| iPad mini | 744 pt | **52.1 pt** |

For cells to be **square**, `row_height ≈ cell_width`. So row height
**must be tied to screen width per tier**:

| Tier | Row height (portrait) | Resulting cell aspect |
|---|---|---|
| `.large` (iPad 13") | 72 pt | 71.7 / 72 ≈ 1.00 |
| `.medium` (iPad 11") | 58 pt | 58.4 / 58 ≈ 1.01 |
| `.small` (iPad mini) | 52 pt | 52.1 / 52 ≈ 1.00 |

**This invariant works regardless of cell count.** A 14-cell EZ row on
iPad mini gets 52×52 pt cells. An 11-cell English row on iPad mini gets
67.6×52 pt cells (wider, more comfortable). Both are usable.

Aspect ratio is solved structurally by row height, **not by trimming
cells**. Cell-count trimming becomes secondary — we trim only when it
is "free" (root-protected scaffolding cells exist), to keep modifier
widths reasonable, not to chase a specific count.

---

## 3. Three device tiers — but only for dimensions, not layouts

```swift
enum IPadSizeClass {
    case large    // SSE >= 870pt — iPad 13" / iPad Pro 12.9" / iPad Air 13"
    case medium   // 750–869pt    — iPad 11" (Pro / Air / 11)
    case small    // < 750pt      — iPad mini
}
```

Detected once per layout-rebuild from `min(screen.width, screen.height)`
(orientation-stable). Re-evaluated in `viewWillLayoutSubviews` and
`traitCollectionDidChange`. Cached on `LayoutLoader.iPadSizeClass`.

Phone path is unchanged. iPad 13" path is unchanged (`.large` returns
today's values).

---

## 4. Layout-file strategy: TWO variants, not three

Three tiers do **not** require three sets of layout files. The layout
variants are:

```
phone JSON
   │
   ▼  scripts/build_ipad_layouts.py    (existing — Chinese IMs only, 13" tier)
*_ipad.json                            (full tier — used by .large only)
   │
   ▼  scripts/trim_ipad_layout.py      (new — single trim ruleset)
*_ipad_narrow.json                    (narrow tier — used by both .medium and .small)
```

`LayoutLoader` fall-through:

```
.small  → _ipad_narrow → _ipad → bare
.medium → _ipad_narrow → _ipad → bare
.large  → _ipad → bare
phone   → bare
```

A narrow file is **optional** — layouts without one fall through to
the full `_ipad.json`. Cache key includes the resolved file name so a
phone-side cache entry can never leak.

`prefetchCommonLayouts()` should also try the narrow variant when
running on `.medium` or `.small`.

The fact that `.medium` and `.small` share the same layout file doesn't
mean they look identical: their `KeyboardView` dimension constants
differ (§5), so the narrow layout renders at 58×58 pt cells on iPad 11"
and at 52×52 pt cells on iPad mini.

---

## 5. KeyboardView / CandidateBarView dimension constants

`KeyboardView`, `CandidateBarView`, and the parts of
`KeyboardViewController` that read those constants are rewritten so each
constant is a 4-tuple `(phone, padSmall, padMedium, padLarge)` resolved
through one `IPadSizeClass.current`-aware getter.

Per-tier values (portrait — landscape resolves to the same values):

| Constant | phone | small (mini) | medium (11") | large (13") |
|---|---|---|---|---|
| `rowHeightPortrait` | 50 | **52** | **58** | **64** |
| `bottomRowHeightPortrait` | 54 | 56 | 62 | 68 |
| `keyHGap` / `keyVGap` | 5 / 2 | 5 / 3 | 6 / 3 | 7 / 4 |
| `keyCornerRadius` | 6 | 6 | 7 | 8 |
| `keySingleLabelFont` (regular) | 22 | 21 | 22 | 24 |
| `keyLabelFont` (light) | 16 | 18 | 19 | 20 |
| `keySublabelFont` (regular) | 22 | 21 | 22 | 24 |
| `baseCandidateFontSize` | 26 | 26 | 28 | 26 |
| `baseComposingCodeFontSize` | 16 | 19 | 21 | 22 |
| `candidateHPad` | 10 | 12 | 14 | 10 |
| Candidate-bar height anchor | 58 | 50 | 54 | 74 |

The small and medium row-height values are calibrated to the §2
invariant (row height ≈ 7% × screen width). The large tier keeps the
current shipped 13" constants.

**`idiomMultiplier` is deleted** — it was the prior phone × 1.5 hack and
would compound with these.

User preferences still apply on top:

- `keySizeScale` (0.8–1.2) multiplies the resolved row height.
- `font_size` pref multiplies label fonts via `fontScale`.

---

## 6. The trim ruleset (single configuration, produces `_narrow`)

`scripts/trim_ipad_layout.py` is a separate ~150-line script. It does
**not** extend `build_ipad_layouts.py`. It walks finished `_ipad.json`
files and emits `_ipad_narrow.json` siblings.

The trimmer is layout-family-agnostic. Same code path runs over Chinese
IM and English/ABC layouts — what differs per layout is the IM root set
(§6.2). Symbol narrow layouts are copied from the phone symbol pages
(§A.13), because page rotation must stay `1/3 → 2/3 → 3/3`.

### 6.1 The trim predicate

A cell is **trimmable** iff ALL of these hold:

1. Its `code` is positive (`> 0` — not a modifier like -1, -5, -9, -200).
2. `chr(code).lower()` is **not** in the layout's IM root set
   (case-folded so HS uppercase letters match).
3. Its `label` contains `\n` (the dual-slide form). Single-glyph labels
   are never scaffolding.
4. Its `popupKeyboard` is empty.

### 6.2 IM root sets

Verbatim from `LimeDB.swift` constants (Chinese IMs hard-coded), plus
three derived from per-IM `Database/<im>.db` SQLite seeds for `ez` /
`hs` / `wb` (frozen here so the trimmer never opens a DB):

```python
IM_ROOTS = {
    "lime_phonetic":      "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/-",
    "lime_cj":            "qwertyuiopasdfghjklzxcvbnm",
    "lime_cj_number":     "qwertyuiopasdfghjklzxcvbnm",
    "lime_dayi":          "1234567890qwertyuiopasdfghjkl;zxcvbnm,./",
    "lime_dayi_sym":      "1234567890qwertyuiopasdfghjkl;zxcvbnm,./",
    "lime_array":         "qazwsxedcrfvtgbyhnujmik,ol.p;/",
    "lime_array_number":  "qazwsxedcrfvtgbyhnujmik,ol.p;/",
    "lime_et26":          "qazwsxedcrfvtgbyhnujmikolp,.",
    "lime_et_41":         "abcdefghijklmnopqrstuvwxyz12347890-=;',./",
    "lime_hsu":           "azwsxedcrfvtgbyhnujmikolpq,.",
    "lime_wb":            ",./mn",
    # Derived from Database/{ez,hs}.db (2026-05 read):
    "lime_ez":            "',-./0123456789;=[\\]abcdefghijklmnopqrstuvwxyz",
    "lime_hs":            "',-./0123456789;=[\\]abcdefghijklmnopqrstuvwxyz",
    # Empty for non-IM layouts (English-family + symbols):
    "lime_english":       "",
    "lime_abc":           "",
    "lime_email":         "",
    "lime_url":           "",
    "lime_english_number": "",
    "lime_number":        "",
    "lime_shift":         "",
    "symbols1":           "",
    "symbols2":           "",
    "symbols3":           "",
}
```

Shift variants reuse the base IM's set (strip `_shift_ipad` to derive).

### 6.3 Row-class detection

| Row class | Detection rule |
|---|---|
| `digit` | codes 48 (`0`) AND 49 (`1`) both present, OR codes 33 (`!`) AND 41 (`)`) both present |
| `qwerty` | last printable code ∈ {112, 80} (`p` or `P`) |
| `asdf` | row contains code 10 (Enter) and is not bottom |
| `zxcv` | row contains code 122 or 90 (`z` or `Z`) |
| `bottom` | `isBottomRow == true` |
| `other` | none of the above (passed through) |

### 6.4 Per-row trim logic — compact visible rows

The trimmer first turns dropped cells into transparent spacers, then
compacts each content row by removing invisible cells. Widths are then
reassigned by the BALANCE rule (§6.7): normal keys share one fixed
width, function keys are never narrower than normal keys, and any
remaining width goes only to useful edge keys or alignment spacers.

What changes visually: low-value edge scaffolding disappears, `[Tab]`
and the ASDF mode key move out of content rows, and normal keys become
wider on narrow iPads.

| Row class | Trim action |
|---|---|
| `digit` | Two-ended walk (§ below), then compact; `[⌫]` remains. |
| `bottom` | Replaced wholesale with the matching narrow bottom template (§6.6). |
| `other` | Passed through unchanged. |
| `qwerty` / `asdf` / `zxcv` | Right-tail walk, then compact; edge modifiers follow §6.5b. |

**Right-tail walk** (qwerty / asdf / zxcv):

1. Identify trailing modifier (`⌫` for qwerty when no digit row, `↩`
   for asdf, right `⇧` for zxcv). Stop walks before reaching it.
2. Scan leftward from the cell before the trailing modifier.
3. While trimmable AND quota is non-zero: replace cell with spacer,
   decrement quota, advance one cell left.
4. Stop on first non-trimmable cell. Do not skip past it.

**Two-ended walk** (digit row):

1. Compute `digit_zone` = `[min_idx, max_idx]` of cells with `code` in
   48–57. The walk only touches cells **strictly outside** this zone
   so mid-zone scaffolding (e.g. ET41's `%|5` `^|6` between 1–4 and
   7–0) is preserved.
2. Identify trailing `⌫` and stop the right walk before it.
3. Left walk (cells `0` … `min_idx − 1`, L→R): replace with spacer
   while trimmable AND `digit_left` quota non-zero.
4. Right walk (cells `max_idx + 1` … last-non-modifier, R→L): replace
   with spacer while trimmable AND `digit_right` quota non-zero.

The IM-toggle key (`[abc]` / `[中]`) remains reachable via the bottom
row on every tier. The `.?123` key keeps its single job: toggle to the
symbol layout.

### 6.5 Drop quotas

```python
DROP_QUOTA_NARROW = {
    "digit_left":  1,
    "digit_right": 2,
    "qwerty":      3,
    "asdf":        2,
    "zxcv":        1,
}
```

Quotas are **upper bounds** — the walk stops on the first
non-trimmable cell anyway, so a layout with fewer trimmable
scaffolding cells produces fewer spacer replacements.

### 6.5b Narrow-tier edge modifier removal

The narrow tier removes editing/mode keys that cost content width:

| Row | Narrow action |
|---|---|
| qwerty | drop `[Tab]` |
| asdf | drop `[abc]` / `[中]` / `[EN]`; the mode key moves to the bottom row |
| zxcv | keep one `[⇧]`; the duplicate right `[⇧]` may be dropped when needed |
| digit | keep `[⌫]`; trim non-root edge scaffolding only |

Dropped keys are removed from the visible content row. Extra row width
is allowed to remain unused unless BALANCE assigns it to a useful edge
key or alignment spacer.

### 6.6 Bottom-row template

```python
BOTTOM_FULL    = [globe(8), .?123(10), emoji(7), space(57), .?123(10), dismiss(8)]    # large tier
BOTTOM_NARROW_ZH = [globe(9), .?123(11), emoji(8), space(53), abc(11), dismiss(8)]      # Chinese IM narrow tier
BOTTOM_NARROW_EN = [globe(9), .?123(11), emoji(8), space(53), 中(11),   dismiss(8)]      # English narrow tier
SYMBOL_BOTTOM_FULL    = [globe(8), 中(10), emoji(7), space(57), abc(10), dismiss(8)]    # large symbol tier
SYMBOL_BOTTOM_NARROW = [globe(9), abc(11), emoji(8), space(53), 中(11), dismiss(8)]    # narrow symbol tier
```

The bottom row uses `[emoji]` in the slot left of `[space]`; iOS does
not allow third-party keyboards to invoke system dictation, so this
slot must not be `[mic]`. On narrow Chinese layouts the right-side
mode key is `abc`; on narrow English layouts it is `中`. The asdf row
does not carry the mode key at narrow tier.

### 6.7 BALANCE width rule

BALANCE is the narrow-tier row auditor:

- Normal keys in the same narrow layout use one fixed width.
- Function keys must not be narrower than normal keys.
- Extra row width is a luxury. It may remain unused.
- Spacer exists only for alignment, never as filler.
- Follow the built-in iPad phonetic stair shape:
  - digit row: up to 11 content keys plus `[⌫]`; if a digit row
    would otherwise have only 10 content keys, keep one more source
    key immediately before `[⌫]` so Backspace does not become oversized;
  - qwerty row: 12 content keys, no function key;
  - asdf row: up to 11 source content keys plus `[↩]` when available, with
    leading alignment spacer `globeWidth / 2`;
  - zxcv row: left `[⇧]` plus up to 10 source content keys, plus the
    trimmed `。/，` punctuation key when assigned there; `[⇧]` uses
    the same width as the bottom-row `[globe]` key. Root-heavy layouts
    may restore the right `[⇧]` with the remaining right-side width;
    otherwise leftover width becomes a trailing spacer.
- First normal-key offsets between adjacent dense content rows must differ
  by no more than half a normal key.
- Rows without a function key should preserve up to 12 content keys from
  the full 14-cell iPad source.
- Rows with a function key should preserve up to 11 content keys plus
  the action key when the source has that edge key available.
- Within one layout, visible row counts should stay close, but content
  preservation and edge-key rules win over artificial symmetry. Do not
  keep a duplicate right `[⇧]` only to make zxcv match a 12- or 13-slot
  root-heavy row.
- Prefer removing edge modifiers/spacers over widening decorative edges.
- Backspace and Enter may receive useful edge width, but Backspace
  should not be used as the primary width sink when one more source key
  can be preserved immediately before it.
- Bottom-row `abc` / `中` mode keys must match the `.?123` key width;
  adjust only the space key to keep the row sum at 100%.

Default narrow normal key width is `8%`. Layouts with required 13-cell
rows (`lime_ez`, `lime_hs`, and any derived sibling that still has a
13-cell row) use `7.5%`.

`lime_wb` keeps its proportional scaling (it has very few keys and
3-key rows at ~33% each — the spacer mechanism doesn't apply because
nothing is trimmable).

### 6.8 No-op layout write-through

If trimming is a no-op for a layout family, still write the
`_ipad_narrow` sibling so medium/small tier selection is explicit. The
content must be byte-for-byte equivalent to the `_ipad` source except
for the layout `id`. `lime_wb` and `lime_wb_shift` use this path.

This write-through rule does not apply to `symbols*_ipad_narrow`;
symbol narrow pages copy the phone symbol pages instead (§A.13).

---

## 7. WB exception (no-op for the trimmer)

`lime_wb_ipad.json` has 2 content rows + bottom (3 + 4 cells, 5 stroke
keys). No cell has `\n` in its label → trim predicate matches nothing.
The generator still writes `lime_wb_ipad_narrow.json`, but the content
is identical to `lime_wb_ipad.json` except for the layout `id`.

`lime_wb_ipad_shift.json` ships an anomalous CJ-style 14/13/12 layout
that doesn't match the base. The generator treats it as opaque input
and writes `lime_wb_ipad_narrow_shift.json` identical to the source
except for the layout `id`.

---

## 8. File counts and scope guard

This plan **does not remove or rewrite any production `*_ipad.json`
layout**. Existing `_ipad` layouts are production files and stay
shipped exactly as they are unless a separate, explicit task changes
them.

| Category | iPad files today | iPad files (this plan) | Net change |
|---|---|---|---|
| Production `*_ipad.json` layouts | Existing shipped set | Existing shipped set, untouched | 0 |
| Narrow tier `*_ipad_narrow.json` layouts | — | New generated/copied narrow siblings | +new files |

Implementation audit must prove that every layout diff under
`LimeIME-iOS/LimeKeyboard/Layouts` is an `_ipad_narrow` file. Any
modified, deleted, regenerated, or reformatted non-narrow layout is
out of scope and must be reverted manually before continuing.

Re-running the narrow pipeline:

```bash
python3 scripts/trim_ipad_layout.py
```

iPhone behavior is unchanged: all `lime_email.json`, `lime_url.json`,
`symbols2.json`, `symbols3.json`, etc. continue to ship as today and
load on iPhone via `LayoutLoader` (the `_ipad` suffix fallback only
applies when `hostIsPad`).

---

## 9. Rollout order

1. **Code-side foundations.** Add `IPadSizeClass` enum + resolver, with
   all three tiers returning today's iPad values. No visible change.
   Verify on 13" / 11" / mini.
2. **Apply narrow dimensions** (§5) — `.medium` and `.small` get the
   smaller row heights. Cells become roughly square on those devices,
   but still using the 13" layout files. Visible improvement on 11" and
   mini.
3. **Audit production layout scope before generation.**
   Confirm `git diff --name-only -- LimeIME-iOS/LimeKeyboard/Layouts |
   rg -v "_ipad_narrow"` prints no files, and confirm there are no
   production layout deletions.
4. **Generate narrow layouts** — write `trim_ipad_layout.py`, run it,
   land the `*_ipad_narrow.json` files. Narrow layout now active on
   `.medium` and `.small`.
5. **Test** on iPad 11" + iPad mini hardware. Measure final
   narrow bottom-row widths against the rendered keyboard.
6. **Update sibling docs** (`IPAD_KEYBOARD.md`, `IPAD_KB_LAYOUT_COVERTER.md`)
   to cross-reference this doc.

Each step is independently shippable. Steps 1–2 alone fix the
"iPad mini cells too tall" complaint without touching layouts. Step 3
guards the production layout boundary before any narrow files are
generated.

---

## 10. Out of scope

- Changing the row count on any iPad tier (always 5 rows).
- Apple Pencil / hover behavior.
- Floating mini-keyboard (no Apple extension hook).
- macOS Catalyst.
- Android (`LimeStudio/`).
- Any DB / IM-table edit. The §6.2 derivation reads SQLite read-only at
  *plan* time; the trimmer ships with frozen string constants and never
  opens a DB at runtime.

---

## 11. Open questions

### 11.1 Should the SSE threshold separate iPad 11" from iPad mini?

The plan combines them into the narrow tier (single `_ipad_narrow.json`).
The `.medium` vs `.small` distinction exists only for dimension
constants (row height, fonts). If iPad 11" users prefer the full
13"-tier layout (more cells, slightly tall), the threshold can move
from `< 870` to `< 800` so only the iPad mini gets narrow. Default
keeps the threshold at 870 — iPad 11" cells today are visibly tall and
this fixes them.

---

## 12. Invariants this plan preserves

1. Five rows on every iPad tier, every layout, every IM.
2. Phone behavior bit-for-bit unchanged.
3. iPad 13" behavior bit-for-bit unchanged (`.large` returns today's
   values).
4. **Cells stay close to square at every tier** (row height ≈ 7% ×
   screen width — §2 invariant).
5. No cell that is an IM root is ever dropped (§6.1 predicate, rule 2).
6. `build_ipad_layouts.py` stays focused on its existing job and grows
   no new arguments.
7. WB stays a no-op for the trimmer.
8. A missing tier file is graceful — `LayoutLoader` falls through.

---

## Appendix A — Narrow layouts per IM (verified against shipped JSONs)

This appendix is **verified against the actual `*_ipad.json` files
shipped today** (read 2026-05). Source rows below are the ground
truth; narrow rows are computed by applying §6 trim rules.

### A.0 Notation

- Single-glyph cell: `q`
- IM-sublabel cell: `q(ㄆ)` — primary on top, IM character below
- Dual-slide scaffolding: `~|`` — top glyph (slide-down) | bottom glyph (direct tap)
- 3-layer cell (et26 / hsu): `q\tㄗ(ㄟ)` — top + middle + sublabel
- Modifier: `[Tab]` `[abc]` `[⇧]` `[↩]` `[⌫]` `[globe]` `[emoji]` `[空白]` `[.?123]` `[123]` `[中]` `[EN]` `[dismiss]`

Bottom row is always 6 cells; standard `BOTTOM_FULL` at large tier and
`BOTTOM_NARROW_ZH` / `BOTTOM_NARROW_EN` at narrow tier (§6.6).
Omitted from per-IM tables.

### A.0.1 iPad layout inventory

Existing production `_ipad` layouts are read-only inputs for this plan.
The narrow tier adds `_ipad_narrow` siblings only; it does not delete,
replace, or regenerate the production layout inventory.

Production layout families read by the narrow generator:

- **Phonetic-family Chinese IM** (digit row): `lime_phonetic`,
  `lime_dayi`, `lime_dayi_sym`, `lime_et26`, `lime_et_41`, `lime_hsu`.
- **CJ-family Chinese IM** (no digit row): `lime_array`, `lime_cj`.
  Qwerty ends in ⌫; only 2 brackets in qwerty.
- **CJ-family + digit row**: `lime_array_number`, `lime_cj_number`.
  Same alpha rows as parent + digit row + 3 brackets in qwerty.
- **High-density Chinese IM**: `lime_ez`, `lime_hs`. Brackets and
  most ASCII punct as IM roots.
- **WB stroke**: `lime_wb` (base only — 2 content rows, 5 stroke keys).
  `lime_wb_shift` ships an anomalous full layout — see §A.10.
- **English-family**: existing production `_ipad` English, email, URL,
  number, and shift layouts stay shipped. Narrow generation may add
  `_ipad_narrow` siblings, but must not remove or modify the production
  files.
- **Symbol pages**: existing production `symbols*_ipad.json` files stay
  shipped. Medium/small tiers use `symbols1/2/3_ipad_narrow`, copied
  from the phone symbol pages so page rotation stays `1/3 → 2/3 → 3/3`.

### A.0.2 Narrow-tier visible cell counts (single trim, both `.medium` and `.small`)

Format: digit / qwerty / asdf / zxcv (visible cells per row). Narrow
examples below list visible slots only; invisible alignment spacers are
implementation detail. Bottom row always 6.

| IM | Full (today) | Narrow (visible cells) |
|---|---|---|
| `lime_phonetic` | 14 / 14 / 13 / 12 | 12 / 12 / 11 / 12 |
| `lime_dayi` | 14 / 14 / 13 / 12 | 12 / 12 / 11 / 12 |
| `lime_dayi_sym` | 14 / 14 / 13 / 12 | 12 / 12 / 11 / 12 |
| `lime_array` | — / 14 / 13 / 12 | — / 12 / 11 / 12 |
| `lime_array_number` | 14 / 14 / 13 / 12 | 12 / 12 / 11 / 12 |
| `lime_cj` | — / 14 / 13 / 12 | — / 12 / 11 / 12 |
| `lime_cj_number` | 14 / 14 / 13 / 12 | 12 / 12 / 11 / 12 |
| `lime_et26` | 14 / 14 / 13 / 12 | 12 / 12 / 11 / 12 |
| `lime_et_41` | production source (see §A.6) | 12 / 12 / 11 / 12 |
| `lime_hsu` | 14 / 14 / 13 / 12 | 12 / 12 / 11 / 12 |
| `lime_ez` | 14 / 14 / 13 / 12 | 13 / 13 / 12 / 13 |
| `lime_hs` | 14 / 14 / 13 / 12 | 13 / 13 / 12 / 12 |
| `lime_wb` | 3 / 4 (no-op) | 3 / 4 (no-op) |
| `lime_english` + `lime_abc` (English-only on iPad — see §A.12) | 14 / 14 / 13 / 12 | 12 / 12 / 11 / 11 |
| `symbols1` + `symbols2` + `symbols3` (phone-style symbol pages — see §A.13) | phone pages | copied phone content rows + narrow symbol bottom |

Function keys must be at least as wide as normal keys. Narrow digit
rows with Backspace preserve 11 content keys where the source has them,
so Backspace stays close to key size instead of becoming a wide slab.
Narrow qwerty rows drop `[Tab]` but preserve up to 12 content keys.
`[abc]` / `[中]` moves to the bottom row. ASDF drops the low-value
`。/，` edge key; when present, that key moves to ZXCV instead.

### A.1 `lime_phonetic`

Source (`lime_phonetic_ipad.json`):

```
digit  (14): ~|` 1(ㄅ) 2(ㄉ) 3(ˇ) 4(ˋ) 5(ㄓ) 6(ˊ) 7(˙) 8(ㄚ) 9(ㄞ) 0(ㄢ) -(ㄦ) +|= [⌫]
qwerty (14): [Tab] q(ㄆ) w(ㄊ) e(ㄍ) r(ㄐ) t(ㄔ) y(ㄗ) u(一) i(ㄛ) o(ㄟ) p(ㄣ) 『|「 』|」 ||、
asdf   (13): [abc] a(ㄇ) s(ㄋ) d(ㄎ) f(ㄑ) g(ㄕ) h(ㄘ) j(ㄨ) k(ㄜ) l(ㄠ) ;(ㄤ) 。|， [↩]
zxcv   (12): [⇧] z(ㄈ) x(ㄌ) c(ㄏ) v(ㄒ) b(ㄖ) n(ㄙ) m(ㄩ) ,(ㄝ) .(ㄡ) /(ㄥ) [⇧]
```

Narrow-tier changes:

| Row | Change | Visible |
|---|---|---|
| digit | cell 0 (`~\|\``), cell 12 (`+\|=`) | 12 |
| qwerty | drop `[Tab]`; preserve 12 content keys | 12 |
| asdf | drop `[abc]`; keep `[↩]`; use leading stair spacer aligned to zxcv `[⇧]` | 11 |
| zxcv | keep left `[⇧]`; drop duplicate right `[⇧]`; preserve all 10 content keys | 11 |

Narrow (`lime_phonetic_ipad_narrow.json`):

```
digit  (12): 1(ㄅ) 2(ㄉ) 3(ˇ) 4(ˋ) 5(ㄓ) 6(ˊ) 7(˙) 8(ㄚ) 9(ㄞ) 0(ㄢ) -(ㄦ) [⌫]
qwerty (12): q(ㄆ) w(ㄊ) e(ㄍ) r(ㄐ) t(ㄔ) y(ㄗ) u(一) i(ㄛ) o(ㄟ) p(ㄣ) 『|「 』|」
asdf   (11): a(ㄇ) s(ㄋ) d(ㄎ) f(ㄑ) g(ㄕ) h(ㄘ) j(ㄨ) k(ㄜ) l(ㄠ) ;(ㄤ) [↩]  # leading stair spacer, not visible
zxcv   (12): [⇧] z(ㄈ) x(ㄌ) c(ㄏ) v(ㄒ) b(ㄖ) n(ㄙ) m(ㄩ) ,(ㄝ) .(ㄡ) /(ㄥ) 。|，
```

### A.2 `lime_dayi`

Source (`lime_dayi_ipad.json`):

```
digit  (14): ~|` 1(言) 2(牛) 3(目) 4(四) 5(王) 6(門) 7(田) 8(米) 9(足) 0(金) …|— +|= [⌫]
qwerty (14): [Tab] q(石) w(山) e(一) r(工) t(糸) y(火) u(艸) i(木) o(口) p(耳) 『|「 』|」 ||、
asdf   (13): [abc] a(人) s(革) d(日) f(土) g(手) h(鳥) j(月) k(立) l(女) ;(虫) 。|， [↩]
zxcv   (12): [⇧] z(心) x(水) c(鹿) v(禾) b(馬) n(魚) m(雨) ,(力) .(舟) /(竹) [⇧]
```

Narrow-tier changes:

| Row | Change | Visible |
|---|---|---|
| digit | cell 0 (`~\|\``), cell 11 (`…\|—`); keep cell 12 (`+\|=`) before `[⌫]` | 12 |
| qwerty | drop `[Tab]`; keep first two bracket cells for 12 visible cells | 12 |
| asdf | drop `[abc]`; cell 11 (`。\|，`); `;(虫)` is a root → walk stops | 11 |
| zxcv | drop duplicate right `[⇧]`; append moved `。\|，` key | 12 |

Narrow (`lime_dayi_ipad_narrow.json`):

```
digit  (12): 1(言) 2(牛) 3(目) 4(四) 5(王) 6(門) 7(田) 8(米) 9(足) 0(金) +|= [⌫]
qwerty (12): q(石) w(山) e(一) r(工) t(糸) y(火) u(艸) i(木) o(口) p(耳) 『|「 』|」
asdf   (11): a(人) s(革) d(日) f(土) g(手) h(鳥) j(月) k(立) l(女) ;(虫) [↩]
zxcv   (12): [⇧] z(心) x(水) c(鹿) v(禾) b(馬) n(魚) m(雨) ,(力) .(舟) /(竹) 。|，
```

### A.3 `lime_array` (no digit row)

Source (`lime_array_ipad.json`) — qwerty has only 2 brackets and ends in `[⌫]`:

```
qwerty (14): [Tab] q(1⇡) w(2⇡) e(3⇡) r(4⇡) t(5⇡) y(6⇡) u(7⇡) i(8⇡) o(9⇡) p(0⇡) 『|「 』|」 [⌫]
asdf   (13): [abc] a(1−) s(2−) d(3−) f(4−) g(5−) h(6−) j(7−) k(8−) l(9−) ;(0−) 。|， [↩]
zxcv   (12): [⇧] z(1⇣) x(2⇣) c(3⇣) v(4⇣) b(5⇣) n(6⇣) m(7⇣) ,(8⇣) .(9⇣) /(0⇣) [⇧]
```

Array roots include `;`, `,`, `.`, `/` (with sublabels) — all protected.

| Row | Change | Visible |
|---|---|---|
| qwerty | drop `[Tab]`; keep one bracket cell plus `[⌫]` | 12 |
| asdf | drop `[abc]`; cell 11 (`。\|，`) | 11 |
| zxcv | drop duplicate right `[⇧]`; append moved `。\|，` key | 12 |

Narrow (`lime_array_ipad_narrow.json`):

```
qwerty (12): q(1⇡) w(2⇡) e(3⇡) r(4⇡) t(5⇡) y(6⇡) u(7⇡) i(8⇡) o(9⇡) p(0⇡) 『|「 [⌫]
asdf   (11): a(1−) s(2−) d(3−) f(4−) g(5−) h(6−) j(7−) k(8−) l(9−) ;(0−) [↩]
zxcv   (12): [⇧] z(1⇣) x(2⇣) c(3⇣) v(4⇣) b(5⇣) n(6⇣) m(7⇣) ,(8⇣) .(9⇣) /(0⇣) 。|，
```

### A.3.1 `lime_array_number`

Source (`lime_array_number_ipad.json`) — has digit row + 3rd bracket
(`||、`); digit cells use dual-slide form (no IM sublabels):

```
digit  (14): ~|` !|1 @|2 #|3 $|4 %|5 ^|6 &|7 *|8 (|9 )|0 …|— +|= [⌫]
qwerty (14): [Tab] q(1⇡) w(2⇡) e(3⇡) r(4⇡) t(5⇡) y(6⇡) u(7⇡) i(8⇡) o(9⇡) p(0⇡) 『|「 』|」 ||、
asdf   (13): [abc] a(1−) s(2−) d(3−) f(4−) g(5−) h(6−) j(7−) k(8−) l(9−) ;(0−) 。|， [↩]
zxcv   (12): [⇧] z(1⇣) x(2⇣) c(3⇣) v(4⇣) b(5⇣) n(6⇣) m(7⇣) ,(8⇣) .(9⇣) /(0⇣) [⇧]
```

Digit-zone protection (§6.4) keeps digit cells 1–10 even though
chr('1')…chr('0') aren't in array roots.

| Row | Change | Visible |
|---|---|---|
| digit | cell 0 (`~\|\``), cell 11 (`…\|—`); keep cell 12 (`+\|=`) before `[⌫]` | 12 |
| qwerty | drop `[Tab]`; keep first two bracket cells for 12 visible cells | 12 |
| asdf | drop `[abc]`; cell 11 (`。\|，`) | 11 |
| zxcv | drop duplicate right `[⇧]`; append moved `。\|，` key | 12 |

Narrow:

```
digit  (12): !|1 @|2 #|3 $|4 %|5 ^|6 &|7 *|8 (|9 )|0 +|= [⌫]
qwerty (12): q(1⇡) w(2⇡) e(3⇡) r(4⇡) t(5⇡) y(6⇡) u(7⇡) i(8⇡) o(9⇡) p(0⇡) 『|「 』|」
asdf   (11): a(1−) s(2−) d(3−) f(4−) g(5−) h(6−) j(7−) k(8−) l(9−) ;(0−) [↩]
zxcv   (12): [⇧] z(1⇣) x(2⇣) c(3⇣) v(4⇣) b(5⇣) n(6⇣) m(7⇣) ,(8⇣) .(9⇣) /(0⇣) 。|，
```

### A.4 `lime_cj` (no digit row)

Source (`lime_cj_ipad.json`) — qwerty has only 2 brackets and ends in `[⌫]`:

```
qwerty (14): [Tab] q(手) w(田) e(水) r(口) t(廿) y(卜) u(山) i(戈) o(人) p(心) 『|「 』|」 [⌫]
asdf   (13): [abc] a(日) s(尸) d(木) f(火) g(土) h(竹) j(十) k(大) l(中) ；|： 。|， [↩]
zxcv   (12): [⇧] z(重) x(難) c(金) v(女) b(月) n(弓) m(一) <|, >|. ?|/ [⇧]
```

CJ root set is letters only — every punct cell is trimmable.

| Row | Change | Visible |
|---|---|---|
| qwerty | drop `[Tab]`; keep one bracket cell plus `[⌫]` | 12 |
| asdf | drop `[abc]`; keep `；\|：` before `[↩]` | 11 |
| zxcv | drop duplicate right `[⇧]`; append moved `。\|，` key | 12 |

Narrow (`lime_cj_ipad_narrow.json`):

```
qwerty (12): q(手) w(田) e(水) r(口) t(廿) y(卜) u(山) i(戈) o(人) p(心) 『|「 [⌫]
asdf   (11): a(日) s(尸) d(木) f(火) g(土) h(竹) j(十) k(大) l(中) ；|： [↩]
zxcv   (12): [⇧] z(重) x(難) c(金) v(女) b(月) n(弓) m(一) <|, >|. ?|/ 。|，
```

### A.4.1 `lime_cj_number`

Source (`lime_cj_number_ipad.json`) — like `lime_cj` + digit row + 3rd
bracket:

```
digit  (14): ~|` !|1 @|2 #|3 $|4 %|5 ^|6 &|7 *|8 (|9 )|0 …|— +|= [⌫]
qwerty (14): [Tab] q(手) w(田) e(水) r(口) t(廿) y(卜) u(山) i(戈) o(人) p(心) 『|「 』|」 ||、
asdf   (13): [abc] a(日) s(尸) d(木) f(火) g(土) h(竹) j(十) k(大) l(中) ；|： 。|， [↩]
zxcv   (12): [⇧] z(重) x(難) c(金) v(女) b(月) n(弓) m(一) <|, >|. ?|/ [⇧]
```

| Row | Change | Visible |
|---|---|---|
| digit | cells 0 and 11; keep cell 12 before `[⌫]` | 12 |
| qwerty | drop `[Tab]`; keep first two bracket cells for 12 visible cells | 12 |
| asdf | drop `[abc]`; keep `；\|：` before `[↩]` | 11 |
| zxcv | drop duplicate right `[⇧]`; append moved `。\|，` key | 12 |

Narrow:

```
digit  (12): !|1 @|2 #|3 $|4 %|5 ^|6 &|7 *|8 (|9 )|0 +|= [⌫]
qwerty (12): q(手) w(田) e(水) r(口) t(廿) y(卜) u(山) i(戈) o(人) p(心) 『|「 』|」
asdf   (11): a(日) s(尸) d(木) f(火) g(土) h(竹) j(十) k(大) l(中) ；|： [↩]
zxcv   (12): [⇧] z(重) x(難) c(金) v(女) b(月) n(弓) m(一) <|, >|. ?|/ 。|，
```

### A.5 `lime_et26`

Source (`lime_et26_ipad.json`) — has 3-layer cells in qwerty/asdf/zxcv:

```
digit  (14): ~|` !|1 @|2 #|3 $|4 %|5 ^|6 &|7 *|8 (|9 )|0 …|— +|= [⌫]
qwerty (14): [Tab] q\tㄗ(ㄟ) w\tㄘ(ㄝ) e(ㄧ) r(ㄜ) t\tㄊ(ㄤ) y(ㄔ) u(ㄩ) i(ㄞ) o(ㄛ) p\tㄆ(ㄡ) 『|「 』|」 ||、
asdf   (13): [abc] a(ㄚ) s(ㄙ) d\t˙(ㄉ) f\tˊ(ㄈ) g\tㄓ(ㄐ) h\tㄏ(ㄦ) j\tˇ(ㄖ) k\tˋ(ㄎ) l\tㄌ(ㄥ) ；|： 。|， [↩]
zxcv   (12): [⇧] z(ㄠ) x(ㄨ) c\tㄒ(ㄕ) v\tㄑ(ㄍ) b(ㄅ) n\tㄋ(ㄣ) m\tㄇ(ㄢ) <|, >|. ?|/ [⇧]
```

ET26 roots include `,` and `.` — `<|,` and `>|.` are roots; `?|/` is not.

| Row | Change | Visible |
|---|---|---|
| digit | cells 0 and 11; keep cell 12 before `[⌫]` | 12 |
| qwerty | drop `[Tab]`; keep first two bracket cells for 12 visible cells | 12 |
| asdf | drop `[abc]`; keep `；\|：` before `[↩]` | 11 |
| zxcv | drop duplicate right `[⇧]`; append moved `。\|，` key | 12 |

Narrow:

```
digit  (12): !|1 @|2 #|3 $|4 %|5 ^|6 &|7 *|8 (|9 )|0 +|= [⌫]
qwerty (12): q\tㄗ(ㄟ) w\tㄘ(ㄝ) e(ㄧ) r(ㄜ) t\tㄊ(ㄤ) y(ㄔ) u(ㄩ) i(ㄞ) o(ㄛ) p\tㄆ(ㄡ) 『|「 』|」
asdf   (11): a(ㄚ) s(ㄙ) d\t˙(ㄉ) f\tˊ(ㄈ) g\tㄓ(ㄐ) h\tㄏ(ㄦ) j\tˇ(ㄖ) k\tˋ(ㄎ) l\tㄌ(ㄥ) ；|： [↩]
zxcv   (12): [⇧] z(ㄠ) x(ㄨ) c\tㄒ(ㄕ) v\tㄑ(ㄍ) b(ㄅ) n\tㄋ(ㄣ) m\tㄇ(ㄢ) <|, >|. ?|/ 。|，
```

### A.6 `lime_et_41`

`lime_et_41_ipad.json` is a production layout and is out of scope for
source-row changes in this plan. The narrow generator must read the
existing production file and emit only `lime_et_41_ipad_narrow.json`
and, where applicable, its `_shift` narrow sibling.

ET_41 roots include `-`, `=`, `;`, `,`, `.`, `/`.

| Row | Change | Visible |
|---|---|---|
| digit | cell 0 (`~\|\``) only — right walk hits `-(ㄥ)` root | 12 |
| qwerty | drop `[Tab]`; keep first two bracket cells for 12 visible cells | 12 |
| asdf | drop `[abc]`; cell 11 (`。\|，`) — `;(ㄗ)` is root | 11 |
| zxcv | drop duplicate right `[⇧]`; append moved `。\|，` key | 12 |

Narrow (`lime_et_41_ipad_narrow.json`):

```
digit  (12): 1(˙) 2(ˊ) 3(ˇ) 4(ˋ) %|5 ^|6 7(ㄑ) 8(ㄢ) 9(ㄣ) 0(ㄤ) -(ㄥ) [⌫]
qwerty (12): q(ㄟ) w(ㄝ) e(一) r(ㄜ) t(ㄊ) y(ㄡ) u(ㄩ) i(ㄞ) o(ㄛ) p(ㄆ) 『|「 』|」
asdf   (11): a(ㄚ) s(ㄙ) d(ㄉ) f(ㄈ) g(ㄐ) h(ㄏ) j(ㄖ) k(ㄎ) l(ㄌ) ;(ㄗ) [↩]
zxcv   (12): [⇧] z(ㄠ) x(ㄨ) c(ㄒ) v(ㄍ) b(ㄅ) n(ㄋ) m(ㄇ) ,(ㄓ) .(ㄔ) /(ㄕ) 。|，
```

ET_41 narrow visible cell counts are determined from the current
production source. If the narrow result is visually crowded, handle
that in the narrow sibling only; do not reshuffle the production
`lime_et_41_ipad.json` source row as part of this scope.

### A.7 `lime_hsu`

Source (`lime_hsu_ipad.json`):

```
digit  (14): ~|` !|1 @|2 #|3 $|4 %|5 ^|6 &|7 *|8 (|9 )|0 …|— +|= [⌫]
qwerty (14): [Tab] q w(ㄠ) e\tㄧ(ㄝ) r\tㄖ(ㄚ) t(ㄊ) y(ㄚ) u(ㄩ) i(ㄞ) o(ㄡ) p(ㄆ) 『|「 』|」 ||、
asdf   (13): [abc] a\tㄘ(ㄟ) s\t˙(ㄙ) d\tˊ(ㄉ) f\tˇ(ㄈ) g\tㄍ(ㄜ) h\tㄏ(ㄛ) j\tˋ(ㄐㄓ) k\tㄎ(ㄤ) l\tㄌ(ㄦㄥ) ；|： 。|， [↩]
zxcv   (12): [⇧] z(ㄗ) x(ㄨ) c\tㄒ(ㄕ) v\tㄑ(ㄔ) b(ㄅ) n\tㄋ(ㄣ) m\tㄇ(ㄢ) <|, >|. ?|/ [⇧]
```

HSU roots include `,` and `.` — `<|,` is a root, walk stops on zxcv.

| Row | Change | Visible |
|---|---|---|
| digit | cells 0 and 11; keep cell 12 before `[⌫]` | 12 |
| qwerty | drop `[Tab]`; keep first two bracket cells for 12 visible cells | 12 |
| asdf | drop `[abc]`; keep `；\|：` before `[↩]` | 11 |
| zxcv | drop duplicate right `[⇧]`; append moved `。\|，` key | 12 |

Narrow:

```
digit  (12): !|1 @|2 #|3 $|4 %|5 ^|6 &|7 *|8 (|9 )|0 +|= [⌫]
qwerty (12): q w(ㄠ) e\tㄧ(ㄝ) r\tㄖ(ㄚ) t(ㄊ) y(ㄚ) u(ㄩ) i(ㄞ) o(ㄡ) p(ㄆ) 『|「 』|」
asdf   (11): a\tㄘ(ㄟ) s\t˙(ㄙ) d\tˊ(ㄉ) f\tˇ(ㄈ) g\tㄍ(ㄜ) h\tㄏ(ㄛ) j\tˋ(ㄐㄓ) k\tㄎ(ㄤ) l\tㄌ(ㄦㄥ) ；|： [↩]
zxcv   (12): [⇧] z(ㄗ) x(ㄨ) c\tㄒ(ㄕ) v\tㄑ(ㄔ) b(ㄅ) n\tㄋ(ㄣ) m\tㄇ(ㄢ) <|, >|. ?|/ 。|，
```

### A.8 `lime_ez`

Source (`lime_ez_ipad.json`) — uses brackets `[` `\` `]` and most ASCII
punct as IM roots:

```
digit  (14): ~|` 1(|) 2(車) 3(糸) 4(言) 5(貝) 6(雨) 7(ㄇ) 8(八) 9(耳) 0(鳥) 儿(-) =(母) [⌫]
qwerty (14): [Tab] q(手) w(田) e(水) r(口) t(廿) y(、) u(山) i(戈) o(人) p(心) [(匚) ](]) \(ㄏ)
asdf   (13): [abc] a(日) s(尸) d(木) f(火) g(土) h(竹) j(十) k(大) l(中) ;(寸) '(Ｌ) [↩]
zxcv   (12): [⇧] z(Ｚ) x(又) c(金) v(女) b(月) n(弓) m(一) ,(／) .(＼) /(ㄥ) [⇧]
```

`儿(-)` and `=(母)` in digit row are single-glyph cells (no `\n`) →
predicate rule 3 fails → not trimmable.

| Row | Change | Visible |
|---|---|---|
| digit | cell 0 (`~\|\``) only | 13 |
| qwerty | `[Tab]` removed; `[`, `]`, `\` all roots | 13 |
| asdf | `[abc]` removed; `;` and `'` both roots | 12 |
| zxcv | keep all 10 content keys; add `。\|，`; restore right `[⇧]` using remaining row width | 13 |

Narrow:

```
digit  (13): 1(|) 2(車) 3(糸) 4(言) 5(貝) 6(雨) 7(ㄇ) 8(八) 9(耳) 0(鳥) 儿(-) =(母) [⌫]
qwerty (13): q(手) w(田) e(水) r(口) t(廿) y(、) u(山) i(戈) o(人) p(心) [(匚) ](]) \(ㄏ)
asdf   (12): a(日) s(尸) d(木) f(火) g(土) h(竹) j(十) k(大) l(中) ;(寸) '(Ｌ) [↩]
zxcv   (13): [⇧] z(Ｚ) x(又) c(金) v(女) b(月) n(弓) m(一) ,(／) .(＼) /(ㄥ) 。|， [⇧]
```

EZ at narrow tier: 13/13/12/13. `qwerty` stays at 13 because all three
bracket cells are roots. `asdf` drops the mode key only; zxcv uses its
right-side slack for `。|，` plus right Shift.

### A.9 `lime_hs`

Source (`lime_hs_ipad.json`) — lowercase unshifted letter labels; same root
set as EZ:

```
digit  (14): ~|` !|1 @|2 #|3 $|4 %|5 ^|6 &|7 *|8 (|9 )|0 _|- +|= [⌫]
qwerty (14): [Tab] q w e r t y u i o p {|[ }|] |\|\
asdf   (13): [abc] a s d f g h j k l ；|： 。|， [↩]
zxcv   (12): [⇧] z x c v b n m >|. <|, ?|/ [⇧]
```

HS asdf scaffolding `；|：` `。|，` are full-shape codes (65306, 65292)
NOT in HS roots (which has ASCII `;` only) → trimmable. HS qwerty
brackets `{|[` `}|]` `|\|\` ARE in roots (`[`, `]`, `\` ∈ HS roots) →
not trimmable.

| Row | Change | Visible |
|---|---|---|
| digit | cell 0 (`~\|\``) only — `_\|-` and `+\|=` are roots | 13 |
| qwerty | `[Tab]` removed; all 3 brackets are roots | 13 |
| asdf | `[abc]` removed; keep `；\|：` and `。\|，` before `[↩]` | 12 |
| zxcv | restore right `[⇧]` using remaining row width | 12 |

Narrow:

```
digit  (13): !|1 @|2 #|3 $|4 %|5 ^|6 &|7 *|8 (|9 )|0 _|- +|= [⌫]
qwerty (13): Q W E R T Y U I O P {|[ }|] |\|\
asdf   (12): A S D F G H J K L ；|： 。|， [↩]
zxcv   (12): [⇧] z x c v b n m >|. <|, ?|/ [⇧]
```

HS at narrow tier: 13/13/12/12. `asdf` keeps both punctuation cells
and a shorter Enter; zxcv uses the remaining right-side width for Shift.

### A.10 `lime_wb`

Source (`lime_wb_ipad.json`):

```
r0 (3): 一 丨 丿
r1 (4): [abc] 丶 ㄣ [⌫]
```

No `\n` cells → trimmer is no-op. Narrow = source except for the
layout `id`; `lime_wb_ipad_narrow.json` is still written (§6.8).

`lime_wb_ipad_shift.json` ships an anomalous full 14/13/12 layout that
doesn't match the base. It is treated as opaque input and copied to
`lime_wb_ipad_narrow_shift.json` except for the layout `id`.

### A.11 `lime_dayi_sym`

Source byte-identical to `lime_dayi`. Narrow identical to A.2.

### A.12 English-family narrow layouts

`KeyboardViewController` today picks between 6 English-family layouts
based on user pref (`numberRowInEnglish`) and text-input context
(email, URL, number, shift): `lime_english`, `lime_english_number`,
`lime_email`, `lime_url`, `lime_number`, `lime_shift`.

Those production `_ipad` layouts remain shipped and untouched. This
plan may add `_ipad_narrow` siblings and narrow runtime selection, but
must not delete, redirect away from, or regenerate production
English-family `_ipad` files as part of the narrow-keyboard scope.

Source (`lime_english_ipad.json`):

```
digit  (14): ~|` !|1 @|2 #|3 $|4 %|5 ^|6 &|7 *|8 (|9 )|0 _|- +|= [⌫]
qwerty (14): [Tab] q w e r t y u i o p {|[ }|] |\|\
asdf   (13): [中] a s d f g h j k l :|; "|, [↩]
zxcv   (12): [⇧] z x c v b n m <|, >|. ?|/ [⇧]
```

`IM_ROOTS = ""` → every `\n`-label cell is trimmable, subject to quotas.

| Row | Change | Visible |
|---|---|---|
| digit | cell 0 (`~\|\``), cell 11 (`_\|-`); keep cell 12 (`+\|=`) before `[⌫]` | 12 |
| qwerty | drop `[Tab]`; keep first two bracket cells for 12 visible cells | 12 |
| asdf | drop `[中]`; keep `:\|;` before `[↩]` | 11 |
| zxcv | drop duplicate right `[⇧]`; append moved `"\|,` key | 12 |

Narrow (`lime_english_ipad_narrow.json` + `lime_abc_ipad_narrow.json`):

```
digit  (12): !|1 @|2 #|3 $|4 %|5 ^|6 &|7 *|8 (|9 )|0 +|= [⌫]
qwerty (12): q w e r t y u i o p {|[ }|]
asdf   (11): a s d f g h j k l :|; [↩]
zxcv   (12): [⇧] z x c v b n m <|, >|. ?|/ "|,
```

The English narrow bottom row carries the `[中]` mode key; content rows
do not.

### A.13 Symbol narrow layouts

Existing production symbol pages remain shipped and untouched. The
narrow tier adds `symbols1_ipad_narrow`, `symbols2_ipad_narrow`, and
`symbols3_ipad_narrow` by copying the phone `symbols1/2/3.json` pages
for the three content rows, applying the narrow iPad symbol bottom row,
and changing the layout `id`.

```
symbols1_ipad_narrow
r0: same as symbols1 row 0
r1: same as symbols1 row 1
r2: 1/3 page key, same as symbols1 row 2
bottom: globe abc emoji space 中 dismiss

symbols2_ipad_narrow
r0: same as symbols2 row 0
r1: same as symbols2 row 1
r2: 2/3 page key, same as symbols2 row 2
bottom: globe abc emoji space 中 dismiss

symbols3_ipad_narrow
r0: same as symbols3 row 0
r1: same as symbols3 row 1
r2: 3/3 page key, same as symbols3 row 2
bottom: globe abc emoji space 中 dismiss
```

The symbol narrow layouts are not produced by the generic trimmer.

### A.14 Bottom row (every IM, every tier)

Normal narrow Chinese layouts use 6 cells (`globe`, `.?123`, `emoji`,
`space`, `abc`, `dismiss`). Normal narrow English layouts use
(`globe`, `.?123`, `emoji`, `space`, `中`, `dismiss`). Large symbol
layouts use (`globe`, `中`, `emoji`, `space`, `abc`, `dismiss`);
narrow symbol layouts use (`globe`, `abc`, `emoji`, `space`, `中`,
`dismiss`). Per-tier widths from §6.6.

---

## Appendix B — Cap-12 alternative (deferred)

This appendix records the cap-12 design discussed during planning. It
was **not adopted** — the spec ships with cap=13 (§6.5b). This is kept
as a reference in case future user feedback indicates the iPad mini
narrow tier needs even tighter cell counts.

### B.1 Why cap-12 still needs displacement

EZ and HS each have **46 IM root keys** distributed across the 4
content rows. Today's source layout puts:

| Row | EZ roots | Cap=12 fits? |
|---|---|---|
| digit | 12 (`1`–`0`, `-`, `=`) | ✓ exact |
| qwerty | **13** (`q`–`p`, `[`, `]`, `\`) | **✗ overflow by 1** |
| asdf | 11 (`a`–`l`, `;`, `'`) | ✓ (1 spare) |
| zxcv | 10 (`z`–`m`, `,`, `.`, `/`) | ✓ (2 spare) |
| **Total** | **46** | — |

`lime_hs` matches: same 46-root distribution.

EZ qwerty's 13 roots cannot be reduced by removing edge modifiers:
`[Tab]` is already gone, and every remaining right-edge cell is a root.
To reach cap=12, one root must be *displaced* to another location.

### B.2 Three displacement strategies (none simple)

**B.2.a Per-IM source-layout rebalancing.** Move `\(ㄏ)` from EZ qwerty
to asdf (which has a spare slot). Same for HS. Resulting source:

```
EZ digit  (14): ~|` 1(|) 2(車) 3(糸) 4(言) 5(貝) 6(雨) 7(ㄇ) 8(八) 9(耳) 0(鳥) 儿(-) =(母) [⌫]
EZ qwerty (12): q(手) w(田) e(水) r(口) t(廿) y(、) u(山) i(戈) o(人) p(心) [(匚) ](])
EZ asdf   (13): a(日) s(尸) d(木) f(火) g(土) h(竹) j(十) k(大) l(中) ;(寸) '(Ｌ) \(ㄏ) [↩]   ← would need 13 roots plus enter
EZ zxcv   (12): [⇧] z(Ｚ) x(又) c(金) v(女) b(月) n(弓) m(一) ,(／) .(＼) /(ㄥ) [⇧]
```

Asdf becomes 13 cells (12 roots + ↩). That fits only if the row uses
the 7.5% exception and still leaves no room for useful Enter width.

**B.2.b iPhone-like bottom-row absorption.** Move modifiers (`[Tab]`,
`[abc]`, both `[⇧]`, `[⌫]`, `[↩]`) to a **fat bottom row** (~9–10 cells
instead of 6), so the alpha rows hold pure-root content only. Bottom
row design:

```
[globe(7)] [.?123(7)] [abc(7)] [⇧(8)] [⌫(7)] [space(28)] [↩(7)] [⇧(8)] [emoji(6)] [dismiss(8)]
```

Then alpha rows can hit cap=12. EZ qwerty becomes pure 12 roots
(q-p + [ + ]) since `[Tab]` and `\(ㄏ)` move out — `\` to asdf,
`[Tab]` to bottom (or dropped).

Trade-offs:

- iPhone-conventional but iPad-unconventional placement of modifiers.
- `[⌫]` further from typing zone — slower error correction.
- Hand-authored narrow files (no trim script can derive this).
- Per-IM root reshuffling required for EZ/HS.
- ~5× more implementation complexity than the cap=13 plan.

**B.2.c Long-press popup spillover.** Trimmer drops 1 EZ qwerty root
to spacer; the displaced root (e.g. `\(ㄏ)`) becomes accessible via a
long-press popup on `[abc]` or another modifier. Reuses existing
`popupKeyboard` infrastructure. Hidden from view — discovery cost.

### B.3 Why none was adopted

1. **One-cell benefit.** EZ qwerty drops from 13 → 12 cells on iPad
   mini. At 7% printable target, 1 cell of width difference is ~4pt on
   the iPad mini screen. Not perceptually significant.
2. **EZ and HS are niche.** Together <5% of LimeIME users. The cost
   (per-IM root reshuffling, hand-authored narrow files, or hidden
   popups) is disproportionate.
3. **The edge-removal plan is uniform.** Cap=13 for root-heavy rows
   applies cleanly across all IMs. No production source-row reshuffle
   is part of this scope.

### B.4 What would trigger reconsideration

If real iPad-mini users of EZ or HS report the 13-cell qwerty as
crowded, the cleanest path forward is **B.2.c (long-press popup)**:

- Add a small "displaced roots" popup attached to one alpha-row modifier
  (e.g. `[abc]` long-press on iPad mini).
- The trimmer is allowed to drop 1 IM-root cell from EZ/HS qwerty under
  cap=12.
- §A.8 / §A.9 narrow listings would change to:
  - `EZ narrow qwerty (12): q-p [(匚) ](])` — `\(ㄏ)` accessible via long-press popup.
  - `HS narrow qwerty (12): Q-P {|[ }|]` — `|\|\` similarly relocated.

This is purely additive — does not change existing files or rules.
Does not require source-layout reshuffling. Does not move `[⌫]`.

If this becomes a need: revisit then. For now, ship cap=13.
