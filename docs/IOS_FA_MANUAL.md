# iOS Full Access Manual Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update the user manuals for the new iOS Full Access design so users understand that Full Access is optional for typing, but required for iOS backup, key haptics, and table editing.

**Architecture:** Keep one manual structure under `docs/manuals/`; do not add a new Full Access page. Fix the stale manual guardrails first, then update the affected user pages with the same three-unlock model from `IOS_FULL_ACCESS.md`, `IOS_DB_COLD_HOT.md`, and `LIME_SETTINGS.md`.

**Tech Stack:** Markdown manuals in `docs/manuals/*.md`, existing manual review docs in `docs/*.md`, existing screenshots under `docs/assets/`.

## Global Constraints

- Full Access is a feature unlock, never a requirement for basic typing.
- FA-off must be described as still supporting typing, IM install/import/delete, restore preparation, learning, and learned-record preservation.
- FA-on unlocks exactly these user-visible features: in-app backup, key haptic feedback, and table editing.
- Backup requires Full Access confirmed ON and LIME active as the current keyboard.
- Restore and restore-default remain enabled regardless of Full Access or active-keyboard state, but the keyboard applies the restored data when it next runs and syncs.
- Table editor and related editor stay read-only unless Full Access is confirmed ON and LIME is active.
- Use Traditional Chinese for user-facing manual copy.
- Keep all manual pages in `docs/manuals/`; do not create subfolders or a thin dedicated FA page.
- Reuse existing screenshots. Add new screenshots only if the UI shown in existing assets no longer matches the implemented screens.

---

## Source Facts To Carry Into The Manuals

Use this wording model everywhere, adjusted to the local page:

> iPhone/iPad 的「允許完整取用」不是正常打字的必要條件。不開啟也能輸入、安裝或匯入輸入法、還原資料庫並保留學習流程。開啟後會解鎖三個功能：備份已學習字詞、按鍵震動回饋，以及編輯字根資料表。

Backup-specific wording:

> 在 iPhone/iPad 上，「備份資料庫」需要先開啟「允許完整取用」，並把目前鍵盤切換為萊姆輸入法。這樣鍵盤才能把最新的學習記錄打包給 App。還原資料庫不需要先開啟完整取用。

Editor-specific wording:

> 若未開啟完整取用，或目前鍵盤尚未切換為萊姆輸入法，資料表仍可瀏覽與搜尋，但新增、編輯、刪除會先維持唯讀狀態。

---

## Task 1: Fix Manual Review Guardrails

**Files:**
- Modify: `docs/MANUAL_SOURCE_COVERAGE.md`
- Modify: `docs/USER_MANUAL_PLAN.md`
- Modify: `docs/MANUAL_REVIEW_WORKFLOW.md`
- Modify: `docs/SOURCE_ACCURACY_AUDITOR.md`
- Modify: `docs/PRIVACY_PLATFORM_LIMIT_AUDITOR.md`
- Modify: `docs/MANUAL_VISUAL_DESIGNER.md`

**Purpose:** These docs currently reject any manual text that ties iOS Full Access to backup or database behavior. That is now wrong.

- [ ] Replace every hard rule saying Full Access is only for haptics with the three-unlock model: backup, key haptics, table editing.
- [ ] Keep the rejection rule that Full Access must never be described as required for basic typing, IM install/import/delete, restore preparation, or normal app use.
- [ ] In `docs/MANUAL_SOURCE_COVERAGE.md`, update the sensitive-claims row for iPhone/iPad Full Access and mark these manual owners: `quick-start.md`, `database-management.md`, `ime-management.md`, `preferences.md`, `faq.md`, `troubleshooting.md`, `privacy.md`.
- [ ] In `docs/USER_MANUAL_PLAN.md`, update the hard rules and rejection list so DB Manager backup and table editing are allowed Full Access unlocks, while restore and typing remain FA-independent.
- [ ] In `docs/MANUAL_REVIEW_WORKFLOW.md`, update Source Accuracy and Privacy auditor checks to match the new model.

**Check:**

```bash
rg -n "僅用於啟用按鍵震動|只用於 LIME 的按鍵震動|anything other than the permission needed for LIME key vibration|tied to database, backup/restore" docs/MANUAL_SOURCE_COVERAGE.md docs/USER_MANUAL_PLAN.md docs/MANUAL_REVIEW_WORKFLOW.md docs/SOURCE_ACCURACY_AUDITOR.md docs/PRIVACY_PLATFORM_LIMIT_AUDITOR.md docs/MANUAL_VISUAL_DESIGNER.md
```

Expected: no stale haptics-only rejection remains. Any remaining Full Access warning must allow backup and table editing, while still rejecting basic-typing requirements.

## Task 2: Update Quick Start And Setup Tab Copy

**Files:**
- Modify: `docs/manuals/index.md`
- Modify: `docs/manuals/quick-start.md`

**Purpose:** The setup manual must match the new Setup tab: Section 1 enables the keyboard and optional Full Access, Section 2 asks the user to switch to LIME, Section 3 routes missing IM tables.

- [ ] In `docs/manuals/index.md`, change the iOS setup screenshot caption so green means the relevant setup status is complete, not merely "keyboard enabled."
- [ ] In `docs/manuals/quick-start.md`, change iPhone/iPad activation from three steps to this flow:
  1. Tap `前往設定`.
  2. Open `鍵盤` and enable `萊姆輸入法`.
  3. Optionally enable `允許完整取用` for backup, key haptics, and table editing.
  4. Return to the app and use `選用萊姆輸入法` / globe key to make LIME current.
  5. Install or enable at least one IM table from the `輸入法` tab.
- [ ] Replace the old orange-banner explanation with: orange means the keyboard is enabled but Full Access is not on; typing still works, but backup, haptics, and table editing stay locked.
- [ ] Keep the installed-IM status explanation, because it is still the right first-run path after keyboard setup.
- [ ] In the migration section, add the iOS backup precondition: old iPhone/iPad backups need Full Access ON and LIME active, while restore on the new device can be started from the `資料庫` tab without first turning FA on.

**Check:**

```bash
rg -n "僅用於啟用按鍵震動|不需要震動|綠色橫幅代表萊姆輸入法已啟用" docs/manuals/index.md docs/manuals/quick-start.md
```

Expected: no old haptics-only quick-start claim remains.

## Task 3: Update Table Editor Manual Copy

**Files:**
- Modify: `docs/manuals/ime-management.md`
- Modify: `docs/manuals/advanced.md`

**Purpose:** Users need to know why table rows may be read-only and how to unlock editing.

- [ ] In `docs/manuals/ime-management.md`, under `編輯輸入法與字根`, add a short status note before the add/edit instructions:
  - FA ON + LIME active: add/edit/delete are enabled.
  - FA off or LIME not active: browse/search still work, but editing is read-only.
- [ ] Mention the status line users will see: `完整取用已開啟，碼表編輯功能已啓用。` when unlocked, and guidance text when locked.
- [ ] In `關聯字庫`, say the related-phrase editor follows the same read-only/unlocked behavior as the mapping table editor.
- [ ] In `docs/manuals/advanced.md`, update the `逐筆編輯` note so advanced users know iOS editing requires Full Access and active LIME, but importing custom tables does not.

**Check:**

```bash
rg -n "唯讀|完整取用|目前鍵盤|碼表編輯" docs/manuals/ime-management.md docs/manuals/advanced.md
```

Expected: both mapping-table and related-phrase paths explain the read-only state.

## Task 4: Update DB Manager Backup And Restore Copy

**Files:**
- Modify: `docs/manuals/database-management.md`
- Modify: `docs/manuals/quick-start.md`
- Modify: `docs/manuals/faq.md`
- Modify: `docs/manuals/troubleshooting.md`

**Purpose:** Backup now has an iOS Full Access and active-keyboard precondition. Restore does not.

- [ ] In `docs/manuals/database-management.md`, add an iPhone/iPad note before backup steps:
  - `備份資料庫` is disabled until Full Access is confirmed ON and LIME is the current keyboard.
  - If Full Access is missing, footer copy points users to enable it.
  - If LIME is not current, footer copy asks users to switch to LIME.
- [ ] Keep restore steps enabled and separate: restore replaces the app-side database and can be started FA-off.
- [ ] Clarify restore timing: after restore, open or switch to LIME so the keyboard reloads the restored database.
- [ ] Keep progress wording: `處理中…`, `備份中… N%`, and `準備備份中…`.
- [ ] In `docs/manuals/faq.md`, update the migration answer so iOS backup requires Full Access ON and LIME active on the old device.
- [ ] In `docs/manuals/troubleshooting.md`, add backup-disabled cases: Full Access not confirmed, LIME not active, large backup still preparing share sheet.

**Check:**

```bash
rg -n "備份資料庫|還原資料庫|完整取用|萊姆輸入法|準備備份中" docs/manuals/database-management.md docs/manuals/quick-start.md docs/manuals/faq.md docs/manuals/troubleshooting.md
```

Expected: backup and restore have different permission rules.

## Task 5: Update Preferences, FAQ, Troubleshooting, And Privacy

**Files:**
- Modify: `docs/manuals/preferences.md`
- Modify: `docs/manuals/faq.md`
- Modify: `docs/manuals/troubleshooting.md`
- Modify: `docs/manuals/privacy.md`

**Purpose:** Remove the stale haptics-only Full Access explanation from support and privacy pages.

- [ ] In `docs/manuals/preferences.md`, keep `打字震動` tied to Full Access, but avoid implying it is the only Full Access use in the app.
- [ ] In `docs/manuals/faq.md`, replace `iPhone 的「允許完整取用」一定要開嗎？` with the three-unlock answer and the FA-off reassurance.
- [ ] In `docs/manuals/faq.md`, update `Android 與 iOS 的設定一樣嗎？` so iOS differences include Full Access-gated backup/table editing/haptics and no keyboard-internal voice input.
- [ ] In `docs/manuals/troubleshooting.md`, change the orange-banner entry to say typing still works, but backup, haptics, and table editing need Full Access.
- [ ] In `docs/manuals/privacy.md`, replace `用途單一` with a privacy-safe explanation: Full Access is used locally for backup export, key haptics, and table-edit refresh; LIME still does not upload typed text, learning data, or personal dictionaries.

**Check:**

```bash
rg -n "用途單一|僅用於啟用按鍵震動|不需要震動，可以不開啟" docs/manuals/preferences.md docs/manuals/faq.md docs/manuals/troubleshooting.md docs/manuals/privacy.md
```

Expected: no public manual page says Full Access is haptics-only.

## Task 6: Review Screenshots And Coverage

**Files:**
- Read: `docs/LIME_SETTINGS.md`
- Read: `docs/assets/lime_settings_ios_setup.png`
- Read: `docs/assets/lime_settings_ios_database.png`
- Read: `docs/assets/lime_settings_ios_record_list.png`
- Modify only if needed: affected manual pages from Tasks 2-5

**Purpose:** Existing screenshot coverage is probably enough, but captions must not contradict the new UI states.

- [ ] Check setup, database, and record-list screenshots against the current UI.
- [ ] If screenshots are current, update only captions and nearby text.
- [ ] If screenshots are stale, capture replacement images using the existing screenshot workflow and place them under the existing assets area, not a new root folder.
- [ ] Do not add a screenshot just to explain Full Access permission internals.

**Check:**

```bash
rg -n "lime_settings_ios_setup|lime_settings_ios_database|lime_settings_ios_record_list|lime_settings_ios_related_list" docs/manuals
```

Expected: the affected manual pages still reference the relevant existing screenshots or deliberately document why a screenshot refresh is needed.

## Task 7: Final Manual Verification

**Files:**
- Read: all modified docs from Tasks 1-6

**Purpose:** Catch stale claims and broken links before calling the manual plan complete.

- [ ] Run stale Full Access claim scan:

```bash
rg -n "僅用於啟用按鍵震動|用途單一|只用於 LIME 的按鍵震動|Full Access.*only|完整取用.*只" docs/manuals docs/MANUAL_SOURCE_COVERAGE.md docs/USER_MANUAL_PLAN.md docs/MANUAL_REVIEW_WORKFLOW.md docs/SOURCE_ACCURACY_AUDITOR.md docs/PRIVACY_PLATFORM_LIMIT_AUDITOR.md docs/MANUAL_VISUAL_DESIGNER.md
```

- [ ] Run manual link check:

```bash
python3 - <<'PY'
from pathlib import Path
import re
broken = []
for path in Path("docs/manuals").glob("*.md"):
    text = path.read_text(encoding="utf-8-sig")
    for target in re.findall(r"\[[^\]]+\]\(([^)#]+\.md)(?:#[^)]+)?\)", text):
        if re.match(r"^(https?://|mailto:)", target):
            continue
        if not (path.parent / target).exists():
            broken.append(f"{path}: {target}")
print(f"broken_md_links={len(broken)}")
print("\n".join(broken))
PY
```

- [ ] Run short-page check:

```bash
for f in docs/manuals/*.md; do lines=$(wc -l < "$f"); [ "$lines" -lt 30 ] && echo "$lines $f"; done
```

- [ ] Run source coverage spot-check:

```bash
rg -n "Full Access|完整取用|備份|碼表編輯|唯讀|還原資料庫" docs/manuals docs/MANUAL_SOURCE_COVERAGE.md
```

Expected: wording is consistent across Setup, Table Editor, DB Manager, FAQ, Troubleshooting, Preferences, and Privacy.

## Do Not Do

- Do not create `docs/manuals/full-access.md`; this topic is not large enough for its own page.
- Do not describe App Group, hot DB, cold DB, Darwin notifications, heartbeat files, or relay payloads in user manuals.
- Do not tell users Full Access is required for typing, installing input methods, importing code tables, restoring the app-side database, or normal learning.
- Do not change source code for this manual update.
- Do not commit unless explicitly asked.
