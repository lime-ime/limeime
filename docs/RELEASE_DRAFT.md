# LIME 2026 — 版本 v6.1.21

**版本標籤：** `v6.1.21`

**APK：** [`LIMEHD2026-6.1.21.apk`](https://raw.githubusercontent.com/lime-ime/limeime/master/LimeStudio/app/release/LIMEHD2026-6.1.21.apk)

**套件名稱：** `net.toload.main.hd2026`

**目標 SDK：** 36

**最低 SDK：** 21

**前一正式版本：** [v6.1.15](https://github.com/lime-ime/limeime/releases/tag/v6.1.15)

## 更新內容

這版整理 v6.1.15 之後的 Android 測試 APK 修正與文件更新。主要包含碼表匯入、備份還原、鍵盤主題、英文候選、初始鍵盤顯示、哈哈倉頡資料、候選列與使用手冊更新。Android APK 已附在本次 Release。同期間合併的 iOS 來源修正仍需等待後續 TestFlight／App Store 發布。

### Android 修正與改善

- **#88 — 舊備份還原後 emoji FTS 索引初始化失敗**
  - 補強從舊版 LIME 備份還原時，既有 `emoji_fts` 索引殘留可能造成設定 app 無法再開啟的處理。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/88>
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/102>
  - 分析文件：[#88_ISSUE.md](https://github.com/lime-ime/limeime/blob/master/docs/%2388_ISSUE.md)

- **#90 — Android 鍵盤主題跟隨系統 accent / 動態色**
  - 新增鍵盤主題對系統 accent / Material You 顏色的支援，讓 Android 鍵盤視覺更貼近系統主題。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/90>
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/101>

- **#91 — `.cin` 匯入後同碼候選字順序改變**
  - 修正 Android 匯入 `.cin` 時同碼候選字順序被改動的問題。關閉「啟動選取排序」時會保留來源碼表順序。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/91>
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/101>
  - 分析文件：[#91_ISSUE.md](https://github.com/lime-ime/limeime/blob/master/docs/%2391_ISSUE.md)

- **#93 — `.lime` / `.cin` 匯入 metadata 與表格註冊補強**
  - 補強 `.lime` / `.cin` 匯入時的 `@cname@`、`@version@`、註解列與表格註冊處理，減少匯入成功但清單狀態不一致的情境。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/93>
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/101>
  - 分析文件：[#93_ISSUE.md](https://github.com/lime-ime/limeime/blob/master/docs/%2393_ISSUE.md)

- **#94 — Android 備份產生 0 B `limeBackup.zip`**
  - 改善資料庫備份錯誤傳遞與 ZIP 內容檢查，避免備份失敗卻顯示成功、產生空白備份檔。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/94>
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/101>
  - 分析文件：[#94_ISSUE.md](https://github.com/lime-ime/limeime/blob/master/docs/%2394_ISSUE.md)

- **#96 — 標點 end-key / Lime end-key 行為、設定與匯出保留**
  - 新增並補強 Android / iOS LIME 專用 `%limeendkey` / `@limeendkey@` 行為，支援指定標點鍵直接送出目前候選字。
  - Android / iOS 皆可在個別輸入法詳細設定頁調整 Lime end-key（結束鍵），匯出／重新匯入時也會保留 Lime end-key metadata。
  - 沒有設定 Lime end-key 的表格，逗號與句號根鍵仍維持一般候選字輸入邏輯。v6.1.18 也修正無 end-key 時標點候選的預設高亮狀態問題。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/96>
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/101>
  - 分析文件：[#96_ISSUE.md](https://github.com/lime-ime/limeime/blob/master/docs/%2396_ISSUE.md)

- **#99 — Shift / Caps Lock 狀態下的非英文字根標籤與 Shift 雙擊鎖定**
  - 調整 Android / iOS 注音等非英文字根鍵盤在 shifted layout 的視覺標籤，降低 Shift / Caps Lock 狀態下的顯示混淆。
  - Android / iOS 軟鍵盤 Shift 改為雙擊進入大寫鎖定。單擊 Shift 只切換一次 shifted 狀態。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/99>
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/101>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/08bf30b951fcf6dd41f4d681036685904d8a081f>、<https://github.com/lime-ime/limeime/commit/2541fc2880c344e5e2a43378635d8d0170d2f124>

- **#103 — Android 英文候選字與預測排序**
  - 保留使用者已完整輸入的英文 exact-match 候選，避免完整字被預測候選擠掉。
  - 加入英文詞庫、prefix 查詢、頻率排序與學習資料整合，改善英文模式候選排序。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/103>
  - 分析文件：[#103_ISSUE.md](https://github.com/lime-ime/limeime/blob/master/docs/%23103_ISSUE.md)

- **#104 — 送出後相關詞候選不應被 Enter/Search/Return 預設送出**
  - 修正送出一個詞後顯示的相關詞／聯想候選不應有預設高亮項目的回歸問題。
  - Enter、Search、Return 在這種瀏覽型候選列狀態下會正常 pass-through，不會誤送出第一個相關候選。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/104>
  - 分析文件：[#104_ISSUE.md](https://github.com/lime-ime/limeime/blob/master/docs/%23104_ISSUE.md)

- **#107 — Android 切換到 LIME 時啟動過慢**
  - 減少切換到 LIME 時的同步初始化負擔，延後完整 emoji 內容渲染、降低重複設定讀取與預載工作。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/107>
  - 分析文件：[#107_ISSUE.md](https://github.com/lime-ime/limeime/blob/master/docs/%23107_ISSUE.md)

- **#114 — Duolingo 英文候選列偶發空白**
  - 修正背景英文預取查詢可能清掉 runtime suggestion 狀態的路徑，降低 Duolingo 等 app 中英文候選列偶發空白的情況。
  - Reporter 已確認 Android APK `LIMEHD2026-6.1.19.apk` 測試改善，issue 已關閉。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/114>
  - 分析文件：[#114_ISSUE.md](https://github.com/lime-ime/limeime/blob/master/docs/%23114_ISSUE.md)

- **#115 — 新增／切換輸入法後初始鍵盤顯示錯誤**
  - 修正新增第一個非注音輸入法、加入第二個輸入法或從特定輸入欄位回到一般文字欄位時，Android 可能用到過期鍵盤設定快照而顯示錯誤初始鍵盤的問題。
  - 6.1.20 已改善部分恢復路徑，6.1.21 進一步在畫出中文鍵盤前重新整理 IM 鍵盤設定並補上回歸測試。
  - Reporter 已確認 Android APK `LIMEHD2026-6.1.21.apk` 測試正常，issue 已關閉。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/115>
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/116>、<https://github.com/lime-ime/limeime/pull/118>
  - 分析文件：[#115_ISSUE.md](https://github.com/lime-ime/limeime/blob/master/docs/%23115_ISSUE.md)

- **#112 — 哈哈倉頡 / 四碼倉頡資料與 Lime end-key metadata**
  - 更新哈哈倉頡相關 `.limedb` 匯入／匯出資料，補齊 Lime end-key metadata，讓表格資料與 catalog 行為更一致。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/112>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/650ca4f0>、<https://github.com/lime-ime/limeime/commit/4f4e9706>

- **候選 popup 與隱藏鍵盤狀態對齊**
  - 修正隱藏鍵盤時 expanded candidate popup 的對齊問題。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/676f9b4d>

- **首次安裝預設輸入法啟用補強**
  - 修正新安裝後應啟用已啟用輸入法，避免錯誤退回英文狀態。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/680d34e5>

- **App 名稱與版本更新**
  - Android app 顯示名稱更新為「萊姆輸入法6」。
  - 版本更新至 `6.1.21`，並附上對應 release APK。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/4f4e9706>

### 使用手冊與文件更新

- 新增並同步 `docs/pages/` 與 `docs/manuals/` 使用手冊內容，包含快速上手、鍵盤輸入、輸入法管理、喜好設定、備份還原、疑難排解、FAQ、隱私與版權頁面。
- FAQ 新增 Android 與 iPhone/iPad 完整資料庫備份可跨平台還原的說明。碼表、字根資料、關聯字詞、學習詞與候選排序會隨資料庫還原，平台專屬設定仍需在目標平台重新確認。
- FAQ 與鍵盤輸入頁補充刪除鍵邏輯。有組字碼時刪除最後一碼組字碼，沒有組字碼時刪除游標前方已送出的文字。候選列左側 X 用來取消目前組字或關閉候選列，作用類似電腦輸入法的 Esc。
- 新增 LIME Settings 設計系統與文件站頁面，並整理 Android / iOS 設定畫面的設計資產與截圖。

### iOS 來源同步更新

> 本次 GitHub Release 附上的安裝檔是 Android APK。以下為同一期間已合併到 `master` 的 iOS 來源與測試更新。iOS 使用者仍需等待後續 TestFlight／App Store 發布。

- **#86 — iOS restore 後鍵盤 extension 狀態同步**
  - 還原後鍵盤 extension 會重新開啟資料庫 runtime，並同步預設資料庫與已啟用輸入法狀態。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/86>
  - 分析文件：[#86_ISSUE.md](https://github.com/lime-ime/limeime/blob/master/docs/%2386_ISSUE.md)

- **#91 / #94 — iOS 表格順序與備份安全性同步**
  - 同步改善 iOS 匯入表格順序與備份安全性，降低與 Android 行為差距。
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/101>

- **#93 / #96 — iOS metadata 與 Lime end-key 同步**
  - 同步改善 iOS `.lime` metadata、匯入註冊與 Lime end-key 儲存／讀取行為，並可在個別輸入法詳細設定頁編輯 Lime end-key（結束鍵）。
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/101>

- **#99 / #100 — iOS 鍵盤視覺狀態與 contextual return key 對比**
  - 調整 shifted label、鍵盤視覺狀態與 contextual return/send key 的亮暗色對比。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/99>、<https://github.com/lime-ime/limeime/issues/100>
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/101>

- **iOS 主題截圖測試補強**
  - 修正 iOS theme-screenshot UITest，讓截圖測試顯示正確主題下的注音鍵盤。
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/108>

## 已知待處理項目

- **#111 — 快倉 `scj` 表格資料 `x` / `z` 候選異常**
  - 目前確認為表格資料待修正項目。`Database/scj.db` 中 `x` 與 `z` 仍可能出現 `1991` 作為前方候選。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/111>
  - 分析文件：[#111_ISSUE.md](https://github.com/lime-ime/limeime/blob/master/docs/%23111_ISSUE.md)

## APK 驗證資訊

- APK 路徑：`LimeStudio/app/release/LIMEHD2026-6.1.21.apk`
- APK raw link：<https://raw.githubusercontent.com/lime-ime/limeime/master/LimeStudio/app/release/LIMEHD2026-6.1.21.apk>
- GitHub Contents blob SHA：`a8838c47b4186956536cd4c8aa4e3931d579d1da`
- GitHub Contents size：`14055188` bytes
