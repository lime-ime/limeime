import XCTest
import GRDB
@testable import LimeIME

final class EmojiAttachTest: XCTestCase {
    private var tempURL: URL!

    override func setUp() {
        super.setUp()
        tempURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".db")
    }

    override func tearDown() {
        removeDBFiles(tempURL)
        super.tearDown()
    }

    private func makeLimeDB() throws -> LimeDB {
        try LimeDB(path: tempURL.path)
    }

    private func removeDBFiles(_ url: URL) {
        try? FileManager.default.removeItem(at: url)
        try? FileManager.default.removeItem(at: URL(fileURLWithPath: url.path + "-wal"))
        try? FileManager.default.removeItem(at: URL(fileURLWithPath: url.path + "-shm"))
    }

    func testEmojiSearchViaAttachment() throws {
        let db = try makeLimeDB()

        XCTAssertFalse(db.searchEmoji("grinning", locale: .en).isEmpty)
    }

    func testEmojiPanelItems() throws {
        let db = try makeLimeDB()

        XCTAssertEqual(db.loadEmojiPanelItems(limit: 10).count, 10)
    }

    func testShadowingHygiene() throws {
        var db = try makeLimeDB()
        try db.closeForReplacement()

        let queue = try DatabaseQueue(path: tempURL.path)
        try queue.write { rawDB in
            try rawDB.execute(sql: """
                CREATE TABLE emoji_data (
                    value TEXT PRIMARY KEY,
                    cp TEXT NOT NULL,
                    group_name TEXT NOT NULL,
                    subgroup TEXT NOT NULL,
                    sort_order INTEGER NOT NULL,
                    name_en TEXT,
                    name_tw TEXT,
                    tags_en TEXT,
                    tags_tw TEXT,
                    version REAL NOT NULL
                )
            """)
            try rawDB.execute(sql: """
                INSERT INTO emoji_data
                (value, cp, group_name, subgroup, sort_order, name_en, name_tw, tags_en, tags_tw, version)
                VALUES ('FAKE', 'FAKE', 'Smileys & Emotion', 'fake', 0, 'fake emoji', '假', 'fake', '假', 1.0)
            """)
        }

        db = try makeLimeDB()

        XCTAssertFalse(db.tableExists("emoji_data"))
        XCTAssertFalse(db.searchEmoji("fake", locale: .en).map(\.word).contains("FAKE"))
        XCTAssertFalse(db.searchEmoji("grinning", locale: .en).isEmpty)
    }

    func testEmojiUserFKRebuild() throws {
        var config = Configuration()
        config.foreignKeysEnabled = false
        let queue = try DatabaseQueue(path: tempURL.path, configuration: config)
        try queue.write { rawDB in
            try rawDB.execute(sql: """
                CREATE TABLE emoji_user (
                    value TEXT PRIMARY KEY REFERENCES emoji_data(value),
                    last_used INTEGER,
                    use_count INTEGER NOT NULL DEFAULT 0
                )
            """)
            try rawDB.execute(sql: """
                INSERT INTO emoji_user (value, last_used, use_count)
                VALUES ('prior', 100, 4)
            """)
        }

        let db = try makeLimeDB()
        db.recordEmojiUsage("not-in-main", timestampSeconds: 200)

        let createSQL = try db.dbQueue.read { rawDB in
            try String.fetchOne(rawDB, sql: """
                SELECT sql FROM sqlite_master
                WHERE type = 'table' AND name = 'emoji_user'
            """) ?? ""
        }
        XCTAssertFalse(createSQL.uppercased().contains("REFERENCES"))
        XCTAssertEqual(try db.emojiUserCountForTest(value: "prior"), 4)
        XCTAssertEqual(try db.emojiUserCountForTest(value: "not-in-main"), 1)
    }

    func testEmojiUserDanglingTolerated() throws {
        let db = try makeLimeDB()

        db.recordEmojiUsage("🦖", timestampSeconds: 200)
        db.recordEmojiUsage("ZZZ", timestampSeconds: 300)

        let recents = db.loadRecentEmoji(limit: 10).map(\.word)
        XCTAssertTrue(recents.contains("🦖"))
        XCTAssertFalse(recents.contains("ZZZ"))
    }
}
