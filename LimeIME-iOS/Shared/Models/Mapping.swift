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

/// A single IM mapping candidate: input code → output word.
/// Mirrors Android's Mapping.java (spec §14).
struct Mapping {
    let id:        Int64
    let code:      String
    let word:      String
    var score:     Int
    let baseScore: Int
    let code3r:    String?  // tone-stripped code (phonetic IMs only)
    var recordType: Int     // one of Mapping.RecordType.* constants

    // MARK: - Extended Fields (spec §14)
    var codeorig:    String?  // original-case code before lowercasing
    var pword:       String?  // parent word (for related/learned phrases)
    var related:     String?  // related info string
    var highLighted: Bool     // candidate is highlighted/pre-selected

    // MARK: - Record Type Constants (spec §14)
    enum RecordType {
        static let composingCode        = 1   // typed code echo shown as first candidate
        static let exactMatchToCode     = 2   // exact code→word DB match
        static let partialMatchToCode   = 3   // prefix/partial DB match
        static let relatedPhrase        = 4   // word following a previously committed word
        static let englishSuggestion    = 5   // UITextChecker completion
        static let runtimeBuiltPhrase   = 6   // LD/QP learned phrase
        static let chinesePunctuation   = 7   // Chinese punctuation symbol
        static let hasMoreMark          = 8   // "More…" placeholder
        static let exactMatchToWord     = 9   // reverse lookup exact
        static let partialMatchToWord   = 10  // reverse lookup partial
        static let completionSuggestion = 11  // app-provided completion
        static let emoji                = 12  // emoji character
    }

    init(id: Int64, code: String, word: String, score: Int, baseScore: Int,
         code3r: String? = nil, recordType: Int = RecordType.exactMatchToCode,
         codeorig: String? = nil, pword: String? = nil, related: String? = nil,
         highLighted: Bool = false) {
        self.id          = id
        self.code        = code
        self.word        = word
        self.score       = score
        self.baseScore   = baseScore
        self.code3r      = code3r
        self.recordType  = recordType
        self.codeorig    = codeorig
        self.pword       = pword
        self.related     = related
        self.highLighted = highLighted
    }

    // MARK: - Type Checkers (mirrors Android Mapping.java is*Record() methods)
    var isComposingCodeRecord:      Bool { recordType == RecordType.composingCode }
    var isExactMatchToCodeRecord:   Bool { recordType == RecordType.exactMatchToCode }
    var isPartialMatchToCodeRecord: Bool { recordType == RecordType.partialMatchToCode }
    var isRelatedPhraseRecord:      Bool { recordType == RecordType.relatedPhrase }
    var isEnglishSuggestionRecord:  Bool { recordType == RecordType.englishSuggestion }
    var isRuntimeBuiltPhraseRecord: Bool { recordType == RecordType.runtimeBuiltPhrase }
    var isChinesePunctuationRecord: Bool { recordType == RecordType.chinesePunctuation }
    var isHasMoreMarkRecord:        Bool { recordType == RecordType.hasMoreMark }
    var isEmojiRecord:              Bool { recordType == RecordType.emoji }

    /// True for any real candidate eligible for learning (not a UI artifact).
    var isRealCandidate: Bool {
        !isComposingCodeRecord && !isHasMoreMarkRecord
    }

    // MARK: - Android-style accessors (used in tests)
    func getId() -> String { "\(id)" }
    func getScore() -> Int { score }
    func getBasescore() -> Int { baseScore }
    func getCode() -> String { code }
    func getWord() -> String { word }
    mutating func setExactMatchToCodeRecord() { recordType = RecordType.exactMatchToCode }
    mutating func setExactMatchToWordRecord() { recordType = RecordType.exactMatchToWord }
    mutating func setRelatedPhraseRecord()    { recordType = RecordType.relatedPhrase }
    mutating func setHasMoreRecordsMarkRecord() { recordType = RecordType.hasMoreMark }
    mutating func setPartialMatchToCodeRecord() { recordType = RecordType.partialMatchToCode }
}

enum LimeEndkeyPolicy {
    static func isCommitKey(primaryCode: Int, endkey: String?, englishOnly: Bool) -> Bool {
        guard !englishOnly,
              let endkey,
              !endkey.isEmpty,
              let scalar = UnicodeScalar(primaryCode) else { return false }
        return endkey.contains(String(Character(scalar)))
    }

    static func isKeyInImkeys(primaryCode: Int, imkeys: String?) -> Bool {
        guard let imkeys,
              !imkeys.isEmpty,
              let scalar = UnicodeScalar(primaryCode) else { return false }
        let key = String(Character(scalar))
        return imkeys.contains(key) || imkeys.contains(key.lowercased())
    }

    static func commitCandidateIndex(_ suggestions: [Mapping]) -> Int {
        guard !suggestions.isEmpty else { return -1 }
        if suggestions[0].isComposingCodeRecord || suggestions[0].isRuntimeBuiltPhraseRecord {
            return CandidateSelectionPolicy.defaultHighlightedCandidateIndex(suggestions)
        }
        if let index = suggestions.firstIndex(where: isEndkeyCommitCandidate) {
            return index
        }
        return -1
    }

    private static func isEndkeyCommitCandidate(_ candidate: Mapping) -> Bool {
        !candidate.isComposingCodeRecord
            && (candidate.isExactMatchToCodeRecord
                || candidate.isPartialMatchToCodeRecord
                || candidate.isChinesePunctuationRecord)
    }
}

enum CandidateSelectionPolicy {
    /// General highlight rule (not punctuation-specific):
    /// - If the first real candidate after the composing-code echo is an exact match
    ///   to the typed code, highlight that candidate (index 1).
    /// - Otherwise highlight the composing-code echo (index 0).
    ///
    /// "Exact match" means the candidate's `code` equals the composing-code echo's
    /// `code` (the full code the user typed). This deliberately does NOT inspect the
    /// record type, so any exact-code candidate — a DB exact match or an auto-inserted
    /// `，`/`。` whose code equals the typed `,`/`.` — is highlighted the same way.
    /// A shorter prefix/partial candidate must not replace the raw composing text when
    /// Space or Enter is pressed.
    static func defaultHighlightedCandidateIndex(_ suggestions: [Mapping]) -> Int {
        guard !suggestions.isEmpty else { return -1 }
        let first = suggestions[0]
        if suggestions.count > 1,
           suggestions[1].isExactMatchToCodeRecord
            || isExactMatchToComposing(suggestions[1], composing: first) {
            return 1
        }
        if first.isComposingCodeRecord || first.isRuntimeBuiltPhraseRecord {
            return 0
        }
        return -1
    }

    /// True when `candidate` is an exact match to the composing-code echo: the echo is a
    /// composing-code record, `candidate` is not, both carry a non-empty code, and the
    /// codes are equal (the full code the user typed). This generalizes "exact match"
    /// beyond `exactMatchToCode` records so an auto-inserted `，`/`。` whose code equals
    /// the typed `,`/`.` is highlighted the same way — without any punctuation-specific
    /// special case.
    private static func isExactMatchToComposing(_ candidate: Mapping, composing: Mapping) -> Bool {
        composing.isComposingCodeRecord
            && !candidate.isComposingCodeRecord
            && !candidate.code.isEmpty
            && candidate.code == composing.code
    }
}
