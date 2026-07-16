# iOS FA Re-Architecture — Task-Level Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (Codex CLI subagents per docs/IOS_FA_SETUP_REARCH.md §Subagent workflow) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Review (gap + /code-review + ponytail-review) is done by the orchestrator, never delegated.

**Goal:** Keyboard-canonical DB with app-as-proxy desired-state sync, so every LIME feature except in-app backup and haptics works with Full Access OFF (docs/IOS_FULL_ACCESS.md), plus tri-state FA detection (docs/IOS_FULL_ACCESS_DETECT.md) and the 前往設定 reliability changes (docs/IOS_GOTO_SETTINGS.md).

**Architecture:** The keyboard owns `lime.db` in its own container (first-run copy of the bundled default; legacy App Group DB adopted once). The app writes single-format `.limedb` table sources + a persistent `restore.limedb` epoch into the App Group; the keyboard scans-on-appear, diffs against an in-DB ledger, and applies epoch-then-tables idempotently. Backup is the sole FA-ON flow (VACUUM INTO relay). Emoji becomes a bundle-attached read-only DB.

**Tech Stack:** Swift, GRDB, ZIPFoundation, xcodebuild (via `.claude/scripts/ios-gate.sh`), Codex CLI subagents.

## Global Constraints

- Simulator gate: `.claude/scripts/ios-gate.sh {build|unit LimeTests/<Class>}` — always green before commit. Device: the physical test iPhone `2A0518C1-2ECE-514D-A199-D9A7B1AFC8CD`, `-allowProvisioningUpdates`, `GIT_CONFIG_COUNT=0`.
- NEVER run xcodegen; `project.pbxproj` is edited by hand. New Swift files must be added to pbxproj (Shared files → both LimeIME + LimeKeyboard targets, matching existing Shared/Database entries).
- Encoding: `.swift`/`.md` UTF-8 WITH BOM; `.json` without BOM. No new repo-root files; briefs/reports in `.claude/txt/`, scripts in `.claude/scripts/`.
- App Group: `group.org.limeime` (`LIMEPreferenceManager.suiteName`). Bundle prefix `org.limeime`.
- TDD: failing test in `LimeTests` first. One commit per task, `feat(ios): FA re-arch I<N>.<task> — <scope>`, no co-author trailer.
- Design deviations discovered while implementing → update the design doc in the same commit. Already-known correction: iOS has NO hanconvert DB (`LimeDB.hanConvert` uses CFStringTransform, LimeDB.swift:2818) — I2 is emoji-only and must fix IOS_FULL_ACCESS.md's static-data section.

## File Structure (new/modified)

| Path | Role |
|---|---|
| `Shared/Database/SyncContract.swift` (new) | Paths, Darwin names, `TableMeta`/`RestoreMeta` codables, ledger types — the single contract both processes compile |
| `Shared/Database/TableStore.swift` (new) | App-side writer: install/uninstall/prepareRestore/clearAllSources (single-writer, temp+rename) |
| `Shared/Database/TableSyncEngine.swift` (new) | Keyboard-side executor: scanAndApply (epoch-first, table diff, attach-import, learned merge, ledger states) |
| `Shared/Database/LimeDB.swift` (mod) | `sync_meta` + `sync_ledger` tables, epoch/ledger API, attach `emoji.db`, schema-qualified emoji queries, post-swap hygiene |
| `Shared/Database/DBServer.swift` (mod) | Canonical path per run-mode, first-run bundled copy, legacy adoption, VACUUM INTO export |
| `LimeKeyboard/KeyboardViewController.swift` (mod) | scan trigger, Darwin observer/pings, status banner, heartbeat→outbox files + local mirror |
| `LimeSettings/Controllers/SetupImController.swift` (mod) | Route install/import/restore/backup through TableStore; conversion-to-limedb via existing export path |
| `LimeSettings/Views/SetupTabView.swift` (mod) | Tri-state FA detection, copy changes, 前往設定 URL variant + guidance |
| `LimeSettings/Views/DBManagerView.swift` (mod) | Backup gating on Confirmed-ON, restore always enabled |
| `LimeKeyboard/Resources` via pbxproj (mod) | Bundled `lime.db` + `emoji.db` in appex |
| `LimeTests/SyncContractTest.swift`, `TableStoreTest.swift`, `TableSyncEngineTest.swift`, `EpochRestoreTest.swift`, `EmojiAttachTest.swift` (new) | Per-iteration TDD suites |
| `.claude/scripts/ios-extract-emoji.sh` (new) | One-time emoji.db extraction + default lime.db regeneration |

Task protocol (applies to every task; steps below name only the task-specific content):
- [ ] Write failing tests named in the task → `ios-gate.sh unit LimeTests/<Class>` shows FAIL
- [ ] Dispatch Codex brief (or implement directly if < ~30 lines) → implementation
- [ ] `ios-gate.sh unit LimeTests/<Class>` PASS, then `ios-gate.sh build` PASS
- [ ] Orchestrator gap review vs design-doc sections + /code-review + ponytail-review
- [ ] Commit

---

## I0 — Feasibility spikes

### Task 0.1: Device probe (App Group read + own-container write, FA state discovery)

**Files:** Modify `LimeKeyboard/KeyboardViewController.swift` (temporary `#if DEBUG` block in `viewDidLoad`); Modify `LimeSettings/Views/SetupTabView.swift` (write marker file `probe_marker.txt` to App Group on appear).
**Probe logic (keyboard):** read `AppGroup/probe_marker.txt`; write `probe_result.txt` into own container `Application Support/`; attempt write `AppGroup/probe_kb_write.txt`; render one-line result in the existing status/banner area: `AGread:<ok|fail> ownWrite:<ok|fail> AGwrite:<ok|fail>` (AGwrite ok ⇒ FA is ON).
**Steps:**
- [ ] Implement probe (direct edit, no subagent — throwaway code)
- [ ] `ios-gate.sh build` PASS, deploy to the physical test iPhone: `GIT_CONFIG_COUNT=0 xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'platform=iOS,id=2A0518C1-2ECE-514D-A199-D9A7B1AFC8CD' -allowProvisioningUpdates build` + `xcrun devicectl device install app --device 2A0518C1 <path>.app`
- [ ] Read results: `xcrun devicectl device copy from --device 2A0518C1 --domain-type groupContainer --domain-identifier group.org.limeime ...` for App-Group-visible artifacts; keyboard banner via device UITest run if container copy insufficient
- [ ] Record measured facts in IOS_FULL_ACCESS.md open items. **AGread fail on device ⇒ STOP ENTIRE PLAN.** If device FA currently ON and cannot be toggled without a human, record `FA-OFF probe = DEVICE RESIDUE` and continue.

### Task 0.2: Darwin notification spike

**Files:** same temporary blocks. App posts `org.limeime.tables.updated` on a debug button/appear; keyboard observer (`CFNotificationCenterAddObserver`, Darwin center) appends receipt line to own-container log; result shown in banner + copied out as in 0.1.
- [ ] Implement, deploy, record result (expected deliverable: works both FA states). Failure → design doc: demote Darwin to "unavailable", scan-only latency noted.

### Task 0.3: Import timing probe

**Files:** temporary; fixture = generate 500k-row limedb via sqlite3 CLI in `.claude/txt/` (not committed).
- [ ] Time `ATTACH + INSERT INTO ... SELECT` in keyboard process on device via os_signpost/NSLog delta; record rows/sec + chosen chunk size (target: chunk ≤ 2 s) in IOS_FULL_ACCESS.md.
- [ ] Remove all I0 temporary code; commit `feat(ios): FA re-arch I0 — probes + measured facts (docs only)`.

---

## I1 — Foundation

### Task 1.1: SyncContract

**Files:** Create `Shared/Database/SyncContract.swift` + pbxproj (both targets); Test `LimeTests/SyncContractTest.swift`.
**Produces (later tasks rely on these exact names):**
```swift
enum SyncPaths {                       // all derived from one base URL, injectable for tests
    static func tablesDir(_ base: URL) -> URL      // base/tables
    static func tableFile(_ base: URL, stem: String) -> URL   // tables/<stem>.limedb
    static func tableMeta(_ base: URL, stem: String) -> URL   // tables/<stem>.meta.json
    static func restoreDB(_ base: URL) -> URL      // base/restore.limedb
    static func restoreMeta(_ base: URL) -> URL    // base/restore.meta.json
    static func outboxDir(_ base: URL) -> URL      // base/outbox
    static func exportRequest(_ base: URL) -> URL  // outbox/export.request.json
    static func backupSnapshot(_ base: URL) -> URL // outbox/backup.limedb
    static func receipt(_ base: URL) -> URL        // outbox/receipt.json
}
enum SyncSignal: String { case tablesUpdated = "org.limeime.tables.updated",
    outboxUpdated = "org.limeime.outbox.updated",
    importDone = "org.limeime.import.done", importFailed = "org.limeime.import.failed" }
struct TableMeta: Codable, Equatable { var restoreLearning: Bool?; var displayName: String?; var provenance: String? }
struct RestoreMeta: Codable, Equatable { var epochUUID: String; var schemaVersion: Int }
struct FileIdentity: Equatable { let size: Int64; let mtime: TimeInterval; init?(url: URL) }
func atomicWrite(_ data: Data, to url: URL) throws          // temp + rename, creates parent dir
```
**Tests:** path derivations; `FileIdentity` nil for missing file, equal/unequal on rewrite; `atomicWrite` leaves no temp on success; `RestoreMeta` JSON round-trip (no BOM).

### Task 1.2: LimeDB sync_meta + ledger

**Files:** Modify `Shared/Database/LimeDB.swift` (migrate step); Test `LimeTests/LimeDBTest.swift` (extend).
**Produces:**
```swift
// migrate(): CREATE TABLE IF NOT EXISTS sync_meta(key TEXT PRIMARY KEY, value TEXT);
// CREATE TABLE IF NOT EXISTS sync_ledger(stem TEXT PRIMARY KEY, size INTEGER, mtime REAL,
//   state TEXT NOT NULL, error TEXT, attempts INTEGER NOT NULL DEFAULT 0, resume_marker INTEGER)
enum LedgerState: String { case pending, inProgress = "in_progress", done, failed }
struct LedgerEntry: Equatable { var stem: String; var identity: FileIdentity?; var state: LedgerState; var error: String?; var attempts: Int; var resumeMarker: Int64? }
extension LimeDB {
    func syncMeta(_ key: String) -> String?
    func setSyncMeta(_ key: String, _ value: String) throws       // "epoch_uuid", "schema_version"
    func ledgerEntry(stem: String) -> LedgerEntry?
    func upsertLedger(_ e: LedgerEntry, in db: Database) throws   // callable inside an open txn
    func wipeLedger(in db: Database) throws
    func ensureEpochUUID() throws -> String                        // stamp-if-missing, return current
}
```
**Tests:** fresh DB gets tables + `ensureEpochUUID` stability across reopen; ledger upsert/read round-trip; upsert inside a rolled-back txn leaves no row (atomicity).

### Task 1.3: Canonical path, first-run copy, legacy adoption

**Files:** Modify `Shared/Database/DBServer.swift`; pbxproj: duplicate the app's "Copy lime.db to bundle" phase onto LimeKeyboard target; Test `LimeTests/DBServerTest.swift` (extend).
**Produces:**
```swift
enum DBRunMode { case app, keyboard }
// DBServer.datasourceContainer gains: init(runMode: DBRunMode, dataDirOverride: URL? = nil, appGroupOverride: URL? = nil)
// keyboard mode dataDirURL = own-container Application Support/LimeIME (FileManager.applicationSupportDirectory)
// openDatasource() keyboard mode order:
//   1. canonical exists → open
//   2. legacy adoption: appGroup lime.db exists → copy db/-wal/-shm trio → open → PRAGMA quick_check → ensureEpochUUID → adopt (delete copies on validation failure)
//   3. bundled default copy (Bundle.main lime.db — now present in appex) → open → ensureEpochUUID
//   4. nil (existing English-fallback behavior)
// app mode unchanged this iteration (legacy screens keep working until I5/I6)
```
KeyboardViewController passes `.keyboard`; app side `.app`.
**Tests (use dataDirOverride/appGroupOverride temp dirs):** fresh-run copies bundled default and stamps epoch; legacy adoption succeeds and preserves rows; corrupt legacy (truncated file) falls back to default; adoption happens once (second open uses canonical).

---

## I2 — Emoji split (design-doc correction: emoji only, no hanconvert DB on iOS)

### Task 2.1: Extraction script + assets

**Files:** Create `.claude/scripts/ios-extract-emoji.sh` (sqlite3: `.dump emoji_data emoji_fts` → new `emoji.db`; regenerate default `Shared/Database/lime.db` without emoji tables via `DROP TABLE` + `VACUUM`); commit regenerated `lime.db` + new `LimeIME-iOS/LimeKeyboard/emoji.db`; pbxproj: emoji.db into appex + app resources; update IOS_FULL_ACCESS.md static-data section (hanconvert correction).
- [ ] Verify: `sqlite3 emoji.db "select count(*) from emoji_data"` > 0; default lime.db loses ~most of its size; `ios-gate.sh build` PASS.

### Task 2.2: ATTACH + qualified queries + hygiene

**Files:** Modify `Shared/Database/LimeDB.swift`; Test `LimeTests/EmojiAttachTest.swift`.
**Produces:** `LimeDB(path:emojiDBURL:)` — prepareDatabase does `ATTACH DATABASE 'file:<url>?immutable=1' AS emoji` when the resource exists; every `emoji_data`/`emoji_fts` reference becomes `emoji.emoji_data`/`emoji.emoji_fts`; `emoji_user` stays in main; `dropLegacyStaticTables(in:)` (post-swap hygiene) drops main.emoji_data/emoji_fts if present.
**Tests:** emoji search returns rows via attachment; canonical DB seeded with a fake `main.emoji_data` row still returns attached data (shadowing) and hygiene drops it; missing emoji.db → no crash, empty emoji results; `emoji_user` insert + dangling reference filtered by join.

---

## I3 — Desired-state sync

### Task 3.1: TableStore (app writer)

**Files:** Create `Shared/Database/TableStore.swift` + pbxproj; Test `LimeTests/TableStoreTest.swift`.
**Produces:**
```swift
final class TableStore {
    init(baseURL: URL)   // App Group container (or temp dir in tests)
    func installLimedb(from url: URL, stem: String, meta: TableMeta?) throws   // quick_check + required-table verify, temp+rename, sidecar after data
    func installConverted(from textURL: URL, stem: String, meta: TableMeta?) throws  // .cin/.lime → temp LimeDB → importTxtTable → exportIMAsLimedb-shaped standalone limedb → installLimedb path
    func installFromZip(from zipURL: URL, stem: String, meta: TableMeta?) throws     // unzip (ZIPFoundation) then route by inner extension
    func uninstall(stem: String) throws                                              // delete data + sidecar
    func installedStems() -> [String]
    func prepareRestore(from dbURL: URL, schemaVersion: Int) throws -> String        // copy to restore.limedb via temp: fresh epoch_uuid into sync_meta, wipe sync_ledger rows, clearAllSources(), write restore.meta.json AFTER db; returns uuid
    func clearAllSources() throws
}
```
Validation failure throws typed errors surfaced by callers; invalid file never lands in `tables/`.
**Tests:** limedb install writes data-then-sidecar and is atomic (no `.tmp` residue); corrupt limedb rejected, folder untouched; cin conversion produces attachable single-table limedb (row count matches fixture); zip routing; uninstall removes both files; prepareRestore stamps fresh uuid ≠ source uuid, clears ledger rows inside the file, clears sources.

### Task 3.2: TableSyncEngine (keyboard executor)

**Files:** Create `Shared/Database/TableSyncEngine.swift` + pbxproj; Test `LimeTests/TableSyncEngineTest.swift`.
**Produces:**
```swift
struct SyncEvent: Equatable { enum Kind { case epochApplied, imported, dropped, failed, noop }; let kind: Kind; let stem: String? }
final class TableSyncEngine {
    init(server: DBServer, baseURL: URL)      // baseURL = App Group (temp in tests)
    @discardableResult func scanAndApply(deadline: Date? = nil) -> [SyncEvent]
}
// Order per IOS_FULL_ACCESS.md §Conflict resolution:
// 1. restore.meta.json uuid != canonical sync_meta.epoch_uuid → validate schema_version ≤ CURRENT_DB_VERSION,
//    close current DB (closeForReplacement + rebuild — see memory: explicit LimeDB rebuild required),
//    copy restore.limedb over canonical (temp+rename), reopen, dropLegacyStaticTables, emit .epochApplied
// 2. per-stem diff vs sync_ledger identity: changed → clear-then-attach-bulk-copy in chunk txns
//    (chunk size from I0.T3; upsertLedger in same txn; learned merge when sidecar restoreLearning != false:
//    carry score/userword columns for matching rows from a pre-clear stash temp table);
//    file gone → drop table rows + ledger row, emit .dropped
// 3. failed(deterministic) identities skip until identity changes; attempts cap 3 for environmental
// busy_timeout set; scan-diff re-checked inside txn (concurrent instance no-op)
```
**Tests (in-process both roles):** TableStore.install → scanAndApply imports rows queryable via SearchServer; same identity → single `.noop`; rewrite file → re-import with learned score preserved (set a score, reinstall, score survives); uninstall → `.dropped`; interrupted chunk (deadline mid-import) resumes and completes on second scan; identity change mid-resume abandons partial; epoch apply resets ledger and re-imports newer table file on top; same-uuid + churned mtime → noop; future schema_version epoch → `.failed`, DB untouched.

### Task 3.3: Wire keyboard + Darwin + banner

**Files:** Modify `LimeKeyboard/KeyboardViewController.swift` (call `scanAndApply` on the existing background DB queue from `viewWillAppear`; Darwin observer for `tablesUpdated` → rescan; post `importDone`/`importFailed`; reuse existing toast/banner for 匯入中/已安裝/匯入失敗); Modify `SetupImController.swift` install paths → TableStore + post `tablesUpdated` + keep ProgressManager UX; probe-field focus after install (existing mechanism).
**Tests:** KeyboardViewControllerTest extension — scan triggered on appear (mock engine records call); SetupImControllerTest — install routes to TableStore (folder contains stem after install), no direct App Group DB write remains in that path.

---

## I4 — Restore / backup

### Task 4.1: Restore flows via epoch

**Files:** Modify `SetupImController.swift`: `restoreDB(from:)` → unzip → validate schema_version ≤ current (reject "請先更新 LIME") → `TableStore.prepareRestore`; `restoreBundledDatabase()` → prepareRestore(from: bundled default); Test `LimeTests/EpochRestoreTest.swift`.
**Tests:** restore zip → folder state = restore.limedb + meta only (sources cleared); engine applies epoch then a later-installed table; factory reset delivers default-db epoch; skew-rejection path returns typed error.

### Task 4.2: Backup relay

**Files:** Modify `Shared/Database/DBServer.swift` (`func exportSnapshot(to url: URL) throws` — completes/rolls back pending imports, then `VACUUM INTO` temp + rename + write `receipt.json {requestUUID, epochUUID, at}`); KeyboardViewController: honor `export.request.json` during scan (FA-write attempt; silent failure ok); SetupImController `backupDB()` → write request `{requestUUID, expiresAt}` (TTL ~2 min; keyboard ignores expired requests; app accepts only a receipt matching its current requestUUID — IOS_FULL_ACCESS.md addendum "request hygiene"), focus probe, poll receipt ≤ 15 s → zip snapshot + prefs (existing backup zip layout) → cleanup. Timeout UX disambiguates via Darwin liveness (addendum): no `org.limeime.fa.*` ping in the window → "請將鍵盤切換至萊姆輸入法後再試"; ping but no receipt → FA unlock guidance.
**Tests:** request → engine/scan produces snapshot + receipt in temp App Group; snapshot opens + quick_check passes + contains learned rows; pending in-progress import defers snapshot until completed; `IntegrationTestBackupRestore` updated end-to-end: backup zip → prepareRestore → engine apply → learned data round-trips.

---

## I5 — Signals + Settings UI + spec sync

### Task 5.1: Tri-state FA detection + heartbeat files

**Files:** Modify `SetupTabView.swift` (`refreshStatus`): `enum FAState { confirmedOn, confirmedOff, unknown }` — confirmedOn from outbox `heartbeat.json` freshness (≤ 120 s); confirmedOff ONLY from a live Darwin `org.limeime.fa.off` ping this session (silence never proves off — detection-doc addendum); else unknown; legacy shared-defaults keys ignored. Probe trigger rewritten to `keyboardEnabled && !hasFreshEvidence` (the current `!fullAccessEnabled` guard is circular and never fires on device — detection-doc addendum). KeyboardViewController: on appear post `org.limeime.fa.on` / `.fa.off` (reads `hasFullAccess` directly) + heartbeat → outbox file (FA-on only lands) + mirror heartbeat/db-error keys to `UserDefaults.standard` (self-diagnosis). Banner: confirmedOff renders the same feature-unlock copy as unknown, never an error.
**Tests:** unit-level state derivation (fixture outbox dirs + simulated fa-ping flags: fresh/stale/missing/off-ping → confirmedOn/unknown/unknown/confirmedOff).

### Task 5.2: Setup/DB-manager copy + gating + goto-settings

**Files:** `SetupTabView.swift`: FA note "完整取用用於：備份已學習字詞、按鍵震動回饋。不開啟也能正常輸入與安裝輸入法。", FA step marked 建議, `openLimeKeyboardSettings()` tries `"\(openSettingsURLString)/\(Bundle.main.bundleIdentifier!)"` then plain, post-tap two-landing guidance elevated; `DBManagerView.swift`: 備份 disabled unless confirmedOn (probe fires on tab appear), footnote unlock copy, restore buttons always enabled; `Settings.bundle/Root.plist` footer softened.
- [ ] Apply LIME_SETTINGS.md §4/§7 spec edits (sanctioned now).
- [ ] ios-visual-verify simulator pass: banner states, install-via-probe flow, DB-manager gating screenshots.

---

## I6 — Legacy removal

### Task 6.1: Delete legacy paths + grep gates

**Files:** DBServer/KeyboardViewController/SetupImController: remove keyboard App-Group-DB open path, `databaseGenerationKey`/`keyboardRuntimeGenerationKey` signal plumbing (prefs reads stay), dead snapshot/fallback code; ponytail sweep.
**Gates:** full unit suite green; `grep -rn "keyboard_has_full_access\|databaseGenerationKey" LimeKeyboard Shared` shows no keyboard→shared-defaults correctness writes; `ponytail:` comments present on deliberate ceilings (source-file record editing, attempts cap, 15 s receipt poll).

Note: app-side record editor / IM counts re-point (sources-as-editable-masters per IOS_FULL_ACCESS.md open item) is OUT OF SCOPE for this plan — current screens keep reading the app's legacy App Group DB until a follow-up plan; IOS_FULL_ACCESS.md open item updated to say so in I6's commit.

---

## Final Target Gate

Run exactly docs/IOS_FA_SETUP_REARCH.md §Final Target Gate items 1–7 (device rows on the physical test iPhone — attached at plan time). Loop on failure per that section. Device FA toggling requires a human; any human-gated row goes to the residue checklist in the final report.
