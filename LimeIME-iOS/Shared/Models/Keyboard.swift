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

/// Keyboard configuration record from the `keyboard` table in lime.db.
/// Mirrors Android's Keyboard.java data class.
struct KeyboardConfig {
    let id: Int64
    let code: String           // Internal code (e.g., "phonetic", "dayi")
    let name: String           // Display name (Chinese)
    let desc: String           // Description
    let type: String           // Layout type (e.g., "phone")
    let image: String          // Preview image resource name
    let imkb: String           // Portrait IM keyboard layout id
    let imshiftkb: String      // Portrait IM shift keyboard layout id
    let engkb: String          // English keyboard layout id
    let engshiftkb: String     // English shift keyboard layout id
    let symbolkb: String       // Symbol keyboard layout id (e.g. lime_dayi_sym)
    let symbolshiftkb: String  // Symbol shift keyboard layout id
    let isDisabled: Bool       // Whether this keyboard entry is hidden
}
