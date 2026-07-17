# 萊姆中文輸入法 - LIME IME v6.1.32

**Google Play 套件名稱：** `org.limeime`

**GitHub APK 套件名稱：** `net.toload.main.hd2026`

**目標 SDK：** 36

**最低 SDK：** 25

**前一正式版本：** [v6.1.31](https://github.com/lime-ime/limeime/releases/tag/v6.1.31)

這版改善 Android 按鍵輸入的穩定性與英文候選學習排序，並修正 iOS 在鍵盤保持顯示時切換到 LIME 後，App 輸入欄位可能被遮住的問題。

> **Android 相容性注意：** v6.1.32 起最低支援 Android 7.1（API 25），支援範圍為 Android 7.1 至 Android 16（API 25 至 36）。

> **Android 發行管道：** Google Play 版本使用套件名稱 `org.limeime` 與 Google Play 簽署流程。GitHub Release APK 使用套件名稱 `net.toload.main.hd2026`、versionCode `2026` 與舊 GitHub APK 相容簽署金鑰。Android 會把兩者視為不同 App，可以同時安裝，但不能互相直接更新。Google Play 使用者請從 Google Play 更新。

Android 正式版可從 [Google Play](https://play.google.com/store/apps/details?id=org.limeime) 下載，GitHub Release 也會提供 Android APK。iPhone／iPad 正式版請從 [Apple App Store](https://apps.apple.com/app/id6784694460) 更新。

## 更新內容

### Android

- **修正按鍵輸入時可能發生的卡頓或無回應**
  - 將目前輸入法的結束鍵與按鍵名稱設定預先載入記憶體。
  - 避免一般按鍵處理同步等待 SQLite 資料庫，降低輸入期間發生 ANR 的風險。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/158>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/a2dc99d6853b5d834b79b7ac8a91cb600062f4c4>

- **改善英文自動完成候選排序**
  - 英文候選會優先依使用者學習分數排序，再參考內建基礎分數，讓常用詞更容易出現在前方。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/a2dc99d6853b5d834b79b7ac8a91cb600062f4c4>

- **調整 Android 支援範圍**
  - 最低支援版本調整為 Android 7.1（API 25）。
  - 支援 Android 7.1 至 Android 16（API 25 至 36）。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/a2dc99d6853b5d834b79b7ac8a91cb600062f4c4>

### iOS

- **修正切換鍵盤後輸入欄位可能被遮住**
  - 修正鍵盤保持顯示時，從較矮的系統鍵盤直接切換到 LIME 後，App 仍沿用舊鍵盤高度的問題。
  - 改善 LIME 鍵盤高度更新，讓 App 能取得切換後的正確鍵盤範圍。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/139>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/9dbe1a86a96fe676ac7a79e75f232673a59d3b8c>
