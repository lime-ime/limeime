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

import Foundation

enum PreferenceBackupAdapter {
    static let schemaVersion = 1
    static let manifestPath = "preferences/lime_prefs.json"
    private static let maxManifestBytes = 1 * 1024 * 1024

    private enum ValueType {
        case bool
        case int
        case string
    }

    private struct Spec {
        let key: String
        let type: ValueType
        let iosSupported: Bool
    }

    private static let specs: [Spec] = [
        Spec(key: "keyboard_theme", type: .int, iosSupported: true),
        Spec(key: "keyboard_size", type: .string, iosSupported: true),
        Spec(key: "font_size", type: .string, iosSupported: true),
        Spec(key: "number_row_in_english", type: .bool, iosSupported: true),
        Spec(key: "show_arrow_key", type: .int, iosSupported: true),
        Spec(key: "split_keyboard_mode", type: .int, iosSupported: true),
        Spec(key: "one_hand_mode", type: .int, iosSupported: true),
        // Issue #169: integrated iPhone portrait mode + landscape split. Backed up
        // alongside the tablet/iPad split key so a backup restored across form factors
        // preserves both the phone and tablet profiles without cross-writing.
        Spec(key: "phone_portrait_keyboard_mode", type: .int, iosSupported: true),
        Spec(key: "phone_landscape_split", type: .bool, iosSupported: true),
        Spec(key: "numpad_anchor", type: .int, iosSupported: true),
        Spec(key: "vibrate_on_keypress", type: .bool, iosSupported: true),
        Spec(key: "vibrate_level", type: .int, iosSupported: true),
        Spec(key: "sound_on_keypress", type: .bool, iosSupported: true),
        Spec(key: "keypress_sound_volume", type: .string, iosSupported: true),
        Spec(key: "smart_chinese_input", type: .bool, iosSupported: true),
        Spec(key: "auto_chinese_symbol", type: .bool, iosSupported: true),
        Spec(key: "candidate_switch", type: .bool, iosSupported: true),
        Spec(key: "persistent_language_mode", type: .bool, iosSupported: true),
        Spec(key: "enable_emoji_position", type: .int, iosSupported: true),
        Spec(key: "similiar_list", type: .int, iosSupported: true),
        Spec(key: "han_convert_option", type: .int, iosSupported: true),
        Spec(key: "active_im", type: .string, iosSupported: true),   // §1.8 active IM (was keyboard_list)
        Spec(key: "similiar_enable", type: .bool, iosSupported: true),
        Spec(key: "candidate_suggestion", type: .bool, iosSupported: true),
        Spec(key: "learn_phrase", type: .bool, iosSupported: true),
        Spec(key: "learning_switch", type: .bool, iosSupported: true),
        Spec(key: "english_dictionary_enable", type: .bool, iosSupported: true),
        Spec(key: "auto_cap", type: .bool, iosSupported: true),
        Spec(key: "custom_im_reverselookup", type: .string, iosSupported: true),
        Spec(key: "cj_im_reverselookup", type: .string, iosSupported: true),
        Spec(key: "scj_im_reverselookup", type: .string, iosSupported: true),
        Spec(key: "cj5_im_reverselookup", type: .string, iosSupported: true),
        Spec(key: "ecj_im_reverselookup", type: .string, iosSupported: true),
        Spec(key: "dayi_im_reverselookup", type: .string, iosSupported: true),
        Spec(key: "bpmf_im_reverselookup", type: .string, iosSupported: true),
        Spec(key: "phonetic_im_reverselookup", type: .string, iosSupported: true),
        Spec(key: "ez_im_reverselookup", type: .string, iosSupported: true),
        Spec(key: "array_im_reverselookup", type: .string, iosSupported: true),
        Spec(key: "array10_im_reverselookup", type: .string, iosSupported: true),
        Spec(key: "wb_im_reverselookup", type: .string, iosSupported: true),
        Spec(key: "hs_im_reverselookup", type: .string, iosSupported: true),
        Spec(key: "pinyin_im_reverselookup", type: .string, iosSupported: true),
        Spec(key: "phonetic_keyboard_type", type: .string, iosSupported: true),
        Spec(key: "auto_commit", type: .int, iosSupported: true),
        Spec(key: "accept_number_index", type: .bool, iosSupported: true),
        Spec(key: "accept_symbol_index", type: .bool, iosSupported: true),
        Spec(key: "hide_software_keyboard_typing_with_physical", type: .bool, iosSupported: false),
        Spec(key: "switch_english_mode", type: .bool, iosSupported: false),
        Spec(key: "switch_english_mode_shift", type: .bool, iosSupported: false),
        Spec(key: "disable_physical_selkey", type: .bool, iosSupported: false),
        Spec(key: "selkey_option", type: .int, iosSupported: false),
        Spec(key: "english_dictionary_physical_keyboard", type: .bool, iosSupported: false),
        Spec(key: "physical_keyboard_sort", type: .bool, iosSupported: false)
    ]

    static func exportManifestData(
        defaults: UserDefaults,
        sourcePlatform: String,
        standardDefaults: UserDefaults = .standard
    ) throws -> Data {
        var preferences: [String: Any] = [:]
        for spec in specs where spec.iosSupported {
            let sourceDefaults = defaults.object(forKey: spec.key) != nil ? defaults : standardDefaults
            guard sourceDefaults.object(forKey: spec.key) != nil else { continue }
            switch spec.type {
            case .bool:
                preferences[spec.key] = sourceDefaults.bool(forKey: spec.key)
            case .int:
                preferences[spec.key] = sourceDefaults.integer(forKey: spec.key)
            case .string:
                if let value = sourceDefaults.string(forKey: spec.key) {
                    preferences[spec.key] = value
                }
            }
        }
        for (key, value) in standardDefaults.dictionaryRepresentation() {
            guard isSupportedDynamicKey(key), preferences[key] == nil else { continue }
            if let boolValue = value as? Bool {
                preferences[key] = boolValue
            }
        }

        let root: [String: Any] = [
            "schema": schemaVersion,
            "sourcePlatform": sourcePlatform,
            "preferences": preferences
        ]
        return try JSONSerialization.data(withJSONObject: root, options: [.sortedKeys])
    }

    @discardableResult
    static func restoreManifestData(
        _ data: Data,
        defaults: UserDefaults,
        standardDefaults: UserDefaults = .standard
    ) throws -> Bool {
        guard data.count <= maxManifestBytes else { return false }
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              root["schema"] as? Int == schemaVersion,
              let preferences = root["preferences"] as? [String: Any] else {
            return false
        }

        for spec in specs where spec.iosSupported {
            guard let value = preferences[spec.key] else { continue }
            switch spec.type {
            case .bool:
                if let boolValue = value as? Bool {
                    defaults.set(boolValue, forKey: spec.key)
                }
            case .int:
                if let intValue = value as? Int {
                    defaults.set(intValue, forKey: spec.key)
                }
            case .string:
                if let stringValue = value as? String {
                    defaults.set(stringValue, forKey: spec.key)
                }
            }
        }
        for (key, value) in preferences where isSupportedDynamicKey(key) {
            if let boolValue = value as? Bool {
                standardDefaults.set(boolValue, forKey: key)
            }
        }
        // Issue #169: old manifests contain only the separate split/one-hand keys. Derive
        // canonical phone keys during restore even when new-key defaults already exist locally.
        // Legacy iOS split was iPad-only; only an Android source may infer phone split from it.
        let hasLegacyGeometry = preferences["split_keyboard_mode"] != nil
            || preferences["one_hand_mode"] != nil
        if hasLegacyGeometry {
            let legacySplit = preferences["split_keyboard_mode"] as? Int ?? 0
            let legacyOneHand = preferences["one_hand_mode"] as? Int ?? 0
            let legacyPhoneSplitSupported = (root["sourcePlatform"] as? String)?.lowercased() != "ios"
            if preferences["phone_portrait_keyboard_mode"] == nil {
                let migrated = PhoneKeyboardModePolicy.migratePortraitMode(
                    legacyOneHand: legacyOneHand,
                    legacySplit: legacySplit,
                    legacyPhoneSplitSupported: legacyPhoneSplitSupported)
                defaults.set(migrated.rawValue, forKey: "phone_portrait_keyboard_mode")
            }
            if preferences["phone_landscape_split"] == nil {
                let migrated = PhoneKeyboardModePolicy.migrateLandscapeSplit(
                    legacySplit: legacySplit,
                    legacyPhoneSplitSupported: legacyPhoneSplitSupported)
                defaults.set(migrated, forKey: "phone_landscape_split")
            }
        }
        defaults.synchronize()
        standardDefaults.synchronize()
        return true
    }

    private static func isSupportedDynamicKey(_ key: String) -> Bool {
        key.hasPrefix("backup_on_delete_") || key.hasPrefix("restore_on_import_")
    }
}
