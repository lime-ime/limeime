# iOS FA Overlay — the no-sync cold/hot design

> ## ⛔ ABANDONED — do not implement
>
> **This design is dead.** Its core mechanism — the keyboard holding a **persistent read-only
> connection to the App-Group `cold.limedb`** and reading it *live* on every candidate query — is
> the documented iOS **`0xdead10cc`** anti-pattern: iOS force-terminates a **suspended** process
> that holds a handle/lock on a SQLite database in a **shared (App-Group) container**, and a
> keyboard extension is suspended constantly and abruptly. The community consensus is explicit —
> *"SQLite databases in App-Group containers: just don't."*
>
> The `immutable=1` argument in §1 (no locks ⇒ no `0xdead10cc`) is **plausible but unverified.**
> There is no evidence that a **persistent immutable attach held across suspension** is safe, and
> the whole thrust of the research is *"don't rely on getting these subtleties right; publish a
> file instead."* And the fatal part: the overlay's entire reason to exist — the large
> **content / mapping tables** — **cannot fall back to a file** (thousands of rows, code-prefix +
> FTS lookups), so there is **no safe escape hatch** if the bet loses. Betting the per-keystroke
> content read path on an unproven sandbox behavior, against the documented consensus, is not
> acceptable.
>
> **Why the *current* copy-based sync is safe where this isn't:** the existing content sync opens
> cold only **transiently, while the keyboard is active**, copies into hot, and **detaches before
> the process is suspended.** Transient-while-active never carries a handle into suspension. The
> overlay, by contrast, *lives* on cold across suspension — that's the difference iOS punishes.
>
> **What was kept from this line of thinking (shipped/planned elsewhere):**
>
> - **`im`** → *not* an overlay read. It becomes a tiny **`im.json`** the app publishes by atomic
>   rename and the keyboard reads as a **plain file** — no SQLite, no App-Group DB open, no
>   `0xdead10cc`. That is how the `im` inbox is deleted; reads reroute at the caller/decorator
>   level so **`LimeDB.swift` stays frozen**.
> - **emoji** → **does not need the overlay** and is out of scope here. The keyboard reads emoji
>   from **hot's own container**, never from the App-Group cold DB, so it was never exposed to the
>   `0xdead10cc` risk this doc is about. *How* emoji reaches hot (bundle reseed vs. app-side
>   mirror) is [IOS_DB_COLD_HOT.md](IOS_DB_COLD_HOT.md) §1.3's concern, not this file's.
> - **content / mapping tables** → **stay copied into hot** via the existing sync
>   ([IOS_DB_COLD_HOT.md](IOS_DB_COLD_HOT.md) §1). iOS forces this; it is load-bearing and cannot
>   be replaced by reading cold live.
>
> **Sources:**
> [ryanwesley.com — SQLite Databases in App-Group Containers: Just Don't](https://ryanwesley.com/sqlite-databases-in-app-group-containers/) ·
> [Michael Tsai — SQLite Databases in App Group Containers (Don't)](https://mjtsai.com/blog/2025/05/15/sqlite-databases-in-app-group-containers-dont/) ·
> [Apple Developer Forums — 0xdead10cc prevention](https://developer.apple.com/forums/thread/126438) ·
> [sqlite.org/wal.html §5 — read-only WAL](https://www.sqlite.org/wal.html)
>
> Everything below is retained as a **historical design record.** The overlay *mechanics* (the
> `_id`-floor scheme, union+anti-join reads, copy-up-on-write, the delta sweep) are sound as pure
> SQLite engineering; only the **iOS App-Group live-attach foundation** is fatal. Read it for the
> ideas, not as a plan.

## 0. Bottom line

The keyboard **stops copying cold into hot**. Instead:

- **Cold** stays the app-owned base (App Group), but the keyboard reads it **in place, live**,
  through a read-only `immutable=1` attach — legal FA-off, because it creates no `-wal`/`-shm`
  sidecars and takes no locks.
- **Hot** shrinks from a full copy to a **delta DB** in the keyboard's own container: empty
  twins of the mapping tables holding only learned rows (score copy-ups + new learns),
  `related` learning, and `emoji_user`.
- Every candidate query is an **overlay**: hot rows shadow cold rows by logical key; a record
  present in hot replaces its cold base row in the result.

What this kills, structurally:

| Dead | Why it can't recur |
| --- | --- |
| full replace + the interrupted-copy states | hot is never replaced — its inode never changes |
| §1.8 `hotFileReplacedSinceOpen` cross-process stale-inode bug | only cold's inode moves; each process re-attaches, a millisecond op |
| install/restore summon probe + `同步中` wait + `sync.scan.done` | nothing to pre-apply — the next appearance reads cold live |
| §1.5 `im` metadata inbox (seq / cursor / GC) | the keyboard reads `cold.im` directly through the attach |
| picker drift from cold's enabled set (the "installed-first IM disappears" class) | the picker *is* a live cold read — there is no second copy to drift |
| keyboard-side emoji re-seed (§1.3 keyboard half) | keyboard queries `cold.emoji_data`/FTS in place; only the app refreshes emoji |
| epoch/generation *convergence* bookkeeping | replaced by a delta-sized sweep (§4) — cold is current the moment it's attached |
| the "cursor must live outside the DB" rule | nothing wipes hot anymore; keyboard-side markers can live in `hot.sync_meta` |

What it costs: **`LimeDB.swift` thaws** (the IOS_DB_COLD_HOT §2.2 freeze breaks — ~10 read sites
get a mechanical union-wrap, 4 write sites gain copy-up, emoji/dictionary SQL gains a schema
prefix, plus one `attachOverlay` entry point). **`SearchServer.swift` stays frozen — it contains
zero SQL**; every DB touch routes through LimeDB. The app process never uses the overlay at all:
it opens cold as `main` with plain LimeDB, unchanged.

## 1. FA legality and the publish discipline

Apple's rule (IOS_FULL_ACCESS.md): FA-off the keyboard has **read-only** App Group access, and
the reason it could never open cold in place was the WAL sidecar problem. The overlay's answer:

```sql
ATTACH DATABASE 'file:<appgroup>/cold.limedb?mode=ro&immutable=1' AS cold
```

`immutable=1` reads pure bytes — no locking, no journal/shm access — exactly the "reading file
bytes is safe FA-off" carve-out.

> **Non-negotiable precondition: cold is published by atomic rename only, never written in
> place.** `immutable=1` assumes the file cannot change under an open handle; an in-place write
> would produce silently wrong reads. `ColdPublisher` already does `VACUUM INTO` temp + rename —
> this discipline must be enforced (assert/review), not hoped. An open attach simply keeps
> reading the old unlinked inode (consistent, stale); the keyboard checks the on-disk inode on
> appearance and re-attaches when it moved.

A corollary: **the keyboard only ever sees published cold state.** Any app-side change —
including a bare `im` metadata toggle — reaches the keyboard only through a publish. Debounce
publishes at flow-end / screen-exit rather than per toggle, or rapid edits become a `VACUUM`
storm (pure cost, no correctness risk).

Connection layout in the keyboard process: **hot delta = `main`** (own container, WAL — shared
by the per-host-app keyboard processes, which is safe because hot is never swapped), **cold
attached read-only**. All writes target `main`; there is never a cross-DB write transaction.

## 2. The overlay mechanics

### 2.1 Shadow keys

| Table(s) | Shadow key | Notes |
| --- | --- | --- |
| mapping tables (`custom`, `dayi`, `phonetic`, …) | `(code, word)` | anti-join in reads; `_id` for writes (§2.2) |
| `related` | `(pword, cword)` | |
| `im` | `(code, title)` | keyboard never writes it → cold arm only, in practice |
| `keyboard` | `code` | keyboard never writes it |

### 2.2 The `_id` scheme (load-bearing)

The learning hot path updates by rowid (`SearchServer` → `updateScore(id:)` →
`UPDATE t SET score=? WHERE _id=?`), and the main candidate sort ends in `_id asc`. Both are
kept correct by **one rule**:

> **Copy-up preserves the cold row's `_id`. New learned rows get ids above a floor**
> (`FLOOR = 1<<40`, seeded into each delta table's `sqlite_sequence` at creation).

Consequences, all for free:

- ids are globally unique across the two arms → UPDATE-by-`_id` after copy-up hits the right
  hot row;
- the `_id asc` tiebreak keeps cold's original import order for base rows; new learns sort
  last among ties — same as today;
- the backup/editor merge is a plain `INSERT OR REPLACE` by primary key (§5, §6) — no join;
- the sweep can discriminate copy-up (`< FLOOR`, GC-able when the base dies) from new learn
  (`>= FLOOR`, protected) (§4).

**Invariant: cold never contains an above-floor id** (the editor merge re-ids new learns into
cold's range, §6). Hygiene: seed the sequence at table creation; never DROP-recreate a delta
table (DELETE only — recreate resets the sequence); `assert max(cold.t._id) < FLOOR` on attach.

### 2.3 Read pattern

SQLite has no FS-style overlay; the supported idiom is UNION ALL + anti-join. Because a bare
compound SELECT rejects expression ORDER BY terms, wrap the union in a subquery — the existing
`sortClause` then works verbatim (both arms must select the same columns, incl. computed
`exactmatch`):

```sql
SELECT * FROM (
    SELECT _id, code, code3r, word, score, basescore, <exact-expr> AS exactmatch
    FROM main.t WHERE <existing clauses>
  UNION ALL
    SELECT _id, code, code3r, word, score, basescore, <exact-expr> AS exactmatch
    FROM cold.t c WHERE <existing clauses>
      AND NOT EXISTS (SELECT 1 FROM main.t h WHERE h.code = c.code AND h.word = c.word)
) ORDER BY <existing sortClause> LIMIT <n>
```

Cost: the sort runs over the code-prefix-narrowed, LIMIT-capped match set (tens–hundreds of
rows); the anti-join probe is one indexed hit against a tiny delta table. The `expandDualCode`
existence probes become `EXISTS hot OR EXISTS cold` — two indexed LIMIT-1 probes, negligible.

### 2.4 Write pattern — copy-up on write

Learning writes route to `main`; a write that targets a cold-only row **copies the row up**
first, preserving `_id`. The one multi-row site is `addScore` (`UPDATE … WHERE word = ?` matches
every code for that word):

```sql
UPDATE main.t SET score = score + 1 WHERE word = ?;
INSERT INTO main.t (_id, code, code3r, word, related, score, basescore)
  SELECT c._id, c.code, c.code3r, c.word, c.related, c.score + 1, c.basescore
  FROM cold.t c
  WHERE c.word = ? AND NOT EXISTS (SELECT 1 FROM main.t h WHERE h._id = c._id);
```

`addOrUpdateMappingRecord`'s read step (`SELECT _id, score WHERE code=? AND word=?`) must read
the **overlay** (else it would insert a duplicate of a cold row instead of copying it up); its
insert path is unchanged. `addOrUpdateRelatedPhraseRecord` follows the same pattern on
`(pword, cword)`.

### 2.5 Exempt from the overlay

- **`emoji_data` + emoji FTS, and the `dictionary` FTS** — read-only reference data with **no
  hot arm**, so the FTS restriction (`MATCH` fails through views/unions) never bites: the
  keyboard queries them **directly, schema-qualified** (`cold.emoji_fts MATCH …`,
  `cold.dictionary`). Cross-schema joins are fine: `loadRecentEmoji` becomes
  `main.emoji_user JOIN cold.emoji_data`.
- **`emoji_user`** — hot-own, keyboard-authoritative (`recordEmojiUsage` unchanged). Cold's
  copy is backup payload only.
- **`t_user` learned-record backups** — real hot tables, resolve outside the overlay naturally.
- **`im` rows with `code='emoji'`** — irrelevant keyboard-side under this design (the emoji
  version stamp is app business only, §7).

The app-process LimeDB needs plain names (cold is its `main`), so the emoji/dictionary SQL
carries a per-instance schema prefix — `"cold."` in the keyboard, `""` in the app.

### 2.6 Schema mirror

`tableExists`/`tableHasData` consult `main`'s `sqlite_master`, and `DBServer` gates IM
activation on `tableHasData`. So on every attach the sync layer **mirrors cold's table list**
into hot: `CREATE TABLE IF NOT EXISTS` empty twins (+ `(code, word)` index, + sequence seeded
above FLOOR) for every cold mapping table. Then `tableExists` is true and `tableHasData` reads
through the overlay — correct. Re-runs on cold re-attach (a new IM's table appears as a new
twin).

## 3. Per-query audit (LimeDB.swift @ branch ios-db-cold-hot)

Every keyboard-reachable SQL site was audited. **SearchServer.swift issues no SQL of its own.**

| Site | Verdict |
| --- | --- |
| `getMappingByCode` main (≈559), `(tableName:)` (727), `WithFallback` (746), `getMappingByWord` (799) | ✅ union-wrap §2.3; Swift-side `duplicateCheck` semantics preserved (anti-join emits no dup `(code,word)`) |
| `getRelatedMappings` (825), `getRelatedPhrase` (842), `getRelatedPhraseList` (862), `isRelatedPhraseExist` (924) | ✅ shadow by `(pword, cword)` |
| `expandDualCode` probes (2164, 2188) | ✅ EXISTS over both arms |
| `getImConfig*` / `getAllImConfigs` (1061–1195), `getKeyboardConfig*` (1295–1394) | ✅ overlay read = **live cold metadata**; this is what kills the §1.5 inbox |
| `updateScore(id:)` (1008), `addOrUpdateMappingRecord` (1023), `addOrUpdateRelatedPhraseRecord` (947) | ✅ copy-up + preserved-`_id` |
| `addScore` (990) | ⚠️→✅ the one multi-row copy-up (§2.4) |
| `recordEmojiUsage` (2432) | ✅ hot-local, unchanged |
| emoji queries (2349–2430), `getEnglishSuggestions` (2295) | ✅ schema-qualified cold reads, no overlay (§2.5) |
| `backupUserRecords` / `restoreUserRecords` / `checkBackupTable` (1505–1551) | ✅ `t_user` is hot-local; the `CREATE TABLE … AS SELECT` reading the overlay is *better* than today (captures restored-base scores too) |
| `tableExists` / `tableHasData` (473, 481), `countRecords` (491) | ⚠️ needs the schema mirror (§2.6); counts must use the anti-join shape, not naive UNION ALL |
| `refreshEmojiDataIfNeeded` (2451) | app-process only under this design (§7) |
| bare DELETEs (`removeImConfig`, `resetImConfig`, `clearTable`, `ensureCj4Schema`) | keyboard-side these can't hide cold rows (no whiteouts) — acceptable because all meaningful callers are app-side on cold; migrate-time fixups self-heal when the app next opens cold. **Guard-rail: no new keyboard-side deletes against overlaid tables.** |
| import/export/editor-list machinery (2849–3600: `importDb`, `importFromAttachedDB`, `importTxtFile`, `getRecordList`, `getRecord`, `queryWithPagination`, `rawQuery`, `exportTxtTable`, `renameTableName`) | app-process only — no overlay, unchanged |

## 4. Cold→hot propagation: the sweep

Rows with **no hot shadow** (the vast majority) propagate for free — the overlay reads cold
live. Propagation only has content for shadowed rows, and preserved-`_id` matching reduces it to
two idempotent, **delta-sized** statements per table, run when cold's inode / `generation` moved
(scan-on-appear; a doorbell makes a live keyboard do it now):

```sql
-- delete propagation: base row gone → copy-up dies
DELETE FROM main.t
 WHERE _id < FLOOR AND _id NOT IN (SELECT _id FROM cold.t);

-- update propagation: base changed → refresh copy-up, keep learned score
INSERT OR REPLACE INTO main.t
SELECT c._id, c.code, c.code3r, c.word, c.related, h.score, c.basescore
  FROM main.t h JOIN cold.t c ON c._id = h._id
 WHERE h._id < FLOOR;
```

The `< FLOOR` guard protects new learns (legitimately absent from cold). Work is proportional to
**learned rows, not table size**, so the sweep can simply run over all shadowed tables on any
generation change — per-table `rev` degrades to an optional skip-optimization. The sweep is also
the orphan GC that backup (§5) and the resurrection rule (§8.4) depend on.

## 5. Backup — vacuum cold, merge deltas, export

Still keyboard-side, FA-on, same IOS_DB_COLD_HOT §1.1 outbox handshake — only the snapshot step
changes:

1. `VACUUM cold INTO '<temp>'` (vacuuming an attached immutable schema is legal — it only reads).
2. Merge deltas — one statement per overlaid table, thanks to preserved ids:
   `INSERT OR REPLACE INTO temp.t SELECT * FROM main.t` — plus the orphan belt-and-suspenders
   filter `WHERE _id >= FLOOR OR EXISTS (SELECT 1 FROM temp.t b WHERE b._id = main.t._id)`
   (run the §4 sweep first *and* keep the filter).
3. `emoji_user` is a **full replace, hot direction**: `DELETE FROM temp.emoji_user;` then
   `INSERT INTO temp.emoji_user SELECT * FROM main.emoji_user;`
4. `im` / `keyboard` need no merge — the keyboard never writes them.
5. Rename into `outbox/backup.limedb`, receipt, app zips with the pref sidecars — unchanged.

The output is a **plain, fully-materialized `user_version` 104 DB** — indistinguishable from
today's backup, portable to Android, restorable by machinery that never heard of the overlay.

## 6. Table editor — merge, adopt, edit, clear

Editing stays FA-on + LIME-active (product gate unchanged). The flow reuses §5 verbatim and
**deletes the close-reconcile entirely**:

1. **Entry**: the app posts the refresh request; the summoned keyboard runs the §5 merge routine
   and hands the merged file over the outbox. The merge **re-ids above-floor rows** (`INSERT`
   without `_id` for new learns; `INSERT OR REPLACE` by id for copy-ups) so the §2.2 invariant —
   no above-floor id in cold — holds. The keyboard stamps a **checkpoint token** into the merged
   file's `sync_meta`.
2. **Adopt**: the app publishes the merged file as the new cold (rename, bumped generation).
   Learned scores now live in the base. `同步中` lasts exactly as long as the merge.
3. **Edit**: the app edits cold directly — it owns it. No op log, no per-edit publish.
4. **Close**: normal publish (rename + generation + doorbell).
5. **Clear**: any keyboard process seeing `cold.sync_meta.delta_checkpoint == a token it
   generated` clears its delta tables — **overlaid mapping tables + `related` only**
   (`emoji_user` and `t_user` are excluded; they stay hot-authoritative). Clear only on the
   *adopted* cold, never on the request — if the app abandons the edit, hot's deltas remain the
   only copy of the learning.

Safety rests on the same invariant as today's §1.4: the keyboard runs only on-screen, and it
can't be on-screen while the editor holds the app foreground — so nothing learns between the
merge snapshot and the clear, and an appearing keyboard scans (and clears) before it can learn.
A crash between adopt and clear is harmless: the lingering deltas are equal-content shadows; the
next scan clears them. Idempotent across keyboard processes.

## 7. Emoji

- `emoji_data` + FTS are **read-only reference**: the keyboard queries them schema-qualified
  from cold; **only the app refreshes them** (bundle-newer check + re-seed, on cold, before
  publish). The keyboard-side re-seed, FTS shadow-table copy, and version bookkeeping are gone.
- `emoji_user` is the only mutable emoji state; it is hot-own and rides backups per §5.3. On
  restore, the keyboard reseeds `hot.emoji_user` from the restored cold's copy (§8.1).
- Orphaned `emoji_user` rows after a set upgrade stay harmless (the JOIN drops them) — unchanged.

## 8. What survives from the old design

1. **`epoch_uuid`** (cold lineage): restore / factory reset still bumps it; the keyboard's apply
   becomes *clear all deltas + reseed `emoji_user` from cold* — in-place DELETEs/INSERTs, FA-off,
   no file swap. The keyboard-side markers (applied epoch, last-swept generation, consumed
   lifecycle seq) can now live in **`hot.sync_meta`** — nothing wipes the hot file anymore.
2. **`generation`**: the sweep trigger. Per-table `rev`: optional optimization or dead.
3. **The relay + keyboard-owned prefs** (IOS_FULL_ACCESS.md): unchanged — still the FA-off
   kb→app report channel and the UserDefaults home of the four keyboard-owned prefs.
4. **The lifecycle seq-inbox — flags only.** The sweep GCs copy-ups of a deleted table, but
   **above-floor new learns linger** (the floor guard protects them), so a reinstall would
   resurrect them. Rule: *cold table content vanished → run `backupUserRecords` per the
   delete-time opt-in flag, then clear that table's deltas including above-floor.* The two user
   opt-ins (備份已學習 on delete, `restoreOnImport` on install) travel app→keyboard through the
   surviving minimal inbox.
5. **The summon probe** — only for the two FA-on handshakes (editor entry §6, backup §5).
   Install / restore / metadata edits need no probe: the next appearance reads cold live.

## 9. Open items before implementation

1. **No-cold bootstrap.** Keyboard enabled before the app ever runs → attach the **appex-bundled
   default DB** as the base (`immutable=1` on a bundle resource is textbook-correct). Decide: the
   app's initial cold is a copy of the same bundled default (ids line up, deltas survive), or
   first-cold-appearance is treated as an epoch change (deltas cleared).
2. **Device spikes (before committing to the design):** (a) `ATTACH 'file:…?immutable=1'` under
   GRDB — URI filename handling must be enabled on the connection for ATTACH to honor it, and
   FA-off App Group reads through an attach must be proven **on a real device** (the Simulator
   does not enforce the keyboard sandbox); (b) `VACUUM cold INTO` from an immutable attach.
   Fallback if (a) fails: a second read-only connection + overlay in code — works, costs the
   single-connection elegance.
3. **Cache invalidation points.** Re-attach / sweep / clear replace §1.7's "sync applied" as the
   rebuild trigger: flush SearchServer caches, `lastValidDualCodeList`, the dual-code blacklist,
   and LimeDB's `relatedScore` rowid-keyed cache (the sweep rewrites rows under it).
4. **Schema evolution constraint (doc-level).** The keyboard can no longer self-migrate its
   base; it reads cold's schema live. Frozen-at-104 makes this moot today, but any future schema
   change requires app-migrates-cold-first ordering and a keyboard tolerant of the old cold.
5. **Existing-install migration.** Current full hot DBs → one-time delta extraction (the §1.4
   harvest LEFT JOIN: `score > 0` or not-in-cold), or an explicit decision to epoch-clear dev
   installs.
6. **`sqlite_sequence` hygiene** (§2.2) — enforcement, not convention.
7. **Publish debounce** for app-side metadata edits (§1) — flow-end, not per toggle.

## 10. Cost summary

| Area | Change |
| --- | --- |
| `SearchServer.swift` | **zero** (no SQL of its own) |
| `LimeDB.swift` | thaws: ~10 read sites union-wrapped, 4 write sites copy-up, emoji/dictionary schema prefix, one `attachOverlay(coldPath:)` + mirror/seed setup |
| `DBServer` / `TableSyncEngine` | shrink to **attach, sweep, clear** + the §5 merge + the lifecycle-flags inbox; full replace, §1.8 reopen, `im` inbox, probe orchestration for install/restore all deleted |
| Steady-state disk | roughly halves (hot is a delta, not a copy) |
| App process | no overlay, no change to how it opens cold |
| Android portability | untouched — backups are plain 104 DBs; `sync_meta` stays a non-versioned side table |

## 11. History — the `im`-delivery lineage (`im.json` won)

The `im` table's delivery went through three dead designs before `im.json`
([IOS_DB_COLD_HOT.md](IOS_DB_COLD_HOT.md) §1.5):

1. **seq-inbox** — cold appended `im` ops to an App-Group inbox with a seq counter; the keyboard
   consumed them against a cursor and GC'd. Dropped for the churn.
2. **im-only mirror** — an earlier draft *in this file*: hot's `im` as a wholesale
   `DELETE + INSERT SELECT` of cold's on every generation bump, dropping the inbox while keeping
   the full-replace content sync.
3. **full-overlay live read** (§0–§10 above) — the keyboard reads `cold.im` live through the
   `immutable` attach, so even the mirror copy is unnecessary.

**All three are abandoned.** (1) and (2) still *copied* (the race); (3) died with the whole
overlay on the `0xdead10cc` foundation (preface). **`im.json` superseded them:** the app
publishes `im` as a plain file, the keyboard reads it through a `LimeDBProtocol` decorator — no
copy, no cold-DB open. The three durable observations survived into it: publish-on-metadata-edit
needs a debounce (§1 / §9.7 here → §1.5 there), the no-cold bootstrap falls back to the bundled
default (§9.1 here → the decorator's `LimeDB` fallback there), and cold-authoritative `im`
structurally closes the picker-drift bug class (§0 here → §1.5 there).
