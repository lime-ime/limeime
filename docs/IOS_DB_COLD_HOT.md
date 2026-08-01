# iOS DB Cold/Hot Architecture

Design for LIME's two-database (cold/hot) model on iOS: the **cold** (app-owned) and **hot**
(keyboard-owned) databases, how data flows cold → hot, how backup / restore / factory reset
behave, how the emoji dataset is shipped and upgraded, and how the table editor pulls the
keyboard's latest data before editing.

The **cross-process transport and Full Access permission model** this design rides on — the App
Group channel, Darwin doorbells, the summon probe, the FA-off-safe inbox / relay, and the
keyboard-owned preferences — live in the companion doc
**[IOS_FULL_ACCESS.md](IOS_FULL_ACCESS.md)**. This doc is the **database** layer; that one is the
**communication** layer beneath it.

> §1 and §2 describe the current rearch2 implementation. Older editor-entry
> ownership/probe/receipt notes are retained only in issue history.

---

## 1. Cold/Hot design

### 1.0 Roles

- **Cold DB** — the canonical, app-owned database. Lives in the **App Group**
  container. The Settings app reads and writes it. The shipped default DB and any
  restored backup *become* the cold DB. Cold is the source of truth for IM table
  content.
- **Hot DB** — the keyboard extension's working database. Lives in the keyboard's
  **own** container (`Application Support/LimeIME/lime.db`). The keyboard reads it
  for candidates and writes learning (scores, related / LD phrases) into it while
  typing.

### 1.0.1 Ownership and direction

- Hot is **derived from** cold. On first run the keyboard copies the bundled
  default DB (or adopts a legacy App-Group DB once), then it **syncs from cold**.
- Data flows **cold → hot**. The app is the writer of record. The keyboard's
  learning is local to hot and is pulled back to cold only on demand
  (see §1.4).
- Two sync paths:
  - **Incremental** — per-table refresh while both DBs already share a lineage
    (the running sync engine; reworked separately).
  - **Full replace (epoch)** — the whole hot DB is discarded and rebuilt from
    cold. Used for restore and factory reset (an emoji upgrade is **not** here — it
    rides its own version-gated cold → hot mirror, §1.3).

### 1.0.2 Cross-process channel and Full Access — see IOS_FULL_ACCESS.md

The app and the keyboard are **separate processes** sharing only the App Group container; all
coordination is **file-based** with payload-free **Darwin doorbells**, and **Full Access gates
the keyboard's App Group *writes*, not its reads** — so the cold→hot sync runs FA-off (it reads
cold and writes the keyboard's own container). The full channel inventory (App Group files and
Darwin signal names), the FA permission matrix, the summon probe, the FA-off-safe inbox / relay
transport, and the keyboard-owned prefs all live in
**[IOS_FULL_ACCESS.md](IOS_FULL_ACCESS.md)**. This doc covers only what those messages carry:
the cold/hot database itself. The version markers the channel delivers — `epoch_uuid`,
`generation`, per-table `rev` — are the subject of §1.0.3.

#### Issue #169 phone keyboard preferences are preference cold/hot, not DB cold/hot

`phone_portrait_keyboard_mode` and `phone_landscape_split` deliberately do **not** enter
`sync_meta`, a DB snapshot, or table reconciliation. Their cold/hot contract is:

- **Cold:** App Group `UserDefaults`, written by Settings and included in preference backup/restore.
- **Hot:** keyboard-private `UserDefaults.standard`, seeded once from cold (or migrated from legacy
  `one_hand_mode` / `split_keyboard_mode`) and used for live rendering.
- **App → keyboard:** `PrefInboxRecord.phonePortraitMode` / `phoneLandscapeSplit`; the keyboard
  drains the sequenced inbox into hot before reapplying its layout. Restore pushes both values
  through this same inbox.
- **Keyboard → app:** `RelayPrefState` emits `pp=` / `pls=` in the FA-off-safe text relay;
  `RelayPrefSync.apply` validates portrait values (`0...3`) and writes both values to cold.
- **Backup:** during the FA-on export handshake, `flushHotPrefsToColdForBackup()` copies only the
  active device profile. A phone flushes these two keys; an iPad flushes
  `split_keyboard_mode` / `numpad_anchor`, so one device class never overwrites the other's
  dormant profile.

This is intentionally separate from the database direction rules below: no preference change
bumps `generation`, changes an epoch, or replaces either DB.

### 1.0.3 Sync metadata — the `sync_meta` table (in-DB, not a sidecar)

All sync bookkeeping lives in a **`sync_meta` table inside each DB file**, read and
written by the **sync layer's own SQLite connection** — never a JSON sidecar
(abandoned: SQLite is faster than file I/O and travels atomically with the DB),
never `UserDefaults`, never a Darwin payload.

**The footprint is deliberately tiny:**

| Key | Where | Required? | Meaning |
| --- | --- | --- | --- |
| `epoch_uuid` | cold **and** hot (each DB carries its own) | **yes** | full-replace lineage. Cold stamps a **fresh** one only on a restore/reset; installs never touch it, so an install-only cold has **no `epoch_uuid` (nil)**. |
| `applied_epoch` | hot | **yes** | the cold epoch hot has applied — **decoupled from hot's own `epoch_uuid`**. Hot's own `epoch_uuid` is hot's *identity* (a **random value at bootstrap**, or cold's epoch after a completed full-replace copy), so it is not a reliable applied-marker by itself: an install-only cold is nil while a hot that synced incrementally keeps its bootstrap epoch, so `coldEpoch(nil) != hotEpoch(bootstrap)` would falsely read as a new lineage and full-replace. `applied_epoch` records the applied cold epoch (nil for an install-only lineage) so `nil == nil` ⟺ converged. Cold's lineage is **applied when EITHER** `applied_epoch` **or** hot's own `epoch_uuid` matches cold — the latter is a fast-path so a completed restore copy is never re-copied when its `applied_epoch` stamp was interrupted. |
| `generation` | cold; hot stores `applied_generation` | fast-path | monotonic publish counter — the "anything changed within this epoch?" gate. The **incremental** path edits hot's tables **in place**, so (unlike the epoch) it gets no free atomic marker and keeps an explicit `applied_generation`. |
| per-table `rev` | cold | **yes** | scopes each cold→hot reconcile to the tables the **app** changed, protecting unharvested keyboard learning elsewhere |
| `applied_emoji_version` | hot | emoji gate | the emoji version hot has mirrored (§1.3). Gates the version-gated emoji mirror; kept **outside** the `im` table (where the emoji version itself lives) so the `im.json` publish (§1.5) — which excludes emoji — can never desync the version from the data. |

Everything heavier is **cut**:

- **No ledger state machine** (pending / in-progress / done / failed / attempts /
  resume). `epoch_uuid` + per-table `rev` are the resume markers; every apply is an
  **idempotent replace**, so a crash just re-runs the compare and re-applies what's
  behind. A durable per-stem queue buys only "skip a redundant re-import after a
  crash" — not worth the persistent state. `// ponytail:` add only if device traces
  show crash-resume re-imports hurt.
- **No editor op-log** — the close direction is a state diff (§1.4), not a replayed
  op list.
- **No `schema_version` copy** — the portable schema version already lives in
  LimeDB's `user_version`; the sync layer reads *that*, never a duplicate.

**How the epoch drives apply.** The keyboard opens cold on its own connection, reads
`cold.sync_meta.epoch_uuid`, and compares it to **hot's own** `epoch_uuid`: **differ →
full replace**; same → per-table reconcile for whatever `rev` moved. The full replace is a
whole-file swap, so it **sets `hot.epoch_uuid = cold.epoch_uuid` as part of the copy** —
there is **no separate "applied" stamp to write**, and therefore no two-step window where
the copy lands but the marker doesn't. Because the epoch is **persisted in the DB**, a
keyboard that was FA-off or not running still notices the change on its next launch — no
reliance on catching the live doorbell.

**Why hot's own epoch is a safe marker (no integrity check needed).** The swap copies the
snapshot to a **temp file**, then **atomically renames** it into hot's place (§1.2). So hot
is never a partial file: an interrupted copy leaves hot **untouched** (old epoch, complete
old DB), and the rename only lands after the full copy. `hot.epoch_uuid == cold.epoch_uuid`
therefore **implies a complete copy** — the epoch rides *inside* the file, so it cannot
appear without the data attached to it. Any incomplete / missing / wrong-epoch hot has a
non-matching epoch and is re-applied on the next scan: the compare is **idempotent and
self-healing**, so no `PRAGMA integrity_check` is needed (that would only guard against disk
bit-rot, orthogonal to this design).

#### Historical note: original `sync_meta` layering

The original `sync_meta` layer could be added **without touching `LimeDB.swift`**,
because master LimeDB already shipped the two prerequisites:

- **WAL** — `PRAGMA journal_mode = WAL` ([LimeDB.swift:144](../LimeIME-iOS/Shared/Database/LimeDB.swift#L144)) is
  multi-connection and multi-process safe, so the sync layer's own `DatabaseQueue` to
  the same file reads/writes `sync_meta` concurrently with LimeDB's queue.
- **close + reopen** — `closeForReplacement()` / `openDBConnection()` let the full
  replace swap the hot file and reopen with **existing** methods.

The sync connection still creates additive sync tables with
`CREATE TABLE IF NOT EXISTS`; portable `user_version` stays outside this layer and
`sync_meta` is still never added to migrations. Rearch2 later added bounded
`LimeDB` entry points for atomic data+outbox/fence transactions, but the metadata
table remains sync-owned and versionless. Whole-file restore remains an atomic
rename, with the epoch riding inside the swapped file.

#### Portable Android ↔ iOS: the schema does **not** change

The DB is portable between Android and iOS, and the portable version — LimeDB
`user_version` — is **lock-step at 105** on both (iOS `CURRENT_DB_VERSION = 105`,
Android `LimeDB.java DATABASE_VERSION = 105`). `sync_meta` must never move it:

- It is **additive and sync-layer-owned**, created with `CREATE TABLE IF NOT EXISTS`,
  **never bumps `user_version`**, and is **never** added to `migrate()` / `onUpgrade`.
  Both platforms stay at 105.
- Android's `LimeDB.java` never creates or queries it, and its `onUpgrade` fires only
  on a **version increase** — which never happens — so an iOS DB carrying `sync_meta`
  opens cleanly on Android (extra table ignored), and an Android DB without it opens
  on iOS (sync layer creates it fresh). **Portable both ways.**
- **The epoch is not portable.** A backup restored on another device gets a **fresh**
  epoch stamped by the receiver; an incoming epoch is never trusted (simplest: the
  backup carries user data only, receiver re-stamps on restore).

**Rule:** `sync_meta` is platform-local sync state parked in a **non-versioned side
table** neither platform's migration ever sees — exactly what keeps the portable
schema frozen at 105 while iOS layers cold/hot on top.

### 1.1 Backup — app-initiated, keyboard-produced, **requires Full Access ON**

The goal is to capture the keyboard's **hot DB**, which holds the newest
learning — not the app's cold DB, which lags. Because only the keyboard can read
hot, and only an FA-on keyboard can write into the App Group, backup is a
cross-process handshake gated on FA.

- **Gating — app side.** The 備份 button is enabled **only when FA is
  Confirmed-ON** (probe-on-appear freshness). With FA **off**, backup is not
  offered: there is no way to reach the keyboard's hot data, and backing up stale
  cold would be a silently-incomplete backup.
- **Handshake (FA ON):**
  1. **App** writes `outbox/export.request.json` `{requestUUID, expiresAt (~2 min
     TTL)}` and posts the export doorbell.
  2. **Keyboard** (alive with FA on, on its next scan) reads the request; if
     unexpired, runs `VACUUM INTO` on its **hot DB** → atomic rename to
     `outbox/backup.limedb` → writes `outbox/receipt.json`
     `{requestUUID, epochUUID, at}` → deletes the request.
  3. **App** polls the outbox for a receipt whose `requestUUID` matches its own,
     then zips `backup.limedb` + the preference sidecars → share sheet → cleans
     up the temp files.
- **Timeout — app side.** If no matching receipt arrives within the window, the
  app disambiguates via Darwin liveness: a keyboard FA ping seen but no receipt →
  Full-Access guidance; no ping at all → "switch the keyboard to 萊姆輸入法 and
  retry."
- Backup only **reads**; it mutates neither hot nor cold.

### 1.2 Restore (from backup or factory default) — app-prepared, keyboard-applied

Restore is a **full replace**: the hot DB is wiped and rebuilt from cold, where
cold has been replaced by the restored backup (or the bundled default, for
factory reset). The two sides split cleanly, and **"ringing the bell" is a
concrete channel**, not a metaphor.

**App side — prepare cold (works with FA on *or* off; the app owns cold):**

1. Restore the backup INTO cold (or copy the bundled default for factory reset).
2. **Bring cold fully current — before the bell.** Run every upgrade on cold:
   schema migration (old backup → current `user_version`) **and** the emoji
   refresh (§1.3), plus any other data fix-ups. An old, stale, or emoji-absent
   backup becomes a current-schema, current-emoji cold DB here. (The bundled
   default already ships current, so a factory reset skips this.) The keyboard
   therefore only ever full-replaces from an already up-to-date cold.
3. **Bump the epoch** — write a fresh `epoch_uuid` into cold's `sync_meta`. This is
   the bell: a new epoch means "new DB lineage → full replace required."
4. Publish via `ColdPublisher`: `VACUUM INTO cold.limedb` — the fresh `epoch_uuid`
   (and bumped `generation`) ride **inside** the snapshot's `sync_meta`, so the
   keyboard reads DB-and-epoch as **one atomic file**; there is no separate sidecar
   to half-publish.
5. Post the **`org.limeime.tables.updated`** Darwin doorbell.

**The bell = the `epoch_uuid` in cold's `sync_meta`.** It is durable: the
keyboard notices the changed epoch on its next scan whether or not it caught the
live Darwin notification. The doorbell only makes a live FA-on keyboard scan
*immediately* rather than at its next natural scan (keyboard appear).

**Keyboard side — apply (requires FA ON):**

1. On scan, open cold and read `cold.sync_meta.epoch_uuid`. Cold's lineage is **applied**
   when it matches **either** hot's `applied_epoch` (the decoupled marker) **or** hot's own
   `epoch_uuid` (the self-marking fast-path). Applied to **neither** → run the full replace.
   The replace copies `cold.limedb` to a **temp file**, then **atomically renames** it over
   hot (`copyItem` → `moveItem`); cold's `sync_meta` — epoch included — rides inside that
   file, so the rename **sets `hot.epoch_uuid = cold.epoch_uuid` atomically with the data**,
   and the engine then **stamps `applied_epoch = cold.epoch_uuid`** (nil for an install-only
   cold, so the marker is *removed*).
   **Why two markers.** `applied_epoch` is what makes an **install-only cold** (which has
   **no `epoch_uuid`**) read as converged: `nil == nil`. Hot's own `epoch_uuid` is hot's
   *identity* — a **random value at bootstrap** — so it does not, on its own, mean "I applied
   cold's nil epoch"; comparing it to cold's nil would falsely trigger a full replace that
   wipes hot's applied IMs and forces a redundant re-sync (the empty-picker / 同步中
   regression). Hot's `epoch_uuid` is kept only as the interrupt-safety fast-path below.
   **Interrupt safety (no re-copy, no partial hot).** Killed during the copy → the temp
   file is discarded and **hot is untouched** (still the old, complete DB, old epoch) → the
   next scan re-copies. Killed after the rename but **before** the `applied_epoch` stamp →
   hot's own `epoch_uuid` already equals cold's (it rode in atomically), so the next scan's
   **fast-path** sees them equal and **does nothing** — no redundant re-copy. The old
   "completed-but-unmarked" failure — a redundant multi-second re-copy on the next
   appearance that closed the live connection (wrong layout, no candidates until it
   reopened) — stays **structurally impossible**, because the self-marking epoch and the
   data are one
   atomic file. This is why the restore probe ([IOS_FULL_ACCESS.md](IOS_FULL_ACCESS.md))
   dismissing "soon" no longer strands the
   keyboard: either the swap landed (done) or it didn't (cleanly re-applied), never a
   half-applied re-copy.
2. The full replace is **wholesale**: hot ends up exactly as cold (= the restored
   backup, or the bundled default for a factory reset) — learned IM / related scores,
   `emoji_user`, and every other table come **straight from cold**. There is **no
   stash / merge and no opt-in at DB level**: a restore *is* a return to the backup's
   state, so any learning that post-dated the backup is intentionally gone (the user
   is warned — see FA state below).

> **`還原已學習記錄` / `restoreOnImport` is table-level, not here.** That opt-in is
> keyed **per `tableName`** (`setRestoreOnImport(_, for: tableName)`) and belongs to
> **importing / installing a single IM table** (§1.6): "when I re-install *this* IM,
> keep the scores I've already learned for it." A whole-DB restore offers no such
> choice — the backup already carries its own learned scores, applied wholesale.

**FA state:** the app-side prepare is FA-independent — Settings shows the restored
data immediately. The keyboard-side apply needs FA on to read the App Group; with
FA **off** the restore is **pending** (hot keeps the old data) until the keyboard
next runs with FA on, sees the epoch change, and applies it. The user is warned
that everything will be replaced; losing local state on an explicit restore is
the defined, expected behavior — the same as before the cold/hot split.

### 1.3 Emoji — cold-authoritative, mirrored to hot; the keyboard never reseeds

Emoji lives *in* each database (Android-aligned: `emoji_data` + prebuilt **FTS5**, plus
`emoji_user` for recency). But it is **app-authoritative**: **only the app upgrades emoji**, on
cold, and hot receives it by a **cold → hot mirror**. The keyboard runs **no** emoji upgrade of
its own — its `refreshEmojiDataIfNeeded` is removed on the keyboard side.

- **`emoji_data` (+ FTS5)** is read-only reference data. The **app** reseeds it on **cold** from
  the app-bundled `emoji.db` when the bundle is newer (Android's `refreshEmojiDataIfNeeded`, app
  side only), then publishes.
- **The version stamp lives in the `im` table** (`code='emoji', title='version'`) — it travels
  with `emoji_data`. Hot records the version it has applied in **`hot.sync_meta`
  (`applied_emoji_version`)**, *outside* the `im` table, so the `im.json` export (§1.5), which
  excludes emoji, can never desync the version from the data.
- **`emoji_user`** (recency / usage) is **hot-owned and keyboard-authoritative**
  (`recordEmojiUsage`). It is a normal table carried in backups; the mirror never touches it.
  Rows orphaned by a new emoji set are harmless (they simply don't JOIN).

**The emoji mirror — cold → hot, version-gated, inside the sync:**

- On sync, if `cold.im`'s emoji version ≠ `hot.sync_meta.applied_emoji_version`, the keyboard
  **mirrors emoji from the attached cold snapshot into hot**: copy `emoji_data`, **copy the FTS5
  shadow tables** (no in-process FTS rebuild — the keyboard does no heavy FTS work in the
  extension), and copy the emoji `im` rows; then set `applied_emoji_version`. Otherwise (the
  common case) it is a no-op. *(FTS shadow-table copy is the v2 intent; if it proves fragile
  across FTS versions, fall back to an in-process rebuild — revisitable.)*
- Because it is **version-gated**, the emoji mirror does **not** run on every generation bump —
  only when the app actually upgraded emoji. A plain `im` edit never copies emoji.

**Restore / factory reset:** the app brings cold's emoji current **before the bell** (§1.2), and
the full replace carries `emoji_data` + FTS + the emoji `im` version to hot wholesale; the full
replace stamps `applied_emoji_version` so the version-gated mirror reads converged next scan. An
emoji-absent backup is backfilled on cold (absent = "older than bundle" → seeded).

**Not editable app-side — out of §1.4 scope.** `emoji_data` changes only via the app reseed;
`emoji_user` is written only by keyboard usage and replaced wholesale by a restore. Emoji never
flows through the §1.4 hot → cold table-editor sync.

### 1.4 Table editor sync (DB operation, not JSON)

Cold is authoritative for row existence and explicit editor values. Hot is
authoritative only for keyboard learning that cold has not acknowledged. The old
editor-entry ownership path is removed: no cold suspension, no request/receipt
files, no hidden keyboard probe, no fixed probe delay, and no attached hot→cold
harvest. Record and related editors open live cold immediately and stay editable
whenever cold opens; Full Access affects delivery status only.

**Logical identity.** Mapping rows sync by `(code, word)` and related rows by
`(pword, cword)`, never by `_id`. Keyed updates and deletes address every matching
duplicate row, inserts use `NOT EXISTS`, and legacy `word IS NULL` / `cword IS NULL`
sentinel rows never become row-level sync keys.

**Durable state.** iOS creates additive sync tables with
`CREATE TABLE IF NOT EXISTS`; they never enter `migrate()` and never bump
`user_version` (105):

- `learn_outbox` in hot stores pending learned keys with `observed_rev` and
  monotonic `version`.
- `editor_fence` and `editor_table_fence` in cold store latest app row/table
  intent at a table revision.
- `im_lifecycle_intent` in cold stores revisioned install/delete lifecycle intent.
- `sync_meta.editor_fence_protocol = 1` marks the upgraded fence/outbox protocol.

**Keyboard learning (hot → outbox).** Mapping score updates, learned mappings, and
learned related phrases validate nonempty keys, mutate hot, read the applied cold
revision, and upsert `learn_outbox` in the same hot transaction. Bulk imports do not
produce outbox rows. If outbox recording fails, the hot learning mutation rolls back.
`SearchServer.postFinishInput(completion:)` runs after the serialized learning queue,
so dismissal can schedule a flush after all submitted session learning is durable.

**Editor edits (cold → fences).** `DBServer.performEditorMutation(_:)` is the record
and related editor mutation entry point. It validates the request, resolves old keys
when the UI addressed a row by `_id`, writes cold rows, increments that table's
revision, and writes row/table fences in one cold transaction. Editor close and
Settings background call `publishPendingEditorChanges()` once; publication failure
does not roll back the saved cold edit/fence.

**Lifecycle edits.** `DBServer.performTableLifecycleMutation(_:)` installs,
replaces, or deletes IM tables from a validated staging database and commits table
data, `im_lifecycle_intent`, revision, and table fence atomically. Restore remains
the separate epoch replacement workflow.

**Cold → hot reconcile.** On keyboard appearance, `TableSyncEngine.scanAndApply`:

1. processes restore/epoch replacement;
2. reads the published marker, revisions, fences, and lifecycle intents;
3. applies lifecycle intents in revision order around table-fence-first reconcile;
4. applies row fences newer than the table fence; and
5. commits hot applied epoch/generation/revisions only after data, lifecycle work,
   and any restored outbox state succeed together.

A marked cold revision advance without a matching row or table fence is an invariant
violation after the one-time legacy transition; the keyboard fails closed and leaves
its applied revision unchanged.

**Hot → cold flush.** Dismissal is a fast path and appearance is the retry path. A
keyboard process takes `KeyboardFlushLock` for each bounded delivery batch, reads
outbox key/version/current hot row plus hot's applied epoch in one snapshot, then
opens live cold in a normal write transaction. Inside that transaction it re-checks
the protocol marker and epoch, rejects obsolete learning behind newer fences, and
otherwise updates every matching cold duplicate row or inserts a new learned key
behind the `NOT EXISTS` guard. Cold commits before hot acknowledgements; hot removes
only captured versions, so a concurrent relearn remains pending.

**Upgrade.** The first upgraded app run performs the scoped baseline before publishing
a marked snapshot: compare live cold to the last published snapshot exactly, write
`replace` fences only for revision-ahead or content-different tables, set the marker,
publish, then remove obsolete request/receipt/probe/lifecycle-inbox artifacts. The
first upgraded keyboard lineage runs the one-time transition: fenced/gap tables use
the documented cold-wins path, while gapless hot-only learning is seeded into
`learn_outbox` and delivered after cold carries the marker.

### 1.5 IM metadata (`im` table) — published as `im.json`; the keyboard reads the file, never syncs it

The `im` table (which IMs exist, plus each IM's config: display label, `keyboard` layout,
`selkey` / `endkey` / `spacestyle`, enabled state) is **app-authoritative and never written by
the keyboard**. It is **not copied into hot.** The mirror, and the seq-inbox before it, are both
gone — every copy lagged its source, and that lag was the race (see the
[IOS_FA_OVERLAY.md](IOS_FA_OVERLAY.md) preface). Instead the app **publishes the `im` table as a
small `im.json`** in the App Group, and the keyboard **reads that file** for every `im` lookup —
no SQLite handle on cold, no App-Group DB open, so it is FA-off-safe and `0xdead10cc`-free
(reading file *bytes* is the safe carve-out; opening a shared DB is not).

**Publish — app side (atomic rename, debounced).** On any `im` edit — `updateIMEnabled`
(enable/disable), `setImConfig` (rename), `setImConfigKeyboard` (layout) — or an IM lifecycle
change that adds / removes an `im` row (§1.6), the app serialises cold's `im` rows (excluding
`code='emoji'`) to a temp file and **atomically renames** it over `<AppGroup>/im.json`. Atomic
rename means a concurrent keyboard reader sees either the whole old file or the whole new one —
never a torn write. Publish is **debounced to the edit's commit event** (Save / field-resign /
screen-exit), never per keystroke: a metadata edit may be authored **with LIME itself** (typing
an IM's Chinese name), and the keyboard only ever reads the committed file.

**Format.**

```json
{
  "schemaVersion": 1,
  "generation": 42,
  "im": [
    { "_id": 1, "code": "phonetic", "title": "desc", "desc": "注音",
      "keyboard": "…", "disable": "…", "selkey": "…", "endkey": "…", "spacestyle": "…" }
  ]
}
```

- `schemaVersion` — the JSON shape version; bumped only if the field set changes. **Independent
  of the DB `user_version` (105)** — `im.json` is iOS-local transport, never portable payload.
- `generation` — cold's publish counter (§1.0.3) at write time, so a reader can tell whether
  `im.json` is paired with the content that has synced into hot (the picker gate below).
- `im` — every non-emoji `im` row, columns verbatim. The emoji `im` version row is **excluded**
  — it stays with the emoji data (§1.3).

**Keyboard read — a narrow reader.** The keyboard's `im` reads are served
by a **tiny reader, not a full-protocol decorator**. `ImConfigReading` is a **narrow 3-method
protocol** — `getImConfig`, `getImConfigList`, `getAllImConfigs` — that `LimeDB` already satisfies
(`extension LimeDB: ImConfigReading {}`, retroactive, no `LimeDB` change). `ImJsonLimeDB:
ImConfigReading` answers those three from a parsed, cached `im.json` (reload on the file's
mtime / size change), and forwards `code='emoji'` lookups **and** the absent-file case to a
**fallback `LimeDB`** (hot's own `im`, §1.3). It lives in `SyncContract.swift` (no separate file)
and is deliberately **not** a `LimeDBProtocol` decorator — that would mean ~60 forwarding stubs for
methods no `im` read needs.

`DBServer` exposes **`imConfigSource: (any ImConfigReading)?`** — on the **keyboard** the
`ImJsonLimeDB` reader (fallback = the live hot `LimeDB`), on the **app** the concrete cold
`datasource`. Its three `im`-config proxies (`getImConfig` / `getImConfigList` /
`getAllImConfigs`) **and the picker** (§1.7) read through it, so the picker's IM set is `im.json`
on the keyboard and cold's `im` on the app. **`DBServer.datasource` stays concrete `LimeDB?`** (it
still calls LimeDB-only methods like `updateIMEnabled`), and **`LimeDBProtocol` is untouched** —
`getAllImConfigs` is **not** added to it.

The keyboard's runtime `im` reads (imkeys / endkey / layout) went through `SearchServer.getImConfig`.
`SearchServer` keeps the real hot `LimeDB` (**not wrapped**), so those **three
call sites in `KeyboardViewController` are rerouted to `DBServer.getImConfig`** instead, which hits
the reader. `SearchServer`'s own internal `getImConfigList` calls are Settings-only (never reached
on the keyboard), so hot's `im` going stale there is harmless.

Result: hot's `im` table is **never read on the keyboard side** (and never written — the mirror is gone).
Rearch2's bounded `LimeDB` and `SearchServer` changes are listed in §2.

> **Why override all three, not just `getImConfigList`.** `getAllImConfigs` lives on the concrete
> `LimeDB` and **self-calls `getImConfigList(nil,nil)` internally** — a call a frozen `final` class
> cannot redirect. Overriding only `getImConfigList` would leave the picker reading hot's stale
> `im` through that internal self-call, so `ImConfigReading` overrides all three. Grouping stays
> app-side: `ImJsonPublisher` calls cold's real `getAllImConfigs()` and ships the grouped
> `configs` in `im.json`, so the keyboard never re-implements the KV-schema grouping.

**App side keeps the DB path.** `imConfigSource` is the reader **only on the keyboard**. In the app
process it is the concrete cold `datasource`: `im` reads / writes hit cold's `im` table directly
(the app is the writer of record), and the app then re-publishes `im.json`. No reader app-side.

**Picker alignment.** `im.json` can list an IM whose **content table** has not yet synced into hot
(content still flows cold → hot separately, §1.4 / §1.6). The picker currently filters on the
`enabled` flag **alone** — parity with the prior picker — and does **not** yet guard each IM on
`tableHasData` (deferred, `// ponytail:`; content applies before the picker in the same scan, so the
skew window is nil in practice — add the guard if device traces show it). The `generation` stamp is
carried for that future skew check. Either way the IM set is a single fresh `im.json` read with no
second in-hot copy to drift — which is what structurally closes the "installed-first IM disappears"
class.

**FA-off-safe / fallback.** Reading `im.json` is a plain file read of the App Group container —
allowed FA-off, exactly like the cold snapshot the sync already `ATTACH`es each scan. File absent
(fresh keyboard / App Group unavailable) → the reader **falls back to the wrapped `LimeDB`**
(hot's bundled-default `im`), so a never-published keyboard still boots its default IM.

Installing / importing / deleting an IM changes table **content** — that is §1.6, unchanged — and
*also* rewrites `im.json` (the `im` row add / remove).

### 1.6 IM-table lifecycle — install / import / delete (app-driven, cold → hot)

An IM's **content** tables (its code table + related phrases) are **installed,
re-imported, and deleted from the app side only** — the keyboard never adds or removes
an IM. Each is a cold-side change that syncs cold → hot on the bell, with optional
learned-data preservation handled by the **existing `<table>_user` backup mechanism,
reused as-is and run entirely on the hot side.**

**Reuse — the `<table>_user` learned-data backup (do NOT reinvent).** LimeDB already
ships it, and it operates on whichever DB it is called on — so it runs on **hot**, where
the learning is:

- `backupUserRecords(t)` — `CREATE TABLE t_user AS SELECT * FROM t WHERE score > 0`
  (the learned rows), an **in-DB** table in hot;
- `checkBackupTable(t)` — is `t_user` present with data?
- `restoreUserRecords(t)` — re-applies each backed-up row via `addOrUpdateMappingRecord`;
- `dropBackupTable(t)` — clears `t_user`.

Because it lives in hot as `t_user`, there is **no App Group stash file** and no new
storage — the backup captures the freshest learning and survives until restored.

**1. Install / import a table** — restore-on-import opt-in (`還原已學習記錄`):

1. **App** writes the base into cold's table `t` (a new IM is empty in hot), adds the
   `im` row (§1.5), bumps `t`'s `rev`, rings the bell.
2. **Keyboard** reconciles `t` cold → hot (a full copy for a new table) — reads cold,
   writes hot, so it runs **FA-off** like the rest of the sync path.
3. **If restore-on-import is ON and `checkBackupTable(t)`** (hot has `t_user`), run
   `restoreUserRecords(t)` on hot, then `dropBackupTable(t)`. The restored keys are
   journaled to `learn_outbox` and reach cold through the normal flush (§1.4). Off /
   no backup → base scores.

**2. Delete a table** — back-up-learned opt-in (true / false):

1. **App** clears cold's table `t`, removes its `im` row (§1.5), bumps `t`'s `rev`, and
   rings the bell carrying the backup flag.
2. **Keyboard**: if the flag is ON, `backupUserRecords(t)` on hot (→ `t_user`),
   then clear hot's `t`. If OFF, clear outright — learning is gone. Both are hot-side
   writes, so this runs **FA-off**.

**3. Delete then re-import** — the `t_user` backup, living in hot, bridges the two even
across FA-off: the delete creates it, the re-import consumes it (on its next FA-on scan
the keyboard runs the pending delete's backup → clear before the import's copy →
restore).

The base moves **one-way cold → hot**; learned data is preserved only by the reused
hot-side `<table>_user` mechanism — no new stash, no App Group learned-data file, and no
`LimeDB` / `SearchServer` change (`backupUserRecords` / `restoreUserRecords` already
exist). Delete and import are `DBServer` / controller operations that bump `rev` and
ring the bell, like every other §2.5 app-side change.

**The `rev` apply-gate (DB-content half).** A lifecycle intent is applied **only when its
table's `rev` moves** — once hot's `rev` matches cold's the table is skipped, so stale
intent is **never re-applied** (this is why a restore-to-default then reinstall cannot
resurrect a wiped IM's learning). Lifecycle delivery is the revisioned
`im_lifecycle_intent` cold table (§1.4), not the removed unversioned App Group inbox.

### 1.7 Keyboard runtime: the query table follows the active IM

The keyboard's candidate query resolves its table from **`LimeDB.currentTableName`** — a
single mutable field on the shared, per-process `LimeDB` (`db.getMappingByCode` reads it;
a `SearchServer`'s own `currentTableName` is only a cache key). So **whichever call last
ran `db.setTableName` wins**, and the invariant the runtime must hold is:

> **The query table always equals the active LIME keyboard's IM (`activeIM`).** It is
> never re-derived from the cold `keyboard_list` pref on a re-open, and never reset to a
> "first activated" default while an IM is active.

**The bug this closes.** `prepareKeyboardRuntimeDatabase` builds a `SearchServer` and
seeds it to the **first activated IM** (`firstNick`); `SearchServer.setTableName` also
sets the shared `LimeDB.currentTableName`. `triggerSyncScan` used to call `prepareKeyboardRuntimeDatabase()`
on **every** keyboard appearance purely for DB-readiness and discard the result — but the
shared-table side effect stuck. Restore a DB with `cj4, dayi, phonetic`, type in **Dayi**,
dismiss, re-open: the layout stays Dayi (driven by `activeIM`) while candidates come from
**cj4** (the `firstNick` clobber), because nothing re-asserted the active table.

**Rule 1 — a no-op appearance does nothing.** On every appearance `triggerSyncScan` only
ensures the hot DB file exists (`DBServer.ensureDatabaseFile()`, a no-op when it already
does) and runs `scanAndApply()`, whose own generation/epoch check returns `false` fast
when cold and hot agree. **No `SearchServer` is rebuilt and the shared table is never
touched** when there is nothing to sync — so `activeIM` and the query table stay put. The
heavy `prepareKeyboardRuntimeDatabase` runs **only when a sync actually applies**.

**Rule 2 — a sync that applies reconciles the active IM.** When `scanAndApply()` returns
`true` the hot IM set changed (install / delete / restore), so the keyboard rebuilds its
runtime (`setupDatabase`, a new `SearchServer` on the new DB) and reconciles the active IM
against the **freshly-activated list**:

- **Current IM survived** → keep it; the new `SearchServer`'s table is set to the current
  `activeIM`, so the user stays on their keyboard and the query follows it.
- **Current IM is gone** (its table was removed / disabled by the sync) → switch to the
  **first available** IM, set the query table to it, persist it as `keyboard_list`, and
  re-apply the layout. `KeyboardViewController` now holds the new `activeIM` — it **knows
  the original active keyboard is gone**.

The reconcile keys off the **live `activeIM`** (in-memory, authoritative), not the cold
`keyboard_list` pref; the pref is read **only** on cold start, when no IM is active yet,
to restore the last-used IM. The fix lives in `DBServer` (the readiness/rebuild split) and
the keyboard-side `setupDatabase` reconcile; the candidate query itself stays outside
the sync contract (§2).

### 1.8 Cross-process reopen — the probe and Safari are different keyboard processes

iOS runs a **separate keyboard-extension process per host app**: the sync probe
([IOS_FULL_ACCESS.md](IOS_FULL_ACCESS.md)) summons
the keyboard **inside the Settings app**, but when the user switches to Safari that is a
**different, independently-lived process** of the *same* extension, sharing the *same* hot
`lime.db` file. The full replace (§1.2, `replaceDatabaseFromSnapshot`) is a **move** — it
unlinks the old `lime.db` and renames a fresh copy into place, so the file gets a **new inode**.
The process that *does* the replace reopens its own `LimeDB` (the `defer` in
`replaceDatabaseFromSnapshot`), but any **other warm process** still holds a GRDB queue bound to
the **old, now-unlinked inode** → every read returns **zero rows → empty IM picker**. This is
issue **#86**, but *across processes* rather than across apps.

The trap is that the other process's `scanAndApply` opens **fresh** connections, so it reads the
swapped-in file and reports **converged** (`applied == false`) — the "nothing changed, skip the
rebuild" path (§1.7). That path must therefore also check `DBServer.hotFileReplacedSinceOpen()`
(the on-disk `lime.db` inode vs. the one the live datasource was opened against). When it
differs, the keyboard **`reopenDatabaseFromDisk()` + reloads** (`setupDatabase`) even though the
sync itself was a no-op — rebinding the warm datasource to the file another process swapped in.
The check is self-healing under races (whoever moved last wins the path; every stale process
notices on its next appearance) and is the reason "restore → switch to Safari" no longer shows
`同步中` + no active IM.

---

## 2. Scope and Boundary

§1 is the design; **this is the contract every part of it obeys**. The old absolute
freeze on `LimeDB.swift`, `LimeDBProtocol.swift`, and `SearchServer.swift` is
superseded by the accepted rearch2 design. Those files are no longer forbidden, but
their changes stay bounded to atomic learning journaling, editor/lifecycle mutation
transactions, and serialized learning completion. Candidate query behavior, relay/FA
detection, Android, seeded assets, and portable schema version remain out of scope.

### 2.1 What the cold/hot layer owns

The layer lives in these iOS files, plus the App Group relay files (§1.0.2):

| File | Responsibility |
| --- | --- |
| `LimeDB.swift` / `LimeDBProtocol.swift` | atomic hot learning + outbox writes; atomic cold editor/lifecycle mutations; additive sync tables created with `CREATE TABLE IF NOT EXISTS`; no `user_version` bump |
| `SearchServer.swift` | one private serial learning queue and `postFinishInput(completion:)` ordering only; no candidate-query or learning-decision change |
| `DBServer.swift` | app-side editor/lifecycle entry points, scoped baseline, publication, restore/epoch orchestration, `im.json` publication |
| `ColdPublisher.swift` | cold snapshot (`VACUUM INTO`) and `sync_meta` generation/epoch publication |
| `TableSyncEngine.swift` | epoch restore, cold fences/lifecycle reconcile, hot learning flush, hot rebuild, legacy transition |
| `SyncContract.swift` | App Group paths, Darwin signal names, fence/outbox/lifecycle contracts, `KeyboardFlushLock`, `im.json` codec/reader/publisher, relay/FA types |
| Settings controllers/views | route record/related edits through `DBServer` and publish on exit/background without a keyboard entry gate |

### 2.3 The rule

Every synchronization state change is a database transaction on the database that owns
that state:

- keyboard learning writes hot row + `learn_outbox` together;
- app editor mutations write cold row/table data + revision + fence together;
- table lifecycle mutations write cold table data + lifecycle intent + revision + fence
  together;
- learning flush validates marker and epoch inside the cold transaction, commits cold
  first, then acknowledges captured hot versions.

No silent hot-only learning, no cold edit without fence, no wall-clock conflict
resolution, no `_id` sync identity, and no new `try?` swallowing on changed write paths.

### 2.4 Why no hooks are needed (the invariants that make the boundary free)

The sync stays bounded because authority is split:

- Cold owns row existence, explicit editor values, table lifecycle, and `im` metadata.
- Hot owns unacknowledged learning only.
- Fences reject stale learning by observed revision, not by arrival time.
- Table fences are the only normal path that clears unfenced hot-only rows.
- Restore/epoch replacement intentionally discards old-lineage pending learning, and the
  epoch check prevents delivery into the restored cold DB.

### 2.5 Where rev / generation bumps live

- App-side table-changing operations — install, import, delete, editor mutation — are
  orchestrated by `DBServer` / its controllers and bump the per-table revision inside
  the same cold transaction as the data/fence/lifecycle intent.
- Restore is epoch replacement, not an editor/lifecycle mutation.
- **`im` has no inbox and no `seq`** — it is published as `im.json` (§1.5). An IM meta edit
  rewrites cold's `im`, **`publish()`es** (a generation bump, debounced to the edit's commit
  event), and re-writes `im.json`; the keyboard reads the file fresh. Nothing to bump or GC for
  metadata.
- Keyboard learning bumps no app revision; it reaches cold only via `learn_outbox`
  flush (§1.4).

### 2.6 Non-goals

- No JSON learned-score file path, and **no JSON sidecar for sync bookkeeping** — epoch /
  generation / per-table rev live in the in-DB `sync_meta` table (§1.0.3), never a file.
  (`im.json` (§1.5) is a different thing: the app-authoritative `im` *table* published as a plain
  file the keyboard reads — IM metadata, not sync bookkeeping.)
- No editor refresh request/receipt files, cold suspension/resume, hidden keyboard probe,
  fixed probe delay, attached hot→cold harvest writer, or refresh-failure read-only
  editor state.
- No `SearchServer` dirty notification or callback beyond the existing completion barrier.
- No candidate-query changes.
- No Apple active-keyboard API / `UITextInputMode` KVC.
- No Android source change, seeded-asset change, unique-index creation, or duplicate cleanup.
- No wall-clock merge heuristic, per-keystroke cold write, or editor wait for the keyboard.
- **No `im` inbox / cursor / GC**, and **no hot `im` mirror** — `im` is published as `im.json`
  and read via the `ImConfigReading` reader (§1.5), so the seq-cursor delivery apparatus and the
  wholesale mirror are both gone.
- **No `user_version` bump for sync tables** — the portable schema stays at 105.
- No fence/lifecycle GC protocol in this PR; row counts and file growth are tracked in
  `docs/BACKLOG.md` until device data justifies it.

UI states, the editor flow, and the delta strategy live in §1.4 — not duplicated here.
