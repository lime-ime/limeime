# 萊姆中文輸入法 - LIME IME v6.1.38

這版更新 Android 與 iOS 的三碼字元碼表，並改善 iOS 的資料同步、游標移動及特定欄位中文輸入。

> **Android 相容性注意：** v6.1.38 最低支援 Android 7.1（API 25），目標 SDK 為 API 37。

> **Android 發行管道：** Google Play 版本使用套件名稱 `org.limeime`。GitHub Release APK 使用套件名稱 `net.toload.main.hd2026`，與舊 GitHub APK 相容。Android 會把兩者視為不同 App，可以同時安裝，但不能互相直接更新。Google Play 使用者請從 Google Play 更新。

Android 正式版可從 [Google Play](https://play.google.com/store/apps/details?id=org.limeime) 下載。GitHub Release 另提供 Android APK。iPhone／iPad 正式版請從 [Apple App Store](https://apps.apple.com/app/id6784694460) 更新。

## 更新內容

### Android 與 iOS

- **更新三碼字元碼表**
  - 更新至 `v.20260805.2`，內容擴充至 23,299 筆。
  - 下載清單名稱調整為「三碼字元」。
  - 已安裝三碼輸入法的使用者需先移除，再重新下載碼表，才會套用新版內容。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/227>

### iOS

- **改善輸入法資料與管理畫面的同步**
  - 改善鍵盤學習資料與設定 App 之間的同步流程，避免管理自建字詞或關聯詞時因資料庫鎖定而無法編輯。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/209>

- **修正上下方向鍵無法跨越換行的問題**
  - 上下方向鍵現在可跨越明確換行，並可連續移動至前後文字行。
  - 受限於 iOS 鍵盤介面，目前不包含畫面自動折行的視覺行移動。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/224>

- **修正電子郵件與電話欄位的中文輸入**
  - 修正在特定電子郵件與電話欄位切換回中文後，只出現英文字母而沒有組字與候選的問題。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/226>
