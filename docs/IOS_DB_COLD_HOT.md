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

> §1 is the current architecture design. §2 retains the original table-sync-only
> spec verbatim.

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

#### Freeze-safe: no LimeDB modification

`sync_meta` is added **without touching `LimeDB.swift`**, because master LimeDB
already ships the two prerequisites:

- **WAL** — `PRAGMA journal_mode = WAL` ([LimeDB.swift:144](../LimeIME-iOS/Shared/Database/LimeDB.swift#L144)) is
  multi-connection and multi-process safe, so the sync layer's own `DatabaseQueue` to
  the same file reads/writes `sync_meta` concurrently with LimeDB's queue.
- **close + reopen** — `closeForReplacement()` / `openDBConnection()` let the full
  replace swap the hot file and reopen with **existing** methods.

So the sync connection creates `sync_meta` with `CREATE TABLE IF NOT EXISTS`; LimeDB
never sees it (it touches only its own tables + `user_version`) and never drops it.
The table rides **in the file** but sits **outside the LimeDB class's concern** — the
§2 "own connection, invisible to LimeDB" contract, achievable *because* of WAL +
close/reopen. The lone thing that would force a LimeDB change — an epoch bump atomic
in the **same transaction** as a LimeDB data write — is never needed: restore is a
whole-file swap (atomic by rename), and the epoch rides **inside** the swapped file, so it
is applied atomically with the data — no post-swap stamp.

#### Portable Android ↔ iOS: the schema does **not** change

The DB is portable between Android and iOS, and the portable version — LimeDB
`user_version` — is **lock-step at 104** on both (iOS `CURRENT_DB_VERSION = 104`,
Android `LimeDB.java DATABASE_VERSION = 104`). `sync_meta` must never move it:

- It is **additive and sync-layer-owned**, created with `CREATE TABLE IF NOT EXISTS`,
  **never bumps `user_version`**, and is **never** added to `migrate()` / `onUpgrade`.
  Both platforms stay at 104.
- Android's `LimeDB.java` never creates or queries it, and its `onUpgrade` fires only
  on a **version increase** — which never happens — so an iOS DB carrying `sync_meta`
  opens cleanly on Android (extra table ignored), and an Android DB without it opens
  on iOS (sync layer creates it fresh). **Portable both ways.**
- **The epoch is not portable.** A backup restored on another device gets a **fresh**
  epoch stamped by the receiver; an incoming epoch is never trusted (simplest: the
  backup carries user data only, receiver re-stamps on restore).

**Rule:** `sync_meta` is platform-local sync state parked in a **non-versioned side
table** neither platform's migration ever sees — exactly what keeps the portable
schema frozen at 104 while iOS layers cold/hot on top.

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

When the table editor opens with Full Access on and LIME the active keyboard,
Settings must show the keyboard's **latest** rows — real scores *and* any
newly-learned rows — before allowing edits. Cold can lag hot, so a **hot → cold
refresh of the requested table** runs first.

**This is a database operation, not the old JSON learned-score diff.** The
abandoned path serialized `score != 0` rows to `learned-scores.json`, parsed them
back, and applied per-row `UPDATE … WHERE code=? AND word=?`. That is slow
(file I/O + per-row statements) and *scores-only* — it cannot reflect rows the
keyboard learned.

**Access matrix — why this is keyboard-side.** The **app** can open **cold only**
(App Group); it has **no access to hot** (the keyboard's private container). The
**keyboard**, with **FA on**, can open **both** — hot (its own container, always)
and cold (App Group). So the hot → cold copy **runs entirely on the keyboard
side**; the app can only request it and read the result. **FA on is required** —
with FA off (or LIME not active) the editor stays read-only (§2 UI state).

Flow:

1. **App — request + show the syncing UI.** On editor entry (FA-on + LIME-active)
   the app posts a refresh request for table `t` (outbox request + doorbell) and
   enters the **syncing state for the whole duration of the keyboard-side copy**:
   `clock.arrow.circlepath` icon, `同步中...` text, gray skeleton rows, and
   add / edit / delete / tap all disabled (§2 "UI State"). It cannot do the copy
   itself — no access to hot — so it just waits.
2. **Keyboard — the delta copy (FA ON, both DBs open).** The keyboard already has
   **hot** open; with FA on it **attaches cold** (App Group) onto that same
   connection and, in **one transaction**, upserts only the rows that differ.
   Because the keyboard is **add/update-only**, "differ" is exactly *new* or
   *score-changed*, recovered with one `LEFT JOIN`: new rows are unmatched
   (`cold.code IS NULL`), learned rows have a moved score
   (`cold.score <> hot.score`) — `INSERT OR REPLACE` those into `cold.t` by
   `(code, word)`. It then detaches cold and posts `import.done` / `import.failed`.
3. **Result — cold matches hot** for the requested table: new learned rows added,
   changed scores updated, unchanged rows left untouched (not rewritten), and no
   deletions to reconcile (the keyboard never deletes).
4. **App — reload + unlock.** On `import.done` the app **leaves the syncing
   state**, reloads the table from cold, and enables editing (success text
   `完整取用已開啟，碼表編輯功能已啓用。`). On `import.failed` or a timeout it stays
   **read-only** with the guidance state, never the editor.
5. **During editing — just edit cold.** Every add / edit / delete lands in cold's
   table `t` normally. **No edit log, no per-edit publish, no op tracking** — the
   state of cold *is* the record.
6. **On editor close — bump the rev and ring the bell.** The app bumps table `t`'s
   `rev` and posts `org.limeime.tables.updated`. That is all the app does; it names
   *which* table changed, not *how*.
7. **Keyboard reconciles `t` by state diff (FA on).** Seeing `t`'s rev move, the
   keyboard attaches cold and, matching by **logical key** (`code, word` /
   `pword, cword`, never `_id` — the copy reassigns rowids), makes **hot's `t` match
   cold's `t`** in one transaction — insert cold-only rows, update changed rows,
   **delete hot-only rows** — then records `t`'s new `rev` as applied so a later cold
   reconcile never reverts it. The delete is unambiguous here (see the strategy
   below), so `t`'s edits (including deletions) land with **no op log to replay**.
   Idempotent, so a crash-then-retry is safe.

**Strategy — both directions are a pure state diff; no tracking either way.**

- **Entry (hot → cold): harvest the delta from state.** The app never saw the
  keyboard's learning, but it doesn't need to — with both DBs attached, one
  `LEFT JOIN` yields it: new rows are unmatched, learned rows have a moved score, and
  add/update-only means there are **no deletions to detect**.
- **Close (cold → hot): a state diff too — because entry re-mirrored first.** Every
  session *starts* by syncing cold from hot (entry), so cold begins as an exact
  mirror of hot, and the keyboard is **not learning into hot while the app editor is
  open** — a keyboard extension only runs as some app's active input view, and the
  editor drops to read-only the moment the app backgrounds (exactly when hot *could*
  start moving). So at close `hot(now) == hot(@entry) == cold(@entry)`, and
  `diff(cold, hot)` is **exactly the app's edits**: a hot-only row means the app
  **deleted** it, never that the keyboard learned it. The delete-vs-learn ambiguity
  that once justified an op log **only exists if hot mutates mid-session — and it
  doesn't.**

So: **pure state diff both ways, no edit log, no op replay.** The per-table `rev`
scopes the close reconcile to the edited table, so it never touches unharvested
learning elsewhere.

**Ceiling (`// ponytail:`).** The one residual interleave — open editor → edit →
background the app → type in another app so LIME learns into hot → return → close —
is closed cheaply by **commit-on-background**: flush the cold → hot reconcile when the
app backgrounds, so no pending edits straddle a keyboard-learning window. Add a
tracked op log **only** if device traces later show this edge bites.

The sync boundary stays in `DBServer` **on the keyboard side**. No `SearchServer`
callback, no dirty hook in IM / learning logic (see §2 "Hard Boundary").

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
  of the DB `user_version` (104)** — `im.json` is iOS-local transport, never portable payload.
- `generation` — cold's publish counter (§1.0.3) at write time, so a reader can tell whether
  `im.json` is paired with the content that has synced into hot (the picker gate below).
- `im` — every non-emoji `im` row, columns verbatim. The emoji `im` version row is **excluded**
  — it stays with the emoji data (§1.3).

**Keyboard read — a narrow reader, `LimeDB.swift` frozen.** The keyboard's `im` reads are served
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
`SearchServer` keeps the real hot `LimeDB` (§2.2, **unchanged** — not wrapped), so those **three
call sites in `KeyboardViewController` are rerouted to `DBServer.getImConfig`** instead, which hits
the reader. `SearchServer`'s own internal `getImConfigList` calls are Settings-only (never reached
on the keyboard), so hot's `im` going stale there is harmless.

Result: **`LimeDB.swift` and `SearchServer.swift` stay byte-for-byte frozen (§2.2)**, and hot's
`im` table is **never read on the keyboard side** (and never written — the mirror is gone).

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
   `restoreUserRecords(t)` on hot, then `dropBackupTable(t)`. The restored scores reach
   cold on the next editor-entry harvest (§1.4). Off / no backup → base scores.

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

**The `rev` apply-gate (DB-content half).** A lifecycle record is applied **only when its
table's `rev` moves** — once hot's `rev` matches cold's the table is skipped, so a lingering
record is **never re-applied** (this is why a restore-to-default then reinstall cannot
resurrect a wiped IM's learning). That gate is the only lifecycle-specific piece; **delivery
and cleanup are the shared FA-off-safe inbox transport** ([IOS_FULL_ACCESS.md](IOS_FULL_ACCESS.md)) —
the keyboard reads, never deletes, and the app GCs on the next probe.

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
sets the shared `LimeDB.currentTableName` (the two files stay frozen — §2.2 — so this
coupling is a given). `triggerSyncScan` used to call `prepareKeyboardRuntimeDatabase()`
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
the keyboard-side `setupDatabase` reconcile — **`SearchServer.swift` / `LimeDB.swift` stay
frozen (§2.2).**

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

§1 is the design; **this is the contract every part of it obeys** — what the cold/hot
layer may touch, and what it may not. Cold/hot is **DB maintenance**; it must stay
outside IM query and learning logic.

### 2.1 What the cold/hot layer owns

The layer lives **only** in these files, plus the App Group relay files (§1.0.2):

| File | Responsibility |
| --- | --- |
| `DBServer.swift` | orchestration + the boundary API the app/keyboard call; `publishImJson()` (app) and `imConfigSource` — the keyboard-side `im.json` reader (§1.5) |
| `ColdPublisher.swift` | cold snapshot (`VACUUM INTO`) + the `sync_meta` stamp (epoch / generation) |
| `TableSyncEngine.swift` | cold → hot import, epoch / generation / per-table rev, the §1.4 editor state diff, the **§1.3 version-gated emoji mirror** (the `im` table is **not** synced — §1.5 publishes `im.json`) |
| `SyncContract.swift` | App Group paths (incl. `im.json`), inbox / outbox shapes, Darwin signal names; **and the `im.json` layer** — codec, the `ImConfigReading` reader (`ImJsonLimeDB`), and `ImJsonPublisher` (§1.5) |

Everything the design needs — `sync_meta` (epoch / generation / per-table rev), the
§1.4 editor state diff (both directions), the §1.5 `im.json` publish + reader, the §1.3 emoji
mirror, backup / restore, emoji seed / upgrade orchestration — is owned here. This layer opens its
**own** GRDB connection(s) to the hot and cold DB files (WAL makes that safe alongside
LimeDB's queue; §1.0.3) and sets its own `busy_timeout`; it does **not** borrow the
keyboard's live `LimeDB` connection, and it creates `sync_meta` **invisibly to
LimeDB**.

### 2.2 Frozen files — do NOT modify

**`LimeDB.swift` and `SearchServer.swift` are frozen at HEAD.** The sync layer adds
nothing to them:

- **`SearchServer.swift`** — the IM query + learning engine. **No** `scoreDidChange`,
  **no** dirty callback, **no** learning-queue change, **no** `syncMode` overloads. It
  keeps learning into hot exactly as today and is **oblivious to sync**.
- **`LimeDB.swift`** — the primitive DB-access layer. **No** `sync_rev` / `ledger` /
  `epoch` methods, **no** rev-bump inside `addRecord` / `updateRecord` / `deleteRecord`.
  It stays a plain CRUD + query + learning-write layer. The `sync_meta` table lives in
  the DB *file* but is created and used **only by the sync layer's own WAL connection**
  (§1.0.3) — LimeDB has no `sync_meta` code and **never bumps `user_version`** for it,
  so the portable Android / iOS schema stays lock-step at **104**.
- Off-limits paths: candidate search, score learning, related / LD / runtime-phrase
  learning, emoji query.

If a task appears to need a change in either file, that is a **red flag** — stop and
justify it against §2.4 before touching them.

### 2.3 The rule

`SearchServer` keeps learning into hot normally. The sync layer must **observe and copy
DB state after the fact** — it must never add a callback or dirty hook inside IM logic.
**The boundary is `DBServer`.** Change is discovered from **DB state** and
**DBServer / controller-level `rev` bumps** — never by instrumenting the learning paths.

### 2.4 Why no hooks are needed (the invariants that make the boundary free)

The sync stays outside IM logic *because* the data model is constrained — all three are
verified in code:

- **Keyboard is add/update-only** — hot's changes are recoverable by a state diff
  (§1.4 entry `LEFT JOIN`), so hot → cold needs no rev-hook in the learning path.
- **All deletes are app-editor-originated**, and entry re-mirrors cold from hot each
  session (hot frozen while the editor is open), so cold → hot is a plain **state
  diff** — a hot-only row is unambiguously an app delete. No op log, no `LimeDB` hook.
- **The `im` table is app-write-only** (§1.5) — the keyboard never writes it; its metadata is
  published as `im.json` and read through a narrow `ImConfigReading` reader (`DBServer.imConfigSource`),
  so no `LimeDB` hook.
- **Emoji is app-authoritative** (§1.3) — the keyboard never reseeds it; it rides a
  version-gated one-way cold → hot mirror, so no `LimeDB` hook.

This is *why* the reverted (HEAD) `LimeDB` / `SearchServer` are sufficient: the sync
never needed their cooperation.

### 2.5 Where rev / generation bumps live

- App-side table-changing operations — install, import, delete, restore, editor-save —
  are **orchestrated by `DBServer` / its controllers**, which bump the per-table rev and
  `generation` **there** (after calling the frozen `LimeDB` CRUD), then publish cold.
  Never inside `LimeDB` itself.
- **`im` has no inbox and no `seq`** — it is published as `im.json` (§1.5). An IM meta edit
  rewrites cold's `im`, **`publish()`es** (a generation bump, debounced to the edit's commit
  event), and re-writes `im.json`; the keyboard reads the file fresh. Nothing to bump or GC for
  metadata.
- Keyboard learning bumps nothing; it reaches cold only via the §1.4 editor-entry state
  diff.

### 2.6 Non-goals

- No JSON learned-score file path, and **no JSON sidecar for sync bookkeeping** — epoch /
  generation / per-table rev live in the in-DB `sync_meta` table (§1.0.3), never a file.
  (`im.json` (§1.5) is a different thing: the app-authoritative `im` *table* published as a plain
  file the keyboard reads — IM metadata, not sync bookkeeping.)
- No `SearchServer` dirty notification or callback.
- No learning-path instrumentation.
- No candidate-query changes.
- No keyboard writes into cold outside the §1.4 sync operation.
- No `sync_rev` / epoch / ledger logic inside `LimeDB` or `SearchServer`.
- **No editor op-log or ledger state machine** — close is a state diff; `sync_meta`
  is epoch + generation + per-table rev + the `applied_emoji_version` gate only (§1.0.3, §1.4) —
  not a per-record ledger.
- **No `im` inbox / cursor / GC**, and **no hot `im` mirror** — `im` is published as `im.json`
  and read via the `ImConfigReading` reader (§1.5), so the seq-cursor delivery apparatus and the
  wholesale mirror are both gone. (The prefs and IM-lifecycle inboxes are unrelated and stay —
  see IOS_FULL_ACCESS.md.)
- **No `user_version` bump for `sync_meta`** — the portable schema stays at 104.

UI states, the editor flow, and the delta strategy live in §1.4 — not duplicated here.
