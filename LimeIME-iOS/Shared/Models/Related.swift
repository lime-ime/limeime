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

/// A related phrase pair: previous word → likely next word.
/// Mirrors Android's Related.java.
struct Related {
    var id: Int64
    var parentWord: String   // pword column
    var childWord: String    // cword column
    var score: Int
    var baseScore: Int

    // Convenience accessors matching Android getter style used in tests
    func getPword() -> String { parentWord }
    func getCword() -> String { childWord }
    func getUserscore() -> Int { score }
    func getBasescore() -> Int { baseScore }
    func getIdAsInt() -> Int { Int(id) }
}
