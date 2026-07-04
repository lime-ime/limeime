// RelayPrefSyncTest.swift

import XCTest
@testable import LimeIME

final class RelayPrefSyncTest: XCTestCase {
    func testPayloadRoundTripsPrefsAndOldPayloadStillDecodes() throws {
        let prefs = LimeIME.RelayPrefState(hanConvert: 2, splitKeyboard: 1, updatedAt: 42.5)
        let payload = LimeIME.encodeRelayPayload(faOn: false, ts: 12.25, prefs: prefs)
        let decoded = try XCTUnwrap(LimeIME.decodeRelayPayload(LimeIME.RelayToken.request + payload))

        XCTAssertEqual(decoded.proto, 1)
        XCTAssertFalse(decoded.faOn)
        XCTAssertEqual(decoded.ts, 12.25)
        XCTAssertEqual(decoded.han, 2)
        XCTAssertEqual(decoded.split, 1)
        XCTAssertEqual(decoded.pts, 42.5)

        let old = try XCTUnwrap(LimeIME.decodeRelayPayload("LIMERLY!v1;fa=1;ts=3.5"))
        XCTAssertTrue(old.faOn)
        XCTAssertNil(old.han)
        XCTAssertNil(old.split)
        XCTAssertNil(old.pts)
    }

    func testKeyboardRelayPrefStorePersistsAndUpdates() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: dir) }
        let store = LimeIME.KeyboardRelayPrefStore(baseDirectory: dir)

        XCTAssertNil(try store.read())

        let initial = LimeIME.RelayPrefState(hanConvert: 1, splitKeyboard: 0, updatedAt: 10)
        try store.write(initial)
        XCTAssertEqual(try store.read(), initial)

        let updated = try store.update(splitKeyboard: 2, updatedAt: 11)
        XCTAssertEqual(updated, LimeIME.RelayPrefState(hanConvert: 1, splitKeyboard: 2, updatedAt: 11))
        XCTAssertEqual(try store.read(), updated)
    }

    func testRelayPrefApplyIsLastWriterWins() throws {
        let (defaults, suite) = try makeDefaults()
        defer { UserDefaults.standard.removePersistentDomain(forName: suite) }

        XCTAssertTrue(LimeIME.RelayPrefSync.apply(han: 2, split: 1, pts: 100, to: defaults))
        XCTAssertEqual(defaults.integer(forKey: LimeIME.RelayPrefSync.hanConvertKey), 2)
        XCTAssertEqual(defaults.integer(forKey: LimeIME.RelayPrefSync.splitKeyboardKey), 1)
        XCTAssertEqual(defaults.double(forKey: LimeIME.RelayPrefSync.appliedAtKey), 100)

        XCTAssertFalse(LimeIME.RelayPrefSync.apply(han: 0, split: 0, pts: 100, to: defaults))
        XCTAssertEqual(defaults.integer(forKey: LimeIME.RelayPrefSync.hanConvertKey), 2)
        XCTAssertEqual(defaults.integer(forKey: LimeIME.RelayPrefSync.splitKeyboardKey), 1)

        XCTAssertFalse(LimeIME.RelayPrefSync.apply(han: 0, split: 0, pts: 99, to: defaults))
        XCTAssertEqual(defaults.integer(forKey: LimeIME.RelayPrefSync.hanConvertKey), 2)
        XCTAssertEqual(defaults.integer(forKey: LimeIME.RelayPrefSync.splitKeyboardKey), 1)

        XCTAssertTrue(LimeIME.RelayPrefSync.apply(han: nil, split: 2, pts: 101, to: defaults))
        XCTAssertEqual(defaults.integer(forKey: LimeIME.RelayPrefSync.hanConvertKey), 2)
        XCTAssertEqual(defaults.integer(forKey: LimeIME.RelayPrefSync.splitKeyboardKey), 2)
        XCTAssertEqual(defaults.double(forKey: LimeIME.RelayPrefSync.appliedAtKey), 101)
    }

    func testRelayOnlyFlagClearsPlainSharedValues() throws {
        let (defaults, suite) = try makeDefaults()
        defer { UserDefaults.standard.removePersistentDomain(forName: suite) }
        defaults.set(2, forKey: LimeIME.RelayPrefSync.hanConvertKey)
        defaults.set(1, forKey: LimeIME.RelayPrefSync.splitKeyboardKey)
        defaults.set(10, forKey: LimeIME.RelayPrefSync.appliedAtKey)

        LimeIME.RelayPrefSync.prepareRelayOnlyIfNeeded(in: defaults,
                                                       arguments: ["LimeIME", "-limeUITestForceRelayOnly", "1"])

        XCTAssertNil(defaults.object(forKey: LimeIME.RelayPrefSync.hanConvertKey))
        XCTAssertNil(defaults.object(forKey: LimeIME.RelayPrefSync.splitKeyboardKey))
        XCTAssertNil(defaults.object(forKey: LimeIME.RelayPrefSync.appliedAtKey))
    }

    private func makeDefaults() throws -> (UserDefaults, String) {
        let suite = "RelayPrefSyncTest.\(UUID().uuidString)"
        return (try XCTUnwrap(UserDefaults(suiteName: suite)), suite)
    }
}
