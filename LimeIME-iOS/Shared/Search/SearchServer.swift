import Foundation

// IM query engine: receives input codes from KeyboardViewController,
// queries lime.db for candidates, manages scoring, caching, and phrase learning.
// Port target: SearchServer.java (~1,500 lines)

final class SearchServer {

    private let dbProvider: () -> any LimeDBProtocol
    private var db: any LimeDBProtocol { dbProvider() }
    private var currentTableName: String = ""

    // MARK: - IM Capability Flags (spec §13 setTableName)
    private(set) var hasNumberMapping: Bool = false
    private(set) var hasSymbolMapping: Bool = false
    private var cachedSelkey: String = "1234567890"

    // MARK: - Sort preference (spec §15 sortSuggestions)
    /// When true, candidates are ordered by (score + basescore) DESC (default DB behaviour).
    /// When false, candidates are returned in DB insertion order (code-alphabetical).
    var sortSuggestions: Bool = true

    // MARK: - Prefs (spec §15) — pushed by KeyboardViewController after loadSettings()
    /// Gates makeRunTimeSuggestion (spec §15 smart_chinese_input).
    var smartChineseInput: Bool = true
    /// Gates learnRelatedPhrase (spec §15 candidate_suggestion).
    var candidateSuggestion: Bool = true
    /// Gates LD phrase learning trigger inside learnRelatedPhrase (spec §15 learn_phrase).
    var learnPhrasePref: Bool = true
    /// When false, similar-code candidates are suppressed (spec §15 similiar_enable).
    var similiarEnable: Bool = true
    /// Max similar-code candidates per query (spec §15 similiar_list).
    var similiarList: Int = 20

    /// Sync pref-driven config to LimeDB under cacheLock.
    /// Call this after setting all pref vars (avoids ordering bugs and threading races).
    func applyPrefsToDatabase() {
        cacheLock.lock()
        db.learnRelatedWords        = candidateSuggestion
        db.similarCodeCandidatesCap = similiarEnable ? similiarList : 0
        db.sortSuggestions          = sortSuggestions   // mirrors Android LimeDB.sort from getSortSuggestions()
        cacheLock.unlock()
    }

    // MARK: - Caches
    private var mappingCache:   [String: [Mapping]] = [:]
    private var relatedCache:   [String: [Mapping]] = [:]  // related phrases as Mapping
    private var blacklistCache: Set<String> = []
    private let cacheLock    = NSLock()
    private let maxCacheEntries = 1024

    // Score constants
    private let scoreAdjustmentIncrement = 50
    private let maxScoreThreshold        = 200
    private let minScoreThreshold        = 120

    // MARK: - Learning State (spec §9)
    private var ldPhraseList:      [Mapping]    = []   // current accumulating LD phrase
    private var ldPhraseListArray: [[Mapping]]  = []   // completed LD phrases pending write
    private let learnLock = NSLock()

    private var prefetchThread: Thread?

    init(db: any LimeDBProtocol) {
        self.dbProvider = { db }
    }

    init(dbProvider: @escaping () -> any LimeDBProtocol) {
        self.dbProvider = dbProvider
    }

    // MARK: - IM Selection (spec §13 setTableName)

    /// Set the phonetic keyboard variant for code remapping (spec §5).
    /// Pass "phonetic", "et_41", "et26", or "hsu".
    func setPhoneticKeyboardType(_ type: String) {
        db.phoneticKeyboardType = type
        clearAllCaches()
    }

    /// Switch IM table and update capability flags. Clears caches and triggers prefetch.
    func setTableName(_ name: String, hasNumberMapping: Bool = false, hasSymbolMapping: Bool = false) {
        self.hasNumberMapping = hasNumberMapping
        self.hasSymbolMapping = hasSymbolMapping
        cachedSelkey = db.getSelkeyForIM(name)
        // Keep LimeDB's own currentTableName in sync — getMappingByCode(softKeyboard:) uses it.
        db.setTableName(name)
        guard name != currentTableName else { return }
        currentTableName = name
        // mirrors Android: if (tablename.startsWith(LIME.DB_TABLE_CJ)) maxCodeLength = 5
        if name.hasPrefix("cj") {
            maxCodeLength = 5
        }
        clearAllCaches()
        triggerPrefetch()
    }

    func setSymbolMapping(_ value: Bool) {
        hasSymbolMapping = value
    }

    /// Backwards-compatible alias used by database setup.
    func setCurrentIM(tableName: String) { setTableName(tableName) }

    /// True if current table is the phonetic (tone-based) IM.
    /// Mirrors Android: tablename.equals(LIME.DB_TABLE_PHONETIC) — exact equality only.
    /// ETEN/HSU variants use sub-type remapping within the same "phonetic" table;
    /// they are not separate top-level tables, so we do not broaden this check.
    var isPhoneticTable: Bool {
        currentTableName == "phonetic"
    }

    /// True if current table is Stroke5 / WB — enforces 5-character code limit (spec §5).
    var isWBTable: Bool {
        currentTableName == "wb" || currentTableName.hasPrefix("stroke")
    }

    // MARK: - Selkey / Keyname (spec §13)

    /// Returns the selection key string for the current IM (e.g. "1234567890").
    func getSelkey() -> String { cachedSelkey }

    /// Converts a typed code to display-friendly symbol names (e.g. "1q" → "ㄅㄆ").
    /// Delegates to LimeDB.keyToKeyName() which maintains per-table caches.
    func keyToKeyname(_ code: String) -> String {
        let converted = db.keyToKeyName(code, currentTableName, true)
        return converted.isEmpty ? code : converted
    }

    // MARK: - Real Code Length (spec §13 getRealCodeLength)

    /// Returns the code length consumed by the selected mapping in the composing buffer.
    /// For phonetic IMs, strips tone symbols [3467 space] to find the actual boundary.
    /// Also prunes suggestionLoL/bestSuggestionStack and triggers LD learning for runtime phrases.
    /// Mirrors Android SearchServer.getRealCodeLength() lines 1024-1108.
    func getRealCodeLength(mapping: Mapping, composing: String) -> Int {
        let mappingCode = mapping.code
        var realCodeLen = mappingCode.count
        if isPhoneticTable {
            let toneChars: Set<Character> = ["3", "4", "6", "7", " "]
            let noToneCode = mappingCode.filter { !toneChars.contains($0) }
            if mappingCode != noToneCode {
                if !composing.hasPrefix(mappingCode) && composing.hasPrefix(noToneCode) {
                    realCodeLen = noToneCode.count
                } else {
                    realCodeLen = composing.count // unexpected condition
                }
            } else {
                realCodeLen = mappingCode.count
            }
        }
        realCodeLen = min(realCodeLen, composing.count)
        if realCodeLen < 1 { realCodeLen = 1 }

        // Prune suggestionLoL and bestSuggestionStack: remove entries whose code length
        // exceeds (currentCode.length - realCodeLen). Mirrors Android lines 1052-1072.
        if realCodeLen < composing.count {
            let maxAllowed = composing.count - realCodeLen
            suggestionLock.lock()
            suggestionLoL = suggestionLoL.compactMap { list in
                let pruned = list.filter { $0.code.count <= maxAllowed }
                return pruned.isEmpty ? nil : pruned
            }
            bestSuggestionStack = bestSuggestionStack.filter { $0.code.count <= maxAllowed }
            suggestionLock.unlock()
        }

        // LD phrase learning for runtime-built phrase selection. Mirrors Android lines 1075-1103.
        if learnPhrasePref && mapping.isRuntimeBuiltPhraseRecord {
            suggestionLock.lock()
            let bestList = suggestionLoL.last ?? []
            suggestionLock.unlock()
            if !bestList.isEmpty {
                let selectedWord = mapping.word
                let tableName = currentTableName
                DispatchQueue.global(qos: .background).async { [weak self] in
                    guard let self = self else { return }
                    for pair in bestList {
                        guard selectedWord.hasPrefix(pair.mapping.word) else { continue }
                        if pair.mapping.word.count > 8 { break }
                        try? self.db.addOrUpdateMappingRecord(code: pair.code, word: pair.mapping.word, tableName: tableName)
                        self.removeRemappedCodeCachedMappings(pair.code)
                    }
                }
            }
        }

        return realCodeLen
    }

    // MARK: - Core Search (spec §13 getMappingByCode)

    /// Returns mapping candidates for the given code.
    /// Prepends a COMPOSING_CODE echo record so index 0 is always the typed code.
    /// `isSoftKeyboard`: affects ordering (soft keyboard gets more results).
    /// `getAllRecords`: use a larger limit.
    func getMappingByCode(_ code: String, isSoftKeyboard: Bool = true,
                          getAllRecords: Bool = false, limit: Int = 50,
                          isPrefetch: Bool = false) -> [Mapping] {
        let effectiveLimit = getAllRecords ? 210 : limit

        if isPhoneticTable {
            let cacheKey = "\(currentTableName):\(code.lowercased()):\(effectiveLimit)"
            cacheLock.lock()
            if let cached = mappingCache[cacheKey] { cacheLock.unlock(); return cached }
            // Fallback: if Stage 1 misses but Stage 2 (full) result is already cached
            // (e.g. populated by prefetch), use it — no hasMoreMark means Stage 2 never fires.
            if !getAllRecords, let fullCached = mappingCache["\(currentTableName):\(code.lowercased()):210"] {
                cacheLock.unlock(); return fullCached
            }
            cacheLock.unlock()

            let dbResults = db.getMappingByCode(code, softKeyboard: isSoftKeyboard,
                                                getAllRecords: getAllRecords) ?? []
            let echo = Mapping(id: 0, code: code.lowercased(), word: code.lowercased(),
                               score: 0, baseScore: 0, recordType: Mapping.RecordType.composingCode)
            if dbResults.isEmpty { return [] }
            if smartChineseInput && !isPrefetch {
                makeRunTimeSuggestion(code: code, completeCodeResultList: dbResults)
            }
            let finalList = assembleResultList(echo: echo, dbResults: dbResults)
            cacheLock.lock(); evictIfNeeded(); mappingCache[cacheKey] = finalList; cacheLock.unlock()
            return finalList
        }

        // Non-phonetic path
        let lowered = code.lowercased()
        let cacheKey = "\(currentTableName):\(lowered):\(effectiveLimit)"

        cacheLock.lock()
        if let cached = mappingCache[cacheKey] { cacheLock.unlock(); return cached }
        // Fallback: if Stage 1 misses but the full (Stage 2) result is already cached
        // (populated by prefetch with getAllRecords:true), return it directly.
        // The full result has no hasMoreMark sentinel, so wasTruncated=false in
        // KeyboardViewController and Stage 2 never fires for this code.
        if !getAllRecords, let fullCached = mappingCache["\(currentTableName):\(lowered):210"] {
            cacheLock.unlock(); return fullCached
        }
        cacheLock.unlock()

        let dbResults: [Mapping] = db.getMappingByCode(
            code, softKeyboard: isSoftKeyboard, getAllRecords: getAllRecords) ?? []

        let echo = Mapping(id: 0, code: code, word: code,
                           score: 0, baseScore: 0, recordType: Mapping.RecordType.composingCode)
        if dbResults.isEmpty { return [] }
        if smartChineseInput && !isPrefetch {
            makeRunTimeSuggestion(code: code, completeCodeResultList: dbResults)
        }
        let finalList = assembleResultList(echo: echo, dbResults: dbResults)
        cacheLock.lock(); evictIfNeeded(); mappingCache[cacheKey] = finalList; cacheLock.unlock()
        return finalList
    }

    /// Assemble final candidate list: echo at 0, optional bestSuggestion/englishSuggestion at 1,
    /// then dbResults. Mirrors Android getMappingByCode lines 876-917.
    private func assembleResultList(echo: Mapping, dbResults: [Mapping]) -> [Mapping] {
        var englishSuggestion: Mapping? = nil
        if echo.code.count > maxCodeLength {
            if let es = getEnglishSuggestions(echo.code).first {
                englishSuggestion = Mapping(id: es.id, code: echo.code, word: es.word,
                                            score: es.score, baseScore: es.baseScore,
                                            recordType: Mapping.RecordType.englishSuggestion)
            }
        }

        let bestPair = bestSuggestionStack.last
        let bestSuggestion = bestPair?.mapping
        var averageScore = 0
        var bestLen = 0
        if let bs = bestSuggestion {
            bestLen = bs.word.count
            if bestLen > 0 { averageScore = bs.baseScore / bestLen }
        }
        let bestSuggestionDuplicatesDbResult = bestSuggestion.map { bs in
            dbResults.contains { $0.word == bs.word }
        } ?? false

        var result: [Mapping] = [echo]
        if let bs = bestSuggestion,
           !abandonPhraseSuggestion,
           !bs.isExactMatchToCodeRecord,
           !bestSuggestionDuplicatesDbResult,
           bestLen > 1,
           (englishSuggestion == nil && averageScore > minScoreThreshold)
             || (englishSuggestion != nil && averageScore > maxScoreThreshold) {
            result.append(bs)
        } else if let es = englishSuggestion, averageScore <= maxScoreThreshold {
            clearRunTimeSuggestion(abandonSuggestion: true)
            result.append(es)
        }
        result.append(contentsOf: dbResults)
        return result
    }

    // MARK: - Runtime Phrase Suggestion (spec §6, §13 makeRunTimeSuggestion)

    // Runtime phrase suggestion state (mirrors Android suggestionLoL / bestSuggestionStack)
    private var suggestionLoL: [[(mapping: Mapping, code: String)]] = []
    private var bestSuggestionStack: [(mapping: Mapping, code: String)] = []
    private var lastCode: String = ""   // mirrors Android's static lastCode
    private let suggestionLock = NSLock()

    // MARK: - Runtime Phrase Suggestion (mirrors Android makeRunTimeSuggestion)

    /// Clears the runtime suggestion state. mirrors Android clearRunTimeSuggestion(boolean).
    private func clearRunTimeSuggestion(abandonSuggestion: Bool) {
        suggestionLoL.removeAll()
        bestSuggestionStack.removeAll()
        abandonPhraseSuggestion = abandonSuggestion
    }

    /// Full port of Android SearchServer.makeRunTimeSuggestion(code, completeCodeResultList).
    /// Called internally from getMappingByCode. Updates suggestionLoL and bestSuggestionStack.
    /// Must be called with suggestionLock NOT held — acquires it internally.
    private func makeRunTimeSuggestion(code: String, completeCodeResultList: [Mapping]) {
        suggestionLock.lock()
        defer { suggestionLock.unlock() }
        // Stage 0: session-state maintenance based on lastCode (mirrors Android lines 363-395)
        if !suggestionLoL.isEmpty {
            if code.count == 1 {
                clearRunTimeSuggestion(abandonSuggestion: false)
            } else if code.count == lastCode.count - 1 {
                // User pressed backspace — trim trailing entries matching lastCode
                for i in 0..<suggestionLoL.count {
                    if let last = suggestionLoL[i].last, last.code == lastCode {
                        suggestionLoL[i].removeLast()
                    }
                }
                if let last = bestSuggestionStack.last, last.code == lastCode {
                    bestSuggestionStack.removeLast()
                }
                if suggestionLoL.allSatisfy({ $0.isEmpty }) {
                    bestSuggestionStack.removeAll()
                }
            }
        }
        lastCode = code

        let firstIsExact = completeCodeResultList.first?.isExactMatchToCodeRecord ?? false
        if firstIsExact {
            // Stage A: exact-match phrase building (mirrors Android do-while k < 5)
            var k = 0
            var highestScore = 0
            var highestScoreIndex = suggestionLoL.count
            let initialSize = suggestionLoL.count
            var snapshot: [[(mapping: Mapping, code: String)]]? = nil

            while k < 5 && k < completeCodeResultList.count {
                let em = completeCodeResultList[k]
                guard em.isExactMatchToCodeRecord else { break }
                guard em.baseScore > 0 else { k += 1; continue }

                var emScore = em.baseScore
                if emScore < minScoreThreshold { emScore = minScoreThreshold }
                if emScore > maxScoreThreshold { emScore = maxScoreThreshold }
                let wordLen = em.word.count
                let codeLenBonus = (wordLen > 0 ? em.code.count / wordLen : 0) * codeLengthBonusMultiplier
                let newScore = emScore + codeLenBonus
                let adjustedBaseScore = newScore * wordLen
                let emAdj = Mapping(id: em.id, code: em.code, word: em.word, score: em.score,
                                    baseScore: adjustedBaseScore, recordType: em.recordType, pword: em.pword)

                if adjustedBaseScore > 0 {
                    if k == 0 && wordLen > 1 {
                        // Multi-char exact match on first result — snapshot and restart
                        snapshot = suggestionLoL
                        suggestionLoL.removeAll()
                        highestScoreIndex = 0
                    }
                    if newScore > highestScore {
                        highestScore = newScore
                        highestScoreIndex = k + (snapshot != nil ? 0 : initialSize)
                    }
                    var suggestionList: [(mapping: Mapping, code: String)] = []
                    if let snap = snapshot {
                        // Carry forward matching chains from snapshot
                        for snapList in snap {
                            if let first = snapList.first,
                               emAdj.word.hasPrefix(first.mapping.word) {
                                for pair in snapList {
                                    if emAdj.word.hasPrefix(pair.mapping.word) {
                                        suggestionList.append(pair)
                                    }
                                }
                            }
                        }
                    }
                    suggestionList.append((mapping: emAdj, code: code))
                    suggestionLoL.append(suggestionList)
                }
                k += 1
            }

            // Promote best to tail
            if !suggestionLoL.isEmpty && highestScoreIndex != suggestionLoL.count - 1
               && highestScoreIndex < suggestionLoL.count {
                let best = suggestionLoL.remove(at: highestScoreIndex)
                suggestionLoL.append(best)
            }

        } else if !suggestionLoL.isEmpty {
            // Stage B: remaining-code search (mirrors Android lines 473-582)
            var highestScore = 0
            var highestRelatedScore = 0
            var highestScoreIndex = 0
            let snapshot = suggestionLoL

            for seedSuggestionList in snapshot {
                let lolSizeBefore = suggestionLoL.count
                // Remove this list from front of suggestionLoL
                if let idx = suggestionLoL.firstIndex(where: { list in
                    guard list.count == seedSuggestionList.count else { return false }
                    return zip(list, seedSuggestionList).allSatisfy { a, b in
                        a.code == b.code && a.mapping.word == b.mapping.word
                    }
                }) {
                    suggestionLoL.remove(at: idx)
                    // Only shift the tracked index when the removed entry was at or before it
                    if idx <= highestScoreIndex && highestScoreIndex > 0 { highestScoreIndex -= 1 }
                }

                for pair in seedSuggestionList {
                    let pCode = pair.code
                    guard pCode.count < code.count,
                          code.hasPrefix(pCode),
                          code.count - pCode.count <= maxCodeLength else { continue }

                    let remainingCode = String(code.dropFirst(pCode.count))
                    let resultList = getMappingByCodeFromCacheOrDB(remainingCode, getAllRecords: false)
                    guard let rem = resultList.first(where: { $0.isExactMatchToCodeRecord }) else { continue }

                    let remWordLen = rem.word.count
                    guard remWordLen >= 1, rem.baseScore >= 2 else { continue }

                    let phrase = pair.mapping.word + rem.word
                    let phraseLen = phrase.count
                    guard phraseLen >= 2 else { continue }

                    var remScore = rem.baseScore
                    let remCodeLenBonus = (remWordLen > 0 ? rem.code.count / remWordLen : 0) * codeLengthBonusMultiplier
                    // Android line 528: if (remainingScore > MIN_SCORE_THRESHOLD) remainingScore = MIN_SCORE_THRESHOLD;
                    // Only a ceiling at MIN_SCORE_THRESHOLD (120), no floor — exact Android port.
                    if remScore > minScoreThreshold { remScore = minScoreThreshold }
                    remScore = remScore / remWordLen + remCodeLenBonus

                    let prevWordLen = pair.mapping.word.count
                    let prevScore = prevWordLen > 0 ? pair.mapping.baseScore / prevWordLen : 0
                    let averageScore = (prevScore + remScore) / 2

                    // Check related table for suffix pairs (k=3..1)
                    var relatedMapping: Mapping? = nil
                    let checkLen = min(phraseLen - 1, 3)
                    for k in stride(from: checkLen, through: 1, by: -1) {
                        let pIdx = phrase.index(phrase.startIndex, offsetBy: phraseLen - k - 1)
                        let cIdx = phrase.index(phrase.startIndex, offsetBy: phraseLen - k)
                        let pword = String(phrase[pIdx..<cIdx])
                        let cword = String(phrase[cIdx...])
                        if let rm = db.isRelatedPhraseExist(pword, cword) {
                            relatedMapping = rm
                            break
                        }
                    }

                    if let rm = relatedMapping,
                       rm.baseScore >= highestRelatedScore,
                       (averageScore + scoreAdjustmentIncrement) > highestScore {
                        highestRelatedScore = rm.baseScore
                        highestScore = averageScore + scoreAdjustmentIncrement
                        let suggest = Mapping(id: 0, code: code, word: phrase,
                                              score: highestRelatedScore,
                                              baseScore: highestScore * phraseLen,
                                              recordType: Mapping.RecordType.runtimeBuiltPhrase)
                        var newList = seedSuggestionList
                        newList.append((mapping: suggest, code: code))
                        suggestionLoL.append(newList)
                        highestScoreIndex = suggestionLoL.count - 1
                    } else if averageScore > highestScore {
                        highestScore = averageScore
                        let suggest = Mapping(id: 0, code: code, word: phrase,
                                              score: 0,
                                              baseScore: highestScore * phraseLen,
                                              recordType: Mapping.RecordType.runtimeBuiltPhrase)
                        var newList = seedSuggestionList
                        newList.append((mapping: suggest, code: code))
                        suggestionLoL.append(newList)
                        highestScoreIndex = suggestionLoL.count - 1
                    }
                }

                // If no new entries were added, keep the seed list
                if suggestionLoL.count == lolSizeBefore - 1 {
                    suggestionLoL.append(seedSuggestionList)
                }
            }

            // Promote best to tail
            if !suggestionLoL.isEmpty && highestScoreIndex != suggestionLoL.count - 1
               && highestScoreIndex < suggestionLoL.count {
                let best = suggestionLoL.remove(at: highestScoreIndex)
                suggestionLoL.append(best)
            }
        }

        // Stage C: push tail of last list to bestSuggestionStack
        if let lastList = suggestionLoL.last, let lastPair = lastList.last {
            bestSuggestionStack.append(lastPair)
        }
    }

    /// Deprecated iOS-only API — Android has no equivalent.
    /// Runtime suggestion state is now managed entirely by makeRunTimeSuggestion via getMappingByCode.
    /// Kept for source compatibility; body is a no-op.
    func addToSuggestionContext(_ candidate: Mapping, code: String) {}

    /// Resets runtime suggestion state. Mirrors Android clearRunTimeSuggestion(false).
    func clearSuggestionContext() {
        suggestionLock.lock()
        clearRunTimeSuggestion(abandonSuggestion: false)
        suggestionLock.unlock()
    }

    // MARK: - Related Phrases (spec §13 getRelatedByWord)

    /// Returns related-phrase candidates following parentWord as Mapping objects.
    func getRelatedByWord(_ word: String, getAllRecords: Bool = false) -> [Mapping] {
        guard similiarEnable else { return [] }
        cacheLock.lock()
        if let cached = relatedCache[word] { cacheLock.unlock(); return cached }
        cacheLock.unlock()

        let limit = getAllRecords ? 50 : 10
        let results = (try? db.getRelatedMappings(parentWord: word, limit: limit)) ?? []

        cacheLock.lock()
        evictIfNeeded()
        relatedCache[word] = results
        cacheLock.unlock()

        return results
    }

    // MARK: - Learning (spec §9 learnRelatedPhraseAndUpdateScore)

    /// Records a committed candidate for session-end batch RP learning and updates score.
    /// Mirrors Android: score update inline, learnRelatedPhrase batched at postFinishInput.
    func learnRelatedPhraseAndUpdateScore(_ candidate: Mapping) {
        // Append to scorelist for batch learning at session end (mirrors Android)
        scorelistLock.lock()
        scorelist.append(candidate)
        scorelistLock.unlock()

        let tableName = currentTableName
        // .utility (not .background): the score update + cache re-warm is a
        // user-initiated learning step that the next composition depends on,
        // so it must not be starved. Mirrors Android's normal-priority learning
        // thread and the emoji preload path's .utility QoS below.
        DispatchQueue.global(qos: .utility).async { [weak self] in
            guard let self = self else { return }
            // Score update
            if candidate.id > 0 {
                let currentScore = candidate.score
                let newScore = currentScore + 1
                try? self.db.updateScore(id: candidate.id, score: newScore, tableName: tableName)
                self.removeRemappedCodeCachedMappings(candidate.code.lowercased())
                let evictedPrefixes = self.updateSimilarCodeCache(candidate.code.lowercased())
                // Re-warm all evicted entries on this background thread so the next
                // composition is still a cache hit — with the updated score order from DB.
                let snapshotTable = self.currentTableName
                guard self.currentTableName == snapshotTable else { return }
                _ = self.getMappingByCode(candidate.code.lowercased())
                for prefix in evictedPrefixes {
                    guard self.currentTableName == snapshotTable else { return }
                    _ = self.getMappingByCode(prefix)
                }
            }
        }
    }

    /// Batch related-phrase learning over the session's committed word pairs.
    /// Mirrors Android SearchServer.learnRelatedPhrase(List<Mapping>) lines 1272-1322.
    /// Called from postFinishInput via background task.
    private func learnRelatedPhrase(_ localScorelist: [Mapping]) {
        guard candidateSuggestion && localScorelist.count > 1 else { return }
        for i in 0..<localScorelist.count - 1 {
            let unit  = localScorelist[i]
            let unit2 = localScorelist[i + 1]
            guard !unit.word.isEmpty, !unit2.word.isEmpty else { continue }
            // Android unit type checks (note: intentional use of `unit` not `unit2` in some checks)
            let unitOK = unit.isExactMatchToCodeRecord
                || unit.isPartialMatchToCodeRecord
                || unit.isRelatedPhraseRecord
            let unit2OK = unit2.isExactMatchToCodeRecord
                || unit2.isPartialMatchToCodeRecord
                || unit.isRelatedPhraseRecord   // Android bug preserved: uses unit not unit2
                || unit2.isChinesePunctuationRecord
                || unit.isEmojiRecord           // Android bug preserved: uses unit not unit2
                || unit2.isEmojiRecord
            guard unitOK && unit2OK else { continue }
            let score = db.addOrUpdateRelatedPhraseRecord(unit.word, unit2.word)
            // Invalidate related cache
            cacheLock.lock()
            relatedCache.removeValue(forKey: unit.word)
            cacheLock.unlock()
            if score > 20 && learnPhrasePref {
                addLDPhrase(unit, ending: false)
                addLDPhrase(unit2, ending: true)
            }
        }
    }

    // MARK: - LD Learning (spec §9 addLDPhrase / learnLDPhrase)

    /// Buffer a mapping for LD phrase learning. When ending=true, saves the accumulated list.
    func addLDPhrase(_ mapping: Mapping?, ending: Bool) {
        guard learnPhrasePref else { return }
        learnLock.lock()
        defer { learnLock.unlock() }
        if let m = mapping { ldPhraseList.append(m) }
        if ending {
            if ldPhraseList.count > 1 { ldPhraseListArray.append(ldPhraseList) }
            ldPhraseList = []
        }
    }

    /// Called from postFinishInput — drains current ldPhraseListArray (deprecated direct path).
    func learnLDPhrase() {
        learnLock.lock()
        let toLearn = ldPhraseListArray
        ldPhraseListArray = []
        learnLock.unlock()
        learnLDPhraseList(toLearn)
    }

    /// Port of Android SearchServer.learnLDPhrase(ArrayList<List<Mapping>>) lines 1333-1491.
    /// Each phraselist is a sequence of committed mappings forming a multi-char phrase.
    private func learnLDPhraseList(_ toLearn: [[Mapping]]) {
        let tableName = currentTableName
        let tones: Set<Character> = ["3", "4", "6", "7", " "]

        for phrase in toLearn {
            guard phrase.count > 1, phrase.count < 5 else { continue }
            guard let unit1 = phrase.first, !unit1.word.isEmpty,
                  unit1.code != unit1.word else { continue }

            var baseCode = unit1.code
            var qpCode = ""
            var baseWord = unit1.word

            // Reverse-lookup if unit1 has no reliable code
            if unit1.id == 0 || unit1.isPartialMatchToCodeRecord
               || unit1.code.isEmpty || unit1.isRelatedPhraseRecord {
                if let first = db.getMappingByWord(unit1.word, table: tableName)?.first {
                    baseCode = first.code
                } else { continue }
            }

            if baseWord.count == 1 {
                guard !baseCode.isEmpty, let fc = baseCode.first else { continue }
                qpCode.append(fc)
            } else {
                // Rebuild baseCode per-char for multi-char unit1
                baseCode = ""
                var abort = false
                for ch in baseWord {
                    guard let first = db.getMappingByWord(String(ch), table: tableName)?.first else {
                        abort = true; break
                    }
                    baseCode += first.code
                    if let fc = first.code.first { qpCode.append(fc) }
                }
                if abort { continue }
            }

            for i in 0..<phrase.count {
                guard i + 1 < phrase.count else { break }
                let unit2 = phrase[i + 1]
                if unit2.word.isEmpty || unit2.isComposingCodeRecord
                   || unit2.isEnglishSuggestionRecord { break }
                let word2 = unit2.word
                var code2 = unit2.code
                baseWord += word2

                if word2.count == 1 && baseWord.count < 5 {
                    if unit2.id == 0 || unit2.isPartialMatchToCodeRecord
                       || code2.isEmpty || unit2.isRelatedPhraseRecord {
                        if let first = db.getMappingByWord(word2, table: tableName)?.first {
                            code2 = first.code
                        } else { break }
                    }
                    guard !code2.isEmpty, let fc = code2.first else { break }
                    baseCode += code2
                    qpCode.append(fc)
                } else if word2.count > 1 && baseWord.count < 5 {
                    var fail = false
                    for ch in word2 {
                        guard let first = db.getMappingByWord(String(ch), table: tableName)?.first else {
                            fail = true; break
                        }
                        baseCode += first.code
                        if let fc = first.code.first { qpCode.append(fc) }
                    }
                    if fail { break }
                } else {
                    break
                }

                // Write on the last pair only (mirrors Android i+1 == phraselist.size - 1)
                if i + 1 == phrase.count - 1 {
                    if isPhoneticTable {
                        let ldCode = baseCode.filter { !tones.contains($0) }.lowercased()
                        let qpLower = qpCode.lowercased()
                        if ldCode.count > 1 {
                            try? db.addOrUpdateMappingRecord(code: ldCode, word: baseWord, tableName: tableName)
                            removeRemappedCodeCachedMappings(ldCode)
                            updateSimilarCodeCache(ldCode)
                        }
                        if qpLower.count > 1 {
                            try? db.addOrUpdateMappingRecord(code: qpLower, word: baseWord, tableName: tableName)
                            removeRemappedCodeCachedMappings(qpLower)
                            updateSimilarCodeCache(qpLower)
                        }
                    } else if baseCode.count > 1 {
                        let bc = baseCode.lowercased()
                        try? db.addOrUpdateMappingRecord(code: bc, word: baseWord, tableName: tableName)
                        removeRemappedCodeCachedMappings(bc)
                        updateSimilarCodeCache(bc)
                    }
                }
            }
        }
    }

    // MARK: - Emoji (spec §6, §13 emojiConvert)

    /// Inject emoji candidates using Android-exact word-based lookup.
    /// Mirrors LIMEService.java lines 2461-2523 exactly.
    ///
    /// - `list` must include the composing-code echo at index 0 (same structure as getMappingByCode output).
    /// - Looks up emoji by list[0].word if it is pure English letters (EMOJI_EN),
    ///   else by list[1].word if multi-byte and length < 4 (EMOJI_TW).
    /// - Deduplicates within the emoji results using a Set<String> (mirrors Android emojiCheck HashMap).
    /// - Inserts at `insertAt` (0-based index into `list`). Clamped to list.count.
    func injectEmoji(into list: [Mapping], insertAt: Int = 3) -> [Mapping] {
        guard SearchServer.shouldInjectEmojiCandidates(insertAt: insertAt) else { return list }
        guard !list.isEmpty else { return list }

        var emojiList: [Mapping] = []
        var emojiCheck: Set<String> = []

        // Android: if (list.get(0).getWord().matches("[A-Za-z]+"))
        let echoWord = list[0].word
        var item1: [Mapping] = []
        if echoWord.range(of: "^[A-Za-z]+$", options: .regularExpression) != nil {
            item1 = db.findEmojiForCandidate(echoWord, locale: .en, limit: 8)
            for m in item1 {
                if emojiCheck.insert(m.word).inserted { emojiList.append(m) }
            }
        }

        if item1.isEmpty {
            // Android: list.get(1).getWord().getBytes().length > 1 && length() < 4
            if list.count > 1 {
                let word2 = list[1].word
                if !word2.isEmpty, word2.utf8.count > 1, word2.count < 4 {
                    let item2 = db.findEmojiForCandidate(word2, locale: .tw, limit: 8)
                    if !item2.isEmpty {
                        for m in item2 {
                            if emojiCheck.insert(m.word).inserted { emojiList.append(m) }
                        }
                    }
                }
            }
        }

        guard !emojiList.isEmpty else { return list }

        // Drop emoji whose word is already in the candidate list (avoid visible duplicates)
        let existingWords = Set(list.map { $0.word })
        let unique = emojiList.filter { !existingWords.contains($0.word) }
        guard !unique.isEmpty else { return list }

        var result = list
        let idx = adjustedEmojiInsertionIndex(in: result, requestedIndex: insertAt)
        result.insert(contentsOf: unique, at: idx)
        return result
    }

    /// Direct emoji injection by explicit word and type.
    /// Used for the English prediction path where the lookup word is known directly.
    func injectEmoji(into list: [Mapping], word: String, type: Int, insertAt: Int = 3) -> [Mapping] {
        guard SearchServer.shouldInjectEmojiCandidates(insertAt: insertAt) else { return list }
        let candidates = emojiConvert(word, type)
        guard !candidates.isEmpty else { return list }
        let existingWords = Set(list.map { $0.word })
        let unique = candidates.filter { !existingWords.contains($0.word) }
        guard !unique.isEmpty else { return list }
        var result = list
        let idx = adjustedEmojiInsertionIndex(in: result, requestedIndex: insertAt)
        result.insert(contentsOf: unique, at: idx)
        return result
    }

    func adjustedEmojiInsertionIndex(in list: [Mapping], requestedIndex: Int) -> Int {
        guard !list.isEmpty else { return 0 }

        var index = min(max(requestedIndex, 0), list.count)
        for candidateIndex in index..<list.count {
            if isChinesePeriodOrComma(list[candidateIndex]) {
                index = candidateIndex + 1
                break
            }
        }
        return index
    }

    static func shouldInjectEmojiCandidates(insertAt: Int) -> Bool {
        insertAt > 0
    }

    private func isChinesePeriodOrComma(_ candidate: Mapping) -> Bool {
        return candidate.isChinesePunctuationRecord || candidate.word == "，" || candidate.word == "。"
    }

    func injectEnglishEmoji(into list: [Mapping], word: String, insertAt: Int = 3) -> [Mapping] {
        return injectEmoji(into: list, word: word, type: LimeDB.EMOJI_EN, insertAt: insertAt)
    }

    func emojiConvert(_ code: String, _ type: Int) -> [Mapping] {
        guard !code.isEmpty else { return [] }
        let cacheKey = "\(type):\(code)"
        if let cached = cachedEmojiMappings(cacheKey) { return cached }
        let results = db.emojiConvert(code, type)
        cacheEmojiMappings(results, for: cacheKey)
        return results
    }

    func findEmojiForCandidate(_ code: String, locale: LimeDB.EmojiLocale, limit: Int) -> [Mapping] {
        guard !code.isEmpty else { return [] }
        let cacheKey = "v2:\(locale):\(code):\(limit)"
        if let cached = cachedEmojiMappings(cacheKey) { return cached }
        let results = db.findEmojiForCandidate(code, locale: locale, limit: limit)
        cacheEmojiMappings(results, for: cacheKey)
        return results
    }

    func searchEmoji(_ query: String, locale: LimeDB.EmojiLocale, limit: Int) -> [Mapping] {
        guard !query.isEmpty else { return [] }
        let cacheKey = "search:\(locale):\(query):\(limit)"
        if let cached = cachedEmojiMappings(cacheKey) { return cached }
        let results = db.searchEmoji(query, locale: locale, limit: limit)
        cacheEmojiMappings(results, for: cacheKey)
        return results
    }

    func loadRecentEmoji(_ limit: Int) -> [Mapping] {
        let cacheKey = "recent:\(limit)"
        if let cached = cachedEmojiMappings(cacheKey) { return cached }
        let results = db.loadRecentEmoji(limit: limit)
        cacheEmojiMappings(results, for: cacheKey)
        return results
    }

    func loadEmojiCategoryPages() -> [[Mapping]] {
        emojiCategoryCacheLock.lock()
        if let cached = emojiCategoryPagesCache {
            emojiCategoryCacheLock.unlock()
            return cached
        }
        emojiCategoryCacheLock.unlock()

        let pages = db.loadEmojiCategoryPages()

        emojiCategoryCacheLock.lock()
        emojiCategoryPagesCache = pages
        emojiCategoryCacheLock.unlock()
        return pages
    }

    func preloadEmojiCategoryPages() {
        emojiCategoryCacheLock.lock()
        let shouldStart = emojiCategoryPagesCache == nil && !isEmojiCategoryPreloadInFlight
        if shouldStart { isEmojiCategoryPreloadInFlight = true }
        emojiCategoryCacheLock.unlock()
        guard shouldStart else { return }

        DispatchQueue.global(qos: .utility).async { [weak self] in
            guard let self else { return }
            let pages = self.db.loadEmojiCategoryPages()
            self.emojiCategoryCacheLock.lock()
            if !pages.isEmpty {
                self.emojiCategoryPagesCache = pages
            }
            self.isEmojiCategoryPreloadInFlight = false
            self.emojiCategoryCacheLock.unlock()
        }
    }

    func recordEmojiUsage(_ value: String) {
        db.recordEmojiUsage(value, timestampSeconds: Int64(Date().timeIntervalSince1970))
        clearEmojiCaches()
    }

    // MARK: - Reverse Lookup (spec §8, §13)

    /// Returns a formatted string of all codes for a given word, with key names applied.
    func getCodeListStringFromWord(_ word: String) -> String? {
        db.getCodeListStringByWord(word, table: nil)
    }

    /// Reverse lookup: returns codes for `word` using an explicit IM table (not the current one).
    func getCodeListStringFromWord(_ word: String, usingTable table: String) -> String? {
        db.getCodeListStringByWord(word, table: table)
    }

    // MARK: - Finish Input (spec §13 postFinishInput)

    /// Flush all pending learning when the text field loses focus.
    func postFinishInput() {
        scorelistLock.lock()
        let snapshot = scorelist
        scorelist.removeAll()
        scorelistLock.unlock()

        // Snapshot continuous-typing LD phrases accumulated so far.
        learnLock.lock()
        let continuousLD = ldPhraseListArray
        ldPhraseListArray = []
        learnLock.unlock()

        DispatchQueue.global(qos: .background).async { [weak self] in
            guard let self = self else { return }
            // learnRelatedPhrase runs first — may call addLDPhrase for high-score RP pairs.
            // Mirrors Android postFinishInput ordering (SearchServer.java lines 1250–1259).
            self.learnRelatedPhrase(snapshot)
            // Snapshot RP-triggered LD phrases added by learnRelatedPhrase.
            self.learnLock.lock()
            let rpLD = self.ldPhraseListArray
            self.ldPhraseListArray = []
            self.learnLock.unlock()
            self.learnLDPhraseList(continuousLD + rpLD)
        }
    }

    // MARK: - Cache Management

    func clearAllCaches() {
        cacheLock.lock()
        mappingCache.removeAll()
        relatedCache.removeAll()
        blacklistCache.removeAll()
        cacheLock.unlock()
        clearEmojiCaches()
    }

    private func evictIfNeeded() {
        // cacheLock must be held by caller
        if mappingCache.count >= maxCacheEntries  { mappingCache.removeAll() }
        if relatedCache.count  >= maxCacheEntries  { relatedCache.removeAll() }
        if blacklistCache.count >= maxCacheEntries  { blacklistCache.removeAll() }
    }

    // MARK: - Prefetch

    /// Background-thread prefetch for first-character keys — mirrors Android's prefetchCache().
    /// Fetches with getAllRecords:true (LIMIT 210) so the full result is cached.
    /// When the user types their first stroke, getMappingByCode falls back to the :210 cache entry,
    /// returns the full result with no hasMoreMark sentinel → Stage 2 never fires for first strokes.
    private func triggerPrefetch() {
        prefetchThread?.cancel()
        let snapshotTable = currentTableName
        let t = Thread { [weak self] in
            var keys = "abcdefghijklmnopqrstuvwxyz"
            if self?.hasNumberMapping == true { keys += "01234567890" }
            if self?.hasSymbolMapping == true { keys += ",./;" }
            for ch in keys {
                guard !Thread.current.isCancelled else { return }
                guard let self = self, self.currentTableName == snapshotTable else { return }
                _ = self.getMappingByCode(String(ch), getAllRecords: true, isPrefetch: true)
            }
        }
        t.qualityOfService = .background
        prefetchThread = t
        t.start()
    }

    // MARK: - Additional Cache / State Fields (mirrors Java scorelist, coderemapcache, etc.)

    // Session-level score list (mirrors Android's static scorelist)
    private var scorelist: [Mapping] = []
    private let scorelistLock = NSLock()

    private var abandonPhraseSuggestion: Bool = false
    private var maxCodeLength: Int = 4
    // CODE_LENGTH_BONUS_MULTIPLIER (mirrors Android constant = 30)
    private let codeLengthBonusMultiplier = 30

    private var coderemap: [String: [String]] = [:]
    private var englishCache: [String: [Mapping]] = [:]

    /// Optional English-completion source injected by the keyboard layer (UITextChecker).
    /// When set, `getEnglishSuggestions` uses it instead of the legacy FTS `dictionary`
    /// table — which no longer exists once the scored-dictionary seed ships (see
    /// docs/ENG_AUTO_COMPLETION.md "iOS impact of the seed change"). Kept as a plain
    /// `(String) -> [String]` closure so SearchServer stays UIKit-free (Foundation only).
    /// Settings-context SearchServers leave this nil and fall back to the DB query.
    var englishCompletionProvider: ((String) -> [String])?
    private var emojiCache: [String: [Mapping]] = [:]
    private var emojiCategoryPagesCache: [[Mapping]]?
    private var isEmojiCategoryPreloadInFlight = false
    private let emojiCategoryCacheLock = NSLock()
    private var keynameCache: [String: String] = [:]
    private var lastEnglishWord: String? = nil
    private var noSuggestionsForLastEnglishWord: Bool = false

    private func cachedEmojiMappings(_ cacheKey: String) -> [Mapping]? {
        cacheLock.lock()
        let cached = emojiCache[cacheKey]
        cacheLock.unlock()
        return cached
    }

    private func cacheEmojiMappings(_ mappings: [Mapping], for cacheKey: String) {
        cacheLock.lock()
        emojiCache[cacheKey] = mappings
        cacheLock.unlock()
    }

    private func clearEmojiCaches() {
        cacheLock.lock()
        emojiCache.removeAll()
        cacheLock.unlock()
        emojiCategoryCacheLock.lock()
        emojiCategoryPagesCache = nil
        isEmojiCategoryPreloadInFlight = false
        emojiCategoryCacheLock.unlock()
    }

    // MARK: - Cache Initialisation (mirrors Java initialCache())

    /// Reinitialise all caches — mirrors Java SearchServer.initialCache().
    func initialCache() {
        scorelistLock.lock()
        scorelist.removeAll()
        scorelistLock.unlock()
        cacheLock.lock()
        mappingCache.removeAll()
        relatedCache.removeAll()
        blacklistCache.removeAll()
        cacheLock.unlock()
        coderemap.removeAll()
        englishCache.removeAll()
        clearEmojiCaches()
        keynameCache.removeAll()
        suggestionLock.lock()
        clearRunTimeSuggestion(abandonSuggestion: false)
        lastCode = ""
        suggestionLock.unlock()
        scorelistLock.lock()
        scorelist.removeAll()
        scorelistLock.unlock()
    }

    // MARK: - Clear (mirrors Java clear())

    /// Clear all runtime caches — mirrors Java SearchServer.clear().
    func clear() {
        scorelistLock.lock()
        scorelist.removeAll()
        scorelistLock.unlock()
        cacheLock.lock()
        mappingCache.removeAll()
        relatedCache.removeAll()
        blacklistCache.removeAll()
        cacheLock.unlock()
        englishCache.removeAll()
        clearEmojiCaches()
        keynameCache.removeAll()
        coderemap.removeAll()
        suggestionLock.lock()
        clearRunTimeSuggestion(abandonSuggestion: false)
        lastCode = ""
        suggestionLock.unlock()
        scorelistLock.lock()
        scorelist.removeAll()
        scorelistLock.unlock()
    }

    // MARK: - Longest Common Substring (mirrors Java lcs())

    /// Returns the longest common substring of two strings (recursive).
    func lcs(_ a: String, _ b: String) -> String {
        guard !a.isEmpty, !b.isEmpty else { return "" }
        if a.last == b.last {
            return lcs(String(a.dropLast()), String(b.dropLast())) + String(a.last!)
        }
        let x = lcs(a, String(b.dropLast()))
        let y = lcs(String(a.dropLast()), b)
        return x.count > y.count ? x : y
    }

    // MARK: - Cache Helpers (mirrors Java getMappingByCodeFromCacheOrDB / removeRemappedCodeCachedMappings / updateSimilarCodeCache)

    private func getMappingByCodeFromCacheOrDB(_ queryCode: String, getAllRecords: Bool) -> [Mapping] {
        // Cache key must match the format used by getMappingByCode (H7 fix):
        // "<table>:<lowercased-code>:<effectiveLimit>"
        let effectiveLimit = getAllRecords ? 210 : 50
        let key = "\(currentTableName):\(queryCode.lowercased()):\(effectiveLimit)"
        cacheLock.lock()
        if let cached = mappingCache[key] { cacheLock.unlock(); return cached }
        cacheLock.unlock()
        let results = db.getMappingByCode(queryCode, softKeyboard: true, getAllRecords: getAllRecords) ?? []
        cacheLock.lock()
        evictIfNeeded()
        if !results.isEmpty { mappingCache[key] = results }
        cacheLock.unlock()
        return results
    }

    /// Invalidates all cache entries that may hold mappings for `code`,
    /// regardless of whether they were stored under the raw-limit or all-records key (H7 fix).
    private func removeRemappedCodeCachedMappings(_ code: String) {
        let lowered = code.lowercased()
        let baseKey = "\(currentTableName):\(lowered)"
        let removeKey: (String) -> Void = { [weak self] key in
            guard let self = self else { return }
            self.mappingCache.removeValue(forKey: key)
            self.mappingCache.removeValue(forKey: "\(key):50")
            self.mappingCache.removeValue(forKey: "\(key):210")
        }
        cacheLock.lock()
        if let codelist = coderemap[baseKey] {
            for entry in codelist {
                removeKey("\(currentTableName):\(entry.lowercased())")
            }
        } else {
            removeKey(baseKey)
        }
        cacheLock.unlock()
    }

    /// Evicts cache entries for all prefix codes of `code` (up to length 5).
    /// Returns the prefix codes that were actually present in cache and evicted,
    /// so the caller can re-warm them without an extra dispatch hop.
    @discardableResult
    private func updateSimilarCodeCache(_ code: String) -> [String] {
        let len = min(code.count, 5)
        guard len > 1 else { return [] }
        var evictedPrefixes: [String] = []
        for k in 1..<len {
            let key = String(code.prefix(code.count - k))
            let cacheKeyBase = "\(currentTableName):\(key.lowercased())"
            cacheLock.lock()
            // Check all possible limit-suffix variants that getMappingByCode may store.
            let wasInCache = mappingCache[cacheKeyBase] != nil
                          || mappingCache["\(cacheKeyBase):50"] != nil
                          || mappingCache["\(cacheKeyBase):210"] != nil
            cacheLock.unlock()
            removeRemappedCodeCachedMappings(key)  // clears plain + :50 + :210 variants
            if wasInCache {
                evictedPrefixes.append(key)
            }
        }
        return evictedPrefixes
    }

    // MARK: - English Suggestions (mirrors Java getEnglishSuggestions())

    /// Returns English word suggestions for a given prefix, with caching.
    /// All shared-state reads/writes are protected by cacheLock (H6 fix).
    func getEnglishSuggestions(_ word: String) -> [Mapping] {
        cacheLock.lock()
        if word.count > 1, let last = lastEnglishWord,
           word.hasPrefix(last), noSuggestionsForLastEnglishWord {
            cacheLock.unlock()
            return []
        }
        if let cached = englishCache[word] {
            cacheLock.unlock()
            return cached
        }
        cacheLock.unlock()

        // Prefer the injected keyboard provider (UITextChecker); fall back to the DB query
        // for non-keyboard contexts (e.g. Settings) where no provider is set.
        let strings = englishCompletionProvider?(word) ?? (db.getEnglishSuggestions(word) ?? [])
        let result = strings.map { s -> Mapping in
            var m = Mapping(id: 0, code: "", word: s, score: 0, baseScore: 0)
            m.recordType = Mapping.RecordType.englishSuggestion
            return m
        }

        cacheLock.lock()
        noSuggestionsForLastEnglishWord = result.isEmpty
        lastEnglishWord = word
        if !result.isEmpty { englishCache[word] = result }
        cacheLock.unlock()
        return result
    }

    // MARK: - Delegation Methods (UI components call SearchServer, not LimeDB directly)

    func getAllImKeyboardConfigList() -> [LimeImConfigRow] {
        return db.getImConfigList(nil, "keyboard")
    }

    func getImConfig(_ imCode: String, _ field: String) -> String {
        return db.getImConfig(imCode, field) ?? ""
    }

    @discardableResult
    func setImConfig(_ imCode: String, _ field: String, _ value: String) -> Bool {
        db.setImConfig(imCode, field, value)
        return true
    }

    func setIMKeyboard(_ im: String, _ value: String, _ keyboard: String) {
        db.setIMConfigKeyboard(im, value, keyboard)
    }

    func setIMKeyboard(_ imCode: String, _ keyboard: KeyboardConfig) {
        db.setImConfigKeyboard(imCode, keyboard)
    }

    func countRecordsByWordOrCode(_ table: String, _ curQuery: String?, searchByCode: Bool) -> Int {
        var whereParts: [String] = []
        var args: [String] = []
        if let q = curQuery, !q.isEmpty {
            if searchByCode {
                whereParts.append("code LIKE ?")
                args.append(q + "%")
            } else {
                whereParts.append("word LIKE ?")
                args.append("%" + q + "%")
            }
        }
        whereParts.append("ifnull(word, '') <> ''")
        let whereClause = whereParts.joined(separator: " AND ")
        return db.countRecords(table, whereClause, args.isEmpty ? nil : args)
    }

    func countRecords(_ table: String) -> Int {
        let where_ = table == "related"
            ? "ifnull(pword,'') <> '' AND ifnull(cword,'') <> ''"
            : "ifnull(word,'') <> ''"
        return db.countRecords(table, where_, nil)
    }

    func countRecordsRelated(_ pword: String?) -> Int {
        var whereParts: [String] = []
        var args: [String] = []
        var searchPword = pword
        var cwordFilter = ""
        if let p = pword, p.count > 1 {
            cwordFilter = String(p.dropFirst())
            searchPword = String(p.prefix(1))
        }
        if let p = searchPword, !p.isEmpty { whereParts.append("pword = ?"); args.append(p) }
        if !cwordFilter.isEmpty { whereParts.append("cword LIKE ?"); args.append(cwordFilter + "%") }
        whereParts.append("ifnull(cword,'') <> ''")
        return db.countRecords("related", whereParts.joined(separator: " AND "), args.isEmpty ? nil : args)
    }

    func hasRelated(_ pword: String?, _ cword: String?) -> Bool {
        guard let p = pword, !p.isEmpty else { return false }
        var whereParts = ["pword = ?"]
        var args = [p]
        if let c = cword, !c.isEmpty {
            whereParts.append("cword = ?")
            args.append(c)
        } else {
            whereParts.append("cword IS NULL")
        }
        return db.countRecords("related", whereParts.joined(separator: " AND "), args) > 0
    }

    func getRelatedByWord(_ pword: String?, maximum: Int, offset: Int) -> [Related] {
        return db.getRelated(pword, maximum, offset)
    }

    func getImAllConfigList(_ code: String?) -> [LimeImConfigRow] {
        return db.getImConfigList(code, nil)
    }

    func isValidTableName(_ tableName: String) -> Bool {
        return db.isValidTableName(tableName)
    }

    func getKeyboard() -> [KeyboardConfig] {
        return db.getKeyboardConfigList() ?? []
    }

    func getKeyboardConfig(_ keyboard: String) -> KeyboardConfig? {
        return db.getKeyboardConfig(keyboard)
    }

    func detectIMCapabilities(tableName: String) -> (hasNumber: Bool, hasSymbol: Bool) {
        let lc = tableName.lowercased()
        let phoneticFamily = ["phonetic", "et26", "et_41", "eten", "hsu", "hs", "dayi", "ez"]
        if phoneticFamily.contains(where: { lc.hasPrefix($0) }) {
            return (hasNumber: true, hasSymbol: true)
        }
        return db.detectIMCapabilities(tableName: tableName)
    }

    func imKeysForTable(_ tableName: String) -> String {
        return db.imKeysForTable(tableName)
    }

    func getKeyboardInfo(_ keyboardCode: String, _ field: String) -> String? {
        return db.getKeyboardInfo(keyboardCode, field)
    }

    func getRecords(_ code: String, _ query: String?, searchByCode: Bool,
                    _ maximum: Int, _ offset: Int) -> [LimeRecord] {
        return db.getRecordList(code, query, searchByCode: searchByCode, maximum, offset)
    }

    func getRecord(_ code: String, _ id: Int64) -> LimeRecord? {
        return db.getRecord(code, id)
    }

    @discardableResult
    func addRecord(_ table: String, _ values: [String: Any?]) -> Int64 {
        return db.addRecord(table, values)
    }

    @discardableResult
    func deleteRecord(_ table: String, _ whereClause: String?, _ whereArgs: [String]?) -> Int {
        return db.deleteRecord(table, whereClause, whereArgs)
    }

    @discardableResult
    func updateRecord(_ table: String, _ values: [String: Any?],
                      _ whereClause: String?, _ whereArgs: [String]?) -> Int {
        return db.updateRecord(table, values, whereClause, whereArgs)
    }

    func clearTable(_ table: String) {
        db.clearTable(table)
        clearAllCaches()
    }

    func resetCache() {
        db.resetCache()
        clearAllCaches()
    }

    func checkPhoneticKeyboardSetting() {
        db.checkPhoneticKeyboardSetting()
    }

    func checkBackupTable(_ table: String) -> Bool {
        return db.checkBackupTable(table)
    }

    func getBackupTableRecords(_ backupTableName: String) -> [[String: Any]]? {
        return db.getBackupTableRecords(backupTableName)
    }

    func removeImInfo(_ im: String, _ field: String) {
        db.removeImConfig(im, field)
    }

    func resetImConfig(_ imCode: String) {
        db.resetImConfig(imCode)
    }

    func restoredToDefault() {
        db.restoredToDefault()
    }

    func addOrUpdateMappingRecord(_ table: String, _ code: String, _ word: String, _ score: Int) {
        db.addOrUpdateMappingRecord(table, code, word, score)
    }

    func getKeyboardConfigList() -> [KeyboardConfig] {
        return db.getKeyboardConfigList() ?? []
    }

    // MARK: - Remaining delegation methods (mirrors Java SearchServer)

    func backupUserRecords(_ table: String) {
        db.backupUserRecords(table)
    }

    @discardableResult
    func restoreUserRecords(_ table: String) -> Int {
        return db.restoreUserRecords(table)
    }

    @discardableResult
    func dropBackupTable(_ table: String) -> Bool {
        return db.dropBackupTable(table)
    }

    func getImConfigList(_ code: String?, _ configEntry: String?) -> [LimeImConfigRow] {
        return db.getImConfigList(code, configEntry)
    }

    func hanConvert(_ input: String, _ hanOption: Int) -> String {
        return db.hanConvert(input, hanOption)
    }

    func getTablename() -> String {
        return currentTableName
    }

    // MARK: - Test Hooks (internal — do not use in production code)
    internal var _testMappingCache:      [String: [Mapping]]                { mappingCache }
    internal var _testRelatedCache:      [String: [Mapping]]                { relatedCache }
    internal var _testBlacklistCache:    Set<String>                        { blacklistCache }
    internal var _testBestSuggestionStack: [(mapping: Mapping, code: String)] { bestSuggestionStack }
    /// Backwards-compat alias for tests written against the old suggestionContext API.
    /// addToSuggestionContext is now a no-op; state lives in bestSuggestionStack.
    internal var _testSuggestionContext: [Mapping] { bestSuggestionStack.map { $0.mapping } }
    /// Exposes db.learnRelatedWords for pref-wiring tests.
    internal var _testLearnRelatedWords: Bool { db.learnRelatedWords }
    /// Exposes db.similarCodeCandidatesCap for pref-wiring tests.
    internal var _testSimilarCodeCandidatesCap: Int { db.similarCodeCandidatesCap }
    /// Exposes ldPhraseListArray for postFinishInput path tests.
    internal var _testLdPhraseListArray: [[Mapping]] {
        learnLock.lock(); defer { learnLock.unlock() }
        return ldPhraseListArray
    }

    internal func _testSetRuntimeSuggestionList(_ list: [(mapping: Mapping, code: String)]) {
        suggestionLock.lock()
        suggestionLoL = [list]
        suggestionLock.unlock()
    }
}
