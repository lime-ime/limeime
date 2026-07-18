# 萊姆中文輸入法 - LIME IME v6.1.33

**Google Play 套件名稱：** `org.limeime`

**GitHub APK 套件名稱：** `net.toload.main.hd2026`

**目標 SDK：** 37

**最低 SDK：** 25

**前一正式版本：** [v6.1.32](https://github.com/lime-ime/limeime/releases/tag/v6.1.32)

這版新增手機單手鍵盤、平板分離鍵盤微調與數字鍵盤位置選項，讓按鍵與候選列更符合單手或雙手拇指操作範圍。Android 與 iOS 也改善關聯字管理，iOS 並修正「LIME+數字符號鍵盤」版面。Android 更新版另修正 Play Vitals 回報的鍵盤無回應與候選相關閃退。

> **Android 相容性注意：** v6.1.33 最低支援 Android 7.1（API 25），目標 SDK 為 API 37。

> **Android 發行管道：** Google Play 版本使用套件名稱 `org.limeime` 與 Google Play 簽署流程。GitHub Release APK 使用套件名稱 `net.toload.main.hd2026`、versionCode `2026` 與舊 GitHub APK 相容簽署金鑰。Android 會把兩者視為不同 App，可以同時安裝，但不能互相直接更新。Google Play 使用者請從 Google Play 更新。

Android 正式版可從 [Google Play](https://play.google.com/store/apps/details?id=org.limeime) 下載，GitHub Release 也會提供 Android APK。iPhone／iPad 正式版請從 [Apple App Store](https://apps.apple.com/app/id6784694460) 更新。

## 更新內容

### Android

- **新增單手、分離與數字鍵盤位置選項**
  - 手機直向模式可將鍵盤縮小並靠左或靠右，方便單手輸入。
  - 手機橫向與平板分離鍵盤會依拇指可及範圍調整按鍵寬度與間距。
  - 數字鍵盤可選擇靠左、置中、靠右或維持滿版。
  - 候選列與展開候選會配合鍵盤位置顯示。

- **修正關聯字管理與候選更新**
  - 關聯字管理改為依「詞彙」前綴搜尋，例如輸入「萊」會列出詞彙以「萊」開頭的資料。
  - 修正新增、修改或刪除關聯字後，鍵盤可能仍顯示舊候選的問題。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/161>

- **修正鍵盤無回應與候選閃退**
  - 資料庫維護期間，主執行緒不再等待 SQLite 連線，避免輸入法啟動或結束輸入時無回應。
  - 修正英文預測候選非同步更新時可能發生的字串範圍錯誤。
  - 修正即時建議與候選列跨執行緒更新時可能發生的集合異動及空值錯誤。

- **更新 Android 建置與相容性設定**
  - 目標 SDK 更新為 API 37。
  - 更新 Android Gradle Plugin、Gradle 與 AndroidX 元件。

### iOS

- **新增單手、分離與數字鍵盤位置選項**
  - iPhone 可使用靠左或靠右的單手鍵盤。
  - iPad 分離鍵盤依拇指可及範圍調整左右鍵區。
  - 數字鍵盤可選擇靠左、置中、靠右或維持滿版，候選列會配合鍵盤位置顯示。

- **修正「LIME+數字符號鍵盤」版面**
  - 補齊 iPhone、iPad 與窄版 iPad 的一般及 Shift 版面。
  - 保留各尺寸應有的數字列、符號與標點按鍵。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/160>

- **修正關聯字管理與候選更新**
  - 關聯字管理改為依「詞彙」前綴搜尋。
  - 對齊 Android 的完整詞與末字關聯查詢，並修正資料異動後鍵盤快取未更新的問題。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/161>
