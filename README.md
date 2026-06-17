# ![萊姆輸入法](LimeStudio/app/src/main/res/drawable-hdpi/logo.png) 萊姆輸入法

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

LIME 是一套以繁體中文輸入為核心的 Android 輸入法專案，採用 GPL 授權並持續開源維護。

本次 2026 版本為相隔多年後的長週期更新，重點包含 Android 新版相容性、架構重整與整體穩定性提升。

## 使用手冊

第一次安裝、換機備份還原、下載輸入法、調整喜好設定或排查鍵盤問題，請先看 [LIME 使用手冊](docs/manuals/index.md)。

## 下載

### 最新正式版（GitHub Release）

- 版本：v6.1.21
- APK：LIMEHD2026-6.1.21.apk
- 下載連結：[LIMEHD2026-6.1.21.apk](https://github.com/lime-ime/limeime/releases/download/v6.1.21/LIMEHD2026-6.1.21.apk)

## 版本與相容性

- 最新套件名稱：net.toload.main.hd2026
- 目標 SDK：36
- 最低支援 SDK：21
- 與舊版可並存安裝

## 隱私與安全性

LIME 僅宣告以下 5 項權限，不要求帳號存取或個人資料，不收集、不分享任何使用者資訊。

| 權限 | 用途 |
|------|------|
| `POST_NOTIFICATIONS` | 顯示下載進度、備份還原狀態等通知（Android 13+ 必須明確宣告） |
| `VIBRATE` | 按鍵時提供觸覺震動回饋 |
| `INTERNET` | 從伺服器下載使用者選擇的輸入法碼表 |
| `ACCESS_NETWORK_STATE` | 下載前檢查網路是否可用，避免無效連線嘗試 |
| `RECORD_AUDIO` | 啟用萊姆內建語音輸入，讓語音辨識留在 LIME 鍵盤畫面內。未授權仍可改用 Google 或系統提供的語音輸入 |

### 語音輸入與麥克風權限

「萊姆內建語音輸入」需要麥克風權限。授權後，語音輸入不必切換系統辨識畫面，可直接在 LIME 鍵盤內顯示辨識狀態與結果。不授權也不會影響 LIME 的其他操作，按下麥克風鍵時，LIME 會改用系統提供的語音輸入，並切換到系統提供的語音輸入視窗。

## 重要使用說明

### 按鍵震動（Android 13 及以上）

Android 13 起，震動強度由系統統一控制，LIME 已於 Android 13 及以上版本移除震動強度設定。

按鍵震動無效時，請同時確認：
- **步驟一**：至手機系統設定開啟觸覺回饋開關，並調高震動強度
  - Pixel：設定 → 聲音與震動 → 震動與觸覺回饋
  - Samsung：設定 → 聲音和震動 → 震動強度 → 觸覺回饋
  - 小米：設定 → 聲音與震動 → 震動強度 → 觸控操作
  - OPPO：設定 → 聲音與震動 → 震動強度 → 觸控回饋
- **步驟二**：至 LIME 設定開啟「按鍵震動」選項

### 雲端備份與還原

最新版已移除內建的 Google Drive 及 Dropbox 備份還原功能，改由系統檔案選擇器處理儲存位置。

**備份：**
在 LIME 設定點擊「備份」→ 系統檔案選擇器開啟後，選擇儲存位置（本機資料夾或 Google Drive 等雲端服務）→ 確認儲存

**還原：**
在 LIME 設定點擊「還原」→ 系統檔案選擇器開啟後，找到並選取備份 `.zip` 檔案（本機或雲端皆可）→ 確認還原

### Google Play 最後上架版本

- 版本標籤：5.2.4-530
- APK：app-release.apk
- Android 實際可用版本：Android 4.0.1 (API 14) 至 Android 12 (API 32) 
- 下載連結：[limeime-524-530.apk](https://github.com/gontera/LIME-IME/raw/refs/heads/main/limeime-524-530.apk)

## 問題回報

若你遇到使用問題，請優先透過 GitHub Issue 回報，並盡量提供以下資訊：

- 裝置品牌與型號（例如：Samsung Galaxy S23）
- Android 版本與 API 等級（例如：Android 14 / API 34）
- App 版本（例如：v6.0.0）
- 安裝來源（GitHub Release 或歷史版本 APK）
- 問題發生步驟與預期結果
- 實際結果與錯誤訊息（若有）

資訊越完整，越能加速問題重現與修正。

## Core Development Team

- Jeremy Wu (jrywjwu@gmail.com)
- Julian Chen (netkidz@gmail.com)
- Art Hung (hosoyu@gmail.com)
