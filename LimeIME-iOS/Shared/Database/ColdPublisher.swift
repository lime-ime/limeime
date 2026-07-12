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
