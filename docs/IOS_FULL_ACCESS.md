# iOS Full Access — Permission Model and Architecture

Scope: LimeKeyboard extension and LimeSettings — DB ownership, install/import/backup/restore flows, app↔keyboard signaling, and what Full Access actually gates.

Companion docs: [IOS_FULL_ACCESS_DETECT.md](IOS_FULL_ACCESS_DETECT.md) (enabled / Full Access detection in Settings UI), [IOS_GOTO_SETTINGS.md](IOS_GOTO_SETTINGS.md) (Settings deep-link reliability).

## Bottom line

LimeIME cannot require Full Access for the keyboard to function (App Review Guideline 4.4.1: a keyboard must type, provide the globe/next-keyboard path, and remain functional without Full Access).

Design: the keyboard's own container holds the one canonical `lime.db`. The app never shares a live database with the keyboard; it acts as a proxy that downloads/receives table files into an App Group "desired-state" folder, and the keyboard imports them itself. With Full Access OFF, everything works — typing, install, import, uninstall, restore, learning, learned-record preservation on re-install. Full Access ON unlocks exactly two features:

1. In-app backup (keyboard exports learned data to the App Group). The sole purpose of backup is the user's learned data — fixed tables are re-downloadable.
2. Key haptic feedback (按鍵震動回饋) — a system restriction: keyboard extensions cannot play haptics without Full Access. Key-click *sound* (`UIDevice.playInputClick()`) works without it.

Sources:

- Apple App Review Guidelines 4.4.1: https://developer.apple.com/app-store/review/guidelines/
- Apple UIKit open-access guide: https://developer.apple.com/documentation/uikit/configuring-open-access-for-a-custom-keyboard

## Permission facts

Apple's open-access documentation, verbatim: without open access a keyboard has "No access to the file system apart from the keyboard's own sandbox container, and read-only access to the containing app's shared containers."

| Actor → Target | Full Access OFF | Full Access ON |
|---|---|---|
| App → App Group | read/write | read/write |
| Keyboard → App Group | **read-only** | read/write |
| Keyboard → its own container | read/write | read/write |
| App → keyboard's own container | never | never |

- The containing app is never restricted — Full Access limits only the keyboard extension.
- The keyboard's own `Application Support` data persists across process restarts, reboot, and Full Access toggles; removed on uninstall.
- Reading App Group file *bytes* is safe FA OFF; opening the shared DB live is not — even a "read-only" SQLite open of a WAL-mode database wants to create/write `-shm`/`-wal` sidecars. Hence the keyboard never opens the App Group DB; it only reads/copies files.
- Shared UserDefaults: app-written preferences ARE readable by the keyboard FA OFF (standard settings-sync mechanism; keyboard re-reads on appear). But keyboard writes are dropped FA OFF, cross-process change notifications do not fire, and cfprefsd may serve a stale cache to a long-lived process — so defaults carry user preferences only, never correctness signals (those use files).
- Darwin notifications (`CFNotificationCenter`) are not Full-Access-gated in either direction, but carry no payload and reach only a live, listening process.
- Simulator does not enforce the keyboard sandbox — permission behavior MUST be validated on a real device.

## Architecture

### Canonical DB — keyboard-owned

- Canonical DB: `KeyboardContainer/Application Support/lime.db`. Opened with the normal `LimeDB(path:)` (WAL, migrate, repair) — always writable, in every permission state. Learning writes always succeed and are never clobbered by installs (imports are incremental table loads, not whole-file replacement).
- DBServer / SearchServer / LimeDB run keyboard-side against this DB.
- **Static pre-shipped data (emoji, han_convert) lives OUTSIDE the canonical DB**: bundled in the appex as read-only `emoji.db` / `hanconvert.db`, ATTACHed at open (`immutable=1` — code-signed bundle resources truly are immutable; cross-DB joins and FTS work normally). Never synced, never in backup/restore, upgraded automatically by app updates (no version-compare/reseed migration). Exception: `emoji_user` (favorites/recents) is user data — it stays in the canonical DB and therefore in backup. Side effect: the default `lime.db` drops its emoji tables, shrinking first-run copy, `VACUUM INTO` snapshots, backup zips, and restore swaps.
- **Upgrade rule: static attached DBs upgrade by replacement, the canonical DB upgrades by migration.** An app update shipping a newer emoji/hanconvert DB applies automatically at next attach — no materialized copy exists to go stale, and code + data travel in the same signed bundle so schema changes are atomically paired with the code reading them (`immutable=1` stays safe: iOS kills the extension during updates, no live handle survives a bundle swap). `emoji_user` rows referencing entries removed by a newer emoji DB dangle harmlessly (join semantics — they just stop displaying). The bundled default `lime.db` does NOT auto-apply on update: it is the fresh-install / factory-reset baseline only; existing canonical DBs upgrade via `migrate()`.
- First run: the keyboard copies its bundled default `lime.db` into the canonical path, then opens it normally (`migrate()` runs only in its usual schema-upgrade role, not as bootstrap). The default DB is "epoch zero" — the identical file the app uses for 還原預設資料庫 — and already carries emoji data and IM metadata (LIME ships with empty IM tables by design). Build requirement: bundle `lime.db` in the LimeKeyboard appex (currently only the app target has the copy phase); ~4 MB appex growth, required for 4.4.1 anyway since the keyboard must work standalone before the app ever runs.

### Desired-state table folder (app → keyboard, works FA OFF)

Not a message queue — a declarative folder that always reflects what should be installed. No acks are needed for correctness, so the one-way FA-OFF channel is sufficient.

- Path: `AppGroup/tables/<tableNick>.limedb` — stem is the table identity (closed enum: `IM_CODES` + related). Single format: everything the keyboard receives is a limedb (matching the downloadable `.limedb` reference schema). Fixed names; no mapping/manifest file (a registry can drift from the directory; the path is the identity).
- App is the sole writer. Replace = write temp → atomic rename. At most one source file per table, by construction.
- Optional per-table sidecar `<tableNick>.meta.json` written after the data file, only if an import needs options a filename cannot carry (e.g. the 還原已學習記錄 choice, display name, source provenance such as "從 xxx.cin 匯入"). No global manifest.
- Install/update: app downloads (app always has network) or receives files, then normalizes to limedb app-side: `.zip` → unzip; `.cin`/`.lime` → app runs the existing text-table import code targeting a standalone single-table limedb (conversion IS validation — parse errors surface immediately in the app UI, and the keyboard can never receive an unparseable text table). The original `.cin` is not kept; the converted limedb doubles as the repair source.
- Uninstall: app deletes the stem's files → keyboard's next scan sees ledger entry without backing file → drops the table.
- Cleanup: none needed. Source files remain while the IM is installed; they double as the app's own record of installed IMs and enable free "repair / re-import" (wipe the keyboard ledger → everything reimports).

### Keyboard import executor

- On every `viewWillAppear`: list `AppGroup/tables/`, diff `(stem, size, mtime)` against a processed-ledger stored in the keyboard's own container. Import whatever differs; drop whatever lost its file. Idempotent — re-delivery or missed signals are harmless.
- Single-format importer: every delivery is a limedb, so the keyboard's only import path is attach-source + bulk row copy (fast — no text parsing in the extension; working set of a few MB, safe under the memory limit). Transactional per chunk with a resume marker for very large tables (e.g. 關聯字庫); the process can be killed at any keyboard dismissal, so the largest imports may complete across sessions.
- Progress/status display lives in the keyboard UI (toolbar/candidate-bar banner: 匯入中… / 已安裝) because the app cannot durably observe progress FA OFF.
- Import success/failure state (FA OFF the app cannot durably know it):
  - **Normalize-and-validate at the proxy**: text tables are converted app-side (see desired-state section) — the conversion is the validation, so an unparseable table can never reach the keyboard. Received/downloaded `.limedb` files are checked app-side before delivery: open read-only (`config.readonly` source pattern already in the import code), `PRAGMA quick_check`, verify expected tables/columns. Invalid files never enter desired state, so keyboard-side failures are only environmental (interruption, disk) — the kind that resolve by resume/retry.
  - Ledger records per-table state: `pending / in-progress / done / failed(error, attempts)`. Deterministic failures do NOT retry the same file identity on every appear; a replaced file (new identity) resets the state. Environmental failures resume with a small attempt cap.
  - Status surfaces: Darwin pings for both outcomes (`org.limeime.import.done` / `.failed`) give the app a live toast when foreground; the keyboard UI is the authoritative surface (匯入中… / 已安裝 / 匯入失敗 banner, failed table absent from the IM switcher); FA ON receipts carry per-table status so the app can show real badges.
  - FA OFF app copy stays honest: sources show as "已交付鍵盤", not installed-and-verified.
- 還原已學習記錄 / 刪除時備份已學習記錄: keyboard-local, FA-free. The keyboard owns both the learned data and the import, so preservation is a local merge in one transaction (import new rows, carry over score/userword values for matching entries; stash lives in its own container/DB). Only the user's *choice* travels via sidecar.

### Signal channels

| Channel | App → Keyboard | Keyboard → App |
|---|---|---|
| App Group files | ✅ backbone (FA OFF ok: keyboard reads) | ✅ FA ON only (receipts, status, snapshots) |
| Scan-on-appear | ✅ guaranteed eventual delivery | — |
| Darwin notification | ✅ instant doorbell, not FA-gated | ✅ live-only ping, not FA-gated (no payload, no persistence) |
| Probe text field | ✅ forces keyboard to load & scan now | — (also forces a fresh heartbeat, see detection doc) |
| Shared UserDefaults | ⚠️ user prefs only (reads work FA OFF; re-read on appear; may be one session stale — not for signaling) | ❌ writes dropped FA OFF |
| Pasteboard / openURL / network | ❌ | ❌ FA-gated or dead for keyboards |

- Names: `org.limeime.tables.updated` (app→kb), `org.limeime.outbox.updated` (kb→app, FA ON), `org.limeime.import.done` (kb→app live toast, works FA OFF while the app is foreground listening — UX garnish only).
- FA ON status uses outbox *files* (receipts, heartbeat), not shared UserDefaults. App keeps its existing 1-second poll.
- Install-flow UX: app writes the table file → posts doorbell → focuses probe field → keyboard loads, scans, imports while the user watches; live done-ping gives the toast. If the app misses everything: "已交付鍵盤，將於下次使用鍵盤時完成" — honest, and correct.

### Conflict resolution — imports vs. restore

No conflicts by construction, via three properties:

1. **Single writer, serialized.** The app is the App Group's sole writer and runs one operation at a time. A restore rewrites the whole desired state atomically from the user's view: `restore.limedb` + reconciled `tables/` in the same operation. Later installs/uninstalls mutate `tables/` on top. The folder at rest is always one coherent snapshot of intent.
2. **Deterministic apply order.** Keyboard scan: (a) if `restore.limedb` identity (size+mtime) differs from the ledger's last-applied restore epoch → swap DB, wipe table ledger, record epoch; (b) then per-table diff on the new baseline. A table file newer than the restore re-imports on top (newer intent); one reconciled by the restore diffs as a no-op.
3. **Destructive-idempotent applies.** Table import starts by clearing that table; restore starts by swapping the whole DB. A superseded half-finished import is obliterated by the successor's first step. Resume markers are keyed to file identity `(stem, size, mtime)` — identity changed or newer restore epoch present → abandon partial work, don't resume it.

Temp+rename writes mean the keyboard never sees a torn file; a file replaced mid-import is redone at next scan, and idempotence makes the redo harmless.

- `restore.limedb` is a persistent epoch baseline, NOT a consumable command. It is never deleted when later installs happen — the app cannot know (FA OFF, no ack) whether the keyboard has applied it yet, so deleting it would race with an un-applied restore and land new imports on the un-restored DB. It is only ever replaced by the next restore (atomic rename = new epoch identity). Cost: one stale file in the App Group — same order as the table sources, the accepted price of ackless correctness.
- The processed-ledger and applied-epoch marker live INSIDE the canonical DB (meta table, updated in the same transaction as each import chunk / swap), not as a separate file. Ledger and DB therefore cannot desync — a lost side-file can never cause an old epoch to be re-applied over learned data, and chunk-commit + ledger-update are atomic for free.
- **Version identity rule: destructive applies key on content, non-destructive applies key on file metadata.** The base DB epoch is a UUID; same UUID as the applied epoch in the canonical DB → no-op, even if mtimes churned (iCloud device restore, filesystem copy) — a whole-DB swap must never trigger on metadata noise. Table stems stay on `(size, mtime)`: a spurious table re-import is non-destructive (clear + reload with learned-score merge), so churn costs CPU, not data.
- **Where the UUID lives:** (1) authoritative — `sync_meta` table inside `restore.limedb`, stamped by the app when PREPARING the file (backup snapshots carry an old `sync_meta` + ledger, so preparation rewrites: fresh `epoch_uuid`, cleared ledger rows); (2) mirror — `restore.meta.json` sidecar written after the DB file, so the scan compares with one tiny JSON read (sidecar missing/suspect → read the in-DB stamp via a safe read-only open; app-written temp+rename, no WAL sidecars); (3) canonical DB — no separate applied-marker write needed: the swap installs the new `sync_meta` (new UUID, empty ledger) as part of the file itself, so "applied epoch" and "current DB" are atomically the same fact. Scan check: sidecar UUID == canonical `epoch_uuid` row. Fresh install: the canonical DB is a copy of the bundled default, whose shipped `sync_meta` UUID serves as the initial epoch; no `restore.limedb` present → no epoch to apply, and no collision is possible because the app always stamps a fresh UUID when preparing a restore file.

### Backup / restore

- **Backup — FA ON.** User taps 備份 → app writes an export-request marker → probe field loads the keyboard → keyboard snapshots its canonical DB into the App Group via `VACUUM INTO` (consistent single-file snapshot while the DB stays open; no WAL sidecar; SQLite ≥3.27 / iOS 13+; issue through GRDB), renames, writes a receipt → app's poll sees the receipt → zips (ZIPFoundation) → share sheet → app deletes the temp snapshot. Whole-DB export: at tens of MB it is a sub-second write, and backup semantics stay identical to Android (backup contains everything, including learned data).
- **Restore — works FA OFF.** App unzips the backup and feeds it through the desired-state folder (whole-DB `restore.limedb`); the keyboard swaps/imports into its own container. Learned data returns because it was inside the whole-DB backup.
- **Restore default DB (還原預設資料庫 / factory reset) — works FA OFF.** Same mechanism: the app delivers the bundled default `lime.db` as the restore file; the swap wipes tables and learned data.
- Consistency on any restore: the app-held table sources ARE the app's installed-set, and every restore begins by deleting all of them (`tables/` stems + sidecars). `restore.limedb` becomes the sole desired state; the keyboard resets its processed-ledger after the swap. After restore the app holds no sources — its installed list is empty and repopulates only through future installs; table files written later import on top of the restored baseline.
- FA OFF backup button: show honest copy ("開啟完整取用權限以備份已學習字詞"), never an error state.

## Product behavior

Never granted Full Access:

- Keyboard types, switches keyboards, uses every installed IM, learns — all normal. Key-click sound available; key haptics unavailable (system restriction).
- Installs/imports/uninstalls/restores made in Settings reach the keyboard through the desired-state folder; applied at next keyboard appearance (or instantly via the probe field during the install flow).
- In-app backup is unavailable; button shows the honest unlock copy.
- Settings sees no durable keyboard status — UI uses the tri-state model (see IOS_FULL_ACCESS_DETECT.md), never claims the keyboard is broken.

Full Access granted:

- Everything above, plus: backup export works, key haptic feedback works, keyboard writes durable receipts/status/heartbeat files, Settings shows confirmed states and instant install feedback.

Full Access later turned off:

- Nothing changes for typing, IMs, or learning — the canonical DB is keyboard-owned and unaffected.
- Backup export, haptics, and durable status stop; Settings UI degrades to neutral copy.

## What Full Access actually gates (final list)

1. Keyboard→App Group writes: backup snapshot export, durable receipts/status/heartbeat.
2. Key haptic feedback (system restriction on keyboard extensions).

That's all. Settings UI copy must present Full Access as a feature unlock ("備份已學習字詞、按鍵震動回饋、即時安裝回報"), never a requirement.

## Planned LIME_SETTINGS.md updates (spec changes — NOT yet applied to that file)

Two UI spec changes follow from this design; apply them to LIME_SETTINGS.md when implementation starts:

1. **Setup tab (§4) — Full Access note.** Current copy says Full Access is only for 按鍵震動回饋 (`SetupTabView.swift:211`). Update the toggle note to list both unlocks and keep the never-required framing: "完整取用用於：備份已學習字詞、按鍵震動回饋。不開啟也能正常輸入與安裝輸入法。" The step row 開啟「允許完整取用」 is marked optional (建議), and the status banner follows the tri-state model (see IOS_FULL_ACCESS_DETECT.md) — never an error state for FA off/unknown.
2. **DB Manager tab (§7) — disable backup when FA is not confirmed ON.** 備份資料庫 button enabled only when FA state = Confirmed ON (fresh heartbeat); otherwise disabled with footnote "開啟完整取用權限以備份已學習字詞". Because FA OFF and never-ran are indistinguishable, the tab triggers the probe-field freshness check on appear so a genuinely-FA-ON user sees the button enable within ~2 s. Restore buttons (還原資料庫 / 還原預設資料庫) stay ENABLED regardless of FA — both work FA OFF by design.

- **Migration from the legacy layout (existing installs).** One-time adoption at keyboard first-run-after-update: canonical DB absent + legacy App Group `lime.db` present → copy the file trio (`db`/`-wal`/`-shm`, all readable FA OFF), open in own container (WAL recovery is legal there), `PRAGMA quick_check`, stamp epoch UUID, adopt as canonical; validation failure → fall back to bundled default. App-side on first launch post-update: clear legacy state; optionally prepare a proper epoch from the legacy DB as a redundant path.
- **Backup version skew.** `sync_meta` carries the schema version. App-side validation rejects backup zips with schema newer than the running app ("請先更新 LIME"); the keyboard also refuses a future-schema epoch. `migrate()` only goes forward.
- **Legacy backups contain static tables.** An old-format backup's in-DB `emoji_data` would shadow the attached bundle DB (unqualified names resolve to `main` first). Rule: emoji/hanconvert queries are always schema-qualified (`emoji.emoji_data`), and post-swap hygiene drops legacy static tables from a swapped-in DB.
- **Concurrent keyboard instances.** iOS runs one extension process per host app; two can be alive across app switches, sharing the canonical DB. Set `busy_timeout`; perform the scan-diff check inside the import transaction so a second instance no-ops (idempotence + ledger already make this safe, this makes it cheap and quiet).
- **Backup during in-progress import.** The keyboard completes or rolls back pending imports before honoring an export request — otherwise the snapshot captures a half-imported table whose resume marker references a source file that will not exist after restoring that backup.
- Non-issues checked: App Group container nil → keyboard runs standalone off canonical DB, sync disabled; FA revoked between backup request and snapshot → app times out on the missing receipt and shows FA guidance; device-level iCloud restore → epoch UUID prevents destructive re-apply, table re-imports are harmless; app uninstall wipes everything by iOS design (in-app backup zip is the recourse).

## Open items

- App-side DB-backed screens (字根資料表 record editor, per-IM counts): the app no longer has a live DB. Decide: move editing into keyboard UI, degrade these screens, or have the app keep a read-model built from the table sources it already holds. If the record editor stays app-side, edits travel as a per-table append-only op-log (`<stem>.ops.json`, ops bound to that table file's identity) — compacted by supersession, not acks: past a size threshold the app rewrites the table limedb with ops folded in and truncates the log (new file identity makes old ops irrelevant by construction). No global transaction file — the epoch UUID already provides stream identity + reset-on-restore, and a global log would reintroduce the unprunable-queue/ack problem.
- On-device probe (step 0, before any build-out): FA OFF, keyboard reads a file from the App Group and writes into its own container — validates the single load-bearing permission assumption on real hardware.
- Verify Darwin notifications are deliverable in the keyboard extension on current iOS (expected yes; not FA-gated).
- Measure import time for the largest table (關聯字庫) on the slowest supported device; tune chunk size for the resume marker.
- Add the bundled `lime.db` copy phase to the LimeKeyboard appex target (fresh-install baseline; only the app target has it today).

## Test matrix

- Fresh install, FA never granted, app never opened: keyboard copies bundled default DB and types (empty IM tables, emoji present, English fallback), globe works; key-click sound plays, no haptics.
- FA OFF, app downloads 倉頡: table file lands in App Group; keyboard imports on next appear (or instantly via probe); IM usable; app shows "交付" state, live toast if foreground.
- FA OFF, kill keyboard mid-import of 關聯字庫: import resumes next session; no corruption (chunk transactions).
- FA OFF, uninstall IM in app: keyboard drops table on next scan.
- FA OFF, re-install IM with 還原已學習記錄: learned scores survive the re-import.
- FA OFF, restore backup zip: tables and learned data return via desired-state swap.
- FA OFF, 還原預設資料庫: keyboard swaps to bundled default DB; tables and learned data wiped; `tables/` folder and ledger reconciled (no stale re-import on next scan).
- FA ON, backup: probe → snapshot → receipt → zip within seconds; learned data included.
- FA ON, haptics enabled in preferences: key press vibrates; turn FA OFF → haptics silently stop, no error.
- FA ON → later OFF: keyboard keeps working on its canonical DB; only backup, haptics, and durable status stop; UI degrades to neutral copy.
