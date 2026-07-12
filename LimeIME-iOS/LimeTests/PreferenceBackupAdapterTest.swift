/*
 *
 *  *
 *  **    Copyright 2026, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

import XCTest
@testable import LimeIME

final class PreferenceBackupAdapterTest: XCTestCase {
    private var suiteName: String!
    private var standardSuiteName: String!
    private var defaults: UserDefaults!
    private var standardDefaults: UserDefaults!

    override func setUp() {
        super.setUp()
        suiteName = "test.preference.backup.\(UUID().uuidString)"
        standardSuiteName = "test.preference.standard.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)!
        standardDefaults = UserDefaults(suiteName: standardSuiteName)!
        defaults.removePersistentDomain(forName: suiteName)
        standardDefaults.removePersistentDomain(forName: standardSuiteName)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        standardDefaults.removePersistentDomain(forName: standardSuiteName)
        defaults = nil
        standardDefaults = nil
        suiteName = nil
        standardSuiteName = nil
        super.tearDown()
    }

    func testExportManifestBacksUpFullPrefsTableSetWithCanonicalTypes() throws {
        let expected = fullIOSPrefsTableFixture()
        for (key, value) in expected {
            if key.hasPrefix("backup_on_delete_") || key.hasPrefix("restore_on_import_") {
                standardDefaults.set(value, forKey: key)
            } else {
                defaults.set(value, forKey: key)
            }
        }
        defaults.set("do-not-export", forKey: "PAYMENT_FLAG")
        standardDefaults.set("also-do-not-export", forKey: "server_url")

        let data = try PreferenceBackupAdapter.exportManifestData(
            defaults: defaults,
            sourcePlatform: "ios",
            standardDefaults: standardDefaults)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let values = try XCTUnwrap(root["preferences"] as? [String: Any])

        XCTAssertEqual(root["schema"] as? Int, 1)
        XCTAssertEqual(root["sourcePlatform"] as? String, "ios")
        XCTAssertEqual(values.count, expected.count, "Manifest must contain exactly the full iOS-supported PREFS_TABLE set seeded by this test")
        assertManifest(values, equals: expected)
        XCTAssertNil(values["PAYMENT_FLAG"])
        XCTAssertNil(values["server_url"])
    }

    func testRestoreManifestAppliesSupportedKeysAndIgnoresAndroidOnlyKeys() throws {
        let json = """
        {
          "schema": 1,
          "sourcePlatform": "android",
            "preferences": {
              "keyboard_theme": 4,
              "keyboard_size": "1",
              "show_arrow_key": 2,
              "vibrate_level": 80,
              "han_convert_option": 2,
              "custom_im_reverselookup": "dayi",
              "auto_commit": 3,
              "smart_chinese_input": false,
              "physical_keyboard_sort": true,
              "unknown_pref": "ignored"
          }
        }
        """.data(using: .utf8)!

        XCTAssertTrue(try PreferenceBackupAdapter.restoreManifestData(json, defaults: defaults))

        XCTAssertEqual(defaults.integer(forKey: "keyboard_theme"), 4)
        XCTAssertEqual(defaults.string(forKey: "keyboard_size"), "1")
        XCTAssertEqual(defaults.integer(forKey: "show_arrow_key"), 2)
        XCTAssertEqual(defaults.integer(forKey: "vibrate_level"), 80)
        XCTAssertEqual(defaults.integer(forKey: "han_convert_option"), 2)
        XCTAssertEqual(defaults.string(forKey: "custom_im_reverselookup"), "dayi")
        XCTAssertEqual(defaults.integer(forKey: "auto_commit"), 3)
        XCTAssertEqual(defaults.bool(forKey: "smart_chinese_input"), false)
        XCTAssertNil(defaults.object(forKey: "physical_keyboard_sort"))
        XCTAssertNil(defaults.object(forKey: "unknown_pref"))
    }

    func testRestoreManifestRestoresEveryIOSPrefsTableValue() throws {
        let expected = fullIOSPrefsTableFixture()
        var manifestValues = expected
        manifestValues["physical_keyboard_sort"] = true
        manifestValues["hide_software_keyboard_typing_with_physical"] = false
        manifestValues["unknown_pref"] = "ignored"
        let data = try JSONSerialization.data(withJSONObject: [
            "schema": 1,
            "sourcePlatform": "android",
            "preferences": manifestValues
        ])

        XCTAssertTrue(try PreferenceBackupAdapter.restoreManifestData(
            data,
            defaults: defaults,
            standardDefaults: standardDefaults))

        for (key, expectedValue) in expected {
            let restoredDefaults = key.hasPrefix("backup_on_delete_") || key.hasPrefix("restore_on_import_")
                ? standardDefaults!
                : defaults!
            switch expectedValue {
            case let expectedInt as Int:
                XCTAssertEqual(restoredDefaults.integer(forKey: key), expectedInt, "\(key) should restore as an integer")
            case let expectedBool as Bool:
                XCTAssertEqual(restoredDefaults.bool(forKey: key), expectedBool, "\(key) should restore as a boolean")
            case let expectedString as String:
                XCTAssertEqual(restoredDefaults.string(forKey: key), expectedString, "\(key) should restore as a string")
            default:
                XCTFail("Unsupported fixture value for \(key)")
            }
        }
        XCTAssertNil(defaults.object(forKey: "physical_keyboard_sort"))
        XCTAssertNil(defaults.object(forKey: "hide_software_keyboard_typing_with_physical"))
        XCTAssertNil(defaults.object(forKey: "unknown_pref"))
    }

    func testRestoreAndroidStyleManifestOnIOS() throws {
        let json = """
        {
          "schema": 1,
          "sourcePlatform": "android",
          "preferences": {
            "keyboard_theme": 4,
            "keyboard_size": "1",
            "smart_chinese_input": false,
            "physical_keyboard_sort": true
          }
        }
        """.data(using: .utf8)!

        XCTAssertTrue(try PreferenceBackupAdapter.restoreManifestData(json, defaults: defaults))

        XCTAssertEqual(defaults.integer(forKey: "keyboard_theme"), 4)
        XCTAssertEqual(defaults.string(forKey: "keyboard_size"), "1")
        XCTAssertEqual(defaults.bool(forKey: "smart_chinese_input"), false)
        XCTAssertNil(defaults.object(forKey: "physical_keyboard_sort"))
    }

    func testRestoreManifestIgnoresWrongTypesAndRejectsInvalidSchema() throws {
        let wrongTypes = """
        {
          "schema": 1,
          "preferences": {
            "keyboard_theme": "not-an-int",
            "smart_chinese_input": "not-a-bool"
          }
        }
        """.data(using: .utf8)!

        XCTAssertTrue(try PreferenceBackupAdapter.restoreManifestData(wrongTypes, defaults: defaults))
        XCTAssertNil(defaults.object(forKey: "keyboard_theme"))
        XCTAssertNil(defaults.object(forKey: "smart_chinese_input"))

        let invalidSchema = """
        {
          "schema": 99,
          "preferences": {
            "keyboard_theme": 4
          }
        }
        """.data(using: .utf8)!

        XCTAssertFalse(try PreferenceBackupAdapter.restoreManifestData(invalidSchema, defaults: defaults))
        XCTAssertNil(defaults.object(forKey: "keyboard_theme"))
    }

    private func fullIOSPrefsTableFixture() -> [String: Any] {
        [
            "keyboard_theme": 4,
            "keyboard_size": "1",
            "font_size": "2",
            "number_row_in_english": false,
            "show_arrow_key": 2,
            "split_keyboard_mode": 1,
            "vibrate_on_keypress": false,
            "vibrate_level": 80,
            "sound_on_keypress": true,
            "keypress_sound_volume": "0.75",
            "smart_chinese_input": false,
            "auto_chinese_symbol": true,
            "candidate_switch": true,
            "persistent_language_mode": true,
            "enable_emoji_position": 3,
            "similiar_list": 30,
            "han_convert_option": 2,
            "similiar_enable": false,
            "candidate_suggestion": false,
            "learn_phrase": false,
            "learning_switch": false,
            "english_dictionary_enable": false,
            "auto_cap": false,
            "custom_im_reverselookup": "dayi",
            "cj_im_reverselookup": "phonetic",
            "scj_im_reverselookup": "cj",
            "cj5_im_reverselookup": "scj",
            "ecj_im_reverselookup": "cj5",
            "dayi_im_reverselookup": "bpmf",
            "bpmf_im_reverselookup": "dayi",
            "phonetic_im_reverselookup": "custom",
            "ez_im_reverselookup": "array",
            "array_im_reverselookup": "array10",
            "array10_im_reverselookup": "ez",
            "wb_im_reverselookup": "hs",
            "hs_im_reverselookup": "pinyin",
            "pinyin_im_reverselookup": "none",
            "phonetic_keyboard_type": "standard",
            "auto_commit": 3,
            "accept_number_index": true,
            "accept_symbol_index": true,
            "backup_on_delete_phonetic": false,
            "restore_on_import_phonetic": false
        ]
    }

    private func assertManifest(_ actual: [String: Any], equals expected: [String: Any]) {
        for (key, expectedValue) in expected {
            guard let actualValue = actual[key] else {
                XCTFail("Missing backed-up preference \(key)")
                continue
            }
            switch expectedValue {
            case let expectedInt as Int:
                XCTAssertEqual(actualValue as? Int, expectedInt, "\(key) should be backed up as an integer")
            case let expectedBool as Bool:
                XCTAssertEqual(actualValue as? Bool, expectedBool, "\(key) should be backed up as a boolean")
            case let expectedString as String:
                XCTAssertEqual(actualValue as? String, expectedString, "\(key) should be backed up as a string")
            default:
                XCTFail("Unsupported fixture value for \(key)")
            }
        }
    }
}
