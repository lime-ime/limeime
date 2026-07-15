# LimeIME Preference Table — iOS Spec vs Android Pre/Post Back-port

3-way comparison of every preference item in the LimeIME settings tree across:

1. **iOS current/spec** — `LimeIME-iOS/LimeSettings/Views/PreferencesTabView.swift`, aligned to `docs/LIME_SETTINGS.md` §8 and Android current preference strings
2. **Android pre-back-port** — commit `6791ab7b` (parent of the "Step 3" back-port series), three flat `PreferenceCategory` blocks (`lime_keyboard` / `lime_im` / `lime_mapping`)
3. **Android current** — HEAD `app/src/main/res/xml/preference.xml`, 7 sectioned categories + 1 nested sub-screen, aligned to iOS §8.1–§8.7 (with §8.3 and §8.8 consolidated away, §8.9 folded into §8.4.1; `auto_cap` moved into §8.7)

## Legend

- **Default** uses the `android:defaultValue` literal (strings keep their XML quoting); empty cell = no default declared
- **Category** = the PreferenceCategory `android:key` (Android) or the iOS section heading
- `pref_section_*` category keys + the reverse-lookup sub-screen wrapper strings are NEW in the back-port
- Items moved between categories show their cross-source section number in **bold** as a hint
- "Function" column: one-line behavioural summary

---

## 8.1 Keyboard Appearance (鍵盤外觀)

| Pref Key | Type | iOS (default · label · category) | Android pre-back-port (default · label · category) | Android current (default · label · category) | Function |
|---|---|---|---|---|---|
| `keyboard_theme` | Picker / ListPreference | 6 · 鍵盤樣式 · §8.1 鍵盤外觀 | "0" · 鍵盤樣式 · lime_keyboard | "6" · 鍵盤樣式 · pref_section_appearance | Light / dark / coloured keyboard theme. Value 6 follows the system Light/Dark appearance on both iOS and Android current. |
| `keyboard_size` | Picker / ListPreference | "1" · 鍵盤大小 · §8.1 鍵盤外觀 | "1" · 鍵盤大小 · lime_keyboard | "1" · 鍵盤大小 · pref_section_appearance | Scale factor for keyboard layout. Default `"1"` (一般) on both iOS and Android. |
| `font_size` | Picker / ListPreference | "1" · 字型大小 · §8.1 鍵盤外觀 | **"1"** · 字型大小 · lime_keyboard | "1" · 字型大小 · pref_section_appearance | Candidate-strip font scale. Moved from §8.3 into §8.1 (UI grouping). |
| `number_row_in_english` | Toggle / CheckBox | true · 數字列英文鍵盤 · §8.1 鍵盤外觀 (**iPhone-only** — hidden on iPad) | true · 數字列英文鍵盤 · lime_keyboard | true · 數字列英文鍵盤 · pref_section_appearance | Digit row in English layout. Moved from §8.3 into §8.1 (UI grouping). iOS gates to iPhone in `PreferencesTabView.swift`. |
| `show_arrow_key` | Picker / ListPreference | 0 · 顯示方向鍵 · §8.1 鍵盤外觀 | "0" · 顯示方向鍵 · lime_keyboard | "0" · 顯示方向鍵 · pref_section_appearance | 無 / 軟鍵盤上方 / 軟鍵盤下方. |
| `split_keyboard_mode` | Picker / ListPreference | 0 · 分離鍵盤 · §8.1 鍵盤外觀 (iPad-only) | "0" · 分離鍵盤 · lime_keyboard | "0" · 分離鍵盤 · pref_section_appearance | 0=關閉, 1=開啟, 2=僅橫向開啟. |

## 8.2 Keyboard Feedback (鍵盤回饋)

| Pref Key | Type | iOS (default · label · category) | Android pre-back-port (default · label · category) | Android current (default · label · category) | Function |
|---|---|---|---|---|---|
| `vibrate_on_keypress` | Toggle / CheckBox | true · 打字震動 · §8.2 鍵盤回饋 | true · 打字震動 · lime_keyboard | true · 打字震動 · pref_section_feedback | Haptic feedback on every keypress. |
| `vibrate_level` | Picker / ListPreference | 40 · 震動強度 · §8.2 鍵盤回饋 | "40" · 震動強度 · lime_keyboard | "40" · 震動強度 · pref_section_feedback | 10 / 20 / 40 / 60 / 80 ms; hidden on API 31+ (system haptics replace it). |
| `sound_on_keypress` | Toggle / CheckBox | false · 打字音效 · §8.2 鍵盤回饋 | false · 打字音效 · lime_keyboard | false · 打字音效 · pref_section_feedback | Audible key-click. |
| `keypress_sound_volume` | Picker / ListPreference | "-1" · 打字音量 · §8.2 鍵盤回饋 · `dependency=sound_on_keypress` | *(new)* | "-1" · 打字音量 · pref_section_feedback · `dependency=sound_on_keypress` | Keypress-sound attenuation. `-1` (系統預設) keeps the platform default click path. `0.10`/`0.25`/`0.50`/`0.75`/`1.00` use explicit scalar volume: Android calls two-arg `playSoundEffect(sound, scalar)`; iOS uses a LIME-owned click sample through `AVAudioPlayer.volume`. Added because platform keyboard clicks expose no app-level volume slider. |

## 8.4 IM Behaviour (輸入法行為)

| Pref Key | Type | iOS (default · label · category) | Android pre-back-port (default · label · category) | Android current (default · label · category) | Function |
|---|---|---|---|---|---|
| `smart_chinese_input` | Toggle / CheckBox | true · 開啟中文智慧組詞 · §8.4 輸入法行為 · subtext=部份輸入法可能會影響中英混打功能 | false · 開啟中文智慧組詞 · lime_im · summary=部份輸入法可能會影響中英混打功能 | true · 開啟中文智慧組詞 · pref_section_im_behaviour · summary=部份輸入法可能會影響中英混打功能 | Smart phrase composition. Defaults reconciled to `true` on both iOS and Android current; pre-back-port Android was `false`. |
| `auto_chinese_symbol` | Toggle / CheckBox | false · 自動中文標點模式 · §8.4 輸入法行為 · subtext=無候選字詞時顯示中文標點選項 | false · 自動中文標點模式 · lime_im · summary=無候選字詞時顯示中文標點選項 | false · 自動中文標點模式 · pref_section_im_behaviour · summary=無候選字詞時顯示中文標點選項 | Auto-insert Chinese punctuation when no candidate. |
| `candidate_switch` | *(hidden — no UI)* | always `true` (UI toggle removed; getter forced to `true` in `LIMEPreferenceManager`) | true · 滑動選取 · **lime_mapping (§8.6)** · summary=滑動選取輸入法建議文字 | *(hidden — no UI)* · always `true` (UI removed from `pref_section_im_behaviour`; `LIMEPreferenceManager.getSelectDefaultOnSliding()` returns `true`) | Swipe vs paged candidate selection. The paged alternative is obsolete on modern iOS/Android, so the toggle was removed and the value is hardcoded to `true` (free-scroll). Stored UserDefaults / SharedPreferences entry is ignored. |
| `persistent_language_mode` | Toggle / CheckBox | false · 記憶中英模式 · §8.4 輸入法行為 · subtext=下次切換前保持中英模式 | false · 記憶中英模式 · **lime_keyboard (§8.1)** · summary=下次切換前保持中英模式 | false · 記憶中英模式 · pref_section_im_behaviour | Persist CN/EN mode across app focus. Re-classified from §8.8 to §8.4 (UI grouping). |
| `enable_emoji_position` | Picker / ListPreference | 5 · 設定 EMOJI 候選列顯示位置 · §8.4 輸入法行為 · options=0 不顯示, 2–10 第 N 候選字後顯示 | "3" · 設定 EMOJI 候選列顯示位置 · lime_keyboard | "5" · 設定 EMOJI 候選列顯示位置 · pref_section_im_behaviour | Position index of emoji in candidate strip; default 5. Value 0 disables inline emoji candidates. When comma/period full-width Chinese punctuation is present at that slot, emoji insertion moves after it so punctuation stays before emoji. |
| `similiar_list` | Picker / ListPreference | 20 · 建議字顯示數量 · §8.4 輸入法行為 · drives `similarCodeCandidatesCap` UNGATED (Android parity; `similiar_enable` does NOT gate it — #5 / `8a338e7b`) | "20" · 建議字顯示數量 · **lime_mapping (§8.6)** | "20" · 建議字顯示數量 · pref_section_im_behaviour | Suggestion-count limit: 0 / 10 / 20 / 30 / 40 / 50. Re-categorised from §8.6 to §8.4 on both iOS and Android current; pre-back-port Android kept it in §8.6 (lime_mapping). |
| `reverse_lookup_screen` | Drill-down / PreferenceScreen | *(screen)* · 字根反查設定 · §8.4.1 字根反查設定 | *(flat entries in lime_im; no wrapper)* | *(screen)* · 字根反查設定 · pref_section_im_behaviour | Opens the reverse-lookup sub-screen. Last item in §8.4 on both platforms. |

## 8.5 Han Conversion (簡繁轉換)

| Pref Key | Type | iOS (default · label · category) | Android pre-back-port (default · label · category) | Android current (default · label · category) | Function |
|---|---|---|---|---|---|
| `han_convert_option` | Picker (`.segmented` on iOS) / ListPreference | 0 · 中文簡/繁體字碼轉換 · §8.5 簡繁轉換 | "0" · 中文簡/繁體字碼轉換 · lime_im | "0" · 中文簡/繁體字碼轉換 · pref_section_han_convert | 0=無, 1=繁轉簡, 2=簡轉繁. |

## 8.6 Related Phrases & Learning (關聯字與學習)

| Pref Key | Type | iOS (default · label · category) | Android pre-back-port (default · label · category) | Android current (default · label · category) | Function |
|---|---|---|---|---|---|
| `similiar_enable` | Toggle / CheckBox | true · 啟用關聯字庫 · §8.6 關聯字與學習 | true · 啟用關聯字庫 · lime_mapping · summary=啟用關聯字庫功能 | true · 啟用關聯字庫 · pref_section_related_learning | Toggle related-phrase dictionary. Aligned to iOS §8.6 on both platforms. |
| `candidate_suggestion` | Toggle / CheckBox | true · 啟動自建關聯字 · §8.6 關聯字與學習 · subtext=依輸入文字自動建立關聯字 | true · 啟動自建關聯字 · lime_mapping · summary=依輸入文字自動建立關聯字 | true · 啟動自建關聯字 · pref_section_related_learning | Auto-build related phrases from typed sequences. |
| `learn_phrase` | Toggle / CheckBox | true · 自動學習新詞 · §8.6 關聯字與學習 | true · 自動學習新詞 · lime_mapping · summary=從常用關聯字學習新詞 | true · 自動學習新詞 · pref_section_related_learning | Promote frequent phrases into the dictionary. |
| `learning_switch` | Toggle / CheckBox | true · 啟動選取排序 · §8.6 關聯字與學習 | true · 啟動選取排序 · lime_mapping · summary=依選取次數排序選字清單 | true · 啟動選取排序 · pref_section_related_learning | Sort candidate list by selection frequency. |

## 8.7 English Keyboard (英文鍵盤)

| Pref Key | Type | iOS (default · label · category) | Android pre-back-port (default · label · category) | Android current (default · label · category) | Function |
|---|---|---|---|---|---|
| `english_dictionary_enable` | Toggle / CheckBox | true · 啟用英文字典 · §8.7 英文鍵盤 · subtext=當使用 英文 輸入模式時，顯示英文建議字 | true · 啟用英文字典 · lime_mapping · summary=當使用 英文 輸入模式時，顯示英文建議字 | true · 啟用英文字典 · pref_section_english_dictionary | Show English suggestions while in English IM mode. |
| `auto_cap` | Toggle / CheckBox | true · 首字自動大寫 · §8.7 英文鍵盤 · subtext=在英文模式下，句首字母自動轉為大寫 | true · 首字自動大寫 · lime_keyboard · summary=英文段落輸入首字自動大寫 *(legacy XML entry, not shown in pre-back-port lime_keyboard category)* | true · 首字自動大寫 · pref_section_english_dictionary · summary=英文段落輸入首字自動大寫 | Auto-capitalize the first letter of English sentences. iOS gates `updateShiftForAutoCap()` (which reads `textDocumentProxy.autocapitalizationType`) on this pref. Android `LIMEService.loadSettings()` reads via `LIMEPreferenceManager.getAutoCaptalization()`. |

## 5.2 IMDetailView — per-IM prefs (cross-listed)

> These prefs are not in the Preferences tab. They live inside the IM Detail page, gated by `tableNick`. Listed here for completeness.
> IM Detail `版本` is canonical in the `im` table as `title = "version"` and `desc = <version text>`. The legacy `{table}mapping_version` preference is retained only as a display fallback for older installs, followed by `im.source` and then `im.name`. New `.lime` and `.cin` imports populate `im.version` from `@version@` and `%version`; legacy `%cname` is used only as a fallback when `%version` is missing.

| Pref Key | Type | iOS (default · label · gating) | Android pre-back-port (default · label · category) | Android current (default · label · placement) | Function |
|---|---|---|---|---|---|
| `accept_number_index` | Toggle / CheckBox | false · 啟動數字對應 · IMDetailView (custom-IM only) | *(no defaultValue)* · 啟動數字對應 · lime_mapping · summary=允許使用數字為輸入法字根 | false · 啟動數字對應 · ImDetailFragment (custom-IM only) | Custom-IM only on both platforms. Surfaces inside IMDetailView, not in Preferences tab. Removed from `preference.xml` in the back-port; re-surfaced in `ImDetailFragment` per ANDROID_BACKPORT_GAP.md §2.2/P1.3. |
| `accept_symbol_index` | Toggle / CheckBox | false · 啟動符號對應 · IMDetailView (custom-IM only) | *(no defaultValue)* · 啟動符號對應 · lime_mapping · summary=允許使用符號為輸入法字根 | false · 啟動符號對應 · ImDetailFragment (custom-IM only) | Same as above. Custom-IM only; moved to IMDetailView. |
| `auto_commit` | Picker / ListPreference | 0 · 電話鍵盤自動上屏 · IMDetailView (array10 only) | "0" · 電話鍵盤自動上屏 · lime_im | "0" · 電話鍵盤自動上屏 · ImDetailFragment (array10 only) | array10 phone-numpad auto-commit. Surfaces in IM Detail when `tableNick == "array10"` on both platforms (`ImDetailFragment.java:193-218` + `IMDetailView.swift:108-122`). |
| `phonetic_keyboard_type` | Picker / ListPreference | "standard" · 鍵盤類型 · IMDetailView (phonetic only) | "standard" · 注音鍵盤選項 · lime_im | "standard" · 注音鍵盤選項 · ImDetailFragment (phonetic only) | Phonetic soft-keyboard variant (standard/HSU/ETEN26/…). Surfaces in IM Detail when `tableNick == "phonetic"` on both platforms (`ImDetailFragment.java:220+` + `IMDetailView.swift:94`). |
| `backup_on_delete_{tableNick}` | Toggle / CheckBox | true · 刪除時備份已學習記錄 · IMDetailView (per-IM, not for related) | *(new)* | true · 刪除時備份已學習記錄 · ImDetailFragment (per-IM) | Per-IM. Stored in `UserDefaults.standard` / default `SharedPreferences` (not the App Group / shared keyboard prefs). Controls whether learned records are backed up before `clearTable`. |
| `restore_on_import_{tableNick}` | Toggle / CheckBox | true · 還原已學習記錄 · IMInstallView (per-IM) | *(new)* | true · 還原已學習記錄 · ImInstallFragment (per-IM) | Per-IM. Same storage tier as `backup_on_delete_*`. Controls whether backed-up records are automatically restored after re-import/re-download. |
| `cj4_semicolon_key` | Toggle / CheckBox | false · 顯示分號（；）鍵 · IMDetailView (cj4 only) | *(new)* | false · 顯示分號（；）鍵 · ImDetailFragment (cj4 only) | cj4 only. When on, adds a `;` key (code 59) to the Cangjie asdf row (programmatic: iPhone/Android append at right end, iPad rewrites `；|：` to `:|;`) and forces `hasSymbolMapping` for cj4 so `;` composes as a root. Stored in the shared App-Group prefs (iOS `sharedDefaults`) / default `SharedPreferences` (Android) so the keyboard reads it — unlike `backup_on_delete_*` which is settings-only. |

## 8.4.1 Reverse Lookup (字根反查設定) — Sub-screen

In iOS this is a drill-down picker in the Preferences tab. In Android pre-back-port it was a flat list of 13 ListPreferences inside `lime_im`. In Android current the 13 entries are wrapped in `PreferenceScreen key="reverse_lookup_screen"` (title="字根反查設定", summary="輸入字根無候選字時，以其他輸入法字根標注說明。"); both wrapper strings are **NEW** in the back-port.

Every entry below: type=`ListPreference`, `android:defaultValue="none"`, `dialogTitle=@string/im_reverse_lookup_list` (輸入法字根反查). The XML keeps `entries=@array/im_reverse_lookup` / `entryValues=@array/im_reverse_lookup_codes` as a safe fallback, but runtime Android and iOS replace the picker choices with `無` plus the currently enabled IM display names. On iOS the visible rows are also limited to the enabled IMs from the IM list tab path. Stored values remain `none` or the matching IM table code (`cj`, `phonetic`, `dayi`, etc.), so the preference keys and reverse-lookup lookup tables do not change.

| Pref Key | iOS picker label | Android title (both versions) |
|---|---|---|
| `custom_im_reverselookup` | 自建 | 自建字根反查 |
| `cj_im_reverselookup` | 倉頡 | 倉頡字根反查 |
| `scj_im_reverselookup` | 快倉 | 快倉字根反查 |
| `cj5_im_reverselookup` | 倉頡五代 | 倉頡五代字根反查 |
| `ecj_im_reverselookup` | 速成 | 速成字根反查 |
| `dayi_im_reverselookup` | 大易 | 大易字根反查 |
| `bpmf_im_reverselookup` | 注音 | 注音字根反查 |
| `ez_im_reverselookup` | 輕鬆 | 輕鬆字根反查 |
| `array_im_reverselookup` | 行列 | 行列字根反查 |
| `array10_im_reverselookup` | 行列 10 | 行列10字根反查 |
| `wb_im_reverselookup` | 筆順五碼 | 筆順五碼字根反查 |
| `hs_im_reverselookup` | 華象直覺 | 華象直覺字根反查 |
| `pinyin_im_reverselookup` | 拼音 | 拼音字根反查 |

---

## Android-only prefs (no iOS counterpart) — current category `pref_section_physical_keyboard` (外接鍵盤)

| Pref Key | Type | Default | Android pre-back-port (category · label) | Android current (category) | Function |
|---|---|---|---|---|---|
| `hide_software_keyboard_typing_with_physical` | CheckBox | true | lime_keyboard · 自動隱藏軟鍵盤 · summary=以實體鍵盤打字時隱藏軟鍵盤，S-PEN使用者請勿勾選 | pref_section_physical_keyboard · 自動隱藏軟鍵盤 | Hide soft keyboard when hardware kbd is connected. |
| `switch_english_mode` | CheckBox | false | lime_keyboard · 快速切換輸入模式1 · summary=[SHIFT]+[SPACE] 鍵切換輸入模式 (實體鍵盤) | pref_section_physical_keyboard · 快速切換輸入模式1 | SHIFT+SPACE toggles CN/EN on HW kbd. |
| `switch_english_mode_shift` | CheckBox | true | lime_keyboard · 快速切換輸入模式2 · summary=單按 [SHIFT] 鍵切換輸入模式 (實體鍵盤) | pref_section_physical_keyboard · 快速切換輸入模式2 | Single SHIFT toggles CN/EN on HW kbd. |
| `disable_physical_selkey` | CheckBox | false | **lime_im** · 關閉實體鍵盤選字鍵 | pref_section_physical_keyboard · 關閉實體鍵盤選字鍵 | Disable HW selection keys. |
| `selkey_option` | ListPreference | "0" | lime_im · 設定選字鍵預選順序 | pref_section_physical_keyboard · 設定選字鍵預選順序 | Selection-key preselect order for physical-keyboard candidate labels/commits. Android-only, no iOS counterpart. |
| `english_dictionary_physical_keyboard` | CheckBox | false | lime_mapping · 實體鍵盤啟用英文字典 · dependency=`english_dictionary_enable` | pref_section_physical_keyboard · 實體鍵盤啟用英文字典 | English dict for HW kbd typing. |
| `physical_keyboard_sort` | CheckBox | true | lime_mapping · 啟動實體鍵盤選取排序 · summary=使用實體鍵鍵時依選取次數排序選字清單 | pref_section_physical_keyboard · 啟動實體鍵盤選取排序 | Sort candidates by frequency on HW kbd. |

---

## Diffs Summary

### Prefs REMOVED from `preference.xml` in the back-port

| Pref Key | Old category | Old default | Where it surfaces now |
|---|---|---|---|
| `auto_commit` | lime_im | "0" | `ImDetailFragment` when `tableCode == "array10"` (ANDROID_BACKPORT_GAP.md §2.2/P1.4) |
| `phonetic_keyboard_type` | lime_im | "standard" | `ImDetailFragment` when `tableCode == "phonetic"` (ANDROID_BACKPORT_GAP.md §2.2/P1.5) |
| `accept_number_index` | lime_mapping | *(none)* | `ImDetailFragment` when `tableCode == "custom"` (ANDROID_BACKPORT_GAP.md §2.2/P1.3) |
| `accept_symbol_index` | lime_mapping | *(none)* | `ImDetailFragment` when `tableCode == "custom"` |

### Strings newly added in the back-port

- 7 `pref_section_*` category-title strings (`pref_section_appearance`, `pref_section_feedback`, `pref_section_im_behaviour`, `pref_section_han_convert`, `pref_section_related_learning`, `pref_section_english_dictionary`, `pref_section_physical_keyboard`). `pref_section_font_display` and `pref_section_advanced` were initially added in the back-port but later removed when §8.3 and §8.8 were consolidated away.
- Reverse-lookup sub-screen wrapper: `im_reverse_lookup_screen_title` (字根反查設定), `im_reverse_lookup_summary` (輸入字根無候選字時，以其他輸入法字根標注說明。).

### Default-value changes (Android pre→current) — alignment with iOS

| Pref | iOS default | Pre-back-port default | Current default | Status |
|---|---|---|---|---|
| `keyboard_theme` | 6 | **"0"** | "6" | Defaults now use value 6 (系統設定 / system follow) on both iOS and Android current. Android current includes value 6 and resolves it to light/dark at runtime. |
| `keyboard_size` | "1" | "1" | "1" | All defaults are now `"1"` (一般). Re-aligned across both platforms 2026-05-14. |
| `font_size` | "1" | **"1"** | "1" | All defaults are now `"1"` (一般). Re-aligned across both platforms 2026-05-14. |
| `smart_chinese_input` | true | **false** | true | Aligned to iOS. `preference.xml` defaultValue flipped `false→true`; Java accessor `LIMEPreferenceManager.java:434` already returned `true` for unset key. |
| `auto_chinese_symbol` | false | false | false | iOS reconciled to `false` (matches Android — no divergence). |
| `backup_on_delete_{tableNick}` | true | *(runtime default false)* | true | At parity. `ImDetailFragment.java:169` reads with default `true`; the bug referenced at the stale line 121 has been fixed. |

### Category re-organisation (Android pre→current)

Pre-back-port had **3 flat categories** (`lime_keyboard` / `lime_im` / `lime_mapping`). Current has **7 categories + 1 nested sub-screen**, aligned to iOS §8.1–§8.7 (§8.3 / §8.8 / §8.9 consolidated). Mapping of where each pref moved:

| Pre-back-port category | Current category | Items |
|---|---|---|
| lime_keyboard (鍵盤) | pref_section_appearance (§8.1) | keyboard_theme, keyboard_size, font_size, number_row_in_english, show_arrow_key, split_keyboard_mode |
| lime_keyboard | pref_section_feedback (§8.2) | vibrate_on_keypress, vibrate_level, sound_on_keypress, keypress_sound_volume |
| lime_keyboard | pref_section_im_behaviour (§8.4) | enable_emoji_position, persistent_language_mode |
| lime_keyboard | pref_section_physical_keyboard | hide_software_keyboard_typing_with_physical, switch_english_mode, switch_english_mode_shift |
| lime_im (輸入法) | pref_section_im_behaviour (§8.4) | smart_chinese_input, auto_chinese_symbol |
| lime_im | pref_section_han_convert (§8.5) | han_convert_option |
| lime_im | reverse_lookup_screen (§8.4.1 sub-screen, nested as the last item inside pref_section_im_behaviour) | All 13 `*_im_reverselookup` |
| lime_im | pref_section_physical_keyboard | disable_physical_selkey, selkey_option |
| lime_mapping (對應表) | pref_section_im_behaviour (§8.4) | candidate_switch |
| lime_mapping | pref_section_related_learning (§8.6) | similiar_enable, similiar_list, candidate_suggestion, learn_phrase, learning_switch |
| lime_mapping | pref_section_english_dictionary (§8.7) | english_dictionary_enable, auto_cap *(auto_cap newly surfaced in §8.7)* |
| lime_mapping | pref_section_physical_keyboard | english_dictionary_physical_keyboard, physical_keyboard_sort |
| lime_im | *(removed)* | auto_commit, phonetic_keyboard_type, han_convert_notify |
| lime_mapping | *(removed)* | accept_number_index, accept_symbol_index |

> `pref_section_font_display` (§8.3) and `pref_section_advanced` (§8.8) both deleted: contents moved into §8.1 / §8.4 / §8.6, and the `reverse_lookup_screen` sub-screen now appears as the final row inside §8.4.

---

## Source paths

- iOS current/spec: `LimeIME-iOS/LimeSettings/Views/PreferencesTabView.swift`; spec background in `docs/LIME_SETTINGS.md` §8 (sections §8.1, §8.2, §8.4, §8.4.1 Reverse Lookup sub-screen, §8.5, §8.6, §8.7).
- Android pre-back-port: `git show 6791ab7b:LimeStudio/app/src/main/res/xml/preference.xml` (xml-v17 byte-identical). String resolution via `git show 6791ab7b:LimeStudio/app/src/main/res/values/strings.xml`.
- Android current: `LimeStudio/app/src/main/res/xml/preference.xml`; strings in `app/src/main/res/values/strings_settings.xml` and `strings.xml`.
- Gap-tracking & priority cross-reference: `docs/ANDROID_BACKPORT_GAP.md`.
