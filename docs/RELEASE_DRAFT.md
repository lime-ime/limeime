# 萊姆中文輸入法 - LIME IME v6.1.36

這版修正 Android 三碼輸入法的切換清單與直向分離鍵盤標示，並修正 iOS 許氏與倚天 26 鍵英文版面的切換及持續套用問題。

> **Android 相容性注意：** v6.1.36 最低支援 Android 7.1（API 25），目標 SDK 為 API 37。

> **Android 發行管道：** Google Play 版本使用套件名稱 `org.limeime`。GitHub Release APK 使用套件名稱 `net.toload.main.hd2026`，與舊 GitHub APK 相容。Android 會把兩者視為不同 App，可以同時安裝，但不能互相直接更新。Google Play 使用者請從 Google Play 更新。

Android 正式版可從 [Google Play](https://play.google.com/store/apps/details?id=org.limeime) 下載。GitHub Release 另提供 Android APK。iPhone／iPad 正式版請從 [Apple App Store](https://apps.apple.com/app/id6784694460) 更新。

## 更新內容

### Android

- **修正三碼輸入法切換**
  - 補齊三碼在輸入法切換清單中的註冊，避免安裝後無法切換到三碼。
  - 保留三碼專用的「LIME+數字符號鍵盤2」及中文輸入設定。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/159>

- **修正直向分離鍵盤標示**
  - 直向分離鍵盤的雙標籤按鍵改為上下排列，避免字根與數字提示左右並排而被裁切。
  - 橫向鍵盤維持原有的排列方式。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/169>

### iOS

- **修正許氏與倚天 26 鍵英文版面**
  - 選擇許氏（英文）或倚天 26 鍵（英文）時，會顯示指定的英文鍵盤，不再誤用符號版面。
  - 補齊 iPhone、完整 iPad 與窄版 iPad 的 LIME 英文鍵盤版面。
  - 修正關閉並重新開啟鍵盤後，版面可能回到標準注音鍵盤的問題。
  - 許氏與倚天 26 鍵的符號版面維持原有行為。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/191>
