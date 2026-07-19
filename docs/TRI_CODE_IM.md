# Plan: 三碼輸入法 (Tri-Code / 3code) Built-in IM — issue #159 / feat#159

## Goal

Ship 三碼輸入法 by 無書自通 (https://3code-type.github.io/, table v.20260715.1, free use / non-commercial) as a built-in downloadable IM on Android and iOS in one pass:

1. Convert `3code.cin` into a `.limedb` and commit it to `Database/`.
2. Add it to the Android and iOS IM install (download) lists.
3. Create a new **LIME_NUM_SYM2** keyboard (`limenumsym2`) on both platforms — same key set as `limenumsym` (a–z + `' , . / ;`, matching https://3code-type.github.io/) but restructured like `lime_dayi_sym`/`lime_phonetic`: shift + backspace on the bottom row, `, . /` in the z-row, `-` `=` removed, `〃`(`'`) key on the bottom row. Strict 10-keys-per-letter-row discipline.
4. All supporting code changes (whitelist, default keyboard, keyboard-row seeding, table migration, IM registration).
5. Update LICENSE acknowledgements.

Done means: on both platforms the user can download 三碼 from the install list, get the `limenumsym2` keyboard, type `lh` → 世, and the five symbol keys `' , . / ;` all enter codes.

## TL;DR

- Internal table name is **`tricode`** — NOT `3code`. LIME builds SQL by string concatenation and SQLite unquoted identifiers cannot start with a digit. Only the user-visible name is 三碼 / 3code.
- Cloud asset is **`Database/tricode.limedb`** in the modern spec format (`custom` mapping table + `im` property rows), per `docs/LIMEDB_SPEC.md`. Model file: `Database/hahacj.limedb` (inner `cj4.db`).
- New keyboard **`limenumsym2`** (LIME+數字符號鍵盤2), layouts `lime_num_sym2(.xml/_shift.xml)`. Same key set as `lime_number_symbol` (a–z + `;`(59) `'`(39) `,`(44) `.`(46) `/`(47)) but with the `lime_dayi_sym`/`lime_phonetic` row structure: `, . /` in the z-row, shift + backspace in the bottom row, `-` `=` dropped, and the `〃` key in the bottom row. All letter rows stay at 10 keys:

  ```text
  [ 1 ][ 2 ][ 3 ][ 4 ][ 5 ][ 6 ][ 7 ][ 8 ][ 9 ][ 0 ]
  [ q ][ w ][ e ][ r ][ t ][ y ][ u ][ i ][ o ][ p ]
  [ a ][ s ][ d ][ f ][ g ][ h ][ j ][ k ][ l ][ ; ]
  [ z ][ x ][ c ][ v ][ b ][ n ][ m ][ , ][ . ][ / ]   <- identical to lime_dayi_sym row 4
  [ done ][ shift ][123/EN][ space ][〃'][ del ][ enter ]
     15%     15%     10%      20%    10%   15%    15%
  ```

  The site's `〃` label is the fullwidth keyname of ASCII `'` — the key sends code 39 (`'`, what the cin uses), labeled `'`/`〃`. Space is 20%p (precedent: `lime_phonetic` bottom row). Rows 1–4 and the bottom-row modifier pattern are cloned from `lime_dayi_sym` (:30–101), minus the dayi radical sub-labels.
- `.limedb` never imports the `keyboard` table (LIMEDB_SPEC.md §Keyboard Table) — the `limenumsym2` keyboard row MUST be seeded in code on both platforms (`insertKeyboardIfAbsent` pattern, precedent `ensureCangjieSemicolonKeyboards()`), so it also appears on existing installs.

## Source facts (from `.claude/txt/3code.cin`, downloaded from https://3code-type.github.io/3code.cin)

- `%ename 3code` / `%cname 三碼輸入法`; header comment: author 無書自通（改編）, v.20260715.1, ~13,060+ chars, 14,428 lines.
- `%selkey 1234567890`. **No** `%endkey` / `%limeendkey` / `%spacestyle` — leave those empty.
- `%keyname` order: `' , . / ;` then a–z, with fullwidth labels `〃 ， ． ／ ； Ａ…Ｚ`.
- `%chardef` quirks the build script must handle: `#`-comment lines interleaved inside the block; trailing spaces after words; tab-delimited; duplicate (code, word) pairs (e.g. two `,of 訴/訢` entries and repeated `㠯` rows) — dedupe exact duplicates; multi-char candidates exist (`sr 啊！`, `sk 呵…`) — keep them.

## Files to Modify

Phase 1 (asset build):
- NEW `scripts/build_tricode_db.py` (UTF-8 **no BOM**)
- NEW `Database/tricode.limedb` (generated)

Phase 2 (Android):
- `LimeStudio/app/src/main/java/org/limeime/global/LIME.java` (~:53–123)
- `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java` (:624 whitelist, :658 default kb, ~:5679 cj4 ensure pattern for the `tricode` table, :5740 `insertKeyboardIfAbsent` for `limenumsym2`)
- `LimeStudio/app/src/main/java/org/limeime/ui/view/ImInstallFragment.java` (:347–474 `buildFamilyList()`)
- NEW `LimeStudio/app/src/main/res/xml/lime_num_sym2.xml`, `lime_num_sym2_shift.xml`
- `LimeStudio/app/src/main/java/org/limeime/LIMEKeyboardSwitcher.java` (`getXmlResId()` switch :397–521)

Phase 3 (iOS):
- `LimeIME-iOS/Shared/Database/LimeDB.swift` (:482 whitelist, :1285+ `KeyboardConfig` fallback, :2760/:2787 keyboard ensure/insert mirror, :3681+ default kb, cj4-style table ensure mirror)
- `LimeIME-iOS/LimeSettings/IMCatalog.swift` (:60–230)
- `LimeIME-iOS/LimeSettings/Controllers/SetupImController.swift` (:711–723 `reregisterKnownIMs`)
- NEW `LimeIME-iOS/LimeKeyboard/Layouts/lime_num_sym2*.json` (6 files: base, `_shift`, `_ipad`, `_ipad_shift`, `_ipad_narrow`, `_ipad_narrow_shift`)
- `scripts/build_ipad_layouts.py` (allowed-layout list ~:59, generation pairs ~:1061–1075)
- `scripts/trim_ipad_layout.py` (`IM_ROOTS` table; Phase 3b shift-mirror fix)
- REGENERATED `LimeIME-iOS/LimeKeyboard/Layouts/*_ipad_narrow_shift.json` for every layout whose unshifted narrow zxcv carries `。|，` (Phase 3b; `_narrow_shift` files only)
- `LimeIME-iOS/LimeIME.xcodeproj/project.pbxproj` (new JSON resource refs — hand-edit or `scripts/add_ipad_layouts_to_xcodeproj.py`; NEVER run xcodegen, project.yml is stale)

Phase 4 (license/docs):
- `LICENSE.md`, `docs/BACKLOG.md` (feat#159), `docs/ANDROID_IM_CATALOG.md`
- `docs/IPAD_KEYBOARD.md` (§12 generator contract), `docs/IPAD_KB_SIZE_TIERS.md` (§6.2 `IM_ROOTS`, §A.0.2 counts table, new appendix section)

## Steps

### Phase 1 — Build `Database/tricode.limedb`

1. Write `scripts/build_tricode_db.py` (same style as `scripts/build_dictionary_db.py`; comment header with usage). Input: path/URL of `3code.cin` (local copy `.claude/txt/3code.cin`); output `Database/tricode.limedb`.
   - Parse `%cname`, `%selkey`, `%keyname begin/end`, `%chardef begin/end`. Inside chardef: skip `#` lines and blanks, split on tab (fallback whitespace), strip trailing spaces, lowercase codes, dedupe exact (code, word) duplicates while preserving first-seen order.
   - Create inner SQLite `tricode.db` with `custom` table exactly as `LIMEDB_SPEC.md` §Single IM Table Export Format / `hahacj.limedb`'s `cj4.db`:
     `custom(_id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT, code3r TEXT, word TEXT, related TEXT, score INTEGER DEFAULT 0, basescore INTEGER DEFAULT 0)` + `CREATE INDEX custom_idx_code ON custom(code)`.
   - Row values: `code3r=''`, `related=NULL`, `score=0`, `basescore` = frequency lookup from `LimeStudio/app/src/main/res/raw/hanconvertv2.db` table `TCSC(code, word, score)` matched on `TCSC.code = word` (same source runtime `getBaseScore()` uses; 0 when not found / multi-char).
   - Create `im` table (spec schema) with **property rows** patterned on `hahacj.limedb`:
     | code | title | desc | keyboard |
     |---|---|---|---|
     | tricode | source | https://3code-type.github.io/3code.cin | |
     | tricode | version | 20260715.1 | |
     | tricode | name | 三碼 | |
     | tricode | amount | <row count> | |
     | tricode | import | <date passed via --date arg> | |
     | tricode | selkey | 1234567890 | |
     | tricode | imkeys | ',./;abcdefghijklmnopqrstuvwxyz | |
     | tricode | imkeynames | 〃\|，\|．\|／\|；\|Ａ\|Ｂ\|…\|Ｚ | |
     | tricode | keyboard | LIME+數字符號鍵盤2 | limenumsym2 |
     (No endkey/limeendkey/spacestyle rows — the cin defines none.)
   - Zip the single inner file as `tricode.db` → `Database/tricode.limedb`.
2. Run it; record the row count and compressed KB — needed verbatim for the Phase 2/3 catalog entries.

### Phase 2 — Android

1. `LIME.java`: add `DB_TABLE_TRICODE = "tricode"` beside the other `DB_TABLE_*` (~:110–123); add `DATABASE_CLOUD_IM_TRICODE = DATABASE_CLOUD_URL_BASED + "tricode.limedb"` and `DATABASE_CLOUD_IM_TRICODE_KEYBOARD = "limenumsym2"` in the cloud-URL block (:57–94).
2. `LimeDB.java` `isValidTableName()` (:624–632): add `LIME.DB_TABLE_TRICODE`.
3. `LimeDB.java` `getDefaultKeyboardCodeForImportedIM()` (:658–685): `case LIME.DB_TABLE_TRICODE: return "limenumsym2";`.
4. Mapping-table migration: clone the cj4 pattern at :5679 — `CREATE TABLE IF NOT EXISTS tricode (...)` + `tricode_idx_code` index, invoked from the same migration/ensure hook that runs the cj4 ensure (seed `lime.db` predates this table; existing installs need it created in code).
5. Layouts: NEW `res/xml/lime_num_sym2.xml` per the TL;DR diagram — rows 1–4 cloned from `lime_dayi_sym.xml` (:30–82) without the radical sub-labels (plain `1`…`0`, `q`…`p`, `a`…`l` `;`, `z`…`m` `,` `.` `/`; keep the `popupKeyboard="@xml/popup_c_punctuation"` on `.`); bottom row cloned from `lime_dayi_sym` (:84–101) with space shrunk 30%p→20%p and a `〃` key (`codes="39"`, `keyLabel="'"` with `〃` hint) inserted after space. `lime_num_sym2_shift.xml` mirrors `lime_number_symbol_shift.xml` key output with the same row structure. Register both in `LIMEKeyboardSwitcher.getXmlResId()` (:397–521).
6. Keyboard row seeding: alongside `ensureCangjieSemicolonKeyboards()` (:5724), seed via `insertKeyboardIfAbsent()` (:5740), cloning the `limenumsym` row values:
   `code=limenumsym2, name=LIMENUMSYM2, desc=LIME+數字符號鍵盤2, type=phone, image=lime_number_symbol_keyboard_priview (reuse), imkb=lime_num_sym2, imshiftkb=lime_num_sym2_shift, engkb=lime_english_number, engshiftkb=lime_english_shift, symbolkb=symbols, symbolshiftkb=symbols_shift, disable=false`.
7. Install list: in `ImInstallFragment.buildFamilyList()` add after an existing family (clone the 大易 block :405–414):
   one `CloudVariant("三碼 v.20260715.1", "<count>", "<size> KB", LIME.DATABASE_CLOUD_IM_TRICODE)` → `new ImFamily(LIME.DB_TABLE_TRICODE, "三碼", tricode, true, false, false, R.drawable.ic_textformat_alt)` (family names are string literals here; no strings.xml entry needed).

### Phase 3 — iOS

1. `LimeDB.swift` `isValidTableName` (:482–488): add `"tricode"`.
2. `defaultKeyboardCodeForImportedIM` (:3681+): `case "tricode": return "limenumsym2"` (keep in sync with Android — the comment demands it).
3. Mirror the Android table migration and keyboard seeding: `CREATE TABLE IF NOT EXISTS tricode` + index, and `insertKeyboardIfAbsent` for `limenumsym2` next to `ensureCangjieSemicolonKeyboards()` (:2760, :2787; call sites :336–337, :386–387). Also add a `limenumsym2` entry to the hardcoded `KeyboardConfig` fallback (:1285+, `dayisym` at :1293) with the same column values as the Android seed row.
4. Phone layouts: run `scripts/convert_keyboard_layouts.py` on the two new XMLs → `lime_num_sym2.json` / `lime_num_sym2_shift.json`.
5. iPad full-tier layouts (`lime_num_sym2_ipad.json`, `lime_num_sym2_ipad_shift.json`) via `scripts/build_ipad_layouts.py`:
   - Add `("lime_num_sym2", "lime_num_sym2_ipad")` / `("lime_num_sym2_shift", "lime_num_sym2_ipad_shift")` to the generation pair list (~:1061–1075, next to `lime_number_symbol`) and add both ids to the allowed Chinese-IM list (~:59).
   - Row invariants per `docs/IPAD_KEYBOARD.md` §12 apply: digit 14 / qwerty 14 / asdf 13 / zxcv 12 / bottom 6.
   - **Critical: the five 3code code keys `; ' , . /` must keep their ASCII tap codes on iPad.** zxcv gets the standard `<\n,` `>\n.` `?\n/` dual-slide upgrades from `apply_zxcv_punct_sliding` (tap stays 44/46/47 — automatic, no change needed). The asdf row follows the `lime_english_ipad` shape — `[abc] a…l :\n;(tap 59) "\n'(tap 39) [↩]`. Concrete generator changes (all keyed on `source_id in ("lime_num_sym2", "lime_num_sym2_shift")`):
     - `append_semicolon_key` (:633): the trailing `;` upgrade must produce `:\n;` (code 59, longPressCode 58) instead of fullwidth `；\n：` (65306/65307).
     - `append_fullshape_period` (:666): append `"\n'` (code 39, longPressCode 34) instead of `。\n，` (65292/12290) — this IS the `〃` key's iPad home (the phone bottom-row `'` is already in the harvest exclude list at :986, so no duplicate appears elsewhere).
     - `append_enter_key` (:689): its asdf detection is `last code == 65292`; extend to also accept 39 so Enter still lands after the quote key.
     - Shift layer: the generic dual-collapse in `apply_shift_key_rules` (:832, rule "x == chr(longPressCode) → code = longPressCode") turns `:\n;` → fixed `:` (58) and `"\n'` → fixed `"` (34) automatically. If the shift pipeline still yields a stray 65307, add `"lime_num_sym2_shift": (58, ":")` to the `semicolon_fix` dict (:871–874) — same pattern as `lime_dayi_sym_shift`. Confirm via the spec diff either way.
     - `validate_ipad_row_counts` (:909) then passes with 14/14/13/12/6 unchanged.
   - The iPad digit-row `_\n-` / `+\n=` fallbacks are fine (iPad scaffold requirement; `-`/`=` are not 3code code keys and stay phone-hidden).
   - Generated rows MUST match the normative spec below, cell for cell.
   - CAUTION: if the generator regenerates all pairs by default, other `_ipad` files may
     churn (dict-order/format drift). Run scoped to the new pair if supported; either way
     the Verification §0 layout scope gate must pass — any regenerated pre-existing
     layout file gets reverted.
6. iPad narrow-tier layouts (`lime_num_sym2_ipad_narrow.json`, `_narrow_shift`) via `scripts/trim_ipad_layout.py`:
   - **Ordering: apply the Phase 3b trimmer fix FIRST**, then run the trimmer once — the same run produces the new `lime_num_sym2` narrow pair and the corrected `_narrow_shift` regenerations. Running this step with the unfixed trimmer means running it twice.
   - Add `"lime_num_sym2": "',./;abcdefghijklmnopqrstuvwxyz"` to `IM_ROOTS` (see `docs/IPAD_KB_SIZE_TIERS.md` §6.2) so the trimmer never drops the five symbol keys; run the trimmer over the two new `_ipad` files.
   - Narrow shape follows the `lime_number_symbol` exception (§A.12.1) with the symbol keys root-protected. Generated rows MUST match the normative spec below, cell for cell.
7. Add all 6 new JSONs to `project.pbxproj` (individual fileRef/buildFile/group/resources entries — `lime_number_symbol` has 24 refs; reuse `scripts/add_ipad_layouts_to_xcodeproj.py` if applicable, else hand-edit; never xcodegen).
8. `IMCatalog.swift`: add an `IMFamily` (clone dayi :143–152):
   `id: "tricode", chineseName: "三碼", englishName: "3code", description: "三碼輸入法，一字最多三碼", systemIcon: "textformat.alt"`, one variant `.init(id: "tricode", name: "三碼 v.20260715.1", filename: "tricode.limedb", tableName: "tricode", imName: "tricode", label: "三碼", keyboardId: "limenumsym2", recordCount: <count>, compressedKB: <size>)`.
9. `SetupImController.swift` `reregisterKnownIMs` (:711–723): add `("tricode", "三碼", "limenumsym2")`.

#### iPad layout spec — `lime_num_sym2` (normative)

Notation as `IPAD_KB_SIZE_TIERS.md` Appendix A: `X|Y` = dual-slide cell, slide/long-press
glyph before `|`, direct-tap glyph after `|`; `[…]` = modifier. Tap codes for the five
3code code keys are always ASCII: `;`=59, `'`=39, `,`=44, `.`=46, `/`=47.

Full tier (row invariant 14 / 14 / 13 / 12 / 6):

```text
lime_num_sym2_ipad
digit  (14): ~|` !|1 @|2 #|3 $|4 %|5 ^|6 &|7 *|8 (|9 )|0 _|- +|= [⌫]
qwerty (14): [Tab] q w e r t y u i o p 『|「 』|」 ？|、
asdf   (13): [abc] a s d f g h j k l :|; "|' [↩]
zxcv   (12): [⇧] z x c v b n m <|, >|. ?|/ [⇧]
bottom  (6): [globe] [.?123] [emoji] [space] [.?123] [dismiss]

lime_num_sym2_ipad_shift    (dual cells collapse to slide output; same counts)
digit  (14): ~ ! @ # $ % ^ & * ( ) _ + [⌫]
qwerty (14): [Tab] Q W E R T Y U I O P 『 』 ？
asdf   (13): [abc] A S D F G H J K L : " [↩]
zxcv   (12): [⇧] Z X C V B N M < > ? [⇧]
bottom  (6): [globe] [.?123] [emoji] [space] [.?123] [dismiss]
```

- asdf `:|;` = tap 59 / slide 58; `"|'` = tap 39 / slide 34 — the `〃` key promoted from
  the phone bottom row (English-layout `"|,` precedent), NOT the fullwidth `；\n：`/`。\n，`
  scaffold cells.
- digit `_|-` / `+|=` and qwerty `『|「 』|」 ？|、` are standard iPad scaffold fallbacks
  (not 3code code keys).

Narrow tier (trim per §6 with `IM_ROOTS = "',./;abcdefghijklmnopqrstuvwxyz"`; the five
symbol keys are roots — never trimmed, never fullwidth-substituted):

```text
lime_num_sym2_ipad_narrow
digit  (12): !|1 @|2 #|3 $|4 %|5 ^|6 &|7 *|8 (|9 )|0 =|- [⌫]
qwerty (12): q w e r t y u i o p 『|「 』|」
asdf   (12): a s d f g h j k l :|; "|' [↩]
zxcv   (12): [⇧] z x c v b n m <|, >|. ?|/ [spacer]
bottom  (6): [globe] [.?123] [emoji] [space] [abc] [dismiss]

lime_num_sym2_ipad_narrow_shift
digit  (12): ! @ # $ % ^ & * ( ) +|_ [⌫]
qwerty (12): Q W E R T Y U I O P 『 』
asdf   (12): A S D F G H J K L : " [↩]
zxcv   (12): [⇧] Z X C V B N M < > ? [spacer]
bottom  (6): [globe] [.?123] [emoji] [space] [abc] [dismiss]
```

- digit `=|-` (unshifted) / `+|_` (shifted) keep all four characters reachable — same
  pairing exception as `lime_number_symbol` §A.12.1, and it keeps 11 content keys before
  `[⌫]` per the BALANCE rule.
- asdf drops `[abc]` (mode key moves to the bottom row); `:|;` and `"|'` survive as roots,
  so no leading spacer is needed (9 letters + 2 root cells + `[↩]` = 12).
- zxcv is 12 cells on BOTH layers. `lime_num_sym2` has no `。|，` cell at any tier (its
  full-tier asdf slot holds the `"|'` root instead), so the trimmer has nothing to
  displace into zxcv and both layers stay symmetric naturally.
- Bottom row is `BOTTOM_NARROW_ZH` (Chinese IM → `abc` mode key right of space).

### Phase 3b — Fix the pre-existing narrow-shift zxcv mirror bug

Shipped `_ipad_narrow` layouts violate the `IPAD_KEYBOARD.md` §12 shift-mirroring rule
("the shifted row always has the same key count as the unshifted row"): when the trimmer
displaces `。|，` from asdf onto the unshifted zxcv row, the `_narrow_shift` sibling drops
the cell entirely instead of mirroring it, so zxcv is 13 unshifted / 12 shifted. Verified
in the shipped `lime_number_symbol_ipad_narrow*.json` (base zxcv `[⇧] z…m <|, >|. ?|/ 。|，
[spacer]` = 13; shift zxcv `[⇧] Z…M < > ? [spacer]` = 12). While shift/caps-lock is active
the `，/。` slot renders blank and neither character is typeable. `IPAD_KB_SIZE_TIERS.md`
§A.12.1 documents the buggy output as-is.

1. Root cause (verified in code): `trim_layout` (`scripts/trim_ipad_layout.py:380–383`)
   captures the asdf second-to-last key as `zxcv_extra_key` only when its code is **65292**
   (the unshifted dual `。|，`). Shift full-tier layouts carry the collapsed fixed `。`
   (**12290**) in that slot (verified in `lime_number_symbol_ipad_shift.json` asdf:
   `… ； 。 [↩]`), so `zxcv_extra_key` stays `None` and the cell is silently dropped.
   **Fix: change the check to `candidate.get("code") in (65292, 12290)`** — the shift
   layout's fixed `。` then rides the same displacement path onto zxcv (:390–391), keeping
   both layers' zxcv counts equal per the §12 mirror rule. Keep the existing `lime_hs`
   exclusion (:391) as-is.
2. Regenerate by running the fixed trimmer once. Note: `main()` glob-rewrites EVERY
   `*_ipad_narrow*.json` in the Layouts folder each run (`utf-8-sig`, `indent=2`, trailing
   newline — same format it originally wrote), so unaffected files must come out
   **byte-identical** and show no git diff. Expected modified set: the `_narrow_shift`
   siblings of layouts whose shift asdf carries the fixed `。` (per `IPAD_KB_SIZE_TIERS.md`
   Appendix A at least phonetic, dayi, dayi_sym, array, array_number, cj, cj_number, et26,
   et_41, hsu, number_symbol; `lime_hs` excluded, `lime_wb` no-op) — plus the NEW
   `lime_num_sym2` narrow pair. Audit:
   `git diff --name-only -- LimeIME-iOS/LimeKeyboard/Layouts | rg -v "_ipad_narrow_shift"`
   printing nothing; any modified unshifted `_narrow` file means a trimmer side effect —
   stop and investigate, do not commit.
3. Docs: update `IPAD_KB_SIZE_TIERS.md` §A.0.2 shift zxcv counts (12→13 for affected
   rows), the §A.12.1 `lime_number_symbol_ipad_narrow_shift` block (append `。`), and add
   a note that the shift-mirror rule now holds at the narrow tier; `lime_num_sym2`
   (§A.12.2) is unaffected — it stays 12/12 because it never carries `。|，`.
4. Verification (after the fix, before the tricode iPad sim checks):
   - Script check: for every `*_ipad_narrow.json` / `*_ipad_narrow_shift.json` pair, zxcv
     key counts are equal; every unshifted `。|，` has a shifted `。` (12290) in the same
     index.
   - iPad mini/11" sim: `lime_number_symbol` narrow — hold shift → the `。` key appears
     where `，/。` was (no blank slot), tap emits `。`; caps-lock same.
   - Re-run the `lime_num_sym2` narrow spec diff — still 12/12/12/12/6 on both layers
     (this fix must not touch it).

### Phase 4 — License & docs

1. `LICENSE.md`:
   - 「輸入法碼表致謝」 table: new row — 三碼輸入法字根表／碼表，作者 無書自通（源自王堯世先生象形王碼輸入法二代之改編創作），官方網站及版本來源 https://3code-type.github.io/ 。
   - 「輸入法碼表版權聲明」: note the grant from issue #159 — 作者同意 LIME 專案保存、轉換、打包、散布及維護更新；免費使用、不得作商業用途。
   - "Third-Party Open Source Notices › Bundled Data" table: add `tricode` row (source URL + non-commercial free license).
2. `docs/BACKLOG.md:25` feat#159: mark implemented (target v6.1.34), note table version 20260715.1.
3. `docs/ANDROID_IM_CATALOG.md`: add 三碼 row to the per-table size/count reference table.
4. `docs/IPAD_KEYBOARD.md`: add `lime_num_sym2`, `lime_num_sym2_shift` to the §12 Chinese-IM iPad generator allowed-layout list, and copy the **full-tier** row spec from this plan (the `lime_num_sym2_ipad` / `_ipad_shift` blocks above, including the ASCII-tap-code notes) into §12 alongside the other layout contracts.
5. `docs/IPAD_KB_SIZE_TIERS.md`: add the `IM_ROOTS` entry (§6.2); add `lime_num_sym2` row `14 / 14 / 13 / 12 → narrow (both layers): 12 / 12 / 12 / 12` to the §A.0.2 counts table; add a new appendix section `A.12.2 lime_num_sym2` copying the full + narrow row specs from this plan verbatim (model on §A.12.1), with the five ASCII symbol keys marked as protected roots and a note that zxcv stays 12/12 symmetric because this layout has no `。|，` cell to displace.
6. (Post-merge, manual) reply on issue #159 asking the author to verify the testable build — out of scope for this plan.

## Verification

0. **Layout scope gate (hard rule, run before every commit):** the ONLY keyboard-layout
   changes allowed in this work are (a) NEW `lime_num_sym2*` files and (b) MODIFIED
   `*_ipad_narrow_shift.json` files inside the Phase 3b bug scope. Every other existing
   layout — Android `res/xml/*.xml`, iOS `Layouts/*.json` (including all `_ipad` and
   unshifted `_ipad_narrow` files) — must be byte-for-byte untouched. Check:

   ```bash
   git status --porcelain -- LimeStudio/app/src/main/res/xml LimeIME-iOS/LimeKeyboard/Layouts \
     | rg -v "lime_num_sym2|_ipad_narrow_shift\.json"
   ```

   must print nothing, and every `_ipad_narrow_shift.json` line must be `M` (modified),
   never `D`/`R`. Any other diff (a generator side effect, a reformat, a normalization)
   is out of scope — revert it before continuing, per the `IPAD_KB_SIZE_TIERS.md` §8
   production-layout guard.

1. `python3 scripts/build_tricode_db.py ...` → `Database/tricode.limedb` exists; `unzip -l` shows exactly one `tricode.db`; `sqlite3`: `select count(*) from custom` matches amount row; spot-check `select word from custom where code='lh'` → 世 and `code=';lh'` → 泄; im table has the 9 property rows.
2. Android: gradle debug build passes. `android-visual-verify` skill: IM download list shows 三碼 family → download installs → keyboard switches to `limenumsym2` matching the TL;DR diagram (z-row ends `m , . /`; bottom row done/shift/123·EN/space/〃/del/enter; no `-` `=`) → typing `lh` + selkey gives 世; each of the 5 symbol keys `' , . / ;` enters a code (composing buffer shows `〃，．／；` keynames).
3. iOS: `.claude/scripts/ios-gate.sh` unit gate passes (prefix builds with `GIT_CONFIG_COUNT=0`). `ios-visual-verify` skill: install 三碼 from LimeSettings, same layout and typing checks; confirm the new `lime_num_sym2*` JSONs load (no LayoutLoader fallback warnings).
   - **Post-generation spec diff (before any sim run):** for each of the 4 generated `lime_num_sym2_ipad*` JSONs, dump per-row `(label, code, longPressCode)` (small `python3 -c` one-liner over the JSON) and compare cell-for-cell against the normative spec blocks in Phase 3 — row counts 14/14/13/12/6 (full) and 12/12/12/12/6 (narrow, both layers), and tap codes 59/39/44/46/47 present exactly once each. Any mismatch = fix the generator exception, regenerate; never hand-edit the generated JSON.
   - iPad (13" sim): 5-row layout appears matching the `lime_num_sym2_ipad` spec block; asdf row ends `:|; "|' [↩]`; typing `;lh` → 泄 proves the ASCII `;` tap code survives the iPad scaffold; `'`-coded chars (e.g. `''h` 併) prove the promoted `〃` key.
   - iPad mini/11" sim (narrow tier): `_ipad_narrow` variant loads matching its spec block; all five symbol keys `; ' , . /` still present (root-protected, never trimmed).
4. Cross-platform sync greps: `tricode` present in both whitelists, both default-keyboard switches (`limenumsym2`), both table migrations; `limenumsym2` keyboard row values identical on both platforms.

## Decisions

- **`tricode` not `3code`** — digit-leading SQLite identifier would break concatenated SQL everywhere; filename `tricode.limedb` keeps asset and table name aligned (import fallback path requires it if `custom` is ever absent).
- **`.limedb` spec format** (not legacy `.zip`) — carries `im` metadata (selkey/imkeys/imkeynames/keyboard) so install configures the IM fully; matches the newest precedent (`hahacj.limedb`, `scj.limedb`).
- **New `limenumsym2` keyboard, dayi-sym row structure** — the site's reference keyboard (1–0 / q–p / a–l ； / 〃 z–m ，．／) has the same key set as the existing `limenumsym`, but its arrangement (`, . /` in the z-row, shift/backspace at bottom) matches `lime_dayi_sym`/`lime_phonetic`, so LIME_NUM_SYM2 clones that structure instead of pointing 三碼 at `limenumsym`. `-` `=` are dropped (not 3code code keys). Letter rows are capped at 10 keys, so the `〃` key lives in the bottom row (space 30%p→20%p, phonetic precedent) rather than making an 11-key z-row.
- **`〃` sends ASCII `'` (code 39)** — the site's 〃 is the fullwidth keyname of the apostrophe; the cin's codes use `'`, so a literal `"` key would never match the table.
- **iPad ships all three tiers** (`_ipad` full via `build_ipad_layouts.py`, `_ipad_narrow` via `trim_ipad_layout.py`), following the `lime_number_symbol` 6-file precedent. The five 3code code keys `; ' , . /` are treated as IM roots: ASCII tap codes preserved on the full tier (asdf follows the `lime_english_ipad` `:|;` `"|'` shape — no fullwidth `；\n：`/`。\n，` substitution), and root-protected from the narrow trimmer.
- **Keyboard row seeded in code** (`insertKeyboardIfAbsent`, precedent `ensureCangjieSemicolonKeyboards()`), not by editing the binary seed `lime.db` — `.limedb` import never touches the `keyboard` table, and code seeding also fixes existing installs.
- **basescore from hanconvertv2 TCSC** at build time — same frequency source the runtime cin importer uses (`getBaseScore()`), so ranking matches a device-side text import; `score` stays 0.
- **Encoding**: `.py` UTF-8 without BOM; `.md`/`.xml`/`.swift` edits saved UTF-8 with BOM; `.java` without BOM. Layout `.json` files: match whatever the converter/generator scripts emit — the shipped `Layouts/*.json` carry a UTF-8 BOM and `LayoutLoader` handles it; never hand-re-encode generated files.
- One variant only for now; author ships updates as new cin versions → rebuild `tricode.limedb` with the same script and bump the version strings.
