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

import CoreGraphics

struct KeyModel: Equatable, Identifiable {
    var id: String {
        let codeID = codes.map(String.init).joined(separator: ",")
        return "\(frame.minX),\(frame.minY),\(frame.width),\(frame.height):\(codeID):\(primaryLabel):\(secondaryLabel)"
    }

    let frame: CGRect
    let codes: [Int]
    let primaryLabel: String
    let secondaryLabel: String
    let isRepeatable: Bool
    let isModifier: Bool
    let hasPopup: Bool
    let isDualRow: Bool
    let isSpace: Bool
}
