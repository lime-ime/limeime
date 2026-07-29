# Issue #209 Analysis: iOS Related-Phrase Editor Becomes Read-Only After a Locked-Database Refresh

## Live issue state

- Issue: https://github.com/lime-ime/limeime/issues/209
- Status: Open
- Labels: `bug`, `Usability`
- Assignee: `jrywu`
- Reporter: maintainer project account `limeimetw`, based on a private support-mail report. The private screenshots are intentionally not reproduced here.
- Reported environment: LIME 6.1.37 on iOS 26.6 RC.
- No comments, linked commits, or linked pull requests were present when this analysis was prepared on 2026-07-27.

## Problem statement

Opening iOS **Related-Phrase Management** starts the editor-refresh handshake that copies current keyboard-side `related` rows from the hot database into the Settings app's live cold database. In the reported path, the keyboard-side harvest fails with `SQLite error 5: database is locked`. The refresh may then time out, and `RelatedListView` deliberately changes to read-only mode. The user can search the cold data but cannot edit or delete the unwanted related phrase.

This is a confirmed iOS usability defect at the database-concurrency boundary. The locked-database error is direct runtime evidence.

## Root cause: an unserialized cold-database lifecycle

The Settings app and the keyboard extension are separate processes that both open the SAME file — the app's live cold `lime.db`. The handshake had no hand-off: the app never released that file, and the keyboard never provably released it back. Three source-level lifecycle gaps were present at branch HEAD `9659a497`:

1. **Settings kept cold open, and actively read it, during the handshake.** `RelatedListView.onAppear` / `RecordListView.onAppear` called `refreshHotSnapshotIfNeeded()` and `loadPhrases()` / `loadRecords()` in the same body, unordered. The load path (`ManageRelatedController.loadRelated` → `DBServer.searchRelatedForManagement` → `SharedDatabase.current()`) opens the cold connection when it is not already open, and opening is itself a write: `LimeDB.init` runs `PRAGMA journal_mode = WAL`, `migrate()`, `ensureCurrentDatabase()`, and `SharedDatabase.openDatasource` may run `repairKeyboardCatalogIfNeeded`. `SharedDatabase.cachedDatasource` was released only by the restore/full-replace paths, so the app also held the file open for the whole session.
2. **The keyboard's `DETACH` was a silent no-op.** `harvestEditorRefreshAttempt` issued `DETACH DATABASE cold_editor` from a `defer` INSIDE GRDB's write transaction and swallowed the error with `try?`. The codebase already documented this failure mode for `applyIncremental` (`TableSyncEngine.swift`, "a DETACH issued inside GRDB's write transaction fails silently"). Release therefore depended on the connection being deallocated, not on an explicit close.
3. **The `.done` receipt did not prove release, and Settings never rebound.** The receipt is what lets Settings unlock editing, but nothing ordered it after a proven release; and the success path only re-ran the loader — it never reopened the connection against the file the keyboard had just rewritten.

### Measured SQLite behavior (executable, this repository)

`scripts/test_issue_209_ios_editor_refresh_lifecycle.py` reproduces the semantics with real SQLite (3.53, Linux host, no Swift, no mocks), while one connection holds an open write on cold and another runs the harvest with cold ATTACHed:

| Lock point | Result | Consequence |
| --- | --- | --- |
| `BEGIN IMMEDIATE` with cold attached | fails after burning the ENTIRE busy timeout | one attempt can consume the whole Settings-side poll budget |
| read→write promotion inside the transaction | fails in `0.000s` | the busy handler is never invoked once the connection holds a lock on another attached database, so no `busy_timeout` value helps |
| plain writer, nothing attached (control) | honours the busy timeout | why re-applying `PRAGMA busy_timeout` never fixed this |
| `DETACH` inside a transaction | `database cold_editor is locked`, cold stays attached | the swallowed error in gap 2 |
| last connection closes | `-wal` / `-shm` removed | the observable "this process still holds the file" signal used by the XCTests |

Both harvest lock points fail while any Settings-side cold write is open. Retrying them is strictly worse than not racing them: the first burns the budget per attempt, the second cannot be waited out at all. That is why the retry-only slice previously on this branch has been removed in favour of serializing the lifecycle.

Still not established: which Settings-side operation held the lock on the reporter's device, and for how long. The fix removes the whole class of Settings-side contention rather than attributing the single reporter-visible owner.

## Implemented lifecycle fix

The invariant: **Settings hands the cold file over before asking for the harvest; the keyboard hands it back provably free; only then may editing unlock.**

1. `DBServer.suspendColdAccess()` / `resumeColdAccess()` / `isColdAccessSuspended` close the process-local cold connection and keep it closed — `SharedDatabase.current()` returns `nil` while suspended, so no caller can lazily re-open it. Direct cold publishers also reject or skip work while suspended.
2. `EditorRefreshFileLock` uses `flock(LOCK_EX)` on an App-Group lock file as the cross-process ownership boundary. Settings owns close/request publication and later cancellation/reopen; the keyboard owns request re-validation, harvest, close and terminal receipt. If the UI poll expires during an in-flight harvest, Settings blocks on ownership instead of reopening cold underneath it. A keyboard starting late re-reads the request only after acquiring ownership, so it cannot consume a request Settings already cancelled.
3. `SetupImController.refreshTableFromKeyboard` suspends cold BEFORE the request file is written and before `postSyncSignal(.tablesUpdated)`, and resumes it — rebinding to whatever the keyboard committed — only after reacquiring cross-process ownership, before returning on success, failure and timeout alike.
4. `TableSyncEngine.harvestEditorRefresh` now ATTACHes cold outside the transaction, runs the whole diff/write in one hot write transaction, DETACHes outside the transaction, and closes the connection explicitly (`SyncDatabaseConnection.close()`, idempotent) — every error path closes too. Only then does `processEditorRefreshRequestIfNeeded` write the `.done` receipt while still holding ownership. A failed release fails the request rather than reporting done.
5. `RelatedListView` / `RecordListView` expose one entry point, `beginEditorSession()`; `onAppear` no longer launches a cold load beside the handshake. They summon the keyboard and allow the complete request window instead of consuming the one-shot attempt on an early advisory relay state. The first cold load runs only after the handshake and reopen. `isRefreshingHotSnapshot` still gates `canEdit`.
6. The retry-only workaround is gone: `editorRefreshBusyRetryWindow`, `editorRefreshBusyRetryBackoff`, `isTransientLockError`, `harvestEditorRefreshAttempt` and `scripts/test_issue_209_ios_editor_refresh_retry.py` are removed. The normal 5-second busy timeout remains for independent hot-side learning/commit writes; cross-process ownership prevents Settings from reopening cold if that wait extends beyond the UI poll.
7. Android and the existing `(pword, cword)` dirty-key semantics remain unchanged.

## Verification

### Executed on this host (Linux, no Xcode)

- `python3 -m unittest scripts.test_issue_209_ios_editor_refresh_lifecycle` — 20 tests, `OK`. Five are real-SQLite behavior tests; fifteen are source contracts covering quiesce/request ordering, cross-process ownership, safe reopen, suspension-gate enforcement, attach/commit/detach/close ordering, error cleanup, receipt ordering, view load ordering and retry removal.
- Full `scripts/test_*.py` suite: `test_build_emoji_db` 6 passed, `test_custom_im_keyboard_ios` 12 passed, `test_hahacj_limedb` 5 passed, `test_ipad_language_mode_key` 4 passed + 172 subtests, `test_number_symbol_layout_ios` 6 passed + 12 subtests, `test_phonetic_layout_ios` 3 passed, this gate 20 passed.
- `python3 -m py_compile scripts/*.py` — clean.

### Written but NOT yet executed (needs Xcode / Xcode Cloud)

These are real GRDB/XCTest cases against real temp databases, not mocks. None of them has been run: this host has no Swift toolchain, so the lifecycle implementation has **no compile or test execution evidence yet**.

1. `DBServerTest.testSuspendColdAccessClosesTheLiveConnectionUntilResumed` — suspension removes the `-wal` sidecar, a read during suspension neither answers from nor re-opens the file, and resuming rebinds and sees a row another connection wrote meanwhile.
2. `SetupImControllerTest.testRefreshTableFromKeyboardQuiescesColdUntilTheReceiptLands` — at the instant the request file appears, cold's `-wal` is gone and a responder standing in for the keyboard takes an IMMEDIATE write transaction on cold without error; after the call, cold is unsuspended and the harvested row is visible.
3. `SetupImControllerTest.testRefreshTableFromKeyboardReopensColdAfterTimeout` — the timeout path still reopens cold and still serves read-only browsing.
4. `TableSyncEngineTest.testEditorRefreshDetachesAndClosesColdBeforeWritingDoneReceipt` — a watcher samples state the moment the receipt appears: cold's `-wal` is gone and cold is immediately writable by another connection.
5. `TableSyncEngineTest.testEditorRefreshFailsBoundedUnderPersistentColdWriteLock` — a foreign persistent lock fails within 5 seconds, reports the lock cause, and leaves cold byte-identical.
6. `TableSyncEngineTest.testEditorRefreshRecoversOnNextRequestAfterColdWriteLockReleased` — the next request succeeds on the same databases after release.
7. `EditorRefreshViewSourceTest` — both editors call `beginEditorSession()` from `onAppear` and load cold only after the handshake.
8. Existing coverage must stay green, including `testEditorRefreshHarvestsRelatedRowsByParentChildKey`, `testEditorRefreshHarvestsNewAndScoreChangedRowsIntoLiveCold`, `testEditorRefreshThenCloseReconcileRoundTripsLearningAndAppEdits`, and the controller receipt-matching / timeout-cleanup tests.

### Runtime (not started)

1. Reproduce on iOS with Full Access enabled and LIME selected, using the Related-Phrase Management path.
2. Verify the editor becomes writable without a timeout or app restart, and that the list is populated only after the sync indicator clears.
3. Search, edit, add, and delete related phrases after refresh, then leave/background the editor and verify the changes reconcile back to the keyboard.
4. Verify a foreign persistent lock fails within a bounded time with clear state, and that reopening the editor recovers.
5. Repeat on iPhone, full iPad, and narrow iPad presentations because the database path is shared but editor lifecycle and keyboard presentation differ.

## Follow-up questions

- Which Settings-side connection and operation held the cold write lock on the reporter's device? The fix removes Settings-side contention as a class, so this is now diagnostic curiosity rather than a blocker.
- While cold is suspended, `SharedDatabase.current()` returns `nil`, so any OTHER screen that reads cold during the handshake window renders empty rather than blocking. The two editors are serialized; audit whether any other concurrently visible surface reads cold during an editor refresh.
- Is an in-screen retry control needed, or is clean recovery on reopen sufficient?

## Platform impact

### iOS

Confirmed affected. The reported failure occurs in the iOS-only cold/hot editor-refresh architecture. The lifecycle fix is in the shared harvest path plus the shared Settings handshake, so it covers every iOS table editor that uses the handshake; the reporter-visible confirmation remains specifically about `related`.

### Android

No corresponding Android defect is established. Android does not use the iOS keyboard-extension/App-Group cold/hot handshake or `TableSyncEngine.harvestEditorRefresh`. Its related-phrase management has separate database and refresh behavior. Android remains unchanged.
