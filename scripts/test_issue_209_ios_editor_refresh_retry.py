#!/usr/bin/env python3
"""Linux source contract for issue #209's iOS editor-refresh lock retry.

The behavioral proof remains the real GRDB/XCTest cases in TableSyncEngineTest.swift.
This gate catches accidental removal or broadening of the retry while Xcode is unavailable.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / "LimeIME-iOS/Shared/Database/TableSyncEngine.swift"
TESTS = ROOT / "LimeIME-iOS/LimeTests/TableSyncEngineTest.swift"


class EditorRefreshRetryContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.engine = ENGINE.read_text(encoding="utf-8")
        cls.tests = TESTS.read_text(encoding="utf-8")

    def test_retry_is_bounded_below_settings_poll_timeout(self):
        window = re.search(
            r"editorRefreshBusyRetryWindow: TimeInterval = ([0-9.]+)", self.engine)
        timeout = re.search(
            r"editorRefreshAttemptBusyTimeoutMilliseconds = ([0-9_]+)", self.engine)
        if window is None or timeout is None:
            self.fail("bounded editor-refresh retry constants are missing")
        # Conservatively allow the final attempt to encounter two busy-handler waits.
        worst_case = (float(window.group(1))
                      + 2 * int(timeout.group(1).replace("_", "")) / 1000)
        self.assertLess(worst_case, 10)

    def test_retry_is_limited_to_sqlite_lock_results(self):
        match = re.search(
            r"private static func isTransientLockError\(_ error: DatabaseError\) -> Bool \{"
            r"(.*?)\n    \}", self.engine, re.S)
        if match is None:
            self.fail("isTransientLockError helper is missing")
        body = match.group(1)
        self.assertIn(".SQLITE_BUSY", body)
        self.assertIn(".SQLITE_LOCKED", body)
        for forbidden in ("SQLITE_ERROR", "SQLITE_IOERR", "SQLITE_CONSTRAINT"):
            self.assertNotIn(forbidden, body)

    def test_every_retry_attempt_opens_a_fresh_connection(self):
        self.assertIn(
            "return try harvestEditorRefreshAttempt(table: table, into: coldDatabaseURL)",
            self.engine,
        )
        start = self.engine.find("private func harvestEditorRefreshAttempt(")
        end = self.engine.find("private func writeEditorRefreshReceipt", start)
        if start < 0 or end < 0:
            self.fail("harvestEditorRefreshAttempt helper is missing")
        attempt = self.engine[start:end]
        self.assertIn("let connection = try SyncDatabaseConnection(", attempt)
        self.assertIn("busyTimeoutMilliseconds:", attempt)

    def test_real_contention_xctests_cover_transient_persistent_and_recovery(self):
        self.assertIn("db.inTransaction(.immediate)", self.tests)
        for name in (
            "testEditorRefreshRetriesThroughTransientColdWriteLock",
            "testEditorRefreshFailsBoundedUnderPersistentColdWriteLock",
            "testEditorRefreshRecoversOnNextRequestAfterColdWriteLockReleased",
        ):
            self.assertIn(f"func {name}()", self.tests)


if __name__ == "__main__":
    unittest.main()
