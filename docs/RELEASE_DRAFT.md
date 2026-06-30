# 萊姆中文輸入法 - LIME IME v6.1.27

**套件名稱：** `net.toload.main.hd2026`

**目標 SDK：** 36

**最低 SDK：** 21

**前一正式版本：** [v6.1.26](https://github.com/lime-ime/limeime/releases/tag/v6.1.26)

這版 `6.1.27` 繼續使用 GitHub 測試版的舊套件名稱與簽署金鑰，已安裝 GitHub APK 的使用者可以沿著同一條測試版更新路徑升級。這次更新加入倉頡分號鍵與行列10電腦數字鍵盤版面，改善英文鍵盤長按 `123`、候選列清除鍵、倚天注音 41 鍵版面，以及底部輸入框附近的提示位置。iOS 也修正了幾個重要問題，包含螢幕邊緣按鍵長按漏按、中文標點寬度、行列10數字欄位鍵盤與鍵盤大小套用問題。

> **相容性注意：** GitHub Release APK 使用套件名稱 `net.toload.main.hd2026`、versionCode `2026` 與舊 GitHub APK 相容簽署金鑰。Google Play 版本使用不同套件名稱與簽署來源，Android 會把兩者視為不同 App，可以同時安裝，但不能互相直接更新。Google Play 測試版或正式版使用者請從 Google Play 更新。若要在 Google Play 版本與 GitHub APK 之間切換使用，請先備份輸入法資料，再依需要啟用另一個版本。若只想保留其中一個版本，請確認資料已備份後再解除安裝不使用的版本。

GitHub Release 附上的安裝檔是 Android APK。iOS 使用者仍需等待後續 TestFlight 或 App Store 發布。

## 更新內容

### Android

- **倉頡鍵盤新增分號鍵支援**
  - 倉頡鍵盤可加入分號鍵，方便四碼倉頡、四碼倉碩等需要分號字根的表格使用。
  - 輸入法明細頁新增設定，需要這個字根的倉頡表格可以自行啟用。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/140>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/1004453b8682>

- **行列10新增電腦數字鍵盤版面**
  - 行列10新增電腦數字鍵盤順序，數字排列為上排 `7 8 9`、中排 `4 5 6`、下排 `1 2 3`。
  - 原本的電話數字鍵盤順序仍保留，使用者可以依照習慣選用。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/5c8fdf745a75>

- **英文鍵盤長按 `123` 可快速切到數字鍵盤**
  - 點按 `123` 仍維持原本切換符號鍵盤的行為。
  - 長按 `123` 會切到注音／簡易數字鍵盤，方便快速輸入數字。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/124>
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/135>

- **候選列清除鍵加寬**
  - 候選列左側清除鍵加寬，改善拇指輸入時不易點到的問題。
  - 按鍵高度、顏色、圖示與清除組字功能維持不變。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/124>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/aa7c9875909f>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/c313f8904fe8>

- **倚天注音 41 鍵版面調整**
  - 調整 `ㄘ` 的位置，並修正 shifted 版面的字根標示。
  - 補上長按數字提示，讓版面提示更完整。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/137>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/c1c83005bc3c>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/732f818841d5>

- **改善底部輸入框附近的提示位置**
  - 調整組字提示與反查提示的位置，降低在聊天 App 底部輸入框上方遮住文字的機率。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/124>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/431762f76b28>

### iOS

- **修正螢幕邊緣按鍵長按漏按**
  - 修正靠近螢幕邊緣的按鍵在長按時可能沒有預覽、震動或按鍵回應的問題。
  - 這項修正會改善最右側按鍵在靠近邊框處長按時被系統邊緣手勢延遲吃掉的情況。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/1004453b8682>

- **新增倉頡分號鍵、行列10電腦數字鍵盤與英文 `123` 長按功能**
  - 倉頡鍵盤可加入分號鍵，方便需要分號字根的倉頡表格使用。
  - 行列10新增電腦數字鍵盤版面，可依照使用習慣選用。
  - 英文鍵盤的 `123` 鍵可長按切到簡易數字鍵盤。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/1004453b8682>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/5c8fdf745a75>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/4d07d0a796e7>

- **行列10數字欄位與鍵盤大小修正**
  - 改善行列10在數字欄位中的鍵盤選擇邏輯，避免不必要地切到一般符號鍵盤。
  - 修正簡易數字鍵盤沒有正確套用鍵盤大小設定的問題。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/139>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/0863e6f07e61>

- **中文標點寬度與倚天注音 41 鍵版面修正**
  - 修正中文標點寬度處理，讓標點顯示與輸入結果更符合中文輸入情境。
  - 調整倚天注音 41 鍵版面與長按數字提示。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/137>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/c1c83005bc3c>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/732f818841d5>

## APK 資訊

- APK manifest：package `net.toload.main.hd2026`，versionName `6.1.27`，versionCode `2026`，minSdk 21，targetSdk 36
- APK 顯示名稱：`萊姆輸入法A`
- APK 檔案：`LIMEHD2026-6.1.27.apk`
- APK 檔案大小：7,410,887 bytes
- APK SHA-256：`299d579df4dc2ffdceabdb038f708b46098dd721bbcd271f522ebd239d4ae653`
- APK 簽署憑證：舊 GitHub APK 相容簽署金鑰，`C=TW, ST=NA, L=Taipei, O=LIME IME, OU=LIME IME, CN=Jeremy Wu`
- APK 簽署憑證 SHA-256：`8fc24cc75da9a86ce90a0591f4d74b2a491106e8b1d72d8afe2653b5d604da34`
