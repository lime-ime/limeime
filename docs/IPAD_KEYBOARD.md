# IPAD_KEYBOARD — iPad-only Keyboard Layout & Candidate Bar Plan

Status: PARTIALLY IMPLEMENTED — see §12 for session implementation log

## §12 Implementation log (session LimeIME-IOS branch)

### Row key count invariant (all non-wb IM iPad layouts)

Every content row must match the following key counts.  "Keys" counts all
keys including modifiers; backspace (⌫) counts as a key.

| Row | Structure | Total |
|---|---|---|
| Digit | 13 normal + ⌫ | **14** |
| QWERTY | Tab + 13 normal | **14** |
| ASDF | abc + 11 normal + Enter | **13** |
| ZXCV | Shift + 10 normal + Shift | **12** |
| Bottom | fixed 6-key template | 6 |

No-digit layouts (lime_array, lime_cj) have no digit row.  Their qwerty and
asdf rows follow the same 14 / 13 targets; their zxcv row follows the same
12 target.

Digit-row rule: every generated iPad digit row is always 14 keys total:
13 normal keys plus backspace. The final normal key before backspace must be
`+\n=` (tap `=`, slide/long-press `+`). If a source layout does not provide an
`=` key, the generator must add the `+\n=` fallback so the row never drops to
13 total keys.

### Chinese IM iPad generator contract

`scripts/build_ipad_layouts.py` is allowed to generate only these Chinese IM
iPad layouts:

- `lime_phonetic`, `lime_phonetic_shift`
- `lime_array`, `lime_array_shift`
- `lime_array_number`, `lime_array_number_shift`
- `lime_cj`, `lime_cj_shift`
- `lime_cj_number`, `lime_cj_number_shift`
- `lime_dayi`, `lime_dayi_shift`
- `lime_dayi_sym`, `lime_dayi_sym_shift`
- `lime_et26`, `lime_et26_shift`
- `lime_et_41`, `lime_et_41_shift`
- `lime_hsu`, `lime_hsu_shift`
- `lime_wb`, `lime_wb_shift`

The generator must always exclude these layouts, including shifted variants:

- `lime_ez`
- `lime_ez_shift`
- `lime_hs`
- `lime_hs_shift`

Do not generate, regenerate, normalize, or otherwise modify
`lime_ez_ipad*.json` or `lime_hs_ipad*.json` from this script.

#### Source-first guard

Before adding any fallback key, the generator must first inspect the original
phone layout. If the source layout already provides the needed key, use that
source key instead of creating a generic fallback.

- Preserve source keys that have IM sublabels; those sublabels are IM data, not
  decoration.
- A source key with no sublabel may be upgraded in place to the iPad
  dual-sliding form for that slot.
- A fallback dual-sliding key may be added only when the source layout does not
  already provide the base key or its shifted equivalent for that slot.
- Any key promoted to a new iPad row must be stripped from its original row so
  the layout does not duplicate it.
- Dual-sliding labels use `hint\nprimary`: top/hint character before `\n`,
  direct tap character after `\n`.

#### Row 1 / digit row guard

Generated 4-content-row IM layouts must have a 14-key digit row:

```
~\n` | 1 ... 0 | - or fallback | +\n= | backspace
```

- Total count is always 14, including backspace.
- The row is 13 normal keys plus backspace.
- Use source `-` if present; if it has no sublabel, upgrade it to `_\n-`.
- Use source `=` if present; if it has no sublabel, upgrade it to `+\n=`.
- If `=` is missing, add fallback `+\n=` so the row does not fall to 13 keys.
- If the source has no dash slot, use the established dash fallback for that
  layout class.
- Shifted digit rows mirror dual-sliding keys as fixed slide-output keys.

`lime_array` and `lime_cj` no-digit layouts have no digit row; their first
content row starts at the QWERTY rule below. `lime_wb` is compact and is exempt
from normal content-row count validation, but it still uses the standard
bottom-row guard.

#### Row 2 / QWERTY guard

Generated QWERTY rows must have 14 keys:

```
Tab | q w e r t y u i o p | bracket-left | bracket-right | CJK-punct/backspace
```

- Total count is always 14.
- Use source `[` and `]` if present; preserve them if they have IM sublabels,
  otherwise upgrade to bracket dual-sliding keys.
- If bracket keys are missing, add fallback `『\n「` and `』\n」`.
- For 4-content-row layouts, the rightmost punctuation key is always
  `？\n、`: tap emits `、` (12289), slide/long-press emits `？` (65311).
- Never synthesize `|\n、` for Chinese IM iPad layouts.
- A source `\` key with an IM sublabel may be preserved. A plain backslash/pipe
  key must not be converted into `|\n、`.
- For no-digit layouts, the row uses the bracket pair and ends with backspace
  instead of adding the `？\n、` key.

#### Row 3 / ASDF guard

Generated ASDF rows must have 13 keys:

```
abc | a s d f g h j k l | ；\n： or source IM key | 。\n， | Enter/search
```

- Total count is always 13.
- The left modifier is `abc`.
- Use source `;` or `:` if present. Preserve it when it has an IM sublabel.
- If source `;` or `:` has no sublabel, upgrade it in place to `；\n：`.
- If no source semicolon/colon slot exists, add fallback `；\n：`.
- `。\n，` belongs on ASDF, immediately left of Enter/search.
- `。\n，` must never be generated in the bottom row.

#### Row 4 / ZXCV guard

Generated ZXCV rows must have 12 keys:

```
Shift | z x c v b n m | ,/. punctuation slots | Shift
```

- Total count is always 12.
- There is a shift key on both ends.
- Existing `,`, `.`, `/` keys without IM sublabels are upgraded to
  `<\n,`, `>\n.`, `?\n/`.
- Missing punctuation fallbacks are added only if neither the base key nor its
  shifted equivalent already exists (`<`, `>`, `?`).
- Do not promote ASDF-owned punctuation (`;`, `:`, `。\n，`) into ZXCV.
- Strip non-QWERTY extra printable keys that would make the generated row exceed
  the fixed 12-key scaffold.

#### Row 5 / bottom-row guard

Every generated Chinese IM iPad layout uses this fixed 6-key bottom row:

```
[ globe ][ .?123 ][ emoji ][ space ][ .?123 ][ dismiss ]
```

- Total count is always 6.
- Widths are `8 + 10 + 7 + 57 + 10 + 8 = 100`.
- The emoji key is `code = -201`, `icon = face.smiling`.
- There is no microphone key in generated Chinese IM iPad bottom rows.
- There is no transparent spacer key in generated Chinese IM iPad bottom rows.
- `。\n，` and `；\n：` are not bottom-row keys.
- `globe` and `dismiss` both carry `longPressCode = -100` for the options menu.
- `globe` is always present on iPad; do not hide it because of
  `needsInputModeSwitchKey`.

### Shift mirroring rule

A dual-sliding key (`hint\nprimary`) on the unshifted row becomes a **fixed
key locked to the slide output** (the top/hint character) on the shifted row.
The shifted row always has the same key count as the unshifted row.

Examples:
| Unshifted | Shifted |
|---|---|
| `~\`` (96, lp 126) | `~` (126) |
| `!\\n1` (49, lp 33) | `!` (33) — already in source |
| `_\\n-` (45, lp 95) | `_` (95) |
| `+\\n=` (61, lp 43) | `+` (43) |
| `{\\n[` (91, lp 123) | `{` (123) |
| `;\n：` (65306, lp 65307) | `：` (65307) |
| `。\\n，` (65292, lp 12290) | `，` (12290) |
| `<\\n,` (44, lp 60) | `<` (60) — already in source shift |
| `？\\n、` (12289, lp 65311) | `？` (65311) |

### Implemented ✅

#### Layout generation (`scripts/build_ipad_layouts.py`)

- **Dual-sliding key rendering**: keys whose `label` contains `\n` but have no `sublabel` now render both lines in primary color (`makeDualSlidingLabelView`), distinguishing them from phonetic/CJK sublabel keys where the primary letter is dimmed (`makeDualLabelView`).
- **`；\n：` on asdf row**: moved off the bottom row; placed right of `l` / in place of source `;` (upgraded if no sublabel, appended as fallback). `append_semicolon_key` runs before `append_fullshape_period`.
- **wb bottom row**: `-2` in wb content row replaced with `abc (-9)`; standard 6-key `IPAD_BOTTOM_ROW` used for all layouts.
- **`,./` → `<\n,` `>\n.` `?\n/` dual-slide on zxcv row**: `apply_zxcv_punct_sliding` upgrades present keys without sublabel; also inserts fallback keys for any of `,` `.` `/` entirely absent from the row, inserted left of trailing terminators (`-1`, `65292`, `10`). Shift-equivalent guard: if `<` (60) / `>` (62) / `?` (63) already occupy those positions, the corresponding fallback is suppressed to avoid overcrowding. Applied to both 4-row and 3-row (no-digit) paths.
- **Row key count invariant enforcement** — all violations resolved, 92 row checks pass:
  - **ASDF detection for `:` (58)**: `prepend_abc_modifier`, `append_semicolon_key`, `append_fullshape_period` now detect asdf rows ending in `:` (58) in addition to `;` (59) / `l` (108) / `L` (76). Fixes `phonetic_shift` and `et_41_shift` asdf rows (previously untransformed).
  - **Extended bottom-row exclusion list**: codes 58 (`:`), 59 (`;`), 95 (`_`), 43 (`+`) added to `exclude_codes` in `harvest_bottom_row_symbols`. Prevents IM colon/semicolon (asdf row keys) and shift-of-dash/equals from being promoted to zxcv. Fixes `cj_number_shift`, `et26_shift`, `hsu_shift` (zxcv 14→12) and `et_41_shift` (zxcv 15→12 together with the strip below).
  - **Non-QWERTY key strip in `ensure_zxcv_shifts`**: after removing the trailing delete, any printable key whose code is not in the standard QWERTY zxcv set (`_ZXCV_QWERTY_CODES`) is stripped. Removes native IM extras such as `_` (95/ㄦ) in `phonetic_shift` and `'` (39/ㄘ) in `et_41` that pushed the row to 13.
  - **Dedup filter when promoting extra_keys**: `,./` (44/46/47) from the source bottom row are skipped when their shift-layer equivalents `<>?` (60/62/63) are already in the zxcv row. Prevents double-counting for phonetic_shift (which has `<>?` as native IM keys).
  - **No-digit qwerty restructured** (`lime_array`, `lime_cj`): `transform_no_digit_im_rows` row 0 changed from `q-p + ⌫(30%)` to `Tab + q-p + 『\n「 + 』\n」 + ⌫` = 14. The `？\n、` CJK punctuation key is removed from both qwerty and asdf; bracket pair moves to qwerty.
  - **No-digit asdf restructured**: row 1 changed from `Tab + letters + ；\n：+ {CJK brackets}` to `abc + letters + ；\n：(or IM key) + 。\n，+ ↩` = 13. Also handles `:` (58) as last asdf key (same logic as `;`/59 — leave unchanged if IM sublabel present).
  - **No-digit zxcv restructured**: row 2 `。\n，` removed; row ends with `abc + letters + ↩` = 12 (plus any `<\n,`/`>\n.`/`?\n/` fallbacks inserted by `apply_zxcv_punct_sliding`).

- **Sliding key label convention locked**: `'sliding\\ndirect'` format throughout — sliding char BEFORE `\n` = TOP (small/dim, 20pt light), direct char AFTER `\n` = BOTTOM (large/prominent, 24pt regular).
- **CJK number-row leftmost key**: direct input `` ` `` (backtick, code 96), sliding `~` (lp 126). Label `'~\\n\`'`. Shifted state shows `~` only.
- **Phonetic r1 shifted**: shift symbol on TOP (small), BPMF character on BOTTOM (large). `mk(sc, label=_SHIFT_CHAR[sc], sublabel=bpmf_char)`.
- **CJK digit row shifted**: shift symbol on TOP (small), CJK sublabel on BOTTOM (large). `mk(sc, label='!', sublabel='言')`.
- **Bracket keys** above Enter: `「/『`, `」/』`, `、/？` — all using correct `'sliding\\ndirect'` format.
- **Colon key** left of Enter: `：/；`.
- **Comma key** right of Space: `，/。`.
- **Symbol keyboard row 4**: left spacer 7.5 to align ↑/↓ arrows.
- All layouts regenerated and deployed.

#### Dual-row key gesture handling (`LimeIME-iOS/LimeKeyboard/KeyboardView.swift`)
- **`setDualRowLabelSecondaryOnly` fix**: during slide, now correctly hides `secondaryLbl` (BOTTOM = direct char) and enlarges `primaryLbl` (TOP = sliding char) to single-label font size. Previously was backwards (showed direct char during slide).
- **`dualRowPanned` secondaryDef label fix**: `secondaryDef.label` changed from `keyDef.sublabel` (direct char) to `keyDef.label` (sliding char) so the committed secondary action carries the correct label.
- **`dualRowLongPressed` added**: new long-press gesture recognizer on all `isDualRowIPadKey` keys. On `.began`, shows key preview popup with a synthesized `KeyDef(code: longPressCode, label: keyDef.label)` = sliding char. On `.ended`, commits sliding char via `didPress`.

#### Composing strip + assist bar (`LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`)
- **`effectiveComposingPopupHeight`**: always uses `composingPopupHeight` (removed iPad=0 override). Composing strip visible on both iPhone and iPad.
- **`setupAssistBar()`**: on iPad, `inputAssistantItem.leadingBarButtonGroups = []` (removes undo/redo), trailing group shows Paste icon + composing label (`assistBarComposingLabel`). Composing text and reverse-lookup text mirrored to assist bar label in addition to the composing strip.

#### Host-idiom (iPhone-only apps on iPad) — iPad layout gating fix
- **Problem**: `UIDevice.current.userInterfaceIdiom` returns `.pad` on iPad hardware even when the host app is an iPhone-only app running in scaled / compatibility mode. The iPad layout was loaded into an iPhone-sized host UI, producing the squashed mismatch shown in the user's screenshot.
- **Fix (final, scope-narrowed)**: gate only the **layout-variant lookup** on the host's `traitCollection.userInterfaceIdiom`. All visual sizing (fonts, key heights, candidate bar dimensions, pill geometry) continues to read `UIDevice.current.userInterfaceIdiom == .pad` so iPad hardware always renders at iPad dimensions.
  - `LayoutLoader.swift`: new `static var hostIsPad: Bool` flag; `_ipad` variant lookup gates on it instead of `UIDevice`.
  - `KeyboardViewController.swift`: new `private var isOnPad: Bool { traitCollection.userInterfaceIdiom == .pad }` (the controller's trait collection reliably reflects the host on iPad). `viewDidLoad` sets `LayoutLoader.hostIsPad = isOnPad` **before** any `LayoutLoader.load(...)` call. `viewWillLayoutSubviews` and `traitCollectionDidChange` resync `LayoutLoader.hostIsPad`, clear the layout cache, and reload the current layout if the host idiom flipped (e.g. iPhone-only app moved between iPad multitasking modes). The five `UIDevice…idiom == .pad` literals that drive **layout selection / behavior gating** (composing-popup height, candidate-bar height, split-keyboard gating, globe visibility, globe-menu dedup) were replaced with `isOnPad`. The two literals that drive **iPad-only visual sizing** in `reloadExpandedCandidates` continue to use `UIDevice` (see below).
  - `KeyboardView.swift`: untouched — `private let isPad = UIDevice.current.userInterfaceIdiom == .pad` captured once at view init. (Earlier session experiments with controller-pushed `isPadHost` and view-side `traitCollectionDidChange` rebuilds were reverted because they changed the visible iPad keyboard height.)
  - `CandidateBarView.swift`: `private let isPad = UIDevice.current.userInterfaceIdiom == .pad` (captured once); used by `baseCandidateFontSize` (`isPad ? 26 : 22`) and `baseComposingCodeFontSize` (`isPad ? 22 : 16`). Chevron point size, `candidateHPad`, and `CandidateButton.pillView` insets remain at their pre-session fixed values (`18`, `10`, `padX=4`, `padY=2`) — they work on both idioms.
  - `KeyboardViewController.reloadExpandedCandidates`: matches `CandidateBarView` glyph metrics exactly — `font = systemFont(ofSize: (UIDevice…isPad ? 26 : 22) * candidateFontScale)`, `composingFont = monospacedSystemFont(ofSize: (UIDevice…isPad ? 22 : 16) * …)`. Button uses plain `UIButton(type: .system)` with `setValue(...forKey: "contentEdgeInsets")` set to a fixed 10pt left/right (KVC bypasses the iOS 15 `contentEdgeInsets` deprecation warning while writing the same backing storage; `UIButton.Configuration.plain()` was tried but adds its own internal padding on top of `contentInsets` and visibly inflates the cell). Pill geometry mirrors `CandidateButton.layoutSubviews` exactly: `cellHPad = 10`, `padX = 4`, `padY = 2`, `pillW = btnW - 12`, `pillX = 6`, `pillH = min(rowH, ceil(lineHeight) + 4)`.
- **Net effect**: native iPad apps still get the iPad layout and full iPad font/spacing; iPhone-only apps on iPad now correctly get the iPhone layout (matching the host UI) while candidate-bar fonts still scale up because the iPad screen is always large enough to read them comfortably. iPhone-on-iPhone behavior bit-for-bit unchanged. Expanded candidate panel is now visually identical to the unexpanded candidate bar (same font, same padding, same pill width) on both iPhone and iPad.

### Remaining from §4–§9 plan (not yet implemented) ⬜
- §4.3 iPad dimension set (replace `idiomMultiplier` with parallel constants for key height, gaps, fonts)
- §4.4 Popup keyboard `_ipad.json` variants
- §4.6 Preview suppression for regular (non-dual-row) iPad keys
- §5 CandidateBarView iPad dimension set (larger fonts, taller bar)
- Globe key always-visible on iPad (§4.2 bottom-row)
- Globe menu deduplication (§4.2)
- Transparent spacer key rendering (§4.2.7)
Scope: iOS only (LimeIME-iOS). Android (LimeStudio) untouched.
DB policy: **Do NOT touch any database.**
- `Database/array.limedb` / `array10.limedb` are **import seeds** (read once during first-launch import). Off-limits.
- The runtime app DB `lime.db` (created in the shared App Group container from those seeds, used for all keyboard / IM / mapping reads at runtime). Off-limits.
- No schema change, no row edit, no migration. The `keyboard` / `im` tables in both files keep their current values.

Layout-file policy: **Do NOT modify existing `lime_*.json` / `phone*.json` / `symbols*.json` layouts.**
For each existing keyboard layout that is exposed on iPad, ship a **separate `*_ipad.json`** sibling with bigger keys, more rows, and more spacers. Layout selection is purely a runtime decision in `LayoutLoader` — the IM tables in `lime.db` (and the `.limedb` seeds they were imported from) keep referencing the existing un-suffixed IDs.

---

## 1. Goals

1. On iPad (`UIDevice.current.userInterfaceIdiom == .pad`), present a layout that **looks exactly like Apple's stock iPad keyboard** in the three attached screenshots:
   - English / ABC keyboard (§4.2.1 / §4.2.2): 5 rows; top row is the dual number+symbol row with `\n`-split labels and slide-down secondary entry; right edge gets the `{ [` / `} ]` / `| \` cluster; row 3 has a `注音` IM-toggle modifier; row 3 right is the blue `search` / `return` accent; row 4 has shift on **both** ends.
   - Symbols / `.?123` keyboard (§4.2.3): 5 rows; row-3 modifier is `undo`; row-4 modifier is `redo`; bottom row uses `ABC` on both sides of the spacebar.
   - Phonetic / 注音 keyboard (§4.2.5): 5 rows; row-3 modifier is `abc`; right-edge cluster uses CJK corner brackets `『「` / `』」` / `？ 、`; row-3 right is the magnifying-glass `search` icon.
   - Every generated Chinese IM (Array / CJ / Dayi / ET26 / ET41 / Hsu / WB) inherits the same scaffolding (§4.2.6) — alpha keys come from the existing phone JSON, but the iPad-only number row, IM-toggle, search, dual punctuation cells, dual shift, and bottom-row template are all added; **no key is squeezed**, and rows that have fewer alpha keys than the iPad scaffold are padded with transparent spacers (§4.2.7) so each surviving key stays at iPad cell width.
   - **EZ and HS are permanently excluded from iPad layout generation**, including shifted variants. The generator must never create or update `lime_ez_ipad*.json` or `lime_hs_ipad*.json`; leave those layout files untouched.
2. The English / ABC top row implements the **dual-character key** (§4.5): tap = primary symbol, slide-down = secondary number, long-press = preview shows secondary only. Encoded with the existing `\n`-split label + `longPressCode` field — no schema change.
3. Larger candidate bar font and row height on iPad (§5).
4. Show the candidate-bar hamburger/options button on iPad when the candidate row is empty; tapping it opens the same menu as long-pressing the keyboard/dismiss key. The button stays on the right/backspace edge but uses a 7% normal-key-width frame instead of the wider backspace width, uses candidate text color for light/dark contrast, and keeps a full-height touch target.
5. **Do not** alter existing JSON layouts, the `.limedb` keyboard / im tables, or the Android port. iPad uses parallel `_ipad.json` files only.
6. Investigate whether the iPad system **shortcut bar** (the strip to the right of undo/redo/paste in the attached screenshots) can host LimeIME candidates. **Outcome (see §6): not feasible from a custom keyboard extension.** Therefore continue rendering candidates inside `CandidateBarView`, but enlarge it on iPad.

---

## 2. Affected files (read-only inventory — confirms what the plan will touch)

Code:
- [LimeIME-iOS/LimeKeyboard/LayoutLoader.swift](LimeIME-iOS/LimeKeyboard/LayoutLoader.swift) — single resolution point that maps an ID like `lime_phonetic` to a JSON file.
- [LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift](LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift) — `resolvedLayoutId(for:)`, all `LayoutLoader.load(...)` call sites (lines 152, 330, 406, 414, 494, 509, 511, 514, 523), `viewWillLayoutSubviews()` (line 212) where `isPad` is already detected.
- [LimeIME-iOS/LimeKeyboard/KeyboardView.swift](LimeIME-iOS/LimeKeyboard/KeyboardView.swift) — existing `idiomMultiplier` (line 234, currently 1.5 on pad), font constants (`keySingleLabelFont`, `keyLabelFont`, `keySublabelFont`, …).
- [LimeIME-iOS/LimeKeyboard/CandidateBarView.swift](LimeIME-iOS/LimeKeyboard/CandidateBarView.swift) — `baseCandidateFontSize` (22), `baseComposingCodeFontSize` (16), `candidateHPad` (10).
- [LimeIME-iOS/project.yml](LimeIME-iOS/project.yml) — already copies `LimeKeyboard/Layouts/` flat into the bundle (line 83); no rule change needed when the new `_ipad.json` files land in the same folder.

Resources to add (new):
- `LimeIME-iOS/LimeKeyboard/Layouts/<existing_id>_ipad.json` for every layout listed in §4.

DB / IM tables: **NOT touched.** No script writes to `Database/*.limedb` (import seeds) and nothing alters the runtime `lime.db` in the App Group container. No migration. The `kbname` column in IM tables still names `lime_phonetic`, `lime_array`, etc.

---

## 3. Runtime selection rule (single point of change)

Add an `_ipad` suffix fallback inside `LayoutLoader.load(_:)` (or a thin wrapper used by all current call sites). The suffix is appended **only when the device is an iPad**. JSON file lookup falls back gracefully so layouts without an iPad variant continue to use the phone JSON.

Pseudocode (no code yet — for plan review only):

```
static func load(_ id: String) -> LimeKeyLayout? {
    let isPad = UIDevice.current.userInterfaceIdiom == .pad
    if isPad, !id.hasSuffix("_ipad") {
        if let pad = loadInternal(id + "_ipad") { return pad }   // try iPad first
    }
    return loadInternal(id)                                       // existing path
}
```

Why centralize here:
- Every existing call site (controller, popup loader, layout-existence probes) automatically benefits.
- IM tables in the runtime `lime.db` (and the `.limedb` import seeds) keep their current `kbname` values (`lime_phonetic`, `lime_array`, `phone`, …) — nothing in either DB needs to know that an `_ipad` variant exists.
- `resolvePopup(_:)` already calls `resolvePopup` with bare IDs; popups can opt into iPad variants the same way (see §4.4).

Cache key: must include `_ipad` suffix when present, so a phone-side cache entry from a previous build cannot leak. Easiest: cache the resolved file name, not the requested ID. `clearCache()` is already called per session start.

`prefetchCommonLayouts()` should also try the iPad variants when running on iPad.

---

## 4. New iPad layout files

### 4.1 Files to add (one `_ipad.json` per source layout)

Alpha / IM layouts — exposed on iPad:
- `lime_abc_ipad.json`, `lime_abc_shift_ipad.json`
- `lime_english_ipad.json`, `lime_english_shift_ipad.json`
- `lime_phonetic_ipad.json`, `lime_phonetic_shift_ipad.json`
- `lime_array_ipad.json`, `lime_array_shift_ipad.json`
- `lime_array_number_ipad.json`, `lime_array_number_shift_ipad.json`
- `lime_cj_ipad.json`, `lime_cj_shift_ipad.json`
- `lime_cj_number_ipad.json`, `lime_cj_number_shift_ipad.json`
- `lime_dayi_ipad.json`, `lime_dayi_shift_ipad.json`
- `lime_dayi_sym_ipad.json`, `lime_dayi_sym_shift_ipad.json`
- `lime_et26_ipad.json`, `lime_et26_shift_ipad.json`
- `lime_et_41_ipad.json`, `lime_et_41_shift_ipad.json`
- `lime_hsu_ipad.json`, `lime_hsu_shift_ipad.json`
- `lime_wb_ipad.json`, `lime_wb_shift_ipad.json`
- `lime_number_ipad.json`, `lime_number_shift_ipad.json`
- `lime_shift_ipad.json`
- `symbols1_ipad.json`, `symbols2_ipad.json`, `symbols3_ipad.json`
- `lime_email_ipad.json`, `lime_url_ipad.json`, `lime_english_number_ipad.json`, `lime_english_number_shift_ipad.json`

Phone-only numpads (no iPad variant — fall through to phone JSON):
- `phone.json`, `phone_number.json`, `phone_shift.json`, `phone_simple.json` — these are bound to `.phonePad` numeric textfields and look identical on iPad.

Excluded from script generation (do not create or modify iPad variants):
- `lime_ez`, `lime_ez_shift`
- `lime_hs`, `lime_hs_shift`

Popups (`popup_*`, see §4.4):
- Optional `popup_*_ipad.json` only if the popup needs more columns on iPad.

### 4.2 Geometry conventions for `_ipad.json`

Goal: the iPad layouts must look **exactly** like Apple's stock iPad keyboards in the three attached screenshots. We have far more screen than iPhone, so the design intent is the opposite of the phone JSON: **do not squeeze keys**. Add the extra side columns, the extra top/right symbol columns, and the wider modifiers that the stock iPad keyboard uses. Every generated IM (Phonetic, Array, CJ, Dayi, ET26, ET41, Hsu, WB, English, ABC) gets the same scaffolding even though the alpha-key cluster differs per IM. EZ and HS are excluded from iPad layout generation.

Common scaffolding (every alpha-IM `_ipad.json`):

- **5 rows** total (vs the 4 rows on phone): top number/symbol row + 3 alpha rows + bottom system row.
- Per row, key counts and widths match the screenshots — **no improvisation**. Sums of `widthPercent` per row must equal `100.0`.
- `defaultWidthPercent` is informational only; widths are set per-key.

Geometric tokens used in the layouts below (each stays consistent across the three screenshots):

| Token            | `widthPercent` | Notes |
| ---              | ---            | --- |
| `KEY`            | `6.66`         | Standard top-row / alpha cell (15-column grid). |
| `KEY_NARROW`     | `6.0`          | Bottom-row tertiary keys (`globe`, `.?123`/`ABC`, emoji, dismiss). |
| `MOD_TAB`        | `7.0`          | `→` (tab) on row 2, left edge. |
| `MOD_IM`         | `7.0`          | `注音` / `abc` toggle on row 3, left edge. |
| `MOD_SHIFT_L`    | `9.5`          | Left shift on row 4. |
| `MOD_SHIFT_R`    | `9.5`          | Right shift on row 4. |
| `MOD_RETURN`     | `9.5`          | `search` / `return` on row 3, right edge. |
| `MOD_BACKSPACE`  | `7.5`          | `⌫` on row 1, right edge. |
| `MOD_PUNCT`      | `6.0`          | `:`/`;`, `"`/`,`, `<`/`,` etc. dual-glyph cells. |
| `SPACE`          | `≈ 50`         | Space — fills whatever is left after the bottom-row siblings. |

Bottom-row template (every alpha layout, every IM):

```
[ globe ][ .?123 ][ emoji ][ space ][ .?123 ][ dismiss ]
```

This is a fixed **6-key** row. The old microphone/dictation slot is not used;
that position is the emoji key (`face.smiling`, code `-201`). There is no
post-space spacer in the generated Chinese IM bottom row. `。\n，` belongs on
the ASDF row, left of Enter/search.

**Both `globe` and `dismiss` keys are always present** in the iPad bottom row — they coexist (visible in all three screenshots: globe icon at the far left, keyboard-with-down-arrow icon at the far right). This is different from the phone behavior where the globe key is conditional on `needsInputModeSwitchKey`:

- `globe` (left edge): SF symbol `globe`, code `LimeKeyCode.globe`. On iPad, render unconditionally — do **not** hide via `setGlobeKeyVisible(false)` even when `needsInputModeSwitchKey == false`. Apple's stock iPad keyboard shows it always; we match.
  - Long-press still opens the input-mode picker; tap still calls `advanceToNextInputMode()`. If the system has only one keyboard installed, the long-press menu shows just the LimeIME entries — same as Apple's behavior.
- `dismiss` (right edge): SF symbol `keyboard.chevron.compact.down`, code `LimeKeyCode.done` (or the existing dismiss code path). Tap dismisses the keyboard via `dismissKeyboard()`. Long-press opens the floating-keyboard / split-keyboard menu on phone today — keep that wiring; iPad will use the same long-press menu.

Update `KeyboardViewController.updateGlobeKeyVisibility()` (line ~213) to bypass the `needsInputModeSwitchKey` check when `isPad && layout.id.hasSuffix("_ipad")` — globe stays visible. Phone path unchanged.

**Globe long-press menu — drop the duplicate `系統輸入法切換` entry whenever the globe key is visible.** Currently `showGlobeMenu()` (`KeyboardViewController.swift:2418` and the gating at line ~2437) appends `系統輸入法切換 → advanceToNextInputMode()` only when `needsInputModeSwitchKey == true`. That is exactly the case where the globe key itself is visible, so the menu entry duplicates what a single tap on the globe already does. Invert the gate:

```
// 系統輸入法切換 — only when no globe key is visible (otherwise tap-globe already does this)
let globeIsVisible = (isPad && currentLayoutEndsWithIPad) || needsInputModeSwitchKey
if !globeIsVisible {
    items.append(("系統輸入法切換", { [weak self] in self?.advanceToNextInputMode() }))
}
```

Net effect:
- iPad: globe always visible → entry never appears in the menu (single tap on globe is the user's gesture).
- Phone with multiple system IMs (`needsInputModeSwitchKey == true`): globe key is visible → entry no longer appears (was a duplicate).
- Phone with only LimeIME installed (`needsInputModeSwitchKey == false`): globe key not shown → entry still appears as a fallback (no regression vs. today, because today the entry is hidden in this case anyway — net neutral; the menu just keeps the fallback path open if the user later installs a second keyboard mid-session).

The duplicate `.?123` / `ABC` keys on both sides of the spacebar is Apple's iPad convention (visible in all three screenshots). When the layout is the symbols mode, both edges show `ABC` instead of `.?123` (also visible in the first screenshot).

#### 4.2.1 `lime_english_ipad.json` — exact replica of screenshot 2 (ABC mode)

Row 1 (14 cells, `KEY` × 13 + `MOD_BACKSPACE`):
```
~ ` | ! 1 | @ 2 | # 3 | $ 4 | % 5 | ^ 6 | & 7 | * 8 | ( 9 | ) 0 | _ - | + = | ⌫
```
- Each "X Y" cell is the **dual top-row key** described in §4.5: `label = "X\nY"` (rendered using the existing `\n`-split sublabel mechanism), `code = ord(X)`, `longPressCode = ord(Y)`.
- The leftmost cell is `~ ` ` (tilde primary, backtick secondary).

Row 2 (`MOD_TAB` + 10× `KEY` + 3× dual-glyph `KEY`):
```
→  |  q  w  e  r  t  y  u  i  o  p  |  { [  |  } ]  |  | \
```
- `{ [`, `} ]`, `| \` are dual-label cells (no `longPressCode` — both are punctuation, but iPad convention is the secondary appears via slide-down, so wire them with `longPressCode` for `[`, `]`, `\` respectively to mirror the screenshot behavior).

Row 3 (`MOD_IM` + 9× `KEY` + 2× `MOD_PUNCT` + `MOD_RETURN`):
```
注音 |  a  s  d  f  g  h  j  k  l  | : ;  | " ,  | search
```
- `注音` is the IM-toggle modifier (switches to the active Chinese IM, e.g. `lime_phonetic_ipad`). On the English layout this label is fixed; on Chinese-IM layouts it becomes `abc` (toggles to `lime_english_ipad`) — see §4.2.3.
- `: ;` and `" ,` are dual-label cells with `longPressCode` set to the secondary glyph code.

Row 4 (`MOD_SHIFT_L` + 7× `KEY` + 3× `MOD_PUNCT` + `MOD_SHIFT_R`):
```
⇧  |  z  x  c  v  b  n  m  | < ,  | > .  | ? /  |  ⇧
```
- The dual cells on row 4 (`< ,`, `> .`, `? /`) carry `code` for the upper glyph and `longPressCode` for the lower glyph (matching the screenshot).
- Two shift keys, one on each end, just like the screenshot.

Row 5 (bottom-row template above).

#### 4.2.2 `lime_english_shift_ipad.json` — shift state of 4.2.1

Same 5 rows, same widths, same key positions. Differences:
- Alpha keys render uppercase via existing `adjustCase(_:)` (no JSON change needed; current shift mechanism already handles this).
- Top-row dual cells: same labels, same codes (the shift state of iPad doesn't actually change the dual top row in the screenshots — both states show `! 1`, `@ 2`, … — verify against the shift screenshot before shipping).

Current iOS runtime behavior:
- The shipped layout IDs use the `*_ipad_shift` suffix form, e.g. `lime_english_ipad_shift` and `lime_english_number_ipad_shift`.
- A tap-and-release on shift enters one-shot shift: the shift key is blue, the shifted iPad layout is shown, the next character is shifted, then the keyboard returns to unshifted.
- A physical shift hold previews the shifted layout without rebuilding/removing the original pressed shift button. This preserves UIKit's shift release event. While held, tapped keys output shifted values; on release, the shift key and layout return to unshifted.
- A double tap on shift enters caps lock. Caps lock keeps the shifted layout and shifted output until shift is tapped again.
- For iPad dual-label keys, held shift uses the key's `longPressCode` as the shifted output while the original key button remains alive for touch tracking.
- `lime_english_number_ipad_shift.json` must be a real shifted layout: letter keys display uppercase labels (`Q`, `W`, ...), top/punctuation dual-label cells collapse to shifted single labels such as `~`, `!`, `{`, `<`.

#### 4.2.3 `symbols1_ipad.json` — exact replica of screenshot 1 (.?123 mode)

Row 1 (14 cells, 13 single-glyph `KEY` + `MOD_BACKSPACE`):
```
` | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 0 | < | > | ⌫
```
- Single label per key (no dual rendering); top row in symbols mode is a 13-key strip + backspace.

Row 2 (`MOD_TAB` + 13× `KEY`):
```
→  |  [  ]  {  }  #  %  ^  *  +  =  \  |  ~
```

Row 3 (`undo`(MOD_IM) + 12× `KEY` + `MOD_RETURN`):
```
undo | - | / | : | ; | ( | ) | $ | & | @ | £ | ¥ | search
```
- `undo` replaces the IM-toggle position (single-shot key code → undo). Renders as `undo` text label, modifier styling.
- `search` is the highlighted blue accent in the screenshot; reuse the existing `done`/`return` SF-symbol color path with the `search` semantic.

Row 4 (`redo`(MOD_SHIFT_L) + 9× `KEY`):
```
redo  |  …  |  .  |  ,  |  ?  |  !  |  '  |  "  |  _  |  €
```
- `redo` is left-aligned modifier label.
- No right-shift in this row in the screenshot — the row ends after `€`. The remaining width on the right is **empty space** (encode as a single transparent spacer key with `widthPercent` filling the gap and `code = 0`, no label, no background).

Row 5 (bottom-row template, but both edge keys are `ABC` instead of `.?123`):
```
[ globe ][ ABC ][ emoji ][ space ][ ABC ][ dismiss ]
```

#### 4.2.4 `symbols2_ipad.json`, `symbols3_ipad.json`

Same scaffolding as 4.2.3 (5 rows, `→`/`undo`/`redo` on the modifier column, `search` on row 3 right). Cell contents mirror the existing `symbols2.json` / `symbols3.json` glyph sets, redistributed across 14 columns instead of the phone's 10. Where a row has fewer than 14 cells, pad with transparent spacers (`code = 0`) — do **not** stretch the existing keys to fill the row; that gives the "squeezed" look the user explicitly does not want.

#### 4.2.5 `lime_phonetic_ipad.json` — exact replica of screenshot 3 (注音 mode)

Row 1 (14 cells, `KEY` × 13 + `MOD_BACKSPACE`):
```
~ . | ㄅ | ㄉ | ˇ | ˋ | ㄓ | ˊ | ˙ | ㄚ | ㄞ | ㄢ | ㄦ | — / ......  | ⌫
```
- The leftmost cell is dual `~ .` (label `"~\n."`, primary `~`, `longPressCode` `.`).
- The rightmost-but-one cell is the dual `— / ......` glyph from the screenshot — encode as `"—\n……"`.

Row 2 (`MOD_TAB` + 10× `KEY` + 3× dual-glyph `KEY`):
```
→  | ㄆ | ㄊ | ㄍ | ㄐ | ㄔ | ㄗ | ㄧ | ㄛ | ㄟ | ㄣ |  『 「  |  』 」  |  ？ 、
```
- `『 「`, `』 」`, `？ 、` are dual-glyph cells (corner-bracket primary, regular bracket secondary).

Row 3 (`MOD_IM`("abc") + 11× `KEY` + `MOD_RETURN`(🔍 search-icon)):
```
abc  | ㄇ | ㄋ | ㄎ | ㄑ | ㄕ | ㄘ | ㄨ | ㄜ | ㄠ | ㄤ |  : ;  | 🔍
```
- `abc` is the IM-toggle modifier — switches to `lime_english_ipad` and saves the previous IM. Mirror of the `注音` key in the English layout (§4.2.1).
- The right-edge button uses an SF-symbol `magnifyingglass` (matches the screenshot).

Row 4 (`MOD_SHIFT_L` + 11× `KEY` + `MOD_SHIFT_R`):
```
⇧  | ㄈ | ㄌ | ㄏ | ㄒ | ㄖ | ㄙ | ㄩ | ㄝ | ㄡ | ㄥ |  ⇧
```
- 11 alpha cells in the screenshot; pad row to 100% with the two shift keys.

Row 5 (bottom-row template).

`lime_phonetic_shift_ipad.json` mirrors §4.2.2: same scaffolding, alpha keys are the shift-state Bopomofo set already in the phone JSON.

#### 4.2.6 Other IM layouts (Array / CJ / Dayi / ET26 / ET41 / Hsu / WB)

Apply the same 5-row scaffolding from §4.2.5:
- Row 1: number/symbol top row using the same 14-cell template as English (`! 1`, `@ 2`, …, `+\n=`, `⌫`) with `\n`-dual labels and `longPressCode`.
- Row 2: `→` + the alpha keys from the IM's existing phone JSON, padded right with the same `『\n「`, `』\n」`, `？\n、` CJK cluster used by the phonetic layout. Do **not** generate `|\n、` here.
- Row 3: `abc` IM-toggle + alpha keys + `: ;` + `。\n，` + `search`.
- Row 4: `⇧` + alpha keys + dual punctuation cells + `⇧`.
- Row 5: bottom-row template.

For each generated IM, the per-row alpha-key list comes 1:1 from the existing phone JSON in the same order. The generator must be **source-first**:
- Harvest source keys that belong in fixed iPad positions before adding fallbacks (`-`, `=`, `[`, `]`, `\`, bottom-row printable symbols).
- Preserve harvested keys with IM sublabels. Do not replace an IM component key with a generic punctuation fallback.
- If a harvested key has no sublabel, it may be upgraded in place to the matching dual-sliding iPad form.
- Add a dual-sliding fallback only when the source layout does not already provide that slot.
- Strip promoted keys from their original source row so the layout does not duplicate them.

Row-specific source-first rules:
- Top row: use source `-` / `=` if present; upgrade no-sublabel keys to `_\n-` / `+\n=`. If `=` is missing, add fallback `+\n=`. Do not drop the row below 14 keys including backspace.
- QWERTY row: use source `[` and `]` if present; upgrade no-sublabel keys to bracket duals. If missing, add `『\n「` / `』\n」`. The CJK punctuation slot is `？\n、`; do not synthesize `|\n、`. A source `\` key with an IM sublabel may be preserved, but a plain backslash/pipe key is not converted into `|\n、`.
- ASDF row: use source `;` / `:` if present. Preserve it when it has an IM sublabel; otherwise upgrade in place to `；\n：`. If missing, add fallback `；\n：`. `。\n，` is appended on ASDF left of search/Enter, not in the bottom row.
- ZXCV row: upgrade existing `,` / `.` / `/` without sublabels to `<\n,` / `>\n.` / `?\n/`. Add missing fallbacks only if neither the base key nor its shift equivalent already exists.

Only the **scaffolding columns** (top number row, side modifiers, right-edge cluster, bottom-row template, second shift key) are added. **No alpha key is removed; no key width is shrunk to fit.** When the alpha-key count for a row falls short of the target row count, pad with transparent spacers (`code = 0`, `label = ""`, no background drawn) so the surviving keys keep the iPad cell width.

EZ and HS are explicit exceptions: `lime_ez`, `lime_ez_shift`, `lime_hs`, and `lime_hs_shift` must be excluded from iPad layout script generation. Do not generate, regenerate, or modify their `_ipad` layout files.

The row-2 CJK punctuation key is always `？\n、`: tap emits `、` (12289), slide/long-press emits `？` (65311). It replaces the older `|\n、` form; Chinese IM iPad layouts must not use pipe as that key's slide output.

Phone-only numpads (`phone.json`, `phone_number.json`, `phone_shift.json`, `phone_simple.json`) do **not** get an iPad variant — they fall through to the phone JSON for `.phonePad` text fields where iPad mirrors the iPhone numpad UI.

#### 4.2.7 Transparent spacer key spec

Spacer keys (used to keep alpha keys at iPad cell width when an IM has fewer keys per row than the screenshot scaffold):

```
{ "code": 0, "codes": [0], "label": "", "sublabel": "",
  "widthPercent": 6.66, "icon": "", "isModifier": false,
  "isRepeatable": false, "isSticky": false,
  "popupKeyboard": "", "popupCharacters": "" }
```

`KeyboardView.makeKeyButton` needs a tiny addition: if `keyDef.code == 0 && keyDef.label.isEmpty && keyDef.icon.isEmpty`, render an empty placeholder view (no background, no shadow, no touch handler). This is the only code change needed to support spacers; everything else is JSON.

**Touch behavior over spacers/gaps (post touch-rewrite).** The spacer *view* has no touch handler, but touches over it are not dead: the row's single `KeyTouchLayer` spans the **full row width** (keys centered via a layout guide), so a tap on a spacer or an inter-key gap resolves to the **nearest real key by proximity** (`KeyDetector`) — taps beyond the proximity threshold of any key are ignored. For that to work inside a keyboard extension the layer's hit surface must be non-transparent: it uses `LayoutMetrics.TouchTrap.fill` (`white 0.5, alpha 0.01`), because iOS drops touches on fully transparent pixels *before* hit-testing ([IOS_CANDI_TOUCH.md §Resolution](IOS_CANDI_TOUCH.md); commit `7188a4ae`). The expanded candidate panel (§5) applies the same `TouchTrap.fill` to its scroll content and cells so gaps/ragged row-ends stay tappable and scrollable. See [ANDROID_IPHONE_KEYBOARD.md §Touch Handling](ANDROID_IPHONE_KEYBOARD.md) for the shared iPhone/iPad rationale.

### 4.3 Key-height & font policy — dedicated iPad dimension set

Do **not** keep the existing `idiomMultiplier` (currently `1.5` on iPad) approach. Multiplying phone dimensions:
- Mixes two unrelated tuning axes (`keySizeScale` user pref × `idiomMultiplier` device class) and makes per-orientation tuning impossible.
- Forces label fonts and key heights to scale together, which gives oversized fonts on iPad.
- Will double-scale once the new `_ipad.json` files (which already encode iPad widths) ship.

Instead, introduce a parallel **iPad dimension set** in `KeyboardView.swift` and pick the active set once per layout-rebuild based on `UIDevice.current.userInterfaceIdiom == .pad`.

New constants (names mirror existing phone ones with an `iPad` suffix):

| Phone constant (current) | iPad equivalent (new) | Suggested value |
| --- | --- | --- |
| `rowHeightPortrait = 50` | `rowHeightPortraitIPad` | `64` |
| `bottomRowHeightPortrait = 54` | `bottomRowHeightPortraitIPad` | `68` |
| `rowHeightLandscape = 36` | `rowHeightLandscapeIPad` | `60` |
| `bottomRowHeightLandscape = 38` | `bottomRowHeightLandscapeIPad` | `64` |
| `keyHGap = 5`, `keyVGap = 2` | `keyHGapIPad = 7`, `keyVGapIPad = 4` | wider gutters |
| `keyCornerRadius = 6` | `keyCornerRadiusIPad` | `8` |
| `keySingleLabelFont` (22 regular) | `keySingleLabelFontIPad` | `24` regular |
| `keyLabelFont` (16 light)         | `keyLabelFontIPad`       | `20` light |
| `keySublabelFont` (22 regular)    | `keySublabelFontIPad`    | `24` regular |
| `keyLabelFontLand` (16 light)     | `keyLabelFontLandIPad`   | `20` light |
| `keySublabelFontLand` (22 regular)| `keySublabelFontLandIPad`| `24` regular |

> Note: in the shipped implementation, the landscape and portrait font getters
> resolve to the same `isPad ? … : …` ternaries — there are no separate
> `*Land*IPad` constants. Landscape font sizes match portrait on both phone
> and iPad. The `*Land*` rows are kept for naming parity with the phone
> getters.

Resolution helpers in `KeyboardView`:

```
private let isPad = UIDevice.current.userInterfaceIdiom == .pad

private var rowHeight: CGFloat {
    let base = isLandscape
        ? (isPad ? rowHeightLandscapeIPad : rowHeightLandscape)
        : (isPad ? rowHeightPortraitIPad  : rowHeightPortrait)
    return base * keySizeScale     // user pref still applies
}
```

Same pattern for `bottomRowHeight`, `keyHGap`, `keyVGap`, `keyCornerRadius`, and every `key*Font*` reference inside `styleKeyContent` / `makeKeyButton`. **Delete `idiomMultiplier` outright**; it is no longer needed and would compound with the new constants.

User preferences still apply on top:
- `keySizeScale` (`keyboard_size` pref, 0.8–1.2) multiplies the resolved row height.
- `font_size` pref multiplies the label fonts via `fontScale` (analogous to how `CandidateBarView.fontScale` works today).

Net effect:
- iPad portrait alpha row ≈ 64 × 1.1 = 70.4pt, vs the phone's 50 × 1.1 = 55pt.
- iPad labels read clearly at finger distance without dragging the row height with them.
- Phone behavior bit-for-bit unchanged because the phone code path still reads the original constants.

### 4.4 Popup keyboards on iPad

`popup_*` layouts (`popup_punctuation`, `popup_smileys`, `popup_domains`, `popup_c_punctuation`, `popup_symbol_mode`, `popup_template`) are loaded via the same `LayoutLoader.load(...)` path through `resolvePopup(_:)`. The `_ipad` fallback applies automatically. Provide `_ipad.json` variants only where the phone popup has < 6 columns; on iPad the popup grid can grow to 7–9 columns and the cell height should match the new iPad key height. Otherwise the phone popup looks adequate.

### 4.5 iPad top-row dual keys (number + symbol with slide-down)

Goal: replicate Apple's iPad QWERTY top row where every key shows two glyphs (e.g. `! / 1`, `@ / 2`) and the user can either:
- **Tap** → enter the **primary** glyph (number / shifted-symbol shown on top, e.g. `!` on the `! 1` key in the unshifted screenshot, or just `1` in some IM layouts).
- **Slide finger down off the key** (without releasing) → enter the **secondary** glyph (the small character shown beneath the primary).
- **Long-press** → show a single-key preview displaying **only the secondary** glyph; releasing commits the secondary glyph.

Both alternates resolve to the same character; the two gestures are just different ways to reach it.

JSON encoding (no schema change required):
- The existing `KeyDef` already has `code` (primary), `sublabel` (rendered glyph string), and `longPressCode` (already wired through `LayoutLoader.swift:168`). Re-use `longPressCode` to carry the secondary character's code point. The dual label is encoded via the existing Android-style `"!\\n1"` pattern that `splitLabel(_:)` already parses.
- Example top-row key in `lime_english_ipad.json`:
  ```
  { "code": 33, "label": "!\\n1", "sublabel": "",
    "widthPercent": 6.6, "longPressCode": 49,
    "isRepeatable": false, "isModifier": false, "isSticky": false,
    "popupKeyboard": "", "popupCharacters": "" }
  ```
  `code = 33` (`!`) is the tap result; `longPressCode = 49` (`1`) is the slide-down / long-press result.
- Layout author convention: top row uses `\n`-split labels; other rows do not. Detection in code: a key is "iPad dual-row key" iff `longPressCode != 0` **and** `popupKeyboard` is empty (so it is not confused with a popup key).

Touch handling additions in `KeyboardView` (touch handlers in `keyDown / keyUp` already exist around line 730+):
- Track touch start point. In `touchesMoved` (or via a new `UIPanGestureRecognizer` attached only to dual-row keys), if vertical translation exceeds a threshold (`24pt` portrait, `16pt` landscape — roughly half a key height) **and** the key has a non-zero `longPressCode`:
  - Cancel the pending tap (set `wasLongPressed = true` so `keyUp` skips the tap dispatch).
  - Update the visible key label in-place to render **only the secondary** glyph (small `sublabel`/secondary text becomes the primary; primary hides) — this matches Apple's behavior described in the user request ("the original key shows only the symbol to be entered").
  - Fire `delegate?.keyboardView(self, didPress: KeyDef(code: keyDef.longPressCode, …))` once on touchUp.
  - On `touchUp` / `touchCancel`, restore the original dual label.
- For long-press on a dual-row key: the existing `UILongPressGestureRecognizer` already handles `popupKeyboard != ""` keys. Add a parallel branch for `longPressCode != 0` keys that:
  - Shows the standard key preview (`showPreviewFor`) **but with `KeyDef` swapped to display only the secondary glyph** — implement by passing a synthesized `KeyDef(label: secondaryGlyph, sublabel: "", code: longPressCode)` to the preview path. See §4.6 for how this special preview interacts with the new "no preview on iPad" rule.
  - On gesture `.ended`, fires `didPress` with the `longPressCode`.

Phone behavior unchanged: phone JSON files do not carry `\n`-split top-row keys, and `longPressCode` on phone keys is currently used for shift-state alternates and the dismiss-key dual function — those code paths remain intact (they do not use the slide-down branch because the dual-row detection requires the iPad device class **and** the new iPad layout).

Gating: the new slide-down + long-press-shows-secondary behavior fires only when `UIDevice.current.userInterfaceIdiom == .pad` **and** the loaded layout id ends with `_ipad`.

### 4.6 iPad key-preview rules

Apple's stock iPad keyboard does **not** show key previews because keys are large enough that the press-state color change is sufficient visual feedback. Replicate that.

Current behavior (phone, `KeyboardView.swift:730+` in `keyDown`):
- Every non-modifier, non-icon, non-space key calls `delegate?.keyboardView(self, showPreviewFor:)` on touchDown.

iPad rules:
1. **Default: no preview.** When `isPad`, the `keyDown` branch that calls `showPreviewFor` is skipped for all regular keys. Visual feedback comes from the existing `btn.backgroundColor = pressedKeyColor` line, which is already applied unconditionally — no change there.
2. **Top-row dual keys (§4.5) — slide-down gesture: still no preview**, but the source key's label morphs in-place to show **only the secondary glyph** while the slide is in progress. Implement by toggling the dual-label container's visibility (hide primary, show only the secondary at the larger primary-font size). Restore on touchUp/cancel.
3. **Top-row dual keys — long-press gesture: show preview, secondary glyph only.** When the long-press recognizer fires `.began` on a key with `longPressCode != 0`, dispatch a normal `showPreviewFor` call but with a synthesized `KeyDef` whose `label` is the secondary glyph and whose `sublabel` is empty. This reuses the existing preview popup machinery in `KeyboardViewController.showPreviewFor(_:keyRect:)` (line 1937) — no new view code, just a different `KeyDef` payload. Dismiss on `.ended` / `.cancelled`.
4. **Popup keys** (long-press to open `popup_*` keyboards) are unchanged: the popup keyboard itself is the visual feedback; no change to that path on iPad.

Implementation sketch in `KeyboardView.keyDown`:

```
if keyDef.icon.isEmpty && !keyDef.isModifier
    && keyDef.code != LimeKeyCode.space.rawValue
    && !isPad {                                  // ← NEW: phone-only preview
    let keyRect = btn.convert(btn.bounds, to: self)
    delegate?.keyboardView(self, showPreviewFor: keyDef, keyRect: keyRect)
}
```

The slide-down preview-suppression is automatic because we never started a preview to begin with. The long-press preview path is added in the new dual-row long-press branch from §4.5.

`KeyboardViewController.showPreviewFor(_:)` (line 1937) is unchanged — it just renders whatever `KeyDef.label` it is handed, so the synthesized secondary-only payload "just works."

---

## 5. Candidate bar (CandidateBarView) — dedicated iPad dimension set

Source: `CandidateBarView.swift` lines 65–75.

Apply the same "parallel constant set" pattern as §4.3 — do not multiply phone values, declare new ones.

| Phone constant (current) | iPad equivalent (new) | Suggested value |
| --- | --- | --- |
| `baseCandidateFontSize = 22` | `baseCandidateFontSizeIPad` | `30` |
| `baseComposingCodeFontSize = 16` | `baseComposingCodeFontSizeIPad` | `22` |
| `candidateHPad = 10` | `candidateHPadIPad` | `16` |
| `dividerWidth = 1` | `dividerWidthIPad` | `1` (unchanged) |
| Bar height anchor in `KeyboardViewController` (≈42pt) | `candidateBarHeightIPad` | `60pt` |
| Selkey number prefix font (currently `0.6 × candidate`) | same ratio against the iPad base | auto-tracks |
| `moreButton` chevron `pointSize = 18` | `moreButtonPointSizeIPad` | `24` |
| Highlight pill insets `padX = 4`, `padY = 2` | `padXIPad = 6`, `padYIPad = 4` | wider pill |

Resolution helpers in `CandidateBarView`:

```
private let isPad = UIDevice.current.userInterfaceIdiom == .pad

private var baseCandidateFontSize:     CGFloat { isPad ? 30 : 22 }
private var baseComposingCodeFontSize: CGFloat { isPad ? 22 : 16 }
private var candidateHPad:             CGFloat { isPad ? 16 : 10 }
```

The existing `fontScale` (driven by user `font_size` pref) still multiplies the resolved base font, so user preferences continue to scale on iPad just as on phone.

The candidate bar's `heightAnchor` is set in `KeyboardViewController` where `candidateBar` is added; switch that constant to read from a single `candidateBarHeight` helper so iPad picks up the new value without other changes. `applyHeight()` already aggregates the bar height into the total keyboard view height — no other plumbing needed.

---

## 6. iPad system shortcut bar (right of undo/redo/paste) — investigation

**Question**: can LimeIME render its candidates into the iPad built-in shortcut bar shown in the attached screenshots (the strip above the keyboard with undo / redo / paste icons on the left and `ABC` / dismiss icons on the right)?

**Answer: NO.** A 3rd-party `UIInputViewController` cannot write to that bar.

Reason (verified against UIKit API surface and the codebase):

1. The bar is a **`UITextInputAssistantItem`** belonging to the **host app's** first responder, not the keyboard extension. The host app controls `leadingBarButtonGroups` and `trailingBarButtonGroups` (the undo / redo / paste / format icons you see). Source of truth: `UIResponder.inputAssistantItem` — only the responder owns it.
2. `UIInputViewController` does **not** expose any property that injects content into the host's `inputAssistantItem`. There is no `quickTypeBar`, no `predictionBar`, and no public hook.
3. The **center "QuickType" prediction strip** that Apple's own keyboard fills with autocorrect candidates is rendered by the **system keyboard process** using private interfaces. 3rd-party keyboards literally cannot draw into that region — it is hidden when the active keyboard is a non-Apple extension. (Compare Gboard, SwiftKey: they also draw their own candidate bar inside the keyboard extension on iPad.)
4. The keyboard extension's view (`view` of `UIInputViewController`) is anchored to the bottom of the screen and cannot extend above the host's input assistant bar.
5. Even with **Full Access**, no entitlement unlocks the assistant bar. Full Access only enables network / pasteboard / shared container.

**Conclusion / implication for the plan:**
- Keep candidates rendered in our own `CandidateBarView` (sized up per §5).
- The iPad shortcut bar (undo/redo/paste/etc.) will remain blank in its center on iPad when LimeIME is active, as it does for every 3rd-party keyboard. There is no workaround.
- Document this clearly in the user-facing release notes so users do not expect candidates "up there".

If a future requirement is to **also build a companion app** (not a keyboard extension) that has its own `UITextView`, that app could populate its own `inputAssistantItem` with custom buttons — but that does not help while LimeIME is acting as a system-wide keyboard inside other apps.

---

## 7. Conversion script (offline, optional)

Not recommended for the iPad layouts. The phone-side `convert_keyboard_layouts.py` produces the existing `lime_*.json` from Android XML; it does not know the iPad scaffolding. Hand-author each `*_ipad.json` from the explicit specs in §4.2.1–4.2.6 — the per-row key sequences are short and the screenshots are the authoritative reference.

If an automation aid is desired later, a small `.claude/scripts/wrap_ipad_layout.py` could take a phone JSON and an alpha-key-list and emit the iPad scaffold (top row + side mods + right cluster + dual shift + bottom-row template + spacers). Keep it strictly opt-in and check in the generated files; the script is never a runtime dependency.

---

## 8. Settings impact

- Add **no new persisted prefs** for the iPad layout itself — selection is purely device-class driven.
- `splitKeyboardMode` (existing; phone=ignored, pad=respected at `KeyboardViewController.swift:213`) continues to drive split rendering for the iPad layout. The new `_ipad.json` files are designed so a vertical "split gap" still produces a sensible left/right halving (key counts on each row are even or have a natural mid-row break).
- Existing `font_size` and `key_size` preferences continue to scale iPad fonts and key heights.

---

## 9. Test plan

Manual (no automated coverage in `LimeTests/` for layout JSON):
1. iPhone (any) — every IM still loads original layout (no `_ipad` suffix sneaks in). Verify `LayoutLoader` returns the unsuffixed JSON.
2. iPad portrait — open each generated IM (phonetic / array / cj / dayi / et26 / hsu / wb / english) and confirm the new wider layout renders, top row visible, bottom row icon set correct. EZ and HS are excluded from generated iPad layouts.
3. iPad landscape — same, plus split-keyboard mode 2 (landscape-only) renders the new layout split.
4. iPad with `font_size` pref at min and max — candidate bar fonts scale.
5. iPad with empty candidate row — right side of candidate bar shows hamburger/options button on the right/backspace edge, sized to normal-key width rather than the wider backspace key. Verify light/dark contrast, full-height top/bottom touch area, and tapping it opens the same menu as long-pressing keyboard/dismiss.
6. Numeric textfields (`.numberPad`, `.phonePad`) on iPad — fall back to existing `phone*.json` (no `_ipad` variant by design).
7. Popup keyboards — long-press a key with `popup_punctuation`, confirm popup either loads `_ipad` variant or falls back to phone variant.
8. **iPad top-row dual key (§4.5)**:
   - Tap `! 1` key → emits `!` (primary code).
   - Press, slide finger down off the key, release → emits `1` (secondary `longPressCode`); during the slide the key's label morphs to show only `1`; **no preview popup appears**.
   - Long-press `! 1` key for ~0.4s without releasing → preview popup appears showing **only `1`**; release commits `1`.
9. **iPad preview suppression (§4.6)**: tap any non-top-row key (e.g. `q`, `a`, `z`) → only the press-state color change fires; no preview popup appears.
10. DB sanity — verify both DBs are byte-identical to the previous build:
   - Import seeds: `shasum Database/array.limedb Database/array10.limedb` unchanged.
   - Runtime `lime.db` in the App Group container: schema and `keyboard` / `im` table contents unchanged after a fresh install + first-launch import.

---

## 10. Out of scope (explicit)

- Apple Pencil / hover support on the candidate bar.
- Floating mini-keyboard (iPad's pinch-to-shrink keyboard) — Apple does not give 3rd-party keyboards a hook for this; LimeIME stays full-width.
- macOS Catalyst / Mac keyboard variant.
- Any change to Android (`LimeStudio/`) layouts.
- Any DB / IM-table edit (neither the `.limedb` import seeds nor the runtime `lime.db`).

---

## 11. Roll-out order

1. Add `LayoutLoader._ipad` fallback + cache-key fix. Phone behavior unchanged because no `_ipad.json` files exist yet → fallback returns the phone JSON.
2. Add transparent-spacer support in `KeyboardView.makeKeyButton` (§4.2.7) — single 5-line addition; no phone-side regression because no phone JSON contains `code = 0`.
3. Land `lime_english_ipad.json` + `lime_english_shift_ipad.json` (§4.2.1–4.2.2) first; validate against screenshot 2 on iPad simulator (12.9", 11", 10.9", mini).
4. Land `symbols1_ipad.json` (§4.2.3) and validate against screenshot 1.
5. Land `lime_phonetic_ipad.json` + `lime_phonetic_shift_ipad.json` (§4.2.5) and validate against screenshot 3.
6. Land `lime_abc_ipad.json` (= English with `lime_abc` semantics), then the remaining IMs (§4.2.6) one at a time, each validated against the screenshot scaffold.
7. **Replace** `KeyboardView.idiomMultiplier` with the parallel iPad constant set from §4.3; delete the multiplier.
8. Apply the parallel iPad constant set in `CandidateBarView` per §5 (and bump the bar `heightAnchor` in `KeyboardViewController`).
9. Implement §4.5 dual-row touch handling (slide-down + long-press → secondary glyph) and §4.6 preview suppression in `KeyboardView.keyDown` / touch handlers. Phone path stays untouched.
10. Optional: add `_ipad` popup variants where columns benefit.
11. Update release notes documenting (a) iPad keyboards now match Apple's stock layout, (b) iPad top-row slide-down + long-press (§4.5), (c) iPad no-preview rule (§4.6), (d) the iPad shortcut bar limitation from §6.

---

## Appendix A. Current Generated Chinese IM iPad Layouts

This appendix is a literal inventory of the current generated JSON files in `LimeIME-iOS/LimeKeyboard/Layouts`. It is generated from the checked-in `*_ipad.json` files, not from the prose rules.

Notation: `label/sublabel` means the JSON key has both `label` and `sublabel`. Icon-only keys are named by role: `tab`, `backspace`, `shift`, `emoji`, `space`, or `dismiss`.

Excluded from generator output and intentionally absent here: `lime_ez_ipad*.json`, `lime_hs_ipad*.json`.

### lime_phonetic_ipad.json
- Row 1 (row, 14 keys): `` ~\n` `` | `1/ㄅ` | `2/ㄉ` | `3/ˇ` | `4/ˋ` | `5/ㄓ` | `6/ˊ` | `7/˙` | `8/ㄚ` | `9/ㄞ` | `0/ㄢ` | `-/ㄦ` | `+\n=` | `backspace`
- Row 2 (row, 14 keys): `tab` | `q/ㄆ` | `w/ㄊ` | `e/ㄍ` | `r/ㄐ` | `t/ㄔ` | `y/ㄗ` | `u/一` | `i/ㄛ` | `o/ㄟ` | `p/ㄣ` | `『\n「` | `』\n」` | `？\n、`
- Row 3 (row, 13 keys): `abc` | `a/ㄇ` | `s/ㄋ` | `d/ㄎ` | `f/ㄑ` | `g/ㄕ` | `h/ㄘ` | `j/ㄨ` | `k/ㄜ` | `l/ㄠ` | `;/ㄤ` | `。\n，` | `enter`
- Row 4 (row, 12 keys): `shift` | `z/ㄈ` | `x/ㄌ` | `c/ㄏ` | `v/ㄒ` | `b/ㄖ` | `n/ㄙ` | `m/ㄩ` | `,/ㄝ` | `./ㄡ` | `//ㄥ` | `shift`
- Row 5 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_phonetic_ipad_shift.json
- Row 1 (row, 14 keys): `~` | `!/ㄅ` | `@/ㄉ` | `#/ˇ` | `$/ˋ` | `%/ㄓ` | `^/ˊ` | `&/˙` | `*/ㄚ` | `(/ㄞ` | `)/ㄢ` | `…` | `+` | `backspace`
- Row 2 (row, 14 keys): `tab` | `Q/ㄆ` | `W/ㄊ` | `E/ㄍ` | `R/ㄐ` | `T/ㄔ` | `Y/ㄗ` | `U/一` | `I/ㄛ` | `O/ㄟ` | `P/ㄣ` | `『` | `』` | `？`
- Row 3 (row, 13 keys): `abc` | `A/ㄇ` | `S/ㄋ` | `D/ㄎ` | `F/ㄑ` | `G/ㄕ` | `H/ㄘ` | `J/ㄨ` | `K/ㄜ` | `L/ㄠ` | `:` | `。` | `enter`
- Row 4 (row, 12 keys): `shift` | `Z/ㄈ` | `X/ㄌ` | `C/ㄏ` | `V/ㄒ` | `B/ㄖ` | `N/ㄙ` | `M/ㄩ` | `<` | `>` | `?` | `shift`
- Row 5 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_array_ipad.json
- Row 1 (row, 14 keys): `tab` | `q/1⇡` | `w/2⇡` | `e/3⇡` | `r/4⇡` | `t/5⇡` | `y/6⇡` | `u/7⇡` | `i/8⇡` | `o/9⇡` | `p/0⇡` | `『\n「` | `』\n」` | `backspace`
- Row 2 (row, 13 keys): `abc` | `a/1−` | `s/2−` | `d/3−` | `f/4−` | `g/5−` | `h/6−` | `j/7−` | `k/8−` | `l/9−` | `;/0−` | `。\n，` | `enter`
- Row 3 (row, 12 keys): `shift` | `z/1⇣` | `x/2⇣` | `c/3⇣` | `v/4⇣` | `b/5⇣` | `n/6⇣` | `m/7⇣` | `,/8⇣` | `./9⇣` | `//0⇣` | `shift`
- Row 4 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_array_ipad_shift.json
- Row 1 (row, 14 keys): `tab` | `Q/1⇡` | `W/2⇡` | `E/3⇡` | `R/4⇡` | `T/5⇡` | `Y/6⇡` | `U/7⇡` | `I/8⇡` | `O/9⇡` | `P/0⇡` | `『` | `』` | `backspace`
- Row 2 (row, 13 keys): `abc` | `A/1−` | `S/2−` | `D/3−` | `F/4−` | `G/5−` | `H/6−` | `J/7−` | `K/8−` | `L/9−` | `:/0−` | `。` | `enter`
- Row 3 (row, 12 keys): `shift` | `Z/1⇣` | `X/2⇣` | `C/3⇣` | `V/4⇣` | `B/5⇣` | `N/6⇣` | `M/7⇣` | `</8⇣` | `>/9⇣` | `?/0⇣` | `shift`
- Row 4 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_array_number_ipad.json
- Row 1 (row, 14 keys): `` ~\n` `` | `!\n1` | `@\n2` | `#\n3` | `$\n4` | `%\n5` | `^\n6` | `&\n7` | `*\n8` | `(\n9` | `)\n0` | `…\n—` | `+\n=` | `backspace`
- Row 2 (row, 14 keys): `tab` | `q/1⇡` | `w/2⇡` | `e/3⇡` | `r/4⇡` | `t/5⇡` | `y/6⇡` | `u/7⇡` | `i/8⇡` | `o/9⇡` | `p/0⇡` | `『\n「` | `』\n」` | `？\n、`
- Row 3 (row, 13 keys): `abc` | `a/1−` | `s/2−` | `d/3−` | `f/4−` | `g/5−` | `h/6−` | `j/7−` | `k/8−` | `l/9−` | `;/0−` | `。\n，` | `enter`
- Row 4 (row, 12 keys): `shift` | `z/1⇣` | `x/2⇣` | `c/3⇣` | `v/4⇣` | `b/5⇣` | `n/6⇣` | `m/7⇣` | `,/8⇣` | `./9⇣` | `//0⇣` | `shift`
- Row 5 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_array_number_ipad_shift.json
- Row 1 (row, 14 keys): `~` | `!` | `@` | `#` | `$` | `%` | `^` | `&` | `*` | `(` | `)` | `…` | `+` | `backspace`
- Row 2 (row, 14 keys): `tab` | `Q/1⇡` | `W/2⇡` | `E/3⇡` | `R/4⇡` | `T/5⇡` | `Y/6⇡` | `U/7⇡` | `I/8⇡` | `O/9⇡` | `P/0⇡` | `『` | `』` | `？`
- Row 3 (row, 13 keys): `abc` | `A/1−` | `S/2−` | `D/3−` | `F/4−` | `G/5−` | `H/6−` | `J/7−` | `K/8−` | `L/9−` | `:/0−` | `。` | `enter`
- Row 4 (row, 12 keys): `shift` | `Z/1⇣` | `X/2⇣` | `C/3⇣` | `V/4⇣` | `B/5⇣` | `N/6⇣` | `M/7⇣` | `</8⇣` | `>/9⇣` | `?/0⇣` | `shift`
- Row 5 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_cj_ipad.json
- Row 1 (row, 14 keys): `tab` | `q/手` | `w/田` | `e/水` | `r/口` | `t/廿` | `y/卜` | `u/山` | `i/戈` | `o/人` | `p/心` | `『\n「` | `』\n」` | `backspace`
- Row 2 (row, 13 keys): `abc` | `a/日` | `s/尸` | `d/木` | `f/火` | `g/土` | `h/竹` | `j/十` | `k/大` | `l/中` | `；\n：` | `。\n，` | `enter`
- Row 3 (row, 12 keys): `shift` | `z/重` | `x/難` | `c/金` | `v/女` | `b/月` | `n/弓` | `m/一` | `<\n,` | `>\n.` | `?\n/` | `shift`
- Row 4 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_cj_ipad_shift.json
- Row 1 (row, 14 keys): `tab` | `Q/手` | `W/田` | `E/水` | `R/口` | `T/廿` | `Y/卜` | `U/山` | `I/戈` | `O/人` | `P/心` | `『` | `』` | `backspace`
- Row 2 (row, 13 keys): `abc` | `A/日` | `S/尸` | `D/木` | `F/火` | `G/土` | `H/竹` | `J/十` | `K/大` | `L/中` | `；` | `。` | `enter`
- Row 3 (row, 12 keys): `shift` | `Z/重` | `X/難` | `C/金` | `V/女` | `B/月` | `N/弓` | `M/一` | `<` | `>` | `?` | `shift`
- Row 4 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_cj_number_ipad.json
- Row 1 (row, 14 keys): `` ~\n` `` | `!\n1` | `@\n2` | `#\n3` | `$\n4` | `%\n5` | `^\n6` | `&\n7` | `*\n8` | `(\n9` | `)\n0` | `…\n—` | `+\n=` | `backspace`
- Row 2 (row, 14 keys): `tab` | `q/手` | `w/田` | `e/水` | `r/口` | `t/廿` | `y/卜` | `u/山` | `i/戈` | `o/人` | `p/心` | `『\n「` | `』\n」` | `？\n、`
- Row 3 (row, 13 keys): `abc` | `a/日` | `s/尸` | `d/木` | `f/火` | `g/土` | `h/竹` | `j/十` | `k/大` | `l/中` | `；\n：` | `。\n，` | `enter`
- Row 4 (row, 12 keys): `shift` | `z/重` | `x/難` | `c/金` | `v/女` | `b/月` | `n/弓` | `m/一` | `<\n,` | `>\n.` | `?\n/` | `shift`
- Row 5 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_cj_number_ipad_shift.json
- Row 1 (row, 14 keys): `~` | `!` | `@` | `#` | `$` | `%` | `^` | `&` | `*` | `(` | `)` | `…` | `+` | `backspace`
- Row 2 (row, 14 keys): `tab` | `Q/手` | `W/田` | `E/水` | `R/口` | `T/廿` | `Y/卜` | `U/山` | `I/戈` | `O/人` | `P/心` | `『` | `』` | `？`
- Row 3 (row, 13 keys): `abc` | `A/日` | `S/尸` | `D/木` | `F/火` | `G/土` | `H/竹` | `J/十` | `K/大` | `L/中` | `；` | `。` | `enter`
- Row 4 (row, 12 keys): `shift` | `Z/重` | `X/難` | `C/金` | `V/女` | `B/月` | `N/弓` | `M/一` | `<` | `>` | `?` | `shift`
- Row 5 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_dayi_ipad.json
- Row 1 (row, 14 keys): `` ~\n` `` | `!\n1` | `@\n2` | `#\n3` | `$\n4` | `%\n5` | `^\n6` | `&\n7` | `*\n8` | `(\n9` | `)\n0` | `…\n—` | `+\n=` | `backspace`
- Row 2 (row, 14 keys): `tab` | `q` | `w` | `e` | `r` | `t` | `y` | `u` | `i` | `o` | `p` | `『\n「` | `』\n」` | `？\n、`
- Row 3 (row, 13 keys): `abc` | `a` | `s` | `d` | `f` | `g` | `h` | `j` | `k` | `l` | `；\n：` | `。\n，` | `enter`
- Row 4 (row, 12 keys): `shift` | `z` | `x` | `c` | `v` | `b` | `n` | `m` | `<\n,` | `>\n.` | `?\n/` | `shift`
- Row 5 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_dayi_ipad_shift.json
- Row 1 (row, 14 keys): `~` | `!` | `@` | `#` | `$` | `%` | `^` | `&` | `*` | `(` | `)` | `…` | `+` | `backspace`
- Row 2 (row, 14 keys): `tab` | `Q` | `W` | `E` | `R` | `T` | `Y` | `U` | `I` | `O` | `P` | `『` | `』` | `？`
- Row 3 (row, 13 keys): `abc` | `A` | `S` | `D` | `F` | `G` | `H` | `J` | `K` | `L` | `；` | `。` | `enter`
- Row 4 (row, 12 keys): `shift` | `Z` | `X` | `C` | `V` | `B` | `N` | `M` | `<` | `>` | `?` | `shift`
- Row 5 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_dayi_sym_ipad.json
- Row 1 (row, 14 keys): `` ~\n` `` | `1/言` | `2/牛` | `3/目` | `4/四` | `5/王` | `6/門` | `7/田` | `8/米` | `9/足` | `0/金` | `…\n—` | `+\n=` | `backspace`
- Row 2 (row, 14 keys): `tab` | `q/石` | `w/山` | `e/一` | `r/工` | `t/糸` | `y/火` | `u/艸` | `i/木` | `o/口` | `p/耳` | `『\n「` | `』\n」` | `？\n、`
- Row 3 (row, 13 keys): `abc` | `a/人` | `s/革` | `d/日` | `f/土` | `g/手` | `h/鳥` | `j/月` | `k/立` | `l/女` | `;/虫` | `。\n，` | `enter`
- Row 4 (row, 12 keys): `shift` | `z/心` | `x/水` | `c/鹿` | `v/禾` | `b/馬` | `n/魚` | `m/雨` | `,/力` | `./舟` | `//竹` | `shift`
- Row 5 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_dayi_sym_ipad_shift.json
- Row 1 (row, 14 keys): `~` | `!/言` | `@/牛` | `#/目` | `$/四` | `%/王` | `^/門` | `&/田` | `*/米` | `(/足` | `)/金` | `…` | `+` | `backspace`
- Row 2 (row, 14 keys): `tab` | `Q/石` | `W/山` | `E/一` | `R/工` | `T/糸` | `Y/火` | `U/艸` | `I/木` | `O/口` | `P/耳` | `『` | `』` | `？`
- Row 3 (row, 13 keys): `abc` | `A/人` | `S/革` | `D/日` | `F/土` | `G/手` | `H/鳥` | `J/月` | `K/立` | `L/女` | `;/虫` | `。` | `enter`
- Row 4 (row, 12 keys): `shift` | `Z/心` | `X/水` | `C/鹿` | `V/禾` | `B/馬` | `N/魚` | `M/雨` | `,/力` | `./舟` | `//竹` | `shift`
- Row 5 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_et26_ipad.json
- Row 1 (row, 14 keys): `` ~\n` `` | `!\n1` | `@\n2` | `#\n3` | `$\n4` | `%\n5` | `^\n6` | `&\n7` | `*\n8` | `(\n9` | `)\n0` | `…\n—` | `+\n=` | `backspace`
- Row 2 (row, 14 keys): `tab` | `q\tㄗ/ㄟ` | `w\tㄘ/ㄝ` | `e/ㄧ` | `r/ㄜ` | `t\tㄊ/ㄤ` | `y/ㄔ` | `u/ㄩ` | `i/ㄞ` | `o/ㄛ` | `p\tㄆ/ㄡ` | `『\n「` | `』\n」` | `？\n、`
- Row 3 (row, 13 keys): `abc` | `a/ㄚ` | `s/ㄙ` | `d\t˙/ㄉ` | `f\tˊ/ㄈ` | `g\tㄓ/ㄐ` | `h\tㄏ/ㄦ` | `j\tˇ/ㄖ` | `k\tˋ/ㄎ` | `l\tㄌ/ㄥ` | `；\n：` | `。\n，` | `enter`
- Row 4 (row, 12 keys): `shift` | `z/ㄠ` | `x/ㄨ` | `c\tㄒ/ㄕ` | `v\tㄑ/ㄍ` | `b/ㄅ` | `n\tㄋ/ㄣ` | `m\tㄇ/ㄢ` | `<\n,` | `>\n.` | `?\n/` | `shift`
- Row 5 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_et26_ipad_shift.json
- Row 1 (row, 14 keys): `~` | `!` | `@` | `#` | `$` | `%` | `^` | `&` | `*` | `(` | `)` | `…` | `+` | `backspace`
- Row 2 (row, 14 keys): `tab` | `Q\tㄗ/ㄟ` | `W\tㄘ/ㄝ` | `E/ㄧ` | `R/ㄜ` | `T\tㄊ/ㄤ` | `Y/ㄔ` | `U/ㄩ` | `I/ㄞ` | `O/ㄛ` | `P\tㄆ/ㄡ` | `『` | `』` | `？`
- Row 3 (row, 13 keys): `abc` | `A/ㄚ` | `S/ㄙ` | `D\t˙/ㄉ` | `F\tˊ/ㄈ` | `G\tㄓ/ㄐ` | `H\tㄏ/ㄦ` | `J\tˇ/ㄖ` | `K\tˋ/ㄎ` | `L\tㄌ/ㄥ` | `；` | `。` | `enter`
- Row 4 (row, 12 keys): `shift` | `Z/ㄠ` | `X/ㄨ` | `C\tㄒ/ㄕ` | `V\tㄑ/ㄍ` | `B/ㄅ` | `N\tㄋ/ㄣ` | `M\tㄇ/ㄢ` | `<` | `>` | `?` | `shift`
- Row 5 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_et_41_ipad.json
- Row 1 (row, 14 keys): `` ~\n` `` | `1/˙` | `2/ˊ` | `3/ˇ` | `4/ˋ` | `%\n5` | `^\n6` | `7/ㄑ` | `8/ㄢ` | `9/ㄣ` | `0/ㄤ` | `-/ㄥ` | `=/ㄦ` | `backspace`
- Row 2 (row, 14 keys): `tab` | `q/ㄟ` | `w/ㄝ` | `e/一` | `r/ㄜ` | `t/ㄊ` | `y/ㄡ` | `u/ㄩ` | `i/ㄞ` | `o/ㄛ` | `p/ㄆ` | `『\n「` | `』\n」` | `？\n、`
- Row 3 (row, 13 keys): `abc` | `a/ㄚ` | `s/ㄙ` | `d/ㄉ` | `f/ㄈ` | `g/ㄐ` | `h/ㄏ` | `j/ㄖ` | `k/ㄎ` | `l/ㄌ` | `;/ㄗ` | `。\n，` | `enter`
- Row 4 (row, 12 keys): `shift` | `z/ㄠ` | `x/ㄨ` | `c/ㄒ` | `v/ㄍ` | `b/ㄅ` | `n/ㄋ` | `m/ㄇ` | `,/ㄓ` | `./ㄔ` | `//ㄕ` | `shift`
- Row 5 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_et_41_ipad_shift.json
- Row 1 (row, 14 keys): `~` | `!/˙` | `@/ˊ` | `#/ˇ` | `$/ˋ` | `%` | `^` | `&/ㄑ` | `*/ㄢ` | `(/ㄣ` | `)/ㄤ` | `…` | `+` | `backspace`
- Row 2 (row, 14 keys): `tab` | `Q/ㄟ` | `W/ㄝ` | `E/一` | `R/ㄜ` | `T/ㄊ` | `Y/ㄡ` | `U/ㄩ` | `I/ㄞ` | `O/ㄛ` | `P/ㄆ` | `『` | `』` | `？`
- Row 3 (row, 13 keys): `abc` | `A/ㄚ` | `S/ㄙ` | `D/ㄉ` | `F/ㄈ` | `G/ㄐ` | `H/ㄏ` | `J/ㄖ` | `K/ㄎ` | `L/ㄌ` | `:` | `。` | `enter`
- Row 4 (row, 12 keys): `shift` | `Z/ㄠ` | `X/ㄨ` | `C/ㄒ` | `V/ㄍ` | `B/ㄅ` | `N/ㄋ` | `M/ㄇ` | `<` | `>` | `?` | `shift`
- Row 5 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_hsu_ipad.json
- Row 1 (row, 14 keys): `` ~\n` `` | `!\n1` | `@\n2` | `#\n3` | `$\n4` | `%\n5` | `^\n6` | `&\n7` | `*\n8` | `(\n9` | `)\n0` | `…\n—` | `+\n=` | `backspace`
- Row 2 (row, 14 keys): `tab` | `q` | `w/ㄠ` | `e\tㄧ/ㄝ` | `r\tㄖ/ㄚ` | `t/ㄊ` | `y/ㄚ` | `u/ㄩ` | `i/ㄞ` | `o/ㄡ` | `p/ㄆ` | `『\n「` | `』\n」` | `？\n、`
- Row 3 (row, 13 keys): `abc` | `a\tㄘ/ㄟ` | `s\t˙/ㄙ` | `d\tˊ/ㄉ` | `f\tˇ/ㄈ` | `g\tㄍ/ㄜ` | `h\tㄏ/ㄛ` | `j\tˋ/ㄐㄓ` | `k\tㄎ/ㄤ` | `l\tㄌ/ㄦㄥ` | `；\n：` | `。\n，` | `enter`
- Row 4 (row, 12 keys): `shift` | `z/ㄗ` | `x/ㄨ` | `c\tㄒ/ㄕ` | `v\tㄑ/ㄔ` | `b/ㄅ` | `n\tㄋ/ㄣ` | `m\tㄇ/ㄢ` | `<\n,` | `>\n.` | `?\n/` | `shift`
- Row 5 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_hsu_ipad_shift.json
- Row 1 (row, 14 keys): `~` | `!` | `@` | `#` | `$` | `%` | `^` | `&` | `*` | `(` | `)` | `…` | `+` | `backspace`
- Row 2 (row, 14 keys): `tab` | `Q` | `W/ㄠ` | `E\tㄧ/ㄝ` | `R\tㄖ/ㄚ` | `T/ㄊ` | `Y/ㄚ` | `U/ㄩ` | `I/ㄞ` | `O/ㄡ` | `P/ㄆ` | `『` | `』` | `？`
- Row 3 (row, 13 keys): `abc` | `A\tㄘ/ㄟ` | `S\t˙/ㄙ` | `D\tˊ/ㄉ` | `F\tˇ/ㄈ` | `G\tㄍ/ㄜ` | `H\tㄏ/ㄛ` | `J\tˋ/ㄐㄓ` | `K\tㄎ/ㄤ` | `L\tㄌ/ㄦㄥ` | `；` | `。` | `enter`
- Row 4 (row, 12 keys): `shift` | `Z/ㄗ` | `X/ㄨ` | `C\tㄒ/ㄕ` | `V\tㄑ/ㄔ` | `B/ㄅ` | `N\tㄋ/ㄣ` | `M\tㄇ/ㄢ` | `<` | `>` | `?` | `shift`
- Row 5 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_wb_ipad.json
- Row 1 (row, 3 keys): `一` | `丨` | `丿`
- Row 2 (row, 4 keys): `abc` | `丶` | `ㄣ` | `backspace`
- Row 3 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`

### lime_wb_ipad_shift.json
- Row 1 (row, 14 keys): `tab` | `Q/手` | `W/田` | `E/水` | `R/口` | `T/廿` | `Y/卜` | `U/山` | `I/戈` | `O/口` | `P/心` | `『` | `』` | `backspace`
- Row 2 (row, 13 keys): `abc` | `A/日` | `S/尸` | `D/木` | `F/火` | `G/土` | `H/竹` | `J/十` | `K/大` | `L/中` | `；` | `。` | `enter`
- Row 3 (row, 12 keys): `shift` | `Z/重` | `X/難` | `C/金` | `V/女` | `B/月` | `N/弓` | `M/一` | `<` | `>` | `?` | `shift`
- Row 4 (bottom, 6 keys): `globe` | `.?123` | `emoji` | `space` | `.?123` | `dismiss`
