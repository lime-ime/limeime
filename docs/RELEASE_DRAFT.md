# 萊姆中文輸入法 - LIME IME v6.1.28

**套件名稱：** `net.toload.main.hd2026`

**目標 SDK：** 36

**最低 SDK：** 21

**前一正式版本：** [v6.1.27](https://github.com/lime-ime/limeime/releases/tag/v6.1.27)

這版 `6.1.28` 繼續使用 GitHub 測試版的舊套件名稱與簽署金鑰，已安裝 GitHub APK 的使用者可以沿著同一條測試版更新路徑升級。這次更新修正 Android 電話鍵盤標示、平板與手勢導覽底部列顯示、Shift 連按兩下進入 Caps Lock 的判定，並新增可選的倉頡分號鍵版面。iOS 也包含幾個重要修正，包含表格鍵盤高度回報、英文混合輸入大小寫保留、輸入法還原與同步穩定性，以及 iPad 版面提示。

> **相容性注意：** GitHub Release APK 使用套件名稱 `net.toload.main.hd2026`、versionCode `2026` 與舊 GitHub APK 相容簽署金鑰。Google Play 版本使用不同套件名稱與簽署來源，Android 會把兩者視為不同 App，可以同時安裝，但不能互相直接更新。Google Play 測試版或正式版使用者請從 Google Play 更新。若要在 Google Play 版本與 GitHub APK 之間切換使用，請先備份輸入法資料，再依需要啟用另一個版本。若只想保留其中一個版本，請確認資料已備份後再解除安裝不使用的版本。

GitHub Release 附上的安裝檔是 Android APK。iOS 使用者仍需等待後續 TestFlight 或 App Store 發布。

## 更新內容

### Android

- **修正電話英文鍵盤標示顯示**
  - 修正行列10等電話英文鍵盤在淺色主題下，數字鍵與英文字母標示過淡或看不清楚的問題。
  - 同步改善按鍵預覽的顯示一致性。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/142>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/d466a2ebcd13>

- **修正平板與手勢導覽底部列被裁切**
  - 改善 Android 平板或手勢導覽環境下，鍵盤最底列可能被螢幕底部裁切的問題。
  - 這項修正會讓空白鍵與底部功能鍵列更穩定地完整顯示。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/145>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/58c178b55d39>

- **改善 Shift 連按兩下 Caps Lock 判定**
  - 英文鍵盤維持各主要輸入法常見的操作方式，快速連按兩下 Shift 進入 Caps Lock。
  - 調整連按判定，讓全大寫鎖定更容易穩定觸發。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/148>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/8ec3b4ccb5a9>

- **新增可選的倉頡分號鍵版面**
  - 新增 `cj_semi` 與 `cj_num_semi` 版面，讓需要分號字根的倉頡類表格可以選用。
  - 原本的倉頡版面維持不變，舊使用者升級後仍可保留原本設定。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/143>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/bbde989e9af7>

- **補強數字鍵與彈出鍵盤體驗**
  - 英文鍵盤純數字快捷鍵補上簡易電話數字鍵盤路徑。
  - 多鍵彈出鍵盤支援滑動選字，觸覺回饋也同步修正。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/124>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/a900b82f7597>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/e15eb8fdb186>

### iOS

- **重寫未開啟完整取用時的資料同步架構**
  - 大幅重寫鍵盤端與設定 App 的冷／熱資料同步流程。
  - 未開啟「完整取用」時，輸入法安裝、切換、備份還原、資料表編輯與鍵盤端同步都更穩定。
  - 修正還原或首次顯示後，鍵盤端可能看不到已安裝輸入法或狀態不同步的問題。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/557de977f46d>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/a336605e78f2>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/a454059deaa3>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/710247d7834f>

- **新增可選的倉頡分號鍵版面**
  - 新增 `cj_semi` 與 `cj_num_semi` 版面，讓需要分號字根的倉頡類表格可以選用。
  - 原本的倉頡版面維持不變，舊使用者升級後仍可保留原本設定。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/143>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/bbde989e9af7>

- **英文鍵盤長按 `123` 可快速切到簡易電話數字鍵盤**
  - 英文鍵盤與多個表格鍵盤的 `123` 快捷操作補上簡易電話數字鍵盤路徑。
  - 需要快速輸入數字時，不必先繞到完整符號鍵盤。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/124>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/a900b82f7597>

- **修正表格鍵盤高度與底部內容遮擋**
  - 修正鍵盤高度回報，使行列10、大易等表格鍵盤在輸入時更正確地反映實際高度。
  - 改善底部內容被鍵盤遮住的情況。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/139>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/f7088f2810b2>

- **英文混合輸入保留大小寫**
  - 混合英文輸入時，候選 0 會保留使用者實際輸入的大小寫。
  - 例如輸入大寫或大小寫混合字串時，不會再被候選列轉成全小寫。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/147>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/441ab48e1ab4>

- **改善觸控、按鍵預覽與彈出鍵盤操作**
  - 改善按鍵觸控判定、快速輸入、按鍵預覽與候選列更新效率。
  - 彈出鍵盤支援更完整的滑動選字，並修正觸覺回饋。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/d6ecce2f47a3>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/8a4189279cca>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/e15eb8fdb186>

- **改善電話英文鍵盤與 iPad 鍵盤版面提示**
  - 改善電話英文鍵盤的按鍵標示與彈出預覽顯示。
  - 修正 iPad 版面的鍵盤提示與 Shift 狀態產生邏輯。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/142>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/d466a2ebcd13>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/869304a27d54>

## APK 資訊

- APK manifest：package `net.toload.main.hd2026`，versionName `6.1.28`，versionCode `2026`，minSdk 21，targetSdk 36
- APK 顯示名稱：`萊姆輸入法A`
- APK 檔案：`LIMEHD2026-6.1.28.apk`
- APK 檔案大小：7,192,114 bytes
- APK SHA-256：`8fbd8468b3c9a6c38f5368c39ce0de51b8dd515e2bbf060b2e28e315d012555e`
- APK 簽署憑證：舊 GitHub APK 相容簽署金鑰，`C=TW, ST=NA, L=Taipei, O=LIME IME, OU=LIME IME, CN=Jeremy Wu`
- APK 簽署憑證 SHA-256：`8fc24cc75da9a86ce90a0591f4d74b2a491106e8b1d72d8afe2653b5d604da34`
