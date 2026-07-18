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
