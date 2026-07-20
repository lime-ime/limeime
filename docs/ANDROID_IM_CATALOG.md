# Android IM Catalog — iOS Gap Analysis & TODO Plan

Reference screenshot: iOS "下載 / 匯入輸入法" page (2026-05-12).

---

## §1  Current Android State

`ImInstallFragment` (Step 2 of the backport) implemented the core structure:

| Feature | Android current |
|---|---|
| Expandable family cards | ✅ 14 families + CUSTOM + RELATED |
| Cloud download buttons | ✅ `borderlessButtonStyle` button per variant, label = `"name (count字)"` |
| Restore-learning switch | ✅ persisted per-table in SharedPreferences |
| Import .limedb | ✅ file picker → `SetupImController.importZippedDb()` |
| Import .cin/.lime | ✅ hidden for RELATED, visible for all others |
| Import default related | ✅ RELATED only, calls `importDbDefaultRelated()` |
| Progress feedback | ✅ delegated to activity overlay (progress bar + text) |
| Chevron expand/collapse | ✅ 200 ms rotation animation |

---

## §2  iOS Reference — Key UI Elements

```
┌─────────────────────────────────────────┐
│  ← 下載 / 匯入輸入法              ↻    │  ← back + refresh buttons
│    下載 / 匯入輸入法 (subtitle)        │
├─────────────────────────────────────────┤
│ [æ]  注音                 已安裝  >   │  ← icon | title | badge | collapsed chevron
├─────────────────────────────────────────┤
│ [⊞]  倉頡                          ∨  │  ← expanded chevron
│   ┌─ 還原已學習記錄  ────────── [●] ─┐ │  ← restore switch (green)
│   │                                   │ │
│   │  倉頡字根               [安裝]    │ │  ← variant row: name line
│   │  28,596 字 · 830 KB               │ │    metadata line (count + size)
│   │                                   │ │
│   │  倉頡字根 (BIG5字集)    [安裝]    │ │
│   │  13,859 字 · 506 KB               │ │
│   │                                   │ │
│   │  倉頡香港字字根         [安裝]    │ │
│   │  30,278 字 · 884 KB               │ │
│   │                                   │ │
│   │ [🗄] 匯入 .limedb                 │ │  ← import button with icon
│   │ [📄] 匯入 .cin / .lime            │ │
│   └───────────────────────────────────┘ │
├─────────────────────────────────────────┤
│ [⊞]  倉頡五代                      ∨  │
│   │  倉頡五代字根           [安裝]    │
│   │  24,004 字 · 491 KB               │
│   │ [🗄] 匯入 .limedb                 │
└─────────────────────────────────────────┘
```

---

## §3  Gap Analysis

### G1 — Per-family icon  *(visual)*
- **iOS**: distinctive icon per family (æ for 注音, ⊞ for 倉頡, etc.)
- **Android**: no icon — header is title text + chevron only
- **Impact**: low utility, medium polish

### G2 — Installed-state badge on header  *(functional)*
- **iOS**: installed families show `"已安裝 >"` badge in the header row; card stays collapsed
- **Android**: no installed state; all cards expand/collapse identically
- **Requires**: query the DB at load time to know which tables exist/have data

### G3 — Cloud variant as rich row, not plain button  *(visual + data)*
- **iOS**: each variant is a two-line row — `name` on line 1, `Xcount字 · Y KB` on line 2, `安裝` button on the right
- **Android**: one `borderlessButtonStyle` button per variant; label = `"name (count字)"`; no file size; no right-aligned install button
- **CloudVariant model gap**: `fileSize` field is missing (iOS shows e.g. "830 KB")

### G4 — File size data missing  *(data)*
- `CloudVariant` has `count` (word count string) but **no `fileSize` field**
- All 28 cloud variants need file sizes added (measure actual `.zip`/`.limedb` sizes from the GitHub raw URLs)

### G5 — Import buttons have no icons  *(visual)*
- **iOS**: `🗄 匯入 .limedb` and `📄 匯入 .cin / .lime` have file/archive icons on the left
- **Android**: text-only `MaterialButton` for both

### G6 — No refresh button  *(functional)*
- **iOS**: ↻ button in the top-right corner (likely re-queries install status)
- **Android**: no refresh action

### G7 — No installed badge per variant  *(functional, lower priority)*
- **iOS**: may show "已安裝" on a variant row once that specific variant has been installed
- **Android**: no per-variant state tracking

---

## §4  File Size Reference

Measured from GitHub raw asset sizes (to be added to `CloudVariant`):

| Family | Variant label key | Count | Est. file size |
|---|---|---|---|
| 注音 | phonetic_big5 | 15,945 | ~370 KB |
| 注音 | phonetic | 34,838 | ~755 KB |
| 注音 | phonetic_adv_big5 | 76,122 | ~1.3 MB |
| 注音 | phonetic_adv | 95,029 | ~1.6 MB |
| 倉頡 | cj_big5 | 13,859 | ~506 KB |
| 倉頡 | cj | 28,596 | ~830 KB |
| 倉頡 | cjhk | 30,278 | ~884 KB |
| 倉頡五代 | cj5 | 24,004 | ~491 KB |
| 速倉 | scj | 74,250 | ~1.2 MB |
| 英文倉頡 | ecj | 13,119 | ~390 KB |
| 英文倉頡 | ecjhk | 27,853 | ~625 KB |
| 大易 | dayiuni | 27,198 | ~630 KB |
| 大易 | dayiunip | 117,766 | ~2.1 MB |
| 大易 | dayi | 18,638 | ~465 KB |
| 輕鬆 | ez | 14,422 | ~340 KB |
| 行列 | array | 32,386 | ~680 KB |
| 行列十 | array10 | 32,120 | ~670 KB |
| 華象 | hs | 183,659 | ~3.2 MB |
| 華象 v1 | hs_v1 | 50,845 | ~1.1 MB |
| 華象 v2 | hs_v2 | 50,838 | ~1.0 MB |
| 華象 v3 | hs_v3 | 64,324 | ~1.2 MB |
| 五筆 | wb | 26,378 | ~590 KB |
| 拼音 | pinyin | 34,753 | ~730 KB |
| 三碼 | tricode | 14,489 | 234 KB |

> **Note**: Sizes marked "Est." — verify by fetching `Content-Length` headers from the GitHub raw URLs before shipping.

---

## §5  Implementation Plan

### Task 1 — Add `fileSize` to `CloudVariant` model + populate all entries

**Files**: `ImInstallFragment.java`

Steps:
1. Add `final String fileSize` field to `CloudVariant` (e.g. `"830 KB"`)
2. Add constructor parameter
3. Update all 23 `new CloudVariant(...)` calls in `buildFamilyList()` with measured sizes
4. The `fileSize` field is display-only — no logic change needed

**Acceptance**: `CloudVariant` instances carry file size strings; build passes.

---

### Task 2 — Replace plain cloud buttons with iOS-style rich variant rows

**Files**: `ImInstallFragment.java`, new layout `item_cloud_variant.xml`

The iOS design for each cloud variant:
```
┌─────────────────────────────────────┐
│  倉頡字根                  [安裝]   │  ← name (Body1) + outlined button
│  28,596 字 · 830 KB                 │  ← caption, gray
└─────────────────────────────────────┘
```

Steps:
1. Create `res/layout/item_cloud_variant.xml`:
   - `ConstraintLayout` root
   - `TextView id/tv_variant_name` — `textAppearanceBody1`, `layout_constraintStart`
   - `TextView id/tv_variant_meta` — `textAppearanceCaption`, color `?attr/colorControlNormal`, below name
   - `MaterialButton id/btn_install` — `OutlinedButton` style, text `@string/install`, `layout_constraintEnd`
2. In `ImFamilyViewHolder.bind()`, replace the dynamic `MaterialButton` creation with `LayoutInflater.inflate(R.layout.item_cloud_variant, ...)` per variant
3. Populate `tv_variant_name` with `getString(variant.labelResId)`, `tv_variant_meta` with `"${variant.count}字 · ${variant.fileSize}"`, `btn_install` click triggers download
4. Add string resource: `<string name="install">安裝</string>`

**Acceptance**: each cloud variant shows name, count+size metadata, and 安裝 button matching iOS layout.

---

### Task 3 — Add per-family icon to card header

**Files**: `item_im_family_card.xml`, `ImInstallFragment.java` (ImFamily model), icon drawables

Steps:
1. Add `ImageView id/iv_family_icon` to `item_im_family_card.xml` before `tv_im_title` (24 dp × 24 dp)
2. Add `final int iconResId` field to `ImFamily` model
3. Map each family to an appropriate existing drawable (or add new ones):
   - 注音/拼音: `@drawable/ic_phonetic` (or repurpose `ic_keyboard_outline`)
   - 倉頡/速倉/英文倉頡: a cangjie radical icon
   - 大易/輕鬆/行列/行列十/五筆/華象: generic `ic_grid_view`
   - 自建 (CUSTOM): `ic_edit`
   - 關聯字庫 (RELATED): `ic_list`
4. In `bind()`, set `iv_family_icon.setImageResource(family.iconResId)`

**Acceptance**: each card header shows a distinct icon to the left of the title.

---

### Task 4 — Installed-state detection + "已安裝" badge + expand-lock behavior

**Files**: `ImInstallFragment.java`, `ManageImController.java` (or `SearchServer`), `item_im_family_card.xml`

**Behavior rules (confirmed):**
- **Installed IM** → card is collapsed by default, header tap does nothing (no expand), chevron hidden or replaced by badge
- **Not-installed IM** → card is expanded by default, user can collapse/re-expand freely

Steps:
1. Add method `isTableInstalled(String tableName)` to `ManageImController` — returns `true` if `countRecords(tableName) > 0`
2. Add `TextView id/tv_installed_badge` to `item_im_family_card.xml` header (text `"已安裝"`, `textAppearanceCaption`, tint `?attr/colorPrimary`, visibility `gone`); hide `iv_chevron` when installed
3. In `loadFamilyList()` (new async loader, replacing inline construction), for each family call `isTableInstalled()` off the main thread; store result in `ImFamily.isInstalled`
4. Initialize `expanded[]` array based on installed state: `expanded[i] = !family.isInstalled` (not-installed → open, installed → closed)
5. In `bind()`:
   - If `family.isInstalled`:
     - Show `tv_installed_badge`, hide `iv_chevron`
     - Force `bodyContainer.setVisibility(View.GONE)`
     - Set `cardHeader.setOnClickListener(null)` — no expand allowed
   - If `!family.isInstalled`:
     - Hide `tv_installed_badge`, show `iv_chevron`
     - Expand/collapse normally with rotation animation
     - `cardHeader.setOnClickListener(v -> toggleExpand.run())`

**Acceptance**:
- Installed families: collapsed, locked, show "已安裝", no chevron
- Not-installed families: expanded on load, can be toggled by user

---

### Task 5 — Add icons to import buttons

**Files**: `item_im_family_card.xml`

Steps:
1. Add `app:icon` to `btn_import_limedb`: use `@drawable/ic_database` or similar archive icon
2. Add `app:icon` to `btn_import_txt`: use `@drawable/ic_file_outline` or document icon
3. Add `app:icon` to `btn_import_default_related`: use `@drawable/ic_download` or cloud icon
4. Use `app:iconGravity="start"` and `app:iconPadding="8dp"` for consistent layout

**Acceptance**: import buttons show icons matching iOS design.

---

### Task 6 — Add refresh action to toolbar

**Files**: `fragment_im_install.xml` or `ImInstallFragment.java`, menu resource

Steps:
1. Add `MaterialToolbar id/im_install_toolbar` to `fragment_im_install.xml` (above RecyclerView), title = `"下載 / 匯入輸入法"`
2. Add menu item `action_refresh` with `@drawable/ic_refresh` icon
3. In `ImInstallFragment.onCreateView()`, inflate menu, handle `action_refresh` → re-run `loadFamilyList()` (re-queries installed state + rebuilds family list)

**Acceptance**: ↻ button in toolbar re-queries installed state and refreshes card badges.

---

## §6  Priority & Sequencing

| # | Task | Priority | Effort | Dependency |
|---|---|---|---|---|
| 1 | Add `fileSize` to CloudVariant | High | S | — |
| 2 | Rich variant rows (name + meta + 安裝 button) | High | M | Task 1 |
| 3 | Per-family icons | Medium | S | — |
| 4 | Installed-state badge | Medium | M | — |
| 5 | Import button icons | Low | S | — |
| 6 | Refresh button | Low | S | Task 4 |

**Recommended order**: 1 → 2 → 4 → 3 → 5 → 6

Tasks 1 + 2 close the most visible iOS gap (the metadata row).
Task 4 adds functional parity (installed detection).
Tasks 3, 5, 6 are polish.
