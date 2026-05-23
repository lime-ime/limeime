#!/usr/bin/env python3
# Generate iPad keyboard layout JSON files for LimeIME.
# See docs/IPAD_KEYBOARD.md §4.2 for specifications.
#
# Usage: python3 .claude/scripts/generate_ipad_layouts.py
# Replaces all *_ipad.json files in LimeIME-iOS/LimeKeyboard/Layouts/

import json, os, sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
LAYOUTS_DIR = os.path.normpath(os.path.join(SCRIPT_DIR,
    '../LimeIME-iOS/LimeKeyboard/Layouts'))

# LimeKeyCode values (mirror LimeKeyCode.swift)
C_ENTER = 10; C_SPACE = 32; C_DELETE = -5; C_SHIFT = -1
C_DONE  = -3; C_SYM   = -2; C_EN    = -9;  C_IM    = -10
C_GLOBE = -200; C_MENU = -100; C_EMOJI = -201; C_TAB = 9
C_ARRL = -30; C_ARRR = -31; C_ARRU = -32; C_ARRD = -33


# ---------------------------------------------------------------------------
# Key helper
# ---------------------------------------------------------------------------

def mk(code, *, label='', sublabel='', width=7.15, icon='',
       mod=False, rep=False, sticky=False, lp=0, popup='', pchars=''):
    return {
        'code': code, 'codes': [code],
        'label': label, 'sublabel': sublabel,
        'widthPercent': round(float(width), 4), 'icon': icon,
        'isModifier': bool(mod), 'isRepeatable': bool(rep),
        'isSticky': bool(sticky), 'longPressCode': lp,
        'popupKeyboard': popup, 'popupCharacters': pchars,
    }


def spacer(w):
    return mk(0, width=w)


def fix_total(keys, target=100.0):
    """Adjust the last key so widths sum to exactly target."""
    total = sum(k['widthPercent'] for k in keys)
    diff = round(target - total, 4)
    if abs(diff) > 0.0001:
        keys[-1]['widthPercent'] = round(keys[-1]['widthPercent'] + diff, 4)
    return keys


# ---------------------------------------------------------------------------
# Fixed rows
# ---------------------------------------------------------------------------

def bottom_row(sym='.?123', left_cjk=False, cjk=False, shifted=False):
    # Non-CJK: globe(8) + left(10) + emoji(7) + space(57) + sym(10) + dismiss(8) = 100
    # CJK:     globe(8) + left(10) + emoji(7) + space(50) + ，/。(7) + sym(10) + dismiss(8) = 100
    # shifted: ，/。 key becomes 。only
    # left_cjk=True: replace left sym key with 中 (C_IM) key for symbol layouts
    left_key = (mk(C_IM, label='中', width=10.0, mod=True) if left_cjk
                else mk(C_SYM, label=sym, width=10.0, mod=True))
    if cjk:
        comma_key = (mk(0x3002, label='。', width=7.0) if shifted
                     else mk(0xFF0C, label='。\\n，', width=7.0, lp=0x3002))
        return fix_total([
            mk(C_GLOBE, icon='globe',                         width=8.0,  mod=True, lp=C_MENU),
            left_key,
            mk(C_EMOJI, icon='face.smiling',                   width=7.0,  mod=True),
            mk(C_SPACE, icon='space.bar',                     width=50.0),
            comma_key,
            mk(C_SYM,   label=sym,                            width=10.0, mod=True),
            mk(C_DONE,  icon='keyboard.chevron.compact.down', width=8.0,  mod=True, lp=C_MENU),
        ])
    return fix_total([
        mk(C_GLOBE, icon='globe',                         width=8.0,  mod=True, lp=C_MENU),
        left_key,
        mk(C_EMOJI, icon='face.smiling',                   width=7.0,  mod=True),
        mk(C_SPACE, icon='space.bar',                     width=57.0),
        mk(C_SYM,   label=sym,                            width=10.0, mod=True),
        mk(C_DONE,  icon='keyboard.chevron.compact.down', width=8.0,  mod=True, lp=C_MENU),
    ])


def row1_english():
    """13 dual-symbol top-row keys + backspace. Sum = 100."""
    # pairs: (symbol_code, symbol_char, number_code, number_char)
    # tap=number (large, bottom), slide=symbol (small, top)
    pairs = [
        (126,'~', 96,'`'), (33,'!',  49,'1'), (64,'@', 50,'2'), (35,'#',  51,'3'),
        (36, '$', 52,'4'), (37,'%',  53,'5'), (94,'^', 54,'6'), (38,'&',  55,'7'),
        (42, '*', 56,'8'), (40,'(',  57,'9'), (41,')', 48,'0'), (95,'_',  45,'-'),
        (43, '+', 61,'='),
    ]
    KEY = 7.0  # unified content key width; fix_total adjusts DELETE to fill remainder
    keys = [mk(c2, label=f'{p}\\n{s}', width=KEY, lp=c1) for c1, p, c2, s in pairs]
    keys.append(mk(C_DELETE, icon='delete.backward', width=7.0, mod=True, rep=True))
    return fix_total(keys)


def row1_english_shift():
    """Shift variant: each top-row key shows only the shifted symbol (no dual label)."""
    # Shifted: ~ ! @ # $ % ^ & * ( ) _ +
    shift_pairs = [(126,'~'), (33,'!'), (64,'@'), (35,'#'), (36,'$'), (37,'%'), (94,'^'),
                   (38,'&'), (42,'*'), (40,'('), (41,')'), (95,'_'), (43,'+')]
    KEY = 7.0
    keys = [mk(c, label=s, width=KEY) for c, s in shift_pairs]
    keys.append(mk(C_DELETE, icon='delete.backward', width=7.0, mod=True, rep=True))
    return fix_total(keys)


def row1_phonetic():
    """Phonetic top row: ~.(dual) + 11 phonetic chars + —…(dual) + backspace. Sum = 100."""
    phonetic = [
        (49,'ㄅ'), (50,'ㄉ'), (51,'ˇ'), (52,'ˋ'), (53,'ㄓ'),
        (54,'ˊ'), (55,'˙'), (56,'ㄚ'), (57,'ㄞ'), (48,'ㄢ'), (45,'ㄦ'),
    ]
    KEY = 7.0
    keys = [mk(126, label='.\\n~', width=KEY, lp=46)]
    keys += [mk(c, label=ch, width=KEY) for c, ch in phonetic]
    keys.append(mk(8212, label='…\\n—', width=KEY, lp=8230))
    keys.append(mk(C_DELETE, icon='delete.backward', width=7.0, mod=True, rep=True))
    return fix_total(keys)


# ---------------------------------------------------------------------------
# Read phone layouts
# ---------------------------------------------------------------------------

_SYSTEM_CODES = frozenset({
    C_SHIFT, C_DONE, C_SYM, C_EN, C_IM, C_DELETE, C_ENTER,
    C_SPACE, C_GLOBE, C_MENU, C_TAB,
    -3, -4, -5, -6, -7, -8, -9, -10, -15, -20, -21, -30, -31, -32, -33,
    -99, -100, -200,
})
_SKIP_LABELS = frozenset({
    '123', 'EN', 'ABC', '中文', '中', '@string/label_symbol_key',
    '.?123', '', 'return', 'delete.backward', 'space.bar',
})


def load_phone(name):
    path = os.path.join(LAYOUTS_DIR, name + '.json')
    if not os.path.exists(path):
        return None
    with open(path, encoding='utf-8-sig') as f:
        return json.load(f)


def extract_alpha(phone_data, row_idx):
    """Return alpha key (code, label, sublabel) tuples from a phone JSON row."""
    if phone_data is None or row_idx >= len(phone_data['rows']):
        return []
    result = []
    for k in phone_data['rows'][row_idx]['keys']:
        code = k.get('code', 0)
        if code in _SYSTEM_CODES:
            continue
        if k.get('isModifier'):
            continue
        if k.get('icon'):
            continue
        label = k.get('label', '')
        if label in _SKIP_LABELS:
            continue
        # Handle \t-encoded dual labels (et26/hsu style: "q\tㄗ")
        if '\t' in label:
            parts = label.split('\t', 1)
            label = parts[0]
            sublabel_override = parts[1]
        else:
            sublabel_override = k.get('sublabel', '')
        result.append((code, label, sublabel_override))
    return result


def alpha_keys(tuples, width, target_n=None):
    """Build key dicts from (code, label, sublabel) tuples, padding with spacers."""
    keys = [mk(c, label=lbl, sublabel=sub, width=width) for c, lbl, sub in tuples]
    if target_n is not None:
        while len(keys) < target_n:
            keys.append(spacer(width))
    return keys


# ---------------------------------------------------------------------------
# Row builders
# ---------------------------------------------------------------------------

def build_row2(phone_alpha_row0, bracket_cluster='english', is_shift=False):
    """Tab + 13 equal-width content keys = 100%.  KEY=7.0, TAB fills remainder."""
    # 13 × 7.0 = 91.0 → TAB = 9.0
    TAB_W = 9.0
    KEY_W = 7.0
    alpha = alpha_keys(phone_alpha_row0, KEY_W, target_n=10)
    if bracket_cluster == 'cjk':
        cluster = [
            mk(12300, label='『\\n「', width=KEY_W, lp=12302),
            mk(12301, label='』\\n」', width=KEY_W, lp=12303),
            mk(12289, label='|\\n、',  width=KEY_W, lp=124),
        ]
    elif is_shift:  # shifted: single symbol only
        cluster = [
            mk(123, label='{', width=KEY_W),
            mk(125, label='}', width=KEY_W),
            mk(124, label='|', width=KEY_W),
        ]
    else:  # normal — tap=plain, slide=shifted (matches Apple iPad convention)
        cluster = [
            mk(91,  label='{\\n[',  width=KEY_W, lp=123),
            mk(93,  label='}\\n]',  width=KEY_W, lp=125),
            mk(92,  label='|\\n\\', width=KEY_W, lp=124),
        ]
    keys = [mk(C_TAB, icon='arrow.forward.to.line', width=TAB_W, mod=True)]
    keys += alpha
    keys += cluster
    return fix_total(keys)


def build_row3_phonetic(im_code, im_label, phone_alpha_row1, im_sub=''):
    """IM(7) + 10 phonetic + :;(6) + search(9.5) = 13. Sum = 100."""
    # 7 + 10×KEY + 6 + 9.5 = 100  →  KEY = 77.5/10 = 7.75
    KEY_W = 7.75
    alpha = alpha_keys(phone_alpha_row1, KEY_W, target_n=10)
    keys = [mk(im_code, label=im_label, sublabel=im_sub, width=7.0, mod=True)]
    keys += alpha
    keys.append(mk(58, label=';\\n:', width=6.0, lp=59))
    keys.append(mk(C_ENTER, icon='return', width=9.5, mod=True))
    return fix_total(keys)


def build_row3_english(im_code, im_label, phone_alpha_row1, im_sub='', is_shift=False):
    """IM(9) + 11 equal-width keys(7.0 each) + search(14) = 100."""
    # 9 + 11×7.0 + 14 = 9+77+14 = 100
    KEY_W = 7.0
    alpha = alpha_keys(phone_alpha_row1, KEY_W, target_n=9)
    keys = [mk(im_code, label=im_label, sublabel=im_sub, width=9.0, mod=True)]
    keys += alpha
    if is_shift:
        keys.append(mk(58, label=':', width=KEY_W))   # : only
        keys.append(mk(34, label='"', width=KEY_W))   # " only
    else:
        keys.append(mk(59, label=':\\n;', width=KEY_W, lp=58))   # tap=;  slide=:
        keys.append(mk(44, label='"\\n,', width=KEY_W, lp=34))   # tap=,  slide="
    keys.append(mk(C_ENTER, icon='return', width=14.0, mod=True))
    return fix_total(keys)


def build_row3_phonetic(im_code, im_label, phone_alpha_row1, im_sub=''):
    """IM(9) + 10 phonetic + colon(7) + search(14) = 100."""
    # 9 + 10×7.0 + 7 + 14 = 100
    KEY_W = 7.0
    alpha = alpha_keys(phone_alpha_row1, KEY_W, target_n=10)
    keys = [mk(im_code, label=im_label, sublabel=im_sub, width=9.0, mod=True)]
    keys += alpha
    keys.append(mk(58, label=';\\n:', width=KEY_W, lp=59))
    keys.append(mk(C_ENTER, icon='return', width=14.0, mod=True))
    return fix_total(keys)


def build_row4_phonetic(phone_alpha_row2):
    """Shift(15) + 10 phonetic(7.0 each) + shift(15) = 100."""
    # 15 + 10×7.0 + 15 = 100
    KEY_W = 7.0
    alpha = alpha_keys(phone_alpha_row2, KEY_W, target_n=10)
    keys = [mk(C_SHIFT, icon='shift', width=15.0, mod=True, sticky=True)]
    keys += alpha
    keys.append(mk(C_SHIFT, icon='shift', width=15.0, mod=True, sticky=True))
    return fix_total(keys)


def build_row4_english(phone_alpha_row2, is_shift=False):
    """Shift(15) + 10 equal-width keys(7.0 each) + shift(15) = 100."""
    # 15 + 10×7.0 + 15 = 100
    KEY_W = 7.0
    alpha = alpha_keys(phone_alpha_row2, KEY_W, target_n=7)
    keys = [mk(C_SHIFT, icon='shift', width=15.0, mod=True, sticky=True)]
    keys += alpha
    if is_shift:
        keys.append(mk(60, label='<', width=KEY_W))
        keys.append(mk(62, label='>', width=KEY_W))
        keys.append(mk(63, label='?', width=KEY_W))
    else:
        keys.append(mk(44, label='<\\n,', width=KEY_W, lp=60))
        keys.append(mk(46, label='>\\n.', width=KEY_W, lp=62))
        keys.append(mk(47, label='?\\n/', width=KEY_W, lp=63))
    keys.append(mk(C_SHIFT, icon='shift', width=15.0, mod=True, sticky=True))
    return fix_total(keys)


# ---------------------------------------------------------------------------
# Layout writers
# ---------------------------------------------------------------------------

def write_layout(lid, rows_data):
    """rows_data: [(keys_list, is_bottom_row), ...]"""
    data = {
        'id': lid,
        'defaultWidthPercent': 7.15,
        'rows': [{'isBottomRow': bool(b), 'keys': k} for k, b in rows_data],
    }
    path = os.path.join(LAYOUTS_DIR, lid + '.json')
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f'  {lid}.json')


def make_english_style(lid, phone_name, im_toggle_code, im_toggle_label,
                        row2_bracket='english'):
    """English-style 5-row layout (dual top row + QWERTY alpha)."""
    phone = load_phone(phone_name)

    # Determine which phone rows are alpha rows (skip pure-digit first rows)
    # English/ABC have 3 alpha rows: rows 0,1,2 (before bottom)
    # For IMs with a digit/special row first, skip row 0
    rows = phone['rows'] if phone else []
    # Find bottom row (isBottomRow=True or last row)
    bottom_idx = next((i for i, r in enumerate(rows) if r.get('isBottomRow')), len(rows)-1)
    alpha_rows = rows[:bottom_idx]

    # Extract alpha data from up to 3 rows
    a0 = extract_alpha(phone, 0) if len(alpha_rows) > 0 else []
    a1 = extract_alpha(phone, 1) if len(alpha_rows) > 1 else []
    a2 = extract_alpha(phone, 2) if len(alpha_rows) > 2 else []

    # If first row looks like digits (codes 48-57), skip it and shift
    first_codes = [c for c, _, _ in a0]
    if first_codes and all(48 <= c <= 57 for c in first_codes):
        # Digit row — use as number row content or just use standard English top
        a0, a1, a2 = a1, a2, (extract_alpha(phone, 3) if len(alpha_rows) > 3 else [])

    r2 = build_row2(a0[:10], bracket_cluster=row2_bracket)
    r3 = build_row3_english(im_toggle_code, im_toggle_label, a1[:9])
    r4 = build_row4_english(a2[:7])

    write_layout(lid, [
        (row1_english(),  False),
        (r2,              False),
        (r3,              False),
        (r4,              False),
        (bottom_row(),    True),
    ])
    # _shift variant — same structure (proper naming: base_ipad_shift)
    write_layout(lid + '_shift', [
        (row1_english(),  False),
        (r2,              False),
        (r3,              False),
        (r4,              False),
        (bottom_row(),    True),
    ])


def make_phonetic_style(lid, phone_name, im_toggle_code, im_toggle_label,
                        row2_bracket='cjk'):
    """Phonetic-style 5-row layout (phonetic top row + dense alpha rows)."""
    phone = load_phone(phone_name)

    # Phonetic: phone rows 0=digits(skip), 1=qwerty, 2=asdf, 3=zxcv, 4=bottom
    # But phone phonetic layout has: row0=digits, row1=qwerty, row2=asdf, row3=zxcv, row4=bottom
    # For iPad: use rows 1,2,3 as alpha rows 2,3,4
    # row1 → iPad row2, row2 → iPad row3, row3 → iPad row4
    a_r2 = extract_alpha(phone, 1)   # qwerty row → iPad row 2
    a_r3 = extract_alpha(phone, 2)   # asdf row  → iPad row 3
    a_r4 = extract_alpha(phone, 3)   # zxcv row  → iPad row 4

    r2 = build_row2(a_r2[:10], bracket_cluster=row2_bracket)
    r3 = build_row3_phonetic(im_toggle_code, im_toggle_label, a_r3[:10])
    r4 = build_row4_phonetic(a_r4[:10])

    write_layout(lid, [
        (row1_phonetic(), False),
        (r2,              False),
        (r3,              False),
        (r4,              False),
        (bottom_row(),    True),
    ])
    write_layout(lid + '_shift', [
        (row1_phonetic(), False),
        (r2,              False),
        (r3,              False),
        (r4,              False),
        (bottom_row(),    True),
    ])


# ---------------------------------------------------------------------------
# Symbols layouts
# ---------------------------------------------------------------------------

def make_symbols1():
    """symbols1_ipad.json — §4.2.3."""
    # Row 1 (13 + backspace = 14): ` 1 2 3 4 5 6 7 8 9 0 < > ⌫
    KEY_R1 = round(92.44/13, 4)
    r1 = fix_total([
        mk(96,  label='`',  width=KEY_R1),
        mk(49,  label='1',  width=KEY_R1), mk(50, label='2', width=KEY_R1),
        mk(51,  label='3',  width=KEY_R1), mk(52, label='4', width=KEY_R1),
        mk(53,  label='5',  width=KEY_R1), mk(54, label='6', width=KEY_R1),
        mk(55,  label='7',  width=KEY_R1), mk(56, label='8', width=KEY_R1),
        mk(57,  label='9',  width=KEY_R1), mk(48, label='0', width=KEY_R1),
        mk(60,  label='<',  width=KEY_R1), mk(62, label='>', width=KEY_R1),
        mk(C_DELETE, icon='delete.backward', width=7.56, mod=True, rep=True),
    ])

    # Row 2 (tab + 13 = 14): → [ ] { } # % ^ * + = \ ~
    TAB = 7.0; KEY = round(93.0/13, 4)
    r2 = fix_total([
        mk(C_TAB, icon='arrow.forward.to.line', width=TAB, mod=True),
        mk(91,label='[',width=KEY), mk(93,label=']',width=KEY),
        mk(123,label='{',width=KEY), mk(125,label='}',width=KEY),
        mk(35,label='#',width=KEY),  mk(37,label='%',width=KEY),
        mk(94,label='^',width=KEY),  mk(42,label='*',width=KEY),
        mk(43,label='+',width=KEY),  mk(61,label='=',width=KEY),
        mk(92,label='\\',width=KEY), mk(126,label='~',width=KEY),
        spacer(KEY),  # pad to 14 keys
    ])

    # Row 3 (undo + 11 keys + search = 13):
    # undo | - / : ; ( ) $ & @ £ ¥ | search
    # undo = modifier placeholder (no standard iOS undo code — use C_DONE with label override)
    # We'll use code=0 for undo with a label, or better: use a unique placeholder
    # Using code=-98 as undo placeholder
    # Row 3: 11 content(7.5) + UP(7.5) + ENTER(10) = 100
    KEY3 = 7.5
    r3 = fix_total([
        mk(45,label='-',width=KEY3),  mk(47,label='/',width=KEY3),
        mk(58,label=':',width=KEY3),  mk(59,label=';',width=KEY3),
        mk(40,label='(',width=KEY3),  mk(41,label=')',width=KEY3),
        mk(36,label='$',width=KEY3),  mk(38,label='&',width=KEY3),
        mk(64,label='@',width=KEY3),  mk(163,label='£',width=KEY3),
        mk(165,label='¥',width=KEY3),
        mk(C_ARRU, icon='arrow.up',   width=KEY3, mod=True, rep=True),
        mk(C_ENTER, icon='return', width=10.0, mod=True),
    ])

    # Row 4: spacer(5) + 9 content(7.5) + LEFT(7.5) + DOWN(7.5) + RIGHT(7.5) + spacer(5) = 100
    KEY4 = 7.5
    r4 = fix_total([
        spacer(7.5),
        mk(8230,label='…',width=KEY4), mk(46,label='.',width=KEY4),
        mk(44,label=',',width=KEY4),   mk(63,label='?',width=KEY4),
        mk(33,label='!',width=KEY4),   mk(39,label="'",width=KEY4),
        mk(34,label='"',width=KEY4),   mk(95,label='_',width=KEY4),
        mk(8364,label='€',width=KEY4),
        mk(C_ARRL, icon='arrow.left',  width=KEY4, mod=True, rep=True),
        mk(C_ARRD, icon='arrow.down',  width=KEY4, mod=True, rep=True),
        mk(C_ARRR, icon='arrow.right', width=KEY4, mod=True, rep=True),
        spacer(5.0),
    ])

    write_layout('symbols1_ipad', [
        (r1, False), (r2, False), (r3, False), (r4, False),
        (bottom_row('abc', left_cjk=True), True),
    ])


def make_symbols2():
    """symbols2_ipad.json — same scaffold, symbols2 glyph set."""
    phone = load_phone('symbols2')
    if not phone:
        print('  symbols2.json not found, skipping')
        return

    # Just use English dual-symbol top row + extract 3 alpha rows from phone
    KEY_R1 = round(92.44/13, 4)
    r1 = fix_total([
        mk(96,label='`',width=KEY_R1),
        mk(49,label='1',width=KEY_R1), mk(50,label='2',width=KEY_R1),
        mk(51,label='3',width=KEY_R1), mk(52,label='4',width=KEY_R1),
        mk(53,label='5',width=KEY_R1), mk(54,label='6',width=KEY_R1),
        mk(55,label='7',width=KEY_R1), mk(56,label='8',width=KEY_R1),
        mk(57,label='9',width=KEY_R1), mk(48,label='0',width=KEY_R1),
        mk(60,label='<',width=KEY_R1), mk(62,label='>',width=KEY_R1),
        mk(C_DELETE, icon='delete.backward', width=7.56, mod=True, rep=True),
    ])

    bottom_rows_idx = next((i for i,r in enumerate(phone['rows']) if r.get('isBottomRow')), len(phone['rows'])-1)
    alpha_rows = phone['rows'][:bottom_rows_idx]

    TAB = 7.0; KEY2 = round(93.0/13, 4)
    a0 = extract_alpha(phone, 0)[:13]
    while len(a0) < 13: a0.append((0,'',''))
    r2_keys = [mk(C_TAB, icon='arrow.forward.to.line', width=TAB, mod=True)]
    for c,lbl,sub in a0:
        r2_keys.append(mk(c, label=lbl, sublabel=sub, width=KEY2) if c != 0 else spacer(KEY2))
    r2 = fix_total(r2_keys)

    KEY3 = 7.5
    a1 = extract_alpha(phone, 1)[:11]
    while len(a1) < 11: a1.append((0,'',''))
    r3_keys = []
    for c,lbl,sub in a1:
        r3_keys.append(mk(c, label=lbl, sublabel=sub, width=KEY3) if c != 0 else spacer(KEY3))
    r3_keys.append(mk(C_ARRU, icon='arrow.up',        width=KEY3, mod=True, rep=True))
    r3_keys.append(mk(C_ENTER, icon='return', width=10.0, mod=True))
    r3 = fix_total(r3_keys)

    KEY4 = 7.5
    a2 = extract_alpha(phone, 2)[:9]
    while len(a2) < 9: a2.append((0,'',''))
    r4_keys = [spacer(7.5)]
    for c,lbl,sub in a2:
        r4_keys.append(mk(c, label=lbl, sublabel=sub, width=KEY4) if c != 0 else spacer(KEY4))
    r4_keys.append(mk(C_ARRL, icon='arrow.left',  width=KEY4, mod=True, rep=True))
    r4_keys.append(mk(C_ARRD, icon='arrow.down',  width=KEY4, mod=True, rep=True))
    r4_keys.append(mk(C_ARRR, icon='arrow.right', width=KEY4, mod=True, rep=True))
    r4_keys.append(spacer(5.0))
    r4 = fix_total(r4_keys)

    write_layout('symbols2_ipad', [
        (r1, False), (r2, False), (r3, False), (r4, False),
        (bottom_row('abc', left_cjk=True), True),
    ])


def make_symbols3():
    """symbols3_ipad.json — same scaffold, symbols3 glyph set."""
    phone = load_phone('symbols3')
    if not phone:
        print('  symbols3.json not found, skipping')
        return

    KEY_R1 = round(92.44/13, 4)
    r1 = fix_total([
        mk(96,label='`',width=KEY_R1),
        mk(49,label='1',width=KEY_R1), mk(50,label='2',width=KEY_R1),
        mk(51,label='3',width=KEY_R1), mk(52,label='4',width=KEY_R1),
        mk(53,label='5',width=KEY_R1), mk(54,label='6',width=KEY_R1),
        mk(55,label='7',width=KEY_R1), mk(56,label='8',width=KEY_R1),
        mk(57,label='9',width=KEY_R1), mk(48,label='0',width=KEY_R1),
        mk(60,label='<',width=KEY_R1), mk(62,label='>',width=KEY_R1),
        mk(C_DELETE, icon='delete.backward', width=7.56, mod=True, rep=True),
    ])

    TAB = 7.0; KEY2 = round(93.0/13, 4)
    a0 = extract_alpha(phone, 0)[:13]
    while len(a0) < 13: a0.append((0,'',''))
    r2_keys = [mk(C_TAB, icon='arrow.forward.to.line', width=TAB, mod=True)]
    for c,lbl,sub in a0:
        r2_keys.append(mk(c, label=lbl, sublabel=sub, width=KEY2) if c != 0 else spacer(KEY2))
    r2 = fix_total(r2_keys)

    KEY3 = 7.5
    a1 = extract_alpha(phone, 1)[:11]
    while len(a1) < 11: a1.append((0,'',''))
    r3_keys = []
    for c,lbl,sub in a1:
        r3_keys.append(mk(c, label=lbl, sublabel=sub, width=KEY3) if c != 0 else spacer(KEY3))
    r3_keys.append(mk(C_ARRU, icon='arrow.up',        width=KEY3, mod=True, rep=True))
    r3_keys.append(mk(C_ENTER, icon='return', width=10.0, mod=True))
    r3 = fix_total(r3_keys)

    KEY4 = 7.5
    a2 = extract_alpha(phone, 2)[:9]
    while len(a2) < 9: a2.append((0,'',''))
    r4_keys = [spacer(7.5)]
    for c,lbl,sub in a2:
        r4_keys.append(mk(c, label=lbl, sublabel=sub, width=KEY4) if c != 0 else spacer(KEY4))
    r4_keys.append(mk(C_ARRL, icon='arrow.left',  width=KEY4, mod=True, rep=True))
    r4_keys.append(mk(C_ARRD, icon='arrow.down',  width=KEY4, mod=True, rep=True))
    r4_keys.append(mk(C_ARRR, icon='arrow.right', width=KEY4, mod=True, rep=True))
    r4_keys.append(spacer(5.0))
    r4 = fix_total(r4_keys)

    write_layout('symbols3_ipad', [
        (r1, False), (r2, False), (r3, False), (r4, False),
        (bottom_row('abc', left_cjk=True), True),
    ])


# ---------------------------------------------------------------------------
# CJK IMs — clean rebuild: all phone alpha keys preserved, only function
# keys rearranged, globe + mic added, both L/R shift added.
# ---------------------------------------------------------------------------

def _cap(lbl):
    """Uppercase single lowercase ASCII letter labels for shift layout."""
    if len(lbl) == 1 and 'a' <= lbl <= 'z':
        return lbl.upper()
    return lbl


def build_cjk_ipad(lid, phone_name):
    """Build CJK iPad layout preserving all phone alpha/digit keys exactly.

    Row 0: first row (digits or alpha) + DELETE
    Row 1: second alpha row, normalized to 100%
    Row 2: third alpha row + RETURN (enter key on the asdf row)
    Row 3: LEFT_SHIFT + (4th-row alpha + bottom-row alpha) + RIGHT_SHIFT
    Row 4: globe + IM + mic + space + dismiss  (isBottomRow, no enter)
    """
    phone = load_phone(phone_name)
    if not phone:
        phone = load_phone(lid)
    if not phone:
        print(f'  SKIP {lid}: no phone layout found')
        return

    rows = phone['rows']
    bottom_idx = next((i for i, r in enumerate(rows) if r.get('isBottomRow')), len(rows)-1)

    # Collect alpha tuples from every non-bottom row (in order)
    non_bottom_alpha = []
    for i in range(bottom_idx):
        a = extract_alpha(phone, i)
        if a:
            non_bottom_alpha.append(a)

    # Alpha keys sitting on the phone bottom row (e.g. , . / for dayi)
    bottom_alpha = []
    for k in rows[bottom_idx]['keys']:
        c = k.get('code', 0)
        if c in _SYSTEM_CODES or k.get('isModifier') or k.get('icon'):
            continue
        lbl = k.get('label', '')
        if lbl in _SKIP_LABELS:
            continue
        bottom_alpha.append((c, lbl, k.get('sublabel', '')))

    im_code, im_lbl, im_sub = extract_im_key(phone)
    n_alpha = len(non_bottom_alpha)

    ar0 = non_bottom_alpha[0] if n_alpha > 0 else []
    ar1 = non_bottom_alpha[1] if n_alpha > 1 else []
    ar2 = non_bottom_alpha[2] if n_alpha > 2 else []
    ar3 = non_bottom_alpha[3] if n_alpha > 3 else []
    shift_alpha = ar3 + bottom_alpha

    # All CJK normal keys = 7% (same as English keyboard).
    KEY_W = 7.0

    # Digit → shift-symbol mapping for number-row shift layout.
    _DIGIT_SHIFT = {49:33, 50:64, 51:35, 52:36, 53:37, 54:94, 55:38, 56:42, 57:40, 48:41}
    _SHIFT_CHAR  = {33:'!', 64:'@', 35:'#', 36:'$', 37:'%', 94:'^', 38:'&', 42:'*', 40:'(', 41:')'}
    _PUNCT_SHIFT = {
        45:95, 61:43, 91:123, 93:125, 92:124, 59:58,
        39:34, 44:60, 46:62, 47:63,
    }
    _PUNCT_SHIFT_CHAR = {
        95:'_', 43:'+', 123:'{', 125:'}', 124:'|', 58:':',
        34:'"', 60:'<', 62:'>', 63:'?',
    }

    def _row(tuples):
        """Normal row: preserve original codes and labels."""
        return [mk(c, label=lbl, sublabel=sub, width=KEY_W) for c, lbl, sub in tuples]

    def _row_shift(tuples):
        """Shift row: uppercase letters (code+label), shift symbols for digits."""
        keys = []
        for c, lbl, sub in tuples:
            if c in _DIGIT_SHIFT:
                sc = _DIGIT_SHIFT[c]
                keys.append(mk(sc, label=_SHIFT_CHAR[sc], sublabel=sub, width=KEY_W))
            elif c in _PUNCT_SHIFT:
                sc = _PUNCT_SHIFT[c]
                keys.append(mk(sc, label=_PUNCT_SHIFT_CHAR[sc], sublabel=sub, width=KEY_W))
            elif 97 <= c <= 122:  # lowercase ASCII letter → uppercase
                keys.append(mk(c - 32, label=lbl.upper(), sublabel=sub, width=KEY_W))
            else:
                keys.append(mk(c, label=lbl, sublabel=sub, width=KEY_W))
        return keys

    def _row_digits_dual(tuples):
        """Non-sym digit row: synthesize 'shift\\ndigit' dual label with longPressCode.
        Matches English iPad behavior: direct=digit (bottom large), sliding=shift symbol (top small)."""
        keys = []
        for c, lbl, sub in tuples:
            if c in _DIGIT_SHIFT and not sub:
                sc = _DIGIT_SHIFT[c]
                keys.append(mk(c, label=f'{_SHIFT_CHAR[sc]}\\n{lbl}', width=KEY_W, lp=sc))
            else:
                keys.append(mk(c, label=lbl, sublabel=sub, width=KEY_W))
        return keys

    # Detect number row: ar0 contains digit keys (codes 48–57)
    has_number_row = bool(ar0) and any(48 <= c <= 57 for c, _, _ in ar0)

    _tab  = mk(C_TAB,    icon='arrow.forward.to.line', width=9.0,   mod=True)
    _tab7 = mk(C_TAB,    icon='arrow.forward.to.line', width=KEY_W, mod=True)
    _del  = mk(C_DELETE, icon='delete.backward',       width=KEY_W, mod=True, rep=True)
    _del9 = mk(C_DELETE, icon='delete.backward',       width=9.0,   mod=True, rep=True)
    # Sliding keys: format 'sliding\\ndirect' — sliding char is small TOP, direct is large BOTTOM.
    _em_dash       = mk(8212, label='…\\n—',  width=KEY_W, lp=8230)  # direct=—, sliding=…
    _ellipsis_only = mk(8230, label='…',       width=KEY_W)
    _slash_tilde       = mk(96,  label='~\\n`', width=KEY_W, lp=126)   # sliding=~(top), direct=`(bottom)
    _slash_tilde_shift = mk(126, label='~',     width=KEY_W)           # shifted: show only ~

    # Bracket cluster used on r0 (5-row) or split between r0/r1 (4-row).
    _br_left        = mk(12300, label='『\\n「', width=KEY_W, lp=12302)
    _br_right       = mk(12301, label='』\\n」', width=KEY_W, lp=12303)
    _br_pipe        = mk(12289, label='|\\n、',  width=KEY_W, lp=124)
    _br_left_shift  = mk(12302, label='『', width=KEY_W)
    _br_right_shift = mk(12303, label='』', width=KEY_W)
    _br_pipe_shift  = mk(124,   label='|',  width=KEY_W)

    # ---- Row 0 ----
    if has_number_row:
        # 5-row: digit row at top, NO tab. delete on right edge.
        # Non-sym family (digits without sublabel) → synthesize dual 'shift\\ndigit' labels.
        digit_has_sub = any(sub for c, _, sub in ar0 if 48 <= c <= 57)
        ar0_normal = _row(ar0) if digit_has_sub else _row_digits_dual(ar0)
        r0       = fix_total([_slash_tilde]       + ar0_normal      + [_em_dash,       _del])
        r0_shift = fix_total([_slash_tilde_shift] + _row_shift(ar0) + [_ellipsis_only, _del])
    else:
        # 4-row: qwerty row at top with tab(7) + 10 alpha(7) + 2 brackets(7) + delete(9) = 100.
        # 、/| moves down to r1 (between content and ：/；).
        r0       = fix_total([_tab7] + _row(ar0)       + [_br_left,       _br_right,       _del9])
        r0_shift = fix_total([_tab7] + _row_shift(ar0) + [_br_left_shift, _br_right_shift, _del9])

    # ---- Row 1 (qwerty, 4th from bottom): tab(9) + content + 3 CJK bracket symbols ----
    # Bracket symbols: format 'sliding\\ndirect' (sliding small top, direct large bottom).
    _sym3       = [mk(12300, label='『\\n「', width=KEY_W, lp=12302),  # direct=「, sliding=『
                   mk(12301, label='』\\n」', width=KEY_W, lp=12303),  # direct=」, sliding=』
                   mk(12289, label='|\\n、',  width=KEY_W, lp=124)]   # direct=、, sliding=|
    _sym3_shift = [mk(12302, label='『', width=KEY_W),
                   mk(12303, label='』', width=KEY_W),
                   mk(124,   label='|',  width=KEY_W)]
    if has_number_row:
        if ar1:
            # tab(9) + N content(7) + 3 symbols(7) — for N=10 this is exactly 100
            r1       = fix_total([_tab] + _row(ar1)       + _sym3)
            r1_shift = fix_total([_tab] + _row_shift(ar1) + _sym3_shift)
        else:
            r1       = fix_total([_tab] + _sym3       + [spacer(100.0 - 9.0 - 3 * KEY_W)])
            r1_shift = fix_total([_tab] + _sym3_shift + [spacer(100.0 - 9.0 - 3 * KEY_W)])
    else:
        # 4-row: r1 is the asdf row → abc + ar1(10) + 、/|(7) + ：/；(7) + return.
        # abc + return split remainder = (100 − 70 − 7 − 7) / 2 = 8 each.
        r1       = None  # built below alongside r2
        r1_shift = None

    # ---- Row 2 (asdf, 3rd from bottom): abc + content + ：/；+ enter ----
    # ：/；shows only the direct output; shifted shows only ；
    _colon       = mk(0xFF1A, label='；\\n：', width=KEY_W, lp=0xFF1B)
    _semicolon   = mk(0xFF1B, label='；', width=KEY_W)
    _enter       = lambda w: mk(C_ENTER, icon='return', width=w, mod=True)
    _abc         = lambda w: mk(C_EN, label='abc',      width=w, mod=True)
    if has_number_row:
        if ar2:
            func_w = round((100.0 - len(ar2) * KEY_W - KEY_W) / 2, 4)
            r2       = fix_total([_abc(func_w)] + _row(ar2)       + [_colon,     _enter(func_w)])
            r2_shift = fix_total([_abc(func_w)] + _row_shift(ar2) + [_semicolon, _enter(func_w)])
        else:
            func_w = round((100.0 - KEY_W) / 2, 4)
            r2       = fix_total([_abc(func_w), _colon,     _enter(func_w)])
            r2_shift = fix_total([_abc(func_w), _semicolon, _enter(func_w)])
    else:
        # 4-row: build r1 (was-asdf with abc+colon+enter and 、/|) and r2 (was-zxcv with shifts).
        # r1 = abc(func_w) + ar1(N×7) + 、/|(7) + ：/；(7) + return(func_w)
        if ar1:
            func_w_r1 = round((100.0 - len(ar1) * KEY_W - 2 * KEY_W) / 2, 4)
            r1       = fix_total([_abc(func_w_r1)] + _row(ar1)       + [_br_pipe,       _colon,     _enter(func_w_r1)])
            r1_shift = fix_total([_abc(func_w_r1)] + _row_shift(ar1) + [_br_pipe_shift, _semicolon, _enter(func_w_r1)])
        else:
            func_w_r1 = round((100.0 - 2 * KEY_W) / 2, 4)
            r1       = fix_total([_abc(func_w_r1), _br_pipe,       _colon,     _enter(func_w_r1)])
            r1_shift = fix_total([_abc(func_w_r1), _br_pipe_shift, _semicolon, _enter(func_w_r1)])
        # r2 = L-shift + (ar2 + bottom_alpha) + R-shift  — handled in shift block below using shift_alpha_4row
        r2 = None
        r2_shift = None

    # ---- Shift row (zxcv, 2nd from bottom): shift + content + shift ----
    # 5-row uses shift_alpha = ar3 + bottom_alpha. 4-row uses ar2 + bottom_alpha (no ar3).
    shift_row_alpha = shift_alpha if has_number_row else (ar2 + bottom_alpha)
    if shift_row_alpha:
        each_shift = round((100.0 - len(shift_row_alpha) * KEY_W) / 2, 4)
        rS = fix_total(
            [mk(C_SHIFT, icon='shift',      width=each_shift, mod=True, sticky=True)]
            + _row(shift_row_alpha)
            + [mk(C_SHIFT, icon='shift',    width=each_shift, mod=True, sticky=True)])
        rS_shift = fix_total(
            [mk(C_SHIFT, icon='shift.fill', width=each_shift, mod=True, sticky=True)]
            + _row_shift(shift_row_alpha)
            + [mk(C_SHIFT, icon='shift.fill', width=each_shift, mod=True, sticky=True)])
    else:
        each_shift = round((100.0 - KEY_W) / 2, 4)
        rS = rS_shift = fix_total([
            mk(C_SHIFT, icon='shift', width=each_shift, mod=True, sticky=True),
            spacer(KEY_W),
            mk(C_SHIFT, icon='shift', width=each_shift, mod=True, sticky=True),
        ])

    if has_number_row:
        r3, r3_shift = rS, rS_shift
    else:
        # 4-row: shift row becomes r2.
        r2, r2_shift = rS, rS_shift

    # ---- Row 4 (bottom): CJK bottom row with ，/。right of space ----
    r4       = bottom_row(cjk=True)
    r4_shift = bottom_row(cjk=True, shifted=True)

    ipad_id = lid + '_ipad'
    if has_number_row:
        write_layout(ipad_id, [
            (r0,       False), (r1,       False), (r2,       False), (r3,       False), (r4,       True),
        ])
        write_layout(ipad_id + '_shift', [
            (r0_shift, False), (r1_shift, False), (r2_shift, False), (r3_shift, False), (r4_shift, True),
        ])
    else:
        write_layout(ipad_id, [
            (r0,       False), (r1,       False), (r2,       False), (r4,       True),
        ])
        write_layout(ipad_id + '_shift', [
            (r0_shift, False), (r1_shift, False), (r2_shift, False), (r4_shift, True),
        ])



# ---------------------------------------------------------------------------
# Special: lime_phonetic
# ---------------------------------------------------------------------------

def make_phonetic():
    phone = load_phone('lime_phonetic')
    # Phone: row0=digits(1-0 with BPMF sublabels), row1=qwerty BPMF, row2=asdf BPMF,
    #        row3=zxcv BPMF, row4=bottom
    a_r2 = extract_alpha(phone, 1)   # qwerty row: q→ㄆ etc.
    a_r3 = extract_alpha(phone, 2)   # asdf row: a→ㄇ etc.
    a_r4 = extract_alpha(phone, 3)   # zxcv row: z→ㄈ etc.

    # Some phone JSON files have visually similar CJK characters substituted
    # for the correct Bopomofo Unicode block (U+3100–U+312F).
    # Correction map: wrong char → correct Bopomofo char
    _BPMF_FIX = {
        '一': 'ㄧ',  # 一(U+4E00) → ㄧ(U+3127) — u key
        '丨': 'ㄨ',  # 丨(U+4E28) → ㄨ(U+3128) — j key (if present)
        '丿': 'ㄩ',  # 丿(U+4E3F) → ㄩ(U+3129) — m key (if present)
    }

    def fix_bpmf(ch):
        return _BPMF_FIX.get(ch, ch)

    # Preserve original label=letter, sublabel=BPMF from phone layout.
    # Fix any wrong-unicode BPMF chars in the sublabel.
    def fixed_phonetic_alpha(tuples, width, target=10):
        keys = []
        for c, lbl, sub in tuples:
            keys.append(mk(c, label=lbl, sublabel=fix_bpmf(sub), width=width))
        while len(keys) < target:
            keys.append(spacer(width))
        return keys

    # Unified KEY_W=7.0 for all content keys across all rows
    # Row 1: ~.(7) + 10 digits/BPMF(7 each) + -/ㄦ(7) + —…(7) + delete(7) = 98+adj
    KEY_W = 7.0; TAB_W = 9.0
    _DS = {49:33, 50:64, 51:35, 52:36, 53:37, 54:94, 55:38, 56:42, 57:40, 48:41}
    _SC = {33:'!', 64:'@', 35:'#', 36:'$', 37:'%', 94:'^', 38:'&', 42:'*', 40:'(', 41:')'}
    a_r1 = extract_alpha(phone, 0)   # digits 1-0 with BPMF sublabels
    r1 = fix_total([
        mk(126, label='.\\n~', width=KEY_W, lp=46),
        *[mk(c, label=lbl, sublabel=fix_bpmf(sub), width=KEY_W) for c, lbl, sub in a_r1],
        mk(45, label='-', sublabel='ㄦ', width=KEY_W),
        mk(8212, label='…\\n—', width=KEY_W, lp=8230),
        mk(C_DELETE, icon='delete.backward', width=KEY_W, mod=True, rep=True),
    ])
    r1_shift = fix_total([
        mk(126, label='~', width=KEY_W),  # shifted: show only ~ (sliding char)
        *[mk(_DS.get(c, c), label=_SC.get(_DS.get(c, 0), lbl), sublabel=fix_bpmf(sub), width=KEY_W)
          for c, lbl, sub in a_r1],
        mk(45, label='-', sublabel='ㄦ', width=KEY_W),
        mk(8230, label='…', width=KEY_W),  # shifted: show only … (sliding char)
        mk(C_DELETE, icon='delete.backward', width=KEY_W, mod=True, rep=True),
    ])

    # Row 2: tab(9) + 13 content(7.0 each) = 100
    r2 = fix_total([mk(C_TAB, icon='arrow.forward.to.line', width=TAB_W, mod=True)]
                    + fixed_phonetic_alpha(a_r2[:10], KEY_W)
                    + [mk(12300, label='『\\n「', width=KEY_W, lp=12302),
                       mk(12301, label='』\\n」', width=KEY_W, lp=12303),
                       mk(12289, label='|\\n、',  width=KEY_W, lp=124)])

    # Row 3: abc(9) + 10 content(7.0) + ：/；(7) + search(7) = 100
    r3 = fix_total([mk(C_EN, label='abc', width=9.0, mod=True)]
                    + fixed_phonetic_alpha(a_r3[:10], KEY_W)
                    + [mk(0xFF1A, label='；\\n：', width=KEY_W, lp=0xFF1B),
                       mk(C_ENTER, icon='return', width=KEY_W, mod=True)])
    # Row 4: shift(15) + 10 content(7.0) + shift(15) = 100
    r4 = fix_total([mk(C_SHIFT, icon='shift', width=15.0, mod=True, sticky=True)]
                    + fixed_phonetic_alpha(a_r4[:10], KEY_W)
                    + [mk(C_SHIFT, icon='shift', width=15.0, mod=True, sticky=True)])

    write_layout('lime_phonetic_ipad', [
        (r1,                   False),
        (r2,                   False),
        (r3,                   False),
        (r4,                   False),
        (bottom_row(cjk=True), True),
    ])
    write_layout('lime_phonetic_ipad_shift', [
        (r1_shift,             False),
        (r2,                   False),
        (r3,                   False),
        (r4,                   False),
        (bottom_row(cjk=True), True),
    ])


# ---------------------------------------------------------------------------
# Special: lime_english / lime_abc
# ---------------------------------------------------------------------------

def _cap_alpha_tuple(t):
    """Uppercase the label of an (code, label, sublabel) tuple if it's a lowercase letter."""
    c, lbl, sub = t
    return (c, _cap(lbl), sub)


def extract_im_key(phone_data):
    """Extract the IM/EN toggle key (C_EN or C_IM) from the phone bottom row.
    Returns (code, label, sublabel) matching the phone key exactly."""
    if phone_data is None:
        return (C_IM, '中', '')
    rows = phone_data['rows']
    bottom_idx = next((i for i, r in enumerate(rows) if r.get('isBottomRow')), len(rows)-1)
    for k in rows[bottom_idx]['keys']:
        c = k.get('code', 0)
        if c in (C_EN, C_IM):
            lbl = k.get('label', '')
            if lbl == '@string/label_symbol_key':
                lbl = '123'
            return (c, lbl, k.get('sublabel', ''))
    return (C_IM, '中', '')


def make_english():
    phone = load_phone('lime_english')
    # English: row0=qwerty, row1=asdf, row2=zxcv(+shift/delete), row3=bottom
    a_r2 = extract_alpha(phone, 0)   # q w e r t y u i o p
    a_r3 = extract_alpha(phone, 1)   # a s d f g h j k l
    a_r4 = extract_alpha(phone, 2)   # z x c v b n m
    im_code, im_lbl, im_sub = extract_im_key(phone)

    r2 = build_row2(a_r2[:10], bracket_cluster='english')
    r3 = build_row3_english(im_code, im_lbl, a_r3[:9], im_sub)
    r4 = build_row4_english(a_r4[:7])

    # Shift variant rows: uppercase alpha, single-symbol punctuation
    a_r2u = [(_cap_alpha_tuple(t)) for t in a_r2[:10]]
    a_r3u = [(_cap_alpha_tuple(t)) for t in a_r3[:9]]
    a_r4u = [(_cap_alpha_tuple(t)) for t in a_r4[:7]]
    r2s = build_row2(a_r2u, bracket_cluster='english', is_shift=True)
    r3s = build_row3_english(im_code, im_lbl, a_r3u, im_sub, is_shift=True)
    r4s = build_row4_english(a_r4u, is_shift=True)

    write_layout('lime_english_ipad', [
        (row1_english(),       False), (r2,  False), (r3,  False), (r4,  False), (bottom_row(), True),
    ])
    write_layout('lime_english_ipad_shift', [
        (row1_english_shift(), False), (r2s, False), (r3s, False), (r4s, False), (bottom_row(), True),
    ])


def make_abc():
    phone = load_phone('lime_abc')
    # lime_abc phone layout is alphabetical (a-j, k-s, t-z), NOT QWERTY.
    # iPad must show QWERTY regardless — hardcode standard ASCII positions.
    a_r2 = [(113,'q',''), (119,'w',''), (101,'e',''), (114,'r',''), (116,'t',''),
            (121,'y',''), (117,'u',''), (105,'i',''), (111,'o',''), (112,'p','')]
    a_r3 = [(97,'a',''), (115,'s',''), (100,'d',''), (102,'f',''), (103,'g',''),
            (104,'h',''), (106,'j',''), (107,'k',''), (108,'l','')]
    a_r4 = [(122,'z',''), (120,'x',''), (99,'c',''), (118,'v',''), (98,'b',''),
            (110,'n',''), (109,'m','')]
    im_code, im_lbl, im_sub = extract_im_key(phone)

    r2 = build_row2(a_r2[:10], bracket_cluster='english')
    r3 = build_row3_english(im_code, im_lbl, a_r3[:9], im_sub)
    r4 = build_row4_english(a_r4[:7])

    a_r2u = [_cap_alpha_tuple(t) for t in a_r2]
    a_r3u = [_cap_alpha_tuple(t) for t in a_r3]
    a_r4u = [_cap_alpha_tuple(t) for t in a_r4]
    r2s = build_row2(a_r2u, bracket_cluster='english', is_shift=True)
    r3s = build_row3_english(im_code, im_lbl, a_r3u, im_sub, is_shift=True)
    r4s = build_row4_english(a_r4u, is_shift=True)

    write_layout('lime_abc_ipad', [
        (row1_english(),       False), (r2,  False), (r3,  False), (r4,  False), (bottom_row(), True),
    ])
    write_layout('lime_abc_ipad_shift', [
        (row1_english_shift(), False), (r2s, False), (r3s, False), (r4s, False), (bottom_row(), True),
    ])


# ---------------------------------------------------------------------------
# Special: lime_wb (Wubi stroke — very few keys)
# ---------------------------------------------------------------------------

def make_wb():
    phone_base = load_phone('lime_wb')
    # WB has only 3-5 alpha keys; pad rows heavily with spacers
    # Row structure: just use 5-row scaffold with large spacers
    KEY_W = 7.15

    # Extract whatever alpha keys exist across all phone rows
    all_alpha = []
    if phone_base:
        for i in range(len(phone_base['rows'])):
            all_alpha += extract_alpha(phone_base, i)

    # Row 2: tab + up to 10 keys (padded) + english brackets
    r2_alpha = list(all_alpha[:10])
    while len(r2_alpha) < 10:
        r2_alpha.append((0, '', ''))
    r2 = build_row2(r2_alpha, bracket_cluster='english')

    # Rows 3 and 4: mostly spacers
    im_code, im_lbl, im_sub = extract_im_key(phone_base)
    r3 = build_row3_english(im_code, im_lbl, [], im_sub)
    r4 = build_row4_english([])

    for lid in ('lime_wb_ipad', 'lime_wb_ipad_shift'):
        write_layout(lid, [
            (row1_english(), False),
            (r2,             False),
            (r3,             False),
            (r4,             False),
            (bottom_row(),   True),
        ])


# ---------------------------------------------------------------------------
# Special: lime_number / lime_shift / lime_email / lime_url
# ---------------------------------------------------------------------------

def make_number_style(base_name, ipad_id):
    phone = load_phone(base_name)
    is_shift = ipad_id.endswith('_shift')
    if not phone:
        # Create a minimal 5-row layout
        r1 = row1_english_shift() if is_shift else row1_english()
        r2 = build_row2([], bracket_cluster='english')
        r3 = build_row3_english(C_IM, '中', [])
        r4 = build_row4_english([])
    else:
        rows = phone['rows']
        bottom_idx = next((i for i,r in enumerate(rows) if r.get('isBottomRow')), len(rows)-1)
        # Skip digit row if phone layout starts with one (e.g. lime_english_number)
        a0_raw = extract_alpha(phone, 0)
        shift_base = 1 if (bool(a0_raw) and all(48 <= c <= 57 for c, _, _ in a0_raw)) else 0
        a_r2 = extract_alpha(phone, shift_base)
        a_r3 = extract_alpha(phone, shift_base + 1)
        a_r4 = extract_alpha(phone, shift_base + 2)
        im_code, im_lbl, im_sub = extract_im_key(phone)
        if is_shift:
            a_r2 = [_cap_alpha_tuple(t) for t in a_r2]
            a_r3 = [_cap_alpha_tuple(t) for t in a_r3]
            a_r4 = [_cap_alpha_tuple(t) for t in a_r4]
        r1 = row1_english_shift() if is_shift else row1_english()
        r2 = build_row2(a_r2[:10], bracket_cluster='english', is_shift=is_shift)
        r3 = build_row3_english(im_code, im_lbl, a_r3[:9], im_sub, is_shift=is_shift)
        r4 = build_row4_english(a_r4[:7], is_shift=is_shift)

    write_layout(ipad_id, [
        (r1, False), (r2, False), (r3, False), (r4, False),
        (bottom_row(), True),
    ])


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    print(f'Output directory: {LAYOUTS_DIR}')
    print()

    # 1. lime_phonetic
    print('Phonetic:')
    make_phonetic()

    # 2. lime_english + lime_abc
    print('English / ABC:')
    make_english()
    make_abc()

    # 3. CJK IMs — clean rebuild from phone layouts
    print('Array / CJ / Dayi / ET / EZ / HS / HSU / WB:')
    build_cjk_ipad('lime_array',        'lime_array')
    build_cjk_ipad('lime_array_number', 'lime_array_number')
    build_cjk_ipad('lime_cj',           'lime_cj')
    build_cjk_ipad('lime_cj_number',    'lime_cj_number')
    build_cjk_ipad('lime_dayi',         'lime_dayi')
    build_cjk_ipad('lime_dayi_sym',     'lime_dayi_sym')
    build_cjk_ipad('lime_et26',         'lime_et26')
    build_cjk_ipad('lime_et_41',        'lime_et_41')
    build_cjk_ipad('lime_ez',           'lime_ez')
    build_cjk_ipad('lime_hs',           'lime_hs')
    build_cjk_ipad('lime_hsu',          'lime_hsu')
    make_wb()

    # 4. Symbols
    print('Symbols:')
    make_symbols1()
    make_symbols2()
    make_symbols3()

    # 5. Misc
    print('Misc:')
    make_number_style('lime_number',         'lime_number_ipad')
    make_number_style('lime_number',         'lime_number_ipad_shift')
    make_number_style('lime_email',          'lime_email_ipad')
    make_number_style('lime_url',            'lime_url_ipad')
    make_number_style('lime_english_number', 'lime_english_number_ipad')
    make_number_style('lime_english_number', 'lime_english_number_ipad_shift')

    # lime_shift_ipad (standalone shift layout, same as english shift)
    phone = load_phone('lime_shift') or load_phone('lime_english')
    if phone:
        a0 = extract_alpha(phone, 0); a1 = extract_alpha(phone, 1); a2 = extract_alpha(phone, 2)
        r2 = build_row2(a0[:10], bracket_cluster='english')
        im_code, im_lbl, im_sub = extract_im_key(phone)
        r3 = build_row3_english(im_code, im_lbl, a1[:9], im_sub)
        r4 = build_row4_english(a2[:7])
        write_layout('lime_shift_ipad', [
            (row1_english(), False), (r2, False), (r3, False), (r4, False),
            (bottom_row(), True),
        ])

    # Remove stale *_shift_ipad.json files (old naming convention)
    print()
    print('Cleaning up old *_shift_ipad.json files...')
    import glob
    for old in glob.glob(os.path.join(LAYOUTS_DIR, '*_shift_ipad.json')):
        # Keep lime_shift_ipad.json (it's a real layout, not a naming artifact)
        if os.path.basename(old) == 'lime_shift_ipad.json':
            continue
        os.remove(old)
        print(f'  removed {os.path.basename(old)}')

    print()
    print('Done.')


if __name__ == '__main__':
    main()
