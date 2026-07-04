import Foundation

enum SyncPaths {
    static func coldDB(_ base: URL) -> URL {
        base.appendingPathComponent("cold.limedb")
    }

    static func coldMeta(_ base: URL) -> URL {
        base.appendingPathComponent("cold.meta.json")
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

enum RecordEditingCapability: Equatable {
    case readOnly
    case live

    static func resolve(faState: FAState,
                        activeThisSession: Bool = true,
                        forceLive: Bool = forceLiveEditingEnabled()) -> RecordEditingCapability {
        forceLive || (faState == .confirmedOn && activeThisSession) ? .live : .readOnly
    }

    static func forceLiveEditingEnabled(arguments: [String] = ProcessInfo.processInfo.arguments) -> Bool {
        #if DEBUG
        // ponytail: simulator UI tests need the live editor without device-only Full Access writes.
        guard let index = arguments.firstIndex(of: "-limeUITestForceLiveEditing"),
              arguments.indices.contains(index + 1)
        else { return false }
        return arguments[index + 1] == "1"
        #else
        return false
        #endif
    }
}

enum FAStateResolver {
    // ponytail: fixed freshness window from the FA detection spec; make configurable only if device polling changes.
    static let heartbeatFreshness: TimeInterval = 120
    // ponytail: short active-keyboard proof window; widen only if device probes prove slower.
    static let activeProbeWaitNanoseconds: UInt64 = 2_500_000_000

    static func resolve(heartbeat: KeyboardHeartbeat?,
                        now: Date = Date(),
                        faPingThisSession: Bool?,
                        faPingAt: TimeInterval? = nil) -> FAState {
        let hbFresh = isFreshOnHeartbeat(heartbeat, now: now)
        // Recency rule: the heartbeat file cannot be rewritten once FA is revoked,
        // so an ON heartbeat can stay "fresh" for up to the window after a revoke.
        // An OFF ping NEWER than the heartbeat therefore overrides it. A ping with
        // no timestamp is treated as oldest (a fresh heartbeat wins).
        if faPingThisSession == false {
            let pingNewer = (faPingAt ?? 0) > (heartbeat?.lastSeenAt ?? -1)
            if !hbFresh || pingNewer { return .confirmedOff }
        }
        if hbFresh { return .confirmedOn }
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

    static func isActiveThisSession(faPingAt: TimeInterval?,
                                    probeFiredAt: TimeInterval?,
                                    window: TimeInterval = 3) -> Bool {
        guard let faPingAt, let probeFiredAt else { return false }
        let elapsed = faPingAt - probeFiredAt
        return elapsed >= 0 && elapsed <= window
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
