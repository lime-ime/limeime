# LIME Settings — iOS → Android UI Backport Plan

Status: APPROVED FOR IMPLEMENTATION. Decisions locked (see §A).

## A. Locked Implementation Decisions

| Decision | Choice | Rationale |
|---|---|---|
| **Rollout strategy** | Single cutover — no feature flag | The git worktree already provides isolation; a flag adds dead code that must be cleaned up later |
| **Language for new files** | Java | All 66 existing source files are `.java`; mixing languages adds build config complexity for no gain |
| **Activity rename** | `MainActivity` → `LIMESettings` | Rename the activity class and all references; update `AndroidManifest.xml` accordingly |
| **Missing deps** | Add Material 3, SlidingPaneLayout, Lifecycle ViewModel | None of these are currently in `app/build.gradle` |

## 0. Scope and Hard Constraints

This is a **view-layer-only** backport of the iOS LimeSettings UI ([LIME_SETTINGS.md](LIME_SETTINGS.md)) to the Android container app. The architectural rules in [UI_ARCHITECTURE.md](UI_ARCHITECTURE.md) (MVC layering) MUST be preserved.

| Constraint | Rule |
|---|---|
| **Layer scope** | Only the View layer (Fragments / layouts / dialogs / `LIMEPreference` activity) is rewritten. `controller/`, `ShareManager`, `NavigationManager`, `ProgressManager`, `IntentHandler`, `SetupImController`, `ManageImController`, `SearchServer`, `DBServer`, `LimeDB`, `LIMEPreferenceManager` are **NOT** touched (except as noted in §10 for additive view-callback hooks if absolutely necessary). |
| **Sharing / import-from-other-app** | The existing `IntentHandler` + `ImportDialog` + `setupImController.import*` flows that receive `.lime` / `.cin` / `.limedb` from other apps via `Intent.ACTION_VIEW` / `ACTION_SEND` MUST continue to work unchanged. The new IM Install screen (§4) routes through the same controller methods. |
| **Setup tab activation logic** | Keep the **current Android detection** path (`LIMEUtilities.showInputMethodPicker` + `Settings.ACTION_INPUT_METHOD_SETTINGS` + `Settings.Secure.DEFAULT_INPUT_METHOD` polling) for "LIME keyboard not enabled / not currently selected". Do **NOT** port the iOS `openLimeKeyboardSettings()` deep-link or the iOS Full-Access banner — those are iOS-specific. |
| **Wide-screen layout** | Preserve the existing tablet "sidebar + detail" presentation (`values-large/-xlarge` already widens `navigation_drawer_width`) using a modern Android two-pane primitive — see §3. |
| **Record / Related editor visual style** | New iOS-like sheet/form aesthetic (Material 3 `ModalBottomSheet` or full-screen `Dialog` with grouped form rows), but bound to the **same** `ManageImController` / `ManageRelatedController` callbacks. |
| **Encoding** | Every Java/Kotlin source file written or modified by the implementation MUST be saved as **UTF-8 with BOM**. Layout XML and `strings.xml` follow the same rule. |

---

## 1. Mapping iOS → Android View Components

| iOS spec section | iOS view | Android target | New / Refactor |
|---|---|---|---|
| §2 TabView (4 tabs) | `TabView` | `BottomNavigationView` (phone, `sw<600dp`) **xor** `NavigationRail` (tablet, `sw≥600dp`) — never both visible at once — over a single `FragmentContainerView` | **Rename + refactor** `MainActivity` → `LIMESettings` + new `activity_main.xml` |
| §4 設定 tab | `SetupTabView` (iOS-only banner + step list) | Refactored `SetupImFragment` — keeps Android activation buttons + adds About card | **Refactor** existing fragment |
| §5.1 IM List | `IMListView` | New `ImListFragment` (RecyclerView with enable/disable toggle) | **New** fragment, replaces the IM-grid portion of `SetupImFragment` |
| §5.2 IM Detail | `IMDetailView` | New `ImDetailFragment` (per-IM info + keyboard picker entry + table editor entry + remove) | **New** |
| §5.2.1 KeyboardPicker | `KeyboardPickerView` | Reuse `ManageImKeyboardDialog`, restyled as a full-width list dialog | **Refactor** |
| §5.3 IM Install | `IMInstallView` (DisclosureGroups) | New `ImInstallFragment` with `RecyclerView` of expandable `ImFamilyCard` | **New** — internally calls existing `setupImController.import*` / cloud download methods |
| §6.1 RecordList + Add/Edit | `RecordListView` + sheets | Refactored `ManageImFragment` + new `ManageImAddSheet` / `ManageImEditSheet` (`BottomSheetDialogFragment`) | **Refactor list, replace dialogs** |
| §6.2 RelatedList + Add/Edit | `RelatedListView` + sheets | Refactored `ManageRelatedFragment` + new `ManageRelatedAddSheet` / `ManageRelatedEditSheet` | **Refactor list, replace dialogs** |
| §7 DB Manager | `DBManagerTabView` | New `DbManagerFragment` (extracts backup/restore from `SetupImFragment`) | **New** |
| §8 Preferences | `PreferencesTabView` (Form sections) | Refactored `LIMEPreference` activity hosting a `PreferenceFragmentCompat` reskinned with Material 3 grouped sections (or a Compose-equivalent `Form` page) | **Refactor** |
| §8.4.1 Reverse Lookup | Sub-screen | Add an `androidx.preference.PreferenceScreen` nested inside `pref_section_im_behaviour` as the last §8.4 item | **Refactor** |

> Android already has the entire 外接鍵盤 (External Keyboard) preferences group; the iOS spec drops it. Android **keeps** that group.

---

## 2. Tab Structure (Android)

Android replaces the existing `DrawerLayout` + `NavigationDrawerFragment` with a tab-bar coordinator that mirrors the iOS `TabView` (§2 of the iOS spec) but uses native Android primitives. Per Material 3 guidance, **only one** top-level nav control is shown per form factor — phones get a horizontal bottom bar, tablets get a vertical left rail.

```
LIMESettings (renamed from MainActivity)
├── activity_main.xml      (phone variant — res/layout/activity_main.xml)
│   └── CoordinatorLayout
│       ├── FragmentContainerView          @+id/main_fragment_container
│       └── BottomNavigationView           @+id/main_bottom_nav   (bottom)
│           menu = res/menu/main_nav.xml
│           ├── 設定       icon: ic_gearshape
│           ├── 輸入法      icon: ic_list_bullet
│           ├── 喜好設定    icon: ic_sliders
│           └── 資料庫      icon: ic_archivebox
│
└── activity_main.xml      (tablet variant — res/layout-sw600dp/activity_main.xml)
    └── ConstraintLayout (horizontal)
        ├── NavigationRail                 @+id/main_nav_rail     (start, vertical, ~80dp)
        │   same menu = res/menu/main_nav.xml (shared)
        └── FragmentContainerView          @+id/main_fragment_container  (fills rest)
```

The two `activity_main.xml` variants are resolved by Android's resource qualifier system; `LIMESettings` looks up either `R.id.main_bottom_nav` or `R.id.main_nav_rail` (whichever exists at runtime) and binds the same `OnItemSelectedListener`. The menu XML is shared between both controls.

Tab-to-iOS mapping mirrors LIME_SETTINGS.md §2:

| Index | Tab title | Hosts |
|---|---|---|
| 0 | 設定 | `SetupFragment` (Android-flavored §4 below) |
| 1 | 輸入法 | `ImListFragment` ⇄ `ImDetailFragment` ⇄ `ImInstallFragment` ⇄ `ManageImFragment` ⇄ `ManageRelatedFragment` |
| 2 | 喜好設定 | `LimePreferenceFragment` (refactored existing `PrefsFragment`) |
| 3 | 資料庫 | `DbManagerFragment` |

The 關聯字庫 entry collapses into the 輸入法 tab (synthetic IM row, same as iOS §5.1 last section).

---

## 3. Wide-Screen "Sidebar + Detail" (Tablet Layout)

The iOS spec uses `NavigationStack` with implicit split-view on iPad. The Android equivalent is **`androidx.slidingpanelayout.widget.SlidingPaneLayout`** (recommended in current AndroidX guidance) or — if the team prefers Compose Material 3 — **`androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold`**. Both auto-switch between single-pane (phone) and two-pane (sw600dp+).

This plan adopts **`SlidingPaneLayout`** for parity with the existing all-Views/Fragments codebase (no new Compose dependency surface required for the View layer):

```
Tab "輸入法" host fragment (TwoPaneHostFragment)
└── SlidingPaneLayout
    ├── (start, fixed 320dp on tablet, full-width on phone)
    │   FragmentContainerView  @+id/im_list_pane
    │   → ImListFragment   (master list of IMs + 關聯字庫 row)
    └── (end, fills remaining width on tablet, slides over on phone)
        FragmentContainerView  @+id/im_detail_pane
        → ImDetailFragment / ImInstallFragment / ManageImFragment / ManageRelatedFragment
```

Behaviour:

- Phone (`sw < 600dp`): tap row in `ImListFragment` → detail pane slides over, back gesture pops it.
- Tablet (`sw ≥ 600dp`): both panes visible side-by-side. Selecting a row swaps the detail pane fragment via a `FragmentTransaction.replace(R.id.im_detail_pane, …)`.
- Selection state hoisted to a shared `ViewModel` (`ImNavigationViewModel`) — **view-layer only**, no controller change.
- Existing `NavigationDrawerFragment` is **removed**; `NavigationManager` is kept (still used by IntentHandler) but rebound to drive the tab + sliding-pane selection instead of the drawer. NavigationManager's public API is preserved.

The same `SlidingPaneLayout` pattern is **not** required for the other three tabs; they are single-pane on all sizes.

---

## 4. Tab 0 — 設定 (Setup) — Android-Specific Activation Path

This is the key deviation from the iOS spec. The iOS §4 banner / single-CTA design is **NOT** ported. Instead the existing Android activation flow is preserved and visually restyled.

### 4.1 Layout (`fragment_setup.xml`, replaces `fragment_setup_im.xml` skeleton)

```
NestedScrollView
└── LinearLayout(orientation=vertical, max_width=560dp on tablet, centered)
    ├── BrandBlock
    │   ├── ImageView app icon (80dp, rounded 18dp)
    │   └── TextView "萊姆輸入法" (28sp, semibold)
    │
    ├── StatusCard  (Material 3 elevated card; colour reflects state — see §4.2)
    │   └── TextView statusText
    │
    ├── TextView "設定萊姆輸入法"   (titleLarge)
    │
    ├── SetupStepList   (2 steps — matches the two CTA buttons below; Android has no Full-Access equivalent)
    │   ├── SetupStepRow icon=ic_keyboard       text="於系統設定中啟用萊姆輸入法"
    │   └── SetupStepRow icon=ic_toggle_on      text="將輸入法切換為萊姆輸入法"
    │
    ├── TextView footnote
    │   text=@string/setup_im_privacy_note  (existing string — 萊姆輸入法不會收集任何輸入內容…)
    │
    ├── // Two action buttons — kept from current Android, restyled as Material 3 filled buttons
    │   Button @+id/btnSetupImSystemSetting     text=@string/setup_im_wizard_nextstep
    │       → opens Settings.ACTION_INPUT_METHOD_SETTINGS (existing handler, unchanged)
    │   Button @+id/btnSetupImSystemIMPicker    text=@string/setup_im_system_selectLIME
    │       → calls LIMEUtilities.showInputMethodPicker() (existing handler, unchanged)
    │
    └── AboutCard (Material 3 outlined card; mirrors iOS §4.1 GroupBox visually)
        ├── LabeledRow "版本"  value=BuildConfig.VERSION_NAME (build)
        ├── Divider
        ├── LabeledRow "授權"  value="GPL-3.0"
        ├── Divider
        └── LabeledRow "原始碼 (GitHub)"  link=@string/url_github_limeime
```

### 4.2 Status Detection (KEEP Android logic; do NOT use iOS App Group flags)

Re-evaluate on `onResume()` and on `Lifecycle.Event.ON_START`:

| Condition | Source | Card colour | Message |
|---|---|---|---|
| LimeIME service is in `getEnabledInputMethodList()` AND `Settings.Secure.DEFAULT_INPUT_METHOD == LIMEService component` | `InputMethodManager` | Green (`?attr/colorPrimaryContainer` w/ green tint) | "萊姆輸入法已啟用且為目前輸入法 ✓" |
| Enabled but not current | same | Orange | "已啟用，但尚未切換萊姆輸入法 — 點按下方按鈕切換" |
| Not in enabled list | same | Red | "尚未在系統中啟用萊姆輸入法 — 點按下方按鈕前往系統設定" |

Implementation note: a small `KeyboardActivationProbe` helper (View-layer, in the new fragment file) wraps these calls. It does **not** replace any controller — it queries `InputMethodManager` directly, exactly as `SetupImFragment` does today via `LIMEUtilities`.

### 4.3 What is NOT in this tab (moved elsewhere — preserves all functionality)

| Removed from old `SetupImFragment` | Moved to |
|---|---|
| Per-IM download buttons (注音/倉頡/快倉/…/關聯字庫) | Tab 1 → `ImInstallFragment` (§5.3 below) |
| Backup / Restore Local buttons | Tab 3 → `DbManagerFragment` (§7) |
| 自建 import button | Tab 1 → `ImInstallFragment` 自建 group |

The underlying click handlers (which call `setupImController.import*`, `downloadIM`, etc.) are **moved verbatim**, only the host fragment changes.

---

## 5. Tab 1 — 輸入法 (IM Manager)

### 5.1 ImListFragment

Flat `RecyclerView`. Sort order is fixed by `im.sortOrder` as written in the seed DB; **no user reorder UI** (Android has no equivalent need — the active IM is selected by the system input-method picker, not by ordering in this list).

```
fragment_im_list.xml
├── MaterialToolbar
│   ├── title = @string/manage_input_methods
│   └── menu = @menu/im_list_menu
│       └── action_install   icon=ic_add        title="下載/匯入"
└── RecyclerView @+id/im_list
    └── item_im_row.xml
        ├── ImageView leading icon (table-nick → drawable map; reuse existing IM icons)
        ├── TextView label (im.label)
        ├── (optional) TextView subtitle = im.fullName  (hide if empty — matches iOS)
        └── SwitchMaterial  @+id/switch_enabled
            onChange → manageImController.setImEnabled(id, enabled)   ← already exists; if not, expose via view-layer helper that delegates to LIMEPreferenceManager + DB

Section "關聯字庫" rendered as a sticky footer item with NavigationLink semantic
→ tap pushes ImDetailFragment(syntheticRelatedRow)
```

Toggling enabled:
- **No controller change.** Android already exposes `LIMEPreferenceManager` + DB write paths used by the current grid in `SetupImFragment`. The new fragment binds the toggle to the **same** existing methods.

Item appearance: enabled rows full alpha; disabled rows `alpha=0.5` and italic typeface — matches iOS half-opacity rule and the existing `HALF_ALPHA_VALUE` constant.

### 5.2 ImDetailFragment

`NestedScrollView` of Material 3 `MaterialCardView` "sections" matching iOS §5.2 layout exactly. Each section is a card with header text and grouped rows.

Sections (visibility identical to iOS spec):

1. **輸入法資訊** — name, version (UserDefaults), 筆數 (count via existing `searchServer.countRecords(table)`).
2. **軟鍵盤配置** — single row "鍵盤佈局: 〈current〉" → opens `ManageImKeyboardDialog` (existing) restyled.
3. **注音鍵盤類型** — *(NOT moved here; stays in Tab 2 喜好設定 per §8.)*
4. **電話鍵盤設定** — *(NOT moved here; stays in Tab 2 喜好設定 per §8.)*
5. **字根對應設定** — *(NOT moved here; stays in Tab 2 喜好設定 per §8. iOS surfaces this only when `tableNick == "custom"`; Android keeps it as a global preference.)*
6. **字根資料表** — single row → push `ManageImFragment(table=im.tableNick)` (or `ManageRelatedFragment` for the synthetic 關聯字庫 row).
7. **選項** — `SwitchMaterial` "刪除時備份已學習記錄" bound to `backup_on_delete_{tableNick}` (use `SharedPreferences` default file, mirroring iOS `UserDefaults.standard` exception — these are settings-app-only and must not pollute the keyboard's shared prefs).
8. **無 header** — `MaterialButton` "移除輸入法" with `?attr/colorError` text → `MaterialAlertDialog` confirm → existing `manageImController.clearTable(...)`.

Note: `cj4_semicolon_key` is a NEW per-IM preference surfaced in the IM detail page on BOTH platforms (iOS `IMDetailView`, Android `ImDetailFragment`). Android stores it in default `SharedPreferences` so the keyboard reads it, unlike `auto_commit` / `phonetic_keyboard_type` / `accept_*`, which Android keeps in the global `preference.xml`.

Toolbar overflow menu has a single "分享/匯出" item that opens the existing `ShareDialog` unchanged — preserves all current sharing behaviour (constraint #2).

### 5.3 ImInstallFragment

Direct visual port of iOS §5.3. Each IM family is an expandable card.

```
fragment_im_install.xml
└── RecyclerView @+id/im_install_list
    └── item_im_family_card.xml  (Material 3 ExpandableCard pattern)
        ├── header row
        │   ├── ImageView icon
        │   ├── TextView family title (注音 / 倉頡 / … / 自建 / 關聯字庫)
        │   └── ImageView chevron (rotates on expand)
        └── expandable body (LinearLayout, hidden when collapsed)
            ├── (if checkBackupTable(tableNick)) SwitchMaterial "還原已學習記錄"
            │   bound to restore_on_import_{tableNick}  (default true, default SharedPreferences)
            ├── one MaterialButton per cloud variant (icon=ic_cloud_download)
            │   onClick → setupImController.downloadIM(url, table, restoreLearning)   ← existing
            ├── MaterialButton "匯入 .limedb"
            │   onClick → file picker (ACTION_OPEN_DOCUMENT, mime "*/*")
            │   → setupImController.importFromAttachedDB(uri, table, restoreLearning)  ← existing
            └── MaterialButton "匯入 .cin / .lime"
                onClick → file picker → setupImController.importTxtTable(uri, table, restoreLearning)  ← existing

Self-build (自建) family: only the two local-import buttons; on success calls
setupImController invocation that internally hits db.seedCustomIM-equivalent path (existing).

關聯字庫 family: only "匯入 .limedb" → existing DBServer.importDbRelated path.
```

Progress overlay: reuse existing `activity_progress_overlay` in `activity_main.xml` driven by `ProgressManager`. **No change to ProgressManager.**

> External-app sharing constraint (#2): `IntentHandler` continues to receive `ACTION_VIEW`/`ACTION_SEND` and route to `setupImController.import*` exactly as today. The new `ImInstallFragment` is purely an **alternate caller** of the same controller methods — both entry points coexist.

---

## 6. Tab 1 sub-screens — Record / Related Editors (iOS-style)

### 6.1 ManageImFragment (refactor list visuals only)

Visual change only — fragment continues to use `ManageImController` and the existing `ManageImView` callbacks.

```
fragment_manage_im.xml
├── MaterialToolbar (title = im.label)
│   ├── menu action_search → expands SearchView
│   ├── (in SearchView submenu) ToggleGroup ["字根", "文字"]
│   └── menu action_add → opens ManageImAddSheet
├── RecyclerView @+id/record_list
│   └── item_record_row.xml
│       ├── TextView code  (mono, primaryText)
│       ├── TextView word  (secondaryText)
│       └── TextView score (caption, end-aligned)
│       Swipe-left → reveal "編輯" + "刪除" actions (ItemTouchHelper)
│       Long-press → context menu fallback for accessibility
└── PaginationBar (LinearLayout, bottom)
    ├── MaterialButton "‹ 上頁"
    ├── TextView "第 X 頁 / 共 Y 筆"
    └── MaterialButton "下頁 ›"
```

`LIME.IM_MANAGE_DISPLAY_AMOUNT` (100/page) is preserved.

### 6.2 ManageImAddSheet / ManageImEditSheet — replaces ManageImAddDialog / ManageImEditDialog

iOS-style grouped form rendered inside a Material 3 `BottomSheetDialogFragment` (large screen sheets / modal full-screen on tablets via `setExpanded(true)`). They replace the legacy `fragment_dialog_add.xml` / `fragment_dialog_edit.xml` layouts.

```
sheet_manage_im_add.xml / sheet_manage_im_edit.xml
└── ConstraintLayout (sheet content)
    ├── DragHandleView
    ├── TextView title
    ├── TextInputLayout / TextInputEditText "字根 (code)"
    ├── TextInputLayout / TextInputEditText "文字 (word)"
    ├── ScoreStepperRow      ([−] [text 0–9999] [+]) — custom CompoundView, no third-party dep
    ├── (edit only) MaterialButton text "刪除" with colorError
    └── MaterialButton.Filled "儲存"
        onClick → ManageImAddDialog → manageImController.addRecord (existing)
                    or  ManageImEditDialog → manageImController.updateRecord (existing)
```

Validation rules unchanged: code & word must be non-empty.

### 6.3 ManageRelatedFragment + ManageRelatedAddSheet / EditSheet

Same treatment as §6.1/§6.2. Two `TextInputLayout` fields ("詞彙", "關聯字"), pagination bar, swipe actions. Bound to existing `ManageRelatedController` methods.

### 6.4 Dialogs to retire / preserve

| Existing | Action |
|---|---|
| `ManageImAddDialog`, `ManageImEditDialog` | Replace by sheets above; delete after migration |
| `ManageRelatedAddDialog`, `ManageRelatedEditDialog` | Replace by sheets; delete |
| `ManageImKeyboardDialog` | **Keep** but restyle to Material 3 `MaterialAlertDialog` with single-choice list; same callback contract |
| `ImportDialog`, `SetupImLoadDialog`, `ShareDialog`, `HelpDialog`, `NewsDialog` | **Keep unchanged** — these are part of the external-app-sharing flow and the constraint forbids touching them |

---

## 7. Tab 3 — 資料庫 (DbManagerFragment)

New fragment that hosts the backup / restore / restore-bundled controls relocated from `SetupImFragment`. All backing methods (`setupImController.exportDb`, `restoreDb`, `restoreBundledDatabase`) are **unchanged**.

```
fragment_db_manager.xml
└── NestedScrollView
    └── LinearLayout (max_width=560dp)
        ├── SectionCard "備份"
        │   ├── MaterialButton "備份資料庫"
        │   │   → setupImController.backupLocal (existing) → ACTION_SEND share intent (system sheet)
        │   └── TextView footnote "備份包含所有字根、關聯字及喜好設定。"
        ├── SectionCard "還原"
        │   ├── MaterialButton "還原資料庫"
        │   │   → MaterialAlertDialog confirm → ACTION_OPEN_DOCUMENT → setupImController.restoreLocal (existing)
        │   └── TextView footnote
        ├── SectionCard "初始資料庫"
        │   └── MaterialButton "還原預設資料庫"
        │       → setupImController.restoreBundledDatabase (existing)
        └── SectionCard "狀態" (visible only when statusMessage non-empty)
            └── TextView statusText
```

Progress overlay reused from `activity_main.xml`.

---

## 8. Tab 2 — 喜好設定 (LimePreferenceFragment)

**Pure visual reskin only.** `LIMEPreference`, `PrefsFragment`, `preference.xml`, and every preference key bound to `LIMEPreferenceManager` are **NOT** modified. The only changes are theme / styling so the existing screen renders with Material 3 grouped-section visuals consistent with the rest of the new UI.

### 8.1 Allowed changes (visual only)

- Apply Material 3 preference theming (`Theme.Material3.PreferenceTheme` or the project's existing M3 theme overlay) so `PreferenceCategory` headers and rows match the new SectionCard look used in §5–§7.
- Host `PrefsFragment` inside the new tab container (`FragmentContainerView` under the bottom-nav / rail) instead of the legacy `LIMEPreference` activity launch path. The `PrefsFragment` class itself is unchanged; only its host changes.
- The legacy `LIMEPreference` activity entry point continues to work (still launchable for back-compat with any external Intent that targets it) — no source change there either.

### 8.2 Explicitly NOT changed

| Off-limits | Reason |
|---|---|
| `res/xml/preference.xml` | Canonical schema consumed by `LIMEPreferenceManager`; touching it risks behavioural drift |
| Category order in `preference.xml` | Out of scope — visual reskin only |
| `auto_commit`, `phonetic_keyboard_type`, `accept_number_index`, `accept_symbol_index` row locations | **Stay in `preference.xml`**. They are NOT relocated to the IM Detail page in this plan. (iOS spec moves them to detail screens; Android keeps the existing global Preferences placement.) |
| `preference.xml` nested 字根反查 sub-screen | Already complete on Android; no edits |
| 8.x 外接鍵盤 (External Keyboard) preference group | Android-only feature; kept as-is |
| `LIMEPreference.java`, `PrefsFragment` Java/Kotlin source | No code edits, only theme/style attributes |

Result: §5.2 `ImDetailFragment` does **not** include the "注音鍵盤類型", "電話鍵盤設定", or "字根對應設定" sections that the iOS spec puts there. Those settings remain reachable only from Tab 2 (喜好設定).

---

## 9. Resources Inventory

New / changed resources:

| Path | Action |
|---|---|
| `res/layout/activity_main.xml` (phone, default) | **Refactor** — `CoordinatorLayout` with `FragmentContainerView` + `BottomNavigationView` + `activity_progress_overlay` |
| `res/layout-sw600dp/activity_main.xml` (tablet) | **New** — `ConstraintLayout` with `NavigationRail` (start) + `FragmentContainerView` (fills rest) + `activity_progress_overlay`. No `BottomNavigationView`. |
| `res/layout/fragment_navigation_drawer.xml` | **Delete** after `NavigationDrawerFragment` is removed |
| `res/menu/main_nav.xml` | **New** — single menu shared by `BottomNavigationView` and `NavigationRail` |
| `res/layout/fragment_setup.xml` | **New** (replaces `fragment_setup_im.xml` for the 設定 tab; old XML deleted) |
| `res/layout/fragment_im_list.xml` + `item_im_row.xml` | **New** |
| `res/layout/fragment_im_detail.xml` + section card includes | **New** |
| `res/layout/fragment_im_install.xml` + `item_im_family_card.xml` | **New** |
| `res/layout/fragment_manage_im.xml` | **Refactor** (existing) |
| `res/layout/sheet_manage_im_add.xml` / `sheet_manage_im_edit.xml` | **New** (replace `fragment_dialog_add.xml` / `fragment_dialog_edit.xml`) |
| `res/layout/fragment_manage_related.xml` | **Refactor** |
| `res/layout/sheet_manage_related_add.xml` / `sheet_manage_related_edit.xml` | **New** (replace `fragment_dialog_related_add.xml` / `fragment_dialog_related_edit.xml`) |
| `res/layout/fragment_db_manager.xml` | **New** |
| `res/layout/fragment_two_pane_im_host.xml` | **New** — hosts `SlidingPaneLayout` for tab 1 |
| `res/values/styles.xml` (or `themes.xml`) | **Add** Material 3 component styles (SectionCard, ScoreStepper, …) |
| `res/values-sw600dp/integers.xml` | **Add** flag `is_two_pane=true` if needed for runtime branching |
| `res/values/strings.xml` | **Add** new strings only; do not retranslate existing ones |
| `res/xml/preference.xml` | **Untouched** — schema and key locations preserved exactly (§8). Only theme/style attributes applied via `?attr/preferenceTheme`. |
| `res/menu/im_list_menu.xml`, `manage_im_menu.xml`, `manage_related_menu.xml` | **New / refactor** |

Drawables (vector icons) — add small set matching iOS SF Symbols semantics:
`ic_gearshape`, `ic_list_bullet`, `ic_sliders`, `ic_archivebox`, `ic_cloud_download`, `ic_add`, `ic_chevron_right`, `ic_keyboard_outline`, `ic_toggle_on_outline` (`ic_database` previously listed for a third setup step has been dropped — Android setup is 2 steps; `ic_reorder` / `ic_drag_handle` removed since IM-list reorder is dropped).

---

## 10. Controller / Model Touchpoints (Allowed Diffs Only)

Per constraint #1, controller and model layers are **off-limits** with the following **narrow** exceptions, each strictly additive:

1. `ManageImController` — add a public pass-through `setImEnabled(int id, boolean enabled)` **only if** an equivalent method is not already callable from a fragment. It must just delegate to the existing `LIMEPreferenceManager` + `DBServer` writes used today by `SetupImFragment`'s grid logic. **Behaviour MUST be identical.**
2. `LIMESettingsView` interface (renamed from `MainActivityView`) — may gain a new callback `onTabSelected(int index)` if needed by `LIMESettings` to drive `BottomNavigationView`. Existing callbacks unchanged.

If during implementation any further controller change appears necessary, **STOP and re-plan** — do not silently expand scope.

`SetupImController`, `ShareManager`, `IntentHandler`, `ProgressManager`, `NavigationManager`, `SearchServer`, `DBServer`, `LimeDB`, `LIMEPreferenceManager`: **zero changes**.

---

## 11. Dependency / Tooling Notes

The following dependencies are **confirmed missing** from `app/build.gradle` and **must be added** in step 1 of §13:

| Dependency | Version | Why needed |
|---|---|---|
| `com.google.android.material:material` | `1.12.0` | `BottomNavigationView`, `NavigationRailView`, `BottomSheetDialogFragment`, `MaterialCardView`, `TextInputLayout`, all M3 styles |
| `androidx.slidingpanelayout:slidingpanelayout` | `1.2.0` | Two-pane host for tab 1 (§3) |
| `androidx.lifecycle:lifecycle-viewmodel` | `2.8.7` | `ImNavigationViewModel` used in §3 for selection state |

Already present (no version change needed):
- `androidx.recyclerview:recyclerview` — via `appcompat:1.7.1` transitive
- `androidx.preference:preference:1.2.1` — explicit
- **No Compose** dependencies introduced in this plan (keeps PR diff scoped).

---

## 12. Test Impact

Per constraint #1 the controller/model layer is unchanged, so existing instrumentation tests under `LimeStudio/app/src/androidTest` and unit tests under `LimeStudio/app/src/test` remain valid without modification.

New view-layer tests recommended (additive only):

| Test | Type | Asserts |
|---|---|---|
| `ImListFragmentTest` | Espresso/Robolectric | toggling switch calls expected controller method via `MockManageImController` |
| `SetupFragmentActivationProbeTest` | Robolectric | three banner states (green/orange/red) for the three `InputMethodManager` shadow configurations |
| `ManageImAddSheetTest` | Robolectric | empty-field validation; non-empty triggers controller `addRecord` |
| `TwoPaneHostFragmentTest` | Robolectric (sw600dp config) | both panes inflated on tablet config; single pane on phone config |

No existing test is rewritten or deleted.

---

## 13. Migration Order (Implementation Sequence — Single Cutover)

**Strategy:** Each step lands as a single commit and leaves the app build-green and runnable. No feature flag — the git worktree provides isolation. All new files are Java.

1. **Dependencies + rename** — add the three missing deps to `app/build.gradle` (§11); rename `MainActivity.java` → `LIMESettings.java` (and `MainActivityView` interface → `LIMESettingsView`); update `AndroidManifest.xml` `<activity android:name=".ui.LIMESettings">`. App must build green.
2. **New `activity_main.xml` + bottom-nav shell** — replace `DrawerLayout` with `CoordinatorLayout` + `BottomNavigationView` + `FragmentContainerView`; add tablet variant at `res/layout-sw600dp/activity_main.xml` with `NavigationRail`; wire 4 empty placeholder fragments in `LIMESettings`; add `res/menu/main_nav.xml`. Remove `NavigationDrawerFragment` and `fragment_navigation_drawer.xml`. App must build green and show 4 empty tabs.
3. **DbManagerFragment** — new fragment + `fragment_db_manager.xml`; move backup/restore/bundled-restore controls and their click handlers verbatim from `SetupImFragment`; host under tab 3 (資料庫).
4. **ImInstallFragment** — new fragment + `fragment_im_install.xml` + `item_im_family_card.xml`; move per-IM download/import buttons verbatim from `SetupImFragment`; host under tab 1 (輸入法) temporarily until §6 SlidingPaneLayout host is ready. Verify `IntentHandler` still routes external `.limedb`/`.lime`/`.cin` shares unchanged.
5. **SetupFragment** — refactor remaining `SetupImFragment` to match §4 layout (brand block, status card, 2 action buttons, About card); delete the now-empty per-IM and backup/restore button blocks. Rename file to `SetupFragment.java` + `fragment_setup.xml`.
6. **ImListFragment + ImDetailFragment + TwoPaneHostFragment + ImNavigationViewModel** — new files per §5.1/§5.2/§3; wire `SlidingPaneLayout`; host under tab 1; retire the temporary placeholder.
7. **Refactor ManageImFragment / ManageRelatedFragment** list visuals per §6.1/§6.3; **add ManageImAddSheet / ManageImEditSheet / ManageRelatedAddSheet / ManageRelatedEditSheet** per §6.2/§6.3; delete legacy `ManageImAddDialog`, `ManageImEditDialog`, `ManageRelatedAddDialog`, `ManageRelatedEditDialog` and their layouts.
8. **LimePreferenceFragment reskin** — Material 3 theme attributes only; host in tab 2 (喜好設定). Do **not** edit `preference.xml` or relocate any key.

At every step the existing **import-from-other-app** path (`IntentHandler` + `IMPORT_*` actions + `ImportDialog`) must be verified by checking that `IntentHandler` still compiles and its `Activity` reference resolves to `LIMESettings`. This is constraint #2.

---

## 14. Resolved Decisions

All questions resolved before implementation began:

1. **Feature flag rollout** → **RESOLVED: single cutover** (no flag). Git worktree provides isolation; flag eliminated to avoid dead-code cleanup later.
2. **Language for new files** → **RESOLVED: Java**. Consistent with all 66 existing source files.
3. **Activity rename** → **RESOLVED: `MainActivity` → `LIMESettings`**. `MainActivityView` → `LIMESettingsView`.
4. **Missing dependencies** → **RESOLVED: add in step 1** — Material 3 `1.12.0`, SlidingPaneLayout `1.2.0`, Lifecycle ViewModel `2.8.7`.
