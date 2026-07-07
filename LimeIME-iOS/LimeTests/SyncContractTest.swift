// SyncContractTest.swift

import XCTest
@testable import LimeIME

final class SyncContractTest: XCTestCase {
    func testPathDerivations() {
        let base = URL(fileURLWithPath: "/tmp/x")

        XCTAssertEqual(SyncPaths.coldDB(base).path, "/tmp/x/cold.limedb")
        XCTAssertEqual(SyncPaths.inboxDir(base).path, "/tmp/x/inbox")
        XCTAssertEqual(SyncPaths.outboxDir(base).path, "/tmp/x/outbox")
        XCTAssertEqual(SyncPaths.exportRequest(base).path, "/tmp/x/outbox/export.request.json")
        XCTAssertEqual(SyncPaths.backupSnapshot(base).path, "/tmp/x/outbox/backup.limedb")
        XCTAssertEqual(SyncPaths.receipt(base).path, "/tmp/x/outbox/receipt.json")
        XCTAssertEqual(SyncPaths.editorRefreshRequest(base).path, "/tmp/x/outbox/editor.refresh.request.json")
        XCTAssertEqual(SyncPaths.editorRefreshReceipt(base).path, "/tmp/x/outbox/editor.refresh.receipt.json")
    }

    // §1.8: the app→kb pref inbox merges fields, bumps a seq, and is consumed one-time via
    // the seq guard — so a lingering file (keyboard can't delete FA-off) never re-applies.
    func testPrefInboxSeqGuardedOneTimeConsumption() throws {
        let base = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("prefinbox-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: base) }
        let suite = "prefinbox-test-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }

        XCTAssertEqual(SyncPaths.prefInbox(base).path,
                       base.appendingPathComponent("inbox/prefs.json").path)
        XCTAssertNil(PrefInbox.read(base: base))

        try PrefInbox.write(base: base, defaults: defaults, hanConvert: 1)          // seq 1
        try PrefInbox.write(base: base, defaults: defaults, splitKeyboard: 2,
                            reverseLookup: (im: "cj", value: "phonetic"))           // merges, seq 2
        let rec = try XCTUnwrap(PrefInbox.read(base: base))
        XCTAssertEqual(rec.seq, 2)
        XCTAssertEqual(rec.hanConvert, 1, "unread field carries forward")
        XCTAssertEqual(rec.splitKeyboard, 2)
        XCTAssertEqual(rec.reverseLookup?["cj"], "phonetic")

        // Keyboard consumes once; the file lingers (FA-off cannot delete) but the seq guard
        // prevents a second apply.
        var lastConsumed = 0
        XCTAssertTrue(rec.seq > lastConsumed)
        lastConsumed = rec.seq
        let again = try XCTUnwrap(PrefInbox.read(base: base))
        XCTAssertFalse(again.seq > lastConsumed, "same seq must not re-apply — one-time without delete")

        try PrefInbox.write(base: base, defaults: defaults, hanConvert: 0)          // seq 3
        let next = try XCTUnwrap(PrefInbox.read(base: base))
        XCTAssertEqual(next.seq, 3)
        XCTAssertTrue(next.seq > lastConsumed)
    }

    func testEditorRefreshPayloadsRoundTrip() throws {
        let request = EditorRefreshRequest(requestUUID: "req-1",
                                           table: "custom",
                                           expiresAt: 123)
        let receipt = EditorRefreshReceipt(requestUUID: "req-1",
                                           table: "custom",
                                           status: .done,
                                           error: nil,
                                           at: 456)

        XCTAssertEqual(try JSONDecoder().decode(EditorRefreshRequest.self,
                                                from: JSONEncoder().encode(request)),
                       request)
        XCTAssertEqual(try JSONDecoder().decode(EditorRefreshReceipt.self,
                                                from: JSONEncoder().encode(receipt)),
                       receipt)
    }

    func testFileIdentity() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: dir) }
        let url = dir.appendingPathComponent("file")

        XCTAssertNil(FileIdentity(url: url))

        try atomicWrite(Data("a".utf8), to: url)
        let first = try XCTUnwrap(FileIdentity(url: url))

        try atomicWrite(Data("ab".utf8), to: url)
        let second = try XCTUnwrap(FileIdentity(url: url))

        XCTAssertNotEqual(first, second)
    }

    func testAtomicWriteReplacesAndLeavesNoTemp() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: dir) }
        let url = dir.appendingPathComponent("payload.limedb")

        try atomicWrite(Data("first".utf8), to: url)
        try atomicWrite(Data("second".utf8), to: url)

        XCTAssertEqual(try String(contentsOf: url, encoding: .utf8), "second")
        let files = try FileManager.default.contentsOfDirectory(atPath: dir.path)
        XCTAssertEqual(files, ["payload.limedb"])
    }

    func testAppLaunchCleanupRemovesLegacyV1ArtifactsOnly() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: dir) }
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)

        let tables = dir.appendingPathComponent("tables", isDirectory: true)
        let restoreDB = dir.appendingPathComponent(["restore", "limedb"].joined(separator: "."))
        let restoreSidecar = dir.appendingPathComponent(["restore", "meta", "json"].joined(separator: "."))
        let keep = dir.appendingPathComponent("cold.limedb")
        try FileManager.default.createDirectory(at: tables, withIntermediateDirectories: true)
        try Data().write(to: tables.appendingPathComponent("cj.limedb"))
        try Data().write(to: restoreDB)
        try Data().write(to: restoreSidecar)
        try Data().write(to: keep)

        AppDelegate.removeLegacyV1Artifacts(in: dir)

        XCTAssertFalse(FileManager.default.fileExists(atPath: tables.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: restoreDB.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: restoreSidecar.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: keep.path))
    }

    // §1.5: the relay carries only prefs now — the `im` inbox / `imseq` cursor is gone
    // (im is delivered by the wholesale cold → hot mirror). A payload still round-trips.
    func testRelayPayloadRoundTripsWithoutIMSeq() {
        let decoded = LimeIME.decodeRelayPayload(LimeIME.encodeRelayPayload(faOn: true, ts: 123.0))
        XCTAssertEqual(decoded?.faOn, true)
        XCTAssertEqual(decoded?.ts, 123.0)
    }

}
