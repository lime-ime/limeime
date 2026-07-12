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

//
//  Profiling.swift
//  LimeKeyboard
//
//  Stroke-to-stroke profiling helper. See docs/IOS_PROFILING.md.
//
//  ─────────────────────────────────────────────────────────────────────
//  Two-level switch
//  ─────────────────────────────────────────────────────────────────────
//
//  1) Compile-time flag `PROFILING` (preferred for App-Store builds).
//     - When NOT defined: every `Prof.*` call collapses to a no-op
//       inline shim. Zero CPU cost, zero binary footprint.
//     - When defined: the real `os_signpost` implementation is compiled
//       in.
//
//     To enable, add `-D PROFILING` to OTHER_SWIFT_FLAGS in the
//     LimeIMEKeyboard target. In project.yml:
//
//         LimeIMEKeyboard:
//           settings:
//             configs:
//               Debug:
//                 OTHER_SWIFT_FLAGS: "$(inherited) -D PROFILING"
//
//     Or via a dedicated `Profile` build configuration (recommended for
//     CI; keeps the regular Debug builds free of any signpost overhead).
//
//  2) Runtime flag `Prof.enabled` (only meaningful when PROFILING is
//     compiled in). Default true. Flip to false to silence signposts
//     temporarily without rebuilding — useful for warm-up frames or to
//     isolate a specific window of activity:
//
//         Prof.enabled = false
//         doWarmup()
//         Prof.enabled = true
//         doMeasuredWork()
//
//  Signpost calls themselves cost ~30 ns each, but the compile flag
//  exists so Release/App-Store builds carry zero profiling code at all.
//

import Foundation
import os.signpost

#if PROFILING

enum Prof {
    /// Subsystem name shared by the iOS keyboard extension. Must match
    /// the value used by the profiling driver
    /// (`scripts/profile_keyboard.py`) when it parses xctrace exports.
    static let log = OSLog(subsystem: "tw.jeremy.limeime.keyboard",
                           category: .pointsOfInterest)

    /// Runtime kill-switch. When false, every begin/end/event call
    /// short-circuits before touching the signpost subsystem.
    /// A stray flip mid-stroke at worst drops a single span.
    /// Default: enabled (matches the compile-time intent).
    static var enabled: Bool = true

    @inline(__always)
    static func newID() -> OSSignpostID {
        guard enabled else { return .invalid }
        return OSSignpostID(log: log)
    }

    @inline(__always)
    static func begin(_ name: StaticString, id: OSSignpostID) {
        guard enabled, id != .invalid else { return }
        os_signpost(.begin, log: log, name: name, signpostID: id)
    }

    @inline(__always)
    static func end(_ name: StaticString, id: OSSignpostID) {
        guard enabled, id != .invalid else { return }
        os_signpost(.end, log: log, name: name, signpostID: id)
    }

    /// Fire-and-forget instantaneous mark.
    @inline(__always)
    static func event(_ name: StaticString, _ message: String = "") {
        guard enabled else { return }
        if message.isEmpty {
            os_signpost(.event, log: log, name: name)
        } else {
            os_signpost(.event, log: log, name: name,
                        "%{public}s", message)
        }
    }

    /// Convenience: time a synchronous block. Avoid in hot paths because
    /// the closure prevents inlining of the wrapped work.
    @discardableResult
    static func interval<T>(_ name: StaticString,
                            _ block: () throws -> T) rethrows -> T {
        guard enabled else { return try block() }
        let id = newID()
        begin(name, id: id)
        defer { end(name, id: id) }
        return try block()
    }
}

#else

/// Non-PROFILING build shim. Every call collapses to nothing after
/// inlining, so call sites can stay in production code unconditionally.
enum Prof {
    typealias Token = UInt64

    /// Present so code that flips this in DEBUG still compiles in
    /// Release. Reads/writes are dead-stripped.
    static var enabled: Bool = false

    @inline(__always) static func newID() -> Token { 0 }
    @inline(__always) static func begin(_: StaticString, id _: Token) {}
    @inline(__always) static func end(_: StaticString, id _: Token) {}
    @inline(__always) static func event(_: StaticString, _: String = "") {}

    @discardableResult
    @inline(__always)
    static func interval<T>(_: StaticString,
                            _ block: () throws -> T) rethrows -> T {
        try block()
    }
}

#endif
