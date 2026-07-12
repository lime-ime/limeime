# CANDI_FUNCTION_KEYS — Functional Keys vs Candidate Display State

Reference for how functional keys (Backspace, Space, Enter, etc.) should behave
when various candidate lists are visible. Covers iOS and Android, documents
their current behavior, and flags bugs being tracked under
[`docs/#78_ISSUE.md`](#78_ISSUE.md).

## Candidate display states

The IME can be showing one of several lists. Each has different semantics for
functional keys:

| State | Set by | Composing buffer | Treated as |
|---|---|---|---|
| **Active composing candidates** | typing IM-input keys (`mComposing` non-empty) | non-empty | committable list — Space/Enter pick the highlighted candidate |
| **Related phrases** | after committing a Chinese candidate, IM emits associated phrases | empty | optional / browse-only — Space/Enter do **not** commit, must insert space/newline |
| **English predictions** | `mEnglishOnly == true` + `UITextChecker`/dictionary suggestions | empty (English doesn't compose into `mComposing`; `tempEnglishWord` is the prediction-side buffer) | optional / browse-only — same as above |
| **Chinese punctuation list** | `autoChineseSymbol == true` + no composing | empty | optional — Space/Enter insert their literal char; Backspace dismisses without delete (intentional gesture) |
| **No candidates** | `hasCandidatesShown == false` | empty | normal text input — keys go straight to `textDocumentProxy` / `InputConnection` |

Optional / browse-only lists must follow these rules:

- **Space**: insert a literal space.
- **Enter**: insert a literal newline.
- **Backspace**: perform the normal delete (`textDocumentProxy.deleteBackward()` /
  `keyDownUp(KEYCODE_DEL)`); stale optional suggestions should be dismissed as
  part of the same action.
- Optional lists must auto-dismiss when normal typing actions make them stale.

Chinese punctuation has one carve-out — Backspace **dismisses without
deleting** (acts as a "cancel" gesture). This is intentional on both platforms.

## Functional-key inventory (iOS)

Dispatch happens in `KeyboardViewController.onKey(primaryCode:)`
([KeyboardViewController.swift](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift)):

| `LimeKeyCode` | Handler | Interacts with candidate state? |
|---|---|---|
| `delete` | `handleBackspace()` | YES — see Backspace section below |
| `space` | `handleEnterOrSpace(isEnter: false)` | YES — uses `isAssociatedList` |
| `enter` | `handleEnterOrSpace(isEnter: true)` | YES — uses `isAssociatedList` |
| `shift` | `handleShift()` | no |
| `done` | `handleClose()` | no |
| `globe` | `advanceToNextInputMode()` | no (system) |
| `emojiPanel`, `emojiABC` | `showEmojiPanel()` / `hideEmojiPanel()` | dismisses candidates implicitly |
| `switchToEnglish`, `switchToIM` | `switchChiEng(...)` | dismisses candidates |
| `switchToSymbol`, `switchSymbolKeyboard` | `switchToSymbol()` / `cycleSymbolPage()` | dismisses candidates |
| `nextIM`, `prevIM` | `switchToNextActivatedIM(...)` | dismisses candidates |
| `arrowLeft`, `arrowRight`, `arrowUp`, `arrowDown` | candidate navigation when candidates shown, otherwise text-cursor movement | YES — but selection-only, not commit/delete |

Backspace and Enter/Space are the only handlers that can both delete-or-insert
text **and** read/write candidate-list state. They are the focus of #78.

## Per-key matrix — current behavior

Each cell describes what happens **right now** in master.

### Backspace

| Visible state | iOS ([KeyboardViewController.swift:1177](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L1177)) | Android ([LIMEService.java:4120](../LimeStudio/app/src/main/java/org/limeime/LIMEService.java#L4120)) |
|---|---|---|
| Active composing (length > 1) | pop last code, `deleteBackward()`, refresh candidates ✓ | pop last code, `setComposingText`, refresh ✓ |
| Active composing (length == 1) | `clearComposing(force: true)` ✓ | `clearComposing(true)` ✓ |
| Related phrases visible, composing empty | `clearSuggestions()` — **clears bar, no delete** ✗ | `clearComposing(false)` — **clears bar, no delete** ✗ (needs 2–3 taps to delete the next character) |
| English prediction visible, composing empty | `clearSuggestions()` — **clears bar, no delete** ✗ | falls through to else → pops `tempEnglishWord` + `keyDownUp(KEYCODE_DEL)` ✓ |
| Chinese punctuation visible | dismiss without delete ✓ (intentional) | dismiss without delete ✓ (intentional) |
| No candidates | `deleteBackward()` ✓ | `keyDownUp(KEYCODE_DEL)` ✓ |

The asymmetry between iOS and Android for the English-prediction case comes
from one extra clause: Android's "clear candidates" branches are gated by
`!mEnglishOnly`, so English mode always falls through to the delete path. iOS
checks `hasCandidatesShown` without that gate.

### Space

| Visible state | iOS | Android |
|---|---|---|
| Active composing (non-phonetic) | pick highlighted candidate ✓ | pick highlighted candidate ✓ |
| Active composing (phonetic, no trailing space) | insert space as tone marker via `handleCharacter` ✓ | insert space as tone marker ✓ |
| Active composing (phonetic, trailing space / empty) | pick highlighted candidate ✓ | pick highlighted candidate ✓ |
| Related phrases visible | `isAssociatedList` true → insert literal space ✓ | `pickHighlightedCandidate()` returns false (no default selection) → `sendKeyChar(' ')` ✓ |
| English prediction visible | this branch is not entered for `mEnglishOnly` Space — handled inside `handleCharacter` (resets prediction, inserts space) ✓ | same — Space falls to `handleCharacter` in English mode ✓ |
| Chinese punctuation visible | insert literal space ✓ | `pickHighlightedCandidate()` returns false → `sendKeyChar(' ')` ✓ |

### Enter

| Visible state | iOS | Android |
|---|---|---|
| Active composing | `shouldPick = hasCandidatesShown` → pick ✓ | `pickHighlightedCandidate()` succeeds → commit ✓ |
| Related phrases visible | `isAssociatedList` true → `insertText("\n")` — **bar stays visible** | `pickHighlightedCandidate()` false → `hideCandidateView()` + `sendKeyChar('\n')` ✓ |
| English prediction visible | `isAssociatedList` true → `insertText("\n")` + reset `tempEnglishWord` + `clearSuggestions()` ✓ | `pickHighlightedCandidate()` false → `hideCandidateView()` + `sendKeyChar('\n')` ✓ |
| Chinese punctuation visible | `isAssociatedList` true → `insertText("\n")` — **bar stays visible** | `pickHighlightedCandidate()` false → `hideCandidateView()` + `sendKeyChar('\n')` ✓ |

### Other functional keys (briefly)

- **Shift / CapsLock**: layout-only, never touches candidates. ✓
- **Globe / Done**: system-handled (Globe) or dismiss-only (Done). ✓
- **Emoji panel toggle, mode switches (ABC↔中, 123, symbol page)**: each
  switch path calls `clearSuggestions()` / `clearComposing(...)` before
  switching, so stale candidates can't leak across modes. ✓
- **Arrow keys**: when candidates are visible they navigate the selection;
  otherwise they move the text cursor. Selection-only — never commit/delete on
  their own — so no candidate-vs-functional-key conflict. ✓
- **IM cycle (`nextIM`, `prevIM`)**: clears composing before switching. ✓

## Bugs (tracked under #78) — FIXED

All three bugs were fixed under issue #78. Both platforms now route
optional/browse-only suggestion lists through the same classification.

### Bug 1 — iOS Backspace on English prediction (FIXED, iOS)

**Was**: typing an English prefix that produces predictions, then pressing
Backspace, cleared the prediction bar instead of deleting a character and
refreshing predictions. The `else if hasCandidatesShown { clearSuggestions() }`
branch in `handleBackspace()` fired before the English-deletion branch was
reached.

**Fix**: in [`KeyboardViewController.swift handleBackspace()`](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift),
the `mEnglishOnly && !tempEnglishWord.isEmpty` branch was promoted above the
generic `hasCandidatesShown` branch so the English delete path is always
reached when predictions are visible. Result: iOS now matches Android's
behaviour (whose analogous branches are gated by `!mEnglishOnly`).

### Bug 2 — Related-phrase Backspace requires multiple taps (FIXED, iOS + Android)

**Was**: after committing a Chinese candidate (which surfaces related
phrases), the first Backspace cleared the related-phrase bar instead of
deleting the previously committed character. iOS just kept the bar visible
without deleting; Android cleared the bar and (under `autoChineseSymbol`)
slid into the Chinese-symbol list, requiring 2–3 taps to actually delete.

**Fix (iOS)**: new shared helper `isBrowseOnlySuggestionList` plus a new
branch in `handleBackspace()` that fires when composing is empty AND the bar
is browse-only. It calls `dismissBrowseOnlySuggestionBar()` (clears
`isShowingRelatedPhrases` / `hasChineseSymbolCandidatesShown` /
`hasCandidatesShown` / `mCandidateList` / `selectedCandidate` and resets the
bar) then calls `textDocumentProxy.deleteBackward()` once.

**Fix (Android)**: in [`LIMEService.java handleBackspace()`](../LimeStudio/app/src/main/java/org/limeime/LIMEService.java),
the `!mEnglishOnly && hasCandidatesShown && !hasChineseSymbolCandidatesShown`
branch now pre-clears `hasCandidatesShown = false` (so
`clearSuggestions()` inside `clearComposing(false)` doesn't slide into
`updateChineseSymbol()`), calls `clearComposing(false)`, then issues
`keyDownUp(KeyEvent.KEYCODE_DEL, false)` — one tap dismisses the bar and
deletes a character.

### Bug 3 — iOS Enter doesn't dismiss stale related/symbol bars (FIXED, iOS)

**Was**: with related phrases or Chinese punctuation visible, pressing Enter
inserted the newline correctly but left the bar visible.

**Fix**: in [`handleEnterOrSpace(isEnter:)`](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift),
after the `textDocumentProxy.insertText(isEnter ? "\n" : " ")` in the
not-picking branch, when `isAssociatedList && mComposing.isEmpty` (and not
already handled by the existing English-mode cleanup), the new
`dismissBrowseOnlySuggestionBar()` helper is called. Matches Android's
`hideCandidateView()` in the same path.

## Classification helper (proposed)

Both `handleBackspace()` and `handleEnterOrSpace(isEnter:)` need the same
"is this an optional/browse-only suggestion list?" check. Centralize it:

```swift
private var isBrowseOnlySuggestionList: Bool {
    isShowingRelatedPhrases
        || hasChineseSymbolCandidatesShown
        || (mEnglishOnly && hasCandidatesShown)
}
```

Then both handlers use the same flag instead of recomputing the disjunction.
Chinese punctuation needs special handling in Backspace (dismiss without
delete), so callers may still check `hasChineseSymbolCandidatesShown`
separately — but the *classification* of "this is optional / no default
selection" is shared.

## Verification (when fixing #78)

For both platforms, after the fix:

- **English mode**: type a prefix that produces predictions. One Backspace
  deletes one character and predictions refresh; another Backspace either
  refreshes further or dismisses the bar. Space inserts a literal space.
  Enter inserts a literal newline and dismisses the bar.
- **Related phrases**: commit a Chinese candidate that produces related
  phrases. One Backspace deletes one character of the previously committed
  text and dismisses the related bar. Space inserts a literal space. Enter
  inserts a literal newline and dismisses the related bar.
- **Chinese punctuation**: Backspace still dismisses without deleting
  (intentional). Space/Enter still insert their literal characters and
  dismiss the bar.
- **Normal composing**: Backspace shrinks composing as before; Space/Enter
  still commit the highlighted candidate.

Regression-check: rapid Backspace during stage-1 candidate window must not
re-introduce stale `…` sentinels (cross-reference with
[`TWO_STAGE_CANDI.md`](TWO_STAGE_CANDI.md) and
[`#77_ISSUE.md`](#77_ISSUE.md)).
