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

/// A single mapping table record.
/// Mirrors Android's Record.java data class.
/// Named LimeRecord to avoid conflict with GRDB.Record.
struct LimeRecord {
    var id: String = ""
    var code: String = ""
    var word: String = ""
    var score: Int = 0
    var baseScore: Int = 0
    var code3r: String = ""

    // MARK: - Convenience accessors (match Android getter style used in tests)
    func getWord() -> String { word }
    func getCode() -> String { code }
    func getScore() -> Int { score }
    func getBasescore() -> Int { baseScore }
    func getCode3r() -> String { code3r }
}

