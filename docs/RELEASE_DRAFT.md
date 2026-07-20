# 萊姆中文輸入法 - LIME IME v6.1.34

**Google Play 套件名稱：** `org.limeime`

**GitHub APK 套件名稱：** `net.toload.main.hd2026`

**目標 SDK：** 37

**最低 SDK：** 25

**前一正式版本：** [v6.1.33](https://github.com/lime-ime/limeime/releases/tag/v6.1.33)

這版統一 Android 手機與 iPhone 的直向鍵盤模式，恢復手機直向分離鍵盤，並讓標準、分離、單手靠左及單手靠右成為互斥選項。Android 與 iOS 也改善自訂 CIN 碼表的空白欄位解析。Android 另修正關聯字管理刪除後列表未即時更新的問題。

> **Android 相容性注意：** v6.1.34 最低支援 Android 7.1（API 25），目標 SDK 為 API 37。

> **Android 發行管道：** Google Play 版本使用套件名稱 `org.limeime` 與 Google Play 簽署流程。GitHub Release APK 使用套件名稱 `net.toload.main.hd2026`、versionCode `2026` 與舊 GitHub APK 相容簽署金鑰。Android 會把兩者視為不同 App，可以同時安裝，但不能互相直接更新。Google Play 使用者請從 Google Play 更新。

Android 正式版可從 [Google Play](https://play.google.com/store/apps/details?id=org.limeime) 下載，GitHub Release 也會提供 Android APK。iPhone／iPad 正式版請從 [Apple App Store](https://apps.apple.com/app/id6784694460) 更新。

## 更新內容

### Android

- **統一手機直向鍵盤模式**
  - 手機可選擇標準、分離、單手靠左或單手靠右，四種模式不會同時衝突。
  - 恢復手機直向分離鍵盤，所有 Android 手機都可使用，不再依手機寬度隱藏。
  - 手機橫向分離鍵盤維持獨立設定，平板的分離鍵盤與數字鍵盤位置設定也保持分開。
  - 改善鍵盤內選單在手機直向畫面的排列。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/169>

- **改善自訂 CIN 碼表匯入**
  - 支援以多個空白或 Tab 對齊欄位的 CIN 碼表，避免匯入完成後無法出字。
  - 保留碼表版本與名稱等中繼資料的原始文字格式。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/172>

- **修正關聯字管理列表更新**
  - 刪除篩選結果後，列表會立即重新整理，不再保留已刪除的舊資料列。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/161>

### iOS

- **統一 iPhone 直向鍵盤模式**
  - iPhone 可選擇標準、分離、單手靠左或單手靠右，所有 iPhone 都可使用，不再依畫面寬度隱藏。
  - 手機橫向分離鍵盤維持獨立設定，iPad 的分離鍵盤與數字鍵盤位置設定也保持分開。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/169>

- **改善自訂 CIN 碼表匯入**
  - 支援以多個空白或 Tab 對齊欄位的 CIN 碼表，避免匯入完成後無法出字。
  - 保留既有 `.lime` 匯入格式的相容性。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/172>
