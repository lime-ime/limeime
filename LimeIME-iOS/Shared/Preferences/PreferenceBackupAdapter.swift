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
        defaults.synchronize()
        standardDefaults.synchronize()
        return true
    }

    private static func isSupportedDynamicKey(_ key: String) -> Bool {
        key.hasPrefix("backup_on_delete_") || key.hasPrefix("restore_on_import_")
    }
}
