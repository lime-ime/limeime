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

    func testActiveThisSessionRequiresPingAtOrAfterProbe() {
        XCTAssertTrue(FAStateResolver.isActiveThisSession(faPingAt: 101.5,
                                                          probeFiredAt: 100))
        XCTAssertTrue(FAStateResolver.isActiveThisSession(faPingAt: 100,
                                                          probeFiredAt: 100))
        XCTAssertFalse(FAStateResolver.isActiveThisSession(faPingAt: 99.99,
                                                           probeFiredAt: 100))
    }

    func testActiveThisSessionRejectsNilEvidence() {
        XCTAssertFalse(FAStateResolver.isActiveThisSession(faPingAt: nil,
                                                           probeFiredAt: 100))
        XCTAssertFalse(FAStateResolver.isActiveThisSession(faPingAt: 103,
                                                           probeFiredAt: nil))
        XCTAssertFalse(FAStateResolver.isActiveThisSession(faPingAt: nil,
                                                           probeFiredAt: nil))
    }

    func testActiveThisSessionUsesWindowBoundary() {
        XCTAssertTrue(FAStateResolver.isActiveThisSession(faPingAt: 103,
                                                          probeFiredAt: 100,
                                                          window: 3))
        XCTAssertFalse(FAStateResolver.isActiveThisSession(faPingAt: 103.01,
                                                           probeFiredAt: 100,
                                                           window: 3))
    }

    func testActiveThisSessionUsesAutomaticProbeWindow() {
        XCTAssertTrue(FAStateResolver.isActiveThisSession(faPingAt: 101.5,
                                                          probeFiredAt: 100,
                                                          mode: .automatic))
        XCTAssertFalse(FAStateResolver.isActiveThisSession(faPingAt: 101.51,
                                                           probeFiredAt: 100,
                                                           mode: .automatic))
    }

    func testActiveThisSessionUsesManualSwitchWindow() {
        XCTAssertTrue(FAStateResolver.isActiveThisSession(faPingAt: 110,
                                                          probeFiredAt: 100,
                                                          mode: .manualSwitch))
        XCTAssertFalse(FAStateResolver.isActiveThisSession(faPingAt: 110.01,
                                                           probeFiredAt: 100,
                                                           mode: .manualSwitch))
    }
}
