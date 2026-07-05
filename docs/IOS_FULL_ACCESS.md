# iOS Full Access — Permission Model and Architecture

Scope: LimeKeyboard extension and LimeSettings — DB ownership, install/import/backup/restore flows, app↔keyboard signaling, and what Full Access actually gates.

Companion docs: [IOS_FULL_ACCESS_DETECT.md](IOS_FULL_ACCESS_DETECT.md) (enabled / Full Access detection in Settings UI), [IOS_GOTO_SETTINGS.md](IOS_GOTO_SETTINGS.md) (Settings deep-link reliability).

## Bottom line

LimeIME cannot require Full Access for the keyboard to function (App Review Guideline 4.4.1: a keyboard must type, provide the globe/next-keyboard path, and remain functional without Full Access).

Design (v2 — cold/hot model, supersedes the v1 "desired-state folder of per-table files"): the app owns a full **cold DB** (a complete `lime.db` run by the app's own LimeDB/DBServer — all imports, IM-meta changes, and table edits happen there, so every app screen works natively). The keyboard owns its **hot DB** (canonical for typing; the ONLY home of learned scores). The app publishes an atomic snapshot of the cold DB into the App Group; the keyboard tracks it by generation/epoch/per-table revision and updates the hot DB incrementally. With Full Access OFF, everything works — typing, install, import, uninstall, restore, learning, learned-record preservation. Full Access ON unlocks:

1. In-app backup (keyboard exports the hot DB — the sole purpose of backup is the user's learned data; fixed tables are re-downloadable).
2. Key haptic feedback (按鍵震動回饋) — a system restriction: keyboard extensions cannot play haptics without Full Access. Key-click *sound* (`UIDevice.playInputClick()`) works without it.
3. Table-record editing with real data (the editor's hot-snapshot refresh needs the same keyboard→App-Group write as backup). FA OFF the record screens are read-only; see §Editor policy.

Ownership principle that drives the whole design: **`code`/`word`/structure/IM-meta are cold-owned (app is the truth); `score` is hot-owned (keyboard is the truth).** The app never displays or accepts a score value it cannot know to be real.

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

### Cold DB — app-owned (v2)

- The app runs a full `LimeDB`/`DBServer` against its own **cold DB** (the App-Group `lime.db` location it always used). ALL structural mutations happen here exactly as pre-re-arch: downloads, `.cin`/`.lime`/`.limedb`/zip imports (conversion IS validation — parse errors surface in the app UI), IM registration, IM-meta edits, record edits, clears, restores. Every app screen (IM list, detail, counts, editors) reads this DB natively — no replicas, no read-models.
- The cold DB carries **no meaningful learned data**: `score` values in it are base/seed values except immediately after a restore or an FA-ON editing session (see below). The app never displays scores from the cold DB as if they were real.
- Sync bookkeeping inside the cold DB, maintained by the app in the same transactions as the mutations they describe:
  - `sync_rev(stem TEXT PRIMARY KEY, rev INTEGER, mode TEXT)` — bumped when that table's DATA changes. `mode`: `merge` (installs/downloads — keyboard preserves learned scores on re-import) or `replace` (FA-ON editing sessions — cold wins wholesale, because cold was just seeded from hot).
  - `sync_meta`: `epoch_uuid` (bumped ONLY by restore-from-backup / 還原預設資料庫 — the destructive events) + `schema_version`.
  - The `im` table needs no revision — it is mirrored wholesale (≈15 rows).

### Published snapshot — the transport (app → keyboard, works FA OFF)

- After each mutation burst (debounced: flow completion / app background), the app **publishes**: `VACUUM INTO` a temp file → atomic rename to `AppGroup/cold.limedb` → write sidecar `cold.meta.json {generation, epochUUID, schemaVersion}` (generation bumps on every publish). Atomic, self-contained (no WAL sidecars), safe for the keyboard to ATTACH `immutable=1` — the rename means an attached inode never changes.
- One artifact replaces the whole v1 `tables/` folder and `restore.limedb`. Not a queue — the snapshot always states the complete intended cold state; no acks needed for correctness. Publish cost: one whole-DB vacuum per burst (~4–40 MB IO), accepted ceiling.
- Doorbell: Darwin `org.limeime.tables.updated` after publish (name kept from v1 wiring); scan-on-appear remains the guaranteed path.

### Keyboard sync engine (hot ← cold)

Scan on every `viewWillAppear` (+ doorbell), against the hot DB's ledger:

1. Read `cold.meta.json` — one tiny JSON read. `generation == applied` → done (the common case costs nothing else).
2. Generation differs → ATTACH `cold.limedb` (`immutable=1`); verify in-DB generation matches the sidecar (mismatch = app mid-publish → skip, retry next scan).
3. **Epoch differs** → destructive rebuild: hot := fresh copy of the snapshot, learned data handled per the 還原已學習記錄 pref (keyboard-local stash/merge; only the user's *choice* travels — as prefs, readable FA OFF). Ledger reset, epoch recorded.
4. Same epoch → incremental:
   - **im mirror, always**: `DELETE FROM im; INSERT … SELECT FROM cold.im` in one transaction — meta-only changes (titles/versions, endkey, selkey, spacestyle, keyboard id, disable) cost milliseconds and never touch table data. Runtime rebuild fires after the mirror so new meta applies immediately.
   - **Per-stem `sync_rev` diff**: only stems whose rev moved get the clear + chunked attach-copy (20k rows/chunk, resume marker, kill-safe) — in `merge` mode learned scores carry over; in `replace` mode cold wins wholesale. A record-edit session re-imports that one table; nothing else moves.
   - Stems registered in the hot ledger but absent/unregistered in cold → dropped (table cleared + im row removed).
5. Ledger (`applied generation/epoch/revs`) lives INSIDE the hot DB, updated in the same transactions — ledger and data cannot desync.

Status surfaces (unchanged from v1): keyboard banner rides LimeToast (匯入中… / 已安裝 / 匯入失敗, no new chrome over the UIInputView blur); Darwin `org.limeime.import.done/.failed` live pings; FA-ON receipts/heartbeat as durable status; FA-OFF app copy stays honest ("已交付鍵盤").

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

### Conflict resolution & versioning (v2)

1. **Single writer, serialized.** The app is the cold DB's only writer and publishes atomically (temp+rename + sidecar-after). The snapshot at rest is always one coherent statement of the complete cold state — there is nothing else to conflict with.
2. **Deterministic apply order** in the scan: epoch check → im mirror → rev diffs → drops. A snapshot published mid-scan is picked up next scan (attached inode never changes under the engine).
3. **Destructive-idempotent applies.** Epoch rebuild replaces hot wholesale; a rev import starts by clearing that table. Resume markers are keyed to `(stem, rev)` — a newer rev or epoch abandons partial work. Replays are always safe.
4. **Version identity rule: destructive applies key on content, incremental applies key on monotonic revs.** Epoch = UUID in `sync_meta` (+ sidecar mirror; sidecar suspect → read the in-DB stamp) — mtime churn (iCloud device restore, filesystem copies) can never trigger a rebuild. Revs are app-maintained integers, consistent with data by same-transaction construction.
5. Snapshot staleness is self-healing: generation mismatch between sidecar and in-DB value (app mid-publish) → skip and retry; the sidecar is written after the DB file.

### Backup / restore / factory reset

- **Backup — FA ON (unchanged from v1).** 備份 → `ExportRequest {requestUUID, expiresAt}` → probe summons keyboard → hot `VACUUM INTO` App Group + receipt(requestUUID) → app zips (existing layout) → cleanup. Timeout UX disambiguates via Darwin liveness (fa ping but no receipt → FA guidance; no ping → 請切換至萊姆輸入法).
- **Restore from backup — works FA OFF.** App-side: legacy restore into the COLD DB (existing code path — zips contain the whole DB including learned scores, which seed the restored baseline) → validate schema (`請先更新 LIME` gate) → bump `epoch_uuid` → publish. Keyboard: epoch rebuild → hot := snapshot copy. Learned data returns because the backup carried it; from then on learning accrues hot-only again. All app screens show the restored content immediately (they read the cold DB — the v1 "empty IM list after restore" defect is structurally impossible).
- **還原預設資料庫 / factory reset — works FA OFF.** Cold := bundled default, epoch bump, publish; keyboard rebuilds. Learned data wiped by definition of the operation (or preserved per 還原已學習記錄 pref where applicable).
- FA OFF backup button: honest unlock copy ("開啟完整取用權限以備份已學習字詞"), never an error state.

### Editor policy (v2 — column ownership)

- **IM meta (titles/version names, endkey, selkey, spacestyle, keyboard id, enable/disable): cold-owned, editable in EVERY FA state.** Changes ride the im mirror; effective at next keyboard appearance (doorbell makes it immediate when live). No snapshot round-trip needed — there is no hot truth for meta.
- **Table records (字根資料表 / 關聯字庫): read-only FA OFF.** The score column is hot-owned; FA OFF the app cannot know real scores (mostly-0 base values would be lies), and blind edits could silently fight learned state. Read-only browse shows cold data, labeled with last-sync freshness.
- **FA ON: full live editing via snapshot refresh.** Entering an edit screen → app requests a hot snapshot (same relay as backup, on-demand) → refreshes that table in the cold DB from it (real rows, REAL scores) → user edits → save bumps `sync_rev` with `mode=replace` → publish → keyboard re-imports that table with cold winning wholesale (a merge here would clobber the user's score edits with pre-edit learned values). Accepted, documented race: typing on that same table between snapshot and save loses those minutes of its learning.
- Full Access's honest sales pitch is therefore: 備份已學習字詞、按鍵震動回饋、編輯字根資料表（含實際分數）.

## Product behavior

Never granted Full Access:

- Keyboard types, switches keyboards, uses every installed IM, learns — all normal. Key-click sound available; key haptics unavailable (system restriction).
- Installs/imports/uninstalls/restores/IM-meta edits made in Settings reach the keyboard through the published snapshot; applied at next keyboard appearance (or instantly via the doorbell + probe during a flow). All app screens are correct at all times (they read the cold DB).
- Table-record editors are read-only (score is hot-owned); IM-meta editing fully works.
- In-app backup is unavailable; button shows the honest unlock copy.
- Settings sees no durable keyboard status — UI uses the tri-state model (see IOS_FULL_ACCESS_DETECT.md), never claims the keyboard is broken.

Full Access granted:

- Everything above, plus: backup export works, key haptic feedback works, table-record editing goes live (snapshot-refresh flow, real scores), keyboard writes durable receipts/status/heartbeat, Settings shows confirmed states and instant feedback.

Full Access later turned off:

- Nothing changes for typing, IMs, or learning — the hot DB is keyboard-owned and unaffected.
- Backup export, haptics, live record editing, and durable status stop; Settings UI degrades to neutral copy; editors fall back to read-only.

## What Full Access actually gates (final list)

1. Keyboard→App Group writes: backup snapshot export, editor snapshot refresh, durable receipts/status/heartbeat.
2. Key haptic feedback (system restriction on keyboard extensions).
3. Consequences of 1: real-score visibility and therefore table-record editing.

That's all. Settings UI copy must present Full Access as a feature unlock ("備份已學習字詞、按鍵震動回饋、編輯字根資料表"), never a requirement.

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

- **v1→v2 code migration** (the branch currently implements the v1 desired-state-folder transport, all green): retarget TableSyncEngine to the snapshot (generation/epoch/rev/im-mirror, merge|replace), add cold-DB rev/publish plumbing, revert SetupImController/IMStoreView to app-DB imports + publish hook, dissolve TableStore (validation/conversion folds back into app import paths), implement editor policy, delete v1 artifacts handling (`tables/`, `restore.limedb`; v1 never shipped — cleanup is unconditional). Plan: IOS_FA_SETUP_REARCH.md campaign 2.
- Import chunk size 20k rows (`// ponytail:` in TableSyncEngine) — revisit only if device timing shows multi-second chunks.
- DONE in v1 campaign (kept for history): appex `lime.db` copy phase; legacy shared-defaults signals removed; hanconvertv2 iOS phase removed; keyboard-side IM registration; FA tri-state detection; goto-settings variant.

## Test matrix (v2)

- Fresh install, FA never granted, app never opened: keyboard copies bundled default DB and types (empty IM tables, emoji present, English fallback); key-click sound, no haptics.
- FA OFF, app downloads 倉頡: cold import → publish → keyboard rev-imports on next appear (or instantly via doorbell+probe); IM usable; IM list correct immediately.
- FA OFF, rename an IM / change endkey (meta only): publish → keyboard im-mirror only — no table data copied; new meta live at next appear.
- FA OFF, kill keyboard mid-import of 關聯字庫: resumes next session; no corruption (chunk transactions, rev-keyed resume).
- FA OFF, uninstall IM in app: cold drop → publish → keyboard drops table + im row on next scan.
- FA OFF, re-install IM (merge mode) with 還原已學習記錄: learned scores survive the re-import.
- FA OFF, restore backup zip: cold restored (screens correct immediately), epoch bump → keyboard rebuilds hot; tables AND learned data back; typing works.
- FA OFF, 還原預設資料庫: cold := default, epoch bump → hot rebuilt; learned wiped.
- FA OFF, record editor: read-only, freshness label, no score lies.
- FA ON, backup: request → probe → hot VACUUM INTO → receipt → zip; learned data included.
- FA ON, edit a record: snapshot refresh shows real scores → edit → replace-mode rev → keyboard re-imports that table only; edited score effective when typing; other tables untouched.
- FA ON, haptics: vibrate; FA OFF → silently stop.
- FA ON → later OFF: typing/learning unaffected; backup, live editing, durable status stop; UI degrades to neutral copy.

## Implementation-phase addendum (2026-07-04)

The plan above is unchanged. These notes record clarifications and optional extensions from implementation-phase review; none is a prerequisite for the current IOS_FA_REARCH_TASKS.md tasks unless marked.

### Keyboard-side preference edits (hamburger menu) — the FA asymmetry

- The keyboard's long-press options menu writes 簡繁轉換 / 分離鍵盤 / 字根反查 straight to shared UserDefaults (`KeyboardViewController.swift:3903`, `:3907`, `:3937`; `LIMEPreferenceManager.setReverseLookup`). **FA ON: these land durably and the app sees them on its next read — no code change needed.** FA OFF: silently dropped; the change may appear applied inside the live keyboard process (cfprefsd in-process cache) and then evaporates when the extension is killed. Nothing in the current code gates these writes on `hasFullAccess`.
- The opposite direction is always safe: app-side edits of the same keys reach the keyboard in every FA state (re-read on appear). So every pref remains fully usable FA OFF via the Settings app; the in-keyboard edit path is the only broken leg.
- **Optional fix (deliberately NOT in the current tasks):** persist hamburger-editable prefs in the keyboard's own container (always writable) as the authoritative copy, and reconcile back to the app via the probe relay below with last-writer-wins timestamps. Adopt only if FA-OFF durability of three prefs justifies the protocol; the accepted lazy alternative is "hamburger edits are FA-ON-only, app-side edits always work".

### Probe relay (insertText) — a keyboard→app channel FA cannot block

Typing is the keyboard's core function; `documentContextBeforeInput` + `insertText` work in every FA state, on device. That makes the existing probe field a two-way channel:

1. App prefills the probe field with a short magic token and focuses it (existing probe moments: Setup tab, DB tab).
2. Keyboard on appear sees the token in `documentContextBeforeInput` → recognizes its own containing app's sync field → types one compact payload: protocol version, FA bit, timestamp, pending pref deltas.
3. App observes its own field binding (instant, no poll), parses, applies, clears the field, resigns focus.

Constraints: LIME must be the summoned (currently active) keyboard; the keyboard visibly pops up (unavoidable — ride the existing probe moments, or gate on a Darwin "pending edits" ping so it only fires when there is something to sync); keep the token short (`documentContextBeforeInput` truncates around the cursor) and read it at `viewDidAppear`/`textDidChange`, not `viewWillAppear` (context can be nil early); debounce once per appear. The token handshake guarantees the payload is never typed into a real text field.

Status: **design option, not yet validated on hardware.** Needs a step-0 spike (WJIP17: token field → keyboard branch → one `insertText`) before anything is built on it. If adopted, FA detection, heartbeat freshness, and pref write-back merge into one round-trip on the existing probe.

### Darwin name-encoded FA report (cheap, recommended alongside Task 5.1)

Darwin notifications carry no payload, but the *name* is free: the keyboard posts `org.limeime.fa.on` or `org.limeime.fa.off` on appear (it reads `hasFullAccess` directly — no write-attempt inference needed). Live-only, but during a probe the app is foreground and listening by construction. This gives:

- **Confirmed OFF** becomes representable (see detection doc addendum) — a state the heartbeat file can never produce.
- Disambiguation for the backup flow: no Darwin ping during the receipt window → LIME never ran → show "請將鍵盤切換至萊姆輸入法後再試"; ping received but no receipt/heartbeat file → LIME ran and FA is off → show FA unlock guidance. Without liveness these two failures are indistinguishable and the timeout message has to hedge.

### Backup: permission ≠ execution, and request hygiene

- Even FA ON, backup requires a **running LIME instance** — only the keyboard process can read its own container and execute `VACUUM INTO`. The probe summons whatever keyboard is active; if that is Apple's, the request sits until LIME next appears. The user is the fallback switch (globe key), so timeout UX should surface the probe field and the switch instruction rather than only FA guidance.
- `export.request.json` persists in the App Group, so a timed-out request could be honored hours later, producing a snapshot nobody consumes. Stamp each request with a UUID + TTL: the keyboard ignores expired requests; the app accepts only a receipt matching its current request UUID. (Refines Task 4.2; same files.)
