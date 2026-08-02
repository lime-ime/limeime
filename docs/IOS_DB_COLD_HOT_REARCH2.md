# iOS Cold/Hot DB Re-Architecture 2 Implementation Plan

**Status: accepted 2026-08-01; implemented 2026-08-02 on branch `ios-db-rearch2` (all task gates green — see the campaign plan flight log). Device measurements and Xcode Cloud run remain maintainer residuals.**
Successor to the shipped [IOS_DB_COLD_HOT.md](IOS_DB_COLD_HOT.md) §1.4 editor sync and the
issue #209 ownership machinery. Deferred side effort: [DB_DEDUPE.md](DB_DEDUPE.md).

> **For agentic workers:** execute via the goal-mode campaign plan
> **[IOS_DB_COLD_HOT_REARCH2_PLAN.md](IOS_DB_COLD_HOT_REARCH2_PLAN.md)** (branch, scope
> invariants, Codex dispatch workflow, iteration gates, final gate). REQUIRED SUB-SKILL:
> superpowers:subagent-driven-development (recommended) or superpowers:executing-plans,
> task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the iOS table editors immediately usable and deliver keyboard learning back to cold
without split brain.

**Architecture:** Cold is authoritative for row existence and explicit editor changes; hot is
authoritative only for learning that cold has not acknowledged. Cold sends revisioned per-key
fences to hot, while hot sends a versioned per-key learning outbox to cold. SQLite transactions,
duplicate-tolerant logical-key operations, and a keyboard-only flush lock make both streams
idempotent without blocking the editor or suspending cold access.

**Tech Stack:** Swift, GRDB/SQLite, iOS App Group files and Darwin notifications, POSIX `flock`,
XCTest.

## Global Constraints

- **This campaign is iOS-only.** `user_version` stays **105** on both platforms; no portable
  schema change, no unique-index creation, no Android source change, no seeded-asset change.
  Duplicate cleanup is out of scope entirely — see [DB_DEDUPE.md](DB_DEDUPE.md).
- Mapping identity is `(code, word)`; related identity is `(pword, cword)`; `_id` is never a
  synchronization or import identity.
- Cold owns explicit add/update/delete/clear and table lifecycle; hot owns unacknowledged score and
  phrase learning.
- No wall-clock conflict resolution, whole-table merge heuristic, per-keystroke cold write, hot DB
  in the App Group, or editor wait for the keyboard.
- Keyboard dismissal is best-effort latency reduction only; durable state plus keyboard-appearance
  retry provides correctness.
- Restore and epoch replacement intentionally discard pending learning from the replaced lineage;
  every cold-bound flush validates that lineage inside its cold transaction.
- Every trust boundary rejects empty synchronization keys and every changed write path returns a
  typed failure; no new `try?` swallowing.
- This plan formally supersedes the absolute freeze on `LimeDB.swift`, `LimeDBProtocol.swift`, and
  `SearchServer.swift` (IOS_DB_COLD_HOT.md §2.2 / the REARCH Red list): the nine-PR #209 record
  shows the freeze pushed sync complexity into a worse cross-process protocol. Changes to those
  files stay bounded to exactly what the tasks list.
- Upgrade from the shipped pull architecture is lossless for every table except one where live
  cold proves a pending pre-upgrade edit (by revision or content drift) while that same table also
  has unharvested hot learning (§5). Cold wins only that genuinely ambiguous table; unharvested
  learning is never discarded wholesale across unrelated tables.

---

## 1. Decision

The keyboard-dismiss flush is a good idea only as a fast path. It usually moves recent learning to
cold before the user opens Settings, but iOS may suspend or kill the extension at any instruction.
The editor must therefore never wait for it and correctness must never depend on its completion.

The complete flow is:

```text
app editor mutation                         keyboard learning
        |                                          |
        v                                          v
cold row + revision + fence                 hot row + outbox version
        |                                          |
        +---- publish on exit/background           +---- flush on dismiss
        |                                          +---- retry on appearance
        v                                          v
keyboard applies exact app intent            cold accepts causally valid learning
```

This avoids split brain because the databases are not equal masters of the same fields. A row's
existence and explicit editor values come from cold. Learning-owned scores come from hot until
cold accepts them. Conflicts are decided by observed revision, not arrival time.

## 2. Authority and causal rules

### 2.1 Logical identity

| Data | Logical key |
| --- | --- |
| Mapping table | `(code, word)` |
| Related phrase | `(pword, cword)` where `cword IS NOT NULL` |

There is no unique index on these keys (and the shipped data contains some duplicate rows — see
[DB_DEDUPE.md](DB_DEDUPE.md); cleaning them is out of scope). Sync SQL therefore never *assumes*
uniqueness; the keyed idioms are:

- **Updates and deletes address every row matching the key** (`WHERE code = ? AND word = ?`,
  never by `_id`) — a keyed write converges any existing copies instead of misrouting to an
  arbitrary row, and a fence `delete` clears them all at once.
- **Inserts are guarded** — a new learned key enters cold via
  `INSERT ... WHERE NOT EXISTS (SELECT 1 FROM t WHERE code = ? AND word = ?)`; the app editor's
  add path performs the same existence check. Sync never creates a duplicate. (Note
  `INSERT OR REPLACE` would be wrong here: without a unique index it degrades to a plain insert.)
- **Legacy `word IS NULL` rows are code-only sentinels**, not logical mappings: every keyed
  operation excludes them (`word IS NOT NULL`).
- **Legacy related rows with `cword IS NULL` are also sentinels**, not row-sync keys. They never
  enter `learn_outbox` or `editor_fence`; only table-level `clear`/`replace` carries them. This
  keeps the protocol truthful because `k2` is non-nullable and avoids inventing a magic sentinel
  value that could collide with user data.
- Learning entry points reject empty key fields before writing either data or sync state.

Both the app add and learning insert perform their `NOT EXISTS` check inside the live-cold write
transaction. SQLite serializes those writers; a busy/stale-snapshot retry restarts the whole
transaction and rechecks existence. There is no accepted app-editor-versus-flush duplicate race,
while duplicate-tolerant keyed SQL still handles rows already present in shipped/user data.

### 2.2 Conflict policy

For the same key:

> An app fence at revision `R` rejects learning whose `observed_rev < R`. Learning recorded after
> hot has applied `R` records `observed_rev >= R` and may flow back to cold.

Therefore:

- learn, then delete in the editor: delete wins;
- learn, then explicitly edit the row: the editor value wins;
- delete, apply that revision to hot, then type the phrase again: later learning may recreate it;
- delete key `D` while unrelated learned key `L` is pending: `D` is deleted and `L` survives.

Learning accepted into live cold does **not** bump an app table revision. Those revisions mean app
intent; bumping one without a fence would make the keyboard misread its own delivery as an app edit.

## 3. Durable protocol state

The following tables are additive iOS sync tables, created with `CREATE TABLE IF NOT EXISTS`,
ignored by Android, and never bumping `user_version`.

### 3.1 Hot learning outbox

```sql
CREATE TABLE IF NOT EXISTS learn_outbox (
    tbl          TEXT    NOT NULL,
    k1           TEXT    NOT NULL,
    k2           TEXT    NOT NULL,
    observed_rev INTEGER NOT NULL,
    version      INTEGER NOT NULL,
    PRIMARY KEY (tbl, k1, k2)
) WITHOUT ROWID;
```

The hot data write and this upsert commit in the same transaction:

```sql
INSERT INTO learn_outbox(tbl, k1, k2, observed_rev, version)
VALUES (?, ?, ?, ?, 1)
ON CONFLICT(tbl, k1, k2) DO UPDATE SET
    observed_rev = excluded.observed_rev,
    version = learn_outbox.version + 1;
```

The outbox stores keys, not row copies. A flush captures the key, version, current hot row, and
hot's applied cold epoch in one read snapshot. Repeated learning collapses to one pending item while
`version` prevents a stale acknowledgement from deleting newer work. Epoch is batch metadata, not
another outbox column: epoch replacement clears the entire old-lineage outbox.

### 3.2 Cold editor fences

```sql
CREATE TABLE IF NOT EXISTS editor_fence (
    tbl      TEXT    NOT NULL,
    k1       TEXT    NOT NULL,
    k2       TEXT    NOT NULL,
    action   TEXT    NOT NULL CHECK (action IN ('upsert', 'delete')),
    revision INTEGER NOT NULL,
    PRIMARY KEY (tbl, k1, k2)
) WITHOUT ROWID;

CREATE TABLE IF NOT EXISTS editor_table_fence (
    tbl      TEXT PRIMARY KEY,
    action   TEXT    NOT NULL CHECK (action IN ('clear', 'replace')),
    revision INTEGER NOT NULL
) WITHOUT ROWID;
```

An editor mutation commits row data, its fence, and its table revision in the same cold transaction.
A logical-key rename writes `delete(old)` and `upsert(new)` at one revision. `clear` and lifecycle
replacement use a table fence instead of one row fence per deleted record.

Fence application order is explicit:

1. When the app writes a table fence at revision `R`, delete cold row fences for that table whose
   revision is `<= R` in the same transaction; the table fence supersedes them.
2. The keyboard applies the newest table fence first.
3. It ignores any older row fence still present in a legacy/interrupted snapshot, then applies row
   fences with revision `> R` in revision order.

The fence tables are latest-intent sets, not replay logs: at most one row per edited logical key
and one table fence per table.

### 3.3 Cold IM lifecycle intents

New-protocol lifecycle intent lives in cold SQLite, not a separate App Group inbox file, so it can
commit atomically with table data, revision, and fence:

```sql
CREATE TABLE IF NOT EXISTS im_lifecycle_intent (
    tbl               TEXT    NOT NULL,
    revision          INTEGER NOT NULL,
    action            TEXT    NOT NULL CHECK (action IN ('install', 'delete')),
    preserve_learning INTEGER NOT NULL CHECK (preserve_learning IN (0, 1)),
    PRIMARY KEY (tbl, revision)
) WITHOUT ROWID;
```

This table is an ordered log because delete-then-reinstall must retain the intermediate instruction
to back up hot learning even if both revisions reach one published snapshot. The keyboard processes
only intents with `hotAppliedRevision < revision <= publishedRevision`; revision gating makes replay
idempotent. Lifecycle volume is tiny compared with mapping data and is measured with fence debt in
Task 5.

For `preserve_learning = 1`, reuse the existing hot `<table>_user` helpers. A delete intent backs up
learned rows before the table fence clears hot. An install intent restores that backup after the
`replace`; each restored logical key and its outbox upsert commit in the same hot transaction with
`observed_rev` equal to the applied install revision, so preserved learning can flow back to cold.
With preservation off, stale backup/outbox state for that table is cleared. The old lifecycle inbox
file has no revisions, so the new protocol cannot distinguish a pending record from one already
consumed. New code does not replay it: upgrade applies the documented cold-wins policy for that
ambiguous table, then deletes the legacy file after marked publication. All post-upgrade lifecycle
intent uses this revisioned SQLite table.

### 3.4 Protocol marker

Cold `sync_meta` stores `editor_fence_protocol = 1`. The marker is written only by the upgraded app
in the same transaction that creates the one-time baseline `replace` fences. The published snapshot
therefore never claims the new protocol without carrying its baseline intent.

After the marker is visible **and the keyboard's one-time §5.2 transition has run for its hot
lineage**, a table revision advance without either a row fence or a table fence is an invariant
violation. The keyboard logs a typed error, leaves its applied revision unchanged, and preserves
the outbox. It must not guess with a wholesale-copy fallback. The §5.2 transition is the single
sanctioned consumer of unfenced legacy gaps.

## 4. Runtime flows

### 4.1 Atomic keyboard learning

Every mapping score update, learned mapping, and learned related phrase performs this inside the
same hot transaction:

1. Validate nonempty logical-key fields.
2. Mutate the hot row by logical key.
3. Read hot's applied revision for the table.
4. Upsert `learn_outbox` and increment `version`.
5. Commit.

Bulk imports do not produce outbox rows. If outbox recording fails, the learning mutation rolls
back; a silent hot-only change is not accepted.

### 4.2 Atomic app editing, table lifecycle, and deferred publication

`DBServer.performEditorMutation(_:)` is the only record/related editor mutation entry point. It:

1. Validates the table and values.
2. Resolves the old key inside the transaction when the UI addressed a row by `_id`.
3. Mutates the cold row.
4. Increments the table revision.
5. Writes row or table fences at that revision.
6. Commits, then updates UI/cache state.

The mutation does not run `VACUUM INTO`. `publishPendingEditorChanges()` publishes once when the
editor closes or Settings backgrounds. If publication fails, the live edit and fence remain saved;
the UI reports that keyboard publication is pending and the next lifecycle attempt retries.

`DBServer.performTableLifecycleMutation(_:)` is the only install/replace/delete entry point for IM
tables. `.cin`, `.lime`, `.limedb`, and `.zip` importers parse and validate into a standalone
staging database without touching the live target. The final operation uses the staging database
read-only and performs all live-cold writes in one transaction:

1. Install or replace the target table contents, or drop the target for delete.
2. Insert the revisioned IM lifecycle intent in §3.3.
3. Increment the table revision.
4. Write `replace` for install/replace or `clear` for delete, and remove superseded row fences.
5. Commit, then discard the staging database and update UI/cache state.

If any live step fails, the target table, lifecycle intent, revision, and table fence all roll back.
Parsing may be long, but normal editors never wait for it; only the user-requested import's final
cold transaction may take the cold writer. Restore is not an import/lifecycle mutation — it remains
the separate epoch-replacement workflow. Lifecycle operations may request immediate publication
after commit, but publication failure never rolls back or hides the saved intent.

### 4.3 Cold-to-hot reconcile

On keyboard appearance, order is mandatory:

1. Process restore/epoch replacement.
2. Read the published protocol marker, revisions, fences, and §3.3 lifecycle intents.
3. For each changed table, process pending lifecycle intents in revision order: perform any required
   hot learning backup before destructive table work; apply the newest table fence and newer row
   fences; then perform any requested learning restore.
4. Restore preserved keys and upsert their `learn_outbox` entries in the same hot transaction, with
   `observed_rev` equal to the applied install revision.
5. Commit hot's applied revisions only after that table's data, lifecycle work, and outbox state
   succeed together.
6. Only then try the hot-to-cold learning flush when Full Access is available.

Fence actions are exact, and each acts on **every hot row matching the logical key** (§2.1):

- `delete`: delete all hot rows for the key and its pending outbox entry;
- `upsert`: replace all hot rows for the key with that cold-snapshot key's row(s) and delete the
  matching outbox entry;
- `clear`: clear the hot table and outbox items with `observed_rev < revision`;
- `replace`: replace that hot table exactly and clear outbox items with
  `observed_rev < revision`.

On a table with no table fence, unfenced hot-only rows are never removed by normal row reconcile.
That rule prevents an unrelated learned row from being mistaken for an app deletion. A deliberate
`clear`/`replace` table fence remains the explicit exception.

### 4.4 Hot-to-cold learning flush

Only keyboard processes take `KeyboardFlushLock`, a POSIX `flock` on a descriptor stored beside the
hot database. The app never takes this lock, no database connection is suspended, and no editor
waits for it. Dismiss uses nonblocking acquisition; appearance may retry only within its measured
sync budget. All calls already run on the keyboard's process-local serial sync queue; `flock`
extends that serialization across warm keyboard processes.

Holding that lock across one delivery batch prevents two warm keyboard processes from performing
the stale sequence “both read, newer writer deletes, older writer overwrites.” Learning writes do
not take the lock; the outbox version check safely leaves a concurrent relearn pending.

**The flush writes only to a marked, matching cold lineage.** The hot read snapshot captures hot's
applied cold epoch. Inside the same live-cold write transaction that would accept learning, the
flush reads `editor_fence_protocol` (§3.4) and live cold's epoch. If the marker is absent or the
epochs do not match, it writes nothing and acknowledges nothing. An epoch mismatch schedules normal
`scanAndApply` so restore replacement can clear the old-lineage outbox; it must never deliver that
outbox into the restored database.

Before the §5.1 baseline commits the live marker, cold must drift from the published snapshot only
for legacy reasons. A pre-marker flush would create content drift the baseline could misread as a
legacy mutation, fence the table, and clear its still-pending learning. The gate costs nothing in
the editor: the background baseline starts on the app's first upgraded launch, but editors may show
and mutate live rows while it runs. Only outbox delivery and marked snapshot publication wait.
Seeding and learning remain hot-only writes and are never marker-gated.

For each bounded batch:

1. Acquire `KeyboardFlushLock`; if unavailable, return and let the other flusher or next appearance
   finish.
2. Read the applied cold epoch plus outbox key + version + current hot row pairs in one hot read
   snapshot.
3. Begin a normal live-cold write transaction; never `ATTACH` hot and cold.
4. Re-read the live protocol marker and epoch. If unmarked or epoch-mismatched, roll back the batch,
   acknowledge nothing, release the lock, and return.
5. Reject an item when a newer matching row fence or newer table fence exists.
6. Otherwise update learning-owned columns on **every** cold row matching the key, or insert a
   genuinely new learned key behind the `NOT EXISTS` guard (§2.1).
7. Commit cold first.
8. Acknowledge accepted items and fence-rejected obsolete items only with
   `WHERE ... AND version = capturedVersion`; an epoch/marker rejection acknowledges none.
9. Release the lock and continue only while budget remains.

Keyed `WHERE` semantics converge pre-existing duplicates instead of misrouting a write, and the
`NOT EXISTS` guard plus the flush lock means the flush never creates a new one (§2.1). Cold-first
acknowledgement gives at-least-once delivery: a crash after the cold commit retries an idempotent
update; a relearn during delivery increments the version and remains pending.

### 4.5 Dismissal ordering

`SearchServer` gets one private serial `learningQueue`. The existing mapping score update currently
uses a global utility queue while `postFinishInput()` uses a different background queue; a
completion added only to the latter would not wait for the former. The corrected ordering is:

```swift
func learnRelatedPhraseAndUpdateScore(_ candidate: Mapping) {
    // append the session candidate as today
    learningQueue.async { [weak self] in
        // perform the existing score update and cache refresh
    }
}

func postFinishInput(completion: (() -> Void)? = nil) {
    scorelistLock.lock()
    let capturedScoreList = scorelist
    scorelist.removeAll()
    scorelistLock.unlock()

    learnLock.lock()
    let capturedContinuousLD = ldPhraseListArray
    ldPhraseListArray.removeAll()
    learnLock.unlock()

    learningQueue.async { [weak self] in
        defer { completion?() }
        guard let self else { return }
        self.learnRelatedPhrase(capturedScoreList)

        self.learnLock.lock()
        let capturedRelatedLD = self.ldPhraseListArray
        self.ldPhraseListArray.removeAll()
        self.learnLock.unlock()

        self.learnLDPhraseList(capturedContinuousLD + capturedRelatedLD)
    }
}
```

Because both operations enter the same serial queue, the completion is a barrier after every score
write already submitted by the session. Moving the score update from the concurrent utility queue
onto `learningQueue` serializes writes that previously interleaved — a deliberate behavior change
(strictly stronger ordering), and Task 1's tests must assert the learning results themselves are
unchanged. `KeyboardViewController.viewWillDisappear` captures Full Access on the main thread,
calls `postFinishInput`, then schedules the nonblocking flush on its existing sync queue. Failure
is logged with `NSLog`; the durable outbox remains for appearance. `SearchServer` does not know
about cold databases, signals, locks, or Full Access.

### 4.6 Recovery when the hot DB is missing or corrupt

Accepted learning may exist in live cold before any app revision changes or snapshot publication.
Consequently `cold.limedb` is not a safe recovery source for a lost hot database.

With Full Access, capture live cold's epoch, generation, and per-table revisions while rebuilding
hot from the same consistent SQLite snapshot into a temporary file. Before atomic installation,
create the hot side tables and initialize the new hot lineage in that temporary database:

- `learn_outbox` is empty;
- applied epoch, generation, and table revisions equal the captured live-cold state;
- `legacy_transition_done = 1`, because this new hot contains no legacy hot-only state to classify.

Do not seed an outbox from a recovery copy: every recovered row is already accepted in live cold.
Immediately before installing the temporary file, verify live cold's epoch still matches the
captured epoch; otherwise discard the temporary file and retry. Later app edits or publications
are handled normally by their higher revisions and fences.

Do not wholesale-overwrite a healthy hot database during normal reconcile. Without Full Access,
keep a healthy existing hot database; if hot is absent/corrupt, fail closed until Full Access
returns rather than installing an older published snapshot as the new authority. Restore/epoch is
the only intentional healthy-hot lineage replacement; it clears the old outbox and stamps the same
applied epoch/generation/revision metadata before normal reconcile resumes.

## 5. Upgrade behavior

The old protocol recorded no causal information, so upgrade states must be classified before they
can be preserved. Only one legacy state is genuinely ambiguous: a table with a **pre-upgrade app
edit the keyboard has not applied** *and* hot-only learning. For that state cold wins, matching
the shipped wholesale-copy semantics, and the loss is explicit. Every other table — in particular
the common case of unharvested learning with **no** pending app edit — transitions **losslessly**
by seeding the outbox.

A revision comparison alone cannot classify every pending app edit. The old controllers committed
the row mutation before their separate `markTableChangedAndPublish` call. If the old app died in
that window, live cold changed while its revision did not. The baseline must therefore compare
both revision and actual table content; otherwise app-first can resurrect a delete and
keyboard-first can leave hot and cold split indefinitely at the same revision.

This scoping matters more than it looks: under the shipped pull design, cold received learning
only at editor entry, so a typical upgrading user's hot holds everything learned since they last
opened that table's editor — and a Full-Access-off user's hot holds *all* of their learning, with
no way to flush before the app runs. A baseline that reset every table to cold would silently
destroy it. The baseline therefore fences only what the app can attest from live cold versus the
last published snapshot: a revision advance **or a content difference**. The keyboard classifies
everything else where both revisions are visible.

### 5.1 App side: scoped baseline

On the first upgraded app open, start the baseline on a background migration queue. It must finish
before the app publishes any snapshot carrying the new marker, but it must **not** gate editor
presentation or live-cold editing:

1. Open separate read-only connections and stable read transactions for live cold and the last
   published snapshot. For every editor-managed table, capture both revisions and calculate:

   ```text
   needsReplace = liveRevision > publishedRevision
               OR liveContentMultiset != publishedContentMultiset
   ```

   Content equality covers all persisted table columns except `_id`, preserves `NULL`, and
   preserves duplicate multiplicity. Implement it as sorted/grouped rows plus `COUNT(*)` (or an
   equivalent exact comparison); `EXCEPT` alone is insufficient because it collapses duplicates,
   and a probabilistic digest must not be the sole proof of equality. A missing valid data table
   counts as different; an incompatible schema is a typed migration failure. Compare only
   editor-managed data tables, never whole database files or sync metadata.
2. In one short live-cold write transaction, for each `needsReplace` table allocate
   `R = max(currentLiveRevision, publishedRevision) + 1`, store `R` as the live table revision, write
   `editor_table_fence(tbl, 'replace', R)`, and delete row fences for that table with revision
   `<= R`. Re-read current live revisions before committing: a table changed by the upgraded app
   after the comparison must already carry its atomic §4.2 fence; a changed revision without that
   fence aborts the baseline for retry. Set `editor_fence_protocol = 1` in that same transaction.
3. Publish `cold.limedb` immediately. The marker and every baseline fence therefore become visible
   together.
4. Delete obsolete editor request/receipt/probe artifacts and the unversioned lifecycle inbox only
   after marked publication succeeds.

If the snapshot cannot be read or the exact comparison fails, do not set the marker and do not
publish a marked snapshot; return a typed retryable migration failure. There is no schema
migration — `user_version` stays 105 — but the first upgraded launch necessarily pays one exact
read pass because the old protocol left no cheaper trustworthy signal. The pass runs once per
lineage on independent read connections, never on editor entry; only publication queues behind
it. A concurrent keyboard learning flush cannot perturb the comparison: the flush is marker-gated
(§4.4) and the marker does not exist until this baseline commits, so every content difference the
comparison sees has a legacy cause. Tables whose captured revision and content both
match keep their revision and get **no baseline fence**, so their pending hot learning is preserved
by the keyboard-side seed below. A concurrent upgraded-editor mutation already has its own fence
and is included in the first marked publication.

A content difference may be learning that an old keyboard already harvested into live cold but
the app had not published. Fencing it is safe because live cold already contains that learning.
If the same table also has newer hot-only learning, the histories are indistinguishable; cold wins
that table as the explicitly bounded legacy ambiguity.

### 5.2 Keyboard side: one-time transition

Runs on the first new-version keyboard appearance, gated by hot
`sync_meta.legacy_transition_done` — the keyboard's own marker, so the transition runs exactly
once per hot lineage regardless of which side upgraded first. Per editor-managed table, with
hot's applied revision and the snapshot's revision both visible:

1. **Fenced table** — not transition work: normal §4.3 fence application handles it (`replace`
   resets the table and clears outbox items with older `observed_rev` — the documented cold-wins
   loss, confined to tables whose revision or content proved live/published drift).
2. **Unfenced revision gap** (a published pre-upgrade edit the keyboard never applied): run the
   shipped legacy wholesale copy once. This is exactly what the old version would have done on
   its next scan, including its known loss of unharvested learning in that one table.
3. **No gap** (the common case): seed every hot-vs-snapshot difference — hot-only keys and
   score-changed keys — into `learn_outbox` with `observed_rev` = hot's applied revision and
   `version = 1`, via the §3.1 idempotent upsert. Seeding writes only hot, so it needs no Full
   Access and is never marker-gated; the seeded set flushes through §4.4 once Full Access allows
   **and cold carries the protocol marker** — delivery waits for the app's first upgraded launch,
   which is exactly what keeps the §5.1 content comparison meaningful.
4. Stamp `legacy_transition_done` after the last table.

Both upgrade orders converge on the same final state, including a legacy mutation committed
without its revision bump:

- **App first:** the baseline detects revision-ahead and same-revision content drift, publishes a
  `replace` fence for each such table, and the keyboard applies those fences before seeding only
  truly gapless/unfenced learning.
- **Keyboard first:** the transition runs against the pre-baseline snapshot (gap drain + seed +
  hot marker). The later app baseline detects unpublished mutations by revision **or content**;
  applying those `replace` fences clears exactly those tables' older seeded items by
  `observed_rev`. Cold-wins lands on precisely the attested mutation set, while every unchanged
  table's seeded learning stays pending because its revision never moved.

After the transition, §3.4 applies in full force: an unfenced revision advance on a marked
lineage is an invariant violation and the keyboard fails closed. The transition is the single
sanctioned consumer of unfenced legacy gaps.

### 5.3 Interrupted transition and bounded ambiguity

- Live marker/fences committed but snapshot not published: the next app launch detects live
  revisions ahead of the snapshot and republishes.
- Baseline comparison or transaction interrupted before commit: no marker is visible; the next app
  launch reruns the exact comparison.
- Keyboard transition interrupted: the hot marker is unset, so it reruns; the wholesale copy is
  idempotent and the seed upserts collapse.
- Old backup restored: the transactional epoch check blocks every stale old-lineage flush, epoch
  replacement clears pending hot learning, the app writes a fresh scoped baseline, and the new hot
  lineage stamps applied metadata plus its transition marker with an empty seed.
- Old app crash after a row mutation but before `markTableChangedAndPublish`: the live-versus-
  published content comparison marks the table `replace` even when both revisions are equal.
  Add, update/rename, delete, and clear therefore converge in both upgrade orders instead of being
  resurrected or left split.
- Legacy unversioned lifecycle inbox present: new code does not replay an unknowable stale/pending
  record. The scoped baseline or legacy revision-gap drain makes final cold table state win in both
  upgrade orders, then marked publication removes the old file. A pending restore-learning option
  may be lost only on that already-ambiguous replaced table.
- **Accepted ambiguity:** when a table has both an attested unpublished cold mutation and newer
  hot-only learning, the old protocol contains no ordering evidence. The scoped `replace` makes
  cold win that table. No other table loses pending learning.

## 6. What is removed and what remains

After native gates pass, delete the editor-only issue #209 ownership path:

- database suspension/resume and access-drain counters;
- editor request/receipt files and polling;
- hidden keyboard probe and fixed 1.5-second delay;
- editor refresh session gate;
- attached hot-to-cold harvest writer;
- refresh-failure read-only editor state;
- unversioned App Group lifecycle inbox after marked publication;
- tests that assert the removed handshake.

Keep the existing restore/epoch, generation, backup, IM lifecycle **behavior**, emoji, `im.json`,
and cross-process reopen behavior. Lifecycle transport moves from the unversioned file to §3.3's
atomic cold table. Keep a small keyboard-only `flock` helper for learning delivery; it does not
recreate issue #209 because the app and editor never participate.

Accepted debt: `editor_fence` retains at most one latest row per distinct logical key ever edited,
and the much-lower-volume lifecycle table retains revisioned install/delete intents. Table
`clear`/`replace` compacts superseded row fences, but ordinary edits can grow the fence set. Do not
add a cross-process acknowledgement/GC protocol in this PR. Measure both tables' row counts and file
size in Task 5, record a backlog threshold, and add GC only if device data shows retention is
materially costly.

Out of scope remains unrelated DBServer sentinel-return and `try?` debt outside changed mutation,
import, and synchronization paths.

---

## Task ordering and PR boundaries

All five tasks are iOS-only and form **one PR**; no task touches Android, `user_version`, or the
seeded assets. Duplicate cleanup is a separate, unscheduled effort — [DB_DEDUPE.md](DB_DEDUPE.md).

### Task 1: Atomic hot learning and serialized learning completion

**Files:**

- Modify: `LimeIME-iOS/Shared/Database/LimeDB.swift`
- Modify: `LimeIME-iOS/Shared/Database/LimeDBProtocol.swift`
- Modify: `LimeIME-iOS/Shared/Search/SearchServer.swift`
- Modify: `LimeIME-iOS/LimeTests/LimeDBTest.swift`
- Modify: `LimeIME-iOS/LimeTests/SearchServerTest.swift`

**Interfaces:**

- Produces: hot-role learning tracking and `postFinishInput(completion:)` whose completion follows all submitted session learning.

- [x] **Step 1: Add failing tests.** Cover atomic row+outbox commit, rollback on outbox failure, version increment on relearn, all three learning entry points, exclusion of bulk imports and legacy `word IS NULL` / `cword IS NULL` sentinels, atomic `<table>_user` restore plus outbox upsert, and completion after the prior mapping-score write plus related/LD writes. Assert the serialized `learningQueue` produces the same learning results the previous concurrent dispatch produced (the §4.5 behavior change is ordering only).
- [x] **Step 2: Run the two focused XCTest classes; confirm failure.**

```bash
xcodebuild test -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LimeTests/LimeDBTest -only-testing:LimeTests/SearchServerTest
```

- [x] **Step 3: Add `learn_outbox` and hot-role tracking inside existing `LimeDB` transactions.** Do not add post-commit callbacks. `user_version` stays 105.
- [x] **Step 4: Move the existing asynchronous learning work onto one private serial `learningQueue` and add the generic completion shown in §4.5.**
- [x] **Step 5: Rerun the focused tests; expected result is PASS.**
- [x] **Step 6: Commit.**

```bash
git add LimeIME-iOS/Shared/Database/LimeDB.swift LimeIME-iOS/Shared/Database/LimeDBProtocol.swift LimeIME-iOS/Shared/Search/SearchServer.swift LimeIME-iOS/LimeTests/LimeDBTest.swift LimeIME-iOS/LimeTests/SearchServerTest.swift
git commit -m "feat(ios): journal keyboard learning atomically"
```

### Task 2: Atomic cold editor and table-lifecycle intent

**Files:**

- Modify: `LimeIME-iOS/Shared/Database/DBServer.swift`
- Modify: `LimeIME-iOS/Shared/Database/LimeDB.swift`
- Modify: `LimeIME-iOS/Shared/Database/SyncContract.swift`
- Modify: `LimeIME-iOS/LimeSettings/Controllers/ManageImController.swift`
- Modify: `LimeIME-iOS/LimeSettings/Controllers/ManageRelatedController.swift`
- Modify: `LimeIME-iOS/LimeSettings/Views/RecordListView.swift`
- Modify: `LimeIME-iOS/LimeSettings/Views/RelatedListView.swift`
- Modify: `LimeIME-iOS/LimeSettings/Controllers/SetupImController.swift`
- Modify: `LimeIME-iOS/LimeTests/ManageImControllerTest.swift`
- Modify: `LimeIME-iOS/LimeTests/ManageRelatedControllerTest.swift`
- Modify: `LimeIME-iOS/LimeTests/SetupImControllerTest.swift`
- Modify: `LimeIME-iOS/LimeTests/DBServerTest.swift`
- Modify: `LimeIME-iOS/LimeTests/ColdPublisherTest.swift`

**Interfaces:**

- Produces: `performEditorMutation(_:) throws`, `performTableLifecycleMutation(_:) throws`, and
  `publishPendingEditorChanges() throws`.

- [x] **Step 1: Add failing atomicity tests.** Inject row, staging install/drop, §3.3 lifecycle-table insert, fence, revision, and publication failures. Assert record mutations and table lifecycle mutations each commit their data/fence/revision/lifecycle state together, UI changes only after success, and publication failure does not roll back live intent.
- [x] **Step 2: Add editor and importer fixtures.** Cover rename, clear-then-add, three-edits-one-publication, `.cin`, `.lime`, `.limedb`/`.zip`, install/replace/delete, and a crash before the final lifecycle transaction. The duplicate fixture remains tolerance-only: editor delete removes every matching row, editor update converges them, and editor add of an existing key is rejected by the `NOT EXISTS` check.
- [x] **Step 3: Implement the smallest editor and lifecycle mutation enums plus §3.3's additive cold table.** Route both record controllers through `performEditorMutation(_:)`; route every `SetupImController` import/install/replace/delete path through staging plus `performTableLifecycleMutation(_:)`. Remove every separate mutate/import-then-`markTableChangedAndPublish` sequence. Restore stays on the epoch workflow.
- [x] **Step 4: Publish record edits only on editor exit and Settings background; lifecycle operations may publish immediately after their atomic commit. Recover any live-revision-versus-snapshot gap on next launch.**
- [x] **Step 5: Run the five focused XCTest classes; expected result is PASS.**

```bash
xcodebuild test -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LimeTests/ManageImControllerTest -only-testing:LimeTests/ManageRelatedControllerTest -only-testing:LimeTests/SetupImControllerTest -only-testing:LimeTests/DBServerTest -only-testing:LimeTests/ColdPublisherTest
```

- [x] **Step 6: Commit.**

```bash
git add LimeIME-iOS/Shared/Database/DBServer.swift LimeIME-iOS/Shared/Database/LimeDB.swift LimeIME-iOS/Shared/Database/SyncContract.swift LimeIME-iOS/LimeSettings/Controllers/ManageImController.swift LimeIME-iOS/LimeSettings/Controllers/ManageRelatedController.swift LimeIME-iOS/LimeSettings/Controllers/SetupImController.swift LimeIME-iOS/LimeSettings/Views/RecordListView.swift LimeIME-iOS/LimeSettings/Views/RelatedListView.swift LimeIME-iOS/LimeTests/ManageImControllerTest.swift LimeIME-iOS/LimeTests/ManageRelatedControllerTest.swift LimeIME-iOS/LimeTests/SetupImControllerTest.swift LimeIME-iOS/LimeTests/DBServerTest.swift LimeIME-iOS/LimeTests/ColdPublisherTest.swift
git commit -m "feat(ios): record atomic cold intent"
```

### Task 3: Causal reconcile, locked flush, and hot recovery

**Files:**

- Modify: `LimeIME-iOS/Shared/Database/TableSyncEngine.swift`
- Modify: `LimeIME-iOS/Shared/Database/SyncContract.swift`
- Modify: `LimeIME-iOS/Shared/Database/ColdPublisher.swift`
- Modify: `LimeIME-iOS/LimeTests/TableSyncEngineTest.swift`
- Modify: `LimeIME-iOS/LimeTests/SyncContractTest.swift`

**Interfaces:**

- Consumes: hot outbox and cold fences.
- Produces: `scanAndApply(hasFullAccess:)`, `flushPendingLearning(hasFullAccess:)`, and keyboard-only `KeyboardFlushLock`.

- [x] **Step 1: Add failing interleave tests.** Include unrelated delete plus missed learning, same-key delete, learning after applied delete, clear then later add, delete-with-learning-backup plus reinstall in one snapshot, preserved-row restore plus outbox delivery, stale outbox version, cold commit before crash, two simulated keyboard processes flushing one key, restore before a stale dismissal flush, and epoch replacement between batch read and cold write. Both epoch races must write and acknowledge nothing from the old lineage.
- [x] **Step 2: Add duplicate-tolerance and recovery tests.** Duplicate fixture: cold holds two rows for one key — a flush score update converges both and inserts nothing; a fence `delete` removes both from hot. Recovery: flush learning into live cold, acknowledge it, delete/corrupt hot before any app edit, rebuild, and assert the learned row remains; also assert the rebuilt hot starts with empty outbox, captured applied epoch/generation/revisions, and `legacy_transition_done = 1`, then applies a later editor fence normally.
- [x] **Step 3: Implement revision-ordered §3.3 lifecycle handling around table-fence-first reconcile and fail closed on a marked unfenced revision gap.** Reuse `<table>_user`; restored keys must journal outbox state atomically. Remove normal wholesale replacement of editor tables.
- [x] **Step 4: Refactor the existing `flock` code into keyboard-only `KeyboardFlushLock` and implement the bounded cold-first flush in §4.4** with keyed `WHERE` updates, `NOT EXISTS` inserts, and marker-plus-epoch validation inside the cold transaction. Use `NSLog` for typed retryable failures; do not add a logging subsystem.
- [x] **Step 5: Rebuild a missing/corrupt hot DB from live cold only when Full Access permits it, initializing all applied lineage/revision/transition metadata exactly as §4.6 specifies.**
- [x] **Step 6: Run `TableSyncEngineTest` and `SyncContractTest`; expected result is PASS.**

```bash
xcodebuild test -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LimeTests/TableSyncEngineTest -only-testing:LimeTests/SyncContractTest
```

- [x] **Step 7: Commit.**

```bash
git add LimeIME-iOS/Shared/Database/TableSyncEngine.swift LimeIME-iOS/Shared/Database/SyncContract.swift LimeIME-iOS/Shared/Database/ColdPublisher.swift LimeIME-iOS/LimeTests/TableSyncEngineTest.swift LimeIME-iOS/LimeTests/SyncContractTest.swift
git commit -m "feat(ios): reconcile cold and hot causally"
```

### Task 4: Dismiss push, appearance retry, immediate editors, and honest upgrade

**Files:**

- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`
- Modify: `LimeIME-iOS/LimeSettings/AppDelegate.swift`
- Modify: `LimeIME-iOS/LimeSettings/Views/RecordListView.swift`
- Modify: `LimeIME-iOS/LimeSettings/Views/RelatedListView.swift`
- Modify: `LimeIME-iOS/LimeSettings/Controllers/SetupImController.swift`
- Modify: `LimeIME-iOS/Shared/Database/DBServer.swift`
- Modify: `LimeIME-iOS/Shared/Database/ColdPublisher.swift`
- Modify: `LimeIME-iOS/Shared/Database/TableSyncEngine.swift`
- Modify: `LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift`
- Modify: `LimeIME-iOS/LimeTests/RecordEditingCapabilityTest.swift`
- Modify: `LimeIME-iOS/LimeTests/TableSyncEngineTest.swift`

**Interfaces:**

- Consumes: Tasks 1-3.
- Produces: best-effort dismiss push, appearance catch-up, marker-gated upgrade, and editor entry with no keyboard dependency.

- [x] **Step 1: Add failing lifecycle tests.** Dismiss completion must run before flush; killed dismiss leaves the outbox; appearance drains it; Full Access off retains it; editor load creates no probe, request, receipt, suspension, or deliberate delay; and an intentionally blocked baseline read must not block editor presentation or an atomic editor mutation (only publication waits).
- [x] **Step 2: Add old-state fixtures for both upgrade orders.** Cover a legacy revision gap; hot-only learning on a revision-and-content-equal table (must seed and deliver losslessly); revision-ahead unpublished mutation; same-revision legacy add, update/rename, delete, and clear (each must be detected by exact content comparison and fenced); previously harvested learning present in live cold but absent from the snapshot (must survive the fence); an unversioned legacy lifecycle file (ignored and removed after cold-wins marked publication); a flush attempted against unmarked cold (no-op; its pending learning survives the subsequent baseline unfenced); a Full-Access-off upgrade (seed retained, nothing wiped); failed comparison; interrupted publication; interrupted keyboard transition (reruns idempotently); version-skipping restore; and app-first versus keyboard-first convergence to the same final state.
- [x] **Step 3: Wire dismissal to the nonblocking flush and appearance to app-first reconcile then retry.**
- [x] **Step 4: Implement the app-owned scoped baseline on independent stable read connections (exact live-versus-published comparison, short marker transaction, and `replace` fences only for revision-ahead or content-different tables) and the keyboard-side one-time transition (unfenced-gap drain, lossless seed, `legacy_transition_done` marker) exactly as §5 specifies.** Serialize publication, not editor entry, behind the baseline. Assert cold-wins is confined to fenced and gap tables, same-revision legacy mutations converge in both upgrade orders, and unchanged-table learning survives to delivery.
- [x] **Step 5: Remove editor refresh and relay/Full-Access gating so both record and related editors load live cold immediately and stay editable whenever cold opens. Full Access becomes delivery status only.**
- [x] **Step 6: Run the three focused XCTest classes; expected result is PASS.**

```bash
xcodebuild test -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LimeTests/KeyboardViewControllerTest -only-testing:LimeTests/RecordEditingCapabilityTest -only-testing:LimeTests/TableSyncEngineTest
```

- [x] **Step 7: Commit.**

```bash
git add LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift LimeIME-iOS/LimeSettings/AppDelegate.swift LimeIME-iOS/LimeSettings/Controllers/SetupImController.swift LimeIME-iOS/LimeSettings/Views/RecordListView.swift LimeIME-iOS/LimeSettings/Views/RelatedListView.swift LimeIME-iOS/Shared/Database/DBServer.swift LimeIME-iOS/Shared/Database/ColdPublisher.swift LimeIME-iOS/Shared/Database/TableSyncEngine.swift LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift LimeIME-iOS/LimeTests/RecordEditingCapabilityTest.swift LimeIME-iOS/LimeTests/TableSyncEngineTest.swift
git commit -m "feat(ios): open editors without keyboard wait"
```

### Task 5: Delete issue #209 machinery and run final gates

**Files:**

- Modify: `LimeIME-iOS/Shared/Database/SyncContract.swift`
- Modify: `LimeIME-iOS/Shared/Database/TableSyncEngine.swift`
- Modify: `LimeIME-iOS/Shared/Database/DBServer.swift`
- Modify: `LimeIME-iOS/LimeSettings/Controllers/SetupImController.swift`
- Modify: `LimeIME-iOS/LimeSettings/Views/RecordListView.swift`
- Modify: `LimeIME-iOS/LimeSettings/Views/RelatedListView.swift`
- Modify: `docs/IOS_DB_COLD_HOT.md`
- Modify: `docs/#209_ISSUE.md`
- Modify: `docs/BACKLOG.md`

**Interfaces:**

- Consumes: all prior tasks.
- Produces: final implementation and documentation matching the source tree.

- [x] **Step 1: Delete the obsolete ownership and handshake code listed in §6 plus its source-contract tests.** Preserve `KeyboardFlushLock`.
- [x] **Step 2: Run static checks.** No live editor path may reference suspension, refresh receipts, probe delay, or attached harvest; no source change touches `user_version`, Android, or `Database/` assets.

```bash
rg -n "suspendColdAccess|harvestEditorRefresh|refreshTableFromKeyboard|EditorRefreshRequest|EditorRefreshReceipt|imLifecycleInbox" LimeIME-iOS --glob '*.{swift,m,mm,h}'
git diff --stat master -- LimeStudio Database
```

- [x] **Step 3: Run the full native suite in one process.**

```bash
xcodebuild test -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LimeTests
```

- [x] **Step 4: Build both iOS targets.**

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' build
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeKeyboard -destination 'generic/platform=iOS Simulator' build
```

- [x] **Step 5 (simulator proxies recorded; physical-device pass = maintainer residual): Measure on an older supported iPhone.** Record editor time-to-first-editable-row while the one-time baseline is running; total baseline comparison time for the largest installed table set; typing overhead from atomic outbox writes; dismiss flush time for 1, 100, 500, and 2,000 pending keys; appearance recovery; one-snapshot behavior across a multi-edit session; and `editor_fence` plus `im_lifecycle_intent` row-count/file-size growth after representative workloads. Put a measured GC threshold in `BACKLOG.md`; do not add GC in this PR.
- [x] **Step 6: Update the three architecture/debt documents with measured results and exact remaining debt, then commit.**

```bash
git add LimeIME-iOS docs/IOS_DB_COLD_HOT.md docs/#209_ISSUE.md docs/BACKLOG.md
git commit -m "refactor(ios): remove editor refresh ownership path"
```

## 7. Final acceptance matrix

| Case | Required result |
| --- | --- |
| Pre-existing duplicate logical rows in cold or hot | keyed operations converge them; sync never creates a new duplicate; `word IS NULL` sentinels untouched |
| Legacy mapping/related sentinel (`word IS NULL` / `cword IS NULL`) | never becomes a row outbox/fence key; table `clear`/`replace` still carries it |
| Learning transaction fails during outbox write | neither data nor outbox commits |
| Relearn occurs during flush | stale delivery may land; newer version remains pending |
| Two keyboard processes flush one key | no stale final overwrite and no new duplicate row |
| App deletes `D`; unrelated learned `L` is pending | `D` absent, `L` preserved and delivered |
| Same key learned before app delete | app delete wins |
| Same key learned after hot applies delete | later learning may recreate it |
| Clear at `R`, add at `R+1` before publication | clear applies first; later add remains |
| `.cin`/`.lime`/`.limedb` install, replace, or delete fails before lifecycle commit | target data, lifecycle intent, revision, and table fence all remain at the prior committed state |
| Preserve-learning delete then reinstall arrives in one snapshot | hot learning is backed up before replace, restored afterward, and journaled to outbox at the install revision |
| Learning accepted, then hot is lost before app edit | live-cold rebuild contains the learning |
| Hot rebuild from live cold | empty outbox; applied epoch/generation/revisions match the copied state; transition is complete |
| Snapshot is older than accepted live-cold learning | healthy hot is not wholesale-overwritten |
| Dismissal is killed | editor still opens; next appearance retries the outbox |
| Full Access is off | editor remains editable; no App Group flush is attempted |
| Three editor mutations | one exit/background snapshot publication |
| Post-transition unfenced revision gap on a marked lineage | keyboard fails closed and does not advance applied revision |
| Legacy table with an unpublished or unapplied app edit | cold-wins via scoped fence or transition drain; loss documented, confined to that table |
| Legacy add/update/delete/clear committed without revision bump | exact content drift creates a new-revision `replace` fence; both upgrade orders converge to live cold |
| Legacy `clear` committed without revision bump | cleared rows are neither reseeded nor retained in hot |
| Baseline content comparison cannot complete exactly | no protocol marker is published; migration remains retryable |
| Unversioned legacy lifecycle file exists during upgrade | new protocol does not replay it; cold wins the ambiguous table in both orders and removes the file after marked publication |
| Flush attempted before the baseline marker exists | no cold write occurs; pending learning survives the baseline unfenced and delivers after the marker |
| Editor opens while one-time baseline scan is running | live rows are immediately editable; only marked snapshot publication waits |
| Upgrade: gapless table with unharvested learning | seeded to the outbox and delivered; no loss |
| Upgrade with Full Access off | seed retained pending; no learning wiped |
| App-first and keyboard-first upgrade orders | converge to the identical final state |
| Restore occurs before or during an old-lineage flush | epoch mismatch writes and acknowledges nothing; replacement wins and old outbox is cleared |
| `user_version`, Android source, `Database/` assets | byte-identical to master |

### 7.1 Amendment acceptance matrix (A1/A2)

The A1/A2 gate races cannot be staged on a physical device on demand, so every row is
proven by a named test (unit tests for the capability/count/codec logic; source-contract
tests, per the existing `EditorPublishSourceTest` pattern, for SwiftUI/keyboard wiring
that cannot be unit-instantiated). All in `LimeTests`.

| Case | Required result | Covering test |
| --- | --- | --- |
| A1: Full Access off or LIME not the active keyboard | entry/viewing immediate; mutations disabled; learned scores masked; unlock hint shown | `RelayActiveStateTest.testDefaultsToReadOnlyUntilActiveFullAccessAndDrainAreProven`, `RecordEditingCapabilityTest.testLiveEditingRequiresActiveConfirmedOnGate`, `EditorSyncGateSourceTest.testEditorViewsGateMutationsAndReloadOnUnlock` |
| A1: forced-live UITest launch argument (simulator seam) | capability resolves live without device-only FA | `RecordEditingCapabilityTest.testForcedLiveEditingLaunchArgument` |
| A2: relay answer reports `pend > 0` (outbox not drained) | editing stays read-only; editors show the syncing state; app re-probes with a bounded, reset-on-foreground budget | `RelayActiveStateTest.testLiveRequiresDrainedOutbox`, `EditorSyncGateSourceTest.testSettingsConsumesPendAndReprobesBoundedly`, `EditorSyncGateSourceTest.testEditorViewsGateMutationsAndReloadOnUnlock` |
| A2: `pend` absent (pre-A2 payload) or `-1` (count failure) | read-only — editing never unlocks on an unproven drain | `RelayPayloadTest.testDecodeDefaultsPendingSyncToNilWhenAbsent`, `RelayActiveStateTest.testLiveRequiresDrainedOutbox` |
| A2: `pend == 0` with active + FA | editing unlocks; both editors reload so the editable display is post-flush | `RelayActiveStateTest.testLiveRequiresDrainedOutbox`, `EditorSyncGateSourceTest.testEditorViewsGateMutationsAndReloadOnUnlock` |
| A2: payload codec | `pend=` round-trips (0 / N / −1); field is additive — every pre-A2 field and assertion unchanged | `RelayPayloadTest.testEncodeDecodeRoundTripsPendingSyncCount` + the unchanged pre-A2 `RelayPayloadTest` assertions |
| A2: `pendingLearningCount()` | reports outbox size; 0 when the table does not exist | `TableSyncEngineTest.testPendingLearningCountReportsOutboxSize` |
| A2: FA-on flush | drains the outbox; count returns 0 (the unlock condition converges) | `TableSyncEngineTest.testFlushDrainsPendingLearningCountToZero` |
| A2: FA-off flush attempt | cannot drain; count stays non-zero; cold unchanged (editing stays locked) | `TableSyncEngineTest.testFlushWithoutFullAccessLeavesPendingLearningCount` |
| A2: keyboard answer path | relay answer includes the live count (`pendingSync:`) | `EditorSyncGateSourceTest.testKeyboardAnswerReportsPendingLearningCount` |
| A3: production keyboard datasource opens (initial, reopen, backup/restore rebinds) | carry the hot role — learning driven through the production `DBServer` → `SearchServer` seam journals `learn_outbox` (PR #223 merge-review blocker: fixture-only flags had masked that no production open passed `tracksHotLearning`) | `DBServerTest.testKeyboardRoleRuntimeJournalsLearningThroughProductionSeam` |
| A3: app (cold-role) datasource through the same open path | never creates or populates `learn_outbox` | `DBServerTest.testAppRoleRuntimeDoesNotCreateLearnOutbox` |

This proposal is not implemented until all task gates pass at one exact source SHA. Issue #209
remains open until the old editor ownership path is removed and both Record and Related editor flows
pass the device measurements with Full Access on and off.

## Amendment A1 (2026-08-02) — editor mutation gate restored (maintainer decision)

After on-device verification (WJIP17) confirmed immediate editor entry, the maintainer
identified an accepted-risk case as unacceptable in practice: with learning pending
(FA off, or LIME not the active keyboard), the editor displays last-flushed scores; a user
editing a score from that stale display silently supersedes newer undelivered learning
(§2.2 "editor value wins"). Decision: restore the original mutation gate rather than a
warn-on-save.

**What changes (UI only):**

- `RecordListView` / `RelatedListView` re-bind `canEdit` to
  `RelayActiveState.editingCapability == .live` (Full Access confirmed-on **and** LIME the
  active keyboard, resolved by the kept relay layer). Add / edit / delete / clear are
  disabled and learned scores are masked (`—`) when not live, with the original unlock
  hint text. `IMDetailView`'s existing `canClearRelated` gate becomes consistent again.

**What deliberately does NOT return:**

- No editor-entry probe wait, no request/receipt handshake, no cold suspension, no
  read-only-on-timeout: entry and viewing remain immediate; the capability flips
  reactively from the already-running relay heartbeat/probe state.
- The sync protocol (§2–§5) is unchanged — fences, outbox, marker/epoch-gated flush, and
  the §2.2 conflict rules still resolve any conflict that slips through; the gate only
  reduces the frequency of stale-informed edits, it is not a correctness mechanism.

**Supersedes** the "Full Access becomes delivery status only" wording in Task 4 Step 5 and
the matching §7 rows ("Full Access is off → editor remains editable" becomes "editor
remains viewable; mutations require live capability").

## Amendment A2 (2026-08-02) — editing additionally gated on a drained outbox (maintainer decision)

A1 closed the FA-off / not-active stale-edit window, but left a residual one: the relay
answer (the unlock evidence) and the appearance flush both run when the keyboard loads for
the probe, yet are **not sequenced** — with a long outbox the answer can arrive before the
flush finishes, unlocking editing while undelivered learning still exists. §2.2 then
resolves any collision deterministically (fence wins, stale item rejected + acknowledged),
but the user made that edit while looking at a stale score. Maintainer decision: rejection
is not good enough — editing must never unlock while stale data can exist.

**Rule:** `canEdit ⇔ FA confirmed-on ∧ LIME active ∧ learn_outbox empty (relay-proven)`.
With the outbox drained at unlock time, every score on screen is fully delivered; the §2.2
rejection branch becomes practically unreachable (only the editor's own search-field
learning during the same seconds can still race, which is irreducible and harmless).

**Mechanism (additive relay field, no handshake resurrection):**

1. **Keyboard** — `TableSyncEngine.pendingLearningCount()`: `COUNT(*)` of `learn_outbox`
   (0 when the table doesn't exist). `answerRelayRequestIfNeeded` includes it in the
   payload as `pend=N`; a count failure reports `-1` (fail-safe: never claims drained).
   The appearance scan (`scanAndApply`) already flushes the whole outbox in one
   transaction FA-on, so the normal answer is `pend=0`.
2. **Payload codec** — `encodeRelayPayload` gains optional `pendingSync`; `decodeRelayPayload`
   gains optional `pend`. Additive: absent field decodes to `nil`; all existing fields and
   assertions unchanged.
3. **App** — `RelayActiveState` stores `pendingSyncCount`; `editingCapability == .live` now
   additionally requires `pendingSyncCount == 0` (nil = unknown = read-only, fail-safe).
   `isSyncPending` distinguishes "active + FA but still draining" so the editors show
   a syncing message instead of the generic unlock hint.
4. **Retry** — if an answer reports `pend > 0`, `LimeSettingsView` re-triggers the root
   relay after a short delay (bounded attempts per foreground session, counter reset on
   `didBecomeActive` and on a drained answer): each probe appearance re-runs the flush, so
   the gate converges to unlocked as the outbox drains. If it never drains (epoch
   mismatch, lock contention), editing stays locked — the fail-safe direction.
5. **Editors** — on capability flip to `.live`, both list views reload, so the unlocked
   display is always post-flush data.

**Scope widening (recorded per plan invariants):** this amendment authorizes exactly three
Amber touches that A1's invariant list forbade — the relay payload codec
(`encodeRelayPayload`/`decodeRelayPayload`, one additive optional field), `RelayActiveState`
(one stored field + capability rule), and `LimeSettingsView` (consume `pend`, bounded
re-trigger). The relay token, probe flow, FA resolution, and Setup-tab active detection are
unchanged — the answer is never delayed or withheld, so detection timing is untouched.
