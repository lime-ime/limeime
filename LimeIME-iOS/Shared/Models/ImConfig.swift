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

/// An input method configuration row from the `im` table in lime.db.
/// Mirrors Android's ImConfig.java.
struct ImConfig: Codable {
    let id: Int64
    let imName: String
    let tableNick: String
    let label: String
    /// Full name from the `title="name"` config entry (mirrors Android LIME.IM_FULL_NAME / sidebar desc).
    let fullName: String
    let keyboardId: String
    let keyboardLandscapeId: String
    var enabled: Bool
    var sortOrder: Int
}
