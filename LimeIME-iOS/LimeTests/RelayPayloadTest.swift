// RelayPayloadTest.swift

import XCTest
@testable import LimeIME

final class RelayPayloadTest: XCTestCase {
    func testEncodeDecodeRoundTripFromProbeText() throws {
        let payload = encodeRelayPayload(faOn: true, ts: 1_234.5)
        let decoded = try XCTUnwrap(decodeRelayPayload(RelayToken.request + payload))

        XCTAssertEqual(decoded.proto, 1)
        XCTAssertTrue(decoded.faOn)
        XCTAssertEqual(decoded.ts, 1_234.5)
    }

    func testDecodeRejectsTokenAndGarbage() {
        XCTAssertNil(decodeRelayPayload(RelayToken.request))
        XCTAssertNil(decodeRelayPayload("not a relay"))
        XCTAssertNil(decodeRelayPayload("LIMERLY!v1;fa=2;ts=123"))
        XCTAssertNil(decodeRelayPayload("LIMERLY!v1;fa=1;ts=nope"))
    }

    func testRelayRequestRequiresContextEndingInToken() {
        // Token entirely before the cursor.
        XCTAssertTrue(isRelayRequestContext(before: RelayToken.request))
        // Token entirely after the cursor (programmatic set leaves cursor at 0).
        XCTAssertTrue(isRelayRequestContext(before: nil, after: RelayToken.request))
        // Cursor splitting the token.
        XCTAssertTrue(isRelayRequestContext(before: "LIME", after: "RELAYREQ?"))
        XCTAssertFalse(isRelayRequestContext(before: nil))
        XCTAssertFalse(isRelayRequestContext(before: "ordinary text"))
        // Real field: token followed by user text must NOT match.
        XCTAssertFalse(isRelayRequestContext(before: "\(RelayToken.request) trailing text"))
    }
}
