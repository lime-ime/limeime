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

// ShareManager.swift
// LimeIME-iOS
//
// Prepares export URLs for the share sheet.
// Mirrors Android ShareManager.

import Foundation

// MARK: - ShareManager

final class ShareManager {

    private let dbServer: DBServer

    init(dbServer: DBServer = .shared) {
        self.dbServer = dbServer
    }

    // MARK: - Export entire DB

    /// Exports the full lime.db to a temp file and returns its URL for sharing.
    func exportDB() throws -> URL {
        let dest = FileManager.default.temporaryDirectory
            .appendingPathComponent("lime_backup_\(Int(Date().timeIntervalSince1970)).db")
        try dbServer.exportDB(to: dest.path)
        return dest
    }

    /// Exports a single mapping table to a temp .limedb file for sharing.
    func exportTable(tableName: String) throws -> URL {
        let dest = FileManager.default.temporaryDirectory
            .appendingPathComponent("\(tableName)_export_\(Int(Date().timeIntervalSince1970)).limedb")
        try dbServer.exportDB(to: dest.path)
        return dest
    }

    /// Exports the related table to a temp file for sharing.
    func exportRelated() throws -> URL {
        return try exportTable(tableName: "related")
    }
}
