#!/usr/bin/env python3
# build_ipad_layouts.py
# Generates _ipad.json layout files from IM phone layouts for the iPad keyboard.
# Only IM layouts (CJK input methods) are generated; English/symbol layouts
# are maintained separately and must not be modified by this script.
# Usage: python3 scripts/build_ipad_layouts.py  (run from repo root)
# Output: LimeIME-iOS/LimeKeyboard/Layouts/*_ipad.json

import json
import os
import copy

LAYOUTS_DIR = "LimeIME-iOS/LimeKeyboard/Layouts"

EXCLUDED_IPAD_LAYOUTS = {
    "lime_ez",
    "lime_ez_shift",
    "lime_hs",
    "lime_hs_shift",
    "lime_wb",
    "lime_wb_shift",
}

# -------------------------------------------------------------------------
# iPad bottom row (standard for all Chinese-IM layouts)
# globe | .?123 | emoji | space | .?123 | dismiss
#
# emoji (code -201) opens the emoji panel. The .?123 cells use the literal
# ".?123" label for iPad layout consistency per docs/IOS_KB_GAP.md Â§3.2.
#
# ï¼\nï¼ and 。\n， are placed on the asdf row by append_semicolon_key
# and append_fullshape_period; they must not be generated in the bottom row.
# globe and dismiss carry longPressCode -100 (show options menu).
# Total: 8 + 10 + 7 + 57 + 10 + 8 = 100
# -------------------------------------------------------------------------
IPAD_BOTTOM_ROW = {
    "isBottomRow": True,
    "keys": [
        {"code": -200, "label": "globe", "sublabel": "", "widthPercent":  8.0, "icon": "globe",                        "isModifier": True,  "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": -100},
        {"code":   -2, "label": ".?123", "sublabel": "", "widthPercent": 10.0, "icon": "",                             "isModifier": True,  "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
        {"code": -201, "label": "",      "sublabel": "", "widthPercent":  7.0, "icon": "face.smiling",                 "isModifier": True,  "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
        {"code":   32, "label": "",      "sublabel": "", "widthPercent": 57.0, "icon": "space.bar",                    "isModifier": False, "isRepeatable": True,  "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
        {"code":   -2, "label": ".?123", "sublabel": "", "widthPercent": 10.0, "icon": "",                             "isModifier": True,  "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
        {"code":   -3, "label": "",      "sublabel": "", "widthPercent":  8.0, "icon": "keyboard.chevron.compact.down","isModifier": True,  "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": -100},
    ]
}

# -------------------------------------------------------------------------
# Layouts that use an IM number-row for the top row (keep existing top row,
# just widen proportionally). All other layouts get the symbol/number dual row.
# -------------------------------------------------------------------------
IM_LAYOUTS = {
    "lime_phonetic", "lime_phonetic_shift",
    "lime_array",    "lime_array_shift",
    "lime_array_number", "lime_array_number_shift",
    "lime_cj",       "lime_cj_shift",
    "lime_cj_number","lime_cj_number_shift",
    "lime_dayi",     "lime_dayi_shift",
    "lime_dayi_sym", "lime_dayi_sym_shift",
    "lime_et26",     "lime_et26_shift",
    "lime_et_41",    "lime_et_41_shift",
    "lime_hsu",      "lime_hsu_shift",
    "lime_num_sym2", "lime_num_sym2_shift",
    "lime_wb",       "lime_wb_shift",
}

# -------------------------------------------------------------------------
# Helper: check if a row is the "1-0 number top row" (to be replaced)
# Criteria: first key code is 49 or 33/1-0 symbols, 10 keys, not isBottomRow
# -------------------------------------------------------------------------
def is_number_top_row(row):
    """Return True if this row looks like a 1â0 number/symbol top row."""
    if row.get("isBottomRow", False):
        return False
    keys = row.get("keys", [])
    if len(keys) != 10:
        return False
    # Check if codes are the standard 1-0 number row: 49,50,51,52,53,54,55,56,57,48
    codes = [k["code"] for k in keys]
    return codes == [49, 50, 51, 52, 53, 54, 55, 56, 57, 48] or codes == [49, 50, 51, 52, 55, 56, 57, 48, 45, 61]


def is_symbol_top_row(row):
    """Return True if this row looks like the !@#... symbol top row (lime_abc_shift etc)."""
    if row.get("isBottomRow", False):
        return False
    keys = row.get("keys", [])
    if len(keys) != 10:
        return False
    codes = [k["code"] for k in keys]
    # !@#$%^&*(  )
    return codes == [33, 64, 35, 36, 37, 94, 38, 42, 40, 41] or codes == [33, 64, 35, 36, 38, 42, 40, 41, 95, 43]


# -------------------------------------------------------------------------
# Helper: normalise a key â ensure all standard fields present, add
# longPressCode if missing (defaults to 0).
# -------------------------------------------------------------------------
def normalise_key(key):
    k = copy.deepcopy(key)
    # Ensure popupCharacters field exists
    if "popupCharacters" not in k:
        k["popupCharacters"] = ""
    # Ensure longPressCode field exists
    if "longPressCode" not in k:
        k["longPressCode"] = 0
    return k


# -------------------------------------------------------------------------
# Helpers: promote `-` (code 45) and `=` (code 61) to the iPad digit row.
#
# Many IM phone layouts keep `-` and `=` in their bottom row (the row that
# `IPAD_BOTTOM_ROW` replaces wholesale on iPad â so those keys would be
# lost). On iPad we want them visible as primary digits to the right of
# `0`, in the order: `... 9 0 - = â«`.
#
# Three pieces:
#   1. `harvest_dash_and_equals(source)` â scans the WHOLE source layout
#      (every row, including the bottom row that's about to be replaced)
#      for primary `-` (code 45) and primary `=` (code 61). Returns the
#      first match for each as deep-copied key dicts. Either may be None.
#   2. `strip_dash_and_equals(row)` â removes primary `-`/`=` from a
#      kept row so they don't end up duplicated once promoted.
#   3. `place_dash_and_equals_next_to_zero(row, dash_key, eq_key)` â
#      inserts `dash_key` (if not None) and `eq_key` immediately to the
#      right of `0` (code 48). When `eq_key` is None, a dual-sliding
#      replacement is created instead:
#         primary  : code 61 (`=`)
#         label    : "=\n+"  (top: =, bottom: +)
#         longPressCode: 43 (`+`)  â slides down / long-presses to commit
#      Promoted keys take the digit-row width so spacing stays uniform;
#      `scale_row_to_100` renormalises afterwards.
#
# Self-gating: the placer does nothing when the row has no primary `0`
# (code 48). `IPAD_TOP_ROW` is therefore unaffected (its `0` lives as
# longPressCode on the `)` key, not as a primary code) â that row's
# design is intentional and the rule simply doesn't apply.
# -------------------------------------------------------------------------
def harvest_dash_and_equals(source):
    """Scan all rows for the first primary `-` (45) and `=` (61). Return
    (dash_key, eq_key); each is a deep-copied key dict, or None."""
    dash_key = None
    eq_key = None
    for r in source.get("rows", []):
        for k in r.get("keys", []):
            if k.get("code") == 45 and dash_key is None:
                dash_key = copy.deepcopy(k)
            elif k.get("code") == 61 and eq_key is None:
                eq_key = copy.deepcopy(k)
    return dash_key, eq_key


def strip_promoted_keys(row):
    """Remove keys that are promoted to a different iPad row, to prevent
    duplication.  Bottom rows are skipped (replaced wholesale).

    Promoted codes:
      45 (-), 61 (=)  â digit row
      91 ([), 92 (\\), 93 (])  â qwerty row
    """
    if row.get("isBottomRow", False):
        return row
    _PROMOTED = frozenset({45, 61, 91, 92, 93})
    row["keys"] = [k for k in row["keys"] if k.get("code") not in _PROMOTED]
    return row


def harvest_qwerty_bracket_keys(phone):
    """Return deep copies of [ (91), \\ (92), ] (93) found anywhere in the
    source layout.  Returns (lbracket, backslash, rbracket); any may be None."""
    lbracket = backslash = rbracket = None
    for r in phone.get("rows", []):
        for k in r.get("keys", []):
            code = k.get("code")
            if code == 91 and lbracket is None:
                lbracket = copy.deepcopy(k)
            elif code == 92 and backslash is None:
                backslash = copy.deepcopy(k)
            elif code == 93 and rbracket is None:
                rbracket = copy.deepcopy(k)
    return lbracket, backslash, rbracket


# -------------------------------------------------------------------------
# IM layout structure helpers
# -------------------------------------------------------------------------

# Codes that represent modifier / non-character keys â skipped when
# harvesting printable symbols from the source bottom row.
_MODIFIER_CODES = frozenset({
    -200, -100, -99, -9, -5, -4, -3, -2, -1, 0, 10, 32,
})


def source_zxcv_ends_with_delete(rows):
    """True if the content row containing z(122) or Z(90) ends with delete(-5).

    When True, ALL printable bottom-row symbols are promoted to the zxcv row
    on iPad (hs/hsu/dayi/ez/et_41/et26 style).  When False, only extra symbols
    beyond -/= go to zxcv and the harvested dash goes to the digit row instead
    (phonetic / array_number style).
    """
    for r in rows:
        if r.get("isBottomRow", False):
            continue
        codes = [k.get("code") for k in r.get("keys", [])]
        if (122 in codes or 90 in codes) and codes and codes[-1] == -5:
            return True
    return False


def harvest_bottom_row_symbols(phone, exclude_codes=()):
    """Return deep copies of printable non-modifier keys from the bottom row.

    Keys whose code is in exclude_codes are also skipped (used to omit -/= when
    they are being placed in the digit row rather than the zxcv row).
    """
    for row in phone.get("rows", []):
        if row.get("isBottomRow", False):
            result = []
            for k in row.get("keys", []):
                code = k.get("code", 0)
                if code not in _MODIFIER_CODES and code not in exclude_codes and code > 0:
                    result.append(copy.deepcopy(k))
            return result
    return []


def harvest_popup_for_codes(phone, codes):
    """Return the first approved popup tuple for one of the requested codes."""
    for row in phone.get("rows", []):
        for k in row.get("keys", []):
            if k.get("code") not in codes:
                continue
            popup = k.get("popupKeyboard", "")
            chars = k.get("popupCharacters", "")
            if not popup and not chars:
                continue
            if k.get("isModifier") or popup == "@xml/phone_simple" or k.get("longPressCode", 0) == -106:
                continue
            return (popup, chars)
    return None


# Dual-sliding labels for punctuation keys promoted to the zxcv row.
# Format: longPressCode, "hint\nprimary"  (top = long-press hint, bottom = tap).
_PUNCT_SLIDING = {
    44: (60, "<\\n,"),   # comma  â longPress <
    46: (62, ">\\n."),   # period â longPress >
    47: (63, "?\\n/"),   # slash  â longPress ?
}

# Standard QWERTY zxcv-row printable codes (both cases + punctuation).
# Any other printable code in the zxcv row is an extra IM component (e.g.
# phonetic_shift _(95/ã¦) or et_41 '(39/ã)) and gets stripped to keep the
# row at exactly 10 normal keys between the two shift keys.
_ZXCV_QWERTY_CODES = frozenset({
    122, 120, 99, 118, 98, 110, 109,   # z x c v b n m
    90, 88, 67, 86, 66, 78, 77,         # Z X C V B N M
    44, 46, 47,                          # , . /
    60, 62, 63,                          # < > ?
})

# Dual-sliding labels for digit keys: symbol on top (long-press hint),
# digit on bottom (primary).  Matches the pattern in lime_english_number_ipad.
_DIGIT_SYMBOL = {
    49: (33,  "!\\n1"),
    50: (64,  "@\\n2"),
    51: (35,  "#\\n3"),
    52: (36,  "$\\n4"),
    53: (37,  "%\\n5"),
    54: (94,  "^\\n6"),
    55: (38,  "&\\n7"),
    56: (42,  "*\\n8"),
    57: (40,  "(\\n9"),
    48: (41,  ")\\n0"),
}


def make_plus_equals_key(width=7.0):
    return {
        "code": 61, "label": "+\\n=", "sublabel": "",
        "widthPercent": width, "icon": "",
        "isModifier": False, "isRepeatable": False, "isSticky": False,
        "popupKeyboard": "", "popupCharacters": "", "longPressCode": 43,
    }


# -------------------------------------------------------------------------
# Per-row IM transforms â applied in pipeline order for 4-row IM layouts.
# Each function is self-gating: it detects its target row by content and
# returns the row unchanged when the detection does not match.
# -------------------------------------------------------------------------

def augment_im_digit_row(row, digit_dash, layout_dash_key=None, eq_key=None,
                         preserve_source_underscore=False):
    """Augment the IM digit row for iPad.

    Result layout: "~\n`" | 1 2 3 4 5 6 7 8 9 0 | [dash] | "+\n=" | â«
    Digit row invariant: exactly 13 normal keys plus delete = 14 total keys.

    "~\n`" prefix: backtick primary (96), tilde long-press (126).
    â«: repeating delete so users don't have to reach the top-right corner.

    dash_key: harvested `-` dict, or None when dash goes to the zxcv row
    instead (source_zxcv_ends_with_delete layouts).
    """
    if row.get("isBottomRow", False):
        return row
    keys = row["keys"]
    codes = [k.get("code") for k in keys]

    # Shifted symbol-shift top row (!@#$%^&*()) â augment as fixed mirror of digit row.
    # Per the shift mirroring rule: each dual-sliding key becomes its fixed slide output.
    if is_symbol_top_row(row):
        W = 7.0
        keys[:] = [k for k in keys if k.get("code") not in (95, 43)]
        codes = [k.get("code") for k in keys]
        if 37 not in codes:
            keys.insert(next((i for i, k in enumerate(keys) if k.get("code") == 38), len(keys)), {
                "code": 37, "label": "%", "sublabel": "", "widthPercent": W, "icon": "",
                "isModifier": False, "isRepeatable": False, "isSticky": False,
                "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0,
            })
        codes = [k.get("code") for k in keys]
        if 94 not in codes:
            keys.insert(next((i for i, k in enumerate(keys) if k.get("code") == 38), len(keys)), {
                "code": 94, "label": "^", "sublabel": "", "widthPercent": W, "icon": "",
                "isModifier": False, "isRepeatable": False, "isSticky": False,
                "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0,
            })
        keys.insert(0, {
            "code": 126, "label": "~", "sublabel": "", "widthPercent": W, "icon": "",
            "isModifier": False, "isRepeatable": False, "isSticky": False,
            "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0,
        })
        # Insert middle key right of ) (41) â mirrors non-shift position after 0.
        try:
            ins = next(i for i, k in enumerate(keys) if k.get("code") == 41) + 1
        except StopIteration:
            ins = len(keys)
        if preserve_source_underscore or digit_dash is not None:
            keys.insert(ins, {
                "code": 95, "label": "_", "sublabel": "", "widthPercent": W, "icon": "",
                "isModifier": False, "isRepeatable": False, "isSticky": False,
                "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0,
            })
        elif layout_dash_key is None:
            keys.insert(ins, {
                "code": 8230, "label": "\u2026", "sublabel": "", "widthPercent": W, "icon": "",
                "isModifier": False, "isRepeatable": False, "isSticky": False,
                "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0,
            })
        keys.append({
            "code": 43, "label": "+", "sublabel": "", "widthPercent": W, "icon": "",
            "isModifier": False, "isRepeatable": False, "isSticky": False,
            "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0,
        })
        keys.append({
            "code": -5, "label": "", "sublabel": "", "widthPercent": W,
            "icon": "delete.backward", "isModifier": True, "isRepeatable": True,
            "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0,
        })
        if len(keys) != 14:
            raise ValueError("Generated iPad symbol top row must be 14 keys")
        return row

    if 48 not in codes or 49 not in codes:  # must be a 1â0 digit row
        return row

    W = 7.0  # normal key width; all added keys use 7 % so scale_row_to_100
             # normalises proportionally

    # Remove any -/= that strip_dash_and_equals may have left (safety).
    keys[:] = [k for k in keys if k.get("code") not in (45, 61)]
    current_codes = {k.get("code") for k in keys}
    if 53 not in current_codes and digit_dash and digit_dash.get("popupCharacters") == "5":
        keys.insert(next((i for i, k in enumerate(keys) if k.get("code") == 55), len(keys)), {
            "code": 53, "label": "%\\n5", "sublabel": "", "widthPercent": W, "icon": "",
            "isModifier": False, "isRepeatable": False, "isSticky": False,
            "popupKeyboard": "", "popupCharacters": "", "longPressCode": 37,
        })
    current_codes = {k.get("code") for k in keys}
    if 54 not in current_codes and eq_key and eq_key.get("popupCharacters") == "6":
        keys.insert(next((i for i, k in enumerate(keys) if k.get("code") == 55), len(keys)), {
            "code": 54, "label": "^\\n6", "sublabel": "", "widthPercent": W, "icon": "",
            "isModifier": False, "isRepeatable": False, "isSticky": False,
            "popupKeyboard": "", "popupCharacters": "", "longPressCode": 94,
        })

    # Add symbol long-press to digit keys that have no sublabel.
    # Layouts with IM-component sublabels (phonetic=ã, dayi=è¨ â¦) are left
    # unchanged; plain-number layouts (array_number, cj_number â¦) get the
    # dual-sliding treatment to match lime_english_number_ipad.
    for k in keys:
        if k.get("sublabel", ""):
            continue  # has IM component hint â don't touch the label
        lp, lbl = _DIGIT_SYMBOL.get(k.get("code", 0), (None, None))
        if lp is not None:
            k["longPressCode"] = lp
            k["label"] = lbl

    zero_idx = next(i for i, k in enumerate(keys) if k.get("code") == 48)
    ins = zero_idx + 1

    if digit_dash is not None:
        # Dash promoted from bottom row â move it right of 0.
        d = normalise_key(copy.deepcopy(digit_dash))
        d["widthPercent"] = W
        if d.get("popupCharacters"):
            d["label"] = d.get("label", "").split()[0]
            d["popupKeyboard"] = ""
            d["popupCharacters"] = ""
        if not d.get("sublabel", ""):
            d["label"] = "_\\n-"
            d["longPressCode"] = 95
        keys.insert(ins, d)
        ins += 1
    elif layout_dash_key is None:
        # No dash anywhere in the layout â use em-dash/ellipsis as fallback.
        keys.insert(ins, {
            "code": 8212, "label": "\u2026\\n\u2014", "sublabel": "",
            "widthPercent": W, "icon": "",
            "isModifier": False, "isRepeatable": False, "isSticky": False,
            "popupKeyboard": "", "popupCharacters": "", "longPressCode": 8230,
        })
        ins += 1
    # else: dash exists but was promoted to zxcv row â nothing added here.

    # Insert = (source key) or +\n= fallback left of backspace.
    if eq_key is not None:
        ek = normalise_key(copy.deepcopy(eq_key))
        ek["widthPercent"] = W
        if ek.get("popupCharacters"):
            ek["label"] = ek.get("label", "").split()[0]
            ek["popupKeyboard"] = ""
            ek["popupCharacters"] = ""
        if not ek.get("sublabel", ""):
            ek["label"] = "+\\n="
            ek["longPressCode"] = 43
        keys.append(ek)
    else:
        keys.append(make_plus_equals_key(W))

    # Hard invariant guard: every generated digit row must have the +\n= key.
    # This catches source layouts where = was stripped or absent in an unusual
    # place and prevents a 13-key top row from being emitted.
    if not any(k.get("code") == 61 for k in keys):
        keys.append(make_plus_equals_key(W))

    keys.append({
        "code": -5, "label": "", "sublabel": "", "widthPercent": W,
        "icon": "delete.backward", "isModifier": True, "isRepeatable": True,
        "isSticky": False, "popupKeyboard": "", "popupCharacters": "",
        "longPressCode": 0,
    })

    keys.insert(0, {
        "code": 96, "label": "~\\n`", "sublabel": "", "widthPercent": W,
        "icon": "", "isModifier": False, "isRepeatable": False, "isSticky": False,
        "popupKeyboard": "", "popupCharacters": "", "longPressCode": 126,
    })
    if len(keys) != 14:
        raise ValueError("Generated iPad digit top row must be 14 keys")
    return row


def transform_qwerty_row(row, lbracket=None, backslash=None, rbracket=None, has_digit_row=True):
    """Detect the qwerty row (last primary code = p/112 or P/80).

    Left edge:          Tab (9), always present.
    Rightmost pair:     source [ (91) or fallback ã\\nã,
                        then source ] (93) or fallback ã\\nã.
    Rightmost position: source \\ with IM sublabel is preserved; otherwise
                        ï¼\\nã (tap ã, slide/long-press ï¼) when digit row exists;
                        â« when no digit row.

    Source keys are moved (stripped from other rows by strip_promoted_keys).
    Fallback dual-sliding keys are added only when the source layout does not
    already provide the slot. A plain backslash/pipe source key is not converted
    to |\\nã; Chinese IM rows use the CJK ï¼\\nã fallback instead.
    """
    if row.get("isBottomRow", False):
        return row
    keys = row["keys"]
    if not keys or keys[-1].get("code") not in (112, 80):
        return row

    W = 7.0

    # Tab is always leftmost.
    keys.insert(0, {
        "code": 9, "label": "", "sublabel": "", "widthPercent": W,
        "icon": "arrow.forward.to.line", "isModifier": True,
        "isRepeatable": False, "isSticky": False,
        "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0,
    })

    # Right of p: [ (source) or ã\nã
    if lbracket is not None:
        lk = normalise_key(copy.deepcopy(lbracket))
        lk["widthPercent"] = W
        if not lk.get("sublabel", ""):
            lk["label"] = "{\\n["
            lk["longPressCode"] = 123
        keys.append(lk)
    else:
        keys.append({
            "code": 12300, "label": "\u300e\\n\u300c", "sublabel": "", "widthPercent": W,
            "icon": "", "isModifier": False, "isRepeatable": False,
            "isSticky": False, "popupKeyboard": "", "popupCharacters": "",
            "longPressCode": 12302,
        })

    # Right of [: ] (source) or ã\nã
    if rbracket is not None:
        rk = normalise_key(copy.deepcopy(rbracket))
        rk["widthPercent"] = W
        if not rk.get("sublabel", ""):
            rk["label"] = "}\\n]"
            rk["longPressCode"] = 125
        keys.append(rk)
    else:
        keys.append({
            "code": 12301, "label": "\u300f\\n\u300d", "sublabel": "", "widthPercent": W,
            "icon": "", "isModifier": False, "isRepeatable": False,
            "isSticky": False, "popupKeyboard": "", "popupCharacters": "",
            "longPressCode": 12303,
        })

    # Rightmost: preserve an IM-component source \ key; otherwise use ï¼\nã
    # when digit row exists. No-digit qwerty row carries delete instead.
    if has_digit_row:
        if backslash is not None and backslash.get("sublabel", ""):
            bs = normalise_key(copy.deepcopy(backslash))
            bs["widthPercent"] = W
            keys.append(bs)
        else:
            keys.append({
                "code": 12289, "label": "\uff1f\\n\u3001", "sublabel": "", "widthPercent": W,
                "icon": "", "isModifier": False, "isRepeatable": False,
                "isSticky": False, "popupKeyboard": "", "popupCharacters": "",
                "longPressCode": 65311,
            })
    else:
        keys.append({
            "code": -5, "label": "", "sublabel": "", "widthPercent": W,
            "icon": "delete.backward", "isModifier": True, "isRepeatable": True,
            "isSticky": False, "popupKeyboard": "", "popupCharacters": "",
            "longPressCode": 0,
        })

    return row


def prepend_abc_modifier(row):
    """Detect the asdf row (last primary code â {58, 59, 108, 76}) and prepend abc(-9).

    58 (:) is the shift-layer equivalent of ; â some phonetic/et_41 shift layouts
    end the asdf row with : instead of ;.
    """
    if row.get("isBottomRow", False):
        return row
    keys = row["keys"]
    if not keys or keys[-1].get("code") not in (58, 59, 108, 76):
        return row
    keys.insert(0, {
        "code": -9, "label": "abc", "sublabel": "", "widthPercent": 7.0,
        "icon": "", "isModifier": True, "isRepeatable": False, "isSticky": False,
        "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0,
    })
    return row



def apply_zxcv_punct_sliding(row):
    """Detect the zxcv row and upgrade , . / keys without sublabel to dual-slide.
    For any of , . / entirely absent from the row, insert fallback keys (also
    with dual-slide labels) left of the trailing shift(-1).

    , (44) â <\\n,  longPress < (60)
    . (46) â >\\n.  longPress > (62)
    / (47) â ?\\n/  longPress ? (63)

    Keys with sublabels (IM components such as phonetic ã/ã¡/ã¥) are left unchanged.
    This runs after ensure_zxcv_shifts so promoted bottom-row keys are already present.
    """
    if row.get("isBottomRow", False):
        return row
    keys = row["keys"]
    codes = [k.get("code") for k in keys]
    if 122 not in codes and 90 not in codes:
        return row

    # Upgrade existing keys that lack a sublabel.
    all_codes = {k.get("code") for k in keys}
    present_punct = set()
    for k in keys:
        code = k.get("code")
        entry = _PUNCT_SLIDING.get(code)
        if entry:
            present_punct.add(code)
            if not k.get("sublabel", ""):
                k["longPressCode"] = entry[0]
                k["label"]         = entry[1]

    # Insert fallbacks for missing codes, left of trailing terminators
    # (shift -1, fullshape-period 65292, return 10).
    # Skip if the shift-layer equivalent (<>? = 60/62/63) is already present â
    # those are IM-component keys occupying the same physical position, so
    # adding both would create an overcrowded row.
    _SHIFT_EQUIV = {44: 60, 46: 62, 47: 63}
    fallbacks = []
    for code in (44, 46, 47):
        if code not in present_punct and _SHIFT_EQUIV[code] not in all_codes:
            lp, lbl = _PUNCT_SLIDING[code]
            fallbacks.append({
                "code": code, "label": lbl, "sublabel": "", "widthPercent": 7.0,
                "icon": "", "isModifier": False, "isRepeatable": False, "isSticky": False,
                "popupKeyboard": "", "popupCharacters": "", "longPressCode": lp,
            })
    if fallbacks:
        _TRAILING = frozenset({-1, 65292, 10})
        ins = len(keys)
        while ins > 0 and keys[ins - 1].get("code") in _TRAILING:
            ins -= 1
        for f in reversed(fallbacks):
            keys.insert(ins, f)

    return row


def append_semicolon_key(row, source_id, semicolon_popup=None):
    """Detect the asdf row (last primary code in {58, 59, 108, 76}) and add fullwidth ；\n：.

    - Source `;` (59) or `:` (58) without sublabel -> upgraded in-place to
      fullwidth ；\n：(65306 tap, 65307 long-press).
    - Exceptions that keep an ASCII tap code:
      * lime_dayi keeps `;` (59) unchanged because it is the Dayi 虫 root.
      * lime_num_sym2 / lime_num_sym2_shift keep `;`/`:` as one of the five
        3code symbol roots and are upgraded in-place to `:\n;` (59 tap,
        58 long-press) instead of the fullwidth glyph.
    - Source `;`/`:` with sublabel (IM component, e.g. phonetic ㄧ / et_41 ㄦ)
      -> left unchanged; the IM key stays as the last character key.
    - No `;`/`:` in the row (last in {108, 76}) -> fullwidth ；\n：appended after l.
    """
    if row.get("isBottomRow", False):
        return row
    keys = row["keys"]
    if not keys or keys[-1].get("code") not in (58, 59, 108, 76):
        return row
    preserve_dayi_root = source_id == "lime_dayi"
    ascii_tap = source_id in ("lime_num_sym2", "lime_num_sym2_shift")
    if keys[-1].get("code") in (58, 59):
        lk = keys[-1]
        if not lk.get("sublabel", ""):
            if preserve_dayi_root:
                lk["code"] = 59
                lk["label"] = ";"
                lk["longPressCode"] = 0
            elif ascii_tap:
                lk["code"]         = 59
                lk["label"]        = ':\\n;'
                lk["longPressCode"] = 58
            else:
                lk["code"]         = 65306
                lk["label"]        = '；\\n：'
                lk["longPressCode"] = 65307
                if semicolon_popup:
                    lk["popupKeyboard"], lk["popupCharacters"] = semicolon_popup
        # else: IM component key -- leave unchanged
    else:
        popup, chars = semicolon_popup or ("", "")
        keys.append({
            "code": 65306, "label": '；\\n：', "sublabel": "", "widthPercent": 7.0,
            "icon": "", "isModifier": False, "isRepeatable": False, "isSticky": False,
            "popupKeyboard": popup, "popupCharacters": chars, "longPressCode": 65307,
        })
    return row


def append_fullshape_period(row, source_id):
    """Detect the asdf row and append fullwidth 。\n，, or (for lime_num_sym2)
    the promoted 〃 key ("\n').

    Detection: last primary code in {58, 59, 65306, 108, 76}.
    58 (:) and 59 (;) are included for IM shift layouts whose last asdf key is an
    IM component (sublabel present) so append_semicolon_key left it unchanged, and
    for lime_num_sym2 / lime_num_sym2_shift whose semicolon key stays ASCII (59).
    65306 (；) is included because append_semicolon_key may have placed it last.
    Fullwidth comma (65292) on tap, fullwidth period (12290) on long-press.
    Exception: lime_num_sym2 / lime_num_sym2_shift append the promoted 〃 (')
    key instead -- ASCII `'` (39) on tap, `"` (34) on long-press -- since this IM
    has no 。/，characters and the phone bottom-row `'` key needs an iPad home.
    Always placed left of the Enter key on the asdf row.
    """
    if row.get("isBottomRow", False):
        return row
    keys = row["keys"]
    if not keys or keys[-1].get("code") not in (58, 59, 65306, 108, 76):
        return row
    if source_id in ("lime_num_sym2", "lime_num_sym2_shift"):
        keys.append({
            "code": 39, "label": '"\\n\'', "sublabel": "", "widthPercent": 7.0,
            "icon": "", "isModifier": False, "isRepeatable": False, "isSticky": False,
            "popupKeyboard": "", "popupCharacters": "", "longPressCode": 34,
        })
    else:
        keys.append({
            "code": 65292, "label": '。\\n，', "sublabel": "", "widthPercent": 7.0,
            "icon": "", "isModifier": False, "isRepeatable": False, "isSticky": False,
            "popupKeyboard": "", "popupCharacters": "", "longPressCode": 12290,
        })
    return row


def append_enter_key(row):
    """Append Enter (code 10) to the asdf row after append_fullshape_period ran.

    Detection: last primary code in {65292, 39} (the 。key, or -- for
    lime_num_sym2 / lime_num_sym2_shift -- the promoted 〃(') key, just added).
    """
    if row.get("isBottomRow", False):
        return row
    keys = row["keys"]
    if not keys or keys[-1].get("code") not in (65292, 39):
        return row
    keys.append({
        "code": 10, "label": "", "sublabel": "", "widthPercent": 7.0,
        "icon": "return", "isModifier": True, "isRepeatable": False,
        "isSticky": False, "popupKeyboard": "", "popupCharacters": "",
        "longPressCode": 0,
    })
    return row


def ensure_zxcv_shifts(row, extra_keys=None):
    """Detect the zxcv row (contains z=122 or Z=90), then:
    1. Remove trailing delete(-5) if present.
    2. Append extra_keys (bottom-row symbols promoted to this row) at 7 % each.
    3. Ensure shift(-1) on both the leading and trailing positions.
    scale_row_to_100 called by the caller normalises the final widths.
    """
    if row.get("isBottomRow", False):
        return row
    keys = row["keys"]
    codes = [k.get("code") for k in keys]
    if 122 not in codes and 90 not in codes:
        return row

    # 1. Remove trailing delete
    if keys and keys[-1].get("code") == -5:
        keys.pop()

    # 1b. Strip non-standard IM component keys that would push the row over
    #     10 normal keys (e.g. phonetic_shift _(95/ã¦) or et_41 '(39/ã)).
    #     Modifier codes (< 33) are always kept; only printable non-QWERTY ones
    #     are removed.
    keys[:] = [k for k in keys
               if k.get("code", 0) < 33 or k.get("code", 0) in _ZXCV_QWERTY_CODES]

    # 2. Promote bottom-row symbols.  Skip a candidate if:
    #    a) its code is already in the row, or
    #    b) it is , . / (44/46/47) and the shift-layer equivalent < > ? (60/62/63)
    #       is already in the row â avoids doubling up IM punct with QWERTY punct.
    if extra_keys:
        _shift_eq = {44: 60, 46: 62, 47: 63}
        current_codes = {k.get("code") for k in keys}
        for k in extra_keys:
            code = k.get("code")
            if code in current_codes:
                continue
            if _shift_eq.get(code) in current_codes:
                continue
            ek = normalise_key(copy.deepcopy(k))
            ek["widthPercent"] = 7.0
            keys.append(ek)

    # 3. Ensure leading shift
    if not keys or keys[0].get("code") != -1:
        keys.insert(0, {
            "code": -1, "label": "", "sublabel": "", "widthPercent": 7.0,
            "icon": "shift", "isModifier": True, "isRepeatable": False,
            "isSticky": True, "popupKeyboard": "", "popupCharacters": "",
            "longPressCode": 0,
        })

    # 4. Ensure trailing shift
    if keys[-1].get("code") != -1:
        keys.append({
            "code": -1, "label": "", "sublabel": "", "widthPercent": 7.0,
            "icon": "shift", "isModifier": True, "isRepeatable": False,
            "isSticky": True, "popupKeyboard": "", "popupCharacters": "",
            "longPressCode": 0,
        })
    return row



# -------------------------------------------------------------------------
# Helper: scale widths in a row proportionally so they sum to 100.0.
# Used only for lime_wb layouts (very few keys per row â proportional scaling
# is more appropriate than the fixed-7% rule).
# -------------------------------------------------------------------------
def scale_row_to_100(row):
    keys = row["keys"]
    total = sum(k["widthPercent"] for k in keys)
    if abs(total - 100.0) < 0.001:
        return row  # already correct
    factor = 100.0 / total
    for k in keys:
        k["widthPercent"] = round(k["widthPercent"] * factor, 4)
    # Fix floating-point residual: force exact sum=100
    diff = 100.0 - sum(k["widthPercent"] for k in keys)
    if abs(diff) > 0.0001:
        keys[-1]["widthPercent"] = round(keys[-1]["widthPercent"] + diff, 4)
    return row


def _is_normal_key(code):
    """Printable character key â gets the fixed 7% normal-key width."""
    return code >= 33 and code not in _MODIFIER_CODES


def normalize_im_row_widths(row):
    """Normalize an IM row for iPad: every printable key gets exactly 7%;
    function/modifier keys share the remaining width equally.

    Overflow fallback: when normal-key count Ã 7% > 100% (e.g. hs zxcv row
    with many promoted symbols), all keys share the row equally instead.

    lime_wb is exempt (uses scale_row_to_100 instead â it has very few keys
    per row so proportional scaling produces better proportions than fixed 7%).
    """
    keys = row["keys"]
    normal = [k for k in keys if _is_normal_key(k.get("code", 0))]
    funcs  = [k for k in keys if not _is_normal_key(k.get("code", 0))]

    if len(normal) * 7.0 > 100.0:
        # Too many keys to fit at 7% â distribute all keys equally.
        eq = round(100.0 / len(keys), 4)
        for k in keys:
            k["widthPercent"] = eq
    else:
        for k in normal:
            k["widthPercent"] = 7.0
        remaining = 100.0 - len(normal) * 7.0
        if funcs:
            fw = remaining / len(funcs)
            for k in funcs:
                k["widthPercent"] = round(fw, 4)

    # Absorb floating-point residual on the last key so the row sums to 100.
    total = sum(k["widthPercent"] for k in keys)
    diff  = 100.0 - total
    if abs(diff) > 0.0001:
        keys[-1]["widthPercent"] = round(keys[-1]["widthPercent"] + diff, 4)
    return row


def apply_shift_key_rules(ipad_layout, source_id):
    """Post-process an iPad shift layout:
    1. Dual-slide keys (label X\\nY, no sublabel) in qwerty/asdf/zxcv rows â
       show only X (the shift-state char).  Digit/symbol-shift top row excluded.
    2. IM sublabel keys whose label is a single lowercase ASCII letter â capitalize.
    No-op on non-shift layouts.
    """
    if not source_id.endswith("_shift"):
        return ipad_layout
    SEP = "\\n"
    for row in ipad_layout.get("rows", []):
        if row.get("isBottomRow", False):
            continue
        codes = {k.get("code") for k in row.get("keys", [])}
        # Digit row (1â0) or symbol-shift row (!@#â¦): keep source IM sublabel
        # symbol keys as shifted symbols. Only non-digit rows need the generic
        # dual-slide and punctuation post-processing below.
        is_digit_or_sym_row = (48 in codes and 49 in codes) or (33 in codes and 41 in codes)
        for key in row.get("keys", []):
            label = key.get("label", "")
            sublabel = key.get("sublabel", "")
            if is_digit_or_sym_row:
                continue
            else:
                if SEP in label and not sublabel:
                    # Rule 1: dual-slide punctuation/bracket, no IM component â show X.
                    # For most keys X = chr(longPressCode); swap codeâlongPressCode so
                    # tapping the simplified key actually delivers X, not Y.
                    # Exception: keys where X = chr(code) already (e.g. ï¼\nï¼) â no swap.
                    x = label.split(SEP)[0]
                    key["label"] = x
                    lp = key.get("longPressCode", 0)
                    if lp and len(x) == 1 and ord(x) == lp:
                        key["code"] = lp
                        key["longPressCode"] = 0
                elif sublabel and len(label) == 1 and 'a' <= label <= 'z':
                    # Rule 2: single lowercase letter with IM sublabel â capitalize
                    key["label"] = label.upper()
    semicolon_fix = {
        "lime_phonetic_shift": (58, ":"),
        "lime_et_41_shift": (58, ":"),
        "lime_dayi_sym_shift": (58, ":"),
    }.get(source_id)
    for row in ipad_layout.get("rows", []):
        for key in row.get("keys", []):
            if semicolon_fix and key.get("code") == 65307:
                key["code"], key["label"] = semicolon_fix
                key["longPressCode"] = 0
    if source_id == "lime_et_41_shift":
        for row in ipad_layout.get("rows", []):
            codes = {k.get("code") for k in row.get("keys", [])}
            if 90 in codes:
                for key in row.get("keys", []):
                    if key.get("code") == -1:
                        key["icon"] = "sym_keyboard_shift_shifted"
                        break
    return ipad_layout


def _make_bottom_row(is_wb=False):
    """Return a deep copy of IPAD_BOTTOM_ROW.

    is_wb is kept for API compatibility but no longer customises the row â
    wb's abc key lives in its content row (replacing the source -2 shortcut)
    so the standard bottom row applies to all layouts.
    """
    row = copy.deepcopy(IPAD_BOTTOM_ROW)
    keys = row.get("keys", [])
    if len(keys) != 6:
        raise ValueError("Generated iPad bottom row must be 6 keys")
    if any(k.get("icon") == "mic" for k in keys):
        raise ValueError("Generated iPad bottom row must use emoji, not microphone")
    if any(k.get("code") == 65292 for k in keys):
        raise ValueError("Generated iPad bottom row must not contain 。\\n，")
    return row


def validate_ipad_row_counts(ipad, source_id):
    bottom_rows = [r for r in ipad.get("rows", []) if r.get("isBottomRow", False)]
    if len(bottom_rows) != 1:
        raise ValueError(f"{source_id}: expected exactly one iPad bottom row")
    bottom_keys = bottom_rows[0].get("keys", [])
    if len(bottom_keys) != 6:
        raise ValueError(f"{source_id}: bottom row must be 6 keys, got {len(bottom_keys)}")
    if any(k.get("icon") == "mic" for k in bottom_keys):
        raise ValueError(f"{source_id}: bottom row must use emoji, not microphone")
    if any(k.get("code") == 65292 for k in bottom_keys):
        raise ValueError(f"{source_id}: bottom row must not contain 。\\n，")

    if "wb" in source_id:
        return

    content_rows = [r for r in ipad.get("rows", []) if not r.get("isBottomRow", False)]
    expected = [14, 14, 13, 12] if len(content_rows) == 4 else [14, 13, 12]
    if len(content_rows) != len(expected):
        raise ValueError(f"{source_id}: unexpected content row count {len(content_rows)}")

    names = ["digit", "qwerty", "asdf", "zxcv"] if len(expected) == 4 else ["qwerty", "asdf", "zxcv"]
    for name, row, count in zip(names, content_rows, expected):
        actual = len(row.get("keys", []))
        if actual != count:
            raise ValueError(f"{source_id}: {name} row must be {count} keys, got {actual}")


# -------------------------------------------------------------------------
# Core transform: produce one iPad layout from a phone layout dict.
#
# Two sub-cases for IM layouts:
#   â¢ 4-row (has digit or symbol-shift top row): per-row pipeline applies
#     augment_im_digit_row, transform_qwerty_row, prepend_abc_modifier,
#     append_fullshape_semicolon, append_enter_key, ensure_zxcv_shifts.
#   â¢ 3-row (no digit/symbol top row â lime_array, lime_cj family):
#     transform_no_digit_im_rows handles all rows at once.
#
# Width rule: normal (printable) keys â exactly 7%; function/modifier keys
# share the remaining width equally.  Exception: lime_wb uses proportional
# scaling (scale_row_to_100) because it has very few keys per row.
# -------------------------------------------------------------------------
def make_ipad_layout(phone, source_id):
    ipad = {}
    if source_id.endswith("_shift"):
        ipad["id"] = source_id[:-6] + "_ipad_shift"
    else:
        ipad["id"] = source_id + "_ipad"
    ipad["defaultWidthPercent"] = 7.0

    rows_in  = phone.get("rows", [])
    rows_out = []

    is_wb = "wb" in source_id
    normalise_row = scale_row_to_100 if is_wb else normalize_im_row_widths

    # Harvest keys that are moved to fixed iPad positions.
    dash_key, eq_key = harvest_dash_and_equals(phone)
    lbracket, backslash, rbracket = harvest_qwerty_bracket_keys(phone)
    semicolon_popup = harvest_popup_for_codes(phone, {58, 59})

    content_rows = [r for r in rows_in if not r.get("isBottomRow", False)]

    # has_digit_row: True for layouts with a 1â0 or !@#â¦ top row (standard IM).
    # False for 3-row layouts without a digit row (lime_array, lime_cj family).
    # The only behavioural difference is the qwerty row rightmost key:
    #   has_digit_row=True  â ï¼\nã
    #   has_digit_row=False â â«
    has_digit_row = any(
        is_number_top_row(r) or is_symbol_top_row(r)
        for r in content_rows
    )

    # Excluded from zxcv promotion:
    #   45(-) 61(=)         â digit row
    #   91([) 92(\) 93(])   â qwerty row
    #   58(:) 59(;)         â belong in asdf row; shift-layer IM components
    #   95(_) 43(+)         â shift of -/=, belong in digit row shift layer
    bottom_syms = harvest_bottom_row_symbols(phone, exclude_codes=(34, 39, 45, 61, 91, 92, 93, 58, 59, 95, 43))
    digit_dash  = dash_key

    had_bottom_row = False
    for row in rows_in:
        row = copy.deepcopy(row)
        if row.get("isBottomRow", False):
            rows_out.append(_make_bottom_row(is_wb))
            had_bottom_row = True
            continue

        row = strip_promoted_keys(row)
        if is_wb:
            # wb source has a -2 (123) shortcut in its content row.
            # On iPad that slot becomes abc(-9) â the content row gives
            # users a direct return to alphabetic mode, and the standard
            # bottom row supplies 123 for symbol access.
            for k in row["keys"]:
                if k.get("code") == -2:
                    k["code"]  = -9
                    k["label"] = "abc"
                    k["icon"]  = ""
                    break
        row["keys"] = [normalise_key(k) for k in row["keys"]]

        row = augment_im_digit_row(
            row,
            digit_dash,
            layout_dash_key=dash_key,
            eq_key=eq_key,
            preserve_source_underscore=(source_id == "lime_number_symbol_shift"),
        )
        row = transform_qwerty_row(row, lbracket=lbracket, backslash=backslash, rbracket=rbracket, has_digit_row=has_digit_row)
        row = prepend_abc_modifier(row)
        row = append_semicolon_key(row, source_id, semicolon_popup=semicolon_popup)
        row = append_fullshape_period(row, source_id)
        row = append_enter_key(row)
        row = ensure_zxcv_shifts(row, extra_keys=bottom_syms)
        row = apply_zxcv_punct_sliding(row)

        rows_out.append(normalise_row(row))

    # No-digit layouts (lime_array, lime_cj family) have no isBottomRow source row.
    if not had_bottom_row:
        rows_out.append(_make_bottom_row(is_wb))

    ipad["rows"] = rows_out
    apply_shift_key_rules(ipad, source_id)

    validate_ipad_row_counts(ipad, source_id)
    return ipad


# -------------------------------------------------------------------------
# Jobs: (source_file_stem, output_file_stem)
# -------------------------------------------------------------------------
JOBS = [
    # lime_phonetic
    ("lime_phonetic",         "lime_phonetic_ipad"),
    ("lime_phonetic_shift",   "lime_phonetic_ipad_shift"),
    # lime_array
    ("lime_array",            "lime_array_ipad"),
    ("lime_array_shift",      "lime_array_ipad_shift"),
    # lime_array_number
    ("lime_array_number",     "lime_array_number_ipad"),
    ("lime_array_number_shift","lime_array_number_ipad_shift"),
    # lime_cj
    ("lime_cj",               "lime_cj_ipad"),
    ("lime_cj_shift",         "lime_cj_ipad_shift"),
    # lime_cj_number
    ("lime_cj_number",        "lime_cj_number_ipad"),
    ("lime_cj_number_shift",  "lime_cj_number_ipad_shift"),
    # lime_dayi
    ("lime_dayi",             "lime_dayi_ipad"),
    ("lime_dayi_shift",       "lime_dayi_ipad_shift"),
    # lime_dayi_sym
    ("lime_dayi_sym",         "lime_dayi_sym_ipad"),
    ("lime_dayi_sym_shift",   "lime_dayi_sym_ipad_shift"),
    # lime_et26
    ("lime_et26",             "lime_et26_ipad"),
    ("lime_et26_shift",       "lime_et26_ipad_shift"),
    # lime_et_41
    ("lime_et_41",            "lime_et_41_ipad"),
    ("lime_et_41_shift",      "lime_et_41_ipad_shift"),
    # lime_hsu
    ("lime_hsu",              "lime_hsu_ipad"),
    ("lime_hsu_shift",        "lime_hsu_ipad_shift"),
    # lime_number_symbol
    ("lime_number_symbol",       "lime_number_symbol_ipad"),
    ("lime_number_symbol_shift", "lime_number_symbol_ipad_shift"),
    # lime_num_sym2
    ("lime_num_sym2",       "lime_num_sym2_ipad"),
    ("lime_num_sym2_shift", "lime_num_sym2_ipad_shift"),
    # lime_wb
    ("lime_wb",               "lime_wb_ipad"),
    ("lime_wb_shift",         "lime_wb_ipad_shift"),
]


def main():
    generated = []
    errors = []

    for src_stem, out_stem in JOBS:
        if src_stem in EXCLUDED_IPAD_LAYOUTS:
            continue

        src_path = os.path.join(LAYOUTS_DIR, src_stem + ".json")
        out_path = os.path.join(LAYOUTS_DIR, out_stem + ".json")

        if not os.path.exists(src_path):
            errors.append(f"  MISSING source: {src_path}")
            continue

        with open(src_path, "r", encoding="utf-8-sig") as f:
            phone = json.load(f)

        ipad = make_ipad_layout(phone, src_stem)

        existing = b""
        if os.path.exists(out_path):
            with open(out_path, "rb") as old:
                existing = old.read()
        encoding = "utf-8-sig" if existing.startswith(b"\xef\xbb\xbf") else "utf-8"
        ending = "\n" if existing.endswith(b"\n") else ""
        with open(out_path, "w", encoding=encoding) as f:
            json.dump(ipad, f, indent=2, ensure_ascii=False)
            f.write(ending)

        generated.append(out_path)
        print(f"  OK  {out_stem}.json")

    print(f"\nGenerated {len(generated)} iPad layout files.")
    if errors:
        print("\nErrors:")
        for e in errors:
            print(e)


if __name__ == "__main__":
    main()
