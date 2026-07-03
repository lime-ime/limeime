// FAStateTest.swift

import XCTest
@testable import LimeIME

final class FAStateTest: XCTestCase {
    func testFreshOnHeartbeatConfirmsOn() {
        let now = Date(timeIntervalSince1970: 1_000)
        let heartbeat = KeyboardHeartbeat(hasFullAccess: true,
                                          lastSeenAt: 950,
                                          lastDBError: nil)

        XCTAssertEqual(FAStateResolver.resolve(heartbeat: heartbeat,
                                               now: now,
                                               faPingThisSession: nil),
                       .confirmedOn)
    }

    func testStaleOnHeartbeatIsUnknown() {
        let now = Date(timeIntervalSince1970: 1_000)
        let heartbeat = KeyboardHeartbeat(hasFullAccess: true,
                                          lastSeenAt: 800,
                                          lastDBError: nil)

        XCTAssertEqual(FAStateResolver.resolve(heartbeat: heartbeat,
                                               now: now,
                                               faPingThisSession: nil),
                       .unknown)
    }

    func testMissingHeartbeatIsUnknown() {
        XCTAssertEqual(FAStateResolver.resolve(heartbeat: nil,
                                               now: Date(timeIntervalSince1970: 1_000),
                                               faPingThisSession: nil),
                       .unknown)
    }

    func testOffPingConfirmsOff() {
        XCTAssertEqual(FAStateResolver.resolve(heartbeat: nil,
                                               now: Date(timeIntervalSince1970: 1_000),
                                               faPingThisSession: false),
                       .confirmedOff)
    }

    func testFreshOnHeartbeatOverridesOffPing() {
        let now = Date(timeIntervalSince1970: 1_000)
        let heartbeat = KeyboardHeartbeat(hasFullAccess: true,
                                          lastSeenAt: 950,
                                          lastDBError: nil)

        XCTAssertEqual(FAStateResolver.resolve(heartbeat: heartbeat,
                                               now: now,
                                               faPingThisSession: false),
                       .confirmedOn)
    }
}
