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

import UIKit

/// Single source of truth for "this host field opens in English".
/// `KeyboardViewController.updateInputModeForCurrentField()` is the only production
/// caller — do not reintroduce a parallel switch there (#226).
enum KeyboardTypePolicy {
    /// Field types that open in English. Mirrors Android
    /// `LIMEService.isForcedEnglishTextVariation()`.
    static func isForcedEnglishKeyboardType(_ keyboardType: UIKeyboardType) -> Bool {
        switch keyboardType {
        case .numberPad, .decimalPad, .asciiCapableNumberPad, .phonePad, .emailAddress:
            return true
        default:
            return false
        }
    }

    /// English-*first*, not English-*only* (#226). The host hint picks the initial mode,
    /// but the `中` key must still reach the Chinese IM — every English layout carries it,
    /// matching Android's `MODE_EMAIL` row. Once the user switches to Chinese inside a
    /// field, re-entering that same field must not snap them back: Outlook re-inits its
    /// recipient field after every committed chip, which would otherwise force English
    /// again on each name the user types.
    static func forcesEnglish(keyboardType: UIKeyboardType,
                              userSwitchedToChineseInField: Bool) -> Bool {
        isForcedEnglishKeyboardType(keyboardType) && !userSwitchedToChineseInField
    }
}
