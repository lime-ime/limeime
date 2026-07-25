# 萊姆中文輸入法 - LIME IME v6.1.37

這版更新 Android 與 iOS 的輸入、碼表及舊資料相容性，並修正 Android 特定欄位的重複輸入問題。

> **Android 相容性注意：** v6.1.37 最低支援 Android 7.1（API 25），目標 SDK 為 API 37。

> **Android 發行管道：** Google Play 版本使用套件名稱 `org.limeime`。GitHub Release APK 使用套件名稱 `net.toload.main.hd2026`，與舊 GitHub APK 相容。Android 會把兩者視為不同 App，可以同時安裝，但不能互相直接更新。Google Play 使用者請從 Google Play 更新。

Android 正式版可從 [Google Play](https://play.google.com/store/apps/details?id=org.limeime) 下載。GitHub Release 另提供 Android APK。iPhone／iPad 正式版請從 [Apple App Store](https://apps.apple.com/app/id6784694460) 更新。

## 更新內容

### Android

- **修正特定英文欄位重複輸入**
  - 修正 LINE 加好友 ID 搜尋等格式異常的英文欄位中，從中文鍵盤輸入一個英文字母可能出現兩次的問題。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/200>

- **修正標點結束鍵送出行為**
  - 修正以逗號或句點作為結束鍵時，候選與標點未一起送出，以及部分路徑可能殘留舊候選的問題。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/196>

- **更新哈哈倉頡碼表**
  - 更新至 `20260723_082459`，並修正關閉選取排序時的候選順序。
  - 已安裝哈哈倉頡的使用者需先移除，再重新下載碼表，才會套用新版內容。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/194>

- **改善數字與電話版面**
  - 新增可直接點按的句點與逗號，括號仍可長按輸入。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/204>

- **改善舊輸入法資料相容性**
  - 資料庫更新至 105，補強舊版標準輸入法的按鍵資訊，改善匯出、還原與跨平台重新匯入。

### iOS

- **修正標點結束鍵候選狀態**
  - 修正部分結束鍵路徑可能重複加入標點或短暫保留舊候選的問題。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/196>

- **更新哈哈倉頡碼表**
  - 更新至 `20260723_082459`，並修正關閉選取排序時的候選順序。
  - 已安裝哈哈倉頡的使用者需先移除，再重新下載碼表，才會套用新版內容。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/194>

- **改善數字與電話版面**
  - 新增可直接點按的句點與逗號，括號仍可長按輸入。

- **改善碼表匯入與舊資料相容性**
  - 改善自建碼表匯入時的候選基礎排序，並補強舊版標準輸入法的按鍵資訊與匯出、還原流程。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/176>
