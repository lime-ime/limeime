#!/usr/bin/env python3
# test_ipad_language_mode_key.py - regression test for GitHub issue #181.
#
# iPad keyboards expose a language mode key in the bottom row. Its meaning is
# "switch to the other mode", so the key that is shown depends on the mode the
# layout itself represents:
#
#   * A Chinese-mode (or symbol/number page reached from Chinese) iPad layout
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
# Note the distinction from the symbol-page key: symbol layouts also carry a
# code -2 (C_SYM) key that is *labelled* "abc" and returns to the letter page.
# That key is not a language mode key and must be preserved; this test only
# looks at codes -9 / -10.
#
# Usage: python3 scripts/test_ipad_language_mode_key.py

import json
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
LAYOUTS_DIR = REPO_ROOT / "LimeIME-iOS" / "LimeKeyboard" / "Layouts"

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
            if is_english_mode(path.stem):
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

    def test_symbol_page_abc_keys_are_preserved(self):
        """The code -2 abc key returns to letters and is not a language key."""
        for stem in (
            "symbols1_ipad",
            "symbols1_ipad_narrow",
            "symbols2_ipad",
            "symbols2_ipad_narrow",
            "symbols3_ipad",
            "symbols3_ipad_narrow",
        ):
            path = LAYOUTS_DIR / f"{stem}.json"
            with self.subTest(layout=stem):
                self.assertTrue(path.exists(), f"{stem}.json missing")
                labels = [
                    key.get("label")
                    for row in self.load(path).get("rows", [])
                    for key in row.get("keys", [])
                    if key.get("code") == -2
                ]
                self.assertIn(
                    "abc",
                    labels,
                    f"{stem} lost its code -2 symbol-page abc key (#181)",
                )


if __name__ == "__main__":
    unittest.main()
