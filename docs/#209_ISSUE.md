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

This is a confirmed iOS usability defect at the database-concurrency boundary. The locked-database error is direct runtime evidence. The exact lock owner and duration have not yet been captured, so the precise source-level cause remains to be proven.

## Current control flow

1. `RelatedListView.refreshHotSnapshotIfNeeded()` focuses the hidden keyboard probe and calls `SetupImController.refreshTableFromKeyboard(stem: "related")`.
2. The controller writes an `EditorRefreshRequest`, signals the keyboard, and polls for a matching receipt.
3. `TableSyncEngine.processEditorRefreshRequestIfNeeded()` calls `harvestEditorRefresh(table:into:)` in the keyboard process.
4. `harvestEditorRefresh` opens the hot database through `SyncDatabaseConnection`, starts a write transaction, attaches the Settings app's live cold `lime.db` as `cold_editor`, computes changed `(pword, cword)` keys, and deletes/inserts those rows in `cold_editor.related`.
5. Any thrown error produces a failed receipt. `RelatedListView` sets `hotRefreshFailed = true`, which forces `editingCapability` to `.readOnly` and disables add/edit/delete for the remainder of that editor presentation.

`SyncDatabaseConnection` configures `PRAGMA busy_timeout = 5000` by default. The existing timeout therefore reduces short contention but does not prove that this cross-process transaction can complete when the cold database remains write-locked for longer, when lock acquisition and request polling interact, or when a busy error needs a whole-transaction retry.

## Likely root cause

The likely failure boundary is the single hot-write transaction in `harvestEditorRefresh` that also writes the attached live cold database. The Settings app and keyboard extension use separate SQLite connections in separate processes. If another Settings-side operation holds a write lock on cold `lime.db`, the keyboard transaction cannot update `cold_editor.related` and eventually throws `SQLITE_BUSY` (`SQLite error 5`).

Important limits on this diagnosis:

- The runtime error proves lock contention, but it does not identify which Settings-side operation owns the lock.
- The shared connection already has a 5-second SQLite busy timeout. Merely adding the same pragma again is not yet an evidence-based fix.
- Existing tests exercise the editor harvest only without a competing cold-database writer, so they cannot distinguish immediate failure, timeout exhaustion, retry behavior, or recovery after the lock is released.

## Implemented issue branch slice

The issue-specific branch now contains the first implementation slice:

1. Real GRDB/XCTest coverage holds a second connection's immediate write transaction on live cold `lime.db`, then covers transient release, bounded persistent failure, and a successful new request after release.
2. `harvestEditorRefresh` retries only GRDB errors whose primary SQLite result is `SQLITE_BUSY` or `SQLITE_LOCKED`; schema, I/O, and integrity failures still escape immediately.
3. Each attempt creates a fresh hot connection and repeats the complete attach/diff/write transaction. A failed attempt is rolled back and its connection is closed before retry.
4. Attempts may start for 3 seconds and use a 500 ms per-attempt busy timeout. Conservatively allowing two timeout-bearing lock points in the final attempt, this caps the retry path near 4 seconds and leaves scheduling/receipt headroom inside the Settings-side 10-second poll and 30-second request TTL.
5. Persistent contention still emits a failed receipt and preserves the fail-safe read-only result. A later request can recover on the same databases after the lock is released.
6. Android and the existing `(pword, cword)` dirty-key semantics remain unchanged.

A direct SQLite runtime experiment on the Linux development host reproduced the key lock behavior independently: after one connection began an immediate write, a second connection first read and then attempted to delete from the same database with a 5-second timeout; the read-to-write promotion returned `database is locked` immediately (`0.000s`). This validates why repeating the existing 5-second pragma is not sufficient and why the complete transaction must be retried.

Xcode is unavailable on the Linux host, so the new XCTest cases have not yet produced executable RED/GREEN results. `scripts/test_issue_209_ios_editor_refresh_retry.py` provides a Linux source-contract gate only; Xcode/Xcode Cloud remains required for Swift compilation and behavioral proof.

## Follow-up questions

- Which Settings-side connection and operation holds the cold-database write lock in the reporter-visible path?
- How long is the lock held on device, and does the failure occur before or after the configured 5-second busy timeout?
- What are `editorRefreshRequestTTL` and `editorRefreshPollTimeout` in the shipped path, and how much bounded retry time can fit without causing the Settings app to discard a late successful receipt?
- Does reopening Related-Phrase Management currently recover once the lock is gone, or does persisted connection/request state keep the editor read-only?
- Is an in-screen retry control needed, or is automatic bounded retry plus clean recovery on reopen sufficient?

## Verification plan

### Automated

1. Add an iOS test that opens the live cold database on a second connection, begins a write transaction, starts a `related` editor refresh, releases the lock before the deadline, and verifies:
   - the refresh waits/retries and returns a `.done` receipt,
   - the learned/new `related` rows are present in cold,
   - no stale attachment or temporary table prevents the next refresh.
2. Add a persistent-lock test that verifies bounded failure, a `.failed` receipt with no partial row changes, and cleanup of the request/transaction state.
3. After releasing the persistent lock, issue a new refresh and verify successful recovery without recreating either database.
4. Keep the existing tests green:
   - `testEditorRefreshHarvestsRelatedRowsByParentChildKey`
   - `testEditorRefreshHarvestsNewAndScoreChangedRowsIntoLiveCold`
   - `testEditorRefreshThenCloseReconcileRoundTripsLearningAndAppEdits`
   - controller receipt-matching and timeout cleanup tests.
5. Run the relevant XCTest target on an iOS simulator or Xcode Cloud. A compile-only result is not runtime proof.

### Runtime

1. Reproduce on iOS with Full Access enabled and LIME selected, using the Related-Phrase Management path while forcing or naturally creating concurrent cold-database access.
2. Verify that transient contention resolves and the editor becomes writable without a timeout or app restart.
3. Search, edit, add, and delete related phrases after refresh, then leave/background the editor and verify the changes reconcile back to the keyboard.
4. Verify persistent contention fails within a bounded time with clear state, and that retrying after the lock is released recovers.
5. Repeat on iPhone, full iPad, and narrow iPad presentations because the database path is shared but editor lifecycle and keyboard presentation differ.

## Platform impact

### iOS

Confirmed affected. The reported failure occurs in the iOS-only cold/hot editor-refresh architecture. It blocks editing and deletion in Related-Phrase Management after the keyboard-to-Settings sync receives `SQLITE_BUSY`. The same handshake is shared by other iOS table editors, so the fix and regression coverage should check whether the concurrency handling belongs in the shared harvest path rather than only the `related` view. The reporter-visible confirmation remains specifically about `related`.

### Android

No corresponding Android defect is established. Android does not use the iOS keyboard-extension/App-Group cold/hot handshake or `TableSyncEngine.harvestEditorRefresh`. Its related-phrase management has separate database and refresh behavior. Android should remain unchanged unless an independent Android reproduction demonstrates a separate locking problem.
