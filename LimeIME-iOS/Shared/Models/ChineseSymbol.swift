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

// Standard Chinese punctuation list — spec §11.
// NOTE: The canonical implementation lives in KeyboardViewController.chinesePunctuationMappings()
// (inlined to avoid project-file dependency on this file before xcodegen regeneration).
// This file provides the same list for use outside the keyboard extension.

enum ChineseSymbol {

    static let standardSet: [String] = [
        "，", "。", "、", "；", "：", "？", "！",
        "「", "」", "『", "』", "【", "】", "〔", "〕",
        "（", "）", "《", "》", "〈", "〉",
        "…", "——", "～", "·", "※",
        "\u{201C}", "\u{201D}",
        "\u{2018}", "\u{2019}",
    ]
}
