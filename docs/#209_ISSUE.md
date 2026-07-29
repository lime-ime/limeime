# Issue #209 Analysis: iOS Related-Phrase Editor Becomes Read-Only After a Locked-Database Refresh

## Live issue state

- Issue: https://github.com/lime-ime/limeime/issues/209
- Status: Open
- Labels: `bug`, `Usability`
- Assignee: `jrywu`
- Reporter: maintainer project account `limeimetw`, based on a private support-mail report. The private screenshots are intentionally not reproduced here.
- Reported environment: LIME 6.1.37 on iOS 26.6 RC.
- Rechecked 2026-07-30: the issue remains open with no public comments. Closed retry-only PR #210 and superseded handoff PRs #214–#219 remain in history. Replacement draft PR #220 publishes the final-v6 tree recorded here.

## Problem statement

Opening iOS **Related-Phrase Management** starts the editor-refresh handshake that copies current keyboard-side `related` rows from the hot database into the Settings app's live cold database. In the reported path, the keyboard-side harvest fails with `SQLite error 5: database is locked`. The refresh may then time out, and `RelatedListView` deliberately changes to read-only mode. The user can search the cold data but cannot edit or delete the unwanted related phrase.

This is a confirmed iOS usability defect at the database-concurrency boundary. The locked-database error is direct runtime evidence.

## Root cause: an unserialized cold-database lifecycle

The Settings app and the keyboard extension are separate processes that both open the SAME file — the app's live cold `lime.db`. The handshake had no hand-off: the app never released that file, and the keyboard never provably released it back. Three source-level lifecycle gaps were present on the `master` baseline `dc6ac0cfe23cef4177d3b7f672542c29b73b2d7a`:

1. **Settings kept cold open, and actively read it, during the handshake.** `RelatedListView.onAppear` / `RecordListView.onAppear` called `refreshHotSnapshotIfNeeded()` and `loadPhrases()` / `loadRecords()` in the same body, unordered. The load path (`ManageRelatedController.loadRelated` → `DBServer.searchRelatedForManagement` → `SharedDatabase.current()`) opens the cold connection when it is not already open, and opening is itself a write: `LimeDB.init` runs `PRAGMA journal_mode = WAL`, `migrate()`, `ensureCurrentDatabase()`, and `SharedDatabase.openDatasource` may run `repairKeyboardCatalogIfNeeded`. `SharedDatabase.cachedDatasource` was released only by the restore/full-replace paths, so the app also held the file open for the whole session.
2. **The keyboard's `DETACH` was a silent no-op.** `harvestEditorRefresh` issued `DETACH DATABASE cold_editor` from a `defer` INSIDE GRDB's write transaction and swallowed the error with `try?`. The codebase already documented this failure mode for `applyIncremental` (`TableSyncEngine.swift`, "a DETACH issued inside GRDB's write transaction fails silently"). Release therefore depended on the connection being deallocated, not on an explicit close.
3. **The `.done` receipt did not prove release, and Settings never rebound.** The receipt is what lets Settings unlock editing, but nothing ordered it after a proven release; and the success path only re-ran the loader — it never reopened the connection against the file the keyboard had just rewritten.

### Measured SQLite behavior (executable, this repository)

`scripts/test_issue_209_ios_editor_refresh_lifecycle.py` reproduces the semantics with real SQLite (3.53, Linux host, no Swift, no mocks), while one connection holds an open write on cold and another runs the harvest with cold ATTACHed:

| Lock point | Result | Consequence |
| --- | --- | --- |
| `BEGIN IMMEDIATE` with cold attached | still blocked after more than 0.5s of a 1.0s timeout | one attempt can consume a substantial part of the Settings-side poll budget |
| read→write promotion inside the transaction | fails in less than 0.1s | the busy handler does not wait out the configured timeout once the connection holds a lock on another attached database, so increasing `busy_timeout` does not solve this race |
| plain writer, nothing attached (control) | honours the busy timeout | why re-applying `PRAGMA busy_timeout` never fixed this |
| `DETACH` inside a transaction | `database cold_editor is locked`, cold stays attached | the swallowed error in gap 2 |
| last connection closes | `-wal` / `-shm` removed | observable SQLite behavior pinned by the Linux gate; native lifecycle tests instead use direct datasource state and real competing writes |

Both harvest lock points fail while any Settings-side cold write is open. Retrying them is strictly worse than not racing them: the first burns the budget per attempt, while the second does not wait out the configured timeout. Closed PR #210 proposed a retry-only mitigation; the final branch deliberately does not carry that proposal forward and serializes the lifecycle instead.

Still not established: which Settings-side operation held the lock on the reporter's device, and for how long. The fix removes the whole class of Settings-side contention rather than attributing the single reporter-visible owner.

## Implemented lifecycle fix

The invariant: **Settings hands the cold file over before asking for the harvest; the keyboard hands it back provably free; only then may editing unlock.**

1. `DBServer.suspendColdAccess()` / `resumeColdAccess()` / `isColdAccessSuspended` close the process-local cold connection and keep it closed — `SharedDatabase.current()` returns `nil` while suspended, so no caller can lazily re-open it. Direct cold publishers also reject or skip work while suspended.
2. `EditorRefreshFileLock` uses bounded, descriptor-owned POSIX `flock` on an App-Group lock file as the cross-process ownership boundary. Each process keeps one descriptor per lock path and also serializes its local handles, so closing another descriptor cannot silently release ownership and same-process callers cannot overlap a lock hold. Settings owns close/request publication and later cancellation/reopen; the keyboard owns request re-validation, harvest, close and terminal receipt.
3. `SetupImController.refreshTableFromKeyboard` uses a process-wide async single-flight gate across the complete handshake, so Related and normal IM editors cannot replace each other's request/receipt files while the cross-process lock is handed to the keyboard. It suspends cold BEFORE publishing the request and resumes it before every return; failure keeps editing read-only.
4. `TableSyncEngine.harvestEditorRefresh` now ATTACHes cold outside the transaction, runs the whole diff/write in one hot write transaction, DETACHes outside the transaction, and closes the connection explicitly (`SyncDatabaseConnection.close()`, idempotent) — every error path closes too. Only then does `processEditorRefreshRequestIfNeeded` write the `.done` receipt while still holding ownership. A failed release fails the request rather than reporting done.
5. `RelatedListView` / `RecordListView` expose one entry point, `beginEditorSession()`; `onAppear` no longer launches a cold load beside the handshake. They summon the keyboard and allow the complete request window instead of consuming the one-shot attempt on an early advisory relay state. The first cold load runs only after the handshake and reopen. `isRefreshingHotSnapshot` still gates `canEdit`.
6. The closed retry-only proposal is not carried forward: `editorRefreshBusyRetryWindow`, `editorRefreshBusyRetryBackoff`, `editorRefreshAttemptBusyTimeoutMilliseconds`, `isTransientLockError`, `harvestEditorRefreshAttempt`, `testEditorRefreshRetriesThroughTransientColdWriteLock`, and `scripts/test_issue_209_ios_editor_refresh_retry.py` are absent. The normal 5-second busy timeout remains for independent hot-side learning/commit writes. Normal Settings reopening waits for ownership; after the shared request deadline, failure recovery may reopen cold best-effort rather than leave all Settings readers empty until restart.
7. Android and the existing `(pword, cword)` dirty-key semantics remain unchanged.

## Verification

### Executed locally

- `python3 scripts/test_issue_209_ios_editor_refresh_lifecycle.py` — 9 tests, `OK`. Five execute real SQLite behavior, one spawns a separate process to verify descriptor-owned `flock` contention and release, and three narrowly prevent resurrection of the retired retry workaround or unbounded convenience lock APIs. Lifecycle and ordering behavior is covered by native XCTest rather than broad Swift source-text assertions.
- Related Python suites passed: `test_limedb_order.py --all`, `test_build_emoji_db.py`, `test_custom_im_keyboard_ios.py`, `test_hahacj_limedb.py`, `test_ipad_language_mode_key.py`, `test_number_symbol_layout_ios.py`, `test_phonetic_layout_ios.py`, and `test_tricode_limedb.py`.
- `python3 -m py_compile scripts/*.py` — clean.
- `git diff --check` — clean.

### Xcode Cloud

The review4, review5, and final review6 code were each validated on their exact pushed SHA:

- Run 42, SHA `b4e490f02260127943efdfd0d42ee978254c1db0`: iOS tests passed; iOS archive passed; no required failures; production workflow restored.
- Run 43, SHA `45803f715400c78e3399df1390bb8e657b5a2594`: iOS tests passed; iOS archive passed; no required failures; production workflow restored.
- Run 44, SHA `7d407ab04bc2552e56f396e3f9afecb1419be67e`: iOS tests passed; iOS archive passed; no required failures; production workflow restored.

Run 44 validates the final review6 production and native-test tree. The rebuilt one-commit final-v6 tree is byte-identical to `7d407ab04bc2552e56f396e3f9afecb1419be67e` outside `docs/#209_ISSUE.md` and `docs/BACKLOG.md`, which record the completed validation.

Native coverage includes real same-process lock contention, controller-propagated initial/reacquisition timeout budgets, Related-versus-normal editor single-flight, cold suspension/reopen, ownership-reacquisition recovery, bounded persistent SQLite contention, hot→cold harvest and post-receipt cold writability. Native executable assertions use direct datasource state and real database access; WAL-sidecar removal is asserted separately by the Linux SQLite gate.

### Review gate

- Final strict review6 gate: zero unresolved correctness, compile, test-quality, maintenance, or documentation findings before Run 44.

### Runtime (not started)

1. Reproduce on iOS with Full Access enabled and LIME selected, using the Related-Phrase Management path.
2. Verify the editor becomes writable without a timeout or app restart, and that the list is populated only after the sync indicator clears.
3. Search, edit, add, and delete related phrases after refresh, then leave/background the editor and verify the changes reconcile back to the keyboard.
4. Verify a foreign persistent lock fails within a bounded time with clear state, and that reopening the editor recovers.
5. Start a keyboard sync scan, publish the editor-refresh request only after that scan has passed its editor-refresh step, and verify a follow-up scan still consumes the surviving request instead of leaving Settings to time out read-only. This covers the pre-existing `syncScanInProgress` signal-coalescing risk that the deferred-contention path also relies on.
6. Repeat on iPhone, full iPad, and narrow iPad presentations because the database path is shared but editor lifecycle and keyboard presentation differ.

## Follow-up questions

- Which Settings-side connection and operation held the cold write lock on the reporter's device? The fix removes Settings-side contention as a class, so this is now diagnostic curiosity rather than a blocker.
- While cold is suspended, `SharedDatabase.current()` returns `nil`, so any OTHER screen that reads cold during the handshake window renders empty rather than blocking. The two editors are serialized; audit whether any other concurrently visible surface reads cold during an editor refresh.
- Is an in-screen retry control needed, or is clean recovery on reopen sufficient?

## Platform impact

### iOS

Confirmed affected. The reported failure occurs in the iOS-only cold/hot editor-refresh architecture. The lifecycle fix is in the shared harvest path plus the shared Settings handshake, so it covers every iOS table editor that uses the handshake; the reporter-visible confirmation remains specifically about `related`.

### Android

No corresponding Android defect is established. Android does not use the iOS keyboard-extension/App-Group cold/hot handshake or `TableSyncEngine.harvestEditorRefresh`. Its related-phrase management has separate database and refresh behavior. Android remains unchanged.
