#!/usr/bin/env python3
"""Durable Linux gate for issue #209's iOS editor-refresh lifecycle.

Five SQLite tests execute the database semantics behind the hot→cold handoff, one
subprocess test exercises descriptor-owned cross-process ``flock``, and three narrow
source guards prevent resurrection of the retired retry workaround or unbounded lock
APIs. Detailed lifecycle behavior belongs in XCTest and runs in Xcode Cloud.
"""
import fcntl
import multiprocessing
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
SYNC_CONTRACT = IOS / "Shared/Database/SyncContract.swift"
DB_SERVER = IOS / "Shared/Database/DBServer.swift"
ENGINE_TESTS = IOS / "LimeTests/TableSyncEngineTest.swift"
RETIRED_RETRY_GATE = ROOT / "scripts/test_issue_209_ios_editor_refresh_retry.py"


def try_exclusive_flock(lock_path: str, result_queue: multiprocessing.Queue) -> None:
    descriptor = os.open(lock_path, os.O_CREAT | os.O_RDWR, 0o600)
    try:
        try:
            fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            result_queue.put(False)
            return
        result_queue.put(True)
        fcntl.flock(descriptor, fcntl.LOCK_UN)
    finally:
        os.close(descriptor)


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
        """An open Settings-side cold write defeats timeout and retry workarounds."""
        self._locked_settings_connection()
        keyboard = sqlite3.connect(self.hot, timeout=1.0)
        self.addCleanup(keyboard.close)
        keyboard.execute("ATTACH DATABASE ? AS cold_editor", (str(self.cold),))

        started = time.monotonic()
        with self.assertRaises(sqlite3.OperationalError) as begun:
            keyboard.execute("BEGIN IMMEDIATE")
        self.assertIn("locked", str(begun.exception))
        self.assertGreater(time.monotonic() - started, 0.5)
        self.assertFalse(keyboard.in_transaction)

        keyboard.execute("BEGIN")
        keyboard.execute("SELECT COUNT(*) FROM cold_editor.related").fetchone()
        started = time.monotonic()
        with self.assertRaises(sqlite3.OperationalError) as promoted:
            keyboard.execute("DELETE FROM cold_editor.related")
        self.assertIn("locked", str(promoted.exception))
        self.assertLess(time.monotonic() - started, 0.1)
        if keyboard.in_transaction:
            keyboard.execute("ROLLBACK")

    def test_plain_cold_writer_does_honour_the_busy_timeout(self):
        """Control: without an attached database the busy handler works normally."""
        self._locked_settings_connection()
        competitor = sqlite3.connect(self.cold, timeout=1.0)
        self.addCleanup(competitor.close)
        started = time.monotonic()
        with self.assertRaises(sqlite3.OperationalError):
            competitor.execute("BEGIN IMMEDIATE")
        self.assertGreater(time.monotonic() - started, 0.5)

    def test_quiesced_cold_lets_the_harvest_commit(self):
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
        self.assertIn("cold_editor", still_attached)

        keyboard.execute("DETACH DATABASE cold_editor")
        attached = [row[1] for row in keyboard.execute("PRAGMA database_list")]
        self.assertNotIn("cold_editor", attached)

    def test_closing_the_last_connection_removes_the_wal_sidecars(self):
        conn = sqlite3.connect(self.cold)
        conn.execute("INSERT INTO related (pword, cword, score) VALUES ('暖', '機', 1)")
        conn.commit()
        self.assertTrue(os.path.exists(str(self.cold) + "-wal"))
        conn.close()
        self.assertFalse(os.path.exists(str(self.cold) + "-wal"))
        self.assertFalse(os.path.exists(str(self.cold) + "-shm"))


class DurableEditorRefreshRemovalContract(unittest.TestCase):
    """Narrow guards against resurrecting retired unsafe surfaces."""

    @classmethod
    def setUpClass(cls):
        cls.engine = ENGINE.read_text(encoding="utf-8")
        cls.contract = SYNC_CONTRACT.read_text(encoding="utf-8")
        cls.db_server = DB_SERVER.read_text(encoding="utf-8")
        cls.engine_tests = ENGINE_TESTS.read_text(encoding="utf-8")

    def test_shared_database_uses_one_concrete_scoped_access_lease(self):
        shared_database = swift_body(self.db_server, "final class SharedDatabase")
        self.assertIn("private var activeAccesses = 0", shared_database)
        self.assertIn(
            "func withLiveAccess<T>(_ operation: (LimeDB) throws -> T) throws -> T",
            shared_database,
        )
        self.assertNotIn("activeIndependentAccesses", shared_database)
        self.assertNotIn("func withLiveAccessOperation", shared_database)

    def test_cross_process_lock_wait_is_bounded(self):
        file_lock = swift_body(self.contract, "final class EditorRefreshFileLock")
        self.assertIn("throw EditorRefreshLockError.timedOut", file_lock)
        self.assertIn("flock(", file_lock)
        self.assertNotIn("F_SETLKW", file_lock)

    def test_file_lock_has_no_unbounded_convenience_surface(self):
        file_lock = swift_body(self.contract, "final class EditorRefreshFileLock")
        declarations = re.findall(r"\bfunc\s+lock\s*\((.*?)\)\s*throws", file_lock, re.DOTALL)
        self.assertTrue(declarations, "the bounded explicit lock API disappeared")
        for parameters in declarations:
            self.assertIn("TimeInterval", parameters)
            self.assertNotIn("=", parameters, "lock acquisition must not have a default timeout")
        self.assertNotIn("defaultAcquisitionTimeout", file_lock)

    def test_the_retry_workaround_is_removed(self):
        for symbol in ("editorRefreshBusyRetryWindow",
                       "editorRefreshBusyRetryBackoff",
                       "editorRefreshAttemptBusyTimeoutMilliseconds",
                       "isTransientLockError",
                       "harvestEditorRefreshAttempt"):
            self.assertNotIn(symbol, self.engine)
        self.assertNotIn("testEditorRefreshRetriesThroughTransientColdWriteLock",
                         self.engine_tests)
        self.assertFalse(RETIRED_RETRY_GATE.exists())


class CrossProcessEditorRefreshOwnershipBehaviour(unittest.TestCase):
    def test_second_process_waits_until_descriptor_flock_is_released(self):
        context = multiprocessing.get_context("spawn")
        with tempfile.TemporaryDirectory() as directory:
            lock_path = os.path.join(directory, "editor-refresh.lock")
            owner = os.open(lock_path, os.O_CREAT | os.O_RDWR, 0o600)
            try:
                fcntl.flock(owner, fcntl.LOCK_EX | fcntl.LOCK_NB)

                blocked_result = context.Queue()
                blocked = context.Process(target=try_exclusive_flock,
                                          args=(lock_path, blocked_result))
                blocked.start()
                blocked.join(timeout=5)
                self.assertFalse(blocked.is_alive(), "contending process must remain bounded")
                self.assertEqual(blocked.exitcode, 0)
                self.assertFalse(blocked_result.get(timeout=1))

                fcntl.flock(owner, fcntl.LOCK_UN)
                acquired_result = context.Queue()
                acquired = context.Process(target=try_exclusive_flock,
                                           args=(lock_path, acquired_result))
                acquired.start()
                acquired.join(timeout=5)
                self.assertFalse(acquired.is_alive(), "released ownership must be acquirable")
                self.assertEqual(acquired.exitcode, 0)
                self.assertTrue(acquired_result.get(timeout=1))
            finally:
                fcntl.flock(owner, fcntl.LOCK_UN)
                os.close(owner)


if __name__ == "__main__":
    unittest.main()
