import Foundation

enum SyncPaths {
    static func tablesDir(_ base: URL) -> URL {
        base.appendingPathComponent("tables", isDirectory: true)
    }

    static func tableFile(_ base: URL, stem: String) -> URL {
        tablesDir(base).appendingPathComponent("\(stem).limedb")
    }

    static func tableMeta(_ base: URL, stem: String) -> URL {
        tablesDir(base).appendingPathComponent("\(stem).meta.json")
    }

    static func restoreDB(_ base: URL) -> URL {
        base.appendingPathComponent("restore.limedb")
    }

    static func restoreMeta(_ base: URL) -> URL {
        base.appendingPathComponent("restore.meta.json")
    }

    static func outboxDir(_ base: URL) -> URL {
        base.appendingPathComponent("outbox", isDirectory: true)
    }

    static func exportRequest(_ base: URL) -> URL {
        outboxDir(base).appendingPathComponent("export.request.json")
    }

    static func backupSnapshot(_ base: URL) -> URL {
        outboxDir(base).appendingPathComponent("backup.limedb")
    }

    static func receipt(_ base: URL) -> URL {
        outboxDir(base).appendingPathComponent("receipt.json")
    }
}

enum SyncSignal: String {
    case tablesUpdated = "org.limeime.tables.updated"
    case outboxUpdated = "org.limeime.outbox.updated"
    case importDone = "org.limeime.import.done"
    case importFailed = "org.limeime.import.failed"
}

struct TableMeta: Codable, Equatable {
    var restoreLearning: Bool?
    var displayName: String?
    var provenance: String?
}

struct RestoreMeta: Codable, Equatable {
    var epochUUID: String
    var schemaVersion: Int
}

struct FileIdentity: Equatable {
    let size: Int64
    let mtime: TimeInterval

    init?(url: URL) {
        let attributes: [FileAttributeKey: Any]
        do {
            attributes = try FileManager.default.attributesOfItem(atPath: url.path)
        } catch {
            return nil
        }

        guard
            let size = attributes[.size] as? NSNumber,
            let modificationDate = attributes[.modificationDate] as? Date
        else {
            return nil
        }

        self.size = size.int64Value
        self.mtime = modificationDate.timeIntervalSince1970
    }
}

func atomicWrite(_ data: Data, to url: URL) throws {
    let fm = FileManager.default
    let parent = url.deletingLastPathComponent()
    try fm.createDirectory(at: parent, withIntermediateDirectories: true)

    let tmp = parent.appendingPathComponent("\(url.lastPathComponent).tmp-\(UUID().uuidString)")
    defer { try? fm.removeItem(at: tmp) }

    try data.write(to: tmp)

    if fm.fileExists(atPath: url.path) {
        _ = try fm.replaceItemAt(url, withItemAt: tmp)
    } else {
        try fm.moveItem(at: tmp, to: url)
    }
}
