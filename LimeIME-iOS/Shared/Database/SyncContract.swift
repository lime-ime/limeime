import Foundation
import Combine

enum SyncPaths {
    static func coldDB(_ base: URL) -> URL {
        base.appendingPathComponent("cold.limedb")
    }

    static func inboxDir(_ base: URL) -> URL {
        base.appendingPathComponent("inbox", isDirectory: true)
    }

    static func imInbox(_ base: URL) -> URL {
        inboxDir(base).appendingPathComponent("im.json")
    }

    static func imLifecycleInbox(_ base: URL) -> URL {
        inboxDir(base).appendingPathComponent("lifecycle.json")
    }

    /// §1.8 two-writer hamburger prefs: the app→keyboard one-time channel. The app writes
    /// the changed field(s) here on a Preferences-tab change; the keyboard drains + clears
    /// it on appearance and applies it to its own (hot) store.
    static func prefInbox(_ base: URL) -> URL {
        inboxDir(base).appendingPathComponent("prefs.json")
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

    static func editorRefreshRequest(_ base: URL) -> URL {
        outboxDir(base).appendingPathComponent("editor.refresh.request.json")
    }

    static func editorRefreshReceipt(_ base: URL) -> URL {
        outboxDir(base).appendingPathComponent("editor.refresh.receipt.json")
    }

    static func heartbeat(_ base: URL) -> URL {
        outboxDir(base).appendingPathComponent("heartbeat.json")
    }
}

struct IMInboxFile: Codable, Equatable {
    var records: [IMInboxRecord]
}

struct IMInboxRecord: Codable, Equatable {
    enum Operation: String, Codable {
        case upsert
        case delete
    }

    var op: Operation
    var row: [String: String?]
    /// §1.5: monotonic app-side stamp. The keyboard applies a record only when
    /// `seq > last_consumed_im_seq` (its own-container cursor) and never deletes the App
    /// Group inbox FA-off; the app GCs consumed records via the relayed cursor. Optional in
    /// the wire format so a pre-seq record (written by an older app) still decodes — it
    /// reads as 0, i.e. already at/below any cursor, so it is left to the snapshot / prior
    /// drain rather than re-applied.
    var seq: Int = 0

    init(op: Operation, row: [String: String?], seq: Int = 0) {
        self.op = op
        self.row = row
        self.seq = seq
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        op = try c.decode(Operation.self, forKey: .op)
        row = try c.decode([String: String?].self, forKey: .row)
        seq = try c.decodeIfPresent(Int.self, forKey: .seq) ?? 0
    }
}

struct IMLifecycleRecord: Codable, Equatable {
    enum Action: String, Codable {
        case delete
        case install
    }

    var table: String
    var action: Action
    var preserveLearning: Bool
}

enum SyncSignal: String {
    case tablesUpdated = "org.limeime.tables.updated"
    case outboxUpdated = "org.limeime.outbox.updated"
    case importDone = "org.limeime.import.done"
    case importFailed = "org.limeime.import.failed"
    case faOn = "org.limeime.fa.on"
    case faOff = "org.limeime.fa.off"
    /// Keyboard → app: a cold→hot scan just finished (hot is synced). Name-only (Darwin
    /// carries no payload); the app uses it to dismiss the sync probe the instant the sync
    /// is done, instead of holding a fixed window. The seq for GC rides the relay, not this.
    case syncScanDone = "org.limeime.sync.scan.done"
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

struct RelayPrefState: Codable, Equatable {
    var hanConvert: Int
    var splitKeyboard: Int
    var updatedAt: TimeInterval
    // Last reverse-lookup change (per-IM). Optional so older stored JSON still decodes.
    var reverseLookupIM: String? = nil
    var reverseLookupValue: String? = nil
}

final class KeyboardRelayPrefStore {
    private let url: URL

    init(baseDirectory: URL? = nil) {
        let directory = baseDirectory ?? FileManager.default.urls(for: .applicationSupportDirectory,
                                                                  in: .userDomainMask)[0]
        self.url = directory.appendingPathComponent("relay-prefs.json")
    }

    func read() throws -> RelayPrefState? {
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        let data = try Data(contentsOf: url)
        return try JSONDecoder().decode(RelayPrefState.self, from: data)
    }

    func write(_ state: RelayPrefState) throws {
        let data = try JSONEncoder().encode(state)
        try atomicWrite(data, to: url)
    }

    @discardableResult
    func update(hanConvert: Int? = nil,
                splitKeyboard: Int? = nil,
                reverseLookupIM: String? = nil,
                reverseLookupValue: String? = nil,
                updatedAt: TimeInterval = Date().timeIntervalSince1970) throws -> RelayPrefState {
        let current = try read()
        let state = RelayPrefState(hanConvert: hanConvert ?? current?.hanConvert ?? 0,
                                   splitKeyboard: splitKeyboard ?? current?.splitKeyboard ?? 0,
                                   updatedAt: updatedAt,
                                   reverseLookupIM: reverseLookupIM ?? current?.reverseLookupIM,
                                   reverseLookupValue: reverseLookupValue ?? current?.reverseLookupValue)
        try write(state)
        return state
    }
}

/// §1.8 app→keyboard pref inbox payload (one-time). Only the changed field(s) are set;
/// `nil` means "unchanged". Reverse-lookup is per-IM (`tableNick → value`).
struct PrefInboxRecord: Codable, Equatable {
    /// Monotonic app-bumped consume marker. The keyboard applies a record only when
    /// `seq` exceeds its own last-consumed (kept in the keyboard's own container). This is
    /// what makes the inbox one-time FA-off, where the keyboard cannot delete the file.
    var seq: Int
    var hanConvert: Int?
    var splitKeyboard: Int?
    var reverseLookup: [String: String]?
    /// Active IM — only ever set by a wholesale restore (the restored backup's active IM).
    var activeIM: String?
}

/// §1.8 app→keyboard one-time pref channel for the three two-writer hamburger prefs
/// (漢字轉換 / 分離鍵盤 / 字根反查). The app writes; the keyboard reads (FA-off OK) and
/// applies once, guarded by `seq`. Distinct from the kb→app relay (`RelayPrefSync`).
enum PrefInbox {
    static let seqCounterKey = "pref_inbox_seq"

    /// App side: bump the App-Group seq counter and merge the changed field(s) into the
    /// inbox (later writes win per field; unread fields carry forward).
    static func write(base: URL,
                      defaults: UserDefaults,
                      hanConvert: Int? = nil,
                      splitKeyboard: Int? = nil,
                      reverseLookup: (im: String, value: String)? = nil,
                      activeIM: String? = nil) throws {
        let url = SyncPaths.prefInbox(base)
        let current = (try? Data(contentsOf: url)).flatMap {
            try? JSONDecoder().decode(PrefInboxRecord.self, from: $0)
        }
        let seq = defaults.integer(forKey: seqCounterKey) + 1
        defaults.set(seq, forKey: seqCounterKey)
        var mergedReverse = current?.reverseLookup ?? [:]
        if let reverseLookup { mergedReverse[reverseLookup.im] = reverseLookup.value }
        let record = PrefInboxRecord(
            seq: seq,
            hanConvert: hanConvert ?? current?.hanConvert,
            splitKeyboard: splitKeyboard ?? current?.splitKeyboard,
            reverseLookup: mergedReverse.isEmpty ? nil : mergedReverse,
            activeIM: activeIM ?? current?.activeIM)
        try FileManager.default.createDirectory(at: SyncPaths.inboxDir(base),
                                                withIntermediateDirectories: true)
        try atomicWrite(try JSONEncoder().encode(record), to: url)
    }

    /// Keyboard side: read WITHOUT deleting (the keyboard cannot write the App Group
    /// FA-off). Returns nil when empty. Consumption is gated by `seq` at the call site.
    static func read(base: URL) -> PrefInboxRecord? {
        let url = SyncPaths.prefInbox(base)
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(PrefInboxRecord.self, from: data)
    }

    /// Keyboard side: best-effort cleanup (succeeds FA-on; a no-op FA-off — the `seq`
    /// guard keeps a lingering file from being re-applied).
    static func clearBestEffort(base: URL) {
        try? FileManager.default.removeItem(at: SyncPaths.prefInbox(base))
    }
}

/// §1.5 `im` inbox — one-way cold→hot metadata channel, consumed FA-off-safe. The app
/// appends `seq`-stamped records (App→App Group is always writable) and GCs consumed ones;
/// the keyboard is a **pure reader** — it applies records above its own-container cursor and
/// never writes/deletes the App Group.
enum IMInbox {
    static let seqCounterKey = "im_inbox_seq"
    /// Keyboard's own-container consume cursor — the highest `seq` it has applied. Lives in
    /// `UserDefaults.standard` (NOT the hot DB / `sync_meta`), so it **survives a wholesale
    /// full-replace** (which copies cold's `sync_meta` over hot's) — the same reason §1.8's
    /// `active_im` lives there. Echoed on the relay so the app GCs at/below it.
    static let consumedSeqKey = "last_consumed_im_seq"

    /// App side: bump the App-Group seq counter, stamp each record, append to the inbox.
    static func append(_ records: [IMInboxRecord], base: URL, defaults: UserDefaults) throws {
        guard !records.isEmpty else { return }
        let url = SyncPaths.imInbox(base)
        var existing = (try? Data(contentsOf: url)).flatMap {
            try? JSONDecoder().decode(IMInboxFile.self, from: $0)
        }?.records ?? []
        var seq = defaults.integer(forKey: seqCounterKey)
        let stamped = records.map { rec -> IMInboxRecord in
            seq += 1
            return IMInboxRecord(op: rec.op, row: rec.row, seq: seq)
        }
        defaults.set(seq, forKey: seqCounterKey)
        existing.append(contentsOf: stamped)
        try FileManager.default.createDirectory(at: SyncPaths.inboxDir(base),
                                                withIntermediateDirectories: true)
        try atomicWrite(try JSONEncoder().encode(IMInboxFile(records: existing)), to: url)
    }

    /// Keyboard side: read WITHOUT deleting (read-only FA-off). Consumption is gated by the
    /// caller's `last_consumed_im_seq` cursor.
    static func read(base: URL) -> IMInboxFile? {
        guard let data = try? Data(contentsOf: SyncPaths.imInbox(base)) else { return nil }
        return try? JSONDecoder().decode(IMInboxFile.self, from: data)
    }

    /// App side: garbage-collect records the keyboard has already consumed (`seq <= cursor`,
    /// the keyboard's relayed cursor). App→App Group is writable, so this always works.
    static func gc(base: URL, throughSeq cursor: Int) {
        let url = SyncPaths.imInbox(base)
        guard let data = try? Data(contentsOf: url),
              let file = try? JSONDecoder().decode(IMInboxFile.self, from: data) else { return }
        let remaining = file.records.filter { $0.seq > cursor }
        if remaining.count == file.records.count { return }
        if remaining.isEmpty {
            try? FileManager.default.removeItem(at: url)
        } else if let encoded = try? JSONEncoder().encode(IMInboxFile(records: remaining)) {
            try? atomicWrite(encoded, to: url)
        }
    }
}

enum RelayPrefSync {
    static let hanConvertKey = "han_convert_option"
    static let splitKeyboardKey = "split_keyboard_mode"
    static let appliedAtKey = "relay_pref_applied_at"

    /// Reverse-lookup is stored per-IM under `<IM>_im_reverselookup` (see
    /// LIMEPreferenceManager.reverseLookupKey). Keep this in sync with that format.
    static func reverseLookupKey(for im: String) -> String { "\(im)_im_reverselookup" }

    static func apply(han: Int?,
                      split: Int?,
                      reverseLookupIM: String? = nil,
                      reverseLookupValue: String? = nil,
                      pts: TimeInterval?,
                      to defaults: UserDefaults) -> Bool {
        guard let pts, pts > defaults.double(forKey: appliedAtKey) else { return false }
        let validHan = han.flatMap { (0...2).contains($0) ? $0 : nil }
        let validSplit = split.flatMap { (0...2).contains($0) ? $0 : nil }
        let rlIM = reverseLookupIM.flatMap { $0.isEmpty ? nil : $0 }
        let rlVal = reverseLookupValue.flatMap { $0.isEmpty ? nil : $0 }
        let hasReverseLookup = rlIM != nil && rlVal != nil
        guard validHan != nil || validSplit != nil || hasReverseLookup else { return false }

        if let validHan {
            defaults.set(validHan, forKey: hanConvertKey)
        }
        if let validSplit {
            defaults.set(validSplit, forKey: splitKeyboardKey)
        }
        if let rlIM, let rlVal {
            defaults.set(rlVal, forKey: reverseLookupKey(for: rlIM))
        }
        defaults.set(pts, forKey: appliedAtKey)
        return true
    }

    static func prepareRelayOnlyIfNeeded(in defaults: UserDefaults,
                                         arguments: [String] = ProcessInfo.processInfo.arguments) {
        guard forceRelayOnly(arguments: arguments) else { return }
        defaults.removeObject(forKey: hanConvertKey)
        defaults.removeObject(forKey: splitKeyboardKey)
        defaults.removeObject(forKey: appliedAtKey)
    }

    static func forceRelayOnly(arguments: [String] = ProcessInfo.processInfo.arguments) -> Bool {
        #if DEBUG
        guard let index = arguments.firstIndex(of: "-limeUITestForceRelayOnly"),
              arguments.indices.contains(index + 1)
        else { return false }
        return arguments[index + 1] == "1"
        #else
        return false
        #endif
    }
}

func encodeRelayPayload(faOn: Bool, ts: TimeInterval, imSeq: Int = 0, prefs: RelayPrefState? = nil) -> String {
    // imseq = the keyboard's last_consumed_im_seq cursor (§1.5); the app GCs im-inbox
    // records at/below it. Always present so the app has a cursor to trim by.
    var payload = "LIMERLY!v1;fa=\(faOn ? 1 : 0);ts=\(ts);imseq=\(imSeq)"
    if let prefs {
        payload += ";han=\(prefs.hanConvert);split=\(prefs.splitKeyboard);pts=\(prefs.updatedAt)"
        if let im = prefs.reverseLookupIM, let val = prefs.reverseLookupValue,
           !im.isEmpty, !val.isEmpty {
            payload += ";rlim=\(im);rlval=\(val)"
        }
    }
    return payload
}

func decodeRelayPayload(_ text: String) -> (proto: Int, faOn: Bool, ts: TimeInterval, han: Int?, split: Int?, pts: TimeInterval?, rlim: String?, rlval: String?, imseq: Int?)? {
    let marker = "LIMERLY!v"
    guard let start = text.range(of: marker)?.lowerBound else { return nil }
    // Lenient: the original fa/ts fields remain mandatory; optional pref fields are
    // read when present, and numeric values tolerate trailing duplicate-payload junk.
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

    var han: Int?
    var split: Int?
    var pts: TimeInterval?
    var rlim: String?
    var rlval: String?
    var imseq: Int?
    // Reverse-lookup IM/value are alphanumeric strings; truncate any concatenated
    // duplicate payload (defensive — the single-probe capture prevents duplicates).
    func stripJunk(_ s: Substring) -> String {
        var out = String(s)
        if let r = out.range(of: "LIMERLY!") { out = String(out[..<r.lowerBound]) }
        return out
    }
    for field in fields.dropFirst(3) {
        let pair = field.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
        guard pair.count == 2 else { continue }
        let value = pair[1]
        switch pair[0] {
        case "han":
            let digits = value.prefix { $0.isNumber || $0 == "-" }
            han = Int(digits)
        case "split":
            let digits = value.prefix { $0.isNumber || $0 == "-" }
            split = Int(digits)
        case "pts":
            let digits = value.prefix { $0.isNumber || $0 == "." || $0 == "-" }
            if let parsed = Double(digits), parsed.isFinite {
                pts = parsed
            }
        case "rlim":
            let v = stripJunk(value); if !v.isEmpty { rlim = v }
        case "rlval":
            let v = stripJunk(value); if !v.isEmpty { rlval = v }
        case "imseq":
            let digits = value.prefix { $0.isNumber || $0 == "-" }
            imseq = Int(digits)
        default:
            continue
        }
    }
    return (proto: proto, faOn: fa == 1, ts: ts, han: han, split: split, pts: pts, rlim: rlim, rlval: rlval, imseq: imseq)
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

struct EditorRefreshRequest: Codable, Equatable {
    var requestUUID: String
    var table: String
    var expiresAt: TimeInterval
}

struct EditorRefreshReceipt: Codable, Equatable {
    enum Status: String, Codable {
        case done
        case failed
    }

    var requestUUID: String
    var table: String
    var status: Status
    var error: String?
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

enum FullAccessBannerState: Equatable {
    case notEnabled
    case enabledNoFullAccess
    case activeNoFullAccess
    case fullyEnabled
}

enum ActiveKeyboardBannerState: Equatable {
    case hidden
    case checking
    case notActive
    case active
}

enum ActiveKeyboardProbeMode: String {
    case automatic
    case manualSwitch

    static let notificationKey = "mode"

    init(notificationUserInfo: [AnyHashable: Any]?) {
        if let raw = notificationUserInfo?[Self.notificationKey] as? String,
           let mode = ActiveKeyboardProbeMode(rawValue: raw) {
            self = mode
        } else {
            self = .automatic
        }
    }

    var timeout: TimeInterval {
        switch self {
        case .automatic: return FAStateResolver.automaticActiveSessionWindow
        case .manualSwitch: return FAStateResolver.manualSwitchActiveSessionWindow
        }
    }

    var notificationUserInfo: [String: String] {
        [Self.notificationKey: rawValue]
    }
}

enum SetupDetection {
    static func keyboardEnabled(appleKeyboards: [String],
                                forceEnabled: Bool = false) -> Bool {
        appleKeyboards.contains { $0.hasPrefix("org.limeime") } || forceEnabled
    }

    static func fullAccessBannerState(keyboardEnabled: Bool,
                                      faConfirmedOn: Bool,
                                      activeThisSession: Bool = false) -> FullAccessBannerState {
        guard keyboardEnabled else { return .notEnabled }
        if faConfirmedOn { return .fullyEnabled }
        return activeThisSession ? .activeNoFullAccess : .enabledNoFullAccess
    }

    static func activeKeyboardBannerState(keyboardEnabled: Bool,
                                          activeThisSession: Bool,
                                          probePending: Bool) -> ActiveKeyboardBannerState {
        guard keyboardEnabled else { return .hidden }
        if probePending { return .checking }
        return activeThisSession ? .active : .notActive
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

    /// Fail-safe: `activeThisSession` defaults to **false** so any caller that forgets to
    /// pass live active-keyboard proof gets `.readOnly`, never `.live`. Editing is a Full
    /// Access + active-keyboard trust boundary — it must never open on a stale heartbeat.
    static func resolve(faState: FAState,
                        activeThisSession: Bool = false,
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

/// Single source of truth for "is LIME the active keyboard this foreground session, and does it
/// have Full Access" -- populated by the root relay in LimeSettingsView. nil = not yet determined.
final class RelayActiveState: ObservableObject {
    @Published var isActive: Bool? = nil
    @Published var hasFullAccess: Bool? = nil

    /// Fail-safe: live only when BOTH are positively true; anything else (nil/false) is read-only.
    var editingCapability: RecordEditingCapability {
        if RecordEditingCapability.forceLiveEditingEnabled() { return .live }
        return (isActive == true && hasFullAccess == true) ? .live : .readOnly
    }

    func markActive(fullAccess: Bool) {
        isActive = true
        hasFullAccess = fullAccess
    }

    func markNotActive(fullAccess: Bool = false) {
        isActive = false
        hasFullAccess = fullAccess
    }
}

enum FAStateResolver {
    // ponytail: fixed freshness window from the FA detection spec; make configurable only if device polling changes.
    static let heartbeatFreshness: TimeInterval = 120
    static let automaticActiveSessionWindow: TimeInterval = 1.5
    static let manualSwitchActiveSessionWindow: TimeInterval = 10
    static let activeSessionWindow: TimeInterval = automaticActiveSessionWindow
    // ponytail: fixed probe windows from setup spec; make configurable only if device timing proves slower.
    static let activeProbeWaitNanoseconds: UInt64 = 1_500_000_000
    static let manualSwitchWaitNanoseconds: UInt64 = 10_000_000_000

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

    static func isActiveThisSession(faPingAt: TimeInterval?,
                                    probeFiredAt: TimeInterval?,
                                    mode: ActiveKeyboardProbeMode) -> Bool {
        isActiveThisSession(faPingAt: faPingAt,
                            probeFiredAt: probeFiredAt,
                            window: mode.timeout)
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

final class SyncSignalObserver {
    private let signal: SyncSignal
    private let onSignal: () -> Void
    private var observer: UnsafeRawPointer?

    init(signal: SyncSignal, onSignal: @escaping () -> Void) {
        self.signal = signal
        self.onSignal = onSignal
        self.observer = UnsafeRawPointer(Unmanaged.passUnretained(self).toOpaque())
        addObserver()
    }

    deinit {
        guard let observer else { return }
        CFNotificationCenterRemoveObserver(CFNotificationCenterGetDarwinNotifyCenter(),
                                           observer,
                                           CFNotificationName(signal.rawValue as CFString),
                                           nil)
    }

    private func addObserver() {
        guard let observer else { return }
        CFNotificationCenterAddObserver(
            CFNotificationCenterGetDarwinNotifyCenter(),
            observer,
            { _, observer, _, _, _ in
                guard let observer else { return }
                let monitor = Unmanaged<SyncSignalObserver>
                    .fromOpaque(observer)
                    .takeUnretainedValue()
                DispatchQueue.main.async {
                    monitor.onSignal()
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
