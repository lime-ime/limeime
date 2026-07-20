# 萊姆中文輸入法 - LIME IME v6.1.35

這版在 Android 與 iOS 新增三碼輸入法 v.20260720.3 及專用的「LIME+數字符號鍵盤2」，並改善手機與 iPad 的鍵盤切換及版面穩定性。iOS 修正自建輸入法可能保留前一個鍵盤、無法直接切換英文，以及 iPad 窄版中文鍵盤顯示錯誤模式鍵的問題。Android 另修正分離鍵盤幾何同步及快速輸入時的關聯詞查詢問題。

> **Android 相容性注意：** v6.1.35 最低支援 Android 7.1（API 25），目標 SDK 為 API 37。

> **Android 發行管道：** Google Play 版本使用套件名稱 `org.limeime`。GitHub Release APK 使用套件名稱 `net.toload.main.hd2026`，與舊 GitHub APK 相容。Android 會把兩者視為不同 App，可以同時安裝，但不能互相直接更新。Google Play 使用者請從 Google Play 更新。

Android 正式版可從 [Google Play](https://play.google.com/store/apps/details?id=org.limeime) 下載。GitHub Release 另提供 Android APK。iPhone／iPad 正式版請從 [Apple App Store](https://apps.apple.com/app/id6784694460) 更新。

## 更新內容

### Android

- **新增三碼輸入法**
  - 新增由無書自通提供的三碼輸入法 v.20260720.3，可從下載輸入法清單安裝。
  - 新增「LIME+數字符號鍵盤2」，保留三碼需要的英文字母與五個符號字根。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/159>

- **改善手機分離鍵盤版面**
  - 鍵盤方向或模式變更後，按鍵寬度、分離間距及候選區會同步更新。
  - 改善較窄按鍵上的文字縮放，避免標示超出按鍵範圍。

- **修正關聯詞查詢**
  - 快速繼續輸入時，關聯詞會固定依照剛送出的字詞查詢，不會誤用後續變動的候選內容。

### iOS

- **新增三碼輸入法**
  - 新增三碼輸入法 v.20260720.3 及「LIME+數字符號鍵盤2」。
  - 補齊 iPhone、完整 iPad 與窄版 iPad 的一般及 Shift 版面。
  - 修正三碼鍵盤的單引號按鍵顯示與窄版 iPad 標點排列。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/159>

- **修正自建輸入法鍵盤切換**
  - 自建輸入法會使用與 Android 相同的中文數字鍵盤配置。
  - 修正切換到自建輸入法後可能保留前一個鍵盤，以及中文模式無法直接切換英文的問題。
  - 中文輸入法缺少指定版面時，不會退回英文鍵盤。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/177>

- **修正 iPad 模式鍵**
  - 五個窄版 iPad 中文數字及 Shift 版面改為顯示 abc 英文切換鍵。
  - 符號頁維持原本的 abc 與「中」雙出口設計。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/181>

- **改善鍵盤方向切換**
  - 修正旋轉裝置後回到直向時，鍵盤可能沿用橫向尺寸的問題。
