# 萊姆中文輸入法 - LIME IME v6.1.30

**Google Play 套件名稱：** `org.limeime`

**目標 SDK：** 36

**最低 SDK：** 21

**前一正式版本：** [v6.1.29](https://github.com/lime-ime/limeime/releases/tag/v6.1.29)

這版修正 Android 與 iOS 行列30 `hg` 加數字鍵的符號候選輸入，並改善 Android 候選列與 iOS 彈出鍵盤的穩定性。Android 也完成內部套件結構整理，iOS 則改善不同裝置與版面上的彈出鍵盤尺寸及按鍵回饋時機。

Android 正式版可從 [Google Play](https://play.google.com/store/apps/details?id=org.limeime) 下載，GitHub Release 也會提供 Android APK。iPhone／iPad 正式版請從 [Apple App Store](https://apps.apple.com/app/id6784694460) 更新。

## 更新內容

### Android

- **修正行列30 `hg` 數字符號輸入**
  - 修正輸入 `hg` 加數字鍵時，無法顯示碼表中對應符號候選的問題。
  - 支援 `hg0`、`hg1`、`hg2`、`hg8` 與 `hg9` 等既有符號列表。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/155>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/81d8dcdc225a117b1664231e135c555f5abfd9ef>

- **改善候選列穩定性**
  - 避免候選列已離開畫面時仍嘗試開啟展開候選視窗。
  - 過濾無效候選資料，避免影響候選顯示與操作。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/92c1790aa5adcb2b4755b4ad73fc7e9b83c201d6>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/35196f24a34af33af3f5b82a7fd404dbe2b0dfdd>

- **改善內部套件結構與測試穩定性**
  - 將 Android 程式套件統一為 `org.limeime`，保持 Google Play App 身分不變。
  - 隔離容易受測試執行順序影響的快取測試。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/6494cd0>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/28ab37485ac8b972629f7c8b99fbf92305035d6a>

### iOS

- **修正行列30 `hg` 數字符號輸入**
  - 修正輸入 `hg` 加數字鍵時，無法顯示碼表中對應符號候選的問題。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/155>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/81d8dcdc225a117b1664231e135c555f5abfd9ef>

- **改善彈出鍵盤尺寸與穩定性**
  - 彈出按鍵會依目前鍵盤版面調整，在 iPhone、iPad、橫向、窄版與分割版面上更一致。
  - 改善彈出鍵盤與鍵盤生命週期切換時的穩定性。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/c45b2e298ceba8b4b4d24e1ecd839a4e44799474>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/ef86dbf1687536b57cc2612f51b578d3ffd2ad8f>

- **改善按鍵回饋時機**
  - 在文字送出後安排按鍵回饋，改善快速輸入時的反應。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/21cfdfdd9ede1c9fa26d36363490f84a86c9f1c7>
