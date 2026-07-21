#!/usr/bin/env python3
# test_custom_im_keyboard_ios.py - Linux parity/resource contract for GitHub issue #177.
#
# Android is the reference implementation. LimeDB.getDefaultKeyboardCodeForImportedIM()
# groups DB_TABLE_CUSTOM with DB_TABLE_PINYIN and returns "limenum", whose catalog row
# resolves imkb/imshiftkb to lime_number / lime_number_shift.
#
# iOS diverged in two places, which together produce the #177 report:
#   1. defaultKeyboardCodeForImportedIM() has no "custom" case, so a text-imported
#      custom table falls through to the default "lime". The "lime" catalog row has
#      imkb = "lime", and at the time the keyboard extension shipped no lime.json —
#      so LayoutLoader.load() dead-ended and the previous IM's layout stayed on
#      screen. (#191 later ported lime.json; the repair below is kept regardless.)
#   2. seedCustomIM() hardcodes keyboard 'lime_abc'. lime_abc IS a loadable layout,
#      so resolvedLayoutId returns it directly — but it is the Chinese-mode alphabet
#      layout whose mode key is `中` (code -10 → switchToIM) rather than an `abc`
#      English switch. That is the "cannot switch to English" half of the report.
#
# The fix is parity with Android — keyboard code "limenum", reusing the ALREADY
# BUNDLED lime_number layouts. No new keyboard layout resource is introduced.
#
# This test guards the contract on Linux (no Xcode/Swift toolchain needed):
#   1. Android really maps custom → limenum (the parity source of truth),
#   2. iOS maps custom → limenum on import and on fresh seed,
#   3. the lime_number layouts that limenum points at exist, parse, and are bundled,
#   4. lime.json ships since #191, but "lime" stays a repaired custom default,
#   5. legacy repair covers only the known-bad values and preserves user choices.
#
# Usage: python3 scripts/test_custom_im_keyboard_ios.py

import json
import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
ANDROID_LIMEDB = (REPO_ROOT / "LimeStudio" / "app" / "src" / "main" / "java"
                  / "org" / "limeime" / "limedb" / "LimeDB.java")
IOS_LIMEDB = REPO_ROOT / "LimeIME-iOS" / "Shared" / "Database" / "LimeDB.swift"
IOS_CONTROLLER = (REPO_ROOT / "LimeIME-iOS" / "LimeKeyboard"
                  / "KeyboardViewController.swift")
IOS_LAYOUTS_DIR = REPO_ROOT / "LimeIME-iOS" / "LimeKeyboard" / "Layouts"
PBXPROJ = REPO_ROOT / "LimeIME-iOS" / "LimeIME.xcodeproj" / "project.pbxproj"

# The keyboard code Android assigns to an imported custom table.
CUSTOM_KEYBOARD_CODE = "limenum"
# The layout ids the `limenum` catalog row points at (imkb / imshiftkb).
LIMENUM_LAYOUT_IDS = ("lime_number", "lime_number_shift")
# Historical custom `im.keyboard` values that are known to be broken (#177).
# Anything NOT in this set is a deliberate user selection and must be preserved.
KNOWN_BAD_CUSTOM_DEFAULTS = ("", "lime", "lime_abc")


def read(path):
    return path.read_text(encoding="utf-8")


class AndroidParitySourceOfTruth(unittest.TestCase):
    """Pin the Android behaviour this fix is matching, so drift is caught here."""

    def test_android_maps_custom_table_to_limenum(self):
        source = read(ANDROID_LIMEDB)
        match = re.search(
            r"public String getDefaultKeyboardCodeForImportedIM\(String tableName\)"
            r"\s*\{(.*?)\n    \}",
            source,
            re.S,
        )
        self.assertIsNotNone(
            match, "getDefaultKeyboardCodeForImportedIM not found in Android LimeDB.java")
        body = match.group(1)
        case_block = re.search(
            r"case LIME\.DB_TABLE_CUSTOM:\s*\n\s*return \"([a-z_]+)\";", body)
        self.assertIsNotNone(
            case_block, "Android no longer has an explicit DB_TABLE_CUSTOM case")
        self.assertEqual(
            CUSTOM_KEYBOARD_CODE, case_block.group(1),
            "Android custom default keyboard changed; iOS parity target must follow")


class IOSImportDefault(unittest.TestCase):
    """#177 part 1: a text-imported custom table must get Android's keyboard code."""

    def setUp(self):
        source = read(IOS_LIMEDB)
        match = re.search(
            r"func defaultKeyboardCodeForImportedIM\(_ tableName: String\)"
            r" -> String \{(.*?)\n    \}",
            source,
            re.S,
        )
        self.assertIsNotNone(match, "defaultKeyboardCodeForImportedIM not found")
        self.body = match.group(1)

    def test_custom_maps_to_limenum(self):
        # A Swift case label may list several comma-separated patterns
        # (`case "pinyin", "custom":`) and may be followed by comment lines before
        # the `return`. Parse both rather than constraining the production syntax.
        cases = re.findall(
            r"case ((?:\"[a-z_0-9]+\"\s*,\s*)*\"[a-z_0-9]+\"):\s*\n"
            r"(?:\s*//[^\n]*\n)*"
            r"\s*return \"([a-z_0-9]+)\"",
            self.body,
        )
        mapping = {}
        for labels, keyboard in cases:
            for label in re.findall(r"\"([a-z_0-9]+)\"", labels):
                mapping[label] = keyboard
        self.assertIn(
            "custom", mapping,
            "#177: iOS defaultKeyboardCodeForImportedIM has no 'custom' case, so a "
            "custom import falls through to the default 'lime', for which iOS ships "
            "no layout JSON. Android returns 'limenum'.")
        self.assertEqual(
            CUSTOM_KEYBOARD_CODE, mapping["custom"],
            "custom must resolve to Android's 'limenum' keyboard code")

    def test_default_fallthrough_names_a_bundled_layout(self):
        # Pre-#191 this asserted the opposite: the default arm's "lime" had no
        # layout JSON, which is why custom needed its own explicit case (#177).
        # The explicit custom → limenum case stays; since #191 the default arm's
        # keyboard resolves to a real bundled layout too.
        fallthrough = re.search(r"default:\s*\n\s*return \"([a-z_0-9]+)\"", self.body)
        self.assertIsNotNone(fallthrough, "default arm not found")
        self.assertTrue(
            (IOS_LAYOUTS_DIR / f"{fallthrough.group(1)}.json").exists(),
            "#191: the default arm's layout must be bundled so no imported IM "
            "dead-ends in LayoutLoader.")


class IOSFreshSeed(unittest.TestCase):
    """#177 part 2: seedCustomIM must not seed the `中`-mode-key lime_abc layout."""

    def setUp(self):
        source = read(IOS_LIMEDB)
        match = re.search(r"func seedCustomIM\(\) throws \{(.*?)\n    \}", source, re.S)
        self.assertIsNotNone(match, "seedCustomIM not found")
        self.body = match.group(1)

    def test_fresh_seed_uses_limenum(self):
        insert = re.search(r"VALUES \('custom', '自建', '', '([a-z_0-9]+)'", self.body)
        self.assertIsNotNone(insert, "custom INSERT literal not found in seedCustomIM")
        self.assertEqual(
            CUSTOM_KEYBOARD_CODE, insert.group(1),
            "#177: seedCustomIM seeds 'lime_abc', a Chinese-mode layout whose mode key "
            "is `中` (switchToIM) instead of an `abc` English switch, leaving the user "
            "with no path to the English keyboard. Android seeds 'limenum'.")

    def test_existing_registration_repairs_only_the_known_bad_keyboards(self):
        update = re.search(
            r"UPDATE im SET keyboard = '([a-z_0-9]+)'\s*\n\s*WHERE code = 'custom'\s*\n"
            r"\s*AND title IN \(([^)]*)\)\s*\n"
            r"\s*AND \(keyboard IS NULL OR keyboard IN \(([^)]*)\)\)",
            self.body,
        )
        self.assertIsNotNone(
            update,
            "#177: seedCustomIM returns early when a custom row already exists, so an "
            "install carrying the old 'lime_abc'/'lime' keyboard stays broken. It must "
            "repair the known-bad values in place.")
        self.assertEqual(CUSTOM_KEYBOARD_CODE, update.group(1))
        titles = tuple(re.findall(r"'([^']+)'", update.group(2)))
        self.assertEqual(
            {"自建", "keyboard"}, set(titles),
            "the DB repair must update only custom registration/keyboard rows, not "
            "unrelated IM metadata rows whose keyboard column is NULL")
        repaired = tuple(re.findall(r"'([a-z_0-9]*)'", update.group(3)))
        self.assertEqual(
            sorted(KNOWN_BAD_CUSTOM_DEFAULTS), sorted(repaired),
            "the DB repair must cover exactly the known-bad values — a broader WHERE "
            "would overwrite keyboards the user deliberately selected")


class IOSLegacyRepair(unittest.TestCase):
    """#177: existing installs must resolve without re-importing the table."""

    def setUp(self):
        self.source = read(IOS_CONTROLLER)

    def test_repair_helper_exists_and_targets_limenum(self):
        self.assertIsNotNone(
            re.search(r"static func repairedCustomKeyboardCode\(", self.source),
            "#177: no repairedCustomKeyboardCode helper, so users who already have a "
            "custom IM row stay broken until they re-import.")
        match = re.search(
            r"static let customKeyboardBadDefaults: Set<String> = \[([^\]]*)\]",
            self.source)
        self.assertIsNotNone(match, "customKeyboardBadDefaults set not found")
        bad = tuple(re.findall(r"\"([a-z_0-9]*)\"", match.group(1)))
        self.assertEqual(
            sorted(KNOWN_BAD_CUSTOM_DEFAULTS), sorted(bad),
            "the repair must cover exactly the known-bad values — widening it would "
            "override keyboards the user deliberately selected")

    def test_runtime_resolution_applies_the_repair_for_custom(self):
        match = re.search(
            r"private func resolvedLayoutId\(for tableNick: String\) -> String \{"
            r"(.*?)\n    \}",
            self.source,
            re.S,
        )
        self.assertIsNotNone(match, "resolvedLayoutId not found")
        body = match.group(1)
        self.assertIn(
            "repairedCustomKeyboardCode", body,
            "#177: resolvedLayoutId must repair a legacy custom keyboard code before "
            "resolving, so a cyclic/direct IM switch lands on lime_number immediately.")
        repair_at = body.index("repairedCustomKeyboardCode")
        load_at = body.index("if LayoutLoader.load(kbCode) != nil")
        self.assertLess(
            repair_at, load_at,
            "the repair must run BEFORE the direct-layout early return: 'lime_abc' is "
            "itself loadable, so a later repair would never be reached.")

    def test_chinese_layout_resolution_never_falls_back_to_english(self):
        self.assertNotIn(
            "LayoutLoader.load(layoutName) ?? LayoutLoader.load(englishLayout)",
            self.source,
            "a Chinese IM must never fall back to the English runtime layout")
        helper = re.search(
            r"static func chineseLayoutCandidates\(preferred: String\) -> \[String\] \{"
            r"(.*?)\n    \}",
            self.source,
            re.S,
        )
        self.assertIsNotNone(
            helper,
            "Chinese layout loading needs an explicit candidate policy that cannot include "
            "the preference-driven English runtime layout")
        body = helper.group(1)
        self.assertIn('"lime_number"', body)
        self.assertNotIn("englishLayoutId", body)
        self.assertNotIn('"lime_abc"', body)


class LimenumLayoutResourceContract(unittest.TestCase):
    """limenum points at already-bundled layouts; #177 adds no new layout resource."""

    def test_layouts_exist_and_parse(self):
        for layout_id in LIMENUM_LAYOUT_IDS:
            path = IOS_LAYOUTS_DIR / f"{layout_id}.json"
            self.assertTrue(path.exists(), f"{path.name} is missing")
            try:
                # Committed layouts are stored UTF-8 with a BOM (utf-8-sig).
                parsed = json.loads(path.read_text(encoding="utf-8-sig"))
            except json.JSONDecodeError as exc:
                self.fail(f"{path.name} is not valid JSON: {exc}")
            self.assertTrue(parsed.get("rows"), f"{path.name} has no rows")

    def test_lime_json_ships_but_lime_stays_a_bad_custom_default(self):
        # #191 ported the lime base faces (LIME 預設鍵盤 / 許氏英文 with the English
        # number row off), so lime.json now ships. The #177 repair is unchanged:
        # a legacy custom row storing "lime" was a fallthrough, never a deliberate
        # choice, and Android's custom default is limenum.
        path = IOS_LAYOUTS_DIR / "lime.json"
        self.assertTrue(path.exists(), "lime.json must ship since #191")
        parsed = json.loads(path.read_text(encoding="utf-8-sig"))
        self.assertTrue(parsed.get("rows"), "lime.json has no rows")
        source = read(IOS_CONTROLLER)
        self.assertIn(
            'customKeyboardBadDefaults: Set<String> = ["", "lime", "lime_abc"]',
            source,
            '"lime" must stay a repaired custom default (Android parity: limenum).')

    def test_limenum_catalog_row_points_at_those_layouts(self):
        source = read(IOS_LIMEDB)
        match = re.search(
            r"if keyboard == \"limenum\" \{(.*?)\n        \}", source, re.S)
        self.assertIsNotNone(match, "limenum KeyboardConfig fallback not found")
        block = match.group(1)
        self.assertIn('imkb: "lime_number"', block)
        self.assertIn('imshiftkb: "lime_number_shift"', block)

    def test_layouts_are_registered_in_the_keyboard_target(self):
        content = read(PBXPROJ)
        target = re.search(
            r"/\* LimeKeyboard \*/ = \{\n\s*isa = PBXNativeTarget;"
            r".*?buildPhases = \((.*?)\);.*?name = LimeKeyboard;",
            content,
            re.S,
        )
        self.assertIsNotNone(target, "LimeKeyboard PBXNativeTarget not found")
        resource_phase_id = re.search(
            r"([A-F0-9]{24}) /\* Resources \*/", target.group(1))
        self.assertIsNotNone(
            resource_phase_id, "LimeKeyboard target has no Resources build phase")
        phase = re.search(
            re.escape(resource_phase_id.group(1))
            + r" /\* Resources \*/ = \{\n\s*isa = PBXResourcesBuildPhase;"
              r".*?files = \((.*?)\);",
            content,
            re.S,
        )
        self.assertIsNotNone(
            phase, "LimeKeyboard Resources build phase not found in project.pbxproj")
        keyboard_resources = phase.group(1)
        for layout_id in LIMENUM_LAYOUT_IDS:
            fname = f"{layout_id}.json"
            self.assertIn(
                f"/* {fname} in Resources */",
                keyboard_resources,
                f"{fname} is not in the LimeKeyboard Resources build phase (#177).",
            )


if __name__ == "__main__":
    unittest.main()
