#!/usr/bin/env python3
"""Regression coverage for GitHub issue #228 (iPad Dayi `;`/`虫` root)."""

import importlib.util
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAYOUTS = ROOT / "LimeIME-iOS" / "LimeKeyboard" / "Layouts"
BUILDER = ROOT / "scripts" / "build_ipad_layouts.py"
TRIMMER = ROOT / "scripts" / "trim_ipad_layout.py"


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise ImportError(f"cannot load module from {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def load_layout(layout_id):
    return json.loads((LAYOUTS / f"{layout_id}.json").read_text(encoding="utf-8-sig"))


def keys(layout):
    return [key for row in layout["rows"] for key in row["keys"]]


class DayiIPadSemicolonContractTests(unittest.TestCase):
    def test_generated_full_and_narrow_layouts_are_current(self):
        builder = load_module("build_ipad_layouts_228", BUILDER)
        trimmer = load_module("trim_ipad_layout_228", TRIMMER)
        expected_full = builder.make_ipad_layout(load_layout("lime_dayi"), "lime_dayi")
        expected_narrow, _ = trimmer.trim_layout(expected_full)
        self.assertEqual(load_layout("lime_dayi_ipad"), expected_full)
        self.assertEqual(load_layout("lime_dayi_ipad_narrow"), expected_narrow)

    def test_unshifted_ipad_variants_keep_ascii_semicolon_root_without_dual_slide(self):
        for layout_id in ("lime_dayi_ipad", "lime_dayi_ipad_narrow"):
            with self.subTest(layout=layout_id):
                layout_keys = keys(load_layout(layout_id))
                semicolons = [key for key in layout_keys if key["code"] == 59]
                self.assertEqual(len(semicolons), 1)
                self.assertEqual(semicolons[0]["label"], ";")
                self.assertEqual(semicolons[0].get("longPressCode", 0), 0)
                self.assertNotIn("\\n", semicolons[0]["label"])
                self.assertFalse(any(key["code"] in (65306, 65307) for key in layout_keys))


if __name__ == "__main__":
    unittest.main()
