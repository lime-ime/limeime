# 萊姆中文輸入法 - LIME IME v6.1.24

**套件名稱：** `net.toload.main.hd2026`

**目標 SDK：** 36

**最低 SDK：** 21

**前一正式版本：** [v6.1.23](https://github.com/lime-ime/limeime/releases/tag/v6.1.23)

這版 `6.1.24` 繼續使用 GitHub 測試版的舊套件名稱與簽署金鑰，讓已安裝 GitHub APK 的使用者可以沿著同一條測試版更新路徑升級。這次更新主要改善 Android 按鍵震動相容性、匯入輸入法表格後的鍵盤配置與鍵名保留，以及設定頁在空狀態與平板版面下的顯示。

> **相容性注意：** GitHub Release APK 使用套件名稱 `net.toload.main.hd2026`、versionCode `2026` 與舊 GitHub APK 相容簽署金鑰。Google Play 版本使用不同套件名稱與簽署來源，Android 會把兩者視為不同 App，可以同時安裝，但不能互相直接更新。Google Play 封閉測試使用者請從 Google Play 更新。若要在 Google Play 版本與 GitHub APK 之間切換使用，請先備份輸入法資料，再依需要啟用另一個版本。若要改成只保留其中一個版本，請確認資料已備份後再解除安裝不使用的版本。

GitHub Release 附上的安裝檔是 Android APK。iOS 使用者仍需等待後續 TestFlight 或 App Store 發布。

## 更新內容

### Android

- **#128 — 改善 Samsung 與部分 Android 16 裝置的按鍵震動相容性**
  - 當裝置或系統 HAL 不支援預設 haptic constant 時，改用一次性震動作為按鍵震動 fallback。
  - Android 13 以上會搭配觸控用途的 vibration attributes，讓「打字震動」更接近系統觸控回饋行為。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/128>
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/132>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/e0659dac3670e42b0970cae54fdc7fd299c2a19e>

- **#131 — 改善匯入 `.lime` / `.cin` 表格後的鍵盤配置與中英文鍵名保留**
  - 匯入表格時會保留 `@imkeys@` / `@imkeynames@` 等鍵名 metadata，避免重新匯出或重新匯入後遺失表格鍵盤資訊。
  - 依照使用者選擇的目標表格套用預設鍵盤配置，讓倉頡、快倉、行列、行列10、大易、嘸蝦米等表格在匯入後更容易使用正確鍵盤。
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/131>

- **設定頁與平板版面改善**
  - 輸入法清單沒有安裝項目時，設定頁會顯示更清楚的提示，協助使用者新增輸入法。
  - 平板版設定頁側邊導覽在較窄視窗下仍保留標籤文字，降低只剩圖示時不容易辨識設定項目的情況。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/606688c0f7e9d48eac5e2f4aa47e85532d8882f5>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/c71d531d448ef8282e11176e3a94874b8cdf9258>

### iOS

- **匯入表格與設定流程來源更新**
  - iOS 也同步整理匯入表格後的 metadata 保留與預設鍵盤配置路徑。
  - 設定流程加入已安裝輸入法狀態與空狀態提示的來源更新，讓後續 TestFlight 或 App Store 發布時能提供一致的設定體驗。
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/131>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/606688c0f7e9d48eac5e2f4aa47e85532d8882f5>

## APK 資訊

- APK manifest：package `net.toload.main.hd2026`，versionName `6.1.24`，versionCode `2026`，minSdk 21，targetSdk 36
- APK 顯示名稱：`萊姆輸入法A`
- APK 檔案：`LIMEHD2026-6.1.24.apk`
- APK 檔案大小：7,406,087 bytes
- APK SHA-256：`33b59c1ced50d179d218807d74e40bd2efa669ef99fa7bf119a6cdfd827963c6`
- APK 簽署憑證：舊 GitHub APK 相容簽署金鑰，`C=TW, ST=NA, L=Taipei, O=LIME IME, OU=LIME IME, CN=Jeremy Wu`
- APK 簽署憑證 SHA-256：`8fc24cc75da9a86ce90a0591f4d74b2a491106e8b1d72d8afe2653b5d604da34`
