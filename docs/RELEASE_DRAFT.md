# 萊姆中文輸入法 - LIME IME v6.1.23

**套件名稱：** `org.limeime`

**目標 SDK：** 36

**最低 SDK：** 21

**前一正式版本：** [v6.1.22](https://github.com/lime-ime/limeime/releases/tag/v6.1.22)

這版 `6.1.23` 持續整理 Android 與 iOS 的輸入體驗。Android 主要改善在 LINE、WeChat、Instagram 這類下方訊息輸入欄 App 內的字根顯示與字根反查浮動視窗位置，也讓設定頁在尚未安裝輸入法時有更清楚的提示。iOS 來源則加入 iPad 11 吋與 iPad mini 的較窄鍵盤尺寸支援，讓小尺寸 iPad 上的按鍵比例更接近方形。

GitHub Release 附上的安裝檔是 Android APK。iOS 使用者仍需等待後續 TestFlight 或 App Store 發布。

> **注意：** Google Play 版本與 GitHub Release APK 使用不同簽署金鑰。兩者不能互相直接更新或升級。Google Play 封閉測試使用者請從 Google Play 更新。若要在 Google Play 版本與 GitHub APK 之間切換，請先備份輸入法資料，解除安裝原本版本後再重新安裝。

## 更新內容

### Android

- **#124 — 改善下方訊息輸入欄 App 內的字根浮動視窗顯示**
  - 改善 LINE、WeChat、Instagram 等下方對話框 App 中，行列輸入時字根顯示與選字後字根反查浮動視窗的位置一致性。
  - 字根反查恢復短時間顯示，仍可在下一次 LIME 按鍵時提前消失，避免選字後灰色提示停留過久。
  - 候選列相關浮動提示改為一致地依候選列上方位置顯示，降低字根顯示與字根反查兩種提示彼此錯位的情況。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/124>
  - 相關 PR：<https://github.com/lime-ime/limeime/pull/125>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/61cf87b65f03f69486e112bf1dc1383c9974a125>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/9fc84f97eaddfea5f550268e950695dadbb3fea5>

- **設定頁輸入法清單空狀態改善**
  - 當尚未安裝任何輸入法時，輸入法清單頁會顯示較清楚的空狀態說明，協助使用者找到新增輸入法的操作入口。
  - 設定流程的輸入法安裝狀態顯示同步更新，讓初次設定時更容易判斷目前是否已安裝輸入法。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/adf370494d30786ff34d339775252c18b5c87502>

- **#127 — 恢復快倉字根下載檔案**
  - 恢復 Android 內建下載路徑仍會使用的 `Database/scj.zip` 檔案，避免快倉字根安裝時下載到不存在的檔案後顯示匯入失敗。
  - 相關 issue：<https://github.com/lime-ime/limeime/issues/127>
  - 相關提交：<https://github.com/lime-ime/limeime/commit/2f0ecdf58a1f8854636456c8fcaae355e40442df>

### iOS

- **iPad 11 吋與 iPad mini 鍵盤尺寸支援**
  - 新增較窄 iPad 鍵盤配置，讓 iPad 11 吋與 iPad mini 上的按鍵高度與寬度更接近方形，降低按鍵過高的視覺問題。
  - 保留五列鍵盤配置，避免小尺寸 iPad 為了縮高度而失去常用數字列。
  - iPad 13 吋與 iPhone 既有配置維持原本路徑，不受這次窄版 iPad 配置影響。
  - 相關提交：<https://github.com/lime-ime/limeime/commit/a318e8c578d7241c875dd28c2806a750c4a0a620>

## APK 資訊

- APK manifest：package `org.limeime`，versionName `6.1.23`，versionCode `202661230`，minSdk 21，targetSdk 36
- APK 檔案：`LIMEHD202661230-6.1.23.apk`
- APK 檔案大小：7,406,573 bytes
- APK SHA-256：`e64db9d33118dfc4bf127f951f5a0f873d939918496a54cc89254c71fe31eb95`
- APK 簽署憑證：LIME IME upload key，`C=TW, ST=Taiwan, L=Taipei, O=LIME IME Team, OU=LIME IME Team, CN=LIME IME`
