# Manual Source Coverage Map

This file maps user-facing source requirements from `docs/` to manual pages under
`docs/manuals/`. It is a working checklist for source accuracy reviews, not a user manual
page.

## Coverage Rules

- Every user-facing feature in the source docs must have one manual owner.
- Screenshots from `docs/LIME_SETTINGS.md` and `docs/KEYBOARD_THEME.md` must be embedded
  in a matching manual section.
- Internal implementation details are included only when they change user-visible
  behavior, risks, permissions, file formats, or troubleshooting.
- Sensitive platform claims must be checked against the source docs before completion.

## Source To Manual Map

| Source area | User-facing requirement | Manual owner | Status |
|---|---|---|---|
| `LIME_SETTINGS.md` App Setup | Enable keyboard, status banner, Full Access explanation, Android microphone permission state. | `docs/manuals/quick-start.md` | Covered |
| `LIME_SETTINGS.md` IM List | Enable/disable IMs, reorder IMs, open IM detail, disabled IMs remain in database. | `docs/manuals/ime-management.md` | Covered |
| `LIME_SETTINGS.md` IM Detail | IM name, version, end key, record count, keyboard layout, phonetic type, array10 auto-commit, custom number/symbol roots. | `docs/manuals/ime-management.md`, `docs/manuals/preferences.md` | Covered |
| `LIME_SETTINGS.md` IM Install | Cloud download, `.limedb`, `.cin`, `.lime`, custom IM, related database import, restore learned records, progress state. | `docs/manuals/ime-management.md`, `docs/manuals/quick-start.md` | Covered |
| `LIME_SETTINGS.md` Table Editor | Mapping records, related phrases, search, pagination, add/edit/delete, score. | `docs/manuals/ime-management.md` | Covered |
| `LIME_SETTINGS.md` DB Manager | Backup database, restore database, restore default database, overwrite warning, `.db` / `.limedb`, progress state. | `docs/manuals/database-management.md`, `docs/manuals/quick-start.md`, `docs/manuals/faq.md` | Covered |
| `LIME_SETTINGS.md` IM Preferences | Theme, size, candidate font, number row, arrow keys, split keyboard, feedback, behavior, Han conversion, learning, English suggestions. | `docs/manuals/preferences.md` | Covered |
| `LIME_SETTINGS.md` Reverse Lookup | Per-IM reverse lookup source, `無`, enabled IM choices, no-candidate use case. | `docs/manuals/preferences.md`, `docs/manuals/keyboard-input.md` | Covered |
| `KEYBOARD_THEME.md` Themes | Theme index meanings, system theme behavior, keyboard/candidate/Emoji screenshots for all documented themes. | `docs/manuals/preferences.md` | Covered |
| `KEYBOARD_TYPE.md` Input fields | Phone, number, email, password, URL/search, generic text, remembered Chinese/English mode, restricted fields. | `docs/manuals/keyboard-input.md` | Covered |
| `ANDROID_IPHONE_KEYBOARD.md` Common keyboard behavior | English `中` key, symbol pages, long press, popup keyboard, space slide, keyboard options menu, iOS inline menu limits. | `docs/manuals/keyboard-input.md` | Covered |
| `ANDROID_IPHONE_KEYBOARD.md` iPad secondary glyphs | iPad slide-down or long-press secondary glyphs, separate from popup keyboards. | `docs/manuals/keyboard-input.md` | Covered |
| `IPAD_KEYBOARD.md` iPad layout | Five-row iPad layouts, split keyboard, globe/menu behavior, secondary glyphs, excluded future work. | `docs/manuals/keyboard-input.md`, `docs/manuals/preferences.md` | Covered |
| `IPAD_KB_SIZE_TIERS.md` iPad tiers | 13-inch, 11-inch, and mini size tiers are planned or not implemented, not current user functionality. | `docs/manuals/preferences.md`, `docs/manuals/faq.md` | Covered |
| `ANDROID_VOICE_INPUT.md` Voice input | Inline dictation permission, Google/system voice IME fallback, `RecognizerIntent` fallback, Traditional Chinese hint, Han conversion. | `docs/manuals/quick-start.md`, `docs/manuals/faq.md` | Covered |
| `USER_MANUAL_PLAN.md` Structure | All manual files under `docs/manuals/`, ASCII paths, page ownership by task area. | `docs/manuals/**/*.md` | Covered |
| Auditor docs | Source accuracy, Chinese writing, links, screenshots, privacy/platform limits, visual layout. | `docs/MANUAL_REVIEW_WORKFLOW.md`, this file, verification commands | Covered |

## Required Screenshot Coverage

The current screenshot audit expects all unique image references from
`docs/LIME_SETTINGS.md` and `docs/KEYBOARD_THEME.md` to appear in `docs/manuals/**/*.md`.

Current verified count:

- Documented screenshots: 66.
- Missing screenshots in manual: 0.

## Sensitive Claims Checklist

| Claim | Required wording |
|---|---|
| iPhone/iPad Full Access | Optional for typing and setup. Unlocks in-app backup, key vibration feedback, and table editing. Do not describe it as required for basic typing, IM install/import/delete, restore preparation, or normal app use. Manual owners: `quick-start.md`, `database-management.md`, `ime-management.md`, `preferences.md`, `faq.md`, `troubleshooting.md`, `privacy.md`. |
| DB restore | Restore replaces current LIME data, so users should backup first when current data matters. |
| Device migration | Users with an old LIME device should backup the full database on the old device and restore it on the new device before rebuilding from scratch. |
| iPad size tiers | 13-inch, 11-inch, and mini tiers are not implemented as current user functionality. |
| Android voice | LIME chooses voice input automatically, and microphone permission is only required for LIME-owned inline dictation. |
| URL/search fields | These remain normal text-oriented fields, so Chinese keyword search remains possible. |
| Restricted fields | Phone, number, password, and similar fields may change or bypass normal LIME behavior because of platform or field restrictions. |

## Verification Commands

Run these before claiming the manual is complete:

```powershell
rg -n "；" manual docs\USER_MANUAL_PLAN.md
```

```powershell
$issues=@(); Get-ChildItem -Recurse -File docs/manuals -Filter *.md | ForEach-Object { $path=$_.FullName.Substring((Get-Location).Path.Length+1); $lineNo=0; Get-Content $_.FullName | ForEach-Object { $lineNo++; $line=$_; if ($line -match '^\s*(#|<|</|```|\||-|\d+\.|$)' ) { return }; $sentences=[regex]::Matches($line,'[^。]+。'); foreach($m in $sentences){ $s=$m.Value.Trim(); if ($s -match '[\p{IsCJKUnifiedIdeographs}]' -and $s -notmatch '，') { $issues += "${path}:${lineNo}: $s" } } } }; "sentences_without_comma=$($issues.Count)"; $issues
```

```powershell
$files = Get-ChildItem -Recurse -File docs/manuals -Filter *.md; $broken=@(); foreach ($file in $files) { $text = Get-Content -Path $file.FullName -Raw; $matches = [regex]::Matches($text, '\[[^\]]+\]\(([^)#]+\.md)(?:#[^)]+)?\)'); foreach ($m in $matches) { $target = $m.Groups[1].Value; if ($target -match '^(https?://|mailto:)') { continue }; $resolved = Join-Path $file.DirectoryName $target; if (-not (Test-Path $resolved)) { $broken += "$($file.FullName): $target" } } }; "broken_md_links=$($broken.Count)"; $broken
```

```powershell
$source = (Get-Content docs\LIME_SETTINGS.md, docs\KEYBOARD_THEME.md -Raw); $shots = [regex]::Matches($source, '(?:assets/lime_settings_[A-Za-z0-9_]+\.png|assets/[A-Za-z0-9_]+\.png)') | ForEach-Object { $_.Value } | Sort-Object -Unique; $manual = Get-ChildItem -Recurse -File docs/manuals -Filter *.md | ForEach-Object { Get-Content $_.FullName -Raw } | Out-String; $missing = foreach ($s in $shots) { if ($manual -notmatch [regex]::Escape($s)) { $s } }; "documented_screenshots=$($shots.Count)"; "missing_in_manual=$($missing.Count)"; $missing
```

```powershell
rg -n "先看|怎麼看|誤把|是不是 LIME|Apple 系統注音|代表目前是 LIME|重要訊號|用.*判斷|確認目前是 iPad LIME|不是 iPhone 版面|目前不是系統鍵盤|目前畫面可能不是 LIME|只處理|本頁|這個頁面|以下說明" manual
```
