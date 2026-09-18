"""Linux source contract for the iOS HSU positional-remapping regression in #257."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
IOS_DB = ROOT / "LimeIME-iOS" / "Shared" / "Database" / "LimeDB.swift"
IOS_TEST = ROOT / "LimeIME-iOS" / "LimeTests" / "LimeDBTest.swift"
ANDROID_DB = ROOT / "LimeStudio" / "app" / "src" / "main" / "java" / "org" / "limeime" / "limedb" / "LimeDB.java"


class HsuPositionalRemapContract(unittest.TestCase):
    def test_ios_forces_exception_keys_initial_only_for_single_character_input(self):
        source = IOS_DB.read_text(encoding="utf-8")
        helper = re.search(
            r"private func applyDualRemap\(.*?\n    \}",
            source,
            flags=re.DOTALL,
        )
        if helper is None:
            self.fail("applyDualRemap helper is missing")
        normalized = re.sub(r"\s+", " ", helper.group(0))
        self.assertIn("code.count == 1 && alwaysSet.contains(ch)", normalized)
        self.assertIn("accumulated.count > 1 &&", normalized)

    def test_android_oracle_scopes_hsu_exception_to_single_character_input(self):
        source = ANDROID_DB.read_text(encoding="utf-8")
        single_character_branch = re.search(
            r"if \(code\.length\(\) == 1\).*?"
            r"phoneticKeyboardType\.startsWith\(LIME\.IM_PHONETIC_KEYBOARD_HSU\).*?"
            r"code\.equals\(\"f\"\)",
            source,
            flags=re.DOTALL,
        )
        self.assertIsNotNone(single_character_branch)

    def test_native_regressions_cover_reported_sequences_and_single_key_exception(self):
        source = IOS_TEST.read_text(encoding="utf-8")
        for assertion in (
            'XCTAssertEqual(db.preProcessingRemappingCode("xmf"), "ja3")',
            'XCTAssertEqual(db.preProcessingRemappingCode("hnf"), "cs3")',
            'XCTAssertEqual(db.preProcessingRemappingCode("ss"), "n7")',
            'XCTAssertEqual(db.preProcessingRemappingCode("asf"), "h7z")',
            'XCTAssertEqual(db.preProcessingRemappingCode("dd"), "27")',
            'XCTAssertEqual(db.preProcessingRemappingCode("adf"), "87z")',
            'db.addOrUpdateMappingRecord(LIME.DB_TABLE_PHONETIC, "j03", "晚", 0)',
            'db.addOrUpdateMappingRecord(LIME.DB_TABLE_PHONETIC, "cp3", "很", 0)',
            'XCTAssertTrue(evening.contains { $0.word == "晚" })',
            'XCTAssertTrue(very.contains { $0.word == "很" })',
        ):
            self.assertIn(assertion, source)

    def test_composing_display_matches_android_position_guard(self):
        source = IOS_DB.read_text(encoding="utf-8")
        helper = re.search(
            r"private func buildKeyNameDual\(.*?\n    \}",
            source,
            flags=re.DOTALL,
        )
        if helper is None:
            self.fail("buildKeyNameDual helper is missing")
        normalized = re.sub(r"\s+", " ", helper.group(0))
        self.assertIn("i > 1 &&", normalized)

        native_tests = IOS_TEST.read_text(encoding="utf-8")
        for assertion in (
            'db.keyToKeyName("sf", LIME.DB_TABLE_PHONETIC, true), "ㄙ(ㄈ/ˇ)"',
            'db.keyToKeyName("asf", LIME.DB_TABLE_PHONETIC, true), "(ㄘ/ㄟ)(ㄙ/˙)ㄈ"',
            'db.keyToKeyName("df", LIME.DB_TABLE_PHONETIC, true), "ㄉˊ"',
            'db.keyToKeyName("adf", LIME.DB_TABLE_PHONETIC, true), "ㄚ˙ㄈ"',
        ):
            self.assertIn(assertion, native_tests)


if __name__ == "__main__":
    unittest.main()
