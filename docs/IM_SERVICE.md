# IMService Specification for iOS Porting

This document extracts the behavioral logic of LimeIME's input method service from the Android Java sources and presents it as a platform-independent specification. It covers the complete pipeline from key event to composing to search to candidates to commit to learning.

**Source files analyzed**: `LIMEService.java`, `SearchServer.java`, `LIMEKeyboardSwitcher.java`, `CandidateView.java`, `LimeDB.java`, `Mapping.java`, `LIME.java`, `LIMEPreferenceManager.java`

---

## 1. Overview

IMService is the **bridge** between the Virtual Keyboard, the SearchServer, and the operating system's text input API. It receives key events from the keyboard, builds composing code sequences, queries SearchServer for candidates, manages candidate selection, commits text to the active text field, and coordinates the learning flow.

```
Virtual Keyboard
    │ key events (onKey)
    ▼
IMService
    │ composing code → query
    ▼
SearchServer
    │ candidate list
    ▼
IMService
    │ display candidates
    ▼
CandidateView
    │ user picks candidate
    ▼
IMService
    │ commit text → OS text API
    │ learn score/phrase → SearchServer
    ▼
OS Text Input (Android: InputConnection / iOS: textDocumentProxy)
```

---

## 2. Initialization

When a text field gains focus, `initOnStartInput()` configures the IMService based on the text field type.

### Text Field Type → Configuration

| Input Type | `mEnglishOnly` | `mPredictionOn` | Keyboard Mode |
|------------|----------------|-----------------|---------------|
| NUMBER, DATETIME | true | true | MODE_TEXT (symbol keyboard) |
| PHONE | true | true | MODE_PHONE |
| PASSWORD, WEB_PASSWORD, VISIBLE_PASSWORD | true | false | MODE_EMAIL |
| EMAIL_ADDRESS, WEB_EMAIL_ADDRESS | true | false | MODE_EMAIL |
| URI | true | false | MODE_URL |
| SHORT_MESSAGE | false | true | MODE_IM (Chinese) |
| TEXT (default) | from persistent setting | true | MODE_TEXT or Chinese IM |
| TEXT with NO_SUGGESTIONS | — | false | — |
| TEXT with AUTO_COMPLETE | — | false | completion mode in fullscreen |

### Initialization Steps

1. Check keyboard theme change — recreate input view if changed
2. Load IM keyboard config list from SearchServer
3. Reset keyboards if arrow key or split keyboard setting changed
4. Load user settings (`hasVibration`, `hasSound`, `mPersistentLanguageMode`)
5. Build activated IM list from preferences
6. Reset composing state: `mPredictionOn=true`, `mCompletionOn=false`, `mCapsLock=false`
7. Clear `tempEnglishWord` and `tempEnglishList`
8. Apply text field type configuration (table above)
9. If English-only and no prediction → force hide candidate view
10. Else → clear composing

### iOS Equivalent

iOS `UIInputViewController` receives `textDocumentProxy.keyboardType` (`.default`, `.emailAddress`, `.URL`, `.numberPad`, `.phonePad`, etc.) and `textDocumentProxy.returnKeyType`. Map these to the same configuration table. iOS does not have composing region styling, so `mPredictionOn` controls whether composing simulation is active.

**iOS Porting Note — Auto-capitalization**: LimeIME manually implements auto-capitalization via `updateShiftKeyState()` checking EditorInfo flags. iOS provides `textDocumentProxy.autocapitalizationType` (`.none`, `.words`, `.sentences`, `.allCharacters`) which the keyboard extension can read directly. Use this instead of manual implementation.

---

## 3. State Model

### Composing State

| Variable | Type | Purpose |
|----------|------|---------|
| `mComposing` | StringBuilder | Accumulates typed code characters |
| `LDComposingBuffer` | String | Tracks original composing string during continuous typing (LD) |
| `mPredictionOn` | boolean | Whether composing/prediction is active |
| `mCompletionOn` | boolean | Whether app-provided completions are active |

### Candidate State

| Variable | Type | Purpose |
|----------|------|---------|
| `selectedCandidate` | Mapping | Currently highlighted candidate |
| `committedCandidate` | Mapping | Last committed candidate (for related phrase lookup) |
| `mCandidateList` | LinkedList\<Mapping\> | Current displayed candidate list |
| `hasCandidatesShown` | boolean | Whether candidate view is visible |
| `hasChineseSymbolCandidatesShown` | boolean | Whether Chinese punctuation candidates are shown |
| `hasMappingList` | boolean | Whether current list has database mappings |

### Mode State

| Variable | Type | Purpose |
|----------|------|---------|
| `mEnglishOnly` | boolean | true = English mode, false = Chinese IM mode |
| `mEnglishFlagShift` | boolean | Shift key flag for English/Chinese toggle |
| `activeIM` | String | Current input method code (e.g., "phonetic", "cj", "dayi") |
| `hasSymbolMapping` | boolean | Whether current IM accepts symbol-key codes |
| `hasNumberMapping` | boolean | Whether current IM accepts number-key codes |
| `currentSoftKeyboard` | String | Current soft keyboard configuration name |
| `mCapsLock` | boolean | Caps lock state |
| `mAutoCap` | boolean | Auto-capitalization enabled |

### English Prediction State

| Variable | Type | Purpose |
|----------|------|---------|
| `tempEnglishWord` | StringBuffer | Accumulates English characters for prediction |
| `tempEnglishList` | List\<Mapping\> | English prediction suggestions |

### Learning State

| Variable | Type | Purpose |
|----------|------|---------|
| `auto_commit` | int | Auto-commit threshold (0 = off) |

---

## 4. Key Event Dispatch

Entry point: `onKey(int primaryCode, int[] keyCodes, int x, int y)`

### CapsLock Pre-processing

If `mCapsLock` is true and code is lowercase letter (97–122), convert to uppercase (subtract 32).

### Special Key Routing

| Key Code | Action |
|----------|--------|
| `KEYCODE_DELETE` | `handleBackspace()` |
| `KEYCODE_SHIFT` | `handleShift()` |
| `KEYCODE_DONE` | `handleClose()` |
| `KEYCODE_UP/DOWN/LEFT/RIGHT` | Arrow key via `keyDownUp()` |
| `KEYCODE_OPTIONS` | `handleOptions()` |
| `KEYCODE_SPACE_LONGPRESS` | `showIMPicker()` — show input method picker |
| `KEYCODE_SWITCH_TO_SYMBOL_MODE` | `switchKeyboard()` — switch to symbol keyboard |
| `KEYCODE_SWITCH_SYMBOL_KEYBOARD` | `switchKeyboard()` — cycle symbol keyboards (1/2/3) |
| `KEYCODE_NEXT_IM` | `switchToNextActivatedIM(true)` — next Chinese IM |
| `KEYCODE_PREV_IM` | `switchToNextActivatedIM(false)` — previous Chinese IM |
| `KEYCODE_SWITCH_TO_ENGLISH_MODE` | `switchKeyboard()` — Chinese → English |
| `KEYCODE_SWITCH_TO_IM_MODE` | `switchKeyboard()` — English → Chinese |

### Space / Enter Handling

Conditions for candidate picking via Space:
- Space key AND not English-only AND not Phonetic IM
- OR: Space key AND not English-only AND Phonetic IM AND (composing ends with space OR composing is empty)
- OR: Enter key

If candidates are shown → `pickHighlightedCandidate()`. If pick fails and composing is empty → hide candidate view and send the key character. If no candidates shown → `sendKeyChar()`.

### Default Character Handling

All other keys → `handleCharacter(primaryCode)`, followed by auto-commit check:
- If `auto_commit > 0` AND not English-only AND composing length == `auto_commit` AND current keyboard is phonetic → `commitTyped()`.

---

## 5. Composing Flow

### Character Acceptance Rules

`handleCharacter()` determines whether to add a character to the composing buffer based on the current IM's mapping capabilities. The character is accepted into composing if it matches one of these conditions:

| Condition | Accepted Characters |
|-----------|-------------------|
| No symbol mapping, no number mapping | Letters, Space (phonetic only), comma, period |
| No symbol mapping, has number mapping | Letters, digits |
| Has symbol mapping, no number mapping | Letters, symbols, Space (phonetic only) |
| Has symbol mapping, no number mapping, Array IM | Digits after "w" prefix (Array special code) |
| Has symbol mapping, has number mapping | Letters, digits, symbols, Space (phonetic only) |

When a character is accepted:
1. `mComposing.append((char) primaryCode)`
2. `ic.setComposingText(mComposing, 1)` — display composing in text field
3. `updateCandidates()` — query SearchServer for candidates

When a character is NOT accepted (doesn't match any condition):
1. `pickHighlightedCandidate()` — commit current candidate if any
2. `ic.commitText(String.valueOf((char) primaryCode), 1)` — send character directly
3. `finishComposing()` — clear composing state

### English Mode Character Handling

When `mEnglishOnly` is true:
1. Apply shift if keyboard is shifted → `Character.toUpperCase(primaryCode)`
2. If English prediction is enabled and character is a letter → append to `tempEnglishWord`, call `updateEnglishPrediction()`
3. If not a letter → `resetTempEnglishWord()`, call `updateEnglishPrediction()`
4. `commitText(String.valueOf((char) primaryCode), 1)` — commit character immediately (no composing buffer in English mode)

### Backspace Handling

`handleBackspace()` behavior depends on composing length:

| Composing Length | Action |
|-----------------|--------|
| > 1 | Delete last character from `mComposing`, update composing text, re-query candidates |
| == 1 | `clearComposing(true)` — clear composing and force-clear system buffer |
| == 0, candidates shown (not Chinese symbol) | `clearComposing(false)` — clear candidates |
| == 0, candidates shown (Chinese symbol) | Hide candidate view |
| == 0, English mode with prediction | Delete last char from `tempEnglishWord`, re-query English predictions |
| == 0, no candidates | Send `KEYCODE_DEL` to delete in text field |

### Stroke5 (WB) Length Limit

For Stroke5 input method, composing is truncated to maximum 5 characters. If the user types a 6th character, it is discarded and composing is reset to the first 5.

### Keyname Display

After querying candidates, `keyToKeyname(code)` converts the typed code sequence to display-friendly symbol names. If the converted string differs from the original code, it is shown in the composing bar above the candidate list.

Examples:
- Phonetic: "bp" → "ㄅㄆ"
- Cangjie: "ab" → "日月"
- Dayi: typed shifted keys → Dayi radical names

This is cached per code in `keynamecache` for performance.

### Phonetic Keyboard Code Remapping

Different phonetic keyboard layouts map the same QWERTY key to different Zhuyin (Bopomofo) symbols. `preProcessingRemappingCode()` in `LimeDB` translates the typed key sequence to the canonical phonetic code before database lookup.

**Standard Phonetic**: No remapping. Keys map directly to Zhuyin (defined in keyboard XML labels).

**ETEN 41-key**: Single remap table. Each QWERTY key maps to one Zhuyin symbol regardless of position.
- Remap: `ETEN_KEY` → `ETEN_KEY_REMAP` (character-by-character substitution)

**ETEN 26-key**: Dual remap tables — `INITIAL` and `FINAL` — because some keys map to different Zhuyin depending on whether they appear at a non-final or final position in the syllable.
- Non-final position: `ETEN26_KEY` → `ETEN26_KEY_REMAP_INITIAL`
- Final position: `ETEN26_KEY` → `ETEN26_KEY_REMAP_FINAL`
- Exception: keys "q", "w", "d", "f", "j", "k" always use INITIAL remap even when single character
- Position detection: if preceding characters match `[dfjk ]$`, the next character uses INITIAL (new syllable starting)

**HSU**: Dual remap tables with similar logic.
- Non-final position: `HSU_KEY` → `HSU_KEY_REMAP_INITIAL`
- Final position: `HSU_KEY` → `HSU_KEY_REMAP_FINAL`
- Exception: keys "a", "e", "s", "d", "f", "j" always use INITIAL remap when single character
- Position detection: if preceding characters match `[sdfj ]$`, the next character uses INITIAL

**Shifted Key Remapping** (also applies to other IMs):
- Dayi, EZ, standard Phonetic: `SHIFTED_NUMBERIC_KEY` ("!@#$%^&*()") → `SHIFTED_NUMBERIC_KEY_REMAP` ("1234567890") and `SHIFTED_SYMBOL_KEY` ("<>?_:+\"") → `SHIFTED_SYMBOL_KEY_REMAP` (",./-;='")
- Array: `SHIFTED_SYMBOL_KEY` → `SHIFTED_SYMBOL_KEY_REMAP` only

Remap tables are cached per `(tableName + phoneticKeyboardType)` combination in a `HashMap<String, HashMap<String, String>>`.

### Auto-Commit

Checked after every `handleCharacter()` call:
- Condition: `auto_commit > 0` AND not English-only AND `mComposing.length() == auto_commit` AND current keyboard is phonetic (keyboard name contains "phone")
- Action: `commitTyped(ic)` — automatically commits the best candidate

### Continuous Typing (LD Composing)

When the user types more code characters than needed for the first matched word, the system supports continuous typing without requiring the user to explicitly select each candidate:

1. After `commitTyped()`, check: `mComposing.length() > selectedCandidate.getCode().length()`
2. If true → `composingNotFinish = true`
3. Determine real code length via `getRealCodeLength(selectedCandidate, mComposing)`:
   - For phonetic: strips tone symbols [3467 ] from the matched code to find the boundary
   - For ETEN: applies `preProcessingRemappingCode()` before boundary detection
   - If code is dual-mapped: uses full composing length (abandons LD)
4. Trim committed code from composing: `mComposing.delete(0, committedCodeLength)`
5. Strip leading space if present
6. Buffer the mapping for LD learning: `SearchSrv.addLDPhrase(selectedCandidate, false)`
7. If remaining composing > 0: `ic.setComposingText(mComposing, 1)` and `updateCandidates()` — re-query for remaining code
8. Track full original composing in `LDComposingBuffer`

When composing finally finishes (no remaining code):
- If `LDComposingBuffer` is not empty → `SearchSrv.addLDPhrase(selectedCandidate, true)` — final mapping, triggers LD learning
- If `LDComposingBuffer` is empty (LD interrupted) → `SearchSrv.addLDPhrase(null, true)` — signal end without learning

---

## 6. Candidate Flow

### Query Pipeline

`updateCandidates()` runs on a background thread:

1. **Stroke5 length check**: If WB keyboard, truncate composing to 5 characters
2. **Main query**: `SearchSrv.getMappingByCode(code, isSoftKeyboard, getAllRecords)`
   - `code`: current composing string
   - `isSoftKeyboard`: true for soft keyboard (affects candidate ordering)
   - `getAllRecords`: false for initial query, true for "show all" request
3. **Thread yield**: Short sleep to allow interruption by newer query
4. **Selection key setup**:
   - Get base selkey from `SearchSrv.getSelkey()` (typically "1234567890" or IM-specific)
   - Determine `mixedModeSelkey`: space (" ") for symbol-mapping IMs (except Dayi and standard phonetic), backtick ("`") for others
   - Apply `selkeyOption`: 0 = no prepend, 1 = prepend mixedModeSelkey, 2 = prepend mixedModeSelkey + space
5. **Emoji injection** (if enabled):
   - Try English emoji lookup on first candidate word
   - If no English match, try Traditional Chinese (TW) lookup on second candidate
   - If no TW match, try Simplified Chinese (CN) lookup
   - Deduplicate emoji by word
   - Insert at configurable position (`getEmojiDisplayPosition()`, default: 6)
6. **Display candidates**: `setSuggestions(list, hasPhysicalKeyPressed, selkey)`
7. **Keyname display**: Convert code to display symbols via `keyToKeyname()`, show in composing bar if different from raw code

If query returns empty → `clearSuggestions()` (which may trigger Chinese punctuation display if `autoChineseSymbol` is enabled).

### Default Candidate Selection

In `setSuggestions()`:
- If candidate at index 1 exists and `isExactMatchToCodeRecord()` → select index 1 (skip the composing code at index 0)
- Else → select index 0

### Runtime Phrase Suggestion

When `smartChineseInput` is enabled, `makeRunTimeSuggestion()` builds phrase candidates incrementally:

1. Maintains `suggestionLoL` — a list of lists of `Pair<Mapping, code>` representing candidate phrase chains
2. As user types more characters, extends existing suggestions by combining previous best matches with new exact matches for the remaining code
3. Validates combinations against the `related` table (phrase must exist as a related word pair)
4. Scores combinations by base score, averaged over word length
5. Best suggestion list is kept at the last position of `suggestionLoL`
6. Results are tagged as `RECORD_RUNTIME_BUILT_PHRASE`
7. On backspace: removes suggestions that used the deleted character's code
8. On composing restart (length == 1): clears all suggestions

### Thread Interruption

Each `updateCandidates()` call checks if a previous query thread is alive and interrupts it. The query thread checks for interruption at yield points and terminates early if interrupted. This prevents stale results from overwriting newer queries.

---

## 7. English Prediction Flow

A parallel candidate pipeline active when `mEnglishOnly` is true and `englishPrediction` is enabled.

### Accumulation

- Letter character → append to `tempEnglishWord`, call `updateEnglishPrediction()`
- Non-letter character → `resetTempEnglishWord()`, call `updateEnglishPrediction()`
- Backspace → delete last char from `tempEnglishWord`, call `updateEnglishPrediction()`

### Query Pipeline

`updateEnglishPrediction()` runs on a background thread:

1. **Cursor context validation**:
   - Check text after cursor: must be empty or non-alphanumeric
   - Check text before cursor: must match `tempEnglishWord` (prevents stale predictions when cursor moved)
2. **Query**: `SearchSrv.getEnglishSuggestions(tempEnglishWord)`
3. **Build candidate list**: Insert `tempEnglishWord` itself as first item (composing code record), then add suggestions
4. **Emoji injection**: Same logic as Chinese flow but English-only lookup (`EMOJI_EN`)
5. **Store in `tempEnglishList`**: Used later for commit handling
6. **Display**: `setSuggestions(list, hasPhysicalKeyPressed, "1234567890")`

If `tempEnglishWord` is empty or query returns empty → `clearSuggestions()`.

### iOS Porting: Use Built-in APIs Instead of Custom Dictionary

iOS provides built-in English word completion that keyboard extensions can use, eliminating the need to port LimeIME's custom English dictionary and prediction logic.

**`UITextChecker`** — Apple's word completion and spell checking API:
- `completions(forPartialWordRange:in:language:)` — returns word completions for a partial string
- `guesses(forWordRange:in:language:)` — returns spelling correction suggestions
- Available to keyboard extensions without "Allow Full Access"
- Supports multiple languages

**`UILexicon`** — personalized vocabulary (via `requestSupplementaryLexicon()` on `UIInputViewController`):
- Contains words from user's contacts, text shortcuts, and paired device vocabulary
- Returns `UILexiconEntry` objects with `userInput` → `documentText` mappings
- Supplements `UITextChecker` with personalized vocabulary

**What to drop on iOS:**
- English dictionary data in `lime.db`
- `SearchServer.getEnglishSuggestions()` implementation
- `tempEnglishWord` / `tempEnglishList` state tracking
- Cursor context validation logic (`getTextBeforeCursor`/`getTextAfterCursor` matching)

**What to keep:**
- Emoji injection on top of `UITextChecker` results (wrap results as `Mapping` objects with `RECORD_ENGLISH_SUGGESTION` type, then apply the same emoji lookup)
- Candidate display integration (feed `UITextChecker` completions into the candidate bar)

**What iOS does NOT provide:** The system's full predictive text engine (the one powering the built-in keyboard). Third-party extensions cannot access that. `UITextChecker` provides word completion (prefix matching) and spell correction, which is functionally equivalent to `getEnglishSuggestions()`.

---

## 8. Candidate Selection and Commit

### Selection Entry Points

- **`pickHighlightedCandidate()`**: Called by Space/Enter key. Delegates to `mCandidateView.takeSelectedSuggestion()` which calls back `pickCandidateManually(index)`.
- **`pickCandidateManually(int index)`**: Called when user taps a candidate or via selection key. Sets `selectedCandidate = mCandidateList.get(index)`, then dispatches to one of the three commit paths below.

### Three Commit Paths

**Path 1 — Completion Suggestion** (fullscreen mode with app completions):
- Condition: `mCompletionOn` and candidate `isPartialMatchToCodeRecord()`
- Action: `ic.commitCompletion(mCompletions[index])`

**Path 2 — Chinese / Related Phrase Candidate**:
- Condition: `mComposing.length() > 0` OR candidate is not a composing code record (i.e., it's a related phrase or database match); AND not English-only mode
- Action: `commitTyped(ic)` (see detailed flow below)
- **After commitTyped returns, `updateRelatedPhrase(false)` is called from inside `commitTyped()` post-commit block (not from `pickCandidateManually` directly) — see step 7 of commitTyped() Detailed Flow.**

**Path 3 — English Prediction**:
- Condition: English-only mode with prediction
- If emoji: `ic.commitText(word + " ", 0)`
- If regular word: `ic.commitText(word.substring(tempEnglishWord.length()) + " ", 0)` — commits only the suffix not yet typed, plus a space
- Then: `resetTempEnglishWord()`, `clearSuggestions()`

**Related Phrase Chaining**: When the user selects a candidate from the related phrase list, the same commit path (Path 2) runs again, because the candidate is not a composing code record and `mComposing` is empty (condition holds). `updateRelatedPhrase(false)` fires again after each related phrase commit, displaying the next level of associated words. This chaining continues as long as the `related` table has entries for each successive committed word.

### commitTyped() Detailed Flow

1. **Null check**: If `selectedCandidate` is null, return
2. **Surrogate check**: If word contains Unicode surrogate (emoji), commit directly and clear composing
3. **Han conversion**: If `hanConvertOption` != 0, apply `SearchSrv.hanConvert(word)` before committing.
   - **iOS Porting Note**: LimeIME uses `hanconvertv2.db` for simple 1-to-1 character-by-character lookup (no context-dependent conversion). iOS provides `CFStringTransform` with `kCFStringTransformToSimplifiedChinese` / `kCFStringTransformToTraditionalChinese` which is functionally equivalent or better — uses Apple's maintained mapping, no need to ship `hanconvertv2.db`. Drop: `LimeHanConverter` class and `hanconvertv2.db`. Replace with a single `CFStringTransform()` call.
4. **Commit text**: `ic.commitText(wordToCommit, 1)`
5. **Special clear**: If Stroke5 (wb), emoji, or Chinese punctuation → `clearComposing(true)`
6. **Continuous typing check** (LD composing):
   - Get `committedCodeLength` via `SearchSrv.getRealCodeLength(selectedCandidate, mComposing)`
   - If `mComposing.length() > selectedCandidate.getCode().length()` → composing not finished:
     - Buffer mapping: `SearchSrv.addLDPhrase(selectedCandidate, false)`
     - Trim composing: `mComposing.delete(0, committedCodeLength)`
     - Strip leading space if present
     - If remaining composing > 0 → `ic.setComposingText(mComposing, 1)` + `updateCandidates()`
   - If composing finished:
     - If `LDComposingBuffer` not empty → `SearchSrv.addLDPhrase(selectedCandidate, true)` (final)
     - Else → `SearchSrv.addLDPhrase(null, true)` (interrupted)
7. **Post-commit** (only if composing finished, no continuous typing):
   - `committedCandidate = new Mapping(selectedCandidate)` — save for related phrase
   - `clearComposing(false)`
   - `updateRelatedPhrase(false)` — display related phrases as next candidates
   - `SearchSrv.learnRelatedPhraseAndUpdateScore(committedCandidate)` — learn score + related
   - `SearchSrv.getCodeListStringFromWord(committedCandidate.getWord())` — reverse lookup

### Runtime Phrase Learning on Commit

When a `RUNTIME_BUILT_PHRASE` candidate is selected, `getRealCodeLength()` spawns a background thread to learn all sub-phrases from `bestSuggestionList`:
- For each sub-phrase where the selected word starts with the sub-phrase word:
  - If word length > 8 → stop learning
  - `dbadapter.addOrUpdateMappingRecord(code, word)` — save as new mapping
  - `removeRemappedCodeCachedMappings(code)` — invalidate cache

### Related Phrase Display

`updateRelatedPhrase(boolean getAllRecords)` runs after commit on a background thread:
1. Check `committedCandidate` has a non-empty word — return if null/empty
2. Check `hasMappingList` is true — return if no mapping results were ever displayed (nothing to relate against)
3. Skip query if committed candidate is emoji or Chinese punctuation — call `clearSuggestions()` and return
4. Query: `SearchSrv.getRelatedByWord(committedCandidate.getWord(), getAllRecords)` with `getAllRecords = false` (top results only)
5. **If results non-empty**: Display as candidate list with selkey `"1234567890"` via `setSuggestions()`
6. **If results empty**: Set `committedCandidate = null`, call `clearSuggestions()` — candidate bar returns to empty state

**`getAllRecords` parameter**: Always `false` when called from `commitTyped()`. The `true` variant is only used for "show all" expansion requests, not after normal commits.

---

## 9. Learning Flow

Learning has **two timing tiers**:
- **Immediate (per-commit)**: Score cache update — fires a background thread on every candidate selection to update in-memory ordering and persist to DB.
- **Deferred (session-end)**: Related phrase (RP) learning and LD phrase learning — processed in a single background thread when `postFinishInput()` is called (text field loses focus).

### Tier 1 — Immediate Score Update (per-commit)

`learnRelatedPhraseAndUpdateScore(committedCandidate)` is called from `commitTyped()` step 7 immediately after every commit:

1. **Thread-safe enqueue**: Adds a copy of `committedCandidate` to the synchronized `scorelist` (used later by `postFinishInput()`).
2. **Spawn background thread** to call `updateScoreCache(mapping)`:
   - Calls `dbadapter.addScore(mapping)` — persists the incremented score to the IM table in DB.
   - **For related phrase records** (`isRelatedPhraseRecord()`): Removes the cache entry for that code, forcing a fresh DB query next time.
   - **For partial match records** (`isPartialMatchToCodeRecord()`): Removes the whole cache entry for that code.
   - **For exact match records** (`isExactMatchToCodeRecord()`): If `sortSuggestions` is enabled, finds the matching entry in the in-memory cache list, increments its score, and moves it up if its new score exceeds the preceding entry (insertion-sort step). If `sortSuggestions` is disabled, still increments the cached score but doesn't reorder. Then calls `updateSimilarCodeCache(code)` to rebuild related-code (similar-code prefix) caches.

### Tier 2 — Deferred Learning (session-end via `postFinishInput()`)

`postFinishInput()` is called when the text field loses focus (input session ends). It spawns a single background thread that runs two learners in sequence, operating on snapshots to avoid locking the input thread:

1. **Snapshot `scorelist`**: Thread-safely copies `scorelist` into `scorelistSnapshot` and clears `scorelist`.
2. **Run `learnRelatedPhrase(scorelistSnapshot)`** — see RP Learning below.
3. **Snapshot `LDPhraseListArray`**: Copies into a local list and clears the global array.
4. **Run `learnLDPhrase(localLDPhraseListArray)`** — see LD Learning below.

### Related Phrase (RP) Learning (deferred)

`learnRelatedPhrase(localScorelist)` runs inside `postFinishInput()`'s background thread:

- **Guard**: `mLIMEPref.getLearnRelatedWord()` must be enabled AND `localScorelist.size() > 1` (need at least two consecutive selections to form a pair).
- Iterates consecutive pairs `(unit at i, unit2 at i+1)` in the scorelist.
- **Pair acceptance conditions**:
  - Both `unit.word` and `unit2.word` are non-empty.
  - `unit` must be: exact match, partial match, **or related phrase** record.
  - `unit2` must be: exact match, partial match, related phrase, Chinese punctuation, **or emoji** record.
- **Action**: `dbadapter.addOrUpdateRelatedPhraseRecord(unit.word, unit2.word)` — increments `score` in the `related` table and returns the new score.
- **LD trigger**: If returned score > 20 **AND** `mLIMEPref.getLearnPhrase()` is enabled → `addLDPhrase(unit, false)` + `addLDPhrase(unit2, true)` — feeds the pair as a completed phrase into the LD buffer.

**Note on related phrase records in the scorelist**: When the user selects a candidate from the *related phrase list* (after a previous commit), `learnRelatedPhraseAndUpdateScore()` is called again for that selection, so it too goes onto the `scorelist`. This means the related-word chain (commit A → select from related → commit B → select from related → commit C) is fully captured as consecutive pairs: `(A,B)` and `(B,C)` are both learned as related associations.

### LD (Learning Dictionary) Phrase Learning (deferred)

`addLDPhrase(mapping, ending)` buffers mappings into `LDPhraseListArray` throughout the session:
- If `mapping` is not null → append to current `LDPhraseList`.
- If `ending` is true: if `LDPhraseList.size() > 1` → save it to `LDPhraseListArray` and start a new empty list; if size ≤ 1 → discard it (single word is not a learnable phrase).
- If `mapping` is null AND `ending` is true → discard current list (interrupted sequence — e.g., LD composing was interrupted mid-sequence).

**`addLDPhrase` call sites**:
- From `commitTyped()` during continuous typing (LD composing) — one `addLDPhrase(mapping, false)` per intermediate commit, then `addLDPhrase(mapping, true)` at the final commit.
- From `learnRelatedPhrase()` when RP score exceeds 20 — `addLDPhrase(unit, false)` + `addLDPhrase(unit2, true)` for high-frequency pairs.

`learnLDPhrase(localLDPhraseListArray)` runs inside `postFinishInput()`'s background thread. For each accumulated phrase list (max 4 characters):

1. **Get first unit's code**: If the unit has no code (selected from related phrase list), do reverse lookup via `dbadapter.getMappingByWord()` to find the canonical code.
2. **Build codes character by character**:
   - **LDCode**: Full concatenation of each character's code (e.g., "su3" + "cl3" = "su3cl3").
   - **QPCode**: First character of each character's code (e.g., "s" + "c" = "sc").
3. **For multi-character words within the phrase**: Break down into individual characters, look up each one's code.
4. **For phonetic table**: Strip tone symbols `[3467 ]` from LDCode → e.g., "su3cl3" becomes "sucl".
5. **Write to database**:
   - If phonetic and LDCode length > 1: `addOrUpdateMappingRecord(LDCode, baseWord)`
   - If phonetic and QPCode length > 1: `addOrUpdateMappingRecord(QPCode, baseWord)`
   - If non-phonetic and baseCode length > 1: `addOrUpdateMappingRecord(baseCode, baseWord)`
6. **Invalidate caches**: `removeRemappedCodeCachedMappings()` + `updateSimilarCodeCache()`

### QP (Quick Phrase) Code

QPCode is part of LD learning. It creates a first-letter shortcut for learned phrases:
- QPCode = first character of each constituent word's code, concatenated.
- Example: 你好 with codes "su3" + "cl3" → QPCode = "sc".
- User can later type "sc" and "你好" appears as a candidate.
- Only generated for phonetic table (other IMs use the full concatenated code as LDCode).

---

## 10. Mode Switching

### Two Levels of Switching

LimeIME has **two independent levels** of keyboard/IM switching:

1. **OS-level switching** — switch between LimeIME and other system keyboards (e.g., iOS built-in, other 3rd-party keyboards). LimeIME registers as a **single keyboard** with the OS.
2. **LIME-internal switching** — switch between different input methods (Phonetic, Cangjie, Dayi, English, Symbol, etc.) within LimeIME itself. The OS is not aware of these internal IMs.

### Key Assignments for Switching

| Key Action | Function |
|------------|----------|
| **Keyboard key — single press** | Hide the soft keyboard |
| **Keyboard key — long press** | Show options menu (switch to other system keyboards, Han conversion settings, etc.) |
| **Space key — slide left/right** | Switch to next/previous IM within LIME |
| **Space key — long press** | Show LIME IM picker (list of activated IMs) |
| **NEXT_IM / PREV_IM keys** | Switch to next/previous IM within LIME (if keyboard layout includes these keys) |
| **SWITCH_TO_ENGLISH / SWITCH_TO_IM keys** | Toggle between Chinese IM and English mode |
| **SWITCH_TO_SYMBOL keys** | Toggle to symbol keyboard |

### iOS Porting Note — Globe Key (OS-Level Keyboard Switch)

iOS keyboard extensions must implement OS-level keyboard switching, but **the globe key does not need its own dedicated bottom-row position**. Apple's contract (stable since iOS 11):

- Check `UIInputViewController.needsInputModeSwitchKey` on every layout pass (e.g., in `viewWillLayoutSubviews`)
- **If `false`** (modern iPhone X and later — virtually all current iPhones): The system draws its own globe/dictation row below the keyboard's input view. The user taps the system's globe and UIKit handles the switch — LIME does nothing.
- **If `true`** (iPad in some configurations, older Home-button iPhones, certain embedded contexts): LIME must provide an in-keyboard control that calls `advanceToNextInputMode()`. Apple does **not** require this control to be a dedicated, always-visible globe button — it can be any easily-accessible affordance.
- Re-check on trait/size changes — the value can change (e.g., iPad split view, external keyboard attach/detach)

**LIME design — long-press the keyboard key:**

LIME does not show an independent globe key. The design mirrors the existing LIME Android behavior, with one visual difference for iOS:

- **Keyboard key — single press**: Hide the soft keyboard (same on Android and iOS)
- **Keyboard key — long press**:
  - **Visual preview**: Android shows a keyboard icon preview; iOS shows a **globe icon (🌐) preview** to satisfy Apple's "clearly visible globe affordance" expectation
  - **Action**: Pop up an options menu containing:
    - **Switch to next system keyboard** → calls `advanceToNextInputMode()` (one entry in the menu, not the only action)
    - **Show keyboard picker** → `handleInputModeList(from:with:)`
    - Han conversion settings
    - LIME preferences
    - Other options

This long-press-on-keyboard-key pattern (with globe icon as the long-press preview) is used by existing App Store–approved third-party keyboards. The globe icon preview is what makes it acceptable to Apple's review — the user sees a globe affordance when they long-press, even though the actual switch is one menu item among several.

When `needsInputModeSwitchKey == false` (modern iPhones), the OS-switch entries in the menu can either be hidden (system row handles it) or kept for parity with the Android behavior.

**Independent of OS switching — LIME-internal IM switching:**

- **Space key — slide left/right**: Switch to next/previous IM within LIME (Phonetic ↔ Cangjie ↔ Dayi ↔ ...)
- **Space key — long press**: Show LIME IM picker (list of activated IMs)
- These are completely invisible to iOS — no API calls, just internal state changes via `switchToNextActivatedIM()`

**Do NOT delete the `advanceToNextInputMode()` code path** — it must exist conditionally inside the long-press menu for the cases where `needsInputModeSwitchKey == true`.

### Chinese ↔ English

`switchChiEng()`:
1. `clearComposing(false)` — clear current composing
2. `mKeyboardSwitcher.toggleChinese()` — switch keyboard layout
3. `mEnglishOnly = !mKeyboardSwitcher.isChinese()` — update mode flag
4. `mLIMEPref.setLanguageMode(mEnglishOnly)` — persist if persistent mode enabled
5. `clearSuggestions()` — clear candidate list

Chinese/English mode switching intentionally does not show a toast.

### Switch to Next/Previous IM (LIME-Internal)

`switchToNextActivatedIM(boolean forward)`:
- Cycles through the activated IM list (from `keyboard_state` preference)
- Updates `activeIM`, calls `SearchSrv.setTableName()` to switch the query table
- Updates keyboard layout via `mKeyboardSwitcher.setKeyboardMode()`
- Resets composing and candidates
- Triggered by: space key slide/long-press, NEXT_IM/PREV_IM key codes

### Symbol Keyboard

`switchKeyboard(keycode)` handles:
- `KEYCODE_SWITCH_TO_SYMBOL_MODE`: Toggle to symbol keyboard
- `KEYCODE_SWITCH_SYMBOL_KEYBOARD`: Cycle through symbol keyboards 1/2/3
- `KEYCODE_SWITCH_TO_ENGLISH_MODE`: Switch from Chinese IM to English keyboard
- `KEYCODE_SWITCH_TO_IM_MODE`: Switch from English back to Chinese IM

---

## 11. Chinese Punctuation

### Auto Chinese Symbol

When `autoChineseSymbol` is enabled and `clearSuggestions()` is called in Chinese mode with candidates visible:
1. `hasChineseSymbolCandidatesShown = true`
2. Load Chinese symbol list via `ChineseSymbol.getChineseSymbolList()`
3. Display with selkey "1234567890"
4. User can pick a punctuation symbol like a regular candidate

### Punctuation Characters

The Chinese symbol list includes: 。，、；：？！「」『』【】〈〉《》（）—…·＆＊＃＠ and more.

---

## 12. OS Text API Mapping

### Android InputConnection → iOS textDocumentProxy

| Android `InputConnection` | iOS `textDocumentProxy` | Context in IMService |
|---------------------------|------------------------|---------------------|
| `setComposingText(text, cursor)` | `insertText()` + manual tracking | Composing display — iOS has no native composing region. Must insert text and track length to delete-and-replace on update. |
| `finishComposingText()` | No-op (text already inserted) | End composing — on iOS the text is already committed inline. |
| `commitText(text, cursor)` | `insertText(text)` | Commit selected candidate |
| `deleteSurroundingText(before, after)` | `deleteBackward()` × count | Delete characters (used sparingly) |
| `sendKeyEvent(KeyEvent)` | `insertText()` or `deleteBackward()` | Arrow keys, DEL key — iOS `textDocumentProxy` has `adjustTextPosition(byCharacterOffset:)` for cursor movement |
| `getTextBeforeCursor(n, flags)` | `documentContextBeforeInput` | Read preceding text for English prediction validation and related phrase context |
| `getTextAfterCursor(n, flags)` | `documentContextAfterInput` | Read following text for English prediction validation |
| `commitCompletion(CompletionInfo)` | N/A | App-provided completions — not available on iOS keyboard extensions |
| `clearMetaKeyStates(states)` | N/A | Physical keyboard meta state — not applicable on iOS |

### iOS Composing Simulation

Since iOS `textDocumentProxy` has no `setComposingText()`:
1. Track composing length internally (e.g., `composingLength` counter)
2. On first composing character: `insertText(char)`; set `composingLength = 1`
3. On subsequent characters: `insertText(char)`; increment `composingLength`
4. On composing update (backspace within composing): `deleteBackward()` × `composingLength`, then `insertText(newComposingText)`; update `composingLength`
5. On commit: composing text is already in place — just reset `composingLength = 0`
6. On cancel: `deleteBackward()` × `composingLength`; reset

---

## 13. SearchServer API Reference

All methods are called by IMService. Thread safety: query/learning methods are called from background threads; configuration methods are called from the main thread during IM switching.

### 13.1 Query Methods

#### `getMappingByCode`

```
getMappingByCode(code: String, isSoftKeyboard: Bool, getAllRecords: Bool) → [Mapping]
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `code` | String | Current composing string |
| `isSoftKeyboard` | Bool | `true` for soft keyboard (affects candidate ordering) |
| `getAllRecords` | Bool | `false` = top results only; `true` = full result set ("show all") |

**Returns**: Mappings matching `code` from the active IM table, sorted by score.  
**Side effect**: If `smartChineseInput` is enabled, calls `makeRunTimeSuggestion()` internally to build phrase candidates.

---

#### `getRelatedByWord`

```
getRelatedByWord(word: String, getAllRecords: Bool) → [Mapping]
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `word` | String | The word just committed |
| `getAllRecords` | Bool | `false` = top results; `true` = full set |

**Returns**: Words that commonly follow `word`, from the `related` table. Empty list if none found.

---

#### `getEnglishSuggestions`

```
getEnglishSuggestions(word: String) → [Mapping]
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `word` | String | Partial English word typed so far |

**Returns**: Words starting with `word` from the English dictionary, as `RECORD_ENGLISH_SUGGESTION` mappings.  
**iOS replacement**: Drop this method. Use `UITextChecker.completions(forPartialWordRange:in:language:)` instead. See §7 for full English prediction porting notes.

---

#### `getSelkey`

```
getSelkey() → String
```

**Returns**: The selection key string for the current IM (e.g., `"1234567890"`). Used to build the candidate selection key row.

---

#### `keyToKeyname`

```
keyToKeyname(code: String) → String
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `code` | String | Raw typed code sequence |

**Returns**: Display-friendly symbol names for the code (e.g., `"bp"` → `"ㄅㄆ"`, `"ab"` → `"日月"`). Result is cached per code. If the returned value equals `code`, nothing extra is shown in the composing bar.

---

#### `getRealCodeLength`

```
getRealCodeLength(selectedMapping: Mapping, currentCode: String) → Int
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `selectedMapping` | Mapping | The candidate the user just selected |
| `currentCode` | String | Current full composing string |

**Returns**: Number of characters from the front of `currentCode` consumed by `selectedMapping`. Used to trim the composing buffer in continuous typing (LD) mode.  
**Behaviour details**:
- Phonetic: strips tone symbols `[3467 ]` from the matched code to find the boundary.
- ETEN: applies `preProcessingRemappingCode()` before boundary detection.
- Dual-mapped code: returns full composing length (abandons LD).  
**Side effect**: If `selectedMapping` is `RECORD_RUNTIME_BUILT_PHRASE`, spawns a background thread to persist all sub-phrases via `addOrUpdateMappingRecord()`.

---

### 13.2 Learning Methods

#### `learnRelatedPhraseAndUpdateScore`

```
learnRelatedPhraseAndUpdateScore(committedCandidate: Mapping)
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `committedCandidate` | Mapping | The mapping just committed |

**Called from**: `commitTyped()` step 7 (main thread), after every successful commit.  
**Behaviour**:
1. Thread-safely appends a copy of `committedCandidate` to `scorelist` (consumed later by `postFinishInput()`).
2. Spawns a background thread to call `updateScoreCache(mapping)`:
   - `dbadapter.addScore(mapping)` — persists incremented score to DB.
   - Related phrase record → invalidates its code cache entry.
   - Partial match record → invalidates the whole code cache entry.
   - Exact match record → increments cached score; if `sortSuggestions` enabled, re-positions entry in cache (insertion-sort step); calls `updateSimilarCodeCache()`.

---

#### `addLDPhrase`

```
addLDPhrase(mapping: Mapping?, ending: Bool)
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `mapping` | Mapping? | Mapping to buffer; `nil` signals an interrupted sequence |
| `ending` | Bool | `true` = this is the final mapping in the phrase |

**Behaviour**:
- `mapping != nil` → append to current `LDPhraseList`.
- `ending == true` AND `LDPhraseList.count > 1` → save list to `LDPhraseListArray`, start new list.
- `ending == true` AND `LDPhraseList.count ≤ 1` → discard (single word is not a learnable phrase).
- `mapping == nil` AND `ending == true` → discard current list (LD sequence was interrupted).

**Call sites**: `commitTyped()` during continuous typing (one `false` per intermediate commit, one `true` at final); `learnRelatedPhrase()` when RP score exceeds 20.

---

### 13.3 Configuration Methods

#### `setTableName`

```
setTableName(table: String, numberMapping: Bool, symbolMapping: Bool)
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `table` | String | IM table name to switch to (e.g., `"cj"`, `"phonetic"`) |
| `numberMapping` | Bool | Whether the IM accepts digit keys as code input |
| `symbolMapping` | Bool | Whether the IM accepts symbol keys as code input |

**Behaviour**: Switches the active IM table. Updates `hasNumberMapping` and `hasSymbolMapping` flags used by `handleCharacter()`. Triggers cache prefetch for the new table.

---

#### `getTablename`

```
getTablename() → String
```

**Returns**: The currently active IM table name.

---

#### `resetCache`

```
resetCache()
```

Clears all in-memory candidate caches. Called when switching IMs or after user data is cleared.

---

### 13.4 Conversion Methods

#### `hanConvert`

```
hanConvert(input: String) → String
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `input` | String | Word to convert |

**Returns**: Converted word per `hanConvertOption` (0=off, 1=T→S, 2=S→T).  
**iOS replacement**: Drop `LimeHanConverter` and `hanconvertv2.db`. Replace with a single `CFStringTransform()` call:
- T→S: `CFStringTransform(str, nil, kCFStringTransformToSimplifiedChinese, false)`
- S→T: `CFStringTransform(str, nil, kCFStringTransformToTraditionalChinese, false)`

---

#### `emojiConvert`

```
emojiConvert(code: String, type: Int) → [Mapping]
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `code` | String | Word to look up |
| `type` | Int | `EMOJI_EN` / `EMOJI_TW` / `EMOJI_CN` |

**Returns**: Emoji candidates matching the word, as `RECORD_EMOJI_WORD` mappings.  
**iOS implementation**: No built-in word-to-emoji API on iOS. Convert `emoji.db` to a bundled read-only JSON dictionary. Wrap results as `Mapping` objects with `RECORD_EMOJI_WORD` type and feed into the standard candidate pipeline. Eliminates SQLite overhead for a small static dataset.

---

#### `getCodeListStringFromWord`

```
getCodeListStringFromWord(word: String) → String
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `word` | String | Word to reverse-look up |

**Returns**: Comma-separated string of all codes that produce `word`. Used to build the reverse-lookup notification shown after commit when the active IM's reverse-lookup table is not `none`.

---

### 13.5 Lifecycle Methods

#### `postFinishInput`

```
postFinishInput()
```

Called when the text field loses focus (input session ends). Flushes all pending learning in a single background thread:

1. Snapshot `scorelist` → `scorelistSnapshot`; clear `scorelist`.
2. Run `learnRelatedPhrase(scorelistSnapshot)` — RP learning (see §9).
3. Snapshot `LDPhraseListArray` → local copy; clear global array.
4. Run `learnLDPhrase(localLDPhraseListArray)` — LD phrase learning (see §9).

Ensures all queued learning is processed even if the user switches away mid-session.

---

### 13.6 IM Configuration Methods

#### `getAllImKeyboardConfigList`

```
getAllImKeyboardConfigList() → [ImConfig]
```

**Returns**: All IM-to-keyboard configuration mappings from the `im` table. Used during IM switching to determine which keyboard layout to load for each IM.

---

#### `getKeyboardConfigList`

```
getKeyboardConfigList() → [Keyboard]
```

**Returns**: All keyboard layout configurations from the `keyboard` table. Used by `LIMEKeyboardSwitcher` to map IM codes to keyboard resource files.

---

## 14. Data Model: Mapping

### Fields

| Field | Type | Purpose |
|-------|------|---------|
| `id` | String | Database record ID |
| `code` | String | Input code (lowercase) |
| `codeorig` | String | Original input code (preserves case) |
| `code3r` | String | Code without tone keys (phonetic only) |
| `word` | String | Output word / candidate text |
| `pword` | String | Parent word (for related phrases) |
| `related` | String | Related phrase info |
| `score` | int | User score (learned frequency) |
| `basescore` | int | Base score from preloaded data |
| `highLighted` | Boolean | From highlighted list vs exact match |
| `recordType` | int | Type classifier (see constants below) |

### Record Type Constants

| Constant | Value | Meaning |
|----------|-------|---------|
| `RECORD_COMPOSING_CODE` | 1 | The input code itself (first item in list) |
| `RECORD_EXACT_MATCH_TO_CODE` | 2 | Exact code match from database |
| `RECORD_PARTIAL_MATCH_TO_CODE` | 3 | Prefix match |
| `RECORD_RELATED_PHRASE` | 4 | Related phrase suggestion |
| `RECORD_ENGLISH_SUGGESTION` | 5 | English word suggestion |
| `RECORD_RUNTIME_BUILT_PHRASE` | 6 | Generated phrase from LD/QP learning |
| `RECORD_CHINESE_PUNCTUATION_SYMBOL` | 7 | Chinese punctuation |
| `RECORD_HAS_MORE_RECORDS_MARK` | 8 | "More..." placeholder |
| `RECORD_EXACT_MATCH_TO_WORD` | 9 | Reverse lookup: word→code exact |
| `RECORD_PARTIAL_MATCH_TO_WORD` | 10 | Reverse lookup: word→code partial |
| `RECORD_COMPLETION_SUGGESTION_WORD` | 11 | Word completion suggestion |
| `RECORD_EMOJI_WORD` | 12 | Emoji character |

### Type Checker Methods

Each record type has a corresponding `is*Record()` method (e.g., `isExactMatchToCodeRecord()`, `isRelatedPhraseRecord()`, `isRuntimeBuiltPhraseRecord()`). These are used throughout the commit and learning flows to determine handling logic.

---

## 15. User Settings Reference

All user-adjustable settings from `LIMEPreferenceManager`, grouped by category with the logic each setting affects.

### Input Behavior

| Setting | Pref Key | Type | Default | Affects |
|---------|----------|------|---------|---------|
| Active IM | `keyboard_list` | String | "phonetic" | Which IM table SearchServer queries |
| IM activated state | `keyboard_state` | String | all enabled | Which IMs appear in NEXT_IM/PREV_IM cycle |
| Phonetic keyboard type | `phonetic_keyboard_type` | String | "phonetic" | Code remapping in `preProcessingRemappingCode()`: Standard / ETEN / ETEN26 / HSU |
| Auto-commit threshold | `auto_commit` | int | 0 (off) | Composing auto-commit when length reaches threshold (phonetic only) |
| Han conversion | `han_convert_option` | int | 0 (off) | 0=off, 1=T→S, 2=S→T. Applied in `commitTyped()` before `commitText()` |
| Selection key option | `selkey_option` | int | 0 | Candidate selkey prepend: 0=none, 1=prepend mixedModeSelkey, 2=prepend with space |
| Smart Chinese input | `smart_chinese_input` | boolean | false | Enables runtime phrase suggestion (`makeRunTimeSuggestion`) in SearchServer |
| Auto Chinese symbol | `auto_chinese_symbol` | boolean | false | Shows Chinese punctuation candidates when candidate list cleared |
| Persistent language mode | `persistent_language_mode` | boolean | false | Remembers Chinese/English mode across text fields |
| Language mode | `language_mode` | String | "no" | Stored Chinese/English state ("yes"=English, "no"=Chinese) |
| Swipe candidate bar | `candidate_switch` | boolean | true | `CandidateView` swipe gesture: `true` = dragging scrolls the candidate list; `false` = swipe left/right commits previous/next candidate. Global — applies to all IMs |

### Learning

| Setting | Pref Key | Type | Default | Affects |
|---------|----------|------|---------|---------|
| Learn related words | `candidate_suggestion` | boolean | true | Enables RP learning in `learnRelatedPhrase()` |
| Learn phrase | `learn_phrase` | boolean | true | Enables LD phrase learning when RP score > 20 |
| Sort suggestions | `learning_switch` | boolean | true | Score-based candidate reordering in search results |
| Similar code enable | `similiar_enable` | boolean | true | Show candidates with similar (prefix-matching) codes |
| Similar code limit | `similiar_list` | int | 20 | Max number of similar-code candidates |

### Emoji

| Setting | Pref Key | Type | Default | Affects |
|---------|----------|------|---------|---------|
| Emoji display position | `enable_emoji_position` | int | 6 | `0` disables inline emoji candidates; `2`–`10` inserts after that candidate index |

### English

| Setting | Pref Key | Type | Default | Affects |
|---------|----------|------|---------|---------|
| English prediction | `english_dictionary_enable` | boolean | true | `updateEnglishPrediction()` activation in English mode |
| Auto-capitalization | `auto_cap` | boolean | true | `updateShiftKeyState()` after key handling |

### Keyboard Appearance

| Setting | Pref Key | Type | Default | Affects |
|---------|----------|------|---------|---------|
| Font size | `font_size` | float | 1.0 | Candidate text size multiplier |
| Keyboard size | `keyboard_size` | float | 1.0 | Key size scale multiplier |
| Show arrow keys | `show_arrow_key` | int | 0 | Arrow key row visibility mode |
| Split keyboard | `split_keyboard_mode` | int | 0 | Split keyboard layout mode |
| Keyboard theme | `keyboard_theme` | int | 6 | Theme index; 6 follows system light/dark and triggers input view recreation on change |
| Number row in English | `number_row_in_english` | boolean | true | Number row in English keyboard |

### Feedback

| Setting | Pref Key | Type | Default | Affects |
|---------|----------|------|---------|---------|
| Vibrate on keypress | `vibrate_on_keypress` | boolean | true | Haptic feedback on key press. iOS: `UIImpactFeedbackGenerator` |
| Vibrate level | `vibrate_level` | int | 40 | Vibration duration in ms. iOS: map to `UIImpactFeedbackGenerator` style — e.g., ≤20ms→`.light`, ≤50ms→`.medium`, >50ms→`.heavy`. Note: iOS does not allow custom duration; only style selection. |
| Sound on keypress | `sound_on_keypress` | boolean | false | Audio feedback. iOS: `AudioServicesPlaySystemSound` for system default, or `AVAudioPlayer` for custom volume. |
| Keypress sound volume | `keypress_sound_volume` | string | "-1" | "-1" uses the system default click path; "0.10" / "0.25" / "0.50" / "0.75" / "1.00" use explicit LIME click volume. |

### Reverse Lookup

| Setting | Pref Key | Type | Default | Affects |
|---------|----------|------|---------|---------|
| Reverse lookup table | `{table}_im_reverselookup` | String | "none" | Which IM to use for reverse lookup display |

### Custom IM

| Setting | Pref Key | Type | Default | Affects |
|---------|----------|------|---------|--------|
| Allow digit keys as IM codes | `accept_number_index` | boolean | false | `hasNumberMapping` flag in `initializeIMKeyboard()` — **custom IM only**; all built-in IMs hardcode their own value regardless of this pref |
| Allow symbol keys as IM codes | `accept_symbol_index` | boolean | false | `hasSymbolMapping` flag in `initializeIMKeyboard()` — **custom IM only**; all built-in IMs hardcode their own value regardless of this pref |

### Physical Keyboard (exclude from iOS port)

These settings are not applicable to iOS keyboard extensions:
`physical_keyboard_enable`, `disable_physical_selkey`, `disable_physical_selkey_option`, `english_dictionary_physical_keyboard`, `physical_keyboard_sort`, `switch_english_mode`, `switch_english_mode_shift`, `hide_software_keyboard_typing_with_physical`, `three_rows_remapping`

`physical_keyboard_type` is a legacy Android phone hardware-keyboard value retained only as a runtime default/backward-compatibility key, not a visible preference.

### Per-Table Metadata (managed by DBServer, not user-facing)

`{table}total_record`, `{table}mapping_version`, `{table}mapping_file`, `{table}mapping_file_temp`, `total_userdict_record`, `searchsrv_reset_cache`
