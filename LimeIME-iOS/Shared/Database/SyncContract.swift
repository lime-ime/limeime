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

enum RelayToken {
    // Plain ASCII, no BOM: iOS strips/normalizes a leading U+FEFF out of
    // documentContextBeforeInput, so a BOM-prefixed token never matches hasSuffix
    // on the keyboard side. The field is a 1×1 invisible probe, so visibility of the
    // token doesn't matter; distinctiveness (a real field never ends with this) does.
    static let request = "LIMERELAYREQ?"
}

func encodeRelayPayload(faOn: Bool, ts: TimeInterval) -> String {
    "LIMERLY!v1;fa=\(faOn ? 1 : 0);ts=\(ts)"
}

func decodeRelayPayload(_ text: String) -> (proto: Int, faOn: Bool, ts: TimeInterval)? {
    let marker = "LIMERLY!v"
    guard let start = text.range(of: marker)?.lowerBound else { return nil }
    // Lenient: only the first 3 fields matter; anything after (e.g. a duplicate payload
    // typed on a re-appearance) is ignored, and ts is read up to its trailing junk.
    let fields = text[start...].split(separator: ";", omittingEmptySubsequences: false)
    guard fields.count >= 3,
          fields[0].hasPrefix(marker),
          let proto = Int(fields[0].dropFirst(marker.count)),
          fields[1].hasPrefix("fa="),
          let fa = Int(fields[1].dropFirst(3)),
          (fa == 0 || fa == 1),
          fields[2].hasPrefix("ts=")
    else { return nil }
    let tsBody = fields[2].dropFirst(3)
    let tsDigits = tsBody.prefix { $0.isNumber || $0 == "." || $0 == "-" }
    guard let ts = Double(tsDigits), ts.isFinite else { return nil }
    return (proto: proto, faOn: fa == 1, ts: ts)
}

func isRelayRequestContext(before: String?, after: String? = nil) -> Bool {
    // Check the full field content (before + after the cursor). SwiftUI sets the probe
    // token programmatically and can leave the cursor at position 0, so the token lands
    // entirely in the after-context. hasSuffix on the full content keeps the handshake
    // safe: a real field where a user typed the token then more text will not match.
    ((before ?? "") + (after ?? "")).hasSuffix(RelayToken.request)
}

extension Notification.Name {
    static let limeTriggerRelay = Notification.Name("org.limeime.triggerRelay")
    static let limeRelayPayloadReceived = Notification.Name("org.limeime.relayPayloadReceived")
    static let limeRelayResolvedNotActive = Notification.Name("org.limeime.relayResolvedNotActive")
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

enum SetupDetectionState: Equatable {
    case notEnabled
    case checkingActive
    case enabledNotActive
    case activeNoFullAccess
    case fullyEnabled
}

enum SetupDetection {
    static func state(keyboardEnabled: Bool,
                      activeThisSession: Bool,
                      probePending: Bool,
                      faConfirmedOn: Bool) -> SetupDetectionState {
        guard keyboardEnabled else { return .notEnabled }
        guard activeThisSession else {
            return probePending ? .checkingActive : .enabledNotActive
        }
        return faConfirmedOn ? .fullyEnabled : .activeNoFullAccess
    }

    static func forceKeyboardEnabled(arguments: [String] = ProcessInfo.processInfo.arguments) -> Bool {
        flagEnabled("-limeUITestForceKeyboardEnabled", arguments)
    }

    /// Forces the enabled-but-not-active rung on the sim (which otherwise can't sit
    /// there because its LIME keyboard keeps loading and pinging) so the activate guide
    /// is visually verifiable.
    static func forceNotActive(arguments: [String] = ProcessInfo.processInfo.arguments) -> Bool {
        flagEnabled("-limeUITestForceNotActive", arguments)
    }

    private static func flagEnabled(_ flag: String, _ arguments: [String]) -> Bool {
        #if DEBUG
        guard let index = arguments.firstIndex(of: flag),
              arguments.indices.contains(index + 1)
        else { return false }
        return arguments[index + 1] == "1"
        #else
        return false
        #endif
    }
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
    static let activeSessionWindow: TimeInterval = 3
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
                                    window: TimeInterval = activeSessionWindow) -> Bool {
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
