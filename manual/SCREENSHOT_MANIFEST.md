# Screenshot Manifest

This document tracks required screenshots for the user manual.

## Overview

- **Total Screenshots:** ~40–50
- **Platforms:** Android, iOS, iPad
- **Resolution:** 
  - Android: 1080×2340 (Pixel 6 equivalent)
  - iOS: 1170×2532 (iPhone 14 equivalent)
  - iPad: 1024×1366 (iPad Air equivalent)

## By Section

### Quick Start

| File | Platform | Description | Status |
|------|----------|-------------|--------|
| setup-android-1.png | Android | System Settings → Language & Input | ⏳ TODO |
| setup-android-2.png | Android | LIME enabled in IME list | ⏳ TODO |
| setup-ios-1.png | iOS | Settings → General → Keyboard | ⏳ TODO |
| setup-ios-2.png | iOS | Allow Full Access permission | ⏳ TODO |
| first-ime-android-1.png | Android | IME Management screen | ⏳ TODO |
| first-ime-android-2.png | Android | Download IME dialog | ⏳ TODO |
| first-ime-ios-1.png | iOS | Add Keyboard screen | ⏳ TODO |
| troubleshooting-error-1.png | Android | Common error message | ⏳ TODO |

### IME Management

| File | Platform | Description | Status |
|------|----------|-------------|--------|
| ime-list-android.png | Android | IME management list | ⏳ TODO |
| ime-editor-android.png | Android | IME editor interface | ⏳ TODO |
| import-dialog-android.png | Android | File import dialog | ⏳ TODO |
| ime-candidate-android.png | Android | Candidate list for each IME | ⏳ TODO |

### Keyboard Layouts

| File | Platform | Description | Status |
|------|----------|-------------|--------|
| keyboard-english-android.png | Android | English keyboard layout | ⏳ TODO |
| keyboard-chinese-android.png | Android | Chinese keyboard with candidates | ⏳ TODO |
| keyboard-symbols-android.png | Android | Symbol keyboard page 1 & 2 | ⏳ TODO |
| keyboard-popup-android.png | Android | Long-press mini-keyboard | ⏳ TODO |
| keyboard-ipad-split.png | iPad | Split keyboard mode | ⏳ TODO |
| keyboard-ipad-secondary.png | iPad | Secondary glyph example | ⏳ TODO |

### Preferences

| File | Platform | Description | Status |
|------|----------|-------------|--------|
| settings-appearance-android.png | Android | Theme & font size options | ⏳ TODO |
| settings-behavior-android.png | Android | Prediction & learning settings | ⏳ TODO |
| settings-conversion-android.png | Android | Traditional/Simplified toggle | ⏳ TODO |
| settings-reverse-lookup-android.png | Android | Reverse lookup settings | ⏳ TODO |
| settings-backup-android.png | Android | Backup & restore dialog | ⏳ TODO |
| settings-ios-comparison.png | iOS | iOS settings differences | ⏳ TODO |

### Advanced

| File | Platform | Description | Status |
|------|----------|-------------|--------|
| custom-ime-example.png | Android | CIN file example content | ⏳ TODO |
| database-stats-android.png | Android | Database statistics screen | ⏳ TODO |
| learning-stats-android.png | Android | Learning history stats | ⏳ TODO |

## Capture Guidelines

### Setup

1. **Device state:** Fresh install or known good state
2. **Language:** Set to Traditional Chinese (繁體中文)
3. **Display:** 100% brightness, default text size
4. **Status bar:** Visible with time, signal, battery

### Naming Convention

```
{section}-{feature}-{platform}-{variant}.png

Examples:
- setup-android-1.png
- ime-list-android.png
- keyboard-symbols-android-page2.png
- settings-appearance-android.png
```

### Annotation

- Use arrows or circles to highlight important UI elements
- Add Traditional Chinese labels where helpful
- Keep annotations minimal and professional
- Use consistent font and colors

## File Organization

```
manual/assets/screenshots/
├── android/
│   ├── setup-1.png
│   ├── setup-2.png
│   ├── ime-list.png
│   ├── ime-editor.png
│   ├── keyboard-english.png
│   ├── keyboard-chinese.png
│   ├── keyboard-symbols-1.png
│   ├── keyboard-symbols-2.png
│   ├── keyboard-popup.png
│   ├── settings-appearance.png
│   ├── settings-behavior.png
│   ├── settings-conversion.png
│   ├── settings-reverse-lookup.png
│   ├── settings-backup.png
│   ├── database-stats.png
│   └── learning-stats.png
├── ios/
│   ├── setup-1.png
│   ├── setup-2.png
│   ├── ime-management.png
│   ├── keyboard-english.png
│   ├── keyboard-chinese.png
│   ├── keyboard-symbols.png
│   ├── keyboard-popup.png
│   ├── settings-appearance.png
│   ├── settings-behavior.png
│   └── settings-backup.png
└── ipad/
    ├── keyboard-split.png
    ├── keyboard-secondary-glyphs.png
    ├── keyboard-landscape.png
    ├── settings-appearance.png
    └── settings-unique-features.png
```

## Integration into Manual

### Screenshot Insertion

Each page that needs screenshots should include:

```markdown
![{Description in Traditional Chinese}](../assets/screenshots/{platform}/{filename}.png)
```

Example:
```markdown
![Android 系統設定頁面](../assets/screenshots/android/setup-1.png)
```

### Pages Needing Screenshots

- [x] quick-start/quick-start.md — 4 screenshots
- [x] quick-start/first-ime.md — 3 screenshots
- [x] ime-management/download-ime.md — 2 screenshots
- [x] ime-management/ime-editor.md — 2 screenshots
- [x] keyboard-layouts/english-keyboard.md — 1 screenshot
- [x] keyboard-layouts/chinese-keyboard.md — 2 screenshots
- [x] keyboard-layouts/symbols-layout.md — 2 screenshots
- [x] keyboard-layouts/popup-keyboards.md — 2 screenshots
- [x] keyboard-layouts/ipad/secondary-glyphs.md — 3 screenshots
- [x] preferences/appearance.md — 2 screenshots
- [x] preferences/ime-behavior.md — 1 screenshot
- [x] preferences/backup-restore.md — 2 screenshots
- [x] advanced/custom-ime.md — 1 screenshot

## Priority Levels

### High Priority (Core functionality)
- Setup screenshots (quick-start)
- Keyboard layout screenshots
- Basic settings screenshots

### Medium Priority (Important features)
- IME editor
- Reverse lookup
- Backup & restore

### Low Priority (Nice-to-have)
- Statistics screens
- Advanced features
- iPad-specific features

## Status Legend

- ⏳ TODO — Not yet captured
- 🎬 IN PROGRESS — Being captured/edited
- ✅ DONE — Captured, annotated, integrated

## Notes

- Screenshots should be captured from actual devices or emulators
- Use consistent device models for consistency
- Test all links after adding screenshots
- Keep screenshot file sizes under 500 KB each
