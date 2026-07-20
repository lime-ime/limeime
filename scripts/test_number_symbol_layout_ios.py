#!/usr/bin/env python3
# test_number_symbol_layout_ios.py - regression test for GitHub issue #160.
#
# The shared LIME keyboard catalog (Android res/raw/lime.db, `keyboard` table)
# maps the `limenumsym` option (desc "LIME+數字符號鍵盤") to:
#     imkb      = lime_number_symbol
#     imshiftkb = lime_number_symbol_shift
# Android ships res/xml/lime_number_symbol{,_shift}.xml for these. iOS resolves
# the same imkb via LayoutLoader.load("lime_number_symbol"), which needs a JSON
# layout of the same id bundled in the keyboard extension. When that JSON is
# missing, LayoutLoader.load returns nil and the controller falls back to the
# ordinary number-row QWERTY layout — the exact symptom reported in #160.
#
# This test guards the iOS layout/resource contract on Linux (no Xcode needed):
#   1. the JSON layouts exist and parse,
#   2. they are a faithful conversion of the Android XML source (parity),
#   3. they are registered in LimeIME.xcodeproj so they are actually bundled.
#
# Usage: python3 scripts/test_number_symbol_layout_ios.py

import importlib.util
import json
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
CONVERTER = REPO_ROOT / "scripts" / "convert_keyboard_layouts.py"
IPAD_BUILDER = REPO_ROOT / "scripts" / "build_ipad_layouts.py"
IPAD_TRIMMER = REPO_ROOT / "scripts" / "trim_ipad_layout.py"
ANDROID_XML_DIR = REPO_ROOT / "LimeStudio" / "app" / "src" / "main" / "res" / "xml"
IOS_LAYOUTS_DIR = REPO_ROOT / "LimeIME-iOS" / "LimeKeyboard" / "Layouts"
PBXPROJ = REPO_ROOT / "LimeIME-iOS" / "LimeIME.xcodeproj" / "project.pbxproj"

# The two layout ids the `limenumsym` catalog row points at (imkb / imshiftkb).
PHONE_LAYOUT_IDS = ("lime_number_symbol", "lime_number_symbol_shift")
IPAD_LAYOUT_IDS = (
    "lime_number_symbol_ipad",
    "lime_number_symbol_ipad_shift",
    "lime_number_symbol_ipad_narrow",
    "lime_number_symbol_ipad_narrow_shift",
)
ALL_LAYOUT_IDS = PHONE_LAYOUT_IDS + IPAD_LAYOUT_IDS


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


# --- Semantic layout contract (independent of the generators) -------------
#
# The generator-parity tests above only prove the committed JSON equals what
# the current scripts emit; they cannot notice when the scripts themselves
# drop a key. These assertions instead compare each layout against the STABLE
# Android XML contract for `limenumsym`, so a lossy generator fails here.
#
# Punctuation the phone contract guarantees, taken straight from the bottom
# rows of res/xml/lime_number_symbol{,_shift}.xml:
#     normal (lime_number_symbol.xml):        codes 45 ('-') and 61 ('=')
#     shift  (lime_number_symbol_shift.xml):  codes 95 ('_') and 43 ('+')
# Every iOS-derived variant (phone JSON, full iPad, narrow iPad) must keep
# these characters typeable — as a primary key code or as a dual-slide
# long-press output — for both the normal and Shift layers. Losing any of
# them is the #160 follow-up regression.
REQUIRED_PUNCT_CODES = {
    "normal": {45, 61},
    "shift": {95, 43},
}

# layer -> (phone id, full iPad id, narrow iPad id)
LAYER_VARIANTS = {
    "normal": (
        "lime_number_symbol",
        "lime_number_symbol_ipad",
        "lime_number_symbol_ipad_narrow",
    ),
    "shift": (
        "lime_number_symbol_shift",
        "lime_number_symbol_ipad_shift",
        "lime_number_symbol_ipad_narrow_shift",
    ),
}


def reachable_codes(layout):
    """Codes a user can actually type in a layout: every primary key `code`
    plus every non-zero `longPressCode` (the slide/long-press output of a
    dual-label key such as '_\\n-')."""
    codes = set()
    for row in layout.get("rows", []):
        for key in row.get("keys", []):
            codes.add(key.get("code"))
            lp = key.get("longPressCode", 0)
            if lp:
                codes.add(lp)
    return codes


def xml_code_label_contract(xml_path, required_codes):
    """Derive required (code, label) pairs directly from Android XML."""
    namespace = "{http://schemas.android.com/apk/res-auto}"
    pairs = {}
    for key in ET.parse(xml_path).getroot().iter("Key"):
        code_text = key.get(f"{namespace}codes")
        if code_text is None:
            continue
        code = int(code_text)
        if code in required_codes:
            label = key.get(f"{namespace}keyLabel")
            if label is None:
                raise AssertionError(
                    f"{xml_path.name} code {code} has no source keyLabel"
                )
            if code in pairs:
                raise AssertionError(
                    f"{xml_path.name} declares code {code} more than once"
                )
            pairs[code] = label
    return pairs


class NumberSymbolLayoutIOSContractTests(unittest.TestCase):
    def test_ios_layout_json_files_exist(self):
        for layout_id in PHONE_LAYOUT_IDS:
            json_path = IOS_LAYOUTS_DIR / f"{layout_id}.json"
            self.assertTrue(
                json_path.exists(),
                f"iOS layout JSON missing for '{layout_id}' (#160): {json_path} — "
                f"limenumsym cannot render; iOS falls back to QWERTY.",
            )
            # Must parse and carry the layout id + non-empty rows.
            # Committed layouts are stored UTF-8 with a BOM (utf-8-sig), matching
            # every other Layouts/*.json; Swift's JSONDecoder tolerates the BOM.
            data = json.loads(json_path.read_text(encoding="utf-8-sig"))
            self.assertEqual(data.get("id"), layout_id)
            self.assertTrue(data.get("rows"), f"{layout_id}.json has no rows")

    def test_ios_layout_json_matches_android_xml(self):
        converter = load_module("convert_keyboard_layouts", CONVERTER)
        for layout_id in PHONE_LAYOUT_IDS:
            xml_path = ANDROID_XML_DIR / f"{layout_id}.xml"
            json_path = IOS_LAYOUTS_DIR / f"{layout_id}.json"
            self.assertTrue(xml_path.exists(), f"Android source XML missing: {xml_path}")
            self.assertTrue(json_path.exists(), f"iOS JSON missing: {json_path}")

            expected = converter.convert_keyboard_xml(str(xml_path))
            actual = json.loads(json_path.read_text(encoding="utf-8-sig"))
            self.assertEqual(
                actual,
                expected,
                f"{layout_id}.json is not a faithful conversion of {layout_id}.xml — "
                f"regenerate with scripts/convert_keyboard_layouts.py.",
            )

    def test_ipad_layout_variants_match_generators(self):
        builder = load_module("build_ipad_layouts", IPAD_BUILDER)
        trimmer = load_module("trim_ipad_layout", IPAD_TRIMMER)
        for source_id in PHONE_LAYOUT_IDS:
            source = json.loads(
                (IOS_LAYOUTS_DIR / f"{source_id}.json").read_text(encoding="utf-8-sig")
            )
            expected_full = builder.make_ipad_layout(source, source_id)
            full_id = expected_full["id"]
            full_path = IOS_LAYOUTS_DIR / f"{full_id}.json"
            self.assertTrue(full_path.exists(), f"iPad layout missing for #160: {full_path}")
            actual_full = json.loads(full_path.read_text(encoding="utf-8-sig"))
            self.assertEqual(actual_full, expected_full, f"{full_id}.json is stale")

            expected_narrow, _ = trimmer.trim_layout(expected_full)
            narrow_id = expected_narrow["id"]
            narrow_path = IOS_LAYOUTS_DIR / f"{narrow_id}.json"
            self.assertTrue(
                narrow_path.exists(),
                f"Narrow iPad layout missing for #160: {narrow_path}",
            )
            actual_narrow = json.loads(narrow_path.read_text(encoding="utf-8-sig"))
            self.assertEqual(actual_narrow, expected_narrow, f"{narrow_id}.json is stale")

    def test_xml_contract_declares_required_punctuation(self):
        # Guard the contract itself: prove the required codes really are in
        # the stable phone XML, so the assertions below rest on the Android
        # source and not on a hand-picked constant.
        for layer, required_codes in REQUIRED_PUNCT_CODES.items():
            xml_path = ANDROID_XML_DIR / f"{LAYER_VARIANTS[layer][0]}.xml"
            declared = xml_code_label_contract(xml_path, required_codes)
            self.assertEqual(
                set(declared),
                required_codes,
                f"{xml_path.name} no longer declares every required #160 punctuation code",
            )
            self.assertTrue(
                all(declared.values()),
                f"{xml_path.name} has an empty required punctuation keyLabel",
            )

    def test_required_punctuation_survives_in_all_variants(self):
        # The heart of the #160 follow-up regression test: for the normal and
        # Shift layers, every required punctuation code must stay typeable in
        # the phone layout AND in both iPad variants derived from it.
        for layer, required_codes in REQUIRED_PUNCT_CODES.items():
            xml_path = ANDROID_XML_DIR / f"{LAYER_VARIANTS[layer][0]}.xml"
            required = xml_code_label_contract(xml_path, required_codes)
            for layout_id in LAYER_VARIANTS[layer]:
                json_path = IOS_LAYOUTS_DIR / f"{layout_id}.json"
                self.assertTrue(json_path.exists(), f"missing layout JSON: {json_path}")
                layout = json.loads(json_path.read_text(encoding="utf-8-sig"))
                reachable = reachable_codes(layout)
                for code, label in required.items():
                    with self.subTest(layout=layout_id, char=label, code=code):
                        self.assertIn(
                            code,
                            reachable,
                            f"{layout_id}.json ({layer}) dropped '{label}' (code {code}) "
                            f"— it is neither a primary key nor a long-press output. "
                            f"The phone/XML contract guarantees it (#160 follow-up).",
                        )
                        matching_keys = [
                            key
                            for row in layout.get("rows", [])
                            for key in row.get("keys", [])
                            if code in (key.get("code"), key.get("longPressCode"))
                        ]
                        self.assertTrue(
                            any(label in key.get("label", "") for key in matching_keys),
                            f"{layout_id}.json exposes code {code} but does not label it as '{label}'.",
                        )

    def test_layouts_registered_in_xcodeproj(self):
        content = PBXPROJ.read_text(encoding="utf-8")
        target = re.search(
            r"[0-9A-F]{24} /\* LimeKeyboard \*/ = \{\s*isa = PBXNativeTarget;.*?buildPhases = \((.*?)\);",
            content,
            re.DOTALL,
        )
        if target is None:
            self.fail("LimeKeyboard target is missing from the Xcode project")
        resources = re.search(r"([0-9A-F]{24}) /\* Resources \*/", target.group(1))
        if resources is None:
            self.fail("LimeKeyboard target has no Resources build phase")
        phase = re.search(
            rf"{resources.group(1)} /\* Resources \*/ = \{{(.*?)\n\t\t\}};",
            content,
            re.DOTALL,
        )
        if phase is None:
            self.fail("LimeKeyboard Resources build phase cannot be resolved")

        for layout_id in ALL_LAYOUT_IDS:
            fname = f"{layout_id}.json"
            self.assertIn(
                f"/* {fname} */ = {{isa = PBXFileReference",
                content,
                f"{fname} has no PBXFileReference in the Xcode project (#160).",
            )
            build_file = re.search(
                rf"([0-9A-F]{{24}}) /\* {re.escape(fname)} in Resources \*/ = "
                rf"\{{isa = PBXBuildFile; fileRef = [0-9A-F]{{24}} /\* {re.escape(fname)} \*/; \}};",
                content,
            )
            if build_file is None:
                self.fail(f"{fname} has no PBXBuildFile resource entry")
            self.assertIn(
                f"{build_file.group(1)} /* {fname} in Resources */",
                phase.group(1),
                f"{fname} is not in the LimeKeyboard target's Resources build phase (#160).",
            )


if __name__ == "__main__":
    unittest.main()
