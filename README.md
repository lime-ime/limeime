# ![萊姆輸入法](LimeStudio/app/src/main/res/drawable-hdpi/logo.png) 萊姆輸入法

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

LIME 是一套以繁體中文輸入為核心的開源輸入法專案，採用 GPL 授權並持續開源維護，目前支援 Android 與 iOS 兩大平台。

本次 2026 版本為相隔多年後的長週期更新，重點包含 Android 新版相容性、架構重整與整體穩定性提升。

## 官方網站與使用手冊

一般使用者請從官方網站開始：

- **官方網站**：<https://lime-ime.github.io/limeime>
- **使用手冊**：<https://lime-ime.github.io/limeime/pages>

第一次[安裝啟用](https://lime-ime.github.io/limeime/pages/quick-start.html)、[換機備份還原](https://lime-ime.github.io/limeime/pages/database-management.html)、[鍵盤輸入](https://lime-ime.github.io/limeime/pages/keyboard-input.html)、[調整喜好設定](https://lime-ime.github.io/limeime/pages/preferences.html)、[按鍵震動](https://lime-ime.github.io/limeime/pages/preferences.html)、[語音輸入](https://lime-ime.github.io/limeime/pages/troubleshooting.html)或[疑難排解](https://lime-ime.github.io/limeime/pages/troubleshooting.html)，請先閱讀使用手冊。

## 隱私與安全性

LIME 不收集、不分享任何使用者資訊，也不要求帳號登入或個人資料存取。所有輸入內容與學習資料僅保存在裝置本機。

App 僅在功能需要時宣告少數權限（如下載碼表、按鍵震動、內建語音輸入等），各權限的實際用途與平台差異，請見手冊的[隱私說明](https://lime-ime.github.io/limeime/pages/privacy.html)。

## 下載

### 最新正式版（GitHub Release）

- 版本：v6.1.28
- APK：[LIMEHD2026-6.1.28.apk](https://github.com/lime-ime/limeime/releases/download/v6.1.28/LIMEHD2026-6.1.28.apk)

## 版本與相容性

- 套件名稱：net.toload.main.hd2026
- 顯示名稱：萊姆輸入法A
- 目標 SDK：36
- 最低支援 SDK：21
- GitHub Release APK 與 Google Play 版本使用不同套件名稱與簽署金鑰，可以同時安裝並共存，但不能互相直接更新或升級
- Google Play 封閉測試使用者請從 Google Play 更新。若要在 GitHub APK 與 Google Play 版本之間切換使用，請先備份輸入法資料

## 開發

本專案包含 Android 與 iOS 兩套平台原始碼：

- **Android（`LimeStudio/`，Java / Kotlin）**：以 Android Studio 開啟 `LimeStudio/` 專案根目錄，待 Gradle Sync 完成後，選擇 `app` 設定檔即可建置與執行。建議使用近期版本的 Android Studio 與相符的 Android SDK / Build Tools。
- **iOS（`LimeIME-iOS/`，Swift）**：以 Xcode 開啟 `LimeIME-iOS/LimeIME.xcodeproj`，選好目標 scheme 與簽署團隊後即可建置與執行。鍵盤擴充需在實機或模擬器上啟用後測試。

## 主要技術規格文件

開發前建議先閱讀以下核心架構與格式規格文件（位於 `docs/`）：

### 架構

- [LimeIME 架構總覽](docs/LIMEIME_ARCHITECTURE.md) — 整體架構與模組關係
- [UI 架構（MVC）](docs/UI_ARCHITECTURE.md) — 介面層的 MVC 設計
- [IMService 規格](docs/IM_SERVICE.md) — 輸入法服務核心規格

### 資料與格式

- [`limedb` 規格](docs/LIMEDB_SPEC.md) — LimeDB 資料庫格式
- [CIN / LIME 檔案格式規格](docs/CIN_LIME_SPEC.md) — 輸入法碼表文字格式

### 鍵盤佈局

- [Android / iPhone 鍵盤佈局](docs/ANDROID_IPHONE_KEYBOARD.md) — 跨平台鍵盤佈局與特殊鍵行為
- [iPad 鍵盤佈局](docs/IPAD_KEYBOARD.md) — iPad 專屬鍵盤佈局與候選列

### 設定

- [LIME Settings 規格](docs/LIME_SETTINGS.md) — 設定 App 與喜好設定規格
- [喜好設定對照表](docs/PREFS_TABLE.md) — iOS 與 Android 各喜好設定項目對照

## 問題回報

若您遇到使用問題，請優先透過 GitHub Issue 回報，並盡量提供以下資訊：

- 裝置品牌與型號（例如：Samsung Galaxy S23 / iPhone 15）
- 系統版本（例如：Android 14 / API 34，或 iOS 17）
- App 版本（例如：v6.1.28）
- 安裝來源（GitHub Release 或歷史版本 APK）
- 問題發生步驟與預期結果
- 實際結果與錯誤訊息（若有）

資訊越完整，越能加速問題重現與修正。

## 開發團隊

- Jeremy Wu
- Julian Chen
- Art Hung

## 聯絡方式

- 萊姆小編 [limeimetw@gmail.com](mailto:limeimetw@gmail.com)
