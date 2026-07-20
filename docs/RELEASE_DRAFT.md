# 萊姆中文輸入法 - LIME IME v6.1.35

**Google Play 套件名稱：** `org.limeime`

**GitHub APK 套件名稱：** `net.toload.main.hd2026`

**目標 SDK：** 37

**最低 SDK：** 25

**前一正式版本：** [v6.1.34](https://github.com/lime-ime/limeime/releases/tag/v6.1.34)

這版在 Android 與 iOS 新增三碼輸入法及專用的「LIME+數字符號鍵盤2」，並改善手機與 iPad 的鍵盤切換及版面穩定性。iOS 修正自建輸入法可能保留前一個鍵盤、無法直接切換英文，以及 iPad 窄版中文鍵盤顯示錯誤模式鍵的問題。Android 另改善分離鍵盤幾何同步及關聯詞非同步查詢穩定性。

> **Android 相容性注意：** v6.1.35 最低支援 Android 7.1（API 25），目標 SDK 為 API 37。

> **Android 發行管道：** Google Play 版本使用套件名稱 `org.limeime` 與 Google Play 簽署流程。GitHub Release APK 使用套件名稱 `net.toload.main.hd2026`、versionCode `2026` 與舊 GitHub APK 相容簽署金鑰。Android 會把兩者視為不同 App，可以同時安裝，但不能互相直接更新。Google Play 使用者請從 Google Play 更新。

Android 正式版可從 [Google Play](https://play.google.com/store/apps/details?id=org.limeime) 下載，GitHub Release 也會提供 Android APK。iPhone／iPad 正式版請從 [Apple App Store](https://apps.apple.com/app/id6784694460) 更新。

## 更新內容

### Android

- **新增三碼輸入法**
  - 新增由無書自通提供的三碼輸入法，可從下載輸入法清單安裝。
  - 新增「LIME+數字符號鍵盤2」，保留三碼需要的英文字母與五個符號字根。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/159>

- **改善手機分離與單手鍵盤版面**
  - 鍵盤模式、按鍵寬度、分離間距及候選區會在方向或模式變更後同步更新。
  - 改善較窄按鍵上的文字縮放，避免標示超出按鍵範圍。

- **改善關聯詞查詢穩定性**
  - 非同步查詢會固定使用該次送出的候選詞，避免快速繼續輸入時查到後續變動的候選內容。

- **改善 Android 平台相容性**
  - 更新語音輸入與系統介面設定，配合目前 Google Play 平台規範。

### iOS

- **新增三碼輸入法**
  - 新增三碼下載項目及「LIME+數字符號鍵盤2」。
  - 補齊 iPhone、完整 iPad 與窄版 iPad 的一般及 Shift 版面。
  - 修正三碼鍵盤的單引號按鍵顯示及窄版 iPad 標點排列。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/159>

- **修正自建輸入法鍵盤切換**
  - 自建輸入法會使用與 Android 相同的中文數字鍵盤配置。
  - 修正切換到自建輸入法後可能保留前一個鍵盤，以及中文模式無法直接切換英文的問題。
  - 中文輸入法缺少指定版面時只會回到可繼續中文組字的安全版面，不會退回英文鍵盤。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/177>

- **修正 iPad 模式鍵**
  - 五個窄版 iPad 中文數字及 Shift 版面改為顯示 `abc` 英文切換鍵。
  - 符號頁維持原本的 `abc` 與「中」雙出口設計。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/181>

- **改善鍵盤方向切換**
  - 修正旋轉裝置後回到直向時，鍵盤可能沿用橫向幾何尺寸的問題。
