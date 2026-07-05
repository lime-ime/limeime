import Foundation
import GRDB
import Darwin

final class ColdPublisher {
    private let liveColdDatabaseURL: URL
    private let appGroupBaseURL: URL

    init(liveColdDatabaseURL: URL, appGroupBaseURL: URL) {
        self.liveColdDatabaseURL = liveColdDatabaseURL
        self.appGroupBaseURL = appGroupBaseURL
    }

    convenience init(locator: SyncDatabaseLocator = .production()) {
        self.init(liveColdDatabaseURL: locator.coldDatabaseURL,
                  appGroupBaseURL: locator.appGroupDirectory)
    }

    func publish() throws {
        _ = try SyncMetaStore(databaseURL: liveColdDatabaseURL).bumpGeneration()

        let fm = FileManager.default
        try fm.createDirectory(at: appGroupBaseURL, withIntermediateDirectories: true)
        let snapshotURL = SyncPaths.coldDB(appGroupBaseURL)
        let tempURL = appGroupBaseURL
            .appendingPathComponent(".cold.\(UUID().uuidString).limedb.tmp")

        let connection = try SyncDatabaseConnection(databaseURL: liveColdDatabaseURL)
        try connection.writeWithoutTransaction { db in
            try db.execute(sql: "VACUUM INTO ?", arguments: [tempURL.path])
        }

        try Self.renameReplacing(tempURL, with: snapshotURL)
        postSyncSignal(.tablesUpdated)
    }

    private static func renameReplacing(_ source: URL, with destination: URL) throws {
        guard rename(source.path, destination.path) == 0 else {
            throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
        }
    }
}
