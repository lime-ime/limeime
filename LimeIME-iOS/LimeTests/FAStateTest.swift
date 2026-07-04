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

        // Un-timestamped ping = oldest evidence; the fresh heartbeat wins.
        XCTAssertEqual(FAStateResolver.resolve(heartbeat: heartbeat,
                                               now: now,
                                               faPingThisSession: false),
                       .confirmedOn)
    }

    // Recency rule (final-review finding #3): the heartbeat file cannot be
    // rewritten after FA is revoked, so an OFF ping NEWER than a still-fresh
    // ON heartbeat must win.
    func testNewerOffPingOverridesFreshHeartbeat() {
        let now = Date(timeIntervalSince1970: 1_000)
        let heartbeat = KeyboardHeartbeat(hasFullAccess: true,
                                          lastSeenAt: 950,
                                          lastDBError: nil)

        XCTAssertEqual(FAStateResolver.resolve(heartbeat: heartbeat,
                                               now: now,
                                               faPingThisSession: false,
                                               faPingAt: 980),
                       .confirmedOff)
        XCTAssertEqual(FAStateResolver.resolve(heartbeat: heartbeat,
                                               now: now,
                                               faPingThisSession: false,
                                               faPingAt: 940),
                       .confirmedOn)
    }
}
