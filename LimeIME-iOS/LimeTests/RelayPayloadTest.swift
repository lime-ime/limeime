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

// RelayPayloadTest.swift

import XCTest
@testable import LimeIME

final class RelayPayloadTest: XCTestCase {
    func testEncodeDecodeRoundTripFromProbeText() throws {
        let payload = LimeIME.encodeRelayPayload(faOn: true, ts: 1_234.5)
        let decoded = try XCTUnwrap(LimeIME.decodeRelayPayload(LimeIME.RelayToken.request + payload))

        XCTAssertEqual(decoded.proto, 1)
        XCTAssertTrue(decoded.faOn)
        XCTAssertEqual(decoded.ts, 1_234.5)
    }

    func testEncodeDecodeRoundTripsOneHandAndNumpadAnchor() throws {
        let prefs = LimeIME.RelayPrefState(hanConvert: 1, splitKeyboard: 2, updatedAt: 42.0,
                                           oneHand: 2, numpadAnchor: 3)
        let payload = LimeIME.encodeRelayPayload(faOn: true, ts: 1_234.5, prefs: prefs)
        let decoded = try XCTUnwrap(LimeIME.decodeRelayPayload(LimeIME.RelayToken.request + payload))

        XCTAssertEqual(decoded.han, 1)
        XCTAssertEqual(decoded.split, 2)
        XCTAssertEqual(decoded.oneHand, 2)
        XCTAssertEqual(decoded.numpadAnchor, 3)
    }

    func testDecodeDefaultsOneHandAndNumpadAnchorToZeroWhenAbsent() throws {
        // Backward compatibility: a payload from an older keyboard build has no oh=/na= fields.
        let payload = LimeIME.encodeRelayPayload(faOn: true, ts: 1_234.5)
        let decoded = try XCTUnwrap(LimeIME.decodeRelayPayload(LimeIME.RelayToken.request + payload))

        XCTAssertNil(decoded.oneHand)
        XCTAssertNil(decoded.numpadAnchor)
    }

    func testEncodeDecodeRoundTripsPhonePortraitAndLandscapeSplit() throws {
        // Issue #169: the integrated iPhone phone prefs ride the FA-off text relay so a
        // globe-menu change reaches the settings app.
        let prefs = LimeIME.RelayPrefState(hanConvert: 0, splitKeyboard: 0, updatedAt: 42.0,
                                           phonePortraitMode: 3, phoneLandscapeSplit: true)
        let payload = LimeIME.encodeRelayPayload(faOn: true, ts: 1_234.5, prefs: prefs)
        let decoded = try XCTUnwrap(LimeIME.decodeRelayPayload(LimeIME.RelayToken.request + payload))

        XCTAssertEqual(decoded.phonePortraitMode, 3)
        XCTAssertEqual(decoded.phoneLandscapeSplit, true)
    }

    func testDecodeDefaultsPhonePrefsWhenAbsent() throws {
        // Older keyboard builds emit no pp=/pls= fields.
        let payload = LimeIME.encodeRelayPayload(faOn: true, ts: 1_234.5)
        let decoded = try XCTUnwrap(LimeIME.decodeRelayPayload(LimeIME.RelayToken.request + payload))

        XCTAssertNil(decoded.phonePortraitMode)
        XCTAssertNil(decoded.phoneLandscapeSplit)
    }

    func testLegacyRelayStateDoesNotInventCanonicalPhoneValues() throws {
        let prefs = LimeIME.RelayPrefState(hanConvert: 0, splitKeyboard: 1, updatedAt: 42.0)
        let payload = LimeIME.encodeRelayPayload(faOn: true, ts: 1_234.5, prefs: prefs)
        XCTAssertFalse(payload.contains(";pp="))
        XCTAssertFalse(payload.contains(";pls="))
        let decoded = try XCTUnwrap(LimeIME.decodeRelayPayload(payload))
        XCTAssertNil(decoded.phonePortraitMode)
        XCTAssertNil(decoded.phoneLandscapeSplit)
    }

    func testRelayPayloadScopesGeometryToCurrentDeviceProfile() throws {
        let phone = LimeIME.RelayPrefState(hanConvert: 0, splitKeyboard: 1, updatedAt: 42.0,
                                          oneHand: 2, numpadAnchor: 3,
                                          phonePortraitMode: 2, phoneLandscapeSplit: true,
                                          geometryProfile: "phone")
        let phonePayload = LimeIME.encodeRelayPayload(faOn: true, ts: 1, prefs: phone)
        XCTAssertFalse(phonePayload.contains(";split="))
        XCTAssertFalse(phonePayload.contains(";na="))
        XCTAssertTrue(phonePayload.contains(";pp=2"))

        var tablet = phone
        tablet.geometryProfile = "tablet"
        let tabletPayload = LimeIME.encodeRelayPayload(faOn: true, ts: 1, prefs: tablet)
        XCTAssertTrue(tabletPayload.contains(";split=1"))
        XCTAssertTrue(tabletPayload.contains(";na=3"))
        XCTAssertFalse(tabletPayload.contains(";pp="))
        XCTAssertFalse(tabletPayload.contains(";pls="))
    }

    func testDecodeRejectsTokenAndGarbage() {
        XCTAssertNil(LimeIME.decodeRelayPayload(LimeIME.RelayToken.request))
        XCTAssertNil(LimeIME.decodeRelayPayload("not a relay"))
        XCTAssertNil(LimeIME.decodeRelayPayload("LIMERLY!v1;fa=2;ts=123"))
        XCTAssertNil(LimeIME.decodeRelayPayload("LIMERLY!v1;fa=1;ts=nope"))
    }

    func testRelayRequestRequiresContextEndingInToken() {
        // Token entirely before the cursor.
        XCTAssertTrue(LimeIME.isRelayRequestContext(before: LimeIME.RelayToken.request))
        // Token entirely after the cursor (programmatic set leaves cursor at 0).
        XCTAssertTrue(LimeIME.isRelayRequestContext(before: nil, after: LimeIME.RelayToken.request))
        // Cursor splitting the token.
        XCTAssertTrue(LimeIME.isRelayRequestContext(before: "LIME", after: "RELAYREQ?"))
        XCTAssertFalse(LimeIME.isRelayRequestContext(before: nil))
        XCTAssertFalse(LimeIME.isRelayRequestContext(before: "ordinary text"))
        // Real field: token followed by user text must NOT match.
        XCTAssertFalse(LimeIME.isRelayRequestContext(before: "\(LimeIME.RelayToken.request) trailing text"))
    }
}
