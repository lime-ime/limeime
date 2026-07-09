# LIME 使用手冊計畫

## 1. 目標

在 repo root 的 `/docs/manuals/` 建立繁體中文使用手冊。手冊給一般使用者閱讀，不放工程架構、測試細節或內部實作追蹤，必要時才連到 `docs/` 技術文件。

完成狀態必須通過 [MANUAL_REVIEW_WORKFLOW.md](MANUAL_REVIEW_WORKFLOW.md) 的多角色審稿，包含來源正確性、使用者任務流程、正式中文、視覺設計、結構連結、網頁可讀性、截圖媒體、版本隱私與平台限制。各角色標準由 dedicated auditor docs 定義，不在 prompt 內重複定義。

## 2. 硬性規則

- 所有手冊頁都放在 `/docs/manuals/`。
- 手冊檔案與資料夾名稱只使用 ASCII English。
- 手冊採用一層式檔案結構，主題頁直接放在 `/docs/manuals/*.md`，不可再建立只有一個 `overview.md`、`guide.md`、`usage.md` 或 `settings.md` 的子資料夾。
- 使用者可見文字使用繁體中文。
- 不建立 30 行以下的薄頁，內容不足時併入父頁。
- `docs/LIME_SETTINGS.md` 與 `docs/KEYBOARD_THEME.md` 已列出的現存截圖必須嵌入手冊，且有對應章節說明畫面、任務與使用者應注意的狀態。
- 不寫作者視角前言。
- 不寫空泛產品介紹，第一段必須直接提供使用者下一步。
- 使用者可見文句不可使用中文分號，一般完整句應以句號結尾，且句內應有逗號支撐清楚的主詞、動詞與受詞結構。
- iPhone「允許完整取用」應描述為選用功能解鎖，用於備份資料庫、按鍵震動回饋與編輯字根資料表，不可寫成正常打字、安裝或匯入輸入法、還原資料庫的必要條件。
- iPad 13 吋、11 吋、mini 尺寸分級是未實作規劃，不可寫成現行功能。
- 手冊頁應使用 [MANUAL_VISUAL_DESIGNER.md](MANUAL_VISUAL_DESIGNER.md) 定義的 CSS 元件改善網頁閱讀性。

## 3. 來源文件

撰寫與審稿前必讀：

- [LIME_SETTINGS.md](LIME_SETTINGS.md)：設定 App 四分頁、DB Manager、喜好設定、輸入法管理、截圖。
- [IOS_FULL_ACCESS.md](IOS_FULL_ACCESS.md)：iOS 完整取用權限、FA-off 可用功能、FA-on 解鎖功能。
- [IOS_DB_COLD_HOT.md](IOS_DB_COLD_HOT.md)：iOS 備份、還原、資料表編輯與 cold/hot 同步的使用者可見行為。
- [KEYBOARD_THEME.md](KEYBOARD_THEME.md)：鍵盤主題、系統設定主題、注音/英文/Emoji 截圖要求。
- [ANDROID_IPHONE_KEYBOARD.md](ANDROID_IPHONE_KEYBOARD.md)：特殊鍵、長按、空白鍵游標、iPad 副鍵符號。
- [KEYBOARD_TYPE.md](KEYBOARD_TYPE.md)：電話、數字、Email、密碼、URL、搜尋欄位行為。
- [ANDROID_VOICE_INPUT.md](ANDROID_VOICE_INPUT.md)：Android 內建語音、Google/系統語音、`RecognizerIntent` fallback。
- [IPAD_KEYBOARD.md](IPAD_KEYBOARD.md)：iPad 五列鍵盤、副鍵符號、分割鍵盤。
- [IPAD_KB_SIZE_TIERS.md](IPAD_KB_SIZE_TIERS.md)：iPad 尺寸分級，狀態為 PLAN / 未實作。
- [#88_ISSUE.md](#88_ISSUE.md)：舊版備份、還原、Samsung 設定入口與資料庫還原風險背景。

審稿角色標準：

- [SOURCE_ACCURACY_AUDITOR.md](SOURCE_ACCURACY_AUDITOR.md)
- [USER_TASK_FLOW_AUDITOR.md](USER_TASK_FLOW_AUDITOR.md)
- [CHINESE_FORMAL_WRITING_AUDITOR.md](CHINESE_FORMAL_WRITING_AUDITOR.md)
- [MANUAL_VISUAL_DESIGNER.md](MANUAL_VISUAL_DESIGNER.md)
- [MANUAL_STRUCTURE_LINK_AUDITOR.md](MANUAL_STRUCTURE_LINK_AUDITOR.md)
- [WEB_LAYOUT_READABILITY_AUDITOR.md](WEB_LAYOUT_READABILITY_AUDITOR.md)
- [SCREENSHOT_MEDIA_AUDITOR.md](SCREENSHOT_MEDIA_AUDITOR.md)
- [PRIVACY_PLATFORM_LIMIT_AUDITOR.md](PRIVACY_PLATFORM_LIMIT_AUDITOR.md)

## 4. 目前手冊結構

```text
docs/manuals/
├── index.md
├── quick-start.md
├── troubleshooting.md
├── ime-management.md
├── keyboard-input.md
├── preferences.md
├── database-management.md
├── advanced.md
└── faq.md
```

舊結構如 `docs/manuals/ime-management/overview.md`、`docs/manuals/database-management/guide.md` 與 `docs/manuals/preferences/settings.md` 會產生無意義的單檔資料夾，後續不得恢復。若某個主題只有一個主頁，請使用 `docs/manuals/<topic>.md`。

## 5. 頁面責任

| 頁面 | 責任 | 必須使用的來源/截圖 |
|------|------|--------------------|
| `docs/manuals/index.md` | 使用者入口、平台啟用分流、新安裝/換機/排查導引 | `assets/lime_settings_ios_setup.png`, `assets/lime_settings_android_setup.png` |
| `docs/manuals/quick-start.md` | iPhone/iPad/Android 啟用鍵盤、狀態提示、舊裝置還原入口、第一次下載或匯入輸入法 | `assets/lime_settings_ios_setup.png`, `assets/lime_settings_android_setup.png`, `assets/lime_settings_*_database.png`, `assets/lime_settings_*_im_list.png`, `assets/lime_settings_*_im_install.png` |
| `docs/manuals/troubleshooting.md` | 啟用、碼表、資料庫、語音輸入快速排查 | `LIME_SETTINGS.md`, `ANDROID_VOICE_INPUT.md`, `#88_ISSUE.md` |
| `docs/manuals/ime-management.md` | 下載、匯入、啟用、編輯輸入法、分享單一輸入法與關聯字詞 | `assets/lime_settings_*_im_list.png`, `assets/lime_settings_*_im_detail.png`, `assets/lime_settings_*_im_install.png`, `assets/lime_settings_*_record_list.png`, `assets/lime_settings_*_related_list.png` |
| `docs/manuals/keyboard-input.md` | 中文、英文、符號、長按、空白鍵、Emoji、iPad 副鍵符號，是使用者實際打字的主要指南 | `ANDROID_IPHONE_KEYBOARD.md`, `KEYBOARD_TYPE.md`, `KEYBOARD_THEME.md`; 必須嵌入並說明代表性的中文鍵盤、英文鍵盤與 Emoji 面板截圖 |
| `docs/manuals/preferences.md` | 喜好設定、主題、輸入行為、繁簡、反查、平台注意事項 | `assets/lime_settings_*_preferences.png`, `KEYBOARD_THEME.md` |
| `docs/manuals/database-management.md` | 備份、還原、還原預設資料庫、舊版備份 | `assets/lime_settings_*_database.png`, `#88_ISSUE.md` |
| `docs/manuals/advanced.md` | 自製輸入法、碼表格式、資料庫進階、學習系統、自訂佈局 | `CIN_LIME_SPEC.md`, `ANDROID_IPHONE_KEYBOARD.md`, `LIMEDB_SPEC.md` |
| `docs/manuals/faq.md` | 常見問題與跨頁快速解答 | 全部來源，不可取代各主題頁 |

## 6. 視覺設計規則

CSS 元件定義在 [MANUAL_VISUAL_DESIGNER.md](MANUAL_VISUAL_DESIGNER.md) 與 `docs/assets/css/style.scss`。

每個主要頁面應視內容使用：

- `.manual-hero`：頁首任務入口。
- `.manual-card-grid` / `.manual-card`：新使用者、換機、排查等分流。
- `.manual-screenshot-pair`：iPhone/Android 或相關雙截圖。
- `.manual-note`：權限、平台差異、補充提示。
- `.manual-warning`：資料覆蓋、未實作功能、隱私與高風險操作。

Raw Markdown 圖片表格只在簡單比較時可用，設定 App 截圖應優先改用 `.manual-screenshot-pair`。

## 7. 審稿工作流

每頁必須依序通過：

1. 來源正確性審稿員
2. 使用者任務流程審稿員
3. 繁體中文正式文稿審稿員
4. 視覺設計師
5. 手冊結構與連結審稿員
6. 網頁版面與可讀性審稿員
7. 截圖與媒體審稿員
8. 版本、隱私與平台限制審稿員

任一角色退稿，修正後必須重新跑受影響審稿。

## 8. 必退稿清單

- 作者視角前言。
- 空泛產品定義，且沒有立即給出操作分流。
- 用不確定語氣描述完整取用權限。
- 把正常打字、安裝或匯入輸入法、還原資料庫寫成完整取用權限的必要條件。
- 把完整取用權限寫成跨程序共享、App Group 或其他內部工程機制的使用者理由。
- 把「喜好設定」寫成「設定」。
- 把 DB Manager / 資料庫分頁寫漏。
- 把 iPad 尺寸分級寫成已實作。
- 沒有使用既有設定截圖卻描述設定 App 畫面。
- 連到不存在的手冊頁。
- 非索引獨立頁少於 30 行。

## 9. 驗收指令

### 手冊路徑必須是 ASCII

```powershell
$bad = rg --files docs/manuals | Where-Object { $_ -match '[^\x00-\x7F]' }
"non_ascii_manual_paths=$($bad.Count)"
$bad
```

### 手冊不得有主題子資料夾

```powershell
$dirs = Get-ChildItem -Path docs/manuals -Directory
"manual_subdirs=$($dirs.Count)"
$dirs
```

### 檢查 broken Markdown links

```powershell
$files = Get-ChildItem -Recurse -File docs/manuals -Filter *.md
$broken=@()
foreach ($file in $files) {
  $text = Get-Content -Path $file.FullName -Raw
  $matches = [regex]::Matches($text, '\[[^\]]+\]\(([^)#]+\.md)(?:#[^)]+)?\)')
  foreach ($m in $matches) {
    $target = $m.Groups[1].Value
    if ($target -match '^(https?://|mailto:)') { continue }
    $resolved = Join-Path $file.DirectoryName $target
    if (-not (Test-Path $resolved)) { $broken += "$($file.FullName): $target" }
  }
}
"broken_md_links=$($broken.Count)"
$broken
```

### 檢查短頁

```powershell
Get-ChildItem -Recurse -File docs/manuals -Filter *.md |
  ForEach-Object {
    $count=(Get-Content -Path $_.FullName).Count
    [pscustomobject]@{ Lines=$count; Path=$_.FullName }
  } |
  Where-Object { $_.Lines -lt 30 }
```

### 檢查禁止語句與錯誤概念

```powershell
rg -n "<use the banned phrase and wrong-concept regex from the active review workflow>" docs/manuals README.md
```

## 10. 完成定義

手冊完成必須同時滿足：

- `/docs/manuals/` 結構與本計畫一致。
- 所有頁面通過多角色審稿。
- 所有連結有效。
- 手冊路徑全為 ASCII。
- 無短薄頁。
- 無禁止語句與錯誤概念。
- 設定 App 相關頁使用 `assets/lime_settings_*` 截圖。
- iPad 尺寸分級只標示未實作或未來規劃。
- `README.md` 連到手冊入口。
