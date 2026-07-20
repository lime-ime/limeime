#!/usr/bin/env python3
# test_ipad_language_mode_key.py - regression test for GitHub issue #181.
#
# iPad keyboards expose a language mode key in the bottom row. Its meaning is
# "switch to the other mode", so the key that is shown depends on the mode the
# layout itself represents:
#
#   * A Chinese-mode (or number/shift page reached from Chinese) iPad layout
#     must offer the way *out* to English: code -9 (C_EN), labelled "EN" or
#     "abc".
#   * An English-mode iPad layout (the lime_english*, lime_abc*, lime_email*,
#     and lime_url* families)
#     must offer the way *back* to Chinese: code -10 (C_IM), labelled "中".
#
# Issue #181 reported iPad layouts that got this backwards: several non-English
# layouts shipped a -10/中 key, so their language-mode key could not switch
# directly to English.
#
# Symbol pages are neutral overlays, not Chinese IM layouts. Android parity
# gives them two explicit exits: code -2 labelled EN and code -10 labelled 中.
# They must not be forced through the Chinese-layout -9 invariant.
#
# Usage: python3 scripts/test_ipad_language_mode_key.py

import json
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
LAYOUTS_DIR = REPO_ROOT / "LimeIME-iOS" / "LimeKeyboard" / "Layouts"
ANDROID_LAYOUTS_DIR = REPO_ROOT / "LimeStudio" / "app" / "src" / "main" / "res" / "xml"
ANDROID_NS = "{http://schemas.android.com/apk/res-auto}"

C_EN = -9
C_IM = -10

# Layout families that *are* the English mode, and therefore keep the 中 key so
# the user can return to Chinese.
ENGLISH_MODE_PREFIXES = ("lime_english", "lime_abc", "lime_email", "lime_url")

EN_LABELS = {"EN", "abc"}
IM_LABELS = {"中"}


def ipad_layout_paths():
    return sorted(LAYOUTS_DIR.glob("*_ipad*.json"))


def is_english_mode(stem):
    return stem.startswith(ENGLISH_MODE_PREFIXES)


def is_symbol_page(stem):
    return stem.startswith(("symbols1_", "symbols2_", "symbols3_"))


def language_mode_keys(layout):
    return [
        key
        for row in layout.get("rows", [])
        for key in row.get("keys", [])
        if key.get("code") in (C_EN, C_IM)
    ]


def all_keys(layout):
    return [
        key
        for row in layout.get("rows", [])
        for key in row.get("keys", [])
    ]


class IPadLanguageModeKeyTest(unittest.TestCase):
    def setUp(self):
        self.paths = ipad_layout_paths()
        self.assertTrue(self.paths, "no iPad layouts found to scan")

    def load(self, path):
        return json.loads(path.read_text(encoding="utf-8-sig"))

    def test_every_ipad_layout_has_exactly_one_language_mode_key(self):
        for path in self.paths:
            with self.subTest(layout=path.stem):
                keys = language_mode_keys(self.load(path))
                self.assertEqual(
                    1,
                    len(keys),
                    f"{path.stem} has {len(keys)} language mode keys, expected exactly 1 (#181)",
                )

    def test_non_english_ipad_layouts_switch_to_english(self):
        for path in self.paths:
            if is_english_mode(path.stem) or is_symbol_page(path.stem):
                continue
            with self.subTest(layout=path.stem):
                layout = self.load(path)
                keys = language_mode_keys(layout)
                codes = [k.get("code") for k in keys]
                self.assertNotIn(
                    C_IM,
                    codes,
                    f"{path.stem} is not an English-mode layout but ships a -10/中 "
                    f"language mode key, so it cannot switch directly to English (#181)",
                )
                self.assertNotIn(
                    "中",
                    [
                        key.get("label")
                        for key in all_keys(layout)
                        if key.get("isModifier")
                    ],
                    f"{path.stem} is not an English-mode layout but contains a 中 modifier (#181)",
                )
                self.assertEqual(
                    [C_EN],
                    codes,
                    f"{path.stem} must expose a -9 language mode key (#181)",
                )
                self.assertIn(
                    keys[0].get("label"),
                    EN_LABELS,
                    f"{path.stem} -9 key must be labelled EN or abc, "
                    f"got {keys[0].get('label')!r} (#181)",
                )

    def test_english_mode_ipad_layouts_switch_back_to_chinese(self):
        for path in self.paths:
            if not is_english_mode(path.stem):
                continue
            with self.subTest(layout=path.stem):
                keys = language_mode_keys(self.load(path))
                self.assertEqual(
                    [C_IM],
                    [k.get("code") for k in keys],
                    f"{path.stem} is an English-mode layout and must keep the -10 "
                    f"key so users can return to Chinese (#181)",
                )
                self.assertIn(
                    keys[0].get("label"),
                    IM_LABELS,
                    f"{path.stem} -10 key must be labelled 中, "
                    f"got {keys[0].get('label')!r} (#181)",
                )

    def test_symbol_pages_keep_android_parity_exits(self):
        """Symbol overlays keep separate EN and Chinese exits, as on Android."""
        for path in self.paths:
            if not is_symbol_page(path.stem):
                continue
            with self.subTest(layout=path.stem):
                page = path.stem.split("_")[0]
                android_root = ET.parse(ANDROID_LAYOUTS_DIR / f"{page}.xml").getroot()
                android_exits = sorted(
                    (int(key.attrib[f"{ANDROID_NS}codes"]), key.attrib[f"{ANDROID_NS}keyLabel"])
                    for key in android_root.iter("Key")
                    if key.get(f"{ANDROID_NS}codes") in {"-2", "-10"}
                )
                self.assertEqual(
                    [(-10, "中"), (-2, "EN")],
                    android_exits,
                    f"Android {page}.xml symbol exit contract changed",
                )
                keys = all_keys(self.load(path))
                ios_exits = sorted(
                    (key.get("code"), key.get("label"))
                    for key in keys
                    if key.get("code") in {-2, -10}
                )
                self.assertEqual(android_exits, ios_exits,
                                 f"{path.stem} must match Android symbol exits (#181)")
                self.assertNotIn(
                    -9,
                    [key.get("code") for key in keys],
                    f"{path.stem} is a symbol overlay, not a Chinese IM layout (#181)",
                )


if __name__ == "__main__":
    unittest.main()
