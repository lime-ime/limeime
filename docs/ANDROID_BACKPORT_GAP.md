# Android Backport Gap - iOS Settings Spec to Android Implementation

> Source of truth: docs/LIME_SETTINGS.md (LIME Settings iOS App Specification).
> This document enumerates every gap between that iOS spec and the current Android
> LIMESettings implementation under LimeStudio/app/src/main/, in order to drive a
> back-port effort to bring the Android Settings UX up to parity with the new iOS design.

## 0. Scope and Direction

- Direction: iOS-spec to Android backport. The iOS spec is the new target design
  (an HIG-driven rethink that should be re-applied to the Android side).
- Excluded from gap list: items the iOS spec marks as iOS-only (status banner via
  App Group flags, keyboard_theme value 6, iPad split keyboard behaviour,
  UIImpactFeedbackGenerator, App Group / shared UserDefaults). See section 7.
- Excluded from this pass: Model layer (SearchServer, DBServer, LimeDB,
  LIMEPreferenceManager) and Controller layer (SetupImController,
  ManageImController, etc.) are already shared between platforms by design; only
  View-layer gaps are tracked.
- Read-only analysis. No Android source files were modified.
- Files sampled: full reads of preference.xml, SetupFragment.java,
  SetupImFragment.java, ImListFragment.java, ImDetailFragment.java,
  ImInstallFragment.java, DbManagerFragment.java, LimePreferenceFragment.java,
  LIMEPreference.java (head + targeted grep over remaining body), plus layouts
  fragment_setup.xml, fragment_db_manager.xml, fragment_im_detail.xml,
  fragment_im_list.xml. ManageImFragment.java and ManageRelatedFragment.java
  were partially sampled (first ~200 lines / ~100 lines); deeper behavioural
  verification of pagination and search filters is recommended before P2 work begins.

---

## 0.1 Tab Labels and Per-Screen Titles

The iOS spec uses a 4-tab TabView with per-screen `.navigationTitle` strings. The
Android side already has the 4 bottom-nav labels and the drill-down toolbars at parity;
what is missing is a top app bar / inline title on the four top-level tab fragments
themselves.

Reference: LimeStudio/app/src/main/res/menu/main_nav.xml, layout/activity_main.xml,
layout/fragment_setup.xml, layout/fragment_im_list.xml, layout/fragment_db_manager.xml,
LimePreferenceFragment.java.

| iOS Spec Item | Android Current | Gap | Severity |
|---|---|---|---|
| Bottom-tab labels 設定 / 輸入法 / 喜好設定 / 資料庫 | main_nav.xml:3-18 declares all 4 with matching icons (ic_gearshape / ic_list_bullet / ic_sliders / ic_archivebox). | At parity. | - |
| Setup tab heading | fragment_setup.xml:57-63 TextView `setup_im_system_settings` ("啟動萊姆輸入法", large) sits below the status banner — restored from prior Android implementation (see §1). | At parity with current direction. | - |
| IM List `.navigationTitle("管理輸入法")` | fragment_im_list.xml has NO MaterialToolbar and NO title TextView. | MISSING. Add MaterialToolbar with `app:title="管理輸入法"` at the top, matching the drill-down toolbar pattern in fragment_im_install.xml. | P2 |
| DB Manager `.navigationTitle("資料庫管理")` | fragment_db_manager.xml has NO MaterialToolbar (only inline section subtitles). | MISSING. Add MaterialToolbar with `app:title="資料庫管理"`. iOS spec hides the nav bar on iPad regular width and shows an inline `.title2.bold()`; for Android keep a single MaterialToolbar across screen sizes. | P2 |
| Preferences `.navigationTitle` (`喜好設定`) | LimePreferenceFragment.java is a bare FrameLayout hosting LIMEPreference.PrefsFragment with no enclosing toolbar (LimePreferenceFragment.java:28-43). | MISSING. Wrap the PrefsFragment host in a layout that adds a MaterialToolbar at the top with the screen title. | P2 |
| Drill-down toolbars (IM Detail / IM Install / Record / Related editors) | All four have MaterialToolbar (fragment_im_detail.xml:8, fragment_im_install.xml:8, fragment_manage_im.xml:14, fragment_manage_related.xml:15). | At parity. | - |
| IM Detail title source | ImDetailFragment.java:87 sets toolbar title to `imDesc` (IM display name) — matches iOS `.navigationTitle(im.label)`. | At parity. | - |

---

## 1. App Setup (Setup Tab) - section 4

**Direction (2026-05-13): the step list / button block under the status banner is NOT a
backport target.** The iOS Gboard-style three-step list + single CTA was an HIG-driven
rethink for iOS; the Android side retains its prior platform-native two-step setup
(Step 1 enable LimeIME in system Input Methods → Step 2 switch to LimeIME via IME picker)
with the existing conditional-visibility behaviour. Only the status banner above and the
About card below are at parity with iOS.

**Status: revert applied.** fragment_setup.xml was edited 2026-05-13 to delete the iOS
inline title, two-step icon list, and privacy note, and to restore the prior Android
heading + two description TextViews (see git diff for the exact change).

Reference: LimeStudio/app/src/main/java/org/limeime/ui/view/SetupFragment.java,
LimeStudio/app/src/main/res/layout/fragment_setup.xml, prior implementation preserved at
LimeStudio/app/src/main/res/layout/fragment_setup_im.xml (lines 55-128) for historical
reference.

| Block | Android Current | Status |
|---|---|---|
| Brand block (logo + 萊姆輸入法 wordmark) | fragment_setup.xml:17-38 horizontal logo 120dp + wordmark | Keep. |
| Status banner (3 states: green/yellow/red) | fragment_setup.xml:41-55 statusCard + statusText, driven by SetupFragment.refreshStatus() (SetupFragment.java:135-157) reading LIMEUtilities.isLIMEEnabled / isLIMEActive | Keep. At parity with iOS spec §4.2. |
| Setup heading "啟動萊姆輸入法" | fragment_setup.xml:57-63 TextView `setup_im_system_settings` (large) | **Restored.** |
| Step 1 description (尚未啟用) | fragment_setup.xml:65-70 TextView `setup_im_system_settings_description` (medium) | **Restored.** |
| Step 2 description (已啟用但尚未選用) | fragment_setup.xml:72-77 TextView `setup_im_system_impicker_description` (medium) | **Restored.** |
| Step 1 button "啟用萊姆輸入法" (open system Input Methods settings) | btnSetupImSystemSetting (fragment_setup.xml:80-86) using string `setup_im_wizard_nextstep`; wired to LIMEUtilities.showInputMethodSettingsPage; hidden when `isLIMEEnabled` (SetupFragment.java:144-156) | Keep wiring + hide-on-enabled logic. |
| Step 2 button "選擇萊姆輸入法" (open IME picker) | btnSetupImSystemIMPicker (fragment_setup.xml:89-95) using string `setup_im_system_selectLIME`; wired to LIMEUtilities.showInputMethodPicker; hidden when `isLIMEActive` and visible only when enabled-but-not-active (SetupFragment.java:144-156) | Keep wiring + hide-on-active logic. |
| About card (Version / License / GitHub) | fragment_setup.xml:97-191 MaterialCardView with three rows | Keep. |
| ~~iOS inline title "設定 LimeIME"~~ | ~~REMOVED~~ | Done. |
| ~~iOS-style 2-step icon list~~ | ~~REMOVED~~ | Done. |
| ~~iOS privacy explanatory note (`setup_im_privacy_note`)~~ | ~~REMOVED~~ | Done. |

Conditional-visibility contract (implemented in `SetupFragment.refreshStatus()`):

- `not enabled`         → statusCard red, btnSetupImSystemSetting **VISIBLE**, btnSetupImSystemIMPicker **GONE**
- `enabled, not active` → statusCard yellow, btnSetupImSystemSetting **GONE**, btnSetupImSystemIMPicker **VISIBLE**
- `enabled, active`     → statusCard green, **BOTH buttons GONE**

No further action items for the Setup tab. Validate visually on a clean install (red banner),
after enabling LimeIME in system settings (yellow banner), and after selecting it as the
active IME (green banner).

---
## 2. IM Manager (IM Tab) - section 5

### 2.1 IM List - section 5.1

Reference: LimeStudio/app/src/main/java/org/limeime/ui/view/ImListFragment.java,
fragment_im_list.xml, item_im_row.xml.

| iOS Spec Item | Android Current | Gap | Severity |
|---|---|---|---|
| IM list with enable Toggle | RecyclerView rows with SwitchMaterial writing im.disable via ManageImController.setImEnabled (ImListFragment.java:182-215); emoji filtered out (ImListFragment.java:99-114). | At parity. | - |
| Sorted by im.sortOrder | getImConfigFullNameList() order; sort source unverified. | Confirm controller sorts by sort_order. | P3 |
| Enabled/disabled opacity HALF_ALPHA_VALUE | Implemented (ImListFragment.java:194, 201). | At parity. | - |
| Related-phrase row appended | TYPE_RELATED row with ic_list_bullet, no toggle, now pushes `ImDetailFragment` with a synthetic `ImConfig(code="related", desc="關聯字庫")` (ImListFragment.java:283-294) — matches iOS `IMRow(id:-1, tableNick:"related")` pattern (LIME_SETTINGS.md:343,401). | At parity. **DONE 2026-05-14.** | - |
| Section headers | No headers; flat list. | Add TYPE_HEADER_* rows. | P2 |
| Toolbar plus install button | FAB fab_install (fragment_im_list.xml:15-24, ImListFragment.java:67-81). | At parity (drag-to-reorder dropped per iOS spec parity). | - |
| Toggle also updates keyboard_state | Spec section 10.4 mandates LIMEPreferenceManager.syncIMActivatedState() after toggle | Verify setImEnabled calls it (currently only SetupImFragment.onPause does). | P2 |

### 2.2 IM Detail - section 5.2

Reference: LimeStudio/app/src/main/java/org/limeime/ui/view/ImDetailFragment.java,
fragment_im_detail.xml.

| iOS Spec Item | Android Current | Gap | Severity |
|---|---|---|---|
| Section IM info: Name / Version / Record count | Name + record count only (fragment_im_detail.xml:28-93, ImDetailFragment.java:99-101, 141-155). VERSION MISSING. | Add Version row reading getParameterString(tableNick + "mapping_version", "-"). | P2 |
| Soft keyboard picker section | row_keyboard opens AlertDialog picker (ImDetailFragment.java:177-208). | Functional parity; no selected-item checkmark. | P3 |
| Phonetic keyboard type Picker (phonetic-only, section 5.2.2, 6 options) | NOT in ImDetailFragment. Still global pref (preference.xml:200-206). | MISSING. Add conditional Section when tableCode == phonetic; on change call DBServer.setImConfigKeyboard(phonetic, kb). | P1 |
| Array10 phone-keypad Picker on auto_commit | NOT in ImDetailFragment. Still global pref (preference.xml:180-186). | MISSING. Add conditional Section when tableCode == array10. | P1 |
| Custom-IM root mapping toggles (accept_number_index / accept_symbol_index) | NOT in ImDetailFragment. Still global prefs (preference.xml:388-395). | MISSING. Add conditional Section when tableCode == custom. | P1 |
| Browse/edit table NavigationLink | row_manage_table pushes ManageImFragment (ImDetailFragment.java:108-115). | At parity. | - |
| Options: backup_on_delete_{tableNick} toggle, default true | Implemented (fragment_im_detail.xml; ImDetailFragment.java:166-176). Default `true` (`ImDetailFragment.java:169`). | At parity. | - |
| Remove IM destructive button | Button + confirm dialog rendered, but on confirm only shows Function-under-development Toast (ImDetailFragment.java:210-219). | MISSING CORE FEATURE. Wire to manageImController.clearTable(tableCode, switchBackup.isChecked()), then sync IM activated state, dismiss. Confirm-message wording varies by backup toggle state. | P1 |
| Related-phrase detail page | iOS uses synthetic IMDetail; Android now matches — `ImListFragment` constructs `ImConfig(code="related")` and pushes `ImDetailFragment` (ImListFragment.java:283-294). `ImDetailFragment` detects `"related".equals(tableCode)` and hides 軟鍵盤配置 / 選項 / 版本 sections, retexts 字根資料表 → 關聯字庫 / 瀏覽 / 編輯關聯字庫, and routes the table row to `ManageRelatedFragment`. 移除輸入法 button is kept so users can clear the related table to load their own (per user direction 2026-05-14 — iOS spec line 387's "hidden when related" is overridden). | **DONE 2026-05-14.** | - |
| Share / Export from detail | ShareManager/ShareDialog exist; no entry point on detail. | Add share icon to im_detail_toolbar. | P2 |

### 2.3 IM Install (Download + Import) - section 5.3

Reference: LimeStudio/app/src/main/java/org/limeime/ui/view/ImInstallFragment.java,
fragment_im_install.xml, item_im_family_card.xml.

| iOS Spec Item | Android Current | Gap | Severity |
|---|---|---|---|
| 13 per-IM DisclosureGroups + related | buildFamilyList() produces all 13 + related (ImInstallFragment.java:339-457). | At parity. | - |
| Display titles match spec | Android title differences vs spec (ImInstallFragment.java:377, 386, 418, 431, 438). | Align or document divergence. | P3 |
| Cloud variants per IM | Pinyin GB variant missing; Android lists only one (ImInstallFragment.java:442-445); spec has CLOUD_PINYIN + CLOUD_PINYINGB. | Audit each cloudVariants; add CLOUD_PINYINGB. | P2 |
| restore_on_import_{tableNick} toggle, default true | Implemented (ImInstallFragment.java:222-232, 564-572). | At parity. | - |
| Import .limedb + Import .cin/.lime per group, fixed tableName | Implemented (ImInstallFragment.java:604-613); .cin/.lime hidden for related. | At parity. | - |
| Custom IM: post-import seedCustomIM() | Custom family declared (ImInstallFragment.java:449-450); seeding step in controller not verified. | Verify; add if missing. | P2 |
| Progress overlay during import/download | ProgressManager exists; wiring through controllers not verified. | Confirm. | P2 |
| Status footer text | iOS has a Status Section bound to statusMessage. | Android has none here (toast-only). Add or accept. | P3 |
| Group iconography | ic_keyboard_outline / ic_archivebox / ic_add / ic_list_bullet (ImInstallFragment.java:352, 364, 449-454). | Cosmetic. | P3 |
| Auto-collapse cards already installed | Implemented (ImInstallFragment.java:469-471, 633-653). Spec silent. | Android enhancement; consider porting to iOS. | P3 |

---

## 3. IM Table Editor - section 6

The two Android editors are inherently inconsistent with each other AND both diverge
from the iOS spec. The IM (mapping) editor surfaces controls the Related editor does
not (keyboard selector, 字根/文字 toggle), and both use a 2-column grid + an inline
search row + bottom pagination bar instead of the iOS single-column List + .searchable +
.swipeActions + toolbar + button + paginator pattern. This entire screen pair should
be re-built to share a single Material design layout.

Reference: LimeStudio/app/src/main/java/org/limeime/ui/view/ManageImFragment.java,
ManageRelatedFragment.java, layout/fragment_manage_im.xml, layout/fragment_manage_related.xml,
ui/dialog/ManageImAddSheet.java, ManageImEditSheet.java, ManageRelatedAddSheet.java,
ManageRelatedEditSheet.java.

### 3.1 Structural Divergence Between the Two Android Editors

The iOS spec defines RecordListView and RelatedListView as the SAME visual template
(differing only in row schema and search filter). Android currently does not:

| Element | ManageImFragment (字根) | ManageRelatedFragment (關聯字) | iOS Spec |
|---|---|---|---|
| Toolbar title | "字根管理" (R.string.im_detail_manage_table, fragment_manage_im.xml:18) | "關聯字庫" (R.string.im_related_label, fragment_manage_related.xml:19) | im.label (§6.1) / "關聯字管理" (§6.2) |
| Keyboard-select button at top | btnManageImKeyboard (fragment_manage_im.xml:30-37, ManageImFragment.java:186-196) — opens ManageImKeyboardDialog | (absent) | iOS moves keyboard selection to IMDetailView (§5.2). On Android it should also live on ImDetailFragment, not here. |
| 字根 / 文字 search-by toggle | ToggleButton toggleManageIm (fragment_manage_im.xml:45-53, ManageImFragment.java:198-207) — sets searchroot flag | (absent — related has only word search) | Picker segmented ["字根","文字"] (§6.1); Related has no toggle (§6.2). Behaviour at parity for Related; Android IM uses ToggleButton instead of segmented Picker but the underlying flag is the same. |
| Search input | EditText edtManageImSearch + explicit Search button toggling to Reset (ManageImFragment.java:241-262) | EditText + same explicit Search/Reset button (ManageRelatedFragment.java:205-225) | .searchable(text:) with native clear (§6.1, §6.2). Both Android editors use explicit Search/Reset button; iOS uses live searchable. |
| Add button placement | btnManageImAdd inline in the search row (fragment_manage_im.xml:70-76) | btnManageRelatedAdd inline in the search row (fragment_manage_related.xml:51-57) | Toolbar + icon (§6.1, §6.2). Both Android editors have Add inline rather than as a toolbar action. |
| Grid layout | GridLayoutManager(activity, 2) (ManageImFragment.java:170) | GridLayoutManager(activity, 2) (ManageRelatedFragment.java:156) | Single-column List (§6.1, §6.2). |
| Row → edit | Tap-row opens ManageImEditSheet (ManageImFragment.java:172-176) | Tap-row opens ManageRelatedEditSheet (ManageRelatedFragment.java:158-162) | Swipe-left to reveal 編輯 + 刪除 (§6.1, §6.2). |
| Delete trigger | Inside edit sheet only — no direct delete from list | Inside edit sheet only | Direct swipe action with confirm alert (§6.1, §6.2). |
| Pagination | btnManageImPrevious / btnManageImNext + txtNavigationInfo "1-100 of 1000" via LIME.format (ManageImFragment.java:209-227, 377-383) | Same controls / same format (ManageRelatedFragment.java:173-191, 297-302) | "< 上頁" / "第 N 頁 / 共 M 筆" / "下頁 >" (§6.1, §6.2). Functional parity, label format differs. |
| Toast on empty result | R.string.no_search_result (ManageImFragment.java:374, ManageRelatedFragment.java:294) | Same | iOS shows empty-state inline; no toast. (§6.1 silent — recommend empty Section text.) |

### 3.2 Mapping Record Editor - section 6.1

Reference: ManageImFragment.java, fragment_manage_im.xml, ManageImAdapter.java,
ManageImAddSheet.java, ManageImEditSheet.java.

| iOS Spec Item | Android Current | Gap | Severity |
|---|---|---|---|
| Single-column List | 2-column grid (ManageImFragment.java:170) | Switch to LinearLayoutManager. | P2 |
| .searchable native search bar | EditText + explicit Search/Reset button (ManageImFragment.java:241-262) | Replace with SearchView (or app:menu searchView) for live filter, drop the Reset toggle. | P2 |
| Picker segmented ["字根","文字"] | ToggleButton textOff="字根" textOn="文字" (fragment_manage_im.xml:45-53) | Cosmetic — Material SegmentedButton for visual parity. | P3 |
| Row HStack: code monospaced / word / score | ManageImAdapter row layout (sampled file not opened here) | Verify monospaced typeface for code and right-aligned score caption. | P3 (verify) |
| Swipe-to-edit + swipe-to-delete | Tap-row opens edit sheet; no swipe (ManageImFragment.java:172-176) | Add ItemTouchHelper.SimpleCallback with edit/delete reveal. | P2 |
| Toolbar + icon to add | btnManageImAdd inline in search row (fragment_manage_im.xml:70-76, ManageImFragment.java:179-184) | Move into MaterialToolbar menu (app:showAsAction="always"). | P2 |
| Keyboard-select button does not belong here | btnManageImKeyboard at top (fragment_manage_im.xml:30-37, ManageImFragment.java:186-196) | Remove from this screen; keyboard selection lives on IMDetailView (§5.2). | P2 |
| Pagination label "第 N 頁 / 共 M 筆" | "1-100 of 1000" via LIME.format (ManageImFragment.java:377-383) | Replace string template (R.string.manage_im_navigation_info already used at line 474 — unify and convert format to page-based). | P3 |
| AddRecord sheet: code / word / score Stepper | ManageImAddSheet (file not opened in this pass) | Verify schema; confirm score widget is a Stepper-equivalent. | P3 (verify) |
| EditRecord sheet: code / word / +/- score / Save / Delete with confirm | ManageImEditSheet (file not opened in this pass); Delete reachable via showDeleteConfirmDialog (ManageImFragment.java:494-501) | Verify schema and confirm-alert wording. | P3 (verify) |
| No keyboard-select button at top | Present (see above) | Same removal item — Severity P2. | (dup) |
| Title at parity with IM label | toolbar title is fixed string "字根管理" / R.string.im_detail_manage_table (fragment_manage_im.xml:18) | iOS spec says navigationTitle == im.label. Programmatically set toolbar title to the IM's display name on open. | P2 |

### 3.3 Related Phrase Editor - section 6.2

Reference: ManageRelatedFragment.java, fragment_manage_related.xml,
ManageRelatedAdapter.java, ManageRelatedAddSheet.java, ManageRelatedEditSheet.java.

| iOS Spec Item | Android Current | Gap | Severity |
|---|---|---|---|
| Single-column List | 2-column grid (ManageRelatedFragment.java:156) | Switch to LinearLayoutManager. | P2 |
| .searchable("搜尋詞彙") | EditText + Search/Reset button (ManageRelatedFragment.java:205-225) | Same SearchView migration as §3.2. | P2 |
| Row HStack: word bold / related secondary | ManageRelatedAdapter row layout (file not opened in this pass) | Verify typography (word bold, related secondary). | P3 (verify) |
| Swipe-to-edit + swipe-to-delete | Tap-row opens edit sheet (ManageRelatedFragment.java:158-162); delete via showDeleteConfirmDialog (ManageRelatedFragment.java:369-378) | Add ItemTouchHelper.SimpleCallback. | P2 |
| Toolbar + icon to add | btnManageRelatedAdd inline (fragment_manage_related.xml:51-57) | Move into MaterialToolbar menu. | P2 |
| Search prefix vs contains on word | searchRelated invokes loadRelatedPhrases (ManageRelatedFragment.java:238-250) — query semantics unverified in this pass. | Verify against §6.2 contains-match. | P3 (verify) |
| Title "關聯字管理" | R.string.im_related_label (fragment_manage_related.xml:19) — current copy is "關聯字庫". | Align string resource to "關聯字管理" if iOS wording is canonical (product decision). | P3 |
| AddRelated sheet: word / related fields | ManageRelatedAddSheet (file not opened) | Verify field labels + non-empty validation. | P3 (verify) |
| EditRelated sheet: word / related / Save / Delete with confirm | ManageRelatedEditSheet (file not opened); confirm flow uses showDeleteConfirmDialog (ManageRelatedFragment.java:369-378) | Verify schema and confirm-alert wording. | P3 (verify) |
| Pagination label format | "1-100 of 1000" (ManageRelatedFragment.java:297-302) | Same format unification as §3.2. | P3 |

> ManageImAddSheet / ManageImEditSheet / ManageRelatedAddSheet / ManageRelatedEditSheet
> were not opened in this pass — verify-marked items above need a second pass to confirm
> field schema, validation, and confirm-alert wording match iOS spec §6.1.1-§6.1.2 and §6.2.1-§6.2.2.

### 3.4 Add / Edit Sheet Verification

Now verified against the four sheet sources (ManageImAddSheet.java, ManageImEditSheet.java,
ManageRelatedAddSheet.java, ManageRelatedEditSheet.java) and their layouts
(sheet_manage_im_add.xml, sheet_manage_im_edit.xml, sheet_manage_related_add.xml,
sheet_manage_related_edit.xml).

| iOS Spec Item | Android Current | Gap | Severity |
|---|---|---|---|
| §6.1.1 AddRecord: code TextField, word TextField, score Stepper, default 0 | ManageImAddSheet.java:73-87 has edt_code, edt_word, tv_score with -/+ buttons. **Initial score = 1** (line 52). | Initial score should be 0 per spec. | P3 |
| §6.1.1 "確認新增" with non-empty guard | btn_save guards both fields non-empty (ManageImAddSheet.java:89-100); Toast(R.string.insert_error) on failure. | Functional parity; replace Toast with inline TextInputLayout error. | P3 |
| §6.1.2 EditRecord: prefilled code/word/score, +/- score, Save with confirmAlert, Delete destructive with confirmAlert | ManageImEditSheet.java:81-116 prefills + has +/- buttons. **Save has NO confirm alert** (line 105). **Delete has NO confirm alert** (line 98-103) — direct call to removeRecord. | iOS spec mandates confirmAlert on both Save and Delete. **MISSING confirm dialogs in the sheet.** Note: ManageImFragment.showDeleteConfirmDialog (ManageImFragment.java:494-501) exists but is dead code — never invoked, because the sheet calls hostFragment.removeRecord directly. | P2 |
| §6.2.1 AddRelated: word TextField + related TextField (TWO separate fields) | **ManageRelatedAddSheet.java:88-100 has ONE EditText (edt_word) only**. User types a concatenated string; on save the code does `pword = source.substring(0,1); cword = source.substring(1)` — pword is always exactly the first character. | **CRITICAL UX BUG / SPEC MISMATCH.** Two separate fields are required per iOS spec; single-char pword is also too restrictive for related-word pairs whose head is multi-char. Layout sheet_manage_related_add.xml needs a second TextInputEditText, and hostFragment.addRelated(pword, cword, score) must receive the user-typed values directly. | P1 |
| §6.2.1 No score field on AddRelated (iOS spec lists only word + related) | ManageRelatedAddSheet has +/- score buttons (line 77-86). | Either keep Android's score input as an enhancement (note in spec), or remove for spec parity. Product decision. | P3 |
| §6.2.2 EditRelated: prefilled word + related (separate), Save + Delete with confirmAlerts | ManageRelatedEditSheet.java:80-83 prefills as `edtWord.setText(related.getPword() + related.getCword())` — concatenated into ONE field. Same substring(0,1) split on Save (line 109-110). **Save and Delete have NO confirm alert** (lines 96-115). | Same one-field bug as Add sheet + missing confirms. Editing a multi-character head pword would silently corrupt the row to length-1. | P1 |
| §6.1.2 / §6.2.2 Toast on validation vs iOS inline error | Both Edit sheets fall back to Toast (R.string.update_error). | Replace with TextInputLayout error text. | P3 |

> The list-fragment helper showDeleteConfirmDialog (ManageImFragment.java:494-501,
> ManageRelatedFragment.java:369-378) is currently unreachable code, because the
> only delete path runs through the Edit sheet's btn_delete which bypasses the
> confirm. After the P1 fix to add confirms inside the sheet, decide whether to
> delete the dead helper or have the sheet delegate confirmation back to the
> fragment.
---

## 4. DB Manager (Database Tab) - section 7

Reference: DbManagerFragment.java, fragment_db_manager.xml.

| iOS Spec Item | Android Current | Gap | Severity |
|---|---|---|---|
| Three sections: Backup / Restore / Restore default | All three present (fragment_db_manager.xml:16-118, DbManagerFragment.java:93-99). | At parity. | - |
| Backup via share sheet / Files | ACTION_CREATE_DOCUMENT (DbManagerFragment.java:113-130) + MediaStore Downloads fallback (DbManagerFragment.java:152-199). | At parity. | - |
| Restore with confirm alert before file picker | Confirm via showAlertDialog(RESTORE) (DbManagerFragment.java:214-233, 280-305). | At parity. | - |
| Restore default with confirm alert | Implemented (DbManagerFragment.java:265-274). | At parity. | - |
| Progress overlay during backup/restore | ProgressManager exists; not visibly wired here. | Verify performBackup/performRestore post progress. | P2 |
| Status footer block | dbStatusCard declared but visibility=gone (fragment_db_manager.xml:121-143); never toggled. | Wire dbStatusText updates. | P3 |
| iPad 560pt width cap | maxWidth=560dp + layout_gravity=center_horizontal already applied (fragment_db_manager.xml:13-14). | At parity. | - |
| Restore footer copy | Android copy differs from spec (fragment_db_manager.xml:83). | Align copy. | P3 |

---

## 5. IM Preferences (Preferences Tab) - section 8

Reference: preference.xml, LIMEPreference.java, LimePreferenceFragment.java.

iOS re-organises preferences into 8 sections + a Reverse-Lookup sub-screen. Android
still uses the legacy 3 PreferenceCategory blocks: lime_keyboard, lime_im,
lime_mapping (preference.xml:31, 157, 331). Several keys belong in per-IM detail
screens (section 2.2), not the global preferences screen.

### 5.1 Keyboard Appearance (section 8.1)

| iOS Spec Item | Android Current | Gap | Severity |
|---|---|---|---|
| Section header | All under lime_keyboard (preference.xml:34). | Add dedicated category. | P2 |
| keyboard_theme Picker (0-5 plus 6=system follow) | defaultValue=6 (preference.xml:35-41). | At parity; Android current resolves value 6 via system night mode. | - |
| enable_emoji_position Picker, default 6 | defaultValue=6; first option `0` disables inline emoji candidates. | At parity; replaces the removed emoji toggle. | - |
| keyboard_size Picker, default 1.1 | defaultValue=1 (preference.xml:116-122). | DEFAULT MISMATCH; change to 1.1. | P2 |
| show_arrow_key Picker, default 0 | defaultValue=0 (preference.xml:80-86). | At parity. | - |
| split_keyboard_mode Picker (iPad only) | Present (preference.xml:89-95). | iOS-only hide rule; Android keep or product decision. | P3 |

### 5.2 Keyboard Feedback (section 8.2)

| iOS Spec Item | Android Current | Gap | Severity |
|---|---|---|---|
| Section header | Inline (preference.xml:130-144). | Reorg. | P2 |
| vibrate_on_keypress Toggle, default true | defaultValue=true (preference.xml:130-133). | At parity. | - |
| vibrate_level Picker, default 40 | defaultValue=40 (preference.xml:134-140); hidden on API 31+ (LIMEPreference.java:169-174). | Platform-appropriate divergence. Keep. | - |
| sound_on_keypress Toggle, default false | defaultValue=false (preference.xml:141-144). | At parity. | - |

### 5.3 Font and Display (section 8.3)

| iOS Spec Item | Android Current | Gap | Severity |
|---|---|---|---|
| Section header | Not a separate category. | Add. | P2 |
| font_size, default 1.1 | defaultValue=1 (preference.xml:123-129). | DEFAULT MISMATCH; change to 1.1. | P2 |
| number_row_in_english Toggle, default true | defaultValue=true (preference.xml:70-74). | At parity. | - |

### 5.4 IM Behaviour (section 8.4)

| iOS Spec Item | Android Current | Gap | Severity |
|---|---|---|---|
| Section header | Items in lime_im (preference.xml:157). | Reorg. | P2 |
| smart_chinese_input Toggle, default true (section 8.4 table) | defaultValue=false (preference.xml:162-166). | DEFAULT MISMATCH vs section 8.4 (section 9 reference table conflicts; likely typo). Confirm with product. | P2 (verify) |
| auto_chinese_symbol Toggle, default true (section 8.4 table) | defaultValue=false (preference.xml:169-173). | Same caveat. | P2 (verify) |
| candidate_switch Toggle, default true | defaultValue=true (preference.xml:358-362). | At parity. | - |
| disable_physical_selkey | Present (preference.xml:175-178); iOS drops as external-keyboard feature. | See section 8 review. | - |

### 5.5 Han Conversion (section 8.5)

| iOS Spec Item | Android Current | Gap | Severity |
|---|---|---|---|
| Section header | Inline (preference.xml:217-228). | Reorg. | P2 |
| han_convert_option Picker (segmented), default 0 | defaultValue=0 (preference.xml). | Functional parity; segmented is iOS-only. | P3 |

### 5.6 Related and Learning (section 8.6)

| iOS Spec Item | Android Current | Gap | Severity |
|---|---|---|---|
| Section header | Items in lime_mapping (preference.xml:331). | Reorg. | P2 |
| similiar_enable Toggle, default true | defaultValue=true (preference.xml:342-346). | At parity. | - |
| similiar_list Picker, default 20 | defaultValue=20 (preference.xml:335-341). | At parity. | - |
| candidate_suggestion Toggle, default true | defaultValue=true (preference.xml:363-367). | At parity. | - |
| learn_phrase Toggle, default true | defaultValue=true (preference.xml:373-377). | At parity. | - |
| learning_switch Toggle, default true | defaultValue=true (preference.xml:378-382). | At parity. | - |

### 5.7 English Dictionary (section 8.7)

| iOS Spec Item | Android Current | Gap | Severity |
|---|---|---|---|
| Section header | Inline in lime_mapping. | Reorg. | P2 |
| english_dictionary_enable Toggle, default true | defaultValue=true (preference.xml:347-351). | At parity. | - |
| english_dictionary_physical_keyboard | Present (preference.xml:352-357); iOS drops. | See section 8 review. | - |

### 5.8 Advanced (section 8.8)

| iOS Spec Item | Android Current | Gap | Severity |
|---|---|---|---|
| Section header | None. | Reorg. | P2 |
| persistent_language_mode Toggle, default false | defaultValue=false (preference.xml:65-69). | Move into new section. | P2 (reorg) |

### 5.9 Reverse Lookup (section 8.9) - Sub-screen

| iOS Spec Item | Android Current | Gap | Severity |
|---|---|---|---|
| Drill-down sub-screen with 13 IM x 14 source pickers | Android renders all 13 ListPreference flat under lime_im (preference.xml:236-327). | Refactor into nested PreferenceScreen (app:fragment=) or dedicated Fragment. | P2 |
| 14 options (none + 13 IM codes) | @array/im_reverse_lookup_codes + @array/im_reverse_lookup; verify both contain all 14. | Verify resource arrays. | P3 (verify) |
| Helper text | No helper text. | Add as Preference summary/category description. | P3 |

### 5.10 Preferences to relocate (NOT in iOS global Preferences screen)

| Pref Key | Currently in preference.xml | Should be in | Severity |
|---|---|---|---|
| phonetic_keyboard_type | line 200-206 (lime_im) | ImDetailFragment when tableCode == phonetic | P1 |
| auto_commit | line 180-186 (lime_im) | ImDetailFragment when tableCode == array10 | P1 |
| accept_number_index | line 388-391 (lime_mapping) | ImDetailFragment when tableCode == custom | P1 |
| accept_symbol_index | line 392-395 (lime_mapping) | ImDetailFragment when tableCode == custom | P1 |

---

## 6. New Preference Keys to Add on Android

| Pref Key | Android Current | Spec Default | Gap |
|---|---|---|---|
| backup_on_delete_{tableNick} | ImDetailFragment.java:169 reads default true | true | At parity. |
| restore_on_import_{tableNick} | ImInstallFragment.java:222-232 reads default true | true | At parity. |

Storage: spec uses UserDefaults.standard (not the App Group). Android uses
PreferenceManager.getDefaultSharedPreferences(requireContext()); appropriate analogue.

---

## 7. iOS-only - Do NOT Backport

- App Group / shared UserDefaults. Android keyboard + Settings already share the
  same process SharedPreferences.
- split_keyboard_mode iPad-only hide rule. Android has no iPad/iPhone split.
- Status banner via App Group flags. Android uses LIMEUtilities.isLIMEEnabled /
  isLIMEActive (SetupFragment.java:138-139).
- UIImpactFeedbackGenerator mapping. Android uses raw vibrate ms; API 31+ haptics
  supersede the user-controlled level.
- AppStorage(store:) / sharedDefaults. SwiftUI binding, no analogue.
- ShareLink backup. Android uses ACTION_CREATE_DOCUMENT.
- Full Access toggle / Setup step 3. No Android analogue.
- scenePhase re-check. Android uses BroadcastReceiver + onResume.
- In-candidate-bar Han convert idle banner. iOS extension cannot post system
  notifications; Android uses system notifications.

---

## 8. Android-only Features Not in iOS Spec (informational)

iOS spec section 10.1 drops these. Not automatic gaps; flagged for product review.

| Pref Key | Location | iOS Spec Rationale | Recommendation |
|---|---|---|---|
| hide_software_keyboard_typing_with_physical | preference.xml:75-79 | External keyboard not supported on iOS | Keep on Android. |
| switch_english_mode | preference.xml:145-149 | External keyboard | Keep on Android. |
| switch_english_mode_shift | preference.xml:150-154 | External keyboard | Keep on Android. |
| disable_physical_selkey | preference.xml:175-178 | External keyboard | Keep on Android. |
| selkey_option | preference.xml:350-356 | External keyboard; no iOS analogue | Keep on Android. |
| physical_keyboard_type | preference.xml:208-214 | Legacy phone hardware keyboard layout type | Retired from visible Preferences; runtime default remains for legacy stored values. |
| english_dictionary_physical_keyboard | preference.xml:352-357 | External keyboard | Keep on Android. |
| physical_keyboard_sort | preference.xml:383-387 | External keyboard | Keep on Android. |
| auto_cap | not in preference.xml | iOS reads autocapitalizationType directly | Verify no leftover references. |

Google Drive backup is dropped on iOS (section 10.1). DbManagerFragment already
exposes only local-file/Downloads backup; at iOS-parity for this View. (Legacy
Drive code may exist in SetupImController; out of View-layer scope.)

---

## 9. Summary - Prioritised Backlog

### P1 - Missing core features (block parity)

1. Related Add/Edit sheets use a single concatenated EditText with `substring(0,1)` split — replace with two separate word/related TextFields (sheet_manage_related_add.xml / sheet_manage_related_edit.xml + ManageRelatedAddSheet.java:88-100 + ManageRelatedEditSheet.java:80-115).
2. IM Detail Remove-IM wiring; currently a Toast stub (ImDetailFragment.java:213-216); call manageImController.clearTable(tableCode, backupOnDelete) and dismiss.
3. IM Detail root-mapping section for tableCode == custom (move accept_number_index / accept_symbol_index from preference.xml:388-395).
4. IM Detail phone-keypad section for tableCode == array10 (move auto_commit from preference.xml:180-186).
5. IM Detail phonetic-keyboard-type section for tableCode == phonetic (move phonetic_keyboard_type from preference.xml:200-206).

### P2 - UX divergence (visible mismatch)

1. Top-of-screen titles missing on tab fragments — add MaterialToolbar with title to fragment_im_list.xml (管理輸入法), fragment_db_manager.xml (資料庫管理), and the preferences host (喜好設定). See §0.1.
2. Reverse-Lookup sub-screen; collapse 13 flat ListPreference into a single drill-down (preference.xml:236-327).
3. Preferences re-sectioning into the 8-section layout from spec section 8.
4. ~~Default mismatches~~ — RESOLVED 2026-05-14: `backup_on_delete_{*}`=true, `keyboard_size`="1", `font_size`="1", `smart_chinese_input`=true, `auto_chinese_symbol`=false, `han_convert_option`=0 — all aligned on both platforms. The old emoji toggle was later folded into `enable_emoji_position = 0`.
5. ~~App Setup: revert step list / privacy note / inline title~~ — DONE 2026-05-13 (see §1).
6. IM Detail: add Version row + share/export toolbar action.
7. IM List: section headers, syncIMActivatedState after toggle.
8. Record / Related editors: rebuild to one shared layout — single-column list, native SearchView, +-in-toolbar, swipe edit/delete, programmatic IM-label title; remove the keyboard-select button from the record editor (§3.1, §3.2, §3.3).
9. Edit-sheet confirms — add confirmAlert before Save and before Delete in ManageImEditSheet and ManageRelatedEditSheet (§3.4); the existing dead helper showDeleteConfirmDialog can be wired or removed.
10. IM Install: add pinyin GB variant, audit cloud lists, verify seedCustomIM post-import, confirm progress overlay.
11. DB Manager: wire status footer, confirm progress overlay, align restore-section footer copy.

### P3 - Cosmetic / parity polish

1. Logo + wordmark stack direction in fragment_setup.xml.
2. Single-CTA vs two-button on App Setup.
3. Dedicated related detail screen vs direct-push.
4. Display title alignment in IM Install.
5. Group icons review in IM Install.
6. Keyboard picker checkmark in IM Detail.
7. Search-bar style (Toolbar SearchView) in record/related editors.
8. Grid vs single-column record layout.
9. Verify Han convert / Reverse Lookup / keyboard-theme arrays match spec value lists.
10. Status footer copy alignment in DB Manager / IM Install.
11. Product decision on Android-only physical-keyboard prefs (section 8).
12. Verify pagination / search-filter / confirm-alert behaviour in ManageImFragment and ManageRelatedFragment bodies (lines >200 not sampled).

---

## 10. TODO Checklist - Close the Gaps

> **Session log 2026-05-14 (visual-parity pass against iOS screenshots):** Side-by-side comparison of each tab/screen against the iOS reference screenshots in `docs/assets/lime_settings_ios_*.png`, with verification on emulator after each change. All edits are layout/styling-only (no controller/model changes). Build clean: `gradlew :app:installDebug`.
>
> - **Setup tab status banner** — Three colour states (`fragment_setup.xml` + `SetupFragment.refreshStatus()`) restyled: neutral grey background `@color/setup_status_bg` with colour applied to icon + text only (green/yellow/red foreground), not as solid fill. About card stroke set to `0dp`, background switched to `setup_status_bg` (white frame removed).
> - **Preferences page left padding** — Programmatic `setIconSpaceReserved(false)` cascade through `PreferenceGroup` tree (`LIMEPreference.disableIconSpaceReserved`) after `setPreferencesFromResource`, because the XML-only attribute does not propagate to children on AndroidX Preference. `search_bar_bg.xml` switched to filled grey pill (no stroke).
> - **Related editor (`ManageRelatedFragment` + `fragment_manage_related.xml`)** — Heading set to "關聯字管理"; row layout (`related.xml`) rebuilt as 3-column `pword` (start, weight 2) / `cword` (center, weight 3) / `freq` (end, weight 2, `%,d` formatted, secondary colour); bold removed. `DividerItemDecoration` between rows. Pagination bar pushed above `BottomNavigationView` at runtime via `bottomNav.post { lp.bottomMargin = navHeight }` (same pattern as IM-list FAB). Pagination text reformatted to iOS-style "第 N / M 頁 · X,XXX 筆". Search hint "搜尋詞彙", pagination buttons "&lt;上頁" / "下頁&gt;".
> - **Mapping record editor (`ManageImFragment` + `fragment_manage_im.xml`)** — Same pagination-bar push + dividers + iOS-format pagination text + button labels.
> - **DB Manager (`fragment_db_manager.xml` + `DbManagerFragment`)** — `MaterialToolbar` removed; large 28sp bold heading "資料庫管理" added at top. Three `MaterialCardView` wrappers removed; each action now rendered as a section-label `TextView` (備份 / 還原 / 初始資料庫) + a single pill `MaterialButton` + caption description below. Buttons use `Widget.MaterialComponents.Button.UnelevatedButton` with `app:cornerRadius="10dp"` + `app:backgroundTint="@color/setup_status_bg"` (note: `?attr/borderlessButtonStyle` cannot show a custom background, so the Unelevated style is required). Colour coding: 備份資料庫 blue text + upload icon (`ic_upload_24`); 還原資料庫 red text + download icon (`ic_download_24`); 還原預設資料庫 red text + refresh icon (`ic_refresh`). Restore caption updated: "還原後鍵盤將重新載入資料庫。".
> - **IM Detail (`fragment_im_detail.xml` + `ImDetailFragment`)** — Toolbar title cleared; large 28sp heading `tv_im_detail_heading` added below the toolbar (matches iOS pattern of inline `.navigationTitle(im.label)` with no toolbar title). Section labels moved OUTSIDE each `MaterialCardView` as small secondary-colour `TextView`s (輸入法資訊 / 軟鍵盤配置 / 字根資料表 / 選項), matching iOS `Section` header pattern. Card row dividers added between 名稱 / 版本 / 筆數. 字根資料表 row gained a leading blue grid icon (`ic_grid_on_24`). 移除輸入法 button switched from `Widget.MaterialComponents.Button.OutlinedButton` to `?attr/borderlessButtonStyle` with `@color/setup_status_fg_red` text, centered, matching iOS's red-text-only destructive style.
> - **IM List → 關聯字庫 routing** — Click on the related row no longer pushes `ManageRelatedFragment` directly. Instead `ImListFragment` constructs a synthetic `ImConfig(id=-1, code="related", desc=getString(im_related_heading))` and pushes `ImDetailFragment`, mirroring iOS's `IMRow(tableNick:"related")` pattern (LIME_SETTINGS.md:343,401). `ImDetailFragment` detects `isRelated = "related".equals(tableCode)` and applies these variations:
>   - Hide `section_keyboard` (no soft keyboard for related)
>   - Hide `section_options` (no `backup_on_delete_related` toggle)
>   - Hide `row_version` + `divider_version` (related has no `mapping_version`)
>   - Retext `tv_section_table_label` → "關聯字庫" (iOS LIME_SETTINGS.md:380)
>   - Retext `tv_manage_table_label` → "瀏覽 / 編輯關聯字庫" (iOS LIME_SETTINGS.md:382)
>   - Route the 字根資料表 row click to `ManageRelatedFragment.newInstance(1)` instead of `ManageImFragment`
>   - Keep 移除輸入法 button visible — user directive 2026-05-14 (overrides iOS spec line 387 "hidden when related"). Rationale: lets users clear the related table to load their own. Handler routes through existing `SearchServer.clearTable("related")`.
>
> New string resources added (`strings_settings.xml`): `im_related_heading=關聯字庫`, `im_detail_section_related=關聯字庫`, `im_detail_manage_related=瀏覽 / 編輯關聯字庫`.
>
> New drawables added: `ic_upload_24.xml` (DB Manager backup), `ic_folder_24.xml` (unused legacy DB Manager backup icon candidate), `ic_grid_on_24.xml` (Material grid, used for 字根資料表 row leading icon), `ic_status_check.xml` / `ic_status_warning.xml` / `ic_status_error.xml` (Setup tab three-state icons), `ic_download_24.xml` (DB Manager restore), `db_pill_bg.xml` (initial attempt at pill background, superseded by `app:backgroundTint` approach — file still on disk, deletable).
>
> New colour resources (`colors.xml`): `setup_status_bg=#1F808080`, `setup_status_fg_green=#2E7D32`, `setup_status_fg_yellow=#EF6C00`, `setup_status_fg_red=#C62828` — shared across Setup banner, About card, DB Manager pills, IM Detail destructive button.
>
> **Session log 2026-05-13:** Batches 1-17 executed. All P1 items DONE. All P2 items DONE except P2.8 (editor rebuild) which got a LIGHT pass — single-column + keyboard-button removal + IM-label title done; SearchView migration / swipe actions / toolbar Add button left as in-source TODOs for a follow-up PR. `gradlew assembleDebug` clean on final pass.

Use this checklist as the work tracker. Tick items as PRs land. Each line points at the
file path or layout block to touch.

### P1 — Missing core features (block parity)

- [x] **§3.4 / P1.1** Split Related Add/Edit sheets into two separate fields. — DONE 2026-05-13.
  - [ ] `sheet_manage_related_add.xml`: add second `TextInputEditText edt_related` below `edt_word`; rename labels (詞彙 / 關聯字).
  - [ ] `sheet_manage_related_edit.xml`: same — and prefill `edt_word` = `related.getPword()`, `edt_related` = `related.getCword()`.
  - [ ] `ManageRelatedAddSheet.java:88-100`: pass `edt_word` and `edt_related` directly to `hostFragment.addRelated(pword, cword, score)`; delete `source.substring(0,1)` split.
  - [ ] `ManageRelatedEditSheet.java:80-115`: same — drop the concat + substring logic.
- [x] **§2.2 / P1.2** ~~Wire Remove-IM~~ — DONE 2026-05-13 (routes through SearchServer.clearTable; backupLearning TODO). Was at `ImDetailFragment.java:210-219` to `manageImController.clearTable(tableCode, switchBackup.isChecked())` then `LIMEPreferenceManager.syncIMActivatedState()` and dismiss. Vary confirm message wording by backup-toggle state per iOS §5.2.
- [x] **§2.2 / P1.3** ~~Add 字根對應設定 Section~~ — DONE 2026-05-13. Was: Add Section to `ImDetailFragment` shown only when `tableCode == "custom"`; two toggles bound to `accept_number_index` / `accept_symbol_index`. Remove them from `preference.xml:388-395`.
- [x] **§2.2 / P1.4** ~~Add 電話鍵盤設定 Section~~ — DONE 2026-05-13. Was: Add Section to `ImDetailFragment` shown only when `tableCode == "array10"`; Picker bound to `auto_commit`. Remove from `preference.xml:180-186`.
- [x] **§2.2 / P1.5** ~~Add 注音鍵盤類型 Section~~ — DONE 2026-05-13. Was: Add Section to `ImDetailFragment` shown only when `tableCode == "phonetic"`; Picker bound to `phonetic_keyboard_type`, 6 options per iOS §5.2.2. On change call `DBServer.setImConfigKeyboard("phonetic", kb)`. Remove from `preference.xml:200-206`.

### P2 — UX divergence (visible mismatch)

- [x] **§0.1 / P2.1** ~~Add MaterialToolbar titles to three tab fragments~~ — DONE 2026-05-13.
  - [ ] `fragment_im_list.xml` — title `管理輸入法`.
  - [ ] `fragment_db_manager.xml` — title `資料庫管理`.
  - [ ] Wrap `LimePreferenceFragment` host in a layout with a `MaterialToolbar` titled `喜好設定`.
- [x] **§5.9 / P2.2** ~~Reverse-Lookup sub-screen — nested PreferenceScreen~~ — DONE 2026-05-13.
- [x] **§5 / P2.3** ~~Preferences re-sectioning into 8 (+1 physical-keyboard) categories~~ — DONE 2026-05-13.
- [x] **§5 / P2.4** ~~Default mismatches (`keyboard_size`/`font_size`/`backup_on_delete`/`smart_chinese_input`/`auto_chinese_symbol`)~~ — DONE 2026-05-14 (all sub-items resolved):
  - [x] `keyboard_size` default → `"1"` (一般) on both iOS and Android — final product decision reverses prior `"1.1"` alignment.
  - [x] `font_size` default → `"1"` (一般) on both platforms — same.
  - [x] `backup_on_delete_{*}` runtime default `true` (`ImDetailFragment.java:169` — bug at old L121 was already fixed).
  - [x] `smart_chinese_input` default → `true` on both platforms (iOS `LIMEPreferenceManager.swift` + Android `preference.xml` defaultValue + `LIMEPreferenceManager.java`).
  - [x] `auto_chinese_symbol` default → `false` on both platforms.
- [x] **§1 / P2.5** ~~App Setup: revert step list / privacy note / inline title~~ — DONE 2026-05-13.
- [x] **§2.2 / P2.6** ~~IM Detail Version row + share toolbar~~ — DONE 2026-05-13 (share is Toast stub pending ShareDialog wiring).
- [x] **§2.1 / P2.7** ~~IM List section headers + syncIMActivatedState on toggle~~ — DONE 2026-05-13.
- [/] **§3 / P2.8** Record + Related editors — LIGHT pass DONE 2026-05-13: single-column LinearLayoutManager + removed `btnManageImKeyboard` + IM-label dynamic title. Outstanding TODOs in source:
  - [ ] `fragment_manage_im.xml` / `fragment_manage_related.xml`: single-column `LinearLayoutManager`; replace `EditText + Search/Reset button` with `SearchView` in the toolbar (live filter); move `btnManage*Add` into the toolbar `app:menu`.
  - [ ] `ManageImFragment.java:170` / `ManageRelatedFragment.java:156`: drop `GridLayoutManager(activity, 2)`.
  - [ ] Add `ItemTouchHelper.SimpleCallback` for swipe-edit / swipe-delete on both adapters.
  - [ ] Remove `btnManageImKeyboard` block (`fragment_manage_im.xml:30-37`, `ManageImFragment.java:186-196`) — keyboard selection lives only on `ImDetailFragment`.
  - [ ] Set `ManageImFragment` toolbar title programmatically from the IM's display name (not fixed `R.string.im_detail_manage_table`).
- [x] **§3.4 / P2.9** ~~Edit-sheet confirms — AlertDialog before Save and Delete~~ — DONE 2026-05-13. — add `AlertDialog` before Save and before Delete in:
  - [ ] `ManageImEditSheet.java:98-103` (delete) and `:105-116` (save).
  - [ ] `ManageRelatedEditSheet.java:96-101` (delete) and `:103-115` (save).
  - [ ] Decide fate of the now-dead `showDeleteConfirmDialog` helpers in both fragments — wire from sheets OR delete.
- [x] **§2.3 / P2.10** ~~IM Install audit~~ — DONE 2026-05-13 (CLOUD_PINYINGB stub variant + TODOs for URL/seedCustomIM/progress verification).
  - [ ] Add `CLOUD_PINYINGB` variant to the pinyin family (`ImInstallFragment.java:442-445`).
  - [ ] Verify `seedCustomIM()` is invoked after a custom-IM import.
  - [ ] Confirm `ProgressManager` is wired through `SetupImController`/`ManageImController` import + download paths.
  - [ ] Audit each `cloudVariants` list against iOS §5.3 (titles + cloud constants).
- [x] **§4 / P2.11** ~~DB Manager status footer + progress wiring~~ — DONE 2026-05-13.
  - [ ] Wire `dbStatusText` updates (status footer is `visibility=gone` and never toggled — `fragment_db_manager.xml:121-143`).
  - [ ] Confirm `performBackup` / `performRestore` post progress through `ProgressManager`.
  - [ ] Align restore-section footer copy with iOS spec wording (`fragment_db_manager.xml:83`).
- [x] **§5.1 / P2.12** ~~Add new preference key `display_number_keypads`~~ — **REVERTED 2026-05-14**. Initially added to Android `preference.xml` 2026-05-13, then fully removed (iOS spec, iOS Swift accessor, Android `preference.xml`, Android Java accessor, Android strings) as a confirmed ghost pref with no consumers on either platform.

### P3 — Cosmetic / parity polish

- [ ] Brand block layout decision: keep current horizontal logo + wordmark or stack vertically per iOS (`fragment_setup.xml:17-38`).
- [ ] App Setup CTA presentation: keep two-button conditional or move to single state-aware CTA. Product decision.
- [ ] Decide: dedicated related-phrase detail page vs direct push (`ImListFragment.java:236-242`).
- [ ] Align IM Install display titles to iOS spec (`ImInstallFragment.java:377, 386, 418, 431, 438`).
- [ ] IM Install group iconography review (`ImInstallFragment.java:352, 364, 449-454`).
- [ ] Keyboard picker checkmark for selected item (`ImDetailFragment.java:177-208`).
- [ ] (covered by P2.8) Search-bar style migration to `Toolbar` `SearchView`.
- [ ] (covered by P2.8) Grid vs single-column record layout.
- [ ] Verify resource arrays match spec value lists:
  - [x] `@array/keyboard_themes_values` has 7 entries (0–5 plus 6=system follow).
  - [ ] `@array/im_reverse_lookup_codes` and `@array/im_reverse_lookup` each have 14 entries (`none` + 13 IMs).
  - [ ] `@array/han_convert_options_values` has 3 entries (0–2).
- [ ] Status footer copy alignment (DB Manager + IM Install).
- [ ] Product decision: keep or retire Android-only physical-keyboard prefs (`hide_software_keyboard_typing_with_physical`, `switch_english_mode*`, `disable_physical_selkey`, `selkey_option`, `english_dictionary_physical_keyboard`, `physical_keyboard_sort`). `physical_keyboard_type` is retired from visible Preferences.
- [ ] Initial score on `ManageImAddSheet.java:52` change `1` -> `0` to match iOS spec §6.1.1.
- [ ] Replace `Toast(R.string.insert_error)` / `Toast(R.string.update_error)` with inline `TextInputLayout` `setError(...)` in all four sheets.
- [ ] Verify pagination / search-filter / confirm-alert behaviour in `ManageImFragment` and `ManageRelatedFragment` bodies beyond line 200 (not sampled in this pass).
- [ ] Decide: keep Android-only score field on Related Add/Edit sheets, or remove for spec parity.
- [ ] Decide: align `R.string.im_related_label` (現「關聯字庫」) with iOS title `關聯字管理`, or keep current copy.
- [ ] Unify pagination label to "第 N 頁 / 共 M 筆" (use existing `R.string.manage_im_navigation_info` / `R.string.manage_related_navigation_info`).

### Test edits (paired with P1/P2 work above)

> See §11 for the full audit. These checkboxes mirror the actionable items.

Paired with P1.1 (Related Add/Edit sheet split):
- [x] `ManageRelatedAddDialogTest.java` — FQN updated to `ManageRelatedAddSheet`. DONE 2026-05-13.
- [x] `ManageRelatedEditDialogTest.java` — FQN updated to `ManageRelatedEditSheet`. DONE 2026-05-13.

Paired with P2.8 (editor rebuild):
- [x] `ManageImAddDialogTest.java` — FQN updated to `ManageImAddSheet`. DONE 2026-05-13.
- [x] `ManageImEditDialogTest.java` — FQN updated to `ManageImEditSheet`. DONE 2026-05-13.
- [ ] `ManageImKeyboardDialogTest.java:18-23` — if P2.8 removes `btnManageImKeyboard` AND retires the dialog class, DELETE the test file. If the class survives elsewhere, UPDATE the FQN only.

Paired with P1.5 (move `phonetic_keyboard_type` out of `PrefsFragment`):
- [ ] `LIMEPreferenceTest.java:79-94` `testPhoneticKeyboardTypeChangeDoesNotCrash` — retarget to `ImDetailFragment`'s controller, OR relax assertion to "no crash regardless of key registration", OR DELETE if the change-listener branch is fully removed from `PrefsFragment`.
- [ ] `LIMEPreferenceTest.java:113-138` `testPhoneticKeyboardMappingBranches` — same options as above.

Verify-only (no edits expected; re-run after each PR lands):
- [ ] `MainActivityTest.java` — should stay green after P2.1 toolbar titles land.
- [ ] `NavigationDrawerFragmentTest.java`, `NavigationManagerTest.java` — verify after P2.1 / P2.3.
- [ ] `SetupImControllerFlowsTest.java` — verify after P1.2 Remove-IM wiring lands.
- [ ] `ManageImAdapterTest.java`, `ManageRelatedAdapterTest.java` — verify after P2.8 editor rebuild.
- [ ] `SetupImFragmentTest.java` — already confirmed unaffected by Setup-tab revert (P2.5 done).
- [ ] `LIMEServiceTest.java`, `SearchServerTest.java` — verify `auto_commit` / `phonetic_keyboard_type` SharedPreferences-key references still hold after P1.4 / P1.5.
### Verification pass (before closing P1/P2)

- [ ] `@array/keyboard_themes_values`, `@array/im_reverse_lookup_codes`, `@array/im_reverse_lookup`, `@array/han_convert_options_values` audited (P3 above).
- [ ] `searchword` semantics in `ManageImFragment.java:311-323` — confirm 字根 mode is prefix on `code`, 文字 mode is contains on `word`.
- [ ] `searchRelated` semantics in `ManageRelatedFragment.java:238-250` — confirm contains-match on `word` column.
- [ ] `auto_cap` — grep the entire `LimeStudio/app/src/main` for stale references; remove if unused.
- [ ] Build green: clean assembleDebug + install to Pixel_9_Pro emulator; smoke-test Setup tab (red/yellow/green), IM list toggle, DB backup roundtrip.

---

## 11. Test Modifications Required

> Audit of `LimeStudio/app/src/androidTest/java/org/limeime/*Test.java` against the §10 backport plan. The Android instrumentation suite is overwhelmingly reflection-based (class existence + method-name presence) rather than view-level Espresso, so most UI-layer back-ports are invisible to it. Below lists every test that does touch a symbol the plan removes/renames.

### Tests already broken by completed work (Setup tab revert)

None confirmed. Grep for `setup_step_enable_keyboard`, `setup_step_switch_keyboard`, `setup_im_privacy_note`, `setupHeading`, `setupStep1Description`, `setupStep2Description` across all `*Test.java` returned zero matches. `SetupImFragmentTest` only checks controller-API and `refreshButtonState` method existence by reflection (`SetupImFragmentTest.java:96-106`) — no view-id assertions. **No action required.**

### Tests blocked by P1 work

| Test file | Method(s) | Reason | Action |
|---|---|---|---|
| `ManageRelatedAddDialogTest.java` | `testManageRelatedAddDialogClassExists` (L17-25), `testValidationAndControllerAddRelatedApis` (L27-44) | Looks up `net.toload.main.hd.ui.dialog.ManageRelatedAddDialog` by reflection (L20, L29). The plan renames Add/Edit dialogs to `ManageRelatedAddSheet`/`ManageRelatedEditSheet` and splits `edt_word` into `edt_word`+`edt_related`. Class name change will make `Class.forName` fail. | UPDATE — change FQN to `…ui.dialog.ManageRelatedAddSheet`, keep method-presence assertions. |
| `ManageRelatedEditDialogTest.java` | `testManageRelatedEditDialogClassExists` (L17-25), `testValidationAndControllerUpdateRelatedApis` (L27-44) | Same reflective lookup for `ManageRelatedEditDialog` (L20, L29). | UPDATE — rename to `ManageRelatedEditSheet`. |
| `ManageImAddDialogTest.java` | `testManageImAddDialogClassExists` (L18), `testValidationAndControllerAddImApis` (L27-35) | Reflective lookup of `ManageImAddDialog` (L20, L29). Editor rebuild renames to `ManageImAddSheet`. | UPDATE — fix FQN. |
| `ManageImEditDialogTest.java` | `testManageImEditDialogClassExists` (L18), `testValidationAndControllerUpdateImApis` (L27-35) | Reflective lookup of `ManageImEditDialog` (L20, L29). Plan adds AlertDialog confirms before Save/Delete; class FQN may shift to `ManageImEditSheet`. | UPDATE — fix FQN; existing validate-method check still holds. |
| `ManageImKeyboardDialogTest.java` | `testManageImKeyboardDialogClassExists` (L18-23) | Reflective lookup of `ManageImKeyboardDialog` (L20). P2 editor rebuild **removes `btnManageImKeyboard`**, so the keyboard-assignment dialog is no longer launched from the editor. | DELETE if the dialog class itself is removed; otherwise UPDATE FQN. Verify against final P2 design. |
| `ManageImFragmentTest.java` | `testRecordManagementDelegatesToController` (L118-135), `testIMKeyboardLoadingUsesController` (L66-82) | Reflection-only — checks `addRecord/updateRecord/deleteRecord/getKeyboardList/getImConfigFullNameList` on `ManageImController` (L77-79, L127-132). Controller API is unchanged by the plan; only the editor view is rebuilt. | No action — passes after rebuild. |

### Tests blocked by P2 work

| Test file | Method(s) | Reason | Action |
|---|---|---|---|
| `LIMEPreferenceTest.java` | `testPhoneticKeyboardTypeChangeDoesNotCrash` (L79-94), `testPhoneticKeyboardMappingBranches` (L113-138) | Calls `onSharedPreferenceChanged(prefs, "phonetic_keyboard_type")` (L88, L126, L132). P1 plan moves `phonetic_keyboard_type` **out of `preference.xml`** into a conditional Section in `ImDetailFragment`. The pref key may still exist in SharedPreferences but `PrefsFragment.onSharedPreferenceChanged` will no longer route it. | UPDATE — either retarget the test to `ImDetailFragment`'s controller, or relax the assertion to "callback does not crash regardless of key registration." DELETE if the change-listener branch is fully removed from PrefsFragment. |
| `LIMEPreferenceTest.java` | `testPrefsFragmentAttachedWithSearchServerInitialized` (L43-60), `testOnSharedPreferenceChangedCallsBackupManager` (L62-77), `testPreferenceChangeListenerLifecycleSafe` (L96-111) | These don't touch flipped defaults; they only verify fragment lifecycle + `SearchSrv` field presence. Pref re-sectioning will not break them unless `PrefsFragment` is split. | No action expected — verify after re-sectioning lands. |

### Tests at risk (verify before merging)

| Test file | Concern |
|---|---|
| `MainActivityTest.java` | Only checks `NavigationDrawerCallbacks` interface placement (L129-178). Adding `MaterialToolbar` titles to `fragment_im_list.xml`/`fragment_db_manager.xml` is view-only; no assertions hit. Expected to remain green. |
| `NavigationDrawerFragmentTest.java`, `NavigationManagerTest.java` | Not sampled in detail, but no grep hits for `toolbar`, `setTitle`, `setSubtitle`, `fragment_im_list`, `fragment_db_manager`. Likely unaffected — verify briefly. |
| `SetupImControllerFlowsTest.java` | Pure controller-API reflection (`performBackup/performRestore/exportZippedDb`, etc.). Wiring `manageImController.clearTable(...)` into `ImDetailFragment` Remove-IM does not change controller API surface. No action. |
| `ManageImAdapterTest.java`, `ManageRelatedAdapterTest.java` | Adapters are unlikely to be touched by the editor rebuild. Grep for `edt_word`/`btnManageImKeyboard` in androidTest returned **zero hits**, so adapter tests do not reference removed view ids. Verify after editor rebuild. |
| `SetupImFragmentTest.java` | Reflection-only; no view-id usage. The Setup tab revert (P1 done) does not break it — confirmed by zero hits on the new/old TextView ids. |
| `LIMEServiceTest.java` / `SearchServerTest.java` | Reference `auto_commit` (LIMEServiceTest L4048-4058) and `phonetic_keyboard_type` (SearchServerTest L86, L645) via reflection on the **service** field / SharedPreferences string, not via `PrefsFragment`. Moving these prefs out of `preference.xml` into `ImDetailFragment` does not change the SharedPreferences key, so service-side tests stay green. |

### Summary

- **Hard breaks (UPDATE):** 4–5 dialog tests (`ManageRelatedAdd/EditDialogTest`, `ManageImAdd/EditDialogTest`, possibly `ManageImKeyboardDialogTest`) — all are 1-line FQN renames once Add/Edit dialogs become `*Sheet` classes.
- **Behavior breaks (UPDATE or DELETE):** 2 methods in `LIMEPreferenceTest` referencing `phonetic_keyboard_type` change-listener routing.
- **Likely DELETE:** `ManageImKeyboardDialogTest` entirely, if `btnManageImKeyboard` removal also removes the dialog class.
- **No action:** `SetupImFragmentTest`, `MainActivityTest`, `SetupImControllerFlowsTest`, `ManageImFragmentTest`, and all model-layer tests (`DBServerTest`, `LimeDBTest`, `SearchServerTest`, `LIMEServiceTest`, `IntegrationTest*`, `RegressionTest`, `PerformanceTest`).
- **Total estimated test-edit cost:** ~6 files, ~10 line edits.
