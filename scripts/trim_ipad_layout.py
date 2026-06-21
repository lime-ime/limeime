#!/usr/bin/env python3
# trim_ipad_layout.py
# Generate *_ipad_narrow*.json files from full *_ipad*.json layouts.
# Usage: python3 scripts/trim_ipad_layout.py

import copy
import json
from pathlib import Path


LAYOUTS_DIR = Path("LimeIME-iOS/LimeKeyboard/Layouts")
SYMBOL_LAYOUTS = {"symbols1", "symbols2", "symbols3"}

DROP_QUOTA_NARROW = {
    "digit_left": 1,
    "digit_right": 2,
    "qwerty": 3,
    "asdf": 2,
    "zxcv": 1,
}

NARROW_NORMAL_KEY_WIDTH = 8.0
MIN_NARROW_FUNCTION_KEY_WIDTH = 8.0
NARROW_GLOBE_WIDTH = 9.0
NARROW_WIDTH_BY_BASE = {
    "lime_ez": 7.5,
    "lime_hs": 7.5,
}
ROOT_HEAVY_BASES = {"lime_ez", "lime_hs"}
ENGLISH_BASES = {"lime_english", "lime_abc", "lime_email", "lime_url", "lime_english_number", "lime_number", "lime_shift"}

IM_ROOTS = {
    "lime_phonetic": "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/-",
    "lime_cj": "qwertyuiopasdfghjklzxcvbnm",
    "lime_cj_number": "qwertyuiopasdfghjklzxcvbnm",
    "lime_dayi": "1234567890qwertyuiopasdfghjkl;zxcvbnm,./",
    "lime_dayi_sym": "1234567890qwertyuiopasdfghjkl;zxcvbnm,./",
    "lime_array": "qazwsxedcrfvtgbyhnujmik,ol.p;/",
    "lime_array_number": "qazwsxedcrfvtgbyhnujmik,ol.p;/",
    "lime_et26": "qazwsxedcrfvtgbyhnujmikolp,.",
    "lime_et_41": "abcdefghijklmnopqrstuvwxyz12347890-=;',./",
    "lime_hsu": "azwsxedcrfvtgbyhnujmikolpq,.",
    "lime_wb": ",./mn",
    "lime_ez": "',-./0123456789;=[\\]abcdefghijklmnopqrstuvwxyz",
    "lime_hs": "',-./0123456789;=[\\]abcdefghijklmnopqrstuvwxyz",
    "lime_english": "",
    "lime_abc": "",
    "lime_email": "",
    "lime_url": "",
    "lime_english_number": "",
    "lime_number": "",
    "lime_shift": "",
}

BOTTOM_NARROW_ZH = [
    {"code": -200, "label": "globe", "sublabel": "", "widthPercent": 9.0, "icon": "globe", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": -100},
    {"code": -2, "label": ".?123", "sublabel": "", "widthPercent": 11.0, "icon": "", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
    {"code": -201, "label": "", "sublabel": "", "widthPercent": 8.0, "icon": "face.smiling", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
    {"code": 32, "label": "", "sublabel": "", "widthPercent": 53.0, "icon": "space.bar", "isModifier": False, "isRepeatable": True, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
    {"code": -9, "label": "abc", "sublabel": "", "widthPercent": 11.0, "icon": "", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
    {"code": -3, "label": "", "sublabel": "", "widthPercent": 8.0, "icon": "keyboard.chevron.compact.down", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": -100},
]


SYMBOL_BOTTOM_NARROW = [
    {"code": -200, "label": "globe", "sublabel": "", "widthPercent": 9.0, "icon": "globe", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": -100},
    {"code": -2, "label": "abc", "sublabel": "", "widthPercent": 11.0, "icon": "", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
    {"code": -201, "label": "", "sublabel": "", "widthPercent": 8.0, "icon": "face.smiling", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
    {"code": 32, "label": "", "sublabel": "", "widthPercent": 53.0, "icon": "space.bar", "isModifier": False, "isRepeatable": True, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
    {"code": -10, "label": "中", "sublabel": "", "widthPercent": 11.0, "icon": "", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
    {"code": -3, "label": "", "sublabel": "", "widthPercent": 8.0, "icon": "keyboard.chevron.compact.down", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": -100},
]


def base_id(layout_id):
    result = layout_id
    if result.endswith("_shift"):
        result = result[:-6]
    if result.endswith("_ipad_narrow"):
        result = result[:-12]
    elif result.endswith("_ipad"):
        result = result[:-5]
    return result


def roots_for(layout_id):
    return set(IM_ROOTS.get(base_id(layout_id), "").lower())


def spacer_from(key):
    result = copy.deepcopy(key)
    result.update({
        "code": 0,
        "codes": [0],
        "label": "",
        "sublabel": "",
        "icon": "",
        "isModifier": False,
        "isRepeatable": False,
        "isSticky": False,
        "popupKeyboard": "",
        "popupCharacters": "",
        "longPressCode": 0,
    })
    return result


def edge_spacer(width):
    key = spacer_from({"code": 0, "codes": [0], "widthPercent": width})
    key["widthPercent"] = round(width, 4)
    return key


def fullwidth_punctuation_key(width):
    return {
        "code": 65292,
        "codes": [65292],
        "label": "。\\n，",
        "sublabel": "",
        "widthPercent": width,
        "icon": "",
        "isModifier": False,
        "isRepeatable": False,
        "isSticky": False,
        "popupKeyboard": "",
        "popupCharacters": "",
        "longPressCode": 0,
    }


def is_visible(key):
    return key.get("code", 0) != 0 or key.get("label") or key.get("icon")


def visible_count(row):
    return sum(1 for key in row["keys"] if is_visible(key))


def compact_row(row):
    row["keys"] = [key for key in row["keys"] if is_visible(key)]


def bottom_narrow_for(layout_id):
    result = copy.deepcopy(BOTTOM_NARROW_ZH)
    if base_id(layout_id) in ENGLISH_BASES:
        result[4].update({"code": -10, "label": "中"})
    return result


def drop_leading_code(row, codes):
    if row["keys"] and row["keys"][0].get("code") in codes:
        row["keys"][0] = spacer_from(row["keys"][0])


def drop_trailing_code(row, codes):
    if row["keys"] and row["keys"][-1].get("code") in codes:
        row["keys"][-1] = spacer_from(row["keys"][-1])


def is_normal_key(key):
    return key.get("code", 0) > 0 and key.get("label", "") and not key.get("icon", "")


def is_function_key(key):
    return is_visible(key) and not is_normal_key(key)


def narrow_width_for(layout_id):
    return NARROW_WIDTH_BY_BASE.get(base_id(layout_id), NARROW_NORMAL_KEY_WIDTH)


def widen_normal_keys(row, key_width):
    normal_indexes = [i for i, key in enumerate(row["keys"]) if is_normal_key(key)]
    if not normal_indexes:
        return

    function_indexes = [i for i, key in enumerate(row["keys"]) if is_function_key(key)]
    for idx in normal_indexes:
        row["keys"][idx]["widthPercent"] = key_width
    for idx in function_indexes:
        row["keys"][idx]["widthPercent"] = key_width


def is_trimmable(key, root_set):
    code = key.get("code", 0)
    if code <= 0:
        return False
    try:
        ch = chr(code).lower()
    except ValueError:
        return False
    return (
        ch not in root_set
        and "\\n" in key.get("label", "")
        and not key.get("popupKeyboard", "")
    )


def row_class(row):
    if row.get("isBottomRow"):
        return "bottom"
    codes = [key.get("code") for key in row.get("keys", [])]
    code_set = set(codes)
    if {48, 49}.issubset(code_set) or {33, 41}.issubset(code_set):
        return "digit"
    if 112 in code_set or 80 in code_set:
        return "qwerty"
    if 10 in code_set:
        return "asdf"
    if 122 in code_set or 90 in code_set:
        return "zxcv"
    return "other"


def trim_right_tail(row, class_name, root_set):
    quota = DROP_QUOTA_NARROW[class_name]
    keys = row["keys"]
    stop_codes = {
        "qwerty": {-5},
        "asdf": {10},
        "zxcv": {-1},
    }[class_name]
    end = len(keys) - 1
    while end >= 0 and keys[end].get("code") in stop_codes:
        end -= 1
    for idx in range(end, -1, -1):
        if quota == 0:
            return
        if not (is_trimmable(keys[idx], root_set) or can_trim_symbol_edge(keys[idx], root_set)):
            return
        keys[idx] = spacer_from(keys[idx])
        quota -= 1


def trim_tail_to_visible(row, target, root_set, stop_codes=(), allow_root_trim=False):
    while visible_count(row) > target:
        keys = row["keys"]
        end = len(keys) - 1
        while end >= 0 and keys[end].get("code") in stop_codes:
            end -= 1

        if end < 0:
            return
        if is_trimmable(keys[end], root_set) or can_trim_symbol_edge(keys[end], root_set):
            keys[end] = spacer_from(keys[end])
            continue
        if allow_root_trim and is_normal_key(keys[end]):
            keys[end] = spacer_from(keys[end])
            continue
        return


def can_trim_symbol_edge(key, root_set):
    code = key.get("code", 0)
    if code <= 0 or key.get("popupKeyboard", ""):
        return False
    try:
        return chr(code).lower() not in root_set
    except ValueError:
        return False


def trim_digit(row, root_set):
    keys = row["keys"]
    digit_indexes = [i for i, key in enumerate(keys) if 48 <= key.get("code", -1) <= 57]
    symbol_row_codes = {33, 64, 35, 36, 37, 94, 38, 42, 40, 41}
    symbol_indexes = [i for i, key in enumerate(keys) if key.get("code") in symbol_row_codes]
    zone_indexes = digit_indexes or symbol_indexes
    if not zone_indexes:
        return
    left_quota = DROP_QUOTA_NARROW["digit_left"]
    right_quota = 1
    first, last = min(zone_indexes), max(zone_indexes)

    for idx in range(0, first):
        if left_quota == 0:
            break
        if not (is_trimmable(keys[idx], root_set) or can_trim_symbol_edge(keys[idx], root_set)):
            break
        keys[idx] = spacer_from(keys[idx])
        left_quota -= 1

    end = len(keys) - 1
    while end > last and keys[end].get("code") <= 0:
        end -= 1
    for idx in range(end, last, -1):
        if right_quota == 0:
            break
        if not (is_trimmable(keys[idx], root_set) or can_trim_symbol_edge(keys[idx], root_set)):
            break
        keys[idx] = spacer_from(keys[idx])
        right_quota -= 1


def enforce_visible_cap(row, class_name):
    return


def apply_row_policy(row, class_name, layout_id, root_set):
    base = base_id(layout_id)
    root_heavy = base in ROOT_HEAVY_BASES
    if class_name == "qwerty":
        drop_leading_code(row, {9})
        trim_tail_to_visible(row, 13 if root_heavy else 12, root_set, stop_codes={-5})
    elif class_name == "asdf":
        drop_leading_code(row, {-9, -10})
        trim_tail_to_visible(row, 12 if base == "lime_hs" else 11, root_set, stop_codes={10})
    elif class_name == "zxcv":
        drop_trailing_code(row, {-1})
        trim_tail_to_visible(row, 11, root_set)
    elif class_name == "digit":
        trim_tail_to_visible(row, 13 if root_heavy else 12, root_set, stop_codes={-5}, allow_root_trim=True)


def apply_widths(row, class_name, key_width):
    if row.get("isBottomRow"):
        return
    for key in row["keys"]:
        if is_visible(key):
            key["widthPercent"] = key_width

    if class_name == "asdf":
        row["keys"].insert(0, edge_spacer(round(NARROW_GLOBE_WIDTH / 2.0, 4)))
    elif class_name == "zxcv" and row["keys"] and row["keys"][0].get("code") == -1:
        row["keys"][0]["widthPercent"] = NARROW_GLOBE_WIDTH

    visible_total = sum(float(key["widthPercent"]) for key in row["keys"])
    missing = round(100.0 - visible_total, 4)
    if missing <= 0:
        return
    if class_name == "digit" and row["keys"][-1].get("code") == -5:
        row["keys"][-1]["widthPercent"] = round(float(row["keys"][-1]["widthPercent"]) + missing, 4)
    elif class_name == "asdf" and row["keys"][-1].get("code") == 10:
        row["keys"][-1]["widthPercent"] = round(float(row["keys"][-1]["widthPercent"]) + missing, 4)
    elif class_name == "zxcv" and row["keys"][0].get("code") == -1:
        if row["keys"][-1].get("code") == -1:
            row["keys"][-1]["widthPercent"] = round(float(row["keys"][-1]["widthPercent"]) + missing, 4)
        else:
            row["keys"].append(edge_spacer(missing))


def trim_layout(layout):
    result = copy.deepcopy(layout)
    if base_id(result["id"]) == "lime_wb":
        result["id"] = result["id"].replace("_ipad", "_ipad_narrow", 1)
        return result, True

    root_set = roots_for(result["id"])
    changed = False
    zxcv_extra_key = None

    for row in result.get("rows", []):
        before = copy.deepcopy(row)
        cls = row_class(row)
        right_shift_key = None
        if cls == "zxcv":
            visible_keys = [key for key in row["keys"] if is_visible(key)]
            if visible_keys and visible_keys[-1].get("code") == -1:
                right_shift_key = copy.deepcopy(visible_keys[-1])
        if cls == "asdf":
            visible_keys = [key for key in row["keys"] if is_visible(key)]
            if len(visible_keys) >= 2 and visible_keys[-1].get("code") == 10:
                candidate = visible_keys[-2]
                if candidate.get("code") == 65292:
                    zxcv_extra_key = copy.deepcopy(candidate)
        if cls == "bottom":
            row["keys"] = bottom_narrow_for(result["id"])
        elif cls == "digit":
            trim_digit(row, root_set)
        if cls in ("digit", "qwerty", "asdf", "zxcv"):
            apply_row_policy(row, cls, result["id"], root_set)
            if cls == "zxcv" and zxcv_extra_key is not None and base_id(result["id"]) != "lime_hs":
                row["keys"].append(zxcv_extra_key)
            if cls == "zxcv" and base_id(result["id"]) == "lime_ez":
                row["keys"].append(fullwidth_punctuation_key(narrow_width_for(result["id"])))
            if cls == "zxcv" and base_id(result["id"]) in ROOT_HEAVY_BASES and right_shift_key is not None:
                row["keys"].append(right_shift_key)
        if cls != "bottom":
            compact_row(row)
        changed = changed or row != before

    key_width = narrow_width_for(result["id"])
    for row in result.get("rows", []):
        cls = row_class(row)
        if cls in ("digit", "qwerty", "asdf", "zxcv"):
            before = copy.deepcopy(row)
            apply_widths(row, cls, key_width)
            changed = changed or row != before

    result["id"] = result["id"].replace("_ipad", "_ipad_narrow", 1)
    return result, changed


def main():
    written = 0
    for symbol_id in sorted(SYMBOL_LAYOUTS):
        path = LAYOUTS_DIR / f"{symbol_id}.json"
        if not path.exists():
            continue
        copied = json.loads(path.read_text(encoding="utf-8-sig"))
        copied["id"] = f"{symbol_id}_ipad_narrow"
        if copied.get("rows"):
            copied["rows"][-1]["keys"] = copy.deepcopy(SYMBOL_BOTTOM_NARROW)
        out_path = LAYOUTS_DIR / f"{symbol_id}_ipad_narrow.json"
        out_path.write_text(
            json.dumps(copied, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8-sig",
        )
        written += 1

    for path in sorted(LAYOUTS_DIR.glob("*_ipad*.json")):
        source_base = base_id(path.stem)
        if "_ipad_narrow" in path.stem or source_base in SYMBOL_LAYOUTS:
            continue
        source = json.loads(path.read_text(encoding="utf-8-sig"))
        trimmed, changed = trim_layout(source)
        out_path = path.with_name(trimmed["id"] + ".json")
        out_path.write_text(
            json.dumps(trimmed, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8-sig",
        )
        written += 1
    print(f"wrote {written} narrow iPad layouts")


if __name__ == "__main__":
    main()
