// PreferencesTabView.swift
// LimeIME-iOS
//
// IM Preferences — all 11 sections with @AppStorage(store: sharedDefaults).
// Spec §8.

import SwiftUI

// MARK: - PreferencesTabView

struct PreferencesTabView: View {

    // MARK: §8.1 Keyboard Appearance
    @AppStorage("keyboard_theme",          store: sharedDefaults) private var keyboardTheme: Int = 6
    @AppStorage("keyboard_size",           store: sharedDefaults) private var keyboardSize: String = "1"
    @AppStorage("font_size",               store: sharedDefaults) private var fontSize: String = "1"
    @AppStorage("number_row_in_english",   store: sharedDefaults) private var numberRowInEnglish: Bool = true
    @AppStorage("show_arrow_key",          store: sharedDefaults) private var showArrowKey: Int = 0
    @AppStorage("split_keyboard_mode",     store: sharedDefaults) private var splitKeyboardMode: Int = 0

    // MARK: §8.2 Keyboard Feedback
    @AppStorage("vibrate_on_keypress",     store: sharedDefaults) private var vibrateOnKeypress: Bool = true
    @AppStorage("vibrate_level",           store: sharedDefaults) private var vibrateLevel: Int = 40
    @AppStorage("sound_on_keypress",       store: sharedDefaults) private var soundOnKeypress: Bool = false

    // MARK: §8.4 IM Behaviour
    @AppStorage("smart_chinese_input",      store: sharedDefaults) private var smartChineseInput: Bool = true
    @AppStorage("auto_chinese_symbol",      store: sharedDefaults) private var autoChineseSymbol: Bool = false
    @AppStorage("candidate_switch",         store: sharedDefaults) private var candidateSwitch: Bool = true
    @AppStorage("persistent_language_mode", store: sharedDefaults) private var persistentLanguageMode: Bool = false
    @AppStorage("enable_emoji_position",    store: sharedDefaults) private var emojiPosition: Int = 5

    // §5.2.2 "鍵盤類型" (phonetic_keyboard_type) is IM-specific — rendered in
    // IMDetailView for the phonetic IM only, not in the Preferences tab.

    // MARK: §8.5 Han Conversion
    @AppStorage("han_convert_option",     store: sharedDefaults) private var hanConvertOption: Int = 0

    // MARK: §8.6 Related Phrases & Learning
    @AppStorage("similiar_enable",        store: sharedDefaults) private var similiarEnable: Bool = true
    @AppStorage("similiar_list",          store: sharedDefaults) private var similiarList: Int = 20
    @AppStorage("candidate_suggestion",   store: sharedDefaults) private var candidateSuggestion: Bool = true
    @AppStorage("learn_phrase",           store: sharedDefaults) private var learnPhrase: Bool = true
    @AppStorage("learning_switch",        store: sharedDefaults) private var learningSwitch: Bool = true

    // MARK: §8.7 English Keyboard
    @AppStorage("english_dictionary_enable", store: sharedDefaults) private var englishDictEnable: Bool = true
    @AppStorage("auto_cap",                  store: sharedDefaults) private var autoCap: Bool = true

    // MARK: Options

    // Value 6 = 系統設定 (follows system light/dark appearance).
    private let themeOptions    = [0, 1, 2, 3, 4, 5, 6]
    private let themeLabels     = ["淺色", "深色", "粉紅", "科技藍", "時尚紫", "放鬆綠", "系統設定"]
    private let sizeOptions     = ["1.2", "1.1", "1", "0.9", "0.8"]
    private let sizeLabels      = ["特大", "大", "一般", "小", "特小"]
    private let arrowOptions    = [0, 1, 2]
    private let arrowLabels     = ["無", "軟鍵盤上方", "軟鍵盤下方"]
    private let splitOptions    = [0, 1, 2]
    private let splitLabels     = ["關閉", "開啟", "僅橫向開啟"]
    private let vibLevelOptions = [10, 20, 40, 60, 80]
    private let vibLevelLabels  = ["特弱", "弱", "中", "強", "特強"]
    private let hanOptions      = [0, 1, 2]
    private let hanLabels       = ["無", "繁轉簡", "簡轉繁"]
    private let similiarOpts    = [0, 10, 20, 30, 40, 50]

    @ViewBuilder
    private func prefRow(_ title: String, _ desc: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
            Text(desc)
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }

    var body: some View {
        // Single-column NavigationStack on all size classes. The only nested
        // destination is `ReverseLookupSettingsView` (reached via NavigationLink
        // in the §8.4 section), so a split-view secondary column would just
        // show an empty placeholder most of the time on iPad. NavigationStack
        // gives a clean push-pop flow that matches the iPhone behaviour.
        NavigationStack {
            VStack(spacing: 0) {
                // Static page title, left-aligned to the 560pt content column
                // edge. Sits above the scrolling Form so it never shrinks /
                // animates on scroll the way `.navigationTitle(.large)` does.
                Text("喜好設定")
                    .font(.largeTitle.bold())
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .padding(.bottom, 8)

                Form {
                // MARK: §8.1
                Section(header: Text("鍵盤外觀")) {
                    Picker("鍵盤樣式", selection: $keyboardTheme) {
                        ForEach(0..<themeOptions.count, id: \.self) { i in
                            Text(themeLabels[i]).tag(themeOptions[i])
                        }
                    }
                    Picker("鍵盤大小", selection: $keyboardSize) {
                        ForEach(0..<sizeOptions.count, id: \.self) { i in
                            Text(sizeLabels[i]).tag(sizeOptions[i])
                        }
                    }
                    Picker("字型大小", selection: $fontSize) {
                        ForEach(0..<sizeOptions.count, id: \.self) { i in
                            Text(sizeLabels[i]).tag(sizeOptions[i])
                        }
                    }
                    if UIDevice.current.userInterfaceIdiom != .pad {
                        Toggle(isOn: $numberRowInEnglish) { prefRow("數字列英文鍵盤", "在英文鍵盤顯示數字列(5列鍵盤)") }
                    }
                    Picker("顯示方向鍵", selection: $showArrowKey) {
                        ForEach(0..<arrowOptions.count, id: \.self) { i in
                            Text(arrowLabels[i]).tag(arrowOptions[i])
                        }
                    }
                    // iPad-only split keyboard
                    if UIDevice.current.userInterfaceIdiom == .pad {
                        Picker("分離鍵盤", selection: $splitKeyboardMode) {
                            ForEach(0..<splitOptions.count, id: \.self) { i in
                                Text(splitLabels[i]).tag(splitOptions[i])
                            }
                        }
                    }
                }
                .setupMatchedSectionBlock()

                // MARK: §8.2
                Section(header: Text("鍵盤回饋")) {
                    Toggle("打字震動", isOn: $vibrateOnKeypress)
                    Picker("震動強度", selection: $vibrateLevel) {
                        ForEach(0..<vibLevelOptions.count, id: \.self) { i in
                            Text(vibLevelLabels[i]).tag(vibLevelOptions[i])
                        }
                    }
                    .disabled(!vibrateOnKeypress)
                    Toggle("打字音效", isOn: $soundOnKeypress)
                }
                .setupMatchedSectionBlock()

                // MARK: §8.4
                Section(header: Text("輸入法行為")) {
                    Toggle(isOn: $smartChineseInput) { prefRow("開啟中文智慧選字", "部份輸入法可能會影響中英混打功能") }
                    Toggle(isOn: $autoChineseSymbol) { prefRow("自動中文標點模式", "無候選字詞時顯示中文標點選項") }
                    Toggle(isOn: $persistentLanguageMode) { prefRow("記憶中英模式", "下次切換前保持中英模式") }
                    Picker("設定 EMOJI 候選列顯示位置", selection: $emojiPosition) {
                        Text("不顯示 Emoji 候選字").tag(0)
                        ForEach(2...10, id: \.self) { pos in
                            Text("第 \(pos) 候選字後顯示").tag(pos)
                        }
                    }
                    Picker("建議字顯示數量", selection: $similiarList) {
                        ForEach(similiarOpts, id: \.self) { v in
                            Text(v == 0 ? "關閉" : "\(v)").tag(v)
                        }
                    }
                    .disabled(!similiarEnable)
                    NavigationLink(destination: ReverseLookupSettingsView()) {
                        Label("字根反查設定", systemImage: "magnifyingglass")
                    }
                }
                .setupMatchedSectionBlock()

                // §5.2.2 — 鍵盤類型 lives in IMDetailView for the phonetic IM, not here.

                // MARK: §8.5
                Section(header: Text("簡繁轉換")) {
                    Picker("中文簡/繁體字碼轉換", selection: $hanConvertOption) {
                        ForEach(0..<hanOptions.count, id: \.self) { i in
                            Text(hanLabels[i]).tag(hanOptions[i])
                        }
                    }
                    .pickerStyle(.segmented)
                }
                .setupMatchedSectionBlock()

                // MARK: §8.6
                Section(header: Text("關聯字與學習")) {
                    Toggle(isOn: $similiarEnable) { prefRow("啟用關聯字庫", "啟用關聯字庫功能") }
                    Toggle(isOn: $candidateSuggestion) { prefRow("啟動自建關聯字", "依輸入文字自動建立關聯字") }
                    Toggle(isOn: $learnPhrase) { prefRow("自動學習新詞", "從常用關聯字學習新詞") }
                    Toggle(isOn: $learningSwitch) { prefRow("啟動選取排序", "依選取次數排序選字清單") }
                }
                .setupMatchedSectionBlock()

                // MARK: §8.7
                Section(header: Text("英文鍵盤")) {
                    Toggle(isOn: $englishDictEnable) { prefRow("啟用英文字典", "當使用 英文 輸入模式時，顯示英文建議字") }
                    Toggle(isOn: $autoCap) { prefRow("首字自動大寫", "在英文模式下，句首字母自動轉為大寫") }
                }
                .setupMatchedSectionBlock()

                }
                .setupMatchedGroupedSurface()
            }
            // iPad / wide-screen reading-width cap. Same 560pt width that
            // SetupTabView and DBManagerView use so the Preferences form
            // doesn't stretch edge-to-edge on iPad portrait/landscape. On
            // iPhone this never engages because the screen is < 560pt.
            .frame(maxWidth: 560)
            .frame(maxWidth: .infinity)
            // Hide the system navigation bar so the custom static title above
            // is the only one on screen. This view is a tab root (no back
            // navigation needed at this level); pushed destinations like
            // ReverseLookupSettingsView declare their own nav bar with a
            // back button.
            .toolbar(.hidden, for: .navigationBar)
            .onAppear(perform: migrateRemovedPreferences)
        }
    }

    private func migrateRemovedPreferences() {
        guard sharedDefaults.object(forKey: "enable_emoji") != nil else { return }
        if sharedDefaults.bool(forKey: "enable_emoji") == false {
            emojiPosition = 0
        }
        sharedDefaults.removeObject(forKey: "enable_emoji")
    }

}
