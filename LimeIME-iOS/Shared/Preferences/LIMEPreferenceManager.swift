// LIMEPreferenceManager.swift
// LimeIME-iOS
//
// Model layer: typed accessors for all shared UserDefaults preference keys.
// Port of Android LIMEPreferenceManager.java.
// All values stored in the shared App Group suite so the keyboard extension
// can read them without IPC.

import Foundation

// MARK: - LIMEPreferenceManager

final class LIMEPreferenceManager {

    struct ReverseLookupOption: Equatable {
        let value: String
        let label: String
    }

    // MARK: - Singleton

    static let shared = LIMEPreferenceManager()

    // MARK: - Constants

    static let suiteName = "group.org.limeime"

    // MARK: - Storage

    private let defaults: UserDefaults

    // MARK: - Init

    /// Designated initialiser — uses the shared App Group suite.
    init() {
        defaults = UserDefaults(suiteName: LIMEPreferenceManager.suiteName)
            ?? UserDefaults.standard
    }

    /// Test helper: inject any UserDefaults instance.
    init(defaults: UserDefaults) {
        self.defaults = defaults
    }

    // MARK: - Generic helpers

    private func intValue(_ key: String, default defaultValue: Int) -> Int {
        guard defaults.object(forKey: key) != nil else { return defaultValue }
        return defaults.integer(forKey: key)
    }

    private func boolValue(_ key: String, default defaultValue: Bool) -> Bool {
        guard defaults.object(forKey: key) != nil else { return defaultValue }
        return defaults.bool(forKey: key)
    }

    private func stringValue(_ key: String, default defaultValue: String) -> String {
        return defaults.string(forKey: key) ?? defaultValue
    }

    private func doubleValue(_ key: String, default defaultValue: Double) -> Double {
        guard defaults.object(forKey: key) != nil else { return defaultValue }
        return defaults.double(forKey: key)
    }

    // MARK: - §8.1 Keyboard Appearance

    /// Keyboard colour palette index.
    /// Values 0–5 are explicit palettes (淺色/深色/粉紅/科技藍/時尚紫/放鬆綠).
    /// Value 6 is "系統設定": callers in the keyboard extension must map it to 0 (light)
    /// or 1 (dark) based on the current system appearance.
    var keyboardTheme: Int {
        get { intValue("keyboard_theme", default: 6) }
        set { defaults.set(newValue, forKey: "keyboard_theme") }
    }

    var keyboardSize: String {
        get { stringValue("keyboard_size", default: "1") }
        set { defaults.set(newValue, forKey: "keyboard_size") }
    }

    var fontSize: String {
        get { stringValue("font_size", default: "1") }
        set { defaults.set(newValue, forKey: "font_size") }
    }

    /// Raw candidate font size in points (14–28). Separate from font_size scale string.
    var candidateFontSize: Double {
        get { doubleValue("candidateFontSize", default: 18) }
        set { defaults.set(newValue, forKey: "candidateFontSize") }
    }

    var numberRowInEnglish: Bool {
        get { boolValue("number_row_in_english", default: true) }
        set { defaults.set(newValue, forKey: "number_row_in_english") }
    }

    var showArrowKey: Int {
        get { intValue("show_arrow_key", default: 0) }
        set { defaults.set(newValue, forKey: "show_arrow_key") }
    }

    var splitKeyboardMode: Int {
        get { intValue("split_keyboard_mode", default: 0) }
        set { defaults.set(newValue, forKey: "split_keyboard_mode") }
    }

    // MARK: - §8.2 Keyboard Feedback

    var vibrateOnKeypress: Bool {
        get { boolValue("vibrate_on_keypress", default: true) }
        set { defaults.set(newValue, forKey: "vibrate_on_keypress") }
    }

    var vibrateLevel: Int {
        get { intValue("vibrate_level", default: 40) }
        set { defaults.set(newValue, forKey: "vibrate_level") }
    }

    var soundOnKeypress: Bool {
        get { boolValue("sound_on_keypress", default: false) }
        set { defaults.set(newValue, forKey: "sound_on_keypress") }
    }

    // MARK: - §8.4 IM Behaviour

    var smartChineseInput: Bool {
        get { boolValue("smart_chinese_input", default: true) }
        set { defaults.set(newValue, forKey: "smart_chinese_input") }
    }

    var autoChineseSymbol: Bool {
        get { boolValue("auto_chinese_symbol", default: false) }
        set { defaults.set(newValue, forKey: "auto_chinese_symbol") }
    }

    /// Always returns `true`. The UI toggle was removed because free-scroll candidate
    /// selection is the only sensible behaviour on modern iOS / Android; the paged
    /// alternative is not used. The stored UserDefaults value (if any) is ignored.
    var candidateSwitch: Bool {
        get { true }
        set { defaults.set(newValue, forKey: "candidate_switch") }
    }

    var persistentLanguageMode: Bool {
        get { boolValue("persistent_language_mode", default: false) }
        set { defaults.set(newValue, forKey: "persistent_language_mode") }
    }

    var enableEmojiPosition: Int {
        get {
            if defaults.object(forKey: "enable_emoji") != nil {
                if defaults.bool(forKey: "enable_emoji") == false {
                    defaults.set(0, forKey: "enable_emoji_position")
                }
                defaults.removeObject(forKey: "enable_emoji")
            }
            return intValue("enable_emoji_position", default: 5)
        }
        set { defaults.set(newValue, forKey: "enable_emoji_position") }
    }

    // MARK: - §5.2 IMDetailView (per-IM prefs)

    /// §5.2.2 — phonetic IM only.
    var phoneticKeyboardType: String {
        get { stringValue("phonetic_keyboard_type", default: "standard") }
        set { defaults.set(newValue, forKey: "phonetic_keyboard_type") }
    }

    /// array10 only.
    var autoCommit: Int {
        get { intValue("auto_commit", default: 0) }
        set { defaults.set(newValue, forKey: "auto_commit") }
    }

    /// custom IM only.
    var acceptNumberIndex: Bool {
        get { boolValue("accept_number_index", default: false) }
        set { defaults.set(newValue, forKey: "accept_number_index") }
    }

    /// custom IM only.
    var acceptSymbolIndex: Bool {
        get { boolValue("accept_symbol_index", default: false) }
        set { defaults.set(newValue, forKey: "accept_symbol_index") }
    }

    // MARK: - §8.5 Han Conversion

    var hanConvertOption: Int {
        get { intValue("han_convert_option", default: 0) }
        set { defaults.set(newValue, forKey: "han_convert_option") }
    }

    // MARK: - §8.6 Related Phrases & Learning

    var similiarEnable: Bool {
        get { boolValue("similiar_enable", default: true) }
        set { defaults.set(newValue, forKey: "similiar_enable") }
    }

    var similiarList: Int {
        get { intValue("similiar_list", default: 20) }
        set { defaults.set(newValue, forKey: "similiar_list") }
    }

    var candidateSuggestion: Bool {
        get { boolValue("candidate_suggestion", default: true) }
        set { defaults.set(newValue, forKey: "candidate_suggestion") }
    }

    var learnPhrase: Bool {
        get { boolValue("learn_phrase", default: true) }
        set { defaults.set(newValue, forKey: "learn_phrase") }
    }

    var learningSwitch: Bool {
        get { boolValue("learning_switch", default: true) }
        set { defaults.set(newValue, forKey: "learning_switch") }
    }

    // MARK: - §8.7 English Dictionary

    var englishDictionaryEnable: Bool {
        get { boolValue("english_dictionary_enable", default: true) }
        set { defaults.set(newValue, forKey: "english_dictionary_enable") }
    }

    // MARK: - Internal storage state

    /// Stored Chinese/English state: "yes" = English-only, "no" = Chinese (spec §15).
    /// Written by setLanguageMode when persistentLanguageMode is enabled.
    var languageMode: String {
        get { stringValue("language_mode", default: "no") }
        set { defaults.set(newValue, forKey: "language_mode") }
    }

    /// Shadow accessor per spec §10.1: iOS uses `textDocumentProxy.autocapitalizationType`
    /// instead. No UI surface and no runtime callers — kept for cross-platform SharedPreferences parity.
    var autoCap: Bool {
        get { boolValue("auto_cap", default: true) }
        set { defaults.set(newValue, forKey: "auto_cap") }
    }

    // MARK: - §8.4.1 Reverse Lookup (sub-screen)

    static let fallbackReverseLookupOptions: [ReverseLookupOption] = [
        ReverseLookupOption(value: "none", label: "無"),
        ReverseLookupOption(value: "custom", label: "自建"),
        ReverseLookupOption(value: "cj", label: "倉頡"),
        ReverseLookupOption(value: "scj", label: "快倉"),
        ReverseLookupOption(value: "cj5", label: "倉頡五代"),
        ReverseLookupOption(value: "ecj", label: "速成"),
        ReverseLookupOption(value: "dayi", label: "大易"),
        ReverseLookupOption(value: "phonetic", label: "注音"),
        ReverseLookupOption(value: "ez", label: "輕鬆"),
        ReverseLookupOption(value: "array", label: "行列"),
        ReverseLookupOption(value: "array10", label: "行列 10"),
        ReverseLookupOption(value: "wb", label: "筆順五碼"),
        ReverseLookupOption(value: "hs", label: "華象直覺"),
        ReverseLookupOption(value: "pinyin", label: "拼音")
    ]

    static func reverseLookupOptions(from imConfigs: [ImConfig]) -> [ReverseLookupOption] {
        let targets = reverseLookupTargets(from: imConfigs)
        return targets.isEmpty
            ? fallbackReverseLookupOptions
            : [ReverseLookupOption(value: "none", label: "無")] + targets
    }

    static func reverseLookupTargets(from imConfigs: [ImConfig]) -> [ReverseLookupOption] {
        var seen = Set<String>()
        var targets: [ReverseLookupOption] = []
        for config in imConfigs where config.enabled {
            let value = config.tableNick.isEmpty ? config.imName : config.tableNick
            guard !value.isEmpty, value != "emoji", !seen.contains(value) else { continue }
            let label = config.label.isEmpty ? config.imName : config.label
            targets.append(ReverseLookupOption(value: value, label: label))
            seen.insert(value)
        }
        return targets
    }

    static func reverseLookupLabel(for value: String, options: [ReverseLookupOption]) -> String {
        if let label = options.first(where: { $0.value == value })?.label {
            return label
        }
        return fallbackReverseLookupOptions.first(where: { $0.value == value })?.label ?? "無"
    }

    var customImReverselookup: String {
        get { stringValue("custom_im_reverselookup", default: "none") }
        set { defaults.set(newValue, forKey: "custom_im_reverselookup") }
    }

    var cjImReverselookup: String {
        get { stringValue("cj_im_reverselookup", default: "none") }
        set { defaults.set(newValue, forKey: "cj_im_reverselookup") }
    }

    var scjImReverselookup: String {
        get { stringValue("scj_im_reverselookup", default: "none") }
        set { defaults.set(newValue, forKey: "scj_im_reverselookup") }
    }

    var cj5ImReverselookup: String {
        get { stringValue("cj5_im_reverselookup", default: "none") }
        set { defaults.set(newValue, forKey: "cj5_im_reverselookup") }
    }

    var ecjImReverselookup: String {
        get { stringValue("ecj_im_reverselookup", default: "none") }
        set { defaults.set(newValue, forKey: "ecj_im_reverselookup") }
    }

    var dayiImReverselookup: String {
        get { stringValue("dayi_im_reverselookup", default: "none") }
        set { defaults.set(newValue, forKey: "dayi_im_reverselookup") }
    }

    var phoneticImReverselookup: String {
        get { stringValue("phonetic_im_reverselookup", default: "none") }
        set { defaults.set(newValue, forKey: "phonetic_im_reverselookup") }
    }

    var ezImReverselookup: String {
        get { stringValue("ez_im_reverselookup", default: "none") }
        set { defaults.set(newValue, forKey: "ez_im_reverselookup") }
    }

    var arrayImReverselookup: String {
        get { stringValue("array_im_reverselookup", default: "none") }
        set { defaults.set(newValue, forKey: "array_im_reverselookup") }
    }

    var array10ImReverselookup: String {
        get { stringValue("array10_im_reverselookup", default: "none") }
        set { defaults.set(newValue, forKey: "array10_im_reverselookup") }
    }

    var wbImReverselookup: String {
        get { stringValue("wb_im_reverselookup", default: "none") }
        set { defaults.set(newValue, forKey: "wb_im_reverselookup") }
    }

    var hsImReverselookup: String {
        get { stringValue("hs_im_reverselookup", default: "none") }
        set { defaults.set(newValue, forKey: "hs_im_reverselookup") }
    }

    var pinyinImReverselookup: String {
        get { stringValue("pinyin_im_reverselookup", default: "none") }
        set { defaults.set(newValue, forKey: "pinyin_im_reverselookup") }
    }

    func reverseLookup(for tableNick: String) -> String {
        return stringValue(reverseLookupKey(for: tableNick), default: "none")
    }

    func setReverseLookup(_ value: String, for tableNick: String) {
        defaults.set(value.isEmpty ? "none" : value, forKey: reverseLookupKey(for: tableNick))
    }

    private func reverseLookupKey(for tableNick: String) -> String {
        let table = tableNick.isEmpty ? "phonetic" : tableNick
        return "\(table)_im_reverselookup"
    }

    // MARK: - Navigation state

    var keyboardList: String {
        get { stringValue("keyboard_list", default: "phonetic") }
        set { defaults.set(newValue, forKey: "keyboard_list") }
    }

    var keyboardState: String {
        get { stringValue("keyboard_state", default: "") }
        set { defaults.set(newValue, forKey: "keyboard_state") }
    }

    // MARK: - §10.4 syncIMActivatedState

    /// Rebuilds the keyboard_state semicolon-delimited string from im.enabled rows.
    /// Mirrors Android's LIMEPreferenceManager.syncIMActivatedState().
    func syncIMActivatedState(dbServer: DBServer) {
        guard let configs = try? dbServer.getAllImConfigs() else { return }
        let enabledConfigs = configs.enumerated()
            .filter { $0.element.enabled }
        keyboardState = enabledConfigs
            .map { "\($0.offset)" }
            .joined(separator: ";")

        // Keep the current IM preference coherent with the newly synced enabled list.
        // Cloud/download installs run from the Settings process; without this, the
        // keyboard extension can warm-start with a stale/default keyboard_list value
        // while setupDatabase() resolves a different active IM from keyboard_state (#121).
        let enabledTableNicks = Set(enabledConfigs.map { $0.element.tableNick })
        let current = keyboardList
        if let firstEnabled = enabledConfigs.first?.element.tableNick,
           (current.isEmpty || !enabledTableNicks.contains(current)) {
            keyboardList = firstEnabled
        }
    }
}
