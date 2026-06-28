# iOS IMService Gap TODO

Derived from fresh gap analysis of `IM_SERVICE.md` vs. current implementation.
Last updated: 2026-04-09. All items complete.

Items are grouped by priority. Check off each item when done.

---

## High Priority — Blocks Correct IME Behavior

### Phonetic Code Remapping (spec §5)

- [x] Implement `preProcessingRemappingCode()` in `LimeDB`
- [x] ETEN-41 single remap table (`ETEN_KEY` → `ETEN_KEY_REMAP`)
- [x] ETEN-26 dual remap tables with position detection logic
- [x] HSU dual remap tables with position detection logic
- [x] Shifted-key remap (`SHIFTED_NUMBERIC_KEY`, `SHIFTED_SYMBOL_KEY`)
- [x] Cache remap tables per `(tableName + phoneticKeyboardType)`
- [x] Wire `preProcessingRemappingCode()` into `getMappingByCode()` for phonetic tables

### Switch to Next/Previous IM (spec §10)

- [x] Load activated IM list from `keyboard_state` preference in `KeyboardViewController`
- [x] Implement `switchToNextActivatedIM(forward:)`
- [x] Call `searchServer.setTableName()` when switching IM
- [x] Update keyboard layout and reset composing/candidates on IM switch
- [x] Route `KEYCODE_NEXT_IM` / `KEYCODE_PREV_IM` in `onKey()`

### Han Conversion (spec §8)

- [x] `CFStringTransform(kCFStringTransformToSimplifiedChinese / kCFStringTransformToTraditionalChinese)` in `commitTyped()`
- [x] Read `hanConvertOption` setting (0 = off, 1 = to Simplified, 2 = to Traditional)

### `keyToKeyname()` Display (spec §5)

- [x] `keyToKeyname()` in `SearchServer` calls `db.keyToKeyName()` with per-IM lookup tables
- [x] Keyname shown in composing bar

### Stroke5 (WB) Length Limit (spec §5)

- [x] Detect WB/Stroke5 keyboard via `isWBTable` on `SearchServer`
- [x] Discard the 6th character in `handleCharacter()`
- [x] Truncate composing to 5 chars in `updateCandidates()` before querying

---

## Medium Priority — Visible Feature Gaps

### Symbol Keyboard (spec §4, §10)

- [x] Load symbol keyboard layout — `lime_number_symbol.json` (layout file exists in bundle)
- [x] Implement `switchToSymbol()` — push symbol layout, reset composing
- [x] Route `KEYCODE_SWITCH_TO_SYMBOL_MODE` in `onKey()`
- [x] Route `KEYCODE_SWITCH_SYMBOL_KEYBOARD` to cycle symbol keyboard pages

### Selkey / Numbered Selection Keys (spec §6)

- [x] `selkeyOption` setting is read
- [x] `tryPickBySelkey()` routes number keypresses to candidates when `selkeyOption > 0`
- [x] Display numbered selection keys (1–9/0) above candidates in `CandidateBarView`

### Chinese Punctuation / Auto Chinese Symbol (spec §11)

- [x] `hasChineseSymbolCandidatesShown` state variable
- [x] `chinesePunctuationMappings()` returns standard punctuation set (inlined in `KeyboardViewController`)
- [x] `clearSuggestions()` loads punctuation when `autoChineseSymbol` + Chinese mode + candidates were shown
- [x] `handleBackspace()` Case 4: hide punctuation view without deleting
- [x] Punctuation candidates tagged with `RecordType.chinesePunctuation`

### Emoji Injection (spec §6, §13)

- [x] `Database/emoji.db` copied from Android source (`LimeStudio/app/src/main/res/raw/emoji.db`)
- [x] Bundled with keyboard extension via `project.yml` preBuildScripts
- [x] `LimeDB.emojiConvert(_:_:)` queries `emoji.db` tables (`en`/`tw`/`cn`) by `tag` column
- [x] `SearchServer.injectEmoji(into:code:type:insertAt:)` injects and deduplicates emoji candidates
- [x] Inject emoji at position 3 in `updateCandidates()`
- [x] Inject emoji (English set) in `updateEnglishPrediction()`

### Globe Key / `needsInputModeSwitchKey` (spec §10)

- [x] `updateGlobeKeyVisibility()` called in `viewWillLayoutSubviews`
- [x] Globe button shown/hidden via `setGlobeKeyVisible(_:)` based on `needsInputModeSwitchKey`
- [x] Globe key long-press → UIAlertController options menu with `advanceToNextInputMode()` and per-IM switch entries

### Space Key Gestures (spec §10)

- [x] Slide-left → `switchToNextActivatedIM(forward: false)`
- [x] Slide-right → `switchToNextActivatedIM(forward: true)`
- [x] Long-press → `advanceToNextInputMode()`

### Feedback: Vibration and Sound (spec §15)

- [x] `hasVibration` wired to `UIImpactFeedbackGenerator` on every key press
- [x] `hasSound` wired to system default click / custom-volume LIME click on every key press

---

## Low Priority — Correctness and Edge Cases

### `LDComposingBuffer` Tracking (spec §5, §8)

- [x] `LDComposingBuffer: String` state variable
- [x] Accumulates across continuous-typing commits
- [x] `addLDPhrase(candidate, ending: true)` on composing finish
- [x] `addLDPhrase(nil, ending: true)` on force-clear (LD interrupt)
- [x] `LDComposingBuffer` cleared after signalling end

### Unicode Surrogate / Emoji Commit (spec §8)

- [x] `containsEmojiSurrogatePair()` check in `commitTyped()`
- [x] Force-clear composing after emoji commit

### Special Clear After Commit (spec §8)

- [x] `clearComposing(force: true)` after WB candidate commit
- [x] `clearComposing(force: true)` after emoji candidate commit
- [x] `clearComposing(force: true)` after Chinese punctuation candidate commit

### Reverse Lookup Notification (spec §8, §13)

- [x] `getCodeListStringFromWord()` wrapper on `SearchServer`
- [x] Called in `commitTyped()` on background thread
- [x] Result briefly shown in composing-code label via `showToast()` (2-second display)

### Missing `Mapping` Fields (spec §14)

- [x] `codeorig: String?` added and populated in primary `getMappingByCode()` query
- [x] `pword: String?` added to struct and populated in `getRelatedMappings()` / `getRelatedPhraseList()`
- [x] `related: String?` populated from the `related` TEXT column present in all mapping tables (phonetic, wb, cj, array, dayi, custom)
- [x] `highLighted: Bool` added to struct

### Settings Wiring (spec §15)

- [x] All settings read from shared `UserDefaults(suiteName: "group.org.limeime")`
- [x] `hanConvertOption`, `autoChineseSymbol`, `learnPhrase`, `englishPredictionOn`, `selkeyOption`, `phonetic_keyboard_type`, `keyboard_state`, `auto_commit`, `mPersistentLanguageMode` all actively used
- [x] `sortSuggestions` passed to `SearchServer` and applied to candidate ordering
- [x] `hasVibration` / `hasSound` wired to `KeyboardView` feedback generators
- [x] `smartChineseInput` gates runtime phrase suggestion — chain depth capped at 3 to bound memory

### Related Phrase Display and Chaining (spec §8, §9)

- [x] `updateRelatedPhrase()` called after `commitTyped()` when composing is finished (not from candidate tap handler)
- [x] `hasMappingList` guard — related phrase query skipped if no DB mapping results were ever shown in current session
- [x] Related phrase chaining — selecting from the related phrase list re-enters Path 2 commit (candidate `!isComposingCodeRecord && mComposing.isEmpty`), triggering another `updateRelatedPhrase()` call
- [x] Empty-result path — when `getRelatedByWord()` returns empty: clear `committedCandidate` and call `clearSuggestions()`
- [ ] `learnRelatedWord` (`candidate_suggestion`) preference gate — RP learning in `SearchServer.learnRelatedPhraseAndUpdateScore()` must be skipped when `candidate_suggestion` is `false`; currently runs unconditionally
- [ ] RP learning pair record-type guard — spec §9 requires `unit` to be exact/partial/related record and `unit2` to be exact/partial/related/punctuation/emoji; current `isRealCandidate` check is broader (also allows English suggestions and runtimeBuiltPhrase as valid pair members)

### Runtime Phrase Suggestion (spec §6, §13)

- [x] `makeRunTimeSuggestion()` in `SearchServer`: for each candidate in `currentList`, checks whether it forms a valid phrase pair with any previously-committed word via the `related` table; matching candidates promoted to top of list
- [x] `suggestionContext: [(Mapping, code)]` tracks committed candidates with their typed code
- [x] `addToSuggestionContext(_:code:)` called after each commit when `smartChineseInput` is on
- [x] `clearSuggestionContext()` on composing restart (length == 1)
- [x] Results tagged as `RecordType.runtimeBuiltPhrase`
- [x] Gated behind `smartChineseInput` setting

### Initialization Edge Cases (spec §2)

- [x] `traitCollectionDidChange` overridden — refreshes keyboard layout on theme change
- [x] `traitCollectionDidChange` — cancels composing and reapplies height on size class change

---

## Not Applicable to iOS (excluded per spec §15 note)

- Physical keyboard settings — soft keyboard only
- `getEnglishSuggestions()` via DB dictionary — replaced by `UITextChecker`
- `hanConvert()` via `hanconvertv2.db` — replaced by `CFStringTransform`
