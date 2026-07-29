#!/usr/bin/env python3
"""Linux gate for issue #209's iOS editor-refresh cold/hot lifecycle.

Two independent parts:

1. `ColdHarvestSQLiteBehaviour` runs REAL SQLite (no mocks, no Swift) and pins the
   database semantics the fix depends on: an open Settings-side write makes the
   keyboard's attached cold write fail immediately, a quiesced cold lets the same
   harvest commit, and `DETACH` inside a transaction fails while `DETACH` after the
   commit succeeds. This is the executable evidence for the lifecycle root cause.

2. `EditorRefreshLifecycleContract` pins the serialized lifecycle in the iOS source
   while Xcode is unavailable on this host. The behavioral proof remains the real
   GRDB/XCTest cases in TableSyncEngineTest / SetupImControllerTest / DBServerTest.
"""

import os
import re
import sqlite3
import tempfile
import time
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
IOS = ROOT / "LimeIME-iOS"
ENGINE = IOS / "Shared/Database/TableSyncEngine.swift"
DBSERVER = IOS / "Shared/Database/DBServer.swift"
SYNC_CONNECTION = IOS / "Shared/Database/SyncConnection.swift"
SYNC_CONTRACT = IOS / "Shared/Database/SyncContract.swift"
SETUP = IOS / "LimeSettings/Controllers/SetupImController.swift"
RELATED_VIEW = IOS / "LimeSettings/Views/RelatedListView.swift"
RECORD_VIEW = IOS / "LimeSettings/Views/RecordListView.swift"
ENGINE_TESTS = IOS / "LimeTests/TableSyncEngineTest.swift"
SETUP_TESTS = IOS / "LimeTests/SetupImControllerTest.swift"
DBSERVER_TESTS = IOS / "LimeTests/DBServerTest.swift"
VIEW_TESTS = IOS / "LimeTests/RecordEditingCapabilityTest.swift"
RETIRED_RETRY_GATE = ROOT / "scripts/test_issue_209_ios_editor_refresh_retry.py"


def swift_body(source: str, signature: str) -> str:
    """Return the brace-balanced body that follows `signature` in Swift source."""
    start = source.find(signature)
    if start < 0:
        raise AssertionError(f"missing Swift declaration: {signature!r}")
    open_brace = source.find("{", start)
    depth = 0
    for index in range(open_brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[open_brace + 1:index]
    raise AssertionError(f"unbalanced braces after {signature!r}")


class ColdHarvestSQLiteBehaviour(unittest.TestCase):
    """Real SQLite proof of the lifecycle the iOS fix serializes."""

    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        base = Path(self.dir.name)
        self.cold = base / "cold-lime.db"
        self.hot = base / "hot-lime.db"
        for path, rows in ((self.cold, [("你", "好", 1)]),
                           (self.hot, [("你", "好", 8), ("天", "氣", 2)])):
            conn = sqlite3.connect(path)
            # LimeDB opens every live database with journal_mode = WAL.
            conn.execute("PRAGMA journal_mode = WAL")
            conn.execute("CREATE TABLE related (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                         "pword TEXT, cword TEXT, score INTEGER DEFAULT 0)")
            conn.executemany("INSERT INTO related (pword, cword, score) VALUES (?, ?, ?)",
                             rows)
            conn.commit()
            conn.close()
        self.addCleanup(self.dir.cleanup)

    def _harvest(self, hot_conn):
        """The keyboard-side hot→cold harvest: read cold, then write cold."""
        hot_conn.execute("BEGIN IMMEDIATE")
        # Read cold first (table probe + dirty-key scan) — the read→write promotion.
        hot_conn.execute("SELECT COUNT(*) FROM cold_editor.related").fetchone()
        hot_conn.execute("DELETE FROM cold_editor.related")
        hot_conn.execute("INSERT INTO cold_editor.related (pword, cword, score) "
                         "SELECT pword, cword, score FROM main.related")
        hot_conn.execute("COMMIT")

    def _locked_settings_connection(self):
        settings = sqlite3.connect(self.cold, timeout=5.0)
        settings.execute("BEGIN IMMEDIATE")
        settings.execute("INSERT INTO related (pword, cword, score) VALUES ('鎖', '住', 1)")

        def rollback():
            if settings.in_transaction:
                settings.execute("ROLLBACK")
            settings.close()

        self.addCleanup(rollback)
        return settings

    def test_open_settings_write_fails_the_whole_attached_harvest(self):
        """An open Settings-side cold write fails the keyboard harvest, both ways.

        Measured on SQLite 3.53 with a 1s busy timeout:

        * `BEGIN IMMEDIATE` with cold ATTACHed waits out the whole busy timeout and then
          fails — one attempt burns the entire budget.
        * A read→write promotion inside the transaction fails in 0.000s: SQLite does not
          invoke the busy handler once the connection already holds a lock on another
          attached database, so no busy_timeout value can help.

        Retrying either lock point is strictly worse than not racing it at all.
        """
        self._locked_settings_connection()

        keyboard = sqlite3.connect(self.hot, timeout=1.0)
        self.addCleanup(keyboard.close)
        keyboard.execute("ATTACH DATABASE ? AS cold_editor", (str(self.cold),))

        started = time.monotonic()
        with self.assertRaises(sqlite3.OperationalError) as begun:
            keyboard.execute("BEGIN IMMEDIATE")
        immediate_wait = time.monotonic() - started
        self.assertIn("locked", str(begun.exception))
        self.assertGreater(immediate_wait, 0.5,
                           "BEGIN IMMEDIATE burns the whole busy timeout before failing")
        self.assertFalse(keyboard.in_transaction)

        keyboard.execute("BEGIN")
        keyboard.execute("SELECT COUNT(*) FROM cold_editor.related").fetchone()
        started = time.monotonic()
        with self.assertRaises(sqlite3.OperationalError) as promoted:
            keyboard.execute("DELETE FROM cold_editor.related")
        promotion_wait = time.monotonic() - started
        self.assertIn("locked", str(promoted.exception))
        self.assertLess(promotion_wait, 0.1,
                        "the read→write promotion never reaches the busy handler")
        if keyboard.in_transaction:
            keyboard.execute("ROLLBACK")

    def test_plain_cold_writer_does_honour_the_busy_timeout(self):
        """Control: without an attached database the busy handler works normally.

        This is why simply re-applying `PRAGMA busy_timeout` never fixed issue #209 —
        the harvest's lock points are the two that skip or exhaust the handler.
        """
        self._locked_settings_connection()

        competitor = sqlite3.connect(self.cold, timeout=1.0)
        self.addCleanup(competitor.close)
        started = time.monotonic()
        with self.assertRaises(sqlite3.OperationalError):
            competitor.execute("BEGIN IMMEDIATE")
        self.assertGreater(time.monotonic() - started, 0.5)

    def test_quiesced_cold_lets_the_harvest_commit(self):
        # Settings closed its process-local cold connection before the request.
        settings = sqlite3.connect(self.cold, timeout=5.0)
        settings.execute("SELECT COUNT(*) FROM related").fetchone()
        settings.close()

        keyboard = sqlite3.connect(self.hot, timeout=5.0)
        keyboard.execute("ATTACH DATABASE ? AS cold_editor", (str(self.cold),))
        self._harvest(keyboard)
        keyboard.execute("DETACH DATABASE cold_editor")
        keyboard.close()

        reopened = sqlite3.connect(self.cold)
        rows = reopened.execute(
            "SELECT pword, cword, score FROM related ORDER BY _id").fetchall()
        reopened.close()
        self.assertEqual(rows, [("你", "好", 8), ("天", "氣", 2)])

    def test_detach_inside_a_transaction_fails_and_leaves_cold_attached(self):
        """`DETACH` inside the write transaction cannot release cold.

        The pre-fix harvest issued `DETACH DATABASE cold_editor` from a `defer` INSIDE
        GRDB's write transaction and swallowed the error with `try?`, so cold stayed
        attached to a live connection. Only a post-commit `DETACH` releases it.
        """
        keyboard = sqlite3.connect(self.hot, timeout=5.0)
        self.addCleanup(keyboard.close)
        keyboard.execute("ATTACH DATABASE ? AS cold_editor", (str(self.cold),))
        keyboard.execute("BEGIN IMMEDIATE")
        keyboard.execute("SELECT COUNT(*) FROM cold_editor.related").fetchone()

        with self.assertRaises(sqlite3.OperationalError) as raised:
            keyboard.execute("DETACH DATABASE cold_editor")
        self.assertIn("locked", str(raised.exception))
        keyboard.execute("COMMIT")
        still_attached = [row[1] for row in keyboard.execute("PRAGMA database_list")]
        self.assertIn("cold_editor", still_attached,
                      "the swallowed in-transaction DETACH left cold attached")

        keyboard.execute("DETACH DATABASE cold_editor")
        attached = [row[1] for row in keyboard.execute("PRAGMA database_list")]
        self.assertNotIn("cold_editor", attached)

    def test_closing_the_last_connection_removes_the_wal_sidecars(self):
        # The XCTest lifecycle assertions use the -wal/-shm sidecars as the observable
        # "this process still holds the database open" signal. Pin that behavior here.
        conn = sqlite3.connect(self.cold)
        conn.execute("INSERT INTO related (pword, cword, score) VALUES ('暖', '機', 1)")
        conn.commit()
        self.assertTrue(os.path.exists(str(self.cold) + "-wal"))
        conn.close()
        self.assertFalse(os.path.exists(str(self.cold) + "-wal"))
        self.assertFalse(os.path.exists(str(self.cold) + "-shm"))


class EditorRefreshLifecycleContract(unittest.TestCase):
    """Source contract for the serialized Settings↔keyboard editor-refresh lifecycle."""

    @classmethod
    def setUpClass(cls):
        cls.engine = ENGINE.read_text(encoding="utf-8")
        cls.dbserver = DBSERVER.read_text(encoding="utf-8")
        cls.connection = SYNC_CONNECTION.read_text(encoding="utf-8")
        cls.contract = SYNC_CONTRACT.read_text(encoding="utf-8")
        cls.setup = SETUP.read_text(encoding="utf-8")
        cls.related_view = RELATED_VIEW.read_text(encoding="utf-8")
        cls.record_view = RECORD_VIEW.read_text(encoding="utf-8")
        cls.engine_tests = ENGINE_TESTS.read_text(encoding="utf-8")
        cls.setup_tests = SETUP_TESTS.read_text(encoding="utf-8")
        cls.dbserver_tests = DBSERVER_TESTS.read_text(encoding="utf-8")
        cls.view_tests = VIEW_TESTS.read_text(encoding="utf-8")

    # 1. Settings quiesces and closes cold before the request is visible.

    def test_settings_suspends_cold_access_before_posting_the_request(self):
        body = swift_body(self.setup,
                          "func refreshTableFromKeyboard(stem: String,\n"
                          "                                  baseURL: URL,")
        suspend = body.find("suspendColdAccess()")
        write_request = body.find("atomicWrite(try JSONEncoder().encode(request)")
        signal = body.find("postSyncSignal(.tablesUpdated)")
        self.assertGreaterEqual(suspend, 0, "cold access is never suspended")
        self.assertGreater(write_request, suspend,
                           "the request file must not be written before cold is quiesced")
        self.assertGreater(signal, suspend,
                           "the keyboard must not be signalled before cold is quiesced")

    def test_cross_process_ownership_spans_harvest_and_safe_reopen(self):
        setup = swift_body(self.setup,
                           "func refreshTableFromKeyboard(stem: String,\n"
                           "                                  baseURL: URL,")
        factory = setup.find("lockFactory(baseURL)")
        initial_acquire = setup.find("ownership.lockAsync", factory)
        publish = setup.find("atomicWrite(try JSONEncoder().encode(request)")
        release_for_keyboard = setup.find("ownership.unlock()", publish)
        wait = setup.find("waitForEditorRefreshReceipt")
        reacquire = setup.find("ownership.lockAsync", wait)
        resume = setup.find("resumeColdAccess()", reacquire)
        self.assertTrue(0 <= factory < initial_acquire < publish < release_for_keyboard < wait < reacquire < resume,
                        "Settings must explicitly own close/publish, release for harvest, then "
                        "reacquire the same process-lifetime handle before reopening cold")

        engine = swift_body(self.engine, "private func processEditorRefreshRequestIfNeeded()")
        hot_handle = engine.find("EditorRefreshFileLock.shared(baseURL: appGroupBaseURL)")
        hot_acquire = engine.find("ownership.lock(timeout: editorRefreshLockTimeout)", hot_handle)
        reread = engine.find("Data(contentsOf: requestURL)")
        harvest = engine.find("harvestEditorRefresh(")
        receipt = engine.find("writeEditorRefreshReceipt(for: decodedRequest, status: .done")
        self.assertTrue(0 <= hot_handle < hot_acquire < reread < harvest < receipt,
                        "keyboard must acquire ownership, re-read the request, harvest, then "
                        "publish the terminal receipt")
        self.assertIn("final class EditorRefreshFileLock", self.contract)
        self.assertIn("flock(descriptor, operation)", self.contract)
        self.assertNotIn("dlopen", self.contract)
        self.assertNotIn("dlsym", self.contract)
        self.assertIn("LOCK_EX | LOCK_NB", self.contract)
        self.assertIn("LOCK_UN", self.contract)

    def test_cross_process_lock_wait_is_bounded(self):
        file_lock = self.contract.split("final class EditorRefreshFileLock", 1)[1]
        lock = swift_body(file_lock, "func lock(timeout: TimeInterval) throws")
        self.assertNotIn("F_SETLKW", lock,
                         "the editor handoff must never block the Settings UI indefinitely")
        self.assertIn("deadline", lock)
        self.assertIn("throw EditorRefreshLockError.timedOut", lock)

    def test_file_lock_has_no_unbounded_convenience_surface(self):
        file_lock = swift_body(self.contract, "final class EditorRefreshFileLock")
        self.assertNotIn("defaultAcquisitionTimeout", file_lock)
        self.assertNotIn("func lock() throws", file_lock)

    def test_unlock_failure_still_releases_process_local_ownership(self):
        file_lock = self.contract.split("final class EditorRefreshFileLock", 1)[1]
        unlock = swift_body(file_lock, "func unlock() throws")
        attempt = unlock.find("shared.applyFlock(LOCK_UN)")
        clear = unlock.find("ownsLock = false", attempt)
        state_unlock = unlock.find("stateLock.unlock()", clear)
        signal = unlock.find("shared.localOwnership.signal()", state_unlock)
        failure_guard = unlock.find("guard attempt.result == 0", signal)
        self.assertTrue(0 <= attempt < clear < state_unlock < signal < failure_guard,
                        "LOCK_UN failure must not wedge process-local ownership")

    def test_session_gate_documents_intentional_cancellation_behavior(self):
        gate = swift_body(self.contract, "final class EditorRefreshSessionGate")
        self.assertIn("cancellation", gate.lower())
        self.assertIn("complete", gate.lower())

    def test_cross_process_lock_is_descriptor_owned_flock(self):
        file_lock = self.contract.split("final class EditorRefreshFileLock", 1)[1]
        lock = swift_body(file_lock, "func lock(timeout: TimeInterval) throws")
        unlock = swift_body(file_lock, "func unlock() throws")
        self.assertIn("LOCK_EX | LOCK_NB", lock)
        self.assertIn("LOCK_UN", unlock)
        self.assertNotIn("F_SETLK", lock + unlock,
                         "process-associated fcntl locks can be dropped by an unrelated close")

    def test_persistent_contention_uses_an_injected_short_timeout(self):
        self.assertIn("editorRefreshBusyTimeoutMilliseconds: Int = 5_000", self.engine)
        self.assertIn("editorRefreshBusyTimeoutMilliseconds: 200", self.engine_tests)
        self.assertNotIn("XCTAssertLessThan(elapsed, 5", self.engine_tests)

    def test_keyboard_ownership_contention_defers_refresh_without_aborting_scan(self):
        self.assertIn("editorRefreshLockTimeout: TimeInterval = 2", self.engine)
        body = swift_body(self.engine, "private func processEditorRefreshRequestIfNeeded()")
        self.assertIn("try ownership.lock(timeout: editorRefreshLockTimeout)", body)
        lock_attempt = body.find("try ownership.lock(timeout: editorRefreshLockTimeout)")
        request_decode = body.find("JSONDecoder().decode", lock_attempt)
        self.assertGreater(request_decode, lock_attempt)
        acquisition = body[lock_attempt:request_decode]
        catch_index = acquisition.find("} catch {")
        return_index = acquisition.find("return", catch_index)
        cleanup_index = acquisition.find("removeItem(at: requestURL)")
        self.assertTrue(0 <= catch_index < return_index < cleanup_index,
                        "keyboard lock contention must return before request cleanup is registered")

    def test_malformed_editor_request_does_not_abort_scan(self):
        body = swift_body(self.engine, "private func processEditorRefreshRequestIfNeeded()")
        do_index = body.find("do {")
        decode_index = body.find("JSONDecoder().decode")
        catch_index = body.find("} catch {", decode_index)
        self.assertTrue(0 <= do_index < decode_index < catch_index)
        self.assertIn("testMalformedEditorRefreshRequestDoesNotAbortColdToHotScan", self.engine_tests)

    def test_lock_factory_is_unowned_and_uses_one_process_descriptor(self):
        factory = swift_body(self.contract,
                             "static func shared(baseURL: URL) throws -> EditorRefreshFileLock")
        self.assertNotIn("F_SETLK", factory,
                         "constructing a handle must not acquire cross-process ownership")
        self.assertIn("private static var sharedByPath", self.contract)
        self.assertNotIn("EditorRefreshFileLock(baseURL: $0)", self.setup)

    def test_settings_explicitly_acquires_without_descriptor_fallback(self):
        body = swift_body(self.setup,
                          "func refreshTableFromKeyboard(stem: String,\n"
                          "                                  baseURL: URL,")
        factory = body.find("lockFactory(baseURL)")
        initial_lock = body.find("ownership.lockAsync", factory)
        suspend = body.find("suspendColdAccess()", initial_lock)
        self.assertTrue(0 <= factory < initial_lock < suspend)
        wait = body.find("waitForEditorRefreshReceipt")
        self.assertNotIn("ownership = try lockFactory(baseURL)", body[wait:],
                         "reacquire must reuse the process-lifetime descriptor")

    def test_settings_serializes_the_entire_handshake_in_process(self):
        self.assertIn("final class EditorRefreshSessionGate", self.contract)
        self.assertNotIn("final class EditorRefreshSessionGate", self.setup)
        refresh = swift_body(self.setup,
                             "func refreshTableFromKeyboard(stem: String,\n"
                             "                                  baseURL: URL,")
        acquire = refresh.find("await editorRefreshSessionGate.acquire()")
        release = refresh.find("defer { editorRefreshSessionGate.release() }")
        publish = refresh.find("atomicWrite(try JSONEncoder().encode(request)")
        self.assertTrue(0 <= acquire < release < publish,
                        "the process-local gate must span the complete Settings handshake")

    def test_settings_lock_waits_run_off_the_cooperative_pool_and_share_request_deadline(self):
        self.assertIn("EditorRefreshLockExecutor", self.contract)
        self.assertIn("DispatchQueue(label:", self.contract)
        self.assertIn("withCheckedThrowingContinuation", self.contract)
        refresh = swift_body(self.setup,
                             "func refreshTableFromKeyboard(stem: String,\n"
                             "                                  baseURL: URL,")
        self.assertIn("try await ownership.lockAsync", refresh)
        self.assertNotIn("try currentOwnership.lock()", refresh,
                         "blocking editor lock waits must not occupy Swift cooperative workers")
        self.assertIn("request.expiresAt - Date().timeIntervalSince1970", refresh)

    def test_receipt_polling_does_not_busy_spin_after_task_cancellation(self):
        wait = swift_body(self.setup, "private func waitForEditorRefreshReceipt")
        self.assertNotIn("try? await Task.sleep", wait)
        self.assertIn("editorRefreshDelayIgnoringCancellation", wait)
        delay = swift_body(self.setup, "private func editorRefreshDelayIgnoringCancellation")
        self.assertIn("asyncAfter", delay)

    def test_timeout_surface_is_observable_and_has_one_default_owner(self):
        handle = swift_body(self.contract, "struct EditorRefreshLockHandle")
        self.assertNotIn("init(lock: @escaping @Sendable () throws -> Void", handle)
        self.assertNotIn("func lock() throws", handle)
        self.assertNotIn("defaultAcquisitionTimeout", handle)
        self.assertIn("init(lock: @escaping @Sendable (TimeInterval) throws -> Void", handle)

    def test_initial_timeout_is_named_and_signal_follows_unlock(self):
        self.assertIn("private let editorRefreshInitialLockTimeout: TimeInterval = 2", self.setup)
        refresh = swift_body(self.setup,
                             "func refreshTableFromKeyboard(stem: String,\n"
                             "                                  baseURL: URL,")
        self.assertIn("lockAsync(timeout: editorRefreshInitialLockTimeout)", refresh)
        self.assertNotIn("min(2,", refresh)
        publish = refresh.find("atomicWrite(try JSONEncoder().encode(request)")
        unlock = refresh.find("try ownership.unlock()", publish)
        signal = refresh.find("postSyncSignal(.tablesUpdated)", publish)
        self.assertTrue(0 <= publish < unlock < signal,
                        "keyboard wakeup must follow ownership release")

    def test_async_lock_executor_does_not_serialize_independent_waits(self):
        executor = swift_body(self.contract, "private enum EditorRefreshLockExecutor")
        self.assertIn("attributes: .concurrent", executor)

    def test_initial_suspend_failure_explicitly_releases_ownership(self):
        refresh = swift_body(self.setup,
                             "func refreshTableFromKeyboard(stem: String,\n"
                             "                                  baseURL: URL,")
        first_catch = refresh.split("try server.suspendColdAccess()", 1)[1].split("do {", 1)[0]
        self.assertIn("try? ownership.unlock()", first_catch)

    def test_settings_reopens_cold_before_returning_after_a_request(self):
        body = swift_body(self.setup,
                          "func refreshTableFromKeyboard(stem: String,\n"
                          "                                  baseURL: URL,")
        wait = body.find("waitForEditorRefreshReceipt")
        resume = body.find("resumeColdAccess()", wait)
        self.assertGreaterEqual(resume, 0, "cold access is never resumed")
        self.assertGreater(resume, wait,
                           "cold must be reopened after the receipt wait, not before")
        returns = [match.start() for match in re.finditer(r"^\s*return\b", body, re.M)]
        self.assertTrue(returns, "the handshake must return a result")
        write_request = body.find("atomicWrite(try JSONEncoder().encode(request)")
        # A close failure safely returns before publishing any request; there is then
        # nothing to reopen. Once a request can be visible, every return must follow resume.
        self.assertTrue(any(index < write_request for index in returns),
                        "a failed suspension must abort before posting the request")
        for index in (index for index in returns if index > wait):
            self.assertGreater(index, resume,
                               "every post-wait success/failure/timeout return must come "
                               "after the cold reopen")

    def test_reacquire_failure_still_attempts_to_resume_cold(self):
        body = swift_body(self.setup,
                          "func refreshTableFromKeyboard(stem: String,\n"
                          "                                  baseURL: URL,")
        final_catch = body.rsplit("} catch {", 1)[-1]
        self.assertIn("resumeColdAccess()", final_catch,
                      "a failed ownership reacquire must not leave Settings empty until restart")

    def test_publish_failure_releases_ownership_before_resuming_cold(self):
        refresh = swift_body(self.setup,
                             "func refreshTableFromKeyboard(stem: String,\n"
                             "                                  baseURL: URL,")
        publish_catch = refresh.split("postSyncSignal(.tablesUpdated)", 1)[1]
        publish_catch = publish_catch.split("var outcome:", 1)[0]
        unlock = publish_catch.find("try? ownership.unlock()")
        resume = publish_catch.find("resumeColdAccess()")
        self.assertTrue(0 <= unlock < resume,
                        "a publish failure must release ownership before cold reopens")

    def test_dbserver_exposes_a_closing_cold_suspension(self):
        self.assertIn("func suspendColdAccess()", self.dbserver)
        self.assertIn("func resumeColdAccess()", self.dbserver)
        self.assertIn("var isColdAccessSuspended: Bool", self.dbserver)
        current = swift_body(self.dbserver, "func current() -> LimeDB?")
        self.assertIn("accessSuspended", current,
                      "a suspended cold database must not be opened on demand")
        suspend = swift_body(self.dbserver, "func suspendLiveAccess()")
        self.assertIn("closeForReplacement()", suspend,
                      "suspension must close the live connection, not just flag it")
        resume = swift_body(self.dbserver, "func resumeLiveAccess()")
        self.assertIn("openDatasource()", resume,
                      "resuming must rebind to the on-disk file")

    def test_dbserver_notifies_whenever_the_suspension_gate_reopens(self):
        suspend = swift_body(self.dbserver, "func suspendLiveAccess() throws")
        suspend_failure = suspend.split("} catch {", 1)[1]
        self.assertGreater(suspend_failure.find("notifyAccessResumed()"),
                           suspend_failure.find("accessSuspended = false"))
        resume = swift_body(self.dbserver, "func resumeLiveAccess() throws")
        failed_reopen = resume.split("guard let reopened = openDatasource() else", 1)[1]
        failed_reopen = failed_reopen.split("cachedDatasource = reopened", 1)[0]
        self.assertGreater(failed_reopen.find("notifyAccessResumed()"),
                           failed_reopen.find("accessSuspended = false"))
        self.assertGreater(resume.rfind("notifyAccessResumed()"),
                           resume.find("cachedDatasource = reopened"))

    def test_cold_suspension_rejects_rebinds_and_drops_a_failed_close(self):
        suspend = swift_body(self.dbserver, "func suspendLiveAccess()")
        self.assertIn("cachedDatasource = nil", suspend)
        set_current = swift_body(self.dbserver, "func setCurrent(_ datasource: LimeDB?)")
        self.assertIn("guard !accessSuspended || datasource == nil", set_current)
        reopen = swift_body(self.dbserver, "func reopenFromDisk()")
        self.assertIn("guard !isAccessSuspended else { return }", reopen)
        replace = swift_body(self.dbserver,
                             "func replaceDatabaseFromSnapshot(_ snapshotURL: URL) throws")
        self.assertIn("try requireColdAccessAvailable()", replace)

    # 3. The keyboard attaches / commits / detaches / closes before the done receipt.

    def test_harvest_attaches_and_detaches_outside_the_write_transaction(self):
        body = swift_body(self.engine,
                          "private func harvestEditorRefresh(table: String, into coldDatabaseURL: URL)")
        attach = body.find("ATTACH DATABASE ? AS cold_editor")
        transaction = body.find("harvestEditorRefreshTransaction(")
        detach = body.find("releaseColdEditor(")
        close = body.find("try connection.close()")
        self.assertGreaterEqual(attach, 0, "the harvest must attach cold")
        self.assertGreaterEqual(transaction, 0, "the harvest must run one hot write transaction")
        self.assertGreaterEqual(detach, 0, "the harvest must detach cold")
        self.assertGreaterEqual(close, 0, "the harvest must close its connection explicitly")
        self.assertLess(attach, transaction, "ATTACH must run before the transaction")
        self.assertLess(transaction, body.rfind("releaseColdEditor("),
                        "DETACH must run after the transaction commits")
        self.assertLess(detach, close, "DETACH must run before the connection is closed")

        # ATTACH and DETACH must both be issued OUTSIDE any transaction: SQLite rejects an
        # in-transaction DETACH ("database cold_editor is locked"), which `try?` then hides.
        attach_scope = swift_body(body, "try connection.writeWithoutTransaction { db in")
        self.assertIn("ATTACH DATABASE ? AS cold_editor", attach_scope,
                      "ATTACH must be issued outside any transaction")
        release = swift_body(self.engine,
                             "private func releaseColdEditor(on connection: SyncDatabaseConnection)")
        self.assertIn("writeWithoutTransaction", release)
        self.assertIn("DETACH DATABASE cold_editor", release)
        transaction_body = swift_body(
            self.engine,
            "private func harvestEditorRefreshTransaction(table: String,")
        self.assertIn("connection.write { db in", transaction_body)
        self.assertNotIn("ATTACH DATABASE", transaction_body,
                         "ATTACH must not be issued inside the write transaction")
        self.assertNotIn("DETACH DATABASE", transaction_body,
                         "DETACH inside the GRDB transaction fails silently")

    def test_harvest_closes_the_connection_on_error_paths(self):
        body = swift_body(self.engine,
                          "private func harvestEditorRefresh(table: String, into coldDatabaseURL: URL)")
        catches = body.count("catch {")
        self.assertGreaterEqual(catches, 1, "error paths must be handled, not leaked")
        for segment in body.split("catch {")[1:]:
            self.assertIn("connection.close()", segment.split("throw")[0],
                          "every error path must close the connection before rethrowing")

    def test_done_receipt_is_written_after_the_harvest_released_cold(self):
        body = swift_body(self.engine, "private func processEditorRefreshRequestIfNeeded()")
        harvest = body.find("try harvestEditorRefresh(")
        receipt = body.find("writeEditorRefreshReceipt(for: decodedRequest, status: .done")
        self.assertGreaterEqual(harvest, 0)
        self.assertGreaterEqual(receipt, 0)
        self.assertLess(harvest, receipt,
                        "the done receipt must follow the fully released harvest")

    def test_sync_connection_can_be_closed_explicitly_and_idempotently(self):
        self.assertIn("func close()", self.connection)
        close = swift_body(self.connection, "func close()")
        self.assertIn("isClosed", close,
                      "close() must be idempotent — deinit also closes")

    # 5. The retry-only workaround and its gate are gone.

    def test_the_retry_workaround_is_removed(self):
        for symbol in ("editorRefreshBusyRetryWindow",
                       "editorRefreshBusyRetryBackoff",
                       "editorRefreshAttemptBusyTimeoutMilliseconds",
                       "isTransientLockError",
                       "harvestEditorRefreshAttempt"):
            self.assertNotIn(symbol, self.engine,
                             f"{symbol} is a leftover of the retry-only workaround")
        self.assertNotIn("testEditorRefreshRetriesThroughTransientColdWriteLock",
                         self.engine_tests,
                         "the transient-retry test asserts behavior the fix removes")
        self.assertFalse(RETIRED_RETRY_GATE.exists(),
                         "the retry source contract must be retired with the retry")

    def test_bounded_failure_and_recovery_remain_covered(self):
        for name in ("testEditorRefreshFailsBoundedUnderPersistentColdWriteLock",
                     "testEditorRefreshRecoversOnNextRequestAfterColdWriteLockReleased",
                     "testEditorRefreshDetachesAndClosesColdBeforeWritingDoneReceipt"):
            self.assertIn(f"func {name}()", self.engine_tests)
        self.assertIn("db.inTransaction(.immediate)", self.engine_tests,
                      "contention coverage must hold a real cold write lock")

    # 2 + 4. Editing stays locked and cold loads never overlap the harvest.

    def test_related_view_loads_cold_only_after_the_refresh_resolves(self):
        appear = swift_body(self.related_view, ".onAppear")
        self.assertIn("beginEditorSession()", appear)
        self.assertNotIn("loadPhrases()", appear,
                         "the initial cold load must not race the harvest")
        session = swift_body(self.related_view, "private func beginEditorSession()")
        refresh = session.find("await setupController.refreshTableFromKeyboard")
        load = session.find("loadPhrases()", refresh)
        self.assertGreater(refresh, 0, "the session must run the harvest handshake")
        self.assertGreater(load, refresh,
                           "cold is loaded only after the handshake resolved and reopened")
        self.assertLess(session.find("isRefreshingHotSnapshot = true"), refresh)
        self.assertGreater(session.find("isRefreshingHotSnapshot = false", refresh), refresh)
        self.assertNotIn("guard relayEditingCapability == .live", session)

    def test_record_view_loads_cold_only_after_the_refresh_resolves(self):
        appear = swift_body(self.record_view, ".onAppear")
        self.assertIn("beginEditorSession()", appear)
        self.assertNotIn("loadRecords()", appear,
                         "the initial cold load must not race the harvest")
        session = swift_body(self.record_view, "private func beginEditorSession()")
        refresh = session.find("await setupController.refreshTableFromKeyboard")
        load = session.find("loadRecords()", refresh)
        self.assertGreater(refresh, 0, "the session must run the harvest handshake")
        self.assertGreater(load, refresh,
                           "cold is loaded only after the handshake resolved and reopened")
        self.assertLess(session.find("isRefreshingHotSnapshot = true"), refresh)
        self.assertGreater(session.find("isRefreshingHotSnapshot = false", refresh), refresh)
        self.assertNotIn("guard relayEditingCapability == .live", session)

    def test_view_source_tests_cover_the_serialized_order(self):
        self.assertIn("beginEditorSession", self.view_tests)

    def test_lifecycle_xctests_exist_for_both_processes(self):
        self.assertIn("func testSuspendColdAccessClosesTheLiveConnectionUntilResumed()",
                      self.dbserver_tests)
        self.assertIn("func testRefreshTableFromKeyboardQuiescesColdUntilTheReceiptLands()",
                      self.setup_tests)
        self.assertIn("func testRefreshTableFromKeyboardReopensColdAfterTimeout()",
                      self.setup_tests)


if __name__ == "__main__":
    unittest.main()
