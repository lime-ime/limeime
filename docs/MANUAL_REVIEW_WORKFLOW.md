# Manual Review Workflow

This document defines the required review workflow for `docs/manuals/**/*.md`.
No manual page may be considered complete based only on the writer's self-review.
Every page must pass the auditor roles below.

## Review Order

Run the auditors in this order:

1. Source Accuracy Auditor
2. User Task Flow Auditor
3. Formal Traditional Chinese Auditor
4. Visual Designer
5. Manual Structure and Link Auditor
6. Web Layout and Readability Auditor
7. Screenshot and Media Auditor
8. Version, Privacy, and Platform Limit Auditor

If any auditor rejects the page, return the page to drafting. After revision, rerun every affected auditor. Do not treat a one-line patch as a valid pass when the failure affects source accuracy, user trust, task completion, screenshot coverage, or Chinese writing quality.

Use [MANUAL_SOURCE_COVERAGE.md](MANUAL_SOURCE_COVERAGE.md) as the coverage checklist for source-to-manual mapping. Update that file whenever a source requirement moves to another manual page, a new source doc adds user-facing behavior, or a screenshot requirement changes.

## 1. Source Accuracy Auditor

Dedicated standard: [SOURCE_ACCURACY_AUDITOR.md](SOURCE_ACCURACY_AUDITOR.md).

### Goal

Confirm that manual content comes from real specifications, screenshots, or observed product behavior, not inference or generic product writing.

### Required Sources

Read the topic-relevant design/spec/reference docs under `docs/`, including:

- `docs/LIME_SETTINGS.md`
- `docs/KEYBOARD_THEME.md`
- `docs/ANDROID_IPHONE_KEYBOARD.md`
- `docs/KEYBOARD_TYPE.md`
- `docs/ANDROID_VOICE_INPUT.md`
- `docs/IPAD_KEYBOARD.md`
- `docs/IPAD_KB_SIZE_TIERS.md`
- relevant issue notes, such as `docs/#88_ISSUE.md`

### Reject If

- The page uses invented UI names or changes source terminology.
- The `喜好設定` tab is described as generic settings instead of IM Preferences.
- The `資料庫` tab or DB Manager flow is missing.
- DB Manager does not cover `備份資料庫`, `還原資料庫`, and `還原預設資料庫`.
- iPhone `允許完整取用` is described as required for basic input, IM install/import/delete, restore preparation, or normal app use.
- iPhone `允許完整取用` is described as an internal App Group, cross-process, or generic database-access requirement instead of the user-facing unlock for in-app backup, key vibration feedback, and table editing.
- iPad 13-inch, 11-inch, or mini size tiers are described as implemented functionality.
- A Settings App screen is described without using the relevant `assets/lime_settings_*` screenshot when one exists in `docs/LIME_SETTINGS.md`.

## 2. User Task Flow Auditor

Dedicated standard: [USER_TASK_FLOW_AUDITOR.md](USER_TASK_FLOW_AUDITOR.md).

### Goal

Confirm that each page starts from a real user task, not from an internal feature list.

### Review Questions

- After the first screenful, does the user know what to do next?
- Does the page route new users, device-migration users, and users with existing code tables clearly?
- Does the page describe the expected success state?
- Does the page give a concrete next step for common failure states?
- Does the page avoid author-perspective wording such as `本頁只處理`?

### Reject If

- The first paragraph is a generic product introduction with no action path.
- The page only lists functions and does not form a task flow.
- The page says to "confirm" something without naming the screen, tab, visible state, or expected result.
- The quick-start path does not prioritize restoring a backup from an old device already running LIME before rebuilding from scratch.

## 3. Formal Traditional Chinese Auditor

Use [CHINESE_FORMAL_WRITING_AUDITOR.md](CHINESE_FORMAL_WRITING_AUDITOR.md).

This auditor owns tone, polished Traditional Chinese, Taiwan usage, terminology consistency, removal of empty prose, avoidance of translationese, and removal of internal engineering language.

Reject pages that sound like AI summaries, rough notes, generic product introductions, or mechanically translated documentation.

## 4. Visual Designer

Use [MANUAL_VISUAL_DESIGNER.md](MANUAL_VISUAL_DESIGNER.md).

This role owns the web presentation of Markdown pages, including CSS components, first-screen task routing, screenshot pairs, notes, warnings, and mobile readability.

The Visual Designer must confirm:

- The first screen gives a clear task entry point.
- Important screenshots use `.manual-screenshot-pair` or an equivalent readable layout.
- High-risk messages use `.manual-warning`.
- Permission, platform difference, and data-overwrite notes use `.manual-note` or `.manual-warning`.
- The page is not a plain text wall.
- The page is not over-HTMLed in a way that makes Markdown maintenance fragile.

## 5. Manual Structure and Link Auditor

Dedicated standard: [MANUAL_STRUCTURE_LINK_AUDITOR.md](MANUAL_STRUCTURE_LINK_AUDITOR.md).

### Goal

Confirm that the manual structure is coherent, pages are not fragmented, and all links resolve.

### Checks

- All manual pages live under `/docs/manuals/`.
- Paths use ASCII English only; no Chinese file or folder names.
- Standalone pages under 30 lines must be merged into a parent page unless they are necessary index pages.
- Small related topics must be consolidated. For example, thin pages for English input, Chinese input, and symbol input should become `docs/manuals/keyboard-input.md`.
- Every Markdown link points to an existing file.
- No link points to a deleted or renamed page.
- `README.md`, `docs/USER_MANUAL_PLAN.md`, and internal manual links agree with the final structure.

### Suggested Verification

```powershell
$files = Get-ChildItem -Recurse -File docs/manuals -Filter *.md
$broken=@()
foreach ($file in $files) {
  $text = Get-Content -Path $file.FullName -Raw
  $matches = [regex]::Matches($text, '\[[^\]]+\]\(([^)#]+\.md)(?:#[^)]+)?\)')
  foreach ($m in $matches) {
    $target = $m.Groups[1].Value
    if ($target -match '^(https?://|mailto:)') { continue }
    $resolved = Join-Path $file.DirectoryName $target
    if (-not (Test-Path $resolved)) { $broken += "$($file.FullName): $target" }
  }
}
"broken_md_links=$($broken.Count)"
$broken
```

## 6. Web Layout and Readability Auditor

Dedicated standard: [WEB_LAYOUT_READABILITY_AUDITOR.md](WEB_LAYOUT_READABILITY_AUDITOR.md).

### Goal

Confirm that the rendered web manual is readable, not merely valid Markdown.

### Checks

- Heading hierarchy is correct; each page has only one `#`.
- Sections are not too short or too fragmented.
- Tables do not create poor mobile reading. Convert wide tables into grouped lists when needed.
- Screenshots have meaningful alt text.
- Step sequences use numbered lists rather than long paragraphs.
- Warnings are short, visible, and not buried inside ordinary prose.
- Screenshot, step, and success-state order is logical.

### Reject If

- The page requires heavy horizontal scrolling on mobile.
- A paragraph exceeds five lines and contains multiple operations.
- A table is used only for visual layout when a list would read better.
- An image has no alt text, or the alt text does not describe the screen's purpose.

## 7. Screenshot and Media Auditor

Dedicated standard: [SCREENSHOT_MEDIA_AUDITOR.md](SCREENSHOT_MEDIA_AUDITOR.md).

### Goal

Confirm that screenshots are complete, accurate, readable, and tied to real manual sections.

### Mandatory Coverage

Every screenshot documented in `docs/LIME_SETTINGS.md` and `docs/KEYBOARD_THEME.md` must be embedded somewhere under `/docs/manuals/`.

Each embedded screenshot must have a corresponding section that explains:

- what screen the screenshot shows
- what user task it supports
- which setting, tab, keyboard state, or behavior the user should notice

Screenshots may not be orphaned, skipped, dumped into galleries, or shown without surrounding manual text.

### Review Order

1. Use existing screenshots from `docs/LIME_SETTINGS.md` first: App Setup, IM Manager, IM Preferences, DB Manager, record tables, download/import, backup/restore, and default restore.
2. Use existing screenshots from `docs/KEYBOARD_THEME.md` next: Zhuyin keyboard, English keyboard with the `中` key, Emoji panel, and all documented themes.
3. Add new screenshots under `assets/screenshots/` only when existing screenshots are insufficient.

### Reject If

- Any screenshot documented in `docs/LIME_SETTINGS.md` or `docs/KEYBOARD_THEME.md` is missing from the manual.
- A Settings App screen is described without the relevant `assets/lime_settings_*` screenshot.
- A screenshot is embedded without a matching heading and explanation.
- Screenshot alt text is vague, generic, or only repeats the filename.
- The iOS keyboard screenshot only shows Apple's system Zhuyin keyboard and lacks a LIME candidate-bar signal.
- The English keyboard screenshot does not show the `中` key.
- The Emoji panel screenshot does not show the search field, emoji grid, and bottom category row when those are part of the documented behavior.

## 8. Version, Privacy, and Platform Limit Auditor

Dedicated standard: [PRIVACY_PLATFORM_LIMIT_AUDITOR.md](PRIVACY_PLATFORM_LIMIT_AUDITOR.md).

### Goal

Prevent the manual from making inaccurate promises, privacy claims, or platform claims.

### Checks

- Android, iOS, and iPad differences are explicit.
- iOS `允許完整取用` is described as optional for typing and setup, and as the unlock for in-app backup, key vibration feedback, and table editing.
- Android voice input clearly distinguishes LIME inline dictation, Google/system voice-capable IME, and `RecognizerIntent` fallback.
- Android 13+ notification and vibration restrictions are described accurately when relevant.
- Legacy backup restore guidance does not promise guaranteed success.
- iPad size tiers are only described as not implemented or future planning.

## Suggested Additional Roles

These roles are optional but recommended for later passes:

- Beginner User Proxy: acts as someone who has never used LIME and checks whether the page can be followed without prior knowledge.
- Device Migration User Proxy: focuses only on backup, restore, legacy data, and data-overwrite warnings.
- Issue Reporting Auditor: checks whether each failure path gives enough information for a user to report a useful issue.
- Release Note Sync Auditor: checks whether manual version, download, compatibility, and known-issue content stays aligned with README and release notes.

## Definition of Done

A manual page is complete only when all of the following are true:

- Source Accuracy Auditor passes.
- User Task Flow Auditor passes.
- Formal Traditional Chinese Auditor passes.
- Visual Designer passes.
- Manual Structure and Link Auditor passes.
- Web Layout and Readability Auditor passes.
- Screenshot and Media Auditor passes.
- Version, Privacy, and Platform Limit Auditor passes.
- `broken_md_links=0`.
- No non-index standalone page is under 30 lines.
- Every screenshot documented in `docs/LIME_SETTINGS.md` and `docs/KEYBOARD_THEME.md` is embedded and explained in the manual.
