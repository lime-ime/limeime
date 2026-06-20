# 萊姆中文輸入法 - LIME IME v6.1.22

**套件名稱：** `org.limeime`

**目標 SDK：** 36

**最低 SDK：** 21

**前一正式版本：** [v6.1.21](https://github.com/lime-ime/limeime/releases/tag/v6.1.21)

這版 `6.1.22` APK 是 LIME IME 首次上傳至 Google Play 封閉測試（alpha testing）並同步發行的 APK。這次整理上架封閉測試所需的 Android release build 設定，Android applicationId 更新為 `org.limeime`，App 顯示名稱調整為「萊姆輸入法」。由於 6.1.22 APK 使用新的套件名稱與不同簽章，可以和 6.1.21 以前的 APK 同時安裝在同一台裝置上。這版也收錄 v6.1.21 之後合併到 `master` 的 Android 與 iOS 來源修正。GitHub Release 附上的安裝檔是 Android APK。iOS 使用者仍需等待後續 TestFlight／App Store 發布。

> **注意：** Google Play 版本與 GitHub Release APK 使用不同簽署金鑰。兩者不能互相直接更新或升級。如果要從 GitHub APK 改用 Google Play 版本，或從 Google Play 版本改用 GitHub APK，請先備份輸入法資料，再解除安裝原本版本後重新安裝。

## 更新內容

### Android release 與 Google Play 封閉測試準備

- **Release build 改善**
  - 移除 release build 的測試覆蓋率插樁，避免 Play Console 判定上傳套件為 debuggable。
  - 啟用 R8 minify 與資源 shrink，縮小 release build 體積，並保留可用於 crash de-obfuscation 的必要資訊。
  - 補上 ProGuard keep rules，保留 XML 載入的自訂 View、Preference、billing AIDL 與 View 建構子。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/8e7ce6e40c522cd7cdb3b987cf8431e3fcf323b1>

- **Android 匯出儲存流程更新**
  - 更新本機儲存選擇流程，從舊的 `startActivityForResult` / `onActivityResult` 改為 AndroidX Activity Result API。
  - 這項調整用於輸入法表格與相關資料匯出／儲存流程，讓設定頁的匯出流程更符合新版 Android API。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/8e7ce6e40c522cd7cdb3b987cf8431e3fcf323b1>

### Android / iOS 來源修正與改善

- **#119 — `.lime` / `.cin` 匯入後預設鍵盤配置補強**
  - Android / iOS 皆補上已知輸入法匯入後的明確預設鍵盤配置，讓 `.lime` / `.cin` 文字匯入後更容易取得正確鍵盤 layout。
  - Android 讓 `scj` 與 `pinyin` 等匯入表格的鍵盤對應更明確。iOS 文字匯入後會寫入鍵盤設定列，避免依賴 runtime fallback。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/119>
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/120>
  - 分析文件：[#119_ISSUE.md](https://github.com/lime-ime/limeime/blob/master/docs/%23119_ISSUE.md)

- **#121 — iOS 雲端／下載輸入法首次切換時 layout 與輸入模式同步**
  - 修正 iOS 從下載來源安裝輸入法後，首次切換到該輸入法時，鍵盤可視 layout 與實際中文／英文模式可能不同步的問題。
  - 設定端同步已啟用輸入法狀態時會維持 `keyboard_list` 一致，鍵盤 extension 在資料庫初始化後也會重新套用目前欄位模式與 layout。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/121>
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/122>
  - 分析文件：[#121_ISSUE.md](https://github.com/lime-ime/limeime/blob/master/docs/%23121_ISSUE.md)

- **#115 後續 iOS layout 同步修正**
  - iOS 鍵盤資料庫 setup 完成後，會重新套用 resolved IM layout，降低首次啟用或切換後顯示舊 layout 的風險。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/1f0d6715860eb9b540697f81ac118e555edc0444>

### 文件與專案整理

- **README 重新整理**
  - README 改為偏向開發者導覽，使用者文件改導向 GitHub Pages 使用手冊。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/78b07ef88ac5d6a43d3ea76d616d7d4b16110496>

- **iOS 偏好設定截圖更新**
  - 更新 iOS 偏好設定頁的亮色與深色截圖資產。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/65508d084c3e3bca6f0306bcaaae47f2a01adf7d>

- **IDE 專案檔整理**
  - 停止追蹤 `.idea` IDE 工作區檔案，降低開發環境產生的雜訊。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/a61f67eb02c7a1128e283e0196431096273069c4>

- APK manifest：package `org.limeime`，versionName `6.1.22`，versionCode `202661221`，minSdk 21，targetSdk 36
- APK 檔案大小：7,399,211 bytes
- APK SHA-256：`d156ac959c87312b3d5a20783117b8a1c1014b0f1313e3feeff6c6db8d1a96e5`
