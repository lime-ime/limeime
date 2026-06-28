# LimeIME iOS Port — Status

Last updated: 2026-04-09 (Phase 1–5 cloud store complete; running in simulator)

---

## Done

### Project Scaffold

- Created `LimeIME-iOS/` folder at repo root alongside `LimeStudio/`
- Installed XcodeGen (`brew install xcodegen 2.45.3`)
- Authored `LimeIME-iOS/project.yml` — defines both targets, GRDB.swift + ZIPFoundation SPM dependencies, App Group entitlements (`group.org.limeime`), iOS 16.0 deployment target
- Generated `LimeIME.xcodeproj` with two targets:
  - **LimeIME** — Container App (`org.limeime`)
  - **LimeIMEKeyboard** — Keyboard Extension (`org.limeime.keyboard`)
- Both targets share the `Shared/` source folder (compiled into each separately)
- App Group entitlement wired via `project.yml` `entitlements.properties` (not entitlements files directly — XcodeGen owns those files)
- `RequestsOpenAccess: true` on keyboard extension — required for App Group access from the keyboard
- `preBuildScripts` phase copies `../Database/lime.db` into the app bundle (XcodeGen silently ignores `../` resource paths)
- Build confirmed clean in simulator (iPhone 16, iOS 18.1, UDID `F487E48B-16A9-4FBB-8B36-6BCB38EA1764`)

### Shared Data Models (`Shared/Models/`)

| File | Mirrors | Status |
|------|---------|--------|
| `Mapping.swift` | `Mapping.java` | Done — id, code, word, score, baseScore, code3r |
| `Related.swift` | `Related.java` | Done — id, parentWord, childWord, score, baseScore |
| `ImConfig.swift` | `ImConfig.java` | Done — maps Android `im` table columns to struct fields |
| `Keyboard.swift` | keyboard table | Done — `KeyboardConfig` struct |

### Database Layer (`Shared/Database/LimeDB.swift`)

Partial port of `LimeDB.java` (~2,500 lines).

**Schema:**
- `migrate()` creates `im` and `related` tables using the **real Android schema** as fallback (in case keyboard opens before container app)
- Real Android `im` schema: `code, title, desc, keyboard, disable, selkey, endkey, spacestyle`
- Real Android `related` schema: `pword, cword, base_score, user_score`
- Bundled `lime.db` (2.5 MB, Android schema, all tables pre-created) is copied to App Group on first `openDB()` call — this is the authoritative schema source

**Queries (all using real Android column names):**
- `getMappingByCode(_:tableName:limit:)` — SELECT with `(score + basescore) DESC`
- `getMappingByCodeWithFallback(_:tableName:limit:)` — phonetic tone-strip retry via `code3r`
- `updateScore(id:score:tableName:)`
- `getAllImConfigs()` — reads `code → imName`, `title → label`, `keyboard → keyboardId`, `disable` (inverted to `enabled`)
- `updateIMEnabled(id:enabled:)` — writes `disable` column (0 = enabled, 1 = disabled)
- `updateIMSortOrder()` — no-op (Android `im` table has no sort_order)
- `getRelatedPhrase(parentWord:limit:)` — uses `base_score + user_score`
- `learnRelatedPhrase(parentWord:childWord:)` — increments `user_score`
- `getKeyboardList()` — reads `keyboard` table

**Import:**
- `importFromAttachedDB(sourcePath:tableName:)` — ATTACH + DELETE existing rows + INSERT (no DROP/CREATE; table already exists in bundled schema). Detects source columns via `PRAGMA src.table_info(tableName)` and adapts INSERT to include `code3r`, `related`, `basescore` only if present
- `importFromZip(at:tableName:)` — extracts first `.db` from ZIP via ZIPFoundation, then delegates to `importFromAttachedDB`
- `registerIM(imName:tableName:label:keyboardId:)` — upserts into `im` table using Android column names
- `importTxtFile(at:tableName:progress:)` — streaming `.cin`/`.lime` text import with batch inserts
- `exportDB(to:)` — `VACUUM INTO` snapshot backup

**Utilities:**
- `tableExists(_:)` — checks sqlite_master
- `tableHasData(_:)` — checks COUNT > 0 (needed because bundled lime.db pre-creates all tables, so tableExists always returns true even before download)
- `isValidTableName(_:)` — whitelist for SQL injection protection
- `seedDefaultIMs()` — no-op (bundled lime.db is the authoritative source; IM rows added by `registerIM` after download)

### Search Engine (`Shared/Search/SearchServer.swift`)

Port of `SearchServer.java` (~1,500 lines). Completed:

- `getMappingByCode(_:)` with mapping cache + blacklist cache (`NSLock`-protected, 1024-entry eviction)
- `getRelatedPhrase(parentWord:)` with related cache
- `learnRelatedPhraseAndUpdateScore(parentWord:selectedWord:selectedId:)` — score clamped to `[120, 200]`
- `setCurrentIM(tableName:)` — switches IM and clears caches
- `isPhoneticTable` — enables `code3r` fallback for phonetic/eten/hsu/dayi
- Background prefetch thread on IM switch (first-character keys `a-z`, `0-9`)

### Keyboard Extension (`LimeIMEKeyboard/`)

- `KeyboardViewController.swift` — full `UIInputViewController` subclass
  - Globe key → `advanceToNextInputMode()`
  - `setupDatabase()` opens lime.db from App Group; copies bundled lime.db if missing
  - Full composing flow: append → search → candidates → commit → learn
  - Mode switching: Chinese ↔ English via `LayoutLoader`
- `CandidateBarView.swift` — horizontal scrolling candidate buttons + composing code label
- `KeyboardView.swift` — proportional-width UIButton keys, repeat timer, shadow styling
- `KeyLayout.swift` — `LimeKeyLayout`/`KeyRow`/`KeyDef` model; hardcoded phonetic + English fallbacks
- `LayoutLoader.swift` — JSON layout loader with NSLock cache + background prefetch
- `Layouts/*.json` — 45 keyboard layouts converted from Android XML by `.claude/scripts/convert_keyboard_layouts.py`

### Container App (`LimeIME/`)

- `AppDelegate.swift`, `MainViewController.swift` — hosts SwiftUI via `UIHostingController`
- `LimeSettingsView.swift` — **5-tab** SwiftUI settings:
  1. **設定** — step-by-step keyboard activation guide
  2. **輸入法** — enable/disable toggle, drag to reorder
  3. **商店** — cloud IM download store (`IMStoreView`)
  4. **匯入** — file picker for `.db`/`.limedb` and `.cin`/`.lime`; export backup
  5. **偏好** — candidate count, font size, Han convert mode
  - `initDatabase()` on `onAppear` → copies bundled lime.db if App Group db < 1 MB
- `IMCatalog.swift` — static catalog of 8 IM families, 23 variants, download URLs (`https://github.com/lime-ime/limeime/raw/master/Database/`)
- `IMStoreView.swift` — cloud IM store:
  - `IMDownloadManager` (@MainActor ObservableObject) with per-variant `IMInstallState` (notInstalled / downloading(progress) / importing / installed / error)
  - `URLSession.downloadTask` + KVO progress observation
  - Validates download size (> 100 KB) before import
  - Calls `importFromZip` / `importFromAttachedDB` + `registerIM` after download
  - Uses `tableHasData()` (not `tableExists()`) for installed state
  - Searchable list, collapsible family sections, animated install button states

### Scripts

| File | Purpose |
|------|---------|
| `.claude/scripts/convert_keyboard_layouts.py` | Android `res/xml/lime_*.xml` → JSON (45 files) |

---

## Known Issues / To Fix

- **Keyboard extension re-add required after reinstall** — iOS removes keyboard extensions from active list on uninstall. User must re-add in Settings → General → Keyboard → Keyboards → Add New Keyboard → LimeIME
- **Allow Full Access required** — `RequestsOpenAccess: true` means user must enable Allow Full Access in keyboard settings before the keyboard extension can read lime.db from the App Group
- **Phonetic import not automatic** — phonetic table exists in bundled lime.db but is empty; user must install phonetic from the store before the keyboard works

---

## To Do

### High Priority (Core functionality)

- [ ] **Phonetic pre-loaded** — ship a populated `phonetic.db` in the bundle and auto-import on first launch so the keyboard works out-of-the-box without requiring a store download
- [ ] **Default IM seed** — after phonetic auto-import, register it in `im` table so `getAllImConfigs()` returns it and the keyboard selects it automatically
- [ ] **End-to-end test** — install phonetic from store → switch to LimeIME keyboard → type → verify candidates appear
- [ ] **IM switching in keyboard** — keyboard reads `im` table to know which layout to load; currently hardcoded to phonetic

### Medium Priority (Polish)

- [ ] Long-press popup key menus — `popupKeyboard` field in JSON already parsed, need popup `UIView`
- [ ] Symbol keyboard — load `lime_number_symbol.json` on `switchToSymbol` key
- [ ] Landscape height variant — detect `UITraitCollection` and adjust `keyRowHeight`
- [ ] Key press haptics — `UIImpactFeedbackGenerator`
- [ ] iPad layout support

### Phase 5 — App Store Polish

- [ ] Han conversion queries (`hanconvertv2.db`)
- [ ] Emoji lookup (`emoji.db`)
- [ ] Dark mode support
- [ ] Dynamic Type / accessibility
- [ ] Memory profiling — keyboard extension must stay under ~50 MB
- [ ] Privacy manifest (`PrivacyInfo.xcprivacy`)
- [ ] App Store screenshots and metadata
- [ ] TestFlight beta
- [ ] App Store submission

---

## Key Files Reference

| iOS File | Android Equivalent |
|----------|--------------------|
| `Shared/Database/LimeDB.swift` | `limedb/LimeDB.java` + `LimeSQLiteOpenHelper.java` |
| `Shared/Search/SearchServer.swift` | `SearchServer.java` |
| `LimeIMEKeyboard/KeyboardViewController.swift` | `LIMEService.java` |
| `LimeIME/MainViewController.swift` | `ui/MainActivity.java` |
| `LimeIME/IMStoreView.swift` | `ui/SetupImLoadDialog.java` + `LIME.java` cloud URLs |
| `LimeIME/IMCatalog.swift` | `SetupImLoadDialog.java` button configs |
| `LimeIME/LimeSettingsView.swift` | `ui/Preference*.java` (settings screens) |
| `LimeIMEKeyboard/CandidateBarView.swift` | `candidate/CandidateView.java` |
| `LimeIMEKeyboard/KeyboardView.swift` | `keyboard/LIMEKeyboardView.java` |
| `LimeIMEKeyboard/Layouts/*.json` | `res/xml/keyboard_*.xml` (60+ files) |
| `Database/lime.db` | `Database/lime.db` — shared, same schema |

## Architecture Notes

- `lime.db` schema is **identical** between Android and iOS — the bundled `Database/lime.db` is copied directly into the App Group on first launch
- Both targets share `Shared/` source files (compiled separately, no framework overhead)
- App Group `group.org.limeime` is the shared data channel; requires **Allow Full Access** for the keyboard extension to access it
- `project.yml` is the source of truth — run `xcodegen generate` after any structural changes
- Resource files outside the project directory (`../Database/`) must use `preBuildScripts` copy phases — XcodeGen silently drops `../` resource paths
- `tableHasData()` not `tableExists()` is the correct check for "IM installed" since bundled lime.db pre-creates all mapping tables
- Real Android `im` table uses `code/title/keyboard/disable` — not `im_name/label/keyboard_id/enabled`; all iOS queries use the Android column names
