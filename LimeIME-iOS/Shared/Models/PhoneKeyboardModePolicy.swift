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

// Issue #169: shared phone keyboard geometry semantics for migration and
// rendering. This is a byte-for-byte semantic mirror of the Android
// `org.limeime.keyboard.PhoneKeyboardModePolicy` so iPhone and Android phone
// behaviour and stored key values stay aligned. Pure logic (no UIKit) so it is
// linkable from the keyboard extension, the settings app, and the unit tests.
//
// Canonical shared keys/values:
//   phone_portrait_keyboard_mode: 0 standard, 1 split, 2 one-hand left, 3 one-hand right
//   phone_landscape_split:        boolean

/// Integrated phone portrait keyboard mode. Mutually exclusive: split and
/// one-hand both own the same horizontal key geometry.
enum PhoneKeyboardPortraitMode: Int {
    case standard = 0
    case split = 1
    case oneHandLeft = 2
    case oneHandRight = 3
}

enum PhoneKeyboardModePolicy {

    /// Migrate the legacy `one_hand_mode` (0 off / 1 left / 2 right) +
    /// `split_keyboard_mode` (0 off / 1 always / 2 landscape-only) pair into the
    /// integrated portrait mode. Deterministic precedence: explicit one-hand wins,
    /// then legacy "always" split, else standard.
    ///
    /// `legacyPhoneSplitSupported` is true on Android (legacy phone portrait split
    /// existed) and false on iOS (iPhone split was never shipped, so migration must
    /// not invent a legacy iPhone split).
    static func migratePortraitMode(legacyOneHand: Int,
                                    legacySplit: Int,
                                    legacyPhoneSplitSupported: Bool) -> PhoneKeyboardPortraitMode {
        if legacyOneHand == 1 { return .oneHandLeft }
        if legacyOneHand == 2 { return .oneHandRight }
        if legacyPhoneSplitSupported && legacySplit == 1 { return .split }
        return .standard
    }

    /// Migrate the legacy split value into the separate phone landscape split
    /// boolean. Android derives it from any nonzero legacy split; iOS defaults it
    /// off because iPhone split did not previously exist.
    static func migrateLandscapeSplit(legacySplit: Int,
                                      legacyPhoneSplitSupported: Bool) -> Bool {
        return legacyPhoneSplitSupported && legacySplit != 0
    }

    /// Whether split rendering is active for a phone keyboard instance. Landscape
    /// reads only `phone_landscape_split`; portrait reads only the integrated mode.
    /// Numpad-based layouts (`splitEligible == false`) never split.
    static func splitActive(isLandscape: Bool,
                            splitEligible: Bool,
                            portraitMode: PhoneKeyboardPortraitMode,
                            landscapeSplit: Bool) -> Bool {
        if !splitEligible { return false }
        return isLandscape ? landscapeSplit : (portraitMode == .split)
    }

    /// Portrait one-hand anchor: 0 none, 1 left, 2 right. Landscape never anchors.
    static func oneHandAnchor(isLandscape: Bool,
                              portraitMode: PhoneKeyboardPortraitMode) -> Int {
        if isLandscape { return 0 }
        switch portraitMode {
        case .oneHandLeft: return 1
        case .oneHandRight: return 2
        default: return 0
        }
    }

    /// Issue #169: the integrated phone controls apply to EVERY iPhone and NEVER
    /// to iPad — there is no screen-width or physical-size gate. iPad uses the
    /// split/numpad_anchor model instead.
    static func phoneControlsApply(isPad: Bool) -> Bool {
        return !isPad
    }
}
