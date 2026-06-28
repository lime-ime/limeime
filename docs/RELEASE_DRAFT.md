# 萊姆中文輸入法 - LIME IME v6.1.26

**套件名稱：** `net.toload.main.hd2026`

**目標 SDK：** 36

**最低 SDK：** 21

**前一正式版本：** [v6.1.24](https://github.com/lime-ime/limeime/releases/tag/v6.1.24)

這版 `6.1.26` 繼續使用 GitHub 測試版的舊套件名稱與簽署金鑰，讓已安裝 GitHub APK 的使用者可以沿著同一條測試版更新路徑升級。這次更新主要改善 Pixel、Samsung 與部分 Android 裝置的按鍵震動與按鍵音量設定，修正自動中文標點在中英文內容交界時的處理，並更新行列 30 輸入法表格資料。

> **相容性注意：** GitHub Release APK 使用套件名稱 `net.toload.main.hd2026`、versionCode `2026` 與舊 GitHub APK 相容簽署金鑰。Google Play 版本使用不同套件名稱與簽署來源，Android 會把兩者視為不同 App，可以同時安裝，但不能互相直接更新。Google Play 測試版或正式版使用者請從 Google Play 更新。若要在 Google Play 版本與 GitHub APK 之間切換使用，請先備份輸入法資料，再依需要啟用另一個版本。若要改成只保留其中一個版本，請確認資料已備份後再解除安裝不使用的版本。

GitHub Release 附上的安裝檔是 Android APK。iOS 使用者仍需等待後續 TestFlight 或 App Store 發布。

## 更新內容

### Android

- **#128 — 改善 Pixel、Samsung 與部分 Android 裝置的按鍵震動與按鍵音效行為**
  - 調整按鍵震動路徑，讓 Pixel 與 Samsung 裝置在系統觸控回饋支援不同時仍能取得較穩定的按鍵震動。
  - 新增按鍵音量偏好設定，讓使用者可以調整 LIME 按鍵音效音量。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/128>
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/133>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/1cbdae60df1fb0e73e91998b668549ff04a9b9f4>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/a51fc76f9a8cc8d9f3ad740887b7cc2e29300936>

- **自動中文標點處理修正**
  - 修正自動中文標點在中英文內容交界時移除前後空白的判斷，讓 Android 與 iOS 行為更一致。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/43336dd3fd45fde4b176db5b4ede55d149516c58>

- **行列 30 輸入法表格更新**
  - 更新內建行列 30 表格資料到 `ar30reg-v2026-1.0 20260623` 版本。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/f2446c88160b281ed901760986455dc9aec5988e>

### iOS

- **按鍵音量與自動中文標點來源更新**
  - iOS 來源同步加入按鍵音量控制，並與 Android 對齊自動中文標點處理。
  - 設定頁品牌圖示與版本設定也同步更新，供後續 TestFlight 或 App Store 發布使用。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/ec7da29e81fbaf0d7448d9813e2d4353e43e7183>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/d1fc6668710c063edb9abaf5346db2493a5a873a>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/e7ad92187026a96810078821836044700658649a>

## APK 資訊

- APK manifest：package `net.toload.main.hd2026`，versionName `6.1.26`，versionCode `2026`，minSdk 21，targetSdk 36
- APK 顯示名稱：`萊姆輸入法A`
- APK 檔案：`LIMEHD2026-6.1.26.apk`
- APK 檔案大小：7,407,267 bytes
- APK SHA-256：`ac765155f70e938c747e8afd299301a2250d5167587e3222c74be94e0a9d929c`
- APK 簽署憑證：舊 GitHub APK 相容簽署金鑰，`C=TW, ST=NA, L=Taipei, O=LIME IME, OU=LIME IME, CN=Jeremy Wu`
- APK 簽署憑證 SHA-256：`8fc24cc75da9a86ce90a0591f4d74b2a491106e8b1d72d8afe2653b5d604da34`
