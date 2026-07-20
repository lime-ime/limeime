/*
 *
 *  *
 *  **    Copyright 2026, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

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

    func testOldRelayJsonDoesNotResetMigratedPhonePreferences() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: dir) }
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let oldJson = #"{"hanConvert":0,"splitKeyboard":1,"updatedAt":42}"#.data(using: .utf8)!
        try oldJson.write(to: dir.appendingPathComponent("relay-prefs.json"))

        let state = try XCTUnwrap(try LimeIME.KeyboardRelayPrefStore(baseDirectory: dir).read())
        XCTAssertNil(state.phonePortraitMode)
        XCTAssertNil(state.phoneLandscapeSplit)
        let payload = LimeIME.encodeRelayPayload(faOn: false, ts: 43, prefs: state)
        XCTAssertFalse(payload.contains(";pp="))
        XCTAssertFalse(payload.contains(";pls="))
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

    func testReverseLookupRoundTripsAndApplies() throws {
        let prefs = LimeIME.RelayPrefState(hanConvert: 0, splitKeyboard: 0, updatedAt: 50,
                                           reverseLookupIM: "dayi", reverseLookupValue: "cj")
        let payload = LimeIME.encodeRelayPayload(faOn: false, ts: 1, prefs: prefs)
        let decoded = try XCTUnwrap(LimeIME.decodeRelayPayload(LimeIME.RelayToken.request + payload))
        XCTAssertEqual(decoded.rlim, "dayi")
        XCTAssertEqual(decoded.rlval, "cj")

        // A concatenated duplicate payload still yields the clean value.
        let dup = try XCTUnwrap(LimeIME.decodeRelayPayload(payload + payload))
        XCTAssertEqual(dup.rlval, "cj")

        // Old payload without reverse-lookup fields → nil.
        let plain = try XCTUnwrap(LimeIME.decodeRelayPayload("LIMERLY!v1;fa=1;ts=3"))
        XCTAssertNil(plain.rlim)
        XCTAssertNil(plain.rlval)

        let (defaults, suite) = try makeDefaults()
        defer { UserDefaults.standard.removePersistentDomain(forName: suite) }
        XCTAssertTrue(LimeIME.RelayPrefSync.apply(han: nil, split: nil,
                                                  reverseLookupIM: "dayi", reverseLookupValue: "cj",
                                                  pts: 50, to: defaults))
        XCTAssertEqual(defaults.string(forKey: LimeIME.RelayPrefSync.reverseLookupKey(for: "dayi")), "cj")
        XCTAssertEqual(defaults.string(forKey: "dayi_im_reverselookup"), "cj")
        // Older pts must not overwrite.
        XCTAssertFalse(LimeIME.RelayPrefSync.apply(han: nil, split: nil,
                                                   reverseLookupIM: "dayi", reverseLookupValue: "none",
                                                   pts: 49, to: defaults))
        XCTAssertEqual(defaults.string(forKey: "dayi_im_reverselookup"), "cj")
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
