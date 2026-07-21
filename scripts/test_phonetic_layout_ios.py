#!/usr/bin/env python3
"""Source-level regression checks for iOS phonetic keyboard layout routing."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
CONTROLLER = ROOT / "LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift"


class PhoneticLayoutRoutingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = CONTROLLER.read_text(encoding="utf-8")
        match = re.search(
            r"static func phoneticSpecialLayoutId\(for kbType: String\) -> String\? \{"
            r"(?P<body>.*?)\n    \}",
            cls.source,
            re.S,
        )
        if match is None:
            raise AssertionError("phoneticSpecialLayoutId helper is missing")
        cls.helper = match.group("body")
        visible_match = re.search(
            r"static func phoneticVisibleLayoutId\(for kbType: String, persistedLayoutId: String\) -> String \{"
            r"(?P<body>.*?)\n    \}",
            cls.source,
            re.S,
        )
        if visible_match is None:
            raise AssertionError("phoneticVisibleLayoutId helper is missing")
        cls.visible_helper = visible_match.group("body")

    def test_english_and_symbol_variants_do_not_share_symbol_layout(self) -> None:
        self.assertNotIn(
            'kbType.hasPrefix("hsu")',
            self.helper,
            "許氏（英文） must fall through to its persisted lime/limenum keyboard instead of lime_hsu",
        )
        self.assertNotIn(
            'kbType.hasPrefix("eten26")',
            self.helper,
            "倚天 26 鍵（英文） must fall through to its persisted lime/limenum keyboard instead of lime_et26",
        )
        self.assertRegex(self.helper, r'kbType == "hsu_symbol".*return "lime_hsu"')
        self.assertRegex(self.helper, r'kbType == "eten26_symbol".*return "lime_et26"')
        self.assertIn("phoneticSpecialLayoutId(for: kbType) ?? persistedLayoutId", self.visible_helper)
        self.assertRegex(
            self.source,
            r"let persistedLayoutId = resolvedPersistedLayoutId\(for: tableNick\)[\s\S]*?"
            r"phoneticVisibleLayoutId\(\s*for: phoneticKeyboardType,\s*"
            r"persistedLayoutId: persistedLayoutId\)",
        )


if __name__ == "__main__":
    unittest.main()
