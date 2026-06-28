### v6.1.26 更新內容

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
