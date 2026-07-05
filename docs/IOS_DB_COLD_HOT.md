# iOS DB Cold/Hot Architecture

Design for LIME's two-database (cold/hot) model on iOS: how the Settings app and
the keyboard extension share IM data across the process boundary, how
backup / restore / factory reset behave, how the emoji dataset is shipped and
upgraded, and how the table editor pulls the keyboard's latest data before
editing.

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
    cold. Used for restore and factory reset (emoji upgrade is **not** here — it
    is local per-process, §1.3).

### 1.0.2 Cross-process channel and Full Access

The Settings app and the keyboard extension are **separate processes** that share
nothing but the App Group container. All coordination is **file-based**, with
payload-free **Darwin notifications** as doorbells that only mean "go look at the
folder." There is no shared memory, RPC, or `UserDefaults` correctness signal.

**Shared files (App Group container):**

| File | Written by | Meaning |
| --- | --- | --- |
| `cold.limedb` | app | the published cold snapshot; **carries its own `sync_meta` table** (epoch + generation) — no JSON sidecar (§1.0.3) |
| `inbox/im.*` | app | changed `im` record(s) for one-way cold → hot metadata sync (§1.5) |
| `outbox/export.request.json` | app | `{requestUUID, expiresAt}` — "please snapshot hot" |
| `outbox/backup.limedb` | keyboard | the hot snapshot produced on request |
| `outbox/receipt.json` | keyboard | `{requestUUID, epochUUID, at}` — snapshot ready |
| `outbox/heartbeat.json` | keyboard | `{hasFullAccess, lastSeenAt, lastDBError}` — FA / liveness |

**Darwin doorbells (`org.limeime.*`, no payload):**

| Signal | Posted by | Wakes the reader to… |
| --- | --- | --- |
| `tables.updated` | app | re-scan cold (new generation and/or epoch) |
| `outbox.updated` | app | check for an export request |
| `import.done` / `import.failed` | keyboard | reflect import status |
| `fa.on` / `fa.off` | keyboard | FA state ping |

**Version markers live in cold's `sync_meta` table (§1.0.3), not a JSON sidecar:**

- **`epoch_uuid`** — the DB **lineage** identity, bumped **only** on restore and
  factory reset. A changed epoch → the keyboard does a **full replace** (§1.2).
  **This is the durable "bell," and the one marker correctness truly requires.**
- **`generation`** + per-table **`rev`** — the incremental selectors: same epoch +
  newer generation → the keyboard reconciles only the tables whose `rev` moved. `rev`
  is **load-bearing, not a mere optimization**: it scopes each cold→hot reconcile to
  the tables the **app** changed, so it never clobbers unharvested keyboard learning
  in untouched tables.

**Full Access is the gate.** A keyboard extension can access the App Group
container **only when Full Access is ON**. With FA **off** the keyboard is
confined to its own container: it still types and learns into hot, but it cannot
read cold, cannot see an export request, and cannot write a snapshot. So every
cross-process step below — incremental sync, backup, applying a restore —
**requires FA on the keyboard side**. The **app side always works** (it owns cold
outright).

### 1.0.3 Sync metadata — the `sync_meta` table (in-DB, not a sidecar)

All sync bookkeeping lives in a **`sync_meta` table inside each DB file**, read and
written by the **sync layer's own SQLite connection** — never a JSON sidecar
(abandoned: SQLite is faster than file I/O and travels atomically with the DB),
never `UserDefaults`, never a Darwin payload.

**The footprint is deliberately tiny:**

| Key | Where | Required? | Meaning |
| --- | --- | --- | --- |
| `epoch_uuid` | cold (authoritative); hot stores `applied_epoch` | **yes** | full-replace lineage — the one thing a state diff cannot reconstruct |
| `generation` | cold | fast-path | monotonic publish counter — the "anything changed?" gate |
| per-table `rev` | cold | **yes** | scopes each cold→hot reconcile to the tables the **app** changed, protecting unharvested keyboard learning elsewhere |

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

**How the epoch drives apply.** The keyboard, FA-on, opens cold on its own
connection, reads `cold.sync_meta.epoch_uuid`, compares it to its own
`hot.applied_epoch`: **differ → full replace**, then stamps `applied_epoch =
cold.epoch`. Same epoch → per-table reconcile for whatever `rev` moved. Because the
epoch is **persisted in the DB**, a keyboard that was FA-off or not running still
notices the change on its next launch — no reliance on catching the live doorbell.

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
whole-file swap (atomic by rename) with a coarse epoch stamped after.

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

1. On scan, open cold and read `cold.sync_meta.epoch_uuid`; ≠ the stored
   `hot.applied_epoch` → run the full replace: wipe hot, rebuild it from
   `cold.limedb`, then stamp `hot.applied_epoch = cold.epoch_uuid`.
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

### 1.3 Emoji: shipping and upgrade — local per-process, **not synced**

Emoji uses the **Android-aligned model**: it lives *in* each database and is
**not** part of the cold/hot sync. Cold and hot each carry their own emoji tables
and keep them current **independently, from the app- / appex-bundled `emoji.db`**.
Emoji never rides the epoch, never triggers a full replace on its own, and is
never diffed cross-process.

- `emoji_data` (+ prebuilt **FTS5**) ships **populated** in the bundled default DB
  — so a fresh install already has emoji — and in the bundled `emoji.db` (the seed
  / upgrade source). Queries read `emoji_data` in the DB, in place.
- `emoji_user` (recency / usage) is a **normal table** carried with the DB. It is
  not a sync stem and is **never special-cased**: backup captures it, restore
  restores it, and the emoji refresh leaves it alone (rows orphaned by a new set
  are harmless — they simply don't JOIN). Nothing stashes, wipes, or resets it.
- The emoji **version stamp is tracked per-DB** (local meta, e.g. `sync_meta`) —
  **not** in the `im` table — so a cross-process `im` mirror never clobbers a
  process's emoji-version bookkeeping.

**Upgrade — each side finishes its own, on launch:**

- On launch, **each process** compares its DB's emoji version against its bundled
  `emoji.db`. If the bundle is newer it re-seeds its own DB: the **app** refreshes
  **cold**, the **keyboard** refreshes **hot** ("hot finishes it").
- The re-seed **copies the prebuilt `emoji_data` + FTS5** from the bundle (attach,
  then copy the FTS shadow tables), so **neither side rebuilds the FTS index** —
  the keyboard does no heavy FTS work in the extension.
- This is Android's `refreshEmojiDataIfNeeded`, run per-process. **No epoch bump,
  no cold republish, no `tables.updated`** for a plain emoji upgrade.

**Interaction with restore:** on restore the emoji refresh runs **app-side, on
cold, before the bell** (§1.2 step 2) — an old or emoji-absent backup is brought
to the current emoji set *in cold*, and the subsequent full replace (cold → hot)
carries it to the keyboard. So the keyboard needs no separate post-restore emoji
step: cold is already current when the bell rings. This is also the **backfill**
path for a backup that predates emoji (absent tables read as "older than bundle"
→ seeded, in cold).

**Not editable app-side — out of §1.4 scope.** There is **no app-side editor for
emoji**. `emoji_data` is read-only reference data (changed only by the emoji
refresh above); `emoji_user` is written **only** by the keyboard
(`recordEmojiUsage` on an emoji commit) and replaced wholesale by a restore. So
emoji never flows through the §1.4 hot → cold table-editor sync — its only two
mutation sources are **keyboard usage** and a **full-DB restore**, nothing else.

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

### 1.5 IM-metadata sync (`im` table) — one-way, cold → hot only

Editing IM **metadata** on the details page (`IMDetailView` — keyboard layout,
title, enabled state) changes cold's **`im`** table. This is the **simplest** sync
case: it flows **one-way, cold → hot**, because the **keyboard never writes the `im`
table** — it only *reads* it to know which IMs exist and how they are configured. So
there is no harvest-back, no `LEFT JOIN` entry step, and none of §1.4's
delete-vs-learn ambiguity: **cold is unconditionally authoritative for `im`.**

Flow:

1. **App** applies the edit to cold's `im`, then writes the changed `im` record(s)
   to the App Group **inbox** (the app → keyboard record channel) and posts
   `org.limeime.tables.updated`.
2. **Keyboard (FA on)** drains the inbox and applies each record to hot's `im` in one
   transaction — upsert for an add / edit, delete for a removal — then clears the
   inbox.

Because the direction is one-way and the keyboard is a pure reader of `im`, applying
the record directly is safe — nothing of the keyboard's to clobber — so no full cold
republish and no per-table state diff are needed for a metadata tweak. (Installing /
importing an IM changes table *content* — that is §1.6 — not just `im` meta.)

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
2. **Keyboard (FA on)** reconciles `t` cold → hot (a full copy for a new table).
3. **If restore-on-import is ON and `checkBackupTable(t)`** (hot has `t_user`), run
   `restoreUserRecords(t)` on hot, then `dropBackupTable(t)`. The restored scores reach
   cold on the next editor-entry harvest (§1.4). Off / no backup → base scores.

**2. Delete a table** — back-up-learned opt-in (true / false):

1. **App** clears cold's table `t`, removes its `im` row (§1.5), bumps `t`'s `rev`, and
   rings the bell carrying the backup flag.
2. **Keyboard (FA on)**: if the flag is ON, `backupUserRecords(t)` on hot (→ `t_user`),
   then clear hot's `t`. If OFF, clear outright — learning is gone.

**3. Delete then re-import** — the `t_user` backup, living in hot, bridges the two even
across FA-off: the delete creates it, the re-import consumes it (on its next FA-on scan
the keyboard runs the pending delete's backup → clear before the import's copy →
restore).

The base moves **one-way cold → hot**; learned data is preserved only by the reused
hot-side `<table>_user` mechanism — no new stash, no App Group learned-data file, and no
`LimeDB` / `SearchServer` change (`backupUserRecords` / `restoreUserRecords` already
exist). Delete and import are `DBServer` / controller operations that bump `rev` and
ring the bell, like every other §2.5 app-side change.

---

## 2. Scope and Boundary

§1 is the design; **this is the contract every part of it obeys** — what the cold/hot
layer may touch, and what it may not. Cold/hot is **DB maintenance**; it must stay
outside IM query and learning logic.

### 2.1 What the cold/hot layer owns

The layer lives **only** in these files, plus the App Group relay files (§1.0.2):

| File | Responsibility |
| --- | --- |
| `DBServer.swift` | orchestration + the boundary API the app/keyboard call |
| `ColdPublisher.swift` | cold snapshot (`VACUUM INTO`) + the `sync_meta` stamp (epoch / generation) |
| `TableSyncEngine.swift` | cold → hot import, epoch / generation / per-table rev, the §1.4 editor state diff, the §1.5 `im` inbox apply |
| `SyncContract.swift` | App Group paths, inbox / outbox shapes, Darwin signal names |

Everything the design needs — `sync_meta` (epoch / generation / per-table rev), the
§1.4 editor state diff (both directions), the §1.5 one-way `im` sync, backup /
restore, emoji seed / upgrade orchestration — is owned here. This layer opens its
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
- **The `im` table is app-write-only** (§1.5) — the keyboard only reads it, so its
  metadata sync is a one-way record push, again with no `LimeDB` hook.
- **Emoji is local per-process** (§1.3) — not part of the sync at all.

This is *why* the reverted (HEAD) `LimeDB` / `SearchServer` are sufficient: the sync
never needed their cooperation.

### 2.5 Where rev / generation bumps live

- App-side table-changing operations — install, import, delete, restore, editor-save —
  are **orchestrated by `DBServer` / its controllers**, which bump the per-table rev and
  `generation` **there** (after calling the frozen `LimeDB` CRUD), then publish cold.
  Never inside `LimeDB` itself.
- Keyboard learning bumps nothing; it reaches cold only via the §1.4 editor-entry state
  diff.

### 2.6 Non-goals

- No JSON learned-score file path, and **no JSON metadata sidecar** — all sync
  metadata is the in-DB `sync_meta` table (§1.0.3).
- No `SearchServer` dirty notification or callback.
- No learning-path instrumentation.
- No candidate-query changes.
- No keyboard writes into cold outside the §1.4 sync operation.
- No `sync_rev` / epoch / ledger logic inside `LimeDB` or `SearchServer`.
- **No editor op-log or ledger state machine** — close is a state diff; `sync_meta`
  is epoch + generation + per-table rev only (§1.0.3, §1.4).
- **No `user_version` bump for `sync_meta`** — the portable schema stays at 104.

UI states, the editor flow, and the delta strategy live in §1.4 — not duplicated here.
