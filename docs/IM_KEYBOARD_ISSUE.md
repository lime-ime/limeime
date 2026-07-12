# IM ↔ Keyboard Resolution — Cross-Platform Bug Report

Status: Investigation complete. Plan only — no source edits yet.
Scope: Android + iOS. No `.limedb` seed-file changes. No runtime `lime.db` schema changes.

---

## 1. Symptom

User installs the **拼音 (Pinyin)** cloud IM via the Settings *IM Store*.

| Platform | Visible symptom |
|---|---|
| iOS | Switching to Pinyin shows an English QWERTY keyboard (`lime_english` / `lime_english_number`) instead of the intended `LIME+數字列鍵盤` (`lime_number`). Other IMs render correctly. |
| Android | The keyboard itself **is correctly rendered** as `LIME+數字列鍵盤` (`lime_number`). But the Settings → *Manage IM* screen's "current keyboard" button shows the IM's own name (`拼音輸入法`) instead of the keyboard description (`LIME+數字列鍵盤`). Same wrong button text appears for **every** other IM on first open (`大易輸入法`, `行列輸入法`, …). The keyboard-selection dialog also lacks a "current selection" indicator. |

So: **two distinct bugs**, one per platform, both surfaced by Pinyin but with different root causes.

---

## 2. Verified facts (ground truth)

### 2.1 Cloud `pinyin.zip` contents
Downloaded directly from `https://github.com/lime-ime/limeime/raw/master/Database/pinyin.zip`. Its `pinyin.db` `im` table:

```
87|pinyin|source  |pinyin                              |       (kv row)
88|pinyin|name    |拼音輸入法                          |       (kv row, IM full name)
89|pinyin|original|pinyin                              |       (kv row)
90|pinyin|amount  |34919                               |       (kv row)
91|pinyin|import  |Thu May 21 22:06:15 台北標準時間 2015|       (kv row)
92|pinyin|keyboard|LIME+數字列鍵盤                     |limenum (kv row, AUTHORITATIVE keyboard mapping)
```

So the cloud DB *does* declare `pinyin → limenum`. The `keyboard` table on a real device contains a `limenum` row mapping to `imkb=lime_number`, and `lime_number.json` exists in iOS Layouts and `R.xml.lime_number` exists on Android.

### 2.2 Android live state (verified via `adb shell run-as … cat databases/lime.db`)
After a clean Pinyin install, `lime.db` correctly contains all 6 cloud kv rows:

```
87|pinyin|source  |pinyin            |
88|pinyin|name    |拼音輸入法        |
89|pinyin|original|pinyin            |
90|pinyin|amount  |34919             |
91|pinyin|import  |…                 |
92|pinyin|keyboard|LIME+數字列鍵盤   |limenum   ← present
```

The `keyboard` table also contains the `limenum` row (`imkb=lime_number`). My earlier hypothesis ("INSERT INTO im SELECT * collides on `_id`") was **wrong** — the import succeeded.

### 2.3 iOS live state
[`importFromAttachedDB`](../LimeIME-iOS/Shared/Database/LimeDB.swift#L2262) only copies the IM **data** table (`code`/`word`/`score`/`basescore`/`code3r`). It **never reads or writes the cloud DB's `im` table**. After install, [`registerIM`](../LimeIME-iOS/Shared/Database/LimeDB.swift#L2464) writes a single synthetic `im` row using the catalog value — for Pinyin that's `keyboardId: "pinyin"` from [IMCatalog.swift L170](../LimeIME-iOS/LimeSettings/IMCatalog.swift#L170).

So on iOS the cloud's `keyboard=limenum` mapping is silently dropped and replaced with `keyboard=pinyin`, which doesn't match anything.

---

## 3. Root causes

### 3.1 iOS — only Pinyin breaks
[`resolvedLayoutId(for: "pinyin")`](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L580) walks:

1. `LayoutLoader.load("pinyin")` → nil (no `pinyin.json`).
2. `searchServer?.getKeyboardConfig("pinyin")?.imkb` → nil (no `keyboard` table row with `code='pinyin'`; the `getKeyboardConfig` Swift fallback only hardcodes `wb`/`hs`).
3. Final fallback `"lime_\(tableNick)"` → `"lime_pinyin"` → also nil.

Then [L408–411](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L408):
```swift
if let newLayout = LayoutLoader.load(layoutName) ?? LayoutLoader.load(englishLayout), …
```
falls back to **English QWERTY**, NOT to last-used.

**Why only Pinyin:** every other IM in [IMCatalog.swift](../LimeIME-iOS/LimeSettings/IMCatalog.swift) hardcodes a `keyboardId` value that *happens* to match a real `keyboard` table row (`phonetic`, `cj`, `dayisym`, `arraynum`, `phone_simple`, `ez`, `wb`, `hs`). Pinyin alone hardcodes `"pinyin"` which has no row.

### 3.2 Android — keyboard renders correctly; UI label is wrong
The actual keyboard chain works:
- `LIMEService` → `getAllImKeyboardConfigList()` → reads `im` rows where `title='keyboard'` → `imConfigMap` correctly contains `{ "pinyin" → "limenum", … }`.
- `LIMEKeyboardSwitcher.setKeyboardMode("pinyin")` ([L455–462](../LimeStudio/app/src/main/java/org/limeime/LIMEKeyboardSwitcher.java#L455)): `localImCode = imConfigMap.get("pinyin") = "limenum"`; `kConfig = kbMap.get("limenum")` → real keyboard row → `lime_number` XML rendered. ✅
- Empty/null fallback path: `localImCode = "lime"` (English QWERTY), NOT last-used.

The bug is **only in the Settings screen label**:

[`ManageImFragment.java` L240–249](../LimeStudio/app/src/main/java/org/limeime/ui/view/ManageImFragment.java#L240):
```java
List<ImConfig> imConfigFullNamelist = manageImController.getImConfigFullNameList();
for (ImConfig imConfig : imConfigFullNamelist) {
    if (imConfig.getCode().equals(table)) {
        btnManageImKeyboard.setText(imConfig.getDesc());   // ← IM full name, not keyboard desc
        break;
    }
}
```

`getImConfigFullNameList()` filters `title='name'`, so `imConfig.getDesc()` is the IM full name (`拼音輸入法`, `大易輸入法`, …). The button **should** show the *keyboard*'s `desc` (e.g. `LIME+數字列鍵盤`), looked up via the `title='keyboard'` kv row → `keyboard` table row.

This bug affects **every IM on first open**, not just Pinyin. Manually picking a keyboard from the dialog calls [`updateKeyboard()`](../LimeStudio/app/src/main/java/org/limeime/ui/view/ManageImFragment.java#L376) which sets the correct `k.getDesc()` text — that's why the second screenshot looks right.

### 3.3 Android — keyboard-selection dialog has no current-selection indicator

[`ManageImKeyboardDialog.java` L127–140](../LimeStudio/app/src/main/java/org/limeime/ui/dialog/ManageImKeyboardDialog.java#L127) builds the keyboard list with `android.R.layout.simple_list_item_1` and a plain `ArrayAdapter`. The current keyboard is not highlighted. Should use `simple_list_item_single_choice` + `CHOICE_MODE_SINGLE` + `setItemChecked(currentIndex, true)`.

### 3.4 Fallback policy — divergence

User asked: "is fallback iOS = last-used, Android = limenum?" — neither is correct.

| | iOS | Android |
|---|---|---|
| When the resolved layout id can't load | English QWERTY (`lime_english` / `lime_english_number`) | English QWERTY (`lime`) |
| When `kConfig`/`localImCode` is empty/null | (resolution chain falls through to the same English QWERTY) | `localImCode = "lime"` → English QWERTY |
| When traitCollection / config changes mid-session and reload fails | Existing `currentLayout` is preserved (the `if let` guard simply doesn't run) — feels like "last used" but only because nothing replaces it | (n/a; Android rebuilds from scratch) |

So both platforms ultimately fall back to English QWERTY, **not** to limenum or last-used. Behavior is already aligned at this layer — the divergence is in *whether the per-IM resolution succeeds at all*.

---

## 4. Proposed cross-platform fix

Goal: keep behavior **identical on both platforms**, with the cloud DB being the single source of truth for the IM↔keyboard mapping.

### 4.1 iOS — port the Android `im`-table merge
Modify [`importFromAttachedDB`](../LimeIME-iOS/Shared/Database/LimeDB.swift#L2262) so that, in addition to copying the data table, it also reads the cloud DB's `im` table key-value rows and merges them into `lime.db`'s `im` table.

Sketch:
```swift
// After copying the data table (existing code), also merge im kv rows.
let imRows = try srcQueue.read { db in
    try Row.fetchAll(db, sql: """
        SELECT code, title, desc, keyboard, disable, selkey, endkey, spacestyle
        FROM im
        WHERE code = ?
    """, arguments: [tableName])
}
try dbQueue.write { db in
    try db.execute(sql: "DELETE FROM im WHERE code = ?", arguments: [tableName])
    for row in imRows {
        try db.execute(sql: """
            INSERT INTO im (code, title, desc, keyboard, disable, selkey, endkey, spacestyle)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, arguments: [
            row["code"] as String?, row["title"] as String?,
            row["desc"]  as String?, row["keyboard"] as String?,
            row["disable"] as Int? ?? 0,
            row["selkey"]  as String? ?? "",
            row["endkey"]  as String? ?? "",
            row["spacestyle"] as String? ?? "",
        ])
    }
}
```
Notes:
- Enumerate columns explicitly; do **not** copy `_id` (matches the Android fix described in §4.2).
- DELETE-then-INSERT inside a single transaction matches Android's `importDb` behaviour.
- After this lands, [`registerIM`](../LimeIME-iOS/Shared/Database/LimeDB.swift#L2464) becomes a fallback for IMs that don't ship `im` kv rows in their cloud DB (today: array10 from `.limedb`); it should only insert if no `im` rows exist for `tableName` after the merge.

Result: iOS Pinyin install → `lime.db.im` contains the cloud `keyboard=limenum` row → `getAllImConfigs()` reads `keyboardId = "limenum"` → `resolvedLayoutId` looks up `getKeyboardConfig("limenum")?.imkb = "lime_number"` → loads `lime_number.json`. ✅

### 4.2 Android — fix `INSERT INTO im SELECT *` defensively
Even though the live emulator pull showed the import working, the current statement at [`LimeDB.java` L3236](../LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java#L3236):
```java
db.execSQL("insert into " + LIME.DB_TABLE_IM + " select * from sourceDB." + LIME.DB_TABLE_IM);
```
copies the source `_id` values, which **could** collide on a heavily-used `lime.db` and silently fail (caught at L3246). Make it explicit and forward-safe:
```java
db.execSQL("insert into " + LIME.DB_TABLE_IM
    + " (code, title, desc, keyboard, disable, selkey, endkey, spacestyle) "
    + "select code, title, desc, keyboard, disable, selkey, endkey, spacestyle "
    + "from sourceDB." + LIME.DB_TABLE_IM);
```
This matches the column list iOS will use after §4.1, so both platforms produce identical post-import `im` table state.

### 4.3 Android — fix Settings *current keyboard* button text
[`ManageImFragment.java` L240–249](../LimeStudio/app/src/main/java/org/limeime/ui/view/ManageImFragment.java#L240): replace the IM-full-name lookup with a real keyboard lookup.

```java
// Look up the current keyboard for this IM via the existing controller chain.
String kbCode = searchServer.getImConfig(table, LIME.DB_KEYBOARD);   // reads im row (title="keyboard").keyboard
Keyboard k    = (kbCode != null && !kbCode.isEmpty())
              ? searchServer.getKeyboardConfig(kbCode)
              : null;
btnManageImKeyboard.setText(k != null ? k.getDesc()
                                       : getString(R.string.manage_im_keyboard_unset));
```
(Use existing helper if one already exists; otherwise this is a one-liner addition to `ManageImController` to mirror `setIMKeyboard`'s reverse direction.)

### 4.4 Android — keyboard-selection dialog: highlight current
[`ManageImKeyboardDialog.java` L127–140](../LimeStudio/app/src/main/java/org/limeime/ui/dialog/ManageImKeyboardDialog.java#L127):

```java
ArrayAdapter<String> adapter = new ArrayAdapter<>(
    getActivity(),
    android.R.layout.simple_list_item_single_choice,   // was simple_list_item_1
    listitems);
listSelectKeyboard.setAdapter(adapter);
listSelectKeyboard.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

int currentIdx = -1;
String currentCode = searchServer.getImConfig(this.code, LIME.DB_KEYBOARD);
for (int i = 0; i < keyboardlist.size(); i++) {
    if (keyboardlist.get(i).getCode().equals(currentCode)) { currentIdx = i; break; }
}
if (currentIdx >= 0) listSelectKeyboard.setItemChecked(currentIdx, true);
```

### 4.5 iOS Settings — mirror the Android *Manage IM* "current keyboard" surface
If the iOS Settings has an equivalent surface (TBD — likely [IMDetailView.swift](../LimeIME-iOS/LimeSettings/Views/IMDetailView.swift) or [KeyboardPickerView.swift](../LimeIME-iOS/LimeSettings/Views/KeyboardPickerView.swift)), audit it for the same "shows current keyboard description, with current selection indicated" UX after §4.1 lands. Without §4.1 the iOS Settings has no real keyboard-code to display either.

---

## 5. Steps & dependencies

1. **iOS §4.1** — port the `im`-table merge into `importFromAttachedDB`; gate `registerIM` so it only inserts when the merge produced no rows. *(unblocks iOS Pinyin)*
2. **Android §4.2** — explicit-column `INSERT INTO im`, omit `_id`. *(defensive; no current bug, prevents future collision)*
3. **Android §4.3** — fix `ManageImFragment` button initial text. *(general bug, all IMs)*
4. **Android §4.4** — fix `ManageImKeyboardDialog` choice mode. *(general UX)*
5. **iOS §4.5** — audit Settings for the equivalent surface. *(after 1)*

§§ 1, 2, 3, 4 are independent and can land in parallel. §5 depends on §1.

---

## 6. Verification

1. **Fresh install of Pinyin (and Pinyingb):**
   - iOS: switching to the IM renders `lime_number` (`LIME+數字列鍵盤`); candidates work.
   - Android: keyboard renders identically; Settings → Manage IM button shows `LIME+數字列鍵盤`; opening the keyboard-picker dialog highlights `LIME+數字列鍵盤`.
2. **Regression sweep on both platforms** — phonetic, cj/cj5/scj/ecj, dayi/dayiuni/dayiunip, array, array10, wb, hs, ez:
   - Keyboard renders the same layout it did pre-fix.
   - Settings shows the correct keyboard description (Android — was previously wrong for *all* IMs).
3. **DB integrity** —
   - iOS: `lime.db.im` after Pinyin install contains the 6 cloud kv rows (`source`, `name`, `original`, `amount`, `import`, `keyboard`); `keyboard=limenum` on the `keyboard` kv row.
   - Android: same 6 rows; `_id` values may differ from the cloud DB's (since `_id` is now omitted on insert).
4. **Seed `.limedb` SHA unchanged** — `shasum Database/array.limedb Database/array10.limedb` matches main.

---

## 7. Out of scope

- Modifying the bundled `.limedb` seed files.
- Adding a dedicated `lime_pinyin.json` / `R.xml.lime_pinyin` layout — Pinyin uses `lime_number` (LIME + number row) per the cloud DB's authoritative mapping.
- Refactoring the unused `DATABASE_CLOUD_IM_*_KEYBOARD` constants in [LIME.java](../LimeStudio/app/src/main/java/org/limeime/global/LIME.java#L82) to be the single source of truth — orthogonal cleanup; defer.
- Changing the fallback policy (currently English QWERTY on both platforms when resolution fails) — already aligned across platforms.

---

## 8. Test gap analysis & new tests

### 8.1 Why the existing suite missed all four bugs

| Bug | Why no test caught it |
|---|---|
| iOS `importFromAttachedDB` drops cloud `im` rows | No end-to-end test installs a real cloud zip and then asserts the resolved `keyboardId`. `LimeDBTest` covers data-table CRUD only. |
| iOS `getAllImConfigs` picks the first non-kv title as `label` | No fixture exercises the cloud-zip schema (where the first non-kv title is `source`/`amount`); seeded `.limedb` IMs happen to have a friendly label as the first row. |
| Android `ManageImFragment` button shows IM full name instead of keyboard desc | UI initial-state never asserted; existing `ManageImKeyboardDialogTest` only reflectively checks method existence. |
| Android `ManageImController.getCurrentKeyboard` (NEW) was reading the wrong column | Without a test pinning down the column semantics of `SearchServer.getImConfig(code, "keyboard")` (returns `desc`, not `keyboard` column), the reverse-lookup wrapper silently picked the wrong field. |

Common thread: the *post-import → UI lookup* round-trip is not covered on either platform.

### 8.2 New tests

#### 8.2.1 Android — `IntegrationTestSearchServerDBServer`
Add three integration tests exercising the cloud-install path against a real `lime.db`.

1. **`test_5_X_ImportDbMergesImKeyboardRow`**
   - Resolve cloud `pinyin.zip` (already cached in `.claude/txt/pinyin.zip`; the test should fall back to downloading from the canonical URL when the cache is absent so CI works).
   - Call the same code path the Settings *IM Store* uses (`importDb` / `importZippedDb`) into a temp `lime.db`.
   - Assert: `dbServer.getImConfigList("pinyin", "keyboard")` returns exactly one row whose `getKeyboard()` equals `"limenum"`.
2. **`test_5_X_GetCurrentKeyboardAfterCloudInstall`**
   - After the same install, call `manageController.getCurrentKeyboard("pinyin")`.
   - Assert: non-null; `.getCode().equals("limenum")`; `.getDesc()` is non-empty.
   - This is the regression guard for the wrong-column bug just fixed.
3. **`test_5_X_GetImConfigKeyboardColumnSemantics`**
   - After install, call `searchServer.getImConfig("pinyin", "keyboard")`.
   - Assert: returns the `desc` value (`"LIME+數字列鍵盤"`), **not** the keyboard code. Pin the documented behaviour so future refactors don't silently flip it; if the contract is intentionally changed later, this test forces the change to be visible.

#### 8.2.2 iOS — `LimeDBTest`
Add two tests using the cached `pinyin.zip` (or fall back to bundled fixture).

1. **`testImportFromZipMergesImTable`**
   - Call `db.importFromZip(at: pinyinZipURL, tableName: "pinyin")`.
   - Assert: `db.getImConfigList("pinyin", nil)` contains a row with `title == "keyboard"` and `keyboard == "limenum"`.
   - Assert: `try db.getAllImConfigs().first { $0.tableNick == "pinyin" }?.keyboardId == "limenum"`.
2. **`testGetAllImConfigsLabelPrefersNameRow`**
   - Same setup; assert `getAllImConfigs().first { $0.tableNick == "pinyin" }?.label == "拼音輸入法"`. Regression guard for the cloud-zip "amount" label bug just fixed.

#### 8.2.3 Test fixture handling

- Add `LimeStudio/app/src/androidTest/assets/pinyin.zip` and `LimeIME-iOS/LimeTests/Fixtures/pinyin.zip` (~520 KB each; checked-in fixture beats network flakiness in CI). Both reference the exact same upstream blob so byte-identical SHA can be asserted in a smoke test if desired.
- Tests skip (with `XCTSkip` / `assumeTrue`) when the fixture is unavailable, so they don't break unrelated CI lanes.

### 8.3 Steps

1. **Android tests** — add three tests + fixture, wire into existing `IntegrationTestSearchServerDBServer` infrastructure (temp DB, controller setup already present).
2. **iOS tests** — add two tests + fixture to `LimeDBTest.swift`; reuse the existing `LimeDB` test instance pattern.
3. **Run both suites** — `./gradlew :app:connectedDebugAndroidTest --tests 'IntegrationTestSearchServerDBServer.test_5_*'` and `xcodebuild test -scheme LimeIME -only-testing:LimeTests/LimeDBTest`. Both must pass.

