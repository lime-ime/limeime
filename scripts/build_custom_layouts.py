#!/usr/bin/env python3
"""build_custom_layouts.py - generate the `lime_custom*` layout family (issue #177).

The imported-CIN `custom` IM composes Chinese, but was registered against the
English runtime `lime_abc` layout. See docs/#177_ISSUE.md for the investigation:

  * `lime_abc`'s mode key is `switchToIM` (`-10`, label `中`), so a user
    composing with a custom table had no route to the English keyboard.
  * there was no dedicated, loadable `lime_custom` fallback for custom
    composition when stored layout resolution missed.

`custom` therefore needs its own layout family. The root keys must stay on the
alphabetic grid (a custom CIN table keys off a-z), so each variant is an exact
copy of the matching `lime_abc` variant with one substitution: the `switchToIM`
mode key becomes the Chinese-layout English switch (`switchToEnglish`, `-9`).

Label convention is taken from the Cangjie family, which is the reference
Chinese-composition layout:
  * phone variants  (lime_cj.json)      -> "EN"
  * iPad  variants  (lime_cj_ipad.json) -> "abc"

Everything else - row shape, key widths, popups, icons - is inherited verbatim,
so the custom keyboard is geometrically identical to the alphabetic keyboard
users already know.

Regenerating is idempotent; `scripts/test_custom_layout_ios.py` verifies the
committed output against this contract.

Usage: python3 scripts/build_custom_layouts.py
"""

import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
LAYOUTS_DIR = REPO_ROOT / "LimeIME-iOS" / "LimeKeyboard" / "Layouts"

SWITCH_TO_ENGLISH = -9   # LimeKeyCode.switchToEnglish
SWITCH_TO_IM = -10       # LimeKeyCode.switchToIM

# (custom id, source lime_abc id, English-switch label)
VARIANTS = (
    ("lime_custom", "lime_abc", "EN"),
    ("lime_custom_shift", "lime_abc_shift", "EN"),
    ("lime_custom_ipad", "lime_abc_ipad", "abc"),
    ("lime_custom_ipad_shift", "lime_abc_ipad_shift", "abc"),
    ("lime_custom_ipad_narrow", "lime_abc_ipad_narrow", "abc"),
    ("lime_custom_ipad_narrow_shift", "lime_abc_ipad_narrow_shift", "abc"),
)


def build(custom_id, source_id, label):
    source_path = LAYOUTS_DIR / f"{source_id}.json"
    layout = json.loads(source_path.read_text(encoding="utf-8-sig"))
    layout["id"] = custom_id

    swapped = 0
    for row in layout["rows"]:
        for key in row["keys"]:
            if key["code"] == SWITCH_TO_IM:
                # Keep slot, width, icon and flags; only the action and label change.
                key["code"] = SWITCH_TO_ENGLISH
                key["label"] = label
                swapped += 1

    if swapped != 1:
        raise SystemExit(
            f"{source_id}: expected exactly one switchToIM mode key, found {swapped}. "
            "The lime_abc family changed shape — review before regenerating."
        )

    text = json.dumps(layout, indent=2, ensure_ascii=False) + "\n"
    (LAYOUTS_DIR / f"{custom_id}.json").write_text(text, encoding="utf-8-sig")
    return custom_id


def main():
    for custom_id, source_id, label in VARIANTS:
        print(f"wrote {build(custom_id, source_id, label)}.json  (from {source_id}, mode key -> {label})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
