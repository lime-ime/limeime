import UIKit

// Keyboard layout data model.
// Phase 2: phonetic and English layouts are hardcoded here.
// Phase 3: JSON loader extends this with all 54+ Android layouts.

// MARK: - Special key codes (mirror Android KEYCODE_ values)
enum LimeKeyCode: Int {
    case enter          = 10
    case space          = 32
    case delete         = -5
    case shift          = -1
    case done           = -3   // iOS: dismiss keyboard / globe
    case switchToSymbol = -2
    case switchToEnglish = -9
    case switchToIM     = -10
    case globe          = -200 // iOS-only: advanceToNextInputMode()
    case emojiPanel     = -201 // iOS/Android: open emoji keyboard panel
    case emojiABC       = -202 // iOS/Android: return from emoji panel to English keyboard
    case emojiCategoryRecent  = -203
    case emojiCategorySmileys = -204
    case emojiCategoryPeople  = -205
    case emojiCategoryAnimals = -206
    case emojiCategoryFood    = -207
    case emojiCategoryTravel  = -208
    case emojiCategoryActivities = -209
    case emojiCategoryObjects = -210
    case emojiCategorySymbols = -211
    case emojiCategoryFlags   = -212
    case voiceInput           = -220 // Deprecated on iOS: third-party keyboards cannot launch dictation
    case nextIM              = -20  // cycle to next activated IM (spec §10)
    case prevIM              = -21  // cycle to previous activated IM (spec §10)
    case switchSymbolKeyboard = -15  // cycle symbol keyboard pages (spec §10, Android code -15)
    case keyboardOptionsMenu = -100 // long-press keyboard key → show options popup menu (spec §10)
    case arrowLeft  = -30
    case arrowRight = -31
    case arrowUp    = -32
    case arrowDown  = -33
}

enum EmojiPanelSource {
    case english
    case chineseIM

    var returnKeyTitle: String {
        switch self {
        case .english: return "ABC"
        case .chineseIM: return "中"
        }
    }

    static func source(isEnglishOnly: Bool) -> EmojiPanelSource {
        isEnglishOnly ? .english : .chineseIM
    }
}

enum CandidateBarSystemChrome {
    static func usesLightForeground(systemUserInterfaceStyle: UIUserInterfaceStyle) -> Bool {
        systemUserInterfaceStyle == .dark
    }

    static func labelColor(systemUserInterfaceStyle: UIUserInterfaceStyle) -> UIColor {
        let style: UIUserInterfaceStyle = usesLightForeground(systemUserInterfaceStyle: systemUserInterfaceStyle)
            ? .dark
            : .light
        return UIColor.label.resolvedColor(with: UITraitCollection(userInterfaceStyle: style))
    }
}

struct EmojiPanelPaginationResult {
    let pages: [[Mapping]]
    let categoryStartDisplayPageIndexes: [Int]
    let sourcePageIndexes: [Int]
    let columnCounts: [Int]
}

enum EmojiPanelPaginator {
    static func displayPages(sourcePages: [[Mapping]],
                             cellsPerPage: Int,
                             rowsPerPage: Int,
                             categoryButtonCount: Int) -> EmojiPanelPaginationResult {
        let safeCellsPerPage = max(cellsPerPage, 1)
        let safeRowsPerPage = max(rowsPerPage, 1)
        var displayPages: [[Mapping]] = []
        var sourceIndexes: [Int] = []
        var columnCounts: [Int] = []
        var starts = Array(repeating: 0, count: max(categoryButtonCount, sourcePages.count + 1))
        for (sourceIndex, sourcePage) in sourcePages.enumerated() {
            let buttonTag = sourceIndex + 1
            if buttonTag < starts.count {
                starts[buttonTag] = displayPages.count
            }
            displayPages.append(sourcePage)
            sourceIndexes.append(sourceIndex)
            let minimumCells = sourceIndex == 0 ? safeCellsPerPage : 1
            let cells = max(sourcePage.count, minimumCells)
            columnCounts.append(max(1, Int(ceil(Double(cells) / Double(safeRowsPerPage)))))
        }
        if displayPages.isEmpty {
            displayPages.append([])
            sourceIndexes.append(0)
            columnCounts.append(max(1, Int(ceil(Double(safeCellsPerPage) / Double(safeRowsPerPage)))))
        }
        return EmojiPanelPaginationResult(pages: displayPages,
                                          categoryStartDisplayPageIndexes: starts,
                                          sourcePageIndexes: sourceIndexes,
                                          columnCounts: columnCounts)
    }
}

enum EmojiRecentSeedQueue {
    static func merged(recent: [Mapping],
                       fallback: [Mapping],
                       limit: Int) -> [Mapping] {
        let safeLimit = max(limit, 1)
        var seen = Set<String>()
        var output: [Mapping] = []
        for mapping in recent + fallback {
            guard !mapping.word.isEmpty, seen.insert(mapping.word).inserted else { continue }
            output.append(mapping)
            if output.count >= safeLimit {
                break
            }
        }
        return output
    }
}

enum EmojiPanelScrollLayout {
    static func contentFrame(viewportWidth: CGFloat,
                             contentWidth: CGFloat,
                             contentHeight: CGFloat) -> CGRect {
        CGRect(x: 0,
               y: 0,
               width: max(viewportWidth, contentWidth),
               height: contentHeight)
    }

    static func cellX(pageOffsetX: CGFloat,
                      column: Int,
                      cellWidth: CGFloat,
                      horizontalInset: CGFloat) -> CGFloat {
        pageOffsetX + CGFloat(column) * cellWidth + horizontalInset
    }

    static func unitOffsets(columnCounts: [Int],
                            cellWidth: CGFloat) -> [CGFloat] {
        var offsets: [CGFloat] = []
        var nextOffset: CGFloat = 0
        for columnCount in columnCounts {
            offsets.append(nextOffset)
            nextOffset += CGFloat(max(columnCount, 1)) * cellWidth
        }
        return offsets.isEmpty ? [0] : offsets
    }

    static func contentWidth(unitOffsets: [CGFloat],
                             columnCounts: [Int],
                             cellWidth: CGFloat,
                             horizontalInset: CGFloat,
                             viewportWidth: CGFloat) -> CGFloat {
        guard let lastOffset = unitOffsets.last,
              let lastColumnCount = columnCounts.last else {
            return max(viewportWidth, 1)
        }
        let compactWidth = lastOffset + CGFloat(max(lastColumnCount, 1)) * cellWidth + horizontalInset * 2
        return max(viewportWidth, compactWidth)
    }

    static func cellPosition(index: Int, rows: Int) -> (column: Int, row: Int) {
        let safeRows = max(rows, 1)
        return (index / safeRows, index % safeRows)
    }
}

// MARK: - Key definition
struct KeyDef {
    let code: Int            // Key code. Positive = Unicode codepoint; negative = special
    let codes: [Int]         // All codes for multi-tap cycling (mirrors Android Key.codes[])
    let label: String        // Primary label (top half of key)
    let sublabel: String     // Secondary label (bottom half, e.g. BPMF character)
    let widthPercent: CGFloat // Key width as percentage of row width (default 10)
    let icon: String         // SF Symbol name for icon keys (empty = use labels)
    let isRepeatable: Bool
    let isModifier: Bool
    let isSticky: Bool       // Stays pressed (e.g., shift lock)
    let longPressCode: Int    // 0 = none; -100 = keyboard options menu; other = special action
    let popupKeyboard: String // e.g. "popup_template", "popup_punctuation"; "" = none
    let popupCharacters: String // characters for popup_template keys (e.g. "àáâãäåæ"); "" = none

    init(
        code: Int,
        codes: [Int] = [],
        label: String = "",
        sublabel: String = "",
        widthPercent: CGFloat = 10,
        icon: String = "",
        isRepeatable: Bool = false,
        isModifier: Bool = false,
        isSticky: Bool = false,
        longPressCode: Int = 0,
        popupKeyboard: String = "",
        popupCharacters: String = ""
    ) {
        self.code = code
        self.codes = codes.isEmpty ? [code] : codes
        self.label = label
        self.sublabel = sublabel
        self.widthPercent = widthPercent
        self.icon = icon
        self.isRepeatable = isRepeatable
        self.isModifier = isModifier
        self.isSticky = isSticky
        self.longPressCode = longPressCode
        self.popupKeyboard = popupKeyboard
        self.popupCharacters = popupCharacters
    }

    /// Returns the string character to append to the composing code.
    /// Returns nil for special/modifier keys.
    var codeCharacter: String? {
        guard code > 0, let scalar = Unicode.Scalar(code) else { return nil }
        return String(scalar)
    }

    var isSpecialKey: Bool { code < 0 }
}

// MARK: - Key row
struct KeyRow {
    let keys: [KeyDef]
    let isBottomRow: Bool

    init(keys: [KeyDef], isBottomRow: Bool = false) {
        self.keys = keys
        self.isBottomRow = isBottomRow
    }
}

// MARK: - Keyboard layout
struct LimeKeyLayout {
    let id: String
    let rows: [KeyRow]

    // MARK: Phonetic (注音) layout — mirrors lime_phonetic.xml
    static let phonetic = LimeKeyLayout(id: "lime_phonetic", rows: [
        KeyRow(keys: [
            KeyDef(code: 49, label: "1", sublabel: "ㄅ"),
            KeyDef(code: 50, label: "2", sublabel: "ㄉ"),
            KeyDef(code: 51, label: "3", sublabel: "ˇ"),
            KeyDef(code: 52, label: "4", sublabel: "ˋ"),
            KeyDef(code: 53, label: "5", sublabel: "ㄓ"),
            KeyDef(code: 54, label: "6", sublabel: "ˊ"),
            KeyDef(code: 55, label: "7", sublabel: "˙"),
            KeyDef(code: 56, label: "8", sublabel: "ㄚ"),
            KeyDef(code: 57, label: "9", sublabel: "ㄞ"),
            KeyDef(code: 48, label: "0", sublabel: "ㄢ"),
        ]),
        KeyRow(keys: [
            KeyDef(code: 113, label: "q", sublabel: "ㄆ"),
            KeyDef(code: 119, label: "w", sublabel: "ㄊ"),
            KeyDef(code: 101, label: "e", sublabel: "ㄍ"),
            KeyDef(code: 114, label: "r", sublabel: "ㄐ"),
            KeyDef(code: 116, label: "t", sublabel: "ㄔ"),
            KeyDef(code: 121, label: "y", sublabel: "ㄗ"),
            KeyDef(code: 117, label: "u", sublabel: "ㄧ"),
            KeyDef(code: 105, label: "i", sublabel: "ㄛ"),
            KeyDef(code: 111, label: "o", sublabel: "ㄟ"),
            KeyDef(code: 112, label: "p", sublabel: "ㄣ"),
        ]),
        KeyRow(keys: [
            KeyDef(code: 97,  label: "a", sublabel: "ㄇ"),
            KeyDef(code: 115, label: "s", sublabel: "ㄋ"),
            KeyDef(code: 100, label: "d", sublabel: "ㄎ"),
            KeyDef(code: 102, label: "f", sublabel: "ㄑ"),
            KeyDef(code: 103, label: "g", sublabel: "ㄕ"),
            KeyDef(code: 104, label: "h", sublabel: "ㄘ"),
            KeyDef(code: 106, label: "j", sublabel: "ㄨ"),
            KeyDef(code: 107, label: "k", sublabel: "ㄜ"),
            KeyDef(code: 108, label: "l", sublabel: "ㄠ"),
            KeyDef(code: 59,  label: ";", sublabel: "ㄤ"),
        ]),
        KeyRow(keys: [
            KeyDef(code: 122, label: "z", sublabel: "ㄈ"),
            KeyDef(code: 120, label: "x", sublabel: "ㄌ"),
            KeyDef(code: 99,  label: "c", sublabel: "ㄏ"),
            KeyDef(code: 118, label: "v", sublabel: "ㄒ"),
            KeyDef(code: 98,  label: "b", sublabel: "ㄖ"),
            KeyDef(code: 110, label: "n", sublabel: "ㄙ"),
            KeyDef(code: 109, label: "m", sublabel: "ㄩ"),
            KeyDef(code: 44,  label: ",", sublabel: "ㄝ"),
            KeyDef(code: 46,  label: ".", sublabel: "ㄡ"),
            KeyDef(code: 47,  label: "/", sublabel: "ㄥ"),
        ]),
        KeyRow(keys: [
            KeyDef(code: LimeKeyCode.done.rawValue,            widthPercent: 14, icon: "keyboard.chevron.compact.down", isRepeatable: false, isModifier: true, longPressCode: LimeKeyCode.keyboardOptionsMenu.rawValue),
            KeyDef(code: LimeKeyCode.shift.rawValue,           widthPercent: 14, icon: "shift",           isRepeatable: false, isModifier: true, isSticky: true),
            KeyDef(code: LimeKeyCode.switchToEnglish.rawValue, label: "ABC",    widthPercent: 14,                              isModifier: true),
            KeyDef(code: 32,                                   widthPercent: 24, icon: "space.bar"),
            KeyDef(code: 45,  label: "-", sublabel: "ㄦ",     widthPercent: 6),
            KeyDef(code: LimeKeyCode.delete.rawValue,          widthPercent: 14, icon: "delete.backward", isRepeatable: true,  isModifier: true),
            KeyDef(code: LimeKeyCode.enter.rawValue,           widthPercent: 14, icon: "return",          isRepeatable: false, isModifier: true),
        ], isBottomRow: true),
    ])

    // MARK: English (ABC) layout — mirrors lime_abc.xml
    static let english = LimeKeyLayout(id: "lime_abc", rows: [
        KeyRow(keys: [
            KeyDef(code: 49, label: "1"), KeyDef(code: 50, label: "2"),
            KeyDef(code: 51, label: "3"), KeyDef(code: 52, label: "4"),
            KeyDef(code: 53, label: "5"), KeyDef(code: 54, label: "6"),
            KeyDef(code: 55, label: "7"), KeyDef(code: 56, label: "8"),
            KeyDef(code: 57, label: "9"), KeyDef(code: 48, label: "0"),
        ]),
        KeyRow(keys: [
            KeyDef(code: 97,  label: "a"), KeyDef(code: 98,  label: "b"),
            KeyDef(code: 99,  label: "c"), KeyDef(code: 100, label: "d"),
            KeyDef(code: 101, label: "e"), KeyDef(code: 102, label: "f"),
            KeyDef(code: 103, label: "g"), KeyDef(code: 104, label: "h"),
            KeyDef(code: 105, label: "i"), KeyDef(code: 106, label: "j"),
        ]),
        KeyRow(keys: [
            KeyDef(code: 107, label: "k"), KeyDef(code: 108, label: "l"),
            KeyDef(code: 109, label: "m"), KeyDef(code: 110, label: "n"),
            KeyDef(code: 111, label: "o"), KeyDef(code: 112, label: "p"),
            KeyDef(code: 113, label: "q"), KeyDef(code: 114, label: "r"),
            KeyDef(code: 115, label: "s"),
        ]),
        KeyRow(keys: [
            KeyDef(code: LimeKeyCode.shift.rawValue,  widthPercent: 15, icon: "shift",           isRepeatable: false, isModifier: true, isSticky: true),
            KeyDef(code: 116, label: "t"), KeyDef(code: 117, label: "u"),
            KeyDef(code: 118, label: "v"), KeyDef(code: 119, label: "w"),
            KeyDef(code: 120, label: "x"), KeyDef(code: 121, label: "y"),
            KeyDef(code: 122, label: "z"),
            KeyDef(code: LimeKeyCode.delete.rawValue, widthPercent: 15, icon: "delete.backward", isRepeatable: true,  isModifier: true),
        ]),
        KeyRow(keys: [
            KeyDef(code: LimeKeyCode.done.rawValue,           widthPercent: 15, icon: "keyboard.chevron.compact.down", isRepeatable: false, isModifier: true, longPressCode: LimeKeyCode.keyboardOptionsMenu.rawValue),
            KeyDef(code: LimeKeyCode.switchToIM.rawValue,     label: "中文", widthPercent: 10,                         isModifier: true),
            KeyDef(code: 44,  label: ",",                     widthPercent: 10),
            KeyDef(code: 32,                                  widthPercent: 30, icon: "space.bar"),
            KeyDef(code: 46,  label: ".",                     widthPercent: 10),
            KeyDef(code: LimeKeyCode.switchToSymbol.rawValue, label: "#+",     widthPercent: 10,                      isModifier: true),
            KeyDef(code: LimeKeyCode.enter.rawValue,          widthPercent: 15, icon: "return",   isRepeatable: false, isModifier: true),
        ], isBottomRow: true),
    ])
}

enum CandidateExpansionPolicy {
    static func shouldExpand(hasCandidatesShown: Bool,
                             composing: String,
                             hasChineseSymbolCandidatesShown: Bool) -> Bool {
        return hasCandidatesShown
    }
}

enum KeyboardGesturePolicy {
    static func shouldUseDualRowGesture(isPad: Bool, layoutId: String, keyDef: KeyDef) -> Bool {
        isPad
            && layoutId.contains("_ipad")
            && keyDef.longPressCode != 0
            && keyDef.longPressCode != LimeKeyCode.keyboardOptionsMenu.rawValue
            && keyDef.popupKeyboard.isEmpty
    }

    static func shouldUseLimeOptionsMenuGesture(keyDef: KeyDef) -> Bool {
        keyDef.code == LimeKeyCode.done.rawValue
            || (keyDef.longPressCode == LimeKeyCode.keyboardOptionsMenu.rawValue
                && keyDef.code != LimeKeyCode.globe.rawValue)
    }
}

enum ShiftResetPolicy {
    static func shouldResetAfterCharacter(isShiftOn: Bool,
                                          capsLock: Bool,
                                          shiftKeyIsHeld: Bool) -> Bool {
        isShiftOn && !capsLock && !shiftKeyIsHeld
    }

    static func shouldResetAfterShiftRelease(capsLock: Bool,
                                             holdModifiedCharacter: Bool) -> Bool {
        !capsLock && holdModifiedCharacter
    }
}

enum ShiftPressPolicy {
    static func shouldHandleShiftPress(wasShiftKeyHeld: Bool) -> Bool {
        !wasShiftKeyHeld
    }
}

enum ShiftHoldTouchPolicy {
    static func isShiftStillHeld(activeTouchCount: Int) -> Bool {
        activeTouchCount > 1
    }

    static func isShiftStillHeld(activeTouchCount: Int,
                                 wasShiftAlreadyHeld: Bool) -> Bool {
        (wasShiftAlreadyHeld && activeTouchCount > 0) || activeTouchCount > 1
    }
}

enum EnglishKeyboardPolicy {
    private static let closingPunctuation: Set<Character> = [
        "\"", "'", ")", "]", "}",
        "\u{201D}", "\u{2019}",
    ]

    private static let abbreviationWords: Set<String> = [
        "Mr", "Mrs", "Ms", "Dr", "Prof", "Jr", "Sr", "St",
        "etc", "vs", "Ltd", "Inc", "Co", "Mt", "Ft",
    ]

    static func shouldAutoCapitalize(before: String) -> Bool {
        if before.isEmpty { return true }

        var s = Substring(before)
        var hasBoundaryWhitespace = false
        while let last = s.last, last == " " || last == "\t" {
            hasBoundaryWhitespace = true
            s = s.dropLast()
        }
        while let last = s.last, closingPunctuation.contains(last) {
            s = s.dropLast()
        }

        if let last = s.last, last == "\n" || last == "\r" { return true }
        guard hasBoundaryWhitespace else { return false }
        guard let term = s.last, term == "." || term == "!" || term == "?" else { return false }

        if term == ".", isAbbreviationBeforeDot(s.dropLast()) {
            return false
        }
        return true
    }

    static func shouldInsertPeriodForDoubleSpace(before: String) -> Bool {
        guard before.hasSuffix(" "), before.count >= 2 else { return false }
        let beforeSpace = before.dropLast()
        guard let previous = beforeSpace.last else { return false }

        if ".!?,:;".contains(previous) { return false }
        let tokenStart = beforeSpace.lastIndex(where: { $0.isWhitespace })
            .map { beforeSpace.index(after: $0) } ?? beforeSpace.startIndex
        let token = String(beforeSpace[tokenStart...])
        if token.contains("://") || token.contains(".") { return false }

        if previous.isLetter || previous.isNumber { return true }
        return closingPunctuation.contains(previous)
    }

    private static func isAbbreviationBeforeDot(_ beforeDot: Substring) -> Bool {
        guard let last = beforeDot.last, last.isLetter else { return false }

        if beforeDot.dropLast().last == "." { return true }

        var idx = beforeDot.endIndex
        while idx > beforeDot.startIndex {
            let prev = beforeDot.index(before: idx)
            if beforeDot[prev].isLetter { idx = prev } else { break }
        }
        let word = String(beforeDot[idx..<beforeDot.endIndex])
        return abbreviationWords.contains(word)
    }
}
