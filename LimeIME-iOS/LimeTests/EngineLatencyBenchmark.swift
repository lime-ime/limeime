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

// EngineLatencyBenchmark.swift
// LimeTests — unit-test target (host: LimeIME.app)
//
// Benchmarks the SearchServer/LimeDB candidate-generation engine directly
// without involving the keyboard UI or XCUITest. This sidesteps the iOS 26
// simulator EXC_GUARD crash that prevents XCUITest-based profiling
// (see docs/IOS_PROFILING.md §10).
//
// Results appear in Xcode's test navigator with baseline tracking.
// Additionally, every getMappingByCode call emits os_signpost intervals
// under the same subsystem/category as Profiling.swift so that a run
// under xctrace captures them identically to a live-keyboard trace.
//
// To capture a trace (simulator or device):
//
//   xcrun xctrace record --template 'Time Profiler' \
//     --output bench.trace --target-stdout - \
//     --launch -- xcodebuild test \
//       -project LimeIME-iOS/LimeIME.xcodeproj \
//       -scheme LimeIME \
//       -destination 'platform=iOS Simulator,id=<UDID>' \
//       -only-testing:LimeTests/EngineLatencyBenchmark \
//       test-without-building
//
// Then export signposts:
//
//   xcrun xctrace export --input bench.trace \
//     --xpath "/trace-toc/run/data/table[@schema='os-signpost']" \
//     --output bench-signposts.xml
//
// Simulator limitation: SRCROOT env is set by Xcode for simulator tests
// (the test process is a native macOS binary), allowing direct access to
// IM archive files in the source tree. This benchmark does NOT run on
// physical devices via standard `xcodebuild test` unless the IM archives
// are embedded as bundle resources.

import XCTest
import os.signpost
@testable import LimeIME

final class EngineLatencyBenchmark: XCTestCase {

    // Same subsystem/category as Profiling.swift so profile_keyboard.py's
    // xctrace xpath query captures both sources together.
    private static let log = OSLog(
        subsystem: "tw.jeremy.limeime.keyboard",
        category: "PointsOfInterest"
    )

    // Representative input codes per IM.
    // These are the raw keystroke strings passed to SearchServer.getMappingByCode()
    // — the same values KeyboardViewController sends after composing key presses.
    //
    // Cangjie: a=日 b=月 c=金 d=木 e=水 (standard cangjie root codes)
    private static let cangjieStrokes: [String] = ["a", "ab", "abd", "d", "di", "b", "bg", "c", "ce", "e"]
    // Array30: codes are 1-3 ascii characters from the array keyboard layout
    private static let arrayStrokes: [String]   = ["c", "cp", "cpu", "w", "wp", "wpu", "g", "g.", "g.;", "f"]
    // Phonetic: standard bopomofo key codes (ㄅ=1 ㄆ=q ㄇ=a ㄈ=z ...)
    // These map through preProcessingRemappingCode inside LimeDB.
    private static let phoneticStrokes: [String] = ["su3", "hk3", "cl3", "ej3", "gj4", "ji4", "dk4", "ru4", "su4", "bm"]
    // Dayi: 1-4 character codes using digits, letters, and punctuation
    // (e.g. q=石部首, q2i=硃, q3q=硯, q5.=砡)
    private static let dayiStrokes: [String]     = ["q", "q2", "q2i", "q3", "q3q", "q5", "q5.", "1", "1a", "2"]

    // Paths to IM archive files in the repo's Database/ folder.
    // Uses #file (compile-time source path) to navigate up to the repo root.
    // This works on iOS simulator (the test process is a native macOS binary
    // with full filesystem access) but NOT on physical devices.
    // #file: …/LimeIME-iOS/LimeTests/EngineLatencyBenchmark.swift
    //         → up 2 dirs → LimeIME-iOS/ → up 1 → repo root → Database/
    private static let dbDir: URL = URL(fileURLWithPath: #file)
        .deletingLastPathComponent()  // LimeTests/
        .deletingLastPathComponent()  // LimeIME-iOS/
        .deletingLastPathComponent()  // repo root
        .appendingPathComponent("Database")

    // Shared state per test method.
    private var tempURL: URL!
    private var limeDB: LimeDB!
    private var server: SearchServer!

    // MARK: - Setup / Teardown

    override func setUpWithError() throws {
        try super.setUpWithError()
        continueAfterFailure = false
        // Each test method allocates its own fresh DB so table imports don't interfere.
        tempURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("bench-\(UUID().uuidString).db")
    }

    override func tearDownWithError() throws {
        server = nil
        limeDB = nil
        try? FileManager.default.removeItem(at: tempURL)
        try? FileManager.default.removeItem(at: URL(fileURLWithPath: tempURL.path + "-wal"))
        try? FileManager.default.removeItem(at: URL(fileURLWithPath: tempURL.path + "-shm"))
        try super.tearDownWithError()
    }

    // Create a LimeDB+SearchServer pair seeded with a real IM archive.
    // `imFile`: file name inside Database/ (e.g. "cj.zip", "phonetic.zip")
    // `tableName`: IM table to import and query (e.g. "cj", "phonetic")
    private func makeServer(imFile: String, tableName: String) throws -> SearchServer {
        let srcPath = Self.dbDir.appendingPathComponent(imFile).path
        guard FileManager.default.fileExists(atPath: srcPath) else {
            throw XCTSkip("IM database not found at \(srcPath) — only runs on iOS simulator, not physical device")
        }
        limeDB = try LimeDB(path: tempURL.path)
        let dbServer = DBServer(_testDatasource: limeDB)
        dbServer.importZippedDb(sourceDbFile: URL(fileURLWithPath: srcPath), tableName: tableName)
        let ss = SearchServer(db: limeDB)
        ss.setTableName(tableName, hasNumberMapping: false, hasSymbolMapping: false)
        ss.initialCache()
        return ss
    }

    // MARK: - Cangjie

    func testBenchmarkCangjie() throws {
        server = try makeServer(imFile: "cj.zip", tableName: "cj")

        // Warm up: fill LimeDB's internal caches and SQLite page cache.
        // The first call per code populates the in-process mapping cache;
        // subsequent calls in the measure block hit the cache and test the
        // fast path only. To benchmark the cold DB path, call clearAllCaches()
        // inside the block — not done here because we want p50/p95 of the
        // steady-state (warm-cache) path that real users experience.
        for code in Self.cangjieStrokes {
            _ = server.getMappingByCode(code, isSoftKeyboard: true)
        }

        measure(metrics: [XCTClockMetric(), XCTCPUMetric()]) {
            for code in Self.cangjieStrokes {
                let id = OSSignpostID(log: Self.log)
                os_signpost(.begin, log: Self.log, name: "DBQueryStage1", signpostID: id, "cj:%{public}s", code)
                _ = server.getMappingByCode(code, isSoftKeyboard: true)
                os_signpost(.end,   log: Self.log, name: "DBQueryStage1", signpostID: id)
            }
        }
    }

    // MARK: - Array30

    func testBenchmarkArray() throws {
        server = try makeServer(imFile: "array.zip", tableName: "array")

        for code in Self.arrayStrokes {
            _ = server.getMappingByCode(code, isSoftKeyboard: true)
        }

        measure(metrics: [XCTClockMetric(), XCTCPUMetric()]) {
            for code in Self.arrayStrokes {
                let id = OSSignpostID(log: Self.log)
                os_signpost(.begin, log: Self.log, name: "DBQueryStage1", signpostID: id, "array:%{public}s", code)
                _ = server.getMappingByCode(code, isSoftKeyboard: true)
                os_signpost(.end,   log: Self.log, name: "DBQueryStage1", signpostID: id)
            }
        }
    }

    // MARK: - Phonetic

    func testBenchmarkPhonetic() throws {
        server = try makeServer(imFile: "phonetic.zip", tableName: "phonetic")

        for code in Self.phoneticStrokes {
            _ = server.getMappingByCode(code, isSoftKeyboard: true)
        }

        measure(metrics: [XCTClockMetric(), XCTCPUMetric()]) {
            for code in Self.phoneticStrokes {
                let id = OSSignpostID(log: Self.log)
                os_signpost(.begin, log: Self.log, name: "DBQueryStage1", signpostID: id, "phonetic:%{public}s", code)
                _ = server.getMappingByCode(code, isSoftKeyboard: true)
                os_signpost(.end,   log: Self.log, name: "DBQueryStage1", signpostID: id)
            }
        }
    }

    // MARK: - Dayi

    func testBenchmarkDayi() throws {
        server = try makeServer(imFile: "dayi.zip", tableName: "dayi")

        for code in Self.dayiStrokes {
            _ = server.getMappingByCode(code, isSoftKeyboard: true)
        }

        measure(metrics: [XCTClockMetric(), XCTCPUMetric()]) {
            for code in Self.dayiStrokes {
                let id = OSSignpostID(log: Self.log)
                os_signpost(.begin, log: Self.log, name: "DBQueryStage1", signpostID: id, "dayi:%{public}s", code)
                _ = server.getMappingByCode(code, isSoftKeyboard: true)
                os_signpost(.end,   log: Self.log, name: "DBQueryStage1", signpostID: id)
            }
        }
    }

    // MARK: - Cold-cache variants
    //
    // Stage 1 (getAllRecords:false, LIMIT 15): models the first-keystroke cold path.
    //   SQLite page cache is cold; measures real disk-hit latency.
    //   Target: eliminate via triggerPrefetch() so first stroke is always warm.
    //
    // Stage 2 (getAllRecords:true, LIMIT 210): models the background full-fetch.
    //   Stage 1 runs first inside the measure block to warm the SQLite page cache,
    //   then Swift cache is cleared and Stage 2 is measured — cold Swift cache,
    //   warm page cache — mirroring how KeyboardViewController dispatches Stage 2
    //   right after Stage 1 on the background thread.

    func testBenchmarkArray_coldCache_stage1() throws {
        server = try makeServer(imFile: "array.zip", tableName: "array")

        measure(metrics: [XCTClockMetric(), XCTCPUMetric()]) {
            server.clearAllCaches()
            for code in Self.arrayStrokes {
                let id = OSSignpostID(log: Self.log)
                os_signpost(.begin, log: Self.log, name: "DBQueryStage1", signpostID: id, "array-cold:%{public}s", code)
                _ = server.getMappingByCode(code, isSoftKeyboard: true)
                os_signpost(.end,   log: Self.log, name: "DBQueryStage1", signpostID: id)
            }
        }
    }

    func testBenchmarkArray_coldCache_stage2() throws {
        server = try makeServer(imFile: "array.zip", tableName: "array")

        measure(metrics: [XCTClockMetric(), XCTCPUMetric()]) {
            // Warm SQLite page cache (mirrors stage 1 running first in production)
            server.clearAllCaches()
            for code in Self.arrayStrokes { _ = server.getMappingByCode(code, isSoftKeyboard: true, getAllRecords: false) }
            // Cold Swift cache, warm page cache → Stage 2 latency
            server.clearAllCaches()
            for code in Self.arrayStrokes {
                let id = OSSignpostID(log: Self.log)
                os_signpost(.begin, log: Self.log, name: "DBQueryStage2", signpostID: id, "array-s2:%{public}s", code)
                _ = server.getMappingByCode(code, isSoftKeyboard: true, getAllRecords: true)
                os_signpost(.end,   log: Self.log, name: "DBQueryStage2", signpostID: id)
            }
        }
    }

    func testBenchmarkCangjie_coldCache_stage1() throws {
        server = try makeServer(imFile: "cj.zip", tableName: "cj")

        measure(metrics: [XCTClockMetric(), XCTCPUMetric()]) {
            server.clearAllCaches()
            for code in Self.cangjieStrokes {
                let id = OSSignpostID(log: Self.log)
                os_signpost(.begin, log: Self.log, name: "DBQueryStage1", signpostID: id, "cj-cold:%{public}s", code)
                _ = server.getMappingByCode(code, isSoftKeyboard: true)
                os_signpost(.end,   log: Self.log, name: "DBQueryStage1", signpostID: id)
            }
        }
    }

    func testBenchmarkCangjie_coldCache_stage2() throws {
        server = try makeServer(imFile: "cj.zip", tableName: "cj")

        measure(metrics: [XCTClockMetric(), XCTCPUMetric()]) {
            server.clearAllCaches()
            for code in Self.cangjieStrokes { _ = server.getMappingByCode(code, isSoftKeyboard: true, getAllRecords: false) }
            server.clearAllCaches()
            for code in Self.cangjieStrokes {
                let id = OSSignpostID(log: Self.log)
                os_signpost(.begin, log: Self.log, name: "DBQueryStage2", signpostID: id, "cj-s2:%{public}s", code)
                _ = server.getMappingByCode(code, isSoftKeyboard: true, getAllRecords: true)
                os_signpost(.end,   log: Self.log, name: "DBQueryStage2", signpostID: id)
            }
        }
    }

    func testBenchmarkPhonetic_coldCache_stage1() throws {
        server = try makeServer(imFile: "phonetic.zip", tableName: "phonetic")

        measure(metrics: [XCTClockMetric(), XCTCPUMetric()]) {
            server.clearAllCaches()
            for code in Self.phoneticStrokes {
                let id = OSSignpostID(log: Self.log)
                os_signpost(.begin, log: Self.log, name: "DBQueryStage1", signpostID: id, "phonetic-cold:%{public}s", code)
                _ = server.getMappingByCode(code, isSoftKeyboard: true)
                os_signpost(.end,   log: Self.log, name: "DBQueryStage1", signpostID: id)
            }
        }
    }

    func testBenchmarkPhonetic_coldCache_stage2() throws {
        server = try makeServer(imFile: "phonetic.zip", tableName: "phonetic")

        measure(metrics: [XCTClockMetric(), XCTCPUMetric()]) {
            server.clearAllCaches()
            for code in Self.phoneticStrokes { _ = server.getMappingByCode(code, isSoftKeyboard: true, getAllRecords: false) }
            server.clearAllCaches()
            for code in Self.phoneticStrokes {
                let id = OSSignpostID(log: Self.log)
                os_signpost(.begin, log: Self.log, name: "DBQueryStage2", signpostID: id, "phonetic-s2:%{public}s", code)
                _ = server.getMappingByCode(code, isSoftKeyboard: true, getAllRecords: true)
                os_signpost(.end,   log: Self.log, name: "DBQueryStage2", signpostID: id)
            }
        }
    }

    func testBenchmarkDayi_coldCache_stage1() throws {
        server = try makeServer(imFile: "dayi.zip", tableName: "dayi")

        measure(metrics: [XCTClockMetric(), XCTCPUMetric()]) {
            server.clearAllCaches()
            for code in Self.dayiStrokes {
                let id = OSSignpostID(log: Self.log)
                os_signpost(.begin, log: Self.log, name: "DBQueryStage1", signpostID: id, "dayi-cold:%{public}s", code)
                _ = server.getMappingByCode(code, isSoftKeyboard: true)
                os_signpost(.end,   log: Self.log, name: "DBQueryStage1", signpostID: id)
            }
        }
    }

    func testBenchmarkDayi_coldCache_stage2() throws {
        server = try makeServer(imFile: "dayi.zip", tableName: "dayi")

        measure(metrics: [XCTClockMetric(), XCTCPUMetric()]) {
            server.clearAllCaches()
            for code in Self.dayiStrokes { _ = server.getMappingByCode(code, isSoftKeyboard: true, getAllRecords: false) }
            server.clearAllCaches()
            for code in Self.dayiStrokes {
                let id = OSSignpostID(log: Self.log)
                os_signpost(.begin, log: Self.log, name: "DBQueryStage2", signpostID: id, "dayi-s2:%{public}s", code)
                _ = server.getMappingByCode(code, isSoftKeyboard: true, getAllRecords: true)
                os_signpost(.end,   log: Self.log, name: "DBQueryStage2", signpostID: id)
            }
        }
    }
}
