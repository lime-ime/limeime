# 萊姆中文輸入法 - LIME IME v6.1.29

**套件名稱：** `net.toload.main.hd2026`

**目標 SDK：** 36

**最低 SDK：** 21

**前一正式版本：** [v6.1.28](https://github.com/lime-ime/limeime/releases/tag/v6.1.28)

這版 `6.1.29` 改善 Android 語音輸入錯誤後的復原、舊版 Android 與資料庫相容性，以及設定頁面的系統介面相容性，並新增完成設定後的商店評分提醒。iOS 修正行列30數字符號輸入，改善按鍵預覽、120Hz 顯示、觸覺回饋生命週期與輸入熱路徑效能，也加入 App Store 評分提醒。

> **相容性注意：** GitHub Release APK 使用套件名稱 `net.toload.main.hd2026`、versionCode `2026` 與舊 GitHub APK 相容簽署金鑰。Google Play 版本使用套件名稱 `org.limeime` 與 Google Play 簽署流程，Android 會把兩者視為不同 App，可以同時安裝，但不能互相直接更新。Google Play 使用者請從 Google Play 更新。若要在兩個版本之間切換，請先備份輸入法資料。

GitHub Release 附上的安裝檔是 Android APK。Android 正式版也可從 [Google Play](https://play.google.com/store/apps/details?id=org.limeime) 下載；iPhone／iPad 正式版請從 [Apple App Store](https://apps.apple.com/app/id6784694460) 更新。

## 更新內容

### Android

- **修正語音輸入錯誤後可能卡住的問題**
  - 語音辨識成功、取消或發生錯誤後，會正確結束辨識狀態並恢復一般鍵盤與候選列。
  - 語音錯誤訊息會短暫顯示後自動恢復，鍵盤可立即繼續輸入或重新啟動語音，不必切換輸入法或重新開機。
  - 避免重複回呼造成重複文字或殘留的麥克風狀態。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/154>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/d4fe8b271dccc5d8d8aa9d53d0c016a53c40e54f>

- **改善舊版 Android 與資料庫相容性**
  - 修正舊版 Android 在資料庫重新整理、備份還原及舊 SQLite 語法相容路徑可能失敗的問題。
  - 保留使用者選字分數與跨版本備份還原資料。
  - 補強 Android 7.1 與 API 25 的相容性測試。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/b8864f4088df7cc4ac0008d5ce7209c26d1eadb4>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/ea70370a6be0019c7c3bb5bd6ba0387ae7d9bbff>

- **改善設定頁面與系統介面相容性**
  - 配合新版 Android edge-to-edge 顯示規範，改善設定頁面的系統列與邊界處理。
  - 保留舊版 Android 原有的版面行為，避免畫面頂端出現多餘空白。
  - 改善通知圖示縮放，並更新 Android 15 相容元件。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/5aa502091c19631e9a0be7805e91bb743fb14623>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/76ffe2fc129a9d715105b9aed478008838fb1e4a>

- **新增 Google Play 評分提醒**
  - 完成鍵盤、輸入法及碼表設定後，設定首頁會顯示簡潔的評分卡片。
  - 可選擇稍後提醒或不再顯示，不會在尚未完成設定時打擾使用者。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/429d2b57601070a28fa5bd71837f49a51bd0cf55>

### iOS

- **修正行列30數字符號輸入**
  - 修正行列30輸入 `w` 加數字鍵時，無法顯示對應符號候選的問題。
  - 保持一般數字輸入與其他輸入法行為不變，與 Android 的既有行列30行為一致。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/153>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/83c5d5af320469af262f98883280ede193c2d1b7>

- **改善鍵盤反應與高更新率顯示**
  - 按鍵預覽會更即時顯示，改善快速輸入時的視覺反應。
  - 支援 ProMotion 裝置的 120Hz 顯示更新。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/08dbf47c0270d42565b7853df1bdd0e3c01437e1>

- **改善觸覺回饋穩定性**
  - 修正鍵盤切換、離開或重新顯示後，觸覺回饋可能重複或持續觸發的問題。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/04765141480afe7d811d02251e29f127e1971ed4>

- **改善輸入與資料同步效能**
  - 快取目前輸入法的按鍵與結束鍵設定，減少每次按鍵時的重複檔案讀取。
  - 補強未開啟完整取用時的資料庫啟動與同步狀態。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/845a82a2506af91af9797450a7d89ebdbf567da5>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/9c58ee5ef6408413a4bbb491a22dbb31d98e8f29>

- **新增 App Store 評分提醒**
  - 完成鍵盤與輸入法設定後，設定頁會顯示簡潔的五星評分卡片。
  - 可選擇稍後提醒或不再顯示。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/5c2d09bac693de4296dd64871c610aedddae4137>

## APK 資訊

- APK manifest：package `net.toload.main.hd2026`，versionName `6.1.29`，versionCode `2026`，minSdk 21，targetSdk 36
- APK 顯示名稱：`萊姆輸入法A`
- APK 檔案：`LIMEHD2026-6.1.29.apk`
- APK 檔案大小：7,257,851 bytes
- APK SHA-256：`a0fbddbf74c29430d2bb3f0da64a98d504c9542010a39768833797167350646a`
- APK 簽署憑證：舊 GitHub APK 相容簽署金鑰，`C=TW, ST=NA, L=Taipei, O=LIME IME, OU=LIME IME, CN=Jeremy Wu`
- APK 簽署憑證 SHA-256：`8fc24cc75da9a86ce90a0591f4d74b2a491106e8b1d72d8afe2653b5d604da34`
