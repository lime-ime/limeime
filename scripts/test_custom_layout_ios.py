#!/usr/bin/env python3
# test_custom_layout_ios.py - regression test for GitHub issue #177.
#
# Issue #177 reports two user-visible failures on iOS after importing a custom
# CIN table:
#
#   1. Switching from another LIME internal IM (e.g. Array 10) to `custom` can
#      leave the PREVIOUS IM's keyboard on screen. Closing and reopening the
#      keyboard then shows the custom IM's layout.
#   2. The custom keyboard shows a `中` mode key (switchToIM) instead of the
#      Chinese-layout English switch, so there is no way to reach the English
#      keyboard from custom composition.
#
# Source evidence:
#   `LimeDB.seedCustomIM()` registers `custom` against the English runtime layout
#   `lime_abc`. Its `code: -10` / `中` mode key explains (2) directly. The switch
#   handlers also skipped `setLayout` whenever a resolved layout was absent, a
#   code path that plausibly permits (1) and is now covered conservatively.
#
# The fix gives `custom` a dedicated layout family (`lime_custom*`) that mirrors
# the `lime_abc*` alphabetic geometry but carries the Chinese-layout English
# switch (`code: -9`), and repairs existing `custom` rows still pointing at
# `lime_abc`. That makes the `lime_<tableNick>` fallback resolve to a real,
# loadable layout AND puts an English switch on every variant.
#
# This test guards the iOS layout/resource contract on Linux (no Xcode needed):
#   1. every phone / iPad / iPad-narrow normal+shift variant exists and parses,
#   2. each is a faithful mirror of its lime_abc counterpart (geometry parity)
#      differing ONLY in `id` and the mode key,
#   3. each exposes exactly one English switch and no switchToIM key,
#   4. all variants are registered in LimeIME.xcodeproj so they are bundled,
#   5. seeding uses the dedicated layout and repairs legacy rows,
#   6. legacy custom metadata resolves to the dedicated layout immediately,
#   7. the direct, cyclic, and language-mode switch paths avoid retaining a stale layout.
#
# Usage: python3 scripts/test_custom_layout_ios.py

import json
import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
IOS_LAYOUTS_DIR = REPO_ROOT / "LimeIME-iOS" / "LimeKeyboard" / "Layouts"
PBXPROJ = REPO_ROOT / "LimeIME-iOS" / "LimeIME.xcodeproj" / "project.pbxproj"
LIMEDB = REPO_ROOT / "LimeIME-iOS" / "Shared" / "Database" / "LimeDB.swift"
KVC = REPO_ROOT / "LimeIME-iOS" / "LimeKeyboard" / "KeyboardViewController.swift"

SWITCH_TO_ENGLISH = -9   # LimeKeyCode.switchToEnglish
SWITCH_TO_IM = -10       # LimeKeyCode.switchToIM

# Every custom variant, the lime_abc variant it mirrors, and the English-switch
# label that variant must use. Phone Chinese layouts label the key "EN"
# (see lime_cj.json); iPad Chinese layouts label it "abc" (see lime_cj_ipad.json).
VARIANTS = (
    ("lime_custom", "lime_abc", "EN"),
    ("lime_custom_shift", "lime_abc_shift", "EN"),
    ("lime_custom_ipad", "lime_abc_ipad", "abc"),
    ("lime_custom_ipad_shift", "lime_abc_ipad_shift", "abc"),
    ("lime_custom_ipad_narrow", "lime_abc_ipad_narrow", "abc"),
    ("lime_custom_ipad_narrow_shift", "lime_abc_ipad_narrow_shift", "abc"),
)

CUSTOM_LAYOUT_IDS = tuple(v[0] for v in VARIANTS)


def load_layout(layout_id):
    """Load a layout JSON. The committed files carry a UTF-8 BOM."""
    path = IOS_LAYOUTS_DIR / f"{layout_id}.json"
    if not path.is_file():
        raise AssertionError(f"{path} is missing")
    return json.loads(path.read_text(encoding="utf-8-sig"))


def mode_keys(layout, code):
    """All (row_index, key_index, key) triples whose key code is `code`."""
    return [
        (ri, ki, key)
        for ri, row in enumerate(layout["rows"])
        for ki, key in enumerate(row["keys"])
        if key["code"] == code
    ]


class CustomLayoutFamilyTest(unittest.TestCase):
    """(1) Every required variant exists and parses."""

    def test_all_required_variants_exist_and_parse(self):
        for layout_id in CUSTOM_LAYOUT_IDS:
            with self.subTest(layout=layout_id):
                layout = load_layout(layout_id)
                self.assertEqual(
                    layout["id"],
                    layout_id,
                    f"{layout_id}.json must declare its own id (#177)",
                )
                self.assertTrue(layout["rows"], f"{layout_id} has no rows")

    def test_variants_mirror_abc_geometry_except_mode_key(self):
        """(2) Geometry parity: identical to lime_abc apart from id + mode key.

        This is what keeps the custom keyboard usable for a custom CIN table:
        the root keys stay on the alphabetic grid users already type on.
        """
        for custom_id, abc_id, _label in VARIANTS:
            with self.subTest(layout=custom_id):
                custom = load_layout(custom_id)
                abc = load_layout(abc_id)

                self.assertEqual(
                    custom["defaultWidthPercent"],
                    abc["defaultWidthPercent"],
                    f"{custom_id} must keep {abc_id}'s default key width",
                )
                self.assertEqual(
                    [len(r["keys"]) for r in custom["rows"]],
                    [len(r["keys"]) for r in abc["rows"]],
                    f"{custom_id} must keep {abc_id}'s row shape",
                )

                # Normalise the mode key on both sides, then require exact equality.
                def normalised(layout):
                    rows = json.loads(json.dumps(layout["rows"]))
                    for row in rows:
                        for key in row["keys"]:
                            if key["code"] in (SWITCH_TO_ENGLISH, SWITCH_TO_IM):
                                key["code"] = "<MODE>"
                                key["label"] = "<MODE>"
                    return rows

                self.assertEqual(
                    normalised(custom),
                    normalised(abc),
                    f"{custom_id} must differ from {abc_id} only in id and the mode key (#177)",
                )

    def test_every_variant_exposes_exactly_one_english_switch(self):
        """(3) The reported dead end: no way out of custom composition."""
        for custom_id, abc_id, label in VARIANTS:
            with self.subTest(layout=custom_id):
                custom = load_layout(custom_id)

                to_english = mode_keys(custom, SWITCH_TO_ENGLISH)
                to_im = mode_keys(custom, SWITCH_TO_IM)

                self.assertEqual(
                    len(to_english),
                    1,
                    f"{custom_id} must expose exactly one English switch (#177)",
                )
                self.assertEqual(
                    to_im,
                    [],
                    f"{custom_id} must not carry the English-keyboard 中 key (#177)",
                )
                self.assertEqual(
                    to_english[0][2]["label"],
                    label,
                    f"{custom_id} English switch must be labelled {label!r}",
                )

    def test_mode_key_keeps_its_abc_slot_and_width(self):
        """The English switch must land where lime_abc put 中 — not shift the row."""
        for custom_id, abc_id, _label in VARIANTS:
            with self.subTest(layout=custom_id):
                custom_slot = mode_keys(load_layout(custom_id), SWITCH_TO_ENGLISH)
                abc_slot = mode_keys(load_layout(abc_id), SWITCH_TO_IM)

                self.assertEqual(len(abc_slot), 1, f"{abc_id} fixture assumption changed")
                self.assertEqual(
                    (custom_slot[0][0], custom_slot[0][1]),
                    (abc_slot[0][0], abc_slot[0][1]),
                    f"{custom_id} must keep the mode key in {abc_id}'s row/column slot",
                )
                self.assertEqual(
                    custom_slot[0][2]["widthPercent"],
                    abc_slot[0][2]["widthPercent"],
                    f"{custom_id} must keep {abc_id}'s mode key width",
                )

    def test_layouts_registered_in_xcodeproj(self):
        """(4) An unbundled JSON makes LayoutLoader.load return nil — the #177 bug."""
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

        for layout_id in CUSTOM_LAYOUT_IDS:
            fname = f"{layout_id}.json"
            with self.subTest(layout=fname):
                self.assertIn(
                    f"/* {fname} */ = {{isa = PBXFileReference",
                    content,
                    f"{fname} has no PBXFileReference in the Xcode project (#177).",
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
                    f"{fname} is not in the LimeKeyboard Resources build phase (#177).",
                )


class CustomSeedContractTest(unittest.TestCase):
    """(5) Seeding and repair of the `custom` IM row."""

    def setUp(self):
        self.source = LIMEDB.read_text(encoding="utf-8")
        match = re.search(
            r"func seedCustomIM\(\) throws \{(.*?)\n    \}\n", self.source, re.DOTALL
        )
        if match is None:
            self.fail("could not isolate seedCustomIM() body in LimeDB.swift")
        self.body = match.group(1)

    def test_seed_registers_dedicated_custom_layout(self):
        self.assertIn(
            "'lime_custom'",
            self.body,
            "seedCustomIM must register `custom` against lime_custom, not the "
            "English lime_abc layout (#177)",
        )

    def test_seed_no_longer_registers_english_layout(self):
        """The INSERT must not plant lime_abc.

        Scoped to the VALUES clause on purpose: `lime_abc` still appears (and must
        appear) in the repair WHERE clause as the legacy value being migrated away
        from, so a blanket substring check would fail on correct code.
        """
        insert = re.search(r"INSERT INTO im .*?VALUES \(([^)]*)\)", self.body, re.DOTALL)
        if insert is None:
            self.fail("could not isolate the seedCustomIM INSERT statement")
        self.assertNotIn(
            "'lime_abc'",
            insert.group(1),
            "seedCustomIM must not seed the English lime_abc layout — its 中 mode "
            "key leaves no route to the English keyboard (#177)",
        )
        self.assertIn(
            "'lime_custom'",
            insert.group(1),
            "seedCustomIM must seed the dedicated lime_custom layout (#177)",
        )

    def test_repair_is_scoped_to_known_bad_values(self):
        """A user's deliberate layout choice must survive the migration."""
        self.assertIn(
            "keyboard = 'lime_abc'",
            self.body,
            "the repair must be scoped to the legacy lime_abc value, not applied "
            "unconditionally to every `custom` row (#177)",
        )

    def test_seed_repairs_existing_custom_rows(self):
        """Users who already imported a table have a row seeded with lime_abc.

        Without a repair pass the fix would only help fresh installs, and the
        reporter — who already imported — would still see 中 and a stale layout.
        """
        self.assertRegex(
            self.body,
            r"UPDATE\s+im\s+SET\s+keyboard\s*=",
            "seedCustomIM must repair existing `custom` rows, not bail out early "
            "when the row already exists (#177)",
        )
        self.assertNotRegex(
            self.body,
            r"guard\s+!exists\s+else\s*\{\s*return\s*\}",
            "the early return on an existing `custom` row blocks migration (#177)",
        )


class InternalIMSwitchFallbackTest(unittest.TestCase):
    """(6) A failed layout load must never leave the previous IM's keyboard up."""

    def setUp(self):
        self.source = KVC.read_text(encoding="utf-8")

    def _body(self, start_marker, end_marker):
        start = self.source.find(start_marker)
        if start < 0:
            self.fail(f"could not find {start_marker!r} in KeyboardViewController.swift")
        end = self.source.find(end_marker, start)
        if end < 0:
            self.fail(f"could not find {end_marker!r} after {start_marker!r}")
        return self.source[start:end]

    def test_switch_to_next_activated_im_uses_safe_layout_resolution(self):
        body = self._body(
            "private func switchToNextActivatedIM(forward: Bool)",
            "// MARK: - Symbol Keyboard",
        )
        self.assertIn(
            "safeLayout(",
            body,
            "switchToNextActivatedIM must resolve through safeLayout() so a missing "
            "layout falls back instead of silently keeping the previous IM's "
            "keyboard on screen (#177)",
        )
        self.assertNotRegex(
            body,
            r"if let newLayout = LayoutLoader\.load\(preferredLayout\)",
            "the optional-binding form skips setLayout entirely when the preferred "
            "layout is missing — that is the #177 stale-keyboard path",
        )

    def test_safe_layout_helper_falls_back_through_a_loadable_chain(self):
        body = self._body(
            "func safeLayout(",
            "\n    // MARK: - Shared UserDefaults",
        )
        self.assertIn(
            "lime_abc",
            body,
            "safeLayout must end on a guaranteed-bundled layout so it can never "
            "return nil (#177)",
        )

    def test_legacy_custom_metadata_resolves_to_dedicated_layout(self):
        body = self._body(
            "private func resolvedLayoutId(for tableNick: String)",
            "func safeLayout(",
        )
        self.assertIn('tableNick == "custom"', body)
        self.assertIn('kbCode == "lime_abc"', body)
        self.assertIn('return "lime_custom"', body)

    def test_chi_eng_switch_uses_safe_layout_resolution(self):
        """switchChiEng shares the same nil-load hazard as the IM cycle path."""
        body = self._body(
            "let layoutName = toEnglish ? englishLayoutId() : resolvedLayoutId(for: activeIM)",
            "applyHeight()",
        )
        self.assertIn(
            "safeLayout(",
            body,
            "switchChiEng must resolve through safeLayout() so a missing custom "
            "layout cannot strand the previous keyboard (#177)",
        )
        self.assertIn('fallback: toEnglish ? "lime_abc" : "lime_custom"', body)

    def test_absolute_menu_switch_uses_safe_layout_resolution(self):
        body = self._body(
            "private func switchIM(toIndex i: Int)",
            "private func displayName(for im: ImConfig)",
        )
        self.assertIn(
            "safeLayout(",
            body,
            "the direct LIME menu switch must not retain the previous layout (#177)",
        )
        self.assertNotRegex(body, r"if let layout = LayoutLoader\.load")


if __name__ == "__main__":
    unittest.main(verbosity=2)
