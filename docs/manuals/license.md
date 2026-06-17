# 版權說明

萊姆輸入法是自由開源軟體，採用 GNU General Public License v3 授權。內建與下載的輸入法碼表、字典與第三方元件各有其授權與致謝，整理於下。

## 授權條款

- **授權：**萊姆輸入法採用 GNU General Public License version 3（`SPDX-License-Identifier: GPL-3.0-only`）授權。

- **正式條款：**GNU GPL v3 正式條款以 Free Software Foundation 維護的英文版本為準：[gnu.org/licenses/gpl-3.0.en.html](https://www.gnu.org/licenses/gpl-3.0.en.html)。

- **非正式翻譯：**GNU 另提供包含繁體中文在內的非正式翻譯連結，可協助理解授權內容：[gnu.org/licenses/translations.en.html](https://www.gnu.org/licenses/translations.en.html)。

除非個別原始碼檔案或內附素材另有相容授權或致謝聲明，本程式碼庫中的萊姆輸入法原始碼皆以 GPL v3 散布。完整原始碼公開於 [GitHub](https://github.com/lime-ime/limeime)。

## 輸入法碼表致謝

萊姆輸入法內建或可下載多種繁體中文輸入法碼表與字典，特此感謝下列作者與提供者。

| 字根表 | 致謝 |

| --- | --- |

| OpenVanilla 注音字根 | 感謝 openvanilla/openvanilla |

| OpenVanilla 注音字根（BIG5 字集） | 感謝 openvanilla/openvanilla |

| OpenVanilla 大易字根 | 感謝大易輸入法作者陳贊傑授權 |

| 老刀行列字根 | 感謝行列輸入法作者廖明德授權，並感謝老刀持續提供行列碼表更新。 |

| 老刀行列 10 字根 | 感謝行列輸入法作者廖明德授權，並感謝老刀持續提供行列碼表更新。 |

| 華象直覺字根 | 感謝華象直覺輸入法發明人／製作者陳華偉授權。 |

| 筆順五碼字根 | 感謝香港長者資訊天地提供授權。 |

| 哈哈倉頡字根 | 感謝尹卂提供哈哈倉頡碼表，依 CC BY 4.0 授權釋出。來源：ejsoon.vip/haha |

> 若您是碼表作者或提供者，希望修正致謝文字或提出版權異議，請透過 [GitHub Issue](https://github.com/lime-ime/limeime/issues) 提出，我們會儘快處理。

## 輸入法碼表版權聲明

萊姆輸入法是一個輸入法框架。從網路下載或自行加入的輸入法碼表與字典，可能各自有不同的版權或授權條件。使用者與散布者加入外部碼表時，應尊重原作者或提供者的授權條款。

## 第三方開源元件

本專案使用下列開源函式庫與平台元件，其授權歸屬各自作者。

### 內附資料

| 資料 | 來源 | 授權 |

| --- | --- | --- |

| 英文詞頻字典（dictionary.db 的 basescore） | Google Books Ngrams，English 1-grams v3（2020-02-17） | CC BY 3.0 |

內附的英文 `basescore` 資料衍生自 Google Books Ngrams 英文 1-gram 詞頻，經彙整與對數縮放為詞頻分數，依 [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) 重新散布。各使用者的學習分數 `score` 屬本機私人資料，不會內附或散布。

### Android 執行階段相依套件

| 元件 | 授權 |

| --- | --- |

| Android Gradle Plugin | Apache License 2.0 |

| AndroidX（AppCompat、Core、Legacy Support、Preference、SlidingPaneLayout、Lifecycle、Fragment/Test、Espresso、AndroidX Test） | Apache License 2.0 |

| Google Material Components for Android | Apache License 2.0 |

| Kotlin Gradle Plugin / Kotlin BOM | Apache License 2.0 |

| desugar_jdk_libs | Apache License 2.0 |

| Zip4j | Apache License 2.0 |

| Google Play Billing AIDL interface | Apache License 2.0 |

### iOS 執行階段相依套件

| 元件 | 授權 |

| --- | --- |

| GRDB.swift | MIT License |

| ZIPFoundation | MIT License |

### 僅測試用相依套件

| 元件 | 授權 |

| --- | --- |

| JUnit 4 | Eclipse Public License 1.0 |

| Mockito Core / Mockito Android | MIT License |

上述僅測試用相依套件用於開發與自動化測試，不會隨應用程式一同散布。
