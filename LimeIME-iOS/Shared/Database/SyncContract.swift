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

    static func heartbeat(_ base: URL) -> URL {
        outboxDir(base).appendingPathComponent("heartbeat.json")
    }
}

enum SyncSignal: String {
    case tablesUpdated = "org.limeime.tables.updated"
    case outboxUpdated = "org.limeime.outbox.updated"
    case importDone = "org.limeime.import.done"
    case importFailed = "org.limeime.import.failed"
    case faOn = "org.limeime.fa.on"
    case faOff = "org.limeime.fa.off"
}

func postSyncSignal(_ signal: SyncSignal) {
    let name = CFNotificationName(signal.rawValue as CFString)
    CFNotificationCenterPostNotification(
        CFNotificationCenterGetDarwinNotifyCenter(), name, nil, nil, true)
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

struct ExportRequest: Codable, Equatable {
    var requestUUID: String
    var expiresAt: TimeInterval
}

struct ExportReceipt: Codable, Equatable {
    var requestUUID: String
    var epochUUID: String
    var at: TimeInterval
}

struct KeyboardHeartbeat: Codable, Equatable {
    var hasFullAccess: Bool
    var lastSeenAt: TimeInterval
    var lastDBError: String?
}

enum FAState: Equatable {
    case confirmedOn
    case confirmedOff
    case unknown
}

enum FAStateResolver {
    // ponytail: fixed freshness window from the FA detection spec; make configurable only if device polling changes.
    static let heartbeatFreshness: TimeInterval = 120

    static func resolve(heartbeat: KeyboardHeartbeat?,
                        now: Date = Date(),
                        faPingThisSession: Bool?) -> FAState {
        if isFreshOnHeartbeat(heartbeat, now: now) {
            return .confirmedOn
        }
        if faPingThisSession == false {
            return .confirmedOff
        }
        return .unknown
    }

    static func hasFreshEvidence(heartbeat: KeyboardHeartbeat?,
                                 now: Date = Date(),
                                 faPingThisSession: Bool?) -> Bool {
        isFreshOnHeartbeat(heartbeat, now: now) || faPingThisSession != nil
    }

    static func isFreshOnHeartbeat(_ heartbeat: KeyboardHeartbeat?,
                                   now: Date = Date()) -> Bool {
        guard let heartbeat, heartbeat.hasFullAccess else { return false }
        return now.timeIntervalSince1970 - heartbeat.lastSeenAt <= heartbeatFreshness
    }
}

final class FAPingObserver {
    private let onPing: (Bool) -> Void
    private var observer: UnsafeRawPointer?

    init(onPing: @escaping (Bool) -> Void) {
        self.onPing = onPing
        self.observer = UnsafeRawPointer(Unmanaged.passUnretained(self).toOpaque())
        addObserver(for: .faOn)
        addObserver(for: .faOff)
    }

    deinit {
        guard let observer else { return }
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        CFNotificationCenterRemoveObserver(center, observer,
                                           CFNotificationName(SyncSignal.faOn.rawValue as CFString),
                                           nil)
        CFNotificationCenterRemoveObserver(center, observer,
                                           CFNotificationName(SyncSignal.faOff.rawValue as CFString),
                                           nil)
    }

    private func addObserver(for signal: SyncSignal) {
        guard let observer else { return }
        CFNotificationCenterAddObserver(
            CFNotificationCenterGetDarwinNotifyCenter(),
            observer,
            { _, observer, name, _, _ in
                guard let observer,
                      let rawName = name?.rawValue as String?
                else { return }
                let monitor = Unmanaged<FAPingObserver>
                    .fromOpaque(observer)
                    .takeUnretainedValue()
                let hasFullAccess: Bool
                if rawName == SyncSignal.faOn.rawValue {
                    hasFullAccess = true
                } else if rawName == SyncSignal.faOff.rawValue {
                    hasFullAccess = false
                } else {
                    return
                }
                DispatchQueue.main.async {
                    monitor.onPing(hasFullAccess)
                }
            },
            signal.rawValue as CFString,
            nil,
            .deliverImmediately)
    }
}

struct FileIdentity: Equatable {
    let size: Int64
    let mtime: TimeInterval

    init(size: Int64, mtime: TimeInterval) {
        self.size = size
        self.mtime = mtime
    }

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
