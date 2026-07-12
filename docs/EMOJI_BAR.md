# Emoji button — candidate bar left-end zone

## Context

The earlier EMOJI_KEYBOARD.md spec placed the emoji launcher inside the English keyboard layout:

- iPhone: move 中 from bottom row to the ASDF home row's empty 10% slot, put 😀 in the vacated bottom-row position.
- iPad: trim space key width, place 😀 immediately left of space.
- Android: add 😀 to the English bottom row near the space cluster.

Those keyboard-layout changes are reverted. The emoji launcher is instead surfaced from the **candidate bar's left-end zone** — the same slot already used by the dismiss (✕) button — making it a context-aware button that alternates between emoji and dismiss depending on candidate-bar state.

Current `lime_abc.json` already shows 中 in the bottom row (no home-row reflow was committed to the layout JSON), so the English keyboard itself needs no changes.

## Design

### Left-end zone state machine

The candidate bar's left zone is shared with the dismiss button:

| Candidate bar state | Left zone shows |
| --- | --- |
| Empty | 😀 emoji button |
| Candidates present | ✕ dismiss button |

This mirrors how Android's right-end zone swaps between the microphone icon (empty) and expand arrow (candidates present). The launcher is always enabled; there is no Settings preference for hiding it.

### iOS geometry

- `emojiButton: UIButton` is a new sibling of `dismissButton` in `CandidateBarView`
- Centered in the first key column guide, with width around 80% of that key column so it aligns with the keyboard below.
- `scrollView.leadingAnchor` stays pinned to `dismissButton.trailingAnchor` — unchanged, works because both buttons occupy the same zone
- Visibility is mutually exclusive:
  - `dismissButton.isHidden = !hasCandidates`
  - `emojiButton.isHidden = hasCandidates`
- Glyph: SF Symbol `face.smiling` (same size/config as dismissButton's `xmark`); literal 😀 as a fallback label
- Background: transparent
- Tap → `delegate?.candidateBarViewDidRequestEmoji(self)` → `KeyboardViewController.showEmojiPanel()`

### Android geometry

- Add an `ImageButton` in the left zone of `inputcandidate.xml`, before `candidatesView`, width = `@dimen/candidate_dismiss_button_width` (21 sp), initial visibility = `GONE`
- `CandidateInInputViewContainer.requestLayout()` existing logic extended:
  - empty candidate bar → emojiButton `VISIBLE`, dismissButton `GONE`
  - candidates present → dismissButton `VISIBLE`, emojiButton `GONE`
- `updateCandidateViewWidthConstraint()` must include emojiButton width when visible
- Wire click → LIMEService emoji panel dispatch (same `-201` code)

## Return keyboard routing

The candidate bar emoji button is accessible from any keyboard state — both the English layout and any Chinese IM layout. The emoji panel must record the source keyboard before mounting and restore it on dismiss.

### Source state capture

When `showEmojiPanel()` fires (triggered by tapping the candidate bar emoji button):

- **iOS**: `KeyboardViewController` captures the current mode as an `EmojiPanelSource` enum (`.english` or `.chineseIM`) and stores it before switching to the panel view.
- **Android**: `LIMEService` captures the current keyboard mode (English vs. IM) at the moment it dispatches `-201`.

### Bottom-left dismiss button

The bottom-left key in the emoji panel bookmark strip reflects the source keyboard, not always "ABC":

| Source keyboard | Button label | Return behavior |
| --- | --- | --- |
| English layout | ABC | Restore English layout |
| Chinese IM (any) | 中 | Restore the active Chinese IM keyboard |

The button sends the same internal code (`-202`) in both cases. The routing lives in the dismiss handler, keyed off the stored source state — no new key code is needed.

### iOS implementation notes

```swift
enum EmojiPanelSource { case english, chineseIM }

// In showEmojiPanel():
emojiPanelSource = isEnglishMode ? .english : .chineseIM
emojiPanelView.dismissLabel = emojiPanelSource == .english ? "ABC" : "中"

// In hideEmojiPanel() / onKey(.emojiABC):
switch emojiPanelSource {
case .english:   switchToEnglish()
case .chineseIM: switchToChineseIM()   // restores whichever IM was active
}
```

### Android implementation notes

```java
// Before showing panel:
boolean emojiSourceIsChineseIM = !isEnglishMode();

// ABC/中 button label in emoji panel view:
abcButton.setText(emojiSourceIsChineseIM ? "中" : "ABC");

// On dismiss (-202):
if (emojiSourceIsChineseIM) {
    switchToChineseIM();
} else {
    switchToEnglish();
}
```

### Invariant

Opening the emoji panel and immediately tapping the dismiss key must leave the user in exactly the same keyboard state they started in — same IM, same composing buffer cleared by the panel mount. The emoji panel is a detour, not a one-way trip to English.

## Files to update

### Source files

| File | Change |
| --- | --- |
| `LimeIME-iOS/LimeKeyboard/CandidateBarView.swift` | Add `emojiButton`, new delegate method, visibility toggle in `rebuildButtons()` |
| `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift` | Implement `candidateBarViewDidRequestEmoji` |
| `LimeStudio/app/src/main/res/layout/inputcandidate.xml` | Add emoji ImageButton to left zone |
| `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateInInputViewContainer.java` | Visibility logic + width accounting |
| `LimeStudio/app/src/main/java/org/limeime/LIMEService.java` | Dispatch emoji panel |
| `docs/EMOJI_KEYBOARD.md` | Update launcher placement section; remove keyboard-layout changes |
| `docs/CANDI_LAYOUT.md` | §9 and TODO already written |

### English keyboard layout files — no changes needed

`lime_abc.json`, `lime_abc_shift.json`, `lime_abc_ipad.json`, `lime_abc_ipad_shift.json` — 中 stays in bottom row; space key untrimmed. Already correct.

## Verification

1. Open any text field with LimeIME active, no composing → candidate bar is empty → 😀 appears at left (iOS and Android).
2. Start composing → candidates appear → left zone switches to ✕, emoji icon gone.
3. Tap ✕ → composing cleared, bar empties → 😀 reappears.
4. Tap 😀 → emoji panel opens (panel behavior per existing EMOJI_KEYBOARD.md spec).
5. English keyboard layout on iPhone shows 中 in bottom row unchanged.
6. iPad English keyboard keeps full-width space, no trimming.
