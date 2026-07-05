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
